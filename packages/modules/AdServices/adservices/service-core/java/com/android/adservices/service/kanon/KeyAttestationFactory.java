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

import android.content.Context;

import androidx.annotation.VisibleForTesting;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Objects;

/**
 * A factory class which returns a {@link com.android.adservices.service.kanon.KeyAttestation}
 * object
 */
public class KeyAttestationFactory {
    private KeyAttestation mKeyAttestation;
    private Context mContext;

    public KeyAttestationFactory(Context context) {
        Objects.requireNonNull(context);
        mContext = context;
    }

    @VisibleForTesting
    KeyAttestationFactory(KeyAttestation keyAttestation) {
        Objects.requireNonNull(keyAttestation);
        mKeyAttestation = keyAttestation;
    }

    /** Returns an object of {@link com.android.adservices.service.kanon.KeyAttestation} */
    public KeyAttestation getKeyAttestation()
            throws KeyStoreException, NoSuchAlgorithmException, NoSuchProviderException {
        if (mKeyAttestation == null) {
            mKeyAttestation = KeyAttestation.create(mContext);
        }
        return mKeyAttestation;
    }

    private KeyAttestationFactory() {}
    ;
}
