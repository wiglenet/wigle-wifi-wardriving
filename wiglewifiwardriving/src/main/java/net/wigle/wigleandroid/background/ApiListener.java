package net.wigle.wigleandroid.background;

import net.wigle.wigleandroid.WiGLEAuthException;

import org.json.JSONObject;

/**
 * Created by bobzilla on 8/30/15
 */
public interface ApiListener {
    void requestComplete(final JSONObject json, final boolean isCache) throws WiGLEAuthException;
}
