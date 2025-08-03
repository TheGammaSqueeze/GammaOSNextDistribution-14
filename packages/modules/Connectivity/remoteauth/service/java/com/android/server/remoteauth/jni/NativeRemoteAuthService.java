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

package com.android.server.remoteauth.jni;

import android.util.Log;

import com.android.internal.annotations.Keep;
import com.android.server.remoteauth.jni.INativeRemoteAuthService.IPlatform;

/**
 * A service providing a proxy between Rust implementation and {@link
 * com.android.server.remoteauth.RemoteAuthService}.
 *
 * @hide
 */
public class NativeRemoteAuthService {
    private static final String TAG = NativeRemoteAuthService.class.getSimpleName();

    private IPlatform mPlatform;
    public final Object mNativeLock = new Object();

    // Constructor should receive pointers to:
    // ConnectivityManager, RangingManager and DB
    public NativeRemoteAuthService() {
        System.loadLibrary("remoteauth_jni_rust");
        synchronized (mNativeLock) {
            native_init();
        }
    }

    public void setDeviceListener(final IPlatform platform) {
        mPlatform = platform;
    }

    /**
     * Sends message to the remote authenticator
     *
     * @param connectionId connection ID of the {@link android.remoteauth.RemoteAuthenticator}
     * @param request payload of the request
     * @param responseHandle a handle associated with the request, used to pass the response to the
     *     platform
     * @param platformHandle a handle associated with the platform object, used to pass the response
     *     to the specific platform
     * @hide
     */
    @Keep
    public void sendRequest(
            int connectionId, byte[] request, long responseHandle, long platformHandle) {
        Log.d(TAG, String.format("sendRequest with connectionId: %d, rh: %d, ph: %d",
                connectionId, responseHandle, platformHandle));
        mPlatform.sendRequest(
                connectionId,
                request,
                new IPlatform.ResponseCallback() {
                    @Override
                    public void onSuccess(byte[] response) {
                        synchronized (mNativeLock) {
                            native_on_send_request_success(
                                    response, platformHandle, responseHandle);
                        }
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        synchronized (mNativeLock) {
                            native_on_send_request_error(errorCode, platformHandle, responseHandle);
                        }
                    }
                });
    }

    /* Native functions implemented in JNI */
    // This function should be implemented in remoteauth_jni_android_protocol
    private native boolean native_init();

    private native void native_on_send_request_success(
            byte[] appResponse, long platformHandle, long responseHandle);

    private native void native_on_send_request_error(
            int errorCode, long platformHandle, long responseHandle);
}
