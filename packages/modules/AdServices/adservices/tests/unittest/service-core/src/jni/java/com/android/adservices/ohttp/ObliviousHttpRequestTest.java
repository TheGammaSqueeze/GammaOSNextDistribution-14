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

package com.android.adservices.ohttp;

import com.google.common.io.BaseEncoding;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;

public class ObliviousHttpRequestTest {

    @Test
    public void serialize_serializesCorrectly() throws InvalidKeySpecException, IOException {
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "00080001000100010003";
        byte[] keyConfigBytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);
        ObliviousHttpKeyConfig keyConfig =
                ObliviousHttpKeyConfig.fromSerializedKeyConfig(keyConfigBytes);
        String encString = "1cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d0b18cb9a67";
        EncapsulatedSharedSecret encapsulatedSharedSecret =
                EncapsulatedSharedSecret.create(
                        BaseEncoding.base16().lowerCase().decode(encString));
        ObliviousHttpRequestContext requestContext =
                ObliviousHttpRequestContext.create(
                        keyConfig, encapsulatedSharedSecret, new byte[1]);
        String plainText = "something";
        String cipherText = "not actual cipher";
        byte[] cipherTextBytes = cipherText.getBytes(StandardCharsets.US_ASCII);

        ObliviousHttpRequest request =
                ObliviousHttpRequest.create(
                        plainText.getBytes(StandardCharsets.US_ASCII),
                        cipherTextBytes,
                        requestContext);

        String expectedHeader = "01002000010001";
        String expectedCipherTextHexString =
                BaseEncoding.base16().lowerCase().encode(cipherTextBytes);
        Assert.assertEquals(
                BaseEncoding.base16().lowerCase().encode(request.serialize()),
                expectedHeader + encString + expectedCipherTextHexString);
    }
}
