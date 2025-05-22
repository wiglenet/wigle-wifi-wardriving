package net.wigle.wigleandroid;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static net.wigle.wigleandroid.util.BluetoothUtil.BLE_STRING_CHARACTERSITIC_UUIDS;

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
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentActivity;

import android.text.ClipboardManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.razir.progressbutton.DrawableButton;
import com.github.razir.progressbutton.DrawableButtonExtensionsKt;
import com.github.razir.progressbutton.ProgressButtonHolderKt;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import net.wigle.wigleandroid.background.KmlSurveyWriter;
import net.wigle.wigleandroid.background.PooledQueryExecutor;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.listener.WiFiScanUpdater;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
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

import kotlin.Unit;

@SuppressWarnings("deprecation")
public class NetworkActivity extends ScreenChildActivity implements DialogListener, WiFiScanUpdater {
    private static final int MENU_RETURN = 11;
    private static final int MENU_COPY = 12;
    private static final int NON_CRYPTO_DIALOG = 130;
    private static final int SITE_SURVEY_DIALOG = 131;


    private static final int MSG_OBS_UPDATE = 1;
    private static final int MSG_OBS_DONE = 2;

    private static final int DEFAULT_ZOOM = 18;

    private Network network;
    private MapView mapView;
    private int observations = 0;
    private boolean isDbResult = false;
    private final ConcurrentLinkedHashMap<LatLng, Integer> obsMap = new ConcurrentLinkedHashMap<>(512);
    private final ConcurrentLinkedHashMap<LatLng, Observation> localObsMap = new ConcurrentLinkedHashMap<>(1024);
    private NumberFormat numberFormat;

    // used for shutting extraneous activities down on an error
    public static NetworkActivity networkActivity;

        /** Called when the activity is first created. */
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
        MainActivity.setLocale( this );
        setContentView(R.layout.network);
        networkActivity = this;

        final SharedPreferences prefs = getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        ThemeUtil.setNavTheme(getWindow(), this, prefs);

        final Intent intent = getIntent();
        final String bssid = intent.getStringExtra( ListFragment.NETWORK_EXTRA_BSSID );
        isDbResult = intent.getBooleanExtra(ListFragment.NETWORK_EXTRA_IS_DB_RESULT, false);
        Logging.info( "bssid: " + bssid + " isDbResult: " + isDbResult);

        if (null != MainActivity.getNetworkCache()) {
            network = MainActivity.getNetworkCache().get(bssid);
        }

        TextView tv = findViewById( R.id.bssid );
        tv.setText( bssid );

        if ( network == null ) {
            Logging.info( "no network found in cache for bssid: " + bssid );
        } else {
            // do gui work
            tv = findViewById( R.id.ssid );
            tv.setText( network.getSsid() );

            final String ouiString = network.getOui(ListFragment.lameStatic.oui);
            tv = findViewById( R.id.oui );
            tv.setText( ouiString );

            final int image = NetworkListUtil.getImage( network );
            final ImageView ico = findViewById( R.id.wepicon );
            ico.setImageResource( image );

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

            tv = findViewById( R.id.na_signal );
            final int level = network.getLevel();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tv.setTextColor( NetworkListUtil.getTextColorForSignal(this, level));
            } else {
                tv.setTextColor( NetworkListUtil.getSignalColor( level, false) );
            }
            tv.setText( numberFormat.format( level ) );

            tv = findViewById( R.id.na_type );
            tv.setText( network.getType().name() );

            tv = findViewById( R.id.na_firsttime );
            tv.setText( NetworkListUtil.getTime(network, true, getApplicationContext()) );

            tv = findViewById( R.id.na_chan );
            Integer chan = network.getChannel();
            if ( NetworkType.WIFI.equals(network.getType()) ) {
                chan = chan != null ? chan : network.getFrequency();
                tv.setText(numberFormat.format(chan));
            } else if ( NetworkType.CDMA.equals(network.getType()) || chan == null) {
                tv.setText(getString(R.string.na));
            } else if (NetworkType.isBtType(network.getType())) {
                final String channelCode = NetworkType.channelCodeTypeForNetworkType(network.getType());
                tv.setText(String.format(MainActivity.getLocale(getApplicationContext(),
                                getApplicationContext().getResources().getConfiguration()),
                        "%s %d", (null == channelCode?"":channelCode), chan));
            } else {
                final String[] cellCapabilities = network.getCapabilities().split(";");
                tv.setText(String.format(MainActivity.getLocale(getApplicationContext(),
                        getApplicationContext().getResources().getConfiguration()),
                        "%s %s %d", cellCapabilities[0], NetworkType.channelCodeTypeForNetworkType(network.getType()), chan));
            }

