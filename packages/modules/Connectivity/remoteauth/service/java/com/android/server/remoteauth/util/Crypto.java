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

package com.android.server.remoteauth.util;

import android.util.Log;

import androidx.annotation.Nullable;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Utility class of cryptographic functions. */
public final class Crypto {
    private static final String TAG = "Crypto";
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    /**
     * A HAMC sha256 based HKDF algorithm to pseudo randomly hash data and salt into a byte array of
     * given size.
     *
     * @param ikm the input keying material.
     * @param salt A possibly non-secret random value.
     * @param size The length of the generated pseudorandom string in bytes. The maximal size is
     *     255.DigestSize, where DigestSize is the size of the underlying HMAC.
     * @return size pseudorandom bytes, null if failed.
     */
    // Based on
    // google3/third_party/tink/java_src/src/main/java/com/google/crypto/tink/subtle/Hkdf.java
    @Nullable
    public static byte[] computeHkdf(byte[] ikm, byte[] salt, int size) {
        Mac mac;
        try {
            mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            Log.w(TAG, "HMAC_SHA256_ALGORITHM is not supported.", e);
            return null;
        }

        if (size > 255 * mac.getMacLength()) {
            Log.w(TAG, "Size too large. " + size + " > " + 255 * mac.getMacLength());
            return null;
        }

        if (ikm == null || ikm.length == 0) {
            Log.w(TAG, "Ikm cannot be empty.");
            return null;
        }

        if (salt == null || salt.length == 0) {
            Log.w(TAG, "Salt cannot be empty.");
            return null;
        }

        try {
            mac.init(new SecretKeySpec(salt, HMAC_SHA256_ALGORITHM));
        } catch (InvalidKeyException e) {
            Log.w(TAG, "Invalid key.", e);
            return null;
        }

        byte[] prk = mac.doFinal(ikm);
        byte[] result = new byte[size];
        try {
            mac.init(new SecretKeySpec(prk, HMAC_SHA256_ALGORITHM));
        } catch (InvalidKeyException e) {
            Log.w(TAG, "Invalid key.", e);
            return null;
        }

        byte[] digest = new byte[0];
        int ctr = 1;
        int pos = 0;
        while (true) {
            mac.update(digest);
            mac.update((byte) ctr);
            digest = mac.doFinal();
            if (pos + digest.length < size) {
                System.arraycopy(digest, 0, result, pos, digest.length);
                pos += digest.length;
                ctr++;
            } else {
                System.arraycopy(digest, 0, result, pos, size - pos);
                break;
            }
        }

        return result;
    }
}
