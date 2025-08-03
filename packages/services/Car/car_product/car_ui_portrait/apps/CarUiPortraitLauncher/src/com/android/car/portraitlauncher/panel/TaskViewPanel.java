/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.portraitlauncher.panel;

import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_GRIP_BAR_CLICKED;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_GRIP_BAR_DRAG;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_PANEL_READY;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.createReason;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;

import com.android.car.portraitlauncher.R;
import com.android.car.portraitlauncher.panel.animation.ClosePanelAnimator;
import com.android.car.portraitlauncher.panel.animation.ExpandPanelAnimator;
import com.android.car.portraitlauncher.panel.animation.FadeInPanelAnimator;
import com.android.car.portraitlauncher.panel.animation.FadeOutPanelAnimator;
import com.android.car.portraitlauncher.panel.animation.FullScreenPanelAnimator;
import com.android.car.portraitlauncher.panel.animation.OpenPanelAnimator;
import com.android.car.portraitlauncher.panel.animation.OpenPanelWithIconAnimator;
import com.android.car.portraitlauncher.panel.animation.PanelAnimator;

/**
 * A view container used to display CarTaskViews.
 *
 * This panel can transition between various states, e.g. open, close and full screen.
 * When panel is in open state it shows a grab bar to the users which can be dragged to transition
 * to the other states.
 */
public class TaskViewPanel extends RelativeLayout {

    private static final String TAG = TaskViewPanel.class.getSimpleName();
    private static final boolean DBG = Build.IS_DEBUGGABLE;

    /** The properties of each valid state of the panel. */
    public static class State {
        /** The bounds used for the panel when put in this state. */
        Rect mBounds = new Rect();
        /** The insets used for the panel. */
        Insets mInsets = Insets.NONE;
        /** Whether or not the panel should display the grip bar. */
        private final boolean mHasGripBar;
        /** Whether the panel is visible when put in this state. */
        private final boolean mIsVisible;
        /** Whether the panel is considered full screen when put in this state. */
        private final boolean mIsFullScreen;
        /** Whether the panel should display the toolbar. */
        private boolean mHasToolBar;
        /** Whether the panel should show the background surfaceView. */
        private boolean mHasBackgroundSurfaceView;

        public State(boolean hasGripBar, boolean isVisible, boolean isFullScreen,
                boolean hasToolBar, boolean hasBackgroundSurfaceView) {
            mHasGripBar = hasGripBar;
            mIsVisible = isVisible;
            mIsFullScreen = isFullScreen;
            mHasToolBar = hasToolBar;
            mHasBackgroundSurfaceView = hasBackgroundSurfaceView;
        }

        boolean hasGripBar() {
            return mHasGripBar;
        }

        boolean hasToolBar() {
            return mHasToolBar;
        }

        boolean hasBackgroundSurfaceView() {
            return mHasBackgroundSurfaceView;
        }

        /** Whether the panel in this state has any visible parts. */
        public boolean isVisible() {
            return mIsVisible;
        }

        /** Is this state considered full screen or not. */
        public boolean isFullScreen() {
            return mIsFullScreen;
        }

        /** The string representation of the state. Used for debugging */
        public String toString() {
            return "(visible: " + isVisible() + ", fullscreen: " + isFullScreen() + ", bounds: "
                    + mBounds + ")";
        }
    }

    /** Notifies the listener when the panel state changes. */
    public interface OnStateChangeListener{
        /**
         * Called right before the panel state changes.
         *
         * @param oldState The state from which the transition is about to start.
         * @param newState The final state of the panel after the transition.
         * @param animated If the transition is animated.
         */
        void onStateChangeStart(State oldState, State newState, boolean animated);

        /**
         * Called right after the panel state changes.
         *
         * If the transition is animated, this method would be called after the animation.
         * @param oldState The state from which the transition started.
         * @param newState The final state of the panel after the transition.
         * @param animated If the transition is animated.
         */
        void onStateChangeEnd(State oldState, State newState, boolean animated);
    }

