/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.remoteaccess;

import android.car.remoteaccess.RemoteTaskClientRegistrationInfo;

/** @hide */
oneway interface ICarRemoteAccessCallback {
    /**
     * Called when the remote task client is successfully registered or the client ID is
     * updated by CarRemoteAccessService.
     *
     * @param info {@code RemoteTaskClientRegistrationInfo} instance which contains service ID,
     *             vehicle ID, processor ID and client ID.
     */
     void onClientRegistrationUpdated(in RemoteTaskClientRegistrationInfo info);

     /**
      * Called when a serverless remote task client is registered.
      *
      * @param clientId The pre-configured client ID for the serverless remote task client.
      */
    void onServerlessClientRegistered(in String clientId);

    /**
     * Called when registering the remote task client fails.
     */
    void onClientRegistrationFailed();

    /**
     * Called when a remote task request is received from the wake-up service.
     *
     * @param clientID Client ID that is asked to execute the remote task.
     * @param taskId ID of the task that is requested by the remote task server.
     * @param data Extra data passed along with the wake-up request. Can be {@code null}.
     * @param taskMaxDurationInSec The timeout before the system goes back to the previous power
     *                             state.
     */
    void onRemoteTaskRequested(in String clientId, in String taskId, in byte[] data,
            int taskMaxDurationInSec);

    /**
     * Called when the device is about to shutdown.
     */
    void onShutdownStarting();
}
