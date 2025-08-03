/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.data.kanon;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.time.Instant;
import java.util.UUID;

public class DBClientParametersTest {
    private static final UUID CLIENT_ID_1 = UUID.randomUUID();
    private static final String CLIENT_PARAMS_VERSION_1 = "Client parameter version";
    private static final Instant EXPIRY_INSTANT_1 = Instant.now();
    private static final byte[] CLIENT_PUBLIC_PARAMS = {1, 2};
    private static final byte[] CLIENT_PRIVATE_PARAMS = {3, 4};
    private static final Long CLIENT_PARAMS_ID = 1L;

    @Test
    public void test_buildValidClientParameters_success() {
        DBClientParameters clientParameters =
                DBClientParameters.builder()
                        .setClientId(CLIENT_ID_1)
                        .setClientPrivateParameters(CLIENT_PRIVATE_PARAMS)
                        .setClientPublicParameters(CLIENT_PUBLIC_PARAMS)
                        .setClientParametersExpiryInstant(EXPIRY_INSTANT_1)
                        .setClientParamsVersion(CLIENT_PARAMS_VERSION_1)
                        .setClientParametersId(CLIENT_PARAMS_ID)
                        .build();

        assertThat(clientParameters.getClientId()).isEqualTo(CLIENT_ID_1);
        assertThat(clientParameters.getClientParametersExpiryInstant()).isEqualTo(EXPIRY_INSTANT_1);
        assertThat(clientParameters.getClientPublicParameters()).isEqualTo(CLIENT_PUBLIC_PARAMS);
        assertThat(clientParameters.getClientPrivateParameters()).isEqualTo(CLIENT_PRIVATE_PARAMS);
        assertThat(clientParameters.getClientParamsVersion()).isEqualTo(CLIENT_PARAMS_VERSION_1);
        assertThat(clientParameters.getClientParametersId()).isEqualTo(CLIENT_PARAMS_ID);
    }

    @Test
    public void test_clientParameterIdNotSet_clientParameterId_buildsSuccessfully() {
        DBClientParameters clientParameters =
                DBClientParameters.builder()
                        .setClientId(CLIENT_ID_1)
                        .setClientPrivateParameters(CLIENT_PRIVATE_PARAMS)
                        .setClientPublicParameters(CLIENT_PUBLIC_PARAMS)
                        .setClientParametersExpiryInstant(EXPIRY_INSTANT_1)
                        .setClientParamsVersion(CLIENT_PARAMS_VERSION_1)
                        .build();

        assertThat(clientParameters.getClientId()).isEqualTo(CLIENT_ID_1);
        assertThat(clientParameters.getClientParametersExpiryInstant()).isEqualTo(EXPIRY_INSTANT_1);
        assertThat(clientParameters.getClientPublicParameters()).isEqualTo(CLIENT_PUBLIC_PARAMS);
        assertThat(clientParameters.getClientPrivateParameters()).isEqualTo(CLIENT_PRIVATE_PARAMS);
        assertThat(clientParameters.getClientParamsVersion()).isEqualTo(CLIENT_PARAMS_VERSION_1);
    }

    @Test
    public void testBuildClientParameters_unsetClientId_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBClientParameters.builder()
                                .setClientPrivateParameters(CLIENT_PRIVATE_PARAMS)
                                .setClientPublicParameters(CLIENT_PUBLIC_PARAMS)
                                .setClientParametersExpiryInstant(EXPIRY_INSTANT_1)
                                .setClientParamsVersion(CLIENT_PARAMS_VERSION_1)
                                .build());
    }

    @Test
    public void testBuildClientParameters_unsetClientParamsVersion_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBClientParameters.builder()
                                .setClientId(CLIENT_ID_1)
                                .setClientPrivateParameters(CLIENT_PRIVATE_PARAMS)
                                .setClientPublicParameters(CLIENT_PUBLIC_PARAMS)
                                .setClientParametersExpiryInstant(EXPIRY_INSTANT_1)
                                .build());
    }

    @Test
    public void testBuildClientParameters_unsetClientPrivateParams_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBClientParameters.builder()
                                .setClientId(CLIENT_ID_1)
                                .setClientPublicParameters(CLIENT_PUBLIC_PARAMS)
                                .setClientParametersExpiryInstant(EXPIRY_INSTANT_1)
                                .setClientParamsVersion(CLIENT_PARAMS_VERSION_1)
                                .build());
    }

    @Test
    public void testBuildClientParameters_unsetClientPublicParams_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBClientParameters.builder()
                                .setClientId(CLIENT_ID_1)
                                .setClientPrivateParameters(CLIENT_PRIVATE_PARAMS)
                                .setClientParametersExpiryInstant(EXPIRY_INSTANT_1)
                                .setClientParamsVersion(CLIENT_PARAMS_VERSION_1)
                                .build());
    }

    @Test
    public void testBuildClientParameters_unsetClientExpiryInstant_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBClientParameters.builder()
                                .setClientId(CLIENT_ID_1)
                                .setClientPrivateParameters(CLIENT_PRIVATE_PARAMS)
                                .setClientPublicParameters(CLIENT_PUBLIC_PARAMS)
                                .setClientParamsVersion(CLIENT_PARAMS_VERSION_1)
                                .build());
    }
}
