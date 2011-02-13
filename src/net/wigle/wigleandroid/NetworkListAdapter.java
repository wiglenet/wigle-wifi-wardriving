package net.wigle.wigleandroid;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.graphics.Color;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * the array adapter for a list of networks.
 * note: separators aren't drawn if areAllItemsEnabled or isEnabled are false
 */
public final class NetworkListAdapter extends ArrayAdapter<Network> {
  //color by signal strength
  private static final int COLOR_1 = Color.rgb( 70, 170,  0);
  private static final int COLOR_2 = Color.rgb(170, 170,  0);
  private static final int COLOR_3 = Color.rgb(170,  95, 30);
  private static final int COLOR_4 = Color.rgb(180,  60, 40);
  private static final int COLOR_5 = Color.rgb(180,  45, 70);
  
  final LayoutInflater mInflater;
  
  private final SimpleDateFormat format;
  
  public NetworkListAdapter( Context context, int rowLayout ) {
    super( context, rowLayout );
    this.mInflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
    
    int value = Settings.System.getInt(context.getContentResolver(), Settings.System.TIME_12_24, -1);
    ListActivity.info("value: " + value);
    if ( value == 24 ) {
      format = new SimpleDateFormat("H:mm:ss");
    }
    else {
      format = new SimpleDateFormat("h:mm:ss a");
    }
  }
    
  @Override
  public View getView( final int position, final View convertView, final ViewGroup parent ) {
    // long start = System.currentTimeMillis();
    View row;
    
    if ( null == convertView ) {
      row = mInflater.inflate( R.layout.row, parent, false );
    } 
    else {
      row = convertView;
    }

    final Network network = getItem(position);
    // info( "listing net: " + network.getBssid() );
    
    final ImageView ico = (ImageView) row.findViewById( R.id.wepicon );
    if ( network.getType().equals(NetworkType.WIFI) ) {
      switch ( network.getCrypto() ) {
        case Network.CRYPTO_WEP:
          ico.setImageResource( R.drawable.wep_ico );
          break;
        case Network.CRYPTO_WPA:
          ico.setImageResource( R.drawable.wpa_ico );
          break;
        case Network.CRYPTO_NONE:
          ico.setImageResource( R.drawable.no_ico );
          break;
        default:
          throw new IllegalArgumentException( "unhanded crypto: " + network.getCrypto() 
              + " in network: " + network );
      }
    }
    else {
      ico.setImageResource( R.drawable.tower_ico );
    }
      
    TextView tv = (TextView) row.findViewById( R.id.ssid ); 
    tv.setText( network.getSsid() + " ");
    
    tv = (TextView) row.findViewById( R.id.time ); 
    tv.setText( format.format(new Date(network.getConstructionTime())));
    
    tv = (TextView) row.findViewById( R.id.level_string );
    int level = network.getLevel();
    if ( level <= -90 ) {
      tv.setTextColor( COLOR_5 );
    }
    else if ( level <= -80 ) {
      tv.setTextColor( COLOR_4 );
    }
    else if ( level <= -70 ) {
      tv.setTextColor( COLOR_3 );
    }
    else if ( level <= -60 ) {
      tv.setTextColor( COLOR_2 );
    }
    else {
      tv.setTextColor( COLOR_1 );
    }
    tv.setText( Integer.toString( level ) );
    
    tv = (TextView) row.findViewById( R.id.detail );
    String det = network.getDetail();
    tv.setText( det );
    // status( position + " view done. ms: " + (System.currentTimeMillis() - start ) );

    return row;
  }

}
