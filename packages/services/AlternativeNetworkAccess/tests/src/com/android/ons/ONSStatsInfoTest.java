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

package com.android.ons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.ons.ONSProfileActivator.Result;
import com.android.ons.ONSProfileDownloader.DownloadRetryResultCode;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ONSStatsInfoTest {

    @Test
    public void testProvisioningResult() {
        ONSStatsInfo info;
        info = new ONSStatsInfo().setProvisioningResult(Result.ERR_AUTO_PROVISIONING_DISABLED);
        assertEquals(Result.ERR_AUTO_PROVISIONING_DISABLED, info.getProvisioningResult());
        assertNull(info.getDownloadResult());
        assertTrue(info.isProvisioningResultUpdated());
    }

    @Test
    public void testDownloadResult() {
        ONSStatsInfo info;
        info = new ONSStatsInfo().setDownloadResult(DownloadRetryResultCode.ERR_MEMORY_FULL);
        assertEquals(DownloadRetryResultCode.ERR_MEMORY_FULL, info.getDownloadResult());
        assertNull(info.getProvisioningResult());
        assertFalse(info.isProvisioningResultUpdated());
    }

    @Test
    public void testPrimarySimSubId() {
        ONSStatsInfo info;
        info = new ONSStatsInfo().setPrimarySimSubId(1);
        assertEquals(1, info.getPrimarySimSubId());
    }

    @Test
    public void testOppSimCarrierId() {
        ONSStatsInfo info;
        info = new ONSStatsInfo().setOppSimCarrierId(1221);
        assertEquals(1221, info.getOppSimCarrierId());
    }

    @Test
    public void testRetryCount() {
        ONSStatsInfo info;
        info = new ONSStatsInfo().setRetryCount(3);
        assertEquals(3, info.getRetryCount());
    }

    @Test
    public void testDetailedErrCode() {
        ONSStatsInfo info;
        info = new ONSStatsInfo().setDetailedErrCode(1000);
        assertEquals(1000, info.getDetailedErrCode());
    }

    @Test
    public void testIsWifiConnected() {
        ONSStatsInfo info;
        info = new ONSStatsInfo().setWifiConnected(true);
        assertTrue(info.isWifiConnected());
    }

    @Test
    public void testIsProvisioningResultUpdated() {
        ONSStatsInfo info;
        info = new ONSStatsInfo().setProvisioningResult(Result.ERR_ESIM_NOT_SUPPORTED);
        assertTrue(info.isProvisioningResultUpdated());

        info.setDownloadResult(DownloadRetryResultCode.ERR_MEMORY_FULL);
        assertFalse(info.isProvisioningResultUpdated());
    }
}
