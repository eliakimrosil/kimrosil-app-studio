package com.kimrosil.cambershim;

/**
 * Pure trigonometric engine for door hinge geometry, sag clearance deflection,
 * and mortise chisel shim corrections.
 */
public class ShimGeometryCalculator {

    public static class CalculationResult {
        public final double sagAngleDegrees;
        public final double topShimRequiredMm;
        public final double bottomMortiseGainMm;
        public final String bindStatus; // "CLEARANCE", "TIGHT", "HINGE_BOUND"
        public final boolean isExtremeAngle;

        public CalculationResult(double sagAngleDegrees, double topShimRequiredMm, 
                                 double bottomMortiseGainMm, String bindStatus, boolean isExtremeAngle) {
            this.sagAngleDegrees = sagAngleDegrees;
            this.topShimRequiredMm = topShimRequiredMm;
            this.bottomMortiseGainMm = bottomMortiseGainMm;
            this.bindStatus = bindStatus;
            this.isExtremeAngle = isExtremeAngle;
        }
    }

    /**
     * Calculates required hinge shimming based on door geometry.
     * @param doorHeightMm Total door height in millimeters.
     * @param doorWidthMm Total door width in millimeters.
     * @param hingeSpreadMm Distance between top and bottom hinge centers.
     * @param topLatchSagDeflectionMm Measured latch margin sag / drop deflection at the strike jamb.
     */
    public static CalculationResult calculate(double doorHeightMm, double doorWidthMm, 
                                              double hingeSpreadMm, double topLatchSagDeflectionMm) {
        // Defensive bounds protection
        if (doorHeightMm <= 0) doorHeightMm = 2032.0; // 80" default
        if (doorWidthMm <= 0) doorWidthMm = 914.4;   // 36" default
        if (hingeSpreadMm <= 10.0) hingeSpreadMm = doorHeightMm * 0.75; // standard 3-hinge spread default

        // Tangent of sag angle = Deflection / Width
        double tanTheta = topLatchSagDeflectionMm / doorWidthMm;
        double sagAngleRad = Math.atan(tanTheta);
        double sagAngleDeg = Math.toDegrees(sagAngleRad);

        // Required hinge adjustment along hinge spread baseline:
        // delta_shim = hingeSpread * tan(theta)
        double totalHingeDisplacementMm = hingeSpreadMm * tanTheta;

        // Splitting distribution: Top hinge pulls in (wedge behind leaf), 
        // bottom hinge can gain mortise depth relief or half-split.
        double topShimMm = Math.round(totalHingeDisplacementMm * 100.0) / 100.0;
        double bottomMortiseGainMm = Math.round((totalHingeDisplacementMm * 0.5) * 100.0) / 100.0;

        boolean isExtreme = sagAngleDeg > 3.5;

        String status;
        if (topLatchSagDeflectionMm <= 1.5) {
            status = "CLEARANCE";
        } else if (topLatchSagDeflectionMm <= 4.0) {
            status = "TIGHT";
        } else {
            status = "HINGE_BOUND";
        }

        return new CalculationResult(sagAngleDeg, topShimMm, bottomMortiseGainMm, status, isExtreme);
    }
}