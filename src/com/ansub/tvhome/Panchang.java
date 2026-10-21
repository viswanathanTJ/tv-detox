package com.ansub.tvhome;

import java.util.ArrayList;
import java.util.List;

// The Tamil daily panchangam for one date in Madurai. Plain Java (no Android classes), so it can be
// checked on a computer.
//
// Printed Tamil calendars use the Vakya method, so dates in assets/panchang-vakya.csv (written by
// tools/vakya_panchang.py from the KKC Astro Vakya engine; see load()) take everything from there:
// Tamil date, year, elements with their end times, and observances. Other dates are computed here
// from the sun and moon in TamilDate (the astronomical method), which can differ from a printed
// calendar by an hour or two, and sometimes by a day on the date an observance falls.
//
// The day runs from sunrise to the next sunrise, like a printed panchangam: each element is the one
// in force at sunrise, with the time it ends and the one after it.
//   Tithi: moon minus sun, 12 degrees each (30 in a lunar month).
//   Nakshatram: the moon's sidereal longitude, 13 deg 20' each (27).
//   Yogam: sun plus moon (sidereal), 13 deg 20' each (27).
//   Karanam: half a tithi, 6 degrees each (60 in a lunar month, 11 names).
// End times inherit TamilDate's accuracy: about 15-30 minutes. The fixed timings (Raagu kalam,
// Yamagandam, Kuligai, Nalla neram, Gowri, Soolam) are the weekday tables printed Tamil calendars
// use, which assume a 6 am sunrise.
final class Panchang {
    static final String[] TITHI_TA = {"பிரதமை", "துவிதியை", "திருதியை", "சதுர்த்தி", "பஞ்சமி", "சஷ்டி",
            "சப்தமி", "அஷ்டமி", "நவமி", "தசமி", "ஏகாதசி", "துவாதசி", "திரயோதசி", "சதுர்த்தசி", "பௌர்ணமி",
            "பிரதமை", "துவிதியை", "திருதியை", "சதுர்த்தி", "பஞ்சமி", "சஷ்டி", "சப்தமி", "அஷ்டமி", "நவமி",
            "தசமி", "ஏகாதசி", "துவாதசி", "திரயோதசி", "சதுர்த்தசி", "அமாவாசை"};
    static final String[] TITHI_EN = {"Prathamai", "Dwitiyai", "Tritiyai", "Chaturthi", "Panchami", "Shashti",
            "Saptami", "Ashtami", "Navami", "Dasami", "Ekadasi", "Dwadasi", "Trayodasi", "Chaturdasi", "Pournami",
            "Prathamai", "Dwitiyai", "Tritiyai", "Chaturthi", "Panchami", "Shashti", "Saptami", "Ashtami", "Navami",
            "Dasami", "Ekadasi", "Dwadasi", "Trayodasi", "Chaturdasi", "Amavasai"};
    static final String[] NAKSHATRA_TA = {"அசுவினி", "பரணி", "கார்த்திகை", "ரோகிணி", "மிருகசீரிடம்",
            "திருவாதிரை", "புனர்பூசம்", "பூசம்", "ஆயில்யம்", "மகம்", "பூரம்", "உத்திரம்", "அஸ்தம்", "சித்திரை",
            "சுவாதி", "விசாகம்", "அனுஷம்", "கேட்டை", "மூலம்", "பூராடம்", "உத்திராடம்", "திருவோணம்", "அவிட்டம்",
            "சதயம்", "பூரட்டாதி", "உத்திரட்டாதி", "ரேவதி"};
    static final String[] NAKSHATRA_EN = {"Aswini", "Bharani", "Karthigai", "Rohini", "Mirugasirisham",
            "Thiruvathirai", "Punarpoosam", "Poosam", "Ayilyam", "Magam", "Pooram", "Uthiram", "Hastham", "Chithirai",
            "Swathi", "Visakam", "Anusham", "Kettai", "Moolam", "Pooradam", "Uthiradam", "Thiruvonam", "Avittam",
            "Sathayam", "Poorattathi", "Uthirattathi", "Revathi"};
    static final String[] YOGA_TA = {"விஷ்கம்பம்", "பிரீதி", "ஆயுஷ்மான்", "சௌபாக்கியம்", "சோபனம்",
            "அதிகண்டம்", "சுகர்மம்", "திருதி", "சூலம்", "கண்டம்", "விருத்தி", "துருவம்", "வியாகாதம்", "ஹர்ஷணம்",
            "வஜ்ரம்", "சித்தி", "வியதீபாதம்", "வரியான்", "பரிகம்", "சிவம்", "சித்தம்", "சாத்தியம்", "சுபம்",
            "சுப்பிரம்", "பிராம்யம்", "ஐந்திரம்", "வைதிருதி"};
    static final String[] YOGA_EN = {"Vishkambam", "Preethi", "Ayushman", "Sowbhagyam", "Sobhanam",
            "Athigandam", "Sukarmam", "Dhriti", "Soolam", "Gandam", "Vriddhi", "Dhruvam", "Vyaghatham", "Harshanam",
            "Vajram", "Siddhi", "Vyatipatham", "Variyan", "Parigam", "Sivam", "Siddham", "Sadhyam", "Subham",
            "Subhram", "Brahmyam", "Aindram", "Vaidhruthi"};
    // 0-6 repeat through the month; 7-10 come once, around the new moon.
    static final String[] KARANA_TA = {"பவம்", "பாலவம்", "கௌலவம்", "தைதுலம்", "கரசை", "வணிசை", "பத்திரை",
            "சகுனி", "சதுஷ்பாதம்", "நாகவம்", "கிம்ஸ்துக்னம்"};
    static final String[] KARANA_EN = {"Bavam", "Balavam", "Kaulavam", "Taitulam", "Karasai", "Vanisai", "Bhadrai",
            "Sakuni", "Chatushpadam", "Nagavam", "Kimstughnam"};
    static final String[] RASI_TA = {"மேஷம்", "ரிஷபம்", "மிதுனம்", "கடகம்", "சிம்மம்", "கன்னி", "துலாம்",
            "விருச்சிகம்", "தனுசு", "மகரம்", "கும்பம்", "மீனம்"};
    static final String[] RASI_EN = {"Mesham", "Rishabam", "Mithunam", "Kadagam", "Simmam", "Kanni", "Thulam",
            "Viruchigam", "Dhanusu", "Magaram", "Kumbam", "Meenam"};
    // The 60-year cycle; Prabhava began in April 1987, so 2026-27 is Parabhava.
    static final String[] YEARS_TA = {"பிரபவ", "விபவ", "சுக்ல", "பிரமோதூத", "பிரசோற்பத்தி", "ஆங்கீரச",
            "ஸ்ரீமுக", "பவ", "யுவ", "தாது", "ஈஸ்வர", "வெகுதானிய", "பிரமாதி", "விக்கிரம", "விஷு", "சித்திரபானு",
            "சுபானு", "தாரண", "பார்த்திப", "விய", "சர்வசித்து", "சர்வதாரி", "விரோதி", "விக்ருதி", "கர", "நந்தன",
            "விஜய", "ஜய", "மன்மத", "துன்முகி", "ஹேவிளம்பி", "விளம்பி", "விகாரி", "சார்வரி", "பிலவ", "சுபகிருது",
            "சோபகிருது", "குரோதி", "விசுவாவசு", "பராபவ", "பிலவங்க", "கீலக", "சௌமிய", "சாதாரண", "விரோதகிருது",
            "பரிதாபி", "பிரமாதீச", "ஆனந்த", "ராட்சச", "நள", "பிங்கள", "காளயுக்தி", "சித்தார்த்தி", "ரௌத்திரி",
            "துன்மதி", "துந்துபி", "ருத்ரோத்காரி", "ரக்தாட்சி", "குரோதன", "அட்சய"};
    // By Tamil month (0 = Chithirai): two months to a season.
    static final String[] RUTHU_TA = {"வசந்த", "கிரீஷ்ம", "வர்ஷ", "சரத்", "ஹேமந்த", "சிசிர"};

