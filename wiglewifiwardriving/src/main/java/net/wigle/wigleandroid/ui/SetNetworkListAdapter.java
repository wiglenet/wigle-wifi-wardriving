package net.wigle.wigleandroid.ui;

import static android.bluetooth.BluetoothDevice.ADDRESS_TYPE_ANONYMOUS;
import static android.bluetooth.BluetoothDevice.ADDRESS_TYPE_RANDOM;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import net.wigle.wigleandroid.AbstractListAdapter;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.OUI;
import net.wigle.wigleandroid.util.Logging;

import java.util.Comparator;
import java.util.Random;

/**
 * the array adapter for a list of networks.
 * note: separators aren't drawn if areAllItemsEnabled or isEnabled are false
 */
public final class SetNetworkListAdapter extends AbstractListAdapter<Network> {

    private final SetBackedNetworkList networks = new SetBackedNetworkList();

    private final boolean historical;

    public SetNetworkListAdapter(final Context context, final boolean historical, final int rowLayout) {
        super(context, rowLayout);
        this.historical = historical;
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
    public void add( Network network) {
        if (null != network) {
            switch (network.getType()) {
                case WIFI:
                    addWiFi(network);
                    break;
                case CDMA:
                case GSM:
                case WCDMA:
                case LTE:
                case NR:
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
            Logging.info("index out of bounds on getItem: " + pPosition + " ex: " + ex, ex);
        }
        return 0L;
    }

    @Override
    public  boolean hasStableIds() {
        return true;
    }

    @Override
    public void sort(@NonNull Comparator comparator) {
        networks.sort(comparator);
    }

    @NonNull
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
            Logging.info("index out of bounds: " + position + " ex: " + ex);
            return row;
        }

        if (null == network) {
            return row;
        }
        // info( "listing net: " + network.getBssid() );

        final ImageView ico = row.findViewById(R.id.wepicon);
        ico.setImageResource(NetworkListUtil.getImage(network));

        final ImageView btico = row.findViewById(R.id.bticon);
        if (NetworkType.BT.equals(network.getType()) || NetworkType.BLE.equals(network.getType())) {
            btico.setVisibility(View.VISIBLE);
            Integer btImageId = NetworkListUtil.getBtImage(network);
            if (null == btImageId) {
                btico.setVisibility(View.GONE);
            } else {
                btico.setImageResource(btImageId);
                ImageViewCompat.setImageTintList(btico, ColorStateList.valueOf(
                        ContextCompat.getColor(getContext(), R.color.colorNavigationItemFg)));
            }
        } else {
            btico.setVisibility(View.GONE);
        }

        final ImageView btRandom = row.findViewById(R.id.btrandom);
        if (NetworkType.BLE.equals(network.getType())) {
            final Integer bleAddressType = network.getBleAddressType();
            if (null != bleAddressType && (bleAddressType == ADDRESS_TYPE_RANDOM || bleAddressType == ADDRESS_TYPE_ANONYMOUS)) {
                final Integer img = NetworkListUtil.getBleAddrTypeImage(bleAddressType);
                if (null != img) {
                    btRandom.setImageResource(img);
                    btRandom.setVisibility(View.VISIBLE);
                }
            } else {
                btRandom.setVisibility(View.GONE);
            }
        } else {
            btRandom.setVisibility(View.GONE);
        }

        TextView tv = row.findViewById(R.id.ssid);
        tv.setText(network.getSsid());

        tv = row.findViewById(R.id.oui);
        final String ouiString = network.getOui(ListFragment.lameStatic.oui);
        final String sep = ouiString.length() > 0 ? " - " : "";
        tv.setText(ouiString + sep);
        if (NetworkType.BLE.equals(network.getType())) {
            tv.setTextAppearance(R.style.ListBt);
        } else {
            tv.setTextAppearance(R.style.ListOui);
        }

        tv = row.findViewById(R.id.time);
        tv.setText(NetworkListUtil.getTime(network, historical, getContext()));

        tv = row.findViewById(R.id.level_string);
        final int level = network.getLevel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tv.setTextColor(NetworkListUtil.getTextColorForSignal(parent.getContext(), level));
        } else {
            tv.setTextColor(NetworkListUtil.getSignalColor(level, false));
        }
        tv.setText(Integer.toString(level));

        tv = row.findViewById(R.id.mac_string);
        tv.setText(network.getBssid());

        tv = row.findViewById(R.id.chan_freq_string);
        if (NetworkType.WIFI.equals(network.getType())) {
            tv.setText(network.getFrequency()+"MHz");
        } else if (NetworkType.BLE.equals(network.getType())) {
            tv.setText(network.getType().toString());
        } else {
            tv.setText("");
        }

        tv = row.findViewById(R.id.detail);
        String det = network.getDetail();
        tv.setText(det);
        // status( position + " view done. ms: " + (System.currentTimeMillis() - start ) );

        return row;
    }
}
