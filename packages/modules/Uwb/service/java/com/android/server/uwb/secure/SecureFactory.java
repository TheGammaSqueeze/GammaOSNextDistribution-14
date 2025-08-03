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

package com.android.server.uwb.secure;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.android.server.uwb.discovery.Transport;
import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.omapi.OmapiConnection;
import com.android.server.uwb.secure.omapi.OmapiConnectionImpl;
import com.android.server.uwb.secure.provisioning.ProvisioningManager;

/**
 * The factory is used to instance the secure session which setup secure channel and
 * exchange UWB parameters.
 */
public class SecureFactory {
    /**
     * Create the instance of SecureSession for an UWB initiator.
     */
    @NonNull
    public static SecureSession makeInitiatorSecureSession(
            @NonNull Context context,
            @NonNull Looper workLooper,
            @NonNull SecureSession.Callback secureSessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo,
            @NonNull Transport transport,
            boolean isController) {
        OmapiConnection omapiConnection = new OmapiConnectionImpl(context);
        SecureElementChannel secureElementChannel = new SecureElementChannel(omapiConnection);
        FiRaSecureChannel fiRaSecureChannel =
                new InitiatorSecureChannel(
                        secureElementChannel, transport, workLooper, runningProfileSessionInfo);
        if (isController) {
            return new ControllerInitiatorSession(
                    workLooper,
                    fiRaSecureChannel,
                    secureSessionCallback,
                    runningProfileSessionInfo);
        } else {
            return new ControleeInitiatorSession(
                    workLooper,
                    fiRaSecureChannel,
                    secureSessionCallback,
                    runningProfileSessionInfo);
        }
    }

    /**
     * Create the instance of SecureSession for an UWB responder.
     */
    @NonNull
    public static SecureSession makeResponderSecureSession(
            @NonNull Context context,
            @NonNull Looper workLooper,
            @NonNull SecureSession.Callback secureSessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo,
            @NonNull Transport transport,
            boolean isController) {
        OmapiConnection omapiConnection = new OmapiConnectionImpl(context);
        SecureElementChannel secureElementChannel = new SecureElementChannel(omapiConnection);
        FiRaSecureChannel fiRaSecureChannel =
                new ResponderSecureChannel(
                        secureElementChannel, transport, workLooper, runningProfileSessionInfo);
        if (isController) {
            return new ControllerResponderSession(
                    workLooper,
                    fiRaSecureChannel,
                    secureSessionCallback,
                    runningProfileSessionInfo);
        } else {
            return new ControleeResponderSession(
                    workLooper,
                    fiRaSecureChannel,
                    secureSessionCallback,
                    runningProfileSessionInfo);
        }
    }

    /** Creates an instance of the {@link ProvisioningManager}. */
    @NonNull
    public static ProvisioningManager makeProvisioningManager(
            @NonNull Context context,
            @NonNull Looper workLooper) {
        OmapiConnection omapiConnection = new OmapiConnectionImpl(context);
        SecureElementChannel secureElementChannel = new SecureElementChannel(omapiConnection);

        return new ProvisioningManager(secureElementChannel, workLooper);
    }
}
