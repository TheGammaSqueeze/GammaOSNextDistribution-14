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
package tests.targets.security;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Enumeration;
import javax.crypto.Cipher;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests the ability of PKCS#12 KeyStores to read PKCS#7 private key entries
 * shrouded with different encryption schemes.
 *
 * KeyStores to test and instructions for regenerating them are in
 * libcore/luni/src/test/resources/keystore.
 *
 * Each KeyStore to be tested contains the same certificate and private key
 * pair and has the password "password".
 */
@RunWith(Enclosed.class)
public class KeyStorePkcs7FormatTest {
    private static final char[] PASSWORD = "password".toCharArray();
    private static final String UNKNOWN_HASH = "/keystore/sha3.p12";

    @RunWith(Parameterized.class)
    public static class ParameterizedTests {
        @Parameters(name = "{0}")
        public static Iterable<?> data() {
            return Arrays.asList(
                "/keystore/pbe-rc2.p12",
                "/keystore/pbe-3des-sha1.p12",
                "/keystore/pbe-3des-sha224.p12",
                "/keystore/pbe-3des-sha256.p12",
                "/keystore/pbe-3des-sha384.p12",
                "/keystore/pbe-3des-sha512.p12",
                "/keystore/pbes2-aes-128-aes-128-sha1.p12",
                "/keystore/pbes2-aes-128-aes-128-sha224.p12",
                "/keystore/pbes2-aes-128-aes-128-sha256.p12",
                "/keystore/pbes2-aes-128-aes-128-sha384.p12",
                "/keystore/pbes2-aes-128-aes-128-sha512.p12",
                "/keystore/pbes2-aes-128-aes-192-sha1.p12",
                "/keystore/pbes2-aes-128-aes-192-sha224.p12",
                "/keystore/pbes2-aes-128-aes-192-sha256.p12",
                "/keystore/pbes2-aes-128-aes-192-sha384.p12",
                "/keystore/pbes2-aes-128-aes-192-sha512.p12",
                "/keystore/pbes2-aes-128-aes-256-sha1.p12",
                "/keystore/pbes2-aes-128-aes-256-sha224.p12",
                "/keystore/pbes2-aes-128-aes-256-sha256.p12",
                "/keystore/pbes2-aes-128-aes-256-sha384.p12",
                "/keystore/pbes2-aes-128-aes-256-sha512.p12",
                "/keystore/pbes2-aes-192-aes-128-sha1.p12",
                "/keystore/pbes2-aes-192-aes-128-sha224.p12",
                "/keystore/pbes2-aes-192-aes-128-sha256.p12",
                "/keystore/pbes2-aes-192-aes-128-sha384.p12",
                "/keystore/pbes2-aes-192-aes-128-sha512.p12",
                "/keystore/pbes2-aes-192-aes-192-sha1.p12",
                "/keystore/pbes2-aes-192-aes-192-sha224.p12",
                "/keystore/pbes2-aes-192-aes-192-sha256.p12",
                "/keystore/pbes2-aes-192-aes-192-sha384.p12",
                "/keystore/pbes2-aes-192-aes-192-sha512.p12",
                "/keystore/pbes2-aes-192-aes-256-sha1.p12",
                "/keystore/pbes2-aes-192-aes-256-sha224.p12",
                "/keystore/pbes2-aes-192-aes-256-sha256.p12",
                "/keystore/pbes2-aes-192-aes-256-sha384.p12",
                "/keystore/pbes2-aes-192-aes-256-sha512.p12",
                "/keystore/pbes2-aes-256-aes-128-sha1.p12",
                "/keystore/pbes2-aes-256-aes-128-sha224.p12",
                "/keystore/pbes2-aes-256-aes-128-sha256.p12",
                "/keystore/pbes2-aes-256-aes-128-sha384.p12",
                "/keystore/pbes2-aes-256-aes-128-sha512.p12",
                "/keystore/pbes2-aes-256-aes-192-sha1.p12",
                "/keystore/pbes2-aes-256-aes-192-sha224.p12",
                "/keystore/pbes2-aes-256-aes-192-sha256.p12",
                "/keystore/pbes2-aes-256-aes-192-sha384.p12",
                "/keystore/pbes2-aes-256-aes-192-sha512.p12",
                "/keystore/pbes2-aes-256-aes-256-sha1.p12",
                "/keystore/pbes2-aes-256-aes-256-sha224.p12",
                "/keystore/pbes2-aes-256-aes-256-sha256.p12",
                "/keystore/pbes2-aes-256-aes-256-sha384.p12",
                "/keystore/pbes2-aes-256-aes-256-sha512.p12"
            );
        }

        @Parameter
        public String keystoreFile;

        @Test
        public void keystoreIsReadableAndConsistent() throws Exception {
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            InputStream inputStream = getClass().getResourceAsStream(keystoreFile);
            assertNotNull("Resource not found: " + keystoreFile, inputStream);
            keystore.load(inputStream, PASSWORD);

            Enumeration<String> aliases = keystore.aliases();
            assertTrue("Empty KeyStore", aliases.hasMoreElements());

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();

                if (!keystore.isKeyEntry(alias)) {
                    fail("Test KeyStore should only contain private key entries");
                }

                PrivateKeyEntry keyEntry = (PrivateKeyEntry) keystore.getEntry(alias, null);
                X509Certificate certificate = (X509Certificate) keyEntry.getCertificate();
                assertEquals("CN=Test", certificate.getSubjectX500Principal().getName());

                // Check the keys actually work with each other.
                RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();
                RSAPrivateKey privateKey = (RSAPrivateKey) keyEntry.getPrivateKey();
                byte[] original = "Some random input text".getBytes();

                Cipher cipher = Cipher.getInstance("RSA");
                cipher.init(Cipher.ENCRYPT_MODE, privateKey);
                byte[] encrypted = cipher.doFinal(original);

                cipher.init(Cipher.DECRYPT_MODE, publicKey);
                byte[] decrypted = cipher.doFinal(encrypted);

                assertArrayEquals(original, decrypted);
            }
        }
    }

    @RunWith(JUnit4.class)
    public static class OtherTests {
        @Test
        public void unknownHashThrowsCorrectException() throws Exception {
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            InputStream inputStream = getClass().getResourceAsStream(UNKNOWN_HASH);
            assertNotNull("Resource not found: " + UNKNOWN_HASH, inputStream);

            try {
                keystore.load(inputStream, PASSWORD);
                fail();
            } catch (IOException expected) {
                assertFalse("Internal BCPrivate exception thrown",
                    expected.getMessage().contains("BCPrivate"));
            }
        }
    }
}
