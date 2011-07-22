package net.wigle.wigleandroid;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.wigle.wigleandroid.ListActivity.TrailStat;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.Overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.location.Location;

/**
 * wrap the open street map view, to allow setting overlays
 */
public final class OpenStreetMapViewWrapper extends Overlay {
  private final Paint crossBackPaint = new Paint();
  private final Paint crossPaint = new Paint();
  
  private final Paint trailBackPaint = new Paint();
	private final Paint trailPaint = new Paint();
	private final Paint trailDBPaint = new Paint();
	
	private final Paint trailCellBackPaint = new Paint();
  private final Paint trailCellPaint = new Paint();
  private final Paint trailCellDBPaint = new Paint();
  
  private Paint trailBackSizePaint;
  private Paint trailSizePaint;
  private Paint trailDBSizePaint;
  
  private Paint trailCellBackSizePaint;
  private Paint trailCellSizePaint;
  private Paint trailCellDBSizePaint;
  
  private final Paint ssidPaintLeft = new Paint();
  private final Paint ssidPaintRight = new Paint();
  private final Paint ssidPaintLeftBack = new Paint();
  private final Paint ssidPaintRightBack = new Paint(); 
  
  private final Paint netTextBack = new Paint();
  private final Paint netText = new Paint();
  
  private final Paint netCountBack = new Paint();
  private final Paint netCount = new Paint();  
  
  private final ConcurrentLinkedHashMap<GeoPoint,Boolean> labelChoice = 
    new ConcurrentLinkedHashMap<GeoPoint,Boolean>(128);
  
  private Network singleNetwork = null;
  private ConcurrentLinkedHashMap<LatLon, Integer> obsMap;
  
  /**
   * code constructor
   */
  public OpenStreetMapViewWrapper( final Context context ) {
    super( context );
    setup();
  }
  
  public void setSingleNetwork( final Network singleNetwork ) {
    this.singleNetwork = singleNetwork;
  }
  
  public void setObsMap( final ConcurrentLinkedHashMap<LatLon, Integer> obsMap ) {
    this.obsMap = obsMap;
  }
  
