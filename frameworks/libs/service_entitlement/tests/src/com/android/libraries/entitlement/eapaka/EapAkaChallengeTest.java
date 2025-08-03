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

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.util.Base64;

import androidx.test.runner.AndroidJUnit4;

import com.android.libraries.entitlement.ServiceEntitlementException;

import com.google.common.io.BaseEncoding;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EapAkaChallengeTest {
    // EAP-AKA challenge request : AQIAfBcBAAABBQAAXOZSkCjxysgE43GWqHJvgQIFAABrikWGrekAALNU4TxmCDPo
    //                             CwUAAJT0nqXeAYlqzT0UGXINENWBBQAA7z3fhImkq+vcCKWIZBdvuYIJAAAPRUFp
    //                             7KWFo+Thr78Qj9hEkB2zA0i6KakODsufBC+BJQ==
    // Base64 decoded : 0102007C17010000010500005CE6529028F1CAC804E37196A8726F81020500006B8A4586ADE
    //                  90000B354E13C660833E80B05000094F49EA5DE01896ACD3D1419720D10D581050000EF3DDF
    //                  8489A4ABEBDC08A58864176FB9820900000F454169ECA585A3E4E1AFBF108FD844901DB3034
    //                  8BA29A90E0ECB9F042F8125
    // HEADER
    // Code : 0x01 (Request)
    // Identifier : 0x02
    // Length : 0x007C = 124
    // Type : 0x17=23 (EAP-AKA)
    // SubType : 0x01 = 1 (AKA Challenge)
    // Reserved : 0x0000
    //
    // Type-DATA
    //
    // Attribute Type : 0x01 (AT_RAND)
    // Length : 0x05 = 5's 4 bytes = 20 bytes
    // Reserved : 0x0000
    // RAND value : 0x5CE6529028F1CAC804E37196A8726F81
    //
    // Attribute Type : 0x02 (AT_AUTN)
    // Length : 0x05 = 5's 4 bytes = 20 bytes
    // Reserved : 0x0000
    // AUTN value : 0x6B8A4586ADE90000B354E13C660833E8
    //
    // Attribute Type : 0x0B (AT_MAC)
    // Length : 0x05 = 20 bytes
    // Reserved : 0x0000
    // MAC value :  0x94F49EA5DE01896ACD3D1419720D10D5
    //
    // Attribute Type : 0x81 (AT_IV)
    // Length : 0x05
    // Reserved : 0x0000
    // IV value : 0xEF3DDF8489A4ABEBDC08A58864176FB9
    //
    // Attribute Type : 0x82 (AT_ENCR_DATA)
    // Length : 0x09 = 36 bytes
    // Reserved : 0x0000
    // ENCR value : 0x0F454169ECA585A3E4E1AFBF108FD844901DB30348BA29A90E0ECB9F042F8125
    //
    // Derived 3G security context for SIM Authentication request
    // Hex : 105CE6529028F1CAC804E37196A8726F81106B8A4586ADE90000B354E13C660833E8
    // Base64 encoded : EFzmUpAo8crIBONxlqhyb4EQa4pFhq3pAACzVOE8Zggz6A==
    public static final String EAP_AKA_CHALLENGE_REQUEST =
            "AQIAfBcBAAABBQAAXOZSkCjxysgE4"
                    + "3GWqHJvgQIFAABrikWGrekAALNU4TxmCDPoCwUAAJT0nqXeAYlqzT0UGXINENWBBQAA7z3fhImk"
                    + "g+vcCKWIZBdvuYIJAAAPRUFp7KWFo+Thr78Qj9hEkB2zA0i6KakODsufBC+BJQ==";
    public static final String EAP_AKA_SECURITY_CONTEXT_REQUEST_EXPECTED =
            "EFzmUpAo8crIBONxlqhyb4EQa4pFhq3pAACzVOE8Zggz6A==";
    private static final String EAP_AKA_CHALLENGE_REQUEST_WITHOUT_RAND =
            "0102006817010000020500006"
                    + "B8A4586ADE90000B354E13C660833E80B05000094F49EA5DE01896ACD3D1419720D10D58105"
                    + "0000EF3DDF8489A4ABEBDC08A58864176FB9820900000F454169ECA585A3E4E1AFBF108FD84"
                    + "4901DB30348BA29A90E0ECB9F042F8125";
    private static final String EAP_AKA_CHALLENGE_REQUEST_WITHOUT_AUTN =
            "0102006817010000010500005"
                    + "CE6529028F1CAC804E37196A8726F810B05000094F49EA5DE01896ACD3D1419720D10D5810"
                    + "50000EF3DDF8489A4ABEBDC08A58864176FB9820900000F454169ECA585A3E4E1AFBF108FD"
                    + "844901DB30348BA29A90E0ECB9F042F8125";
    private static final String EAP_AKA_CHALLENGE_REQUEST_WITHOUT_RAND_AUTN =
            "01020054170100000B05"
                    + "000094F49EA5DE01896ACD3D1419720D10D581050000EF3DDF8489A4ABEBDC08A58864176F"
                    + "B9820900000F454169ECA585A3E4E1AFBF108FD844901DB30348BA29A90E0ECB9F042F8125";
    private static final String EAP_AKA_CHALLENGE_REQUEST_WITH_CODE_RESPONSE =
            "02020054170100000B0"
                    + "5000094F49EA5DE01896ACD3D1419720D10D581050000EF3DDF8489A4ABEBDC08A58864176F"
                    + "B9820900000F454169ECA585A3E4E1AFBF108FD844901DB30348BA29A90E0ECB9F042F8125";
    private static final String EAP_AKA_CHALLENGE_REQUEST_WITH_WRONG_LENGTH =
            "01020099170100000B05"
                    + "000094F49EA5DE01896ACD3D1419720D10D581050000EF3DDF8489A4ABEBDC08A58864176FB"
                    + "9820900000F454169ECA585A3E4E1AFBF108FD844901DB30348BA29A90E0ECB9F042F8125";
    private static final String EAP_AKA_CHALLENGE_REQUEST_WITH_EAP_TYPE_99 =
            "01020054990100000B050"
                    + "00094F49EA5DE01896ACD3D1419720D10D581050000EF3DDF8489A4ABEBDC08A58864176FB9"
                    + "F454169ECA585A3E4E1AFBF108FD844901DB30348BA29A90E0ECB9F042F8125820900000";
    private static final String EAP_AKA_CHALLENGE_REQUEST_WITH_SUBTYPE_99 =
            "01020054179900000B0500"
                    + "0094F49EA5DE01896ACD3D1419720D10D581050000EF3DDF8489A4ABEBDC08A58864176FB98"
                    + "454169ECA585A3E4E1AFBF108FD844901DB30348BA29A90E0ECB9F042F812520900000F";
    private static final String EAP_AKA_CHALLENGE_REQUEST_WITH_LENGTH_OVER_DATA =
            "0102001C17010000010600005CE6529028F1CAC804E37196A8726F81";
    private static final String EAP_AKA_CHALLENGE_REQUEST_WITH_RAND_LENGTH_16 =
            "0102001817010000010400005CE6529028F1CAC804E37196";
    private static final String EAP_AKA_CHALLENGE_REQUEST_WITH_AUTN_LENGTH_16 =
            "0102001817010000020400006B8A4586ADE90000B354E13C";

    @Test
    public void parseEapAkaChallengeRequest() throws Exception {
        EapAkaChallenge message =
                EapAkaChallenge.parseEapAkaChallenge(EAP_AKA_CHALLENGE_REQUEST);

        assertThat(message.getIdentifier()).isEqualTo((byte) 0x02);
        assertThat(message.getSimAuthenticationRequest())
                .isEqualTo(EAP_AKA_SECURITY_CONTEXT_REQUEST_EXPECTED);
    }

    @Test
    public void parseEapAkaChallengeRequest_withoutRand() {
        byte[] data = convertHexStringToBytes(EAP_AKA_CHALLENGE_REQUEST_WITHOUT_RAND);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class,
                () -> EapAkaChallenge.parseEapAkaChallenge(encodedData));
    }

    @Test
    public void parseEapAkaChallengeRequest_withoutAutn() {
        byte[] data = convertHexStringToBytes(EAP_AKA_CHALLENGE_REQUEST_WITHOUT_AUTN);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class,
                () -> EapAkaChallenge.parseEapAkaChallenge(encodedData));
    }

    @Test
    public void parseEapAkaChallengeRequest_withoutRandAndAutn() {
        byte[] data = convertHexStringToBytes(EAP_AKA_CHALLENGE_REQUEST_WITHOUT_RAND_AUTN);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class,
                () -> EapAkaChallenge.parseEapAkaChallenge(encodedData));
    }

    @Test
    public void parseEapAkaChallengeRequest_withCodeResponse() {
        byte[] data = convertHexStringToBytes(EAP_AKA_CHALLENGE_REQUEST_WITH_CODE_RESPONSE);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class,
                () -> EapAkaChallenge.parseEapAkaChallenge(encodedData));
    }

    @Test
    public void parseEapAkaChallengeRequest_withWrongLength() {
        byte[] data = convertHexStringToBytes(EAP_AKA_CHALLENGE_REQUEST_WITH_WRONG_LENGTH);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class,
                () -> EapAkaChallenge.parseEapAkaChallenge(encodedData));
    }

    @Test
    public void parseEapAkaChallengeRequest_withEapType99() {
        byte[] data = convertHexStringToBytes(EAP_AKA_CHALLENGE_REQUEST_WITH_EAP_TYPE_99);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class,
                () -> EapAkaChallenge.parseEapAkaChallenge(encodedData));
    }

    @Test
    public void parseEapAkaChallengeRequest_withSubType99() {
        byte[] data = convertHexStringToBytes(EAP_AKA_CHALLENGE_REQUEST_WITH_SUBTYPE_99);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class,
                () -> EapAkaChallenge.parseEapAkaChallenge(encodedData));
    }

    @Test
    public void parseEapAkaChallengeRequest_withLengthOverData() {
        byte[] data = convertHexStringToBytes(EAP_AKA_CHALLENGE_REQUEST_WITH_LENGTH_OVER_DATA);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class,
                () -> EapAkaChallenge.parseEapAkaChallenge(encodedData));
    }

    @Test
    public void parseEapAkaChallengeRequest_withRandLength16() {
        byte[] data = convertHexStringToBytes(EAP_AKA_CHALLENGE_REQUEST_WITH_RAND_LENGTH_16);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class,
                () -> EapAkaChallenge.parseEapAkaChallenge(encodedData));
    }

    @Test
    public void parseEapAkaChallengeRequest_withAutnLength16() {
        byte[] data = convertHexStringToBytes(EAP_AKA_CHALLENGE_REQUEST_WITH_AUTN_LENGTH_16);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class,
                () -> EapAkaChallenge.parseEapAkaChallenge(encodedData));
    }

    @Test
    public void parseEapAkaChallengeRequest_notValid_throwException() {
        byte[] data = convertHexStringToBytes(EAP_AKA_CHALLENGE_REQUEST_WITH_WRONG_LENGTH);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class,
                () -> EapAkaChallenge.parseEapAkaChallenge(encodedData));
    }

    private static byte[] convertHexStringToBytes(String input) {
        return BaseEncoding.base16().decode(input);
    }
}
