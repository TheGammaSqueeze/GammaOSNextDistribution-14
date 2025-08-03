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

import org.junit.Assert;
import org.junit.Test;

public class KemAlgorithmSpecTest {

    @Test
    public void get_unsupportedId_throwsError() {
        Assert.assertThrows(UnsupportedHpkeAlgorithmException.class, () -> KemAlgorithmSpec.get(1));
    }

    @Test
    public void get_supportedId_returnsCorrectSpec() throws UnsupportedHpkeAlgorithmException {
        KemAlgorithmSpec kemAlgorithmSpec =
                KemAlgorithmSpec.get(KemAlgorithmSpec.DHKEM_X25519_HKDF_SHA256_IDENTIFIER);

        Assert.assertEquals(kemAlgorithmSpec.encapsulatedKeyLength(), 32);
        Assert.assertEquals(kemAlgorithmSpec.privateKeyLength(), 32);
        Assert.assertEquals(kemAlgorithmSpec.publicKeyLength(), 32);
        Assert.assertEquals(kemAlgorithmSpec.secretLength(), 32);
        Assert.assertEquals(
                kemAlgorithmSpec.identifier(),
                KemAlgorithmSpec.DHKEM_X25519_HKDF_SHA256_IDENTIFIER);
        Assert.assertNotNull(kemAlgorithmSpec.kemNativeRefSupplier().get().getAddress());
    }
}
