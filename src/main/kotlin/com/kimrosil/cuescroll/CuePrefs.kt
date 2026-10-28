package com.kimrosil.cuescroll

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

/** Shared settings plus the Day / Night / Auto configuration override used by every activity. */
object CuePrefs {
    private const val NAME = "cuescroll_prefs"

    const val THEME_DAY = 1
    const val THEME_NIGHT = 2
    const val THEME_AUTO = 0

    const val KEY_THEME = "theme_mode"
    const val KEY_SPEED = "speed"
    const val KEY_SIZE = "text_size"
    const val KEY_MIRROR = "mirror"
    const val KEY_COLOR = "text_color"
    const val KEY_COUNTDOWN = "countdown_sec"
    const val KEY_SEEDED = "seeded"

    const val SPEED_MIN = 4
    const val SPEED_MAX = 240
    const val SPEED_DEFAULT = 36
    const val SIZE_MIN = 20
    const val SIZE_MAX = 96
    const val SIZE_DEFAULT = 36

    fun of(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Forces the night bit of the configuration; Auto leaves the system value untouched. */
    fun wrap(base: Context): Context {
        val mode = base.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, THEME_AUTO)
        val night = when (mode) {
            THEME_DAY -> Configuration.UI_MODE_NIGHT_NO
            THEME_NIGHT -> Configuration.UI_MODE_NIGHT_YES
            else -> return base
        }
        val config = Configuration(base.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return base.createConfigurationContext(config)
    }

    fun clear(ctx: Context) {
        of(ctx).edit().clear().apply()
    }
}
