package com.ansub.tvhome;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Festivals and holidays from assets/holidays.csv ("date,name", dates as YYYY-MM-DD), read once.
// If the file is missing or broken the list is just empty, and the tile says so.
final class Holidays {
    static final class Next {
        final String date, names; // names of every entry on that date, joined
        final int y, m, d;
        final long days; // from the day asked about: 0 is that day
        Next(String date, String names, long days) {
            this.date = date;
            this.names = names;
            this.y = Integer.parseInt(date.substring(0, 4));
            this.m = Integer.parseInt(date.substring(5, 7));
            this.d = Integer.parseInt(date.substring(8, 10));
            this.days = days;
        }
    }

    private static List<String[]> list; // {date, name}, sorted by date

    private Holidays() {}

    // Tamil names for the festivals in the list; anything not here stays in English.
    private static final String[][] TAMIL = {
        {"Dussehra", "விஜயதசமி"}, {"Maha Ashtami", "மகா அஷ்டமி"}, {"Pongal", "பொங்கல்"},
        {"Diwali/Deepavali", "தீபாவளி"}, {"Diwali", "தீபாவளி"}, {"Deepavali", "தீபாவளி"},
        {"Independence Day", "சுதந்திர தினம்"}, {"Republic Day", "குடியரசு தினம்"},
        {"Gandhi Jayanti", "காந்தி ஜெயந்தி"}, {"Christmas", "கிறிஸ்துமஸ்"},
        {"New Year's Day", "புத்தாண்டு"}, {"Labour Day", "தொழிலாளர் தினம்"},
        {"Ayudha Puja", "ஆயுத பூஜை"},
        {"Maharishi Valmiki Jayanti", "மகரிஷி வால்மீகி ஜெயந்தி"},
    };

    /** Tamil name for each festival in a joined list ("A, B"); unknown names are kept as they are. */
    static String tamilName(String names) {
        if (names == null) return "";
        StringBuilder out = new StringBuilder();
        for (String part : names.split(", ")) {
            String ta = part;
            for (String[] pair : TAMIL) if (pair[0].equalsIgnoreCase(part.trim())) { ta = pair[1]; break; }
            if (out.length() > 0) out.append(", ");
            out.append(ta);
        }
        return out.toString();
    }

    static synchronized boolean load(Context c) {
        if (list != null) return !list.isEmpty();
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("holidays.csv"), "UTF-8"))) {
            for (String line; (line = r.readLine()) != null; ) {
                int comma = line.indexOf(',');
                if (comma != 10) continue; // also skips the header
                String date = line.substring(0, 10), name = line.substring(11).trim();
                if (name.startsWith("\"") && name.endsWith("\"") && name.length() > 1)
                    name = name.substring(1, name.length() - 1);
                if (date.matches("\\d{4}-\\d{2}-\\d{2}") && !name.isEmpty()) out.add(new String[] {date, name});
            }
        } catch (Exception ignored) {}
        Collections.sort(out, (a, b) -> a[0].compareTo(b[0]));
        list = out;
        return !out.isEmpty();
    }

    // The first date on or after (y, m, d) with an entry, or null.
    static Next next(Context c, int y, int m, int d) {
        if (!load(c)) return null;
        String from = Weather.isoDate(y, m, d);
        String date = null;
        StringBuilder names = new StringBuilder();
        for (String[] e : list) {
            if (e[0].compareTo(from) < 0) continue;
            if (date == null) date = e[0];
            if (!e[0].equals(date)) break;
            if (names.length() > 0) names.append(", ");
            names.append(e[1]);
        }
        if (date == null) return null;
        Next n = new Next(date, names.toString(), 0);
        long days = TamilDate.epochDay(n.y, n.m, n.d) - TamilDate.epochDay(y, m, d);
        return new Next(date, n.names, days);
    }

    // Entries on exactly that date, joined, or null.
    static String on(Context c, int y, int m, int d) {
        Next n = next(c, y, m, d);
        return n != null && n.days == 0 ? n.names : null;
    }
}
