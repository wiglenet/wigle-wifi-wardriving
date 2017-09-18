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
import android.database.Cursor;
import android.graphics.Color;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.ClipboardManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
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
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;

import net.wigle.wigleandroid.background.QueryThread;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.OUI;

@SuppressWarnings("deprecation")
public class NetworkActivity extends AppCompatActivity implements DialogListener {
    private static final int MENU_EXIT = 11;
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

        final Intent intent = getIntent();
        final String bssid = intent.getStringExtra( ListFragment.NETWORK_EXTRA_BSSID );
        isDbResult = intent.getBooleanExtra(ListFragment.NETWORK_EXTRA_IS_DB_RESULT, false);
        MainActivity.info( "bssid: " + bssid + " isDbResult: " + isDbResult);

        network = MainActivity.getNetworkCache().get(bssid);
        SimpleDateFormat format = NetworkListAdapter.getConstructionTimeFormater(this);

        TextView tv = (TextView) findViewById( R.id.bssid );
        tv.setText( bssid );

        if ( network == null ) {
            MainActivity.info( "no network found in cache for bssid: " + bssid );
        }
        else {
            // do gui work
            tv = (TextView) findViewById( R.id.ssid );
            tv.setText( network.getSsid() );

            final String ouiString = network.getOui(ListFragment.lameStatic.oui);
            tv = (TextView) findViewById( R.id.oui );
            tv.setText( ouiString );

            final int image = NetworkListAdapter.getImage( network );
            final ImageView ico = (ImageView) findViewById( R.id.wepicon );
            ico.setImageResource( image );

            tv = (TextView) findViewById( R.id.na_signal );
            final int level = network.getLevel();
            tv.setTextColor( NetworkListAdapter.getSignalColor( level ) );
            tv.setText( Integer.toString( level ) );

            tv = (TextView) findViewById( R.id.na_type );
            tv.setText( network.getType().name() );

            tv = (TextView) findViewById( R.id.na_firsttime );
            tv.setText( NetworkListAdapter.getConstructionTime(format, network ) );

            tv = (TextView) findViewById( R.id.na_chan );
            if ( ! NetworkType.WIFI.equals(network.getType()) ) {
                tv.setText( getString(R.string.na) );
            }
            else {
                Integer chan = network.getChannel();
                chan = chan != null ? chan : network.getFrequency();
                tv.setText( " " + Integer.toString(chan) + " " );
            }

            tv = (TextView) findViewById( R.id.na_cap );
            tv.setText( " " + network.getCapabilities().replace("][", "]  [") );

            setupMap( network, savedInstanceState );
            // kick off the query now that we have our map
            setupQuery();
            setupButtons( network );
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
                final TextView tv = (TextView) findViewById( R.id.na_observe );
                if ( msg.what == MSG_OBS_UPDATE ) {
                    tv.setText( " " + Integer.toString( observations ) + "...");
                }
                else if ( msg.what == MSG_OBS_DONE ) {
                    tv.setText( " " + Integer.toString( observations ) );

                    mapView.getMapAsync(new OnMapReadyCallback() {
                        @Override
                        public void onMapReady(final GoogleMap googleMap) {
                            int count = 0;
                            for (Map.Entry<LatLng, Integer> obs : obsMap.entrySet()) {
                                final LatLng latLon = obs.getKey();
                                final int level = obs.getValue();

                                if (count == 0 && network.getLatLng() == null) {
                                    final CameraPosition cameraPosition = new CameraPosition.Builder()
                                            .target(latLon).zoom(DEFAULT_ZOOM).build();
                                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                                }

                                googleMap.addCircle(new CircleOptions()
                                        .center(latLon)
                                        .radius(4)
                                        .fillColor(NetworkListAdapter.getSignalColor(level, true))
                                        .strokeWidth(0)
                                        .zIndex(level));
                                count++;
                            }
                            MainActivity.info("observation count: " + count);
                        }
                    });
                }
            }
        };

        final String sql = "SELECT level,lat,lon FROM "
                + DatabaseHelper.LOCATION_TABLE + " WHERE bssid = '" + network.getBssid() + "' limit " + obsMap.maxSize();

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

    private void setupMap( final Network network, final Bundle savedInstanceState ) {
        mapView = new MapView( this );
        try {
            mapView.onCreate(savedInstanceState);
        }
        catch (NullPointerException ex) {
            MainActivity.error("npe in mapView.onCreate: " + ex, ex);
        }
        MapsInitializer.initialize( this );

        if (network.getLatLng() != null) {
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

        final RelativeLayout rlView = (RelativeLayout) findViewById( R.id.netmap_rl );
        rlView.addView( mapView );
    }

    private void setupButtons( final Network network ) {
        final SharedPreferences prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final Button connectButton = (Button) findViewById( R.id.connect_button );
        final ArrayList<String> hideAddresses = addressListForPref(prefs, ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS);
        final ArrayList<String> blockAddresses = addressListForPref(prefs, ListFragment.PREF_EXCLUDE_LOG_ADDRS);

        if ( ! NetworkType.WIFI.equals(network.getType()) ) {
            connectButton.setEnabled( false );
            final View connectRowView = (View) findViewById(R.id.connect_row);
            connectRowView.setVisibility(View.GONE);
            final View filterRowView = (View) findViewById(R.id.filter_row);
            filterRowView.setVisibility(View.GONE);
        } else {
            final Button hideMacButton = (Button) findViewById( R.id.hide_mac_button );
            final Button hideOuiButton = (Button) findViewById( R.id.hide_oui_button );
            final Button disableLogMacButton = (Button) findViewById( R.id.disable_log_mac_button );
            connectButton.setOnClickListener( new OnClickListener() {
                @Override
                public void onClick( final View buttonView ) {
                    if ( Network.CRYPTO_NONE == network.getCrypto() ) {
                        MainActivity.createConfirmation( NetworkActivity.this, "You have permission to access this network?",
                                0, NON_CRYPTO_DIALOG);
                    }
                    else {
                        final CryptoDialog cryptoDialog = CryptoDialog.newInstance(network);
                        try {
                            cryptoDialog.show(NetworkActivity.this.getSupportFragmentManager(), "crypto-dialog");
                        }
                        catch (final IllegalStateException ex) {
                            MainActivity.error("exception showing crypto dialog: " + ex, ex);
                        }
                    }
                }
            });
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

            TextView text = (TextView) view.findViewById( R.id.security );
            text.setText(getArguments().getString("capabilities"));

            text = (TextView) view.findViewById( R.id.signal );
            text.setText(getArguments().getString("level"));

            final Button ok = (Button) view.findViewById( R.id.ok_button );

            final EditText password = (EditText) view.findViewById( R.id.edit_password );
            password.addTextChangedListener( new SettingsFragment.SetWatcher() {
                @Override
                public void onTextChanged( final String s ) {
                    if ( s.length() > 0 ) {
                        ok.setEnabled(true);
                    }
                }
            });

            final CheckBox showpass = (CheckBox) view.findViewById( R.id.showpass );
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

            Button cancel = (Button) view.findViewById( R.id.cancel_button );
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

        item = menu.add(0, MENU_EXIT, 0, getString(R.string.menu_return));
        item.setIcon( android.R.drawable.ic_menu_revert );

        return true;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        switch ( item.getItemId() ) {
            case MENU_EXIT:
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
