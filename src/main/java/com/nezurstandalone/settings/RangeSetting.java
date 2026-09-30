package com.nezurstandalone.settings;

import java.util.Random;

/**
 * Dual-bound range setting with Gaussian bell-curve randomization support.
 */
public class RangeSetting extends Setting {
    public double minVal;
    public double maxVal;
    public double minBound;
    public double maxBound;
    public int decimalPlaces;
    private boolean initialized;
    private static final Random RANDOM = new Random();

    public RangeSetting(String name, double defaultMin, double defaultMax, double minBound, double maxBound, int decimalPlaces) {
        super(name);
        NumberSetting.validateBounds(minBound, maxBound, decimalPlaces);
        this.minBound = minBound;
        this.maxBound = maxBound;
        this.decimalPlaces = decimalPlaces;
        setRange(defaultMin, defaultMax);
        initialized = true;
    }

    public void setMinVal(double val) {
        double rounded = round(val);
        double clamped = Math.max(minBound, Math.min(maxVal, rounded));
        if (clamped != this.minVal) {
            this.minVal = clamped;
            changed();
        }
    }

    public void setMaxVal(double val) {
        double rounded = round(val);
        double clamped = Math.max(minVal, Math.min(maxBound, rounded));
        if (clamped != this.maxVal) {
            this.maxVal = clamped;
            changed();
        }
    }

    /** Range drags have to reach the config like every other setting - the old setters wrote
     *  the field silently and slider edits were lost unless something else saved later. */
    private void changed() {
        notifyChange();
        com.nezurstandalone.utils.ConfigSaveDebouncer.markDirty();
    }

    public void setRange(double min, double max) {
        double rMin = Math.max(minBound, Math.min(maxBound, round(min)));
        double rMax = Math.max(minBound, Math.min(maxBound, round(max)));
        double nextMin = Math.min(rMin, rMax), nextMax = Math.max(rMin, rMax);
        boolean different = nextMin != minVal || nextMax != maxVal;
        minVal = nextMin;
        maxVal = nextMax;
        if (initialized && different) changed();
    }

    private double round(double val) {
        return NumberSetting.normalize(val, minBound, maxBound, decimalPlaces);
    }

    public double getRandomGaussian() {
        return getGaussian(minVal, maxVal);
    }

    /**
     * Gaussian / Normal Randomization bounded strictly between min and max.
     * Peak centered around mean = (min + max) / 2
     * Standard Deviation = (max - min) / 6 (3-sigma rule covers 99.7% of bell curve)
     */
    public static double getGaussian(double min, double max) {
        if (min >= max) return min;
        double mean = (min + max) / 2.0;
        double stdDev = (max - min) / 6.0;
        double val = mean + RANDOM.nextGaussian() * stdDev;
        return Math.max(min, Math.min(max, val));
    }
}
