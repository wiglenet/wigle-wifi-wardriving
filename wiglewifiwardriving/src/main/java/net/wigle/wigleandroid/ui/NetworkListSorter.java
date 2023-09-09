package net.wigle.wigleandroid.ui;

import android.content.SharedPreferences;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.util.Comparator;

/**
 * Shared UI utility to define and access comparators for {@link net.wigle.wigleandroid.model.Network} objects
 * @author arkasha, bobzilla
 */
public class NetworkListSorter {

    public static final int SIGNAL_COMPARE = 10;
    public static final int CHANNEL_COMPARE = 11;
    public static final int CRYPTO_COMPARE = 12;
    public static final int FIND_TIME_COMPARE = 13;
    public static final int SSID_COMPARE = 14;

    public static final Comparator<Network> signalCompare = (a, b) -> b.getLevel() - a.getLevel();

    public static final Comparator<Network> channelCompare = (a, b) -> a.getFrequency() - b.getFrequency();

    public static final Comparator<Network> cryptoCompare = (a, b) -> b.getCrypto() - a.getCrypto();

    public static final Comparator<Network> findTimeCompare = (a, b) -> (int) (b.getConstructionTime() - a.getConstructionTime());

    public static final Comparator<Network> ssidCompare = (a, b) -> a.getSsid().compareTo( b.getSsid() );

    /**
     * Check preferences and return the correct comparator
     * @param prefs a SharedPreferences instance that contains a PREF_LIST_SORT element
     * @return a Network Comparator instance
     */
    public static Comparator<Network> getSort(final SharedPreferences prefs) {
        if (prefs == null) {
            Logging.warn("null preferences; returning default comparator");
            return signalCompare;
        }

        final int sort = prefs.getInt(PreferenceKeys.PREF_LIST_SORT, SIGNAL_COMPARE);
        switch (sort) {
            case SIGNAL_COMPARE:
                return signalCompare;
            case CHANNEL_COMPARE:
                return channelCompare;
            case CRYPTO_COMPARE:
                return cryptoCompare;
            case FIND_TIME_COMPARE:
                return findTimeCompare;
            case SSID_COMPARE:
                return ssidCompare;
            default:
                Logging.warn("fell through to default comparator");
                return signalCompare;
        }
    }
}
