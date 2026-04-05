# WiGLE Wardriving - Custom Enhancements Summary

This document summarizes the custom features and architectural changes added to this fork of the WiGLE Wireless Wardriving application.

## 1. ShadowCheck-Web Ingest Pipeline
- **Direct Upload**: Added a "Upload to ShadowCheck" button to the main list screen.
- **S3 Import**: Implemented `ShadowCheckUploader.java` to stream the SQLite database directly to a configurable S3 import gateway.
- **Case ID Support**: Records a user-defined `case_id` in the database and sends it as a POST parameter to the ingest pipeline for automated data organization.
- **Configuration**: Managed in `UrlConfig.java` via `SHADOWCHECK_POST_URL` and `SHADOWCHECK_API_KEY`.
- **Reference**: See `SHADOWCHECK.md` for full technical details.

## 2. SC Collector Mode (High Performance UI)
- **Visual Branding**: Implemented a modern "Slate & Amber" UI matching the `shadowcheck-web` dashboard.
- **Minimalist Fragment**: Added `CollectorFragment.java` which hides live network lists and maps to maximize CPU availability for high-speed SQLite writes and tracking.
- **Live Detection Feed**: Included a compact, real-time list of the last 10 detected networks directly on the collector dashboard.
- **Toggle**: Managed via "SC Collector Mode" in Settings.

## 3. Mapbox Integration
- **Custom Map Themes**: Added support for Mapbox Streets, Satellite, and Dark themes within the FOSS (MapLibre) mapping engine.
- **API Key Management**: Added a secure "Mapbox API Key" field in Settings, allowing users to provide their own access token for high-quality tile rendering.
- **Engine**: Leverages the existing MapLibre implementation to load Mapbox vector tiles dynamically.

## 4. Real-Time Tracker Detection (Anti-Tracking)
- **Heuristic Engine**: Added `TrackerEngine.java` to detect unknown radio beacons (WiFi, BT, BLE) physically following the user.
- **Three-Layer Logic**:
    - **Temporal**: Device must be seen for a sustained period (e.g., 15+ mins).
    - **Spatial**: Device must travel a minimum distance with the user (e.g., 500m).
    - **Signal Behavioral**: Distinguished between "innocent" persistent signals and "suspect" varying/sporadic signals.
- **Notifications**: High-priority Android notifications are triggered when a suspect follower is identified.
- **UI Controls**: Added a "Tracker Alerts & Personal Security" section in Settings to toggle the feature and tune time/distance thresholds.

## 5. Personal Security & Known Device Alerts
- **Friends/Family Integration**: Rebranded the existing "MAC Alert" system to explicitly support "Known Devices (Friends/Family)".
- **Visual Cues**: Known devices are highlighted with a red background in the network list when detected.
- **Ignore Workflow**: Rebranded logging/display exclusions to "Ignore (Hide/Block)" to clarify silencing alerts for personal devices.

## 6. Enhanced Persistence & Reliability
- **Service Locks**: Added `WakeLock` and `WifiLock` directly to the foreground `WigleService`.
- **Aggressive Persistence**: Prevents the CPU and WiFi radio from sleeping or entering low-power modes during active scanning.
- **UI Toggle**: Added "Aggressive Service Persistence" in Settings to allow users to control these extra power-consuming locks.
- **Priority**: Increased foreground notification importance to `HIGH` to protect the process from OS-level resource reclamation.

## 7. Modern Android Storage & Data Visibility
- **Visible Storage**: Changed the default database and export path to a visible external directory on Android 11+ (API 30+).
- **Path**: `/sdcard/Android/data/net.wigle.wigleandroid/files/wiglewifi/`
- **Auto-Migration**: Implemented logic in `DatabaseHelper` to automatically move existing databases from hidden internal storage to the new visible location on the first run.

## 8. Device & Mission Metadata
- **Enhanced Schema**: Upgraded the database to **Version 7**.
- **Mission Tracking**: The `route` table now includes a `case_id` column to associate data with specific investigations.
- **Sensor Logging**: Added automatic logging of **Barometric Pressure** (hPa) if the phone's hardware sensor is available.
- **Metadata Capture**: Automatically records `device_model`, `device_brand`, `os_release`, and `device_id`.
- **Purpose**: Enables professional-grade multi-mission analysis within the ShadowCheck ecosystem.
