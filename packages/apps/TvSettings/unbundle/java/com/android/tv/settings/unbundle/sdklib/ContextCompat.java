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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.UserIdInt;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.UserHandle;

public class ContextCompat {
    public static final String SENSOR_PRIVACY_SERVICE = Context.SENSOR_PRIVACY_SERVICE;

    public ContextCompat(Context context) {
        mContext = context;
    }

    private final Context mContext;

    public void sendBroadcastAsUser(@RequiresPermission Intent intent, UserHandle handle) {
        mContext.sendBroadcastAsUser(intent, handle);
    }

    public static final String OEM_LOCK_SERVICE = "oem_lock";

    public @UserIdInt
    int getUserId() {
        return mContext.getUserId();
    }

    public ComponentName startServiceAsUser(Intent service, UserHandle user) {
        return mContext.startServiceAsUser(service, user);
    }

    public void startActivityAsUser(
            @RequiresPermission @NonNull Intent intent, @NonNull UserHandle user) {
        mContext.startActivityAsUser(intent, user);
    }

    public boolean stopServiceAsUser(Intent service, UserHandle user) {
        return mContext.stopServiceAsUser(service, user);
    }

    public Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user,
            IntentFilter filter, @Nullable String broadcastPermission,
            @Nullable Handler scheduler) {
        return mContext.registerReceiverAsUser(
                receiver, user, filter, broadcastPermission, scheduler);
    }
}
