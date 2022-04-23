// -*- Mode: Java; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
// vim:ts=2:sw=2:tw=80:et

package net.wigle.wigleandroid;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;

import net.wigle.wigleandroid.MainActivity.State;
import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.ObservationUploader;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.OUI;
import net.wigle.wigleandroid.model.QueryArgs;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;
import net.wigle.wigleandroid.ui.NetworkListSorter;
import net.wigle.wigleandroid.ui.UINumberFormat;
import net.wigle.wigleandroid.util.Logging;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Set;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Main Network List View Fragment Adapter. Manages dynamic update of view apart from list when showing.
 * TODO: confirm that lameStatic values are being updated correctly, always.
 * @author: bobzilla, arkasha
 */
public final class ListFragment extends Fragment implements ApiListener, DialogListener {
    private static final int MENU_WAKELOCK = 12;
    private static final int MENU_SORT = 13;
    private static final int MENU_SCAN = 14;
    private static final int MENU_FILTER = 15;
    private static final int MENU_MUTE = 16;
    private static final int MENU_MAP = 17;

    private static final int SORT_DIALOG = 100;
    private static final int UPLOAD_DIALOG = 101;
    private static final int QUICK_PAUSE_DIALOG = 102;

    public static final float MIN_DISTANCE_ACCURACY = 32f;

    // preferences
    public static final String SHARED_PREFS = "WiglePrefs";
    public static final String PREF_USERNAME = "username";
    public static final String PREF_PASSWORD = "password";
    public static final String PREF_AUTHNAME = "authname";
    public static final String PREF_TOKEN = "token";
    public static final String PREF_TOKEN_IV = "tokenIV";
    public static final String PREF_TOKEN_TAG_LENGTH = "tokenTagLength";
    public static final String PREF_SHOW_CURRENT = "showCurrent";
    public static final String PREF_BE_ANONYMOUS = "beAnonymous";
    public static final String PREF_DONATE = "donate";
    public static final String PREF_DB_MARKER = "dbMarker";
    public static final String PREF_MAX_DB = "maxDbMarker";
    public static final String PREF_ROUTE_DB_RUN = "routeDbRun";
    public static final String PREF_NETS_UPLOADED = "netsUploaded";
    public static final String PREF_SCAN_PERIOD_STILL = "scanPeriodStill";
    public static final String PREF_SCAN_PERIOD = "scanPeriod";
    public static final String PREF_SCAN_PERIOD_FAST = "scanPeriodFast";
    public static final String PREF_OG_BT_SCAN_PERIOD_STILL = "btGeneralScanPeriodStill";
    public static final String PREF_OG_BT_SCAN_PERIOD = "btGeneralScanPeriod";
    public static final String PREF_OG_BT_SCAN_PERIOD_FAST = "btGeneralScanPeriodFast";
    public static final String PREF_QUICK_PAUSE = "quickScanPause";
    public static final String GPS_SCAN_PERIOD = "gpsPeriod";
    public static final String PREF_FOUND_SOUND = "foundSound";
    public static final String PREF_FOUND_NEW_SOUND = "foundNewSound";
    public static final String PREF_LANGUAGE = "speechLanguage";
    public static final String PREF_RESET_WIFI_PERIOD = "resetWifiPeriod";
    public static final String PREF_BATTERY_KILL_PERCENT = "batteryKillPercent";
    public static final String PREF_MUTED = "muted";
    public static final String PREF_BT_WAS_OFF = "btWasOff";
    public static final String PREF_SCAN_BT = "scanBluetooth";
    public static final String PREF_DISTANCE_RUN = "distRun";
    public static final String PREF_STARTTIME_RUN = "timestampRunStart";
    public static final String PREF_CUMULATIVE_SCANTIME_RUN = "millisScannedDuringRun";
    public static final String PREF_STARTTIME_CURRENT_SCAN = "timestampScanStart";
    public static final String PREF_DISTANCE_TOTAL = "distTotal";
    public static final String PREF_DISTANCE_PREV_RUN = "distPrevRun";
    public static final String PREF_PREV_LAT = "prevLat";
    public static final String PREF_PREV_LON = "prevLon";
    public static final String PREF_PREV_ZOOM = "prevZoom2";
    public static final String PREF_LIST_SORT = "listSort";
    public static final String PREF_SCAN_RUNNING = "scanRunning";
    public static final String PREF_METRIC = "metric";
    public static final String PREF_USE_NETWORK_LOC = "useNetworkLoc";
    public static final String PREF_DISABLE_TOAST = "disableToast"; // bool
    public static final String PREF_BLOWED_UP = "blowedUp";
    public static final String PREF_CONFIRM_UPLOAD_USER = "confirmUploadUser";
    public static final String PREF_EXCLUDE_DISPLAY_ADDRS = "displayExcludeAddresses";
    public static final String PREF_EXCLUDE_LOG_ADDRS = "logExcludeAddresses";
    public static final String PREF_GPS_TIMEOUT = "gpsTimeout";
    public static final String PREF_GPS_KALMAN_FILTER = "gpsKalmanFilter";
    public static final String PREF_NET_LOC_TIMEOUT = "networkLocationTimeout";
    public static final String PREF_START_AT_BOOT = "startAtBoot";
    public static final String PREF_LOG_ROUTES = "logRoutes";
    public static final String PREF_DAYNIGHT_MODE = "dayNightMode";

