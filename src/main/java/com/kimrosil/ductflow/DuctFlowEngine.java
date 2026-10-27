package com.kimrosil.ductflow;

import java.util.Locale;

public class DuctFlowEngine {

    public static final int MODE_EQUAL_FRICTION = 0;
    public static final int MODE_VELOCITY = 1;

    public static final int MATERIAL_GALVANIZED = 0; // Sheet metal standard (1.0x)
    public static final int MATERIAL_FLEX_DUCT = 1;  // Flexible spiral wire duct (1.8x)

    public static final int UNIT_IMPERIAL = 0; // CFM, in. wg/100', inches, FPM
    public static final int UNIT_METRIC = 1;   // m³/h, Pa/m, mm, m/s

    public static class DuctResult {
        public int mode;
        public int material;
        public int unitSystem;

        public double cfm;
        public double frictionRate; // in. wg / 100 ft
        public double targetVelocityFpm; // FPM (used in velocity mode)
        public double roundDiaInches;
        public double roundDiaRounded;
        public double knownHeightInches;
        public double rectWidthInches;
        public double rectWidthRounded;
        public double roundVelocityFpm;
        public double rectVelocityFpm;
        public double aspectRatio;
        public boolean isAspectSafe; // true if <= 4.0
        public double hydraulicDiaInches;

