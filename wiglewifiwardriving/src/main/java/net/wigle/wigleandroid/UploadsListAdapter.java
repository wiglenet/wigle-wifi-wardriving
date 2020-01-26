package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.content.FileProvider;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.CsvDownloader;
import net.wigle.wigleandroid.background.KmlDownloader;
import net.wigle.wigleandroid.model.Upload;
import net.wigle.wigleandroid.util.FileUtility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import static net.wigle.wigleandroid.util.FileUtility.WIWI_PREFIX;

/**
 * the array adapter for a list of uploads.
 */
public final class UploadsListAdapter extends AbstractListAdapter<Upload> {
    private final SharedPreferences prefs;
    private final Fragment fragment;
    private final Context context;

    public UploadsListAdapter(final Context context, final int rowLayout, final SharedPreferences prefs,
                              final Fragment fragment) {
        super(context, rowLayout);
        this.prefs = prefs;
        this.fragment = fragment;
        this.context = context;
    }

    @SuppressLint("SetTextI18n")
    @Override
    @NonNull
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

        if (null != upload) {
            TextView tv = row.findViewById(R.id.transid);
            final String transId = upload.getTransid();


            tv.setText(transId);

            tv = row.findViewById(R.id.total_wifi_gps);
            tv.setText(numberFormat.format(upload.getTotalWifiGps()));

            tv = row.findViewById(R.id.total_bt_gps);
            tv.setText(numberFormat.format(upload.getTotalBtGps()));

            tv = row.findViewById(R.id.total_cell_gps);
            tv.setText(numberFormat.format(upload.getTotalCellGps()));

            tv = row.findViewById(R.id.file_size);
            tv.setText(getContext().getString(R.string.bytes) + ": "
                    + numberFormat.format(upload.getFileSize()));

            //tv = row.findViewById(R.id.upload_status);
            //tv.setText(upload.getFileName());

            //tv = row.findViewById(R.id.local_status);
            //MainActivity.error("file name: "+ upload.getFileName());
            ImageButton ib = row.findViewById(R.id.csv_status_button);
            tv = row.findViewById(R.id.upload_status);
            String wholeName = upload.getFileName();
            if (wholeName.contains("_")) {
                wholeName = wholeName.substring(wholeName.indexOf("_")+1);
            }
            String message = getContext().getString(R.string.csv);
            /*try {
                final File f = FileUtility.getCsvGzFile(context, wholeName+FileUtility.GZ_EXT);
                if (f.exists()) {
                    message += getContext().getString(R.string.uploaded) + getContext().getString(R.string.click_access);
                    ib.setImageResource(R.drawable.ic_ulstatus_uled);
                } else {
                    message += getContext().getString(R.string.uploaded) + getContext().getString(R.string.click_download);
                    ib.setImageResource(R.drawable.ic_ulstatus_nolocal);
                }
            } catch (NullPointerException e) {
                message += getContext().getString(R.string.uploaded) + getContext().getString(R.string.click_download);
                ib.setImageResource(R.drawable.ic_ulstatus_nolocal);
            }*/
            if (upload.getDownloadedToLocal()) {
                message += getContext().getString(R.string.downloaded) + getContext().getString(R.string.click_access);
                final String fileName = transId + FileUtility.CSV_GZ_EXT;
                ib.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        handleCsvShare(transId, fileName, fragment);
                    }
                });
                ib.setImageResource(R.drawable.ic_ulstatus_dl);
            } else if (upload.getUploadedFromLocal()) {
                message += getContext().getString(R.string.uploaded) + getContext().getString(R.string.click_access);
                ib.setImageResource(R.drawable.ic_ulstatus_uled);
                //final String fileName = WIWI_PREFIX+transId + FileUtility.CSV_GZ_EXT;
                final String fName = upload.getFileName();
                if (fName.contains("_")) {
                    final String fileName = fName.substring(fName.indexOf("_")+1) + FileUtility.GZ_EXT;
                    ib.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            handleCsvShare(transId, fileName, fragment);
                        }
                    });
                } else {
                    MainActivity.error("non-importable file: "+fName);
                }
            } else {
                final View rowView = row;
                message += getContext().getString(R.string.uploaded) + getContext().getString(R.string.click_download);
                ib.setImageResource(R.drawable.ic_ulstatus_nolocal);
                ib.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        MainActivity.info("Downloading transid CSV: " + transId);
                        final CsvDownloader task = new CsvDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper, transId,
                                new ApiListener() {
                                    @Override
                                    public void requestComplete(final JSONObject json, final boolean isCache) {
                                        UploadsListAdapter.handleCsvDownload(transId, json, fragment, rowView);
                                    }
                                });
                        try {
                            task.startDownload(fragment);
                        } catch (WiGLEAuthException waex) {
                            MainActivity.warn("Authentication error on CSV download for transid " +
                                    transId, waex);
                        }
                    }
                });
            }
            //TODO: un-uploaded files for retry

            tv.setText(message);

            final String status = upload.getStatus();
            final String userId = prefs.getString(ListFragment.PREF_AUTHNAME, "");
            final boolean isAnonymous = prefs.getBoolean(ListFragment.PREF_BE_ANONYMOUS, false);

            if (!userId.isEmpty() && (!isAnonymous) && ("Completed".equals(status))) {
                ImageButton share = row.findViewById(R.id.share_upload);
                share.setVisibility(View.VISIBLE);
                share.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        MainActivity.info("Sharing transid: " + transId);
                        final KmlDownloader task = new KmlDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper, transId,
                                new ApiListener() {
                                    @Override
                                    public void requestComplete(final JSONObject json, final boolean isCache) {
                                        UploadsListAdapter.handleKmlDownload(transId, json, fragment, Intent.ACTION_SEND);
                                    }
                                });
                        try {
                            task.startDownload(fragment);
                        } catch (WiGLEAuthException waex) {
                            MainActivity.warn("Authentication error on KML download for transid " +
                                    transId, waex);
                        }
                    }
                });
                ImageButton view = row.findViewById(R.id.view_upload);
                view.setVisibility(View.VISIBLE);
                view.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        MainActivity.info("Viewing transid: " + transId);
                        final KmlDownloader task = new KmlDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper, transId,
                                new ApiListener() {
                                    @Override
                                    public void requestComplete(final JSONObject json, final boolean isCache) {
                                        UploadsListAdapter.handleKmlDownload(transId, json, fragment, Intent.ACTION_VIEW);
                                    }
                                });
                        try {
                            task.startDownload(fragment);
                        } catch (WiGLEAuthException waex) {
                            MainActivity.warn("Authentication error on KML download for transid " +
                                    transId, waex);
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
            tv = row.findViewById(R.id.percent_done);
            tv.setText(percentDonePrefix + upload.getPercentDone() + percentDoneSuffix);

            tv = row.findViewById(R.id.status);
            tv.setText(upload.getStatus());
        }
        return row;
    }

    private static void handleKmlDownload(final String transId, final JSONObject json,
                                         final Fragment fragment, final String actionIntent ) {
        try {
            if (json.getBoolean("success")) {
                MainActivity.info("transid " + transId + " worked!");
                String localFilePath = json.getString("file");
                MainActivity.info("Local Path: "+localFilePath);
                Intent intent = new Intent(actionIntent);
                intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE " + transId);
                try {
                    Context c = fragment.getContext();
                    if (null != c) {
                        // content:// url for the file.
                        File file = FileUtility.getKmlDownloadFile(c, transId, localFilePath);
                        if (file != null) {
                            Uri fileUri = FileProvider.getUriForFile(fragment.getContext(),
                                    MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                                            ".kmlprovider", file);
                            // the old, but easier to debug way of getting a file:// url for a file
                            //Uri fileUri = Uri.fromFile(file);

                            if (Intent.ACTION_SEND.equals(actionIntent)) {
                                //share case, populates arguments to work with email, drive
                                MainActivity.info("send action called for file URI: " + fileUri.toString());
                                intent.setType("application/vnd.google-earth.kml+xml");
                                intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                            } else if (Intent.ACTION_VIEW.equals(actionIntent)) {
                                MainActivity.info("view action called for file URI: " + fileUri.toString());
                                intent.setDataAndType(fileUri, "application/vnd.google-earth.kml+xml");
                            } else {
                                //catch-all, same as "view" for now.
                                MainActivity.info("view action called for file URI: " + fileUri.toString());
                                intent.setDataAndType(fileUri, "application/vnd.google-earth.kml+xml");
                            }
                            //TODO: necessary (1/2)?
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            fragment.startActivity(Intent.createChooser(intent, fragment.getResources().getText(R.string.send_to)));
                        } else {
                            MainActivity.error("Unable to get context for file interaction in handleKmlDownload.");
                        }
                    } else {
                        MainActivity.error("Failed to determine filename for transid: " + transId);
                    }
                } catch (IllegalStateException ise) {
                    MainActivity.error("had completed KML DL, but user had disassociated activity.");
                } catch (IllegalArgumentException e) {
                    MainActivity.error("Unable to open file: " + localFilePath);
                    e.printStackTrace();
                }
            } else {
                MainActivity.error("Failed to download transid: " + transId);
            }
        } catch(JSONException jex) {
            MainActivity.error("Exception downloading transid: " + transId);
        }
    }

    private static void handleCsvDownload(final String transId, final JSONObject json,
                                          final Fragment fragment, final View row) {
        try {
            if (json.getBoolean("success")) {
                MainActivity.info("transid " + transId + " worked!");
                String localFilePath = json.getString("file");
                MainActivity.info("Local Path: " + localFilePath);

                //TODO UI update
                fragment.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        row.invalidate();
                    }
                });
            }
        } catch (JSONException jex) {
            MainActivity.error("Exception downloading transid CSV: " + transId);
        } catch (Exception e) {
            MainActivity.error("error updating item: ", e);
        }
    }
    private static void handleCsvShare(final String transId, final String fileName,
                                          final Fragment fragment) {
        MainActivity.info("Initiating share for "+fileName+" ("+transId+")");
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE " + transId);
        Context c = fragment.getContext();
        File csvFile = FileUtility.getCsvGzFile(c, fileName);
        if (csvFile != null && csvFile.exists()) {
            Uri fileUri = FileProvider.getUriForFile(fragment.getContext(),
                    MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                            ".csvgzprovider", csvFile);

            MainActivity.info("send action called for file URI: " + fileUri.toString());
            intent.setType("application/gzip");
            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            //TODO: necessary (2/2)?
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            fragment.startActivity(Intent.createChooser(intent, fragment.getResources().getText(R.string.send_to)));
        } else {
            MainActivity.error("Unable to get context for file interaction in handleCsvShare.");
        }
    }

}
