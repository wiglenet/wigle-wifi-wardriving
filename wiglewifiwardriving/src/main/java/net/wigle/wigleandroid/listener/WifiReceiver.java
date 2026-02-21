package net.wigle.wigleandroid.listener;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.lang.String;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.ui.NetworkListUtil;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;
import net.wigle.wigleandroid.FilterMatcher;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.ui.UINumberFormat;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.ScanUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * Primary receiver logic for WiFi scan results.
 * @author bobzilla, arkasha
 */
public class WifiReceiver extends BroadcastReceiver {
    private MainActivity mainActivity;
    private final DatabaseHelper dbHelper;
    private SetNetworkListAdapter listAdapter;
    private final SimpleDateFormat timeFormat;
    private final NumberFormat numberFormat1;
    private final SsidSpeaker ssidSpeaker;

    private Handler wifiTimer;
    private long scanRequestTime = Long.MIN_VALUE;
    private long lastScanResponseTime = Long.MIN_VALUE;
    private long lastWifiUnjamTime = 0;
    private long lastSaveLocationTime = 0;
    private long lastHaveLocationTime = 0;
    private final long constructionTime = System.currentTimeMillis();
    private long previousTalkTime = System.currentTimeMillis();
    private final Set<String> runNetworks = new HashSet<>();
    private long prevNewNetCount;
    private long prevScanPeriod;
    private boolean scanInFlight = false;

    private Set<String> safeWatchSsids = Collections.synchronizedSet(new HashSet<>());

    private WiFiScanUpdater updateOnSeen = null;

    /** Executor for running WifiManager.startScan() off the main thread to avoid ANR from Binder blocking. */
    private final ExecutorService wifiScanExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public WifiReceiver( final MainActivity mainActivity, final DatabaseHelper dbHelper) {
        this.mainActivity = mainActivity;
        this.dbHelper = dbHelper;
        prevScanPeriod = mainActivity.getLocationSetPeriod();
        ListFragment.lameStatic.runNetworks = runNetworks;
        ssidSpeaker = new SsidSpeaker( mainActivity );
        // formats for speech
        timeFormat = new SimpleDateFormat( "h mm aa", Locale.US );
        numberFormat1 = NumberFormat.getNumberInstance( Locale.US );
        if ( numberFormat1 instanceof DecimalFormat ) {
            numberFormat1.setMaximumFractionDigits(1);
        }
    }

    public void setMainActivity( final MainActivity mainActivity ) {
        this.mainActivity = mainActivity;
        this.ssidSpeaker.setListActivity( mainActivity );
        if (mainActivity != null) {
            prevScanPeriod = mainActivity.getLocationSetPeriod();
            Logging.info("WifiReceiver setting prevScanPeriod: " + prevScanPeriod);
        }
    }

    public void setListAdapter( final SetNetworkListAdapter listAdapter ) {
        this.listAdapter = listAdapter;
    }

    public int getRunNetworkCount() {
        return runNetworks.size();
    }

    public void updateLastScanResponseTime() {
        lastHaveLocationTime = System.currentTimeMillis();
    }

    /**
     * the massive core receive handler for WiFi scan callback
     * @param context context of the onreceive
     * @param intent the intent for the receive
     */
    @SuppressWarnings("ConstantConditions")
    @Override
    public void onReceive( final Context context, final Intent intent ) {
        scanInFlight = false;
        final long now = System.currentTimeMillis();
        lastScanResponseTime = now;
        // final long start = now;
        final WifiManager wifiManager = (WifiManager) mainActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        List<ScanResult> results = null;
        try {
            results = wifiManager.getScanResults(); // return can be null!
        }
        catch (final SecurityException ex) {
            Logging.info("security exception getting scan results: " + ex, ex);
        }
        catch (final Exception ex) {
            // ignore, happens on some vm's
            Logging.info("exception getting scan results: " + ex, ex);
        }
        Logging.debug("wifi receive, results: " + (results == null ? null : results.size()));

        long nonstopScanRequestTime = Long.MIN_VALUE;
        final SharedPreferences prefs = mainActivity.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0 );
        final long period = getScanPeriod();
        if ( period == 0 ) {
            // treat as "continuous", so request scan in here
            doWifiScan();
            nonstopScanRequestTime = now;
        }

