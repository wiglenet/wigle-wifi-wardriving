package net.wigle.wigleandroid.listener;

import static android.location.LocationManager.GPS_PROVIDER;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.DashboardFragment;
import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.NetworkListAdapter;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.FilterMatcher;
import net.wigle.wigleandroid.R;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

public class WifiReceiver extends BroadcastReceiver {
    private MainActivity mainActivity;
    private final DatabaseHelper dbHelper;
    private NetworkListAdapter listAdapter;
    private final SimpleDateFormat timeFormat;
    private final NumberFormat numberFormat1;
    private final SsidSpeaker ssidSpeaker;

    private Handler wifiTimer;
    private Location prevGpsLocation;
    private long scanRequestTime = Long.MIN_VALUE;
    private long lastScanResponseTime = Long.MIN_VALUE;
    private long lastWifiUnjamTime = 0;
    private long lastSaveLocationTime = 0;
    private long lastHaveLocationTime = 0;
    private int pendingWifiCount = 0;
    private int pendingCellCount = 0;
    private final long constructionTime = System.currentTimeMillis();
    private long previousTalkTime = System.currentTimeMillis();
    private final Set<String> runNetworks = new HashSet<>();
    private long prevNewNetCount;
    private long prevScanPeriod;
    private boolean scanInFlight = false;

    public static final int SIGNAL_COMPARE = 10;
    public static final int CHANNEL_COMPARE = 11;
    public static final int CRYPTO_COMPARE = 12;
    public static final int FIND_TIME_COMPARE = 13;
    public static final int SSID_COMPARE = 14;

    private static final Comparator<Network> signalCompare = new Comparator<Network>() {
        @Override
        public int compare( Network a, Network b ) {
            return b.getLevel() - a.getLevel();
        }
    };

    private static final Comparator<Network> channelCompare = new Comparator<Network>() {
        @Override
        public int compare( Network a, Network b ) {
            return a.getFrequency() - b.getFrequency();
        }
    };

    private static final Comparator<Network> cryptoCompare = new Comparator<Network>() {
        @Override
        public int compare( Network a, Network b ) {
            return b.getCrypto() - a.getCrypto();
        }
    };

    private static final Comparator<Network> findTimeCompare = new Comparator<Network>() {
        @Override
        public int compare( Network a, Network b ) {
            return (int) (b.getConstructionTime() - a.getConstructionTime());
        }
    };

    private static final Comparator<Network> ssidCompare = new Comparator<Network>() {
        @Override
        public int compare( Network a, Network b ) {
            return a.getSsid().compareTo( b.getSsid() );
        }
    };

