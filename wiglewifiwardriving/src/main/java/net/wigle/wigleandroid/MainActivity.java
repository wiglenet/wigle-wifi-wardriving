package net.wigle.wigleandroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.location.GnssMeasurementRequest;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.navigation.NavigationView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.location.LocationManagerCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;

import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import com.google.android.gms.common.ConnectionResult;
import com.google.gson.Gson;

import net.wigle.wigleandroid.background.BssidMatchingAudioThread;
import net.wigle.wigleandroid.background.ObservationUploader;
import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.db.MxcDatabaseHelper;
import net.wigle.wigleandroid.listener.BatteryLevelReceiver;
import net.wigle.wigleandroid.listener.BluetoothReceiver;
import net.wigle.wigleandroid.listener.CellReceiver;
import net.wigle.wigleandroid.listener.GNSSListener;
import net.wigle.wigleandroid.listener.PhoneState;
import net.wigle.wigleandroid.listener.WifiReceiver;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.net.WiGLEApiManager;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.BluetoothUtil;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.InstallUtility;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.location.LocationManager.GPS_PROVIDER;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * MainActivity for WiGLE Wireless logging and visualization client
 */
public final class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
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
        MediaPlayer soundScanning;
        MediaPlayer soundContact;
        WifiLock wifiLock;
        GNSSListener GNSSListener;
        WifiReceiver wifiReceiver;
        BluetoothReceiver bluetoothReceiver;
        CellReceiver cellReceiver;
        NumberFormat numberFormat0;
        NumberFormat numberFormat1;
        NumberFormat numberFormat8;
        TextToSpeech tts;
        boolean ttsChecked = false;
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
        private final String[] logs = new String[25];
        Matcher bssidLogExclusions;
        Matcher bssidDisplayExclusions;
        Matcher bssidAlertList;
        Matcher bleMfgrIdList;
        int uiMode;
        AtomicBoolean uiRestart;
        AtomicBoolean ttsNag = new AtomicBoolean(true);
        public WiGLEApiManager apiManager;
        Map<Integer, String> btVendors = Collections.emptyMap();
        Map<Integer, String> btMfgrIds = Collections.emptyMap();
        Map<Integer, String> btServiceUuids = Collections.emptyMap();
        Map<Integer, String> btCharUuids = Collections.emptyMap();
        Map<Integer, BluetoothUtil.AppearanceCategory> btAppearance = Collections.emptyMap();
        Thread bssidMatchHeartbeat;
        // ALIBI set to -80 if you want a test ping on startup, Integer.MIN_VALUE for quiet start.
        AtomicInteger lastHighestSignal = new AtomicInteger(-80);
    }

    private State state;
    // *** end of state that is retained ***
    private GnssStatus.Callback gnssStatusCallback = null;
    private GnssMeasurementsEvent.Callback gnssMeasurementsCallback = null;
    static Locale ORIG_LOCALE = Locale.getDefault();
    public static final String ENCODING = "ISO-8859-1";
    private static final int PERMISSIONS_REQUEST = 1;
    private static final int ACTION_WIFI_CODE = 2;
    private static final int ACTION_TTS_CODE = 3;
    public static final int ACTION_GPX_MGMT = 4;


    static final String ERROR_REPORT_DO_EMAIL = "doEmail";
    public static final String ERROR_REPORT_DIALOG = "doDialog";

    public static final long DEFAULT_SPEECH_PERIOD = 60L;
    public static final long DEFAULT_RESET_WIFI_PERIOD = 90000L;
    public static final long LOCATION_UPDATE_INTERVAL = 1000L;
    public static final long SCAN_STILL_DEFAULT = 3000L;
    public static final long SCAN_DEFAULT = 2000L;
    public static final long SCAN_FAST_DEFAULT = 1000L;
    public static final long SCAN_P_DEFAULT = 30000L;
    public static final long OG_BT_SCAN_STILL_DEFAULT = 5000L;
    public static final long OG_BT_SCAN_DEFAULT = 5000L;
    public static final long OG_BT_SCAN_FAST_DEFAULT = 5000L;

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

    public static final boolean ENABLE_DEBUG_LOGGING = false;

    private static MainActivity mainActivity;
    private BatteryLevelReceiver batteryLevelReceiver;
    private boolean playServiceShown = false;

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;

    private static final String STATE_FRAGMENT_TAG = "StateFragmentTag";
    public static final String LIST_FRAGMENT_TAG = "ListFragmentTag";

    private static final AtomicLong utteranceSequenceGenerator = new AtomicLong();
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> Logging.info("state change on-fold.")
            );
        }

        if (ENABLE_DEBUG_LOGGING) {
            Logging.enableDebugLogging();
            Logging.info("Debug log-level is enabled.");
        }

        Logging.info("MAIN onCreate. state:  " + state);
        //DEBUG:
        /*StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder()
                        .detectDiskReads()
                        .detectDiskWrites()
                        .detectNetwork()
                        .penaltyLog()
                        .build());
        StrictMode.setVmPolicy(
                new StrictMode.VmPolicy.Builder()
                        .detectLeakedClosableObjects()
                        .detectLeakedSqlLiteObjects()
                        .penaltyLog()
                        .build());*/
        //END DEBUG
        final int THREAD_ID = 31973;
        TrafficStats.setThreadStatsTag(THREAD_ID);
        workAroundGoogleMapsBug();
        final SharedPreferences prefs = getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);

        ThemeUtil.setTheme(prefs);
        ThemeUtil.setNavTheme(this.getWindow(), this, prefs);
        mainActivity = this;

        // set language
        setLocale(this);
        setContentView(R.layout.main);
        EdgeToEdge.enable(this);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> {
                        Logging.info("onKeyDown: not quitting app on back");
                        selectFragment(R.id.nav_list);
                        //TODO: anything else required to prevent exit here?
                    }
            );
        }

        View mainWrapper = findViewById(R.id.main_wrapper);
        if (null != mainWrapper) {
            ViewCompat.setOnApplyWindowInsetsListener(mainWrapper, new OnApplyWindowInsetsListener() {
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
            // propagate insets to fragments
            mainWrapper.post(() -> ViewCompat.requestApplyInsets(mainWrapper));
        }

        DrawerLayout dl = findViewById(R.id.drawer_layout);
        if (null != dl) {
            int [] attrs = { com.google.android.material.R.attr.scrimBackground };
            try (@SuppressLint("ResourceType") TypedArray typedValues  = obtainStyledAttributes(R.style.AppTheme, attrs)) {
                int scrimColor = typedValues.getColor(0, Color.parseColor("#99000000"));
                dl.setScrimColor(scrimColor);
            }
        }
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

        // force the retained fragments to live
        final FragmentManager fm = getSupportFragmentManager();
        fm.executePendingTransactions();
        StateFragment stateFragment = (StateFragment) fm.findFragmentByTag(STATE_FRAGMENT_TAG);

        pieScanningSettings(prefs);

        if (stateFragment != null && stateFragment.getState() != null) {
            Logging.info("MAIN: using retained stateFragment state");
            // pry an orientation change, which calls destroy, but we get this from retained fragment
            state = stateFragment.getState();

            // tell those that need it that we have a new context
            if (state.GNSSListener != null) {
                state.GNSSListener.setMainActivity(this);
            }
            if (state.wifiReceiver != null) {
                state.wifiReceiver.setMainActivity(this);
            }
            if (state.observationUploader != null) {
                state.observationUploader.setContext(this);
            }
        } else {
            Logging.info("MAIN: creating new state");
            state = new State();
            state.finishing = new AtomicBoolean(false);
            state.transferring = new AtomicBoolean(false);
            state.uiRestart = new AtomicBoolean(false);
            // setup api manager
            if (state.apiManager == null) {
                state.apiManager = new WiGLEApiManager(prefs, getApplicationContext());
            }

            // set it up for retain
            stateFragment = new StateFragment();
            stateFragment.setState(state);
            fm.beginTransaction().add(stateFragment, STATE_FRAGMENT_TAG).commit();
            // new run, reset
            final float prevRun = prefs.getFloat(PreferenceKeys.PREF_DISTANCE_RUN, 0f);
            Editor edit = prefs.edit();
            edit.putFloat(PreferenceKeys.PREF_DISTANCE_RUN, 0f);
            edit.putLong(PreferenceKeys.PREF_STARTTIME_RUN, System.currentTimeMillis());
            edit.putLong(PreferenceKeys.PREF_STARTTIME_CURRENT_SCAN, System.currentTimeMillis());
            edit.putLong(PreferenceKeys.PREF_CUMULATIVE_SCANTIME_RUN, 0L);
            edit.putFloat(PreferenceKeys.PREF_DISTANCE_PREV_RUN, prevRun);
            edit.apply();
        }

        Logging.info("MAIN: powerManager setup");
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (state.wakeLock == null) {
            state.wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "wiglewifiwardriving:DoNotDimScreen");
            if (state.wakeLock.isHeld()) {
                state.wakeLock.release();
            }
        }

        @SuppressLint("HardwareIds")
        final String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // DO NOT turn these into |=, they will cause older dalvik verifiers to freak out
        state.inEmulator = id == null;
        state.inEmulator = state.inEmulator || "sdk".equals(android.os.Build.PRODUCT);
        state.inEmulator = state.inEmulator || "google_sdk".equals(android.os.Build.PRODUCT);

        state.uiMode = getResources().getConfiguration().uiMode;

        Logging.info("MAIN:\tid: '" + id + "' inEmulator: " + state.inEmulator + " product: " + android.os.Build.PRODUCT);
        Logging.info("MAIN:\tandroid release: '" + Build.VERSION.RELEASE);

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

        Logging.info("MAIN: setupService");
        setupService();
        Logging.info("MAIN: checkStorage");
        checkStorage();
        Logging.info("MAIN: setupDatabase");
        setupDatabase(prefs);
        Logging.info("MAIN: setupBattery");
        setupBattery();
        Logging.info("MAIN: setupSound");
        setupSound();
        Logging.info("MAIN: setupActivationDialog");
        setupActivationDialog(prefs);
        Logging.info("MAIN: setupBluetooth");
        setupBluetooth(prefs);
        Logging.info("MAIN: setupWifi");
        setupWifi(prefs);
        Logging.info("MAIN: setupLocation"); // must be after setupWifi
        setupLocation(prefs);
        Logging.info("MAIN: setup tabs");
        if (savedInstanceState == null) {
            setupFragments();
        }
        setupFilters(prefs);

        Logging.info("MAIN: first install check");
        // ALIBI: don't inherit MxC implant failures from backups.
        if (InstallUtility.isFirstInstall(this)) {
            SharedPreferences mySPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = mySPrefs.edit();
            editor.remove(ListFragment.PREF_MXC_REINSTALL_ATTEMPTED);
            if (!isImperialUnitsLocale()) {
                editor.putBoolean(PreferenceKeys.PREF_METRIC, true);
            }
            editor.apply();
        }

        Logging.info("MAIN: cell data check");
        //TODO: if we can determine whether DB needs updating, we can avoid copying every time
        //if (!state.mxcDbHelper.isPresent()) {
        state.mxcDbHelper.implantMxcDatabase(this, isFinishing());
        //}

        Logging.info("MAIN: keystore check");
        // rksh 20160202 - api/authuser secure preferences storage
        checkInitKeystore(prefs);

        // show the list by default
        selectFragment(state.currentTab);
        Logging.info("MAIN: onCreate setup complete");
    }

    private void pieScanningSettings(final SharedPreferences prefs) {
        if (Build.VERSION.SDK_INT < 28) return;

        final List<String> keys = Arrays.asList(PreferenceKeys.PREF_SCAN_PERIOD_STILL,
                PreferenceKeys.PREF_SCAN_PERIOD, PreferenceKeys.PREF_SCAN_PERIOD_FAST);
        if (Build.VERSION.SDK_INT == 28) {
            for (final String key : keys) {
                pieScanSet(prefs, key);
            }
        } else if (Build.VERSION.SDK_INT == 29) {
            // see if all configs are at the P
            for (final String key : keys) {
                if (prefs.getLong(key, -1) != SCAN_P_DEFAULT) {
                    return;
                }
            }
            for (final String key : keys) {
                qScanSet(prefs, key);
            }
        }
    }

    /**
     * ALIBI: API (unsupported) will get registered for this in the pre-release reports, but this
     * works around the ZoomTable Array Index OOB bugs. 80% of violations in pre-release report are this.
     */
    private void workAroundGoogleMapsBug() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        //DEBUG: Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                SharedPreferences googleBug = getSharedPreferences("google_bug_154855417", Context.MODE_PRIVATE);
                if (!googleBug.contains("fixed")) {
                    Logging.info("working around google maps bug 154855417");
                    File corruptedZoomTables = new File(getFilesDir(), "ZoomTables.data");
                    if (corruptedZoomTables.exists() && corruptedZoomTables.delete()) {
                        googleBug.edit().putBoolean("fixed", true).apply();
                    } else if (!corruptedZoomTables.exists()) {
                        googleBug.edit().putBoolean("fixed", true).apply();
                    } else {
                        Logging.error("unable to work around corrupted ZoomTables.data error");
                    }
                } else {
                    Logging.info("already worked around google maps bug 154855417");
                }
            } catch (Exception e) {
                Logging.warn("Exception in workAroundGoogleMapsBug: " + e);
            }
            /*DEBUG: handler.post(() -> {
                WiGLEToast.showOverActivity(this, R.string.app_name, "ZoomTables cleanup complete", Toast.LENGTH_LONG);
            });*/
        });
    }

    @SuppressLint("ApplySharedPref")
    private void pieScanSet(final SharedPreferences prefs, final String key) {
        if (-1 == prefs.getLong(key, -1)) {
            Logging.info("Setting 30 second scan for " + key + " due to broken Android Pie");
            prefs.edit().putLong(key, SCAN_P_DEFAULT).commit();
        }
    }

    @SuppressLint("ApplySharedPref")
    private void qScanSet(final SharedPreferences prefs, final String key) {
        if (SCAN_P_DEFAULT == prefs.getLong(key, -1)) {
            Logging.info("Removing 30 second scan for " + key + " due to less broken Android Q");
            prefs.edit().remove(key).commit();
        }
    }

    /**
     * migration method for viable APIs to switch to encrypted AUTH_TOKENs
     */
    private void checkInitKeystore(final SharedPreferences prefs) {
        if (TokenAccess.checkMigrateKeystoreVersion(prefs)) {
            // successful migration should remove the password value
            if (!prefs.getString(PreferenceKeys.PREF_PASSWORD,
                    "").isEmpty()) {
                final Editor editor = prefs.edit();
                editor.remove(PreferenceKeys.PREF_PASSWORD);
                editor.apply();
            }
        } else {
            Logging.info("Not able to upgrade key storage.");
        }
    }

    private void setupPermissions() {
        final List<String> permissionsNeeded = new ArrayList<>();
        final List<String> permissionsList = new ArrayList<>();
        if (!addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionsNeeded.add(mainActivity.getString(R.string.gps_permission));
        }
        if (!addPermission(permissionsList, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissionsNeeded.add(mainActivity.getString(R.string.cell_permission));
        }
        addPermission(permissionsList, Manifest.permission.BLUETOOTH);
        addPermission(permissionsList, Manifest.permission.READ_PHONE_STATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addPermission(permissionsList, Manifest.permission.BLUETOOTH_SCAN);
            addPermission(permissionsList, Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addPermission(permissionsList, Manifest.permission.POST_NOTIFICATIONS);
        }
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

            Logging.info("no permission for " + permissionsNeeded);

            // Fire off an async request to actually get the permission
            // This will show the standard permission request dialog UI
            requestPermissions(permissionsList.toArray(new String[0]),
                    PERMISSIONS_REQUEST);
        }
    }

    private boolean addPermission(List<String> permissionsList, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            if (!shouldShowRequestPermissionRationale(permission))
                return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String permissions[],
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST: {
                Logging.info("location grant response permissions: " + Arrays.toString(permissions)
                        + " grantResults: " + Arrays.toString(grantResults));

                boolean restart = false;
                for (int i = 0; i < permissions.length; i++) {
                    final String permission = permissions[i];
                    if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)
                            && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        restart = true;
                        break;
                    }
                }

                if (restart) {
                    // restart the app now that we can talk to the database
                    Logging.info("Restarting to pick up storage permission");
                    finishSoon(FINISH_TIME_MILLIS, true);
                }
                return;
            }

            default:
                Logging.warn("Unhandled onRequestPermissionsResult code: " + requestCode);
        }
    }

    private void setupMenuDrawer() {
        mDrawerLayout = findViewById(R.id.drawer_layout);
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
                InputMethodManager inputMethodManager = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputMethodManager != null && getCurrentFocus() != null) {
                    inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                    View current = getCurrentFocus();
                    if (null != current) {
                        current.clearFocus();
                    }
                }
            }
        };
        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        final NavigationView navigationView = findViewById(R.id.left_drawer);
        navigationView.getMenu().setGroupVisible(R.id.stats_group, false);

        navigationView.setNavigationItemSelectedListener(
                menuItem -> {
                    // set item as selected to persist highlight
                    menuItem.setCheckable(true);
                    if (menuItem.getItemId() == R.id.nav_stats) {
                        menuItem.setChecked(!menuItem.isChecked());
                    } else {
                        menuItem.setChecked(true);
                    }
                    if (state.previousTab != menuItem.getItemId() && state.previousTab != 0) {
                        MenuItem mPreviousMenuItem = navigationView.getMenu().findItem(state.previousTab);
                        mPreviousMenuItem.setChecked(false);
                    }
                    state.previousTab = menuItem.getItemId();

                    // close drawer when item is tapped
                    if (R.id.nav_stats == menuItem.getItemId()) {
                        showSubmenu(navigationView.getMenu(), R.id.stats_group, menuItem.isChecked());
                        applyExitBackground(navigationView);
                    } else if (R.id.nav_exit == menuItem.getItemId()) {
                        selectFragment(menuItem.getItemId());
                        return false;
                    } else {
                        if (R.id.nav_site_stats != menuItem.getItemId() &&
                                R.id.nav_user_stats != menuItem.getItemId() &&
                                R.id.nav_rank != menuItem.getItemId())
                            showSubmenu(navigationView.getMenu(), R.id.stats_group, false);
                        mDrawerLayout.closeDrawers();
                        selectFragment(menuItem.getItemId());
                        applyExitBackground(navigationView);
                    }
                    return true;
                });
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        //TODO:
        int menuSubColor = 0xE0777777;
        MenuItem uStats = navigationView.getMenu().findItem(R.id.nav_user_stats);
        if (null != uStats.getTitle()) {
            final SpannableString uSpanString = new SpannableString("    " + uStats.getTitle().toString());
            uSpanString.setSpan(new ForegroundColorSpan(menuSubColor), 0, uSpanString.length(), 0);
            uStats.setTitle(uSpanString);
        }

        MenuItem sStats = navigationView.getMenu().findItem(R.id.nav_site_stats);
        if (null != sStats.getTitle()) {
            SpannableString sSpanString = new SpannableString("    " + sStats.getTitle().toString());
            sSpanString.setSpan(new ForegroundColorSpan(menuSubColor), 0, sSpanString.length(), 0);
            sStats.setTitle(sSpanString);
        }

        MenuItem rStats = navigationView.getMenu().findItem(R.id.nav_rank);
        if (null != rStats.getTitle()) {
            SpannableString  rSpanString = new SpannableString("    " + rStats.getTitle().toString());
            rSpanString.setSpan(new ForegroundColorSpan(menuSubColor), 0, rSpanString.length(), 0);
            rStats.setTitle(rSpanString);
        }

        navigationView.getMenu().getItem(0).setCheckable(true);
        navigationView.getMenu().getItem(0).setChecked(true);

        // Use a custom background for nav_exit menu item
        applyExitBackground(navigationView);
    // end drawer setup
    }

    /**
     * Ugly hack to keep the exit button red when other things happen in the menu
     * @param navigationView the exit view
     */
    public static void applyExitBackground(final NavigationView navigationView) {
        if (navigationView == null) {
            Logging.error("null exit navigation view.");
            return;
        }
        MenuItem exitMenuItem = navigationView.getMenu().findItem(R.id.nav_exit);
        if (exitMenuItem != null) {
            exitMenuItem.setCheckable(false);
            navigationView.post(() -> {
                View exitView = navigationView.findViewById(R.id.nav_exit);
                if (exitView != null) {
                    exitView.setBackgroundResource(R.drawable.wigle_menu_item_exit_selector);
                }
            });
        } else {
            Logging.info("null exit menu item");
        }
    }

    /**
     * Swaps fragments in the main content view
     */
    public void selectFragment(final int itemId) {
        if (itemId == R.id.nav_exit) {
            finishSoon();
            return;
        }

        final NavigationView navigationView = findViewById(R.id.left_drawer);
        if (null != navigationView) {
            applyExitBackground(navigationView);
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

            try {
                fragmentManager.beginTransaction()
                        .replace(R.id.tabcontent, frag, FRAGMENT_TAG_PREFIX + itemId)
                        .commit();
            } catch (final NullPointerException | IllegalStateException ex) {
                final String message = "exception in fragment switch: " + ex;
                Logging.error(message, ex);
            }

            // Highlight the selected item, update the title, and close the drawer
            state.currentTab = itemId;
            setTitle(fragmentTitles.get(itemId));
        } catch (IllegalAccessException ex) {
            Logging.error("Unable to get fragment for id: " + itemId, ex);
        } catch (InstantiationException ex) {
            Logging.error("Unable to make fragment for id: " + itemId, ex);
        }
    }

    private void showSubmenu(final Menu menu, final int submenuGroupId, final boolean visible) {
        runOnUiThread(() -> {
            // Your menu modification code here
            menu.setGroupVisible(submenuGroupId, visible);
        });
    }

    @Override
    public void setTitle(CharSequence title) {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setTitle(title);
    }

    private void setupFragments() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        Logging.info("\tCreating ListFragment");
        ListFragment listFragment = new ListFragment();
        Bundle bundle = new Bundle();
        listFragment.setArguments(bundle);

        transaction.add(R.id.tabcontent, listFragment, FRAGMENT_TAG_PREFIX + R.id.nav_list);
        transaction.commit();
    }

    private static Class classForFragmentNavId(final int navId) {
        if (navId == R.id.nav_list) {
            return ListFragment.class;
        } else if (navId == R.id.nav_dash) {
            return DashboardFragment.class;
        } else if (navId == R.id.nav_data) {
            return DataFragment.class;
        } else if (navId == R.id.nav_search) {
            if (null != mainActivity) {
                SharedPreferences prefs = mainActivity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
                if (null != prefs) {
                    if (prefs.getBoolean(PreferenceKeys.PREF_USE_FOSS_MAPS, false)) {
                        return FossSearchFragment.class;
                    } else {
                        return SearchFragment.class;
                    }
                }
            }
            return SearchFragment.class;
        } else if (navId == R.id.nav_map) {
            if (null != mainActivity) {
                SharedPreferences prefs = mainActivity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
                if (null != prefs) {
                    if (prefs.getBoolean(PreferenceKeys.PREF_USE_FOSS_MAPS, false)) {
                        return FossMappingFragment.class;
                    } else {
                        return MappingFragment.class;
                    }
                }
            }
            return MappingFragment.class;
        } else if (navId == R.id.nav_user_stats) {
            return UserStatsFragment.class;
        } else if (navId == R.id.nav_rank) {
            return RankStatsFragment.class;
        } else if (navId == R.id.nav_site_stats) {
            return SiteStatsFragment.class;
        } else if (navId == R.id.nav_news) {
            return NewsFragment.class;
        } else if (navId == R.id.nav_uploads) {
            return UploadsFragment.class;
        } else if (navId == R.id.nav_settings) {
            return SettingsFragment.class;
        } else {
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
                    Logging.error("onMenuOpened no such method: " + ex, ex);
                } catch (Exception ex) {
                    Logging.error("onMenuOpened ex: " + ex, ex);
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
        DisplayMetrics metrics = new DisplayMetrics();
        final MainActivity main = MainActivity.getMainActivity();
        if (main == null) return false;
        main.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int dpi = metrics.densityDpi;
        return dpi >= 240;
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
                Logging.info("acquire wake lock");
                state.wakeLock.acquire();
            }
        } else if (state.wakeLock.isHeld()) {
            Logging.info("release wake lock");
            state.wakeLock.release();
        }
    }

    private void setupDatabase(final SharedPreferences prefs) {
        // could be set by nonconfig retain
        if (state.dbHelper == null) {
            state.dbHelper = new DatabaseHelper(getApplicationContext(), prefs);
            //state.dbHelper.checkDB();
            state.dbHelper.start();
            ListFragment.lameStatic.dbHelper = state.dbHelper;
        }
        if (state.mxcDbHelper == null) {
            state.mxcDbHelper = new MxcDatabaseHelper(getApplicationContext(), prefs);
        }
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
            Logging.info("not main activity: " + activity);
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onDestroy() {
        Logging.info("MAIN: destroy.");
        super.onDestroy();
        stopHeartbeat();
        if (!state.uiRestart.get()) {
            try {
                Logging.info("unregister batteryLevelReceiver");
                unregisterReceiver(batteryLevelReceiver);
            } catch (final IllegalArgumentException ex) {
                Logging.info("batteryLevelReceiver not registered: " + ex);
            }

            try {
                Logging.info("unregister wifiReceiver");
                unregisterReceiver(state.wifiReceiver);
            } catch (final IllegalArgumentException ex) {
                Logging.info("wifiReceiver not registered: " + ex);
            }

            if (state.tts != null) state.tts.shutdown();
            //TODO: redundant with endBluetooth?
            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            try {
                if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
                Logging.info("unregister bluetoothReceiver");
                unregisterReceiver(state.bluetoothReceiver);
            } catch (final IllegalArgumentException ex) {
                Logging.info("bluetoothReceiver not registered: " + ex);
            } catch (final SecurityException ex) {
                Logging.info("bluetoothReceiver access: " + ex);
            }
            if (state.bluetoothReceiver != null) {
                state.bluetoothReceiver.stopScanning();
                state.bluetoothReceiver.close();
            }
            finishSoon(DESTROY_FINISH_MILLIS, false);
        } else {
            state.uiRestart.set(false);
        }
        mainActivity = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        Logging.info("MAIN: onSaveInstanceState");
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        Logging.info("MAIN: pause.");
        try {
            super.onPause();
        } catch (RuntimeException ex) {
            Logging.warn("super onPause exception: " + ex);
        }

        // deal with wake lock
        if (state.wakeLock.isHeld()) {
            Logging.info("release wake lock");
            state.wakeLock.release();
        }
    }

    @Override
    public void onResume() {
        Logging.info("MAIN: resume.");
        super.onResume();
        mainActivity = this;

        // deal with wake lock
        if (!state.wakeLock.isHeld() && state.screenLocked) {
            Logging.info("acquire wake lock");
            state.wakeLock.acquire();
        }

        final int serviceAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
        Logging.info("GoogleApiAvailability: " + serviceAvailable);
        if (serviceAvailable != ConnectionResult.SUCCESS && !playServiceShown) {
            Logging.error("GoogleApiAvailability not available! " + serviceAvailable);
            final Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(this, serviceAvailable, 0);
            if (null != dialog) {
                dialog.show();
                playServiceShown = true;
            }
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
        Logging.info("MAIN: post resume.");
        super.onPostResume();
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        Logging.info("MAIN: config changed");
        setLocale(this, newConfig);
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
        if (Build.VERSION.SDK_INT > 28) {
            if (newConfig.uiMode != state.uiMode) {
                Logging.info("uiMode change - " + state.uiMode + "->" + newConfig.uiMode);
                state.uiMode = newConfig.uiMode;
                //DEBUG:
                finishSoon(0, false, true);
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onStart() {
        Logging.info("MAIN: start.");
        final SharedPreferences prefs = getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PreferenceKeys.PREF_BLOWED_UP, false)) {
            prefs.edit().putBoolean(PreferenceKeys.PREF_BLOWED_UP, false).commit();
            // activate the email intent
            final Intent intent = new Intent(this, ErrorReportActivity.class);
            intent.putExtra(MainActivity.ERROR_REPORT_DO_EMAIL, true);
            startActivity(intent);
        }
        super.onStart();
        mainActivity = this;
    }

    @Override
    public void onStop() {
        Logging.info("MAIN: stop.");
        super.onStop();
    }

    @Override
    public void onRestart() {
        Logging.info("MAIN: restart.");
        super.onRestart();
        mainActivity = this;
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

    /**
     * ALIBI LocaleData.getMeasurementSystem requires API 28 and up
     * @return true if you're in USA, Liberia, or Burma
     */
    public static boolean isImperialUnitsLocale() {
        final Locale locale = Locale.getDefault();
        final String countryCode = locale.getCountry();
        return "US".equals(countryCode) || "LR".equals(countryCode) || "MM".equals(countryCode);
    }

    public static void setLocale(final Context context, final Configuration config) {
        final SharedPreferences prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
        final String lang = prefs.getString(PreferenceKeys.PREF_LANGUAGE, "");
        final String current = config.getLocales().get(0).getLanguage();
        Logging.info("current lang: " + current + " new lang: " + lang);
        Locale newLocale = null;
        if (!lang.isEmpty() && !current.equals(lang)) {
            if (lang.contains("-r")) {
                String[] parts = lang.split("-r");
                Logging.info("\tlang: " + parts[0] + " country: " + parts[1]);
                newLocale = new Locale(parts[0], parts[1]);
            } else {
                Logging.info("\tlang: " + lang + " current: " + current);
                newLocale = new Locale(lang);
            }
        } else if (lang.isEmpty() && ORIG_LOCALE != null && !ORIG_LOCALE.getLanguage().isEmpty()
                && !current.equals(ORIG_LOCALE.getLanguage())) {
            newLocale = ORIG_LOCALE;
        }

        if (newLocale != null) {
            Locale.setDefault(newLocale);
            config.setLocale(newLocale);
            Logging.info("setting locale: " + newLocale);
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            //ALIBI: loop protection
            if (MainActivity.getMainActivity() != null &&
                    MainActivity.getMainActivity().state != null &&
                    !MainActivity.getMainActivity().state.ttsChecked) {
                ttsCheckIntent();
            }
        }
    }

    public static void ttsCheckIntent() {
        if (mainActivity != null) {
            if (null != mainActivity.getState() && mainActivity.getState().ttsNag.compareAndSet(true, false)) {
                try {
                    Intent checkTTSIntent = new Intent();
                    checkTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
                    mainActivity.startActivityForResult(checkTTSIntent, ACTION_TTS_CODE);
                } catch (ActivityNotFoundException e) {
                    Logging.error("TTS check not available in your device." + e.fillInStackTrace());
                    //TODO: does make sense to disable the TTS pref here, or is this recoverable?
                }
            }
        } else {
            Logging.error("could not launch TTS check due to pre-instantiation state");
        }
    }

    /**
     * hack to get locale
     * @param context the Android context for which to get the locale
     * @param config the Configuration of the applications
     * @return the Locale according to device and configuration
     */
    public static Locale getLocale(final Context context, final Configuration config) {
        final SharedPreferences prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
        final String current = config.getLocales().get(0).getLanguage();
        String lang = prefs.getString(PreferenceKeys.PREF_LANGUAGE, current);
        if (lang.isEmpty()) {
            lang = current;
        }
        Logging.info("current lang: " + current + " new lang: " + lang);
        if (lang.contains("-r")) {
            String[] parts = lang.split("-r");
            Logging.info("\tlang: " + parts[0] + " country: " + parts[1]);
            return new Locale(parts[0], parts[1]);
        } else {
            Logging.info("\tlang: " + lang);
            return new Locale(lang);
        }
    }

    public boolean isMuted() {
        //noinspection SimplifiableIfStatement
        if (state.phoneState != null && state.phoneState.isPhoneActive()) {
            // always be quiet when the phone is active
            return true;
        }
        return getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE)
                .getBoolean(PreferenceKeys.PREF_MUTED, true);
    }

    public static void sleep(final long sleep) {
        try {
            Thread.sleep(sleep);
        } catch (final InterruptedException ex) {
            // no worries
        }
    }

    /**
     * force-incremental-save log.
     * ALIBI: exposed for Logging core.
     * TOOD: is there a cleaner way to handle this in modern Android?
     * @param value the value to log.
     */
    public static void saveLog(final String value) {
        final State state = getStaticState();
        if (state == null) return;
        final int pointer = state.logPointer++ % state.logs.length;
        final DateFormat format = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        state.logs[pointer] = format.format(new Date()) + " "
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
        MainActivity a = MainActivity.mainActivity;
        if (a != null) {
            final FragmentManager fragmentManager = a.getSupportFragmentManager();
            if (null != getStaticState() && getStaticState().currentTab == R.id.nav_map) {
                // Map is visible, give it the new network
                final AbstractMappingFragment f = (AbstractMappingFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG_PREFIX + R.id.nav_map);
                if (f != null) {
                    f.addNetwork(network);
                }
            }
        }
    }

    public static void updateNetworkOnMap(final Network network) {
        MainActivity a = MainActivity.mainActivity;
        if (a != null) {
            final FragmentManager fragmentManager = a.getSupportFragmentManager();
            if (null != getStaticState() && getStaticState().currentTab == R.id.nav_map) {
                // Map is visible, give it the new network
                final AbstractMappingFragment f = (AbstractMappingFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG_PREFIX + R.id.nav_map);
                if (f != null) {
                    f.updateNetwork(network);
                }
            }
        }
    }

    public static void reclusterMap() {
        final MainActivity a = MainActivity.mainActivity;
        if (a == null) {
            return;
        }
        final FragmentManager fragmentManager = a.getSupportFragmentManager();
        if (null != getStaticState() && getStaticState().currentTab == R.id.nav_map) {
            final AbstractMappingFragment f = (AbstractMappingFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG_PREFIX + R.id.nav_map);
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
            Logging.error(error, throwable);
            final File stackPath = FileUtility.getErrorStackPath(context);
            if (stackPath.exists() && stackPath.canWrite()) {
                //noinspection ResultOfMethodCallIgnored
                stackPath.mkdirs();
                final File file = new File(stackPath, FileUtility.ERROR_STACK_FILE_PREFIX + "_" + System.currentTimeMillis() + ".txt");
                Logging.error("Writing stackfile to: " + file.getAbsolutePath());
                if (!file.exists()) {
                    if (!file.createNewFile()) {
                        throw new IOException("Cannot create file: " + file);
                    }
                }

                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(file);
                    PackageInfo pi = null;
                    try {
                        final PackageManager pm = context.getPackageManager();
                        pi = pm.getPackageInfo(context.getPackageName(), 0);
                    } catch (Throwable er) {
                        handleErrorError(fos, er);
                    }

                    try {
                        StringBuilder builder = new StringBuilder("WigleWifi error log - ");
                        final DateFormat format = SimpleDateFormat.getDateTimeInstance();
                        builder.append(format.format(new Date())).append("\n");
                        if (pi != null) {
                            builder.append("versionName: ").append(pi.versionName).append("\n");
                            builder.append("packageName: ").append(pi.packageName).append("\n");
                        }
                        if (detail != null) {
                            builder.append("detail: ").append(detail).append("\n");
                        }
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
                        handleErrorError(fos, er);
                    }

                    try {
                        final String baseErrorMessage = MainActivity.getBaseErrorMessage(throwable, false);
                        fos.write("baseError: ".getBytes(ENCODING));
                        fos.write(baseErrorMessage.getBytes(ENCODING));
                        fos.write("\n\n".getBytes(ENCODING));
                    } catch (Throwable er) {
                        handleErrorError(fos, er);
                    }

                    try {
                        throwable.printStackTrace(new PrintStream(fos));
                        fos.write((error + "\n\n").getBytes(ENCODING));
                    } catch (Throwable er) {
                        handleErrorError(fos, er);
                    }

                    try {
                        for (final String log : getLogLines()) {
                            fos.write(log.getBytes(ENCODING));
                            fos.write("\n".getBytes(ENCODING));
                        }
                    } catch (Throwable er) {
                        // ohwell
                        Logging.error("error getting logs for error: " + er, er);
                    }
                } finally {
                    // can't try-with-resources and support api 14
                    try {
                        if (fos != null) fos.close();
                    } catch (final Exception ex) {
                        Logging.error("error closing fos: " + ex, ex);
                    }
                }

            }
        } catch (final Exception ex) {
            Logging.error("error logging error: " + ex, ex);
        }
    }

    private static void handleErrorError(FileOutputStream fos, Throwable er) throws IOException {
        // ohwell
        final String errorMessage = "error getting data for error: " + er;
        Logging.error(errorMessage, er);
        fos.write((errorMessage + "\n\n").getBytes(ENCODING));
        er.printStackTrace(new PrintStream(fos));
    }

    public static Iterable<String> getLogLines() {
        final State state = getStaticState();
        return () -> {
            // Collections.emptyIterator() requires api 19, but this works.
            if (state == null) return Collections.emptyIterator();

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
        };
    }

    public static boolean isDevMode(final Context context) {
        return android.provider.Settings.Secure.getInt(context.getContentResolver(),
                android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    private void setupSound() {
        // could have been retained
        if (state.soundPop == null) {
            state.soundPop = MediaPlayer.create(getApplicationContext(), R.raw.pop);
        }
        if (state.soundNewPop == null) {
            state.soundNewPop = MediaPlayer.create(getApplicationContext(), R.raw.newpop);
        }
        if (state.soundScanning == null) {
            state.soundScanning = MediaPlayer.create(getApplicationContext(), R.raw.scanning);
        }
        if (state.soundContact == null) {
            state.soundContact = MediaPlayer.create(getApplicationContext(), R.raw.contact);
        }

        // make volume change "media"
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        TelephonyManager tele = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tele != null && state.phoneState == null) {
            state.phoneState = new PhoneState();
            final int signal_strengths = 256;
            try {
                tele.listen(state.phoneState, PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | signal_strengths);
            } catch (SecurityException ex) {
                Logging.info("cannot get call state, will play audio over any telephone calls: " + ex);
            }
        }
        if (MainActivity.getMainActivity() != null &&
                MainActivity.getMainActivity().state != null &&
                !MainActivity.getMainActivity().state.ttsChecked) {
            ttsCheckIntent();
        }
    }

    /**
     * Instantiate both both BSSID matchers - initial load
     * @param prefs the preferences instance for which to get matchers
     */
    private void setupFilters(final SharedPreferences prefs) {
        if (null != state) {
            state.bssidDisplayExclusions = generateBssidFilterMatcher(prefs, PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS);
            state.bssidLogExclusions = generateBssidFilterMatcher(prefs, PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS);
            state.bssidAlertList = generateBssidFilterMatcher(prefs, PreferenceKeys.PREF_ALERT_ADDRS);
            state.bleMfgrIdList = generateBssidFilterMatcher(prefs, PreferenceKeys.PREF_ALERT_BLE_MFGR_IDS);
            //TODO: port SSID matcher over as well?
            if (null != state.bssidAlertList || null != state.bleMfgrIdList) {
                startHeartbeat(prefs);
            } else {
                stopHeartbeat();
            }
        }
    }

    /**
     * Trigger recreation of BSSID address filter from prefs
     * @param addressKey the preferences key for which to create a settings entry.
     */
    public void updateAddressFilter(final String addressKey) {
        if (null != state) {
            final SharedPreferences prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
            if (PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS.equals(addressKey)) {
                state.bssidDisplayExclusions = generateBssidFilterMatcher(prefs, PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS);
            } else if (PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS.equals(addressKey)) {
                state.bssidLogExclusions = generateBssidFilterMatcher(prefs, PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS);
            } else if (PreferenceKeys.PREF_ALERT_ADDRS.equals(addressKey)) {
                state.bssidAlertList = generateBssidFilterMatcher(prefs, PreferenceKeys.PREF_ALERT_ADDRS);
                if (null == state.bssidAlertList && null == state.bleMfgrIdList) {
                    stopHeartbeat();
                } else {
                    startHeartbeat(prefs);
                }
            } else if (PreferenceKeys.PREF_ALERT_BLE_MFGR_IDS.equals(addressKey)) {

                state.bleMfgrIdList = generateBssidFilterMatcher(prefs, PreferenceKeys.PREF_ALERT_BLE_MFGR_IDS);
                if (null == state.bssidAlertList && null == state.bleMfgrIdList) {
                    stopHeartbeat();
                } else {
                    startHeartbeat(prefs);
                }
            }
        }
    }

    /**
     * Accessor for state BSSID matchers
     * @param addressKey the preferences key for which to build the matcher
     * @return the matcher corresponding to the key as set in the preferences
     */
    public Matcher getBssidFilterMatcher(final String addressKey) {
        if (null != state) {
            if (PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS.equals(addressKey)) {
                return state.bssidDisplayExclusions;
            } else if (PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS.equals(addressKey)) {
                return state.bssidLogExclusions;
            } else if (PreferenceKeys.PREF_ALERT_ADDRS.equals(addressKey)) {
                return state.bssidAlertList;
            } else if (PreferenceKeys.PREF_ALERT_BLE_MFGR_IDS.equals(addressKey)) {
                return state.bleMfgrIdList;
            }
        }
        return null;
    }

    /**
     * Build a BSSID matcher from preferences for the supplied key
     * @param prefs the SharedPreferences instance for the application
     * @param addressKey the preferences key of the matching settings for which to build the matcher
     * @return a Matcher instance corresponding to the current user settings from preferences
     */
    private Matcher generateBssidFilterMatcher(final SharedPreferences prefs, final String addressKey) {
        Gson gson = new Gson();
        Matcher matcher = null;
        String[] values = gson.fromJson(prefs.getString(addressKey, "[]"), String[].class);
        if (values.length > 0) {
            StringBuilder sb = new StringBuilder("^(");
            boolean first = true;
            for (String value : values) {
                if (first) {
                    first = false;
                } else {
                    sb.append("|");
                }
                sb.append(value);
                if (value.length() == 17 || value.length() == 4) {
                    sb.append("$");
                }
            }
            sb.append(")");
            //DEBUG: Logging.info("building regex from: " + sb.toString());
            Pattern pattern = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
            matcher = pattern.matcher("");
        }
        return matcher;
    }

    public boolean inEmulator() {
        return state.inEmulator;
    }

    public BatteryLevelReceiver getBatteryLevelReceiver() {
        return batteryLevelReceiver;
    }

    public GNSSListener getGPSListener() {
        return state.GNSSListener;
    }

    public PhoneState getPhoneState() {
        return state.phoneState;
    }

    @Override
    public boolean isFinishing() {
        //ALIBI: seeing ostensibly impossible crashes without null checks exclusively on HONOR devices
        return null != state && null != state.finishing && state.finishing.get();
    }

    public boolean isTransferring() {
        return state.transferring.get();
    }

    public boolean isScanning() {
        return isScanning(this);
    }

    public static boolean isScanning(final Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PreferenceKeys.PREF_SCAN_RUNNING, true);
    }

    public void playNewNetSound() {
        try {
            if (state.soundNewPop != null && !state.soundNewPop.isPlaying()) {
                // play sound on something new
                state.soundNewPop.start();
            } else {
                Logging.info("\tsoundNewPop is playing or null");
            }
        } catch (IllegalStateException ex) {
            // ohwell, likely already playing
            Logging.info("\texception trying to play sound: " + ex);
        }
    }

    public void playRunNetSound() {
        try {
            if (state.soundPop != null && !state.soundPop.isPlaying()) {
                // play sound on something new
                state.soundPop.start();
            } else {
                Logging.info("\tsoundPop is playing or null");
            }
        } catch (IllegalStateException ex) {
            // ohwell, likely already playing
            Logging.info("\texception trying to play sound: " + ex);
        }
    }

    private void setupActivationDialog(final SharedPreferences prefs) {
        final boolean willActivateBt = canBtBeActivated();
        final boolean willActivateWifi = canWifiBeActivated();
        final boolean useBt = prefs.getBoolean(PreferenceKeys.PREF_SCAN_BT, true);
        final boolean alertVersions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
        int pieBadCount = prefs.getInt(ListFragment.PREF_PIE_BAD_TOAST_COUNT, 0);
        int qBadCount = prefs.getInt(ListFragment.PREF_Q_BAD_TOAST_COUNT, 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            if ((willActivateBt && useBt) || willActivateWifi || alertVersions) {
                String activationMessages = "";

                SharedPreferences.Editor editor = prefs.edit();
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                    if (pieBadCount < 5) activationMessages = getString(R.string.pie_bad);
                    editor.putInt(ListFragment.PREF_PIE_BAD_TOAST_COUNT, pieBadCount + 1);

                } else if (Build.VERSION.SDK_INT == 29) {
                    if (qBadCount < 5) activationMessages = getString(R.string.q_bad);
                    editor.putInt(ListFragment.PREF_Q_BAD_TOAST_COUNT, qBadCount + 1);
                }
                editor.apply();

                if (willActivateBt && useBt) {
                    if (!activationMessages.isEmpty()) activationMessages += "\n";
                    activationMessages += getString(R.string.turn_on_bt);
                    if (willActivateWifi) {
                        activationMessages += "\n";
                    }
                }

                if (willActivateWifi) {
                    activationMessages += getString(R.string.turn_on_wifi);
                }
                // tell user, cuz this takes a little while
                if (!activationMessages.isEmpty()) {
                    String finalActivationMessages = activationMessages;
                    handler.post(() -> WiGLEToast.showOverActivity(this, R.string.app_name, finalActivationMessages, Toast.LENGTH_LONG));
                }
            }
        });
    }

    private boolean canWifiBeActivated() {
        final WifiManager wifiManager = (WifiManager) this.getApplicationContext().
                getSystemService(Context.WIFI_SERVICE);
        if (null == wifiManager) {
            return false;
        }
        return !wifiManager.isWifiEnabled() && !state.inEmulator;
    }

    private void setupWifi(final SharedPreferences prefs) {
        final WifiManager wifiManager = (WifiManager) this.getApplicationContext().
                getSystemService(Context.WIFI_SERVICE);
        final Editor edit = prefs.edit();

        // keep track of for later
        boolean turnedWifiOn = false;
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            turnedWifiOn = true;
            // switch this to androidx call when it becomes available
            if (Build.VERSION.SDK_INT >= 29) {
                final Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                startActivityForResult(panelIntent, ACTION_WIFI_CODE);
            } else {
                // open wifi setting pages after a few seconds
                new Handler().postDelayed(() -> {
                    WiGLEToast.showOverActivity(MainActivity.this, R.string.app_name,
                            getString(R.string.turn_on_wifi), Toast.LENGTH_LONG);
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                }, 3000);
            }
        }
        edit.apply();

        if (state.wifiReceiver == null) {
            Logging.info("\tnew wifiReceiver");
            // wifi scan listener
            // this receiver is the main workhorse of the entire app
            state.wifiReceiver = new WifiReceiver(this, state.dbHelper);
            state.wifiReceiver.setupWifiTimer(turnedWifiOn);
        }
        if (state.cellReceiver == null) {
            Logging.info("\tnew cellReceiver");
            state.cellReceiver = new CellReceiver(this, state.dbHelper, getApplicationContext());
            state.cellReceiver.setupCellTimer(turnedWifiOn);
        } else {
            state.cellReceiver.setupCellTimer(false);
        }

        // register wifi receiver
        setupWifiReceiverIntent();

        if (state.wifiLock == null && wifiManager != null) {
            Logging.info("\tlock wifi radio on");
            // lock the radio on (only works in 28 (P) and lower)
            state.wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, ListFragment.WIFI_LOCK_NAME);
            state.wifiLock.acquire();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Check which request we're responding to
        if (requestCode == ACTION_WIFI_CODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Logging.info("wifi turned on");
            } else {
                Logging.info("wifi NOT turned on, resultCode: " + resultCode);
            }
        } else if (requestCode == ACTION_TTS_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                state.tts = new TextToSpeech(getApplicationContext(), this);
            } else {
                try {
                    PackageManager pm = getPackageManager();
                    Intent installTTSIntent = new Intent();
                    installTTSIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    ResolveInfo resolveInfo = pm.resolveActivity(installTTSIntent, PackageManager.MATCH_DEFAULT_ONLY);

                    if (resolveInfo == null) {
                        Logging.error("ACTION_TTS_CALLBACK: resolve ACTION_INSTALL_TTS_DATA via package mgr.");
                    } else {
                        startActivity(installTTSIntent);
                    }
                } catch (Exception e) {
                    Logging.error("ACTION_TTS_CALLBACK: failed to issue ACTION_INSTALL_TTS_DATA", e);
                }
            }
        } else {
            Logging.info("MainActivity: Unhandled requestCode: " + requestCode
                    + " resultCode: " + resultCode);
        }
    }

    private void setupWifiReceiverIntent() {
        // register
        Logging.info("register BroadcastReceiver");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(state.wifiReceiver, intentFilter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(state.wifiReceiver, intentFilter);
        }
    }

    private boolean canBtBeActivated() {
        try {
            final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt == null) {
                Logging.info("No bluetooth adapter");
                return false;
            }
            if (!bt.isEnabled()) {
                return true;
            }
        } catch (java.lang.SecurityException sex) {
            Logging.warn("bt activation security exception");
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    public void setupBluetooth(final SharedPreferences prefs) {
        try {
            final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt == null) {
                Logging.info("No bluetooth adapter");
                return;
            }
            final Editor edit = prefs.edit();
            if (prefs.getBoolean(PreferenceKeys.PREF_SCAN_BT, true)) {
                //NB: almost certainly getting specious 'false' answers to isEnabled.
                //  BluetoothAdapter.STATE_TURNING_OFF also a possible match
                if (bt.getState() == BluetoothAdapter.STATE_OFF ||
                        bt.getState() == BluetoothAdapter.STATE_TURNING_OFF) {
                    Logging.info("Enable bluetooth");
                    edit.putBoolean(PreferenceKeys.PREF_BT_WAS_OFF, true);
                    bt.enable();
                } else {
                    edit.putBoolean(PreferenceKeys.PREF_BT_WAS_OFF, false);
                }
                edit.apply();
                if (state.bluetoothReceiver == null) {
                    Logging.info("\tnew bluetoothReceiver");
                    // dynamically detect BTLE feature - prevents occasional NPEs
                    boolean hasLeSupport = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
                    if (hasLeSupport) {
                        //initialize the two global maps for BT mfgr/service UUID lookups
                        AsyncTask.execute(() -> {
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader((getAssets().open("btmember.yaml"))))) {
                                Constructor constructor = new Constructor(new LoaderOptions());
                                Yaml yaml = new Yaml(constructor);
                                final HashMap<String, Object> data = yaml.load(reader);
                                final List<LinkedHashMap<String, Object>> entries = (List<LinkedHashMap<String, Object>>) data.get("uuids");
                                state.btVendors = new HashMap<>();
                                if (null != entries) {
                                    for (LinkedHashMap<String, Object> entry : entries) {
                                        state.btVendors.put((Integer) entry.get("uuid"), (String) entry.get("name"));
                                    }
                                    Logging.info("BLE members initialized: " + entries.size() + " entries");
                                }
                            } catch (IOException e) {
                                Logging.error("Failed to load BLE member yaml:", e);
                            }
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader((getAssets().open("btco.yaml"))))) {
                                Constructor constructor = new Constructor(new LoaderOptions());
                                Yaml yaml = new Yaml(constructor);
                                final HashMap<String, Object> data = yaml.load(reader);
                                final List<LinkedHashMap<String, Object>> entries = (List<LinkedHashMap<String, Object>>) data.get("company_identifiers");
                                state.btMfgrIds = new HashMap<>();
                                if (null != entries) {
                                    for (LinkedHashMap<String, Object> entry : entries) {
                                        state.btMfgrIds.put((Integer) entry.get("value"), (String) entry.get("name"));
                                    }
                                    Logging.info("BLE mfgrs initialized: "+entries.size()+" entries");
                                }
                            } catch (IOException e) {
                                Logging.error("Failed to load BLE mfgr yaml: ",e);
                            }
                            state.btServiceUuids = new HashMap<>();
                            state.btCharUuids = new HashMap<>();
                            setupBleUuids("ble_svc_uuids.yaml", state.btServiceUuids);
                            setupBleUuids("ble_char_uuids.yaml", state.btCharUuids);
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader((getAssets().open("appearance_values.yaml"))))) {
                                Constructor constructor = new Constructor(new LoaderOptions());
                                Yaml yaml = new Yaml(constructor);
                                final HashMap<Integer, Object> data = yaml.load(reader);
                                final List<LinkedHashMap<String, Object>> entries = (List<LinkedHashMap<String, Object>>) data.get("appearance_values");
                                state.btAppearance = new HashMap<>();
                                if (null != entries) {
                                    for (LinkedHashMap<String, Object> entry : entries) {
                                        final List<LinkedHashMap<String, Object>> subEntries = (List<LinkedHashMap<String, Object>>) entry.get("subcategory");
                                        Map<Integer, String> subcategories = null;
                                        if (null != subEntries) {
                                            subcategories = new HashMap<>();
                                            for (LinkedHashMap<String, Object> subEntry : subEntries) {
                                                if (null != subEntry) {
                                                    int value = (Integer) subEntry.get("value");
                                                    String name = (String) subEntry.get("name");
                                                    if (null != name) {
                                                        subcategories.put(value, name);
                                                    }
                                                }
                                            }
                                        }
                                        state.btAppearance.put((Integer) entry.get("category"), new BluetoothUtil.AppearanceCategory( (String) entry.get("name"), subcategories));
                                    }
                                    Logging.info("BLE appearance initialized: " + entries.size() + " categories");
                                }
                            } catch (IOException e) {
                                Logging.error("Failed to load BLE appearance yaml:", e);
                            }
                        });
                    }

                    // bluetooth scan listener
                    // this receiver is the main workhorse of bluetooth scanning
                    state.bluetoothReceiver = new BluetoothReceiver(state.dbHelper,
                            hasLeSupport, prefs);
                    state.bluetoothReceiver.setupBluetoothTimer(true);
                }
                Logging.info("\tregister bluetooth BroadcastReceiver");
                final IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(state.bluetoothReceiver, intentFilter, RECEIVER_EXPORTED);
                } else {
                    registerReceiver(state.bluetoothReceiver, intentFilter);
                }
            }
        } catch (SecurityException e) {
            Logging.error("exception initializing bluetooth: ", e);
        } catch (Exception e) {
            //ALIBI: there's a lot of wonkiness in real-world BT adapters
            //  seeing them go null during this block after null check passes,
            Logging.error("failure initializing bluetooth: ", e);
        }
    }

    @SuppressLint("MissingPermission")
    public void endBluetooth(SharedPreferences prefs) {
        if (state.bluetoothReceiver != null) {
            state.bluetoothReceiver.stopScanning();
        }

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            } catch (final SecurityException ex) {
                Logging.info("\tbluetoothReceiver access: " + ex);
            }
        }
        try {
            Logging.info("\tunregister bluetoothReceiver");
            unregisterReceiver(state.bluetoothReceiver);
            state.bluetoothReceiver = null;
        } catch (final IllegalArgumentException ex) {
            //ALIBI: it's fine to call and fail here.
            Logging.info("\tbluetoothReceiver not registered: " + ex);
        }

        final boolean btWasOff = prefs.getBoolean(PreferenceKeys.PREF_BT_WAS_OFF, false);
        //ALIBI: this pref seems to be persisting across runs, shutting down BT on exit when it was active on start.
        Editor removeBtPref = prefs.edit();
        removeBtPref.remove(PreferenceKeys.PREF_BT_WAS_OFF);
        removeBtPref.apply();

        // don't call on emulator, it crashes it
        if (btWasOff && !state.inEmulator) {
            // ALIBI: we disabled this for WiFi since we had weird errors with root window disposal. Uncomment if we get that resolved?
            //WiGLEToast.showOverActivity(this, R.string.app_name, getString(R.string.turning_bt_off));

            // turn it off now that we're done
            Logging.info("\tturning bluetooth back off");
            try {
                final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
                if (bt == null) {
                    Logging.info("\tNo bluetooth adapter");
                } else if (bt.isEnabled()) {
                    Logging.info("\tDisable bluetooth");
                    bt.disable();
                }
            } catch (final SecurityException ex) {
                Logging.info("\tbluetoothReceiver access: " + ex);
            } catch (Exception ex) {
                Logging.error("exception turning bluetooth back off: " + ex, ex);
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
        Logging.info("setTransferring");
        state.transferring.set(true);
    }

    public void scheduleScan() {
        state.wifiReceiver.scheduleScan();
    }

    public void speak(final String string) {
        final MainActivity a = MainActivity.getMainActivity();
        if (a != null && !a.isMuted() && state.tts != null) {
            state.tts.speak(string, TextToSpeech.QUEUE_ADD, null,
                    "WiGLEtts-"+utteranceSequenceGenerator.getAndAdd(1L));
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
                    Logging.info("\t" + name + " service connected");
                    final WigleService.WigleServiceBinder binder = (WigleService.WigleServiceBinder) iBinder;
                    state.wigleService = binder.getService();
                }

                @Override
                public void onServiceDisconnected(final ComponentName name) {
                    Logging.info("\t" + name + " service disconnected");
                }
            };

            // have to use the app context to bind to the service, cuz we're in tabs
            // http://code.google.com/p/android/issues/detail?id=2483#c2
            final Intent serviceIntent = new Intent(getApplicationContext(), WigleService.class);
            final boolean bound = getApplicationContext().bindService(serviceIntent, state.serviceConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
            Logging.info("\tservice bound: " + bound);
        }
    }

    private void setupLocation(final SharedPreferences prefs) {
        final LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        try {
            // check if there is a gps
            Logging.info("\tGNSS HW: "+LocationManagerCompat.getGnssHardwareModelName(locationManager)+" year: "+LocationManagerCompat.getGnssYearOfHardware(locationManager)+ " enabled: "+ LocationManagerCompat.isLocationEnabled(locationManager));
            final LocationProvider locProvider = locationManager.getProvider(GPS_PROVIDER);

            if (locProvider == null) {
                WiGLEToast.showOverActivity(this, R.string.app_name, getString(R.string.no_gps_device), Toast.LENGTH_LONG);
            } else if (!locationManager.isProviderEnabled(GPS_PROVIDER)) {
                // gps exists, but isn't on
                WiGLEToast.showOverActivity(this, R.string.app_name, getString(R.string.turn_on_gps), Toast.LENGTH_LONG);

                final Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                try {
                    startActivity(myIntent);
                } catch (Exception ex) {
                    Logging.error("exception trying to start location activity: " + ex, ex);
                }
            }
        } catch (final SecurityException ex) {
            Logging.info("Security exception in setupLocation: " + ex);
            return;
        }

        if (state.GNSSListener == null) {
            // force a listener to be created
            boolean logRoutes = prefs.getBoolean(PreferenceKeys.PREF_LOG_ROUTES, false);
            if (logRoutes) {
                startRouteLogging(prefs);
            }
            boolean displayRoute = prefs.getBoolean(PreferenceKeys.PREF_VISUALIZE_ROUTE, false);
            if (displayRoute) {
                startRouteMapping(prefs);
            }
            internalHandleScanChange(prefs.getBoolean(PreferenceKeys.PREF_SCAN_RUNNING, true));
        }
    }

    public void handleScanChange(final boolean isScanning) {
        final boolean oldIsScanning = isScanning();
        if (isScanning == oldIsScanning) {
            Logging.info("\tmain handleScanChange: no difference, returning");
            return;
        }

        final SharedPreferences prefs = getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
        final Editor edit = prefs.edit();
        if (isScanning) {
            edit.putLong(PreferenceKeys.PREF_STARTTIME_CURRENT_SCAN, System.currentTimeMillis());
        } else {
            final long scanTime = prefs.getLong(PreferenceKeys.PREF_CUMULATIVE_SCANTIME_RUN, 0L);
            final long lastScanStart = prefs.getLong(PreferenceKeys.PREF_STARTTIME_CURRENT_SCAN, System.currentTimeMillis());
            final long newTare = scanTime + System.currentTimeMillis() - lastScanStart;
            edit.putLong(PreferenceKeys.PREF_CUMULATIVE_SCANTIME_RUN, newTare);
        }

        edit.putBoolean(PreferenceKeys.PREF_SCAN_RUNNING, isScanning);
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
            Logging.error("Unable to get listfragment: ",ex);
        }
        return null;
    }

    private void internalHandleScanChange(final boolean isScanning) {
        Logging.info("\tmain internalHandleScanChange: isScanning now: " + isScanning);
        ListFragment listFragment = getListFragmentIfCurrent();

        if (isScanning) {
            if (listFragment != null) {
                listFragment.setScanStatusUI(getString(R.string.list_scanning_on));
                listFragment.setScanningStatusIndicator(true);
            }
            if (state.wifiReceiver != null) {
                state.wifiReceiver.updateLastScanResponseTime();
            }
            if (state.cellReceiver != null) {
                state.cellReceiver.setupCellTimer(false);
            }
            // turn on location updates
            this.setLocationUpdates(getLocationSetPeriod(), 0f);

            if (!state.wifiLock.isHeld()) {
                state.wifiLock.acquire();
            }
        } else {
            if (listFragment != null) {
                listFragment.setScanStatusUI(getString(R.string.list_scanning_off));
                listFragment.setScanningStatusIndicator(false);
            }
            if (state.cellReceiver != null) {
                state.cellReceiver.stopCellTimer();
            }
            // turn off location updates
            this.setLocationUpdates(0L, 0f);
            state.GNSSListener.handleScanStop();
            if (state.wifiLock.isHeld()) {
                try {
                    state.wifiLock.release();
                } catch (SecurityException ex) {
                    // a case where we have a leftover lock from another run?
                    Logging.info("\texception releasing wifilock: " + ex);
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
            Logging.error("Security exception in setLocationUpdates: " + ex, ex);
        }
    }

    private void internalSetLocationUpdates(final long updateIntervalMillis, final float updateMeters)
            throws SecurityException {
        final LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        if (state.GNSSListener != null) {
            // remove any old requests
            locationManager.removeUpdates(state.GNSSListener);
            if (gnssStatusCallback != null) {
                locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
            }
            if (gnssMeasurementsCallback != null) {
                locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback);
            }
        }

        // create a new listener to try and get around the gps stopping bug
        state.GNSSListener = new GNSSListener(this, state.dbHelper);
        state.GNSSListener.setMapListener(MappingFragment.STATIC_LOCATION_LISTENER);
        final SharedPreferences prefs = getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);

        try {
            gnssStatusCallback = new GnssStatus.Callback() {
                @Override
                public void onStarted() {
                }

                @Override
                public void onStopped() {
                    state.GNSSListener.handleScanStop();
                }

                @Override
                public void onFirstFix(int ttffMillis) {
                }

                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    if (null != state && null != state.GNSSListener && !isFinishing()) {
                        state.GNSSListener.onGnssStatusChanged(status);
                    }
                }
            };
            locationManager.registerGnssStatusCallback(gnssStatusCallback);

            // gnss full tracking option, available in android sdk 31
            final boolean useGnssFull = prefs.getBoolean(PreferenceKeys.PREF_GPS_GNSS_FULL, false);
            if (useGnssFull && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                gnssMeasurementsCallback = new GnssMeasurementsEvent.Callback() {
                    @Override
                    public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
                        Logging.debug("GnssMeasurements clock: " + eventArgs.getClock());
                    }
                };

                final GnssMeasurementRequest request = new GnssMeasurementRequest.Builder()
                        .setFullTracking(useGnssFull).build();
                locationManager.registerGnssMeasurementsCallback(request,
                        ContextCompat.getMainExecutor(getApplicationContext()),
                        gnssMeasurementsCallback
                );
            }
        } catch (final SecurityException ex) {
            Logging.info("\tSecurity exception adding status listener: " + ex, ex);
        } catch (final Exception ex) {
            Logging.error("Error registering for gnss: " + ex, ex);
        }

        final boolean useNetworkLoc = prefs.getBoolean(PreferenceKeys.PREF_USE_NETWORK_LOC, false);

        final List<String> providers = locationManager.getAllProviders();
        if (providers != null) {
            for (String provider : providers) {
                Logging.info("\tavailable provider: " + provider + " updateIntervalMillis: " + updateIntervalMillis);
                if (!useNetworkLoc && LocationManager.NETWORK_PROVIDER.equals(provider)) {
                    // skip!
                    continue;
                }
                if (!"passive".equals(provider) && updateIntervalMillis > 0L) {
                    Logging.info("\tusing provider: " + provider);
                    try {
                        locationManager.requestLocationUpdates(provider, updateIntervalMillis, updateMeters, state.GNSSListener);
                    } catch (final SecurityException ex) {
                        Logging.info("\tSecurity exception adding status listener: " + ex, ex);
                    }
                }
            }

            if (updateIntervalMillis <= 0L) {
                Logging.info("removing location listener: " + state.GNSSListener);
                try {
                    locationManager.removeUpdates(state.GNSSListener);
                    if (gnssStatusCallback != null) {
                        locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
                    }
                    if (gnssMeasurementsCallback != null) {
                        locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback);
                    }
                    locationManager.removeUpdates(state.GNSSListener);
                } catch (final SecurityException ex) {
                    Logging.info("Security exception removing status listener: " + ex, ex);
                }
            }
        }
    }

    private void setupBleUuids (final String uuidFileName, Map<Integer, String> uuidDestination) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader((getAssets().open(uuidFileName))))) {
            Constructor constructor = new Constructor(new LoaderOptions());
            Yaml yaml = new Yaml(constructor);
            final HashMap<String, Object> data = yaml.load(reader);
            final List<LinkedHashMap<String, Object>> entries =
                    (List<LinkedHashMap<String, Object>>) data.get("uuids");
            if (null != entries) {
                for (LinkedHashMap<String, Object> entry : entries) {
                    uuidDestination.put(((Integer) entry.get("uuid")), (String) entry.get("id"));
                }
                Logging.info("BLE " + uuidFileName + " initialized: " +
                        entries.size()+" entries");
            }
        } catch (IOException e) {
            Logging.error("Failed to load BLE "+uuidFileName+": ",e);
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
            sb.append(i);
        }
        return sb.toString();
    }

    public long getLocationSetPeriod() {
        final SharedPreferences prefs = getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
        long setPeriod = prefs.getLong(PreferenceKeys.GPS_SCAN_PERIOD, MainActivity.LOCATION_UPDATE_INTERVAL);
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
        Logging.info("transfer complete");
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

    public void setScanStatusUI(final int resultSize, final long inMs) {
        if (null != mainActivity) {
            final String status =
                    mainActivity.getString(R.string.scanned_in, resultSize, inMs, mainActivity.getString(R.string.ms_short));
            setScanStatusUI(status);
        }
    }

    public void setScanStatusUI(String status) {
        if (status == null) {
            status = state.previousStatus;
        }
        if (status != null) {
            // keep around a previous, for orientation changes
            state.previousStatus = status;
            ListFragment listFragment = getListFragmentIfCurrent();

            if (listFragment != null) {
                // tell list
                listFragment.setScanStatusUI(status);
            }
        }
    }

    public void setDBQueue(final long queue) {
        final String status = getString(R.string.dash_db_queue, NumberFormat.getInstance().format(queue));
        if (status != null) {
            ListFragment listFragment = getListFragmentIfCurrent();
            if (listFragment != null) {
                // tell list
                listFragment.setDBStatusUI(status, queue);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        return mDrawerToggle.onOptionsItemSelected(item);
    }

    public void finishSoon() {
        this.state.wigleService = null;
        finishSoon(FINISH_TIME_MILLIS, false, false);
    }

    public void finishSoon(final long finishTimeMillis, final boolean restart) {
        finishSoon(finishTimeMillis, restart, false);
    }

    public void finishSoon(final long finishTimeMillis, final boolean restart, final boolean uiOnly) {
        Logging.info("Will finish in " + finishTimeMillis + "ms");
        new Handler().postDelayed(() -> {
            final Intent i = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getBaseContext().getPackageName());
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                if (state.finishing.get()) {
                    Logging.info("finishSoon: finish already called");
                } else {
                    Logging.info("finishSoon: calling finish now");
                    if (uiOnly) {
                        Logging.info("UI-only termination (ui restart: "+state.uiRestart.get()+")");
                        state.uiRestart.set(true);
                        MainActivity.super.recreate();
                        /*new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                startActivity(i);
                            }
                        }, 10L);*/
                    } else {
                        finish();
                    }
                }

                if (restart) {
                    new Handler().postDelayed(() -> startActivity(i), 10L);
                }
            } else {
                Logging.warn("Intent generation failed during finishSoon thread");
            }
        }, finishTimeMillis);
    }

    /**
     * Never call this directly! call finishSoon to give the service time to show a notification if needed
     */
    @Override
    public void finish() {
        Logging.info("MAIN: finish.");
        if (!state.uiRestart.get()) {
            if (state.wifiReceiver != null) {
                Logging.info("MAIN: finish. networks: " + state.wifiReceiver.getRunNetworkCount());
            }

            final boolean wasFinishing = state.finishing.getAndSet(true);
            if (wasFinishing) {
                Logging.info("MAIN: finish called twice!");
            }

            // interrupt this just in case
            final ObservationUploader observationUploader = state.observationUploader;
            if (observationUploader != null) {
                observationUploader.setInterrupted();
            }

            if (state.GNSSListener != null) {
                // save our location for later runs
                state.GNSSListener.saveLocation();
            }

            // close the db. not in destroy, because it'll still write after that.
            if (state.dbHelper != null) state.dbHelper.close();
            if (state.mxcDbHelper != null) state.mxcDbHelper.close();

            final LocationManager locationManager = (LocationManager) this.getApplicationContext().getSystemService(Context.LOCATION_SERVICE); //ALIBI: avoid activity context-based leaks
            if (state.GNSSListener != null && locationManager != null) {
                try {
                    if (gnssStatusCallback != null) {
                        locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
                    }
                    if (gnssMeasurementsCallback != null) {
                        locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback);
                    }
                    locationManager.removeUpdates(state.GNSSListener);
                } catch (final SecurityException ex) {
                    Logging.error("SecurityException on finish: " + ex, ex);
                } catch (final IllegalStateException ise) {
                    Logging.error("ISE turning off GPS: ", ise);
                } catch (final NullPointerException npe) {
                    Logging.error("NPE turning off GPS: ", npe);
                }
            }

            // stop the service, so when we die it's both stopped and unbound and will die
            final Intent serviceIntent = new Intent(this, WigleService.class);
            stopService(serviceIntent);
            try {
                // have to use the app context to bind to the service, cuz we're in tabs
                final Context c = getApplicationContext();
                if (null != c) {
                    c.unbindService(state.serviceConnection);
                }
            } catch (final IllegalArgumentException ex) {
                Logging.info("serviceConnection not registered: " + ex, ex);
            }

            // release the lock before turning wifi off
            if (state.wifiLock != null && state.wifiLock.isHeld()) {
                try {
                    state.wifiLock.release();
                } catch (Exception ex) {
                    Logging.error("exception releasing wifi lock: " + ex, ex);
                }
            }

            final SharedPreferences prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
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
            Logging.info("MAIN: finish complete.");
        }
        super.finish();
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    /*
     * ALIBI: handle back on old (pre-predictive back) Android versions
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                Logging.info("onKeyDown: not quitting app on back");
                selectFragment(R.id.nav_list);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * pure-background upload method for intent-based uploads
     */
    public void backgroundUploadFile(){
        Logging.info( "background upload file" );
        final State state = getState();
        setTransferring();
        state.observationUploader = new ObservationUploader(this,
                ListFragment.lameStatic.dbHelper,
                (json, isCache) -> { transferComplete();},
                false, false, false);
        try {
            state.observationUploader.startDownload(null);
        } catch (WiGLEAuthException x) {
            Logging.warn("Authentication failure on background run upload");
        }
    }

    public void checkStorage() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            boolean safe;
            boolean external = FileUtility.hasSD();
            if (external) {
                safe = FileUtility.checkExternalStorageDangerZone();
            } else {
                safe = FileUtility.checkInternalStorageDangerZone();
            }
            handler.post(() -> {
                if (!safe) {
                    AlertDialog.Builder iseDlgBuilder = new AlertDialog.Builder(this);
                    iseDlgBuilder.setMessage(external?R.string.no_external_space_message:R.string.no_internal_space_message)
                            .setTitle(external?R.string.no_external_space_title:R.string.no_internal_space_title)
                            .setCancelable(true)
                            .setPositiveButton(R.string.ok, (dialog, which) -> {
                                if (null != dialog) {
                                    dialog.dismiss();
                                }});
                    final Dialog dialog = iseDlgBuilder.create();
                    if (!isFinishing()) {
                        dialog.show();
                    }
                }
            });
        });
    }

    /**
     * When we start a new run/start logging for a run, provide the new run a new ID
     * @param prefs current sharedPreferences
     */
    public void startRouteLogging(SharedPreferences prefs) {
        // ALIBI: we initialize this value to 0L on table setup as well.
        long lastRouteId = prefs.getLong(PreferenceKeys.PREF_ROUTE_DB_RUN, 0L);
        long routeId = lastRouteId+1L; //ALIBI: always skips the default 0 run id. (see vis below)
        final Editor edit = prefs.edit();
        edit.putLong(PreferenceKeys.PREF_ROUTE_DB_RUN, routeId);
        edit.apply();
    }

    /**
     * since we do the prefs check on logging, no real need to do anything here
     */
    public void endRouteLogging() {
        //TODO: null operation for now
    }

    /**
     * if we're already logging the route, we'll simply use that route log when displaying.
     * If we're not already logging, we'll log the route to run ID 0 in the routes table.
     * ALIBI: since route lengths may be extreme, ring-buffer/dynamic allocation could cause serious problems
     * @param prefs current sharedPreferences
     */
    public void startRouteMapping(SharedPreferences prefs) {
        boolean logRoutes = prefs.getBoolean(PreferenceKeys.PREF_LOG_ROUTES, false);
        //ALIBI: we'll piggyback off the current route, if we're logging it
        if (!logRoutes) {
            if (state != null && state.dbHelper != null) {
                final DatabaseHelper dbHelper = state.dbHelper;
                try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                    executor.execute(() -> {
                        try {
                            dbHelper.clearDefaultRoute();
                        } catch (DBException dbe) {
                            Logging.warn("unable to clear default route on startRouteMapping: ", dbe);
                        }
                    });
                    executor.shutdown();
                }
            }
        }
    }

    /**
     * if we're logging to the default slot, we'll clear here, JiC. This might be unnecessary, or even
     * pose problems if we decide to stop adding, but keep visualizing the route up to that point.
     * @param prefs current sharedPreferences
     */
    public void endRouteMapping(SharedPreferences prefs) {
        boolean logRoutes = prefs.getBoolean(PreferenceKeys.PREF_LOG_ROUTES, false);
        if (!logRoutes) {
            if (state != null && state.dbHelper != null) {
                try {
                    state.dbHelper.clearDefaultRoute();
                } catch (DBException dbe) {
                    Logging.warn("unable to clear default route on end-viz: ", dbe);
                }
            }
        }
    }

    /**
     * When prefs/the token manager have been updated in response to authorization/de-auth, recreate in order to reflect the state change.
     */
    public static void refreshApiManager() {
        final MainActivity mainActivity = MainActivity.getMainActivity();
        if (null != mainActivity) {
            MainActivity.State s = mainActivity.getState();
            if (null != s) {
                SharedPreferences prefs = mainActivity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
                if (null != prefs) {
                    s.apiManager = new WiGLEApiManager(prefs, mainActivity.getApplicationContext());
                    return;
                } else {
                    Logging.error("Unable to update aipManager: null prefs.");
                }
            }
        } else {
            Logging.error("Unable to update aipManager: null MainActivity.");
        }
    }

    /**
     * respond to TTS initialization
     * @param status status of initialization
     */
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && state != null && state.tts != null) {
            Locale locale = getLocale(getApplicationContext(), getApplicationContext().getResources().getConfiguration());
            Logging.info("LOCALE: "+locale);
            if(state.tts.isLanguageAvailable(locale)==TextToSpeech.LANG_AVAILABLE) {
                state.tts.setLanguage(locale);
            } else {
                Logging.info("preferred locale: [" +locale+"] not available on device.");
            }
            state.ttsChecked = true;
        } else if (status == TextToSpeech.SUCCESS) {
            Logging.info("TTS init successful, but state or TTS engine was null");
        } else if (status == TextToSpeech.ERROR) {
            Logging.error("TTS init failed: "+status);
        }
    }

    public String getBleVendor(final int i) {
        final State s = state;
        return s == null ? null : s.btVendors.get(i);
    }

    public String getBleMfgr(final int i) {
        final State s = state;
        return s == null ? null : s.btMfgrIds.get(i);
    }

    public String getBleService(final String uuid) {
        final State s = state;
        int key = Integer.parseInt(uuid, 16);
        return s == null ? null : s.btServiceUuids.get(key);
    }

    public String getBleCharacteristic(final String uuid) {
        final State s = state;
        int key = Integer.parseInt(uuid, 16);
        return s == null ? null : s.btCharUuids.get(key);
    }

    /**
     * Update the last highest level seen matching the current BSSID alert filter (since last announcement)
     * @param value the candidate to update if higher
     */
    public void updateLastHighSignal(final Integer value) {
        final State s = state;
        if (null != value && !value.equals(Integer.MIN_VALUE)) {
            s.lastHighestSignal.updateAndGet(currentValue -> value > currentValue ?
                    value : currentValue);
        }
    }

    /**
     * Get the related String for category and sub-category.
     * @param category category int ID
     * @param subcategory subcategory int ID
     * @return the composite string name
     */
    public String getBleAppearance(final Integer category, final Integer subcategory) {
        final State s = state;
        if (s.btAppearance != null) {
            final BluetoothUtil.AppearanceCategory cat = s.btAppearance.get(category);
            if (null != cat && cat.getSubcategories() != null) {
                return cat.getName() + ": "+cat.getSubcategories().get(subcategory);
            } else if (null != cat ){
                return cat.getName();
            }
        }
        return null;
    }

    public boolean hasWakeLock() {
        final State s = state;
        return s != null && s.screenLocked;
    }

    private void startHeartbeat(SharedPreferences prefs) {
        if (null != state.bssidMatchHeartbeat) {
            state.bssidMatchHeartbeat.interrupt();
            state.bssidMatchHeartbeat = null;
        }
        state.bssidMatchHeartbeat = new BssidMatchingAudioThread(
                prefs,
                state.soundScanning,
                state.soundContact, state.lastHighestSignal, state.wifiReceiver);
        state.bssidMatchHeartbeat.start();
    }

    private void stopHeartbeat() {
        if (null != state.bssidMatchHeartbeat) {
            state.bssidMatchHeartbeat.interrupt();
            try {
                state.bssidMatchHeartbeat.join();
            } catch (InterruptedException e) {
                Logging.error("Failed to join bssidMatchHeartbeat");
            }
            state.bssidMatchHeartbeat = null;
        }
    }
}
