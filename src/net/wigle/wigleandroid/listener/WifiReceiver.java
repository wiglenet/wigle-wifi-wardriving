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
import java.util.regex.Matcher;

import net.wigle.wigleandroid.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.DashboardActivity;
import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListActivity;
import net.wigle.wigleandroid.Network;
import net.wigle.wigleandroid.NetworkListAdapter;
import net.wigle.wigleandroid.NetworkType;
import net.wigle.wigleandroid.OpenStreetMapViewWrapper;
import net.wigle.wigleandroid.ListActivity.TrailStat;

import org.osmdroid.util.GeoPoint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.widget.Toast;

public class WifiReceiver extends BroadcastReceiver {
  private ListActivity listActivity;
  private final DatabaseHelper dbHelper;
  private NetworkListAdapter listAdapter;
  private SimpleDateFormat timeFormat;
  private NumberFormat numberFormat1;
  
  private Handler wifiTimer;
  private Location prevGpsLocation;
  private long scanRequestTime = Long.MIN_VALUE;
  private long lastScanResponseTime = Long.MIN_VALUE;
  private long lastWifiUnjamTime = Long.MIN_VALUE;
  private long lastSaveLocationTime = Long.MIN_VALUE;
  private int pendingWifiCount = 0;
  private int pendingCellCount = 0;
  private long previousTalkTime = System.currentTimeMillis();
  private final Set<String> runNetworks = new HashSet<String>();
  private long prevNewNetCount;
  private long prevNewCellCount;
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
    final long now = System.currentTimeMillis();
    lastScanResponseTime = now;
    // final long start = now;
    final WifiManager wifiManager = (WifiManager) listActivity.getSystemService(Context.WIFI_SERVICE);
    final List<ScanResult> results = wifiManager.getScanResults(); // return can be null!
    
    long nonstopScanRequestTime = Long.MIN_VALUE;
    final SharedPreferences prefs = listActivity.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final long period = getScanPeriod();
    if ( period == 0 ) {
      // treat as "continuous", so request scan in here
      doWifiScan();
      nonstopScanRequestTime = now;
    }
    final long prefPeriod = prefs.getLong(ListActivity.GPS_SCAN_PERIOD, ListActivity.LOCATION_UPDATE_INTERVAL);
    long setPeriod = prefPeriod;
    if (setPeriod == 0 ){
      setPeriod = Math.max(period, ListActivity.LOCATION_UPDATE_INTERVAL); 
    }
    
    if ( setPeriod != prevScanPeriod && listActivity.isScanning() ) {
      // update our location scanning speed
      ListActivity.info("setting location updates to: " + setPeriod);
      listActivity.setLocationUpdates(setPeriod, 0f);

      prevScanPeriod = setPeriod;
    }
    
    final Location location = listActivity.getGPSListener().getLocation();
    
    // save the location every minute, for later runs, or viewing map during loss of location.
    if (now - lastSaveLocationTime > 60000L && location != null) {
      listActivity.getGPSListener().saveLocation();
      lastSaveLocationTime = now;      
    }
    
    final boolean showCurrent = prefs.getBoolean( ListActivity.PREF_SHOW_CURRENT, true );
    if ( showCurrent ) {
      listAdapter.clear();
    }
    
    final int preQueueSize = dbHelper.getQueueSize();
    final boolean fastMode = dbHelper.isFastMode();
    final ConcurrentLinkedHashMap<String,Network> networkCache = ListActivity.getNetworkCache();
    boolean somethingAdded = false;
    int resultSize = 0;
    int newWifiForRun = 0;
    
    StringBuilder ssidSpeakBuilder = null;
    final boolean ssidSpeak = prefs.getBoolean( ListActivity.PREF_SPEAK_SSID, false );
    if ( ssidSpeak ) {
      ssidSpeakBuilder = new StringBuilder();
    }    
    
