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

package com.android.libraries.entitlement;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

import android.content.Context;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.libraries.entitlement.eapaka.EapAkaApi;
import com.android.libraries.entitlement.http.HttpResponse;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class ServiceEntitlementTest {
    private static final String QUERY_APP_VOLTE_RESULT = "QUERY_APP_VOLTE_RESULT";
    private static final String QUERY_APP_VOWIFI_RESULT = "QUERY_APP_VOWIFI_RESULT";
    private static final String QUERY_APP_ODSA_COMPANION_RESULT = "QUERY_APP_ODSA_COMPANION_RESULT";
    private static final String QUERY_APP_ODSA_PRIMARY_RESULT = "QUERY_APP_ODSA_PRIMARY_RESULT";
    private static final String QUERY_OIDC_RESULT = "QUERY_OIDC_RESULT";
    private static final String QUERY_ENTITLEMENT_STATUS_FROM_OIDC =
            "QUERY_ENTITLEMENT_STATUS_FROM_OIDC";
    private static final String TEST_URL = "https://test.url";

    private static final String IMSI = "234107813240779";
    private static final String MCCMNC = "23410";
    private static final int SUB_ID = 1;

    @Rule public final MockitoRule rule = MockitoJUnit.rule();
    @Mock EapAkaApi mMockEapAkaApi;
    @Mock HttpResponse mMockHttpResponse;
    @Mock private TelephonyManager mMockTelephonyManager;
    @Mock private TelephonyManager mMockTelephonyManagerForSubId;

    private Context mContext;
    private ServiceEntitlement mServiceEntitlement;
    private CarrierConfig mCarrierConfig;

    @Before
    public void setUp() {
        mCarrierConfig = CarrierConfig.builder().setServerUrl(TEST_URL).build();
        mServiceEntitlement = new ServiceEntitlement(mCarrierConfig, mMockEapAkaApi);
        mContext = spy(ApplicationProvider.getApplicationContext());
        when(mContext.getSystemService(TelephonyManager.class)).thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(SUB_ID))
                .thenReturn(mMockTelephonyManagerForSubId);
        when(mMockTelephonyManagerForSubId.getSubscriberId()).thenReturn(IMSI);
        when(mMockTelephonyManagerForSubId.getSimOperator()).thenReturn(MCCMNC);
    }

    @Test
    public void queryEntitlementStatus_noServerAddress_throwException() throws Exception {
        CarrierConfig config = CarrierConfig.builder().build();
        ServiceEntitlementRequest request = ServiceEntitlementRequest.builder().build();
        ServiceEntitlement serviceEntitlement = new ServiceEntitlement(mContext, config, SUB_ID);

        ServiceEntitlementException exception =
                expectThrows(
                        ServiceEntitlementException.class,
                        () ->
                                serviceEntitlement.queryEntitlementStatus(
                                        ImmutableList.of(ServiceEntitlement.APP_VOWIFI), request));

        assertThat(exception.getErrorCode())
                .isEqualTo(ServiceEntitlementException.ERROR_SERVER_NOT_CONNECTABLE);
        assertThat(exception.getMessage()).isEqualTo("Configure connection failed!");
        assertThat(exception.getHttpStatus()).isEqualTo(0);
        assertThat(exception.getRetryAfter()).isEmpty();
    }

    @Test
    public void queryEntitlementStatus_appVolte_returnResult() throws Exception {
        ServiceEntitlementRequest request = ServiceEntitlementRequest.builder().build();
        when(mMockEapAkaApi.queryEntitlementStatus(
                        ImmutableList.of(ServiceEntitlement.APP_VOLTE), mCarrierConfig, request))
                .thenAnswer(
                        invocation -> {
                            when(mMockHttpResponse.body()).thenReturn(QUERY_APP_VOLTE_RESULT);
                            return mMockHttpResponse;
                        });

        assertThat(
                        mServiceEntitlement.queryEntitlementStatus(
                                ServiceEntitlement.APP_VOLTE, request))
                .isEqualTo(QUERY_APP_VOLTE_RESULT);
    }

    @Test
    public void queryEntitlementStatus_appVowifi_returnResult() throws Exception {
        ServiceEntitlementRequest request = ServiceEntitlementRequest.builder().build();
        when(mMockEapAkaApi.queryEntitlementStatus(
                        ImmutableList.of(ServiceEntitlement.APP_VOWIFI), mCarrierConfig, request))
                .thenAnswer(
                        invocation -> {
                            when(mMockHttpResponse.body()).thenReturn(QUERY_APP_VOWIFI_RESULT);
                            return mMockHttpResponse;
                        });

        assertThat(
                        mServiceEntitlement.queryEntitlementStatus(
                                ImmutableList.of(ServiceEntitlement.APP_VOWIFI), request))
                .isEqualTo(QUERY_APP_VOWIFI_RESULT);
    }

    @Test
    public void performEsimOdsa_appOdsaCompanion_returnResult() throws Exception {
        ServiceEntitlementRequest request = ServiceEntitlementRequest.builder().build();
        EsimOdsaOperation odsaOperation = EsimOdsaOperation.builder().build();
        when(mMockEapAkaApi.performEsimOdsaOperation(
                        ServiceEntitlement.APP_ODSA_COMPANION,
                        mCarrierConfig,
                        request,
                        odsaOperation))
                .thenAnswer(
                        invocation -> {
                            when(mMockHttpResponse.body())
                                    .thenReturn(QUERY_APP_ODSA_COMPANION_RESULT);
                            return mMockHttpResponse;
                        });

        assertThat(
                        mServiceEntitlement.performEsimOdsa(
                                ServiceEntitlement.APP_ODSA_COMPANION, request, odsaOperation))
                .isEqualTo(QUERY_APP_ODSA_COMPANION_RESULT);
    }

    @Test
    public void performEsimOdsa_appOdsaPrimary_returnResult() throws Exception {
        ServiceEntitlementRequest request = ServiceEntitlementRequest.builder().build();
        EsimOdsaOperation odsaOperation = EsimOdsaOperation.builder().build();
        when(mMockEapAkaApi.performEsimOdsaOperation(
                        ServiceEntitlement.APP_ODSA_PRIMARY,
                        mCarrierConfig,
                        request,
                        odsaOperation))
                .thenAnswer(
                        invocation -> {
                            when(mMockHttpResponse.body())
                                    .thenReturn(QUERY_APP_ODSA_PRIMARY_RESULT);
                            return mMockHttpResponse;
                        });

        assertThat(
                        mServiceEntitlement.performEsimOdsa(
                                ServiceEntitlement.APP_ODSA_PRIMARY, request, odsaOperation))
                .isEqualTo(QUERY_APP_ODSA_PRIMARY_RESULT);
    }

    @Test
    public void acquireOidcAuthenticationEndpoint_returnResult() throws Exception {
        ServiceEntitlementRequest request = ServiceEntitlementRequest.builder().build();
        when(mMockEapAkaApi.acquireOidcAuthenticationEndpoint(
                        ServiceEntitlement.APP_ODSA_COMPANION, mCarrierConfig, request))
                .thenReturn(QUERY_OIDC_RESULT);

        assertThat(
                        mServiceEntitlement.acquireOidcAuthenticationEndpoint(
                                ServiceEntitlement.APP_ODSA_COMPANION, request))
                .isEqualTo(QUERY_OIDC_RESULT);
    }

    @Test
    public void queryEntitlementStatusFromOidc_returnResult() throws Exception {
        when(mMockEapAkaApi.queryEntitlementStatusFromOidc(
                        ServiceEntitlement.APP_ODSA_PRIMARY, mCarrierConfig, null))
                .thenAnswer(
                        invocation -> {
                            when(mMockHttpResponse.body())
                                    .thenReturn(QUERY_ENTITLEMENT_STATUS_FROM_OIDC);
                            return mMockHttpResponse;
                        });

        assertThat(
                        mServiceEntitlement.queryEntitlementStatusFromOidc(
                                ServiceEntitlement.APP_ODSA_PRIMARY))
                .isEqualTo(QUERY_ENTITLEMENT_STATUS_FROM_OIDC);
    }
}
