package net.wigle.wigleandroid.util;

import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import net.wigle.wigleandroid.MainActivity;

import java.io.File;

/**
 * Simple filespace check calls
 */
public class FileUtility {

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
            // if we can't determine freespace, be optimistic.
            MainActivity.error("Unable to determine free space: ",ex);
            return Long.MAX_VALUE;
        }
    }

    public static boolean checkInternalStorageDangerZone() {
        return getFreeInternalBytes() > WARNING_THRESHOLD_BYTES;
    }

    public static boolean checkExternalStorageDangerZone() {
        return getFreeExternalBytes() > WARNING_THRESHOLD_BYTES;
    }

    public static long getFreeExternalBytes() {
        return FileUtility.getFreeBytes(Environment.getExternalStorageDirectory());
    }

    public static long getFreeInternalBytes() {
        return FileUtility.getFreeBytes(Environment.getDataDirectory());
    }

}
