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

package com.android.server.uwb.secure.provisioning;

import static com.google.common.truth.Truth.assertThat;

import com.android.server.uwb.util.DataTypeConversionUtil;

import org.junit.Test;

public class ScriptParserTest {

    @Test
    public void parseCorrect() throws ProvisioningException {
        // 0x0101, [h'010203',h'0a0b0c'], h'01' - (version, apdus, adf oid)
        byte[] cborEncodedData = DataTypeConversionUtil.hexStringToByteArray(
                "1901018243010203430a0b0c4101");

        ScriptParser.ScriptContent scriptContent =
                ScriptParser.parseScript(cborEncodedData);

        assertThat(scriptContent.mMajorVersion).isEqualTo(1);
        assertThat(scriptContent.mMinorVersion).isEqualTo(1);
        assertThat(scriptContent.mAdfOid.get().value).isEqualTo(new byte[] {(byte) 1});
                // ObjectIdentifier.fromBytes(new byte[] {(byte) 1}));
        assertThat(scriptContent.mProvisioningApdus.get(0))
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("010203"));
        assertThat(scriptContent.mProvisioningApdus.get(1))
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("0a0b0c"));
    }
    @Test
    public void parseCorrectWithoutAdfOid() throws ProvisioningException {
        // 0x0101, [h'010203', h'0a0b0c'] - (version, apdus)
        byte[] cborEncodedData = DataTypeConversionUtil.hexStringToByteArray(
                "1901018243010203430a0b0c");

        ScriptParser.ScriptContent scriptContent =
                ScriptParser.parseScript(cborEncodedData);

        assertThat(scriptContent.mMajorVersion).isEqualTo(1);
        assertThat(scriptContent.mMinorVersion).isEqualTo(1);
        assertThat(scriptContent.mAdfOid.isEmpty()).isTrue();
        assertThat(scriptContent.mProvisioningApdus.get(0))
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("010203"));
        assertThat(scriptContent.mProvisioningApdus.get(1))
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("0a0b0c"));
    }

    @Test(expected = ProvisioningException.class)
    public void scriptWithMoreDataItems() throws ProvisioningException {
        // 0x0101, [h'010203',h'0a0b0c'], h'01', 1 - (version, apdus, adf oid, placeholder)
        byte[] cborEncodedData = DataTypeConversionUtil.hexStringToByteArray(
                "1901018243010203430a0b0c410101");

        ScriptParser.parseScript(cborEncodedData);
    }

    @Test(expected = ProvisioningException.class)
    public void scriptWithLessDataItems() throws ProvisioningException {
        // 0x0101 - (version)
        byte[] cborEncodedData = DataTypeConversionUtil.hexStringToByteArray(
                "190101");

        ScriptParser.parseScript(cborEncodedData);
    }

    @Test(expected = ProvisioningException.class)
    public void scriptWithWrongDataType() throws ProvisioningException {
        // 0x0101, [h'010203', h'0a0b0c'], 1 - (version, apdus, adf oid(wrong data type)
        byte[] cborEncodedData = DataTypeConversionUtil.hexStringToByteArray(
                "1901018243010203430a0b0c01");

        ScriptParser.parseScript(cborEncodedData);
    }
}
