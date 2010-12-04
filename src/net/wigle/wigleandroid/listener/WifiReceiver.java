package net.wigle.wigleandroid.listener;

import static android.location.LocationManager.GPS_PROVIDER;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.wigle.wigleandroid.CacheMap;
import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListActivity;
import net.wigle.wigleandroid.Network;
import net.wigle.wigleandroid.NetworkListAdapter;
import net.wigle.wigleandroid.ListActivity.TrailStat;

import org.andnav.osm.util.GeoPoint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;

public class WifiReceiver extends BroadcastReceiver {
  private ListActivity listActivity;
  private final DatabaseHelper dbHelper;
  private NetworkListAdapter listAdapter;
  private SimpleDateFormat timeFormat;
  private NumberFormat numberFormat1;
  
  private Handler wifiTimer;
  private Location prevGpsLocation;
  private long scanRequestTime = Long.MIN_VALUE;
  private int pendingCount = 0;
  private long previousTalkTime = System.currentTimeMillis();
  private final Set<String> runNetworks = new HashSet<String>();
  private long prevNewNetCount;
  private long prevScanPeriod;
  
  public static final int SIGNAL_COMPARE = 10;
  public static final int CHANNEL_COMPARE = 11;
  public static final int CRYPTO_COMPARE = 12;
  public static final int FIND_TIME_COMPARE = 13;
  public static final int SSID_COMPARE = 14;
  
  private static final Comparator<Network> signalCompare = new Comparator<Network>() {
    public int compare( Network a, Network b ) {
      return b.getLevel() - a.getLevel();
    }
  };
  
  private static final Comparator<Network> channelCompare = new Comparator<Network>() {
    public int compare( Network a, Network b ) {
      return a.getFrequency() - b.getFrequency();
    }
  };
  
  private static final Comparator<Network> cryptoCompare = new Comparator<Network>() {
    public int compare( Network a, Network b ) {
      return b.getCrypto() - a.getCrypto();
    }
  };
  
  private static final Comparator<Network> findTimeCompare = new Comparator<Network>() {
    public int compare( Network a, Network b ) {
      return (int) (b.getConstructionTime() - a.getConstructionTime());
    }
  };
  
  private static final Comparator<Network> ssidCompare = new Comparator<Network>() {
    public int compare( Network a, Network b ) {
      return a.getSsid().compareTo( b.getSsid() );
    }
  };
  
  public WifiReceiver( final ListActivity listActivity, final DatabaseHelper dbHelper,
      final NetworkListAdapter listAdapter ) {
    this.listActivity = listActivity;
    this.dbHelper = dbHelper;
    this.listAdapter = listAdapter;
    ListActivity.lameStatic.runNetworks = runNetworks;
    
    // formats for speech
    timeFormat = new SimpleDateFormat( "h mm aa" );
    numberFormat1 = NumberFormat.getNumberInstance( Locale.US );
    if ( numberFormat1 instanceof DecimalFormat ) {
      ((DecimalFormat) numberFormat1).setMaximumFractionDigits( 1 );
    }
  }
  
  public void setListActivity( final ListActivity listActivity ) {
    this.listActivity = listActivity;
  }
  
  public void setListAdapter( final NetworkListAdapter listAdapter ) {
    this.listAdapter = listAdapter;
  }
  
  public int getRunNetworkCount() {
    return runNetworks.size();
  }
  
