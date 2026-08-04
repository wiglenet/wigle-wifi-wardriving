package net.wigle.wigleandroid.ui;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static net.wigle.wigleandroid.R.color.list_item_match_background;
import static net.wigle.wigleandroid.model.NetworkType.BLE;

import android.content.Context;
import android.content.SharedPreferences;
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
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.OUI;
import net.wigle.wigleandroid.model.RssiSample;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.RssiHistoryCache;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

/**
 * the array adapter for a list of networks.
 * note: separators aren't drawn if areAllItemsEnabled or isEnabled are false
 */
public final class SetNetworkListAdapter extends AbstractListAdapter<Network> {

    private final SetBackedNetworkList networks = new SetBackedNetworkList();

    private final boolean historical;
    private final MainActivity mainActivity;
    private final SharedPreferences prefs;

    public SetNetworkListAdapter(final Context context, final boolean historical, final int rowLayout) {
        super(context, rowLayout);
        this.historical = historical;
        if (ListFragment.lameStatic.oui == null) {
            ListFragment.lameStatic.oui = new OUI(context.getAssets());
        }
        mainActivity = MainActivity.getMainActivity();
        prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
    }

    public void updateBssidFilterMatcher() {
        MainActivity mainActivity = MainActivity.getMainActivity();
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

    public void batchUpdateBt(final boolean showCurrent, final boolean updateLe, final boolean updateClassic) {
        networks.batchUpdateBt(showCurrent,updateLe,updateClassic);
        //TODO: could simply move list sort here, since they're always paired
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
        final ViewHolder holder;
        final View row;

        if (null == convertView) {
            row = mInflater.inflate(R.layout.row, parent, false);
            holder = new ViewHolder(row);
            final int fillColor = ContextCompat.getColor(getContext(), R.color.colorListSsidText);
            holder.histogramDrawable = RssiHistogramDrawable.forListRow(fillColor);
            holder.histogramView.setBackground(holder.histogramDrawable);
            row.setTag(holder);
        } else {
            row = convertView;
            holder = (ViewHolder) row.getTag();
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

        boolean matches = false;
        Matcher bssidAlertMatcher = null != mainActivity ?
                mainActivity.getBssidFilterMatcher(PreferenceKeys.PREF_ALERT_ADDRS) : null;
        if (null != bssidAlertMatcher) {
            bssidAlertMatcher.reset(network.getBssid());
            matches = bssidAlertMatcher.find();
        }
        if (BLE.equals(network.getType())) {
            Matcher mfgrAlertMatcher = null != mainActivity ?
                    mainActivity.getBssidFilterMatcher(PreferenceKeys.PREF_ALERT_BLE_MFGR_IDS) : null;
            if (null != mfgrAlertMatcher) {
                mfgrAlertMatcher.reset(String.format("%04X", network.getBleMfgrId()));
                matches |= mfgrAlertMatcher.find();
            }
        }
        if (matches) {
            row.setBackgroundColor(row.getResources().getColor(list_item_match_background));
        } else {
            row.setBackgroundColor(0);
        }

        bindHistogram(holder, network);

        holder.wepIcon.setImageResource(NetworkListUtil.getImage(network));

        if (NetworkType.BT.equals(network.getType()) || BLE.equals(network.getType())) {
            holder.btIcon.setVisibility(View.VISIBLE);
            Integer btImageId = NetworkListUtil.getBtImage(network);
            if (null == btImageId) {
                holder.btIcon.setVisibility(View.GONE);
            } else {
                holder.btIcon.setImageResource(btImageId);
                ImageViewCompat.setImageTintList(holder.btIcon, ColorStateList.valueOf(
                        ContextCompat.getColor(getContext(), R.color.colorNavigationItemFg)));
            }
        } else {
            holder.btIcon.setVisibility(View.GONE);
        }

        if (NetworkType.WIFI.equals(network.getType())) {
            if (network.isPasspoint()) {
                holder.passpointIcon.setVisibility(VISIBLE);
            } else {
                holder.passpointIcon.setVisibility(GONE);
            }
        } else {
            holder.passpointIcon.setVisibility(GONE);
        }

        if (BLE.equals(network.getType())) {
            final Integer bleAddressType = network.getBleAddressType();
            if (null != bleAddressType /*&& (bleAddressType == ADDRESS_TYPE_RANDOM || bleAddressType == ADDRESS_TYPE_ANONYMOUS)*/) {
                final Integer img = NetworkListUtil.getBleAddrTypeImage(bleAddressType, network.getBssid());
                if (null != img) {
                    holder.btRandom.setImageResource(img);
                    holder.btRandom.setVisibility(View.VISIBLE);
                } else {
                    holder.btRandom.setVisibility(View.GONE);
                }
            } else {
                //DEBUG: Logging.error("null/random address type: "+bleAddressType);
                holder.btRandom.setVisibility(View.GONE);
            }
        } else {
            holder.btRandom.setVisibility(View.GONE);
        }

        holder.ssid.setText(network.getSsid());

        final String ouiString = network.getOui(ListFragment.lameStatic.oui);
        final String sep = ouiString.length() > 0 ? " - " : "";
        holder.oui.setText(ouiString + sep);
        if (BLE.equals(network.getType())) {
            holder.oui.setTextAppearance(R.style.ListBt);
        } else {
            holder.oui.setTextAppearance(R.style.ListOui);
        }

        holder.time.setText(NetworkListUtil.getTime(network, historical, getContext()));

        final int level = network.getLevel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            holder.level.setTextColor(NetworkListUtil.getTextColorForSignal(parent.getContext(), level));
        } else {
            holder.level.setTextColor(NetworkListUtil.getSignalColor(level, false));
        }
        holder.level.setText(Integer.toString(level));

        holder.mac.setText(network.getBssid());

        if (NetworkType.WIFI.equals(network.getType())) {
            holder.chanFreq.setText(network.getFrequency()+"MHz");
        } else if (BLE.equals(network.getType())) {
            holder.chanFreq.setText(network.getType().toString());
        } else {
            holder.chanFreq.setText("");
        }

        holder.detail.setText(network.getDetail());

        return row;
    }

    private void bindHistogram(final ViewHolder holder, final Network network) {
        if (holder.histogramDrawable == null || holder.histogramView == null) {
            return;
        }
        final boolean show = !historical
                && prefs != null
                && prefs.getBoolean(PreferenceKeys.PREF_DISPLAY_INLINE_LIST_SIGNAL_HISTOGRAMS, false);
        if (!show) {
            holder.histogramDrawable.clear();
            return;
        }
        final MainActivity.State state = MainActivity.getStaticState();
        final RssiHistoryCache cache = state != null ? state.rssiHistoryCache : null;
        if (cache == null || !cache.isEnabled()) {
            holder.histogramDrawable.clear();
            return;
        }
        final List<RssiSample> samples = cache.getSeries(network.getBssid());
        if (samples.isEmpty()) {
            holder.histogramDrawable.clear();
            return;
        }
        holder.histogramDrawable.setSamples(samples, System.currentTimeMillis());
    }

    private static final class ViewHolder {
        final View histogramView;
        final ImageView wepIcon;
        final ImageView btIcon;
        final ImageView passpointIcon;
        final ImageView btRandom;
        final TextView ssid;
        final TextView oui;
        final TextView time;
        final TextView level;
        final TextView mac;
        final TextView chanFreq;
        final TextView detail;
        RssiHistogramDrawable histogramDrawable;

        ViewHolder(final View row) {
            histogramView = row.findViewById(R.id.rssi_histogram);
            wepIcon = row.findViewById(R.id.wepicon);
            btIcon = row.findViewById(R.id.bticon);
            passpointIcon = row.findViewById(R.id.passpoint_logo_view);
            btRandom = row.findViewById(R.id.btrandom);
            ssid = row.findViewById(R.id.ssid);
            oui = row.findViewById(R.id.oui);
            time = row.findViewById(R.id.time);
            level = row.findViewById(R.id.level_string);
            mac = row.findViewById(R.id.mac_string);
            chanFreq = row.findViewById(R.id.chan_freq_string);
            detail = row.findViewById(R.id.detail);
        }
    }
}
