package com.powergrid.forecasting.enums;

public enum TerrainType {
    COASTAL("Coastal"),
    HILLY("Hilly"),
    PLAIN("Plain");

    private final String displayName;

    TerrainType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TerrainType fromDisplayName(String value) {
        for (TerrainType type : values()) {
            if (type.displayName.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid terrain type: " + value);
    }
}
