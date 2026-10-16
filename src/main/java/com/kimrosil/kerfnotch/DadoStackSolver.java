package com.kimrosil.kerfnotch;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DadoStackSolver {

    public static class ShimEntry {
        public final String name;
        public final double thicknessInch;
        public final String colorName;
        public final int colorHex;
        public int count;

        public ShimEntry(String name, double thicknessInch, String colorName, int colorHex, int count) {
            this.name = name;
            this.thicknessInch = thicknessInch;
            this.colorName = colorName;
            this.colorHex = colorHex;
            this.count = count;
        }
    }

    public static class DadoRecipe {
        public double targetKerfInch;
        public double totalKerfInch;
        public double deviationInch;

        public int outerBladesCount = 2; // Always two outside 1/8" blades
        public int chippers18 = 0;       // 1/8" (0.125)
        public int chippers332 = 0;      // 3/32" (0.09375)
        public int chippers116 = 0;      // 1/16" (0.0625)

        public List<ShimEntry> shims = new ArrayList<>();

        public String getChipperSummary() {
            List<String> list = new ArrayList<>();
            if (chippers18 > 0) list.add(chippers18 + "x 1/8\" (0.125\")");
            if (chippers332 > 0) list.add(chippers332 + "x 3/32\" (0.0938\")");
            if (chippers116 > 0) list.add(chippers116 + "x 1/16\" (0.0625\")");
            if (list.isEmpty()) return "None (outside blades only)";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(" + ");
                sb.append(list.get(i));
            }
            return sb.toString();
        }

        public String getRecipeDescription() {
            return String.format(Locale.US, "Stack: %.4f\" (Chippers: %s, Shims: %d)",
                    totalKerfInch, getChipperSummary(), shims.size());
        }
    }

    // Standard Dado Set Chippers available in modern workshop sets (e.g. Freud, Forrest, CMT)
    private static final double CHIPPER_1_8 = 0.1250;
    private static final double CHIPPER_3_32 = 0.09375;
    private static final double CHIPPER_1_16 = 0.0625;

    // Standard Color-Coded Precision Shims
    private static final double SHIM_20 = 0.0200; // Yellow Plastic
    private static final double SHIM_15 = 0.0150; // Green Plastic
    private static final double SHIM_10 = 0.0100; // Blue Plastic
    private static final double SHIM_5 = 0.0050;  // Red Plastic
    private static final double SHIM_2 = 0.0020;  // Silver / Stainless Steel

    public static DadoRecipe solve(double targetKerfInch) {
        DadoRecipe recipe = new DadoRecipe();
        recipe.targetKerfInch = targetKerfInch;

        // Two outside blades provide 0.250" baseline
        double baseBlades = 0.2500;
        if (targetKerfInch < baseBlades) {
            recipe.totalKerfInch = baseBlades;
            recipe.deviationInch = baseBlades - targetKerfInch;
            return recipe;
        }

        double remainingTarget = targetKerfInch - baseBlades;

        // Greedy Knapsack for Chippers (prioritizing 1/8", then 3/32", then 1/16")
        // Maximum available in standard set: 4x 1/8", 1x 3/32", 1x 1/16"
        int max18 = 5;
        int max332 = 1;
        int max116 = 2;

        double bestChipperSum = 0;
        int bestC18 = 0, bestC332 = 0, bestC116 = 0;
        double minUnderflow = Double.MAX_VALUE;

        for (int c18 = 0; c18 <= max18; c18++) {
            for (int c332 = 0; c332 <= max332; c332++) {
                for (int c116 = 0; c116 <= max116; c116++) {
                    double currentSum = (c18 * CHIPPER_1_8) + (c332 * CHIPPER_3_32) + (c116 * CHIPPER_1_16);
                    if (currentSum <= remainingTarget) {
                        double underflow = remainingTarget - currentSum;
                        if (underflow < minUnderflow) {
                            minUnderflow = underflow;
                            bestChipperSum = currentSum;
                            bestC18 = c18;
                            bestC332 = c332;
                            bestC116 = c116;
                        }
                    }
                }
            }
        }

        recipe.chippers18 = bestC18;
        recipe.chippers332 = bestC332;
        recipe.chippers116 = bestC116;

        double remainingForShims = remainingTarget - bestChipperSum;

        // Allocate Precision Color-Coded Shims
        int count20 = 0, count15 = 0, count10 = 0, count5 = 0, count2 = 0;

        while (remainingForShims >= SHIM_20 - 0.0005 && count20 < 4) {
            count20++;
            remainingForShims -= SHIM_20;
        }
        while (remainingForShims >= SHIM_15 - 0.0005 && count15 < 2) {
            count15++;
            remainingForShims -= SHIM_15;
        }
        while (remainingForShims >= SHIM_10 - 0.0005 && count10 < 3) {
            count10++;
            remainingForShims -= SHIM_10;
        }
        while (remainingForShims >= SHIM_5 - 0.0005 && count5 < 3) {
            count5++;
            remainingForShims -= SHIM_5;
        }
        while (remainingForShims >= SHIM_2 - 0.0005 && count2 < 4) {
            count2++;
            remainingForShims -= SHIM_2;
        }

        if (count20 > 0) {
            recipe.shims.add(new ShimEntry("0.020\" Shim", SHIM_20, "Yellow", Color.parseColor("#EAB308"), count20));
        }
        if (count15 > 0) {
            recipe.shims.add(new ShimEntry("0.015\" Shim", SHIM_15, "Green", Color.parseColor("#22C55E"), count15));
        }
        if (count10 > 0) {
            recipe.shims.add(new ShimEntry("0.010\" Shim", SHIM_10, "Blue", Color.parseColor("#3B82F6"), count10));
        }
        if (count5 > 0) {
            recipe.shims.add(new ShimEntry("0.005\" Shim", SHIM_5, "Red", Color.parseColor("#EF4444"), count5));
        }
        if (count2 > 0) {
            recipe.shims.add(new ShimEntry("0.002\" Shim", SHIM_2, "Silver", Color.parseColor("#CBD5E1"), count2));
        }

        double totalShims = (count20 * SHIM_20) + (count15 * SHIM_15) + (count10 * SHIM_10) + (count5 * SHIM_5) + (count2 * SHIM_2);
        recipe.totalKerfInch = baseBlades + bestChipperSum + totalShims;
        recipe.deviationInch = recipe.totalKerfInch - targetKerfInch;

        return recipe;
    }
}