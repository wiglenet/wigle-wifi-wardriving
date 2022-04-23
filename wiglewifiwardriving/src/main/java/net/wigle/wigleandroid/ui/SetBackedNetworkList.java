package net.wigle.wigleandroid.ui;

import androidx.annotation.NonNull;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.util.Logging;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * Because a SetList is for bands.
 */
public class SetBackedNetworkList extends AbstractList<Network> implements List<Network> {
    private final List<Network> unsafeNetworks = new ArrayList<>();
    private final List<Network> networks = Collections.synchronizedList(unsafeNetworks);

    private final Set<Network> btNets = new HashSet<>();
    private final Set<Network> leNets = new HashSet<>();
    private final Set<Network> nextBtNets = new HashSet<>();
    private final Set<Network> nextLeNets = new HashSet<>();
    private final Set<Network> cellNets = new HashSet<>();
    private final Set<Network> wifiNets = new HashSet<>();

    @Override
    public int size() {
        return btNets.size() + leNets.size() + cellNets.size() + wifiNets.size();
    }

    @Override
    public boolean isEmpty() {
        return btNets.isEmpty() && leNets.isEmpty() && cellNets.isEmpty() && wifiNets.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        if (o != null && o instanceof Network) {
            switch (((Network) o).getType()) {
                case BLE:
                    return leNets.contains(o);
                case BT:
                    return btNets.contains(o);
                case WIFI:
                    return wifiNets.contains(o);
                case CDMA:
                case GSM:
                case LTE:
                case WCDMA:
                    return cellNets.contains(o);
                default:
                    return false;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Iterator<Network> iterator() {
        return networks.iterator();
    }

    @NonNull
    @Override
    public Object[] toArray() {
        return networks.toArray();
    }

    @NonNull
    @Override
    public <T> T[] toArray(@NonNull T[] ts) {
        if (ts.length < networks.size()) {
            ts = (T[]) Array.newInstance(ts.getClass().getComponentType(), networks.size());
        } else if (ts.length > networks.size()) {
            ts[networks.size()] = null;
        }
        System.arraycopy(networks.toArray(), 0, ts, 0, networks.size());
        return ts;
    }

    /**
     * fulfilling the contract. not recommended due to introspection
     * @param network
     * @return
     */
    @Deprecated
    @Override
    public boolean add(Network network) {
        if (network != null) {
            boolean newNet;
            switch (network.getType()) {
                case BLE:
                    newNet = leNets.add(network);
                    break;
                case BT:
                    newNet = btNets.add(network);
                    break;
                case WIFI:
                    newNet = wifiNets.add(network);
                    break;
                case CDMA:
                case GSM:
                case LTE:
                case WCDMA:
                    newNet = cellNets.add(network);
                    break;
                default:
                    return false;
            }
            if (newNet) {
                networks.add(network);
            }
            return newNet;
        }
        return false;
    }

    @Override
    public boolean remove(Object o) {
        if (o != null && o instanceof Network) {
            boolean found;
            switch (((Network) o).getType()) {
                case BLE:
                    found = leNets.remove(o);
                    break;
                case BT:
                    found = btNets.remove(o);
                    break;
                case WIFI:
                    found = wifiNets.remove(o);
                    break;
                case CDMA:
                case GSM:
                case LTE:
                case WCDMA:
                    found = cellNets.remove(o);
                    break;
                default:
                    return false;
            }
            if (found) {
                return networks.remove(o);
            }
        }
        return false;
    }

    @Override
    public boolean containsAll(@NonNull Collection<?> collection) {
        return networks.containsAll(collection);
    }

    private Set<Network> addAllToSets(@NonNull Collection<? extends Network> collection) {
        Set<Network> added = new HashSet<>();
        for (Network net: collection) {
            switch (net.getType()) {
                case BLE:
                    if (leNets.add(net)) {
                        added.add(net);
                    }
                    break;
                case BT:
                    if (btNets.add(net)) {
                        added.add(net);
                    }
                    break;
                case WIFI:
                    if (wifiNets.add(net)) {
                        added.add(net);
                    }
                    break;
                case CDMA:
                case GSM:
                case LTE:
                case WCDMA:
                    if (cellNets.add(net)) {
                        added.add(net);
                    }
                    break;
                default:
                    Logging.error("unhandled addAll case: "+net.getType());
                    break;
            }
        }
        return added;
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends Network> collection) {
        Set<Network> added = addAllToSets(collection);
        //ALIBI: this is a slight hack, as networks may have an existing spurious reference
        return networks.addAll(added);
    }

    @Override
    public boolean addAll(int i, @NonNull Collection<? extends Network> collection) {
        Logging.info("addAll w/ offset: "+i);
        Set<Network> added = addAllToSets(collection);
        for (Network net: collection) {
            int offset = i;
            if (added.contains(net) && !networks.contains(net)) {
                //TODO: just everything about this is fragile.
                networks.add(offset, net);
                offset++;
            }
        }
        return false;
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> collection) {
        boolean succeeded = false;
        Iterator netIter = collection.iterator();
        while (netIter.hasNext()) {
            succeeded |= remove(netIter.next());
        }
        return succeeded;
    }

    @Override
    public boolean retainAll(@NonNull Collection<?> collection) {
        btNets.retainAll(collection);
        leNets.retainAll(collection);
        wifiNets.retainAll(collection);
        cellNets.retainAll(collection);
        Set retainNets = new HashSet<Network>();
        //TODO: perftesting
        retainNets.addAll(btNets);
        retainNets.addAll(leNets);
        retainNets.addAll(cellNets);
        retainNets.addAll(wifiNets);
        return networks.retainAll(retainNets);
    }

    @Override
    public void clear() {
        btNets.clear();
        leNets.clear();
        wifiNets.clear();
        cellNets.clear();
        networks.clear();
    }

    @Override
    public Network get(int i) {
        try {
            return networks.get(i);
        } catch (IndexOutOfBoundsException iobex) {
            Logging.error("failed SBNL.get - index out of bound (likely structure changed)");
            return null;
        }
    }

    @Override
    public Network set(int i, Network network) {
        Logging.info("set-at index "+i);
        if (null != network) {
            boolean newNet = false;
            switch (network.getType()) {
                case BLE:
                    newNet = leNets.add(network);
                    break;
                case BT:
                    newNet = btNets.add(network);
                    break;
                case WIFI:
                    newNet = wifiNets.add(network);
                    break;
                case CDMA:
                case GSM:
                case LTE:
                case WCDMA:
                    newNet = cellNets.add(network);
                    break;
                default:
                    break;
            }
            if (newNet) {
                networks.add(i, network);
            } else {
                networks.remove(network);
                networks.set(i, network);
                //TODO: this is dicey
            }
            return network;
        }
        return null;
    }

    @Override
    public void add(int i, Network network) {
        Logging.info("add-at index "+i);
        if (null != network) {
            boolean newNet = false;
            switch (network.getType()) {
                case BLE:
                    newNet = leNets.add(network);
                    break;
                case BT:
                    newNet = btNets.add(network);
                    break;
                case WIFI:
                    newNet = wifiNets.add(network);
                    break;
                case CDMA:
                case GSM:
                case LTE:
                case WCDMA:
                    newNet = cellNets.add(network);
                    break;
                default:
                    break;
            }
            if (newNet) {
                networks.add(i, network);
            } else {
                throw new UnsupportedOperationException("Element already present in ordered Network list");
                //TODO: would we remove it from its current slot and move it?
                // this is dicey
            }
        }
    }

    @Override
    public Network remove(int i) {
        Network n = networks.get(i);
        if (null != n) {
            switch (n.getType()) {
                case BLE:
                    leNets.remove(n);
                case BT:
                    btNets.remove(n);
                case WIFI:
                    wifiNets.remove(n);
                case CDMA:
                case GSM:
                case LTE:
                case WCDMA:
                    cellNets.remove(n);
                default:
                    break;
            }
            return networks.remove(i);
        }
        return null;
    }

    @Override
    public int indexOf(Object o) {
        return networks.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return networks.lastIndexOf(o);
    }

    @NonNull
    @Override
    public ListIterator<Network> listIterator() {
        return networks.listIterator();
    }

    @NonNull
    @Override
    public ListIterator<Network> listIterator(int i) {
        return networks.listIterator(i);
    }

    @NonNull
    @Override
    public List<Network> subList(int i, int i1) {
        return networks.subList(i, i1);
    }

    public void clearWifiAndCell() {
        networks.removeAll(wifiNets);
        networks.removeAll(cellNets);
        wifiNets.clear();
        cellNets.clear();
    }

    public void clearWifi() {
        networks.removeAll(wifiNets);
        wifiNets.clear();
    }

    public void clearCell() {
        networks.removeAll(cellNets);
        cellNets.clear();
    }

    public void clearBluetooth() {
        networks.removeAll(btNets);
        btNets.clear();
    }

    public void clearBluetoothLe() {
        networks.removeAll(leNets);
        leNets.clear();
    }

    public void morphBluetoothToLe(Network n) {
        btNets.remove(n);
        leNets.add(n);
    }

    public void addWiFi(Network n) {
        if (null != n) {
            networks.add(n);
            wifiNets.add(n);
        }
    }

    public void addCell(Network n) {
        if (null != n) {
            networks.add(n);
            cellNets.add(n);
        }
    }

    public void addBluetooth(Network n) {
        if (null != n) {
            if (!btNets.contains(n) && !networks.contains(n)) {
                networks.add(n);
                btNets.add(n);
            }
        }
    }

    public void addBluetoothLe(Network n) {
        if (null != n) {
            if (!leNets.contains(n) && !networks.contains(n)) {
                networks.add(n);
                leNets.add(n);
            }
        }
    }

    public void enqueueBluetooth(Network n) {
        if (null != n) {
            if (!btNets.contains(n) && !networks.contains(n)) {
                nextBtNets.add(n);
            }
        }
    }

    public void enqueueBluetoothLe(Network n) {
        if (null != n) {
            if (!leNets.contains(n) && !networks.contains(n)) {
                nextLeNets.add(n);
            }
        }
    }

    public void batchUpdateBt(final boolean showCurrent, final boolean updateLe, final boolean updateClassic) {
        if (showCurrent) {
            //ALIBI: if we're in current-only, strip last from networks and sets, add new to set, add revamped set to networks
            if (updateLe) {
                networks.removeAll(leNets);
                //TODO: 1/2: faster to clear and re-add all?
                leNets.retainAll(nextLeNets);
                leNets.addAll(nextLeNets);
                networks.addAll(leNets);
            }
            if (updateClassic) {
                networks.removeAll(btNets);
                //TODO: 2/2: faster to clear and re-add all?
                btNets.retainAll(nextBtNets);
                btNets.addAll(nextBtNets);
                networks.addAll(btNets);
            }
        } else {
            //ALIBI: if it's cumulative, add anything not in the sets to sets and networks
            if (updateLe) {
                for (Network net : nextLeNets) {
                    if (leNets.add(net)) {
                        networks.add(net);
                    }
                }
            }
            if (updateClassic) {
                for (Network net : nextBtNets) {
                    if (btNets.add(net)) {
                        networks.add(net);
                    }
                }
            }
        }
        if (updateClassic) {
            nextBtNets.clear();
        }
        if (updateLe) {
            nextLeNets.clear();
        }
    }

    @Override
    public void sort(Comparator comparator) {
        try {
            Collections.sort(networks, comparator);
        } catch (IllegalArgumentException iaex) {
            Logging.warn("SBNL.sort: IllegalArgumentException", iaex);
            iaex.printStackTrace();
            //ALIBI: missing a sort isn't a critical error, since this list gets updated continually
        }
    }
}
