/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.cts.statsdatom.lib;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog;
import com.android.os.StatsLog.ConfigMetricsReport;
import com.android.os.StatsLog.ConfigMetricsReportList;
import com.android.os.StatsLog.EventMetricData;
import com.android.os.StatsLog.GaugeBucketInfo;
import com.android.os.StatsLog.GaugeMetricData;
import com.android.os.StatsLog.StatsLogReport;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.Pair;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class ReportUtils {
    private static final String DUMP_REPORT_CMD = "cmd stats dump-report";
    private static final long NS_PER_SEC = (long) 1E+9;

    /**
     * Returns a list of event metrics, which is sorted by timestamp, from the statsd report.
     * Note: Calling this function deletes the report from statsd.
     */
    public static List<EventMetricData> getEventMetricDataList(
            ITestDevice device, ExtensionRegistry extensionRegistry) throws Exception {
        ConfigMetricsReportList reportList = getReportList(device, extensionRegistry);
        return getEventMetricDataList(reportList);
    }

    public static List<EventMetricData> getEventMetricDataList(ITestDevice device)
            throws Exception {
        return getEventMetricDataList(device, ExtensionRegistry.getEmptyRegistry());
    }

    /**
     * Extracts and sorts the EventMetricData from the given ConfigMetricsReportList (which must
     * contain a single report) and sorts the atoms by timestamp within the report.
     */
    public static List<EventMetricData> getEventMetricDataList(ConfigMetricsReportList reportList)
            throws Exception {
        return getEventMetricDataList(reportList, /*reportIndex*/ 0);
    }

    /**
     * Returns a list of event metrics, which is sorted by timestamp, from the statsd report.
     * Note: Calling this function deletes the report from statsd.
     */
    public static List<EventMetricData> getEventMetricDataList(ITestDevice device,
            ExtensionRegistry extensionRegistry, int reportIndex) throws Exception {
        ConfigMetricsReportList reportList = getReportList(device, extensionRegistry);
        return getEventMetricDataList(reportList, reportIndex);
    }

    /**
     * Extracts and sorts the EventMetricData from the given ConfigMetricsReportList containing the
     * report at the specified index. The atoms are sorted by timestamp within the report.
     */
    public static List<EventMetricData> getEventMetricDataList(
            ConfigMetricsReportList reportList, int reportIndex) throws Exception {
        assertThat(reportList.getReportsCount()).isGreaterThan(reportIndex);
        ConfigMetricsReport report = reportList.getReports(reportIndex);

        List<EventMetricData> data = new ArrayList<>();
        for (StatsLogReport metric : report.getMetricsList()) {
            for (EventMetricData metricData :
                    metric.getEventMetrics().getDataList()) {
                if (metricData.hasAtom()) {
                    data.add(metricData);
                } else {
                    data.addAll(backfillAggregatedAtomsInEventMetric(metricData));
                }
            }
        }
        data.sort(Comparator.comparing(EventMetricData::getElapsedTimestampNanos));

        CLog.d("Get EventMetricDataList as following:\n");
        printEventMetricDataList(data);
        return data;
    }

    public static void printEventMetricDataList(List<EventMetricData> data) {
        for (EventMetricData d : data) {
            CLog.d("Atom at " + d.getElapsedTimestampNanos() + ":\n" + d.getAtom().toString());
        }
    }

    private static List<EventMetricData> backfillAggregatedAtomsInEventMetric(
            EventMetricData metricData) {
        if (!metricData.hasAggregatedAtomInfo()) {
            return Collections.emptyList();
        }
        List<EventMetricData> data = new ArrayList<>();
        StatsLog.AggregatedAtomInfo atomInfo = metricData.getAggregatedAtomInfo();
        for (long timestamp : atomInfo.getElapsedTimestampNanosList()) {
            data.add(EventMetricData.newBuilder()
                    .setAtom(atomInfo.getAtom())
                    .setElapsedTimestampNanos(timestamp)
                    .build());
        }
        return data;
    }

    public static List<Atom> getGaugeMetricAtoms(ITestDevice device) throws Exception {
        return getGaugeMetricAtoms(device, /*checkTimestampTruncated=*/false);
    }

    /**
     * Returns a list of gauge atoms from the statsd report. Assumes that there is only one bucket
     * for the gauge metric.
     * Note: calling this function deletes the report from statsd.
     *
     * @param extensionRegistry ExtensionRegistry containing extensions that should be parsed
     * @param checkTimestampTrucated if true, checks that atom timestmaps are properly truncated
     */
    public static List<Atom> getGaugeMetricAtoms(ITestDevice device,
            ExtensionRegistry extensionRegistry, boolean checkTimestampTruncated) throws Exception {
        ConfigMetricsReportList reportList = getReportList(device, extensionRegistry);
        assertThat(reportList.getReportsCount()).isEqualTo(1);
        ConfigMetricsReport report = reportList.getReports(0);
        assertThat(report.getMetricsCount()).isEqualTo(1);
        CLog.d("Got the following report: " + report.getMetrics(0).getGaugeMetrics().toString());
        List<Atom> atoms = new ArrayList<>();
        for (GaugeMetricData d : report.getMetrics(0).getGaugeMetrics().getDataList()) {
            assertThat(d.getBucketInfoCount()).isEqualTo(1);
            GaugeBucketInfo bucketInfo = d.getBucketInfo(0);
            if (bucketInfo.getAtomCount() != 0) {
                atoms.addAll(bucketInfo.getAtomList());
            } else {
                atoms.addAll(backFillGaugeBucketAtoms(bucketInfo.getAggregatedAtomInfoList()));
            }
            if (checkTimestampTruncated) {
                for (long timestampNs : bucketInfo.getElapsedTimestampNanosList()) {
                    assertTimestampIsTruncated(timestampNs);
                }
            }
        }

        CLog.d("Got the following GaugeMetric atoms:\n");
        for (Atom atom : atoms) {
            CLog.d("Atom:\n" + atom.toString());
        }
        return atoms;
    }

    public static List<Atom> getGaugeMetricAtoms(
            ITestDevice device, boolean checkTimestampTruncated) throws Exception {
        return getGaugeMetricAtoms(
                device, ExtensionRegistry.getEmptyRegistry(), checkTimestampTruncated);
    }

    private static List<Atom> backFillGaugeBucketAtoms(
            List<StatsLog.AggregatedAtomInfo> atomInfoList) {
        List<Pair<Atom, Long>> atomTimestamp = new ArrayList<>();
        for (StatsLog.AggregatedAtomInfo atomInfo : atomInfoList) {
            for (long timestampNs : atomInfo.getElapsedTimestampNanosList()) {
                atomTimestamp.add(Pair.create(atomInfo.getAtom(), timestampNs));
            }
        }
        return atomTimestamp.stream()
                .sorted(Comparator.comparing(o -> o.second))
                .map(p -> p.first).collect(Collectors.toList());
    }

    /**
     * Delete all pre-existing reports corresponding to the CTS config.
     */
    public static void clearReports(ITestDevice device) throws Exception {
        getReportList(device, ExtensionRegistry.getEmptyRegistry());
    }

    /**
     * Retrieves the ConfigMetricsReports corresponding to the CTS config from statsd.
     * Note: Calling this functions deletes the report from statsd.
     */
    private static ConfigMetricsReportList getReportList(
            ITestDevice device, ExtensionRegistry extensionRegistry) throws Exception {
        try {
            String cmd = String.join(" ", DUMP_REPORT_CMD, ConfigUtils.SHELL_UID_STRING,
                    ConfigUtils.CONFIG_ID_STRING, "--include_current_bucket", "--proto");
            ConfigMetricsReportList reportList = DeviceUtils.getShellCommandOutput(
                    device, ConfigMetricsReportList.parser(), extensionRegistry, cmd);
            return reportList;
        } catch (InvalidProtocolBufferException ex) {
            int hostUid = DeviceUtils.getHostUid(device);
            CLog.e("Failed to fetch and parse the statsd output report. Perhaps there is not a "
                    + "valid statsd config for the requested uid=" + hostUid + ", id="
                    + ConfigUtils.CONFIG_ID + ".");
            throw ex;
        }
    }

    /**
     * Checks that a timestamp has been truncated to a multiple of 5 min.
     */
    private static void assertTimestampIsTruncated(long timestampNs) {
        long fiveMinutesInNs = NS_PER_SEC * 5 * 60;
        assertWithMessage("Timestamp is not truncated")
                .that(timestampNs % fiveMinutesInNs).isEqualTo(0);
    }

    private ReportUtils() {}
}
