package net.wigle.wigleandroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.annotation.NonNull;
import com.google.android.material.navigation.NavigationView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.gson.Gson;

import net.wigle.wigleandroid.background.ObservationUploader;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.db.MxcDatabaseHelper;
import net.wigle.wigleandroid.listener.BatteryLevelReceiver;
import net.wigle.wigleandroid.listener.BluetoothReceiver;
import net.wigle.wigleandroid.listener.GPSListener;
import net.wigle.wigleandroid.listener.PhoneState;
import net.wigle.wigleandroid.listener.PrefCheckboxListener;
import net.wigle.wigleandroid.listener.WifiReceiver;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.InstallUtility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.location.LocationManager.GPS_PROVIDER;

public final class MainActivity extends AppCompatActivity {
    //*** state that is retained ***
    public static class State {
        public MxcDatabaseHelper mxcDbHelper;
        public DatabaseHelper dbHelper;
        ServiceConnection serviceConnection;
        WigleService wigleService;
        AtomicBoolean finishing;
        AtomicBoolean transferring;
        MediaPlayer soundPop;
        MediaPlayer soundNewPop;
        WifiLock wifiLock;
        GPSListener gpsListener;
        WifiReceiver wifiReceiver;
        BluetoothReceiver bluetoothReceiver;
        NumberFormat numberFormat0;
        NumberFormat numberFormat1;
        NumberFormat numberFormat8;
        TTS tts;
        boolean inEmulator;
        PhoneState phoneState;
        ObservationUploader observationUploader;
        SetNetworkListAdapter listAdapter;
        String previousStatus;
        int currentTab = R.id.nav_list;
        int previousTab = 0;
        private boolean screenLocked = false;
        private PowerManager.WakeLock wakeLock;
        private int logPointer = 0;
        private String[] logs = new String[25];
        Matcher bssidLogExclusions;
        Matcher bssidDisplayExclusions;
    }

    private State state;
    // *** end of state that is retained ***

    static final Locale ORIG_LOCALE = Locale.getDefault();
    // form auth
    public static final String TOKEN_URL = "https://api.wigle.net/api/v2/activate";
    // no auth
    public static final String SITE_STATS_URL = "https://api.wigle.net/api/v2/stats/site";
    public static final String RANK_STATS_URL = "https://api.wigle.net/api/v2/stats/standings";
    public static final String NEWS_URL = "https://api.wigle.net/api/v2/news/latest";
    // api token auth
    public static final String UPLOADS_STATS_URL = "https://api.wigle.net/api/v2/file/transactions";
    public static final String USER_STATS_URL = "https://api.wigle.net/api/v2/stats/user";
    public static final String OBSERVED_URL = "https://api.wigle.net/api/v2/network/mine";
    public static final String FILE_POST_URL = "https://api.wigle.net/api/v2/file/upload";
    public static final String KML_TRANSID_URL_STEM = "https://api.wigle.net/api/v2/file/kml/";
    public static final String SEARCH_WIFI_URL = "https://api.wigle.net/api/v2/network/search";
    public static final String SEARCH_CELL_URL = "https://api.wigle.net/api/v2/cell/search";

        // registration web view
    public static final String REG_URL = "https://wigle.net/register";

    private static final String LOG_TAG = "wigle";
    public static final String ENCODING = "ISO-8859-1";
    private static final int PERMISSIONS_REQUEST = 1;

    static final String ERROR_STACK_FILENAME = "errorstack";
    static final String ERROR_REPORT_DO_EMAIL = "doEmail";
    public static final String ERROR_REPORT_DIALOG = "doDialog";

    public static final long DEFAULT_SPEECH_PERIOD = 60L;
    public static final long DEFAULT_RESET_WIFI_PERIOD = 90000L;
    public static final long LOCATION_UPDATE_INTERVAL = 1000L;
    public static final long SCAN_STILL_DEFAULT = 3000L;
    public static final long SCAN_DEFAULT = 2000L;
    public static final long SCAN_FAST_DEFAULT = 1000L;
    public static final long SCAN_P_DEFAULT = 30000L;
    public static final long DEFAULT_BATTERY_KILL_PERCENT = 2L;
    private static final long FINISH_TIME_MILLIS = 10L;
    private static final long DESTROY_FINISH_MILLIS = 3000L; // if someone force kills, how long until service finishes

    public static final String ACTION_END = "net.wigle.wigleandroid.END";
    public static final String ACTION_UPLOAD = "net.wigle.wigleandroid.UPLOAD";
    public static final String ACTION_PAUSE = "net.wigle.wigleandroid.PAUSE";
    public static final String ACTION_SCAN = "net.wigle.wigleandroid.SCAN";

    public static final String FRAGMENT_TAG_PREFIX = "VisibleFragment-";

    public static final boolean DEBUG_CELL_DATA = false;
    public static final boolean DEBUG_BLUETOOTH_DATA = false;

    private static MainActivity mainActivity;
    private BatteryLevelReceiver batteryLevelReceiver;
    private boolean playServiceShown = false;

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;

    private static final String STATE_FRAGMENT_TAG = "StateFragmentTag";
    public static final String LIST_FRAGMENT_TAG = "ListFragmentTag";


    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        info("MAIN onCreate. state:  " + state);

        // set language
        setLocale(this);
        setContentView(R.layout.main);

        mainActivity = this;

        // set language
        setLocale(this);

        setupPermissions();
        setupMenuDrawer();

        // do some of our own error handling, write a file with the stack
        final UncaughtExceptionHandler origHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (!(origHandler instanceof WigleUncaughtExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(
                    new WigleUncaughtExceptionHandler(getApplicationContext(), origHandler));
        }

        // test the error reporting
        // if( true ){ throw new RuntimeException( "weee" ); }

        final FragmentManager fm = getSupportFragmentManager();
        // force the retained fragments to live
        fm.executePendingTransactions();
        StateFragment stateFragment = (StateFragment) fm.findFragmentByTag(STATE_FRAGMENT_TAG);

        final SharedPreferences prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        pieScanningSettings(prefs);

        if (stateFragment != null && stateFragment.getState() != null) {
            info("MAIN: using retained stateFragment state");
            // pry an orientation change, which calls destroy, but we get this from retained fragment
            state = stateFragment.getState();

            // tell those that need it that we have a new context
            state.gpsListener.setMainActivity(this);
            state.wifiReceiver.setMainActivity(this);
            if (state.observationUploader != null) {
                state.observationUploader.setContext(this);
            }
        } else {
            info("MAIN: creating new state");
            state = new State();
            state.finishing = new AtomicBoolean(false);
            state.transferring = new AtomicBoolean(false);

            // set it up for retain
            stateFragment = new StateFragment();
            stateFragment.setState(state);
            fm.beginTransaction().add(stateFragment, STATE_FRAGMENT_TAG).commit();
            // new run, reset
            final float prevRun = prefs.getFloat(ListFragment.PREF_DISTANCE_RUN, 0f);
            Editor edit = prefs.edit();
            edit.putFloat(ListFragment.PREF_DISTANCE_RUN, 0f);
            edit.putLong(ListFragment.PREF_STARTTIME_RUN, System.currentTimeMillis());
            edit.putLong(ListFragment.PREF_STARTTIME_CURRENT_SCAN, System.currentTimeMillis());
            edit.putLong(ListFragment.PREF_CUMULATIVE_SCANTIME_RUN, 0L);
            edit.putFloat(ListFragment.PREF_DISTANCE_PREV_RUN, prevRun);
            edit.apply();
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (state.wakeLock == null) {
            state.wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "wiglewifiwardriving:DoNotDimScreen");
            if (state.wakeLock.isHeld()) {
                state.wakeLock.release();
            }
        }

        final String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // DO NOT turn these into |=, they will cause older dalvik verifiers to freak out
        state.inEmulator = id == null;
        state.inEmulator = state.inEmulator || "sdk".equals(android.os.Build.PRODUCT);
        state.inEmulator = state.inEmulator || "google_sdk".equals(android.os.Build.PRODUCT);

        info("id: '" + id + "' inEmulator: " + state.inEmulator + " product: " + android.os.Build.PRODUCT);
        info("android release: '" + Build.VERSION.RELEASE);

        if (state.numberFormat0 == null) {
            state.numberFormat0 = NumberFormat.getNumberInstance(Locale.US);
            if (state.numberFormat0 instanceof DecimalFormat) {
                state.numberFormat0.setMaximumFractionDigits(0);
            }
        }

        if (state.numberFormat1 == null) {
            state.numberFormat1 = NumberFormat.getNumberInstance(Locale.US);
            if (state.numberFormat1 instanceof DecimalFormat) {
                state.numberFormat1.setMaximumFractionDigits(1);
            }
        }

        if (state.numberFormat8 == null) {
            state.numberFormat8 = NumberFormat.getNumberInstance(Locale.US);
            if (state.numberFormat8 instanceof DecimalFormat) {
                state.numberFormat8.setMaximumFractionDigits(8);
                state.numberFormat8.setMinimumFractionDigits(8);
            }
        }

        info("setupService");
        setupService();
        info("setupDatabase");
        setupDatabase();
        info("setupBattery");
        setupBattery();
        info("setupSound");
        setupSound();
        info("setupActivationDialog");
        setupActivationDialog();
        info("setupBluetooth");
        setupBluetooth();
        info("setupWifi");
        setupWifi();
        info("setupLocation"); // must be after setupWifi
        setupLocation();
        info("setup tabs");
        if (savedInstanceState == null) {
            setupFragments();
        }
        setupFilters(prefs);

        // ALIBI: don't inherit MxC implant failures from backups.
        if (InstallUtility.isFirstInstall(this)) {
            SharedPreferences mySPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = mySPrefs.edit();
            editor.remove(ListFragment.PREF_MXC_REINSTALL_ATTEMPTED);
            editor.apply();
        }

        //TODO: if we can determine whether DB needs updating, we can avoid copying every time
        //if (!state.mxcDbHelper.isPresent()) {
        try {
            state.mxcDbHelper.implantMxcDatabase();
        } catch (IOException ex) {
            MainActivity.error("unable to implant mcc/mnc db", ex);
        }
        //}

        // rksh 20160202 - api/authuser secure preferences storage
        checkInitKeystore();

        // show the list by default
        selectFragment(state.currentTab);
        info("onCreate setup complete");
    }

