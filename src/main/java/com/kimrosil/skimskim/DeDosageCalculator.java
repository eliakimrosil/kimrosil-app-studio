package com.kimrosil.skimskim;

public class DeDosageCalculator {

    // Standard rule: 1 lb of Diatomaceous Earth per 10 sq ft of filter area
    // Standard 1 lb DE coffee can/scoop ~= 2 standard pool scoops (approx 0.5 lb each)
    public static final double DEFAULT_AREA_SQFT = 48.0;
    public static final double MIN_AREA_SQFT = 10.0;
    public static final double MAX_AREA_SQFT = 150.0;

    public static double clampArea(double area) {
        if (Double.isNaN(area) || area <= 0.0) {
            return DEFAULT_AREA_SQFT;
        }
        return Math.max(MIN_AREA_SQFT, Math.min(MAX_AREA_SQFT, area));
    }

    public static double calculateFullRechargeLbs(double areaSqFt) {
        double clamped = clampArea(areaSqFt);
        return clamped / 10.0;
    }

    public static double calculateBumpOrPartialLbs(double areaSqFt) {
        // Bump / partial backwash typically replenishes ~80% of total powder capacity
        return calculateFullRechargeLbs(areaSqFt) * 0.80;
    }

    public static double lbsToStandardScoops(double lbs) {
        // Standard 1 lb scoop
        return lbs;
    }

    public static double lbsTo1lbCoffeeCans(double lbs) {
        return lbs; // 1 lb DE equals approx 1 1-lb coffee can volume
    }
}