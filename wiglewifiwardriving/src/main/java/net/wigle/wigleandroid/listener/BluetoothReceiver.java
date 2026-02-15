package net.wigle.wigleandroid.listener;

import static android.bluetooth.BluetoothDevice.ADDRESS_TYPE_PUBLIC;
import static android.bluetooth.BluetoothDevice.ADDRESS_TYPE_RANDOM;
import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC;
import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL;
import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
import static android.bluetooth.le.ScanSettings.MATCH_MODE_AGGRESSIVE;
//import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY; battery drain
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER;

import android.annotation.SuppressLint;
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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.SparseArray;

import net.wigle.wigleandroid.FilterMatcher;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.ui.NetworkListUtil;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.ScanUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
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
import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_GUESS_BLE_ADDRESS_TYPE;

/**
 * Central class for compound BT scanning: BT Scan intent receiver and (if supported) LE scan
 * Created by bobzilla and rksh
 */
public final class BluetoothReceiver extends BroadcastReceiver implements LeScanUpdater {

    // if a batch scan arrives within <x> millis of the previous batch, maybe that's too close
    // Common on Android 8.1+ devices
    // Apparently a feature used for ranging/distance
    private final static long MIN_LE_BATCH_GAP_MILLIS = 250; // ALIBI: must be lower than LE_REPORT_DELAY_MILLIS
    private final static long LE_REPORT_DELAY_MILLIS = 15000; // ALIBI: experimental - should this be settable?

    private final byte RANDOM_ADDRESS_BIT = 0x01;

