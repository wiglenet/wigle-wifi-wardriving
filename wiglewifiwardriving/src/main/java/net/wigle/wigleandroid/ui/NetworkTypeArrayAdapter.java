package net.wigle.wigleandroid.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.wigle.wigleandroid.model.NetworkFilterType;

public class NetworkTypeArrayAdapter extends ArrayAdapter<NetworkFilterType> {

    public NetworkTypeArrayAdapter(final Context context) {
        super(context, android.R.layout.simple_spinner_item);
        addAll(NetworkFilterType.values());
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView label = (TextView) super.getView(position, convertView, parent);
        NetworkFilterType netFilterType = getItem(position);
        label.setText(netFilterType.getStringResourceId());
        label.setCompoundDrawablesWithIntrinsicBounds(0, 0, netFilterType.getImageResourceId(), 0);
        return label;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        TextView label = (TextView) super.getDropDownView(position, convertView, parent);

        NetworkFilterType netFilterType = getItem(position);
        label.setText(netFilterType.getStringResourceId());
        label.setCompoundDrawablesWithIntrinsicBounds(0, 0, netFilterType.getImageResourceId(), 0);
        return label;
    }
}
