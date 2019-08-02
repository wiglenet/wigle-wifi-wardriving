package net.wigle.wigleandroid.util;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Context-based fresh/upgrade install detection
 * based on https://stackoverflow.com/questions/26352881/detect-if-new-install-or-updated-version-android-app
 *
 */
public class InstallUtility {
    public static boolean isFirstInstall(final Context context) {
        try {
            long firstInstallTime = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).firstInstallTime;
            long lastUpdateTime = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
            return firstInstallTime == lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return true;
        }
    }



    public static boolean isInstallFromUpdate(final Context context) {
        try {
            long firstInstallTime =   context.getPackageManager().getPackageInfo(context.getPackageName(), 0).firstInstallTime;
            long lastUpdateTime = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
            return firstInstallTime != lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }
}