        final long setPeriod = mainActivity.getLocationSetPeriod();
        if ( setPeriod != prevScanPeriod && mainActivity.isScanning() ) {
            // update our location scanning speed
            Logging.info("setting location updates to: " + setPeriod);
            mainActivity.setLocationUpdates(setPeriod, 0f);

            prevScanPeriod = setPeriod;
        }

        // have the gps listener to a self-check, in case it isn't getting updates anymore
        final GNSSListener gpsListener = mainActivity.getGPSListener();
        Location location = null;
        if (gpsListener != null) {
            location = gpsListener.checkGetLocation(prefs);
        }

        // save the location every minute, for later runs, or viewing map during loss of location.
        if (now - lastSaveLocationTime > 60000L && location != null) {
            mainActivity.getGPSListener().saveLocation();
            lastSaveLocationTime = now;
        }

        if (location != null) {
            lastHaveLocationTime = now;
        }
        // MainActivity.info("now minus haveloctime: " + (now-lastHaveLocationTime)
        //    + " lastHaveLocationTime: " + lastHaveLocationTime);
        if (now - lastHaveLocationTime > 45000L) {
            // no location in a while, make sure we're subscribed to updates
            Logging.info("no location for a while, setting location update period: " + setPeriod);
            mainActivity.setLocationUpdates(setPeriod, 0f);
            // don't do this until another period has passed
            lastHaveLocationTime = now;
        }

        final boolean showCurrent = prefs.getBoolean( PreferenceKeys.PREF_SHOW_CURRENT, true );
        if ( showCurrent && listAdapter != null ) {
            listAdapter.clearWifiAndCell();
        }

        final int preQueueSize = dbHelper.getQueueSize();
        final boolean fastMode = dbHelper.isFastMode();
        final ConcurrentLinkedHashMap<String,Network> networkCache = MainActivity.getNetworkCache();
        boolean somethingAdded = false;
        int resultSize = 0;

        final boolean ssidSpeak = prefs.getBoolean( PreferenceKeys.PREF_SPEAK_SSID, false )
                && ! mainActivity.isMuted();

        //TODO: should we memoize the ssidMatcher in the MainActivity state as well?
        final Matcher ssidMatcher = FilterMatcher.getSsidFilterMatcher( prefs, PreferenceKeys.FILTER_PREF_PREFIX );
        final Matcher bssidMatcher = mainActivity.getBssidFilterMatcher( PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS );
        final Matcher bssidDbMatcher = mainActivity.getBssidFilterMatcher( PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS );
        final Matcher bssidAlertMatcher = mainActivity.getBssidFilterMatcher( PreferenceKeys.PREF_ALERT_ADDRS );

