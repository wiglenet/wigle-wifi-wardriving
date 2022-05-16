package net.wigle.wigleandroid.listener;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;

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

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

import static net.wigle.wigleandroid.MainActivity.DEBUG_BLUETOOTH_DATA;

/**
 * Central classic BT broadcast receiver and BTLE scanner.
 * Created by bobzilla on 12/20/15
 */
public final class BluetoothReceiver extends BroadcastReceiver {

    // if a batch scan arrives within <x> millis of the previous batch, maybe that's too close
    // currently seeing double-callbacks on motorola+8.1 devices
    private final static long MIN_LE_BATCH_GAP = 50;

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

    private final WeakReference<MainActivity> mainActivity;
    private final DatabaseHelper dbHelper;
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    //TODO: this is pretty redundant with the central network list,
    // but they all seem to be getting out of sync, which is annoying AF
    private final Set<String> unsafeRunNetworks = new HashSet<>();
    private final Set<String> runNetworks = Collections.synchronizedSet(unsafeRunNetworks);

    private SetNetworkListAdapter listAdapter;
    private final ScanCallback scanCallback;

    private Handler bluetoothTimer;
    private long scanRequestTime = Long.MIN_VALUE;
    private boolean scanInFlight = false;
    private long lastScanResponseTime = Long.MIN_VALUE;

    //ALIBI: seeing same-count (redundant) batch returns in rapid succession triggering pointless churn
    private AtomicLong lastLeBatchResponseTime = new AtomicLong(Long.MIN_VALUE);
    private final long constructionTime = System.currentTimeMillis();

    // refresh threshold - probably should either make these configurable
    // arguably expiration should live per element not-seen in n scans.
    private static final int EMPTY_LE_THRESHOLD = 10;

    // scan state
    private long lastDiscoveryAt = 0;

    private long adUuidNoScanUuid = 0;
    private long scanUuidNoAdUuid = 0;

    // prev/current sets of BSSIDs for each scan. ~ redundant w/ sets in SetBackedNetworkList in current-only mode...
    //ALIBI: both need to be synchronized since BTLE scan results can mutate/remove a BSSID from prev
    private Set<String> latestBt = Collections.synchronizedSet(new HashSet<String>());
    private Set<String> prevBt = Collections.synchronizedSet(new HashSet<String>());

    //ALIBI: only current synchronized since prev only ever gets copied and counted
    private Set<String> latestBtle = Collections.synchronizedSet(new HashSet<String>());
    private Set<String> prevBtle = new HashSet<>();