  private void setup() {    
    crossPaint.setColor( Color.argb( 255, 0, 0, 0 ) );
    crossPaint.setAntiAlias( true );
    crossBackPaint.setColor( Color.argb( 128, 30, 250, 30 ) );
    crossBackPaint.setAntiAlias( true );
    crossBackPaint.setStrokeWidth( 3f );
    
    trailDBPaint.setColor( Color.argb( 200, 10, 64, 220 ) );
    trailDBPaint.setAntiAlias( true );
    trailDBPaint.setStyle( Style.STROKE );
    trailDBPaint.setStrokeWidth( 2f );
    
    trailPaint.setColor( Color.argb( 200, 255, 32, 32 ) );
    trailPaint.setAntiAlias( true );
    trailPaint.setStyle( Style.STROKE );
    trailPaint.setStrokeWidth( 2f );
    
    trailBackPaint.setColor( Color.argb( 128, 240, 240, 240 ) );
    trailBackPaint.setAntiAlias( true );
    trailBackPaint.setStyle( Style.FILL );
    trailBackPaint.setStrokeWidth( 3f );
    
    // these are squares, no need to turn on anti-aliasing
    trailCellDBPaint.setColor( Color.argb( 200, 64, 10, 220 ) );
    trailCellDBPaint.setStyle( Style.STROKE );
    trailCellDBPaint.setStrokeWidth( 2f );
    
    trailCellPaint.setColor( Color.argb( 200, 128, 200, 200 ) );
    trailCellPaint.setStyle( Style.STROKE );
    trailCellPaint.setStrokeWidth( 2f );
    
    trailCellBackPaint.setColor( Color.argb( 128, 240, 240, 240 ) );
    trailCellBackPaint.setStyle( Style.FILL );
    trailCellBackPaint.setStrokeWidth( 3f );
        
    trailBackSizePaint = new Paint(trailBackPaint);
    trailBackSizePaint.setStyle( Style.STROKE );
    trailBackSizePaint.setStrokeWidth( 2f );
    trailSizePaint = new Paint(trailPaint);
    trailSizePaint.setColor( Color.argb( 128, 200, 128, 200 ) );
    trailSizePaint.setStyle( Style.FILL );
    trailDBSizePaint = new Paint(trailDBPaint);
    trailDBSizePaint.setColor( Color.argb( 128, 10, 64, 220 ) );
    trailDBSizePaint.setStyle( Style.FILL );
    
    trailCellBackSizePaint = new Paint(trailCellBackPaint);
    trailCellBackSizePaint.setStyle( Style.STROKE );
    trailCellBackSizePaint.setStrokeWidth( 2f );    
    trailCellSizePaint = new Paint(trailCellPaint);
    trailCellSizePaint.setColor( Color.argb( 128, 128, 200, 200 ) );
    trailCellSizePaint.setStyle( Style.FILL );
    trailCellDBSizePaint = new Paint(trailCellDBPaint);
    trailCellDBSizePaint.setColor( Color.argb( 128, 64, 10, 220 ) );
    trailCellDBSizePaint.setStyle( Style.FILL );
    
    ssidPaintLeft.setColor( Color.argb( 255, 0, 0, 0 ) );
    ssidPaintLeft.setAntiAlias( true );
    ssidPaintLeft.setTextAlign( Align.LEFT );
    
    ssidPaintRight.setColor( Color.argb( 255, 0, 0, 0 ) );
    ssidPaintRight.setAntiAlias( true );
    ssidPaintRight.setTextAlign( Align.RIGHT );
    
    ssidPaintLeftBack.setColor( Color.argb( 255, 240, 230, 230 ) );
    ssidPaintLeftBack.setStyle( Style.STROKE );
    ssidPaintLeftBack.setStrokeWidth( 3f );
    ssidPaintLeftBack.setAntiAlias( true );
    ssidPaintLeftBack.setTextAlign( Align.LEFT );
    
    ssidPaintRightBack.setColor( Color.argb( 255, 240, 230, 230 ) );
    ssidPaintRightBack.setStyle( Style.STROKE );
    ssidPaintRightBack.setStrokeWidth( 3f );
    ssidPaintRightBack.setAntiAlias( true );
    ssidPaintRightBack.setTextAlign( Align.RIGHT );    
    
    netTextBack.setColor( Color.argb( 255, 250, 250, 250 ) );
    netTextBack.setStyle( Style.STROKE );
    netTextBack.setTextSize(20f);
    netTextBack.setAntiAlias( true );
    netTextBack.setStrokeWidth( 4f );
    
    netText.setColor( Color.argb( 255, 0, 0, 0 ) );
    netText.setStyle( Style.STROKE );
    netText.setTextSize(20f);
    netText.setAntiAlias( true );
    
    netCountBack.setColor( Color.argb( 255, 245, 245, 245 ) );
    netCountBack.setStyle( Style.STROKE );
    netCountBack.setTextSize(16f);
    netCountBack.setAntiAlias( true );
    netCountBack.setStrokeWidth( 3f );
    netCountBack.setTextAlign( Align.CENTER );    
    
    netCount.setColor( Color.argb( 255, 32, 32, 32 ) );
    netCount.setStyle( Style.STROKE );
    netCount.setTextSize(16f);
    netCount.setAntiAlias( true );
    netCount.setTextAlign( Align.CENTER );   
    
    
  }
  
  @Override
  public void draw( final Canvas c, final MapView osmv, final boolean shadow ) {
    if ( shadow ) {
      return;
    }
    
    if ( singleNetwork != null ) {
      drawNetwork( c, osmv, singleNetwork );
    }
    else {
      drawTrail( c, osmv );
    }
  }
  
