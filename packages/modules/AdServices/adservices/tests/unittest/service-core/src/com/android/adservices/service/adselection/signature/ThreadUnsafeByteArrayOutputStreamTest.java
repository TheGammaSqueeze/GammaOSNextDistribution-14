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

package com.android.adservices.service.adselection.signature;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ThreadUnsafeByteArrayOutputStreamTest {
    private ThreadUnsafeByteArrayOutputStream mOutputStream;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mOutputStream = new ThreadUnsafeByteArrayOutputStream();
    }

    @Test
    public void testCtorWithInitialCapacity() {
        int initialCapacity = 10000;
        ThreadUnsafeByteArrayOutputStream outputStream =
                new ThreadUnsafeByteArrayOutputStream(initialCapacity);
        assertThat(outputStream.getBuffer().length).isEqualTo(initialCapacity);
        assertThat(outputStream.getCount()).isEqualTo(0);
    }

    @Test
    public void testWriteBytes() {
        byte[] data = new byte[] {1, 2, 3, 4, 5};
        mOutputStream.writeBytes(data);
        assertThat(mOutputStream.getBytes()).isEqualTo(data);
    }

    @Test
    public void testWriteSingleByte() {
        mOutputStream.write(1);
        assertThat(mOutputStream.getBytes()).isEqualTo(new byte[] {1});
    }

    @Test
    public void testCapacityExpansion() {
        for (int i = 0; i < 100000; i++) {
            mOutputStream.write(i);
        }
        assertThat(mOutputStream.getBytes().length).isEqualTo(100000);
    }

    @Test
    public void testGetBytesReturnsCopy() {
        byte[] data = new byte[] {1, 2, 3, 4, 5};
        mOutputStream.writeBytes(data);
        byte[] retrievedData = mOutputStream.getBytes();
        assertThat(retrievedData).isEqualTo(data);
        assertThat(retrievedData).isNotSameInstanceAs(data);
    }
}
