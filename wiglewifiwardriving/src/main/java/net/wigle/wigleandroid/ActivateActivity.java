package net.wigle.wigleandroid;

import android.*;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/**
 * fetch wigle authentication tokens by scanning a barcode
 * @Author: rksh
 */
public class ActivateActivity extends Activity {

    //intent string
    public static final String barcodeIntent = "net.wigle.wigleandroid://activate";

    //log tag for activity
    private static final String LOG_TAG = "wigle.activate";

    private SurfaceView cameraView;

    private static final int REQUEST_CAMERA = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activate);

        //TODO: figure out what checks (if any) make manual setup necessary with AppCompat theme
        //if (Build.VERSION.SDK_INT >= 11) {
        //    getActionBar().setDisplayHomeAsUpEnabled(true);
        //}

        Uri data = getIntent().getData();
        //DEBUG Log.i(LOG_TAG, "intent data: "+data+" matches: "+
        //        ActivateActivity.barcodeIntent.equals(data.toString()));
        if (data != null && ActivateActivity.barcodeIntent.equals(data.toString())) {
            launchBarcodeScanning();
        } else {
            Log.e(LOG_TAG, "intent data: "+data+" did not match "+ActivateActivity.barcodeIntent);
            finish();
        }
    }

    private void launchBarcodeScanning() {
        setContentView(R.layout.activity_activate);
        cameraView = (SurfaceView)findViewById(R.id.camera_view);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.e(LOG_TAG, "Attempt to initialize camera capture with a pre-SDKv23 client");
            return;
        }
        Log.e(LOG_TAG, "FOSS has no barcode scanning play services");
        return;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "onDestroy");
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String permissions[],
                                           @NonNull final int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA: {
                Log.i(LOG_TAG, "location grant response permissions: " + Arrays.toString(permissions)
                        + " grantResults: " + Arrays.toString(grantResults));
                launchBarcodeScanning();
                return;
            }

            default:
                Log.w(LOG_TAG, "Unhandled onRequestPermissionsResult code: " + requestCode);
        }
    }

    private void requestCameraPermission() {
        Log.i(LOG_TAG, "CAMERA permission has NOT been granted. Requesting permission.");

        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA},
                REQUEST_CAMERA);
    }

}
