package com.kimrosil.brazering;

import java.util.Locale;

/**
 * BrazeEngine
 * Hydraulic capillary volume, silver alloy preform ring sizing, and nitrogen purge solver
 * for HVAC/R copper refrigeration and plumbing tubing brazing.
 */
public class BrazeEngine {

    public static final int ALLOY_BCUP2_0_SILVER = 0;   // 0% Ag (BCuP-2) Density: 8.08 g/cm³
    public static final int ALLOY_BCUP5_15_SILVER = 1;  // 15% Ag (Sil-Fos 15) Density: 8.44 g/cm³
    public static final int ALLOY_BAG36_45_SILVER = 2;  // 45% Ag (Safety-Silv 45) Density: 9.06 g/cm³
    public static final int ALLOY_BAG50_50_SILVER = 3;  // 50% Ag (High Ag) Density: 9.25 g/cm³

    public static final double[] STANDARD_OD_SIZES = {
            0.250,  // 1/4"
            0.375,  // 3/8"
            0.500,  // 1/2"
            0.625,  // 5/8"
            0.750,  // 3/4"
            0.875,  // 7/8"
            1.125,  // 1-1/8"
            1.375,  // 1-3/8"
            1.625,  // 1-5/8"
            2.125   // 2-1/8"
    };

    public static final String[] STANDARD_OD_LABELS = {
            "1/4\"", "3/8\"", "1/2\"", "5/8\"", "3/4\"",
            "7/8\"", "1-1/8\"", "1-3/8\"", "1-5/8\"", "2-1/8\""
    };

    public static class BrazeResult {
        public double tubeOdInches;
        public double socketDepthInches;
        public double radialClearanceInches;
        public int alloyType;
        public int jointCount;

        // Outputs
        public double jointVolumeCuIn;       // per joint
        public double totalJobVolumeCuIn;    // all joints
        public double alloyMassGrams;        // per joint
        public double totalJobMassGrams;     // all joints
        public double rodLength1_16_Inches;  // 1/16" wire length needed
        public double rodLength1_8_Inches;   // 1/8" wire length needed
        public double preformWireDiaInches;  // exact wire diameter if wound into ring
        public String recommendedPreformGauge;
        public double nitrogenSweepScfh;     // recommended N2 flow
        public boolean isClearanceOptimal;
        public String clearanceStatusTitle;
        public String clearanceAdvice;
    }

    public static BrazeResult calculateJoint(double tubeOdInches, double socketDepthInches,
                                            double radialClearanceInches, int alloyType, int jointCount) {
        BrazeResult res = new BrazeResult();
        res.tubeOdInches = Math.max(0.125, tubeOdInches);
        res.socketDepthInches = Math.max(0.1, socketDepthInches);
        res.radialClearanceInches = Math.max(0.0005, radialClearanceInches);
        res.alloyType = alloyType;
        res.jointCount = Math.max(1, jointCount);

        // Capillary annular cylinder volume: V = pi * OD * Depth * Clearance * 1.25 (fillet cap)
        double baseAnnular = Math.PI * res.tubeOdInches * res.socketDepthInches * res.radialClearanceInches;
        res.jointVolumeCuIn = baseAnnular * 1.25; // 25% extra for concave fillet & face cap
        res.totalJobVolumeCuIn = res.jointVolumeCuIn * res.jointCount;

        // Density in g/cm³:
        double densityGcm3 = 8.44; // default BCuP-5 (15% Ag)
        switch (alloyType) {
            case ALLOY_BCUP2_0_SILVER: densityGcm3 = 8.08; break;
            case ALLOY_BCUP5_15_SILVER: densityGcm3 = 8.44; break;
            case ALLOY_BAG36_45_SILVER: densityGcm3 = 9.06; break;
            case ALLOY_BAG50_50_SILVER: densityGcm3 = 9.25; break;
        }

        // 1 cubic inch = 16.387 cm³
        double volCm3 = res.jointVolumeCuIn * 16.387064;
        res.alloyMassGrams = volCm3 * densityGcm3;
        res.totalJobMassGrams = res.alloyMassGrams * res.jointCount;

        // Equivalent Rod lengths:
        // 1/16" wire dia (0.0625") -> radius 0.03125" -> Area = pi * r² = 0.003068 in²
        double area1_16 = Math.PI * Math.pow(0.0625 / 2.0, 2);
        res.rodLength1_16_Inches = res.jointVolumeCuIn / area1_16;

        // 1/8" wire dia (0.125") -> radius 0.0625" -> Area = pi * r² = 0.01227 in²
        double area1_8 = Math.PI * Math.pow(0.125 / 2.0, 2);
        res.rodLength1_8_Inches = res.jointVolumeCuIn / area1_8;

        // Preform Ring wire diameter: Ring length = pi * tubeOdInches
        // Required wire cross section: Aw = jointVolume / (pi * tubeOdInches) = Depth * Clearance * 1.25
        double aw = res.socketDepthInches * res.radialClearanceInches * 1.25;
        res.preformWireDiaInches = 2.0 * Math.sqrt(aw / Math.PI);

        if (res.preformWireDiaInches <= 0.045) {
            res.recommendedPreformGauge = "0.035\" (20 AWG Wire)";
        } else if (res.preformWireDiaInches <= 0.070) {
            res.recommendedPreformGauge = "1/16\" (0.062\" Wire)";
        } else if (res.preformWireDiaInches <= 0.100) {
            res.recommendedPreformGauge = "5/64\" (0.078\" Wire)";
        } else {
            res.recommendedPreformGauge = "1/8\" (0.125\" Wire)";
        }

        // Recommended Nitrogen purge sweep (SCFH) during heating:
        // Small lines (1/4 - 1/2): 2-3 SCFH. Medium (5/8 - 7/8): 3-4 SCFH. Large (> 1"): 4-5 SCFH.
        if (res.tubeOdInches <= 0.50) {
            res.nitrogenSweepScfh = 2.5;
        } else if (res.tubeOdInches <= 0.875) {
            res.nitrogenSweepScfh = 3.5;
        } else {
            res.nitrogenSweepScfh = 5.0;
        }

        // Clearance evaluation (AWS C3.4 / CDA standards)
        if (res.radialClearanceInches < 0.001) {
            res.isClearanceOptimal = false;
            res.clearanceStatusTitle = "TIGHT INTERFERENCE FIT";
            res.clearanceAdvice = "Clearance < 0.001\". Capillary action restricted. Flux entrapment risk.";
        } else if (res.radialClearanceInches > 0.005) {
            res.isClearanceOptimal = false;
            res.clearanceStatusTitle = "WIDE JOINT GAP (EXCESS)";
            res.clearanceAdvice = "Clearance > 0.005\". Capillary draw weakened. Joint requires high-bridging alloy.";
        } else {
            res.isClearanceOptimal = true;
            res.clearanceStatusTitle = "OPTIMAL CAPILLARY CLEARANCE";
            res.clearanceAdvice = "Clearance 0.002\"-0.004\" provides maximum capillary draw & tensile strength.";
        }

        return res;
    }

    public static String getAlloyName(int alloy) {
        switch (alloy) {
            case ALLOY_BCUP2_0_SILVER: return "BCuP-2 (0% Ag / Phosphor Copper)";
            case ALLOY_BCUP5_15_SILVER: return "BCuP-5 (Sil-Fos 15% Ag)";
            case ALLOY_BAG36_45_SILVER: return "BAg-36 (Safety-Silv 45% Ag)";
            case ALLOY_BAG50_50_SILVER: return "BAg-50 (50% High Silver)";
            default: return "Silver Brazing Alloy";
        }
    }
}
