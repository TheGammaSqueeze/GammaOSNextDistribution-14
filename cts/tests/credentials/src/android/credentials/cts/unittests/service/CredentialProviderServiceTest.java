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

package android.credentials.cts.unittests.service;

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

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CredentialProviderServiceTest {

    @Test
    public void testConstructor() {
        new FakeCredentialProviderService();
    }

    // TODO: Add tests for propagating the OutcomeReceiver results back to the credman service

    static class FakeCredentialProviderService extends CredentialProviderService {
        @Override
        public void onBeginGetCredential(@NonNull BeginGetCredentialRequest request,
                @NonNull CancellationSignal cancellationSignal,
                @NonNull OutcomeReceiver<BeginGetCredentialResponse,
                         GetCredentialException> callback) {

        }

        @Override
        public void onBeginCreateCredential(@NonNull BeginCreateCredentialRequest request,
                @NonNull CancellationSignal cancellationSignal,
                @NonNull OutcomeReceiver<BeginCreateCredentialResponse,
                        CreateCredentialException> callback) {

        }

        @Override
        public void onClearCredentialState(@NonNull ClearCredentialStateRequest request,
                @NonNull CancellationSignal cancellationSignal,
                @NonNull OutcomeReceiver<Void, ClearCredentialStateException> callback) {

        }
    }
}
