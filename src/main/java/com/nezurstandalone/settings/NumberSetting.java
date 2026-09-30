package com.nezurstandalone.settings;

public class NumberSetting extends Setting {
    public double value, min, max;
    public int decimalPlaces;
    
    public NumberSetting(String name, double defaultValue, double min, double max, int decimalPlaces) {
        super(name);
        validateBounds(min, max, decimalPlaces);
        this.value = normalize(defaultValue, min, max, decimalPlaces);
        this.min = min;
        this.max = max;
        this.decimalPlaces = decimalPlaces;
    }

    public double getValue() {
        return value;
    }
    
    public void setValue(double value) {
        double next = normalize(value, min, max, decimalPlaces);
        if (next != this.value) {
            this.value = next;
            notifyChange();
            com.nezurstandalone.utils.ConfigSaveDebouncer.markDirty();
        }
    }
    static void validateBounds(double min, double max, int precision) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max
                || precision < 0 || precision > 12) {
            throw new IllegalArgumentException("Invalid numeric setting bounds or precision");
        }
    }

    static double normalize(double value, double min, double max, int precision) {
        validateBounds(min, max, precision);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite setting value");
        double clamped = Math.max(min, Math.min(max, value));
        // BigDecimal avoids overflow/saturation in Math.round(value * factor).
        double rounded = java.math.BigDecimal.valueOf(clamped)
                .setScale(precision, java.math.RoundingMode.HALF_UP).doubleValue();
        return Math.max(min, Math.min(max, rounded));
    }
}

