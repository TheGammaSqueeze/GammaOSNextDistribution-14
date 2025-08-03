/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.obex.HeaderSet;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ObexAppParametersTest {

    private static final byte KEY = 0x12;

    @Test
    public void constructorWithByteArrays_withOneInvalidElement() {
        final int length = 4;

        byte[] byteArray = new byte[] {KEY, length, 0x12, 0x34, 0x56, 0x78,
                0x66}; // Last one is invalid. It will be filtered out.

        ObexAppParameters params = new ObexAppParameters(byteArray);
        assertThat(params.exists(KEY)).isTrue();

        byte[] expected = Arrays.copyOfRange(byteArray, 2, 6);
        assertThat(params.getByteArray(KEY)).isEqualTo(expected);
    }

    @Test
    public void constructorWithByteArrays_withTwoInvalidElements() {
        final int length = 4;
        byte[] byteArray = new byte[] {KEY, length, 0x12, 0x34, 0x56, 0x78,
                0x66, 0x77}; // Last two are invalid. It will be filtered out.

        ObexAppParameters params = new ObexAppParameters(byteArray);
        assertThat(params.exists(KEY)).isTrue();

        byte[] expected = Arrays.copyOfRange(byteArray, 2, 6);
        assertThat(params.getByteArray(KEY)).isEqualTo(expected);
    }

    @Test
    public void fromHeaderSet() {
        final int length = 4;
        byte[] byteArray = new byte[] {KEY, length, 0x12, 0x34, 0x56, 0x78};

        HeaderSet headerSet = new HeaderSet();
        headerSet.setHeader(HeaderSet.APPLICATION_PARAMETER, byteArray);

        ObexAppParameters params = ObexAppParameters.fromHeaderSet(headerSet);
        assertThat(params).isNotNull();

        byte[] expected = Arrays.copyOfRange(byteArray, 2, 6);
        assertThat(params.getByteArray(KEY)).isEqualTo(expected);
    }

    @Test
    public void addToHeaderSet() throws Exception {
        final int length = 4;
        byte[] byteArray = new byte[] {KEY, length, 0x12, 0x34, 0x56, 0x78};

        HeaderSet headerSet = new HeaderSet();
        ObexAppParameters params = new ObexAppParameters(byteArray);
        params.addToHeaderSet(headerSet);

        assertThat(byteArray).isEqualTo(headerSet.getHeader(HeaderSet.APPLICATION_PARAMETER));
    }

    @Test
    public void add_byte() {
        ObexAppParameters params = new ObexAppParameters();
        final byte value = 0x34;
        params.add(KEY, value);

        assertThat(params.getByte(KEY)).isEqualTo(value);
    }

    @Test
    public void add_short() {
        ObexAppParameters params = new ObexAppParameters();
        final short value = 0x99; // More than max byte value
        params.add(KEY, value);

        assertThat(params.getShort(KEY)).isEqualTo(value);
    }

    @Test
    public void add_int() {
        ObexAppParameters params = new ObexAppParameters();
        final int value = 12345678; // More than max short value
        params.add(KEY, value);

        assertThat(params.getInt(KEY)).isEqualTo(value);
    }

    @Test
    public void add_long() {
        ObexAppParameters params = new ObexAppParameters();
        final long value = 1234567890123456L; // More than max integer value
        params.add(KEY, value);

        // Note: getLong() does not exist
        byte[] byteArray = params.getByteArray(KEY);
        assertThat(ByteBuffer.wrap(byteArray).getLong()).isEqualTo(value);
    }

    @Test
    public void add_string() {
        ObexAppParameters params = new ObexAppParameters();
        final String value = "Some string value";
        params.add(KEY, value);

        assertThat(params.getString(KEY)).isEqualTo(value);
    }

    @Test
    public void add_byteArray() {
        ObexAppParameters params = new ObexAppParameters();
        final byte[] value = new byte[] {0x00, 0x01, 0x02, 0x03};
        params.add(KEY, value);

        assertThat(params.getByteArray(KEY)).isEqualTo(value);
    }

    @Test
    public void get_errorCases() {
        ObexAppParameters emptyParams = new ObexAppParameters();

        assertThat(emptyParams.getByte(KEY)).isEqualTo(0);
        assertThat(emptyParams.getShort(KEY)).isEqualTo(0);
        assertThat(emptyParams.getInt(KEY)).isEqualTo(0);
        // Note: getLong() does not exist
        assertThat(emptyParams.getString(KEY)).isNull();
        assertThat(emptyParams.getByteArray(KEY)).isNull();
    }

    @Test
    public void toString_isNotNull() {
        ObexAppParameters params = new ObexAppParameters();
        assertThat(params.toString()).isNotNull();
    }

    @Test
    public void getHeader_withTwoEntries() {
        ObexAppParameters params = new ObexAppParameters();

        final byte key1 = 0x01;
        final int value1 = 12345;
        params.add(key1, value1);

        final byte key2 = 0x02;
        final int value2 = 56789;
        params.add(key2, value2);

        ByteBuffer result = ByteBuffer.wrap(params.getHeader());
        final byte firstKey = result.get();

        final int sizeOfInt = 4;
        if (firstKey == key1) {
            assertThat(result.get()).isEqualTo(sizeOfInt);
            assertThat(result.getInt()).isEqualTo(value1);

            assertThat(result.get()).isEqualTo(key2);
            assertThat(result.get()).isEqualTo(sizeOfInt);
            assertThat(result.getInt()).isEqualTo(value2);
        } else if (firstKey == key2) {
            assertThat(result.get()).isEqualTo(sizeOfInt);
            assertThat(result.getInt()).isEqualTo(value2);

            assertThat(result.get()).isEqualTo(key1);
            assertThat(result.get()).isEqualTo(sizeOfInt);
            assertThat(result.getInt()).isEqualTo(value1);
        } else {
            assertWithMessage("Key should be one of two keys").fail();
        }
    }
}
