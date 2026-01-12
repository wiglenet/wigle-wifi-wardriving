package net.wigle.wigleandroid.ui;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Build;
import android.view.WindowInsets;

/**
 * Absolutely awful hack to compensate for edge-to-edge insets propagation problems in Fragments.
 */
public class LayoutUtil {
    public static int getNavigationBarHeight(Activity activity, Resources resources) {
        if (activity == null) return 0;

        WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            }
        }
        //this is sketchy, and shouldn't happen
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        return resourceId > 0 ? resources.getDimensionPixelSize(resourceId) : 0;
    }
}
