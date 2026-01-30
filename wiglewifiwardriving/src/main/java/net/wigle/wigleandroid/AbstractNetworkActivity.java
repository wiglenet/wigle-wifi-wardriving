package net.wigle.wigleandroid;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static net.wigle.wigleandroid.util.BluetoothUtil.BLE_SERVICE_CHARACTERISTIC_MAP;
import static net.wigle.wigleandroid.util.BluetoothUtil.BLE_STRING_CHARACTERISTIC_UUIDS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.ClipboardManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.github.razir.progressbutton.DrawableButton;
import com.github.razir.progressbutton.DrawableButtonExtensionsKt;
import com.github.razir.progressbutton.ProgressButtonHolderKt;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import net.wigle.wigleandroid.background.KmlSurveyWriter;
import net.wigle.wigleandroid.background.PooledQueryExecutor;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.listener.WiFiScanUpdater;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.MccMncRecord;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.OUI;
import net.wigle.wigleandroid.model.Observation;
import net.wigle.wigleandroid.ui.NetworkListUtil;
import net.wigle.wigleandroid.ui.ScreenChildActivity;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.ui.WiGLEConfirmationDialog;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.BluetoothUtil;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.Unit;

public abstract class AbstractNetworkActivity extends ScreenChildActivity implements DialogListener, WiFiScanUpdater {
    protected static final int MENU_RETURN = 11;
    protected static final int MENU_COPY = 12;
    //protected static final int NON_CRYPTO_DIALOG = 130;
    protected static final int SITE_SURVEY_DIALOG = 131;
    protected static final int MSG_OBS_UPDATE = 1;
    protected static final int MSG_OBS_DONE = 2;
    protected static final int DEFAULT_ZOOM = 18;
    // used for shutting extraneous activities down on an error
    protected static NetworkActivity networkActivity;
    protected final ConcurrentLinkedHashMap<LatLng, Integer> obsMap = new ConcurrentLinkedHashMap<>(512);
    protected final ConcurrentLinkedHashMap<LatLng, Observation> localObsMap = new ConcurrentLinkedHashMap<>(1024);
    protected Network network;
    protected int observations = 0;
    protected boolean isDbResult = false;
    protected NumberFormat numberFormat;

    /**
     * Observation query for network
     */
    final static String OBSERVATION_QUERY_SQL = "SELECT level,lat,lon FROM "
            + DatabaseHelper.LOCATION_TABLE + " WHERE bssid = ?" +
            " ORDER BY _id DESC limit ?" ;

    /**
     * draw observations on map (subclass-specific)
     */
    protected abstract void mapObservations();

    /**
     * Draw network on map (subclass-specific)
     * @param network The network
     * @param zoomLevel the intended zoom for the map
     * @param latest the latest position
     * @param rssi the latest RSSI value
     */
    protected abstract void mapWifiSeen(final Network network, final int zoomLevel, final LatLng latest, final int rssi);

    /**
     * remove dynamic data from map for subclasses
     */
    protected abstract void clearMap();

    /**
     * Destroys the map view for subclasses
     */
    protected abstract void destroyMapView();

    /**
     * Resumes the map view for subclasses
     */
    protected abstract void resumeMapView();

    /**
     * Pause the map view for subclasses
     */
    protected abstract void pauseMapView();

    /**
     * Save the map view state for subclasses
     * @param outState Bundle to save state to
     */
    protected abstract void saveMapViewState(@NonNull Bundle outState);

    /**
     * Handles low memory for subclasses
     */
    protected abstract void onLowMemoryMapView();

    /**
     * subclasses must specify a compatible base layout resource ID
     * @return the unique ID of the resource
     */
    protected abstract int getLayoutResourceId();

    @NonNull
    private static String getPnpValue(@NonNull BluetoothGattCharacteristic characteristic) {
        byte[] charValue = characteristic.getValue();
        final String vendorIdSrc = BluetoothUtil.getGattUint8(charValue[0]) == 1 ? "BLE" : "USB";
        final String vendorId = "" + BluetoothUtil.getGattUint8(charValue[1]);
        final String productString = "" + BluetoothUtil.getGattUint8(charValue[2]);
        final String productVersionString = "" + BluetoothUtil.getGattUint8(charValue[3]);
        return vendorIdSrc + ":" + vendorId + ":" + productString + ":" + productVersionString;
    }

    /**
     * optimistic signal weighting
     */
    public static float cleanSignal(Float signal) {
        float signalMemo = signal;
        if (signal == 0f) {
            return 100f;
        } else if (signal >= -200 && signal < 0) {
            signalMemo += 200f;
        } else if (signal <= 0 || signal > 200) {
            signalMemo = 100f;
        }
        if (signalMemo < 1f) {
            signalMemo = 1f;
        }
        return signalMemo;
    }

    private static String characteristicDisplayString(final Map<String, String> characteristicResults) {
        final StringBuilder results = new StringBuilder();
        boolean first = true;
        for (String key : characteristicResults.keySet()) {
            if (first) {
                first = false;
            } else {
                results.append("\n");
            }
            results.append(key).append(": ").append(characteristicResults.get(key));
        }
        return results.toString();
    }

