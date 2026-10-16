package com.kimrosil.flaregage;

import java.io.Serializable;
import java.util.Locale;

public class FlareSpec implements Serializable {

    public enum Standard {
        SAE_45_SINGLE("45° SAE Single", 45, "HVAC / Refrigeration linesets & brass flare fittings"),
        SAE_45_DOUBLE("45° SAE Double", 45, "Automotive hydraulic brake circuits & high-pressure fuel"),
        AN_37_JIC("37° AN / JIC", 37, "Motorsport, aviation, aerospace & industrial hydraulics"),
        DIN_BUBBLE("DIN / ISO Bubble", 90, "European automotive brake lines (VW, BMW, Audi, Mercedes)");

        public final String displayName;
        public final int angleDeg;
        public final String description;

        Standard(String displayName, int angleDeg, String description) {
            this.displayName = displayName;
            this.angleDeg = angleDeg;
            this.description = description;
        }
    }

    public enum TubingSize {
        SIZE_3_16("3/16\"", 4.76, "4.76 mm"),
        SIZE_1_4("1/4\"", 6.35, "6.35 mm"),
        SIZE_5_16("5/16\"", 7.94, "7.94 mm"),
        SIZE_3_8("3/8\"", 9.53, "9.53 mm"),
        SIZE_1_2("1/2\"", 12.70, "12.70 mm"),
        SIZE_5_8("5/8\"", 15.88, "15.88 mm"),
        SIZE_3_4("3/4\"", 19.05, "19.05 mm");

        public final String fraction;
        public final double metricMm;
        public final String metricStr;

        TubingSize(String fraction, double metricMm, String metricStr) {
            this.fraction = fraction;
            this.metricMm = metricMm;
            this.metricStr = metricStr;
        }
    }

    public enum Material {
        SOFT_COPPER("Soft Copper", "Refrigeration / Lineset", false, "Clean deburr inside & out. Place 1 drop of compatible POE/mineral oil on flaring cone to prevent galling."),
        HARD_COPPER("Hard Copper", "Plumbing Type L/K", true, "CRITICAL: Hard drawn copper must be annealed to dull cherry red with torch and quenched before flaring, or it will split immediately!"),
        CUNI("CuNi (Cunifer)", "Brake / Fuel 90/10", false, "Ideal brake line alloy. Seamlessly bends and flares smoothly with standard hand flaring yoke. Zero corrosion."),
        STEEL_BUNDY("Steel Bundy", "OEM Automotive", false, "Double-flare adapter required. Ensure tube is clamped perfectly square in flaring block with zero slip."),
        STAINLESS_304("304 Stainless", "Aerospace / Racing", false, "Extremely tough work-hardening alloy. Requires 37° AN tool or hydraulic flaring clamp with extreme-pressure anti-seize paste."),
        ALUMINUM("Aluminum", "Air / Low-Pressure", false, "Soft alloy. Do not over-torque; excessive nut torque will crush the flare cone into the fitting threads.");

        public final String displayName;
        public final String application;
        public final boolean requiresAnneal;
        public final String tip;

        Material(String displayName, String application, boolean requiresAnneal, String tip) {
            this.displayName = displayName;
            this.application = application;
            this.requiresAnneal = requiresAnneal;
            this.tip = tip;
        }
    }

    public static class SpecData {
        public final double flareOdMinMm;
        public final double flareOdMaxMm;
        public final double flareOdNominalMm;
        public final double stickoutMinMm;
        public final double stickoutMaxMm;
        public final String stickoutFraction;
        public final String threadSpec;
        public final int torqueMinNm;
        public final int torqueMaxNm;
        public final int torqueMinFtLb;
        public final int torqueMaxFtLb;
        public final String fftTurns;
        public final int maxPressurePsi;

        public SpecData(double minMm, double maxMm, double stickMin, double stickMax, String stickFrac,
                        String thread, int tNmMin, int tNmMax, int tFtMin, int tFtMax, String fft, int psi) {
            this.flareOdMinMm = minMm;
            this.flareOdMaxMm = maxMm;
            this.flareOdNominalMm = (minMm + maxMm) / 2.0;
            this.stickoutMinMm = stickMin;
            this.stickoutMaxMm = stickMax;
            this.stickoutFraction = stickFrac;
            this.threadSpec = thread;
            this.torqueMinNm = tNmMin;
            this.torqueMaxNm = tNmMax;
            this.torqueMinFtLb = tFtMin;
            this.torqueMaxFtLb = tFtMax;
            this.fftTurns = fft;
            this.maxPressurePsi = psi;
        }
    }

    public static class VerificationResult {
        public enum Status { PASS, UNDERSIZED, OVERSIZED, MARGINAL }

