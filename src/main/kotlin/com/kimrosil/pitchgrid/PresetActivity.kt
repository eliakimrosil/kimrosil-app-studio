package com.kimrosil.pitchgrid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class PresetActivity : Activity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var lvPresets: ListView
    private lateinit var btnBack: Button
    private var presetsList = mutableListOf<TonePreset>()
    private lateinit var adapter: PresetAdapter

    override fun attachBaseContext(newBase: Context) {
        val sp = newBase.getSharedPreferences("pitchgrid_prefs", Context.MODE_PRIVATE)
        val mode = sp.getInt("pref_theme_mode", 0)
        val config = Configuration(newBase.resources.configuration)

        val nightMode = when (mode) {
            1 -> Configuration.UI_MODE_NIGHT_NO
            2 -> Configuration.UI_MODE_NIGHT_YES
            else -> {
                val sysMode = try {
                    Settings.Secure.getInt(newBase.contentResolver, "ui_night_mode", 0)
                } catch (e: Exception) {
                    0
                }
                if (sysMode == 2) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
        }
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presets)

        dbHelper = DatabaseHelper(this)
        lvPresets = findViewById(R.id.lvPresets)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            finish()
        }

        loadPresets()
    }

    private fun loadPresets() {
        presetsList = dbHelper.getAllPresets().toMutableList()
        adapter = PresetAdapter()
        lvPresets.adapter = adapter
    }

    private inner class PresetAdapter : BaseAdapter() {
        override fun getCount(): Int = presetsList.size
        override fun getItem(position: Int): Any = presetsList[position]
        override fun getItemId(position: Int): Long = presetsList[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@PresetActivity)
                .inflate(R.layout.item_preset, parent, false)

            val preset = presetsList[position]
            val tvTitle = view.findViewById<TextView>(R.id.tvPresetTitle)
            val tvFreq = view.findViewById<TextView>(R.id.tvPresetFreq)
            val tvWave = view.findViewById<TextView>(R.id.tvPresetWave)
            val btnAudition = view.findViewById<Button>(R.id.btnAuditionPreset)
            val btnDelete = view.findViewById<Button>(R.id.btnDeletePreset)

            tvTitle.text = preset.name
            tvFreq.text = String.format("%.2f Hz", preset.frequency)
            tvWave.text = preset.waveform.name

            if (preset.isFactory) {
                btnDelete.visibility = View.GONE
            } else {
                btnDelete.visibility = View.VISIBLE
                btnDelete.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (dbHelper.deletePreset(preset.id)) {
                        Toast.makeText(this@PresetActivity, "Deleted preset: ${preset.name}", Toast.LENGTH_SHORT).show()
                        loadPresets()
                    }
                }
            }

            btnAudition.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val resultIntent = Intent().apply {
                    putExtra("freq", preset.frequency)
                    putExtra("waveform", preset.waveform.name)
                    putExtra("cents", preset.cents)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }

            return view
        }
    }
}
