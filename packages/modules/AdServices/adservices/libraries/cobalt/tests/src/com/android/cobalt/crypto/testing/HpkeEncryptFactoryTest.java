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

package com.android.cobalt.crypto.testing;

import static com.google.common.truth.Truth.assertThat;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.cobalt.crypto.HpkeEncrypt;

import org.junit.Test;

public class HpkeEncryptFactoryTest {
    private static final byte[] PUBLIC_KEY = "test_key".getBytes(UTF_8);
    private static final byte[] PLAIN_TEXT = "test_plaintext".getBytes(UTF_8);
    private static final byte[] CONTEXT_INFO = "test".getBytes(UTF_8);

    @Test
    public void emptyHpkeEncrpyt_returnsEmpty() {
        HpkeEncrypt emptyEncrypt = HpkeEncryptFactory.emptyHpkeEncrypt();
        byte[] ciphertext = emptyEncrypt.encrypt(PUBLIC_KEY, PLAIN_TEXT, CONTEXT_INFO);
        assertThat(ciphertext).isEqualTo(new byte[] {});
    }

    @Test
    public void noOpHpkeEncrypt_retainsPlaintext() {
        HpkeEncrypt noOpEncrypt = HpkeEncryptFactory.noOpHpkeEncrypt();
        byte[] ciphertext = noOpEncrypt.encrypt(PUBLIC_KEY, PLAIN_TEXT, CONTEXT_INFO);
        assertThat(ciphertext).isEqualTo(PLAIN_TEXT);
    }
}
