/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.keychain.tests;

import static com.android.keychain.KeyChainActivity.CertificateParametersFilter;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.util.Base64;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CancellationException;

import javax.security.auth.x500.X500Principal;

@LargeTest
@RunWith(AndroidJUnit4.class)
public final class KeyChainActivityTest {
    // Generated with:
    // openssl req -newkey rsa:2048 -nodes -keyout key.pem -x509 -days 3650
    //   -out root_ca_certificate.pem
    private static final String ROOT_CA_CERT_RSA =
            "MIIDazCCAlOgAwIBAgIUKNVj8g/LG+6tqsK8HZqT6EuktpkwDQYJKoZIhvcNAQEL" +
            "BQAwRTELMAkGA1UEBhMCVUsxCzAJBgNVBAgMAk5BMQ8wDQYDVQQHDAZMb25kb24x" +
            "GDAWBgNVBAoMD0FuZHJvaWQgVGVzdCBDQTAeFw0yMTAyMjMwMDU0MzdaFw0zMTAy" +
            "MjEwMDU0MzdaMEUxCzAJBgNVBAYTAlVLMQswCQYDVQQIDAJOQTEPMA0GA1UEBwwG" +
            "TG9uZG9uMRgwFgYDVQQKDA9BbmRyb2lkIFRlc3QgQ0EwggEiMA0GCSqGSIb3DQEB" +
            "AQUAA4IBDwAwggEKAoIBAQDLpcaoJijYhS3QUDgG8kVGrwTxaVTS0TE156fJa5za" +
            "s3RI7TYKHzYwn1KMJJUwoc+cOkv+rFC7j5MQ+6SMK2GpDoCoGn4FV9dDPVnxIgjj" +
            "/66kuf+we1ur3gz7m/8tFFdZhLFoFMRzcNg+F35jSur8y0dnc8O83gMwuf+91pU7" +
            "HyNahHzDyMM5sR7u1K91R1MKiOnVqJNTHWVK+rl3G0m0rbDTz7/xbq3/FPBvw764" +
            "QUJgkSEG15i5CFNn2ww5IYnF30Wbke3kUfdd/Q32MOyfkcp3El/TPJj/3mevGHed" +
            "vTi0j6ovIXMrDnoJvmeI40p97EMIlaZ3x39i+krE02RfAgMBAAGjUzBRMB0GA1Ud" +
            "DgQWBBRDpNR2iik8NGDmY5IvgiUy6dMRMjAfBgNVHSMEGDAWgBRDpNR2iik8NGDm" +
            "Y5IvgiUy6dMRMjAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQDG" +
            "OIZdoopwX2PlkAnWMR6PIl4GJ9eiPg1cbQHHcqsB8oNYiiiZydJ8TniZYFptEIjQ" +
            "LIuerSr/a+c353JNOOK76w0vciJld8uQDCTnMChUahJsQjJJgDCyhjkQqV9Srstu" +
            "IClm5IP/LEYVSzfR+LqhwX5JYUU8zsDnbiAH3CIENhBw5ApXgfDHP54bHOBw5eHE" +
            "ETATnib8uPZVHsyHQXXULDqMITY9pmjc9/9x6CqcMGEiSVvhDSujerVlnw3I17Te" +
            "HWT8bf8mJyQFR8kr59NPQMNN4oUIfFzj2VgzjL21mKGR+hBGqoIMJ1ZdPGCJsw0W" +
            "5WYM4TtQtL7yNVDtJzOL";

