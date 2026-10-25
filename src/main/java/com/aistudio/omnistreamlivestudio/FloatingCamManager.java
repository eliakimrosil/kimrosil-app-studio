package com.aistudio.omnistreamlivestudio;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;

public class FloatingCamManager {

    private static final String TAG = "FloatingCamManager";

    public interface StateListener {
        void onFacecamToggled(boolean isShowing);
        void onCameraFlipped(boolean isFront);
        default void onCameraRotated(int rotationDegrees) {}
        default void onCameraDocked(boolean isDocked) {}
        default void onAspectRatioChanged(AspectRatio ratio) {}
        default void onRotationModeChanged(AutoRotateMode mode, int effectiveDegrees) {}
    }

    public enum AutoRotateMode {
        AUTO_GAME("Auto (Gaming)", "⟳A"),
        LOCK_0("0° Portrait", "0°"),
        LOCK_90("90° Landscape", "90°"),
        LOCK_180("180° Inverted", "180°"),
        LOCK_270("270° Rev. Land", "270°");

        public final String title;
        public final String badge;

        AutoRotateMode(String title, String badge) {
            this.title = title;
            this.badge = badge;
        }
    }

    public enum AspectRatio {
        PORTRAIT_3_4("3:4 Portrait", 3f / 4f, "▯"),
        LANDSCAPE_16_9("16:9 Widescreen", 16f / 9f, "▭"),
        SQUARE_1_1("1:1 Square", 1f, "□"),
        CIRCLE("Circular Mask", 1f, "◯");

        public final String title;
        public final float ratio; // width / height
        public final String icon;

        AspectRatio(String title, float ratio, String icon) {
            this.title = title;
            this.ratio = ratio;
            this.icon = icon;
        }
    }

    private static FloatingCamManager instance;

    private Context appContext;
    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams windowParams;

    // View Containers
    private View pipCameraContainer;
    private View pipBubbleContainer;
    private TextView pipBubbleBadge;

    // Camera Header Controls
    private View pipHeaderScroll;
    private View pipHeader;
    private TextView tvPipBadge;
    private TextView btnPipRotate;
    private TextView btnPipFlip;
    private TextView btnPipShape;
    private TextView btnPipDock;
    private TextView btnPipClose;
    private TextureView tvPipCamera;

    // Auto-Hiding Controls
    private static final long AUTO_HIDE_DELAY_MS = 3500;
    private final Runnable hideControlsRunnable = this::hideControls;
    private boolean areControlsVisible = true;

    // Aspect Ratio & Compact Gaming Sizing
    private AspectRatio currentAspectRatio = AspectRatio.PORTRAIT_3_4;
    private static final float DEFAULT_WIDTH_DP = 110f; // Compact gaming starting size
    private static final float MIN_WIDTH_DP = 64f;      // Ultra-compact corner thumbnail
    private static final float MAX_WIDTH_DP = 220f;     // Non-intrusive upper limit
    private float currentWidthDp = DEFAULT_WIDTH_DP;

    // Docked Edge Bubble State
    private boolean isDocked = false;
    private int savedWidthPx = 0;
    private int savedHeightPx = 0;

    // Camera2 State
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private boolean isFrontCamera = true;
    private int sensorOrientation = 0;
    private Size previewSize;
    private boolean isOverlayShowing = false;
    private boolean isLiveBroadcasting = false;

    // Camera Rotation & Auto-Rotate Engine for Live Gaming
    private AutoRotateMode autoRotateMode = AutoRotateMode.AUTO_GAME;
    private int currentRotationDegrees = 0;
    private DisplayManager displayManager;
    private DisplayManager.DisplayListener displayListener;
    private OrientationEventListener orientationEventListener;
    private int currentPhysicalQuadrant = 0; // 0, 90, 180, 270
    private int pendingPhysicalQuadrant = 0;
    private long pendingQuadrantTimestamp = 0;
    private static final long ORIENTATION_DEBOUNCE_MS = 350; // Stable anti-gyro recoil filter
    private int lastScreenWidth = 0;
    private int lastScreenHeight = 0;

    private ScaleGestureDetector scaleGestureDetector;
    private StateListener stateListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FloatingCamManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized FloatingCamManager getInstance(Context context) {
        if (instance == null) {
            instance = new FloatingCamManager(context);
        }
        return instance;
    }

    public void setStateListener(StateListener listener) {
        this.stateListener = listener;
    }

