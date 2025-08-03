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

package com.android.adservices.data.adselection;

import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.time.temporal.ChronoUnit;

public class DBEncryptionKeyTest {

    private static final String KEY_ID_1 = "key_id_1";
    private static final String PUBLIC_KEY_1 = "public_key_1";
    private static final Long EXPIRY_TTL_SECONDS_1 = 1209600L;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidEncryptionKey_success() {
        DBEncryptionKey dBEncryptionKey =
                DBEncryptionKey.builder()
                        .setKeyIdentifier(KEY_ID_1)
                        .setPublicKey(PUBLIC_KEY_1)
                        .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                        .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS_1)
                        .build();

        assertThat(dBEncryptionKey.getKeyIdentifier()).isEqualTo(KEY_ID_1);
        assertThat(dBEncryptionKey.getPublicKey()).isEqualTo(PUBLIC_KEY_1);
        assertThat(dBEncryptionKey.getEncryptionKeyType()).isEqualTo(ENCRYPTION_KEY_TYPE_AUCTION);
        assertThat(dBEncryptionKey.getExpiryTtlSeconds()).isEqualTo(EXPIRY_TTL_SECONDS_1);
    }

    @Test
    public void testBuildEncryptionKey_unsetKeyIdentifier_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBEncryptionKey.builder()
                                .setPublicKey(PUBLIC_KEY_1)
                                .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                                .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS_1)
                                .build());
    }

    @Test
    public void testBuildEncryptionKey_unsetPublicKey_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBEncryptionKey.builder()
                                .setKeyIdentifier(KEY_ID_1)
                                .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                                .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS_1)
                                .build());
    }

    @Test
    public void testBuildEncryptionKey_unsetEncryptionKeyType_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBEncryptionKey.builder()
                                .setKeyIdentifier(KEY_ID_1)
                                .setPublicKey(PUBLIC_KEY_1)
                                .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS_1)
                                .build());
    }

    @Test
    public void testBuildEncryptionKey_unsetExpiryTtlSeconds_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBEncryptionKey.builder()
                                .setKeyIdentifier(KEY_ID_1)
                                .setPublicKey(PUBLIC_KEY_1)
                                .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                                .build());
    }

    @Test
    public void testBuildEncryptionKey_expiryInstantSetCorrectly() {
        DBEncryptionKey encryptionKey =
                DBEncryptionKey.builder()
                        .setKeyIdentifier(KEY_ID_1)
                        .setPublicKey(PUBLIC_KEY_1)
                        .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                        .setExpiryTtlSeconds(5L)
                        .build();
        assertThat(
                        encryptionKey
                                .getCreationInstant()
                                .plusSeconds(5L)
                                .truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(encryptionKey.getExpiryInstant().truncatedTo(ChronoUnit.MILLIS));
    }
}
