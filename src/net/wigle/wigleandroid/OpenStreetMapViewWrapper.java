package net.wigle.wigleandroid;

import java.util.Map;
import java.util.Set;

import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.views.OpenStreetMapView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.location.Location;
import android.util.AttributeSet;

/**
 * wrap the open street map view, to allow setting overlays
 */
public final class OpenStreetMapViewWrapper extends OpenStreetMapView {
  
	private final Paint trailPaint = new Paint();
	private final Paint trailBackPaint = new Paint();
  
  /**
   * XML Constructor (uses default Renderer)
   */
  public OpenStreetMapViewWrapper( final Context context, final AttributeSet attrs ) {
    super( context, attrs );
    trailPaint.setColor( Color.argb( 200, 200, 128, 200 ) );
    trailBackPaint.setColor( Color.argb( 200, 224, 224, 224 ) );
  }

	@Override
  public void onDraw( final Canvas c ) {
    super.onDraw( c );
    
	  final Set<Map.Entry<GeoPoint,Integer>> entrySet = WigleAndroid.lameStatic.trail.entrySet();
	  for ( Map.Entry<GeoPoint,Integer> entry : entrySet ) {
			final GeoPoint geoPoint = entry.getKey();
			final int nets = entry.getValue();
			if ( nets > 0 ) {
  	  	final Point point = this.getProjection().toMapPixels( geoPoint, null );
  	  	c.drawCircle(point.x, point.y, nets + 1, trailBackPaint);
			}
  	}
  	for ( Map.Entry<GeoPoint,Integer> entry : entrySet ) {
      final GeoPoint geoPoint = entry.getKey();
      final int nets = entry.getValue();
      if ( nets > 0 ) {
        final Point point = this.getProjection().toMapPixels( geoPoint, null );
        c.drawCircle(point.x, point.y, nets, trailPaint);
      }
    }
    
    // draw user crosshairs
    //final GeoPoint center = this.getMapCenter();
    Location location = WigleAndroid.lameStatic.location;
    if ( location != null ) {
      final GeoPoint user = new GeoPoint( location );
      final Point centerPoint = this.getProjection().toMapPixels( user, null );
      c.drawLine( centerPoint.x - 9, centerPoint.y - 9, centerPoint.x + 9, centerPoint.y + 9, mPaint );
      c.drawLine( centerPoint.x - 9, centerPoint.y + 9, centerPoint.x + 9, centerPoint.y - 9, mPaint );
    }
  }
}
