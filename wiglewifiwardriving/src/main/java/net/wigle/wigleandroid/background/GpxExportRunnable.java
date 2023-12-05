package net.wigle.wigleandroid.background;

import static net.wigle.wigleandroid.util.FileUtility.GPX_EXT;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.view.WindowManager;

import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.Logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * GPX route export - writes files and launches share intent.
 */
public class GpxExportRunnable extends ProgressPanelRunnable implements Runnable, AlertSettable {

    public static final int EXPORT_GPX_DIALOG = 130;

    final static DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);

    private final static String GPX_HEADER_A = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?><gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"";
    private final static String GPX_HEADER_B ="\" version=\"1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"  xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\"><trk>\n";
    private final static String GPX_FOOTER = "</trkseg></trk></gpx>";
    File gpxDestFile;
    private long routeId = -1;
    private final long totalCount;

    public GpxExportRunnable(final FragmentActivity activity, final UniqueTaskExecutorService executorService, final boolean showProgress, final long totalPoints) {
        super(activity, executorService, showProgress);
        this.totalCount = totalPoints;
    }
    public GpxExportRunnable(final FragmentActivity activity, final boolean showProgress, final long totalPoints, final long routeId) {
        super(activity, null, showProgress);
        this.totalCount = totalPoints;
        this.routeId = routeId;
    }

    @Override
    public void run() {
        // TODO: android R FS changes
        onPreExecute();
        final boolean hasSD = FileUtility.hasSD();
        final String name = df.format(new Date());
        final String nameStr = "<name>" + name + "</name><trkseg>\n";
        try {
            if ( hasSD ) {
                final String basePath = FileUtility.getGpxPath();
                if (null == basePath) {
                    Logging.error("Unable to determine external GPX path");
                    return;
                }
                final File path = new File( basePath );
                //noinspection ResultOfMethodCallIgnored
                path.mkdirs();
                if (!path.exists()) {
                    Logging.info("Got '!exists': " + path);
                }
                String openString = basePath + name +  GPX_EXT;
                //DEBUG: MainActivity.info("Opening file: " + openString);
                gpxDestFile = new File( openString );

            } else {
                if (null != activity) {
                    gpxDestFile = new File(activity.getApplication().getFilesDir(),
                            name + GPX_EXT);
                } else {
                    Logging.error("set destination file due to null Activity in GPX export");
                }
            }
            try (FileWriter writer = new FileWriter(gpxDestFile, false)) {
                writer.append(GPX_HEADER_A);
                String creator = "WiGLE WiFi ";
                try {
                    if (null != activity) {
                        final PackageManager pm = activity.getApplicationContext().getPackageManager();
                        final PackageInfo pi = pm.getPackageInfo(activity.getApplicationContext().getPackageName(), 0);
                        creator += pi.versionName;
                    } else {
                        Logging.error("unable to get packageManager due to null Activity in GPX export");
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
                long segmentCount = writeSegmentsWithCursor(writer, cursor, df, totalCount);
                Logging.info("wrote " + segmentCount + " segments");
                writer.append(GPX_FOOTER);
                writer.flush();
                writer.close();
                cursor.close();
                return;
            }
        } catch (IOException | DBException | InterruptedException e) {
            Logging.error("Error writing GPX", e);
        } finally {
            onPostExecute("completed");
        }
    }

    protected long writeSegmentsWithCursor(final FileWriter writer, final Cursor cursor,
                                           final DateFormat dateFormat, final Long totalCount)
            throws IOException, InterruptedException {
        long lineCount = 0;

        for ( cursor.moveToFirst(); ! cursor.isAfterLast(); cursor.moveToNext() ) {
            Logging.info("export: "+lineCount + " / "+totalCount);
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
                setProgressStatus(R.string.gpx_exporting);
            } else {
                final int percentDone = (int) (((lineCount) ) / totalCount);
                onProgressUpdate(percentDone);
            }
        }
        return lineCount;
    }

    @Override
    protected void onPreExecute() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //ALIBI: Android like killing long-running tasks like this if you let the screen shut off
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                setProgressStatus(R.string.gpx_preparing);
                setProgressIndeterminate();
            }});
    }

    @Override
    protected void onPostExecute(String result) {
        if (null != result) { //launch task will exist with bg thread enqueued with null return
            activity.runOnUiThread(new Runnable() {
                              @Override
                              public void run() {
                                  clearProgressDialog();
                              }
                          });
            // fire share intent?
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Intent intent = new Intent(Intent.ACTION_SEND);
            final String fileName = (gpxDestFile != null && !gpxDestFile.getName().isEmpty()) ? gpxDestFile.getName() : "WiGLE.gpx";
            intent.putExtra(Intent.EXTRA_SUBJECT, fileName);
            intent.setType("application/gpx");

            //TODO: verify local-only storage case/gpx_paths.xml
            final MainActivity main = MainActivity.getMainActivity();
            final Context context = main.getApplicationContext();
            if (null != context && main != null) {
                final Uri fileUri = FileProvider.getUriForFile(context,
                        main.getApplicationContext().getPackageName() +
                                ".gpxprovider", gpxDestFile);

                intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(intent, activity.getResources().getText(R.string.send_to)));
            } else {
                Logging.error("Unable to initiate GPX export - null context");
            }
        }
    }
}