  private void drawNetwork( final Canvas c, final MapView osmv, final Network network ) {
    final Projection proj = osmv.getProjection();
        
    if ( obsMap != null ) {
      final GeoPoint obsPoint = new GeoPoint(0,0);
      Point point = new Point();
      Paint paint = new Paint();
      paint.setColor( Color.argb( 255, 0, 0, 0 ) );
      
      for ( Map.Entry<LatLon, Integer> obs : obsMap.entrySet() ) {
        final LatLon latLon = obs.getKey();
        final int level = obs.getValue();
        obsPoint.setLatitudeE6( (int) (latLon.getLat() * 1E6) );
        obsPoint.setLongitudeE6( (int) (latLon.getLon() * 1E6) );
        point = proj.toMapPixels( obsPoint, point );
        paint.setColor( NetworkListAdapter.getSignalColor( level, true ) );
        c.drawCircle( point.x, point.y, 4, paint );
      }
    }
    
    final GeoPoint geoPoint = network.getGeoPoint();    
    if ( geoPoint != null ) {
      Point point = proj.toMapPixels( geoPoint, null );
      c.drawCircle(point.x, point.y, 16, trailBackPaint);
      c.drawCircle(point.x, point.y, 16, trailPaint);
      
      c.drawText( network.getSsid(), point.x, point.y, netTextBack );                
      c.drawText( network.getSsid(), point.x, point.y, netText );
    }
  }
   
  private void drawTrail( final Canvas c, final MapView osmv ) {    
	  final Set<Map.Entry<GeoPoint,TrailStat>> entrySet = ListActivity.lameStatic.trail.entrySet();
	  
	  // if zoomed in past 15, give a little boost to circle size
    float boost = osmv.getZoomLevel() - 15;
    boost *= 0.50f;
    boost += 2f;
    if ( boost < 2f ) {
      boost = 2f;
    }
        
    renderCircleNumbers( c, osmv, entrySet, boost );        
    renderSsidStrings( c, osmv, boost );
  }
  
