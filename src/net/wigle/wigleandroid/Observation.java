package net.wigle.wigleandroid;

import android.location.Location;

public class Observation {
  private final int level;
  private final double lat;
  private final double lon;
  private final double alt;
  private final float accuracy;
  private final long time;
  
  public Observation( int level, Location location ) {
    this.level = level;
    this.lat = location.getLatitude();
    this.lon = location.getLongitude();
    this.alt = location.getAltitude();
    this.accuracy = location.getAccuracy();
    this.time = location.getTime();
  }

  public int getLevel() {
    return level;
  }

  public double getLat() {
    return lat;
  }

  public double getLon() {
    return lon;
  }

  public double getAlt() {
    return alt;
  }

  public float getAccuracy() {
    return accuracy;
  }
  
  public long getTime() {
    return time;
  }
  
  @Override
  public boolean equals( Object other ) {
    if ( other instanceof Observation ) {
      Observation o = (Observation) other;
      return level == o.level
        && lat == o.lat
        && lon == o.lon;
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    int retval = 17;
    retval += 37 * level;
    retval += 37 * Double.doubleToLongBits( lat );
    retval += 37 * Double.doubleToLongBits( lon );
    return retval;
  }
  
}
