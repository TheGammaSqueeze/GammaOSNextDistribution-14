/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.kanon;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import dagger.internal.Preconditions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

@AutoValue
public abstract class KeyAttestationCertificateChainRecord {

    private static final int BYTES_TO_ENCODE_CERTIFICATE_LENGTH = 4;

    abstract ImmutableList<byte[]> getCertificateChain();

    /** Create a KeyAttestation Record object with the given certificate chain */
    public static KeyAttestationCertificateChainRecord create(List<byte[]> certificateChain) {
        Preconditions.checkNotNull(certificateChain);
        return new AutoValue_KeyAttestationCertificateChainRecord(
                ImmutableList.copyOf(certificateChain));
    }

    /**
     * Encode in a format K-Anon server can understand
     *
     * <p>The format is 4 bytes for the size of first certificate followed by the certificate, next
     * 4 bytes for the size of the second certificate followed by the second certificate, and so on.
     */
    public byte[] encode() throws IOException {
        List<byte[]> certificateChain = getCertificateChain();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        for (byte[] certificate : certificateChain) {
            outputStream.write(
                    ByteBuffer.allocate(BYTES_TO_ENCODE_CERTIFICATE_LENGTH)
                            .putInt(certificate.length)
                            .array());
            outputStream.write(certificate);
        }

        return outputStream.toByteArray();
    }
}
