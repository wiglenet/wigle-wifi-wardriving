// -*- Mode: Java; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
// vim:ts=2:sw=2:tw=80:et

package net.wigle.wigleandroid;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;
import android.os.Environment;
import android.os.Process;

/**
 * our database helper, makes a great data meal.
 */
public final class DatabaseHelper extends Thread {
  // if in same spot, only log once an hour
  private static final long SMALL_LOC_DELAY = 1000L * 60L * 60L;
  // if change is less than these digits, don't bother
  private static final double SMALL_LATLON_CHANGE = 0.0001D;
  private static final double MEDIUM_LATLON_CHANGE = 0.001D;
  private static final double BIG_LATLON_CHANGE = 0.01D;
  private static final String DATABASE_NAME = "wiglewifi.sqlite";
  private static final String DATABASE_PATH = Environment.getExternalStorageDirectory() + "/wiglewifi/";
  private static final int DB_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND;
  
  private static final long QUEUE_CULL_TIMEOUT = 10000L;
  private long prevQueueCullTime = 0L;
  private long prevPendingQueueCullTime = 0L;
  
  private SQLiteStatement insertNetwork;
  private SQLiteStatement insertLocation;
  private SQLiteStatement updateNetwork;
  
  private static final String NETWORK_TABLE = "network";
  private static final String NETWORK_CREATE =
    "create table " + NETWORK_TABLE + " ( "
    + "bssid varchar(20) primary key not null,"
    + "ssid text not null,"
    + "frequency int not null,"
    + "capabilities text not null,"
    + "lasttime long not null,"
    + "lastlat double not null,"
    + "lastlon double not null"
    + ")";
  
  private static final String LOCATION_TABLE = "location";
  private static final String LOCATION_CREATE =
    "create table " + LOCATION_TABLE + " ( "
    + "_id integer primary key autoincrement,"
    + "bssid varchar(20) not null,"
    + "level integer not null,"
    + "lat double not null,"
    + "lon double not null,"
    + "altitude double not null,"
    + "accuracy float not null,"
    + "time long not null"
    + ")";
  
  private SQLiteDatabase db;
  
  private static final int MAX_QUEUE = 512;
  private static final int MAX_DRAIN = 512; // seems to work fine slurping the whole darn thing
  private final Context context;
  private final ArrayBlockingQueue<DBUpdate> queue = new ArrayBlockingQueue<DBUpdate>( MAX_QUEUE );
  private final ArrayBlockingQueue<DBPending> pending = new ArrayBlockingQueue<DBPending>( MAX_QUEUE ); // how to size this better?
  private final AtomicBoolean done = new AtomicBoolean(false);
  private final AtomicLong networkCount = new AtomicLong();
  private final AtomicLong locationCount = new AtomicLong();
  private final AtomicLong newNetworkCount = new AtomicLong();

  private Location lastLoc = null;
  private long lastLocWhen = 0L;

  private final SharedPreferences prefs;
  /** used in private addObservation */
  private final CacheMap<String,Location> previousWrittenLocationsCache = 
    new CacheMap<String,Location>( 16, 64 );
  
  /** class for queueing updates to the database */
  final class DBUpdate {
    public final Network network;
    public final int level;
    public final Location location;
    public final boolean newForRun;
    
    public DBUpdate( final Network network, final int level, final Location location, final boolean newForRun ) {
      this.network = network;
      this.level = level;
      this.location = location;
      this.newForRun = newForRun;
    }
  }

  /** holder for updates which we'll attempt to interpolate based on timing */
  final class DBPending {
    public final Network network;
    public final int level;
    public final boolean newForRun;
    public final long when; // in MS

    public DBPending( final Network network, final int level, final boolean newForRun ) {
      this.network = network;
      this.level = level;
      this.newForRun = newForRun;
      this.when = System.currentTimeMillis();
    }
  }

