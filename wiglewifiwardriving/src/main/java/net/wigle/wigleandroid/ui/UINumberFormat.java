package net.wigle.wigleandroid.ui;

public class UINumberFormat {
    /**
     * format the topbar counters
     * @param input
     * @return
     */
    public static String counterFormat(long input) {
        if (input > 9999999999L) {
            //any android device on the market today would explode
            return (input / 1000000000L) + "G";
        } else if (input >  9999999L) {
            return (input / 1000000L) + "M";
        } else if (input > 9999L) {
            //stay specific until we pass 5 digits
            return (input / 1000L) + "K";
        } else {
            return input+"";
        }
    }

}
