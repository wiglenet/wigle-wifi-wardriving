package net.wigle.wigleandroid;

import static net.wigle.wigleandroid.background.GpxExportRunnable.EXPORT_GPX_DIALOG;
import static net.wigle.wigleandroid.ui.GpxRecyclerAdapter.DELETE_GPX_DIALOG;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.wigle.wigleandroid.background.GpxExportRunnable;
import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.RouteDescriptor;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.ui.GpxRecyclerAdapter;
import net.wigle.wigleandroid.ui.ScreenChildActivity;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.RouteConfigurable;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.RouteDeleteSelector;
import net.wigle.wigleandroid.util.RouteExportSelector;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import static android.view.View.GONE;

public abstract class AbstractGpxManagementActivity extends ScreenChildActivity implements RouteConfigurable, RouteExportSelector, RouteDeleteSelector, DialogListener {
    protected DatabaseHelper dbHelper;
    protected NumberFormat numberFormat;
    protected final int DEFAULT_MAP_PADDING = 25;
    protected final String CURRENT_ROUTE_LINE_TAG = "currentRoutePolyline";
    protected View infoView;
    protected TextView distanceText;
    protected SharedPreferences prefs;
    protected long exportRouteId = -1L;
    protected long deleteRouteId = -1L;
    protected Object mapView;
    protected Object routePolyline;

    /**
     * Initializes the subclass map library.
     */
    protected void initializeMapLibrary() {
        // Default: M/A for Google Maps
    }

    /**
     * Returns the subclass layout resource ID
     * @return the unique ID of the resource
     */
    protected abstract int getLayoutResourceId();

    /**
     * set up the map (subclass-specific)
     * @param prefs the current SharedPreferences instance for the app
     */
    protected abstract void setupMap(SharedPreferences prefs);

    /**
     * Configures the map for the given route in subclass
     * @param routeDescriptor The route descriptor to configure the map for
     */
    protected abstract void configureMapForRouteInternal(RouteDescriptor routeDescriptor);

    /**
     * Destroy the map view (subclass-specific)
     */
    protected abstract void destroyMapView();

    /**
     * Resumes the map view (subclass-specific)
     */
    protected abstract void resumeMapView();

    /**
     * Pauses the map view (subclass-specific)
     */
    protected abstract void pauseMapView();

