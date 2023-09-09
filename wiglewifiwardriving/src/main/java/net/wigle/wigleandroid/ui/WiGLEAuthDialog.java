package net.wigle.wigleandroid.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.util.Logging;

public class WiGLEAuthDialog extends DialogFragment {
    private static final int AUTHENTICATE_DIALOG = 221;
    public static WiGLEAuthDialog newInstance(final String title, final String message, final String loginTxt, final String cancelText) {
        final WiGLEAuthDialog dlg = new WiGLEAuthDialog();
        Bundle args = new Bundle();
        args.putString("message", title);
        args.putString("message", message);
        args.putString("loginTxt", loginTxt);
        args.putString("cancelTxt", cancelText);
        args.putInt("dialogId", AUTHENTICATE_DIALOG);
        dlg.setArguments(args);
        return dlg;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        final android.app.AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(getArguments().getString("title"));
        builder.setMessage(getArguments().getString("message"));
        builder.setPositiveButton(
                getArguments().getString("loginTxt"),
                (dialog, id) -> {
                    final MainActivity ma = MainActivity.getMainActivity();
                    if (null != ma && !ma.isFinishing()) {
                        ma.selectFragment(R.id.nav_settings);
                    }
                    dialog.cancel();
                });
        builder.setNegativeButton(
                getArguments().getString("cancelTxt"),
                (dialog, id) -> {
                    final MainActivity ma = MainActivity.getMainActivity();
                    if (null != ma && !ma.isFinishing()) {
                        ma.selectFragment(R.id.nav_list);
                    }
                    dialog.cancel();
                });
        return builder.create();
    }

    public static void createDialog(final FragmentActivity activity, final String title, final String message,
                                          final String loginText, final String cancelText) {
        try {
            final FragmentManager fm = activity.getSupportFragmentManager();
            final WiGLEAuthDialog dialog = WiGLEAuthDialog.newInstance(title, message, loginText, cancelText);
            Logging.info("LoginDialog fm: " + fm);
            dialog.show(fm, AUTHENTICATE_DIALOG+"");
        } catch (WindowManager.BadTokenException ex) {
            Logging.info("exception showing dialog, view probably changed: " + ex, ex);
        } catch (final IllegalStateException ex) {
            final String errorMessage = "Exception trying to show dialog: " + ex;
            Logging.error(errorMessage, ex);
        }
    }

}