  private void renderSsidStrings( final Canvas c, final MapView osmv, final float boost ) {
    final SharedPreferences prefs = osmv.getContext().getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final boolean showLabel = prefs.getBoolean( ListActivity.PREF_MAP_LABEL, true );
    final Projection proj = osmv.getProjection();

    // point to recycle
    Point point = null;
    
    if ( osmv.getZoomLevel() >= 16 && showLabel ) {
      // draw ssid strings
      final Collection<Network> networks = ListActivity.getNetworkCache().values();
      if ( ! networks.isEmpty() ) { 
        Boolean prevChoice = new Boolean(true);
        Map<GeoPoint,Integer> netsMap = new HashMap<GeoPoint,Integer>();
        
        final boolean filter = prefs.getBoolean( ListActivity.PREF_MAPF_ENABLED, true );
        final Matcher matcher = getFilterMatcher( prefs, "" );
        
        for( Network network : ListActivity.getNetworkCache().values() ) {
          
          if ( filter && ! isOk( matcher, prefs, "", network ) ) {
            continue;
          }
          
          final GeoPoint geoPoint = network.getGeoPoint();
          if ( geoPoint != null ) {
            final GeoPoint geoCopy = new GeoPoint( geoPoint );
            // round off a bit
            final int shiftBits = 6;
            geoCopy.setLatitudeE6( geoCopy.getLatitudeE6() >> shiftBits << shiftBits );
            geoCopy.setLongitudeE6( geoCopy.getLongitudeE6() >> shiftBits << shiftBits );
            geoCopy.setAltitude(0);
            // ListActivity.info( "geoPoint: " + geoPoint + " geoCopy: " + geoCopy );
            
            Integer nets = netsMap.get( geoCopy );
            if ( nets == null ) {
              nets = 0;
            }
            else {
              nets++;              
            } 
            netsMap.put( geoCopy, nets );
            
            // use geoCopy
            point = proj.toMapPixels( geoCopy, point );
            int x = point.x;
            int y = point.y; 
            
            Boolean choice = labelChoice.get( geoCopy ); 
            if ( nets == 0 ) {
              // new box for this frame, see if we need to adjust                  
              if ( choice == null ) {
                choice = !prevChoice;
                labelChoice.put( geoCopy, choice );
              }              
              prevChoice = choice;
            }
            
            // some race causes nets to be not zero sometimes
            if ( choice == null ) {
              choice = !prevChoice;
            }
            
            int horizontalOffset = 20 + (int) (boost * 2);
            int verticalDirection = 1;
            int verticalOffset = 0;
            Paint paint = ssidPaintLeft;
            Paint paintBack = ssidPaintLeftBack;
            if ( choice ) {
              horizontalOffset *= -1;
              verticalDirection = -1;    
              verticalOffset = -12;
              paint = ssidPaintRight;
              paintBack = ssidPaintRightBack;
            }
            
            // adjust so they don't overlap too bad
            y += nets * 12 * verticalDirection + verticalOffset;
            x += horizontalOffset;
            
            // ListActivity.info("x: " + x + " y: " + y + " point: " + point);
            c.drawText( network.getSsid(), x, y, paintBack );     
            c.drawText( network.getSsid(), x, y, paint );            
          }
        }
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
  
  private void renderCircleNumbers( final Canvas c, final MapView osmv, 
      final Set<Map.Entry<GeoPoint,TrailStat>> entrySet, float boost) {
    
    final SharedPreferences prefs = osmv.getContext().getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final boolean showNewDBOnly = prefs.getBoolean( ListActivity.PREF_MAP_ONLY_NEWDB, false );
    final boolean circleSizeMap = prefs.getBoolean(ListActivity.PREF_CIRCLE_SIZE_MAP, false);
    final Projection proj = osmv.getProjection();
        
    float wifiSize = (5 * boost) + 1;
    float cellSize = (4 * boost) + 1;
    if ( osmv.getZoomLevel() < 15 ) {
      wifiSize /= 2;
      cellSize /= 2;
    }
    
    if ( circleSizeMap && osmv.getZoomLevel() < 15 ) {
      boost /= 2;
    }
    
    // point to recycle
    Point point = null;
    
    // backgrounds
    for ( Map.Entry<GeoPoint,TrailStat> entry : entrySet ) {
      final TrailStat value = entry.getValue();
      boolean projected = false;
      
      if ( (value.newWifiForRun > 0 && ! showNewDBOnly) || value.newWifiForDB > 0 ) {
        point = proj.toMapPixels( entry.getKey(), point );
        projected = true;
        float size = wifiSize;
        Paint paint = trailBackPaint;
        if ( circleSizeMap) {
          size = (Math.max(value.newWifiForDB, value.newWifiForRun) * boost) + 1;
          paint = trailBackSizePaint;
        }        
        c.drawCircle(point.x, point.y, size, paint);
      }
          
      if ( (value.newCellForRun > 0 && ! showNewDBOnly) || value.newCellForDB > 0 ) {
        if ( ! projected ) {
          point = proj.toMapPixels( entry.getKey(), point );
        }
        float size = cellSize;
        Paint paint = trailCellBackPaint;
        if ( circleSizeMap ) {
          size = (Math.max(value.newCellForDB, value.newCellForRun) * boost) + 1;
          paint = trailCellBackSizePaint;
        }
        c.drawRect(point.x - size, point.y - size, point.x + size, point.y + size, paint);
      }
    }
    
    // foregrounds
    for ( Map.Entry<GeoPoint,TrailStat> entry : entrySet ) {
      final TrailStat value = entry.getValue();
      boolean projected = false;

      if ( (value.newWifiForRun > 0 && ! showNewDBOnly) || value.newWifiForDB > 0 ) {
        point = proj.toMapPixels( entry.getKey(), point );
        projected = true;
        float size = wifiSize;
        Paint paint = value.newWifiForDB > 0 ? trailDBPaint : trailPaint;
        if ( circleSizeMap ) {
          size = (Math.max(value.newWifiForDB, value.newWifiForRun) * boost) + 1;
          paint = value.newWifiForDB > 0 ? trailDBSizePaint : trailSizePaint;
        }
        c.drawCircle(point.x, point.y, size, paint);
      }
    
      if ( (value.newCellForRun > 0 && ! showNewDBOnly) || value.newCellForDB > 0 ) {
        if ( ! projected ) {
          point = proj.toMapPixels( entry.getKey(), point );
        }
        float size = cellSize;
        Paint paint = value.newCellForDB > 0 ? trailCellDBPaint : trailCellPaint;
        if ( circleSizeMap ) {
          size = (Math.max(value.newCellForDB, value.newCellForRun) * boost) + 1;
          paint = value.newCellForDB > 0 ? trailCellDBSizePaint : trailCellSizePaint;
        }
        c.drawRect(point.x - size, point.y - size, point.x + size, point.y + size, paint);
      }
       
      if ( osmv.getZoomLevel() >= 15 && ! circleSizeMap ) {
        int nets = value.newWifiForDB + value.newCellForDB;
        if ( ! showNewDBOnly ) {
          nets = value.newWifiForRun + value.newCellForRun;
        }
        if ( nets > 1 ) {
          final String netString = Integer.toString( nets );
          c.drawText( netString, point.x, point.y + 7, netCountBack );                
          c.drawText( netString, point.x, point.y + 7, netCount );
        }
      }
    }
  }
  
  private static boolean isFilterOn( final SharedPreferences prefs, final String prefix ) {
    return prefs.getBoolean( prefix + ListActivity.PREF_MAPF_ENABLED, true );
  }
  
  public static Matcher getFilterMatcher( final SharedPreferences prefs, final String prefix ) {
    final String regex = prefs.getString( prefix + ListActivity.PREF_MAPF_REGEX, "" );
    Matcher matcher = null;
    if ( isFilterOn( prefs, prefix ) && ! "".equals(regex) ) {
      try {
        Pattern pattern = Pattern.compile( regex, Pattern.CASE_INSENSITIVE );
        matcher = pattern.matcher( "" );
      }
      catch ( PatternSyntaxException ex ) {
        ListActivity.error("regex pattern exception: " + ex);
      }
    }
    
    return matcher;
  }
  
  public static boolean isOk( final Matcher matcher, final SharedPreferences prefs, final String prefix, 
      final Network network ) {
    
    if ( ! isFilterOn( prefs, prefix ) ) {
      return true;
    }
    
    boolean retval = true;
    
    if ( matcher != null ) {
      matcher.reset(network.getSsid());
      final boolean invert = prefs.getBoolean( prefix + ListActivity.PREF_MAPF_INVERT, false );
      final boolean matches = matcher.find();
      if ( ! matches && ! invert) {
        return false;
      }
      else if ( matches && invert ) {
        return false;
      }
    }
    
    if ( NetworkType.WIFI.equals( network.getType() ) ) {
      switch ( network.getCrypto() ) {
        case Network.CRYPTO_NONE:
          if ( ! prefs.getBoolean( prefix + ListActivity.PREF_MAPF_OPEN, true ) ) {
            return false;
          }
          break;
        case Network.CRYPTO_WEP:
          if ( ! prefs.getBoolean( prefix + ListActivity.PREF_MAPF_WEP, true ) ) {
            return false;
          }
          break;
        case Network.CRYPTO_WPA:
          if ( ! prefs.getBoolean( prefix + ListActivity.PREF_MAPF_WPA, true ) ) {
            return false;
          }
          break;
        default: 
          ListActivity.error( "unhandled crypto: " + network );
      }
    }
    else {
      if ( ! prefs.getBoolean( prefix + ListActivity.PREF_MAPF_CELL, true ) ) {
        return false;
      }
    }

    return retval;
  }
  
}
