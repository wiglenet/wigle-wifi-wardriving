# Proposal: Transforming WiGLE into "ShadowCheck Collector Lite"

Since `wigle-wifi-wardriving` and `shadowcheck-web` are co-located on this machine, we can turn this modified WiGLE app into a dedicated, lightweight collector for the ShadowCheck ecosystem. This serves as a functional interim solution while `ShadowCheckMobile` is in development.

## 1. Dedicated "ShadowCheck" Build Flavor
We can use Gradle Product Flavors to create a standalone version of the app that is branded for ShadowCheck. This avoids confusing users with WiGLE-specific terminology.

- **Application ID**: `net.shadowcheck.collector`
- **App Name**: "SC Collector"
- **Custom Icon**: Use a stylized ShadowCheck logo.
- **Default Ingest**: Hardcode the `SHADOWCHECK_POST_URL` to your production/staging endpoint so users don't have to configure it manually.

## 2. "Lightweight" UI Mode (Zero-Distraction)
The current WiGLE UI is dense with statistics and maps that might not be needed for a simple "collect and upload" mission. We can add a "ShadowCheck Mode" toggle that:
- Hides the **Dashboard**, **Rank**, and **News** fragments.
- Makes the **Upload to ShadowCheck** button the primary UI element on the main list.
- Streamlines the **Settings** to focus only on Tracker Alerts and Upload credentials.

## 3. Scoped Collection (Project/Case IDs)
To better align with `shadowcheck-web`, we can add a "Case ID" field to the main screen. 
- This ID would be stored in the `route` table (adding a `case_id` column to the v5 schema).
- When the data is ingested by `shadowcheck-web`, it can automatically group observations by Case ID, making it a true professional tool for field operations.

## 4. Local Ingest Dev-Link
Since the repos are adjacent, we can add a Gradle task to this project that automates the local testing pipeline:
- `task deployToWeb`: Builds the debug APK and copies the latest `wiglewifi.sqlite` (from a connected device via ADB) directly into the `shadowcheck-web/test_ingest/` folder.
- This allows you to verify the entire pipeline (Collector -> DB -> Web Ingest) without needing a real S3 gateway during development.

## 5. Schema Alignment (Single Source of Truth)
We can implement a simple script that reads the SQLite schema from the Android code and generates a TypeScript interface or Python Pydantic model for `shadowcheck-web`. This ensures that as we add metadata (like the device info we just added), the web ingest pipeline never breaks due to a mismatch.

## Next Steps
If you'd like to proceed with any of these, I recommend starting with **Step 1 (Build Flavors)** and **Step 3 (Case IDs)** as they provide the most immediate "professional" value for the ShadowCheck pipeline.
