/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.secure.iso7816;

import static com.google.common.truth.Truth.assertThat;

import  com.android.server.uwb.secure.iso7816.TlvDatum.Tag;
import com.android.server.uwb.util.DataTypeConversionUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;

/** Unit tests for {@link TlvDatum} */
public class TlvDatumTest {

    @Test
    public void testTlvDatumToBytes() {
        Tag childTag = new Tag((byte) 0x84);
        Tag parentTag = new Tag((byte) 0x48);
        TlvDatum tlvDatumChild =
                new TlvDatum(
                        childTag,
                        DataTypeConversionUtil.hexStringToByteArray("A0000000041010"));

        byte[] actualChild = tlvDatumChild.toBytes();
        byte[] expectedChild = DataTypeConversionUtil.hexStringToByteArray("8407A0000000041010");

        assertThat(actualChild).isEqualTo(expectedChild);

        TlvDatum tlvDatumParent1 =
                new TlvDatum(
                        parentTag,
                        tlvDatumChild);
        byte[] actualParent1 = tlvDatumParent1.toBytes();
        byte[] expectedParent = DataTypeConversionUtil.hexStringToByteArray(
                "48098407A0000000041010");

        assertThat(actualParent1).isEqualTo(expectedParent);

        byte[] actualParent2 = new TlvDatum(
                parentTag, ImmutableMap.of(childTag, ImmutableList.of(tlvDatumChild))).toBytes();

        assertThat(actualParent2).isEqualTo(expectedParent);
    }

    @Test
    public void testIntTlvDatum() {
        int v = 0x01020304;
        TlvDatum tlvDatum = new TlvDatum(new Tag((byte) 0x84), v);

        byte[] actual = tlvDatum.toBytes();
        byte[] expected = DataTypeConversionUtil.hexStringToByteArray("840401020304");

        assertThat(actual).isEqualTo(expected);
    }
}
