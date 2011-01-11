package net.wigle.wigleandroid;

import java.util.Map;
import java.util.Set;

import net.wigle.wigleandroid.ListActivity.TrailStat;

import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.views.OpenStreetMapView;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Paint.Style;
import android.location.Location;
import android.util.AttributeSet;

/**
 * wrap the open street map view, to allow setting overlays
 */
public final class OpenStreetMapViewWrapper extends OpenStreetMapView {
  private final Paint crossBackPaint = new Paint();
  private final Paint crossPaint = new Paint();
  private final Paint trailBackPaint = new Paint();
	private final Paint trailPaint = new Paint();
	private final Paint trailDBPaint = new Paint();
  
  /**
   * XML Constructor (uses default Renderer)
   */
  public OpenStreetMapViewWrapper( final Context context, final AttributeSet attrs ) {
    super( context, attrs );
    
    crossPaint.setColor( Color.argb( 255, 0, 0, 0 ) );
    crossBackPaint.setColor( Color.argb( 128, 30, 250, 30 ) );
    crossBackPaint.setStrokeWidth( 3f );
    
    trailDBPaint.setColor( Color.argb( 128, 10, 64, 220 ) );
    trailDBPaint.setStyle( Style.FILL );
    
    trailPaint.setColor( Color.argb( 128, 200, 128, 200 ) );
    trailPaint.setStyle( Style.FILL );
    
    trailBackPaint.setColor( Color.argb( 128, 224, 224, 224 ) );
    trailBackPaint.setStyle( Style.STROKE );
    trailBackPaint.setStrokeWidth( 2f );
  }
  
  @Override
  public void onDraw( final Canvas c ) {
    super.onDraw( c );
    
    OpenStreetMapViewProjection proj = this.getProjection();
	  final Set<Map.Entry<GeoPoint,TrailStat>> entrySet = ListActivity.lameStatic.trail.entrySet();
	  // point to recycle
	  Point point = null;
	  final SharedPreferences prefs = this.getContext().getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final boolean showNewDBOnly = prefs.getBoolean( ListActivity.PREF_MAP_ONLY_NEWDB, false );
    
    // if zoomed in past 15, give a little boost to circle size
    float boost = getZoomLevel() - 15;
    boost *= 0.25f;
    boost += 1f;
    if ( boost < 1f ) {
      boost = 1f;
    }
    
	  if ( ! showNewDBOnly ) {
  	  for ( Map.Entry<GeoPoint,TrailStat> entry : entrySet ) {
  			int nets = entry.getValue().newForRun;
  			if ( nets > 0 ) {
  			  nets *= boost;
    	  	point = proj.toMapPixels( entry.getKey(), point );
    	  	c.drawCircle(point.x, point.y, nets + 1, trailBackPaint);
  			}
    	}
  
    	for ( Map.Entry<GeoPoint,TrailStat> entry : entrySet ) {
        int nets = entry.getValue().newForRun;
        if ( nets > 0 ) {
          nets *= boost;
          point = proj.toMapPixels( entry.getKey(), point );
          c.drawCircle(point.x, point.y, nets + 1, trailPaint);
        }
    	}
	  }

  	for ( Map.Entry<GeoPoint,TrailStat> entry : entrySet ) {
      int nets = entry.getValue().newForDB;
      if ( nets > 0 ) {
        nets *= boost;
        point = proj.toMapPixels( entry.getKey(), point );
        c.drawCircle(point.x, point.y, nets + 1, trailDBPaint);
      }
    }
    
    // draw user crosshairs
    Location location = ListActivity.lameStatic.location;
    if ( location != null ) {
      final GeoPoint user = new GeoPoint( location );
      point = proj.toMapPixels( user, point );
      // c.drawLine( point.x - 9, point.y - 9, point.x + 9, point.y + 9, crossBackPaint );
      // c.drawLine( point.x - 9, point.y + 9, point.x + 9, point.y - 9, crossBackPaint );
      c.drawLine( point.x - 9, point.y - 9, point.x + 9, point.y + 9, crossPaint );
      c.drawLine( point.x - 9, point.y + 9, point.x + 9, point.y - 9, crossPaint );
    }
  }
}