        // Metric SI equivalents
        public double airflowM3h;
        public double frictionPaPerM;
        public double roundDiaMm;
        public double rectWidthMm;
        public double knownHeightMm;
        public double velocityMps;

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("DuctFlow HVAC Equal Friction Schedule\n");
            sb.append("---------------------------------------\n");
            sb.append(String.format(Locale.US, "Sizing Mode: %s\n", (mode == MODE_VELOCITY) ? "Max Velocity Sizing" : "Equal Friction Sizing"));
            sb.append(String.format(Locale.US, "Duct Material: %s\n", (material == MATERIAL_FLEX_DUCT) ? "Flexible Spiral Duct" : "Galvanized Sheet Metal"));
            sb.append(String.format(Locale.US, "Airflow Volume: %.0f CFM (%.0f m³/h)\n", cfm, airflowM3h));
            sb.append(String.format(Locale.US, "Design Friction Rate: %.3f in. wg/100 ft (%.1f Pa/m)\n", frictionRate, frictionPaPerM));
            sb.append(String.format(Locale.US, "Equivalent Round Duct: %.1f in. / %.0f mm (Nominal %d in.)\n",
                    roundDiaInches, roundDiaMm, (int) Math.round(roundDiaInches)));
            sb.append(String.format(Locale.US, "Rectangular Equivalent: %.1f\" W x %.0f\" H (%.0f x %.0f mm)\n",
                    rectWidthInches, knownHeightInches, rectWidthMm, knownHeightMm));
            sb.append(String.format(Locale.US, "Air Velocity: %.0f FPM (%.2f m/s Rect) / %.0f FPM (Round)\n",
                    rectVelocityFpm, velocityMps, roundVelocityFpm));
            sb.append(String.format(Locale.US, "Aspect Ratio: %.1f:1 (%s)\n",
                    aspectRatio, isAspectSafe ? "Compliant SMACNA standard" : "High aspect ratio warning > 4:1"));
            sb.append(String.format(Locale.US, "Hydraulic Diameter: %.1f in.\n", hydraulicDiaInches));
            sb.append("---------------------------------------\nKimRosil App Studio • DuctFlow");
            return sb.toString();
        }
    }

    /**
     * Solves equivalent round duct diameter using ASHRAE equal friction equation:
     * h_f = 0.109136 * (Q^1.9) / (D^5.02) * roughnessFactor
     * D = (0.109136 * Q^1.9 * roughnessFactor / h_f)^(1 / 5.02)
     */
    public static double calculateRoundDiameterFromFriction(double cfm, double frictionRate, double roughnessFactor) {
        if (cfm <= 0 || frictionRate <= 0) return 0;
        double factor = Math.max(0.5, roughnessFactor);
        double numerator = 0.109136 * Math.pow(cfm, 1.9) * factor;
        return Math.pow(numerator / frictionRate, 1.0 / 5.02);
    }

    /**
     * Solves equivalent round duct diameter from maximum velocity limit:
     * D = sqrt((576 * CFM) / (pi * V))
     */
    public static double calculateRoundDiameterFromVelocity(double cfm, double targetVelocityFpm) {
        if (cfm <= 0 || targetVelocityFpm <= 0) return 0;
        return Math.sqrt((576.0 * cfm) / (Math.PI * targetVelocityFpm));
    }

    /**
     * Computes friction loss rate in in. wg / 100 ft from CFM and diameter:
     * h_f = 0.109136 * (Q^1.9) / (D^5.02) * roughnessFactor
     */
    public static double calculateFrictionFromDiameter(double cfm, double diaInches, double roughnessFactor) {
        if (cfm <= 0 || diaInches <= 0) return 0;
        double factor = Math.max(0.5, roughnessFactor);
        return (0.109136 * Math.pow(cfm, 1.9) * factor) / Math.pow(diaInches, 5.02);
    }

    /**
     * Computes round duct air velocity in FPM:
     * V = CFM / Area(sq ft) = (576 * CFM) / (pi * D^2)
     */
    public static double calculateRoundVelocity(double cfm, double diaInches) {
        if (diaInches <= 0) return 0;
        return (576.0 * cfm) / (Math.PI * diaInches * diaInches);
    }

    /**
     * Solves rectangular duct width (a) using Huebscher's formula:
     * D_e = 1.30 * (a * b)^0.625 / (a + b)^0.25
     * Solved numerically via high-precision binary search.
     */
    public static double calculateRectWidth(double roundDia, double knownHeight) {
        if (roundDia <= 0 || knownHeight <= 0) return 0;

        double low = 1.0;
        double high = 300.0;
        double targetDe = roundDia;

        for (int iter = 0; iter < 50; iter++) {
            double mid = (low + high) / 2.0;
            double currentDe = 1.30 * Math.pow(mid * knownHeight, 0.625) / Math.pow(mid + knownHeight, 0.25);
            if (currentDe < targetDe) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2.0;
    }

    /**
     * Complete solver supporting Equal Friction and Max Velocity modes,
     * material roughness factors, and dual Imperial / Metric SI readouts.
     */
    public static DuctResult calculate(int mode, int material, int unitSystem,
                                       double cfm, double frictionRate, double targetVelocity,
                                       double knownHeight) {
        DuctResult res = new DuctResult();
        res.mode = (mode == MODE_VELOCITY) ? MODE_VELOCITY : MODE_EQUAL_FRICTION;
        res.material = (material == MATERIAL_FLEX_DUCT) ? MATERIAL_FLEX_DUCT : MATERIAL_GALVANIZED;
        res.unitSystem = (unitSystem == UNIT_METRIC) ? UNIT_METRIC : UNIT_IMPERIAL;

        // Defensive input bounds clamping
        res.cfm = Math.min(100000.0, Math.max(10.0, Double.isNaN(cfm) ? 800.0 : cfm));
        double roughnessFactor = (res.material == MATERIAL_FLEX_DUCT) ? 1.80 : 1.00;
        res.knownHeightInches = Math.min(72.0, Math.max(3.0, Double.isNaN(knownHeight) ? 10.0 : knownHeight));

        if (res.mode == MODE_VELOCITY) {
            res.targetVelocityFpm = Math.min(6000.0, Math.max(100.0, Double.isNaN(targetVelocity) ? 1000.0 : targetVelocity));
            res.roundDiaInches = calculateRoundDiameterFromVelocity(res.cfm, res.targetVelocityFpm);
            res.frictionRate = calculateFrictionFromDiameter(res.cfm, res.roundDiaInches, roughnessFactor);
        } else {
            res.frictionRate = Math.min(1.0, Math.max(0.01, Double.isNaN(frictionRate) ? 0.080 : frictionRate));
            res.roundDiaInches = calculateRoundDiameterFromFriction(res.cfm, res.frictionRate, roughnessFactor);
            res.targetVelocityFpm = calculateRoundVelocity(res.cfm, res.roundDiaInches);
        }

        res.roundDiaRounded = Math.round(res.roundDiaInches);
        res.roundVelocityFpm = calculateRoundVelocity(res.cfm, res.roundDiaInches);

        res.rectWidthInches = calculateRectWidth(res.roundDiaInches, res.knownHeightInches);
        res.rectWidthRounded = Math.round(res.rectWidthInches);

        double rectAreaSqFt = (res.rectWidthInches * res.knownHeightInches) / 144.0;
        res.rectVelocityFpm = (rectAreaSqFt > 0) ? res.cfm / rectAreaSqFt : 0;

        double maxDim = Math.max(res.rectWidthInches, res.knownHeightInches);
        double minDim = Math.min(res.rectWidthInches, res.knownHeightInches);
        res.aspectRatio = (minDim > 0) ? (maxDim / minDim) : 1.0;
        res.isAspectSafe = res.aspectRatio <= 4.0;

        double sumDim = res.rectWidthInches + res.knownHeightInches;
        res.hydraulicDiaInches = (sumDim > 0) ? (2.0 * res.rectWidthInches * res.knownHeightInches) / sumDim : 0;

        // Metric conversions
        res.airflowM3h = res.cfm * 1.699011;
        res.frictionPaPerM = res.frictionRate * 8.163;
        res.roundDiaMm = res.roundDiaInches * 25.4;
        res.rectWidthMm = res.rectWidthInches * 25.4;
        res.knownHeightMm = res.knownHeightInches * 25.4;
        res.velocityMps = res.rectVelocityFpm * 0.00508;

        return res;
    }
}
