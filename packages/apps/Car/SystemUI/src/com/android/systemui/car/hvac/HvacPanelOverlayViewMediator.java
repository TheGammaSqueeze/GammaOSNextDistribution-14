/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.car.hvac;

import static com.android.systemui.car.window.OverlayPanelViewController.OVERLAY_FROM_BOTTOM_BAR;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.systembar.CarSystemBarController;
import com.android.systemui.car.window.OverlayViewMediator;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

@SysUISingleton
public class HvacPanelOverlayViewMediator implements OverlayViewMediator {
    private static final boolean DEBUG = false;
    private static final String TAG = "HvacPanelViewMediator";

    private final Context mContext;
    private final CarSystemBarController mCarSystemBarController;
    private final HvacPanelOverlayViewController mHvacPanelOverlayViewController;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final UserTracker mUserTracker;

    @VisibleForTesting
    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.v(TAG, "onReceive: " + intent);
            String action = intent.getAction();

            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                    && mHvacPanelOverlayViewController.isPanelExpanded()) {
                mHvacPanelOverlayViewController.toggle();
            }
        }
    };

    private final UserTracker.Callback mUserTrackerCallback = new UserTracker.Callback() {
        @Override
        public void onUserChanged(int newUser, Context userContext) {
            mBroadcastDispatcher.unregisterReceiver(mBroadcastReceiver);
            mBroadcastDispatcher.registerReceiver(mBroadcastReceiver,
                    new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), /* executor= */ null,
                    mUserTracker.getUserHandle());
        }
    };

    @Inject
    public HvacPanelOverlayViewMediator(
            Context context,
            CarSystemBarController carSystemBarController,
            HvacPanelOverlayViewController hvacPanelOverlayViewController,
            BroadcastDispatcher broadcastDispatcher,
            UserTracker userTracker) {
        mContext = context;
        mCarSystemBarController = carSystemBarController;
        mHvacPanelOverlayViewController = hvacPanelOverlayViewController;
        mBroadcastDispatcher = broadcastDispatcher;
        mUserTracker = userTracker;
    }

    @Override
    public void registerListeners() {
        mCarSystemBarController.registerTopBarTouchListener(
                mHvacPanelOverlayViewController.getDragCloseTouchListener());
        mCarSystemBarController.registerBottomBarTouchListener(
                mHvacPanelOverlayViewController.getDragCloseTouchListener());
        mCarSystemBarController.registerLeftBarTouchListener(
                mHvacPanelOverlayViewController.getDragCloseTouchListener());
        mCarSystemBarController.registerRightBarTouchListener(
                mHvacPanelOverlayViewController.getDragCloseTouchListener());

        mCarSystemBarController.registerHvacPanelController(
                new CarSystemBarController.HvacPanelController() {
                    @Override
                    public void togglePanel() {
                        mHvacPanelOverlayViewController.toggle();
                    }

                    @Override
                    public boolean isHvacPanelOpen() {
                        return mHvacPanelOverlayViewController.isPanelExpanded();
                    }
                });

        mCarSystemBarController.registerHvacPanelOverlayViewController(
                mHvacPanelOverlayViewController);

        mBroadcastDispatcher.registerReceiver(mBroadcastReceiver,
                new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), /* executor= */ null,
                mUserTracker.getUserHandle());
        mUserTracker.addCallback(mUserTrackerCallback, mContext.getMainExecutor());
    }

    @Override
    public void setUpOverlayContentViewControllers() {
        mHvacPanelOverlayViewController.setOverlayDirection(OVERLAY_FROM_BOTTOM_BAR);
    }
}