    private void pieScanningSettings(final SharedPreferences prefs) {
        if (Build.VERSION.SDK_INT == 28) {
            for (final String key : Arrays.asList(ListFragment.PREF_SCAN_PERIOD_STILL,
                    ListFragment.PREF_SCAN_PERIOD, ListFragment.PREF_SCAN_PERIOD_FAST)) {
                pieScanSet(prefs, key);
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    private void pieScanSet(final SharedPreferences prefs, final String key) {
        if (-1 == prefs.getLong(key, -1)) {
            info("Setting 30 second scan for " + key + " due to broken Android Pie");
            prefs.edit().putLong(key, SCAN_P_DEFAULT).commit();
        }
    }

    /**
     * migration method for viable APIs to switch to encrypted AUTH_TOKENs
     */
    private void checkInitKeystore() {
        final SharedPreferences prefs = getApplicationContext().
                getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        if (TokenAccess.checkMigrateKeystoreVersion(prefs, this)) {
            // successful migration should remove the password value
            if (!prefs.getString(ListFragment.PREF_PASSWORD,
                    "").isEmpty()) {
                final Editor editor = prefs.edit();
                editor.remove(ListFragment.PREF_PASSWORD);
                editor.apply();
            }
        } else {
            MainActivity.info("Not able to upgrade key storage.");
        }
    }

    private void setupPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            final List<String> permissionsNeeded = new ArrayList<>();
            final List<String> permissionsList = new ArrayList<>();
            if (!addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION)) {
                permissionsNeeded.add(mainActivity.getString(R.string.gps_permission));
            }
            if (!addPermission(permissionsList, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                permissionsNeeded.add(mainActivity.getString(R.string.cell_permission));
            }
            addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            addPermission(permissionsList, Manifest.permission.BLUETOOTH);

            if (!permissionsList.isEmpty()) {
                // The permission is NOT already granted.
                // Check if the user has been asked about this permission already and denied
                // it. If so, we want to give more explanation about why the permission is needed.
                // 20170324 rksh: disabled due to
                // https://stackoverflow.com/questions/35453759/android-screen-overlay-detected-message-if-user-is-trying-to-grant-a-permissio
                /*String message = mainActivity.getString(R.string.please_allow);
                for (int i = 0; i < permissionsNeeded.size(); i++) {
                    if (i > 0) message += ", ";
                    message += permissionsNeeded.get(i);
                }

                if (permissionsList.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    message = mainActivity.getString(R.string.allow_storage);
                } */

                MainActivity.info("no permission for " + permissionsNeeded);

                // Fire off an async request to actually get the permission
                // This will show the standard permission request dialog UI
                requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                        PERMISSIONS_REQUEST);
            }
        }
    }

