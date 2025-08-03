/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.cts.emptydeviceowner;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.compatibility.common.util.enterprise.DeviceAdminReceiverUtils;

public class EmptyDeviceAdmin extends DeviceAdminReceiver {

    private static final String TAG = EmptyDeviceAdmin.class.getSimpleName();

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, EmptyDeviceAdmin.class);
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        // Normally DeviceOwnerChangedReceiver should be triggered by ACTION_DEVICE_OWNER_CHANGED
        // however there could be a race condition where the package is still in stopped state
        // when ACTION_DEVICE_OWNER_CHANGED is being sent, resulting in the broadcast not
        // received. To mitigate that, try manually running DeviceOwnerChangedReceiver as well.
        // This should be safe as long as DeviceOwnerChangedReceiver is idempotent.
        context.sendBroadcast(new Intent(DeviceOwnerChangedReceiver.ACTION_TRANSFER_DEVICE_OWNER)
                .setComponent(new ComponentName(context, DeviceOwnerChangedReceiver.class)));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive(): user=" + context.getUserId() + ", action=" + action);

        if (DeviceAdminReceiverUtils.disableSelf(context, intent)) return;

        super.onReceive(context, intent);
    }
}
