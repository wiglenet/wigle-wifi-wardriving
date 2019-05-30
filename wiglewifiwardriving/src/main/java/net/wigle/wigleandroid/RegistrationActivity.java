package net.wigle.wigleandroid;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import net.wigle.wigleandroid.background.WiGLERegistrationInterface;

/**
 * Created by arkasha on 1/8/18.
 */

public class RegistrationActivity extends AppCompatActivity {

    public static final String AGENT = "WiGLE WiFi Registrant";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);
        WebView regWebView = (WebView) findViewById(R.id.wigle_registration_container);
        WebSettings webSettings = regWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        regWebView.clearCache(true);
        MainActivity mActivity = MainActivity.getMainActivity();
        if (mActivity != null) {
            clearCookies(mActivity.getApplicationContext());
        }
        regWebView.setWebChromeClient(new WebChromeClient());
        regWebView.getSettings().setUserAgentString(AGENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        regWebView.addJavascriptInterface(new WiGLERegistrationInterface(this),
                "WiGLEWiFi");
        regWebView.loadUrl(MainActivity.REG_URL);
    }

    /**
     * dammit, android. stolen from:
     * https://stackoverflow.com/questions/28998241/how-to-clear-cookies-and-cache-of-webview-on-android-when-not-in-webview
     */
    @SuppressWarnings("deprecation")
    protected void clearCookies(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } else  {
            CookieSyncManager cookieSyncMngr=CookieSyncManager.createInstance(context);
            cookieSyncMngr.startSync();
            CookieManager cookieManager=CookieManager.getInstance();
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            cookieSyncMngr.stopSync();
            cookieSyncMngr.sync();
        }
    }
}
