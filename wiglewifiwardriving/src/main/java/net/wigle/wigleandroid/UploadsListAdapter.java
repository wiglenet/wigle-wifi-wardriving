package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.fragment.app.Fragment;
import androidx.core.content.FileProvider;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.KmlDownloader;
import net.wigle.wigleandroid.model.Upload;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * the array adapter for a list of uploads.
 */
public final class UploadsListAdapter extends AbstractListAdapter<Upload> {
    private final SharedPreferences prefs;
    private final Fragment fragment;

    public UploadsListAdapter(final Context context, final int rowLayout, final SharedPreferences prefs,
                              final Fragment fragment) {
        super(context, rowLayout);
        this.prefs = prefs;
        this.fragment = fragment;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        View row;

        if (null == convertView) {
            row = mInflater.inflate(R.layout.uploadrow, parent, false);
        } else {
            row = convertView;
        }

        Upload upload;
        try {
            upload = getItem(position);
        } catch (final IndexOutOfBoundsException ex) {
            // yes, this happened to someone
            MainActivity.info("index out of bounds: " + position + " ex: " + ex);
            return row;
        }

        final String transid = upload.getTransid();
        TextView tv = (TextView) row.findViewById(R.id.transid);
        tv.setText(upload.getTransid());

        tv = (TextView) row.findViewById(R.id.total_wifi_gps);
        tv.setText(getContext().getString(R.string.wifi_gps) + ": "
                + numberFormat.format(upload.getTotalWifiGps()));

        tv = (TextView) row.findViewById(R.id.total_bt_gps);
        tv.setText(getContext().getString(R.string.bt_gps) + ": "
                + numberFormat.format(upload.getTotalBtGps()));

        tv = (TextView) row.findViewById(R.id.total_cell_gps);
        tv.setText(getContext().getString(R.string.cell_gps) + ": "
                + numberFormat.format(upload.getTotalCellGps()));

        tv = (TextView) row.findViewById(R.id.file_size);
        tv.setText(getContext().getString(R.string.bytes) + ": "
                + numberFormat.format(upload.getFileSize()));


        final String status = upload.getStatus();
        final String userid = prefs.getString(ListFragment.PREF_AUTHNAME, "");
        final Boolean isAnonymous = prefs.getBoolean(ListFragment.PREF_BE_ANONYMOUS, false);

        if ((null != userid) && (!userid.isEmpty()) && (!isAnonymous) && ("Completed".equals(status))) {
            ImageButton share = (ImageButton) row.findViewById(R.id.share_upload);
            share.setVisibility(View.VISIBLE);
            share.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    MainActivity.info("Sharing transid: " + transid);
                    final KmlDownloader task = new KmlDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper, transid,
                            new ApiListener() {
                                @Override
                                public void requestComplete(final JSONObject json, final boolean isCache) {
                                    UploadsListAdapter.handleKmlDownload(transid, json, fragment, Intent.ACTION_SEND);
                                }
                            });
                    try {
                        task.startDownload(fragment);
                    } catch (WiGLEAuthException waex) {
                        MainActivity.warn("Authentication error on KML download for transid " +
                                transid, waex);
                    }
                }
            });
            ImageButton view = (ImageButton) row.findViewById(R.id.view_upload);
            view.setVisibility(View.VISIBLE);
            view.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    MainActivity.info("Viewing transid: " + transid);
                    final KmlDownloader task = new KmlDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper, transid,
                            new ApiListener() {
                                @Override
                                public void requestComplete(final JSONObject json, final boolean isCache) {
                                    UploadsListAdapter.handleKmlDownload(transid, json, fragment, Intent.ACTION_VIEW);
                                }
                            });
                    try {
                        task.startDownload(fragment);
                    } catch (WiGLEAuthException waex) {
                        MainActivity.warn("Authentication error on KML download for transid " +
                                transid, waex);
                    }
                }
            });
        }
        String percentDonePrefix = "";
        String percentDoneSuffix = "%";
        if ("Queued for Processing".equals(status)) {
            percentDonePrefix = "#";
            percentDoneSuffix = "";
        }
        tv = (TextView) row.findViewById(R.id.percent_done);
        tv.setText(percentDonePrefix + upload.getPercentDone() + percentDoneSuffix);

        tv = (TextView) row.findViewById(R.id.status);
        tv.setText(upload.getStatus());

        return row;
    }

    public static void handleKmlDownload(final String transid, final JSONObject json,
                                         final Fragment fragment, final String actionIntent ) {
        try {
            if (json.getBoolean("success")) {
                MainActivity.info("transid " + transid + " worked!");
                String localFilePath = json.getString("file");
                MainActivity.info("Local Path: "+localFilePath);
                Intent intent = new Intent(actionIntent);
                intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE " + transid);
                try {

                    // content:// url for the file.
                    Uri fileUri;
                    if (MainActivity.hasSD()) {
                        File file = new File(localFilePath);
                        fileUri = FileProvider.getUriForFile(fragment.getContext(),
                                MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                                        ".kmlprovider", file);
                    } else {
                        File dir = new File(fragment.getContext().getFilesDir(), "app_kml");
                        File file = new File(dir, transid+".kml");
                        if (!file.exists()) {
                            MainActivity.error("file does not exist: " + file.getAbsolutePath());
                        } else {
                            MainActivity.info(file.getAbsolutePath());
                        }
                        fileUri = FileProvider.getUriForFile(fragment.getContext(),
                                MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                                        ".kmlprovider", file);
                    }


                    // the old, but easier to debug way of getting a file:// url for a file
                    //Uri fileUri = Uri.fromFile(file);

                    if (Intent.ACTION_SEND.equals(actionIntent)) {
                        //share case, populates arguments to work with email, drive
                        MainActivity.info("send action called for file URI: " + fileUri.toString());
                        intent.setType("application/vnd.google-earth.kml+xml");
                        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                    } else if (Intent.ACTION_VIEW.equals(actionIntent)) {
                        MainActivity.info("view action called for file URI: "+fileUri.toString());
                        intent.setDataAndType(fileUri, "application/vnd.google-earth.kml+xml");
                    } else {
                        //catch-all, same as "view" for now.
                        MainActivity.info("view action called for file URI: "+fileUri.toString());
                        intent.setDataAndType(fileUri, "application/vnd.google-earth.kml+xml");
                    }
                    //TODO: necessary?
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    fragment.startActivity(Intent.createChooser(intent, fragment.getResources().getText(R.string.send_to)));
                } catch (IllegalArgumentException e) {
                    MainActivity.error("Unable to open file: " + localFilePath);
                    e.printStackTrace();
                }
            } else {
                MainActivity.error("Failed to download transid: " + transid);
            }
        } catch(JSONException jex) {
            MainActivity.error("Exception downloading transid: " + transid);
        }
    }
}
