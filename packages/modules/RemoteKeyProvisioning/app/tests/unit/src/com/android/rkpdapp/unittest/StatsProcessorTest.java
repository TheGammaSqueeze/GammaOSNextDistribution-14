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

package com.android.rkpdapp.unittest;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.rkpdapp.database.ProvisionedKeyDao;
import com.android.rkpdapp.utils.StatsProcessor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class StatsProcessorTest {
    private static final String SERVICE_NAME = "not a service name";

    private ProvisionedKeyDao mKeyDao;

    @Before
    public void setUp() {
        mKeyDao = mock(ProvisionedKeyDao.class);
    }

    @Test
    public void testMinUnassignedBoundary() {
        int total = 10;
        int expiring = 0;
        int extra = 5;
        int unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(extra);
        assertThat(unassigned).isEqualTo(2);
        // Add an unassigned key to avoid the threshold for triggering reprovisioning.
        unassigned += 1;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(7, stats.keysInUse);
        assertEquals(12, stats.idealTotalSignedKeys);
        assertEquals(0, stats.keysToGenerate);
        // Now test provisioning needed boundary
        unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(extra);

        assertEquals(2, unassigned);
        when(mKeyDao.getTotalUnassignedKeysForIrpc(anyString())).thenReturn(unassigned);
        stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(8, stats.keysInUse);
        assertEquals(13, stats.idealTotalSignedKeys);
        assertEquals(13, stats.keysToGenerate);
    }

    @Test
    public void testStatsNoProvisioning() {
        int total = 10;
        int expiring = 0;
        int extra = 4;
        int unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(extra);
        assertEquals(2, unassigned);
        // Add an unassigned key to avoid the threshold for triggering reprovisioning.
        unassigned += 1;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(7, stats.keysInUse);
        assertEquals(11, stats.idealTotalSignedKeys);
        assertEquals(0, stats.keysToGenerate);
    }

    @Test
    public void testStatsProvisioning1() {
        int total = 10;
        int expiring = 0;
        int extra = 4;
        int unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(extra);
        assertEquals(2, unassigned);
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(8, stats.keysInUse);
        assertEquals(12, stats.idealTotalSignedKeys);
        assertEquals(12, stats.keysToGenerate);
    }

    @Test
    public void testStatsProvisioning2() {
        int total = 10;
        int expiring = 0;
        int extra = 4;
        int unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(extra);
        assertEquals(2, unassigned);
        unassigned -= 1;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(9, stats.keysInUse);
        assertEquals(13, stats.idealTotalSignedKeys);
        assertEquals(13, stats.keysToGenerate);
    }

    @Test
    public void testStatsProvisioning3() {
        int total = 15;
        int expiring = 0;
        int extra = 4;
        int unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(extra);
        assertEquals(2, unassigned);
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(13, stats.keysInUse);
        assertEquals(17, stats.idealTotalSignedKeys);
        assertEquals(17, stats.keysToGenerate);
    }

    @Test
    public void testExpiringProvisioningNeeded1() {
        int total = 10;
        int unassigned = 5;
        int expiring = 6;
        int extra = 4;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(9, stats.keysToGenerate);
    }

    @Test
    public void testExpiringProvisioningNeeded2() {
        int total = 10;
        int unassigned = 5;
        int expiring = 10;
        int extra = 4;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(9, stats.keysToGenerate);
    }

    @Test
    public void testExpiringProvisioningNeeded3() {
        int total = 10;
        int unassigned = 5;
        int expiring = 5;
        int extra = 4;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(9, stats.keysToGenerate);
    }

    @Test
    public void testExpiringProvisioningNeeded4() {
        int total = 10;
        int unassigned = 10;
        int expiring = 10;
        int extra = 4;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(0, stats.keysInUse);
        assertEquals(4, stats.idealTotalSignedKeys);
        assertEquals(4, stats.keysToGenerate);
    }

    @Test
    public void testExpiringProvisioningNeeded5() {
        int total = 10;
        int unassigned = 5;
        int expiring = 3;
        int extra = 4;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(9, stats.keysToGenerate);
    }

    @Test
    public void testExpiringProvisioningNotNeeded1() {
        int total = 10;
        int unassigned = 5;
        int expiring = 0;
        int extra = 4;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(0, stats.keysToGenerate);
    }

    @Test
    public void testExpiringProvisioningNotNeeded2() {
        int extra = 4;
        int total = 10;
        int unassigned = 5;
        int expiring = 2;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(0, stats.keysToGenerate);
    }

    @Test
    public void testExpiringProvisioningNeededSomeKeysPregenerated() {
        int total = 12;
        int unassigned = 5;
        int expiring = 6;
        int extra = 4;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(7, stats.keysInUse);
        assertEquals(11, stats.idealTotalSignedKeys);
        assertEquals(11, stats.keysToGenerate);
    }

    @Test
    public void testBothExpiringAndBelowMinimumExtraKeysAvailable() {
        int total = 10;
        int unassigned = 1;
        int expiring = 6;
        int extra = 5;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(9, stats.keysInUse);
        assertEquals(14, stats.idealTotalSignedKeys);
        assertEquals(14, stats.keysToGenerate);
    }

    @Test
    public void testBothExpiringAndBelowMinimumExtraKeysAvailableWithPreGenKeys() {
        int total = 14;
        int unassigned = 1;
        int expiring = 6;
        int extra = 5;
        StatsProcessor.PoolStats stats = prepareStats(total, unassigned, expiring, extra);
        assertEquals(13, stats.keysInUse);
        assertEquals(18, stats.idealTotalSignedKeys);
        assertEquals(18, stats.keysToGenerate);
    }

    private StatsProcessor.PoolStats prepareStats(int totalKeys, int unassignedKeys,
            int expiringKeys, int numExtraKeys) {
        when(mKeyDao.getTotalKeysForIrpc(anyString())).thenReturn(totalKeys);
        when(mKeyDao.getTotalUnassignedKeysForIrpc(anyString())).thenReturn(unassignedKeys);
        when(mKeyDao.getTotalExpiringKeysForIrpc(anyString(), notNull())).thenReturn(expiringKeys);
        return StatsProcessor.processPool(mKeyDao, SERVICE_NAME, numExtraKeys, Instant.now());
    }
}
