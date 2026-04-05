# Android Repo Audit — 2026-04-05

## DB Schema Version — Actual vs Docs
- **Actual DATABASE_VERSION:** 7
- **Findings:**
    - Schema version in code is **7**.
    - Migration history:
        - v4 -> v5: Adds `device_model`, `device_brand`, `os_release`, `device_id` to `route` table.
        - v5 -> v6: Adds `case_id` to `route` table.
        - v6 -> v7: Adds `barometer` to `route` table.
    - `ROUTE_CREATE` string in `DatabaseHelper.java` includes all these columns, making it consistent with Version 7.
- **Documentation Mismatch:**
    - `GEMINI.md`: Mentions v5 (**Mismatch**)
    - `SHADOWCHECK.md`: Mentions v6 (**Mismatch**)
    - `FEATURES_MOD.md`: Mentions v7 (**Match**)

## Upload Pattern — Compliance with Presigned URL Spec
- **Current Implementation:** Direct POST with `MultipartBody`.
- **Compliance Status:** **NON-COMPLIANT** with `TERRAFORM_S3.md` specification.
- **Issues Found:**
    - Uses a 1-step direct POST instead of the required 2-step process (POST for presigned URL, then PUT to S3).
    - Sends `api_key` as a form data part in the POST body instead of the required `Authorization` header.
    - `api_key` and `case_id` are bundled in the multipart form.
- **HTTP Library:** `OkHttp 4.11.0`.

## UrlConfig — Security Assessment
- **SHADOWCHECK_POST_URL:** Hardcoded to `https://shadowcheck.example.com/api/v1/import` (Placeholder).
- **SHADOWCHECK_API_KEY:** Hardcoded to `REPLACE_WITH_ACTUAL_KEY` (Placeholder).
- **Findings:**
    - Secrets are not driven by `BuildConfig` or `local.properties`.
    - Placeholders are present in source code, but no actual production secrets were found committed.
    - **Risk:** High risk of accidentally committing real keys if developers follow this pattern without moving to `BuildConfig`.

## Build Config Summary
- **SDK Versions:**
    - `compileSdk`: 36
    - `minSdkVersion`: 24
    - `targetSdkVersion`: 36
- **Flavors:** None (Default build types only).
- **Dependencies:**
    - `okhttp:4.11.0` is present.
    - No AWS SDK or S3-specific libraries are included (consistent with intent to use presigned URLs, but implementation is currently using direct POST).
- **Signing Config:** Not defined in `build.gradle`.

## Garbage Files
- `import-summary.txt`: Tracked file identified as a leftover artifact.

## Permissions
- **Declared Permissions:**
    - `android.permission.BLUETOOTH`
    - `android.permission.BLUETOOTH_ADMIN`
    - `android.permission.BLUETOOTH_CONNECT`
    - `android.permission.CHANGE_WIFI_STATE`
    - `android.permission.CHANGE_NETWORK_STATE`
    - `android.permission.ACCESS_COARSE_LOCATION`
    - `android.permission.ACCESS_FINE_LOCATION`
    - `android.permission.ACCESS_WIFI_STATE`
    - `android.permission.BLUETOOTH_SCAN`
    - `android.permission.INTERNET`
    - `android.permission.WAKE_LOCK`
    - `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
    - `android.permission.READ_PHONE_STATE`
    - `android.permission.ACCESS_NETWORK_STATE`
    - `net.wigle.wigleandroid.permission.MAPS_RECEIVE`
    - `com.google.android.providers.gsf.permission.READ_GSERVICES`
    - `android.permission.RECEIVE_BOOT_COMPLETED`
    - `android.permission.FOREGROUND_SERVICE`
    - `android.permission.FOREGROUND_SERVICE_LOCATION`
    - `android.permission.POST_NOTIFICATIONS`
    - `android.permission.SYSTEM_ALERT_WINDOW`
    - `android.permission.CAMERA`
- **Flagged Permissions:**
    - `SYSTEM_ALERT_WINDOW`: High-privilege permission for drawing over other apps; should verify if strictly necessary for the Collector UI.
    - `READ_PHONE_STATE`: Sensitive permission; used for cell data but also provides device identifiers.
