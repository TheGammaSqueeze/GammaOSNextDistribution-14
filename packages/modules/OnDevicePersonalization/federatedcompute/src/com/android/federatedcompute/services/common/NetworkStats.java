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

package com.android.federatedcompute.services.common;

import java.util.concurrent.atomic.AtomicLong;

/** Collection of network usage statistics. */
public class NetworkStats {
    private final AtomicLong mTotalBytesDownloaded;
    private final AtomicLong mTotalBytesUploaded;

    public NetworkStats() {
        this.mTotalBytesUploaded = new AtomicLong(0);
        this.mTotalBytesDownloaded = new AtomicLong(0);
    }

    /** Adds download bytes to existing counter. */
    public void addBytesDownloaded(long bytesDownloaded) {
        mTotalBytesDownloaded.addAndGet(bytesDownloaded);
    }

    /** Adds upload bytes to existing counter. */
    public void addBytesUploaded(long bytesUploaded) {
        mTotalBytesUploaded.addAndGet(bytesUploaded);
    }

    /** Gets total download bytes. */
    public long getTotalBytesDownloaded() {
        return mTotalBytesDownloaded.get();
    }

    /** Gets total upload bytes. */
    public long getTotalBytesUploaded() {
        return mTotalBytesUploaded.get();
    }
}
