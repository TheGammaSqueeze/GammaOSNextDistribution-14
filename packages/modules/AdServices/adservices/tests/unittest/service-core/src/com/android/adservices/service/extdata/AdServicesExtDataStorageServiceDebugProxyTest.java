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

import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_FALSE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_TRUE;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_NO_MANUAL_INTERACTIONS_RECORDED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_MEASUREMENT_CONSENTED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_NOTIFICATION_DISPLAYED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import android.adservices.extdata.AdServicesExtDataParams;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

public class AdServicesExtDataStorageServiceDebugProxyTest {
    private static final String TEST_EXCEPTION_MSG = "Test exception thrown!";
    private static final long NO_APEX_VALUE = -1L;
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

    private AdServicesExtDataStorageServiceDebugProxy mSpyProxy;

    @Mock private SharedPreferences mSharedPreferences;

    @Mock private SharedPreferences.Editor mEditor;

    @Rule
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this).build();

    @Rule public final Expect expect = Expect.create();

    @Before
    public void setup() {
        Context mContext = spy(ApplicationProvider.getApplicationContext());
        doReturn(mSharedPreferences)
                .when(mContext)
                .getSharedPreferences(any(String.class), anyInt());
        doReturn(mEditor).when(mSharedPreferences).edit();
        doReturn(mEditor).when(mEditor).putLong(any(), anyLong());
        doReturn(mEditor).when(mEditor).putBoolean(any(), anyBoolean());
        doReturn(mEditor).when(mEditor).putInt(any(), anyInt());
        doNothing().when(mEditor).apply();
        mSpyProxy = Mockito.spy(AdServicesExtDataStorageServiceDebugProxy.getInstance(mContext));
    }

    @Test
    public void testGetAdServicesExtData_onResultSet() throws Exception {
        doReturn(TEST_PARAMS.getIsNotificationDisplayed())
                .when(mSharedPreferences)
                .getInt(eq("is_notification_displayed"), anyInt());
        doReturn(TEST_PARAMS.getIsMeasurementConsented())
                .when(mSharedPreferences)
                .getInt(eq("is_measurement_consented"), anyInt());
        doReturn(TEST_PARAMS.getIsU18Account())
                .when(mSharedPreferences)
                .getInt(eq("is_u18_account"), anyInt());
        doReturn(TEST_PARAMS.getIsAdultAccount())
                .when(mSharedPreferences)
                .getInt(eq("is_adult_account"), anyInt());
        doReturn(TEST_PARAMS.getManualInteractionWithConsentStatus())
                .when(mSharedPreferences)
                .getInt(eq("manual_interaction_with_consent_status"), anyInt());
        doReturn(TEST_PARAMS.getMeasurementRollbackApexVersion())
                .when(mSharedPreferences)
                .getLong(eq("measurement_rollback_apex_version"), anyLong());

        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        mSpyProxy.getAdServicesExtData(receiver);

        AdServicesExtDataParams adServicesExtDataParams = receiver.assertSuccess();
        expect.that(adServicesExtDataParams.getIsNotificationDisplayed())
                .isEqualTo(TEST_PARAMS.getIsNotificationDisplayed());
        expect.that(adServicesExtDataParams.getIsMeasurementConsented())
                .isEqualTo(TEST_PARAMS.getIsMeasurementConsented());
        expect.that(adServicesExtDataParams.getIsU18Account())
                .isEqualTo(TEST_PARAMS.getIsU18Account());
        expect.that(adServicesExtDataParams.getIsAdultAccount())
                .isEqualTo(TEST_PARAMS.getIsAdultAccount());
        expect.that(adServicesExtDataParams.getManualInteractionWithConsentStatus())
                .isEqualTo(TEST_PARAMS.getManualInteractionWithConsentStatus());
        expect.that(adServicesExtDataParams.getMeasurementRollbackApexVersion())
                .isEqualTo(TEST_PARAMS.getMeasurementRollbackApexVersion());
    }

    @Test
    public void testGetAdServicesExtData_onErrorSet() throws Exception {
        doThrow(new RuntimeException(TEST_EXCEPTION_MSG))
                .when(mSharedPreferences)
                .getInt(anyString(), anyInt());
        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        mSpyProxy.getAdServicesExtData(receiver);
        Exception exception = receiver.assertErrorReceived();
        expect.that(exception).hasMessageThat().isEqualTo(TEST_EXCEPTION_MSG);
    }

    @Test
    public void testSetAdServicesExtData_onResultSet() throws Exception {
        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        mSpyProxy.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST, receiver);
        expect.that(receiver.assertSuccess()).isEqualTo(TEST_PARAMS);
    }

    @Test
    public void testSetAdServicesExtData_onErrorSet() throws Exception {

        doThrow(new RuntimeException(TEST_EXCEPTION_MSG)).when(mEditor).apply();
        AdServicesOutcomeReceiverForTests<AdServicesExtDataParams> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        mSpyProxy.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST, receiver);
        Exception exception = receiver.assertErrorReceived();
        expect.that(exception).hasMessageThat().isEqualTo(TEST_EXCEPTION_MSG);
    }
}
