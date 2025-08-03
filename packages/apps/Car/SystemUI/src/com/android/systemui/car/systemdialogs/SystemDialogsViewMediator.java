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

package com.android.systemui.car.systemdialogs;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;

import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.window.OverlayViewMediator;
import com.android.systemui.dagger.SysUISingleton;

import javax.inject.Inject;

/**
 * A view mediator that listens to events that trigger various system dialogs.
 *
 * NOTE: Even though {@link com.android.systemui.statusbar.phone.StatusBar} listens to these
 * events in core Android, a separate view mediator is created in AAOS since the event listeners
 * need to exist independent of OEM SystemBar configuration (that is, we cannot assume the existence
 * of any particular SystemBars in AAOS).
 */
@SysUISingleton
public class SystemDialogsViewMediator implements OverlayViewMediator {
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final SystemDialogsViewController mSystemDialogsViewController;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DevicePolicyManager.ACTION_SHOW_DEVICE_MONITORING_DIALOG.equals(action)) {
                mSystemDialogsViewController.showDeviceMonitoringDialog();
            }
        }
    };

    @Inject
    public SystemDialogsViewMediator(
            BroadcastDispatcher broadcastDispatcher,
            SystemDialogsViewController systemDialogsViewController) {
        mBroadcastDispatcher = broadcastDispatcher;
        mSystemDialogsViewController = systemDialogsViewController;
    }

    @Override
    public void registerListeners() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(DevicePolicyManager.ACTION_SHOW_DEVICE_MONITORING_DIALOG);
        mBroadcastDispatcher.registerReceiver(mBroadcastReceiver, filter, /* executor= */ null,
                UserHandle.ALL);
    }

    @Override
    public void setUpOverlayContentViewControllers() {
        // no-op.
    }
}
