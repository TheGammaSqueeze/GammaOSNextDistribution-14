/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.server.remoteauth;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.Context;
import android.remoteauth.IDeviceDiscoveryListener;
import android.remoteauth.IRemoteAuthService;

import com.android.internal.util.Preconditions;

/** Service implementing remoteauth functionality. */
public class RemoteAuthService extends IRemoteAuthService.Stub {
    public static final String TAG = "RemoteAuthService";
    public static final String SERVICE_NAME = Context.REMOTE_AUTH_SERVICE;

    public RemoteAuthService(Context context) {
        Preconditions.checkNotNull(context);
        // TODO(b/290280702): Create here RemoteConnectivityManager and RangingManager
    }

    @Override
    public boolean isRemoteAuthSupported() {
        // TODO(b/297301535): checkPermission(mContext, MANAGE_REMOTE_AUTH);
        // TODO(b/290676192): integrate with RangingManager
        //  (check if UWB is supported by this device)
        return true;
    }

    @Override
    public boolean registerDiscoveryListener(
            IDeviceDiscoveryListener deviceDiscoveryListener,
            @UserIdInt int userId,
            int timeoutMs,
            String packageName,
            @Nullable String attributionTag) {
        // TODO(b/297301535): checkPermission(mContext, MANAGE_REMOTE_AUTH);
        // TODO(b/290280702): implement register discovery logic
        return true;
    }

    @Override
    public void unregisterDiscoveryListener(
            IDeviceDiscoveryListener deviceDiscoveryListener,
            @UserIdInt int userId,
            String packageName,
            @Nullable String attributionTag) {
        // TODO(b/297301535): checkPermission(mContext, MANAGE_REMOTE_AUTH);
        // TODO(b/290094221): implement unregister logic
    }

    private static void checkPermission(Context context, String permission) {
        context.enforceCallingOrSelfPermission(
                permission, "Must have " + permission + " permission.");
    }
}