    // Fixed timings, Sunday first, as minutes after midnight {start, end}. Printed Tamil calendars
    // split a 6 am - 6 pm day into eight 90-minute parts and give each weekday its parts.
    static final int[][] RAAGU = {{990, 1080}, {450, 540}, {900, 990}, {720, 810}, {810, 900}, {630, 720}, {540, 630}};
    static final int[][] YAMA = {{720, 810}, {630, 720}, {540, 630}, {450, 540}, {360, 450}, {900, 990}, {810, 900}};
    static final int[][] KULIGAI = {{900, 990}, {810, 900}, {720, 810}, {630, 720}, {540, 630}, {450, 540}, {360, 450}};
    // Nalla neram: {morning start, end, evening start, end}. Printed calendars don't follow one fixed
    // table; these are the slots Daily Thanthi and Golden Chennai print most often (October 2026).
    static final int[][] NALLA = {{465, 525, 915, 975}, {375, 435, 1005, 1065}, {465, 525, 1005, 1065},
            {555, 615, 1005, 1065}, {645, 705, 735, 795}, {555, 615, 1005, 1065}, {645, 705, 1005, 1065}};
    // Gowri nalla neram, the same way; -1 where calendars print no slot (Thursday morning).
    static final int[][] GOWRI = {{645, 705, 810, 870}, {555, 615, 1170, 1230}, {645, 705, 1170, 1230},
            {645, 705, 1110, 1170}, {-1, -1, 1110, 1170}, {735, 795, 1110, 1170}, {645, 705, 1290, 1350}};
    // Kari naal: fixed days of each Tamil month (0 = Chithirai), as dinamalar lists them.
    static final int[][] KARI = {{6, 15}, {7, 16, 17}, {1, 6}, {2, 10, 20}, {2, 9, 28}, {16, 29}, {6, 20},
            {1, 10, 17}, {6, 9, 11}, {1, 2, 3, 11, 17}, {15, 16, 17}, {6, 15, 19}};
    // Vasthu naal: {Tamil month, day, pooja start, end} (the 36-minute window most calendars print).
    static final int[][] VASTHU = {{9, 12, 641, 677}, {10, 22, 632, 668}, {0, 10, 534, 570}, {1, 21, 598, 634},
            {3, 11, 464, 500}, {4, 6, 443, 479}, {6, 11, 464, 500}, {7, 8, 689, 725}};
    // Soolam: the direction to avoid travelling, and its remedy.
    static final String[] SOOLAM_TA = {"மேற்கு", "கிழக்கு", "வடக்கு", "வடக்கு", "தெற்கு", "மேற்கு", "கிழக்கு"};
    static final String[] SOOLAM_EN = {"West", "East", "North", "North", "South", "West", "East"};
    static final String[] PARIGARAM_TA = {"வெல்லம்", "தயிர்", "பால்", "பால்", "தைலம்", "வெல்லம்", "தயிர்"};

