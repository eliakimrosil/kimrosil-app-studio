package com.kimrosil.tapdrill;

import java.util.Locale;

public class SpeedsFeedsEngine {

    public enum WorkpieceMaterial {
        ALUMINUM_6061("6061-T6 Aluminum", 350.0, 0.004),
        STEEL_1018("1018 Mild Steel", 100.0, 0.003),
        STAINLESS_304("304 Stainless", 55.0, 0.002),
        CAST_IRON("Grey Cast Iron", 80.0, 0.003),
        BRASS_360("360 Brass", 250.0, 0.004),
        TITANIUM_TI6AL4V("Titanium Ti-6Al-4V", 45.0, 0.0015),
        TOOL_STEEL_D2("D2 Tool Steel", 50.0, 0.002);

        public final String displayName;
        public final double baseSfmHss;
        public final double iptFeed;

        WorkpieceMaterial(String name, double sfmHss, double ipt) {
            this.displayName = name;
            this.baseSfmHss = sfmHss;
            this.iptFeed = ipt;
        }
    }

    public enum ToolMaterial {
        HSS("High-Speed Steel (HSS)", 1.0),
        COBALT("M42 Cobalt", 1.35),
        CARBIDE("Solid Carbide", 3.0);

        public final String displayName;
        public final double sfmMultiplier;

        ToolMaterial(String name, double mult) {
            this.displayName = name;
            this.sfmMultiplier = mult;
        }
    }

    public static class CalculationResult {
        public final int spindleRpm;
        public final double feedIpm;
        public final double feedMmpm;
        public final double sfm;
        public final double depthCompInch;
        public final int maxSafeRpm;
        public final double tip118CompInch;
        public final double tip135CompInch;
        public final double recommendedBlindDepthInch;

        public CalculationResult(int rpm, double ipm, double mmpm, double sfm,
                                 double comp, int maxRpm,
                                 double tip118, double tip135, double blindDepth) {
            this.spindleRpm = rpm;
            this.feedIpm = ipm;
            this.feedMmpm = mmpm;
            this.sfm = sfm;
            this.depthCompInch = comp;
            this.maxSafeRpm = maxRpm;
            this.tip118CompInch = tip118;
            this.tip135CompInch = tip135;
            this.recommendedBlindDepthInch = blindDepth;
        }
    }

    public static double getSfm(WorkpieceMaterial wm, ToolMaterial tm) {
        if (wm == null || tm == null) return 100.0;
        return wm.baseSfmHss * tm.sfmMultiplier;
    }

    public static CalculationResult compute(WorkpieceMaterial wm, ToolMaterial tm, double diameterInch, boolean drillPointComp) {
        return compute(wm, tm, diameterInch, drillPointComp, 0.0, 0.050);
    }

    public static CalculationResult compute(WorkpieceMaterial wm, ToolMaterial tm, double diameterInch,
                                            boolean drillPointComp, double desiredThreadDepthInch, double pitchInch) {
        double dia = Math.max(0.010, Math.min(12.0, diameterInch));
        double effectiveSfm = getSfm(wm, tm);

        // RPM formula: (SFM * 3.82) / Diameter
        int rpm = (int) Math.round((effectiveSfm * 3.82) / dia);
        if (rpm < 50) rpm = 50;
        if (rpm > 35000) rpm = 35000;

        // Feed: RPM * Flutes (assumed 2 for standard drill) * IPT
        int flutes = 2;
        double ipm = rpm * flutes * (wm != null ? wm.iptFeed : 0.003);
        double mmpm = ipm * 25.4;

        // Drill point geometry allowances:
        // 118° drill point tip height: L_point = (dia / 2) / tan(59°) = dia * 0.300
        // 135° drill point tip height: L_point = (dia / 2) / tan(67.5°) = dia * 0.207
        double tip118 = 0.300 * dia;
        double tip135 = 0.207 * dia;

        double depthComp = drillPointComp ? tip118 : 0.0;

        // Blind-hole bottoming allowance:
        // Tap chamfer clearance (approx 3 to 4 pitches) + tip height + chip pocket safety (0.040" min)
        double p = pitchInch > 0 ? pitchInch : 0.050;
        double tapChamferRunout = 3.5 * p;
        double baseDepth = desiredThreadDepthInch > 0 ? desiredThreadDepthInch : (dia * 1.5);
        double blindDepth = baseDepth + tapChamferRunout + (drillPointComp ? tip118 : tip135) + 0.040;

        // Maximum safe RPM before tool thermal breakdown
        int maxSafeRpm = (int) (rpm * 1.35);

        return new CalculationResult(rpm, ipm, mmpm, effectiveSfm, depthComp, maxSafeRpm, tip118, tip135, blindDepth);
    }

    public static String generateGCodeSnippet(String nominal, String rpm, String ipm, double pitchInch) {
        return String.format(Locale.US,
                "( TapDrill Precision Spec: %s )\n( Rigid Tap / Drill Cycle - G90 Absolute / G20 Inch )\nG90 G94 G17 G20\nS%s M03\nG00 Z1.0000\nG00 X0.000 Y0.000\nG01 Z0.1000 F50.0\nG84 Z-0.7500 R0.1000 F%.4f\nG80\nG00 Z2.0000\nM05\nM30",
                nominal, rpm.replace(" RPM", "").trim(), (pitchInch > 0 ? pitchInch : 0.050));
    }
}