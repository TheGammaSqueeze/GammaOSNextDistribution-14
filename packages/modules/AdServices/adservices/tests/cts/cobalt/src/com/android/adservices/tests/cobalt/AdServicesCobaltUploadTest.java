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
package com.android.adservices.tests.cobalt;

import static android.adservices.cobalt.EncryptedCobaltEnvelopeParams.ENVIRONMENT_DEV;
import static android.adservices.cobalt.EncryptedCobaltEnvelopeParams.ENVIRONMENT_PROD;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.cobalt.AdServicesCobaltUploadService;
import android.adservices.cobalt.EncryptedCobaltEnvelopeParams;
import android.adservices.cobalt.IAdServicesCobaltUploadService;
import android.content.Intent;
import android.os.IBinder;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AdServicesCobaltUploadTest {
    private static final int KEY_INDEX = 5;
    private static final byte[] BYTES = {0x0a, 0x0b, 0x0c};

    static final class AdServicesCobaltUploadServiceProxy extends AdServicesCobaltUploadService {
        private EncryptedCobaltEnvelopeParams mParams;

        AdServicesCobaltUploadServiceProxy() {
            mParams = null;
        }

        @Override
        public void onUploadEncryptedCobaltEnvelope(EncryptedCobaltEnvelopeParams params) {
            mParams = params;
            return;
        }

        public EncryptedCobaltEnvelopeParams getEncryptedCobaltEnvelopeParams() {
            return mParams;
        }
    }

    @Test
    public void adServicesCobaltUploadService_devEnvironment() throws Exception {
        AdServicesCobaltUploadServiceProxy proxy = new AdServicesCobaltUploadServiceProxy();
        Intent intent = new Intent();
        intent.setAction(AdServicesCobaltUploadService.SERVICE_INTERFACE);
        IBinder remoteObject = proxy.onBind(intent);
        assertThat(remoteObject).isNotNull();

        IAdServicesCobaltUploadService service =
                IAdServicesCobaltUploadService.Stub.asInterface(remoteObject);
        assertThat(service).isNotNull();

        service.uploadEncryptedCobaltEnvelope(
                new EncryptedCobaltEnvelopeParams(ENVIRONMENT_DEV, KEY_INDEX, BYTES));
        EncryptedCobaltEnvelopeParams params = proxy.getEncryptedCobaltEnvelopeParams();
        assertThat(params.getEnvironment()).isEqualTo(ENVIRONMENT_DEV);
        assertThat(params.getKeyIndex()).isEqualTo(KEY_INDEX);
        assertThat(params.getCipherText()).isEqualTo(BYTES);
    }

    @Test
    public void adServicesCobaltUploadService_prodEnvironment() throws Exception {
        AdServicesCobaltUploadServiceProxy proxy = new AdServicesCobaltUploadServiceProxy();
        Intent intent = new Intent();
        intent.setAction(AdServicesCobaltUploadService.SERVICE_INTERFACE);
        IBinder remoteObject = proxy.onBind(intent);
        assertThat(remoteObject).isNotNull();

        IAdServicesCobaltUploadService service =
                IAdServicesCobaltUploadService.Stub.asInterface(remoteObject);
        assertThat(service).isNotNull();

        service.uploadEncryptedCobaltEnvelope(
                new EncryptedCobaltEnvelopeParams(ENVIRONMENT_PROD, KEY_INDEX, BYTES));
        EncryptedCobaltEnvelopeParams params = proxy.getEncryptedCobaltEnvelopeParams();
        assertThat(params.getEnvironment()).isEqualTo(ENVIRONMENT_PROD);
        assertThat(params.getKeyIndex()).isEqualTo(KEY_INDEX);
        assertThat(params.getCipherText()).isEqualTo(BYTES);
    }
}
