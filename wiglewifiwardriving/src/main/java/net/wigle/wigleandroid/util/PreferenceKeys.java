package net.wigle.wigleandroid.util;

public class PreferenceKeys {
    // preferences
    public static final String SHARED_PREFS = "WiglePrefs";
    public static final String PREF_USERNAME = "username";
    public static final String PREF_PASSWORD = "password";
    public static final String PREF_AUTHNAME = "authname";
    public static final String PREF_TOKEN = "token";
    public static final String PREF_TOKEN_IV = "tokenIV";
    public static final String PREF_TOKEN_TAG_LENGTH = "tokenTagLength";
    public static final String PREF_SHOW_CURRENT = "showCurrent";
    public static final String PREF_BE_ANONYMOUS = "beAnonymous";
    public static final String PREF_DONATE = "donate";
    public static final String PREF_DB_MARKER = "dbMarker";
    public static final String PREF_MAX_DB = "maxDbMarker";
    public static final String PREF_ROUTE_DB_RUN = "routeDbRun";
    public static final String PREF_NETS_UPLOADED = "netsUploaded";
    public static final String PREF_SCAN_PERIOD_STILL = "scanPeriodStill";
    public static final String PREF_SCAN_PERIOD = "scanPeriod";
    public static final String PREF_SCAN_PERIOD_FAST = "scanPeriodFast";
    public static final String PREF_OG_BT_SCAN_PERIOD_STILL = "btGeneralScanPeriodStill";
    public static final String PREF_OG_BT_SCAN_PERIOD = "btGeneralScanPeriod";
    public static final String PREF_OG_BT_SCAN_PERIOD_FAST = "btGeneralScanPeriodFast";
    public static final String PREF_QUICK_PAUSE = "quickScanPause";
    public static final String GPS_SCAN_PERIOD = "gpsPeriod";
    public static final String PREF_FOUND_SOUND = "foundSound";
    public static final String PREF_FOUND_NEW_SOUND = "foundNewSound";
    public static final String PREF_LANGUAGE = "speechLanguage";
    public static final String PREF_RESET_WIFI_PERIOD = "resetWifiPeriod";
    public static final String PREF_BATTERY_KILL_PERCENT = "batteryKillPercent";
    public static final String PREF_MUTED = "muted";
    public static final String PREF_BT_WAS_OFF = "btWasOff";
    public static final String PREF_SCAN_BT = "scanBluetooth";
    public static final String PREF_DISTANCE_RUN = "distRun";
    public static final String PREF_STARTTIME_RUN = "timestampRunStart";
    public static final String PREF_CUMULATIVE_SCANTIME_RUN = "millisScannedDuringRun";
    public static final String PREF_STARTTIME_CURRENT_SCAN = "timestampScanStart";
    public static final String PREF_DISTANCE_TOTAL = "distTotal";
    public static final String PREF_DISTANCE_PREV_RUN = "distPrevRun";
    public static final String PREF_PREV_LAT = "prevLat";
    public static final String PREF_PREV_LON = "prevLon";
    public static final String PREF_PREV_ZOOM = "prevZoom2";
    public static final String PREF_LIST_SORT = "listSort";
    public static final String PREF_SCAN_RUNNING = "scanRunning";
    public static final String PREF_METRIC = "metric";
    public static final String PREF_USE_NETWORK_LOC = "useNetworkLoc";
    public static final String PREF_DISABLE_TOAST = "disableToast"; // bool
    public static final String PREF_BLOWED_UP = "blowedUp";
    public static final String PREF_CONFIRM_UPLOAD_USER = "confirmUploadUser";
    public static final String PREF_EXCLUDE_DISPLAY_ADDRS = "displayExcludeAddresses";
    public static final String PREF_EXCLUDE_LOG_ADDRS = "logExcludeAddresses";
    public static final String PREF_GPS_TIMEOUT = "gpsTimeout";
    public static final String PREF_GPS_KALMAN_FILTER = "gpsKalmanFilter";
    public static final String PREF_GPS_GNSS_FULL = "gpsGnssFull";
    public static final String PREF_NET_LOC_TIMEOUT = "networkLocationTimeout";
    public static final String PREF_START_AT_BOOT = "startAtBoot";
    public static final String PREF_LOG_ROUTES = "logRoutes";
    public static final String PREF_DAYNIGHT_MODE = "dayNightMode";
    public static final String PREF_ALERT_ADDRS = "alertOnAddresses";

