package net.wigle.wigleandroid.listener;

import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

public class GPSListener implements Listener, LocationListener {
    private static final long GPS_TIMEOUT = 15000L;
    private static final long NET_LOC_TIMEOUT = 60000L;

    private MainActivity mainActivity;
    private Location location;
    private Location networkLocation;
    private GpsStatus gpsStatus;
    // set these times to avoid NPE in locationOK() seen by <DooMMasteR>
    private Long lastLocationTime = 0L;
    private Long lastNetworkLocationTime = 0L;
    private Long satCountLowTime = 0L;
    private float previousSpeed = 0f;
    private LocationListener mapLocationListener;
    private int prevStatus = 0;

    public GPSListener( MainActivity mainActivity ) {
        this.mainActivity = mainActivity;
    }

    public void setMapListener( LocationListener mapLocationListener ) {
        this.mapLocationListener = mapLocationListener;
    }

    public void setMainActivity( MainActivity mainActivity ) {
        this.mainActivity = mainActivity;
    }

    @Override
    public void onGpsStatusChanged( final int event ) {
        if ( event == GpsStatus.GPS_EVENT_STOPPED ) {
            MainActivity.info("GPS STOPPED");
            // this event lies, on one device it gets called when the
            // network provider is disabled :(  so we do nothing...
            // listActivity.setLocationUpdates();
        }
        // MainActivity.info("GPS event: " + event);
        updateLocationData(null);
    }

    public void handleScanStop() {
        MainActivity.info("GPSListener: handleScanStop");
        gpsStatus = null;
        location = null;
    }

    @Override
    public void onLocationChanged( final Location newLocation ) {
        // MainActivity.info("GPS onLocationChanged: " + newLocation);
        updateLocationData( newLocation );

        if ( mapLocationListener != null ) {
            mapLocationListener.onLocationChanged( newLocation );
        }
    }

    @Override
    public void onProviderDisabled( final String provider ) {
        MainActivity.info("provider disabled: " + provider);

        if ( mapLocationListener != null ) {
            mapLocationListener.onProviderDisabled( provider );
        }
    }

    @Override
    public void onProviderEnabled( final String provider ) {
        MainActivity.info("provider enabled: " + provider);

        if ( mapLocationListener != null ) {
            mapLocationListener.onProviderEnabled( provider );
        }
    }

    @Override
    public void onStatusChanged( final String provider, final int status, final Bundle extras ) {
        final boolean isgps = "gps".equals(provider);
        if (!isgps || status != prevStatus) {
            MainActivity.info("provider status changed: " + provider + " status: " + status);
            if (isgps) prevStatus = status;
        }

        if ( mapLocationListener != null ) {
            mapLocationListener.onStatusChanged( provider, status, extras );
        }
    }

