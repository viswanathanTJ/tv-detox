"""Writes assets/panchang-vakya.csv: the Tamil daily panchangam by the Vakya method, the way printed
Tamil calendars give it, for the TV calendar (Panchang.java reads it; dates outside it fall back to
the app's own astronomical calculation).

Needs the kkc-astro library (the Vakya engine). Run from this repo:
    uv run --project ~/Documents/KkcAstro/app python tools/vakya_panchang.py 2025 2030

Times are Indian Standard Time, as minutes after that date's midnight (over 1440 = the next day).
The Vakya day is measured from sunrise at Thanjavur, as the engine (and printed calendars) do.
Columns:
  date        YYYY-MM-DD
  tm, td      Tamil month (0 = Chithirai) and day
  yr          60-year cycle position (0 = Prabhava)
  tithi..rasi each element in force at sunrise (0-based, the order of Panchang.java's name arrays),
              *_end when it ends, *_next the one after it. rasi_end is empty when the moon stays in
              its sign until the next sunrise.
  obs         observance keys (Panchang.java's), joined by ';'
"""
from __future__ import annotations

import csv
import datetime as dt
import functools
import sys
from pathlib import Path

from kkc_astro import ephemeris
from kkc_astro.vakya.engine import run_vakya_engine

IST = 5.5
THANJAVUR = (79 + 10 / 60, 10 + 47 / 60)   # the engine's reference place
MADURAI = (78.1344, 9.9181)                 # the TV's place, for sunset
RISE_FLAGS = ephemeris.SE_CALC_RISE | ephemeris.SE_BIT_DISC_CENTER | ephemeris.SE_BIT_NO_REFRACTION
NAZHIGAI_PER_HOUR = 2.5


def jd_ist(day: dt.date, minute: float) -> float:
    """Julian day (UT) of a minute after the day's IST midnight."""
    return ephemeris.julday(day.year, day.month, day.day, minute / 60 - IST)


@functools.lru_cache(maxsize=None)
def day_sunrise(day: dt.date) -> float:
    """Thanjavur sunrise on that IST date, JD UT (the first one after its midnight)."""
    _, rise = ephemeris.rise_trans(jd_ist(day, 0), ephemeris.SE_SUN, ephemeris.SEFLG_MOSEPH,
                                   RISE_FLAGS, THANJAVUR[0], THANJAVUR[1], 0.0, 0.0, 25.0)
    return rise


def next_sunrise(jd: float) -> float:
    """First Thanjavur sunrise after the instant, JD UT. Sunrises are cached per date, since the
    search runs millions of times."""
    y, m, d, _ = ephemeris.revjul(jd + IST / 24)
    day = dt.date(y, m, d)
    rise = day_sunrise(day)
    return rise if jd < rise else day_sunrise(day + dt.timedelta(days=1))


def state_at(day: dt.date, minute: float):
    """Engine state at an IST instant, mapped to (civil date, nazhigai since reference sunrise) the
    way kkc_astro.vakya.reference_time does for a birth time."""
    jd = jd_ist(day, minute)
    rise = next_sunrise(jd)
    hours = ephemeris.revjul(jd + IST / 24)[3]
    sunrise_hours = ephemeris.revjul(rise + IST / 24 - 1)[3]
    if hours < sunrise_hours:
        y, m, d, _ = ephemeris.revjul(jd + IST / 24 - 1)
        n = (hours - sunrise_hours + 24) * NAZHIGAI_PER_HOUR
    else:
        y, m, d, _ = ephemeris.revjul(jd + IST / 24)
        n = (hours - sunrise_hours) * NAZHIGAI_PER_HOUR
    return run_vakya_engine(y, m, d, n)


