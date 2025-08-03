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

package com.android.bluetooth.le_scan;

import android.bluetooth.le.IScannerCallback;

import com.android.bluetooth.gatt.ContextMap;
import com.android.bluetooth.gatt.GattService;
import com.android.internal.annotations.VisibleForTesting;

/**
 * A helper class which contains all scan related functions extracted from {@link
 * com.android.bluetooth.gatt.GattService}. The purpose of this class is to preserve scan
 * functionality within GattService and provide the same functionality in a new scan dedicated
 * {@link com.android.bluetooth.btservice.ProfileService} when introduced.
 *
 * @hide
 */
public class TransitionalScanHelper {

    /** List of our registered scanners. */
    public static class ScannerMap
            extends ContextMap<IScannerCallback, GattService.PendingIntentInfo> {}

    private ScannerMap mScannerMap = new ScannerMap();

    public ScannerMap getScannerMap() {
        return mScannerMap;
    }

    @VisibleForTesting
    public void setScannerMap(ScannerMap scannerMap) {
        mScannerMap = scannerMap;
    }
}
