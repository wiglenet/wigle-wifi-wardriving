package net.wigle.wigleandroid;

import java.util.LinkedList;

import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.views.OpenStreetMapView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;

/**
 * wrap the open street map view, to allow setting overlays
 */
public class OpenStreetMapViewWrapper extends OpenStreetMapView {
  private LinkedList<GeoPoint> trail = new LinkedList<GeoPoint>();
  private Paint trailPaint = new Paint();
  
  /**
   * XML Constructor (uses default Renderer)
   */
  public OpenStreetMapViewWrapper(Context context, AttributeSet attrs) {
    super( context, attrs );
    int color = Color.argb(200, 200, 128, 200);
    trailPaint.setColor( color );
  }

	public void latestLocation( GeoPoint loc ) {
    if ( trail.isEmpty() || ! trail.getLast().equals( loc ) ) {
      trail.add( loc );
    }

    // keep it from getting out of hand
    while ( trail.size() > 1024 ) {
      trail.removeFirst();
    }
	}
  
  @Override
  public void onDraw( Canvas c ) {
    super.onDraw( c );
    
    for ( GeoPoint geoPoint : trail ) {
      final Point point = this.getProjection().toMapPixels( geoPoint, null );
      c.drawCircle(point.x, point.y, 2, trailPaint);
    }
    
    // draw center crosshairs
    final GeoPoint center = this.getMapCenter();
    final Point centerPoint = this.getProjection().toMapPixels( center, null );
    c.drawLine( centerPoint.x, centerPoint.y - 9, centerPoint.x, centerPoint.y + 9, mPaint );
    c.drawLine( centerPoint.x - 9, centerPoint.y, centerPoint.x + 9, centerPoint.y, mPaint );
  }
}
