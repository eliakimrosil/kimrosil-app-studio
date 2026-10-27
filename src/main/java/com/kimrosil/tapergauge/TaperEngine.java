package com.kimrosil.tapergauge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TaperEngine {

    public static final double MM_PER_INCH = 25.4;

    public enum UnitSystem {
        INCHES, MM
    }

    public static class TaperPreset {
        public final String id;
        public final String name;
        public final String standard; // "Morse", "Brown & Sharpe", "Jacobs", "R8"
        public final double majorDiaInches;
        public final double minorDiaInches;
        public final double lengthInches;
        public final double tpf;

        public TaperPreset(String id, String name, String standard,
                           double majorDiaInches, double minorDiaInches,
                           double lengthInches, double tpf) {
            this.id = id;
            this.name = name;
            this.standard = standard;
            this.majorDiaInches = majorDiaInches;
            this.minorDiaInches = minorDiaInches;
            this.lengthInches = lengthInches;
            this.tpf = tpf;
        }
    }

    public static class TaperResult {
        public double majorDia;
        public double minorDia;
        public double taperLength;
        public double totalWorkpieceLength;
        public UnitSystem unit;

        public double tpi;
        public double tpf;
        public double halfAngleDeg; // Compound rest angle α
        public double includedAngleDeg; // 2α
        public double tailstockOffset; // S

        public String getFormattedCompoundAngle() {
            int deg = (int) halfAngleDeg;
            double remMin = (halfAngleDeg - deg) * 60.0;
            int min = (int) remMin;
            double sec = (remMin - min) * 60.0;
            return String.format(Locale.US, "%.3f° (%d° %02d' %02.0f\")", halfAngleDeg, deg, min, sec);
        }

        public String getFormattedIncludedAngle() {
            int deg = (int) includedAngleDeg;
            double remMin = (includedAngleDeg - deg) * 60.0;
            int min = (int) remMin;
            return String.format(Locale.US, "%.3f° (%d° %02d')", includedAngleDeg, deg, min);
        }

        public String getFormattedTpf() {
            return String.format(Locale.US, "%.4f in/ft", tpf);
        }

        public String getFormattedTpi() {
            return String.format(Locale.US, "%.5f in/in", tpi);
        }

        public String getFormattedTailstockOffset() {
            String u = (unit == UnitSystem.MM) ? "mm" : "in";
            return String.format(Locale.US, "%.4f %s", tailstockOffset, u);
        }

        public String getSummary(String presetName) {
            StringBuilder sb = new StringBuilder();
            sb.append("TaperGauge Machining Specification\n");
            sb.append("----------------------------------\n");
            sb.append("Standard / Profile: ").append(presetName).append("\n");
            String u = (unit == UnitSystem.MM) ? "mm" : "in";
            sb.append(String.format(Locale.US, "Major Diameter (D): %.4f %s\n", majorDia, u));
            sb.append(String.format(Locale.US, "Minor Diameter (d): %.4f %s\n", minorDia, u));
            sb.append(String.format(Locale.US, "Taper Length (l): %.4f %s\n", taperLength, u));
            sb.append(String.format(Locale.US, "Total Workpiece Length (L): %.4f %s\n", totalWorkpieceLength, u));
            sb.append("----------------------------------\n");
            sb.append("Compound Rest Angle (α): ").append(getFormattedCompoundAngle()).append("\n");
            sb.append("Included Angle (2α): ").append(getFormattedIncludedAngle()).append("\n");
            sb.append("Taper Per Foot (TPF): ").append(getFormattedTpf()).append("\n");
            sb.append("Taper Per Inch (TPI): ").append(getFormattedTpi()).append("\n");
            sb.append("Tailstock Set-Over (S): ").append(getFormattedTailstockOffset()).append("\n");
            sb.append("----------------------------------\nKimRosil App Studio • TaperGauge");
            return sb.toString();
        }
    }

    public static List<TaperPreset> getBuiltInPresets() {
        List<TaperPreset> list = new ArrayList<>();
        // Morse Tapers
        list.add(new TaperPreset("mt1", "Morse #1", "Morse", 0.4750, 0.3690, 2.1250, 0.59858));
        list.add(new TaperPreset("mt2", "Morse #2", "Morse", 0.7000, 0.5720, 2.5625, 0.59941));
        list.add(new TaperPreset("mt3", "Morse #3", "Morse", 0.9380, 0.7780, 3.1875, 0.60235));
        list.add(new TaperPreset("mt4", "Morse #4", "Morse", 1.2310, 1.0200, 4.0625, 0.62326));
        list.add(new TaperPreset("mt5", "Morse #5", "Morse", 1.7480, 1.4750, 5.1875, 0.63151));

        // Brown & Sharpe Tapers (0.500 in/ft)
        list.add(new TaperPreset("bs7", "B&S #7", "Brown & Sharpe", 0.7200, 0.6000, 2.8800, 0.50000));
        list.add(new TaperPreset("bs9", "B&S #9", "Brown & Sharpe", 0.9000, 0.7540, 3.5000, 0.50000));

        // Jacobs Tapers
        list.add(new TaperPreset("jt2", "Jacobs #2", "Jacobs", 0.5590, 0.4870, 0.8750, 0.98743));
        list.add(new TaperPreset("jt3", "Jacobs #3", "Jacobs", 0.8110, 0.7460, 1.2188, 0.64000));
        list.add(new TaperPreset("jt6", "Jacobs #6", "Jacobs", 0.6760, 0.6240, 1.0000, 0.62400));

        // R8 Collet Shank Taper (16° 51' included 33° 42')
        list.add(new TaperPreset("r8", "R8 Collet", "Bridgeport", 0.9500, 0.7470, 0.9375, 2.59840));

        return list;
    }

    public static TaperResult calculate(double majorD, double minorD,
                                       double taperL, double totalL,
                                       UnitSystem unit) {
        TaperResult res = new TaperResult();
        res.majorDia = Math.max(0, majorD);
        res.minorDia = Math.max(0, minorD);
        res.taperLength = Math.max(0.001, taperL);
        res.totalWorkpieceLength = (totalL > 0) ? totalL : res.taperLength;
        res.unit = unit;

        if (res.majorDia <= res.minorDia || res.taperLength <= 0) {
            res.tpi = 0;
            res.tpf = 0;
            res.halfAngleDeg = 0;
            res.includedAngleDeg = 0;
            res.tailstockOffset = 0;
            return res;
        }

        // Calculations in native input units
        double deltaDia = res.majorDia - res.minorDia;
        double slope = deltaDia / (2.0 * res.taperLength);

        // Half angle α = arctan((D - d) / (2l))
        double halfAngleRad = Math.atan(slope);
        res.halfAngleDeg = Math.toDegrees(halfAngleRad);
        res.includedAngleDeg = res.halfAngleDeg * 2.0;

        // Tailstock set-over offset S = L * (D - d) / (2l)
        res.tailstockOffset = res.totalWorkpieceLength * slope;

        // TPI and TPF (standardized to inch scale)
        double majorInches = (unit == UnitSystem.MM) ? (res.majorDia / MM_PER_INCH) : res.majorDia;
        double minorInches = (unit == UnitSystem.MM) ? (res.minorDia / MM_PER_INCH) : res.minorDia;
        double lengthInches = (unit == UnitSystem.MM) ? (res.taperLength / MM_PER_INCH) : res.taperLength;

        res.tpi = (majorInches - minorInches) / lengthInches;
        res.tpf = res.tpi * 12.0;

        return res;
    }
}
