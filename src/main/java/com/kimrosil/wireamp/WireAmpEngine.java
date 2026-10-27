package com.kimrosil.wireamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WireAmpEngine {

    public enum Metal {
        COPPER, ALUMINUM
    }

    public enum TempRating {
        TEMP_60C("60°C (TW, UF)"),
        TEMP_75C("75°C (THWN, RHW)"),
        TEMP_90C("90°C (THHN, XHHW-2)");

        public final String label;
        TempRating(String label) {
            this.label = label;
        }
    }

    public static class WireGauge {
        public final String name;
        public final int cu60, cu75, cu90;
        public final int al60, al75, al90;
        public final int maxSmallBreakerCu; // NEC 240.4(D) limit (0 if none)

        public WireGauge(String name, int cu60, int cu75, int cu90,
                         int al60, int al75, int al90, int maxSmallBreakerCu) {
            this.name = name;
            this.cu60 = cu60;
            this.cu75 = cu75;
            this.cu90 = cu90;
            this.al60 = al60;
            this.al75 = al75;
            this.al90 = al90;
            this.maxSmallBreakerCu = maxSmallBreakerCu;
        }

        public int getBaseAmpacity(Metal metal, TempRating temp) {
            if (metal == Metal.COPPER) {
                switch (temp) {
                    case TEMP_60C: return cu60;
                    case TEMP_75C: return cu75;
                    case TEMP_90C: return cu90;
                }
            } else {
                switch (temp) {
                    case TEMP_60C: return al60;
                    case TEMP_75C: return al75;
                    case TEMP_90C: return al90;
                }
            }
            return 0;
        }
    }

    public static class AmpResult {
        public WireGauge gauge;
        public Metal metal;
        public TempRating temp;
        public int ambientTempF;
        public int conductorCount;

        public int baseAmpacity;
        public double tempFactor;
        public double bundleFactor;
        public double deratedAmpacity;
        public double continuousLoadLimit; // 80% rule
        public int standardBreakerSize;

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("WireAmp NEC 310.16 Feeder Specification\n");
            sb.append("---------------------------------------\n");
            sb.append("Conductor: ").append(gauge.name).append(" ").append(metal == Metal.COPPER ? "Copper (Cu)" : "Aluminum (Al)").append("\n");
            sb.append("Insulation: ").append(temp.label).append("\n");
            sb.append(String.format(Locale.US, "Ambient Temperature: %d°F (%.0f°C)\n", ambientTempF, (ambientTempF - 32) * 5.0 / 9.0));
            sb.append(String.format(Locale.US, "Raceway Bundle: %d Current-Carrying Conductors\n", conductorCount));
            sb.append("---------------------------------------\n");
            sb.append(String.format(Locale.US, "Table 310.16 Base Ampacity: %d A\n", baseAmpacity));
            sb.append(String.format(Locale.US, "Temperature Correction (FT): %.2f\n", tempFactor));
            sb.append(String.format(Locale.US, "Conduit Bundling Factor (FB): %.2f\n", bundleFactor));
            sb.append(String.format(Locale.US, "DERATED ALLOWABLE AMPACITY: %.1f A\n", deratedAmpacity));
            sb.append(String.format(Locale.US, "80%% Continuous Circuit Limit: %.1f A\n", continuousLoadLimit));
            sb.append(String.format(Locale.US, "Recommended Max Breaker (OCPD): %d A\n", standardBreakerSize));
            sb.append("---------------------------------------\nKimRosil App Studio • WireAmp");
            return sb.toString();
        }
    }

    public static List<WireGauge> getStandardGauges() {
        List<WireGauge> list = new ArrayList<>();
        list.add(new WireGauge("14 AWG", 15, 20, 25, 0, 0, 0, 15));
        list.add(new WireGauge("12 AWG", 20, 25, 30, 15, 20, 25, 20));
        list.add(new WireGauge("10 AWG", 30, 35, 40, 25, 30, 35, 30));
        list.add(new WireGauge("8 AWG", 40, 50, 55, 35, 40, 45, 0));
        list.add(new WireGauge("6 AWG", 55, 65, 75, 40, 50, 60, 0));
        list.add(new WireGauge("4 AWG", 70, 85, 95, 55, 65, 75, 0));
        list.add(new WireGauge("3 AWG", 85, 100, 115, 65, 75, 85, 0));
        list.add(new WireGauge("2 AWG", 95, 115, 130, 75, 90, 100, 0));
        list.add(new WireGauge("1 AWG", 110, 130, 145, 85, 100, 115, 0));
        list.add(new WireGauge("1/0", 125, 150, 170, 100, 120, 135, 0));
        list.add(new WireGauge("2/0", 145, 175, 195, 115, 135, 150, 0));
        list.add(new WireGauge("3/0", 165, 200, 225, 130, 155, 175, 0));
        list.add(new WireGauge("4/0", 195, 230, 260, 150, 180, 205, 0));
        list.add(new WireGauge("250 kcmil", 215, 255, 290, 170, 205, 230, 0));
        list.add(new WireGauge("350 kcmil", 260, 310, 350, 210, 250, 280, 0));
        list.add(new WireGauge("500 kcmil", 320, 380, 430, 260, 310, 350, 0));
        return list;
    }

    public static double getTemperatureFactor(int tempF, TempRating temp) {
        if (tempF <= 77) {
            return (temp == TempRating.TEMP_60C) ? 1.08 : (temp == TempRating.TEMP_75C ? 1.05 : 1.04);
        } else if (tempF <= 86) {
            return 1.00;
        } else if (tempF <= 95) {
            return (temp == TempRating.TEMP_60C) ? 0.91 : (temp == TempRating.TEMP_75C ? 0.94 : 0.96);
        } else if (tempF <= 104) {
            return (temp == TempRating.TEMP_60C) ? 0.82 : (temp == TempRating.TEMP_75C ? 0.88 : 0.91);
        } else if (tempF <= 113) {
            return (temp == TempRating.TEMP_60C) ? 0.71 : (temp == TempRating.TEMP_75C ? 0.82 : 0.87);
        } else if (tempF <= 122) {
            return (temp == TempRating.TEMP_60C) ? 0.58 : (temp == TempRating.TEMP_75C ? 0.75 : 0.82);
        } else if (tempF <= 131) {
            return (temp == TempRating.TEMP_60C) ? 0.41 : (temp == TempRating.TEMP_75C ? 0.67 : 0.76);
        } else if (tempF <= 140) {
            return (temp == TempRating.TEMP_60C) ? 0.00 : (temp == TempRating.TEMP_75C ? 0.58 : 0.71);
        } else {
            return (temp == TempRating.TEMP_60C) ? 0.00 : (temp == TempRating.TEMP_75C ? 0.33 : 0.58);
        }
    }

    public static double getBundlingFactor(int conductors) {
        if (conductors <= 3) return 1.00;
        if (conductors <= 6) return 0.80;
        if (conductors <= 9) return 0.70;
        if (conductors <= 20) return 0.50;
        if (conductors <= 30) return 0.45;
        return 0.40;
    }

    public static int getStandardBreakerSize(double allowableAmpacity, int maxSmallRule) {
        int[] standardBreakers = {15, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 90, 100, 110, 125, 150, 175, 200, 225, 250, 300, 350, 400};
        int selected = 15;
        for (int b : standardBreakers) {
            if (b <= allowableAmpacity) {
                selected = b;
            } else {
                break;
            }
        }
        if (maxSmallRule > 0 && selected > maxSmallRule) {
            selected = maxSmallRule;
        }
        return selected;
    }

    public static AmpResult calculate(WireGauge gauge, Metal metal, TempRating temp,
                                      int ambientTempF, int conductorCount) {
        AmpResult res = new AmpResult();
        res.gauge = gauge;
        res.metal = metal;
        res.temp = temp;
        res.ambientTempF = ambientTempF;
        res.conductorCount = Math.max(1, conductorCount);

        res.baseAmpacity = gauge.getBaseAmpacity(metal, temp);
        res.tempFactor = getTemperatureFactor(ambientTempF, temp);
        res.bundleFactor = getBundlingFactor(conductorCount);

        res.deratedAmpacity = res.baseAmpacity * res.tempFactor * res.bundleFactor;
        res.continuousLoadLimit = res.deratedAmpacity * 0.80;

        int smallRule = (metal == Metal.COPPER) ? gauge.maxSmallBreakerCu : 0;
        res.standardBreakerSize = getStandardBreakerSize(res.deratedAmpacity, smallRule);

        return res;
    }
}
