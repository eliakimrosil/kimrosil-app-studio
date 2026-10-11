package com.kimrosil.turfnotch;

import java.util.Locale;

public class PresetManager {

    public static class NotchResult {
        public final String dialSetting;
        public final String guidance;

        public NotchResult(String dialSetting, String guidance) {
            this.dialSetting = dialSetting;
            this.guidance = guidance;
        }
    }

    /**
     * Computes the recommended dial notch across OEM spreader curves based on lbs/1,000 sq ft.
     * Index 0: Scotts Standard Rotary / EdgeGuard (0 - 18)
     * Index 1: Scotts Drop Spreader (0 - 18)
     * Index 2: Earthway EV-N-SPRED Rotary (0 - 30)
     * Index 3: Chapin Residential Broadcast (0 - 30)
     * Index 4: Agri-Fab Tow Broadcast (0 - 10)
     */
    public static NotchResult calculateNotch(int spreaderIndex, double targetRateLbsPer1k) {
        if (targetRateLbsPer1k <= 0) {
            return new NotchResult("--", "Enter a valid positive application rate");
        }

        switch (spreaderIndex) {
            case 0: { // Scotts Rotary EdgeGuard
                // Baseline: ~3.0 lbs/1k is Notch 4.0; 5.0 lbs/1k is Notch 5.5
                double setting = 2.0 + (targetRateLbsPer1k * 0.7);
                if (setting > 18.0) setting = 18.0;
                String dial = String.format(Locale.US, "%.1f", setting);
                return new NotchResult(dial, "Scotts Rotary: Set dial to " + dial + ". Overlap wheel tracks by 6 inches.");
            }
            case 1: { // Scotts Drop Spreader
                // Drop spreaders have tighter apertures: ~3.0 lbs/1k is Notch 5.5
                double setting = 2.5 + (targetRateLbsPer1k * 0.95);
                if (setting > 18.0) setting = 18.0;
                String dial = String.format(Locale.US, "%.1f", setting);
                return new NotchResult(dial, "Scotts Drop: Set gate to " + dial + ". Ensure zero wheel overlap gap.");
            }
            case 2: { // Earthway Rotary (0 - 30 scale)
                // Baseline: ~3.0 lbs/1k is Notch 13 - 14
                double setting = 6.0 + (targetRateLbsPer1k * 2.5);
                if (setting > 30.0) setting = 30.0;
                int notch = (int) Math.round(setting);
                return new NotchResult(String.valueOf(notch), "Earthway EV-N-SPRED: Set stop plate to notch " + notch + ".");
            }
            case 3: { // Chapin Broadcast (0 - 30 scale)
                double setting = 5.0 + (targetRateLbsPer1k * 2.3);
                if (setting > 30.0) setting = 30.0;
                int notch = (int) Math.round(setting);
                return new NotchResult(String.valueOf(notch), "Chapin Broadcast: Set gate pin to notch " + notch + ".");
            }
            case 4: { // Agri-Fab Tow (0 - 10 scale)
                double setting = 1.5 + (targetRateLbsPer1k * 0.65);
                if (setting > 10.0) setting = 10.0;
                String dial = String.format(Locale.US, "%.1f", setting);
                return new NotchResult(dial, "Agri-Fab Tow: Lock slide lever to indicator " + dial + ".");
            }
            default: {
                return new NotchResult("4.0", "Universal Rotary default setting");
            }
        }
    }
}