        public final Status status;
        public final double measuredMm;
        public final double nominalMm;
        public final double deltaMm;
        public final String headline;
        public final String detail;
        public final int badgeColor;

        public VerificationResult(Status status, double measuredMm, double nominalMm, double deltaMm,
                                  String headline, String detail, int badgeColor) {
            this.status = status;
            this.measuredMm = measuredMm;
            this.nominalMm = nominalMm;
            this.deltaMm = deltaMm;
            this.headline = headline;
            this.detail = detail;
            this.badgeColor = badgeColor;
        }
    }

    public static SpecData getSpec(Standard standard, TubingSize size, Material material) {
        double minMm;
        double maxMm;
        double stickMin;
        double stickMax;
        String stickFrac;
        String thread;
        int tNmMin;
        int tNmMax;
        int tFtMin;
        int tFtMax;
        String fft;
        int psi;

        switch (size) {
            case SIZE_3_16:
                if (standard == Standard.DIN_BUBBLE) {
                    minMm = 7.00; maxMm = 7.30;
                    stickMin = 1.4; stickMax = 1.8; stickFrac = "1/16\"";
                    thread = "M10 x 1.0 (Metric)";
                } else if (standard == Standard.AN_37_JIC) {
                    minMm = 6.80; maxMm = 7.20;
                    stickMin = 1.3; stickMax = 1.7; stickFrac = "1/16\"";
                    thread = "3/8\"-24 UNF (AN3)";
                } else {
                    minMm = 7.10; maxMm = 7.50;
                    stickMin = 1.5; stickMax = 2.0; stickFrac = "1/16\"";
                    thread = "3/8\"-24 UNF";
                }
                tNmMin = 14; tNmMax = 18;
                tFtMin = 10; tFtMax = 13;
                fft = "1/6 Turn (1 Flat)";
                psi = 2500;
                break;

            case SIZE_1_4:
                if (standard == Standard.DIN_BUBBLE) {
                    minMm = 8.50; maxMm = 8.90;
                    stickMin = 1.6; stickMax = 2.0; stickFrac = "5/64\"";
                    thread = "M12 x 1.0";
                } else if (standard == Standard.AN_37_JIC) {
                    minMm = 8.60; maxMm = 9.10;
                    stickMin = 1.6; stickMax = 2.0; stickFrac = "5/64\"";
                    thread = "7/16\"-20 UNF (AN4)";
                } else {
                    minMm = 8.80; maxMm = 9.30;
                    stickMin = 1.8; stickMax = 2.3; stickFrac = "5/64\"";
                    thread = "7/16\"-20 UNF";
                }
                tNmMin = 16; tNmMax = 20;
                tFtMin = 12; tFtMax = 15;
                fft = "1/6 Turn (1 Flat)";
                psi = 2000;
                break;

            case SIZE_5_16:
                if (standard == Standard.AN_37_JIC) {
                    minMm = 10.20; maxMm = 10.70;
                    stickMin = 1.8; stickMax = 2.2; stickFrac = "3/32\"";
                    thread = "1/2\"-20 UNF (AN5)";
                } else {
                    minMm = 10.40; maxMm = 10.90;
                    stickMin = 2.0; stickMax = 2.5; stickFrac = "3/32\"";
                    thread = "1/2\"-20 UNF";
                }
                tNmMin = 24; tNmMax = 30;
                tFtMin = 18; tFtMax = 22;
                fft = "1/6 Turn (1 Flat)";
                psi = 1800;
                break;

            case SIZE_3_8:
                if (standard == Standard.AN_37_JIC) {
                    minMm = 12.10; maxMm = 12.70;
                    stickMin = 2.0; stickMax = 2.5; stickFrac = "7/64\"";
                    thread = "9/16\"-18 UNF (AN6)";
                } else {
                    minMm = 12.40; maxMm = 13.00;
                    stickMin = 2.2; stickMax = 2.8; stickFrac = "7/64\"";
                    thread = "5/8\"-18 UNF";
                }
                tNmMin = 35; tNmMax = 44;
                tFtMin = 26; tFtMax = 32;
                fft = "1/6 to 1/4 Turn";
                psi = 1600;
                break;

            case SIZE_1_2:
                if (standard == Standard.AN_37_JIC) {
                    minMm = 15.60; maxMm = 16.20;
                    stickMin = 2.3; stickMax = 2.8; stickFrac = "1/8\"";
                    thread = "3/4\"-16 UNF (AN8)";
                } else {
                    minMm = 16.00; maxMm = 16.60;
                    stickMin = 2.5; stickMax = 3.2; stickFrac = "1/8\"";
                    thread = "3/4\"-16 UNF";
                }
                tNmMin = 50; tNmMax = 62;
                tFtMin = 37; tFtMax = 46;
                fft = "1/4 Turn";
                psi = 1400;
                break;

            case SIZE_5_8:
                if (standard == Standard.AN_37_JIC) {
                    minMm = 18.80; maxMm = 19.50;
                    stickMin = 2.7; stickMax = 3.4; stickFrac = "9/64\"";
                    thread = "7/8\"-14 UNF (AN10)";
                } else {
                    minMm = 19.30; maxMm = 20.00;
                    stickMin = 3.0; stickMax = 3.8; stickFrac = "9/64\"";
                    thread = "7/8\"-14 UNF";
                }
                tNmMin = 68; tNmMax = 82;
                tFtMin = 50; tFtMax = 60;
                fft = "1/4 Turn";
                psi = 1200;
                break;

            case SIZE_3_4:
            default:
                if (standard == Standard.AN_37_JIC) {
                    minMm = 22.40; maxMm = 23.20;
                    stickMin = 3.2; stickMax = 4.0; stickFrac = "5/32\"";
                    thread = "1-1/16\"-12 UN (AN12)";
                } else {
                    minMm = 23.00; maxMm = 23.80;
                    stickMin = 3.5; stickMax = 4.5; stickFrac = "5/32\"";
                    thread = "1-1/16\"-14 UNS";
                }
                tNmMin = 100; tNmMax = 120;
                tFtMin = 74; tFtMax = 89;
                fft = "1/4 Turn";
                psi = 1000;
                break;
        }

        // Adjust torque for material stiffness
        if (material == Material.STAINLESS_304) {
            tNmMin = (int)(tNmMin * 1.25);
            tNmMax = (int)(tNmMax * 1.25);
            tFtMin = (int)(tFtMin * 1.25);
            tFtMax = (int)(tFtMax * 1.25);
        } else if (material == Material.ALUMINUM) {
            tNmMin = (int)(tNmMin * 0.75);
            tNmMax = (int)(tNmMax * 0.75);
            tFtMin = (int)(tFtMin * 0.75);
            tFtMax = (int)(tFtMax * 0.75);
        }

        return new SpecData(minMm, maxMm, stickMin, stickMax, stickFrac,
                thread, tNmMin, tNmMax, tFtMin, tFtMax, fft, psi);
    }

