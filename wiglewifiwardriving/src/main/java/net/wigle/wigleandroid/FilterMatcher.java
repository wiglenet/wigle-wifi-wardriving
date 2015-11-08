package net.wigle.wigleandroid;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.content.SharedPreferences;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

/**
 * filter matchers
 */
public final class FilterMatcher {
    private static boolean isFilterOn( final SharedPreferences prefs, final String prefix ) {
        return prefs.getBoolean( prefix + ListFragment.PREF_MAPF_ENABLED, true );
    }

    public static Matcher getFilterMatcher( final SharedPreferences prefs, final String prefix ) {
        final String regex = prefs.getString( prefix + ListFragment.PREF_MAPF_REGEX, "" );
        Matcher matcher = null;
        if ( isFilterOn( prefs, prefix ) && regex != null && ! "".equals(regex) ) {
            try {
                Pattern pattern = Pattern.compile( regex, Pattern.CASE_INSENSITIVE );
                matcher = pattern.matcher( "" );
            }
            catch ( PatternSyntaxException ex ) {
                MainActivity.error("regex pattern exception: " + ex);
            }
        }

        return matcher;
    }

    public static boolean isOk( final Matcher matcher, final SharedPreferences prefs, final String prefix,
                                final Network network ) {

        if ( ! isFilterOn( prefs, prefix ) ) {
            return true;
        }

        if ( matcher != null ) {
            matcher.reset(network.getSsid());
            final boolean invert = prefs.getBoolean( prefix + ListFragment.PREF_MAPF_INVERT, false );
            final boolean matches = matcher.find();
            if ( ! matches && ! invert) {
                return false;
            }
            else if ( matches && invert ) {
                return false;
            }
        }

        if ( NetworkType.WIFI.equals( network.getType() ) ) {
            switch ( network.getCrypto() ) {
                case Network.CRYPTO_NONE:
                    if ( ! prefs.getBoolean( prefix + ListFragment.PREF_MAPF_OPEN, true ) ) {
                        return false;
                    }
                    break;
                case Network.CRYPTO_WEP:
                    if ( ! prefs.getBoolean( prefix + ListFragment.PREF_MAPF_WEP, true ) ) {
                        return false;
                    }
                    break;
                case Network.CRYPTO_WPA:
                    if ( ! prefs.getBoolean( prefix + ListFragment.PREF_MAPF_WPA, true ) ) {
                        return false;
                    }
                    break;
                default:
                    MainActivity.error( "unhandled crypto: " + network );
            }
        }
        else if (!prefs.getBoolean(prefix + ListFragment.PREF_MAPF_CELL, true)) {
            return false;
        }

        return true;
    }

}
