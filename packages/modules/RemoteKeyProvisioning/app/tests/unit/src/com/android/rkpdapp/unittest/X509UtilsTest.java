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

package com.android.rkpdapp.unittest;

import static com.android.rkpdapp.unittest.Utils.generateEcdsaKeyPair;
import static com.android.rkpdapp.unittest.Utils.getP256PubKeyFromBytes;
import static com.android.rkpdapp.unittest.Utils.signPublicKey;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.rkpdapp.utils.X509Utils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@RunWith(AndroidJUnit4.class)
public class X509UtilsTest {

    @Test
    public void testFormatX509Certs() throws Exception {
        KeyPair root = generateEcdsaKeyPair();
        KeyPair intermediate = generateEcdsaKeyPair();
        KeyPair leaf = generateEcdsaKeyPair();
        X509Certificate[] certs = new X509Certificate[3];
        certs[2] = signPublicKey(root, root.getPublic());
        certs[1] = signPublicKey(root, intermediate.getPublic());
        certs[0] = signPublicKey(intermediate, leaf.getPublic());
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        for (X509Certificate cert : certs) {
            os.write(cert.getEncoded());
        }
        X509Certificate[] roundTrip = X509Utils.formatX509Certs(os.toByteArray());
        assertThat(certs.length).isEqualTo(roundTrip.length);
        for (int i = 0; i < certs.length; i++) {
            assertWithMessage("Failed on index " + i)
                    .that(certs[i].getEncoded())
                    .isEqualTo(roundTrip[i].getEncoded());
        }
    }

    @Test
    public void testGetAndFormatRawPublicKey() throws Exception {
        KeyPair testKey = generateEcdsaKeyPair();
        X509Certificate testCert = signPublicKey(testKey, testKey.getPublic());
        byte[] formattedKey = X509Utils.getAndFormatRawPublicKey(testCert);
        byte[] xPoint = new byte[32];
        byte[] yPoint = new byte[32];
        System.arraycopy(formattedKey, 0 /* offset */, xPoint, 0 /* offset */, 32 /* length */);
        System.arraycopy(formattedKey, 32 /* offset */, yPoint, 0 /* offset */, 32 /* length */);
        assertThat(testKey.getPublic()).isEqualTo(getP256PubKeyFromBytes(xPoint, yPoint));
    }

    @Test
    public void testCertificateChains() throws Exception {
        String encodedTestCert = "MIIBvTCCAWOgAwIBAgIRAKrDc87UaGSeFTRzF4vz0IcwCgYIKoZIzj0EAwIwIDEN"
                + "MAsGA1UECgwERmFrZTEPMA0GA1UEAwwGSXNzdWVyMCAXDTIzMDIwMTE1MzExMVoYDzIxMjMwMTA4MTU"
                + "zMTExWjA5MQwwCgYDVQQKDANURUUxKTAnBgNVBAMMIGFhYzM3M2NlZDQ2ODY0OWUxNTM0NzMxNzhiZj"
                + "NkMDg3MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEcC8SjTKkEqpPGQMXiZMC1/Dk3Fo/PsCZBI0E8"
                + "N4zXhBHJJZdT4LnYUNQXhSndDhrPO/x0MSySnz+hDZiRlRdzKNjMGEwHQYDVR0OBBYEFMcyyg91rTsG"
                + "QxM2hY2dfrmcYNIoMB8GA1UdIwQYMBaAFN2wvxbmHbqJicPAK1Ce+692JkfcMA8GA1UdEwEB/wQFMAM"
                + "BAf8wDgYDVR0PAQH/BAQDAgIEMAoGCCqGSM49BAMCA0gAMEUCIQD/ZJAabKvYlyuL6Ehc7bZMZFn9e7"
                + "Gu8f+QTA2fPjN/EQIgUeJPlHjNhoiu0QPpAoRbd4idOLyf5pqNEiXt7n8VDe0=";
        String encodedRootCert = "MIIBpDCCAUmgAwIBAgIQf7TE7zQ0iDLyiZIIpqKCvjAKBggqhkjOPQQDAjAgMQ0w"
                + "CwYDVQQKDARGYWtlMQ8wDQYDVQQDDAZJc3N1ZXIwIBcNMjMwMjAxMTUxMDM0WhgPMjEyMzAxMDgxNTE"
                + "wMzRaMCAxDTALBgNVBAoMBEZha2UxDzANBgNVBAMMBklzc3VlcjBZMBMGByqGSM49AgEGCCqGSM49Aw"
                + "EHA0IABNh7P0mPpgFdSw9pC+aDMDRWnZa6g7H+jdy/a4V+erKJ+lDqdsV4Ao+2+vt2WelEP0DIZl51U"
                + "CaS8CKqZtRGLB6jYzBhMB0GA1UdDgQWBBTdsL8W5h26iYnDwCtQnvuvdiZH3DAfBgNVHSMEGDAWgBTd"
                + "sL8W5h26iYnDwCtQnvuvdiZH3DAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwICBDAKBggqhkj"
                + "OPQQDAgNJADBGAiEAm9Y2YGYe/2RqI6xMGq2IFJzeJ0qjfQzBLg6KjRLiJ10CIQCxpJCHRN4Gj17/ON"
                + "JGL2npbIsQVpSn1M5xPsY+9/qB1g==";

        X509Certificate rootCert = generateCertificateFromEncodedBytes(encodedRootCert);
        X509Certificate testCert = generateCertificateFromEncodedBytes(encodedTestCert);
        X509Certificate[] validCertChain = new X509Certificate[]{testCert, rootCert};
        X509Certificate[] invalidCertChain = new X509Certificate[]{rootCert, testCert};

        assertThat(X509Utils.isCertChainValid(validCertChain)).isTrue();
        assertThat(X509Utils.isCertChainValid(invalidCertChain)).isFalse();
    }

