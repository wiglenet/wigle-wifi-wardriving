package net.wigle.wigleandroid.listener;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.model.GsmOperator;
import net.wigle.wigleandroid.model.GsmOperatorException;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.FilterMatcher;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.ui.NetworkListSorter;
import net.wigle.wigleandroid.ui.UINumberFormat;
import net.wigle.wigleandroid.util.CellNetworkLegend;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.telephony.CellIdentityCdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

import com.google.android.gms.maps.model.LatLng;

/**
 * Primary receiver logic for WiFi and Cell nets.
 * Monolithic - candidate for refactor.
 * TODO: split Cell into own class
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
    private int pendingWifiCount = 0;
    private int pendingCellCount = 0;
    private final long constructionTime = System.currentTimeMillis();
    private long previousTalkTime = System.currentTimeMillis();
    private final Set<String> runNetworks = new HashSet<>();
    private long prevNewNetCount;
    private long prevScanPeriod;
    private boolean scanInFlight = false;

    public static final int CELL_MIN_STRENGTH = -113;

    public WifiReceiver( final MainActivity mainActivity, final DatabaseHelper dbHelper, final Context context ) {
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
     * @param context
     * @param intent
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
        Logging.info("wifi receive, results: " + (results == null ? null : results.size()));

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
            Logging.info("setting location updates to: " + setPeriod);
            mainActivity.setLocationUpdates(setPeriod, 0f);

            prevScanPeriod = setPeriod;
        }

        // have the gps listener to a self-check, in case it isn't getting updates anymore
        final GPSListener gpsListener = mainActivity.getGPSListener();
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

        final boolean showCurrent = prefs.getBoolean( ListFragment.PREF_SHOW_CURRENT, true );
        if ( showCurrent && listAdapter != null ) {
            listAdapter.clearWifiAndCell();
        }

        final int preQueueSize = dbHelper.getQueueSize();
        final boolean fastMode = dbHelper.isFastMode();
        final ConcurrentLinkedHashMap<String,Network> networkCache = MainActivity.getNetworkCache();
        boolean somethingAdded = false;
        int resultSize = 0;
        int newWifiForRun = 0;

        final boolean ssidSpeak = prefs.getBoolean( ListFragment.PREF_SPEAK_SSID, false )
                && ! mainActivity.isMuted();

        //TODO: should we memoize the ssidMatcher in the MainActivity state as well?
        final Matcher ssidMatcher = FilterMatcher.getSsidFilterMatcher( prefs, ListFragment.FILTER_PREF_PREFIX );
        final Matcher bssidMatcher = mainActivity.getBssidFilterMatcher( ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS );
        final Matcher bssidDbMatcher = mainActivity.getBssidFilterMatcher( ListFragment.PREF_EXCLUDE_LOG_ADDRS );

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
                    if ( FilterMatcher.isOk( ssidMatcher, bssidMatcher, prefs, ListFragment.FILTER_PREF_PREFIX, network ) ) {
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
            }
        }

        // check if there are more "New" nets
        final long newNetCount = dbHelper.getNewNetworkCount();
        final long newWifiCount = dbHelper.getNewWifiCount();
        final long newNetDiff = newWifiCount - prevNewNetCount;
        prevNewNetCount = newWifiCount;

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

        // TODO: this ties cell collection to WiFi collection - refactor cells onto their own timer
        // check cell tower info
        final int preCellForRun = runNetworks.size();
        int newCellForRun = 0;
        final Map<String,Network>cellNetworks = recordCellInfo(location);
        if ( cellNetworks != null ) {
            for (String key: cellNetworks.keySet()) {
                final Network cellNetwork = cellNetworks.get(key);
                if (cellNetwork != null) {
                    resultSize++;
                    if (showCurrent && listAdapter != null && FilterMatcher.isOk(ssidMatcher, bssidMatcher, prefs, ListFragment.FILTER_PREF_PREFIX, cellNetwork)) {
                        listAdapter.addCell(cellNetwork);
                    }
                    if (runNetworks.size() > preCellForRun) {
                        newCellForRun++;
                    }
                }
            }
        }

        // check for "New" cell towers
        final long newCellCount = dbHelper.getNewCellCount();


        if (listAdapter != null) {
            listAdapter.sort(NetworkListSorter.getSort(prefs) );
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

        if ( somethingAdded && ssidSpeak ) {
            ssidSpeaker.speak();
        }

        final long speechPeriod = prefs.getLong( ListFragment.PREF_SPEECH_PERIOD, MainActivity.DEFAULT_SPEECH_PERIOD );
        if ( speechPeriod != 0 && now - previousTalkTime > speechPeriod * 1000L ) {
            doAnnouncement( preQueueSize, newWifiCount, newCellCount, now );
        }
    }

    /**
     * trigger for cell collection and logging
     * @param location
     * @return
     */
    private Map<String,Network> recordCellInfo(final Location location) {
        TelephonyManager tele = (TelephonyManager) mainActivity.getSystemService( Context.TELEPHONY_SERVICE );
        Map<String,Network> networks = new HashMap<>();
        if ( tele != null ) {
            try {
                CellLocation currentCell = null;
                //DEBUG: MainActivity.info("SIM State: "+tele.getSimState() + "("+getNetworkTypeName()+")");
                currentCell = tele.getCellLocation();
                if (currentCell != null) {
                    Network currentNetwork = handleSingleCellLocation(currentCell, tele, location);
                    if (currentNetwork != null) {
                        networks.put(currentNetwork.getBssid(), currentNetwork);
                        ListFragment.lameStatic.currCells = 1;
                    }
                }

                if (Build.VERSION.SDK_INT >= 17) { // we can survey cells
                    List<CellInfo> infos = tele.getAllCellInfo();
                    if (null != infos) {
                        for (final CellInfo cell : infos) {
                            Network network = handleSingleCellInfo(cell, tele, location);
                            if (null != network) {
                                if (networks.containsKey(network.getBssid())) {
                                    //DEBUG: MainActivity.info("matching network already in map: " + network.getBssid());
                                    Network n = networks.get(network.getBssid());
                                    //TODO merge to improve data instead of replace?
                                    networks.put(network.getBssid(), network);
                                } else {
                                    networks.put(network.getBssid(), network);
                                }
                            }
                        }
                        ListFragment.lameStatic.currCells = infos.size();
                    }
                } else {
                    //TODO: handle multiple SIMs in early revs?
                }
                //ALIBI: haven't been able to find a circumstance where there's anything but garbage in these.
                //  should be an alternative to getAllCellInfo above for older phones, but oly dBm looks valid


                /*List<NeighboringCellInfo> list = tele.getNeighboringCellInfo();
                if (null != list) {
                    for (final NeighboringCellInfo cell : list) {
                        //networks.put(
                        handleSingleNeighboringCellInfo(cell, tele, location);
                        //);
                    }
                }*/
            } catch (SecurityException sex) {
                Logging.warn("unable to scan cells due to permission issue: ", sex);
            } catch (NullPointerException ex) {
                Logging.warn("NPE on cell scan: ", ex);
            }
        }
        return networks;
    }

    /**
     * Translate a CellInfo record to Network record / sorts by type and capabilities to correct update methods
     * (new implementation)
     * @param cellInfo
     * @param tele
     * @param location
     * @return
     */
    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
    private Network handleSingleCellInfo(final CellInfo cellInfo, final TelephonyManager tele, final Location location) {
        if (cellInfo == null) {
            Logging.info("null cellInfo");
            // ignore
        } else {
            if (MainActivity.DEBUG_CELL_DATA) {
                Logging.info("cell: " + cellInfo + " class: " + cellInfo.getClass().getCanonicalName());
            }
            GsmOperator g = null;
            try {
                switch (cellInfo.getClass().getSimpleName()) {
                    case "CellInfoCdma":
                        return handleSingleCdmaInfo(((CellInfoCdma) (cellInfo)), tele, location);
                    case "CellInfoGsm":
                        g = new GsmOperator(((CellInfoGsm) (cellInfo)).getCellIdentity());
                        CellSignalStrengthGsm cellStrengthG = ((CellInfoGsm) (cellInfo)).getCellSignalStrength();
                        return addOrUpdateCell(g.getOperatorKeyString(), g.getOperatorString(), g.getXfcn(), "GSM",
                                cellStrengthG.getDbm(), NetworkType.typeForCode("G"), location);
                    case "CellInfoLte":
                        g = new GsmOperator(((CellInfoLte) (cellInfo)).getCellIdentity());
                        CellSignalStrengthLte cellStrengthL = ((CellInfoLte) (cellInfo)).getCellSignalStrength();
                        return addOrUpdateCell(g.getOperatorKeyString(), g.getOperatorString(), g.getXfcn(), "LTE",
                                cellStrengthL.getDbm(), NetworkType.typeForCode("L"), location);
                    case "CellInfoWcdma":
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) { //WHYYYYYY?
                            g = new GsmOperator(((CellInfoWcdma) (cellInfo)).getCellIdentity());
                            CellSignalStrengthWcdma cellStrengthW = ((CellInfoWcdma) (cellInfo)).getCellSignalStrength();
                            return addOrUpdateCell(g.getOperatorKeyString(), g.getOperatorString(), g.getXfcn(), "WCDMA",
                                    cellStrengthW.getDbm(), NetworkType.typeForCode("D"), location);
                        }
                        break;
                    default:
                        Logging.warn("Unknown cell case: " + cellInfo.getClass().getSimpleName());
                        break;
                }
            } catch (GsmOperatorException gsex) {
                //MainActivity.info("skipping invalid cell data: "+gsex);
            }
        }
        return null;
    }

    /**
     * no test environment to implement this, but the handleCellInfo methods should work to complete it.
     * NeighboringCellInfos never appear in practical testing
     * @param cellInfo
     * @param tele
     * @param location
     * @return
     */
    @Deprecated
    private Network handleSingleNeighboringCellInfo(final NeighboringCellInfo cellInfo, final TelephonyManager tele, final Location location) {
        //noinspection StatementWithEmptyBody
        if (null == cellInfo) {
            // ignore
        } else {
            if (MainActivity.DEBUG_CELL_DATA) {
                Logging.info("NeighboringCellInfo:" +
                        "\n\tCID: " + cellInfo.getCid() +
                        "\n\tLAC: " + cellInfo.getLac() +
                        "\n\tType: " + cellInfo.getNetworkType() +
                        "\n\tPsc: " + cellInfo.getPsc() +
                        "\n\tRSSI: " + cellInfo.getRssi());
            }
            switch (cellInfo.getNetworkType()) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                    //TODO!!!
                    break;
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    //TODO!!!
                    break;
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    //TODO!!!
                    break;
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                    //TODO!!!
                    break;
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                    //TODO!!!
                    break;
                case TelephonyManager.NETWORK_TYPE_HSPA:
                    //TODO!!!
                    break;
                default:
                    //TODO!!!
                    break;
            }
        }
        return null; //TODO:
    }

    /**
     * Translate and categorize a CellLocation record for update and logging
     * (old/compat implementation)
     * @param cellLocation
     * @param tele
     * @param location
     * @return
     */
    private Network handleSingleCellLocation(final CellLocation cellLocation,
                                             final TelephonyManager tele, final Location location) {
        String bssid = null;
        NetworkType type = null;
        Network network = null;
        String ssid = null;

        //noinspection StatementWithEmptyBody
        if ( cellLocation == null ) {
            // ignore
        } else if ( cellLocation.getClass().getSimpleName().equals("CdmaCellLocation") ) {
            try {
                final int systemId = ((CdmaCellLocation) cellLocation).getSystemId();
                final int networkId = ((CdmaCellLocation) cellLocation).getNetworkId();
                final int baseStationId = ((CdmaCellLocation) cellLocation).getBaseStationId();
                if ( systemId > 0 && networkId >= 0 && baseStationId >= 0 ) {
                    bssid = systemId + "_" + networkId + "_" + baseStationId;
                    type = NetworkType.CDMA;
                }
                //TODO: not sure if there's anything else we can do here
                ssid = tele.getNetworkOperatorName();
            } catch ( Exception ex ) {
                Logging.error("CDMA reflection exception: " + ex);
            }
        } else if ( cellLocation instanceof GsmCellLocation ) {
            GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
            final String operatorCode = tele.getNetworkOperator();
            if ( gsmCellLocation.getLac() >= 0 && gsmCellLocation.getCid() >= 0) {
                bssid = tele.getNetworkOperator() + "_" + gsmCellLocation.getLac() + "_" + gsmCellLocation.getCid();
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        ssid = GsmOperator.getOperatorName(tele.getNetworkOperator());
                    } catch (SQLException sex) {
                        ssid = tele.getNetworkOperatorName();
                    }
                } else {
                    ssid = tele.getNetworkOperatorName();
                }
                //DEBUG: MainActivity.info("GSM Operator name: "+ ssid + " vs TM: "+ tele.getNetworkOperatorName());
                type = NetworkType.GSM;
            }
            if (operatorCode == null || operatorCode.isEmpty()) {
                return null;
            }
        } else {
            Logging.warn("Unhandled CellLocation type: "+cellLocation.getClass().getSimpleName());
        }

        if ( bssid != null ) {
            final String networkType = CellNetworkLegend.getNetworkTypeName(tele);
            final String capabilities = networkType + ";" + tele.getNetworkCountryIso();

            int strength = 0;
            PhoneState phoneState = mainActivity.getPhoneState();
            if (phoneState != null) {
                strength = phoneState.getStrength();
            }

            if ( NetworkType.GSM.equals(type) ) {
                // never seems to work well in practice
                strength = gsmDBmMagicDecoderRing( strength );
            }

            if (MainActivity.DEBUG_CELL_DATA) {
                Logging.info("bssid: " + bssid);
                Logging.info("strength: " + strength);
                Logging.info("ssid: " + ssid);
                Logging.info("capabilities: " + capabilities);
                Logging.info("networkType: " + networkType);
                Logging.info("location: " + location);
            }

            final ConcurrentLinkedHashMap<String,Network> networkCache = MainActivity.getNetworkCache();

            final boolean newForRun = runNetworks.add( bssid );

            network = networkCache.get( bssid );
            if ( network == null ) {
                network = new Network( bssid, ssid, 0, capabilities, strength, type );
                networkCache.put( network.getBssid(), network );
            } else {
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
        return network;
    }

    /**
     * This was named RSSI - but I think it's more accurately dBm. Also worth noting that ALL the
     * SignalStrength changes we've received in PhoneState for GSM networks have been resulting in
     * "99" -> -113 in every measurable case on all hardware in testing.
     * @param strength
     * @return
     */
    @Deprecated
    private int gsmDBmMagicDecoderRing( int strength ) {
        int retval;
        if ( strength == 99 ) {
            // unknown
            retval = CELL_MIN_STRENGTH;
        }
        else {
            //  0        -113 dBm or less
            //  1        -111 dBm
            //  2...30   -109... -53 dBm
            //  31        -51 dBm or greater
            //  99 not known or not detectable
            retval = strength * 2 + CELL_MIN_STRENGTH;
        }
        //DEBUG: MainActivity.info("strength: " + strength + " dBm: " + retval);
        return retval;
    }

    /**
     * Voice announcement method for scan
     * @param preQueueSize
     * @param newWifiCount
     * @param newCellCount
     * @param now
     */
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
            final String distString = UINumberFormat.metersToString(prefs, numberFormat1, mainActivity, dist, false );
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
        Logging.info( "speak: " + speak );
        if (! "".equals(speak)) {
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
     * @return
     */
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

    /**
     * Schedule the next WiFi scan
     */
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
                    Logging.warn("exception starting scan: " + ex, ex);
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
                Logging.info("startScan returned " + success + ". last response seconds ago: " + sinceLastScan/1000d);
                final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
                final long resetWifiPeriod = prefs.getLong(
                        ListFragment.PREF_RESET_WIFI_PERIOD, MainActivity.DEFAULT_RESET_WIFI_PERIOD );

                if ( resetWifiPeriod > 0 && sinceLastScan > resetWifiPeriod ) {
                    Logging.warn("Time since last scan: " + sinceLastScan + " milliseconds");
                    if ( now - lastWifiUnjamTime > resetWifiPeriod ) {
                        final boolean disableToast = prefs.getBoolean(ListFragment.PREF_DISABLE_TOAST, false);
                        if (!disableToast) {
                            WiGLEToast.showOverActivity(mainActivity, R.string.error_general, mainActivity.getString(R.string.wifi_jammed));
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
                    if (null != mainActivity) {
                        final String text = mainActivity.getString(R.string.battery_at) + " " + batteryLevel + " "
                            + mainActivity.getString(R.string.battery_postfix);
                        WiGLEToast.showOverActivity(mainActivity, R.string.error_general, text);
                        Logging.warn("low battery, shutting down");
                        mainActivity.speak(text);
                        mainActivity.finishSoon(4000L, false);
                    }
                }
            }
        }

        return success;
    }

    /**
     * CDMA entrypoint to update and logging
     * @param cellInfo
     * @param tele
     * @param location
     * @return
     */
    private Network handleSingleCdmaInfo(final CellInfoCdma cellInfo, final TelephonyManager tele, final Location location) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            CellIdentityCdma cellIdentC = cellInfo.getCellIdentity();
            CellSignalStrengthCdma cellStrengthC = ((CellInfoCdma) (cellInfo)).getCellSignalStrength();

            final int bssIdInt = cellIdentC.getBasestationId();
            final int netIdInt = cellIdentC.getNetworkId();
            final int systemIdInt = cellIdentC.getSystemId();

            if ((Integer.MAX_VALUE == bssIdInt) || (Integer.MAX_VALUE == netIdInt) || (Integer.MAX_VALUE == systemIdInt)) {
                Logging.info("Discarding CDMA cell with invalid ID");
                return null;
            }

            final String networkKey = systemIdInt + "_" + netIdInt + "_" + bssIdInt;
            final int dBmLevel = cellStrengthC.getDbm();
            if (MainActivity.DEBUG_CELL_DATA) {

                String res = "CDMA Cell:" +
                        "\n\tBSSID:" + bssIdInt +
                        "\n\tNet ID:" + netIdInt +
                        "\n\tSystem ID:" + systemIdInt +
                        "\n\tNetwork Key: " + networkKey;

                res += "\n\tLat: " + new Double(cellIdentC.getLatitude()) / 4.0d / 60.0d / 60.0d;
                res += "\n\tLon: " + new Double(cellIdentC.getLongitude()) / 4.0d / 60.0d / 60.0d;
                res += "\n\tSignal: " + cellStrengthC.getCdmaLevel();

                int rssi = cellStrengthC.getEvdoDbm() != 0 ? cellStrengthC.getEvdoDbm() : cellStrengthC.getCdmaDbm();
                res += "\n\tRSSI: " + rssi;

                final int asuLevel = cellStrengthC.getAsuLevel();

                res += "\n\tSSdBm: " + dBmLevel;
                res += "\n\tSSasu: " + asuLevel;
                res += "\n\tEVDOdBm: " + cellStrengthC.getEvdoDbm();
                res += "\n\tCDMAdBm: " + cellStrengthC.getCdmaDbm();
                Logging.info(res);
            }
            //TODO: don't see any way to get CDMA channel from current CellInfoCDMA/CellIdentityCdma
            //  references http://niviuk.free.fr/cdma_band.php
            return addOrUpdateCell(networkKey,
                    /*TODO: can we improve on this?*/ tele.getNetworkOperator(),
                    0, "CDMA", dBmLevel, NetworkType.typeForCode("C"), location);

        }
        return null;
    }

    /**
     * Cell update and logging
     * @param bssid
     * @param operator
     * @param frequency
     * @param networkTypeName
     * @param strength
     * @param type
     * @param location
     * @return
     */
    private Network addOrUpdateCell(final String bssid, final String operator,
                                    final int frequency, final String networkTypeName,
                                    final int strength, final NetworkType type,
                                    final Location location) {

        final String capabilities = networkTypeName + ";" + operator;

        final ConcurrentLinkedHashMap<String,Network> networkCache = MainActivity.getNetworkCache();
        final boolean newForRun = runNetworks.add( bssid );

        Network network = networkCache.get( bssid );

        try {
            final String operatorName = GsmOperator.getOperatorName(operator);

            if ( network == null ) {
                network = new Network( bssid, operatorName, frequency, capabilities, (Integer.MAX_VALUE == strength) ? CELL_MIN_STRENGTH : strength, type );
                networkCache.put( network.getBssid(), network );
            } else {
                network.setLevel( (Integer.MAX_VALUE == strength) ? CELL_MIN_STRENGTH : strength);
                network.setFrequency(frequency);
            }

            if ( location != null && (newForRun || network.getLatLng() == null) ) {
                // set the LatLng for mapping
                final LatLng LatLng = new LatLng( location.getLatitude(), location.getLongitude() );
                network.setLatLng( LatLng );
            }

            if ( location != null ) {
                dbHelper.addObservation(network, location, newForRun);
            }
        } catch (SQLException sex) {
            Logging.error("Error in add/update:", sex);
        }
        //ALIBI: allows us to run in conjunction with current-carrier detection
        return network;
    }

}
