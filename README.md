# WiGLE Wireless Wardriving

Nethugging client for Android, from [wigle.net](https://wigle.net).

This client provides geolocated detection and logging for WiFi, Bluetooth, and cellular signals using Android devices.

## Features
- View and map local RF signals
- Accumulate a database of wireless signal observations
- Search and export observed data
- Integrate with a [WiGLE.net account](https://wigle.net) for competition, statistics, and online aggregation and visualization

## Data Export
The client offers numerous data export formats including:

- CSV (curent run, full DB, previous runs for WiGLE.net uploaders)
- KML (current run and full DB, previous runs for WiGLE.net uploaders)
- SQLite database (full DB)
- GPX route data (current and previous routes)
- [Magic Eight Ball](https://github.com/wiglenet/m8b) networkless geolocation artifacts

## Issues and feature requests
Please use [github issue tracking](https://github.com/wiglenet/wigle-wifi-wardriving/issues) to report bugs and request features.

**Please note:** *this is primarly a data collection tool - as such we aim to support the widest range of devices possible, and so advanced visualization and data management feature requests that would limit low-end device support will probably not be prioritized.*

## Device Support
Currently Android versions from KitKat (Android 4.4.3 / API 19) to Android 12 (API 31) are supported. Android 9 fundamentally throttled WiFi scanning support without a reliable remediation (see [our forums](https://wigle.net/phpbb/viewtopic.php?f=13&t=2841) for some possible fixes), but as of Android 10 and above, disabling WiFi scan throttling was added to the Developer Options settings menu. If you're using a modern Android OS, you should [disable WiFi Scan Throttling](https://www.netspotapp.com/help/how-to-disable-wi-fi-throttling-on-android-10/) to maximize the effectiveness of this application.

We receive various bug reports from forks/ports of Android to non-standard devices, but cannot address or test all possible variations. While we do our best to support the widest range of devices possible, the best way to get support for your device is to help us debug or to submit a pull request!

## Contributing
You can submit fixes and changes for inclusion by forking this repository, working in a branch, and issuing a pull request.

## Where to get it
Available on [Google Play](https://play.google.com/store/apps/details?id=net.wigle.wigleandroid&hl=en)
and [Amazon App Store](http://www.amazon.com/WiGLE-net-Wigle-Wifi-Wardriving/dp/B004L5XBXS).

F-Droid (externally maintained build of the [foss-master branch](https://github.com/wiglenet/wigle-wifi-wardriving/tree/foss-master): [https://f-droid.org/en/packages/net.wigle.wigleandroid/](https://f-droid.org/en/packages/net.wigle.wigleandroid/)

## How to use it
There aren't presently any official how-to guides, but a youtube search will provide tutorials. For detail on settings in the application, see [https://wigle.net/wiwi_settings](https://wigle.net/wiwi_settings).


This project is maintained by the WiGLE.net team