package net.wigle.wigleandroid;

import static android.view.View.GONE;

import static net.wigle.wigleandroid.listener.GNSSListener.MIN_ROUTE_LOCATION_DIFF_METERS;
import static net.wigle.wigleandroid.listener.GNSSListener.MIN_ROUTE_LOCATION_DIFF_TIME;
import static net.wigle.wigleandroid.listener.GNSSListener.MIN_ROUTE_LOCATION_PRECISION_METERS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.ui.PrefsBackedCheckbox;
import net.wigle.wigleandroid.ui.UINumberFormat;
import net.wigle.wigleandroid.util.FilterUtil;
import net.wigle.wigleandroid.util.HeadingManager;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.StatsUtil;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Superclass for mapping activity. Concrete child classes should contain map-tech specific elements
 */
public abstract class AbstractMappingFragment extends Fragment {
    public static final String MAP_DIALOG_PREFIX = "";
    public static final LatLng DEFAULT_POINT = new LatLng(41.95d, -87.65d);
    // parameters for polyline simplification package: https://github.com/hgoebl/simplify-java
    // ALIBI: we could tighten these parameters significantly, but it results in wonky over-
    //   simplifications leading up to the present position (since there are no "subsequent" values
    //   to offset the algo's propensity to over-optimize the "end" cap.)
    // Values chosen not to overburden most modern Android phones capabilities.
    // assume we need to undertake drastic route line complexity if we exceed this many segments
    protected static final int POLYLINE_PERF_THRESHOLD_COARSE = 15000;
    // perform minor route line complexity simplification if we exceed this many segments
    protected static final int POLYLINE_PERF_THRESHOLD_FINE = 5000;
    // when performing drastic polyline simplification (Radial-Distance), this is our "tolerance value"
    protected static final float POLYLINE_TOLERANCE_COARSE = 20.0f;
    // when performing minor polyline simplification (Douglas-Peucker), this is our "tolerance value"
    protected static final float POLYLINE_TOLERANCE_FINE = 50.0f;
    protected static final int UPDATE_MAP_FILTER = 1;
    protected static final String DIALOG_PREFIX = "DialogPrefix";
    protected static final int DEFAULT_ZOOM = 17;
    protected static final int MENU_ZOOM_IN = 13;
    protected static final int MENU_ZOOM_OUT = 14;
    protected static final int MENU_TOGGLE_LOCK = 15;
    protected static final int MENU_TOGGLE_NEWDB = 16;
    protected static final int MENU_LABEL = 17;
    protected static final int MENU_FILTER = 18;
    protected static final int MENU_CLUSTER = 19;
    protected static final int MENU_TRAFFIC = 20;
    protected static final int MENU_MAP_TYPE = 21;
    protected static final int MENU_WAKELOCK = 22;
    // ALIBI: 15% is actually pretty acceptable for map orientation.
    protected static final float MIN_BEARING_UPDATE_ACCURACY = 54.1f;
    protected static final String MAP_TILE_URL_FORMAT =
            "https://wigle.net/clientTile?zoom=%d&x=%d&y=%d&startTransID=%s&endTransID=%s";
    protected static final String HIGH_RES_TILE_TRAILER = "&sizeX=512&sizeY=512";
    protected static final String ONLY_MINE_TILE_TRAILER = "&onlymine=1";
    protected static final String NOT_MINE_TILE_TRAILER = "&notmine=1";
    protected static final float ROUTE_WIDTH = 20.0f;
    protected static final int OVERLAY_DARK = Color.BLACK;
    protected static final int OVERLAY_LIGHT = Color.parseColor("#F4D03F");
    protected static LocationListener STATIC_LOCATION_LISTENER = null;
    protected final State state = new State();
    protected final String ROUTE_LINE_TAG = "routePolyline";
    private final ExecutorService routeLoadExecutor = Executors.newSingleThreadExecutor();
    protected AtomicBoolean finishing;
    protected Location previousLocation;
    protected int previousRunNets;
    protected Location lastLocation;
    protected HeadingManager headingManager;
    protected NumberFormat numberFormat;
    protected Menu menu;