    public WifiReceiver( final MainActivity mainActivity, final DatabaseHelper dbHelper ) {
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
            MainActivity.info("WifiReceiver setting prevScanPeriod: " + prevScanPeriod);
        }
    }

    public void setListAdapter( final NetworkListAdapter listAdapter ) {
        this.listAdapter = listAdapter;
    }

    public int getRunNetworkCount() {
        return runNetworks.size();
    }

    public void updateLastScanResponseTime() {
        lastHaveLocationTime = System.currentTimeMillis();
    }

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
            MainActivity.info("security exception getting scan results: " + ex, ex);
        }
        catch (final Exception ex) {
            // ignore, happens on some vm's
            MainActivity.info("exception getting scan results: " + ex, ex);
        }

        long nonstopScanRequestTime = Long.MIN_VALUE;
        final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        final long period = getScanPeriod();
        if ( period == 0 ) {
            // treat as "continuous", so request scan in here
            doWifiScan();
            nonstopScanRequestTime = now;
        }

        final long setPeriod = mainActivity.getLocationSetPeriod();
        if ( setPeriod != prevScanPeriod && mainActivity.isScanning() ) {
            // update our location scanning speed
            MainActivity.info("setting location updates to: " + setPeriod);
            mainActivity.setLocationUpdates(setPeriod, 0f);

            prevScanPeriod = setPeriod;
        }

        // have the gps listener to a self-check, in case it isn't getting updates anymore
        final GPSListener gpsListener = mainActivity.getGPSListener();
        Location location = null;
        if (gpsListener != null) {
            gpsListener.checkLocationOK();
            location = gpsListener.getLocation();
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
        if (now - lastHaveLocationTime > 30000L) {
            // no location in a while, make sure we're subscribed to updates
            MainActivity.info("no location for a while, setting location update period: " + setPeriod);
            mainActivity.setLocationUpdates(setPeriod, 0f);
            // don't do this until another period has passed
            lastHaveLocationTime = now;
        }

        final boolean showCurrent = prefs.getBoolean( ListFragment.PREF_SHOW_CURRENT, true );
        if ( showCurrent && listAdapter != null ) {
            listAdapter.clear();
        }

        final int preQueueSize = dbHelper.getQueueSize();
        final boolean fastMode = dbHelper.isFastMode();
        final ConcurrentLinkedHashMap<String,Network> networkCache = MainActivity.getNetworkCache();
        boolean somethingAdded = false;
        int resultSize = 0;
        int newWifiForRun = 0;

        final boolean ssidSpeak = prefs.getBoolean( ListFragment.PREF_SPEAK_SSID, false )
                && ! mainActivity.isMuted();
        final Matcher ssidMatcher = FilterMatcher.getFilterMatcher( prefs, ListFragment.FILTER_PREF_PREFIX );

        // can be null on shutdown
        if ( results != null ) {
            resultSize = results.size();
            for ( ScanResult result : results ) {
                Network network = networkCache.get( result.BSSID );
                if ( network == null ) {
                    network = new Network( result );
                    networkCache.put( network.getBssid(), network );
                }
                else {
                    // cache hit, just set the level
                    network.setLevel( result.level );
                }

                final boolean added = runNetworks.add( result.BSSID );
                if ( added ) {
                    newWifiForRun++;
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
                    if ( FilterMatcher.isOk( ssidMatcher, prefs, ListFragment.FILTER_PREF_PREFIX, network ) ) {
                        if (listAdapter != null) {
                            listAdapter.add( network );
                        }
                    }
                    // load test
                    // for ( int i = 0; i< 10; i++) {
                    //  listAdapter.add( network );
                    // }

                }
                else if (listAdapter != null){
                    // not showing current, and not a new thing, go find the network and update the level
                    // this is O(n), ohwell, that's why showCurrent is the default config.
                    for ( int index = 0; index < listAdapter.getCount(); index++ ) {
                        final Network testNet = listAdapter.getItem(index);
                        if ( testNet.getBssid().equals( network.getBssid() ) ) {
                            testNet.setLevel( result.level );
                        }
                    }
                }

                if ( location != null  ) {
                    // if in fast mode, only add new-for-run stuff to the db queue
                    if ( fastMode && ! added ) {
                        MainActivity.info( "in fast mode, not adding seen-this-run: " + network.getBssid() );
                    }
                    else {
                        // loop for stress-testing
                        // for ( int i = 0; i < 10; i++ ) {
                        dbHelper.addObservation( network, location, added );
                        // }
                    }
                } else {
                    // no location
                    dbHelper.pendingObservation( network, added );
                }
            }
        }

        // check if there are more "New" nets
        final long newNetCount = dbHelper.getNewNetworkCount();
        final long newWifiCount = dbHelper.getNewWifiCount();
        final long newNetDiff = newWifiCount - prevNewNetCount;
        prevNewNetCount = newWifiCount;
        // check for "New" cell towers
        final long newCellCount = dbHelper.getNewCellCount();

        if ( ! mainActivity.isMuted() ) {
            final boolean playRun = prefs.getBoolean( ListFragment.PREF_FOUND_SOUND, true );
            final boolean playNew = prefs.getBoolean( ListFragment.PREF_FOUND_NEW_SOUND, true );
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

        // check cell tower info
        final int preCellForRun = runNetworks.size();
        int newCellForRun = 0;
        final Network cellNetwork = recordCellInfo(location);
        if ( cellNetwork != null ) {
            resultSize++;
            if ( showCurrent && listAdapter != null && FilterMatcher.isOk( ssidMatcher, prefs, ListFragment.FILTER_PREF_PREFIX, cellNetwork ) ) {
                listAdapter.add(cellNetwork);
            }
            if ( runNetworks.size() > preCellForRun ) {
                newCellForRun++;
            }
        }

        final int sort = prefs.getInt(ListFragment.PREF_LIST_SORT, SIGNAL_COMPARE);
        Comparator<Network> comparator = signalCompare;
        switch ( sort ) {
            case SIGNAL_COMPARE:
                comparator = signalCompare;
                break;
            case CHANNEL_COMPARE:
                comparator = channelCompare;
                break;
            case CRYPTO_COMPARE:
                comparator = cryptoCompare;
                break;
            case FIND_TIME_COMPARE:
                comparator = findTimeCompare;
                break;
            case SSID_COMPARE:
                comparator = ssidCompare;
                break;
        }
        if (listAdapter != null) {
            listAdapter.sort( comparator );
        }

        final long dbNets = dbHelper.getNetworkCount();
        final long dbLocs = dbHelper.getLocationCount();

        // update stat
        mainActivity.setNetCountUI();

        // set the statics for the map
        ListFragment.lameStatic.runNets = runNetworks.size();
        ListFragment.lameStatic.newNets = newNetCount;
        ListFragment.lameStatic.newWifi = newWifiCount;
        ListFragment.lameStatic.newCells = newCellCount;
        ListFragment.lameStatic.currNets = resultSize;
        ListFragment.lameStatic.preQueueSize = preQueueSize;
        ListFragment.lameStatic.dbNets = dbNets;
        ListFragment.lameStatic.dbLocs = dbLocs;

        // do this if trail is empty, so as soon as we get first gps location it gets triggered
        // and will show up on map
        if ( newWifiForRun > 0 || newCellForRun > 0 || ListFragment.lameStatic.networkCache.isEmpty() ) {
            if ( location == null ) {
                // save for later
                pendingWifiCount += newWifiForRun;
                pendingCellCount += newCellForRun;
                // MainActivity.info("pendingCellCount: " + pendingCellCount);
            }
            else {
                // add any pendings
                // don't go crazy
                if ( pendingWifiCount > 25 ) {
                    pendingWifiCount = 25;
                }
                pendingWifiCount = 0;

                if ( pendingCellCount > 25 ) {
                    pendingCellCount = 25;
                }
                pendingCellCount = 0;
            }
        }

        // info( savedStats );

        // notify
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }

        if ( scanRequestTime <= 0 ) {
            // wasn't set, set to now
            scanRequestTime = now;
        }
        final String status = resultSize + " " + mainActivity.getString(R.string.scanned_in) + " "
                + (now - scanRequestTime) + mainActivity.getString(R.string.ms_short) + ". "
                + mainActivity.getString(R.string.dash_db_queue) + " " + preQueueSize;
        mainActivity.setStatusUI( status );
        // we've shown it, reset it to the nonstop time above, or min_value if nonstop wasn't set.
        scanRequestTime = nonstopScanRequestTime;

        // do lerp if need be
        if ( location == null ) {
            if ( prevGpsLocation != null ) {
                dbHelper.lastLocation( prevGpsLocation );
                // MainActivity.info("set last location for lerping");
            }
        }
        else {
            dbHelper.recoverLocations( location );
        }

        // do distance calcs
        if ( location != null && GPS_PROVIDER.equals( location.getProvider() )
                && location.getAccuracy() <= ListFragment.MIN_DISTANCE_ACCURACY ) {
            if ( prevGpsLocation != null ) {
                float dist = location.distanceTo( prevGpsLocation );
                // info( "dist: " + dist );
                if ( dist > 0f ) {
                    final Editor edit = prefs.edit();
                    edit.putFloat( ListFragment.PREF_DISTANCE_RUN,
                            dist + prefs.getFloat( ListFragment.PREF_DISTANCE_RUN, 0f ) );
                    edit.putFloat( ListFragment.PREF_DISTANCE_TOTAL,
                            dist + prefs.getFloat( ListFragment.PREF_DISTANCE_TOTAL, 0f ) );
                    edit.apply();
                }
            }

            // set for next time
            prevGpsLocation = location;
        }

        if ( somethingAdded && ssidSpeak ) {
            ssidSpeaker.speak();
        }

        final long speechPeriod = prefs.getLong( ListFragment.PREF_SPEECH_PERIOD, MainActivity.DEFAULT_SPEECH_PERIOD );
        if ( speechPeriod != 0 && now - previousTalkTime > speechPeriod * 1000L ) {
            doAnnouncement( preQueueSize, newWifiCount, newCellCount, now );
        }
    }

    public String getNetworkTypeName() {
        TelephonyManager tele = (TelephonyManager) mainActivity.getSystemService( Context.TELEPHONY_SERVICE );
        if ( tele == null ) {
            return null;
        }
        switch (tele.getNetworkType()) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE";
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS";
            case 4:
                return "CDMA";
            case 5:
                return "CDMA - EvDo rev. 0";
            case 6:
                return "CDMA - EvDo rev. A";
            case 7:
                return "CDMA - 1xRTT";
            case 8:
                return "HSDPA";
            case 9:
                return "HSUPA";
            case 10:
                return "HSPA";
            case 11:
                return "IDEN";
            case 12:
                return "CDMA - EvDo rev. B";
            default:
                return "UNKNOWN";
        }
    }

    private Network recordCellInfo(final Location location) {
        TelephonyManager tele = (TelephonyManager) mainActivity.getSystemService( Context.TELEPHONY_SERVICE );
        Network network = null;
        if ( tele != null ) {
      /*
      List<NeighboringCellInfo> list = tele.getNeighboringCellInfo();
      for (final NeighboringCellInfo cell : list ) {
        MainActivity.info("neigh cell: " + cell + " class: " + cell.getClass().getCanonicalName() );
        MainActivity.info("cid: " + cell.getCid());

        // api level 5!!!!
        MainActivity.info("lac: " + cell.getLac() );
        MainActivity.info("psc: " + cell.getPsc() );
        MainActivity.info("net type: " + cell.getNetworkType() );
        MainActivity.info("nettypename: " + getNetworkTypeName() );
      }
      */
            String bssid = null;
            NetworkType type = null;

            CellLocation cellLocation = null;
            try {
                cellLocation = tele.getCellLocation();
            }
            catch ( NullPointerException ex ) {
                // bug in Archos7 can NPE there, just ignore
            }
            catch (final SecurityException ex) {
                MainActivity.info("Security exception tele.getCellLocation: " + ex);
            }

            //noinspection StatementWithEmptyBody
            if ( cellLocation == null ) {
                // ignore
            }
            else if ( cellLocation.getClass().getSimpleName().equals("CdmaCellLocation") ) {
                try {
                    final int systemId = (Integer) cellLocation.getClass().getMethod("getSystemId").invoke(cellLocation);
                    final int networkId = (Integer) cellLocation.getClass().getMethod("getNetworkId").invoke(cellLocation);
                    final int baseStationId = (Integer) cellLocation.getClass().getMethod("getBaseStationId").invoke(cellLocation);
                    if ( systemId > 0 && networkId >= 0 && baseStationId >= 0 ) {
                        bssid = systemId + "_" + networkId + "_" + baseStationId;
                        type = NetworkType.CDMA;
                    }
                }
                catch ( Exception ex ) {
                    MainActivity.error("cdma reflection exception: " + ex);
                }
            }
            else if ( cellLocation instanceof GsmCellLocation ) {
                GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
                if ( gsmCellLocation.getLac() >= 0 && gsmCellLocation.getCid() >= 0 ) {
                    bssid = tele.getNetworkOperator() + "_" + gsmCellLocation.getLac() + "_" + gsmCellLocation.getCid();
                    type = NetworkType.GSM;
                }
            }

            if ( bssid != null ) {
                final String ssid = tele.getNetworkOperatorName();
                final String networkType = getNetworkTypeName();
                final String capabilities = networkType + ";" + tele.getNetworkCountryIso();

                int strength = 0;
                PhoneState phoneState = mainActivity.getPhoneState();
                if (phoneState != null) {
                    strength = phoneState.getStrength();
                }

                if ( NetworkType.GSM.equals(type) ) {
                    strength = gsmRssiMagicDecoderRing( strength );
                }

//          MainActivity.info( "bssid: " + bssid );
//          MainActivity.info( "strength: " + strength );
//          MainActivity.info( "ssid: " + ssid );
//          MainActivity.info( "capabilities: " + capabilities );
//          MainActivity.info( "networkType: " + networkType );
//          MainActivity.info( "location: " + location );

                final ConcurrentLinkedHashMap<String,Network> networkCache = MainActivity.getNetworkCache();

                final boolean newForRun = runNetworks.add( bssid );

                network = networkCache.get( bssid );
                if ( network == null ) {
                    network = new Network( bssid, ssid, 0, capabilities, strength, type );
                    networkCache.put( network.getBssid(), network );
                }
                else {
                    network.setLevel(strength);
                }

                if ( location != null && (newForRun || network.getLatLng() == null) ) {
                    // set the LatLng for mapping
                    final LatLng LatLng = new LatLng( location.getLatitude(), location.getLongitude() );
                    network.setLatLng( LatLng );
                }

                if ( location != null ) {
                    dbHelper.addObservation(network, location, newForRun);
                }
            }
        }

        return network;
    }

    private int gsmRssiMagicDecoderRing( int strength ) {
        int retval;
        if ( strength == 99 ) {
            // unknown
            retval = -113;
        }
        else {
            retval = ((strength - 31) * 2) - 51;
        }
        // MainActivity.info("strength: " + strength + " retval: " + retval);
        return retval;
    }

    private void doAnnouncement( int preQueueSize, long newWifiCount, long newCellCount, long now ) {
        final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        StringBuilder builder = new StringBuilder();

        if ( mainActivity.getGPSListener().getLocation() == null && prefs.getBoolean( ListFragment.PREF_SPEECH_GPS, true ) ) {
            builder.append(mainActivity.getString(R.string.tts_no_gps_fix)).append(", ");
        }

        // run, new, queue, miles, time, battery
        if ( prefs.getBoolean( ListFragment.PREF_SPEAK_RUN, true ) ) {
            builder.append(mainActivity.getString(R.string.run)).append(" ")
                    .append(runNetworks.size()).append( ", " );
        }
        if ( prefs.getBoolean( ListFragment.PREF_SPEAK_NEW_WIFI, true ) ) {
            builder.append(mainActivity.getString(R.string.tts_new_wifi)).append(" ")
                    .append(newWifiCount).append( ", " );
        }
        if ( prefs.getBoolean( ListFragment.PREF_SPEAK_NEW_CELL, true ) ) {
            builder.append(mainActivity.getString(R.string.tts_new_cell)).append(" ")
                    .append(newCellCount).append( ", " );
        }
        if ( preQueueSize > 0 && prefs.getBoolean( ListFragment.PREF_SPEAK_QUEUE, true ) ) {
            builder.append(mainActivity.getString(R.string.tts_queue)).append(" ")
                    .append(preQueueSize).append( ", " );
        }
        if ( prefs.getBoolean( ListFragment.PREF_SPEAK_MILES, true ) ) {
            final float dist = prefs.getFloat( ListFragment.PREF_DISTANCE_RUN, 0f );
            final String distString = DashboardFragment.metersToString( numberFormat1, mainActivity, dist, false );
            builder.append(mainActivity.getString(R.string.tts_from)).append(" ")
                    .append(distString).append( ", " );
        }
        if ( prefs.getBoolean( ListFragment.PREF_SPEAK_TIME, true ) ) {
            String time = timeFormat.format( new Date() );
            // time is hard to say.
            time = time.replace(" 00", " " + mainActivity.getString(R.string.tts_o_clock));
            time = time.replace(" 0", " " + mainActivity.getString(R.string.tts_o) +  " ");
            builder.append( time ).append( ", " );
        }
        final int batteryLevel = mainActivity.getBatteryLevelReceiver().getBatteryLevel();
        if ( batteryLevel >= 0 && prefs.getBoolean( ListFragment.PREF_SPEAK_BATTERY, true ) ) {
            builder.append(mainActivity.getString(R.string.tts_battery)).append(" ").append(batteryLevel).append(" ").append(mainActivity.getString(R.string.tts_percent)).append(", ");
        }

        final String speak = builder.toString();
        MainActivity.info( "speak: " + speak );
        if (! "".equals(speak)) {
            mainActivity.speak( builder.toString() );
        }
        previousTalkTime = now;
    }

    public void setupWifiTimer( final boolean turnedWifiOn ) {
        MainActivity.info( "create wifi timer" );
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
                        MainActivity.info( "finishing timer" );
                    }
                }
            };
            wifiTimer.removeCallbacks( mUpdateTimeTask );
            wifiTimer.postDelayed( mUpdateTimeTask, 100 );

            if ( turnedWifiOn ) {
                MainActivity.info( "not immediately running wifi scan, since it was just turned on"
                        + " it will block for a few seconds and fail anyway");
            }
            else {
                MainActivity.info( "start first wifi scan");
                // starts scan, sends event when done
                final boolean scanOK = doWifiScan();
                if ( scanRequestTime <= 0 ) {
                    scanRequestTime = System.currentTimeMillis();
                }
                MainActivity.info( "startup finished. wifi scanOK: " + scanOK );
            }
        }
    }

    public long getScanPeriod() {
        final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );

        String scanPref = ListFragment.PREF_SCAN_PERIOD;
        long defaultRate = MainActivity.SCAN_DEFAULT;
        // if over 5 mph
        Location location = null;
        final GPSListener gpsListener = mainActivity.getGPSListener();
        if (gpsListener != null) {
            location = gpsListener.getLocation();
        }
        if ( location != null && location.getSpeed() >= 2.2352f ) {
            scanPref = ListFragment.PREF_SCAN_PERIOD_FAST;
            defaultRate = MainActivity.SCAN_FAST_DEFAULT;
        }
        else if ( location == null || location.getSpeed() < 0.1f ) {
            scanPref = ListFragment.PREF_SCAN_PERIOD_STILL;
            defaultRate = MainActivity.SCAN_STILL_DEFAULT;
        }
        return prefs.getLong( scanPref, defaultRate );
    }

    public void scheduleScan() {
        wifiTimer.post(new Runnable() {
            @Override
            public void run() {
                doWifiScan();
            }
        });
    }

    /**
     * only call this from a Handler
     * @return true if startScan success
     */
    private boolean doWifiScan() {
        // MainActivity.info("do wifi scan. lastScanTime: " + lastScanResponseTime);
        final WifiManager wifiManager = (WifiManager) mainActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean success = false;

        if (mainActivity.isScanning()) {
            if ( ! scanInFlight ) {
                try {
                    success = wifiManager.startScan();
                }
                catch (Exception ex) {
                    MainActivity.warn("exception starting scan: " + ex, ex);
                }
                if ( success ) {
                    scanInFlight = true;
                }
            }

            final long now = System.currentTimeMillis();
            if ( lastScanResponseTime < 0 ) {
                // use now, since we made a request
                lastScanResponseTime = now;
            } else {
                final long sinceLastScan = now - lastScanResponseTime;
                final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
                final long resetWifiPeriod = prefs.getLong(
                        ListFragment.PREF_RESET_WIFI_PERIOD, MainActivity.DEFAULT_RESET_WIFI_PERIOD );

                if ( resetWifiPeriod > 0 && sinceLastScan > resetWifiPeriod ) {
                    MainActivity.warn("Time since last scan: " + sinceLastScan + " milliseconds");
                    if ( now - lastWifiUnjamTime > resetWifiPeriod ) {
                        final boolean disableToast = prefs.getBoolean(ListFragment.PREF_DISABLE_TOAST, false);
                        if (!disableToast) {
                            Toast.makeText( mainActivity,
                                    mainActivity.getString(R.string.wifi_jammed), Toast.LENGTH_LONG ).show();
                        }
                        scanInFlight = false;
                        try {
                            wifiManager.setWifiEnabled(false);
                            wifiManager.setWifiEnabled(true);
                        }
                        catch (SecurityException ex) {
                            MainActivity.info("exception resetting wifi: " + ex, ex);
                        }
                        lastWifiUnjamTime = now;
                        if (prefs.getBoolean(ListFragment.PREF_SPEAK_WIFI_RESTART, true)) {
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
            mainActivity.setStatusUI("Scanning Turned Off" );
            // keep the scan times from getting huge
            scanRequestTime = System.currentTimeMillis();
            // reset this
            lastScanResponseTime = Long.MIN_VALUE;
        }

        // battery kill
        if ( ! mainActivity.isTransferring() ) {
            final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
            long batteryKill = prefs.getLong(
                    ListFragment.PREF_BATTERY_KILL_PERCENT, MainActivity.DEFAULT_BATTERY_KILL_PERCENT);

            if ( mainActivity.getBatteryLevelReceiver() != null ) {
                final int batteryLevel = mainActivity.getBatteryLevelReceiver().getBatteryLevel();
                final int batteryStatus = mainActivity.getBatteryLevelReceiver().getBatteryStatus();
                // MainActivity.info("batteryStatus: " + batteryStatus);
                // give some time since starting up to change this configuration
                if ( batteryKill > 0 && batteryLevel > 0 && batteryLevel <= batteryKill
                        && batteryStatus != BatteryManager.BATTERY_STATUS_CHARGING
                        && (System.currentTimeMillis() - constructionTime) > 30000L) {
                    final String text = mainActivity.getString(R.string.battery_at) + " " + batteryLevel + " "
                            + mainActivity.getString(R.string.battery_postfix);
                    Toast.makeText( mainActivity, text, Toast.LENGTH_LONG ).show();
                    MainActivity.warn("low battery, shutting down");
                    mainActivity.speak( text );
                    MainActivity.sleep(5000L);
                    mainActivity.finish();
                }
            }
        }

        return success;
    }

}
