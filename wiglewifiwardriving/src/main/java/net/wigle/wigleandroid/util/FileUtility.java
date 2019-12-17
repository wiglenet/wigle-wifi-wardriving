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

    //directory locations - centrally managed here, but must be in sync with fileprovider defs
    private  final static String APP_DIR = "wiglewifi";
    private final static String APP_SUB_DIR = "/"+APP_DIR+"/";
    private static final String GPX_DIR = APP_SUB_DIR+"gpx/";
    private final static String KML_DIR = "app_kml";
    private final static String KML_DIR_BASE = "kml";
    private static final String M8B_DIR = APP_SUB_DIR+"m8b/";

    public final static String CSV_EXT = ".csv";
    public static final String ERROR_STACK_FILE_PREFIX = "errorstack";
    public static final String GPX_EXT = ".gpx";
    public static final String GZ_EXT = ".gz";
    public final static String CSV_GZ_EXT = CSV_EXT+GZ_EXT;
    public final static String KML_EXT = ".kml";
    public static final String M8B_FILE_PREFIX = "export";
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
        File sdCard = new File(safeFilePath(Environment.getExternalStorageDirectory()) + "/");
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
        return safeFilePath(Environment.getExternalStorageDirectory()) + APP_SUB_DIR;
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
                //noinspection ResultOfMethodCallIgnored
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

    /**
     * return the uploads dir if we're using external storage
     * @return external file location if we're using external/otherwise null
     * //TODO: do we write uploads to context.getApplicationContext().getFilesDir() if !hasSD?
     */
    public static String getUploadFilePath() {
        if ( hasSD() ) {
            return getSDPath();
        }
        return null;
    }

    /**
     * return the m8b dir if we're using external storage
     * @return external file location if we're using external/otherwise null
     * //TODO: useful to return the true path if !hasSD?
     */
    public static String getM8bPath() {
        if ( hasSD() ) {
            return safeFilePath(Environment.getExternalStorageDirectory()) + M8B_DIR;
        }
        return null;
    }

    /**
     * return the GPX dir if we're using external storage
     * @return external file location if we're using external/otherwise null
     * //TODO: useful to return the true path if !hasSD?
     */
    public static String getGpxPath() {
        if ( hasSD() ) {
            return safeFilePath(Environment.getExternalStorageDirectory()) + GPX_DIR;
        }
        return null;
    }

    /**
     * just get the KML location for internal purposes; should be compatible with the results of
     * getKmlDownloadFile
     * @param context the context of the application
     * @return the string path suitable for intent construction
     */
    public static String getKmlPath(final Context context) {
        if (hasSD()) {
            //ALIBI: placing these right in the appdir external in storage for now.
            return FileUtility.getSDPath();
        }
        File f = new File(context.getFilesDir(), KML_DIR);
        return f.getAbsolutePath();
    }

    /**
     * return the error stack dir
     * @param context application context to locate output
     * @return the File instance for the path
     */
    public static File getErrorStackPath(final Context context) {
        if (hasSD()) {
            return new File(getSDPath());
        }
        return context.getApplicationContext().getFilesDir();
    }

    public static File getKmlDownloadFile(final Context context, final String fileName, final String localFilePath) {
        if (hasSD()) {
            return new File(localFilePath);
        } else {
            File dir = new File(context.getFilesDir(), KML_DIR);
            File file = new File(dir, fileName + KML_EXT);
            if (!file.exists()) {
                MainActivity.error("file does not exist: " + file.getAbsolutePath());
                return null;
            } else {
                //DEBUG: MainActivity.info(file.getAbsolutePath());
                return file;
            }
        }
    }

    public static File getCsvGzFile(final Context context, final String fileName) {
        File file;
        if (hasSD()) {
            file = new File(getSDPath(), fileName);
        } else {
            file = new File(context.getFilesDir(), fileName);
        }
        if (!file.exists()) {
            MainActivity.error("file does not exist: " + file.getAbsolutePath());
            return null;
        } else {
            //DEBUG: MainActivity.info(file.getAbsolutePath());
            return file;
        }

    }

    /**
     * Get the latest stack file
     * @param context context for the request
     * @return the path string for the latest stack file
     */
    public static String getLatestStackfilePath(final Context context) {
        try {
            File fileDir = getErrorStackPath(context);
            if (!fileDir.canRead() || !fileDir.isDirectory()) {
                MainActivity.error("file is not readable or not a directory. fileDir: " + fileDir);
            } else {
                String[] files = fileDir.list();
                if (files == null) {
                    MainActivity.error("no files in dir: " + fileDir);
                } else {
                    String latestFilename = null;
                    for (String filename : files) {
                        if (filename.startsWith(ERROR_STACK_FILE_PREFIX)) {
                            if (latestFilename == null || filename.compareTo(latestFilename) > 0) {
                                latestFilename = filename;
                            }
                        }
                    }
                    MainActivity.info("latest filename: " + latestFilename);

                    return safeFilePath(fileDir) + "/" + latestFilename;
                }
            }
        } catch (Exception ex) {
            MainActivity.error( "error finding stack file: " + ex, ex );
        }
        return null;
    }

    /**
     *  safely get the canonical path, as this call throws exceptions on some devices
     * @param file the file for which to retrieve the cannonical path
     * @return the String path
     */
    private static String safeFilePath(final File file) {
        String retval = null;
        try {
            retval = file.getCanonicalPath();
        } catch (Exception ex) {
            MainActivity.error("Failed to get filepath", ex);
        }

        if (retval == null) {
            retval = file.getAbsolutePath();
        }
        return retval;
    }

    /**
     * file inspection debugging method - probably should get moved into a utility class eventually
     * @param directory the directory to enumerate
     */
    public static void printDirContents(final File directory) {
        System.out.println("\tListing for: "+directory.toString());
        File[] files = directory.listFiles();
        System.out.println("\tSize: "+ files.length);
        for (int i = 0; i < files.length; i++) {
            System.out.println("\t\t"+files[i].getName()+"\t"+files[i].getAbsoluteFile());
        }
    }

}
