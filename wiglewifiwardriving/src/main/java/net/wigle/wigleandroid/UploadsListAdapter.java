package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.widget.TextView;

import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.CsvDownloader;
import net.wigle.wigleandroid.background.KmlDownloader;
import net.wigle.wigleandroid.model.Upload;
import net.wigle.wigleandroid.util.FileUtility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import static android.view.View.GONE;
import static net.wigle.wigleandroid.UploadsFragment.disableListButtons;

/*
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
        UploadsFragment.disableListButtons = false;
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

        final Upload upload;
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
            tv.setText(context.getString(R.string.bytes) + ": "
                    + numberFormat.format(upload.getFileSize()));

            final String status = upload.getStatus();
            final String userId = prefs.getString(ListFragment.PREF_AUTHNAME, "");
            final boolean isAnonymous = prefs.getBoolean(ListFragment.PREF_BE_ANONYMOUS, false);

            final ImageButton ib = row.findViewById(R.id.csv_status_button);
            final TextView statusTv = row.findViewById(R.id.upload_status);
            String message = context.getString(R.string.csv);
            if (!userId.isEmpty() && (!isAnonymous)) {
                if (upload.getDownloadedToLocal()) {
                    message += context.getString(R.string.downloaded) + context.getString(R.string.click_access);
                    final String fileName = transId + FileUtility.CSV_GZ_EXT;
                    ib.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            handleCsvShare(transId, fileName, fragment);
                        }
                    });
                    ib.setImageResource(R.drawable.ic_ulstatus_dl);
                } else if (upload.getUploadedFromLocal()) {
                    final String fName = upload.getFileName();
                    final String fileName = fName.substring(fName.indexOf("_") + 1) + FileUtility.GZ_EXT;
                    if ("Completed".equals(status)) {
                        message += context.getString(R.string.uploaded) + context.getString(R.string.click_access);
                        ib.setImageResource(R.drawable.ic_ulstatus_uled);
                        if (fName.contains("_")) {
                            ib.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) {
                                    handleCsvShare(transId, fileName, fragment);
                                }
                            });
                        } else {
                            MainActivity.error("non-importable file: " + fName);
                        }
                    } else {
                        message += context.getString(R.string.not_proc) + context.getString(R.string.click_access);
                        ib.setImageResource(R.drawable.ic_ulstatus_queued);
                        ib.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                handleCsvShare(transId, fileName, fragment);
                            }
                        });
                    }
                } else {
                    if ("Completed".equals(status)) {
                        message += context.getString(R.string.uploaded) + context.getString(R.string.click_download);
                        ib.setImageResource(R.drawable.ic_ulstatus_nolocal);
                        ib.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                //disable buttons
                                disableListButtons = true;
                                notifyDataSetChanged();
                                MainActivity.info("Downloading transId CSV: " + transId);
                                final CsvDownloader task = new CsvDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper, transId,
                                        new ApiListener() {
                                            @Override
                                            public void requestComplete(final JSONObject json, final boolean isCache) {
                                                UploadsListAdapter.handleCsvDownload(transId, json);
                                                upload.setDownloadedToLocal(true);
                                                disableListButtons = false;
                                                Activity activity = fragment.getActivity();
                                                if (null != activity) {
                                                    activity.runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            //re-enable buttons
                                                            notifyDataSetChanged();
                                                        }
                                                    });
                                                }
                                            }
                                        });
                                try {
                                    task.startDownload(fragment);
                                } catch (Exception waex) {
                                    MainActivity.warn("Error on CSV download for transId " +
                                            transId, waex);
                                    disableListButtons = false;
                                    Activity activity = fragment.getActivity();
                                    if (null != activity) {
                                        activity.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                //re-enable buttons
                                                notifyDataSetChanged();
                                            }
                                        });
                                    }
                                }
                            }
                        });
                    } else {
                        message += context.getString(R.string.not_proc);
                        ib.setImageResource(R.drawable.ic_ulstatus_queuenotlocal);
                        ib.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                MainActivity.error("not available yet - nothing to do for " + transId);
                            }
                        });
                    }
                }
                if (disableListButtons) {
                    ib.setEnabled(false);
                } else {
                    ib.setEnabled(true);
                }
            } else {
                MainActivity.error("no user set - no download controls to offer.");
                ib.setVisibility(GONE);
                ib.setEnabled(false);
                statusTv.setVisibility(GONE);
            }

            //TODO: un-uploaded files for retry

            statusTv.setText(message);

            ImageButton share = row.findViewById(R.id.share_upload);
            ImageButton view = row.findViewById(R.id.view_upload);
            if (!userId.isEmpty() && (!isAnonymous) && ("Completed".equals(status))) {
                share.setVisibility(View.VISIBLE);
                share.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        MainActivity.info("Sharing transId: " + transId);
                        //disable buttons
                        disableListButtons = true;
                        notifyDataSetChanged();
                        final KmlDownloader task = new KmlDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper, transId,
                                new ApiListener() {
                                    @Override
                                    public void requestComplete(final JSONObject json, final boolean isCache) {
                                        UploadsListAdapter.handleKmlDownload(transId, json, fragment, Intent.ACTION_SEND);
                                        disableListButtons = false;
                                        Activity activity = fragment.getActivity();
                                        if (null != activity) {
                                            activity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    //re-enable buttons
                                                    notifyDataSetChanged();
                                                }
                                            });
                                        }
                                    }
                                });
                        try {
                            task.startDownload(fragment);
                        } catch (Exception e) {
                            MainActivity.warn("Error on KML download for transId " +
                                    transId, e);
                            disableListButtons = false;
                            Activity activity = fragment.getActivity();
                            if (null != activity) {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        //re-enable buttons
                                        notifyDataSetChanged();
                                    }
                                });
                            }
                        }
                    }
                });
                if (disableListButtons) {
                    share.setEnabled(false);
                } else {
                    share.setEnabled(true);
                }


                view.setVisibility(View.VISIBLE);
                view.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        MainActivity.info("Viewing transId: " + transId);
                        //disable buttons
                        disableListButtons = true;
                        notifyDataSetChanged();
                        final KmlDownloader task = new KmlDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper, transId,
                                new ApiListener() {
                                    @Override
                                    public void requestComplete(final JSONObject json, final boolean isCache) {
                                        UploadsListAdapter.handleKmlDownload(transId, json, fragment, Intent.ACTION_VIEW);
                                        disableListButtons = false;
                                        Activity activity = fragment.getActivity();
                                        if (null != activity) {
                                            activity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    //re-enable buttons
                                                    notifyDataSetChanged();
                                                }
                                            });
                                        }
                                    }
                                });
                        try {
                            task.startDownload(fragment);
                        } catch (Exception e) {
                            MainActivity.warn("Authentication error on KML download for transId " +
                                    transId, e);
                            disableListButtons = false;
                            Activity activity = fragment.getActivity();
                            if (null != activity) {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        //re-enable buttons
                                        notifyDataSetChanged();
                                    }
                                });
                            }
                        }
                    }
                });
                if (disableListButtons) {
                    view.setEnabled(false);
                } else {
                    view.setEnabled(true);
                }
            } else {
                share.setVisibility(View.INVISIBLE);
                share.setEnabled(false);
                view.setVisibility(View.INVISIBLE);
                view.setEnabled(false);
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
                //DEBUG: MainActivity.info("transId " + transId + " worked!");
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
                        MainActivity.error("Failed to determine filename for transId: " + transId);
                    }
                } catch (IllegalStateException ise) {
                    MainActivity.error("had completed KML DL, but user had disassociated activity.");
                } catch (IllegalArgumentException e) {
                    MainActivity.error("Unable to open file: " + localFilePath, e);
                }
            } else {
                MainActivity.error("Failed to download transId: " + transId);
            }
        } catch(JSONException jex) {
            MainActivity.error("Exception downloading transId: " + transId);
        }
    }

    private static void handleCsvDownload(final String transId, final JSONObject json) {
        try {
            if (json.getBoolean("success")) {
                //DEBUG: MainActivity.info("transId " + transId + " worked!");
                String localFilePath = json.getString("file");
                MainActivity.info("Local Path: " + localFilePath);
            }
        } catch (JSONException jex) {
            MainActivity.error("Exception downloading transId CSV: " + transId);
        } catch (Exception e) {
            MainActivity.error("error updating item: ", e);
        }
    }

    private static void handleCsvShare(final String transId, final String fileName,
                                          final Fragment fragment) {
        MainActivity.info("Initiating share for CSV "+fileName+" ("+transId+")");
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE " + transId);
        Context c = fragment.getContext();
        File csvFile = FileUtility.getCsvGzFile(c, fileName);
        if (csvFile != null && csvFile.exists()) {
            final Context context = fragment.getContext();
            if (null != context) {
                Uri fileUri = FileProvider.getUriForFile(context,
                        MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                                ".csvgzprovider", csvFile);

                MainActivity.info("send action called for file URI: " + fileUri.toString());
                intent.setType("application/gzip");
                intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                //TODO: necessary (2/2)?
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                fragment.startActivity(Intent.createChooser(intent, fragment.getResources().getText(R.string.send_to)));
            }
        } else {
            MainActivity.error("Unable to get context for file interaction in handleCsvShare.");
        }
    }

}
