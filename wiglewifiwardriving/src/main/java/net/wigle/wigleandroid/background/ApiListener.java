package net.wigle.wigleandroid.background;

import org.json.JSONObject;

/**
 * Created by bobzilla on 8/30/15
 */
public interface ApiListener {
    void requestComplete(final JSONObject json);
}
