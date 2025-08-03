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

import static org.junit.Assert.assertTrue;

import com.android.server.uwb.secure.iso7816.TlvDatum.Tag;
import com.android.server.uwb.util.DataTypeConversionUtil;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Unit tests for {@link TlvParser} */
public class TlvParserTest {

    @Test
    public void testParseComplicatedTlv() {
        ResponseApdu responseApdu =
                ResponseApdu.fromResponse(
                        DataTypeConversionUtil.hexStringToByteArray(
                                "6F1AA50F870101500A4D6173746572436172648407A00000000410109000"));
        TlvDatum actual = TlvParser.parseTlvs(responseApdu).get(new Tag((byte) 0x6F)).get(0);

        TlvDatum expected = new TlvDatum(new Tag((byte) 0x6F),
                DataTypeConversionUtil.hexStringToByteArray(
                        "A50F870101500A4D6173746572436172648407A0000000041010"));

        assertThat(actual.toBytes()).isEqualTo(expected.toBytes());
        assertTlvDatumEquals(expected, actual);
    }

    @Test
    public void testParseSelectResponse() {
        ResponseApdu responseApdu =
                ResponseApdu.fromResponse(
                        DataTypeConversionUtil.hexStringToByteArray("5A0201005C020100D401009000"));

        TlvDatum subTlvDatum1 =
                new TlvDatum(new Tag((byte) 0x5A),
                        DataTypeConversionUtil.hexStringToByteArray("0100"));
        TlvDatum subTlvDatum2 =
                new TlvDatum(new Tag((byte) 0x5C),
                        DataTypeConversionUtil.hexStringToByteArray("0100"));
        TlvDatum subTlvDatum3 =
                new TlvDatum(new Tag((byte) 0xD4),
                        DataTypeConversionUtil.hexStringToByteArray("00"));
        Map<Tag, List<TlvDatum>> result = TlvParser.parseTlvs(responseApdu);
        List<TlvDatum> actual = Arrays.asList(
                result.get(new Tag((byte) 0x5A)).get(0),
                result.get(new Tag((byte) 0x5C)).get(0),
                result.get(new Tag((byte) 0xD4)).get(0));
        List<TlvDatum> expected = Arrays.asList(subTlvDatum1, subTlvDatum2, subTlvDatum3);

        assertTlvDatumListEquals(expected, actual);
    }

    @Test
    public void invalidInput_singleZero_failure() {
        assertThat(TlvParser.parseTlvs(new byte[1])).isEmpty();
    }

    @Test
    public void invalidInput_truncatedLength_failure() {
        assertThat(TlvParser.parseTlvs(new byte[] {0x00, (byte) 0b10000001})).isEmpty();
    }

    @Test
    public void noDataTag_success() {
        Map<Tag, List<TlvDatum>> result = TlvParser.parseTlvs(new byte[] {0x5f, 0x5f, 0x00});
        List<TlvDatum> actual = result.get(new Tag((byte) 0x5f, (byte) 0x5f));
        assertThat(actual).isNotNull();
        assertTlvDatumListEquals(
                actual, ImmutableList.of(
                        new TlvDatum(new Tag((byte) 0x5f, (byte) 0x5f), new byte[] {})));
    }

    private static void assertTlvDatumListEquals(List<TlvDatum> expected, List<TlvDatum> actual) {
        assertThat(actual).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertTlvDatumEquals(expected.get(i), actual.get(i));
        }
    }

    private static void assertTlvDatumEquals(TlvDatum expected, TlvDatum actual) {
        assertTrue(equals(expected, actual));
    }

    /** Determine if two TlvDatums are equal. */
    private static boolean equals(TlvDatum tlv1, TlvDatum tlv2) {
        for (Map.Entry<Tag, List<TlvDatum>> tlv1SubEntry : tlv1.subTlvData.entrySet()) {
            List<TlvDatum> tlv2SubList = tlv2.subTlvData.get(tlv1SubEntry.getKey());
            assertTlvDatumListEquals(tlv1SubEntry.getValue(), tlv2SubList);
            if (!Objects.equals(tlv1.tag, tlv2.tag) || !Arrays.equals(tlv1.value, tlv2.value)) {
                return false;
            }
        }
        return true;
    }
}
