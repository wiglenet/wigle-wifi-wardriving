package net.wigle.wigleandroid.background;

import android.app.Activity;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.TokenAccess;
import net.wigle.wigleandroid.util.Logging;

/**
 * Created by arkasha on 1/8/18.
 */

public class WiGLERegistrationInterface {
    private final Activity activity;

    public WiGLERegistrationInterface(Activity activity) {
        this.activity = activity;
    }


    /**
     * update the users authentication preferences from JS running in the page
     *
     * @param userName
     * @param userId
     * @param token
     */
    @JavascriptInterface
    public void registrationComplete(final String userName, final String userId, final String token) {

        Logging.info("Successful registration for "+userName+ " auth ID: "+userId);
        final SharedPreferences prefs = MainActivity.getMainActivity().
                getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString(ListFragment.PREF_USERNAME, userName);
        editor.putString(ListFragment.PREF_AUTHNAME, userId);
        editor.putBoolean(ListFragment.PREF_BE_ANONYMOUS, false);
        editor.apply();
        TokenAccess.setApiToken(prefs, token);
        activity.finish();
    }

    /**
     * exit webview activity on reg cancel
     */
    @JavascriptInterface
    public void registrationCancelled() {
        activity.finish();
    }
}
