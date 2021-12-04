package net.wigle.wigleandroid.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.GoogleMap;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.PolylineRoute;
import net.wigle.wigleandroid.util.PolyRouteConfigurable;
import net.wigle.wigleandroid.util.RouteExportSelector;

import java.text.DateFormat;

import static net.wigle.wigleandroid.util.AsyncGpxExportTask.EXPORT_GPX_DIALOG;

public class GpxRecyclerAdapter extends RecyclerView.Adapter<GpxRecyclerAdapter.ViewHolder>  {

    private final Context context;
    private final FragmentActivity fragmentActivity;
    private Cursor cursor;
    private boolean dataValid;
    private int rowIdColumn;
    private DataSetObserver dataSetObserver;
    private PolyRouteConfigurable configurable;
    private RouteExportSelector routeSelector;
    private SharedPreferences prefs;
    private DateFormat dateFormat;
    private DateFormat timeFormat;


    public GpxRecyclerAdapter(Context context, FragmentActivity fragmentActivity, Cursor cursor, PolyRouteConfigurable configurable, RouteExportSelector routeSelector,
                              SharedPreferences prefs, DateFormat dateFormat, DateFormat timeFormat) {
        this.context = context;
        this.fragmentActivity = fragmentActivity;
        this.cursor = cursor;
        this.dataValid = cursor != null;
        rowIdColumn = dataValid ? cursor.getColumnIndex("_id") : -1;
        dataSetObserver = new NotifyingDataSetObserver();
        if (cursor != null) {
            cursor.registerDataSetObserver(dataSetObserver);
        }
        this.configurable = configurable;
        this.routeSelector = routeSelector;
        this.prefs = prefs;
        this.dateFormat = dateFormat;
        this.timeFormat = timeFormat;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;
        private final ImageButton shareButton;

        public ViewHolder(View view) {
            super(view);
            textView = (TextView) view.findViewById(R.id.gpxItemLabel);
            shareButton = (ImageButton) view.findViewById(R.id.share_route);
        }

        public TextView getTextView() {
            return textView;
        }
        public ImageButton getShareButton() {
            return shareButton;
        }
    }

    public Cursor getCursor() {
        return cursor;
    }

    public void updateCursor(Cursor cursor) {
        Cursor old = swapCursor(cursor);
        if (old != null) {
            old.close();
        }
    }

    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor == cursor) {
            return null;
        }
        final Cursor oldCursor = cursor;
        if (oldCursor != null && dataSetObserver != null) {
            oldCursor.unregisterDataSetObserver(dataSetObserver);
        }
        cursor = newCursor;
        if (cursor != null) {
            if (dataSetObserver != null) {
                cursor.registerDataSetObserver(dataSetObserver);
            }
            rowIdColumn = newCursor.getColumnIndexOrThrow("_id");
            dataValid = true;
            notifyDataSetChanged();
        } else {
            rowIdColumn = -1;
            dataValid = false;
            notifyDataSetChanged();
        }
        return oldCursor;
    }

    private class NotifyingDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            super.onChanged();
            dataValid = true;
            notifyDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
            dataValid = false;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public GpxRecyclerAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.gpx_recycler_element, parent, false);
        ViewHolder handler = new ViewHolder(itemView);
        return handler;
    }

    @Override
    public void onBindViewHolder(@NonNull GpxRecyclerAdapter.ViewHolder holder, int position) {
        if (!dataValid) {
            throw new IllegalStateException("Invalid cursor / dataValid flag onBindViewHolder");
        }
        if (!cursor.moveToPosition(position)) {
            throw new IllegalStateException("Cursor couldn't move to position position " + position);
        }
        GpxListItem listItem = GpxListItem.fromCursor(cursor, dateFormat, timeFormat);
        final long clickedId = listItem.getRunId();
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                configurable.clearCurrentRoute();
                try {
                    //DEBUG: MainActivity.info("get route "+clickedId);
                    Cursor routeCursor = ListFragment.lameStatic.dbHelper.routeIterator(clickedId);
                    final int mapMode = prefs.getInt(ListFragment.PREF_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL);
                    if (null == routeCursor) {
                        MainActivity.info("null route cursor; not mapping");
                    } else {
                        PolylineRoute newRoute = new PolylineRoute();
                        for (routeCursor.moveToFirst(); !routeCursor.isAfterLast(); routeCursor.moveToNext()) {
                            final float lat = routeCursor.getFloat(0);
                            final float lon = routeCursor.getFloat(1);
                            //final long time = routeCursor.getLong(2);
                            newRoute.addLatLng(lat, lon, mapMode);
                        }
                        MainActivity.info("Loaded route with " + newRoute.getSegments() + " segments");
                        configurable.configureMapForRoute(newRoute);
                    }
                } catch (Exception e) {
                    MainActivity.error("Unable to add route: ",e);
                }
            }
        });
        holder.shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.info("share route "+clickedId);
                routeSelector.setRouteToExport(clickedId);
                if (null != context) {
                    MainActivity.createConfirmation(fragmentActivity,
                            context.getString(R.string.export_gpx_detail), R.id.nav_data, EXPORT_GPX_DIALOG);
                } else {
                    MainActivity.error("unable to get fragment activity");
                }

            }
        });
        holder.textView.setText(listItem.getName());
    }

    @Override
    public long getItemId(int position) {
        if (dataValid && cursor != null && cursor.moveToPosition(position)) {
            return cursor.getLong(rowIdColumn);
        }
        return 0;
    }

    @Override
    public void setHasStableIds(boolean hasStableIds) {
        super.setHasStableIds(true);
    }

    @Override
    public int getItemCount() {
        if (dataValid && cursor != null) {
            return cursor.getCount();
        }
        return 0;
    }
}