    public BluetoothReceiver(final MainActivity mainActivity, final DatabaseHelper dbHelper, final boolean hasLeSupport) {
        this.mainActivity = new WeakReference<>(mainActivity);
        this.dbHelper = dbHelper;
        ListFragment.lameStatic.runBtNetworks = runNetworks;

        if (Build.VERSION.SDK_INT >= 21 && hasLeSupport) {
            scanCallback = new ScanCallback() {
                final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
                private int empties = 0;

                @Override
                public void onScanResult(int callbackType, ScanResult scanResult) {
                    final GPSListener gpsListener = mainActivity.getGPSListener();
                    //DEBUG:
                    Logging.info("LE scanResult: " + scanResult + " callbackType: " + callbackType);
                    Location location = null;
                    if (gpsListener != null) {
                        final long gpsTimeout = prefs.getLong(ListFragment.PREF_GPS_TIMEOUT, GPSListener.GPS_TIMEOUT_DEFAULT);
                        final long netLocTimeout = prefs.getLong(ListFragment.PREF_NET_LOC_TIMEOUT, GPSListener.NET_LOC_TIMEOUT_DEFAULT);
                        gpsListener.checkLocationOK(gpsTimeout, netLocTimeout);
                        location = gpsListener.getLocation();
                    } else {
                        Logging.warn("Null gpsListener in LE Single Scan Result");
                    }

                    handleLeScanResult(scanResult, location, false);
                    ListFragment.lameStatic.newBt = dbHelper.getNewBtCount();
                    ListFragment.lameStatic.runBt = runNetworks.size();
                    sort(prefs);
                    if (listAdapter != null) listAdapter.notifyDataSetChanged();
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    long responseClockTime = System.currentTimeMillis();
                    long diff = responseClockTime - lastLeBatchResponseTime.longValue();
                    lastLeBatchResponseTime.set(responseClockTime);
                    if (diff < MIN_LE_BATCH_GAP) {
                        Logging.warn("Tried to update BTLE batch in improbably short time: "+diff);
                        return;
                    }
                    //MainActivity.info("LE Batch results: " + results);
                    final GPSListener gpsListener = mainActivity.getGPSListener();

                    Location location = null;

                    boolean forceLeListReset = false;
                    if (results.isEmpty()) {
                        empties++;
                        //DEBUG: MainActivity.info("empty scan result ("+empties+"/"+EMPTY_LE_THRESHOLD+")");
                        //ALIBI: if it's been too long with no nets seen, we'll force-clear
                        if (EMPTY_LE_THRESHOLD < empties) {
                            forceLeListReset = true;
                            empties = 0;
                            prevBtle = new HashSet<>(latestBtle);
                            latestBtle.clear();
                        }
                    } else {
                        empties = 0;
                    }

                    if ((listAdapter != null) && prefs.getBoolean( ListFragment.PREF_SHOW_CURRENT, true ) && forceLeListReset ) {
                        listAdapter.clearBluetoothLe();
                    }

                    if (results.isEmpty()) {
                        //ALIBI: if this was an empty scan result, not further processing is required.
                        return;
                    }

                    if (gpsListener != null) {
                        location = gpsListener.checkGetLocation(prefs);
                    } else {
                        Logging.warn("Null gpsListener in LE Batch Scan Result");
                    }

                    for (final ScanResult scanResult : results) {
                        handleLeScanResult(scanResult, location, true);
                    }
                    //DEBUG: MainActivity.error("Previous BTLE: "+prevBtle.size()+ " Latest BTLE: "+latestBtle.size());
                    prevBtle = new HashSet<>(latestBtle);
                    latestBtle.clear();

                    ListFragment.lameStatic.currBt = prevBtle.size() + latestBt.size();

                    if (listAdapter != null) {
                        listAdapter.batchUpdateBt(prefs.getBoolean(ListFragment.PREF_SHOW_CURRENT, true),
                                true, false);
                    }
                    ListFragment.lameStatic.newBt = dbHelper.getNewBtCount();
                    ListFragment.lameStatic.runBt = runNetworks.size();
                    sort(prefs);
                    if (listAdapter != null) listAdapter.notifyDataSetChanged();
                }

                @Override
                public void onScanFailed(int errorCode) {
                    switch (errorCode) {
                        case SCAN_FAILED_ALREADY_STARTED:
                            Logging.info("BluetoothLE Scan already started");
                            break;
                        default:
                            if ((listAdapter != null) && prefs.getBoolean( ListFragment.PREF_SHOW_CURRENT, true ) ) {
                                listAdapter.clearBluetoothLe();
                            }
                            Logging.error("Bluetooth LE scan error: " + errorCode);
                            scanning.set(false);
                    }
                }
            };
        } else {
            scanCallback = null;
        }
    }

