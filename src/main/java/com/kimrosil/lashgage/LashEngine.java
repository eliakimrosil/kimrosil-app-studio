package com.kimrosil.lashgage;

import java.util.Locale;

/**
 * Deterministic mechanical calculation engine for:
 * 1. Ring and pinion gear backlash, pressure angle vectors, and carrier shim corrections.
 * 2. Solid lifter valve lash thermal cold-staging with metallurgy compensation.
 * 3. 4-point total indicator reading (TIR) runout variation.
 */
public final class LashEngine {

    private LashEngine() {
        // Utility class
    }

    public enum PressureAngle {
        PA_14_5(14.5, "14.5° PA (Spur / Vintage)", 1.997),
        PA_20_0(20.0, "20.0° PA (Modern Automotive)", 1.462),
        PA_25_0(25.0, "25.0° PA (Heavy Truck / Hypoid)", 1.183);

        public final double degrees;
        public final String label;
        public final double carrierShimFactor;

        PressureAngle(double deg, String lbl, double factor) {
            this.degrees = deg;
            this.label = lbl;
            this.carrierShimFactor = factor;
        }
    }

    public enum Metallurgy {
        ALL_IRON("Cast Iron Block & Iron Head", -0.001, -0.025),
        MIXED_IRON_ALUM("Iron Block & Aluminum Head", -0.005, -0.127),
        ALL_ALUMINUM("All-Aluminum Block & Head", -0.007, -0.178);

        public final String label;
        public final double deltaColdIn;
        public final double deltaColdMm;

        Metallurgy(String lbl, double dIn, double dMm) {
            this.label = lbl;
            this.deltaColdIn = dIn;
            this.deltaColdMm = dMm;
        }
    }

    public static class BacklashResult {
        public double measuredIn;
        public double measuredMm;
        public double targetMinIn;
        public double targetMaxIn;
        public double targetMinMm;
        public double targetMaxMm;
        public double deltaIn;
        public double deltaMm;
        public double requiredShimChangeIn;
        public double requiredShimChangeMm;
        public String statusBadge;
        public String actionInstruction;
        public boolean isInSpec;
        public boolean isTight;
        public boolean isLoose;
        public float dialNeedleAngle; // 0 to 360 deg

        @Override
        public String toString() {
            return String.format(Locale.US,
                "Backlash: %.4f\" (%.3f mm) [%s] - Action: %s",
                measuredIn, measuredMm, statusBadge, actionInstruction);
        }
    }

    public static class ValveLashResult {
        public double hotSpecIn;
        public double hotSpecMm;
        public double coldTargetIn;
        public double coldTargetMm;
        public double expansionDeltaIn;
        public double expansionDeltaMm;
        public double feelerGoIn;
        public double feelerNoGoIn;
        public double feelerGoMm;
        public double feelerNoGoMm;
        public String note;

        @Override
        public String toString() {
            return String.format(Locale.US,
                "Hot: %.3f\" -> Cold Target: %.3f\" (Go: %.3f\", No-Go: %.3f\")",
                hotSpecIn, coldTargetIn, feelerGoIn, feelerNoGoIn);
        }
    }

    public static class RunoutResult {
        public double minVal;
        public double maxVal;
        public double tir; // Total Indicator Reading
        public boolean isAcceptable;
        public String status;
    }

