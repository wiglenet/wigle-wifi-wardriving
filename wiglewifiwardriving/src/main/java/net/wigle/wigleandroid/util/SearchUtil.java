package net.wigle.wigleandroid.util;

import android.content.Context;
import android.location.Address;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;

import androidx.annotation.NonNull;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.NetworkFilterType;
import net.wigle.wigleandroid.model.QueryArgs;
import net.wigle.wigleandroid.model.WiFiSecurityType;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import br.com.sapereaude.maskedEditText.MaskedEditText;

/**
 * Utilities for composing search QueryArgs from inputs
 * @author bobzilla, arkasha
 */
public class SearchUtil {

    private SearchUtil() {}

    public static void clearSearchFields(final View view) {
        IntStream.of(R.id.query_address, R.id.query_ssid, R.id.query_bssid, R.id.query_cell_op, R.id.query_cell_net, R.id.query_cell_id).mapToObj(
                id -> (EditText) view.findViewById(id)).forEach(editText -> editText.setText(""));
        final RadioButton local = view.findViewById(R.id.radio_search_local);
        if (null != local) {
            local.setChecked(true);
        }
        final Spinner networkTypeSpinner = view.findViewById(R.id.type_spinner);
        final Spinner wifiEncryptionSpinner = view.findViewById(R.id.encryption_spinner);
        networkTypeSpinner.setSelection(0);
        wifiEncryptionSpinner.setSelection(0);
        ListFragment.lameStatic.queryArgs = new QueryArgs();
    }

    public static void clearCellId(final View view) {
        EditText cellOp = view.findViewById(R.id.query_cell_op);
        cellOp.setText("");
        EditText cellNet = view.findViewById(R.id.query_cell_net);
        cellNet.setText("");
        EditText cellId = view.findViewById(R.id.query_cell_id);
        cellId.setText("");
    }

    public static void setupQuery(final View view, final Context context, final boolean local,
                                  @NonNull final Consumer<String> resultCallback) {
        final QueryArgs queryArgs = new QueryArgs();

        try {
            final Spinner networkTypeSpinner = view.findViewById(R.id.type_spinner);
            final Spinner wifiEncryptionSpinner = view.findViewById(R.id.encryption_spinner);

            if (null != networkTypeSpinner) {
                queryArgs.setType((NetworkFilterType) networkTypeSpinner.getSelectedItem());
                if (null != wifiEncryptionSpinner && (NetworkFilterType.ALL.equals(networkTypeSpinner.getSelectedItem()) || NetworkFilterType.WIFI.equals(networkTypeSpinner.getSelectedItem()))) {
                    queryArgs.setCrypto((WiFiSecurityType) wifiEncryptionSpinner.getSelectedItem());
                } else {
                    queryArgs.setCrypto(null);
                }
            }
        } catch (Exception e) {
            Logging.error("Problem with type/encryption selection: ", e);
        }

        final EditText addressEdit = view.findViewById(R.id.query_address);
        final String addressText = (addressEdit == null) ? "" : addressEdit.getText().toString().trim();

        if (addressText.isEmpty()) {
            // ALIBI: only applies for the search UI, NOT for the database tab.
            // These aren't directly editable, so we have to persist them into the new
            // queryArgs if set via the UI.
            if (null != ListFragment.lameStatic.queryArgs && null != ListFragment.lameStatic.queryArgs.getLocationBounds()) {
                queryArgs.setLocationBounds(ListFragment.lameStatic.queryArgs.getLocationBounds());
            }
            finishSetupQuery(view, context, queryArgs, local, /*addressOk*/ false, resultCallback);
            return;
        }

        // Address resolution can hit the network/disk - run off the main thread.
        GeocodingUtil.getFromLocationName(context, addressText, 1, new GeocodingUtil.GeocodeCallback() {
            @Override
            public void onResult(@NonNull List<Address> addresses) {
                if (addresses.isEmpty()) {
                    resultCallback.accept(context.getString(R.string.no_address_found));
                    return;
                }
                queryArgs.setAddress(addresses.get(0));
                finishSetupQuery(view, context, queryArgs, local, /*addressOk*/ true, resultCallback);
            }

            @Override
            public void onError(@NonNull Exception e) {
                resultCallback.accept(context.getString(R.string.problem_with_field)
                        + " '" + context.getString(R.string.address) + "': " + e.getMessage());
            }
        });
    }

