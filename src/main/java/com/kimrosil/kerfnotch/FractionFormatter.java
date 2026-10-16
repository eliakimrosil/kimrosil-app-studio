package com.kimrosil.kerfnotch;

import java.util.Locale;

public class FractionFormatter {

    public static String format(double inches, int unitMode) {
        if (unitMode == 2) {
            // Metric Millimeters
            return String.format(Locale.US, "%.2f mm", inches * 25.4);
        } else if (unitMode == 1) {
            // Pure Decimal Inches
            return String.format(Locale.US, "%.4f\"", inches);
        } else {
            // Workshop Fractional Inch (nearest 1/64")
            return toFractionString(inches);
        }
    }

    public static String formatSecondary(double inches, int unitMode) {
        if (unitMode == 2) {
            return String.format(Locale.US, "%.4f\" (%s)", inches, toFractionString(inches));
        } else if (unitMode == 1) {
            return String.format(Locale.US, "%.2f mm (%s)", inches * 25.4, toFractionString(inches));
        } else {
            return String.format(Locale.US, "%.4f\" (%.2f mm)", inches, inches * 25.4);
        }
    }

    private static String toFractionString(double inches) {
        if (inches <= 0) return "0\"";

        int whole = (int) inches;
        double remainder = inches - whole;

        int denominator = 64;
        int numerator = (int) Math.round(remainder * denominator);

        if (numerator == denominator) {
            whole += 1;
            numerator = 0;
        }

        if (numerator == 0) {
            return whole + "\"";
        }

        // Simplify fraction
        while (numerator % 2 == 0 && denominator % 2 == 0 && denominator > 1) {
            numerator /= 2;
            denominator /= 2;
        }

        if (whole > 0) {
            return whole + " " + numerator + "/" + denominator + "\"";
        } else {
            return numerator + "/" + denominator + "\"";
        }
    }
}