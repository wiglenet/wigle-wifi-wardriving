// -*- Mode: Java; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
// vim:ts=2:sw=2:tw=80:et

package net.wigle.wigleandroid;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
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
import net.wigle.wigleandroid.ui.WiGLEConfirmationDialog;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Main Network List View Fragment Adapter. Manages dynamic update of view apart from list when showing.
 * TODO: confirm that lameStatic values are being updated correctly, always.
 * @author bobzilla, arkasha
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

        public ExecutorService executor;
    }
    public static final LameStatic lameStatic = new LameStatic();

    private boolean animating = false;
    private AnimatedVectorDrawableCompat scanningAnimation = null;

    static {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        int cacheSize = 128;
        if (maxMemory > 400_000_000L) {
            cacheSize = 4000; // cap at 4,000
        }
        else if (maxMemory > 50_000_000L) {
            cacheSize = (int)(maxMemory / 100_000); // 100MiB == 1000 cache
        }
        Logging.info("Heap: maxMemory: " + maxMemory + " cacheSize: " + cacheSize);
        lameStatic.networkCache = new ConcurrentLinkedHashMap<>(cacheSize);

        lameStatic.executor =  Executors.newFixedThreadPool(3);
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
     * @param on desired bluetooth state
     */
    public void toggleBluetoothStats(final boolean on) {
        try {
            final Activity a = this.getActivity();
            if (null != a) {
                final View fragRoot = this.getActivity().findViewById(android.R.id.content);
                final View btView = fragRoot.findViewById(R.id.bt_list_total);
                if (on) {
                    btView.setVisibility(VISIBLE);
                } else {
                    btView.setVisibility(GONE);
                }
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

        //ALIBI: the number of async requests to perform.
        final Handler handler = new Handler(Looper.getMainLooper());
        TextView tv = view.findViewById( R.id.stats_run );
        long netCount = state.wifiReceiver.getRunNetworkCount();
        if (null != state.bluetoothReceiver){
            netCount += state.bluetoothReceiver.getRunNetworkCount();
        }
        tv.setText( getString(R.string.run) + ": " + UINumberFormat.counterFormat(netCount));
        lameStatic.executor.execute(() -> {
            final long count = state.dbHelper.getNewWifiCount();
            handler.post(() -> {
                TextView text = view.findViewById( R.id.stats_wifi );
                text.setText( ""+UINumberFormat.counterFormat(count) );
            });
        });
        tv = view.findViewById( R.id.stats_cell );
        tv.setText( ""+UINumberFormat.counterFormat(lameStatic.newCells));
        lameStatic.executor.execute(() -> {
            final long count = state.dbHelper.getNewBtCount();
            handler.post(() -> {
                TextView text = view.findViewById( R.id.stats_bt );
                text.setText( ""+UINumberFormat.counterFormat(count) );
            });
        });
        lameStatic.executor.execute(() -> {
            final long count = state.dbHelper.getNetworkCount();
            handler.post(() -> {
                TextView text = view.findViewById( R.id.stats_dbnets );
                text.setText(""+count);
            });
        });
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

            final SharedPreferences prefs = getActivity().getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            String quickPausePref = prefs.getString(PreferenceKeys.PREF_QUICK_PAUSE, QUICK_SCAN_UNSET);
            if (!QUICK_SCAN_DO_NOTHING.equals(quickPausePref)) {
                scanningImageButton.setContentDescription(getString(R.string.scan)+" "+getString(R.string.off));
                notScanningImageButton.setContentDescription(getString(R.string.scan)+" "+getString(R.string.on));
            } else {
                scanningImageButton.setContentDescription(getString(R.string.list_scanning_on));
                notScanningImageButton.setContentDescription(getString(R.string.list_scanning_off));
            }

            scanningImageButton.setOnClickListener(buttonView -> {
                String quickPausePref12 = prefs.getString(PreferenceKeys.PREF_QUICK_PAUSE, QUICK_SCAN_UNSET);
                if (QUICK_SCAN_DO_NOTHING.equals(quickPausePref12)) return;
                if (QUICK_SCAN_PAUSE.equals(quickPausePref12)) {
                    toggleScan();
                } else {
                    makeQuickPausePrefDialog();
                }
            });
            notScanningImageButton.setOnClickListener(buttonView -> {
                String quickPausePref1 = prefs.getString(PreferenceKeys.PREF_QUICK_PAUSE, QUICK_SCAN_UNSET);
                if (QUICK_SCAN_DO_NOTHING.equals(quickPausePref1)) return;
                toggleScan();
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
            final ImageButton scanningImageButton = view.findViewById(R.id.scanning);
            final ImageButton notScanningImageButton = view.findViewById(R.id.not_scanning);
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
        final Activity a = getActivity();
        if (null != a) {
            a.setTitle(R.string.list_app_name);
        }
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
    public void onCreateOptionsMenu (final Menu menu, @NonNull final MenuInflater inflater) {
        MenuItem item = menu.add(0, MENU_MAP, 0, getString(R.string.tab_map));
        item.setIcon( android.R.drawable.ic_menu_mapmode );
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_FILTER, 0, getString(R.string.menu_ssid_filter));
        item.setIcon(android.R.drawable.ic_menu_manage);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_SORT, 0, getString(R.string.menu_sort));
        item.setIcon( android.R.drawable.ic_menu_sort_alphabetically );
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        final MainActivity main = MainActivity.getMainActivity(this);
        final String scan = (main == null || main.isScanning()) ? getString(R.string.off) : getString(R.string.on);
        item = menu.add(0, MENU_SCAN, 0, getString(R.string.scan) + " " + scan);
        item.setIcon((main == null || main.isScanning()) ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

        final String wake = MainActivity.isScreenLocked(this) ?
                getString(R.string.menu_screen_sleep) : getString(R.string.menu_screen_wake);
        item = menu.add(0, MENU_WAKELOCK, 0, wake);
        item.setIcon( android.R.drawable.ic_menu_gallery );

        final Activity a = getActivity();
        if (null != a) {
            final SharedPreferences prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            boolean muted = prefs.getBoolean(PreferenceKeys.PREF_MUTED, true);
            item = menu.add(0, MENU_MUTE, 0,
                    muted ? getString(R.string.play) : getString(R.string.mute));
            item.setIcon(muted ? android.R.drawable.ic_media_play
                    : android.R.drawable.ic_media_pause);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        final MainActivity main = MainActivity.getMainActivity(this);
        final FragmentActivity a = getActivity();
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
                if (null != a) {
                    a.startActivity(intent);
                }
                return true;
            case MENU_MAP:
                if (null != a) {
                    NavigationView navigationView = a.findViewById(R.id.left_drawer);
                    MenuItem mapMenuItem = navigationView.getMenu().findItem(R.id.nav_map);
                    mapMenuItem.setCheckable(true);
                    navigationView.setCheckedItem(R.id.nav_map);
                }
                if (main != null) main.selectFragment(R.id.nav_map);
                return true;
            case MENU_MUTE:
                if (null != a) {
                    final SharedPreferences prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
                    boolean muted = prefs.getBoolean(PreferenceKeys.PREF_MUTED, true);
                    muted = !muted;
                    Editor editor = prefs.edit();
                    editor.putBoolean(PreferenceKeys.PREF_MUTED, muted);
                    editor.apply();
                item.setTitle(muted ? getString(R.string.play) : getString(R.string.mute));
                item.setIcon(muted ? android.R.drawable.ic_media_play
                        : android.R.drawable.ic_media_pause);
                }
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
            final FragmentActivity a = getActivity();
            if (null != a) {
                final FragmentManager fm = a.getSupportFragmentManager();
                dialogFragment.show(fm, MainActivity.LIST_FRAGMENT_TAG);
            }
        }
    }

    public static class SortDialog extends DialogFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            final Dialog dialog = getDialog();
            View view = inflater.inflate(R.layout.listdialog, container);
            dialog.setTitle(getString(R.string.sort_title));

            TextView text = view.findViewById( R.id.text );
            text.setText( getString(R.string.sort_spin_label) );

            final SharedPreferences prefs = getActivity().getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0 );
            final Editor editor = prefs.edit();

            Spinner spinner = view.findViewById( R.id.sort_spinner );
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    getActivity(), android.R.layout.simple_spinner_item);
            final int[] listSorts = new int[]{ NetworkListSorter.CHANNEL_COMPARE, NetworkListSorter.CRYPTO_COMPARE,
                    NetworkListSorter.FIND_TIME_COMPARE, NetworkListSorter.SIGNAL_COMPARE, NetworkListSorter.SSID_COMPARE };
            final String[] listSortName = new String[]{ getString(R.string.channel),getString(R.string.crypto),
                    getString(R.string.found_time),getString(R.string.signal),getString(R.string.ssid) };
            int listSort = prefs.getInt( PreferenceKeys.PREF_LIST_SORT, NetworkListSorter.SIGNAL_COMPARE );
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
                    Logging.info( PreferenceKeys.PREF_LIST_SORT + " setting list sort: " + listSort );
                    editor.putInt( PreferenceKeys.PREF_LIST_SORT, listSort );
                    editor.apply();
                }
                @Override
                public void onNothingSelected( final AdapterView<?> arg0 ) {}
            });

            Button ok = view.findViewById( R.id.listdialog_button );
            ok.setOnClickListener(buttonView -> {
                try {
                    dialog.dismiss();
                }
                catch ( Exception ex ) {
                    // guess it wasn't there anyways
                    Logging.info( "exception dismissing sort dialog: " + ex );
                }
            });

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
        final FragmentActivity a = this.getActivity();
        if (null != a) {
            MainActivity.setLocale( a, newConfig);
        }
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
        if (null != state && state.listAdapter == null) {
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
        listView.setOnItemClickListener((parent, view, position, id) -> {
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
        });
    }

    private void setupLocation( final View view ) {
        // set on UI if we already have one
        final MainActivity main = MainActivity.getMainActivity(this);
        if (null != main) {
            setLocationUI(main, view);
        }
        handleScanChange(main, view);
    }

    public void setLocationUI( final MainActivity main ) {
        setLocationUI( main, getView() );
    }

    private void setLocationUI( final MainActivity main, final View view ) {
        final State state = main.getState();
        if ( state.GNSSListener == null ) {
            return;
        }
        if ( view == null ) {
            return;
        }

        try {
            TextView tv = view.findViewById( R.id.LocationTextView06 );
            tv.setText( getString(R.string.list_short_sats) + " " + state.GNSSListener.getSatCount() );

            final Location location = state.GNSSListener.getCurrentLocation();

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
            final Activity a = getActivity();
            if (null != a) {
                tv.setText(getString(R.string.list_speed) + " " + (location == null ? "" : metersPerSecondToSpeedString(state.numberFormat1, a, location.getSpeed())));
            }

            TextView tv4 = view.findViewById( R.id.LocationTextView04 );
            TextView tv5 = view.findViewById( R.id.LocationTextView05 );
            if ( location == null ) {
                tv4.setText( "" );
                tv5.setText( "" );
            } else {
                if (main != null) {
                    final SharedPreferences prefs = main.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
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

        final SharedPreferences prefs = context.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0 );
        final boolean metric = prefs.getBoolean( PreferenceKeys.PREF_METRIC, false );

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

        button.setOnClickListener(view1 -> {
            final MainActivity main = MainActivity.getMainActivity( ListFragment.this );
            if (main == null) {return;}
            final FragmentActivity a = getActivity();
            if (null != a) {
                final SharedPreferences prefs = getActivity().getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
                final boolean userConfirmed = prefs.getBoolean(PreferenceKeys.PREF_CONFIRM_UPLOAD_USER, false);
                final State state = MainActivity.getStaticState();

                if (userConfirmed && null != state) {
                    uploadFile( state.dbHelper );
                } else {
                    makeUploadDialog(main);
                }
            }
        });
    }

    public void makeUploadDialog(final MainActivity main) {
        final SharedPreferences prefs = main.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0 );
        final boolean beAnonymous = prefs.getBoolean(PreferenceKeys.PREF_BE_ANONYMOUS, false);
        final String username = beAnonymous? "anonymous":
                prefs.getString( PreferenceKeys.PREF_USERNAME, "anonymous" );

        final String text = getString(R.string.list_upload) + "\n" + getString(R.string.username) + ": " + username;
        final FragmentActivity a = getActivity();
        if (null != a) {
            WiGLEConfirmationDialog.createConfirmation(a, text, R.id.nav_list, UPLOAD_DIALOG);
        }
    }

    public void makeQuickPausePrefDialog() {
        final String dialogText = getString(R.string.quick_pause_text);
        final String checkboxText = getString(R.string.quick_pause_decision);
        final FragmentActivity a = getActivity();
        if (null != a) {
            WiGLEConfirmationDialog.createCheckboxConfirmation(a, dialogText, checkboxText,
                    PreferenceKeys.PREF_QUICK_PAUSE, ListFragment.QUICK_SCAN_PAUSE,
                    ListFragment.QUICK_SCAN_DO_NOTHING, R.id.nav_list, QUICK_PAUSE_DIALOG);
        }
    }

    @Override
    public void handleDialog(final int dialogId) {
        final FragmentActivity a = getActivity();
        if (null != a) {
            final SharedPreferences prefs = getActivity().getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            final SharedPreferences.Editor editor = prefs.edit();
            switch (dialogId) {
                case UPLOAD_DIALOG:
                    final State state = MainActivity.getStaticState();
                    final boolean userConfirmed = prefs.getBoolean(PreferenceKeys.PREF_CONFIRM_UPLOAD_USER, false);
                    final String authUser = prefs.getString(PreferenceKeys.PREF_AUTHNAME, "");

                    if (!userConfirmed && !authUser.isEmpty()) {
                        //remember the confirmation
                        editor.putBoolean(PreferenceKeys.PREF_CONFIRM_UPLOAD_USER, true);
                        editor.apply();
                    }
                    if (null != state) {
                        uploadFile(state.dbHelper);
                    }
                    break;
                case QUICK_PAUSE_DIALOG:
                    Logging.info("quick pause callback");
                    toggleScan();
                default:
                    Logging.warn("ListFragment unhandled dialogId: " + dialogId);
            }
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
