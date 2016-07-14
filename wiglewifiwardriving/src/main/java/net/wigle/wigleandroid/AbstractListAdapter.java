package net.wigle.wigleandroid;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * the array adapter base
 */
public abstract class AbstractListAdapter<M> extends ArrayAdapter<M> {
    protected final LayoutInflater mInflater;
    protected final NumberFormat numberFormat;

    public AbstractListAdapter(final Context context, final int rowLayout ) {
        super( context, rowLayout );

        this.mInflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        numberFormat = NumberFormat.getNumberInstance( Locale.US );
        numberFormat.setGroupingUsed(true);
    }

    @Override
    public abstract View getView(final int position, final View convertView, final ViewGroup parent);
}
