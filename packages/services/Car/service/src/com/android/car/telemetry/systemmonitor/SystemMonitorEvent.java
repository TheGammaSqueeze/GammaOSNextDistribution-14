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

package com.android.car.telemetry.systemmonitor;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * System health events emitted by the SystemMonitor.
 */
public class SystemMonitorEvent {

    @IntDef(prefix = { "USAGE_LEVEL_" },
            value = { USAGE_LEVEL_LOW, USAGE_LEVEL_MED, USAGE_LEVEL_HI })

    @Retention(RetentionPolicy.SOURCE)
    public @interface UsageLevel {}

    public static final int USAGE_LEVEL_LOW = 0;

    public static final int USAGE_LEVEL_MED = 1;

    public static final int USAGE_LEVEL_HI = 2;

    private @UsageLevel int mCpuUsageLevel;

    private @UsageLevel int mMemoryUsageLevel;

    public void setCpuUsageLevel(@UsageLevel int usageLevel) {
        this.mCpuUsageLevel = usageLevel;
    }

    public void setMemoryUsageLevel(@UsageLevel int usageLevel) {
        this.mMemoryUsageLevel = usageLevel;
    }

    public @UsageLevel int getCpuUsageLevel() {
        return this.mCpuUsageLevel;
    }

    public @UsageLevel int getMemoryUsageLevel() {
        return this.mMemoryUsageLevel;
    }
}
