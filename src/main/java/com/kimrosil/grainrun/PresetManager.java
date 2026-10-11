package com.kimrosil.grainrun;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PresetManager {

    public static class TimberSpecies {
        private final String name;
        private final int janka;
        private final String categories;
        private final String description;

        public TimberSpecies(String name, int janka, String categories, String description) {
            this.name = name;
            this.janka = janka;
            this.categories = categories;
            this.description = description;
        }

        public String getName() { return name; }
        public int getJanka() { return janka; }
        public String getCategories() { return categories; }
        public String getDescription() { return description; }
    }

    private final List<TimberSpecies> mCatalog = new ArrayList<>();

    public PresetManager(Context context) {
        initCatalog();
    }

    private void initCatalog() {
        mCatalog.add(new TimberSpecies("Hard Maple (Sugar Maple)", 1450, "Diffuse-Porous Hardwood", "Highly prone to wave tearout when curly or quilted. High pitch recommended."));
        mCatalog.add(new TimberSpecies("Quartersawn White Oak", 1360, "Ring-Porous Hardwood", "Pronounced medullary rays. Directional cathedrals along ray flecks."));
        mCatalog.add(new TimberSpecies("Black Walnut", 1010, "Semi-Ring Porous", "Workable fiber structure, planes cleanly at 45° with sharp hone."));
        mCatalog.add(new TimberSpecies("Cherry (Black Cherry)", 950, "Diffuse-Porous Hardwood", "Smooth cutting, prone to burning under dull blades or high router friction."));
        mCatalog.add(new TimberSpecies("Honduran Mahogany", 800, "Tropical Hardwood", "Even grain, minimal tearout risk."));
        mCatalog.add(new TimberSpecies("Ipe (Brazilian Walnut)", 3680, "Extreme Dense Exotic", "Interlocked grain, severe tearout danger; requires scraper or 55°+ bevel."));
        mCatalog.add(new TimberSpecies("Eastern White Pine", 380, "Softwood", "Soft fibers tear if bevel is not razor sharp; compress fibers before planing."));
        mCatalog.add(new TimberSpecies("Purpleheart", 1860, "Dense Exotic", "Vigorous chip deflection, easily tears on reverse grain transitions."));
        mCatalog.add(new TimberSpecies("Teak", 1070, "Oily Hardwood", "High silica content dulls cutting edges rapidly causing micro-tearout."));
        mCatalog.add(new TimberSpecies("Ash (White Ash)", 1320, "Ring-Porous Hardwood", "Strong growth contrast between earlywood and latewood rings."));
    }

    public List<TimberSpecies> getAllSpecies() {
        return mCatalog;
    }

    public TimberSpecies fuzzyFindSpecies(String query) {
        if (query == null || query.trim().isEmpty()) {
            return mCatalog.isEmpty() ? null : mCatalog.get(0);
        }
        String cleanQuery = query.toLowerCase(Locale.US).trim();

        for (TimberSpecies s : mCatalog) {
            if (s.getName().toLowerCase(Locale.US).contains(cleanQuery)) {
                return s;
            }
        }
        for (TimberSpecies s : mCatalog) {
            if (s.getCategories().toLowerCase(Locale.US).contains(cleanQuery)) {
                return s;
            }
        }
        return null;
    }
}