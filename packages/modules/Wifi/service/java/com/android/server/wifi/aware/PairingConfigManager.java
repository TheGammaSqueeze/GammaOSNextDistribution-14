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

package com.android.server.wifi.aware;

import android.util.Log;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The manager to store and maintain the NAN identity key and NPK/NIK caching
 */
public class PairingConfigManager {

    private static final String TAG = "AwarePairingManager";

    private static final int NIK_SIZE_IN_BYTE = 16;
    private static final int TAG_SIZE_IN_BYTE = 8;

    /**
     * Store the NPKSA from the NAN Pairing confirmation
     */
    public static class PairingSecurityAssociationInfo {
        public final byte[] mPeerNik;
        public final byte[] mNpk;
        public final int mAkm;
        public final byte[] mLocalNik;

        public final int mCipherSuite;

        public PairingSecurityAssociationInfo(byte[] peerNik, byte[] localNik, byte[] npk,
                int akm, int cipherSuite) {
            mPeerNik = peerNik;
            mNpk = npk;
            mAkm = akm;
            mLocalNik = localNik;
            mCipherSuite = cipherSuite;
        }
    }

    private final Map<String, byte[]> mPackageNameToNikMap = new HashMap<>();
    private final Map<String, Set<String>> mPerAppPairedAliasMap = new HashMap<>();
    private final Map<String, byte[]> mAliasToNikMap = new HashMap<>();
    private final Map<String, PairingSecurityAssociationInfo> mAliasToSecurityInfoMap =
            new HashMap<>();
    public PairingConfigManager() {
    }

    private byte[] createRandomNik() {
        long first, second;

        Random mRandom = new SecureRandom();
        first = mRandom.nextLong();
        second = mRandom.nextLong();
        ByteBuffer buffer = ByteBuffer.allocate(NIK_SIZE_IN_BYTE);
        buffer.putLong(first);
        buffer.putLong(second);
        return buffer.array();
    }

    /**
     * Get the NAN identity key of the app
     * @param packageName the package name of the calling App
     * @return the NAN identity key for this app
     */
    public byte[] getNikForCallingPackage(String packageName) {
        if (mPackageNameToNikMap.get(packageName) != null) {
            return mPackageNameToNikMap.get(packageName);
        }

        byte[] nik = createRandomNik();
        mPackageNameToNikMap.put(packageName, nik);
        return nik;
    }

    /**
     * Get the matched pairing device's alias set by the app
     */
    public String getPairedDeviceAlias(String packageName, byte[] nonce, byte[] tag, byte[] mac) {
        Set<String> aliasSet = mPerAppPairedAliasMap.get(packageName);
        if (aliasSet == null) {
            return null;
        }
        for (String alias : aliasSet) {
            if (checkMatchAlias(alias, nonce, tag, mac)) {
                return alias;
            }
        }
        return null;
    }

    private boolean checkMatchAlias(String alias, byte[] nonce, byte[] tag, byte[] mac) {
        byte[] nik = mAliasToNikMap.get(alias);
        byte[] nir = {'N', 'I', 'R'};
        if (nik == null) return false;
        SecretKeySpec spec = new SecretKeySpec(nik, "HmacSHA256");

        try {
            Mac hash = Mac.getInstance("HmacSHA256");
            hash.init(spec);
            hash.update(nir);
            hash.update(mac);
            hash.update(nonce);
            byte[] message = Arrays.copyOf(hash.doFinal(), TAG_SIZE_IN_BYTE);
            if (Arrays.equals(tag, message)) {
                return true;
            }

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Log.e(TAG, "Unexpected exception caught in getPairedDeviceAlias", e);
        }
        return false;
    }

    /**
     * Get the cached security info fo the target alias.
     */
    public PairingSecurityAssociationInfo getSecurityInfoPairedDevice(String alias) {
        return mAliasToSecurityInfoMap.get(alias);
    }

    /**
     * Add the NAN pairing sercurity info to the cache.
     */
    public void addPairedDeviceSecurityAssociation(String packageName, String alias,
            PairingSecurityAssociationInfo info) {
        if (info == null) {
            return;
        }
        Set<String> pairedDevices = mPerAppPairedAliasMap.get(packageName);
        if (pairedDevices == null) {
            pairedDevices = new HashSet<>();
            mPerAppPairedAliasMap.put(packageName, pairedDevices);
        }
        pairedDevices.add(alias);
        mAliasToNikMap.put(alias, info.mPeerNik);
        mAliasToSecurityInfoMap.put(alias, info);
    }

    /**
     * Remove all the caches related to the target calling App
     */
    public void removePackage(String packageName) {
        mPackageNameToNikMap.remove(packageName);
        Set<String> aliasSet = mPerAppPairedAliasMap.remove(packageName);
        if (aliasSet == null) {
            return;
        }
        for (String alias : aliasSet) {
            mAliasToNikMap.remove(alias);
            mAliasToSecurityInfoMap.remove(alias);
        }
    }

    /**
     * Remove the caches related to the target alias
     */
    public void removePairedDevice(String packageName, String alias) {
        mAliasToNikMap.remove(alias);
        mAliasToSecurityInfoMap.remove(alias);
        Set<String> aliasSet = mPerAppPairedAliasMap.remove(packageName);
        if (aliasSet == null) {
            return;
        }
        aliasSet.remove(alias);
    }

    /**
     * Get all paired devices alias for target calling app
     */
    public List<String> getAllPairedDevices(String callingPackage) {
        return new ArrayList<>(mPerAppPairedAliasMap.get(callingPackage));
    }
}
