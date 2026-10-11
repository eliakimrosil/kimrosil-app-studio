package com.kimrosil.pebbledrift;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 60FPS Hardware-Accelerated Interactive Zen Canvas.
 * Supports:
 *  1. Ripple Oasis: Multi-touch fluid ripple waves, falling pebble splashes, drifting sakura petals & lilies.
 *  2. Stone Stack: Physics-based cairn stone balancing with stability calculations.
 *  3. Sand Garden: Meditative Karesan sand raking with tactile furrow shadows and clean-sweep wave animation.
 *  4. Zen Breathing Pacer: Concentric harmonic lotus breathing guide.
 *  5. Bioluminescent Night Mode: Drifting fireflies and neon water luminescence.
 */
public class PebbleZenCanvasView extends View {

    public enum ZenMode {
        RIPPLE_OASIS,
        STONE_STACK,
        SAND_GARDEN
    }

    public interface ZenListener {
        void onPebbleDropped(int totalPebbles);
        void onCairnBalanced(int stackHeight);
        void onCairnToppled();
        void onZenAphorismUnlocked();
    }

    private ZenMode currentMode = ZenMode.RIPPLE_OASIS;
    private ZenListener zenListener;
    private ZenChimeSynthesizer synthesizer;

    private boolean isNightMode = false;
    private boolean isRainEnabled = false;
    private boolean isBreathingEnabled = true;

    private final Random random = new Random();

    // Paints
    private Paint bgPaint;
    private Paint ripplePaint;
    private Paint petalPaint;
    private Paint petalStrokePaint;
    private Paint lilyPaint;
    private Paint lilyStrokePaint;
    private Paint stonePaint;
    private Paint stoneStrokePaint;
    private Paint sandPaint;
    private Paint rakeGroovePaint;
    private Paint rakeRidgePaint;
    private Paint breathPaint;
    private Paint breathTextPaint;
    private Paint fireflyPaint;

    // Simulation Data
    private final List<RippleRing> ripples = new ArrayList<>();
    private final List<FallingPebble> fallingPebbles = new ArrayList<>();
    private final List<FloatingPetal> floatingPetals = new ArrayList<>();
    private final List<FloatingLily> floatingLilies = new ArrayList<>();
    private final List<Firefly> fireflies = new ArrayList<>();
    private final List<RakeStroke> rakeStrokes = new ArrayList<>();

    // Cairn Stacking
    private final List<StackedStone> cairnStack = new ArrayList<>();
    private final List<CairnStoneType> stoneTray = new ArrayList<>();
    private DraggingStone draggingStone = null;
    private int totalCairnsBalanced = 0;
    private int totalPebblesDropped = 0;

    // Clean Sweep Animation
    private boolean isSweeping = false;
    private float sweepProgress = 0f;

    // Rain Simulation
    private long lastRainDropTime = 0;

    // Breathing Pacer (4-4-4 seconds = 12s cycle)
    private long breathStartTime = 0;

    // Timing
    private long lastFrameTime = 0;

    public PebbleZenCanvasView(Context context) {
        super(context);
        init();
    }

    public PebbleZenCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PebbleZenCanvasView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setFocusable(true);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ripplePaint.setStyle(Paint.Style.STROKE);

        petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        petalStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        petalStrokePaint.setStyle(Paint.Style.STROKE);

        lilyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lilyStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lilyStrokePaint.setStyle(Paint.Style.STROKE);

        stonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stoneStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stoneStrokePaint.setStyle(Paint.Style.STROKE);

        sandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rakeGroovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rakeGroovePaint.setStyle(Paint.Style.STROKE);
        rakeGroovePaint.setStrokeCap(Paint.Cap.ROUND);
        rakeGroovePaint.setStrokeJoin(Paint.Join.ROUND);

        rakeRidgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rakeRidgePaint.setStyle(Paint.Style.STROKE);
        rakeRidgePaint.setStrokeCap(Paint.Cap.ROUND);
        rakeRidgePaint.setStrokeJoin(Paint.Join.ROUND);

        breathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        breathPaint.setStyle(Paint.Style.STROKE);

        breathTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        breathTextPaint.setTextAlign(Paint.Align.CENTER);
        breathTextPaint.setTextSize(36f);

        fireflyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        breathStartTime = SystemClock.uptimeMillis();
        initStoneTray();
    }

    public void setSynthesizer(ZenChimeSynthesizer synth) {
        this.synthesizer = synth;
    }

    public void setZenListener(ZenListener listener) {
        this.zenListener = listener;
    }

    public void setNightMode(boolean night) {
        this.isNightMode = night;
        invalidate();
    }

    public void setMode(ZenMode mode) {
        this.currentMode = mode;
        invalidate();
    }

    public ZenMode getMode() {
        return currentMode;
    }

    public void toggleRain() {
        this.isRainEnabled = !this.isRainEnabled;
    }

    public boolean isRainEnabled() {
        return isRainEnabled;
    }

    public void toggleBreathing() {
        this.isBreathingEnabled = !this.isBreathingEnabled;
        invalidate();
    }

    public boolean isBreathingEnabled() {
        return isBreathingEnabled;
    }

    public void triggerCleanSweep() {
        isSweeping = true;
        sweepProgress = 0f;
        if (synthesizer != null) {
            synthesizer.playScaleStep(2);
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initFloatingElements(w, h);
    }

    private void initFloatingElements(int w, int h) {
        floatingPetals.clear();
        for (int i = 0; i < 14; i++) {
            floatingPetals.add(new FloatingPetal(
                    random.nextFloat() * w,
                    random.nextFloat() * h,
                    14f + random.nextFloat() * 10f,
                    random.nextFloat() * 360f
            ));
        }

        floatingLilies.clear();
        floatingLilies.add(new FloatingLily(w * 0.22f, h * 0.28f, 44f));
        floatingLilies.add(new FloatingLily(w * 0.80f, h * 0.38f, 52f));
        floatingLilies.add(new FloatingLily(w * 0.28f, h * 0.72f, 38f));

        fireflies.clear();
        for (int i = 0; i < 10; i++) {
            fireflies.add(new Firefly(random.nextFloat() * w, random.nextFloat() * h));
        }

        resetCairnStack(w, h);
    }

    private void initStoneTray() {
        stoneTray.clear();
        stoneTray.add(new CairnStoneType("Basalt Base", 170f, 52f, Color.parseColor("#334155"), Color.parseColor("#475569")));
        stoneTray.add(new CairnStoneType("River Slate", 140f, 44f, Color.parseColor("#475569"), Color.parseColor("#64748B")));
        stoneTray.add(new CairnStoneType("Granite Oval", 112f, 38f, Color.parseColor("#64748B"), Color.parseColor("#94A3B8")));
        stoneTray.add(new CairnStoneType("Jade River Gem", 84f, 32f, Color.parseColor("#10B981"), Color.parseColor("#34D399")));
    }

    public void resetCairnStack(int w, int h) {
        cairnStack.clear();
        float altarY = h * 0.65f;
        // Foundation altar stone
        cairnStack.add(new StackedStone(
                w * 0.5f,
                altarY,
                200f,
                58f,
                Color.parseColor("#1E293B"),
                Color.parseColor("#334155"),
                0f
        ));
    }

    public void dropPebbleAt(float x, float y) {
        fallingPebbles.add(new FallingPebble(x, y));
        totalPebblesDropped++;
        if (zenListener != null) {
            zenListener.onPebbleDropped(totalPebblesDropped);
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public void addRipple(float x, float y, float maxRadius, float speed) {
        int color = isNightMode ? Color.parseColor("#38BDF8") : Color.parseColor("#06B6D4");
        ripples.add(new RippleRing(x, y, maxRadius, speed, color));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        if (currentMode == ZenMode.RIPPLE_OASIS) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    dropPebbleAt(x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    // Subtle trailing fluid ripples on drag
                    if (random.nextFloat() < 0.4f) {
                        addRipple(x, y, 70f + random.nextFloat() * 40f, 2.5f);
                    }
                    break;
            }
            return true;
        } else if (currentMode == ZenMode.STONE_STACK) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    handleStoneStackDown(x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (draggingStone != null) {
                        draggingStone.currentX = x;
                        draggingStone.currentY = y;
                        invalidate();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handleStoneStackUp(x, y);
                    break;
            }
            return true;
        } else if (currentMode == ZenMode.SAND_GARDEN) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    RakeStroke newStroke = new RakeStroke();
                    newStroke.addPoint(x, y);
                    rakeStrokes.add(newStroke);
                    if (synthesizer != null && random.nextFloat() < 0.25f) {
                        synthesizer.playRandomChime();
                    }
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!rakeStrokes.isEmpty()) {
                        rakeStrokes.get(rakeStrokes.size() - 1).addPoint(x, y);
                    }
                    break;
            }
            invalidate();
            return true;
        }

        return super.onTouchEvent(event);
    }

    private void handleStoneStackDown(float x, float y) {
        // Check if user tapped a stone in the bottom tray
        float trayY = getHeight() * 0.82f;
        float spacing = getWidth() / (float) (stoneTray.size() + 1);

        for (int i = 0; i < stoneTray.size(); i++) {
            float stoneCenterX = spacing * (i + 1);
            CairnStoneType st = stoneTray.get(i);
            RectF hitBox = new RectF(
                    stoneCenterX - st.width / 2f - 20f,
                    trayY - st.height / 2f - 20f,
                    stoneCenterX + st.width / 2f + 20f,
                    trayY + st.height / 2f + 20f
            );

            if (hitBox.contains(x, y)) {
                draggingStone = new DraggingStone(st, x, y);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (synthesizer != null) {
                    synthesizer.playStoneTap();
                }
                invalidate();
                return;
            }
        }
    }

    private void handleStoneStackUp(float x, float y) {
        if (draggingStone == null) return;

        StackedStone topStone = cairnStack.get(cairnStack.size() - 1);
        float targetY = topStone.centerY - (topStone.height / 2f) - (draggingStone.type.height / 2f) + 6f;

        // Check if placed near stack top
        float deltaX = Math.abs(x - topStone.centerX);
        float deltaY = Math.abs(y - targetY);

        if (deltaY < 120f) {
            // Check stability tolerance (allow up to 45px offset)
            if (deltaX <= 46f) {
                // Balanced!
                float tilt = (x - topStone.centerX) * 0.12f;
                cairnStack.add(new StackedStone(
                        x,
                        targetY,
                        draggingStone.type.width,
                        draggingStone.type.height,
                        draggingStone.type.color,
                        draggingStone.type.strokeColor,
                        tilt
                ));
                totalCairnsBalanced++;

                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (synthesizer != null) {
                    synthesizer.playScaleStep(cairnStack.size() + 1);
                    synthesizer.playStoneTap();
                }

                if (zenListener != null) {
                    zenListener.onCairnBalanced(cairnStack.size() - 1);
                    if (cairnStack.size() % 3 == 0) {
                        zenListener.onZenAphorismUnlocked();
                    }
                }
            } else {
                // Toppled!
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (synthesizer != null) {
                    synthesizer.playStoneTap();
                }
                dropPebbleAt(x, y);
                if (zenListener != null) {
                    zenListener.onCairnToppled();
                }
            }
        }

        draggingStone = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        long now = SystemClock.uptimeMillis();
        float dt = (lastFrameTime == 0) ? 0.016f : (now - lastFrameTime) / 1000f;
        lastFrameTime = now;

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // 1. Draw Background
        drawZenBackground(canvas, w, h);

        // 2. Draw Mode Specific Layer
        if (currentMode == ZenMode.SAND_GARDEN) {
            drawSandGarden(canvas, w, h, dt);
        } else if (currentMode == ZenMode.STONE_STACK) {
            drawStoneStack(canvas, w, h);
        } else {
            drawRippleOasis(canvas, w, h, dt);
        }

        // 3. Draw Clean Sweep Wave if active
        if (isSweeping) {
            drawSweepWave(canvas, w, h, dt);
        }

        // 4. Draw Breathing Guide if enabled
        if (isBreathingEnabled) {
            drawBreathingGuide(canvas, w, h, now);
        }

        // 5. Draw Night Fireflies
        if (isNightMode) {
            drawFireflies(canvas, w, h, dt);
        }

        // Handle Ambient Rain
        if (isRainEnabled && now - lastRainDropTime > 340) {
            lastRainDropTime = now;
            float rx = random.nextFloat() * w;
            float ry = random.nextFloat() * h;
            addRipple(rx, ry, 60f + random.nextFloat() * 40f, 2.0f);
            if (synthesizer != null && random.nextFloat() < 0.4f) {
                synthesizer.playWaterDrop();
            }
        }

        postInvalidateOnAnimation();
    }

    private void drawZenBackground(Canvas canvas, int w, int h) {
        if (currentMode == ZenMode.SAND_GARDEN) {
            int sandBase = isNightMode ? Color.parseColor("#131C24") : Color.parseColor("#F1EADC");
            sandPaint.setColor(sandBase);
            canvas.drawRect(0, 0, w, h, sandPaint);
        } else {
            int waterBase = isNightMode ? Color.parseColor("#081318") : Color.parseColor("#EBF5F6");
            int waterShimmer = isNightMode ? Color.parseColor("#0D1F27") : Color.parseColor("#DDF0F2");

            RadialGradient grad = new RadialGradient(
                    w * 0.5f, h * 0.45f, Math.max(w, h) * 0.75f,
                    waterShimmer, waterBase, Shader.TileMode.CLAMP
            );
            bgPaint.setShader(grad);
            canvas.drawRect(0, 0, w, h, bgPaint);
            bgPaint.setShader(null);
        }
    }

    private void drawRippleOasis(Canvas canvas, int w, int h, float dt) {
        // Draw Falling Pebbles
        Iterator<FallingPebble> pIter = fallingPebbles.iterator();
        while (pIter.hasNext()) {
            FallingPebble p = pIter.next();
            p.update(dt);
            if (p.isLanded) {
                addRipple(p.x, p.y, 160f, 4.0f);
                addRipple(p.x, p.y, 220f, 2.8f);
                if (synthesizer != null) {
                    synthesizer.playWaterDrop();
                    synthesizer.playRandomChime();
                }
                pIter.remove();
            } else {
                p.draw(canvas, stonePaint, stoneStrokePaint);
            }
        }

        // Draw Ripples
        Iterator<RippleRing> rIter = ripples.iterator();
        while (rIter.hasNext()) {
            RippleRing r = rIter.next();
            r.update(dt);
            if (r.alpha <= 0.01f || r.radius >= r.maxRadius) {
                rIter.remove();
            } else {
                r.draw(canvas, ripplePaint);
                // Push nearby petals
                for (FloatingPetal petal : floatingPetals) {
                    float dist = (float) Math.hypot(petal.x - r.x, petal.y - r.y);
                    if (Math.abs(dist - r.radius) < 20f) {
                        float pushAngle = (float) Math.atan2(petal.y - r.y, petal.x - r.x);
                        petal.x += Math.cos(pushAngle) * 2.2f;
                        petal.y += Math.sin(pushAngle) * 2.2f;
                        petal.rotation += 1.5f;
                    }
                }
            }
        }

        // Draw Floating Water Lilies
        for (FloatingLily lily : floatingLilies) {
            lily.update(dt);
            lily.draw(canvas, lilyPaint, lilyStrokePaint, isNightMode);
        }

        // Draw Floating Sakura Petals
        for (FloatingPetal petal : floatingPetals) {
            petal.update(dt, w, h);
            petal.draw(canvas, petalPaint, petalStrokePaint, isNightMode);
        }
    }

    private void drawStoneStack(Canvas canvas, int w, int h) {
        // Draw stacked cairn stones
        for (StackedStone s : cairnStack) {
            s.draw(canvas, stonePaint, stoneStrokePaint);
        }

        // Draw Stone Tray at bottom
        float trayY = h * 0.82f;
        float spacing = w / (float) (stoneTray.size() + 1);

        for (int i = 0; i < stoneTray.size(); i++) {
            float cx = spacing * (i + 1);
            CairnStoneType st = stoneTray.get(i);
            stonePaint.setColor(st.color);
            stoneStrokePaint.setColor(st.strokeColor);
            stoneStrokePaint.setStrokeWidth(2f);

            RectF rect = new RectF(cx - st.width / 2f, trayY - st.height / 2f, cx + st.width / 2f, trayY + st.height / 2f);
            canvas.drawRoundRect(rect, st.height / 2f, st.height / 2f, stonePaint);
            canvas.drawRoundRect(rect, st.height / 2f, st.height / 2f, stoneStrokePaint);
        }

        // Draw dragging stone if active
        if (draggingStone != null) {
            stonePaint.setColor(draggingStone.type.color);
            stoneStrokePaint.setColor(draggingStone.type.strokeColor);
            stoneStrokePaint.setStrokeWidth(3f);

            float cx = draggingStone.currentX;
            float cy = draggingStone.currentY;
            RectF rect = new RectF(
                    cx - draggingStone.type.width / 2f,
                    cy - draggingStone.type.height / 2f,
                    cx + draggingStone.type.width / 2f,
                    cy + draggingStone.type.height / 2f
            );
            canvas.drawRoundRect(rect, draggingStone.type.height / 2f, draggingStone.type.height / 2f, stonePaint);
            canvas.drawRoundRect(rect, draggingStone.type.height / 2f, draggingStone.type.height / 2f, stoneStrokePaint);
        }
    }

    private void drawSandGarden(Canvas canvas, int w, int h, float dt) {
        int grooveColor = isNightMode ? Color.parseColor("#0A1116") : Color.parseColor("#D9CEBA");
        int ridgeColor = isNightMode ? Color.parseColor("#253744") : Color.parseColor("#FAF6EE");

        rakeGroovePaint.setColor(grooveColor);
        rakeGroovePaint.setStrokeWidth(32f);

        rakeRidgePaint.setColor(ridgeColor);
        rakeRidgePaint.setStrokeWidth(3.5f);

        for (RakeStroke stroke : rakeStrokes) {
            stroke.draw(canvas, rakeGroovePaint, rakeRidgePaint);
        }

        // Decorative Zen Garden Rocks
        drawGardenRock(canvas, w * 0.2f, h * 0.25f, 54f, Color.parseColor("#475569"));
        drawGardenRock(canvas, w * 0.78f, h * 0.70f, 68f, Color.parseColor("#334155"));
        drawGardenRock(canvas, w * 0.82f, h * 0.22f, 40f, Color.parseColor("#64748B"));
    }

    private void drawGardenRock(Canvas canvas, float cx, float cy, float radius, int color) {
        stonePaint.setColor(color);
        stoneStrokePaint.setColor(isNightMode ? Color.parseColor("#64748B") : Color.parseColor("#94A3B8"));
        stoneStrokePaint.setStrokeWidth(2f);

        RectF r = new RectF(cx - radius, cy - radius * 0.7f, cx + radius, cy + radius * 0.7f);
        canvas.drawOval(r, stonePaint);
        canvas.drawOval(r, stoneStrokePaint);

        // Moss rim accent
        Paint mossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mossPaint.setColor(Color.parseColor("#15803D"));
        mossPaint.setStyle(Paint.Style.STROKE);
        mossPaint.setStrokeWidth(3f);
        RectF mossRect = new RectF(cx - radius * 0.9f, cy - radius * 0.6f, cx + radius * 0.9f, cy + radius * 0.6f);
        canvas.drawArc(mossRect, 180, 180, false, mossPaint);
    }

    private void drawSweepWave(Canvas canvas, int w, int h, float dt) {
        sweepProgress += dt * 1.4f;
        if (sweepProgress >= 1f) {
            isSweeping = false;
            rakeStrokes.clear();
            sweepProgress = 0f;
            return;
        }

        Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sweepPaint.setColor(isNightMode ? Color.parseColor("#22D3EE") : Color.parseColor("#99E6EF"));
        sweepPaint.setAlpha((int) ((1f - sweepProgress) * 140));

        float waveX = w * sweepProgress;
        canvas.drawRect(0, 0, waveX, h, sweepPaint);
    }

    private void drawBreathingGuide(Canvas canvas, int w, int h, long now) {
        long elapsed = (now - breathStartTime) % 12000; // 12-second cycle
        float radiusBase = 46f;
        float radiusMax = 82f;
        float currentRadius;
        String phaseText;

        if (elapsed < 4000) {
            // Inhale (0 - 4s)
            float frac = elapsed / 4000f;
            currentRadius = radiusBase + (radiusMax - radiusBase) * frac;
            phaseText = "Inhale...";
        } else if (elapsed < 8000) {
            // Hold (4 - 8s)
            currentRadius = radiusMax;
            phaseText = "Hold...";
        } else {
            // Exhale (8 - 12s)
            float frac = (elapsed - 8000) / 4000f;
            currentRadius = radiusMax - (radiusMax - radiusBase) * frac;
            phaseText = "Exhale...";
        }

        float bx = w * 0.5f;
        float by = h * 0.92f;

        breathPaint.setColor(isNightMode ? Color.parseColor("#38BDF8") : Color.parseColor("#028090"));
        breathPaint.setStrokeWidth(3.5f);
        breathPaint.setAlpha(160);

        canvas.drawCircle(bx, by, currentRadius, breathPaint);

        breathTextPaint.setColor(isNightMode ? Color.parseColor("#E0F2FE") : Color.parseColor("#134E4A"));
        canvas.drawText(phaseText, bx, by + 12f, breathTextPaint);
    }

    private void drawFireflies(Canvas canvas, int w, int h, float dt) {
        for (Firefly f : fireflies) {
            f.update(dt, w, h);
            f.draw(canvas, fireflyPaint);
        }
    }

    // --- Sub-Entities ---

    private static class RippleRing {
        float x, y;
        float radius = 4f;
        float maxRadius;
        float speed;
        float alpha = 1.0f;
        int color;

        RippleRing(float x, float y, float maxRadius, float speed, int color) {
            this.x = x;
            this.y = y;
            this.maxRadius = maxRadius;
            this.speed = speed;
            this.color = color;
        }

        void update(float dt) {
            radius += speed * (dt * 60f);
            alpha = Math.max(0f, 1.0f - (radius / maxRadius));
        }

        void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setAlpha((int) (alpha * 220));
            paint.setStrokeWidth(Math.max(1.2f, 3.2f * alpha));
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private static class FallingPebble {
        float x, y;
        float altitude = 160f;
        boolean isLanded = false;

        FallingPebble(float x, float y) {
            this.x = x;
            this.y = y;
        }

        void update(float dt) {
            altitude -= dt * 640f;
            if (altitude <= 0f) {
                isLanded = true;
            }
        }

        void draw(Canvas canvas, Paint paint, Paint stroke) {
            float size = 18f + (altitude * 0.12f);
            paint.setColor(Color.parseColor("#475569"));
            stroke.setColor(Color.parseColor("#64748B"));
            stroke.setStrokeWidth(2f);

            canvas.drawCircle(x, y - altitude, size, paint);
            canvas.drawCircle(x, y - altitude, size, stroke);
        }
    }

    private static class FloatingPetal {
        float x, y, size, rotation;
        float vx = 0.3f;
        float vy = 0.2f;

        FloatingPetal(float x, float y, float size, float rotation) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.rotation = rotation;
        }

        void update(float dt, int w, int h) {
            x += vx * (dt * 60f);
            y += vy * (dt * 60f);
            rotation += 0.15f;

            if (x > w + 40) x = -30;
            if (y > h + 40) y = -30;
        }

        void draw(Canvas canvas, Paint paint, Paint stroke, boolean night) {
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(rotation);

            paint.setColor(night ? Color.parseColor("#F472B6") : Color.parseColor("#FBCFE8"));
            stroke.setColor(Color.parseColor("#EC4899"));
            stroke.setStrokeWidth(1.2f);

            Path path = new Path();
            path.moveTo(0, -size);
            path.cubicTo(size * 0.8f, -size * 0.5f, size * 0.8f, size * 0.5f, 0, size);
            path.cubicTo(-size * 0.8f, size * 0.5f, -size * 0.8f, -size * 0.5f, 0, -size);

            canvas.drawPath(path, paint);
            canvas.drawPath(path, stroke);
            canvas.restore();
        }
    }

    private static class FloatingLily {
        float x, y, radius;

        FloatingLily(float x, float y, float radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }

        void update(float dt) {}

        void draw(Canvas canvas, Paint paint, Paint stroke, boolean night) {
            paint.setColor(night ? Color.parseColor("#065F46") : Color.parseColor("#A7F3D0"));
            stroke.setColor(Color.parseColor("#047857"));
            stroke.setStrokeWidth(2.2f);

            RectF rect = new RectF(x - radius, y - radius, x + radius, y + radius);
            // Draw lily pad with authentic cut-out notch
            canvas.drawArc(rect, 40, 310, true, paint);
            canvas.drawArc(rect, 40, 310, true, stroke);

            // Blossom in the center
            Paint flower = new Paint(Paint.ANTI_ALIAS_FLAG);
            flower.setColor(Color.parseColor("#FFFFFF"));
            canvas.drawCircle(x, y, radius * 0.35f, flower);
            flower.setColor(Color.parseColor("#F472B6"));
            canvas.drawCircle(x, y, radius * 0.22f, flower);
            flower.setColor(Color.parseColor("#FBBF24"));
            canvas.drawCircle(x, y, radius * 0.10f, flower);
        }
    }

    private static class Firefly {
        float x, y;
        float phase;

        Firefly(float x, float y) {
            this.x = x;
            this.y = y;
            this.phase = (float) (Math.random() * Math.PI * 2);
        }

        void update(float dt, int w, int h) {
            phase += dt * 2.2f;
            x += (float) Math.sin(phase * 0.6f) * 0.8f;
            y += (float) Math.cos(phase * 0.4f) * 0.6f;

            if (x < 0) x = w;
            if (x > w) x = 0;
            if (y < 0) y = h;
            if (y > h) y = 0;
        }

        void draw(Canvas canvas, Paint paint) {
            float glow = (float) (0.5f + 0.5f * Math.sin(phase));
            paint.setColor(Color.parseColor("#FDE047"));
            paint.setAlpha((int) (glow * 220));
            canvas.drawCircle(x, y, 4f + glow * 3f, paint);
        }
    }

    private static class StackedStone {
        float centerX, centerY, width, height, tilt;
        int color, strokeColor;

        StackedStone(float cx, float cy, float w, float h, int c, int sc, float t) {
            this.centerX = cx;
            this.centerY = cy;
            this.width = w;
            this.height = h;
            this.color = c;
            this.strokeColor = sc;
            this.tilt = t;
        }

        void draw(Canvas canvas, Paint paint, Paint stroke) {
            canvas.save();
            canvas.translate(centerX, centerY);
            canvas.rotate(tilt);

            paint.setColor(color);
            stroke.setColor(strokeColor);
            stroke.setStrokeWidth(2.5f);

            RectF rect = new RectF(-width / 2f, -height / 2f, width / 2f, height / 2f);
            canvas.drawRoundRect(rect, height / 2f, height / 2f, paint);
            canvas.drawRoundRect(rect, height / 2f, height / 2f, stroke);

            canvas.restore();
        }
    }

    private static class CairnStoneType {
        String name;
        float width, height;
        int color, strokeColor;

        CairnStoneType(String n, float w, float h, int c, int sc) {
            this.name = n;
            this.width = w;
            this.height = h;
            this.color = c;
            this.strokeColor = sc;
        }
    }

    private static class DraggingStone {
        CairnStoneType type;
        float currentX, currentY;

        DraggingStone(CairnStoneType type, float x, float y) {
            this.type = type;
            this.currentX = x;
            this.currentY = y;
        }
    }

    private static class RakeStroke {
        private final List<PointF> points = new ArrayList<>();

        void addPoint(float x, float y) {
            points.add(new PointF(x, y));
        }

        void draw(Canvas canvas, Paint groove, Paint ridge) {
            if (points.size() < 2) return;

            Path mainPath = new Path();
            mainPath.moveTo(points.get(0).x, points.get(0).y);
            for (int i = 1; i < points.size(); i++) {
                mainPath.lineTo(points.get(i).x, points.get(i).y);
            }

            canvas.drawPath(mainPath, groove);
            canvas.drawPath(mainPath, ridge);
        }
    }

    private static class PointF {
        float x, y;
        PointF(float x, float y) { this.x = x; this.y = y; }
    }
}
