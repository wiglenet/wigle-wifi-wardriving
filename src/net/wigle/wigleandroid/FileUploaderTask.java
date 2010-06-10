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
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

public final class FileUploaderTask extends Thread {
  private final Context context;
  private final Handler handler;
  private final DatabaseHelper dbHelper;
  private final ProgressDialog pd;
  
  static final int WRITING_PERCENT_START = 10000;
  private static final String COMMA = ",";
  private static final String NEWLINE = "\n";
  private static final String ERROR = "error";
  
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
  
  public FileUploaderTask( final Context context, final DatabaseHelper dbHelper ) {
    this.context = context;
    this.dbHelper = dbHelper;
    
    this.pd = ProgressDialog.show( context, Status.WRITING.getTitle(), Status.WRITING.getMessage(), true, false );  
    
    this.handler = new Handler() {
            private String msg_text = "";
      @Override
      public void handleMessage( final Message msg ) {
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
        if ( pd.isShowing() ) {
          pd.dismiss();
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder( FileUploaderTask.this.context );
        builder.setCancelable( false );
        builder.setTitle( status.getTitle() );
        Bundle bundle = msg.peekData();
        if ( bundle == null ) {
          builder.setMessage( status.getMessage() );
        }
        else {
          String error = bundle.getString( ERROR );
          builder.setMessage( status.getMessage() + " Error: " + error );
        }
        final AlertDialog ad = builder.create();
        ad.setButton( "OK", new DialogInterface.OnClickListener() {
          public void onClick( final DialogInterface dialog, final int which ) {
            dialog.dismiss();
            return;
          } }); 
        ad.show();
      }
     };
  }
  
  public void run() {
    try {
      doRun();
    }
    catch ( final Throwable throwable ) {
      WigleAndroid.writeError( Thread.currentThread(), throwable );
      throw new RuntimeException( "FileUploaderTask throwable: " + throwable, throwable );
    }
  }
  
  private void doRun() {
    final SharedPreferences prefs = context.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0);
    final String username = prefs.getString( WigleAndroid.PREF_USERNAME, "" );
    final String password = prefs.getString( WigleAndroid.PREF_PASSWORD, "" );
    Status status = Status.UNKNOWN;
    Bundle bundle = new Bundle();
    
    if ( "".equals( username ) ) {
      // TODO: error
      WigleAndroid.error( "username not defined" );
      status = Status.BAD_USERNAME;
    }
    else if ( "".equals( password ) && ! WigleAndroid.ANONYMOUS.equals( username.toLowerCase() ) ) {
      // TODO: error
      WigleAndroid.error( "password not defined and username isn't 'anonymous'" );
      status = Status.BAD_PASSWORD;
    }
    else {
      status = doUpload( username, password, bundle );
    }

    // tell the gui thread
    String error = bundle.getString( ERROR );
    if ( error == null ) { 
      handler.sendEmptyMessage( status.ordinal() );
    }
    else {
      Message msg = new Message();
      msg.what = status.ordinal();
      msg.setData(bundle);
      handler.sendMessage(msg);
    }
  }
  
