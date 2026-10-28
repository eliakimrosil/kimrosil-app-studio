package com.kimrosil.whohasmy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
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
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "who_has_my_prefs";
    private static final String KEY_ITEMS_JSON = "lent_items_json";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0: Auto, 1: Light, 2: Dark
    private static final String KEY_FILTER_MODE = "filter_mode"; // 0: Active, 1: Overdue, 2: Returned

    // UI Elements
    private TextView tvActiveLentCount;
    private TextView tvOverdueCount;
    private EditText etItemName;
    private EditText etBorrowerName;
    private EditText etBorrowerContact;
    private EditText etLendDays;
    private EditText etNotes;
    private Button btnDaysMinus;
    private Button btnDaysPlus;
    private Button btnSaveLentItem;
    private Button chipFilterActive;
    private Button chipFilterOverdue;
    private Button chipFilterReturned;
    private LinearLayout llItemsContainer;
    private TextView tvEmptyState;
    private Button btnThemeToggle;
    private ImageButton btnSettings;

    // State
    private int themeMode = 0;
    private int filterMode = 0;
    private final List<LentItem> items = new ArrayList<>();

    public static class LentItem {
        public String id;
        public String itemName;
        public String borrowerName;
        public String borrowerContact;
        public long lentDateEpochMs;
        public int lendDays;
        public String notes;
        public boolean isReturned;

        public LentItem(String itemName, String borrowerName, String borrowerContact, int lendDays, String notes) {
            this.id = UUID.randomUUID().toString();
            this.itemName = itemName;
            this.borrowerName = borrowerName;
            this.borrowerContact = borrowerContact;
            this.lentDateEpochMs = System.currentTimeMillis();
            this.lendDays = lendDays;
            this.notes = notes;
            this.isReturned = false;
        }

        public long getDueDateEpochMs() {
            return lentDateEpochMs + (lendDays * 24L * 60L * 60L * 1000L);
        }

        public int getDaysRemaining() {
            long diff = getDueDateEpochMs() - System.currentTimeMillis();
            if (diff <= 0) return 0;
            return (int) (diff / (24L * 60L * 60L * 1000L));
        }

        public boolean isOverdue() {
            return System.currentTimeMillis() > getDueDateEpochMs();
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("itemName", itemName);
                obj.put("borrowerName", borrowerName);
                obj.put("borrowerContact", borrowerContact);
                obj.put("lentDateEpochMs", lentDateEpochMs);
                obj.put("lendDays", lendDays);
                obj.put("notes", notes);
                obj.put("isReturned", isReturned);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static LentItem fromJson(JSONObject obj) {
            try {
                LentItem item = new LentItem(
                        obj.optString("itemName", "Tool"),
                        obj.optString("borrowerName", "Neighbor"),
                        obj.optString("borrowerContact", ""),
                        obj.optInt("lendDays", 7),
                        obj.optString("notes", "")
                );
                item.id = obj.optString("id", UUID.randomUUID().toString());
                item.lentDateEpochMs = obj.optLong("lentDateEpochMs", System.currentTimeMillis());
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
        tvActiveLentCount = findViewById(R.id.tvActiveLentCount);
        tvOverdueCount = findViewById(R.id.tvOverdueCount);
        etItemName = findViewById(R.id.etItemName);
        etBorrowerName = findViewById(R.id.etBorrowerName);
        etBorrowerContact = findViewById(R.id.etBorrowerContact);
        etLendDays = findViewById(R.id.etLendDays);
        etNotes = findViewById(R.id.etNotes);
        btnDaysMinus = findViewById(R.id.btnDaysMinus);
        btnDaysPlus = findViewById(R.id.btnDaysPlus);
        btnSaveLentItem = findViewById(R.id.btnSaveLentItem);
        chipFilterActive = findViewById(R.id.chipFilterActive);
        chipFilterOverdue = findViewById(R.id.chipFilterOverdue);
        chipFilterReturned = findViewById(R.id.chipFilterReturned);
        llItemsContainer = findViewById(R.id.llItemsContainer);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSettings = findViewById(R.id.btnSettings);

        updateThemeToggleButton();
    }

    private void setupListeners() {
        btnDaysMinus.setOnClickListener(v -> adjustLendDays(-1));
        btnDaysPlus.setOnClickListener(v -> adjustLendDays(1));

        btnSaveLentItem.setOnClickListener(v -> handleSaveLentItem());

        chipFilterActive.setOnClickListener(v -> setFilterMode(0));
        chipFilterOverdue.setOnClickListener(v -> setFilterMode(1));
        chipFilterReturned.setOnClickListener(v -> setFilterMode(2));

        btnThemeToggle.setOnClickListener(v -> toggleTheme());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void adjustLendDays(int delta) {
        String val = etLendDays.getText().toString().trim();
        int days = 7;
        try {
            days = Integer.parseInt(val);
        } catch (NumberFormatException ignored) {}
        days = Math.max(1, Math.min(365, days + delta));
        etLendDays.setText(String.valueOf(days));
    }

    private void handleSaveLentItem() {
        String item = etItemName.getText().toString().trim();
        String borrower = etBorrowerName.getText().toString().trim();
        String contact = etBorrowerContact.getText().toString().trim();
        String daysStr = etLendDays.getText().toString().trim();
        String notes = etNotes.getText().toString().trim();

        if (item.isEmpty()) {
            Toast.makeText(this, "Please enter an equipment name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (borrower.isEmpty()) {
            borrower = "Borrower";
        }

        int days = 7;
        try {
            days = Integer.parseInt(daysStr);
        } catch (NumberFormatException ignored) {}

        LentItem newItem = new LentItem(item, borrower, contact, days, notes);
        items.add(0, newItem);
        saveState();

        etItemName.setText("");
        etBorrowerName.setText("");
        etBorrowerContact.setText("");
        etNotes.setText("");

        renderTelemetryAndList();
        Toast.makeText(this, "Logged: " + item + " lent to " + borrower, Toast.LENGTH_SHORT).show();
    }

    private void setFilterMode(int mode) {
        filterMode = mode;
        chipFilterActive.setBackgroundResource(mode == 0 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipFilterActive.setTextColor(getResources().getColor(mode == 0 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipFilterOverdue.setBackgroundResource(mode == 1 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipFilterOverdue.setTextColor(getResources().getColor(mode == 1 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipFilterReturned.setBackgroundResource(mode == 2 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipFilterReturned.setTextColor(getResources().getColor(mode == 2 ? R.color.m3_on_primary : R.color.m3_on_surface));

        saveState();
        renderTelemetryAndList();
    }

    private void renderTelemetryAndList() {
        int activeCount = 0;
        int overdueCount = 0;

        for (LentItem item : items) {
            if (!item.isReturned) {
                activeCount++;
                if (item.isOverdue()) {
                    overdueCount++;
                }
            }
        }

        tvActiveLentCount.setText(String.valueOf(activeCount));
        tvOverdueCount.setText(String.valueOf(overdueCount));

        // Render Cards
        llItemsContainer.removeAllViews();
        List<LentItem> filtered = new ArrayList<>();
        for (LentItem item : items) {
            if (filterMode == 0 && !item.isReturned) {
                filtered.add(item);
            } else if (filterMode == 1 && !item.isReturned && item.isOverdue()) {
                filtered.add(item);
            } else if (filterMode == 2 && item.isReturned) {
                filtered.add(item);
            }
        }

        if (filtered.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            for (LentItem item : filtered) {
                llItemsContainer.addView(createLentItemCardView(item));
            }
        }
    }

    private View createLentItemCardView(LentItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_m3);
        card.setPadding(32, 28, 32, 28);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 24);
        card.setLayoutParams(lp);

        // Header Row: Item Title & Urgency Badge
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
        String contactInfo = item.borrowerContact.isEmpty() ? "" : " (" + item.borrowerContact + ")";
        tvSub.setText("Lent to " + item.borrowerName + contactInfo);
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
        } else if (item.isOverdue()) {
            tvBadge.setText("OVERDUE");
            tvBadge.setBackgroundResource(R.drawable.bg_badge_critical);
        } else {
            int days = item.getDaysRemaining();
            if (days <= 2) {
                tvBadge.setText(days + "d LEFT");
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

        Button btnRemind = new Button(this);
        btnRemind.setText("Remind");
        btnRemind.setTextSize(12);
        btnRemind.setTextColor(getResources().getColor(R.color.m3_primary));
        btnRemind.setBackgroundResource(R.drawable.btn_m3_outlined);
        btnRemind.setMinHeight(120);
        LinearLayout.LayoutParams remindLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f);
        remindLp.setMargins(0, 0, 8, 0);
        btnRemind.setLayoutParams(remindLp);
        btnRemind.setOnClickListener(v -> sendReminder(item));

        Button btnToggleReturned = new Button(this);
        btnToggleReturned.setText(item.isReturned ? "Active" : "Returned");
        btnToggleReturned.setTextSize(12);
        btnToggleReturned.setTextColor(getResources().getColor(R.color.m3_primary));
        btnToggleReturned.setBackgroundResource(R.drawable.btn_m3_outlined);
        btnToggleReturned.setMinHeight(120);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        btnLp.setMargins(0, 0, 8, 0);
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
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f);
        btnDelete.setLayoutParams(delLp);
        btnDelete.setOnClickListener(v -> {
            items.remove(item);
            saveState();
            renderTelemetryAndList();
            Toast.makeText(this, "Removed " + item.itemName, Toast.LENGTH_SHORT).show();
        });

        actionsRow.addView(btnRemind);
        actionsRow.addView(btnToggleReturned);
        actionsRow.addView(btnDelete);
        card.addView(actionsRow);

        return card;
    }

    private void sendReminder(LentItem item) {
        String msg = "Hi " + item.borrowerName + ", just checking in on the " + item.itemName + " when you get a chance. Thanks!";
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, msg);
        sendIntent.setType("text/plain");
        try {
            startActivity(Intent.createChooser(sendIntent, "Send Lending Reminder"));
        } catch (Exception e) {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }

    private void seedSampleData() {
        items.add(new LentItem("Dewalt 20V Cordless Drill", "Dave (Neighbor)", "555-0144", 7, "Includes charger and 2 batteries"));
        items.add(new LentItem("16ft Extension Ladder", "Marcus", "555-0182", 14, "Aluminum heavy-duty"));
        items.add(new LentItem("Catan Board Game", "Emma", "", 5, "With 5-6 player expansion"));
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            if (uiModeManager != null) {
                if (themeMode == 1) {
                    uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
                } else if (themeMode == 2) {
                    uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
                } else {
                    uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);
                }
            }
        }

        Configuration config = new Configuration(getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = Configuration.UI_MODE_NIGHT_NO | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        } else if (themeMode == 2) {
            config.uiMode = Configuration.UI_MODE_NIGHT_YES | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        }
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("WhoHasMy")
                .setMessage("Tool & Equipment Lending Ledger\nVersion 1.0.0\n\nKimRosil App Studio Portfolio freeware.\n100% Offline, on-device data storage.\n\nKeep track of who borrowed your gear.")
                .setPositiveButton("Close", null)
                .setNeutralButton("Reset Sample Data", (dialog, which) -> {
                    items.clear();
                    seedSampleData();
                    saveState();
                    renderTelemetryAndList();
                    Toast.makeText(MainActivity.this, "Reset to default sample lent items", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void saveState() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putInt(KEY_THEME_MODE, themeMode);
        ed.putInt(KEY_FILTER_MODE, filterMode);

        JSONArray arr = new JSONArray();
        for (LentItem item : items) {
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
                    LentItem item = LentItem.fromJson(arr.getJSONObject(i));
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
