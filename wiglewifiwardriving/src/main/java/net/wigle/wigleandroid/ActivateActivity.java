package net.wigle.wigleandroid;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
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
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.nio.ByteBuffer;
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

    private QRCodeReader qrCodeReader;
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
        qrCodeReader = new QRCodeReader();
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
        qrCodeReader = null;
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
        imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @OptIn(markerClass = ExperimentalGetImage.class)
            @Override
            public void analyze(@NonNull ImageProxy image) {
                Image mediaImage = image.getImage();
                if (mediaImage != null && mediaImage.getFormat() == ImageFormat.YUV_420_888 && qrCodeReader != null) {
                    try {
                        Image.Plane yPlane = mediaImage.getPlanes()[0];
                        ByteBuffer yBuffer = yPlane.getBuffer();
                        int yRowStride = yPlane.getRowStride();
                        int yPixelStride = yPlane.getPixelStride();
                        int width = mediaImage.getWidth();
                        int height = mediaImage.getHeight();
                        
                        byte[] yBytes = new byte[yBuffer.remaining()];
                        yBuffer.get(yBytes);
                        yBuffer.rewind();
                        
                        // Create a LuminanceSource that handles row stride correctly
                        LuminanceSource source = new YPlaneLuminanceSource(yBytes, width, height, yRowStride, yPixelStride);
                        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                        
                        Result result = qrCodeReader.decode(bitmap);
                        if (result != null && result.getText() != null) {
                            String qrText = result.getText();
                            Logging.info("received QR code detection: " + qrText);
                            if (qrText.matches("^.*:[a-zA-Z0-9]*:[a-zA-Z0-9]*$")) {
                                String[] tokens = qrText.split(":");
                                
                                runOnUiThread(() -> {
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
                                });
                                return;
                            }
                        }
                    } catch (NotFoundException e) {
                        // QR code not found in this frame, continue scanning
                    } catch (ChecksumException | FormatException e) {
                        Logging.error("Failed to decode QR code: ", e);
                    } catch (Exception e) {
                        Logging.error("Failed to process image for QR codes: ", e);
                    }
                } else {
                    Logging.error("Failed to process image for QR codes - incorrect image type.");
                }
                image.close();
            }
        });

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
    
    /**
     * Custom LuminanceSource for Y plane data from YUV_420_888 images
     */
    private static class YPlaneLuminanceSource extends LuminanceSource {
        private final byte[] yBytes;
        private final int dataWidth;
        private final int dataHeight;
        private final int rowStride;
        private final int pixelStride;
        
        YPlaneLuminanceSource(byte[] yBytes, int width, int height, int rowStride, int pixelStride) {
            super(width, height);
            this.yBytes = yBytes;
            this.dataWidth = width;
            this.dataHeight = height;
            this.rowStride = rowStride;
            this.pixelStride = pixelStride;
        }
        
        @Override
        public byte[] getRow(int y, byte[] row) {
            if (y < 0 || y >= getHeight()) {
                throw new IllegalArgumentException("Requested row is outside the image: " + y);
            }
            int width = getWidth();
            if (row == null || row.length < width) {
                row = new byte[width];
            }
            int offset = y * rowStride;
            if (pixelStride == 1) {
                System.arraycopy(yBytes, offset, row, 0, width);
            } else {
                for (int x = 0; x < width; x++) {
                    row[x] = yBytes[offset + x * pixelStride];
                }
            }
            return row;
        }
        
        @Override
        public byte[] getMatrix() {
            int width = getWidth();
            int height = getHeight();
            if (rowStride == width && pixelStride == 1) {
                return yBytes.clone();
            }
            byte[] matrix = new byte[width * height];
            int outputOffset = 0;
            for (int y = 0; y < height; y++) {
                int inputOffset = y * rowStride;
                for (int x = 0; x < width; x++) {
                    matrix[outputOffset++] = yBytes[inputOffset + x * pixelStride];
                }
            }
            return matrix;
        }
        
        @Override
        public boolean isCropSupported() {
            return true;
        }
        
        @Override
        public LuminanceSource crop(int left, int top, int width, int height) {
            return super.crop(left, top, width, height);
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
