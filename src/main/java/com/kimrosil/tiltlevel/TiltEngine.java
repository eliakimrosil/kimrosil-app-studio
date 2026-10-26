package com.kimrosil.tiltlevel;

import java.util.Locale;

/**
 * Pure Native 2-Axis Clinometer, Spirit Level, Percent Grade (%), and Roof Pitch (X:12) Engine.
 * Hardened against NaN, Infinity, malformed decimals, negatives, and extreme overflow inputs.
 */
public final class TiltEngine {

    public static final int AXIS_PITCH = 0;
    public static final int AXIS_ROLL = 1;
    public static final int AXIS_COMPOSITE = 2;

    private TiltEngine() {
    }

    public static final class TiltTelemetry {
        public final double rawPitchDeg;
        public final double rawRollDeg;
        public final double tareOffsetDeg;
        public final double netAngleDeg;
        public final double absAngleDeg;
        public final double gradePercent;
        public final double roofPitchX12;
        public final double dropMmPerMeter;
        public final double dropInPerFoot;
        public final double spanMeters;
        public final double spanRiseMm;
        public final double spanRiseInches;
        public final String axisLabel;
        public final String zoneBadge;
        public final String diagnosisText;

        public TiltTelemetry(
                double rawPitchDeg,
                double rawRollDeg,
                double tareOffsetDeg,
                double netAngleDeg,
                double absAngleDeg,
                double gradePercent,
                double roofPitchX12,
                double dropMmPerMeter,
                double dropInPerFoot,
                double spanMeters,
                double spanRiseMm,
                double spanRiseInches,
                String axisLabel,
                String zoneBadge,
                String diagnosisText) {
            this.rawPitchDeg = rawPitchDeg;
            this.rawRollDeg = rawRollDeg;
            this.tareOffsetDeg = tareOffsetDeg;
            this.netAngleDeg = netAngleDeg;
            this.absAngleDeg = absAngleDeg;
            this.gradePercent = gradePercent;
            this.roofPitchX12 = roofPitchX12;
            this.dropMmPerMeter = dropMmPerMeter;
            this.dropInPerFoot = dropInPerFoot;
            this.spanMeters = spanMeters;
            this.spanRiseMm = spanRiseMm;
            this.spanRiseInches = spanRiseInches;
            this.axisLabel = axisLabel;
            this.zoneBadge = zoneBadge;
            this.diagnosisText = diagnosisText;
        }
    }

    /**
     * Parses arbitrary user or fuzzer input safely into a bounded finite double.
     */
    public static double parseSafeDouble(String raw, double fallback, double min, double max) {
        if (raw == null) {
            return clamp(fallback, min, max);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return clamp(fallback, min, max);
        }
        StringBuilder clean = new StringBuilder(trimmed.length());
        boolean seenDot = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= '0' && c <= '9') {
                clean.append(c);
            } else if ((c == '.' || c == ',') && !seenDot) {
                clean.append('.');
                seenDot = true;
            }
        }
        if (clean.length() == 0 || ".".equals(clean.toString())) {
            return clamp(fallback, min, max);
        }
        try {
            double val = Double.parseDouble(clean.toString());
            if (Double.isNaN(val) || Double.isInfinite(val)) {
                return clamp(fallback, min, max);
            }
            return clamp(val, min, max);
        } catch (NumberFormatException e) {
            return clamp(fallback, min, max);
        }
    }

    public static double clamp(double val, double min, double max) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return min;
        }
        if (val < min) {
            return min;
        }
        if (val > max) {
            return max;
        }
        return val;
    }

    public static String getAxisModeName(int axisMode) {
        switch (axisMode) {
            case AXIS_ROLL:
                return "ROLL AXIS";
            case AXIS_COMPOSITE:
                return "2D BULLSEYE";
            case AXIS_PITCH:
            default:
                return "PITCH AXIS";
        }
    }

    public static TiltTelemetry evaluate(
            double rawPitchInput,
            double rawRollInput,
            double tareOffsetInput,
            double spanMetersInput,
            int axisMode) {

        double safePitch = clamp(rawPitchInput, -88.0, 88.0);
        double safeRoll = clamp(rawRollInput, -88.0, 88.0);
        double safeTare = clamp(tareOffsetInput, -45.0, 45.0);
        double safeSpan = clamp(spanMetersInput, 0.10, 500.0);

        double activeRaw;
        if (axisMode == AXIS_ROLL) {
            activeRaw = safeRoll;
        } else if (axisMode == AXIS_COMPOSITE) {
            activeRaw = Math.hypot(safePitch, safeRoll);
        } else {
            activeRaw = safePitch;
        }

        double netAngle = clamp(activeRaw - safeTare, -88.0, 88.0);
        double absAngle = Math.abs(netAngle);

        double rad = Math.toRadians( Math.min(85.0, absAngle) );
        double tanVal = Math.tan(rad);
        double sinVal = Math.sin(rad);

        double gradePct = clamp(tanVal * 100.0, 0.0, 999.9);
        double roofPitch = clamp(tanVal * 12.0, 0.0, 120.0);
        double dropMmPerM = clamp(tanVal * 1000.0, 0.0, 9999.0);
        double dropInPerFt = roofPitch;

        double spanRiseMm = clamp(sinVal * safeSpan * 1000.0, 0.0, 99999.0);
        double spanRiseIn = spanRiseMm / 25.4;

        String badge = classifyZoneBadge(absAngle);
        String diagnosis = buildDiagnosis(absAngle, gradePct, roofPitch, safeSpan, spanRiseMm, spanRiseIn);

        return new TiltTelemetry(
                safePitch,
                safeRoll,
                safeTare,
                netAngle,
                absAngle,
                gradePct,
                roofPitch,
                dropMmPerM,
                dropInPerFt,
                safeSpan,
                spanRiseMm,
                spanRiseIn,
                getAxisModeName(axisMode),
                badge,
                diagnosis
        );
    }

    private static String classifyZoneBadge(double absDeg) {
        if (absDeg <= 0.25) {
            return "TRUE LEVEL";
        } else if (absDeg <= 2.20) {
            return "DRAIN SLOPE";
        } else if (absDeg <= 5.00) {
            return "ADA RAMP SPEC";
        } else if (absDeg <= 22.0) {
            return "GABLE PITCH";
        } else {
            return "STEEP SLOPE";
        }
    }

    private static String buildDiagnosis(
            double absDeg,
            double gradePct,
            double pitchX12,
            double spanM,
            double riseMm,
            double riseIn) {
        String specNote;
        if (absDeg <= 0.25) {
            specNote = "Within machinist precision level tolerance (±0.25°). Ideal for machine beds & cabinetry.";
        } else if (absDeg <= 2.20) {
            specNote = "Positive water runoff grade (1% to 3.8%). Suitable for plumbing lines, patios & trenches.";
        } else if (absDeg <= 5.00) {
            specNote = "Compliant with ADA wheelchair ramp slope limits (max 4.76° / 8.33% / 1:12 rise).";
        } else if (absDeg <= 25.0) {
            specNote = "Standard architectural roof pitch range (2:12 to 5.5:12). Suitable for shingle & metal roofing.";
        } else {
            specNote = "High-angle incline or steep roof rafter cut. Verify safety anchoring and miter bevel.";
        }
        return String.format(
                Locale.US,
                "Tilt %.2f° (%.2f%% Grade • %.2f:12 Pitch) • Span %.2fm Rise: %.0fmm (%.1fin)\n%s",
                absDeg,
                gradePct,
                pitchX12,
                spanM,
                riseMm,
                riseIn,
                specNote
        );
    }
}
