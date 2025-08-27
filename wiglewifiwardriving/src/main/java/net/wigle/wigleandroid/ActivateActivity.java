package net.wigle.wigleandroid;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.util.Log;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.ImageButton;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * fetch wigle authentication tokens by scanning a barcode
 * @author rksh
 */
public class ActivateActivity extends AppCompatActivity {

    //intent string
    public static final String barcodeIntent = "net.wigle.wigleandroid://activate";

    //log tag for activity
    private static final String LOG_TAG = "wigle.activate";

    private BarcodeScanner barcodeScanner;
    private ExecutorService cameraExecutor;

    private PreviewView cameraView;

    private static final int REQUEST_CAMERA = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activate);

        EdgeToEdge.enable(this);
        View backButtonWrapper = findViewById(R.id.activate_back_layout);
        if (null != backButtonWrapper) {
            ViewCompat.setOnApplyWindowInsetsListener(backButtonWrapper, new OnApplyWindowInsetsListener() {
                        @Override
                        public @org.jspecify.annotations.NonNull
                        WindowInsetsCompat onApplyWindowInsets(@org.jspecify.annotations.NonNull View v,
                                                               @org.jspecify.annotations.NonNull WindowInsetsCompat insets) {
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

        ImageButton backButton = findViewById(R.id.activate_back_button);
        if (null != backButton) {
            backButton.setOnClickListener(v -> finish());
        }

        Uri data = getIntent().getData();
        //DEBUG Logging.info("intent data: "+data+" matches: "+
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
        cameraView = findViewById(R.id.camera_view);
        BarcodeScannerOptions options =
                new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                                Barcode.FORMAT_QR_CODE)
                        .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        cameraExecutor = Executors.newSingleThreadExecutor();
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != barcodeScanner) {
            barcodeScanner.close();
        }
        if (null != cameraExecutor) {
            cameraExecutor.shutdown();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindImageAnalysis(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Logging.error("Failed to start camera: ",e);
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private void bindImageAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder().setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new ImageAnalysis.Analyzer() {
            @OptIn(markerClass = ExperimentalGetImage.class)
            @Override
            public void analyze(@NonNull ImageProxy image) {
                Image mediaImage = image.getImage();
                if (mediaImage != null) {
                    InputImage inputImage =
                            InputImage.fromMediaImage(mediaImage, image.getImageInfo().getRotationDegrees());
                    barcodeScanner.process(inputImage).addOnSuccessListener(
                            barcodes -> {
                                if (!barcodes.isEmpty()) {
                                    Logging.info("received detections");
                                    for (Barcode qr : barcodes) {
                                        if (qr.getDisplayValue() != null && qr.getDisplayValue().matches("^.*:[a-zA-Z0-9]*:[a-zA-Z0-9]*$")) {
                                            String[] tokens = qr.getDisplayValue().split(":");

                                            final SharedPreferences prefs = MainActivity.getMainActivity().
                                                    getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
                                            final SharedPreferences.Editor editor = prefs.edit();
                                            editor.putString(PreferenceKeys.PREF_USERNAME, tokens[0]);
                                            editor.putString(PreferenceKeys.PREF_AUTHNAME, tokens[1]);
                                            editor.putBoolean(PreferenceKeys.PREF_BE_ANONYMOUS, false);
                                            editor.apply();
                                            TokenAccess.setApiToken(prefs, tokens[2]);
                                            MainActivity.refreshApiManager();
                                            image.close();
                                            finish();
                                        }
                                    }
                                }
                                image.close();
                            }).addOnFailureListener(e -> {
                                    Logging.error("Failed to process image for barcodes: ",e);
                                    image.close();
                                });
                            }
            }});

        OrientationEventListener orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                //textView.setText(Integer.toString(orientation));
            }
        };
        orientationEventListener.enable();
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        try {
            preview.setSurfaceProvider(cameraView.getSurfaceProvider());
            Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);
        } catch(Exception e) {
            Logging.error("failed to bind to preview: ", e);
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            Logging.info("Camera response permissions: " + Arrays.toString(permissions)
                    + " grantResults: " + Arrays.toString(grantResults));
            List<String> deniedPermissions = new ArrayList<>();
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i]);
                }
            }
            if (deniedPermissions.isEmpty()) {
                startCamera();
            } else {
                finish();
            }
        } else {
            Logging.info("Unhandled onRequestPermissionsResult code: " + requestCode);
        }
    }

    private void requestCameraPermission() {
        Logging.info( "Camera permissions have NOT been granted. Requesting....");
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA},
                REQUEST_CAMERA);
    }
}
