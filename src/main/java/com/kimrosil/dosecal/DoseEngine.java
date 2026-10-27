package com.kimrosil.dosecal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DoseEngine {

    public static final double LBS_PER_KG = 2.20462262185;

    public enum WeightUnit {
        KG, LBS
    }

    public static class DrugPreset {
        public final String id;
        public final String name;
        public final String category; // "Pediatric" or "Veterinary"
        public final double defaultDoseRateMgPerKg;
        public final double defaultConcMg;
        public final double defaultConcMl;
        public final int defaultIntervalHours;
        public final double maxDailyMgPerKg;
        public final double maxDailyAbsoluteMg;
        public final String notes;

        public DrugPreset(String id, String name, String category,
                          double defaultDoseRateMgPerKg,
                          double defaultConcMg, double defaultConcMl,
                          int defaultIntervalHours,
                          double maxDailyMgPerKg, double maxDailyAbsoluteMg,
                          String notes) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.defaultDoseRateMgPerKg = defaultDoseRateMgPerKg;
            this.defaultConcMg = defaultConcMg;
            this.defaultConcMl = defaultConcMl;
            this.defaultIntervalHours = defaultIntervalHours;
            this.maxDailyMgPerKg = maxDailyMgPerKg;
            this.maxDailyAbsoluteMg = maxDailyAbsoluteMg;
            this.notes = notes;
        }

        public double getConcentrationMgPerMl() {
            return defaultConcMl > 0 ? (defaultConcMg / defaultConcMl) : 0;
        }
    }

    public static class DoseResult {
        public double weightKg;
        public double doseRateMgPerKg;
        public double concentrationMgPerMl;
        public int intervalHours;
        public double singleDoseMg;
        public double singleDoseMl;
        public double dailyTotalMg;
        public double dailyTotalMl;
        public int dosesPerDay;
        public boolean isExceedingDailyMax;
        public String safetyMessage;

        public String getFormattedVolume() {
            if (singleDoseMl < 0.1) {
                return String.format(Locale.US, "%.2f mL", singleDoseMl);
            } else if (singleDoseMl < 10.0) {
                return String.format(Locale.US, "%.1f mL", singleDoseMl);
            } else {
                return String.format(Locale.US, "%.1f mL", singleDoseMl);
            }
        }

        public String getFormattedSingleDose() {
            return String.format(Locale.US, "%.1f mg", singleDoseMg);
        }

        public String getFormattedDailyTotal() {
            return String.format(Locale.US, "%.1f mg/day", dailyTotalMg);
        }

        public String getSummary(String drugName) {
            StringBuilder sb = new StringBuilder();
            sb.append("DoseCal Prescription Report\n");
            sb.append("----------------------------\n");
            sb.append("Drug / Regimen: ").append(drugName).append("\n");
            sb.append(String.format(Locale.US, "Patient Weight: %.2f kg\n", weightKg));
            sb.append(String.format(Locale.US, "Dose Rate: %.2f mg/kg\n", doseRateMgPerKg));
            sb.append(String.format(Locale.US, "Suspension Concentration: %.2f mg/mL\n", concentrationMgPerMl));
            sb.append(String.format(Locale.US, "Dosing Schedule: Every %d hours (%d times/day)\n", intervalHours, dosesPerDay));
            sb.append("----------------------------\n");
            sb.append(String.format(Locale.US, "SINGLE DOSE: %s (%.1f mg)\n", getFormattedVolume(), singleDoseMg));
            sb.append(String.format(Locale.US, "TOTAL DAILY: %.1f mL/day (%.1f mg/day)\n", dailyTotalMl, dailyTotalMg));
            sb.append("Safety Status: ").append(isExceedingDailyMax ? "ALERT: Exceeds Recommended Max" : "Within Safe Clinical Range").append("\n");
            if (safetyMessage != null && !safetyMessage.isEmpty()) {
                sb.append("Note: ").append(safetyMessage).append("\n");
            }
            sb.append("----------------------------\nKimRosil App Studio • DoseCal");
            return sb.toString();
        }
    }

    public static List<DrugPreset> getBuiltInPresets() {
        List<DrugPreset> list = new ArrayList<>();
        // Pediatric Presets
        list.add(new DrugPreset(
                "p_acetaminophen", "Acetaminophen", "Pediatric",
                15.0, 160.0, 5.0, 6, 75.0, 4000.0,
                "Infant & Child Oral Suspension (160 mg / 5 mL)"
        ));
        list.add(new DrugPreset(
                "p_ibuprofen", "Ibuprofen", "Pediatric",
                10.0, 100.0, 5.0, 6, 40.0, 2400.0,
                "Children Oral Suspension (100 mg / 5 mL)"
        ));
        list.add(new DrugPreset(
                "p_amoxicillin", "Amoxicillin", "Pediatric",
                25.0, 250.0, 5.0, 8, 90.0, 3000.0,
                "Standard Pediatric Suspension (250 mg / 5 mL)"
        ));
        list.add(new DrugPreset(
                "p_diphenhydramine", "Diphenhydramine", "Pediatric",
                1.25, 12.5, 5.0, 6, 5.0, 300.0,
                "Children Allergy Liquid (12.5 mg / 5 mL)"
        ));
        list.add(new DrugPreset(
                "p_cephalexin", "Cephalexin", "Pediatric",
                12.5, 250.0, 5.0, 6, 100.0, 4000.0,
                "Pediatric Oral Suspension (250 mg / 5 mL)"
        ));

        // Veterinary Presets
        list.add(new DrugPreset(
                "v_meloxicam_dog", "Meloxicam (Canine)", "Veterinary",
                0.1, 1.5, 1.0, 24, 0.2, 10.0,
                "Canine Oral Suspension (1.5 mg / mL)"
        ));
        list.add(new DrugPreset(
                "v_meloxicam_cat", "Meloxicam (Feline)", "Veterinary",
                0.05, 0.5, 1.0, 24, 0.05, 2.0,
                "Feline Oral Suspension (0.5 mg / mL)"
        ));
        list.add(new DrugPreset(
                "v_gabapentin", "Gabapentin (Pet)", "Veterinary",
                10.0, 50.0, 1.0, 8, 30.0, 1200.0,
                "Veterinary Liquid Solution (50 mg / mL)"
        ));
        list.add(new DrugPreset(
                "v_cephalexin_pet", "Cephalexin (Pet)", "Veterinary",
                22.0, 250.0, 5.0, 12, 45.0, 2000.0,
                "Pet Oral Suspension (250 mg / 5 mL)"
        ));
        list.add(new DrugPreset(
                "v_metronidazole", "Metronidazole (Pet)", "Veterinary",
                15.0, 50.0, 1.0, 12, 30.0, 1500.0,
                "Veterinary Oral Liquid (50 mg / mL)"
        ));
        return list;
    }

    public static double lbsToKg(double lbs) {
        return lbs / LBS_PER_KG;
    }

    public static double kgToLbs(double kg) {
        return kg * LBS_PER_KG;
    }

    public static DoseResult calculate(double weightKg, double doseRateMgPerKg,
                                      double concMg, double concMl,
                                      int intervalHours, DrugPreset preset) {
        DoseResult res = new DoseResult();
        if (weightKg <= 0 || doseRateMgPerKg <= 0 || concMg <= 0 || concMl <= 0 || intervalHours <= 0) {
            res.weightKg = Math.max(0, weightKg);
            res.doseRateMgPerKg = Math.max(0, doseRateMgPerKg);
            res.concentrationMgPerMl = 0;
            res.intervalHours = Math.max(1, intervalHours);
            res.singleDoseMg = 0;
            res.singleDoseMl = 0;
            res.dailyTotalMg = 0;
            res.dailyTotalMl = 0;
            res.dosesPerDay = 24 / res.intervalHours;
            res.isExceedingDailyMax = false;
            res.safetyMessage = "Enter valid patient weight and dosing parameters.";
            return res;
        }

        res.weightKg = weightKg;
        res.doseRateMgPerKg = doseRateMgPerKg;
        res.concentrationMgPerMl = concMg / concMl;
        res.intervalHours = intervalHours;
        res.dosesPerDay = Math.max(1, 24 / intervalHours);

        // Core dosing math
        res.singleDoseMg = weightKg * doseRateMgPerKg;
        res.singleDoseMl = res.singleDoseMg / res.concentrationMgPerMl;
        res.dailyTotalMg = res.singleDoseMg * res.dosesPerDay;
        res.dailyTotalMl = res.singleDoseMl * res.dosesPerDay;

        // Safety limit checks
        res.isExceedingDailyMax = false;
        if (preset != null) {
            double dailyMgPerKg = res.dailyTotalMg / weightKg;
            if (preset.maxDailyMgPerKg > 0 && dailyMgPerKg > (preset.maxDailyMgPerKg + 0.001)) {
                res.isExceedingDailyMax = true;
                res.safetyMessage = String.format(Locale.US, "Exceeds max daily limit of %.1f mg/kg/day.", preset.maxDailyMgPerKg);
            } else if (preset.maxDailyAbsoluteMg > 0 && res.dailyTotalMg > (preset.maxDailyAbsoluteMg + 0.001)) {
                res.isExceedingDailyMax = true;
                res.safetyMessage = String.format(Locale.US, "Exceeds max absolute limit of %.0f mg/day.", preset.maxDailyAbsoluteMg);
            } else {
                res.safetyMessage = "Dosage is within safe clinical parameters.";
            }
        } else {
            res.safetyMessage = "Calculated using custom parameters.";
        }

        return res;
    }
}
