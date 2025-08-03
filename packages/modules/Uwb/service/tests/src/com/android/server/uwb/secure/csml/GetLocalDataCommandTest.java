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

package com.android.server.uwb.secure.csml;

import static com.google.common.truth.Truth.assertThat;

import com.android.server.uwb.util.DataTypeConversionUtil;

import org.junit.Test;

public class GetLocalDataCommandTest {
    @Test
    public void encodeGetLocalDataCommand() {
        byte p1 = (byte) 0x0A;
        byte p2 = (byte) 0x0B;

        // <code>cla | ins | p1 | p2 | lc | data | le</code>
        byte[] expectedApdu = DataTypeConversionUtil.hexStringToByteArray(
                "80CA0A0B00");
        byte[] actualApdu = GetLocalDataCommand.build(p1, p2)
                .getCommandApdu().getEncoded();

        assertThat(actualApdu).isEqualTo(expectedApdu);
    }

    @Test
    public void getPaList() {
        byte[] expectedApdu = DataTypeConversionUtil.hexStringToByteArray(
                "80CA00B000");
        byte[] actualApdu = GetLocalDataCommand.getPaListCommand()
                .getCommandApdu().getEncoded();

        assertThat(actualApdu).isEqualTo(expectedApdu);
    }

    @Test
    public void getFiraAppletCertificates() {
        byte[] expectedApdu = DataTypeConversionUtil.hexStringToByteArray(
                "80CABF2100");
        byte[] actualApdu = GetLocalDataCommand.getFiRaAppletCertificatesCommand()
                .getCommandApdu().getEncoded();

        assertThat(actualApdu).isEqualTo(expectedApdu);
    }
}
