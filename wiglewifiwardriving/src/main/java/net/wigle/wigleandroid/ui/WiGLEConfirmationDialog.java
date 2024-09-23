package net.wigle.wigleandroid.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.wigle.wigleandroid.DialogListener;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

/**
 * A confirmation dialog to use throughout the app.
 */
public class WiGLEConfirmationDialog extends DialogFragment {
    public static WiGLEConfirmationDialog newInstance(final String message, final int tabPos, final int dialogId) {
        final WiGLEConfirmationDialog frag = new WiGLEConfirmationDialog();
        Bundle args = new Bundle();
        args.putString("message", message);
        args.putInt("tabPos", tabPos);
        args.putInt("dialogId", dialogId);
        frag.setArguments(args);
        return frag;
    }

    /**
     * alternate instantiation with a prefs-back checkbox inline - String prefs only
     * @param message the message to display in the dialog
     * @param checkboxLabel the label for the checkbox in the dialog
     * @param tabPos the tab state
     * @param dialogId the dialog id
     * @return a ConfirmationDialog instance
     */
    public static WiGLEConfirmationDialog newInstance(final String message, final String checkboxLabel,
                                                              final String persistPrefKey,
                                                              final String persistPrefAgreeValue,
                                                              final String persistPrefDisagreeValue,
                                                              final int tabPos,
                                                              final int dialogId) {
        final WiGLEConfirmationDialog frag = new WiGLEConfirmationDialog();
        Bundle args = new Bundle();
        args.putString("message", message);
        args.putInt("tabPos", tabPos);
        args.putInt("dialogId", dialogId);
        args.putString("checkboxLabel", checkboxLabel);
        args.putString("persistPref", persistPrefKey);
        args.putString("persistPrefAgreeValue", persistPrefAgreeValue);
        args.putString("persistPrefDisagreeValue", persistPrefDisagreeValue);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        final String confirmString = getString(R.string.dialog_confirm);
        builder.setTitle(confirmString);
        Bundle arguments = getArguments();
        if (null != arguments) {
            final String checkboxLabel = arguments.getString("checkboxLabel");
            if (null != checkboxLabel) {
                View checkBoxView = View.inflate(activity, R.layout.checkbox, null);
                CheckBox checkBox = checkBoxView.findViewById(R.id.checkbox);
                checkBox.setText(checkboxLabel);
                builder.setView(checkBoxView);
            }
        }
        final String persistPrefKey = getArguments().getString("persistPref");
        final String persistPrefAgreeValue = getArguments().getString("persistPrefAgreeValue");
        final String persistPrefDisagreeValue = getArguments().getString("persistPrefDisagreeValue");

        builder.setMessage(getArguments().getString("message"));
        final int tabPos = getArguments().getInt("tabPos");
        final int dialogId = getArguments().getInt("dialogId");
        final SharedPreferences prefs = null !=
                activity?activity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE): null;

        final AlertDialog ad = builder.create();
        // ok
        final String okString = null != activity?activity.getString(R.string.ok):"OK";
        ad.setButton(DialogInterface.BUTTON_POSITIVE, okString, (dialog, which) -> {
            try {
                if (null != persistPrefKey) {
                    CheckBox checkBox = ((AlertDialog) dialog).findViewById(R.id.checkbox);
                    if (checkBox.isChecked() && prefs != null) {
                        final SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(persistPrefKey, persistPrefAgreeValue);
                        editor.apply();
                    }
                }
                if (null != dialog) {
                    dialog.dismiss();
                }
                final Activity activity1 = getActivity();
                if (activity1 == null) {
                    Logging.info("activity is null in dialog. tabPos: " + tabPos + " dialogId: " + dialogId);
                } else if (activity1 instanceof MainActivity) {
                    final MainActivity mainActivity = (MainActivity) activity1;
                    if (mainActivity.getState() != null && tabPos != 0) {
                        final String maybeName = getResources().getResourceName(tabPos);
                        //DEBUG: MainActivity.info("Attempting lookup for: " + String.format("0x%08X", tabPos) + " (" + maybeName + ")");
                        FragmentManager fragmentManager = ((MainActivity) activity1).getSupportFragmentManager();
                        final Fragment fragment = fragmentManager.findFragmentByTag(MainActivity.FRAGMENT_TAG_PREFIX + tabPos);
                        if (fragment == null) {
                            Logging.error("null fragment for: " + String.format("0x%08X", tabPos) + " (" + maybeName + ")");
                            //ALIBI: how would we show an error here with a null fragment?
                        } else {
                            ((DialogListener) fragment).handleDialog(dialogId);
                        }
                    }
                } else {
                    ((DialogListener) activity1).handleDialog(dialogId);
                }
            } catch (Exception ex) {
                // guess it wasn't there anyways
                Logging.info("exception handling fragment alert dialog: " + ex, ex);
            }
        });

        // cancel
        final String cancelString = null != activity?activity.getString(R.string.cancel):"Cancel";
        ad.setButton(DialogInterface.BUTTON_NEGATIVE, cancelString, (dialog, which) -> {
            try {
                if (null != persistPrefKey) {
                    CheckBox checkBox = ((AlertDialog) dialog).findViewById(R.id.checkbox);
                    if (checkBox.isChecked() && prefs != null) {
                        final SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(persistPrefKey, persistPrefDisagreeValue);
                        editor.apply();
                    }
                }
                if (null != dialog) {
                    dialog.dismiss();
                }
            } catch (Exception ex) {
                // guess it wasn't there anyways
                Logging.info("exception dismissing fragment alert dialog: ", ex);
            }
        });
        return ad;
    }

    public static void createConfirmation(final FragmentActivity activity, final String message,
                                          final int tabPos, final int dialogId) {
        try {
            final FragmentManager fm = activity.getSupportFragmentManager();
            final WiGLEConfirmationDialog dialog = WiGLEConfirmationDialog.newInstance(message, tabPos, dialogId);
            final String tag = tabPos + "-" + dialogId + "-" + activity.getClass().getSimpleName();
            Logging.info("tag: " + tag + " fm: " + fm);
            dialog.show(fm, tag);
        } catch (WindowManager.BadTokenException ex) {
            Logging.info("exception showing dialog, view probably changed: " + ex, ex);
        } catch (final IllegalStateException ex) {
            final String errorMessage = "Exception trying to show dialog: " + ex;
            Logging.error(errorMessage, ex);
        }
    }

    public static void createCheckboxConfirmation(final FragmentActivity activity, final String message,
                                                  final String checkboxLabel, final String persistPrefKey,
                                                  final String persistPrefAgreeValue,
                                                  final String persistPrefDisagreeValue,
                                                  final int tabPos, final int dialogId) {
        try {
            final FragmentManager fm = activity.getSupportFragmentManager();
            final WiGLEConfirmationDialog dialog = WiGLEConfirmationDialog.newInstance(message, checkboxLabel,
                    persistPrefKey, persistPrefAgreeValue, persistPrefDisagreeValue, tabPos, dialogId);
            final String tag = tabPos + "-" + dialogId + "-" + activity.getClass().getSimpleName();
            Logging.info("tag: " + tag + " fm: " + fm);
            dialog.show(fm, tag);
        } catch (WindowManager.BadTokenException ex) {
            Logging.info("exception showing dialog, view probably changed: " + ex, ex);
        } catch (final IllegalStateException ex) {
            final String errorMessage = "Exception trying to show dialog: " + ex;
            Logging.error(errorMessage, ex);
        }
    }
}
