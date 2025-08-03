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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_FALSE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_TRUE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_NO_MANUAL_INTERACTIONS_RECORDED;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_ADULT_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_MEASUREMENT_CONSENTED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_NOTIFICATION_DISPLAYED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_U18_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlags;
import static com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager.UNKNOWN_PACKAGE_NAME;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__ADEXT_DATA_SERVICE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SERVICES_EXT_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.extdata.AdServicesExtDataParams;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class AdServicesExtDataStorageServiceManagerTest {
    private static final long TIMEOUT = 5_000L;
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

    @Rule
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(AdServicesExtDataStorageServiceWorker.class)
                    .spyStatic(AdServicesLoggerImpl.class)
                    .spyStatic(ErrorLogUtil.class)
                    .spyStatic(FlagsFactory.class)
                    .build();

    @Rule public final Expect expect = Expect.create();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final AdServicesLogger mAdServicesLogger = spy(AdServicesLoggerImpl.getInstance());

    @Mock private AdServicesExtDataStorageServiceWorker mMockWorker;
    @Mock private Flags mFlags;
    @Captor private ArgumentCaptor<AdServicesExtDataParams> mParamsCaptor;
    @Captor private ArgumentCaptor<int[]> mFieldsCaptor;

    private AdServicesExtDataStorageServiceManager mManager;

    @Before
    public void setup() {
        // mock ErrorLogUtil for logging in the callbacks
        doNothingOnErrorLogUtilError();

        // mock the device config read for checking debug proxy
        doReturn(false).when(mFlags).getEnableAdExtServiceDebugProxy();
        mockGetFlags(mFlags);

        doReturn(mMockWorker)
                .when(() -> AdServicesExtDataStorageServiceWorker.getInstance(mContext));
        doReturn(mAdServicesLogger).when(AdServicesLoggerImpl::getInstance);
        mManager = AdServicesExtDataStorageServiceManager.getInstance(mContext);
    }

    @Test
    public void testGetAdServicesExtData_onResultSet_returnsParams() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);

        AdServicesExtDataParams result = mManager.getAdServicesExtData();
        expect.that(result.getIsMeasurementConsented()).isEqualTo(BOOLEAN_FALSE);
        expect.that(result.getMeasurementRollbackApexVersion()).isEqualTo(NO_APEX_VALUE);
        expect.that(result.getIsU18Account()).isEqualTo(BOOLEAN_TRUE);
        expect.that(result.getIsAdultAccount()).isEqualTo(BOOLEAN_FALSE);
        expect.that(result.getManualInteractionWithConsentStatus())
                .isEqualTo(STATE_NO_MANUAL_INTERACTIONS_RECORDED);
        expect.that(result.getIsNotificationDisplayed()).isEqualTo(BOOLEAN_TRUE);

        verifyLogging(AD_SERVICES_API_CALLED__API_NAME__GET_AD_SERVICES_EXT_DATA, STATUS_SUCCESS);
    }

    @Test
    public void testGetAdServicesExtData_onErrorSet_returnsDefaultParams() {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(0))
                                    .onError(new RuntimeException("Testing exception thrown"));
                            return null;
                        })
                .when(mMockWorker)
                .getAdServicesExtData(any());

        AdServicesExtDataParams result = mManager.getAdServicesExtData();
        expect.that(result.getIsMeasurementConsented()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getMeasurementRollbackApexVersion()).isEqualTo(NO_APEX_VALUE);
        expect.that(result.getIsU18Account()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getIsAdultAccount()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getManualInteractionWithConsentStatus()).isEqualTo(STATE_UNKNOWN);
        expect.that(result.getIsNotificationDisplayed()).isEqualTo(BOOLEAN_UNKNOWN);

        verifyLogging(
                AD_SERVICES_API_CALLED__API_NAME__GET_AD_SERVICES_EXT_DATA, STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testGetAdServicesExtData_timedOut_returnsDefaultParams() {
        doAnswer(
                        (invocation) -> {
                            Thread.sleep(TIMEOUT);
                            return null;
                        })
                .when(mMockWorker)
                .getAdServicesExtData(any());

        AdServicesExtDataParams result = mManager.getAdServicesExtData();
        expect.that(result.getIsMeasurementConsented()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getMeasurementRollbackApexVersion()).isEqualTo(NO_APEX_VALUE);
        expect.that(result.getIsU18Account()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getIsAdultAccount()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getManualInteractionWithConsentStatus()).isEqualTo(STATE_UNKNOWN);
        expect.that(result.getIsNotificationDisplayed()).isEqualTo(BOOLEAN_UNKNOWN);

        verifyLogging(AD_SERVICES_API_CALLED__API_NAME__GET_AD_SERVICES_EXT_DATA, STATUS_TIMEOUT);
    }

    @Test
    public void testSetAdServicesExtData_emptyFieldList_returnEarly() {
        expect.that(mManager.setAdServicesExtData(TEST_PARAMS, new int[] {})).isTrue();
        verifyZeroInteractions(mMockWorker);
    }

    @Test
    public void testSetAdServicesExtData_onResultSet_returnsTrue() {
        mockWorkerSetAdExtDataCall();

        expect.that(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST)).isTrue();

        verify(mMockWorker).setAdServicesExtData(any(), any(), any());

        verifyLogging(AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA, STATUS_SUCCESS);
    }

    @Test
    public void testSetAdServicesExtData_onErrorSet_returnsFalse() {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(2))
                                    .onError(new RuntimeException("Testing exception thrown"));
                            return null;
                        })
                .when(mMockWorker)
                .setAdServicesExtData(any(), any(), any());

        expect.that(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST)).isFalse();

        verify(mMockWorker).setAdServicesExtData(any(), any(), any());

        verifyLogging(
                AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA, STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testSetAdServicesExtData_timedOut_returnsFalse() {
        doAnswer(
                        (invocation) -> {
                            Thread.sleep(TIMEOUT);
                            return null;
                        })
                .when(mMockWorker)
                .setAdServicesExtData(any(), any(), any());

        expect.that(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST)).isFalse();

        verify(mMockWorker).setAdServicesExtData(any(), any(), any());

        verifyLogging(AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA, STATUS_TIMEOUT);
    }

    @Test
    public void testUpdateRequestToStr_emptyFields() {
        expect.that(mManager.updateRequestToString(TEST_PARAMS, new int[] {})).isEqualTo("{}");
    }

    @Test
    public void testUpdateRequestToStr_nonEmptyFields() {
        expect.that(mManager.updateRequestToString(TEST_PARAMS, TEST_FIELD_LIST))
                .isEqualTo("{MsmtConsent: 0,NotificationDisplayed: 1,}");
    }

    @Test
    public void testSetMsmtConsent_withFalse() {
        mockWorkerSetAdExtDataCall();
        expect.that(mManager.setMsmtConsent(false)).isTrue();
        expect.that(mParamsCaptor.getValue().getIsMeasurementConsented()).isEqualTo(BOOLEAN_FALSE);
        expect.that(mFieldsCaptor.getValue())
                .asList()
                .containsExactly(FIELD_IS_MEASUREMENT_CONSENTED);
    }

    @Test
    public void testSetMsmtConsent_withTrue() {
        mockWorkerSetAdExtDataCall();
        expect.that(mManager.setMsmtConsent(true)).isTrue();
        expect.that(mParamsCaptor.getValue().getIsMeasurementConsented()).isEqualTo(BOOLEAN_TRUE);
        expect.that(mFieldsCaptor.getValue())
                .asList()
                .containsExactly(FIELD_IS_MEASUREMENT_CONSENTED);
    }

    @Test
    public void testGetMsmtConsent_withZeroRetrieved_returnsFalse() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);
        expect.that(mManager.getMsmtConsent()).isFalse();
    }

    @Test
    public void testGetMsmtConsent_withOneRetrieved_returnsTrue() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder().setMsmtConsent(BOOLEAN_TRUE).build();
        mockWorkerGetAdExtDataCall(params);
        expect.that(mManager.getMsmtConsent()).isTrue();
    }

    @Test
    public void testGetManualInteractionWithConsentStatus() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);
        expect.that(mManager.getManualInteractionWithConsentStatus())
                .isEqualTo(STATE_NO_MANUAL_INTERACTIONS_RECORDED);
    }

    @Test
    public void testSetManualInteractionWithConsentStatus() {
        mockWorkerSetAdExtDataCall();
        expect.that(
                        mManager.setManualInteractionWithConsentStatus(
                                STATE_NO_MANUAL_INTERACTIONS_RECORDED))
                .isTrue();
        expect.that(mParamsCaptor.getValue().getManualInteractionWithConsentStatus())
                .isEqualTo(STATE_NO_MANUAL_INTERACTIONS_RECORDED);
        expect.that(mFieldsCaptor.getValue())
                .asList()
                .containsExactly(FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS);
    }

    @Test
    public void testSetNotifDisplayed_withFalse() {
        mockWorkerSetAdExtDataCall();
        expect.that(mManager.setNotificationDisplayed(false)).isTrue();
        expect.that(mParamsCaptor.getValue().getIsNotificationDisplayed()).isEqualTo(BOOLEAN_FALSE);
        expect.that(mFieldsCaptor.getValue())
                .asList()
                .containsExactly(FIELD_IS_NOTIFICATION_DISPLAYED);
    }

    @Test
    public void testSetNotifDisplayed_withTrue() {
        mockWorkerSetAdExtDataCall();
        expect.that(mManager.setNotificationDisplayed(true)).isTrue();
        expect.that(mParamsCaptor.getValue().getIsNotificationDisplayed()).isEqualTo(BOOLEAN_TRUE);
        expect.that(mFieldsCaptor.getValue())
                .asList()
                .containsExactly(FIELD_IS_NOTIFICATION_DISPLAYED);
    }

    @Test
    public void testGetNotifDisplayed_withOneRetrieved_returnsTrue() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);
        expect.that(mManager.getNotificationDisplayed()).isTrue();
    }

    @Test
    public void testGetNotifDisplayed_withZeroRetrieved_returnsFalse() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder()
                        .setNotificationDisplayed(BOOLEAN_FALSE)
                        .build();
        mockWorkerGetAdExtDataCall(params);
        expect.that(mManager.getNotificationDisplayed()).isFalse();
    }

    @Test
    public void testSetIsAdultAccount_withFalse() {
        mockWorkerSetAdExtDataCall();
        expect.that(mManager.setIsAdultAccount(false)).isTrue();
        expect.that(mParamsCaptor.getValue().getIsAdultAccount()).isEqualTo(BOOLEAN_FALSE);
        expect.that(mFieldsCaptor.getValue()).asList().containsExactly(FIELD_IS_ADULT_ACCOUNT);
    }

    @Test
    public void testSetIsAdultAccount_withTrue() {
        mockWorkerSetAdExtDataCall();
        expect.that(mManager.setIsAdultAccount(true)).isTrue();
        expect.that(mParamsCaptor.getValue().getIsAdultAccount()).isEqualTo(BOOLEAN_TRUE);
        expect.that(mFieldsCaptor.getValue()).asList().containsExactly(FIELD_IS_ADULT_ACCOUNT);
    }

    @Test
    public void testGetIsAdultAccount_withZeroRetrieved_returnsFalse() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);
        expect.that(mManager.getIsAdultAccount()).isFalse();
    }

    @Test
    public void testGetIsAdultAccount_withOneRetrieved_returnsTrue() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder().setIsAdultAccount(BOOLEAN_TRUE).build();
        mockWorkerGetAdExtDataCall(params);
        expect.that(mManager.getIsAdultAccount()).isTrue();
    }

    @Test
    public void testSetIsU18Account_withFalse() {
        mockWorkerSetAdExtDataCall();
        expect.that(mManager.setIsU18Account(false)).isTrue();
        expect.that(mParamsCaptor.getValue().getIsU18Account()).isEqualTo(BOOLEAN_FALSE);
        expect.that(mFieldsCaptor.getValue()).asList().containsExactly(FIELD_IS_U18_ACCOUNT);
    }

    @Test
    public void testSetIsU18Account_withTrue() {
        mockWorkerSetAdExtDataCall();
        expect.that(mManager.setIsU18Account(true)).isTrue();
        expect.that(mParamsCaptor.getValue().getIsU18Account()).isEqualTo(BOOLEAN_TRUE);
        expect.that(mFieldsCaptor.getValue()).asList().containsExactly(FIELD_IS_U18_ACCOUNT);
    }

    @Test
    public void testGetIsU18Account_withOneRetrieved_returnsTrue() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);
        expect.that(mManager.getIsU18Account()).isTrue();
    }

    @Test
    public void testGetIsU18Account_withZeroRetrieved_returnsFalse() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder().setIsU18Account(BOOLEAN_FALSE).build();
        mockWorkerGetAdExtDataCall(params);
        expect.that(mManager.getIsU18Account()).isFalse();
    }

    @Test
    public void testSetMeasurementRollbackApexVersion_withValue() {
        mockWorkerSetAdExtDataCall();
        long apex = 1000L;
        expect.that(mManager.setMeasurementRollbackApexVersion(apex)).isTrue();
        expect.that(mParamsCaptor.getValue().getMeasurementRollbackApexVersion()).isEqualTo(apex);
        expect.that(mFieldsCaptor.getValue())
                .asList()
                .containsExactly(FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION);
    }

    @Test
    public void testSetMeasurementRollbackApexVersion_withNotFoundValue() {
        mockWorkerSetAdExtDataCall();
        expect.that(mManager.setMeasurementRollbackApexVersion(NO_APEX_VALUE)).isTrue();
        expect.that(mParamsCaptor.getValue().getMeasurementRollbackApexVersion())
                .isEqualTo(NO_APEX_VALUE);
        expect.that(mFieldsCaptor.getValue())
                .asList()
                .containsExactly(FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION);
    }

    @Test
    public void testGetMeasurementRollbackApexVersion_noData() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);
        expect.that(mManager.getMeasurementRollbackApexVersion()).isEqualTo(NO_APEX_VALUE);
    }

    @Test
    public void testGetMeasurementRollbackApexVersion_dataPresent() {
        long apex = 1000L;
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder().setMsmtRollbackApexVersion(apex).build();
        mockWorkerGetAdExtDataCall(params);
        expect.that(mManager.getMeasurementRollbackApexVersion()).isEqualTo(apex);
    }

    @Test
    public void testClearDataOnOtaAsync() {
        mockWorkerSetAdExtDataCall();
        mManager.clearDataOnOtaAsync();

        expect.that(mParamsCaptor.getValue().getIsMeasurementConsented())
                .isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(mParamsCaptor.getValue().getMeasurementRollbackApexVersion())
                .isEqualTo(NO_APEX_VALUE);
        expect.that(mParamsCaptor.getValue().getIsU18Account()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(mParamsCaptor.getValue().getIsAdultAccount()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(mParamsCaptor.getValue().getManualInteractionWithConsentStatus())
                .isEqualTo(STATE_UNKNOWN);

        expect.that(mFieldsCaptor.getValue())
                .asList()
                .containsExactly(
                        FIELD_IS_MEASUREMENT_CONSENTED,
                        FIELD_IS_U18_ACCOUNT,
                        FIELD_IS_ADULT_ACCOUNT,
                        FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS,
                        FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION);
    }

    private void verifyLogging(int apiName, int expectedResultCode) {
        ArgumentCaptor<ApiCallStats> argument = ArgumentCaptor.forClass(ApiCallStats.class);

        verify(mAdServicesLogger).logApiCallStats(argument.capture());

        ApiCallStats stats = argument.getValue();
        expect.that(stats.getCode()).isEqualTo(AD_SERVICES_API_CALLED);
        expect.that(stats.getApiClass())
                .isEqualTo(AD_SERVICES_API_CALLED__API_CLASS__ADEXT_DATA_SERVICE);
        expect.that(stats.getApiName()).isEqualTo(apiName);
        expect.that(stats.getResultCode()).isEqualTo(expectedResultCode);
        expect.that(stats.getAppPackageName()).isEqualTo(mContext.getPackageName());
        expect.that(stats.getSdkPackageName()).isEqualTo(UNKNOWN_PACKAGE_NAME);
    }

    private void mockWorkerGetAdExtDataCall(AdServicesExtDataParams params) {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(0))
                                    .onResult(params);
                            return null;
                        })
                .when(mMockWorker)
                .getAdServicesExtData(any());
    }

    private void mockWorkerSetAdExtDataCall() {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(2))
                                    .onResult(invocation.getArgument(0));
                            return null;
                        })
                .when(mMockWorker)
                .setAdServicesExtData(mParamsCaptor.capture(), mFieldsCaptor.capture(), any());
    }
}