    private static void logIfDebuggable(String message) {
        if (DBG) {
            Log.d(TAG, message);
        }
    }

    /** The properties of the panel when in {@code open} state. */
    private final State mOpenState;
    /** The properties of the panel when in {@code close} state. */
    private final State mCloseState;
    /** The properties of the panel when in {@code full screen} state. */
    private final State mFullScreenState;

    /**
     * The current state of the panel.
     *
     * When transitioning from an state to another, this value will show the final state of the
     * animation.
     */
    private State mActiveState;

    /**
     * The current animator if there is an on-going animation.
     */
    private PanelAnimator mActiveAnimator;

    /** An optional listener to observe when the panel state changes. */
    private OnStateChangeListener mOnStateChangeListener;

    /** The drag threshold after which the panel transitions to the close mode. */
    private final int mDragThreshold;

    /** The top margin for task view panel. */
    private final int mPanelTopMargin;

    /** The grip bar used to drag the panel. */
    private GripBarView mGripBar;

    /** The toolbar on top of the panel. */
    private ToolBarView mToolBarView;

    /** Internal container of the {@code CarTaskView}. */
    private ViewGroup mTaskViewContainer;

    /** A view that is shown on top of the task view and used to improve visual effects. */
    private TaskViewPanelOverlay mTaskViewOverlay;

    /**
     * The {@code View} embedded in this panel, used to show application. This is the main
     * content of the panel, should be instance of CarTaskView or RemoteCarTaskView.
     */
    private View mTaskView;

    /** The last reported window bounds of the task view. */
    private Rect mTaskViewWindowBounds;

    /** The surface view showed on the back of the panel. */
    private BackgroundSurfaceView mBackgroundSurfaceView;

    /** The flag indicating if the task view on the panel is ready. */
    private boolean mIsReady;

    /** The current task running inside panel. */
    private TaskInfo mCurrentTask;

    public TaskViewPanel(Context context) {
        this(context, null);
    }

