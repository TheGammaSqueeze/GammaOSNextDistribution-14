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

import static com.android.imsserviceentitlement.entitlement.EntitlementConfiguration.ClientBehavior.NEEDS_TO_RESET;
import static com.android.imsserviceentitlement.entitlement.EntitlementConfiguration.ClientBehavior.VALID_DURING_VALIDITY;
import static com.android.libraries.entitlement.ServiceEntitlementException.ERROR_HTTP_STATUS_NOT_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.imsserviceentitlement.entitlement.EntitlementConfiguration;
import com.android.imsserviceentitlement.entitlement.EntitlementResult;
import com.android.imsserviceentitlement.fcm.FcmTokenStore;
import com.android.imsserviceentitlement.utils.TelephonyUtils;
import com.android.libraries.entitlement.ServiceEntitlement;
import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.libraries.entitlement.ServiceEntitlementRequest;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class ImsEntitlementApiTest {
    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    @Spy private Context mContext = ApplicationProvider.getApplicationContext();

    @Mock private ServiceEntitlement mMockServiceEntitlement;
    @Mock private EntitlementConfiguration mMockEntitlementConfiguration;
    @Mock private CarrierConfigManager mCarrierConfigManager;

    private static final int SUB_ID = 1;
    private static final String FCM_TOKEN = "FCM_TOKEN";
    private static final String RAW_XML =
            "<wap-provisioningdoc version=\"1.1\">"
                    + "  <characteristic type=\"VERS\">"
                    + "    <parm name=\"version\" value=\"1\"/>"
                    + "    <parm name=\"validity\" value=\"1728000\"/>"
                    + "  </characteristic>"
                    + "  <characteristic type=\"TOKEN\">"
                    + "    <parm name=\"token\" value=\"kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX\"/>"
                    + "    <parm name=\"validity\" value=\"3600\"/>"
                    + "  </characteristic>"
                    + "  <characteristic type=\"APPLICATION\">"
                    + "    <parm name=\"AppID\" value=\"ap2004\"/>"
                    + "    <parm name=\"EntitlementStatus\" value=\"1\"/>"
                    + "  </characteristic>"
                    + "</wap-provisioningdoc>";
    private static final String RAW_XML_NEW_TOKEN =
            "<wap-provisioningdoc version=\"1.1\">"
                    + "  <characteristic type=\"VERS\">"
                    + "    <parm name=\"version\" value=\"1\"/>"
                    + "    <parm name=\"validity\" value=\"1728000\"/>"
                    + "  </characteristic>"
                    + "  <characteristic type=\"TOKEN\">"
                    + "    <parm name=\"token\" value=\"NEW_TOKEN\"/>"
                    + "    <parm name=\"validity\" value=\"3600\"/>"
                    + "  </characteristic>\n"
                    + "  <characteristic type=\"APPLICATION\">"
                    + "    <parm name=\"AppID\" value=\"ap2004\"/>"
                    + "    <parm name=\"EntitlementStatus\" value=\"1\"/>"
                    + "  </characteristic>"
                    + "</wap-provisioningdoc>";

    private static final String MULTIPLE_APPIDS_RAW_XML =
            "<wap-provisioningdoc version=\"1.1\">"
                    + "  <characteristic type=\"VERS\">"
                    + "    <parm name=\"version\" value=\"1\"/>"
                    + "    <parm name=\"validity\" value=\"1728000\"/>"
                    + "  </characteristic>"
                    + "  <characteristic type=\"TOKEN\">"
                    + "    <parm name=\"token\" value=\"kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX\"/>"
                    + "    <parm name=\"validity\" value=\"3600\"/>"
                    + "  </characteristic>"
                    + "  <characteristic type=\"APPLICATION\">"
                    + "    <parm name=\"AppID\" value=\"ap2003\"/>"
                    + "    <parm name=\"EntitlementStatus\" value=\"1\"/>"
                    + "  </characteristic>\n"
                    + "  <characteristic type=\"APPLICATION\">"
                    + "    <parm name=\"AppID\" value=\"ap2004\"/>\n"
                    + "    <parm name=\"EntitlementStatus\" value=\"1\"/>"
                    + "  </characteristic>"
                    + "  <characteristic type=\"APPLICATION\">"
                    + "    <parm name=\"AppID\" value=\"ap2005\"/>"
                    + "    <parm name=\"EntitlementStatus\" value=\"1\"/>"
                    + "  </characteristic>"
                    + "</wap-provisioningdoc>";

    private final EntitlementConfiguration mEntitlementConfiguration =
            new EntitlementConfiguration(ApplicationProvider.getApplicationContext(), SUB_ID);

    private ImsEntitlementApi mImsEntitlementApi;

    @Before
    public void setUp() {
        setImsProvisioningBool(true);
        FcmTokenStore.setToken(mContext, SUB_ID, FCM_TOKEN);
        mEntitlementConfiguration.reset();
    }

    @Test
    public void checkEntitlementStatus_verifyVowifiStatus() throws Exception {
        setImsProvisioningBool(false);
        setupImsEntitlementApi(mEntitlementConfiguration);
        when(mMockServiceEntitlement.queryEntitlementStatus(
                eq(ImmutableList.of(ServiceEntitlement.APP_VOWIFI)), any())).thenReturn(RAW_XML);

        EntitlementResult result = mImsEntitlementApi.checkEntitlementStatus();

        assertThat(result.getVowifiStatus().vowifiEntitled()).isTrue();
    }

    @Test
    public void checkEntitlementStatus_verifyImsAppsStatus() throws Exception {
        setupImsEntitlementApi(mEntitlementConfiguration);
        when(mMockServiceEntitlement.queryEntitlementStatus(
                eq(ImmutableList.of(
                        ServiceEntitlement.APP_VOWIFI,
                        ServiceEntitlement.APP_VOLTE,
                        ServiceEntitlement.APP_SMSOIP)), any())
        ).thenReturn(MULTIPLE_APPIDS_RAW_XML);

        EntitlementResult result = mImsEntitlementApi.checkEntitlementStatus();

        assertThat(result.getVowifiStatus().vowifiEntitled()).isTrue();
        assertThat(result.getVolteStatus().isActive()).isTrue();
        assertThat(result.getSmsoveripStatus().isActive()).isTrue();
    }

    @Test
    public void checkEntitlementStatus_verifyConfigs() throws Exception {
        setImsProvisioningBool(false);
        setupImsEntitlementApi(mEntitlementConfiguration);
        when(mMockServiceEntitlement.queryEntitlementStatus(
                eq(ImmutableList.of(ServiceEntitlement.APP_VOWIFI)),
                any())).thenReturn(RAW_XML);

        EntitlementResult result = mImsEntitlementApi.checkEntitlementStatus();

        assertThat(mEntitlementConfiguration.getVoWifiStatus()).isEqualTo(1);
        assertThat(mEntitlementConfiguration.getVolteStatus()).isEqualTo(2);
        assertThat(mEntitlementConfiguration.getSmsOverIpStatus()).isEqualTo(2);
        assertThat(mEntitlementConfiguration.getToken().get()).isEqualTo(
                "kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX");
        assertThat(mEntitlementConfiguration.getTokenValidity()).isEqualTo(3600);
        assertThat(mEntitlementConfiguration.entitlementValidation()).isEqualTo(
                VALID_DURING_VALIDITY);
    }

    @Test
    public void checkEntitlementStatus_resultNull_verifyVowifiStatusAndConfigs() throws Exception {
        setImsProvisioningBool(false);
        setupImsEntitlementApi(mEntitlementConfiguration);
        when(mMockServiceEntitlement.queryEntitlementStatus(
                eq(ImmutableList.of(ServiceEntitlement.APP_VOWIFI)), any())).thenReturn(null);

        EntitlementResult result = mImsEntitlementApi.checkEntitlementStatus();

        assertThat(result.getVowifiStatus().vowifiEntitled()).isFalse();
        assertThat(mEntitlementConfiguration.getVoWifiStatus()).isEqualTo(2);
        assertThat(mEntitlementConfiguration.getVolteStatus()).isEqualTo(2);
        assertThat(mEntitlementConfiguration.getSmsOverIpStatus()).isEqualTo(2);
        assertThat(mEntitlementConfiguration.getToken().isPresent()).isFalse();
        assertThat(mEntitlementConfiguration.getTokenValidity()).isEqualTo(0);
        assertThat(mEntitlementConfiguration.entitlementValidation()).isEqualTo(NEEDS_TO_RESET);
    }

    @Test
    public void checkEntitlementStatus_httpResponse511_dataStoreReset() throws Exception {
        setImsProvisioningBool(false);
        setupImsEntitlementApi(mMockEntitlementConfiguration);
        when(mMockServiceEntitlement.queryEntitlementStatus(
                eq(ImmutableList.of(ServiceEntitlement.APP_VOWIFI)), any()))
                .thenThrow(
                        new ServiceEntitlementException(
                                ERROR_HTTP_STATUS_NOT_SUCCESS, 511, "Invalid connection response"));

        EntitlementResult result = mImsEntitlementApi.checkEntitlementStatus();

        verify(mMockEntitlementConfiguration).reset();
        assertThat(result).isNull();
    }

    @Test
    public void checkEntitlementStatus_httpResponse511_fullAuthnDone() throws Exception {
        setImsProvisioningBool(false);
        setupImsEntitlementApi(mEntitlementConfiguration);
        mEntitlementConfiguration.update(RAW_XML);
        // While perform fast-authn, throws exception with code 511
        when(mMockServiceEntitlement.queryEntitlementStatus(
                ImmutableList.of(ServiceEntitlement.APP_VOWIFI),
                authenticationRequest("kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX")))
                .thenThrow(
                        new ServiceEntitlementException(
                                ERROR_HTTP_STATUS_NOT_SUCCESS, 511, "Invalid connection response"));
        // While perform full-authn, return the result
        when(mMockServiceEntitlement.queryEntitlementStatus(
                ImmutableList.of(ServiceEntitlement.APP_VOWIFI),
                authenticationRequest(null)))
                .thenReturn(RAW_XML_NEW_TOKEN);

        EntitlementResult result = mImsEntitlementApi.checkEntitlementStatus();

        assertThat(result).isNotNull();
        assertThat(mEntitlementConfiguration.getToken().get()).isEqualTo("NEW_TOKEN");
    }

    @Test
    public void checkEntitlementStatus_httpResponse503WithDateTime_returnsRetryAfter()
            throws Exception {
        setImsProvisioningBool(false);
        setupImsEntitlementApi(mEntitlementConfiguration);
        mEntitlementConfiguration.update(RAW_XML);
        Clock fixedClock = Clock.fixed(Instant.ofEpochSecond(0), ZoneOffset.UTC);
        ImsEntitlementApi.sClock = fixedClock;

        // While perform fast-authn, throws exception with code 503
        when(mMockServiceEntitlement.queryEntitlementStatus(
                ImmutableList.of(ServiceEntitlement.APP_VOWIFI),
                authenticationRequest("kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX")))
                .thenThrow(
                        new ServiceEntitlementException(
                                ERROR_HTTP_STATUS_NOT_SUCCESS,
                                503,
                                getDateTimeAfter(120, fixedClock),
                                "Invalid connection response"));

        EntitlementResult result = mImsEntitlementApi.checkEntitlementStatus();

        assertThat(result).isNotNull();
        assertThat(result.getRetryAfterSeconds()).isEqualTo(120);
    }

    @Test
    public void checkEntitlementStatus_httpResponse503WithNumericValue_returnsRetryAfter()
            throws Exception {
        setImsProvisioningBool(false);
        setupImsEntitlementApi(mEntitlementConfiguration);
        mEntitlementConfiguration.update(RAW_XML);
        // While perform fast-authn, throws exception with code 503
        when(mMockServiceEntitlement.queryEntitlementStatus(
                ImmutableList.of(ServiceEntitlement.APP_VOWIFI),
                authenticationRequest("kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX")))
                .thenThrow(
                        new ServiceEntitlementException(
                                ERROR_HTTP_STATUS_NOT_SUCCESS,
                                503,
                                "120",
                                "Invalid connection response"));

        EntitlementResult result = mImsEntitlementApi.checkEntitlementStatus();

        assertThat(result).isNotNull();
        assertThat(result.getRetryAfterSeconds()).isEqualTo(120);
    }

    @Test
    public void checkEntitlementStatus_invalidSubId_resultNull() {
        ImsEntitlementApi imsEntitlementApi =
                new ImsEntitlementApi(mContext, SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        EntitlementResult result = imsEntitlementApi.checkEntitlementStatus();

        assertThat(result).isNull();
    }

    private ServiceEntitlementRequest authenticationRequest(String token) {
        ServiceEntitlementRequest.Builder requestBuilder = ServiceEntitlementRequest.builder();
        if (token != null) {
            requestBuilder.setAuthenticationToken(token);
        }
        requestBuilder.setNotificationToken(FcmTokenStore.getToken(mContext, SUB_ID));
        requestBuilder.setAcceptContentType(ServiceEntitlementRequest.ACCEPT_CONTENT_TYPE_XML);
        return requestBuilder.build();
    }

    private void setupImsEntitlementApi(EntitlementConfiguration entitlementConfiguration) {
        mImsEntitlementApi = new ImsEntitlementApi(
                mContext,
                SUB_ID,
                TelephonyUtils.isImsProvisioningRequired(mContext, SUB_ID),
                mMockServiceEntitlement,
                entitlementConfiguration);
    }

    private void setImsProvisioningBool(boolean provisioning) {
        PersistableBundle carrierConfig = new PersistableBundle();
        carrierConfig.putBoolean(
                CarrierConfigManager.ImsServiceEntitlement.KEY_IMS_PROVISIONING_BOOL,
                provisioning
        );
        when(mCarrierConfigManager.getConfigForSubId(SUB_ID)).thenReturn(carrierConfig);
        when(mContext.getSystemService(CarrierConfigManager.class))
                .thenReturn(mCarrierConfigManager);
    }

    private String getDateTimeAfter(long seconds, Clock fixedClock) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(Date.from(fixedClock.instant().plusSeconds(seconds)));
    }
}
