package net.wigle.wigleandroid;

import java.util.Map;
import java.util.Set;

import net.wigle.wigleandroid.WigleAndroid.TrailStat;

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
  private final Context context;
  
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
    this.context = context;
    
    crossPaint.setColor( Color.argb( 255, 0, 0, 0 ) );
    crossBackPaint.setColor( Color.argb( 128, 30, 250, 30 ) );
    crossBackPaint.setStrokeWidth( 3f );
    
    trailDBPaint.setColor( Color.argb( 128, 10, 64, 220 ) );
    trailDBPaint.setStyle( Style.FILL );
    
    trailPaint.setColor( Color.argb( 128, 200, 128, 200 ) );
    trailPaint.setStyle( Style.FILL );
    
    trailBackPaint.setColor( Color.argb( 128, 244, 150, 150 ) );
    trailBackPaint.setStyle( Style.STROKE );
    trailBackPaint.setStrokeWidth( 2f );
  }
  
  @Override
  public void onDraw( final Canvas c ) {
    super.onDraw( c );
    
    OpenStreetMapViewProjection proj = this.getProjection();
	  final Set<Map.Entry<GeoPoint,TrailStat>> entrySet = WigleAndroid.lameStatic.trail.entrySet();
	  // point to recycle
	  Point point = null;
	  final SharedPreferences prefs = context.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0 );
    final boolean showNewDBOnly = prefs.getBoolean( WigleAndroid.PREF_MAP_ONLY_NEWDB, false );
    
	  if ( ! showNewDBOnly ) {
  	  for ( Map.Entry<GeoPoint,TrailStat> entry : entrySet ) {
  			final int nets = entry.getValue().newForRun;
  			if ( nets > 0 ) {
    	  	point = proj.toMapPixels( entry.getKey(), point );
    	  	c.drawCircle(point.x, point.y, nets + 1, trailBackPaint);
  			}
    	}
  
    	for ( Map.Entry<GeoPoint,TrailStat> entry : entrySet ) {
        final int nets = entry.getValue().newForRun;
        if ( nets > 0 ) {
          point = proj.toMapPixels( entry.getKey(), point );
          c.drawCircle(point.x, point.y, nets + 1, trailPaint);
        }
    	}
	  }

  	for ( Map.Entry<GeoPoint,TrailStat> entry : entrySet ) {
      final int nets = entry.getValue().newForDB;
      if ( nets > 0 ) {
        point = proj.toMapPixels( entry.getKey(), point );
        c.drawCircle(point.x, point.y, nets + 1, trailDBPaint);
      }
    }
    
    // draw user crosshairs
    Location location = WigleAndroid.lameStatic.location;
    if ( location != null ) {
      final GeoPoint user = new GeoPoint( location );
      point = proj.toMapPixels( user, point );
      c.drawLine( point.x - 9, point.y - 9, point.x + 9, point.y + 9, crossBackPaint );
      c.drawLine( point.x - 9, point.y + 9, point.x + 9, point.y - 9, crossBackPaint );
      c.drawLine( point.x - 9, point.y - 9, point.x + 9, point.y + 9, crossPaint );
      c.drawLine( point.x - 9, point.y + 9, point.x + 9, point.y - 9, crossPaint );
    }
  }
}
