package com.kimrosil.krhabit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Chroma Palette & Dynamic Accent Engine for KR Habit.
 * Provides curated domain colors and tactile check-in / heatmap drawables.
 */
public final class HabitColors {

    // 8 Curated Chroma Accent Colors (Loop Habit Tracker Inspired)
    public static final String[] PALETTE = new String[]{
            "#10B981", // Emerald (Health, Wellness, Mindfulness)
            "#2563EB", // Azure Blue (Deep Focus, Reading, Writing)
            "#D97706", // Solar Amber (Energy, Morning Routine, Sunrise)
            "#7C3AED", // Amethyst Purple (Meditation, Creativity, Journaling)
            "#E11D48", // Rose Coral (Fitness, Workout, Cardio)
            "#0D9488", // Ocean Teal (Hydration, Recovery, Cold Plunge)
            "#EA580C", // Sunset Orange (Productivity, Coding, Sprints)
            "#4F46E5"  // Indigo (Night Routine, Sleep Hygiene)
    };

    public static final String DEFAULT_COLOR = PALETTE[0];

    private HabitColors() {}

    public static String getColorForIndex(int index) {
        if (index < 0) index = 0;
        return PALETTE[index % PALETTE.length];
    }

    public static int parseColor(String hex, int defaultColor) {
        if (hex == null || hex.trim().isEmpty()) return defaultColor;
        try {
            return Color.parseColor(hex.trim());
        } catch (Exception e) {
            return defaultColor;
        }
    }

    public static int parseColor(String hex) {
        return parseColor(hex, Color.parseColor(DEFAULT_COLOR));
    }

    /**
     * Creates a tactile rounded-rectangle RippleDrawable for completed check-ins.
     */
    public static Drawable createCheckInDoneDrawable(Context context, int habitColor, float radiusDp) {
        float density = context.getResources().getDisplayMetrics().density;
        float radiusPx = radiusDp * density;

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radiusPx);
        shape.setColor(habitColor);

        int rippleColor = Color.argb(80, 255, 255, 255);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, null);
    }

    /**
     * Creates a subtle elevated RippleDrawable for pending check-ins.
     */
    public static Drawable createCheckInPendingDrawable(Context context, int habitColor, float radiusDp) {
        float density = context.getResources().getDisplayMetrics().density;
        float radiusPx = radiusDp * density;

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radiusPx);
        shape.setColor(context.getColor(R.color.bg_surface_elevated));
        shape.setStroke((int) (1.2f * density), context.getColor(R.color.border_hairline));

        int rippleColor = Color.argb(40, Color.red(habitColor), Color.green(habitColor), Color.blue(habitColor));
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, null);
    }

    public interface OnColorSelectedListener {
        void onColorSelected(String colorHex);
    }

    /**
     * Populates a horizontal color picker with circular chips.
     */
    public static void populateColorPicker(final Context context,
                                           final LinearLayout container,
                                           final String selectedHex,
                                           final OnColorSelectedListener listener) {
        container.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (32 * density);
        int marginPx = (int) (5 * density);
        String current = (selectedHex == null || selectedHex.isEmpty()) ? DEFAULT_COLOR : selectedHex;

        for (int i = 0; i < PALETTE.length; i++) {
            final String hex = PALETTE[i];
            final int colorVal = Color.parseColor(hex);
            final boolean isSelected = hex.equalsIgnoreCase(current);

            final TextView chip = new TextView(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(marginPx, 0, marginPx, 0);
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER);

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(colorVal);

            if (isSelected) {
                circle.setStroke((int) (2.5f * density), Color.WHITE);
                chip.setText("✓");
                chip.setTextColor(Color.WHITE);
                chip.setTextSize(14f);
            } else {
                chip.setText("");
            }

            chip.setBackground(circle);
            chip.setClickable(true);
            chip.setFocusable(true);

            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onColorSelected(hex);
                    }
                    populateColorPicker(context, container, hex, listener);
                }
            });

            container.addView(chip);
        }
    }
}
