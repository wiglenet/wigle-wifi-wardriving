package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by bobzilla on 8/28/16
 */
public class ShareActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        // Get the intent that started this activity
        final Intent intent = getIntent();

        // Figure out what to do based on the intent type
        MainActivity.info("ShareActivity intent type: " + intent.getAction());
        switch (intent.getAction()) {
            case Intent.ACTION_INSERT:
                MainActivity.getMainActivity().handleScanChange(true);
                break;
            case Intent.ACTION_DELETE:
                MainActivity.getMainActivity().handleScanChange(false);
                break;
            case Intent.ACTION_SYNC:
                MainActivity.getMainActivity().doUpload();
                break;
            default:
                MainActivity.info("Unhandled intent action: " + intent.getAction());
        }

        Intent result = new Intent("com.example.RESULT_ACTION");
        setResult(Activity.RESULT_OK, result);
        finish();
    }
}
