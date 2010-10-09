package net.wigle.wigleandroid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.view.WindowManager;

public final class FileUploaderTask extends Thread {
  private Context context;
  private final Handler handler;
  private final DatabaseHelper dbHelper;
  private ProgressDialog pd;
  private final FileUploaderListener listener;
  private AlertDialog ad;
  private final Object lock = new Object();
  
  static final int WRITING_PERCENT_START = 10000;
  private static final String COMMA = ",";
  private static final String NEWLINE = "\n";
  private static final String ERROR = "error";
  private static final String FILENAME = "filename";
  private static final String FILEPATH = "filepath";
  private static final int UPLOAD_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND;
  
  private enum Status {
    UNKNOWN("Unknown", "Unknown error"),
    FAIL( "Fail", "Fail" ),
    SUCCESS( "Success", "Upload Successful"),
    BAD_USERNAME("Fail", "Username not set"),
    BAD_PASSWORD("Fail", "Password not set and username not 'anonymous'"),
    EXCEPTION("Fail", "Exception"),
    BAD_LOGIN("Fail", "Login failed, check password?"),
    UPLOADING("Working...", "Uploading File "),
    WRITING("Working...", "Writing File "),
    EMPTY_FILE("Doing Nothing", "File would be empty");
    
    private final String title;
    private final String message;
    private Status( final String title, final String message ) {
      this.title = title;
      this.message = message;
    }
    public String getTitle() {
      return title;
    }
    public String getMessage() {
      return message;
    }
  }
  
  public FileUploaderTask( final Context context, final DatabaseHelper dbHelper, final FileUploaderListener listener ) {
    if ( context == null ) {
      throw new IllegalArgumentException( "context is null" );
    }
    if ( dbHelper == null ) {
      throw new IllegalArgumentException( "dbHelper is null" );
    }
    if ( listener == null ) {
      throw new IllegalArgumentException( "listener is null" );
    }
    
    this.context = context;
    this.dbHelper = dbHelper;
    this.listener = listener;
    
    // set with activity context, sets up the progress dialog
    this.pd = ProgressDialog.show( context, Status.WRITING.getTitle(), Status.WRITING.getMessage(), true, false );
    
    this.handler = new Handler() {
      private String msg_text = "";
      
      @Override
      public void handleMessage( final Message msg ) {
        synchronized ( lock ) {
          if ( msg.what >= WRITING_PERCENT_START ) {
            final int percent = msg.what - WRITING_PERCENT_START;
            pd.setMessage( msg_text + percent + "%" );
            pd.setProgress( percent * 100 );
            return;
          }
          
          final Status status = Status.values()[ msg.what ];
          if ( Status.UPLOADING.equals( status ) ) {
            //          pd.setMessage( status.getMessage() );
            msg_text = status.getMessage();
            pd.setProgress(0);
            return;
          }
          if ( Status.WRITING.equals( status ) ) {
            msg_text = status.getMessage();
            pd.setProgress(0);
            return;
          }
          // make sure we didn't progress dialog this somewhere
          if ( pd != null && pd.isShowing() ) {
            try {
              pd.dismiss();
              pd = null;
            }
            catch ( Exception ex ) {
              // guess it wasn't there anyways
              ListActivity.info( "exception dismissing dialog: " + ex );
            }
          }
          // Activity context
          buildAlertDialog( msg, status );
        }
      }
     };
  }
  
  private void buildAlertDialog( final Message msg, final Status status ) {
    final AlertDialog.Builder builder = new AlertDialog.Builder( context );
    builder.setCancelable( false );
    builder.setTitle( status.getTitle() );
    Bundle bundle = msg.peekData();
    String filename = "";
    if ( bundle != null ) {
      String filepath = bundle.getString( FILEPATH );
      filepath = filepath == null ? "" : filepath + "\n";
      filename = bundle.getString( FILENAME );
      if ( filename != null ) {
        // just don't show the gz
        final int index = filename.indexOf( ".gz" );
        if ( index > 0 ) {
          filename = filename.substring( 0, index );
        }
      }
      filename = "\n\nFile location:\n" + filepath + filename;
    }
    
    if ( bundle == null ) {
      builder.setMessage( status.getMessage() + filename );
    }
    else {
      String error = bundle.getString( ERROR );
      error = error == null ? "" : " Error: " + error;
      builder.setMessage( status.getMessage() + error + filename );
    }
    ad = builder.create();
    ad.setButton( "OK", new DialogInterface.OnClickListener() {
      public void onClick( final DialogInterface dialog, final int which ) {
        try {
          dialog.dismiss();
        }
        catch ( Exception ex ) {
          // guess it wasn't there anyways
          ListActivity.info( "exception dismissing alert dialog: " + ex );
        }
        return;
      } }); 
    try {
      ad.show();
    }
    catch ( WindowManager.BadTokenException ex ) {
      ListActivity.info( "exception showing dialog, view probably changed: " + ex, ex );
    }
  }
  