    protected abstract void setupQuery();
    protected abstract int getLayoutResourceId();
    @SuppressLint("MissingPermission")
    protected abstract void setupMapView(View view, LatLng oldCenter, int oldZoom);
    public abstract void addNetwork(Network network);
    public abstract void updateNetwork(Network network);
    public abstract void reCluster();
    public abstract boolean onOptionsItemSelected(@NonNull final MenuItem item);

    public static LatLng getCenter(final Context context, final LatLng priorityCenter,
                                   final Location previousLocation) {

        LatLng centerPoint = DEFAULT_POINT;
        final Location location = ListFragment.lameStatic.location;
        final SharedPreferences prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);

        if (priorityCenter != null) {
            centerPoint = priorityCenter;
        } else if (location != null) {
            centerPoint = new LatLng(location.getLatitude(), location.getLongitude());
        } else if (previousLocation != null) {
            centerPoint = new LatLng(previousLocation.getLatitude(), previousLocation.getLongitude());
        } else {
            final Location gpsLocation = AbstractMappingFragment.safelyGetLast(context, LocationManager.GPS_PROVIDER);
            final Location networkLocation = AbstractMappingFragment.safelyGetLast(context, LocationManager.NETWORK_PROVIDER);

            if (gpsLocation != null) {
                centerPoint = new LatLng(gpsLocation.getLatitude(), gpsLocation.getLongitude());
            } else if (networkLocation != null) {
                centerPoint = new LatLng(networkLocation.getLatitude(), networkLocation.getLongitude());
            } else {
                // ok, try the saved prefs
                float lat = prefs.getFloat(PreferenceKeys.PREF_PREV_LAT, Float.MIN_VALUE);
                float lon = prefs.getFloat(PreferenceKeys.PREF_PREV_LON, Float.MIN_VALUE);
                if (lat != Float.MIN_VALUE && lon != Float.MIN_VALUE) {
                    centerPoint = new LatLng(lat, lon);
                }
            }
        }
        return centerPoint;
    }

    /**
     * Loads route points from the database on a background thread.
     * Calls onLoaded on the main thread with the result. No-op if prefs is null
     * or PREF_VISUALIZE_ROUTE is false.
     *
     * @param prefs SharedPreferences for route visibility and query params
     * @param onLoaded Consumer invoked on main thread with loaded points (may be empty)
     */
    protected void loadRoutePointsInBackground(final SharedPreferences prefs,
            final Consumer<List<LatLng>> onLoaded) {
        if (prefs == null || !prefs.getBoolean(PreferenceKeys.PREF_VISUALIZE_ROUTE, false)) {
            return;
        }
        routeLoadExecutor.execute(() -> {
            List<LatLng> routePoints = new ArrayList<>();
            try (Cursor routeCursor = ListFragment.lameStatic.dbHelper
                    .getCurrentVisibleRouteIterator(prefs)) {
                if (routeCursor == null) {
                    Logging.info("null route cursor; not mapping");
                    return;
                }
                for (routeCursor.moveToFirst(); !routeCursor.isAfterLast(); routeCursor.moveToNext()) {
                    final double lat = routeCursor.getDouble(0);
                    final double lon = routeCursor.getDouble(1);
                    routePoints.add(new LatLng(lat, lon));
                }
            } catch (Exception e) {
                Logging.error("Unable to load route: ", e);
                return;
            }
            final List<LatLng> pointsToAdd = routePoints;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (getActivity() == null) {
                    return;
                }
                onLoaded.accept(pointsToAdd);
            });
        });
    }

    @SuppressLint("MissingPermission")
    protected static Location safelyGetLast(final Context context, final String provider) {
        Location retval = null;
        try {
            final LocationManager locationManager = (LocationManager) context.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            retval = locationManager.getLastKnownLocation(provider);
        } catch (final IllegalArgumentException | SecurityException ex) {
            Logging.info("exception getting last known location: " + ex);
        }
        return retval;
    }

    //TODO: why no usages?
    public static DialogFragment createSsidFilterDialog(final String prefix) {
        final DialogFragment dialog = new MapDialogFragment();
        final Bundle bundle = new Bundle();
        bundle.putString(DIALOG_PREFIX, prefix);
        dialog.setArguments(bundle);
        return dialog;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        Logging.info("MAP: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language
        final Activity a = getActivity();
        if (null != a) {
            MainActivity.setLocale(a);
            a.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        }
        finishing = new AtomicBoolean(false);
        final Configuration conf = getResources().getConfiguration();
        Locale locale = null;
        if (null != conf && null != conf.getLocales()) {
            locale = conf.getLocales().get(0);
        }
        if (null == locale) {
            locale = Locale.US;
        }
        numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setMaximumFractionDigits(2);
        final SharedPreferences prefs = (null != a) ? a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0) : null;
        if (prefs != null && BuildConfig.DEBUG && HeadingManager.DEBUG && prefs.getBoolean(PreferenceKeys.PREF_MAP_FOLLOW_BEARING, false)) {
            headingManager = new HeadingManager(a);
        }
        setupQuery();
    }

    @Override
    public void onDestroy() {
        routeLoadExecutor.shutdown();
        super.onDestroy();
    }

    public Float getBearing(final Context context) {
        final Location gpsLocation = AbstractMappingFragment.safelyGetLast(context, LocationManager.GPS_PROVIDER);
        if (gpsLocation != null) {
            //DEBUG: Logging.info("acc: "+headingManager.getAccuracy());
            final Float bearing = (gpsLocation.hasBearing() && gpsLocation.getBearing() != 0.0f) ? gpsLocation.getBearing() : null;

            if (null != bearing) {
                //ALIBI: prefer bearing if it's not garbage, because heading almost certainly is.
                if (gpsLocation.hasAccuracy() && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
                    if (gpsLocation.getBearingAccuracyDegrees() < MIN_BEARING_UPDATE_ACCURACY) {
                        return gpsLocation.getBearing();
                    }
                } else {
                    Logging.warn("have GPS location but no headingManager or accuracy");
                    return bearing;
                }
            }
            //ALIBI: heading is too often completely wrong. This is here for debugging only unless things improve.
            if (null != headingManager && BuildConfig.DEBUG && HeadingManager.DEBUG && headingManager.getAccuracy() >= 3.0) {
                // if the fusion of accelerometer and magnetic compass claims it doesn't suck (although it probably still does)
                return headingManager.getHeading(gpsLocation);
            }
        }
        return null;
    }

    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
        Logging.info("MAP: onCreateOptionsMenu");
        MenuItem item;
        final Activity a = getActivity();
        if (null != a) {
            final SharedPreferences prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            final boolean showNewDBOnly = prefs.getBoolean(PreferenceKeys.PREF_MAP_ONLY_NEWDB, false);
            final boolean showLabel = prefs.getBoolean(PreferenceKeys.PREF_MAP_LABEL, true);
            final boolean showCluster = prefs.getBoolean(PreferenceKeys.PREF_MAP_CLUSTER, true);
            final boolean showTraffic = prefs.getBoolean(PreferenceKeys.PREF_MAP_TRAFFIC, true);

            String nameLabel = showLabel ? getString(R.string.menu_labels_off) : getString(R.string.menu_labels_on);
            item = menu.add(0, MENU_LABEL, 0, nameLabel);
            item.setIcon(android.R.drawable.ic_dialog_info);

            String nameCluster = showCluster ? getString(R.string.menu_cluster_off) : getString(R.string.menu_cluster_on);
            item = menu.add(0, MENU_CLUSTER, 0, nameCluster);
            item.setIcon(android.R.drawable.ic_menu_add);

            String nameTraffic = showTraffic ? getString(R.string.menu_traffic_off) : getString(R.string.menu_traffic_on);
            item = menu.add(0, MENU_TRAFFIC, 0, nameTraffic);
            item.setIcon(android.R.drawable.ic_menu_directions);

            item = menu.add(0, MENU_MAP_TYPE, 0, getString(R.string.menu_map_type));
            item.setIcon(R.drawable.map_layer);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

            item = menu.add(0, MENU_FILTER, 0, getString(R.string.settings_map_head));
            item.setIcon(R.drawable.filter);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

            String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
            item = menu.add(0, MENU_TOGGLE_LOCK, 0, name);
            item.setIcon(android.R.drawable.ic_lock_lock);

            String nameDB = showNewDBOnly ? getString(R.string.menu_show_old) : getString(R.string.menu_show_new);
            item = menu.add(0, MENU_TOGGLE_NEWDB, 0, nameDB);
            item.setIcon(android.R.drawable.ic_menu_edit);
        }
        final String wake = MainActivity.isScreenLocked(this) ?
                getString(R.string.menu_screen_sleep) : getString(R.string.menu_screen_wake);
        item = menu.add(0, MENU_WAKELOCK, 0, wake);
        item.setIcon(android.R.drawable.ic_menu_gallery);

        super.onCreateOptionsMenu(menu, inflater);
        this.menu = menu;
    }

    protected static class State {
        protected boolean locked = true;
        protected boolean firstMove = true;
        protected LatLng oldCenter = null;
    }

    /**
     * Checks if a route should be updated based on location accuracy and time/distance thresholds.
     *
     * @param location The location to check
     * @return true if the route should be updated, false otherwise
     */
    protected boolean shouldUpdateRoute(final Location location) {
        if (location.getTime() == 0) {
            return false;
        }
        double accuracy = location.getAccuracy();
        if (accuracy >= MIN_ROUTE_LOCATION_PRECISION_METERS || accuracy <= 0.0d) {
            return false;
        }
        if (lastLocation == null) {
            return true;
        }

        return (location.getTime() - lastLocation.getTime()) > MIN_ROUTE_LOCATION_DIFF_TIME
                && lastLocation.distanceTo(location) > MIN_ROUTE_LOCATION_DIFF_METERS;
    }

    protected void updateStatsViews(View view, SharedPreferences prefs) {
        updateCounterView(view, R.id.stats_wifi, ListFragment.lameStatic.newWifi);
        updateCounterView(view, R.id.stats_cell, ListFragment.lameStatic.newCells);
        updateCounterView(view, R.id.stats_bt, ListFragment.lameStatic.newBt);
        if (null != prefs) {
            updateCounterView(view, R.id.stats_unuploaded, StatsUtil.newNetsSinceUpload(prefs));
        }
        TextView tv = view.findViewById( R.id.stats_dbnets );
        tv.setText(UINumberFormat.counterFormat(ListFragment.lameStatic.dbNets));
        if (prefs != null) {
            float dist = prefs.getFloat(PreferenceKeys.PREF_DISTANCE_RUN, 0f);
            final String distString = UINumberFormat.metersToString(prefs,
                    numberFormat, getActivity(), dist, true);
            tv = view.findViewById(R.id.rundistance);
            tv.setText(distString);
        }
        tv = view.findViewById(R.id.heading);
        final Location gpsLocation = safelyGetLast(getContext(), LocationManager.GPS_PROVIDER);
        if (BuildConfig.DEBUG && HeadingManager.DEBUG) {
            tv.setText(String.format(Locale.ROOT, "heading: %3.2f", ((headingManager != null) ? headingManager.getHeading(gpsLocation) : -1f)));
            if (null != ListFragment.lameStatic.location) {
                tv = view.findViewById(R.id.bearing);
                if (gpsLocation.hasAccuracy() && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
                    tv.setText(String.format(Locale.ROOT,"bearing: %3.2f +/- %3.2f", ListFragment.lameStatic.location.getBearing(), ListFragment.lameStatic.location.getBearingAccuracyDegrees()));
                } else {
                    tv.setText(String.format(Locale.ROOT,"bearing: %3.2f", ListFragment.lameStatic.location.getBearing()));
                }
            }
            tv = view.findViewById(R.id.selectedbh);
            tv.setText(String.format(Locale.ROOT,"chose: %3.2f", getBearing(getContext())));
        } else {
            final View v =view.findViewById(R.id.debug);
            if (null != v) {
                v.setVisibility(GONE);
            }
        }

    }

    protected void updateCounterView(View view, int viewId, long value) {
        TextView tv = view.findViewById(viewId);
        if (tv != null) {
            tv.setText(UINumberFormat.counterFormat(value));
        }
    }

    /**
     * Updates the lock menu item title based on current lock state.
     */
    protected void updateLockMenuItemTitle() {
        if (menu != null) {
            MenuItem item = menu.findItem(MENU_TOGGLE_LOCK);
            String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
            item.setTitle(name);
        }
    }

    public static class MapDialogFragment extends DialogFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            Bundle args = getArguments();
            final String prefix = null != args ? args.getString(DIALOG_PREFIX) : "";

            final Dialog dialog = getDialog();
            final Activity activity = getActivity();
            final View view = inflater.inflate(R.layout.filterdialog, container);
            if (null != dialog) {
                dialog.setTitle(R.string.ssid_filter_head);
            }
            Logging.info("make new dialog. prefix: " + prefix);
            if (null != activity) {
                final SharedPreferences prefs = activity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
                final EditText regex = view.findViewById(R.id.edit_regex);
                regex.setText(prefs.getString(prefix + PreferenceKeys.PREF_MAPF_REGEX, ""));

                final CheckBox invert = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showinvert,
                        prefix + PreferenceKeys.PREF_MAPF_INVERT, false, prefs);
                final CheckBox open = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showopen,
                        prefix + PreferenceKeys.PREF_MAPF_OPEN, true, prefs, value -> FilterUtil.updateWifiGroupCheckbox(view));
                final CheckBox wep = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showwep,
                        prefix + PreferenceKeys.PREF_MAPF_WEP, true, prefs, value -> FilterUtil.updateWifiGroupCheckbox(view));
                final CheckBox wpa = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showwpa,
                        prefix + PreferenceKeys.PREF_MAPF_WPA, true, prefs, value -> FilterUtil.updateWifiGroupCheckbox(view));
                final CheckBox cell = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showcell,
                        prefix + PreferenceKeys.PREF_MAPF_CELL, true, prefs);
                final CheckBox enabled = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.enabled,
                        prefix + PreferenceKeys.PREF_MAPF_ENABLED, true, prefs);
                final CheckBox btc = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showbtc,
                        prefix + PreferenceKeys.PREF_MAPF_BT, true, prefs, value -> FilterUtil.updateBluetoothGroupCheckbox(view));
                final CheckBox btle = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showbtle,
                        prefix + PreferenceKeys.PREF_MAPF_BTLE, true, prefs, value -> FilterUtil.updateBluetoothGroupCheckbox(view));

                FilterUtil.updateWifiGroupCheckbox(view);
                FilterUtil.updateBluetoothGroupCheckbox(view);

                Button ok = view.findViewById(R.id.ok_button);
                ok.setOnClickListener(buttonView -> {
                    try {
                        final SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(prefix + PreferenceKeys.PREF_MAPF_REGEX, regex.getText().toString());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_INVERT, invert.isChecked());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_OPEN, open.isChecked());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_WEP, wep.isChecked());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_WPA, wpa.isChecked());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_CELL, cell.isChecked());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_ENABLED, enabled.isChecked());
                        editor.apply();
                        MainActivity.reclusterMap();

                        if (null != dialog) {
                            dialog.dismiss();
                        }
                    } catch (Exception ex) {
                        // guess it wasn't there anyways
                        Logging.info("exception dismissing filter dialog: " + ex);
                    }
                });

                Button cancel = view.findViewById(R.id.cancel_button);
                cancel.setOnClickListener(buttonView -> {
                    try {
                        regex.setText(prefs.getString(prefix + PreferenceKeys.PREF_MAPF_REGEX, ""));
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showinvert,
                                prefix + PreferenceKeys.PREF_MAPF_INVERT, false, prefs);
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showopen,
                                prefix + PreferenceKeys.PREF_MAPF_OPEN, true, prefs);
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showwep,
                                prefix + PreferenceKeys.PREF_MAPF_WEP, true, prefs);
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showwpa,
                                prefix + PreferenceKeys.PREF_MAPF_WPA, true, prefs);
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showcell,
                                prefix + PreferenceKeys.PREF_MAPF_CELL, true, prefs);
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.enabled,
                                prefix + PreferenceKeys.PREF_MAPF_ENABLED, true, prefs);

                        if (null != dialog) {
                            dialog.dismiss();
                        }
                    } catch (Exception ex) {
                        // guess it wasn't there anyways
                        Logging.info("exception dismissing filter dialog: " + ex);
                    }
                });
            }
            return view;
        }
    }
}
