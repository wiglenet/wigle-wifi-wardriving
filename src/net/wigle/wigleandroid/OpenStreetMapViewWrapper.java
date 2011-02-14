package net.wigle.wigleandroid;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.wigle.wigleandroid.ListActivity.TrailStat;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

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
public final class OpenStreetMapViewWrapper extends MapView {
  private final Paint crossBackPaint = new Paint();
  private final Paint crossPaint = new Paint();
  
  private final Paint trailBackPaint = new Paint();
	private final Paint trailPaint = new Paint();
	private final Paint trailDBPaint = new Paint();
	
	private final Paint trailCellBackPaint = new Paint();
  private final Paint trailCellPaint = new Paint();
  private final Paint trailCellDBPaint = new Paint();
  
  
  /**
   * XML Constructor (uses default Renderer)
   */
  public OpenStreetMapViewWrapper( final Context context, final AttributeSet attrs ) {
    super( context, attrs );
    
    crossPaint.setColor( Color.argb( 255, 0, 0, 0 ) );
    crossPaint.setAntiAlias( true );
    crossBackPaint.setColor( Color.argb( 128, 30, 250, 30 ) );
    crossBackPaint.setAntiAlias( true );
    crossBackPaint.setStrokeWidth( 3f );
    
    trailDBPaint.setColor( Color.argb( 128, 10, 64, 220 ) );
    trailDBPaint.setAntiAlias( true );
    trailDBPaint.setStyle( Style.FILL );
    
    trailPaint.setColor( Color.argb( 128, 200, 128, 200 ) );
    trailPaint.setAntiAlias( true );
    trailPaint.setStyle( Style.FILL );
    
    trailBackPaint.setColor( Color.argb( 128, 240, 240, 240 ) );
    trailBackPaint.setAntiAlias( true );
    trailBackPaint.setStyle( Style.STROKE );
    trailBackPaint.setStrokeWidth( 2f );
    
    // these are squares, no need to turn on anti-aliasing
    trailCellDBPaint.setColor( Color.argb( 128, 64, 10, 220 ) );
    trailCellDBPaint.setStyle( Style.FILL );
    
    trailCellPaint.setColor( Color.argb( 128, 128, 200, 200 ) );
    trailCellPaint.setStyle( Style.FILL );
    
    trailCellBackPaint.setColor( Color.argb( 128, 240, 240, 240 ) );
    trailCellBackPaint.setStyle( Style.STROKE );
    trailCellBackPaint.setStrokeWidth( 2f );
  }
  
  @Override
  public void onDraw( final Canvas c ) {
    super.onDraw( c );
    
    Projection proj = this.getProjection();
	  final Set<Map.Entry<GeoPoint,TrailStat>> entrySet = ListActivity.lameStatic.trail.entrySet();
	  // point to recycle
	  Point point = null;
	  final SharedPreferences prefs = this.getContext().getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final boolean showNewDBOnly = prefs.getBoolean( ListActivity.PREF_MAP_ONLY_NEWDB, false );
    
    // if zoomed in past 15, give a little boost to circle size
    float boost = getZoomLevel() - 15;
    boost *= 0.50f;
    boost += 2f;
    if ( boost < 2f ) {
      boost = 2f;
    }
    
    // backgrounds
    for ( Map.Entry<GeoPoint,TrailStat> entry : entrySet ) {
      if ( ! showNewDBOnly ) {  	  
  			int nets = entry.getValue().newWifiForRun;
  			if ( nets > 0 ) {
  			  nets *= boost;
    	  	point = proj.toMapPixels( entry.getKey(), point );
    	  	c.drawCircle(point.x, point.y, nets + 1, trailBackPaint);
  			}
  			
  			nets = entry.getValue().newCellForRun;        
        if ( nets > 0 ) {
          // ListActivity.info("new cell for run: " + nets);
          nets *= boost * 8;
          point = proj.toMapPixels( entry.getKey(), point );
          int sub = nets/2 + 1;
          int add = nets/2 + (nets % 2);
          c.drawRect(point.x - sub, point.y - sub, point.x + add, point.y + add, trailCellBackPaint);
        }
    	}
	  }
	  
    // foregrounds
    for ( Map.Entry<GeoPoint,TrailStat> entry : entrySet ) {
      if ( ! showNewDBOnly ) {    	
        int nets = entry.getValue().newWifiForRun;
        if ( nets > 0 ) {
          nets *= boost;
          point = proj.toMapPixels( entry.getKey(), point );
          c.drawCircle(point.x, point.y, nets + 1, trailPaint);
        }
    	
        nets = entry.getValue().newCellForRun;
        if ( nets > 0 ) {
          nets *= boost * 8;
          point = proj.toMapPixels( entry.getKey(), point );
          int sub = nets/2 + 1;
          int add = nets/2 + (nets % 2);
          c.drawRect(point.x - sub, point.y - sub, point.x + add, point.y + add, trailCellPaint);
        }
      }
      
      int nets = entry.getValue().newWifiForDB;
      if ( nets > 0 ) {
        nets *= boost;
        point = proj.toMapPixels( entry.getKey(), point );
        c.drawCircle(point.x, point.y, nets + 1, trailDBPaint);
      }
      
      nets = entry.getValue().newCellForDB;
      if ( nets > 0 ) {
        nets *= boost * 8;
        point = proj.toMapPixels( entry.getKey(), point );
        int sub = nets/2 + 1;
        int add = nets/2 + (nets % 2);
        c.drawRect(point.x - sub, point.y - sub, point.x + add, point.y + add, trailCellDBPaint);
      }
	  }
    
    if ( getZoomLevel() >= 16 ) {
      // draw ssid strings
      final Collection<Network> networks = ListActivity.getNetworkCache().values();
      if ( ! networks.isEmpty() ) {
        final Map<GeoPoint,Integer> geoPoints = new HashMap<GeoPoint,Integer>();
        for( Network network : ListActivity.getNetworkCache().values() ) {
          final GeoPoint geoPoint = network.getGeoPoint();
          if ( geoPoint != null ) {
            point = proj.toMapPixels( geoPoint, point );
            int y = point.y;
            Integer nets = geoPoints.get(geoPoint);
            if ( nets == null ) {
              nets = 0;
            }
            else {
              nets++;
            }
            geoPoints.put( geoPoint, nets );
            // ListActivity.info("geopoint: " + geoPoint + " " + nets + " got: " + geoPoints.get(geoPoint));
            // adjust so they don't overlap too bad
            y += nets * 12;
            c.drawText( network.getSsid(), point.x, y, crossPaint );
          }
        }
        // ListActivity.info("geopoints: " + geoPoints);

      }
    }
  	 
    // draw user crosshairs
    Location location = ListActivity.lameStatic.location;
    if ( location != null ) {
      final GeoPoint user = new GeoPoint( location );
      point = proj.toMapPixels( user, point );
      final int len = 20;
      c.drawLine( point.x - len, point.y - len, point.x + len, point.y + len, crossBackPaint );
      c.drawLine( point.x - len, point.y + len, point.x + len, point.y - len, crossBackPaint );
      c.drawLine( point.x - len, point.y - len, point.x + len, point.y + len, crossPaint );
      c.drawLine( point.x - len, point.y + len, point.x + len, point.y - len, crossPaint );
    }
  }
}
