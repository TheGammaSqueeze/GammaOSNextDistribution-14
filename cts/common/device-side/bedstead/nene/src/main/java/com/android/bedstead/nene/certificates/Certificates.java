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

package com.android.bedstead.nene.certificates;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

public final class Certificates {

    public static final Certificates sInstance = new Certificates();

    public enum KeyAlgorithmType {
        RSA("RSA"),
        EC("EC");

        private final String mValue;

        KeyAlgorithmType(String value) {
            mValue = value;
        }

        public String getValue() {
            return mValue;
        }
    }

    private Certificates() {}

    /** Generate a private key. */
    public PrivateKey generatePrivateKey(final byte[] key, KeyAlgorithmType keyAlgorithmType) {
        try {
            return KeyFactory.getInstance(keyAlgorithmType.getValue()).generatePrivate(
                    new PKCS8EncodedKeySpec(key));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new AssertionError("Unable to get private key." + e);
        }
    }

    /** Generate a certificate. */
    public Certificate generateCertificate(byte[] cert) {
        try {
            return CertificateFactory.getInstance("X.509").generateCertificate(
                    new ByteArrayInputStream(cert));
        } catch (CertificateException e) {
            throw new AssertionError("Unable to get certificate." + e);
        }
    }

}
