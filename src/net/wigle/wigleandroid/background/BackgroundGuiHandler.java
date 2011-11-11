package net.wigle.wigleandroid.background;

import net.wigle.wigleandroid.ListActivity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.WindowManager;

public class BackgroundGuiHandler extends Handler {
  public static final int WRITING_PERCENT_START = 100000;
  public static final String ERROR = "error";
  public static final String FILENAME = "filename";
  public static final String FILEPATH = "filepath";
  
  private final Context context;
  private final Object lock;
  private final ProgressDialog pd;
  private final AlertSettable alertSettable;
  
  private String msg_text = "";
  
  public BackgroundGuiHandler(final Context context, final Object lock, final ProgressDialog pd,
      final AlertSettable alertSettable) {
    
    this.context = context;
    this.lock = lock;
    this.pd = pd;
    this.alertSettable = alertSettable;          
  }
  
  @Override
  public void handleMessage( final Message msg ) {
    synchronized ( lock ) {
      if ( msg.what >= WRITING_PERCENT_START ) {
        final int percentTimesTen = msg.what - WRITING_PERCENT_START;
        pd.setMessage( msg_text + " " + (percentTimesTen/10f) + "%" );
        // "The progress range is 0..10000."
        pd.setProgress( percentTimesTen * 10 );
        return;
      }
      
      if ( msg.what >= Status.values().length || msg.what < 0 ) {
        ListActivity.error( "msg.what: " + msg.what + " out of bounds on Status values");
        return;
      }
      final Status status = Status.values()[ msg.what ];
      if ( Status.UPLOADING.equals( status ) ) {
        //          pd.setMessage( status.getMessage() );
        msg_text = context.getString( status.getMessage() );
        pd.setProgress(0);
        return;
      }
      if ( Status.WRITING.equals( status ) ) {
        msg_text = context.getString( status.getMessage() );
        pd.setProgress(0);
        return;
      }
      // make sure we didn't progress dialog this somewhere
      if ( pd != null && pd.isShowing() ) {
        try {
          pd.dismiss();
          alertSettable.clearProgressDialog();
        }
        catch ( Exception ex ) {
          // guess it wasn't there anyways
          ListActivity.info( "exception dismissing dialog: " + ex );
        }
      }
      // Activity context
      alertSettable.setAlertDialog( buildAlertDialog( context, msg, status ) );
    }
  }
  
  public static AlertDialog buildAlertDialog( final Context context, final Message msg, final Status status ) {
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
        int index = filename.indexOf( ".gz" );
        if ( index > 0 ) {
          filename = filename.substring( 0, index );
        }
        index = filename.indexOf( ".kml" );
        if ( index > 0 ) {
          filename = filename.substring( 0, index );
        }
      }
      if ( filename == null ) {
        filename = "";
      }
      else {
        filename = "\n\nFile location:\n" + filepath + filename;
      }
    }
    
    if ( bundle == null ) {
      builder.setMessage( context.getString( status.getMessage() ) + filename );
    }
    else {
      String error = bundle.getString( ERROR );
      error = error == null ? "" : " Error: " + error;
      builder.setMessage( context.getString( status.getMessage() ) + error + filename );
    }
    AlertDialog ad = builder.create();
    ad.setButton( DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
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
    
    return ad;
  }
  
  
}  

