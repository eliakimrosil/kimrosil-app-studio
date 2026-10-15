package com.kimrosil.combgauge;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PresetBottomSheetDialog extends Dialog {

    public interface OnPresetSelectedListener {
        void onPresetSelected(CoatingDbHelper.CoatingPreset preset);
    }

    private final OnPresetSelectedListener listener;
    private final CoatingDbHelper dbHelper;
    private final List<CoatingDbHelper.CoatingPreset> presetList = new ArrayList<>();
    private PresetAdapter adapter;
    private String currentFilter = "All";

    private EditText edtPresetName;
    private EditText edtPresetSolids;
    private EditText edtMaxThinnerLimit;
    private View llAddPresetForm;

    public PresetBottomSheetDialog(Context context, CoatingDbHelper dbHelper, OnPresetSelectedListener listener) {
        super(context, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar_MinWidth);
        this.dbHelper = dbHelper;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_coating_presets);

        ListView lvPresets = findViewById(R.id.rvCoatingPresets);
        adapter = new PresetAdapter();
        lvPresets.setAdapter(adapter);

        llAddPresetForm = findViewById(R.id.llAddPresetForm);
        edtPresetName = findViewById(R.id.edtPresetName);
        edtPresetSolids = findViewById(R.id.edtPresetSolids);
        edtMaxThinnerLimit = findViewById(R.id.edtMaxThinnerLimit);

        Button btnFilterAll = findViewById(R.id.chipFilterAll);
        Button btnFilterZinc = findViewById(R.id.chipFilterZinc);
        Button btnFilterEpoxy = findViewById(R.id.chipFilterEpoxy);
        Button btnFilterPoly = findViewById(R.id.chipFilterPoly);
        Button btnFilterHighSolids = findViewById(R.id.chipFilterHighSolids);

        btnFilterAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setFilter("All"); }
        });
        btnFilterZinc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setFilter("Zinc"); }
        });
        btnFilterEpoxy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setFilter("Epoxy"); }
        });
        btnFilterPoly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setFilter("Polyurethane"); }
        });
        btnFilterHighSolids.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setFilter("High-Solids"); }
        });

        Button btnCreatePreset = findViewById(R.id.btnCreatePreset);
        btnCreatePreset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (llAddPresetForm.getVisibility() == View.VISIBLE) {
                    llAddPresetForm.setVisibility(View.GONE);
                } else {
                    llAddPresetForm.setVisibility(View.VISIBLE);
                }
            }
        });

        Button btnSavePreset = findViewById(R.id.btnSavePreset);
        btnSavePreset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCustomPreset();
            }
        });

        Button btnCloseDialog = findViewById(R.id.btnClosePresets);
        btnCloseDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        loadPresets();
    }

    private void setFilter(String filter) {
        currentFilter = filter;
        loadPresets();
    }

    private void loadPresets() {
        presetList.clear();
        presetList.addAll(dbHelper.getPresets(currentFilter));
        adapter.notifyDataSetChanged();
    }

    private void saveCustomPreset() {
        String name = edtPresetName.getText().toString().trim();
        String solidsStr = edtPresetSolids.getText().toString().trim();
        String thinnerStr = edtMaxThinnerLimit.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(getContext(), "Enter coating formulation name", Toast.LENGTH_SHORT).show();
            return;
        }

        double solids = 70.0;
        try {
            if (!TextUtils.isEmpty(solidsStr)) {
                solids = Double.parseDouble(solidsStr);
            }
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid solids %", Toast.LENGTH_SHORT).show();
            return;
        }

        double thinner = 10.0;
        try {
            if (!TextUtils.isEmpty(thinnerStr)) {
                thinner = Double.parseDouble(thinnerStr);
            }
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid thinner limit", Toast.LENGTH_SHORT).show();
            return;
        }

        solids = Math.max(1.0, Math.min(100.0, solids));
        thinner = Math.max(0.0, Math.min(50.0, thinner));

        long id = dbHelper.insertCustomPreset(name, currentFilter.equals("All") ? "Custom" : currentFilter, solids, thinner);
        if (id != -1) {
            Toast.makeText(getContext(), "Custom formulation saved", Toast.LENGTH_SHORT).show();
            edtPresetName.setText("");
            edtPresetSolids.setText("");
            edtMaxThinnerLimit.setText("");
            llAddPresetForm.setVisibility(View.GONE);
            loadPresets();
        }
    }

    private class PresetAdapter extends BaseAdapter {
        @Override
        public int getCount() { return presetList.size(); }
        @Override
        public Object getItem(int pos) { return presetList.get(pos); }
        @Override
        public long getItemId(int pos) { return presetList.get(pos).id; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_coating_preset, parent, false);
            }

            final CoatingDbHelper.CoatingPreset item = presetList.get(pos);

            TextView tvName = convertView.findViewById(R.id.tvPresetItemName);
            TextView tvDetails = convertView.findViewById(R.id.tvPresetItemDetails);
            TextView tvBadge = convertView.findViewById(R.id.tvPresetItemBadge);

            tvName.setText(item.name);
            tvDetails.setText(String.format(Locale.US, "%s • %.0f%% Volume Solids • Max %.0f%% Thinner",
                    item.genericType, item.volumeSolids, item.maxThinner));

            if (item.isCustom) {
                tvBadge.setVisibility(View.VISIBLE);
                tvBadge.setText("CUSTOM");
            } else {
                tvBadge.setVisibility(View.GONE);
            }

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onPresetSelected(item);
                    }
                    dismiss();
                }
            });

            return convertView;
        }
    }
}