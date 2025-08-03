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

package com.android.adservices.service.js;

import androidx.javascriptengine.JavaScriptIsolate;

import com.android.internal.util.Preconditions;

/** Class used to set startup parameters for {@link JavaScriptIsolate}. */
public final class IsolateSettings {
    private final long mMaxHeapSizeBytes;
    private final boolean mEnforceMaxHeapSizeFeature;

    /**
     * Constructor to set the initial values of isolate settings
     *
     * @param enforceMaxHeapSizeFeature boolean value if the max heap size restriction should be
     *     enforced
     * @param maxHeapSizeBytes value of the max heap memory size
     */
    private IsolateSettings(boolean enforceMaxHeapSizeFeature, long maxHeapSizeBytes) {
        Preconditions.checkArgument(maxHeapSizeBytes >= 0, "maxHeapSizeBytes should be >= 0");
        this.mEnforceMaxHeapSizeFeature = enforceMaxHeapSizeFeature;
        this.mMaxHeapSizeBytes = maxHeapSizeBytes;
    }

    /**
     * Gets the max heap size used by the {@link JavaScriptIsolate}.
     *
     * <p>The default value is 0 which indicates no heap size limit.
     *
     * @return heap size in bytes
     */
    public long getMaxHeapSizeBytes() {
        return mMaxHeapSizeBytes;
    }

    /**
     * Gets the condition if the Max Heap feature is enforced for JS Isolate
     *
     * <p>The default value is false
     *
     * @return boolean value stating if the feature is enforced
     */
    public boolean getEnforceMaxHeapSizeFeature() {
        return mEnforceMaxHeapSizeFeature;
    }

    /** Creates setting for which memory restrictions are not enforced */
    public static IsolateSettings forMaxHeapSizeEnforcementDisabled() {
        return new IsolateSettings(false, 0);
    }

    /** Creates setting for which memory restrictions are enforced and sets the max heap memory */
    public static IsolateSettings forMaxHeapSizeEnforcementEnabled(long maxHeapSizeBytes) {
        return new IsolateSettings(true, maxHeapSizeBytes);
    }
}