    /**
     * Computes differential carrier shim adjustments based on measured backlash.
     */
    public static BacklashResult computeBacklash(
            double measured,
            boolean isInputInInches,
            double targetMin,
            double targetMax,
            PressureAngle pa) {

        BacklashResult res = new BacklashResult();

        // Defensive normalization
        if (Double.isNaN(measured) || Double.isInfinite(measured) || measured < 0) {
            measured = 0.0;
        }
        if (Double.isNaN(targetMin) || targetMin <= 0) targetMin = isInputInInches ? 0.006 : 0.15;
        if (Double.isNaN(targetMax) || targetMax <= targetMin) targetMax = targetMin + (isInputInInches ? 0.004 : 0.10);
        if (pa == null) pa = PressureAngle.PA_20_0;

        double measuredIn = isInputInInches ? measured : (measured / 25.4);
        double targetMinIn = isInputInInches ? targetMin : (targetMin / 25.4);
        double targetMaxIn = isInputInInches ? targetMax : (targetMax / 25.4);
        double targetMidIn = (targetMinIn + targetMaxIn) / 2.0;

        res.measuredIn = measuredIn;
        res.measuredMm = measuredIn * 25.4;
        res.targetMinIn = targetMinIn;
        res.targetMaxIn = targetMaxIn;
        res.targetMinMm = targetMinIn * 25.4;
        res.targetMaxMm = targetMaxIn * 25.4;

        double errorIn = measuredIn - targetMidIn;
        res.deltaIn = errorIn;
        res.deltaMm = errorIn * 25.4;

        double requiredShimIn = Math.abs(errorIn) * pa.carrierShimFactor;
        res.requiredShimChangeIn = requiredShimIn;
        res.requiredShimChangeMm = requiredShimIn * 25.4;

        if (measuredIn >= targetMinIn && measuredIn <= targetMaxIn) {
            res.isInSpec = true;
            res.isTight = false;
            res.isLoose = false;
            res.statusBadge = "IN SPEC";
            res.actionInstruction = "Optimal tooth mesh. No carrier shim changes required.";
        } else if (measuredIn < targetMinIn) {
            res.isInSpec = false;
            res.isTight = true;
            res.isLoose = false;
            res.statusBadge = "TIGHT / BINDING";
            res.actionInstruction = String.format(Locale.US,
                "Move ring gear AWAY from pinion: Remove %.4f\" (%.3f mm) from Left shim, Add to Right.",
                requiredShimIn, res.requiredShimChangeMm);
        } else {
            res.isInSpec = false;
            res.isTight = false;
            res.isLoose = true;
            res.statusBadge = "EXCESSIVE / LOOSE";
            res.actionInstruction = String.format(Locale.US,
                "Move ring gear CLOSER to pinion: Add %.4f\" (%.3f mm) to Left shim, Remove from Right.",
                requiredShimIn, res.requiredShimChangeMm);
        }

        // Calculate dial needle angle (0 = 12 o'clock, 0.020" = full 360 deg)
        double dialFraction = Math.min(1.0, Math.max(0.0, measuredIn / 0.025));
        res.dialNeedleAngle = (float) (dialFraction * 360.0);

        return res;
    }

    /**
     * Computes cold solid-lifter valve lash staging from hot manufacturer specs.
     */
    public static ValveLashResult computeColdValveLash(
            double hotSpec,
            boolean isInputInInches,
            Metallurgy metallurgy,
            boolean isAlumPushrods) {

        ValveLashResult res = new ValveLashResult();

        if (Double.isNaN(hotSpec) || Double.isInfinite(hotSpec) || hotSpec <= 0) {
            hotSpec = isInputInInches ? 0.022 : 0.55;
        }
        if (metallurgy == null) metallurgy = Metallurgy.MIXED_IRON_ALUM;

        double hotIn = isInputInInches ? hotSpec : (hotSpec / 25.4);
        res.hotSpecIn = hotIn;
        res.hotSpecMm = hotIn * 25.4;

        double deltaIn = metallurgy.deltaColdIn;
        if (isAlumPushrods && metallurgy == Metallurgy.ALL_ALUMINUM) {
            // Aluminum pushrods expand at similar rate to aluminum block, neutralizing excessive clearance growth
            deltaIn = -0.002;
        }

        double coldIn = Math.max(0.002, hotIn + deltaIn);
        res.coldTargetIn = coldIn;
        res.coldTargetMm = coldIn * 25.4;
        res.expansionDeltaIn = deltaIn;
        res.expansionDeltaMm = deltaIn * 25.4;

        // Go / No-Go feeler gauge recommendations (standard shop feeler step: 0.001" or 0.002")
        res.feelerGoIn = coldIn;
        res.feelerNoGoIn = coldIn + 0.002;
        res.feelerGoMm = coldIn * 25.4;
        res.feelerNoGoMm = (coldIn + 0.002) * 25.4;

        if (deltaIn < 0) {
            res.note = String.format(Locale.US,
                "Engine expands %.4f\" (%.3f mm) hot. Set cold clearance tighter on bench.",
                Math.abs(deltaIn), Math.abs(res.expansionDeltaMm));
        } else {
            res.note = "Clearance remains stable across thermal operating range.";
        }

        return res;
    }

    /**
     * Computes 4-point total indicator reading (TIR) for ring gear wobble / flange runout.
     */
    public static RunoutResult computeTir(double p1, double p2, double p3, double p4, boolean isInch) {
        RunoutResult res = new RunoutResult();
        double[] vals = new double[]{p1, p2, p3, p4};
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        for (double v : vals) {
            if (!Double.isNaN(v) && !Double.isInfinite(v) && v >= 0) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }

        if (min == Double.MAX_VALUE) {
            min = 0;
            max = 0;
        }

        res.minVal = min;
        res.maxVal = max;
        res.tir = max - min;

        double maxTirAllowed = isInch ? 0.0025 : 0.06;
        res.isAcceptable = res.tir <= maxTirAllowed;
        res.status = res.isAcceptable
            ? String.format(Locale.US, "ACCEPTABLE (TIR %.4f%s <= %.4f)", res.tir, isInch ? "\"" : "mm", maxTirAllowed)
            : String.format(Locale.US, "EXCESSIVE WOBBLE (TIR %.4f%s > %.4f)", res.tir, isInch ? "\"" : "mm", maxTirAllowed);

        return res;
    }
}
