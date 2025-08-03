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

package com.android.server.wifi;

import android.annotation.NonNull;

import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * Stores supplicant certificate event information
 */
public class CertificateEventInfo {
    CertificateEventInfo(@NonNull X509Certificate cert, @NonNull String certHash) {
        this.mCert = Objects.requireNonNull(cert);
        this.mCertHash = Objects.requireNonNull(certHash);
    }
    @NonNull private final X509Certificate mCert;
    @NonNull private final String mCertHash;

    /**
     * Get the X509 certificate stored in this object
     *
     * @return X509 certificate
     */
    public X509Certificate getCert() {
        return mCert;
    }

    /**
     * Get the certificate hash of the stored certificate
     *
     * @return certificate hash
     */
    public String getCertHash() {
        return mCertHash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" Certificate Hash: ").append(mCertHash);
        sb.append(" X509Certificate: ").append(mCert);
        return sb.toString();
    }
}



