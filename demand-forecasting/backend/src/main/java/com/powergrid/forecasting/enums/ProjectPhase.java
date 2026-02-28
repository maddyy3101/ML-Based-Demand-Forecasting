package com.powergrid.forecasting.enums;

public enum ProjectPhase {
    PLANNING("Planning"),
    EXECUTION("Execution"),
    COMMISSIONING("Commissioning");

    private final String displayName;

    ProjectPhase(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ProjectPhase fromDisplayName(String value) {
        for (ProjectPhase phase : values()) {
            if (phase.displayName.equalsIgnoreCase(value) || phase.name().equalsIgnoreCase(value)) {
                return phase;
            }
        }
        throw new IllegalArgumentException("Invalid project phase: " + value);
    }
}
