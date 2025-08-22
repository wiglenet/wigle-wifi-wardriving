package net.wigle.wigleandroid;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import net.wigle.wigleandroid.ui.ScreenChildActivity;

/**
 * display latest logs.
 * allow the logs to be emailed to us.
 * @author bobzilla
 *
 */
public class DebugActivity extends ScreenChildActivity {
    private static final int MENU_EXIT = 11;
    private static final int MENU_EMAIL = 12;

    @Override
    public void onCreate( final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // set language
        MainActivity.setLocale(this);
        setContentView(R.layout.debug);
        setupSwipeRefresh();
        updateView();
        EdgeToEdge.enable(this);
        View wrapperLayout = findViewById(R.id.debug_wrapper);
        if (null != wrapperLayout) {
            ViewCompat.setOnApplyWindowInsetsListener(wrapperLayout, new OnApplyWindowInsetsListener() {
                        @Override
                        public @org.jspecify.annotations.NonNull WindowInsetsCompat onApplyWindowInsets(@org.jspecify.annotations.NonNull View v, @org.jspecify.annotations.NonNull WindowInsetsCompat insets) {
                            final Insets innerPadding = insets.getInsets(
                                    WindowInsetsCompat.Type.statusBars() |
                                            WindowInsetsCompat.Type.displayCutout());
                            v.setPadding(
                                    innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom
                            );
                            return insets;
                        }
                    }
            );
        }

        final ImageButton backButton = findViewById(R.id.debug_back_button);
        if (null != backButton) {
            backButton.setOnClickListener(v -> finish());
        }
        final ImageButton shareButton = findViewById(R.id.debug_share_button);
        if (shareButton != null) {
            shareButton.setOnClickListener(v -> setupEmail());
        }
    }

    private void setupSwipeRefresh() {
        // Lookup the swipe container view
        final SwipeRefreshLayout swipeContainer = findViewById(R.id.uploads_swipe_container);

        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Your code to refresh the list here.
                // Make sure you call swipeContainer.setRefreshing(false)
                // once the network request has completed successfully.
                updateView();
                swipeContainer.setRefreshing(false);
            }
        });
    }

    private void updateView() {
        // set on view
        final TextView tv = findViewById( R.id.debugreport );
        //ALIBI: calling append() on a TextView directly (especially inside a loop) considered unreliable
        StringBuilder sb = new StringBuilder();
        for (final String log : MainActivity.getLogLines()) {
            sb.append(log).append("\n");
        }
        tv.setText( sb.toString() );
    }

    private void setupEmail() {
        final StringBuilder sb = new StringBuilder("Log Data:\n\n");
        for (final String log : MainActivity.getLogLines()) {
            sb.append(log);
            sb.append("\n");
        }

        final Intent emailIntent = new Intent( Intent.ACTION_SEND );
        // don't encourage people to send us their random logs.
        // emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"wiwiwa@wigle.net"} );
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "WigleWifi log report" );
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        final Intent chooserIntent = Intent.createChooser( emailIntent, "Email WigleWifi log report?" );
        startActivity( chooserIntent );
    }

    /* Creates the menu items */
    @Override
    public boolean onCreateOptionsMenu( final Menu menu ) {
        MenuItem item = menu.add(0, MENU_EXIT, 0, getString(R.string.menu_return));
        item.setIcon( android.R.drawable.ic_media_previous );

        item = menu.add(0, MENU_EMAIL, 0, getString(R.string.send_to) + "...");
        item.setIcon( android.R.drawable.ic_menu_send );

        return true;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item ) {
        switch ( item.getItemId() ) {
            case MENU_EXIT:
                finish();
                return true;
            case MENU_EMAIL:
                setupEmail();
                return true;
        }
        return false;
    }

}
