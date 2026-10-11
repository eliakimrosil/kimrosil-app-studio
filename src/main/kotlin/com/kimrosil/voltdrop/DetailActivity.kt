package com.kimrosil.voltdrop

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class DetailActivity : Activity() {

    private lateinit var btnBack: Button
    private lateinit var btnClearHistory: Button
    private lateinit var btnTabTable: Button
    private lateinit var btnTabHistory: Button
    private lateinit var llTableContainer: LinearLayout
    private lateinit var llHistoryContainer: LinearLayout
    private lateinit var llTableRows: LinearLayout
    private lateinit var llHistoryItems: LinearLayout
    private lateinit var tvEmptyHistory: TextView
    private lateinit var dbHelper: DatabaseHelper

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("voltdrop_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getInt("pref_theme_mode", 0)
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
        setContentView(R.layout.activity_detail)

        dbHelper = DatabaseHelper(this)

        btnBack = findViewById(R.id.btnBack)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        btnTabTable = findViewById(R.id.btnTabTable)
        btnTabHistory = findViewById(R.id.btnTabHistory)
        llTableContainer = findViewById(R.id.llTableContainer)
        llHistoryContainer = findViewById(R.id.llHistoryContainer)
        llTableRows = findViewById(R.id.llTableRows)
        llHistoryItems = findViewById(R.id.llHistoryItems)
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory)

        btnBack.setOnClickListener {
            finish()
        }

        btnTabTable.setOnClickListener {
            showTableTab()
        }

        btnTabHistory.setOnClickListener {
            showHistoryTab()
        }

        btnClearHistory.setOnClickListener {
            dbHelper.clearHistory()
            renderHistory()
            Toast.makeText(this, "Calculation history cleared", Toast.LENGTH_SHORT).show()
        }

        renderWireTable()
        renderHistory()
    }

    private fun showTableTab() {
        btnTabTable.setBackgroundResource(R.drawable.chip_active)
        btnTabTable.setTextColor(getColor(R.color.m3_on_primary))

        btnTabHistory.setBackgroundResource(R.drawable.chip_inactive)
        btnTabHistory.setTextColor(getColor(R.color.m3_on_surface))

        llTableContainer.visibility = View.VISIBLE
        llHistoryContainer.visibility = View.GONE
    }

    private fun showHistoryTab() {
        btnTabHistory.setBackgroundResource(R.drawable.chip_active)
        btnTabHistory.setTextColor(getColor(R.color.m3_on_primary))

        btnTabTable.setBackgroundResource(R.drawable.chip_inactive)
        btnTabTable.setTextColor(getColor(R.color.m3_on_surface))

        llHistoryContainer.visibility = View.VISIBLE
        llTableContainer.visibility = View.GONE
        renderHistory()
    }

    private fun renderWireTable() {
        llTableRows.removeAllViews()

        for (g in CalculatorEngine.WIRE_GAUGES) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(12, 16, 12, 16)
                setBackgroundResource(R.drawable.card_m3)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
                layoutParams = params
            }

            val tvGauge = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.2f)
                text = "${g.name}\n${g.areaMm2} mm²"
                setTextColor(getColor(R.color.m3_on_surface))
                textSize = 12f
            }

            val tvCu = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.0f)
                text = String.format("%.3f", g.copperResistancePerKm)
                setTextColor(getColor(R.color.copper_accent))
                textSize = 12f
            }

            val tvAl = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.0f)
                text = String.format("%.3f", g.aluminumResistancePerKm)
                setTextColor(getColor(R.color.telemetry_accent))
                textSize = 12f
            }

            val tvAmp = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f)
                text = "${g.ampacity75C} A"
                gravity = android.view.Gravity.END
                setTextColor(getColor(R.color.m3_on_surface))
                textSize = 12f
            }

            row.addView(tvGauge)
            row.addView(tvCu)
            row.addView(tvAl)
            row.addView(tvAmp)

            llTableRows.addView(row)
        }
    }

    private fun renderHistory() {
        llHistoryItems.removeAllViews()
        val records = dbHelper.getAllRecords()

        if (records.isEmpty()) {
            tvEmptyHistory.visibility = View.VISIBLE
            btnClearHistory.visibility = View.GONE
        } else {
            tvEmptyHistory.visibility = View.GONE
            btnClearHistory.visibility = View.VISIBLE

            for (r in records) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 14, 16, 14)
                    setBackgroundResource(R.drawable.card_m3)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 6, 0, 6)
                    }
                    layoutParams = params
                }

                val header = TextView(this).apply {
                    text = "${r.title} • ${r.gauge}"
                    setTextColor(getColor(R.color.m3_primary))
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }

                val body = TextView(this).apply {
                    text = "${r.voltage}V @ ${r.current}A • ${r.distance} ${r.unit} (${r.conductor})\nDrop: ${String.format("%.2f", r.dropVolts)}V (${String.format("%.1f", r.dropPercent)}%) • Loss: ${String.format("%.1f", r.powerLoss)}W"
                    setTextColor(getColor(R.color.m3_on_surface))
                    textSize = 12f
                }

                val time = TextView(this).apply {
                    text = r.timestamp
                    setTextColor(getColor(R.color.m3_on_surface_variant))
                    textSize = 10f
                }

                card.addView(header)
                card.addView(body)
                card.addView(time)

                llHistoryItems.addView(card)
            }
        }
    }
}
