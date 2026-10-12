package com.kimrosil.tapdrill;

import java.util.Locale;

public class SpeedsFeedsEngine {

    public enum WorkpieceMaterial {
        ALUMINUM_6061("6061-T6 Aluminum", 350.0, 0.004),
        STEEL_1018("1018 Mild Steel", 100.0, 0.003),
        STAINLESS_304("304 Stainless", 55.0, 0.002),
        CAST_IRON("Grey Cast Iron", 80.0, 0.003),
        BRASS_360("360 Brass", 250.0, 0.004);

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

        public CalculationResult(int rpm, double ipm, double mmpm, double sfm, double comp, int maxRpm) {
            this.spindleRpm = rpm;
            this.feedIpm = ipm;
            this.feedMmpm = mmpm;
            this.sfm = sfm;
            this.depthCompInch = comp;
            this.maxSafeRpm = maxRpm;
        }
    }

    public static double getSfm(WorkpieceMaterial wm, ToolMaterial tm) {
        if (wm == null || tm == null) return 100.0;
        return wm.baseSfmHss * tm.sfmMultiplier;
    }

    public static CalculationResult compute(WorkpieceMaterial wm, ToolMaterial tm, double diameterInch, boolean drillPointComp) {
        double dia = Math.max(0.010, Math.min(12.0, diameterInch));
        double effectiveSfm = getSfm(wm, tm);

        // RPM formula: (SFM * 3.82) / Diameter
        int rpm = (int) Math.round((effectiveSfm * 3.82) / dia);
        if (rpm < 50) rpm = 50;
        if (rpm > 35000) rpm = 35000;

        // Feed: RPM * Flutes (assumed 2 for standard drill) * IPT
        int flutes = 2;
        double ipm = rpm * flutes * wm.iptFeed;
        double mmpm = ipm * 25.4;

        // Standard 118° drill point allowance: DepthComp = 0.3 * DrillDiameter
        double depthComp = drillPointComp ? (0.300 * dia) : 0.0;

        // Maximum safe RPM before tool thermal breakdown
        int maxSafeRpm = (int) (rpm * 1.35);

        return new CalculationResult(rpm, ipm, mmpm, effectiveSfm, depthComp, maxSafeRpm);
    }

    public static String generateGCodeSnippet(String nominal, String rpm, String ipm, double pitchInch) {
        return String.format(Locale.US,
                "( TapDrill G-Code: %s )\nG90 G94 G17 G20\n( Rigid Tap / Drill Cycle )\nS%s M03\nG00 Z1.0000\nG00 X0.000 Y0.000\nG01 Z0.1000 F50.0\nG84 Z-0.7500 R0.1000 F%.4f\nG80\nG00 Z2.0000\nM05\nM30",
                nominal, rpm.replace(" RPM", "").trim(), (pitchInch > 0 ? pitchInch : 0.050));
    }
}