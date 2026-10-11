package com.phvat.calc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    // ─── Header ───────────────────────────────────────────────────────────────
    private LinearLayout rootLayout;
    private TextView tvAppTitle;
    private TextView tvAppSubtitle;
    private Button btnTheme;

    // ─── Amount Card ──────────────────────────────────────────────────────────
    private LinearLayout cardAmount;
    private TextView tvAmountLabel;
    private LinearLayout llInputBox;
    private TextView tvPesoSymbol;
    private EditText etAmount;


    // ─── Mode Card ────────────────────────────────────────────────────────────
    private LinearLayout cardMode;
    private TextView tvModeLabel;
    private RadioGroup rgVatMode;
    private RadioButton rbInclusive, rbExclusive;
    private View dividerMode;
    private Switch swSeniorPwd;

    // VAT rate chips
    private Button chipVat12, chipVat0;
    private LinearLayout llVatRateRow;

    // ─── WHT Card ─────────────────────────────────────────────────────────────
    private LinearLayout cardWht;
    private TextView tvWhtLabel;
    private Switch swWht;
    private View dividerWht;
    private LinearLayout llWhtRateRow;
    private Button chipWht1, chipWht2, chipWht5, chipWhtNone;
    private LinearLayout llWhtResultRow;
    private TextView tvWhtAmountLabel;
    private TextView tvWhtAmountVal;
    private TextView tvNetAfterWhtLabel;
    private TextView tvNetAfterWhtVal;

    // ─── Breakdown Card ───────────────────────────────────────────────────────
    private LinearLayout cardBreakdown;
    private TextView tvBreakdownLabel;
    private TextView tvTotalLabel, tvTotalVal;
    private View dividerBreakdown;
    private TextView tvVatableLabel, tvVatableVal;
    private TextView tvVatLabel, tvVatVal;
    private LinearLayout llDiscountRow;
    private TextView tvDiscountLabel, tvDiscountVal;
    private TextView tvFormulaNote;

    // ─── History Card ─────────────────────────────────────────────────────────
    private LinearLayout cardHistory;
    private TextView tvHistoryLabel;
    private LinearLayout llHistoryList;
    private Button btnClearHistory;

    // ─── Bottom Actions ───────────────────────────────────────────────────────
    private Button btnClear, btnCopy;

    // ─── State ────────────────────────────────────────────────────────────────
    private boolean isDarkTheme = true;
    private double customVatRate = 0.12;   // 12% standard
    private double whtRate = 0.0;          // 0 = no WHT selected
    private SharedPreferences prefs;
    private Vibrator vibrator;

    private static final int MAX_HISTORY = 10;
    private static final String PREF_HISTORY = "calc_history";

    private final DecimalFormat currencyFmt = new DecimalFormat("₱#,##0.00");
    private final DecimalFormat plainFmt    = new DecimalFormat("#.##");
    private final DecimalFormat pctFmt      = new DecimalFormat("0.##'%'");
    private final SimpleDateFormat timeFmt  = new SimpleDateFormat("HH:mm", Locale.getDefault());

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs    = getSharedPreferences("ph_vat_prefs", MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean("dark_theme", true);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initViews();
        setupListeners();
        applyTheme(isDarkTheme);
        refreshHistoryCard();
        calculate();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        String savedAmount = etAmount != null ? etAmount.getText().toString() : "";
        boolean isInc  = rbInclusive == null || rbInclusive.isChecked();
        boolean isPwd  = swSeniorPwd != null && swSeniorPwd.isChecked();
        boolean isWht  = swWht       != null && swWht.isChecked();
        double  vatR   = customVatRate;
        double  whtR   = whtRate;

        setContentView(R.layout.activity_main);
        initViews();
        setupListeners();
        applyTheme(isDarkTheme);

        customVatRate = vatR;
        whtRate       = whtR;

        if (etAmount   != null) etAmount.setText(savedAmount);
        if (rbInclusive != null) rbInclusive.setChecked(isInc);
        if (rbExclusive != null) rbExclusive.setChecked(!isInc);
        if (swSeniorPwd != null) swSeniorPwd.setChecked(isPwd);
        if (swWht       != null) swWht.setChecked(isWht);

        refreshVatRateChips();
        refreshWhtRateChips();
        updateWhtVisibility();
        refreshHistoryCard();
        calculate();
    }

    // ─── View Binding ─────────────────────────────────────────────────────────
    private void initViews() {
        rootLayout   = findViewById(R.id.root_layout);
        tvAppTitle   = findViewById(R.id.tv_app_title);
        tvAppSubtitle = findViewById(R.id.tv_app_subtitle);
        btnTheme     = findViewById(R.id.btn_theme);

        cardAmount   = findViewById(R.id.card_amount);
        tvAmountLabel = findViewById(R.id.tv_amount_label);
        llInputBox   = findViewById(R.id.ll_input_box);
        tvPesoSymbol = findViewById(R.id.tv_peso_symbol);
        etAmount     = findViewById(R.id.et_amount);


        cardMode    = findViewById(R.id.card_mode);
        tvModeLabel = findViewById(R.id.tv_mode_label);
        rgVatMode   = findViewById(R.id.rg_vat_mode);
        rbInclusive = findViewById(R.id.rb_inclusive);
        rbExclusive = findViewById(R.id.rb_exclusive);
        dividerMode = findViewById(R.id.divider_mode);
        swSeniorPwd = findViewById(R.id.sw_senior_pwd);
        llVatRateRow = findViewById(R.id.ll_vat_rate_row);
        chipVat12   = findViewById(R.id.chip_vat_12);
        chipVat0    = findViewById(R.id.chip_vat_0);

        cardWht         = findViewById(R.id.card_wht);
        tvWhtLabel      = findViewById(R.id.tv_wht_label);
        swWht           = findViewById(R.id.sw_wht);
        dividerWht      = findViewById(R.id.divider_wht);
        llWhtRateRow    = findViewById(R.id.ll_wht_rate_row);
        chipWht1        = findViewById(R.id.chip_wht_1);
        chipWht2        = findViewById(R.id.chip_wht_2);
        chipWht5        = findViewById(R.id.chip_wht_5);
        chipWhtNone     = findViewById(R.id.chip_wht_none);
        llWhtResultRow  = findViewById(R.id.ll_wht_result_row);
        tvWhtAmountLabel = findViewById(R.id.tv_wht_amount_label);
        tvWhtAmountVal  = findViewById(R.id.tv_wht_amount_val);
        tvNetAfterWhtLabel = findViewById(R.id.tv_net_after_wht_label);
        tvNetAfterWhtVal   = findViewById(R.id.tv_net_after_wht_val);

        cardBreakdown   = findViewById(R.id.card_breakdown);
        tvBreakdownLabel = findViewById(R.id.tv_breakdown_label);
        tvTotalLabel    = findViewById(R.id.tv_total_label);
        tvTotalVal      = findViewById(R.id.tv_total_val);
        dividerBreakdown = findViewById(R.id.divider_breakdown);
        tvVatableLabel  = findViewById(R.id.tv_vatable_label);
        tvVatableVal    = findViewById(R.id.tv_vatable_val);
        tvVatLabel      = findViewById(R.id.tv_vat_label);
        tvVatVal        = findViewById(R.id.tv_vat_val);
        llDiscountRow   = findViewById(R.id.ll_discount_row);
        tvDiscountLabel = findViewById(R.id.tv_discount_label);
        tvDiscountVal   = findViewById(R.id.tv_discount_val);
        tvFormulaNote   = findViewById(R.id.tv_formula_note);

        cardHistory     = findViewById(R.id.card_history);
        tvHistoryLabel  = findViewById(R.id.tv_history_label);
        llHistoryList   = findViewById(R.id.ll_history_list);
        btnClearHistory = findViewById(R.id.btn_clear_history);

        btnClear = findViewById(R.id.btn_clear);
        btnCopy  = findViewById(R.id.btn_copy);
    }

    // ─── Listeners ────────────────────────────────────────────────────────────
    private void setupListeners() {
        btnTheme.setOnClickListener(v -> {
            isDarkTheme = !isDarkTheme;
            prefs.edit().putBoolean("dark_theme", isDarkTheme).apply();
            applyTheme(isDarkTheme);
            calculate();
        });

        etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { calculate(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        rgVatMode.setOnCheckedChangeListener((g, id) -> calculate());

        swSeniorPwd.setOnCheckedChangeListener((btn, checked) -> {
            // Senior/PWD implicitly exempts VAT — show it but keep user's selected rate visible
            calculate();
        });

        // VAT Rate chips
        chipVat12.setOnClickListener(v -> {
            customVatRate = 0.12;
            refreshVatRateChips();
            calculate();
            haptic(20);
        });
        chipVat0.setOnClickListener(v -> {
            customVatRate = 0.0;
            refreshVatRateChips();
            calculate();
            haptic(20);
        });

        // WHT toggle
        swWht.setOnCheckedChangeListener((btn, checked) -> {
            updateWhtVisibility();
            calculate();
        });

        // WHT rate chips
        chipWht1.setOnClickListener(v -> { whtRate = 0.01; refreshWhtRateChips(); calculate(); haptic(20); });
        chipWht2.setOnClickListener(v -> { whtRate = 0.02; refreshWhtRateChips(); calculate(); haptic(20); });
        chipWht5.setOnClickListener(v -> { whtRate = 0.05; refreshWhtRateChips(); calculate(); haptic(20); });
        chipWhtNone.setOnClickListener(v -> { whtRate = 0.0; refreshWhtRateChips(); calculate(); haptic(20); });


        btnClear.setOnClickListener(v -> {
            etAmount.setText("");
            calculate();
        });

        btnCopy.setOnClickListener(v -> copySummary());

        btnClearHistory.setOnClickListener(v -> {
            prefs.edit().remove(PREF_HISTORY).apply();
            refreshHistoryCard();
            haptic(30);
        });
    }


    // ─── Theme ────────────────────────────────────────────────────────────────
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void applyTheme(boolean isDark) {
        int bgMain     = isDark ? 0xFF0F1117 : 0xFFF8F9FA;
        int bgCard     = isDark ? 0xFF1A1D27 : 0xFFFFFFFF;
        int bgInner    = isDark ? 0xFF222634 : 0xFFF3F4F6;
        int strokeColor = isDark ? 0xFF2E3446 : 0xFFE5E7EB;

        int textHigh   = isDark ? 0xFFF0F3F8 : 0xFF111827;
        int textMid    = isDark ? 0xFF9CA3AF : 0xFF4B5563;
        int textMuted  = isDark ? 0xFF6B7280 : 0xFF9CA3AF;

        int accentGold    = isDark ? 0xFFFFD54F : 0xFFB45309;
        int accentCyan    = isDark ? 0xFF00E5FF : 0xFF0284C7;
        int accentEmerald = isDark ? 0xFF00E676 : 0xFF15803D;
        int accentOrange  = isDark ? 0xFFFF9800 : 0xFFD97706;

        // Status / nav bars
        getWindow().setStatusBarColor(bgMain);
        getWindow().setNavigationBarColor(bgMain);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (!isDark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }

        rootLayout.setBackgroundColor(bgMain);

        // Header
        tvAppTitle.setTextColor(accentGold);
        tvAppSubtitle.setTextColor(textMid);
        btnTheme.setText(isDark ? "☀ Light" : "🌙 Dark");
        btnTheme.setTextColor(isDark ? 0xFFFFD54F : 0xFF111827);

        // Cards helper
        applyCardBg(cardAmount,    bgCard, strokeColor);
        applyCardBg(cardMode,      bgCard, strokeColor);
        applyCardBg(cardWht,       bgCard, strokeColor);
        applyCardBg(cardBreakdown, bgCard, strokeColor);
        applyCardBg(cardHistory,   bgCard, strokeColor);

        // Input box
        GradientDrawable inputDraw = new GradientDrawable();
        inputDraw.setColor(bgInner);
        inputDraw.setStroke(dpToPx(1), isDark ? strokeColor : 0xFFCBD5E1);
        inputDraw.setCornerRadius(dpToPx(14));
        llInputBox.setBackground(inputDraw);

        tvAmountLabel.setTextColor(textMid);
        tvPesoSymbol.setTextColor(accentGold);
        etAmount.setTextColor(textHigh);
        etAmount.setHintTextColor(textMuted);

        // Theme button chip style
        applyChipBg(btnTheme, isDark ? 0xFF222634 : 0xFFF3F4F6, strokeColor);

        // VAT rate chips
        refreshVatRateChips();
        styleVatRateChips(isDark, accentCyan, strokeColor);

        // Mode card
        tvModeLabel.setTextColor(accentCyan);
        rbInclusive.setTextColor(textHigh);
        rbExclusive.setTextColor(textHigh);
        swSeniorPwd.setTextColor(textHigh);
        dividerMode.setBackgroundColor(strokeColor);

        // WHT card
        tvWhtLabel.setTextColor(accentOrange);
        swWht.setTextColor(textHigh);
        dividerWht.setBackgroundColor(strokeColor);
        refreshWhtRateChips();
        styleWhtRateChips(isDark, accentOrange, strokeColor);
        tvWhtAmountLabel.setTextColor(textMid);
        tvWhtAmountVal.setTextColor(accentOrange);
        tvNetAfterWhtLabel.setTextColor(textMid);
        tvNetAfterWhtVal.setTextColor(accentEmerald);

        // Breakdown card
        tvBreakdownLabel.setTextColor(accentGold);
        tvTotalLabel.setTextColor(textHigh);
        tvTotalVal.setTextColor(accentEmerald);
        dividerBreakdown.setBackgroundColor(strokeColor);
        tvVatableLabel.setTextColor(textMid);
        tvVatableVal.setTextColor(textHigh);
        tvVatLabel.setTextColor(textMid);
        tvVatVal.setTextColor(accentCyan);
        tvDiscountLabel.setTextColor(accentGold);
        tvDiscountVal.setTextColor(accentGold);
        tvFormulaNote.setTextColor(textMuted);

        // History card
        tvHistoryLabel.setTextColor(textMid);
        applyChipBg(btnClearHistory, isDark ? 0xFF1A2736 : 0xFFF3F4F6, strokeColor);
        btnClearHistory.setTextColor(textMuted);

        // Bottom buttons
        GradientDrawable clearDraw = new GradientDrawable();
        clearDraw.setColor(isDark ? 0xFF1A2736 : 0xFFF3F4F6);
        clearDraw.setStroke(dpToPx(1), strokeColor);
        clearDraw.setCornerRadius(dpToPx(16));
        btnClear.setBackground(clearDraw);
        btnClear.setTextColor(textMid);

        GradientDrawable copyDraw = new GradientDrawable();
        copyDraw.setColor(isDark ? 0xFF1A2736 : 0xFFEFF6FF);
        copyDraw.setStroke(dpToPx(1), isDark ? accentCyan : 0xFF0284C7);
        copyDraw.setCornerRadius(dpToPx(16));
        btnCopy.setBackground(copyDraw);
        btnCopy.setTextColor(isDark ? accentGold : 0xFF0284C7);

        // Re-style history rows
        refreshHistoryCard();
    }

    private void applyCardBg(LinearLayout card, int bg, int stroke) {
        if (card == null) return;
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setStroke(dpToPx(1), stroke);
        d.setCornerRadius(dpToPx(20));
        card.setBackground(d);
    }

    private void applyChipBg(Button btn, int bg, int stroke) {
        if (btn == null) return;
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setStroke(dpToPx(1), stroke);
        d.setCornerRadius(dpToPx(20));
        btn.setBackground(d);
    }

    private void styleVatRateChips(boolean isDark, int accentCyan, int stroke) {
        if (chipVat12 == null || chipVat0 == null) return;
        boolean is12 = (customVatRate == 0.12);

        GradientDrawable d12 = new GradientDrawable();
        d12.setColor(is12 ? accentCyan : (isDark ? 0xFF222634 : 0xFFF3F4F6));
        d12.setStroke(dpToPx(1), is12 ? accentCyan : stroke);
        d12.setCornerRadius(dpToPx(20));
        chipVat12.setBackground(d12);
        chipVat12.setTextColor(is12 ? 0xFF000000 : (isDark ? 0xFF9CA3AF : 0xFF4B5563));

        GradientDrawable d0 = new GradientDrawable();
        d0.setColor(!is12 ? accentCyan : (isDark ? 0xFF222634 : 0xFFF3F4F6));
        d0.setStroke(dpToPx(1), !is12 ? accentCyan : stroke);
        d0.setCornerRadius(dpToPx(20));
        chipVat0.setBackground(d0);
        chipVat0.setTextColor(!is12 ? 0xFF000000 : (isDark ? 0xFF9CA3AF : 0xFF4B5563));
    }

    private void styleWhtRateChips(boolean isDark, int accentOrange, int stroke) {
        Button[] chips = {chipWhtNone, chipWht1, chipWht2, chipWht5};
        double[] rates  = {0.0, 0.01, 0.02, 0.05};

        for (int i = 0; i < chips.length; i++) {
            if (chips[i] == null) continue;
            boolean active = (Math.abs(whtRate - rates[i]) < 0.0001);
            GradientDrawable d = new GradientDrawable();
            d.setColor(active ? accentOrange : (isDark ? 0xFF222634 : 0xFFF3F4F6));
            d.setStroke(dpToPx(1), active ? accentOrange : stroke);
            d.setCornerRadius(dpToPx(20));
            chips[i].setBackground(d);
            chips[i].setTextColor(active ? 0xFF000000 : (isDark ? 0xFF9CA3AF : 0xFF4B5563));
        }
    }

    private void refreshVatRateChips() {
        // re-style using current theme colors
        boolean isDark = isDarkTheme;
        int accentCyan = isDark ? 0xFF00E5FF : 0xFF0284C7;
        int stroke     = isDark ? 0xFF2E3446 : 0xFFE5E7EB;
        styleVatRateChips(isDark, accentCyan, stroke);
    }

    private void refreshWhtRateChips() {
        boolean isDark    = isDarkTheme;
        int accentOrange  = isDark ? 0xFFFF9800 : 0xFFD97706;
        int stroke        = isDark ? 0xFF2E3446 : 0xFFE5E7EB;
        styleWhtRateChips(isDark, accentOrange, stroke);
    }

    private void updateWhtVisibility() {
        if (swWht == null) return;
        boolean on = swWht.isChecked();
        if (dividerWht    != null) dividerWht.setVisibility(on ? View.VISIBLE : View.GONE);
        if (llWhtRateRow  != null) llWhtRateRow.setVisibility(on ? View.VISIBLE : View.GONE);
        if (llWhtResultRow != null) llWhtResultRow.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    // ─── Calculation ──────────────────────────────────────────────────────────
    private double parseInput() {
        String str = etAmount.getText().toString().trim();
        if (str.isEmpty()) return 0.0;
        // Simple expression evaluator: supports +, -, *, / between numbers
        try {
            return evalExpression(str);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Tiny left-to-right expression evaluator supporting +, -, *, / and parentheses-free
     * sequences. Handles "1200+850", "5000-120", etc.
     */
    private double evalExpression(String expr) {
        expr = expr.trim();
        // Split on + and - (respecting leading minus for negative numbers)
        // We use a recursive descent approach: additive level only (*, / not in phone numpad)
        // Actually support all four ops via simple tokeniser
        return parseAddSub(expr);
    }

    private int pos;
    private String curExpr;

    private double parseAddSub(String expr) {
        curExpr = expr;
        pos = 0;
        return parseAS();
    }

    private double parseAS() {
        double result = parseMD();
        while (pos < curExpr.length()) {
            char c = curExpr.charAt(pos);
            if (c == '+') { pos++; result += parseMD(); }
            else if (c == '-') { pos++; result -= parseMD(); }
            else break;
        }
        return result;
    }

    private double parseMD() {
        double result = parseNum();
        while (pos < curExpr.length()) {
            char c = curExpr.charAt(pos);
            if (c == '*') { pos++; result *= parseNum(); }
            else if (c == '/') { pos++; double d = parseNum(); result = d != 0 ? result / d : 0; }
            else break;
        }
        return result;
    }

    private double parseNum() {
        while (pos < curExpr.length() && curExpr.charAt(pos) == ' ') pos++;
        int start = pos;
        if (pos < curExpr.length() && curExpr.charAt(pos) == '-') pos++;
        while (pos < curExpr.length() &&
               (Character.isDigit(curExpr.charAt(pos)) || curExpr.charAt(pos) == '.')) {
            pos++;
        }
        String token = curExpr.substring(start, pos).trim();
        return token.isEmpty() ? 0.0 : Double.parseDouble(token);
    }

    private void calculate() {
        double input = parseInput();
        boolean isInclusive = rbInclusive.isChecked();
        boolean isSeniorPwd = swSeniorPwd.isChecked();
        boolean useWht      = swWht != null && swWht.isChecked();

        // Effective VAT rate: Senior/PWD forces 0%
        double vatRate = isSeniorPwd ? 0.0 : customVatRate;
        double vatDivisor = 1.0 + vatRate;

        double vatableSales, vatAmount, discount = 0.0, total;

        if (isSeniorPwd) {
            // Senior/PWD: strip VAT from inclusive, then 20% off vatable
            if (isInclusive) {
                vatableSales = input / 1.12;   // always strip at 12% for PH legal basis
            } else {
                vatableSales = input;
            }
            vatAmount = 0.0;
            discount  = vatableSales * 0.20;
            total     = vatableSales - discount;

            llDiscountRow.setVisibility(View.VISIBLE);
            tvDiscountVal.setText("-" + currencyFmt.format(discount));
            tvVatVal.setText("₱0.00 (Exempt)");
            String vatRateNote = customVatRate == 0.0 ? "VAT-Exempt" : "12% VAT Exempt";
            tvFormulaNote.setText("RA 9994 / 10754: Net = Amount ÷ 1.12 | " + vatRateNote + " | 20% Discount");
        } else {
            llDiscountRow.setVisibility(View.GONE);

            if (vatRate == 0.0) {
                // VAT-Exempt
                vatableSales = input;
                vatAmount    = 0.0;
                total        = input;
                tvVatVal.setText("₱0.00 (VAT-Exempt)");
                tvFormulaNote.setText("VAT-Exempt: No 12% VAT applied to this transaction.");
            } else if (isInclusive) {
                vatableSales = input / vatDivisor;
                vatAmount    = input - vatableSales;
                total        = input;
                tvVatVal.setText(currencyFmt.format(vatAmount));
                tvFormulaNote.setText(
                    "Vatable Sales = Amount ÷ " + vatDivisor + " | "
                    + pctFmt.format(vatRate * 100) + " VAT = Total − Vatable");
            } else {
                vatableSales = input;
                vatAmount    = input * vatRate;
                total        = input + vatAmount;
                tvVatVal.setText(currencyFmt.format(vatAmount));
                tvFormulaNote.setText(
                    pctFmt.format(vatRate * 100) + " VAT = Amount × " + vatRate
                    + " | Total = Amount + VAT");
            }
        }

        tvVatableVal.setText(currencyFmt.format(vatableSales));
        tvTotalVal.setText(currencyFmt.format(total));

        // WHT calculation
        if (useWht) {
            double whtBase   = vatableSales;  // WHT applies on vatable amount
            double whtAmount = whtBase * whtRate;
            double netAfterWht = total - whtAmount;
            tvWhtAmountVal.setText("-" + currencyFmt.format(whtAmount));
            tvNetAfterWhtVal.setText(currencyFmt.format(netAfterWht));

            // Update WHT label to reflect rate
            if (tvWhtAmountLabel != null) {
                tvWhtAmountLabel.setText("WHT (" + pctFmt.format(whtRate * 100) + ")");
            }
        }

        // Save to history when input > 0
        if (input > 0) {
            saveToHistory(input, isInclusive, isSeniorPwd, vatRate, vatableSales, vatAmount,
                          discount, total, useWht, whtRate);
        }
    }

    // ─── Calculation History ─────────────────────────────────────────────────
    private void saveToHistory(double input, boolean inclusive, boolean seniorPwd,
                                double vatRate, double vatable, double vat,
                                double discount, double total,
                                boolean hasWht, double whtRate) {
        // Encode as compact string:  time|input|mode|senior|vatRate|vatable|vat|discount|total|whtRate
        String mode   = inclusive ? "INC" : "EXC";
        String entry  = timeFmt.format(new Date()) + "|"
                      + plainFmt.format(input)     + "|"
                      + mode                        + "|"
                      + (seniorPwd ? "1" : "0")    + "|"
                      + vatRate                     + "|"
                      + Math.round(vatable * 100)   + "|"
                      + Math.round(vat * 100)       + "|"
                      + Math.round(discount * 100)  + "|"
                      + Math.round(total * 100)     + "|"
                      + (hasWht ? whtRate : 0.0);

        String raw = prefs.getString(PREF_HISTORY, "");
        String[] parts = raw.isEmpty() ? new String[0] : raw.split("\n");

        // De-duplicate: skip if same as last entry
        if (parts.length > 0 && parts[parts.length - 1].equals(entry)) return;

        ArrayList<String> list = new ArrayList<>();
        for (String p : parts) if (!p.isEmpty()) list.add(p);
        list.add(entry);
        while (list.size() > MAX_HISTORY) list.remove(0);

        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(s).append("\n");
        prefs.edit().putString(PREF_HISTORY, sb.toString().trim()).apply();
        refreshHistoryCard();
    }

    private void refreshHistoryCard() {
        if (llHistoryList == null || cardHistory == null) return;
        llHistoryList.removeAllViews();

        String raw = prefs.getString(PREF_HISTORY, "");
        if (raw.isEmpty()) {
            cardHistory.setVisibility(View.GONE);
            return;
        }

        String[] parts = raw.split("\n");
        cardHistory.setVisibility(parts.length > 0 ? View.VISIBLE : View.GONE);

        int textHigh  = isDarkTheme ? 0xFFF0F3F8 : 0xFF111827;
        int textMid   = isDarkTheme ? 0xFF9CA3AF : 0xFF4B5563;
        int textMuted = isDarkTheme ? 0xFF6B7280 : 0xFF9CA3AF;
        int accentGold = isDarkTheme ? 0xFFFFD54F : 0xFFB45309;
        int strokeColor = isDarkTheme ? 0xFF2E3446 : 0xFFE5E7EB;

        // Show newest first
        for (int i = parts.length - 1; i >= 0; i--) {
            String entry = parts[i].trim();
            if (entry.isEmpty()) continue;
            String[] f = entry.split("\\|");
            if (f.length < 9) continue;

            try {
                String time    = f[0];
                double input   = Double.parseDouble(f[1]);
                String mode    = f[2];
                boolean senior = f[3].equals("1");
                double vatRate = Double.parseDouble(f[4]);
                double total   = Long.parseLong(f[8]) / 100.0;
                double vat     = Long.parseLong(f[6]) / 100.0;

                // Row container
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, dpToPx(8), 0, dpToPx(8));

                // Divider (except first)
                if (i < parts.length - 1) {
                    View div = new View(this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
                    lp.setMargins(0, 0, 0, dpToPx(8));
                    div.setLayoutParams(lp);
                    div.setBackgroundColor(strokeColor);
                    llHistoryList.addView(div);
                }

                // Top line: time + mode badge + total
                LinearLayout topLine = new LinearLayout(this);
                topLine.setOrientation(LinearLayout.HORIZONTAL);

                TextView tvTime = new TextView(this);
                tvTime.setText(time);
                tvTime.setTextColor(textMuted);
                tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                LinearLayout.LayoutParams lpTime = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tvTime.setLayoutParams(lpTime);
                topLine.addView(tvTime);

                String badge = mode + (senior ? " · Senior" : "") +
                               (vatRate == 0.0 && !senior ? " · Exempt" : "");
                TextView tvBadge = new TextView(this);
                tvBadge.setText(badge);
                tvBadge.setTextColor(textMuted);
                tvBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                topLine.addView(tvBadge);
                row.addView(topLine);

                // Bottom line: entered amount → total
                LinearLayout botLine = new LinearLayout(this);
                botLine.setOrientation(LinearLayout.HORIZONTAL);
                botLine.setPadding(0, dpToPx(2), 0, 0);

                TextView tvAmt = new TextView(this);
                tvAmt.setText("₱" + plainFmt.format(input));
                tvAmt.setTextColor(textMid);
                tvAmt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                LinearLayout.LayoutParams lpAmt = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tvAmt.setLayoutParams(lpAmt);
                botLine.addView(tvAmt);

                TextView tvTot = new TextView(this);
                tvTot.setText("= " + currencyFmt.format(total));
                tvTot.setTextColor(accentGold);
                tvTot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tvTot.setTypeface(null, android.graphics.Typeface.BOLD);
                botLine.addView(tvTot);

                row.addView(botLine);
                llHistoryList.addView(row);

            } catch (Exception ignored) {}
        }
    }

    // ─── Copy Summary ─────────────────────────────────────────────────────────
    private void copySummary() {
        String inputStr = etAmount.getText().toString().trim();
        if (inputStr.isEmpty()) inputStr = "0.00";

        StringBuilder sb = new StringBuilder();
        sb.append("─── PH VAT Calculation Summary ───\n");
        sb.append("Mode:          ").append(rbInclusive.isChecked() ? "VAT Inclusive" : "VAT Exclusive").append("\n");
        sb.append("VAT Rate:      ").append(swSeniorPwd.isChecked() ? "Exempt (Senior/PWD)" : pctFmt.format(customVatRate * 100)).append("\n");
        sb.append("Entered:       ₱").append(inputStr).append("\n");
        sb.append("Vatable Sales: ").append(tvVatableVal.getText()).append("\n");
        sb.append("VAT Amount:    ").append(tvVatVal.getText()).append("\n");
        if (swSeniorPwd.isChecked()) {
            sb.append("20% Discount:  ").append(tvDiscountVal.getText()).append("\n");
        }
        sb.append("Total Payable: ").append(tvTotalVal.getText()).append("\n");
        if (swWht != null && swWht.isChecked()) {
            sb.append("WHT (").append(pctFmt.format(whtRate * 100)).append("): ").append(tvWhtAmountVal.getText()).append("\n");
            sb.append("Net (after WHT): ").append(tvNetAfterWhtVal.getText()).append("\n");
        }
        sb.append("───────────────────────────────────\n");
        sb.append("BIR 12% VAT · RA 9994 Senior/PWD Discount");

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText("VAT Calculation", sb.toString());
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "Calculation copied!", Toast.LENGTH_SHORT).show();
        }
        haptic(40);
    }

    // ─── Haptics ──────────────────────────────────────────────────────────────
    private void haptic(int ms) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
    }

    // ─── Keyboard Shortcuts ───────────────────────────────────────────────────
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int kc = event.getKeyCode();
            if (event.isCtrlPressed() && kc == KeyEvent.KEYCODE_C) {
                copySummary(); return true;
            }
            if (kc == KeyEvent.KEYCODE_ESCAPE) {
                etAmount.setText(""); calculate(); return true;
            }
            if (event.isAltPressed()) {
                if (kc == KeyEvent.KEYCODE_T)      { btnTheme.performClick();             return true; }
                if (kc == KeyEvent.KEYCODE_I)      { rbInclusive.setChecked(true);        return true; }
                if (kc == KeyEvent.KEYCODE_E)      { rbExclusive.setChecked(true);        return true; }
                if (kc == KeyEvent.KEYCODE_S)      { swSeniorPwd.setChecked(!swSeniorPwd.isChecked()); return true; }
                if (kc == KeyEvent.KEYCODE_W)      { swWht.setChecked(!swWht.isChecked()); return true; }
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
