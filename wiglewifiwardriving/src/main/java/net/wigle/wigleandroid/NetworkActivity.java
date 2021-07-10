package net.wigle.wigleandroid;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
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

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.ClipboardManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import net.wigle.wigleandroid.background.QueryThread;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.MccMncRecord;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.OUI;
import net.wigle.wigleandroid.ui.NetworkListUtil;

@SuppressWarnings("deprecation")
public class NetworkActivity extends AppCompatActivity implements DialogListener {
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

    // used for shutting extraneous activities down on an error
    public static NetworkActivity networkActivity;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        MainActivity.info("NET: onCreate");
        super.onCreate(savedInstanceState);

        if (ListFragment.lameStatic.oui == null) {
            ListFragment.lameStatic.oui = new OUI(getAssets());
        }

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // set language
        MainActivity.setLocale( this );
        setContentView(R.layout.network);
        networkActivity = this;

        final SharedPreferences prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final int displayMode = prefs.getInt(ListFragment.PREF_DAYNIGHT_MODE, AppCompatDelegate.MODE_NIGHT_YES);
        final int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (AppCompatDelegate.MODE_NIGHT_YES == displayMode ||
                (AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM == displayMode &&
                        nightModeFlags == Configuration.UI_MODE_NIGHT_YES)) {
                getWindow().setNavigationBarColor(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            }
        }

        final Intent intent = getIntent();
        final String bssid = intent.getStringExtra( ListFragment.NETWORK_EXTRA_BSSID );
        isDbResult = intent.getBooleanExtra(ListFragment.NETWORK_EXTRA_IS_DB_RESULT, false);
        MainActivity.info( "bssid: " + bssid + " isDbResult: " + isDbResult);

        final SimpleDateFormat format = NetworkListUtil.getConstructionTimeFormater(this);
        if (null != MainActivity.getNetworkCache()) {
            network = MainActivity.getNetworkCache().get(bssid);
        }

        TextView tv = findViewById( R.id.bssid );
        tv.setText( bssid );

