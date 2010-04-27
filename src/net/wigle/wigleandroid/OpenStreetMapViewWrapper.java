package net.wigle.wigleandroid;

import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.views.OpenStreetMapView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.util.AttributeSet;

/**
 * wrap the open street map view, to allow setting overlays
 */
public class OpenStreetMapViewWrapper extends OpenStreetMapView {
  /**
   * XML Constructor (uses default Renderer)
   */
  public OpenStreetMapViewWrapper(Context context, AttributeSet attrs) {
    super( context, attrs );
  }
  
  @Override
  public void onDraw( Canvas c ) {
    super.onDraw( c );
    
    // draw center crosshairs
    final GeoPoint center = this.getMapCenter();
    final Point centerPoint = this.getProjection().toMapPixels(center, null);
    c.drawLine( centerPoint.x, centerPoint.y - 9, centerPoint.x, centerPoint.y + 9, mPaint );
    c.drawLine( centerPoint.x - 9, centerPoint.y, centerPoint.x + 9, centerPoint.y, mPaint );
  }
}