def elements(state) -> dict[str, int]:
    sun, moon = state.longitudes[0] % 360, state.longitudes[1] % 360
    elong = (moon - sun) % 360
    k = int(((elong - 6) % 360) // 6) + 1          # 1..60, as kkc_astro.panchanga.elements.karana
    if 7 < k < 57:
        k = k - int((k - 1) / 7) * 7
    elif k > 56:
        k -= 49
    return {
        "tithi": int(elong // 12) % 30,
        "star": int(moon // (360 / 27)) % 27,
        "yoga": int(((sun + moon) % 360) // (360 / 27)) % 27,
        "karana": k - 1,
        "rasi": int(moon // 30) % 12,
    }


def at(day: dt.date, minute: float, key: str) -> int:
    return elements(state_at(day, minute))[key]


def sunrise_minute(day: dt.date) -> float:
    """Thanjavur sunrise on the day, minutes after IST midnight."""
    return (day_sunrise(day) - jd_ist(day, 0)) * 1440


@functools.lru_cache(maxsize=None)
def sunset_minute(day: dt.date) -> float:
    _, s = ephemeris.rise_trans(jd_ist(day, 0), ephemeris.SE_SUN, ephemeris.SEFLG_MOSEPH,
                                ephemeris.SE_CALC_SET, MADURAI[0], MADURAI[1], 0.0, 0.0, 25.0)
    return (s - jd_ist(day, 0)) * 1440


def end_of(day: dt.date, start: float, key: str) -> float:
    """Minute the element in force at `start` changes: 30-minute steps, then halving to a minute."""
    value = at(day, start, key)
    lo = start
    hi = start + 30
    while at(day, hi, key) == value:
        lo, hi = hi, hi + 30
        if hi - start > 3 * 1440:
            raise RuntimeError(f"{key} doesn't change on {day}")
    while hi - lo > 0.5:
        mid = (lo + hi) / 2
        if at(day, mid, key) == value:
            lo = mid
        else:
            hi = mid
    return hi


def observances(day: dt.date, rise: float, el: dict[str, int]) -> list[str]:
    """Same rules as Panchang.java, on Vakya values: the tithi (or star) at the time each is kept."""
    prev = day - dt.timedelta(days=1)
    t_rise = el["tithi"]
    t_set = at(day, sunset_minute(day), "tithi")
    t_set_before = at(prev, sunset_minute(prev), "tithi")
    t_noon, t_noon_before = at(day, 720, "tithi"), at(prev, 720, "tithi")
    t_nine = at(day, 21 * 60, "tithi")
    out = []
    # Pournami and Amavasai: the evening they hold (or, for one too short to reach a sunset, the
    # evening right after it).
    if t_set == 14 or (t_set == 15 and t_set_before == 13):
        out.append("pournami")
    if t_set == 29 or (t_set == 0 and t_set_before == 27):
        out.append("amavasai")
    if t_rise in (10, 25):
        out.append("ekadasi")
    if t_set in (12, 27):
        out.append("pradosham")
    if t_rise == 5:
        out.append("shashti")
    if t_rise == 20:
        out.append("thei_shashti")
    if t_rise == 3:
        out.append("chaturthi")
    if t_nine == 18:
        out.append("sankatahara_chaturthi")
    if t_set == 28:
        out.append("masa_sivarathri")
    if t_noon in (7, 22) and t_noon_before != t_noon:
        out.append("ashtami")
    if t_noon in (8, 23) and t_noon_before != t_noon:
        out.append("navami")
    # Karthigai and Thiruvonam are kept in the evening: the star at 9 pm.
    star_nine = at(day, 21 * 60, "star")
    if star_nine == 2:
        out.append("karthigai")
    if star_nine == 21:
        out.append("thiruvonam")
    if el["star"] == 5:
        out.append("thiruvathirai")
    if t_set in (1, 2) and t_set_before in (29, 0):
        out.append("chandra_darisanam")
    return out


def row(day: dt.date) -> dict:
    rise = sunrise_minute(day)
    next_rise = sunrise_minute(day + dt.timedelta(days=1)) + 1440
    el = elements(state_at(day, rise + 0.5))
    # The Tamil date from noon: just after sunrise the engine's mapping (made for birth times) can
    # compare with the next day's later sunrise and give the previous day's date (late November).
    noon = state_at(day, 720)
    r = {"date": day.isoformat(), "tm": noon.solar_month - 1, "td": noon.solar_day,
         "yr": (noon.samvatsara_index - 1) % 60}
    for key in ("tithi", "star", "yoga", "karana", "rasi"):
        end = end_of(day, rise + 0.5, key)
        r[key] = el[key]
        r[key + "_end"] = round(end) if key != "rasi" or end < next_rise else ""
        r[key + "_next"] = at(day, end + 0.5, key)
    r["obs"] = ";".join(observances(day, rise, el))
    return r


def main() -> None:
    first, last = int(sys.argv[1]), int(sys.argv[2])
    out = Path(__file__).resolve().parent.parent / "assets" / "panchang-vakya.csv"
    fields = ["date", "tm", "td", "yr"] + [f"{k}{s}" for k in ("tithi", "star", "yoga", "karana", "rasi")
                                           for s in ("", "_end", "_next")] + ["obs"]
    day, end = dt.date(first, 1, 1), dt.date(last, 12, 31)
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields, lineterminator="\n")
        w.writeheader()
        while day <= end:
            w.writerow(row(day))
            day += dt.timedelta(days=1)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