  private Status doUpload( final String username, final String password, final Bundle bundle ) {    
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    final String filename = "WigleWifi_" + fileDateFormat.format(new Date()) + ".csv.gz";
    
    Status status = Status.UNKNOWN;
    
    try {
      // if ( true ) { throw new IOException( "oh noe" ); }
      
      String openString = filename;
      final boolean hasSD = WigleAndroid.hasSD();
      if ( hasSD ) {
        final String filepath = Environment.getExternalStorageDirectory().getCanonicalPath() + "/wiglewifi/";
        final File path = new File( filepath );
        path.mkdirs();
        openString = filepath + filename;
      }
      final File file = new File( openString );
      if ( ! file.exists() ) {
        file.createNewFile();
      }
      
      final FileOutputStream rawFos = hasSD ? new FileOutputStream( file )
        : context.openFileOutput( filename, Context.MODE_WORLD_READABLE );

      final GZIPOutputStream fos = new GZIPOutputStream( rawFos );

      final long start = System.currentTimeMillis();
      // name, version
      writeFos( fos, "WigleWifi-1.0\n" );
      // header
      writeFos( fos, "MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters\n" );
      // write file
      final SharedPreferences prefs = context.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0);
      long maxId = prefs.getLong( WigleAndroid.PREF_DB_MARKER, 0L );
      final Cursor cursor = dbHelper.networkIterator( maxId );
      int lineCount = 0;
      final int total = cursor.getCount();
      long fileWriteMillis = 0;
      long netMillis = 0;
      
      handler.sendEmptyMessage( Status.WRITING.ordinal() );

      int bytecount = 0;

      if ( total > 0 ) {
        int lastSentPercent = 0;
        CharBuffer charBuffer = CharBuffer.allocate( 256 );
        ByteBuffer byteBuffer = ByteBuffer.allocate( 256 ); // this ensures hasArray() is true
        final CharsetEncoder encoder = Charset.forName( WigleAndroid.ENCODING ).newEncoder();
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
            WigleAndroid.error("network not in database: " + bssid );
            continue;
          }
          
          lineCount++;
          String ssid = network.getSsid();
          if ( ssid.indexOf( COMMA ) >= 0 ) {
            // comma isn't a legal ssid character, but just in case
            ssid = ssid.replaceAll( COMMA, "_" ); 
          }
          // WigleAndroid.debug("writing network: " + ssid );
          
          // reset the buffers
          charBuffer.clear();
          byteBuffer.clear();
          // fill in the line
          try {
            charBuffer.append( network.getBssid() );
            charBuffer.append( COMMA );
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
            WigleAndroid.info("buffer overflow: " + ex );
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
          
          // WigleAndroid.info("buffer: arrayOffset: " + byteBuffer.arrayOffset() + " limit: " + byteBuffer.limit()
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
            handler.sendEmptyMessage( WRITING_PERCENT_START + percentDone );
            lastSentPercent = percentDone;
          }
        }
      }
      cursor.close();
      fos.close();
      
      WigleAndroid.info("wrote file in: " + (System.currentTimeMillis() - start) + "ms. fileWriteMillis: "
          + fileWriteMillis + " netmillis: " + netMillis );
      
      // don't upload empty files
      if ( lineCount == 0 ) {
          return Status.EMPTY_FILE;
      }
      
      // show on the UI
      handler.sendEmptyMessage( Status.UPLOADING.ordinal() );

      long filesize = file.length();
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
                                                      WigleAndroid.FILE_POST_URL, filename, "stumblefile", fis, 
                                                      params, context.getResources(), handler, filesize );
      
      if ( response != null && response.indexOf("uploaded successfully") > 0 ) {
        status = Status.SUCCESS;
        
        // save in the prefs
        final Editor editor = prefs.edit();
        editor.putLong( WigleAndroid.PREF_DB_MARKER, maxId );
        editor.commit();
      }
      else if ( response != null && response.indexOf("does not match login") > 0 ) {
        status = Status.BAD_LOGIN;
      }
      else {
          if ( response != null && response.trim().equals( "" ) ) {
              WigleAndroid.error("fail: no response from server" );
          } else {
              WigleAndroid.error("fail: " + response );
          }
        status = Status.FAIL;
      }
    } 
    catch ( final FileNotFoundException e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      WigleAndroid.error( "file problem: " + e );
      status = Status.EXCEPTION;
      bundle.putString( ERROR, "file problem: " + e );
    }
    catch ( final IOException ex ) {
      ex.printStackTrace();
      WigleAndroid.error( "io problem: " + ex );
      status = Status.EXCEPTION;
      bundle.putString( ERROR, "io problem: " + ex );
    }
    catch ( final Exception ex ) {
      ex.printStackTrace();
      WigleAndroid.error( "ex problem: " + ex );
      status = Status.EXCEPTION;
      bundle.putString( ERROR, "ex problem: " + ex );
    }
    
    return status;
  }
  
  private void writeFos( final OutputStream fos, final String data ) throws IOException, UnsupportedEncodingException {
    if ( data != null ) {
      fos.write( data.getBytes( WigleAndroid.ENCODING ) );
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
