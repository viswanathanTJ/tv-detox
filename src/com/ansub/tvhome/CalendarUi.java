package com.ansub.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

// The Tamil calendar pages, laid out like the approved mock (docs/mockups/tamil-calendar-*-v2.png):
// a month of days with their Tamil dates and observances, and a day page with its panchangam, good
// and bad times, Soolam and Chandrashtamam. Everything comes from Panchang and the holiday list, so it
// works offline. Opened from the Tamil date and festival tiles on the home screen.
final class CalendarUi {
    private static final int CARD = WeatherUi.CARD, CARD2 = WeatherUi.CARD2, TEXT = WeatherUi.TEXT,
            DIM = WeatherUi.DIM, GOLD = WeatherUi.GOLD, PAGE = WeatherUi.PAGE, BUTTON = WeatherUi.BUTTON;
    private static final int SUNDAY = 0xFFFF8A8A, FEST_BG = 0xFF3A2A10, FEST_EDGE = 0xFF6B4D16,
            GOOD = 0xFF7EE2A8, BAD = 0xFFFF7A7A, WARN = 0xFFFFB35C, LINE = 0x12FFFFFF;
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};

    private final Activity a;
    private final float scale;
    private final Typeface regular, semibold, bold, tamil, tamilBold;
    private Dialog monthDialog, dayDialog;
    private View[] monthCells = new View[32]; // the shown month's days, by day number
    private final java.util.Map<String, Bitmap> icons = new java.util.HashMap<>();

    CalendarUi(Activity a, float scale, Typeface regular, Typeface semibold) {
        this.a = a;
        this.scale = scale;
        this.regular = regular;
        this.semibold = semibold;
        bold = Typeface.create("sans-serif", Typeface.BOLD);
        // Tamil lines use the system font, which always has Tamil (some TV fonts don't).
        tamil = Typeface.create("sans-serif", Typeface.NORMAL);
        tamilBold = Typeface.create("sans-serif", Typeface.BOLD);
        load(a);
    }

    // The Vakya data for Panchang, read once (it's small: a few years of days).
    static void load(Activity a) {
        try {
            Panchang.load(new java.io.InputStreamReader(a.getAssets().open("panchang-vakya.csv"), "UTF-8"));
        } catch (Exception ignored) {
            // No data: every date is computed instead.
        }
    }

    // ---- Month ----

    void openMonth() {
        int[] now = Weather.now();
        openMonth(now[0], now[1], now[2]);
    }

    // The month of (y, m), with focus on day d (or the last day if the month is shorter).
    private void openMonth(int y, int m, int d) {
        if (monthDialog == null) {
            monthDialog = fullScreen();
            monthDialog.getWindow().setBackgroundDrawable(new ColorDrawable(PAGE));
        }
        final int[] shown = {y, m};
        monthDialog.setOnKeyListener((di, code, ev) -> {
            int step = code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_PAGE_DOWN ? 1
                    : code == KeyEvent.KEYCODE_CHANNEL_DOWN || code == KeyEvent.KEYCODE_PAGE_UP ? -1 : 0;
            if (code == KeyEvent.KEYCODE_ESCAPE) {
                if (ev.getAction() == KeyEvent.ACTION_UP) di.dismiss();
                return true;
            }
            if (step == 0) return false;
            if (ev.getAction() == KeyEvent.ACTION_UP) {
                int[] t = addMonths(shown[0], shown[1], step);
                openMonth(t[0], t[1], 1);
            }
            return true;
        });
        shown[0] = y;
        shown[1] = m;
        View focus = renderMonth(y, m, d);
        if (!monthDialog.isShowing()) monthDialog.show();
        if (focus != null) focus.requestFocus();
    }

    private View renderMonth(final int y, final int m, int focusDay) {
        int[] now = Weather.now();
        int days = daysIn(y, m);
        focusDay = Math.max(1, Math.min(focusDay, days));
        LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(96), px(40), px(96), px(24));
        page.setClipChildren(false);
        page.setClipToPadding(false);

        // Header: the month, its Tamil months and year, and the month buttons.
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);
        TextView title = line(72, TEXT, bold);
        title.setText(MONTHS[m - 1] + " " + y);
        head.addView(title, new LinearLayout.LayoutParams(-2, -1));
        Panchang.Day first = Panchang.of(y, m, 1), last = Panchang.of(y, m, days);
        LinearLayout sub = new LinearLayout(a);
        sub.setOrientation(LinearLayout.VERTICAL);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        TextView months = line(34, GOLD, tamil);
        months.setText(first.tamil.monthTa() + (first.tamil.month != last.tamil.month ? " – " + last.tamil.monthTa() : ""));
        sub.addView(months, new LinearLayout.LayoutParams(-2, px(48)));
        TextView year = line(27, DIM, tamil);
        year.setText(first.yearTa + " வருடம்"
                + (first.yearTa.equals(last.yearTa) ? "" : " – " + last.yearTa + " வருடம்"));
        sub.addView(year, new LinearLayout.LayoutParams(-2, px(42)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -1, 1f);
        slp.leftMargin = px(36);
        head.addView(sub, slp);
        int[] prev = addMonths(y, m, -1), next = addMonths(y, m, 1);
        View back = pill("◀ " + MONTHS[prev[1] - 1].substring(0, 3));
        back.setOnClickListener(v -> openMonth(prev[0], prev[1], 1));
        View today = pill("Today");
        today.setOnClickListener(v -> openMonth(now[0], now[1], now[2]));
        View fwd = pill(MONTHS[next[1] - 1].substring(0, 3) + " ▶");
        fwd.setOnClickListener(v -> openMonth(next[0], next[1], 1));
        head.addView(back, pillParams(0));
        head.addView(today, pillParams(px(14)));
        head.addView(fwd, pillParams(px(14)));
        page.addView(head, new LinearLayout.LayoutParams(-1, px(110)));

        // Weekday names, Sunday first.
        LinearLayout names = new LinearLayout(a);
        for (int i = 0; i < 7; i++) {
            TextView t = line(28, i == 0 ? SUNDAY : DIM, tamil);
            t.setGravity(Gravity.CENTER);
            t.setText(TamilDate.WEEKDAYS_TA[i]);
            names.addView(t, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, px(48));
        nlp.topMargin = px(10);
        page.addView(names, nlp);

        // The days, a week to a row.
        int lead = TamilDate.weekday(y, m, 1);
        int rows = (lead + days + 6) / 7;
        int cellH = rows == 6 ? 118 : 134, gap = 14;
        View focus = null;
        monthCells = new View[32];
        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(a);
            row.setClipChildren(false);
            for (int c = 0; c < 7; c++) {
                int day = r * 7 + c - lead + 1;
                View cell = day >= 1 && day <= days
                        ? dayCell(y, m, day, c == 0, now[0] == y && now[1] == m && now[2] == day, cellH)
                        : new View(a);
                if (day >= 1 && day <= days) monthCells[day] = cell;
                if (day == focusDay) focus = cell;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                if (c > 0) lp.leftMargin = px(gap);
                row.addView(cell, lp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, px(cellH));
            if (r > 0) rlp.topMargin = px(gap);
            grid.addView(row, rlp);
        }
        page.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        // How to use it, and what the marks mean.
        LinearLayout foot = new LinearLayout(a);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView hint = line(25, DIM, regular);
        hint.setText("◀ ▲ ▼ ▶ move (past the ends: next or last month)  ·  OK open the day  ·  Back home");
        foot.addView(hint, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView legend = line(25, DIM, regular);
        legend.setText("● Pournami   ○ Amavasai   ■ Festival");
        foot.addView(legend, new LinearLayout.LayoutParams(-2, -1));
        page.addView(foot, new LinearLayout.LayoutParams(-1, px(60)));

        monthDialog.setContentView(page);
        monthDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        return focus;
    }

    // One day: the date, its Tamil date at the top right, and its festival or observance at the bottom.
    private View dayCell(final int y, final int m, final int d, boolean sunday, boolean today, int h) {
        Panchang.Day p = Panchang.of(y, m, d);
        String fest = Holidays.on(a, y, m, d);
        boolean festival = fest != null;
        FrameLayout c = new FrameLayout(a);
        c.setPadding(px(16), px(10), px(14), px(8));
        final GradientDrawable bg = rounded(festival ? FEST_BG : today ? CARD2 : CARD, 18, 0, 0);
        c.setBackground(bg);

        TextView en = line(50, sunday ? SUNDAY : TEXT, bold);
        en.setGravity(Gravity.TOP);
        en.setIncludeFontPadding(false);
        en.setText(String.valueOf(d));
        c.addView(en, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        TextView tn = line(p.tamil.day == 1 ? 18 : 26, p.tamil.day == 1 ? 0xFF1A1200 : GOLD,
                p.tamil.day == 1 ? tamilBold : semibold);
        if (p.tamil.day == 1) {
            tn.setText(p.tamil.monthTa() + " 1");
            tn.setPadding(px(6), 0, px(6), 0); // narrow, so it clears the date
            tn.setBackground(rounded(GOLD, 8, 0, 0));
        } else {
            tn.setText(String.valueOf(p.tamil.day));
        }
        c.addView(tn, new FrameLayout.LayoutParams(-2, px(34), Gravity.TOP | Gravity.END));

        Drawable pic = icon(iconKey(fest, p));
        if (pic != null) {
            ImageView iv = new ImageView(a);
            iv.setImageDrawable(pic);
            // Big, in the free space between the date and the Tamil day; under the month badge on day 1.
            boolean badge = p.tamil.day == 1;
            FrameLayout.LayoutParams lp = badge
                    ? new FrameLayout.LayoutParams(px(52), px(52), Gravity.TOP | Gravity.END)
                    : new FrameLayout.LayoutParams(px(70), px(70), Gravity.TOP | Gravity.START);
            if (badge) lp.topMargin = px(36);
            else lp.leftMargin = px(66);
            c.addView(iv, lp);
        }
        String ev = festival ? Holidays.tamilName(fest) : p.observances.isEmpty() ? null : mark(p.observances.get(0));
        if (ev != null) {
            TextView t = line(23, festival ? GOLD : TEXT, festival ? tamilBold : tamil);
            t.setText(ev);
            c.addView(t, new FrameLayout.LayoutParams(-1, px(36), Gravity.BOTTOM | Gravity.START));
        }

        c.setFocusable(true);
        c.setOnFocusChangeListener((v, has) -> {
            bg.setStroke(has ? px(5) : 0, Color.WHITE);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).translationZ(has ? px(20) : 0)
                    .setDuration(120).start();
        });
        c.setOnClickListener(v -> openDay(y, m, d));
        // Arrows walk the days: left and right one day, up and down a week. Past the first or last day
        // they go on into the last or next month, so the remote alone can page through the months.
        c.setOnKeyListener((v, code, key) -> {
            int step = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : code == KeyEvent.KEYCODE_DPAD_LEFT ? -1
                    : code == KeyEvent.KEYCODE_DPAD_DOWN ? 7 : code == KeyEvent.KEYCODE_DPAD_UP ? -7 : 0;
            if (step == 0) return false;
            if (key.getAction() == KeyEvent.ACTION_DOWN) {
                int[] t = TamilDate.civil(TamilDate.epochDay(y, m, d) + step);
                if (t[0] == y && t[1] == m && monthCells[t[2]] != null) monthCells[t[2]].requestFocus();
                else openMonth(t[0], t[1], t[2]);
            }
            return true;
        });
        return c;
    }

    // ---- Icons (assets/calendar-icons, drawn for this app; sources in tools/calendar-icons) ----

    // Holiday list names to icons; the first match wins.
    private static final String[][] FESTIVAL_ICONS = {
        {"Deepavali", "deepavali"}, {"Naraka Chaturdasi", "deepavali"}, {"Dussehra", "vijayadasami"},
        {"Maha Navami", "saraswati_pooja"}, {"Navratri", "navarathri"}, {"Durga Puja", "navarathri"},
        {"Maha Ashtami", "navarathri"}, {"Maha Saptami", "navarathri"}, {"Ganesh Chaturthi", "vinayagar_chaturthi"},
        {"Janmashtami", "krishna_jayanthi"}, {"Maha Shivaratri", "maha_sivarathri"}, {"Pongal", "pongal"},
        {"Makar Sankranti", "pongal"}, {"Mesadi", "tamil_new_year"}, {"Ramzan", "ramzan"}, {"Ramadan", "ramzan"},
        {"Bakrid", "bakrid"}, {"Christmas", "christmas"}, {"Gandhi Jayanti", "gandhi_jayanthi"},
        {"Republic Day", "republic_day"}, {"Independence Day", "independence_day"},
    };

    // The day's icon: its festival's, else its first observance that has one (or null).
    private static String iconKey(String festival, Panchang.Day p) {
        if (festival != null) {
            for (String[] f : FESTIVAL_ICONS) if (festival.contains(f[0])) return f[1];
            return "generic_festival";
        }
        for (Panchang.Observance o : p.observances) {
            if (Panchang.THEI_SHASHTI.equals(o.key)) return "shashti";
            if (!Panchang.THIRUVATHIRAI.equals(o.key) && !Panchang.KARI_NAAL.equals(o.key)
                    && !Panchang.VASTHU_NAAL.equals(o.key)) return o.key;
        }
        return null;
    }

    private Drawable icon(String key) {
        if (key == null) return null;
        if (!icons.containsKey(key)) {
            Bitmap b = null;
            try (java.io.InputStream in = a.getAssets().open("calendar-icons/" + key + ".png")) {
                b = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) {
                // No icon for it: the day just shows its name.
            }
            icons.put(key, b);
        }
        Bitmap b = icons.get(key);
        return b == null ? null : new BitmapDrawable(a.getResources(), b);
    }

    private static String mark(Panchang.Observance o) {
        if (Panchang.POURNAMI.equals(o.key)) return "● " + o.ta;
        if (Panchang.AMAVASAI.equals(o.key)) return "○ " + o.ta;
        return o.ta;
    }

    // ---- Day ----

    void openDay(int y, int m, int d) {
        if (dayDialog == null) {
            dayDialog = fullScreen();
            dayDialog.getWindow().setBackgroundDrawable(new ColorDrawable(PAGE));
        }
        final long ed = TamilDate.epochDay(y, m, d);
        dayDialog.setOnKeyListener((di, code, ev) -> {
            if (code == KeyEvent.KEYCODE_ESCAPE) {
                if (ev.getAction() == KeyEvent.ACTION_UP) di.dismiss();
                return true;
            }
            int step = code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_PAGE_DOWN ? 1
                    : code == KeyEvent.KEYCODE_CHANNEL_DOWN || code == KeyEvent.KEYCODE_PAGE_UP ? -1 : 0;
            if (step == 0) return false;
            if (ev.getAction() == KeyEvent.ACTION_UP) {
                int[] t = TamilDate.civil(ed + step);
                openDay(t[0], t[1], t[2]);
            }
            return true;
        });
        View focus = renderDay(y, m, d);
        if (!dayDialog.isShowing()) dayDialog.show();
        if (focus != null) focus.requestFocus();
    }

    private View renderDay(final int y, final int m, final int d) {
        final Panchang.Day p = Panchang.of(y, m, d);
        LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(96), px(56), px(96), px(36));
        page.setClipChildren(false);
        page.setClipToPadding(false);

        LinearLayout main = new LinearLayout(a);
        main.setClipChildren(false);
        page.addView(main, new LinearLayout.LayoutParams(-1, 0, 1f));
        main.addView(dayLeft(p), new LinearLayout.LayoutParams(px(500), -1));

        LinearLayout right = new LinearLayout(a);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1f);
        rlp.leftMargin = px(28);
        main.addView(right, rlp);
        LinearLayout panels = new LinearLayout(a);
        right.addView(panels, new LinearLayout.LayoutParams(-1, 0, 1f));
        panels.addView(panchangPanel(p), new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -1, 1f);
        tlp.leftMargin = px(28);
        panels.addView(timesPanel(p), tlp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, px(134));
        clp.topMargin = px(26);
        right.addView(chips(p), clp);

        // Previous day, the month, next day; and where the numbers come from.
        LinearLayout foot = new LinearLayout(a);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setClipChildren(false);
        final int[] prev = TamilDate.civil(p.epochDay - 1), next = TamilDate.civil(p.epochDay + 1);
        View back = pill("◀ " + prev[2] + " " + MONTHS[prev[1] - 1].substring(0, 3));
        back.setOnClickListener(v -> openDay(prev[0], prev[1], prev[2]));
        View month = pill("Month");
        month.setOnClickListener(v -> {
            dayDialog.dismiss();
            openMonth(y, m, d);
        });
        View fwd = pill(next[2] + " " + MONTHS[next[1] - 1].substring(0, 3) + " ▶");
        fwd.setOnClickListener(v -> openDay(next[0], next[1], next[2]));
        foot.addView(back, pillParams(0));
        foot.addView(month, pillParams(px(14)));
        foot.addView(fwd, pillParams(px(14)));
        TextView src = line(23, DIM, regular);
        src.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        src.setText(p.vakya ? "வாக்கிய பஞ்சாங்கம் · Vakya, as printed calendars"
                : "Computed for " + Weather.PLACE + " · times may differ from printed calendars");
        foot.addView(src, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, px(70));
        flp.topMargin = px(26);
        page.addView(foot, flp);

        dayDialog.setContentView(page);
        dayDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        return fwd;
    }

    // The big date, weekday, Tamil date and year; the day's festival; sunrise and sunset.
    private View dayLeft(Panchang.Day p) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView big = line(220, TEXT, bold);
        big.setIncludeFontPadding(false);
        big.setLetterSpacing(-0.03f);
        big.setText(String.valueOf(p.d));
        col.addView(big, new LinearLayout.LayoutParams(-1, px(236))); // room for the digits' full height
        TextView wk = line(40, TEXT, semibold);
        wk.setText(TamilDate.WEEKDAYS_EN[p.weekday] + ", " + MONTHS[p.m - 1] + " " + p.y);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, px(56));
        wlp.topMargin = px(4);
        col.addView(wk, wlp);
        TextView ta = line(38, GOLD, tamilBold); // fits the longest weekday, வியாழன்
        ta.setText(p.tamil.ta() + " · " + TamilDate.WEEKDAYS_TA[p.weekday]);
        col.addView(ta, new LinearLayout.LayoutParams(-1, px(58)));
        TextView yr = text(24, DIM, tamil);
        yr.setText(p.yearTa + " வருடம் · " + p.ayanamTa + "\n" + p.ruthuTa + " ருது · "
                + (p.valarpirai ? "வளர்பிறை" : "தேய்பிறை"));
        yr.setLineSpacing(0, 1.25f);
        col.addView(yr, new LinearLayout.LayoutParams(-1, -2));
        col.addView(new View(a), new LinearLayout.LayoutParams(-1, 0, 1f));

        // Today's festivals and observances, if any.
        String fest = Holidays.on(a, p.y, p.m, p.d);
        StringBuilder ta2 = new StringBuilder(), en2 = new StringBuilder();
        if (fest != null) {
            ta2.append(Holidays.tamilName(fest));
            en2.append(fest);
        }
        for (Panchang.Observance o : p.observances) {
            if (ta2.length() > 0) { ta2.append(" · "); en2.append(" · "); }
            ta2.append(o.ta);
            en2.append(o.en);
        }
        if (p.vasthu != null) en2.append(" · pooja ").append(Panchang.range(p.vasthu[0], p.vasthu[1]));
        if (ta2.length() > 0) {
            LinearLayout card = new LinearLayout(a);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(px(20), px(14), px(26), px(14));
            card.setBackground(rounded(FEST_BG, 22, FEST_EDGE, 2));
            Drawable pic = icon(iconKey(fest, p));
            if (pic != null) {
                ImageView iv = new ImageView(a);
                iv.setImageDrawable(pic);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(116), px(116));
                lp.rightMargin = px(18);
                card.addView(iv, lp);
            }
            LinearLayout words = new LinearLayout(a);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = text(31, GOLD, tamilBold); // two lines when a day has several
            t1.setMaxLines(2);
            t1.setEllipsize(TextUtils.TruncateAt.END);
            t1.setLineSpacing(0, 0.95f);
            t1.setText(ta2);
            words.addView(t1, new LinearLayout.LayoutParams(-1, -2));
            TextView t2 = line(25, DIM, regular);
            t2.setText(en2);
            words.addView(t2, new LinearLayout.LayoutParams(-1, px(36)));
            card.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
            col.addView(card, new LinearLayout.LayoutParams(-1, px(144)));
        }

        LinearLayout sun = new LinearLayout(a);
        sun.addView(stat("Sunrise", Panchang.clock(p, p.sunriseJd)), new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -1, 1f);
        sp.leftMargin = px(18);
        sun.addView(stat("Sunset", Panchang.clock(p, p.sunsetJd)), sp);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, px(96));
        slp.topMargin = px(18);
        col.addView(sun, slp);
        return col;
    }

    private View stat(String label, String value) {
        LinearLayout s = new LinearLayout(a);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setPadding(px(20), 0, px(20), 0);
        s.setBackground(rounded(CARD, 18, 0, 0));
        TextView k = line(25, DIM, regular);
        k.setText(label);
        s.addView(k, new LinearLayout.LayoutParams(-1, px(34)));
        TextView v = line(32, TEXT, semibold);
        v.setText(value);
        s.addView(v, new LinearLayout.LayoutParams(-1, px(42)));
        return s;
    }

    // Tithi, nakshatram, yogam, karanam and the moon's sign, each with when it ends and what follows.
    private View panchangPanel(Panchang.Day p) {
        LinearLayout panel = panel("பஞ்சாங்கம் · PANCHANGAM");
        // Valarpirai or theipirai is in the left column; as a tag here it ran out of room on long tithis.
        addRow(panel, "திதி", Panchang.TITHI_TA[p.tithi.index], null,
                until(p, p.tithi.endJd, Panchang.TITHI_TA[p.tithi.next]));
        addRow(panel, "நட்சத்திரம்", Panchang.NAKSHATRA_TA[p.nakshatra.index], null,
                until(p, p.nakshatra.endJd, Panchang.NAKSHATRA_TA[p.nakshatra.next]));
        addRow(panel, "யோகம்", Panchang.YOGA_TA[p.yoga.index], null,
                until(p, p.yoga.endJd, Panchang.YOGA_TA[p.yoga.next]));
        addRow(panel, "கரணம்", Panchang.KARANA_TA[p.karana.index], null,
                until(p, p.karana.endJd, Panchang.KARANA_TA[p.karana.next]));
        addRow(panel, "ராசி", Panchang.RASI_TA[p.moonRasi], null, p.moonRasiNext < 0
                ? "Moon in " + Panchang.RASI_EN[p.moonRasi] + " all day"
                : until(p, p.moonRasiEndJd, Panchang.RASI_TA[p.moonRasiNext]));
        return panel;
    }

    private static String until(Panchang.Day p, double jd, String next) {
        return "till " + Panchang.clock(p, jd) + ", then " + next;
    }

    private void addRow(LinearLayout panel, String key, String value, String tag, String below) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView k = line(26, DIM, tamil);
        k.setText(key);
        row.addView(k, new LinearLayout.LayoutParams(px(176), -2));
        LinearLayout v = new LinearLayout(a);
        v.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView main = line(34, TEXT, tamilBold);
        main.setText(value);
        top.addView(main, new LinearLayout.LayoutParams(-2, px(48)));
        if (tag != null) {
            TextView t = line(24, GOLD, tamil);
            t.setText(tag);
            t.setPadding(px(10), 0, px(10), 0);
            t.setBackground(rounded(0x22FFD166, 8, 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, px(34));
            lp.leftMargin = px(12);
            top.addView(t, lp);
        }
        v.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView b = line(25, DIM, tamil);
        b.setText(below);
        v.addView(b, new LinearLayout.LayoutParams(-1, px(36)));
        row.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
        addDivided(panel, row);
    }

    // Good times in green, Raagu kalam and Yamagandam in red, Kuligai in orange.
    private View timesPanel(Panchang.Day p) {
        LinearLayout panel = panel("நேரம் · TIMES TODAY");
        int w = p.weekday;
        int[] n = Panchang.NALLA[w], g = Panchang.GOWRI[w];
        addTime(panel, GOOD, "நல்ல நேரம்", "Nalla neram", Panchang.range(n[0], n[1]), Panchang.range(n[2], n[3]));
        addTime(panel, GOOD, "கௌரி நேரம்", "Gowri nalla neram", g[0] < 0 ? Panchang.range(g[2], g[3])
                : Panchang.range(g[0], g[1]), g[0] < 0 ? null : Panchang.range(g[2], g[3]));
        addTime(panel, BAD, "இராகு காலம்", "Raagu kalam", Panchang.range(Panchang.RAAGU[w][0], Panchang.RAAGU[w][1]), null);
        addTime(panel, BAD, "எமகண்டம்", "Yamagandam", Panchang.range(Panchang.YAMA[w][0], Panchang.YAMA[w][1]), null);
        addTime(panel, WARN, "குளிகை", "Kuligai", Panchang.range(Panchang.KULIGAI[w][0], Panchang.KULIGAI[w][1]), null);
        return panel;
    }

    private void addTime(LinearLayout panel, int color, String ta, String en, String first, String second) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(a);
        bar.setBackground(rounded(color, 6, 0, 0));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(px(12), px(52));
        blp.rightMargin = px(20);
        row.addView(bar, blp);
        LinearLayout names = new LinearLayout(a);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView t = line(26, TEXT, tamil);
        t.setText(ta);
        names.addView(t, new LinearLayout.LayoutParams(-1, px(38)));
        TextView e = line(22, DIM, regular);
        e.setText(en);
        names.addView(e, new LinearLayout.LayoutParams(-1, px(30)));
        row.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout times = new LinearLayout(a);
        times.setOrientation(LinearLayout.VERTICAL);
        times.setGravity(Gravity.END);
        TextView v = line(30, color, bold);
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        v.setText(first);
        times.addView(v, new LinearLayout.LayoutParams(-2, px(40)));
        if (second != null) {
            TextView v2 = line(24, color, semibold);
            v2.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            v2.setText(second);
            times.addView(v2, new LinearLayout.LayoutParams(-2, px(32)));
        }
        row.addView(times, new LinearLayout.LayoutParams(-2, -2));
        addDivided(panel, row);
    }

    // Soolam and its remedy; whose Chandrashtamam it is; the moon's star at sunrise and next.
    private View chips(Panchang.Day p) {
        LinearLayout row = new LinearLayout(a);
        row.addView(chip("சூலம் · Soolam", Panchang.SOOLAM_TA[p.weekday],
                "பரிகாரம்: " + Panchang.PARIGARAM_TA[p.weekday]), new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.leftMargin = px(22);
        row.addView(chip("சந்திராஷ்டமம்", Panchang.RASI_TA[p.chandrashtamam], starsOf(p.chandrashtamam)), lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -1, 1f);
        lp2.leftMargin = px(22);
        // This month's Kari naal and Vasthu days, as the printed sheet lists them.
        StringBuilder kari = new StringBuilder(), vasthu = new StringBuilder();
        long first = TamilDate.epochDay(p.y, p.m, 1);
        for (long ed = first; TamilDate.civil(ed)[1] == p.m; ed++) {
            int[] c = TamilDate.civil(ed);
            Panchang.Day q = ed == p.epochDay ? p : Panchang.of(c[0], c[1], c[2]);
            for (Panchang.Observance o : q.observances) {
                if (Panchang.KARI_NAAL.equals(o.key)) kari.append(kari.length() > 0 ? ", " : "").append(c[2]);
                if (Panchang.VASTHU_NAAL.equals(o.key)) vasthu.append(vasthu.length() > 0 ? ", " : "").append(c[2]);
            }
        }
        row.addView(chip("இம்மாதம் · This month", kari.length() > 0 ? "கரி நாள் " + kari : "கரி நாள் இல்லை",
                vasthu.length() > 0 ? "வாஸ்து நாள் " + vasthu : ""), lp2);
        return row;
    }

    // The stars of a sign, with the quarters (padam) when only some of a star is in it:
    // "புனர்பூசம் 4, பூசம், ஆயில்யம்".
    static String starsOf(int rasi) {
        StringBuilder out = new StringBuilder();
        int from = rasi * 9, to = from + 9; // quarters, 4 to a star, 9 to a sign
        for (int star = from / 4; star * 4 < to; star++) {
            int a = Math.max(from, star * 4), b = Math.min(to, star * 4 + 4);
            if (out.length() > 0) out.append(", ");
            out.append(Panchang.NAKSHATRA_TA[star]);
            if (b - a < 4) {
                out.append(' ');
                for (int q = a; q < b; q++) out.append(q == a ? "" : ",").append(q - star * 4 + 1);
            }
        }
        return out.toString();
    }

    private View chip(String label, String value, String below) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(px(24), 0, px(24), 0);
        c.setBackground(rounded(CARD, 20, 0, 0));
        TextView k = line(23, DIM, tamil);
        k.setText(label);
        c.addView(k, new LinearLayout.LayoutParams(-1, px(30)));
        TextView v = line(30, TEXT, tamilBold);
        v.setText(value);
        c.addView(v, new LinearLayout.LayoutParams(-1, px(44)));
        TextView b = line(24, DIM, tamil);
        b.setText(below);
        c.addView(b, new LinearLayout.LayoutParams(-1, px(34)));
        return c;
    }

    // ---- Small helpers ----

    private LinearLayout panel(String title) {
        LinearLayout p = new LinearLayout(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(px(30), px(22), px(30), px(16));
        p.setBackground(rounded(CARD, 24, 0, 0));
        TextView t = line(23, DIM, tamil);
        t.setLetterSpacing(0.08f);
        t.setText(title);
        p.addView(t, new LinearLayout.LayoutParams(-1, px(36)));
        return p;
    }

    // Rows share the panel's height evenly, with a hairline between them.
    private void addDivided(LinearLayout panel, View row) {
        if (panel.getChildCount() > 1) {
            View rule = new View(a);
            rule.setBackgroundColor(LINE);
            panel.addView(rule, new LinearLayout.LayoutParams(-1, Math.max(1, px(1))));
        }
        panel.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private Dialog fullScreen() {
        return new Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen);
    }

    private TextView pill(String label) {
        TextView b = line(26, DIM, regular);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(28), 0, px(28), 0);
        final GradientDrawable bg = rounded(BUTTON, 40, 0x1CFFFFFF, 2);
        b.setBackground(bg);
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : BUTTON);
            ((TextView) v).setTextColor(has ? 0xFF0B1020 : DIM);
        });
        return b;
    }

    private LinearLayout.LayoutParams pillParams(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, px(60));
        lp.leftMargin = leftMargin;
        return lp;
    }

    private GradientDrawable rounded(int color, int radius, int stroke, int strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radius));
        if (strokeW > 0) g.setStroke(Math.max(1, px(strokeW)), stroke);
        return g;
    }

    private TextView line(int size, int color, Typeface tf) {
        TextView t = text(size, color, tf);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView text(int size, int color, Typeface tf) {
        TextView t = new TextView(a);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private int px(int v) {
        return Math.round(v * scale);
    }

    private static int daysIn(int y, int m) {
        int[] next = addMonths(y, m, 1);
        return (int) (TamilDate.epochDay(next[0], next[1], 1) - TamilDate.epochDay(y, m, 1));
    }

    private static int[] addMonths(int y, int m, int n) {
        int i = y * 12 + (m - 1) + n;
        return new int[] {i / 12, i % 12 + 1};
    }
}
