package com.kimrosil.plumbbob;

import java.util.Locale;

/**
 * PlumbingEngine
 * High-precision drainage slope, invert depth elevation, and code compliance calculator.
 * Supports International Plumbing Code (IPC) and Uniform Plumbing Code (UPC) rules.
 */
public class PlumbingEngine {

    public static final int CODE_IPC = 0;
    public static final int CODE_UPC = 1;

    public static final int PIPE_SIZE_LE_2_INCH = 0;  // 1-1/4" to 2"
    public static final int PIPE_SIZE_3_INCH = 1;     // 3"
    public static final int PIPE_SIZE_4_TO_6_INCH = 2;// 4" to 6"
    public static final int PIPE_SIZE_GE_8_INCH = 3;  // 8"+

    public static class CalculationResult {
        public double runLength;       // feet or meters
        public double pitchInPerFt;    // inches per foot
        public double pitchMmPerM;     // mm per meter
        public double startInvert;     // inches or mm
        public double endInvert;       // inches or mm
        public double totalFall;       // inches or mm
        public double gradePercent;    // % (e.g. 2.08)
        public int ratioDenominator;   // 1 : 48
        public String fractionalFall;  // e.g. "5 3/8\""
        public boolean isCompliant;
        public int complianceLevel;    // 0=Pass, 1=Warning (steep), 2=Fail (shallow)
        public String complianceTitle;
        public String complianceReason;
    }

    /**
     * Solves for Total Fall and End Invert given Run Length, Pitch, and Start Invert.
     */
    public static CalculationResult solveFallAndInvert(double runLength, double pitchInPerFt,
                                                       double startInvert, int pipeSize,
                                                       int codeStandard, boolean isMetric) {
        CalculationResult res = new CalculationResult();
        res.runLength = Math.max(0.1, runLength);
        res.pitchInPerFt = Math.max(0.001, pitchInPerFt);
        res.startInvert = startInvert;

        if (isMetric) {
            // runLength is in meters, pitchInPerFt is converted to mm/m
            // 1 in/ft = (25.4 mm) / (0.3048 m) = 83.333 mm/m
            res.pitchMmPerM = res.pitchInPerFt * 83.333333;
            res.totalFall = res.runLength * res.pitchMmPerM; // mm
            res.endInvert = res.startInvert + res.totalFall;
            res.gradePercent = res.pitchMmPerM / 10.0;
            res.ratioDenominator = (int) Math.round(1000.0 / res.pitchMmPerM);
            res.fractionalFall = String.format(Locale.US, "%.1f mm", res.totalFall);
        } else {
            res.totalFall = res.runLength * res.pitchInPerFt; // inches
            res.endInvert = res.startInvert + res.totalFall;
            res.gradePercent = (res.pitchInPerFt / 12.0) * 100.0;
            res.ratioDenominator = (int) Math.round(12.0 / res.pitchInPerFt);
            res.pitchMmPerM = (res.pitchInPerFt / 12.0) * 1000.0;
            res.fractionalFall = formatFractionalInches(res.totalFall);
        }

        evaluateCompliance(res, pipeSize, codeStandard);
        return res;
    }

    /**
     * Solves for required Pitch and Grade % given Start Invert, End Invert, and Run Length.
     */
    public static CalculationResult solvePitchFromInverts(double startInvert, double endInvert,
                                                          double runLength, int pipeSize,
                                                          int codeStandard, boolean isMetric) {
        CalculationResult res = new CalculationResult();
        res.runLength = Math.max(0.1, runLength);
        res.startInvert = startInvert;
        res.endInvert = Math.max(startInvert, endInvert);
        res.totalFall = res.endInvert - res.startInvert;

        if (isMetric) {
            // totalFall in mm, runLength in m
            res.pitchMmPerM = res.totalFall / res.runLength;
            res.pitchInPerFt = res.pitchMmPerM / 83.333333;
            res.gradePercent = res.pitchMmPerM / 10.0;
            res.ratioDenominator = res.pitchMmPerM > 0 ? (int) Math.round(1000.0 / res.pitchMmPerM) : 0;
            res.fractionalFall = String.format(Locale.US, "%.1f mm", res.totalFall);
        } else {
            // totalFall in inches, runLength in feet
            res.pitchInPerFt = res.totalFall / res.runLength;
            res.pitchMmPerM = (res.pitchInPerFt / 12.0) * 1000.0;
            res.gradePercent = (res.pitchInPerFt / 12.0) * 100.0;
            res.ratioDenominator = res.pitchInPerFt > 0 ? (int) Math.round(12.0 / res.pitchInPerFt) : 0;
            res.fractionalFall = formatFractionalInches(res.totalFall);
        }

        evaluateCompliance(res, pipeSize, codeStandard);
        return res;
    }

