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

package com.android.rkpdapp.utils;

import android.util.Log;

import com.android.rkpdapp.database.ProvisionedKeyDao;

import java.time.Instant;

/**
 * Utility class to process and maintain the key count.
 */
public class StatsProcessor {
    public static final double LIMIT_SCALER = .4;

    private static final String TAG = "RkpdKeyPoolStats";

    private StatsProcessor() {}

    /**
     * Returns the minimum unassigned keys required to trigger a round of remote key provisioning.
     */
    public static int calcMinUnassignedToTriggerProvisioning(int extraSignedKeysAvailable) {
        return (int) Math.ceil(LIMIT_SCALER * extraSignedKeysAvailable);
    }

    /**
     * Creates a PoolStats. Takes an {@code ProvisionedKeyDao} and calculates different
     * pieces of status to inform the caller if any action needs to be taken to re-provision the
     * pool and what action is needed in terms of keys to generate.
     *
     * @param keyDao class to get current status of RKPD database.
     * @param extraSignedKeysAvailable how many extra attested keys should ideally be available
     *                                     for assignment.
     * @param irpcHal IRPC HAL for which we need to calculate pool status.
     * @return the PoolStats object describing higher level info about the state of the key pool.
     */
    public static PoolStats processPool(ProvisionedKeyDao keyDao, String irpcHal,
            int extraSignedKeysAvailable, Instant expirationTime) {
        PoolStats stats = new PoolStats();
        int totalKeys = keyDao.getTotalKeysForIrpc(irpcHal);
        int unassignedKeys = keyDao.getTotalUnassignedKeysForIrpc(irpcHal);
        int expiringKeys = keyDao.getTotalExpiringKeysForIrpc(irpcHal, expirationTime);
        stats.keysUnassigned = unassignedKeys;
        stats.keysInUse = totalKeys - unassignedKeys;
        // Need to generate the total number of keys in use along with the "slack" of extra signed
        // keys so that we always have extra keys when other clients decide to call us.
        stats.idealTotalSignedKeys = stats.keysInUse + extraSignedKeysAvailable;
        // If nothing is expiring, and the amount of available unassigned keys is sufficient,
        // then do nothing. Otherwise, generate the complete amount of idealTotalSignedKeys.
        //
        // It will reduce network usage if the app just provisions an entire new batch in one go,
        // rather than consistently grabbing just a few at a time as the expiration dates become
        // misaligned.
        boolean provisioningNeeded =
                (unassignedKeys - expiringKeys)
                <= calcMinUnassignedToTriggerProvisioning(extraSignedKeysAvailable);
        if (!provisioningNeeded) {
            Log.i(TAG, "Sufficient keys are available, no CSR needed.");
            stats.keysToGenerate = 0;
        } else {
            stats.keysToGenerate = stats.idealTotalSignedKeys;
        }
        Log.i(TAG, stats.toString());
        return stats;
    }

    /**
     * Actual stats for KeyPool
     */
    public static class PoolStats {
        public int keysInUse;
        public int idealTotalSignedKeys;
        public int keysToGenerate;
        public int keysUnassigned;

        @Override
        public String toString() {
            return "PoolStats{"
                    + "keysInUse=" + keysInUse
                    + ", idealTotalSignedKeys=" + idealTotalSignedKeys
                    + ", keysToGenerate=" + keysToGenerate
                    + ", keysUnassigned=" + keysUnassigned
                    + '}';
        }
    }
}
