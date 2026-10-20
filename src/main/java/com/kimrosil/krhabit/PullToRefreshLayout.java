package com.kimrosil.krhabit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Pure native, ultra-lightweight pull-to-refresh layout for KR Habits.
 * Provides tactile drag resistance, threshold haptic click, and smooth animations.
 */
public class PullToRefreshLayout extends FrameLayout {

    public interface OnRefreshListener {
        void onRefresh();
    }

    private View targetView;
    private FrameLayout indicatorContainer;
    private TextView tvArrow;
    private ProgressBar progressBar;

    private int touchSlop;
    private float initialDownY;
    private float initialDownX;
    private boolean isBeingDragged;
    private boolean isRefreshing;
    private boolean hapticFired;

    private float currentDragDistance = 0f;
    private float triggerDistance;
    private float maxDragDistance;
    private float restingDistance;
    private float indicatorSize;

    private int activePointerId = -1;
    private OnRefreshListener refreshListener;
    private final DecelerateInterpolator interpolator = new DecelerateInterpolator(2.0f);

    public PullToRefreshLayout(Context context) {
        super(context);
        init(context);
    }

    public PullToRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PullToRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        triggerDistance = dpToPx(68);
        maxDragDistance = dpToPx(130);
        restingDistance = dpToPx(48);
        indicatorSize = dpToPx(40);
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (targetView == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child != indicatorContainer) {
                    targetView = child;
                    break;
                }
            }
        }
        setupIndicator();
    }

    private void setupIndicator() {
        if (indicatorContainer != null) return;

        Context context = getContext();
        indicatorContainer = new FrameLayout(context);
        LayoutParams lp = new LayoutParams((int) indicatorSize, (int) indicatorSize);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = (int) dpToPx(8);
        indicatorContainer.setLayoutParams(lp);
        indicatorContainer.setBackgroundResource(R.drawable.refresh_badge_bg);
        indicatorContainer.setVisibility(View.GONE);
        indicatorContainer.setTranslationY(-indicatorSize * 2);

        tvArrow = new TextView(context);
        LayoutParams lpArrow = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        tvArrow.setLayoutParams(lpArrow);
        tvArrow.setGravity(Gravity.CENTER);
        tvArrow.setText("↓");
        tvArrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tvArrow.setTextColor(context.getColor(R.color.primary_accent));
        indicatorContainer.addView(tvArrow);

        progressBar = new ProgressBar(context);
        int pbSize = (int) dpToPx(22);
        LayoutParams lpPb = new LayoutParams(pbSize, pbSize);
        lpPb.gravity = Gravity.CENTER;
        progressBar.setLayoutParams(lpPb);
        progressBar.setIndeterminate(true);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(context.getColor(R.color.primary_accent)));
        progressBar.setVisibility(View.GONE);
        indicatorContainer.addView(progressBar);

        addView(indicatorContainer);
        indicatorContainer.bringToFront();
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.refreshListener = listener;
    }

    public boolean isRefreshing() {
        return isRefreshing;
    }

    private boolean canChildScrollUp() {
        if (targetView == null) return false;
        return targetView.canScrollVertically(-1);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // When user is at the top of the scroll content, ignore disallow requests from ScrollView
        // so we can intercept pull-to-refresh gestures.
        if (!canChildScrollUp()) {
            return;
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled() || canChildScrollUp() || isRefreshing) {
            return false;
        }

        final int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = ev.getPointerId(0);
                initialDownY = ev.getY();
                initialDownX = ev.getX();
                isBeingDragged = false;
                hapticFired = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                if (activePointerId == -1) break;
                int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex < 0) break;

                float y = ev.getY(pointerIndex);
                float x = ev.getX(pointerIndex);
                float yDiff = y - initialDownY;
                float xDiff = Math.abs(x - initialDownX);

                if (yDiff > touchSlop && yDiff > xDiff) {
                    isBeingDragged = true;
                    if (indicatorContainer != null) {
                        indicatorContainer.setVisibility(View.VISIBLE);
                        indicatorContainer.bringToFront();
                    }
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isBeingDragged = false;
                activePointerId = -1;
                break;
        }

        return isBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled() || canChildScrollUp() || isRefreshing) {
            return super.onTouchEvent(ev);
        }

        final int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = ev.getPointerId(0);
                initialDownY = ev.getY();
                initialDownX = ev.getX();
                isBeingDragged = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex < 0) break;

                float y = ev.getY(pointerIndex);
                float yDiff = y - initialDownY;

                if (!isBeingDragged && yDiff > touchSlop) {
                    isBeingDragged = true;
                }

                if (isBeingDragged) {
                    float overscroll = yDiff - touchSlop;
                    if (overscroll >= 0) {
                        currentDragDistance = Math.min(maxDragDistance, overscroll * 0.45f);
                        updateIndicatorProgress(currentDragDistance);
                        return true;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = ev.getActionIndex();
                activePointerId = ev.getPointerId(index);
                initialDownY = ev.getY(index);
                initialDownX = ev.getX(index);
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (isBeingDragged) {
                    isBeingDragged = false;
                    activePointerId = -1;
                    if (currentDragDistance >= triggerDistance) {
                        startRefreshing();
                    } else {
                        cancelRefreshing();
                    }
                    return true;
                }
                activePointerId = -1;
                break;
            }
        }

        return super.onTouchEvent(ev);
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        int pointerIndex = ev.getActionIndex();
        int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == activePointerId) {
            int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            activePointerId = ev.getPointerId(newPointerIndex);
            initialDownY = ev.getY(newPointerIndex);
            initialDownX = ev.getX(newPointerIndex);
        }
    }

    private void updateIndicatorProgress(float dragDistance) {
        if (indicatorContainer == null) return;

        if (dragDistance <= 0) {
            indicatorContainer.setVisibility(View.GONE);
            if (targetView != null) targetView.setTranslationY(0);
            return;
        }

        float progress = Math.min(1.0f, dragDistance / triggerDistance);
        float indicatorY = (dragDistance * 0.8f) - (indicatorSize * 0.2f);

        indicatorContainer.setVisibility(View.VISIBLE);
        indicatorContainer.bringToFront();
        indicatorContainer.setTranslationY(indicatorY);

        if (targetView != null) {
            targetView.setTranslationY(dragDistance * 0.4f);
        }

        if (tvArrow != null) {
            tvArrow.setVisibility(View.VISIBLE);
            tvArrow.setRotation(progress * 180f);
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        float scale = 0.75f + (progress * 0.25f);
        indicatorContainer.setScaleX(scale);
        indicatorContainer.setScaleY(scale);
        indicatorContainer.setAlpha(0.4f + (progress * 0.6f));

        if (progress >= 1.0f && !hapticFired) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            hapticFired = true;
        } else if (progress < 1.0f) {
            hapticFired = false;
        }
    }

    public void startRefreshing() {
        if (isRefreshing) return;
        isRefreshing = true;

        performHapticFeedback(HapticFeedbackConstants.CONFIRM);

        if (tvArrow != null) tvArrow.setVisibility(View.GONE);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        animateToResting(new Runnable() {
            @Override
            public void run() {
                if (refreshListener != null) {
                    refreshListener.onRefresh();
                }
            }
        });
    }

    private void animateToResting(final Runnable onEnd) {
        if (indicatorContainer == null) {
            if (onEnd != null) onEnd.run();
            return;
        }

        ValueAnimator anim = ValueAnimator.ofFloat(indicatorContainer.getTranslationY(), restingDistance);
        anim.setDuration(220);
        anim.setInterpolator(interpolator);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                indicatorContainer.setTranslationY((float) animation.getAnimatedValue());
            }
        });
        if (onEnd != null) {
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
        }
        anim.start();

        if (targetView != null) {
            targetView.animate()
                    .translationY(dpToPx(16))
                    .setDuration(220)
                    .setInterpolator(interpolator)
                    .start();
        }
    }

    public void setRefreshing(boolean refreshing) {
        if (!refreshing && isRefreshing) {
            isRefreshing = false;

            if (indicatorContainer != null) {
                ValueAnimator anim = ValueAnimator.ofFloat(indicatorContainer.getTranslationY(), -indicatorSize * 2);
                anim.setDuration(260);
                anim.setInterpolator(interpolator);
                anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        indicatorContainer.setTranslationY((float) animation.getAnimatedValue());
                    }
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        indicatorContainer.setVisibility(View.GONE);
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        if (tvArrow != null) {
                            tvArrow.setVisibility(View.VISIBLE);
                            tvArrow.setRotation(0);
                        }
                        currentDragDistance = 0f;
                        hapticFired = false;
                    }
                });
                anim.start();
            }

            if (targetView != null && targetView.getTranslationY() > 0) {
                targetView.animate()
                        .translationY(0)
                        .setDuration(240)
                        .setInterpolator(interpolator)
                        .start();
            }
        } else if (refreshing && !isRefreshing) {
            if (indicatorContainer != null) {
                indicatorContainer.setVisibility(View.VISIBLE);
                indicatorContainer.bringToFront();
                if (tvArrow != null) tvArrow.setVisibility(View.GONE);
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            }
            startRefreshing();
        }
    }

    private void cancelRefreshing() {
        if (indicatorContainer != null) {
            ValueAnimator anim = ValueAnimator.ofFloat(indicatorContainer.getTranslationY(), -indicatorSize * 2);
            anim.setDuration(220);
            anim.setInterpolator(interpolator);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    indicatorContainer.setTranslationY((float) animation.getAnimatedValue());
                }
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    indicatorContainer.setVisibility(View.GONE);
                    currentDragDistance = 0f;
                    hapticFired = false;
                }
            });
            anim.start();
        }

        if (targetView != null && targetView.getTranslationY() > 0) {
            targetView.animate()
                    .translationY(0)
                    .setDuration(200)
                    .setInterpolator(interpolator)
                    .start();
        }
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
