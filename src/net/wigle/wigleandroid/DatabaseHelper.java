package net.wigle.wigleandroid;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;

/**
 * our database
 */
public class DatabaseHelper extends Thread {
  private static final long LOC_DELAY = 10000L;
  private static final String DATABASE_NAME = "wiglewifi.sqlite";
  private static final String DATABASE_PATH = "/sdcard/wiglewifi/";
  
  private static final String NETWORK_TABLE = "network";
  private static final String NETWORK_CREATE =
    "create table " + NETWORK_TABLE + " ( "
    + "bssid varchar(20) primary key not null,"
    + "ssid text not null,"
    + "frequency int not null,"
    + "capabilities text not null,"
    + "lasttime long not null"
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
  private BlockingQueue<DBUpdate> queue = new LinkedBlockingQueue<DBUpdate>( 256 );
  private AtomicBoolean done = new AtomicBoolean(false);
  private AtomicLong networkCount = new AtomicLong();
  private AtomicLong locationCount = new AtomicLong();
  
  public class DBUpdate {
    public Network network;
    public int level;
    public Location location;
  }
  
  public DatabaseHelper() {
    // not much here
  }
  
  @Override
  public void run() {
    WigleAndroid.info( "starting db thread" );
    getNetworkCountFromDB();
    getLocationCountFromDB();
    
    while ( ! done.get() ) {
      try {
        DBUpdate update = queue.take();
        addObservation( update );
      }
      catch ( InterruptedException ex ) {
        // no worries
        WigleAndroid.info("db queue take interrupted");
      }
    }
  }
  
  public void open() {
    File sdCard = new File("/sdcard/");
    boolean hasSD = sdCard.exists() && sdCard.isDirectory();
    String dbFilename = DATABASE_NAME;
    if ( hasSD ) {
      File path = new File( DATABASE_PATH );
      path.mkdirs();
      dbFilename = DATABASE_PATH + DATABASE_NAME;
    }
    File dbFile = new File( dbFilename );
    boolean doCreate = false;
    if ( ! dbFile.exists() ) {
      doCreate = true;
    }
    WigleAndroid.info("opening: " + dbFilename );
    db = SQLiteDatabase.openOrCreateDatabase( dbFilename, null );
    if ( doCreate ) {
      WigleAndroid.info( "creating tables" );
      db.execSQL(NETWORK_CREATE);
      db.execSQL(LOCATION_CREATE);
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
      WigleAndroid.info("db still alive. countdown: " + countdown );
      WigleAndroid.sleep(100L);
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
  
  public void addObservation( Network network, Location location ) {
    DBUpdate update = new DBUpdate();
    update.network = network;
    update.location = location;
    update.level = network.getLevel();
    boolean complete = false;
    while ( ! complete ) {
      try {
        queue.put( update );
        complete = true;
      }
      catch ( InterruptedException ex ) {
        WigleAndroid.info( "interrupted in main addObservation: " + ex ); 
      }
    }
  }
  
  private void addObservation( DBUpdate update ) {
    checkDB();
    Network network = update.network;
    Location location = update.location;
    
    ContentValues values = new ContentValues();
    String[] bssidArgs = new String[]{ network.getBssid() };    
    Cursor cursor = db.rawQuery("SELECT bssid,lasttime FROM network WHERE bssid = ?", bssidArgs );
    long lasttime;
    if ( cursor.getCount() == 0 ) {    
      // WigleAndroid.info("inserting net: " + network.getSsid() );
      
      values.put("bssid", network.getBssid() );
      values.put("ssid", network.getSsid() );
      values.put("frequency", network.getFrequency() );
      values.put("capabilities", network.getCapabilities() );
      values.put("lasttime", location.getTime() );
      db.insert(NETWORK_TABLE, null, values);
      
      // update the count
      getNetworkCountFromDB();
      
      lasttime = location.getTime();
    }
    else {
      cursor.moveToFirst();
      lasttime = cursor.getLong(1);
    }
    cursor.close();
    
    long now = System.currentTimeMillis();
    // WigleAndroid.debug("time: " + time + " now: " + now + " ssid: " + network.getSsid() );
    if ( now - lasttime > LOC_DELAY ) {
      // WigleAndroid.info("inserting loc: " + network.getSsid() );
      values.clear();
      values.put("bssid", network.getBssid() );
      values.put("level", update.level );  // make sure to use the level's update, network's is mutable...
      values.put("lat", location.getLatitude() );
      values.put("lon", location.getLongitude() );
      values.put("altitude", location.getAltitude() );
      values.put("accuracy", location.getAccuracy() );
      values.put("time", location.getTime() );
      db.insert( LOCATION_TABLE, null, values );
      
      // update the network with the lasttime
      values.clear();
      values.put("lasttime", location.getTime() );
      db.update( NETWORK_TABLE, values, "bssid = ?", bssidArgs );
      
      // update the count
      getLocationCountFromDB();
    }
  }
  
  public long getNetworkCount() {
    return networkCount.get();
  }
  private void getNetworkCountFromDB() {
    checkDB();
    Cursor cursor = db.rawQuery("select count(*) FROM " + NETWORK_TABLE, null);
    cursor.moveToFirst();
    long count = cursor.getLong( 0 );
    cursor.close();
    networkCount.set( count );
  }
  
  public long getLocationCount() {
    return locationCount.get();
  }
  private void getLocationCountFromDB() {
    checkDB();
    Cursor cursor = db.rawQuery("select count(*) FROM " + LOCATION_TABLE, null);
    cursor.moveToFirst();
    long count = cursor.getLong( 0 );
    cursor.close();
    locationCount.set( count );
  }
  
  public Network getNetwork( String bssid ) {
    checkDB();
    Network retval = null;
    String[] args = new String[]{ bssid };
    Cursor cursor = db.rawQuery("select ssid,frequency,capabilities FROM " + NETWORK_TABLE 
        + " WHERE bssid = ?", args);
    if ( cursor.getCount() > 0 ) {
      cursor.moveToFirst();
      String ssid = cursor.getString(0);
      int frequency = cursor.getInt(1);
      String capabilities = cursor.getString(2);
      retval = new Network( bssid, ssid, frequency, capabilities, 0 );
    }
    cursor.close();
    return retval;
  }
  
  public Cursor networkIterator( long fromId ) {
    checkDB();
    WigleAndroid.info("networkIterator fromId: " + fromId );
    String[] args = new String[]{ Long.toString( fromId ) };
    return db.rawQuery("SELECT _id,bssid,level,lat,lon,altitude,accuracy,time FROM location WHERE _id > ?", args);
  }
  
}
