package com.kimrosil.returnguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "return_guard_prefs";
    private static final String KEY_ITEMS_JSON = "return_items_json";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0: Auto, 1: Light, 2: Dark
    private static final String KEY_FILTER_MODE = "filter_mode"; // 0: Active, 1: Urgent, 2: Returned

    // UI Elements
    private TextView tvTotalMoneyAtRisk;
    private TextView tvActiveCount;
    private TextView tvUrgentCount;
    private EditText etItemName;
    private EditText etStoreName;
    private EditText etPrice;
    private EditText etReturnDays;
    private EditText etNotes;
    private Button btnDaysMinus;
    private Button btnDaysPlus;
    private Button btnSaveReturn;
    private Button chipFilterActive;
    private Button chipFilterUrgent;
    private Button chipFilterReturned;
    private LinearLayout llReturnsContainer;
    private TextView tvEmptyState;
    private Button btnThemeToggle;
    private ImageButton btnSettings;

    // State
    private int themeMode = 0;
    private int filterMode = 0;
    private final List<ReturnItem> items = new ArrayList<>();

    public static class ReturnItem {
        public String id;
        public String itemName;
        public String storeName;
        public double price;
        public long purchaseDateMillis;
        public int returnWindowDays;
        public String notes;
        public boolean isReturned;

        public ReturnItem(String itemName, String storeName, double price, int returnWindowDays, String notes) {
            this.id = UUID.randomUUID().toString();
            this.itemName = itemName;
            this.storeName = storeName;
            this.price = price;
            this.purchaseDateMillis = System.currentTimeMillis();
            this.returnWindowDays = returnWindowDays;
            this.notes = notes;
            this.isReturned = false;
        }

        public long getDeadlineMillis() {
            return purchaseDateMillis + (returnWindowDays * 24L * 60L * 60L * 1000L);
        }

        public int getDaysRemaining() {
            long diff = getDeadlineMillis() - System.currentTimeMillis();
            if (diff <= 0) return 0;
            return (int) (diff / (24L * 60L * 60L * 1000L));
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > getDeadlineMillis();
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("itemName", itemName);
                obj.put("storeName", storeName);
                obj.put("price", price);
                obj.put("purchaseDateMillis", purchaseDateMillis);
                obj.put("returnWindowDays", returnWindowDays);
                obj.put("notes", notes);
                obj.put("isReturned", isReturned);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static ReturnItem fromJson(JSONObject obj) {
            try {
                ReturnItem item = new ReturnItem(
                        obj.optString("itemName", "Item"),
                        obj.optString("storeName", "Store"),
                        obj.optDouble("price", 0.0),
                        obj.optInt("returnWindowDays", 30),
                        obj.optString("notes", "")
                );
                item.id = obj.optString("id", UUID.randomUUID().toString());
                item.purchaseDateMillis = obj.optLong("purchaseDateMillis", System.currentTimeMillis());
                item.isReturned = obj.optBoolean("isReturned", false);
                return item;
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        loadPreferences();
        applyAppTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();

        if (items.isEmpty()) {
            seedSampleData();
        }
        renderTelemetryAndList();
    }

    private void initViews() {
        tvTotalMoneyAtRisk = findViewById(R.id.tvTotalMoneyAtRisk);
        tvActiveCount = findViewById(R.id.tvActiveCount);
        tvUrgentCount = findViewById(R.id.tvUrgentCount);
        etItemName = findViewById(R.id.etItemName);
        etStoreName = findViewById(R.id.etStoreName);
        etPrice = findViewById(R.id.etPrice);
        etReturnDays = findViewById(R.id.etReturnDays);
        etNotes = findViewById(R.id.etNotes);
        btnDaysMinus = findViewById(R.id.btnDaysMinus);
        btnDaysPlus = findViewById(R.id.btnDaysPlus);
        btnSaveReturn = findViewById(R.id.btnSaveReturn);
        chipFilterActive = findViewById(R.id.chipFilterActive);
        chipFilterUrgent = findViewById(R.id.chipFilterUrgent);
        chipFilterReturned = findViewById(R.id.chipFilterReturned);
        llReturnsContainer = findViewById(R.id.llReturnsContainer);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSettings = findViewById(R.id.btnSettings);

        updateThemeToggleButton();
    }

    private void setupListeners() {
        btnDaysMinus.setOnClickListener(v -> adjustReturnDays(-5));
        btnDaysPlus.setOnClickListener(v -> adjustReturnDays(5));

        btnSaveReturn.setOnClickListener(v -> handleSaveReturn());

        chipFilterActive.setOnClickListener(v -> setFilterMode(0));
        chipFilterUrgent.setOnClickListener(v -> setFilterMode(1));
        chipFilterReturned.setOnClickListener(v -> setFilterMode(2));

        btnThemeToggle.setOnClickListener(v -> toggleTheme());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void adjustReturnDays(int delta) {
        String val = etReturnDays.getText().toString().trim();
        int days = 30;
        try {
            days = Integer.parseInt(val);
        } catch (NumberFormatException ignored) {}
        days = Math.max(1, Math.min(365, days + delta));
        etReturnDays.setText(String.valueOf(days));
    }

    private void handleSaveReturn() {
        String name = etItemName.getText().toString().trim();
        String store = etStoreName.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String daysStr = etReturnDays.getText().toString().trim();
        String notes = etNotes.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter an item name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (store.isEmpty()) {
            store = "Store";
        }

        double price = 0.0;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException ignored) {}

        int days = 30;
        try {
            days = Integer.parseInt(daysStr);
        } catch (NumberFormatException ignored) {}

        ReturnItem newItem = new ReturnItem(name, store, price, days, notes);
        items.add(0, newItem);
        saveState();

        etItemName.setText("");
        etStoreName.setText("");
        etPrice.setText("");
        etNotes.setText("");

        renderTelemetryAndList();
        Toast.makeText(this, "Protected: " + name + " (" + days + "d return window)", Toast.LENGTH_SHORT).show();
    }

    private void setFilterMode(int mode) {
        filterMode = mode;
        chipFilterActive.setBackgroundResource(mode == 0 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipFilterActive.setTextColor(getResources().getColor(mode == 0 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipFilterUrgent.setBackgroundResource(mode == 1 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipFilterUrgent.setTextColor(getResources().getColor(mode == 1 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipFilterReturned.setBackgroundResource(mode == 2 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipFilterReturned.setTextColor(getResources().getColor(mode == 2 ? R.color.m3_on_primary : R.color.m3_on_surface));

        saveState();
        renderTelemetryAndList();
    }

    private void renderTelemetryAndList() {
        double moneyAtRisk = 0.0;
        int activeCount = 0;
        int urgentCount = 0;

        for (ReturnItem item : items) {
            if (!item.isReturned && !item.isExpired()) {
                moneyAtRisk += item.price;
                activeCount++;
                if (item.getDaysRemaining() <= 7) {
                    urgentCount++;
                }
            }
        }

        tvTotalMoneyAtRisk.setText(String.format(Locale.US, "$%.2f", moneyAtRisk));
        tvActiveCount.setText(String.valueOf(activeCount));
        tvUrgentCount.setText(String.valueOf(urgentCount));

        // Render Cards
        llReturnsContainer.removeAllViews();
        List<ReturnItem> filtered = new ArrayList<>();
        for (ReturnItem item : items) {
            if (filterMode == 0 && !item.isReturned) {
                filtered.add(item);
            } else if (filterMode == 1 && !item.isReturned && item.getDaysRemaining() <= 7 && !item.isExpired()) {
                filtered.add(item);
            } else if (filterMode == 2 && item.isReturned) {
                filtered.add(item);
            }
        }

        if (filtered.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            for (ReturnItem item : filtered) {
                llReturnsContainer.addView(createReturnCardView(item));
            }
        }
    }

    private View createReturnCardView(ReturnItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_m3);
        card.setPadding(32, 28, 32, 28);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 24);
        card.setLayoutParams(lp);

        // Header Row: Item Name, Price, and Urgency Badge
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        infoCol.setLayoutParams(infoLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(item.itemName);
        tvTitle.setTextSize(17);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(getResources().getColor(R.color.m3_on_surface));

        TextView tvSub = new TextView(this);
        tvSub.setText(String.format(Locale.US, "%s • $%.2f", item.storeName, item.price));
        tvSub.setTextSize(13);
        tvSub.setTextColor(getResources().getColor(R.color.m3_on_surface_variant));

        infoCol.addView(tvTitle);
        infoCol.addView(tvSub);

        // Badge
        TextView tvBadge = new TextView(this);
        tvBadge.setTextSize(11);
        tvBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvBadge.setTextColor(Color.WHITE);
        tvBadge.setPadding(20, 10, 20, 10);

        if (item.isReturned) {
            tvBadge.setText("RETURNED");
            tvBadge.setBackgroundResource(R.drawable.bg_badge_returned);
        } else if (item.isExpired()) {
            tvBadge.setText("EXPIRED");
            tvBadge.setBackgroundResource(R.drawable.bg_badge_critical);
        } else {
            int days = item.getDaysRemaining();
            if (days <= 3) {
                tvBadge.setText(days + "d LEFT (CRITICAL)");
                tvBadge.setBackgroundResource(R.drawable.bg_badge_critical);
            } else if (days <= 7) {
                tvBadge.setText(days + "d LEFT (URGENT)");
                tvBadge.setBackgroundResource(R.drawable.bg_badge_warning);
            } else {
                tvBadge.setText(days + "d LEFT");
                tvBadge.setBackgroundResource(R.drawable.bg_badge_safe);
            }
        }

        topRow.addView(infoCol);
        topRow.addView(tvBadge);
        card.addView(topRow);

        // Notes if any
        if (item.notes != null && !item.notes.isEmpty()) {
            TextView tvNotes = new TextView(this);
            tvNotes.setText("Note: " + item.notes);
            tvNotes.setTextSize(12);
            tvNotes.setTextColor(getResources().getColor(R.color.m3_on_surface_variant));
            tvNotes.setPadding(0, 12, 0, 0);
            card.addView(tvNotes);
        }

        // Actions Row
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setPadding(0, 18, 0, 0);

        Button btnToggleReturned = new Button(this);
        btnToggleReturned.setText(item.isReturned ? "Mark Active" : "Mark Returned");
        btnToggleReturned.setTextSize(12);
        btnToggleReturned.setTextColor(getResources().getColor(R.color.m3_primary));
        btnToggleReturned.setBackgroundResource(R.drawable.btn_m3_outlined);
        btnToggleReturned.setMinHeight(120);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        btnLp.setMargins(0, 0, 12, 0);
        btnToggleReturned.setLayoutParams(btnLp);
        btnToggleReturned.setOnClickListener(v -> {
            item.isReturned = !item.isReturned;
            saveState();
            renderTelemetryAndList();
        });

        Button btnDelete = new Button(this);
        btnDelete.setText("Delete");
        btnDelete.setTextSize(12);
        btnDelete.setTextColor(getResources().getColor(R.color.m3_error));
        btnDelete.setBackgroundResource(R.drawable.btn_m3_outlined);
        btnDelete.setMinHeight(120);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f);
        btnDelete.setLayoutParams(delLp);
        btnDelete.setOnClickListener(v -> {
            items.remove(item);
            saveState();
            renderTelemetryAndList();
            Toast.makeText(this, "Removed " + item.itemName, Toast.LENGTH_SHORT).show();
        });

        actionsRow.addView(btnToggleReturned);
        actionsRow.addView(btnDelete);
        card.addView(actionsRow);

        return card;
    }

    private void seedSampleData() {
        items.add(new ReturnItem("Wireless Headphones", "Best Buy", 89.99, 15, "Check receipt for 15-day policy"));
        items.add(new ReturnItem("Wool Overcoat", "Zara", 129.50, 30, "Original tags attached"));
        items.add(new ReturnItem("Espresso Machine", "Target", 199.99, 90, "Box unopened in trunk"));
    }

    private void toggleTheme() {
        themeMode = (themeMode + 1) % 3;
        saveState();
        recreate();
    }

    private void updateThemeToggleButton() {
        switch (themeMode) {
            case 1:
                btnThemeToggle.setText("Theme: Light");
                break;
            case 2:
                btnThemeToggle.setText("Theme: Dark");
                break;
            default:
                btnThemeToggle.setText("Theme: Auto");
                break;
        }
    }

    private void applyAppTheme() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.UiModeManager uiModeManager = (android.app.UiModeManager) getSystemService(android.content.Context.UI_MODE_SERVICE);
            if (uiModeManager != null) {
                if (themeMode == 1) {
                    uiModeManager.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_NO);
                } else if (themeMode == 2) {
                    uiModeManager.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_YES);
                } else {
                    uiModeManager.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_AUTO);
                }
            }
        }

        android.content.res.Configuration config = new android.content.res.Configuration(getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO | (config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK);
        } else if (themeMode == 2) {
            config.uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES | (config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK);
        }
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("ReturnGuard")
                .setMessage("Receipt & Return Countdown Vault\nVersion 1.0.0\n\nKimRosil App Studio Portfolio freeware.\n100% Offline, on-device data storage.\n\nNever miss an item return deadline again.")
                .setPositiveButton("Close", null)
                .setNeutralButton("Reset Sample Data", (dialog, which) -> {
                    items.clear();
                    seedSampleData();
                    saveState();
                    renderTelemetryAndList();
                    Toast.makeText(MainActivity.this, "Reset to default sample purchases", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void saveState() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putInt(KEY_THEME_MODE, themeMode);
        ed.putInt(KEY_FILTER_MODE, filterMode);

        JSONArray arr = new JSONArray();
        for (ReturnItem item : items) {
            arr.put(item.toJson());
        }
        ed.putString(KEY_ITEMS_JSON, arr.toString());
        ed.commit();
    }

    private void loadPreferences() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        themeMode = sp.getInt(KEY_THEME_MODE, 0);
        filterMode = sp.getInt(KEY_FILTER_MODE, 0);

        items.clear();
        String json = sp.getString(KEY_ITEMS_JSON, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    ReturnItem item = ReturnItem.fromJson(arr.getJSONObject(i));
                    if (item != null) {
                        items.add(item);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveState();
    }
}
