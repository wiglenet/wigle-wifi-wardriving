package net.wigle.wigleandroid;

public final class LatLon {
  private final float lat;
  private final float lon;
  
  public LatLon( final float lat, final float lon ) {
    this.lat = lat;
    this.lon = lon;
  }
  
  public float getLat() {
    return lat;
  }
  
  public float getLon() {
    return lon;
  }
  
  @Override
  public int hashCode() {
    return ( Float.floatToIntBits(lat) * 17 ) + Float.floatToIntBits(lon);
  }
  
  @Override
  public boolean equals( Object other ) {
    if ( other instanceof LatLon ) {
      LatLon o = (LatLon) other;
      return lat == o.lat && lon == o.lon;
    }
    return false;
  }
}
