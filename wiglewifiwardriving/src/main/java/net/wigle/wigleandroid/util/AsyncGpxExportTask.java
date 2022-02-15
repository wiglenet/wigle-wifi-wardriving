package net.wigle.wigleandroid.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import androidx.core.content.FileProvider;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.db.DBException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static net.wigle.wigleandroid.util.FileUtility.GPX_EXT;

public class AsyncGpxExportTask extends AsyncTask<Long, Integer, String> {
    public static final int EXPORT_GPX_DIALOG = 130;

    private final static String GPX_HEADER_A = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?><gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"";
    private final static String GPX_HEADER_B ="\" version=\"1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"  xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\"><trk>\n";
    private final static String GPX_FOOTER = "</trkseg></trk></gpx>";

    File gpxDestFile;
    private final Context context;
    private final Activity activity;
    private ProgressDialog pd;
    private long routeId = -1;

    public AsyncGpxExportTask(final Context context, final Activity activity, final ProgressDialog pd) {
        this.context = context;
        this.activity = activity;
        this.pd = pd;
    }

    public AsyncGpxExportTask(final Context context, final Activity activity, final ProgressDialog pd, final long routeId) {
        this.context = context;
        this.activity = activity;
        this.pd = pd;
        this.routeId = routeId;
    }
    @Override
    protected String doInBackground(Long... routeLocs) {
        // TODO: AS ABOVE there's a real case for refusing to export on devices that don't have SD...
        // TODO: android R FS changes
        final boolean hasSD = FileUtility.hasSD();

        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        final String name = df.format(new Date());
        final String nameStr = "<name>" + name + "</name><trkseg>\n";

        try {
            if ( hasSD ) {
                final String basePath = FileUtility.getGpxPath();
                if (null == basePath) {
                    MainActivity.error("Unable to determine external GPX path");
                    return null;
                }
                final File path = new File( basePath );
                //noinspection ResultOfMethodCallIgnored
                path.mkdirs();
                if (!path.exists()) {
                    MainActivity.info("Got '!exists': " + path);
                }
                String openString = basePath + name +  GPX_EXT;
                //DEBUG: MainActivity.info("Opening file: " + openString);
                gpxDestFile = new File( openString );

            } else {
                if (null != activity) {
                    gpxDestFile = new File(activity.getApplication().getFilesDir(),
                            name + GPX_EXT);
                } else {
                    MainActivity.error("set destination file due to null Activity in GPX export");
                }
            }
            FileWriter writer = new FileWriter(gpxDestFile, false);
            writer.append(GPX_HEADER_A);
            String creator = "WiGLE WiFi ";
            try {
                if (null != activity) {
                    final PackageManager pm = activity.getApplicationContext().getPackageManager();
                    final PackageInfo pi = pm.getPackageInfo(activity.getApplicationContext().getPackageName(), 0);
                    creator += pi.versionName;
                } else {
                    MainActivity.error("unable to get packageManager due to null Activity in GPX export");
                }
            } catch (Exception ex) {
                creator += "(unknown)";
            }
            writer.append(creator);
            writer.append(GPX_HEADER_B);
            writer.append(nameStr);

            Cursor cursor;
            if (routeId == -1) {
                cursor = ListFragment.lameStatic.dbHelper.currentRouteIterator();
            } else {
                cursor = ListFragment.lameStatic.dbHelper.routeIterator(routeId);
            }
            long segmentCount = writeSegmentsWithCursor(writer, cursor, df, routeLocs[0]);
            MainActivity.info("wrote "+segmentCount+" segments");
            writer.append(GPX_FOOTER);
            writer.flush();
            writer.close();
            return "completed export";
        } catch (IOException | DBException | InterruptedException e) {
            MainActivity.error("Error writing GPX", e);
        }
        return null;
    }

    protected long writeSegmentsWithCursor(final FileWriter writer, final Cursor cursor, final DateFormat dateFormat, final Long totalCount) throws IOException, InterruptedException {
        long lineCount = 0;

        for ( cursor.moveToFirst(); ! cursor.isAfterLast(); cursor.moveToNext() ) {
            MainActivity.info("export: "+lineCount + " / "+totalCount);
            //if (wasInterrupted()) {
            //    throw new InterruptedException("GPX export interrupted");
            //}
            final double lat = cursor.getDouble(0);
            final double lon = cursor.getDouble(1);
            final long time = cursor.getLong(2);

            writer.append("<trkpt lat=\"").append(String.valueOf(lat)).append("\" lon=\"")
                    .append(String.valueOf(lon)).append("\"><time>").append(
                    dateFormat.format(new Date(time))).append("</time></trkpt>\n");
            lineCount++;
            if (totalCount == 0) {
                return totalCount;
            }
            if (lineCount == 0) {
                onProgressUpdate( 0 );
            } else {
                final int percentDone = (int) (((lineCount) * 1000) / totalCount);
                publishProgress(percentDone);
            }
        }
        return lineCount;
    }

    @Override
    protected void onPostExecute(String result) {
        if (null != result) { //launch task will exist with bg thread enqueued with null return
            MainActivity.error("GPX POST EXECUTE: " + result);
            if (pd.isShowing()) {
                pd.dismiss();
            }
            // fire share intent?
            Intent intent = new Intent(Intent.ACTION_SEND);
            final String fileName = (gpxDestFile != null && !gpxDestFile.getName().isEmpty()) ? gpxDestFile.getName() : "WiGLE.gpx";
            intent.putExtra(Intent.EXTRA_SUBJECT, fileName);
            intent.setType("application/gpx");

            //TODO: verify local-only storage case/gpx_paths.xml
            if (null != context) {
                final Uri fileUri = FileProvider.getUriForFile(context,
                        MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                                ".gpxprovider", gpxDestFile);

                intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(intent, activity.getResources().getText(R.string.send_to)));
            } else {
                MainActivity.error("Unable to initiate GPX export - null context");
            }
        }
    }

    @Override
    protected void onPreExecute() {
        pd = new ProgressDialog(context);
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setCancelable(false);
        pd.setMessage(context.getString(R.string.gpx_preparing));
        pd.setIndeterminate(true);
        pd.show();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (values.length == 1) {
            MainActivity.info("progress: "+values[0]);
            if (values[0] > 0) {
                if (100 == values[0]) {
                    if (pd.isShowing()) {
                        pd.dismiss();
                    }
                    return;
                }
                pd.setMessage(context.getString(R.string.gpx_exporting));
                pd.setProgress(values[0]);
            } else {
                pd.setIndeterminate(false);
                pd.setMessage(context.getString(R.string.gpx_preparing));
                pd.setProgress(values[0]);
            }
        } else {
            MainActivity.warn("too many values for GPX progress update");
        }
    }
}
