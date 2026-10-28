package com.kimrosil.cuescroll

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.Choreographer
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Full-screen teleprompter. A Choreographer loop owns the scroll position as a float so slow speeds
 * stay smooth: the integer part drives scrollTo(), the fraction becomes a child translation.
 */
class PrompterActivity : android.app.Activity(), Choreographer.FrameCallback {

    private enum class Phase { STANDBY, COUNTDOWN, LIVE, PAUSED, END }

    private companion object {
        const val READ_LINE_FRACTION = 0.36f
        const val CONTROLS_HIDE_MS = 3500L
        const val SPEED_STEP = 2
        const val SIZE_STEP = 2
        const val UI_INTERVAL_NANOS = 150_000_000L
    }

    private lateinit var db: ScriptDb
    private lateinit var prefs: android.content.SharedPreferences

    private lateinit var stageRoot: FrameLayout
    private lateinit var mirrorLayer: View
    private lateinit var scroll: PrompterScrollView
    private lateinit var tvScript: TextView
    private lateinit var readBand: View
    private lateinit var markerLeft: ImageView
    private lateinit var markerRight: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var topBar: View
    private lateinit var controlPanel: View
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvWpm: TextView
    private lateinit var tvRemain: TextView
    private lateinit var tvElapsed: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvSize: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var btnPlay: TextView
    private lateinit var btnMirror: ImageButton
    private lateinit var btnColor: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { setControlsVisible(false) }
    private val gestures by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                togglePlay()
                return true
            }
        }).apply { setIsLongpressEnabled(false) }
    }

    private var scriptId = 0L
    private var script: Script? = null
    private var wordCount = 0

    private var phase = Phase.STANDBY
    private var pos = 0f
    private var elapsedSec = 0.0
    private var touching = false
    private var frameRunning = false
    private var lastFrameNanos = 0L
    private var lastUiNanos = 0L

    private var speed = CuePrefs.SPEED_DEFAULT
    private var textSizeSp = CuePrefs.SIZE_DEFAULT
    private var mirrored = false
    private var colorIndex = 0
    private var countdownSec = 3
    private var countdownLeft = 0

    private var topPad = 0
    private var bottomPad = 0
    private var pendingFraction: Float? = null
    private var controlsShown = true
    private var repeating = false
    private var insetLeft = 0
    private var insetTop = 0
    private var insetRight = 0
    private var insetBottom = 0

    private val countdownTick = object : Runnable {
        override fun run() {
            countdownLeft--
            if (countdownLeft <= 0) {
                tvCountdown.visibility = View.GONE
                setPhase(Phase.LIVE)
            } else {
                tvCountdown.text = countdownLeft.toString()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(CuePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_prompter)

        db = ScriptDb(this)
        prefs = CuePrefs.of(this)
        scriptId = intent.getLongExtra(EditorActivity.EXTRA_ID, 0L)

        speed = prefs.getInt(CuePrefs.KEY_SPEED, CuePrefs.SPEED_DEFAULT)
            .coerceIn(CuePrefs.SPEED_MIN, CuePrefs.SPEED_MAX)
        textSizeSp = prefs.getInt(CuePrefs.KEY_SIZE, CuePrefs.SIZE_DEFAULT)
            .coerceIn(CuePrefs.SIZE_MIN, CuePrefs.SIZE_MAX)
        mirrored = prefs.getBoolean(CuePrefs.KEY_MIRROR, false)
        colorIndex = prefs.getInt(CuePrefs.KEY_COLOR, 0).coerceIn(0, 2)
        countdownSec = prefs.getInt(CuePrefs.KEY_COUNTDOWN, 3)

        bindViews()
        bindControls()
        applyTextStyle()
        applyMirror()
        applyColor()
        updateSpeedLabels()
        setPhase(Phase.STANDBY)
    }

    private fun bindViews() {
        stageRoot = findViewById(R.id.stageRoot)
        mirrorLayer = findViewById(R.id.mirrorLayer)
        scroll = findViewById(R.id.scroll)
        tvScript = findViewById(R.id.tvScript)
        readBand = findViewById(R.id.readBand)
        markerLeft = findViewById(R.id.markerLeft)
        markerRight = findViewById(R.id.markerRight)
        progress = findViewById(R.id.progress)
        topBar = findViewById(R.id.topBar)
        controlPanel = findViewById(R.id.controlPanel)
        tvTitle = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)
        tvWpm = findViewById(R.id.tvWpm)
        tvRemain = findViewById(R.id.tvRemain)
        tvElapsed = findViewById(R.id.tvElapsed)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvSize = findViewById(R.id.tvSize)
        tvCountdown = findViewById(R.id.tvCountdown)
        btnPlay = findViewById(R.id.btnPlay)
        btnMirror = findViewById(R.id.btnMirror)
        btnColor = findViewById(R.id.btnColor)
    }

    private fun bindControls() {
        scroll.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, orr, ob ->
            if (r - l != orr - ol || b - t != ob - ot) recomputeStageLayout()
        }
        tvScript.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) onScriptResized()
        }
        scroll.setOnTouchListener { _, ev ->
            gestures.onTouchEvent(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touching = true
                    showControls()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touching = false
                    pos = scroll.scrollY.toFloat()
                    if (phase == Phase.END && pos < maxScroll() - 2) setPhase(Phase.PAUSED)
                }
            }
            false
        }

        stageRoot.setOnApplyWindowInsetsListener { v, insets ->
            val cut = insets.displayCutout
            insetLeft = cut?.safeInsetLeft ?: 0
            insetTop = cut?.safeInsetTop ?: 0
            insetRight = cut?.safeInsetRight ?: 0
            insetBottom = cut?.safeInsetBottom ?: 0
            applyInsetMargins()
            v.onApplyWindowInsets(insets)
            insets
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnEdit).setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_ID, scriptId))
        }
        btnPlay.setOnClickListener { togglePlay() }
        findViewById<View>(R.id.btnRestart).setOnClickListener { restart(); pokeControls() }
        btnMirror.setOnClickListener {
            mirrored = !mirrored
            prefs.edit().putBoolean(CuePrefs.KEY_MIRROR, mirrored).apply()
            applyMirror()
            pokeControls()
        }
        btnColor.setOnClickListener {
            colorIndex = (colorIndex + 1) % 3
            prefs.edit().putInt(CuePrefs.KEY_COLOR, colorIndex).apply()
            applyColor()
            pokeControls()
        }

        bindStepper(findViewById(R.id.btnSpeedDown)) { changeSpeed(-SPEED_STEP) }
        bindStepper(findViewById(R.id.btnSpeedUp)) { changeSpeed(SPEED_STEP) }
        bindStepper(findViewById(R.id.btnSizeDown)) { changeSize(-SIZE_STEP) }
        bindStepper(findViewById(R.id.btnSizeUp)) { changeSize(SIZE_STEP) }
    }

    /** Single tap steps once; holding the key repeats after a short delay. */
    private fun bindStepper(v: View, action: () -> Unit) {
        val repeat = object : Runnable {
            override fun run() {
                repeating = true
                action()
                handler.postDelayed(this, 70)
            }
        }
        v.setOnClickListener {
            if (!repeating) action()
            pokeControls()
        }
        v.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeating = false
                    handler.postDelayed(repeat, 380)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(repeat)
            }
            false
        }
    }

    // ----- lifecycle -------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (!loadScript()) {
            finish()
            return
        }
        frameRunning = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onPause() {
        super.onPause()
        frameRunning = false
        Choreographer.getInstance().removeFrameCallback(this)
        handler.removeCallbacks(hideControlsRunnable)
        when (phase) {
            Phase.LIVE -> setPhase(Phase.PAUSED)
            Phase.COUNTDOWN -> cancelCountdown()
            else -> {}
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        pendingFraction = fraction()
        super.onConfigurationChanged(newConfig)
    }

    private fun loadScript(): Boolean {
        val s = db.get(scriptId) ?: return false
        val changed = script?.body != s.body
        script = s
        tvTitle.text = s.title
        wordCount = s.words
        if (changed) {
            if (tvScript.text.isNotEmpty()) pendingFraction = fraction()
            tvScript.text = s.body
        }
        return true
    }

    // ----- layout & scrolling ----------------------------------------------------------------

    private fun recomputeStageLayout() {
        val h = scroll.height
        val w = scroll.width
        if (h == 0 || w == 0) return
        val readY = (h * READ_LINE_FRACTION).toInt()
        val lineH = tvScript.lineHeight
        topPad = (readY - lineH / 2).coerceAtLeast(0)
        bottomPad = (h - readY - lineH / 2).coerceAtLeast(0)
        val hPad = UiKit.dp(this, 26) + (w * 0.03f).toInt()
        tvScript.setPadding(hPad, topPad, hPad, bottomPad)

        (readBand.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = topPad
            height = lineH
        }
        readBand.requestLayout()
        val markerTop = readY - UiKit.dp(this, 13)
        (markerLeft.layoutParams as FrameLayout.LayoutParams).topMargin = markerTop
        (markerRight.layoutParams as FrameLayout.LayoutParams).topMargin = markerTop
        markerLeft.requestLayout()
        markerRight.requestLayout()

        val panelWidth = UiKit.dp(this, 560)
        val lp = controlPanel.layoutParams as FrameLayout.LayoutParams
        lp.width = if (w > panelWidth + UiKit.dp(this, 20)) panelWidth else ViewGroup.LayoutParams.MATCH_PARENT
        controlPanel.requestLayout()
        applyInsetMargins()
    }

    private fun applyInsetMargins() {
        val m = UiKit.dp(this, 10)
        (topBar.layoutParams as FrameLayout.LayoutParams).apply {
            setMargins(m + insetLeft, m + insetTop, m + insetRight, m)
        }
        (controlPanel.layoutParams as FrameLayout.LayoutParams).apply {
            setMargins(m + insetLeft, m, m + insetRight, m + insetBottom)
        }
        topBar.requestLayout()
        controlPanel.requestLayout()
    }

    private fun onScriptResized() {
        val frac = pendingFraction
        if (frac != null) {
            pendingFraction = null
            pos = frac * maxScroll()
        }
        pos = pos.coerceIn(0f, maxScroll().toFloat())
        applyScroll()
        updateTelemetry()
    }

    private fun maxScroll(): Int = (tvScript.height - scroll.height).coerceAtLeast(0)

    private fun textHeight(): Int = (tvScript.height - topPad - bottomPad).coerceAtLeast(0)

    private fun fraction(): Float {
        val m = maxScroll()
        return if (m <= 0) 0f else (pos / m).coerceIn(0f, 1f)
    }

    private fun speedPx(): Float = speed * resources.displayMetrics.density

    private fun applyScroll() {
        val whole = floor(pos).toInt()
        scroll.scrollTo(0, whole)
        tvScript.translationY = -(pos - whole)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!frameRunning) return
        val dt = if (lastFrameNanos == 0L) 0.0 else minOf((frameTimeNanos - lastFrameNanos) / 1e9, 0.05)
        lastFrameNanos = frameTimeNanos
        val max = maxScroll().toFloat()
        if (touching) {
            pos = scroll.scrollY.toFloat()
        } else if (phase == Phase.LIVE) {
            pos += speedPx() * dt.toFloat()
            elapsedSec += dt
            if (pos >= max) {
                pos = max
                setPhase(Phase.END)
            }
            applyScroll()
        }
        if (frameTimeNanos - lastUiNanos > UI_INTERVAL_NANOS) {
            lastUiNanos = frameTimeNanos
            updateTelemetry()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun updateTelemetry() {
        val max = maxScroll()
        val textH = textHeight()
        val pxPerSec = speedPx()
        val wpm = if (textH > 0) (pxPerSec / textH * wordCount * 60f).roundToInt() else 0
        val remain = if (pxPerSec > 0f) ((max - pos) / pxPerSec).toInt() else 0
        tvWpm.text = wpm.toString()
        tvRemain.text = TextStats.clock(remain)
        tvElapsed.text = TextStats.clock(elapsedSec.toInt())
        progress.progress = if (max > 0) (pos / max * 1000f).toInt().coerceIn(0, 1000) else 0
    }

    // ----- transport -------------------------------------------------------------------------

    private fun togglePlay() {
        when (phase) {
            Phase.STANDBY, Phase.PAUSED ->
                if (pos < 2f && countdownSec > 0) startCountdown() else setPhase(Phase.LIVE)
            Phase.COUNTDOWN -> cancelCountdown()
            Phase.LIVE -> setPhase(Phase.PAUSED)
            Phase.END -> restart()
        }
    }

    private fun restart() {
        cancelCountdown()
        pos = 0f
        elapsedSec = 0.0
        applyScroll()
        setPhase(Phase.STANDBY)
        updateTelemetry()
    }

    private fun startCountdown() {
        countdownLeft = countdownSec
        tvCountdown.text = countdownLeft.toString()
        tvCountdown.visibility = View.VISIBLE
        setPhase(Phase.COUNTDOWN)
        handler.removeCallbacks(countdownTick)
        handler.postDelayed(countdownTick, 1000)
    }

    private fun cancelCountdown() {
        handler.removeCallbacks(countdownTick)
        tvCountdown.visibility = View.GONE
        if (phase == Phase.COUNTDOWN) setPhase(Phase.STANDBY)
    }

    private fun jump(dy: Float) {
        pos = (pos + dy).coerceIn(0f, maxScroll().toFloat())
        applyScroll()
        if (phase == Phase.END && pos < maxScroll() - 2) setPhase(Phase.PAUSED)
        updateTelemetry()
    }

    private fun setPhase(next: Phase) {
        phase = next
        val label: Int
        val dotColor: Int
        when (next) {
            Phase.STANDBY -> { label = R.string.ready; dotColor = R.color.stage_label }
            Phase.COUNTDOWN -> { label = R.string.cue; dotColor = R.color.stage_amber }
            Phase.LIVE -> { label = R.string.live; dotColor = R.color.stage_text }
            Phase.PAUSED -> { label = R.string.paused; dotColor = R.color.stage_amber }
            Phase.END -> { label = R.string.end_of_script; dotColor = R.color.stage_green }
        }
        tvStatus.setText(label)
        tvStatus.setBackgroundResource(if (next == Phase.LIVE) R.drawable.badge_live else R.drawable.badge_idle)
        tvStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_tally_dot, 0, 0, 0)
        tvStatus.compoundDrawableTintList = ColorStateList.valueOf(getColor(dotColor))

        val playLabel: Int
        val playIcon: Int
        when (next) {
            Phase.LIVE -> { playLabel = R.string.pause; playIcon = R.drawable.ic_pause }
            Phase.COUNTDOWN -> { playLabel = R.string.stop; playIcon = R.drawable.ic_pause }
            Phase.END -> { playLabel = R.string.restart; playIcon = R.drawable.ic_restart }
            Phase.PAUSED -> { playLabel = R.string.resume; playIcon = R.drawable.ic_play }
            Phase.STANDBY -> { playLabel = R.string.play; playIcon = R.drawable.ic_play }
        }
        btnPlay.setText(playLabel)
        btnPlay.setCompoundDrawablesRelativeWithIntrinsicBounds(playIcon, 0, 0, 0)
        btnPlay.compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.on_ruby))

        if (next == Phase.LIVE) showControls() else {
            handler.removeCallbacks(hideControlsRunnable)
            setControlsVisible(true)
        }
    }

    // ----- controls --------------------------------------------------------------------------

    private fun changeSpeed(delta: Int) {
        speed = (speed + delta).coerceIn(CuePrefs.SPEED_MIN, CuePrefs.SPEED_MAX)
        prefs.edit().putInt(CuePrefs.KEY_SPEED, speed).apply()
        updateSpeedLabels()
        updateTelemetry()
    }

    private fun changeSize(delta: Int) {
        val next = (textSizeSp + delta).coerceIn(CuePrefs.SIZE_MIN, CuePrefs.SIZE_MAX)
        if (next == textSizeSp) return
        pendingFraction = fraction()
        textSizeSp = next
        prefs.edit().putInt(CuePrefs.KEY_SIZE, textSizeSp).apply()
        applyTextStyle()
        recomputeStageLayout()
    }

    private fun updateSpeedLabels() {
        tvSpeed.text = speed.toString()
        tvSize.text = textSizeSp.toString()
    }

    private fun applyTextStyle() {
        tvScript.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
        updateSpeedLabels()
    }

    private fun applyMirror() {
        mirrorLayer.scaleX = if (mirrored) -1f else 1f
        btnMirror.setBackgroundResource(if (mirrored) R.drawable.btn_stage_key_on else R.drawable.btn_stage_key)
        btnMirror.imageTintList = ColorStateList.valueOf(getColor(if (mirrored) R.color.stage_amber else R.color.stage_text))
    }

    private fun applyColor() {
        val res = when (colorIndex) {
            1 -> R.color.stage_amber
            2 -> R.color.stage_green
            else -> R.color.stage_text
        }
        tvScript.setTextColor(getColor(res))
        btnColor.imageTintList = ColorStateList.valueOf(getColor(res))
    }

    private fun showControls() {
        handler.removeCallbacks(hideControlsRunnable)
        setControlsVisible(true)
        if (phase == Phase.LIVE) handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_MS)
    }

    private fun pokeControls() = showControls()

    private fun setControlsVisible(visible: Boolean) {
        if (visible == controlsShown && topBar.visibility == (if (visible) View.VISIBLE else View.GONE)) return
        controlsShown = visible
        for (v in listOf(topBar, controlPanel)) {
            v.animate().cancel()
            if (visible) {
                v.visibility = View.VISIBLE
                v.animate().alpha(1f).setDuration(160).start()
            } else {
                v.animate().alpha(0f).setDuration(220).withEndAction {
                    if (!controlsShown) v.visibility = View.GONE
                }.start()
            }
        }
    }

    // ----- hardware keys: volume = speed, clickers = play/pause --------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { changeSpeed(SPEED_STEP); pokeControls(); return true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { changeSpeed(-SPEED_STEP); pokeControls(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (event.repeatCount == 0 && phase != Phase.LIVE) togglePlay()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (event.repeatCount == 0 && phase == Phase.LIVE) togglePlay()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (event.repeatCount == 0) togglePlay()
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                jump(scroll.height * 0.7f); return true
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                jump(-scroll.height * 0.7f); return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> { jump(textSizeSp * resources.displayMetrics.scaledDensity); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { jump(-textSizeSp * resources.displayMetrics.scaledDensity); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ----- system bars -------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        window.statusBarColor = Color.TRANSPARENT
    }
}
