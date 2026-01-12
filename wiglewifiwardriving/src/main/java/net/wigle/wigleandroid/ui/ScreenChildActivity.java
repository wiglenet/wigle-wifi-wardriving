package net.wigle.wigleandroid.ui;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import net.wigle.wigleandroid.MainActivity;

/**
 * A child AppCompatActivity that follows WiGLE's MainActivity.state.screenLocked behavior while the MainActivity is paused.
 * Created by arkasha on 20231017.
 */
public class ScreenChildActivity extends AppCompatActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final MainActivity ma = MainActivity.getMainActivity();
        if (null != ma) {
            if (ma.hasWakeLock()) {
                //NB: changes to the parent activity state won't be reflected here.
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }
}
