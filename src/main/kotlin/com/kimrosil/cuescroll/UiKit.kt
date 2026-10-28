package com.kimrosil.cuescroll

import android.content.Context
import android.widget.TextView

object UiKit {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /** Paints a row of segment keys: the selected one lit ruby, the rest recessed. */
    fun paintSegments(ctx: Context, keys: List<TextView>, selected: Int) {
        keys.forEachIndexed { i, key ->
            val on = i == selected
            key.setBackgroundResource(if (on) R.drawable.seg_on else R.drawable.seg_off)
            key.setTextColor(ctx.getColor(if (on) R.color.on_ruby else R.color.text_primary))
            key.isClickable = true
            key.isFocusable = true
        }
    }
}
