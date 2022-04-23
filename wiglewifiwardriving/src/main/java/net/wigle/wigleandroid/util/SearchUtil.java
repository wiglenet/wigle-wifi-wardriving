package net.wigle.wigleandroid.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.view.View;
import android.widget.EditText;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.QueryArgs;

import java.util.List;

import br.com.sapereaude.maskedEditText.MaskedEditText;

public class SearchUtil {

    private SearchUtil() {}

    public static void clearWiFiBtFields(final View view) {
        for (final int id : new int[]{R.id.query_address, R.id.query_ssid, R.id.query_bssid}) {
            final EditText editText = (EditText) view.findViewById(id);
            editText.setText("");
        }
        ListFragment.lameStatic.queryArgs = null;
    }

    public static String setupQuery(final View view, final Context context, final boolean local) {
        final QueryArgs queryArgs = new QueryArgs();
        String fail = null;
        String field = null;
        boolean okValue = false;

        for (final int id : new int[]{R.id.query_address, R.id.query_ssid, R.id.query_bssid}) {
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
                    //DEBUG: MainActivity.info("text: " + text);
                } else {
                    text = "";
                }
            }
            if (text.isEmpty()) {
                continue;
            }

            try {
                switch (id) {
                    case R.id.query_address:
                        field = context.getString(R.string.address);
                        Geocoder gc = new Geocoder(context);
                        List<Address> addresses = gc.getFromLocationName(text, 1);
                        if (addresses.size() < 1) {
                            fail = context.getString(R.string.no_address_found);
                            break;
                        }
                        queryArgs.setAddress(addresses.get(0));
                        okValue = true;
                        break;
                    case R.id.query_ssid:
                        field = context.getString(R.string.ssid);
                        //TODO: validation on SSID max length
                        queryArgs.setSSID(text);
                        okValue = true;
                        break;
                    case R.id.query_bssid:
                        field = context.getString(R.string.bssid);
                        queryArgs.setBSSID(text);
                        if (local) {
                            if (text.length() > 17 || (text.length() < 17 && !text.contains("%"))) {
                                okValue = false;
                                fail = context.getString(R.string.error_invalid_bssid);
                            } else {
                                //DEBUG:
                                Logging.info("text: "+text);
                                okValue = true;
                            }
                        } else {
                            if (text.contains("%") || text.contains("_")) {
                                //ALIBI: hack, since online BSSIDs don't allow wildcards
                                String splitBssid[] = queryArgs.getBSSID().split("%|_", 2);
                                text = splitBssid[0];
                            }

                            if (((text.length() == 9) || (text.length() == 12) || (text.length() == 15))
                                    && (text.charAt(text.length()-1) == ':')) {
                                //remove trailing ':'s
                                queryArgs.setBSSID(text.substring(0,text.length()-1));
                                Logging.info("text: "+text);
                                okValue = true;
                            } else if (text.length() < 8) {
                                okValue = false;
                                fail = context.getString(R.string.error_less_than_oui);
                            } else if (text.length() == 17) {
                                Logging.info("text: "+text);
                                okValue = true;
                            } else {
                                okValue = false;
                                fail = context.getString(R.string.error_incomplete_octet);
                            }

                        }
                        break;
                    default:
                        Logging.error("setupButtons: bad id: " + id);
                }
            } catch (Exception ex) {
                fail = context.getString(R.string.problem_with_field) + " '" + field + "': " + ex.getMessage();
                break;
            }
        }

        if (fail == null && !okValue) {
            fail = "No query fields specified";
        }

        if (null == fail) {
            ListFragment.lameStatic.queryArgs = queryArgs;
        }

        return fail;
    }
}
