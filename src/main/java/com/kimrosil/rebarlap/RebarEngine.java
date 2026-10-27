package com.kimrosil.rebarlap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RebarEngine {

    public static final double MM_PER_INCH = 25.4;

    public static class BarSpec {
        public final String name;
        public final String metricName;
        public final double diaInches;
        public final double areaSqInches;
        public final double weightLbsPerFt;

        public BarSpec(String name, String metricName, double diaInches, double areaSqInches, double weightLbsPerFt) {
            this.name = name;
            this.metricName = metricName;
            this.diaInches = diaInches;
            this.areaSqInches = areaSqInches;
            this.weightLbsPerFt = weightLbsPerFt;
        }

        public double getDiaMm() {
            return diaInches * MM_PER_INCH;
        }
    }

    public static class SpliceResult {
        public BarSpec bar;
        public double fcPsi;
        public double fyPsi;
        public boolean isTopBar;
        public boolean isEpoxyCoated;

        public double ldInches; // Basic tension development length
        public double classBLapInches; // Standard Class B tension lap
        public double classALapInches; // Class A tension lap
        public double compLapInches; // Compression lap
        public double hook90ExtInches; // 90° hook tail extension
        public double hook180ExtInches; // 180° hook tail extension
        public double pinDiaInches; // Hook bend pin diameter
        public double hookLdhInches; // Hook development length Ldh

        public String getFormattedClassBLap() {
            int feet = (int) (classBLapInches / 12.0);
            double remInches = classBLapInches - (feet * 12.0);
            if (feet > 0) {
                return String.format(Locale.US, "%.1f in (%d' %04.1f\")", classBLapInches, feet, remInches);
            } else {
                return String.format(Locale.US, "%.1f in", classBLapInches);
            }
        }

        public String getFormattedClassALap() {
            return String.format(Locale.US, "%.1f in", classALapInches);
        }

        public String getFormattedCompLap() {
            return String.format(Locale.US, "%.1f in", compLapInches);
        }

        public String getFormattedHook90() {
            return String.format(Locale.US, "%.1f in", hook90ExtInches);
        }

        public String getFormattedPinDia() {
            return String.format(Locale.US, "%.1f in", pinDiaInches);
        }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("RebarLap ACI 318-19 Splice Schedule\n");
            sb.append("------------------------------------\n");
            sb.append("Bar Size: ").append(bar.name).append(" (").append(bar.metricName).append(")\n");
            sb.append(String.format(Locale.US, "Nominal Diameter: %.3f in (%.1f mm)\n", bar.diaInches, bar.getDiaMm()));
            sb.append(String.format(Locale.US, "Concrete Strength (f'c): %.0f psi\n", fcPsi));
            sb.append(String.format(Locale.US, "Steel Yield (fy): %.0f psi (Grade %d)\n", fyPsi, (int) (fyPsi / 1000)));
            sb.append("Location: ").append(isTopBar ? "Top Bar (> 12\" fresh concrete)" : "Bottom / Other Bar").append("\n");
            sb.append("Coating: ").append(isEpoxyCoated ? "Epoxy Coated" : "Uncoated Black Steel").append("\n");
            sb.append("------------------------------------\n");
            sb.append("CLASS B TENSION LAP: ").append(getFormattedClassBLap()).append("\n");
            sb.append("Class A Tension Lap: ").append(getFormattedClassALap()).append("\n");
            sb.append("Tension Dev Length (Ld): ").append(String.format(Locale.US, "%.1f in\n", ldInches));
            sb.append("Compression Lap Splice: ").append(getFormattedCompLap()).append("\n");
            sb.append("90° Hook Tail Extension: ").append(getFormattedHook90()).append("\n");
            sb.append("180° Hook Tail Extension: ").append(String.format(Locale.US, "%.1f in\n", hook180ExtInches));
            sb.append("Bend Pin Inside Diameter: ").append(getFormattedPinDia()).append("\n");
            sb.append("------------------------------------\nKimRosil App Studio • RebarLap");
            return sb.toString();
        }
    }

    public static List<BarSpec> getStandardBars() {
        List<BarSpec> list = new ArrayList<>();
        list.add(new BarSpec("#3", "10M", 0.375, 0.11, 0.376));
        list.add(new BarSpec("#4", "13M", 0.500, 0.20, 0.668));
        list.add(new BarSpec("#5", "16M", 0.625, 0.31, 1.043));
        list.add(new BarSpec("#6", "19M", 0.750, 0.44, 1.502));
        list.add(new BarSpec("#7", "22M", 0.875, 0.60, 2.044));
        list.add(new BarSpec("#8", "25M", 1.000, 0.79, 2.670));
        list.add(new BarSpec("#9", "29M", 1.128, 1.00, 3.400));
        list.add(new BarSpec("#10", "32M", 1.270, 1.27, 4.303));
        list.add(new BarSpec("#11", "36M", 1.410, 1.56, 5.313));
        return list;
    }

    public static SpliceResult calculate(BarSpec bar, double fcPsi, double fyPsi, boolean isTop, boolean isEpoxy) {
        SpliceResult res = new SpliceResult();
        res.bar = bar;
        res.fcPsi = Math.max(2000, fcPsi);
        res.fyPsi = Math.max(40000, fyPsi);
        res.isTopBar = isTop;
        res.isEpoxyCoated = isEpoxy;

        double db = bar.diaInches;
        double sqrtFc = Math.min(100.0, Math.sqrt(res.fcPsi)); // ACI 318 limits sqrt(fc) to 100 psi

        // Modification factors
        double psiT = isTop ? 1.3 : 1.0;
        double psiE = isEpoxy ? 1.2 : 1.0;
        double lambda = 1.0; // Normal weight concrete

        // Tension Development Length Ld (ACI 318-19 Section 25.4.2.3 Simplified)
        double divisor = (db <= 0.750) ? 25.0 : 20.0;
        double basicLd = (res.fyPsi * psiT * psiE) / (divisor * lambda * sqrtFc) * db;
        res.ldInches = Math.max(12.0, basicLd);

        // Lap Splices (ACI 318-19 Section 25.5.2)
        res.classALapInches = Math.max(12.0, 1.0 * res.ldInches);
        res.classBLapInches = Math.max(12.0, 1.3 * res.ldInches);

        // Compression Lap Splice (ACI 318-19 Section 25.5.5)
        if (res.fyPsi <= 60000) {
            res.compLapInches = Math.max(12.0, 0.0005 * res.fyPsi * db);
        } else {
            res.compLapInches = Math.max(12.0, (0.0009 * res.fyPsi - 24.0) * db);
        }

        // Standard Hook Geometry (ACI 318-19 Table 25.3.1)
        res.hook90ExtInches = 12.0 * db;
        res.hook180ExtInches = Math.max(2.5, 4.0 * db);

        // Inside bend pin diameter
        if (db <= 1.0) {
            res.pinDiaInches = 6.0 * db;
        } else {
            res.pinDiaInches = 8.0 * db;
        }

        // Hook development length Ldh
        double basicLdh = (res.fyPsi * (isEpoxy ? 1.2 : 1.0)) / (55.0 * lambda * sqrtFc) * Math.pow(db, 1.5);
        res.hookLdhInches = Math.max(6.0, Math.max(8.0 * db, basicLdh));

        return res;
    }
}