    public static final String PREF_ALERT_BLE_MFGR_IDS = "alertOnBleMfgrId";

    // map prefs
    public static final String PREF_MAP_NO_TILE = "NONE";
    public static final String PREF_MAP_ONLYMINE_TILE = "MINE";
    public static final String PREF_MAP_NOTMINE_TILE = "NOTMINE";
    public static final String PREF_MAP_ALL_TILE = "ALL";
    public static final String PREF_MAP_TYPE = "mapType";
    public static final String PREF_MAP_ONLY_NEWDB = "mapOnlyNewDB";
    public static final String PREF_MAP_LABEL = "mapLabel";
    public static final String PREF_MAP_CLUSTER = "mapCluster";
    public static final String PREF_MAP_TRAFFIC = "mapTraffic";
    public static final String PREF_CIRCLE_SIZE_MAP = "circleSizeMap";
    public static final String PREF_MAP_HIDE_NETS = "hideNetsMap";
    public static final String PREF_VISUALIZE_ROUTE = "visualizeRoute";
    public static final String PREF_SHOW_DISCOVERED_SINCE = "showDiscoveredSince";
    public static final String PREF_SHOW_DISCOVERED = "showMyDiscovered";
    public static final String PREF_MAPS_FOLLOW_DAYNIGHT = "mapThemeMatchDayNight";
    public static final String PREF_MAP_FOLLOW_BEARING = "mapFollowBearing";

    // what to speak on announcements
    public static final String PREF_SPEECH_PERIOD = "speechPeriod";
    public static final String PREF_SPEECH_GPS = "speechGPS";
    public static final String PREF_SPEAK_RUN = "speakRun";
    public static final String PREF_SPEAK_NEW_WIFI = "speakNew";
    public static final String PREF_SPEAK_NEW_CELL = "speakNewCell";
    public static final String PREF_SPEAK_NEW_BT = "speakNewBt";
    public static final String PREF_SPEAK_QUEUE = "speakQueue";
    public static final String PREF_SPEAK_MILES = "speakMiles";
    public static final String PREF_SPEAK_TIME = "speakTime";
    public static final String PREF_SPEAK_BATTERY = "speakBattery";
    public static final String PREF_SPEAK_SSID = "speakSsid";
    public static final String PREF_SPEAK_WIFI_RESTART = "speakWifiRestart";

    // map ssid filter
    public static final String PREF_MAPF_REGEX = "mapfRegex";
    public static final String PREF_MAPF_INVERT = "mapfInvert";
    public static final String PREF_MAPF_OPEN = "mapfOpen";
    public static final String PREF_MAPF_WEP = "mapfWep";
    public static final String PREF_MAPF_WPA = "mapfWpa";
    public static final String PREF_MAPF_CELL = "mapfCell";
    public static final String PREF_MAPF_BT = "mapfBt";
    public static final String PREF_MAPF_BTLE = "mapfBtle";
    public static final String PREF_MAPF_ENABLED = "mapfEnabled";
    public static final String FILTER_PREF_PREFIX = "LA";
    public static final String PREF_GUESS_BLE_ADDRESS_TYPE = "guessBleAddressType";
    //[remove this key and all checks based on it (default to true) for FOSS build]
    public static final String PREF_USE_FOSS_MAPS = "useFossMaps";
    public static final String PREF_FOSS_MAPS_VECTOR_TILE_STYLE = "fossMapsBaseStyleUrl";
    public static final String PREF_FOSS_MAPS_VECTOR_TILE_KEY = "fossMapsBaseTileKey";
}
