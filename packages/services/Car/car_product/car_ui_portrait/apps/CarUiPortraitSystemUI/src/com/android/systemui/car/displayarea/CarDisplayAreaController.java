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

package com.android.systemui.car.displayarea;

import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_COLLAPSE_NOTIFICATION_PANEL;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_HIDE_SYSTEM_BAR_FOR_IMMERSIVE_MODE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_IMMERSIVE_MODE_REQUESTED_SOURCE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_IS_IMMERSIVE_MODE_REQUESTED;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_IS_IMMERSIVE_MODE_STATE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_LAUNCHER_READY;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_SUW_IN_PROGRESS;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.REQUEST_FROM_LAUNCHER;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.REQUEST_FROM_SYSTEM_UI;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;
import android.view.WindowInsets;
import android.window.DisplayAreaOrganizer;

import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarDeviceProvisionedListener;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.loading.LoadingViewController;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.wm.CarUiPortraitDisplaySystemBarsController;
import com.android.wm.shell.common.ShellExecutor;

import javax.inject.Inject;

/**
 * Controls the bounds of the home background, audio bar and application displays. This is a
 * singleton class as there should be one controller used to register and control the DA's
 */
public class CarDisplayAreaController implements ConfigurationController.ConfigurationListener,
        CommandQueue.Callbacks {

    private static final String TAG = CarDisplayAreaController.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private final DisplayAreaOrganizer mOrganizer;
    private final CarFullscreenTaskListener mCarFullscreenTaskListener;

    private final ShellExecutor mShellExecutor;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final CarUiPortraitDisplaySystemBarsController mCarUiDisplaySystemBarsController;
    private final CarFullScreenTouchHandler mCarFullScreenTouchHandler;

    private final ComponentName mNotificationCenterComponent;
    private final Context mApplicationContext;
    private final CarServiceProvider mCarServiceProvider;

    private boolean mUserSetupInProgress;
    private boolean mIsImmersive;
    private boolean mIsLauncherReady;
    private LoadingViewController mLoadingViewController;

    private final CarUiPortraitDisplaySystemBarsController.Callback
            mCarUiPortraitDisplaySystemBarsControllerCallback =
            new CarUiPortraitDisplaySystemBarsController.Callback() {
                @Override
                public void onImmersiveRequestedChanged(ComponentName componentName,
                        boolean requested) {
                    mUserSetupInProgress = mCarDeviceProvisionedController
                            .isCurrentUserSetupInProgress();
                    if (mUserSetupInProgress) {
                        mLoadingViewController.stop();
                        logIfDebuggable(
                                "No need to send out immersive request change intent during SUW");
                        updateImmersiveModeInternal();
                        return;
                    }
                    mIsImmersive = requested;
                    Intent intent = new Intent(REQUEST_FROM_SYSTEM_UI);
                    intent.putExtra(INTENT_EXTRA_IS_IMMERSIVE_MODE_REQUESTED, requested);
                    if (componentName != null) {
                        intent.putExtra(INTENT_EXTRA_IMMERSIVE_MODE_REQUESTED_SOURCE,
                                componentName.flattenToString());
                    }
                    mApplicationContext.sendBroadcastAsUser(intent,
                            new UserHandle(ActivityManager.getCurrentUser()));
                }

                @Override
                public void onImmersiveStateChanged(boolean hideNavBar) {
                    if (mUserSetupInProgress) {
                        logIfDebuggable(
                                "No need to send out immersive state change intent during SUW");
                        return;
                    }
                    Intent intent = new Intent(REQUEST_FROM_SYSTEM_UI);
                    intent.putExtra(INTENT_EXTRA_IS_IMMERSIVE_MODE_STATE, hideNavBar);
                    mApplicationContext.sendBroadcastAsUser(intent,
                            new UserHandle(ActivityManager.getCurrentUser()));

                }
            };

    private final CarDeviceProvisionedListener mCarDeviceProvisionedListener =
            new CarDeviceProvisionedListener() {
                @Override
                public void onUserSetupInProgressChanged() {
                    mUserSetupInProgress = mCarDeviceProvisionedController
                            .isCurrentUserSetupInProgress();
                    logIfDebuggable("mUserSetupInProgress changed to " + mUserSetupInProgress);
                    Intent intent = new Intent(REQUEST_FROM_SYSTEM_UI);
                    intent.putExtra(INTENT_EXTRA_SUW_IN_PROGRESS, mUserSetupInProgress);
                    mApplicationContext.sendBroadcastAsUser(intent,
                            new UserHandle(ActivityManager.getCurrentUser()));

                    updateImmersiveModeInternal();
                }
            };


    private void updateImmersiveModeInternal() {
        mCarUiDisplaySystemBarsController.requestImmersiveModeForSUW(
                mApplicationContext.getDisplayId(), mUserSetupInProgress);
        if (mUserSetupInProgress) {
            updateImmersiveModeForSUW();
        } else if (!mIsLauncherReady) {
            updateImmersiveMode();
        }
    }

    private void updateImmersiveModeForSUW() {
        mLoadingViewController.stop();
    }

    private void updateImmersiveMode() {
        if (!mIsLauncherReady) {
            mLoadingViewController.start();
        }
    }


    /**
     * Initializes the controller
     */
    @Inject
    public CarDisplayAreaController(Context applicationContext,
            CarFullscreenTaskListener carFullscreenTaskListener,
            ShellExecutor shellExecutor,
            CarServiceProvider carServiceProvider,
            DisplayAreaOrganizer organizer,
            CarUiPortraitDisplaySystemBarsController carUiPortraitDisplaySystemBarsController,
            CommandQueue commandQueue,
            CarDeviceProvisionedController deviceProvisionedController,
            LoadingViewController loadingViewController) {
        mApplicationContext = applicationContext;
        mOrganizer = organizer;
        mShellExecutor = shellExecutor;
        mCarServiceProvider = carServiceProvider;
        mCarFullscreenTaskListener = carFullscreenTaskListener;
        Resources resources = applicationContext.getResources();
        mNotificationCenterComponent = ComponentName.unflattenFromString(resources.getString(
                R.string.config_notificationCenterActivity));
        mCarUiDisplaySystemBarsController = carUiPortraitDisplaySystemBarsController;
        mCarDeviceProvisionedController = deviceProvisionedController;
        mCarFullScreenTouchHandler = new CarFullScreenTouchHandler(mShellExecutor);
        mLoadingViewController = loadingViewController;
        if (applicationContext.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT) {
            mLoadingViewController.start();
        }

        BroadcastReceiver taskViewReadyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(INTENT_EXTRA_LAUNCHER_READY)) {
                    mLoadingViewController.stop();
                    mIsLauncherReady = true;
                }
            }
        };
        mApplicationContext.registerReceiverForAllUsers(taskViewReadyReceiver,
                new IntentFilter(REQUEST_FROM_LAUNCHER), null, null, Context.RECEIVER_EXPORTED);

        if (applicationContext.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT) {
            mCarUiDisplaySystemBarsController.registerCallback(mApplicationContext.getDisplayId(),
                    mCarUiPortraitDisplaySystemBarsControllerCallback);
            commandQueue.addCallback(this);
        }
    }

    @Override
    public void animateCollapsePanels(int flags, boolean force) {
        Intent intent = new Intent(REQUEST_FROM_SYSTEM_UI);
        intent.putExtra(INTENT_EXTRA_COLLAPSE_NOTIFICATION_PANEL, true);
        mApplicationContext.sendBroadcastAsUser(intent,
                new UserHandle(ActivityManager.getCurrentUser()));
    }

    private static void logIfDebuggable(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

    @Override
    public void animateExpandNotificationsPanel() {
        Intent intent = new Intent();
        intent.setComponent(mNotificationCenterComponent);
        mApplicationContext.startActivityAsUser(intent, UserHandle.CURRENT);
    }

    /** Registers the DA organizer. */
    public void register() {
        mCarDeviceProvisionedController.addCallback(mCarDeviceProvisionedListener);
        mCarFullScreenTouchHandler.registerTouchEventListener(
                () -> {
                    if (!mIsImmersive) {
                        logIfDebuggable("Block touch event as not in immersive");
                        return;
                    }
                    // Show Nav bar
                    mCarUiDisplaySystemBarsController.requestImmersiveMode(
                            mApplicationContext.getDisplayId(), WindowInsets.Type.navigationBars());

                    // Notify Launcher
                    Intent intent = new Intent(REQUEST_FROM_SYSTEM_UI);
                    intent.putExtra(INTENT_EXTRA_IS_IMMERSIVE_MODE_STATE, true);
                    mApplicationContext.sendBroadcastAsUser(intent,
                            new UserHandle(ActivityManager.getCurrentUser()));
                }
        );
        BroadcastReceiver immersiveModeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!intent.hasExtra(INTENT_EXTRA_HIDE_SYSTEM_BAR_FOR_IMMERSIVE_MODE)) {
                    return;
                }
                int insetsType = intent.getIntExtra(INTENT_EXTRA_HIDE_SYSTEM_BAR_FOR_IMMERSIVE_MODE,
                        WindowInsets.Type.systemBars());
                logIfDebuggable("Get insetsType from launcher, insetsType = " + insetsType);
                mCarUiDisplaySystemBarsController.requestImmersiveMode(
                        mApplicationContext.getDisplayId(), insetsType);
            }
        };
        mApplicationContext.registerReceiverForAllUsers(immersiveModeChangeReceiver,
                new IntentFilter(REQUEST_FROM_LAUNCHER), null, null, Context.RECEIVER_EXPORTED);
    }
}
