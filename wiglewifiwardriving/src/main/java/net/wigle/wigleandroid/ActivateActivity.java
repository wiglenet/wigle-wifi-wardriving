package net.wigle.wigleandroid;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
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
            CameraSource.Builder builder =
                    new CameraSource.Builder(getApplicationContext(), barcodeDetector)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setRequestedPreviewSize(640, 480).setAutoFocusEnabled(true)
                    .setRequestedFps(10.0f);

            cameraSource = builder.build();
            Log.i(LOG_TAG, "Camera Source built");
            cameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    try {
                        cameraSource.start(cameraView.getHolder());
                    } catch (IOException ie) {
                        Log.e(LOG_TAG, "CAMERA SOURCE"+ ie.getMessage());
                    } catch (SecurityException se) {
                        Log.e(LOG_TAG, "CAMERA SOURCE SECURITY ERROR"+ se.getMessage());
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
                            Log.i(LOG_TAG, item.displayValue+" matched.");
                            String[] tokens = item.displayValue.split(":");
                            final SharedPreferences prefs = MainActivity.getMainActivity().
                                    getSharedPreferences(ListFragment.SHARED_PREFS, 0);
                            final SharedPreferences.Editor editor = prefs.edit();
                            editor.putString(ListFragment.PREF_USERNAME, tokens[0]);
                            editor.putString(ListFragment.PREF_AUTHNAME, tokens[1]);
                            editor.putString(ListFragment.PREF_TOKEN, tokens[2]);
                            //TODO: should we actively unset prefs PREF_PASSWORD here?
                            editor.apply();
                            //DEBUG: Log.i(LOG_TAG, tokens[0]+" : "+tokens[1] + " : "+tokens[2] );
                            finish();
                        } else {
                            Log.i(LOG_TAG, item.displayValue+" failed to match token pattern");
                        }
                    }
                }
            });
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

}
