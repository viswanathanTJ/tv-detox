package com.ansub.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
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
import android.widget.TextClock;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

// The home screen's top half: the clock with the English and Tamil date, the rain banner, five day
// cards and the two info tiles (next festival, next Tamil day). A day card opens that day hour by
// hour; from there (or the day picker) the full-day page. Both are full-screen dialogs: Back closes.
// Everything here is designed at 1920x1080 and scaled by px(), like MainActivity.
// Anything that fails (no forecast, no holiday list) shows a short line instead.
final class WeatherUi {
    // Colours from the approved mockup.
    static final int CARD = 0xFF1A2742, CARD2 = 0xFF22335A, WET_BG = 0xFF16335F, RAIN = 0xFF2F7DF6,
            RAIN_BG = 0xFF12315E, DIM = 0xFFB8C4E0, TEXT = 0xFFF4F7FF, RAIN_TEXT = 0xFF8DBBFF, DRY_TEXT = 0xFF7EE2A8, GOLD = 0xFFFFD166,
            BUTTON = 0xFF2A3A5F, PAGE = 0xFF070C16, BAR = 0xFF2A3A5F, BAR_MID = 0xFF4A6FB0;

    // Where things sit on the home screen (design pixels).
    static final int HEAD_TOP = 30, HEAD_H = 140, BANNER_TOP = 180, BANNER_H = 60, CARDS_TOP = 180, CARDS_H = 228;
    static final int BELOW = 440; // the first row under the weather

    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};
    private static final String[] PERIODS = {"Morning", "Afternoon", "Evening", "Night"};
    private static final String[] PERIOD_TIMES = {"6 am – 12 pm", "12 – 6 pm", "6 pm – 12 am", "12 – 6 am"};
    private static final int[][] PERIOD_HOURS = {{6, 12}, {12, 18}, {18, 24}, {0, 6}};

    interface OnPick { void at(int which); }

    private final Activity a;
    private final float scale;
    private final Typeface regular, semibold, tamil, tamilBold;

    private TextView dateEn, dateTa, banner, festName, festWhen, tamilName, tamilWhen;
    private LinearLayout cards;
    private Weather.Forecast forecast;
    private Dialog hoursDialog, dayDialog;

    WeatherUi(Activity a, float scale, Typeface regular, Typeface semibold) {
        this.a = a;
        this.scale = scale;
        this.regular = regular;
        this.semibold = semibold;
        // Tamil lines use the system font, which always has Tamil (some TV fonts don't).
        tamil = Typeface.create("sans-serif", Typeface.NORMAL);
        tamilBold = Typeface.create("sans-serif", Typeface.BOLD);
    }

    // ---- Home screen ----

    // Big time on the left; the English date and the Tamil weekday and date beside it.
    void addHeader(FrameLayout root, int x) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextClock time = new TextClock(a);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(112));
        time.setTextColor(Color.WHITE);
        time.setTypeface(semibold);
        time.setLetterSpacing(-0.028f);
        time.setFontFeatureSettings("tnum");
        time.setIncludeFontPadding(false);
        time.setSingleLine(true);
        row.addView(time, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        dateEn = line(38, TEXT, semibold);
        dateTa = line(32, GOLD, tamil);
        col.addView(dateEn, new LinearLayout.LayoutParams(px(900), px(54)));
        col.addView(dateTa, new LinearLayout.LayoutParams(px(900), px(52)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.leftMargin = px(40);
        row.addView(col, cp);
        root.addView(row, at(x, px(HEAD_TOP), -2, px(HEAD_H)));
    }

    // The rain banner and the row of day cards, w wide.
    void addWeather(FrameLayout root, int x, int w) {
        banner = line(28, TEXT, semibold);
        banner.setPadding(px(24), 0, px(24), 0);
        banner.setCompoundDrawablePadding(px(18));
        root.addView(banner, at(x, px(BANNER_TOP), w, px(BANNER_H)));

        cards = new LinearLayout(a);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        cards.setClipToPadding(false);
        root.addView(cards, at(x, px(CARDS_TOP), w, px(CARDS_H)));
        showForecast(null);
    }

    // Next festival or holiday, from the bundled list. View only.
    View festivalTile() {
        festName = line(30, TEXT, semibold);
        festWhen = line(26, DIM, regular);
        return infoTile(festName, festWhen);
    }

    // Next Amavasai, Pournami or Tamil month start. View only.
    View tamilTile() {
        tamilName = line(30, GOLD, tamilBold);
        tamilWhen = line(26, DIM, regular);
        return infoTile(tamilName, tamilWhen);
    }

    private View infoTile(TextView top, TextView bottom) {
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(px(28), 0, px(28), 0);
        t.setBackground(rounded(CARD, 20, 0, 0));
        t.addView(top, new LinearLayout.LayoutParams(-1, px(46)));
        t.addView(bottom, new LinearLayout.LayoutParams(-1, px(40)));
        return t;
    }

    // Date lines and the two info tiles. Cheap; called every minute so they turn over at midnight.
    void refreshCalendar() {
        int[] now = Weather.now();
        int y = now[0], m = now[1], d = now[2], wd = TamilDate.weekday(y, m, d);
        if (dateEn != null) dateEn.setText(TamilDate.WEEKDAYS_EN[wd] + ", " + d + " " + MONTHS[m - 1] + " " + y);
        try {
            TamilDate.Day t = TamilDate.of(y, m, d);
            if (dateTa != null) dateTa.setText(TamilDate.WEEKDAYS_TA[wd] + " · " + t.ta()
                    + (t.event != TamilDate.NONE && t.event != TamilDate.MONTH_START ? " · " + t.eventTa() : ""));
        } catch (Throwable e) {
            if (dateTa != null) dateTa.setText(TamilDate.WEEKDAYS_TA[wd]);
        }
        if (festName != null) {
            Holidays.Next n = null;
            try { n = Holidays.next(a, y, m, d); } catch (Throwable ignored) {}
            if (n == null) {
                festName.setText("Next festival");
                festWhen.setText("Holiday list not available");
            } else {
                festName.setText((n.days == 0 ? "Today: " : "Next festival: ") + Holidays.tamilName(n.names));
                festWhen.setText(shortDate(n.y, n.m, n.d) + " · " + inDays(n.days));
            }
        }
        if (tamilName != null) {
            int[] e = null;
            try { e = TamilDate.nextEvent(y, m, d); } catch (Throwable ignored) {}
            if (e == null) {
                tamilName.setText("தமிழ் நாள்");
                tamilWhen.setText("Not available");
            } else {
                long days = TamilDate.epochDay(e[0], e[1], e[2]) - TamilDate.epochDay(y, m, d);
                String month = "";
                if (e[3] == TamilDate.MONTH_START) month = " · " + TamilDate.of(e[0], e[1], e[2]).ta();
                tamilName.setText(TamilDate.EVENTS_TA[e[3]] + month);
                tamilWhen.setText(TamilDate.EVENTS_EN[e[3]] + " · " + shortDate(e[0], e[1], e[2]) + " · " + inDays(days));
            }
        }
    }

    // Shows a forecast, or the "not available" line for null.
    void showForecast(Weather.Forecast f) {
        forecast = f;
        if (banner == null) return;
        int keep = -1;
        for (int i = 0; i < cards.getChildCount(); i++) if (cards.getChildAt(i).hasFocus()) keep = i;
        cards.removeAllViews();
        if (f == null) {
            banner.setVisibility(View.VISIBLE);
            banner.setText("Weather not available right now");
            banner.setTextColor(DIM);
            banner.setBackground(rounded(CARD, 16, 0, 0));
            banner.setCompoundDrawablesRelative(null, null, null, null);
            cards.setVisibility(View.GONE);
            return;
        }
        banner.setVisibility(View.GONE);   // the rain banner is off; rain times show on the day cards
        cards.setVisibility(View.VISIBLE);
        for (int i = 0; i < f.days.size() && i < 5; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.leftMargin = px(24);
            cards.addView(dayCard(f.days.get(i), i), lp);
        }
        if (keep >= 0 && keep < cards.getChildCount()) cards.getChildAt(keep).requestFocus();
    }

    private View dayCard(Weather.Day d, final int index) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(22), px(14), px(18), px(12));
        int bgColor = d.wet() ? WET_BG : d.today ? CARD2 : CARD;
        GradientDrawable bg = rounded(bgColor, 20, d.wet() ? RAIN : 0, d.wet() ? 3 : 0);
        c.setBackground(bg);

        // Top: the day and date on the left, the weather icon on the right.
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView name = line(30, TEXT, semibold);
        name.setText(d.today ? "Today" : TamilDate.WEEKDAYS_EN[d.weekday]);
        titles.addView(name, new LinearLayout.LayoutParams(-1, px(40)));
        TextView sub = line(22, DIM, tamil);
        sub.setText(d.d + " " + MONTHS[d.m - 1].substring(0, 3) + " · " + TamilDate.WEEKDAYS_TA[d.weekday]);
        titles.addView(sub, new LinearLayout.LayoutParams(-1, px(30)));
        head.addView(titles, new LinearLayout.LayoutParams(0, -1, 1f));
        ImageView ic = new ImageView(a);
        ic.setImageDrawable(new WeatherIcon(WeatherIcon.kindFor(d.code, false)));
        head.addView(ic, new LinearLayout.LayoutParams(px(60), px(60)));
        c.addView(head, new LinearLayout.LayoutParams(-1, px(70)));

        // 7–9 pm comes first: rain then, with its chance. If it's dry then, say so, and show the day's
        // first other rain time under it. Without hourly data for 7–9 pm, just the first rain time.
        int evening = Weather.eveningRain(d);
        if (evening >= Weather.RAIN_PCT) {
            c.addView(rainRow("7–9 PM · " + evening + "%", 32), rowParams());
        } else if (evening >= 0) {
            LinearLayout two = new LinearLayout(a);
            two.setOrientation(LinearLayout.VERTICAL);
            TextView dry = line(24, DRY_TEXT, semibold);
            dry.setText("No rain 7–9 PM");
            two.addView(dry, new LinearLayout.LayoutParams(-1, px(30)));
            if (d.wet()) two.addView(rainRow(Weather.shortWindow(d.windows.get(0)), 24),
                    new LinearLayout.LayoutParams(-1, px(30)));
            c.addView(two, rowParams());
        } else if (d.wet()) {
            c.addView(rainRow(Weather.shortWindow(d.windows.get(0)), 32), rowParams());
        } else {
            TextView none = line(24, DIM, semibold);
            none.setText("No rain");
            c.addView(none, new LinearLayout.LayoutParams(-1, px(32)));
        }

        TextView temps = line(24, TEXT, regular);
        temps.setText(Weather.temp(d.max) + " / " + Weather.temp(d.min) + (d.rain >= 0 ? "   ·   Rain " + d.rain + "%" : ""));
        LinearLayout.LayoutParams tempsLp = new LinearLayout.LayoutParams(-1, px(40));
        tempsLp.topMargin = px(14);
        c.addView(temps, tempsLp);

        focusStyle(c, bg, d.wet() ? RAIN : 0, d.wet() ? 3 : 0, 1f);
        c.setOnClickListener(v -> openDay(index));   // straight to the full-day data, no hourly grid
        return c;
    }

    // A small rain icon and a time like "6–9 PM".
    private View rainRow(String text, int size) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ri = new ImageView(a);
        ri.setImageDrawable(new WeatherIcon(WeatherIcon.RAIN));
        row.addView(ri, new LinearLayout.LayoutParams(px(size - 2), px(size - 2)));
        TextView t = line(size, RAIN_TEXT, semibold);
        t.setText(text.toUpperCase(Locale.ROOT));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.leftMargin = px(8);
        row.addView(t, tp);
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(60));
        lp.topMargin = px(10);
        return lp;
    }

    // "from 8 am to 12 pm", or "all day".
    private static String spell(int[] w) {
        String s = Weather.window(w);
        return s.equals("all day") ? s : "from " + s.replace(" – ", " to ");
    }

    private static String rainNote(Weather.Day d) {
        if (d.wet()) return Weather.window(d.windows.get(0)) + (d.windows.size() > 1 ? " +" + (d.windows.size() - 1) : "");
        return d.rain >= 0 && d.rain < 20 ? "no rain" : "unlikely";
    }

    // ---- Hour by hour ----

    void openHours(final int index) {
        if (forecast == null || index >= forecast.days.size()) return;
        final Weather.Day d = forecast.days.get(index);
        final Dialog dlg = fullScreen();
        hoursDialog = dlg;
        LinearLayout page = page();

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);
        LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = line(46, TEXT, semibold);
        title.setText(longDate(d) + " · " + TamilDate.WEEKDAYS_TA[d.weekday]);
        titles.addView(title, new LinearLayout.LayoutParams(-1, px(62)));
        TextView sub = line(26, DIM, regular);
        sub.setText("Max " + Weather.temp(d.max) + " · Min " + Weather.temp(d.min) + " · "
                + (d.wet() ? "Rain " + d.rain + "%, " + spell(d.windows.get(0))
                : "Rain " + (d.rain >= 0 ? d.rain + "%" : "–") + ", not expected"));
        titles.addView(sub, new LinearLayout.LayoutParams(-1, px(40)));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        View full = button("Full day ›");
        full.setOnClickListener(v -> openDay(index));
        head.addView(full, buttonParams(px(24)));
        View back = button("← Back");
        back.setOnClickListener(v -> dlg.dismiss());
        head.addView(back, buttonParams(px(24)));
        page.addView(head, new LinearLayout.LayoutParams(-1, px(120)));

        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 6; col++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                if (col > 0) lp.leftMargin = px(16);
                row.addView(hourCell(d, r * 6 + col), lp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, px(150));
            rp.topMargin = px(r == 0 ? 20 : 14);
            page.addView(row, rp);
        }

        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(-1, px(120));
        chipsLp.topMargin = px(24);
        page.addView(chips(index, this::openDay), chipsLp);

        show(dlg, page, full);
    }

    private View hourCell(Weather.Day d, int h) {
        Weather.Hour x = d.hours[h];
        boolean wet = x != null && x.rain >= Weather.RAIN_PCT;
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setBackground(rounded(wet ? WET_BG : CARD, 20, wet ? RAIN : 0, wet ? 3 : 0));
        TextView label = line(24, DIM, semibold);
        label.setGravity(Gravity.CENTER);
        label.setText(Weather.hour(h));
        c.addView(label, new LinearLayout.LayoutParams(-1, px(34)));
        LinearLayout mid = new LinearLayout(a);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.setGravity(Gravity.CENTER);
        ImageView ic = new ImageView(a);
        if (x != null) ic.setImageDrawable(new WeatherIcon(WeatherIcon.kindFor(x.code, h < 6 || h >= 18)));
        mid.addView(ic, new LinearLayout.LayoutParams(px(52), px(52)));
        TextView t = line(38, TEXT, semibold);
        t.setText(x == null ? "–" : Weather.temp(x.temp));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -1);
        tp.leftMargin = px(12);
        mid.addView(t, tp);
        c.addView(mid, new LinearLayout.LayoutParams(-1, px(60)));
        TextView r = line(22, RAIN_TEXT, semibold);
        r.setGravity(Gravity.CENTER);
        r.setText(x == null || x.rain < 0 ? "Rain –" : "Rain " + x.rain + "%");
        c.addView(r, new LinearLayout.LayoutParams(-1, px(32)));
        return c;
    }

    // ---- Full day ----

    void openDay(final int index) {
        if (forecast == null || index >= forecast.days.size()) return;
        if (hoursDialog != null && hoursDialog.isShowing()) hoursDialog.dismiss();
        if (dayDialog != null && dayDialog.isShowing()) dayDialog.dismiss();
        final Dialog dlg = fullScreen();
        dayDialog = dlg;
        LinearLayout page = page();

        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setClipChildren(false);
        View home = button("← Home");
        home.setOnClickListener(v -> dlg.dismiss());
        top.addView(home, buttonParams(0));
        TextView title = line(26, DIM, semibold);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        String updated = "";
        if (forecast.fetchedAt > 0) {
            SimpleDateFormat f = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            f.setTimeZone(TimeZone.getTimeZone(Weather.TZ));
            updated = " · updated " + f.format(new Date(forecast.fetchedAt)).toLowerCase(Locale.ROOT);
        }
        title.setText("Full-day weather · " + Weather.PLACE + updated);
        top.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));
        page.addView(top, new LinearLayout.LayoutParams(-1, px(80)));

        final FrameLayout main = new FrameLayout(a);
        main.setClipChildren(false);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, px(720));
        mlp.topMargin = px(24);
        page.addView(main, mlp);
        renderDay(main, index);

        final LinearLayout[] chipRow = new LinearLayout[1];
        chipRow[0] = chips(index, which -> {
            renderDay(main, which);
            markChip(chipRow[0], which);
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, px(130));
        clp.topMargin = px(28);
        page.addView(chipRow[0], clp);

        show(dlg, page, chipRow[0].getChildAt(index));
    }

    // The full-day page, laid out like the approved mock: the day and its temperatures, the hour-by-hour
    // strip, the four parts of the day and today's details. Text is large for older viewers.
    private void renderDay(FrameLayout main, int index) {
        main.removeAllViews();
        Weather.Day d = forecast.days.get(index);
        LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);

        // Hero: the day and Tamil date, the max and min, the summary and the next festival.
        LinearLayout hero = new LinearLayout(a);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(px(32), px(14), px(32), px(14));
        hero.setBackground(rounded(CARD, 26, 0, 0));

        LinearLayout names = new LinearLayout(a);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView wd = line(40, TEXT, semibold);
        wd.setText(d.today ? "Today, " + TamilDate.WEEKDAYS_EN[d.weekday] : TamilDate.WEEKDAYS_EN[d.weekday]);
        names.addView(wd, new LinearLayout.LayoutParams(-1, -2));
        TextView taWd = line(28, GOLD, tamil);
        taWd.setText(TamilDate.WEEKDAYS_TA[d.weekday]);
        names.addView(taWd, new LinearLayout.LayoutParams(-1, -2));
        TextView dt = line(26, DIM, regular);
        dt.setText(d.d + " " + MONTHS[d.m - 1] + " " + d.y);
        names.addView(dt, new LinearLayout.LayoutParams(-1, -2));
        TextView taDt = line(26, GOLD, tamil);
        String tamilDay;
        try {
            TamilDate.Day t = TamilDate.of(d.y, d.m, d.d);
            tamilDay = t.ta() + (t.event != TamilDate.NONE ? " · " + t.eventTa() : "");
        } catch (Throwable e) {
            tamilDay = "";
        }
        taDt.setText(tamilDay);
        names.addView(taDt, new LinearLayout.LayoutParams(-1, -2));
        hero.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout temps = new LinearLayout(a);
        temps.setOrientation(LinearLayout.HORIZONTAL);
        temps.setGravity(Gravity.CENTER_VERTICAL);
        TextView max = text(96, TEXT, semibold);
        max.setText(Weather.temp(d.max));
        max.setIncludeFontPadding(false);
        temps.addView(max, new LinearLayout.LayoutParams(-2, -2));
        TextView min = text(34, DIM, regular);
        min.setText("min " + Weather.temp(d.min));
        LinearLayout.LayoutParams minLp = new LinearLayout.LayoutParams(-2, -2);
        minLp.leftMargin = px(16);
        temps.addView(min, minLp);
        hero.addView(temps, new LinearLayout.LayoutParams(-2, -2));

        // The summary on the right, left-aligned, over several lines.
        LinearLayout side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        side.setPaddingRelative(px(24), 0, 0, 0);
        TextView summary = text(30, d.wet() ? RAIN_TEXT : TEXT, semibold);
        summary.setText(summary(d));
        summary.setMaxLines(3);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        summary.setGravity(Gravity.START);
        side.addView(summary, new LinearLayout.LayoutParams(-1, -2));
        hero.addView(side, new LinearLayout.LayoutParams(0, -2, 1.2f));
        page.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        // Hour by hour, 12 pm to 11 pm, with the rain window in the heading.
        LinearLayout hourly = new LinearLayout(a);
        hourly.setOrientation(LinearLayout.VERTICAL);
        hourly.setPadding(px(26), px(18), px(26), px(18));
        hourly.setBackground(rounded(CARD, 26, 0, 0));
        List<String> wins = new ArrayList<>();
        for (int[] win : d.windows) wins.add(Weather.window(win));
        TextView h3 = line(28, DIM, semibold);
        h3.setText("HOUR BY HOUR   ·   " + (wins.isEmpty() ? "no rain window" : "rain " + TextUtils.join(", ", wins)));
        hourly.addView(h3, new LinearLayout.LayoutParams(-1, px(36)));
        LinearLayout hrow = new LinearLayout(a);
        hrow.setOrientation(LinearLayout.HORIZONTAL);
        for (int h = 12; h < 24; h++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (h > 12) lp.leftMargin = px(12);
            hrow.addView(hourCellBig(d, h), lp);
        }
        hourly.addView(hrow, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, px(340));
        hlp.topMargin = px(16);
        page.addView(hourly, hlp);

        // Festivals: a separate section below the hours. Only the next festival, with its date.
        LinearLayout fest = new LinearLayout(a);
        fest.setOrientation(LinearLayout.VERTICAL);
        fest.setGravity(Gravity.CENTER_VERTICAL);
        fest.setPadding(px(32), px(20), px(32), px(20));
        fest.setBackground(rounded(CARD, 26, 0, 0));
        TextView fh = line(26, DIM, semibold);
        fh.setText("FESTIVALS");
        fest.addView(fh, new LinearLayout.LayoutParams(-1, -2));
        TextView fv = line(34, TEXT, semibold);
        fv.setText(festivalLine(d));
        fv.setMaxLines(4);
        fv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams fvlp = new LinearLayout.LayoutParams(-1, -2);
        fvlp.topMargin = px(10);
        fest.addView(fv, fvlp);
        LinearLayout.LayoutParams felp = new LinearLayout.LayoutParams(-1, 0, 1f);
        felp.topMargin = px(16);
        page.addView(fest, felp);



        main.addView(page, new FrameLayout.LayoutParams(-1, -1));
    }

    // One hour in the strip: time, icon, temperature, rain bar and rain chance, all large.
    private View hourCellBig(Weather.Day d, int h) {
        Weather.Hour x = d.hours[h];
        boolean wet = x != null && x.rain >= Weather.RAIN_PCT;
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(px(4), px(8), px(4), px(8));
        c.setBackground(rounded(wet ? WET_BG : CARD2, 18, wet ? RAIN : 0, wet ? 3 : 0));
        TextView label = line(28, DIM, semibold);
        label.setGravity(Gravity.CENTER);
        label.setText(Weather.hour(h));
        c.addView(label, new LinearLayout.LayoutParams(-1, px(34)));
        ImageView ic = new ImageView(a);
        if (x != null) ic.setImageDrawable(new WeatherIcon(WeatherIcon.kindFor(x.code, h < 6 || h >= 18)));
        c.addView(ic, new LinearLayout.LayoutParams(px(46), px(46)));
        TextView t = line(38, TEXT, semibold);
        t.setGravity(Gravity.CENTER);
        t.setText(x == null ? "–" : Weather.temp(x.temp));
        c.addView(t, new LinearLayout.LayoutParams(-1, px(44)));
        int pct = x == null || x.rain < 0 ? 0 : Math.min(100, x.rain);
        FrameLayout track = new FrameLayout(a);
        track.setBackground(rounded(0xFF0F1A30, 13, 0, 0));
        View fill = new View(a);
        fill.setBackground(rounded(RAIN, 13, 0, 0));
        track.addView(fill, new FrameLayout.LayoutParams(-1, Math.max(px(6), px(40) * pct / 100), Gravity.BOTTOM));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(22), px(40));
        tl.topMargin = px(4);
        c.addView(track, tl);
        TextView r = line(30, RAIN_TEXT, semibold);
        r.setGravity(Gravity.CENTER);
        r.setText(x == null || x.rain < 0 ? "–" : x.rain + "%");
        c.addView(r, new LinearLayout.LayoutParams(-1, px(36)));
        return c;
    }

    // One hour: the time and the chance of rain. Rainy hours have a blue outline.
    private View hourRain(Weather.Day d, int h) {
        Weather.Hour x = d.hours[h];
        boolean wet = x != null && x.rain >= Weather.RAIN_PCT;
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setBackground(rounded(wet ? WET_BG : CARD2, 20, wet ? RAIN : 0, wet ? 3 : 0));
        TextView t = line(44, DIM, semibold);
        t.setGravity(Gravity.CENTER);
        t.setText(Weather.hour(h));
        c.addView(t, new LinearLayout.LayoutParams(-1, px(56)));
        TextView r = line(64, wet ? RAIN_TEXT : TEXT, semibold);
        r.setGravity(Gravity.CENTER);
        r.setText(x == null || x.rain < 0 ? "–" : x.rain + "%");
        c.addView(r, new LinearLayout.LayoutParams(-1, px(76)));
        return c;
    }

    // One hour in the hour-by-hour strip: time, icon, temperature, a rain bar and the rain chance.
    private View hourBar(Weather.Day d, int h) {
        Weather.Hour x = d.hours[h];
        boolean wet = x != null && x.rain >= Weather.RAIN_PCT;
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(px(4), px(8), px(4), px(8));
        c.setBackground(rounded(wet ? WET_BG : CARD2, 18, wet ? RAIN : 0, wet ? 3 : 0));
        TextView label = line(22, DIM, semibold);
        label.setGravity(Gravity.CENTER);
        label.setText(Weather.hour(h));
        c.addView(label, new LinearLayout.LayoutParams(-1, px(26)));
        ImageView ic = new ImageView(a);
        if (x != null) ic.setImageDrawable(new WeatherIcon(WeatherIcon.kindFor(x.code, h < 6 || h >= 18)));
        c.addView(ic, new LinearLayout.LayoutParams(px(44), px(44)));
        TextView t = line(30, TEXT, semibold);
        t.setGravity(Gravity.CENTER);
        t.setText(x == null ? "–" : Weather.temp(x.temp));
        c.addView(t, new LinearLayout.LayoutParams(-1, px(36)));
        int pct = x == null || x.rain < 0 ? 0 : Math.min(100, x.rain);
        FrameLayout track = new FrameLayout(a);
        track.setBackground(rounded(0xFF0F1A30, 13, 0, 0));
        View fill = new View(a);
        fill.setBackground(rounded(RAIN, 13, 0, 0));
        track.addView(fill, new FrameLayout.LayoutParams(-1, Math.max(px(6), px(36) * pct / 100), Gravity.BOTTOM));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(22), px(36));
        tl.topMargin = px(6);
        c.addView(track, tl);
        TextView r = line(24, RAIN_TEXT, semibold);
        r.setGravity(Gravity.CENTER);
        r.setText(x == null || x.rain < 0 ? "–" : x.rain + "%");
        c.addView(r, new LinearLayout.LayoutParams(-1, px(28)));
        return c;
    }

    private View period(Weather.Day d, int p) {
        int from = PERIOD_HOURS[p][0], to = PERIOD_HOURS[p][1];
        double sum = 0;
        int n = 0, rain = -1;
        for (int h = from; h < to; h++) {
            Weather.Hour x = d.hours[h];
            if (x == null) continue;
            if (!Double.isNaN(x.temp)) { sum += x.temp; n++; }
            rain = Math.max(rain, x.rain);
        }
        boolean wet = rain >= Weather.RAIN_PCT;
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(18), px(8), px(18), px(8));
        c.setBackground(rounded(wet ? WET_BG : CARD2, 18, wet ? RAIN : 0, wet ? 3 : 0));
        TextView name = line(26, TEXT, semibold);
        android.text.SpannableString s = new android.text.SpannableString(PERIODS[p] + "  " + PERIOD_TIMES[p]);
        int start = PERIODS[p].length();
        s.setSpan(new android.text.style.AbsoluteSizeSpan(px(22)), start, s.length(), 0);
        s.setSpan(new android.text.style.ForegroundColorSpan(DIM), start, s.length(), 0);
        name.setText(s);
        c.addView(name, new LinearLayout.LayoutParams(-1, px(34)));
        c.addView(new View(a), new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout val = new LinearLayout(a);
        val.setOrientation(LinearLayout.HORIZONTAL);
        val.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = line(40, TEXT, semibold);
        t.setText(n > 0 ? Weather.temp(sum / n) : "–");
        val.addView(t, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView r = line(28, RAIN_TEXT, semibold);
        r.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        r.setText(rain >= 0 ? "Rain " + rain + "%" : "Rain –");
        val.addView(r, new LinearLayout.LayoutParams(-2, -1));
        c.addView(val, new LinearLayout.LayoutParams(-1, px(52)));
        return c;
    }

    private static String summary(Weather.Day d) {
        String s = d.code >= 0 ? Weather.describe(d.code) + ". " : "";
        if (d.wet()) {
            List<String> w = new ArrayList<>();
            for (int[] win : d.windows) w.add(spell(win));
            return s + "Rain likely " + TextUtils.join(", and ", w) + " (" + d.rain + "%).";
        }
        if (d.rain >= 20) return s + "Small chance of rain (" + d.rain + "%).";
        return s + "Rain is not expected.";
    }

    // Only the next festival and its date. Nothing is shown for today.
    private String festivalLine(Weather.Day d) {
        try {
            int[] c = TamilDate.civil(TamilDate.epochDay(d.y, d.m, d.d) + 1);
            Holidays.Next n = Holidays.next(a, c[0], c[1], c[2]);
            if (n == null) return "";
            long days = TamilDate.epochDay(n.y, n.m, n.d) - TamilDate.epochDay(d.y, d.m, d.d);
            return "அடுத்தது: " + Holidays.tamilName(n.names) + ", " + shortDate(n.y, n.m, n.d) + " (" + inDays(days) + ")";
        } catch (Throwable e) {
            return "";
        }
    }

    // ---- Day picker (5 chips), shared by both screens ----

    private LinearLayout chips(int selected, final OnPick pick) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        for (int i = 0; i < forecast.days.size() && i < 5; i++) {
            final int which = i;
            Weather.Day d = forecast.days.get(i);
            LinearLayout c = new LinearLayout(a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPadding(px(22), 0, px(16), 0);
            TextView t1 = line(30, TEXT, semibold);
            t1.setText(d.today ? "Today" : TamilDate.WEEKDAYS_EN[d.weekday].substring(0, 3) + " " + d.d);
            c.addView(t1, new LinearLayout.LayoutParams(-1, px(42)));
            TextView t2 = line(24, DIM, semibold);
            t2.setText(Weather.temp(d.max) + " / " + Weather.temp(d.min));
            c.addView(t2, new LinearLayout.LayoutParams(-1, px(32)));
            TextView t3 = line(24, RAIN_TEXT, semibold);
            t3.setText(d.wet() ? "Rain " + d.rain + "%" : "No rain");
            c.addView(t3, new LinearLayout.LayoutParams(-1, px(32)));
            c.setTag(d.wet());
            c.setOnClickListener(v -> pick.at(which));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.leftMargin = px(20);
            row.addView(c, lp);
        }
        markChip(row, selected);
        return row;
    }

    // The picked day gets a white ring; rainy days a blue one.
    private void markChip(LinearLayout row, int selected) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            boolean wet = Boolean.TRUE.equals(c.getTag());
            int stroke = i == selected ? 0xB3FFFFFF : wet ? RAIN : 0;
            int sw = i == selected || wet ? 3 : 0;
            GradientDrawable bg = rounded(wet ? WET_BG : CARD, 22, stroke, sw);
            c.setBackground(bg);
            focusStyle(c, bg, stroke, sw, 1f);
            if (c.hasFocus()) bg.setStroke(px(5), Color.WHITE);
        }
    }

    // ---- Small helpers ----

    private Dialog fullScreen() {
        Dialog d = new Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        d.setOnKeyListener((di, code, ev) -> {
            if (code != KeyEvent.KEYCODE_ESCAPE) return false;
            if (ev.getAction() == KeyEvent.ACTION_UP) di.dismiss();
            return true;
        });
        return d;
    }

    private LinearLayout page() {
        LinearLayout p = new LinearLayout(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(px(64), px(40), px(64), px(30));
        p.setClipChildren(false);
        p.setClipToPadding(false);
        return p;
    }

    private void show(Dialog d, View content, View focus) {
        d.setContentView(content);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(PAGE));
        d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        d.show();
        if (focus != null) focus.requestFocus();
    }

    private TextView button(String label) {
        TextView b = line(32, TEXT, semibold);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(36), 0, px(36), 0);
        GradientDrawable bg = rounded(BUTTON, 22, 0, 0);
        b.setBackground(bg);
        focusStyle(b, bg, 0, 0, 1f);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, px(76));
        lp.leftMargin = leftMargin;
        return lp;
    }

    // Focus: a white ring and a little bigger, like the app tiles.
    private void focusStyle(View v, final GradientDrawable bg, final int stroke, final int strokeW, final float grow) {
        v.setFocusable(true);
        v.setOnFocusChangeListener((view, has) -> {
            // A thick white border shows where the remote's focus is. No zoom: the text stays where it is.
            if (has) bg.setStroke(px(6), Color.WHITE);
            else bg.setStroke(strokeW > 0 ? px(strokeW) : 0, stroke);
            view.animate().scaleX(has ? grow : 1f).scaleY(has ? grow : 1f).setDuration(140).start();
        });
    }

    private GradientDrawable rounded(int color, int radius, int stroke, int strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radius));
        if (strokeW > 0) g.setStroke(Math.max(1, px(strokeW)), stroke);
        return g;
    }

    // A single line of text that ends in "…" when it doesn't fit, centred in its (fixed) height.
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

    private static String longDate(Weather.Day d) {
        return (d.today ? "Today, " : "") + TamilDate.WEEKDAYS_EN[d.weekday] + " " + d.d + " " + MONTHS[d.m - 1];
    }

    // "Sun 8 Nov"
    private static String shortDate(int y, int m, int d) {
        return TamilDate.WEEKDAYS_EN[TamilDate.weekday(y, m, d)].substring(0, 3) + " " + d + " " + MONTHS[m - 1].substring(0, 3);
    }

    private static String inDays(long n) {
        return n == 0 ? "today" : n == 1 ? "tomorrow" : "in " + n + " days";
    }

    private int px(int v) {
        return Math.round(v * scale);
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }

    // 24 rain bars, one per hour: blue at 40% and over, lighter blue from 20%, grey below.
    static final class RainBars extends Drawable {
        private final int[] values;
        private final float gap, radius;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        RainBars(int[] values, float gap, float radius) {
            this.values = values;
            this.gap = gap;
            this.radius = radius;
        }

        @Override
        public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            float bw = (w - gap * (values.length - 1)) / values.length;
            for (int i = 0; i < values.length; i++) {
                int v = Math.min(100, values[i]);
                paint.setColor(v >= Weather.RAIN_PCT ? RAIN : v >= 20 ? BAR_MID : BAR);
                float bh = Math.max(h * 0.06f, h * v / 100f);
                float x = getBounds().left + i * (bw + gap);
                r.set(x, getBounds().top + h - bh, x + bw, getBounds().top + h);
                c.drawRoundRect(r, radius, radius, paint);
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
