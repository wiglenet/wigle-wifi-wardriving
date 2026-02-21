# WiGLE Wireless Wardriving

![ci badge](https://github.com/wiglenet/wigle-wifi-wardriving/actions/workflows/android.yml/badge.svg)

Nethugging client for Android, from [wigle.net](https://wigle.net). 

This client provides geolocated detection and logging for WiFi, Bluetooth, and cellular signals using Android devices.

As of January 2023, this application supports Android SDK versions 24 (Nougat) and up. For older versions, see the [2.67 release tag](https://github.com/wiglenet/wigle-wifi-wardriving/releases/tag/2.67) to build your own copy or side-load [the compiled artifact](https://github.com/wiglenet/wigle-wifi-wardriving/blob/2.67/dist/release/wiglewifiwardriving-release.apk).

## Features
- View and map local RF signals including WiFi, Blueooth, and Cell
- Accumulate a database of wireless signal observations
- Search and export observed data
- Integrate with a [WiGLE.net account](https://wigle.net) for competition, statistics, and online aggregation and visualization
- See your standings and accomplishments per the WiGLE.net server
- Perform WiFi site surveys on a per-BSSID basis
- Register for proximity notifications based on the basis MAC, OUI, or Bluetooth LE manufacturer IDs

## Data Export
The client offers numerous data export formats including:

- CSV (current run, full DB, previous runs for WiGLE.net uploaders)
- KML (current run and full DB, previous runs for WiGLE.net uploaders)
- SQLite database (full DB)
- GPX route data (current and previous routes)
- [Magic Eight Ball](https://github.com/wiglenet/m8b) networkless geolocation artifacts

## Issues and feature requests
Please use [github issue tracking](https://github.com/wiglenet/wigle-wifi-wardriving/issues) to report bugs and request features.

**Please note:** *this is primarly a data collection tool - as such we aim to support the widest range of devices possible, and so advanced visualization and data management feature requests that would limit low-end device support will probably not be prioritized.*

## Device Support
Currently Android versions from Nougat (Android 7 / API 24) to Android 16 (API 36) are supported. Android 9 fundamentally throttled WiFi scanning support without a reliable remediation (see [our forums](https://wigle.net/phpbb/viewtopic.php?f=13&t=2841) for some possible fixes), but as of Android 10 and above, disabling WiFi scan throttling was added to the Developer Options settings menu. If you're using a modern Android OS, you should use the Android Developer options to [disable WiFi Scan Throttling](https://www.netspotapp.com/help/how-to-disable-wi-fi-throttling-on-android-10/) to maximize the effectiveness of this application.

We receive various bug reports from forks/ports of Android to non-standard devices, but cannot address or test all possible variations. While we do our best to support the widest range of devices possible, the best way to get support for your device is to help us debug or to submit a pull request!

## Contributing
You can submit fixes and changes for inclusion by forking this repository, working in a branch, and issuing a pull request. Langauge help and translations are VERY welcome.

We don't have a lot of contribution guidelines, but please:

- Make sure to test your changes
- Make sure that exporting data is the result of a direct, intentional user action, or via the Android Broadcast Intent system - don't send data off-device without user permission!
- Please be mindful of the need for multi-language support if adding text to the UI. Google translate is enough to get people started, but please add an attempt!

## Where to get it
Available on [Google Play](https://play.google.com/store/apps/details?id=net.wigle.wigleandroid&hl=en)
and [Amazon App Store](http://www.amazon.com/WiGLE-net-Wigle-Wifi-Wardriving/dp/B004L5XBXS).

F-Droid (externally maintained build of the [foss-master branch](https://github.com/wiglenet/wigle-wifi-wardriving/tree/foss-master): [https://f-droid.org/en/packages/net.wigle.wigleandroid/](https://f-droid.org/en/packages/net.wigle.wigleandroid/)

## How to use it
There aren't presently any official how-to guides, but a youtube search will provide tutorials. For detail on settings in the application, see [https://wigle.net/wiwi_settings](https://wigle.net/wiwi_settings).

## Attributions
Icon source from SVG:
 - dashboard: https://www.figma.com/@syalankush
 - eye, filter, map, sort: https://dazzleui.gumroad.com/l/dazzleiconsfree
 - no wifi: https://www.figma.com/@d12da0b9_b193_4
 - share: https://noahjacob.us/?ref=svgrepo.com
 - user star: https://github.com/Remix-Design/remixicon
 - wrench: https://github.com/FortAwesome/Font-Awesome

Sounds for proximity detection by Balcoran:
 - Beep: https://freesound.org/people/Balcoran/sounds/478186/
 - Blip: https://freesound.org/people/Balcoran/sounds/478187/

This project is maintained by the WiGLE.net team