  @Override
  public void onReceive( final Context context, final Intent intent ){
    // final long start = System.currentTimeMillis();
    final WifiManager wifiManager = (WifiManager) listActivity.getSystemService(Context.WIFI_SERVICE);
    final List<ScanResult> results = wifiManager.getScanResults(); // return can be null!
    
    long nonstopScanRequestTime = Long.MIN_VALUE;
    final SharedPreferences prefs = listActivity.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final long period = getScanPeriod();
    if ( period == 0 ) {
      // treat as "continuous", so request scan in here
      doWifiScan();
      nonstopScanRequestTime = System.currentTimeMillis();
    }
    if ( period != prevScanPeriod && listActivity.isScanning() ) {
      if ( period >= ListActivity.LOCATION_UPDATE_INTERVAL ) {
        // update our location scanning speed
        ListActivity.info("setting location updates to: " + period);
        listActivity.setLocationUpdates(period, 0f);
      }
      prevScanPeriod = period;
    }
    
    final boolean showCurrent = prefs.getBoolean( ListActivity.PREF_SHOW_CURRENT, true );
    if ( showCurrent ) {
      listAdapter.clear();
    }
    
    final int preQueueSize = dbHelper.getQueueSize();
    final boolean fastMode = dbHelper.isFastMode();
    final Location location = listActivity.getGPSListener().getLocation();
    
    final CacheMap<String,Network> networkCache = ListActivity.getNetworkCache();
    boolean somethingAdded = false;
    int resultSize = 0;
    int newForRun = 0;
    // can be null on shutdown
    if ( results != null ) {
      resultSize = results.size();
      for ( ScanResult result : results ) {
        Network network = networkCache.get( result.BSSID );
        if ( network == null ) {
          network = new Network( result );
          networkCache.put( network.getBssid(), network );
        }
        else {
          // cache hit, just set the level
          network.setLevel( result.level );
        }
        final boolean added = runNetworks.add( result.BSSID );
        if ( added ) {
            newForRun++;
        }
        somethingAdded |= added;
        
        // if we're showing current, or this was just added, put on the list
        if ( showCurrent || added ) {
          listAdapter.add( network );
          // load test
          // for ( int i = 0; i< 10; i++) {
          //  listAdapter.add( network );
          // }
          
        }
        else {
          // not showing current, and not a new thing, go find the network and update the level
          // this is O(n), ohwell, that's why showCurrent is the default config.
          for ( int index = 0; index < listAdapter.getCount(); index++ ) {
            final Network testNet = listAdapter.getItem(index);
            if ( testNet.getBssid().equals( network.getBssid() ) ) {
              testNet.setLevel( result.level );
            }
          }
        }
        
        
        if ( dbHelper != null ) {
          if ( location != null  ) {
            // if in fast mode, only add new-for-run stuff to the db queue
            if ( fastMode && ! added ) {
              ListActivity.info( "in fast mode, not adding seen-this-run: " + network.getBssid() );
            }
            else {
              // loop for stress-testing
              // for ( int i = 0; i < 10; i++ ) {
              dbHelper.addObservation( network, location, added );
              // }
            }
          } else {
            // no location
            dbHelper.pendingObservation( network, added );
          }
        }
      }
    }

    // check if there are more "New" nets
    final long newNetCount = dbHelper.getNewNetworkCount();
    final long newNetDiff = newNetCount - prevNewNetCount;
    prevNewNetCount = newNetCount;
    
    if ( ! listActivity.isMuted() ) {
      final boolean playRun = prefs.getBoolean( ListActivity.PREF_FOUND_SOUND, true );
      final boolean playNew = prefs.getBoolean( ListActivity.PREF_FOUND_NEW_SOUND, true );
      if ( newNetDiff > 0 && playNew ) {
        listActivity.playNewNetSound();
      }
      else if ( somethingAdded && playRun ) {
        listActivity.playRunNetSound();
      }
    }
    
    final int sort = prefs.getInt(ListActivity.PREF_LIST_SORT, SIGNAL_COMPARE);
    Comparator<Network> comparator = signalCompare;
    switch ( sort ) {
      case SIGNAL_COMPARE:
        comparator = signalCompare;
        break;
      case CHANNEL_COMPARE:
        comparator = channelCompare;
        break;
      case CRYPTO_COMPARE:
        comparator = cryptoCompare;
        break;
      case FIND_TIME_COMPARE:
        comparator = findTimeCompare;
        break;
      case SSID_COMPARE:
        comparator = ssidCompare;
        break;
    }
    listAdapter.sort( comparator );

    final long dbNets = dbHelper.getNetworkCount();
    final long dbLocs = dbHelper.getLocationCount();
    
    // update stat
    listActivity.setNetCountUI();
    
    // set the statics for the map
    ListActivity.lameStatic.runNets = runNetworks.size();
    ListActivity.lameStatic.newNets = newNetCount;
    ListActivity.lameStatic.currNets = resultSize;
    ListActivity.lameStatic.preQueueSize = preQueueSize;
    ListActivity.lameStatic.dbNets = dbNets;
    ListActivity.lameStatic.dbLocs = dbLocs;
    
    if ( newForRun > 0 ) {
      if ( location == null ) {
        // save for later
        pendingCount += newForRun;
      }
      else {
        final GeoPoint geoPoint = new GeoPoint( location );
        TrailStat trailStat = ListActivity.lameStatic.trail.get( geoPoint );
        if ( trailStat == null ) {
          trailStat = new TrailStat();
          ListActivity.lameStatic.trail.put( geoPoint, trailStat );
        }
        trailStat.newForRun += newForRun;
        trailStat.newForDB += newNetDiff;
        
        // add any pendings
        // don't go crazy
        if ( pendingCount > 25 ) {
          pendingCount = 25;
        }
        trailStat.newForRun += pendingCount;
        pendingCount = 0;
      }
    }
    
    // info( savedStats );
    
    // notify
    listAdapter.notifyDataSetChanged();
    
    final long now = System.currentTimeMillis();
    if ( scanRequestTime <= 0 ) {
      // wasn't set, set to now
      scanRequestTime = now;
    }
    final String status = resultSize + " scanned in " + (now - scanRequestTime) + "ms. DB Queue: " + preQueueSize;
    listActivity.setStatusUI( status );
    // we've shown it, reset it to the nonstop time above, or min_value if nonstop wasn't set.
    scanRequestTime = nonstopScanRequestTime;
    
    // do lerp if need be
    if ( location == null ) {
      if ( prevGpsLocation != null ) {
        dbHelper.lastLocation( prevGpsLocation );
        // ListActivity.info("set last location for lerping");
      }
    } 
    else {
      dbHelper.recoverLocations( location );
    }
    
    // do distance calcs
    if ( location != null && GPS_PROVIDER.equals( location.getProvider() )
        && location.getAccuracy() <= ListActivity.MIN_DISTANCE_ACCURACY ) {
      if ( prevGpsLocation != null ) {
        float dist = location.distanceTo( prevGpsLocation );
        // info( "dist: " + dist );
        if ( dist > 0f ) {
          final Editor edit = prefs.edit();
          edit.putFloat( ListActivity.PREF_DISTANCE_RUN,
              dist + prefs.getFloat( ListActivity.PREF_DISTANCE_RUN, 0f ) );
          edit.putFloat( ListActivity.PREF_DISTANCE_TOTAL,
              dist + prefs.getFloat( ListActivity.PREF_DISTANCE_TOTAL, 0f ) );
          edit.commit();
        }
      }
      
      // set for next time
      prevGpsLocation = location;
    }
    
    final long speechPeriod = prefs.getLong( ListActivity.PREF_SPEECH_PERIOD, ListActivity.DEFAULT_SPEECH_PERIOD );
    if ( speechPeriod != 0 && now - previousTalkTime > speechPeriod * 1000L ) {
      doAnnouncement( preQueueSize, newNetCount, now );
    }
  }
  
