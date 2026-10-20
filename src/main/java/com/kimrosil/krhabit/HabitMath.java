package com.kimrosil.krhabit;

import java.util.List;
import java.util.Map;

/**
 * HabitMath implements the exponential smoothing habit strength algorithm.
 *
 * It models habit automaticity where consistency builds strength exponentially,
 * and a single missed day causes only partial decay rather than wiping out progress.
 */
public class HabitMath {

    // Default half-life in days (time for habit score to decay by 50% if neglected)
    private static final double DEFAULT_HALF_LIFE = 10.0;

    /**
     * Calculates the exponential habit strength score (0.0 to 1.0)
     * based on chronological completion history.
     *
     * @param history Chronological list of completions (1 = completed, 0 = missed),
     *                where index 0 is oldest and last element is today.
     * @return Habit strength score between 0.0 and 1.0
     */
    public static double computeHabitStrength(List<Boolean> history) {
        if (history == null || history.isEmpty()) {
            return 0.0;
        }

        double alpha = Math.pow(0.5, 1.0 / DEFAULT_HALF_LIFE);
        double strength = 0.0;

        for (Boolean completed : history) {
            double value = (completed != null && completed) ? 1.0 : 0.0;
            strength = strength * alpha + value * (1.0 - alpha);
        }

        return Math.min(1.0, Math.max(0.0, strength));
    }

    /**
     * Returns formatted percentage string for habit strength (e.g. "85%").
     */
    public static String formatStrength(double strength) {
        int percent = (int) Math.round(strength * 100.0);
        return percent + "%";
    }

    /**
     * Computes current consecutive completion streak up to today (or yesterday).
     */
    public static int computeCurrentStreak(List<Boolean> reversedHistory) {
        if (reversedHistory == null || reversedHistory.isEmpty()) {
            return 0;
        }

        int streak = 0;
        boolean first = true;

        for (Boolean completed : reversedHistory) {
            if (completed != null && completed) {
                streak++;
                first = false;
            } else {
                // If today is not checked in yet, don't break streak if yesterday was completed
                if (first) {
                    first = false;
                    continue;
                }
                break;
            }
        }
        return streak;
    }

    /**
     * Computes the longest historical streak.
     */
    public static int computeBestStreak(List<Boolean> chronologicalHistory) {
        if (chronologicalHistory == null || chronologicalHistory.isEmpty()) {
            return 0;
        }

        int best = 0;
        int current = 0;

        for (Boolean completed : chronologicalHistory) {
            if (completed != null && completed) {
                current++;
                if (current > best) {
                    best = current;
                }
            } else {
                current = 0;
            }
        }
        return best;
    }
}
