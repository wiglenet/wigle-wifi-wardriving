package net.wigle.wigleandroid.background;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.wigle.wigleandroid.DBException;
import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.MainActivity;

public class QueryThread extends Thread {
    private final BlockingQueue<Request> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean done = new AtomicBoolean( false );
    private final DatabaseHelper dbHelper;

    public interface ResultHandler {
        boolean handleRow( Cursor cursor );
        void complete();
    }
    public static class Request {
        private final String sql;
        private final ResultHandler handler;

        public Request( final String sql, final ResultHandler handler ) {
            if ( sql == null ) {
                throw new IllegalArgumentException( "sql is null" );
            }
            if ( handler == null ) {
                throw new IllegalArgumentException( "handler is null" );
            }
            this.sql = sql;
            this.handler = handler;
        }
    }

    public QueryThread( final DatabaseHelper dbHelper ) {
        this.dbHelper = dbHelper;
        setName( "query-" + getName() );
    }

    public void setDone() {
        done.set( true );
    }

    public void addToQueue( final Request request ) {
        try {
            queue.put( request );
        }
        catch ( InterruptedException ex ) {
            MainActivity.info(getName() + " interrupted");
        }
    }

    @Override
    public void run() {
        while ( ! done.get() ) {
            try {
                final Request request = queue.take();
                // if(true) throw new DBException("meh", new SQLiteException("meat puppets"));
                if ( request != null ) {
                    final SQLiteDatabase db = dbHelper.getDB();
                    if ( db != null ) {
                        final Cursor cursor = db.rawQuery( request.sql, null );
                        while ( cursor.moveToNext() ) {
                            if (!request.handler.handleRow( cursor )) {
                                break;
                            }
                        }
                        request.handler.complete();
                        cursor.close();
                    }
                }
            }
            catch ( InterruptedException ex ) {
                MainActivity.info( getName() + " interrupted: " + ex);
            }
            catch ( IllegalStateException ex ) {
                MainActivity.info( getName() + " illegal state ex: " + ex);
            }
            catch ( DBException ex ) {
                dbHelper.deathDialog("query thread", ex);
            }
        }
    }

}
