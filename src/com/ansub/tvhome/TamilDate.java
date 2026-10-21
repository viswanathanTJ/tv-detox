package com.ansub.tvhome;

// The Tamil calendar, computed here because there is no reliable Tamil calendar service.
// Plain Java (no Android classes), so it can be checked on a computer.
//
// Tamil month: the sidereal sign the sun is in (Chithirai = Mesham ... Panguni = Meenam), sidereal
// meaning the tropical longitude minus the Lahiri ayanamsa. The month starts on the day the sun
// enters the sign if that happens before sunset in Madurai, else on the next day (the usual Tamil
// rule). So a day belongs to the sign the sun is in at that day's sunset, and the day number counts
// from the first such day. Examples: in 2026 the sun enters sidereal Libra on 17 Oct about 19:47 IST,
// after sunset, so Aippasi 1 is 18 Oct and 20 Oct is Aippasi 3; it enters Mesham on 14 Apr in the
// morning, so Chithirai 1 (Tamil New Year) is 14 Apr.
//
// Amavasai and Pournami follow the tithi at local sunrise: Moon-Sun elongation 348-360 degrees
// (tithi 30) is Amavasai, 168-180 degrees (tithi 15) is Pournami. A tithi can cover two sunrises or
// none, so to mark exactly one day per new or full moon, the day marked is the one whose span from
// sunrise to the next sunrise contains the new (or full) moon: on that day the tithi at sunrise is
// 30 (or 15) in the normal case, and the second day is taken when it covers two sunrises.
//
// Accuracy: the sun formula is good to about 0.01 degree and the moon terms to about 0.01-0.02 degree,
// which is roughly 15-30 minutes of time. The day number and the events are APPROXIMATE on the rare
// days when the sun changes sign, or the moon reaches new or full, within about half an hour of
// sunset or sunrise; printed panchangams can also differ from each other on those days.
final class TamilDate {
    // Madurai, for sunrise. Same place as the weather.
    static final double LAT = 9.9181, LON = 78.1344;
    static final double IST_HOURS = 5.5;

    static final String[] MONTHS_EN = {"Chithirai", "Vaikasi", "Aani", "Aadi", "Aavani", "Purattasi",
            "Aippasi", "Karthigai", "Margazhi", "Thai", "Maasi", "Panguni"};
    static final String[] MONTHS_TA = {"சித்திரை", "வைகாசி", "ஆனி", "ஆடி", "ஆவணி", "புரட்டாசி",
            "ஐப்பசி", "கார்த்திகை", "மார்கழி", "தை", "மாசி", "பங்குனி"};
    // Sunday first, like java.util.Calendar (SUNDAY = 1).
    static final String[] WEEKDAYS_TA = {"ஞாயிறு", "திங்கள்", "செவ்வாய்", "புதன்", "வியாழன்", "வெள்ளி", "சனி"};
    static final String[] WEEKDAYS_EN = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    // Lunar and solar events, in Tamil script with an English transliteration.
    static final int NONE = 0, AMAVASAI = 1, POURNAMI = 2, MONTH_START = 3;
    static final String[] EVENTS_TA = {"", "அமாவாசை", "பௌர்ணமி", "புது மாதம்"};
    static final String[] EVENTS_EN = {"", "Amavasai", "Pournami", "Puthu maatham"};

    private TamilDate() {}

    // The result for one date: month index (0 = Chithirai), day number, and the event (NONE if none).
    static final class Day {
        final int month, day, event;
        Day(int month, int day, int event) { this.month = month; this.day = day; this.event = event; }
        String monthTa() { return MONTHS_TA[month]; }
        String monthEn() { return MONTHS_EN[month]; }
        String eventTa() { return EVENTS_TA[event]; }
        String eventEn() { return EVENTS_EN[event]; }
        // "ஐப்பசி 3"
        String ta() { return MONTHS_TA[month] + " " + day; }
    }

    static Day of(int y, int m, int d) {
        long ed = epochDay(y, m, d);
        int sign = signAtSunset(ed);
        int n = 1;
        while (n < 40 && signAtSunset(ed - n) == sign) n++;
        int event = NONE;
        double e0 = elongation(riseSetJd(ed, true)), e1 = elongation(riseSetJd(ed + 1, true));
        if (n == 1) event = MONTH_START;
        else if (e1 < e0) event = AMAVASAI;              // passed 360 (new moon) before the next sunrise
        else if (e0 < 180 && e1 >= 180) event = POURNAMI; // passed 180 (full moon) before the next sunrise
        return new Day(sign, n, event);
    }

    // Weekday for a date: 0 = Sunday ... 6 = Saturday.
    static int weekday(int y, int m, int d) {
        return (int) fmod(epochDay(y, m, d) + 4, 7); // 1970-01-01 was a Thursday
    }

