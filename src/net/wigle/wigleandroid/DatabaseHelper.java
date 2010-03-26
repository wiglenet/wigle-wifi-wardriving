package net.wigle.wigleandroid;

import java.io.File;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;

/**
 * our database
 */
public class DatabaseHelper {
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
  
  private static final String UPLOAD_TABLE = "upload";
  private static final String UPLOAD_CREATE = 
    "create table " + UPLOAD_TABLE + " ( "
    + "key integer primary key not null,"
    + "lastupload long not null"
    + ")";
  
  private SQLiteDatabase db;
  
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
      db.execSQL(UPLOAD_CREATE);
    }
  }
  
  public void close() {
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
    checkDB();
    ContentValues values = new ContentValues();
    String[] args = new String[]{ network.getBssid() };    
    Cursor cursor = db.rawQuery("SELECT bssid,lasttime FROM network WHERE bssid = ?", args );
    long lasttime;
    if ( cursor.getCount() == 0 ) {    
      // WigleAndroid.debug("inserting net: " + network.getSsid() );
      
      values.put("bssid", network.getBssid() );
      values.put("ssid", network.getSsid() );
      values.put("frequency", network.getFrequency() );
      values.put("capabilities", network.getCapabilities() );
      values.put("lasttime", location.getTime() );
      db.replace(NETWORK_TABLE, null, values);
      
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
      // WigleAndroid.debug("inserting loc: " + network.getSsid() );
      values.clear();
      values.put("bssid", network.getBssid() );
      values.put("level", network.getLevel() );
      values.put("lat", location.getLatitude() );
      values.put("lon", location.getLongitude() );
      values.put("altitude", location.getAltitude() );
      values.put("accuracy", location.getAccuracy() );
      values.put("time", location.getTime() );
      db.insert( LOCATION_TABLE, null, values );
      
      // update the network with the lasttime
      values.clear();
      values.put("lasttime", location.getTime() );
      args = new String[]{ network.getBssid() };
      db.update( NETWORK_TABLE, values, "bssid = ?", args );
    }
  }
  
  public long getNetworkCount() {
    checkDB();
    Cursor cursor = db.rawQuery("select count(*) FROM " + NETWORK_TABLE, null);
    cursor.moveToFirst();
    long count = cursor.getLong( 0 );
    cursor.close();
    return count;
  }
  
  public long getLocationCount() {
    checkDB();
    Cursor cursor = db.rawQuery("select count(*) FROM " + LOCATION_TABLE, null);
    cursor.moveToFirst();
    long count = cursor.getLong( 0 );
    cursor.close();
    return count;
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
  
  public long getLastUpload() {
    checkDB();
    Cursor cursor = db.rawQuery("SELECT lastupload FROM " + UPLOAD_TABLE + " WHERE key = 0", null);
    long maxId = -1L;
    if ( cursor.getCount() > 0 ) {
      cursor.moveToFirst();
      maxId = cursor.getLong(0);
    }
    cursor.close();
    return maxId;
  }
  
  public void lastUpload( long maxId ) {
    checkDB();
    WigleAndroid.info("updating lastUpload maxId: " + maxId );
    
    ContentValues values = new ContentValues();
    values.put( "key", 0);
    values.put("lastupload", maxId);
    db.replace(UPLOAD_TABLE, null, values);
  }
  
  public Cursor networkIterator( long fromId ) {
    checkDB();
    WigleAndroid.info("networkIterator fromId: " + fromId );
    String[] args = new String[]{ Long.toString( fromId ) };
    return db.rawQuery("SELECT _id,bssid,level,lat,lon,altitude,accuracy,time FROM location WHERE _id > ?", args);
  }
  
}
