package com.kimrosil.ohmband;

import java.util.Locale;

public class OhmEngine {

    // Color definitions
    public static final int COLOR_BLACK = 0;
    public static final int COLOR_BROWN = 1;
    public static final int COLOR_RED = 2;
    public static final int COLOR_ORANGE = 3;
    public static final int COLOR_YELLOW = 4;
    public static final int COLOR_GREEN = 5;
    public static final int COLOR_BLUE = 6;
    public static final int COLOR_VIOLET = 7;
    public static final int COLOR_GRAY = 8;
    public static final int COLOR_WHITE = 9;
    public static final int COLOR_GOLD = 10;
    public static final int COLOR_SILVER = 11;

    public static final String[] COLOR_NAMES = {
            "Black", "Brown", "Red", "Orange", "Yellow",
            "Green", "Blue", "Violet", "Gray", "White",
            "Gold", "Silver"
    };

    public static final int[] COLOR_HEX = {
            0xFF1E1E1E, // Black
            0xFF8B4513, // Brown
            0xFFEF4444, // Red
            0xFFF97316, // Orange
            0xFFFACC15, // Yellow
            0xFF10B981, // Green
            0xFF3B82F6, // Blue
            0xFFA855F7, // Violet
            0xFF9CA3AF, // Gray
            0xFFF3F4F6, // White
            0xFFD97706, // Gold
            0xFF94A3B8  // Silver
    };

    public static class ResistorResult {
        public double nominalOhms;
        public double minOhms;
        public double maxOhms;
        public double tolerancePercent;
        public int tcrPpm; // 0 if N/A
        public String formattedNominal;
        public String formattedMin;
        public String formattedMax;
        public String formattedTolerance;
        public String formattedTcr;
        public String eiaSeriesMatch;
    }

    public static class SmdResult {
        public String code;
        public double ohms;
        public double tolerancePercent;
        public String formattedOhms;
        public String type; // 3-Digit, 4-Digit, EIA-96
        public boolean isValid;
    }

    // Standard Multiplier factors
    public static double getMultiplier(int color) {
        switch (color) {
            case COLOR_SILVER: return 0.01;
            case COLOR_GOLD: return 0.1;
            case COLOR_BLACK: return 1.0;
            case COLOR_BROWN: return 10.0;
            case COLOR_RED: return 100.0;
            case COLOR_ORANGE: return 1000.0;
            case COLOR_YELLOW: return 10000.0;
            case COLOR_GREEN: return 100000.0;
            case COLOR_BLUE: return 1000000.0;
            case COLOR_VIOLET: return 10000000.0;
            case COLOR_GRAY: return 100000000.0;
            case COLOR_WHITE: return 1000000000.0;
            default: return 1.0;
        }
    }

    // Standard Tolerance percentage
    public static double getTolerance(int color) {
        switch (color) {
            case COLOR_BROWN: return 1.0;
            case COLOR_RED: return 2.0;
            case COLOR_GREEN: return 0.5;
            case COLOR_BLUE: return 0.25;
            case COLOR_VIOLET: return 0.1;
            case COLOR_GRAY: return 0.05;
            case COLOR_GOLD: return 5.0;
            case COLOR_SILVER: return 10.0;
            default: return 20.0;
        }
    }

    // Temperature Coefficient of Resistance (ppm/K) for 6th band
    public static int getTcr(int color) {
        switch (color) {
            case COLOR_BROWN: return 100;
            case COLOR_RED: return 50;
            case COLOR_ORANGE: return 15;
            case COLOR_YELLOW: return 25;
            case COLOR_BLUE: return 10;
            case COLOR_VIOLET: return 5;
            default: return 0;
        }
    }

    // Calculate 4-Band Resistor
    public static ResistorResult calculate4Band(int b1, int b2, int multColor, int tolColor) {
        double sig = b1 * 10 + b2;
        double mult = getMultiplier(multColor);
        double tol = getTolerance(tolColor);
        double ohms = sig * mult;
        return buildResult(ohms, tol, 0);
    }

    // Calculate 5-Band Resistor
    public static ResistorResult calculate5Band(int b1, int b2, int b3, int multColor, int tolColor) {
        double sig = b1 * 100 + b2 * 10 + b3;
        double mult = getMultiplier(multColor);
        double tol = getTolerance(tolColor);
        double ohms = sig * mult;
        return buildResult(ohms, tol, 0);
    }

    // Calculate 6-Band Resistor
    public static ResistorResult calculate6Band(int b1, int b2, int b3, int multColor, int tolColor, int tcrColor) {
        double sig = b1 * 100 + b2 * 10 + b3;
        double mult = getMultiplier(multColor);
        double tol = getTolerance(tolColor);
        int tcr = getTcr(tcrColor);
        double ohms = sig * mult;
        return buildResult(ohms, tol, tcr);
    }

