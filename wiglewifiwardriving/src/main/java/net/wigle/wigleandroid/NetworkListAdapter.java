package net.wigle.wigleandroid;

import android.content.Context;
import android.graphics.Color;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.OUI;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * the array adapter for a list of networks.
 * note: separators aren't drawn if areAllItemsEnabled or isEnabled are false
 */
public final class NetworkListAdapter extends AbstractListAdapter<Network> {
    //color by signal strength
    private static final int COLOR_1 = Color.rgb( 70, 170,  0);
    private static final int COLOR_2 = Color.rgb(170, 170,  0);
    private static final int COLOR_3 = Color.rgb(170,  95, 30);
    private static final int COLOR_4 = Color.rgb(180,  60, 40);
    private static final int COLOR_5 = Color.rgb(180,  45, 70);

    private static final int COLOR_1A = Color.argb(128,  70, 170,  0);
    private static final int COLOR_2A = Color.argb(128, 170, 170,  0);
    private static final int COLOR_3A = Color.argb(128, 170,  95, 30);
    private static final int COLOR_4A = Color.argb(128, 180,  60, 40);
    private static final int COLOR_5A = Color.argb(128, 180,  45, 70);

    private final SimpleDateFormat format;

    public NetworkListAdapter( final Context context, final int rowLayout ) {
        super( context, rowLayout );
        format = getConstructionTimeFormater( context );
        if (ListFragment.lameStatic.oui == null) {
            ListFragment.lameStatic.oui = new OUI(context.getAssets());
        }
    }

    public static SimpleDateFormat getConstructionTimeFormater( final Context context ) {
        final int value = Settings.System.getInt(context.getContentResolver(), Settings.System.TIME_12_24, -1);
        SimpleDateFormat format;
        if ( value == 24 ) {
            format = new SimpleDateFormat("H:mm:ss", Locale.getDefault());
        }
        else {
            format = new SimpleDateFormat("h:mm:ss a", Locale.getDefault());
        }
        return format;
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

        Network network;
        try {
            network = getItem(position);
        }
        catch ( final IndexOutOfBoundsException ex ) {
            // yes, this happened to someone
            MainActivity.info("index out of bounds: " + position + " ex: " + ex);
            return row;
        }
        // info( "listing net: " + network.getBssid() );

        final ImageView ico = (ImageView) row.findViewById( R.id.wepicon );
        ico.setImageResource(getImage(network));

        TextView tv = (TextView) row.findViewById( R.id.ssid );
        tv.setText( network.getSsid() + " ");

        tv = (TextView) row.findViewById( R.id.oui );
        final String ouiString = network.getOui(ListFragment.lameStatic.oui);
        final String sep = ouiString.length() > 0 ? " - " : "";
        tv.setText( ouiString + sep );

        tv = (TextView) row.findViewById( R.id.time );
        tv.setText( getConstructionTime( format, network ) );

        tv = (TextView) row.findViewById( R.id.level_string );
        final int level = network.getLevel();
        tv.setTextColor( getSignalColor( level ) );
        tv.setText( Integer.toString( level ) );

        tv = (TextView) row.findViewById( R.id.detail );
        String det = network.getDetail();
        tv.setText( det );
        // status( position + " view done. ms: " + (System.currentTimeMillis() - start ) );

        return row;
    }

    public static String getConstructionTime( final SimpleDateFormat format, final Network network ) {
        return format.format( new Date( network.getConstructionTime() ) );
    }

    public static int getSignalColor( final int level ) {
        return getSignalColor( level, false );
    }

    public static int getSignalColor( final int level, final boolean alpha ) {
        int color = alpha ? COLOR_1A : COLOR_1;
        if ( level <= -90 ) {
            color = alpha ? COLOR_5A : COLOR_5;
        }
        else if ( level <= -80 ) {
            color = alpha ? COLOR_4A : COLOR_4;
        }
        else if ( level <= -70 ) {
            color = alpha ? COLOR_3A : COLOR_3;
        }
        else if ( level <= -60 ) {
            color = alpha ? COLOR_2A : COLOR_2;
        }

        return color;
    }

    public static int getImage( final Network network ) {
        int resource;
        if ( network.getType().equals(NetworkType.WIFI) ) {
            switch ( network.getCrypto() ) {
                case Network.CRYPTO_WEP:
                    resource = R.drawable.wep_ico;
                    break;
                case Network.CRYPTO_WPA2:
                    resource = R.drawable.wpa2_ico;
                    break;
                case Network.CRYPTO_WPA:
                    resource = R.drawable.wpa_ico;
                    break;
                case Network.CRYPTO_NONE:
                    resource = R.drawable.no_ico;
                    break;
                default:
                    throw new IllegalArgumentException( "unhanded crypto: " + network.getCrypto()
                            + " in network: " + network );
            }
        }
        else {
            resource = R.drawable.tower_ico;
        }

        return resource;
    }

}