    // map prefs
    public static final String PREF_MAP_NO_TILE = "NONE";
    public static final String PREF_MAP_ONLYMINE_TILE = "MINE";
    public static final String PREF_MAP_NOTMINE_TILE = "NOTMINE";
    public static final String PREF_MAP_ALL_TILE = "ALL";
    public static final String PREF_MAP_TYPE = "mapType";
    public static final String PREF_MAP_ONLY_NEWDB = "mapOnlyNewDB";
    public static final String PREF_MAP_LABEL = "mapLabel";
    public static final String PREF_MAP_CLUSTER = "mapCluster";
    public static final String PREF_MAP_TRAFFIC = "mapTraffic";
    public static final String PREF_CIRCLE_SIZE_MAP = "circleSizeMap";
    public static final String PREF_MAP_HIDE_NETS = "hideNetsMap";
    public static final String PREF_VISUALIZE_ROUTE = "visualizeRoute";
    public static final String PREF_SHOW_DISCOVERED_SINCE = "showDiscoveredSince";
    public static final String PREF_SHOW_DISCOVERED = "showMyDiscovered";
    public static final String PREF_MAPS_FOLLOW_DAYNIGHT = "mapThemeMatchDayNight";

    // what to speak on announcements
    public static final String PREF_SPEECH_PERIOD = "speechPeriod";
    public static final String PREF_SPEECH_GPS = "speechGPS";
    public static final String PREF_SPEAK_RUN = "speakRun";
    public static final String PREF_SPEAK_NEW_WIFI = "speakNew";
    public static final String PREF_SPEAK_NEW_CELL = "speakNewCell";
    public static final String PREF_SPEAK_QUEUE = "speakQueue";
    public static final String PREF_SPEAK_MILES = "speakMiles";
    public static final String PREF_SPEAK_TIME = "speakTime";
    public static final String PREF_SPEAK_BATTERY = "speakBattery";
    public static final String PREF_SPEAK_SSID = "speakSsid";
    public static final String PREF_SPEAK_WIFI_RESTART = "speakWifiRestart";

    // map ssid filter
    public static final String PREF_MAPF_REGEX = "mapfRegex";
    public static final String PREF_MAPF_INVERT = "mapfInvert";
    public static final String PREF_MAPF_OPEN = "mapfOpen";
    public static final String PREF_MAPF_WEP = "mapfWep";
    public static final String PREF_MAPF_WPA = "mapfWpa";
    public static final String PREF_MAPF_CELL = "mapfCell";
    public static final String PREF_MAPF_BT = "mapfBt";
    public static final String PREF_MAPF_BTLE = "mapfBtle";
    public static final String PREF_MAPF_ENABLED = "mapfEnabled";
    public static final String FILTER_PREF_PREFIX = "LA";

    // rank stats data
    public static final String PREF_RANK = "rank";
    public static final String PREF_MONTH_RANK = "monthRank";

