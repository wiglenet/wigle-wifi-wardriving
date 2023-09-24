package net.wigle.wigleandroid.model;

import net.wigle.wigleandroid.R;

/**
 * Network type for filtering searches
 */
public enum NetworkFilterType {
    ALL, WIFI, BT, CELL;

    public Integer getImageResourceId() {
        switch (this) {
            case WIFI:
                return R.drawable.ic_wifi_sm;
            case CELL:
                return R.drawable.ic_cell_sm;
            case BT:
                return R.drawable.ic_bt_sm;
            default:
                return R.drawable.cancel_button;
        }
    }

    public Integer getStringResourceId() {
        switch (this) {
            case WIFI:
            case CELL:
            case BT:
                return R.string.network_type;
            default:
                return R.string.map_all;
        }
    }
}
