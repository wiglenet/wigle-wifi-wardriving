package net.wigle.wigleandroid.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Individual record for a unique MCC/MNC pair
 * MCC/MNC record from https://github.com/pbakondy/mcc-mnc-list/
 *
 * @author arkasha
 *
 */

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
//TODO: DRY up with server-side?
public class MccMncRecord {
    /**
     * example:
     * {
     * "type": "National",
     * "countryName": "United Kingdom",
     * "countryCode": "GB",
     * "mcc": "234",
     * "mnc": "37",
     * "brand": null,
     * "operator": "Synectiv Ltd",
     * "status": "Unknown",
     * "bands": "Unknown",
     * "notes": null
     * },
     **/
    private String type;
    private String countryName;
    private String countryCode;
    private String mcc;
    private String mnc;
    private String brand;
    private String operator;
    private String status;
    private String bands;
    private String notes;

    public MccMncRecord() {
    }

    public MccMncRecord(String type, String countryName, String countryCode, String mcc, String mnc, String brand,
                        String operator, String status, String bands, String notes) {
        super();
        this.type = type;
        this.countryName = countryName;
        this.countryCode = countryCode;
        this.mcc = mcc;
        this.mnc = mnc;
        this.brand = brand;
        this.operator = operator;
        this.status = status;
        this.bands = bands;
        this.notes = notes;
    }

    public String getType() {
        return type;
    }

    public String getCountryName() {
        return countryName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getMcc() {
        return mcc;
    }

    public String getMnc() {
        return mnc;
    }

    public String getBrand() {
        return brand;
    }

    public String getOperator() {
        return operator;
    }

    public String getStatus() {
        return status;
    }

    public String getBands() {
        return bands;
    }

    public String getNotes() {
        return notes;
    }

}
