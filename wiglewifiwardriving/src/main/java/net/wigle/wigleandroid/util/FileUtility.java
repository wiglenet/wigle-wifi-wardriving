package net.wigle.wigleandroid.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import net.wigle.wigleandroid.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * file space and name routines
 */
public class FileUtility {

    public final static String APP_DIR = "wiglewifi";
    public final static String APP_SUB_DIR = "/"+APP_DIR+"/";
    public final static String CSV_EXT = ".csv";
    public static final String GPX_DIR = APP_SUB_DIR+"gpx/";
    public static final String GPX_EXT = ".gpx";
    public static final String GZ_EXT = ".gz";
    public final static String KML_DIR = "app_kml";
    public final static String KML_EXT = ".kml";
    public static final String M8B_FILE_PREFIX = "export";
    //public static final String M8B_SOURCE_EXTENSION = ".m8bs";
    public static final String M8B_DIR = APP_SUB_DIR+"m8b/";
    public static final String M8B_EXT = ".m8b";
    public static final String SQL_EXT = ".sqlite";

    //ALIBI: can't actually read the size of compressed assets via the asset manager - has to be hardcoded
    //  this can be updated by checking the size of wiglewifiwardriving/src/main/assets/mmcmnc.sqlite on build
    public final static long EST_MXC_DB_SIZE = 331776;

    // Start warning if there isn't this much space left on the primary storage location for networks
    public final static long WARNING_THRESHOLD_BYTES = 131072;

    //based on the smart answer in https://stackoverflow.com/questions/7115016/how-to-find-the-amount-of-free-storage-disk-space-left-on-android
    public static long getFreeBytes(File path) {
        try {
            StatFs stats = new StatFs(path.getAbsolutePath());
            if (Build.VERSION.SDK_INT >= 18) {
                return stats.getAvailableBlocksLong() * stats.getBlockSizeLong();
            } else {
                return (long) (stats.getAvailableBlocks() * stats.getBlockSize());
            }
        } catch (Exception ex) {
            // if we can't determine free space, be optimistic. Possibly because of missing permission?
            MainActivity.error("Unable to determine free space: ",ex);
            return Long.MAX_VALUE;
        }
    }

    /**
     * check internal storage for near-fullness
     * @return true if we're in the danger zone
     */
    public static boolean checkInternalStorageDangerZone() {
        return getFreeInternalBytes() > WARNING_THRESHOLD_BYTES;
    }

    /**
     * check external storage for near-fullness
     * @return true if we're in the danger zone
     */
    public static boolean checkExternalStorageDangerZone() {
        return getFreeExternalBytes() > WARNING_THRESHOLD_BYTES;
    }

    /**
     * get the free bytes on external storage
     * @return the number of bytes
     */
    public static long getFreeExternalBytes() {
        return FileUtility.getFreeBytes(Environment.getExternalStorageDirectory());
    }

    /**
     * get the free bytes on internal storage
     * @return the number of bytes
     */
    public static long getFreeInternalBytes() {
        return FileUtility.getFreeBytes(Environment.getDataDirectory());
    }

    /**
     * Core check to determine whether this device has "external" storage the app can use
     * @return true if we can find it and we have permission
     */
    public static boolean hasSD() {
        File sdCard = new File(MainActivity.safeFilePath(Environment.getExternalStorageDirectory()) + "/");
        MainActivity.info("exists: " + sdCard.exists() + " dir: " + sdCard.isDirectory()
                + " read: " + sdCard.canRead() + " write: " + sdCard.canWrite()
                + " path: " + sdCard.getAbsolutePath());

        return sdCard.exists() && sdCard.isDirectory() && sdCard.canRead() && sdCard.canWrite();
    }

    /**
     * determine the FS location on which the "external" storage is mounted
     * @return the string file path
     */
    public static String getSDPath() {
        return MainActivity.safeFilePath(Environment.getExternalStorageDirectory()) + APP_SUB_DIR;
    }

    /**
     * Create an output file sensitive to the SD availability of the install - currently used for network temp files and KmlWriter output
     * @param context Context of the application
     * @param filename the filename to store
     * @param isCache whether to locate this in the cache directory
     * @return tje FileOutputStream of the new file
     * @throws IOException if unable to create the file/directory.
     */
    public static FileOutputStream createFile(final Context context, final String filename, final boolean isCache) throws IOException {
        final String filepath = getSDPath();
        final File path = new File(filepath);

        final boolean hasSD = hasSD();
        if (hasSD) {
            //noinspection ResultOfMethodCallIgnored
            path.mkdirs();
            final String openString = filepath + filename;
            MainActivity.info("openString: " + openString);
            final File file = new File(openString);
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    throw new IOException("Could not create file: " + openString);
                }
            }
            return new FileOutputStream(file);
        } else if (isCache) {
            File file = File.createTempFile(filename, null, context.getCacheDir());
            return new FileOutputStream(file);
        }

        //TODO: dedupe w/ KmlDownloader.writeSharefile()
        if (filename.endsWith(KML_EXT)) {
            File kmlPath = new File(context.getFilesDir(), KML_DIR);
            if (!kmlPath.exists()) {
                kmlPath.mkdir();
            }
            if (kmlPath.exists() && kmlPath.isDirectory()) {
                //DEBUG: MainActivity.info("... file output directory found");
                File kmlFile = new File(kmlPath, filename);
                return new FileOutputStream(kmlFile);
            }
        }
        MainActivity.info("saving as: "+filename);

        return context.openFileOutput(filename, Context.MODE_PRIVATE);
    }

    public static File getErrorStackPath(final Context context) {
        if (FileUtility.hasSD()) {
            return new File(MainActivity.safeFilePath(Environment.getExternalStorageDirectory()) + APP_SUB_DIR);
        }
        return context.getApplicationContext().getFilesDir();
    }

    //TODO: get KML/M8B/GPX/CSV paths?

}
