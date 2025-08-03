/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.imsserviceentitlement;

import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__FAILED;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__SUCCESSFUL;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__PURPOSE__ACTIVATION;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__SERVICE_TYPE__VOWIFI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.imsserviceentitlement.entitlement.EntitlementResult;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.AddrStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.EntitlementStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.ProvStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.TcStatus;
import com.android.imsserviceentitlement.utils.Executors;
import com.android.imsserviceentitlement.utils.ImsUtils;
import com.android.imsserviceentitlement.utils.MetricsLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.reflect.Field;

@RunWith(AndroidJUnit4.class)
public class WfcActivationControllerTest {
    @Rule public final MockitoRule rule = MockitoJUnit.rule();
    @Mock private TelephonyManager mMockTelephonyManager;
    @Mock private ImsEntitlementApi mMockActivationApi;
    @Mock private WfcActivationUi mMockActivationUi;
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private NetworkInfo mMockNetworkInfo;
    @Mock private ImsUtils mMockImsUtils;
    @Mock private MetricsLogger mMockMetricsLogger;

    private static final int SUB_ID = 1;
    private static final int CARRIER_ID = 1234;
    private static final String EMERGENCY_ADDRESS_WEB_URL = "webUrl";
    private static final String EMERGENCY_ADDRESS_WEB_DATA = "webData";

    private WfcActivationController mWfcActivationController;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = spy(ApplicationProvider.getApplicationContext());

