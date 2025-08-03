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
import static org.testng.Assert.expectThrows;

import android.util.Base64;

import androidx.test.runner.AndroidJUnit4;

import com.android.libraries.entitlement.ServiceEntitlementException;

import com.google.common.io.BaseEncoding;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EapAkaSecurityContextTest {
    // Base64 data : 2wjHnwKln8mjjxDzMKJvLBzMHtm0X9SNBsUWEAbEiAdD7xeqqZ7nsXzukRkIhd6SDZ4bj7s=
    // RAW DATA : DB08C79F02A59FC9A38F10F330A26F2C1CCC1ED9B45FD48D06C5161006C4880743EF17AAA99EE7B17
    //            CEE91190885DE920D9E1B8FBB
    // TAG : DB
    // RES length : 08
    // RES : C79F02A59FC9A38F
    // CK Length : 10
    // CK : F330A26F2C1CCC1ED9B45FD48D06C516
    // IK Length : 10
    // IK : 06C4880743EF17AAA99EE7B17CEE9119
    // KC Length : 08
    // KC : 85DE920D9E1B8FBB
    private static final String GSM_SECURITY_CONTEXT_RESPONSE =
            "2wjHnwKln8mjjxDzMKJvLBzMHtm0X9SNBsUWEAbEiAdD7xeqqZ7nsXzukRkIhd6SDZ4bj7s=";
    private static final String EXPECTED_IK = "06C4880743EF17AAA99EE7B17CEE9119";
    private static final String EXPECTED_CK = "F330A26F2C1CCC1ED9B45FD48D06C516";
    private static final String EXPECTED_RES = "C79F02A59FC9A38F";
    // Invalid data
    private static final String GSM_SECURITY_CONTEXT_RESPONSE_TAG_DE = "DE08C79F02A59FC9A38F";
    private static final String GSM_SECURITY_CONTEXT_RESPONSE_INVALID_RES_LENGTH =
            "DC40C79F02A59FC"
                    + "9A38F10F330A26F2C1CCC1ED9B45FD48D06C5161006C4880743EF17AAA99EE7B17CEE911908"
                    + "85DE920D9E1B8FBB";
    private static final String GSM_SECURITY_CONTEXT_RESPONSE_INVALID_CK_LENGTH =
            "DB08C79F02A59FC9"
                    + "A38F40F330A26F2C1CCC1ED9B45FD48D06C5161006C4880743EF17AAA99EE7B17CEE9119088"
                    + "5DE920D9E1B8FBB";
    private static final String GSM_SECURITY_CONTEXT_RESPONSE_INVALID_IK_LENGTH =
            "DB08C79F02A59FC9"
                    + "A38F10F330A26F2C1CCC1ED9B45FD48D06C5164006C4880743EF17AAA99EE7B17CEE9119088"
                    + "5DE920D9E1B8FBB";
    private static final String GSM_SECURITY_CONTEXT_RESPONSE_INVALID_KC_LENGTH =
            "DB08C79F02A59FC9"
                    + "A38F10F330A26F2C1CCC1ED9B45FD48D06C5161006C4880743EF17AAA99EE7B17CEE9119108"
                    + "5DE920D9E1B8FBB";
    private static final String GSM_SECURITY_CONTEXT_RESPONSE_NO_KC_KEY =
            "DB08C79F02A59FC9A38F10F3"
                    + "30A26F2C1CCC1ED9B45FD48D06C5161006C4880743EF17AAA99EE7B17CEE9119";
    // Base64 data : 3A4z1Y05YZwhM2fjdhFTFQ==
    // RAW DATA : DC0E33D58D39619C213367E376115315
    // TAG : DC
    // AUTS length : 0E
    // AUTS : 33D58D39619C213367E376115315
    private static final String RESPONSE_SYNC_FAILURE = "3A4z1Y05YZwhM2fjdhFTFQ==";
    private static final String EXPECTED_AUTS = "33D58D39619C213367E376115315";

    @Test
    public void parseResponseData_validResponse_pass() throws Exception {
        EapAkaSecurityContext securityContext =
                EapAkaSecurityContext.from(GSM_SECURITY_CONTEXT_RESPONSE);

        assertThat(securityContext.getIk()).isEqualTo(convertHexStringToBytes(EXPECTED_IK));
        assertThat(securityContext.getCk()).isEqualTo(convertHexStringToBytes(EXPECTED_CK));
        assertThat(securityContext.getRes()).isEqualTo(convertHexStringToBytes(EXPECTED_RES));
    }

    @Test
    public void parseResponseData_invalidWithWrongTag_throwsException() {
        byte[] data = convertHexStringToBytes(GSM_SECURITY_CONTEXT_RESPONSE_TAG_DE);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        ServiceEntitlementException exception =
                expectThrows(
                        ServiceEntitlementException.class,
                        () -> EapAkaSecurityContext.from(encodedData));

        assertThat(exception.getErrorCode())
                .isEqualTo(ServiceEntitlementException.ERROR_ICC_AUTHENTICATION_NOT_AVAILABLE);
        assertThat(exception.getMessage())
                .isEqualTo("Invalid SIM EAP-AKA authentication response!");
        assertThat(exception.getHttpStatus()).isEqualTo(0);
        assertThat(exception.getRetryAfter()).isEmpty();
    }

    @Test
    public void parseResponseData_invalidWithWrongResLength_throwsException() {
        byte[] data = convertHexStringToBytes(GSM_SECURITY_CONTEXT_RESPONSE_INVALID_RES_LENGTH);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class, () -> EapAkaSecurityContext.from(encodedData));
    }

    @Test
    public void parseResponseData_invalidWithWrongCkLength_throwsException() {
        byte[] data = convertHexStringToBytes(GSM_SECURITY_CONTEXT_RESPONSE_INVALID_CK_LENGTH);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class, () -> EapAkaSecurityContext.from(encodedData));
    }

    @Test
    public void parseResponseData_invalidWithWrongIkLength_throwsException() {
        byte[] data = convertHexStringToBytes(GSM_SECURITY_CONTEXT_RESPONSE_INVALID_IK_LENGTH);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        assertThrows(
                ServiceEntitlementException.class, () -> EapAkaSecurityContext.from(encodedData));
    }

    @Test
    public void parseResponseData_validWithWrongKcLength() throws Exception {
        // Because we don't parse KC, invalid KC length doesn't hurt
        byte[] data = convertHexStringToBytes(GSM_SECURITY_CONTEXT_RESPONSE_INVALID_KC_LENGTH);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        EapAkaSecurityContext securityContext = EapAkaSecurityContext.from(encodedData);

        assertThat(securityContext.getIk()).isEqualTo(convertHexStringToBytes(EXPECTED_IK));
        assertThat(securityContext.getCk()).isEqualTo(convertHexStringToBytes(EXPECTED_CK));
        assertThat(securityContext.getRes()).isEqualTo(convertHexStringToBytes(EXPECTED_RES));
    }

    @Test
    public void parseResponseData_noKcKey() throws Exception {
        byte[] data = convertHexStringToBytes(GSM_SECURITY_CONTEXT_RESPONSE_NO_KC_KEY);
        String encodedData = Base64.encodeToString(data, Base64.NO_WRAP).trim();

        EapAkaSecurityContext securityContext = EapAkaSecurityContext.from(encodedData);

        assertThat(securityContext.getIk()).isEqualTo(convertHexStringToBytes(EXPECTED_IK));
        assertThat(securityContext.getCk()).isEqualTo(convertHexStringToBytes(EXPECTED_CK));
        assertThat(securityContext.getRes()).isEqualTo(convertHexStringToBytes(EXPECTED_RES));
    }

    @Test
    public void parseResponseData_syncFailure() throws Exception {
        EapAkaSecurityContext securityContext = EapAkaSecurityContext.from(RESPONSE_SYNC_FAILURE);

        assertThat(securityContext.getAuts()).isEqualTo(convertHexStringToBytes(EXPECTED_AUTS));
        assertThat(securityContext.getRes()).isNull();
        assertThat(securityContext.getCk()).isNull();
        assertThat(securityContext.getIk()).isNull();
    }

    private byte[] convertHexStringToBytes(String input) {
        return BaseEncoding.base16().decode(input);
    }
}
