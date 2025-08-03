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

package com.android.adservices.cobalt;

import static com.android.adservices.cobalt.CobaltApiKeys.copyFromHexApiKey;
import static com.android.adservices.cobalt.CobaltConstants.DEFAULT_API_KEY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.protobuf.ByteString;

import org.junit.Rule;
import org.junit.Test;

public final class CobaltApiKeysTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void defaultApiKey_copiedAsUtf8() throws Exception {
        assertThat(copyFromHexApiKey(DEFAULT_API_KEY))
                .isEqualTo(ByteString.copyFromUtf8(DEFAULT_API_KEY));
    }

    @Test
    public void hexApiKey_encodedAsBytes() throws Exception {
        String stringKey = "abcdef";
        byte[] bytesKey = {(byte) 0xab, (byte) 0xcd, (byte) 0xef};
        assertThat(copyFromHexApiKey(stringKey)).isEqualTo(ByteString.copyFrom(bytesKey));
    }

    @Test
    public void emptyApiKey_IsEmpty() throws Exception {
        assertThat(copyFromHexApiKey("")).isEqualTo(ByteString.EMPTY);
    }

    @Test
    public void nullApiKey_throwsNullPointerException() throws Exception {
        assertThrows(NullPointerException.class, () -> copyFromHexApiKey(null));
    }
}
