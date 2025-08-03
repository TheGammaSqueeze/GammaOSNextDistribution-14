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

package android.telecom.cts.selfmanagedcstestapp;

import android.telecom.PhoneAccountHandle;
import android.telecom.PhoneAccount;

interface ICtsSelfManagedConnectionServiceControl {
    void init();
    void deInit();
    boolean waitForBinding();
    boolean waitForUpdate(int lock);
    void registerPhoneAccount(in PhoneAccount phoneAccount);
    void unregisterPhoneAccount(in PhoneAccountHandle phoneAccountHandle);
    boolean isConnectionAvailable();
    boolean waitOnHold();
    boolean waitOnUnHold();
    boolean waitOnDisconnect();
    boolean initiateIncomingCall(in PhoneAccountHandle handle, in String uri);
    boolean placeOutgoingCall(in PhoneAccountHandle handle, in String uri);
    boolean placeIncomingCall(in PhoneAccountHandle handle,
            in String uri, in int videoState);
    boolean isIncomingCall();
    boolean waitOnAnswer();
    boolean getAudioModeIsVoip();
    int getConnectionState();
    int getOnShowIncomingUiInvokeCounter();
    void setConnectionCapabilityNoHold();
    void setConnectionActive();
    void disconnectConnection();
}