    // Monthly observances, from the tithi or nakshatram at the time each is kept.
    static final String EKADASI = "ekadasi", PRADOSHAM = "pradosham", SHASHTI = "shashti",
            THEI_SHASHTI = "thei_shashti", CHATURTHI = "chaturthi", SANKATAHARA = "sankatahara_chaturthi",
            SIVARATHRI = "masa_sivarathri", ASHTAMI = "ashtami", NAVAMI = "navami", KARTHIGAI = "karthigai",
            THIRUVONAM = "thiruvonam", THIRUVATHIRAI = "thiruvathirai", CHANDRA = "chandra_darisanam",
            POURNAMI = "pournami", AMAVASAI = "amavasai", KARI_NAAL = "kari_naal", VASTHU_NAAL = "vasthu";
    // Every observance key with its Tamil and English name.
    private static final String[][] NAMES = {
        {AMAVASAI, "அமாவாசை", "Amavasai"}, {POURNAMI, "பௌர்ணமி", "Pournami"},
        {EKADASI, "ஏகாதசி", "Ekadasi"}, {PRADOSHAM, "பிரதோஷம்", "Pradosham"},
        {SHASHTI, "ஷஷ்டி", "Shashti"}, {THEI_SHASHTI, "தேய்பிறை ஷஷ்டி", "Theipirai Shashti"},
        {CHATURTHI, "சதுர்த்தி", "Chaturthi"}, {SANKATAHARA, "சங்கடஹர சதுர்த்தி", "Sankatahara Chathurthi"},
        {SIVARATHRI, "மாத சிவராத்திரி", "Masa Sivarathri"}, {ASHTAMI, "அஷ்டமி", "Ashtami"},
        {NAVAMI, "நவமி", "Navami"}, {KARTHIGAI, "கார்த்திகை", "Karthigai"},
        {THIRUVONAM, "திருவோணம்", "Thiruvonam"}, {THIRUVATHIRAI, "திருவாதிரை", "Thiruvathirai"},
        {CHANDRA, "சந்திர தரிசனம்", "Chandra darisanam"}, {KARI_NAAL, "கரி நாள்", "Kari naal"},
        {VASTHU_NAAL, "வாஸ்து நாள்", "Vasthu naal"},
    };