  private void doAnnouncement( int preQueueSize, long newNetCount, long now ) {
    final SharedPreferences prefs = listActivity.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    StringBuilder builder = new StringBuilder();
    
    if ( listActivity.getGPSListener().getLocation() == null ) {
      builder.append( "no gps fix, " );
    }
    
    // run, new, queue, miles, time, battery
    if ( prefs.getBoolean( ListActivity.PREF_SPEAK_RUN, true ) ) {
      builder.append( "run " ).append( runNetworks.size() ).append( ", " );
    }
    if ( prefs.getBoolean( ListActivity.PREF_SPEAK_NEW, true ) ) {
      builder.append( "new " ).append( newNetCount ).append( ", " );
    }
    if ( preQueueSize > 0 && prefs.getBoolean( ListActivity.PREF_SPEAK_QUEUE, true ) ) {
      builder.append( "queue " ).append( preQueueSize ).append( ", " );
    }
    if ( prefs.getBoolean( ListActivity.PREF_SPEAK_MILES, true ) ) {
      final float dist = prefs.getFloat( ListActivity.PREF_DISTANCE_RUN, 0f );
      builder.append( "from " ).append( numberFormat1.format( dist / 1609.344f ) ).append( " miles, " );
    }
    if ( prefs.getBoolean( ListActivity.PREF_SPEAK_TIME, true ) ) {
      String time = timeFormat.format( new Date() );      
      time = time.replace(" 0", " oh ");
      builder.append( time ).append( ", " );
    }
    final int batteryLevel = listActivity.getBatteryLevelReceiver().getBatteryLevel();
    if ( batteryLevel >= 0 && prefs.getBoolean( ListActivity.PREF_SPEAK_BATTERY, true ) ) {
      builder.append( "battery " ).append( batteryLevel ).append( " percent, " );
    }
    
    ListActivity.info( "speak: " + builder.toString() );
    listActivity.speak( builder.toString() );
    previousTalkTime = now;
  }
  
