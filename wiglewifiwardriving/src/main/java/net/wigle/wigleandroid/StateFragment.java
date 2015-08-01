package net.wigle.wigleandroid;

import android.os.Bundle;
import android.support.v4.app.Fragment;

public class StateFragment extends Fragment {
    //data object we want to retain
    private MainActivity.State state;

    // this method is only called once for this fragment
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    public void setState(MainActivity.State state) {
        this.state = state;
    }

    public MainActivity.State getState() {
        return state;
    }

}