    // Generated with:
    // openssl genrsa  -out intermediate_key.pem 2048
    // openssl req -new -key intermediate_key.pem -out intermediate_ca.csr
    // openssl x509 -req -days 3650 -in intermediate_ca.csr -CA root_ca_certificate.pem
    //   -CAkey key.pem -CAcreateserial -out intermediate_ca.pem
    private static final String INTERMEDIATE_CA_CERT_RSA =
            "MIIDHjCCAgYCFHgZBbZMuJTvvm1wlBBoPE7peS4jMA0GCSqGSIb3DQEBCwUAMEUx" +
            "CzAJBgNVBAYTAlVLMQswCQYDVQQIDAJOQTEPMA0GA1UEBwwGTG9uZG9uMRgwFgYD" +
            "VQQKDA9BbmRyb2lkIFRlc3QgQ0EwHhcNMjEwMjIzMDA1ODQ5WhcNMzEwMjIxMDA1" +
            "ODQ5WjBSMQswCQYDVQQGEwJVSzELMAkGA1UECAwCTkExDzANBgNVBAcMBkxvbmRv" +
            "bjElMCMGA1UECgwcQW5kcm9pZCBJbnRlcm1lZGlhdGUgVGVzdCBDQTCCASIwDQYJ" +
            "KoZIhvcNAQEBBQADggEPADCCAQoCggEBAMZ5tLvS8qiZGK8fBUPzZYWRG2kmf3Hi" +
            "o6db8qjTvzkJ0l43zmI92v0pEy8a4/XlC7wGSaJuX/TqpAR43+a9kYqGWczkASpX" +
            "7w1W5tjjDlLANZdRP2R7Wb3XWTTMxzDWOqsMGTHw+H92oFm4bGO9+PaBRRzifLmQ" +
            "OI/Whw6/kHZlXyI7J68EwRbyaZXNJ6iqk8dF7B4iwZ9yNgW1H0m8uxdDAykEA2WX" +
            "wbzITwD1zdMsQV7/eLe9RIMFN5VMeHtIFUi2AcioG0i4ZLZNFnVrFQKu7u3XQyxk" +
            "u7ZzgeGtpluM71sDxnqhZv9NaZIq3mV3JrKHPsw6+uJjN4U5AVfzIYUCAwEAATAN" +
            "BgkqhkiG9w0BAQsFAAOCAQEAXrRQUFlLyS3QlmwkGocLQISY9B8fF8LTH7sl6HFA" +
            "VSVuhPDuNsmqVhsMH1981MY2rSVfM3fkUMz1WEH7ZbhooYPirax/AlW+oRdRB/xX" +
            "WEAJRGgybK98PXogI4tqEvicVn2kfcyNzmfMn8yRClxD5GuZ0oOA50lpUwUmeJjo" +
            "jb3DY8NF+bcA0lW5h7p86ezqjhB836XZRL47jZJj+jgKoiSsdcex7rzikW6bzdlV" +
            "f9DCuBJMpM1y31AP15Gvg5Jhh9Wc4y8LLipTn7wGdJvvhclUe1U3roerhAEB+rU/" +
            "Eu7h+Nqjogg3IzmfHlhDe4N8o/XLdc2FnhCZGZklDDDMvA==";

    // Generated with:
    // openssl req -new -newkey rsa:2048 -nodes -keyout server.key -out server.csr
    // openssl x509 -req -days 3650 -in server.csr -CA intermediate_ca.pem
    //   -CAkey intermediate_key.pem -CAcreateserial -out server.pem
    private static final String LEAF_SERVER_CERT_RSA =
            "MIIDIjCCAgoCFDV+Rg1WxBy4ThLxvu1lRJl1YUzwMA0GCSqGSIb3DQEBCwUAMFIx" +
            "CzAJBgNVBAYTAlVLMQswCQYDVQQIDAJOQTEPMA0GA1UEBwwGTG9uZG9uMSUwIwYD" +
            "VQQKDBxBbmRyb2lkIEludGVybWVkaWF0ZSBUZXN0IENBMB4XDTIxMDIyMzAxMDEw" +
            "OFoXDTMxMDIyMTAxMDEwOFowSTELMAkGA1UEBhMCVUsxCzAJBgNVBAgMAk5BMQ8w" +
            "DQYDVQQHDAZMb25kb24xHDAaBgNVBAoME0FuZHJvaWQgU2VydmVyIFRlc3QwggEi" +
            "MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC7267U6iCagBJFiYMMUkteQliO" +
            "ljOfnuSZJTG1xxNXBggLDdHmchjOPQgEICINpxz7Hhg+PLME3DEqrwUHo9k/bR3e" +
            "Tglt1o6qxGirIEKROtdzNyi3medRex6FATXq1g4W/1U0tl8EbMv9kJPZP52Uj0Rq" +
            "XYZ9Y27IGtWDcudpyJij/nBbV/kfufti2pFNHhnXytyBrQXz6AziZjHnNt316z64" +
            "Tfqchr0jxqIF3Sup9AVKnGooGymwT8ez5C6VO7WoRZnp40pH78GzSALnRJC9w26V" +
            "1IVlqjYPWvFwJ0ENb0Nuc8Jr3tW/0gf1UzTsuRsMU9vl+tMjweXS8HI8M4uRAgMB" +
            "AAEwDQYJKoZIhvcNAQELBQADggEBAIcOLAdu9HU6+Bk/SkQW2qW2mY5+WFSYYRG5" +
            "KfDiMQr0kUeddBJYk+bLN3Qqi6KUAZ+ITxyarVrjZjxaTr1JV6m9fVxdZ8elAx+7" +
            "ci9ghBkiUPKFtVz3Y1F31Em4tLRr0LHF49Mjvr62+mQuZlAXZ3TuMdxrwc9AePhN" +
            "btY8YwUsPPJ2vrIQB14NJ6EIaMaIyTowBPX9eo5K47ISbfnCUKhFYAEK4v5s4Hkt" +
            "Us/209E0FNdFKOZDKDwclhZSFbyA1tknBRXsP7QCFuj/hPFxcRaj9R8CoHQAqEFr" +
            "mV8JscA9dV40m+kRkj5TZeHjIw2xI39btv5aeRW2fBNB0MEoNPQ=";

