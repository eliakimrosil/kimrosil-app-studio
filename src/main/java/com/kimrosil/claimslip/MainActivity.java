package com.kimrosil.claimslip;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String PREFS_NAME = "claimslip_prefs";
    public static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark

    private SharedPreferences mPrefs;
    private WarrantyDbHelper mDbHelper;

    // Filter & Search State
    private String mCurrentFilter = "ALL";
    private String mCurrentQuery = "";
    private int mThemeMode = 0;

    // Views
    private LinearLayout mRootLayout;
    private TextView mTvAppTitle;
    private TextView mTvAppSubtitle;
    private Button mBtnThemeToggle;
    private LinearLayout mCardHud;
    private TextView mTvHudActiveCount;
    private TextView mTvHudActiveValue;
    private TextView mTvHudExpiringCount;
    private TextView mTvHudExpiringStatus;

    // Filter Chips
    private Button mChipAll;
    private Button mChipActive;
    private Button mChipExpiring;
    private Button mChipExpired;

    // Search & Add
    private EditText mEtSearch;
    private Button mBtnAddWarranty;

    // List & Empty State
    private LinearLayout mLlWarrantiesList;
    private LinearLayout mLlEmptyState;

    // Bottom Export
    private Button mBtnExportJson;

    private final SimpleDateFormat mDateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, 0);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = Configuration.UI_MODE_NIGHT_NO | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        } else if (themeMode == 2) {
            config.uiMode = Configuration.UI_MODE_NIGHT_YES | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        }
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mThemeMode = mPrefs.getInt(KEY_THEME_MODE, 0);

        if (mThemeMode == 1) {
            setTheme(R.style.AppTheme_Light);
        } else if (mThemeMode == 2) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        applyStatusBarContrast();

        mDbHelper = new WarrantyDbHelper(this);
        mDbHelper.seedInitialSampleDataIfEmpty();

        bindViews();
        setupListeners();
        updateThemeButtonLabel();
        loadWarranties();
    }

    private void bindViews() {
        mRootLayout = findViewById(R.id.root_layout);
        mTvAppTitle = findViewById(R.id.tv_app_title);
        mTvAppSubtitle = findViewById(R.id.tv_app_subtitle);
        mBtnThemeToggle = findViewById(R.id.btn_theme_toggle);
        mCardHud = findViewById(R.id.card_hud);
        mTvHudActiveCount = findViewById(R.id.tv_hud_active_count);
        mTvHudActiveValue = findViewById(R.id.tv_hud_active_value);
        mTvHudExpiringCount = findViewById(R.id.tv_hud_expiring_count);
        mTvHudExpiringStatus = findViewById(R.id.tv_hud_expiring_status);

        mChipAll = findViewById(R.id.chip_filter_all);
        mChipActive = findViewById(R.id.chip_filter_active);
        mChipExpiring = findViewById(R.id.chip_filter_expiring);
        mChipExpired = findViewById(R.id.chip_filter_expired);

        mEtSearch = findViewById(R.id.et_search);
        mBtnAddWarranty = findViewById(R.id.btn_add_warranty);

        mLlWarrantiesList = findViewById(R.id.ll_warranties_list);
        mLlEmptyState = findViewById(R.id.ll_empty_state);

        mBtnExportJson = findViewById(R.id.btn_export_json);

        // Remove drop shadows for crisp VatCal PH hairline styling
        Button[] buttons = {
            mBtnThemeToggle, mChipAll, mChipActive, mChipExpiring,
            mChipExpired, mBtnAddWarranty, mBtnExportJson
        };
        for (Button btn : buttons) {
            if (btn != null) {
                btn.setElevation(0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    btn.setStateListAnimator(null);
                }
            }
        }
    }

    private void setupListeners() {
        mBtnThemeToggle.setOnClickListener(v -> cycleThemeMode());

        mBtnAddWarranty.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showAddWarrantyDialog();
        });

        mBtnExportJson.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportWarrantiesJson();
        });

        // Filter chips
        mChipAll.setOnClickListener(v -> setFilter("ALL"));
        mChipActive.setOnClickListener(v -> setFilter("ACTIVE"));
        mChipExpiring.setOnClickListener(v -> setFilter("EXPIRING"));
        mChipExpired.setOnClickListener(v -> setFilter("EXPIRED"));

        // Live search
        mEtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                mCurrentQuery = s.toString();
                loadWarranties();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setFilter(String filter) {
        mCurrentFilter = filter;
        updateFilterChipStyles();
        loadWarranties();
    }

    private void updateFilterChipStyles() {
        boolean isDark = isDarkThemeActive();
        int activeBgRes = R.drawable.chip_filter_active;
        int inactiveBgRes = R.drawable.chip_btn;

        int activeTextColor = 0xFFFFFFFF;
        int inactiveTextColor = isDark ? 0xFFF1F5F9 : 0xFF0F172A;

        mChipAll.setBackgroundResource("ALL".equals(mCurrentFilter) ? activeBgRes : inactiveBgRes);
        mChipAll.setTextColor("ALL".equals(mCurrentFilter) ? activeTextColor : inactiveTextColor);

        mChipActive.setBackgroundResource("ACTIVE".equals(mCurrentFilter) ? activeBgRes : inactiveBgRes);
        mChipActive.setTextColor("ACTIVE".equals(mCurrentFilter) ? activeTextColor : inactiveTextColor);

        mChipExpiring.setBackgroundResource("EXPIRING".equals(mCurrentFilter) ? activeBgRes : inactiveBgRes);
        mChipExpiring.setTextColor("EXPIRING".equals(mCurrentFilter) ? activeTextColor : inactiveTextColor);

        mChipExpired.setBackgroundResource("EXPIRED".equals(mCurrentFilter) ? activeBgRes : inactiveBgRes);
        mChipExpired.setTextColor("EXPIRED".equals(mCurrentFilter) ? activeTextColor : inactiveTextColor);
    }

    private void loadWarranties() {
        mLlWarrantiesList.removeAllViews();
        List<WarrantyItem> items = mDbHelper.getWarranties(mCurrentFilter, mCurrentQuery);

        // Update HUD Metrics from total database items
        List<WarrantyItem> allItems = mDbHelper.getWarranties("ALL", "");
        int activeCount = 0;
        double activeValue = 0;
        int expiringCount = 0;

        for (WarrantyItem item : allItems) {
            if (item.getStatus() == WarrantyItem.Status.ACTIVE || item.getStatus() == WarrantyItem.Status.EXPIRING_SOON) {
                activeCount++;
                activeValue += item.getPrice();
            }
            if (item.getStatus() == WarrantyItem.Status.EXPIRING_SOON) {
                expiringCount++;
            }
        }

        mTvHudActiveCount.setText(activeCount + " " + (activeCount == 1 ? "Item" : "Items"));
        mTvHudActiveValue.setText(String.format(Locale.US, "₱%,.0f Protected", activeValue));
        mTvHudExpiringCount.setText(expiringCount + " " + (expiringCount == 1 ? "Item" : "Items"));
        if (expiringCount > 0) {
            mTvHudExpiringStatus.setText("Claims due in ≤ 30 days");
            mTvHudExpiringStatus.setTextColor(isDarkThemeActive() ? 0xFFF59E0B : 0xFFD97706);
        } else {
            mTvHudExpiringStatus.setText("No urgent claims");
            mTvHudExpiringStatus.setTextColor(isDarkThemeActive() ? 0xFF94A3B8 : 0xFF475569);
        }

        if (items.isEmpty()) {
            mLlEmptyState.setVisibility(View.VISIBLE);
        } else {
            mLlEmptyState.setVisibility(View.GONE);
            for (WarrantyItem item : items) {
                View card = createWarrantyCard(item);
                mLlWarrantiesList.addView(card);
            }
        }
    }

    private View createWarrantyCard(final WarrantyItem item) {
        boolean isDark = isDarkThemeActive();
        int textHigh = isDark ? 0xFFF1F5F9 : 0xFF0F172A;
        int textMid = isDark ? 0xFF94A3B8 : 0xFF475569;
        int textMuted = isDark ? 0xFF64748B : 0xFF94A3B8;
        int accentEmerald = isDark ? 0xFF10B981 : 0xFF059669;
        int accentAmber = isDark ? 0xFFF59E0B : 0xFFD97706;
        int colorError = isDark ? 0xFFF87171 : 0xFFDC2626;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_telemetry);
        card.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);

        // 1. Top Row: Category + Retailer and Expiry Badge
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvMeta = new TextView(this);
        tvMeta.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        String metaText = item.getCategory().toUpperCase(Locale.US);
        if (!item.getRetailer().isEmpty()) {
            metaText += " • " + item.getRetailer();
        }
        tvMeta.setText(metaText);
        tvMeta.setTextColor(textMuted);
        tvMeta.setTextSize(10f);
        tvMeta.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(tvMeta);

        // Badge
        TextView tvBadge = new TextView(this);
        tvBadge.setText(item.getStatusBadgeText());
        tvBadge.setTextSize(11f);
        tvBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvBadge.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));

        if (item.getStatus() == WarrantyItem.Status.ACTIVE) {
            tvBadge.setBackgroundResource(R.drawable.badge_active);
            tvBadge.setTextColor(accentEmerald);
        } else if (item.getStatus() == WarrantyItem.Status.EXPIRING_SOON) {
            tvBadge.setBackgroundResource(R.drawable.badge_expiring);
            tvBadge.setTextColor(accentAmber);
        } else {
            tvBadge.setBackgroundResource(R.drawable.badge_expired);
            tvBadge.setTextColor(colorError);
        }
        topRow.addView(tvBadge);
        card.addView(topRow);

        // 2. Item Name Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(item.getItemName());
        tvTitle.setTextColor(textHigh);
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, dpToPx(6), 0, dpToPx(6));
        card.addView(tvTitle);

        // 3. 2-Column Specs Telemetry (Purchase Date & Expiry Date)
        LinearLayout specsRow = new LinearLayout(this);
        specsRow.setOrientation(LinearLayout.HORIZONTAL);
        specsRow.setPadding(0, dpToPx(4), 0, dpToPx(8));

        // Left Col: Purchased & Price
        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvPurchasedLbl = new TextView(this);
        tvPurchasedLbl.setText("PURCHASE DATE");
        tvPurchasedLbl.setTextColor(textMuted);
        tvPurchasedLbl.setTextSize(9f);
        tvPurchasedLbl.setTypeface(Typeface.DEFAULT_BOLD);
        leftCol.addView(tvPurchasedLbl);

        TextView tvPurchasedVal = new TextView(this);
        String priceFormatted = item.getPrice() > 0 ? String.format(Locale.US, " (₱%,.0f)", item.getPrice()) : "";
        tvPurchasedVal.setText(item.getPurchaseDate() + priceFormatted);
        tvPurchasedVal.setTextColor(textMid);
        tvPurchasedVal.setTextSize(12f);
        leftCol.addView(tvPurchasedVal);
        specsRow.addView(leftCol);

        // Divider
        View specDiv = new View(this);
        specDiv.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(1), dpToPx(28)));
        specDiv.setBackgroundResource(R.drawable.divider_hairline);
        specDiv.setPadding(dpToPx(4), 0, dpToPx(4), 0);
        specsRow.addView(specDiv);

        // Right Col: Warranty Term & Expiry
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setPadding(dpToPx(10), 0, 0, 0);
        rightCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvExpiryLbl = new TextView(this);
        tvExpiryLbl.setText("WARRANTY EXPIRATION");
        tvExpiryLbl.setTextColor(textMuted);
        tvExpiryLbl.setTextSize(9f);
        tvExpiryLbl.setTypeface(Typeface.DEFAULT_BOLD);
        rightCol.addView(tvExpiryLbl);

        TextView tvExpiryVal = new TextView(this);
        tvExpiryVal.setText(item.getExpiryDate() + " (" + item.getWarrantyMonths() + "m)");
        tvExpiryVal.setTextColor(item.getStatus() == WarrantyItem.Status.EXPIRING_SOON ? accentAmber : textHigh);
        tvExpiryVal.setTextSize(12f);
        tvExpiryVal.setTypeface(Typeface.DEFAULT_BOLD);
        rightCol.addView(tvExpiryVal);
        specsRow.addView(rightCol);

        card.addView(specsRow);

        // 4. Model & Serial Number Bar (with click to copy)
        if (!item.getModelNo().isEmpty() || !item.getSerialNo().isEmpty()) {
            LinearLayout snBox = new LinearLayout(this);
            snBox.setOrientation(LinearLayout.HORIZONTAL);
            snBox.setGravity(Gravity.CENTER_VERTICAL);
            snBox.setBackgroundResource(R.drawable.edittext_bg);
            snBox.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
            snBox.setMinimumHeight(dpToPx(50));

            TextView tvSn = new TextView(this);
            tvSn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            String snText = "";
            if (!item.getModelNo().isEmpty()) snText += "Model: " + item.getModelNo() + "  ";
            if (!item.getSerialNo().isEmpty()) snText += "S/N: " + item.getSerialNo();
            tvSn.setText(snText);
            tvSn.setTextColor(textHigh);
            tvSn.setTextSize(11f);
            snBox.addView(tvSn);

            TextView tvCopyHint = new TextView(this);
            tvCopyHint.setText("Copy S/N");
            tvCopyHint.setTextColor(accentEmerald);
            tvCopyHint.setTextSize(11f);
            tvCopyHint.setTypeface(Typeface.DEFAULT_BOLD);
            snBox.addView(tvCopyHint);

            snBox.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Serial Number", item.getSerialNo().isEmpty() ? item.getModelNo() : item.getSerialNo());
                cm.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "Copied Serial Number to Clipboard!", Toast.LENGTH_SHORT).show();
            });

            card.addView(snBox);
        }

        // 5. Card Action Buttons (Receipt Slip & Delete)
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setPadding(0, dpToPx(10), 0, 0);

        Button btnReceipt = new Button(this);
        btnReceipt.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(50), 1.0f));
        btnReceipt.setMinHeight(dpToPx(50));
        btnReceipt.setElevation(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) btnReceipt.setStateListAnimator(null);
        btnReceipt.setBackgroundResource(R.drawable.btn_action);
        btnReceipt.setText("📄 View Receipt Slip");
        btnReceipt.setTextColor(accentEmerald);
        btnReceipt.setTextSize(12f);
        btnReceipt.setTypeface(Typeface.DEFAULT_BOLD);
        btnReceipt.setAllCaps(false);
        btnReceipt.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showReceiptSlipDialog(item);
        });
        actionsRow.addView(btnReceipt);

        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(8), 1));
        actionsRow.addView(gap);

        Button btnDel = new Button(this);
        btnDel.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(50), dpToPx(50)));
        btnDel.setMinHeight(dpToPx(50));
        btnDel.setMinWidth(dpToPx(50));
        btnDel.setElevation(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) btnDel.setStateListAnimator(null);
        btnDel.setBackgroundResource(R.drawable.btn_tonal_action);
        btnDel.setText("🗑");
        btnDel.setTextColor(colorError);
        btnDel.setTextSize(14f);
        btnDel.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            confirmDeleteWarranty(item);
        });
        actionsRow.addView(btnDel);

        card.addView(actionsRow);

        return card;
    }

    private void showAddWarrantyDialog() {
        boolean isDark = isDarkThemeActive();
        int textHigh = isDark ? 0xFFF1F5F9 : 0xFF0F172A;
        int textMid = isDark ? 0xFF94A3B8 : 0xFF475569;
        int accentEmerald = isDark ? 0xFF10B981 : 0xFF059669;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Warranty Record");

        ScrollView sv = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        // Appliance Name
        EditText etName = new EditText(this);
        etName.setHint("Appliance / Gadget Name");
        etName.setTextColor(textHigh);
        etName.setBackgroundResource(R.drawable.edittext_bg);
        etName.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        layout.addView(etName);

        // Retailer
        EditText etRetailer = new EditText(this);
        etRetailer.setHint("Store / Retailer (e.g. Best Buy)");
        etRetailer.setTextColor(textHigh);
        etRetailer.setBackgroundResource(R.drawable.edittext_bg);
        etRetailer.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p1.setMargins(0, dpToPx(8), 0, 0);
        etRetailer.setLayoutParams(p1);
        layout.addView(etRetailer);

        // Price
        EditText etPrice = new EditText(this);
        etPrice.setHint("Price Amount (e.g. 15999.00)");
        etPrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etPrice.setTextColor(textHigh);
        etPrice.setBackgroundResource(R.drawable.edittext_bg);
        etPrice.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        etPrice.setLayoutParams(p1);
        layout.addView(etPrice);

        // Purchase Date Button
        final Calendar chosenDate = Calendar.getInstance();
        final Button btnDate = new Button(this);
        btnDate.setLayoutParams(p1);
        btnDate.setMinHeight(dpToPx(50));
        btnDate.setElevation(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) btnDate.setStateListAnimator(null);
        btnDate.setBackgroundResource(R.drawable.btn_tonal_action);
        btnDate.setText("Purchase Date: " + mDateFmt.format(chosenDate.getTime()));
        btnDate.setTextColor(accentEmerald);
        btnDate.setTextSize(12f);
        btnDate.setTypeface(Typeface.DEFAULT_BOLD);
        btnDate.setAllCaps(false);
        btnDate.setOnClickListener(v -> {
            new DatePickerDialog(MainActivity.this, (view, year, month, dayOfMonth) -> {
                chosenDate.set(year, month, dayOfMonth);
                btnDate.setText("Purchase Date: " + mDateFmt.format(chosenDate.getTime()));
            }, chosenDate.get(Calendar.YEAR), chosenDate.get(Calendar.MONTH), chosenDate.get(Calendar.DAY_OF_MONTH)).show();
        });
        layout.addView(btnDate);

        // Warranty Period Selection
        TextView tvPeriodLbl = new TextView(this);
        tvPeriodLbl.setText("WARRANTY COVERAGE PERIOD");
        tvPeriodLbl.setTextColor(textMid);
        tvPeriodLbl.setTextSize(10f);
        tvPeriodLbl.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams pLbl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pLbl.setMargins(0, dpToPx(12), 0, dpToPx(4));
        tvPeriodLbl.setLayoutParams(pLbl);
        layout.addView(tvPeriodLbl);

        final int[] selectedMonths = {12}; // Default 1 year
        LinearLayout periodRow = new LinearLayout(this);
        periodRow.setOrientation(LinearLayout.HORIZONTAL);

        int[] periodOptions = {6, 12, 24, 36, 60};
        String[] periodLabels = {"6m", "1y", "2y", "3y", "5y"};
        final Button[] periodButtons = new Button[periodOptions.length];

        for (int i = 0; i < periodOptions.length; i++) {
            final int months = periodOptions[i];
            final Button b = new Button(this);
            periodButtons[i] = b;
            b.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(50), 1.0f));
            b.setMinHeight(dpToPx(50));
            b.setElevation(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) b.setStateListAnimator(null);
            b.setText(periodLabels[i]);
            b.setTextSize(11f);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setBackgroundResource(months == selectedMonths[0] ? R.drawable.chip_filter_active : R.drawable.chip_btn);
            b.setTextColor(months == selectedMonths[0] ? 0xFFFFFFFF : textHigh);
            b.setOnClickListener(v -> {
                selectedMonths[0] = months;
                for (int j = 0; j < periodOptions.length; j++) {
                    boolean sel = periodOptions[j] == selectedMonths[0];
                    periodButtons[j].setBackgroundResource(sel ? R.drawable.chip_filter_active : R.drawable.chip_btn);
                    periodButtons[j].setTextColor(sel ? 0xFFFFFFFF : (isDarkThemeActive() ? 0xFFF1F5F9 : 0xFF0F172A));
                }
            });
            periodRow.addView(b);
        }
        layout.addView(periodRow);

        // Model & Serial
        EditText etModel = new EditText(this);
        etModel.setHint("Model Number (Optional)");
        etModel.setTextColor(textHigh);
        etModel.setBackgroundResource(R.drawable.edittext_bg);
        etModel.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        etModel.setLayoutParams(p1);
        layout.addView(etModel);

        EditText etSerial = new EditText(this);
        etSerial.setHint("Serial Number S/N (Optional)");
        etSerial.setTextColor(textHigh);
        etSerial.setBackgroundResource(R.drawable.edittext_bg);
        etSerial.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        etSerial.setLayoutParams(p1);
        layout.addView(etSerial);

        // Notes
        EditText etNotes = new EditText(this);
        etNotes.setHint("Support Phone / Claim Notes (Optional)");
        etNotes.setTextColor(textHigh);
        etNotes.setBackgroundResource(R.drawable.edittext_bg);
        etNotes.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        etNotes.setLayoutParams(p1);
        layout.addView(etNotes);

        sv.addView(layout);
        builder.setView(sv);

        builder.setPositiveButton("Save Record", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(MainActivity.this, "Item name cannot be blank", Toast.LENGTH_SHORT).show();
                return;
            }

            double price = 0.0;
            try {
                String pStr = etPrice.getText().toString().trim();
                if (!pStr.isEmpty()) price = Double.parseDouble(pStr);
            } catch (Exception ignored) {}

            String pDateStr = mDateFmt.format(chosenDate.getTime());
            String expDateStr = WarrantyItem.calculateExpiry(pDateStr, selectedMonths[0]);

            WarrantyItem item = new WarrantyItem();
            item.setItemName(name);
            item.setRetailer(etRetailer.getText().toString().trim());
            item.setPurchaseDate(pDateStr);
            item.setWarrantyMonths(selectedMonths[0]);
            item.setExpiryDate(expDateStr);
            item.setPrice(price);
            item.setCurrency("₱");
            item.setModelNo(etModel.getText().toString().trim());
            item.setSerialNo(etSerial.getText().toString().trim());
            item.setNotes(etNotes.getText().toString().trim());
            item.setThermalSlip(true);

            mDbHelper.insertWarranty(item);
            Toast.makeText(MainActivity.this, "Warranty saved for " + name, Toast.LENGTH_SHORT).show();
            loadWarranties();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showReceiptSlipDialog(final WarrantyItem item) {
        boolean isDark = isDarkThemeActive();
        int textHigh = isDark ? 0xFFF1F5F9 : 0xFF0F172A;
        int textMid = isDark ? 0xFF94A3B8 : 0xFF475569;
        int accentEmerald = isDark ? 0xFF10B981 : 0xFF059669;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Proof of Purchase Slip");

        ScrollView sv = new ScrollView(this);
        LinearLayout slipCard = new LinearLayout(this);
        slipCard.setOrientation(LinearLayout.VERTICAL);
        slipCard.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        slipCard.setBackgroundResource(R.drawable.card_telemetry);

        // Store Header
        TextView tvHeader = new TextView(this);
        tvHeader.setText(item.getRetailer().isEmpty() ? "OFFICIAL PURCHASE VOUCHER" : item.getRetailer().toUpperCase(Locale.US));
        tvHeader.setTextColor(accentEmerald);
        tvHeader.setTextSize(14f);
        tvHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvHeader.setGravity(Gravity.CENTER);
        slipCard.addView(tvHeader);

        TextView tvReceiptSub = new TextView(this);
        tvReceiptSub.setText("DIGITAL WARRANTY PROOF • " + item.getPurchaseDate());
        tvReceiptSub.setTextColor(textMid);
        tvReceiptSub.setTextSize(10f);
        tvReceiptSub.setTypeface(Typeface.MONOSPACE);
        tvReceiptSub.setGravity(Gravity.CENTER);
        tvReceiptSub.setPadding(0, dpToPx(2), 0, dpToPx(8));
        slipCard.addView(tvReceiptSub);

        // Receipt Dotted Hairline
        View div = new View(this);
        div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        div.setBackgroundResource(R.drawable.divider_hairline);
        slipCard.addView(div);

        // Receipt Content Block
        TextView tvBody = new TextView(this);
        tvBody.setTypeface(Typeface.MONOSPACE);
        tvBody.setTextSize(11f);
        tvBody.setTextColor(textHigh);
        tvBody.setPadding(0, dpToPx(10), 0, dpToPx(10));

        StringBuilder sb = new StringBuilder();
        sb.append("ITEM:    ").append(item.getItemName()).append("\n");
        if (!item.getModelNo().isEmpty()) sb.append("MODEL:   ").append(item.getModelNo()).append("\n");
        if (!item.getSerialNo().isEmpty()) sb.append("S/N:     ").append(item.getSerialNo()).append("\n");
        if (item.getPrice() > 0) sb.append("PRICE:   ").append(String.format(Locale.US, "₱%,.2f\n", item.getPrice()));
        sb.append("PERIOD:  ").append(item.getWarrantyMonths()).append(" Months\n");
        sb.append("EXPIRES: ").append(item.getExpiryDate()).append("\n");
        sb.append("STATUS:  ").append(item.getStatusBadgeText()).append("\n");
        if (!item.getNotes().isEmpty()) sb.append("\nNOTES:\n").append(item.getNotes()).append("\n");

        tvBody.setText(sb.toString());
        slipCard.addView(tvBody);

        // Simulated Thermal Barcode
        TextView tvBarcode = new TextView(this);
        tvBarcode.setText("||| | |||| | |||||| | || |||| | ||||| | |||");
        tvBarcode.setTextColor(textHigh);
        tvBarcode.setTextSize(16f);
        tvBarcode.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvBarcode.setGravity(Gravity.CENTER);
        tvBarcode.setPadding(0, dpToPx(6), 0, dpToPx(6));
        slipCard.addView(tvBarcode);

        TextView tvSlipNotice = new TextView(this);
        tvSlipNotice.setText("⚡ High-Contrast Thermal Ledger: Proof archived locally against ink degradation.");
        tvSlipNotice.setTextColor(textMid);
        tvSlipNotice.setTextSize(10f);
        tvSlipNotice.setGravity(Gravity.CENTER);
        slipCard.addView(tvSlipNotice);

        sv.addView(slipCard);
        builder.setView(sv);

        builder.setPositiveButton("Copy Record", (dialog, which) -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Warranty Slip", sb.toString());
            cm.setPrimaryClip(clip);
            Toast.makeText(MainActivity.this, "Copied warranty proof to clipboard!", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void confirmDeleteWarranty(final WarrantyItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Warranty Record?")
                .setMessage("Are you sure you want to remove \"" + item.getItemName() + "\"? This action is irreversible.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    mDbHelper.deleteWarranty(item.getId());
                    Toast.makeText(MainActivity.this, "Record deleted", Toast.LENGTH_SHORT).show();
                    loadWarranties();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportWarrantiesJson() {
        try {
            List<WarrantyItem> all = mDbHelper.getWarranties("ALL", "");
            JSONArray array = new JSONArray();
            for (WarrantyItem item : all) {
                JSONObject obj = new JSONObject();
                obj.put("item_name", item.getItemName());
                obj.put("category", item.getCategory());
                obj.put("retailer", item.getRetailer());
                obj.put("purchase_date", item.getPurchaseDate());
                obj.put("warranty_months", item.getWarrantyMonths());
                obj.put("expiry_date", item.getExpiryDate());
                obj.put("price", item.getPrice());
                obj.put("model_no", item.getModelNo());
                obj.put("serial_no", item.getSerialNo());
                obj.put("notes", item.getNotes());
                array.put(obj);
            }

            String jsonOutput = array.toString(2);
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("ClaimSlip Export", jsonOutput);
            cm.setPrimaryClip(clip);

            Toast.makeText(this, "Exported " + all.size() + " warranty records to Clipboard (JSON)!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // Theme Management
    // =========================================================================

    private void cycleThemeMode() {
        mBtnThemeToggle.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        mThemeMode = (mThemeMode + 1) % 3;
        mPrefs.edit().putInt(KEY_THEME_MODE, mThemeMode).apply();
        recreate();
    }

    private boolean isDarkThemeActive() {
        if (mThemeMode == 2) return true;
        if (mThemeMode == 1) return false;
        int nightFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void updateThemeButtonLabel() {
        boolean isDark = isDarkThemeActive();
        int accentEmerald = isDark ? 0xFF10B981 : 0xFF059669;

        if (mThemeMode == 1) {
            mBtnThemeToggle.setText("☀️ Light");
        } else if (mThemeMode == 2) {
            mBtnThemeToggle.setText("🌙 Dark");
        } else {
            mBtnThemeToggle.setText("🌓 Auto");
        }
        mBtnThemeToggle.setTextColor(accentEmerald);
    }

    private void applyStatusBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (isDarkThemeActive()) {
                    controller.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                } else {
                    controller.setSystemBarsAppearance(
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    );
                }
            }
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
