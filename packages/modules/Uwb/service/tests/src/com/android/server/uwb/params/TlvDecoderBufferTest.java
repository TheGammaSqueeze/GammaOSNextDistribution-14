/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb.params;

import static com.android.server.uwb.params.TlvDecoderBuffer.Tlv;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.util.UwbUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link com.android.server.uwb.params.TlvDecoderBuffer}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class TlvDecoderBufferTest {
    private static final byte[] TEST_TLV_DATA =
            UwbUtil.getByteArray("0001010101020301000401050501010602040007020800080260090904C"
                    + "80000000C01010D01011101001201031401091501021B041E000000270208072806010"
                    + "2030405062B04640000002C01002D01002E010F32020000");
    private static final List<Tlv> TEST_TLVS = Arrays.asList(
            new Tlv((byte) 0, (byte) 1, UwbUtil.getByteArray("01")),
            new Tlv((byte) 1, (byte) 1, UwbUtil.getByteArray("02")),
            new Tlv((byte) 3, (byte) 1, UwbUtil.getByteArray("00")),
            new Tlv((byte) 4, (byte) 1, UwbUtil.getByteArray("05")),
            new Tlv((byte) 5, (byte) 1, UwbUtil.getByteArray("01")),
            new Tlv((byte) 6, (byte) 2, UwbUtil.getByteArray("0400")),
            new Tlv((byte) 7, (byte) 2, UwbUtil.getByteArray("0800")),
            new Tlv((byte) 8, (byte) 2, UwbUtil.getByteArray("6009")),
            new Tlv((byte) 9, (byte) 4, UwbUtil.getByteArray("C8000000")),
            new Tlv((byte) 12, (byte) 1, UwbUtil.getByteArray("01")),
            new Tlv((byte) 13, (byte) 1, UwbUtil.getByteArray("01")),
            new Tlv((byte) 17, (byte) 1, UwbUtil.getByteArray("00")),
            new Tlv((byte) 18, (byte) 1, UwbUtil.getByteArray("03")),
            new Tlv((byte) 20, (byte) 1, UwbUtil.getByteArray("09")),
            new Tlv((byte) 21, (byte) 1, UwbUtil.getByteArray("02")),
            new Tlv((byte) 27, (byte) 4, UwbUtil.getByteArray("1E000000")),
            new Tlv((byte) 39, (byte) 2, UwbUtil.getByteArray("0807")),
            new Tlv((byte) 40, (byte) 6, UwbUtil.getByteArray("010203040506")),
            new Tlv((byte) 43, (byte) 4, UwbUtil.getByteArray("64000000")),
            new Tlv((byte) 44, (byte) 1, UwbUtil.getByteArray("00")),
            new Tlv((byte) 45, (byte) 1, UwbUtil.getByteArray("00")),
            new Tlv((byte) 46, (byte) 1, UwbUtil.getByteArray("0F")),
            new Tlv((byte) 50, (byte) 2, UwbUtil.getByteArray("0000")));
    private static final int TEST_TLV_NUM_PARAMS = TEST_TLVS.size();

    @Test
    public void testTlvParse() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(TEST_TLV_DATA, TEST_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        Collection<Tlv> tlvsParsedList = tlvDecoderBuffer.getTlvs();
        Set<Tlv> tlvsExpected = Set.copyOf(TEST_TLVS);
        Set<Tlv> tlvsParsed = Set.copyOf(tlvsParsedList);
        assertThat(tlvsExpected).isEqualTo(tlvsParsed);
    }

    @Test
    public void testGetters() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(TEST_TLV_DATA, TEST_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        assertThat(tlvDecoderBuffer.getByte(1)).isEqualTo(0x2);
        assertThat(tlvDecoderBuffer.getShort(8)).isEqualTo(0x0960);
        assertThat(tlvDecoderBuffer.getInt(9)).isEqualTo(0x000000C8);
        assertThat(tlvDecoderBuffer.getByteArray(40)).isEqualTo(UwbUtil.getByteArray(
                "010203040506"));
    }
}
