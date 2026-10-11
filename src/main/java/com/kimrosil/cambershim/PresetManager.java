package com.kimrosil.cambershim;

import java.util.ArrayList;
import java.util.List;

public class PresetManager {

    public static class DoorPreset {
        public final String name;
        public final double heightMm;
        public final double widthMm;
        public final double hingeSpreadMm;

        public DoorPreset(String name, double heightMm, double widthMm, double hingeSpreadMm) {
            this.name = name;
            this.heightMm = heightMm;
            this.widthMm = widthMm;
            this.hingeSpreadMm = hingeSpreadMm;
        }
    }

    public static List<DoorPreset> getStandardPresets() {
        List<DoorPreset> presets = new ArrayList<>();
        // 30" x 80" Interior Hollow Core (762 x 2032 mm, ~1524 mm hinge spread)
        presets.add(new DoorPreset("30\" x 80\" Interior (Residential)", 2032.0, 762.0, 1524.0));
        // 36" x 80" Exterior / Entry (914.4 x 2032 mm, ~1575 mm hinge spread)
        presets.add(new DoorPreset("36\" x 80\" Standard Entry (Solid Core)", 2032.0, 914.4, 1575.0));
        // 36" x 84" Commercial Heavy Duty (914.4 x 2133.6 mm, ~1676 mm hinge spread)
        presets.add(new DoorPreset("36\" x 84\" Commercial (Architectural)", 2133.6, 914.4, 1676.4));
        return presets;
    }
}