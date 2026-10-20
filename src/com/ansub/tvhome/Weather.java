package com.ansub.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

// The 5-day forecast for one place, from Open-Meteo (free, no key). Fetched on a background thread at
// most every 30 minutes (a failed try is retried after 5 minutes), and the last good answer is kept in
// SharedPreferences, so the home screen still shows it when the TV is offline.
final class Weather {
    // Madurai, PIN 625009.
    static final String PLACE = "Madurai";
    static final double LAT = 9.9252, LON = 78.1198;
    static final String TZ = "Asia/Kolkata";
    static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast?latitude=" + LAT + "&longitude=" + LON
            + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code,sunrise,sunset,uv_index_max"
            + "&hourly=temperature_2m,precipitation_probability,weather_code,relative_humidity_2m,wind_speed_10m"
            + "&timezone=Asia%2FKolkata&forecast_days=5";
    static final long REFRESH_MS = 30 * 60 * 1000L, RETRY_MS = 5 * 60 * 1000L;
    static final int RAIN_PCT = 40; // an hour at or above this chance of rain counts as rainy

    private static final String PREFS = "weather";
    private static long lastTry;
    private static boolean running;

    static final class Hour {
        int hour, rain = -1, code = -1, humidity = -1;
        double temp = Double.NaN, wind = Double.NaN;
    }

    static final class Day {
        String date; // YYYY-MM-DD
        int y, m, d, weekday; // weekday 0 = Sunday
        double max = Double.NaN, min = Double.NaN, uv = Double.NaN;
        int rain = -1, code = -1;
        String sunrise, sunset; // "6:01 am", or null
        final Hour[] hours = new Hour[24]; // entries can be null
        List<int[]> windows = new ArrayList<>(); // rainy spells {from, to}, to exclusive
        boolean today;

        boolean wet() { return !windows.isEmpty(); }
    }

    static final class Forecast {
        final List<Day> days = new ArrayList<>();
        long fetchedAt;
    }

    private Weather() {}

