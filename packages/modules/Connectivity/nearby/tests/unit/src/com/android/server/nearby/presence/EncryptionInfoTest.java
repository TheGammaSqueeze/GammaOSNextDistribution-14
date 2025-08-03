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

package com.android.server.nearby.presence;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.server.nearby.presence.EncryptionInfo.EncodingScheme;
import com.android.server.nearby.util.ArrayUtils;

import org.junit.Test;


/**
 * Unit test for {@link EncryptionInfo}.
 */
public class EncryptionInfoTest {
    private static final byte[] SALT =
            new byte[]{25, -21, 35, -108, -26, -126, 99, 60, 110, 45, -116, 34, 91, 126, -23, 127};

    @Test
    public void test_illegalLength() {
        byte[] data = new byte[]{1, 2};
        assertThrows(IllegalArgumentException.class, () -> new EncryptionInfo(data));
    }

    @Test
    public void test_illegalEncodingScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> new EncryptionInfo(ArrayUtils.append((byte) 0b10110000, SALT)));
        assertThrows(IllegalArgumentException.class,
                () -> new EncryptionInfo(ArrayUtils.append((byte) 0b01101000, SALT)));
    }

    @Test
    public void test_getMethods_signature() {
        byte[] data = ArrayUtils.append((byte) 0b10001000, SALT);
        EncryptionInfo info = new EncryptionInfo(data);
        assertThat(info.getEncodingScheme()).isEqualTo(EncodingScheme.SIGNATURE);
        assertThat(info.getSalt()).isEqualTo(SALT);
    }

    @Test
    public void test_getMethods_mic() {
        byte[] data = ArrayUtils.append((byte) 0b10000000, SALT);
        EncryptionInfo info = new EncryptionInfo(data);
        assertThat(info.getEncodingScheme()).isEqualTo(EncodingScheme.MIC);
        assertThat(info.getSalt()).isEqualTo(SALT);
    }
    @Test
    public void test_toBytes() {
        byte[] data = EncryptionInfo.toByte(EncodingScheme.MIC, SALT);
        EncryptionInfo info = new EncryptionInfo(data);
        assertThat(info.getEncodingScheme()).isEqualTo(EncodingScheme.MIC);
        assertThat(info.getSalt()).isEqualTo(SALT);
    }
}
