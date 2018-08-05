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
import android.os.Build;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;

import net.wigle.wigleandroid.FilterMatcher;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.NetworkListAdapter;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.util.BluetoothUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;

import uk.co.alt236.bluetoothlelib.device.BluetoothLeDevice;
import uk.co.alt236.bluetoothlelib.device.adrecord.AdRecord;
import uk.co.alt236.bluetoothlelib.device.adrecord.AdRecordStore;

import static net.wigle.wigleandroid.MainActivity.DEBUG_BLUETOOTH_DATA;
import static net.wigle.wigleandroid.listener.WifiReceiver.CHANNEL_COMPARE;
import static net.wigle.wigleandroid.listener.WifiReceiver.CRYPTO_COMPARE;
import static net.wigle.wigleandroid.listener.WifiReceiver.FIND_TIME_COMPARE;
import static net.wigle.wigleandroid.listener.WifiReceiver.SIGNAL_COMPARE;
import static net.wigle.wigleandroid.listener.WifiReceiver.SSID_COMPARE;
import static net.wigle.wigleandroid.listener.WifiReceiver.channelCompare;
import static net.wigle.wigleandroid.listener.WifiReceiver.cryptoCompare;
import static net.wigle.wigleandroid.listener.WifiReceiver.findTimeCompare;
import static net.wigle.wigleandroid.listener.WifiReceiver.signalCompare;
import static net.wigle.wigleandroid.listener.WifiReceiver.ssidCompare;

/**
 * Created by bobzilla on 12/20/15
 */
public final class BluetoothReceiver extends BroadcastReceiver {

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

    private MainActivity mainActivity;
    private final DatabaseHelper dbHelper;
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final Map<String,Network> currentBluetoothNetworks = new ConcurrentLinkedHashMap<String, Network>(64);
    private final Set<String> runNetworks = new HashSet<>();
    private NetworkListAdapter listAdapter;
    private final ScanCallback scanCallback;

    // refresh thresholds - probably should either make these configurable, or take BT scans off the WiFiReceiveer clock
    private static final int EMPTY_LE_THRESHOLD = 30;
    private static final int EMPTY_BT_THRESHOLD = 2;

    // scan state
    private int btCount = 0;
    private long lastDiscoveryAt = 0;

    public BluetoothReceiver(final MainActivity mainActivity, final DatabaseHelper dbHelper ) {
        this.mainActivity = mainActivity;
        this.dbHelper = dbHelper;


        if (Build.VERSION.SDK_INT >= 21) {
            scanCallback = new ScanCallback() {
                final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
                private int empties = 0;

                @Override
                public void onScanResult(int callbackType, ScanResult scanResult) {
                    final GPSListener gpsListener = mainActivity.getGPSListener();
                    //DEBUG:
                    MainActivity.info("LE scanResult: " + scanResult + " callbackType: " + callbackType);
                    Location location = null;
                    if (gpsListener != null) {
                        final long gpsTimeout = prefs.getLong(ListFragment.PREF_GPS_TIMEOUT, GPSListener.GPS_TIMEOUT_DEFAULT);
                        final long netLocTimeout = prefs.getLong(ListFragment.PREF_NET_LOC_TIMEOUT, GPSListener.NET_LOC_TIMEOUT_DEFAULT);
                        gpsListener.checkLocationOK(gpsTimeout, netLocTimeout);
                        location = gpsListener.getLocation();
                    } else {
                        MainActivity.warn("Null gpsListener in LE Single Scan Result");
                    }

                    handleLeScanResult(scanResult, location);
                    final long newBtCount = dbHelper.getNewBtCount();
                    ListFragment.lameStatic.newBt = newBtCount;
                    ListFragment.lameStatic.runBt = runNetworks.size();
                    sort(prefs);
                    listAdapter.notifyDataSetChanged();
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    //MainActivity.info("LE Batch results: " + results);
                    final GPSListener gpsListener = mainActivity.getGPSListener();

                    Location location = null;

                    boolean forceReset = false;
                    if (results.isEmpty()) {
                        empties++;
                        //ALIBI: if it's been too long, we'll force-clear
                        if (EMPTY_LE_THRESHOLD < empties) {
                            forceReset = true;
                            empties = 0;
                        }
                    } else {
                        forceReset = true;
                    }

                    if ((listAdapter != null) && prefs.getBoolean( ListFragment.PREF_SHOW_CURRENT, true ) && forceReset ) {
                        listAdapter.clearBluetoothLe();
                    }

                    if (gpsListener != null) {
                        final long gpsTimeout = prefs.getLong(ListFragment.PREF_GPS_TIMEOUT, GPSListener.GPS_TIMEOUT_DEFAULT);
                        final long netLocTimeout = prefs.getLong(ListFragment.PREF_NET_LOC_TIMEOUT, GPSListener.NET_LOC_TIMEOUT_DEFAULT);
                        gpsListener.checkLocationOK(gpsTimeout, netLocTimeout);
                        location = gpsListener.getLocation();
                    } else {
                        MainActivity.warn("Null gpsListener in LE Batch Scan Result");
                    }

                    for (final ScanResult scanResult : results) {
                        handleLeScanResult(scanResult, location);
                    }
                    final long newBtCount = dbHelper.getNewBtCount();
                    ListFragment.lameStatic.newBt = newBtCount;
                    ListFragment.lameStatic.runBt = runNetworks.size();
                    sort(prefs);
                    listAdapter.notifyDataSetChanged();
                }

                @Override
                public void onScanFailed(int errorCode) {
                    /*if ((listAdapter != null) && prefs.getBoolean( ListFragment.PREF_SHOW_CURRENT, true ) ) {
                        listAdapter.clearBluetoothLe();
                    }*/
                    switch (errorCode) {
                        case SCAN_FAILED_ALREADY_STARTED:
                            MainActivity.info("BluetoothLEScan already started");
                            break;
                        default:
                            MainActivity.error("Bluetooth scan error: " + errorCode);
                            scanning.set(false);
                    }
                }
            };
        } else {
            scanCallback = null;
        }
    }

