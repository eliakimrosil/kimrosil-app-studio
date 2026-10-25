package com.aistudio.omnistreamlivestudio;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
        default void onRequestStartBroadcast() {}
        default void onBroadcastToggled(boolean isLive) {}
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

    // Camera Header Controls
    private View pipHeaderScroll;
    private View pipHeader;
    private TextView btnPipFlip;
    private TextView btnPipShape;
    private TextView btnPipBroadcast;
    private TextureView tvPipCamera;

    // Bottom Dismiss Target Overlay (Drag to Close)
    private View dismissOverlayView;
    private WindowManager.LayoutParams dismissParams;
    private View dismissBadge;
    private TextView tvDismissHint;
    private boolean isDismissOverlayShowing = false;
    private boolean isHoveringDismiss = false;
    private final Runnable longPressRunnable = this::onLongPressDetected;

    // Auto-Hiding Controls
    private static final long AUTO_HIDE_DELAY_MS = 3500;
    private final Runnable hideControlsRunnable = this::hideControls;
    private boolean areControlsVisible = true;

    // Dual Position Memory (Landscape & Portrait)
    private static final String PREFS_NAME = "kim_live_studio_prefs";
    private static final String PREF_PIP_PORTRAIT_X = "pip_portrait_x";
    private static final String PREF_PIP_PORTRAIT_Y = "pip_portrait_y";
    private static final String PREF_PIP_LANDSCAPE_X = "pip_landscape_x";
    private static final String PREF_PIP_LANDSCAPE_Y = "pip_landscape_y";

    // Aspect Ratio & Compact Gaming Sizing
    private AspectRatio currentAspectRatio = AspectRatio.PORTRAIT_3_4;
    private static final float DEFAULT_WIDTH_DP = 110f; // Compact gaming starting size
    private static final float MIN_WIDTH_DP = 64f;      // Ultra-compact corner thumbnail
    private static final float MAX_WIDTH_DP = 220f;     // Non-intrusive upper limit
    private float currentWidthDp = DEFAULT_WIDTH_DP;

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
        return false;
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
                if (btnPipBroadcast != null) {
                    btnPipBroadcast.setText(isLive ? "■" : "▶");
                    btnPipBroadcast.setTextColor(isLive ? 0xFFFFFFFF : 0xFFEF4444);
                    btnPipBroadcast.setBackgroundResource(isLive ? R.drawable.btn_pip_broadcast_live : R.drawable.btn_pip_broadcast_standby);
                    btnPipBroadcast.setContentDescription(isLive ? "Stop Broadcast" : "Start Broadcast");
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
            mainHandler.removeCallbacks(longPressRunnable);
            stopOrientationMonitoring();
            closeCamera();
            stopCameraThread();

            hideDismissTargetImmediate();

            if (overlayView != null && windowManager != null) {
                try {
                    windowManager.removeView(overlayView);
                } catch (Exception ignored) {}
                overlayView = null;
            }
            isOverlayShowing = false;
            areControlsVisible = true;

            if (stateListener != null) {
                stateListener.onFacecamToggled(false);
            }
        });
    }

    public void flipCamera() {
        isFrontCamera = !isFrontCamera;
        currentRotationDegrees = getEffectiveRotationDegrees();
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

        if (tvPipCamera != null && tvPipCamera.getWidth() > 0 && tvPipCamera.getHeight() > 0) {
            configureTransform(tvPipCamera.getWidth(), tvPipCamera.getHeight());
        }

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
        return computeAutoRotationDegrees();
    }

    public int computeCompensationDegrees(int displayDegrees) {
        // Both front and rear cameras follow standard Android Camera2 display compensation:
        // display 0° (Portrait) -> 0°
        // display 90° (Landscape Left) -> 270° (-90°)
        // display 180° (Inverted Portrait) -> 180°
        // display 270° (Landscape Right / Reverse Landscape) -> 90°
        // (Front camera additionally receives horizontal mirror postScale(-1, 1) in configureTransform)
        return (360 - (displayDegrees % 360)) % 360;
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

        return computeCompensationDegrees(displayDegrees);
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
            if (isDismissOverlayShowing) {
                hideDismissTargetImmediate();
            }
            realignWindowForOrientation();
            int newEffective = getEffectiveRotationDegrees();
            if (newEffective != currentRotationDegrees) {
                currentRotationDegrees = newEffective;
                if (tvPipCamera != null && tvPipCamera.getWidth() > 0 && tvPipCamera.getHeight() > 0) {
                    configureTransform(tvPipCamera.getWidth(), tvPipCamera.getHeight());
                }
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

            boolean wasLandscape = lastScreenWidth > lastScreenHeight;
            boolean isLandscape = newScreenWidth > newScreenHeight;

            // Save the position of the orientation we are leaving
            savePositionForOrientation(wasLandscape);

            // Restore the saved position of the newly rotated orientation
            restorePositionForOrientation(isLandscape, newScreenWidth, newScreenHeight);
        }

        lastScreenWidth = newScreenWidth;
        lastScreenHeight = newScreenHeight;

        updateWindowDimensions();
    }

    private void savePositionForOrientation(boolean isLandscape) {
        if (windowParams == null || appContext == null) return;
        try {
            SharedPreferences sp = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            sp.edit()
                .putInt(isLandscape ? PREF_PIP_LANDSCAPE_X : PREF_PIP_PORTRAIT_X, windowParams.x)
                .putInt(isLandscape ? PREF_PIP_LANDSCAPE_Y : PREF_PIP_PORTRAIT_Y, windowParams.y)
                .apply();
        } catch (Exception ignored) {}
    }

    private void restorePositionForOrientation(boolean isLandscape, int screenWidth, int screenHeight) {
        if (windowParams == null || appContext == null) return;
        DisplayMetrics metrics = getRealDisplayMetrics();
        float density = metrics.density;

        try {
            SharedPreferences sp = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (isLandscape) {
                int defaultX = Math.max(0, screenWidth - windowParams.width - (int) (16 * density));
                int defaultY = (int) (12 * density);
                windowParams.x = sp.getInt(PREF_PIP_LANDSCAPE_X, defaultX);
                windowParams.y = sp.getInt(PREF_PIP_LANDSCAPE_Y, defaultY);
            } else {
                int defaultX = (int) (8 * density);
                int defaultY = (int) (70 * density);
                windowParams.x = sp.getInt(PREF_PIP_PORTRAIT_X, defaultX);
                windowParams.y = sp.getInt(PREF_PIP_PORTRAIT_Y, defaultY);
            }
        } catch (Exception e) {
            if (isLandscape) {
                windowParams.x = Math.max(0, screenWidth - windowParams.width - (int) (16 * density));
                windowParams.y = (int) (12 * density);
            } else {
                windowParams.x = (int) (8 * density);
                windowParams.y = (int) (70 * density);
            }
        }

        clampPosition(windowParams);
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

    public void toggleBroadcast() {
        if (isLiveBroadcasting) {
            Intent stopIntent = new Intent(appContext, MainActivity.StreamService.class);
            stopIntent.setAction(MainActivity.StreamService.ACTION_STOP);
            appContext.startService(stopIntent);
            setLiveState(false);
            Toast.makeText(appContext, "Broadcast Stopped", Toast.LENGTH_SHORT).show();
            if (stateListener != null) {
                stateListener.onBroadcastToggled(false);
            }
        } else {
            if (stateListener != null) {
                stateListener.onRequestStartBroadcast();
            } else {
                Intent startIntent = new Intent(appContext, MainActivity.class);
                startIntent.setAction(MainActivity.ACTION_REQUEST_START_STREAM);
                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                appContext.startActivity(startIntent);
            }
        }
    }

    private void updateWindowDimensions() {
        if (!isOverlayShowing || overlayView == null || windowParams == null || windowManager == null) return;

        DisplayMetrics metrics = getRealDisplayMetrics();
        float density = metrics.density;
        boolean isScreenLandscape = metrics.widthPixels > metrics.heightPixels;

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
                if (isScreenLandscape) {
                    // Match landscape screen orientation: 4:3 widescreen card
                    heightPx = (int) (currentWidthDp / (4f / 3f) * density);
                } else {
                    // Match portrait screen orientation: 3:4 portrait card
                    heightPx = (int) (currentWidthDp * (4f / 3f) * density);
                }
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

    private void applyShapeOutline() {
        if (overlayView == null) return;
        overlayView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                float density = appContext.getResources().getDisplayMetrics().density;

                if (currentAspectRatio == AspectRatio.CIRCLE) {
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
        if (!isOverlayShowing || overlayView == null) return;
        areControlsVisible = false;
        mainHandler.post(() -> {
            if (btnPipFlip != null) btnPipFlip.setVisibility(View.GONE);
            if (btnPipShape != null) btnPipShape.setVisibility(View.GONE);
            if (btnPipBroadcast != null) btnPipBroadcast.setVisibility(View.GONE);
            if (pipHeaderScroll != null) {
                pipHeaderScroll.setBackgroundColor(Color.TRANSPARENT);
            }
        });
    }

    public void showControls() {
        if (!isOverlayShowing || overlayView == null) return;
        areControlsVisible = true;
        mainHandler.removeCallbacks(hideControlsRunnable);
        mainHandler.post(() -> {
            if (btnPipFlip != null) btnPipFlip.setVisibility(View.VISIBLE);
            if (btnPipShape != null) btnPipShape.setVisibility(View.VISIBLE);
            if (btnPipBroadcast != null) btnPipBroadcast.setVisibility(View.VISIBLE);
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
        boolean isLandscape = screenWidth > metrics.heightPixels;

        if (isLandscape) {
            // Freeform in landscape: clamp and save position with zero edge snapping
            clampPosition(windowParams);
            try {
                windowManager.updateViewLayout(overlayView, windowParams);
            } catch (Exception ignored) {}
            savePositionForOrientation(true);
            return;
        }

        float density = metrics.density;
        int marginPx = (int) (8 * density);

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
                clampPosition(windowParams);
                try {
                    windowManager.updateViewLayout(overlayView, windowParams);
                } catch (Exception ignored) {}
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                savePositionForOrientation(false);
            }
        });
        animator.start();
    }

    private void initOverlayView() {
        LayoutInflater inflater = LayoutInflater.from(appContext);
        overlayView = inflater.inflate(R.layout.layout_floating_cam, null);
        overlayView.setHapticFeedbackEnabled(true);
        overlayView.setScaleX(1.0f);
        overlayView.setScaleY(1.0f);
        overlayView.setAlpha(1.0f);
        overlayView.setTranslationX(0f);
        overlayView.setTranslationY(0f);

        pipCameraContainer = overlayView.findViewById(R.id.pipCameraContainer);

        pipHeaderScroll = overlayView.findViewById(R.id.pipHeaderScroll);
        pipHeader = overlayView.findViewById(R.id.pipHeader);
        btnPipFlip = overlayView.findViewById(R.id.pipBtnFlip);
        btnPipShape = overlayView.findViewById(R.id.pipBtnShape);
        btnPipBroadcast = overlayView.findViewById(R.id.pipBtnBroadcast);
        tvPipCamera = overlayView.findViewById(R.id.pipCameraView);

        if (btnPipShape != null) {
            btnPipShape.setText(currentAspectRatio.icon);
        }

        if (btnPipBroadcast != null) {
            btnPipBroadcast.setText(isLiveBroadcasting ? "■" : "▶");
            btnPipBroadcast.setTextColor(isLiveBroadcasting ? 0xFFFFFFFF : 0xFFEF4444);
            btnPipBroadcast.setBackgroundResource(isLiveBroadcasting ? R.drawable.btn_pip_broadcast_live : R.drawable.btn_pip_broadcast_standby);
            btnPipBroadcast.setContentDescription(isLiveBroadcasting ? "Stop Broadcast" : "Start Broadcast");
        }

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

        boolean isLandscape = lastScreenWidth > lastScreenHeight;
        restorePositionForOrientation(isLandscape, lastScreenWidth, lastScreenHeight);

        setupOverlayListeners();
        applyShapeOutline();
    }

    private void setupOverlayListeners() {
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

        if (btnPipBroadcast != null) {
            btnPipBroadcast.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                toggleBroadcast();
                resetControlsTimer();
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
                    DisplayMetrics m = getRealDisplayMetrics();
                    savePositionForOrientation(m.widthPixels > m.heightPixels);
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
                if (scaleGestureDetector != null) {
                    scaleGestureDetector.onTouchEvent(event);
                    if (scaleGestureDetector.isInProgress() || event.getPointerCount() > 1) {
                        mainHandler.removeCallbacks(longPressRunnable);
                        if (isMoving) {
                            isMoving = false;
                            hideDismissTarget();
                        }
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
                        isHoveringDismiss = false;
                        downTime = SystemClock.uptimeMillis();
                        mainHandler.removeCallbacks(longPressRunnable);
                        mainHandler.postDelayed(longPressRunnable, 350);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6 || isMoving) {
                            if (!isMoving) {
                                isMoving = true;
                                mainHandler.removeCallbacks(longPressRunnable);
                                showDismissTarget();
                            }
                            windowParams.x = initialX + dx;
                            windowParams.y = initialY + dy;
                            clampPosition(windowParams);
                            try {
                                windowManager.updateViewLayout(overlayView, windowParams);
                            } catch (Exception ignored) {}

                            updateDismissHoverState(event.getRawX(), event.getRawY());
                        }
                        mainHandler.removeCallbacks(hideControlsRunnable);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mainHandler.removeCallbacks(longPressRunnable);
                        long clickDuration = SystemClock.uptimeMillis() - downTime;

                        if (isHoveringDismiss) {
                            triggerDismissAnimation();
                            return true;
                        }

                        hideDismissTarget();

                        if (!isMoving && clickDuration < 320) {
                            v.performClick();
                            if (!areControlsVisible) {
                                showControls();
                            } else {
                                hideControls();
                            }
                        } else if (isMoving) {
                            snapToEdge();
                            resetControlsTimer();
                        } else {
                            resetControlsTimer();
                        }
                        return true;
                }
                return false;
            }
        };

        if (tvPipCamera != null) {
            tvPipCamera.setOnTouchListener(dragListener);
        }
        if (pipCameraContainer != null) {
            pipCameraContainer.setOnTouchListener(dragListener);
        }
        if (overlayView != null) {
            overlayView.setOnTouchListener(dragListener);
        }
        View spacer = overlayView.findViewById(R.id.pipHeaderSpacer);
        if (spacer != null) {
            spacer.setOnTouchListener(dragListener);
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

    // =========================================================================
    // Bottom Dismiss Target Overlay (Drag to Close)
    // =========================================================================

    private void onLongPressDetected() {
        if (!isOverlayShowing || isDismissOverlayShowing) return;
        if (overlayView != null) {
            overlayView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        showDismissTarget();
    }

    private void initDismissOverlay() {
        if (dismissOverlayView != null) return;
        LayoutInflater inflater = LayoutInflater.from(appContext);
        dismissOverlayView = inflater.inflate(R.layout.layout_pip_dismiss_target, null);
        dismissBadge = dismissOverlayView.findViewById(R.id.pipDismissBadge);
        tvDismissHint = dismissOverlayView.findViewById(R.id.pipDismissHint);

        DisplayMetrics metrics = getRealDisplayMetrics();
        float density = metrics.density;

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        dismissParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        dismissParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dismissParams.y = (int) (36 * density);
    }

    private void showDismissTarget() {
        if (isDismissOverlayShowing || windowManager == null) return;
        initDismissOverlay();
        if (dismissOverlayView == null) return;

        try {
            dismissOverlayView.setAlpha(0f);
            dismissOverlayView.setScaleX(0.7f);
            dismissOverlayView.setScaleY(0.7f);
            dismissOverlayView.setTranslationY(50f);
            if (dismissBadge != null) {
                dismissBadge.setBackgroundResource(R.drawable.bg_pip_dismiss_target);
                dismissBadge.setScaleX(1.0f);
                dismissBadge.setScaleY(1.0f);
            }
            if (tvDismissHint != null) {
                tvDismissHint.setText("DRAG HERE TO CLOSE");
                tvDismissHint.setTextColor(0xFFA1A1AA);
            }
            isHoveringDismiss = false;

            windowManager.addView(dismissOverlayView, dismissParams);
            isDismissOverlayShowing = true;

            dismissOverlayView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(180)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } catch (Exception e) {
            Log.e(TAG, "Error displaying dismiss overlay target", e);
        }
    }

    private void hideDismissTarget() {
        if (!isDismissOverlayShowing || dismissOverlayView == null || windowManager == null) {
            isDismissOverlayShowing = false;
            isHoveringDismiss = false;
            return;
        }
        isHoveringDismiss = false;
        final View targetView = dismissOverlayView;
        isDismissOverlayShowing = false;
        targetView.animate()
                .alpha(0f)
                .scaleX(0.6f)
                .scaleY(0.6f)
                .translationY(50f)
                .setDuration(150)
                .withEndAction(() -> {
                    try {
                        windowManager.removeView(targetView);
                    } catch (Exception ignored) {}
                    if (dismissOverlayView == targetView) {
                        dismissOverlayView = null;
                    }
                })
                .start();
    }

    private void hideDismissTargetImmediate() {
        isDismissOverlayShowing = false;
        isHoveringDismiss = false;
        if (dismissOverlayView != null && windowManager != null) {
            try {
                windowManager.removeView(dismissOverlayView);
            } catch (Exception ignored) {}
            dismissOverlayView = null;
        }
    }

    private void updateDismissHoverState(float rawTouchX, float rawTouchY) {
        if (!isDismissOverlayShowing || overlayView == null || windowParams == null) return;

        DisplayMetrics metrics = getRealDisplayMetrics();
        float density = metrics.density;
        float screenWidth = metrics.widthPixels;
        float screenHeight = metrics.heightPixels;

        float targetCenterX = screenWidth / 2.0f;
        // Gravity BOTTOM with 36dp margin + 24dp label offset + 28dp half badge = 88dp
        float targetCenterY = screenHeight - (88f * density);

        float camCenterX = windowParams.x + (windowParams.width / 2.0f);
        float camCenterY = windowParams.y + (windowParams.height / 2.0f);

        double camDist = Math.hypot(camCenterX - targetCenterX, camCenterY - targetCenterY);
        double touchDist = Math.hypot(rawTouchX - targetCenterX, rawTouchY - targetCenterY);

        boolean shouldHover = (camDist < 115f * density) || (touchDist < 95f * density);

        if (shouldHover && !isHoveringDismiss) {
            isHoveringDismiss = true;
            if (overlayView != null) {
                overlayView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            if (dismissBadge != null) {
                dismissBadge.setBackgroundResource(R.drawable.bg_pip_dismiss_target_hover);
                dismissBadge.animate().scaleX(1.22f).scaleY(1.22f).setDuration(120).start();
            }
            if (tvDismissHint != null) {
                tvDismissHint.setText("RELEASE TO CLOSE");
                tvDismissHint.setTextColor(0xFFEF4444);
            }
        } else if (!shouldHover && isHoveringDismiss) {
            isHoveringDismiss = false;
            if (dismissBadge != null) {
                dismissBadge.setBackgroundResource(R.drawable.bg_pip_dismiss_target);
                dismissBadge.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }
            if (tvDismissHint != null) {
                tvDismissHint.setText("DRAG HERE TO CLOSE");
                tvDismissHint.setTextColor(0xFFA1A1AA);
            }
        }
    }

    private void triggerDismissAnimation() {
        if (overlayView != null) {
            overlayView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            overlayView.animate()
                    .scaleX(0.1f)
                    .scaleY(0.1f)
                    .alpha(0f)
                    .setDuration(180)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        hideOverlay();
                        Toast.makeText(appContext, "Facecam Closed", Toast.LENGTH_SHORT).show();
                    })
                    .start();
        } else {
            hideOverlay();
            Toast.makeText(appContext, "Facecam Closed", Toast.LENGTH_SHORT).show();
        }
        hideDismissTarget();
    }

    private void clampPosition(WindowManager.LayoutParams params) {
        DisplayMetrics metrics = getRealDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        float density = metrics.density;
        boolean isLandscape = screenWidth > screenHeight;

        int minX = 0;
        int maxX = Math.max(0, screenWidth - params.width);
        int minY = isLandscape ? (int) (4 * density) : (int) (24 * density);
        int maxY = isLandscape
                ? Math.max(minY, screenHeight - params.height - (int) (4 * density))
                : Math.max(minY, screenHeight - params.height - (int) (32 * density));

        if (params.x < minX) params.x = minX;
        if (params.x > maxX) params.x = maxX;
        if (params.y < minY) params.y = minY;
        if (params.y > maxY) params.y = maxY;
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

                boolean isScreenLandscape = viewWidth > viewHeight;
                int bufMax = Math.max(previewSize.getWidth(), previewSize.getHeight()); // e.g. 640
                int bufMin = Math.min(previewSize.getWidth(), previewSize.getHeight()); // e.g. 480

                // Base buffer geometry centered in view
                float naturalBufW = bufMin;
                float naturalBufH = bufMax;
                RectF bufferRect = new RectF(0, 0, naturalBufW, naturalBufH);
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);

                // Target upright dimensions matching current screen framing
                float targetBufW = isScreenLandscape ? bufMax : bufMin;
                float targetBufH = isScreenLandscape ? bufMin : bufMax;

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