    public TaskViewPanel(Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskViewPanel(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskViewPanel(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mDragThreshold = (int) getResources().getDimension(R.dimen.panel_drag_threshold);
        mPanelTopMargin = (int) getResources().getDimension(R.dimen.panel_default_top_margin);

        mOpenState = new State(/* hasGripBar = */ true, /* isVisible = */ true,
                /* isFullScreen */false, /* hasToolBar = */ false,
                /* hasBackgroundSurfaceView = */ false);
        mCloseState = new State(/* hasGripBar = */ true, /* isVisible = */ false,
                /* isFullScreen */false, /* hasToolBar = */ false,
                /* hasBackgroundSurfaceView = */ false);
        mFullScreenState = new State(/* hasGripBar = */ false, /* isVisible = */ true,
                /* isFullScreen */true, /* hasToolBar = */ true,
                /* hasBackgroundSurfaceView = */ true);

        mCurrentTask = null;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mGripBar = findViewById(R.id.grip_bar);
        mToolBarView = findViewById(R.id.toolbar);
        mTaskViewContainer = findViewById(R.id.task_view_container);
        mTaskViewOverlay = findViewById(R.id.task_view_overlay);
        mBackgroundSurfaceView = findViewById(R.id.surface_view);
        mBackgroundSurfaceView.setZOrderOnTop(false);
        mActiveState = mCloseState;

        setupGripBar();
    }

    /** Whether the panel is in the open state. */
    public boolean isOpen() {
        return mActiveState == mOpenState;
    }

    /** Whether the panel is in the full screen state. */
    public boolean isFullScreen() {
        return mActiveState.isFullScreen();
    }

    /** Whether the panel is visible */
    public boolean isVisible() {
        return mActiveState.isVisible();
    }

    /** Whether the panel is actively animating. */
    public boolean isAnimating() {
        return mActiveAnimator != null;
    }

    /** Transitions the panel into the open state. */
    public void openPanel(TaskViewPanelStateChangeReason reason) {
        openPanel(/* animated= */ true, reason);
    }

    /** Transitions the panel into the open state. */
    public void openPanel(boolean animated, TaskViewPanelStateChangeReason reason) {
        PanelAnimator animator =
                animated ? new OpenPanelAnimator(this, mOpenState.mBounds) : null;
        setActiveState(mOpenState, animator, reason);
    }

    /** Transitions the panel into the open state with overlay and centered icon. */
    public void openPanelWithIcon(TaskViewPanelStateChangeReason reason) {
        PanelAnimator animator = new OpenPanelWithIconAnimator(this, mOpenState.mBounds,
                mTaskViewOverlay);
        setActiveState(mOpenState, animator, reason);
    }

    /** Transitions the panel into the close state. */
    public void closePanel(TaskViewPanelStateChangeReason reason) {
        closePanel(/* animated= */ true, reason);
    }

    /** Transitions the panel into the close state. */
    public void closePanel(boolean animated, TaskViewPanelStateChangeReason reason) {
        PanelAnimator animator =
                animated ? new ClosePanelAnimator(this, mCloseState.mBounds) : null;

        setActiveState(mCloseState, animator, reason);
    }

    /** Transitions the panel into the open state using the expand animation. */
    public void expandPanel(TaskViewPanelStateChangeReason reason) {
        Point origin = new Point(mOpenState.mBounds.centerX(), mOpenState.mBounds.centerY());
        PanelAnimator animator =
                new ExpandPanelAnimator(/* panel= */ this, origin, mOpenState.mBounds, mGripBar);
        setActiveState(mOpenState, animator, reason);
    }

    /** Transitions the panel into the open state using the fade-in animation. */
    public void fadeInPanel(TaskViewPanelStateChangeReason reason) {
        setActiveState(mOpenState,
                new FadeInPanelAnimator(/* panel= */ this, mTaskView, mOpenState.mBounds), reason);
    }

    /** Transitions the panel into the close state using the fade-out animation. */
    public void fadeOutPanel(TaskViewPanelStateChangeReason reason) {
        PanelAnimator animator = new FadeOutPanelAnimator(/* panel= */ this, mTaskViewOverlay,
                mTaskView, mCloseState.mBounds, mCloseState.mBounds.top);
        setActiveState(mCloseState, animator, reason);
    }

    /**
     * Transitions the panel into the full screen state. During
     * transition,{@link mTaskViewOverlay} shows with given {@code drawable} at the center.
     */
    public void openFullScreenPanel(boolean animated, boolean showToolBar, int bottomAdjustment,
            TaskViewPanelStateChangeReason reason) {
        mFullScreenState.mHasToolBar = showToolBar;
        mFullScreenState.mBounds.bottom = ((ViewGroup) getParent()).getHeight() - bottomAdjustment;
        setActiveState(mFullScreenState, animated ? createFullScreenPanelAnimator() : null, reason);
    }

    /** Sets the state change listener for the panel. */
    public void setOnStateChangeListener(OnStateChangeListener listener) {
        mOnStateChangeListener = listener;
    }

    /** Sets the component that {@link mTaskViewOverlay} covers */
    public void setComponentName(@NonNull ComponentName componentName) {
        mTaskViewOverlay.setComponentName(componentName);
        mGripBar.update(componentName);
    }

    /**
     * Returns the grip bar bounds of the current state.
     *
     * Note that the visual grip bar bounds might be different from the value here during
     * transition animations.
     *
     * @param bounds The {@code Rect} that is used to return the data.
     */
    public void getGripBarBounds(Rect bounds) {
        if (mActiveState.hasGripBar()) {
            bounds.set(mActiveState.mBounds);
            bounds.bottom = mActiveState.mBounds.top + mGripBar.getHeight();
        } else {
            bounds.setEmpty();
        }
    }

    /** Updates the {@code TaskView} used in the panel. */
    public void setTaskView(SurfaceView surfaceView) {
        mTaskView =  surfaceView;
        mTaskViewContainer.addView(mTaskView);
        onParentDimensionChanged();
    }

    /** Updates the readiness state of the panel. */
    public void setReady(boolean isReady) {
        mIsReady = isReady;
        if (mIsReady) {
            closePanel(createReason(ON_PANEL_READY));
        }
    }

    /** Returns whether the panel is ready. */
    public boolean isReady() {
        return mIsReady;
    }

    /** Refreshes the panel according to the given {@code Theme}. */
    public void refresh(Resources.Theme theme) {
        int backgroundColor = getResources().getColor(R.color.car_background, theme);
        mTaskViewContainer.setBackgroundColor(backgroundColor);
        mTaskViewOverlay.refresh();
        mGripBar.refresh(theme);
        mBackgroundSurfaceView.refresh(theme);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGripBar() {
        mGripBar.setOnTouchListener(new OnPanelDragListener(getContext()) {
            @Override void onClick() {
                closePanel(createReason(ON_GRIP_BAR_CLICKED));
            }

            @Override
            public void onDragBegin() {
            }

            @Override
            public void onDrag(int deltaX, int deltaY) {
                deltaY = Math.max(0, deltaY);
                Rect rect = new Rect(mActiveState.mBounds);
                rect.offset(/* dx= */ 0, deltaY);
                updateBounds(rect);
            }

            @Override
            public void onDragEnd(int deltaX, int deltaY) {
                deltaY = Math.max(0, deltaY);
                if (deltaY > mDragThreshold) {
                    closePanel(createReason(ON_GRIP_BAR_DRAG));
                } else {
                    openPanel(createReason(ON_GRIP_BAR_DRAG));
                }
            }
        });
    }

    /** Returns the bounds of the task view once fully transitioned to the active state */
    public Rect getTaskViewBounds(State state) {
        Rect bounds = new Rect(state.mBounds);
        bounds.inset(mActiveState.mInsets);

        if (state.hasGripBar()) {
            bounds.top += mGripBar.getHeight();
        }

        return bounds;
    }

    /** Updates the insets of the taskView for non-fullscreen states. */
    public void setInsets(Insets insets) {
        if (insets.equals(mOpenState.mInsets)) {
            return;
        }
        mOpenState.mInsets = insets;
        mCloseState.mInsets = insets;
        if (!isReady()) {
            return;
        }
        updateInsets(mActiveState.mInsets);
        recalculateBounds();
        updateBounds(mActiveState.mBounds);
    }

    /** Sets a fixed background color for the task view. */
    public void setTaskViewBackgroundColor(int color) {
        mBackgroundSurfaceView.setFixedColor(color);
    }

    /** Should be called when the view is no longer in use. */
    public void onDestroy() {
        mTaskView = null;
        mCurrentTask = null;
    }

    /** Should be called when the parent dimension changes. */
    public void onParentDimensionChanged() {
        int parentWidth = ((ViewGroup) getParent()).getWidth();
        int parentHeight = ((ViewGroup) getParent()).getHeight();

        logIfDebuggable("onDimensionChanged: " + parentWidth + " " + parentHeight);

        recalculateBounds();
        updateBounds(mActiveState.mBounds);
    }

    /**
     * Set Callback for {@link ToolBarView} on {@link TaskViewPanel}
     */
    public void setToolBarCallback(ToolBarView.Callback callback) {
        mToolBarView.registerToolbarCallback(callback);
    }

    /**
     * Show/hide the content in {@link ToolBarView}
     */
    public void setToolBarViewVisibility(boolean isVisible) {
        mToolBarView.updateToolBarContentVisibility(mActiveState.hasToolBar() && isVisible);
    }

    private void recalculateBounds() {
        int parentWidth = ((ViewGroup) getParent()).getWidth();
        int parentHeight = ((ViewGroup) getParent()).getHeight();

        mOpenState.mBounds.set(0, mPanelTopMargin + mGripBar.getHeight(), parentWidth,
                parentHeight);
        mCloseState.mBounds.set(0, parentHeight, parentWidth,
                parentHeight + mOpenState.mBounds.height());
        mFullScreenState.mBounds.set(0, 0, parentWidth, parentHeight);
    }

    private void setActiveState(State toState, PanelAnimator animator,
            TaskViewPanelStateChangeReason reason) {
        if (!isReady()) {
            logIfDebuggable("Skipping state change. Not Ready.");
        }

        if (toState.mBounds.height() == 0) {
            logIfDebuggable("Skipping state change. Not initialized.");
            return;
        }

        State fromState = mActiveState;
        logIfDebuggable("Panel( " + getTag() + ") active state changes from " + fromState
                + " to " + toState + " with reason code = " + reason);

        if (mActiveAnimator != null) {
            logIfDebuggable("cancelling the old animation");
            mActiveAnimator.cancel();
            mActiveAnimator = null;
            mGripBar.setVisibility(mActiveState.hasGripBar() ? VISIBLE : GONE);
            mToolBarView.setVisibility(GONE);
            mBackgroundSurfaceView.setVisibility(GONE);
        }

        boolean animated = animator != null;
        onStateChangeStart(fromState, toState, animated);

        mActiveState = toState;
        mActiveAnimator = animator;

        updateInsets(mActiveState.mInsets);

        if (animated) {
            // Change toolbar and grip bar visibilities before the animation for better animation.
            if (toState.hasToolBar() != fromState.hasToolBar()) {
                mToolBarView.setVisibility(toState.hasToolBar() ? VISIBLE : GONE);
            }

            if (toState.hasGripBar() != fromState.hasGripBar()) {
                mGripBar.setVisibility(toState.hasGripBar() ? VISIBLE : GONE);
            }

            post(() -> animator.animate(() -> {
                mGripBar.setVisibility(mActiveState.hasGripBar() ? VISIBLE : GONE);
                mToolBarView.setVisibility(mActiveState.hasToolBar() ? VISIBLE : GONE);
                mBackgroundSurfaceView.setVisibility(
                        mActiveState.hasBackgroundSurfaceView() ? VISIBLE : GONE);
                updateBounds(mActiveState.mBounds);
                onStateChangeEnd(fromState, mActiveState, /* animated= */ true);
            }));
        } else {
            mGripBar.setVisibility(mActiveState.hasGripBar() ? VISIBLE : GONE);
            mToolBarView.setVisibility(mActiveState.hasToolBar()  ? VISIBLE : GONE);
            mBackgroundSurfaceView.setVisibility(
                    mActiveState.hasBackgroundSurfaceView() ? VISIBLE : GONE);
            updateBounds(mActiveState.mBounds);
            mTaskViewOverlay.setVisibility(GONE);
            onStateChangeEnd(fromState, mActiveState, /* animated= */ false);
        }
    }

    private void onStateChangeStart(State fromState, State toState, boolean animated) {
        if (mOnStateChangeListener != null) {
            mOnStateChangeListener.onStateChangeStart(fromState, toState, animated);
        }
    }
    private void onStateChangeEnd(State fromState, State toState, boolean animated) {
        mActiveAnimator = null;
        if (mOnStateChangeListener != null) {
            mOnStateChangeListener.onStateChangeEnd(fromState, toState, animated);
        }
    }

    /** Updates bounds for {@link mTaskView}. */
    public void updateTaskViewBounds(Rect bounds) {
        mTaskViewWindowBounds = bounds;
    }

    private void updateInsets(Insets insets) {
        mTaskViewContainer.setPadding(insets.left, insets.top, insets.right, insets.bottom);
    }

    private void updateBounds(Rect bounds) {
        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        layoutParams.topMargin = bounds.top;
        layoutParams.rightMargin = bounds.right;
        layoutParams.width = bounds.width();
        layoutParams.height = bounds.height();
        setLayoutParams(layoutParams);
    }

    private FullScreenPanelAnimator createFullScreenPanelAnimator() {
        Point offset = new Point(mOpenState.mBounds.left, mOpenState.mBounds.top);
        Rect bounds = mFullScreenState.mBounds;
        return new FullScreenPanelAnimator(this, bounds, offset, mTaskViewOverlay);
    }
}
