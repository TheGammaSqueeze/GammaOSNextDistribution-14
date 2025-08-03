/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.helpers;

import android.app.StatsManager;
import android.app.StatsManager.StatsUnavailableException;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.util.StatsLog;

import androidx.test.InstrumentationRegistry;

import com.android.internal.os.nano.StatsdConfigProto;
import com.android.os.nano.AtomsProto;

import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * StatsdHelper consist of basic utilities that will be used to setup statsd
 * config, parse the collected information and remove the statsd config.
 */
public class StatsdHelper {
    private static final String LOG_TAG = StatsdHelper.class.getSimpleName();
    private static final long MAX_ATOMS = 2000;
    private static final long METRIC_DELAY_MS = 3000;
    private static final long CONFIG_REGISTRATION_TIMEOUT_MS = 1000;
    private long mConfigId = -1;
    private StatsManager mStatsManager;

    /**
     * Add simple event configurations using a list of atom ids.
     *
     * @param atomIdList uniquely identifies the information that we need to track by statsManager.
     * @return true if the configuration is added successfully, otherwise false.
     */
    public boolean addEventConfig(List<Integer> atomIdList) {
        long configId = System.currentTimeMillis();
        StatsdConfigProto.StatsdConfig config = getSimpleSources(configId);
        List<StatsdConfigProto.EventMetric> metrics = new ArrayList<>(atomIdList.size());
        List<StatsdConfigProto.AtomMatcher> atomMatchers = new ArrayList<>(atomIdList.size());
        for (Integer atomId : atomIdList) {
            int atomUniqueId = getUniqueId();
            StatsdConfigProto.EventMetric metric = new StatsdConfigProto.EventMetric();
            metric.id = getUniqueId();
            metric.what = atomUniqueId;
            metrics.add(metric);
            atomMatchers.add(getSimpleAtomMatcher(atomUniqueId, atomId));
        }
        config.eventMetric = metrics.toArray(new StatsdConfigProto.EventMetric[0]);
        config.atomMatcher = atomMatchers.toArray(new StatsdConfigProto.AtomMatcher[0]);
        try {
            adoptShellIdentity();
            getStatsManager().addConfig(configId, toByteArray(config));
            if (!pollForRegisteredConfig(configId)) {
                return false;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Not able to setup the event config.", e);
            return false;
        } finally {
            dropShellIdentity();
        }
        Log.i(LOG_TAG, "Successfully added config with config-id:" + configId);
        setConfigId(configId);
        return true;
    }

    /**
     * Build gauge metric config based on trigger events (i.e AppBreadCrumbReported).
     * Whenever the events are triggered via StatsLog.logEvent() collect the gauge metrics.
     * It doesn't matter what the log event is. It could be 0 or 1.
     * In order to capture the usage during the test take the difference of gauge metrics
     * before and after the test.
     *
     * @param atomIdList List of atoms to be collected in gauge metrics.
     * @return if the config is added successfully otherwise false.
     */
    public boolean addGaugeConfig(List<Integer> atomIdList) {
        long configId = System.currentTimeMillis();
        StatsdConfigProto.StatsdConfig config = getSimpleSources(configId);
        int appBreadCrumbUniqueId = getUniqueId();
        config.whitelistedAtomIds =
                new int[] {AtomsProto.Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER};
        List<StatsdConfigProto.AtomMatcher> matchers = new ArrayList<>(atomIdList.size());
        List<StatsdConfigProto.GaugeMetric> gaugeMetrics = new ArrayList<>();
        // Needed for collecting gauge metric based on trigger events.
        matchers.add(
                getSimpleAtomMatcher(
                        appBreadCrumbUniqueId,
                        AtomsProto.Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER));
        for (Integer atomId : atomIdList) {
            int atomUniqueId = getUniqueId();
            // Build Gauge metric config.
            StatsdConfigProto.GaugeMetric gaugeMetric = new StatsdConfigProto.GaugeMetric();
            gaugeMetric.id = getUniqueId();
            gaugeMetric.what = atomUniqueId;
            StatsdConfigProto.FieldFilter fieldFilter = new StatsdConfigProto.FieldFilter();
            fieldFilter.includeAll = true;
            gaugeMetric.gaugeFieldsFilter = fieldFilter;
            gaugeMetric.maxNumGaugeAtomsPerBucket = MAX_ATOMS;
            gaugeMetric.samplingType = StatsdConfigProto.GaugeMetric.FIRST_N_SAMPLES;
            gaugeMetric.triggerEvent = appBreadCrumbUniqueId;
            gaugeMetric.bucket = StatsdConfigProto.CTS;
            matchers.add(getSimpleAtomMatcher(atomUniqueId, atomId));
            gaugeMetrics.add(gaugeMetric);
        }
        config.atomMatcher = matchers.toArray(new StatsdConfigProto.AtomMatcher[0]);
        config.gaugeMetric = gaugeMetrics.toArray(new StatsdConfigProto.GaugeMetric[0]);
        try {
            adoptShellIdentity();
            getStatsManager().addConfig(configId, toByteArray(config));
            if (!pollForRegisteredConfig(configId)) {
                return false;
            }
            StatsLog.logEvent(0);
            // Dump the counters before the test started.
            SystemClock.sleep(METRIC_DELAY_MS);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Not able to setup the gauge config.", e);
            return false;
        } finally {
            dropShellIdentity();
        }

        Log.i(LOG_TAG, "Successfully added config with config-id:" + configId);
        setConfigId(configId);
        return true;
    }

    /** Create simple atom matcher with the given id and the field id. */
    private StatsdConfigProto.AtomMatcher getSimpleAtomMatcher(int id, int fieldId) {
        StatsdConfigProto.AtomMatcher atomMatcher = new StatsdConfigProto.AtomMatcher();
        atomMatcher.id = id;
        StatsdConfigProto.SimpleAtomMatcher simpleAtomMatcher =
                new StatsdConfigProto.SimpleAtomMatcher();
        simpleAtomMatcher.atomId = fieldId;
        atomMatcher.setSimpleAtomMatcher(simpleAtomMatcher);
        return atomMatcher;
    }

    /**
     * Create a statsd config with the list of authorized source that can write metrics.
     *
     * @param configId unique id of the configuration tracked by StatsManager.
     */
    private static StatsdConfigProto.StatsdConfig getSimpleSources(long configId) {
        StatsdConfigProto.StatsdConfig config = new StatsdConfigProto.StatsdConfig();
        config.id = configId;
        String[] allowedLogSources =
                new String[] {
                    "AID_ROOT",
                    "AID_SYSTEM",
                    "AID_RADIO",
                    "AID_BLUETOOTH",
                    "AID_GRAPHICS",
                    "AID_STATSD",
                    "AID_INCIDENTD"
                };
        String[] defaultPullPackages =
                new String[] {"AID_SYSTEM", "AID_RADIO", "AID_STATSD", "AID_GPU_SERVICE"};
        int[] whitelistedAtomIds =
                new int[] {
                    AtomsProto.Atom.UI_INTERACTION_FRAME_INFO_REPORTED_FIELD_NUMBER,
                    AtomsProto.Atom.UI_ACTION_LATENCY_REPORTED_FIELD_NUMBER
                };
        config.allowedLogSource = allowedLogSources;
        config.defaultPullPackages = defaultPullPackages;
        config.whitelistedAtomIds = whitelistedAtomIds;
        return config;
    }

    /** Returns accumulated StatsdStats. */
    public com.android.os.nano.StatsLog.StatsdStatsReport getStatsdStatsReport() {
        com.android.os.nano.StatsLog.StatsdStatsReport report =
                new com.android.os.nano.StatsLog.StatsdStatsReport();
        try {
            adoptShellIdentity();
            byte[] serializedReports = getStatsManager().getStatsMetadata();
            report = com.android.os.nano.StatsLog.StatsdStatsReport.parseFrom(serializedReports);
            dropShellIdentity();
        } catch (InvalidProtocolBufferNanoException | StatsUnavailableException se) {
            Log.e(LOG_TAG, "Retrieving StatsdStats report failed.", se);
        }
        return report;
    }

    /** Returns the list of EventMetricData tracked under the config. */
    public List<com.android.os.nano.StatsLog.EventMetricData> getEventMetrics() {
        List<com.android.os.nano.StatsLog.EventMetricData> eventData = new ArrayList<>();
        com.android.os.nano.StatsLog.ConfigMetricsReportList reportList = null;
        try {
            if (getConfigId() != -1) {
                adoptShellIdentity();
                byte[] serializedReports = getStatsManager().getReports(getConfigId());
                reportList =
                        com.android.os.nano.StatsLog.ConfigMetricsReportList.parseFrom(
                                serializedReports);
                dropShellIdentity();
            }
        } catch (InvalidProtocolBufferNanoException | StatsUnavailableException se) {
            Log.e(LOG_TAG, "Retrieving event metrics failed.", se);
            return eventData;
        }

        if (reportList != null && reportList.reports.length > 0) {
            com.android.os.nano.StatsLog.ConfigMetricsReport configReport = reportList.reports[0];
            for (com.android.os.nano.StatsLog.StatsLogReport metric : configReport.metrics) {
                com.android.os.nano.StatsLog.StatsLogReport.EventMetricDataWrapper
                        eventMetricDataWrapper = metric.getEventMetrics();
                List<com.android.os.nano.StatsLog.EventMetricData> backfilledData =
                        new ArrayList<>();
                if (eventMetricDataWrapper != null) {
                    for (com.android.os.nano.StatsLog.EventMetricData eventMetricData :
                            eventMetricDataWrapper.data) {
                        backfilledData.addAll(backfillEventMetricData(eventMetricData));
                    }
                    backfilledData.sort(Comparator.comparing(d -> d.elapsedTimestampNanos));
                    eventData.addAll(backfilledData);
                }
            }
        }
        Log.i(LOG_TAG, "Number of events: " + eventData.size());
        return eventData;
    }

    /** Returns the list of GaugeMetric data tracked under the config. */
    public List<com.android.os.nano.StatsLog.GaugeMetricData> getGaugeMetrics() {
        com.android.os.nano.StatsLog.ConfigMetricsReportList reportList = null;
        List<com.android.os.nano.StatsLog.GaugeMetricData> gaugeData = new ArrayList<>();
        try {
            if (getConfigId() != -1) {
                adoptShellIdentity();
                StatsLog.logEvent(0);
                // Dump the the counters after the test completed.
                SystemClock.sleep(METRIC_DELAY_MS);
                reportList =
                        com.android.os.nano.StatsLog.ConfigMetricsReportList.parseFrom(
                                getStatsManager().getReports(getConfigId()));
                dropShellIdentity();
            }
        } catch (InvalidProtocolBufferNanoException | StatsUnavailableException se) {
            Log.e(LOG_TAG, "Retrieving gauge metrics failed.", se);
            return gaugeData;
        }

        if (reportList != null && reportList.reports.length > 0) {
            com.android.os.nano.StatsLog.ConfigMetricsReport configReport = reportList.reports[0];
            for (com.android.os.nano.StatsLog.StatsLogReport metric : configReport.metrics) {
                com.android.os.nano.StatsLog.StatsLogReport.GaugeMetricDataWrapper
                        gaugeMetricDataWrapper = metric.getGaugeMetrics();
                backfillGaugeMetricData(gaugeMetricDataWrapper);
                if (gaugeMetricDataWrapper != null) {
                    gaugeData.addAll(Arrays.asList(gaugeMetricDataWrapper.data));
                }
            }
        }
        Log.i(LOG_TAG, "Number of Gauge data: " + gaugeData.size());
        return gaugeData;
    }

    /**
     * Remove the existing config tracked in the statsd.
     *
     * @return true if the config is removed successfully otherwise false.
     */
    public boolean removeStatsConfig() {
        Log.i(LOG_TAG, "Removing statsd config-id: " + getConfigId());
        try {
            adoptShellIdentity();
            getStatsManager().removeConfig(getConfigId());
            dropShellIdentity();
            Log.i(LOG_TAG, "Successfully removed config-id: " + getConfigId());
            return true;
        } catch (StatsUnavailableException e) {
            Log.e(LOG_TAG, String.format("Not able to remove the config-id: %d due to %s ",
                    getConfigId(), e.getMessage()));
            return false;
        }
    }

    /** Returns the package name for the UID if it is available. Otherwise return null. */
    public String getPackageName(int uid) {
        String pkgName =
                InstrumentationRegistry.getTargetContext().getPackageManager().getNameForUid(uid);
        // Remove the UID appended at the end of the package name.
        if (pkgName != null) {
            String[] pkgNameSplit = pkgName.split(String.format("\\:%d", uid));
            return pkgNameSplit[0];
        }
        return pkgName;
    }

    private List<com.android.os.nano.StatsLog.EventMetricData> backfillEventMetricData(
            com.android.os.nano.StatsLog.EventMetricData metricData) {
        if (metricData.aggregatedAtomInfo == null) {
            return List.of(metricData);
        }
        List<com.android.os.nano.StatsLog.EventMetricData> data = new ArrayList<>();
        com.android.os.nano.StatsLog.AggregatedAtomInfo atomInfo = metricData.aggregatedAtomInfo;
        for (long timestamp : atomInfo.elapsedTimestampNanos) {
            com.android.os.nano.StatsLog.EventMetricData newMetricData =
                    new com.android.os.nano.StatsLog.EventMetricData();
            newMetricData.atom = atomInfo.atom;
            newMetricData.elapsedTimestampNanos = timestamp;
            data.add(newMetricData);
        }
        return data;
    }

    protected void backfillGaugeMetricData(
            com.android.os.nano.StatsLog.StatsLogReport.GaugeMetricDataWrapper dataWrapper) {
        if (dataWrapper == null) {
            return;
        }
        for (com.android.os.nano.StatsLog.GaugeMetricData gaugeMetricData : dataWrapper.data) {
            for (com.android.os.nano.StatsLog.GaugeBucketInfo bucketInfo :
                    gaugeMetricData.bucketInfo) {
                backfillGaugeBucket(bucketInfo);
            }
        }
    }

    private void backfillGaugeBucket(com.android.os.nano.StatsLog.GaugeBucketInfo bucketInfo) {
        if (bucketInfo.atom.length != 0) {
            return;
        }
        List<Pair<AtomsProto.Atom, Long>> atomTimestampData = new ArrayList<>();
        for (com.android.os.nano.StatsLog.AggregatedAtomInfo atomInfo :
                bucketInfo.aggregatedAtomInfo) {
            for (long timestampNs : atomInfo.elapsedTimestampNanos) {
                atomTimestampData.add(Pair.create(atomInfo.atom, timestampNs));
            }
        }
        atomTimestampData.sort(Comparator.comparing(o -> o.second));
        bucketInfo.atom = new AtomsProto.Atom[atomTimestampData.size()];
        bucketInfo.elapsedTimestampNanos = new long[atomTimestampData.size()];
        for (int i = 0; i < atomTimestampData.size(); i++) {
            bucketInfo.atom[i] = atomTimestampData.get(i).first;
            bucketInfo.elapsedTimestampNanos[i] = atomTimestampData.get(i).second;
        }
    }

    private boolean pollForRegisteredConfig(long configId) {
        long endTime = System.currentTimeMillis() + CONFIG_REGISTRATION_TIMEOUT_MS;
        while (System.currentTimeMillis() < endTime) {
            if (verifyConfigIsRegistered(configId)) {
                Log.i(LOG_TAG, String.format("Found config %d registered.", configId));
                return true;
            }
            SystemClock.sleep(100);
        }
        Log.e(
                LOG_TAG,
                String.format(
                        "Didn't find config registered after %d ms.",
                        CONFIG_REGISTRATION_TIMEOUT_MS));
        return false;
    }

    private boolean verifyConfigIsRegistered(long configId) {
        com.android.os.nano.StatsLog.StatsdStatsReport report = getStatsdStatsReport();
        for (com.android.os.nano.StatsLog.StatsdStatsReport.ConfigStats configStats :
                report.configStats) {
            if (configStats.id == configId
                    && configStats.isValid
                    && configStats.deletionTimeSec == 0) {
                return true;
            }
        }
        return false;
    }

    /** Gets {@code StatsManager}, used to configure, collect and remove the statsd configs. */
    private StatsManager getStatsManager() {
        if (mStatsManager == null) {
            mStatsManager = (StatsManager) InstrumentationRegistry.getTargetContext().
                    getSystemService(Context.STATS_MANAGER);
        }
        return mStatsManager;
    }

    /** Returns the package name associated with this UID if available, or null otherwise. */
    /**
     * Serializes a {@link StatsdConfigProto.StatsdConfig}.
     *
     * @return byte[]
     */
    private static byte[] toByteArray(StatsdConfigProto.StatsdConfig config) throws IOException {
        byte[] serialized = new byte[config.getSerializedSize()];
        CodedOutputByteBufferNano outputByteBufferNano =
                CodedOutputByteBufferNano.newInstance(serialized);
        config.writeTo(outputByteBufferNano);
        return serialized;
    }

    /** Sets the statsd config id currently tracked by this class. */
    private void setConfigId(long configId) {
        mConfigId = configId;
    }

    /** Returns the statsd config id currently tracked by this class. */
    private long getConfigId() {
        return mConfigId;
    }

    /** Returns a unique identifier using a {@code UUID}'s hashcode. */
    private static int getUniqueId() {
        return UUID.randomUUID().hashCode();
    }

    /**
     * Adopts shell permission identity needed to access StatsManager service
     */
    public static void adoptShellIdentity() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
    }

    /**
     * Drop shell permission identity
     */
    public static void dropShellIdentity() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

}
