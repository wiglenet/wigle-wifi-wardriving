package net.wigle.wigleandroid;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.RelativeLayout;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;

import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.PolylineRoute;
import net.wigle.wigleandroid.ui.GpxRecyclerAdapter;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.AsyncGpxExportTask;
import net.wigle.wigleandroid.util.PolyRouteConfigurable;
import net.wigle.wigleandroid.util.RouteExportSelector;

import java.text.DateFormat;

import static net.wigle.wigleandroid.util.AsyncGpxExportTask.EXPORT_GPX_DIALOG;

public class GpxManagementActivity extends AppCompatActivity implements PolyRouteConfigurable, RouteExportSelector, DialogListener {

    private int DEFAULT_MAP_PADDING = 25;
    private GpxRecyclerAdapter adapter;
    final DatabaseHelper dbHelper;
    private MapView mapView;
    private Polyline routePolyline;
    private final String CURRENT_ROUTE_LINE_TAG = "currentRoutePolyline";
    private SharedPreferences prefs;
    private ProgressDialog pd;
    private long exportRouteId = -1L;

    public GpxManagementActivity() {
        this.dbHelper = MainActivity.getStaticState().dbHelper;
    }

    @Override
    public void onCreate( final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpx_mgmt);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        setupMap(null, prefs);
        setupList();
    }

    @Override
    public void onDestroy() {
        MainActivity.info("NET: onDestroy");
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
        //setResult(Result.OK);
        finish();
    }

    @Override
    public void onResume() {
        MainActivity.info("NET: onResume");
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        } else {
            final SharedPreferences prefs = getSharedPreferences(ListFragment.SHARED_PREFS, 0);
            setupMap( null, prefs);
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

    private void setupMap(final Bundle savedInstanceState, final SharedPreferences prefs ) {
        mapView = new MapView( this );
        try {
            mapView.onCreate(savedInstanceState);
        }
        catch (NullPointerException ex) {
            MainActivity.error("npe in mapView.onCreate: " + ex, ex);
        }
        MapsInitializer.initialize( this );
        final RelativeLayout rlView = findViewById( R.id.gpx_map_rl );
        rlView.addView( mapView );
    }

    @Override
    public void configureMapForRoute(final PolylineRoute polyRoute) {
        if ((polyRoute != null)) {
            mapView.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(final GoogleMap googleMap) {
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    builder.include(polyRoute.getNEExtent());
                    builder.include(polyRoute.getSWExtent());
                    LatLngBounds bounds = builder.build();
                    final CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, DEFAULT_MAP_PADDING);
                    googleMap.animateCamera(cu);
                    routePolyline = googleMap.addPolyline(polyRoute.getPolyline());
                    routePolyline.setTag(CURRENT_ROUTE_LINE_TAG);
                }
            });
        }
    }

    @Override
    public void clearCurrentRoute() {
        if (routePolyline != null ) {
            routePolyline.remove();
        }
    }

    private void setupList() {
        RecyclerView recyclerView = findViewById(R.id.gpx_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));
        try {
            Cursor cursor = dbHelper.routeMetaIterator();
            final DateFormat itemDateFormat = android.text.format.DateFormat.getDateFormat(this.getApplicationContext());
            final DateFormat itemTimeFormat = android.text.format.DateFormat.getTimeFormat(this.getApplicationContext());
            adapter = new GpxRecyclerAdapter(this, this, cursor, this, this, prefs, itemDateFormat, itemTimeFormat);
            recyclerView.setAdapter(adapter);
        } catch (DBException dbex) {
            MainActivity.error("Failed to setup list for GPX management: ", dbex);
        }
    }

    @Override
    public void handleDialog(int dialogId) {
        switch (dialogId) {
            case EXPORT_GPX_DIALOG: {
                if (!exportRouteGpxFile(exportRouteId)) {
                    MainActivity.warn("Failed to export gpx.");
                    WiGLEToast.showOverFragment(this, R.string.error_general,
                            getString(R.string.gpx_failed));
                }
                break;
            }
            default:
                MainActivity.warn("Data unhandled dialogId: " + dialogId);
        }
    }

    private boolean exportRouteGpxFile(long runId) {
        final long totalRoutePoints = ListFragment.lameStatic.dbHelper.getRoutePointCount(runId);
        if (totalRoutePoints > 1) {
            new AsyncGpxExportTask(this, this,
                    pd, runId).execute(totalRoutePoints);
        } else {
            MainActivity.error("no points to create route");
            WiGLEToast.showOverFragment(this, R.string.gpx_failed,
                    getString(R.string.gpx_no_points));
            //NO POINTS
        }
        return true;
    }

    @Override
    public void setRouteToExport(long routeId) {
        exportRouteId = routeId;
    }
}