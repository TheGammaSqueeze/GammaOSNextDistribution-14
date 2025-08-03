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

package com.android.adservices.service.extdata;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_FALSE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_TRUE;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_NO_MANUAL_INTERACTIONS_RECORDED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_MEASUREMENT_CONSENTED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_NOTIFICATION_DISPLAYED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.extdata.AdServicesExtDataParams;
import android.adservices.extdata.GetAdServicesExtDataResult;
import android.adservices.extdata.IAdServicesExtDataStorageService;
import android.adservices.extdata.IGetAdServicesExtDataCallback;
import android.content.Context;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

public class AdServicesExtDataStorageServiceWorkerTest {
    private static final long NO_APEX_VALUE = -1L;
    private static final String TEST_EXCEPTION_MESSAGE = "Test Exception!";
    private static final AdServicesExtDataParams TEST_PARAMS =
            new AdServicesExtDataParams.Builder()
                    .setNotificationDisplayed(BOOLEAN_TRUE)
                    .setMsmtConsent(BOOLEAN_FALSE)
                    .setIsU18Account(BOOLEAN_TRUE)
                    .setIsAdultAccount(BOOLEAN_FALSE)
                    .setManualInteractionWithConsentStatus(STATE_NO_MANUAL_INTERACTIONS_RECORDED)
                    .setMsmtRollbackApexVersion(NO_APEX_VALUE)
                    .build();
    private static final int[] TEST_FIELD_LIST = {
        FIELD_IS_MEASUREMENT_CONSENTED, FIELD_IS_NOTIFICATION_DISPLAYED
    };

    @Rule public final Expect expect = Expect.create();

    private AdServicesExtDataStorageServiceWorker mSpyWorker;

    @Mock private Flags mFlags;

    @Rule
    public final AdServicesExtendedMockitoRule mockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .mockStatic(FlagsFactory.class)
                    .spyStatic(AdServicesExtDataStorageServiceDebugProxy.class)
                    .build();

    @Mock private AdServicesExtDataStorageServiceDebugProxy mDebugProxy;

    @Before
    public void setup() {
        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(mDebugProxy)
                .when(
                        () ->
                                AdServicesExtDataStorageServiceDebugProxy.getInstance(
                                        any(Context.class)));
        mSpyWorker =
                spy(
                        AdServicesExtDataStorageServiceWorker.getInstance(
                                ApplicationProvider.getApplicationContext()));
    }

    @Test
    public void testGetAdServicesExtData_serviceNotFound_resultsInOnErrorSet() throws Exception {
        doReturn(null).when(mSpyWorker).getService();
        when(mFlags.getEnableAdExtServiceDebugProxy()).thenReturn(false);
        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        mSpyWorker.getAdServicesExtData(receiver);

        Exception exception = receiver.assertErrorReceived();
        expect.that(exception).isInstanceOf(IllegalStateException.class);
        verify(mSpyWorker, never()).unbindFromService();
    }

    @Test
    public void testGetAdServicesExtData_onResultSet() throws Exception {
        doReturn(getMockService(/* isSuccess */ true)).when(mSpyWorker).getService();
        when(mFlags.getEnableAdExtServiceDebugProxy()).thenReturn(false);
        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        mSpyWorker.getAdServicesExtData(receiver);

        AdServicesExtDataParams result = receiver.assertSuccess();
        expect.that(result).isEqualTo(TEST_PARAMS);
        verify(mSpyWorker).unbindFromService();
    }

    @Test
    public void testGetAdServicesExtData_onErrorSet() throws Exception {
        doReturn(getMockService(/* isSuccess */ false)).when(mSpyWorker).getService();
        when(mFlags.getEnableAdExtServiceDebugProxy()).thenReturn(false);
        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        mSpyWorker.getAdServicesExtData(receiver);

        Exception exception = receiver.assertErrorReceived();
        expect.that(exception).hasMessageThat().isEqualTo(TEST_EXCEPTION_MESSAGE);
        verify(mSpyWorker).unbindFromService();
    }