        if ( network == null ) {
            MainActivity.info( "no network found in cache for bssid: " + bssid );
        }
        else {
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
            tv.setText( Integer.toString( level ) );

            tv = findViewById( R.id.na_type );
            tv.setText( network.getType().name() );

            tv = findViewById( R.id.na_firsttime );
            tv.setText( NetworkListUtil.getConstructionTime(format, network ) );

            tv = findViewById( R.id.na_chan );
            Integer chan = network.getChannel();
            if ( NetworkType.WIFI.equals(network.getType()) ) {
                chan = chan != null ? chan : network.getFrequency();
                tv.setText(" " + Integer.toString(chan) + " ");
            } else if ( NetworkType.CDMA.equals(network.getType()) || chan == null) {
                tv.setText( getString(R.string.na) );
            } else {
                final String[] cellCapabilities = network.getCapabilities().split(";");
                tv.setText(cellCapabilities[0]+" "+channelCodeTypeForNetworkType(network.getType())+" "+chan);
            }

            tv = findViewById( R.id.na_cap );
            tv.setText( " " + network.getCapabilities().replace("][", "]  [") );

            if ( NetworkType.GSM.equals(network.getType()) ||
                    NetworkType.LTE.equals(network.getType()) ||
                    NetworkType.WCDMA.equals(network.getType())) { // cell net types  with advanced data

                if ((bssid != null) && (bssid.length() > 5) && (bssid.indexOf('_') >= 5)) {
                    final String operatorCode = bssid.substring(0, bssid.indexOf("_"));

                    MccMncRecord rec = null;
                    if (operatorCode.length() > 5 && operatorCode.length() < 7) {
                        final String mnc = operatorCode.substring(3, operatorCode.length());
                        final String mcc = operatorCode.substring(0, 3);

                        //DEBUG: MainActivity.info("\t\tmcc: "+mcc+"; mnc: "+mnc);

                        try {
                            rec = MainActivity.getStaticState().mxcDbHelper.networkRecordForMccMnc(mcc, mnc);
                        } catch (SQLException sqex) {
                            MainActivity.error("Unable to access Mxc Database: ",sqex);
                        }
                        if (rec != null) {
                            View v = findViewById(R.id.cell_info);
                            v.setVisibility(View.VISIBLE);
                            tv = findViewById( R.id.na_cell_status );
                            tv.setText( " "+rec.getStatus() );
                            tv = findViewById( R.id.na_cell_brand );
                            tv.setText( " "+rec.getBrand());
                            tv = findViewById( R.id.na_cell_bands );
                            tv.setText( " "+rec.getBands());
                            if (rec.getNotes() != null && !rec.getNotes().isEmpty()) {
                                v = findViewById(R.id.cell_notes_row);
                                v.setVisibility(View.VISIBLE);
                                tv = findViewById( R.id.na_cell_notes );
                                tv.setText( " "+rec.getNotes());
                            }
                        }
                    }
                } else {
                    MainActivity.warn("unable to get operatorCode for "+bssid);
                }
            }
            setupMap(network, savedInstanceState, prefs );
            // kick off the query now that we have our map
            setupQuery();
            setupButtons(network, prefs);
        }
    }

    @Override
    public void onDestroy() {
        MainActivity.info("NET: onDestroy");
        networkActivity = null;
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        MainActivity.info("NET: onResume");
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        } else {
            final SharedPreferences prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
            setupMap( network, null, prefs);
        }
    }

    @Override
    public void onPause() {
        MainActivity.info("NET: onPause");
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        MainActivity.info("NET: onSaveInstanceState");
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            try {
                mapView.onSaveInstanceState(outState);
            } catch (android.os.BadParcelableException bpe) {
                MainActivity.error("Exception saving NetworkActivity instance state: ",bpe);
                //this is really low-severity, since we can restore all state anyway
            }
        }
    }

    @Override
    public void onLowMemory() {
        MainActivity.info("NET: onLowMemory");
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
                    tv.setText( " " + Integer.toString( observations ) + "...");
                }
                else if ( msg.what == MSG_OBS_DONE ) {
                    tv.setText( " " + Integer.toString( observations ) );
                    // ALIBI:  assumes all observations belong to one "cluster" w/ a single centroid.
                    // we could check and perform multi-cluster here
                    // (get arithmetic mean, std-dev, try to do sigma-based partitioning)
                    // but that seems less likely w/ one individual's observations
                    final LatLng estCentroid = computeBasicLocation(obsMap);
                    final int zoomLevel = computeZoom(obsMap, estCentroid);
                    mapView.getMapAsync(new OnMapReadyCallback() {
                        @Override
                        public void onMapReady(final GoogleMap googleMap) {
                            int count = 0;
                            int maxDistMeters = 0;
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
                            if (null != estCentroid && estCentroid.latitude != 0d && estCentroid.longitude != 0d) {
                                //TODO: improve zoom based on obs distances?
                                final CameraPosition cameraPosition = new CameraPosition.Builder()
                                        .target(estCentroid).zoom(zoomLevel).build();
                                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                                googleMap.addMarker(new MarkerOptions().position(estCentroid));
                            }
                            MainActivity.info("observation count: " + count);
                        }
                    });
                }
            }
        };

        final String sql = "SELECT level,lat,lon FROM "
                + DatabaseHelper.LOCATION_TABLE + " WHERE bssid = '" + network.getBssid() +
                "' ORDER BY _id DESC limit " + obsMap.maxSize();

        final QueryThread.Request request = new QueryThread.Request( sql, new QueryThread.ResultHandler() {
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
        });
        ListFragment.lameStatic.dbHelper.addToQueue( request );
    }

    private final LatLng computeBasicLocation(ConcurrentLinkedHashMap<LatLng, Integer> obsMap) {
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

    private final int computeZoom(ConcurrentLinkedHashMap<LatLng, Integer> obsMap, final LatLng centroid) {
        float maxDist = 0f;
        for (Map.Entry<LatLng, Integer> obs : obsMap.entrySet()) {
            float[] res = new float[3];
            Location.distanceBetween(centroid.latitude, centroid.longitude, obs.getKey().latitude, obs.getKey().longitude, res);
            if(res[0] > maxDist) {
                maxDist = res[0];
            }
        }
        MainActivity.info("max dist: "+maxDist);
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
     * @param signal
     * @return
     */
    public static float cleanSignal(Float signal) {
        float signalMemo = signal;
        if (signal == null || signal == 0f) {
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
        }
        catch (NullPointerException ex) {
            MainActivity.error("npe in mapView.onCreate: " + ex, ex);
        }
        MapsInitializer.initialize( this );

        if ((network != null) && (network.getLatLng() != null)) {
            mapView.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(final GoogleMap googleMap) {
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
                }
            });
        }

        final RelativeLayout rlView = findViewById( R.id.netmap_rl );
        rlView.addView( mapView );
    }

    private void setupButtons( final Network network, final SharedPreferences prefs ) {
        final ArrayList<String> hideAddresses = addressListForPref(prefs, ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS);
        final ArrayList<String> blockAddresses = addressListForPref(prefs, ListFragment.PREF_EXCLUDE_LOG_ADDRS);

        if ( ! NetworkType.WIFI.equals(network.getType()) ) {
            final View filterRowView = findViewById(R.id.filter_row);
            filterRowView.setVisibility(View.GONE);
        } else {
            final Button hideMacButton = findViewById( R.id.hide_mac_button );
            final Button hideOuiButton = findViewById( R.id.hide_oui_button );
            final Button disableLogMacButton = findViewById( R.id.disable_log_mac_button );
            if ( (null == network.getBssid()) || (network.getBssid().length() < 17) ||
                    (hideAddresses.contains(network.getBssid().toUpperCase())) ) {
                hideMacButton.setEnabled(false);
            }

            if ( (null == network.getBssid()) || (network.getBssid().length() < 8) ||
                    (hideAddresses.contains(network.getBssid().toUpperCase().substring(0, 8)))) {
                hideOuiButton.setEnabled(false);
            }

            if ( (null == network.getBssid()) || (network.getBssid().length() < 17) ||
                    (blockAddresses.contains(network.getBssid().toUpperCase())) ) {
                disableLogMacButton.setEnabled(false);
            }


            hideMacButton.setOnClickListener( new OnClickListener() {
                @Override
                public void onClick( final View buttonView ) {
                    // add a display-exclude row fot MAC
                    MacFilterActivity.addEntry(hideAddresses,
                            prefs, network.getBssid().replace(":",""), ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS);
                    hideMacButton.setEnabled(false);
                }
            });

            hideOuiButton.setOnClickListener( new OnClickListener() {
                @Override
                public void onClick( final View buttonView ) {
                    // add a display-exclude row fot OUI
                    MacFilterActivity.addEntry(hideAddresses,
                            prefs, network.getBssid().replace(":","").substring(0,6),
                            ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS);
                    hideOuiButton.setEnabled(false);
                }
            });

            disableLogMacButton.setOnClickListener( new OnClickListener() {
                @Override
                public void onClick( final View buttonView ) {
                    // add a log-exclude row fot OUI
                    MacFilterActivity.addEntry(blockAddresses,
                            prefs, network.getBssid().replace(":",""), ListFragment.PREF_EXCLUDE_LOG_ADDRS);
                    //TODO: should this also delete existing records?
                    disableLogMacButton.setEnabled(false);
                }
            });
        }
    }

    private ArrayList<String> addressListForPref(final SharedPreferences prefs, final String key) {
        Gson gson = new Gson();
        String[] values = gson.fromJson(prefs.getString(key, "[]"), String[].class);
        return new ArrayList<String>(Arrays.asList(values));
    }

    private int getExistingSsid( final String ssid ) {
        final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        final String quotedSsid = "\"" + ssid + "\"";
        int netId = -2;

        for ( final WifiConfiguration config : wifiManager.getConfiguredNetworks() ) {
            MainActivity.info( "bssid: " + config.BSSID
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
                MainActivity.warn("Network unhandled dialogId: " + dialogId);
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
            dialog.setTitle(getArguments().getString("ssid"));

            TextView text = view.findViewById( R.id.security );
            text.setText(getArguments().getString("capabilities"));

            text = view.findViewById( R.id.signal );
            text.setText(getArguments().getString("level"));

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
            showpass.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) {
                    if ( isChecked ) {
                        password.setInputType( InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD );
                        password.setTransformationMethod( null );
                    }
                    else {
                        password.setInputType( InputType.TYPE_TEXT_VARIATION_PASSWORD );
                        password.setTransformationMethod(
                                android.text.method.PasswordTransformationMethod.getInstance() );
                    }
                }
            });

            ok.setOnClickListener( new OnClickListener() {
                @Override
                public void onClick( final View buttonView ) {
                    try {
                        final NetworkActivity networkActivity = (NetworkActivity) getActivity();
                        networkActivity.connectToNetwork( password.getText().toString() );
                        dialog.dismiss();
                    }
                    catch ( Exception ex ) {
                        // guess it wasn't there anyways
                        MainActivity.info( "exception dismissing crypto dialog: " + ex );
                    }
                }
            } );

            Button cancel = view.findViewById( R.id.cancel_button );
            cancel.setOnClickListener( new OnClickListener() {
                @Override
                public void onClick( final View buttonView ) {
                    try {
                        dialog.dismiss();
                    }
                    catch ( Exception ex ) {
                        // guess it wasn't there anyways
                        MainActivity.info( "exception dismissing crypto dialog: " + ex );
                    }
                }
            } );

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

    private static final String channelCodeTypeForNetworkType(NetworkType type) {
        switch (type) {
            case GSM:
                return "ARFCN"  ;
            case LTE:
                return "EARFCN";
            case WCDMA:
                return "UARFCN";
            default:
                return null;
        }
    }
}
