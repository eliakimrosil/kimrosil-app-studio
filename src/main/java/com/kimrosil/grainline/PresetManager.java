package com.kimrosil.grainline;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages user preferences, unit systems (Inches vs Centimeters, Yards vs Meters),
 * quick fabric width presets, and pro tier simulation.
 */
public class PresetManager {

    private static final String PREF_NAME = "grainline_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_UNIT_SYSTEM = "pref_unit_system"; // 0=Imperial (Inches/Yds), 1=Metric (cm/Meters)
    private static final String KEY_PRO_USER = "is_pro_user";
    private static final String KEY_LAST_BOLT_WIDTH = "pref_last_bolt_width";
    private static final String KEY_LAST_SHRINKAGE = "pref_last_shrinkage";
    private static final String KEY_LAST_SEAM = "pref_last_seam";

    private final SharedPreferences mPrefs;

    public PresetManager(Context context) {
        mPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getThemeMode() {
        return mPrefs.getInt(KEY_THEME_MODE, 0);
    }

    public void setThemeMode(int mode) {
        mPrefs.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public boolean isMetric() {
        return mPrefs.getInt(KEY_UNIT_SYSTEM, 0) == 1;
    }

    public void setMetric(boolean isMetric) {
        mPrefs.edit().putInt(KEY_UNIT_SYSTEM, isMetric ? 1 : 0).apply();
    }

    public boolean isProUser() {
        return mPrefs.getBoolean(KEY_PRO_USER, false);
    }

    public void setProUser(boolean isPro) {
        mPrefs.edit().putBoolean(KEY_PRO_USER, isPro).apply();
    }

    public double getLastBoltWidth(double fallback) {
        return Double.longBitsToDouble(mPrefs.getLong(KEY_LAST_BOLT_WIDTH, Double.doubleToLongBits(fallback)));
    }

    public void saveLastBoltWidth(double width) {
        mPrefs.edit().putLong(KEY_LAST_BOLT_WIDTH, Double.doubleToLongBits(width)).apply();
    }

    public double getLastShrinkage(double fallback) {
        return Double.longBitsToDouble(mPrefs.getLong(KEY_LAST_SHRINKAGE, Double.doubleToLongBits(fallback)));
    }

    public void saveLastShrinkage(double shrinkage) {
        mPrefs.edit().putLong(KEY_LAST_SHRINKAGE, Double.doubleToLongBits(shrinkage)).apply();
    }

    public double getLastSeam(double fallback) {
        return Double.longBitsToDouble(mPrefs.getLong(KEY_LAST_SEAM, Double.doubleToLongBits(fallback)));
    }

    public void saveLastSeam(double seam) {
        mPrefs.edit().putLong(KEY_LAST_SEAM, Double.doubleToLongBits(seam)).apply();
    }
}