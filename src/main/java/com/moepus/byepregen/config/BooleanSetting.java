package com.moepus.byepregen.config;

import java.util.Objects;

public enum BooleanSetting {
    DEFAULT("Default"),
    TRUE("True"),
    FALSE("False");

    private final String serializedValue;

    BooleanSetting(String serializedValue) {
        this.serializedValue = serializedValue;
    }

    public static BooleanSetting explicit(boolean value) {
        return value ? TRUE : FALSE;
    }

    static BooleanSetting parse(Object value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof Boolean booleanValue) {
            return explicit(booleanValue);
        }
        if (value instanceof String stringValue) {
            for (BooleanSetting setting : values()) {
                if (setting.serializedValue.equalsIgnoreCase(stringValue.trim())) {
                    return setting;
                }
            }
        }
        throw new IllegalArgumentException("Expected Default, True, or False");
    }

    public boolean resolve(boolean defaultValue) {
        return switch (this) {
            case DEFAULT -> defaultValue;
            case TRUE -> true;
            case FALSE -> false;
        };
    }

    public String serializedValue() {
        return this.serializedValue;
    }
}
