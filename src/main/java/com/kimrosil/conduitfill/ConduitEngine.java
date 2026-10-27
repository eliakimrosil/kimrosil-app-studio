package com.kimrosil.conduitfill;

import java.util.Locale;

public class ConduitEngine {

    // Raceway Types
    public static final int RACEWAY_EMT = 0;
    public static final int RACEWAY_PVC_40 = 1;
    public static final int RACEWAY_PVC_80 = 2;
    public static final int RACEWAY_RMC = 3;
    public static final int RACEWAY_FMC = 4;

    public static final String[] RACEWAY_NAMES = {
            "EMT (Steel Tubing)",
            "PVC Schedule 40",
            "PVC Schedule 80",
            "RMC (Rigid Metal)",
            "FMC (Flexible Metal)"
    };

    // Conduit Trade Sizes: 1/2", 3/4", 1", 1-1/4", 1-1/2", 2", 2-1/2", 3", 3-1/2", 4"
    public static final String[] TRADE_SIZES = {
            "1/2\"", "3/4\"", "1\"", "1-1/4\"", "1-1/2\"", "2\"", "2-1/2\"", "3\"", "3-1/2\"", "4\""
    };

    public static final double[] TRADE_NOMINAL_INCHES = {
            0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0
    };

    // NEC Chapter 9 Table 4: Internal Diameters (inches)
    // [Raceway][SizeIndex]
    private static final double[][] CONDUIT_ID = {
            // EMT
            {0.622, 0.824, 1.049, 1.380, 1.610, 2.067, 2.731, 3.356, 3.834, 4.310},
            // PVC Sch 40
            {0.602, 0.804, 1.029, 1.360, 1.590, 2.047, 2.445, 3.042, 3.521, 3.998},
            // PVC Sch 80
            {0.526, 0.722, 0.936, 1.255, 1.476, 1.913, 2.295, 2.864, 3.325, 3.786},
            // RMC
            {0.632, 0.836, 1.063, 1.394, 1.624, 2.083, 2.489, 3.090, 3.570, 4.050},
            // FMC
            {0.625, 0.812, 1.000, 1.250, 1.500, 2.000, 2.500, 3.000, 3.500, 4.000}
    };

    // Conductor Insulation Types
    public static final int INSULATION_THHN = 0;
    public static final int INSULATION_XHHW = 1;
    public static final int INSULATION_RHW = 2;

    public static final String[] INSULATION_NAMES = {
            "THHN / THWN-2",
            "XHHW / XHHW-2",
            "RHW / USE"
    };

    // Conductor Gauges
    public static final String[] WIRE_GAUGES = {
            "14 AWG", "12 AWG", "10 AWG", "8 AWG", "6 AWG",
            "4 AWG", "3 AWG", "2 AWG", "1 AWG",
            "1/0", "2/0", "3/0", "4/0",
            "250 kcmil", "350 kcmil", "500 kcmil"
    };

    // NEC Chapter 9 Table 5: Conductor Overall Diameters (inches)
    // [Insulation][GaugeIndex]
    private static final double[][] WIRE_OD = {
            // THHN
            {0.111, 0.130, 0.164, 0.216, 0.254, 0.324, 0.352, 0.384, 0.446, 0.486, 0.532, 0.584, 0.642, 0.711, 0.817, 0.949},
            // XHHW
            {0.133, 0.152, 0.176, 0.236, 0.274, 0.322, 0.350, 0.382, 0.442, 0.482, 0.528, 0.580, 0.638, 0.707, 0.813, 0.945},
            // RHW
            {0.163, 0.182, 0.206, 0.266, 0.304, 0.352, 0.380, 0.412, 0.482, 0.522, 0.568, 0.620, 0.678, 0.757, 0.863, 0.995}
    };

    // Result structure
    public static class FillResult {
        public double conduitId;
        public double totalConduitAreaSqIn;
        public double allowablePercent;
        public double allowableAreaSqIn;
        public int totalConductorCount;
        public double totalConductorAreaSqIn;
        public double actualFillPercent;
        public double remainingAreaSqIn;
        public double jamRatio;
        public String jamStatus; // "SAFE", "CRITICAL RISK"
        public String complianceStatus; // "COMPLIANT", "CAUTION", "OVERFILL VIOLATION"
        public double straightPullBoxInches;
        public double anglePullBoxInches;

        public String formattedFill;
        public String formattedAllowed;
        public String formattedConduitArea;
        public String formattedWireArea;
        public String formattedRemaining;
    }

    public static double getConduitId(int raceway, int sizeIdx) {
        if (raceway < 0 || raceway >= CONDUIT_ID.length) raceway = 0;
        if (sizeIdx < 0 || sizeIdx >= CONDUIT_ID[raceway].length) sizeIdx = 0;
        return CONDUIT_ID[raceway][sizeIdx];
    }

