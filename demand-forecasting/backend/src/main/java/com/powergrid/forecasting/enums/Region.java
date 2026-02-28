package com.powergrid.forecasting.enums;

public enum Region {
    NORTH("North", "Uttar Pradesh,Rajasthan,Bihar", "NORTH"),
    SOUTH("South", "Karnataka,Tamil Nadu,Telangana", "SOUTH"),
    EAST("East", "West Bengal,Odisha", "EAST"),
    WEST("West", "Maharashtra,Gujarat", "WEST");

    private final String displayName;
    private final String states;
    private final String code;

    Region(String displayName, String states, String code) {
        this.displayName = displayName;
        this.states = states;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStates() {
        return states;
    }

    public String getCode() {
        return code;
    }

    public static Region fromDisplayName(String value) {
        for (Region region : values()) {
            if (region.displayName.equalsIgnoreCase(value) || region.name().equalsIgnoreCase(value)) {
                return region;
            }
        }
        throw new IllegalArgumentException("Invalid region: " + value);
    }
}