    final Matcher ssidMatcher = OpenStreetMapViewWrapper.getFilterMatcher( prefs, ListActivity.FILTER_PREF_PREFIX );
    
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
            newWifiForRun++;
            if ( ssidSpeak ) {
              ssidSpeakBuilder.append( network.getSsid() ).append( ", " );
            }
        }
        somethingAdded |= added;
        
        if ( location != null && (added || network.getGeoPoint() == null) ) {
          // set the geopoint for mapping
          final GeoPoint geoPoint = new GeoPoint( location );
          network.setGeoPoint( geoPoint );
        }                
        
        // if we're showing current, or this was just added, put on the list
        if ( showCurrent || added ) {
          if ( OpenStreetMapViewWrapper.isOk( ssidMatcher, prefs, ListActivity.FILTER_PREF_PREFIX, network ) ) {
            listAdapter.add( network );
          }
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
    final long newWifiCount = dbHelper.getNewWifiCount();
    final long newNetDiff = newWifiCount - prevNewNetCount;
    prevNewNetCount = newWifiCount;
    // check for "New" cell towers
    final long newCellCount = dbHelper.getNewCellCount();
    final long newCellDiff = newCellCount - prevNewCellCount;
    prevNewCellCount = newCellCount;
    
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
    
    // check cell tower info
    final int preCellForRun = runNetworks.size();
    int newCellForRun = 0;
    final Network cellNetwork = recordCellInfo(location);
    if ( cellNetwork != null ) {
      resultSize++;
      if ( showCurrent && OpenStreetMapViewWrapper.isOk( ssidMatcher, prefs, ListActivity.FILTER_PREF_PREFIX, cellNetwork ) ) {
        listAdapter.add(cellNetwork);
      }
      if ( runNetworks.size() > preCellForRun ) {
        newCellForRun++;
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
    ListActivity.lameStatic.newWifi = newWifiCount;
    ListActivity.lameStatic.newCells = newCellCount;
    ListActivity.lameStatic.currNets = resultSize;
    ListActivity.lameStatic.preQueueSize = preQueueSize;
    ListActivity.lameStatic.dbNets = dbNets;
    ListActivity.lameStatic.dbLocs = dbLocs;
    
    // do this if trail is empty, so as soon as we get first gps location it gets triggered
    // and will show up on map
    if ( newWifiForRun > 0 || newCellForRun > 0 || ListActivity.lameStatic.trail.isEmpty() ) {
      if ( location == null ) {
        // save for later
        pendingWifiCount += newWifiForRun;
        pendingCellCount += newCellForRun;
        // ListActivity.info("pendingCellCount: " + pendingCellCount);
      }
      else {
        final GeoPoint geoPoint = new GeoPoint( location );
        TrailStat trailStat = ListActivity.lameStatic.trail.get( geoPoint );
        if ( trailStat == null ) {
          trailStat = new TrailStat();
          ListActivity.lameStatic.trail.put( geoPoint, trailStat );
        }
        trailStat.newWifiForRun += newWifiForRun;
        trailStat.newWifiForDB += newNetDiff;
        // ListActivity.info("newCellForRun: " + newCellForRun);
        trailStat.newCellForRun += newCellForRun;
        trailStat.newCellForDB += newCellDiff;        
        
        // add any pendings
        // don't go crazy
        if ( pendingWifiCount > 25 ) {
          pendingWifiCount = 25;
        }
        trailStat.newWifiForRun += pendingWifiCount;
        pendingWifiCount = 0;
        
        if ( pendingCellCount > 25 ) {
          pendingCellCount = 25;
        }
        trailStat.newCellForRun += pendingCellCount;
        pendingCellCount = 0;
      }
    }
    
    // info( savedStats );
    
    // notify
    listAdapter.notifyDataSetChanged();
    
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
    
    if ( somethingAdded && ssidSpeak ) {
      ListActivity.info( "speak: " + ssidSpeakBuilder.toString() );
      listActivity.speak( ssidSpeakBuilder.toString() );
    }
    
    final long speechPeriod = prefs.getLong( ListActivity.PREF_SPEECH_PERIOD, ListActivity.DEFAULT_SPEECH_PERIOD );
    if ( speechPeriod != 0 && now - previousTalkTime > speechPeriod * 1000L ) {
      doAnnouncement( preQueueSize, newWifiCount, newCellCount, now );
    }
  }
  
  public String getNetworkTypeName() {
    TelephonyManager tele = (TelephonyManager) listActivity.getSystemService( Context.TELEPHONY_SERVICE );
    if ( tele == null ) {
      return null;
    }
    switch (tele.getNetworkType()) {
        case TelephonyManager.NETWORK_TYPE_GPRS:
            return "GPRS";
        case TelephonyManager.NETWORK_TYPE_EDGE:
            return "EDGE";
        case TelephonyManager.NETWORK_TYPE_UMTS:
            return "UMTS";        
        case 4:
            return "CDMA";
        case 5:
            return "CDMA - EvDo rev. 0";
        case 6:
            return "CDMA - EvDo rev. A";
        case 7:
            return "CDMA - 1xRTT";
        case 8:
          return "HSDPA";
        case 9:
          return "HSUPA";
        case 10:
          return "HSPA";
        case 11:
            return "IDEN";    
        case 12:
          return "CDMA - EvDo rev. B";    
        default:
            return "UNKNOWN";
    }
  }
  
  private Network recordCellInfo(final Location location) {
    TelephonyManager tele = (TelephonyManager) listActivity.getSystemService( Context.TELEPHONY_SERVICE );
    Network network = null;
    if ( tele != null ) {
      /*
      List<NeighboringCellInfo> list = tele.getNeighboringCellInfo();
      for (final NeighboringCellInfo cell : list ) {
        ListActivity.info("neigh cell: " + cell + " class: " + cell.getClass().getCanonicalName() );
        ListActivity.info("cid: " + cell.getCid());        
        
        // api level 5!!!!
        ListActivity.info("lac: " + cell.getLac() );
        ListActivity.info("psc: " + cell.getPsc() );
        ListActivity.info("net type: " + cell.getNetworkType() );
        ListActivity.info("nettypename: " + getNetworkTypeName() );
      }
      */
      String bssid = null;
      NetworkType type = null;
      
      CellLocation cellLocation = null;
      try { 
        cellLocation = tele.getCellLocation();
      }
      catch ( NullPointerException ex ) {
        // bug in Archos7 can NPE there, just ignore
      }
      
      if ( cellLocation == null ) {
        // ignore
      }
      else if ( cellLocation.getClass().getSimpleName().equals("CdmaCellLocation") ) {
        try {
          final int systemId = (Integer) cellLocation.getClass().getMethod("getSystemId").invoke(cellLocation);
          final int networkId = (Integer) cellLocation.getClass().getMethod("getNetworkId").invoke(cellLocation);
          final int baseStationId = (Integer) cellLocation.getClass().getMethod("getBaseStationId").invoke(cellLocation);
          if ( systemId > 0 && networkId >= 0 && baseStationId >= 0 ) { 
            bssid = systemId + "_" + networkId + "_" + baseStationId;
            type = NetworkType.CDMA;
          }
        }
        catch ( Exception ex ) {
          ListActivity.error("cdma reflection exception: " + ex);
        }        
      }
      else if ( cellLocation instanceof GsmCellLocation ) {
        GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
        if ( gsmCellLocation.getLac() >= 0 && gsmCellLocation.getCid() >= 0 ) {
          bssid = tele.getNetworkOperator() + "_" + gsmCellLocation.getLac() + "_" + gsmCellLocation.getCid();
          type = NetworkType.GSM;
        }
      }
      
      if ( bssid != null ) {
        final String ssid = tele.getNetworkOperatorName();        
        final String networkType = getNetworkTypeName();
        final String capabilities = networkType + ";" + tele.getNetworkCountryIso();
        
        int strength = 0;
        PhoneState phoneState = listActivity.getPhoneState();
        if (phoneState != null) {
          strength = phoneState.getStrength();
        }
        
        if ( NetworkType.GSM.equals(type) ) {
          strength = gsmRssiMagicDecoderRing( strength );
        }
        
        if ( false ) {
          ListActivity.info( "bssid: " + bssid );        
          ListActivity.info( "strength: " + strength );
          ListActivity.info( "ssid: " + ssid ); 
          ListActivity.info( "capabilities: " + capabilities ); 
          ListActivity.info( "networkType: " + networkType ); 
          ListActivity.info( "location: " + location );
        }
                
        final ConcurrentLinkedHashMap<String,Network> networkCache = ListActivity.getNetworkCache();
        
        final boolean newForRun = runNetworks.add( bssid );
        
        network = networkCache.get( bssid );        
        if ( network == null ) {
          network = new Network( bssid, ssid, 0, capabilities, strength, type );
          networkCache.put( network.getBssid(), network );
        }
        else {
          network.setLevel(strength);
        }
        
        if ( location != null && (newForRun || network.getGeoPoint() == null) ) {
          // set the geopoint for mapping
          final GeoPoint geoPoint = new GeoPoint( location );
          network.setGeoPoint( geoPoint );
        }  
        
        if ( location != null ) {
          dbHelper.addObservation(network, location, newForRun);
        }
      }
    }   
    
    return network;
  }
  
  private int gsmRssiMagicDecoderRing( int strength ) {
    int retval = -113;
    if ( strength == 99 ) {
      // unknown
      retval = -113;
    }
    else {
      retval = ((strength - 31) * 2) - 51;
    }
    // ListActivity.info("strength: " + strength + " retval: " + retval);
    return retval;
  }
  
  private void doAnnouncement( int preQueueSize, long newWifiCount, long newCellCount, long now ) {
    final SharedPreferences prefs = listActivity.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    StringBuilder builder = new StringBuilder();
    
    if ( listActivity.getGPSListener().getLocation() == null ) {
      builder.append( "no gps fix, " );
    }
    
    // run, new, queue, miles, time, battery
    if ( prefs.getBoolean( ListActivity.PREF_SPEAK_RUN, true ) ) {
      builder.append( "run " ).append( runNetworks.size() ).append( ", " );
    }
    if ( prefs.getBoolean( ListActivity.PREF_SPEAK_NEW_WIFI, true ) ) {
      builder.append( "new wifi " ).append( newWifiCount ).append( ", " );
    }
    if ( prefs.getBoolean( ListActivity.PREF_SPEAK_NEW_CELL, true ) ) {
      builder.append( "new cell " ).append( newCellCount ).append( ", " );
    }
    if ( preQueueSize > 0 && prefs.getBoolean( ListActivity.PREF_SPEAK_QUEUE, true ) ) {
      builder.append( "queue " ).append( preQueueSize ).append( ", " );
    }
    if ( prefs.getBoolean( ListActivity.PREF_SPEAK_MILES, true ) ) {
      final float dist = prefs.getFloat( ListActivity.PREF_DISTANCE_RUN, 0f );
      final String distString = DashboardActivity.metersToString( numberFormat1, listActivity, dist, false );
      builder.append( "from " ).append( distString );
    }
    if ( prefs.getBoolean( ListActivity.PREF_SPEAK_TIME, true ) ) {
      String time = timeFormat.format( new Date() );
      // time is hard to say.
      time = time.replace(" 00", " owe clock");
      time = time.replace(" 0", " owe ");
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
    long defaultRate = ListActivity.SCAN_DEFAULT;
    // if over 5 mph
    final Location location = listActivity.getGPSListener().getLocation();
    if ( location != null && location.getSpeed() >= 2.2352f ) {
      scanPref = ListActivity.PREF_SCAN_PERIOD_FAST;
      defaultRate = ListActivity.SCAN_FAST_DEFAULT;
    }
    else if ( location == null || location.getSpeed() < 0.1f ) {
      scanPref = ListActivity.PREF_SCAN_PERIOD_STILL;
      defaultRate = ListActivity.SCAN_STILL_DEFAULT;
    }
    return prefs.getLong( scanPref, defaultRate );    
  }
  
  public void scheduleScan() {
    wifiTimer.post(new Runnable() {
      public void run() {
        doWifiScan();
      }
    });    
  }
  
  /**
   * only call this from a Handler
   * @return
   */
  private boolean doWifiScan() {
    // ListActivity.info("do wifi scan. lastScanTime: " + lastScanResponseTime);
    final WifiManager wifiManager = (WifiManager) listActivity.getSystemService(Context.WIFI_SERVICE);
    boolean retval = false;
    if ( listActivity.isUploading() ) {
      ListActivity.info( "uploading, not scanning for now" );
      // reset this
      lastScanResponseTime = Long.MIN_VALUE;
    }
    else if (listActivity.isScanning()){
      retval = wifiManager.startScan();
      final long now = System.currentTimeMillis();
      if ( lastScanResponseTime < 0 ) {
        // use now, since we made a request
        lastScanResponseTime = now;
      }
      else {
        final long sinceLastScan = now - lastScanResponseTime;
        final SharedPreferences prefs = listActivity.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
        final long resetWifiPeriod = prefs.getLong(
            ListActivity.PREF_RESET_WIFI_PERIOD, ListActivity.DEFAULT_RESET_WIFI_PERIOD );
        
        if ( resetWifiPeriod > 0 && sinceLastScan > resetWifiPeriod ) {
          ListActivity.warn("Time since last scan: " + sinceLastScan + " milliseconds");
          if ( lastWifiUnjamTime < 0 || now - lastWifiUnjamTime > resetWifiPeriod ) {
            Toast.makeText( listActivity, 
                "Wifi appears jammed, Turning off, and then on, WiFi.", Toast.LENGTH_LONG ).show();
            wifiManager.setWifiEnabled(false);
            wifiManager.setWifiEnabled(true);    
            lastWifiUnjamTime = now;
            listActivity.speak("Warning, latest wifi scan completed " 
                + (sinceLastScan / 1000L) + " seconds ago. Restarting wifi.");
          }
        }
      }
    }
    else {
      // scanning is off. since we're the only timer, update the UI
      listActivity.setNetCountUI();
      listActivity.setLocationUI();
      listActivity.setStatusUI( "Scanning Turned Off" );
      // keep the scan times from getting huge
      scanRequestTime = System.currentTimeMillis();
      // reset this
      lastScanResponseTime = Long.MIN_VALUE;
    }
    
    // battery kill
    if ( ! listActivity.isUploading() ) {
      final SharedPreferences prefs = listActivity.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
      final long batteryKill = prefs.getLong(
          ListActivity.PREF_BATTERY_KILL_PERCENT, ListActivity.DEFAULT_BATTERY_KILL_PERCENT);
      
      if ( listActivity.getBatteryLevelReceiver() != null ) {
        final int batteryLevel = listActivity.getBatteryLevelReceiver().getBatteryLevel(); 
        final int batteryStatus = listActivity.getBatteryLevelReceiver().getBatteryStatus();
        // ListActivity.info("batteryStatus: " + batteryStatus);
  
        if ( batteryKill > 0 && batteryLevel > 0 && batteryLevel <= batteryKill 
            && batteryStatus != BatteryManager.BATTERY_STATUS_CHARGING) {
          final String text = "Battery level at " + batteryLevel + " percent, shutting down Wigle Wifi";
          Toast.makeText( listActivity, text, Toast.LENGTH_LONG ).show();
          listActivity.speak( "low battery" );
          listActivity.finish();
        }
      }
    }
    
    return retval;
  }
  
  
}