  public void setupWifiTimer( final boolean turnedWifiOn ) {
    ListActivity.info( "create wifi timer" );
    if ( wifiTimer == null ) {
      wifiTimer = new Handler();
      final Runnable mUpdateTimeTask = new Runnable() {
        public void run() {              
            // make sure the app isn't trying to finish
            if ( ! listActivity.isFinishing() ) {
              // info( "timer start scan" );
              doWifiScan();
              if ( scanRequestTime <= 0 ) {
                scanRequestTime = System.currentTimeMillis();
              }
              long period = getScanPeriod();
              // check if set to "continuous"
              if ( period == 0L ) {
                // set to default here, as a scan will also be requested on the scan result listener
                period = ListActivity.SCAN_DEFAULT;
              }
              // info("wifitimer: " + period );
              wifiTimer.postDelayed( this, period );
            }
            else {
              ListActivity.info( "finishing timer" );
            }
        }
      };
      wifiTimer.removeCallbacks( mUpdateTimeTask );
      wifiTimer.postDelayed( mUpdateTimeTask, 100 );
  
      if ( turnedWifiOn ) {
        ListActivity.info( "not immediately running wifi scan, since it was just turned on"
            + " it will block for a few seconds and fail anyway");
      }
      else {
        ListActivity.info( "start first wifi scan");
        // starts scan, sends event when done
        final boolean scanOK = doWifiScan();
        if ( scanRequestTime <= 0 ) {
          scanRequestTime = System.currentTimeMillis();
        }
        ListActivity.info( "startup finished. wifi scanOK: " + scanOK );
      }
    }
  }
  
  public long getScanPeriod() {
    final SharedPreferences prefs = listActivity.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    
    String scanPref = ListActivity.PREF_SCAN_PERIOD;
    // if over 5 mph
    final Location location = listActivity.getGPSListener().getLocation();
    if ( location != null && location.getSpeed() >= 2.2352f ) {
      scanPref = ListActivity.PREF_SCAN_PERIOD_FAST;
    }
    return prefs.getLong( scanPref, ListActivity.SCAN_DEFAULT );    
  }
  
  public boolean doWifiScan() {
    final WifiManager wifiManager = (WifiManager) listActivity.getSystemService(Context.WIFI_SERVICE);
    boolean retval = false;
    if ( listActivity.isUploading() ) {
      ListActivity.info( "uploading, not scanning for now" );
    }
    else if (listActivity.isScanning()){
      retval = wifiManager.startScan();
    }
    else {
      // scanning is off. since we're the only timer, update the UI
      listActivity.setNetCountUI();
      listActivity.setLocationUI();
      listActivity.setStatusUI( "Scanning Turned Off" );
      // keep the scan times from getting huge
      scanRequestTime = System.currentTimeMillis();
    }
    return retval;
  }
  
  
}
