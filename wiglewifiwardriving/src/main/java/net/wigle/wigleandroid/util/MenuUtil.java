package net.wigle.wigleandroid.util;

import android.view.MenuItem;

import com.google.android.material.navigation.NavigationView;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;

public class MenuUtil {
    /**
     * utility method to check an item in the stats submenu.
     * @param navigationView
     * @param mainActivity
     * @param itemId
     * @return true if nothing went obviously wrong
     */
    public static boolean selectStatsSubmenuItem(final NavigationView navigationView, final MainActivity mainActivity, final int itemId ) {
        if (navigationView == null) {
            return false;
        }
        MenuItem menuItem = navigationView.getMenu().findItem(itemId);
        if (menuItem == null) {
            return false;
        }
        menuItem.setCheckable(true);
        navigationView.setCheckedItem(itemId);
        navigationView.getMenu().setGroupVisible(R.id.stats_group, true);
        menuItem.setChecked(true);
        if (mainActivity != null) mainActivity.selectFragment(itemId);
        return true;
    }
}