    public static VerificationResult verifyCaliper(double measuredMm, SpecData spec) {
        double delta = measuredMm - spec.flareOdNominalMm;
        double tol = (spec.flareOdMaxMm - spec.flareOdMinMm) / 2.0;

        if (measuredMm < spec.flareOdMinMm) {
            double under = spec.flareOdMinMm - measuredMm;
            String msg = String.format(Locale.US,
                    "UNDERSIZED (-%.2f mm below min). DANGER: Cone will pull through flare nut under pressure. Reject and reflate with more stickout.",
                    under);
            return new VerificationResult(VerificationResult.Status.UNDERSIZED, measuredMm, spec.flareOdNominalMm,
                    delta, "REJECT: UNDERSIZED", msg, 0xFFDC2626);
        } else if (measuredMm > spec.flareOdMaxMm) {
            double over = measuredMm - spec.flareOdMaxMm;
            String msg = String.format(Locale.US,
                    "OVERSIZED (+%.2f mm above max). DANGER: Head will bind fitting threads, gall brass, and crack lip. Re-cut and reflare.",
                    over);
            return new VerificationResult(VerificationResult.Status.OVERSIZED, measuredMm, spec.flareOdNominalMm,
                    delta, "REJECT: OVERSIZED", msg, 0xFFDC2626);
        } else {
            // Check if within marginal 15% edge
            double margin = tol * 0.20;
            if (measuredMm <= (spec.flareOdMinMm + margin) || measuredMm >= (spec.flareOdMaxMm - margin)) {
                String msg = String.format(Locale.US,
                        "MARGINAL FIT (Δ = %+.2f mm). Within nominal bounds [%.2f - %.2f mm] but close to threshold. Inspect flare lip for splits.",
                        delta, spec.flareOdMinMm, spec.flareOdMaxMm);
                return new VerificationResult(VerificationResult.Status.MARGINAL, measuredMm, spec.flareOdNominalMm,
                        delta, "PASS: MARGINAL LIMIT", msg, 0xFFD97706);
            } else {
                String msg = String.format(Locale.US,
                        "PERFECT SPEC (Δ = %+.2f mm). Dead-center within tolerance [%.2f - %.2f mm]. Safe to torque to %d - %d N·m.",
                        delta, spec.flareOdMinMm, spec.flareOdMaxMm, spec.torqueMinNm, spec.torqueMaxNm);
                return new VerificationResult(VerificationResult.Status.PASS, measuredMm, spec.flareOdNominalMm,
                        delta, "PASS: IN TOLERANCE", msg, 0xFF15803D);
            }
        }
    }
}
