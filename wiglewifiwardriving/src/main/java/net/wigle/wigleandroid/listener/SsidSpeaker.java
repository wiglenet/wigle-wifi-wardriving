package net.wigle.wigleandroid.listener;

import java.util.ArrayList;
import java.util.List;

import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.MainActivity;

public final class SsidSpeaker {
    private static final String EMPTY = "";

    private final ConcurrentLinkedHashMap<String,String> recentSsids = new ConcurrentLinkedHashMap<>(128);
    private final List<String> toSay = new ArrayList<>();
    private MainActivity mainActivity;

    public SsidSpeaker( final MainActivity listActivity ) {
        this.mainActivity = listActivity;
    }

    public void setListActivity( final MainActivity listActivity ) {
        this.mainActivity = listActivity;
    }

    public void add( final String ssid ) {
        final String previous = recentSsids.put(ssid, EMPTY);
        if ( previous == null ) {
            toSay.add(ssid);
        }
    }

    public void speak() {
        final StringBuilder ssidSpeakBuilder = new StringBuilder();
        for ( final String ssid : toSay ) {
            ssidSpeakBuilder.append( ssid ).append( ", " );
        }
        MainActivity.info( "speak: " + ssidSpeakBuilder.toString() );
        mainActivity.speak( ssidSpeakBuilder.toString() );
        toSay.clear();
    }
}
