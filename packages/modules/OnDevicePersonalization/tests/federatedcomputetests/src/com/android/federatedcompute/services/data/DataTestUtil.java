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

package com.android.federatedcompute.services.data;

import static com.google.common.truth.Truth.assertThat;

/** Helpers for data related testing. */
public final class DataTestUtil {
    private DataTestUtil() {}

    public static boolean isEqualTask(FederatedTrainingTask task1, FederatedTrainingTask task2) {
        assertThat(task1.jobId()).isEqualTo(task2.jobId());
        assertThat(task1.appPackageName()).isEqualTo(task2.appPackageName());
        assertThat(task1.populationName()).isEqualTo(task2.populationName());
        assertThat(task1.constraints()).isEqualTo(task2.constraints());
        assertThat(task1.intervalOptions()).isEqualTo(task2.intervalOptions());
        assertThat(task1.creationTime()).isEqualTo(task2.creationTime());
        assertThat(task1.lastScheduledTime()).isEqualTo(task2.lastScheduledTime());
        assertThat(task1.lastRunStartTime()).isEqualTo(task2.lastRunStartTime());
        assertThat(task1.lastRunEndTime()).isEqualTo(task2.lastRunEndTime());
        assertThat(task1.earliestNextRunTime()).isEqualTo(task2.earliestNextRunTime());
        assertThat(task1.schedulingReason()).isEqualTo(task2.schedulingReason());
        return true;
    }
}
