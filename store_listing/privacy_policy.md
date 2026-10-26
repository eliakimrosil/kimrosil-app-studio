# Privacy Policy for GaussMeter

**Effective Date:** October 26, 2026  
**Developer:** KimRosil App Studio  

## 1. 100% Offline Architecture
GaussMeter operates exclusively offline on your Android device. The application requests zero network permissions (`INTERNET` is not declared in the manifest) and transmits zero data to external servers.

## 2. Hardware Sensor Data
GaussMeter reads your device's hardware magnetic field sensor (`android.hardware.sensor.compass`) solely in real time while the application is active in the foreground to calculate magnetic flux density (µT and mG) and ferrous proximity metrics. Sensor readings are never transmitted off your device.

## 3. Local Storage
Baseline tare settings and saved magnetic scan logs are stored strictly inside your device's local application sandbox (`SharedPreferences` and local SQLite database `gauss_meter_logs.db`). Uninstalling the application or clearing app data permanently removes all stored records.

## 4. Zero Third-Party SDKs or Trackers
GaussMeter contains zero advertising SDKs, zero analytics frameworks, and zero telemetry trackers.
