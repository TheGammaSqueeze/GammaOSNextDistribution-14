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

package com.android.car.dockutil.events;

import static com.android.car.hidden.apis.HiddenApiAccess.getDisplayId;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.dockutil.Flags;

/**
 * Helper used to send Dock Events.
 */
public class DockEventSenderHelper {
    public static final String EXTRA_COMPONENT = "EXTRA_COMPONENT";

    private final Context mContext;

    public DockEventSenderHelper(Context context) {
        mContext = context;
    }

    /**
     * Used to send launch event to the dock. Generally used when an app is launched.
     */
    public void sendLaunchEvent(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        sendEventBroadcast(DockEvent.LAUNCH, taskInfo);
    }

    /**
     * @see #sendPinEvent(ComponentName)
     */
    public void sendPinEvent(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        sendEventBroadcast(DockEvent.PIN, taskInfo);
    }

    /**
     * Used to send pin event to the dock. Generally used when an app should be pinned to the dock.
     */
    public void sendPinEvent(@NonNull ComponentName componentName) {
        sendEventBroadcast(DockEvent.PIN, componentName);
    }

    /**
     * @see #sendUnpinEvent(ComponentName)
     */
    public void sendUnpinEvent(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        sendEventBroadcast(DockEvent.UNPIN, taskInfo);
    }

    /**
     * Used to send unpin event to the dock. Generally used when an app should be unpinned from the
     * dock.
     */
    public void sendUnpinEvent(@NonNull ComponentName componentName) {
        sendEventBroadcast(DockEvent.UNPIN, componentName);
    }

    @VisibleForTesting
    void sendEventBroadcast(@NonNull DockEvent event,
            @NonNull ActivityManager.RunningTaskInfo taskInfo) {
        if (getDisplayId(taskInfo) != Display.DEFAULT_DISPLAY) {
            return;
        }
        ComponentName component = getComponentName(taskInfo);
        if (component != null) {
            sendEventBroadcast(event, component);
        }
    }

    private void sendEventBroadcast(@NonNull DockEvent event, @NonNull ComponentName component) {
        if (!Flags.dockFeature()) {
            return;
        }

        Intent intent = new Intent();
        intent.setAction(event.toString());
        intent.putExtra(EXTRA_COMPONENT, component);
        mContext.sendBroadcast(intent, DockPermission.DOCK_RECEIVER_PERMISSION.toString());
    }

    @Nullable
    private ComponentName getComponentName(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        if (taskInfo.baseActivity == null && taskInfo.baseIntent.getComponent() == null) {
            return null;
        }
        return taskInfo.baseActivity != null ? taskInfo.baseActivity
                : taskInfo.baseIntent.getComponent();
    }
}
