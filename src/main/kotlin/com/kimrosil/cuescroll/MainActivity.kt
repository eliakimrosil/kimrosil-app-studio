package com.kimrosil.cuescroll

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView

class MainActivity : android.app.Activity() {

    private lateinit var db: ScriptDb
    private lateinit var list: ListView
    private lateinit var emptyView: View
    private lateinit var tvSubtitle: TextView
    private var scripts: List<Script> = emptyList()
    private val adapter = ScriptAdapter()
    private var settingsDialog: Dialog? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(CuePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = ScriptDb(this)
        db.seedIfFirstRun(this)

        list = findViewById(R.id.lvScripts)
        emptyView = findViewById(R.id.emptyView)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> openPrompter(scripts[pos].id) }

        findViewById<View>(R.id.btnNew).setOnClickListener { openEditor(0L) }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettings() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        settingsDialog?.dismiss()
        super.onDestroy()
    }

    private fun refresh() {
        scripts = db.all()
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (scripts.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (scripts.isEmpty()) View.GONE else View.VISIBLE
        val n = scripts.size
        tvSubtitle.text = getString(R.string.app_tagline) + "  /  " + n + if (n == 1) " SCRIPT" else " SCRIPTS"
    }

    private fun openPrompter(id: Long) {
        startActivity(Intent(this, PrompterActivity::class.java).putExtra(EditorActivity.EXTRA_ID, id))
    }

    private fun openEditor(id: Long) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_ID, id))
    }

    private fun showSettings() {
        val prefs = CuePrefs.of(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_bg)
        settingsDialog = dialog

        // Theme: Light / Dark / Auto
        val themeKeys = listOf<TextView>(
            view.findViewById(R.id.btnThemeLight),
            view.findViewById(R.id.btnThemeDark),
            view.findViewById(R.id.btnThemeAuto)
        )
        val themeValues = intArrayOf(CuePrefs.THEME_DAY, CuePrefs.THEME_NIGHT, CuePrefs.THEME_AUTO)
        UiKit.paintSegments(this, themeKeys, themeValues.indexOf(prefs.getInt(CuePrefs.KEY_THEME, CuePrefs.THEME_AUTO)))
        themeKeys.forEachIndexed { i, key ->
            key.setOnClickListener {
                if (prefs.getInt(CuePrefs.KEY_THEME, CuePrefs.THEME_AUTO) != themeValues[i]) {
                    prefs.edit().putInt(CuePrefs.KEY_THEME, themeValues[i]).apply()
                    dialog.dismiss()
                    recreate()
                }
            }
        }

        // Count-in
        val countKeys = listOf<TextView>(
            view.findViewById(R.id.segCountOff),
            view.findViewById(R.id.segCount3),
            view.findViewById(R.id.segCount5)
        )
        val countValues = intArrayOf(0, 3, 5)
        UiKit.paintSegments(this, countKeys, countValues.indexOf(prefs.getInt(CuePrefs.KEY_COUNTDOWN, 3)).coerceAtLeast(0))
        countKeys.forEachIndexed { i, key ->
            key.setOnClickListener {
                prefs.edit().putInt(CuePrefs.KEY_COUNTDOWN, countValues[i]).apply()
                UiKit.paintSegments(this, countKeys, i)
            }
        }

        // Mirror default
        val mirrorKeys = listOf<TextView>(
            view.findViewById(R.id.segMirrorOff),
            view.findViewById(R.id.segMirrorOn)
        )
        UiKit.paintSegments(this, mirrorKeys, if (prefs.getBoolean(CuePrefs.KEY_MIRROR, false)) 1 else 0)
        mirrorKeys.forEachIndexed { i, key ->
            key.setOnClickListener {
                prefs.edit().putBoolean(CuePrefs.KEY_MIRROR, i == 1).apply()
                UiKit.paintSegments(this, mirrorKeys, i)
            }
        }

        // About
        view.findViewById<TextView>(R.id.tvAboutName).text = getString(R.string.app_name)
        view.findViewById<TextView>(R.id.tvAboutVersion).text = versionLabel()
        view.findViewById<View>(R.id.tvPrivacy).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com/")))
            } catch (_: Exception) {
            }
        }

        view.findViewById<View>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_reset_title)
                .setMessage(R.string.confirm_reset_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    db.clear()
                    CuePrefs.clear(this)
                    db.seedIfFirstRun(this)
                    dialog.dismiss()
                    recreate()
                }
                .show()
        }

        dialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost(UiKit.dp(this, 480))
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun versionLabel(): String = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        "v${info.versionName}  /  BUILD ${info.longVersionCode}"
    } catch (_: Exception) {
        ""
    }

    private inner class ScriptAdapter : BaseAdapter() {
        override fun getCount() = scripts.size
        override fun getItem(position: Int) = scripts[position]
        override fun getItemId(position: Int) = scripts[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.item_script, parent, false)
            val s = scripts[position]
            val words = s.words
            row.findViewById<TextView>(R.id.tvTitle).text =
                s.title.ifBlank { getString(R.string.untitled) }
            row.findViewById<TextView>(R.id.tvWords).text = "$words WORDS"
            row.findViewById<TextView>(R.id.tvDuration).text =
                TextStats.clock(TextStats.readSeconds(words)) + " EST"
            row.findViewById<View>(R.id.btnModify).setOnClickListener { openEditor(s.id) }
            return row
        }
    }
}
