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
import com.android.helpers.TimingHelper;

/**
 * A {@link TimingCollector} that reports lengths of time logged by a test under the
 * "ForTimingCollector" tag. Metric keys are determined by the test log and are arbitrary; metric
 * values are numerical (unit unspecified). For example:
 * - total-bugreport-duration-ms=148434
 * - total-test-duration=31
 *
 * <p>The key must be separated from the numerical metric by a single ':' character, and must be
 * logged under the "ForTimingCollector" tag. For example:
 * - Log.i("ForTimingCollector", "some-metric-key:123");
 */
@OptionClass(alias = "timingcollector")
public class TimingCollector extends BaseCollectionListener<Integer> {

    public TimingCollector() {
        createHelperInstance(new TimingHelper());
    }
}