    private void handleLeScanResult(final ScanResult scanResult, Location location) {
        if (Build.VERSION.SDK_INT >= 21) {
            //DEBUG: MainActivity.info("LE scanResult: " + scanResult);
            final ScanRecord scanRecord = scanResult.getScanRecord();
            if (scanRecord != null) {
                final BluetoothDevice device = scanResult.getDevice();
                BluetoothUtil.BleAdvertisedData adData = BluetoothUtil.parseAdvertisedData(scanRecord.getBytes());

                final String adDeviceName = (adData != null) ? adData.getName(): null;

                final String bssid = device.getAddress();

                final String ssid =
                        (null ==  scanRecord.getDeviceName() || scanRecord.getDeviceName().isEmpty())
                                ? adDeviceName
                                :scanRecord.getDeviceName();

                // This is questionable - of of Major class being known when specific class seems thin
                int type = (device.getBluetoothClass().getDeviceClass() == 0 ||
                            device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.Major.UNCATEGORIZED)
                        ?  device.getBluetoothClass().getMajorDeviceClass()
                        : device.getBluetoothClass().getDeviceClass();

                if (DEBUG_BLUETOOTH_DATA) {
                    MainActivity.info("LE deviceName: " + ssid
                            + "\n\taddress: " + bssid
                            + "\n\tname: " + scanRecord.getDeviceName() + " (vs. "+device.getName()+")"
                            + "\n\tadName: " + adDeviceName
                            + "\n\tclass:" + DEVICE_TYPE_LEGEND.get(device.getBluetoothClass().getDeviceClass())+ "("
                            + device.getBluetoothClass() + ")"
                            + "\n\ttype:" + device.getType()
                            + "\n\tRSSI:" + scanResult.getRssi()
                            //+ "\n\tTX power:" + scanRecord.getTxPowerLevel() //THIS IS ALWAYS GARBAGE
                            //+ "\n\tbytes: " + Arrays.toString(scanRecord.getBytes())
                            );
                    if ((scanRecord != null) && (scanRecord.getServiceUuids() != null)) {
                        if (adData != null) {
                            final List<java.util.UUID> uuids = adData.getUuids();
                            if (uuids != null) {
                                for (ParcelUuid uuid: scanRecord.getServiceUuids()) {
                                    if (uuids.contains(uuid.getUuid())) {
                                        MainActivity.info("\n\t\tScan Record adRecord match: "+uuid.toString());
                                    } else {
                                        MainActivity.error("\n\t\tScan Record UUID not present in adRecord: "+uuid.toString());
                                    }
                                }
                            }
                        } else {
                            MainActivity.error("Scan Record uuids present, but not adData uuids");
                            for (ParcelUuid uuid: scanRecord.getServiceUuids()) {
                                MainActivity.info("\n\t\t"+uuid.toString());
                            }
                        }
                    }
                }
                try {
                    //TODO: not seeing a lot of value from these checks yet (vs. the adData name extraction above)
                    final BluetoothLeDevice deviceLe = new BluetoothLeDevice(device, scanResult.getRssi(),
                            scanRecord.getBytes(), System.currentTimeMillis());
                    final AdRecordStore adRecordStore = deviceLe.getAdRecordStore();
                    for (int i = 0; i < 200; i++) {
                        if (!adRecordStore.isRecordPresent(i)) {
                            continue;
                        }
                        final AdRecord adRecord = adRecordStore.getRecord(i);
                        if (DEBUG_BLUETOOTH_DATA) {
                            MainActivity.info("LE adRecord(" + i + "): " + adRecord);
                        }
                    }
                } catch (Exception ex) {
                    //TODO: so this happens:
                    MainActivity.warn("failed to parse LeDevice from ScanRecord", ex);
                    //parseScanRecordAsSparseArray explodes on array indices
                }

                final String capabilities = DEVICE_TYPE_LEGEND.get(device.getBluetoothClass().getDeviceClass()) + /*"("
                        + device.getBluetoothClass().getMajorDeviceClass() + ":"
                        + device.getBluetoothClass().getDeviceClass() + ") " +*/ " [LE]";
                final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
                //ALIBI: shamelessly re-using frequency here for device type.
                final Network network = addOrUpdateBt(bssid, ssid, type, capabilities,
                        scanResult.getRssi(),
                        NetworkType.BLE, location, prefs);
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
            //ALIBI: since calls are triggered off of the WiFi Receiver delay, we're just introducing a retirement counter for BT
            btCount++;
        } else {
            if (DEBUG_BLUETOOTH_DATA) {
                MainActivity.info("skipping bluetooth scan; discover already in progress (last scan started "+(System.currentTimeMillis()-lastDiscoveryAt)+"ms ago)");
            }
        }

        if (Build.VERSION.SDK_INT >= 21) {
            final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner == null) {
                MainActivity.info("bluetoothLeScanner is null");
            }  else {
                if (scanning.compareAndSet(false, true)) {
                    final ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
                    scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
                    //TODO: make settable? NOTE: unset, you'll never get batch results, even with LOWER_POWER above
                    //  this is effectively how often we update the display
                    scanSettingsBuilder.setReportDelay(15000);
                    bluetoothLeScanner.startScan(
                            Collections.<ScanFilter>emptyList(), scanSettingsBuilder.build(), scanCallback);

                } else {
                    bluetoothLeScanner.flushPendingScanResults(scanCallback);
                }
            }
        }
        final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        final boolean showCurrent = prefs.getBoolean( ListFragment.PREF_SHOW_CURRENT, true );

        if (showCurrent && listAdapter != null && btCount > EMPTY_BT_THRESHOLD) {
            //ALIBI: not super elegant, but this gives devices a longer "decay".
            // Could try incrementing counter on scan finished instead of successful scan start?
            btCount = 0;
            listAdapter.clearBluetooth();
        }

        /*
        Paired device check?
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : pairedDevices) {
            MainActivity.info("\tpareid device: "+device.getAddress()+" - "+device.getName() + device.getBluetoothClass());
            //BluetoothClass bluetoothClass = device.getBluetoothClass();
        }*/
    }