    public static final String NETWORK_EXTRA_BSSID = "extraBssid";
    public static final String NETWORK_EXTRA_IS_DB_RESULT = "extraIsDbResult";

    public static final String ANONYMOUS = "anonymous";
    public static final String WIFI_LOCK_NAME = "wigleWifiLock";

    public static final String QUICK_SCAN_UNSET = "UNSET";
    public static final String QUICK_SCAN_DO_NOTHING = "DO_NOTHING";
    public static final String QUICK_SCAN_PAUSE = "PAUSE";

    public static final String PREF_MXC_REINSTALL_ATTEMPTED = "TRIED_INSTALLING_MXC2";
    public static final String PREF_PIE_BAD_TOAST_COUNT = "PIE_BAD_TOAST_COUNT";
    public static final String PREF_Q_BAD_TOAST_COUNT = "Q_BAD_TOAST_COUNT";

    /** cross-activity communication */
    public static class LameStatic {
        public Location location;
        public int runNets;
        public int runBt;
        public long newNets;
        public long newWifi;
        public long newCells;
        public long newBt;
        public int currNets;
        public int currCells;
        public int currBt;
        public int preQueueSize;
        public long dbNets;
        public long dbLocs;
        public DatabaseHelper dbHelper;
        public Set<String> runNetworks;
        public Set<String> runBtNetworks;
        public QueryArgs queryArgs;
        public ConcurrentLinkedHashMap<String,Network> networkCache;
        public OUI oui;
    }
    public static final LameStatic lameStatic = new LameStatic();

    private boolean animating = false;
    private AnimatedVectorDrawableCompat scanningAnimation = null;

    static {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        int cacheSize = 128;
        if (maxMemory > 200000000L) {
            cacheSize = 1024;
        }
        else if (maxMemory > 100000000L) {
            cacheSize = 512;
        }
        Logging.info("Heap: maxMemory: " + maxMemory + " cacheSize: " + cacheSize);
        lameStatic.networkCache = new ConcurrentLinkedHashMap<>(cacheSize);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.list, container, false);
        final State state = MainActivity.getStaticState();

        Logging.info("setupUploadButton");
        setupUploadButton(view);
        Logging.info("setupList");
        setupList(view);
        Logging.info("setNetCountUI");
        setNetCountUI(state, view);
        Logging.info("setStatusUI");
        setStatusUI(view, null);
        Logging.info("setupLocation");
        setupLocation(view);

