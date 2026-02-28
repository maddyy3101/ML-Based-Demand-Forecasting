package com.powergrid.forecasting.enums;

public enum MaterialType {
    CEMENT("Cement", "Bags", 223, 23876, "CEM"),
    CONDUCTOR("Conductor", "Metres (coils)", 59, 6419, "COND"),
    INSULATOR("Insulator", "Units", 45, 4586, "INS"),
    STEEL("Steel", "MT", 112, 12905, "STL"),
    TRANSFORMER("Transformer", "Units", 1, 158, "TRANS");

    private final String displayName;
    private final String unitLabel;
    private final int typicalMin;
    private final int typicalMax;
    private final String code;

    MaterialType(String displayName, String unitLabel, int typicalMin, int typicalMax, String code) {
        this.displayName = displayName;
        this.unitLabel = unitLabel;
        this.typicalMin = typicalMin;
        this.typicalMax = typicalMax;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUnitLabel() {
        return unitLabel;
    }

    public int getTypicalMin() {
        return typicalMin;
    }

    public int getTypicalMax() {
        return typicalMax;
    }

    public String getCode() {
        return code;
    }

    public static MaterialType fromDisplayName(String value) {
        for (MaterialType type : values()) {
            if (type.displayName.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid material type: " + value);
    }
}