    public boolean isShowing() {
        return isOverlayShowing;
    }

    public boolean isDocked() {
        return isDocked;
    }

    public boolean isFrontCamera() {
        return isFrontCamera;
    }

    public AspectRatio getCurrentAspectRatio() {
        return currentAspectRatio;
    }

    public boolean isCircleShape() {
        return currentAspectRatio == AspectRatio.CIRCLE;
    }

    public float getCurrentWidthDp() {
        return currentWidthDp;
    }

    public void setLiveState(boolean isLive) {
        this.isLiveBroadcasting = isLive;
        mainHandler.post(() -> {
            if (overlayView != null) {
                if (pipCameraContainer != null) {
                    pipCameraContainer.setBackgroundResource(isLive ? R.drawable.bg_floating_cam_live : R.drawable.bg_floating_cam_standby);
                }
                if (pipBubbleContainer != null) {
                    pipBubbleContainer.setBackgroundResource(isLive ? R.drawable.bg_floating_cam_bubble_live : R.drawable.bg_floating_cam_bubble_standby);
                }
                if (tvPipBadge != null) {
                    tvPipBadge.setText(isLive ? "● LIVE" : "●");
                    tvPipBadge.setTextColor(isLive ? 0xFFEF4444 : 0xFF00E5FF);
                }
                if (pipBubbleBadge != null) {
                    pipBubbleBadge.setText(isLive ? "LIVE" : "CAM");
                    pipBubbleBadge.setTextColor(isLive ? 0xFFEF4444 : 0xFF00E5FF);
                }
            }
        });
    }

