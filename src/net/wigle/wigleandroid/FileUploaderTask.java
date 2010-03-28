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
import android.os.Handler;
import android.os.Message;

public class FileUploaderTask extends Thread {
  private final Context context;
  private final Handler handler;
  private final DatabaseHelper dbHelper;
  private final ProgressDialog pd;
  
  private static final int WRITING_PERCENT_START = 10000;
  private static final String COMMA = ",";
  private static final String NEWLINE = "\n";
  
  private enum Status {
    UNKNOWN("Unknown", "Unknown error"),
    FAIL( "Fail", "Fail" ),
    SUCCESS( "Success", "Upload Successful"),
    BAD_USERNAME("Fail", "Username not set"),
    BAD_PASSWORD("Fail", "Password not set and username not 'anonymous'"),
    EXCEPTION("Fail", "Exception"),
    BAD_LOGIN("Fail", "Login failed, check password?"),
    UPLOADING("Working...", "Uploading File"),
    WRITING("Working...", "Writing File ");
    
    private final String title;
    private final String message;
    private Status( String title, String message ) {
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
  
  public FileUploaderTask( Context context, DatabaseHelper dbHelper ) {
    this.context = context;
    this.dbHelper = dbHelper;
    
    this.pd = ProgressDialog.show( context, Status.WRITING.title, Status.WRITING.getMessage(), true, false );  
    
    this.handler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
        if ( msg.what >= WRITING_PERCENT_START ) {
          int percent = msg.what - WRITING_PERCENT_START;
          pd.setMessage( Status.WRITING.getMessage() + percent + "%" );
          return;
        }
        
        Status status = Status.values()[ msg.what ];
        if ( Status.UPLOADING.equals( status ) ) {
          pd.setMessage( status.getMessage() );
          return;
        }
        // make sure we didn't progress dialog this somewhere
        if ( pd.isShowing() ) {
          pd.dismiss();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder( FileUploaderTask.this.context );
        builder.setCancelable( false );
        builder.setTitle( status.getTitle() );
        builder.setMessage( status.getMessage() );
        AlertDialog ad = builder.create();
        ad.setButton("OK", new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            return;
          } }); 
        ad.show();
      }
     };
  }
  
  public void run() {
    SharedPreferences prefs = context.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0);
    String username = prefs.getString( WigleAndroid.PREF_USERNAME, "" );
    String password = prefs.getString( WigleAndroid.PREF_PASSWORD, "" );
    Status status = Status.UNKNOWN;
    
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
      status = doUpload( username, password );
    }

    // tell the gui thread
    handler.sendEmptyMessage( status.ordinal() );
  }
  
  private Status doUpload( String username, String password ) {    
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    String filename = "WigleWifi_" + fileDateFormat.format(new Date()) + ".csv.gz";
    String filepath = "/sdcard/wiglewifi/";
    
    Status status = Status.UNKNOWN;
    
    try {
      File sdcard = new File( "/sdcard/" );
      boolean hasSD = sdcard.exists() && sdcard.isDirectory();
      String openString = filename;
      if ( hasSD ) {
        File path = new File( filepath );
        path.mkdirs();
        openString = filepath + filename;
      }
      File file = new File( openString );
      if ( ! file.exists() ) {
        file.createNewFile();
      }
      
      FileOutputStream rawFos = hasSD ? new FileOutputStream( file )
        : context.openFileOutput( filename, Context.MODE_WORLD_READABLE );

      GZIPOutputStream fos = new GZIPOutputStream( rawFos );

      long start = System.currentTimeMillis();
      // name, version
      writeFos( fos, "WigleWifi-1.0\n" );
      // header
      writeFos( fos, "MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters\n" );
      // write file
      SharedPreferences prefs = context.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0);
      long maxId = prefs.getLong( WigleAndroid.PREF_DB_MARKER, 0L );
      Cursor cursor = dbHelper.networkIterator( maxId );
      int lineCount = 0;
      int total = cursor.getCount();
      long fileWriteMillis = 0;
      long netMillis = 0;
      if ( total > 0 ) {
        int lastSentPercent = 0;
        CharBuffer charBuffer = CharBuffer.allocate( 256 );
        ByteBuffer byteBuffer = ByteBuffer.allocate( 256 );
        CharsetEncoder encoder = Charset.forName( WigleAndroid.ENCODING ).newEncoder();
        NumberFormat numberFormat = NumberFormat.getNumberInstance( Locale.US );
        if ( numberFormat instanceof DecimalFormat ) {
          DecimalFormat dc = (DecimalFormat) numberFormat;
          dc.setMaximumFractionDigits( 16 );
        }
        StringBuffer stringBuffer = new StringBuffer();
        FieldPosition fp = new FieldPosition(NumberFormat.INTEGER_FIELD);
        Date date = new Date();
        // loop!
        for ( cursor.moveToFirst(); ! cursor.isAfterLast(); cursor.moveToNext() ) {
          lineCount++;
          
          // _id,bssid,level,lat,lon,time
          long id = cursor.getLong(0);
          if ( id > maxId ) {
            maxId = id;
          }
          String bssid = cursor.getString(1);
          long netStart = System.currentTimeMillis();
          Network network = dbHelper.getNetwork( bssid );
          netMillis += System.currentTimeMillis() - netStart;
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
            singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, network.getChannel() );
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
          
          // tell the encoder to stop here
          charBuffer.limit( charBuffer.position() );
          // tell the encoder to start at the beginning
          charBuffer.position( 0 );
          
          // do the encoding
          encoder.reset();
          encoder.encode( charBuffer, byteBuffer, true );
          encoder.flush( byteBuffer );
          // byteBuffer = encoder.encode( charBuffer );  (old way)
          
          // figure out where in the byteBuffer to stop
          int end = byteBuffer.position();
          //if ( end == 0 ) {
            // if doing the encode without giving a long-term byteBuffer (old way), the output
            // byteBuffer position is zero, and the limit and capacity are how long to write for.
          //  end = byteBuffer.limit();
          //}
          
          // WigleAndroid.info("buffer: arrayOffset: " + byteBuffer.arrayOffset() + " limit: " + byteBuffer.limit()
          //     + " capacity: " + byteBuffer.capacity() + " pos: " + byteBuffer.position() + " end: " + end
          //     + " result: " + result );
          long writeStart = System.currentTimeMillis();
          fos.write(byteBuffer.array(), byteBuffer.arrayOffset(), end );
          fileWriteMillis += System.currentTimeMillis() - writeStart;
          
          // update UI
          int percentDone = (lineCount * 100) / total;
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
      
      // show on the UI
      handler.sendEmptyMessage( Status.UPLOADING.ordinal() );
      
      // send file
      FileInputStream fis = hasSD ? new FileInputStream( file ) 
        : context.openFileInput( filepath );
      Map<String,String> params = new HashMap<String,String>();
      
      params.put("observer", username);
      params.put("password", password);
      String response = HttpFileUploader.upload( WigleAndroid.FILE_POST_URL, filename, "stumblefile", fis, params );
      
      if ( response.indexOf("uploaded successfully") > 0 ) {
        status = Status.SUCCESS;
        
        // save in the prefs
        final Editor editor = prefs.edit();
        editor.putLong( WigleAndroid.PREF_DB_MARKER, maxId );
        editor.commit();
      }
      else if ( response.indexOf("does not match login") > 0 ) {
        status = Status.BAD_LOGIN;
      }
      else {
        WigleAndroid.error("fail: " + response );
        status = Status.FAIL;
      }
    } 
    catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      WigleAndroid.error( "file problem: " + e );
      status = Status.EXCEPTION;
    }
    catch ( IOException ex ) {
      ex.printStackTrace();
      WigleAndroid.error( "file problem: " + ex );
      status = Status.EXCEPTION;
    }
    
    return status;
  }
  
  private void writeFos( OutputStream fos, String data ) throws IOException, UnsupportedEncodingException {
    if ( data != null ) {
      fos.write( data.getBytes( WigleAndroid.ENCODING ) );
    }
  }
  
  private void singleCopyNumberFormat( NumberFormat numberFormat, StringBuffer stringBuffer, CharBuffer charBuffer,
      FieldPosition fp, int number ) {
    stringBuffer.setLength( 0 );
    numberFormat.format( number, stringBuffer, fp );
    stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
    charBuffer.position( charBuffer.position() + stringBuffer.length() );
  }
  
  private void singleCopyNumberFormat( NumberFormat numberFormat, StringBuffer stringBuffer, CharBuffer charBuffer,
      FieldPosition fp, double number ) {
    stringBuffer.setLength( 0 );
    numberFormat.format( number, stringBuffer, fp );
    stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
    charBuffer.position( charBuffer.position() + stringBuffer.length() );
  }
  
  private void singleCopyDateFormat( DateFormat dateFormat, StringBuffer stringBuffer, CharBuffer charBuffer,
      FieldPosition fp, Date date ) {
    stringBuffer.setLength( 0 );
    dateFormat.format( date, stringBuffer, fp );
    stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
    charBuffer.position( charBuffer.position() + stringBuffer.length() );
  }
   
}
