package net.wigle.wigleandroid.ui;

import android.content.Context;
import android.content.SharedPreferences;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.R;

import java.text.NumberFormat;

public class UINumberFormat {
    /**
     * format the topbar counters
     * @param input
     * @return
     */
    public static String counterFormat(long input) {
        if (input > 9999999999L) {
            //any android device on the market today would explode
            return (input / 1000000000L) + "G";
        } else if (input >  9999999L) {
            return (input / 1000000L) + "M";
        } else if (input > 9999L) {
            //stay specific until we pass 5 digits
            return (input / 1000L) + "K";
        } else {
            return input+"";
        }
    }

    public static String metersToString(final SharedPreferences prefs, final NumberFormat numberFormat, final Context context, final float meters,
                                        final boolean useShort ) {
        final boolean metric = prefs.getBoolean( ListFragment.PREF_METRIC, false );
        String retval;
        if ( meters > 3000f ) {
            if ( metric ) {
                retval = numberFormat.format( meters / 1000f ) + " " + context.getString(R.string.km_short);
            } else {
                retval = numberFormat.format( meters / 1609.344f ) + " " +
                        (useShort ? context.getString(R.string.mi_short) : context.getString(R.string.miles));
            }
        } else if (metric) {
            retval = numberFormat.format( meters ) + " " + (useShort ? context.getString(R.string.m_short) : context.getString(R.string.meters));
        } else {
            retval = numberFormat.format( meters * 3.2808399f  ) + " " +
                (useShort ? context.getString(R.string.ft_short) : context.getString(R.string.feet));
        }
        return retval;
    }
}
