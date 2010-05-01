package net.wigle.wigleandroid;

import java.util.Map;
import java.util.LinkedHashMap;

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
  
	private LinkedHashMap<GeoPoint,Integer> trail = 
	  new LinkedHashMap<GeoPoint,Integer>() {
			public boolean removeEldestEntry( Entry<GeoPoint,Integer> entry ) {
				return size() > 1024;
			}
		};

  private Paint trailPaint = new Paint();
  
  /**
   * XML Constructor (uses default Renderer)
   */
  public OpenStreetMapViewWrapper(Context context, AttributeSet attrs) {
    super( context, attrs );
    int color = Color.argb( 200, 200, 128, 200 );
    trailPaint.setColor( color );
  }

	public void latestLocation( GeoPoint loc, int newForRun ) {
		synchronized( trail ) {
    	if ( ! trail.containsKey( loc ) ) {
    	  trail.put( loc, newForRun );
    	}
    }
	}
  
  @Override
  public void onDraw( Canvas c ) {
    super.onDraw( c );
    
		synchronized( trail ) {
    	for ( Map.Entry<GeoPoint,Integer> entry : trail.entrySet() ) {
				GeoPoint geoPoint = entry.getKey();
				int nets = entry.getValue();
				if ( nets > 0 ) {
    	  	final Point point = this.getProjection().toMapPixels( geoPoint, null );
    	  	c.drawCircle(point.x, point.y, 
					  (float) Math.sqrt(3 * nets), trailPaint);
				}
    	}
		}
    
    // draw center crosshairs
    final GeoPoint center = this.getMapCenter();
    final Point centerPoint = this.getProjection().toMapPixels( center, null );
    c.drawLine( centerPoint.x, centerPoint.y - 9, centerPoint.x, centerPoint.y + 9, mPaint );
    c.drawLine( centerPoint.x - 9, centerPoint.y, centerPoint.x + 9, centerPoint.y, mPaint );
  }
}
