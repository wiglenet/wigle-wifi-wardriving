package net.wigle.wigleandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.google.gson.Gson;

import java.util.ArrayList;

/**
 * It seems obvious that you'd want to implement all this junk in every mobile phone app that has a list.
 * Created by rksh on 20170901
 */

public class AddressFilterAdapter extends BaseAdapter implements ListAdapter {

    //enough to make a list and update prefs from it.
    private ArrayList<String> list = new ArrayList<String>();
    private Context context;
    private final SharedPreferences prefs;
    private final String filterKey;



    public AddressFilterAdapter(ArrayList<String> list, Context context, final SharedPreferences prefs, final String filterKey) {
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
    public Object getItem(int pos) {
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

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.address_filter_list_item, null);
        }

        TextView listItemText = (TextView)view.findViewById(R.id.list_item_string);
        listItemText.setText(list.get(position));

        ImageButton deleteBtn = (ImageButton)view.findViewById(R.id.delete_btn);

        deleteBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                list.remove(position);
                Gson gson = new Gson();
                String serialized = gson.toJson(list.toArray());
                MainActivity.info(serialized);
                final SharedPreferences.Editor editor = prefs.edit();
                editor.putString(filterKey,serialized);
                editor.apply();
                notifyDataSetChanged();
                MainActivity m = MainActivity.getMainActivity();
                if (null != m) {
                    m.updateAddressFilter(filterKey);
                }
                //ALIBI: if there's no mainactivity, we can't very well log the error.
            }
        });
        return view;
    }
}
