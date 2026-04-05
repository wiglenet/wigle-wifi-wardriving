# ShadowCheck Collector (wigle-wifi-wardriving fork) — Gemini CLI Config

---

## Project Overview

This is a hardened fork of the WiGLE Wireless Wardriving Android application,
modified to serve as a field data collection device for the ShadowCheck-Web
SIGINT forensics platform.

**Role in Ecosystem:**
- Collects WiFi, Bluetooth, BLE, and cellular observations in the field
- Stores data in a local SQLite database (wiglewifi.sqlite)
- Uploads the database to ShadowCheck-Web via S3 presigned URL pipeline
- Runs TrackerEngine for real-time follower detection

**Primary Technologies:**
- Platform: Android (Min SDK 24, Target/Compile SDK 36)
- Language: Java 8 (with desugaring enabled)
- Build System: Gradle 8.13.0
- Maps: Google Maps SDK + MapLibre GL (FOSS builds)
- Database: SQLite — DatabaseHelper.java manages all schema migrations
- Upload: S3 presigned URL pattern (NOT direct multipart POST)

---

## Directory Structure

- `wiglewifiwardriving/src/main/java/net/wigle/wigleandroid/`
  - `MainActivity.java` — Primary entry point, initializes TrackerEngine
  - `WigleService.java` — High-priority foreground service with WakeLock/WifiLock
  - `db/DatabaseHelper.java` — SQLite schema and all migrations — source of truth
  - `util/UrlConfig.java` — Endpoint and API key constants
  - `listener/` — WiFi, Bluetooth, Cell receivers
  - `ui/` — Fragment and UI components
  - `CollectorFragment.java` — SC Collector Mode minimalist UI
  - `ShadowCheckUploader.java` — S3 upload implementation
  - `TrackerEngine.java` — Follower detection heuristics
- `wiglewifiwardriving/src/main/res/` — Android resources
- `wiglewifiwardriving/src/main/AndroidManifest.xml` — App manifest
- `gradle/` — Gradle wrapper
- `build.gradle` — Root build config
- `wiglewifiwardriving/build.gradle` — App module build config
- `gradle.properties` — Project-wide Gradle properties
- `SHADOWCHECK.md` — Integration technical reference
- `FEATURES_MOD.md` — Custom feature summary
- `TERRAFORM_S3.md` — S3 infrastructure and upload pattern reference

---

## Schema — Source of Truth

**DatabaseHelper.java is the ONLY authoritative source for the DB version.**
Do not trust GEMINI.md, SHADOWCHECK.md, or FEATURES_MOD.md for version numbers.
Always read DatabaseHelper.java first when working on schema.

Current known columns in `route` table (verify against DatabaseHelper.java):
- case_id TEXT
- device_model TEXT
- device_brand TEXT
- os_release TEXT
- device_id TEXT
- barometer DOUBLE

---

## Build Commands
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumentation tests (device required)
./gradlew lint                   # Lint check
./gradlew clean                  # Clean build outputs
```

---

## Upload Architecture — Critical

The correct upload pattern is PRESIGNED URL, not direct multipart POST.

Flow:
1. Android app POSTs to `/api/v1/ingest/request-upload` on shadowcheck-web
   - Body: { api_key, fileName, case_id }
   - Returns: { uploadUrl, s3Key }
2. Android app PUTs the binary sqlite file directly to uploadUrl
3. No AWS credentials ever touch the APK

The api_key must be sent in the Authorization header, NOT in the POST body.
Reference: TERRAFORM_S3.md for the complete server-side implementation.

Any prompt touching ShadowCheckUploader.java must use this pattern.
Never implement direct S3 credentials in the APK.
Never send api_key as a form field — header only.

---

## Hard Rules — No Exceptions

### Android Manifest
- NEVER modify AndroidManifest.xml without explicit instruction
- NEVER add permissions without explicit instruction and justification
- NEVER change minSdkVersion or targetSdkVersion without explicit instruction

### Build Config
- NEVER modify build.gradle dependency versions without checking for
  compatibility with Min SDK 24 and Java 8 desugaring first
- NEVER add a dependency without checking if it already exists in
  wiglewifiwardriving/build.gradle
- NEVER modify gradle.properties without explicit instruction

### Schema
- NEVER modify DatabaseHelper.java schema without explicit instruction
- Any schema change MUST increment DATABASE_VERSION
- Any schema change MUST have a corresponding onUpgrade() migration case
- NEVER drop or rename an existing column — add new columns only

### Security
- NEVER put AWS credentials, API keys, or secrets in Java source files
- NEVER put secrets in gradle.properties (they end up in build outputs)
- API keys go in local.properties (gitignored) surfaced via BuildConfig
- NEVER send api_key as a POST body parameter — Authorization header only

### Git
- NEVER run git push without explicit approval
- NEVER run git commit without showing exact diff and message first
- NEVER use --force on any git operation

### Testing
- Run ./gradlew lint after every Java change
- Run ./gradlew test after any logic change
- A lint error is a hard stop — report it, do not suppress it with annotations
  without asking first

---

## Approval Gates

Stop, show the plan, wait for explicit "yes" before:

1. Any git commit
2. Any git push
3. Any AndroidManifest.xml change
4. Any DATABASE_VERSION increment
5. Any new dependency added to build.gradle
6. Any change to UrlConfig.java (endpoints or keys)
7. Any new Android permission

---

## Context Loading Order

When starting any task, read these before doing anything else:

1. `wiglewifiwardriving/build.gradle` — current deps and SDK versions
2. `wiglewifiwardriving/src/main/java/net/wigle/wigleandroid/db/DatabaseHelper.java`
   — actual schema version and migration history
3. `wiglewifiwardriving/src/main/AndroidManifest.xml` — current permissions
4. Any file explicitly referenced in the prompt

---

## Verification Pattern

For every change:

1. Make the change
2. Run: ./gradlew lint
3. Run: ./gradlew test
4. Report PASS or exact failure output
5. Stop for approval before committing

---

## Known Doc Inconsistencies (Do Not Trust These Numbers)

- GEMINI.md says DB Version 7 — verified
- SHADOWCHECK.md says DB Version 7 — verified
- FEATURES_MOD.md says DB Version 7 — verified

Always read DatabaseHelper.java. Never use doc version numbers in code.

---

## Scope Discipline

You are NOT:
- Refactoring anything not mentioned in the current prompt
- Improving adjacent code you notice while working
- Adding logging beyond what the prompt asks
- Changing code style or formatting outside affected lines
- Making decisions about stash, untracked files, or open branches
  without asking first
- Adding features from SHADOWCHECK_COLLECTOR_PROPOSAL.md unless
  explicitly instructed
