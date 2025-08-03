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

package android.device.collectors;

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import com.android.helpers.BugReportDurationHelper;

/**
 * A {@link BugReportDurationCollector} that captures the length of time that a bug report takes
 * during (not after) a test run, as well as the durations of its component sections. Metric values
 * are given in seconds, for example:
 * - bugreport-duration-dumpstate_board()=45.104
 * - bugreport-duration-for_each_pid(smaps-of-all-processes)=18.198
 */
@OptionClass(alias = "bugreport-duration-collector")
public class BugReportDurationCollector extends BaseCollectionListener<Float> {

    private static final String BR_DIR = "/bugreports/";

    public BugReportDurationCollector() {
        createHelperInstance(new BugReportDurationHelper(BR_DIR));
    }

    @VisibleForTesting
    BugReportDurationCollector(Bundle args, BugReportDurationHelper helper) {
        super(args, helper);
    }
}