    public synchronized void showOverlay() {
        if (isOverlayShowing) return;

        if (!Settings.canDrawOverlays(appContext)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW not granted");
            return;
        }

        if (appContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(appContext, "Camera permission required for floating facecam", Toast.LENGTH_SHORT).show();
            return;
        }

        mainHandler.post(() -> {
            try {
                initOverlayView();
                windowManager.addView(overlayView, windowParams);
                isOverlayShowing = true;
                isDocked = false;
                setLiveState(isLiveBroadcasting);
                showControls();
                startOrientationMonitoring();

                startCameraThread();
                if (tvPipCamera.isAvailable()) {
                    openCamera(tvPipCamera.getSurfaceTexture(), tvPipCamera.getWidth(), tvPipCamera.getHeight());
                }

                if (stateListener != null) {
                    stateListener.onFacecamToggled(true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error displaying floating camera overlay", e);
            }
        });
    }

    public synchronized void hideOverlay() {
        if (!isOverlayShowing) return;

        mainHandler.post(() -> {
            mainHandler.removeCallbacks(hideControlsRunnable);
            stopOrientationMonitoring();
            closeCamera();
            stopCameraThread();

            if (overlayView != null && windowManager != null) {
                try {
                    windowManager.removeView(overlayView);
                } catch (Exception ignored) {}
                overlayView = null;
            }
            isOverlayShowing = false;
            isDocked = false;
            areControlsVisible = true;

            if (stateListener != null) {
                stateListener.onFacecamToggled(false);
            }
        });
    }

    public void flipCamera() {
        isFrontCamera = !isFrontCamera;
        if (isOverlayShowing && tvPipCamera != null && tvPipCamera.isAvailable()) {
            if (cameraHandler != null) {
                cameraHandler.post(() -> {
                    closeCamera();
                    try {
                        Thread.sleep(120);
                    } catch (InterruptedException ignored) {}
                    mainHandler.post(() -> {
                        if (isOverlayShowing && tvPipCamera != null && tvPipCamera.isAvailable()) {
                            openCamera(tvPipCamera.getSurfaceTexture(), tvPipCamera.getWidth(), tvPipCamera.getHeight());
                        }
                    });
                });
            }
        }
        if (stateListener != null) {
            mainHandler.post(() -> stateListener.onCameraFlipped(isFrontCamera));
        }
    }

    public void rotateCamera() {
        cycleAutoRotateMode();
    }

    public void cycleAutoRotateMode() {
        AutoRotateMode[] modes = AutoRotateMode.values();
        int nextIndex = (autoRotateMode.ordinal() + 1) % modes.length;
        setAutoRotateMode(modes[nextIndex]);
    }

    public void setAutoRotateMode(AutoRotateMode mode) {
        this.autoRotateMode = mode;
        currentRotationDegrees = getEffectiveRotationDegrees();
        updateRotateButtonUi();

        if (tvPipCamera != null && tvPipCamera.getWidth() > 0 && tvPipCamera.getHeight() > 0) {
            configureTransform(tvPipCamera.getWidth(), tvPipCamera.getHeight());
        }

        String msg;
        if (mode == AutoRotateMode.AUTO_GAME) {
            msg = "Facecam: AUTO-ROTATE (Gaming Active)";
        } else {
            msg = "Facecam: " + mode.title + " (Locked)";
        }
        Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show();

        if (stateListener != null) {
            stateListener.onCameraRotated(currentRotationDegrees);
            stateListener.onRotationModeChanged(autoRotateMode, currentRotationDegrees);
        }
    }

    public AutoRotateMode getAutoRotateMode() {
        return autoRotateMode;
    }

    public int getCameraRotationDegrees() {
        return getEffectiveRotationDegrees();
    }

    public int getEffectiveRotationDegrees() {
        if (autoRotateMode == AutoRotateMode.AUTO_GAME) {
            return computeAutoRotationDegrees();
        }
        switch (autoRotateMode) {
            case LOCK_90: return 90;
            case LOCK_180: return 180;
            case LOCK_270: return 270;
            case LOCK_0:
            default:
                return 0;
        }
    }

    public int computeAutoRotationDegrees() {
        int displayRotation = Surface.ROTATION_0;
        if (windowManager != null && windowManager.getDefaultDisplay() != null) {
            displayRotation = windowManager.getDefaultDisplay().getRotation();
        }

        int displayDegrees = 0;
        switch (displayRotation) {
            case Surface.ROTATION_90:  displayDegrees = 90; break;
            case Surface.ROTATION_180: displayDegrees = 180; break;
            case Surface.ROTATION_270: displayDegrees = 270; break;
            case Surface.ROTATION_0:
            default:
                displayDegrees = 0; break;
        }

        // If physical sensor detected an inverted landscape (e.g. gamer flipped phone 180° for charger clearance),
        // physical quadrant takes precedence between 90° and 270°
        if ((displayDegrees == 90 || displayDegrees == 270) &&
            (currentPhysicalQuadrant == 90 || currentPhysicalQuadrant == 270)) {
            return currentPhysicalQuadrant;
        }

        if (displayDegrees != 0) {
            return displayDegrees;
        }

        if (currentPhysicalQuadrant == 180) {
            return 180;
        }

        return displayDegrees;
    }

    private void startOrientationMonitoring() {
        if (displayManager == null) {
            displayManager = (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
        }
        if (displayManager != null && displayListener == null) {
            displayListener = new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {}

                @Override
                public void onDisplayRemoved(int displayId) {}

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        onOrientationOrDisplayChanged();
                    }
                }
            };
            displayManager.registerDisplayListener(displayListener, mainHandler);
        }

        if (orientationEventListener == null) {
            orientationEventListener = new OrientationEventListener(appContext, SensorManager.SENSOR_DELAY_UI) {
                @Override
                public void onOrientationChanged(int orientation) {
                    if (orientation == ORIENTATION_UNKNOWN) return;
                    handlePhysicalOrientation(orientation);
                }
            };
        }
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
    }

    private void stopOrientationMonitoring() {
        if (displayManager != null && displayListener != null) {
            try {
                displayManager.unregisterDisplayListener(displayListener);
            } catch (Exception ignored) {}
            displayListener = null;
        }
        if (orientationEventListener != null) {
            try {
                orientationEventListener.disable();
            } catch (Exception ignored) {}
            orientationEventListener = null;
        }
    }

    private void handlePhysicalOrientation(int rawDegrees) {
        // Hysteresis deadband filter (±15° deadband) to eliminate gyro-aiming recoil jitter
        int candidate = -1;
        if (rawDegrees >= 345 || rawDegrees <= 15) {
            candidate = 0;
        } else if (rawDegrees >= 75 && rawDegrees <= 105) {
            candidate = 270;
        } else if (rawDegrees >= 165 && rawDegrees <= 195) {
            candidate = 180;
        } else if (rawDegrees >= 255 && rawDegrees <= 285) {
            candidate = 90;
        }

        if (candidate == -1) return;

        long now = SystemClock.uptimeMillis();
        if (candidate != pendingPhysicalQuadrant) {
            pendingPhysicalQuadrant = candidate;
            pendingQuadrantTimestamp = now;
        } else if (now - pendingQuadrantTimestamp >= ORIENTATION_DEBOUNCE_MS) {
            if (currentPhysicalQuadrant != candidate) {
                currentPhysicalQuadrant = candidate;
                if (autoRotateMode == AutoRotateMode.AUTO_GAME) {
                    onOrientationOrDisplayChanged();
                }
            }
        }
    }

