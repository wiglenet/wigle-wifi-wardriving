package net.wigle.wigleandroid;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.ActionBar;
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

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import net.wigle.wigleandroid.background.PooledQueryExecutor;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.MccMncRecord;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.OUI;
import net.wigle.wigleandroid.ui.NetworkListUtil;
import net.wigle.wigleandroid.ui.ScreenChildActivity;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

@SuppressWarnings("deprecation")
public class NetworkActivity extends ScreenChildActivity implements DialogListener {
    private static final int MENU_RETURN = 11;
    private static final int MENU_COPY = 12;
    private static final int NON_CRYPTO_DIALOG = 130;

    private static final int MSG_OBS_UPDATE = 1;
    private static final int MSG_OBS_DONE = 2;

    private static final int DEFAULT_ZOOM = 18;

    private Network network;
    private MapView mapView;
    private int observations = 0;
    private boolean isDbResult = false;
    private final ConcurrentLinkedHashMap<LatLng, Integer> obsMap = new ConcurrentLinkedHashMap<>(512);
    private NumberFormat numberFormat;

    // used for shutting extraneous activities down on an error
    public static NetworkActivity networkActivity;

    /** Called when the activity is first created. */
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

        final SimpleDateFormat format = NetworkListUtil.getConstructionTimeFormater(this);
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
                btico.setVisibility(View.VISIBLE);
                Integer btImageId = NetworkListUtil.getBtImage(network);
                if (null == btImageId) {
                    btico.setVisibility(View.GONE);
                } else {
                    btico.setImageResource(btImageId);
                }
            } else {
                btico.setVisibility(View.GONE);
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
            tv.setText( NetworkListUtil.getConstructionTime(format, network ) );

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
                            v.setVisibility(View.VISIBLE);
                            tv = findViewById( R.id.na_cell_status );
                            tv.setText(rec.getStatus() );
                            tv = findViewById( R.id.na_cell_brand );
                            tv.setText(rec.getBrand());
                            tv = findViewById( R.id.na_cell_bands );
                            tv.setText( rec.getBands());
                            if (rec.getNotes() != null && !rec.getNotes().isEmpty()) {
                                v = findViewById(R.id.cell_notes_row);
                                v.setVisibility(View.VISIBLE);
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
                v.setVisibility(View.VISIBLE);
                if (network.getBleMfgrId() != null || network.getBleMfgr() != null) {
                    v = findViewById(R.id.ble_vendor_row);
                    v.setVisibility(View.VISIBLE);
                    tv = findViewById( R.id.na_ble_vendor_id );
                    tv.setText((null != network.getBleMfgrId())?network.getBleMfgrId()+"":"-");
                    tv = findViewById( R.id.na_ble_vendor_lookup );
                    tv.setText(network.getBleMfgr() );
                }

                List<String> serviceUuids = network.getBleServiceUuids();
                if (null != serviceUuids && !serviceUuids.isEmpty()) {
                    v = findViewById(R.id.ble_services_row);
                    v.setVisibility(View.VISIBLE);
                    tv = findViewById( R.id.na_ble_service_uuids );
                    tv.setText(serviceUuids.toString() );
                }
            }
            setupMap(network, savedInstanceState, prefs );
            // kick off the query now that we have our map
            setupButtons(network, prefs);
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
            filterRowView.setVisibility(View.GONE);
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
                    if ( s.length() > 0 ) {
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
}
