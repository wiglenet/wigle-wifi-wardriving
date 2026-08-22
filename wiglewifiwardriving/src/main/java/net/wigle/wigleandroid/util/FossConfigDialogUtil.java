package net.wigle.wigleandroid.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import net.wigle.wigleandroid.R;

/**
 * Helper that displays a dismissable dialog informing the user that their FOSS map
 * configuration appears invalid. Used as a defensive fallback whenever MapLibre's
 * MapView fails to inflate or the user's PREF_FOSS_MAPS_VECTOR_TILE_STYLE /
 * PREF_FOSS_MAPS_VECTOR_TILE_KEY produces a malformed style URL at runtime.
 */
public final class FossConfigDialogUtil {

    private FossConfigDialogUtil() {
        // utility
    }

    /**
     * Show a dismissable dialog backed by R.string.invalid_foss_config. When the dialog is
     * dismissed (OK button, back, cancel), the supplied {@code onDismiss} runnable is executed
     * exactly once. Typical usage is to finish() the host activity or pop the fragment back
     * stack, so the user is not left on a half-initialized screen that may continue to crash.
     *
     * @param activity  host activity; if null/finishing/destroyed, {@code onDismiss} is invoked
     *                  immediately and no dialog is shown.
     * @param onDismiss action to run after the user dismisses the dialog (may be null).
     */
    public static void show(final Activity activity, final Runnable onDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            if (onDismiss != null) {
                onDismiss.run();
            }
            return;
        }
        try {
            final boolean[] fired = {false};
            final Runnable fireOnce = () -> {
                if (!fired[0]) {
                    fired[0] = true;
                    if (onDismiss != null) {
                        onDismiss.run();
                    }
                }
            };
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.app_name);
            builder.setMessage(R.string.invalid_foss_config);
            builder.setCancelable(true);
            final DialogInterface.OnClickListener okListener = (d, which) -> d.dismiss();
            builder.setPositiveButton(android.R.string.ok, okListener);
            final AlertDialog dialog = builder.create();
            dialog.setOnCancelListener(d -> fireOnce.run());
            dialog.setOnDismissListener(d -> fireOnce.run());
            dialog.show();
        } catch (Throwable t) {
            Logging.error("Could not show invalid FOSS config dialog: ", t);
            if (onDismiss != null) {
                onDismiss.run();
            }
        }
    }
}
