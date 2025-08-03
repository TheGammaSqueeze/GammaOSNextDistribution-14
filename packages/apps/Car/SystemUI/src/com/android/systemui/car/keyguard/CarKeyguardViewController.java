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


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowManager;

import androidx.annotation.MainThread;

import com.android.car.ui.FocusParkingView;
import com.android.internal.widget.LockPatternView;
import com.android.keyguard.KeyguardMessageAreaController;
import com.android.keyguard.KeyguardSecurityModel;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardViewController;
import com.android.keyguard.ViewMediatorCallback;
import com.android.keyguard.dagger.KeyguardBouncerComponent;
import com.android.systemui.R;
import com.android.systemui.bouncer.domain.interactor.BouncerMessageInteractor;
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerCallbackInteractor;
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerCallbackInteractor.PrimaryBouncerExpansionCallback;
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor;
import com.android.systemui.bouncer.ui.BouncerView;
import com.android.systemui.bouncer.ui.binder.KeyguardBouncerViewBinder;
import com.android.systemui.bouncer.ui.viewmodel.KeyguardBouncerViewModel;
import com.android.systemui.car.systembar.CarSystemBarController;
import com.android.systemui.car.window.OverlayViewController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.car.window.SystemUIOverlayWindowController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.keyguard.ui.viewmodel.PrimaryBouncerToGoneTransitionViewModel;
import com.android.systemui.log.BouncerLogger;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.shade.ShadeExpansionStateManager;
import com.android.systemui.shade.ShadeLockscreenInteractor;
import com.android.systemui.statusbar.phone.BiometricUnlockController;
import com.android.systemui.statusbar.phone.CentralSurfaces;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.toast.SystemUIToast;
import com.android.systemui.toast.ToastFactory;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;
import com.android.systemui.util.concurrency.DelayableExecutor;

import dagger.Lazy;

import javax.inject.Inject;

/**
 * Automotive implementation of the {@link KeyguardViewController}. It controls the Keyguard View
 * that is mounted to the SystemUIOverlayWindow.
 */
