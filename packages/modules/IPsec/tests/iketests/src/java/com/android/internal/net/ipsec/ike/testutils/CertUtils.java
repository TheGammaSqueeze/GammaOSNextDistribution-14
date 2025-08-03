/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.internal.net.ipsec.test.ike.testutils;

import android.content.Context;

import androidx.test.InstrumentationRegistry;

import com.android.internal.net.ipsec.test.ike.utils.IkeCertUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.stream.Collectors;

/** CertUtils provides utility methods for creating X509 certificate and private key. */
public final class CertUtils {
    private static final String PEM_FOLDER_NAME = "pem";
    private static final String KEY_FOLDER_NAME = "key";
    private static final String NEW_LINE_CHAR = "\n";
    private static final String PEM_TYPE_PRIVATE_KEY = "-----(BEGIN|END) PRIVATE KEY-----";

    /** Creates an X509Certificate with a pem file */
    public static X509Certificate createCertFromPemFile(String fileName) throws Exception {
        Context context = InstrumentationRegistry.getContext();
        InputStream inputStream =
                context.getResources().getAssets().open(PEM_FOLDER_NAME + "/" + fileName);

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(inputStream);
    }

    /** Creates an private key from a PKCS8 format key file */
    public static RSAPrivateKey createRsaPrivateKeyFromKeyFile(String fileName) throws Exception {
        Context context = InstrumentationRegistry.getContext();
        InputStream inputStream =
                context.getResources().getAssets().open(KEY_FOLDER_NAME + "/" + fileName);

        String pemText =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining(NEW_LINE_CHAR));

        byte[] certificateBytes =
                Base64.getDecoder()
                        .decode(
                                pemText.replaceAll(PEM_TYPE_PRIVATE_KEY, "")
                                        .replaceAll(NEW_LINE_CHAR, "")
                                        .getBytes(StandardCharsets.UTF_8));
        return (RSAPrivateKey) IkeCertUtils.privateKeyFromByteArray(certificateBytes);
    }
}
