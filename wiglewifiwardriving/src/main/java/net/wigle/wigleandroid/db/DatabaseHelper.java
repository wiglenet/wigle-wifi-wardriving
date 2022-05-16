// -*- Mode: Java; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
// vim:ts=2:sw=2:tw=80:et

package net.wigle.wigleandroid.db;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static net.wigle.wigleandroid.MainActivity.ERROR_REPORT_DIALOG;
import static net.wigle.wigleandroid.util.Logging.info;
import static net.wigle.wigleandroid.listener.GPSListener.MIN_ROUTE_LOCATION_DIFF_METERS;
import static net.wigle.wigleandroid.listener.GPSListener.MIN_ROUTE_LOCATION_DIFF_TIME;
import static net.wigle.wigleandroid.listener.GPSListener.MIN_ROUTE_LOCATION_PRECISION_METERS;
import static net.wigle.wigleandroid.util.FileUtility.SQL_EXT;
import static net.wigle.wigleandroid.util.FileUtility.hasSD;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.wigle.wigleandroid.DataFragment.BackupTask;
import net.wigle.wigleandroid.ErrorReportActivity;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.background.QueryThread;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.Pair;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.Logging;

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
import android.os.Build;
import android.os.Bundle;
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
    private static final String DATABASE_NAME = "wiglewifi"+SQL_EXT;
    private static final String EXTERNAL_DATABASE_PATH = FileUtility.getSDPath();
    private static final String INTERNAL_DB_PATH = "databases/";
    private static final int DB_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND;
    private static final Object TRANS_LOCK = new Object();

    private static final long QUEUE_CULL_TIMEOUT = 10000L;
    private long prevQueueCullTime = 0L;
    private long prevPendingQueueCullTime = 0L;

    private Location lastLoggedLocation;

    private SQLiteStatement insertNetwork;
    private SQLiteStatement insertLocationExternal;
    private SQLiteStatement updateNetwork;
    private SQLiteStatement updateNetworkMetadata;
    private SQLiteStatement updateNetworkType;
    private SQLiteStatement insertRoute;

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
                    + "time long not null,"
                    + "external integer not null default 0"
                    + ")";

    public static final String ROUTE_TABLE = "route";
    private static final String ROUTE_CREATE =
            "create table " + ROUTE_TABLE + " ( "
                    + "_id integer primary key autoincrement,"
                    + "run_id integer not null,"
                    + "wifi_visible integer not null default 0,"
                    + "cell_visible integer not null default 0,"
                    + "bt_visible integer not null default 0,"
                    + "lat double not null,"
                    + "lon double not null,"
                    + "altitude double not null,"
                    + "accuracy float not null,"
                    + "time long not null"
                    + ")";

    private static final String LOCATION_DELETE = "drop table " + LOCATION_TABLE;
    private static final String NETWORK_DELETE = "drop table " + NETWORK_TABLE;
    private static final String ROUTE_DELETE = "drop table " + ROUTE_TABLE;

    private static final String LOCATED_NETS_QUERY_STEM = (Build.VERSION.SDK_INT > 19)?
                " FROM " + DatabaseHelper.NETWORK_TABLE
            + " WHERE bestlat != 0.0 AND bestlon != 0.0 AND instr(bssid, '_') <= 0":
                " FROM " + DatabaseHelper.NETWORK_TABLE
            + " WHERE bestlat != 0.0 AND bestlon != 0.0 AND bssid NOT LIKE '%_%' ESCAPE '_'";

    //ALIBI: Sqlite types are dynamic, so usual warnings about doubles and zero == should be moot

    private static final String LOCATED_NETS_COUNT_QUERY = "SELECT count(*)" +LOCATED_NETS_QUERY_STEM;
    public static final String LOCATED_NETS_QUERY = "SELECT bssid, bestlat, bestlon" +LOCATED_NETS_QUERY_STEM;

    private static final String ROUTE_COUNT_QUERY = "SELECT count(*) FROM "+ROUTE_TABLE+" WHERE run_id = ?";

    private static final String CLEAR_DEFAULT_ROUTE = "DELETE FROM "+ROUTE_TABLE+" WHERE run_id = 0";

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
    private final AtomicLong currentRoutePointCount = new AtomicLong();
    private final AtomicLong locationCount = new AtomicLong();
    private final AtomicLong newNetworkCount = new AtomicLong();
    private final AtomicLong newWifiCount = new AtomicLong();
    private final AtomicLong newCellCount = new AtomicLong();
    private final AtomicLong newBtCount = new AtomicLong();
    private final QueryThread queryThread;

    private Location lastLoc = null;
    private long lastLocWhen = 0L;
    private final DeathHandler deathHandler;
    private final SharedPreferences prefs;

    public enum NetworkFilter {
        WIFI("type = 'W'"),
        BT("type IN ('B','E')"),
        CELL("type IN ('G','C','L','D')");

        final String filter;

        NetworkFilter(final String filter) {
            this.filter = filter;
        }

        public String getFilter() {
            return this.filter;
        }
    }


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
        public final boolean frequencyChanged;
        public final boolean typeMorphed;
        public final int external;

        public DBUpdate( final Network network, final int level, final Location location, final boolean newForRun ) {
            this(network, level, location, newForRun, false, false);
        }

        public DBUpdate( final Network network, final int level, final Location location, final boolean newForRun, final boolean frequencyChanged, final boolean typeMorphed ) {
            this(network, level, location, newForRun, false, false, 0);
        }

        public DBUpdate( final Network network, final int level, final Location location, final boolean newForRun, final boolean frequencyChanged, final boolean typeMorphed, final int external ) {
            this.network = network;
            this.level = level;
            this.location = location;
            this.newForRun = newForRun;
            this.frequencyChanged = frequencyChanged;
            this.typeMorphed = typeMorphed;
            this.external = external;
        }
    }

    /** holder for updates which we'll attempt to interpolate based on timing */
    final static class DBPending {
        public final Network network;
        public final int level;
        public final boolean newForRun;
        public final long when; // in MS
        public final boolean frequencyChanged;
        public final boolean typeMorphed;

        public DBPending( final Network network, final int level, final boolean newForRun ) {
            this(network, level, newForRun, false, false);
        }
        public DBPending( final Network network, final int level, final boolean newForRun, boolean frequencyChanged, boolean typeMorphed) {
            this.network = network;
            this.level = level;
            this.newForRun = newForRun;
            this.when = System.currentTimeMillis();
            this.frequencyChanged = frequencyChanged;
            this.typeMorphed = typeMorphed;
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
                Logging.error("no bundle in msg: " + msg);
            }
            else {
                error = bundle.getString( ERROR );
            }

            final MainActivity mainActivity = MainActivity.getMainActivity();
            final Intent errorReportIntent = new Intent( mainActivity, ErrorReportActivity.class );
            errorReportIntent.putExtra( ERROR_REPORT_DIALOG, error );
            mainActivity.startActivity( errorReportIntent );
        }
    }

    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public void run() {
        try {
            Logging.info( "starting db thread" );

            Logging.info( "setting db thread priority (-20 highest, 19 lowest) to: " + DB_PRIORITY );
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
                                Logging.warn("DB run loop constraint ex, countdown: " + countdown + " ex: " + ex );
                                countdown--;
                            }
                            catch ( Exception ex ) {
                                Logging.warn("DB run loop ex, countdown: " + countdown + " ex: " + ex );
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
                        Logging.info( "db run loop took: " + delay + " ms. drainSize: " + drainSize );
                    }
                }
                catch ( final InterruptedException ex ) {
                    // no worries
                    Logging.info("db queue take interrupted");
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
                            Logging.error( "exception in db.endTransaction: " + ex, ex );
                        }
                    }
                }
            }
        }
        catch ( final Throwable throwable ) {
            if ( ! done.get() ) { // ALIBI: no need to crash / error if this was a query post-terminate.
                MainActivity.writeError(Thread.currentThread(), throwable, context);
                throw new RuntimeException("DatabaseHelper throwable: " + throwable, throwable);
            } else {
                Logging.warn("DB error during shutdown - ignoring: ", throwable);
            }
        }

        Logging.info("db worker thread shutting down");
    }

    public void deathDialog( String message, Exception ex ) {
        // send message to the handler that will get this dialog on the activity thread
        Logging.error( "db exception. " + message + ": " + ex, ex );
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
        final boolean hasSD = FileUtility.hasSD();
        if ( hasSD ) {
            File path = new File( EXTERNAL_DATABASE_PATH );
            //noinspection ResultOfMethodCallIgnored
            path.mkdirs();
            dbFilename = EXTERNAL_DATABASE_PATH + DATABASE_NAME;
            info("made path: " + path + " exists: " + path.exists() + " write: " + path.canWrite());
        }
        final File dbFile = new File( dbFilename );
        boolean doCreateNetwork = false;
        boolean doCreateLocation = false;
        boolean doCreateRoute = false;
        if ( ! dbFile.exists() && hasSD ) {
            doCreateNetwork = true;
            doCreateLocation = true;
            doCreateRoute = true;
        }
        Logging.info("opening: " + dbFilename );

        if ( hasSD ) {
            db = SQLiteDatabase.openOrCreateDatabase( dbFilename, null );
        }
        else {
            db = context.openOrCreateDatabase( dbFilename, Context.MODE_PRIVATE, null );
        }

        try {
            db.rawQuery( "SELECT count(*) FROM "+NETWORK_TABLE, null).close();
        }
        catch ( final SQLiteException ex ) {
            Logging.info("exception selecting from network, try to create. ex: " + ex );
            doCreateNetwork = true;
        }

        try {
            db.rawQuery( "SELECT count(*) FROM "+LOCATION_TABLE, null).close();
        }
        catch ( final SQLiteException ex ) {
            Logging.info("exception selecting from location, try to create. ex: " + ex );
            doCreateLocation = true;
        }

        try {
            db.rawQuery( "SELECT max(run_id) FROM "+ROUTE_TABLE, null).close();
        }
        catch ( final SQLiteException ex ) {
            Logging.info("exception selecting from route, try to create. ex: " + ex );
            doCreateRoute = true;
        }

        if ( doCreateNetwork ) {
            Logging.info( "creating network table" );
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
                if (db.getVersion() == 2) {
                    // only diff to version 3 is the "external" column on the location table
                    db.setVersion(3);
                }
            }
            catch ( final SQLiteException ex ) {
                Logging.error( "sqlite exception: " + ex, ex );
            }
        }

        if ( doCreateLocation ) {
            Logging.info( "creating location table" );
            try {
                db.execSQL(LOCATION_CREATE);
                // new database, reset a marker, if any
                final Editor edit = prefs.edit();
                edit.putLong( ListFragment.PREF_DB_MARKER, 0L );
                edit.apply();
            }
            catch ( final SQLiteException ex ) {
                Logging.error( "sqlite exception: " + ex, ex );
            }
        }

        if ( doCreateRoute ) {
            Logging.info( "creating route table" );
            try {
                db.execSQL(ROUTE_CREATE);
                // new database, start with route Zero
                final Editor edit = prefs.edit();
                edit.putLong( ListFragment.PREF_ROUTE_DB_RUN, 0L );
                edit.apply();
            }
            catch ( final SQLiteException ex ) {
                Logging.error( "sqlite exception: " + ex, ex );
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

        Logging.info( "database version: " + db.getVersion() );
        if ( db.getVersion() == 0 ) {
            Logging.info("upgrading db from 0 to 1");
            try {
                db.execSQL( "ALTER TABLE network ADD COLUMN type text not null default '" + NetworkType.WIFI.getCode() + "'" );
                db.setVersion(1);
            }
            catch ( SQLiteException ex ) {
                Logging.info("ex: " + ex, ex);
                if ( "duplicate column name".equals( ex.toString() ) ) {
                    db.setVersion(1);
                }
            }
        }
        else if ( db.getVersion() == 1 ) {
            Logging.info("upgrading db from 1 to 2");
            try {
                db.execSQL( "ALTER TABLE network ADD COLUMN bestlevel integer not null default 0" );
                db.execSQL( "ALTER TABLE network ADD COLUMN bestlat double not null default 0");
                db.execSQL( "ALTER TABLE network ADD COLUMN bestlon double not null default 0");
                db.setVersion(2);
            }
            catch ( SQLiteException ex ) {
                Logging.info("ex: " + ex, ex);
                if ( "duplicate column name".equals( ex.toString() ) ) {
                    db.setVersion(2);
                }
            }
        } else if ( db.getVersion() == 2) {
            Logging.info("upgrading db from 2 to 3");
            try {
                db.execSQL( "ALTER TABLE "+LOCATION_TABLE+" ADD COLUMN external integer not null default 0" );
                db.setVersion(3);
            } catch ( SQLiteException ex ) {
                Logging.info("ex: " + ex, ex);
                if ( "duplicate column name".equals( ex.toString() ) ) {
                    db.setVersion(3);
                }
            }
        }

        // drop index, was never publicly released
        db.execSQL("DROP INDEX IF EXISTS type");

        // compile statements
        insertNetwork = db.compileStatement( "INSERT INTO "+NETWORK_TABLE
                + " (bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon,type,bestlevel,bestlat,bestlon) VALUES (?,?,?,?,?,?,?,?,?,?,?)" );

        insertLocationExternal = db.compileStatement( "INSERT INTO " + LOCATION_TABLE
                + " (bssid,level,lat,lon,altitude,accuracy,time,external) VALUES (?,?,?,?,?,?,?,?)" );

        updateNetwork = db.compileStatement( "UPDATE "+NETWORK_TABLE+" SET"
                + " lasttime = ?, lastlat = ?, lastlon = ? WHERE bssid = ?" );

        updateNetworkMetadata = db.compileStatement( "UPDATE "+NETWORK_TABLE+" SET"
                + " bestlevel = ?, bestlat = ?, bestlon = ?, ssid = ?, frequency = ?, capabilities = ? WHERE bssid = ?" );

        updateNetworkType = db.compileStatement( "UPDATE "+NETWORK_TABLE+" SET"
                + " type = ? WHERE bssid = ?" );

        insertRoute = db.compileStatement( "INSERT INTO "+ROUTE_TABLE
                + " (run_id,wifi_visible,cell_visible,bt_visible,lat,lon,altitude,accuracy,time) VALUES (?,?,?,?,?,?,?,?,?)" );
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
            Logging.info( "db still alive. countdown: " + countdown );
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
                    if ( insertLocationExternal != null) {
                        insertLocationExternal.close();
                    }
                    if ( updateNetwork != null ) {
                        updateNetwork.close();
                    }
                    if ( updateNetworkMetadata != null ) {
                        updateNetworkMetadata.close();
                    }
                    if ( insertRoute != null ) {
                        insertRoute.close();
                    }
                    if ( db.isOpen() ) {
                        db.close();
                    }
                }
            }
            catch ( SQLiteException ex ) {
                Logging.info( "db close exception, will try again. countdown: " + countdown + " ex: " + ex, ex );
                MainActivity.sleep( 100L );
            }
            countdown--;
        }
    }

    public synchronized void checkDB() throws DBException {
        if ( db == null || ! db.isOpen() ) {
            Logging.info( "re-opening db in checkDB" );
            try {
                open();
            }
            catch ( SQLiteException ex ) {
                throw new DBException("checkDB", ex);
            }
        }
    }

    public void blockingAddExternalObservation(final Network network, final Location location, final boolean newForRun )
            throws InterruptedException {

        final DBUpdate update = new DBUpdate( network, network.getLevel(), location, newForRun, false, false, 1 );
        queue.put(update);
    }

    public boolean addObservation( final Network network, final Location location, final boolean newForRun ) {
        try {
            return addObservation(network, network.getLevel(), location, newForRun, false, false);
        }
        catch (final IllegalMonitorStateException ex) {
            Logging.error("exception adding network: " + ex, ex);
        }
        return false;
    }

    public boolean addObservation( final Network network, final Location location, final boolean newForRun,
                                   final boolean frequencyChanged, final boolean typeMorphed  ) {
        try {
            return addObservation(network, network.getLevel(), location, newForRun, frequencyChanged, typeMorphed);
        }
        catch (final IllegalMonitorStateException ex) {
            Logging.error("exception adding network: " + ex, ex);
        }
        return false;
    }

    private boolean addObservation( final Network network, final int level, final Location location,
                                    final boolean newForRun, final boolean frequencyChanged, final boolean typeMorphed ) {

        final DBUpdate update = new DBUpdate( network, level, location, newForRun, frequencyChanged, typeMorphed );

        // data is lost if queue is full!
        boolean added = queue.offer( update );
        if ( ! added ) {
            Logging.info( "queue full, not adding: " + network.getBssid() + " ssid: " + network.getSsid() );
            if ( System.currentTimeMillis() - prevQueueCullTime > QUEUE_CULL_TIMEOUT ) {
                Logging.info("culling queue. size: " + queue.size() );
                // go thru the queue, cull out anything not newForRun
                for ( Iterator<DBUpdate> it = queue.iterator(); it.hasNext(); ) {
                    final DBUpdate val = it.next();

                    if ( ! val.newForRun && !val.typeMorphed && !val.frequencyChanged) {
                        it.remove();
                    }
                }
                Logging.info("culled queue. size now: " + queue.size() );
                added = queue.offer( update );
                if ( ! added ) {
                    Logging.info( "queue still full, couldn't add: " + network.getBssid() );
                }
                prevQueueCullTime = System.currentTimeMillis();
            }

        }
        return added;
    }

    @SuppressWarnings("deprecation")
    private void addObservation( final DBUpdate update, final int drainSize ) throws DBException {
        checkDB();
        if (insertNetwork == null || insertLocationExternal == null
                || updateNetwork == null || updateNetworkMetadata == null) {

            Logging.warn("A stored procedure is null, not adding observation");
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

        // STEP 1: verify location
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
                Logging.info("the weird close-cursor exception: " + ex );
            }
        }

        if ( isNew ) {
            newNetworkCount.incrementAndGet();
            if ( NetworkType.WIFI.equals( network.getType() ) ) {
                newWifiCount.incrementAndGet();
            } else if ( NetworkType.BT.equals( network.getType() ) || NetworkType.BLE.equals( network.getType() ) ) {
                newBtCount.incrementAndGet();
            } else if ( NetworkType.NFC.equals( network.getType() ) ) {
                //TODO:
            } else {
                newCellCount.incrementAndGet();
            }
        }

        final boolean fastMode = isFastMode();

        //STEP 2: evaluate lat/lon diff
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
            long start;
            insertLocationExternal.bindString(1, bssid);
            insertLocationExternal.bindLong(2, update.level);  // make sure to use the update's level, network's is mutable...
            insertLocationExternal.bindDouble(3, location.getLatitude());
            insertLocationExternal.bindDouble(4, location.getLongitude());
            insertLocationExternal.bindDouble(5, location.getAltitude());
            insertLocationExternal.bindDouble(6, location.getAccuracy());
            insertLocationExternal.bindLong(7, location.getTime());
            insertLocationExternal.bindLong( 8, update.external);
            if (db.isDbLockedByOtherThreads()) {
                // this is kinda lame, make this better
                Logging.error("db locked by another thread, waiting to loc insert. bssid: " + bssid
                        + " drainSize: " + drainSize);
                MainActivity.sleep(1000L);
            }
            start = System.currentTimeMillis();
            // INSERT
            insertLocationExternal.execute();
            logTime(start, "db location inserted: " + bssid + " drainSize: " + drainSize);
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
                    Logging.error( "db locked by another thread, waiting to net update. bssid: " + bssid
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

                if (update.typeMorphed) {
                    //ALIBI: currently this trick is only used when a BT networks turns out to be BLE
                    //  if this should expand, it's important to update the counts here.
                    updateNetworkType.bindString(1, network.getType().getCode());
                    updateNetworkType.bindString( 2, bssid );
                    start = System.currentTimeMillis();
                    updateNetworkType.execute();
                    logTime( start, "db network type updated" );
                }

                if (smallLocDelay || newBest || update.frequencyChanged) {
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
    public boolean pendingObservation( final Network network, final boolean newForRun, final boolean frequencyChanged, final boolean typeMorphed) {
        if ( lastLoc != null ) {
            // modify this to check age at some point on failure. or offer a flush method. or.. something
            DBPending update = new DBPending( network, network.getLevel(), newForRun, frequencyChanged, typeMorphed);
            boolean added = pending.offer( update );
            if ( ! added ) {
                if ( System.currentTimeMillis() - prevPendingQueueCullTime > QUEUE_CULL_TIMEOUT ) {
                    Logging.info("culling pending queue. size: " + pending.size() );
                    // go thru the queue, cull out anything not newForRun
                    for ( Iterator<DBPending> it = pending.iterator(); it.hasNext(); ) {
                        final DBPending val = it.next();
                        if ( ! val.newForRun ) {
                            it.remove();
                        }
                    }
                    Logging.info("culled pending queue. size now: " + pending.size() );
                    added = pending.offer( update );
                    if ( ! added ) {
                        Logging.info( "pending queue still full, couldn't add: " + network.getBssid() );
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
                            Logging.info( "pending queue still full post-dup-purge, couldn't add: " + network.getBssid() );
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

    public void clearPendingObservations() {
        Logging.info("clearing pending observations");
        pending.clear();
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
            Logging.info( "moved " + accuracy + "m without a GPS fix, over " + d_time + "s" );
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
                if ( addObservation(pend.network, pend.level, lerpLoc, pend.newForRun,
                        pend.frequencyChanged, pend.typeMorphed) ) {
                    count++;
                } else {
                    Logging.info( "failed to add "+pend );
                }
                // XXX: altitude? worth it?
            }
            // return
            Logging.info( "recovered "+count+" location"+(count==1?"":"s")+" with the power of lerp");
        }

        lastLoc = null;
        return count;
    }

    private void logTime( final long start, final String string ) {
        long diff = System.currentTimeMillis() - start;
        if ( diff > 150L ) {
            Logging.info( string + " in " + diff + " ms" );
        }
    }

    /**
     * insert a new point for the specified route
     * @param location location of current measurement
     * @param wifiVisible # wifi nets visible
     * @param cellVisible # cells visible
     * @param btVisible # bt devices visible
     * @param runId # the current, monotonic run ID
     */
    public void logRouteLocation (final Location location, final int wifiVisible, final int cellVisible, final int btVisible, final long runId) {
        if (location == null) {
            Logging.error("Null location in logRouteLocation");
            return;
        }
        final double accuracy = location.getAccuracy();
        if (insertRoute != null && location.getTime() != 0L &&
                accuracy < MIN_ROUTE_LOCATION_PRECISION_METERS &&
                accuracy > 0.0d && //ALIBI: should never happen?
                (lastLoggedLocation == null ||
                        ((lastLoggedLocation.distanceTo(location) > MIN_ROUTE_LOCATION_DIFF_METERS &&
                                (location.getTime() - lastLoggedLocation.getTime() > MIN_ROUTE_LOCATION_DIFF_TIME)
                        )) )) {
            insertRoute.bindLong(1, runId);
            insertRoute.bindLong(2, wifiVisible);
            insertRoute.bindLong(3, cellVisible);
            insertRoute.bindLong(4, btVisible);
            insertRoute.bindDouble(5, location.getLatitude());
            insertRoute.bindDouble(6, location.getLongitude());
            insertRoute.bindDouble(7, location.getAltitude());
            insertRoute.bindDouble(8, location.getAccuracy());
            insertRoute.bindLong(9, location.getTime());
            long start = System.currentTimeMillis();

            insertRoute.execute();
            lastLoggedLocation = location;
            currentRoutePointCount.incrementAndGet();
            logTime(start, "db route point added");
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

    public long getNewBtCount() {
        return newBtCount.get();
    }

    public long getNetworkCount() {
        return networkCount.get();
    }

    public long getCurrentRoutePointCount() { return currentRoutePointCount.get(); }

    public long getRoutePointCount(long routeId) {
        try {
            checkDB();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery(ROUTE_COUNT_QUERY, new String[]{String.valueOf(routeId)});
                cursor.moveToFirst();
                final long count = cursor.getLong(0);
                return count;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            return 0L;
        }
    }

    public void getNetworkCountFromDB() throws DBException {
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
        Logging.info( "loc count: " + count + " in: " + (end-start) + "ms" );
        locationCount.set( count );
        setupMaxidDebug( count );
    }

    private void setupMaxidDebug( final long locCount ) {
        final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        final long maxid = prefs.getLong( ListFragment.PREF_DB_MARKER, -1L );
        final Editor edit = prefs.edit();
        final long oldMaxDb = prefs.getLong( ListFragment.PREF_MAX_DB, locCount );
        edit.putLong( ListFragment.PREF_MAX_DB, locCount );

        if ( maxid == -1L ) {
            if ( locCount > 0 ) {
                // there is no preference set, yet there are locations, this is likely
                // a developer testing a new install on an old db, so set the pref.
                Logging.info( "setting db marker to: " + locCount );
                edit.putLong( ListFragment.PREF_DB_MARKER, locCount );
            }
        }
        else if (maxid > locCount || (maxid == 0 && oldMaxDb == 0 && locCount > 10000)) {
            final long newMaxid = Math.max(0, locCount - 10000);
            Logging.warn("db marker: " + maxid + " greater than location count: " + locCount
                    + ", setting to: " + newMaxid);
            edit.putLong( ListFragment.PREF_DB_MARKER, newMaxid );
        }
        edit.apply();
    }

    private long getCountFromDB( final String table ) throws DBException {
        checkDB();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("select count(*) FROM " + table, null);
            cursor.moveToFirst();
            final long count = cursor.getLong(0);
            return count;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private long getMaxIdFromDB( final String table ) throws DBException {
        checkDB();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery( "select MAX(_id) FROM " + table, null );
            cursor.moveToFirst();
            final long count = cursor.getLong( 0 );
            return count;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public long getNetsWithLocCountFromDB() throws DBException {
        checkDB();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(LOCATED_NETS_COUNT_QUERY, null);
            cursor.moveToFirst();
            final long count = cursor.getLong( 0 );
            return count;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public Network getNetwork( final String bssid ) {
        // check cache
        Network retval = MainActivity.getNetworkCache().get( bssid );
        if ( retval == null ) {
            Cursor cursor = null;
            try {
                checkDB();
                final String[] args = new String[]{ bssid };
                cursor = db.rawQuery("select ssid,frequency,capabilities,type,lastlat,lastlon,bestlat,bestlon FROM "
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
            } catch (DBException ex ) {
                deathDialog( "getNetwork", ex );
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return retval;
    }

    public Cursor locationIterator( final long fromId ) throws DBException {
        checkDB();
        Logging.info( "locationIterator fromId: " + fromId );
        final String[] args = new String[]{ Long.toString( fromId ) };
        return db.rawQuery( "SELECT _id,bssid,level,lat,lon,altitude,accuracy,time FROM location WHERE _id > ? AND external = 0", args );
    }

    public Cursor networkIterator() throws DBException {
        checkDB();
        Logging.info( "networkIterator" );
        final String[] args = new String[]{};
        return db.rawQuery( "SELECT bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon,bestlevel,type FROM network", args );
    }

    public Cursor networkIterator(final NetworkFilter filter) throws DBException {
        checkDB();
        Logging.info( "networkIterator (filtered)" );
        final String[] args = new String[]{};
        return db.rawQuery( "SELECT bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon,bestlevel,type FROM network WHERE "+filter.getFilter(), args );
    }

    public Cursor routeIterator(final long routeId) throws DBException {
        checkDB();
        Logging.info( "routeIterator" );
        final String[] args = new String[]{String.valueOf(routeId)};
        return db.rawQuery( "SELECT lat,lon,time FROM route WHERE run_id = ?", args );
    }

    public Cursor routeMetaIterator() throws DBException {
        checkDB();
        Logging.info( "routeMetaIterator" );
        final String[] args = new String[]{};
        //ALIBI: we'd love to parameterize min observations here, but SQLite rawQuery doesn't seem to respect ? parameterization in HAVING statements.
        return db.rawQuery( "SELECT _id, run_id, MIN(time) AS starttime, MAX(time) AS endtime, count(_id) AS obs FROM route GROUP BY run_id HAVING obs >= 20 ORDER BY time DESC", args);
    }

    public Cursor currentRouteIterator() throws DBException {
        checkDB();
        Logging.info( "routeIterator" );
        final String[] args = new String[]{};
        return db.rawQuery( "SELECT lat,lon,time FROM route WHERE run_id = (SELECT MAX(run_id) FROM route)", args );
    }

    public void clearDefaultRoute() throws DBException {
        checkDB();
        if (null != db) {
            db.execSQL(CLEAR_DEFAULT_ROUTE);
        }
    }

    public Cursor getCurrentVisibleRouteIterator(SharedPreferences prefs) throws DBException{
        Logging.info("currentRouteIterator");
        checkDB();
        if (prefs == null || !prefs.getBoolean(ListFragment.PREF_VISUALIZE_ROUTE, false)) {
            return null;
        }
        boolean logRoutes = prefs.getBoolean(ListFragment.PREF_LOG_ROUTES, false);
        final long visibleRouteId = logRoutes?prefs.getLong(ListFragment.PREF_ROUTE_DB_RUN, 0L):0L;
        final String[] args = new String[]{String.valueOf(visibleRouteId)};
        return db.rawQuery( "SELECT lat,lon FROM route WHERE run_id = ?", args );
    }

    public Cursor getSingleNetwork( final String bssid ) throws DBException {
        checkDB();
        final String[] args = new String[]{bssid};
        return db.rawQuery(
                "SELECT bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon,bestlevel,type FROM network WHERE bssid = ?", args );
    }

    public Cursor getSingleNetwork( final String bssid, final NetworkFilter filter ) throws DBException {
        checkDB();
        final String[] args = new String[]{bssid};
        return db.rawQuery(
                "SELECT bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon,bestlevel,type FROM network WHERE bssid = ? AND "+ filter.getFilter(), args );
    }


    public Pair<Boolean,String> copyDatabase(final BackupTask task) {
        File file = context.getDatabasePath(DATABASE_NAME);
        String outputFilename = "backup-" + System.currentTimeMillis() + SQL_EXT;

        if (hasSD()) {
            file = new File(EXTERNAL_DATABASE_PATH, DATABASE_NAME);
        }
        Pair<Boolean,String> result;
        try {
            InputStream input = new FileInputStream(file);
            FileOutputStream output = FileUtility.createFile(context, outputFilename, false);
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
            final File outputFile = new File(FileUtility.getBackupPath(context), outputFilename);
            result = new Pair<>(Boolean.TRUE, outputFile.getAbsolutePath());
        }
        catch ( IOException ex ) {
            Logging.error("backup failure: " + ex, ex);
            result = new Pair<>(Boolean.FALSE, "ERROR: " + ex);
        }

        return result;
    }

    public int clearDatabase() {
        try {
            db.beginTransaction();
            Logging.info( "deleting location table" );
            db.execSQL(LOCATION_DELETE);

            Logging.info( "deleting network table" );
            db.execSQL(NETWORK_DELETE);

            Logging.info( "deleting route table" );
            db.execSQL(ROUTE_DELETE);

            Logging.info( "creating network table" );
            db.execSQL(NETWORK_CREATE);
            if ( db.getVersion() == 0 ) {
                // only diff to version 1 is the "type" column in network table
                db.setVersion(1);
            }
            if ( db.getVersion() == 1 ) {
                // only diff to version 2 is the "bestlevel", "bestlat", "bestlon" columns in network table
                db.setVersion(2);
            }
            Logging.info( "creating location table" );
            db.execSQL(LOCATION_CREATE);
            db.execSQL(ROUTE_CREATE);
            db.setTransactionSuccessful();
            //TODO: update list header count
        } catch ( final SQLiteException ex ) {
            Logging.error( "sqlite exception: " + ex, ex );
            return 0;
        } finally {
            db.endTransaction();
        }
        return 1;
    }
}
