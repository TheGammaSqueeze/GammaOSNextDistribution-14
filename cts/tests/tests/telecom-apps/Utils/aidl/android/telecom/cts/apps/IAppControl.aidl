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

package android.telecom.cts.apps;

import android.os.Bundle;
import android.os.UserHandle;
import android.telecom.CallAttributes;
import android.telecom.CallEndpoint;
import android.telecom.PhoneAccountHandle;
import android.telecom.PhoneAccount;
import android.telecom.CallException;
import android.telecom.cts.apps.TestAppException;
import android.telecom.cts.apps.NoDataTransaction;
import android.telecom.cts.apps.CallEndpointTransaction;
import android.telecom.cts.apps.AvailableEndpointsTransaction;
import android.telecom.cts.apps.CallExceptionTransaction;
import android.telecom.cts.apps.PhoneAccountTransaction;
import android.telecom.cts.apps.BooleanTransaction;

// Note: This interface is overridden by the following applications:
//  - TransactionalVoipApp_Main,
//  - ConnectionServiceVoipApp_Main,
//  - ManagedConnectionServiceApp
// Which means modifying any of the interface methods requires modifying the overridden methods in
// every application.
interface IAppControl {
    // The following AIDL methods do not currently fail via a TestAppException so they do not need
    // to be wrapped in a Transaction
    boolean isBound();
    List<PhoneAccountHandle> getOwnAccountHandlesForApp();
    List<PhoneAccount> getRegisteredPhoneAccounts();
    void registerCustomPhoneAccount(in PhoneAccount account);
    void unregisterPhoneAccountWithHandle( in PhoneAccountHandle handle);
    UserHandle getProcessUserHandle();
    int getProcessUid();

    // The below AIDL methods all need to return a BaseTransaction:
    NoDataTransaction registerDefaultPhoneAccount();
    PhoneAccountTransaction getDefaultPhoneAccount();
    CallEndpointTransaction getCurrentCallEndpoint(String id);
    AvailableEndpointsTransaction getAvailableCallEndpoints(String id);
    NoDataTransaction addCall(in CallAttributes callAttributes);
    NoDataTransaction setMuteState(String id, boolean isMuted);
    BooleanTransaction isMuted(String id);
    CallExceptionTransaction transitionCallStateTo(String id, int state, boolean expectSuccess, in Bundle extras);
    NoDataTransaction requestCallEndpointChange(String id, in CallEndpoint callEndpoint);
    NoDataTransaction removeNotificationForCall(String callId);
    BooleanTransaction isNotificationPostedForCall(String callId);
}