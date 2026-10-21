package com.kimrosil.swagedie;

import java.util.ArrayList;
import java.util.List;

public class EngineeringEngine {

    public static final double MM_TO_INCH = 0.0393700787;
    public static final double INCH_TO_MM = 25.4;
    public static final double METRIC_TON_TO_US_TON = 1.10231;
    public static final double US_TON_TO_METRIC_TON = 0.907185;
    public static final double MPA_TO_PSI = 145.038;
    public static final double GRAVITY = 9.80665; // m/s^2 for N -> kgf conversion

    // Shape constants
    public static final int SHAPE_ROUND = 0;
    public static final int SHAPE_RECTANGLE = 1;
    public static final int SHAPE_SQUARE = 2;
    public static final int SHAPE_CUSTOM_PERIMETER = 3;

    // Swage Modes
    public static final int SWAGE_TUBE = 0;
    public static final int SWAGE_SOLID_ROD = 1;

    public static class MaterialPreset {
        public final String id;
        public final String name;
        public final double shearStrengthMpa; // N/mm^2
        public final double tensileStrengthMpa; // UTS
        public final double yieldStrengthMpa;
        public final double defaultClearancePct; // per side % of thickness
        public final double minClearancePct;
        public final double maxClearancePct;

        public MaterialPreset(String id, String name, double shear, double uts, double yield,
                              double defClr, double minClr, double maxClr) {
            this.id = id;
            this.name = name;
            this.shearStrengthMpa = shear;
            this.tensileStrengthMpa = uts;
            this.yieldStrengthMpa = yield;
            this.defaultClearancePct = defClr;
            this.minClearancePct = minClr;
            this.maxClearancePct = maxClr;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class GaugeEntry {
        public final int gauge;
        public final double thicknessMm;
        public final double thicknessInch;

        public GaugeEntry(int gauge, double mm, double inch) {
            this.gauge = gauge;
            this.thicknessMm = mm;
            this.thicknessInch = inch;
        }
    }

    public static class PunchResult {
        public double perimeterMm;
        public double clearancePerSideMm;
        public double totalClearanceMm;
        public double dieOpening1Mm;
        public double dieOpening2Mm;
        public double tonnageMetric;
        public double tonnageUs;
        public double forceKn;
        public double strippingForceMetric;
        public double strippingForceUs;
        public double recommendedDieLandMm;
        public double angularReliefDeg;
    }

    public static class SwageResult {
        public double initialAreaMm2;
        public double finalAreaMm2;
        public double areaReductionPct;
        public double diameterReductionMm;
        public double diameterRatio;
        public double swageForceMetric;
        public double swageForceUs;
        public double swageForceKn;
        public double contactLengthMm;
        public boolean requiresAnneal;
        public String annealMessage;
    }

    public static class BrakeResult {
        public double recommendedVMm;
        public double minInsideRadiusMm;
        public double minFlangeMm;
        public double tonnagePerMeterMetric;
        public double totalTonnageMetric;
        public double totalTonnageUs;
        public double totalForceKn;
    }

    public static List<MaterialPreset> getStandardMaterials() {
        List<MaterialPreset> list = new ArrayList<>();
        list.add(new MaterialPreset("mild_steel", "Mild Steel / CRS (1018 / A36)", 310.0, 420.0, 250.0, 8.0, 7.0, 10.0));
        list.add(new MaterialPreset("stainless_304", "Stainless Steel (304 / 316)", 450.0, 620.0, 290.0, 10.0, 9.0, 12.0));
        list.add(new MaterialPreset("aluminum_5052", "Aluminum 5052-H32 (Forming)", 140.0, 230.0, 190.0, 6.0, 5.0, 8.0));
        list.add(new MaterialPreset("aluminum_6061", "Aluminum 6061-T6 (Structural)", 205.0, 310.0, 275.0, 7.5, 6.0, 9.0));
        list.add(new MaterialPreset("brass_c260", "Brass C26000 (Cartridge)", 270.0, 380.0, 210.0, 7.0, 6.0, 8.5));
        list.add(new MaterialPreset("copper_c110", "Copper C11000 (Electrolytic)", 200.0, 280.0, 180.0, 6.5, 5.5, 8.0));
        list.add(new MaterialPreset("hss_domex", "High Strength Steel (HSS / Domex)", 550.0, 750.0, 600.0, 12.0, 10.0, 15.0));
        list.add(new MaterialPreset("custom", "Custom Shop Material...", 300.0, 400.0, 250.0, 8.0, 5.0, 15.0));
        return list;
    }

    public static final GaugeEntry[] STANDARD_GAUGES = new GaugeEntry[] {
            new GaugeEntry(10, 3.42, 0.1345),
            new GaugeEntry(12, 2.66, 0.1046),
            new GaugeEntry(14, 1.90, 0.0747),
            new GaugeEntry(16, 1.52, 0.0598),
            new GaugeEntry(18, 1.21, 0.0478),
            new GaugeEntry(20, 0.91, 0.0359),
            new GaugeEntry(22, 0.76, 0.0299),
            new GaugeEntry(24, 0.61, 0.0239),
            new GaugeEntry(26, 0.45, 0.0179)
    };

    /**
     * Calculates punch & die clearance and tonnage.
     */
    public static PunchResult calculatePunching(int shapeType, double dim1Mm, double dim2Mm,
                                                double thicknessMm, double shearStrengthMpa,
                                                double clearancePctPerSide) {
        PunchResult res = new PunchResult();
        if (thicknessMm <= 0 || shearStrengthMpa <= 0) {
            return res;
        }

        // Perimeter
        double perimeter = 0;
        double dieOp1 = 0;
        double dieOp2 = 0;

        double clrPerSide = thicknessMm * (clearancePctPerSide / 100.0);
        double totalClr = clrPerSide * 2.0;

        switch (shapeType) {
            case SHAPE_ROUND:
                perimeter = Math.PI * Math.max(0.001, dim1Mm);
                dieOp1 = dim1Mm + totalClr;
                dieOp2 = dieOp1;
                break;
            case SHAPE_RECTANGLE:
                perimeter = 2.0 * (Math.max(0.001, dim1Mm) + Math.max(0.001, dim2Mm));
                dieOp1 = dim1Mm + totalClr;
                dieOp2 = dim2Mm + totalClr;
                break;
            case SHAPE_SQUARE:
                perimeter = 4.0 * Math.max(0.001, dim1Mm);
                dieOp1 = dim1Mm + totalClr;
                dieOp2 = dieOp1;
                break;
            case SHAPE_CUSTOM_PERIMETER:
            default:
                perimeter = Math.max(0.001, dim1Mm);
                dieOp1 = dim1Mm + totalClr;
                dieOp2 = dieOp1;
                break;
        }

        double shearAreaMm2 = perimeter * thicknessMm;
        double forceNewtons = shearAreaMm2 * shearStrengthMpa; // N
        double forceKn = forceNewtons / 1000.0;
        double forceMetricTon = forceKn / GRAVITY;
        double forceUsTon = forceMetricTon * METRIC_TON_TO_US_TON;

        res.perimeterMm = perimeter;
        res.clearancePerSideMm = clrPerSide;
        res.totalClearanceMm = totalClr;
        res.dieOpening1Mm = dieOp1;
        res.dieOpening2Mm = dieOp2;
        res.forceKn = forceKn;
        res.tonnageMetric = forceMetricTon;
        res.tonnageUs = forceUsTon;
        res.strippingForceMetric = forceMetricTon * 0.15; // standard 15% stripping load
        res.strippingForceUs = res.strippingForceMetric * METRIC_TON_TO_US_TON;

        // Recommended straight land: between 2mm and 5mm, approximately 2 * t or 3mm
        res.recommendedDieLandMm = Math.max(2.0, Math.min(5.0, thicknessMm * 1.5));
        res.angularReliefDeg = 0.5; // standard 1/2 degree draft per side

        return res;
    }

    /**
     * Calculates tube or rod swaging reduction and required deformation force.
     */
    public static SwageResult calculateSwaging(int mode, double initialOdMm, double targetOdMm,
                                              double wallThicknessMm, double dieHalfAngleDeg,
                                              double frictionCoeff, double yieldStrengthMpa) {
        SwageResult res = new SwageResult();
        if (initialOdMm <= 0 || targetOdMm <= 0 || initialOdMm <= targetOdMm || yieldStrengthMpa <= 0) {
            return res;
        }

        double initialArea = 0;
        double finalArea = 0;
        double wall = Math.max(0.1, wallThicknessMm);

        if (mode == SWAGE_TUBE) {
            // Check wall thickness sanity
            if (wall >= initialOdMm / 2.0) {
                wall = initialOdMm * 0.2;
            }
            // A0 = pi * (OD - t) * t
            initialArea = Math.PI * (initialOdMm - wall) * wall;
            // In swaging, tube wall slightly thickens: t1 ≈ t0 * sqrt(D0 / D1)
            double targetWall = wall * Math.sqrt(initialOdMm / targetOdMm);
            finalArea = Math.PI * (targetOdMm - targetWall) * targetWall;
        } else {
            // Solid Rod
            initialArea = Math.PI * Math.pow(initialOdMm / 2.0, 2);
            finalArea = Math.PI * Math.pow(targetOdMm / 2.0, 2);
        }

        if (finalArea >= initialArea || finalArea <= 0) {
            return res;
        }

        double reductionRatio = (1.0 - (finalArea / initialArea)) * 100.0;
        double diamReduction = initialOdMm - targetOdMm;
        double diamRatio = initialOdMm / targetOdMm;

        double alphaRad = Math.toRadians(Math.max(1.0, Math.min(45.0, dieHalfAngleDeg)));
        double contactLen = (initialOdMm - targetOdMm) / (2.0 * Math.tan(alphaRad));

        // True plastic strain: epsilon = ln(A0 / A1)
        double trueStrain = Math.log(initialArea / finalArea);

        // Sachs / Avitzur Swaging load model:
        // sigma_mean ≈ yieldStrength * (1.0 + trueStrain / 2.0)
        double flowStress = yieldStrengthMpa * (1.0 + (trueStrain * 0.3));
        double geomFrictionFactor = 1.0 + (frictionCoeff / Math.tan(alphaRad));
        double deformationPressure = flowStress * trueStrain * geomFrictionFactor; // N/mm2

        // Total axial force
        double forceNewtons = finalArea * deformationPressure;
        double forceKn = forceNewtons / 1000.0;
        double forceMetricTon = forceKn / GRAVITY;

        res.initialAreaMm2 = initialArea;
        res.finalAreaMm2 = finalArea;
        res.areaReductionPct = reductionRatio;
        res.diameterReductionMm = diamReduction;
        res.diameterRatio = diamRatio;
        res.contactLengthMm = contactLen;
        res.swageForceKn = forceKn;
        res.swageForceMetric = forceMetricTon;
        res.swageForceUs = forceMetricTon * METRIC_TON_TO_US_TON;

        // Anneal limits: In metal forming, >25% reduction in single pass work hardens stock
        if (reductionRatio > 25.0) {
            res.requiresAnneal = true;
            res.annealMessage = String.format(java.util.Locale.US,
                    "Reduction (%.1f%%) exceeds 25%% single-pass threshold. Intermediate anneal advised.",
                    reductionRatio);
        } else {
            res.requiresAnneal = false;
            res.annealMessage = "Reduction within safe single-pass ductile limits.";
        }

        return res;
    }

    /**
     * Calculates press brake V-die opening, tonnage, and minimum flange.
     */
    public static BrakeResult calculatePressBrake(double thicknessMm, double bendLengthMm,
                                                  double tensileStrengthMpa, double selectedVDiamMm) {
        BrakeResult res = new BrakeResult();
        if (thicknessMm <= 0 || bendLengthMm <= 0 || tensileStrengthMpa <= 0) {
            return res;
        }

        // Recommended V-die width rule: V = 8 * t for normal sheet
        double vDie = selectedVDiamMm;
        if (vDie <= 0) {
            if (thicknessMm < 2.5) {
                vDie = thicknessMm * 6.0;
            } else if (thicknessMm <= 4.0) {
                vDie = thicknessMm * 8.0;
            } else {
                vDie = thicknessMm * 10.0;
            }
        }

        // Standard press brake air bending tonnage formula:
        // Force (kN) = (1.42 * UTS * L * t^2) / (V * 1000)
        double forceKn = (1.42 * tensileStrengthMpa * bendLengthMm * Math.pow(thicknessMm, 2)) / (vDie * 1000.0);
        double forceMetricTon = forceKn / GRAVITY;
        double tonnagePerMeter = (forceMetricTon / bendLengthMm) * 1000.0;

        res.recommendedVMm = vDie;
        res.minInsideRadiusMm = vDie * 0.16;
        res.minFlangeMm = vDie * 0.70;
        res.totalForceKn = forceKn;
        res.totalTonnageMetric = forceMetricTon;
        res.totalTonnageUs = forceMetricTon * METRIC_TON_TO_US_TON;
        res.tonnagePerMeterMetric = tonnagePerMeter;

        return res;
    }
}