    /**
     * Validate the non-address fields (SSID / BSSID / cell op-net-id) and commit the result.
     * Runs synchronously - none of these touch the network or disk.
     */
    private static void finishSetupQuery(final View view, final Context context,
                                         final QueryArgs queryArgs, final boolean local,
                                         final boolean addressOk,
                                         @NonNull final Consumer<String> resultCallback) {
        String fail = null;
        String errorField = null;
        boolean okValue = addressOk;
        final boolean isCellSearch = (null != queryArgs.getType()) && NetworkFilterType.CELL.equals(queryArgs.getType());

        for (final int id : new int[]{R.id.query_ssid, R.id.query_bssid, R.id.query_cell_op, R.id.query_cell_net, R.id.query_cell_id}) {
            if (fail != null) {
                break;
            }

            EditText editText = (EditText) view.findViewById(id);
            String text = editText.getText().toString().trim();
            if (id == R.id.query_bssid) {
                //ALIBI: long workaround because getText on empty field returns hint text
                final String intermediateText = ((MaskedEditText) editText).getRawText();
                if (null != intermediateText && !intermediateText.isEmpty()) {
                    text = intermediateText.replaceAll("(..)(?!$)", "$1:");
                } else {
                    text = "";
                }
            }
            if (text.isEmpty()) {
                continue;
            }

            try {
                if (id == R.id.query_ssid) {
                    errorField = context.getString(R.string.ssid);
                    //TODO: validation of SSID
                    queryArgs.setSSID(text);
                    okValue = true;
                } else if (id == R.id.query_cell_op) {
                    if (isCellSearch) {
                        queryArgs.setCellOp(text);
                        //TODO: validation
                        okValue = true;
                    }
                } else if (id == R.id.query_cell_net) {
                    if (isCellSearch) {
                        queryArgs.setCellNet(text);
                        //TODO: validation
                        okValue = true;
                    }
                } else if (id == R.id.query_cell_id) {
                    if (isCellSearch) {
                        queryArgs.setCellId(text);
                        //TODO: validation
                        okValue = true;
                    }
                } else if (id == R.id.query_bssid) {
                    errorField = context.getString(R.string.bssid);
                    //ALIBI: bssid text only applies for BT/WiFi
                    if (!isCellSearch) {
                        queryArgs.setBSSID(text);
                        if (local) {
                            if (text.length() > 17 || (text.length() < 17 && !text.contains("%"))) {
                                okValue = false;
                                fail = context.getString(R.string.error_invalid_bssid);
                            } else {
                                //DEBUG: Logging.info("text: "+text);
                                okValue = true;
                            }
                        } else {
                            if (text.contains("%") || text.contains("_")) {
                                //ALIBI: hack, since online BSSIDs don't allow wildcards
                                String[] splitBssid = queryArgs.getBSSID().split("%|_", 2);
                                text = splitBssid[0];
                            }

                            if (((text.length() == 9) || (text.length() == 12) || (text.length() == 15))
                                    && (text.charAt(text.length() - 1) == ':')) {
                                //remove trailing ':'s
                                queryArgs.setBSSID(text.substring(0, text.length() - 1));
                                //DEBUG: Logging.info("text: "+text);
                                okValue = true;
                            } else if (text.length() < 8) {
                                okValue = false;
                                fail = context.getString(R.string.error_less_than_oui);
                            } else if (text.length() == 17) {
                                //DEBUG: Logging.info("text: "+text);
                                okValue = true;
                            } else {
                                okValue = false;
                                fail = context.getString(R.string.error_incomplete_octet);
                            }
                        }
                    }
                } else {
                    Logging.error("setupQuery: bad id: " + id);
                }
            } catch (Exception ex) {
                fail = context.getString(R.string.problem_with_field) + " '" + errorField + "': " + ex.getMessage(); //TODO: not language aware 1/2 - replace w/ templated message
                break;
            }
        }

        try {
            if (fail == null && !okValue) {
                fail = "No query fields specified";
            }
            if (null == fail) {
                ListFragment.lameStatic.queryArgs = queryArgs;
            }
        } catch (Exception e) {
            fail = context.getString(R.string.problem_with_field) + " '" + errorField + "': " + e.getMessage(); //TODO: not language aware 2/2 - replace w/ templated message
        }
        resultCallback.accept(fail);
    }
}