    @Test
    public void testCertChainSwapOAndCN() throws Exception {
        String encodedTestCert = "MIIBvTCCAWOgAwIBAgIRAKrDc87UaGSeFTRzF4vz0IcwCgYIKoZIzj0EAwIwIDEP"
                + "MA0GA1UEAwwGSXNzdWVyMQ0wCwYDVQQKDARGYWtlMCAXDTIzMDIwMTE1MzExMVoYDzIxMjMwMTA4MTU"
                + "zMTExWjA5MQwwCgYDVQQKDANURUUxKTAnBgNVBAMMIGFhYzM3M2NlZDQ2ODY0OWUxNTM0NzMxNzhiZj"
                + "NkMDg3MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEcC8SjTKkEqpPGQMXiZMC1/Dk3Fo/PsCZBI0E8"
                + "N4zXhBHJJZdT4LnYUNQXhSndDhrPO/x0MSySnz+hDZiRlRdzKNjMGEwHQYDVR0OBBYEFMcyyg91rTsG"
                + "QxM2hY2dfrmcYNIoMB8GA1UdIwQYMBaAFN2wvxbmHbqJicPAK1Ce+692JkfcMA8GA1UdEwEB/wQFMAM"
                + "BAf8wDgYDVR0PAQH/BAQDAgIEMAoGCCqGSM49BAMCA0gAMEUCIQD/ZJAabKvYlyuL6Ehc7bZMZFn9e7"
                + "Gu8f+QTA2fPjN/EQIgUeJPlHjNhoiu0QPpAoRbd4idOLyf5pqNEiXt7n8VDe0=";
        String encodedRootCert = "MIIBpDCCAUmgAwIBAgIQf7TE7zQ0iDLyiZIIpqKCvjAKBggqhkjOPQQDAjAgMQ0w"
                + "CwYDVQQKDARGYWtlMQ8wDQYDVQQDDAZJc3N1ZXIwIBcNMjMwMjAxMTUxMDM0WhgPMjEyMzAxMDgxNTE"
                + "wMzRaMCAxDTALBgNVBAoMBEZha2UxDzANBgNVBAMMBklzc3VlcjBZMBMGByqGSM49AgEGCCqGSM49Aw"
                + "EHA0IABNh7P0mPpgFdSw9pC+aDMDRWnZa6g7H+jdy/a4V+erKJ+lDqdsV4Ao+2+vt2WelEP0DIZl51U"
                + "CaS8CKqZtRGLB6jYzBhMB0GA1UdDgQWBBTdsL8W5h26iYnDwCtQnvuvdiZH3DAfBgNVHSMEGDAWgBTd"
                + "sL8W5h26iYnDwCtQnvuvdiZH3DAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwICBDAKBggqhkj"
                + "OPQQDAgNJADBGAiEAm9Y2YGYe/2RqI6xMGq2IFJzeJ0qjfQzBLg6KjRLiJ10CIQCxpJCHRN4Gj17/ON"
                + "JGL2npbIsQVpSn1M5xPsY+9/qB1g==";

        X509Certificate rootCert = generateCertificateFromEncodedBytes(encodedRootCert);
        X509Certificate testCert = generateCertificateFromEncodedBytes(encodedTestCert);
        X509Certificate[] certChain = new X509Certificate[]{testCert, rootCert};

        assertThat(X509Utils.isSelfSignedCertificate(rootCert)).isTrue();
        assertThat(X509Utils.isSelfSignedCertificate(testCert)).isFalse();
        assertThat(X509Utils.isCertChainValid(certChain)).isFalse();
    }

    @Test
    public void testCertChainExpirationTimeWhenRootExpiresLater() throws Exception {
        KeyPair root = generateEcdsaKeyPair();
        KeyPair leaf = generateEcdsaKeyPair();
        X509Certificate[] certs = new X509Certificate[2];

        // root cert expires later.
        certs[0] = signPublicKey(root, leaf.getPublic(), Instant.now().plus(Duration.ofDays(2)));
        certs[1] = signPublicKey(root, root.getPublic(), Instant.now().plus(Duration.ofDays(1)));

        Date expirationTime = X509Utils.getExpirationTimeForCertificateChain(certs);
        assertThat(expirationTime).isEqualTo(certs[1].getNotAfter());
        assertThat(expirationTime).isNotEqualTo(certs[0].getNotAfter());
    }

    @Test
    public void testCertChainExpirationTimeWhenLeafExpiresLater() throws Exception {
        KeyPair root = generateEcdsaKeyPair();
        KeyPair leaf = generateEcdsaKeyPair();
        X509Certificate[] certs = new X509Certificate[2];

        // leaf cert expires later.
        certs[0] = signPublicKey(root, leaf.getPublic(), Instant.now().plus(Duration.ofDays(1)));
        certs[1] = signPublicKey(root, root.getPublic(), Instant.now().plus(Duration.ofDays(2)));

        Date expirationTime = X509Utils.getExpirationTimeForCertificateChain(certs);
        assertThat(expirationTime).isEqualTo(certs[0].getNotAfter());
        assertThat(expirationTime).isNotEqualTo(certs[1].getNotAfter());
    }

    private X509Certificate generateCertificateFromEncodedBytes(String encodedCert)
            throws CertificateException {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        InputStream in = new ByteArrayInputStream(Base64.decode(encodedCert, Base64.DEFAULT));
        return (X509Certificate) certFactory.generateCertificate(in);
    }
}
