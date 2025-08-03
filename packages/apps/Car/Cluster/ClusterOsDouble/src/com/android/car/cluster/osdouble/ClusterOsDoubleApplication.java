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

package com.android.car.cluster.osdouble;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.annotation.NonNull;
import android.app.ActivityOptions;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.DisplayAddress;

/**
 * Application class to start ClusterOsDoubleActivity on the physical cluster display.
 */
public class ClusterOsDoubleApplication extends Application {
    public static final String TAG = "ClusterOsDouble";

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        int displayPort = context.getResources().getInteger(R.integer.config_clusterDisplayPort);
        String displayUniqueId = context.getResources().getString(
                R.string.config_clusterDisplayUniqueId);

        if (displayPort <= 0 && TextUtils.isEmpty(displayUniqueId)) {
            Log.e(TAG, "Cluster display isn't configured.");
            return;
        }

        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        ClusterDisplayMonitor clusterDisplayMonitor = new ClusterDisplayMonitor(context,
                displayManager, displayPort, displayUniqueId);
        clusterDisplayMonitor.start(new Handler(Looper.myLooper()));
    }

    /**
     * Monitors displays and starts the cluster activity when the correct display becomes available.
     */
    private static class ClusterDisplayMonitor {
        private final Context mContext;
        private final DisplayManager mDisplayManager;
        private final int mDisplayPort;
        private final String mDisplayUniqueId;

        private final DisplayManager.DisplayListener mDisplayListener =
                new DisplayManager.DisplayListener() {
                    @Override
                    public void onDisplayAdded(int displayId) {
                        int clusterDisplayId = findClusterDisplayId();
                        if (clusterDisplayId == displayId) {
                            Log.d(TAG, "Display " + displayId + " was added. Starting cluster.");
                            onDisplayReadyForCluster(displayId);
                        }
                    }

                    @Override
                    public void onDisplayRemoved(int displayId) {
                        // No-op
                    }

                    @Override
                    public void onDisplayChanged(int displayId) {
                        // No-op
                    }
                };

        public ClusterDisplayMonitor(Context context, DisplayManager displayManager,
                int displayPort, String displayUniqueId) {
            mContext = context;
            mDisplayManager = displayManager;
            mDisplayPort = displayPort;
            mDisplayUniqueId = displayUniqueId;
        }

        public void start(Handler handler) {
            int clusterDisplayId = findClusterDisplayId();
            if (clusterDisplayId != Display.INVALID_DISPLAY) {
                onDisplayReadyForCluster(clusterDisplayId);
            }
            // This listener will never get unregistered. This is only ok as long as this is a
            // persistent app that is not expected to stop.
            mDisplayManager.registerDisplayListener(mDisplayListener, handler);
        }

        private void onDisplayReadyForCluster(int displayId) {
            Intent intent = Intent.makeMainActivity(
                    ComponentName.createRelative(mContext,
                            ClusterOsDoubleActivity.class.getName()));
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);

            ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId);
            mContext.startActivity(intent, options.toBundle());
        }

        private int findClusterDisplayId() {
            int displayId = Display.INVALID_DISPLAY;
            if (mDisplayPort > 0) {
                displayId = findDisplayByPort(mDisplayPort);
                if (displayId == Display.INVALID_DISPLAY) {
                    Log.e(TAG, "Can't find the display with portId: " + mDisplayPort);
                }
            } else if (!TextUtils.isEmpty(mDisplayUniqueId)) {
                displayId = findDisplayIdByUniqueId(mDisplayUniqueId);
                if (displayId == Display.INVALID_DISPLAY) {
                    Log.e(TAG, "Can't find the display with uniqueId: " + mDisplayUniqueId);
                }
            } else {
                // This should not ever happen.
                Log.wtf(TAG, "No valid cluster display configs found.");
            }

            return displayId;
        }

        private int findDisplayIdByUniqueId(@NonNull String displayUniqueId) {
            for (Display display : mDisplayManager.getDisplays()) {
                if (displayUniqueId.equals(display.getUniqueId())) {
                    return display.getDisplayId();
                }
            }
            return Display.INVALID_DISPLAY;
        }

        private int findDisplayByPort(int displayPort) {
            for (Display display : mDisplayManager.getDisplays()) {
                DisplayAddress address = display.getAddress();
                if (!(address instanceof DisplayAddress.Physical)) {
                    continue;
                }
                DisplayAddress.Physical physical = (DisplayAddress.Physical) address;
                if (physical.getPort() == displayPort) {
                    return display.getDisplayId();
                }
            }
            return Display.INVALID_DISPLAY;
        }
    }

}
