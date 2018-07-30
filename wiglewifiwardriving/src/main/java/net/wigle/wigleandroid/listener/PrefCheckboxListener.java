package net.wigle.wigleandroid.listener;

/**
 * Mathod interface - call a method when you tick/untick a PrefBackedCheckbox
 * @author rksh
 */
public interface PrefCheckboxListener {

    /**
     * Checkbox ticked/unticked and saved. This is called every time the checkedbox is changed.
     * @param value
     */
    void preferenceSet(final boolean value);
}