    public AbstractGpxManagementActivity() {
        final MainActivity.State s = MainActivity.getStaticState();
        if (null != s) {
            this.dbHelper = s.dbHelper;
        } else {
            this.dbHelper = null;
        }
        Locale locale = Locale.getDefault();
        numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setMaximumFractionDigits(1);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        initializeMapLibrary();
        setContentView(getLayoutResourceId());
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        View backButtonWrapper = findViewById(R.id.gpx_back_layout);
        if (null != backButtonWrapper) {
            ViewCompat.setOnApplyWindowInsetsListener(backButtonWrapper, new OnApplyWindowInsetsListener() {
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

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ImageButton backButton = findViewById(R.id.gpx_back_button);
        if (null != backButton) {
            backButton.setOnClickListener(v -> finish());
        }
        prefs = getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        infoView = findViewById(R.id.gpx_info);
        distanceText = findViewById(R.id.gpx_rundistance);
        setupMap(prefs);
        setupList();
    }

    @Override
    public void configureMapForRoute(final RouteDescriptor routeDescriptor) {
        if (routeDescriptor != null) {
            configureMapForRouteInternal(routeDescriptor);
            updateRouteInfoView(routeDescriptor);
        } else {
            hideRouteInfoView();
        }
    }

    private void updateRouteInfoView(RouteDescriptor routeDescriptor) {
        infoView.setVisibility(View.VISIBLE);
        final String distString = net.wigle.wigleandroid.ui.UINumberFormat.metersToString(
            prefs, numberFormat, this, routeDescriptor.getDistanceMeters(), true);
        distanceText.setText(distString);
    }

    private void hideRouteInfoView() {
        infoView.setVisibility(GONE);
        distanceText.setText("");
    }

    @Override
    public void clearCurrentRoute() {
        clearRoutePolyline();
    }

    /**
     * Subclass-specific the route polyline from the map.
     */
    protected abstract void clearRoutePolyline();

    private void setupList() {
        RecyclerView recyclerView = findViewById(R.id.gpx_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));
        if (null != dbHelper) {
            try {
                Cursor cursor = dbHelper.routeMetaIterator();
                final DateFormat itemDateFormat = android.text.format.DateFormat.getDateFormat(this.getApplicationContext());
                final DateFormat itemTimeFormat = android.text.format.DateFormat.getTimeFormat(this.getApplicationContext());
                GpxRecyclerAdapter adapter = new GpxRecyclerAdapter(this, this, cursor, this, this, this, prefs, itemDateFormat, itemTimeFormat);
                recyclerView.setAdapter(adapter);
            } catch (DBException dbex) {
                Logging.error("Failed to setup list for GPX management: ", dbex);
            }
        }
    }

    @Override
    public void handleDialog(int dialogId) {
        switch (dialogId) {
            case EXPORT_GPX_DIALOG: {
                if (!exportRouteGpxFile(exportRouteId)) {
                    Logging.warn("Failed to export gpx.");
                    //WiGLEToast.showOverFragment(this, R.string.error_general,
                    //        getString(R.string.gpx_failed));
                }
                break;
            }
            case DELETE_GPX_DIALOG: {
                if (deleteRouteId >= 0 && dbHelper != null) {
                    try {
                        dbHelper.deleteRoute(deleteRouteId);
                        clearCurrentRoute();
                        refreshRouteList();
                        displayFirstRouteIfAvailable();
                        WiGLEToast.showOverFragment(this, R.string.delete_gpx, getString(R.string.delete_gpx_success));
                    } catch (DBException e) {
                        Logging.error("Failed to delete route: ", e);
                        WiGLEToast.showOverFragment(this, R.string.error_general, getString(R.string.delete_gpx_failed));
                    } finally {
                        deleteRouteId = -1L;
                    }
                }
                break;
            }
            default:
                Logging.warn("Data unhandled dialogId: " + dialogId);
        }
    }

    private boolean exportRouteGpxFile(long runId) {
        final long totalRoutePoints = ListFragment.lameStatic.dbHelper.getRoutePointCount(runId);
        if (totalRoutePoints > 1) {
            ExecutorService es = ListFragment.lameStatic.executorService;
            if (null != es) {
                try {
                    es.submit(new GpxExportRunnable(this, true, totalRoutePoints, runId));
                } catch (IllegalArgumentException e) {
                    Logging.error("failed to submit job: ", e);
                    WiGLEToast.showOverFragment(this, R.string.export_gpx,
                            getResources().getString(R.string.duplicate_job));
                    return false;
                }
            } else {
                Logging.error("null LameStatic ExecutorService - unable to submit route export");
            }
        } else {
            Logging.error("no points to create route");
            WiGLEToast.showOverFragment(this, R.string.gpx_failed,
                    getResources().getString(R.string.gpx_no_points));
            //NO POINTS
        }
        return true;
    }

    @Override
    public void setRouteToExport(long routeId) {
        exportRouteId = routeId;
    }

    @Override
    public void setRouteToDelete(long routeId) {
        deleteRouteId = routeId;
    }

    private void refreshRouteList() {
        RecyclerView recyclerView = findViewById(R.id.gpx_list);
        GpxRecyclerAdapter adapter = (GpxRecyclerAdapter) recyclerView.getAdapter();
        if (adapter != null && dbHelper != null) {
            try {
                Cursor newCursor = dbHelper.routeMetaIterator();
                adapter.updateCursor(newCursor);
            } catch (DBException e) {
                Logging.error("Failed to refresh route list: ", e);
            }
        }
    }

    /**
     * After a route is deleted, display the first remaining route on the map and select it in the list.
     */
    private void displayFirstRouteIfAvailable() {
        RecyclerView recyclerView = findViewById(R.id.gpx_list);
        GpxRecyclerAdapter adapter = (GpxRecyclerAdapter) recyclerView.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0 || dbHelper == null) {
            return;
        }
        Cursor cursor = adapter.getCursor();
        if (cursor == null || !cursor.moveToPosition(0)) {
            return;
        }
        int runIdCol = cursor.getColumnIndexOrThrow("run_id");
        long runId = cursor.getLong(runIdCol);
        try (Cursor routeCursor = dbHelper.routeIterator(runId)) {
            if (routeCursor == null) return;
            final int mapMode = prefs.getInt(PreferenceKeys.PREF_MAP_TYPE, 1);
            final boolean nightMode = ThemeUtil.shouldUseMapNightMode(this, prefs);
            RouteDescriptor newRoute = new RouteDescriptor();
            for (routeCursor.moveToFirst(); !routeCursor.isAfterLast(); routeCursor.moveToNext()) {
                float lat = routeCursor.getFloat(0);
                float lon = routeCursor.getFloat(1);
                newRoute.addLatLng(lat, lon, mapMode, nightMode);
            }
            configureMapForRoute(newRoute);
        } catch (Exception e) {
            Logging.error("Unable to display route after delete: ", e);
        }
        adapter.setSelectedPosition(0);
    }

    @Override
    public void onDestroy() {
        Logging.info("GPX MGMT: onDestroy");
        destroyMapView();
        super.onDestroy();
        finish();
    }

    @Override
    public void onResume() {
        Logging.info("GPX MGMT: onResume");
        super.onResume();
        resumeMapView();
    }

    @Override
    public void onPause() {
        Logging.info("GPX MGMT: onPause");
        pauseMapView();
        super.onPause();
    }
}