    private static final String PRIVATE_SERVER_KEY =
            "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQC7267U6iCagBJF" +
            "iYMMUkteQliOljOfnuSZJTG1xxNXBggLDdHmchjOPQgEICINpxz7Hhg+PLME3DEq" +
            "rwUHo9k/bR3eTglt1o6qxGirIEKROtdzNyi3medRex6FATXq1g4W/1U0tl8EbMv9" +
            "kJPZP52Uj0RqXYZ9Y27IGtWDcudpyJij/nBbV/kfufti2pFNHhnXytyBrQXz6Azi" +
            "ZjHnNt316z64Tfqchr0jxqIF3Sup9AVKnGooGymwT8ez5C6VO7WoRZnp40pH78Gz" +
            "SALnRJC9w26V1IVlqjYPWvFwJ0ENb0Nuc8Jr3tW/0gf1UzTsuRsMU9vl+tMjweXS" +
            "8HI8M4uRAgMBAAECggEARWNUhYJhPpAVr6emRxPSkONysGAce1YGW+bYIKuCoj8x" +
            "E1wsbrEwJmV2o4d27JIQa1TnYX2sJhxq8Lgq5HKJ2Rql0KoEY5S/p6Xaf3LwA5K3" +
            "Z/A00vQ+8+LFGB2lW7NrCuWPBGRkXk8NXgBcC/+qZegxPhSDi6cBkVoQCXiUr4Zs" +
            "4wacrmpQbl/FekvpuWQxnVAm95knZhJ87r7izwO6e3VP1VZseG7ld6PbxkLnuTP7" +
            "Y9Q+viAPwkk1SedvYy6RtIRbyyOKzTVbh9SXirPsLM6N+a8k3J7LY+8HnZhte0O4" +
            "BFoZwwhXt/kPHilro6gt69Bh2bas+lpP62c8e7Q3HQKBgQDwY6XJzLXV1UQNbwLL" +
            "FHTDBdf3Afe6W5jcKpsOUaNP/424JeLpc5a2ccaytCPs2p3RCTZ3SHaxe+LNrKbU" +
            "iKdloGO/XTqcAHbvw/uy5Zzsv6XqUjREmCvCatF7UU7PH9Pztjhb91IXT7taB1Ee" +
            "yYyPPJU9ioZDEV45/PIUpGwjnwKBgQDIDrtHx5fBH0od8XWSBzuQ3QjLl3WZcEAv" +
            "Bf2GcdYUV7migWOXm28k51l2VbQQRCVOulJjf45TZgZgzKInZZJ+rZ69FLrOx7q3" +
            "7X+8rTyWKa2u/IlZ/EBtNIRuqH7A+OuY5qroFngtYa/qsaX58/eUAe64I6HslenG" +
            "ktvmwdSCzwKBgAtQ1YQLU9/t+xcay6ndm6V2h/UDrbKjDy4F/2iMJUDlybkKZ4UP" +
            "wN9zuaO94RcML3OgmGTDD3tJVqLR5sSIbkDVbPycGd8wEmk084s3TczDNL80AWvd" +
            "Meoj9xpz+F69o8+MG1kQ6ldYlHwnbgUh/bDcbDYKaEmN7r6SDp80IjcHAoGAOrqQ" +
            "Yf8G3qu3z1h94jN7Wgh5N4MsA7I/NU614Uzzwp8KINmJCg2YMCY2ThXUuV238gei" +
            "fhEJEBSIVMxd4eDgg42mZu159ZAOkUYIVLQqcA6mLRN3otH5e9WJ9w5Bv5aTWxyE" +
            "GYPXHcNqqCQkjF8BVBLJKIdVVqWfriqYoYJPR2MCgYAWWIrS8XN1c2iLParb+xOJ" +
            "q1WKe8q5wFucqJCVFbJXjtpGgZFxZLAFlT8VpaiBwDLQBPC+CYhzLVwAvzW2h46W" +
            "KONqO7zoxiuwOEj466iH4YrgviNw6lGtgSPB6wx91c7se/1lcZhviBMB5rUjtxxj" +
            "qKIC9Y+gz77w1M3pwMlhDA==";