  public DatabaseHelper( final Context context ) {    
    this.context = context;
    this.prefs = context.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0 );
    setName("db-worker");
  }

	public int getQueueSize() {
		return queue.size();
	}
  
  @Override
  public void run() {
    try {
      WigleAndroid.info( "starting db thread" );
      
      WigleAndroid.info( "setting db thread priority (-20 highest, 19 lowest) to: " + DB_PRIORITY );
      Process.setThreadPriority( DB_PRIORITY );
      
      getNetworkCountFromDB();
      getLocationCountFromDB();
      
      final List<DBUpdate> drain = new ArrayList<DBUpdate>();
      while ( ! done.get() ) {
        try {
          drain.clear();
          drain.add( queue.take() );
          final long startTime = System.currentTimeMillis();
          
          // do a transaction for everything
          db.beginTransaction();
          addObservation( drain.get( 0 ), 1 );
          // give other thread some time
          Thread.yield();
          // now that we've taken care of the one, see if there's more we can do in this transaction
          if ( MAX_DRAIN > 1 ) {
            // try to drain some more
            queue.drainTo( drain, MAX_DRAIN - 1 );
          }
          final int drainSize = drain.size();
          for ( int i = 1; i < drainSize; i++ ) {
            addObservation( drain.get( i ), drainSize );
          }
          db.setTransactionSuccessful();
          db.endTransaction();
          
          final long delay = System.currentTimeMillis() - startTime;
          if ( delay > 100L || WigleAndroid.DEBUG ) {
            WigleAndroid.info( "db run loop took: " + delay + " ms. drainSize: " + drainSize );
          }
        }
        catch ( final InterruptedException ex ) {
          // no worries
          WigleAndroid.info("db queue take interrupted");
        }
        finally {
          if ( db != null && db.inTransaction() ) {
            db.endTransaction();
          }
        }
      }
    }
    catch ( final Throwable throwable ) {
      WigleAndroid.writeError( Thread.currentThread(), throwable, context );
      throw new RuntimeException( "DatabaseHelper throwable: " + throwable, throwable );
    }
  }
  
  public void open() {
    String dbFilename = DATABASE_NAME;
    final boolean hasSD = WigleAndroid.hasSD();
    if ( hasSD ) {
      File path = new File( DATABASE_PATH );
      path.mkdirs();
      dbFilename = DATABASE_PATH + DATABASE_NAME;
    }
    final File dbFile = new File( dbFilename );
    boolean doCreate = false;
    if ( ! dbFile.exists() ) {
      doCreate = true;
    }
    WigleAndroid.info("opening: " + dbFilename );
    
    if ( hasSD ) {
      db = SQLiteDatabase.openOrCreateDatabase( dbFilename, null );
    }
    else {      
      db = context.openOrCreateDatabase( dbFilename, MAX_PRIORITY, null );
    }
    
    if ( doCreate ) {
      WigleAndroid.info( "creating tables" );
      try {
        db.execSQL(NETWORK_CREATE);
        db.execSQL(LOCATION_CREATE);
        // new database, reset a marker, if any
        final Editor edit = prefs.edit();
        edit.putLong( WigleAndroid.PREF_DB_MARKER, 0L );
        edit.commit();
      }
      catch ( final SQLiteException ex ) {
        WigleAndroid.error( "sqlite exception: " + ex, ex );
      }
    }
    
    // VACUUM turned off, this takes a long long time (20 min), and has little effect since we're not using DELETE
    // WigleAndroid.info("Vacuuming db");
    // db.execSQL( "VACUUM" );
    // WigleAndroid.info("Vacuuming done");
    
    // we don't need to know how many we wrote
    db.execSQL( "PRAGMA count_changes = false" );
    // keep transactions in memory until committed
    db.execSQL( "PRAGMA temp_store = MEMORY" );
    // keep around the journal file, don't create and delete a ton of times
    db.rawQuery( "PRAGMA journal_mode = PERSIST", (String[]) null );
    
    // compile statements
    insertNetwork = db.compileStatement( "INSERT INTO network"
        + " (bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon) VALUES (?,?,?,?,?,?,?)" );
    
    insertLocation = db.compileStatement( "INSERT INTO location"
        + " (bssid,level,lat,lon,altitude,accuracy,time) VALUES (?,?,?,?,?,?,?)" );
    
    updateNetwork = db.compileStatement( "UPDATE network SET"
        + " lasttime = ?, lastlat = ?, lastlon = ? WHERE bssid = ?" );
  }
  
  /**
   * close db, shut down thread
   */
  public void close() {
    done.set( true );
    // interrupt the take, if any
    this.interrupt();
    // give time for db to finish any writes
    int countdown = 50;
    while ( this.isAlive() && countdown > 0 ) {
      WigleAndroid.info( "db still alive. countdown: " + countdown );
      WigleAndroid.sleep( 100L );
      countdown--;
    }
    if ( db.isOpen() ) {
      db.close();
    }
  }
  
  public void checkDB() {
    if ( db == null || ! db.isOpen() ) {
      WigleAndroid.info( "re-opening db in checkDB" );
      open();
    }
  }
  
  public boolean addObservation( final Network network, final Location location, final boolean newForRun ) {
    return addObservation( network, network.getLevel(), location, newForRun );
  }
  private boolean addObservation( final Network network, final int level, final Location location, final boolean newForRun ) {
    final DBUpdate update = new DBUpdate( network, level, location, newForRun );
    // data is lost if queue is full!
    boolean added = queue.offer( update );
    if ( ! added ) {
      WigleAndroid.info( "queue full, not adding: " + network.getBssid() + " ssid: " + network.getSsid() );
      if ( System.currentTimeMillis() - prevQueueCullTime > QUEUE_CULL_TIMEOUT ) {
        WigleAndroid.info("culling queue. size: " + queue.size() );
        // go thru the queue, cull out anything not newForRun
        for ( Iterator<DBUpdate> it = queue.iterator(); it.hasNext(); ) {
          final DBUpdate val = it.next();
          if ( ! val.newForRun ) {
            it.remove();
          }
        }
        WigleAndroid.info("culled queue. size now: " + queue.size() );
        added = queue.offer( update );
        if ( ! added ) {
          WigleAndroid.info( "queue still full, couldn't add: " + network.getBssid() );
        }
        prevQueueCullTime = System.currentTimeMillis();
      }
      
    }
    return added;
  }
  
  private void addObservation( final DBUpdate update, final int drainSize ) {
    checkDB();
    final Network network = update.network;
    final Location location = update.location;
    final String bssid = network.getBssid();
    final String[] bssidArgs = new String[]{ bssid }; 
    
    long lasttime = 0;
    double lastlat = 0;
    double lastlon = 0;
    boolean isNew = false;
    
    // first try cache
    final Location prevWrittenLocation = previousWrittenLocationsCache.get( bssid );
    if ( prevWrittenLocation != null ) {
      // cache hit!
      lasttime = prevWrittenLocation.getTime();
      lastlat = prevWrittenLocation.getLatitude();
      lastlon = prevWrittenLocation.getLongitude();
      // WigleAndroid.info( "db cache hit. bssid: " + network.getBssid() );
    }
    else {
      // cache miss, get the last values from the db, if any
      long start = System.currentTimeMillis();
      // SELECT: can't precompile, as it has more than 1 result value
      final Cursor cursor = db.rawQuery("SELECT lasttime,lastlat,lastlon FROM network WHERE bssid = ?", bssidArgs );
      logTime( start, "db network queried " + bssid );
      if ( cursor.getCount() == 0 ) {
        insertNetwork.bindString( 1, bssid );
        insertNetwork.bindString( 2, network.getSsid() );
        insertNetwork.bindLong( 3, network.getFrequency() );
        insertNetwork.bindString( 4, network.getCapabilities() );
        insertNetwork.bindLong( 5, location.getTime() );
        insertNetwork.bindDouble( 6, location.getLatitude() );
        insertNetwork.bindDouble( 7, location.getLongitude() );
        
        start = System.currentTimeMillis();
        // INSERT
        insertNetwork.execute();
        logTime( start, "db network inserted: " + bssid + " drainSize: " + drainSize );
        
        // update the count
        networkCount.incrementAndGet();
        isNew = true;
        
        // to make sure this new network's location is written
        // don't update stack lasttime,lastlat,lastlon variables
      }
      else {
        // WigleAndroid.info("db using cursor values: " + network.getBssid() );
        cursor.moveToFirst();
        lasttime = cursor.getLong(0);
        lastlat = cursor.getDouble(1);
        lastlon = cursor.getDouble(2);
      }
      cursor.close();
    }
    
    if ( isNew ) {
      newNetworkCount.incrementAndGet();
    }
    
    final boolean fastMode = isFastMode();
    
    final long now = System.currentTimeMillis();
    final double latDiff = Math.abs(lastlat - location.getLatitude());
    final double lonDiff = Math.abs(lastlon - location.getLongitude());
    final boolean smallChange = latDiff > SMALL_LATLON_CHANGE || lonDiff > SMALL_LATLON_CHANGE;
    final boolean mediumChange = latDiff > MEDIUM_LATLON_CHANGE || lonDiff > MEDIUM_LATLON_CHANGE;
    final boolean bigChange = latDiff > BIG_LATLON_CHANGE || lonDiff > BIG_LATLON_CHANGE;
    // WigleAndroid.info( "lasttime: " + lasttime + " now: " + now + " ssid: " + network.getSsid() 
    //    + " lastlat: " + lastlat + " lat: " + location.getLatitude() 
    //    + " lastlon: " + lastlon + " lon: " + location.getLongitude() );
    final boolean changeWorthy = mediumChange || (now - lasttime > SMALL_LOC_DELAY && smallChange);
    
    if ( WigleAndroid.DEBUG ) {
      // do lots of inserts when debug is on
      isNew = true;
    }

    if ( isNew || bigChange || (! fastMode && changeWorthy ) ) {
      // WigleAndroid.info("inserting loc: " + network.getSsid() );
      insertLocation.bindString( 1, bssid );
      insertLocation.bindLong( 2, update.level );  // make sure to use the update's level, network's is mutable...
      insertLocation.bindDouble( 3, location.getLatitude() );
      insertLocation.bindDouble( 4, location.getLongitude() );
      insertLocation.bindDouble( 5, location.getAltitude() );
      insertLocation.bindDouble( 6, location.getAccuracy() );
      insertLocation.bindLong( 7, location.getTime() );
      if ( db.isDbLockedByOtherThreads() ) {
        // this is kinda lame, make this better
        WigleAndroid.error( "db locked by another thread, waiting to loc insert. bssid: " + bssid
            + " drainSize: " + drainSize );
        WigleAndroid.sleep(1000L);
      }
      long start = System.currentTimeMillis();
      // INSERT
      insertLocation.execute();
      logTime( start, "db location inserted: " + bssid + " drainSize: " + drainSize );
      
      // update the count
      locationCount.incrementAndGet();
      // update the cache
      previousWrittenLocationsCache.put( bssid, location );
      
      if ( ! isNew ) {
        // update the network with the lasttime,lastlat,lastlon
        updateNetwork.bindLong( 1, location.getTime() );
        updateNetwork.bindDouble( 2, location.getLatitude() );
        updateNetwork.bindDouble( 3, location.getLongitude() );
        updateNetwork.bindString( 4, bssid );
        if ( db.isDbLockedByOtherThreads() ) {
          // this is kinda lame, make this better
          WigleAndroid.error( "db locked by another thread, waiting to net update. bssid: " + bssid
              + " drainSize: " + drainSize );
          WigleAndroid.sleep(1000L);
        }
        start = System.currentTimeMillis();
        // UPDATE
        updateNetwork.execute();
        logTime( start, "db network updated" );
      }
    }
    else {
      // WigleAndroid.info( "db network not changeworthy: " + bssid );
    }
  }


  /* 
   * GPS location interpolation strategy:
   * 
   *  . keep track of last seen GPS location, either on location updates,
   *    or when we lose a fix (the current approach)
   *    we record both the location, and when the sample was taken.
   * 
   *  . when we do not have a location, keep a "pending" list of observations, 
   *    by recording the network information, and a timestamp.
   *
   *  . when we regain a GPS fix, perform two linear interpolations, 
   *    one for lat and one for lon, based on the time between the lost and 
   *    regained:
   *
   * 
   *   lat
   *    | L
   *    |    ?1
   *    |    ?2
   *    |        F
   *    +--------- lon
   *
   *   lost gps at location L (time t0), found gps at location F (time t1), 
   *   where are "?1" and "?2" at? 
   * 
   *   (t is the time we're interploating for, X is lat/lon):
   *      ?.X = L.X + ( t - t0 ) ( ( F.X - L.X ) / ( t1 - t0 ) )
   * 
   *   we know when all four points were sampled, so we can make a broad 
   *   (i.e. bad) assumption that we moved from L to F at a constant rate 
   *   and along a linear path, and fill in the blanks.
   * 
   *   this approach can be improved (perhaps) with inertial data from 
   *   the accelerometer.
   *
   *   downsides: . this only interpolates, no extrapolation for early 
   *                observations before a GPS fix, or late observations after
   *                a loss but before location is found again. it is no more 
   *                lossy than previous behavior, which discarded these 
   *                observations entirely.
   *              . in-memory queue, so we're tossing pending observations if 
   *                the app lifecycles.
   *              . still subject to a "hotel-lobby" effect, where you enter and
   *                exit a large gps-occluded zone via the same door, which 
   *                degenerates to a point observation.
   */

  /** 
   * mark the last known location where we had a gps fix, when losing it. 
   * you can call this all the time, or just on transitions.
   * call order should be lastLocation() -&gt; 0 or more pendingObservation()  -&gt; recoverLocations()
   * @param loc the location we last saw a gps at, assumed to be "now".
   */
  public void lastLocation( final Location loc ) {
    lastLoc = loc;
    lastLocWhen = System.currentTimeMillis();
  }

  /** 
   * enqueue a pending observation. 
   * if called after lastLocation: when recoverLocations is called, these pending observations will have 
   * their locations backfilled and then they'll be added to the database.
   * 
   * @param network the mutable network, will have it's level saved out.
   * @param newForRun was this new for the run?
   * @return was the pending observation enqueued
   */
  public boolean pendingObservation( final Network network, final boolean newForRun ) {
    if ( lastLoc != null ) {
      // modify this to check age at some point on failure. or offer a flush method. or.. something
      DBPending update = new DBPending( network, network.getLevel(), newForRun );
      boolean added = pending.offer( update );
      if ( ! added ) {
        if ( System.currentTimeMillis() - prevPendingQueueCullTime > QUEUE_CULL_TIMEOUT ) {
          WigleAndroid.info("culling pending queue. size: " + pending.size() );
          // go thru the queue, cull out anything not newForRun
          for ( Iterator<DBPending> it = pending.iterator(); it.hasNext(); ) {
            final DBPending val = it.next();
            if ( ! val.newForRun ) {
              it.remove();
            }
          }
          WigleAndroid.info("culled pending queue. size now: " + pending.size() );
          added = pending.offer( update );
          if ( ! added ) {
            WigleAndroid.info( "pending queue still full, couldn't add: " + network.getBssid() );
            // go thru the queue, squash dups.
            HashSet<String> bssids = new HashSet<String>();
            for ( Iterator<DBPending> it = pending.iterator(); it.hasNext(); ) {
              final DBPending val = it.next();
              if ( ! bssids.add( val.network.getBssid() ) ) {
                it.remove();
              }
            }
            bssids.clear();
            
            added = pending.offer( update );
            if ( ! added ) {
              WigleAndroid.info( "pending queue still full post-dup-purge, couldn't add: " + network.getBssid() );
            }
          }
          prevPendingQueueCullTime = System.currentTimeMillis();
        }
      }
      return added;
    } else {
      return false;
    }
  }

  /** 
   *  walk any pending observations, lerp from last to recover to fill in their location details, add to the real queue.
   *
   * @param loc where we picked up a gps fix again
   * @return how many locations were recovered.
   */
  public int recoverLocations( final Location loc ) {
    int count = 0;
    long locWhen = System.currentTimeMillis();

    if ( ( lastLoc != null ) && ( ! pending.isEmpty() ) ) { 
      final float accuracy = loc.distanceTo( lastLoc );

      if ( locWhen <= lastLocWhen ) { // prevent divide by 0
        locWhen = lastLocWhen + 1;
      }

      final long d_time = MILLISECONDS.toSeconds( locWhen - lastLocWhen );
      WigleAndroid.info( "moved " + accuracy + "m without a GPS fix, over " + d_time + "s" );
      // walk the locations and 
      // lerp! y = y0 + (t - t0)((y1-y0)/(t1-t0))
      // y = y0 + (t - lastLocWhen)((y1-y0)/d_time);
      final double lat0 = lastLoc.getLatitude();
      final double lon0 = lastLoc.getLongitude();
      final double d_lat = loc.getLatitude() - lat0;
      final double d_lon = loc.getLongitude() - lon0;
      final double lat_ratio = d_lat/d_time;
      final double lon_ratio = d_lon/d_time;

      for ( DBPending pend = pending.poll(); pend != null; pend = pending.poll() ) {

        final long tdiff = MILLISECONDS.toSeconds( pend.when - lastLocWhen );

        // do lat lerp:
        final double lerp_lat = lat0 + ( tdiff * lat_ratio );
        
        // do lon lerp
        final double lerp_lon = lon0 + ( tdiff * lon_ratio );

        Location lerpLoc = new Location( "lerp" );
        lerpLoc.setLatitude( lerp_lat );
        lerpLoc.setLongitude( lerp_lon );
        lerpLoc.setAccuracy( accuracy );

        // pull this once we're happy.
        //        WigleAndroid.info( "interpolated to ("+lerp_lat+","+lerp_lon+")" );

        // throw it on the queue!
        if ( addObservation( pend.network, pend.level, lerpLoc, pend.newForRun ) ) {
          count++;
        } else {
          WigleAndroid.info( "failed to add "+pend );
        }
        // XXX: altitude? worth it?
      }
      // return
    }

    lastLoc = null;
    return count;
  }

  private void logTime( long start, String string ) {
    long diff = System.currentTimeMillis() - start;
    if ( diff > 150L ) {
      WigleAndroid.info( string + " in " + diff + " ms" );
    }
  }
  
  public boolean isFastMode() {
    boolean fastMode = false;
    if ( (queue.size() * 100) / MAX_QUEUE > 75 ) {
      // queue is filling up, go to fast mode, only write new networks or big changes
      fastMode = true;
    }
    return fastMode;
  }
  
  /**
   * get the number of networks new to the db for this run
   * @return number of new networks
   */
  public long getNewNetworkCount() {
    return newNetworkCount.get();
  }
  
  public long getNetworkCount() {
    return networkCount.get();
  }
  private void getNetworkCountFromDB() {
    networkCount.set( getCountFromDB( NETWORK_TABLE ) );
  }
  
  public long getLocationCount() {
    return locationCount.get();
  }
  
  /** careful with threading on this one */
  public void getLocationCountFromDB() {
    locationCount.set( getCountFromDB( LOCATION_TABLE ) );
  }
  
  private long getCountFromDB( final String table ) {
    checkDB();
    final Cursor cursor = db.rawQuery( "select count(*) FROM " + table, null );
    cursor.moveToFirst();
    final long count = cursor.getLong( 0 );
    cursor.close();
    return count;
  }
  
  public Network getNetwork( final String bssid ) {
    // check cache
    Network retval = WigleAndroid.getNetworkCache().get( bssid );
    if ( retval == null ) {
      checkDB();
      final String[] args = new String[]{ bssid };
      final Cursor cursor = db.rawQuery("select ssid,frequency,capabilities FROM " + NETWORK_TABLE 
          + " WHERE bssid = ?", args);
      if ( cursor.getCount() > 0 ) {
        cursor.moveToFirst();
        final String ssid = cursor.getString(0);
        final int frequency = cursor.getInt(1);
        final String capabilities = cursor.getString(2);
        retval = new Network( bssid, ssid, frequency, capabilities, 0 );
        WigleAndroid.getNetworkCache().put( bssid, retval );
      }
      cursor.close();
    }
    return retval;
  }
  
  public Cursor networkIterator( final long fromId ) {
    checkDB();
    WigleAndroid.info( "networkIterator fromId: " + fromId );
    final String[] args = new String[]{ Long.toString( fromId ) };
    return db.rawQuery( "SELECT _id,bssid,level,lat,lon,altitude,accuracy,time FROM location WHERE _id > ?", args );
  }
  
}
