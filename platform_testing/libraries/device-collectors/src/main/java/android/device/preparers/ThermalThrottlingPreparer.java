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
package android.device.preparers;

import android.device.collectors.BaseMetricListener;
import android.device.collectors.DataRecord;
import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import android.util.Log;

import com.android.helpers.ThermalThrottlingHelper;

import org.junit.runner.Description;
import org.junit.runner.Result;

@OptionClass(alias = "thermal-throttling-preparer")
public final class ThermalThrottlingPreparer extends BaseMetricListener {
    private static final String TAG = ThermalThrottlingPreparer.class.getSimpleName();
    private static final String EMUL_SEVERITY = "emul_severity";
    private static final String DEFAULT_EMUL_SEVERITY = "4"; // THERMAL_STATUS_CRITICAL

    private boolean mThrottlingStarted;
    private final ThermalThrottlingHelper mThermalThrottlingHelper = new ThermalThrottlingHelper();

    public ThermalThrottlingPreparer() {
        super();
    }

    @Override
    public void onTestRunStart(DataRecord runData, Description description) {
        Bundle args = getArgsBundle();

        int mEmulSeverity = Integer.parseInt(args.getString(EMUL_SEVERITY, DEFAULT_EMUL_SEVERITY));
        mThrottlingStarted = mThermalThrottlingHelper.startThrottling(mEmulSeverity);
        if (!mThrottlingStarted) {
            throw new IllegalStateException("Failed to start emulating thermal throttling.");
        }
    }

    @Override
    public void onTestRunEnd(DataRecord runData, Result result) {
        if (!mThrottlingStarted) {
            Log.i(TAG, "Skipping thermal throttling stop attempt because it never started.");
            return;
        }
        if (!mThermalThrottlingHelper.stopThrottling()) {
            Log.e(TAG, "Failed to stop emulating thermal throttling.");
        }
    }
}