    static final class Observance {
        final String key, ta, en;
        Observance(String key, String ta, String en) { this.key = key; this.ta = ta; this.en = en; }
    }

    // One of tithi, nakshatram, yogam or karanam: the one at sunrise, when it ends (UT Julian day),
    // and the one after it.
    static final class Element {
        final int index, next;
        final double endJd;
        final boolean endsToday; // before the next sunrise
        Element(int index, int next, double endJd, boolean endsToday) {
            this.index = index; this.next = next; this.endJd = endJd; this.endsToday = endsToday;
        }
    }

    static final class Day {
        int y, m, d, weekday;
        long epochDay;
        TamilDate.Day tamil;
        String yearTa, ayanamTa, ruthuTa;
        double sunriseJd, sunsetJd;
        Element tithi, nakshatra, yoga, karana;
        boolean valarpirai; // waxing, at sunrise
        int moonRasi, moonRasiNext = -1; // next is set when the moon changes sign before the next sunrise
        double moonRasiEndJd;
        int chandrashtamam; // the rasi whose people have Chandrashtamam today
        int[] vasthu;       // {start, end} minutes on a Vasthu day, else null
        boolean vakya;      // from the Vakya data, not computed here
        final List<Observance> observances = new ArrayList<>();

        String tithiTa(int i) { return TITHI_TA[i]; }
    }

    private Panchang() {}

    static Day of(int y, int m, int d) {
        Day p = new Day();
        p.y = y; p.m = m; p.d = d;
        p.epochDay = TamilDate.epochDay(y, m, d);
        p.weekday = TamilDate.weekday(y, m, d);
        double rise = TamilDate.riseSetJd(p.epochDay, true);
        p.sunriseJd = rise;
        p.sunsetJd = TamilDate.riseSetJd(p.epochDay, false);
        String[] row = vakya == null ? null : vakya.get(p.epochDay);
        if (row != null) fromVakya(p, row);
        else compute(p, rise);
        int tm = p.tamil.month;
        p.ayanamTa = (tm >= 3 && tm <= 8) ? "தட்சிணாயனம்" : "உத்தராயணம்";
        p.ruthuTa = RUTHU_TA[tm / 2];
        p.valarpirai = p.tithi.index < 15;
        // The moon in the 8th sign from yours: yours is 7 signs behind the moon's.
        p.chandrashtamam = (p.moonRasi + 5) % 12;
        for (int k : KARI[tm]) if (k == p.tamil.day) add(p, KARI_NAAL);
        for (int[] v : VASTHU) if (v[0] == tm && v[1] == p.tamil.day) {
            add(p, VASTHU_NAAL);
            p.vasthu = new int[] {v[2], v[3]};
        }
        return p;
    }

    // ---- Vakya data ----

    // Rows of assets/panchang-vakya.csv by epoch day; see tools/vakya_panchang.py for the columns.
    private static java.util.Map<Long, String[]> vakya;