    /**
     * Solves for required Run Length given Total Invert Drop and target Pitch.
     */
    public static CalculationResult solveRunFromDropAndPitch(double totalDrop, double pitchInPerFt,
                                                            double startInvert, int pipeSize,
                                                            int codeStandard, boolean isMetric) {
        CalculationResult res = new CalculationResult();
        res.totalFall = Math.max(0.1, totalDrop);
        res.pitchInPerFt = Math.max(0.001, pitchInPerFt);
        res.startInvert = startInvert;
        res.endInvert = startInvert + res.totalFall;

        if (isMetric) {
            res.pitchMmPerM = res.pitchInPerFt * 83.333333;
            res.runLength = res.totalFall / res.pitchMmPerM;
            res.gradePercent = res.pitchMmPerM / 10.0;
            res.ratioDenominator = (int) Math.round(1000.0 / res.pitchMmPerM);
            res.fractionalFall = String.format(Locale.US, "%.1f mm", res.totalFall);
        } else {
            res.runLength = res.totalFall / res.pitchInPerFt;
            res.gradePercent = (res.pitchInPerFt / 12.0) * 100.0;
            res.ratioDenominator = (int) Math.round(12.0 / res.pitchInPerFt);
            res.pitchMmPerM = (res.pitchInPerFt / 12.0) * 1000.0;
            res.fractionalFall = formatFractionalInches(res.totalFall);
        }

        evaluateCompliance(res, pipeSize, codeStandard);
        return res;
    }

    private static void evaluateCompliance(CalculationResult res, int pipeSize, int codeStandard) {
        double minPitchInPerFt = 0.25; // default 1/4" / ft

        switch (pipeSize) {
            case PIPE_SIZE_LE_2_INCH:
                minPitchInPerFt = 0.25; // 1/4"
                break;
            case PIPE_SIZE_3_INCH:
                if (codeStandard == CODE_UPC) {
                    minPitchInPerFt = 0.25; // UPC requires 1/4" for 3" unless approved
                } else {
                    minPitchInPerFt = 0.125; // IPC permits 1/8" for 3"
                }
                break;
            case PIPE_SIZE_4_TO_6_INCH:
                minPitchInPerFt = 0.125; // 1/8"
                break;
            case PIPE_SIZE_GE_8_INCH:
                minPitchInPerFt = 0.0625; // 1/16"
                break;
        }

        if (res.pitchInPerFt < (minPitchInPerFt - 0.005)) {
            res.isCompliant = false;
            res.complianceLevel = 2; // Alert / Non-compliant
            res.complianceTitle = "DEFICIENT PITCH (FAIL)";
            res.complianceReason = String.format(Locale.US,
                    "Below minimum code pitch (%.3f\"/ft required). Risk of solid sedimentation and blockages.",
                    minPitchInPerFt);
        } else if (res.pitchInPerFt > 0.505) {
            res.isCompliant = true;
            res.complianceLevel = 1; // Warning: Too steep
            res.complianceTitle = "STEEP PITCH (NOTICE)";
            res.complianceReason = "Pitch exceeds 1/2\"/ft. Liquids may outrun solids over long straight runs.";
        } else {
            res.isCompliant = true;
            res.complianceLevel = 0; // Pass
            res.complianceTitle = "CODE COMPLIANT (PASS)";
            res.complianceReason = String.format(Locale.US,
                    "Meets self-cleansing scouring velocity (≥ 2.0 ft/sec) per %s standards.",
                    codeStandard == CODE_UPC ? "UPC" : "IPC");
        }
    }

    public static String formatFractionalInches(double totalInches) {
        int whole = (int) Math.floor(totalInches);
        double remainder = totalInches - whole;
        int sixteenths = (int) Math.round(remainder * 16.0);

        if (sixteenths == 16) {
            whole += 1;
            sixteenths = 0;
        }

        if (sixteenths == 0) {
            return whole + "\"";
        }

        // Simplify fraction
        int num = sixteenths;
        int den = 16;
        while (num % 2 == 0 && den % 2 == 0) {
            num /= 2;
            den /= 2;
        }

        if (whole == 0) {
            return num + "/" + den + "\"";
        } else {
            return whole + " " + num + "/" + den + "\"";
        }
    }
}
