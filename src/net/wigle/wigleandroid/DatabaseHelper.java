package net.wigle.wigleandroid;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.os.Environment;

/**
 * our database
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
  private final Context context;
  private final BlockingQueue<DBUpdate> queue = new LinkedBlockingQueue<DBUpdate>( MAX_QUEUE );
  private final AtomicBoolean done = new AtomicBoolean(false);
  private final AtomicLong networkCount = new AtomicLong();
  private final AtomicLong locationCount = new AtomicLong();
  private final AtomicLong newNetworkCount = new AtomicLong();
  private final SharedPreferences prefs;
  /** used in private addObservation */
  private final CacheMap<String,Location> previousWrittenLocationsCache = 
    new CacheMap<String,Location>( 16, 64 );
  
  /** class for queueing updates to the database */
  final class DBUpdate {
    public final Network network;
    public final int level;
    public final Location location;
    
    public DBUpdate( final Network network, final int level, final Location location ) {
      this.network = network;
      this.level = level;
      this.location = location;
    }
  }
  
  public DatabaseHelper( final Context context ) {    
    this.context = context;
    this.prefs = context.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0 );
  }

	public int getQueueSize() {
		return queue.size();
	}
  
  @Override
  public void run() {
    try {
      WigleAndroid.info( "starting db thread" );    
      getNetworkCountFromDB();
      getLocationCountFromDB();
      
      while ( ! done.get() ) {
        try {
          addObservation( queue.take() );
        }
        catch ( final InterruptedException ex ) {
          // no worries
          WigleAndroid.info("db queue take interrupted");
        }
      }
    }
    catch ( final Throwable throwable ) {
      WigleAndroid.writeError( Thread.currentThread(), throwable );
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
        WigleAndroid.error( "sqlite exception: " + ex );
      }
    }
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
  
  public void addObservation( final Network network, final Location location ) {
    final DBUpdate update = new DBUpdate( network, network.getLevel(), location );
    boolean complete = false;
    while ( ! complete ) {
      try {
        queue.put( update );
        complete = true;
      }
      catch ( final InterruptedException ex ) {
        WigleAndroid.info( "interrupted in main addObservation: " + ex ); 
      }
    }
  }
  
  private void addObservation( final DBUpdate update ) {
    checkDB();
    final Network network = update.network;
    final Location location = update.location;
    final ContentValues values = new ContentValues();
    final String[] bssidArgs = new String[]{ network.getBssid() }; 
    
    long lasttime = 0;
    double lastlat = 0;
    double lastlon = 0;
    boolean isNew = false;
    
    // first try cache
    final Location prevWrittenLocation = previousWrittenLocationsCache.get( network.getBssid() );
    if ( prevWrittenLocation != null ) {
      // cache hit!
      lasttime = prevWrittenLocation.getTime();
      lastlat = prevWrittenLocation.getLatitude();
      lastlon = prevWrittenLocation.getLongitude();
    }
    else {
      // cache miss, get the last values from the db, if any
      final Cursor cursor = db.rawQuery("SELECT bssid,lasttime,lastlat,lastlon FROM network WHERE bssid = ?", bssidArgs );
      if ( cursor.getCount() == 0 ) {    
        // WigleAndroid.info("inserting net: " + network.getSsid() );
        values.put("bssid", network.getBssid() );
        values.put("ssid", network.getSsid() );
        values.put("frequency", network.getFrequency() );
        values.put("capabilities", network.getCapabilities() );
        values.put("lasttime", location.getTime() );
        values.put("lastlat", location.getLatitude() );
        values.put("lastlon", location.getLongitude() );
        db.insert(NETWORK_TABLE, null, values);
        
        // update the count
        networkCount.incrementAndGet();
        isNew = true;
        
        // to make sure this new network's location is written
        // don't update stack lasttime,lastlat,lastlon variables
      }
      else {
        cursor.moveToFirst();
        lasttime = cursor.getLong(1);
        lastlat = cursor.getDouble(2);
        lastlon = cursor.getDouble(3);
      }
      cursor.close();
    }
    
    if ( isNew ) {
      newNetworkCount.incrementAndGet();
    }
    
    boolean fastMode = false;
    if ( (queue.size() * 100) / MAX_QUEUE > 75 ) {
      // queue is filling up, go to fast mode, only write new networks or big changes
      fastMode = true;
    }       
    
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
    if ( isNew || bigChange || (! fastMode && changeWorthy ) ) {
      // WigleAndroid.info("inserting loc: " + network.getSsid() );
      values.clear();
      values.put("bssid", network.getBssid() );
      values.put("level", update.level );  // make sure to use the update's level, network's is mutable...
      values.put("lat", location.getLatitude() );
      values.put("lon", location.getLongitude() );
      values.put("altitude", location.getAltitude() );
      values.put("accuracy", location.getAccuracy() );
      values.put("time", location.getTime() );
      if ( db.isDbLockedByOtherThreads() ) {
        // this is kinda lame, make this better
        WigleAndroid.error("db locked by another thread, waiting");
        WigleAndroid.sleep(1000L);
      }
      db.insert( LOCATION_TABLE, null, values );
      
      // update the count
      locationCount.incrementAndGet();
      // update the cache
      previousWrittenLocationsCache.put( network.getBssid(), location );
      
      if ( ! isNew ) {
        // update the network with the lasttime,lastlat,lastlon
        values.clear();
        values.put("lasttime", location.getTime() );
        values.put("lastlat", location.getLatitude() );
        values.put("lastlon", location.getLongitude() );
        if ( db.isDbLockedByOtherThreads() ) {
          // this is kinda lame, make this better
          WigleAndroid.error("db locked by another thread, waiting");
          WigleAndroid.sleep(1000L);
        }
        db.update( NETWORK_TABLE, values, "bssid = ?", bssidArgs );
      }
      
    }
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
