package com.phvat.calc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;

public class MainActivity extends Activity {

    // ─── Header ───────────────────────────────────────────────────────────────
    private LinearLayout rootLayout;
    private TextView tvAppTitle;
    private TextView tvAppSubtitle;
    private Button btnTheme;

    // ─── Hero Input ───────────────────────────────────────────────────────────
    private LinearLayout cardAmount;
    private TextView tvAmountLabel;
    private LinearLayout llInputBox;
    private TextView tvPesoSymbol;
    private EditText etAmount;
    private RadioGroup rgVatMode;
    private RadioButton rbInclusive, rbExclusive;

    // ─── Adjustments & Tax Rates ──────────────────────────────────────────────
    private LinearLayout cardMode;
    private TextView tvModeLabel;
    private Switch swSeniorPwd;
    private View dividerMode;
    private LinearLayout llVatRateRow;
    private Button chipVat12, chipVat0;

    private Switch swWht;
    private LinearLayout llWhtRateRow;
    private Button chipWhtNone, chipWht1, chipWht2, chipWht5;

    // Legacy stubs (safe fallback)
    private LinearLayout cardWht;
    private TextView tvWhtLabel;
    private View dividerWht;

    // ─── Invoice Ledger Breakdown ─────────────────────────────────────────────
    private LinearLayout cardBreakdown;
    private TextView tvBreakdownLabel;
    private TextView tvVatableLabel, tvVatableVal;
    private TextView tvVatLabel, tvVatVal;
    private LinearLayout llDiscountRow;
    private TextView tvDiscountLabel, tvDiscountVal;
    private LinearLayout llWhtResultRow;
    private TextView tvWhtAmountLabel, tvWhtAmountVal;
    private View dividerBreakdown;
    private TextView tvTotalLabel, tvTotalVal;
    private LinearLayout llNetAfterWhtRow;
    private TextView tvNetAfterWhtLabel, tvNetAfterWhtVal;
    private TextView tvFormulaNote;

    // ─── In-App Numpad Keys ───────────────────────────────────────────────────
    private LinearLayout layoutNumpad;
    private Button btnKey0, btnKey1, btnKey2, btnKey3, btnKey4;
    private Button btnKey5, btnKey6, btnKey7, btnKey8, btnKey9;
    private Button btnKey00, btnKeyDot, btnKeyPlus, btnKeyMinus;
    private Button btnKeyDel, btnKeyC;

    // ─── Bottom Actions ───────────────────────────────────────────────────────
    private Button btnClear, btnCopy;

    // ─── State ────────────────────────────────────────────────────────────────
    private boolean isDarkTheme = true;
    private double customVatRate = 0.12;   // 12% standard
    private double whtRate = 0.0;          // 0 = no WHT selected
    private SharedPreferences prefs;
    private Vibrator vibrator;

    private final DecimalFormat currencyFmt = new DecimalFormat("₱#,##0.00");
    private final DecimalFormat plainFmt    = new DecimalFormat("#.##");
    private final DecimalFormat pctFmt      = new DecimalFormat("0.##'%'");

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs       = getSharedPreferences("ph_vat_prefs", MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean("dark_theme", true);
        vibrator    = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initViews();
        setupListeners();
        applyTheme(isDarkTheme);
        calculate();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        String savedAmount = etAmount != null ? etAmount.getText().toString() : "";
        boolean isInc      = rbInclusive == null || rbInclusive.isChecked();
        boolean isPwd      = swSeniorPwd != null && swSeniorPwd.isChecked();
        boolean isWht      = swWht       != null && swWht.isChecked();
        double  vatR       = customVatRate;
        double  whtR       = whtRate;

        setContentView(R.layout.activity_main);
        initViews();
        setupListeners();
        applyTheme(isDarkTheme);

        customVatRate    = vatR;
        whtRate          = whtR;

        if (etAmount != null) etAmount.setText(savedAmount);
        if (rbInclusive != null) rbInclusive.setChecked(isInc);
        if (rbExclusive != null) rbExclusive.setChecked(!isInc);
        if (swSeniorPwd != null) swSeniorPwd.setChecked(isPwd);
        if (swWht != null) swWht.setChecked(isWht);

        refreshVatRateChips();
        refreshWhtRateChips();
        updateWhtVisibility();
        calculate();
    }