        when(mContext.getSystemService(TelephonyManager.class)).thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(SUB_ID)).thenReturn(
                mMockTelephonyManager);
        setNetworkConnected(true);

        Field field = Executors.class.getDeclaredField("sUseDirectExecutorForTest");
        field.setAccessible(true);
        field.set(null, true);
    }

    @Test
    public void startFlow_launchAppForActivation_setPurposeActivation() {
        InOrder mOrderVerifier = inOrder(mMockActivationUi);
        setNetworkConnected(false);
        buildActivity(ActivityConstants.LAUNCH_APP_ACTIVATE);

        mWfcActivationController.startFlow();

        verifyGeneralWaitingUiInOrder(mOrderVerifier, R.string.activate_title);
        verifyErrorUiInOrder(
                mOrderVerifier,
                R.string.activate_title,
                R.string.wfc_activation_error);
    }

    @Test
    public void startFlow_launchAppForUpdate_setPurposeUpdate() {
        InOrder mOrderVerifier = inOrder(mMockActivationUi);
        setNetworkConnected(false);
        buildActivity(ActivityConstants.LAUNCH_APP_UPDATE);

        mWfcActivationController.startFlow();

        verifyGeneralWaitingUiInOrder(mOrderVerifier, R.string.e911_title);
        verifyErrorUiInOrder(mOrderVerifier, R.string.e911_title, R.string.address_update_error);
    }

    @Test
    public void startFlow_launchAppForShowTc_setPurposeUpdate() {
        InOrder mOrderVerifier = inOrder(mMockActivationUi);
        setNetworkConnected(false);
        buildActivity(ActivityConstants.LAUNCH_APP_SHOW_TC);

        mWfcActivationController.startFlow();

        verifyGeneralWaitingUiInOrder(mOrderVerifier, R.string.tos_title);
        verifyErrorUiInOrder(
                mOrderVerifier,
                R.string.tos_title,
                R.string.show_terms_and_condition_error);
    }

    @Test
    public void finishFlow_isFinishing_showGeneralWaitingUi() {
        InOrder mOrderVerifier = inOrder(mMockActivationUi);
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(null);
        buildActivity(ActivityConstants.LAUNCH_APP_ACTIVATE);

        mWfcActivationController.finishFlow();

        mOrderVerifier
                .verify(mMockActivationUi)
                .showActivationUi(
                        R.string.activate_title,
                        R.string.progress_text,
                        true,
                        0,
                        Activity.RESULT_CANCELED,
                        0);
        mOrderVerifier
                .verify(mMockActivationUi)
                .showActivationUi(
                        R.string.activate_title,
                        R.string.wfc_activation_error,
                        false,
                        R.string.ok,
                        WfcActivationUi.RESULT_FAILURE,
                        0);
    }

    @Test
    public void finishFlow_startForUpdate_showGeneralWaitingUi() {
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(
                EntitlementResult
                        .builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus
                                        .builder()
                                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                                        .setProvStatus(ProvStatus.PROVISIONED)
                                        .setTcStatus(TcStatus.AVAILABLE)
                                        .setAddrStatus(AddrStatus.AVAILABLE)
                                        .build())
                        .build());
        setNetworkConnected(false);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID);
        intent.putExtra(ActivityConstants.EXTRA_LAUNCH_CARRIER_APP,
                ActivityConstants.LAUNCH_APP_UPDATE);
        mWfcActivationController =
                new WfcActivationController(
                        mContext,
                        mMockActivationUi,
                        mMockActivationApi,
                        null,
                        mMockImsUtils,
                        mMockMetricsLogger);

        mWfcActivationController.finishFlow();

        verify(mMockActivationUi).setResultAndFinish(eq(Activity.RESULT_OK));
    }

    @Test
    public void finish_startFlowForActivate_writeLoggerPurposeActivation() {
        when(mMockTelephonyManager.getSimCarrierId()).thenReturn(CARRIER_ID);
        when(mMockTelephonyManager.getSimSpecificCarrierId()).thenReturn(CARRIER_ID);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID);
        intent.putExtra(ActivityConstants.EXTRA_LAUNCH_CARRIER_APP,
                ActivityConstants.LAUNCH_APP_ACTIVATE);
        mWfcActivationController =
                new WfcActivationController(
                        mContext,
                        mMockActivationUi,
                        mMockActivationApi,
                        intent,
                        mMockImsUtils,
                        mMockMetricsLogger);

        mWfcActivationController.startFlow();
        mWfcActivationController.finish();

        verify(mMockMetricsLogger).start(eq(IMS_SERVICE_ENTITLEMENT_UPDATED__PURPOSE__ACTIVATION));
        verify(mMockMetricsLogger).write(
                eq(IMS_SERVICE_ENTITLEMENT_UPDATED__SERVICE_TYPE__VOWIFI),
                eq(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__FAILED));
    }

    @Test
    public void finish_entitlementResultWfcEntitled_writeLoggerAppResultSuccessful() {
        when(mMockTelephonyManager.getSimCarrierId()).thenReturn(CARRIER_ID);
        when(mMockTelephonyManager.getSimSpecificCarrierId()).thenReturn(CARRIER_ID);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID);
        intent.putExtra(ActivityConstants.EXTRA_LAUNCH_CARRIER_APP,
                ActivityConstants.LAUNCH_APP_ACTIVATE);
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(
                EntitlementResult
                        .builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus
                                        .builder()
                                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                                        .setProvStatus(ProvStatus.PROVISIONED)
                                        .setTcStatus(TcStatus.AVAILABLE)
                                        .setAddrStatus(AddrStatus.AVAILABLE)
                                        .build())
                        .build());
        mWfcActivationController =
                new WfcActivationController(
                        mContext,
                        mMockActivationUi,
                        mMockActivationApi,
                        intent,
                        mMockImsUtils,
                        mMockMetricsLogger);

        mWfcActivationController.startFlow();
        mWfcActivationController.finish();

        verify(mMockMetricsLogger).start(eq(IMS_SERVICE_ENTITLEMENT_UPDATED__PURPOSE__ACTIVATION));
        verify(mMockMetricsLogger).write(
                eq(IMS_SERVICE_ENTITLEMENT_UPDATED__SERVICE_TYPE__VOWIFI),
                eq(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__SUCCESSFUL));
    }

    @Test
    public void handleEntitlementStatusForActivation_isVowifiEntitledTrue_setActivityResultOk() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                                        .setTcStatus(TcStatus.AVAILABLE)
                                        .setAddrStatus(AddrStatus.AVAILABLE)
                                        .setProvStatus(ProvStatus.PROVISIONED)
                                        .build())
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        buildActivity(ActivityConstants.LAUNCH_APP_ACTIVATE);

        mWfcActivationController.evaluateEntitlementStatus();

        verify(mMockActivationUi).setResultAndFinish(Activity.RESULT_OK);
    }

    @Test
    public void handleEntitlementStatusForActivation_isServerDataMissingTrue_showWebview() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                                        .setTcStatus(TcStatus.NOT_AVAILABLE)
                                        .setAddrStatus(AddrStatus.NOT_AVAILABLE)
                                        .build())
                        .setEmergencyAddressWebUrl(EMERGENCY_ADDRESS_WEB_URL)
                        .setEmergencyAddressWebData(EMERGENCY_ADDRESS_WEB_DATA)
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        buildActivity(ActivityConstants.LAUNCH_APP_ACTIVATE);

        mWfcActivationController.evaluateEntitlementStatus();

        verify(mMockActivationUi).showWebview(EMERGENCY_ADDRESS_WEB_URL,
                EMERGENCY_ADDRESS_WEB_DATA);
    }

    @Test
    public void handleEntitlementStatusForActivation_showTc_showWebview() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                                        .setTcStatus(TcStatus.NOT_AVAILABLE)
                                        .setAddrStatus(AddrStatus.NOT_AVAILABLE)
                                        .build())
                        .setTermsAndConditionsWebUrl(EMERGENCY_ADDRESS_WEB_URL)
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        buildActivity(ActivityConstants.LAUNCH_APP_ACTIVATE);

        mWfcActivationController.evaluateEntitlementStatus();

        verify(mMockActivationUi).showWebview(EMERGENCY_ADDRESS_WEB_URL, null);
    }

    @Test
    public void handleEntitlementStatusForActivation_isIncompatibleTrue_showErrorUi() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.INCOMPATIBLE)
                                        .build())
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        buildActivity(ActivityConstants.LAUNCH_APP_ACTIVATE);

        mWfcActivationController.evaluateEntitlementStatus();

        verifyErrorUi(R.string.activate_title, R.string.failure_contact_carrier);
    }

    @Test
    public void handleEntitlementStatusForActivation_unexpectedStatus_showGeneralErrorUi() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                                        .setTcStatus(TcStatus.IN_PROGRESS)
                                        .setAddrStatus(AddrStatus.IN_PROGRESS)
                                        .build())
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        buildActivity(ActivityConstants.LAUNCH_APP_ACTIVATE);

        mWfcActivationController.evaluateEntitlementStatus();

        verifyErrorUi(R.string.activate_title, R.string.wfc_activation_error);
    }

    @Test
    public void handleEntitlementStatusAfterActivation_isVowifiEntitledTrue_setActivityResultOk() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                                        .setTcStatus(TcStatus.AVAILABLE)
                                        .setAddrStatus(AddrStatus.AVAILABLE)
                                        .setProvStatus(ProvStatus.PROVISIONED)
                                        .build())
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        buildActivity(ActivityConstants.LAUNCH_APP_ACTIVATE);

        mWfcActivationController.reevaluateEntitlementStatus();

        verify(mMockActivationUi).setResultAndFinish(Activity.RESULT_OK);
    }

    @Test
    public void handleEntitlementStatusAfterActivation_unexpectedStatus_showGeneralErrorUi() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                                        .setTcStatus(TcStatus.IN_PROGRESS)
                                        .setAddrStatus(AddrStatus.IN_PROGRESS)
                                        .build())
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        buildActivity(ActivityConstants.LAUNCH_APP_ACTIVATE);

        mWfcActivationController.reevaluateEntitlementStatus();

        verifyErrorUi(R.string.activate_title, R.string.wfc_activation_error);
    }

    @Test
    public void handleEntitlementStatusAfterUpdating_entitlementStatusEnabled_setResultOk() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                                        .setTcStatus(TcStatus.AVAILABLE)
                                        .setAddrStatus(AddrStatus.AVAILABLE)
                                        .setProvStatus(ProvStatus.PROVISIONED)
                                        .build())
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        buildActivity(ActivityConstants.LAUNCH_APP_UPDATE);

        mWfcActivationController.reevaluateEntitlementStatus();

        verify(mMockActivationUi).setResultAndFinish(eq(Activity.RESULT_OK));
    }

    @Test
    public void handleEntitlementStatusAfterUpdating_entitlementStatusNoServerData_turnOffWfc() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                                        .setTcStatus(TcStatus.NOT_AVAILABLE)
                                        .setAddrStatus(AddrStatus.NOT_AVAILABLE)
                                        .build())
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID);
        intent.putExtra(ActivityConstants.EXTRA_LAUNCH_CARRIER_APP,
                ActivityConstants.LAUNCH_APP_UPDATE);
        mWfcActivationController =
                new WfcActivationController(
                        mContext,
                        mMockActivationUi,
                        mMockActivationApi,
                        intent,
                        mMockImsUtils,
                        mMockMetricsLogger);

        mWfcActivationController.reevaluateEntitlementStatus();

        verify(mMockImsUtils).turnOffWfc(any());
    }

    @Test
    public void handleEntitlementStatusAfterUpdating_unexpectedStatus_showGeneralErrorUi() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                                        .setTcStatus(TcStatus.IN_PROGRESS)
                                        .setAddrStatus(AddrStatus.IN_PROGRESS)
                                        .build())
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        buildActivity(ActivityConstants.LAUNCH_APP_UPDATE);

        mWfcActivationController.reevaluateEntitlementStatus();

        verifyErrorUi(R.string.e911_title, R.string.address_update_error);
    }

    @Test
    public void handleEntitlementStatusForUpdate_serviceEntitled_showWebview() {
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(
                EntitlementResult
                        .builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus
                                        .builder()
                                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                                        .setProvStatus(ProvStatus.PROVISIONED)
                                        .setTcStatus(TcStatus.AVAILABLE)
                                        .setAddrStatus(AddrStatus.AVAILABLE)
                                        .build())
                        .setEmergencyAddressWebUrl(EMERGENCY_ADDRESS_WEB_URL)
                        .setEmergencyAddressWebData(EMERGENCY_ADDRESS_WEB_DATA)
                        .build());
        buildActivity(ActivityConstants.LAUNCH_APP_UPDATE);

        mWfcActivationController.evaluateEntitlementStatus();

        verify(mMockActivationUi).showWebview(EMERGENCY_ADDRESS_WEB_URL,
                EMERGENCY_ADDRESS_WEB_DATA);
    }

    @Test
    public void handleEntitlementStatusForUpdate_showTc_showWebview() {
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(
                EntitlementResult
                        .builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus
                                        .builder()
                                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                                        .setProvStatus(ProvStatus.PROVISIONED)
                                        .setTcStatus(TcStatus.AVAILABLE)
                                        .setAddrStatus(AddrStatus.AVAILABLE)
                                        .build())
                        .setTermsAndConditionsWebUrl(EMERGENCY_ADDRESS_WEB_URL)
                        .build());
        buildActivity(ActivityConstants.LAUNCH_APP_SHOW_TC);

        mWfcActivationController.evaluateEntitlementStatus();

        verify(mMockActivationUi).showWebview(EMERGENCY_ADDRESS_WEB_URL, null);
    }

    @Test
    public void handleEntitlementStatusForUpdate_entitlementStatusIncompatible_showErrorUi() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.INCOMPATIBLE)
                                        .build())
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        buildActivity(ActivityConstants.LAUNCH_APP_UPDATE);

        mWfcActivationController.evaluateEntitlementStatus();

        verifyErrorUi(R.string.e911_title, R.string.failure_contact_carrier);
    }

    @Test
    public void handleEntitlementStatusForUpdate_entitlementStatusDisabled_showGenericErrorUi() {
        EntitlementResult entitlementResult =
                EntitlementResult.builder()
                        .setVowifiStatus(
                                Ts43VowifiStatus.builder()
                                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                                        .build())
                        .build();
        when(mMockActivationApi.checkEntitlementStatus()).thenReturn(entitlementResult);
        buildActivity(ActivityConstants.LAUNCH_APP_UPDATE);

        mWfcActivationController.evaluateEntitlementStatus();

        verifyErrorUi(R.string.e911_title, R.string.address_update_error);
    }

    private void buildActivity(int extraLaunchCarrierApp) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID);
        intent.putExtra(ActivityConstants.EXTRA_LAUNCH_CARRIER_APP, extraLaunchCarrierApp);
        mWfcActivationController =
                new WfcActivationController(mContext, mMockActivationUi, mMockActivationApi,
                        intent);
    }

    private void setNetworkConnected(boolean isConnected) {
        when(mMockNetworkInfo.isConnected()).thenReturn(isConnected);
        when(mContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(
                mMockConnectivityManager);
        when(mMockConnectivityManager.getActiveNetworkInfo()).thenReturn(mMockNetworkInfo);
        when(mMockNetworkInfo.isConnected()).thenReturn(isConnected);
    }

    private void verifyErrorUi(int title, int errorMesssage) {
        verify(mMockActivationUi)
                .showActivationUi(
                        title,
                        errorMesssage,
                        false, R.string.ok,
                        WfcActivationUi.RESULT_FAILURE,
                        0);
    }

    private void verifyErrorUiInOrder(InOrder inOrder, int title, int errorMesssage) {
        inOrder.verify(mMockActivationUi)
                .showActivationUi(
                        title,
                        errorMesssage,
                        false, R.string.ok,
                        WfcActivationUi.RESULT_FAILURE,
                        0);
    }

    private void verifyGeneralWaitingUiInOrder(InOrder inOrder, int title) {
        inOrder.verify(mMockActivationUi)
                .showActivationUi(title, R.string.progress_text, true, 0, 0, 0);
    }
}
