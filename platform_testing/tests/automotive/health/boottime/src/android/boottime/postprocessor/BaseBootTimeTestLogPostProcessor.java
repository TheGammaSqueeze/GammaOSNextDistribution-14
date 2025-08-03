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

package android.boottime.postprocessor;

import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.postprocessor.BasePostProcessor;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class BaseBootTimeTestLogPostProcessor extends BasePostProcessor {
    protected static final String DMESG_BOOT_COMPLETE_TIME =
            "dmesg_action_sys.boot_completed_first_timestamp";

    @Option(name = "file-regex", description = "Regex for identifying a logcat file name.")
    protected Set<String> mFileRegex = new HashSet<>();

    /** {@inheritDoc} */
    @Override
    public Map<String, MetricMeasurement.Metric.Builder> processTestMetricsAndLogs(
            TestDescription testDescription,
            HashMap<String, MetricMeasurement.Metric> testMetrics,
            Map<String, LogFile> testLogs) {
        return new HashMap<>();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, MetricMeasurement.Metric.Builder> processRunMetricsAndLogs(
            HashMap<String, MetricMeasurement.Metric> rawMetrics, Map<String, LogFile> runLogs) {
        return new HashMap<>();
    }

    /** {@inheritDoc} */
    /**
     * Returns {@link MetricMeasurement.DataType.RAW} for metrics reported by the post processor.
     * RAW is required in order for {@link
     * com.android.tradefed.postprocessor.MetricFilePostProcessor} to aggregate the values
     */
    @Override
    protected MetricMeasurement.DataType getMetricType() {
        // Return raw metrics in order for MetricFilePostProcessor to aggregate
        return MetricMeasurement.DataType.RAW;
    }

    /**
     * Build TradeFed metrics from raw Double values.
     *
     * @param metrics contains a map of {@link Collection} each single value represents a metric for
     *     a particular boot iteration
     * @return Map with metric keys and stringified double values joined by comma
     */
    protected Map<String, MetricMeasurement.Metric.Builder> buildTfMetrics(
            Map<String, Collection<Double>> metrics) {
        Map<String, MetricMeasurement.Metric.Builder> tfMetrics = new HashMap<>();

        LogUtil.CLog.v("Collected %d metrics", metrics.size());
        for (Map.Entry<String, Collection<Double>> entry : metrics.entrySet()) {
            String stringValue =
                    entry.getValue().stream()
                            .map(value -> value.toString())
                            .collect(Collectors.joining(","));
            MetricMeasurement.Measurements.Builder measurement =
                    MetricMeasurement.Measurements.newBuilder().setSingleString(stringValue);
            MetricMeasurement.Metric.Builder metricBuilder =
                    MetricMeasurement.Metric.newBuilder().setMeasurements(measurement);
            tfMetrics.put(entry.getKey(), metricBuilder);
        }
        return tfMetrics;
    }

    protected List<File> filterFiles(Map<String, LogFile> logs) {
        List<File> files = new ArrayList<>();
        for (Map.Entry<String, LogFile> entry : logs.entrySet()) {
            LogUtil.CLog.v("Filtering log file %s", entry.getKey());
            Optional<String> match =
                    mFileRegex.stream().filter(regex -> entry.getKey().matches(regex)).findAny();
            if (match.isPresent()) {
                LogUtil.CLog.d(
                        "Found match testLog file %s at %s",
                        entry.getKey(), entry.getValue().getPath());
                files.add(new File(entry.getValue().getPath()));
            }
        }
        files.sort(Comparator.comparing(File::getName));
        return files;
    }
}
