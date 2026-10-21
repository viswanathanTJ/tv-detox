# Home screen: weather and Tamil calendar

## What the home screen shows (top to bottom)

1. **Clock and date.** The time, the date in English ("Tuesday, 20 October 2026"), and below it the Tamil weekday and Tamil date ("செவ்வாய் · ஐப்பசி 3"). On an Amavasai or Pournami day the line also names it.
2. **Rain banner.** "Rain: Wed 8 am–12 pm · Thu 6–9 pm …" when rain is expected in the next 5 days. Otherwise it says "No rain expected" or "Weather not available right now".
3. **Five day cards** (today and the next 4 days): weekday, date with the Tamil weekday, a weather picture, max and min temperature, chance of rain and when. A rainy day has a blue outline. Press OK on a card to see that day hour by hour (24 hours, rainy hours outlined in blue). Press "Full day" there, or pick a day at the bottom, to open the full-day page: max and min, a summary, a 24-hour rain chart, morning, afternoon, evening and night, sunrise, sunset, humidity, wind, UV, festivals, and a 5-day picker. Back (or Escape) closes these pages.
4. **The green "TV" button** (HDMI 3). It has the focus when the screen opens. Next to it are two tiles: the **next festival or holiday** and the **next Tamil day** (Amavasai, Pournami or the first day of a Tamil month). Press OK on either tile to open the **Tamil calendar** (below).
5. **Shortcuts and apps.** Play Store, other HDMI inputs, Settings and Add app, then the favourite apps. These work as before: hold OK or press Menu on an app to move, replace or remove it.

## Tamil calendar

- **Month view.** Every day with its Tamil date, its festival or observance (Pournami, Amavasai, Ekadasi, Pradosham, Shashti, Chaturthi, Sankatahara Chathurthi, Masa Sivarathri, Ashtami, Navami, Karthigai, Thiruvonam, Thiruvathirai, Chandra darisanam, Kari naal, Vasthu naal) and its picture. Arrows move a day (left, right) or a week (up, down); going past the first or last day opens the last or next month. OK opens the day.
- **Day view.** Tamil date, year, ayanam, ruthu and paksham; tithi, nakshatram, yogam, karanam and the moon's rasi, each with its end time and the next one; Nalla neram, Gowri nalla neram, Raagu kalam, Yamagandam, Kuligai; Soolam and its parigaram; Chandrashtamam; this month's Kari naal and Vasthu days; sunrise and sunset. Arrows or the buttons go to the next or last day.
- **Where it comes from.** Printed Tamil calendars use the Vakya method, so 2025-2030 come from `assets/panchang-vakya.csv`, written by `tools/vakya_panchang.py` with the KKC Astro Vakya engine (times within a few minutes of a printed Daily Thanthi style calendar, October 2026 checked day by day). Other years are computed in the app by the astronomical method, which can differ by an hour or two. Raagu kalam, Yamagandam, Kuligai, Soolam, Kari naal and Vasthu are the fixed tables printed calendars use; Nalla neram and Gowri nalla neram vary between publishers, so the app shows the slots printed most often.
- **Pictures** are in `assets/calendar-icons`, drawn for this app (SVG sources in `tools/calendar-icons`).

## Where the numbers come from

- **Weather:** Google Weather API (the key is read from weather.key in /sdcard/Android/data/com.ansub.tvhome/files on the TV; install.sh copies it there) for Munichalai, Madurai, PIN 625009 (latitude 9.9181, longitude 78.1344, time zone Asia/Kolkata). The key allows 1,000 calls a month, so the app downloads it at most once every 6 hours (6 calls each time), retries a failed download after an hour, and never makes more than 30 calls a day. A "rainy hour" means a rain chance of 40% or more. Rain times are runs of rainy hours next to each other. For today, only the hours still to come count.
- **Humidity** is the average for the day. **Wind** is the strongest hourly wind of the day. **UV** is the day's maximum.
- **Festivals and holidays:** the file `assets/holidays.csv` (2026 and 2027), which is built into the app.
- **Tamil date, Amavasai and Pournami:** worked out in the app from the positions of the sun and the moon. It uses standard astronomy formulas and the Lahiri ayanamsa (about 24.2° in 2026). A Tamil month starts on the day the sun enters the next sign, if that happens before sunset in Madurai. If it happens after sunset, the month starts the next day. Amavasai and Pournami are the days on which the new or full moon falls, counted from one sunrise to the next.

## What is approximate

- **The Tamil day number and the Amavasai and Pournami days are calculated, not looked up.** They match printed calendars on almost every day. They can be one day off when the sun changes sign, or the moon reaches new or full, within about half an hour of sunset or sunrise. Printed calendars also differ from each other on those days.
- Forecasts are only forecasts. The rain chance is Google's estimate for each hour.

## When the TV is offline

The screen shows the last forecast it downloaded, as long as it still covers today. If it has none, the banner says "Weather not available right now" and the day cards are hidden. The clock, the Tamil date, the festival tiles, the HDMI button and the apps all work without the internet. If the holiday list cannot be read, its tile says "Holiday list not available".
