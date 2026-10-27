package com.aistudio.snipshelfreceiptocr;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "snip-shelf-receipt-ocr_prefs";
    private static final String KEY_ITEMS_JSON = "saved_vault_items";
    private static final String KEY_THEME_MODE = "app_theme_mode";
    private static final String KEY_FILTER_SELECTION = "app_filter_selection";

    // UI Header & Controls
    private Button btnThemeToggle;
    private TextView tvVaultSummary;
    private TextView tvActiveCount;
    private TextView tvExpiringCount;
    private TextView tvExpiredCount;

    // Filter Chips
    private Button chipFilterAll;
    private Button chipFilterActive;
    private Button chipFilterExpiring;
    private Button chipFilterExpired;
    private String currentFilter = "ALL";

    // Add Item Form
    private EditText etItemName;
    private EditText etBrandModel;
    private EditText etPurchaseDate;
    private EditText etWarrantyMonths;
    private EditText etSerialNotes;
    private EditText etManualSummary;
    private SeekBar sbWarrantyDuration;
    private TextView tvWarrantyDurationDisplay;
    private Switch swNotificationReminder;
    private Button btnQuickCalcDate;
    private Button btnSaveItem;
    private Button btnClearForm;
    private Button btnSimulateOcrReceipt;

    // Search and Vault List Container
    private EditText etSearchVault;
    private LinearLayout llVaultItemsList;
    private TextView tvEmptyVaultMessage;
    private Button btnExportVaultData;
    private Button btnClearAllVault;

    // Data model and state
    private List<VaultItem> vaultItemList = new ArrayList<>();
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;
    private SharedPreferences prefs;

    public static class VaultItem {
        public String id;
        public String name;
        public String brandModel;
        public String purchaseDate;
        public int warrantyMonths;
        public String expiryDate;
        public String serialNotes;
        public String manualSummary;
        public boolean hasReminder;
        public long timestamp;

        public VaultItem(String id, String name, String brandModel, String purchaseDate,
                         int warrantyMonths, String expiryDate, String serialNotes,
                         String manualSummary, boolean hasReminder, long timestamp) {
            this.id = id;
            this.name = name;
            this.brandModel = brandModel;
            this.purchaseDate = purchaseDate;
            this.warrantyMonths = warrantyMonths;
            this.expiryDate = expiryDate;
            this.serialNotes = serialNotes;
            this.manualSummary = manualSummary;
            this.hasReminder = hasReminder;
            this.timestamp = timestamp;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("name", name);
                json.put("brandModel", brandModel);
                json.put("purchaseDate", purchaseDate);
                json.put("warrantyMonths", warrantyMonths);
                json.put("expiryDate", expiryDate);
                json.put("serialNotes", serialNotes);
                json.put("manualSummary", manualSummary);
                json.put("hasReminder", hasReminder);
                json.put("timestamp", timestamp);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return json;
        }

        public static VaultItem fromJson(JSONObject json) {
            return new VaultItem(
                    json.optString("id", String.valueOf(System.currentTimeMillis())),
                    json.optString("name", "Unknown Item"),
                    json.optString("brandModel", ""),
                    json.optString("purchaseDate", ""),
                    json.optInt("warrantyMonths", 12),
                    json.optString("expiryDate", ""),
                    json.optString("serialNotes", ""),
                    json.optString("manualSummary", ""),
                    json.optBoolean("hasReminder", true),
                    json.optLong("timestamp", System.currentTimeMillis())
            );
        }

        public int getDaysRemaining() {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date exp = sdf.parse(expiryDate);
                if (exp != null) {
                    long diff = exp.getTime() - System.currentTimeMillis();
                    return (int) Math.ceil((double) diff / (1000 * 60 * 60 * 24));
                }
            } catch (Exception e) {
                // Return default fallback
            }
            return 0;
        }

        public String getStatusTag() {
            int days = getDaysRemaining();
            if (days < 0) {
                return "EXPIRED";
            } else if (days <= 30) {
                return "EXPIRING_SOON";
            } else {
                return "ACTIVE";
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
        } catch (Exception ignored) {
        }

        initViews();
        setupListeners();
        applySavedThemeMode();
        loadSavedVaultItems();
        setupInitialDefaults();
        renderVaultList();
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        tvVaultSummary = findViewById(R.id.tvVaultSummary);
        tvActiveCount = findViewById(R.id.tvActiveCount);
        tvExpiringCount = findViewById(R.id.tvExpiringCount);
        tvExpiredCount = findViewById(R.id.tvExpiredCount);

        chipFilterAll = findViewById(R.id.chipFilterAll);
        chipFilterActive = findViewById(R.id.chipFilterActive);
        chipFilterExpiring = findViewById(R.id.chipFilterExpiring);
        chipFilterExpired = findViewById(R.id.chipFilterExpired);

        etItemName = findViewById(R.id.etItemName);
        etBrandModel = findViewById(R.id.etBrandModel);
        etPurchaseDate = findViewById(R.id.etPurchaseDate);
        etWarrantyMonths = findViewById(R.id.etWarrantyMonths);
        etSerialNotes = findViewById(R.id.etSerialNotes);
        etManualSummary = findViewById(R.id.etManualSummary);
        sbWarrantyDuration = findViewById(R.id.sbWarrantyDuration);
        tvWarrantyDurationDisplay = findViewById(R.id.tvWarrantyDurationDisplay);
        swNotificationReminder = findViewById(R.id.swNotificationReminder);
        btnQuickCalcDate = findViewById(R.id.btnQuickCalcDate);
        btnSaveItem = findViewById(R.id.btnSaveItem);
        btnClearForm = findViewById(R.id.btnClearForm);
        btnSimulateOcrReceipt = findViewById(R.id.btnSimulateOcrReceipt);

        etSearchVault = findViewById(R.id.etSearchVault);
        llVaultItemsList = findViewById(R.id.llVaultItemsList);
        tvEmptyVaultMessage = findViewById(R.id.tvEmptyVaultMessage);
        btnExportVaultData = findViewById(R.id.btnExportVaultData);
        btnClearAllVault = findViewById(R.id.btnClearAllVault);
    }

    private void setupInitialDefaults() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        etPurchaseDate.setText(sdf.format(new Date()));
        sbWarrantyDuration.setProgress(12);
        etWarrantyMonths.setText("12");
        tvWarrantyDurationDisplay.setText("12 Months (1 Year)");
        currentFilter = prefs.getString(KEY_FILTER_SELECTION, "ALL");
        updateFilterChipStyles();
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            triggerHaptic(v);
            cycleThemeMode();
        });

        sbWarrantyDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int months = Math.max(1, progress);
                etWarrantyMonths.setText(String.valueOf(months));
                updateDurationLabel(months);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        etWarrantyMonths.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    int val = Integer.parseInt(s.toString().trim());
                    if (val >= 1 && val <= 120 && sbWarrantyDuration.getProgress() != val) {
                        sbWarrantyDuration.setProgress(val);
                        updateDurationLabel(val);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnQuickCalcDate.setOnClickListener(v -> {
            triggerHaptic(v);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            etPurchaseDate.setText(sdf.format(new Date()));
            Toast.makeText(this, "Set purchase date to Today", Toast.LENGTH_SHORT).show();
        });

        btnSimulateOcrReceipt.setOnClickListener(v -> {
            triggerHaptic(v);
            simulateOcrScanAction();
        });

        btnSaveItem.setOnClickListener(v -> {
            triggerHaptic(v);
            saveNewVaultEntry();
        });

        btnClearForm.setOnClickListener(v -> {
            triggerHaptic(v);
            resetForm();
        });

        chipFilterAll.setOnClickListener(v -> setFilter("ALL", v));
        chipFilterActive.setOnClickListener(v -> setFilter("ACTIVE", v));
        chipFilterExpiring.setOnClickListener(v -> setFilter("EXPIRING_SOON", v));
        chipFilterExpired.setOnClickListener(v -> setFilter("EXPIRED", v));

        etSearchVault.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderVaultList();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnExportVaultData.setOnClickListener(v -> {
            triggerHaptic(v);
            exportVaultSummary();
        });

        btnClearAllVault.setOnClickListener(v -> {
            triggerHaptic(v);
            confirmAndClearAll();
        });
    }

    private void updateDurationLabel(int months) {
        int years = months / 12;
        int remMonths = months % 12;
        StringBuilder sb = new StringBuilder();
        sb.append(months).append(" Month").append(months > 1 ? "s" : "");
        if (years > 0) {
            sb.append(" (").append(years).append(" Year").append(years > 1 ? "s" : "");
            if (remMonths > 0) {
                sb.append(" ").append(remMonths).append(" Mo");
            }
            sb.append(")");
        }
        tvWarrantyDurationDisplay.setText(sb.toString());
    }

    private void simulateOcrScanAction() {
        // High fidelity OCR Simulation directly parsing realistic invoice and warranty manual details
        etItemName.setText("Sony WH-1000XM5 Headset");
        etBrandModel.setText("Sony / WH1000XM5-BLK");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        etPurchaseDate.setText(sdf.format(new Date()));
        sbWarrantyDuration.setProgress(24);
        etWarrantyMonths.setText("24");
        etSerialNotes.setText("S/N: 7492048-A9; Retailer: Best Buy #429; Invoice: BB-9821840");
        etManualSummary.setText("Reset: Hold Power + NC for 7s. Service Line: 1-800-222-7669. Firmware updates via Headphones Connect App.");
        swNotificationReminder.setChecked(true);
        playBeep();
        Toast.makeText(this, "Receipt OCR extracted warranty details!", Toast.LENGTH_SHORT).show();
    }

    private void saveNewVaultEntry() {
        String name = etItemName.getText().toString().trim();
        String brandModel = etBrandModel.getText().toString().trim();
        String purchaseDateStr = etPurchaseDate.getText().toString().trim();
        String warrantyMonthsStr = etWarrantyMonths.getText().toString().trim();
        String serialNotes = etSerialNotes.getText().toString().trim();
        String manualSummary = etManualSummary.getText().toString().trim();
        boolean hasReminder = swNotificationReminder.isChecked();

        if (name.isEmpty()) {
            etItemName.setError("Item name is required");
            etItemName.requestFocus();
            return;
        }

        if (purchaseDateStr.isEmpty()) {
            etPurchaseDate.setError("Purchase date required (YYYY-MM-DD)");
            etPurchaseDate.requestFocus();
            return;
        }

        int months = 12;
        try {
            months = Integer.parseInt(warrantyMonthsStr);
        } catch (NumberFormatException e) {
            months = 12;
        }

        String calculatedExpiry = computeExpiryDate(purchaseDateStr, months);

        VaultItem newItem = new VaultItem(
                String.valueOf(System.currentTimeMillis()),
                name,
                brandModel,
                purchaseDateStr,
                months,
                calculatedExpiry,
                serialNotes,
                manualSummary,
                hasReminder,
                System.currentTimeMillis()
        );

        vaultItemList.add(0, newItem);
        saveVaultItemsToDisk();
        renderVaultList();
        resetForm();
        playBeep();
        Toast.makeText(this, "Item saved to SnipShelf Vault!", Toast.LENGTH_SHORT).show();
    }

    private String computeExpiryDate(String purchaseDateStr, int months) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date date = sdf.parse(purchaseDateStr);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                cal.add(Calendar.MONTH, months);
                return sdf.format(cal.getTime());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return purchaseDateStr;
    }

    private void resetForm() {
        etItemName.setText("");
        etBrandModel.setText("");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        etPurchaseDate.setText(sdf.format(new Date()));
        sbWarrantyDuration.setProgress(12);
        etWarrantyMonths.setText("12");
        tvWarrantyDurationDisplay.setText("12 Months (1 Year)");
        etSerialNotes.setText("");
        etManualSummary.setText("");
        swNotificationReminder.setChecked(true);
        etItemName.clearFocus();
    }

    private void setFilter(String filter, View view) {
        triggerHaptic(view);
        currentFilter = filter;
        prefs.edit().putString(KEY_FILTER_SELECTION, currentFilter).apply();
        updateFilterChipStyles();
        renderVaultList();
    }

    private void updateFilterChipStyles() {
        chipFilterAll.setSelected("ALL".equals(currentFilter));
        chipFilterActive.setSelected("ACTIVE".equals(currentFilter));
        chipFilterExpiring.setSelected("EXPIRING_SOON".equals(currentFilter));
        chipFilterExpired.setSelected("EXPIRED".equals(currentFilter));

        updateChipVisual(chipFilterAll, "ALL".equals(currentFilter));
        updateChipVisual(chipFilterActive, "ACTIVE".equals(currentFilter));
        updateChipVisual(chipFilterExpiring, "EXPIRING_SOON".equals(currentFilter));
        updateChipVisual(chipFilterExpired, "EXPIRED".equals(currentFilter));
    }

    private void updateChipVisual(Button button, boolean isSelected) {
        if (isSelected) {
            button.setBackgroundResource(R.drawable.chip_active);
            button.setTextColor(getResources().getColor(R.color.m3_on_primary));
        } else {
            button.setBackgroundResource(R.drawable.chip_inactive);
            button.setTextColor(getResources().getColor(R.color.m3_on_surface));
        }
    }

    private void renderVaultList() {
        llVaultItemsList.removeAllViews();
        String query = etSearchVault.getText().toString().trim().toLowerCase(Locale.US);

        int active = 0;
        int expiring = 0;
        int expired = 0;

        List<VaultItem> filteredList = new ArrayList<>();

        for (VaultItem item : vaultItemList) {
            String status = item.getStatusTag();
            if ("ACTIVE".equals(status)) active++;
            else if ("EXPIRING_SOON".equals(status)) expiring++;
            else if ("EXPIRED".equals(status)) expired++;

            boolean matchesFilter = "ALL".equals(currentFilter) || status.equals(currentFilter);
            boolean matchesSearch = query.isEmpty()
                    || item.name.toLowerCase(Locale.US).contains(query)
                    || item.brandModel.toLowerCase(Locale.US).contains(query)
                    || item.serialNotes.toLowerCase(Locale.US).contains(query)
                    || item.manualSummary.toLowerCase(Locale.US).contains(query);

            if (matchesFilter && matchesSearch) {
                filteredList.add(item);
            }
        }

        tvActiveCount.setText(String.valueOf(active));
        tvExpiringCount.setText(String.valueOf(expiring));
        tvExpiredCount.setText(String.valueOf(expired));
        tvVaultSummary.setText(vaultItemList.size() + " total items guarded offline");

        if (filteredList.isEmpty()) {
            tvEmptyVaultMessage.setVisibility(View.VISIBLE);
        } else {
            tvEmptyVaultMessage.setVisibility(View.GONE);
            for (VaultItem item : filteredList) {
                View itemView = createVaultItemCard(item);
                llVaultItemsList.addView(itemView);
            }
        }
    }

    private View createVaultItemCard(VaultItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_m3_high);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        card.setPadding(padding, padding, padding, padding);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, (int) (12 * getResources().getDisplayMetrics().density));
        card.setLayoutParams(params);

        // Header Row: Title & Status Badge
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(item.name);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(getResources().getColor(R.color.m3_on_surface));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        tvTitle.setLayoutParams(titleParams);
        headerRow.addView(tvTitle);

        TextView tvBadge = new TextView(this);
        int days = item.getDaysRemaining();
        String badgeText;
        int badgeBg;
        int badgeColor = Color.WHITE;

        if (days < 0) {
            badgeText = "EXPIRED (" + Math.abs(days) + "d ago)";
            badgeBg = 0xFFBA1A1A;
        } else if (days <= 30) {
            badgeText = days + " DAYS LEFT";
            badgeBg = 0xFFE65100;
        } else {
            badgeText = days + " DAYS ACTIVE";
            badgeBg = 0xFF2E7D32;
        }
        tvBadge.setText(badgeText);
        tvBadge.setTextSize(11);
        tvBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvBadge.setTextColor(badgeColor);
        tvBadge.setPadding((int) (8 * getResources().getDisplayMetrics().density),
                (int) (4 * getResources().getDisplayMetrics().density),
                (int) (8 * getResources().getDisplayMetrics().density),
                (int) (4 * getResources().getDisplayMetrics().density));

        GradientDrawable badgeDrawable = new GradientDrawable();
        badgeDrawable.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        badgeDrawable.setColor(badgeBg);
        tvBadge.setBackground(badgeDrawable);
        headerRow.addView(tvBadge);

        card.addView(headerRow);

        // Subtitle / Brand
        if (!item.brandModel.isEmpty()) {
            TextView tvSub = new TextView(this);
            tvSub.setText(item.brandModel);
            tvSub.setTextSize(14);
            tvSub.setTextColor(getResources().getColor(R.color.m3_on_surface_variant));
            card.addView(tvSub);
        }

        // Timeline Info
        TextView tvDates = new TextView(this);
        tvDates.setText("Purchased: " + item.purchaseDate + "  •  Expires: " + item.expiryDate + " (" + item.warrantyMonths + " Mo)");
        tvDates.setTextSize(13);
        tvDates.setTextColor(getResources().getColor(R.color.m3_on_surface_variant));
        tvDates.setPadding(0, (int) (6 * getResources().getDisplayMetrics().density), 0, 0);
        card.addView(tvDates);

        // Serial / Notes snippet
        if (!item.serialNotes.isEmpty()) {
            TextView tvSerial = new TextView(this);
            tvSerial.setText("Details: " + item.serialNotes);
            tvSerial.setTextSize(13);
            tvSerial.setTextColor(getResources().getColor(R.color.m3_on_surface));
            tvSerial.setPadding(0, (int) (4 * getResources().getDisplayMetrics().density), 0, 0);
            card.addView(tvSerial);
        }

        // Manual / Quick troubleshooting guide
        if (!item.manualSummary.isEmpty()) {
            TextView tvManual = new TextView(this);
            tvManual.setText("Manual/Guide: " + item.manualSummary);
            tvManual.setTextSize(13);
            tvManual.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
            tvManual.setTextColor(getResources().getColor(R.color.m3_primary));
            tvManual.setPadding(0, (int) (4 * getResources().getDisplayMetrics().density), 0, 0);
            card.addView(tvManual);
        }

        // Actions Row: Copy, Share, Delete
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, (int) (10 * getResources().getDisplayMetrics().density), 0, 0);
        actionRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button btnCopy = new Button(this);
        btnCopy.setText("Copy Specs");
        btnCopy.setTextSize(12);
        btnCopy.setBackgroundResource(R.drawable.btn_m3_outlined);
        btnCopy.setTextColor(getResources().getColor(R.color.m3_primary));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        btnParams.setMargins(0, 0, (int) (6 * getResources().getDisplayMetrics().density), 0);
        btnCopy.setLayoutParams(btnParams);
        btnCopy.setOnClickListener(v -> {
            triggerHaptic(v);
            copyItemToClipboard(item);
        });
        actionRow.addView(btnCopy);

        Button btnShare = new Button(this);
        btnShare.setText("Share");
        btnShare.setTextSize(12);
        btnShare.setBackgroundResource(R.drawable.btn_m3_tonal);
        btnShare.setTextColor(getResources().getColor(R.color.m3_on_surface));
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        shareParams.setMargins(0, 0, (int) (6 * getResources().getDisplayMetrics().density), 0);
        btnShare.setLayoutParams(shareParams);
        btnShare.setOnClickListener(v -> {
            triggerHaptic(v);
            shareSingleItem(item);
        });
        actionRow.addView(btnShare);

        Button btnDelete = new Button(this);
        btnDelete.setText("Delete");
        btnDelete.setTextSize(12);
        btnDelete.setBackgroundResource(R.drawable.btn_m3_outlined);
        btnDelete.setTextColor(0xFFBA1A1A);
        LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        btnDelete.setLayoutParams(delParams);
        btnDelete.setOnClickListener(v -> {
            triggerHaptic(v);
            deleteItem(item);
        });
        actionRow.addView(btnDelete);

        card.addView(actionRow);
        return card;
    }

    private void copyItemToClipboard(VaultItem item) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String details = "Item: " + item.name + "\n"
                + "Model: " + item.brandModel + "\n"
                + "Purchase Date: " + item.purchaseDate + "\n"
                + "Expiry: " + item.expiryDate + "\n"
                + "Serial/Notes: " + item.serialNotes + "\n"
                + "Manual: " + item.manualSummary;
        ClipData clip = ClipData.newPlainText("SnipShelf Warranty", details);
        if (cm != null) {
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "Warranty & Manual copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSingleItem(VaultItem item) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TITLE, "Warranty Vault: " + item.name);
        sendIntent.putExtra(Intent.EXTRA_TEXT,
                "SnipShelf Vault Record\n\n"
                        + "Product: " + item.name + " (" + item.brandModel + ")\n"
                        + "Purchased: " + item.purchaseDate + "\n"
                        + "Warranty End: " + item.expiryDate + " (" + item.getDaysRemaining() + " days remaining)\n"
                        + "Notes: " + item.serialNotes + "\n"
                        + "Quick Troubleshooting: " + item.manualSummary);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "Share Warranty Info");
        startActivity(shareIntent);
    }

    private void deleteItem(VaultItem item) {
        vaultItemList.remove(item);
        saveVaultItemsToDisk();
        renderVaultList();
        playBeep();
        Toast.makeText(this, "Deleted: " + item.name, Toast.LENGTH_SHORT).show();
    }

    private void exportVaultSummary() {
        if (vaultItemList.isEmpty()) {
            Toast.makeText(this, "Vault is empty! Add receipts first.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== SNIPSHELF OFFLINE WARRANTY & MANUAL VAULT EXPORT ===\n");
        sb.append("Total Items: ").append(vaultItemList.size()).append("\n");
        sb.append("Export Timestamp: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append("\n\n");

        for (int i = 0; i < vaultItemList.size(); i++) {
            VaultItem item = vaultItemList.get(i);
            sb.append("[").append(i + 1).append("] ").append(item.name).append("\n");
            sb.append("   Model: ").append(item.brandModel).append("\n");
            sb.append("   Purchased: ").append(item.purchaseDate).append(" | Expires: ").append(item.expiryDate).append("\n");
            sb.append("   Status: ").append(item.getStatusTag()).append(" (").append(item.getDaysRemaining()).append(" days left)\n");
            if (!item.serialNotes.isEmpty()) sb.append("   Serial/Notes: ").append(item.serialNotes).append("\n");
            if (!item.manualSummary.isEmpty()) sb.append("   Manual: ").append(item.manualSummary).append("\n");
            sb.append("\n");
        }

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TITLE, "SnipShelf Vault Full Backup");
        sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Export Vault Backup"));
    }

    private void confirmAndClearAll() {
        if (vaultItemList.isEmpty()) {
            Toast.makeText(this, "Vault is already empty.", Toast.LENGTH_SHORT).show();
            return;
        }
        vaultItemList.clear();
        saveVaultItemsToDisk();
        renderVaultList();
        Toast.makeText(this, "Vault cleared completely", Toast.LENGTH_SHORT).show();
    }

    private void loadSavedVaultItems() {
        vaultItemList.clear();
        String jsonStr = prefs.getString(KEY_ITEMS_JSON, null);
        if (jsonStr != null && !jsonStr.isEmpty()) {
            try {
                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    vaultItemList.add(VaultItem.fromJson(array.getJSONObject(i)));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Preload default demonstration items if first launch
        if (vaultItemList.isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();

            // Item 1: Active
            cal.setTime(new Date());
            cal.add(Calendar.MONTH, -3);
            String pDate1 = sdf.format(cal.getTime());
            String expDate1 = computeExpiryDate(pDate1, 24);

            vaultItemList.add(new VaultItem("seed_1", "MacBook Pro M3 Max", "Apple Inc. (A2992)",
                    pDate1, 24, expDate1, "S/N: C02G90123Q; AppleCare+ Active",
                    "DFU mode: Hold power for 10s while connected to USB-C port 1. Genius Bar: 1-800-MY-APPLE",
                    true, System.currentTimeMillis()));

            // Item 2: Expiring Soon
            cal.setTime(new Date());
            cal.add(Calendar.MONTH, -11);
            String pDate2 = sdf.format(cal.getTime());
            String expDate2 = computeExpiryDate(pDate2, 12);

            vaultItemList.add(new VaultItem("seed_2", "De'Longhi Espresso Machine", "Magnifica S (ECAM22110B)",
                    pDate2, 12, expDate2, "Purchased at Seattle Coffee Gear; S/N: DL-491204",
                    "Descaling routine: Press rinse for 5 seconds until red light flashes. Fill tank with 100ml eco-decalcifier.",
                    true, System.currentTimeMillis()));

            // Item 3: Expired
            cal.setTime(new Date());
            cal.add(Calendar.MONTH, -28);
            String pDate3 = sdf.format(cal.getTime());
            String expDate3 = computeExpiryDate(pDate3, 24);

            vaultItemList.add(new VaultItem("seed_3", "Sony Bravia 65 OLED TV", "XR-65A80K",
                    pDate3, 24, expDate3, "Extended 2yr Costco warranty; S/N: S01-8849120",
                    "Factory Reset: Unplug AC, press Power + Vol Down on rear panel while plugging in AC. Wait for green LED.",
                    false, System.currentTimeMillis()));

            saveVaultItemsToDisk();
        }
    }

    private void saveVaultItemsToDisk() {
        JSONArray array = new JSONArray();
        for (VaultItem item : vaultItemList) {
            array.put(item.toJson());
        }
        prefs.edit().putString(KEY_ITEMS_JSON, array.toString()).apply();
    }

    private void cycleThemeMode() {
        int current = prefs.getInt(KEY_THEME_MODE, 0); // 0=Auto, 1=Dark, 2=Light
        int next = (current + 1) % 3;
        prefs.edit().putInt(KEY_THEME_MODE, next).apply();
        applySavedThemeMode();
    }

    private void applySavedThemeMode() {
        int mode = prefs.getInt(KEY_THEME_MODE, 0);
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (mode == 1) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
                btnThemeToggle.setText("Theme: Dark");
            } else if (mode == 2) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
                btnThemeToggle.setText("Theme: Light");
            } else {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);
                btnThemeToggle.setText("Theme: Auto");
            }
        } else {
            if (mode == 1) btnThemeToggle.setText("Theme: Dark");
            else if (mode == 2) btnThemeToggle.setText("Theme: Light");
            else btnThemeToggle.setText("Theme: Auto");
        }
    }

    private void triggerHaptic(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private void playBeep() {
        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 80);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (getCurrentFocus() != null && getCurrentFocus() instanceof EditText) {
                    // Let normal text input proceed unless focus is in manual summary
                    if (getCurrentFocus().getId() == R.id.etManualSummary) {
                        saveNewVaultEntry();
                        return true;
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_NUMPAD_ADD) {
                int currentProg = sbWarrantyDuration.getProgress();
                if (currentProg < sbWarrantyDuration.getMax()) {
                    sbWarrantyDuration.setProgress(currentProg + 1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_MINUS || keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT) {
                int currentProg = sbWarrantyDuration.getProgress();
                if (currentProg > 1) {
                    sbWarrantyDuration.setProgress(currentProg - 1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                if (getCurrentFocus() == null || !(getCurrentFocus() instanceof EditText)) {
                    swNotificationReminder.toggle();
                    triggerHaptic(swNotificationReminder);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Seamlessly adapts freeform desktop resizes without resetting user inputs
    }

    @Override
    protected void onDestroy() {
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDestroy();
    }
}