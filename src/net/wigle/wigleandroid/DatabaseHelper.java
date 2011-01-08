// -*- Mode: Java; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
// vim:ts=2:sw=2:tw=80:et

package net.wigle.wigleandroid;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.view.WindowManager;

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
  private static final String ERROR = "error";
  private static final String EXCEPTION = "exception";
  private final Context context;
  private final ArrayBlockingQueue<DBUpdate> queue = new ArrayBlockingQueue<DBUpdate>( MAX_QUEUE );
  private final ArrayBlockingQueue<DBPending> pending = new ArrayBlockingQueue<DBPending>( MAX_QUEUE ); // how to size this better?
  private final AtomicBoolean done = new AtomicBoolean(false);
  private final AtomicLong networkCount = new AtomicLong();
  private final AtomicLong locationCount = new AtomicLong();
  private final AtomicLong newNetworkCount = new AtomicLong();

  private Location lastLoc = null;
  private long lastLocWhen = 0L;
  private final DeathHandler deathHandler;
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
    this.context = context.getApplicationContext();
    this.prefs = context.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    setName("db-worker");
    this.deathHandler = new DeathHandler( context ); 
  }
    
  private class DeathHandler extends Handler {    
    private Context context;
    public DeathHandler( Context context ) {
      this.context = context;
    }
    
    public void setContext( Context context ) {
      this.context = context;
    }
    
    @Override
    public void handleMessage( final Message msg ) {
      final AlertDialog.Builder builder = new AlertDialog.Builder( context );
      builder.setCancelable( false );
      builder.setTitle( "Fatal DB Problem" );
      Bundle bundle = msg.peekData();
      Exception ex = null;
      if ( bundle == null ) {
        builder.setMessage( "Nothing in bundle" );
      }
      else {
        String error = bundle.getString( ERROR );
        builder.setMessage( "Error: " + error );
        ex = (Exception) bundle.getSerializable( EXCEPTION );
      }
      final Exception finalEx = ex;
      final AlertDialog ad = builder.create();
      ad.setButton( DialogInterface.BUTTON_POSITIVE, "OK, Shutdown", new DialogInterface.OnClickListener() {
        public void onClick( final DialogInterface dialog, final int which ) {
          try {
            dialog.dismiss();
          }
          catch ( Exception ex ) {
            // guess it wasn't there anyways
            ListActivity.info( "exception dismissing alert dialog: " + ex );
          }
          if ( finalEx != null ) {
            throw new RuntimeException( "rethrowing db exception: " + finalEx, finalEx );
          }
          return;
        } }); 
      
      try {
        ad.show();
      }
      catch ( WindowManager.BadTokenException windowEx ) {
        ListActivity.info("window probably gone when trying to display dialog. windowEx: " + windowEx, windowEx );
      }
    }
  }
  
  public void setContext( Context context ) {
    deathHandler.setContext( context );
  }

	public int getQueueSize() {
		return queue.size();
	}
  
  @Override
  public void run() {
    try {
      ListActivity.info( "starting db thread" );
      
      ListActivity.info( "setting db thread priority (-20 highest, 19 lowest) to: " + DB_PRIORITY );
      Process.setThreadPriority( DB_PRIORITY );
      
      try {
        getNetworkCountFromDB();
        getLocationCountFromDB();
      }
      catch ( SQLiteException ex ) {
        ListActivity.error( "exception getting counts from db: " + ex, ex );
        deathDialog( "counts db: " + ex, ex );
      }
      
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
          if ( delay > 100L || ListActivity.DEBUG ) {
            ListActivity.info( "db run loop took: " + delay + " ms. drainSize: " + drainSize );
          }
        }
        catch ( final InterruptedException ex ) {
          // no worries
          ListActivity.info("db queue take interrupted");
        }
        catch ( SQLiteException ex ) {
          ListActivity.error( "exception in db run loop: " + ex, ex );
          deathDialog( "runloop db: " + ex, ex );
        }
        finally {
          if ( db != null && db.inTransaction() ) {
            db.endTransaction();
          }
        }
      }
    }
    catch ( final Throwable throwable ) {
      ListActivity.writeError( Thread.currentThread(), throwable, context );
      throw new RuntimeException( "DatabaseHelper throwable: " + throwable, throwable );
    }
  }
  
  private void deathDialog( String message, Exception ex ) {
    // send message to the handler that will get this dialog on the activity thread
    final Bundle bundle = new Bundle();
    bundle.putString( ERROR, message );
    bundle.putSerializable( EXCEPTION, ex );
    final Message msg = new Message();
    msg.setData(bundle);
    deathHandler.sendMessage(msg);
  }
  
  private void open() {
    String dbFilename = DATABASE_NAME;
    final boolean hasSD = ListActivity.hasSD();
    if ( hasSD ) {
      File path = new File( DATABASE_PATH );
      path.mkdirs();
      dbFilename = DATABASE_PATH + DATABASE_NAME;
    }
    final File dbFile = new File( dbFilename );
    boolean doCreateNetwork = false;
    boolean doCreateLocation = false;
    if ( ! dbFile.exists() ) {
      doCreateNetwork = true;
      doCreateLocation = true;
    }
    ListActivity.info("opening: " + dbFilename );
    
    if ( hasSD ) {
      db = SQLiteDatabase.openOrCreateDatabase( dbFilename, null );
    }
    else {      
      db = context.openOrCreateDatabase( dbFilename, MAX_PRIORITY, null );
    }
    
    try {
      db.rawQuery( "SELECT count(*) FROM network", (String[]) null ).close();
    }
    catch ( final SQLiteException ex ) {
      ListActivity.info("exception selecting from network, try to create. ex: " + ex );
      doCreateNetwork = true;
    }
    
    try {
      db.rawQuery( "SELECT count(*) FROM location", (String[]) null ).close();
    }
    catch ( final SQLiteException ex ) {
      ListActivity.info("exception selecting from location, try to create. ex: " + ex );
      doCreateLocation = true;
    }
    
    if ( doCreateNetwork ) {
      ListActivity.info( "creating network table" );
      try {
        db.execSQL(NETWORK_CREATE);
      }
      catch ( final SQLiteException ex ) {
        ListActivity.error( "sqlite exception: " + ex, ex );
      }
    }
    
    if ( doCreateLocation ) {
      ListActivity.info( "creating location table" );
      try {
        db.execSQL(LOCATION_CREATE);
        // new database, reset a marker, if any
        final Editor edit = prefs.edit();
        edit.putLong( ListActivity.PREF_DB_MARKER, 0L );
        edit.commit();
      }
      catch ( final SQLiteException ex ) {
        ListActivity.error( "sqlite exception: " + ex, ex );
      }
    }
    
    // VACUUM turned off, this takes a long long time (20 min), and has little effect since we're not using DELETE
    // ListActivity.info("Vacuuming db");
    // db.execSQL( "VACUUM" );
    // ListActivity.info("Vacuuming done");
    
    // we don't need to know how many we wrote
    db.execSQL( "PRAGMA count_changes = false" );
    // keep transactions in memory until committed
    db.execSQL( "PRAGMA temp_store = MEMORY" );
    // keep around the journal file, don't create and delete a ton of times
    db.rawQuery( "PRAGMA journal_mode = PERSIST", (String[]) null ).close();
    
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
      ListActivity.info( "db still alive. countdown: " + countdown );
      ListActivity.sleep( 100L );
      countdown--;
      this.interrupt();
    }
    
    countdown = 50;
    while ( db.isOpen() && countdown > 0 ) {
      try {
        synchronized ( this ) {
          if ( insertNetwork != null ) {
            insertNetwork.close();
          }
          if ( insertLocation != null ) {
            insertLocation.close();
          }
          if ( updateNetwork != null ) {
            updateNetwork.close();
          }
          if ( db.isOpen() ) {
            db.close();
          }
        }
      }
      catch ( SQLiteException ex ) {
        ListActivity.info( "db close exception, will try again. countdown: " + countdown + " ex: " + ex, ex );
        ListActivity.sleep( 100L );
      }
    }
  }
  
  public synchronized void checkDB() {
    if ( db == null || ! db.isOpen() ) {
      ListActivity.info( "re-opening db in checkDB" );
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
      ListActivity.info( "queue full, not adding: " + network.getBssid() + " ssid: " + network.getSsid() );
      if ( System.currentTimeMillis() - prevQueueCullTime > QUEUE_CULL_TIMEOUT ) {
        ListActivity.info("culling queue. size: " + queue.size() );
        // go thru the queue, cull out anything not newForRun
        for ( Iterator<DBUpdate> it = queue.iterator(); it.hasNext(); ) {
          final DBUpdate val = it.next();
          if ( ! val.newForRun ) {
            it.remove();
          }
        }
        ListActivity.info("culled queue. size now: " + queue.size() );
        added = queue.offer( update );
        if ( ! added ) {
          ListActivity.info( "queue still full, couldn't add: " + network.getBssid() );
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
      // ListActivity.info( "db cache hit. bssid: " + network.getBssid() );
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
        // ListActivity.info("db using cursor values: " + network.getBssid() );
        cursor.moveToFirst();
        lasttime = cursor.getLong(0);
        lastlat = cursor.getDouble(1);
        lastlon = cursor.getDouble(2);
      }
      try {
        cursor.close();
      }
      catch ( NoSuchElementException ex ) {
        // weird error cropping up
        ListActivity.info("the weird close-cursor exception: " + ex );
      }
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
    // ListActivity.info( "lasttime: " + lasttime + " now: " + now + " ssid: " + network.getSsid() 
    //    + " lastlat: " + lastlat + " lat: " + location.getLatitude() 
    //    + " lastlon: " + lastlon + " lon: " + location.getLongitude() );
    final boolean changeWorthy = mediumChange || (now - lasttime > SMALL_LOC_DELAY && smallChange);
    
    if ( ListActivity.DEBUG ) {
      // do lots of inserts when debug is on
      isNew = true;
    }

    if ( isNew || bigChange || (! fastMode && changeWorthy ) ) {
      // ListActivity.info("inserting loc: " + network.getSsid() );
      insertLocation.bindString( 1, bssid );
      insertLocation.bindLong( 2, update.level );  // make sure to use the update's level, network's is mutable...
      insertLocation.bindDouble( 3, location.getLatitude() );
      insertLocation.bindDouble( 4, location.getLongitude() );
      insertLocation.bindDouble( 5, location.getAltitude() );
      insertLocation.bindDouble( 6, location.getAccuracy() );
      insertLocation.bindLong( 7, location.getTime() );
      if ( db.isDbLockedByOtherThreads() ) {
        // this is kinda lame, make this better
        ListActivity.error( "db locked by another thread, waiting to loc insert. bssid: " + bssid
            + " drainSize: " + drainSize );
        ListActivity.sleep(1000L);
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
          ListActivity.error( "db locked by another thread, waiting to net update. bssid: " + bssid
              + " drainSize: " + drainSize );
          ListActivity.sleep(1000L);
        }
        start = System.currentTimeMillis();
        // UPDATE
        updateNetwork.execute();
        logTime( start, "db network updated" );
      }
    }
    else {
      // ListActivity.info( "db network not changeworthy: " + bssid );
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
          ListActivity.info("culling pending queue. size: " + pending.size() );
          // go thru the queue, cull out anything not newForRun
          for ( Iterator<DBPending> it = pending.iterator(); it.hasNext(); ) {
            final DBPending val = it.next();
            if ( ! val.newForRun ) {
              it.remove();
            }
          }
          ListActivity.info("culled pending queue. size now: " + pending.size() );
          added = pending.offer( update );
          if ( ! added ) {
            ListActivity.info( "pending queue still full, couldn't add: " + network.getBssid() );
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
              ListActivity.info( "pending queue still full post-dup-purge, couldn't add: " + network.getBssid() );
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
      ListActivity.info( "moved " + accuracy + "m without a GPS fix, over " + d_time + "s" );
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
        //        ListActivity.info( "interpolated to ("+lerp_lat+","+lerp_lon+")" );

        // throw it on the queue!
        if ( addObservation( pend.network, pend.level, lerpLoc, pend.newForRun ) ) {
          count++;
        } else {
          ListActivity.info( "failed to add "+pend );
        }
        // XXX: altitude? worth it?
      }
      // return
      ListActivity.info( "recovered "+count+" location"+(count==1?"":"s")+" with the power of lerp");
    }

    lastLoc = null;
    return count;
  }

  private void logTime( final long start, final String string ) {
    long diff = System.currentTimeMillis() - start;
    if ( diff > 150L ) {
      ListActivity.info( string + " in " + diff + " ms" );
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
    Network retval = ListActivity.getNetworkCache().get( bssid );
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
        // final NetworkType type = NetworkType.meh( cursor.getString(3) );
        final NetworkType type = NetworkType.WIFI;
        retval = new Network( bssid, ssid, frequency, capabilities, 0, type );
        ListActivity.getNetworkCache().put( bssid, retval );
      }
      cursor.close();
    }
    return retval;
  }
  
  public Cursor locationIterator( final long fromId ) {
    checkDB();
    ListActivity.info( "locationIterator fromId: " + fromId );
    final String[] args = new String[]{ Long.toString( fromId ) };
    return db.rawQuery( "SELECT _id,bssid,level,lat,lon,altitude,accuracy,time FROM location WHERE _id > ?", args );
  }
  
  public Cursor networkIterator() {
    checkDB();
    ListActivity.info( "networkIterator" );
    final String[] args = new String[]{};
    return db.rawQuery( "SELECT bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon FROM network", args );
  }
  
  public Cursor getSingleNetwork( final String bssid ) {
    checkDB();
    final String[] args = new String[]{bssid};
    return db.rawQuery( 
        "SELECT bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon FROM network WHERE bssid = ?", args );
  }
  
}
