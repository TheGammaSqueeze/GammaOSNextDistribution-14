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
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;

import com.google.common.io.BaseEncoding;

import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

public class DBEncryptionContextTest {
    private static final long CONTEXT_ID_1 = 1L;
    private static final String SHARED_SECRET_STRING = "1";
    private static final String KEY_CONFIG_HEX =
            "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                    + "000400010001";
    private static final byte[] SEED_BYTES =
            "6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] SHARED_SECRET_BYTES =
            SHARED_SECRET_STRING.getBytes(StandardCharsets.UTF_8);
    private Clock mClock = Clock.systemUTC();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildEncryptionContext_success() throws Exception {
        ObliviousHttpKeyConfig keyConfig =
                ObliviousHttpKeyConfig.fromSerializedKeyConfig(
                        BaseEncoding.base16().lowerCase().decode(KEY_CONFIG_HEX));
        DBEncryptionContext dbEncryptionContext =
                DBEncryptionContext.builder()
                        .setContextId(CONTEXT_ID_1)
                        .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                        .setCreationInstant(mClock.instant())
                        .setKeyConfig(keyConfig.serializeKeyConfigToBytes())
                        .setSharedSecret(SHARED_SECRET_BYTES)
                        .setSeed(SEED_BYTES)
                        .build();

        assertThat(dbEncryptionContext.getContextId()).isEqualTo(CONTEXT_ID_1);
        assertThat(dbEncryptionContext.getKeyConfig())
                .isEqualTo(BaseEncoding.base16().lowerCase().decode(KEY_CONFIG_HEX));
        assertThat(dbEncryptionContext.getSharedSecret()).isEqualTo(SHARED_SECRET_BYTES);
        assertThat(dbEncryptionContext.getSeed()).isEqualTo(SEED_BYTES);
    }

    @Test
    public void test_unsetFields_throwsISE() {
        assertThrows(IllegalStateException.class, () -> DBEncryptionContext.builder().build());
    }
}
