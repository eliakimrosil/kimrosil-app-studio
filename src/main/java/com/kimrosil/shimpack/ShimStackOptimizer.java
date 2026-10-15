package com.kimrosil.shimpack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded knapsack / change-making optimizer to determine the minimal
 * combination of physical slotted shims (capped at max 4 shims to prevent soft-foot elasticity).
 * Supports stock depletion exclusion where millwrights flag missing inventory thicknesses.
 */
public class ShimStackOptimizer {

    // Standard Industrial Stainless Pre-cut Shim Stock in Inches (mils / 1000)
    public static final double[] IMPERIAL_STOCK = {
            0.125, 0.100, 0.075, 0.050, 0.040, 0.030, 0.025, 0.020, 0.015, 0.010, 0.005, 0.004, 0.003, 0.002, 0.001
    };

    // Standard Industrial Metric Pre-cut Shim Stock in Millimeters (mm)
    public static final double[] METRIC_STOCK = {
            3.00, 2.00, 1.00, 0.80, 0.70, 0.50, 0.40, 0.30, 0.25, 0.20, 0.10, 0.05
    };

    public static class StackSolution {
        public final List<Double> shims;
        public final double totalThickness;
        public final double residualError;
        public final boolean softFootRisk; // True if > 4 shims needed or residual exceeds tolerance

        public StackSolution(List<Double> shims, double total, double error, boolean risk) {
            this.shims = shims;
            this.totalThickness = total;
            this.residualError = error;
            this.softFootRisk = risk;
        }

        public String formatRecipe(boolean isMetric) {
            if (shims == null || shims.isEmpty()) return "[No Shims Required]";
            StringBuilder sb = new StringBuilder();
            sb.append("[ ");
            for (int i = 0; i < shims.size(); i++) {
                if (i > 0) sb.append(" + ");
                if (isMetric) {
                    sb.append(String.format("%.2f mm", shims.get(i)));
                } else {
                    sb.append(String.format("%.3f\"", shims.get(i)));
                }
            }
            sb.append(" ]");
            return sb.toString();
        }
    }

    public static StackSolution solve(double targetThickness, boolean isMetric) {
        return solve(targetThickness, isMetric, null);
    }

    public static StackSolution solve(double targetThickness, boolean isMetric, Set<Double> depletedStock) {
        double absTarget = Math.abs(targetThickness);
        double[] allStock = isMetric ? METRIC_STOCK : IMPERIAL_STOCK;
        double maxAcceptableError = isMetric ? 0.02 : 0.0006;

        if (absTarget < maxAcceptableError) {
            return new StackSolution(new ArrayList<Double>(), 0.0, 0.0, false);
        }

        // Filter out depleted inventory stock
        List<Double> availableList = new ArrayList<Double>();
        for (double s : allStock) {
            boolean isDepleted = false;
            if (depletedStock != null && !depletedStock.isEmpty()) {
                for (Double d : depletedStock) {
                    if (Math.abs(d - s) < 0.00001) {
                        isDepleted = true;
                        break;
                    }
                }
            }
            if (!isDepleted) {
                availableList.add(s);
            }
        }

        if (availableList.isEmpty()) {
            return new StackSolution(new ArrayList<Double>(), 0.0, absTarget, true);
        }

        List<Double> bestCombo = new ArrayList<Double>();
        double remaining = absTarget;

        // Bounded greedy branch for minimal shims (max 4 layers)
        for (double shim : availableList) {
            while (remaining >= shim - maxAcceptableError && bestCombo.size() < 4) {
                bestCombo.add(shim);
                remaining -= shim;
                if (Math.abs(remaining) <= maxAcceptableError) break;
            }
            if (bestCombo.size() >= 4 || remaining <= maxAcceptableError) break;
        }

        // Calculate sum
        double total = 0.0;
        for (double d : bestCombo) total += d;
        double residual = Math.abs(absTarget - total);
        boolean softFootRisk = bestCombo.size() >= 4 && residual > maxAcceptableError;

        Collections.sort(bestCombo, Collections.reverseOrder());
        return new StackSolution(bestCombo, total, residual, softFootRisk);
    }
}