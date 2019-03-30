package androidx.fragment.app;

import android.os.Bundle;
import android.os.Parcel;

/**
 * Created by arkasha on 7/3/17.
 */

public class FixedMapSavedState extends Fragment.SavedState {
    FixedMapSavedState(Bundle b) {
        super(b);
    }


    FixedMapSavedState(Parcel in, ClassLoader loader) {
        super(in, loader);
    }

    public FixedMapSavedState(Fragment.SavedState ss, ClassLoader cl) {
        super(ss.mState);
        ss.mState.setClassLoader(cl);
    }
}