@SysUISingleton
public class CarKeyguardViewController extends OverlayViewController implements
        KeyguardViewController {
    private static final String TAG = "CarKeyguardViewController";
    private static final boolean DEBUG = true;
    private static final float TOAST_PARAMS_HORIZONTAL_WEIGHT = 1.0f;
    private static final float TOAST_PARAMS_VERTICAL_WEIGHT = 1.0f;

    private final Context mContext;
    private final DelayableExecutor mMainExecutor;
    private final WindowManager mWindowManager;
    private final ToastFactory mToastFactory;
    private final FocusParkingView mFocusParkingView;
    private final KeyguardStateController mKeyguardStateController;
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private final Lazy<BiometricUnlockController> mBiometricUnlockControllerLazy;
    private final ViewMediatorCallback mViewMediatorCallback;
    private final CarSystemBarController mCarSystemBarController;
    private final PrimaryBouncerInteractor mPrimaryBouncerInteractor;
    private final KeyguardSecurityModel mKeyguardSecurityModel;
    private final KeyguardBouncerViewModel mKeyguardBouncerViewModel;
    private final KeyguardBouncerComponent.Factory mKeyguardBouncerComponentFactory;
    private final BouncerView mBouncerView;
    private final PrimaryBouncerExpansionCallback mExpansionCallback =
            new PrimaryBouncerExpansionCallback() {
                @Override
                public void onFullyShown() {
                    LockPatternView patternView = getLayout().findViewById(R.id.lockPatternView);
                    if (patternView != null) {
                        patternView.setOnFocusChangeListener((v, hasFocus) -> {
                            if (hasFocus) {
                                makeOverlayToast(R.string.lockpattern_does_not_support_rotary);
                            }
                        });
                    }
                }

                @Override
                public void onStartingToHide() {
                }

                @Override
                public void onStartingToShow() {
                }

                @Override
                public void onFullyHidden() {
                }

                @Override
                public void onVisibilityChanged(boolean isVisible) {
                }

                @Override
                public void onExpansionChanged(float bouncerHideAmount) {
                }
            };
    private final KeyguardMessageAreaController.Factory mMessageAreaControllerFactory;
    private final BouncerLogger mBouncerLogger;
    private final SelectedUserInteractor mSelectedUserInteractor;
    private final BouncerMessageInteractor mBouncerMessageInteractor;

    private OnKeyguardCancelClickedListener mKeyguardCancelClickedListener;
    private boolean mShowing;
    private boolean mIsSleeping;
    private int mToastShowDurationMillisecond;
    private ViewGroup mKeyguardContainer;
    private PrimaryBouncerToGoneTransitionViewModel mPrimaryBouncerToGoneTransitionViewModel;

    @Inject
    public CarKeyguardViewController(
            Context context,
            UserTracker userTracker,
            @Main DelayableExecutor mainExecutor,
            WindowManager windowManager,
            ToastFactory toastFactory,
            SystemUIOverlayWindowController systemUIOverlayWindowController,
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            KeyguardStateController keyguardStateController,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            Lazy<BiometricUnlockController> biometricUnlockControllerLazy,
            ViewMediatorCallback viewMediatorCallback,
            CarSystemBarController carSystemBarController,
            PrimaryBouncerCallbackInteractor primaryBouncerCallbackInteractor,
            PrimaryBouncerInteractor primaryBouncerInteractor,
            KeyguardSecurityModel keyguardSecurityModel,
            KeyguardBouncerViewModel keyguardBouncerViewModel,
            PrimaryBouncerToGoneTransitionViewModel primaryBouncerToGoneTransitionViewModel,
            KeyguardBouncerComponent.Factory keyguardBouncerComponentFactory,
            BouncerView bouncerView,
            KeyguardMessageAreaController.Factory messageAreaControllerFactory,
            BouncerLogger bouncerLogger,
            BouncerMessageInteractor bouncerMessageInteractor,
            SelectedUserInteractor selectedUserInteractor) {
        super(R.id.keyguard_stub, overlayViewGlobalStateController);

        mContext = context;
        mMainExecutor = mainExecutor;
        mWindowManager = windowManager;
        mToastFactory = toastFactory;
        mFocusParkingView = systemUIOverlayWindowController.getBaseLayout().findViewById(
                R.id.focus_parking_view);
        mKeyguardStateController = keyguardStateController;
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mBiometricUnlockControllerLazy = biometricUnlockControllerLazy;
        mViewMediatorCallback = viewMediatorCallback;
        mCarSystemBarController = carSystemBarController;
        mPrimaryBouncerInteractor = primaryBouncerInteractor;
        mKeyguardSecurityModel = keyguardSecurityModel;
        mKeyguardBouncerViewModel = keyguardBouncerViewModel;
        mKeyguardBouncerComponentFactory = keyguardBouncerComponentFactory;
        mPrimaryBouncerToGoneTransitionViewModel = primaryBouncerToGoneTransitionViewModel;
        mBouncerView = bouncerView;
        mSelectedUserInteractor = selectedUserInteractor;

        mToastShowDurationMillisecond = mContext.getResources().getInteger(
                R.integer.car_keyguard_toast_show_duration_millisecond);
        mMessageAreaControllerFactory = messageAreaControllerFactory;
        mBouncerLogger = bouncerLogger;
        mBouncerMessageInteractor = bouncerMessageInteractor;
        primaryBouncerCallbackInteractor.addBouncerExpansionCallback(mExpansionCallback);
    }

    @Override
    protected int getFocusAreaViewId() {
        return R.id.keyguard_container;
    }

    @Override
    protected boolean shouldShowNavigationBarInsets() {
        return true;
    }

    @Override
    public void onFinishInflate() {
        mKeyguardContainer = getLayout().findViewById(R.id.keyguard_container);
        KeyguardBouncerViewBinder.bind(mKeyguardContainer,
                mKeyguardBouncerViewModel, mPrimaryBouncerToGoneTransitionViewModel,
                mKeyguardBouncerComponentFactory,
                mMessageAreaControllerFactory,
                mBouncerMessageInteractor,
                mBouncerLogger,
                mSelectedUserInteractor);
        mBiometricUnlockControllerLazy.get().setKeyguardViewController(this);
    }

    @Override
    @MainThread
    public void notifyKeyguardAuthenticated(boolean strongAuth) {
        mPrimaryBouncerInteractor.notifyKeyguardAuthenticatedBiometrics(strongAuth);
    }

    @Override
    @MainThread
    public void showPrimaryBouncer(boolean scrimmed) {
        if (mShowing && !mPrimaryBouncerInteractor.isFullyShowing()) {
            mPrimaryBouncerInteractor.show(/* isScrimmed= */ true);
        }
    }

    @Override
    @MainThread
    public void show(Bundle options) {
        if (mShowing) return;

        mShowing = true;
        mKeyguardStateController.notifyKeyguardState(mShowing,
                mKeyguardStateController.isOccluded());
        mCarSystemBarController.showAllKeyguardButtons(/* isSetUp= */ true);
        start();
        reset(/* hideBouncerWhenShowing= */ false);
        notifyKeyguardUpdateMonitor();
    }

    @Override
    @MainThread
    public void hide(long startTime, long fadeoutDuration) {
        if (!mShowing || mIsSleeping) return;

        mViewMediatorCallback.readyForKeyguardDone();
        mShowing = false;
        mKeyguardStateController.notifyKeyguardState(mShowing,
                mKeyguardStateController.isOccluded());
        mPrimaryBouncerInteractor.hide();
        mCarSystemBarController.showAllNavigationButtons(/* isSetUp= */ true);
        stop();
        mKeyguardStateController.notifyKeyguardDoneFading();
        mMainExecutor.execute(mViewMediatorCallback::keyguardGone);
        notifyKeyguardUpdateMonitor();
    }

    @Override
    public void reset(boolean hideBouncerWhenShowing) {
        if (mIsSleeping) return;

        mMainExecutor.execute(() -> {
            if (mShowing) {
                if (!isSecure()) {
                    dismissAndCollapse();
                } else {
                    resetBouncer();
                }
                mKeyguardUpdateMonitor.sendKeyguardReset();
                notifyKeyguardUpdateMonitor();
            } else {
                // This is necessary in order to address an inconsistency between the keyguard
                // service and the keyguard views.
                // TODO: Investigate the source of the inconsistency.
                show(/* options= */ null);
            }
        });
    }

    @Override
    public void hideAlternateBouncer(boolean forceUpdateScrim) {
        // no-op
    }

    @Override
    @MainThread
    public void onFinishedGoingToSleep() {
        mPrimaryBouncerInteractor.hide();
    }

    @Override
    @MainThread
    public void setOccluded(boolean occluded, boolean animate) {
        mKeyguardStateController.notifyKeyguardState(
                mKeyguardStateController.isShowing(), occluded);
        getOverlayViewGlobalStateController().setOccluded(occluded);
        if (occluded && !mKeyguardStateController.isUnlocked()) {
            mCarSystemBarController.showAllOcclusionButtons(/* isSetup= */ true);
        } else {
            if (mShowing && isSecure()) {
                mCarSystemBarController.showAllKeyguardButtons(/* isSetup= */ true);
            } else {
                mCarSystemBarController.showAllNavigationButtons(/* isSetUp= */ true);
            }
        }
    }

    @Override
    @MainThread
    public void onCancelClicked() {
        mPrimaryBouncerInteractor.hide();
        mKeyguardCancelClickedListener.onCancelClicked();
    }

    @Override
    @MainThread
    public void dismissAndCollapse() {
        // If dismissing and collapsing Keyguard is requested (e.g. by a Keyguard-dismissing
        // Activity) while Keyguard is occluded, unocclude Keyguard so the user can authenticate to
        // dismiss Keyguard.
        if (mKeyguardStateController.isOccluded()) {
            setOccluded(/* occluded= */ false, /* animate= */ false);
        }
        if (!isSecure()) {
            hide(/* startTime= */ 0, /* fadeoutDuration= */ 0);
            resetBouncer();
        }
    }

    @Override
    @MainThread
    public void startPreHideAnimation(Runnable finishRunnable) {
        if (isBouncerShowing()) {
            mPrimaryBouncerInteractor.startDisappearAnimation(finishRunnable);
        } else if (finishRunnable != null) {
            finishRunnable.run();
        }
    }

    @Override
    public void setNeedsInput(boolean needsInput) {
        getOverlayViewGlobalStateController().setWindowNeedsInput(needsInput);
    }

    @Override
    public void onStartedGoingToSleep() {
        mIsSleeping = true;
    }

    @Override
    public void onStartedWakingUp() {
        mIsSleeping = false;
        reset(/* hideBouncerWhenShowing= */ false);
    }

    /**
     * Add listener for keyguard cancel clicked.
     */
    public void registerOnKeyguardCancelClickedListener(
            OnKeyguardCancelClickedListener keyguardCancelClickedListener) {
        mKeyguardCancelClickedListener = keyguardCancelClickedListener;
    }

    /**
     * Remove listener for keyguard cancel clicked.
     */
    public void unregisterOnKeyguardCancelClickedListener(
            OnKeyguardCancelClickedListener keyguardCancelClickedListener) {
        mKeyguardCancelClickedListener = null;
    }

    @Override
    public ViewRootImpl getViewRootImpl() {
        if (getLayout() == null) return null;
        return ((View) getLayout().getParent()).getViewRootImpl();
    }

    @Override
    @MainThread
    public boolean isBouncerShowing() {
        return mPrimaryBouncerInteractor.isFullyShowing();
    }

    @Override
    @MainThread
    public boolean primaryBouncerIsOrWillBeShowing() {
        return mPrimaryBouncerInteractor.isFullyShowing()
                || mPrimaryBouncerInteractor.isInTransit();
    }

    @Override
    public void keyguardGoingAway() {
        // no-op
    }

    @Override
    public void setKeyguardGoingAwayState(boolean isKeyguardGoingAway) {
        // no-op
    }

    @Override
    public boolean shouldDisableWindowAnimationsForUnlock() {
        return false;
    }

    @Override
    public boolean isGoingToNotificationShade() {
        return false;
    }

    @Override
    public boolean isUnlockWithWallpaper() {
        return false;
    }

    @Override
    public boolean isBouncerShowingOverDream() {
        return false;
    }

    @Override
    public boolean shouldSubtleWindowAnimationsForUnlock() {
        return false;
    }

    @Override
    public void blockPanelExpansionFromCurrentTouch() {
        // no-op
    }

    @Override
    public void registerCentralSurfaces(
            CentralSurfaces centralSurfaces,
            ShadeLockscreenInteractor shadeLockscreenInteractor,
            ShadeExpansionStateManager shadeExpansionStateManager,
            BiometricUnlockController biometricUnlockController,
            View notificationContainer,
            KeyguardBypassController bypassController) {
        // no-op
    }

    /**
     * Hides Keyguard so that the transitioning Bouncer can be hidden until it is prepared. To be
     * called by {@link com.android.systemui.car.userswitcher.FullscreenUserSwitcherViewMediator}
     * when a new user is selected.
     */
    public void hideKeyguardToPrepareBouncer() {
        getLayout().setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean setAllowRotaryFocus(boolean allowRotaryFocus) {
        boolean changed = super.setAllowRotaryFocus(allowRotaryFocus);
        if (changed && allowRotaryFocus && mBouncerView.getDelegate() != null) {
            // Resume the view so it can regain focus
            mBouncerView.getDelegate().resume();
        }
        return changed;
    }

    private void revealKeyguardIfBouncerPrepared() {
        int reattemptDelayMillis = 50;
        Runnable revealKeyguard = () -> {
            if (!mPrimaryBouncerInteractor.isInTransit() || !isSecure()) {
                if (mShowing) {
                    // Only set the layout as visible if the keyguard should be showing
                    showInternal();
                }
            } else {
                if (DEBUG) {
                    Log.d(TAG, "revealKeyguardIfBouncerPrepared: Bouncer is not prepared "
                            + "yet so reattempting after " + reattemptDelayMillis + "ms.");
                }
                mMainExecutor.executeDelayed(this::revealKeyguardIfBouncerPrepared,
                        reattemptDelayMillis);
            }
        };
        mMainExecutor.execute(revealKeyguard);
    }

    private void notifyKeyguardUpdateMonitor() {
        mKeyguardUpdateMonitor.sendPrimaryBouncerChanged(
                primaryBouncerIsOrWillBeShowing(), isBouncerShowing());
    }

    /**
     * WARNING: This method might cause Binder calls.
     */
    private boolean isSecure() {
        return mKeyguardSecurityModel.getSecurityMode(mSelectedUserInteractor.getSelectedUserId())
                != KeyguardSecurityModel.SecurityMode.None;
    }


    private void resetBouncer() {
        mMainExecutor.execute(() -> {
            hideInternal();
            mPrimaryBouncerInteractor.hide();
            mPrimaryBouncerInteractor.show(/* isScrimmed= */ true);
            revealKeyguardIfBouncerPrepared();
        });
    }

    private void makeOverlayToast(int stringId) {
        Resources res = mContext.getResources();

        SystemUIToast systemUIToast = mToastFactory.createToast(mContext,
                res.getString(stringId), mContext.getPackageName(), UserHandle.myUserId(),
                res.getConfiguration().orientation);

        if (systemUIToast == null) {
            return;
        }

        View toastView = systemUIToast.getView();

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.format = PixelFormat.TRANSLUCENT;
        params.type = WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL;
        params.y = systemUIToast.getYOffset();

        int absGravity = Gravity.getAbsoluteGravity(systemUIToast.getGravity(),
                res.getConfiguration().getLayoutDirection());
        params.gravity = absGravity;

        if ((absGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.FILL_HORIZONTAL) {
            params.horizontalWeight = TOAST_PARAMS_HORIZONTAL_WEIGHT;
        }
        if ((absGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.FILL_VERTICAL) {
            params.verticalWeight = TOAST_PARAMS_VERTICAL_WEIGHT;
        }

        // Make FocusParkingView temporarily unfocusable so it does not steal the focus.
        // If FocusParkingView is focusable, it first steals focus and then returns it to Pattern
        // Lock, which causes the Toast to appear repeatedly.
        mFocusParkingView.setFocusable(false);
        mWindowManager.addView(toastView, params);

        Animator inAnimator = systemUIToast.getInAnimation();
        if (inAnimator != null) {
            inAnimator.start();
        }

        mMainExecutor.executeDelayed(new Runnable() {
            @Override
            public void run() {
                Animator outAnimator = systemUIToast.getOutAnimation();
                if (outAnimator != null) {
                    outAnimator.start();
                    outAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            mWindowManager.removeViewImmediate(toastView);
                            mFocusParkingView.setFocusable(true);
                        }
                    });
                } else {
                    mFocusParkingView.setFocusable(true);
                }
            }
        }, mToastShowDurationMillisecond);
    }

    /**
     * Defines a callback for keyguard cancel button clicked listeners.
     */
    public interface OnKeyguardCancelClickedListener {
        /**
         * Called when keyguard cancel button is clicked.
         */
        void onCancelClicked();
    }
}
