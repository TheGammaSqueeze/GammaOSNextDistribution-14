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

package com.android.bluetooth.util;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.InstrumentationRegistry;

import com.android.internal.telephony.uicc.IccUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GsmAlphabetTest {

  private static final String GSM_EXTENDED_CHARS = "{|}\\[~]\f\u20ac";

  @Before
  public void setUp() throws Exception {
    InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
  }

  @Test
  public void gsm7BitPackedToString() throws Exception {
    byte[] packed;
    StringBuilder testString = new StringBuilder(300);

    packed = com.android.internal.telephony.GsmAlphabet.stringToGsm7BitPacked(
            testString.toString());
    assertThat(GsmAlphabet.gsm7BitPackedToString(packed, 1, 0xff & packed[0], 0, 0, 0))
            .isEqualTo(testString.toString());

    // Check all alignment cases
    for (int i = 0; i < 9; i++, testString.append('@')) {
      packed = com.android.internal.telephony.GsmAlphabet.stringToGsm7BitPacked(
              testString.toString());
      assertThat(GsmAlphabet.gsm7BitPackedToString(packed, 1, 0xff & packed[0], 0, 0, 0))
              .isEqualTo(testString.toString());
    }

    // Test extended chars too
    testString.append(GSM_EXTENDED_CHARS);
    packed = com.android.internal.telephony.GsmAlphabet.stringToGsm7BitPacked(
            testString.toString());
    assertThat(GsmAlphabet.gsm7BitPackedToString(packed, 1, 0xff & packed[0], 0, 0, 0))
            .isEqualTo(testString.toString());

    // Try 254 septets with 127 extended chars
    testString.setLength(0);
    for (int i = 0; i < (255 / 2); i++) {
      testString.append('{');
    }
    packed = com.android.internal.telephony.GsmAlphabet.stringToGsm7BitPacked(
            testString.toString());
    assertThat(GsmAlphabet.gsm7BitPackedToString(packed, 1, 0xff & packed[0], 0, 0, 0))
            .isEqualTo(testString.toString());

    // Reserved for extension to extension table (mapped to space)
    packed = new byte[]{(byte)(0x1b | 0x80), 0x1b >> 1};
    assertThat(GsmAlphabet.gsm7BitPackedToString(packed, 0, 2, 0, 0, 0)).isEqualTo(" ");

    // Unmappable (mapped to character in default alphabet table)
    packed[0] = 0x1b;
    packed[1] = 0x00;
    assertThat(GsmAlphabet.gsm7BitPackedToString(packed, 0, 2, 0, 0, 0)).isEqualTo("@");
    packed[0] = (byte)(0x1b | 0x80);
    packed[1] = (byte)(0x7f >> 1);
    assertThat(GsmAlphabet.gsm7BitPackedToString(packed, 0, 2, 0, 0, 0)).isEqualTo("\u00e0");
  }

  @Test
  public void stringToGsm8BitPacked() throws Exception {
    byte unpacked[];
    unpacked = IccUtils.hexStringToBytes("566F696365204D61696C");
    assertThat(IccUtils.bytesToHexString(GsmAlphabet.stringToGsm8BitPacked("Voice Mail")))
            .isEqualTo(IccUtils.bytesToHexString(unpacked));

    unpacked = GsmAlphabet.stringToGsm8BitPacked(GSM_EXTENDED_CHARS);
    // two bytes for every extended char
    assertThat(unpacked.length).isEqualTo(2 * GSM_EXTENDED_CHARS.length());
  }

  @Test
  public void stringToGsm8BitUnpackedField() throws Exception {
    byte unpacked[];
    // Test truncation of unaligned extended chars
    unpacked = new byte[3];
    GsmAlphabet.stringToGsm8BitUnpackedField(GSM_EXTENDED_CHARS, unpacked,
            0, unpacked.length);

    // Should be one extended char and an 0xff at the end
    assertThat(0xff & unpacked[2]).isEqualTo(0xff);
    assertThat(com.android.internal.telephony.GsmAlphabet.gsm8BitUnpackedToString(
            unpacked, 0, unpacked.length)).isEqualTo(GSM_EXTENDED_CHARS.substring(0, 1));

    // Test truncation of normal chars
    unpacked = new byte[3];
    GsmAlphabet.stringToGsm8BitUnpackedField("abcd", unpacked,
            0, unpacked.length);

    assertThat(com.android.internal.telephony.GsmAlphabet.gsm8BitUnpackedToString(
            unpacked, 0, unpacked.length)).isEqualTo("abc");

    // Test truncation of mixed normal and extended chars
    unpacked = new byte[3];
    GsmAlphabet.stringToGsm8BitUnpackedField("a{cd", unpacked,
            0, unpacked.length);

    assertThat(com.android.internal.telephony.GsmAlphabet.gsm8BitUnpackedToString(
            unpacked, 0, unpacked.length)).isEqualTo("a{");

    // Test padding after normal char
    unpacked = new byte[3];
    GsmAlphabet.stringToGsm8BitUnpackedField("a", unpacked,
            0, unpacked.length);

    assertThat(com.android.internal.telephony.GsmAlphabet.gsm8BitUnpackedToString(
            unpacked, 0, unpacked.length)).isEqualTo("a");

    assertThat(0xff & unpacked[1]).isEqualTo(0xff);
    assertThat(0xff & unpacked[2]).isEqualTo(0xff);

    // Test malformed input -- escape char followed by end of field
    unpacked[0] = 0;
    unpacked[1] = 0;
    unpacked[2] = GsmAlphabet.GSM_EXTENDED_ESCAPE;

    assertThat(com.android.internal.telephony.GsmAlphabet.gsm8BitUnpackedToString(
            unpacked, 0, unpacked.length)).isEqualTo("@@");

    // non-zero offset
    assertThat(com.android.internal.telephony.GsmAlphabet.gsm8BitUnpackedToString(
            unpacked, 1, unpacked.length - 1)).isEqualTo("@");

    // test non-zero offset
    unpacked[0] = 0;
    GsmAlphabet.stringToGsm8BitUnpackedField("abcd", unpacked,
            1, unpacked.length - 1);


    assertThat(unpacked[0]).isEqualTo(0);

    assertThat(com.android.internal.telephony.GsmAlphabet.gsm8BitUnpackedToString(
            unpacked, 1, unpacked.length - 1)).isEqualTo("ab");

    // test non-zero offset with truncated extended char
    unpacked[0] = 0;

    GsmAlphabet.stringToGsm8BitUnpackedField("a{", unpacked,
            1, unpacked.length - 1);

    assertThat(unpacked[0]).isEqualTo(0);

    assertThat(com.android.internal.telephony.GsmAlphabet.gsm8BitUnpackedToString(
            unpacked, 1, unpacked.length - 1)).isEqualTo("a");

    // Reserved for extension to extension table (mapped to space)
    unpacked[0] = 0x1b;
    unpacked[1] = 0x1b;
    assertThat(com.android.internal.telephony.GsmAlphabet.gsm8BitUnpackedToString(
            unpacked, 0, 2)).isEqualTo(" ");

    // Unmappable (mapped to character in default or national locking shift table)
    unpacked[1] = 0x00;
    assertThat(com.android.internal.telephony.GsmAlphabet.gsm8BitUnpackedToString(
            unpacked, 0, 2)).isEqualTo("@");
    unpacked[1] = 0x7f;
    assertThat(com.android.internal.telephony.GsmAlphabet.gsm8BitUnpackedToString(
            unpacked, 0, 2)).isEqualTo("\u00e0");
  }
}
