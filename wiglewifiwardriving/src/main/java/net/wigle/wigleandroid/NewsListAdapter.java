package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.wigle.wigleandroid.model.NewsItem;
import net.wigle.wigleandroid.util.Logging;

/**
 * the array adapter for a list of uploads.
 */
public final class NewsListAdapter extends AbstractListAdapter<NewsItem> {
    public NewsListAdapter(final Context context, final int rowLayout ) {
        super( context, rowLayout );
    }

    @SuppressLint("SetTextI18n")
    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        View row;

        if ( null == convertView ) {
            row = mInflater.inflate( R.layout.newsrow, parent, false );
        }
        else {
            row = convertView;
        }

        NewsItem newsItem;
        try {
            newsItem = getItem(position);
        }
        catch ( final IndexOutOfBoundsException ex ) {
            // yes, this happened to someone
            Logging.info("index out of bounds: " + position + " ex: " + ex);
            return row;
        }

        TextView tv = row.findViewById( R.id.subject );
        tv.setText(Html.fromHtml("<a href=\""+newsItem.getLink()+"\">"+newsItem.getSubject()+"</a>"));
        tv.setMovementMethod(LinkMovementMethod.getInstance());

        tv = row.findViewById( R.id.poster_date );
        tv.setText(newsItem.getPoster() + " - " + newsItem.getDateTime());

        tv = row.findViewById( R.id.post );
        tv.setText(newsItem.getPost());
        tv.setMovementMethod(LinkMovementMethod.getInstance());

        tv = row.findViewById( R.id.link );
        tv.setText(Html.fromHtml("<a href=\""+newsItem.getLink()+"\">"+newsItem.getLink()+"</a>"));
        tv.setMovementMethod(LinkMovementMethod.getInstance());

        return row;
    }
}
