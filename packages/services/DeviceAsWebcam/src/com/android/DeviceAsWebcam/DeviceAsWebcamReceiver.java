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

package com.android.DeviceAsWebcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;

import com.android.DeviceAsWebcam.utils.IgnoredV4L2Nodes;

public class DeviceAsWebcamReceiver extends BroadcastReceiver {
    private static final String TAG = DeviceAsWebcamReceiver.class.getSimpleName();
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    static {
        System.loadLibrary("jni_deviceAsWebcam");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Bundle extras = intent.getExtras();
        boolean connected = extras.getBoolean(UsbManager.USB_CONNECTED);
        boolean uvcSelected = extras.getBoolean(UsbManager.USB_FUNCTION_UVC);
        if (VERBOSE) {
            Log.v(TAG, "Got broadcast with extras" + extras);
        }
        if (!UsbManager.isUvcSupportEnabled()) {
            Log.e(TAG, "UVC support isn't enabled, why do we have DeviceAsWebcam installed ?");
            return;
        }
        if (UsbManager.ACTION_USB_STATE.equals(action) && uvcSelected) {
            String[] ignoredNodes = IgnoredV4L2Nodes.getIgnoredNodes(context);
            if (!DeviceAsWebcamFgService.shouldStartServiceNative(ignoredNodes)) {
                if (VERBOSE) {
                    Log.v(TAG, "Shouldn't start service so returning");
                }
                return;
            }
            Intent fgIntent = new Intent(context, DeviceAsWebcamFgService.class);
            context.startForegroundService(fgIntent);
            if (VERBOSE) {
                Log.v(TAG, "started foreground service");
            }
        }
    }
}
