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

// The 5-day forecast for one place, from the Google Weather API (needs a key, see key()). Fetched on a
// background thread at 8 am, 2 pm and 6 pm (the first time the home screen looks after each; a failed
// try is retried after an hour), or when Refresh is pressed. The last good answer is kept in
// SharedPreferences, so the home screen still shows it when the TV is offline.
// The key allows 1,000 calls a month and one refresh is 6 calls (1 daily, 5 pages of 24 hours): the
// three a day take 18, leaving room for two presses of Refresh under MAX_CALLS_A_DAY.
final class Weather {
    // Munichalai, Madurai, PIN 625009.
    static final String PLACE = "Madurai";
    static final double LAT = 9.9181, LON = 78.1344;
    static final String TZ = "Asia/Kolkata";
    static final String API = "https://weather.googleapis.com/v1/forecast/";
    static final String WHERE = "location.latitude=" + LAT + "&location.longitude=" + LON + "&unitsSystem=METRIC";
    // Hours come 24 to a page: four pages, plus the days and the current conditions, keep a refresh at
    // 6 calls. The fifth day's evening falls back on its night forecast (see eveningRain).
    static final int DAYS = 5, HOURS = 96;
    static final int[] SLOTS = {8 * 60, 14 * 60, 18 * 60}; // refresh times, minutes after midnight IST
    static final long RETRY_MS = 60 * 60 * 1000L;
    static final int MAX_CALLS_A_DAY = 30; // 30 x 31 days = 930, under the 1,000 a month
    static final int RAIN_PCT = 40; // an hour at or above this chance of rain counts as rainy
    static final int EVENING_FROM = 19, EVENING_TO = 21; // 7–9 pm, shown first on the home screen

    private static final String PREFS = "google-weather"; // not "weather": that holds Open-Meteo's answer
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
        int night = -1; // chance of rain in the night forecast (6 pm - 6 am), or -1
        String sunrise, sunset; // "6:01 am", or null
        final Hour[] hours = new Hour[24]; // entries can be null
        List<int[]> windows = new ArrayList<>(); // rainy spells {from, to}, to exclusive
        boolean today;

