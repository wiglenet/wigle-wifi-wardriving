package net.wigle.wigleandroid.model.api;

import com.google.gson.annotations.SerializedName;

public class SiteStats {
    private long netloc;
    private long loctotal;
    private long btloc;
    private  long genloc;
    private long userstot;
    private long transtot;
    private long netwpa3;
    private long netwpa2;
    private long netwpa;
    private long netwep;
    private long netnowep;
    @SerializedName("netwep?")
    private long netwepunknown;

    public long getNetloc() {
        return netloc;
    }

    public void setNetloc(long netloc) {
        this.netloc = netloc;
    }

    public long getLoctotal() {
        return loctotal;
    }

    public void setLoctotal(long loctotal) {
        this.loctotal = loctotal;
    }

    public long getBtloc() {
        return btloc;
    }

    public void setBtloc(long btloc) {
        this.btloc = btloc;
    }

    public long getGenloc() {
        return genloc;
    }

    public void setGenloc(long genloc) {
        this.genloc = genloc;
    }

    public long getUserstot() {
        return userstot;
    }

    public void setUserstot(long userstot) {
        this.userstot = userstot;
    }

    public long getTranstot() {
        return transtot;
    }

    public void setTranstot(long transtot) {
        this.transtot = transtot;
    }

    public long getNetwpa3() {
        return netwpa3;
    }

    public void setNetwpa3(long netwpa3) {
        this.netwpa3 = netwpa3;
    }

    public long getNetwpa2() {
        return netwpa2;
    }

    public void setNetwpa2(long netwpa2) {
        this.netwpa2 = netwpa2;
    }

    public long getNetwpa() {
        return netwpa;
    }

    public void setNetwpa(long netwpa) {
        this.netwpa = netwpa;
    }

    public long getNetwep() {
        return netwep;
    }

    public void setNetwep(long netwep) {
        this.netwep = netwep;
    }

    public long getNetnowep() {
        return netnowep;
    }

    public void setNetnowep(long netnowep) {
        this.netnowep = netnowep;
    }

    public long getNetwepunknown() {
        return netwepunknown;
    }

    public void setNetwepunknown(long netwepunknown) {
        this.netwepunknown = netwepunknown;
    }
}
