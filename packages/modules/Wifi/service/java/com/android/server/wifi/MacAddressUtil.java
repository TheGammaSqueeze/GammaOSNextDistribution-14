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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.net.MacAddress;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.util.KeystoreWrapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.ProviderException;
import java.util.Arrays;

import javax.crypto.Mac;

/**
 * Contains helper methods to support MAC randomization.
 */
public class MacAddressUtil {
    private static final String TAG = "MacAddressUtil";
    @VisibleForTesting
    public static final String MAC_RANDOMIZATION_ALIAS = "MacRandSecret";
    @VisibleForTesting
    public static final String MAC_RANDOMIZATION_SAP_ALIAS = "MacRandSapSecret";
    private static final long MAC_ADDRESS_VALID_LONG_MASK = (1L << 48) - 1;
    private static final long MAC_ADDRESS_LOCALLY_ASSIGNED_MASK = 1L << 41;
    private static final long MAC_ADDRESS_MULTICAST_MASK = 1L << 40;
    private static final int ETHER_ADDR_LEN = 6;
    private final KeystoreWrapper mKeystoreWrapper;
    private Mac mMacForSta = null;
    private Mac mMacForSap = null;

    public MacAddressUtil(KeystoreWrapper keystoreWrapper) {
        mKeystoreWrapper = keystoreWrapper;
    }

    /**
     * Computes the persistent randomized MAC using the given key and hash function.
     * @param key the key to compute MAC address for
     * @param hashFunction the hash function that will perform the MAC address computation.
     * @return The persistent randomized MAC address or null if inputs are invalid.
     */
    private MacAddress calculatePersistentMacInternal(String key, Mac hashFunction) {
        if (key == null || hashFunction == null) {
            return null;
        }
        byte[] hashedBytes;
        try {
            hashedBytes = hashFunction.doFinal(key.getBytes(StandardCharsets.UTF_8));
        } catch (ProviderException | IllegalStateException e) {
            Log.e(TAG, "Failure in calculatePersistentMac", e);
            return null;
        }
        ByteBuffer bf = ByteBuffer.wrap(hashedBytes);
        long longFromSsid = bf.getLong();
        /**
         * Masks the generated long so that it represents a valid randomized MAC address.
         * Specifically, this sets the locally assigned bit to 1, multicast bit to 0
         */
        longFromSsid &= MAC_ADDRESS_VALID_LONG_MASK;
        longFromSsid |= MAC_ADDRESS_LOCALLY_ASSIGNED_MASK;
        longFromSsid &= ~MAC_ADDRESS_MULTICAST_MASK;
        bf.clear();
        bf.putLong(0, longFromSsid);

        // MacAddress.fromBytes requires input of length 6, which is obtained from the
        // last 6 bytes from the generated long.
        MacAddress macAddress = MacAddress.fromBytes(Arrays.copyOfRange(bf.array(), 2, 8));
        return macAddress;
    }

    private MacAddress calculatePersistentMacWithCachedHash(@NonNull String key, int uid,
            @NonNull String alias) {
        Mac hashFunction = getCachedHashFunction(alias);
        if (hashFunction != null) {
            // first try calculating the MacAddress using the cached hash function
            MacAddress macAddress = calculatePersistentMacInternal(key, hashFunction);
            if (macAddress != null) {
                return macAddress;
            }
            // intentional fallthrough if calculating MacAddress with the cached hash function fails
        }
        hashFunction = mKeystoreWrapper.getHmacSHA256ForUid(uid, alias);
        cacheHashFunction(alias, hashFunction);
        return calculatePersistentMacInternal(key, hashFunction);
    }

    /**
     * calculate the persistent randomized MAC for STA
     */
    public MacAddress calculatePersistentMacForSta(String key, int uid) {
        if (key == null) {
            return null;
        }
        return calculatePersistentMacWithCachedHash(key, uid, MAC_RANDOMIZATION_ALIAS);
    }

    /**
     * calculate the persistent randomized MAC for SoftAp
     */
    public MacAddress calculatePersistentMacForSap(String key, int uid) {
        if (key == null) {
            return null;
        }
        return calculatePersistentMacWithCachedHash(key, uid, MAC_RANDOMIZATION_SAP_ALIAS);
    }

    private Mac getCachedHashFunction(@NonNull String alias) {
        if (MAC_RANDOMIZATION_ALIAS.equals(alias)) {
            return mMacForSta;
        } else if (MAC_RANDOMIZATION_SAP_ALIAS.equals(alias)) {
            return mMacForSap;
        }
        return null;
    }

    private void cacheHashFunction(@NonNull String alias, Mac mac) {
        if (MAC_RANDOMIZATION_ALIAS.equals(alias)) {
            mMacForSta = mac;
        } else if (MAC_RANDOMIZATION_SAP_ALIAS.equals(alias)) {
            mMacForSap = mac;
        }
    }

    /**
     * Returns the next Mac address to the given Mac address.
     */
    public static MacAddress nextMacAddress(MacAddress mac) {
        byte[] bytes = mac.toByteArray();
        bytes[MacAddressUtil.ETHER_ADDR_LEN - 1] =
                (byte) ((bytes[MacAddressUtil.ETHER_ADDR_LEN - 1] + 1) & 0xff);
        return MacAddress.fromBytes(bytes);
    }
}
