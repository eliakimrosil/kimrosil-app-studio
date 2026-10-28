package com.kimrosil.cuescroll

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Script(val id: Long, val title: String, val body: String, val updated: Long) {
    val words: Int get() = TextStats.words(body)
}

object TextStats {
    const val READ_WPM = 150

    fun words(text: String): Int {
        var count = 0
        var inWord = false
        for (ch in text) {
            if (ch.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
    }

    fun clock(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
    }

    fun readSeconds(words: Int): Int = (words * 60.0 / READ_WPM).toInt()
}

/** Private on-device script library. No network, no export. */
class ScriptDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "cuescroll.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE scripts (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, body TEXT NOT NULL, updated INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS scripts")
        onCreate(db)
    }

    fun all(): List<Script> {
        val out = ArrayList<Script>()
        readableDatabase.rawQuery(
            "SELECT _id, title, body, updated FROM scripts ORDER BY updated DESC", null
        ).use { c ->
            while (c.moveToNext()) out.add(Script(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
        }
        return out
    }

    fun get(id: Long): Script? {
        readableDatabase.rawQuery(
            "SELECT _id, title, body, updated FROM scripts WHERE _id = ?", arrayOf(id.toString())
        ).use { c ->
            return if (c.moveToFirst()) Script(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)) else null
        }
    }

    /** Inserts when [id] is 0, otherwise updates. Returns the row id. */
    fun save(id: Long, title: String, body: String): Long {
        val v = ContentValues().apply {
            put("title", title)
            put("body", body)
            put("updated", System.currentTimeMillis())
        }
        return if (id <= 0L) {
            writableDatabase.insert("scripts", null, v)
        } else {
            writableDatabase.update("scripts", v, "_id = ?", arrayOf(id.toString()))
            id
        }
    }

    fun delete(id: Long) {
        writableDatabase.delete("scripts", "_id = ?", arrayOf(id.toString()))
    }

    fun clear() {
        writableDatabase.delete("scripts", null, null)
    }

    fun seedIfFirstRun(ctx: Context) {
        val prefs = CuePrefs.of(ctx)
        if (prefs.getBoolean(CuePrefs.KEY_SEEDED, false)) return
        if (all().isEmpty()) {
            save(0, ctx.getString(R.string.sample_title), ctx.getString(R.string.sample_body))
        }
        prefs.edit().putBoolean(CuePrefs.KEY_SEEDED, true).apply()
    }
}
