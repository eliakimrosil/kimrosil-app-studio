package com.kimrosil.sizevault;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
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

    private static final String PREFS_NAME = "size_vault_prefs";
    private static final String KEY_PROFILES_JSON = "profiles_json";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0: Auto, 1: Light, 2: Dark

    // UI Elements
    private TextView tvActiveProfileName;
    private TextView tvTotalProfilesCount;
    private TextView tvTotalSizesCount;
    private EditText etProfileName;
    private EditText etRelationship;
    private EditText etShoeUs;
    private EditText etShoeEu;
    private EditText etShirtSize;
    private EditText etPantsSize;
    private EditText etJacketSize;
    private EditText etRingSize;
    private EditText etBrandNotes;
    private Button btnSaveProfile;
    private LinearLayout llProfilesContainer;
    private TextView tvEmptyState;
    private Button btnThemeToggle;
    private ImageButton btnSettings;

    // State
    private int themeMode = 0;
    private final List<ProfileItem> profiles = new ArrayList<>();
    private String editingProfileId = null;

    public static class ProfileItem {
        public String id;
        public String name;
        public String relationship;
        public String shoeUs;
        public String shoeEu;
        public String shirtSize;
        public String pantsSize;
        public String jacketSize;
        public String ringSize;
        public String brandNotes;

        public ProfileItem(String name, String relationship) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
            this.relationship = relationship;
            this.shoeUs = "";
            this.shoeEu = "";
            this.shirtSize = "";
            this.pantsSize = "";
            this.jacketSize = "";
            this.ringSize = "";
            this.brandNotes = "";
        }

        public int getFilledSpecsCount() {
            int count = 0;
            if (!shoeUs.isEmpty()) count++;
            if (!shoeEu.isEmpty()) count++;
            if (!shirtSize.isEmpty()) count++;
            if (!pantsSize.isEmpty()) count++;
            if (!jacketSize.isEmpty()) count++;
            if (!ringSize.isEmpty()) count++;
            return count;
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("name", name);
                obj.put("relationship", relationship);
                obj.put("shoeUs", shoeUs);
                obj.put("shoeEu", shoeEu);
                obj.put("shirtSize", shirtSize);
                obj.put("pantsSize", pantsSize);
                obj.put("jacketSize", jacketSize);
                obj.put("ringSize", ringSize);
                obj.put("brandNotes", brandNotes);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static ProfileItem fromJson(JSONObject obj) {
            try {
                ProfileItem item = new ProfileItem(
                        obj.optString("name", "Member"),
                        obj.optString("relationship", "Self")
                );
                item.id = obj.optString("id", UUID.randomUUID().toString());
                item.shoeUs = obj.optString("shoeUs", "");
                item.shoeEu = obj.optString("shoeEu", "");
                item.shirtSize = obj.optString("shirtSize", "");
                item.pantsSize = obj.optString("pantsSize", "");
                item.jacketSize = obj.optString("jacketSize", "");
                item.ringSize = obj.optString("ringSize", "");
                item.brandNotes = obj.optString("brandNotes", "");
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

        if (profiles.isEmpty()) {
            seedSampleProfiles();
        }
        renderProfilesList();
    }

    private void initViews() {
        tvActiveProfileName = findViewById(R.id.tvActiveProfileName);
        tvTotalProfilesCount = findViewById(R.id.tvTotalProfilesCount);
        tvTotalSizesCount = findViewById(R.id.tvTotalSizesCount);
        etProfileName = findViewById(R.id.etProfileName);
        etRelationship = findViewById(R.id.etRelationship);
        etShoeUs = findViewById(R.id.etShoeUs);
        etShoeEu = findViewById(R.id.etShoeEu);
        etShirtSize = findViewById(R.id.etShirtSize);
        etPantsSize = findViewById(R.id.etPantsSize);
        etJacketSize = findViewById(R.id.etJacketSize);
        etRingSize = findViewById(R.id.etRingSize);
        etBrandNotes = findViewById(R.id.etBrandNotes);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        llProfilesContainer = findViewById(R.id.llProfilesContainer);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSettings = findViewById(R.id.btnSettings);

        updateThemeToggleButton();
    }

    private void setupListeners() {
        btnSaveProfile.setOnClickListener(v -> handleSaveProfile());
        btnThemeToggle.setOnClickListener(v -> toggleTheme());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void handleSaveProfile() {
        String name = etProfileName.getText().toString().trim();
        String rel = etRelationship.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a profile name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (rel.isEmpty()) {
            rel = "Self";
        }

        ProfileItem item;
        if (editingProfileId != null) {
            item = findProfileById(editingProfileId);
            if (item == null) {
                item = new ProfileItem(name, rel);
                profiles.add(0, item);
            } else {
                item.name = name;
                item.relationship = rel;
            }
            editingProfileId = null;
            btnSaveProfile.setText("Save Sizing Profile");
        } else {
            item = new ProfileItem(name, rel);
            profiles.add(0, item);
        }

        item.shoeUs = etShoeUs.getText().toString().trim();
        item.shoeEu = etShoeEu.getText().toString().trim();
        item.shirtSize = etShirtSize.getText().toString().trim();
        item.pantsSize = etPantsSize.getText().toString().trim();
        item.jacketSize = etJacketSize.getText().toString().trim();
        item.ringSize = etRingSize.getText().toString().trim();
        item.brandNotes = etBrandNotes.getText().toString().trim();

        saveState();
        clearForm();
        renderProfilesList();
        Toast.makeText(this, "Saved profile: " + name, Toast.LENGTH_SHORT).show();
    }

    private ProfileItem findProfileById(String id) {
        for (ProfileItem p : profiles) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    private void clearForm() {
        etProfileName.setText("");
        etRelationship.setText("");
        etShoeUs.setText("");
        etShoeEu.setText("");
        etShirtSize.setText("");
        etPantsSize.setText("");
        etJacketSize.setText("");
        etRingSize.setText("");
        etBrandNotes.setText("");
        editingProfileId = null;
        btnSaveProfile.setText("Save Sizing Profile");
    }

    private void renderProfilesList() {
        int totalSpecs = 0;
        for (ProfileItem p : profiles) {
            totalSpecs += p.getFilledSpecsCount();
        }

        tvTotalProfilesCount.setText(String.valueOf(profiles.size()));
        tvTotalSizesCount.setText(String.valueOf(totalSpecs));

        llProfilesContainer.removeAllViews();
        if (profiles.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            for (ProfileItem p : profiles) {
                llProfilesContainer.addView(createProfileCardView(p));
            }
        }
    }

    private View createProfileCardView(ProfileItem profile) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_m3);
        card.setPadding(32, 28, 32, 28);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 24);
        card.setLayoutParams(lp);

        // Header Row: Name & Relation
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleCol.setLayoutParams(titleLp);

        TextView tvName = new TextView(this);
        tvName.setText(profile.name);
        tvName.setTextSize(18);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setTextColor(getResources().getColor(R.color.m3_on_surface));

        TextView tvRel = new TextView(this);
        tvRel.setText(profile.relationship + " • " + profile.getFilledSpecsCount() + " measurements");
        tvRel.setTextSize(12);
        tvRel.setTextColor(getResources().getColor(R.color.m3_on_surface_variant));

        titleCol.addView(tvName);
        titleCol.addView(tvRel);
        topRow.addView(titleCol);
        card.addView(topRow);

        // 2-Column Tabular Specs Grid
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, 16, 0, 8);

        // Row 1: Shoe (US/EU) & Shirt
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(createSpecItem("Shoe (US / EU)", profile.shoeUs + (profile.shoeEu.isEmpty() ? "" : " / " + profile.shoeEu)));
        r1.addView(createSpecItem("Shirt / Top", profile.shirtSize));
        grid.addView(r1);

        // Row 2: Pants & Jacket/Dress
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setPadding(0, 8, 0, 0);
        r2.addView(createSpecItem("Pants (W x L)", profile.pantsSize));
        r2.addView(createSpecItem("Jacket / Dress", profile.jacketSize));
        grid.addView(r2);

        // Row 3: Ring & Notes
        if (!profile.ringSize.isEmpty() || !profile.brandNotes.isEmpty()) {
            LinearLayout r3 = new LinearLayout(this);
            r3.setOrientation(LinearLayout.HORIZONTAL);
            r3.setPadding(0, 8, 0, 0);
            r3.addView(createSpecItem("Ring Size", profile.ringSize));
            r3.addView(createSpecItem("Fit Notes", profile.brandNotes));
            grid.addView(r3);
        }

        card.addView(grid);

        // Action Buttons Row (Edit & Delete)
        LinearLayout actRow = new LinearLayout(this);
        actRow.setOrientation(LinearLayout.HORIZONTAL);
        actRow.setPadding(0, 16, 0, 0);

        Button btnEdit = new Button(this);
        btnEdit.setText("Edit Profile");
        btnEdit.setTextSize(12);
        btnEdit.setTextColor(getResources().getColor(R.color.m3_primary));
        btnEdit.setBackgroundResource(R.drawable.btn_m3_outlined);
        btnEdit.setMinHeight(120);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        editLp.setMargins(0, 0, 12, 0);
        btnEdit.setLayoutParams(editLp);
        btnEdit.setOnClickListener(v -> populateFormForEdit(profile));

        Button btnDel = new Button(this);
        btnDel.setText("Delete");
        btnDel.setTextSize(12);
        btnDel.setTextColor(getResources().getColor(R.color.m3_error));
        btnDel.setBackgroundResource(R.drawable.btn_m3_outlined);
        btnDel.setMinHeight(120);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f);
        btnDel.setLayoutParams(delLp);
        btnDel.setOnClickListener(v -> {
            profiles.remove(profile);
            saveState();
            renderProfilesList();
            Toast.makeText(this, "Removed " + profile.name, Toast.LENGTH_SHORT).show();
        });

        actRow.addView(btnEdit);
        actRow.addView(btnDel);
        card.addView(actRow);

        return card;
    }

    private View createSpecItem(String label, String value) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        col.setLayoutParams(lp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(11);
        tvLabel.setTextColor(getResources().getColor(R.color.m3_on_surface_variant));

        TextView tvVal = new TextView(this);
        tvVal.setText(value == null || value.isEmpty() ? "—" : value);
        tvVal.setTextSize(14);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        tvVal.setTextColor(getResources().getColor(R.color.m3_on_surface));

        col.addView(tvLabel);
        col.addView(tvVal);
        return col;
    }

    private void populateFormForEdit(ProfileItem profile) {
        editingProfileId = profile.id;
        etProfileName.setText(profile.name);
        etRelationship.setText(profile.relationship);
        etShoeUs.setText(profile.shoeUs);
        etShoeEu.setText(profile.shoeEu);
        etShirtSize.setText(profile.shirtSize);
        etPantsSize.setText(profile.pantsSize);
        etJacketSize.setText(profile.jacketSize);
        etRingSize.setText(profile.ringSize);
        etBrandNotes.setText(profile.brandNotes);

        btnSaveProfile.setText("Update Profile (" + profile.name + ")");
        Toast.makeText(this, "Editing: " + profile.name, Toast.LENGTH_SHORT).show();
    }

    private void seedSampleProfiles() {
        ProfileItem p1 = new ProfileItem("Self", "Self");
        p1.shoeUs = "10.5";
        p1.shoeEu = "44";
        p1.shirtSize = "L (16.5)";
        p1.pantsSize = "34x32";
        p1.jacketSize = "42R";
        p1.ringSize = "9.5";
        p1.brandNotes = "Nike runs narrow; Uniqlo fits TTS";
        profiles.add(p1);

        ProfileItem p2 = new ProfileItem("Sarah", "Partner / Spouse");
        p2.shoeUs = "7.5";
        p2.shoeEu = "38";
        p2.shirtSize = "S";
        p2.pantsSize = "27";
        p2.jacketSize = "Size 4";
        p2.ringSize = "6.0";
        p2.brandNotes = "Zara runs small; size up to M";
        profiles.add(p2);
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
                .setTitle("SizeVault")
                .setMessage("Family & Personal Sizing Safe\nVersion 1.0.0\n\nKimRosil App Studio Portfolio freeware.\n100% Offline, on-device data storage.\n\nKeep clothes and shoe sizes at your fingertips when shopping sales.")
                .setPositiveButton("Close", null)
                .setNeutralButton("Reset Sample Data", (dialog, which) -> {
                    profiles.clear();
                    seedSampleProfiles();
                    saveState();
                    renderProfilesList();
                    Toast.makeText(MainActivity.this, "Reset to default family sizing", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void saveState() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putInt(KEY_THEME_MODE, themeMode);

        JSONArray arr = new JSONArray();
        for (ProfileItem p : profiles) {
            arr.put(p.toJson());
        }
        ed.putString(KEY_PROFILES_JSON, arr.toString());
        ed.commit();
    }

    private void loadPreferences() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        themeMode = sp.getInt(KEY_THEME_MODE, 0);

        profiles.clear();
        String json = sp.getString(KEY_PROFILES_JSON, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    ProfileItem item = ProfileItem.fromJson(arr.getJSONObject(i));
                    if (item != null) {
                        profiles.add(item);
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
