package net.wigle.wigleandroid.background;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.util.Logging;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Execute raw parameterized DB queries off the main thread.
 * Replacement for the old QueryThread system we used pre Android SDK 31 cutover.
 * Maintains matching abstractions for ease of porting but uses an Executor instead of keeping a
 * thread hot in the DatabaseHelper (and leaking).
 * @author arkasha
 */
public class PooledQueryExecutor {
    //ALIBI: unclear whether we need more than one thread a time for anything -
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface ResultHandler {
        boolean handleRow( Cursor cursor );
        void complete();
    }
    public static class Request implements Runnable{
        private final String sql;
        private final String[] args;
        private final PooledQueryExecutor.ResultHandler handler;
        private final DatabaseHelper dbHelper;

        public Request( final String sql, final String[] args,
                        final PooledQueryExecutor.ResultHandler handler, final DatabaseHelper dbHelper) {
            if ( sql == null ) {
                throw new IllegalArgumentException( "sql is null" );
            }
            if ( handler == null ) {
                throw new IllegalArgumentException( "handler is null" );
            }
            this.sql = sql;
            this.args = args;
            this.handler = handler;
            this.dbHelper = dbHelper;
        }

        @Override
        public void run() {
            Cursor cursor = null;
            try {
                final SQLiteDatabase db = dbHelper.getDB();
                if ( db != null ) {
                    cursor = db.rawQuery( sql, args );
                    while ( cursor.moveToNext() ) {
                        if (!handler.handleRow( cursor )) {
                            break;
                        }
                    }
                    handler.complete();
                }
            } catch ( IllegalStateException ex ) {
                Logging.info( sql + " illegal state ex: " + ex);
            }
            catch ( DBException ex ) {
                dbHelper.deathDialog("query thread", ex);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        public String getSql() {
            return sql;
        }
    }

    /**
     * Add a job to the threadppol
     * @param request the Request instance to execute
     */
    public static void enqueue( final Request request ) {
        executor.execute(request);
    }

    public static void shutdownNow() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

}
