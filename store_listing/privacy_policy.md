# Privacy Policy for TiltLevel

**Effective Date:** October 26, 2026  
**Developer:** KimRosil App Studio  

## 1. 100% Offline Architecture
TiltLevel operates exclusively offline on your Android device. The application requests zero network permissions (`INTERNET` is not declared in the manifest) and transmits zero data to external servers.

## 2. Hardware Sensor Data
TiltLevel reads your device's hardware accelerometer sensor (`android.hardware.sensor.accelerometer`) solely in real time while the application is active in the foreground to calculate tilt angle, percent grade, and roof pitch metrics. Sensor readings are never transmitted off your device.

## 3. Local Storage
Tare calibration settings and saved surface slope logs are stored strictly inside your device's local application sandbox (`SharedPreferences` and local SQLite database `tilt_level_logs.db`). Uninstalling the application or clearing app data permanently removes all stored records.

## 4. Zero Third-Party SDKs or Trackers
TiltLevel contains zero advertising SDKs, zero analytics frameworks, and zero telemetry trackers.