    // Reads the CSV once (the app passes the asset). A missing or broken file just means every date is
    // computed here.
    static synchronized void load(java.io.Reader in) {
        if (vakya != null) return;
        java.util.Map<Long, String[]> out = new java.util.HashMap<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(in)) {
            r.readLine(); // header
            for (String line; (line = r.readLine()) != null; ) {
                String[] f = line.split(",", -1);
                if (f.length < 20 || f[0].length() != 10) continue;
                out.put(TamilDate.epochDay(Integer.parseInt(f[0].substring(0, 4)),
                        Integer.parseInt(f[0].substring(5, 7)), Integer.parseInt(f[0].substring(8, 10))), f);
            }
        } catch (Exception ignored) {
            // Keep what was read.
        }
        vakya = out;
    }

    static boolean hasVakya(long epochDay) {
        return vakya != null && vakya.containsKey(epochDay);
    }

    private static void fromVakya(Day p, String[] f) {
        p.vakya = true;
        int tm = Integer.parseInt(f[1]), td = Integer.parseInt(f[2]);
        java.util.List<String> obs = java.util.Arrays.asList(f[19].split(";"));
        int event = td == 1 ? TamilDate.MONTH_START : obs.contains(AMAVASAI) ? TamilDate.AMAVASAI
                : obs.contains(POURNAMI) ? TamilDate.POURNAMI : TamilDate.NONE;
        p.tamil = new TamilDate.Day(tm, td, event);
        p.yearTa = YEARS_TA[Integer.parseInt(f[3])];
        double nextRise = TamilDate.riseSetJd(p.epochDay + 1, true);
        p.tithi = vakyaElement(p, f, 4, nextRise);
        p.nakshatra = vakyaElement(p, f, 7, nextRise);
        p.yoga = vakyaElement(p, f, 10, nextRise);
        p.karana = vakyaElement(p, f, 13, nextRise);
        p.moonRasi = Integer.parseInt(f[16]);
        if (!f[17].isEmpty()) {
            p.moonRasiNext = Integer.parseInt(f[18]);
            p.moonRasiEndJd = minuteJd(p, Integer.parseInt(f[17]));
        }
        for (String key : obs) if (!key.isEmpty()) add(p, key);
    }

    private static Element vakyaElement(Day p, String[] f, int at, double nextRise) {
        double end = minuteJd(p, Integer.parseInt(f[at + 1]));
        return new Element(Integer.parseInt(f[at]), Integer.parseInt(f[at + 2]), end, end < nextRise);
    }

    // A minute after the day's IST midnight, as a Julian day (UT).
    private static double minuteJd(Day p, int minute) {
        return dayJd(p.epochDay) + minute / 1440.0 - TamilDate.IST_HOURS / 24.0;
    }

    // ---- Computed (outside the Vakya data) ----

    private static void compute(Day p, double rise) {
        int y = p.y, m = p.m;
        p.tamil = TamilDate.of(y, m, p.d);
        int startYear = (m > 4 || (m == 4 && p.tamil.month != 11)) ? y : y - 1;
        p.yearTa = YEARS_TA[(int) (((startYear - 1987) % 60 + 60) % 60)];
        double nextRise = TamilDate.riseSetJd(p.epochDay + 1, true);

        p.tithi = element(TITHI, rise, nextRise);
        p.nakshatra = element(NAKSHATRA, rise, nextRise);
        p.yoga = element(YOGA, rise, nextRise);
        Element k = element(KARANA, rise, nextRise);
        p.karana = new Element(karanaName(k.index), karanaName(k.next), k.endJd, k.endsToday);
        Element r = element(RASI, rise, nextRise);
        p.moonRasi = r.index;
        if (r.endsToday) { p.moonRasiNext = r.next; p.moonRasiEndJd = r.endJd; }
        observances(p, rise);
    }

    // ---- Observances ----

    private static void observances(Day p, double rise) {
        double set = p.sunsetJd;
        int atRise = tithiAt(rise), atSet = tithiAt(set);
        // Noon, late evening and midnight, IST.
        double noon = dayJd(p.epochDay) + (12 - TamilDate.IST_HOURS) / 24.0;
        double nine = dayJd(p.epochDay) + (21 - TamilDate.IST_HOURS) / 24.0;
        int atNoon = tithiAt(noon);
        int before = tithiAt(TamilDate.riseSetJd(p.epochDay - 1, false));
        // Pournami and Amavasai: the evening they hold (or, for one too short to reach a sunset, the
        // evening right after it), as printed calendars mark them.
        if (atSet == 14 || (atSet == 15 && before == 13)) add(p, POURNAMI);
        if (atSet == 29 || (atSet == 0 && before == 27)) add(p, AMAVASAI);
        if (atRise == 10 || atRise == 25) add(p, EKADASI);
        if (atSet == 12 || atSet == 27) add(p, PRADOSHAM);
        if (atRise == 5) add(p, SHASHTI);
        if (atRise == 20) add(p, THEI_SHASHTI);
        if (atRise == 3) add(p, CHATURTHI);
        if (tithiAt(nine) == 18) add(p, SANKATAHARA);
        if (atSet == 28) add(p, SIVARATHRI);
        // Ashtami and Navami go on the first day they hold at noon, as calendars print them (a long
        // tithi can hold at two noons; it's still one Ashtami).
        int noonBefore = tithiAt(noon - 1);
        if ((atNoon == 7 || atNoon == 22) && noonBefore != atNoon) add(p, ASHTAMI);
        if ((atNoon == 8 || atNoon == 23) && noonBefore != atNoon) add(p, NAVAMI);
        // Karthigai and Thiruvonam are kept in the evening: the star at 9 pm.
        int starNine = starAt(nine);
        if (starNine == 2) add(p, KARTHIGAI);
        if (starNine == 21) add(p, THIRUVONAM);
        if (p.nakshatra.index == 5) add(p, THIRUVATHIRAI);
        // The first evening the new crescent can be seen: the moon at least 12 degrees from the sun at
        // sunset (past Prathamai), when it wasn't the evening before.
        if ((atSet == 1 || atSet == 2) && (before == 29 || before == 0)) add(p, CHANDRA);
    }

    private static void add(Day p, String key) {
        for (String[] n : NAMES) if (n[0].equals(key)) { p.observances.add(new Observance(key, n[1], n[2])); return; }
    }

    // ---- Elements ----

    private static final int TITHI = 0, NAKSHATRA = 1, YOGA = 2, KARANA = 3, RASI = 4;

    // Degrees the element is measured in, and the size of one.
    private static double angle(int kind, double jd) {
        switch (kind) {
            case TITHI: case KARANA: return TamilDate.elongation(jd);
            case NAKSHATRA: case RASI: return moonSidereal(jd);
            default: return norm(TamilDate.siderealSun(jd) + moonSidereal(jd));
        }
    }

    private static double span(int kind) {
        switch (kind) {
            case TITHI: return 12;
            case KARANA: return 6;
            case RASI: return 30;
            default: return 360.0 / 27;
        }
    }

    private static int count(int kind) {
        return (int) Math.round(360 / span(kind));
    }

    private static Element element(int kind, double rise, double nextRise) {
        double sp = span(kind);
        int n = count(kind);
        int index = (int) Math.floor(angle(kind, rise) / sp) % n;
        double end = crossing(kind, rise, ((index + 1) % n) * sp);
        return new Element(index, (index + 1) % n, end, end < nextRise);
    }

    // When the element's angle next reaches the target, searching forward from jd: steps of six
    // hours until it's passed, then halving the gap to well under a minute.
    private static double crossing(int kind, double jd, double target) {
        double lo = jd, hi = jd;
        for (int i = 0; i < 16; i++) {
            hi = lo + 0.25;
            if (wrap180(angle(kind, hi) - target) >= 0) break;
            lo = hi;
        }
        for (int i = 0; i < 30; i++) {
            double mid = (lo + hi) / 2;
            if (wrap180(angle(kind, mid) - target) >= 0) hi = mid; else lo = mid;
        }
        return hi;
    }

    // Karanam number in the lunar month (0-59) to its name in KARANA_TA.
    private static int karanaName(int k) {
        if (k == 0) return 10;
        if (k >= 57) return k - 50; // 57 Sakuni, 58 Chatushpadam, 59 Nagavam
        return (k - 1) % 7;
    }

    static int tithiAt(double jd) {
        return (int) Math.floor(TamilDate.elongation(jd) / 12) % 30;
    }

    static int starAt(double jd) {
        return (int) Math.floor(moonSidereal(jd) / (360.0 / 27)) % 27;
    }

    static double moonSidereal(double jd) {
        return norm(TamilDate.moonLongitude(jd) - TamilDate.ayanamsa(jd));
    }

    // ---- Times ----

    // 00:00 UT of an epoch day, as a Julian day.
    private static double dayJd(long ed) {
        return ed + 2440587.5;
    }

    // Minutes after the day's IST midnight (more than 1440 for the next day).
    static int minutes(Day p, double jd) {
        double local = jd - dayJd(p.epochDay) + TamilDate.IST_HOURS / 24.0;
        return (int) Math.round(local * 1440);
    }

    // "11:05 am", or "tomorrow 7:10 am".
    static String clock(Day p, double jd) {
        int min = minutes(p, jd);
        return (min >= 1440 ? "tomorrow " : "") + clock(min % 1440);
    }

    // Minutes after midnight to "9:30 am"; noon is "12:00 pm".
    static String clock(int min) {
        min = ((min % 1440) + 1440) % 1440;
        int h = min / 60, mm = min % 60;
        return (h % 12 == 0 ? 12 : h % 12) + ":" + (mm < 10 ? "0" : "") + mm + (h < 12 ? " am" : " pm");
    }

    // "9:30–10:30 am", or "11:30 am–12:30 pm" when it crosses noon.
    static String range(int from, int to) {
        String a = clock(from), b = clock(to);
        return a.substring(a.length() - 2).equals(b.substring(b.length() - 2))
                ? a.substring(0, a.length() - 3) + "–" + b : a + "–" + b;
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
