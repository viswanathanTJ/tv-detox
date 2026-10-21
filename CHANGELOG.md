# Changelog

Releases of this fork (`viswanathanTJ/tv-detox`). Versions are the GitHub release tags; the home
screen app's own version is in brackets.

## v1.3.0 (home screen 1.6.0), 2026-10-21

- **Raining now:** today's card says "Raining now" when Google's current conditions measure rain,
  even if the hourly forecast missed it.
- **7–9 pm and today's rain chance:** these use the higher of the hourly forecast and the night
  forecast. Google's hourly numbers can stay low on a night its own night forecast calls rainy. A day
  card turns blue when 7–9 pm is likely to be rainy. The full-day page (summary, the line under the
  date, the hour-by-hour heading and the day chips) uses the same rule.
- **Refresh button** (the round arrow at the top right) downloads the weather at once. Left and
  right now stay in that row of icons.
- **Weather updates at 8 am, 2 pm and 6 pm** instead of every 6 hours. Each update is still 6 calls:
  1 for the days, 4 pages of hours, and 1 for current conditions. With the cap of 30 calls a day,
  that leaves room for two presses of Refresh.

## v1.2.0 (home screen 1.5.0), 2026-10-21

### Tamil calendar
- Press OK on the festival or Tamil-day tile to open it.
- **Month view:** every day shows its Tamil date and its festival or observance with a picture:
  Pournami, Amavasai, Ekadasi, Pradosham, Shashti, Chaturthi, Sankatahara Chathurthi, Masa
  Sivarathri, Ashtami, Navami, Karthigai, Thiruvonam, Thiruvathirai, Chandra darisanam, Kari naal
  and Vasthu naal.
- **Getting around:** arrows move a day or a week. Going past the first or last day opens the last
  or next month, so the remote alone pages through the months.
- **Day view:**
  - Tamil date, year, ayanam, ruthu and paksham.
  - Tithi, nakshatram, yogam, karanam and the moon's rasi, each with its end time and the next one.
  - Nalla neram, Gowri nalla neram, Raagu kalam, Yamagandam and Kuligai.
  - Soolam with its parigaram, and Chandrashtamam.
  - This month's Kari naal and Vasthu days, and sunrise and sunset.
- **Vakya, as printed calendars:** 2025–2030 come from `assets/panchang-vakya.csv`, which
  `tools/vakya_panchang.py` writes with the KKC Astro Vakya engine.
  - Checked against a printed October 2026 calendar: Tamil dates, kari naal and vasthu match
    exactly, every observance falls on the printed date, and times are within 6 minutes.
  - Other years are computed in the app.
- **Same data on the home screen:** the Tamil date and the "next Tamil day" tile use it too, so they
  agree with the calendar.
- 34 pictures drawn for the app (`assets/calendar-icons`, sources in `tools/calendar-icons`).

## v1.1.0 (home screen 1.4.0), 2026-10-21

- **Google Weather API instead of Open-Meteo,** for Munichalai, Madurai.
  - The API key is read from `weather.key` on the TV's storage, so release APKs ship without it.
  - The weather refreshes every 6 hours, with a hard cap of 30 calls a day (under 1,000 a month).
- **Rain at 7–9 pm shown first** on the day cards, or "No rain 7–9 PM" and the day's other rain time.
- **HDMI 3 button is now "TV":** a green gradient with a bold white label and icon, and more space
  before the apps.
- **Pinned apps survive a reinstall:** they're copied to the app's shared folder, and `install.sh`
  and `setup/setup-new-tv.sh` carry them across an uninstall.
- **`install.sh`** grants the AdGuard tile's permission and copies `weather.key` on every install.
- **`build.sh`:** `BUILD_TOOLS` and `PLATFORM` overrides, and it no longer stops when `weather.key`
  is missing.

## v1.0.0, 2026-10-20

- Home screen (launcher) and TV Monitor, signed release builds.
- **Home screen:**
  - Weather for Madurai.
  - Tamil date with Amavasai, Pournami and month start.
  - Festivals, and a full-day hourly view.
  - AdGuard DNS tile, All apps button, and a Switch to HDMI 3 button.
- **TV Monitor:** memory per app, Stop and Free up memory, a CPU tab.
