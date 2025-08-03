/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.notification;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;

import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.TimeUnit;

/**
 * OnTouchListener that enables swipe-to-dismiss gesture on heads-up notifications.
 */
class HeadsUpNotificationOnTouchListener implements View.OnTouchListener {
    // todo(b/301474982): converge common logic in this and CarNotificationItemTouchListener class.
    private static final int INITIAL_TRANSLATION_X = 0;
    private static final int INITIAL_TRANSLATION_Y = 0;
    private static final float MAXIMUM_ALPHA = 1f;
    private static final float MINIMUM_ALPHA = 0f;
    /**
     * Factor by which view's alpha decreases based on the translation in the direction of dismiss.
     * Example: If set to 1f, the view will be invisible when it has translated the maximum possible
     * translation, similarly for 2f, view will be invisible halfway.
     */
    private static final float ALPHA_FADE_FACTOR_MULTIPLIER = 2f;

    /**
     * The unit of velocity in milliseconds. A value of 1 means "pixels per millisecond",
     * 1000 means "pixels per 1000 milliseconds (1 second)".
     */
    private static final int PIXELS_PER_SECOND = (int) TimeUnit.SECONDS.toMillis(1);
    private final View mView;
    private final DismissCallbacks mCallbacks;
    private final Axis mDismissAxis;
    /**
     * Distance a touch can wander before we think the user is scrolling in pixels.
     */
    private final int mTouchSlop;
    private final boolean mDismissOnSwipe;
    /**
     * The proportion which view has to be swiped before it dismisses.
     */
    private final float mPercentageOfMaxTransaltionToDismiss;
    /**
     * The minimum velocity in pixel per second the swipe gesture to initiate a dismiss action.
     */
    private final int mMinimumFlingVelocity;
    /**
     * The cap on velocity in pixel per second a swipe gesture is calculated to have.
     */
    private final int mMaximumFlingVelocity;
    /**
     * The transaltion that a view can have. To set change value of
     * {@code R.dimen.max_translation_headsup} to a non zero value. If set to zero, the view's
     * dimensions(height/width) will be used instead.
     */
    private float mMaxTranslation;
    /**
     * Distance by which a view should be translated by to be considered dismissed. Can be
     * configured by setting {@code R.dimen.percentage_of_max_translation_to_dismiss}
     */
    private float mDismissDelta;
    private VelocityTracker mVelocityTracker;
    private float mDownX;
    private float mDownY;
    private boolean mSwiping;
    private int mSwipingSlop;
    private float mTranslation;

    /**
     * The callback indicating the supplied view has been dismissed.
     */
    interface DismissCallbacks {
        void onDismiss();
    }

    private enum Axis {
        HORIZONTAL, VERTICAL;

        public Axis getOppositeAxis() {
            switch (this) {
                case VERTICAL:
                    return HORIZONTAL;
                default:
                    return VERTICAL;
            }
        }
    }