        boolean wet() { return !windows.isEmpty(); }
    }

    static final class Forecast {
        final List<Day> days = new ArrayList<>();
        long fetchedAt;
        // Rain measured in the hour before the download (current conditions), mm; NaN if unknown.
        double rainLastHour = Double.NaN;

        // Raining when the forecast was fetched, by Google's own measurement: the hourly forecast can
        // miss a shower that's already falling.
        boolean rainingNow() {
            return rainLastHour >= 0.2 && System.currentTimeMillis() - fetchedAt < 2 * 60 * 60 * 1000L;
        }
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

    interface Result { void done(String message); }

    // Starts a download if none has been made since the latest refresh time; done runs on the main
    // thread when it finishes.
    static void maybeFetch(Context context, final Runnable done) {
        Context app = context.getApplicationContext();
        long t = System.currentTimeMillis();
        long at = app.getSharedPreferences(PREFS, 0).getLong("at", 0);
        if (running || at >= lastSlot(t) || (lastTry != 0 && t - lastTry < RETRY_MS)) return;
        fetch(app, m -> { if (done != null) done.run(); });
    }

    // The Refresh button: downloads now, whatever the time, within the day's calls. done gets a line
    // to show: updated, out of calls for today, or couldn't update.
    static void refreshNow(Context context, Result done) {
        if (running) { done.done("Updating the weather…"); return; }
        fetch(context.getApplicationContext(), done);
    }

    private static void fetch(final Context app, final Result done) {
        running = true;
        lastTry = System.currentTimeMillis();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String message;
            try {
                String json = download(app.getSharedPreferences(PREFS, 0), key(app));
                app.getSharedPreferences(PREFS, 0).edit()
                        .putString("json", json).putLong("at", System.currentTimeMillis()).apply();
                message = "Weather updated";
            } catch (Throwable e) {
                // Offline or a bad answer: keep the last good forecast.
                android.util.Log.w("tvhome", "Weather download failed: " + e);
                message = String.valueOf(e.getMessage()).contains("out of calls")
                        ? "Weather: today's updates are used up. It updates again tomorrow at 8 am."
                        : "Couldn't update the weather. Check the internet and try again.";
            }
            final String m = message;
            main.post(() -> {
                running = false;
                done.done(m);
            });
        }, "weather").start();
    }

    // The latest refresh time at or before t (today's, or yesterday's 6 pm before 8 am), epoch millis.
    static long lastSlot(long t) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(TZ));
        c.setTimeInMillis(t);
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int slot = -1;
        for (int s : SLOTS) if (s <= now) slot = s;
        if (slot < 0) {
            c.add(Calendar.DAY_OF_MONTH, -1);
            slot = SLOTS[SLOTS.length - 1];
        }
        c.set(Calendar.HOUR_OF_DAY, slot / 60);
        c.set(Calendar.MINUTE, slot % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // Both answers in one object: {"days": [...forecastDays], "hours": [...forecastHours]}.
    // The API key: from weather.key in the app's folder on the TV's storage
    // (/sdcard/Android/data/com.ansub.tvhome/files, put there by install.sh or adb push), else the one
    // built in. Release APKs have none built in, so the key never ships with them.
    static String key(Context app) {
        java.io.File dir = app.getExternalFilesDir(null);
        java.io.File f = dir == null ? null : new java.io.File(dir, "weather.key");
        if (f != null && f.isFile() && f.length() < 1024) {
            try (InputStream in = new java.io.FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()];
                int n = 0;
                for (int r; n < b.length && (r = in.read(b, n, b.length - n)) > 0; ) n += r;
                String k = new String(b, 0, n, "UTF-8").replaceAll("\\s", "");
                if (!k.isEmpty()) return k;
            } catch (IOException ignored) {
                // Unreadable: fall back to the built-in key.
            }
        }
        return WeatherKey.KEY;
    }

    private static String download(SharedPreferences p, String apiKey) throws Exception {
        if (apiKey.isEmpty()) throw new IOException("no API key");
        if (budget(p) < 6) throw new IOException("out of calls for today"); // don't start what can't finish
        // 1 days + 4 hours pages + 1 current conditions = 6 calls.
        String key = "&key=" + apiKey;
        JSONArray days = new JSONObject(get(p, API + "days:lookup?" + WHERE + "&days=" + DAYS + "&pageSize=" + DAYS + key))
                .getJSONArray("forecastDays"); // only keep an answer that looks right
        JSONArray hours = new JSONArray();
        String page = "";
        do {
            JSONObject o = new JSONObject(get(p, API + "hours:lookup?" + WHERE + "&hours=" + HOURS + "&pageSize=24" + key
                    + (page.isEmpty() ? "" : "&pageToken=" + java.net.URLEncoder.encode(page, "UTF-8"))));
            JSONArray a = o.optJSONArray("forecastHours");
            for (int i = 0; a != null && i < a.length(); i++) hours.put(a.get(i));
            page = o.optString("nextPageToken", "");
        } while (!page.isEmpty() && hours.length() < HOURS);
        JSONObject now = new JSONObject(get(p, "https://weather.googleapis.com/v1/currentConditions:lookup?"
                + WHERE + key));
        return new JSONObject().put("days", days).put("hours", hours).put("now", now).toString();
    }

    // Calls left today, counted per day in Madurai. (Kept under new names: the first count also took
    // calls that never reached Google, so it could run out on a day with no internet.)
    private static int budget(SharedPreferences p) {
        int[] n = now();
        String today = isoDate(n[0], n[1], n[2]);
        return today.equals(p.getString("calls_day2", "")) ? MAX_CALLS_A_DAY - p.getInt("calls2", 0) : MAX_CALLS_A_DAY;
    }

    private static String get(SharedPreferences p, String url) throws IOException {
        int left = budget(p);
        if (left <= 0) throw new IOException("out of calls for today");
        HttpURLConnection c = (HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        try {
            int code = c.getResponseCode();
            // Google answered, so it counts. Calls that never got there (no internet, no DNS) don't.
            int[] d = now();
            p.edit().putString("calls_day2", isoDate(d[0], d[1], d[2])).putInt("calls2", MAX_CALLS_A_DAY - left + 1).commit();
            if (code != 200) throw new IOException("HTTP " + code);
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
        JSONArray days = root.getJSONArray("days");
        Forecast f = new Forecast();
        for (int i = 0; i < days.length(); i++) {
            JSONObject g = days.getJSONObject(i);
            JSONObject dd = g.getJSONObject("displayDate");
            Day d = new Day();
            d.y = dd.getInt("year");
            d.m = dd.getInt("month");
            d.d = dd.getInt("day");
            d.date = isoDate(d.y, d.m, d.d);
            if (d.date.compareTo(today) < 0) continue; // an old answer: skip days already gone
            d.weekday = TamilDate.weekday(d.y, d.m, d.d);
            d.today = d.date.equals(today);
            JSONObject day = g.optJSONObject("daytimeForecast"), night = g.optJSONObject("nighttimeForecast");
            d.max = num(g, "maxTemperature", "degrees");
            d.min = num(g, "minTemperature", "degrees");
            d.uv = day == null ? Double.NaN : day.optDouble("uvIndex", Double.NaN);
            d.night = rain(night);
            d.rain = Math.max(rain(day), d.night);
            d.code = code(day);
            JSONObject sun = g.optJSONObject("sunEvents");
            if (sun != null) {
                d.sunrise = clock(sun.optString("sunriseTime", null));
                d.sunset = clock(sun.optString("sunsetTime", null));
            }
            f.days.add(d);
        }
        JSONArray hours = root.optJSONArray("hours");
        for (int i = 0; hours != null && i < hours.length(); i++) {
            JSONObject g = hours.getJSONObject(i);
            JSONObject t = g.optJSONObject("displayDateTime");
            if (t == null) continue;
            Day d = find(f, isoDate(t.optInt("year"), t.optInt("month"), t.optInt("day")));
            if (d == null) continue;
            Hour h = new Hour();
            h.hour = t.optInt("hours", 0); // left out at midnight
            if (h.hour < 0 || h.hour > 23) continue;
            h.temp = num(g, "temperature", "degrees");
            h.rain = rain(g);
            h.code = code(g);
            h.humidity = g.has("relativeHumidity") ? g.optInt("relativeHumidity") : -1;
            JSONObject wind = g.optJSONObject("wind");
            h.wind = wind == null ? Double.NaN : num(wind, "speed", "value"); // km/h
            d.hours[h.hour] = h;
        }
        for (Day d : f.days) {
            // Today only counts the hours still to come.
            d.windows = windows(d, d.today ? now[3] : 0);
            if (d.today) {
                // What's left of today: the hours to come, and tonight's forecast (often wetter).
                int left = d.night;
                for (int h = now[3]; h < 24; h++) if (d.hours[h] != null) left = Math.max(left, d.hours[h].rain);
                if (left >= 0) d.rain = left;
            }
        }
        JSONObject current = root.optJSONObject("now");
        JSONObject cp = current == null ? null : current.optJSONObject("precipitation");
        if (cp != null) f.rainLastHour = num(cp, "qpf", "quantity");
        return f;
    }

    private static Day find(Forecast f, String date) {
        for (Day d : f.days) if (d.date.equals(date)) return d;
        return null;
    }

    // The chance of rain from 7 to 9 pm, or -1 without data or once it's past: the higher of the hourly
    // forecast and the night forecast. Google's hourly numbers can stay low on a night its own night
    // forecast (and other providers) call rainy, and a missed shower is worse than a dry evening.
    static int eveningRain(Day d) {
        int hour = now()[3];
        if (d.today && hour >= EVENING_TO) return -1;
        int from = d.today ? Math.max(EVENING_FROM, hour) : EVENING_FROM, max = d.night;
        for (int h = from; h < EVENING_TO; h++) if (d.hours[h] != null) max = Math.max(max, d.hours[h].rain);
        return max;
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

    // WMO weather codes (Google's conditions are turned into these by code()), in words.
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

    private static double num(JSONObject o, String key, String field) {
        JSONObject v = o == null ? null : o.optJSONObject(key);
        return v == null || !v.has(field) ? Double.NaN : v.optDouble(field, Double.NaN);
    }

    // The chance of rain in percent, or -1.
    private static int rain(JSONObject o) {
        JSONObject p = o == null ? null : o.optJSONObject("precipitation");
        JSONObject q = p == null ? null : p.optJSONObject("probability");
        return q == null ? -1 : q.optInt("percent", -1);
    }

    // Google's weatherCondition.type as the nearest WMO code, which the icons and words are keyed on.
    static int code(JSONObject o) {
        JSONObject c = o == null ? null : o.optJSONObject("weatherCondition");
        String t = c == null ? "" : c.optString("type", "");
        switch (t) {
            case "CLEAR": return 0;
            case "MOSTLY_CLEAR": return 1;
            case "PARTLY_CLOUDY": return 2;
            case "MOSTLY_CLOUDY": case "CLOUDY": case "WINDY": return 3;
            case "LIGHT_RAIN_SHOWERS": case "CHANCE_OF_SHOWERS": case "SCATTERED_SHOWERS": return 80;
            case "RAIN_SHOWERS": return 81;
            case "HEAVY_RAIN_SHOWERS": return 82;
            case "LIGHT_RAIN": case "LIGHT_TO_MODERATE_RAIN": return 61;
            case "RAIN": case "WIND_AND_RAIN": return 63;
            case "MODERATE_TO_HEAVY_RAIN": case "HEAVY_RAIN": case "RAIN_PERIODICALLY_HEAVY": return 65;
            case "HAIL": case "HAIL_SHOWERS": return 77;
            case "THUNDERSTORM": case "THUNDERSHOWER": case "LIGHT_THUNDERSTORM_RAIN":
            case "SCATTERED_THUNDERSTORMS": case "HEAVY_THUNDERSTORM": return 95;
        }
        if (t.contains("SNOW")) return 73;
        return t.isEmpty() ? -1 : 3;
    }

    // "2026-10-20T00:31:12.345Z" (UTC) to "6:01 am" in Madurai.
    private static String clock(String iso) {
        if (iso == null || iso.length() < 19) return null;
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone(TZ));
            c.setTime(in.parse(iso.substring(0, 19)));
            int h = c.get(Calendar.HOUR_OF_DAY);
            return (h % 12 == 0 ? 12 : h % 12) + String.format(Locale.ROOT, ":%02d", c.get(Calendar.MINUTE))
                    + (h < 12 ? " am" : " pm");
        } catch (Exception e) {
            return null;
        }
    }
}
