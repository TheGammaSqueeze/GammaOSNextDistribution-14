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

package libcore.javax.security.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.crypto.spec.DHParameterSpec;
import javax.security.auth.x500.X500PrivateCredential;

@RunWith(JUnit4.class)
public class X500PrivateCredentialTest {

    @Test
    public void testX500PrivateCredential_nullArgs() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new X500PrivateCredential(null, null));
        PrivateKey key = createPrivateKey();
        assertThrows(IllegalArgumentException.class, () -> new X500PrivateCredential(null, key));
        X509Certificate cert = createCertificate();
        assertThrows(IllegalArgumentException.class, () -> new X500PrivateCredential(
                cert, null));
    }

    @Test
    public void testX500PrivateCredential_validArgs() throws Exception {
        PrivateKey key = createPrivateKey();
        X509Certificate cert = createCertificate();
        X500PrivateCredential x500Cred = new X500PrivateCredential(cert, key);
        assertEquals(cert, x500Cred.getCertificate());
        assertEquals(key, x500Cred.getPrivateKey());
        assertNull(x500Cred.getAlias());

        String alias = "alias";
        x500Cred = new X500PrivateCredential(cert, key, alias);
        assertEquals(cert, x500Cred.getCertificate());
        assertEquals(key, x500Cred.getPrivateKey());
        assertEquals(alias, x500Cred.getAlias());

        x500Cred.destroy();
        assertTrue(x500Cred.isDestroyed());
    }

    private static X509Certificate createCertificate() throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
        Certificate certificate = certificateFactory.generateCertificate(
                new ByteArrayInputStream(encodedCertificate.getBytes()));

        return (X509Certificate) certificate;
    }

    private static final String encodedCertificate =
            "-----BEGIN CERTIFICATE-----\n"
            + "MIID0jCCAzugAwIBAgIBAjANBgkqhkiG9w0BAQQFADCBmjELMAkGA1UEBhMCVUsx\n"
            + "EjAQBgNVBAgTCUhhbXBzaGlyZTETMBEGA1UEBxMKV2luY2hlc3RlcjETMBEGA1UE\n"
            + "ChMKSUJNIFVLIEx0ZDEMMAoGA1UECxMDSlRDMRYwFAYDVQQDEw1QYXVsIEggQWJi\n"
            + "b3R0MScwJQYJKoZIhvcNAQkBFhhQYXVsX0hfQWJib3R0QHVrLmlibS5jb20wHhcN\n"
            + "MDQwNjIyMjA1MDU1WhcNMDUwNjIyMjA1MDU1WjCBmDELMAkGA1UEBhMCVUsxEjAQ\n"
            + "BgNVBAgTCUhhbXBzaGlyZTETMBEGA1UEBxMKV2luY2hlc3RlcjETMBEGA1UEChMK\n"
            + "SUJNIFVrIEx0ZDEMMAoGA1UECxMDSkVUMRQwEgYDVQQDEwtQYXVsIEFiYm90dDEn\n"
            + "MCUGCSqGSIb3DQEJARYYUGF1bF9IX0FiYm90dEB1ay5pYm0uY29tMIGfMA0GCSqG\n"
            + "SIb3DQEBAQUAA4GNADCBiQKBgQDitZBQ5d18ecNJpcnuKTraHYtqsAugoc95/L5Q\n"
            + "28s3t1QAu2505qQR1MZaAkY7tDNyl1vPnZoym+Y06UswTrZoVYo/gPNeyWPMTsLA\n"
            + "wzQvk5/6yhtE9ciH7B0SqYw6uSiDTbUY/zQ6qed+TsQhjlbn3PUHRjnI2P8A04cg\n"
            + "LgYYGQIDAQABo4IBJjCCASIwCQYDVR0TBAIwADAsBglghkgBhvhCAQ0EHxYdT3Bl\n"
            + "blNTTCBHZW5lcmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYEFPplRPs65hUfxUBs\n"
            + "6/Taq7nN8i1UMIHHBgNVHSMEgb8wgbyAFJOMtPAwlXdZLqE7DKU6xpL6FjFtoYGg\n"
            + "pIGdMIGaMQswCQYDVQQGEwJVSzESMBAGA1UECBMJSGFtcHNoaXJlMRMwEQYDVQQH\n"
            + "EwpXaW5jaGVzdGVyMRMwEQYDVQQKEwpJQk0gVUsgTHRkMQwwCgYDVQQLEwNKVEMx\n"
            + "FjAUBgNVBAMTDVBhdWwgSCBBYmJvdHQxJzAlBgkqhkiG9w0BCQEWGFBhdWxfSF9B\n"
            + "YmJvdHRAdWsuaWJtLmNvbYIBADANBgkqhkiG9w0BAQQFAAOBgQAnQ22Jw2HUrz7c\n"
            + "VaOap31mTikuQ/CQxpwPYiSyTJ4s99eEzn+2yAk9tIDIJpqoay/fj+OLgPUQKIAo\n"
            + "XpRVvmHlGE7UqMKebZtSZJQzs6VoeeKFhgHmqg8eVC2AsTc4ZswJmg4wCui5AH3a\n"
            + "oqG7PIM3LxZqXYQlZiPSZ6kCpDOWVg==\n"
            + "-----END CERTIFICATE-----\n";
    private static PrivateKey createPrivateKey()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
        kpg.initialize(new DHParameterSpec(DH_PARAMS_P, DH_PARAMS_G));
        PrivateKey key = kpg.generateKeyPair().getPrivate();
        assertNotNull(key);
        return key;

    }

    // Generated with: openssl dhparam -C 1024
    private static final BigInteger DH_PARAMS_P = new BigInteger(1, new byte[] {
            (byte) 0xA2, (byte) 0x31, (byte) 0xB4, (byte) 0xB3, (byte) 0x6D, (byte) 0x9B,
            (byte) 0x7E, (byte) 0xF4, (byte) 0xE7, (byte) 0x21, (byte) 0x51, (byte) 0x40,
            (byte) 0xEB, (byte) 0xC6, (byte) 0xB6, (byte) 0xD6, (byte) 0x54, (byte) 0x56,
            (byte) 0x72, (byte) 0xBE, (byte) 0x43, (byte) 0x18, (byte) 0x30, (byte) 0x5C,
            (byte) 0x15, (byte) 0x5A, (byte) 0xF9, (byte) 0x19, (byte) 0x62, (byte) 0xAD,
            (byte) 0xF4, (byte) 0x29, (byte) 0xCB, (byte) 0xC6, (byte) 0xF6, (byte) 0x64,
            (byte) 0x0B, (byte) 0x9D, (byte) 0x23, (byte) 0x80, (byte) 0xF9, (byte) 0x5B,
            (byte) 0x1C, (byte) 0x1C, (byte) 0x6A, (byte) 0xB4, (byte) 0xEA, (byte) 0xB9,
            (byte) 0x80, (byte) 0x98, (byte) 0x8B, (byte) 0xAF, (byte) 0x15, (byte) 0xA8,
            (byte) 0x5C, (byte) 0xC4, (byte) 0xB0, (byte) 0x41, (byte) 0x29, (byte) 0x66,
            (byte) 0x9F, (byte) 0x9F, (byte) 0x1F, (byte) 0x88, (byte) 0x50, (byte) 0x97,
            (byte) 0x38, (byte) 0x0B, (byte) 0x01, (byte) 0x16, (byte) 0xD6, (byte) 0x84,
            (byte) 0x1D, (byte) 0x48, (byte) 0x6F, (byte) 0x7C, (byte) 0x06, (byte) 0x8C,
            (byte) 0x6E, (byte) 0x68, (byte) 0xCD, (byte) 0x38, (byte) 0xE6, (byte) 0x22,
            (byte) 0x30, (byte) 0x61, (byte) 0x37, (byte) 0x02, (byte) 0x3D, (byte) 0x47,
            (byte) 0x62, (byte) 0xCE, (byte) 0xB9, (byte) 0x1A, (byte) 0x69, (byte) 0x9D,
            (byte) 0xA1, (byte) 0x9F, (byte) 0x10, (byte) 0xA1, (byte) 0xAA, (byte) 0x70,
            (byte) 0xF7, (byte) 0x27, (byte) 0x9C, (byte) 0xD4, (byte) 0xA5, (byte) 0x15,
            (byte) 0xE2, (byte) 0x15, (byte) 0x0C, (byte) 0x20, (byte) 0x90, (byte) 0x08,
            (byte) 0xB6, (byte) 0xF5, (byte) 0xDF, (byte) 0x1C, (byte) 0xCB, (byte) 0x82,
            (byte) 0x6D, (byte) 0xC0, (byte) 0xE1, (byte) 0xBD, (byte) 0xCC, (byte) 0x4A,
            (byte) 0x76, (byte) 0xE3,
    });

    // generator of 2
    private static final BigInteger DH_PARAMS_G = BigInteger.valueOf(2);
}
