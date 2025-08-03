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
package android.device.preparers;

import android.device.collectors.BaseMetricListener;
import android.device.collectors.DataRecord;
import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import android.util.Log;
import com.android.helpers.MemhogHelper;

import org.junit.runner.Description;
import org.junit.runner.Result;

@OptionClass(alias = "memhog-preparer")
public final class MemhogPreparer extends BaseMetricListener {
    private static final String TAG = MemhogPreparer.class.getSimpleName();
    private static final String MEMORY_SIZE_BYTES = "memory_size_bytes";
    private static final String DEFAULT_MEMORY_SIZE_BYTES = "1337";

    private boolean mMemhogStarted;

    private final MemhogHelper mMemhogHelper = new MemhogHelper();

    public MemhogPreparer() {
        super();
    }

    @Override
    public void onTestRunStart(DataRecord runData, Description description) {
        Bundle args = getArgsBundle();

        // Amount of memory (in bytes) to be allocated.
        long mMemorySizeBytes =
                Long.parseLong(args.getString(MEMORY_SIZE_BYTES, DEFAULT_MEMORY_SIZE_BYTES));
        mMemhogStarted = mMemhogHelper.startMemhog(mMemorySizeBytes);
        if (!mMemhogStarted) {
            throw new IllegalStateException("Memhog failed to start.");
        }
    }

    @Override
    public void onTestRunEnd(DataRecord runData, Result result) {
        if (!mMemhogStarted) {
            Log.i(TAG, "Skipping memhog stop attempt because it never started.");
            return;
        }
        if (!mMemhogHelper.stopMemhog()) {
            Log.e(TAG, "Memhog failed to stop.");
        }
    }
}