    private static ResistorResult buildResult(double ohms, double tol, int tcr) {
        ResistorResult r = new ResistorResult();
        r.nominalOhms = ohms;
        r.tolerancePercent = tol;
        r.tcrPpm = tcr;
        r.minOhms = ohms * (1.0 - (tol / 100.0));
        r.maxOhms = ohms * (1.0 + (tol / 100.0));

        r.formattedNominal = formatOhms(ohms);
        r.formattedMin = formatOhms(r.minOhms);
        r.formattedMax = formatOhms(r.maxOhms);
        r.formattedTolerance = String.format(Locale.US, "±%.2f%%", tol).replace(".00", "");
        r.formattedTcr = (tcr > 0) ? (tcr + " ppm/K") : "N/A";
        r.eiaSeriesMatch = matchEiaSeries(ohms);
        return r;
    }

    public static String formatOhms(double ohms) {
        if (ohms >= 1e9) {
            return String.format(Locale.US, "%.2f GΩ", ohms / 1e9).replace(".00", "");
        } else if (ohms >= 1e6) {
            return String.format(Locale.US, "%.2f MΩ", ohms / 1e6).replace(".00", "");
        } else if (ohms >= 1e3) {
            return String.format(Locale.US, "%.2f kΩ", ohms / 1e3).replace(".00", "");
        } else if (ohms >= 1.0) {
            return String.format(Locale.US, "%.2f Ω", ohms).replace(".00", "");
        } else {
            return String.format(Locale.US, "%.3f Ω", ohms);
        }
    }

    // Parse user input text e.g. "4.7k", "220R", "10M"
    public static double parseResistanceText(String text) {
        if (text == null) return -1.0;
        String clean = text.trim().toUpperCase().replace(" ", "").replace("Ω", "").replace("OHM", "");
        if (clean.isEmpty()) return -1.0;

        try {
            double multiplier = 1.0;
            if (clean.endsWith("K")) {
                multiplier = 1000.0;
                clean = clean.substring(0, clean.length() - 1);
            } else if (clean.endsWith("M")) {
                multiplier = 1000000.0;
                clean = clean.substring(0, clean.length() - 1);
            } else if (clean.endsWith("G")) {
                multiplier = 1000000000.0;
                clean = clean.substring(0, clean.length() - 1);
            } else if (clean.endsWith("R")) {
                multiplier = 1.0;
                clean = clean.substring(0, clean.length() - 1);
            } else if (clean.contains("K")) {
                String[] parts = clean.split("K");
                double whole = Double.parseDouble(parts[0]);
                double frac = parts.length > 1 && !parts[1].isEmpty() ? Double.parseDouble("0." + parts[1]) : 0.0;
                return (whole + frac) * 1000.0;
            } else if (clean.contains("R")) {
                String[] parts = clean.split("R");
                double whole = Double.parseDouble(parts[0]);
                double frac = parts.length > 1 && !parts[1].isEmpty() ? Double.parseDouble("0." + parts[1]) : 0.0;
                return whole + frac;
            } else if (clean.contains("M")) {
                String[] parts = clean.split("M");
                double whole = Double.parseDouble(parts[0]);
                double frac = parts.length > 1 && !parts[1].isEmpty() ? Double.parseDouble("0." + parts[1]) : 0.0;
                return (whole + frac) * 1000000.0;
            }

            return Double.parseDouble(clean) * multiplier;
        } catch (Exception e) {
            return -1.0;
        }
    }

    // Reverse Band Synthesizer (value to 4-band array: [b1, b2, mult, tol])
    public static int[] synthesize4Band(double ohms, int toleranceColor) {
        if (ohms <= 0) return new int[]{COLOR_BROWN, COLOR_BLACK, COLOR_BROWN, COLOR_GOLD};

        // Determine multiplier
        int multColor = COLOR_BLACK;
        double mantissa = ohms;

        if (ohms < 0.1) {
            multColor = COLOR_SILVER;
            mantissa = ohms / 0.01;
        } else if (ohms < 1.0) {
            multColor = COLOR_GOLD;
            mantissa = ohms / 0.1;
        } else {
            int power = (int) Math.floor(Math.log10(ohms));
            if (power >= 1) {
                multColor = Math.min(COLOR_WHITE, power - 1);
                mantissa = ohms / Math.pow(10, multColor);
            }
        }

        int roundSig = (int) Math.round(mantissa);
        if (roundSig >= 100) {
            roundSig = 10;
            multColor = Math.min(COLOR_WHITE, multColor + 1);
        } else if (roundSig < 10) {
            roundSig = 10;
        }

        int b1 = roundSig / 10;
        int b2 = roundSig % 10;
        return new int[]{b1, b2, multColor, toleranceColor};
    }

    // Check against EIA E12 and E24 standard values
    private static final int[] E12_VALUES = {10, 12, 15, 18, 22, 27, 33, 39, 47, 56, 68, 82};
    private static final int[] E24_VALUES = {
            10, 11, 12, 13, 15, 16, 18, 20, 22, 24, 27, 30,
            33, 36, 39, 43, 47, 51, 56, 62, 68, 75, 82, 91
    };

