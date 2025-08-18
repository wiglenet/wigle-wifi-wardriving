package net.wigle.wigleandroid.ui;

import net.wigle.wigleandroid.ListFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MAC/OUI OCR result view class
 * @author rksh
 */
public class MacFinderListView {
    private String name;
    private boolean isChecked;
    private String mfgrInfo;

    public MacFinderListView(String name, boolean isChecked, String mfgrInfo) {
        this.name = name;
        this.isChecked = isChecked;
        this.mfgrInfo = mfgrInfo;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setChecked(boolean checked) {
        isChecked = checked;
    }

    public String getName() {
        return name;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public String getMfgrInfo() {
        return mfgrInfo;
    }

    public static List<MacFinderListView> listForAddressesAndKnown(final Set<String> addresses, final List<String> configured) {
        final List<MacFinderListView> result = new ArrayList<>();
        for (String address: addresses) {
            String mfgrInfo;
            if (ListFragment.lameStatic.oui != null && address.length() >= 6) {
                mfgrInfo = ListFragment.lameStatic.oui.getOui(address.replace(":", "").substring(0, 6));
            } else {
                mfgrInfo = "";
            }
            result.add(new MacFinderListView(address, configured.contains(address), mfgrInfo));
            //DEBUG: Logging.info("ADDED: " + address+" ("+configured.contains(address) +"): " + mfgrInfo);
        }
        return result;
    }
}