    // Address type constants for pattern-based detection
    // Note: These extend beyond Android's API constants to include sub-types
    private static final int PATTERN_ADDRESS_TYPE_PUBLIC = 0;
    private static final int PATTERN_ADDRESS_TYPE_RANDOM_STATIC = 1;        // bits [1:0] = 01
    private static final int PATTERN_ADDRESS_TYPE_RANDOM_RESOLVABLE = 2;    // bits [1:0] = 10
    private static final int PATTERN_ADDRESS_TYPE_RANDOM_NON_RESOLVABLE = 3; // bits [1:0] = 11

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            initMap.put(BluetoothClass.Device.PERIPHERAL_KEYBOARD, "Keyboard");
            initMap.put(BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING, "Keyboard+p");
            initMap.put(BluetoothClass.Device.PERIPHERAL_NON_KEYBOARD_NON_POINTING, "Keyboard !p");
            initMap.put(BluetoothClass.Device.PERIPHERAL_POINTING, "Pointer");
        }
        initMap.put(BluetoothClass.Device.PHONE_CELLULAR, "Cellphone");
        initMap.put(BluetoothClass.Device.PHONE_CORDLESS, "Cordless Phone");
        initMap.put(BluetoothClass.Device.PHONE_ISDN, "ISDN");
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
    private Set prevBt = Collections.synchronizedSet(new HashSet<>());

    private final Set<String> latestBtle = Collections.synchronizedSet(new HashSet<>());

    private final Set<String> prevBtle = Collections.synchronizedSet(new HashSet<>());

    private final boolean hasLeSupport;

    private BluetoothReceiver() {
        hasLeSupport = false;
        dbHelper = null;
        prefs = null;
        //nope, not default constructor
    }

    /**
     * Standard constructor
     * @param dbHelper the DatabaseHelper instance for adding observations
     * @param hasLeSupport whether BT LE support was detected and permitted at the top level of the app
     * @param prefs SharedPreferences instance
     */
    public BluetoothReceiver(final DatabaseHelper dbHelper, final boolean hasLeSupport, final SharedPreferences prefs) {
        this.dbHelper = dbHelper;
        ListFragment.lameStatic.runBtNetworks = runNetworks;
        this.prefs = prefs;
        this.hasLeSupport = hasLeSupport;

        if (this.hasLeSupport) {
            //ALIBI: seeing same-count (redundant) batch returns in rapid succession triggering pointless churn
            AtomicInteger btLeEmpties = new AtomicInteger(0);
            scanCallback = new LeScanCallback(dbHelper, prefs, runNetworks, latestBtle, prevBtle,
                    latestBt, new AtomicLong(System.currentTimeMillis()), scanning, this, btLeEmpties);
        } else {
            scanCallback = null;
        }
    }

    /**
     * Handle a BT LE scan le.ScanResult
     * @param scanResult the result
     * @param location location from location provider
     * @param batch whether this scan result is part of a batch (invalidate the UI immediately or later in addOrUpdateBt)
     */
    @SuppressLint("MissingPermission")
    public void handleLeScanResult(final ScanResult scanResult, Location location, final boolean batch, final boolean guessLeAddressType) {
        try {
            final ScanRecord scanRecord = scanResult.getScanRecord();
            if (scanRecord != null) {
                final BluetoothDevice device = scanResult.getDevice();
                Integer bleAddressType =
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) ?
                                device.getAddressType() : null;

                final String address = device.getAddress();
                final int deviceType = device.getType();
                Integer patternAddressType = null;

                if ((deviceType == DEVICE_TYPE_LE || deviceType == DEVICE_TYPE_DUAL)) {
                    patternAddressType = getAddressTypeFromPattern(address);
                }
                if (bleAddressType == null && guessLeAddressType) {
                    // API unavailable - use pattern detection (only for BLE devices)
                    if (patternAddressType != null) {
                        bleAddressType = patternAddressType;
                        if (DEBUG_BLUETOOTH_DATA && patternAddressType == ADDRESS_TYPE_RANDOM) {
                            Logging.info("API unavailable, detected random address via pattern: " + address);
                        }
                    } else {
                        // default to public
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bleAddressType = ADDRESS_TYPE_PUBLIC;
                        } else {
                            bleAddressType = 0;
                        }
                    }
                } else if (null != bleAddressType) {
                    if (bleAddressType == ADDRESS_TYPE_PUBLIC && patternAddressType != null && patternAddressType == ADDRESS_TYPE_RANDOM) {
                        // API says PUBLIC but pattern suggests RANDOM - trust pattern (with OUI verification)
                        if (DEBUG_BLUETOOTH_DATA) {
                            Logging.warn("API + OUI check indicates RANDOM for " + address);
                        }
                        bleAddressType = 1; //ADDRESS_TYPE_RANDOM;
                    } else if (bleAddressType == ADDRESS_TYPE_RANDOM && patternAddressType != null && patternAddressType == ADDRESS_TYPE_PUBLIC) {
                        // API says RANDOM but pattern indicates PUBLIC
                        if (DEBUG_BLUETOOTH_DATA) {
                            Logging.warn("API returned RANDOM but pattern indicates PUBLIC for " + address + " (trusting API)");
                        }
                    }
                }
                final String bssid = device.getAddress();
                latestBtle.add(bssid);
                prevBt.remove(bssid);   // ALIBI: upgrade to BLE -> remove BC
                latestBt.remove(bssid); // ""

                // Coalesce name if not previously known
                final String ssid =
                        (null == scanRecord.getDeviceName() || scanRecord.getDeviceName().isEmpty())
                                ? device.getName()
                                : scanRecord.getDeviceName();

                // odds of Major class being known when specific class UNCAT seem thin
                final BluetoothClass bluetoothClass = device.getBluetoothClass();
                int type = BluetoothClass.Device.Major.UNCATEGORIZED;
                if (bluetoothClass != null) {
                    final int deviceClass = bluetoothClass.getDeviceClass();
                    type = (deviceClass == 0 || deviceClass == BluetoothClass.Device.Major.UNCATEGORIZED)
                            ? bluetoothClass.getMajorDeviceClass()
                            : deviceClass;
                }
                final String alias = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ? device.getAlias(): "";

                if (DEBUG_BLUETOOTH_DATA) {
                    Logging.info("LE deviceName:\t" + ssid
                                    + "\n\taddress:\t" + bssid
                                    + "\n\tname:\t" + scanRecord.getDeviceName() + " (vs. " + device.getName() + ")"
                                    //+ "\n\tadName: " + adDeviceName
                                    + "\n\tclass:\t"
                                    + (bluetoothClass == null ? null : DEVICE_TYPE_LEGEND.get(bluetoothClass.getDeviceClass()))
                                    + " (" + bluetoothClass + ")"
                                    + "\n\ttype:\t" + typeMap(device.getType()) + " ("+device.getType()+")"
                                    + "\n\tRSSI:\t" + scanResult.getRssi()
                                    + "\n\tFlags:\t" + scanRecord.getAdvertiseFlags()
                                    + "\n\tScanRecord:\t" + scanRecord
                                    + "\n\trandom address:\t" + bleAddressType
                                    + "\n\tAlias:\t" + alias
                                    + "\n\tUUIDs:\t" + Arrays.toString(device.getUuids())
                                    + "\n\tService UUIDs:\t" + scanRecord.getServiceUuids()
                                    );
                    if (bleAddressType != null && bleAddressType != 0) {
                        Logging.info("\tinteresting addressType: "+bleAddressType);
                    }
                }

                List<String> uuid16Services = null;
                if (null != scanRecord.getServiceUuids() && !scanRecord.getServiceUuids().isEmpty()) {
                    uuid16Services = new ArrayList<>();
                    for (ParcelUuid u: scanRecord.getServiceUuids()) {
                        uuid16Services.add(u.getUuid().toString());
                        byte[] data = scanRecord.getServiceData(u);
                        if (null != data) {
                            //Logging.infoHexString(data);
                            //Logging.info(u.getUuid().toString() + ": " + data);
                        }
                    }
                }

                Integer mfgrKey = null;
                if (null == uuid16Services && scanRecord.getManufacturerSpecificData() != null) {
                    SparseArray<byte[]> bytesArray= scanRecord.getManufacturerSpecificData();
                    for(int i = 0; i < bytesArray.size(); i++) {
                        mfgrKey = bytesArray.keyAt(i);
                        //DEBUG: Logging.error("got: "+mfgrKey+" net "+bssid);
                    }
                }

                final String capabilities = DEVICE_TYPE_LEGEND.get(
                        bluetoothClass == null ? null : bluetoothClass.getDeviceClass());

                if (MainActivity.getMainActivity() != null) {
                    addOrUpdateBt(bssid, ssid, type, capabilities,
                            scanResult.getRssi(), NetworkType.BLE,
                            uuid16Services, mfgrKey, location, prefs, batch, bleAddressType);
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

        // classic BT scan - basically "Always Be Discovering"
        // times between discovery runs will be MAX(wifi delay) since this is called from wifi receiver
        try {
            if (!bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.startDiscovery();
                lastDiscoveryAt = System.currentTimeMillis();
            } else {
                if (DEBUG_BLUETOOTH_DATA) {
                    Logging.info("skipping bluetooth scan; discover already in progress (last scan started "
                            + (System.currentTimeMillis() - lastDiscoveryAt) + "ms ago)");
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
                            // TODO: consider SCAN_MODE_BALANCED and SCAN_MODE_LOW_LATENCY
                            scanCallback);
                } catch (SecurityException se) {
                    Logging.error("No permission for bluetoothLeScanner.startScan", se);
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
            Logging.info("\tpaired device: "+device.getAddress()+" - "+device.getName() + device.getBluetoothClass());
            //BluetoothClass bluetoothClass = device.getBluetoothClass();
        }*/
    }

    /**
     * Stop all scanning - both bluetoothAdapter and bluetoothLeScanner
     */
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
                    } catch (IllegalArgumentException iae) {
                        Logging.error("Illegal arg. for bluetoothAdapter.stopScan", iae);
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
     * General Bluetooth on-receive callback. Can register a BC or BLE network.
     * @param context the Context of the intent we're getting
     * @param intent the intent for the scan
     */
    @SuppressLint("MissingPermission")
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
                    // Seen in Play console as a crash
                    Logging.error("onReceive with null device - discarding this instance");
                    return;
                }

                final Integer bleAddressType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                        ?  device.getAddressType(): null;

                final String bssid = device.getAddress();
                if (device.getType() == DEVICE_TYPE_CLASSIC) {
                    latestBt.add(bssid);
                } else {
                    latestBtle.add(bssid);
                }

                final BluetoothClass btClass = intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

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
                                + " (" + type + ")"
                                + "\n\ttype: " + typeMap(device.getType())
                                + "\n\tbondState: " + device.getBondState()
                                + "\n\tuuids: " + Arrays.toString(device.getUuids())
                                + "\n\tbleAddressType: "+bleAddressType;
                        Logging.info(log);
                        if (bleAddressType != null && bleAddressType != 0) {
                            Logging.error("interesting addressType: "+bleAddressType);
                        }
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

                    final Network network = addOrUpdateBt(bssid, ssid, type, capabilities, rssi,
                            btNetworkType(device.getType()),
                            //TODO: will BTLE networks in this callback ever contain uuids/mfgrId ?
                            null, null,
                            location, prefs,
                            false, bleAddressType);
                    if (listAdapter != null) {
                        SetNetworkListAdapter l = listAdapter.get();
                        if (null != l) {
                            NetworkListUtil.sort(prefs, l);
                        }
                    }
                } catch (SecurityException se) {
                    Logging.error("No permission for device inspection", se);
                }

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                //DEBUG: Logging.error("**onReceive**: prev/latest Classic"+prevBt.size()+ "/" + latestBt.size() + " prev/latest LE:" + prevBtle.size() + "/"+latestBtle.size());
                prevBt = Collections.synchronizedSet(new HashSet(latestBt));
                latestBt.clear();
                //if (!this.hasLeSupport && !latestBtle.isEmpty()) {
                    //ALIBI: only reset on standard scan result finished if Le scan handlers won't also reset. Should be impossible
                     latestBtle.clear();
                //}

                ListFragment.lameStatic.currBt = ((null != scanCallback) ? (scanCallback.getPrevBtLeSize() + prevBt.size()) : (prevBt.size()));

                final boolean showCurrent = prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true);
                if (listAdapter != null) {
                    SetNetworkListAdapter l = listAdapter.get();
                    if (null != l) {
                        l.batchUpdateBt(showCurrent, !this.hasLeSupport, true);
                        NetworkListUtil.sort(prefs, l);
                    }
                }
                ListFragment.lameStatic.newBt = dbHelper.getNewBtCount();
                ListFragment.lameStatic.runBt = runNetworks.size();
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
                        return false;
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
                m.setScanStatusUI(m.getString(R.string.list_scanning_off));
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
        if (m == null) {
            return 0L;
        }
        Location location = null;
        final GNSSListener gpsListener = m.getGPSListener();
        if (gpsListener != null) {
            location = gpsListener.getCurrentLocation();
        }
        return ScanUtil.getBtScanPeriod(prefs, location);
    }

    /**
     * Common "update" method for BT networks - make a new one if not present, otherwise merge attributes
     * @param bssid the unique address
     * @param ssid the name
     * @param deviceType BLE/BC
     * @param capabilities the computed capabilities list
     * @param strength signal strength
     * @param type composite of reported type (BC) and discovered capabilities/services (BLE)
     * @param uuid16Services services uuid16 values for BLE
     * @param mfgrId manufacturer ID for BLE
     * @param location location at time of observation
     * @param prefs SharedPreferences instance
     * @param batch whether this is part of a batch update (false = single add)
     * @param bleAddressType address type PUBLIC/RANDOM (not seen IRL yet)
     * @return the new or updated Network instance
     */
    private Network addOrUpdateBt(final String bssid, final String ssid,
                                    final int deviceType, /*final String networkTypeName*/final String capabilities,
                                    final int strength, final NetworkType type, final List<String> uuid16Services, final Integer mfgrId,
                                    final Location location, SharedPreferences prefs, final boolean batch, final Integer bleAddressType) {

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
            network = new Network(bssid, ssid, deviceType, capabilities, strength, type, uuid16Services, mfgrId, null, bleAddressType);
            networkCache.put(bssid, network);
        } else {
            String mergedSsid = (ssid == null || ssid.isEmpty()) ? network.getSsid() : ssid;
            network.setSsid(mergedSsid);
            int mergedDeviceType = (!isMiscOrUncategorized(network.getFrequency()) ? network.getFrequency() : deviceType);
            final int oldDevType = network.getFrequency();
            if (mergedDeviceType != oldDevType) {
                deviceTypeUpdate = true;
            }
            network.setFrequency(mergedDeviceType);
            network.setLevel(strength);

            if (null != mfgrId) {
                network.addBleMfgrId(mfgrId);
            }

            if (null != uuid16Services && !uuid16Services.isEmpty()) {
                for (final String uuid : uuid16Services) {
//Logging.info(bssid+": svc: "+uuid);
                    network.addBleServiceUuid(uuid);
                }
            }

            if (null != bleAddressType) {
                network.setBleAddressType(bleAddressType);
            }

            if (NetworkType.BT.equals(type) && NetworkType.BLE.equals(network.getType())) {
                if (DEBUG_BLUETOOTH_DATA) {
                    Logging.info("had a BLE record, got BC: " + network.getBssid() + "(new: " + newForRun + ")");
                }
            } else if (NetworkType.BLE.equals(type) && NetworkType.BT.equals(network.getType())) {
                //ALIBI: detected via standard bluetooth, updated as LE (LE should win)
                //DEBUG: MainActivity.info("had a BC record, moving to BLE: "+network.getBssid()+ "(new: "+newForRun+")");
                btTypeUpdate = true;
                network.setType(NetworkType.BLE);
            } else {
                //ALIBI: update capabilities only if was Misc/Uncategorized, now recognized?
                //DEBUG: MainActivity.info("existing BT net: "+network.getBssid() + "(new: "+newForRun+")");
                if (capabilities != null && !capabilities.isEmpty() &&
                        !capabilities.startsWith("Misc") && !capabilities.startsWith("Uncategorized") &&
                        (network.getCapabilities().isEmpty() || network.getCapabilities().startsWith("Misc")
                                || network.getCapabilities().startsWith("Uncategorized"))) {
                    network.setCapabilities(capabilities);
                    // ALIBI: device state/bond state not available in this method to post-pend;
                }
            }
        }

        if ( location != null && (newForRun || network.getLatLng() == null) ) {
            // set the LatLng for mapping
            final LatLng LatLng = new LatLng( location.getLatitude(), location.getLongitude() );
            network.setLatLng( LatLng );
        }

        final MainActivity m = MainActivity.getMainActivity();
        final Matcher ssidMatcher = FilterMatcher.getSsidFilterMatcher( prefs, PreferenceKeys.FILTER_PREF_PREFIX );
        if (null != m) {
            final Matcher bssidMatcher = m.getBssidFilterMatcher(PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS);
            final Matcher bssidDbMatcher = m.getBssidFilterMatcher(PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS);
            final Matcher bssidAlertMatcher = m.getBssidFilterMatcher( PreferenceKeys.PREF_ALERT_ADDRS );
            final Matcher mfgrIdMatcher = m.getBssidFilterMatcher( PreferenceKeys.PREF_ALERT_BLE_MFGR_IDS );

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
                            } else {
                                Logging.error("MISSED enqueue for "+network.getBssid() + " " + network.getType());
                            }
                        } else {
                            if (NetworkType.BT.equals(network.getType())) {
                                listAdapter.get().addBluetooth(network);
                                //Logging.info("add BTC "+network.getBssid());
                            } else if (NetworkType.BLE.equals(network.getType())) {
                                listAdapter.get().addBluetoothLe(network);
                                //Logging.info("add BLE "+network.getBssid());
                            } else {
                                Logging.error("MISSED enqueue for "+network.getBssid() + " " + network.getType());
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
            if (bssidAlertMatcher != null) {
                bssidAlertMatcher.reset(network.getBssid());
                if (bssidAlertMatcher.find()) {
                    m.updateLastHighSignal(network.getLevel());
                }
            }
            if (mfgrIdMatcher != null && network.getBleMfgrId() != null) {
                mfgrIdMatcher.reset(String.format("%04X", network.getBleMfgrId()));
                if (mfgrIdMatcher.find()) {
                    m.updateLastHighSignal(network.getLevel());
                }
            }
        }

        //TODO: notify matcher for BLE goes here
        final boolean ssidSpeak = prefs.getBoolean(PreferenceKeys.PREF_SPEAK_SSID, false)
                && null != m && !m.isMuted();

        if (newForRun) {
            // ALIBI: There are simply a lot of these - not sure this is practical
            /*if ( ssidSpeak ) {
                ssidSpeaker.add( network.getSsid() );
            }*/
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
        private final AtomicBoolean bleScanning;
        private final WeakReference<LeScanUpdater> updater;
        private final AtomicInteger empties;
        private final AtomicBoolean miniBatch;

        private static final int MINI_BATCH_CUTOFF = 8;

        public LeScanCallback(DatabaseHelper dbHelper,
                              SharedPreferences prefs,
                              Set<String> runNetworks,
                              Set<String> latestBtle,
                              Set<String> latestBt,
                              Set<String> prevBtle,
                              AtomicLong lastLeBatchResponseTime,
                              AtomicBoolean scanning, LeScanUpdater updater, final AtomicInteger empties) {
            this.dbHelper = dbHelper;
            this.prefs = prefs;
            this.runNetworks = runNetworks;
            this.latestBtle = latestBtle;
            this.latestBt = latestBt;
            this.prevBtle = prevBtle;
            this.lastLeBatchResponseTime = lastLeBatchResponseTime;
            this.bleScanning = scanning;
            this.updater = new WeakReference<>(updater);
            this.empties = empties;
            this.miniBatch = new AtomicBoolean(false);
        }

        @Override
        public void onScanResult(int callbackType, ScanResult scanResult) {
            final MainActivity m = MainActivity.getMainActivity();
            Location location = null;
            final boolean guessLeAddressType = prefs != null && prefs.getBoolean(PREF_GUESS_BLE_ADDRESS_TYPE, false);
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

            final LeScanUpdater update = updater.get();
            if (null != update) {
                update.handleLeScanResult(scanResult, location, false, guessLeAddressType);
            }
            ListFragment.lameStatic.newBt = dbHelper.getNewBtCount();
            ListFragment.lameStatic.runBt = runNetworks.size();
            if (listAdapter != null) {
                final SetNetworkListAdapter l = listAdapter.get();
                if (null != l) {
                    NetworkListUtil.sort(prefs, l);
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            long responseClockTime = System.currentTimeMillis();
            long diff = responseClockTime - lastLeBatchResponseTime.longValue();
            final boolean guessLeAddressType = prefs != null && prefs.getBoolean(PREF_GUESS_BLE_ADDRESS_TYPE, false);

            lastLeBatchResponseTime.set(responseClockTime);
            if (diff < MIN_LE_BATCH_GAP_MILLIS) {
                Logging.debug("Tried to update BTLE batch in improbably short time: " + diff + " ("+results.size()+" results)");
                return;
            }

            if (results.size() <= MINI_BATCH_CUTOFF && miniBatch.compareAndSet(false, true)) {
                //ALIBI: we see a pattern with LE scan batch sizes: | | | . | | | . - the small
                // batches clear the display, making interaction with the list view tricky.
                // This will let us "lag" for those small batches instead of clearing
                Logging.info("mini LE batch...");
            } else {
                miniBatch.set(false);
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

            final LeScanUpdater update = updater.get();
            if (null != update) {
                for (final ScanResult scanResult : results) {
                    update.handleLeScanResult(scanResult, location, true, guessLeAddressType);
                }
            }
            //DEBUG:Logging.error("**LE.onBatchScanResults** Previous BTLE: "+(prevBtle != null ? prevBtle.size() : "N/A")+ " Latest BTLE: "+(latestBtle != null ? latestBtle.size() : "N/A"));
            prevBtle = new HashSet<>(latestBtle);
            latestBtle.clear();

            ListFragment.lameStatic.currBt = prevBtle.size() + latestBt.size();
            ListFragment.lameStatic.newBt = dbHelper.getNewBtCount();
            ListFragment.lameStatic.runBt = runNetworks.size();
            if (listAdapter != null) {
                final SetNetworkListAdapter l = listAdapter.get();
                if (null != l) {
                    l.batchUpdateBt(prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true)
                                    && !miniBatch.get(), true, false);
                    NetworkListUtil.sort(prefs, l);
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
                    Logging.error("BluetoothLE Scan: failed: " +
                            (errorCode == SCAN_FAILED_FEATURE_UNSUPPORTED?"Scanning not supported":"Unable to register"));
                    if ((listAdapter != null) && prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true)) {
                        final SetNetworkListAdapter l = listAdapter.get();
                        if (null != l) {
                            l.clearBluetoothLe();
                        }
                    }
                    break;
                case ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY:
                    Logging.error("scan failed due to too-frequent scan requests.");
                    break;
                case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                case ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES:
                    //ALIBI: catch-all - as of API 33, this subsumes:
                    //   SCAN_FAILED_OUT_OF_HARDWARE_RESOURCESl SCAN_FAILED_SCANNING_TOO_FREQUENTLY
                    if ((listAdapter != null) && prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true)) {
                        final SetNetworkListAdapter l = listAdapter.get();
                        if (null != l) {
                            Logging.error("Scan callback failure: "+errorCode);
                            l.clearBluetoothLe();
                        }
                    }
                    Logging.error("Bluetooth LE scan error: " + errorCode);
                    bleScanning.set(false);
                default:
                    Logging.error("Bluetooth LE scan error: " + errorCode);
                    break;
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

    public static String typeMap(final int i) {
        switch (i) {
            case DEVICE_TYPE_CLASSIC:
                return "Classic";
            case DEVICE_TYPE_LE:
                return "LE";
            case DEVICE_TYPE_DUAL:
                return "Dual";
            default:
                return "Unknown";
        }
    }
    public static NetworkType btNetworkType(final int i) {
        switch (i) {
            case DEVICE_TYPE_CLASSIC:
                return NetworkType.BT;
            case DEVICE_TYPE_LE:
                return NetworkType.BLE;
            case DEVICE_TYPE_DUAL:
                return NetworkType.BLE; //TODO: address DUAL
            default:
                return NetworkType.BT;
        }
    }

    /**
     * Guess BLE address types
     * - 00 = Public address
     * - 01 = Random static address
     * - 10 = Random private resolvable address
     * - 11 = Random private non-resolvable address
     * @param address MAC address string in format "XX:XX:XX:XX:XX:XX"
     * @return Integer address type: ADDRESS_TYPE_PUBLIC (0), ADDRESS_TYPE_RANDOM (1) for any random type,
     *         or null if address is invalid or OUI check indicates it's likely public
     */
    private Integer getAddressTypeFromPattern(String address) {
        if (address == null || address.length() < 2) {
            return null;
        }

        try {
            String firstByteStr = address.substring(0, 2);
            int firstByte = Integer.parseInt(firstByteStr, 16);
            int addressTypeBits = firstByte & 0x03;

            // 00 -> public for sure
            if (addressTypeBits == PATTERN_ADDRESS_TYPE_PUBLIC) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return ADDRESS_TYPE_PUBLIC;
                } else {
                    return 0;
                }
            }

            // check first 24 bits, make sure we have no OUI matching this
            String addressNoColons = address.replace(":", "").toUpperCase();
            if (addressNoColons.length() < 6) {
                return null;
            }

            String ouiPrefix = addressNoColons.substring(0, 6);
            if (ListFragment.lameStatic.oui != null) {
                String ouiResult = ListFragment.lameStatic.oui.getOui(ouiPrefix);
                if (ouiResult != null && !ouiResult.isEmpty()) {
                    if (DEBUG_BLUETOOTH_DATA) {
                        Logging.info("Address " + address + " has random bit pattern (type bits: " + addressTypeBits +
                                ") but OUI " + ouiPrefix + " is in database (" + ouiResult + "), treating as public");
                    }
                    return ADDRESS_TYPE_PUBLIC;
                }
            }
            if (DEBUG_BLUETOOTH_DATA) {
                String subType = "unknown";
                switch (addressTypeBits) {
                    case PATTERN_ADDRESS_TYPE_RANDOM_STATIC:
                        subType = "static";
                        break;
                    case PATTERN_ADDRESS_TYPE_RANDOM_RESOLVABLE:
                        subType = "resolvable";
                        break;
                    case PATTERN_ADDRESS_TYPE_RANDOM_NON_RESOLVABLE:
                        subType = "non-resolvable";
                        break;
                }
                Logging.info("Detected random address type: " + subType + " (bits: " + addressTypeBits + ") for " + address);
            }
            return ADDRESS_TYPE_RANDOM;
        } catch (NumberFormatException e) {
            Logging.warn("Failed to parse MAC address for random address detection: " + address, e);
            return null;
        } catch (Exception e) {
            //presume public on error.
            Logging.warn("Error checking OUI database for address: " + address, e);
            return null;
        }
    }

}
