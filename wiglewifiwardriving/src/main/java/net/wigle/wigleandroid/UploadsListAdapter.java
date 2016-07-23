package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.wigle.wigleandroid.model.RankUser;
import net.wigle.wigleandroid.model.Upload;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * the array adapter for a list of uploads.
 */
public final class UploadsListAdapter extends AbstractListAdapter<Upload> {
    public UploadsListAdapter(final Context context, final int rowLayout ) {
        super( context, rowLayout );
    }

    @SuppressLint("SetTextI18n")
    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        View row;

        if ( null == convertView ) {
            row = mInflater.inflate( R.layout.uploadrow, parent, false );
        }
        else {
            row = convertView;
        }

        Upload upload;
        try {
            upload = getItem(position);
        }
        catch ( final IndexOutOfBoundsException ex ) {
            // yes, this happened to someone
            MainActivity.info("index out of bounds: " + position + " ex: " + ex);
            return row;
        }

        TextView tv = (TextView) row.findViewById( R.id.transid );
        tv.setText(upload.getTransid());

        tv = (TextView) row.findViewById( R.id.total_wifi_gps );
        tv.setText(getContext().getString(R.string.wifi_gps) + ": "
                + numberFormat.format(upload.getTotalWifiGps()));

        tv = (TextView) row.findViewById( R.id.total_cell_gps );
        tv.setText(getContext().getString(R.string.cell_gps) + ": "
                + numberFormat.format(upload.getTotalCellGps()));

        tv = (TextView) row.findViewById( R.id.file_size );
        tv.setText(getContext().getString(R.string.bytes) + ": "
                + numberFormat.format(upload.getFileSize()));

        final String status = upload.getStatus();
        String percentDonePrefix = "";
        String percentDoneSuffix = "%";
        if ("Queued for Processing".equals(status)) {
            percentDonePrefix = "#";
            percentDoneSuffix = "";
        }
        tv = (TextView) row.findViewById( R.id.percent_done );
        tv.setText(percentDonePrefix + upload.getPercentDone() + percentDoneSuffix);

        tv = (TextView) row.findViewById( R.id.status );
        tv.setText(upload.getStatus());

        return row;
    }
}
