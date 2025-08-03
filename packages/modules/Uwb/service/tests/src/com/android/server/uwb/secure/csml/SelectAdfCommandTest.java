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
import com.android.server.uwb.util.ObjectIdentifier;

import org.junit.Test;

public class SelectAdfCommandTest {
    @Test
    public void encodeSelectAdfCommand() {
        ObjectIdentifier oid =
                ObjectIdentifier.fromBytes(DataTypeConversionUtil.hexStringToByteArray("0102"));
        // <code>cla | ins | p1 | p2 | lc | data | le</code>
        byte[] expectedApdu = DataTypeConversionUtil.hexStringToByteArray(
                "80A50400040602010200");
        byte[] actualApdu = SelectAdfCommand.build(oid)
                .getCommandApdu().getEncoded();

        assertThat(actualApdu).isEqualTo(expectedApdu);
    }
}