    private void onOrientationOrDisplayChanged() {
        if (!isOverlayShowing || overlayView == null) return;

        mainHandler.post(() -> {
            realignWindowForOrientation();
            int newEffective = getEffectiveRotationDegrees();
            if (newEffective != currentRotationDegrees) {
                currentRotationDegrees = newEffective;
                if (tvPipCamera != null && tvPipCamera.getWidth() > 0 && tvPipCamera.getHeight() > 0) {
                    configureTransform(tvPipCamera.getWidth(), tvPipCamera.getHeight());
                }
                updateRotateButtonUi();
                if (stateListener != null) {
                    stateListener.onCameraRotated(currentRotationDegrees);
                    stateListener.onRotationModeChanged(autoRotateMode, currentRotationDegrees);
                }
            }
        });
    }

    private void realignWindowForOrientation() {
        if (overlayView == null || windowParams == null || windowManager == null || !isOverlayShowing) return;

        DisplayMetrics metrics = getRealDisplayMetrics();
        int newScreenWidth = metrics.widthPixels;
        int newScreenHeight = metrics.heightPixels;

        if (lastScreenWidth > 0 && lastScreenHeight > 0 &&
            (lastScreenWidth != newScreenWidth || lastScreenHeight != newScreenHeight)) {

            float density = metrics.density;
            int marginPx = isDocked ? (int) (4 * density) : (int) (8 * density);

            // Maintain relative corner anchor across rotation transition
            boolean wasAnchoredRight = (windowParams.x + windowParams.width / 2) > (lastScreenWidth / 2);
            boolean wasAnchoredBottom = (windowParams.y + windowParams.height / 2) > (lastScreenHeight / 2);

            if (wasAnchoredRight) {
                windowParams.x = newScreenWidth - windowParams.width - marginPx;
            } else {
                windowParams.x = marginPx;
            }

            if (wasAnchoredBottom) {
                windowParams.y = newScreenHeight - windowParams.height - marginPx - (int) (30 * density);
            } else {
                windowParams.y = (int) (70 * density);
            }
        }

        lastScreenWidth = newScreenWidth;
        lastScreenHeight = newScreenHeight;

        clampPosition(windowParams);

        try {
            windowManager.updateViewLayout(overlayView, windowParams);
        } catch (Exception ignored) {}

        applyShapeOutline();
    }

    private void updateRotateButtonUi() {
        if (btnPipRotate == null) return;
        mainHandler.post(() -> {
            if (btnPipRotate != null) {
                if (autoRotateMode == AutoRotateMode.AUTO_GAME) {
                    btnPipRotate.setText("⟳A");
                    btnPipRotate.setTextColor(0xFF00E5FF); // Studio Cyan
                } else {
                    btnPipRotate.setText(autoRotateMode.badge);
                    btnPipRotate.setTextColor(0xFFFFFFFF);
                }
            }
        });
    }

