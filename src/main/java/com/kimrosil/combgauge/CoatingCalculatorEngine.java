package com.kimrosil.combgauge;

import java.util.Locale;

public final class CoatingCalculatorEngine {

    public static final int UNIT_MICRONS = 0;
    public static final int UNIT_MILS = 1;

    public static final int PROFILE_SMOOTH = 0;
    public static final int PROFILE_FINE_25 = 1;
    public static final int PROFILE_MEDIUM_50 = 2;
    public static final int PROFILE_COARSE_75 = 3;

    public static final int MODE_DFT_TO_WFT = 0;
    public static final int MODE_WFT_TO_DFT = 1;

    public static final double MICRONS_PER_MIL = 25.4;

    private CoatingCalculatorEngine() {}

    public static double getDeadVolumeDepth(int profileType, int unit) {
        double microns = 0.0;
        switch (profileType) {
            case PROFILE_FINE_25:
                microns = 25.0;
                break;
            case PROFILE_MEDIUM_50:
                microns = 50.0;
                break;
            case PROFILE_COARSE_75:
                microns = 75.0;
                break;
            case PROFILE_SMOOTH:
            default:
                microns = 0.0;
                break;
        }

        if (unit == UNIT_MILS) {
            return microns / MICRONS_PER_MIL;
        }
        return microns;
    }

    public static double getEffectiveDeadVolumeLoss(int profileType, int unit) {
        // Dead volume factor is typically ~50% of the peak-to-valley anchor profile
        return getDeadVolumeDepth(profileType, unit) * 0.50;
    }

    public static double getEffectiveSolidsPercent(double volumeSolidsPercent, double thinnerPercent) {
        double solids = Math.max(1.0, Math.min(100.0, volumeSolidsPercent));
        double thinner = Math.max(0.0, Math.min(50.0, thinnerPercent));
        return (solids * 100.0) / (100.0 + thinner);
    }

    public static double calculateRequiredWft(double targetDft, double volumeSolidsPercent,
                                              double thinnerPercent, int profileType, int unit) {
        double effectiveSolidsRatio = getEffectiveSolidsPercent(volumeSolidsPercent, thinnerPercent) / 100.0;
        if (effectiveSolidsRatio <= 0.0) {
            effectiveSolidsRatio = 1.0;
        }
        double filmWft = targetDft / effectiveSolidsRatio;
        double deadVolumeLoss = getEffectiveDeadVolumeLoss(profileType, unit);
        return filmWft + deadVolumeLoss;
    }

    public static double calculateResultingDft(double measuredWft, double volumeSolidsPercent,
                                               double thinnerPercent, int profileType, int unit) {
        double effectiveSolidsRatio = getEffectiveSolidsPercent(volumeSolidsPercent, thinnerPercent) / 100.0;
        double deadVolumeLoss = getEffectiveDeadVolumeLoss(profileType, unit);
        double netWft = Math.max(0.0, measuredWft - deadVolumeLoss);
        return netWft * effectiveSolidsRatio;
    }

    public static double calculateTheoreticalSpreadingRate(double dft, double volumeSolidsPercent, int unit) {
        double clampedSolids = Math.max(1.0, Math.min(100.0, volumeSolidsPercent));
        if (dft <= 0.0001) {
            return 0.0;
        }

        if (unit == UNIT_MICRONS) {
            // Spreading rate (m²/L) = (% Volume Solids * 10) / DFT (µm)
            return (clampedSolids * 10.0) / dft;
        } else {
            // Spreading rate (sq ft/gal) = (% Volume Solids * 1604) / DFT (mils)
            return (clampedSolids * 16.04) / dft;
        }
    }

    public static double getShrinkagePercent(double volumeSolidsPercent, double thinnerPercent) {
        double effectiveSolids = getEffectiveSolidsPercent(volumeSolidsPercent, thinnerPercent);
        return Math.max(0.0, 100.0 - effectiveSolids);
    }

    public static String formatThickness(double value, int unit) {
        if (unit == UNIT_MICRONS) {
            return String.format(Locale.US, "%.0f", Math.max(0.0, value));
        } else {
            return String.format(Locale.US, "%.1f", Math.max(0.0, value));
        }
    }

    public static String getUnitLabel(int unit) {
        return unit == UNIT_MICRONS ? "µm" : "mils";
    }

    public static String getSpreadingRateUnit(int unit) {
        return unit == UNIT_MICRONS ? "m²/L" : "sq ft/gal";
    }
}