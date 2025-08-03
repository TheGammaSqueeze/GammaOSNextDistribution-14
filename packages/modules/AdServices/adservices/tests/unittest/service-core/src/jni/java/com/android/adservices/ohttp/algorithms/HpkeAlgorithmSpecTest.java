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

package com.android.adservices.ohttp.algorithms;

import com.android.adservices.ohttp.ObliviousHttpKeyConfig;

import com.google.common.io.BaseEncoding;

import org.junit.Assert;
import org.junit.Test;

import java.security.spec.InvalidKeySpecException;

public class HpkeAlgorithmSpecTest {

    @Test
    public void fromKeyConfig_createsHpkeAlgorithmSpec()
            throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException {
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "00080001000200010003";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);
        ObliviousHttpKeyConfig keyConfig = ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes);

        HpkeAlgorithmSpec hpkeAlgorithmSpec = HpkeAlgorithmSpec.fromKeyConfig(keyConfig);

        Assert.assertEquals(hpkeAlgorithmSpec.kem().identifier(), 0X0020);
        Assert.assertEquals(hpkeAlgorithmSpec.kdf().identifier(), 0X0001);
        Assert.assertEquals(hpkeAlgorithmSpec.aead().identifier(), 0X0002);
    }

    @Test
    public void fromKeyConfig_unsupportedKdf_throwsError() throws InvalidKeySpecException {
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "00080005000100010003";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);
        ObliviousHttpKeyConfig keyConfig = ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes);

        Assert.assertThrows(
                UnsupportedHpkeAlgorithmException.class,
                () -> HpkeAlgorithmSpec.fromKeyConfig(keyConfig));
    }

    @Test
    public void fromKeyConfig_unsupportedAead_throwsError() throws InvalidKeySpecException {
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "00080001000100010003";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);
        ObliviousHttpKeyConfig keyConfig = ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes);

        Assert.assertThrows(
                UnsupportedHpkeAlgorithmException.class,
                () -> HpkeAlgorithmSpec.fromKeyConfig(keyConfig));
    }
}
