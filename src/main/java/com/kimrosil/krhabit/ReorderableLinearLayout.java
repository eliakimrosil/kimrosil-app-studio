package com.kimrosil.krhabit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom LinearLayout supporting fluid in-place drag-and-drop reordering on long press,
 * inspired by Loop Habit Tracker.
 */
public class ReorderableLinearLayout extends LinearLayout {

    public interface OnOrderChangedListener {
        void onOrderChanged(List<String> reorderedUuids);
    }

    private static final int LONG_PRESS_TIMEOUT_MS = 300;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int touchSlop;
    private float lastTouchX;
    private float lastTouchY;
    private float downX;
    private float downY;
    private float dragStartY;

    private View candidateChild;
    private View draggedChild;
    private boolean isDragging = false;

    private ScrollView parentScrollView;
    private PullToRefreshLayout parentPullToRefresh;
    private OnOrderChangedListener orderChangedListener;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (candidateChild != null && candidateChild.getParent() == ReorderableLinearLayout.this) {
                startDragging(candidateChild);
            }
        }
    };

    public ReorderableLinearLayout(Context context) {
        super(context);
        init(context);
    }

    public ReorderableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ReorderableLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Smooth sliding animations for sibling views when swapped
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(LayoutTransition.CHANGE_APPEARING, 180);
        transition.setDuration(LayoutTransition.CHANGE_DISAPPEARING, 180);
        transition.setDuration(LayoutTransition.CHANGING, 180);
        transition.disableTransitionType(LayoutTransition.APPEARING);
        transition.disableTransitionType(LayoutTransition.DISAPPEARING);
        setLayoutTransition(transition);
    }

    public void setOnOrderChangedListener(OnOrderChangedListener listener) {
        this.orderChangedListener = listener;
    }

    public boolean isDragging() {
        return isDragging;
    }

    private ScrollView getParentScrollView() {
        if (parentScrollView != null) return parentScrollView;
        ViewParent p = getParent();
        while (p != null) {
            if (p instanceof ScrollView) {
                parentScrollView = (ScrollView) p;
                return parentScrollView;
            }
            p = p.getParent();
        }
        return null;
    }

    private PullToRefreshLayout getParentPullToRefresh() {
        if (parentPullToRefresh != null) return parentPullToRefresh;
        ViewParent p = getParent();
        while (p != null) {
            if (p instanceof PullToRefreshLayout) {
                parentPullToRefresh = (PullToRefreshLayout) p;
                return parentPullToRefresh;
            }
            p = p.getParent();
        }
        return null;
    }

    private View findChildUnder(float x, float y) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == VISIBLE &&
                    x >= child.getLeft() && x <= child.getRight() &&
                    y >= child.getTop() && y <= child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastTouchX = ev.getX();
        lastTouchY = ev.getY();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                downX = ev.getX();
                downY = ev.getY();
                candidateChild = findChildUnder(downX, downY);

                if (candidateChild != null && getChildCount() > 1) {
                    handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS);
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                float dx = Math.abs(ev.getX() - downX);
                float dy = Math.abs(ev.getY() - downY);

                if (!isDragging && (dx > touchSlop || dy > touchSlop)) {
                    handler.removeCallbacks(longPressRunnable);
                    candidateChild = null;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                handler.removeCallbacks(longPressRunnable);
                candidateChild = null;
                break;
            }
        }

        return isDragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                if (isDragging && draggedChild != null) {
                    float currentY = ev.getY();
                    float deltaY = currentY - dragStartY;
                    draggedChild.setTranslationY(deltaY);

                    checkAutoScroll(ev.getRawY());
                    checkAndPerformSwap();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                handler.removeCallbacks(longPressRunnable);
                if (isDragging) {
                    finishDragging();
                    return true;
                }
                break;
            }
        }

        return super.onTouchEvent(ev);
    }

    public boolean startDragForChild(View child) {
        if (child == null || indexOfChild(child) < 0 || getChildCount() <= 1) {
            return false;
        }
        handler.removeCallbacks(longPressRunnable);
        candidateChild = child;
        downX = lastTouchX;
        downY = lastTouchY;
        startDragging(child);
        return true;
    }

    private void startDragging(View child) {
        if (isDragging) return;
        isDragging = true;
        draggedChild = child;
        dragStartY = downY;

        // Visual and haptic feedback
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        draggedChild.setTranslationZ(dpToPx(16));
        draggedChild.animate()
                .scaleX(1.025f)
                .scaleY(1.025f)
                .alpha(0.92f)
                .setDuration(120)
                .start();
        int pl = draggedChild.getPaddingLeft();
        int pt = draggedChild.getPaddingTop();
        int pr = draggedChild.getPaddingRight();
        int pb = draggedChild.getPaddingBottom();
        draggedChild.setBackgroundResource(R.drawable.card_surface_dragged);
        draggedChild.setPadding(pl, pt, pr, pb);

        // Lock parent scrolling and pull-to-refresh
        ScrollView sv = getParentScrollView();
        if (sv != null) sv.requestDisallowInterceptTouchEvent(true);

        PullToRefreshLayout ptr = getParentPullToRefresh();
        if (ptr != null) ptr.setEnabled(false);
    }

    private void checkAndPerformSwap() {
        if (draggedChild == null) return;

        int currentIndex = indexOfChild(draggedChild);
        if (currentIndex < 0) return;

        float draggedCenter = draggedChild.getTop() + draggedChild.getTranslationY() + (draggedChild.getHeight() / 2f);

        // Check Swap Upward
        if (currentIndex > 0) {
            View prevChild = getChildAt(currentIndex - 1);
            if (prevChild != null) {
                float prevCenter = prevChild.getTop() + prevChild.getTranslationY() + (prevChild.getHeight() / 2f);
                if (draggedCenter < prevCenter) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    int offset = prevChild.getTop() - draggedChild.getTop();
                    removeView(draggedChild);
                    addView(draggedChild, currentIndex - 1);
                    dragStartY += offset;
                    draggedChild.setTranslationY(draggedChild.getTranslationY() - offset);
                    return;
                }
            }
        }

        // Check Swap Downward
        if (currentIndex < getChildCount() - 1) {
            View nextChild = getChildAt(currentIndex + 1);
            if (nextChild != null) {
                float nextCenter = nextChild.getTop() + nextChild.getTranslationY() + (nextChild.getHeight() / 2f);
                if (draggedCenter > nextCenter) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    int offset = nextChild.getTop() - draggedChild.getTop();
                    removeView(draggedChild);
                    addView(draggedChild, currentIndex + 1);
                    dragStartY += offset;
                    draggedChild.setTranslationY(draggedChild.getTranslationY() - offset);
                }
            }
        }
    }

    private void checkAutoScroll(float rawY) {
        ScrollView sv = getParentScrollView();
        if (sv == null) return;

        int[] loc = new int[2];
        sv.getLocationOnScreen(loc);
        int top = loc[1];
        int bottom = top + sv.getHeight();
        int zone = (int) dpToPx(75);

        if (rawY < top + zone) {
            float ratio = (top + zone - rawY) / zone;
            int scroll = -(int) (14 * ratio + 3);
            sv.smoothScrollBy(0, scroll);
        } else if (rawY > bottom - zone) {
            float ratio = (rawY - (bottom - zone)) / zone;
            int scroll = (int) (14 * ratio + 3);
            sv.smoothScrollBy(0, scroll);
        }
    }

    private void finishDragging() {
        if (draggedChild != null) {
            final View view = draggedChild;
            view.animate()
                    .translationY(0)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(160)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            view.setTranslationZ(0f);
                            int pl = view.getPaddingLeft();
                            int pt = view.getPaddingTop();
                            int pr = view.getPaddingRight();
                            int pb = view.getPaddingBottom();
                            view.setBackgroundResource(R.drawable.card_surface);
                            view.setPadding(pl, pt, pr, pb);
                        }
                    })
                    .start();

            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }

        isDragging = false;
        draggedChild = null;
        candidateChild = null;

        // Re-enable parent scrolling and pull-to-refresh
        ScrollView sv = getParentScrollView();
        if (sv != null) sv.requestDisallowInterceptTouchEvent(false);

        PullToRefreshLayout ptr = getParentPullToRefresh();
        if (ptr != null) ptr.setEnabled(true);

        // Notify order change
        if (orderChangedListener != null) {
            List<String> reorderedUuids = new ArrayList<>();
            final int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof String) {
                    reorderedUuids.add((String) tag);
                }
            }
            orderChangedListener.onOrderChanged(reorderedUuids);
        }
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
