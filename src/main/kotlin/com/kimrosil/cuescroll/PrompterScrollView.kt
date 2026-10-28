package com.kimrosil.cuescroll

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/** ScrollView without fling so the auto-scroll position stays deterministic after a scrub. */
class PrompterScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    override fun fling(velocityY: Int) {
        // Intentionally empty: the prompter owns the scroll position.
    }
}
