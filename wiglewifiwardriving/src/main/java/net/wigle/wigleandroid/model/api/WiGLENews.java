package net.wigle.wigleandroid.model.api;

import android.os.Bundle;

import net.wigle.wigleandroid.model.NewsItem;

import java.util.List;

/**
 * response from WiGLE news API call
 * @author arkasha
 */
public class WiGLENews {
    private Boolean success;
    private List<NewsItem> results;

    public Boolean getSuccess() {
        return success;
    }
    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public List<NewsItem> getResults() {
        return results;
    }
    public void setResults(List<NewsItem> results) {
        this.results = results;
    }
}
