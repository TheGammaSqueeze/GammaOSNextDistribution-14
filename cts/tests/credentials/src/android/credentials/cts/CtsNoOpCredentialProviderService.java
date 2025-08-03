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

package android.credentials.cts;

import android.credentials.ClearCredentialStateException;
import android.credentials.CreateCredentialException;
import android.credentials.GetCredentialException;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.service.credentials.BeginCreateCredentialRequest;
import android.service.credentials.BeginCreateCredentialResponse;
import android.service.credentials.BeginGetCredentialRequest;
import android.service.credentials.BeginGetCredentialResponse;
import android.service.credentials.ClearCredentialStateRequest;
import android.service.credentials.CredentialProviderService;
import android.util.Log;

/**
 * {@link CredentialProviderService} implementation that does not do anything.
 */
public class CtsNoOpCredentialProviderService extends CredentialProviderService {
    private static final String TAG = "CtsNoOpCredentialProviderService";
    private static final String FAKE_APP_PACKAGE = "cts.crm.cps";

    @Override
    public void onBeginGetCredential(BeginGetCredentialRequest request,
            CancellationSignal cancellationSignal,
            OutcomeReceiver<
                    BeginGetCredentialResponse, GetCredentialException> callback) {
        Log.i(TAG, "onBeginGetCredential() final service");
        callback.onResult(new BeginGetCredentialResponse());
    }

    @Override
    public void onBeginCreateCredential(BeginCreateCredentialRequest request,
            CancellationSignal cancellationSignal,
            OutcomeReceiver<BeginCreateCredentialResponse,
                    CreateCredentialException> callback) {
        Log.i(TAG, "onBeginCreateCredential() final service");
        callback.onResult(new BeginCreateCredentialResponse());
    }

    @Override
    public void onClearCredentialState(ClearCredentialStateRequest request,
            CancellationSignal cancellationSignal,
            OutcomeReceiver<Void, ClearCredentialStateException> callback) {
        Log.i(TAG, "onClearCredentialState() final service");
        callback.onResult(null);
    }
}
