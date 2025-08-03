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

package com.android.bedstead.metricsrecorder;

import static com.android.os.nano.AtomsProto.Atom.DEVICE_POLICY_EVENT_FIELD_NUMBER;

import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.framework.protobuf.nano.CodedOutputByteBufferNano;
import com.android.framework.protobuf.nano.InvalidProtocolBufferNanoException;
import com.android.framework.protobuf.nano.MessageNano;
import com.android.internal.os.nano.StatsdConfigProto.AtomMatcher;
import com.android.internal.os.nano.StatsdConfigProto.EventMetric;
import com.android.internal.os.nano.StatsdConfigProto.FieldValueMatcher;
import com.android.internal.os.nano.StatsdConfigProto.SimpleAtomMatcher;
import com.android.internal.os.nano.StatsdConfigProto.StatsdConfig;
import com.android.os.nano.AtomsProto;
import com.android.os.nano.StatsLog;
import com.android.os.nano.StatsLog.ConfigMetricsReportList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Metrics testing utility
 *
 * <p>Example usage:
 * <pre>{@code
 *     try (EnterpriseMetricsRecorder r = EnterpriseMetricsRecorder.create() {
 *         // Call code which generates metrics
 *
 *         assertThat(r.query().poll()).isNotNull();
 *     }
 *
 * }</pre>
 */
public class EnterpriseMetricsRecorder implements AutoCloseable {

    /** Create a {@link EnterpriseMetricsRecorder} and begin listening for metrics. */
    public static EnterpriseMetricsRecorder create() {
        EnterpriseMetricsRecorder r = new EnterpriseMetricsRecorder();
        r.start(DEVICE_POLICY_EVENT_FIELD_NUMBER);

        return r;
    }

    private static final long CONFIG_ID = "cts_config".hashCode();

    private final List<EnterpriseMetricInfo> mData = new ArrayList<>();

    private EnterpriseMetricsRecorder() {

    }

    private void start(int atomTag) {
        cleanLogs();
        createAndUploadConfig(atomTag);
    }

    /**
     * Begin querying the recorded metrics.
     */
    public MetricQueryBuilder query() {
        return new MetricQueryBuilder(this);
    }

    List<EnterpriseMetricInfo> fetchLatestData() {
        mData.addAll(getEventMetricDataList(getReportList()));
        return mData;
    }

    @Override
    public void close() {
        cleanLogs();
    }

    private void createAndUploadConfig(int atomTag) {
        StatsdConfig conf = new StatsdConfig();
        conf.id = CONFIG_ID;
        conf.allowedLogSource = new String[]{"AID_SYSTEM"};

        addAtomEvent(conf, atomTag);
        uploadConfig(conf);
    }

    private void addAtomEvent(StatsdConfig conf, int atomTag) {
        addAtomEvent(conf, atomTag, new ArrayList<>());
    }

    private void addAtomEvent(StatsdConfig conf, int atomTag,
            List<FieldValueMatcher> fvms) {
        String atomName = "Atom" + System.nanoTime();
        String eventName = "Event" + System.nanoTime();

        SimpleAtomMatcher sam = new SimpleAtomMatcher();
        sam.atomId = atomTag;
        if (fvms != null) {
            sam.fieldValueMatcher = fvms.toArray(new FieldValueMatcher[]{});
        }

        AtomMatcher atomMatcher = new AtomMatcher();
        atomMatcher.id = atomName.hashCode();
        atomMatcher.setSimpleAtomMatcher(sam);

        conf.atomMatcher = new AtomMatcher[]{
                atomMatcher
        };

        EventMetric eventMetric = new EventMetric();
        eventMetric.id = eventName.hashCode();
        eventMetric.what = atomName.hashCode();

        conf.eventMetric = new EventMetric[]{
                eventMetric
        };
    }

    private void uploadConfig(StatsdConfig config) {
        byte[] bytes = new byte[config.getSerializedSize()];
        CodedOutputByteBufferNano b = CodedOutputByteBufferNano.newInstance(bytes);
        try {
            ShellCommand.builder("cmd stats config update")
                    .addOperand(CONFIG_ID)
                    .writeToStdIn(MessageNano.toByteArray(config))
                    .validate(String::isEmpty)
                    .execute();
        } catch (AdbException e) {
            throw new NeneException("Error uploading config", e);
        }
    }

    private void cleanLogs() {
        removeConfig(CONFIG_ID);
        getReportList(); // Clears data.
    }

    private void removeConfig(long configId) {
        try {
            ShellCommand.builder("cmd stats config remove").addOperand(configId)
                    .validate(String::isEmpty).execute();
        } catch (AdbException e) {
            throw new NeneException("Error removing config " + configId, e);
        }
    }

    private ConfigMetricsReportList getReportList() {
        try {
            byte[] bytes = ShellCommand.builder("cmd stats dump-report")
                    .addOperand(CONFIG_ID)
                    .addOperand("--include_current_bucket")
                    .addOperand("--proto")
                    .forBytes()
                    .execute();

            return ConfigMetricsReportList.parseFrom(bytes);
        } catch (AdbException e) {
            throw new NeneException("Error getting stat report list", e);
        } catch (InvalidProtocolBufferNanoException e) {
            throw new NeneException("Invalid proto", e);
        }
    }

    private List<EnterpriseMetricInfo> getEventMetricDataList(
            ConfigMetricsReportList reportList) {
        return Arrays.stream(reportList.reports)
                .flatMap(s -> Arrays.stream(s.metrics.clone()))
                .filter(s -> s.getEventMetrics() != null && s.getEventMetrics().data != null)
                .flatMap(statsLogReport -> Arrays.stream(
                        statsLogReport.getEventMetrics().data.clone()))
                .flatMap(eventMetricData -> Arrays.stream(
                        backfillAggregatedAtomsinEventMetric(eventMetricData)))
                .sorted(Comparator.comparing(e -> e.elapsedTimestampNanos))
                .map(e -> e.atom)
                .filter((Objects::nonNull))
                .map(AtomsProto.Atom::getDevicePolicyEvent)
                .filter((Objects::nonNull))
                .map(EnterpriseMetricInfo::new)
                .collect(Collectors.toList());
    }

    private StatsLog.EventMetricData[] backfillAggregatedAtomsinEventMetric(
            StatsLog.EventMetricData metricData) {
        if (metricData.aggregatedAtomInfo == null) {
            return new StatsLog.EventMetricData[]{metricData};
        }
        List<StatsLog.EventMetricData> data = new ArrayList<>();
        StatsLog.AggregatedAtomInfo atomInfo = metricData.aggregatedAtomInfo;
        for (long timestamp : atomInfo.elapsedTimestampNanos) {
            StatsLog.EventMetricData newMetricData = new StatsLog.EventMetricData();
            newMetricData.atom = atomInfo.atom;
            newMetricData.elapsedTimestampNanos = timestamp;
            data.add(newMetricData);
        }
        return data.toArray(new StatsLog.EventMetricData[0]);
    }
}
