package net.wigle.wigleandroid.ui;

import android.app.Activity;
import android.os.Build;
import androidx.fragment.app.FragmentActivity;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.util.Logging;

/**
 * Display that magic WiGLE toast
 * Created by arkasha on 9/21/17.
 */

public class WiGLEToast {

    public static void showOverFragment(final FragmentActivity context, final int titleId,
                                        final String messageString) {
        showOverActivity(context, titleId, messageString, Toast.LENGTH_LONG);
    }

    public static void showOverActivity(final Activity context, final int titleId,
                                        final String messageString) {
        showOverActivity(context, titleId, messageString, Toast.LENGTH_SHORT);
    }

    public static void showOverActivity(final Activity context, final int titleId,
        final String messageString, final int toastLength) {
        if (null != context && !context.isFinishing()) {
            if (Build.VERSION.SDK_INT != 25) {
                LayoutInflater inflater = context.getLayoutInflater();
                View layout = inflater.inflate(R.layout.wigle_detail_toast,
                        context.findViewById(R.id.custom_toast_container));

                TextView title = layout.findViewById(R.id.toast_title_text);
                title.setText(titleId);

                TextView text = layout.findViewById(R.id.toast_message_text);
                text.setText(messageString);

                Toast toast = new Toast(context.getApplicationContext()); // https://stackoverflow.com/questions/57082435/memory-leaks-while-showing-toast-from-other-class
                //ALIBI: logs errors in Toast, but works
                toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                toast.setDuration(toastLength);
                toast.setView(layout);
                toast.show();
            } else {
                Logging.info("toast disabled because 7.1.x bombs: " + messageString);
            }
        }
    }

}