    private boolean addPermission(List<String> permissionsList, String permission) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsList.add(permission);
                // Check for Rationale Option
                if (!shouldShowRequestPermissionRationale(permission))
                    return false;
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String permissions[],
                                           @NonNull final int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST: {
                info("location grant response permissions: " + Arrays.toString(permissions)
                        + " grantResults: " + Arrays.toString(grantResults));

                boolean restart = false;
                for (int i = 0; i < permissions.length; i++) {
                    final String permission = permissions[i];
                    if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)
                            && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        restart = true;
                    }
                }

                if (restart) {
                    // restart the app now that we can talk to the database
                    info("Restarting to pick up storage permission");
                    finishSoon(FINISH_TIME_MILLIS, true);
                }
                return;
            }

            default:
                warn("Unhandled onRequestPermissionsResult code: " + requestCode);
        }
    }

    private void setupMenuDrawer() {

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                final ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) actionBar.setTitle("Menu");
            }
        };
        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        final NavigationView navigationView = findViewById(R.id.left_drawer);
        navigationView.getMenu().setGroupVisible(R.id.stats_group, false);

        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        // set item as selected to persist highlight

                        menuItem.setCheckable(true);
                        if (menuItem.getItemId() == R.id.nav_stats) {
                            menuItem.setChecked(!menuItem.isChecked());
                        } else {
                            menuItem.setChecked(true);

                            if (state.previousTab != menuItem.getItemId() && state.previousTab != 0) {
                                MenuItem mPreviousMenuItem = navigationView.getMenu().findItem(state.previousTab);
                                mPreviousMenuItem.setChecked(false);
                            }
                        }
                        state.previousTab = menuItem.getItemId();

                        // close drawer when item is tapped
                        if (R.id.nav_stats == menuItem.getItemId()) {
                            MainActivity.info("Nav stats clicked");
                            showSubmenu(navigationView.getMenu(), R.id.stats_group, menuItem.isChecked() );
                        } else {
                            if (R.id.nav_site_stats != menuItem.getItemId() &&
                                    R.id.nav_user_stats != menuItem.getItemId() &&
                                    R.id.nav_rank != menuItem.getItemId() )
                            showSubmenu(navigationView.getMenu(), R.id.stats_group, false);
                            mDrawerLayout.closeDrawers();
                            selectFragment(menuItem.getItemId());
                        }
                        return true;
                    }
                });
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        int menuSubColor = 0xE0777777;
        MenuItem uStats = navigationView.getMenu().findItem(R.id.nav_user_stats);
        SpannableString spanString = new SpannableString("    "+uStats.getTitle().toString());
        spanString.setSpan(new ForegroundColorSpan(menuSubColor), 0,     spanString.length(), 0); //fix the color to white
        uStats.setTitle(spanString);

        MenuItem sStats = navigationView.getMenu().findItem(R.id.nav_site_stats);
        spanString = new SpannableString("    "+sStats.getTitle().toString());
        spanString.setSpan(new ForegroundColorSpan(menuSubColor), 0,     spanString.length(), 0); //fix the color to white
        sStats.setTitle(spanString);

        MenuItem rStats = navigationView.getMenu().findItem(R.id.nav_rank);
        spanString = new SpannableString("    "+rStats.getTitle().toString());
        spanString.setSpan(new ForegroundColorSpan(menuSubColor), 0,     spanString.length(), 0); //fix the color to white
        rStats.setTitle(spanString);

        navigationView.getMenu().getItem(0).setCheckable(true);
        navigationView.getMenu().getItem(0).setChecked(true);
        // end drawer setup
    }

    /**
     * Swaps fragments in the main content view
     */
    public void selectFragment(final int itemId) {
        if (itemId == R.id.nav_exit) {
            finishSoon();
            return;
        }

        final Map<Integer, String> fragmentTitles = new HashMap<>();
        fragmentTitles.put(R.id.nav_list, getString(R.string.mapping_app_name));
        fragmentTitles.put(R.id.nav_dash, getString(R.string.dashboard_app_name));
        fragmentTitles.put(R.id.nav_data, getString(R.string.data_activity_name));
        fragmentTitles.put(R.id.nav_search, getString(R.string.tab_search));
        fragmentTitles.put(R.id.nav_news, getString(R.string.news_app_name));
        fragmentTitles.put(R.id.nav_rank, getString(R.string.rank_stats_app_name));
        fragmentTitles.put(R.id.nav_stats, getString(R.string.tab_stats));
        fragmentTitles.put(R.id.nav_uploads, getString(R.string.uploads_app_name));
        fragmentTitles.put(R.id.nav_settings, getString(R.string.settings_app_name));
        fragmentTitles.put(R.id.nav_exit, getString(R.string.menu_exit));
        //fragmentTitles.put(R.id.nav_, getString(R.string.site_stats_app_name));

        try {
            final FragmentManager fragmentManager = getSupportFragmentManager();
            final Fragment frag = (Fragment) classForFragmentNavId(itemId).newInstance();
            Bundle bundle = new Bundle();
            frag.setArguments(bundle);


            //fragmentManager.findFragmentByTag(FRAGMENT_TAG_PREFIX+itemId);

            if (null == frag) {
                final String maybeName = getResources().getResourceName(itemId);
                MainActivity.error("null fragment for: " + String.format("0x%08X", itemId) + " (" + maybeName + ")");
            }

            try {
                fragmentManager.beginTransaction()
                        .replace(R.id.tabcontent, frag, FRAGMENT_TAG_PREFIX+itemId)
                        .commit();
            } catch (final NullPointerException | IllegalStateException ex) {
                final String message = "exception in fragment switch: " + ex;
                error(message, ex);
            }

            // Highlight the selected item, update the title, and close the drawer
            state.currentTab = itemId;
            setTitle(fragmentTitles.get(itemId));
        } catch (IllegalAccessException ex) {
            MainActivity.error("Unable to get fragment for id: "+itemId, ex);
        } catch (InstantiationException ex) {
            MainActivity.error("Unable to make fragment for id: "+itemId, ex);
        }
    }

    private void showSubmenu(final Menu menu, final int submenuGroupId, final boolean visible) {
        menu.setGroupVisible(submenuGroupId, visible);
    }

    @Override
    public void setTitle(CharSequence title) {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setTitle(title);
    }

    private void setupFragments() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        info("Creating ListFragment");
        ListFragment listFragment = new ListFragment();
        Bundle bundle = new Bundle();
        listFragment.setArguments(bundle);

        transaction.add(R.id.tabcontent, listFragment, FRAGMENT_TAG_PREFIX+R.id.nav_list);
        transaction.commit();
    }

    private static Class classForFragmentNavId(final int navId) {
        switch(navId) {
            case R.id.nav_list:
                return ListFragment.class;
            case R.id.nav_dash:
                return DashboardFragment.class;
            case R.id.nav_data:
                return DataFragment.class;
            case R.id.nav_search:
                return SearchFragment.class;
            case R.id.nav_map:
                return MappingFragment.class;
            case R.id.nav_user_stats:
                return UserStatsFragment.class;
            case R.id.nav_rank:
                return RankStatsFragment.class;
            case R.id.nav_site_stats:
                return SiteStatsFragment.class;
            case R.id.nav_news:
                return NewsFragment.class;
            case R.id.nav_uploads:
                return UploadsFragment.class;
            case R.id.nav_settings:
                return SettingsFragment.class;
            default:
                return ListFragment.class;
        }
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (featureId == Window.FEATURE_ACTION_BAR && menu != null) {
            if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
                try {
                    Method m = menu.getClass().getDeclaredMethod(
                            "setOptionalIconsVisible", Boolean.TYPE);
                    m.setAccessible(true);
                    m.invoke(menu, true);
                } catch (NoSuchMethodException ex) {
                    error("onMenuOpened no such method: " + ex, ex);
                } catch (Exception ex) {
                    error("onMenuOpened ex: " + ex, ex);
                }
            }
        }
        return super.onMenuOpened(featureId, menu);
    }

    // be careful with this
    public static MainActivity getMainActivity() {
        return mainActivity;
    }

    static void setLockScreen(Fragment fragment, boolean lockScreen) {
        final MainActivity main = getMainActivity(fragment);
        if (main != null) {
            main.setLockScreen(lockScreen);
        }
    }

    public static boolean isHighDefinition() {
        if (Build.VERSION.SDK_INT >= 17) {
            DisplayMetrics metrics = new DisplayMetrics();
            MainActivity.getMainActivity().getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            int dpi = metrics.densityDpi;
            if (dpi >= 240) {
                return true;
            }
        }
        return false;
    }

    static boolean isScreenLocked(Fragment fragment) {
        final MainActivity main = getMainActivity(fragment);
        return main != null && main.getState().screenLocked;
    }

    @SuppressLint("Wakelock")
    private void setLockScreen(boolean lockScreen) {
        state.screenLocked = lockScreen;
        if (lockScreen) {
            if (!state.wakeLock.isHeld()) {
                MainActivity.info("acquire wake lock");
                state.wakeLock.acquire();
            }
        } else if (state.wakeLock.isHeld()) {
            MainActivity.info("release wake lock");
            state.wakeLock.release();
        }
    }

    // Fragment-style dialog
    public static class ConfirmationDialog extends DialogFragment {
        public static ConfirmationDialog newInstance(final String message, final int tabPos, final int dialogId) {
            final ConfirmationDialog frag = new ConfirmationDialog();
            Bundle args = new Bundle();
            args.putString("message", message);
            args.putInt("tabPos", tabPos);
            args.putInt("dialogId", dialogId);
            frag.setArguments(args);
            return frag;
        }

        /**
         * alternate instantiation with a prefs-back checkbox inline - String prefs only
         * @param message
         * @param checkboxLabel
         * @param tabPos
         * @param dialogId
         * @return
         */
        public static ConfirmationDialog newInstance(final String message, final String checkboxLabel,
                                                     final String persistPrefKey,
                                                     final String persistPrefAgreeValue,
                                                     final String persistPrefDisagreeValue,
                                                     final int tabPos,
                                                     final int dialogId) {
            final ConfirmationDialog frag = new ConfirmationDialog();
            Bundle args = new Bundle();
            args.putString("message", message);
            args.putInt("tabPos", tabPos);
            args.putInt("dialogId", dialogId);
            args.putString("checkboxLabel", checkboxLabel);
            args.putString("persistPref", persistPrefKey);
            args.putString("persistPrefAgreeValue", persistPrefAgreeValue);
            args.putString("persistPrefDisagreeValue", persistPrefDisagreeValue);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setCancelable(true);
            builder.setTitle("Confirmation"); //TODO: literal string
            final String checkboxLabel = getArguments().containsKey("checkboxLabel") ? getArguments().getString("checkboxLabel"): null;
            if (null != checkboxLabel) {
                View checkBoxView = View.inflate(activity, R.layout.checkbox, null);
                CheckBox checkBox = (CheckBox) checkBoxView.findViewById(R.id.checkbox);
                checkBox.setText(checkboxLabel);
                builder.setView(checkBoxView);
            }
            final String persistPrefKey = getArguments().containsKey("persistPref") ?
                    getArguments().getString("persistPref"): null;
            final String persistPrefAgreeValue = getArguments().containsKey("persistPrefAgreeValue") ?
                    getArguments().getString("persistPrefAgreeValue"): null;
            final String persistPrefDisagreeValue = getArguments().containsKey("persistPrefDisagreeValue") ?
                    getArguments().getString("persistPrefDisagreeValue"): null;

            builder.setMessage(getArguments().getString("message"));
            final int tabPos = getArguments().getInt("tabPos");
            final int dialogId = getArguments().getInt("dialogId");
            final SharedPreferences prefs = activity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );


            final AlertDialog ad = builder.create();
            // ok
            ad.setButton(DialogInterface.BUTTON_POSITIVE, activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    try {
                        if (null != persistPrefKey) {
                            CheckBox checkBox = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.checkbox);
                            if (checkBox.isChecked()) {
                                final SharedPreferences.Editor editor = prefs.edit();
                                editor.putString(persistPrefKey, persistPrefAgreeValue);
                                editor.apply();
                            }
                        }
                        dialog.dismiss();
                        final Activity activity = getActivity();
                        if (activity == null) {
                            info("activity is null in dialog. tabPos: " + tabPos + " dialogId: " + dialogId);
                        } else if (activity instanceof MainActivity) {
                            final MainActivity mainActivity = (MainActivity) activity;
                            if (mainActivity.getState() != null) {
                                final String maybeName = getResources().getResourceName(tabPos);
                                //DEBUG: MainActivity.info("Attempting lookup for: " + String.format("0x%08X", tabPos) + " (" + maybeName + ")");
                                FragmentManager fragmentManager = ((MainActivity) activity).getSupportFragmentManager();
                                final Fragment fragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG_PREFIX+tabPos);
                                if (fragment == null) {
                                    MainActivity.error("null fragment for: " + String.format("0x%08X", tabPos) + " (" + maybeName + ")");
                                    //TODO: might behoove us to show an error here
                                } else {
                                    ((DialogListener) fragment).handleDialog(dialogId);
                                }
                            }
                        } else {
                            ((DialogListener) activity).handleDialog(dialogId);
                        }
                    } catch (Exception ex) {
                        // guess it wasn't there anyways
                        MainActivity.info("exception handling fragment alert dialog: " + ex, ex);
                    }
                }
            });

            // cancel
            ad.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    try {
                        if (null != persistPrefKey) {
                            CheckBox checkBox = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.checkbox);
                            if (checkBox.isChecked()) {
                                final SharedPreferences.Editor editor = prefs.edit();
                                editor.putString(persistPrefKey, persistPrefDisagreeValue);
                                editor.apply();
                            }
                        }
                        dialog.dismiss();
                    } catch (Exception ex) {
                        // guess it wasn't there anyways
                        MainActivity.info("exception dismissing fragment alert dialog: " + ex, ex);
                    }
                }
            });

            return ad;
        }
    }

    static void createConfirmation(final FragmentActivity activity, final String message,
                                   final int tabPos, final int dialogId) {
        try {
            final FragmentManager fm = activity.getSupportFragmentManager();
            final ConfirmationDialog dialog = ConfirmationDialog.newInstance(message, tabPos, dialogId);
            final String tag = tabPos + "-" + dialogId + "-" + activity.getClass().getSimpleName();
            info("tag: " + tag + " fm: " + fm);
            dialog.show(fm, tag);
        } catch (WindowManager.BadTokenException ex) {
            MainActivity.info("exception showing dialog, view probably changed: " + ex, ex);
        } catch (final IllegalStateException ex) {
            final String errorMessage = "Exception trying to show dialog: " + ex;
            MainActivity.error(errorMessage, ex);
        }
    }

    static void createCheckboxConfirmation(final FragmentActivity activity, final String message,
                                           final String checkboxLabel, final String persistPrefKey,
                                           final String persistPrefAgreeValue,
                                           final String persistPrefDisagreeValue,
                                   final int tabPos, final int dialogId) {
        try {
            final FragmentManager fm = activity.getSupportFragmentManager();
            final ConfirmationDialog dialog = ConfirmationDialog.newInstance(message, checkboxLabel,
                    persistPrefKey, persistPrefAgreeValue, persistPrefDisagreeValue, tabPos, dialogId);
            final String tag = tabPos + "-" + dialogId + "-" + activity.getClass().getSimpleName();
            info("tag: " + tag + " fm: " + fm);
            dialog.show(fm, tag);
        } catch (WindowManager.BadTokenException ex) {
            MainActivity.info("exception showing dialog, view probably changed: " + ex, ex);
        } catch (final IllegalStateException ex) {
            final String errorMessage = "Exception trying to show dialog: " + ex;
            MainActivity.error(errorMessage, ex);
        }
    }

    private void setupDatabase() {
        // could be set by nonconfig retain
        if (state.dbHelper == null) {
            state.dbHelper = new DatabaseHelper(getApplicationContext());
            //state.dbHelper.checkDB();
            state.dbHelper.start();
            ListFragment.lameStatic.dbHelper = state.dbHelper;
        }
        if (state.mxcDbHelper == null) {
            state.mxcDbHelper = new MxcDatabaseHelper(getApplicationContext());
            //state.mxcDbHelper.start();
        }
    }

    public static CheckBox prefSetCheckBox(final Context context, final View view, final int id,
                                           final String pref, final boolean def) {

        final SharedPreferences prefs = context.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final CheckBox checkbox = (CheckBox) view.findViewById(id);
        checkbox.setChecked(prefs.getBoolean(pref, def));
        return checkbox;
    }

    private static CheckBox prefSetCheckBox(final SharedPreferences prefs, final View view, final int id, final String pref,
                                            final boolean def) {
        final CheckBox checkbox = (CheckBox) view.findViewById(id);
        if (checkbox == null) {
            error("No checkbox for id: " + id);
        } else {
            checkbox.setChecked(prefs.getBoolean(pref, def));
        }
        return checkbox;
    }

    public static CheckBox prefBackedCheckBox(final Activity activity, final View view, final int id,
                                              final String pref, final boolean def) {
        return prefBackedCheckBox(activity, view, id, pref, def, null);
    }

    public static CheckBox prefBackedCheckBox(final Activity activity, final View view, final int id,
                                              final String pref, final boolean def, final PrefCheckboxListener listener) {
        final SharedPreferences prefs = activity.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final Editor editor = prefs.edit();
        final CheckBox checkbox = prefSetCheckBox(prefs, view, id, pref, def);
        checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                editor.putBoolean(pref, isChecked);
                editor.apply();
                if (null != listener) {
                    listener.preferenceSet(isChecked);
                }
            }
        });

        return checkbox;
    }

    public static State getStaticState() {
        final MainActivity mainActivity = getMainActivity();
        return mainActivity == null ? null : mainActivity.getState();
    }

    public State getState() {
        return state;
    }

    static MainActivity getMainActivity(Fragment fragment) {
        final Activity activity = fragment.getActivity();
        if (activity instanceof MainActivity) {
            return (MainActivity) activity;
        } else {
            info("not main activity: " + activity);
        }
        return null;
    }

    /**
     * safely get the canonical path, as this call throws exceptions on some devices
     */
    public static String safeFilePath(final File file) {
        String retval = null;
        try {
            retval = file.getCanonicalPath();
        } catch (Exception ex) {
            MainActivity.error("Failed to get filepath", ex);
        }

        if (retval == null) {
            retval = file.getAbsolutePath();
        }
        return retval;
    }

    public static String getSDPath() {
        return MainActivity.safeFilePath(Environment.getExternalStorageDirectory()) + "/wiglewifi/";
    }

    public static FileOutputStream createFile(final Context context, final String filename, final boolean isCache) throws IOException {
        final String filepath = getSDPath();
        final File path = new File(filepath);

        final boolean hasSD = MainActivity.hasSD();
        if (hasSD) {
            //noinspection ResultOfMethodCallIgnored
            path.mkdirs();
            final String openString = filepath + filename;
            MainActivity.info("openString: " + openString);
            final File file = new File(openString);
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    throw new IOException("Could not create file: " + openString);
                }
            }

            return new FileOutputStream(file);
        } else if (isCache) {
            File file = File.createTempFile(filename, null, context.getCacheDir());
            return new FileOutputStream(file);
        }

        return context.openFileOutput(filename, Context.MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
        MainActivity.info("MAIN: destroy.");
        super.onDestroy();

        try {
            info("unregister batteryLevelReceiver");
            unregisterReceiver(batteryLevelReceiver);
        } catch (final IllegalArgumentException ex) {
            info("batteryLevelReceiver not registered: " + ex);
        }

        try {
            info("unregister wifiReceiver");
            unregisterReceiver(state.wifiReceiver);
        } catch (final IllegalArgumentException ex) {
            info("wifiReceiver not registered: " + ex);
        }

        if (state.tts != null) state.tts.shutdown();

        //TODO: redundant with endBluetooth?
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        } try {
            info("unregister bluetoothReceiver");
            unregisterReceiver( state.bluetoothReceiver );
        } catch ( final IllegalArgumentException ex ) {
            info( "bluetoothReceiver not registered: " + ex );
        }
        if (state.bluetoothReceiver != null) {
            state.bluetoothReceiver.stopScanning();
        }

        finishSoon(DESTROY_FINISH_MILLIS, false);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        info("MAIN: onSaveInstanceState");
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        MainActivity.info("MAIN: pause.");
        super.onPause();

        // deal with wake lock
        if (state.wakeLock.isHeld()) {
            MainActivity.info("release wake lock");
            state.wakeLock.release();
        }
    }

    @Override
    public void onResume() {
        MainActivity.info("MAIN: resume.");
        super.onResume();

        // deal with wake lock
        if (!state.wakeLock.isHeld() && state.screenLocked) {
            MainActivity.info("acquire wake lock");
            state.wakeLock.acquire();
        }

        final int serviceAvailable = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());
        info("GooglePlayServicesAvailable: " + serviceAvailable);
        if (serviceAvailable != ConnectionResult.SUCCESS && !playServiceShown) {
            error("service not available! " + serviceAvailable);
            final Dialog dialog = GooglePlayServicesUtil.getErrorDialog(serviceAvailable, this, 0);
            dialog.show();
            playServiceShown = true;
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onPostResume() {
        MainActivity.info("MAIN: post resume.");
        super.onPostResume();
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        MainActivity.info("MAIN: config changed");
        setLocale(this, newConfig);
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onStart() {
        MainActivity.info("MAIN: start.");
        final SharedPreferences prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        if (prefs.getBoolean(ListFragment.PREF_BLOWED_UP, false)) {
            prefs.edit().putBoolean(ListFragment.PREF_BLOWED_UP, false).commit();
            // activate the email intent
            final Intent intent = new Intent(this, ErrorReportActivity.class);
            intent.putExtra( MainActivity.ERROR_REPORT_DO_EMAIL, true );
            startActivity(intent);
        }

        super.onStart();

    }

    @Override
    public void onStop() {
        MainActivity.info("MAIN: stop.");
        super.onStop();
    }

    @Override
    public void onRestart() {
        MainActivity.info("MAIN: restart.");
        super.onRestart();
    }

    public static Throwable getBaseThrowable(final Throwable throwable) {
        Throwable retval = throwable;
        while (retval.getCause() != null) {
            retval = retval.getCause();
        }
        return retval;
    }

    public static String getBaseErrorMessage(Throwable throwable, final boolean withNewLine) {
        throwable = MainActivity.getBaseThrowable(throwable);
        final String newline = withNewLine ? "\n" : " ";
        return throwable.getClass().getSimpleName() + ":" + newline + throwable.getMessage();
    }

    public static void setLocale(final Activity activity) {
        final Context context = activity.getBaseContext();
        final Configuration config = context.getResources().getConfiguration();
        setLocale(context, config);
    }

    public static void setLocale(final Context context, final Configuration config) {
        final SharedPreferences prefs = context.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final String lang = prefs.getString(ListFragment.PREF_LANGUAGE, "");
        final String current = config.locale.getLanguage();
        MainActivity.info("current lang: " + current + " new lang: " + lang);
        Locale newLocale = null;
        if (!"".equals(lang) && !current.equals(lang)) {
            if (lang.contains("-r")) {
                String[] parts = lang.split("-r");
                MainActivity.info("\tlang: "+parts[0]+" country: "+parts[1]);
                newLocale = new Locale(parts[0], parts[1]);
            } else {
                MainActivity.info("\tlang: "+lang);
                newLocale = new Locale(lang);

            }
        } else if ("".equals(lang) && ORIG_LOCALE != null && !current.equals(ORIG_LOCALE.getLanguage())) {
            newLocale = ORIG_LOCALE;
        }

        if (newLocale != null) {
            Locale.setDefault(newLocale);
            config.locale = newLocale;
            MainActivity.info("setting locale: " + newLocale);
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
        }
    }

    /**
     * create a mediaplayer for a given raw resource id.
     *
     * @param soundId the R.raw. id for a given sound
     * @return the mediaplayer for soundId or null if it could not be created.
     */
    private MediaPlayer createMediaPlayer(final int soundId) {
        final MediaPlayer sound = createMp(getApplicationContext(), soundId);
        if (sound == null) {
            info("sound null from media player");
            return null;
        }
        // try to figure out why sounds stops after a while
        sound.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(final MediaPlayer mp, final int what, final int extra) {
                String whatString;
                switch (what) {
                    case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                        whatString = "error unknown";
                        break;
                    case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                        whatString = "server died";
                        break;
                    default:
                        whatString = "not defined";
                }
                info("media player error \"" + whatString + "\" what: " + what
                        + " extra: " + extra + " mp: " + mp);
                return false;
            }
        });

        return sound;
    }

    /**
     * externalize the file from a given resource id (if it dosen't already exist), write to our dir if there is one.
     *
     * @param context the context to use
     * @param resid   the resource id
     * @param name    the file name to write out
     * @return the uri of a file containing resid's resource
     */
    private static Uri resToFile(final Context context, final int resid, final String name) throws IOException {
        // throw it in our bag of fun.
        String openString = name;
        final boolean hasSD = hasSD();
        if (hasSD) {
            final String filepath = MainActivity.safeFilePath(Environment.getExternalStorageDirectory()) + "/wiglewifi/";
            final File path = new File(filepath);
            //noinspection ResultOfMethodCallIgnored
            path.mkdirs();
            openString = filepath + name;
        }

        final File f = new File(openString);

        // see if it exists already
        if (!f.exists()) {
            info("causing " + MainActivity.safeFilePath(f) + " to be made");
            // make it happen:
            if (!f.createNewFile()) {
                throw new IOException("Could not create file: " + openString);
            }

            InputStream is = null;
            FileOutputStream fos = null;
            try {
                is = context.getResources().openRawResource(resid);
                if (hasSD) {
                    fos = new FileOutputStream(f);
                } else {
                    // XXX: should this be using openString instead? baroo?
                    fos = context.openFileOutput(name, Context.MODE_PRIVATE);

                }

                final byte[] buff = new byte[1024];
                int rv;
                while ((rv = is.read(buff)) > -1) {
                    fos.write(buff, 0, rv);
                }
            } finally {
                if (fos != null) {
                    fos.close();
                }
                if (is != null) {
                    is.close();
                }
            }
        }
        return Uri.fromFile(f);
    }

    /**
     * create a media player (trying several paths if available)
     *
     * @param context the context to use
     * @param resid   the resource to use
     * @return the media player for resid (or null if it wasn't creatable)
     */
    private static MediaPlayer createMp(final Context context, final int resid) {
        try {
            MediaPlayer mp = MediaPlayer.create(context, resid);
            // this can fail for many reasons, but android 1.6 on archos5 definitely hates creating from resource
            if (mp == null) {
                Uri sounduri;
                // XXX: find a better way? baroo.
                if (resid == R.raw.pop) {
                    sounduri = resToFile(context, resid, "pop.wav");
                } else if (resid == R.raw.newpop) {
                    sounduri = resToFile(context, resid, "newpop.wav");
                } else {
                    info("unknown raw sound id:" + resid);
                    return null;
                }
                mp = MediaPlayer.create(context, sounduri);
                // may still end up null
            }

            return mp;
        } catch (IOException ex) {
            error("ioe create failed: " + ex, ex);
            // fall through
        } catch (IllegalArgumentException ex) {
            error("iae create failed: " + ex, ex);
            // fall through
        } catch (SecurityException ex) {
            error("se create failed: " + ex, ex);
            // fall through
        } catch (Resources.NotFoundException ex) {
            error("rnfe create failed(" + resid + "): " + ex, ex);
        }
        return null;
    }

    public boolean isMuted() {
        //noinspection SimplifiableIfStatement
        if (state.phoneState != null && state.phoneState.isPhoneActive()) {
            // always be quiet when the phone is active
            return true;
        }
        return getSharedPreferences(ListFragment.SHARED_PREFS, 0)
                .getBoolean(ListFragment.PREF_MUTED, true);
    }

    public static void sleep(final long sleep) {
        try {
            Thread.sleep(sleep);
        } catch (final InterruptedException ex) {
            // no worries
        }
    }

    public static void info(final String value) {
        Log.i(LOG_TAG, Thread.currentThread().getName() + "] " + value);
        saveLog(value);
    }

    public static void warn(final String value) {
        Log.w(LOG_TAG, Thread.currentThread().getName() + "] " + value);
        saveLog(value);
    }

    public static void error(final String value) {
        Log.e(LOG_TAG, Thread.currentThread().getName() + "] " + value);
        saveLog(value);
    }

    public static void info(final String value, final Throwable t) {
        Log.i(LOG_TAG, Thread.currentThread().getName() + "] " + value, t);
        saveLog(value);
    }

    public static void warn(final String value, final Throwable t) {
        Log.w(LOG_TAG, Thread.currentThread().getName() + "] " + value, t);
        saveLog(value);
    }

    public static void error(final String value, final Throwable t) {
        Log.e(LOG_TAG, Thread.currentThread().getName() + "] " + value, t);
        saveLog(value);
    }

    private static void saveLog(final String value) {
        final State state = getStaticState();
        if (state == null) return;
        final int pointer = state.logPointer++ % state.logs.length;
        state.logs[pointer] = SimpleDateFormat.getTimeInstance().format(new Date()) + " "
                + Thread.currentThread().getName() + "] " + value;
        if (pointer > 10000 * state.logs.length) {
            state.logPointer -= 100 * state.logs.length;
        }
    }

    /**
     * get the network LRU cache
     *
     * @return network cache
     */
    public static ConcurrentLinkedHashMap<String, Network> getNetworkCache() {
        return ListFragment.lameStatic.networkCache;
    }

    public static void addNetworkToMap(final Network network) {
        final FragmentManager fragmentManager = MainActivity.mainActivity.getSupportFragmentManager();
        if (getStaticState().currentTab == R.id.nav_map) {
            // Map is visible, give it the new network
            final State state = mainActivity.getState();

            final MappingFragment f = (MappingFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG_PREFIX+R.id.nav_map);
            if (f != null) {
                f.addNetwork(network);
            }
        }
    }

    public static void updateNetworkOnMap(final Network network) {
        final FragmentManager fragmentManager = MainActivity.mainActivity.getSupportFragmentManager();
        if (getStaticState().currentTab == R.id.nav_map) {
            // Map is visible, give it the new network
            final State state = mainActivity.getState();
            final MappingFragment f = (MappingFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG_PREFIX+R.id.nav_map);
            if (f != null) {
                f.updateNetwork(network);
            }
        }
    }

    public static void reclusterMap() {
        final FragmentManager fragmentManager = MainActivity.mainActivity.getSupportFragmentManager();
        if (getStaticState().currentTab == R.id.nav_map) {
            // Map is visible, give it the new network
            final State state = mainActivity.getState();
            final MappingFragment f = (MappingFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG_PREFIX+R.id.nav_map);
            if (f != null) {
                f.reCluster();
            }
        }
    }

    public static void writeError(final Thread thread, final Throwable throwable, final Context context) {
        writeError(thread, throwable, context, null);
    }

    public static void writeError(final Thread thread, final Throwable throwable, final Context context, final String detail) {
        try {
            final String error = "Thread: " + thread + " throwable: " + throwable;
            error(error, throwable);
            if (hasSD()) {
                File file = new File(MainActivity.safeFilePath(Environment.getExternalStorageDirectory()) + "/wiglewifi/");
                //noinspection ResultOfMethodCallIgnored
                file.mkdirs();
                file = new File(MainActivity.safeFilePath(Environment.getExternalStorageDirectory())
                        + "/wiglewifi/" + ERROR_STACK_FILENAME + "_" + System.currentTimeMillis() + ".txt");
                error("Writing stackfile to: " + MainActivity.safeFilePath(file) + "/" + file.getName());
                if (!file.exists()) {
                    if (!file.createNewFile()) {
                        throw new IOException("Cannot create file: " + file);
                    }
                }
                final FileOutputStream fos = new FileOutputStream(file);

                try {
                    final String baseErrorMessage = MainActivity.getBaseErrorMessage(throwable, false);
                    StringBuilder builder = new StringBuilder("WigleWifi error log - ");
                    final DateFormat format = SimpleDateFormat.getDateTimeInstance();
                    builder.append(format.format(new Date())).append("\n");
                    final PackageManager pm = context.getPackageManager();
                    final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
                    builder.append("versionName: ").append(pi.versionName).append("\n");
                    builder.append("baseError: ").append(baseErrorMessage).append("\n\n");
                    if (detail != null) {
                        builder.append("detail: ").append(detail).append("\n");
                    }
                    builder.append("packageName: ").append(pi.packageName).append("\n");
                    builder.append("MODEL: ").append(android.os.Build.MODEL).append("\n");
                    builder.append("RELEASE: ").append(android.os.Build.VERSION.RELEASE).append("\n");

                    builder.append("BOARD: ").append(android.os.Build.BOARD).append("\n");
                    builder.append("BRAND: ").append(android.os.Build.BRAND).append("\n");
                    // android 1.6 android.os.Build.CPU_ABI;
                    builder.append("DEVICE: ").append(android.os.Build.DEVICE).append("\n");
                    builder.append("DISPLAY: ").append(android.os.Build.DISPLAY).append("\n");
                    builder.append("FINGERPRINT: ").append(android.os.Build.FINGERPRINT).append("\n");
                    builder.append("HOST: ").append(android.os.Build.HOST).append("\n");
                    builder.append("ID: ").append(android.os.Build.ID).append("\n");
                    // android 1.6: android.os.Build.MANUFACTURER;
                    builder.append("PRODUCT: ").append(android.os.Build.PRODUCT).append("\n");
                    builder.append("TAGS: ").append(android.os.Build.TAGS).append("\n");
                    builder.append("TIME: ").append(android.os.Build.TIME).append("\n");
                    builder.append("TYPE: ").append(android.os.Build.TYPE).append("\n");
                    builder.append("USER: ").append(android.os.Build.USER).append("\n");

                    // write to file
                    fos.write(builder.toString().getBytes(ENCODING));
                } catch (Throwable er) {
                    // ohwell
                    error("error getting data for error: " + er, er);
                }

                fos.write((error + "\n\n").getBytes(ENCODING));
                throwable.printStackTrace(new PrintStream(fos));
                try {
                    fos.write((error + "\n\n").getBytes(ENCODING));
                    for (final String log : getLogLines()) {
                        fos.write(log.getBytes(ENCODING));
                        fos.write("\n".getBytes(ENCODING));
                    }
                } catch (Throwable er) {
                    // ohwell
                    error("error getting logs for error: " + er, er);
                }
                fos.close();
            }
        } catch (final Exception ex) {
            error("error logging error: " + ex, ex);
            ex.printStackTrace();
        }
    }

    public static Iterable<String> getLogLines() {
        final State state = getStaticState();
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {
                    int currentPointer = state.logPointer;
                    final int maxPointer = currentPointer + state.logs.length;

                    @Override
                    public boolean hasNext() {
                        return state.logs[currentPointer % state.logs.length] != null && currentPointer < maxPointer;
                    }

                    @Override
                    public String next() {
                        final String retval = state.logs[currentPointer % state.logs.length];
                        currentPointer++;
                        return retval;
                    }
                };
            }
        };
    }

    public static boolean hasSD() {
        File sdCard = new File(MainActivity.safeFilePath(Environment.getExternalStorageDirectory()) + "/");
        MainActivity.info("exists: " + sdCard.exists() + " dir: " + sdCard.isDirectory()
                + " read: " + sdCard.canRead() + " write: " + sdCard.canWrite()
                + " path: " + sdCard.getAbsolutePath());

        return sdCard.exists() && sdCard.isDirectory() && sdCard.canRead() && sdCard.canWrite();
    }

    private void setupSound() {
        // could have been retained
        if (state.soundPop == null) {
            state.soundPop = createMediaPlayer(R.raw.pop);
        }
        if (state.soundNewPop == null) {
            state.soundNewPop = createMediaPlayer(R.raw.newpop);
        }

        // make volume change "media"
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        try {
            if (TTS.hasTTS()) {
                // don't reuse an old one, has to be on *this* context
                if (state.tts != null) {
                    state.tts.shutdown();
                }
                // this has to have the parent activity, for whatever wacky reasons
                state.tts = new TTS(this);
            }
        } catch (Exception ex) {
            error("exception setting TTS: " + ex, ex);
        }

        TelephonyManager tele = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tele != null && state.phoneState == null) {
            state.phoneState = new PhoneState();
            final int signal_strengths = 256;
            try {
                tele.listen(state.phoneState, PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | signal_strengths);
            } catch (SecurityException ex) {
                info("cannot get call state, will play audio over any telephone calls: " + ex);
            }
        }
    }

    /**
     * Instantiate both both BSSID matchers - inital load
     * @param prefs
     */
    private void setupFilters(final SharedPreferences prefs) {
        if (null != state) {
            state.bssidDisplayExclusions = generateBssidFilterMatcher(prefs, ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS);
            state.bssidLogExclusions = generateBssidFilterMatcher(prefs, ListFragment.PREF_EXCLUDE_LOG_ADDRS);
            //TODO: port SSID matcher over as well?
        }
    }

    /**
     * Trigger recreation of BSSID address filter from prefs
     * @param addressKey
     */
    public void updateAddressFilter(final String addressKey) {
        if (null != state) {
            final SharedPreferences prefs = this.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
            if (ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS.equals(addressKey)) {
                state.bssidDisplayExclusions = generateBssidFilterMatcher(prefs, ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS);
            } else if (ListFragment.PREF_EXCLUDE_LOG_ADDRS.equals(addressKey)) {
                state.bssidLogExclusions = generateBssidFilterMatcher(prefs, ListFragment.PREF_EXCLUDE_LOG_ADDRS);
            }
        }
    }

    /**
     * Accessor for state BSSID matchers
     * @param addressKey
     * @return
     */
    public Matcher getBssidFilterMatcher(final String addressKey) {
        if (null != state) {
            if (ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS.equals(addressKey)) {
                return state.bssidDisplayExclusions;
            } else if (ListFragment.PREF_EXCLUDE_LOG_ADDRS.equals(addressKey)) {
                return state.bssidLogExclusions;
            }
        }
        return null;
    }

    /**
     * Build a BSSID matcher from preferences for the supplied key
     * @param prefs
     * @param addressKey
     * @return
     */
    private Matcher generateBssidFilterMatcher( final SharedPreferences prefs,  final String addressKey) {
        Gson gson = new Gson();
        Matcher matcher = null;
        final String f = prefs.getString( ListFragment.PREF_EXCLUDE_DISPLAY_ADDRS, "");
        String[] values = gson.fromJson(prefs.getString(addressKey, "[]"), String[].class);
        if(values.length>0) {
            StringBuffer sb = new StringBuffer("^(");
            boolean first = true;
            for (String value: values) {

                if (first) {
                    first = false;
                } else {
                    sb.append("|");
                }
                sb.append(value);
                if (value.length() == 17) {
                    sb.append("$");
                }
            }
            sb.append(")");
            //TODO: debug
            MainActivity.info("building regex from: "+sb.toString());
            Pattern pattern = Pattern.compile( sb.toString(), Pattern.CASE_INSENSITIVE );
            matcher = pattern.matcher( "" );
        }

        return matcher;
    }

    public boolean inEmulator() {
        return state.inEmulator;
    }

    public BatteryLevelReceiver getBatteryLevelReceiver() {
        return batteryLevelReceiver;
    }

    public GPSListener getGPSListener() {
        return state.gpsListener;
    }

    public PhoneState getPhoneState() {
        return state.phoneState;
    }

    @Override
    public boolean isFinishing() {
        return state.finishing.get();
    }

    public boolean isTransferring() {
        return state.transferring.get();
    }

    public boolean isScanning() {
        return isScanning(this);
    }

    public static boolean isScanning(final Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        return prefs.getBoolean(ListFragment.PREF_SCAN_RUNNING, true);
    }

    public void playNewNetSound() {
        try {
            if (state.soundNewPop != null && !state.soundNewPop.isPlaying()) {
                // play sound on something new
                state.soundNewPop.start();
            } else {
                MainActivity.info("soundNewPop is playing or null");
            }
        } catch (IllegalStateException ex) {
            // ohwell, likely already playing
            MainActivity.info("exception trying to play sound: " + ex);
        }
    }

    public void playRunNetSound() {
        try {
            if (state.soundPop != null && !state.soundPop.isPlaying()) {
                // play sound on something new
                state.soundPop.start();
            } else {
                MainActivity.info("soundPop is playing or null");
            }
        } catch (IllegalStateException ex) {
            // ohwell, likely already playing
            MainActivity.info("exception trying to play sound: " + ex);
        }
    }

    public static boolean shouldScanBtByDefault() {
        return ! "Samsung".equalsIgnoreCase(android.os.Build.MANUFACTURER);
    }

    private void setupActivationDialog() {
        final boolean willActivateBt = canBtBeActivated();
        final boolean willActivateWifi = canWifiBeActivated();
        final SharedPreferences prefs = getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        final boolean useBt = (prefs.getBoolean(ListFragment.PREF_SCAN_BT, shouldScanBtByDefault()));

        if ((willActivateBt && useBt) || willActivateWifi) {

            String activationMessages = "";

            if (willActivateBt && useBt) {
                activationMessages += getString(R.string.turn_on_bt);
                if (willActivateWifi) {
                    activationMessages += "\n";
                }
            }

            if (willActivateWifi) {
                activationMessages += getString(R.string.turn_on_wifi);
            }
            // tell user, cuz this takes a little while
            if (!isFinishing()) {
                WiGLEToast.showOverActivity(this, R.string.app_name, activationMessages, Toast.LENGTH_LONG);
            }
        }
    }

    private boolean canWifiBeActivated() {
        final WifiManager wifiManager = (WifiManager) this.getApplicationContext().
                getSystemService(Context.WIFI_SERVICE);
        if (null == wifiManager) {
            return false;
        }
        if (!wifiManager.isWifiEnabled() && !state.inEmulator) {
            return true;
        }
        return false;
    }

    private void setupWifi() {
        // warn about turning off network notification
        final String notifOn = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON);
        if (notifOn != null && "1".equals(notifOn) && state.wifiReceiver == null && !isFinishing()) {
            WiGLEToast.showOverActivity(this, R.string.app_name, getString(R.string.best_results));
        }

        final WifiManager wifiManager = (WifiManager) this.getApplicationContext().
                getSystemService(Context.WIFI_SERVICE);
        final SharedPreferences prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final Editor edit = prefs.edit();

        // keep track of for later
        boolean turnedWifiOn = false;
        if (!wifiManager.isWifiEnabled()) {

            // save so we can turn it back off when we exit
            edit.putBoolean(ListFragment.PREF_WIFI_WAS_OFF, true);

            // just turn it on, but not in emulator cuz it crashes it
            if (!state.inEmulator) {
                MainActivity.info("turning on wifi");
                wifiManager.setWifiEnabled(true);
                MainActivity.info("wifi on");
                turnedWifiOn = true;
            }
        } else {
            edit.putBoolean(ListFragment.PREF_WIFI_WAS_OFF, false);
        }
        edit.apply();

        if (state.wifiReceiver == null) {
            MainActivity.info("new wifiReceiver");
            // wifi scan listener
            // this receiver is the main workhorse of the entire app
            state.wifiReceiver = new WifiReceiver(this, state.dbHelper, getApplicationContext());
            state.wifiReceiver.setupWifiTimer(turnedWifiOn);
        }

        // register wifi receiver
        setupWifiReceiverIntent();

        if (state.wifiLock == null) {
            MainActivity.info("lock wifi radio on");
            // lock the radio on
            state.wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, ListFragment.WIFI_LOCK_NAME);
            state.wifiLock.acquire();
        }
    }

    private void setupWifiReceiverIntent() {
        // register
        MainActivity.info("register BroadcastReceiver");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(state.wifiReceiver, intentFilter);
    }

    private boolean canBtBeActivated() {
        try {
            final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt == null) {
                info("No bluetooth adapter");
                return false;
            }
            if (!bt.isEnabled()) {
                return true;
            }
        } catch (java.lang.SecurityException sex) {
            MainActivity.warn("bt activation security exception");
        }
        return false;
    }

    public void setupBluetooth() {
        final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) {
            info("No bluetooth adapter");
            return;
        }
        final SharedPreferences prefs = getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        final Editor edit = prefs.edit();
        if (prefs.getBoolean(ListFragment.PREF_SCAN_BT, MainActivity.shouldScanBtByDefault())) {
            if (!bt.isEnabled()) {
                info("Enable bluetooth");
                edit.putBoolean(ListFragment.PREF_BT_WAS_OFF, true);
                bt.enable();
            } else {
                edit.putBoolean(ListFragment.PREF_BT_WAS_OFF, false);
            }
            edit.commit();
            if ( state.bluetoothReceiver == null ) {
                MainActivity.info( "new bluetoothReceiver");
                // bluetooth scan listener
                // this receiver is the main workhorse of bluetooth scanning
                state.bluetoothReceiver = new BluetoothReceiver( this, state.dbHelper );
                state.bluetoothReceiver.setupBluetoothTimer(true);
            }
            info("register bluetooth BroadcastReceiver");
            final IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            registerReceiver(state.bluetoothReceiver, intentFilter);
        }
    }

    public void endBluetooth(SharedPreferences prefs) {
        if (state.bluetoothReceiver != null) {
            state.bluetoothReceiver.stopScanning();
        }

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        }
        try {
            info("unregister bluetoothReceiver");
            unregisterReceiver( state.bluetoothReceiver );
            state.bluetoothReceiver = null;
        } catch ( final IllegalArgumentException ex ) {
            //ALIBI: it's fine to call and fail here.
            info( "bluetoothReceiver not registered: " + ex );
        }

        final boolean btWasOff = prefs.getBoolean( ListFragment.PREF_BT_WAS_OFF, false );
        // don't call on emulator, it crashes it
        if ( btWasOff && ! state.inEmulator ) {
            // ALIBI: we disabled this for WiFi since we had weird errors with root window disposal. Uncomment if we get that resolved?
            //WiGLEToast.showOverActivity(this, R.string.app_name, getString(R.string.turning_bt_off));

            // turn it off now that we're done
            MainActivity.info("turning bluetooth back off");
            try {
                final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
                if (bt == null) {
                    info("No bluetooth adapter");
                } else if (bt.isEnabled()) {
                    info("Disable bluetooth");
                    bt.disable();
                }
            } catch (Exception ex) {
                MainActivity.error("exception turning bluetooth back off: " + ex, ex);
            }
        }
    }

    public void bluetoothScan() {
        if (state.bluetoothReceiver != null) {
            state.bluetoothReceiver.bluetoothScan();
        }
    }

    /**
     * Computes the battery level by registering a receiver to the intent triggered
     * by a battery status/level change.
     */
    private void setupBattery() {
        if (batteryLevelReceiver == null) {
            batteryLevelReceiver = new BatteryLevelReceiver();
            IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            registerReceiver(batteryLevelReceiver, batteryLevelFilter);
        }
    }

    public void setTransferring() {
        info("setTransferring");
        state.transferring.set(true);
    }

    public void scheduleScan() {
        state.wifiReceiver.scheduleScan();
    }

    public void speak(final String string) {
        if (!MainActivity.getMainActivity().isMuted() && state.tts != null) {
            state.tts.speak(string);
        }
    }

    public void interruptSpeak() {
        if (state.tts != null) {
            state.tts.stop();
        }
    }

    private void setupService() {
        // could be set by nonconfig retain
        if (state.serviceConnection == null) {
            state.serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(final ComponentName name, final IBinder iBinder) {
                    MainActivity.info(name + " service connected");
                    final WigleService.WigleServiceBinder binder = (WigleService.WigleServiceBinder) iBinder;
                    state.wigleService = binder.getService();
                }

                @Override
                public void onServiceDisconnected(final ComponentName name) {
                    MainActivity.info(name + " service disconnected");
                }
            };

            // have to use the app context to bind to the service, cuz we're in tabs
            // http://code.google.com/p/android/issues/detail?id=2483#c2
            final Intent serviceIntent = new Intent(getApplicationContext(), WigleService.class);
            final boolean bound = getApplicationContext().bindService(serviceIntent, state.serviceConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
            MainActivity.info("service bound: " + bound);
        }
    }

    private void setupLocation() {
        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        try {
            // check if there is a gps
            final LocationProvider locProvider = locationManager.getProvider(GPS_PROVIDER);

            if (locProvider == null && !isFinishing()) {
                WiGLEToast.showOverActivity(this, R.string.app_name, getString(R.string.no_gps_device), Toast.LENGTH_LONG);
            } else if (!locationManager.isProviderEnabled(GPS_PROVIDER) && !isFinishing()) {
                // gps exists, but isn't on
                WiGLEToast.showOverActivity(this, R.string.app_name, getString(R.string.turn_on_gps), Toast.LENGTH_LONG);

                final Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                try {
                    startActivity(myIntent);
                } catch (Exception ex) {
                    error("exception trying to start location activity: " + ex, ex);
                }
            }
        } catch (final SecurityException ex) {
            info("Security exception in setupLocation: " + ex);
            return;
        }

        if (state.gpsListener == null) {
            // force a listener to be created
            final SharedPreferences prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
            internalHandleScanChange(prefs.getBoolean(ListFragment.PREF_SCAN_RUNNING, true));
        }
    }

    public void handleScanChange(final boolean isScanning) {
        final boolean oldIsScanning = isScanning();
        if (isScanning == oldIsScanning) {
            info("main handleScanChange: no difference, returning");
            return;
        }

        final SharedPreferences prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final Editor edit = prefs.edit();
        if (isScanning) {
            edit.putLong(ListFragment.PREF_STARTTIME_CURRENT_SCAN, System.currentTimeMillis());
        } else {
            final long scanTime = prefs.getLong(ListFragment.PREF_CUMULATIVE_SCANTIME_RUN, 0L);
            final long lastScanStart = prefs.getLong(ListFragment.PREF_STARTTIME_CURRENT_SCAN, System.currentTimeMillis());
            final long newTare = scanTime + System.currentTimeMillis() - lastScanStart;
            edit.putLong(ListFragment.PREF_CUMULATIVE_SCANTIME_RUN, newTare);
        }

        edit.putBoolean(ListFragment.PREF_SCAN_RUNNING, isScanning);
        edit.apply();
        internalHandleScanChange(isScanning);
    }

    private ListFragment getListFragmentIfCurrent() {
        try {
            final FragmentManager fragmentManager = getSupportFragmentManager();
            Fragment f = fragmentManager.findFragmentByTag(FRAGMENT_TAG_PREFIX+R.id.nav_list);
            if (null != f) {
                return (ListFragment) f;
            }
        } catch (Exception ex) {
            MainActivity.error("Unable to get listfragment: ",ex);
        }
        return null;
    }

    private void internalHandleScanChange(final boolean isScanning) {
        info("main internalHandleScanChange: isScanning now: " + isScanning);
        ListFragment listFragment = getListFragmentIfCurrent();

        if (isScanning) {
            if (listFragment != null) {
                listFragment.setStatusUI(getString(R.string.list_scanning_on));
                listFragment.setScanningStatusIndicator(true);
            }
            if (state.wifiReceiver != null) {
                state.wifiReceiver.updateLastScanResponseTime();
            }
            // turn on location updates
            this.setLocationUpdates(getLocationSetPeriod(), 0f);

            if (!state.wifiLock.isHeld()) {
                state.wifiLock.acquire();
            }
        } else {
            if (listFragment != null) {
                listFragment.setStatusUI(getString(R.string.list_scanning_off));
                listFragment.setScanningStatusIndicator(false);
            }
            // turn off location updates
            this.setLocationUpdates(0L, 0f);
            state.gpsListener.handleScanStop();
            if (state.wifiLock.isHeld()) {
                try {
                    state.wifiLock.release();
                } catch (SecurityException ex) {
                    // a case where we have a leftover lock from another run?
                    MainActivity.info("exception releasing wifilock: " + ex);
                }
            }
        }
        if (null != state && null != state.wigleService) {
            state.wigleService.setupNotification();
        }
    }

    /**
     * resets the gps listener to the requested update time and distance.
     * an updateIntervalMillis of <= 0 will not register for updates.
     */
    public void setLocationUpdates(final long updateIntervalMillis, final float updateMeters) {
        try {
            internalSetLocationUpdates(updateIntervalMillis, updateMeters);
        } catch (final SecurityException ex) {
            error("Security exception in setLocationUpdates: " + ex, ex);
        }
    }

    private void internalSetLocationUpdates(final long updateIntervalMillis, final float updateMeters)
            throws SecurityException {
        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (state.gpsListener != null) {
            // remove any old requests
            locationManager.removeUpdates(state.gpsListener);
            locationManager.removeGpsStatusListener(state.gpsListener);
        }

        // create a new listener to try and get around the gps stopping bug
        state.gpsListener = new GPSListener(this, state.dbHelper);
        state.gpsListener.setMapListener(MappingFragment.STATIC_LOCATION_LISTENER);
        try {
            locationManager.addGpsStatusListener(state.gpsListener);
        } catch (final SecurityException ex) {
            info("Security exception adding status listener: " + ex, ex);
        }

        if (Build.VERSION.SDK_INT >= 24) {
            try {
                locationManager.registerGnssStatusCallback(new GnssStatus.Callback() {
                    @Override
                    public void onStarted() {
                    }

                    @Override
                    public void onStopped() {
                    }

                    @Override
                    public void onFirstFix(int ttffMillis) {
                    }

                    @Override
                    public void onSatelliteStatusChanged(GnssStatus status) {
                        state.gpsListener.onGnssStatusChanged(status);
                    }
                });
            }
            catch (final Exception ex) {
                error("Error registering for gnss: " + ex, ex);
            }
        }

        final SharedPreferences prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final boolean useNetworkLoc = prefs.getBoolean(ListFragment.PREF_USE_NETWORK_LOC, false);

        final List<String> providers = locationManager.getAllProviders();
        if (providers != null) {
            for (String provider : providers) {
                MainActivity.info("available provider: " + provider + " updateIntervalMillis: " + updateIntervalMillis);
                if (!useNetworkLoc && LocationManager.NETWORK_PROVIDER.equals(provider)) {
                    // skip!
                    continue;
                }
                if (!"passive".equals(provider) && updateIntervalMillis > 0L) {
                    MainActivity.info("using provider: " + provider);
                    try {
                        locationManager.requestLocationUpdates(provider, updateIntervalMillis, updateMeters, state.gpsListener);
                    } catch (final SecurityException ex) {
                        info("Security exception adding status listener: " + ex, ex);
                    }
                }
            }

            if (updateIntervalMillis <= 0L) {
                info("removing location listener: " + state.gpsListener);
                try {
                    locationManager.removeUpdates(state.gpsListener);
                } catch (final SecurityException ex) {
                    info("Security exception removing status listener: " + ex, ex);
                }
            }
        }
    }

    public static <A> String join(final String delimiter, final Iterable<A> iterable) {
        if (delimiter == null || iterable == null) {
            throw new IllegalArgumentException("join argument is null. delimiter: " + delimiter
                    + " iterable: " + iterable);
        }
        final StringBuilder sb = new StringBuilder();
        for (final A i : iterable) {
            if (i == null) continue;
            if (sb.length() > 0) sb.append(delimiter);
            sb.append(i.toString());
        }
        return sb.toString();
    }

    public long getLocationSetPeriod() {
        final SharedPreferences prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        long setPeriod = prefs.getLong(ListFragment.GPS_SCAN_PERIOD, MainActivity.LOCATION_UPDATE_INTERVAL);
        if (setPeriod == 0) {
            if (state.wifiReceiver == null) {
                setPeriod = MainActivity.LOCATION_UPDATE_INTERVAL;
            }
            else {
                setPeriod = Math.max(state.wifiReceiver.getScanPeriod(), MainActivity.LOCATION_UPDATE_INTERVAL);
            }
        }
        return setPeriod;
    }

    public void setLocationUpdates() {
        final long setPeriod = getLocationSetPeriod();
        setLocationUpdates(setPeriod, 0f);
    }

    /**
     * TransferListener interface
     */
    public void transferComplete() {
        state.transferring.set(false);
        MainActivity.info("transfer complete");
        // start a scan to get the ball rolling again if this is non-stop mode
        scheduleScan();
        state.observationUploader = null;
    }

    public void setLocationUI() {
        // tell list about new location
        ListFragment listFragment = getListFragmentIfCurrent();
        if (listFragment != null) {
            listFragment.setLocationUI(this);
        }
    }

    public void setNetCountUI() {
        // tell list
        ListFragment listFragment = getListFragmentIfCurrent();
        if (listFragment != null) {
            listFragment.setNetCountUI(getState());
        }
    }

    public void setStatusUI(String status) {
        if (status == null) {
            status = state.previousStatus;
        }
        if (status != null) {
            // keep around a previous, for orientation changes
            state.previousStatus = status;
            ListFragment listFragment = getListFragmentIfCurrent();

            if (listFragment != null) {
                // tell list
                listFragment.setStatusUI(status);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        return mDrawerToggle.onOptionsItemSelected(item);
    }

    public void finishSoon() {
        finishSoon(FINISH_TIME_MILLIS, false);
    }

    public void finishSoon(final long finishTimeMillis, final boolean restart) {
        MainActivity.info("Will finish in " + finishTimeMillis + "ms");
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                final Intent i = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage(getBaseContext().getPackageName());
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                if (state.finishing.get()) {
                    MainActivity.info("finishSoon: finish already called");
                }
                else {
                    MainActivity.info("finishSoon: calling finish now");
                    finish();
                }

                if (restart) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startActivity(i);
                        }
                    }, 10L);
                }
            }
        }, finishTimeMillis);
    }

    /**
     * Never call this directly! call finishSoon to give the service time to show a notification if needed
     */
    @Override
    public void finish() {
        info("MAIN: finish.");
        if (state.wifiReceiver != null) {
            info("MAIN: finish. networks: " + state.wifiReceiver.getRunNetworkCount());
        }

        final boolean wasFinishing = state.finishing.getAndSet(true);
        if (wasFinishing) {
            info("MAIN: finish called twice!");
        }

        // interrupt this just in case
        final ObservationUploader observationUploader = state.observationUploader;
        if (observationUploader != null) {
            observationUploader.setInterrupted();
        }

        if (state.gpsListener != null) {
            // save our location for later runs
            state.gpsListener.saveLocation();
        }

        // close the db. not in destroy, because it'll still write after that.
        if (state.dbHelper != null) state.dbHelper.close();

        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (state.gpsListener != null) {
            locationManager.removeGpsStatusListener(state.gpsListener);
            try {
                locationManager.removeUpdates(state.gpsListener);
            } catch (final SecurityException ex) {
                error("SecurityException on finish: " + ex, ex);
            }
        }

        // stop the service, so when we die it's both stopped and unbound and will die
        final Intent serviceIntent = new Intent(this, WigleService.class);
        stopService(serviceIntent);
        try {
            // have to use the app context to bind to the service, cuz we're in tabs
            getApplicationContext().unbindService(state.serviceConnection);
        } catch (final IllegalArgumentException ex) {
            MainActivity.info("serviceConnection not registered: " + ex, ex);
        }

        // release the lock before turning wifi off
        if (state.wifiLock != null && state.wifiLock.isHeld()) {
            try {
                state.wifiLock.release();
            } catch (Exception ex) {
                MainActivity.error("exception releasing wifi lock: " + ex, ex);
            }
        }

        final SharedPreferences prefs = this.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final boolean wifiWasOff = prefs.getBoolean(ListFragment.PREF_WIFI_WAS_OFF, false);
        // don't call on emulator, it crashes it
        if (wifiWasOff && !state.inEmulator) {
            // well turn it of now that we're done
            final WifiManager wifiManager = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            MainActivity.info("turning back off wifi");
            try {
                wifiManager.setWifiEnabled(false);
            } catch (Exception ex) {
                MainActivity.error("exception turning wifi back off: " + ex, ex);
            }
        }

        endBluetooth(prefs);

        TelephonyManager tele = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (tele != null && state.phoneState != null) {
            tele.listen(state.phoneState, PhoneStateListener.LISTEN_NONE);
        }

        if (state.tts != null) {
            if (!isMuted()) {
                // give time for the above "done" to be said
                sleep(250);
            }
            state.tts.shutdown();
        }

        // clean up.
        if (state.soundPop != null) {
            state.soundPop.release();
        }
        if (state.soundNewPop != null) {
            state.soundNewPop.release();
        }
        info("MAIN: finish complete.");

        super.finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            MainActivity.info("onKeyDown: not quitting app on back");
            selectFragment(R.id.nav_list);
            return true;
        }
        // we may want this, but devices with menu button don't get the 3 dots, so we'd have to force on the 3 dots
        // pry not worth it. leaving in case we do want it in the future
//        else if (keyCode == KeyEvent.KEYCODE_MENU) {
//            if (!mDrawerLayout.isDrawerOpen(mDrawerList)) {
//                mDrawerLayout.openDrawer(mDrawerList);
//            } else if (mDrawerLayout.isDrawerOpen(mDrawerList)) {
//                mDrawerLayout.closeDrawer(mDrawerList);
//            }
//            return true;
//        }
        return super.onKeyDown(keyCode, event);
    }

    public void doUpload() {
        selectFragment(R.id.nav_list);
        ListFragment listFragment = getListFragmentIfCurrent();
        listFragment.makeUploadDialog(this);
    }

    /**
     * pure-background upload method fo intent-based uploads
     */
    public void backgroundUploadFile(){
        MainActivity.info( "background upload file" );
        if (this == null) { return; }
        final State state = getState();
        setTransferring();
        state.observationUploader = new ObservationUploader(this,
                ListFragment.lameStatic.dbHelper, null, false, false, false);
        try {
            state.observationUploader.startDownload(null);
        } catch (WiGLEAuthException waex) {
            MainActivity.warn("Authentication failure on background run upload");
        }
    }

}
