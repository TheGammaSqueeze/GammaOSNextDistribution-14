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

import static com.android.adservices.ohttp.ObliviousHttpTestFixtures.SERVER_PRIVATE_KEY;
import static com.android.adservices.ohttp.ObliviousHttpTestFixtures.getTestVectors;

import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;

import com.google.common.io.BaseEncoding;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

public class ObliviousHttpGatewayTest {

    @Test
    public void decrypt_canDecryptPayloadsEncryptedByOhttpClient()
            throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException, IOException {
        List<ObliviousHttpTestFixtures.OhttpTestVector> testVectors = getTestVectors();
        for (ObliviousHttpTestFixtures.OhttpTestVector testVector : testVectors) {
            ObliviousHttpClient client = ObliviousHttpClient.create(testVector.keyConfig);
            String plainText = testVector.plainText;
            byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);
            byte[] seedBytes = testVector.seed.getBytes(StandardCharsets.US_ASCII);

            ObliviousHttpRequest request =
                    client.createObliviousHttpRequest(plainTextBytes, seedBytes);

            byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PRIVATE_KEY);
            byte[] decrypted =
                    ObliviousHttpGateway.decrypt(
                            OhttpGatewayPrivateKey.create(keyBytes), request.serialize());

            Assert.assertEquals(
                    new String(decrypted, StandardCharsets.UTF_8), testVector.plainText);
        }
    }

    @Test
    public void encrypt_canBeDecryptedByOhttpClient() throws Exception {
        List<ObliviousHttpTestFixtures.OhttpTestVector> testVectors = getTestVectors();
        for (ObliviousHttpTestFixtures.OhttpTestVector testVector : testVectors) {
            ObliviousHttpClient client = ObliviousHttpClient.create(testVector.keyConfig);
            String plainText = testVector.plainText;
            byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);
            byte[] seedBytes = testVector.seed.getBytes(StandardCharsets.US_ASCII);

            ObliviousHttpRequest request =
                    client.createObliviousHttpRequest(plainTextBytes, seedBytes);

            byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PRIVATE_KEY);
            byte[] serverEncrypted =
                    ObliviousHttpGateway.encrypt(
                            OhttpGatewayPrivateKey.create(keyBytes),
                            request.serialize(),
                            testVector.responsePlainText.getBytes(StandardCharsets.US_ASCII));

            byte[] clientDecrypted =
                    client.decryptObliviousHttpResponse(serverEncrypted, request.requestContext());

            Assert.assertEquals(
                    new String(clientDecrypted, StandardCharsets.UTF_8),
                    testVector.responsePlainText);
        }
    }

    @Test
    public void encrypt_missingInfo_throwsError() throws Exception {
        ObliviousHttpTestFixtures.OhttpTestVector testVector = getTestVectors().get(0);
        ObliviousHttpClient client = ObliviousHttpClient.create(testVector.keyConfig);
        String plainText = testVector.plainText;
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);
        byte[] seedBytes = testVector.seed.getBytes(StandardCharsets.US_ASCII);

        ObliviousHttpRequest request = client.createObliviousHttpRequest(plainTextBytes, seedBytes);

        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        ObliviousHttpGateway.encrypt(
                                OhttpGatewayPrivateKey.create(null),
                                request.serialize(),
                                testVector.responsePlainText.getBytes(StandardCharsets.US_ASCII)));
    }
}
