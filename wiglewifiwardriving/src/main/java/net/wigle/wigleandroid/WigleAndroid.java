package net.wigle.wigleandroid;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

/**
 * here for backwards compatibility
 */
public class WigleAndroid extends ActionBarActivity {
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
