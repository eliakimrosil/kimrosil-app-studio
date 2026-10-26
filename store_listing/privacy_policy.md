# Privacy Policy for LuxScale

**Effective Date:** October 26, 2026  
**Developer:** KimRosil App Studio  

## 1. 100% Offline Architecture
LuxScale operates exclusively offline on your Android device. The application requests zero network permissions (`INTERNET` is not declared in the manifest) and transmits zero data to external servers.

## 2. Hardware Sensor Data
LuxScale reads your device's ambient light sensor (`android.hardware.sensor.light`) solely in real time while the application is active in the foreground to calculate Lux, Foot-Candles, PPFD, DLI, and EV100 metrics. Sensor readings are never transmitted off your device.

## 3. Local Storage
Calibration settings and saved room light audits are stored strictly inside your device's local application sandbox (`SharedPreferences` and local SQLite database `lux_scale_audits.db`). Uninstalling the application or clearing app data permanently removes all stored records.

## 4. Zero Third-Party SDKs or Trackers
LuxScale contains zero advertising SDKs, zero analytics frameworks, and zero telemetry trackers.
