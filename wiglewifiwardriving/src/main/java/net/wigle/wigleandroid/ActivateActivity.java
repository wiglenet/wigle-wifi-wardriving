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

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

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
    public static String barcodeIntent = "net.wigle.wigleandroid://activate";

    //log tag for activity
    private static final String LOG_TAG = "wigle.activate";

    private SurfaceView cameraView;
    private CameraSource cameraSource;
    private BarcodeDetector barcodeDetector;

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
        if (ActivateActivity.barcodeIntent.equals(data.toString())) {
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
        barcodeDetector =
                new BarcodeDetector.Builder(this)
                        .setBarcodeFormats(Barcode.QR_CODE)
                        .build();
        if (!barcodeDetector.isOperational()) {
            Toast.makeText(this.getApplicationContext(),
                    "Barcode detection not available on this device", Toast.LENGTH_LONG);
            Log.e(LOG_TAG, "Barcode detection not available on this device.");
            this.finish();
        } else {
            Log.i(LOG_TAG, "Barcode detection available, initializing...");
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                this.requestCameraPermission();
            } else {
                DisplayMetrics metrics = this.getResources().getDisplayMetrics();

                CameraSource.Builder builder =
                        new CameraSource.Builder(getApplicationContext(), barcodeDetector)
                                .setFacing(CameraSource.CAMERA_FACING_BACK)
                                .setRequestedPreviewSize(metrics.heightPixels, metrics.widthPixels)
                                .setAutoFocusEnabled(true)
                                .setRequestedFps(10.0f);

                cameraSource = builder.build();
                Log.i(LOG_TAG, "Camera Source built");
                cameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        try {
                            cameraSource.start(cameraView.getHolder());
                        } catch (IOException ie) {
                            Log.e(LOG_TAG, "CAMERA SOURCE " + ie.getMessage());
                        } catch (SecurityException se) {
                            Log.e(LOG_TAG, "CAMERA SOURCE SECURITY ERROR " + se.getMessage());
                        } catch (Exception ex) {
                            Log.e(LOG_TAG, "CAMERA ERROR " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }

                    @Override
                    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    }

                    @Override
                    public void surfaceDestroyed(SurfaceHolder holder) {
                        cameraSource.stop();
                    }
                });
                barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
                    @Override
                    public void release() {
                        Log.i(LOG_TAG, "CAMERA released");
                    }

                    @Override
                    public void receiveDetections(Detector.Detections<Barcode> detections) {
                        final SparseArray<Barcode> barcodes = detections.getDetectedItems();
                        if (barcodes.size() > 0) {
                            Log.i(LOG_TAG, "CAMERA received detections");
                            Barcode item = barcodes.valueAt(0);
                            if (item.displayValue.matches("^.*:[a-zA-Z0-9]*:[a-zA-Z0-9]*$")) {
                                Log.i(LOG_TAG, item.displayValue + " matched.");
                                String[] tokens = item.displayValue.split(":");
                                final SharedPreferences prefs = MainActivity.getMainActivity().
                                        getSharedPreferences(ListFragment.SHARED_PREFS, 0);
                                final SharedPreferences.Editor editor = prefs.edit();
                                editor.putString(ListFragment.PREF_USERNAME, tokens[0]);
                                editor.putString(ListFragment.PREF_AUTHNAME, tokens[1]);
                                editor.putBoolean(ListFragment.PREF_BE_ANONYMOUS, false);
                                editor.apply();
                                TokenAccess.setApiToken(prefs, tokens[2]);
                                finish();
                            } else {
                                Log.i(LOG_TAG, item.displayValue + " failed to match token pattern");
                            }
                        }
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != cameraSource) {
            cameraSource.release();
        }
        if (null != barcodeDetector) {
            barcodeDetector.release();
        }
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
