package com.kimrosil.tapdrill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MachiningStandardsRepository {

    public static class ThreadSpec {
        public final String standard;      // METRIC, UNC_UNF, PIPE, ACME
        public final String nominalDisplay;  // e.g. M6 x 1.0, 1/4-20 UNC
        public final String pitchDisplay;    // e.g. 1.00 mm or 20 TPI
        public final double majorDiameterInch;
        public final double majorDiameterMm;
        public final double pitchInch;
        public final double tapDrill75Inch;  // 75% standard thread depth
        public final double tapDrill50Inch;  // 50% tough alloy depth
        public final String nearestFractional;
        public final String wireLetterGauge;
        public final String clearanceClose;
        public final String clearanceFree;
        public final boolean isMetric;

        public ThreadSpec(String standard, String nominalDisplay, String pitchDisplay,
                          double majorInch, double pitchInch, double drill75, double drill50,
                          String fractional, String gauge, String close, String free, boolean isMetric) {
            this.standard = standard;
            this.nominalDisplay = nominalDisplay;
            this.pitchDisplay = pitchDisplay;
            this.majorDiameterInch = majorInch;
            this.majorDiameterMm = majorInch * 25.4;
            this.pitchInch = pitchInch;
            this.tapDrill75Inch = drill75;
            this.tapDrill50Inch = drill50;
            this.nearestFractional = fractional;
            this.wireLetterGauge = gauge;
            this.clearanceClose = close;
            this.clearanceFree = free;
            this.isMetric = isMetric;
        }
    }

    private static final List<ThreadSpec> ALL_THREADS = new ArrayList<>();

    static {
        // --- ISO METRIC COARSE & FINE (ISO 68-1) ---
        addMetric("M2 x 0.40", 2.0, 0.40, 1.60, 1.75, "1/16\"", "#52", "2.10 mm", "2.40 mm");
        addMetric("M2.5 x 0.45", 2.5, 0.45, 2.05, 2.20, "5/64\"", "#46", "2.65 mm", "2.90 mm");
        addMetric("M3 x 0.50", 3.0, 0.50, 2.50, 2.70, "3/32\"", "#40", "3.20 mm", "3.40 mm");
        addMetric("M3.5 x 0.60", 3.5, 0.60, 2.90, 3.10, "7/64\"", "#32", "3.70 mm", "3.90 mm");
        addMetric("M4 x 0.70", 4.0, 0.70, 3.30, 3.50, "1/8\"", "#30", "4.30 mm", "4.50 mm");
        addMetric("M5 x 0.80", 5.0, 0.80, 4.20, 4.50, "#19", "#19", "5.30 mm", "5.50 mm");
        addMetric("M6 x 1.00", 6.0, 1.00, 5.00, 5.30, "13/64\"", "#8", "6.40 mm", "6.60 mm");
        addMetric("M8 x 1.25", 8.0, 1.25, 6.80, 7.20, "17/64\"", "H", "8.40 mm", "9.00 mm");
        addMetric("M8 x 1.00 (Fine)", 8.0, 1.00, 7.00, 7.30, "J", "J", "8.40 mm", "9.00 mm");
        addMetric("M10 x 1.50", 10.0, 1.50, 8.50, 9.00, "21/64\"", "Q", "10.5 mm", "11.0 mm");
        addMetric("M10 x 1.25 (Fine)", 10.0, 1.25, 8.80, 9.20, "11/32\"", "S", "10.5 mm", "11.0 mm");
        addMetric("M12 x 1.75", 12.0, 1.75, 10.20, 10.80, "13/32\"", "Y", "13.0 mm", "13.5 mm");
        addMetric("M12 x 1.25 (Fine)", 12.0, 1.25, 10.80, 11.20, "27/64\"", "7/16\"", "13.0 mm", "13.5 mm");
        addMetric("M14 x 2.00", 14.0, 2.00, 12.00, 12.70, "15/32\"", "15/32\"", "15.0 mm", "15.5 mm");
        addMetric("M16 x 2.00", 16.0, 2.00, 14.00, 14.70, "35/64\"", "35/64\"", "17.0 mm", "17.5 mm");
        addMetric("M20 x 2.50", 20.0, 2.50, 17.50, 18.30, "11/16\"", "11/16\"", "21.0 mm", "22.0 mm");

        // --- ASME UNIFIED NATIONAL COARSE & FINE (ASME B1.1) ---
        addInch("0-80 UNF", 0.0600, 80, 0.0469, 0.0510, "3/64\"", "#56", ".0670\"", ".0730\"");
        addInch("1-64 UNC", 0.0730, 64, 0.0595, 0.0635, "#53", "#53", ".0810\"", ".0860\"");
        addInch("2-56 UNC", 0.0860, 56, 0.0700, 0.0750, "#50", "#50", ".0960\"", ".1015\"");
        addInch("4-40 UNC", 0.1120, 40, 0.0890, 0.0960, "#43", "#43", ".1200\"", ".1285\"");
        addInch("6-32 UNC", 0.1380, 32, 0.1065, 0.1160, "#36", "#36", ".1495\"", ".1570\"");
        addInch("8-32 UNC", 0.1640, 32, 0.1360, 0.1440, "#29", "#29", ".1770\"", ".1850\"");
        addInch("10-24 UNC", 0.1900, 24, 0.1495, 0.1610, "#25", "#25", ".2010\"", ".2090\"");
        addInch("10-32 UNF", 0.1900, 32, 0.1590, 0.1690, "#21", "#21", ".2010\"", ".2090\"");
        addInch("1/4-20 UNC", 0.2500, 20, 0.2010, 0.2180, "7/32\"", "#7", ".2570\"", ".2660\"");
        addInch("1/4-28 UNF", 0.2500, 28, 0.2130, 0.2260, "#3", "#3", ".2570\"", ".2660\"");
        addInch("5/16-18 UNC", 0.3125, 18, 0.2570, 0.2760, "F", "F", ".3230\"", ".3320\"");
        addInch("5/16-24 UNF", 0.3125, 24, 0.2720, 0.2860, "I", "I", ".3230\"", ".3320\"");
        addInch("3/8-16 UNC", 0.3750, 16, 0.3125, 0.3340, "5/16\"", "5/16\"", ".3860\"", ".3970\"");
        addInch("3/8-24 UNF", 0.3750, 24, 0.3320, 0.3470, "Q", "Q", ".3860\"", ".3970\"");
        addInch("7/16-14 UNC", 0.4375, 14, 0.3680, 0.3920, "U", "U", ".4530\"", ".4688\"");
        addInch("1/2-13 UNC", 0.5000, 13, 0.4219, 0.4480, "27/64\"", "27/64\"", ".5156\"", ".5312\"");
        addInch("1/2-20 UNF", 0.5000, 20, 0.4531, 0.4680, "29/64\"", "29/64\"", ".5156\"", ".5312\"");
        addInch("5/8-11 UNC", 0.6250, 11, 0.5312, 0.5620, "17/32\"", "17/32\"", ".6406\"", ".6562\"");
        addInch("3/4-10 UNC", 0.7500, 10, 0.6562, 0.6875, "21/32\"", "21/32\"", ".7656\"", ".7812\"");
        addInch("1-8 UNC", 1.0000, 8, 0.8750, 0.9150, "7/8\"", "7/8\"", "1.031\"", "1.062\"");

        // --- NPT PIPE THREADS (ANSI B1.20.1) ---
        addPipe("1/8-27 NPT", 0.405, 27, 0.3390, 0.3480, "R", "11/32\"", ".4210\"", ".4375\"");
        addPipe("1/4-18 NPT", 0.540, 18, 0.4375, 0.4530, "7/16\"", "7/16\"", ".5625\"", ".5781\"");
        addPipe("3/8-18 NPT", 0.675, 18, 0.5625, 0.5820, "9/16\"", "9/16\"", ".7031\"", ".7188\"");
        addPipe("1/2-14 NPT", 0.840, 14, 0.7031, 0.7250, "45/64\"", "23/32\"", ".8750\"", ".8906\"");
        addPipe("3/4-14 NPT", 1.050, 14, 0.9062, 0.9320, "29/32\"", "15/16\"", "1.093\"", "1.125\"");
        addPipe("1-11.5 NPT", 1.315, 11.5, 1.1406, 1.1710, "1-9/64\"", "1-9/64\"", "1.375\"", "1.406\"");

        // --- ACME GENERAL PURPOSE (ASME B1.5) ---
        addAcme("1/2-10 ACME", 0.500, 10, 0.4000, 0.4300, "13/32\"", ".4000\"", ".5156\"", ".5312\"");
        addAcme("5/8-8 ACME", 0.625, 8, 0.5000, 0.5380, "1/2\"", ".5000\"", ".6406\"", ".6562\"");
        addAcme("3/4-6 ACME", 0.750, 6, 0.5833, 0.6350, "19/32\"", ".5833\"", ".7656\"", ".7812\"");
        addAcme("1-5 ACME", 1.000, 5, 0.8000, 0.8650, "13/16\"", ".8000\"", "1.031\"", "1.062\"");
    }

    private static void addMetric(String nom, double dMm, double pMm, double d75Mm, double d50Mm,
                                  String frac, String gauge, String close, String free) {
        double dInch = dMm / 25.4;
        double pInch = pMm / 25.4;
        double d75Inch = d75Mm / 25.4;
        double d50Inch = d50Mm / 25.4;
        ALL_THREADS.add(new ThreadSpec(
                "METRIC", nom, String.format(Locale.US, "%.2f mm", pMm),
                dInch, pInch, d75Inch, d50Inch, frac, gauge, close, free, true
        ));
    }

    private static void addInch(String nom, double dInch, double tpi, double d75Inch, double d50Inch,
                                String frac, String gauge, String close, String free) {
        double pInch = 1.0 / tpi;
        ALL_THREADS.add(new ThreadSpec(
                "UNC_UNF", nom, String.format(Locale.US, "%.0f TPI", tpi),
                dInch, pInch, d75Inch, d50Inch, frac, gauge, close, free, false
        ));
    }

    private static void addPipe(String nom, double dInch, double tpi, double d75Inch, double d50Inch,
                                String frac, String gauge, String close, String free) {
        double pInch = 1.0 / tpi;
        ALL_THREADS.add(new ThreadSpec(
                "PIPE", nom, String.format(Locale.US, "%.1f TPI", tpi),
                dInch, pInch, d75Inch, d50Inch, frac, gauge, close, free, false
        ));
    }

    private static void addAcme(String nom, double dInch, double tpi, double d75Inch, double d50Inch,
                                String frac, String gauge, String close, String free) {
        double pInch = 1.0 / tpi;
        ALL_THREADS.add(new ThreadSpec(
                "ACME", nom, String.format(Locale.US, "%.0f TPI", tpi),
                dInch, pInch, d75Inch, d50Inch, frac, gauge, close, free, false
        ));
    }

    public static List<ThreadSpec> getThreads(String standard, String filter) {
        List<ThreadSpec> result = new ArrayList<>();
        String query = (filter != null) ? filter.toLowerCase(Locale.US).trim() : "";
        for (ThreadSpec s : ALL_THREADS) {
            if (s.standard.equalsIgnoreCase(standard)) {
                if (query.isEmpty() || s.nominalDisplay.toLowerCase(Locale.US).contains(query)) {
                    result.add(s);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }
}