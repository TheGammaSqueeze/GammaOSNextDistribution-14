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

import android.content.Context;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class EapAkaHelperTest {
    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    private static final int SUB_ID = 1;

    @Mock TelephonyManager mMockTelephonyManager;
    @Mock TelephonyManager mMockTelephonyManagerForSubId;

    private Context mContext;
    private EapAkaHelper mEapAkaHelper;

    @Before
    public void setUp() {
        mContext = spy(ApplicationProvider.getApplicationContext());
        when(mContext.getSystemService(TelephonyManager.class))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(SUB_ID))
                .thenReturn(mMockTelephonyManagerForSubId);

        mEapAkaHelper = EapAkaHelper.getInstance(mContext, SUB_ID);
    }

    @Test
    public void getEapAkaRootNai_twoDigitMnc() {
        when(mMockTelephonyManagerForSubId.getSubscriberId()).thenReturn("123457813240779");
        when(mMockTelephonyManagerForSubId.getSimOperator()).thenReturn("12345");

        String result = mEapAkaHelper.getEapAkaRootNai();

        assertThat(result).isEqualTo("0123457813240779@nai.epc.mnc045.mcc123.3gppnetwork.org");
    }

    @Test
    public void getEapAkaRootNai_threeDigitMnc() {
        when(mMockTelephonyManagerForSubId.getSubscriberId()).thenReturn("123457813240779");
        when(mMockTelephonyManagerForSubId.getSimOperator()).thenReturn("123457");

        String result = mEapAkaHelper.getEapAkaRootNai();

        assertThat(result).isEqualTo("0123457813240779@nai.epc.mnc457.mcc123.3gppnetwork.org");
    }

    @Test
    public void getEapAkaRootNai_invalidMccMnc_returnNull() {
        when(mMockTelephonyManagerForSubId.getSubscriberId()).thenReturn("123457813240779");
        when(mMockTelephonyManagerForSubId.getSimOperator()).thenReturn("");

        String result = mEapAkaHelper.getEapAkaRootNai();

        assertThat(result).isNull();
    }

    @Test
    public void getEapAkaChallengeResponse() {
        when(mMockTelephonyManagerForSubId.getSubscriberId()).thenReturn("234107813240779");
        when(mMockTelephonyManagerForSubId.getSimOperator()).thenReturn("23410");
        when(mMockTelephonyManagerForSubId.getIccAuthentication(
                    TelephonyManager.APPTYPE_USIM, TelephonyManager.AUTHTYPE_EAP_AKA,
                    "EFzmUpAo8crIBONxlqhyb4EQa4pFhq3pAACzVOE8Zggz6A=="))
                .thenReturn(
                        "2wjHnwKln8mjjxDzMKJvLBzMHtm0X9SNBsUWEAbEiAdD7xeqqZ7nsXzukRkIhd6SDZ4bj7s=");
        String challenge = "AQIAfBcBAAABBQAAXOZSkCjxysgE4"
                + "3GWqHJvgQIFAABrikWGrekAALNU4TxmCDPoCwUAAJT0nqXeAYlqzT0UGXINENWBBQAA7z3fhImk"
                + "q+vcCKWIZBdvuYIJAAAPRUFp7KWFo+Thr78Qj9hEkB2zA0i6KakODsufBC+BJQ==";
        String expectedResponse = "AgIAKBcBAAADAwBAx58CpZ/Jo48LBQAAHBQb3d6L7y5QL7Z5OAjefA==";

        String response = mEapAkaHelper.getEapAkaChallengeResponse(challenge);

        assertThat(response).isEqualTo(expectedResponse);
    }

    @Test
    public void getEapAkaChallengeResponse_getIccAuthenticationFailed() {
        when(mMockTelephonyManagerForSubId.getSubscriberId()).thenReturn("234107813240779");
        when(mMockTelephonyManagerForSubId.getSimOperator()).thenReturn("23410");
        when(mMockTelephonyManagerForSubId.getIccAuthentication(
                    TelephonyManager.APPTYPE_USIM, TelephonyManager.AUTHTYPE_EAP_AKA,
                    "EFzmUpAo8crIBONxlqhyb4EQa4pFhq3pAACzVOE8Zggz6A=="))
                .thenReturn(null);
        String challenge = "AQIAfBcBAAABBQAAXOZSkCjxysgE4"
                + "3GWqHJvgQIFAABrikWGrekAALNU4TxmCDPoCwUAAJT0nqXeAYlqzT0UGXINENWBBQAA7z3fhImk"
                + "q+vcCKWIZBdvuYIJAAAPRUFp7KWFo+Thr78Qj9hEkB2zA0i6KakODsufBC+BJQ==";

        String response = mEapAkaHelper.getEapAkaChallengeResponse(challenge);

        assertThat(response).isNull();
    }

    @Test
    public void getEapAkaResponse() {
        when(mMockTelephonyManagerForSubId.getSubscriberId()).thenReturn("234107813240779");
        when(mMockTelephonyManagerForSubId.getSimOperator()).thenReturn("23410");
        when(mMockTelephonyManagerForSubId.getIccAuthentication(
                    TelephonyManager.APPTYPE_USIM, TelephonyManager.AUTHTYPE_EAP_AKA,
                    "EFzmUpAo8crIBONxlqhyb4EQa4pFhq3pAACzVOE8Zggz6A=="))
                .thenReturn(
                        "2wjHnwKln8mjjxDzMKJvLBzMHtm0X9SNBsUWEAbEiAdD7xeqqZ7nsXzukRkIhd6SDZ4bj7s=");
        String challenge = "AQIAfBcBAAABBQAAXOZSkCjxysgE4"
                + "3GWqHJvgQIFAABrikWGrekAALNU4TxmCDPoCwUAAJT0nqXeAYlqzT0UGXINENWBBQAA7z3fhImk"
                + "q+vcCKWIZBdvuYIJAAAPRUFp7KWFo+Thr78Qj9hEkB2zA0i6KakODsufBC+BJQ==";
        String expectedResponse = "AgIAKBcBAAADAwBAx58CpZ/Jo48LBQAAHBQb3d6L7y5QL7Z5OAjefA==";

        EapAkaHelper.EapAkaResponse response = mEapAkaHelper.getEapAkaResponse(challenge);

        assertThat(response.response()).isEqualTo(expectedResponse);
        assertThat(response.synchronizationFailureResponse()).isNull();
    }

    @Test
    public void getEapAkaResponse_synchronizationFailure() {
        when(mMockTelephonyManagerForSubId.getSubscriberId()).thenReturn("234107813240779");
        when(mMockTelephonyManagerForSubId.getSimOperator()).thenReturn("23410");
        when(mMockTelephonyManagerForSubId.getIccAuthentication(
                    TelephonyManager.APPTYPE_USIM, TelephonyManager.AUTHTYPE_EAP_AKA,
                    "EFzmUpAo8crIBONxlqhyb4EQa4pFhq3pAACzVOE8Zggz6A=="))
                .thenReturn(
                        "3A4z1Y05YZwhM2fjdhFTFQ==");
        String challenge = "AQIAfBcBAAABBQAAXOZSkCjxysgE4"
                + "3GWqHJvgQIFAABrikWGrekAALNU4TxmCDPoCwUAAJT0nqXeAYlqzT0UGXINENWBBQAA7z3fhImk"
                + "q+vcCKWIZBdvuYIJAAAPRUFp7KWFo+Thr78Qj9hEkB2zA0i6KakODsufBC+BJQ==";
        String expectedResponse = "AgIAGBcEAAAEBDPVjTlhnCEzZ+N2EVMV";

        EapAkaHelper.EapAkaResponse response = mEapAkaHelper.getEapAkaResponse(challenge);

        assertThat(response.response()).isNull();
        assertThat(response.synchronizationFailureResponse()).isEqualTo(expectedResponse);
    }

    @Test
    public void getEapAkaResponse_getIccAuthenticationFailed() {
        when(mMockTelephonyManagerForSubId.getSubscriberId()).thenReturn("234107813240779");
        when(mMockTelephonyManagerForSubId.getSimOperator()).thenReturn("23410");
        when(mMockTelephonyManagerForSubId.getIccAuthentication(
                    TelephonyManager.APPTYPE_USIM, TelephonyManager.AUTHTYPE_EAP_AKA,
                    "EFzmUpAo8crIBONxlqhyb4EQa4pFhq3pAACzVOE8Zggz6A=="))
                .thenReturn(null);
        String challenge = "AQIAfBcBAAABBQAAXOZSkCjxysgE4"
                + "3GWqHJvgQIFAABrikWGrekAALNU4TxmCDPoCwUAAJT0nqXeAYlqzT0UGXINENWBBQAA7z3fhImk"
                + "q+vcCKWIZBdvuYIJAAAPRUFp7KWFo+Thr78Qj9hEkB2zA0i6KakODsufBC+BJQ==";

        EapAkaHelper.EapAkaResponse response = mEapAkaHelper.getEapAkaResponse(challenge);

        assertThat(response).isNull();
    }
}
