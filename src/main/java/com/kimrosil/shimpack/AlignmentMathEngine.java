package com.kimrosil.shimpack;

/**
 * Encapsulates Rim-Face and Reverse Dial geometric calculations,
 * indicator sag deduction, angular coplanar formulas, and base-bound limits.
 */
public class AlignmentMathEngine {

    public static class AlignmentResult {
        public final double frontFootTarget;  // +Add shim, -Remove shim
        public final double backFootTarget;
        public final double angularMisalignment; // Deg or Rad equivalent
        public final double parallelMisalignment;
        public final boolean isWithinTolerance;
        public final String toleranceStatus; // "ACCEPTABLE", "MARGINAL", "SEVERE"

        public AlignmentResult(double f1, double f2, double angle, double offset, boolean withinTol, String status) {
            this.frontFootTarget = f1;
            this.backFootTarget = f2;
            this.angularMisalignment = angle;
            this.parallelMisalignment = offset;
            this.isWithinTolerance = withinTol;
            this.toleranceStatus = status;
        }
    }

    /**
     * Rim-Face Alignment Formula
     * D1 = Coupling center to Front Foot (F1)
     * D2 = Front Foot to Back Foot (F2)
     * dialRadiusOrDiam = Indicator sweep circle diameter (D3)
     * dialRim = Total Indicator Reading (TIR) on Rim (bottom - top reading - sag)
     * dialFace = Total Indicator Reading (TIR) on Face (bottom - top reading)
     */
    public static AlignmentResult solveRimFace(double d1Front, double d2Back, double dialDiam,
                                               double dialRimTir, double dialFaceTir,
                                               int rpm, boolean isMetric) {
        if (dialDiam <= 0.001) dialDiam = 1.0;

        // Axial slope / angular gap per unit diameter
        double faceSlope = dialFaceTir / dialDiam;

        // Front foot correction (Rim offset/2 +/- face angle * distance)
        double rimOffset = dialRimTir / 2.0;
        double f1 = (faceSlope * d1Front) - rimOffset;
        double f2 = (faceSlope * (d1Front + d2Back)) - rimOffset;

        double parallelOffset = Math.abs(rimOffset);
        double angularGap = Math.abs(dialFaceTir);

        // ISO / Industrial RPM-based tolerance limits
        // 3600 RPM: 2.0 mil (0.05mm) rim, 0.5 mil/inch face
        // 1800 RPM: 3.0 mil (0.08mm) rim, 0.7 mil/inch face
        double maxRimTol = isMetric ? 0.08 : 3.0;
        double maxFaceTol = isMetric ? 0.06 : 2.5;

        if (rpm >= 3000) {
            maxRimTol = isMetric ? 0.05 : 2.0;
            maxFaceTol = isMetric ? 0.04 : 1.5;
        }

        boolean withinTol = (parallelOffset <= maxRimTol) && (angularGap <= maxFaceTol);
        String status;
        if (withinTol) {
            status = "ACCEPTABLE";
        } else if (parallelOffset <= maxRimTol * 1.8 && angularGap <= maxFaceTol * 1.8) {
            status = "MARGINAL";
        } else {
            status = "SEVERE";
        }

        double angleDeg = Math.toDegrees(Math.atan(faceSlope));
        return new AlignmentResult(f1, f2, angleDeg, parallelOffset, withinTol, status);
    }

    /**
     * Reverse Indicator (Dual Rim) Alignment Formula
     * dStationaryDial: Indicator on stationary reading movable rim
     * dMovableDial: Indicator on movable reading stationary rim
     */
    public static AlignmentResult solveReverseDial(double d1Front, double d2Back, double indicatorSpan,
                                                  double stationaryRimTir, double movableRimTir,
                                                  int rpm, boolean isMetric) {
        if (indicatorSpan <= 0.001) indicatorSpan = 1.0;

        double offset1 = stationaryRimTir / 2.0;
        double offset2 = movableRimTir / 2.0;

        double angleRatio = (offset1 + offset2) / indicatorSpan;
        double f1 = offset1 + (angleRatio * d1Front);
        double f2 = offset1 + (angleRatio * (d1Front + d2Back));

        double parallelOffset = Math.abs((offset1 + offset2) / 2.0);
        double maxRimTol = isMetric ? 0.07 : 2.5;
        if (rpm >= 3000) maxRimTol = isMetric ? 0.04 : 1.8;

        boolean withinTol = parallelOffset <= maxRimTol;
        String status = withinTol ? "ACCEPTABLE" : (parallelOffset <= maxRimTol * 2.0 ? "MARGINAL" : "SEVERE");

        double angleDeg = Math.toDegrees(Math.atan(angleRatio));
        return new AlignmentResult(f1, f2, angleDeg, parallelOffset, withinTol, status);
    }
}