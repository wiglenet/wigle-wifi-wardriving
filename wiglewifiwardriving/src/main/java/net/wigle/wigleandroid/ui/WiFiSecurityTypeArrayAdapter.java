package net.wigle.wigleandroid.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.wigle.wigleandroid.model.WiFiSecurityType;

public class WiFiSecurityTypeArrayAdapter extends ArrayAdapter<WiFiSecurityType> {

    public WiFiSecurityTypeArrayAdapter(final Context context) {
        super(context, android.R.layout.simple_spinner_item);
        addAll(WiFiSecurityType.values());
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView label = (TextView) super.getView(position, convertView, parent);
        WiFiSecurityType securityType = getItem(position);
        label.setCompoundDrawablesWithIntrinsicBounds(0, 0, securityType.getImageResourceId(), 0);
        return label;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        TextView label = (TextView) super.getDropDownView(position, convertView, parent);

        WiFiSecurityType securityType = getItem(position);
        label.setCompoundDrawablesWithIntrinsicBounds(0, 0, securityType.getImageResourceId(), 0);
        return label;
    }
}
