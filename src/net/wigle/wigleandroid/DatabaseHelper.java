package net.wigle.wigleandroid;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/**
 * our database
 */
public class DatabaseHelper {
  private static final String DATABASE_NAME = "/sdcard/wiglewifi/wiflewifi.sqlite";
  private static final String DATABASE_TABLE = "observation_test";
  private static final String DATABASE_CREATE =
    "create table observation_test ( "
    + "_id integer primary key autoincrement, "
    + "bssid varchar(20) not null,"
    + "ssid text not null"
    +");";
  
  private SQLiteDatabase db;
  
  public void open() {
    db = SQLiteDatabase.openOrCreateDatabase( DATABASE_NAME, null );
    // check if we have tables
//    db.rawQuery("select * from sqlite_master where type='table';", selectionArgs);
    db.execSQL(DATABASE_CREATE);
  }
  
  public void close() {
    db.close();
  }
  
  public void addObservation( Network network, Observation observation ) {
    ContentValues values = new ContentValues();
    values.put( "bssid", network.getBssid() );
    values.put( "ssid", network.getSsid() );

    db.insert(DATABASE_TABLE, null, values);
  }
}
