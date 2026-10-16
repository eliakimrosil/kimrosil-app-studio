package com.kimrosil.kerfnotch;

public class BoxJointCalculator {

    public static class BoxJointResult {
        public double boardWidthInch;
        public double bladeKerfInch;
        public int pinCount;
        public int socketCount;
        public double fingerWidthInch;
        public int passesPerSocket;
        public double stepOffDistanceInch;
    }

    public static BoxJointResult calculate(double boardWidthInch, double bladeKerfInch) {
        BoxJointResult result = new BoxJointResult();
        result.boardWidthInch = Math.max(0.5, boardWidthInch);
        result.bladeKerfInch = Math.max(0.0625, bladeKerfInch);

        // Target standard finger width ~0.250" (1/4") or blade kerf if larger
        double idealFingerWidth = Math.max(0.250, result.bladeKerfInch);

        // Total fingers must be odd so outer edges both start and terminate with symmetrical male pins
        int rawFingers = (int) Math.round(result.boardWidthInch / idealFingerWidth);
        if (rawFingers % 2 == 0) {
            rawFingers += 1;
        }
        if (rawFingers < 3) rawFingers = 3;

        result.pinCount = (rawFingers / 2) + 1;
        result.socketCount = rawFingers / 2;

        result.fingerWidthInch = result.boardWidthInch / rawFingers;
        result.passesPerSocket = (int) Math.ceil(result.fingerWidthInch / result.bladeKerfInch);
        result.stepOffDistanceInch = result.fingerWidthInch * 2.0;

        return result;
    }
}