    /** newLocation can be null */
    private void updateLocationData( final Location newLocation ) {

        /**
         * ALIBI: the location manager call's a non-starter if permission hasn't been granted.
         */
        if ( Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission( mainActivity.getApplicationContext(),
                        android.Manifest.permission.ACCESS_FINE_LOCATION )
                        != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission( mainActivity.getApplicationContext(),
                        android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        final LocationManager locationManager = (LocationManager)
                mainActivity.getSystemService(Context.LOCATION_SERVICE);

        // see if we have new data
        try {
            gpsStatus = locationManager.getGpsStatus(gpsStatus);
        } catch (NullPointerException npe) {
            MainActivity.error("NPE trying to call getGPSStatus");
            return;
        }
        final int satCount = getSatCount();

        boolean newOK = newLocation != null;
        final boolean locOK = locationOK( location, satCount );
        final long now = System.currentTimeMillis();

        if ( newOK ) {
            if ( NETWORK_PROVIDER.equals( newLocation.getProvider() ) ) {
                // save for later, in case we lose gps
                networkLocation = newLocation;
                lastNetworkLocationTime = now;
            }
            else {
                lastLocationTime = now;
                // make sure there's enough sats on this new gps location
                newOK = locationOK( newLocation, satCount );
            }
        }

        if ( mainActivity.inEmulator() && newLocation != null ) {
            newOK = true;
        }

        final boolean netLocOK = locationOK( networkLocation, satCount );

        boolean wasProviderChange = false;
        if ( ! locOK ) {
            if ( newOK ) {
                wasProviderChange = true;
                //noinspection RedundantIfStatement
                if ( location != null && ! location.getProvider().equals( newLocation.getProvider() ) ) {
                    wasProviderChange = false;
                }

                location = newLocation;
            }
            else if ( netLocOK ) {
                location = networkLocation;
                wasProviderChange = true;
            }
            else if ( location != null ) {
                // transition to null
                MainActivity.info( "nulling location: " + location );
                location = null;
                wasProviderChange = true;
                // make sure we're registered for updates
                mainActivity.setLocationUpdates();
            }
        }
        else if ( newOK && GPS_PROVIDER.equals( newLocation.getProvider() ) ) {
            if ( NETWORK_PROVIDER.equals( location.getProvider() ) ) {
                // this is an upgrade from network to gps
                wasProviderChange = true;
            }
            location = newLocation;
            if ( wasProviderChange ) {
                // save it in prefs
                saveLocation();
            }
        }
        else if ( newOK && NETWORK_PROVIDER.equals( newLocation.getProvider() ) ) {
            if ( NETWORK_PROVIDER.equals( location.getProvider() ) ) {
                // just a new network provided location over an old one
                location = newLocation;
            }
        }

        // for maps. so lame!
        ListFragment.lameStatic.location = location;
        boolean scanScheduled = false;
        if ( location != null ) {
            final float currentSpeed = location.getSpeed();
            if ( (previousSpeed == 0f && currentSpeed > 0f)
                    || (previousSpeed < 5f && currentSpeed >= 5f)) {
                // moving faster now than before, schedule a scan because the timing config pry changed
                MainActivity.info("Going faster, scheduling scan");
                mainActivity.scheduleScan();
                scanScheduled = true;
            }
            previousSpeed = currentSpeed;
        }
        else {
            previousSpeed = 0f;
        }

        // MainActivity.info("sat count: " + satCount);

        if ( wasProviderChange ) {
            MainActivity.info( "wasProviderChange: satCount: " + satCount
                    + " newOK: " + newOK + " locOK: " + locOK + " netLocOK: " + netLocOK
                    + (newOK ? " newProvider: " + newLocation.getProvider() : "")
                    + (locOK ? " locProvider: " + location.getProvider() : "")
                    + " newLocation: " + newLocation );

            final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
            final boolean disableToast = prefs.getBoolean( ListFragment.PREF_DISABLE_TOAST, false );
            if (!disableToast) {
                final String announce = location == null ? mainActivity.getString(R.string.lost_location)
                        : mainActivity.getString(R.string.have_location) + " \"" + location.getProvider() + "\"";
                Toast.makeText( mainActivity, announce, Toast.LENGTH_SHORT ).show();
            }

            final boolean speechGPS = prefs.getBoolean( ListFragment.PREF_SPEECH_GPS, true );
            if ( speechGPS ) {
                // no quotes or the voice pauses
                final String speakAnnounce = location == null ? "Lost Location"
                        : "Now have location from " + location.getProvider() + ".";
                mainActivity.speak( speakAnnounce );
            }

            if ( ! scanScheduled ) {
                // get the ball rolling
                MainActivity.info("Location provider change, scheduling scan");
                mainActivity.scheduleScan();
            }
        }

        // update the UI
        mainActivity.setLocationUI();

    }

    public void checkLocationOK() {
        if ( ! locationOK( location, getSatCount() ) ) {
            // do a self-check
            updateLocationData(null);
        }
    }

    private boolean locationOK( final Location location, final int satCount ) {
        boolean retval = false;
        final long now = System.currentTimeMillis();

        //noinspection StatementWithEmptyBody
        if ( location == null ) {
            // bad!
        }
        else if ( GPS_PROVIDER.equals( location.getProvider() ) ) {
            if ( satCount > 0 && satCount < 3 ) {
                if ( satCountLowTime == null ) {
                    satCountLowTime = now;
                }
            }
            else {
                // plenty of sats
                satCountLowTime = null;
            }
            boolean gpsLost = satCountLowTime != null && (now - satCountLowTime) > GPS_TIMEOUT;
            gpsLost |= now - lastLocationTime > GPS_TIMEOUT;
            gpsLost |= horribleGps(location);
            retval = ! gpsLost;
        }
        else if ( NETWORK_PROVIDER.equals( location.getProvider() ) ) {
            boolean gpsLost = now - lastNetworkLocationTime > NET_LOC_TIMEOUT;
            gpsLost |= horribleGps(location);
            retval = ! gpsLost;
        }

        return retval;
    }

    private boolean horribleGps(final Location location) {
        // try to protect against some horrible gps's out there
        // check if accuracy is under 10 miles
        boolean horrible = location.hasAccuracy() && location.getAccuracy() > 16000;
        horrible |= location.getLatitude() < -90 || location.getLatitude() > 90;
        horrible |= location.getLongitude() < -180 || location.getLongitude() > 180;
        return horrible;
    }

    public int getSatCount() {
        int satCount = 0;
        if ( gpsStatus != null ) {
            for ( GpsSatellite sat : gpsStatus.getSatellites() ) {
                if ( sat.usedInFix() ) {
                    satCount++;
                }
            }
        }
        return satCount;
    }

    public void saveLocation() {
        // save our location for use on later runs
        if ( this.location != null ) {
            final SharedPreferences prefs = mainActivity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
            final Editor edit = prefs.edit();
            // there is no putDouble
            edit.putFloat( ListFragment.PREF_PREV_LAT, (float) location.getLatitude() );
            edit.putFloat( ListFragment.PREF_PREV_LON, (float) location.getLongitude() );
            edit.apply();
        }
    }

    public Location getLocation() {
        return location;
    }

}
