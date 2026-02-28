package com.powergrid.forecasting.enums;

public enum TowerType {
    KV_220("220kV"),
    KV_400("400kV"),
    KV_765("765kV");

    private final String displayName;

    TowerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TowerType fromDisplayName(String value) {
        for (TowerType type : values()) {
            if (type.displayName.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid tower type: " + value);
    }
}
