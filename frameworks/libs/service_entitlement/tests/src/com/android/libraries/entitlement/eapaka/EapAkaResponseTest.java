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

package com.android.libraries.entitlement.eapaka;

import static com.android.libraries.entitlement.eapaka.EapAkaChallengeTest.EAP_AKA_CHALLENGE_REQUEST;
import static com.android.libraries.entitlement.eapaka.EapAkaChallengeTest.EAP_AKA_SECURITY_CONTEXT_REQUEST_EXPECTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Base64;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.io.BaseEncoding;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class EapAkaResponseTest {
    // EAP_AKA_CHALLENGE_REQUEST:
    // Identifier :
    //   0x02
    // Derived 3G security context for SIM authentication request :
    //   EAP_AKA_SECURITY_CONTEXT_REQUEST_EXPECTED

    // 3G security context for SIM authentication response
    //
    // Success:
    // Hex : DB08C79F02A59FC9A38F10F330A26F2C1CCC1ED9B45FD48D06C5161006C4880743EF17AAA99EE7B17
    //       CEE91190885DE920D9E1B8FBB
    // Base64 encoded : 2wjHnwKln8mjjxDzMKJvLBzMHtm0X9SNBsUWEAbEiAdD7xeqqZ7nsXzukRkIhd6SDZ4bj7s=
    //
    // Synchronization failure:
    // Hex : DC0E33D58D39619C213367E376115315
    // Base64 encoded : 3A4z1Y05YZwhM2fjdhFTFQ==
    public static final String EAP_AKA_SECURITY_CONTEXT_RESPONSE_SUCCESS =
            "2wjHnwKln8mjjxDzMKJvLBzMHtm0X9SNBsUWEAbEiAdD7xeqqZ7nsXzukRkIhd6SDZ4bj7s=";
    public static final String EAP_AKA_SECURITY_CONTEXT_RESPONSE_SYNC_FAILURE =
            "3A4z1Y05YZwhM2fjdhFTFQ==";

    // EAP-AKA challenge response
    // Identifier : 0x02, same as in EAP_AKA_CHALLENGE_REQUEST
    // Success
    private static final String EAP_AKA_CHALLENGE_RESPONSE =
            "020200281701000003030040C79F02A59FC9A38F0B0500001C141BDDDE8BEF2E502FB6793808DE7C";
    // Synchronization failure
    private static final String EAP_AKA_CHALLENGE_SYNC_FAILURE =
            "0202001817040000040433D58D39619C213367E376115315";

    private static final int SUB_ID = 0;
    private static final String IMSI = "234107813240779";
    private static final String MCCMNC = "23410";

    @Rule public final MockitoRule rule = MockitoJUnit.rule();
    @Mock private TelephonyManager mMockTelephonyManager;
    @Mock private TelephonyManager mMockTelephonyManagerForSubId;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = spy(ApplicationProvider.getApplicationContext());
        when(mContext.getSystemService(TelephonyManager.class))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(SUB_ID))
                .thenReturn(mMockTelephonyManagerForSubId);
        when(mMockTelephonyManagerForSubId.getSubscriberId()).thenReturn(IMSI);
        when(mMockTelephonyManagerForSubId.getSimOperator()).thenReturn(MCCMNC);
    }

    @Test
    public void generateEapAkaChallengeResponse_authSuccess() throws Exception {
        EapAkaChallenge challenge = EapAkaChallenge.parseEapAkaChallenge(EAP_AKA_CHALLENGE_REQUEST);
        when(mMockTelephonyManagerForSubId.getIccAuthentication(
                TelephonyManager.APPTYPE_USIM,
                TelephonyManager.AUTHTYPE_EAP_AKA,
                EAP_AKA_SECURITY_CONTEXT_REQUEST_EXPECTED))
                .thenReturn(EAP_AKA_SECURITY_CONTEXT_RESPONSE_SUCCESS);
        String expectedResponse =
                Base64.encodeToString(
                        convertHexStringToBytes(EAP_AKA_CHALLENGE_RESPONSE), Base64.NO_WRAP);

        EapAkaResponse challengeResponse =
                EapAkaResponse.respondToEapAkaChallenge(mContext, SUB_ID, challenge);

        assertThat(challengeResponse.response()).isEqualTo(expectedResponse);
    }

    @Test
    public void generateEapAkaChallengeResponse_syncFailure() throws Exception {
        EapAkaChallenge challenge = EapAkaChallenge.parseEapAkaChallenge(EAP_AKA_CHALLENGE_REQUEST);
        when(mMockTelephonyManagerForSubId.getIccAuthentication(
                TelephonyManager.APPTYPE_USIM,
                TelephonyManager.AUTHTYPE_EAP_AKA,
                EAP_AKA_SECURITY_CONTEXT_REQUEST_EXPECTED))
                .thenReturn(EAP_AKA_SECURITY_CONTEXT_RESPONSE_SYNC_FAILURE);
        String expectedResponse =
                Base64.encodeToString(
                        convertHexStringToBytes(EAP_AKA_CHALLENGE_SYNC_FAILURE), Base64.NO_WRAP);

        EapAkaResponse challengeResponse =
                EapAkaResponse.respondToEapAkaChallenge(mContext, SUB_ID, challenge);

        assertThat(challengeResponse.synchronizationFailureResponse()).isEqualTo(expectedResponse);
    }

    private static byte[] convertHexStringToBytes(String input) {
        return BaseEncoding.base16().decode(input);
    }
}
