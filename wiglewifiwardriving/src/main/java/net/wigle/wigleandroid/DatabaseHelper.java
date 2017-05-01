// -*- Mode: Java; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
// vim:ts=2:sw=2:tw=80:et

package net.wigle.wigleandroid;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.wigle.wigleandroid.DataFragment.BackupTask;
import net.wigle.wigleandroid.background.QueryThread;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.Pair;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;

import com.google.android.gms.maps.model.LatLng;

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
    private static final int LEVEL_CHANGE = 5;
    private static final String DATABASE_NAME = "wiglewifi.sqlite";
    private static final String DATABASE_PATH = Environment.getExternalStorageDirectory() + "/wiglewifi/";
    private static final int DB_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND;
    private static final Object TRANS_LOCK = new Object();

    private static final long QUEUE_CULL_TIMEOUT = 10000L;
    private long prevQueueCullTime = 0L;
    private long prevPendingQueueCullTime = 0L;

    private SQLiteStatement insertNetwork;
    private SQLiteStatement insertLocation;
    private SQLiteStatement updateNetwork;
    private SQLiteStatement updateNetworkMetadata;

    public static final String NETWORK_TABLE = "network";
    private static final String NETWORK_CREATE =
            "create table " + NETWORK_TABLE + " ( "
                    + "bssid text primary key not null,"
                    + "ssid text not null,"
                    + "frequency int not null,"
                    + "capabilities text not null,"
                    + "lasttime long not null,"
                    + "lastlat double not null,"
                    + "lastlon double not null,"
                    + "type text not null default '" + NetworkType.WIFI.getCode() + "',"
                    + "bestlevel integer not null default 0,"
                    + "bestlat double not null default 0,"
                    + "bestlon double not null default 0"
                    + ")";

    public static final String LOCATION_TABLE = "location";
    private static final String LOCATION_CREATE =
            "create table " + LOCATION_TABLE + " ( "
                    + "_id integer primary key autoincrement,"
                    + "bssid text not null,"
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
    private final ArrayBlockingQueue<DBUpdate> queue = new ArrayBlockingQueue<>(MAX_QUEUE);
    private final ArrayBlockingQueue<DBPending> pending = new ArrayBlockingQueue<>(MAX_QUEUE); // how to size this better?
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final AtomicLong networkCount = new AtomicLong();
    private final AtomicLong locationCount = new AtomicLong();
    private final AtomicLong newNetworkCount = new AtomicLong();
    private final AtomicLong newWifiCount = new AtomicLong();
    private final AtomicLong newCellCount = new AtomicLong();
    private final QueryThread queryThread;

    private Location lastLoc = null;
    private long lastLocWhen = 0L;
    private final DeathHandler deathHandler;
    private final SharedPreferences prefs;

    /** used in private addObservation */
    private final ConcurrentLinkedHashMap<String,CachedLocation> previousWrittenLocationsCache =
            new ConcurrentLinkedHashMap<>(64);

    private final static class CachedLocation {
        public Location location;
        public int bestlevel;
        public double bestlat;
        public double bestlon;
    }

    /** class for queueing updates to the database */
    final static class DBUpdate {
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
    final static class DBPending {
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
        this.prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        setName("dbworker-" + getName());
        this.deathHandler = new DeathHandler();

        queryThread = new QueryThread( this );
        queryThread.start();
    }

    public SQLiteDatabase getDB() throws DBException {
        checkDB();
        return db;
    }

    public void addToQueue( QueryThread.Request request ) {
        queryThread.addToQueue( request );
    }

    private static class DeathHandler extends Handler {
        private boolean fired = false;

        public DeathHandler() {
        }

        @Override
        public void handleMessage( final Message msg ) {
            if ( fired ) {
                return;
            }
            fired = true;

            final Bundle bundle = msg.peekData();
            String error = "unknown";
            if ( bundle == null ) {
                MainActivity.error("no bundle in msg: " + msg);
            }
            else {
                error = bundle.getString( ERROR );
            }

            final MainActivity mainActivity = MainActivity.getMainActivity();
            final Intent errorReportIntent = new Intent( mainActivity, ErrorReportActivity.class );
            errorReportIntent.putExtra( MainActivity.ERROR_REPORT_DIALOG, error );
            mainActivity.startActivity( errorReportIntent );
        }
    }

    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public void run() {
        try {
            MainActivity.info( "starting db thread" );

            MainActivity.info( "setting db thread priority (-20 highest, 19 lowest) to: " + DB_PRIORITY );
            Process.setThreadPriority( DB_PRIORITY );

            try {
                // keep checking done, these counts take a while
                if ( ! done.get() ) {
                    getNetworkCountFromDB();
                }
                if ( ! done.get() ) {
                    getLocationCountFromDB();
                }
//        if ( ! done.get() ) {
//          MainActivity.info("gsm count: " + getNetworkCountFromDB(NetworkType.GSM));
//        }
//        if ( ! done.get() ) {
//          MainActivity.info("cdma count: " + getNetworkCountFromDB(NetworkType.CDMA));
//        }
            }
            catch ( DBException ex ) {
                deathDialog( "getting counts from DB", ex );
            }

            final List<DBUpdate> drain = new ArrayList<>();
            while ( ! done.get() ) {
                try {
                    checkDB();
                    drain.clear();
                    drain.add( queue.take() );
                    final long startTime = System.currentTimeMillis();

                    // give other thread some time
                    Thread.yield();

                    // now that we've taken care of the one, see if there's more we can do in this transaction
                    // try to drain some more
                    queue.drainTo( drain, MAX_DRAIN - 1 );
                    final int drainSize = drain.size();

                    int countdown = 10;
                    while ( countdown > 0 && ! done.get() ) {
                        // doubt this will help the exclusive lock problems, but trying anyway
                        synchronized(TRANS_LOCK) {
                            try {
                                // do a transaction for everything
                                db.beginTransaction();
                                for ( int i = 0; i < drainSize; i++ ) {
                                    addObservation( drain.get( i ), drainSize );
                                }
                                db.setTransactionSuccessful();
                                db.endTransaction();
                                countdown = 0;
                            }
                            catch ( SQLiteConstraintException ex ) {
                                MainActivity.warn("DB run loop constraint ex, countdown: " + countdown + " ex: " + ex );
                                countdown--;
                            }
                            catch ( Exception ex ) {
                                MainActivity.warn("DB run loop ex, countdown: " + countdown + " ex: " + ex );
                                countdown--;
                                if ( countdown <= 0 ) {
                                    // give up
                                    throw ex;
                                }
                                MainActivity.sleep(100L);
                            }
                        }
                    }

                    final long delay = System.currentTimeMillis() - startTime;
                    if ( delay > 1000L ) {
                        MainActivity.info( "db run loop took: " + delay + " ms. drainSize: " + drainSize );
                    }
                }
                catch ( final InterruptedException ex ) {
                    // no worries
                    MainActivity.info("db queue take interrupted");
                }
                catch ( IllegalStateException | SQLiteException | DBException ex ) {
                    if ( ! done.get() ) {
                        deathDialog( "DB run loop", ex );
                    }
                    MainActivity.sleep(100L);
                } finally {
                    if ( db != null && db.isOpen() && db.inTransaction() ) {
                        try {
                            db.endTransaction();
                        }
                        catch ( Exception ex ) {
                            MainActivity.error( "exception in db.endTransaction: " + ex, ex );
                        }
                    }
                }
            }
        }
        catch ( final Throwable throwable ) {
            MainActivity.writeError( Thread.currentThread(), throwable, context );
            throw new RuntimeException( "DatabaseHelper throwable: " + throwable, throwable );
        }

        MainActivity.info("db worker thread shutting down");
    }

    public void deathDialog( String message, Exception ex ) {
        // send message to the handler that will get this dialog on the activity thread
        MainActivity.error( "db exception. " + message + ": " + ex, ex );
        MainActivity.writeError(Thread.currentThread(), ex, context);
        final Bundle bundle = new Bundle();
        final String dialogMessage = MainActivity.getBaseErrorMessage( ex, true );
        bundle.putString( ERROR, dialogMessage );
        bundle.putSerializable( EXCEPTION, ex );
        final Message msg = new Message();
        msg.setData(bundle);
        deathHandler.sendMessage(msg);
    }

    private void open() {
        // if(true) throw new SQLiteException("meat puppets");

        String dbFilename = DATABASE_NAME;
        final boolean hasSD = MainActivity.hasSD();
        if ( hasSD ) {
            File path = new File( DATABASE_PATH );
            //noinspection ResultOfMethodCallIgnored
            path.mkdirs();
            dbFilename = DATABASE_PATH + DATABASE_NAME;
        }
        final File dbFile = new File( dbFilename );
        boolean doCreateNetwork = false;
        boolean doCreateLocation = false;
        if ( ! dbFile.exists() && hasSD ) {
            doCreateNetwork = true;
            doCreateLocation = true;
        }
        MainActivity.info("opening: " + dbFilename );

        if ( hasSD ) {
            db = SQLiteDatabase.openOrCreateDatabase( dbFilename, null );
        }
        else {
            db = context.openOrCreateDatabase( dbFilename, Context.MODE_PRIVATE, null );
        }

        try {
            db.rawQuery( "SELECT count(*) FROM network", null).close();
        }
        catch ( final SQLiteException ex ) {
            MainActivity.info("exception selecting from network, try to create. ex: " + ex );
            doCreateNetwork = true;
        }

        try {
            db.rawQuery( "SELECT count(*) FROM location", null).close();
        }
        catch ( final SQLiteException ex ) {
            MainActivity.info("exception selecting from location, try to create. ex: " + ex );
            doCreateLocation = true;
        }

        if ( doCreateNetwork ) {
            MainActivity.info( "creating network table" );
            try {
                db.execSQL(NETWORK_CREATE);
                if ( db.getVersion() == 0 ) {
                    // only diff to version 1 is the "type" column in network table
                    db.setVersion(1);
                }
                if ( db.getVersion() == 1 ) {
                    // only diff to version 2 is the "bestlevel", "bestlat", "bestlon" columns in network table
                    db.setVersion(2);
                }
            }
            catch ( final SQLiteException ex ) {
                MainActivity.error( "sqlite exception: " + ex, ex );
            }
        }

        if ( doCreateLocation ) {
            MainActivity.info( "creating location table" );
            try {
                db.execSQL(LOCATION_CREATE);
                // new database, reset a marker, if any
                final Editor edit = prefs.edit();
                edit.putLong( ListFragment.PREF_DB_MARKER, 0L );
                edit.apply();
            }
            catch ( final SQLiteException ex ) {
                MainActivity.error( "sqlite exception: " + ex, ex );
            }
        }

        // VACUUM turned off, this takes a long long time (20 min), and has little effect since we're not using DELETE
        // MainActivity.info("Vacuuming db");
        // db.execSQL( "VACUUM" );
        // MainActivity.info("Vacuuming done");

        // we don't need to know how many we wrote
        db.execSQL( "PRAGMA count_changes = false" );
        // keep transactions in memory until committed
        db.execSQL( "PRAGMA temp_store = MEMORY" );
        // keep around the journal file, don't create and delete a ton of times
        db.rawQuery( "PRAGMA journal_mode = PERSIST", null).close();

        MainActivity.info( "database version: " + db.getVersion() );
        if ( db.getVersion() == 0 ) {
            MainActivity.info("upgrading db from 0 to 1");
            try {
                db.execSQL( "ALTER TABLE network ADD COLUMN type text not null default '" + NetworkType.WIFI.getCode() + "'" );
                db.setVersion(1);
            }
            catch ( SQLiteException ex ) {
                MainActivity.info("ex: " + ex, ex);
                if ( "duplicate column name".equals( ex.toString() ) ) {
                    db.setVersion(1);
                }
            }
        }
        else if ( db.getVersion() == 1 ) {
            MainActivity.info("upgrading db from 1 to 2");
            try {
                db.execSQL( "ALTER TABLE network ADD COLUMN bestlevel integer not null default 0" );
                db.execSQL( "ALTER TABLE network ADD COLUMN bestlat double not null default 0");
                db.execSQL( "ALTER TABLE network ADD COLUMN bestlon double not null default 0");
                db.setVersion(2);
            }
            catch ( SQLiteException ex ) {
                MainActivity.info("ex: " + ex, ex);
                if ( "duplicate column name".equals( ex.toString() ) ) {
                    db.setVersion(2);
                }
            }
        }

        // drop index, was never publically released
        db.execSQL("DROP INDEX IF EXISTS type");

        // compile statements
        insertNetwork = db.compileStatement( "INSERT INTO network"
                + " (bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon,type,bestlevel,bestlat,bestlon) VALUES (?,?,?,?,?,?,?,?,?,?,?)" );

        insertLocation = db.compileStatement( "INSERT INTO location"
                + " (bssid,level,lat,lon,altitude,accuracy,time) VALUES (?,?,?,?,?,?,?)" );

        updateNetwork = db.compileStatement( "UPDATE network SET"
                + " lasttime = ?, lastlat = ?, lastlon = ? WHERE bssid = ?" );

        updateNetworkMetadata = db.compileStatement( "UPDATE network SET"
                + " bestlevel = ?, bestlat = ?, bestlon = ?, ssid = ?, frequency = ?, capabilities = ? WHERE bssid = ?" );
    }

    /**
     * close db, shut down thread
     */
    public void close() {
        done.set( true );
        if (queryThread != null) {
            queryThread.setDone();
        }
        // interrupt the take, if any
        this.interrupt();
        // give time for db to finish any writes
        int countdown = 30;
        while ( this.isAlive() && countdown > 0 ) {
            MainActivity.info( "db still alive. countdown: " + countdown );
            MainActivity.sleep( 100L );
            countdown--;
            this.interrupt();
        }

        countdown = 50;
        while ( db != null && db.isOpen() && countdown > 0 ) {
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
                    if ( updateNetworkMetadata != null ) {
                        updateNetworkMetadata.close();
                    }
                    if ( db.isOpen() ) {
                        db.close();
                    }
                }
            }
            catch ( SQLiteException ex ) {
                MainActivity.info( "db close exception, will try again. countdown: " + countdown + " ex: " + ex, ex );
                MainActivity.sleep( 100L );
            }
            countdown--;
        }
    }

    public synchronized void checkDB() throws DBException {
        if ( db == null || ! db.isOpen() ) {
            MainActivity.info( "re-opening db in checkDB" );
            try {
                open();
            }
            catch ( SQLiteException ex ) {
                throw new DBException("checkDB", ex);
            }
        }
    }

    public void blockingAddObservation( final Network network, final Location location, final boolean newForRun )
            throws InterruptedException {

        final DBUpdate update = new DBUpdate( network, network.getLevel(), location, newForRun );
        queue.put(update);
    }

    public boolean addObservation( final Network network, final Location location, final boolean newForRun ) {
        try {
            return addObservation(network, network.getLevel(), location, newForRun);
        }
        catch (final IllegalMonitorStateException ex) {
            MainActivity.error("exception adding network: " + ex, ex);
        }
        return false;
    }

    private boolean addObservation( final Network network, final int level, final Location location,
                                    final boolean newForRun ) {

        final DBUpdate update = new DBUpdate( network, level, location, newForRun );

        // data is lost if queue is full!
        boolean added = queue.offer( update );
        if ( ! added ) {
            MainActivity.info( "queue full, not adding: " + network.getBssid() + " ssid: " + network.getSsid() );
            if ( System.currentTimeMillis() - prevQueueCullTime > QUEUE_CULL_TIMEOUT ) {
                MainActivity.info("culling queue. size: " + queue.size() );
                // go thru the queue, cull out anything not newForRun
                for ( Iterator<DBUpdate> it = queue.iterator(); it.hasNext(); ) {
                    final DBUpdate val = it.next();
                    if ( ! val.newForRun ) {
                        it.remove();
                    }
                }
                MainActivity.info("culled queue. size now: " + queue.size() );
                added = queue.offer( update );
                if ( ! added ) {
                    MainActivity.info( "queue still full, couldn't add: " + network.getBssid() );
                }
                prevQueueCullTime = System.currentTimeMillis();
            }

        }
        return added;
    }

    @SuppressWarnings("deprecation")
    private void addObservation( final DBUpdate update, final int drainSize ) throws DBException {
        checkDB();
        if (insertNetwork == null || insertLocation == null
                || updateNetwork == null || updateNetworkMetadata == null) {

            MainActivity.warn("A stored procedure is null, not adding observation");
            return;
        }
        final Network network = update.network;
        final Location location = update.location;
        final String bssid = network.getBssid();
        final String[] bssidArgs = new String[]{ bssid };

        long lasttime = 0;
        double lastlat = 0;
        double lastlon = 0;
        int bestlevel = 0;
        double bestlat = 0;
        double bestlon = 0;
        boolean isNew = false;

        // first try cache
        final CachedLocation prevWrittenLocation = previousWrittenLocationsCache.get( bssid );
        if ( prevWrittenLocation != null ) {
            // cache hit!
            lasttime = prevWrittenLocation.location.getTime();
            lastlat = prevWrittenLocation.location.getLatitude();
            lastlon = prevWrittenLocation.location.getLongitude();
            bestlevel = prevWrittenLocation.bestlevel;
            bestlat = prevWrittenLocation.bestlat;
            bestlon = prevWrittenLocation.bestlon;
            // MainActivity.info( "db cache hit. bssid: " + network.getBssid() );
        }
        else {
            // cache miss, get the last values from the db, if any
            long start = System.currentTimeMillis();
            // SELECT: can't precompile, as it has more than 1 result value
            final Cursor cursor = db.rawQuery("SELECT lasttime,lastlat,lastlon,bestlevel,bestlat,bestlon FROM network WHERE bssid = ?", bssidArgs );
            logTime( start, "db network queried " + bssid );
            if ( cursor.getCount() == 0 ) {
                insertNetwork.bindString( 1, bssid );
                insertNetwork.bindString( 2, network.getSsid() );
                insertNetwork.bindLong( 3, network.getFrequency() );
                insertNetwork.bindString( 4, network.getCapabilities() );
                insertNetwork.bindLong( 5, location.getTime() );
                insertNetwork.bindDouble( 6, location.getLatitude() );
                insertNetwork.bindDouble( 7, location.getLongitude() );
                insertNetwork.bindString( 8, network.getType().getCode() );
                insertNetwork.bindLong( 9, network.getLevel() );
                insertNetwork.bindDouble( 10, location.getLatitude() );
                insertNetwork.bindDouble( 11, location.getLongitude() );

                start = System.currentTimeMillis();
                // INSERT
                insertNetwork.execute();
                logTime( start, "db network inserted: " + bssid + " drainSize: " + drainSize );

                // update the count
                networkCount.incrementAndGet();
                isNew = true;

                final Network cacheNetwork = MainActivity.getNetworkCache().get( bssid );
                if (cacheNetwork != null) {
                    cacheNetwork.setIsNew();
                    MainActivity.updateNetworkOnMap(network);
                }

                // to make sure this new network's location is written
                // don't update stack lasttime,lastlat,lastlon variables
            }
            else {
                // MainActivity.info("db using cursor values: " + network.getBssid() );
                cursor.moveToFirst();
                lasttime = cursor.getLong(0);
                lastlat = cursor.getDouble(1);
                lastlon = cursor.getDouble(2);
                bestlevel = cursor.getInt(3);
                bestlat = cursor.getDouble(4);
                bestlon = cursor.getDouble(5);
            }
            try {
                cursor.close();
            }
            catch ( NoSuchElementException ex ) {
                // weird error cropping up
                MainActivity.info("the weird close-cursor exception: " + ex );
            }
        }

        if ( isNew ) {
            newNetworkCount.incrementAndGet();
            if ( NetworkType.WIFI.equals( network.getType() ) ) {
                newWifiCount.incrementAndGet();
            }
            else {
                newCellCount.incrementAndGet();
            }
        }

        final boolean fastMode = isFastMode();

        final long now = System.currentTimeMillis();
        final double latDiff = Math.abs(lastlat - location.getLatitude());
        final double lonDiff = Math.abs(lastlon - location.getLongitude());
        final boolean levelChange = bestlevel <= (update.level - LEVEL_CHANGE) ;
        final boolean smallChange = latDiff > SMALL_LATLON_CHANGE || lonDiff > SMALL_LATLON_CHANGE;
        final boolean mediumChange = latDiff > MEDIUM_LATLON_CHANGE || lonDiff > MEDIUM_LATLON_CHANGE;
        final boolean bigChange = latDiff > BIG_LATLON_CHANGE || lonDiff > BIG_LATLON_CHANGE;
        // MainActivity.info( "lasttime: " + lasttime + " now: " + now + " ssid: " + network.getSsid()
        //    + " lastlat: " + lastlat + " lat: " + location.getLatitude()
        //    + " lastlon: " + lastlon + " lon: " + location.getLongitude() );
        final boolean smallLocDelay = now - lasttime > SMALL_LOC_DELAY;
        final boolean changeWorthy = mediumChange || (smallLocDelay && smallChange) || levelChange;

        final boolean blank = location.getLatitude() == 0 && location.getLongitude() == 0
                && location.getAltitude() == 0 && location.getAccuracy() == 0
                && update.level == 0;

        /**
         * ALIBI: +/-infinite lat/long, 0 timestamp data (even with high accuracy) is gigo
         */
        final boolean likelyJunk = Double.isInfinite(location.getLatitude()) ||
                Double.isInfinite(location.getLongitude()) ||
                location.getTime() == 0L;

        /*
        //debugging path
        if (likelyJunk) {
            MainActivity.info(network.getSsid() + " " + bssid + ") blank: " + blank + "isNew: " + isNew + " bigChange: " + bigChange + " fastMode: " + fastMode
                        + " changeWorthy: " + changeWorthy + " mediumChange: " + mediumChange + " smallLocDelay: " + smallLocDelay
                        + " smallChange: " + smallChange + " latDiff: " + latDiff + " lonDiff: " + lonDiff);
        } */

        // MainActivity.info(network.getSsid() + " " + bssid + ") blank: " + blank + "isNew: " + isNew + " bigChange: " + bigChange + " fastMode: " + fastMode
        //    + " changeWorthy: " + changeWorthy + " mediumChange: " + mediumChange + " smallLocDelay: " + smallLocDelay
        //    + " smallChange: " + smallChange + " latDiff: " + latDiff + " lonDiff: " + lonDiff);

        if ( !blank && (isNew || bigChange || (! fastMode && changeWorthy )) ) {
            // MainActivity.info("inserting loc: " + network.getSsid() );
            insertLocation.bindString( 1, bssid );
            insertLocation.bindLong( 2, update.level );  // make sure to use the update's level, network's is mutable...
            insertLocation.bindDouble( 3, location.getLatitude() );
            insertLocation.bindDouble( 4, location.getLongitude() );
            insertLocation.bindDouble( 5, location.getAltitude() );
            insertLocation.bindDouble( 6, location.getAccuracy() );
            insertLocation.bindLong( 7, location.getTime() );
            if ( db.isDbLockedByOtherThreads() ) {
                // this is kinda lame, make this better
                MainActivity.error( "db locked by another thread, waiting to loc insert. bssid: " + bssid
                        + " drainSize: " + drainSize );
                MainActivity.sleep(1000L);
            }
            long start = System.currentTimeMillis();
            // INSERT
            insertLocation.execute();
            logTime( start, "db location inserted: " + bssid + " drainSize: " + drainSize );

            // update the count
            locationCount.incrementAndGet();
            // update the cache
            CachedLocation cached = new CachedLocation();
            cached.location = location;
            cached.bestlevel = update.level;
            cached.bestlat = location.getLatitude();
            cached.bestlon = location.getLongitude();
            previousWrittenLocationsCache.put( bssid, cached );

            if ( ! isNew ) {
                // update the network with the lasttime,lastlat,lastlon
                updateNetwork.bindLong( 1, location.getTime() );
                updateNetwork.bindDouble( 2, location.getLatitude() );
                updateNetwork.bindDouble( 3, location.getLongitude() );
                updateNetwork.bindString( 4, bssid );
                if ( db.isDbLockedByOtherThreads() ) {
                    // this is kinda lame, make this better
                    MainActivity.error( "db locked by another thread, waiting to net update. bssid: " + bssid
                            + " drainSize: " + drainSize );
                    MainActivity.sleep(1000L);
                }
                start = System.currentTimeMillis();
                // UPDATE
                updateNetwork.execute();
                logTime( start, "db network updated" );


                boolean newBest = (bestlevel == 0 || update.level > bestlevel) &&
                        // https://github.com/wiglenet/wigle-wifi-wardriving/issues/82
                        !likelyJunk;

                // MainActivity.info("META testing network: " + bssid + " newBest: " + newBest + " updatelevel: " + update.level + " bestlevel: " + bestlevel);
                if (newBest) {
                    bestlevel = update.level;
                    bestlat = location.getLatitude();
                    bestlon = location.getLongitude();
                }

                if (smallLocDelay || newBest) {
                    // MainActivity.info("META updating network: " + bssid + " newBest: " + newBest + " updatelevel: " + update.level + " bestlevel: " + bestlevel);
                    updateNetworkMetadata.bindLong( 1, bestlevel );
                    updateNetworkMetadata.bindDouble( 2, bestlat );
                    updateNetworkMetadata.bindDouble( 3, bestlon );
                    updateNetworkMetadata.bindString( 4, network.getSsid() );
                    updateNetworkMetadata.bindLong( 5, network.getFrequency() );
                    updateNetworkMetadata.bindString( 6, network.getCapabilities() );
                    updateNetworkMetadata.bindString( 7, bssid );

                    start = System.currentTimeMillis();
                    updateNetworkMetadata.execute();
                    logTime( start, "db network metadata updated" );
                }
            }
        }
        // else {
            // MainActivity.info( "db network not changeworthy: " + bssid );
        // }
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
                    MainActivity.info("culling pending queue. size: " + pending.size() );
                    // go thru the queue, cull out anything not newForRun
                    for ( Iterator<DBPending> it = pending.iterator(); it.hasNext(); ) {
                        final DBPending val = it.next();
                        if ( ! val.newForRun ) {
                            it.remove();
                        }
                    }
                    MainActivity.info("culled pending queue. size now: " + pending.size() );
                    added = pending.offer( update );
                    if ( ! added ) {
                        MainActivity.info( "pending queue still full, couldn't add: " + network.getBssid() );
                        // go thru the queue, squash dups.
                        HashSet<String> bssids = new HashSet<>();
                        for ( Iterator<DBPending> it = pending.iterator(); it.hasNext(); ) {
                            final DBPending val = it.next();
                            if ( ! bssids.add( val.network.getBssid() ) ) {
                                it.remove();
                            }
                        }
                        bssids.clear();

                        added = pending.offer( update );
                        if ( ! added ) {
                            MainActivity.info( "pending queue still full post-dup-purge, couldn't add: " + network.getBssid() );
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
            MainActivity.info( "moved " + accuracy + "m without a GPS fix, over " + d_time + "s" );
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
                //        MainActivity.info( "interpolated to ("+lerp_lat+","+lerp_lon+")" );

                // throw it on the queue!
                if ( addObservation( pend.network, pend.level, lerpLoc, pend.newForRun ) ) {
                    count++;
                } else {
                    MainActivity.info( "failed to add "+pend );
                }
                // XXX: altitude? worth it?
            }
            // return
            MainActivity.info( "recovered "+count+" location"+(count==1?"":"s")+" with the power of lerp");
        }

        lastLoc = null;
        return count;
    }

    private void logTime( final long start, final String string ) {
        long diff = System.currentTimeMillis() - start;
        if ( diff > 150L ) {
            MainActivity.info( string + " in " + diff + " ms" );
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

    public long getNewWifiCount() {
        return newWifiCount.get();
    }

    public long getNewCellCount() {
        return newCellCount.get();
    }

    public long getNetworkCount() {
        return networkCount.get();
    }

    private void getNetworkCountFromDB() throws DBException {
        networkCount.set( getCountFromDB( NETWORK_TABLE ) );
    }

//  private long getNetworkCountFromDB(NetworkType type) {
//    return getCountFromDB( NETWORK_TABLE + " WHERE type = '" + type.getCode() + "'" );
//  }

    public long getLocationCount() {
        return locationCount.get();
    }

    private void getLocationCountFromDB() throws DBException {
        long start = System.currentTimeMillis();
        final long count = getMaxIdFromDB( LOCATION_TABLE );
        long end = System.currentTimeMillis();
        MainActivity.info( "loc count: " + count + " in: " + (end-start) + "ms" );
        locationCount.set( count );
        setupMaxidDebug( count );
    }

    private void setupMaxidDebug( final long locCount ) {
        final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        final long maxid = prefs.getLong( ListFragment.PREF_DB_MARKER, -1L );
        final Editor edit = prefs.edit();
        edit.putLong( ListFragment.PREF_MAX_DB, locCount );

        if ( maxid == -1L ) {
            if ( locCount > 0 ) {
                // there is no preference set, yet there are locations, this is likely
                // a developer testing a new install on an old db, so set the pref.
                MainActivity.info( "setting db marker to: " + locCount );
                edit.putLong( ListFragment.PREF_DB_MARKER, locCount );
            }
        }
        else if (maxid > locCount) {
            final long newMaxid = Math.max(0, locCount - 10000);
            MainActivity.warn("db marker: " + maxid + " greater than location count: " + locCount
                    + ", setting to: " + newMaxid);
            edit.putLong( ListFragment.PREF_DB_MARKER, newMaxid );
        }
        edit.apply();
    }

    private long getCountFromDB( final String table ) throws DBException {
        checkDB();
        final Cursor cursor = db.rawQuery( "select count(*) FROM " + table, null );
        cursor.moveToFirst();
        final long count = cursor.getLong( 0 );
        cursor.close();
        return count;
    }

    private long getMaxIdFromDB( final String table ) throws DBException {
        checkDB();
        final Cursor cursor = db.rawQuery( "select MAX(_id) FROM " + table, null );
        cursor.moveToFirst();
        final long count = cursor.getLong( 0 );
        cursor.close();
        return count;
    }

    public Network getNetwork( final String bssid ) {
        // check cache
        Network retval = MainActivity.getNetworkCache().get( bssid );
        if ( retval == null ) {
            try {
                checkDB();
                final String[] args = new String[]{ bssid };
                final Cursor cursor = db.rawQuery("select ssid,frequency,capabilities,type,lastlat,lastlon,bestlat,bestlon FROM "
                        + NETWORK_TABLE
                        + " WHERE bssid = ?", args);
                if ( cursor.getCount() > 0 ) {
                    cursor.moveToFirst();
                    final String ssid = cursor.getString(0);
                    final int frequency = cursor.getInt(1);
                    final String capabilities = cursor.getString(2);
                    final float lastlat = cursor.getFloat(4);
                    final float lastlon = cursor.getFloat(5);
                    final float bestlat = cursor.getFloat(6);
                    final float bestlon = cursor.getFloat(7);

                    final NetworkType type = NetworkType.typeForCode( cursor.getString(3) );
                    retval = new Network( bssid, ssid, frequency, capabilities, 0, type );
                    if (bestlat != 0 && bestlon != 0) {
                        retval.setLatLng( new LatLng(bestlat, bestlon) );
                    }
                    else {
                        retval.setLatLng( new LatLng(lastlat, lastlon) );
                    }
                    MainActivity.getNetworkCache().put( bssid, retval );
                }
                cursor.close();
            }
            catch (DBException ex ) {
                deathDialog( "getNetwork", ex );
            }
        }
        return retval;
    }

    public Cursor locationIterator( final long fromId ) throws DBException {
        checkDB();
        MainActivity.info( "locationIterator fromId: " + fromId );
        final String[] args = new String[]{ Long.toString( fromId ) };
        return db.rawQuery( "SELECT _id,bssid,level,lat,lon,altitude,accuracy,time FROM location WHERE _id > ?", args );
    }

    public Cursor networkIterator() throws DBException {
        checkDB();
        MainActivity.info( "networkIterator" );
        final String[] args = new String[]{};
        return db.rawQuery( "SELECT bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon FROM network", args );
    }

    public Cursor getSingleNetwork( final String bssid ) throws DBException {
        checkDB();
        final String[] args = new String[]{bssid};
        return db.rawQuery(
                "SELECT bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon FROM network WHERE bssid = ?", args );
    }


    public Pair<Boolean,String> copyDatabase(final BackupTask task) {
        final String dbFilename = DATABASE_PATH + DATABASE_NAME;
        final String outputFilename = DATABASE_PATH + "backup-" + System.currentTimeMillis() + ".sqlite";
        File file = new File(dbFilename);
        File outputFile = new File(outputFilename);
        Pair<Boolean,String> result;
        try {
            InputStream input = new FileInputStream(file);
            OutputStream output = new FileOutputStream(outputFile);
            byte[] buffer = new byte[1024];
            int bytesRead;
            final long total = file.length();
            long read = 0;
            while( (bytesRead = input.read(buffer)) > 0){
                output.write(buffer, 0, bytesRead);
                read += bytesRead;
                int percent = (int)( (read*100)/total );
                // MainActivity.info("percent: " + percent + " read: " + read + " total: " + total );
                task.progress( percent );
            }
            output.close();
            input.close();
            result = new Pair<>(Boolean.TRUE, outputFilename);
        }
        catch ( IOException ex ) {
            result = new Pair<>(Boolean.FALSE, "ERROR: " + ex);
        }

        return result;
    }
}