    public static double getWireOd(int insulation, int gaugeIdx) {
        if (insulation < 0 || insulation >= WIRE_OD.length) insulation = 0;
        if (gaugeIdx < 0 || gaugeIdx >= WIRE_OD[insulation].length) gaugeIdx = 0;
        return WIRE_OD[insulation][gaugeIdx];
    }

    public static double getWireArea(int insulation, int gaugeIdx) {
        double od = getWireOd(insulation, gaugeIdx);
        return Math.PI * Math.pow(od * 0.5, 2);
    }

    public static FillResult calculate(
            int racewayType,
            int conduitSizeIdx,
            int insulationType,
            int primaryGaugeIdx,
            int primaryCount,
            int groundGaugeIdx,
            int groundCount,
            boolean isNippleRule
    ) {
        FillResult r = new FillResult();

        // 1. Conduit Geometry
        r.conduitId = getConduitId(racewayType, conduitSizeIdx);
        r.totalConduitAreaSqIn = Math.PI * Math.pow(r.conduitId * 0.5, 2);

        // 2. Conductor Areas
        primaryCount = Math.max(0, primaryCount);
        groundCount = Math.max(0, groundCount);
        r.totalConductorCount = primaryCount + groundCount;

        double primaryArea = getWireArea(insulationType, primaryGaugeIdx);
        double groundArea = getWireArea(insulationType, groundGaugeIdx);
        r.totalConductorAreaSqIn = (primaryCount * primaryArea) + (groundCount * groundArea);

        // 3. Allowable Fill Percentage (NEC Chapter 9 Table 1)
        if (isNippleRule) {
            r.allowablePercent = 60.0;
        } else if (r.totalConductorCount == 1) {
            r.allowablePercent = 53.0;
        } else if (r.totalConductorCount == 2) {
            r.allowablePercent = 31.0;
        } else {
            r.allowablePercent = 40.0; // 3 or more conductors
        }

        r.allowableAreaSqIn = r.totalConduitAreaSqIn * (r.allowablePercent / 100.0);

        // 4. Actual Fill & Remaining
        if (r.totalConduitAreaSqIn > 0) {
            r.actualFillPercent = (r.totalConductorAreaSqIn / r.totalConduitAreaSqIn) * 100.0;
        } else {
            r.actualFillPercent = 0.0;
        }
        r.remainingAreaSqIn = Math.max(0.0, r.allowableAreaSqIn - r.totalConductorAreaSqIn);

        // 5. Compliance Status
        if (r.actualFillPercent <= r.allowablePercent) {
            if (r.actualFillPercent >= r.allowablePercent * 0.92) {
                r.complianceStatus = "CAUTION (Near Limit)";
            } else {
                r.complianceStatus = "CODE COMPLIANT";
            }
        } else {
            r.complianceStatus = "OVERFILL VIOLATION";
        }

        // 6. Conductor Jam Ratio (D/d)
        double largestCableOd = 0.0;
        if (primaryCount > 0) {
            largestCableOd = Math.max(largestCableOd, getWireOd(insulationType, primaryGaugeIdx));
        }
        if (groundCount > 0) {
            largestCableOd = Math.max(largestCableOd, getWireOd(insulationType, groundGaugeIdx));
        }

        if (largestCableOd > 0) {
            r.jamRatio = r.conduitId / largestCableOd;
        } else {
            r.jamRatio = 0.0;
        }

        // Critical Jam probability for 3 conductors wedging side-by-side
        if (r.totalConductorCount >= 3 && r.jamRatio >= 2.8 && r.jamRatio <= 3.2) {
            r.jamStatus = "CRITICAL JAM RISK";
        } else {
            r.jamStatus = "SAFE PULL";
        }

        // 7. NEC 314.28 Pull Box Sizing
        double nominalConduitTrade = TRADE_NOMINAL_INCHES[Math.min(conduitSizeIdx, TRADE_NOMINAL_INCHES.length - 1)];
        r.straightPullBoxInches = 8.0 * nominalConduitTrade;
        r.anglePullBoxInches = 6.0 * nominalConduitTrade;

        // Formatted strings
        r.formattedFill = String.format(Locale.US, "%.1f%%", r.actualFillPercent);
        r.formattedAllowed = String.format(Locale.US, "%.0f%% Max Allowed", r.allowablePercent);
        r.formattedConduitArea = String.format(Locale.US, "%.3f sq in", r.totalConduitAreaSqIn);
        r.formattedWireArea = String.format(Locale.US, "%.3f sq in", r.totalConductorAreaSqIn);
        r.formattedRemaining = String.format(Locale.US, "%.3f sq in", r.remainingAreaSqIn);

        return r;
    }
}
