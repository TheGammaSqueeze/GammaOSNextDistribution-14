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

package com.android.car.notification;


import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.Animator;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.testing.TestableContext;
import android.testing.TestableResources;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

@RunWith(AndroidJUnit4.class)
public class HeadsUpNotificationOnTouchListenerTest {
    private static final int TRANSLATION = 5;
    private static final int VELOCITY = 5;
    private static final int VIEW_SIZE = 20;

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext()) {
        @Override
        public Context createApplicationContext(ApplicationInfo application, int flags) {
            return this;
        }
    };
    private TestableResources mTestableResources;
    private HeadsUpNotificationOnTouchListener mHeadsUpNotificationOnTouchListener;
    @Mock
    private View mView;
    @Mock
    private ViewParent mViewParent;
    @Mock
    private ViewPropertyAnimator mViewPropertyAnimator;
    @Mock
    private ViewTreeObserver mViewTreeObserver;
    @Mock
    private MotionEvent mDownMotionEvent;
    @Mock
    private MotionEvent mMoveMotionEventLessThanTouchSlop;
    @Mock
    private MotionEvent mMoveMotionEventMoreThanTouchSlop;
    @Mock
    private MotionEvent mCancelMotionEvent;
    @Mock
    private MotionEvent mUpMotionEventMoreThanThreshold;
    @Mock
    private MotionEvent mUpMotionEventLessThanThreshold;
    @Mock
    private MotionEvent mNewMotionEvent;
    @Mock
    private VelocityTracker mVelocityTracker;
    @Mock
    private HeadsUpNotificationOnTouchListener.DismissCallbacks mDismissCallbacks;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestableResources = mContext.getOrCreateTestableResources();
        mTestableResources
                .addOverride(R.bool.config_isHeadsUpNotificationDismissibleVertically, false);
        mTestableResources.addOverride(R.dimen.max_translation_headsup, 0f);
        setUpView();
        createHeadsUpNotificationOnTouchListener();
        setUpMotionEvents();
    }

    @Test
    public void onTouch_actionCancel_viewTranslationReset() {
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mCancelMotionEvent);

        verify(mView).animate();
        verify(mViewPropertyAnimator).translationX(0);
        verify(mViewPropertyAnimator).translationY(0);
    }

    @Test
    public void onTouch_actionCancel_viewAlphaReset() {
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mCancelMotionEvent);

        verify(mView).animate();
        verify(mViewPropertyAnimator).alpha(1);
    }

    @Test
    public void onTouch_actionCancel_callbackNotSent() {
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mCancelMotionEvent);

        verify(mDismissCallbacks, times(0)).onDismiss();
    }

    @Test
    public void onTouch_actionMove_moveLessThanTouchSlop_horizontal_returnsFalse() {
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);

        boolean returnValue = mHeadsUpNotificationOnTouchListener
                .onTouch(mView, mMoveMotionEventLessThanTouchSlop);

        assertThat(returnValue).isFalse();
    }

    @Test
    public void onTouch_actionMove_moveLessThanTouchSlop_vertical_returnsFalse() {
        mTestableResources
                .addOverride(R.bool.config_isHeadsUpNotificationDismissibleVertically, true);
        createHeadsUpNotificationOnTouchListener();
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);

        boolean returnValue = mHeadsUpNotificationOnTouchListener
                .onTouch(mView, mMoveMotionEventLessThanTouchSlop);

        assertThat(returnValue).isFalse();
    }

    @Test
    public void onTouch_actionMove_moveLessThanTouchSlop_horizontal_viewNotMoved() {
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mMoveMotionEventLessThanTouchSlop);

        verify(mView, times(0)).setTranslationX(anyFloat());
    }

    @Test
    public void onTouch_actionMove_moveLessThanTouchSlop_vertical_viewNotMoved() {
        mTestableResources
                .addOverride(R.bool.config_isHeadsUpNotificationDismissibleVertically, true);
        createHeadsUpNotificationOnTouchListener();
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mMoveMotionEventLessThanTouchSlop);

        verify(mView, times(0)).setTranslationY(anyFloat());
    }

    @Test
    public void onTouch_actionMove_moveMoreThanTouchSlop_horizontal_viewTranslationSet() {
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mMoveMotionEventMoreThanTouchSlop);

        verify(mView, times(1)).setTranslationX(TRANSLATION);
        verify(mView, times(0)).setTranslationY(anyFloat());

    }

    @Test
    public void onTouch_actionMove_moveMoreThanTouchSlop_vertical_viewTranslationSet() {
        mTestableResources
                .addOverride(R.bool.config_isHeadsUpNotificationDismissibleVertically, true);
        createHeadsUpNotificationOnTouchListener();
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mMoveMotionEventMoreThanTouchSlop);

        verify(mView, times(1)).setTranslationY(TRANSLATION);
        verify(mView, times(0)).setTranslationX(anyFloat());
    }

    @Test
    public void onTouch_actionUp_moveMoreThanMin_horizontal_callbackSent() {
        when(mVelocityTracker.getXVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() - VELOCITY);
        when(mVelocityTracker.getYVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() - VELOCITY);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mUpMotionEventMoreThanThreshold);

        verify(mDismissCallbacks, times(1)).onDismiss();
    }

    @Test
    public void onTouch_actionUp_moveMoreThanMin_vertical_callbackSent() {
        mTestableResources
                .addOverride(R.bool.config_isHeadsUpNotificationDismissibleVertically, true);
        createHeadsUpNotificationOnTouchListener();
        when(mVelocityTracker.getXVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() - VELOCITY);
        when(mVelocityTracker.getYVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() - VELOCITY);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mUpMotionEventMoreThanThreshold);

        verify(mDismissCallbacks, times(1)).onDismiss();
    }

    @Test
    public void onTouch_actionUp_flingMoreThanThreshold_horizontal_callbackSent() {
        when(mVelocityTracker.getXVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() + VELOCITY);
        when(mVelocityTracker.getYVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() - VELOCITY);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mMoveMotionEventMoreThanTouchSlop);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mUpMotionEventLessThanThreshold);

        verify(mDismissCallbacks, times(1)).onDismiss();
    }

    @Test
    public void onTouch_actionUp_flingMoreThanThreshold_vertical_callbackSent() {
        mTestableResources
                .addOverride(R.bool.config_isHeadsUpNotificationDismissibleVertically, true);
        createHeadsUpNotificationOnTouchListener();
        when(mVelocityTracker.getXVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() - VELOCITY);
        when(mVelocityTracker.getYVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() + VELOCITY);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mMoveMotionEventMoreThanTouchSlop);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mUpMotionEventLessThanThreshold);

        verify(mDismissCallbacks, times(1)).onDismiss();
    }

    @Test
    public void onTouch_actionUp_flingEqualInBothDirection_horizontal_callbackNotSent() {
        when(mVelocityTracker.getXVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() + VELOCITY);
        when(mVelocityTracker.getYVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() + VELOCITY);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mMoveMotionEventMoreThanTouchSlop);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mUpMotionEventLessThanThreshold);

        verify(mDismissCallbacks, never()).onDismiss();
    }

    @Test
    public void onTouch_actionUp_flingEqualInBothDirection_vertical_callbackNotSent() {
        mTestableResources
                .addOverride(R.bool.config_isHeadsUpNotificationDismissibleVertically, true);
        createHeadsUpNotificationOnTouchListener();
        when(mVelocityTracker.getXVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() + VELOCITY);
        when(mVelocityTracker.getYVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() + VELOCITY);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mMoveMotionEventMoreThanTouchSlop);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mUpMotionEventLessThanThreshold);

        verify(mDismissCallbacks, never()).onDismiss();
    }

    @Test
    public void onTouch_actionUp_moveLessThanMin_velocityLessThanMin_horizontal_callbackNotSent() {
        when(mVelocityTracker.getXVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() - VELOCITY);
        when(mVelocityTracker.getYVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() - VELOCITY);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mMoveMotionEventMoreThanTouchSlop);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mUpMotionEventLessThanThreshold);

        verify(mDismissCallbacks, never()).onDismiss();
    }

    @Test
    public void onTouch_actionUp_moveLessThanMin_velocityLessThanMin_vertical_callbackNotSent() {
        mTestableResources
                .addOverride(R.bool.config_isHeadsUpNotificationDismissibleVertically, true);
        createHeadsUpNotificationOnTouchListener();
        when(mVelocityTracker.getXVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() - VELOCITY);
        when(mVelocityTracker.getYVelocity()).thenReturn(
                (float) mHeadsUpNotificationOnTouchListener.getMinimumFlingVelocity() - VELOCITY);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mDownMotionEvent);
        mHeadsUpNotificationOnTouchListener.onTouch(mView, mMoveMotionEventMoreThanTouchSlop);

        mHeadsUpNotificationOnTouchListener.onTouch(mView, mUpMotionEventLessThanThreshold);

        verify(mDismissCallbacks, never()).onDismiss();
    }

    private void setUpView() {
        when(mView.getContext()).thenReturn(mContext);
        when(mView.getViewTreeObserver()).thenReturn(mViewTreeObserver);
        doAnswer((Answer<Void>) invocation -> {
            ViewTreeObserver.OnGlobalLayoutListener listener = invocation.getArgument(0);
            listener.onGlobalLayout();
            return null;
        }).when(mViewTreeObserver).addOnGlobalLayoutListener(
                any(ViewTreeObserver.OnGlobalLayoutListener.class));
        when(mView.animate()).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.alpha(anyFloat())).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.translationX(anyFloat())).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.translationY(anyFloat())).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.setListener(nullable(Animator.AnimatorListener.class)))
                .thenReturn(mViewPropertyAnimator);
        when(mView.getHeight()).thenReturn(VIEW_SIZE);
        when(mView.getWidth()).thenReturn(VIEW_SIZE);
        when(mView.getParent()).thenReturn(mViewParent);
    }

    private void setUpMotionEvents() {
        float touchSlop = (float) mHeadsUpNotificationOnTouchListener.getTouchSlop();
        float percentageOfMaxTransaltionToDismiss = (float) mHeadsUpNotificationOnTouchListener
                .getPercentageOfMaxTransaltionToDismiss();
        when(mDownMotionEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);
        when(mDownMotionEvent.getRawX()).thenReturn((float) 0);
        when(mDownMotionEvent.getRawY()).thenReturn((float) 0);

        when(mCancelMotionEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_CANCEL);

        when(mMoveMotionEventLessThanTouchSlop.getActionMasked())
                .thenReturn(MotionEvent.ACTION_MOVE);
        when(mMoveMotionEventLessThanTouchSlop.getRawX()).thenReturn(touchSlop - TRANSLATION);
        when(mMoveMotionEventLessThanTouchSlop.getRawY()).thenReturn(touchSlop - TRANSLATION);


        when(mMoveMotionEventMoreThanTouchSlop.getActionMasked())
                .thenReturn(MotionEvent.ACTION_MOVE);
        when(mMoveMotionEventMoreThanTouchSlop.getRawX()).thenReturn(touchSlop + TRANSLATION);
        when(mMoveMotionEventMoreThanTouchSlop.getRawY()).thenReturn(touchSlop + TRANSLATION);

        when(mUpMotionEventMoreThanThreshold.getActionMasked())
                .thenReturn(MotionEvent.ACTION_UP);
        when(mUpMotionEventMoreThanThreshold.getRawX()).thenReturn(
                percentageOfMaxTransaltionToDismiss * VIEW_SIZE + TRANSLATION);
        when(mUpMotionEventMoreThanThreshold.getRawY()).thenReturn(
                percentageOfMaxTransaltionToDismiss * VIEW_SIZE + TRANSLATION);

        when(mUpMotionEventLessThanThreshold.getActionMasked())
                .thenReturn(MotionEvent.ACTION_UP);
        when(mUpMotionEventLessThanThreshold.getRawX()).thenReturn(
                percentageOfMaxTransaltionToDismiss * VIEW_SIZE - TRANSLATION);
        when(mUpMotionEventLessThanThreshold.getRawY()).thenReturn(
                percentageOfMaxTransaltionToDismiss * VIEW_SIZE - TRANSLATION);

    }

    private void createHeadsUpNotificationOnTouchListener() {
        mHeadsUpNotificationOnTouchListener = new HeadsUpNotificationOnTouchListener(mView,
                /* dismissOnSwipe= */ true, mDismissCallbacks) {
            @Override
            MotionEvent obtainMotionEvent(MotionEvent motionEvent) {
                return mNewMotionEvent;
            }

            @Override
            VelocityTracker obtainVelocityTracker() {
                return mVelocityTracker;
            }
        };
    }
}