    public void stopScanning() {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();

            if (Build.VERSION.SDK_INT >= 21) {
                final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                if (bluetoothLeScanner != null && scanning.compareAndSet(true, false)) {
                    bluetoothLeScanner.stopScan(scanCallback);
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

        final String action = intent.getAction();
        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final BluetoothClass btClass = intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);
            int  rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,Short.MIN_VALUE);

            final String bssid = device.getAddress();
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
                        + "\n\tclass:" + DEVICE_TYPE_LEGEND.get(type)
                        + "("+type+")"
                        + "\n\tbondState: " + device.getBondState();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                    log += "\n\tuuids: " + device.getUuids();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    log += "\n\ttype:" + device.getType();
                }

                MainActivity.info(log);
            }

            final String capabilities = DEVICE_TYPE_LEGEND.get(type)
                    /*+ " (" + device.getBluetoothClass().getMajorDeviceClass()
                    + ":" +device.getBluetoothClass().getDeviceClass() + ")"*/
                    + ";" + device.getBondState();
            final GPSListener gpsListener = mainActivity.getGPSListener();

            final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
            Location location = null;
            if (gpsListener != null) {
                final long gpsTimeout = prefs.getLong(ListFragment.PREF_GPS_TIMEOUT, GPSListener.GPS_TIMEOUT_DEFAULT);
                final long netLocTimeout = prefs.getLong(ListFragment.PREF_NET_LOC_TIMEOUT, GPSListener.NET_LOC_TIMEOUT_DEFAULT);
                gpsListener.checkLocationOK(gpsTimeout, netLocTimeout);
                location = gpsListener.getLocation();
            } else {
                MainActivity.warn("null gpsListener in BTR onReceive");
            }

            //ALIBI: shamelessly re-using frequency here for device type.
            final Network network =  addOrUpdateBt(bssid, ssid, type, capabilities, rssi, NetworkType.BT, location, prefs);

            final long newBtCount = dbHelper.getNewBtCount();
            ListFragment.lameStatic.newBt = newBtCount;
            ListFragment.lameStatic.runBt = runNetworks.size();

            sort(prefs);
            listAdapter.notifyDataSetChanged();
        }
    }

    /**
     * TODO: DRY this up with the sort in WifiReceiver?
     * @param prefs
     */
    private void sort(final SharedPreferences prefs) {
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
    }

    public void setListAdapter( final NetworkListAdapter listAdapter ) {
        this.listAdapter = listAdapter;
    }

    public int getRunNetworkCount() {
        return runNetworks.size();
    }

    private Network addOrUpdateBt(final String bssid, final String ssid,
                                    final int frequency, /*final String networkTypeName*/final String capabilities,
                                    final int strength, final NetworkType type,
                                    final Location location, SharedPreferences prefs) {

        //final String capabilities = networkTypeName + ";" + operator;

        final ConcurrentLinkedHashMap<String, Network> networkCache = MainActivity.getNetworkCache();
        final boolean showCurrent = prefs.getBoolean(ListFragment.PREF_SHOW_CURRENT, true);

        final boolean newForRun = runNetworks.add(bssid);

        Network network = networkCache.get(bssid);

        if (network == null) {
            //MainActivity.info("new BT net: "+bssid);
            network = new Network(bssid, ssid, frequency, capabilities, strength, type);
            networkCache.put(bssid, network);
        } else if (NetworkType.BLE.equals(type) && NetworkType.BT.equals(network.getType())) {
            //detected via standard bluetooth, updated as LE (LE should win)
            //DEBUG: MainActivity.info("had a BC record, moving to BLE: "+network.getBssid());
            String mergedSsid = (ssid == null || ssid.isEmpty()) ? network.getSsid() : ssid;
            int mergedDeviceType = (!isMiscOrUncategorized(network.getFrequency())?network.getFrequency():frequency);

            //TODO: We need a way to update the existing network record
            network.setSsid(mergedSsid);
            network.setFrequency(mergedDeviceType);
            network.setType(NetworkType.BLE);
        } else {
            //DEBUG: MainActivity.info("existing BT net");
            //TODO: is there any freq/channel info in BT at all??
            //TODO: update capabilities? only if was Misc/Uncategorized, now recognized?
            //network.setCapabilities(capabilities);
            network.setLevel(strength);
            network.setFrequency(frequency);
            if (null != ssid) {
                network.setSsid(ssid);
            }

        }

        final boolean ssidSpeak = prefs.getBoolean(ListFragment.PREF_SPEAK_SSID, false)
                && !mainActivity.isMuted();

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
        final Matcher bssidMatcher = mainActivity.getBssidFilterMatcher( ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS );
        final Matcher bssidDbMatcher = mainActivity.getBssidFilterMatcher( ListFragment.PREF_EXCLUDE_LOG_ADDRS );

        //Update display
        if (listAdapter != null) {
            if ( showCurrent || newForRun ) {
                if ( FilterMatcher.isOk( ssidMatcher, bssidMatcher, prefs, ListFragment.FILTER_PREF_PREFIX, network ) ) {
                    if (NetworkType.BT.equals(network.getType()) ) {
                        listAdapter.addBluetooth(network);
                    } else if (NetworkType.BLE.equals(network.getType())) {
                        listAdapter.addBluetoothLe(network);
                    }
                }

            } else {
                network.setLevel(strength != Integer.MAX_VALUE?strength:-113);
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
                dbHelper.addObservation(network, location, newForRun);
            }
        } else {
            // w/out location
            if (!matches) {
                dbHelper.pendingObservation(network, newForRun);
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