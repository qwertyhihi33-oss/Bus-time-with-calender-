# HK Bus Calendar 巴士日程 (Android App)

An Android (Kotlin + Jetpack Compose Material 3) application that integrates your **Android Calendar events** with real-time Hong Kong bus arrival data from **KMB (九龍巴士)**, **LWB (龍運巴士)**, and **Citybus (城巴)** based on the current time and your live GPS location.

## Official Data Sources (HKSAR DATA.GOV.HK)
- **KMB & LWB Real-Time Arrival Data**: [DATA.GOV.HK KMB/LWB Dataset](https://data.gov.hk/en-data/dataset/hk-td-tis_21-etakmb)  
  Base URL: `https://data.etabus.gov.hk/v1/transport/kmb/`
  - `GET /stop` — All KMB & LWB bus stops with WGS84 coordinates (cached in local Room DB)
  - `GET /route-stop/{route}/{direction}/{service_type}` — Ordered stops per route
  - `GET /stop-eta/{stop_id}` — Live ETAs for all routes at a nearby stop
  - `GET /eta/{stop_id}/{route}/{service_type}` — Live ETA for a specific route & stop
- **Citybus Real-Time "Next Bus" Arrival Data (v2)**: [DATA.GOV.HK Citybus Dataset](https://data.gov.hk/en-data/dataset/ctb-eta-transport-realtime-eta)  
  Base URL: `https://rt.data.gov.hk/v2/transport/citybus/`
  - `GET /route/CTB` — Citybus route list
  - `GET /route-stop/CTB/{route}/{direction}` — Ordered stops per Citybus route
  - `GET /stop/{stop_id}` — Stop coordinates & bilingual names (cached in local Room DB)
  - `GET /eta/CTB/{stop_id}/{route}` — Live Citybus ETA

## Key Features
1. **Calendar Integration (`CalendarContract.Instances`)**: Reads upcoming non-all-day calendar events (including recurring events) and calculates time remaining until each event.
2. **GPS-Aware Stop Selection (`FusedLocationProviderClient` + Haversine)**: Automatically locates the nearest KMB/LWB stops within your chosen radius and selects the closest boarding stop along any saved Citybus, KMB, or LWB route.
3. **Smart Departure Countdown**: Subtracts estimated walking time to the stop from the live bus ETA to show when you should leave (`Leave in X min`).
4. **Keyword-to-Route Rules**: Bind calendar keywords (e.g., `PolyU`, `尖沙咀`, `Airport`) to specific KMB, LWB, or Citybus routes — or leave blank (`*`) for favorite commute routes.
5. **Background Calendar Alerts (`WorkManager` + `NotificationCompat`)**: Automatically checks upcoming events every 15 minutes and pushes a rich notification with live ETAs and a one-tap **Refresh ETA** button when an event is within your lead-time window.

## How to Build & Run
1. Unzip `HKBusCalendarApp.zip` and open the `HKBusCalendarApp` folder in **Android Studio Ladybug (2024.2+)** or newer.
2. Let Gradle sync (JDK 17, Android SDK 35).
3. Connect an Android device (Android 8.0+ / API 26+) or start an emulator with Google Play Services (for `FusedLocationProviderClient`).
4. Click **Run** (`Shift+F10`), grant **Calendar**, **Location**, and **Notification** permissions, and add your commute routes under the **Routes** tab.
5. *(Optional)* For reliable background location checks while the screen is off, allow **"Allow all the time"** location access in Android App Settings.


## Publish to GitHub & Auto-Build Downloadable APK
This repository includes a pre-configured GitHub Actions workflow (`.github/workflows/android-build.yml`) that runs unit tests and compiles `app-debug.apk` automatically on every push:

```bash
cd HKBusCalendarApp
git init
git add .
git commit -m "Initial commit: HK Bus Calendar Android App"
git branch -M main
git remote add origin https://github.com/<YOUR_USERNAME>/HKBusCalendarApp.git
git push -u origin main
```

Once pushed, open the **Actions** tab in your GitHub repository, click the latest **Android CI & Build APK** run, and download **`HKBusCalendarApp-debug-apk`** directly to install on your Android phone.
