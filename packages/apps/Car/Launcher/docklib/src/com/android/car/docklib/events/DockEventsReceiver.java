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

package com.android.car.docklib.events;

import static android.content.Context.RECEIVER_EXPORTED;

import static com.android.car.dockutil.events.DockEventSenderHelper.EXTRA_COMPONENT;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.car.docklib.DockInterface;
import com.android.car.dockutil.events.DockEvent;
import com.android.car.dockutil.events.DockPermission;

/**
 * BroadcastReceiver for Dock Events.
 */
public class DockEventsReceiver extends BroadcastReceiver {
    private static final String TAG = "DockEventsReceiver";
    private static final boolean DEBUG = Build.isDebuggable();
    private final DockInterface mDockController;

    public DockEventsReceiver(DockInterface dockController) {
        mDockController = dockController;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        DockEvent event = DockEvent.toDockEvent(intent.getAction());
        ComponentName component = intent.getParcelableExtra(EXTRA_COMPONENT, ComponentName.class);

        if (event == null || component == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "DockEvent received of type " + event + " with component: "
                    + component);
        }

        switch (event) {
            case LAUNCH:
                mDockController.appLaunched(component);
                break;
            case PIN:
                mDockController.appPinned(component);
                break;
            case UNPIN:
                mDockController.appUnpinned(component);
                break;
        }
    }

    /**
     * Helper method to register {@link DockEventsReceiver} through context and listen to dock
     * events from packages with required permissions.
     *
     * @param context the context through which the DockEventsReceiver is registered
     * @return successfully registered DockEventsReceiver.
     */
    @NonNull
    public static DockEventsReceiver registerDockReceiver(
            @NonNull Context context,
            @NonNull DockInterface dockController
    ) {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DockEvent.LAUNCH.toString());
        intentFilter.addAction(DockEvent.PIN.toString());
        intentFilter.addAction(DockEvent.UNPIN.toString());
        DockEventsReceiver receiver = new DockEventsReceiver(dockController);
        context.registerReceiver(receiver, intentFilter,
                DockPermission.DOCK_SENDER_PERMISSION.toString(),
                /* handler= */null, RECEIVER_EXPORTED);
        if (DEBUG) {
            Log.d(TAG, "DockReceiver registered from: " + context.getPackageName());
        }
        return receiver;
    }
}
