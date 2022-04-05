package net.wigle.wigleandroid.util;

/**
 * A really clean Lat/Lon Kalman filter implementation as provided by
 * <a href="https://stackoverflow.com/users/2110762/stochastically">Stochastically</a>'s answer to
 * <a href="https://stackoverflow.com/questions/1134579/smooth-gps-data">this question</a> about smoothing GPS</a>
 */
public class KalmanLatLong {
    private final float minAccuracy = 1;

    private float qMetersPerSecond;
    private long TimeStamp_milliseconds;
    private double lat;
    private double lng;
    private float variance; // P matrix.  Negative means object uninitialised.  NB: units irrelevant, as long as same units used throughout

    public KalmanLatLong(float qMetersPerSec) { this.qMetersPerSecond = qMetersPerSec; variance = -1; }

    public long getTimeStamp() { return TimeStamp_milliseconds; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public float getAccuracy() { return (float)Math.sqrt(variance); }

    public void setState(double lat, double lng, float accuracy, long TimeStamp_milliseconds) {
        this.lat=lat; this.lng=lng; variance = accuracy * accuracy; this.TimeStamp_milliseconds=TimeStamp_milliseconds;
    }

    /// <summary>
    /// Kalman filter processing for lattitude and longitude
    /// </summary>
    /// <param name="lat_measurement_degrees">new measurement of lattidude</param>
    /// <param name="lng_measurement">new measurement of longitude</param>
    /// <param name="accuracy">measurement of 1 standard deviation error in metres</param>
    /// <param name="TimeStamp_milliseconds">time of measurement</param>
    /// <returns>new state</returns>
    public void process(double latMeasurement, double lngMeasurement, float accuracy, long timeStampMillis) {
        if (accuracy < minAccuracy) accuracy = minAccuracy;
        if (variance < 0) {
            // if variance < 0, object is unitialised, so initialise with current values
            this.TimeStamp_milliseconds = timeStampMillis;
            lat=latMeasurement; lng = lngMeasurement; variance = accuracy*accuracy;
        } else {
            // else apply Kalman filter methodology

            long TimeInc_milliseconds = timeStampMillis - this.TimeStamp_milliseconds;
            if (TimeInc_milliseconds > 0) {
                // time has moved on, so the uncertainty in the current position increases
                variance += TimeInc_milliseconds * qMetersPerSecond * qMetersPerSecond / 1000;
                this.TimeStamp_milliseconds = timeStampMillis;
                // TO DO: USE VELOCITY INFORMATION HERE TO GET A BETTER ESTIMATE OF CURRENT POSITION
            }

            // Kalman gain matrix K = Covarariance * Inverse(Covariance + MeasurementVariance)
            // NB: because K is dimensionless, it doesn't matter that variance has different units to lat and lng
            float K = variance / (variance + accuracy * accuracy);
            // apply K
            lat += K * (latMeasurement - lat);
            lng += K * (lngMeasurement - lng);
            // new Covarariance  matrix is (IdentityMatrix - K) * Covarariance
            variance = (1 - K) * variance;
        }
    }

    public void reset() {
        variance = -1;
    }
}