    // Days since 1970-01-01 for a calendar date (proleptic Gregorian).
    static long epochDay(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = fdiv(y, 400);
        long yoe = y - era * 400;
        long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    // Calendar date for an epoch day: {year, month, day}.
    static int[] civil(long ed) {
        long z = ed + 719468;
        long era = fdiv(z, 146097);
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        int d = (int) (doy - (153 * mp + 2) / 5 + 1);
        int m = (int) (mp < 10 ? mp + 3 : mp - 9);
        int y = (int) (yoe + era * 400 + (m <= 2 ? 1 : 0));
        return new int[] {y, m, d};
    }

    private static int signAtSunset(long ed) {
        return (int) Math.floor(siderealSun(riseSetJd(ed, false)) / 30.0) % 12;
    }

    // Julian day (UT) of 00:00 UT on an epoch day.
    private static double jd0(long ed) {
        return ed + 2440587.5;
    }

    // Julian day (UT) of sunrise (or sunset) in Madurai on that local date: the standard sunrise
    // equation (upper limb, refraction -0.833 degrees), two passes. Good to a minute or two.
    static double riseSetJd(long ed, boolean rise) {
        double jd = jd0(ed) + ((rise ? 6.0 : 18.0) - IST_HOURS) / 24.0; // start near 6 am / 6 pm IST
        for (int pass = 0; pass < 2; pass++) {
            double t = (jd - 2451545.0) / 36525.0;
            double lambda = Math.toRadians(sunLongitude(jd));
            double eps = Math.toRadians(23.439291 - 0.0130042 * t);
            double dec = Math.asin(Math.sin(eps) * Math.sin(lambda));
            double phi = Math.toRadians(LAT);
            double cosH = (Math.sin(Math.toRadians(-0.833)) - Math.sin(phi) * Math.sin(dec))
                    / (Math.cos(phi) * Math.cos(dec));
            double h = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosH))));
            // Equation of time, minutes (from the mean longitude and right ascension).
            double l0 = norm(280.46646 + 36000.76983 * t);
            double ra = Math.toDegrees(Math.atan2(Math.cos(eps) * Math.sin(lambda), Math.cos(lambda)));
            double eot = 4 * wrap180(l0 - 0.0057183 - ra);
            double noonUt = 12 - LON / 15.0 - eot / 60.0; // hours UT
            jd = jd0(ed) + (noonUt + (rise ? -h : h) / 15.0) / 24.0;
        }
        return jd;
    }

    // Apparent tropical longitude of the sun, degrees (Meeus ch. 25, low precision, about 0.01 degree).
    static double sunLongitude(double jd) {
        double t = (jd - 2451545.0) / 36525.0;
        double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        double m = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t);
        double c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(m)
                + (0.019993 - 0.000101 * t) * Math.sin(2 * m) + 0.000289 * Math.sin(3 * m);
        double omega = Math.toRadians(125.04 - 1934.136 * t);
        return norm(l0 + c - 0.00569 - 0.00478 * Math.sin(omega));
    }

    // Lahiri (Chitrapaksha) ayanamsa, degrees: 23.85306 degrees at J2000 (23 deg 51' 11")
    // plus the general precession, about 50.29" a year. Gives about 24.22 degrees in late 2026.
    static double ayanamsa(double jd) {
        double t = (jd - 2451545.0) / 36525.0;
        return 23.85306 + 1.396971 * t + 0.0003086 * t * t;
    }

    static double siderealSun(double jd) {
        return norm(sunLongitude(jd) - ayanamsa(jd));
    }

    // Geocentric longitude of the moon, degrees (Meeus ch. 47, the main periodic terms).
    static double moonLongitude(double jd) {
        double t = (jd - 2451545.0) / 36525.0;
        double lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t * t;
        double d = Math.toRadians(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t);
        double m = Math.toRadians(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t);
        double mp = Math.toRadians(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t);
        double f = Math.toRadians(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t);
        double e = 1 - 0.002516 * t - 0.0000074 * t * t;
        double s = 6288774 * Math.sin(mp)
                + 1274027 * Math.sin(2 * d - mp)
                + 658314 * Math.sin(2 * d)
                + 213618 * Math.sin(2 * mp)
                - 185116 * e * Math.sin(m)
                - 114332 * Math.sin(2 * f)
                + 58793 * Math.sin(2 * d - 2 * mp)
                + 57066 * e * Math.sin(2 * d - m - mp)
                + 53322 * Math.sin(2 * d + mp)
                + 45758 * e * Math.sin(2 * d - m)
                - 40923 * e * Math.sin(m - mp)
                - 34720 * Math.sin(d)
                - 30383 * e * Math.sin(m + mp)
                + 15327 * Math.sin(2 * d - 2 * f)
                - 12528 * Math.sin(mp + 2 * f)
                + 10980 * Math.sin(mp - 2 * f)
                + 10675 * Math.sin(4 * d - mp)
                + 10034 * Math.sin(3 * mp)
                + 8548 * Math.sin(4 * d - 2 * mp)
                - 7888 * e * Math.sin(2 * d + m - mp)
                - 6766 * e * Math.sin(2 * d + m)
                - 5163 * Math.sin(d - mp)
                + 4987 * e * Math.sin(d + m)
                + 4036 * e * Math.sin(2 * d - m + mp)
                + 3994 * Math.sin(2 * d + 2 * mp)
                + 3861 * Math.sin(4 * d)
                + 3665 * Math.sin(2 * d - 3 * mp)
                - 2689 * e * Math.sin(m - 2 * mp)
                - 2602 * Math.sin(2 * d - mp + 2 * f)
                + 2390 * e * Math.sin(2 * d - m - 2 * mp)
                - 2348 * Math.sin(d + mp)
                + 2236 * e * e * Math.sin(2 * d - 2 * m)
                - 2120 * e * Math.sin(m + 2 * mp)
                - 2069 * e * e * Math.sin(2 * m);
        // Venus, Jupiter and the Earth's flattening.
        double a1 = Math.toRadians(119.75 + 131.849 * t), a2 = Math.toRadians(53.09 + 479264.290 * t);
        double lpr = Math.toRadians(lp);
        s += 3958 * Math.sin(a1) + 1962 * Math.sin(lpr - f) + 318 * Math.sin(a2);
        double omega = Math.toRadians(125.04 - 1934.136 * t);
        return norm(lp + s / 1e6 - 0.00478 * Math.sin(omega)); // same nutation as the sun's, so it cancels
    }

    // Moon minus sun, 0 to 360 degrees: 0 is new moon, 180 full moon. Tithi = floor(e / 12) + 1.
    static double elongation(double jd) {
        return norm(moonLongitude(jd) - sunLongitude(jd));
    }

    // Mean new moon (k whole) or full moon (k + 0.5), Julian day in TT, with the main correction
    // terms (Meeus ch. 49). Used only to check the elongation above; about a minute or two off.
    static double phaseJd(double k, boolean full) {
        double t = k / 1236.85;
        double jde = 2451550.09766 + 29.530588861 * k + 0.00015437 * t * t;
        double m = Math.toRadians(norm(2.5534 + 29.10535670 * k));
        double mp = Math.toRadians(norm(201.5643 + 385.81693528 * k));
        double f = Math.toRadians(norm(160.7108 + 390.67050284 * k));
        double c = full
                ? -0.40614 * Math.sin(mp) + 0.17302 * Math.sin(m) + 0.01614 * Math.sin(2 * mp) + 0.01043 * Math.sin(2 * f)
                  + 0.00734 * Math.sin(mp - m) - 0.00515 * Math.sin(mp + m) + 0.00209 * Math.sin(2 * m)
                  - 0.00111 * Math.sin(mp - 2 * f) - 0.00057 * Math.sin(mp + 2 * f)
                : -0.40720 * Math.sin(mp) + 0.17241 * Math.sin(m) + 0.01608 * Math.sin(2 * mp) + 0.01039 * Math.sin(2 * f)
                  + 0.00739 * Math.sin(mp - m) - 0.00514 * Math.sin(mp + m) + 0.00208 * Math.sin(2 * m)
                  - 0.00111 * Math.sin(mp - 2 * f) - 0.00057 * Math.sin(mp + 2 * f);
        return jde + c;
    }

    // The first Amavasai, Pournami or month start on or after the date, within 40 days:
    // {year, month, day, event}, or null.
    static int[] nextEvent(int y, int m, int d) {
        long ed = epochDay(y, m, d);
        for (int i = 0; i <= 40; i++) {
            int[] c = civil(ed + i);
            Day t = of(c[0], c[1], c[2]);
            if (t.event != NONE) return new int[] {c[0], c[1], c[2], t.event};
        }
        return null;
    }

    // Math.floorDiv and floorMod need Android 7, so these do the same for older TVs.
    private static long fdiv(long a, long b) {
        long q = a / b;
        return (a % b != 0 && (a < 0) != (b < 0)) ? q - 1 : q;
    }

    private static long fmod(long a, long b) {
        return a - fdiv(a, b) * b;
    }

    private static double norm(double a) {
        a %= 360;
        return a < 0 ? a + 360 : a;
    }

    private static double wrap180(double a) {
        a = norm(a);
        return a > 180 ? a - 360 : a;
    }
}