            tv = findViewById( R.id.na_cap );
            tv.setText( network.getCapabilities().replace("][", "]  [") );

            tv = findViewById( R.id.na_rcois );
            if (network.getRcois() != null) {
                tv.setText( network.getRcois() );
            }
            else {
                TextView row = findViewById(R.id.na_rcoi_label);
                row.setVisibility(View.INVISIBLE);
            }

            if ( NetworkType.isGsmLike(network.getType())) { // cell net types  with advanced data
                if ((bssid != null) && (bssid.length() > 5) && (bssid.indexOf('_') >= 5)) {
                    final String operatorCode = bssid.substring(0, bssid.indexOf("_"));

                    MccMncRecord rec = null;
                    if (operatorCode.length() == 6) {
                        final String mnc = operatorCode.substring(3);
                        final String mcc = operatorCode.substring(0, 3);

                        //DEBUG: MainActivity.info("\t\tmcc: "+mcc+"; mnc: "+mnc);

                        try {
                            final MainActivity.State s = MainActivity.getStaticState();
                            if (s != null && s.mxcDbHelper != null) {
                                rec = s.mxcDbHelper.networkRecordForMccMnc(mcc, mnc);
                            }
                        } catch (SQLException sqex) {
                            Logging.error("Unable to access Mxc Database: ",sqex);
                        }
                        if (rec != null) {
                            View v = findViewById(R.id.cell_info);
                            v.setVisibility(VISIBLE);
                            tv = findViewById( R.id.na_cell_status );
                            tv.setText(rec.getStatus() );
                            tv = findViewById( R.id.na_cell_brand );
                            tv.setText(rec.getBrand());
                            tv = findViewById( R.id.na_cell_bands );
                            tv.setText( rec.getBands());
                            if (rec.getNotes() != null && !rec.getNotes().isEmpty()) {
                                v = findViewById(R.id.cell_notes_row);
                                v.setVisibility(VISIBLE);
                                tv = findViewById( R.id.na_cell_notes );
                                tv.setText( rec.getNotes());
                            }
                        }
                    }
                } else {
                    Logging.warn("unable to get operatorCode for "+bssid);
                }
            }

