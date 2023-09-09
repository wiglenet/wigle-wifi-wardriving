package net.wigle.wigleandroid.ui;

import android.database.Cursor;

import java.text.DateFormat;
import java.util.Date;

public class GpxListItem {
    private String name;
    private long runId;
    private static DateFormat df;
    private static DateFormat tf;
    public GpxListItem(DateFormat df) {
        this.df = df;
    }

    public void setName(String name){
        this.name=name;
    }
    public String getName(){
        return name;
    }

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }

    public static GpxListItem fromCursor(Cursor cursor, DateFormat df, DateFormat tf) {
        GpxListItem item = new GpxListItem(df);
        long unixTimeStart = cursor.getLong(2);
        long unixTimeEnd = cursor.getLong(3);
        int count = cursor.getInt(4);
        if (unixTimeStart >= 0L) {
            Date startDate = new Date(unixTimeStart);
            String startDateString = df.format(startDate);
            String startTimeString = tf.format(startDate);
            if (unixTimeEnd >= 0L) {
                Date endDate = new Date(unixTimeEnd);
                String endDateString = df.format(endDate);
                String label = startDateString;
                if (!startDateString.equals(endDateString)) {
                    label +=  " - " + endDateString;
                } else {
                    String endTimeString = tf.format(endDate);
                    label += " " + startTimeString + " - " + endTimeString;
                }
                item.setName(label + " ("+count+")");
            } else {
                item.setName(startDateString + " " + startTimeString +  " ("+count+")");
            }
            item.setRunId(cursor.getLong(1));
        } else {
            item.setName("Undefined");
        }
        return item;
    }
}
