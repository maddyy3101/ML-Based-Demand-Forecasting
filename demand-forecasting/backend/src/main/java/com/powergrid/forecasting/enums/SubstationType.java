package com.powergrid.forecasting.enums;

public enum SubstationType {
    AIS("AIS", "Air Insulated Switchgear"),
    GIS("GIS", "Gas Insulated Switchgear");

    private final String code;
    private final String displayName;

    SubstationType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SubstationType fromCode(String value) {
        for (SubstationType type : values()) {
            if (type.code.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid substation type: " + value);
    }
}