  public  void setContext( final Context context ) {
    synchronized ( lock ) {
      this.context = context;
      
      if ( pd != null && pd.isShowing() ) {
        try {
          pd.dismiss();
        }
        catch ( Exception ex ) {
          // guess it wasn't there anyways
          ListActivity.info( "exception dismissing progress dialog: " + ex );
        }
        this.pd = ProgressDialog.show( context, Status.WRITING.getTitle(), Status.WRITING.getMessage(), true, false ); 
      }
      
      if ( ad != null && ad.isShowing() ) {
        try {
          ad.dismiss();
        }
        catch ( Exception ex ) {
          // guess it wasn't there anyways
          ListActivity.info( "exception dismissing alert dialog: " + ex );
        }
      }
    }
  }
  
  public void run() {
    try {
      ListActivity.info( "setting file upload thread priority (-20 highest, 19 lowest) to: " + UPLOAD_PRIORITY );
      Process.setThreadPriority( UPLOAD_PRIORITY );
      
      doRun();
    }
    catch ( final Throwable throwable ) {
      ListActivity.writeError( Thread.currentThread(), throwable, context );
      throw new RuntimeException( "FileUploaderTask throwable: " + throwable, throwable );
    }
    finally {
      // tell the listener
      listener.uploadComplete();
    }
  }
  
  private void doRun() {
    final SharedPreferences prefs = context.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
    final String username = prefs.getString( ListActivity.PREF_USERNAME, "" );
    final String password = prefs.getString( ListActivity.PREF_PASSWORD, "" );
    Status status = Status.UNKNOWN;
    final Bundle bundle = new Bundle();
    
    if ( "".equals( username ) ) {
      // TODO: error
      ListActivity.error( "username not defined" );
      status = Status.BAD_USERNAME;
    }
    else if ( "".equals( password ) && ! ListActivity.ANONYMOUS.equals( username.toLowerCase() ) ) {
      // TODO: error
      ListActivity.error( "password not defined and username isn't 'anonymous'" );
      status = Status.BAD_PASSWORD;
    }
    else {
      status = doUpload( username, password, bundle );
    }

    // tell the gui thread
    sendBundledMessage( handler, status.ordinal(), bundle );
  }
  
  private static void sendBundledMessage( Handler handler, int what, Bundle bundle ) {
    final Message msg = new Message();
    msg.what = what;
    msg.setData(bundle);
    handler.sendMessage(msg);
  }
  