    private void handleLeScanResult(final ScanResult scanResult, Location location, final boolean batch) {
        if (Build.VERSION.SDK_INT >= 21) {
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


                        /*final int scanCount = ((scanRecord != null) && (scanRecord.getServiceUuids() != null)) ? scanRecord.getServiceUuids().size() : 0;
                        final int adCount = ((adData != null) && (adData.getUuids() != null)) ? adData.getUuids().size() : 0;

                        if (adCount > 0 || scanCount > 0){
                            final List<java.util.UUID> adUuids = adData.getUuids();
                            final List<ParcelUuid> srUuids = scanRecord.getServiceUuids();
                            if (scanCount > adCount) {
                                for (ParcelUuid uuid: srUuids) {
                                    if (! adUuids.contains(uuid.getUuid())) {
                                        MainActivity.error("\n\t\tSR: "+uuid.toString());
                                    }
                                }
                                scanUuidNoAdUuid++;
                            } else if (adCount > scanCount) {
                                for (UUID uuid: adUuids) {
                                    if (! srUuids.contains(new ParcelUuid(uuid))) {
                                        MainActivity.error("\n\t\tAD: "+uuid.toString());
                                    }
                                }
                                adUuidNoScanUuid++;
                            } else if (scanCount > 0) {
                                for (ParcelUuid uuid: srUuids) {
                                    MainActivity.info("\n\t\t==: "+uuid.toString());
                                }
                            }
                        }*/
                    }

                    final String capabilities = DEVICE_TYPE_LEGEND.get(
                            bluetoothClass == null ? null : bluetoothClass.getDeviceClass());
                    if (mainActivity.get() != null) {
                        final SharedPreferences prefs = mainActivity.get()
                                .getSharedPreferences(ListFragment.SHARED_PREFS, 0);
                        //ALIBI: shamelessly re-using frequency here for device type.
                        final Network network = addOrUpdateBt(bssid, ssid, type, capabilities,
                                scanResult.getRssi(),
                                NetworkType.BLE, location, prefs, batch);
                    }
                }
            } catch (SecurityException se) {
                Logging.warn("failing to perform BTLE scans: BT perms not granted", se);
            }
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
        if (!bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.startDiscovery();
            lastDiscoveryAt = System.currentTimeMillis();
        } else {
            if (DEBUG_BLUETOOTH_DATA) {
                Logging.info("skipping bluetooth scan; discover already in progress (last scan started "+(System.currentTimeMillis()-lastDiscoveryAt)+"ms ago)");
            }
        }

        if (Build.VERSION.SDK_INT >= 21) {
            final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner == null) {
                Logging.info("bluetoothLeScanner is null");
            }  else {
                if (scanning.compareAndSet(false, true)) {
                    final ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
                    scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
                    //TODO: make settable? NOTE: unset, you'll never get batch results, even with LOWER_POWER above
                    //  this is effectively how often we update the display
                    scanSettingsBuilder.setReportDelay(15000);
                    Logging.error("START BLE SCANs");
                    bluetoothLeScanner.startScan(
                            Collections.<ScanFilter>emptyList(), scanSettingsBuilder.build(), scanCallback);

                } else {
                    //ALIBI: tried a no-op here, but not the source of the pairs of batch callbacks
                    //DEBUG: MainActivity.error("FLUSH BLE SCANs");
                    bluetoothLeScanner.flushPendingScanResults(scanCallback);
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

        if (DEBUG_BLUETOOTH_DATA) {
            if (adUuidNoScanUuid > 0 || scanUuidNoAdUuid > 0) {
                Logging.error("AD but No Scan UUID: "+ adUuidNoScanUuid + " Scan but No Ad UUID: " + scanUuidNoAdUuid);
            }
        }
    }

    public void stopScanning() {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();

            if (mainActivity.get() != null) {
                final SharedPreferences prefs = mainActivity.get().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
                final boolean showCurrent = prefs.getBoolean(ListFragment.PREF_SHOW_CURRENT, true);
                if (listAdapter != null && showCurrent) {
                    listAdapter.clearBluetoothLe();
                    listAdapter.clearBluetooth();
                }
            }


            if (Build.VERSION.SDK_INT >= 21) {
                final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                if (bluetoothLeScanner != null) {
                    if (scanning.compareAndSet(true, false)) {
                        Logging.error("STOPPING BLE SCANS");
                        bluetoothLeScanner.stopScan(scanCallback);
                    } else {
                        Logging.error("Scanner present, comp-and-set prevented stop-scan");
                    }
                }
            }
        }
    }

    /**
     * General Bluetooth on-receive callback. Can register a BC or BLE network, although provides no means for distinguishing between them.
     * @param context
     * @param intent
     */
    @Override
    public void onReceive(final Context context, final Intent intent) {
        final MainActivity m = mainActivity.get();
        if (m != null) {
            final SharedPreferences prefs = m.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
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
                final String ssid = device.getName();

                int type;

                if (btClass == null && device != null) {
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

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                        log += "\n\tuuids: " + device.getUuids();
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        log += "\n\ttype:" + device.getType();
                    }

                    Logging.info(log);
                }

                final String capabilities = DEVICE_TYPE_LEGEND.get(type)
                    /*+ " (" + device.getBluetoothClass().getMajorDeviceClass()
                    + ":" +device.getBluetoothClass().getDeviceClass() + ")"*/
                        + ";" + device.getBondState();
                final GPSListener gpsListener = m.getGPSListener();

                Location location = null;
                if (gpsListener != null) {
                    final long gpsTimeout = prefs.getLong(ListFragment.PREF_GPS_TIMEOUT, GPSListener.GPS_TIMEOUT_DEFAULT);
                    final long netLocTimeout = prefs.getLong(ListFragment.PREF_NET_LOC_TIMEOUT, GPSListener.NET_LOC_TIMEOUT_DEFAULT);
                    gpsListener.checkLocationOK(gpsTimeout, netLocTimeout);
                    location = gpsListener.getLocation();
                } else {
                    Logging.warn("null gpsListener in BTR onReceive");
                }

                //ALIBI: shamelessly re-using frequency here for device type.
                final Network network = addOrUpdateBt(bssid, ssid, type, capabilities, rssi, NetworkType.BT, location, prefs, false);
                sort(prefs);
                if (listAdapter != null) listAdapter.notifyDataSetChanged();

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                //DEBUG: MainActivity.error("Previous BT "+prevBt.size()+ " Latest BT "+latestBt.size());
                prevBt = Collections.synchronizedSet(new HashSet(latestBt));
                latestBt.clear();

                ListFragment.lameStatic.currBt = prevBtle.size() + prevBt.size();

                final boolean showCurrent = prefs.getBoolean(ListFragment.PREF_SHOW_CURRENT, true);
                if (listAdapter != null) listAdapter.batchUpdateBt(showCurrent, false, true);
                ListFragment.lameStatic.newBt = dbHelper.getNewBtCount();
                ListFragment.lameStatic.runBt = runNetworks.size();
                sort(prefs);
                if (listAdapter != null) listAdapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * TODO: DRY this up with the sort in WifiReceiver?
     * @param prefs
     */
    private void sort(final SharedPreferences prefs) {
        if (listAdapter != null) {
            try {
                listAdapter.sort(NetworkListSorter.getSort(prefs));
            } catch (IllegalArgumentException ex) {
                Logging.error("sort failed: ",ex);
            }
        }
    }

    /**
     * Set display list adapter
     * @param listAdapter
     */
    public void setListAdapter( final SetNetworkListAdapter listAdapter ) {
        this.listAdapter = listAdapter;
    }

    /**
     * get the number of BT networks seen this run
     * @return
     */
    public int getRunNetworkCount() {
        return runNetworks.size();
    }

    /**
     * create the bluetooth timer thread
     * @param turnedBtOn
     */
    public void setupBluetoothTimer( final boolean turnedBtOn ) {
        Logging.info( "create Bluetooth timer" );
        final MainActivity m = mainActivity.get();
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
        final MainActivity m = mainActivity.get();
        if (null != m) {
            if (m.isScanning()) {
                if (!scanInFlight) {
                    try {
                        m.bluetoothScan();
                        //bluetoothManager.startScan();
                    } catch (Exception ex) {
                        Logging.warn("exception starting bt scan: " + ex, ex);
                    }
                    if (success) {
                        scanInFlight = true;
                    }
                }

                final long now = System.currentTimeMillis();
                if (lastScanResponseTime < 0) {
                    // use now, since we made a request
                    lastScanResponseTime = now;
                } else {
                    // are we seeing jams?
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
                final SharedPreferences prefs = m.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
                long batteryKill = prefs.getLong(
                        ListFragment.PREF_BATTERY_KILL_PERCENT, MainActivity.DEFAULT_BATTERY_KILL_PERCENT);

                if ( m.getBatteryLevelReceiver() != null ) {
                    final int batteryLevel = m.getBatteryLevelReceiver().getBatteryLevel();
                    final int batteryStatus = m.getBatteryLevelReceiver().getBatteryStatus();
                    // MainActivity.info("batteryStatus: " + batteryStatus);
                    // give some time since starting up to change this configuration
                    if ( batteryKill > 0 && batteryLevel > 0 && batteryLevel <= batteryKill
                        && batteryStatus != BatteryManager.BATTERY_STATUS_CHARGING
                        && (System.currentTimeMillis() - constructionTime) > 30000L) {
                        final String text = m.getString(R.string.battery_at) + " " + batteryLevel + " "
                                + m.getString(R.string.battery_postfix);
                        WiGLEToast.showOverActivity(m, R.string.error_general, text);
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
        final MainActivity m = mainActivity.get();
        if (null != m) {
            final SharedPreferences prefs = m.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
            String scanPref = ListFragment.PREF_OG_BT_SCAN_PERIOD;
            long defaultRate = MainActivity.OG_BT_SCAN_DEFAULT;
            // if over 5 mph
            Location location = null;
            final GPSListener gpsListener = m.getGPSListener();
            if (gpsListener != null) {
                location = gpsListener.getLocation();
            }
            if (location != null && location.getSpeed() >= 2.2352f) {
                scanPref = ListFragment.PREF_OG_BT_SCAN_PERIOD_FAST;
                defaultRate = MainActivity.OG_BT_SCAN_FAST_DEFAULT;
            } else if (location == null || location.getSpeed() < 0.1f) {
                scanPref = ListFragment.PREF_OG_BT_SCAN_PERIOD_STILL;
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
        final boolean showCurrent = prefs.getBoolean(ListFragment.PREF_SHOW_CURRENT, true);

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

        final MainActivity m = mainActivity.get();
        final boolean ssidSpeak = prefs.getBoolean(ListFragment.PREF_SPEAK_SSID, false)
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


        final Matcher ssidMatcher = FilterMatcher.getSsidFilterMatcher( prefs, ListFragment.FILTER_PREF_PREFIX );
        if (null != m) {
            final Matcher bssidMatcher = m.getBssidFilterMatcher(ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS);
            final Matcher bssidDbMatcher = m.getBssidFilterMatcher(ListFragment.PREF_EXCLUDE_LOG_ADDRS);
            //Update display
            if (listAdapter != null) {
                if (btTypeUpdate) {
                    listAdapter.morphBluetoothToLe(network);
                }
                if (showCurrent || newForRun) {
                    if (FilterMatcher.isOk(ssidMatcher, bssidMatcher, prefs, ListFragment.FILTER_PREF_PREFIX, network)) {
                        if (batch) {
                            if (NetworkType.BT.equals(network.getType())) {
                                listAdapter.enqueueBluetooth(network);
                            } else if (NetworkType.BLE.equals(network.getType())) {
                                listAdapter.enqueueBluetoothLe(network);
                            }
                        } else {
                            if (NetworkType.BT.equals(network.getType())) {
                                listAdapter.addBluetooth(network);
                            } else if (NetworkType.BLE.equals(network.getType())) {
                                listAdapter.addBluetoothLe(network);
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
    private boolean isMiscOrUncategorized(final int type) {
        if (type == 0 || type == 7936) {
            return true;
        }
        return false;
    }

}
