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

package com.android.systemui.car.keyguard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.test.filters.SmallTest;

import com.android.keyguard.KeyguardMessageAreaController;
import com.android.keyguard.KeyguardSecurityContainerController;
import com.android.keyguard.KeyguardSecurityModel;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.ViewMediatorCallback;
import com.android.keyguard.dagger.KeyguardBouncerComponent;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.bouncer.domain.interactor.BouncerMessageInteractor;
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerCallbackInteractor;
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor;
import com.android.systemui.bouncer.ui.BouncerView;
import com.android.systemui.bouncer.ui.viewmodel.KeyguardBouncerViewModel;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.systembar.CarSystemBarController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.car.window.SystemUIOverlayWindowController;
import com.android.systemui.keyguard.ui.viewmodel.PrimaryBouncerToGoneTransitionViewModel;
import com.android.systemui.log.BouncerLogger;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.phone.BiometricUnlockController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.toast.ToastFactory;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class CarKeyguardViewControllerTest extends SysuiTestCase {

    private CarKeyguardViewController mCarKeyguardViewController;
    private FakeExecutor mExecutor;

    @Mock
    private UserTracker mUserTracker;
    @Mock
    private OverlayViewGlobalStateController mOverlayViewGlobalStateController;
    @Mock
    private SystemUIOverlayWindowController mSystemUIOverlayWindowController;
    @Mock
    private CarKeyguardViewController.OnKeyguardCancelClickedListener mCancelClickedListener;
    @Mock
    private PrimaryBouncerCallbackInteractor mPrimaryBouncerCallbackInteractor;
    @Mock
    private PrimaryBouncerInteractor mPrimaryBouncerInteractor;
    @Mock
    private KeyguardStateController mKeyguardStateController;
    @Mock
    private KeyguardSecurityModel mKeyguardSecurityModel;
    @Mock
    private KeyguardBouncerViewModel mKeyguardBouncerViewModel;
    @Mock
    private KeyguardBouncerComponent.Factory mKeyguardBouncerComponentFactory;
    @Mock
    private PrimaryBouncerToGoneTransitionViewModel mPrimaryBouncerToGoneTransitionViewModel;
    @Mock
    private CarSystemBarController mCarSystemBarController;
    @Mock
    private BouncerView mBouncerView;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        ViewGroup mockBaseLayout = new FrameLayout(mContext);

        when(mSystemUIOverlayWindowController.getBaseLayout()).thenReturn(mockBaseLayout);
        mExecutor = new FakeExecutor(new FakeSystemClock());

        KeyguardBouncerComponent keyguardBouncerComponent = mock(KeyguardBouncerComponent.class);
        KeyguardSecurityContainerController securityContainerController = mock(
                KeyguardSecurityContainerController.class);
        when(mKeyguardBouncerComponentFactory.create(any(ViewGroup.class))).thenReturn(
                keyguardBouncerComponent);
        when(keyguardBouncerComponent.getSecurityContainerController()).thenReturn(
                securityContainerController);

        mCarKeyguardViewController = new CarKeyguardViewController(
                mContext,
                mUserTracker,
                mExecutor,
                mock(WindowManager.class),
                mock(ToastFactory.class),
                mSystemUIOverlayWindowController,
                mOverlayViewGlobalStateController,
                mKeyguardStateController,
                mock(KeyguardUpdateMonitor.class),
                () -> mock(BiometricUnlockController.class),
                mock(ViewMediatorCallback.class),
                mCarSystemBarController,
                mPrimaryBouncerCallbackInteractor,
                mPrimaryBouncerInteractor,
                mKeyguardSecurityModel,
                mKeyguardBouncerViewModel,
                mPrimaryBouncerToGoneTransitionViewModel,
                mKeyguardBouncerComponentFactory,
                mBouncerView,
                mock(KeyguardMessageAreaController.Factory.class),
                mock(BouncerLogger.class),
                mock(BouncerMessageInteractor.class),
                mock(SelectedUserInteractor.class)
        );
        mCarKeyguardViewController.inflate((ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.sysui_overlay_window, /* root= */ null));
    }

    @Test
    public void onShow_bouncerIsSecure_showsBouncerWithSecuritySelectionReset() {
        setIsSecure(true);
        mCarKeyguardViewController.show(/* options= */ null);
        waitForDelayableExecutor();

        verify(mPrimaryBouncerInteractor).show(/* isScrimmed= */ true);
    }

    @Test
    public void onShow_bouncerIsSecure_keyguardIsVisible() {
        setIsSecure(true);
        mCarKeyguardViewController.show(/* options= */ null);

        verify(mOverlayViewGlobalStateController).showView(eq(mCarKeyguardViewController), any());
    }

    @Test
    public void onShow_bouncerNotSecure_hidesBouncerAndDestroysTheView() {
        setIsSecure(false);
        mCarKeyguardViewController.show(/* options= */ null);
        waitForDelayableExecutor();

        verify(mPrimaryBouncerInteractor, Mockito.times(2)).hide();
    }

    @Test
    public void onShow_bouncerNotSecure_keyguardIsNotVisible() {
        setIsSecure(false);
        mCarKeyguardViewController.show(/* options= */ null);
        waitForDelayableExecutor();

        // Here we check for both showView and hideView since the current implementation of show
        // with bouncer being not secure has the following method execution orders:
        // 1) show -> start -> showView
        // 2) show -> reset -> dismissAndCollapse -> hide -> stop -> hideView
        // Hence, we want to make sure that showView is called before hideView and not in any
        // other combination.
        InOrder inOrder = inOrder(mOverlayViewGlobalStateController);
        inOrder.verify(mOverlayViewGlobalStateController).showView(eq(mCarKeyguardViewController),
                any());
        inOrder.verify(mOverlayViewGlobalStateController).hideView(eq(mCarKeyguardViewController),
                any());
    }

    @Test
    public void onHide_keyguardShowing_hidesBouncerAndDestroysTheView() {
        setIsSecure(true);
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.hide(/* startTime= */ 0, /* fadeoutDelay= */ 0);

        verify(mPrimaryBouncerInteractor).hide();
    }

    @Test
    public void onHide_keyguardNotShown_doesNotHideOrDestroyBouncer() {
        mCarKeyguardViewController.hide(/* startTime= */ 0, /* fadeoutDelay= */ 0);

        verify(mPrimaryBouncerInteractor, never()).hide();
    }

    @Test
    public void onHide_KeyguardNotVisible() {
        setIsSecure(true);
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.hide(/* startTime= */ 0, /* fadeoutDelay= */ 0);

        InOrder inOrder = inOrder(mOverlayViewGlobalStateController);
        inOrder.verify(mOverlayViewGlobalStateController).showView(eq(mCarKeyguardViewController),
                any());
        inOrder.verify(mOverlayViewGlobalStateController).hideView(eq(mCarKeyguardViewController),
                any());
    }

    @Test
    public void setOccludedFalse_currentlyOccluded_showsKeyguard() {
        setIsSecure(true);
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.setOccluded(/* occluded= */ true, /* animate= */ false);
        reset(mPrimaryBouncerInteractor);

        mCarKeyguardViewController.setOccluded(/* occluded= */ false, /* animate= */ false);
        waitForDelayableExecutor();

        verify(mPrimaryBouncerInteractor).show(/* isScrimmed= */ true);
    }

    @Test
    public void setOccludedTrue_currentlyIsLocked_showsNavigationButtons() {
        setIsSecure(true);
        mCarKeyguardViewController.show(/* options= */ null);

        when(mKeyguardStateController.isUnlocked()).thenReturn(true);
        mCarKeyguardViewController.setOccluded(/* occluded= */ true, /* animate= */ false);
        verify(mCarSystemBarController, never()).showAllOcclusionButtons(true);

        when(mKeyguardStateController.isUnlocked()).thenReturn(false);
        mCarKeyguardViewController.setOccluded(/* occluded= */ true, /* animate= */ false);
        verify(mCarSystemBarController).showAllOcclusionButtons(true);
    }

    @Test
    public void onCancelClicked_callsCancelClickedListener() {
        setIsSecure(true);
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.registerOnKeyguardCancelClickedListener(mCancelClickedListener);
        mCarKeyguardViewController.onCancelClicked();

        verify(mCancelClickedListener).onCancelClicked();
    }

    @Test
    public void onEnterSleepModeAndThenShowKeyguard_bouncerNotSecure_keyguardIsVisible() {
        setIsSecure(false);
        mCarKeyguardViewController.onStartedGoingToSleep();
        mCarKeyguardViewController.show(/* options= */ null);
        waitForDelayableExecutor();

        // We want to make sure that showView is called beforehand and hideView is never called
        // so that the Keyguard is visible as a result.
        InOrder inOrder = inOrder(mOverlayViewGlobalStateController);
        inOrder.verify(mOverlayViewGlobalStateController).showView(eq(mCarKeyguardViewController),
                any());
        inOrder.verify(mOverlayViewGlobalStateController, never()).hideView(
                eq(mCarKeyguardViewController), any());
    }

    @Test
    public void onFinishedGoingToSleep() {
        mCarKeyguardViewController.onFinishedGoingToSleep();
        verify(mPrimaryBouncerInteractor).hide();
    }

    @Test
    public void onDeviceWakeUpWhileKeyguardShown_bouncerNotSecure_keyguardIsNotVisible() {
        setIsSecure(false);
        mCarKeyguardViewController.onStartedGoingToSleep();
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.onStartedWakingUp();
        waitForDelayableExecutor();

        // We want to make sure that showView is called beforehand and then hideView is called so
        // that the Keyguard is invisible as a result.
        InOrder inOrder = inOrder(mOverlayViewGlobalStateController);
        inOrder.verify(mOverlayViewGlobalStateController).showView(eq(mCarKeyguardViewController),
                any());
        inOrder.verify(mOverlayViewGlobalStateController).hideView(eq(mCarKeyguardViewController),
                any());
    }

    @Test
    public void onCancelClicked_hidesBouncerAndDestroysTheView() {
        setIsSecure(true);
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.registerOnKeyguardCancelClickedListener(mCancelClickedListener);
        mCarKeyguardViewController.onCancelClicked();

        verify(mPrimaryBouncerInteractor).hide();
    }

    private void waitForDelayableExecutor() {
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
    }

    private void setIsSecure(boolean isSecure) {
        when(mKeyguardSecurityModel.getSecurityMode(anyInt())).thenReturn(
                isSecure ? KeyguardSecurityModel.SecurityMode.PIN
                        : KeyguardSecurityModel.SecurityMode.None);
    }
}
