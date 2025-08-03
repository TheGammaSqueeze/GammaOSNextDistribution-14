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

package android.security.rkp.service;

import static android.annotation.SystemApi.Client.SYSTEM_SERVER;

import android.annotation.NonNull;
import android.annotation.SystemApi;

/**
 * Certified keys that have been received from the RKPD app. These keys are represented as
 * implementation-specific binary key blobs and binary X.509 certificate chains.
 *
 * @see RegistrationProxy#getKeyAsync
 * @hide
 */
@SystemApi(client = SYSTEM_SERVER)
public class RemotelyProvisionedKey {
    private final byte[] mKeyBlob;
    private final byte[] mEncodedCertChain;

    /** @hide */
    protected RemotelyProvisionedKey(com.android.rkpdapp.RemotelyProvisionedKey key) {
        this.mKeyBlob = key.keyBlob;
        this.mEncodedCertChain = key.encodedCertChain;
    }

    /**
     * Accessor for a key blob to be used with a HAL.
     *
     * @return The raw key, encoded in an implementation-specific way according to the underlying
     * HAL that generated the key.
     */
    @NonNull
    public byte[] getKeyBlob() {
        return mKeyBlob;
    }

    /**
     * Accessor for the remotely-provisioned certificate chain for the key.
     *
     * @return a DER-encoded X.509 certificate chain
     */
    @NonNull
    public byte[] getEncodedCertChain() {
        return mEncodedCertChain;
    }
}
