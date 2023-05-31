package net.wigle.wigleandroid.ui;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.R;

public class AuthenticatedFragment extends Fragment {
    public void showAuthDialog() {
        final FragmentActivity fa = getActivity();
        if (null != fa) {
            WiGLEAuthDialog.createDialog(fa, getString(R.string.login_title),
                    getString(R.string.login_required), getString(R.string.login),
                    getString(R.string.cancel));
        }
    }
}