    private static void checkChangeHandler(final boolean checked, final String bssid, final boolean ouiMode,
                                           final List<String> currentAddresses, String prefKey, SharedPreferences prefs) {
        if (bssid != null) {
            final String useBssid = ouiMode ? bssid.substring(0, 8).toUpperCase(Locale.ROOT) : bssid.toUpperCase(Locale.ROOT);
            final String entryText = useBssid.replace(":", "");
            if (checked) {
                MacFilterActivity.addEntry(currentAddresses,
                        prefs, entryText, prefKey, true);
            } else {
                if (currentAddresses.remove(useBssid)) {
                    MacFilterActivity.updateEntries(currentAddresses,
                            prefs, prefKey);
                } else {
                    Logging.error("Attempted to remove " + prefKey + ": " + useBssid + " but unable to match. (oui: " + ouiMode + ", " + currentAddresses + ")");
                }
            }
        } else {
            Logging.error("null BSSID value in checkChangeHandler - unable to modify.");
        }
    }

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("MissingPermission")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logging.info("NET: onCreate");
        super.onCreate(savedInstanceState);

        if (ListFragment.lameStatic.oui == null) {
            ListFragment.lameStatic.oui = new OUI(getAssets());
        }

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        // set language
        numberFormat = NumberFormat.getNumberInstance(MainActivity.getLocale(this, this.getResources().getConfiguration()));
        if (numberFormat instanceof DecimalFormat) {
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(2);
        }
        MainActivity.setLocale(this);
        setContentView(getLayoutResourceId());
        //networkActivity = this;

        EdgeToEdge.enable(this);
        final SharedPreferences prefs = getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        ThemeUtil.setNavTheme(getWindow(), this, prefs);

        View titleLayout = findViewById(R.id.na_network_detail_overlay);
        if (null != titleLayout) {
            ViewCompat.setOnApplyWindowInsetsListener(titleLayout, new OnApplyWindowInsetsListener() {
                        @Override
                        public @org.jspecify.annotations.NonNull WindowInsetsCompat onApplyWindowInsets(@org.jspecify.annotations.NonNull View v, @org.jspecify.annotations.NonNull WindowInsetsCompat insets) {
                            final Insets innerPadding = insets.getInsets(
                                    WindowInsetsCompat.Type.statusBars() |
                                            WindowInsetsCompat.Type.displayCutout());
                            v.setPadding(
                                    innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom
                            );
                            return insets;
                        }
                    }
            );
        }

        View bleToolsLayout = findViewById(R.id.ble_tools_row);
        if (null != bleToolsLayout) {
            ViewCompat.setOnApplyWindowInsetsListener(bleToolsLayout, new OnApplyWindowInsetsListener() {
                @Override
                public @org.jspecify.annotations.NonNull WindowInsetsCompat onApplyWindowInsets(@org.jspecify.annotations.NonNull View v, @org.jspecify.annotations.NonNull WindowInsetsCompat insets) {
                    final Insets innerPadding = insets.getInsets(
                            WindowInsetsCompat.Type.navigationBars());
                    v.setPadding(
                            innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom
                    );
                    return insets;
                }
            });
        }

        View filterToolsLayout = findViewById(R.id.filter_row);
        if (null != filterToolsLayout) {
            ViewCompat.setOnApplyWindowInsetsListener(filterToolsLayout, new OnApplyWindowInsetsListener() {
                @Override
                public @org.jspecify.annotations.NonNull WindowInsetsCompat onApplyWindowInsets(@org.jspecify.annotations.NonNull View v, @org.jspecify.annotations.NonNull WindowInsetsCompat insets) {
                    final Insets innerPadding = insets.getInsets(
                            WindowInsetsCompat.Type.navigationBars());
                    v.setPadding(
                            innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom
                    );
                    return insets;
                }
            });
        }

        final Intent intent = getIntent();
        final String bssid = intent.getStringExtra(ListFragment.NETWORK_EXTRA_BSSID);
        isDbResult = intent.getBooleanExtra(ListFragment.NETWORK_EXTRA_IS_DB_RESULT, false);
        Logging.info("bssid: " + bssid + " isDbResult: " + isDbResult);

        if (null != MainActivity.getNetworkCache()) {
            network = MainActivity.getNetworkCache().get(bssid);
        }

