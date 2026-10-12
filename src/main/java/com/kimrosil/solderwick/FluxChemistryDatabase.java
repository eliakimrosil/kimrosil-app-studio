package com.kimrosil.solderwick;

public class FluxChemistryDatabase {

    public static class FluxSpec {
        public final String designation;
        public final String name;
        public final String activationTempRange;
        public final String solidsPercentage;
        public final String cleaningProtocol;
        public final String typicalUse;

        public FluxSpec(String designation, String name, String activationTempRange, String solidsPercentage, String cleaningProtocol, String typicalUse) {
            this.designation = designation;
            this.name = name;
            this.activationTempRange = activationTempRange;
            this.solidsPercentage = solidsPercentage;
            this.cleaningProtocol = cleaningProtocol;
            this.typicalUse = typicalUse;
        }
    }

    public static final FluxSpec ROSIN_RMA = new FluxSpec(
            "ROL1 / RMA",
            "Rosin Mildly Activated",
            "130°C – 160°C",
            "15% – 25%",
            "99% Isopropyl Alcohol (IPA) scrub recommended",
            "Heavy oxide tinned leads, vintage repair, through-hole wiring"
    );

    public static final FluxSpec NO_CLEAN = new FluxSpec(
            "REL0 / No-Clean",
            "Synthetic Low-Residue",
            "140°C – 180°C",
            "1.5% – 3.5%",
            "Non-corrosive residue; optional IPA cosmetic wipe",
            "Fine-pitch QFP, 0402/0201 SMD, production rework"
    );

    public static final FluxSpec WATER_SOLUBLE = new FluxSpec(
            "ORH1 / Organic Acid",
            "Water-Washable Halide High-Activity",
            "110°C – 150°C",
            "20% – 35%",
            "MANDATORY deionized water rinse within 4 hours",
            "BGA reballing, aggressive oxidized copper ground planes"
    );

    public static FluxSpec getSpecByType(int typeIndex) {
        switch (typeIndex) {
            case 1:
                return NO_CLEAN;
            case 2:
                return WATER_SOLUBLE;
            case 0:
            default:
                return ROSIN_RMA;
        }
    }
}