        // can be null on shutdown
        if ( results != null ) {
            resultSize = results.size();
            for ( ScanResult result : results ) {
                if (result == null) continue; // have seen in the wild

                Network network = networkCache.get( result.BSSID );
                if ( network == null ) {
                    network = new Network( result );

                    // Roaming Consortium Organizational Identifiers
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        String rcois = null;
                        for ( ScanResult.InformationElement info : result.getInformationElements()) {
                            if (info.getId() == net.wigle.wigleandroid.listener.WifiReceiver.EID_ROAMING_CONSORTIUM) {
                                rcois = net.wigle.wigleandroid.listener.WifiReceiver.getConcatenatedRcois(info);
                            }
                        }
                        network.setRcois(rcois);
                    }

                    networkCache.put( network.getBssid(), network );
                }
                else {
                    // cache hit, just set the level
                    network.setLevel( result.level );
                }

                final boolean added = runNetworks.add( result.BSSID );
                if ( added ) {
                    if ( ssidSpeak ) {
                        ssidSpeaker.add( network.getSsid() );
                    }
                }
                somethingAdded |= added;

                if ( location != null && (added || network.getLatLng() == null) ) {
                    // set the LatLng for mapping
                    final LatLng LatLng = new LatLng( location.getLatitude(), location.getLongitude() );
                    network.setLatLng( LatLng );
                    MainActivity.addNetworkToMap(network);
                }

                // if we're showing current, or this was just added, put on the list
                if ( showCurrent || added ) {
                    if ( FilterMatcher.isOk( ssidMatcher, bssidMatcher, prefs, PreferenceKeys.FILTER_PREF_PREFIX, network ) ) {
                        if (listAdapter != null) {
                            listAdapter.addWiFi( network );
                        }
                    }
                    // load test
                    // for ( int i = 0; i< 10; i++) {
                    //  listAdapter.addWifi( network );
                    // }

                } else if (listAdapter != null) {
                    // not showing current, and not a new thing, go find the network and update the level
                    // this is O(n), ohwell, that's why showCurrent is the default config.
                    for ( int index = 0; index < listAdapter.getCount(); index++ ) {
                        try {
                            final Network testNet = listAdapter.getItem(index);
                            if (null != testNet) {
                                if ( testNet.getBssid().equals( network.getBssid() ) ) {
                                    testNet.setLevel( result.level );
                                }
                            }
                        }
                        catch (final IndexOutOfBoundsException ex) {
                            // yes, this happened to someone
                            Logging.info("WifiReceiver: index out of bounds: " + index + " ex: " + ex);
                        }
                    }
                }

                if ( location != null  ) {
                    // if in fast mode, only add new-for-run stuff to the db queue
                    if ( fastMode && ! added ) {
                        Logging.info( "in fast mode, not adding seen-this-run: " + network.getBssid() );
                    } else {
                        // loop for stress-testing
                        // for ( int i = 0; i < 10; i++ ) {
                        boolean matches = false;
                        if (bssidDbMatcher != null) {
                            bssidDbMatcher.reset(network.getBssid());
                            matches = bssidDbMatcher.find();
                        }
                        if (!matches) {
                            dbHelper.addObservation(network, location, added);
                        }
                        // }
                    }
                } else {
                    // no location
                    boolean matches = false;
                    if (bssidDbMatcher != null) {
                        bssidDbMatcher.reset(network.getBssid());
                        matches = bssidDbMatcher.find();
                    }
                    if (!matches) {
                        dbHelper.pendingObservation( network, added, false, false );
                    }
                }

                if (bssidAlertMatcher != null) {
                    bssidAlertMatcher.reset(network.getBssid());
                    if (bssidAlertMatcher.find()) {
                        if (null != mainActivity) {
                            mainActivity.updateLastHighSignal(network.getLevel());
                        }
                    }
                }

                if (null != updateOnSeen && null != safeWatchSsids && null != location) {
                    if (safeWatchSsids.contains(network.getBssid())) {
                        updateOnSeen.handleWiFiSeen(network.getBssid(), result.level, location);
                    }
                }
            }
        }

        // check if there are more "New" nets
        final long newNetCount = dbHelper.getNewNetworkCount();
        final long newWifiCount = dbHelper.getNewWifiCount();
        final long newNetDiff = newWifiCount - prevNewNetCount;
        prevNewNetCount = newWifiCount;

        if ( ! mainActivity.isMuted() ) {
            final boolean playRun = prefs.getBoolean( PreferenceKeys.PREF_FOUND_SOUND, true );
            final boolean playNew = prefs.getBoolean( PreferenceKeys.PREF_FOUND_NEW_SOUND, true );
            if ( newNetDiff > 0 && playNew ) {
                mainActivity.playNewNetSound();
            }
            else if ( somethingAdded && playRun ) {
                mainActivity.playRunNetSound();
            }
        }

        if ( mainActivity.getPhoneState().isPhoneActive() ) {
            // a phone call is active, make sure we aren't speaking anything
            mainActivity.interruptSpeak();
        }

        final long effectiveScanRequestTime = scanRequestTime <= 0 ? now : scanRequestTime;
        // setting statics for shared access
        ListFragment.lameStatic.currWifi = resultSize;
        ListFragment.lameStatic.currNets = resultSize + ListFragment.lameStatic.currCells; //TODO: outdated? (1/2)
        ListFragment.lameStatic.runNets = runNetworks.size();
        ListFragment.lameStatic.newNets = newNetCount;
        ListFragment.lameStatic.newWifi = newWifiCount;
        ListFragment.lameStatic.currWifiScanDurMs = (now - effectiveScanRequestTime);
        ListFragment.lameStatic.preQueueSize = preQueueSize;
        ListFragment.lameStatic.dbNets = dbHelper.getNetworkCount();
        ListFragment.lameStatic.dbLocs = dbHelper.getLocationCount();

        NetworkListUtil.sort(prefs, listAdapter);
        mainActivity.setNetCountUI();

        if (scanRequestTime <= 0) {
            scanRequestTime = now;
        }

        mainActivity.setScanStatusUI(ListFragment.lameStatic.currNets, ListFragment.lameStatic.currWifiScanDurMs);
        mainActivity.setDBQueue(preQueueSize);
        scanRequestTime = nonstopScanRequestTime;

        if (somethingAdded && ssidSpeak) {
            ssidSpeaker.speak();
        }

        final long speechPeriod = prefs.getLong(PreferenceKeys.PREF_SPEECH_PERIOD, MainActivity.DEFAULT_SPEECH_PERIOD);
        if (speechPeriod != 0 && now - previousTalkTime > speechPeriod * 1000L) {
            doAnnouncement(preQueueSize, newWifiCount, ListFragment.lameStatic.newCells, ListFragment.lameStatic.newBt, now);
        }
    }

    public static final int NIBBLE_MASK = 0x0f;
    public static final int BYTE_MASK = 0xff;

    public static final int EID_ROAMING_CONSORTIUM = 111;

    // get any Roaming Consortium Organizational identifiers from beacon and concatenate
    // @param ScanResult.InformationElement
    // @return a string of concatenated RCOIS with " " delimiter
    //

    public static String getConcatenatedRcois (ScanResult.InformationElement ie) {
        String concatenatedRcois = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ie.getId() != EID_ROAMING_CONSORTIUM) {
                throw new IllegalArgumentException("Element id is not ROAMING_CONSORTIUM, : "
                        + ie.getId());
            }
            // RCOI length handling from https://android.googlesource.com/platform/frameworks/opt/net/wifi/+/6f5af9b7f69b15369238bd2642c46638ba1f0255/service/java/com/android/server/wifi/util/InformationElementUtil.java#206

            // Roaming Consortium (OI) element format defined in IEEE 802.11 clause 9.4.2.95
            // ElementID (1 Octet), Length (1 Octet), Number of OIs (1 Octet), OI #1 and #2 Lengths (1 Octet), OI#1 (variable), OI#2 (variable), OI#3 (variable)
            // where 1 octet "OI #1 and #2 Length" comprises: OI#1 Length [B0-B3], OI#2 Length [B4-B7]
            ByteBuffer data = ie.getBytes().order(ByteOrder.LITTLE_ENDIAN);
            data.get(); // anqpOICount
            int oi12Length = data.get() & BYTE_MASK;
            int oi1Length = oi12Length & NIBBLE_MASK;
            int oi2Length = (oi12Length >>> 4) & NIBBLE_MASK;
            int oi3Length = ie.getBytes().limit() - 2 - oi1Length - oi2Length;

            if (oi1Length > 0) {
                final long rcoiInteger = getInteger(data, ByteOrder.BIG_ENDIAN, oi1Length, 0);
                concatenatedRcois = formatRcoi(rcoiInteger);
            }
            if (oi2Length > 0) {
                final long rcoiInteger = getInteger(data, ByteOrder.BIG_ENDIAN, oi2Length, oi1Length);
                concatenatedRcois += " " + formatRcoi(rcoiInteger);
            }
            if (oi3Length > 0) {
                final long rcoiInteger = getInteger(data, ByteOrder.BIG_ENDIAN, oi3Length, oi2Length + oi1Length);
                concatenatedRcois += " " + formatRcoi(rcoiInteger);
            }
        }
        // OpenRoaming example "5A03BA0000 BAA2D00000 BAA2D02000"
        return concatenatedRcois;
    }

    private static String formatRcoi(final long rcoi) {
        if (rcoi < 16777216) {
            return String.format("%1$06X", rcoi);
        }
        return String.format("%1$010X", rcoi);
    }

    public static long getInteger(ByteBuffer payload, ByteOrder bo, int size, int position) {
        payload.position(position + 2);
        long value = 0;
        if (bo == ByteOrder.LITTLE_ENDIAN) {
            final byte[] octets = new byte[size];
            payload.get(octets);

            for (int n = octets.length - 1; n >= 0; n--) {
                value = (value << Byte.SIZE) | (octets[n] & BYTE_MASK);
            }
        }
        else {
            for (int i = 0; i < size; i++) {
                final byte octet = payload.get();
                value = (value << Byte.SIZE) | (octet & BYTE_MASK);
            }
        }
        return value;
    }


    /**
     * Voice announcement method for scan
     */
    private void doAnnouncement( int preQueueSize, long newWifiCount, long newCellCount, long newBtCount, long now ) {
        final SharedPreferences prefs = mainActivity.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0 );
        StringBuilder builder = new StringBuilder();

        if ( mainActivity.getGPSListener().getCurrentLocation() == null && prefs.getBoolean( PreferenceKeys.PREF_SPEECH_GPS, true ) ) {
            builder.append(mainActivity.getString(R.string.tts_no_gps_fix)).append(", ");
        }

        // run, new, queue, miles, time, battery
        if ( prefs.getBoolean( PreferenceKeys.PREF_SPEAK_RUN, true ) ) {
            builder.append(mainActivity.getString(R.string.run)).append(" ")
                    .append(runNetworks.size() + ListFragment.lameStatic.runCells).append( ", " ); //TODO: also outdated? (2/2)
        }
        if ( prefs.getBoolean( PreferenceKeys.PREF_SPEAK_NEW_WIFI, true ) ) {
            builder.append(mainActivity.getString(R.string.tts_new_wifi)).append(" ")
                    .append(newWifiCount).append( ", " );
        }
        if ( prefs.getBoolean( PreferenceKeys.PREF_SPEAK_NEW_CELL, true ) ) {
            builder.append(mainActivity.getString(R.string.tts_new_cell)).append(" ")
                    .append(newCellCount).append( ", " );
        }
        if ( prefs.getBoolean( PreferenceKeys.PREF_SPEAK_NEW_BT, true ) ) {
            builder.append(mainActivity.getString(R.string.tts_new_bt)).append(" ")
                    .append(newBtCount).append( ", " );
        }
        if ( preQueueSize > 0 && prefs.getBoolean( PreferenceKeys.PREF_SPEAK_QUEUE, true ) ) {
            builder.append(mainActivity.getString(R.string.tts_queue)).append(" ")
                    .append(preQueueSize).append( ", " );
        }
        if ( prefs.getBoolean( PreferenceKeys.PREF_SPEAK_MILES, true ) ) {
            final float dist = prefs.getFloat( PreferenceKeys.PREF_DISTANCE_RUN, 0f );
            final String distString = UINumberFormat.metersToString(prefs, numberFormat1, mainActivity, dist, false );
            builder.append(mainActivity.getString(R.string.tts_from)).append(" ")
                    .append(distString).append( ", " );
        }
        if ( prefs.getBoolean( PreferenceKeys.PREF_SPEAK_TIME, true ) ) {
            String time = timeFormat.format( new Date() );
            // time is hard to say.
            time = time.replace(" 00", " " + mainActivity.getString(R.string.tts_o_clock));
            time = time.replace(" 0", " " + mainActivity.getString(R.string.tts_o) +  " ");
            builder.append( time ).append( ", " );
        }
        final int batteryLevel = mainActivity.getBatteryLevelReceiver().getBatteryLevel();
        if ( batteryLevel >= 0 && prefs.getBoolean( PreferenceKeys.PREF_SPEAK_BATTERY, true ) ) {
            builder.append(mainActivity.getString(R.string.tts_battery)).append(" ").append(batteryLevel).append(" ").append(mainActivity.getString(R.string.tts_percent)).append(", ");
        }

        final String speak = builder.toString();
        Logging.info( "speak: " + speak );
        if (!speak.isEmpty()) {
            mainActivity.speak( builder.toString() );
        }
        previousTalkTime = now;
    }

    public void setupWifiTimer( final boolean turnedWifiOn ) {
        Logging.info( "create wifi timer" );
        if ( wifiTimer == null ) {
            wifiTimer = new Handler();
            final Runnable mUpdateTimeTask = new Runnable() {
                @Override
                public void run() {
                    // make sure the app isn't trying to finish
                    if ( ! mainActivity.isFinishing() ) {
                        // info( "timer start scan" );
                        doWifiScan();
                        if ( scanRequestTime <= 0 ) {
                            scanRequestTime = System.currentTimeMillis();
                        }
                        long period = getScanPeriod();
                        // check if set to "continuous"
                        if ( period == 0L ) {
                            // set to default here, as a scan will also be requested on the scan result listener
                            period = MainActivity.SCAN_DEFAULT;
                        }
                        // info("wifitimer: " + period );
                        wifiTimer.postDelayed( this, period );
                    }
                    else {
                        Logging.info( "finishing timer" );
                    }
                }
            };
            wifiTimer.removeCallbacks( mUpdateTimeTask );
            wifiTimer.postDelayed( mUpdateTimeTask, 100 );

            if ( turnedWifiOn ) {
                Logging.info( "not immediately running wifi scan, since it was just turned on"
                        + " it will block for a few seconds and fail anyway");
            }
            else {
                Logging.info( "start first wifi scan");
                // starts scan, sends event when done
                final boolean scanOK = doWifiScan();
                if ( scanRequestTime <= 0 ) {
                    scanRequestTime = System.currentTimeMillis();
                }
                Logging.info( "startup finished. wifi scanOK: " + scanOK );
            }
        }
    }

    /**
     * get the scan period based on preferences and current speed
     */
    public long getScanPeriod() {
        if (mainActivity == null) {
            return MainActivity.SCAN_DEFAULT;
        }
        final SharedPreferences prefs = mainActivity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        Location location = null;
        final GNSSListener gpsListener = mainActivity.getGPSListener();
        if (gpsListener != null) {
            location = gpsListener.getCurrentLocation();
        }
        return ScanUtil.getWifiScanPeriod(prefs, location);
    }

    /**
     * Schedule the next WiFi scan
     */
    public void scheduleScan() {
        wifiTimer.post(this::doWifiScan);
    }

    public synchronized void registerWiFiScanUpdater(final WiFiScanUpdater updater, final Set<String> watchBssids) {
        safeWatchSsids.addAll(watchBssids);
        updateOnSeen = updater;
    }

    public synchronized void unregisterWiFiScanUpdater() {
        updateOnSeen = null;
        safeWatchSsids.clear();
    }

    /**
     * only call this from a Handler
     * Runs WifiManager.startScan() on a background thread to avoid ANR from Binder blocking.
     * @return true if a scan was submitted (optimistic; actual result is applied asynchronously)
     */
    private boolean doWifiScan() {
        // MainActivity.info("do wifi scan. lastScanTime: " + lastScanResponseTime);
        final WifiManager wifiManager = (WifiManager) mainActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean submitted = false;

        if (mainActivity.isScanning()) {
            if ( ! scanInFlight ) {
                scanInFlight = true;
                submitted = true;
                wifiScanExecutor.execute(() -> {
                    boolean success = false;
                    try {
                        success = wifiManager.startScan();
                    } catch (Exception ex) {
                        Logging.warn("exception starting scan: " + ex, ex);
                    }
                    final boolean scanSuccess = success;
                    mainHandler.post(() -> {
                        if (!scanSuccess) {
                            scanInFlight = false;
                        }
                        Logging.debug("startScan returned " + scanSuccess + ". last response seconds ago: " + (System.currentTimeMillis() - lastScanResponseTime) / 1000d);
                    });
                });
            }

            final long now = System.currentTimeMillis();
            if ( lastScanResponseTime < 0 ) {
                // use now, since we made a request
                lastScanResponseTime = now;
            } else {
                final long sinceLastScan = now - lastScanResponseTime;
                final SharedPreferences prefs = mainActivity.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0 );
                final long resetWifiPeriod = prefs.getLong(
                        PreferenceKeys.PREF_RESET_WIFI_PERIOD, MainActivity.DEFAULT_RESET_WIFI_PERIOD );

                if ( resetWifiPeriod > 0 && sinceLastScan > resetWifiPeriod ) {
                    Logging.warn("Time since last scan: " + sinceLastScan + " milliseconds");
                    if ( now - lastWifiUnjamTime > resetWifiPeriod ) {
                        final boolean disableToast = prefs.getBoolean(PreferenceKeys.PREF_DISABLE_TOAST, false);
                        if (!disableToast) {
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.post(() -> WiGLEToast.showOverActivity(mainActivity, R.string.error_general, mainActivity.getString(R.string.wifi_jammed)));
                        }
                        scanInFlight = false;
                        try {
                            if (wifiManager != null) {
                                wifiManager.setWifiEnabled(false);
                                wifiManager.setWifiEnabled(true);
                            }
                        } catch (SecurityException ex) {
                            Logging.info("exception resetting wifi: " + ex, ex);
                        }
                        lastWifiUnjamTime = now;
                        if (prefs.getBoolean(PreferenceKeys.PREF_SPEAK_WIFI_RESTART, true)) {
                            mainActivity.speak(mainActivity.getString(R.string.wifi_restart_1) + " "
                                    + (sinceLastScan / 1000L) + " " + mainActivity.getString(R.string.wifi_restart_2));
                        }
                    }
                }
            }
        }
        else {
            // scanning is off. since we're the only timer, update the UI
            mainActivity.setNetCountUI();
            mainActivity.setLocationUI();
            mainActivity.setScanStatusUI(mainActivity.getString(R.string.list_scanning_off));
            // keep the scan times from getting huge
            scanRequestTime = System.currentTimeMillis();
            // reset this
            lastScanResponseTime = Long.MIN_VALUE;
        }

        // battery kill
        if ( ! mainActivity.isTransferring() ) {
            final SharedPreferences prefs = mainActivity.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0 );
            long batteryKill = prefs.getLong(
                    PreferenceKeys.PREF_BATTERY_KILL_PERCENT, MainActivity.DEFAULT_BATTERY_KILL_PERCENT);

            if ( mainActivity.getBatteryLevelReceiver() != null ) {
                final int batteryLevel = mainActivity.getBatteryLevelReceiver().getBatteryLevel();
                final int batteryStatus = mainActivity.getBatteryLevelReceiver().getBatteryStatus();
                // MainActivity.info("batteryStatus: " + batteryStatus);
                // give some time since starting up to change this configuration
                if ( batteryKill > 0 && batteryLevel > 0 && batteryLevel <= batteryKill
                        && batteryStatus != BatteryManager.BATTERY_STATUS_CHARGING
                        && (System.currentTimeMillis() - constructionTime) > 30000L) {
                    if (null != mainActivity) {
                        final String text = mainActivity.getString(R.string.battery_at) + " " + batteryLevel + " "
                            + mainActivity.getString(R.string.battery_postfix);

                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(() -> WiGLEToast.showOverActivity(mainActivity, R.string.error_general, text));
                        Logging.warn("low battery, shutting down");
                        mainActivity.speak(text);
                        mainActivity.finishSoon(4000L, false);
                    }
                }
            }
        }

        return submitted;
    }
}
