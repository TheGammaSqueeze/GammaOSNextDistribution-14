/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.app.IActivityTaskManager;
import android.app.trust.TrustManager;
import android.content.Context;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationTarget;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardDisplayManager;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardViewController;
import com.android.keyguard.mediator.ScreenOnCoordinator;
import com.android.systemui.animation.ActivityTransitionAnimator;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.classifier.FalsingCollector;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dreams.DreamOverlayStateController;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.flags.SystemPropertiesHelper;
import com.android.systemui.keyguard.DismissCallbackRegistry;
import com.android.systemui.keyguard.KeyguardUnlockAnimationController;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.keyguard.WindowManagerLockscreenVisibilityManager;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;
import com.android.systemui.keyguard.ui.viewmodel.DreamingToLockscreenTransitionViewModel;
import com.android.systemui.log.SessionTracker;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.shade.ShadeController;
import com.android.systemui.statusbar.NotificationShadeDepthController;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.phone.ScreenOffAnimationController;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;
import com.android.systemui.util.DeviceConfigProxy;
import com.android.systemui.util.kotlin.JavaAdapter;
import com.android.systemui.util.settings.SecureSettings;
import com.android.systemui.util.settings.SystemSettings;
import com.android.systemui.util.time.SystemClock;
import com.android.systemui.wallpapers.data.repository.WallpaperRepository;
import com.android.wm.shell.keyguard.KeyguardTransitions;

import dagger.Lazy;

import java.util.concurrent.Executor;

import kotlinx.coroutines.CoroutineDispatcher;

/**
 * Car customizations on top of {@link KeyguardViewMediator}. Please refer to that class for
 * more details on specific functionalities.
 */
public class CarKeyguardViewMediator extends KeyguardViewMediator {
    private static final String TAG = "CarKeyguardViewMediator";
    private final Context mContext;
    private final Object mOcclusionLock = new Object();
    private final IRemoteAnimationRunner mOccludeAnimationRunner =
            new CarOcclusionAnimationRunner(/* occlude= */ true);
    private final IRemoteAnimationRunner mUnoccludeAnimationRunner =
            new CarOcclusionAnimationRunner(/* occlude= */ false);

    /**
     * Injected constructor. See {@link CarKeyguardModule}.
     */
    public CarKeyguardViewMediator(Context context,
            UiEventLogger uiEventLogger,
            SessionTracker sessionTracker,
            UserTracker userTracker,
            FalsingCollector falsingCollector,
            LockPatternUtils lockPatternUtils,
            BroadcastDispatcher broadcastDispatcher,
            Lazy<KeyguardViewController> statusBarKeyguardViewManagerLazy,
            DismissCallbackRegistry dismissCallbackRegistry,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            DumpManager dumpManager,
            Executor uiBgExecutor, PowerManager powerManager,
            TrustManager trustManager,
            UserSwitcherController userSwitcherController,
            DeviceConfigProxy deviceConfig,
            NavigationModeController navigationModeController,
            KeyguardDisplayManager keyguardDisplayManager,
            DozeParameters dozeParameters,
            SysuiStatusBarStateController statusBarStateController,
            KeyguardStateController keyguardStateController,
            Lazy<KeyguardUnlockAnimationController> keyguardUnlockAnimationControllerLazy,
            ScreenOffAnimationController screenOffAnimationController,
            Lazy<NotificationShadeDepthController> notificationShadeDepthController,
            ScreenOnCoordinator screenOnCoordinator,
            KeyguardTransitions keyguardTransitions,
            InteractionJankMonitor interactionJankMonitor,
            DreamOverlayStateController dreamOverlayStateController,
            JavaAdapter javaAdapter,
            WallpaperRepository wallpaperRepository,
            Lazy<ShadeController> mShadeControllerLazy,
            Lazy<NotificationShadeWindowController> notificationShadeWindowControllerLazy,
            Lazy<ActivityTransitionAnimator> activityTransitionAnimator,
            Lazy<ScrimController> scrimControllerLazy,
            IActivityTaskManager activityTaskManagerService,
            FeatureFlags featureFlags,
            SecureSettings secureSettings,
            SystemSettings systemSettings,
            SystemClock systemClock,
            @Main CoroutineDispatcher mainDispatcher,
            Lazy<DreamingToLockscreenTransitionViewModel> dreamingToLockscreenTransitionViewModel,
            SystemPropertiesHelper systemPropertiesHelper,
            Lazy<WindowManagerLockscreenVisibilityManager> wmLockscreenVisibilityManager,
            SelectedUserInteractor selectedUserInteractor,
            KeyguardInteractor keyguardInteractor) {
        super(context, uiEventLogger, sessionTracker,
                userTracker, falsingCollector, lockPatternUtils, broadcastDispatcher,
                statusBarKeyguardViewManagerLazy, dismissCallbackRegistry, keyguardUpdateMonitor,
                dumpManager, uiBgExecutor, powerManager, trustManager, userSwitcherController,
                deviceConfig, navigationModeController, keyguardDisplayManager, dozeParameters,
                statusBarStateController, keyguardStateController,
                keyguardUnlockAnimationControllerLazy, screenOffAnimationController,
                notificationShadeDepthController, screenOnCoordinator, keyguardTransitions,
                interactionJankMonitor, dreamOverlayStateController,
                javaAdapter,
                wallpaperRepository,
                mShadeControllerLazy,
                notificationShadeWindowControllerLazy,
                activityTransitionAnimator,
                scrimControllerLazy,
                activityTaskManagerService,
                featureFlags, secureSettings, systemSettings, systemClock,
                mainDispatcher,
                dreamingToLockscreenTransitionViewModel,
                systemPropertiesHelper,
                wmLockscreenVisibilityManager,
                selectedUserInteractor,
                keyguardInteractor);
        mContext = context;
    }

    @Override
    public void start() {
        if (CarSystemUIUserUtil.isSecondaryMUMDSystemUI()) {
            // Currently keyguard is not functional for the secondary users in a MUMD configuration
            // TODO_MD: make keyguard functional for secondary users
            return;
        }
        super.start();
    }

    @Override
    public IRemoteAnimationRunner getOccludeAnimationRunner() {
        return mOccludeAnimationRunner;
    }

    @Override
    public IRemoteAnimationRunner getUnoccludeAnimationRunner() {
        return mUnoccludeAnimationRunner;
    }

    private class CarOcclusionAnimationRunner extends IRemoteAnimationRunner.Stub {
        private final boolean mOcclude;
        private final String mAnimatorType;

        CarOcclusionAnimationRunner(boolean occlude) {
            mOcclude = occlude;
            mAnimatorType = mOcclude ? "OccludeAnimator" : "UnoccludeAnimator";
        }

        @Override
        public void onAnimationStart(int transit, RemoteAnimationTarget[] apps,
                RemoteAnimationTarget[] wallpapers, RemoteAnimationTarget[] nonApps,
                IRemoteAnimationFinishedCallback finishedCallback) throws RemoteException {
            synchronized (mOcclusionLock) {
                Log.d(TAG, String.format("%s#onAnimationStart. Set occluded = %b.",
                        mAnimatorType, mOcclude));
                setOccluded(mOcclude, /* animate= */ false);
                finishedCallback.onAnimationFinished();
            }
        }

        @Override
        public void onAnimationCancelled() throws RemoteException {
            Log.d(TAG, String.format("%s cancelled by WM.", mAnimatorType));
        }
    }
}
