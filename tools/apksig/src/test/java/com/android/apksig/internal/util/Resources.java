/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.apksig.internal.util;

import com.android.apksig.ApkSignerTest;
import com.android.apksig.KeyConfig;
import com.android.apksig.SigningCertificateLineage;
import com.android.apksig.util.DataSource;

import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Assorted methods to obtaining test input from resources.
 */
public final class Resources {
    private Resources() {}

    // All signers with the same prefix and an _X suffix were signed with the private key of the
    // (X-1) signer.
    public static final String FIRST_RSA_2048_SIGNER_RESOURCE_NAME = "rsa-2048";
    public static final String SECOND_RSA_2048_SIGNER_RESOURCE_NAME = "rsa-2048_2";
    public static final String THIRD_RSA_2048_SIGNER_RESOURCE_NAME = "rsa-2048_3";
    public static final String FIRST_RSA_1024_SIGNER_RESOURCE_NAME = "rsa-1024";
    public static final String SECOND_RSA_1024_SIGNER_RESOURCE_NAME = "rsa-1024_2";

    public static final String FIRST_RSA_4096_SIGNER_RESOURCE_NAME = "rsa-4096";

    public static final String EC_P256_SIGNER_RESOURCE_NAME = "ec-p256";
    public static final String EC_P256_2_SIGNER_RESOURCE_NAME = "ec-p256_2";

    // This is the same cert as above with the modulus reencoded to remove the leading 0 sign bit.
    public static final String FIRST_RSA_2048_SIGNER_CERT_WITH_NEGATIVE_MODULUS =
            "rsa-2048_negmod.x509.der";

    public static final String LINEAGE_RSA_2048_2_SIGNERS_RESOURCE_NAME =
            "rsa-2048-lineage-2-signers";
    public static final String LINEAGE_RSA_2048_3_SIGNERS_RESOURCE_NAME =
            "rsa-2048-lineage-3-signers";
    public static final String LINEAGE_RSA_2048_3_SIGNERS_1_NO_CAPS_RESOURCE_NAME =
            "rsa-2048-lineage-3-signers-1-no-caps";
    public static final String LINEAGE_RSA_2048_2_SIGNERS_2_3_RESOURCE_NAME =
            "rsa-2048-lineage-2-signers-2-3";
    public static final String LINEAGE_RSA_2048_TO_RSA_4096_RESOURCE_NAME =
            "rsa-2048-to-4096-lineage-2-signers";

    public static final String LINEAGE_EC_P256_2_SIGNERS_RESOURCE_NAME =
            "ec-p256-lineage-2-signers";

    public static byte[] toByteArray(Class<?> cls, String resourceName) throws IOException {
        try (InputStream in = cls.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }
            return ByteStreams.toByteArray(in);
        }
    }

    public static InputStream toInputStream(Class<?> cls, String resourceName) throws IOException {
            InputStream in = cls.getResourceAsStream(resourceName);
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }
            return in;
    }

    public static X509Certificate toCertificate(
            Class <?> cls, String resourceName) throws IOException, CertificateException {
        try (InputStream in = cls.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }
            return X509CertificateUtils.generateCertificate(in);
        }
    }

    public static List<X509Certificate> toCertificateChain(
            Class <?> cls, String resourceName) throws IOException, CertificateException {
        Collection<? extends Certificate> certs;
        try (InputStream in = cls.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }
            certs = X509CertificateUtils.generateCertificates(in);
        }
        List<X509Certificate> result = new ArrayList<>(certs.size());
        for (Certificate cert : certs) {
            result.add((X509Certificate) cert);
        }
        return result;
    }

    public static PrivateKey toPrivateKey(Class <?> cls, String resourceName)
                    throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        int delimiterIndex = resourceName.indexOf('-');
        if (delimiterIndex == -1) {
            throw new IllegalArgumentException(
                    "Failed to autodetect key algorithm from resource name: " + resourceName);
        }
        String keyAlgorithm = resourceName.substring(0, delimiterIndex).toUpperCase(Locale.US);
        return toPrivateKey(cls, resourceName, keyAlgorithm);
    }

    public static PrivateKey toPrivateKey(
            Class <?> cls, String resourceName, String keyAlgorithm)
                    throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        byte[] encoded = toByteArray(cls, resourceName);

        // Keep overly strictly linter happy by limiting what JCA KeyFactory algorithms are used
        // here
        KeyFactory keyFactory;
        switch (keyAlgorithm.toUpperCase(Locale.US)) {
            case "RSA":
                keyFactory = KeyFactory.getInstance("rsa");
                break;
            case "DSA":
                keyFactory = KeyFactory.getInstance("dsa");
                break;
            case "EC":
                keyFactory = KeyFactory.getInstance("ec");
                break;
            default:
                throw new InvalidKeySpecException("Unsupported key algorithm: " + keyAlgorithm);
        }

        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    public static SigningCertificateLineage.SignerConfig toLineageSignerConfig(Class<?> cls,
            String resourcePrefix) throws Exception {
        PrivateKey privateKey = toPrivateKey(cls, resourcePrefix + ".pk8");
        X509Certificate cert = Resources.toCertificate(cls,
                resourcePrefix + ".x509.pem");
        return new SigningCertificateLineage.SignerConfig.Builder(
                        new KeyConfig.Jca(privateKey), cert)
                .build();
    }

    public static DataSource toDataSource(Class<?> cls, String dataSourceResourceName)
            throws IOException {
        return new ByteBufferDataSource(ByteBuffer.wrap(Resources
                .toByteArray(ApkSignerTest.class, dataSourceResourceName)));
    }

    public static SigningCertificateLineage toSigningCertificateLineage(Class<?> cls,
            String fileResourceName) throws IOException {
        DataSource lineageDataSource = toDataSource(cls, fileResourceName);
        return SigningCertificateLineage.readFromDataSource(lineageDataSource);
    }

    public static File toFile(Class<?> cls, String fileResourceName,
            TemporaryFolder temporaryFolder) throws IOException {
        File outFile = temporaryFolder.newFile();
        try (InputStream in = cls.getResourceAsStream(fileResourceName);
             OutputStream out = new FileOutputStream(outFile)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + fileResourceName);
            }
            in.transferTo(out);
            return outFile;
        }
    }
}