  private Status doUpload( final String username, final String password, final Bundle bundle ) {    
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    final String filename = "WigleWifi_" + fileDateFormat.format(new Date()) + ".csv.gz";
    
    Status status = Status.UNKNOWN;
    
    try {
      // if ( true ) { throw new IOException( "oh noe" ); }
      final PackageManager pm = context.getPackageManager();
      final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
      
      String openString = filename;
      final boolean hasSD = ListActivity.hasSD();
      File file = null;
      bundle.putString( FILENAME, filename );
      if ( hasSD ) {
        final String filepath = Environment.getExternalStorageDirectory().getCanonicalPath() + "/wiglewifi/";
        final File path = new File( filepath );
        path.mkdirs();
        openString = filepath + filename;
        file = new File( openString );
        if ( ! file.exists() && hasSD ) {
          file.createNewFile();
        }
        bundle.putString( FILEPATH, filepath );
        bundle.putString( FILENAME, filename );
      }
      
      final FileOutputStream rawFos = hasSD ? new FileOutputStream( file )
        : context.openFileOutput( filename, Context.MODE_WORLD_READABLE );

      final GZIPOutputStream fos = new GZIPOutputStream( rawFos );

      final long start = System.currentTimeMillis();
      // name, version
      writeFos( fos, "WigleWifi-1.1"
          + ",appRelease=" + pi.versionName
          + ",model=" + android.os.Build.MODEL
          + ",release=" + android.os.Build.VERSION.RELEASE
          + ",device=" + android.os.Build.DEVICE
          + ",display=" + android.os.Build.DISPLAY
          + ",board=" + android.os.Build.BOARD
          + ",brand=" + android.os.Build.BRAND
          + "\n" );
      // header
      writeFos( fos, "MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters\n" );
      // write file
      final SharedPreferences prefs = context.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
      long maxId = prefs.getLong( ListActivity.PREF_DB_MARKER, 0L );
      final Cursor cursor = dbHelper.locationIterator( maxId );
      int lineCount = 0;
      final int total = cursor.getCount();
      long fileWriteMillis = 0;
      long netMillis = 0;
      
      sendBundledMessage( handler, Status.WRITING.ordinal(), bundle );

      int bytecount = 0;

      if ( total > 0 ) {
        int lastSentPercent = 0;
        CharBuffer charBuffer = CharBuffer.allocate( 256 );
        ByteBuffer byteBuffer = ByteBuffer.allocate( 256 ); // this ensures hasArray() is true
        final CharsetEncoder encoder = Charset.forName( ListActivity.ENCODING ).newEncoder();
        // don't stop when a goofy character is found
        encoder.onUnmappableCharacter( CodingErrorAction.REPLACE );
        final NumberFormat numberFormat = NumberFormat.getNumberInstance( Locale.US );
        if ( numberFormat instanceof DecimalFormat ) {
          final DecimalFormat dc = (DecimalFormat) numberFormat;
          dc.setMaximumFractionDigits( 16 );
        }
        final StringBuffer stringBuffer = new StringBuffer();
        final FieldPosition fp = new FieldPosition(NumberFormat.INTEGER_FIELD);
        final Date date = new Date();
        // loop!
        for ( cursor.moveToFirst(); ! cursor.isAfterLast(); cursor.moveToNext() ) {
          // _id,bssid,level,lat,lon,time
          final long id = cursor.getLong(0);
          if ( id > maxId ) {
            maxId = id;
          }
          final String bssid = cursor.getString(1);
          final long netStart = System.currentTimeMillis();
          final Network network = dbHelper.getNetwork( bssid );
          netMillis += System.currentTimeMillis() - netStart;
          if ( network == null ) {
            // weird condition, skipping
            ListActivity.error("network not in database: " + bssid );
            continue;
          }
          
          lineCount++;
          String ssid = network.getSsid();
          if ( ssid.indexOf( COMMA ) >= 0 ) {
            // comma isn't a legal ssid character, but just in case
            ssid = ssid.replaceAll( COMMA, "_" ); 
          }
          // ListActivity.debug("writing network: " + ssid );
          
          // reset the buffers
          charBuffer.clear();
          byteBuffer.clear();
          // fill in the line
          try {
            charBuffer.append( network.getBssid() );
            charBuffer.append( COMMA );
            // ssid = "ronan stephensÕs iMac";
            charBuffer.append( ssid );
            charBuffer.append( COMMA );
            charBuffer.append( network.getCapabilities() );
            charBuffer.append( COMMA );
            date.setTime( cursor.getLong(7) );
            singleCopyDateFormat( dateFormat, stringBuffer, charBuffer, fp, date );
            charBuffer.append( COMMA );
            Integer channel = network.getChannel();
            if ( channel == null ) {
              channel = network.getFrequency();
            }
            singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, channel );
            charBuffer.append( COMMA );
            singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getInt(2) );
            charBuffer.append( COMMA );
            singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(3) );
            charBuffer.append( COMMA );
            singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(4) );
            charBuffer.append( COMMA );
            singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(5) );
            charBuffer.append( COMMA );
            singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(6) );
            charBuffer.append( NEWLINE );
          }
          catch ( BufferOverflowException ex ) {
            ListActivity.info("buffer overflow: " + ex, ex );
            // double the buffer
            charBuffer = CharBuffer.allocate( charBuffer.capacity() * 2 );
            byteBuffer = ByteBuffer.allocate( byteBuffer.capacity() * 2 );
            // try again
            cursor.moveToPrevious();
            continue;
          }
          
          // tell the encoder to stop here and to start at the beginning
          charBuffer.flip();

          // do the encoding
          encoder.reset();
          encoder.encode( charBuffer, byteBuffer, true );
          encoder.flush( byteBuffer );
          // byteBuffer = encoder.encode( charBuffer );  (old way)
          
          // figure out where in the byteBuffer to stop
          final int end = byteBuffer.position();
          final int offset = byteBuffer.arrayOffset();
          //if ( end == 0 ) {
            // if doing the encode without giving a long-term byteBuffer (old way), the output
            // byteBuffer position is zero, and the limit and capacity are how long to write for.
          //  end = byteBuffer.limit();
          //}
          
          // ListActivity.info("buffer: arrayOffset: " + byteBuffer.arrayOffset() + " limit: " + byteBuffer.limit()
          //     + " capacity: " + byteBuffer.capacity() + " pos: " + byteBuffer.position() + " end: " + end
          //     + " result: " + result );
          final long writeStart = System.currentTimeMillis();
          fos.write(byteBuffer.array(), offset, end+offset );
          fileWriteMillis += System.currentTimeMillis() - writeStart;

          bytecount += end;

          // update UI
          final int percentDone = (lineCount * 100) / total;
          // only send up to 100 times
          if ( percentDone > lastSentPercent ) {
            sendBundledMessage( handler, WRITING_PERCENT_START + percentDone, bundle );
            lastSentPercent = percentDone;
          }
        }
      }
      cursor.close();
      fos.close();
      
      ListActivity.info("wrote file in: " + (System.currentTimeMillis() - start) + "ms. fileWriteMillis: "
          + fileWriteMillis + " netmillis: " + netMillis );
      
      // don't upload empty files
      if ( lineCount == 0 ) {
        return Status.EMPTY_FILE;
      }
      
      // show on the UI
      sendBundledMessage( handler, Status.UPLOADING.ordinal(), bundle );

      long filesize = file != null ? file.length() : 0L;
      if ( filesize <= 0 ) {
        filesize = bytecount; // as an upper bound
      }

      // send file
      final FileInputStream fis = hasSD ? new FileInputStream( file ) 
        : context.openFileInput( filename );
      final Map<String,String> params = new HashMap<String,String>();
      
      params.put("observer", username);
      params.put("password", password);
      final String response = HttpFileUploader.upload( 
        ListActivity.FILE_POST_URL, filename, "stumblefile", fis, 
        params, context.getResources(), handler, filesize, context );
      
      if ( response != null && response.indexOf("uploaded successfully") > 0 ) {
        status = Status.SUCCESS;
        
        // save in the prefs
        final Editor editor = prefs.edit();
        editor.putLong( ListActivity.PREF_DB_MARKER, maxId );
        editor.putLong( ListActivity.PREF_MAX_DB, maxId );
        editor.commit();
      }
      else if ( response != null && response.indexOf("does not match login") > 0 ) {
        status = Status.BAD_LOGIN;
      }
      else {
        String error = null;
        if ( response != null && response.trim().equals( "" ) ) {
          error = "no response from server";
        } 
        else {
          error = "response: " + response;
        }
        ListActivity.error( error );
        bundle.putString( ERROR, error );
        status = Status.FAIL;
      }
    } 
    catch ( final FileNotFoundException ex ) {
      ex.printStackTrace();
      ListActivity.error( "file problem: " + ex, ex );
      ListActivity.writeError( this, ex, context );
      status = Status.EXCEPTION;
      bundle.putString( ERROR, "file problem: " + ex );
    }
    catch ( final IOException ex ) {
      ex.printStackTrace();
      ListActivity.error( "io problem: " + ex, ex );
      ListActivity.writeError( this, ex, context );
      status = Status.EXCEPTION;
      bundle.putString( ERROR, "io problem: " + ex );
    }
    catch ( final Exception ex ) {
      ex.printStackTrace();
      ListActivity.error( "ex problem: " + ex, ex );
      ListActivity.writeError( this, ex, context );
      status = Status.EXCEPTION;
      bundle.putString( ERROR, "ex problem: " + ex );
    }
    
    return status;
  }
  
  public static void writeFos( final OutputStream fos, final String data ) throws IOException, UnsupportedEncodingException {
    if ( data != null ) {
      fos.write( data.getBytes( ListActivity.ENCODING ) );
    }
  }
  
  private void singleCopyNumberFormat( final NumberFormat numberFormat, final StringBuffer stringBuffer, 
      final CharBuffer charBuffer, final FieldPosition fp, final int number ) {
    stringBuffer.setLength( 0 );
    numberFormat.format( number, stringBuffer, fp );
    stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
    charBuffer.position( charBuffer.position() + stringBuffer.length() );
  }
  
  private void singleCopyNumberFormat( final NumberFormat numberFormat, final StringBuffer stringBuffer, 
      final CharBuffer charBuffer, final FieldPosition fp, final double number ) {
    stringBuffer.setLength( 0 );
    numberFormat.format( number, stringBuffer, fp );
    stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
    charBuffer.position( charBuffer.position() + stringBuffer.length() );
  }
  
  private void singleCopyDateFormat( final DateFormat dateFormat, final StringBuffer stringBuffer, 
      final CharBuffer charBuffer, final FieldPosition fp, final Date date ) {
    stringBuffer.setLength( 0 );
    dateFormat.format( date, stringBuffer, fp );
    stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
    charBuffer.position( charBuffer.position() + stringBuffer.length() );
  }
   
}
