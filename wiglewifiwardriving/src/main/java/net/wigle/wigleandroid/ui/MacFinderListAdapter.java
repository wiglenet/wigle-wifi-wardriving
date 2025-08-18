package net.wigle.wigleandroid.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;

import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.util.Logging;

import java.util.List;

/**
 * A list adapter for MAC/OUI detection results with checkboxes.
 * @author rksh
 */
public class MacFinderListAdapter extends ArrayAdapter<MacFinderListView> {
    private final List<MacFinderListView> items;
    private final Context context;

    public MacFinderListAdapter(@NonNull Context context, int resource, @NonNull List<MacFinderListView> items) {
        super(context, resource, items);
        this.items = items;
        this.context = context;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.mac_oui_list_item, parent, false);
            holder = new ViewHolder();
            holder.checkBox = convertView.findViewById(R.id.mac_oui_selected);
            holder.itemName = convertView.findViewById(R.id.text_item_name);
            holder.mfgrInfo = convertView.findViewById(R.id.mfgr_info);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        MacFinderListView currentItem = items.get(position);
        holder.itemName.setText(currentItem.getName());
        holder.checkBox.setChecked(currentItem.isChecked());
        holder.mfgrInfo.setText(currentItem.getMfgrInfo());
        return convertView;
    }

    @Override
    public int getCount() {
        if (null != items) {
            return items.size();
        }
        return 0;
    }

    @Override
    public MacFinderListView getItem(int i) {
        if (null != items) {
            return items.get(i);
        }
        return null;
    }

    @Override
    public long getItemId(int i) {
        try {
            //should i just hash the object?
            if (null != items.get(i)) {
                return items.get(i).getName().hashCode();
            }
        } catch (final IndexOutOfBoundsException ex) {
            Logging.warn("index out of bounds on getItem: " + i + " ex: " + ex, ex);
        }
        return 0L;
    }

    static class ViewHolder {
        CheckBox checkBox;
        TextView itemName;
        TextView mfgrInfo;
    }
}