            if (NetworkType.isBtType(network.getType())) {
                View v = findViewById(R.id.ble_info);
                v.setVisibility(VISIBLE);
                if (network.getBleMfgrId() != null || network.getBleMfgr() != null) {
                    v = findViewById(R.id.ble_vendor_row);
                    v.setVisibility(VISIBLE);
                    tv = findViewById( R.id.na_ble_vendor_id );
                    tv.setText((null != network.getBleMfgrId())?network.getBleMfgrId()+"":"-");
                    tv = findViewById( R.id.na_ble_vendor_lookup );
                    tv.setText(network.getBleMfgr() );
                }

                List<String> serviceUuids = network.getBleServiceUuids();
                if (null != serviceUuids && !serviceUuids.isEmpty()) {
                    v = findViewById(R.id.ble_services_row);
                    v.setVisibility(VISIBLE);
                    tv = findViewById( R.id.na_ble_service_uuids );
                    tv.setText(serviceUuids.toString() );
                }
            }
            setupMap(network, savedInstanceState, prefs );
            // kick off the query now that we have our map
            setupButtons(network, prefs);
            if (NetworkType.BLE.equals(network.getType())) {
                setupBleInspection(this, network);
            } else {
                View interrogateView = findViewById(R.id.ble_tools_row);
                if (interrogateView != null) {
                    interrogateView.setVisibility(GONE);
                }
            }
            setupQuery();
        }
    }

    @Override
    public void onDestroy() {
        Logging.info("NET: onDestroy");
        networkActivity = null;
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        Logging.info("NET: onResume");
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        } else {
            final SharedPreferences prefs = getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            setupMap( network, null, prefs);
        }
    }

    @Override
    public void onPause() {
        Logging.info("NET: onPause");
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        Logging.info("NET: onSaveInstanceState");
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            try {
                mapView.onSaveInstanceState(outState);
            } catch (android.os.BadParcelableException bpe) {
                Logging.error("Exception saving NetworkActivity instance state: ",bpe);
                //this is really low-severity, since we can restore all state anyway
            }
        }
    }

    @Override
    public void onLowMemory() {
        Logging.info("NET: onLowMemory");
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @SuppressLint("HandlerLeak")
    private void setupQuery() {
        // what runs on the gui thread
        final Handler handler = new Handler() {
            @Override
            public void handleMessage( final Message msg ) {
                final TextView tv = findViewById( R.id.na_observe );
                if ( msg.what == MSG_OBS_UPDATE ) {
                    tv.setText( numberFormat.format( observations ));
                } else if ( msg.what == MSG_OBS_DONE ) {
                    tv.setText( numberFormat.format( observations ) );
                    // ALIBI:  assumes all observations belong to one "cluster" w/ a single centroid.
                    // we could check and perform multi-cluster here
                    // (get arithmetic mean, std-dev, try to do sigma-based partitioning)
                    // but that seems less likely w/ one individual's observations
                    final LatLng estCentroid = computeBasicLocation(obsMap);
                    final int zoomLevel = computeZoom(obsMap, estCentroid);
                    mapView.getMapAsync(googleMap -> {
                        int count = 0;
                        for (Map.Entry<LatLng, Integer> obs : obsMap.entrySet()) {
                            final LatLng latLon = obs.getKey();
                            final int level = obs.getValue();

                            // default to initial position
                            if (count == 0 && network.getLatLng() == null) {
                                final CameraPosition cameraPosition = new CameraPosition.Builder()
                                        .target(latLon).zoom(DEFAULT_ZOOM).build();
                                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                            }

                            BitmapDescriptor obsIcon = NetworkListUtil.getSignalBitmap(
                                    getApplicationContext(), level);

                            googleMap.addMarker(new MarkerOptions().icon(obsIcon)
                                    .position(latLon).zIndex(level));
                            count++;
                        }
                        // if we got a good centroid, display it and center on it
                        if (estCentroid.latitude != 0d && estCentroid.longitude != 0d) {
                            //TODO: improve zoom based on obs distances?
                            final CameraPosition cameraPosition = new CameraPosition.Builder()
                                    .target(estCentroid).zoom(zoomLevel).build();
                            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                            googleMap.addMarker(new MarkerOptions().position(estCentroid));
                        }
                        Logging.info("observation count: " + count);
                        if ( NetworkType.WIFI.equals(network.getType()) ) {
                            View v = findViewById(R.id.survey);
                            v.setVisibility(VISIBLE);
                        }
                    });
                }
            }
        };

        final String sql = "SELECT level,lat,lon FROM "
                + DatabaseHelper.LOCATION_TABLE + " WHERE bssid = ?" +
                " ORDER BY _id DESC limit ?" ;

        PooledQueryExecutor.enqueue( new PooledQueryExecutor.Request( sql,
                new String[]{network.getBssid(), obsMap.maxSize()+""}, new PooledQueryExecutor.ResultHandler() {
            @Override
            public boolean handleRow( final Cursor cursor ) {
                observations++;
                obsMap.put( new LatLng( cursor.getFloat(1), cursor.getFloat(2) ), cursor.getInt(0) );
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
                    for (BluetoothGattService service: gatt.getServices()) {
                        final String serviceUuid = service.getUuid().toString();
                        if (serviceUuid.length() >= 8) {
                            final String serviceId = serviceUuid.substring(4,8);
                            final String serviceTitle = MainActivity.getMainActivity()
                                    .getBleService(serviceId.toUpperCase());
                            if ("180A".equalsIgnoreCase(serviceId)) {
                                //Most devices supply some info, notably Mfgr and model #
                                BluetoothGattCharacteristic mfgrNameCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb"));
                                if (mfgrNameCharacteristic != null) {
                                    characteristicsToQuery.add(mfgrNameCharacteristic);
                                } else {
                                    Logging.info("GATT: Mfgr Name is null");
                                }
                                BluetoothGattCharacteristic modelCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb"));
                                if (modelCharacteristic != null) {
                                    characteristicsToQuery.add(modelCharacteristic);
                                } else {
                                    Logging.info("GATT: model number is null");
                                }
                                BluetoothGattCharacteristic serialNumCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb"));
                                if (serialNumCharacteristic != null) {
                                    characteristicsToQuery.add(serialNumCharacteristic);
                                } else {
                                    Logging.info("GATT: Serial number is null");
                                }
                                BluetoothGattCharacteristic firmwareRevCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb"));
                                if (firmwareRevCharacteristic != null) {
                                    characteristicsToQuery.add(firmwareRevCharacteristic);
                                } else {
                                    Logging.info("GATT: firmware rev. is null");
                                }
                                BluetoothGattCharacteristic hardwareNRevCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb"));
                                if (hardwareNRevCharacteristic != null) {
                                    characteristicsToQuery.add(hardwareNRevCharacteristic);
                                } else {
                                    Logging.info("GATT: hardware rev. is null");
                                }
                                BluetoothGattCharacteristic softwareRevCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb"));
                                if (softwareRevCharacteristic != null) {
                                    characteristicsToQuery.add(softwareRevCharacteristic);
                                } else {
                                    Logging.info("GATT: Software rev. is null");
                                }
                                BluetoothGattCharacteristic pnpIdCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb"));
                                if (pnpIdCharacteristic != null) {
                                    characteristicsToQuery.add(pnpIdCharacteristic);
                                } else {
                                    Logging.info("GATT: PnP ID is null");
                                }
                                //TODO: what other services should we query?
                            } else if ("1800".equalsIgnoreCase(serviceId)) {
                                //Most devices publish GAP, including device name and appearance
                                BluetoothGattCharacteristic deviceNameCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb"));
                                if (deviceNameCharacteristic != null) {
                                    characteristicsToQuery.add(deviceNameCharacteristic);
                                } else {
                                    Logging.info("GAP: Device Name is null");
                                }
                                BluetoothGattCharacteristic appearanceCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb"));
                                if (appearanceCharacteristic != null) {
                                    characteristicsToQuery.add(appearanceCharacteristic);
                                } else {
                                    Logging.info("GAP: Appearance is null");
                                }
                                BluetoothGattCharacteristic systemIdCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb"));
                                if (systemIdCharacteristic != null) {
                                    characteristicsToQuery.add(systemIdCharacteristic);
                                } else {
                                    Logging.info("GAP: system ID is null");
                                }
                                BluetoothGattCharacteristic modelNumCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb"));
                                if (modelNumCharacteristic != null) {
                                    characteristicsToQuery.add(modelNumCharacteristic);
                                } else {
                                    Logging.info("GAP: model number is null");
                                }
                            } else if ("180D".equalsIgnoreCase(serviceId)) {
                                BluetoothGattCharacteristic heartRateCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002aa4-0000-1000-8000-00805f9b34fb"));
                                if (heartRateCharacteristic != null) {
                                    characteristicsToQuery.add(heartRateCharacteristic);
                                } else {
                                    Logging.info("HR: Heart rate is null");
                                }
                                //TODO: what other services should we query?
                            } else if ("180F".equalsIgnoreCase(serviceId)) {
                                BluetoothGattCharacteristic batteryLevelCharacteristic =
                                        service.getCharacteristic(UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb"));
                                if (batteryLevelCharacteristic != null) {
                                    characteristicsToQuery.add(batteryLevelCharacteristic);
                                } else {
                                    Logging.info("BAT: Battery level is null");
                                }
                            }

                            if (null != serviceTitle) {
                                int lastServicePeriod = serviceTitle.lastIndexOf(".");
                                displayMessage.append(lastServicePeriod == -1
                                                ? serviceTitle : serviceTitle.substring(lastServicePeriod+1))
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
                                                            ? charTitle : charTitle.substring(lastCharPeriod+1))
                                                    .append(" (0x").append(charId.toUpperCase()).append(")\n");
                                        }
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
                        Integer titleResourceStringId = BLE_STRING_CHARACTERSITIC_UUIDS.get(characteristic.getUuid());
                        if (null != titleResourceStringId) {
                            // common case: this is a string value characteristic.
                            final String characteristicStringValue = new String(characteristic.getValue());
                            characteristicResults.put(getString(titleResourceStringId), characteristicStringValue);
                        } else if (UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb").equals(characteristic.getUuid())) {
                            //ALIBI: PnP value has a slightly complex decoding
                            final String pnpValue = getPnpValue(characteristic);
                            characteristicResults.put(getString(R.string.ble_pnp_title), pnpValue);
                        } else if (UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb").equals(characteristic.getUuid())) {
                            //ALIBI: this could be added to the stringCharacteristicUuids map, but we're leaving it its own case in case we want to update the network SSID value
                            final String name = new String(characteristic.getValue());
                            characteristicResults.put(getString(R.string.ble_name_title), name);
                            // NB: _could_ replace BT name with the discovered dev name here, but is that useful?
                            //if (null == network.getSsid() || network.getSsid().isBlank()) {
                            //    network.setSsid(name);
                            //}
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
                            //TODO: battery and heart rate will land here for now
                            Logging.info(characteristic.getUuid().toString()+": "+new String(characteristic.getValue()) );
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
                                final StringBuffer results = new StringBuffer();
                                boolean first = true;
                                for (String key: characteristicResults.keySet()) {
                                    if (first) {
                                       first = false;
                                    } else {
                                        results.append("\n");
                                    }
                                    results.append(key).append(": ").append(characteristicResults.get(key));
                                }
                                runOnUiThread(() -> {
                                    charView.setVisibility(VISIBLE);
                                    charContents.setText(results.toString());
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
                    Logging.info("CHARACTERISTIC CHANGED" + characteristic.getUuid().toString()+" :"+new String(characteristic.getValue()) );
                    gatt.disconnect();
                    gatt.close();
                    found.set(false);
                    runOnUiThread(() -> {
                        WiGLEToast.showOverActivity(activity, R.string.btloc_title, "characteristic change.");
                    });
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
                    runOnUiThread(() -> {
                        WiGLEToast.showOverActivity(activity, R.string.btloc_title, "service change.");
                    });
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

    @NonNull
    private static String getPnpValue(@NonNull BluetoothGattCharacteristic characteristic) {
        byte[] charValue = characteristic.getValue();
        final String vendorIdSrc = BluetoothUtil.getGattUint8(charValue[0]) == 1 ? "BLE" : "USB";
        final String vendorId = ""+BluetoothUtil.getGattUint8(charValue[1]);
        final String productString = ""+BluetoothUtil.getGattUint8(charValue[2]);
        final String productVersionString = "" + BluetoothUtil.getGattUint8(charValue[3]);
        return vendorIdSrc + ":" +  vendorId + ":" + productString + ":" + productVersionString;
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

    private LatLng computeBasicLocation(ConcurrentLinkedHashMap<LatLng, Integer> obsMap) {
        double latSum = 0.0;
        double lonSum = 0.0;
        double weightSum = 0.0;
        for (Map.Entry<LatLng, Integer> obs : obsMap.entrySet()) {
            if (null != obs.getKey()) {
                float cleanSignal = cleanSignal((float) obs.getValue());
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
                float cleanSignal = cleanSignal((float) obs.getValue().getRssi());
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

    private int computeZoom(ConcurrentLinkedHashMap<LatLng, Integer> obsMap, final LatLng centroid) {
        float maxDist = 0f;
        for (Map.Entry<LatLng, Integer> obs : obsMap.entrySet()) {
            float[] res = new float[3];
            Location.distanceBetween(centroid.latitude, centroid.longitude, obs.getKey().latitude, obs.getKey().longitude, res);
            if(res[0] > maxDist) {
                maxDist = res[0];
            }
        }
        Logging.info("max dist: "+maxDist);
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

    private void setupMap( final Network network, final Bundle savedInstanceState, final SharedPreferences prefs ) {
        mapView = new MapView( this );
        try {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(googleMap -> ThemeUtil.setMapTheme(googleMap, mapView.getContext(), prefs, R.raw.night_style_json));
        }
        catch (NullPointerException ex) {
            Logging.error("npe in mapView.onCreate: " + ex, ex);
        }
        MapsInitializer.initialize( this );

        if ((network != null) && (network.getLatLng() != null)) {
            mapView.getMapAsync(googleMap -> {
                final CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(network.getLatLng()).zoom(DEFAULT_ZOOM).build();
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                googleMap.addCircle(new CircleOptions()
                        .center(network.getLatLng())
                        .radius(5)
                        .fillColor(Color.argb(128, 240, 240, 240))
                        .strokeColor(Color.argb(200, 255, 32, 32))
                        .strokeWidth(3f)
                        .zIndex(100));
            });
        }

        final RelativeLayout rlView = findViewById( R.id.netmap_rl );
        rlView.addView( mapView );
    }

    private void setupButtons( final Network network, final SharedPreferences prefs ) {
        final ArrayList<String> hideAddresses = addressListForPref(prefs, PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS);
        final ArrayList<String> blockAddresses = addressListForPref(prefs, PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS);

        if ( ! NetworkType.WIFI.equals(network.getType()) ) {
            final View filterRowView = findViewById(R.id.filter_row);
            filterRowView.setVisibility(GONE);
        } else {
            final Button hideMacButton = findViewById( R.id.hide_mac_button );
            final Button hideOuiButton = findViewById( R.id.hide_oui_button );
            final Button disableLogMacButton = findViewById( R.id.disable_log_mac_button );
            if ( (null == network.getBssid()) || (network.getBssid().length() < 17) ||
                    (hideAddresses.contains(network.getBssid().toUpperCase(Locale.ROOT))) ) {
                hideMacButton.setEnabled(false);
            }

            if ( (null == network.getBssid()) || (network.getBssid().length() < 8) ||
                    (hideAddresses.contains(network.getBssid().toUpperCase(Locale.ROOT).substring(0, 8)))) {
                hideOuiButton.setEnabled(false);
            }

            if ( (null == network.getBssid()) || (network.getBssid().length() < 17) ||
                    (blockAddresses.contains(network.getBssid().toUpperCase(Locale.ROOT))) ) {
                disableLogMacButton.setEnabled(false);
            }

            hideMacButton.setOnClickListener(buttonView -> {
                // add a display-exclude row fot MAC
                if (network.getBssid() != null) {
                    MacFilterActivity.addEntry(hideAddresses,
                            prefs, network.getBssid().replace(":",""), PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS);
                    hideMacButton.setEnabled(false);
                }
            });

            hideOuiButton.setOnClickListener(buttonView -> {
                // add a display-exclude row fot OUI
                if (network.getBssid() != null) {
                    MacFilterActivity.addEntry(hideAddresses,
                            prefs, network.getBssid().replace(":", "").substring(0, 6),
                            PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS);
                }
                hideOuiButton.setEnabled(false);
            });

            disableLogMacButton.setOnClickListener(buttonView -> {
                // add a log-exclude row fot OUI
                if (network.getBssid() != null) {
                    MacFilterActivity.addEntry(blockAddresses,
                            prefs, network.getBssid().replace(":", ""), PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS);
                    //TODO: should this also delete existing records?
                }
                disableLogMacButton.setEnabled(false);
            });

            final Button startSurveyButton = findViewById(R.id.start_survey);
            final Button endSurveyButton = findViewById(R.id.end_survey);
            MainActivity.State state = MainActivity.getStaticState();
            startSurveyButton.setOnClickListener(buttonView -> {
                final FragmentActivity fa = this;
                //TDDO: disabled until obsMap DB load complete?
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
                    KmlSurveyWriter kmlWriter = new KmlSurveyWriter(MainActivity.getMainActivity(), ListFragment.lameStatic.dbHelper,
                            "KmlSurveyWriter", true, network.getBssid(), localObsMap.values());
                    kmlWriter.start();
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

    @SuppressLint("MissingPermission")
    private int getExistingSsid(final String ssid ) {
        final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        final String quotedSsid = "\"" + ssid + "\"";
        int netId = -2;

        for ( final WifiConfiguration config : wifiManager.getConfiguredNetworks() ) {
            Logging.info( "bssid: " + config.BSSID
                            + " ssid: " + config.SSID
                            + " status: " + config.status
                            + " id: " + config.networkId
                            + " preSharedKey: " + config.preSharedKey
                            + " priority: " + config.priority
                            + " wepTxKeyIndex: " + config.wepTxKeyIndex
                            + " allowedAuthAlgorithms: " + config.allowedAuthAlgorithms
                            + " allowedGroupCiphers: " + config.allowedGroupCiphers
                            + " allowedKeyManagement: " + config.allowedKeyManagement
                            + " allowedPairwiseCiphers: " + config.allowedPairwiseCiphers
                            + " allowedProtocols: " + config.allowedProtocols
                            + " hiddenSSID: " + config.hiddenSSID
                            + " wepKeys: " + Arrays.toString( config.wepKeys )
            );
            if ( quotedSsid.equals( config.SSID ) ) {
                netId = config.networkId;
                break;
            }
        }

        return netId;
    }

    @Override
    public void handleDialog(final int dialogId) {
        switch(dialogId) {
            case NON_CRYPTO_DIALOG:
                connectToNetwork( null );
                break;
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
                    mapView.getMapAsync(GoogleMap::clear);
                }
                break;
            default:
                Logging.warn("Network unhandled dialogId: " + dialogId);
        }
    }

    private void connectToNetwork( final String password ) {
        final int preExistingNetId = getExistingSsid( network.getSsid() );
        final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService( Context.WIFI_SERVICE );
        int netId = -2;
        if ( preExistingNetId < 0 ) {
            final WifiConfiguration newConfig = new WifiConfiguration();
            newConfig.SSID = "\"" + network.getSsid() + "\"";
            newConfig.hiddenSSID = false;
            if ( password != null ) {
                if ( Network.CRYPTO_WEP == network.getCrypto() ) {
                    newConfig.wepKeys = new String[]{ "\"" + password + "\"" };
                }
                else {
                    newConfig.preSharedKey = "\"" + password + "\"";
                }
            }

            netId = wifiManager.addNetwork( newConfig );
        }

        if ( netId >= 0 ) {
            final boolean disableOthers = true;
            wifiManager.enableNetwork(netId, disableOthers);
        }
    }

    @Override
    public void handleWiFiSeen(String bssid, Integer rssi, Location location) {
        LatLng latest = new LatLng(location.getLatitude(), location.getLongitude());
        localObsMap.put(latest, new Observation(rssi, location.getLatitude(), location.getLongitude(), location.getAltitude()));
        final LatLng estCentroid = computeObservationLocation(localObsMap);
        final int zoomLevel = computeZoom(obsMap, estCentroid);
        mapView.getMapAsync(googleMap -> {
            if (network.getLatLng() == null) {
                final CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(latest).zoom(zoomLevel).build();
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
            BitmapDescriptor obsIcon = NetworkListUtil.getSignalBitmap(
                    getApplicationContext(), rssi);
            googleMap.addMarker(new MarkerOptions().icon(obsIcon)
                    .position(latest).zIndex(rssi));
            Logging.info("survey observation added");
        });
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

            TextView text = view.findViewById( R.id.security );
            text.setText(args != null?args.getString("capabilities"):"");

            text = view.findViewById( R.id.signal );
            text.setText(args != null?args.getString("level"):"");

            final Button ok = view.findViewById( R.id.ok_button );

            final TextInputEditText password = view.findViewById( R.id.edit_password );
            password.addTextChangedListener( new SettingsFragment.SetWatcher() {
                @Override
                public void onTextChanged( final String s ) {
                    if (!s.isEmpty()) {
                        ok.setEnabled(true);
                    }
                }
            });

            final CheckBox showpass = view.findViewById( R.id.showpass );
            showpass.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if ( isChecked ) {
                    password.setInputType( InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD );
                    password.setTransformationMethod( null );
                }
                else {
                    password.setInputType( InputType.TYPE_TEXT_VARIATION_PASSWORD );
                    password.setTransformationMethod(
                            android.text.method.PasswordTransformationMethod.getInstance() );
                }
            });

            ok.setOnClickListener(buttonView -> {
                try {
                    final NetworkActivity networkActivity = (NetworkActivity) getActivity();
                    if (networkActivity != null && password.getText() != null) {
                        networkActivity.connectToNetwork(password.getText().toString());
                    }
                    if (null != dialog) {
                        dialog.dismiss();
                    }
                }
                catch ( Exception ex ) {
                    // guess it wasn't there anyways
                    Logging.info( "exception dismissing crypto dialog: " + ex );
                }
            });

            Button cancel = view.findViewById( R.id.cancel_button );
            cancel.setOnClickListener(buttonView -> {
                try {
                    if (null != dialog) {
                        dialog.dismiss();
                    }
                }
                catch ( Exception ex ) {
                    // guess it wasn't there anyways
                    Logging.info( "exception dismissing crypto dialog: " + ex );
                }
            });

            return view;
        }
    }

    /* Creates the menu items */
    @Override
    public boolean onCreateOptionsMenu( final Menu menu ) {
        MenuItem item = menu.add(0, MENU_COPY, 0, getString(R.string.menu_copy_network));
        item.setIcon( android.R.drawable.ic_menu_save );

        item = menu.add(0, MENU_RETURN, 0, getString(R.string.menu_return));
        item.setIcon( android.R.drawable.ic_menu_revert );

        return true;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        switch ( item.getItemId() ) {
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
}
