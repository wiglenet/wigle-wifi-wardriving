package net.wigle.wigleandroid.background;

import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_MUTED;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.PlaybackParams;

import net.wigle.wigleandroid.listener.WifiReceiver;
import net.wigle.wigleandroid.util.Logging;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Audio thread to indicate BSSID matching results.
 * @author arkasha
 */
public class BssidMatchingAudioThread extends Thread {
    MediaPlayer soundScanning;
    MediaPlayer soundContact;
    AtomicInteger lastHighestSignal;
    WifiReceiver wifiReceiver;

    SharedPreferences prefs;
    public BssidMatchingAudioThread(final SharedPreferences prefs, final MediaPlayer soundScanning, final MediaPlayer soundContact,
        final AtomicInteger lastHighestSignal, final WifiReceiver wifiReceiver) {
        this.prefs = prefs;
        this.soundScanning = soundScanning;
        this.soundContact = soundContact;
        this.lastHighestSignal = lastHighestSignal;
        this.wifiReceiver = wifiReceiver;
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                boolean notify = true;
                if (null != prefs) {
                    notify = !prefs.getBoolean(PREF_MUTED, false);
                }
                if (notify) {
                    soundScanning.start();
                    final long last = lastHighestSignal.getAndSet(Integer.MIN_VALUE);
                    if (last != Integer.MIN_VALUE) {
                        PlaybackParams params = new PlaybackParams();
                        params.setPitch(scaleLevel(last));
                        soundContact.setPlaybackParams(params);
                        soundContact.start();
                    }
                }
                final long currentScanPeriod = wifiReceiver.getScanPeriod();
                // ALIBI: more frequent than 1/2 second is too frenetic
                final long currentRate = Math.max(currentScanPeriod, 500L);
                Thread.sleep(currentRate - soundScanning.getDuration());
            } catch (InterruptedException e) {
                Logging.info("terminated BSSID Matching Audio Thread.", e);
                return;
            }
        }
    }

    static float scaleLevel(final long level) {
        if (level > -80 && level < 0) {
            float factor = (Math.abs(20/((float)level)));
            return 1.0f + factor;
        }
        return 1.0f;
    }
}
