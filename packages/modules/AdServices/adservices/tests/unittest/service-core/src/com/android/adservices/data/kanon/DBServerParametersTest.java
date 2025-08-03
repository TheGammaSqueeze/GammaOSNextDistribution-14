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

public class DBServerParametersTest {
    private static final String SERVER_PARAMS_VERSION_1 = "server parameter version";
    private static final Instant INSTANT_1 = Instant.now();
    private static final Instant INSTANT_2 = Instant.now().plusSeconds(123);
    private static final Instant INSTANT_3 = Instant.now().plusSeconds(1245);
    private static final byte[] SERVER_PUBLIC_PARAMS = {1, 2};

    @Test
    public void test_buildValidServerParameters_success() {
        DBServerParameters serverParameters =
                DBServerParameters.builder()
                        .setServerParamsVersion(SERVER_PARAMS_VERSION_1)
                        .setServerPublicParameters(SERVER_PUBLIC_PARAMS)
                        .setServerParamsSignExpiryInstant(INSTANT_1)
                        .setServerParamsJoinExpiryInstant(INSTANT_2)
                        .setCreationInstant(INSTANT_3)
                        .build();

        assertThat(serverParameters.getServerPublicParameters()).isEqualTo(SERVER_PUBLIC_PARAMS);
        assertThat(serverParameters.getCreationInstant()).isEqualTo(INSTANT_3);
        assertThat(serverParameters.getServerParamsJoinExpiryInstant()).isEqualTo(INSTANT_2);
        assertThat(serverParameters.getServerParamsSignExpiryInstant()).isEqualTo(INSTANT_1);
        assertThat(serverParameters.getServerParamsVersion()).isEqualTo(SERVER_PARAMS_VERSION_1);
    }

    @Test
    public void test_creationInstantNotSet_creationInstantIsGeneratedAutomatically() {
        DBServerParameters serverParameters =
                DBServerParameters.builder()
                        .setServerParamsVersion(SERVER_PARAMS_VERSION_1)
                        .setServerPublicParameters(SERVER_PUBLIC_PARAMS)
                        .setServerParamsSignExpiryInstant(INSTANT_1)
                        .setServerParamsJoinExpiryInstant(INSTANT_2)
                        .setCreationInstant(INSTANT_3)
                        .build();

        assertThat(serverParameters.getCreationInstant()).isNotNull();
    }

    @Test
    public void testBuildServerParameters_unsetServerPublicParams_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBServerParameters.builder()
                                .setServerPublicParameters(SERVER_PUBLIC_PARAMS)
                                .setServerParamsSignExpiryInstant(INSTANT_1)
                                .setServerParamsJoinExpiryInstant(INSTANT_2)
                                .setCreationInstant(INSTANT_3)
                                .build());
    }

    @Test
    public void testBuildServerParameters_unsetServerParamsVersion_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBServerParameters.builder()
                                .setServerPublicParameters(SERVER_PUBLIC_PARAMS)
                                .setServerParamsSignExpiryInstant(INSTANT_1)
                                .setServerParamsJoinExpiryInstant(INSTANT_2)
                                .setCreationInstant(INSTANT_3)
                                .build());
    }

    @Test
    public void testBuildServerParameters_unsetServerParamsSignExpiry_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBServerParameters.builder()
                                .setServerPublicParameters(SERVER_PUBLIC_PARAMS)
                                .setServerParamsJoinExpiryInstant(INSTANT_2)
                                .setCreationInstant(INSTANT_3)
                                .build());
    }

    @Test
    public void testBuildServerParameters_unsetServerParamsJoinExpiry_throwsError() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBServerParameters.builder()
                                .setServerPublicParameters(SERVER_PUBLIC_PARAMS)
                                .setServerParamsSignExpiryInstant(INSTANT_1)
                                .setCreationInstant(INSTANT_3)
                                .build());
    }
}