    @Test
    public void testSetAdServicesExtData_serviceNotFound_resultsInOnErrorSet() throws Exception {
        doReturn(null).when(mSpyWorker).getService();
        when(mFlags.getEnableAdExtServiceDebugProxy()).thenReturn(false);
        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        mSpyWorker.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST, receiver);

        Exception exception = receiver.assertErrorReceived();
        expect.that(exception).isInstanceOf(IllegalStateException.class);
        verify(mSpyWorker, never()).unbindFromService();
    }

    @Test
    public void testSetAdServicesExtData_onResultSet() throws Exception {
        doReturn(getMockService(/* isSuccess */ true)).when(mSpyWorker).getService();
        when(mFlags.getEnableAdExtServiceDebugProxy()).thenReturn(false);
        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        mSpyWorker.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST, receiver);

        AdServicesExtDataParams result = receiver.assertSuccess();
        expect.that(result).isEqualTo(TEST_PARAMS);
        verify(mSpyWorker).unbindFromService();
    }

    @Test
    public void testSetAdServicesExtData_onErrorSet() throws Exception {
        doReturn(getMockService(/* isSuccess */ false)).when(mSpyWorker).getService();
        when(mFlags.getEnableAdExtServiceDebugProxy()).thenReturn(false);
        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        mSpyWorker.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST, receiver);

        Exception exception = receiver.assertErrorReceived();
        expect.that(exception).hasMessageThat().isEqualTo(TEST_EXCEPTION_MESSAGE);
        verify(mSpyWorker).unbindFromService();
    }

    @Test
    public void testSetAdServicesExtData_serviceNotFound_useProxy() throws Exception {
        when(mFlags.getEnableAdExtServiceDebugProxy()).thenReturn(true);
        doReturn(null).when(mSpyWorker).getService();
        doNothing().when(mDebugProxy).setAdServicesExtData(any(), any(), any());
        mSpyWorker.setProxy(mDebugProxy);
        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        mSpyWorker.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST, receiver);
        verify(mDebugProxy).setAdServicesExtData(any(), any(), any());
        verify(mSpyWorker, never()).unbindFromService();
    }

    @Test
    public void testGetAdServicesExtData_serviceNotFound_useProxy() throws Exception {
        when(mFlags.getEnableAdExtServiceDebugProxy()).thenReturn(true);
        doReturn(null).when(mSpyWorker).getService();
        doNothing().when(mDebugProxy).getAdServicesExtData(any());
        mSpyWorker.setProxy(mDebugProxy);
        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        doNothing().when(mDebugProxy).getAdServicesExtData(any());
        mSpyWorker.getAdServicesExtData(receiver);
        verify(mDebugProxy).getAdServicesExtData(any());
        verify(mSpyWorker, never()).unbindFromService();
    }

    private IAdServicesExtDataStorageService getMockService(boolean isSuccess) {
        return new IAdServicesExtDataStorageService.Stub() {
            @Override
            public void getAdServicesExtData(IGetAdServicesExtDataCallback callback)
                    throws RemoteException {
                if (isSuccess) {
                    GetAdServicesExtDataResult result =
                            new GetAdServicesExtDataResult.Builder()
                                    .setStatusCode(STATUS_SUCCESS)
                                    .setErrorMessage("")
                                    .setAdServicesExtDataParams(TEST_PARAMS)
                                    .build();
                    callback.onResult(result);
                } else {
                    callback.onError(TEST_EXCEPTION_MESSAGE);
                }
            }

            @Override
            public void putAdServicesExtData(
                    AdServicesExtDataParams params,
                    int[] fields,
                    IGetAdServicesExtDataCallback callback)
                    throws RemoteException {
                if (isSuccess) {
                    GetAdServicesExtDataResult result =
                            new GetAdServicesExtDataResult.Builder()
                                    .setStatusCode(STATUS_SUCCESS)
                                    .setErrorMessage("")
                                    .setAdServicesExtDataParams(params)
                                    .build();
                    callback.onResult(result);
                } else {
                    callback.onError(TEST_EXCEPTION_MESSAGE);
                }
            }
        };
    }
}
