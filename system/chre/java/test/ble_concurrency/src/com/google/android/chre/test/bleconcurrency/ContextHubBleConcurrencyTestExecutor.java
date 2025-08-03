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

package com.google.android.chre.test.bleconcurrency;

import android.hardware.location.NanoAppBinary;

import com.google.android.chre.test.chqts.ContextHubBleTestExecutor;

/**
 * A class that can execute the CHRE BLE concurrency test.
 */
public class ContextHubBleConcurrencyTestExecutor extends ContextHubBleTestExecutor {
    private static final String TAG = "ContextHubBleConcurrencyTestExecutor";

    public ContextHubBleConcurrencyTestExecutor(NanoAppBinary nanoapp) {
        super(nanoapp);
    }

    /**
     * Runs the test.
     */
    public void run() throws Exception {
        if (doesNecessaryBleCapabilitiesExist()) {
            testHostScanFirst();
            Thread.sleep(1000);
            testChreScanFirst();
        }
    }

    /**
     * Tests with the host starting scanning first.
     */
    private void testHostScanFirst() throws Exception {
        startBleScanOnHost();
        chreBleStartScanSync(getDefaultScanFilter());
        Thread.sleep(1000);
        chreBleStopScanSync();
        stopBleScanOnHost();
    }

    /**
     * Tests with CHRE starting scanning first.
     */
    private void testChreScanFirst() throws Exception {
        chreBleStartScanSync(getDefaultScanFilter());
        startBleScanOnHost();
        Thread.sleep(1000);
        stopBleScanOnHost();
        chreBleStopScanSync();
    }
}
