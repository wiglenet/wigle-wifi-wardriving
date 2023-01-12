package net.wigle.wigleandroid.listener;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
import static android.bluetooth.le.ScanSettings.MATCH_MODE_AGGRESSIVE;
//import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY; battery drain
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.maps.model.LatLng;

import net.wigle.wigleandroid.FilterMatcher;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.ui.NetworkListSorter;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

import static net.wigle.wigleandroid.MainActivity.DEBUG_BLUETOOTH_DATA;

/**
 * Central classic BT broadcast receiver and BTLE scanner.
 * Created by bobzilla on 12/20/15
 */
public final class BluetoothReceiver extends BroadcastReceiver implements LeScanUpdater {

    // if a batch scan arrives within <x> millis of the previous batch, maybe that's too close
    // Common on Android 8.1+ devices
    // Apparently a feature used for ranging/distance
    private final static long MIN_LE_BATCH_GAP_MILLIS = 250; // ALIBI: must be lower than LE_REPORT_DELAY_MILLIS
    private final static long LE_REPORT_DELAY_MILLIS = 15000; // ALIBI: experimental - should this be settable?

    private static final Map<Integer, String> DEVICE_TYPE_LEGEND;

    //TODO: i18n
    static {
        Map<Integer, String> initMap = new HashMap<>();
        initMap.put(0, "Misc");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_CAMCORDER, "Camcorder");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO, "Car Audio");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE, "Handsfree");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES, "Headphones");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO, "HiFi");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER, "Speaker");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE, "Mic");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO, "Portable Audio");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX, "Settop");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED, "A/V");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_VCR, "VCR");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CAMERA, "Camera");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CONFERENCING, "Videoconf");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER, "Display/Speaker");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_VIDEO_GAMING_TOY, "AV Toy");
        initMap.put(BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR, "Monitor");
        initMap.put(BluetoothClass.Device.COMPUTER_DESKTOP, "Desktop");
        initMap.put(BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA, "PDA");
        initMap.put(BluetoothClass.Device.COMPUTER_LAPTOP, "Laptop");
        initMap.put(BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA, "Palm");
        initMap.put(BluetoothClass.Device.COMPUTER_SERVER, "Server");
        initMap.put(BluetoothClass.Device.COMPUTER_UNCATEGORIZED, "Computer");
        initMap.put(BluetoothClass.Device.COMPUTER_WEARABLE, "Wearable Computer");
        initMap.put(BluetoothClass.Device.HEALTH_BLOOD_PRESSURE, "Blood Pressure");
        initMap.put(BluetoothClass.Device.HEALTH_DATA_DISPLAY, "Health Display");
        initMap.put(BluetoothClass.Device.HEALTH_GLUCOSE, "Glucose");
        initMap.put(BluetoothClass.Device.HEALTH_PULSE_OXIMETER, "PulseOxy");
        initMap.put(BluetoothClass.Device.HEALTH_PULSE_RATE, "Pulse");
        initMap.put(BluetoothClass.Device.HEALTH_THERMOMETER, "Thermometer");
        initMap.put(BluetoothClass.Device.HEALTH_UNCATEGORIZED, "Health");
        initMap.put(BluetoothClass.Device.HEALTH_WEIGHING, "Scale");
        initMap.put(BluetoothClass.Device.PHONE_CELLULAR, "Cellphone");
        initMap.put(BluetoothClass.Device.PHONE_CORDLESS, "Cordless Phone");
        initMap.put(BluetoothClass.Device.PHONE_ISDN, "ISDN Phone");
        initMap.put(BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY, "Modem/GW");
        initMap.put(BluetoothClass.Device.PHONE_SMART, "Smartphone");
        initMap.put(BluetoothClass.Device.PHONE_UNCATEGORIZED, "Phone");
        initMap.put(BluetoothClass.Device.TOY_CONTROLLER, "Controller");
        initMap.put(BluetoothClass.Device.TOY_DOLL_ACTION_FIGURE, "Doll");
        initMap.put(BluetoothClass.Device.TOY_GAME, "Game");
        initMap.put(BluetoothClass.Device.TOY_ROBOT, "Robot");
        initMap.put(BluetoothClass.Device.TOY_UNCATEGORIZED, "Toy");
        initMap.put(BluetoothClass.Device.TOY_VEHICLE, "Vehicle");
        initMap.put(BluetoothClass.Device.WEARABLE_GLASSES, "Glasses");
        initMap.put(BluetoothClass.Device.WEARABLE_HELMET, "Helmet");
        initMap.put(BluetoothClass.Device.WEARABLE_JACKET, "Jacket");
        initMap.put(BluetoothClass.Device.WEARABLE_PAGER, "Pager");
        initMap.put(BluetoothClass.Device.WEARABLE_UNCATEGORIZED, "Wearable");
        initMap.put(BluetoothClass.Device.WEARABLE_WRIST_WATCH, "Watch");
        initMap.put(BluetoothClass.Device.Major.UNCATEGORIZED, "Uncategorized");

        DEVICE_TYPE_LEGEND = Collections.unmodifiableMap(initMap);
    }

    private final DatabaseHelper dbHelper;
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    //TODO: this is pretty redundant with the central network list,
    // but they all seem to be getting out of sync, which is annoying AF
    private final Set<String> unsafeRunNetworks = new HashSet<>();
    private final Set<String> runNetworks = Collections.synchronizedSet(unsafeRunNetworks);

    private WeakReference<SetNetworkListAdapter> listAdapter;
    private LeScanCallback scanCallback;
    private final SharedPreferences prefs;

    private Handler bluetoothTimer;
    private long scanRequestTime = Long.MIN_VALUE;
    private long lastScanResponseTime = Long.MIN_VALUE;

    private final long constructionTime = System.currentTimeMillis();

    // refresh threshold - probably should either make these configurable
    // arguably expiration should live per element not-seen in n scans.
    private static final int EMPTY_LE_THRESHOLD = 10;

    // scan state
    private long lastDiscoveryAt = 0;

    // prev/current sets of BSSIDs for each scan. ~ redundant w/ sets in SetBackedNetworkList in current-only mode...
    //ALIBI: both need to be synchronized since BTLE scan results can mutate/remove a BSSID from prev
    private final Set<String> latestBt = Collections.synchronizedSet(new HashSet<>());
    private Set<String> prevBt = Collections.synchronizedSet(new HashSet<>());

    private final Set<String> latestBtle = Collections.synchronizedSet(new HashSet<>());

    public BluetoothReceiver(final DatabaseHelper dbHelper, final boolean hasLeSupport, final SharedPreferences prefs) {
        this.dbHelper = dbHelper;
        ListFragment.lameStatic.runBtNetworks = runNetworks;
        this.prefs = prefs;

        if (hasLeSupport) {
            //ALIBI: seeing same-count (redundant) batch returns in rapid succession triggering pointless churn
            AtomicInteger btLeEmpties = new AtomicInteger(0);
            scanCallback = new LeScanCallback(dbHelper, prefs, runNetworks, latestBtle,
                    latestBt, new AtomicLong(System.currentTimeMillis()), scanning, this, btLeEmpties);
        } else {
            scanCallback = null;
        }
    }

    public void handleLeScanResult(final ScanResult scanResult, Location location, final boolean batch) {
        //DEBUG: MainActivity.info("LE scanResult: " + scanResult);
        try {
            final ScanRecord scanRecord = scanResult.getScanRecord();
            if (scanRecord != null) {
                final BluetoothDevice device = scanResult.getDevice();
                //BluetoothUtil.BleAdvertisedData adData = BluetoothUtil.parseAdvertisedData(scanRecord.getBytes());
                //final String adDeviceName = (adData != null) ? adData.getName(): null;

                final String bssid = device.getAddress();

                latestBtle.add(bssid);
                prevBt.remove(bssid);
                latestBt.remove(bssid);

                final String ssid =
                        (null == scanRecord.getDeviceName() || scanRecord.getDeviceName().isEmpty())
                                ? device.getName()
                                : scanRecord.getDeviceName();

                // This is questionable - of Major class being known when specific class seems thin
                final BluetoothClass bluetoothClass = device.getBluetoothClass();
                int type = BluetoothClass.Device.Major.UNCATEGORIZED;
                if (bluetoothClass != null) {
                    final int deviceClass = bluetoothClass.getDeviceClass();
                    type = (deviceClass == 0 || deviceClass == BluetoothClass.Device.Major.UNCATEGORIZED)
                            ? bluetoothClass.getMajorDeviceClass()
                            : deviceClass;
                }

                if (DEBUG_BLUETOOTH_DATA) {
                    Logging.info("LE deviceName: " + ssid
                                    + "\n\taddress: " + bssid
                                    + "\n\tname: " + scanRecord.getDeviceName() + " (vs. " + device.getName() + ")"
                                    //+ "\n\tadName: " + adDeviceName
                                    + "\n\tclass:"
                                    + (bluetoothClass == null ? null : DEVICE_TYPE_LEGEND.get(bluetoothClass.getDeviceClass()))
                                    + "(" + bluetoothClass + ")"
                                    + "\n\ttype:" + device.getType()
                                    + "\n\tRSSI:" + scanResult.getRssi()
                            //+ "\n\tTX power:" + scanRecord.getTxPowerLevel() //THIS IS ALWAYS GARBAGE
                            //+ "\n\tbytes: " + Arrays.toString(scanRecord.getBytes())
                    );
                }

                final String capabilities = DEVICE_TYPE_LEGEND.get(
                        bluetoothClass == null ? null : bluetoothClass.getDeviceClass());
                if (MainActivity.getMainActivity() != null) {
                    //ALIBI: shamelessly re-using frequency here for device type.
                    addOrUpdateBt(bssid, ssid, type, capabilities,
                            scanResult.getRssi(),
                            NetworkType.BLE, location, prefs, batch);
                }
            }
        } catch (SecurityException se) {
            Logging.warn("failing to perform BTLE scans: BT perms not granted", se);
        }
    }

    /**
     * initiate a bluetooth scan, if discovery is not currently in-progress (callbacks via onReceive)
     */
    public void bluetoothScan() {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            return;
        }

        // classic BT scan - basically "Always Be Discovering" times between discovery runs will be MAX(wifi delay) since this is called from wifi receiver
        try {
            if (!bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.startDiscovery();
                lastDiscoveryAt = System.currentTimeMillis();
            } else {
                if (DEBUG_BLUETOOTH_DATA) {
                    Logging.info("skipping bluetooth scan; discover already in progress (last scan started " + (System.currentTimeMillis() - lastDiscoveryAt) + "ms ago)");
                }
            }
        } catch (SecurityException se) {
            Logging.error("No permission for bluetoothAdapter.startDiscovery/isDiscovering", se);
        }
        final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Logging.info("bluetoothLeScanner is null");
        } else {
            if (scanning.compareAndSet(false, true)) {
                try {
                    Logging.info("START BLE SCANs");
                    bluetoothLeScanner.startScan(Collections.emptyList(),
                            new ScanSettings.Builder()
                                .setCallbackType(CALLBACK_TYPE_ALL_MATCHES)
                                .setMatchMode(MATCH_MODE_AGGRESSIVE)
                                .setReportDelay(LE_REPORT_DELAY_MILLIS)
                                .setScanMode(SCAN_MODE_LOW_POWER).build(),
                            scanCallback);
                } catch (SecurityException se) {
                    Logging.error("No permission for bluetoothScanner.startScan", se);
                }
            } else {
                //ALIBI: tried a no-op here, but not the source of the pairs of batch callbacks
                //DEBUG: MainActivity.error("FLUSH BLE SCANs");
                try {
                    bluetoothLeScanner.flushPendingScanResults(scanCallback);
                } catch (SecurityException se) {
                    Logging.error("No permission for bluetoothScanner.flushPendingScanResults", se);
                }
            }
        }

        /*
        Paired device check? could exclude paired devices...
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : pairedDevices) {
            MainActivity.info("\tpareid device: "+device.getAddress()+" - "+device.getName() + device.getBluetoothClass());
            //BluetoothClass bluetoothClass = device.getBluetoothClass();
        }*/
    }

    public void stopScanning() {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (SecurityException se) {
                Logging.error("No permission for bluetoothAdapter.cancelDiscovery", se);
            }
            final boolean showCurrent = prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true);
            if (listAdapter != null && showCurrent) {
                SetNetworkListAdapter l = listAdapter.get();
                if (null != l) {
                    l.clearBluetoothLe();
                    l.clearBluetooth();
                }
            }


            final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner != null) {
                if (scanning.compareAndSet(true, false)) {
                    Logging.info("STOPPING BLE SCANS");
                    try {
                        bluetoothLeScanner.stopScan(scanCallback);
                    } catch (SecurityException se) {
                        Logging.error("No permission for bluetoothAdapter.stopScan", se);
                    }

                } else {
                    Logging.error("Scanner present, comp-and-set prevented stop-scan");
                }
            }
        }
    }

    public void close() {
        this.listAdapter = null;
        this.scanCallback = null;
    }

    /**
     * General Bluetooth on-receive callback. Can register a BC or BLE network, although provides no means for distinguishing between them.
     */
    @Override
    public void onReceive(final Context context, final Intent intent) {
        final MainActivity m = MainActivity.getMainActivity();
        if (m != null) {
            if (null == intent) {
                Logging.error("null intent in Bluetooth onReceive");
                return;
            }
            final String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null) {
                    // as reported in bug feed
                    Logging.error("onReceive with null device - discarding this instance");
                    return;
                }
                latestBt.add(device.getAddress());
                final BluetoothClass btClass = intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                final String bssid = device.getAddress();
                try {
                    final String ssid = device.getName();

                    int type;

                    if (btClass == null) {
                        type = (isMiscOrUncategorized(device.getBluetoothClass().getDeviceClass())) ?
                                device.getBluetoothClass().getMajorDeviceClass() : device.getBluetoothClass().getDeviceClass();
                    } else {
                        type = btClass.getDeviceClass();
                    }
                    if (DEBUG_BLUETOOTH_DATA) {
                        String log = "BT deviceName: " + device.getName()
                                + "\n\taddress: " + bssid
                                + "\n\tname: " + ssid
                                + "\n\tRSSI dBM: " + rssi
                                + "\n\tclass: " + DEVICE_TYPE_LEGEND.get(type)
                                + "(" + type + ")"
                                + "\n\tbondState: " + device.getBondState();
                        log += "\n\tuuids: " + Arrays.toString(device.getUuids());

                        Logging.info(log);
                    }
                    final String capabilities = DEVICE_TYPE_LEGEND.get(type)
                        /*+ " (" + device.getBluetoothClass().getMajorDeviceClass()
                        + ":" +device.getBluetoothClass().getDeviceClass() + ")"*/
                            + ";" + device.getBondState();
                    final GNSSListener gpsListener = m.getGPSListener();

                    Location location = null;
                    if (gpsListener != null) {
                        final long gpsTimeout = prefs.getLong(PreferenceKeys.PREF_GPS_TIMEOUT, GNSSListener.GPS_TIMEOUT_DEFAULT);
                        final long netLocTimeout = prefs.getLong(PreferenceKeys.PREF_NET_LOC_TIMEOUT, GNSSListener.NET_LOC_TIMEOUT_DEFAULT);
                        gpsListener.checkLocationOK(gpsTimeout, netLocTimeout);
                        location = gpsListener.getCurrentLocation();
                    } else {
                        Logging.warn("null gpsListener in BTR onReceive");
                    }

                    //ALIBI: shamelessly re-using frequency here for device type.
                    final Network network = addOrUpdateBt(bssid, ssid, type, capabilities, rssi, NetworkType.BT, location, prefs, false);
                    if (listAdapter != null) {
                        SetNetworkListAdapter l = listAdapter.get();
                        if (null != l) {
                            sort(prefs, l);
                            l.notifyDataSetChanged();
                        }
                    }
                } catch (SecurityException se) {
                    Logging.error("No permission for device inspection", se);
                }

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                //DEBUG: MainActivity.error("Previous BT "+prevBt.size()+ " Latest BT "+latestBt.size());
                prevBt = Collections.synchronizedSet(new HashSet(latestBt));
                latestBt.clear();

                ListFragment.lameStatic.currBt = ((null != scanCallback) ? scanCallback.getPrevBtLeSize() : (0 + prevBt.size()));

                final boolean showCurrent = prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true);
                if (listAdapter != null) {
                    SetNetworkListAdapter l = listAdapter.get();
                    if (null != l) {
                        l.batchUpdateBt(showCurrent, false, true);
                        sort(prefs, l);
                        l.notifyDataSetChanged();
                    }
                }
                ListFragment.lameStatic.newBt = dbHelper.getNewBtCount();
                ListFragment.lameStatic.runBt = runNetworks.size();
            }
        }
    }

    /**
     * TODO: DRY this up with the sort in WifiReceiver?
     */
    private static void sort(final SharedPreferences prefs, final SetNetworkListAdapter listAdapter) {
        if (listAdapter != null) {
            try {
                listAdapter.sort(NetworkListSorter.getSort(prefs));
            } catch (IllegalArgumentException ex) {
                Logging.error("sort failed: ",ex);
            }
        }
    }

    /**
     * get the number of BT networks seen this run
     */
    public int getRunNetworkCount() {
        return runNetworks.size();
    }

    /**
     * create the bluetooth timer thread
     */
    public void setupBluetoothTimer( final boolean turnedBtOn ) {
        Logging.info( "create Bluetooth timer" );
        final MainActivity m = MainActivity.getMainActivity();
        if ( bluetoothTimer == null) {
            bluetoothTimer = new Handler();
            final Runnable mUpdateTimeTask = new Runnable() {
                @Override
                public void run() {
                    // make sure the app isn't trying to finish
                    if ( null != m && !m.isFinishing() ) {
                        // info( "timer start scan" );
                        // schedule a bluetooth scan
                        doBluetoothScan();
                        if ( scanRequestTime <= 0 ) {
                            scanRequestTime = System.currentTimeMillis();
                        }
                        long period = getScanPeriod();
                        // check if set to "continuous"
                        if ( period == 0L ) {
                            // set to default here, as a scan will also be requested on the scan result listener
                            period = MainActivity.SCAN_DEFAULT;
                        }
                        // info("bluetoothtimer: " + period );
                        bluetoothTimer.postDelayed( this, period );
                    }
                    else {
                        Logging.info( "finishing timer" );
                    }
                }
            };
            bluetoothTimer.removeCallbacks( mUpdateTimeTask );
            bluetoothTimer.postDelayed( mUpdateTimeTask, 100 );

            if ( turnedBtOn ) {
                Logging.info( "not immediately running BT scan, since it was just turned on"
                        + " it will block for a few seconds and fail anyway");
            }
            else {
                Logging.info( "start first bluetooth scan");
                // starts scan, sends event when done
                final boolean scanOK = doBluetoothScan();

                if ( scanRequestTime <= 0 ) {
                    scanRequestTime = System.currentTimeMillis();
                }
                Logging.info( "startup finished. BT scanOK: " + scanOK );
            }
        }
    }

    public boolean doBluetoothScan() {
        boolean success = false;
        final MainActivity m = MainActivity.getMainActivity();
        if (null != m) {
            if (m.isScanning()) {
                boolean scanInFlight = false;
                if (!scanInFlight) {
                    try {
                        m.bluetoothScan();
                    } catch (Exception ex) {
                        Logging.warn("exception starting bt scan: " + ex, ex);
                    }
                }

                final long now = System.currentTimeMillis();
                if (lastScanResponseTime < 0) {
                    // use now, since we made a request
                    lastScanResponseTime = now;
                }
            } else {
                // scanning is off. since we're the only timer, update the UI
                m.setNetCountUI();
                m.setLocationUI();
                m.setStatusUI("Scanning Turned Off");
                // keep the scan times from getting huge
                scanRequestTime = System.currentTimeMillis();
                // reset this
                lastScanResponseTime = Long.MIN_VALUE;
            }
            // battery kill
            if ( ! m.isTransferring() ) {
                long batteryKill = prefs.getLong(
                        PreferenceKeys.PREF_BATTERY_KILL_PERCENT, MainActivity.DEFAULT_BATTERY_KILL_PERCENT);

                if ( m.getBatteryLevelReceiver() != null ) {
                    final int batteryLevel = m.getBatteryLevelReceiver().getBatteryLevel();
                    final int batteryStatus = m.getBatteryLevelReceiver().getBatteryStatus();
                    // give some time since starting up to change this configuration
                    if ( batteryKill > 0 && batteryLevel > 0 && batteryLevel <= batteryKill
                        && batteryStatus != BatteryManager.BATTERY_STATUS_CHARGING
                        && (System.currentTimeMillis() - constructionTime) > 30000L) {
                        final String text = m.getString(R.string.battery_at) + " " + batteryLevel + " "
                                + m.getString(R.string.battery_postfix);
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(() -> WiGLEToast.showOverActivity(m, R.string.error_general, text));
                        Logging.warn("low battery, shutting down");
                        m.speak(text);
                        m.finishSoon(4000L, false);
                    }
                }
            }
        }
        return success;
    }

    public long getScanPeriod() {
        final MainActivity m = MainActivity.getMainActivity();
        if (null != m) {
            String scanPref = PreferenceKeys.PREF_OG_BT_SCAN_PERIOD;
            long defaultRate = MainActivity.OG_BT_SCAN_DEFAULT;
            // if over 5 mph
            Location location = null;
            final GNSSListener gpsListener = m.getGPSListener();
            if (gpsListener != null) {
                location = gpsListener.getCurrentLocation();
            }
            if (location != null && location.getSpeed() >= 2.2352f) {
                scanPref = PreferenceKeys.PREF_OG_BT_SCAN_PERIOD_FAST;
                defaultRate = MainActivity.OG_BT_SCAN_FAST_DEFAULT;
            } else if (location == null || location.getSpeed() < 0.1f) {
                scanPref = PreferenceKeys.PREF_OG_BT_SCAN_PERIOD_STILL;
                defaultRate = MainActivity.OG_BT_SCAN_STILL_DEFAULT;
            }
            return prefs.getLong(scanPref, defaultRate);
        }
        return 0L;
    }

    private Network addOrUpdateBt(final String bssid, final String ssid,
                                    final int frequency, /*final String networkTypeName*/final String capabilities,
                                    final int strength, final NetworkType type,
                                    final Location location, SharedPreferences prefs, final boolean batch) {

        //final String capabilities = networkTypeName + ";" + operator;

        final ConcurrentLinkedHashMap<String, Network> networkCache = MainActivity.getNetworkCache();
        final boolean showCurrent = prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true);

        //ALIBI: addressing synchronization issues: if runNetworks syncset did not already contain this bssid
        //  AND the global ConcurrentLinkedHashMap network cache doesn't contain this key
        final boolean newForRun = runNetworks.add(bssid) && !networkCache.containsKey(bssid);

        Network network = networkCache.get(bssid);

        if (newForRun && network != null) {
            //ALIBI: sanity check used in debugging
            Logging.warn("runNetworks not working as expected (add -> true, but networkCache already contained)");
        }

        boolean deviceTypeUpdate = false;
        boolean btTypeUpdate = false;
        if (network == null) {
            //DEBUG: MainActivity.info("new BT net: "+bssid + "(new: "+newForRun+")");
            network = new Network(bssid, ssid, frequency, capabilities, strength, type);
            networkCache.put(bssid, network);
        } else if (NetworkType.BLE.equals(type) && NetworkType.BT.equals(network.getType())) {
            //ALIBI: detected via standard bluetooth, updated as LE (LE should win)
            //DEBUG: MainActivity.info("had a BC record, moving to BLE: "+network.getBssid()+ "(new: "+newForRun+")");
            String mergedSsid = (ssid == null || ssid.isEmpty()) ? network.getSsid() : ssid;
            int mergedDeviceType = (!isMiscOrUncategorized(network.getFrequency())?network.getFrequency():frequency);

            network.setSsid(mergedSsid);
            final int oldDevType = network.getFrequency();
            if (mergedDeviceType != oldDevType) {
                deviceTypeUpdate = true;
            }
            btTypeUpdate = true;
            network.setFrequency(mergedDeviceType);
            network.setLevel(strength);
            network.setType(NetworkType.BLE);
        } else if (NetworkType.BT.equals(type) && NetworkType.BLE.equals(network.getType())) {
            //fill in device type if not present
            //DEBUG: MainActivity.info("had a BLE record, got BC: "+network.getBssid() + "(new: "+newForRun+")");
            int mergedDeviceType = (!isMiscOrUncategorized(network.getFrequency())?network.getFrequency():frequency);
            final int oldDevType = network.getFrequency();
            if (mergedDeviceType != oldDevType) {
                deviceTypeUpdate = true;
            }
            network.setFrequency(mergedDeviceType);
            network.setLevel(strength);

            //fill in name if not present
            String mergedSsid = (ssid == null || ssid.isEmpty()) ? network.getSsid() : ssid;
            network.setSsid(mergedSsid);
        } else {
            //DEBUG: MainActivity.info("existing BT net: "+network.getBssid() + "(new: "+newForRun+")");
            //TODO: update capabilities? only if was Misc/Uncategorized, now recognized?
            //network.setCapabilities(capabilities);
            network.setLevel(strength);
            network.setFrequency(frequency);
            if (null != ssid) {
                network.setSsid(ssid);
            }
        }

        final MainActivity m = MainActivity.getMainActivity();
        final boolean ssidSpeak = prefs.getBoolean(PreferenceKeys.PREF_SPEAK_SSID, false)
                && null != m && !m.isMuted();

        if (newForRun) {
            // ALIBI: There are simply a lot of these - not sure this is practical
            /*if ( ssidSpeak ) {
                ssidSpeaker.add( network.getSsid() );
            }*/
        }
        //TODO: somethingAdded |= added;

        if ( location != null && (newForRun || network.getLatLng() == null) ) {
            // set the LatLng for mapping
            final LatLng LatLng = new LatLng( location.getLatitude(), location.getLongitude() );
            network.setLatLng( LatLng );
        }


        final Matcher ssidMatcher = FilterMatcher.getSsidFilterMatcher( prefs, PreferenceKeys.FILTER_PREF_PREFIX );
        if (null != m) {
            final Matcher bssidMatcher = m.getBssidFilterMatcher(PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS);
            final Matcher bssidDbMatcher = m.getBssidFilterMatcher(PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS);
            //Update display
            if (listAdapter != null) {
                if (btTypeUpdate) {
                    listAdapter.get().morphBluetoothToLe(network);
                }
                if (showCurrent || newForRun) {
                    if (FilterMatcher.isOk(ssidMatcher, bssidMatcher, prefs, PreferenceKeys.FILTER_PREF_PREFIX, network)) {
                        if (batch) {
                            if (NetworkType.BT.equals(network.getType())) {
                                listAdapter.get().enqueueBluetooth(network);
                            } else if (NetworkType.BLE.equals(network.getType())) {
                                listAdapter.get().enqueueBluetoothLe(network);
                            }
                        } else {
                            if (NetworkType.BT.equals(network.getType())) {
                                listAdapter.get().addBluetooth(network);
                            } else if (NetworkType.BLE.equals(network.getType())) {
                                listAdapter.get().addBluetoothLe(network);
                            }
                        }
                    }

                } else {
                    network.setLevel(strength != Integer.MAX_VALUE ? strength : -113);
                }
            }
            //Store to DB
            boolean matches = false;
            if (bssidDbMatcher != null) {
                bssidDbMatcher.reset(network.getBssid());
                matches = bssidDbMatcher.find();
            }
            if ( location != null ) {
                // w/ location
                if (!matches) {
                    dbHelper.addObservation(network, location, newForRun, deviceTypeUpdate, btTypeUpdate);
                }
            } else {
                // bob asks "since BT are often indoors, should we be saving regardless of loc?"
                // w/out location
                if (!matches) {
                    dbHelper.pendingObservation(network, newForRun, deviceTypeUpdate, btTypeUpdate);
                }
            }
        }
        return network;
    }

    // check standard BT types undefined
    private static boolean isMiscOrUncategorized(final int type) {
        return type == 0 || type == 7936;
    }

    public void setListAdapter(SetNetworkListAdapter listAdapter) {
        this.listAdapter = new WeakReference<>(listAdapter);
        if (scanCallback != null) {
            scanCallback.setListAdapter(listAdapter);
        }
    }

    private static final class LeScanCallback extends ScanCallback {
        private final DatabaseHelper dbHelper;
        private WeakReference<SetNetworkListAdapter> listAdapter;
        private final SharedPreferences prefs;
        private final Set<String> runNetworks;
        private final Set<String> latestBtle;
        private final Set<String> latestBt;
        private Set<String> prevBtle;
        private final AtomicLong lastLeBatchResponseTime;
        private final AtomicBoolean scanning;
        private final WeakReference<LeScanUpdater> updater;
        private final AtomicInteger empties;

        public LeScanCallback(DatabaseHelper dbHelper,
                              SharedPreferences prefs,
                              Set<String> runNetworks, Set<String> latestBtle,
                              Set<String> latestBt, AtomicLong lastLeBatchResponseTime,
                              AtomicBoolean scanning, LeScanUpdater updater, final AtomicInteger empties) {
            this.dbHelper = dbHelper;
            this.prefs = prefs;
            this.runNetworks = runNetworks;
            this.latestBtle = latestBtle;
            this.latestBt = latestBt;
            this.lastLeBatchResponseTime = lastLeBatchResponseTime;
            this.scanning = scanning;
            this.updater = new WeakReference<>(updater);
            this.empties = empties;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult scanResult) {
            final MainActivity m = MainActivity.getMainActivity();
            Location location = null;
            if (m != null) {
                final GNSSListener gpsListener = m.getGPSListener();
                //DEBUG: Logging.info("LE scanResult: " + scanResult + " callbackType: " + callbackType);
                if (gpsListener != null) {
                    final long gpsTimeout = prefs.getLong(PreferenceKeys.PREF_GPS_TIMEOUT, GNSSListener.GPS_TIMEOUT_DEFAULT);
                    final long netLocTimeout = prefs.getLong(PreferenceKeys.PREF_NET_LOC_TIMEOUT, GNSSListener.NET_LOC_TIMEOUT_DEFAULT);
                    gpsListener.checkLocationOK(gpsTimeout, netLocTimeout);
                    location = gpsListener.getCurrentLocation();
                } else {
                    Logging.warn("Null gpsListener in LE Single Scan Result");
                }
            }

            final LeScanUpdater updt = updater.get();
            if (null != updt) {
                updt.handleLeScanResult(scanResult, location, false);
            }
            ListFragment.lameStatic.newBt = dbHelper.getNewBtCount();
            ListFragment.lameStatic.runBt = runNetworks.size();
            if (listAdapter != null) {
                final SetNetworkListAdapter l = listAdapter.get();
                if (null != l) {
                    sort(prefs, l);
                    l.notifyDataSetChanged();
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            long responseClockTime = System.currentTimeMillis();
            long diff = responseClockTime - lastLeBatchResponseTime.longValue();
            lastLeBatchResponseTime.set(responseClockTime);
            if (diff < MIN_LE_BATCH_GAP_MILLIS) {
                Logging.info("Tried to update BTLE batch in improbably short time: " + diff + " ("+results.size()+" results)");
                return;
            }
            //DEBUG: Logging.error("LE Batch results: " + results.size());
            final MainActivity m = MainActivity.getMainActivity();
            Location location = null;
            if (m != null) {
                final GNSSListener gpsListener = m.getGPSListener();
                if (gpsListener != null) {
                    location = gpsListener.checkGetLocation(prefs);
                } else {
                    Logging.warn("Null gpsListener in LE Batch Scan Result");
                }
            }
            if (results.isEmpty()) {
                final int emptyCnt = empties.addAndGet(1);
                //DEBUG: Logging.info("empty scan result ("+empties+"/"+EMPTY_LE_THRESHOLD+")");
                //ALIBI: if it's been too long with no nets seen, we'll force-clear
                if (EMPTY_LE_THRESHOLD < emptyCnt) {
                    if ((listAdapter != null) && prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true)) {
                        final SetNetworkListAdapter l = listAdapter.get();
                        if (null != l) {
                            l.clearBluetoothLe();
                        } else {
                            Logging.error("Failed to clear BLE due to null listAdapter de-weak-ref");
                        }
                    }
                    empties.set(0);
                    prevBtle = new HashSet<>(latestBtle);
                    latestBtle.clear();
                }
                //ALIBI: if this was an empty scan result, not further processing is required.
                return;
            } else {
                empties.set(0);
            }

            final LeScanUpdater updt = updater.get();
            if (null != updt) {
                for (final ScanResult scanResult : results) {
                    updt.handleLeScanResult(scanResult, location, true);
                }
            }
            //DEBUG: Logging.error("Previous BTLE: "+prevBtle.size()+ " Latest BTLE: "+latestBtle.size());
            prevBtle = new HashSet<>(latestBtle);
            latestBtle.clear();

            ListFragment.lameStatic.currBt = prevBtle.size() + latestBt.size();
            ListFragment.lameStatic.newBt = dbHelper.getNewBtCount();
            ListFragment.lameStatic.runBt = runNetworks.size();
            if (listAdapter != null) {
                final SetNetworkListAdapter l = listAdapter.get();
                if (null != l) {
                    l.batchUpdateBt(prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true),
                            true, false);
                    sort(prefs, l);
                    l.notifyDataSetChanged();
                } else {
                    Logging.error("Null set network list adapter in updateLe ScanCallback");
                }
            } else {
                Logging.error("Null set network list adapter weakref in updateLe ScanCallback");
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED:
                    Logging.info("BluetoothLE Scan already started");
                    break;
                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    Logging.info("BluetoothLE Scan: failed: " +
                            (errorCode == SCAN_FAILED_FEATURE_UNSUPPORTED?"Scanning not supported":"Unable to register"));
                    if ((listAdapter != null) && prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true)) {
                        final SetNetworkListAdapter l = listAdapter.get();
                        if (null != l) {
                            l.clearBluetoothLe();
                        }
                    }
                    break;
                default:
                    //ALIBI: catch-all - as of API 33, this subsumes:
                    //   SCAN_FAILED_OUT_OF_HARDWARE_RESOURCESl SCAN_FAILED_SCANNING_TOO_FREQUENTLY
                    if ((listAdapter != null) && prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true)) {
                        final SetNetworkListAdapter l = listAdapter.get();
                        if (null != l) {
                            l.clearBluetoothLe();
                        }
                    }
                    Logging.error("Bluetooth LE scan error: " + errorCode);
                    scanning.set(false);
            }
        }

        public int getPrevBtLeSize() {
            if (null != prevBtle)  return prevBtle.size();
            return 0;
        }

        public void setListAdapter(SetNetworkListAdapter listAdapter) {
            this.listAdapter = new WeakReference<>(listAdapter);
        }
    }
}
