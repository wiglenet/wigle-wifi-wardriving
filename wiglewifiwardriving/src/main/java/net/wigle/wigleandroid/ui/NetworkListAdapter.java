package net.wigle.wigleandroid.ui;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.wigle.wigleandroid.AbstractListAdapter;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.OUI;
import net.wigle.wigleandroid.util.Logging;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * the array adapter for a list of networks.
 * note: separators aren't drawn if areAllItemsEnabled or isEnabled are false
 */
@Deprecated
public final class NetworkListAdapter extends AbstractListAdapter<Network> {

    private final SimpleDateFormat format;

    private final List<Network> unsafeNetworks = new ArrayList<>();
    private final List<Network> networks = Collections.synchronizedList(unsafeNetworks);

    //TODO: do these tracking lists need to be synchronized as well?
    private final Set<Network> btNets = new HashSet<>();
    private final Set<Network> leNets = new HashSet<>();
    private final Set<Network> nextBtNets = new HashSet<>();
    private final Set<Network> nextLeNets = new HashSet<>();
    private final List<Network> cellNets = new ArrayList<>();
    private final List<Network> wifiNets = new ArrayList<>();

    public NetworkListAdapter(final Context context, final int rowLayout) {
        super(context, rowLayout);
        format = NetworkListUtil.getConstructionTimeFormater(context);
        if (ListFragment.lameStatic.oui == null) {
            ListFragment.lameStatic.oui = new OUI(context.getAssets());
        }
    }

    public void clearWifiAndCell() {
        networks.removeAll(wifiNets);
        networks.removeAll(cellNets);
        wifiNets.clear();
        cellNets.clear();
        notifyDataSetChanged();
    }

    public void clearWifi() {
        networks.removeAll(wifiNets);
        wifiNets.clear();
        notifyDataSetChanged();
    }

    public void clearCell() {
        networks.removeAll(cellNets);
        cellNets.clear();
        notifyDataSetChanged();
    }

    public void clearBluetooth() {
        networks.removeAll(btNets);
        btNets.clear();
        notifyDataSetChanged();
    }

    public void clearBluetoothLe() {
        networks.removeAll(leNets);
        leNets.clear();
        notifyDataSetChanged();
    }

    public void morphBluetoothToLe(Network n) {
        btNets.remove(n);
        leNets.add(n);
        notifyDataSetChanged();
    }

    public  void clear() {
        networks.clear();
        wifiNets.clear();
        cellNets.clear();
        btNets.clear();
        leNets.clear();
        notifyDataSetChanged();
    }

    public void addWiFi(Network n) {
        networks.add(n);
        wifiNets.add(n);
        notifyDataSetChanged();
    }

    public void addCell(Network n) {
        networks.add(n);
        cellNets.add(n);
        notifyDataSetChanged();
    }

    public void addBluetooth(Network n) {
        if (!btNets.contains(n) && !networks.contains(n)) {
            networks.add(n);
            btNets.add(n);
            notifyDataSetChanged();
        //} else if (!btNets.contains(n)) {
        //    MainActivity.info("BT add error - "+ n.getBssid() +" present in nets");
        //} else if (!networks.contains(n)) {
        //    MainActivity.info("BT add error - "+ n.getBssid() +" present in btnets");
        }
    }

    public void addBluetoothLe(Network n) {
        if (!leNets.contains(n) && !networks.contains(n)) {
            networks.add(n);
            leNets.add(n);
            notifyDataSetChanged();
        //} else if (!btNets.contains(n)) {
        //    MainActivity.info("BTLE add error - "+ n.getBssid() +" present in nets");
        //} else if (networks.contains(n)) {
        //    MainActivity.info("BTLE add error - "+ n.getBssid() +" present in lenets");
        }
    }

    public void enqueueBluetooth(Network n) {
        if (!btNets.contains(n) && !networks.contains(n)) {
            nextBtNets.add(n);
        //} else if (!btNets.contains(n)) {
        //    MainActivity.info("BT enqueue error - "+ n.getBssid() +" present in nets");
        }
    }

    public void enqueueBluetoothLe(Network n) {
        if (!leNets.contains(n) && !networks.contains(n)) {
            nextLeNets.add(n);
        //} else if (!btNets.contains(n)) {
        //    MainActivity.info("BTLE enqueue error - "+ n.getBssid() +" present in nets");
        }
    }

    //TODO: almost certainly the source of our duplicate BT nets in non show-current
    public void batchUpdateBt(final boolean showCurrent, final boolean updateLe, final boolean updateClassic) {

        if (showCurrent) {
            if (updateLe) {
                networks.removeAll(leNets);
                leNets.retainAll(nextLeNets);
            }
            if (updateClassic) {
                networks.removeAll(btNets);
                btNets.retainAll(nextBtNets);
            }
        }
        if (updateLe) {
            leNets.addAll(nextLeNets);
            for (Network leNet: leNets) {
                if (!networks.contains(leNet)) {
                    networks.add(leNet);
                }
            }
        }
        if (updateClassic) {
            btNets.addAll(nextBtNets);
            for (Network btNet: btNets) {
                if (!networks.contains(btNet)) {
                    networks.add(btNet);
                }
            }
            //networks.addAll(btNets);
        }
        notifyDataSetChanged();

        if (updateClassic) {
            nextBtNets.clear();
        }
        if (updateLe) {
            nextLeNets.clear();
        }
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
            return networks.get(pPosition).getBssid().hashCode();
        }
        catch (final IndexOutOfBoundsException ex) {
            Logging.info("index out of bounds on getItem: " + pPosition + " ex: " + ex, ex);
        }
        return 0L;
    }

    @Override
    public  boolean hasStableIds() {
        return true;
    }

    public void sort(Comparator comparator) {
        Collections.sort(networks, comparator);
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
            Logging.info("index out of bounds: " + position + " ex: " + ex);
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
            }
        } else {
            btico.setVisibility(View.GONE);
        }

        TextView tv = row.findViewById(R.id.ssid);
        tv.setText(network.getSsid() + " ");

        tv = row.findViewById(R.id.oui);
        final String ouiString = network.getOui(ListFragment.lameStatic.oui);
        final String sep = ouiString.length() > 0 ? " - " : "";
        tv.setText(ouiString + sep);

        tv = row.findViewById(R.id.time);
        tv.setText(NetworkListUtil.getConstructionTime(format, network));

        tv = row.findViewById(R.id.level_string);
        final int level = network.getLevel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tv.setTextColor(NetworkListUtil.getTextColorForSignal(parent.getContext(), level));
        } else {
            tv.setTextColor(NetworkListUtil.getSignalColor(level, false));
        }
        tv.setText(Integer.toString(level));

        tv = row.findViewById(R.id.detail);
        String det = network.getDetail();
        tv.setText(det);
        // status( position + " view done. ms: " + (System.currentTimeMillis() - start ) );

        return row;
    }
}
