package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * here for backwards compatibility
 */
public class WigleAndroid extends Activity {
  @Override
  public void onCreate( final Bundle savedInstanceState ) {
    super.onCreate( savedInstanceState );
    
    // start tab activity
    final Intent intent = new Intent( this, MainActivity.class );
    this.startActivity( intent );
    // done
    finish();
  }
}