    HeadsUpNotificationOnTouchListener(View view, boolean dismissOnSwipe,
            DismissCallbacks callbacks) {
        mView = view;
        mCallbacks = callbacks;
        mDismissOnSwipe = dismissOnSwipe;
        Resources res = view.getContext().getResources();
        mDismissAxis = res.getBoolean(R.bool.config_isHeadsUpNotificationDismissibleVertically)
                ? Axis.VERTICAL : Axis.HORIZONTAL;
        mTouchSlop = res.getDimensionPixelSize(R.dimen.touch_slop);
        mPercentageOfMaxTransaltionToDismiss =
                res.getFloat(R.dimen.percentage_of_max_translation_to_dismiss);
        mMaxTranslation = res.getDimension(R.dimen.max_translation_headsup);
        if (mMaxTranslation != 0) {
            mDismissDelta = mMaxTranslation * mPercentageOfMaxTransaltionToDismiss;
        } else {
            mView.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            mView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            if (mDismissAxis == Axis.VERTICAL) {
                                mMaxTranslation = view.getHeight();
                            } else {
                                mMaxTranslation = view.getWidth();
                            }
                            mDismissDelta = mMaxTranslation * mPercentageOfMaxTransaltionToDismiss;
                        }
                    });
        }
        ViewConfiguration viewConfiguration = ViewConfiguration.get(view.getContext());
        mMaximumFlingVelocity = viewConfiguration.getScaledMaximumFlingVelocity();
        mMinimumFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (mDismissAxis == Axis.VERTICAL) {
            motionEvent.offsetLocation(INITIAL_TRANSLATION_X, /* deltaY= */ mTranslation);
        } else {
            motionEvent.offsetLocation(/* deltaX= */ mTranslation, INITIAL_TRANSLATION_Y);
        }

        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mDownX = motionEvent.getRawX();
                mDownY = motionEvent.getRawY();
                mVelocityTracker = obtainVelocityTracker();
                mVelocityTracker.addMovement(motionEvent);
                break;
            }

            case MotionEvent.ACTION_UP: {
                if (mVelocityTracker == null) {
                    return false;
                }

                mVelocityTracker.addMovement(motionEvent);
                mVelocityTracker.computeCurrentVelocity(PIXELS_PER_SECOND, mMaximumFlingVelocity);
                float deltaInDismissAxis =
                        getDeltaInAxis(mDownX, mDownY, motionEvent, mDismissAxis);
                boolean shouldBeDismissed = false;
                boolean dismissInPositiveDirection = false;
                if (Math.abs(deltaInDismissAxis) > mDismissDelta) {
                    // dismiss when the movement is more than the defined threshold.
                    shouldBeDismissed = true;
                    dismissInPositiveDirection = deltaInDismissAxis > 0;
                } else if (mSwiping && isFlingEnoughForDismiss(mVelocityTracker, mDismissAxis)
                        && isFlingInSameDirectionAsDelta(
                                deltaInDismissAxis, mVelocityTracker, mDismissAxis)) {
                    // dismiss when the velocity is more than the defined threshold.
                    // dismiss only if flinging in the same direction as dragging.
                    shouldBeDismissed = true;
                    dismissInPositiveDirection =
                            getVelocityInAxis(mVelocityTracker, mDismissAxis) > 0;
                }

                if (shouldBeDismissed && mDismissOnSwipe) {
                    mCallbacks.onDismiss();
                    animateDismissInAxis(mView, mDismissAxis, dismissInPositiveDirection);
                } else if (mSwiping) {
                    animateToCenter();
                }
                reset();
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (mVelocityTracker == null) {
                    return false;
                }
                animateToCenter();
                reset();
                return false;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mVelocityTracker == null) {
                    return false;
                }

                mVelocityTracker.addMovement(motionEvent);
                float deltaInDismissAxis =
                        getDeltaInAxis(mDownX, mDownY, motionEvent, mDismissAxis);
                if (Math.abs(deltaInDismissAxis) > mTouchSlop) {
                    mSwiping = true;
                    mSwipingSlop = (deltaInDismissAxis > 0 ? mTouchSlop : -mTouchSlop);
                    disallowAndCancelTouchEvents(mView, motionEvent);
                }

                if (mSwiping) {
                    mTranslation = deltaInDismissAxis;
                    moveView(mView,
                            /* translation= */ deltaInDismissAxis - mSwipingSlop, mDismissAxis);
                    if (mDismissOnSwipe) {
                        mView.setAlpha(getAlphaForDismissingView(mTranslation, mMaxTranslation));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void animateToCenter() {
        mView.animate()
                .translationX(INITIAL_TRANSLATION_X)
                .translationY(INITIAL_TRANSLATION_Y)
                .alpha(MAXIMUM_ALPHA)
                .setListener(null);
    }

    private void reset() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
        }
        mVelocityTracker = null;
        mTranslation = 0;
        mDownX = 0;
        mDownY = 0;
        mSwiping = false;
    }

    private void resetView(View view) {
        view.setTranslationX(INITIAL_TRANSLATION_X);
        view.setTranslationY(INITIAL_TRANSLATION_Y);
        view.setAlpha(MAXIMUM_ALPHA);
    }

    private float getDeltaInAxis(
            float downX, float downY, MotionEvent motionEvent, Axis dismissAxis) {
        switch (dismissAxis) {
            case VERTICAL:
                return motionEvent.getRawY() - downY;
            default:
                return motionEvent.getRawX() - downX;
        }
    }

    private void disallowAndCancelTouchEvents(View view, MotionEvent motionEvent) {
        view.getParent().requestDisallowInterceptTouchEvent(true);

        // prevent onClickListener being triggered when moving.
        MotionEvent cancelEvent = obtainMotionEvent(motionEvent);
        cancelEvent.setAction(MotionEvent.ACTION_CANCEL
                | (motionEvent.getActionIndex()
                << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
        view.onTouchEvent(cancelEvent);
        cancelEvent.recycle();
    }

    private void moveView(View view, float translation, Axis dismissAxis) {
        if (dismissAxis == Axis.VERTICAL) {
            view.setTranslationY(translation);
        } else {
            view.setTranslationX(translation);
        }
    }

    private float getAlphaForDismissingView(float translation, float maxTranslation) {
        float fractionMoved = Math.abs(translation) / Math.abs(maxTranslation);
        // min is required to avoid value greater than MAXIMUM_ALPHA
        float alphaBasedOnTranslation = Math.min(MAXIMUM_ALPHA,
                MAXIMUM_ALPHA - (ALPHA_FADE_FACTOR_MULTIPLIER * fractionMoved));
        // max is required to avoid alpha values less than min
        return Math.max(MINIMUM_ALPHA, alphaBasedOnTranslation);
    }

    private boolean isFlingEnoughForDismiss(VelocityTracker velocityTracker, Axis axis) {
        float velocityInDismissingDirection = getVelocityInAxis(velocityTracker, axis);
        float velocityInOppositeDirection =
                getVelocityInAxis(velocityTracker, axis.getOppositeAxis());
        boolean isMoreFlingInDismissAxis =
                Math.abs(velocityInDismissingDirection) > Math.abs(velocityInOppositeDirection);
        return mMinimumFlingVelocity <= Math.abs(velocityInDismissingDirection)
                && isMoreFlingInDismissAxis;
    }

    private float getVelocityInAxis(VelocityTracker velocityTracker, Axis axis) {
        switch (axis) {
            case VERTICAL:
                return velocityTracker.getYVelocity();
            default:
                return velocityTracker.getXVelocity();
        }
    }

    private boolean isFlingInSameDirectionAsDelta(float delta, VelocityTracker velocityTracker,
            Axis axis) {
        float velocityInDismissingDirection = getVelocityInAxis(velocityTracker, axis);
        boolean isVelocityInPositiveDirection = velocityInDismissingDirection > 0;
        boolean isDeltaInPositiveDirection = delta > 0;
        return isVelocityInPositiveDirection == isDeltaInPositiveDirection;
    }

    private void animateDismissInAxis(View view, Axis axis, boolean dismissInPositiveDirection) {
        float dismissTranslation = dismissInPositiveDirection ? mMaxTranslation : -mMaxTranslation;
        ViewPropertyAnimator animator = view.animate();
        if (axis == Axis.VERTICAL) {
            animator.translationY(dismissTranslation);
        } else {
            animator.translationX(dismissTranslation);
        }
        animator.alpha(MINIMUM_ALPHA).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                resetView(mView);
            }
        }).start();
    }

    /**
     * Should be overridden in test to not access static obtain method.
     */
    @VisibleForTesting
    MotionEvent obtainMotionEvent(MotionEvent motionEvent) {
        return MotionEvent.obtain(motionEvent);
    }

    /**
     * Should be overridden in test to not access static obtain method.
     */
    @VisibleForTesting
    VelocityTracker obtainVelocityTracker() {
        return VelocityTracker.obtain();
    }

    @VisibleForTesting
    int getMinimumFlingVelocity() {
        return mMinimumFlingVelocity;
    }

    @VisibleForTesting
    int getTouchSlop() {
        return mTouchSlop;
    }

    @VisibleForTesting
    float getPercentageOfMaxTransaltionToDismiss() {
        return mPercentageOfMaxTransaltionToDismiss;
    }
}
