package net.wigle.wigleandroid.util;

/**
 * Utility class that holds the WiGLE URLs used throughout the app
 */
public class UrlConfig {
    public static final String API_DOMAIN = "api.wigle.net";
    public static final String API_HOST = "https://" + API_DOMAIN; /*+ ":" + API_PORT*/;

    public static final String CSV_TRANSID_URL_STEM = API_HOST+"/api/v2/file/csv/";
    // form auth
    public static final String TOKEN_URL = API_HOST+"/api/v2/activate";

    // no auth
    public static final String SITE_STATS_URL = API_HOST+"/api/v2/stats/site";
    public static final String RANK_STATS_URL = API_HOST+"/api/v2/stats/standings";
    public static final String NEWS_URL = API_HOST+"/api/v2/news/latest";

    // optional auth
    public static final String FILE_POST_URL = API_HOST+"/api/v2/file/upload";

    // api token auth
    public static final String UPLOADS_STATS_URL = API_HOST+"/api/v2/file/transactions";
    public static final String USER_STATS_URL = API_HOST+"/api/v2/stats/user";
    public static final String OBSERVED_URL = API_HOST+"/api/v2/network/mine";
    public static final String KML_TRANSID_URL_STEM = API_HOST+"/api/v2/file/kml/";
    public static final String SEARCH_WIFI_URL = API_HOST+"/api/v2/network/search";
    public static final String SEARCH_CELL_URL = API_HOST+"/api/v2/cell/search";

    public static final String WIGLE_BASE_URL = "https://wigle.net";

    // registration web view
    public static final String REG_URL = "https://wigle.net/register";

}