        TextView tv = findViewById(R.id.bssid);
        tv.setText(bssid);
        tv.setOnLongClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (null != clipboard) {
                clipboard.setText(bssid);
            }
            return true;
        });

        if (network == null) {
            Logging.info("no network found in cache for bssid: " + bssid);
        } else {
            // do gui work
            tv = findViewById(R.id.ssid);
            tv.setText(network.getSsid());
            tv.setOnLongClickListener(view -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (null != clipboard) {
                    clipboard.setText(network.getSsid());
                }
                return true;
            });

            final String ouiString = network.getOui(ListFragment.lameStatic.oui);
            tv = findViewById(R.id.oui);
            tv.setText(ouiString);

            final int image = NetworkListUtil.getImage(network);
            final ImageView ico = findViewById(R.id.wepicon);
            ico.setImageResource(image);

            final ImageView btico = findViewById(R.id.bticon);
            if (NetworkType.BT.equals(network.getType()) || NetworkType.BLE.equals(network.getType())) {
                btico.setVisibility(VISIBLE);
                Integer btImageId = NetworkListUtil.getBtImage(network);
                if (null == btImageId) {
                    btico.setVisibility(GONE);
                } else {
                    btico.setImageResource(btImageId);
                }
            } else {
                btico.setVisibility(GONE);
            }

            tv = findViewById(R.id.na_signal);
            final int level = network.getLevel();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tv.setTextColor(NetworkListUtil.getTextColorForSignal(this, level));
            } else {
                tv.setTextColor(NetworkListUtil.getSignalColor(level, false));
            }
            tv.setText(numberFormat.format(level));

            tv = findViewById(R.id.na_type);
            tv.setText(network.getType().name());

            tv = findViewById(R.id.na_firsttime);
            tv.setText(NetworkListUtil.getTime(network, true, getApplicationContext()));

            tv = findViewById(R.id.na_chan);
            Integer chan = network.getChannel();
            if (NetworkType.WIFI.equals(network.getType())) {
                chan = chan != null ? chan : network.getFrequency();
                tv.setText(numberFormat.format(chan));
            } else if (NetworkType.CDMA.equals(network.getType()) || chan == null) {
                tv.setText(getString(R.string.na));
            } else if (NetworkType.isBtType(network.getType())) {
                final String channelCode = NetworkType.channelCodeTypeForNetworkType(network.getType());
                tv.setText(String.format(MainActivity.getLocale(getApplicationContext(),
                                getApplicationContext().getResources().getConfiguration()),
                        "%s %d", (null == channelCode ? "" : channelCode), chan));
            } else {
                final String[] cellCapabilities = network.getCapabilities().split(";");
                tv.setText(String.format(MainActivity.getLocale(getApplicationContext(),
                                getApplicationContext().getResources().getConfiguration()),
                        "%s %s %d", cellCapabilities[0], NetworkType.channelCodeTypeForNetworkType(network.getType()), chan));
            }

            tv = findViewById(R.id.na_cap);
            tv.setText(network.getCapabilities().replace("][", "]  ["));

            final ImageView ppImg = findViewById(R.id.passpoint_logo_net);
            if (network.isPasspoint()) {
                ppImg.setVisibility(VISIBLE);
            } else {
                ppImg.setVisibility(GONE);
            }
            tv = findViewById(R.id.na_rcois);
            if (network.getRcois() != null) {
                tv.setText(network.getRcois());
            } else {
                TextView row = findViewById(R.id.na_rcoi_label);
                row.setVisibility(View.INVISIBLE);
            }

            if (NetworkType.isGsmLike(network.getType())) { // cell net types  with advanced data
                if ((bssid != null) && (bssid.length() > 5) && (bssid.indexOf('_') >= 5)) {
                    final String operatorCode = bssid.substring(0, bssid.indexOf("_"));

                    MccMncRecord rec = null;
                    if (operatorCode.length() == 6) {
                        final String mnc = operatorCode.substring(3);
                        final String mcc = operatorCode.substring(0, 3);

                        try {
                            final MainActivity.State s = MainActivity.getStaticState();
                            if (s != null && s.mxcDbHelper != null) {
                                rec = s.mxcDbHelper.networkRecordForMccMnc(mcc, mnc);
                            }
                        } catch (SQLException sqex) {
                            Logging.error("Unable to access Mxc Database: ", sqex);
                        }
                        if (rec != null) {
                            View v = findViewById(R.id.cell_info);
                            v.setVisibility(VISIBLE);
                            tv = findViewById(R.id.na_cell_status);
                            tv.setText(rec.getStatus());
                            tv = findViewById(R.id.na_cell_brand);
                            tv.setText(rec.getBrand());
                            tv = findViewById(R.id.na_cell_bands);
                            tv.setText(rec.getBands());
                            if (rec.getNotes() != null && !rec.getNotes().isEmpty()) {
                                v = findViewById(R.id.cell_notes_row);
                                v.setVisibility(VISIBLE);
                                tv = findViewById(R.id.na_cell_notes);
                                tv.setText(rec.getNotes());
                            }
                        }
                    }
                } else {
                    Logging.warn("unable to get operatorCode for " + bssid);
                }
            }

            if (NetworkType.isBtType(network.getType())) {
                View v = findViewById(R.id.ble_info);
                v.setVisibility(VISIBLE);
                if (network.getBleMfgrId() != null || network.getBleMfgr() != null) {
                    v = findViewById(R.id.ble_vendor_row);
                    v.setVisibility(VISIBLE);
                    tv = findViewById(R.id.na_ble_vendor_id);
                    tv.setText((null != network.getBleMfgrId()) ? "0x" + String.format("%04X", network.getBleMfgrId()) : "-");
                    tv = findViewById(R.id.na_ble_vendor_lookup);
                    tv.setText(network.getBleMfgr());
                }

                List<String> serviceUuids = network.getBleServiceUuids();
                if (null != serviceUuids && !serviceUuids.isEmpty()) {
                    v = findViewById(R.id.ble_services_row);
                    v.setVisibility(VISIBLE);
                    tv = findViewById(R.id.na_ble_service_uuids);
                    tv.setText(serviceUuids.toString());
                }
            }
            setupMap(network, savedInstanceState, prefs);
            // kick off the query now that we have our map
            setupButtons(network, prefs);
            if (NetworkType.BLE.equals(network.getType())) {
                setupBleInspection(this, network);
            } else {
                if (bleToolsLayout != null) {
                    bleToolsLayout.setVisibility(GONE);
                }
            }
            setupQuery();
        }
    }

    @Override
    public void onDestroy() {
        Logging.info("NET: onDestroy");
        destroyMapView();
        networkActivity = null;
        super.onDestroy();
    }

    @Override
    public void onResume() {
        Logging.info("NET: onResume");
        super.onResume();
        resumeMapView();
    }

    @Override
    public void onPause() {
        Logging.info("NET: onPause");
        pauseMapView();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        Logging.info("NET: onSaveInstanceState");
        super.onSaveInstanceState(outState);
        saveMapViewState(outState);
    }

    @Override
    public void onLowMemory() {
        Logging.info("NET: onLowMemory");
        onLowMemoryMapView();
        super.onLowMemory();
    }

    @SuppressLint("HandlerLeak")
    protected void setupQuery() {
        // what runs on the gui thread
        final Handler handler = new Handler() {
            @Override
            public void handleMessage( final Message msg ) {
                final TextView tv = findViewById( R.id.na_observe );
                if ( msg.what == MSG_OBS_UPDATE ) {
                    tv.setText( numberFormat.format( observations ));
                } else if ( msg.what == MSG_OBS_DONE ) {
                    tv.setText( numberFormat.format( observations ) );
                    mapObservations();
                }
            }
        };

        PooledQueryExecutor.enqueue( new PooledQueryExecutor.Request( OBSERVATION_QUERY_SQL,
                new String[]{network.getBssid(), obsMap.maxSize()+""}, new PooledQueryExecutor.ResultHandler() {
            @Override
            public boolean handleRow( final Cursor cursor ) {
                observations++;
                obsMap.put( new net.wigle.wigleandroid.model.LatLng( cursor.getFloat(1), cursor.getFloat(2) ), cursor.getInt(0) );
                if ( ( observations % 10 ) == 0 ) {
                    // change things on the gui thread
                    handler.sendEmptyMessage( MSG_OBS_UPDATE );
                }
                return true;
            }

            @Override
            public void complete() {
                handler.sendEmptyMessage( MSG_OBS_DONE );
            }
        }, ListFragment.lameStatic.dbHelper ));
        //ListFragment.lameStatic.dbHelper.addToQueue( request );
    }

    private void setupBleInspection(Activity activity, final Network network) {
        View interrogateView = findViewById(R.id.ble_tools_row);
        if (interrogateView != null) {
            interrogateView.setVisibility(VISIBLE);
        }
        final AtomicBoolean done = new AtomicBoolean(false);
        final Button pair = findViewById(R.id.query_ble_network);
        final View charView = findViewById(R.id.ble_chars_row);
        final TextView charContents = findViewById(R.id.ble_chars_content);
        if (null != pair) {
            ProgressButtonHolderKt.bindProgressButton(this, pair);
            final AtomicBoolean found = new AtomicBoolean(false);
            Set<BluetoothGattCharacteristic> characteristicsToQuery = new HashSet<>();
            ConcurrentHashMap<String, String> characteristicResults = new ConcurrentHashMap<>();
            final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
                @SuppressLint("MissingPermission")
                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
                    final StringBuffer displayMessage = new StringBuffer();
                    for (BluetoothGattService service : gatt.getServices()) {
                        final String serviceId = service.getUuid().toString().substring(4, 8);
                        final String serviceTitle = MainActivity.getMainActivity()
                                .getBleService(serviceId.toUpperCase());
                        if (service.getUuid() != null) {
                            Map<UUID, String> currentMap = BLE_SERVICE_CHARACTERISTIC_MAP.get(service.getUuid());
                            if (currentMap != null) {
                                for (UUID key : currentMap.keySet()) {
                                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(key);
                                    if (null != characteristic) {
                                        //DEBUG: Logging.error("enqueueing: " + currentMap.get(key));
                                        characteristicsToQuery.add(characteristic);
                                    } else {
                                        Logging.info(currentMap.get(key) + " is null");
                                    }
                                }
                            } else {
                                Logging.debug("Unhandled service: " + serviceTitle + " (" + serviceId + ")");
                            }
                        }

                        if (null != serviceTitle) {
                            int lastServicePeriod = serviceTitle.lastIndexOf(".");
                            displayMessage.append(lastServicePeriod == -1
                                            ? serviceTitle : serviceTitle.substring(lastServicePeriod + 1))
                                    .append(" (0x").append(serviceId.toUpperCase()).append(")\n");
                            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                                final String characteristicUuid = characteristic.getUuid().toString();
                                if (characteristicUuid.length() >= 8) {
                                    final String charId = characteristicUuid.substring(4, 8);
                                    String charTitle = MainActivity.getMainActivity()
                                            .getBleCharacteristic(charId);
                                    if (null != charTitle) {
                                        int lastCharPeriod = charTitle.lastIndexOf(".");
                                        displayMessage.append("\t").append(lastCharPeriod == -1
                                                        ? charTitle : charTitle.substring(lastCharPeriod + 1))
                                                .append(" (0x").append(charId.toUpperCase()).append(")\n");
                                    }
                                }
                            }
                        }
                    }
                    if (!characteristicsToQuery.isEmpty()) {
                        BluetoothGattCharacteristic first = characteristicsToQuery.iterator().next();
                        characteristicsToQuery.remove(first);
                        gatt.readCharacteristic(first);
                    } else {
                        found.set(false);
                        done.set(true);
                    }
                    runOnUiThread(() -> WiGLEToast.showOverActivity(
                            activity, R.string.btloc_title, displayMessage.toString(), Toast.LENGTH_LONG)
                    );
                }

                @SuppressLint("MissingPermission")
                @Override
                public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
                    super.onCharacteristicRead(gatt, characteristic, value, status);
                    if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getValue() != null) {
                        Integer titleResourceStringId = BLE_STRING_CHARACTERISTIC_UUIDS.get(characteristic.getUuid());
                        if (null != titleResourceStringId) {
                            // common case: this is a string value characteristic.
                            final String characteristicStringValue = new String(characteristic.getValue());
                            characteristicResults.put(getString(titleResourceStringId), characteristicStringValue);
                        } else if (UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb").equals(characteristic.getUuid())) {
                            //ALIBI: PnP value has a slightly complex decoding
                            final String pnpValue = AbstractNetworkActivity.getPnpValue(characteristic);
                            characteristicResults.put(getString(R.string.ble_pnp_title), pnpValue);
                        } else if (UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb").equals(characteristic.getUuid())) {
                            //ALIBI: this could be added to the stringCharacteristicUuids map, but we're leaving it its own case in case we want to update the network SSID value
                            final String name = new String(characteristic.getValue());
                            characteristicResults.put(getString(R.string.ble_name_title), name);
                            // NB: _could_ replace BT name with the discovered dev name here, but is that useful?
                            //if (null == network.getSsid() || network.getSsid().isBlank()) {
                            //    network.setSsid(name);
                            //}
                        } else if (UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb").equals(characteristic.getUuid())) {
                            final int level = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                            characteristicResults.put("\uD83D\uDD0B", "" + level); //ALIBI: skipping language files since this is an emoji
                        } else if (UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb").equals(characteristic.getUuid())) {
                            //ALIBI: appearance value has a slightly complex decoding
                            byte[] charValue = characteristic.getValue();
                            int appearanceValue = BluetoothUtil.getGattUint16(charValue);
                            int category = (appearanceValue >> 6) & 0xFF;
                            int subcategory = appearanceValue & 0x3F;
                            final String categoryHex = Integer.toHexString(category);
                            final String subcategoryHex = Integer.toHexString(subcategory);
                            final String appearanceString = MainActivity.getMainActivity().getBleAppearance(category, subcategory);
                            //DEBUG: Logging.info("APPEARANCE: " + categoryHex + ":" + subcategoryHex + " - " + appearanceString + " from "+ Hex.bytesToStringLowercase(characteristic.getValue()) + ": "+Integer.toHexString(appearanceValue));
                            characteristicResults.put(getString(R.string.ble_appearance_title), appearanceString + "( 0x" + categoryHex + ": 0x" + subcategoryHex + ")");
                        } else {
                            //TODO: heart rate will land here for now
                            Logging.info(characteristic.getUuid().toString() + ": " + new String(characteristic.getValue()));
                        }

                        if (!characteristicsToQuery.isEmpty()) {
                            BluetoothGattCharacteristic next = characteristicsToQuery.iterator().next();
                            characteristicsToQuery.remove(next);
                            gatt.readCharacteristic(next);
                        } else {
                            gatt.disconnect();
                            gatt.close();
                            if (characteristicResults.isEmpty()) {
                                runOnUiThread(() -> {
                                    WiGLEToast.showOverActivity(activity, R.string.btloc_title, "No results read", Toast.LENGTH_LONG);
                                    hideProgressCenter(pair);
                                });
                            } else {
                                final String results = AbstractNetworkActivity.characteristicDisplayString(characteristicResults);
                                runOnUiThread(() -> {
                                    charView.setVisibility(VISIBLE);
                                    charContents.setText(results);
                                    hideProgressCenter(pair);
                                });
                            }
                        }
                    }
                }

                @SuppressLint("MissingPermission")
                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt,
                                                    BluetoothGattCharacteristic characteristic) {
                    super.onCharacteristicChanged(gatt, characteristic);
                    Logging.info("CHARACTERISTIC CHANGED" + characteristic.getUuid().toString() + " :" + new String(characteristic.getValue()));
                    gatt.disconnect();
                    gatt.close();
                    found.set(false);
                    runOnUiThread(() -> WiGLEToast.showOverActivity(activity, R.string.btloc_title, "characteristic change."));
                }

                @SuppressLint("MissingPermission")
                @Override
                public void onServiceChanged(@NonNull BluetoothGatt gatt) {
                    super.onServiceChanged(gatt);
                    //DEBUG: Logging.info(gatt + " d!");
                    gatt.disconnect();
                    gatt.close();
                    found.set(false);
                    done.set(true);
                    runOnUiThread(() -> WiGLEToast.showOverActivity(activity, R.string.btloc_title, "service change."));
                }

                @SuppressLint("MissingPermission")
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices();
                        runOnUiThread(() -> WiGLEToast.showOverActivity(activity, R.string.btloc_title, "connected to device."));
                    } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                        Logging.info("Connecting...");
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                        Logging.info("Disconnecting...");
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gatt.disconnect();
                        gatt.close();
                        found.set(false);
                        done.set(true);
                        runOnUiThread(() -> {
                            WiGLEToast.showOverActivity(activity, R.string.btloc_title, "disconnected from device.");
                            hideProgressCenter(pair);
                            if (!characteristicsToQuery.isEmpty() && !characteristicResults.isEmpty()) {
                                //ALIBI: we were interrupted, but have some characteristics to show
                                final String results = AbstractNetworkActivity.characteristicDisplayString(characteristicResults);
                                runOnUiThread(() -> {
                                    charView.setVisibility(VISIBLE);
                                    charContents.setText(results);
                                    hideProgressCenter(pair);
                                });
                            }
                        });
                    } else {
                        Logging.info("GATT Characteristic status: " + status + " new: " + newState);
                    }
                }
            };

            final BluetoothAdapter.LeScanCallback scanCallback = getLeScanCallback(network, found, done, gattCallback);

            pair.setOnClickListener(buttonView -> {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    if (bluetoothAdapter != null) {
                        done.set(false);
                        found.set(false);
                        charView.setVisibility(GONE);
                        showProgressCenter(pair);
                        bluetoothAdapter.startLeScan(scanCallback); //TODO: should already be going on
                    }
                }
            });
        }
    }

    private BluetoothAdapter.LeScanCallback getLeScanCallback(Network network, AtomicBoolean found, AtomicBoolean done, BluetoothGattCallback gattCallback) {
        final ScanCallback leScanCallback = new ScanCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                if (!done.get()) {
                    if (null != result) {
                        final BluetoothDevice device = result.getDevice();
                        if (device.getAddress().compareToIgnoreCase(network.getBssid()) == 0) {
                            if (found.compareAndSet(false, true)) {
                                //DEBUG: Logging.info("** MATCHED DEVICE IN NetworkActivity: " + network.getBssid() + " **");
                                final BluetoothGatt btGatt = device.connectGatt(getApplicationContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE);
                            }
                        }
                    }
                }
            }

            @Override
            @SuppressLint("MissingPermission")
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
                if (!done.get()) {
                    if (results != null) {
                        for (ScanResult result : results) {
                            final BluetoothDevice device = result.getDevice();
                            if (device.getAddress().compareToIgnoreCase(network.getBssid()) == 0) {
                                if (found.compareAndSet(false, true)) {
                                    //DEBUG: Logging.info("** MATCHED DEVICE IN NetworkActivity: " + network.getBssid() + " **");
                                    final BluetoothGatt btGatt = device.connectGatt(getApplicationContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE);
                                    btGatt.discoverServices();
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Logging.info("LE failed before scan stop");
            }
        };


        return new BluetoothAdapter.LeScanCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
                if (!done.get()) {
                    if (bluetoothDevice.getAddress().compareToIgnoreCase(network.getBssid()) == 0) {
                        if (found.compareAndSet(false, true)) {
                            //DEBUG: Logging.info("** MATCHED DEVICE IN NetworkActivity: " + network.getBssid() + " **");
                            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                            if (bluetoothAdapter != null) {
                                bluetoothAdapter.getBluetoothLeScanner().stopScan(leScanCallback);
                                bluetoothAdapter.getBluetoothLeScanner().flushPendingScanResults(leScanCallback);
                            }
                            final BluetoothGatt btGatt = bluetoothDevice.connectGatt(getApplicationContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE);
                            //Logging.info("class: " + bluetoothDevice.getBluetoothClass().getMajorDeviceClass() + " (all " + bluetoothDevice.getBluetoothClass().getDeviceClass() + ") vs "+network.getCapabilities());
                            btGatt.discoverServices();
                        }
                    }
                }
            }
        };
    }

    protected LatLng computeBasicLocation(ConcurrentLinkedHashMap<LatLng, Integer> obsMap) {
        double latSum = 0.0;
        double lonSum = 0.0;
        double weightSum = 0.0;
        for (Map.Entry<LatLng, Integer> obs : obsMap.entrySet()) {
            if (null != obs.getKey()) {
                float cleanSignal = AbstractNetworkActivity.cleanSignal((float) obs.getValue());
                final double latV = obs.getKey().latitude;
                final double lonV = obs.getKey().longitude;
                if (Math.abs(latV) > 0.01d && Math.abs(lonV) > 0.01d) { // 0 GPS-coord check
                    cleanSignal *= cleanSignal;
                    latSum += (obs.getKey().latitude * cleanSignal);
                    lonSum += (obs.getKey().longitude * cleanSignal);
                    weightSum += cleanSignal;
                }
            }
        }
        double trilateratedLatitude = 0;
        double trilateratedLongitude = 0;
        if (weightSum > 0) {
            trilateratedLatitude = latSum / weightSum;
            trilateratedLongitude = lonSum / weightSum;
        }
        return new LatLng(trilateratedLatitude, trilateratedLongitude);
    }

    private LatLng computeObservationLocation(ConcurrentLinkedHashMap<LatLng, Observation> obsMap) {
        double latSum = 0.0;
        double lonSum = 0.0;
        double weightSum = 0.0;
        for (Map.Entry<LatLng, Observation> obs : obsMap.entrySet()) {
            if (null != obs.getKey()) {
                float cleanSignal = AbstractNetworkActivity.cleanSignal((float) obs.getValue().getRssi());
                final double latV = obs.getKey().latitude;
                final double lonV = obs.getKey().longitude;
                if (Math.abs(latV) > 0.01d && Math.abs(lonV) > 0.01d) { // 0 GPS-coord check
                    cleanSignal *= cleanSignal;
                    latSum += (obs.getKey().latitude * cleanSignal);
                    lonSum += (obs.getKey().longitude * cleanSignal);
                    weightSum += cleanSignal;
                }
            }
        }
        double trilateratedLatitude = 0;
        double trilateratedLongitude = 0;
        if (weightSum > 0) {
            trilateratedLatitude = latSum / weightSum;
            trilateratedLongitude = lonSum / weightSum;
        }
        return new LatLng(trilateratedLatitude, trilateratedLongitude);
    }

    protected int computeZoom(ConcurrentLinkedHashMap<LatLng, Integer> obsMap, final LatLng centroid) {
        float maxDist = 0f;
        for (Map.Entry<LatLng, Integer> obs : obsMap.entrySet()) {
            float[] res = new float[3];
            Location.distanceBetween(centroid.latitude, centroid.longitude, obs.getKey().latitude, obs.getKey().longitude, res);
            if (res[0] > maxDist) {
                maxDist = res[0];
            }
        }
        Logging.info("max dist: " + maxDist);
        if (maxDist < 135) {
            return 18;
        } else if (maxDist < 275) {
            return 17;
        } else if (maxDist < 550) {
            return 16;
        } else if (maxDist < 1100) {
            return 15;
        } else if (maxDist < 2250) {
            return 14;
        } else if (maxDist < 4500) {
            return 13;
        } else if (maxDist < 9000) {
            return 12;
        } else if (maxDist < 18000) {
            return 11;
        } else if (maxDist < 36000) {
            // ALiBI: cells can be this big.
            return 10;
        } else {
            return DEFAULT_ZOOM;
        }
    }

    protected abstract void setupMap(Network network, Bundle savedInstanceState, SharedPreferences prefs);

    private void setupButtons(final Network network, final SharedPreferences prefs) {
        final ArrayList<String> hideAddresses = addressListForPref(prefs, PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS);
        final ArrayList<String> blockAddresses = addressListForPref(prefs, PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS);
        final ArrayList<String> alertAddresses = addressListForPref(prefs, PreferenceKeys.PREF_ALERT_ADDRS);

        ImageButton back = findViewById(R.id.network_back_button);
        if (null != back) {
            back.setOnClickListener(v -> finish());
        }
        if (!NetworkType.WIFI.equals(network.getType()) && !NetworkType.isBtType(network.getType())) {
            final View filterRowView = findViewById(R.id.filter_row);
            filterRowView.setVisibility(GONE);
        } else {
            final CheckBox hideMacBox = findViewById(R.id.hide_mac);
            final CheckBox hideOuiBox = findViewById(R.id.hide_oui);
            final CheckBox disableLogMacBox = findViewById(R.id.block_mac);
            final CheckBox disableLogOuiBox = findViewById(R.id.block_oui);
            final CheckBox alertMacBox = findViewById(R.id.alert_mac);
            final CheckBox alertOuiBox = findViewById(R.id.alert_oui);


            if ((null == network.getBssid()) || (network.getBssid().length() < 17)) {
                hideMacBox.setEnabled(false);
                disableLogMacBox.setEnabled(false);
                alertMacBox.setEnabled(false);
            } else {
                if (hideAddresses.contains(network.getBssid().toUpperCase(Locale.ROOT))) {
                    hideMacBox.setChecked(true);
                }
                if (blockAddresses.contains(network.getBssid().toUpperCase(Locale.ROOT))) {
                    disableLogMacBox.setChecked(true);
                }
                if (alertAddresses.contains(network.getBssid().toUpperCase(Locale.ROOT))) {
                    alertMacBox.setChecked(true);
                }
            }

            if ((null == network.getBssid()) || (network.getBssid().length() < 8)) {
                hideOuiBox.setEnabled(false);
                disableLogOuiBox.setEnabled(false);
                alertOuiBox.setEnabled(false);
            } else {
                final String ouiString = network.getBssid().toUpperCase(Locale.ROOT).substring(0, 8);
                if (hideAddresses.contains(ouiString)) {
                    hideOuiBox.setChecked(true);
                }
                if (blockAddresses.contains(ouiString)) {
                    disableLogOuiBox.setChecked(true);
                }
                if (alertAddresses.contains(ouiString)) {
                    alertOuiBox.setChecked(true);
                }
            }

            hideMacBox.setOnCheckedChangeListener((compoundButton, checked) -> AbstractNetworkActivity.checkChangeHandler(checked, network.getBssid(), false, hideAddresses,
                    PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS, prefs));

            hideOuiBox.setOnCheckedChangeListener((compoundButton, checked) -> AbstractNetworkActivity.checkChangeHandler(
                    checked, network.getBssid(), true, hideAddresses,
                    PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS, prefs));

            disableLogMacBox.setOnCheckedChangeListener((compoundButton, checked) -> AbstractNetworkActivity.checkChangeHandler(
                    checked, network.getBssid(), false, blockAddresses,
                    PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS, prefs));

            disableLogOuiBox.setOnCheckedChangeListener((compoundButton, checked) -> AbstractNetworkActivity.checkChangeHandler(
                    checked, network.getBssid(), true, blockAddresses,
                    PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS, prefs));

            alertMacBox.setOnCheckedChangeListener((compoundButton, checked) -> AbstractNetworkActivity.checkChangeHandler(
                    checked, network.getBssid(), false, alertAddresses,
                    PreferenceKeys.PREF_ALERT_ADDRS, prefs));

            alertOuiBox.setOnCheckedChangeListener((compoundButton, checked) -> AbstractNetworkActivity.checkChangeHandler(
                    checked, network.getBssid(), true, alertAddresses,
                    PreferenceKeys.PREF_ALERT_ADDRS, prefs));

            final Button startSurveyButton = findViewById(R.id.start_survey);
            final Button endSurveyButton = findViewById(R.id.end_survey);
            MainActivity.State state = MainActivity.getStaticState();
            startSurveyButton.setOnClickListener(buttonView -> {
                final FragmentActivity fa = this;
                //TODO: disabled until obsMap DB load complete?
                if (null != fa) {
                    final String message = String.format(getString(R.string.confirm_survey),
                            getString(R.string.end_survey), getString(R.string.nonstop));
                    WiGLEConfirmationDialog.createConfirmation(fa, message,
                            R.id.nav_data, SITE_SURVEY_DIALOG);
                }
            });
            endSurveyButton.setOnClickListener(buttonView -> {
                startSurveyButton.setVisibility(VISIBLE);
                endSurveyButton.setVisibility(GONE);
                if (null != state) {
                    state.wifiReceiver.unregisterWiFiScanUpdater();
                    try {
                        KmlSurveyWriter kmlWriter = new KmlSurveyWriter(MainActivity.getMainActivity(), ListFragment.lameStatic.dbHelper,
                                "KmlSurveyWriter", true, network.getBssid(), localObsMap.values());
                        kmlWriter.start();
                    } catch (IllegalArgumentException e) {
                        Logging.error("Failed to start KML writer: ", e);
                    }
                    //TODO: do we want the obsMap back?
                }
            });

        }
    }

    private ArrayList<String> addressListForPref(final SharedPreferences prefs, final String key) {
        Gson gson = new Gson();
        String[] values = gson.fromJson(prefs.getString(key, "[]"), String[].class);
        return new ArrayList<>(Arrays.asList(values));
    }

    @Override
    public void handleDialog(final int dialogId) {
        switch (dialogId) {
            case SITE_SURVEY_DIALOG:
                MainActivity.State state = MainActivity.getStaticState();
                final Button startSurveyButton = findViewById(R.id.start_survey);
                final Button endSurveyButton = findViewById(R.id.end_survey);
                if (null != state) {
                    startSurveyButton.setVisibility(GONE);
                    endSurveyButton.setVisibility(VISIBLE);
                    obsMap.clear();
                    final String[] currentList = new String[]{network.getBssid()};
                    final Set<String> registerSet = new HashSet<>(Arrays.asList(currentList));
                    state.wifiReceiver.registerWiFiScanUpdater(this, registerSet);
                    clearMap();
                }
                break;
            default:
                Logging.warn("Network unhandled dialogId: " + dialogId);
        }
    }

    @Override
    public void handleWiFiSeen(String bssid, Integer rssi, Location location) {
        LatLng latest = new LatLng(location.getLatitude(), location.getLongitude());
        localObsMap.put(latest, new Observation(rssi, location.getLatitude(), location.getLongitude(), location.getAltitude()));
        final LatLng estCentroid = computeObservationLocation(localObsMap);
        final int zoomLevel = computeZoom(obsMap, estCentroid);
        mapWifiSeen(network, zoomLevel, latest, rssi);
    }

    /* Creates the menu items */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuItem item = menu.add(0, MENU_COPY, 0, getString(R.string.menu_copy_network));
        item.setIcon(android.R.drawable.ic_menu_save);

        item = menu.add(0, MENU_RETURN, 0, getString(R.string.menu_return));
        item.setIcon(android.R.drawable.ic_menu_revert);

        return true;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RETURN:
                // call over to finish
                finish();
                return true;
            case MENU_COPY:
                // copy the netid
                if (network != null) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setText(network.getBssid());
                }
                return true;
            case android.R.id.home:
                // MainActivity.info("NETWORK: actionbar back");
                if (isDbResult) {
                    // don't go back to main activity
                    finish();
                    return true;
                }
        }
        return false;
    }

    private void showProgressCenter(final Button button) {
        DrawableButtonExtensionsKt.showProgress(button, progressParams -> {
            progressParams.setProgressColor(Color.WHITE);
            progressParams.setGravity(DrawableButton.GRAVITY_CENTER);
            return Unit.INSTANCE;
        });
        button.setEnabled(false);
    }

    private void hideProgressCenter(final Button button) {
        button.setEnabled(true);
        DrawableButtonExtensionsKt.hideProgress(button, R.string.interrogate_ble);
    }

    public static class CryptoDialog extends DialogFragment {
        public static CryptoDialog newInstance(final Network network) {
            final CryptoDialog frag = new CryptoDialog();
            Bundle args = new Bundle();
            args.putString("ssid", network.getSsid());
            args.putString("capabilities", network.getCapabilities());
            args.putString("level", Integer.toString(network.getLevel()));
            frag.setArguments(args);
            return frag;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            final Dialog dialog = getDialog();
            View view = inflater.inflate(R.layout.cryptodialog, container);
            final Bundle args = getArguments();
            if (null != dialog) {
                dialog.setTitle(args != null ? args.getString("ssid") : "");
            }

            TextView text = view.findViewById(R.id.security);
            text.setText(args != null ? args.getString("capabilities") : "");

            text = view.findViewById(R.id.signal);
            text.setText(args != null ? args.getString("level") : "");

            final Button ok = view.findViewById(R.id.ok_button);

            final TextInputEditText password = view.findViewById(R.id.edit_password);
            password.addTextChangedListener(new SettingsFragment.SetWatcher() {
                @Override
                public void onTextChanged(final String s) {
                    if (!s.isEmpty()) {
                        ok.setEnabled(true);
                    }
                }
            });

            final CheckBox showpass = view.findViewById(R.id.showpass);
            showpass.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    password.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    password.setTransformationMethod(null);
                } else {
                    password.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    password.setTransformationMethod(
                            android.text.method.PasswordTransformationMethod.getInstance());
                }
            });

            //ALIBI: no longer supported in modern Android
            /*ok.setOnClickListener(buttonView -> {
                try {
                    final NetworkActivity networkActivity = (NetworkActivity) getActivity();
                    if (networkActivity != null && password.getText() != null) {
                        networkActivity.connectToNetwork(password.getText().toString());
                    }
                    if (null != dialog) {
                        dialog.dismiss();
                    }
                } catch (Exception ex) {
                    // guess it wasn't there anyways
                    Logging.info("exception dismissing crypto dialog: " + ex);
                }
            });*/

            Button cancel = view.findViewById(R.id.cancel_button);
            cancel.setOnClickListener(buttonView -> {
                try {
                    if (null != dialog) {
                        dialog.dismiss();
                    }
                } catch (Exception ex) {
                    // guess it wasn't there anyways
                    Logging.info("exception dismissing crypto dialog: " + ex);
                }
            });

            return view;
        }
    }
}
