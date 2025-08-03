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

package com.android.rkpdapp.database;

import java.time.Instant;

import co.nstant.in.cbor.model.DataItem;

/**
 * In-memory key representation for Remotely Provisioned Keys.
 */
public final class RkpKey {
    private final byte[] mMacedPublicKey;
    private final DataItem mCoseKey;
    private final byte[] mKeyBlob;
    private final String mIrpcHal;
    private final byte[] mPublicKey;

    public RkpKey(byte[] keyBlob, byte[] macedPublicKey, DataItem coseKey, String irpcHal,
            byte[] publicKey) {
        this.mKeyBlob = keyBlob;
        this.mMacedPublicKey = macedPublicKey;
        this.mCoseKey = coseKey;
        this.mIrpcHal = irpcHal;
        this.mPublicKey = publicKey;
    }

    public byte[] getMacedPublicKey() {
        return mMacedPublicKey;
    }

    public DataItem getCoseKey() {
        return mCoseKey;
    }

    public byte[] getPublicKey() {
        return mPublicKey;
    }

    /**
     * Creates the provisioned key with the information present in this data object as well as the
     * provided expiration time and certificate chain.
     *
     * This function is helpful to generate the provisioned key only when required instead of
     * generating and storing it separately.
     */
    public ProvisionedKey generateProvisionedKey(byte[] certificateChain, Instant expirationTime) {
        return new ProvisionedKey(mKeyBlob, mIrpcHal, mPublicKey, certificateChain, expirationTime);
    }
}