    public static String matchEiaSeries(double ohms) {
        if (ohms <= 0) return "Custom Value";
        double norm = ohms;
        while (norm >= 100.0) norm /= 10.0;
        while (norm < 10.0) norm *= 10.0;
        int rounded = (int) Math.round(norm);

        for (int v : E12_VALUES) {
            if (v == rounded) return "Standard EIA E12 (10%)";
        }
        for (int v : E24_VALUES) {
            if (v == rounded) return "Standard EIA E24 (5%)";
        }
        return "EIA E96/E192 (Precision)";
    }

    // SMD Decoder: 3-Digit, 4-Digit, and EIA-96
    public static SmdResult decodeSmd(String codeStr) {
        SmdResult res = new SmdResult();
        res.code = (codeStr != null) ? codeStr.trim().toUpperCase() : "";
        res.isValid = false;

        if (res.code.isEmpty()) return res;

        // 1. R-based decimal notation (e.g. 4R7, 0R22, R10, 47R)
        if (res.code.contains("R")) {
            try {
                String[] parts = res.code.split("R");
                double whole = (parts.length > 0 && !parts[0].isEmpty()) ? Double.parseDouble(parts[0]) : 0.0;
                double frac = (parts.length > 1 && !parts[1].isEmpty()) ? Double.parseDouble("0." + parts[1]) : 0.0;
                res.ohms = whole + frac;
                res.tolerancePercent = (res.code.length() >= 4) ? 1.0 : 5.0;
                res.type = "Decimal 'R' Format";
                res.formattedOhms = formatOhms(res.ohms);
                res.isValid = true;
                return res;
            } catch (Exception ignored) {}
        }

        // 2. 3-Digit SMD (5% tolerance: 2 digits + power)
        if (res.code.length() == 3 && res.code.matches("\\d{3}")) {
            int d1 = res.code.charAt(0) - '0';
            int d2 = res.code.charAt(1) - '0';
            int mult = res.code.charAt(2) - '0';
            res.ohms = (d1 * 10 + d2) * Math.pow(10, mult);
            res.tolerancePercent = 5.0;
            res.type = "3-Digit Standard (5%)";
            res.formattedOhms = formatOhms(res.ohms);
            res.isValid = true;
            return res;
        }

        // 3. 4-Digit SMD (1% tolerance: 3 digits + power)
        if (res.code.length() == 4 && res.code.matches("\\d{4}")) {
            int d1 = res.code.charAt(0) - '0';
            int d2 = res.code.charAt(1) - '0';
            int d3 = res.code.charAt(2) - '0';
            int mult = res.code.charAt(3) - '0';
            res.ohms = (d1 * 100 + d2 * 10 + d3) * Math.pow(10, mult);
            res.tolerancePercent = 1.0;
            res.type = "4-Digit Precision (1%)";
            res.formattedOhms = formatOhms(res.ohms);
            res.isValid = true;
            return res;
        }

        // 4. EIA-96 SMD (2 digits 01-96 + 1 letter multiplier)
        if (res.code.length() == 3 && Character.isDigit(res.code.charAt(0)) && Character.isDigit(res.code.charAt(1)) && Character.isLetter(res.code.charAt(2))) {
            int index = Integer.parseInt(res.code.substring(0, 2));
            char letter = res.code.charAt(2);
            double base = getEia96Base(index);
            double mult = getEia96Multiplier(letter);

            if (base > 0 && mult > 0) {
                res.ohms = base * mult;
                res.tolerancePercent = 1.0;
                res.type = "EIA-96 Precision (1%)";
                res.formattedOhms = formatOhms(res.ohms);
                res.isValid = true;
                return res;
            }
        }

        return res;
    }

    private static final int[] EIA96_BASE_VALUES = {
            100, 102, 105, 107, 110, 113, 115, 118, 121, 124, 127, 130, 133, 137, 140, 143,
            147, 150, 154, 158, 162, 165, 169, 174, 178, 182, 187, 191, 196, 200, 205, 210,
            215, 221, 226, 232, 237, 243, 249, 255, 261, 267, 274, 280, 287, 294, 301, 309,
            316, 324, 332, 340, 348, 357, 365, 374, 383, 392, 402, 412, 422, 432, 442, 453,
            464, 475, 487, 499, 511, 523, 536, 549, 562, 576, 590, 604, 619, 634, 649, 665,
            681, 698, 715, 732, 750, 768, 787, 806, 825, 845, 866, 887, 909, 931, 953, 976
    };

    private static double getEia96Base(int index) {
        if (index >= 1 && index <= 96) {
            return EIA96_BASE_VALUES[index - 1];
        }
        return -1;
    }

    private static double getEia96Multiplier(char letter) {
        switch (letter) {
            case 'Z': return 0.001;
            case 'Y': case 'R': return 0.01;
            case 'X': case 'S': return 0.1;
            case 'A': return 1.0;
            case 'B': case 'H': return 10.0;
            case 'C': return 100.0;
            case 'D': return 1000.0;
            case 'E': return 10000.0;
            case 'F': return 100000.0;
            default: return -1;
        }
    }
}
