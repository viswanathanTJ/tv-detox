package dev.tvmon;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** TV Monitor: shows how full the TV's memory is, which apps are running, and lets the user stop them with the remote. */
public class MainActivity extends Activity {
    static final int BG = 0xFF0F1418, PANEL = 0xFF1B232C, ROW = 0xFF19202A, ROW_HI = 0xFF2B3746, FG = 0xFFF1F3F4, DIM = 0xFFB4BAC1,
        DARK = 0xFF101418, OK = 0xFF81C995, WARN = 0xFFFDD663, BAD = 0xFFF28B82, ACC = 0xFF8AB4F8, BTN = 0xFF2E3A48,
        STOP_BG = 0xFF6B2E2B, WHITE = 0xFFFFFFFF, TRACK = 0xFF333D49;
    static final long REFRESH_MS = 5000, CONFIRM_MS = 5000;
    static final String CORE = "__core__";   // native system processes with no installable package, grouped into one row

    // Never given a Stop button and never stopped by "Free up memory": the TV needs these to run.
    static final String[] NEEDED = {
        "system", "zygote", "webview_zygote", "surfaceflinger", "android", "com.android.", "com.google.android.gms", "com.google.android.gsf",
        "com.google.android.inputmethod", "com.google.android.tungsten", "com.google.android.webview", "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller", "com.google.android.ext.", "com.mediatek.", "mitv.service", "xiaomi.tvservice", "com.mitv.overseaservice",
        "com.mitv.milinkservice", "com.mi.bluetooth", "com.xiaomi.android.tvsetup", "com.mitv.tvhome.atv", "com.ansub.tvhome", "dev.tvmon", "media.", "vendor."
    };

    /** One app (all of its processes added up). cls: 0 needed by the TV, 1 app you use, 2 other app in the background. */
    /** Text is drawn at this share of the sizes below, so it reads as medium size on a TV. */
    static final float TEXT_SCALE = 0.66f;

    static class App { String pkg, label; long kb; int cls; Drawable icon; }
    static class Snap {
        Map<String, Long> mi = new HashMap<>();
        List<App> apps = new ArrayList<>();
        String problem;
        int cpuPct = -1;                                   // busy share of the processor, 0-100
        final List<String[]> cpuTop = new ArrayList<>();   // {percent, process name}, busiest first
        final long at = SystemClock.uptimeMillis();   // when the reading started
    }

    final Handler ui = new Handler(Looper.getMainLooper());
    final Map<String, Drawable> icons = new ConcurrentHashMap<>();
    volatile boolean busy, resumed, stopping, freeing;
    PackageManager pm;
    final Set<String> launcherPkgs = new HashSet<>();

    ScrollView sv;
    LinearLayout tabRow, memPage, cpuPage, cpuBody;
    Button tabMem, tabCpu;
    TextView cpuHead, cpuNote;
    String tab = "mem";
    LinearLayout body;
    TextView headline, subline, freeText, msg;
    View barFill, barRest;
    Button freeBtn, refreshBtn;
    final Runnable[] tickRef = new Runnable[1];

    boolean showNeeded;
    Snap lastSnap;
    String layoutSig = "";
    final Map<String, TextView> memViews = new HashMap<>();
    final List<View> focusOrder = new ArrayList<>();