    // Today's date in Madurai: {year, month, day, hour}.
    static int[] now() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(TZ));
        return new int[] {c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY)};
    }

    static String isoDate(int y, int m, int d) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d);
    }

    // Starts a download if the saved forecast is old; done runs on the main thread when it finishes.
    static void maybeFetch(Context context, final Runnable done) {
        final Context app = context.getApplicationContext();
        long t = System.currentTimeMillis();
        SharedPreferences p = app.getSharedPreferences(PREFS, 0);
        long at = p.getLong("at", 0);
        if (running || (t - at >= 0 && t - at < REFRESH_MS) || (lastTry != 0 && t - lastTry < RETRY_MS)) return;
        running = true;
        lastTry = t;
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String json = download();
                new JSONObject(json).getJSONObject("daily"); // only keep an answer that looks right
                app.getSharedPreferences(PREFS, 0).edit()
                        .putString("json", json).putLong("at", System.currentTimeMillis()).apply();
            } catch (Throwable ignored) {
                // Offline or a bad answer: keep the last good forecast.
            }
            main.post(() -> {
                running = false;
                if (done != null) done.run();
            });
        }, "weather").start();
    }

    private static String download() throws IOException {
        HttpURLConnection c = (HttpURLConnection) new java.net.URL(FORECAST_URL).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            in.close();
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // The saved forecast from today on, or null if there is none (never fetched, or too old).
    static Forecast load(Context context) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, 0);
        String json = p.getString("json", null);
        if (json == null) return null;
        try {
            Forecast f = parse(json);
            f.fetchedAt = p.getLong("at", 0);
            return f.days.isEmpty() ? null : f;
        } catch (Throwable e) {
            return null;
        }
    }

    static Forecast parse(String json) throws Exception {
        int[] now = now();
        String today = isoDate(now[0], now[1], now[2]);
        JSONObject root = new JSONObject(json);
        JSONObject daily = root.getJSONObject("daily");
        JSONArray dates = daily.getJSONArray("time");
        Forecast f = new Forecast();
        for (int i = 0; i < dates.length(); i++) {
            String date = dates.getString(i);
            if (date.compareTo(today) < 0) continue; // an old answer: skip days already gone
            Day d = new Day();
            d.date = date;
            d.y = Integer.parseInt(date.substring(0, 4));
            d.m = Integer.parseInt(date.substring(5, 7));
            d.d = Integer.parseInt(date.substring(8, 10));
            d.weekday = TamilDate.weekday(d.y, d.m, d.d);
            d.today = date.equals(today);
            d.max = num(daily, "temperature_2m_max", i);
            d.min = num(daily, "temperature_2m_min", i);
            d.uv = num(daily, "uv_index_max", i);
            d.rain = whole(num(daily, "precipitation_probability_max", i));
            d.code = whole(num(daily, "weather_code", i));
            d.sunrise = clock(str(daily, "sunrise", i));
            d.sunset = clock(str(daily, "sunset", i));
            f.days.add(d);
        }
        JSONObject hourly = root.optJSONObject("hourly");
        JSONArray times = hourly == null ? null : hourly.optJSONArray("time");
        if (times != null) {
            for (int i = 0; i < times.length(); i++) {
                String t = times.optString(i, ""); // "2026-10-20T08:00"
                if (t.length() < 13) continue;
                Day d = find(f, t.substring(0, 10));
                if (d == null) continue;
                Hour h = new Hour();
                h.hour = Integer.parseInt(t.substring(11, 13));
                if (h.hour < 0 || h.hour > 23) continue;
                h.temp = num(hourly, "temperature_2m", i);
                h.rain = whole(num(hourly, "precipitation_probability", i));
                h.code = whole(num(hourly, "weather_code", i));
                h.humidity = whole(num(hourly, "relative_humidity_2m", i));
                h.wind = num(hourly, "wind_speed_10m", i);
                d.hours[h.hour] = h;
            }
        }
        for (Day d : f.days) {
            // Today only counts the hours still to come.
            d.windows = windows(d, d.today ? now[3] : 0);
            if (d.today) {
                int left = -1;
                for (int h = now[3]; h < 24; h++) if (d.hours[h] != null) left = Math.max(left, d.hours[h].rain);
                if (left >= 0) d.rain = left;
            }
        }
        return f;
    }

    private static Day find(Forecast f, String date) {
        for (Day d : f.days) if (d.date.equals(date)) return d;
        return null;
    }

    // Rainy spells: runs of hours with at least RAIN_PCT chance, as {from, to} with to exclusive.
    static List<int[]> windows(Day d, int fromHour) {
        List<int[]> out = new ArrayList<>();
        int start = -1;
        for (int h = fromHour; h <= 24; h++) {
            boolean wet = h < 24 && d.hours[h] != null && d.hours[h].rain >= RAIN_PCT;
            if (wet && start < 0) start = h;
            if (!wet && start >= 0) {
                out.add(new int[] {start, h});
                start = -1;
            }
        }
        return out;
    }

    // "8 am", "12 pm", "12 am" (midnight, also for 24).
    static String hour(int h) {
        h %= 24;
        return h == 0 ? "12 am" : h < 12 ? h + " am" : h == 12 ? "12 pm" : (h - 12) + " pm";
    }

    // "8 am – 12 pm"
    static String window(int[] w) {
        if (w[0] == 0 && w[1] == 24) return "all day";
        return hour(w[0]) + " – " + hour(w[1]);
    }

    // Shorter, for the banner: "6–9 pm", or "8 am–12 pm" when it crosses noon.
    static String shortWindow(int[] w) {
        if (w[0] == 0 && w[1] == 24) return "all day";
        String a = hour(w[0]), b = hour(w[1]);
        String sa = a.substring(a.indexOf(' ')), sb = b.substring(b.indexOf(' '));
        return sa.equals(sb) ? a.substring(0, a.indexOf(' ')) + "–" + b : a + "–" + b;
    }

    // WMO weather codes, in words.
    static String describe(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code == 65 || code == 67) return "Heavy rain";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code == 82) return "Heavy showers";
        if (code >= 80 && code <= 86) return "Showers";
        if (code >= 95) return "Thunderstorm";
        return "Weather";
    }

    static String temp(double t) {
        return Double.isNaN(t) ? "–" : Math.round(t) + "°";
    }

    private static double num(JSONObject o, String key, int i) {
        JSONArray a = o.optJSONArray(key);
        return a == null || a.isNull(i) ? Double.NaN : a.optDouble(i);
    }

    private static String str(JSONObject o, String key, int i) {
        JSONArray a = o.optJSONArray(key);
        return a == null || a.isNull(i) ? null : a.optString(i, null);
    }

    private static int whole(double v) {
        return Double.isNaN(v) ? -1 : (int) Math.round(v);
    }

    // "2026-10-20T06:01" to "6:01 am".
    private static String clock(String iso) {
        if (iso == null || iso.length() < 16) return null;
        try {
            int h = Integer.parseInt(iso.substring(11, 13));
            String mm = iso.substring(14, 16);
            return (h % 12 == 0 ? 12 : h % 12) + ":" + mm + (h < 12 ? " am" : " pm");
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