    private static final X500Principal LEAF_SUBJECT =
            new X500Principal("O=Android Server Test, L=London, ST=NA, C=UK");

    private static final X500Principal INTERMEDIATE_SUBJECT =
            new X500Principal("O=Android Intermediate Test CA, L=London, ST=NA, C=UK");

    private static final X500Principal ROOT_SUBJECT =
            new X500Principal("O=Android Test CA, L=London, ST=NA, C=UK");

    private byte[] mPrivateKey;
    private byte[] mLeafRsaCertificate;
    private byte[] mIntermediateRsaCertificate;
    private byte[] mRootRsaCertificate;

    @Before
    public void setUp() {
        mPrivateKey = Base64.decode(PRIVATE_SERVER_KEY, Base64.DEFAULT);
        mLeafRsaCertificate = Base64.decode(LEAF_SERVER_CERT_RSA, Base64.DEFAULT);
        mIntermediateRsaCertificate = Base64.decode(INTERMEDIATE_CA_CERT_RSA, Base64.DEFAULT);
        mRootRsaCertificate = Base64.decode(ROOT_CA_CERT_RSA, Base64.DEFAULT);
    }

    @After
    public void tearDown() {
        KeyStore keyStore = null;
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry("testCertificateParametersFilter_client");
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException
                | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testCertificateParametersFilter_filtersByIntermediateIssuer()
            throws CancellationException, IOException, CertificateException,
            KeyStoreException, NoSuchAlgorithmException {
        KeyStore keyStore = prepareKeyStoreWithLongChainCertificates();

        assertThat(createCheckerForIssuer(keyStore, ROOT_SUBJECT)
                .shouldPresentCertificate("testCertificateParametersFilter_client")).isTrue();

        assertThat(createCheckerForIssuer(keyStore, INTERMEDIATE_SUBJECT)
                .shouldPresentCertificate("testCertificateParametersFilter_client")).isTrue();

        assertThat(createCheckerForIssuer(keyStore, LEAF_SUBJECT)
                .shouldPresentCertificate("testCertificateParametersFilter_client")).isFalse();
    }

    // Return a KeyStore instance that has both a client certificate as well as a certificate
    // chain associated with it.
    private KeyStore prepareKeyStoreWithLongChainCertificates()
            throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        Certificate[] certs = new Certificate[3];
        certs[0] = parseCertificate(mLeafRsaCertificate);
        certs[1] = parseCertificate(mIntermediateRsaCertificate);
        certs[2] = parseCertificate(mRootRsaCertificate);

        keyStore.setKeyEntry("testCertificateParametersFilter_client", parseKey(mPrivateKey),
                null, certs);

        return keyStore;
    }

    // Create a CertificateParametersFilter instance that has the specified issuer as a requested
    // issuer.
    private static CertificateParametersFilter createCheckerForIssuer(
            KeyStore keyStore, X500Principal issuer) {
        return new CertificateParametersFilter(
                keyStore, new String[] {},
                new ArrayList<byte[]>(Collections.singletonList(issuer.getEncoded())));
    }

    private static X509Certificate parseCertificate(byte[] certificateBytes) {
        InputStream in = new ByteArrayInputStream(certificateBytes);
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate)cf.generateCertificate(in);
        } catch (CertificateException e) {
            fail(String.format("Could not parse certificate: %s", e));
            return null;
        }
    }

    private static PrivateKey parseKey(byte[] key) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(key));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            fail(String.format("Could not parse private key: %s", e));
            return null;
        }
    }
}
