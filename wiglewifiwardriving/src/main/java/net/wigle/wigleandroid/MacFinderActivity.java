package net.wigle.wigleandroid;

import static net.wigle.wigleandroid.ui.MacFinderListView.listForAddressesAndKnown;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import net.wigle.wigleandroid.ui.MacFinderListAdapter;
import net.wigle.wigleandroid.ui.MacFinderListView;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MacFinderActivity extends AppCompatActivity {
    public static final String MAC_FILTER = "(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}";
    public static final String OUI_FILTER = "(?:[0-9A-Fa-f]{2}[:-]){2}[0-9A-Fa-f]{2}";

    private static final Pattern MAC_REGEX = Pattern.compile(MAC_FILTER);
    private static final Pattern OUI_REGEX = Pattern.compile(OUI_FILTER);
    private TextRecognizer textRecognizer;
    private ExecutorService cameraExecutor;
    private static final int REQUEST_CAMERA = 0;
    private PreviewView previewView;

    private TextView status;
    private ListView list;
    private MacFinderListAdapter adapter;
    ArrayList<MacFinderListView> listItems= new ArrayList<>();

    private String filterKey;
    private List<String> macOuiList;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        setContentView(R.layout.camera_text_recognizer);
        previewView = findViewById(R.id.camera_ocr_view);
        EdgeToEdge.enable(this);
        View backButtonWrapper = findViewById(R.id.recognizer_back_layout);
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

        ImageButton backButton = findViewById(R.id.recognizer_back_button);
        if (null != backButton) {
            backButton.setOnClickListener(v -> {setResult(RESULT_OK); finish();});
        }

        Intent intent = getIntent();
        //what type of filter we're managing
        final String filterType = intent.getStringExtra(MacFilterActivity.SCAN_MAC_FILTER_MESSAGE);
        if (FilterActivity.INTENT_DISPLAY_FILTER.equals(filterType)) {
            filterKey = PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS;
        } else if (FilterActivity.INTENT_LOG_FILTER.equals(filterType)) {
            filterKey = PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS;
        } else if (FilterActivity.INTENT_ALERT_FILTER.equals(filterType)) {
            filterKey = PreferenceKeys.PREF_ALERT_ADDRS;
        } else {
            filterKey = PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS;
        }
        prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        Gson gson = new Gson();
        String[] values = gson.fromJson(prefs.getString(filterKey, "[]"), String[].class);
        if (values.length > 0) {
            //ALIBI: the java.util.Arrays.ArrayList version is immutable, so a new ArrayList must be made from it.
            macOuiList = new ArrayList<>(Arrays.asList(values));
        } else {
            macOuiList = new ArrayList<>();
        }

        status = findViewById(R.id.debug_text_area);
        list = findViewById(R.id.select_macs_and_ouis);
        adapter = new MacFinderListAdapter(this,
                R.layout.mac_oui_list_item, listItems);
        list.setAdapter(adapter);

        list.setOnItemClickListener((adapterView, view, i, l) -> {
            MacFinderListView selected = adapter.getItem(i);
            final boolean checked = null != selected && selected.isChecked();
            final String itemAddress = null != selected ? selected.getName() : null;

            boolean modified = false;
            if (checked && null != itemAddress && macOuiList.contains(itemAddress)) {
                selected.setChecked(true);
                macOuiList.remove(itemAddress);
                modified = true;
                Logging.info("Check: "+ itemAddress);
            } else if (!checked && null != itemAddress) {
                selected.setChecked(false);
                macOuiList.add(itemAddress);
                modified = true;
                Logging.info("Uncheck: "+ itemAddress);
            } else {
                Logging.info("Selected: "+ itemAddress + " checked: " + checked +
                        " in list? "+macOuiList.contains(itemAddress));
            }
            if (modified) {
                Gson gson1 = new Gson();
                String serialized = gson1.toJson(macOuiList.toArray());
                Logging.info(serialized);
                final SharedPreferences.Editor editor = prefs.edit();
                editor.putString(filterKey, serialized);
                editor.apply();
                MainActivity m = MainActivity.getMainActivity();
                if (null != m) {
                    m.updateAddressFilter(filterKey);
                }
            }
        });
        cameraExecutor = Executors.newSingleThreadExecutor();
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestCameraPermission();
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
                    textRecognizer.process(inputImage)
                            .addOnSuccessListener(visionText -> {
                                Set<String> matches = new HashSet<>();
                                for (Text.TextBlock t: visionText.getTextBlocks()) {
                                    //Logging.info(t.getText());
                                    if (!t.getText().isEmpty()) {
                                        Matcher macMatcher = MAC_REGEX.matcher(t.getText());
                                        Matcher ouiMatcher = OUI_REGEX.matcher(t.getText());
                                        while (macMatcher.find()) {
                                            //Logging.info("MAC MATCH: " + t.getBoundingBox());
                                            matches.add(macMatcher.group());
                                        }
                                        while (ouiMatcher.find()) {
                                            //Logging.info("OUI MATCH: "+ t.getBoundingBox());
                                            matches.add(ouiMatcher.group());
                                        }/* else {

                                        }*/
                                    }
                                }
                                if (null != status) {
                                    status.setText(matches.toString());
                                }
                                if (null != list) {
                                    adapter.clear();
                                    adapter.addAll(listForAddressesAndKnown(matches, macOuiList));
                                    adapter.sort(Comparator.comparing(MacFinderListView::getName));
                                    runOnUiThread(() -> {adapter.notifyDataSetChanged();});
                                }
                                image.close();
                            })
                            .addOnFailureListener( e -> {
                                    Logging.error("Failed to process image for text: ",e);
                                    image.close();
                            });
                }
                //image.close();
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
            //cameraProvider.unbindAll();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
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
    protected void onDestroy() {
        super.onDestroy();
        if (null != textRecognizer) {
            textRecognizer.close();
        }
        if (null != cameraExecutor) {
            cameraExecutor.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CAMERA: {
                Logging.info( "Camera response permissions: " + Arrays.toString(permissions)
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
            }
            default:
                Logging.info( "Unhandled onRequestPermissionsResult code: " + requestCode);
        }
    }

    private void requestCameraPermission() {
        Logging.info( "Camera permissions have NOT been granted. Requesting....");
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA},
                REQUEST_CAMERA);
    }

}
