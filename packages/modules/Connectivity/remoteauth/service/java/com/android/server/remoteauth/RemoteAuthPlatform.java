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

package com.android.server.remoteauth;

import android.util.Log;

import com.android.server.remoteauth.connectivity.Connection;
import com.android.server.remoteauth.jni.INativeRemoteAuthService;

/** Implementation of the {@link INativeRemoteAuthService.IPlatform} interface. */
public class RemoteAuthPlatform implements INativeRemoteAuthService.IPlatform {
    public static final String TAG = "RemoteAuthPlatform";
    private final RemoteAuthConnectionCache mConnectionCache;

    public RemoteAuthPlatform(RemoteAuthConnectionCache connectionCache) {
        mConnectionCache = connectionCache;
    }

    /**
     * Sends message to the remote device via {@link Connection} created by
     * {@link com.android.server.remoteauth.connectivity.ConnectivityManager}.
     *
     * @param connectionId connection ID of the {@link android.remoteauth.RemoteAuthenticator}
     * @param request payload of the request
     * @param callback to be used to pass the response result
     * @return true if succeeded, false otherwise.
     * @hide
     */
    @Override
    public boolean sendRequest(int connectionId, byte[] request, ResponseCallback callback) {
        Connection connection = mConnectionCache.getConnection(connectionId);
        if (null == connection) {
            Log.e(TAG, String.format("Failed to get a connection for: %d", connectionId));
            return false;
        }
        connection.sendRequest(
                request,
                new Connection.MessageResponseCallback() {
                    @Override
                    public void onSuccess(byte[] buffer) {
                        callback.onSuccess(buffer);
                    }

                    @Override
                    public void onFailure(@Connection.ErrorCode int errorCode) {
                        callback.onFailure(errorCode);
                    }
                });
        return true;
    }
}
