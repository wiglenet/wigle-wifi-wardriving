package net.wigle.wigleandroid.listener;

import java.util.ArrayList;
import java.util.List;

import net.wigle.wigleandroid.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.ListActivity;

public final class SsidSpeaker {
  private static final String EMPTY = "";
  
  private final ConcurrentLinkedHashMap<String,String> recentSsids = new ConcurrentLinkedHashMap<String,String>(128);  
  private final List<String> toSay = new ArrayList<String>();
  private ListActivity listActivity;
  
  public SsidSpeaker( final ListActivity listActivity ) {
    this.listActivity = listActivity;
  }
  
  public void setListActivity( final ListActivity listActivity ) {
    this.listActivity = listActivity;
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
    ListActivity.info( "speak: " + ssidSpeakBuilder.toString() );    
    listActivity.speak( ssidSpeakBuilder.toString() );
    toSay.clear();
  }
}
