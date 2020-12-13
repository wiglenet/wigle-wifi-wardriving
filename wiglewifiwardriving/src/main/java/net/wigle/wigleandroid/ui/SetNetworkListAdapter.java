package net.wigle.wigleandroid.ui;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.wigle.wigleandroid.AbstractListAdapter;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.OUI;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;

/**
 * the array adapter for a list of networks.
 * note: separators aren't drawn if areAllItemsEnabled or isEnabled are false
 */
public final class SetNetworkListAdapter extends AbstractListAdapter<Network> {
    private final SimpleDateFormat format;

    private final SetBackedNetworkList networks = new SetBackedNetworkList();

    public SetNetworkListAdapter(final Context context, final int rowLayout) {
        super(context, rowLayout);
        format = NetworkListUtil.getConstructionTimeFormater(context);
        if (ListFragment.lameStatic.oui == null) {
            ListFragment.lameStatic.oui = new OUI(context.getAssets());
        }
    }

    public void clearWifiAndCell() {
        networks.clearWifiAndCell();
        notifyDataSetChanged();
    }

    public void clearWifi() {
        networks.clearWifi();
        notifyDataSetChanged();
    }

    public void clearCell() {
        networks.clearCell();
        notifyDataSetChanged();
    }

    public void clearBluetooth() {
        networks.clearBluetooth();
        notifyDataSetChanged();
    }

    public void clearBluetoothLe() {
        networks.clearBluetoothLe();
        notifyDataSetChanged();
    }

    public void morphBluetoothToLe(Network n) {
        networks.morphBluetoothToLe(n);
        notifyDataSetChanged();
    }

    public  void clear() {
        networks.clear();
        notifyDataSetChanged();
    }

    @Override
    public void add(Network network) {
        switch (network.getType()) {
            case WIFI:
                addWiFi( network );
                break;
            case CDMA:
            case GSM:
            case WCDMA:
            case LTE:
                addCell(network);
                break;
            case BT:
                addBluetooth(network);
                break;
            case BLE:
                addBluetoothLe(network);
                break;
        }
    }

    public void addWiFi(Network n) {
        networks.addWiFi(n);
        notifyDataSetChanged();
    }

    public void addCell(Network n) {
        networks.addCell(n);
        notifyDataSetChanged();
    }

    public void addBluetooth(Network n) {
        networks.addBluetooth(n);
        notifyDataSetChanged();
    }

    public void addBluetoothLe(Network n) {
        networks.addBluetoothLe(n);
        notifyDataSetChanged();
    }

    public void enqueueBluetooth(Network n) {
        networks.enqueueBluetooth(n);
    }

    public void enqueueBluetoothLe(Network n) {
        networks.enqueueBluetoothLe(n);
    }

    //TODO: almost certainly the source of our duplicate BT nets in non show-current
    public void batchUpdateBt(final boolean showCurrent, final boolean updateLe, final boolean updateClassic) {

        networks.batchUpdateBt(showCurrent,updateLe,updateClassic);
        notifyDataSetChanged();
    }

    @Override
    public  boolean isEmpty() {
        return super.isEmpty();
    }

    @Override
    public  int getCount() {
        return networks.size();
    }

    @Override
    public  Network getItem(int pPosition) {
        return networks.get(pPosition);
    }

    @Override
    public  long getItemId(int pPosition) {
        try {
            //should i just hash the object?
            if (null != networks.get(pPosition)) {
                return networks.get(pPosition).getBssid().hashCode();
            }
        } catch (final IndexOutOfBoundsException ex) {
            MainActivity.info("index out of bounds on getItem: " + pPosition + " ex: " + ex, ex);
        }
        return 0L;
    }

    @Override
    public  boolean hasStableIds() {
        return true;
    }

    public void sort(Comparator comparator) {
        networks.sort(comparator);
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        // long start = System.currentTimeMillis();
        View row;

        if (null == convertView) {
            row = mInflater.inflate(R.layout.row, parent, false);
        } else {
            row = convertView;
        }

        Network network;
        try {
            network = getItem(position);
        } catch (final IndexOutOfBoundsException ex) {
            // yes, this happened to someone
            MainActivity.info("index out of bounds: " + position + " ex: " + ex);
            return row;
        }

        if (null == network) {
            return row;
        }
        // info( "listing net: " + network.getBssid() );

        final ImageView ico = (ImageView) row.findViewById(R.id.wepicon);
        ico.setImageResource(NetworkListUtil.getImage(network));

        final ImageView btico = (ImageView) row.findViewById(R.id.bticon);
        if (NetworkType.BT.equals(network.getType()) || NetworkType.BLE.equals(network.getType())) {
            btico.setVisibility(View.VISIBLE);
            Integer btImageId = NetworkListUtil.getBtImage(network);
            if (null == btImageId) {
                btico.setVisibility(View.GONE);
            } else {
                btico.setImageResource(btImageId);
            }
        } else {
            btico.setVisibility(View.GONE);
        }

        TextView tv = (TextView) row.findViewById(R.id.ssid);
        tv.setText(network.getSsid() + " ");

        tv = (TextView) row.findViewById(R.id.oui);
        final String ouiString = network.getOui(ListFragment.lameStatic.oui);
        final String sep = ouiString.length() > 0 ? " - " : "";
        tv.setText(ouiString + sep);

        tv = (TextView) row.findViewById(R.id.time);
        tv.setText(NetworkListUtil.getConstructionTime(format, network));

        tv = (TextView) row.findViewById(R.id.level_string);
        final int level = network.getLevel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tv.setTextColor(NetworkListUtil.getTextColorForSignal(parent.getContext(), level));
        } else {
            tv.setTextColor(NetworkListUtil.getSignalColor(level, false));
        }
        tv.setText(Integer.toString(level));

        tv = (TextView) row.findViewById(R.id.detail);
        String det = network.getDetail();
        tv.setText(det);
        // status( position + " view done. ms: " + (System.currentTimeMillis() - start ) );

        return row;
    }
}
