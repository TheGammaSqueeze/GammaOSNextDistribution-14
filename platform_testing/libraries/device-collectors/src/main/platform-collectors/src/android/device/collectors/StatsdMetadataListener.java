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
package android.device.collectors;

import android.device.collectors.annotations.OptionClass;

import com.android.helpers.StatsdStatsHelper;

/**
 * A {@link StatsdMetadataListener} that captures statsd metadata during test method.
 *
 * <p>The StatsdMetadataListener with {@link StatsdStatsHelper} support collects various statsd
 * metadata information. This information will be populated into metrics with a prefix
 * "statsdstats_" The metrics are about statsd logged atoms statistics, matchers and atom pullers
 * such as below but not limited to: - atom stats (count, errors) - config stats (metrics,
 * conditions, matcher & alerts count, and many more) - pulled atoms statsd (total pull count, data
 * error, pull timeouts, etc.) Full list of available metrics is defined in {@link
 * StatsdStatsHelper}
 *
 * <p>Do NOT throw exception anywhere in this class. We don't want to halt the test when metrics
 * collection fails.
 */
@OptionClass(alias = "statsdstats-collector")
public class StatsdMetadataListener extends BaseCollectionListener<Long> {
    public StatsdMetadataListener() {
        createHelperInstance(new StatsdStatsHelper());
    }
}
