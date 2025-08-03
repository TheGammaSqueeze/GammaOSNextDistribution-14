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

/**
 * Interface defining a proxy between Rust and Java implementation of RemoteAuth protocol.
 *
 * @hide
 */
public interface INativeRemoteAuthService {
    /**
     * Interface for RemoteAuth PAL
     *
     * @hide
     */
    interface IPlatform {
        /**
         * Sends message to the remote authenticator
         *
         * @param connectionId connection ID of the {@link android.remoteauth.RemoteAuthenticator}
         * @param request payload of the request
         * @param callback to be used to pass the response result
         * @return true if succeeded, false otherwise.
         * @hide
         */
        boolean sendRequest(int connectionId, byte[] request, ResponseCallback callback);

        /**
         * Interface for a callback to send a response back.
         *
         * @hide
         */
        interface ResponseCallback {
            /**
             * Invoked when message sending succeeds.
             *
             * @param response contains response
             * @hide
             */
            void onSuccess(byte[] response);

            /**
             * Invoked when message sending fails.
             *
             * @param errorCode indicating the error
             * @hide
             */
            void onFailure(int errorCode);
        }
    }
}
