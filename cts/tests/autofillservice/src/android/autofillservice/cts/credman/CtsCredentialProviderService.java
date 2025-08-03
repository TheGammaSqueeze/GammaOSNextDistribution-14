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

package android.autofillservice.cts.credman;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.CreateCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.provider.BeginCreateCredentialRequest;
import androidx.credentials.provider.BeginCreateCredentialResponse;
import androidx.credentials.provider.BeginGetCredentialOption;
import androidx.credentials.provider.BeginGetCredentialRequest;
import androidx.credentials.provider.BeginGetCredentialResponse;
import androidx.credentials.provider.BeginGetPasswordOption;
import androidx.credentials.provider.CredentialProviderService;
import androidx.credentials.provider.PasswordCredentialEntry;
import androidx.credentials.provider.ProviderClearCredentialStateRequest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * {@link CredentialProviderService} implementation that returns constant credentials. Cts tests
 * can know whether this provider received a request by calling {@link #onReceivedResponse} which
 * waits until the provider receives a {@link #onBeginGetCredentialRequest}.
 */
public class CtsCredentialProviderService extends CredentialProviderService {
    private static final String TAG = "CredentialProviderServiceCts";

    private static CountDownLatch sRespondLatch = new CountDownLatch(1);

    @Override
    public void onBeginCreateCredentialRequest(
            @NonNull BeginCreateCredentialRequest beginCreateCredentialRequest,
            @NonNull CancellationSignal cancellationSignal,
            @NonNull OutcomeReceiver<androidx.credentials.provider.BeginCreateCredentialResponse,
                    CreateCredentialException> outcomeReceiver) {
        Log.i(TAG, "onBeginCreateCredential() final service");
        outcomeReceiver.onResult(new BeginCreateCredentialResponse(List.of(), null));
    }

    @Override
    public void onBeginGetCredentialRequest(@NonNull BeginGetCredentialRequest request,
                                            @NonNull CancellationSignal cancellationSignal,
                                            @NonNull OutcomeReceiver<BeginGetCredentialResponse,
                                                    GetCredentialException> outcomeReceiver) {
        Log.i(TAG, "onBeginGetCredential() final service");
        sRespondLatch.countDown();
        BeginGetCredentialResponse.Builder builder = new BeginGetCredentialResponse.Builder();
        for (BeginGetCredentialOption option: request.getBeginGetCredentialOptions()) {
            if (option instanceof BeginGetPasswordOption) {
                builder.addCredentialEntry(new PasswordCredentialEntry.Builder(
                        getApplicationContext(),
                        "defaultUsername",
                        PendingIntent.getService(
                                getApplicationContext(),
                                /* requestCode= */ 0,
                                new Intent(),
                                PendingIntent.FLAG_IMMUTABLE),
                        (BeginGetPasswordOption) option).build());
            }
        }
        outcomeReceiver.onResult(builder.build());
    }

    @Override
    public void onClearCredentialStateRequest(
            @NonNull ProviderClearCredentialStateRequest providerClearCredentialStateRequest,
            @NonNull CancellationSignal cancellationSignal,
            @NonNull OutcomeReceiver<Void, ClearCredentialException> outcomeReceiver) {
        Log.i(TAG, "onClearCredentialState() final service");
        outcomeReceiver.onResult(null);
    }

    /**
     * Waits for the service to receive a get credential request. If the request was never received,
     * the method throws an exception.
     */
    public static void onReceivedResponse() {
        try {
            if (!sRespondLatch.await(2000, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("CtsCredentialProviderService timed out "
                        + "while expecting a call.");
            }
            //reset for next
            sRespondLatch = new CountDownLatch(1);
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
            Thread.currentThread().interrupt();
            throw new AssertionError("Got InterruptedException while"
                    + " waiting for response.");
        }
    }
}