    // Stop confirm state: the first OK arms the button, a second OK within CONFIRM_MS stops the app.
    String armedPkg;
    Button armedBtn;
    final Runnable disarmLater = () -> { disarm(); hint(); };
    String lastStoppedPkg, lastStoppedLabel;
    long lastStoppedAt;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        for (String cat : new String[] {"android.intent.category.LEANBACK_LAUNCHER", Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo r : pm.queryIntentActivities(q, 0)) launcherPkgs.add(r.activityInfo.packageName);
        }
        build();
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true;
        tickRef[0] = () -> {
            if (armedPkg == null && !stopping && !freeing) refresh();
            if (resumed) ui.postDelayed(tickRef[0], REFRESH_MS);
        };
        ui.post(tickRef[0]);
    }
    @Override protected void onPause() { super.onPause(); resumed = false; disarm(); if (tickRef[0] != null) ui.removeCallbacks(tickRef[0]); }

    // ---- small view helpers -----------------------------------------------------------------------

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp * TEXT_SCALE); t.setTextColor(color);
        t.setIncludeFontPadding(true);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Single line that cuts off with "…" instead of wrapping into the next row. */
    TextView oneLine(String s, int sp, int color, boolean bold) {
        TextView t = text(s, sp, color, bold);
        t.setSingleLine(true); t.setMaxLines(1); t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    GradientDrawable round(int fill, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(12));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    /** Background that turns white with a thick outline when focused, so the remote's position is always obvious. */
    Drawable focusBg(int normal, int focused) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_pressed}, round(ACC, WHITE, 4));
        s.addState(new int[] {android.R.attr.state_focused}, round(focused, ACC, 4));
        s.addState(new int[] {android.R.attr.state_selected}, round(ROW_HI, ACC, 3));
        s.addState(new int[] {}, round(normal, 0, 0));
        return s;
    }

    ColorStateList textOnFocus(int normal, int focused) {
        return new ColorStateList(new int[][] {{android.R.attr.state_pressed}, {android.R.attr.state_focused}, {}}, new int[] {focused, focused, normal});
    }

    Button button(String s, int sp, int heightDp, int minWidthDp, int normalBg) {
        Button b = new Button(this);
        b.setText(s); b.setTextSize(sp * TEXT_SCALE); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setSingleLine(true); b.setEllipsize(TextUtils.TruncateAt.END);
        b.setFocusable(true);
        b.setBackground(focusBg(normalBg, WHITE));
        b.setTextColor(textOnFocus(FG, DARK));
        b.setStateListAnimator(null);
        b.setPadding(dp(28), 0, dp(28), 0);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(dp(minWidthDp)); b.setMinimumWidth(dp(minWidthDp));
        b.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(heightDp)));
        return b;
    }

    LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    LinearLayout.LayoutParams fullWidth() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }

    void say(String s, int color) { msg.setText(s); msg.setTextColor(color); }
    void hint() { say("", DIM); }

    // ---- fixed part of the screen -------------------------------------------------------------------

    void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(20), dp(16), dp(20));   // keeps clear of the TV's overscan edges
        sv.addView(content, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        // Split screen: the summary and buttons on the left, the app and CPU lists on the right.
        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(40), dp(16), dp(24), dp(16));
        split.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.42f));
        split.addView(sv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.58f));
        root.addView(split, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Title line
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = oneLine("TV Monitor", 34, FG, true);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(oneLine("Updates every 5 seconds", 22, DIM, false), wrap());
        left.addView(top, fullWidth());

        // Tabs: Memory and CPU
        tabRow = new LinearLayout(this);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        tabMem = button("Memory", 22, 68, 220, BTN);
        tabMem.setTag("tab:mem");
        tabMem.setOnClickListener(v -> showTab("mem"));
        tabCpu = button("CPU", 22, 68, 220, BTN);
        tabCpu.setTag("tab:cpu");
        tabCpu.setOnClickListener(v -> showTab("cpu"));
        tabRow.addView(tabMem);
        ((LinearLayout.LayoutParams) tabCpu.getLayoutParams()).leftMargin = dp(16);
        tabRow.addView(tabCpu);
        LinearLayout.LayoutParams trp = fullWidth();
        trp.topMargin = dp(18); trp.bottomMargin = dp(8);
        left.addView(tabRow, trp);

        memPage = new LinearLayout(this);
        memPage.setOrientation(LinearLayout.VERTICAL);
        content.addView(memPage, fullWidth());

        // Summary panel
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(round(PANEL, 0, 0));
        panel.setPadding(dp(28), dp(20), dp(28), dp(22));
        headline = oneLine("Checking your TV…", 32, FG, true);
        panel.addView(headline, fullWidth());
        subline = oneLine("This takes a few seconds.", 24, FG, false);
        subline.setPadding(0, dp(6), 0, dp(14));
        panel.addView(subline, fullWidth());

        LinearLayout barRow = new LinearLayout(this);
        barRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout track = new LinearLayout(this);
        track.setBackground(round(TRACK, 0, 0));
        barFill = new View(this);
        barFill.setBackground(round(OK, 0, 0));
        barRest = new View(this);
        track.addView(barFill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0f));
        track.addView(barRest, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 100f));
        barRow.addView(track, new LinearLayout.LayoutParams(0, dp(24), 1f));
        TextView barLabel = oneLine("Memory in use", 22, DIM, false);
        barLabel.setPadding(dp(20), 0, 0, 0);
        barRow.addView(barLabel, wrap());
        panel.addView(barRow, fullWidth());

        freeText = oneLine("Free memory: …", 24, FG, false);
        freeText.setPadding(0, dp(14), 0, 0);
        panel.addView(freeText, fullWidth());
        LinearLayout.LayoutParams plp = fullWidth();
        plp.topMargin = dp(16);
        left.addView(panel, plp);

        // Main actions
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(24), 0, dp(4));
        freeBtn = button("Free up memory", 26, 80, 380, BTN);
        freeBtn.setTag("free");
        freeRing.paint.setColor(0xFF101418);   // dark, so it shows on the white focused button
        freeRing.paint.setStrokeWidth(dp(5));
        freeRing.setBounds(0, 0, dp(36), dp(36));
        freeBtn.setCompoundDrawablePadding(dp(14));
        freeBtn.setCompoundDrawablesRelative(freeRing, null, null, null);
        freeRing.start();
        refreshBtn = button("Refresh", 24, 80, 200, BTN);
        refreshBtn.setTag("refresh");
        LinearLayout.LayoutParams rlp = (LinearLayout.LayoutParams) refreshBtn.getLayoutParams();
        rlp.leftMargin = dp(24);
        freeBtn.setOnClickListener(v -> freeUp());
        refreshBtn.setOnClickListener(v -> { disarm(); say("Checking again…", DIM); refresh(); });
        actions.addView(freeBtn);
        actions.addView(refreshBtn);
        left.addView(actions, fullWidth());
        TextView explain = text("\"Free up memory\" closes apps you are not using right now. It is safe.", 22, DIM, false);
        explain.setPadding(0, dp(8), 0, 0);


        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        memPage.addView(body, fullWidth());

        cpuPage = new LinearLayout(this);
        cpuPage.setOrientation(LinearLayout.VERTICAL);
        cpuHead = oneLine("Checking the processor…", 32, FG, true);
        cpuPage.addView(cpuHead, fullWidth());
        cpuNote = text("This is what is using the processor right now. Short busy moments are normal.", 22, DIM, false);
        cpuNote.setPadding(0, dp(6), 0, dp(12));
        cpuPage.addView(cpuNote, fullWidth());
        cpuBody = new LinearLayout(this);
        cpuBody.setOrientation(LinearLayout.VERTICAL);
        cpuPage.addView(cpuBody, fullWidth());
        content.addView(cpuPage, fullWidth());

        // Message bar: always on screen, even when the list is scrolled down.
        msg = text("", 24, DIM, false);
        msg.setMaxLines(2); msg.setEllipsize(TextUtils.TruncateAt.END);
        msg.setGravity(Gravity.CENTER_VERTICAL);
        msg.setMinHeight(0);
        msg.setBackgroundColor(PANEL);
        msg.setPadding(dp(40), dp(4), dp(40), dp(4));

        hint();

        showTab("mem");
        setContentView(root);
        freeBtn.requestFocus();
    }

    // ---- reading ------------------------------------------------------------------------------------

    static long num(String s) { return Long.parseLong(s.replace(",", "")); }

    static boolean needed(String name) {
        for (String p : NEEDED) if (name.equals(p) || (p.endsWith(".") ? name.startsWith(p) : name.startsWith(p + "."))) return true;
        return false;
    }

    static Map<String, Long> meminfo() {
        Map<String, Long> mi = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new java.io.FileReader("/proc/meminfo"))) {
            String l; while ((l = r.readLine()) != null) {
                String[] p = l.split("\\s+");
                if (p.length >= 2) try { mi.put(p[0].replace(":", ""), Long.parseLong(p[1])); } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) { }
        return mi;
    }

    static long get(Map<String, Long> mi, String k) { Long v = mi.get(k); return v == null ? 0 : v; }

    /** Memory a new app can get right now (what the screen calls "free memory"). */
    static long availKb(Map<String, Long> mi) {
        long ma = get(mi, "MemAvailable"), total = get(mi, "MemTotal");
        if (ma > 0 && ma <= total) return ma;
        return get(mi, "MemFree") + get(mi, "Cached");
    }

    Drawable iconFor(String pkg) {
        Drawable d = icons.get(pkg);
        if (d != null) return d;
        try { d = pm.getApplicationIcon(pkg); } catch (Exception e) { d = null; }
        if (d == null) d = getDrawable(android.R.drawable.sym_def_app_icon);
        if (d != null) icons.put(pkg, d);
        return d;
    }

    /** Runs on a background thread: memory numbers, the app list and the icons. */
    Snap read() {
        Snap s = new Snap();
        s.mi = meminfo();
        String mem, cpu = "";
        Adb adb = null;
        try {
            adb = new Adb(this);
            adb.connect();
            mem = adb.shell("dumpsys meminfo");
            cpu = adb.shell("dumpsys cpuinfo");
        } catch (Adb.NeedsAllow e) {
            s.problem = "A box on the TV asks \"Allow USB debugging?\". Tick \"Always allow\", press Allow, then press Refresh.";
            return s;
        } catch (Exception e) {
            s.problem = "TV Monitor cannot see the app list yet. Turn on \"USB debugging\" in the TV's Developer options, then press Refresh.";
            return s;
        } finally {
            if (adb != null) adb.close();
        }

        for (String l : cpu.split("\n")) {
            if (l.contains("TOTAL:")) {
                Matcher x = Pattern.compile("([\\d.]+)% user \\+ ([\\d.]+)% kernel").matcher(l);
                if (x.find()) s.cpuPct = (int) Math.round(Double.parseDouble(x.group(1)) + Double.parseDouble(x.group(2)));
            } else if (s.cpuTop.size() < 8) {
                Matcher x = Pattern.compile("^\\s*([\\d.]+)% (\\d+)/(\\S+?): ").matcher(l);
                if (x.find()) s.cpuTop.add(new String[] {x.group(1), x.group(3)});
            }
        }

        // Prefer PSS (shared memory split fairly) for "frees about"; fall back to RSS on TVs that do not print PSS by process.
        List<String[]> pss = section(mem, "Total PSS by process"), rss = section(mem, "Total RSS by process");
        List<String[]> rows = pss.isEmpty() ? rss : pss;

        Map<String, App> groups = new LinkedHashMap<>();
        for (String[] r : rows) {
            String name = r[1], pkg = name.split(":")[0];
            long kb = num(r[0]);
            String label = null;
            boolean installed = false;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                label = String.valueOf(pm.getApplicationLabel(ai));
                installed = true;
            } catch (Exception ignored) { }
            String key = installed ? pkg : CORE;
            App a = groups.get(key);
            if (a == null) {
                a = new App();
                a.pkg = key;
                if (!installed) { a.label = "Android system"; a.cls = 0; }
                else { a.label = label; a.cls = needed(pkg) ? 0 : (launcherPkgs.contains(pkg) ? 1 : 2); }
                if (pkg.equals(getPackageName())) a.label = "TV Monitor (this app)";
                a.icon = iconFor(installed ? pkg : "android");
                groups.put(key, a);
            }
            a.kb += kb;
        }
        s.apps.addAll(groups.values());
        Collections.sort(s.apps, (x, y) -> Long.compare(y.kb, x.kb));
        if (s.apps.isEmpty()) s.problem = "TV Monitor could not read the app list. Press Refresh to try again.";
        return s;
    }

    /** Lines like "  123,456K: com.foo (pid 123 / activities)" under the given heading, as {kb, name}. */
    static List<String[]> section(String mem, String heading) {
        List<String[]> out = new ArrayList<>();
        Pattern line = Pattern.compile("^\\s*([\\d,]+)K: (\\S+) \\(pid (\\d+)");
        boolean in = false;
        for (String l : mem.split("\n")) {
            if (l.startsWith(heading)) { in = true; continue; }
            if (in && l.trim().isEmpty()) break;
            if (!in) continue;
            Matcher m = line.matcher(l);
            if (m.find()) out.add(new String[] {m.group(1), m.group(2)});
        }
        return out;
    }

    // A round spinner shown inside the Refresh button while the app checks the TV.
    final RingSpinner ring = new RingSpinner();
    // The ring inside the Free up memory button, shown from launch until the first reading arrives.
    final RingSpinner freeRing = new RingSpinner();
    boolean loaded;

    void startRing() {
        ring.setBounds(0, 0, dp(28), dp(28));
        refreshBtn.setText("Checking…");
        refreshBtn.setCompoundDrawablePadding(dp(12));
        refreshBtn.setCompoundDrawablesRelative(ring, null, null, null);
        ring.start();
    }

    void stopRing() {
        ring.stop();
        refreshBtn.setCompoundDrawablesRelative(null, null, null, null);
        refreshBtn.setText("Refresh");
    }

    /** A round arc that turns. Static nested class, as the other drawables in this file. */
    static final class RingSpinner extends android.graphics.drawable.Drawable {
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.RectF box = new android.graphics.RectF();
        float angle;

        RingSpinner() {
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        }

        // Turns with a timer, not an animator: the TV's "animations off" setting stops animators.
        final android.os.Handler turn = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable step = new Runnable() {
            @Override public void run() {
                angle = (angle + 14f) % 360f;
                invalidateSelf();
                turn.postDelayed(this, 40);
            }
        };
        boolean turning;

        void start() {
            if (turning) return;
            turning = true;
            turn.post(step);
        }

        void stop() {
            turning = false;
            turn.removeCallbacks(step);
        }

        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            float pad = paint.getStrokeWidth() / 2f;
            box.set(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad);
            c.drawArc(box, angle, 270f, false, paint);
        }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    void refresh() {
        if (busy) return;
        busy = true;
        startRing();
        new Thread(() -> {
            Snap s = read();
            ui.post(() -> { busy = false; stopRing(); render(s); });
        }).start();
    }

    // ---- showing ------------------------------------------------------------------------------------

    // Under 1000 MB shows as MB; from 1000 MB up shows as GB.
    static String gb(long kb) {
        long mb = kb / 1024;
        return mb < 1000 ? mb + " MB" : String.format(Locale.US, "%.2f GB", kb / 1048576.0);
    }

    void summary(Snap s) {
        long total = get(s.mi, "MemTotal"), avail = availKb(s.mi);
        if (total <= 0) return;
        int freePct = (int) (avail * 100 / total), usedPct = Math.max(0, Math.min(100, 100 - freePct));
        int color;
        if (freePct >= 25) { color = OK; headline.setText("Your TV is running well"); subline.setText("There is plenty of free memory. Nothing to do."); }
        else if (freePct >= 12) { color = WARN; headline.setText("Your TV is a bit full"); subline.setText("Free up memory to speed it up."); }
        else { color = BAD; headline.setText("Your TV is very full"); subline.setText("Press \"Free up memory\", or stop apps you are not using."); }
        headline.setTextColor(color);
        barFill.setBackground(round(color, 0, 0));
        ((LinearLayout.LayoutParams) barFill.getLayoutParams()).weight = usedPct;
        ((LinearLayout.LayoutParams) barRest.getLayoutParams()).weight = 100 - usedPct;
        barFill.requestLayout(); barRest.requestLayout();
        freeText.setText("Free memory: " + gb(avail) + " of " + gb(total));
    }

    void showTab(String t) {
        tab = t;
        memPage.setVisibility(t.equals("mem") ? View.VISIBLE : View.GONE);
        cpuPage.setVisibility(t.equals("cpu") ? View.VISIBLE : View.GONE);
        tabMem.setSelected(t.equals("mem"));
        tabCpu.setSelected(t.equals("cpu"));
        if (lastSnap != null) renderCpu(lastSnap);
    }

    static String cpuName(String n) {
        if (n.startsWith("kworker")) return "Android kernel work";
        if (n.startsWith("irq/")) return "Hardware signals";
        return n;
    }

    void renderCpu(Snap s) {
        if (s.problem != null) { cpuHead.setText("Not available yet. See the Memory tab."); cpuHead.setTextColor(WARN); cpuBody.removeAllViews(); return; }
        int p = s.cpuPct;
        if (p < 0) { cpuHead.setText("Checking the processor…"); cpuHead.setTextColor(FG); }
        else if (p < 50) { cpuHead.setText("Processor in use: " + p + "%. Running well."); cpuHead.setTextColor(OK); }
        else if (p < 80) { cpuHead.setText("Processor is busy: " + p + "%"); cpuHead.setTextColor(WARN); }
        else { cpuHead.setText("Processor is very busy: " + p + "%"); cpuHead.setTextColor(BAD); }
        cpuBody.removeAllViews();
        for (String[] row : s.cpuTop) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setMinimumHeight(dp(68));
            r.setPadding(dp(20), dp(8), dp(20), dp(8));
            r.setBackground(round(ROW, 0, 0));
            r.addView(oneLine(cpuName(row[1]), 22, FG, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView pc = oneLine(row[0] + "%", 22, DIM, true);
            pc.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            r.addView(pc, wrap());
            LinearLayout.LayoutParams lp = fullWidth();
            lp.bottomMargin = dp(8);
            cpuBody.addView(r, lp);
        }
    }

    void render(Snap s) {
        if (!loaded) {                       // the first reading has arrived: the ring leaves the button
            loaded = true;
            freeRing.stop();
            freeBtn.setCompoundDrawablesRelative(null, null, null, null);
        }
        lastSnap = s;
        renderCpu(s);
        if (armedPkg != null) return;   // never reset a Stop button that is waiting for its second OK
        summary(s);

        List<App> yours = new ArrayList<>(), other = new ArrayList<>(), need = new ArrayList<>();
        if (s.problem == null) for (App a : s.apps) (a.cls == 1 ? yours : a.cls == 2 ? other : need).add(a);

        if (lastStoppedPkg != null && s.problem == null && s.at > lastStoppedAt) {   // only trust readings taken after the stop
            for (App a : s.apps) if (a.pkg.equals(lastStoppedPkg)) {
                say(msg.getText() + " " + lastStoppedLabel + " started again by itself. Some apps do this.", WARN);
                break;
            }
            lastStoppedPkg = null;
        }

        StringBuilder sig = new StringBuilder(s.problem == null ? "ok" : "problem:" + s.problem);
        for (App a : yours) sig.append("|1:").append(a.pkg);
        for (App a : other) sig.append("|2:").append(a.pkg);
        sig.append("|need:").append(need.size()).append(showNeeded);
        if (showNeeded) for (App a : need) sig.append("|0:").append(a.pkg);

        if (sig.toString().equals(layoutSig)) {           // same rows as before: just update the numbers, no flicker
            for (App a : s.apps) { TextView t = memViews.get(a.pkg); if (t != null) t.setText(gb(a.kb)); }
            return;
        }

        // Remember where the remote's focus was so a rebuild does not throw the user back to the top.
        View f = getCurrentFocus();
        String focusKey = f != null && f.getTag() instanceof String ? (String) f.getTag() : null;
        int focusIdx = f == null ? -1 : focusOrder.indexOf(f);
        int scrollY = sv.getScrollY();

        body.removeAllViews();
        memViews.clear();
        focusOrder.clear();
        layoutSig = sig.toString();

        if (s.problem != null) {
            heading("One step needed");
            TextView t = text(s.problem, 24, WARN, false);
            t.setPadding(dp(8), dp(4), dp(8), dp(8));
            body.addView(t, fullWidth());
        } else {
            heading("Apps you use");
            if (yours.isEmpty()) note("None of your apps are running right now.");
            for (App a : yours) appRow(a, true);

            heading("Other apps running in the background");
            if (other.isEmpty()) note("Nothing else is running.");
            for (App a : other) appRow(a, true);

            heading("Needed by your TV");
            LinearLayout nr = new LinearLayout(this);
            nr.setGravity(Gravity.CENTER_VERTICAL);
            TextView nn = oneLine("Your TV needs these. Do not stop them.", 22, DIM, false);
            nr.addView(nn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Button toggle = button(showNeeded ? "Hide them" : "Show them (" + need.size() + ")", 22, 68, 220, BTN);
            toggle.setTag("toggle");
            toggle.setOnClickListener(v -> { showNeeded = !showNeeded; if (lastSnap != null) render(lastSnap); });
            nr.addView(toggle);
            focusOrder.add(toggle);
            body.addView(nr, fullWidth());
            if (showNeeded) for (App a : need) appRow(a, false);
        }
        View end = new View(this);
        body.addView(end, new LinearLayout.LayoutParams(1, dp(24)));

        // Put focus back on the same item, or the nearest one if it went away (for example after Stop).
        sv.scrollTo(0, scrollY);
        View target = null;
        if (focusKey != null) target = sv.findViewWithTag(focusKey);
        if (target == null && focusIdx >= 0 && !focusOrder.isEmpty()) target = focusOrder.get(Math.min(focusIdx, focusOrder.size() - 1));
        if (target == null && (f == null || f.getParent() == null || !f.isAttachedToWindow())) target = freeBtn;
        if (target != null) target.requestFocus();
    }

    void heading(String s) {
        TextView t = oneLine(s, 28, ACC, true);
        t.setPadding(0, dp(28), 0, dp(10));
        body.addView(t, fullWidth());
    }

    void note(String s) {
        TextView t = text(s, 22, DIM, false);
        t.setPadding(dp(8), 0, 0, dp(8));
        body.addView(t, fullWidth());
    }

    void appRow(App a, boolean canStop) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(68));
        row.setPadding(dp(20), dp(10), dp(16), dp(10));
        row.setBackground(focusBg(ROW, ROW_HI));
        row.setDuplicateParentStateEnabled(false);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (a.icon != null) iv.setImageDrawable(a.icon);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
        ilp.rightMargin = dp(16);
        row.addView(iv, ilp);

        TextView name = oneLine(a.label, 26, FG, true);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView mem = oneLine(gb(a.kb), 24, DIM, false);
        mem.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = dp(12); mlp.rightMargin = dp(16);
        row.addView(mem, mlp);
        memViews.put(a.pkg, mem);

        if (canStop) {
            Button stop = button("Stop", 22, 60, 120, STOP_BG);
            stop.setTag("stop:" + a.pkg);
            stop.setOnClickListener(new StopClick(this, a.pkg, a.label, a.kb));
            stop.setOnFocusChangeListener(new RowFocus(this, row));
            row.addView(stop);
            focusOrder.add(stop);
        } else {
            TextView t = oneLine("Needed by the TV", 22, DIM, false);
            row.addView(t, wrap());
            row.setFocusable(true);          // so the arrows can still scroll through this list
            row.setTag("need:" + a.pkg);
            focusOrder.add(row);
        }
        LinearLayout.LayoutParams lp = fullWidth();
        lp.bottomMargin = dp(10);
        body.addView(row, lp);
    }

    /** Lights up the whole row while its Stop button has focus; leaving the button cancels a half-done Stop. */
    // Static nested classes (not inner): newer javac writes a nameless constructor parameter for inner classes that d8 8.2 rejects.
    static final class RowFocus implements View.OnFocusChangeListener {
        final MainActivity act; final View row;
        RowFocus(MainActivity act, View row) { this.act = act; this.row = row; }
        @Override public void onFocusChange(View v, boolean has) {
            row.setSelected(has);
            if (!has && v == act.armedBtn) { act.disarm(); act.hint(); }
        }
    }

    // ---- stopping one app ---------------------------------------------------------------------------

    static final class StopClick implements View.OnClickListener {
        final MainActivity act; final String pkg, label; final long kb;
        StopClick(MainActivity act, String pkg, String label, long kb) { this.act = act; this.pkg = pkg; this.label = label; this.kb = kb; }
        @Override public void onClick(View v) {
            if (act.stopping) return;
            if (pkg.equals(act.armedPkg) && v == act.armedBtn) { act.disarm(); act.stopApp(pkg, label, kb); return; }
            act.disarm();
            act.arm((Button) v, pkg, label);
        }
    }

    void arm(Button b, String pkg, String label) {
        armedPkg = pkg; armedBtn = b;
        String shortName = label.length() > 16 ? label.substring(0, 15) + "…" : label;
        b.setText("Stop " + shortName + "? Press OK again to confirm");
        b.setBackground(focusBg(WARN, WARN));
        b.setTextColor(textOnFocus(DARK, DARK));
        say("Press OK again within 5 seconds to stop " + label + ". Move away to cancel.", WARN);
        ui.removeCallbacks(disarmLater);
        ui.postDelayed(disarmLater, CONFIRM_MS);
    }

    void disarm() {
        ui.removeCallbacks(disarmLater);
        if (armedBtn != null) {
            armedBtn.setText("Stop");
            armedBtn.setBackground(focusBg(STOP_BG, WHITE));
            armedBtn.setTextColor(textOnFocus(FG, DARK));
        }
        armedPkg = null; armedBtn = null;
    }

    void stopApp(String pkg, String label, long kb) {
        stopping = true;
        say("Stopping " + label + "…", DIM);
        new Thread(() -> {
            String err = null;
            if (!pkg.matches("[A-Za-z0-9._]+") || needed(pkg) || pkg.equals(getPackageName())) err = "This one is needed by your TV.";
            else {
                Adb adb = null;
                try {
                    adb = new Adb(this);
                    adb.connect();
                    String out = adb.shell("am force-stop " + pkg).toLowerCase(Locale.US);
                    if (out.contains("exception") || out.contains("error") || out.contains("denied")) err = "The TV said no.";
                } catch (Adb.NeedsAllow e) {
                    err = "Press Allow on the TV's pop-up box, then try again.";
                } catch (Exception e) {
                    err = "TV Monitor could not reach the TV's settings. Check that USB debugging is on.";
                } finally {
                    if (adb != null) adb.close();
                }
            }
            try { Thread.sleep(800); } catch (InterruptedException ignored) { }
            final String fe = err;
            ui.post(() -> {
                stopping = false;
                if (fe == null) {
                    say("Stopped " + label + ". It frees about " + gb(kb) + ".", OK);
                    lastStoppedPkg = pkg; lastStoppedLabel = label; lastStoppedAt = SystemClock.uptimeMillis();
                } else {
                    say("Could not stop " + label + ". " + fe, BAD);
                }
                refresh();
            });
        }).start();
    }

    // ---- freeing memory -----------------------------------------------------------------------------

    void freeUp() {
        if (freeing) return;
        disarm();
        freeing = true;
        freeBtn.setText("Freeing up…");
        say("Closing apps you are not using…", DIM);
        new Thread(() -> {
            long before = availKb(meminfo());
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<String> targets = new ArrayList<>();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                String p = ai.packageName;
                if (needed(p) || p.equals(getPackageName())) continue;
                boolean user = (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                if (launcherPkgs.contains(p) || user) targets.add(p);
            }
            // Same action as the Stop buttons (force-stop) through the loopback adb; the plain kill
            // call is the fallback when adb is not available.
            Adb adb = null;
            try {
                adb = new Adb(this);
                adb.connect();
                for (String p : targets) adb.shell("am force-stop " + p);
            } catch (Exception e) {
                for (String p : targets) am.killBackgroundProcesses(p);
            } finally {
                if (adb != null) adb.close();
            }
            try { Thread.sleep(2500); } catch (InterruptedException ignored) { }
            long after = availKb(meminfo());
            final String m = after > before
                ? "Done. Free memory went up from " + gb(before) + " to " + gb(after) + "."
                : "Done. There was nothing more to close. Free memory: " + gb(after) + ".";
            ui.post(() -> { freeing = false; freeBtn.setText("Free up memory"); say(m, OK); refresh(); });
        }).start();
    }
}