    // ─── View Binding ─────────────────────────────────────────────────────────
    private void initViews() {
        rootLayout        = findViewById(R.id.root_layout);
        tvAppTitle        = findViewById(R.id.tv_app_title);
        tvAppSubtitle     = findViewById(R.id.tv_app_subtitle);
        btnTheme          = findViewById(R.id.btn_theme);

        cardAmount        = findViewById(R.id.card_amount);
        tvAmountLabel     = findViewById(R.id.tv_amount_label);
        llInputBox        = findViewById(R.id.ll_input_box);
        tvPesoSymbol      = findViewById(R.id.tv_peso_symbol);
        etAmount          = findViewById(R.id.et_amount);
        rgVatMode         = findViewById(R.id.rg_vat_mode);
        rbInclusive       = findViewById(R.id.rb_inclusive);
        rbExclusive       = findViewById(R.id.rb_exclusive);

        // Prevent Android system keyboard from popping up over the ledger
        if (etAmount != null) {
            etAmount.setShowSoftInputOnFocus(false);
        }

        cardMode          = findViewById(R.id.card_mode);
        tvModeLabel       = findViewById(R.id.tv_mode_label);
        swSeniorPwd       = findViewById(R.id.sw_senior_pwd);
        dividerMode       = findViewById(R.id.divider_mode);
        llVatRateRow      = findViewById(R.id.ll_vat_rate_row);
        chipVat12         = findViewById(R.id.chip_vat_12);
        chipVat0          = findViewById(R.id.chip_vat_0);

        swWht             = findViewById(R.id.sw_wht);
        llWhtRateRow      = findViewById(R.id.ll_wht_rate_row);
        chipWhtNone       = findViewById(R.id.chip_wht_none);
        chipWht1          = findViewById(R.id.chip_wht_1);
        chipWht2          = findViewById(R.id.chip_wht_2);
        chipWht5          = findViewById(R.id.chip_wht_5);

        cardWht           = findViewById(R.id.card_wht);
        tvWhtLabel        = findViewById(R.id.tv_wht_label);
        dividerWht        = findViewById(R.id.divider_wht);

        cardBreakdown     = findViewById(R.id.card_breakdown);
        tvBreakdownLabel  = findViewById(R.id.tv_breakdown_label);
        tvVatableLabel    = findViewById(R.id.tv_vatable_label);
        tvVatableVal      = findViewById(R.id.tv_vatable_val);
        tvVatLabel        = findViewById(R.id.tv_vat_label);
        tvVatVal          = findViewById(R.id.tv_vat_val);
        llDiscountRow     = findViewById(R.id.ll_discount_row);
        tvDiscountLabel   = findViewById(R.id.tv_discount_label);
        tvDiscountVal     = findViewById(R.id.tv_discount_val);
        llWhtResultRow    = findViewById(R.id.ll_wht_result_row);
        tvWhtAmountLabel  = findViewById(R.id.tv_wht_amount_label);
        tvWhtAmountVal    = findViewById(R.id.tv_wht_amount_val);
        dividerBreakdown  = findViewById(R.id.divider_breakdown);
        tvTotalLabel      = findViewById(R.id.tv_total_label);
        tvTotalVal        = findViewById(R.id.tv_total_val);
        llNetAfterWhtRow  = findViewById(R.id.ll_net_after_wht_row);
        tvNetAfterWhtLabel = findViewById(R.id.tv_net_after_wht_label);
        tvNetAfterWhtVal  = findViewById(R.id.tv_net_after_wht_val);
        tvFormulaNote     = findViewById(R.id.tv_formula_note);

        // Numpad Keys
        layoutNumpad      = findViewById(R.id.layout_numpad);
        btnKey0           = findViewById(R.id.btn_key_0);
        btnKey1           = findViewById(R.id.btn_key_1);
        btnKey2           = findViewById(R.id.btn_key_2);
        btnKey3           = findViewById(R.id.btn_key_3);
        btnKey4           = findViewById(R.id.btn_key_4);
        btnKey5           = findViewById(R.id.btn_key_5);
        btnKey6           = findViewById(R.id.btn_key_6);
        btnKey7           = findViewById(R.id.btn_key_7);
        btnKey8           = findViewById(R.id.btn_key_8);
        btnKey9           = findViewById(R.id.btn_key_9);
        btnKey00          = findViewById(R.id.btn_key_00);
        btnKeyDot         = findViewById(R.id.btn_key_dot);
        btnKeyPlus        = findViewById(R.id.btn_key_plus);
        btnKeyMinus       = findViewById(R.id.btn_key_minus);
        btnKeyDel         = findViewById(R.id.btn_key_del);
        btnKeyC           = findViewById(R.id.btn_key_c);

        btnClear          = findViewById(R.id.btn_clear);
        btnCopy           = findViewById(R.id.btn_copy);
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

        swSeniorPwd.setOnCheckedChangeListener((btn, checked) -> calculate());

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
            if (checked && whtRate == 0.0) {
                whtRate = 0.02; // Default to standard 2% when toggled on
                refreshWhtRateChips();
            }
            calculate();
        });

        // WHT rate chips
        chipWhtNone.setOnClickListener(v -> { whtRate = 0.0;  refreshWhtRateChips(); calculate(); haptic(20); });
        chipWht1.setOnClickListener(v ->    { whtRate = 0.01; refreshWhtRateChips(); calculate(); haptic(20); });
        chipWht2.setOnClickListener(v ->    { whtRate = 0.02; refreshWhtRateChips(); calculate(); haptic(20); });
        chipWht5.setOnClickListener(v ->    { whtRate = 0.05; refreshWhtRateChips(); calculate(); haptic(20); });

        // Numpad key handlers
        setupNumpadKey(btnKey0, "0");
        setupNumpadKey(btnKey1, "1");
        setupNumpadKey(btnKey2, "2");
        setupNumpadKey(btnKey3, "3");
        setupNumpadKey(btnKey4, "4");
        setupNumpadKey(btnKey5, "5");
        setupNumpadKey(btnKey6, "6");
        setupNumpadKey(btnKey7, "7");
        setupNumpadKey(btnKey8, "8");
        setupNumpadKey(btnKey9, "9");
        setupNumpadKey(btnKey00, "00");
        setupNumpadKey(btnKeyDot, ".");
        setupNumpadKey(btnKeyPlus, "+");
        setupNumpadKey(btnKeyMinus, "-");

        if (btnKeyDel != null) {
            btnKeyDel.setOnClickListener(v -> deleteCharacter());
            btnKeyDel.setOnLongClickListener(v -> {
                clearInput();
                return true;
            });
        }

        if (btnKeyC != null) {
            btnKeyC.setOnClickListener(v -> clearInput());
        }

        if (btnClear != null) {
            btnClear.setOnClickListener(v -> clearInput());
        }

        btnCopy.setOnClickListener(v -> copySummary());
    }

    private void setupNumpadKey(Button btn, final String text) {
        if (btn == null) return;
        btn.setOnClickListener(v -> insertText(text));
    }

    private void insertText(String str) {
        haptic(15);
        if (etAmount == null) return;
        int start = Math.max(etAmount.getSelectionStart(), 0);
        int end = Math.max(etAmount.getSelectionEnd(), 0);
        Editable editable = etAmount.getText();
        if (editable == null) {
            etAmount.setText(str);
            etAmount.setSelection(str.length());
            return;
        }
        editable.replace(Math.min(start, end), Math.max(start, end), str);
    }

    private void deleteCharacter() {
        haptic(15);
        if (etAmount == null) return;
        Editable editable = etAmount.getText();
        if (editable == null || editable.length() == 0) return;
        int start = etAmount.getSelectionStart();
        int end = etAmount.getSelectionEnd();
        if (start != end) {
            editable.delete(Math.min(start, end), Math.max(start, end));
        } else if (start > 0) {
            editable.delete(start - 1, start);
        }
    }

    private void clearInput() {
        haptic(25);
        if (etAmount != null) {
            etAmount.setText("");
        }
    }

    // ─── Theme ────────────────────────────────────────────────────────────────
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void applyTheme(boolean isDark) {
        // Slate & Emerald Palette
        int bgMain      = isDark ? 0xFF090D16 : 0xFFF8FAFC;
        int bgCard      = isDark ? 0xFF111827 : 0xFFFFFFFF;
        int bgInner     = isDark ? 0xFF182235 : 0xFFF1F5F9;
        int strokeColor = isDark ? 0xFF1F2E45 : 0xFFE2E8F0;

        int textHigh    = isDark ? 0xFFF1F5F9 : 0xFF0F172A;
        int textMid     = isDark ? 0xFF94A3B8 : 0xFF475569;
        int textMuted   = isDark ? 0xFF64748B : 0xFF94A3B8;

        int accentEmerald = isDark ? 0xFF10B981 : 0xFF059669;
        int accentCyan    = isDark ? 0xFF38BDF8 : 0xFF0284C7;
        int accentAmber   = isDark ? 0xFFF59E0B : 0xFFD97706;

        // Status & Navigation Bars
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
        tvAppTitle.setTextColor(accentEmerald);
        tvAppSubtitle.setTextColor(textMid);
        btnTheme.setText(isDark ? "☀ Light" : "🌙 Dark");
        btnTheme.setTextColor(accentEmerald);
        applyChipBg(btnTheme, isDark ? 0xFF182235 : 0xFFF1F5F9, strokeColor);

        // Cards
        applyCardBg(cardAmount,    bgCard, strokeColor);
        applyCardBg(cardMode,      bgCard, strokeColor);
        applyCardBg(cardBreakdown, bgCard, strokeColor);

        // Input Box
        GradientDrawable inputDraw = new GradientDrawable();
        inputDraw.setColor(bgInner);
        inputDraw.setStroke(dpToPx(1), isDark ? strokeColor : 0xFFCBD5E1);
        inputDraw.setCornerRadius(dpToPx(12));
        llInputBox.setBackground(inputDraw);

        tvPesoSymbol.setTextColor(accentEmerald);
        etAmount.setTextColor(textHigh);
        etAmount.setHintTextColor(textMuted);

        // Segmented VAT Mode
        rbInclusive.setTextColor(textHigh);
        rbExclusive.setTextColor(textHigh);

        // Adjustments Card
        if (tvModeLabel != null) tvModeLabel.setTextColor(textMuted);
        swSeniorPwd.setTextColor(textHigh);
        styleSwitch(swSeniorPwd, isDark);
        dividerMode.setBackgroundColor(strokeColor);
        swWht.setTextColor(textHigh);
        styleSwitch(swWht, isDark);

        // VAT rate chips
        refreshVatRateChips();
        styleVatRateChips(isDark, accentEmerald, strokeColor);

        // WHT rate chips
        refreshWhtRateChips();
        styleWhtRateChips(isDark, accentAmber, strokeColor);

        // Invoice Ledger Breakdown Card
        tvBreakdownLabel.setTextColor(textMuted);
        tvVatableLabel.setTextColor(textMid);
        tvVatableVal.setTextColor(textHigh);
        tvVatLabel.setTextColor(textMid);
        tvVatVal.setTextColor(accentCyan);
        tvDiscountLabel.setTextColor(accentAmber);
        tvDiscountVal.setTextColor(accentAmber);
        tvWhtAmountLabel.setTextColor(accentAmber);
        tvWhtAmountVal.setTextColor(accentAmber);
        dividerBreakdown.setBackgroundColor(strokeColor);
        tvTotalLabel.setTextColor(textHigh);
        tvTotalVal.setTextColor(accentEmerald);
        tvNetAfterWhtLabel.setTextColor(textMid);
        tvNetAfterWhtVal.setTextColor(accentEmerald);
        tvFormulaNote.setTextColor(textMuted);

        // Numpad Keys Styling — Strictly Flat with Zero Shadow & Bounded Ripple
        int rippleColor = isDark ? 0x2EFFFFFF : 0x14000000;

        Button[] digitKeys = {
            btnKey0, btnKey1, btnKey2, btnKey3, btnKey4,
            btnKey5, btnKey6, btnKey7, btnKey8, btnKey9,
            btnKey00, btnKeyDot
        };
        for (Button key : digitKeys) {
            styleFlatKey(key, bgInner, strokeColor, textHigh, rippleColor, 12);
        }

        // Operator Keys
        styleFlatKey(btnKeyPlus,  bgCard, strokeColor, accentEmerald, rippleColor, 12);
        styleFlatKey(btnKeyMinus, bgCard, strokeColor, accentEmerald, rippleColor, 12);
        styleFlatKey(btnKeyDel,   bgCard, strokeColor, textMid,       rippleColor, 12);
        styleFlatKey(btnKeyC,     bgCard, strokeColor, isDark ? 0xFFF87171 : 0xFFDC2626, rippleColor, 12);

        // Bottom Copy Button
        int copyBg = isDark ? 0xFF064E3B : 0xFFD1FAE5;
        int copyStroke = isDark ? accentEmerald : 0xFF059669;
        int copyText = isDark ? 0xFFFFFFFF : 0xFF064E3B;
        int copyRipple = isDark ? 0x3310B981 : 0x22059669;
        styleFlatKey(btnCopy, copyBg, copyStroke, copyText, copyRipple, 14);
    }

    private void styleFlatKey(Button btn, int bg, int stroke, int textColor, int rippleColor, int radiusDp) {
        if (btn == null) return;
        btn.setElevation(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btn.setStateListAnimator(null);
        }

        GradientDrawable content = new GradientDrawable();
        content.setColor(bg);
        content.setStroke(dpToPx(1), stroke);
        content.setCornerRadius(dpToPx(radiusDp));

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(dpToPx(radiusDp));

        btn.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask));
        btn.setTextColor(textColor);
    }

    private void applyCardBg(LinearLayout card, int bg, int stroke) {
        if (card == null) return;
        card.setElevation(0);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setStroke(dpToPx(1), stroke);
        d.setCornerRadius(dpToPx(16));
        card.setBackground(d);
    }

    private void applyChipBg(Button btn, int bg, int stroke) {
        if (btn == null) return;
        btn.setElevation(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btn.setStateListAnimator(null);
        }
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setStroke(dpToPx(1), stroke);
        d.setCornerRadius(dpToPx(16));
        btn.setBackground(d);
    }

    private void styleVatRateChips(boolean isDark, int accentEmerald, int stroke) {
        if (chipVat12 == null || chipVat0 == null) return;
        boolean is12 = (customVatRate == 0.12);

        chipVat12.setElevation(0);
        chipVat0.setElevation(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            chipVat12.setStateListAnimator(null);
            chipVat0.setStateListAnimator(null);
        }

        GradientDrawable d12 = new GradientDrawable();
        d12.setColor(is12 ? accentEmerald : (isDark ? 0xFF182235 : 0xFFF1F5F9));
        d12.setStroke(dpToPx(1), is12 ? accentEmerald : stroke);
        d12.setCornerRadius(dpToPx(16));
        chipVat12.setBackground(d12);
        chipVat12.setTextColor(is12 ? 0xFFFFFFFF : (isDark ? 0xFF94A3B8 : 0xFF475569));

        GradientDrawable d0 = new GradientDrawable();
        d0.setColor(!is12 ? accentEmerald : (isDark ? 0xFF182235 : 0xFFF1F5F9));
        d0.setStroke(dpToPx(1), !is12 ? accentEmerald : stroke);
        d0.setCornerRadius(dpToPx(16));
        chipVat0.setBackground(d0);
        chipVat0.setTextColor(!is12 ? 0xFFFFFFFF : (isDark ? 0xFF94A3B8 : 0xFF475569));
    }

    private void styleWhtRateChips(boolean isDark, int accentAmber, int stroke) {
        Button[] chips = {chipWhtNone, chipWht1, chipWht2, chipWht5};
        double[] rates = {0.0, 0.01, 0.02, 0.05};

        for (int i = 0; i < chips.length; i++) {
            if (chips[i] == null) continue;
            chips[i].setElevation(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                chips[i].setStateListAnimator(null);
            }
            boolean active = (Math.abs(whtRate - rates[i]) < 0.0001);
            GradientDrawable d = new GradientDrawable();
            d.setColor(active ? accentAmber : (isDark ? 0xFF182235 : 0xFFF1F5F9));
            d.setStroke(dpToPx(1), active ? accentAmber : stroke);
            d.setCornerRadius(dpToPx(16));
            chips[i].setBackground(d);
            chips[i].setTextColor(active ? 0xFF000000 : (isDark ? 0xFF94A3B8 : 0xFF475569));
        }
    }

    private void styleSwitch(Switch sw, boolean isDark) {
        if (sw == null) return;
        int[][] states = new int[][] {
            new int[] { android.R.attr.state_checked },
            new int[] { -android.R.attr.state_checked }
        };
        int[] thumbColors = new int[] {
            0xFFFFFFFF,
            isDark ? 0xFFCBD5E1 : 0xFF64748B
        };
        int[] trackColors = new int[] {
            isDark ? 0xFF10B981 : 0xFF059669,
            isDark ? 0xFF3E4F66 : 0xFFCBD5E1
        };
        sw.setThumbTintList(new ColorStateList(states, thumbColors));
        sw.setTrackTintList(new ColorStateList(states, trackColors));
    }

    private void refreshVatRateChips() {
        boolean isDark        = isDarkTheme;
        int accentEmerald     = isDark ? 0xFF10B981 : 0xFF059669;
        int stroke            = isDark ? 0xFF1F2E45 : 0xFFE2E8F0;
        styleVatRateChips(isDark, accentEmerald, stroke);
    }

    private void refreshWhtRateChips() {
        boolean isDark      = isDarkTheme;
        int accentAmber     = isDark ? 0xFFF59E0B : 0xFFD97706;
        int stroke          = isDark ? 0xFF1F2E45 : 0xFFE2E8F0;
        styleWhtRateChips(isDark, accentAmber, stroke);
    }

    private void updateWhtVisibility() {
        if (swWht == null) return;
        boolean on = swWht.isChecked();
        if (llWhtRateRow != null) llWhtRateRow.setVisibility(on ? View.VISIBLE : View.GONE);
        if (llWhtResultRow != null) llWhtResultRow.setVisibility(on ? View.VISIBLE : View.GONE);
        if (llNetAfterWhtRow != null) llNetAfterWhtRow.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    // ─── Calculation ──────────────────────────────────────────────────────────
    private double parseInput() {
        if (etAmount == null) return 0.0;
        String str = etAmount.getText().toString().trim();
        if (str.isEmpty()) return 0.0;
        try {
            return evalExpression(str);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private int pos;
    private String curExpr;

    private double evalExpression(String expr) {
        curExpr = expr.trim();
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
            if (isInclusive) {
                vatableSales = input / 1.12;   // PH legal basis under RA 9994
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
            tvFormulaNote.setText("RA 9994 / 10754: Net = Amount ÷ 1.12 | " + vatRateNote + " | 20% Off");
        } else {
            llDiscountRow.setVisibility(View.GONE);

            if (vatRate == 0.0) {
                vatableSales = input;
                vatAmount    = 0.0;
                total        = input;
                tvVatVal.setText("₱0.00 (VAT-Exempt)");
                tvFormulaNote.setText("VAT-Exempt: Zero-rated transaction.");
            } else if (isInclusive) {
                vatableSales = input / vatDivisor;
                vatAmount    = input - vatableSales;
                total        = input;
                tvVatVal.setText(currencyFmt.format(vatAmount));
                tvFormulaNote.setText(
                    "Formula: Net = Amount ÷ " + plainFmt.format(vatDivisor) + " | "
                    + pctFmt.format(vatRate * 100) + " Output VAT = Gross − Net");
            } else {
                vatableSales = input;
                vatAmount    = input * vatRate;
                total        = input + vatAmount;
                tvVatVal.setText(currencyFmt.format(vatAmount));
                tvFormulaNote.setText(
                    "Formula: Output VAT = Net × " + plainFmt.format(vatRate)
                    + " | Total = Net + VAT");
            }
        }

        tvVatableVal.setText(currencyFmt.format(vatableSales));
        tvTotalVal.setText(currencyFmt.format(total));

        // Withholding Tax
        if (useWht) {
            double whtBase     = vatableSales;
            double whtAmount   = whtBase * whtRate;
            double netAfterWht = total - whtAmount;

            llWhtResultRow.setVisibility(View.VISIBLE);
            llNetAfterWhtRow.setVisibility(View.VISIBLE);

            tvWhtAmountVal.setText("-" + currencyFmt.format(whtAmount));
            tvNetAfterWhtVal.setText(currencyFmt.format(netAfterWht));

            if (tvWhtAmountLabel != null) {
                tvWhtAmountLabel.setText("Less: EWT (" + pctFmt.format(whtRate * 100) + ")");
            }
        } else {
            llWhtResultRow.setVisibility(View.GONE);
            llNetAfterWhtRow.setVisibility(View.GONE);
        }
    }

    // ─── Copy Summary ─────────────────────────────────────────────────────────
    private void copySummary() {
        String inputStr = etAmount.getText().toString().trim();
        if (inputStr.isEmpty()) inputStr = "0.00";

        StringBuilder sb = new StringBuilder();
        sb.append("─── VatCal PH Invoice Breakdown ───\n");
        sb.append("Calculation Mode: ").append(rbInclusive.isChecked() ? "VAT Inclusive" : "VAT Exclusive").append("\n");
        sb.append("VAT Rate:         ").append(swSeniorPwd.isChecked() ? "Exempt (Senior/PWD)" : pctFmt.format(customVatRate * 100)).append("\n");
        sb.append("Entered Amount:   ₱").append(inputStr).append("\n");
        sb.append("Vatable Sales:    ").append(tvVatableVal.getText()).append("\n");
        sb.append("12% Output VAT:   ").append(tvVatVal.getText()).append("\n");
        if (swSeniorPwd.isChecked()) {
            sb.append("Senior/PWD 20%:   ").append(tvDiscountVal.getText()).append("\n");
        }
        sb.append("Total Payable:    ").append(tvTotalVal.getText()).append("\n");
        if (swWht != null && swWht.isChecked()) {
            sb.append("Creditable EWT:   ").append(tvWhtAmountVal.getText()).append("\n");
            sb.append("Net Due/Received: ").append(tvNetAfterWhtVal.getText()).append("\n");
        }
        sb.append("───────────────────────────────\n");
        sb.append("Standard 12% VAT · RA 9994 Senior/PWD");

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText("VatCal PH Calculation", sb.toString());
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "Invoice breakdown copied!", Toast.LENGTH_SHORT).show();
        }
        haptic(35);
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
                clearInput(); return true;
            }
            if (event.isAltPressed()) {
                if (kc == KeyEvent.KEYCODE_T) { btnTheme.performClick(); return true; }
                if (kc == KeyEvent.KEYCODE_I) { rbInclusive.setChecked(true); return true; }
                if (kc == KeyEvent.KEYCODE_E) { rbExclusive.setChecked(true); return true; }
                if (kc == KeyEvent.KEYCODE_S) { swSeniorPwd.setChecked(!swSeniorPwd.isChecked()); return true; }
                if (kc == KeyEvent.KEYCODE_W) { swWht.setChecked(!swWht.isChecked()); return true; }
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
