package net.wigle.wigleandroid.util;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.app.Activity;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import net.wigle.wigleandroid.BuildConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class HeadingManager implements SensorEventListener {
    private SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private boolean hasAccelerometer = false;
    private boolean hasMagnetometer = false;
    private float compassAzimuthDegrees;
    private float compassPitchDegrees;
    private float trueHeading;
    private final float[] mR = new float[9];
    private final float[] mOrientation = new float[3];
    private Context context;
    private final Map<Integer,Integer> accuracyBySensorType = new HashMap<>();

    private static final List<String> typeEmoji = new ArrayList<>(Arrays.asList("?",
            "\ud83e\udded \u2699", //compass, gear - accelerometer
            "\ud83e\udded \ud83e\uddf2")); //compass, magnet - magnetic compass
    private static final List<String> qualityEmoji = new ArrayList<>(
            Arrays.asList("\ud83d\udd34", // red
                    "\ud83d\udfe0", // orange
                    "\ud83d\udfe1", // yellow
                    "\ud83d\udfe2", //green
             "?"));

    public static final Boolean DEBUG = false;

    private HeadingManager() {}

    public HeadingManager(final Context c) {
        this.context = c;
        sensorManager = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
    }

    public void startSensor() {
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_GAME, SensorManager.SENSOR_DELAY_GAME);
        }

        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_GAME, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stopSensor() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //TODO: API33 has "firstEventAfterDiscontinuity"
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, event.values.length);
            hasAccelerometer = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, event.values.length);
            hasMagnetometer = true;
        }
        if (hasMagnetometer && hasAccelerometer) {
            if (SensorManager.getRotationMatrix(mR, null, accelerometerReading, magnetometerReading)) {
                SensorManager.getOrientation(mR, mOrientation);
                float azimuthInRadians = mOrientation[0];
                float pitchInRadians = mOrientation[1];
                //DEBUG: this would compensate for the phone being upside-down
                //compassAzimuthDegrees = (float) (Math.toDegrees(azimuthInRadians) + ((pitchInRadians > 0)?180f/*ALIBI: phone upside down*/:0f));
                compassAzimuthDegrees = (float) (Math.toDegrees(azimuthInRadians));
                compassPitchDegrees = (float) (Math.toDegrees(pitchInRadians));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        final int sensorType = sensor.getType();
        final Integer type = accuracyBySensorType.get(sensorType);
        if (!accuracyBySensorType.containsKey(sensorType) ||
                (null != type && type.equals(accuracy))) {
            accuracyBySensorType.put(sensor.getType(),accuracy);
            if (BuildConfig.DEBUG) {
                showAccuracyChangeNotification();
            }
            switch (accuracy) {
                case 0:
                    Logging.warn("Compass: Unreliable ("+sensor+")");
                    break;
                case 1:
                    Logging.warn("Compass: Low Accuracy ("+sensor+")");
                    break;
                case 2:
                    Logging.info("Compass: Medium Accuracy ("+sensor+")");
                    break;
                case 3:
                    Logging.info("Compass: High Accuracy ("+sensor+")");
                    break;
            }
        }
    }

    public float getAccuracy() {
        Float accuracySum = 0.0f;
        int count = 0;
        for (int i: accuracyBySensorType.keySet()) {
            count++;
            accuracySum += accuracyBySensorType.get(i);
        }
        if (count == 0) return 0.0f;
        return accuracySum/(float) count;
    }

    private void showAccuracyChangeNotification() {
        StringBuilder b = new StringBuilder();
        boolean first = true;
        for (int i: accuracyBySensorType.keySet()) {
            if (first) {
                first = false;
            } else {
                b.append("\n");
            }
            Integer accuracy = accuracyBySensorType.get(i);
            b.append(typeEmoji.get(i)).append(" accuracy: ").append(
                    (null!=accuracy)?qualityEmoji.get(accuracy):"?");
        }
        final CharSequence text = b.toString();
        final int duration = Toast.LENGTH_SHORT;

        // Sensor callbacks run on background thread; must show toast on main thread
        new Handler(Looper.getMainLooper()).post(() -> {
            if (context != null && context instanceof Activity && !((Activity) context).isFinishing()) {
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }
        });
    }

    public Float getHeading(final Location location) {
        if (location != null) {
            GeomagneticField field = new GeomagneticField(
                    (float) location.getLatitude(),
                    (float) location.getLongitude(),
                    (float) location.getAltitude(),
                    System.currentTimeMillis()
            );
            //ALIBI: set "true heading" from the compass azimuth + the declination (degrees)
            trueHeading = compassAzimuthDegrees + field.getDeclination();
        }
        return (trueHeading + 360f) % 360f;
    }
}
