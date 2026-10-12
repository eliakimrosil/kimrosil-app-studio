package com.kimrosil.flangematch;

import java.util.ArrayList;
import java.util.List;

public class TorquePatternCalculator {

    /**
     * Standard ASME PCC-1 / Industrial criss-cross (star) bolt numbering sequence.
     * All bolt indices returned are 1-based (from 1 to N, ordered clockwise on the flange).
     */
    public static int[] getStarSequence(int boltCount) {
        switch (boltCount) {
            case 4:
                return new int[]{1, 3, 2, 4};
            case 8:
                return new int[]{1, 5, 3, 7, 2, 6, 4, 8};
            case 12:
                return new int[]{1, 7, 4, 10, 2, 8, 5, 11, 3, 9, 6, 12};
            case 16:
                return new int[]{1, 9, 5, 13, 3, 11, 7, 15, 2, 10, 6, 14, 4, 12, 8, 16};
            case 20:
                return new int[]{1, 11, 6, 16, 3, 13, 8, 18, 5, 15, 10, 20, 2, 12, 7, 17, 4, 14, 9, 19};
            case 24:
                return new int[]{
                    1, 13, 7, 19, 4, 16, 10, 22,
                    2, 14, 8, 20, 5, 17, 11, 23,
                    3, 15, 9, 21, 6, 18, 12, 24
                };
            default:
                // Universal cross-diameter fallback generator for uncommon bolt counts
                return generateFallbackSequence(boltCount);
        }
    }

    private static int[] generateFallbackSequence(int n) {
        if (n <= 2) {
            int[] seq = new int[n];
            for (int i = 0; i < n; i++) seq[i] = i + 1;
            return seq;
        }
        List<Integer> list = new ArrayList<>(n);
        boolean[] used = new boolean[n + 1];
        int half = n / 2;

        for (int i = 1; i <= half; i++) {
            if (!used[i]) {
                list.add(i);
                used[i] = true;
            }
            int opposite = i + half;
            if (opposite <= n && !used[opposite]) {
                list.add(opposite);
                used[opposite] = true;
            }
        }
        for (int i = 1; i <= n; i++) {
            if (!used[i]) {
                list.add(i);
            }
        }

        int[] result = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    /**
     * Recommended torque stages according to ASME PCC-1:
     * Pass 1: 30% of target torque (Criss-cross pattern)
     * Pass 2: 60% of target torque (Criss-cross pattern)
     * Pass 3: 100% of target torque (Criss-cross pattern)
     * Pass 4: 100% final check (Rotational clockwise pattern around flange)
     */
    public static double getTorqueForPass(int passIndex, double targetTorque) {
        switch (passIndex) {
            case 0: // Pass 1
                return Math.round(targetTorque * 0.30 * 10.0) / 10.0;
            case 1: // Pass 2
                return Math.round(targetTorque * 0.60 * 10.0) / 10.0;
            case 2: // Pass 3
            case 3: // Pass 4 (Rotational)
            default:
                return Math.round(targetTorque * 1.00 * 10.0) / 10.0;
        }
    }

    public static int getPassPercentage(int passIndex) {
        switch (passIndex) {
            case 0: return 30;
            case 1: return 60;
            case 2: return 100;
            case 3: return 100;
            default: return 100;
        }
    }

    public static String getPassDescription(int passIndex) {
        switch (passIndex) {
            case 0:
                return "Pass 1: 30% Target (Star Sequence)";
            case 1:
                return "Pass 2: 60% Target (Star Sequence)";
            case 2:
                return "Pass 3: 100% Target (Star Sequence)";
            case 3:
                return "Pass 4: 100% Final Check (Rotational CW)";
            default:
                return "Pass " + (passIndex + 1);
        }
    }

    public static boolean isRotationalPass(int passIndex) {
        return passIndex == 3;
    }

    /**
     * Calculate adjusted torque based on thread friction coefficient (k-factor).
     * Industry baseline ASTM torque is based on k = 0.13 (copper anti-seize / standard lubricated).
     */
    public static double calculateLubricatedTorque(double baseTorque, double kFactor) {
        double ratio = kFactor / 0.13;
        return Math.round(baseTorque * ratio * 10.0) / 10.0;
    }
}