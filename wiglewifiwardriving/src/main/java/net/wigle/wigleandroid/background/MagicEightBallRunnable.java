package net.wigle.wigleandroid.background;

import static net.wigle.wigleandroid.util.FileUtility.M8B_EXT;
import static net.wigle.wigleandroid.util.FileUtility.M8B_FILE_PREFIX;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import net.wigle.m8b.geodesy.mgrs;
import net.wigle.m8b.geodesy.utm;
import net.wigle.m8b.siphash.SipKey;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.MagicEightUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * M8b export - writes files and launches share intent. Checks available memory and limits database query accordingly, but can still be the victim of OOM
 */
public class MagicEightBallRunnable extends ProgressRunnable implements Runnable, AlertSettable {
    private final long totalCount;
    private static final int SLICE_BITS = 30;

    File m8bOutputFile;

    public MagicEightBallRunnable(final FragmentActivity activity, final boolean showProgress, final long totalRecords) {
        super(activity, showProgress);
        this.totalCount = totalRecords;
    }

    @Override
    public void run() {
        onPreExecute();
        final boolean hasSD = FileUtility.hasSD();
        long dbCount = 0;
        try {
            dbCount = ListFragment.lameStatic.dbHelper.getNetsWithLocCountFromDB();
        } catch (DBException dbe) {
            // just rely on the total number of records.
        }
        long available = Runtime.getRuntime().maxMemory();
        long used = Runtime.getRuntime().totalMemory();

        Logging.error("Started with "+(available-used)/1000+"kb free - "+used/1000+"/"+available/1000);
        final long maxRecords = (available-used)/300;
        if (maxRecords < totalCount) {
            Logging.error("Bounding query at  " + maxRecords + " (was " + dbCount+")");
            dbCount = maxRecords;
            //TODO: should we warn here?
        }

        // replace our placeholder if we get a proper number
        long thousandDbRecords = dbCount == 0 ? totalCount: dbCount/1000;

        //DEBUG: MainActivity.info("matching values: " + thousandDbRecords);

        // TODO: there's a real case for refusing to export on devices that don't have SD...
        // TODO: android R FS changes
        File m8bDestFile;
        final FileChannel out;
        try {
            if (hasSD) {
                final String basePath = FileUtility.getM8bPath();
                if (null != basePath) {
                    final File path = new File(basePath);
                    //noinspection ResultOfMethodCallIgnored
                    path.mkdirs();
                    if (!path.exists()) {
                        Logging.info("Got '!exists': " + path);
                    }
                    String openString = basePath + M8B_FILE_PREFIX + M8B_EXT;
                    //DEBUG: MainActivity.info("Opening file: " + openString);
                    m8bDestFile = new File(openString);
                } else {
                    Logging.error("Unable to determine m8b output base path.");
                    return;
                }
            } else {
                if (activity == null) {
                    return;
                }
                m8bDestFile = new File(activity.getApplication().getFilesDir(),
                        M8B_FILE_PREFIX + M8B_EXT);
                if (m8bDestFile.exists()) {
                    //ALIBI: cleanup old
                    m8bDestFile.delete();
                }
            }
            //ALIBI: always start fresh
            out = new FileOutputStream(m8bDestFile, false).getChannel();
            m8bOutputFile = m8bDestFile;

            final SipKey sipkey = new SipKey(new byte[16]);
            final byte[] macBytes = new byte[6];
            final Map<Long, Set<mgrs>> mjg = new TreeMap<>();

            final long genStart = System.currentTimeMillis();
            setProgressStatus(R.string.calculating_m8b);
            final PooledQueryExecutor.Request request = new PooledQueryExecutor.Request(
                    DatabaseHelper.LOCATED_NETS_QUERY+" ORDER BY lasttime DESC LIMIT "+maxRecords, null, new PooledQueryExecutor.ResultHandler() {

                int non_utm = 0;
                int rows = 0;
                int records = 0;
                @Override
                public boolean handleRow(final Cursor cursor) {
                    try {
                        final String bssid = cursor.getString(0);
                        final float lat = cursor.getFloat(1);
                        final float lon = cursor.getFloat(2);
                        if (!(-80 <= lat && lat <= 84)) {
                            non_utm++;
                        } else {
                            mgrs m = mgrs.fromUtm(utm.fromLatLon(lat, lon));

                            Long kslice2 = MagicEightUtil.extractKeyFrom(bssid, macBytes, sipkey, SLICE_BITS);

                            if (null != kslice2) {
                                Set<mgrs> locs = mjg.get(kslice2);
                                if (locs == null) {
                                    locs = new HashSet<>();
                                    mjg.put((long) kslice2, locs);
                                }
                                if (locs.add(m)) {
                                    records++;
                                }
                            }
                        }
                    } catch (IndexOutOfBoundsException ioobe) {
                        //ALIBI: seeing ArrayIndexOutOfBoundsException: length=3; index=-2 from geodesy.mgrs.fromUtm(mgrs.java:64)
                        Logging.error("Bad UTM ", ioobe);
                    }

                    rows++;
                    if (rows % 1000 == 0) {
                        if (checkMemoryHeadroom()) {
                            //DEBUG:  Logging.info("\tprogress: rows: " + (rows/(double)1000) + " / " + thousandDbRecords + " = " + (int) ((rows / (double) 1000 / (double) thousandDbRecords)*100));
                            onProgressUpdate((int) ((rows / (double) 1000 / (double) thousandDbRecords)*100));
                        } else {
                            Logging.error("STOPPING: ran out of memory at "+rows+" rows.");
                            return false;
                        }
                    }
                    return true;
                }

                /**
                 * once the intermediate file's written, run the generate pipeline, setup and enqueue the intent to share
                 */
                @Override
                public void complete() {
                    Logging.info("m8b source export complete...");
                    reactivateProgressBar();
                    // Tidy up the finished writer
                    if (null != out) {
                        try {
                            Charset utf8 = StandardCharsets.UTF_8;

                            ByteBuffer bb = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN); // screw you, java

                            // write header
                            bb.put("MJG\n".getBytes(utf8)); // magic number
                            bb.put("2\n".getBytes(utf8)); // version
                            bb.put("SIP-2-4\n".getBytes(utf8)); // hash
                            bb.put(String.format("%x\n", SLICE_BITS).getBytes(utf8)); // slice bits (hex)
                            bb.put("MGRS-1000\n".getBytes(utf8)); // coords
                            bb.put("4\n".getBytes(utf8)); // id size in bytes (hex)
                            bb.put("9\n".getBytes(utf8)); // coords size in bytes (hex)
                            bb.put(String.format("%x\n", records).getBytes(utf8)); // record count (hex)

                            int recordsize = 4 + 9;
                            bb.flip();
                            while (bb.hasRemaining()) {
                                out.write(bb);
                            }

                            // back to fill mode
                            bb.clear();
                            byte[] mstr = new byte[9];
                            int outElements = 0;
                            for (Map.Entry<Long, Set<mgrs>> me : mjg.entrySet()) {
                                long key = me.getKey();
                                for (mgrs m : me.getValue()) {
                                    if (bb.remaining() < recordsize) {
                                        bb.flip();
                                        while (bb.hasRemaining()) {
                                            out.write(bb);
                                        }
                                        bb.clear();
                                    }
                                    m.populateBytes(mstr);
                                    //ALIBI: relying on narrowing primitive conversion to get the low int bytes - https://docs.oracle.com/javase/specs/jls/se10/html/jls-5.html#jls-5.1.3
                                    bb.putInt((int) key).put(mstr);
                                }
                                outElements++;
                                if (outElements % 100 == 0) {
                                    onProgressUpdate((int) (outElements / (double) mjg.size() * 100));
                                }
                            }

                            bb.flip();
                            while (bb.hasRemaining()) {
                                out.write(bb);
                            }
                            bb.clear();
                            out.close();

                        } catch (IOException ioex) {
                            Logging.error("Failed to close m8b writer", ioex);
                            //TODO: how to handle?
                        }
                    }

                    final long duration = System.currentTimeMillis() - genStart;
                    Logging.info("completed m8b generation. Generation time: " + ((double) duration * 0.001d) + "s");
                    onProgressUpdate(100); //ALIBI: will close the dialog in case fractions didn't work out.
                    onPostExecute("completed");
                }
            }, ListFragment.lameStatic.dbHelper);
            PooledQueryExecutor.enqueue(request);
        } catch (IOException ioex) {
            Logging.error("Unable to open output: ", ioex);
        }
    }

    protected void reactivateProgressBar() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pp.show();
                pp.setMessage(activity.getString(R.string.exporting_m8b_final));
                pp.setIndeterminate();
            }});
    }

    @Override
    protected void onPreExecute() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pp.setMessage(activity.getString(R.string.m8b_sizing));
                pp.setIndeterminate();
            }});
    }

    @Override
    protected void onPostExecute(String result) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (null != result) { //launch task will exist with bg thread enqueued with null return
                    clearProgressDialog();
                    // fire share intent?
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE.m8b");
                    intent.setType("application/wigle.m8b");

                    //TODO: verify local-only storage case/m8b_paths.xml
                    Context c = activity.getApplicationContext();
                    if (null != c) {
                        final Uri fileUri = FileProvider.getUriForFile(c,
                                MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                                        ".m8bprovider", m8bOutputFile);

                        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        //activity.startActivity(Intent.createChooser(intent, activity.getResources().getText(R.string.send_to)));
                        activity.startActivity(intent);
                    } else {
                        Logging.error("Unable to link m8b provider - null context");
                    }
                }
            }
        });
    }

    protected boolean checkMemoryHeadroom() {
        long available = Runtime.getRuntime().maxMemory();
        long used = Runtime.getRuntime().totalMemory();
        float percentAvailable = 100f * (1f - ((float) used / available ));
        if( percentAvailable <= 5.0f ) {
            Logging.error("LOW MEMORY WARNING");
            return false;
        }
        return true;
    }
}
