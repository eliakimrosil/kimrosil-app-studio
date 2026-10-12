package com.kimrosil.solderwick;

public class ThermalPhysicsEngine {

    public static class SolderProfile {
        public final String alloyName;
        public final int liquidusTempC;
        public final int baseRecTipTempC;
        public final int defaultDwellMs;

        public SolderProfile(String alloyName, int liquidusTempC, int baseRecTipTempC, int defaultDwellMs) {
            this.alloyName = alloyName;
            this.liquidusTempC = liquidusTempC;
            this.baseRecTipTempC = baseRecTipTempC;
            this.defaultDwellMs = defaultDwellMs;
        }
    }

    public static final SolderProfile ALLOY_63_37 = new SolderProfile("63/37 SnPb Eutectic", 183, 315, 2500);
    public static final SolderProfile ALLOY_60_40 = new SolderProfile("60/40 SnPb Leaded", 190, 320, 2500);
    public static final SolderProfile ALLOY_SAC305 = new SolderProfile("SAC305 Lead-Free", 217, 350, 3200);
    public static final SolderProfile ALLOY_SN42BI58 = new SolderProfile("Sn42Bi58 Low-Temp", 138, 220, 2000);
    public static final SolderProfile ALLOY_CHIPQUIK = new SolderProfile("ChipQuik Removal", 58, 180, 1500);

    // SMD Component Package Definitions
    public static class ComponentPackage {
        public final String name;
        public final int maxDwellCapMs;
        public final String recommendedFlux;

        public ComponentPackage(String name, int maxDwellCapMs, String recommendedFlux) {
            this.name = name;
            this.maxDwellCapMs = maxDwellCapMs;
            this.recommendedFlux = recommendedFlux;
        }
    }

    public static final ComponentPackage PKG_0402 = new ComponentPackage("0402 Micro", 1500, "ROL0 No-Clean");
    public static final ComponentPackage PKG_0805 = new ComponentPackage("0603/0805 SMD", 2500, "ROL0 No-Clean / RMA");
    public static final ComponentPackage PKG_SOIC = new ComponentPackage("SOIC/QFP IC", 3500, "RMA Rosin / No-Clean");
    public static final ComponentPackage PKG_DPAK = new ComponentPackage("DPAK/Power", 4500, "RMA Rosin");

    // Soldering Iron Tip Geometry Compensation
    public static class TipGeometry {
        public final String name;
        public final int tempOffsetC;
        public final String bestFor;

        public TipGeometry(String name, int tempOffsetC, String bestFor) {
            this.name = name;
            this.tempOffsetC = tempOffsetC;
            this.bestFor = bestFor;
        }
    }

    public static final TipGeometry TIP_CHISEL = new TipGeometry("Chisel (2.4mm)", 0, "General PCB & SMD Work");
    public static final TipGeometry TIP_BEVEL = new TipGeometry("Bevel (Hoof)", 5, "Drag Soldering & High Mass");
    public static final TipGeometry TIP_CONICAL = new TipGeometry("Conical (0.2mm)", 15, "Micro Pitch Jumper Repair");
    public static final TipGeometry TIP_KNIFE = new TipGeometry("Knife (K-Type)", -5, "Bridge Clearing & Multi-Pin");

    // Wire Diameter Gauge Reference
    public static class WireGauge {
        public final String diameter;
        public final String pitchTarget;

        public WireGauge(String diameter, String pitchTarget) {
            this.diameter = diameter;
            this.pitchTarget = pitchTarget;
        }
    }

    public static final WireGauge WIRE_03MM = new WireGauge("0.3mm (0.012\")", "0.4 - 0.5mm Ultra-Fine Pitch");
    public static final WireGauge WIRE_05MM = new WireGauge("0.5mm (0.020\")", "0.65 - 0.8mm SMD Standards");
    public static final WireGauge WIRE_08MM = new WireGauge("0.8mm (0.031\")", "1.27 - 2.54mm Through-Hole");
    public static final WireGauge WIRE_10MM = new WireGauge("1.0mm (0.040\")", "Heavy Ground Lugs & Connectors");

    /**
     * Calculates recommended soldering iron tip setpoint temperature (°C).
     * Clamped strictly between 150°C and 450°C.
     */
    public static int calculateRecommendedTipTemp(SolderProfile alloy, int boardLayers, double copperOz,
                                                  boolean heavyGroundPlane, int tipOffsetC, int tipGeometryOffsetC) {
        int target = alloy.baseRecTipTempC;

        // Layer heat sink dissipation factor
        if (boardLayers >= 8) {
            target += 30;
        } else if (boardLayers >= 4) {
            target += 15;
        } else if (boardLayers >= 2) {
            target += 5;
        }

        // Copper plane thickness
        if (copperOz >= 2.0) {
            target += 20;
        } else if (copperOz > 1.0) {
            target += 10;
        }

        // Direct ground plane connection
        if (heavyGroundPlane) {
            target += 25;
        }

        // Iron tip calibration offset
        target += tipOffsetC;

        // Tip geometry thermal compensation
        target += tipGeometryOffsetC;

        // Clamp to prevent pad lifting and thermal degradation
        return Math.max(150, Math.min(450, target));
    }

    public static int calculateRecommendedTipTemp(SolderProfile alloy, int boardLayers, double copperOz,
                                                  boolean heavyGroundPlane, int tipOffsetC) {
        return calculateRecommendedTipTemp(alloy, boardLayers, copperOz, heavyGroundPlane, tipOffsetC, 0);
    }

    /**
     * Calculates safe thermal dwell budget (ms) based on board layer dissipation and component package limits.
     * Clamped strictly between 500ms and 10000ms.
     */
    public static int calculateMaxSafeDwellMs(SolderProfile alloy, int boardLayers, double copperOz,
                                             boolean heavyGroundPlane, int pkgMaxDwellCapMs) {
        int safeDwell = alloy.defaultDwellMs;

        if (boardLayers >= 6) {
            safeDwell += 800; // Requires longer dwell to reach wetting
        } else if (boardLayers >= 4) {
            safeDwell += 400;
        }

        if (heavyGroundPlane) {
            safeDwell += 600;
        }

        if (copperOz >= 2.0) {
            safeDwell += 500;
        }

        // Cap by SMD package sensitivity if specified
        if (pkgMaxDwellCapMs > 0 && safeDwell > pkgMaxDwellCapMs) {
            safeDwell = pkgMaxDwellCapMs;
        }

        return Math.max(500, Math.min(10000, safeDwell));
    }

    public static int calculateMaxSafeDwellMs(SolderProfile alloy, int boardLayers, double copperOz,
                                             boolean heavyGroundPlane) {
        return calculateMaxSafeDwellMs(alloy, boardLayers, copperOz, heavyGroundPlane, 0);
    }

    /**
     * Calculates Intermetallic Compound (IMC) bond margin assessment.
     */
    public static String assessJointBond(int dwellElapsedMs, int maxSafeBudgetMs) {
        if (dwellElapsedMs < 800) {
            return "Cold Joint Risk: Insufficient dwell for wetting & intermetallic bond formation.";
        } else if (dwellElapsedMs <= maxSafeBudgetMs) {
            int margin = maxSafeBudgetMs - dwellElapsedMs;
            return "Optimal Joint: Proper intermetallic Cu6Sn5 boundary layer formed. Safe thermal headroom: " + margin + "ms.";
        } else {
            int breach = dwellElapsedMs - maxSafeBudgetMs;
            return "Substrate Delamination Risk: Safe thermal dwell exceeded by " + breach + "ms. Excess Cu3Sn intermetallic brittleness.";
        }
    }
}