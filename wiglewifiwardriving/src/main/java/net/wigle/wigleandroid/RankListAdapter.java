package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.wigle.wigleandroid.model.RankUser;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

/**
 * the array adapter for a list of users.
 * @author arkasha, bobzilla
 */
public final class RankListAdapter extends AbstractListAdapter<RankUser> {
    private static final String ANONYMOUS = "anonymous";
    private final String username;
    private boolean monthRanking = true;

    public RankListAdapter(final Context context, final int rowLayout ) {
        super( context, rowLayout );
        final SharedPreferences prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        username = prefs.getString(PreferenceKeys.PREF_USERNAME, "");
    }

    public void setMonthRanking(final boolean monthRanking) {
        this.monthRanking = monthRanking;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        View row;

        if ( null == convertView ) {
            row = mInflater.inflate( R.layout.rankrow, parent, false );
        }
        else {
            row = convertView;
        }

        RankUser rankUser;
        try {
            rankUser = getItem(position);
        }
        catch ( final IndexOutOfBoundsException ex ) {
            // yes, this happened to someone
            Logging.info("index out of bounds: " + position + " ex: " + ex);
            return row;
        }

        TextView tv = row.findViewById( R.id.rank );
        tv.setText(numberFormat.format(rankUser.getRank()));

        tv = row.findViewById( R.id.rankdiff );
        UserStatsFragment.diffToString(rankUser.getRankDiff(), tv);

        tv = row.findViewById( R.id.username );
        String rankUsername = rankUser.getUsername();
        if (ANONYMOUS.equals(rankUser.getUsername())) {
            tv.setTypeface(null, Typeface.ITALIC);
        }
        else if (username.equals(rankUser.getUsername())) {
            tv.setTypeface(null, Typeface.BOLD);
            rankUsername = "**** " + rankUsername + " ****";
        }
        else {
            tv.setTypeface(null, Typeface.NORMAL);
        }
        tv.setText(rankUsername);

        tv = row.findViewById( R.id.month_wifi_gps );
        tv.setText(numberFormat.format(
                monthRanking ? rankUser.getMonthWifiGps() : rankUser.getTotalWifiGps()));

        tv = row.findViewById( R.id.total_bt_gps );
        tv.setText(getContext().getString(R.string.total_bt) + ": "
                + numberFormat.format(rankUser.getTotalBtGps()));

        tv = row.findViewById( R.id.total_cell_gps );
        tv.setText(getContext().getString(R.string.total_cell) + ": "
                + numberFormat.format(rankUser.getTotalCellGps()));

        return row;
    }
}
