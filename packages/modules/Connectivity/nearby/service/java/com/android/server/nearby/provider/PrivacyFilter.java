/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.nearby.provider;

import android.annotation.Nullable;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.ScanRequest;

/**
 * Class strips out privacy sensitive data before delivering the callbacks to client.
 */
public class PrivacyFilter {

    /**
     * Strips sensitive data from {@link NearbyDeviceParcelable} according to
     * different {@link android.nearby.ScanRequest.ScanType}s.
     */
    @Nullable
    public static NearbyDeviceParcelable filter(@ScanRequest.ScanType int scanType,
            NearbyDeviceParcelable scanResult) {
        return scanResult;
    }
}