    private DisplayMetrics getRealDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null && windowManager.getDefaultDisplay() != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics = appContext.getResources().getDisplayMetrics();
        }
        return metrics;
    }

    public void toggleShape() {
        cycleAspectRatio();
    }

    public void cycleAspectRatio() {
        if (isDocked) return;
        AspectRatio[] ratios = AspectRatio.values();
        int nextIndex = (currentAspectRatio.ordinal() + 1) % ratios.length;
        currentAspectRatio = ratios[nextIndex];

        if (btnPipShape != null) {
            btnPipShape.setText(currentAspectRatio.icon);
        }

        updateWindowDimensions();
        Toast.makeText(appContext, "Facecam: " + currentAspectRatio.title, Toast.LENGTH_SHORT).show();

        if (stateListener != null) {
            stateListener.onAspectRatioChanged(currentAspectRatio);
        }
        resetControlsTimer();
    }

    private void updateWindowDimensions() {
        if (!isOverlayShowing || overlayView == null || windowParams == null || windowManager == null || isDocked) return;

        float density = appContext.getResources().getDisplayMetrics().density;
        int widthPx = (int) (currentWidthDp * density);
        int heightPx;

        switch (currentAspectRatio) {
            case LANDSCAPE_16_9:
                heightPx = (int) (currentWidthDp / (16f / 9f) * density);
                break;
            case SQUARE_1_1:
            case CIRCLE:
                heightPx = widthPx;
                break;
            case PORTRAIT_3_4:
            default:
                heightPx = (int) (currentWidthDp * (4f / 3f) * density);
                break;
        }

        windowParams.width = widthPx;
        windowParams.height = heightPx;
        clampPosition(windowParams);

        try {
            windowManager.updateViewLayout(overlayView, windowParams);
        } catch (Exception ignored) {}

        overlayView.post(() -> {
            applyShapeOutline();
            if (tvPipCamera != null && tvPipCamera.getWidth() > 0 && tvPipCamera.getHeight() > 0) {
                configureTransform(tvPipCamera.getWidth(), tvPipCamera.getHeight());
            }
        });
    }

    public void dockToBubble() {
        if (!isOverlayShowing || overlayView == null || isDocked) return;
        isDocked = true;
        savedWidthPx = windowParams.width;
        savedHeightPx = windowParams.height;

        DisplayMetrics metrics = getRealDisplayMetrics();
        float density = metrics.density;
        int bubbleSizePx = (int) (52 * density);

        // Snap bubble to nearest screen edge
        int screenWidth = metrics.widthPixels;
        int targetX = (windowParams.x + windowParams.width / 2 < screenWidth / 2)
                ? (int) (4 * density)
                : screenWidth - bubbleSizePx - (int) (4 * density);

        windowParams.width = bubbleSizePx;
        windowParams.height = bubbleSizePx;
        windowParams.x = targetX;
        clampPosition(windowParams);

        if (pipCameraContainer != null) pipCameraContainer.setVisibility(View.GONE);
        if (pipBubbleContainer != null) pipBubbleContainer.setVisibility(View.VISIBLE);

        applyShapeOutline();
        try {
            windowManager.updateViewLayout(overlayView, windowParams);
        } catch (Exception ignored) {}

        Toast.makeText(appContext, "Facecam Docked (Tap bubble to expand)", Toast.LENGTH_SHORT).show();

        if (stateListener != null) {
            stateListener.onCameraDocked(true);
        }
    }

    public void expandFromDock() {
        if (!isOverlayShowing || overlayView == null || !isDocked) return;
        isDocked = false;

        DisplayMetrics metrics = getRealDisplayMetrics();
        float density = metrics.density;
        int screenWidth = metrics.widthPixels;

        float widthDp = currentWidthDp > 0 ? currentWidthDp : DEFAULT_WIDTH_DP;
        int widthPx = (int) (widthDp * density);
        int heightPx;

        switch (currentAspectRatio) {
            case LANDSCAPE_16_9:
                heightPx = (int) (widthDp / (16f / 9f) * density);
                break;
            case SQUARE_1_1:
            case CIRCLE:
                heightPx = widthPx;
                break;
            case PORTRAIT_3_4:
            default:
                heightPx = (int) (widthDp * (4f / 3f) * density);
                break;
        }

        windowParams.width = savedWidthPx > 0 ? savedWidthPx : widthPx;
        windowParams.height = savedHeightPx > 0 ? savedHeightPx : heightPx;

        if (windowParams.x + windowParams.width > screenWidth) {
            windowParams.x = screenWidth - windowParams.width - (int) (8 * density);
        }
        clampPosition(windowParams);

        if (pipBubbleContainer != null) pipBubbleContainer.setVisibility(View.GONE);
        if (pipCameraContainer != null) pipCameraContainer.setVisibility(View.VISIBLE);

        applyShapeOutline();
        try {
            windowManager.updateViewLayout(overlayView, windowParams);
        } catch (Exception ignored) {}

        overlayView.post(() -> {
            if (tvPipCamera != null && tvPipCamera.getWidth() > 0 && tvPipCamera.getHeight() > 0) {
                configureTransform(tvPipCamera.getWidth(), tvPipCamera.getHeight());
            }
        });

        showControls();
        snapToEdge();

        if (stateListener != null) {
            stateListener.onCameraDocked(false);
        }
    }

    private void applyShapeOutline() {
        if (overlayView == null) return;
        overlayView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                float density = appContext.getResources().getDisplayMetrics().density;

                if (isDocked) {
                    outline.setOval(0, 0, w, h);
                } else if (currentAspectRatio == AspectRatio.CIRCLE) {
                    int minDim = Math.min(w, h);
                    int left = (w - minDim) / 2;
                    int top = (h - minDim) / 2;
                    outline.setOval(left, top, left + minDim, top + minDim);
                } else {
                    outline.setRoundRect(0, 0, w, h, 14 * density);
                }
            }
        });
        overlayView.setClipToOutline(true);
    }

    public void hideControls() {
        if (!isOverlayShowing || overlayView == null || isDocked) return;
        areControlsVisible = false;
        mainHandler.post(() -> {
            if (btnPipRotate != null) btnPipRotate.setVisibility(View.GONE);
            if (btnPipFlip != null) btnPipFlip.setVisibility(View.GONE);
            if (btnPipShape != null) btnPipShape.setVisibility(View.GONE);
            if (btnPipDock != null) btnPipDock.setVisibility(View.GONE);
            if (btnPipClose != null) btnPipClose.setVisibility(View.GONE);
            if (pipHeaderScroll != null) {
                pipHeaderScroll.setBackgroundColor(Color.TRANSPARENT);
            }
        });
    }

    public void showControls() {
        if (!isOverlayShowing || overlayView == null || isDocked) return;
        areControlsVisible = true;
        mainHandler.removeCallbacks(hideControlsRunnable);
        mainHandler.post(() -> {
            if (btnPipRotate != null) btnPipRotate.setVisibility(View.VISIBLE);
            if (btnPipFlip != null) btnPipFlip.setVisibility(View.VISIBLE);
            if (btnPipShape != null) btnPipShape.setVisibility(View.VISIBLE);
            if (btnPipDock != null) btnPipDock.setVisibility(View.VISIBLE);
            if (btnPipClose != null) btnPipClose.setVisibility(View.VISIBLE);
            if (pipHeaderScroll != null) {
                pipHeaderScroll.setBackgroundResource(R.drawable.bg_floating_cam_header);
            }
        });
        mainHandler.postDelayed(hideControlsRunnable, AUTO_HIDE_DELAY_MS);
    }

    private void resetControlsTimer() {
        mainHandler.removeCallbacks(hideControlsRunnable);
        mainHandler.postDelayed(hideControlsRunnable, AUTO_HIDE_DELAY_MS);
    }

    private void snapToEdge() {
        if (overlayView == null || windowParams == null || windowManager == null || !isOverlayShowing) return;

        DisplayMetrics metrics = getRealDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        float density = metrics.density;
        int marginPx = isDocked ? (int) (4 * density) : (int) (8 * density);

        int viewWidth = windowParams.width;
        int centerX = windowParams.x + (viewWidth / 2);
        int targetX;

        if (centerX < screenWidth / 2) {
            targetX = marginPx; // Snap to Left edge
        } else {
            targetX = screenWidth - viewWidth - marginPx; // Snap to Right edge
        }

        ValueAnimator animator = ValueAnimator.ofInt(windowParams.x, targetX);
        animator.setDuration(200);
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(animation -> {
            if (overlayView != null && isOverlayShowing) {
                windowParams.x = (int) animation.getAnimatedValue();
                try {
                    windowManager.updateViewLayout(overlayView, windowParams);
                } catch (Exception ignored) {}
            }
        });
        animator.start();
    }

    private void initOverlayView() {
        LayoutInflater inflater = LayoutInflater.from(appContext);
        overlayView = inflater.inflate(R.layout.layout_floating_cam, null);

        pipCameraContainer = overlayView.findViewById(R.id.pipCameraContainer);
        pipBubbleContainer = overlayView.findViewById(R.id.pipBubbleContainer);
        pipBubbleBadge = overlayView.findViewById(R.id.pipBubbleBadge);

        pipHeaderScroll = overlayView.findViewById(R.id.pipHeaderScroll);
        pipHeader = overlayView.findViewById(R.id.pipHeader);
        tvPipBadge = overlayView.findViewById(R.id.pipBadge);
        btnPipRotate = overlayView.findViewById(R.id.pipBtnRotate);
        btnPipFlip = overlayView.findViewById(R.id.pipBtnFlip);
        btnPipShape = overlayView.findViewById(R.id.pipBtnShape);
        btnPipDock = overlayView.findViewById(R.id.pipBtnDock);
        btnPipClose = overlayView.findViewById(R.id.pipBtnClose);
        tvPipCamera = overlayView.findViewById(R.id.pipCameraView);

        if (btnPipShape != null) {
            btnPipShape.setText(currentAspectRatio.icon);
        }
        updateRotateButtonUi();

        DisplayMetrics metrics = getRealDisplayMetrics();
        lastScreenWidth = metrics.widthPixels;
        lastScreenHeight = metrics.heightPixels;
        float density = metrics.density;
        int widthPx = (int) (currentWidthDp * density);
        int heightPx = (int) (currentWidthDp * (4f / 3f) * density);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        windowParams = new WindowManager.LayoutParams(
                widthPx, heightPx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        windowParams.gravity = Gravity.TOP | Gravity.START;
        windowParams.x = (int) (8 * density);
        windowParams.y = (int) (70 * density);

        setupOverlayListeners();
        applyShapeOutline();
    }

    private void setupOverlayListeners() {
        if (btnPipRotate != null) {
            btnPipRotate.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                rotateCamera();
                resetControlsTimer();
            });
        }

        if (btnPipFlip != null) {
            btnPipFlip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                flipCamera();
                resetControlsTimer();
            });
        }

        if (btnPipShape != null) {
            btnPipShape.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                cycleAspectRatio();
            });
        }

        if (btnPipDock != null) {
            btnPipDock.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                dockToBubble();
            });
        }

        if (btnPipClose != null) {
            btnPipClose.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                hideOverlay();
            });
        }

        scaleGestureDetector = new ScaleGestureDetector(appContext, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float newWidth = Math.max(MIN_WIDTH_DP, Math.min(MAX_WIDTH_DP, currentWidthDp * factor));
                if (Math.abs(newWidth - currentWidthDp) > 0.5f) {
                    currentWidthDp = newWidth;
                    updateWindowDimensions();
                }
                return true;
            }
        });

        View.OnTouchListener dragListener = new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isMoving = false;
            private long downTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!isDocked && scaleGestureDetector != null) {
                    scaleGestureDetector.onTouchEvent(event);
                    if (scaleGestureDetector.isInProgress() || event.getPointerCount() > 1) {
                        isMoving = false;
                        mainHandler.removeCallbacks(hideControlsRunnable);
                        return true;
                    }
                }

                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = windowParams.x;
                        initialY = windowParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isMoving = false;
                        downTime = SystemClock.uptimeMillis();
                        if (!isDocked) {
                            showControls();
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6 || isMoving) {
                            isMoving = true;
                            windowParams.x = initialX + dx;
                            windowParams.y = initialY + dy;
                            clampPosition(windowParams);
                            try {
                                windowManager.updateViewLayout(overlayView, windowParams);
                            } catch (Exception ignored) {}
                        }
                        mainHandler.removeCallbacks(hideControlsRunnable);
                        return true;

                    case MotionEvent.ACTION_UP:
                        long clickDuration = SystemClock.uptimeMillis() - downTime;
                        if (!isMoving && clickDuration < 320) {
                            v.performClick();
                            if (isDocked) {
                                expandFromDock();
                            } else {
                                if (!areControlsVisible) {
                                    showControls();
                                } else {
                                    hideControls();
                                }
                            }
                        } else if (isMoving) {
                            snapToEdge();
                            if (!isDocked) {
                                resetControlsTimer();
                            }
                        } else {
                            if (!isDocked) {
                                resetControlsTimer();
                            }
                        }
                        return true;
                }
                return false;
            }
        };

        if (tvPipCamera != null) {
            tvPipCamera.setOnTouchListener(dragListener);
        }
        if (tvPipBadge != null) {
            tvPipBadge.setOnTouchListener(dragListener);
        }
        View spacer = overlayView.findViewById(R.id.pipHeaderSpacer);
        if (spacer != null) {
            spacer.setOnTouchListener(dragListener);
        }
        if (pipBubbleContainer != null) {
            pipBubbleContainer.setOnTouchListener(dragListener);
        }

        tvPipCamera.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera(surface, width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                closeCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
    }

    private void clampPosition(WindowManager.LayoutParams params) {
        DisplayMetrics metrics = getRealDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        if (params.x < 0) params.x = 0;
        if (params.y < 30) params.y = 30; // Keep below notification status bar
        if (params.x + params.width > screenWidth) params.x = screenWidth - params.width;
        if (params.y + params.height > screenHeight - 40) params.y = screenHeight - params.height - 40;
    }

    // =========================================================================
    // Camera2 Subsystem
    // =========================================================================
    private void startCameraThread() {
        if (cameraThread == null) {
            cameraThread = new HandlerThread("KimLive-CameraThread");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());
        }
    }

    private void stopCameraThread() {
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try {
                cameraThread.join(500);
            } catch (InterruptedException ignored) {}
            cameraThread = null;
            cameraHandler = null;
        }
    }

    private void openCamera(SurfaceTexture surfaceTexture, int viewWidth, int viewHeight) {
        if (appContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        CameraManager cm = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            String selectedId = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(id);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                if (isFrontCamera && facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    selectedId = id;
                    Integer orientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    sensorOrientation = orientation != null ? orientation : 270;
                    break;
                } else if (!isFrontCamera && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    selectedId = id;
                    Integer orientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    sensorOrientation = orientation != null ? orientation : 90;
                    break;
                }
            }

            if (selectedId == null && cm.getCameraIdList().length > 0) {
                selectedId = cm.getCameraIdList()[0];
            }
            if (selectedId == null) return;

            CameraCharacteristics cc = cm.getCameraCharacteristics(selectedId);
            StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight);
                surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            }

            configureTransform(viewWidth, viewHeight);

            cm.openCamera(selectedId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview(surfaceTexture);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    closeCamera();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e(TAG, "Camera device error: " + error);
                    closeCamera();
                }
            }, cameraHandler);

        } catch (Exception e) {
            Log.e(TAG, "Failed to open camera: ", e);
        }
    }

    private void startPreview(SurfaceTexture surfaceTexture) {
        if (cameraDevice == null) return;
        try {
            Surface surface = new Surface(surfaceTexture);
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        session.setRepeatingRequest(builder.build(), null, cameraHandler);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start camera repeating preview request", e);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "Camera capture session configuration failed");
                }
            }, cameraHandler);

        } catch (Exception e) {
            Log.e(TAG, "Error starting camera preview", e);
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (tvPipCamera == null || previewSize == null || viewWidth <= 0 || viewHeight <= 0) return;

        mainHandler.post(() -> {
            try {
                Matrix matrix = new Matrix();
                RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
                float centerX = viewRect.centerX();
                float centerY = viewRect.centerY();

                int effectiveRotation = getEffectiveRotationDegrees();
                currentRotationDegrees = effectiveRotation;
                boolean isRotated90or270 = (effectiveRotation == 90 || effectiveRotation == 270);
                float naturalBufW = previewSize.getHeight(); // e.g. 480
                float naturalBufH = previewSize.getWidth();  // e.g. 640

                RectF bufferRect = new RectF(0, 0, naturalBufW, naturalBufH);
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);

                float targetBufW = isRotated90or270 ? naturalBufH : naturalBufW;
                float targetBufH = isRotated90or270 ? naturalBufW : naturalBufH;

                float scale = Math.max(
                        (float) viewWidth / targetBufW,
                        (float) viewHeight / targetBufH
                );
                matrix.postScale(scale, scale, centerX, centerY);

                if (effectiveRotation != 0) {
                    matrix.postRotate(effectiveRotation, centerX, centerY);
                }

                if (isFrontCamera) {
                    // Mirror horizontally for natural selfie orientation
                    matrix.postScale(-1, 1, centerX, centerY);
                }

                tvPipCamera.setTransform(matrix);
            } catch (Exception ignored) {}
        });
    }

    private Size chooseOptimalSize(Size[] choices, int width, int height) {
        if (choices == null || choices.length == 0) {
            return new Size(640, 480);
        }
        // Prefer standard 640x480 for optimal performance, low heat, and zero frame drops
        for (Size size : choices) {
            if (size.getWidth() == 640 && size.getHeight() == 480) {
                return size;
            }
        }
        for (Size size : choices) {
            if (size.getWidth() == 1280 && size.getHeight() == 720) {
                return size;
            }
        }
        return choices[0];
    }

    private void closeCamera() {
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
                captureSession.close();
            } catch (Exception ignored) {}
            captureSession = null;
        }
        if (cameraDevice != null) {
            try {
                cameraDevice.close();
            } catch (Exception ignored) {}
            cameraDevice = null;
        }
    }
}
