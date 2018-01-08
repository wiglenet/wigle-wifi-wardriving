package net.wigle.wigleandroid;

import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
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
        regWebView.setWebChromeClient(new WebChromeClient());
        regWebView.getSettings().setUserAgentString(AGENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        regWebView.addJavascriptInterface(new WiGLERegistrationInterface(this),
                "WiGLEWiFi");
        regWebView.loadUrl(MainActivity.REG_URL);
    }
}