        return view;
    }

    /**
     * hide/show bluetooth new total
     * @param on
     */
    public void toggleBluetoothStats(final boolean on) {
        try {
            View fragRoot = this.getActivity().findViewById(android.R.id.content);
            View btView = fragRoot.findViewById(R.id.bt_list_total);
            if (on) {
                btView.setVisibility(VISIBLE);
            } else {
                btView.setVisibility(GONE);
            }
        } catch (Exception ex) {
            //this shouldn't be a critical failure if the view's not present.
            Logging.warn("Failed to toggle bluetooth new total to "+on);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setHasOptionsMenu(true);
    }

    public void setNetCountUI( final State state ) {
        setNetCountUI(state, getView());
    }

    private void setNetCountUI( final State state, final View view ) {
        if (view == null) {
            return;
        }
        TextView tv = (TextView) view.findViewById( R.id.stats_run );
        long netCount = state.wifiReceiver.getRunNetworkCount();
        if (null != state.bluetoothReceiver){
            netCount += state.bluetoothReceiver.getRunNetworkCount();
        }
        tv.setText( getString(R.string.run) + ": " + UINumberFormat.counterFormat(netCount) );
        tv = (TextView) view.findViewById( R.id.stats_wifi );
        tv.setText( ""+UINumberFormat.counterFormat(state.dbHelper.getNewWifiCount()) );
        tv = (TextView) view.findViewById( R.id.stats_cell );
        tv.setText( ""+UINumberFormat.counterFormat(lameStatic.newCells)  );
        tv = (TextView) view.findViewById( R.id.stats_bt );
        tv.setText( ""+UINumberFormat.counterFormat(state.dbHelper.getNewBtCount())  );
        tv = (TextView) view.findViewById( R.id.stats_dbnets );
        tv.setText(""+state.dbHelper.getNetworkCount());
    }

    public void setStatusUI( String status ) {
        setStatusUI(getView(), status);
    }

    public void setStatusUI( final View view, final String status ) {
        if ( status != null && view != null ) {
            final TextView tv = view.findViewById( R.id.status );
            tv.setText( status );
        }
        final MainActivity ma = MainActivity.getMainActivity();
        if (null != ma && view != null) {
            setScanningStatusIndicator(ma.isScanning());
            final ImageButton scanningImageButton = view.findViewById(R.id.scanning);
            final ImageButton notScanningImageButton = view.findViewById(R.id.not_scanning);
            if (null != getActivity()) {
                if (!animating) {
                    animating = true;
                    if (null == scanningAnimation) {
                        scanningAnimation = AnimatedVectorDrawableCompat.create(getActivity(), R.drawable.animated_wifi_simplified);
                        scanningImageButton.setImageDrawable(scanningAnimation);
                        scanningImageButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    }
                    if (null != scanningAnimation) {
                        scanningAnimation.start();
                    }
                }
            } else {
                Logging.error("Null activity context - can't set animation");
            }

            final SharedPreferences prefs = getActivity().getSharedPreferences(SHARED_PREFS, 0);
            String quickPausePref = prefs.getString(PREF_QUICK_PAUSE, QUICK_SCAN_UNSET);
            if (!QUICK_SCAN_DO_NOTHING.equals(quickPausePref)) {
                scanningImageButton.setContentDescription(getString(R.string.scan)+" "+getString(R.string.off));
                notScanningImageButton.setContentDescription(getString(R.string.scan)+" "+getString(R.string.on));
            } else {
                scanningImageButton.setContentDescription(getString(R.string.list_scanning_on));
                notScanningImageButton.setContentDescription(getString(R.string.list_scanning_off));
            }

            scanningImageButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick( final View buttonView ) {
                    String quickPausePref = prefs.getString(PREF_QUICK_PAUSE, QUICK_SCAN_UNSET);
                    if (QUICK_SCAN_DO_NOTHING.equals(quickPausePref)) return;
                    if (QUICK_SCAN_PAUSE.equals(quickPausePref)) {
                        toggleScan();
                    } else {
                        makeQuickPausePrefDialog(ma);
                    }
                }
           });
            notScanningImageButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick( final View buttonView ) {
                        String quickPausePref = prefs.getString(PREF_QUICK_PAUSE, QUICK_SCAN_UNSET);
                        if (QUICK_SCAN_DO_NOTHING.equals(quickPausePref)) return;
                        toggleScan();
                    }
            });
        }

    }

    public void toggleScan() {
        final MainActivity ma = MainActivity.getMainActivity();
        if (null != ma) {
            final boolean scanning = !ma.isScanning();
            ma.handleScanChange(scanning);
            handleScanChange(ma, getView());
        }
    }

    public void setScanningStatusIndicator(boolean scanning) {
        View view = getView();
        if (view != null) {
            final ImageButton scanningImageButton = (ImageButton) view.findViewById(R.id.scanning);
            final ImageButton notScanningImageButton = (ImageButton) view.findViewById(R.id.not_scanning);
            if (scanning) {
                scanningImageButton.setVisibility(VISIBLE);
                notScanningImageButton.setVisibility(GONE);
            } else {
                scanningImageButton.setVisibility(GONE);
                notScanningImageButton.setVisibility(VISIBLE);
            }
        }

    }

    @Override
    public void onPause() {
        Logging.info("LIST: paused.");
        super.onPause();
        if (animating) {
            if (scanningAnimation != null) {
                scanningAnimation.stop();
            }
            animating = false;
        }
    }

    @Override
    public void onResume() {
        Logging.info( "LIST: resumed.");
        super.onResume();
        getActivity().setTitle(R.string.list_app_name);
        //ALIBI: default status can confuse users on resume
        Logging.info("setNetCountUI");
        setNetCountUI(MainActivity.getStaticState(), getView());
        setStatusUI(null);
        animating = false;
    }

    @Override
    public void onStart() {
        Logging.info("LIST: start.");
        super.onStart();
    }

    @Override
    public void onStop() {
        Logging.info( "LIST: stop.");
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        Logging.info( "LIST: onDestroyView.");
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Logging.info( "LIST: destroy.");
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        Logging.info( "LIST: onDetach.");
        super.onDetach();
    }

    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu (final Menu menu, final MenuInflater inflater) {
        MenuItem item = menu.add(0, MENU_MAP, 0, getString(R.string.tab_map));
        item.setIcon( android.R.drawable.ic_menu_mapmode );
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_FILTER, 0, getString(R.string.menu_ssid_filter));
        item.setIcon(android.R.drawable.ic_menu_manage);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_SORT, 0, getString(R.string.menu_sort));
        item.setIcon( android.R.drawable.ic_menu_sort_alphabetically );
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        MainActivity main = MainActivity.getMainActivity(this);
        final String scan = (main == null || main.isScanning()) ? getString(R.string.off) : getString(R.string.on);
        item = menu.add(0, MENU_SCAN, 0, getString(R.string.scan) + " " + scan);
        item.setIcon((main == null || main.isScanning()) ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

        final String wake = MainActivity.isScreenLocked(this) ?
                getString(R.string.menu_screen_sleep) : getString(R.string.menu_screen_wake);
        item = menu.add(0, MENU_WAKELOCK, 0, wake);
        item.setIcon( android.R.drawable.ic_menu_gallery );

        final SharedPreferences prefs = getActivity().getSharedPreferences(SHARED_PREFS, 0);
        boolean muted = prefs.getBoolean(PREF_MUTED, true);
        item = menu.add(0, MENU_MUTE, 0,
                muted ? getString(R.string.play) : getString(R.string.mute));
        item.setIcon( muted ? android.R.drawable.ic_media_play
                : android.R.drawable.ic_media_pause);

        // item = menu.add(0, MENU_SETTINGS, 0, getString(R.string.menu_settings));
        // item.setIcon( android.R.drawable.ic_menu_preferences );

        // item = menu.add(0, MENU_EXIT, 0, getString(R.string.menu_exit));
        // item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );

        super.onCreateOptionsMenu(menu, inflater);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        final MainActivity main = MainActivity.getMainActivity(this);
        switch ( item.getItemId() ) {
            case MENU_WAKELOCK: {
                boolean screenLocked = ! MainActivity.isScreenLocked( this );
                MainActivity.setLockScreen( this, screenLocked );
                final String wake = screenLocked ? getString(R.string.menu_screen_sleep) : getString(R.string.menu_screen_wake);
                item.setTitle( wake );
                return true;
            }
            case MENU_SORT: {
                Logging.info("sort dialog");
                onCreateDialog( SORT_DIALOG );
                return true;
            }
            case MENU_SCAN: {
                if (main != null) {
                    final boolean scanning = !main.isScanning();
                    main.handleScanChange(scanning);
                    String name = getString(R.string.scan) + " " + (scanning ? getString(R.string.off) : getString(R.string.on));
                    item.setTitle(name);
                    item.setIcon(scanning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                    handleScanChange(main, getView());
                }

                return true;
            }
            case MENU_FILTER:
                final Intent intent = new Intent(getActivity(), FilterActivity.class);
                getActivity().startActivity(intent);
                return true;
            case MENU_MAP:
                NavigationView navigationView = (NavigationView) getActivity().findViewById(R.id.left_drawer);
                MenuItem mapMenuItem = navigationView.getMenu().findItem(R.id.nav_map);
                mapMenuItem.setCheckable(true);
                navigationView.setCheckedItem(R.id.nav_map);
                if (main != null) main.selectFragment(R.id.nav_map);
                return true;
            case MENU_MUTE:
                final SharedPreferences prefs = getActivity().getSharedPreferences(SHARED_PREFS, 0);
                boolean muted = prefs.getBoolean(PREF_MUTED, true);
                muted = ! muted;
                Editor editor = prefs.edit();
                editor.putBoolean(PREF_MUTED, muted);
                editor.apply();
                item.setTitle(muted ? getString(R.string.play) : getString(R.string.mute));
                item.setIcon(muted ? android.R.drawable.ic_media_play
                        : android.R.drawable.ic_media_pause);
                return true;
        }
        return false;
    }

    private void handleScanChange(final MainActivity main, final View view ) {
        final boolean isScanning = main == null || main.isScanning();
        Logging.info("list handleScanChange: isScanning now: " + isScanning );
        if ( isScanning ) {
            setStatusUI(view, getString(R.string.list_scanning_on));
        }
        else {
            setStatusUI(view, getString(R.string.list_scanning_off));
        }
    }

    public void onCreateDialog( int which ) {
        DialogFragment dialogFragment = null;
        switch ( which ) {
            case SORT_DIALOG:
                dialogFragment = new SortDialog();
                break;
            default:
                Logging.error( "unhandled dialog: " + which );
        }

        if (dialogFragment != null) {
            final FragmentManager fm = getActivity().getSupportFragmentManager();
            dialogFragment.show(fm, MainActivity.LIST_FRAGMENT_TAG);
        }
    }

    public static class SortDialog extends DialogFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            final Dialog dialog = getDialog();
            View view = inflater.inflate(R.layout.listdialog, container);
            dialog.setTitle(getString(R.string.sort_title));

            TextView text = (TextView) view.findViewById( R.id.text );
            text.setText( getString(R.string.sort_spin_label) );

            final SharedPreferences prefs = getActivity().getSharedPreferences( SHARED_PREFS, 0 );
            final Editor editor = prefs.edit();

            Spinner spinner = (Spinner) view.findViewById( R.id.sort_spinner );
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    getActivity(), android.R.layout.simple_spinner_item);
            final int[] listSorts = new int[]{ NetworkListSorter.CHANNEL_COMPARE, NetworkListSorter.CRYPTO_COMPARE,
                    NetworkListSorter.FIND_TIME_COMPARE, NetworkListSorter.SIGNAL_COMPARE, NetworkListSorter.SSID_COMPARE };
            final String[] listSortName = new String[]{ getString(R.string.channel),getString(R.string.crypto),
                    getString(R.string.found_time),getString(R.string.signal),getString(R.string.ssid) };
            int listSort = prefs.getInt( PREF_LIST_SORT, NetworkListSorter.SIGNAL_COMPARE );
            int periodIndex = 0;
            for ( int i = 0; i < listSorts.length; i++ ) {
                adapter.add( listSortName[i] );
                if ( listSort == listSorts[i] ) {
                    periodIndex = i;
                }
            }
            adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
            spinner.setAdapter( adapter );
            spinner.setSelection( periodIndex );
            spinner.setOnItemSelectedListener( new OnItemSelectedListener() {
                @Override
                public void onItemSelected( final AdapterView<?> parent, final View v, final int position, final long id ) {
                    // set pref
                    final int listSort = listSorts[position];
                    Logging.info( PREF_LIST_SORT + " setting list sort: " + listSort );
                    editor.putInt( PREF_LIST_SORT, listSort );
                    editor.apply();
                }
                @Override
                public void onNothingSelected( final AdapterView<?> arg0 ) {}
            });

            Button ok = (Button) view.findViewById( R.id.listdialog_button );
            ok.setOnClickListener( new OnClickListener() {
                @Override
                public void onClick( final View buttonView ) {
                    try {
                        dialog.dismiss();
                    }
                    catch ( Exception ex ) {
                        // guess it wasn't there anyways
                        Logging.info( "exception dismissing sort dialog: " + ex );
                    }
                }
            } );

            return view;
        }
    }

    // why is this even here? this is stupid. via:
    // http://stackoverflow.com/questions/456211/activity-restart-on-rotation-android
    @Override
    public void onConfigurationChanged( final Configuration newConfig ) {
        final MainActivity main = MainActivity.getMainActivity(this);
        final State state = MainActivity.getStaticState();

        Logging.info( "LIST: on config change" );
        MainActivity.setLocale( this.getActivity(), newConfig);
        super.onConfigurationChanged( newConfig );
        // getActivity().setContentView( R.layout.list );

        // have to redo linkages/listeners
        setupUploadButton(getView());
        setNetCountUI( state, getView() );
        setLocationUI(main, getView());
        setStatusUI(getView(), state.previousStatus);
    }

    private void setupList( final View view ) {
        State state = MainActivity.getStaticState();
        if (state.listAdapter == null) {
            state.listAdapter = new SetNetworkListAdapter( getActivity(), R.layout.row );
        }
        // always set our current list adapter
        state.wifiReceiver.setListAdapter(state.listAdapter);
        if (null != state.bluetoothReceiver) {
            state.bluetoothReceiver.setListAdapter(state.listAdapter);
        }
        final ListView listView = view.findViewById( R.id.ListView01 );
        setupListAdapter(listView, getActivity(), state.listAdapter, false);
    }

    public static void setupListAdapter( final ListView listView, final FragmentActivity activity,
                                         final SetNetworkListAdapter listAdapter, final boolean isDbResult) {

        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, final long id) {
                final Network network = (Network) parent.getItemAtPosition(position);
                if (network != null) {
                    MainActivity.getNetworkCache().put(network.getBssid(), network);
                    final Intent intent = new Intent(activity, NetworkActivity.class);
                    intent.putExtra(NETWORK_EXTRA_BSSID, network.getBssid());
                    intent.putExtra(NETWORK_EXTRA_IS_DB_RESULT, isDbResult);
                    activity.startActivity(intent);
                } else {
                    Logging.error("Null network onItemClick - ignoring");
                }
            }
        });
    }

    private void setupLocation( final View view ) {
        // set on UI if we already have one
        final MainActivity main = MainActivity.getMainActivity(this);
        setLocationUI(main, view);
        handleScanChange(main, view);
    }

    public void setLocationUI( final MainActivity main ) {
        setLocationUI( main, getView() );
    }

    private void setLocationUI( final MainActivity main, final View view ) {
        final State state = main.getState();
        if ( state.gpsListener == null ) {
            return;
        }
        if ( view == null ) {
            return;
        }

        try {
            TextView tv = view.findViewById( R.id.LocationTextView06 );
            tv.setText( getString(R.string.list_short_sats) + " " + state.gpsListener.getSatCount() );

            final Location location = state.gpsListener.getLocation();

            tv = view.findViewById( R.id.LocationTextView01 );
            String latText;
            if ( location == null ) {
                if ( main.isScanning() ) {
                    latText = getString(R.string.list_waiting_gps);
                }
                else {
                    latText = getString(R.string.list_scanning_off);
                    setScanningStatusIndicator(false);
                }
            }
            else {
                latText = state.numberFormat8.format( location.getLatitude() );
            }
            tv.setText( getString(R.string.list_short_lat) + " " + latText );

            tv = view.findViewById( R.id.LocationTextView02 );
            tv.setText( getString(R.string.list_short_lon) + " " + (location == null ? "" : state.numberFormat8.format( location.getLongitude() ) ) );

            tv = view.findViewById( R.id.LocationTextView03 );
            tv.setText( getString(R.string.list_speed) + " " + (location == null ? "" : metersPerSecondToSpeedString(state.numberFormat1, getActivity(), location.getSpeed()) ) );

            TextView tv4 = view.findViewById( R.id.LocationTextView04 );
            TextView tv5 = view.findViewById( R.id.LocationTextView05 );
            if ( location == null ) {
                tv4.setText( "" );
                tv5.setText( "" );
            } else {
                if (main != null) {
                    final SharedPreferences prefs = main.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
                    final String distString = UINumberFormat.metersToString(prefs,
                            state.numberFormat0, main, location.getAccuracy(), true);
                    tv4.setText("+/- " + distString);
                    final String accString = UINumberFormat.metersToString(prefs,
                            state.numberFormat0, main, (float) location.getAltitude(), true);
                    tv5.setText(getString(R.string.list_short_alt) + " " + accString);
                }
            }
        }
        catch ( IncompatibleClassChangeError ex ) {
            // yeah, saw this in the wild, who knows.
            Logging.error( "wierd ex: " + ex, ex);
        }
    }

    public static String metersPerSecondToSpeedString( final NumberFormat numberFormat, final Context context,
                                                       final float metersPerSecond ) {

        final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        final boolean metric = prefs.getBoolean( ListFragment.PREF_METRIC, false );

        String retval;
        if ( metric ) {
            retval = numberFormat.format( metersPerSecond * 3.6 ) + " " + context.getString(R.string.kmph);
        }
        else {
            retval = numberFormat.format( metersPerSecond * 2.23693629f ) + " " + context.getString(R.string.mph);
        }
        return retval;
    }

    private void setupUploadButton( final View view ) {
        final Button button = view.findViewById( R.id.upload_button );

        if (MainActivity.getMainActivity().isTransferring()) {
            button.setEnabled(false);
        }

        button.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick( final View view ) {
                final MainActivity main = MainActivity.getMainActivity( ListFragment.this );
                if (main == null) {return;}
                final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
                final boolean userConfirmed = prefs.getBoolean(ListFragment.PREF_CONFIRM_UPLOAD_USER,false);
                final State state = MainActivity.getStaticState();

                if (userConfirmed) {
                    uploadFile( state.dbHelper );
                } else {
                    makeUploadDialog(main);
                }
            }
        });
    }

    public void makeUploadDialog(final MainActivity main) {
        final SharedPreferences prefs = main.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        final boolean beAnonymous = prefs.getBoolean(ListFragment.PREF_BE_ANONYMOUS, false);
        final String username = beAnonymous? "anonymous":
                prefs.getString( ListFragment.PREF_USERNAME, "anonymous" );

        final String text = getString(R.string.list_upload) + "\n" + getString(R.string.username) + ": " + username;
        MainActivity.createConfirmation( ListFragment.this.getActivity(), text, R.id.nav_list, UPLOAD_DIALOG);
    }

    public void makeQuickPausePrefDialog(final MainActivity main) {
        final String dialogText = getString(R.string.quick_pause_text);
        final String checkboxText = getString(R.string.quick_pause_decision);
        MainActivity.createCheckboxConfirmation(ListFragment.this.getActivity(), dialogText, checkboxText,
                ListFragment.PREF_QUICK_PAUSE, ListFragment.QUICK_SCAN_PAUSE,
                ListFragment.QUICK_SCAN_DO_NOTHING, R.id.nav_list, QUICK_PAUSE_DIALOG);
    }

    @Override
    public void handleDialog(final int dialogId) {
        final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final SharedPreferences.Editor editor = prefs.edit();
        switch (dialogId) {
            case UPLOAD_DIALOG:
                final State state = MainActivity.getStaticState();
                final boolean userConfirmed = prefs.getBoolean(ListFragment.PREF_CONFIRM_UPLOAD_USER,false);
                final String authUser = prefs.getString(ListFragment.PREF_AUTHNAME,"");

                if (!userConfirmed && !authUser.isEmpty()) {
                    //remember the confirmation
                    editor.putBoolean(ListFragment.PREF_CONFIRM_UPLOAD_USER, true);
                    editor.apply();
                }
                uploadFile( state.dbHelper );
                break;
            case QUICK_PAUSE_DIALOG:
                Logging.info("quick pause callback");
                toggleScan();
            default:
                Logging.warn("ListFragment unhandled dialogId: " + dialogId);
        }
    }

    public void uploadFile( final DatabaseHelper dbHelper ){
        Logging.info( "upload file" );
        final MainActivity main = MainActivity.getMainActivity(this);
        if (main == null) { return; }
        final State state = main.getState();
        main.setTransferring();
        // actually need this Activity context, for dialogs
        // writeEntireDb and writeRun are both false, so PREF_DB_MARKER is used
        state.observationUploader = new ObservationUploader(main,
                ListFragment.lameStatic.dbHelper, this, false, false, false);
        try {
            state.observationUploader.startDownload(this);
        } catch (WiGLEAuthException waex) {
            Logging.warn("Authentication failure on run upload");
        }
    }

    /**
     * ApiListener interface
     */
    @Override
    public void requestComplete(final JSONObject json, final boolean isCache)
            throws WiGLEAuthException {
        final MainActivity main = MainActivity.getMainActivity( this );
        if (main == null) {
            Logging.warn("No main for requestComplete");
        }
        else {
            main.transferComplete();
        }
    }
}
