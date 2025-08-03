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

package com.android.libraries.entitlement;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;

import com.android.libraries.entitlement.Ts43Authentication.Ts43AuthToken;
import com.android.libraries.entitlement.eapaka.EapAkaApi;
import com.android.libraries.entitlement.http.HttpResponse;
import com.android.libraries.entitlement.utils.Ts43Constants;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.net.URL;

@RunWith(AndroidTestingRunner.class)
public class Ts43AuthenticationTest {
    private static final String TEST_URL = "https://test.url";
    private static final String ENTITLEMENT_VERSION = "9.0";
    private static final String APP_NAME = "com.fake.app";
    private static final String APP_VERSION = "1.0";
    private static final String TOKEN = "ASH127AHHA88SF";
    private static final long VALIDITY = 86400;
    private static final String IMEI = "861536030196001";
    private static final ImmutableList<String> COOKIES =
            ImmutableList.of("key1=value1", "key2=value2");

    private static final String HTTP_RESPONSE_WITH_TOKEN =
            "<?xml version=\"1.0\"?>"
            + "<wap-provisioningdoc version=\"1.1\">"
            + "    <characteristic type=\"VERS\">"
            + "        <parm name=\"version\" value=\"1\"/>"
            + "        <parm name=\"validity\" value=\" + " + VALIDITY + "\"/>"
            + "    </characteristic>"
            + "    <characteristic type=\"TOKEN\">"
            + "        <parm name=\"token\" value=\"" + TOKEN + "\"/>"
            + "        <parm name=\"validity\" value=\"" + VALIDITY + "\"/>"
            + "    </characteristic>"
            + "</wap-provisioningdoc>";
    private static final String HTTP_RESPONSE_WITHOUT_TOKEN =
            "<?xml version=\"1.0\"?>"
                    + "<wap-provisioningdoc version=\"1.1\">"
                    + "    <characteristic type=\"VERS\">"
                    + "        <parm name=\"version\" value=\"1\"/>"
                    + "        <parm name=\"validity\" value=\" + " + VALIDITY + "\"/>"
                    + "    </characteristic>"
                    + "</wap-provisioningdoc>";

    private static final String HTTP_RESPONSE_WITHOUT_VALIDITY =
            "<?xml version=\"1.0\"?>"
                    + "<wap-provisioningdoc version=\"1.1\">"
                    + "    <characteristic type=\"VERS\">"
                    + "        <parm name=\"version\" value=\"1\"/>"
                    + "        <parm name=\"validity\" value=\" + " + VALIDITY + "\"/>"
                    + "    </characteristic>"
                    + "    <characteristic type=\"TOKEN\">"
                    + "        <parm name=\"token\" value=\"" + TOKEN + "\"/>"
                    + "    </characteristic>"
                    + "</wap-provisioningdoc>";

    private Ts43Authentication mTs43Authentication;

    @Mock
    private EapAkaApi mMockEapAkaApi;

    @Mock
    private HttpResponse mMockHttpResponse;

    @Mock
    private Context mContext;

    @Mock
    private TelephonyManager mTelephonyManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        CarrierConfig carrierConfig = CarrierConfig.builder().setServerUrl(TEST_URL).build();
        ServiceEntitlement serviceEntitlement = new ServiceEntitlement(carrierConfig,
                mMockEapAkaApi);
        mTs43Authentication = new Ts43Authentication(mContext, new URL(TEST_URL),
                ENTITLEMENT_VERSION);

