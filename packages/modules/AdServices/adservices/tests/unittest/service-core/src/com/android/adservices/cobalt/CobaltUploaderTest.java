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

package com.android.adservices.cobalt;

import static android.adservices.cobalt.EncryptedCobaltEnvelopeParams.ENVIRONMENT_DEV;
import static android.adservices.cobalt.EncryptedCobaltEnvelopeParams.ENVIRONMENT_PROD;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilError;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__COBALT_UPLOAD_API_REMOTE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.adservices.cobalt.EncryptedCobaltEnvelopeParams;
import android.adservices.cobalt.IAdServicesCobaltUploadService;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.cobalt.CobaltPipelineType;

import com.google.cobalt.EncryptedMessage;
import com.google.common.truth.Expect;
import com.google.protobuf.ByteString;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public final class CobaltUploaderTest {
    private static final int KEY_INDEX = 5;
    private static final byte[] BYTES = {0x0a, 0x0b, 0x0c};

    public @Rule final Expect expect = Expect.create();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this).spyStatic(ErrorLogUtil.class).build();

    static class AdServicesCobaltUploadServiceStub extends IAdServicesCobaltUploadService.Stub {
        private final List<EncryptedCobaltEnvelopeParams> mParams;

        AdServicesCobaltUploadServiceStub() {
            mParams = new ArrayList<EncryptedCobaltEnvelopeParams>();
        }

        @Override
        public void uploadEncryptedCobaltEnvelope(EncryptedCobaltEnvelopeParams params) {
            mParams.add(params);
            return;
        }

        List<EncryptedCobaltEnvelopeParams> getParams() {
            return mParams;
        }
    }

    @Test
    public void environmentSet_devEnvironment() throws Exception {
        AdServicesCobaltUploadServiceStub interfaceStub = new AdServicesCobaltUploadServiceStub();
        CobaltUploader uploader =
                new CobaltUploader(
                        ApplicationProvider.getApplicationContext(), CobaltPipelineType.DEV);
        CobaltUploader spyUploader = spy(uploader);
        doReturn(interfaceStub).when(spyUploader).getService();

        spyUploader.upload(
                EncryptedMessage.newBuilder()
                        .setKeyIndex(KEY_INDEX)
                        .setCiphertext(ByteString.copyFrom(BYTES))
                        .build());

        List<EncryptedCobaltEnvelopeParams> allParams = interfaceStub.getParams();
        assertThat(allParams).hasSize(1);
        EncryptedCobaltEnvelopeParams params = allParams.get(0);
        expect.that(params.getEnvironment()).isEqualTo(ENVIRONMENT_DEV);
        expect.that(params.getKeyIndex()).isEqualTo(KEY_INDEX);
        expect.that(params.getCipherText()).isEqualTo(BYTES);
    }

    @Test
    public void environmentSet_prodEnvironment() throws Exception {
        AdServicesCobaltUploadServiceStub interfaceStub = new AdServicesCobaltUploadServiceStub();
        CobaltUploader uploader =
                new CobaltUploader(
                        ApplicationProvider.getApplicationContext(), CobaltPipelineType.PROD);
        CobaltUploader spyUploader = spy(uploader);
        doReturn(interfaceStub).when(spyUploader).getService();

        spyUploader.upload(
                EncryptedMessage.newBuilder()
                        .setKeyIndex(KEY_INDEX)
                        .setCiphertext(ByteString.copyFrom(BYTES))
                        .build());

        List<EncryptedCobaltEnvelopeParams> allParams = interfaceStub.getParams();
        assertThat(allParams).hasSize(1);
        EncryptedCobaltEnvelopeParams params = allParams.get(0);
        expect.that(params.getEnvironment()).isEqualTo(ENVIRONMENT_PROD);
        expect.that(params.getKeyIndex()).isEqualTo(KEY_INDEX);
        expect.that(params.getCipherText()).isEqualTo(BYTES);
    }

    @Test
    public void nullService_doesThrow() throws Exception {
        CobaltUploader uploader =
                new CobaltUploader(
                        ApplicationProvider.getApplicationContext(), CobaltPipelineType.PROD);
        CobaltUploader spyUploader = spy(uploader);
        when(spyUploader.getService()).thenReturn(null);

        spyUploader.upload(
                EncryptedMessage.newBuilder()
                        .setKeyIndex(KEY_INDEX)
                        .setCiphertext(ByteString.copyFrom(BYTES))
                        .build());
    }

    @Test
    public void uploadThrowsRemoteException_logsError() throws Exception {
        RemoteException exception = new RemoteException("D'OH!");
        CobaltUploader uploader =
                new CobaltUploader(
                        ApplicationProvider.getApplicationContext(), CobaltPipelineType.PROD);
        CobaltUploader spyUploader = spy(uploader);
        IAdServicesCobaltUploadService interfaceStub =
                new IAdServicesCobaltUploadService.Stub() {
                    @Override
                    public void uploadEncryptedCobaltEnvelope(EncryptedCobaltEnvelopeParams params)
                            throws RemoteException {
                        throw exception;
                    }
                };
        doReturn(interfaceStub).when(spyUploader).getService();
        doNothingOnErrorLogUtilError();

        spyUploader.upload(
                EncryptedMessage.newBuilder()
                        .setKeyIndex(KEY_INDEX)
                        .setCiphertext(ByteString.copyFrom(BYTES))
                        .build());
        verifyErrorLogUtilError(
                exception,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__COBALT_UPLOAD_API_REMOTE_EXCEPTION,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
    }
}
