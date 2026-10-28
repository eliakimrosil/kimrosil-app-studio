package com.kimrosil.cuescroll

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class EditorActivity : android.app.Activity() {

    companion object {
        const val EXTRA_ID = "script_id"
    }

    private lateinit var db: ScriptDb
    private lateinit var etTitle: EditText
    private lateinit var etBody: EditText
    private lateinit var tvWords: TextView
    private lateinit var tvTime: TextView
    private var scriptId = 0L
    private var skipAutoSave = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(CuePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        db = ScriptDb(this)

        etTitle = findViewById(R.id.etTitle)
        etBody = findViewById(R.id.etBody)
        tvWords = findViewById(R.id.tvStatWords)
        tvTime = findViewById(R.id.tvStatTime)

        scriptId = savedInstanceState?.getLong(EXTRA_ID) ?: intent.getLongExtra(EXTRA_ID, 0L)
        if (savedInstanceState == null) {
            db.get(scriptId)?.let {
                etTitle.setText(it.title)
                etBody.setText(it.body)
            } ?: run { scriptId = 0L }
        }

        findViewById<TextView>(R.id.tvHeader).text = if (scriptId == 0L) "NEW SCRIPT" else "EDIT SCRIPT"
        findViewById<View>(R.id.btnDelete).visibility = if (scriptId == 0L) View.INVISIBLE else View.VISIBLE

        etBody.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updateStats()
        })
        updateStats()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSave).setOnClickListener {
            if (persist()) finish()
        }
        findViewById<View>(R.id.btnPaste).setOnClickListener { pasteClipboard() }
        findViewById<View>(R.id.btnOnAir).setOnClickListener {
            if (persist()) {
                skipAutoSave = true
                startActivity(Intent(this, PrompterActivity::class.java).putExtra(EXTRA_ID, scriptId))
                finish()
            }
        }
        findViewById<View>(R.id.btnDelete).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    db.delete(scriptId)
                    skipAutoSave = true
                    finish()
                }
                .show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(EXTRA_ID, scriptId)
    }

    override fun onPause() {
        super.onPause()
        if (!skipAutoSave) persist(silent = true)
    }

    private fun updateStats() {
        val words = TextStats.words(etBody.text.toString())
        tvWords.text = words.toString()
        tvTime.text = TextStats.clock(TextStats.readSeconds(words))
    }

    private fun pasteClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, R.string.nothing_to_paste, Toast.LENGTH_SHORT).show()
            return
        }
        val start = etBody.selectionStart.coerceAtLeast(0)
        val end = etBody.selectionEnd.coerceAtLeast(start)
        etBody.text.replace(start, end, text)
    }

    /** Writes the script; returns false (and stays on screen) when there is nothing to save. */
    private fun persist(silent: Boolean = false): Boolean {
        val body = etBody.text.toString().trim()
        if (body.isEmpty()) {
            if (!silent) Toast.makeText(this, R.string.empty_script, Toast.LENGTH_SHORT).show()
            return false
        }
        val title = etTitle.text.toString().trim().ifEmpty { getString(R.string.untitled) }
        scriptId = db.save(scriptId, title, body)
        return true
    }
}