        Field field = Ts43Authentication.class.getDeclaredField("mServiceEntitlement");
        field.setAccessible(true);
        field.set(mTs43Authentication, serviceEntitlement);

        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        doReturn(IMEI).when(mTelephonyManager).getImei(0);
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());
        doReturn(Context.TELEPHONY_SERVICE).when(mContext)
                .getSystemServiceName(TelephonyManager.class);
        doReturn(mTelephonyManager).when(mContext).getSystemService(Context.TELEPHONY_SERVICE);
        doReturn(mMockHttpResponse).when(mMockEapAkaApi)
                .queryEntitlementStatus(any(), any(), any());
        doReturn(COOKIES).when(mMockHttpResponse).cookies();
    }

    @Test
    public void testGetAuthToken_receivedValidToken() throws Exception {
        doReturn(HTTP_RESPONSE_WITH_TOKEN).when(mMockHttpResponse).body();
        Ts43AuthToken mToken = mTs43Authentication.getAuthToken(
                0, Ts43Constants.APP_ODSA_PRIMARY, APP_NAME, APP_VERSION);
        assertThat(mToken.token()).isEqualTo(TOKEN);
        assertThat(mToken.validity()).isEqualTo(VALIDITY);
    }

    @Test
    public void testGetAuthToken_invalidParams_throwException() {
        assertThrows(NullPointerException.class, () -> new Ts43Authentication(
                null, new URL(TEST_URL), ENTITLEMENT_VERSION));

        assertThrows(NullPointerException.class, () -> new Ts43Authentication(
                mContext, null, ENTITLEMENT_VERSION));
    }

    @Test
    public void testGetAuthToken_invalidAppId_throwException() {
        assertThrows(NullPointerException.class, () -> mTs43Authentication.getAuthToken(
                0, null, APP_NAME, APP_VERSION));
        assertThrows(IllegalArgumentException.class, () -> mTs43Authentication.getAuthToken(
                0, "invalid_app_id", APP_NAME, APP_VERSION));
    }

    @Test
    public void testGetAuthToken_invalidSlotIndex_throwException() {
        assertThrows(IllegalArgumentException.class, () -> mTs43Authentication.getAuthToken(
                5, Ts43Constants.APP_ODSA_PRIMARY, APP_NAME, APP_VERSION));
    }

    @Test
    public void testGetAuthToken_tokenNotAvailable_throwException() {
        doReturn(HTTP_RESPONSE_WITHOUT_TOKEN).when(mMockHttpResponse).body();

        try {
            mTs43Authentication.getAuthToken(
                    0, Ts43Constants.APP_ODSA_PRIMARY, APP_NAME, APP_VERSION);
            fail("Expected to get exception.");
        } catch (ServiceEntitlementException e) {
            assertThat(e.getErrorCode()).isEqualTo(
                    ServiceEntitlementException.ERROR_TOKEN_NOT_AVAILABLE);
        }
    }

    @Test
    public void testGetAuthToken_validityNotAvailable() throws Exception {
        doReturn(HTTP_RESPONSE_WITHOUT_VALIDITY).when(mMockHttpResponse).body();
        Ts43AuthToken mToken = mTs43Authentication.getAuthToken(
                0, Ts43Constants.APP_ODSA_PRIMARY, APP_NAME, APP_VERSION);
        assertThat(mToken.token()).isEqualTo(TOKEN);
        assertThat(mToken.validity()).isEqualTo(Ts43AuthToken.VALIDITY_NOT_AVAILABLE);
    }

    @Test
    public void testGetAuthToken_httpResponseError() throws Exception {
        doThrow(new ServiceEntitlementException(
                ServiceEntitlementException.ERROR_HTTP_STATUS_NOT_SUCCESS, 1234, "http error"))
                .when(mMockEapAkaApi).queryEntitlementStatus(any(), any(), any());
        try {
            mTs43Authentication.getAuthToken(
                    0, Ts43Constants.APP_ODSA_PRIMARY, APP_NAME, APP_VERSION);
            fail("Expected to get exception.");
        } catch (ServiceEntitlementException e) {
            assertThat(e.getErrorCode()).isEqualTo(
                    ServiceEntitlementException.ERROR_HTTP_STATUS_NOT_SUCCESS);
            assertThat(e.getHttpStatus()).isEqualTo(1234);
        }
    }
}
