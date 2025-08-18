package net.wigle.wigleandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import net.wigle.wigleandroid.util.Logging;

import java.util.List;
import java.util.Locale;

/**
 * It seems obvious that you'd want to implement all this junk in every mobile phone app that has a list.
 * Created by rksh on 20170901
 */

public class AddressFilterAdapter extends ArrayAdapter<String> implements ListAdapter {

    //enough to make a list and update prefs from it.
    private final List<String> list;
    private final Context context;
    private final SharedPreferences prefs;
    private final String filterKey;


    public AddressFilterAdapter(List<String> list, final int resource, Context context,
                                final SharedPreferences prefs, final String filterKey) {
        super(context, resource ,list);
        this.list = list;
        this.context = context;
        this.prefs = prefs;
        this.filterKey = filterKey;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public String getItem(int pos) {
        return list.get(pos);
    }

    @Override
    public long getItemId(int pos) {
        /*
        ALIBI: MACs are too long for Longs :/
        String hexAddr = list.get(pos);
        if (null != hexAddr) {
            return Long.decode(hexAddr.replace(":",""));
        }*/
        return 0L;
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.address_filter_list_item, parent, false);
        }

        TextView listItemText = view.findViewById(R.id.list_item_string);
        listItemText.setText(list.get(position));

        TextView listItemOui = view.findViewById(R.id.address_oui);
        if (null != listItemOui) {
            final String lookup = list.get(position).replace(":", "").toUpperCase(Locale.ROOT);
            if (ListFragment.lameStatic.oui != null && lookup.length() >= 6) {
                String result = ListFragment.lameStatic.oui.getOui(lookup.substring(0, 6));
                listItemOui.setText(result);
            }
        }
        ImageButton deleteBtn = view.findViewById(R.id.delete_btn);

        deleteBtn.setOnClickListener(v -> {
            if (position < list.size()) list.remove(position);
            Gson gson = new Gson();
            String serialized = gson.toJson(list.toArray());
            Logging.info(serialized);
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString(filterKey,serialized);
            editor.apply();
            notifyDataSetChanged();
            MainActivity m = MainActivity.getMainActivity();
            if (null != m) {
                m.updateAddressFilter(filterKey);
            }
        });
        return view;
    }
}
