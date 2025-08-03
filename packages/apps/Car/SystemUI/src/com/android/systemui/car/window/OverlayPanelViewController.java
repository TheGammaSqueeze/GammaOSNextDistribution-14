/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.car.window;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.IntDef;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.CallSuper;

import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.wm.shell.animation.FlingAnimationUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The {@link OverlayPanelViewController} provides additional dragging animation capabilities to
 * {@link OverlayViewController}.
 */
public abstract class OverlayPanelViewController extends OverlayViewController {

    /** @hide */
    @IntDef(flag = true, prefix = { "OVERLAY_" }, value = {
            OVERLAY_FROM_TOP_BAR,
            OVERLAY_FROM_BOTTOM_BAR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OverlayDirection {}

    /**
     * Indicates that the overlay panel should be opened from the top bar and expanded by dragging
     * towards the bottom bar.
     */
    public static final int OVERLAY_FROM_TOP_BAR = 0;

    /**
     * Indicates that the overlay panel should be opened from the bottom bar and expanded by
     * dragging towards the top bar.
     */
    public static final int OVERLAY_FROM_BOTTOM_BAR = 1;

    private static final boolean DEBUG = false;
    private static final String TAG = "OverlayPanelViewController";

    // used to calculate how fast to open or close the window
    protected static final float DEFAULT_FLING_VELOCITY = 0;
    // max time a fling animation takes
    protected static final float FLING_ANIMATION_MAX_TIME = 0.5f;
    // acceleration rate for the fling animation
    protected static final float FLING_SPEED_UP_FACTOR = 0.6f;

    protected static final int SWIPE_DOWN_MIN_DISTANCE = 25;
    protected static final int SWIPE_MAX_OFF_PATH = 75;
    protected static final int SWIPE_THRESHOLD_VELOCITY = 200;
    private static final int POSITIVE_DIRECTION = 1;
    private static final int NEGATIVE_DIRECTION = -1;

    private final Context mContext;
    private final int mScreenHeightPx;
    private final FlingAnimationUtils mFlingAnimationUtils;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final View.OnTouchListener mDragOpenTouchListener;
    private final View.OnTouchListener mDragCloseTouchListener;

    protected int mAnimateDirection = POSITIVE_DIRECTION;

    private int mSettleClosePercentage;
    private int mPercentageFromEndingEdge;
    private int mPercentageCursorPositionOnScreen;

    private boolean mPanelVisible;
    private boolean mPanelExpanded;

    protected float mOpeningVelocity = DEFAULT_FLING_VELOCITY;
    protected float mClosingVelocity = DEFAULT_FLING_VELOCITY;

    protected boolean mIsAnimating;
    private boolean mIsTracking;

    public OverlayPanelViewController(
            Context context,
            @Main Resources resources,
            int stubId,
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            FlingAnimationUtils.Builder flingAnimationUtilsBuilder,
            CarDeviceProvisionedController carDeviceProvisionedController
    ) {
        super(stubId, overlayViewGlobalStateController);

        mContext = context;
        mScreenHeightPx = Resources.getSystem().getDisplayMetrics().heightPixels;
        mFlingAnimationUtils = flingAnimationUtilsBuilder
                .setMaxLengthSeconds(FLING_ANIMATION_MAX_TIME)
                .setSpeedUpFactor(FLING_SPEED_UP_FACTOR)
                .build();
        mCarDeviceProvisionedController = carDeviceProvisionedController;

        // Attached to a navigation bar to open the overlay panel
        GestureDetector openGestureDetector = new GestureDetector(context,
                new OpenGestureListener() {
                    @Override
                    protected void open() {
                        animateExpandPanel();
                    }
                });

        // Attached to the other navigation bars to close the overlay panel
        GestureDetector closeGestureDetector = new GestureDetector(context,
                new SystemBarCloseGestureListener() {
                    @Override
                    protected void close() {
                        if (isPanelExpanded()) {
                            animateCollapsePanel();
                        }
                    }
                });

        mDragOpenTouchListener = (v, event) -> {
            if (!shouldAnimateExpandPanel()) {
                return true;
            }
            if (!mCarDeviceProvisionedController.isCurrentUserFullySetup()) {
                return true;
            }
            if (!isInflated()) {
                getOverlayViewGlobalStateController().inflateView(this);
            }

            boolean consumed = openGestureDetector.onTouchEvent(event);
            if (consumed) {
                return true;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP) {
                maybeCompleteAnimation(event);
            }

            return true;
        };

        mDragCloseTouchListener = (v, event) -> {
            if (!isInflated()) {
                return true;
            }
            boolean consumed = closeGestureDetector.onTouchEvent(event);
            if (consumed) {
                return true;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP) {
                maybeCompleteAnimation(event);
            }
            return true;
        };
    }

    @Override
    protected void onFinishInflate() {
        setUpHandleBar();
    }

    /** Sets the overlay panel animation direction along the x or y axis. */
    public void setOverlayDirection(@OverlayDirection int direction) {
        if (direction == OVERLAY_FROM_TOP_BAR) {
            mAnimateDirection = POSITIVE_DIRECTION;
        } else if (direction == OVERLAY_FROM_BOTTOM_BAR) {
            mAnimateDirection = NEGATIVE_DIRECTION;
        } else {
            throw new IllegalArgumentException("Direction not supported");
        }
    }

    /** Toggles the visibility of the panel. */
    public void toggle() {
        if (!isInflated()) {
            getOverlayViewGlobalStateController().inflateView(this);
        }
        if (isPanelExpanded()) {
            animateCollapsePanel();
        } else {
            animateExpandPanel();
        }
    }

    /** Checks if a {@link MotionEvent} is an action to open the panel.
     * @param e {@link MotionEvent} to check.
     * @return true only if opening action.
     */
    protected boolean isOpeningAction(MotionEvent e) {
        if (isOverlayFromTopBar()) {
            return e.getActionMasked() == MotionEvent.ACTION_DOWN;
        }

        if (isOverlayFromBottomBar()) {
            return e.getActionMasked() == MotionEvent.ACTION_UP;
        }

        return false;
    }

    /** Checks if a {@link MotionEvent} is an action to close the panel.
     * @param e {@link MotionEvent} to check.
     * @return true only if closing action.
     */
    protected boolean isClosingAction(MotionEvent e) {
        if (isOverlayFromTopBar()) {
            return e.getActionMasked() == MotionEvent.ACTION_UP;
        }

        if (isOverlayFromBottomBar()) {
            return e.getActionMasked() == MotionEvent.ACTION_DOWN;
        }

        return false;
    }

    /* ***************************************************************************************** *
     * Panel Animation
     * ***************************************************************************************** */

    /** Animates the closing of the panel. */
    protected void animateCollapsePanel() {
        if (!shouldAnimateCollapsePanel()) {
            return;
        }

        if (!isPanelExpanded() && !isPanelVisible()) {
            return;
        }

        onAnimateCollapsePanel();
        animatePanel(mClosingVelocity, /* isClosing= */ true);
    }

    /** Determines whether {@link #animateCollapsePanel()} should collapse the panel. */
    protected abstract boolean shouldAnimateCollapsePanel();

    /** Called when the panel is beginning to collapse. */
    protected abstract void onAnimateCollapsePanel();

    /** Animates the expansion of the panel. */
    protected void animateExpandPanel() {
        if (!shouldAnimateExpandPanel()) {
            return;
        }

        if (!mCarDeviceProvisionedController.isCurrentUserFullySetup()) {
            return;
        }

        onAnimateExpandPanel();
        setPanelVisible(true);
        animatePanel(mOpeningVelocity, /* isClosing= */ false);

        setPanelExpanded(true);
    }

    /** Determines whether {@link #animateExpandPanel()}} should expand the panel. */
    protected abstract boolean shouldAnimateExpandPanel();

    /** Called when the panel is beginning to expand. */
    protected abstract void onAnimateExpandPanel();

    /** Returns the percentage at which we've determined whether to open or close the panel. */
    protected abstract int getSettleClosePercentage();

    /**
     * Depending on certain conditions, determines whether to fully expand or collapse the panel.
     */
    protected void maybeCompleteAnimation(MotionEvent event) {
        if (isPanelVisible()) {
            if (mSettleClosePercentage == 0) {
                mSettleClosePercentage = getSettleClosePercentage();
            }

            boolean closePanel = isOverlayFromTopBar()
                    ? mSettleClosePercentage > mPercentageCursorPositionOnScreen
                    : mSettleClosePercentage < mPercentageCursorPositionOnScreen;
            animatePanel(DEFAULT_FLING_VELOCITY, closePanel);
        }
    }

    /**
     * Animates the panel from one position to other. This is used to either open or
     * close the panel completely with a velocity. If the animation is to close the
     * panel this method also makes the view invisible after animation ends.
     */
    protected void animatePanel(float velocity, boolean isClosing) {
        float to = getEndPosition(isClosing);

        Rect rect = getLayout().getClipBounds();
        if (rect != null) {
            float from = getCurrentStartPosition(rect);
            if (from != to) {
                animate(from, to, velocity, isClosing);
            } else if (isClosing) {
                resetPanelVisibility();
            } else if (!mIsAnimating && !mPanelExpanded) {
                // This case can happen when the touch ends in the navigation bar.
                // It is important to check for mIsAnimation, because sometime a closing animation
                // starts and the following calls will grey out the navigation bar for a sec, this
                // looks awful ;)
                onExpandAnimationEnd();
                setPanelExpanded(true);
            }

            // If we swipe down the notification panel all the way to the bottom of the screen
            // (i.e. from == to), then we have finished animating the panel.
            return;
        }

        // We will only be here if the shade is being opened programmatically or via button when
        // height of the layout was not calculated.
        ViewTreeObserver panelTreeObserver = getLayout().getViewTreeObserver();
        panelTreeObserver.addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        ViewTreeObserver obs = getLayout().getViewTreeObserver();
                        obs.removeOnGlobalLayoutListener(this);
                        animate(
                                getDefaultStartPosition(),
                                getEndPosition(/* isClosing= */ false),
                                velocity,
                                isClosing
                        );
                    }
                });
    }

    /* Returns the start position if the user has not started swiping. */
    private int getDefaultStartPosition() {
        return isOverlayFromTopBar() ? 0 : getLayout().getHeight();
    }

    /** Returns the start position if we are in the middle of swiping. */
    protected int getCurrentStartPosition(Rect clipBounds) {
        return isOverlayFromTopBar() ? clipBounds.bottom : clipBounds.top;
    }

    private int getEndPosition(boolean isClosing) {
        return (isOverlayFromTopBar() && !isClosing) || (isOverlayFromBottomBar() && isClosing)
                ? getLayout().getHeight()
                : 0;
    }

    protected void animate(float from, float to, float velocity, boolean isClosing) {
        if (mIsAnimating) {
            return;
        }
        mIsAnimating = true;
        mIsTracking = true;
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.addUpdateListener(
                animation -> {
                    float animatedValue = (Float) animation.getAnimatedValue();
                    setViewClipBounds((int) animatedValue);
                });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mIsAnimating = false;
                mIsTracking = false;
                mOpeningVelocity = DEFAULT_FLING_VELOCITY;
                mClosingVelocity = DEFAULT_FLING_VELOCITY;
                if (isClosing) {
                    resetPanelVisibility();
                } else {
                    onExpandAnimationEnd();
                    setPanelExpanded(true);
                }
            }
        });
        getFlingAnimationUtils().apply(animator, from, to, Math.abs(velocity));
        animator.start();
    }

    protected void resetPanelVisibility() {
        setPanelVisible(false);
        getLayout().setClipBounds(null);
        onCollapseAnimationEnd();
        setPanelExpanded(false);
    }

    /**
     * Called in {@link Animator.AnimatorListener#onAnimationEnd(Animator)} when the panel is
     * closing.
     */
    protected abstract void onCollapseAnimationEnd();

    /**
     * Called in {@link Animator.AnimatorListener#onAnimationEnd(Animator)} when the panel is
     * opening.
     */
    protected abstract void onExpandAnimationEnd();

    /* ***************************************************************************************** *
     * Panel Visibility
     * ***************************************************************************************** */

    /** Set the panel view to be visible. */
    protected final void setPanelVisible(boolean visible) {
        mPanelVisible = visible;
        onPanelVisible(visible);
    }

    /** Returns {@code true} if panel is visible. */
    public final boolean isPanelVisible() {
        return mPanelVisible;
    }

    /** Business logic run when panel visibility is set. */
    @CallSuper
    protected void onPanelVisible(boolean visible) {
        if (DEBUG) {
            Log.e(TAG, "onPanelVisible: " + visible);
        }

        if (visible) {
            getOverlayViewGlobalStateController().showView(/* panelViewController= */ this);
        }
        else if (getOverlayViewGlobalStateController().isWindowVisible()) {
            getOverlayViewGlobalStateController().hideView(/* panelViewController= */ this);
        }
        getLayout().setVisibility(visible ? View.VISIBLE : View.INVISIBLE);

        // TODO(b/202890142): Unify OverlayPanelViewController with super class show and hide
        for (OverlayViewStateListener l : mViewStateListeners) {
            l.onVisibilityChanged(visible);
        }
    }

    /* ***************************************************************************************** *
     * Panel Expansion
     * ***************************************************************************************** */

    /**
     * Set the panel state to expanded. This will expand or collapse the overlay window if
     * necessary.
     */
    protected final void setPanelExpanded(boolean expand) {
        mPanelExpanded = expand;
        onPanelExpanded(expand);
    }

    /** Returns {@code true} if panel is expanded. */
    public final boolean isPanelExpanded() {
        return mPanelExpanded;
    }

    @CallSuper
    protected void onPanelExpanded(boolean expand) {
        if (DEBUG) {
            Log.e(TAG, "onPanelExpanded: " + expand);
        }
    }

    /* ***************************************************************************************** *
     * Misc
     * ***************************************************************************************** */

    /**
     * Given the position of the pointer dragging the panel, return the percentage of its closeness
     * to the ending edge.
     */
    protected void calculatePercentageFromEndingEdge(float y) {
        if (getLayout().getHeight() > 0) {
            float height = getVisiblePanelHeight(y);
            mPercentageFromEndingEdge = Math.round(
                    Math.abs(height / getLayout().getHeight() * 100));
        }
    }

    /**
     * Given the position of the pointer dragging the panel, update its vertical position in terms
     * of the percentage of the total height of the screen.
     */
    protected void calculatePercentageCursorPositionOnScreen(float y) {
        mPercentageCursorPositionOnScreen = Math.round(Math.abs(y / mScreenHeightPx * 100));
    }

    private float getVisiblePanelHeight(float y) {
        return isOverlayFromTopBar() ? y : getLayout().getHeight() - y;
    }

    /** Sets the boundaries of the overlay panel that can be seen based on pointer position. */
    protected void setViewClipBounds(int y) {
        // Bound the pointer position to be within the overlay panel.
        y = Math.max(0, Math.min(y, getLayout().getHeight()));
        Rect clipBounds = new Rect();
        int top, bottom;
        if (isOverlayFromTopBar()) {
            top = 0;
            bottom = y;
        } else {
            top = y;
            bottom = getLayout().getHeight();
        }
        clipBounds.set(0, top, getLayout().getWidth(), bottom);
        getLayout().setClipBounds(clipBounds);
        onScroll(y);
    }

    /**
     * Called while scrolling, this passes the position of the clip boundary that is currently
     * changing.
     */
    protected void onScroll(int y) {
        if (getHandleBarViewId() == null) return;
        View handleBar = getLayout().findViewById(getHandleBarViewId());
        if (handleBar == null) return;

        int handleBarPos = y;
        if (isOverlayFromTopBar()) {
            // For top-down panels, shift the handle bar up by its height to make space such that
            // it is aligned to the bottom of the visible overlay area.
            handleBarPos = Math.max(0, y - handleBar.getHeight());
        }
        handleBar.setTranslationY(handleBarPos);
    }

    /* ***************************************************************************************** *
     * Getters
     * ***************************************************************************************** */

    /** Returns the open touch listener. */
    public final View.OnTouchListener getDragOpenTouchListener() {
        return mDragOpenTouchListener;
    }

    /** Returns the close touch listener. */
    public final View.OnTouchListener getDragCloseTouchListener() {
        return mDragCloseTouchListener;
    }

    /** Gets the fling animation utils used for animating this panel. */
    protected final FlingAnimationUtils getFlingAnimationUtils() {
        return mFlingAnimationUtils;
    }

    /** Returns {@code true} if the panel is currently tracking. */
    protected final boolean isTracking() {
        return mIsTracking;
    }

    /** Sets whether the panel is currently tracking or not. */
    protected final void setIsTracking(boolean isTracking) {
        mIsTracking = isTracking;
    }

    /** Returns {@code true} if the panel is currently animating. */
    protected final boolean isAnimating() {
        return mIsAnimating;
    }

    /** Returns the percentage of the panel that is open from the bottom. */
    protected final int getPercentageFromEndingEdge() {
        return mPercentageFromEndingEdge;
    }

    private boolean isOverlayFromTopBar() {
        return mAnimateDirection == POSITIVE_DIRECTION;
    }

    private boolean isOverlayFromBottomBar() {
        return mAnimateDirection == NEGATIVE_DIRECTION;
    }

    /* ***************************************************************************************** *
     * Gesture Listeners
     * ***************************************************************************************** */

    /** Called when the user is beginning to scroll down the panel. */
    protected abstract void onOpenScrollStart();

    /**
     * Only responsible for open hooks. Since once the panel opens it covers all elements
     * there is no need to merge with close.
     */
    protected abstract class OpenGestureListener extends
            GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX,
                float distanceY) {

            if (!isPanelVisible()) {
                onOpenScrollStart();
            }
            setPanelVisible(true);

            // clips the view for the panel when the user scrolls to open.
            setViewClipBounds((int) event2.getRawY());

            // Initially the scroll starts with height being zero. This checks protects from divide
            // by zero error.
            calculatePercentageFromEndingEdge(event2.getRawY());
            calculatePercentageCursorPositionOnScreen(event2.getRawY());

            mIsTracking = true;
            return true;
        }


        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                float velocityX, float velocityY) {
            if (mAnimateDirection * velocityY > SWIPE_THRESHOLD_VELOCITY) {
                mOpeningVelocity = velocityY;
                open();
                return true;
            }
            animatePanel(DEFAULT_FLING_VELOCITY, true);

            return false;
        }

        protected abstract void open();
    }

    /** Determines whether the scroll event should allow closing of the panel. */
    protected abstract boolean shouldAllowClosingScroll();

    protected abstract class CloseGestureListener extends
            GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp(MotionEvent motionEvent) {
            if (isPanelExpanded()) {
                animatePanel(DEFAULT_FLING_VELOCITY, true);
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX,
                float distanceY) {
            if (!shouldAllowClosingScroll()) {
                return false;
            }
            float y = getYPositionOfPanelEndingEdge(event1, event2);
            if (getLayout().getHeight() > 0) {
                mPercentageFromEndingEdge = (int) Math.abs(
                        y / getLayout().getHeight() * 100);
                mPercentageCursorPositionOnScreen = (int) Math.abs(y / mScreenHeightPx * 100);
                boolean isInClosingDirection = mAnimateDirection * distanceY > 0;

                // This check is to figure out if onScroll was called while swiping the card at
                // bottom of the panel. At that time we should not allow panel to
                // close. We are also checking for the upwards swipe gesture here because it is
                // possible if a user is closing the panel and while swiping starts
                // to open again but does not fling. At that time we should allow the
                // panel to close fully or else it would stuck in between.
                if (Math.abs(getLayout().getHeight() - y)
                        > SWIPE_DOWN_MIN_DISTANCE && isInClosingDirection) {
                    setViewClipBounds((int) y);
                    mIsTracking = true;
                } else if (!isInClosingDirection) {
                    setViewClipBounds((int) y);
                }
            }
            // if we return true the items in RV won't be scrollable.
            return false;
        }

        /**
         * To prevent the jump in the clip bounds while closing the panel we should calculate the y
         * position using the diff of event1 and event2. This will help the panel clip smoothly as
         * the event2 value changes while event1 value will be fixed.
         * @param event1 MotionEvent that contains the position of where the event2 started.
         * @param event2 MotionEvent that contains the position of where the user has scrolled to
         *               on the screen.
         */
        private float getYPositionOfPanelEndingEdge(MotionEvent event1, MotionEvent event2) {
            float diff = mAnimateDirection * (event1.getRawY() - event2.getRawY());
            float y = isOverlayFromTopBar() ? getLayout().getHeight() - diff : diff;
            y = Math.max(0, Math.min(y, getLayout().getHeight()));
            return y;
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                float velocityX, float velocityY) {
            // should not fling if the touch does not start when view is at the end of the list.
            if (!shouldAllowClosingScroll()) {
                return false;
            }
            if (Math.abs(event1.getX() - event2.getX()) > SWIPE_MAX_OFF_PATH
                    || Math.abs(velocityY) < SWIPE_THRESHOLD_VELOCITY) {
                // swipe was not vertical or was not fast enough
                return false;
            }
            boolean isInClosingDirection = mAnimateDirection * velocityY < 0;
            if (isInClosingDirection) {
                close();
                return true;
            } else {
                // we should close the shade
                animatePanel(velocityY, false);
            }
            return false;
        }

        protected abstract void close();
    }

    protected abstract class SystemBarCloseGestureListener extends CloseGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            mClosingVelocity = DEFAULT_FLING_VELOCITY;
            if (isPanelExpanded()) {
                close();
            }
            return super.onSingleTapUp(e);
        }

        @Override
        public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX,
                float distanceY) {
            calculatePercentageFromEndingEdge(event2.getRawY());
            calculatePercentageCursorPositionOnScreen(event2.getRawY());
            setViewClipBounds((int) event2.getRawY());
            return true;
        }
    }

    /**
     * Optionally returns the ID of the handle bar view which enables dragging the panel to close
     * it. Return null if no handle bar is to be set up.
     */
    protected Integer getHandleBarViewId() {
        return null;
    };

    protected void setUpHandleBar() {
        Integer handleBarViewId = getHandleBarViewId();
        if (handleBarViewId == null) return;
        View handleBar = getLayout().findViewById(handleBarViewId);
        if (handleBar == null) return;
        GestureDetector handleBarCloseGestureDetector =
                new GestureDetector(mContext, new HandleBarCloseGestureListener());
        handleBar.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_UP:
                    maybeCompleteAnimation(event);
                    // Intentionally not breaking here, since handleBarClosureGestureDetector's
                    // onTouchEvent should still be called with MotionEvent.ACTION_UP.
                default:
                    handleBarCloseGestureDetector.onTouchEvent(event);
                    return true;
            }
        });
    }

    /**
     * A GestureListener to be installed on the handle bar.
     */
    private class HandleBarCloseGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX,
                float distanceY) {
            calculatePercentageFromEndingEdge(event2.getRawY());
            calculatePercentageCursorPositionOnScreen(event2.getRawY());
            // To prevent the jump in the clip bounds while closing the notification panel using
            // the handle bar, we should calculate the height using the diff of event1 and event2.
            // This will help the notification shade to clip smoothly as the event2 value changes
            // as event1 value will be fixed.
            float diff = mAnimateDirection * (event1.getRawY() - event2.getRawY());
            float y = isOverlayFromTopBar()
                    ? getLayout().getHeight() - diff
                    : diff;
            // Ensure the position is within the overlay panel.
            y = Math.max(0, Math.min(y, getLayout().getHeight()));
            setViewClipBounds((int) y);
            return true;
        }
    }
}
