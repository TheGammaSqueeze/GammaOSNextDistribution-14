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

package com.android.car.telemetry.publisher;

import static com.android.car.telemetry.AtomsProto.Atom.ACTIVITY_FOREGROUND_STATE_CHANGED_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.Atom.ANR_OCCURRED_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.Atom.APP_CRASH_OCCURRED_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.Atom.APP_START_MEMORY_STATE_CAPTURED_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.Atom.PROCESS_CPU_TIME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.Atom.PROCESS_MEMORY_SNAPSHOT_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.Atom.PROCESS_MEMORY_STATE_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.Atom.PROCESS_START_TIME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.Atom.WTF_OCCURRED_FIELD_NUMBER;
import static com.android.car.telemetry.CarTelemetryService.DEBUG;

import static java.nio.charset.StandardCharsets.UTF_16;

import android.annotation.NonNull;
import android.app.StatsManager.StatsUnavailableException;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimingsTraceLog;
import android.car.telemetry.TelemetryProto;
import android.car.telemetry.TelemetryProto.Publisher.PublisherCase;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.Process;
import android.util.LongSparseArray;

import com.android.car.CarLog;
import com.android.car.telemetry.AtomsProto.ProcessCpuTime;
import com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot;
import com.android.car.telemetry.AtomsProto.ProcessMemoryState;
import com.android.car.telemetry.ResultStore;
import com.android.car.telemetry.StatsLogProto;
import com.android.car.telemetry.StatsdConfigProto;
import com.android.car.telemetry.StatsdConfigProto.StatsdConfig;
import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.car.telemetry.publisher.statsconverters.ConfigMetricsReportListConverter;
import com.android.car.telemetry.publisher.statsconverters.StatsConversionException;
import com.android.car.telemetry.sessioncontroller.SessionAnnotation;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import com.google.protobuf.InvalidProtocolBufferException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Publisher for {@link TelemetryProto.StatsPublisher}.
 *
 * <p>The publisher adds subscriber configurations in StatsD and they persist between reboots and
 * CarTelemetryService restarts. Please use {@link #removeAllDataSubscribers} to clean-up these
 * configs from StatsD store.
 */
public class StatsPublisher extends AbstractPublisher {
    // These IDs are used in StatsdConfig and ConfigMetricsReport.
    @VisibleForTesting
    static final long APP_START_MEMORY_STATE_CAPTURED_ATOM_MATCHER_ID = 1;
    @VisibleForTesting
    static final long APP_START_MEMORY_STATE_CAPTURED_EVENT_METRIC_ID = 2;
    @VisibleForTesting
    static final long PROCESS_MEMORY_STATE_MATCHER_ID = 3;
    @VisibleForTesting
    static final long PROCESS_MEMORY_STATE_GAUGE_METRIC_ID = 4;
    @VisibleForTesting
    static final long ACTIVITY_FOREGROUND_STATE_CHANGED_ATOM_MATCHER_ID = 5;
    @VisibleForTesting
    static final long ACTIVITY_FOREGROUND_STATE_CHANGED_EVENT_METRIC_ID = 6;
    @VisibleForTesting
    static final long PROCESS_CPU_TIME_MATCHER_ID = 7;
    @VisibleForTesting
    static final long PROCESS_CPU_TIME_GAUGE_METRIC_ID = 8;
    @VisibleForTesting
    static final long APP_CRASH_OCCURRED_ATOM_MATCHER_ID = 9;
    @VisibleForTesting
    static final long APP_CRASH_OCCURRED_EVENT_METRIC_ID = 10;
    @VisibleForTesting
    static final long ANR_OCCURRED_ATOM_MATCHER_ID = 11;
    @VisibleForTesting
    static final long ANR_OCCURRED_EVENT_METRIC_ID = 12;
    @VisibleForTesting
    static final long WTF_OCCURRED_ATOM_MATCHER_ID = 13;
    @VisibleForTesting
    static final long WTF_OCCURRED_EVENT_METRIC_ID = 14;
    @VisibleForTesting
    static final long PROCESS_MEMORY_SNAPSHOT_ATOM_MATCHER_ID = 15;
    @VisibleForTesting
    static final long PROCESS_MEMORY_SNAPSHOT_GAUGE_METRIC_ID = 16;
    @VisibleForTesting
    static final long PROCESS_START_TIME_ATOM_MATCHER_ID = 17;
    @VisibleForTesting
    static final long PROCESS_START_TIME_EVENT_METRIC_ID = 18;

    // TODO(b/202115033): Flatten the load spike by pulling reports for each MetricsConfigs
    //                    using separate periodical timers.
    private static final Duration PULL_REPORTS_PERIOD = Duration.ofMinutes(10);

    private static final String BUNDLE_CONFIG_KEY_PREFIX = "statsd-publisher-config-id-";
    private static final String BUNDLE_CONFIG_VERSION_PREFIX = "statsd-publisher-config-version-";

    @VisibleForTesting
    static final StatsdConfigProto.FieldMatcher PROCESS_MEMORY_STATE_FIELDS_MATCHER =
            StatsdConfigProto.FieldMatcher.newBuilder()
                    .setField(
                            PROCESS_MEMORY_STATE_FIELD_NUMBER)
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemoryState.OOM_ADJ_SCORE_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemoryState.PAGE_FAULT_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemoryState.PAGE_MAJOR_FAULT_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemoryState.RSS_IN_BYTES_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemoryState.CACHE_IN_BYTES_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemoryState.SWAP_IN_BYTES_FIELD_NUMBER))
                    .build();

    @VisibleForTesting
    static final StatsdConfigProto.FieldMatcher PROCESS_CPU_TIME_FIELDS_MATCHER =
            StatsdConfigProto.FieldMatcher.newBuilder()
                    .setField(PROCESS_CPU_TIME_FIELD_NUMBER)
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessCpuTime.USER_TIME_MILLIS_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessCpuTime.SYSTEM_TIME_MILLIS_FIELD_NUMBER))
                    .build();

    @VisibleForTesting
    static final StatsdConfigProto.FieldMatcher PROCESS_MEMORY_SNAPSHOT_FIELDS_MATCHER =
            StatsdConfigProto.FieldMatcher.newBuilder()
                    .setField(
                            PROCESS_MEMORY_SNAPSHOT_FIELD_NUMBER)
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemorySnapshot.PID_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemorySnapshot.OOM_SCORE_ADJ_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemorySnapshot.RSS_IN_KILOBYTES_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemorySnapshot.ANON_RSS_IN_KILOBYTES_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemorySnapshot.SWAP_IN_KILOBYTES_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemorySnapshot
                                    .ANON_RSS_AND_SWAP_IN_KILOBYTES_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemorySnapshot.GPU_MEMORY_KB_FIELD_NUMBER))
                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                            .setField(ProcessMemorySnapshot.HAS_FOREGROUND_SERVICES_FIELD_NUMBER))
                    .build();

    private final StatsManagerProxy mStatsManager;
    private final ResultStore mResultStore;
    private final Handler mTelemetryHandler;

    /** Assign the method to {@link Runnable}, otherwise the handler fails to remove it. */
    private final Runnable mPullReportsPeriodically = this::pullReportsPeriodically;

    // LongSparseArray is memory optimized, but they can be bit slower for more
    // than 100 items. We're expecting much less number of subscribers, so these data structures
    // are ok.
    // Maps config_key to the set of DataSubscriber.
    private final LongSparseArray<DataSubscriber> mConfigKeyToSubscribers = new LongSparseArray<>();

    private PersistableBundle mSavedStatsConfigs;

    // True if the publisher is periodically pulling reports from StatsD.
    private boolean mIsPullingReports = false;

    StatsPublisher(
            @NonNull PublisherListener listener,
            @NonNull StatsManagerProxy statsManager,
            @NonNull ResultStore resultStore,
            @NonNull Handler telemetryHandler) {
        super(listener);
        mStatsManager = statsManager;
        mResultStore = resultStore;
        mTelemetryHandler = telemetryHandler;
        // Loads the PersistableBundle containing stats config keys and versions from disk
        mSavedStatsConfigs = mResultStore.getPublisherData(
                StatsPublisher.class.getSimpleName(), false);
        if (mSavedStatsConfigs == null) {
            mSavedStatsConfigs = new PersistableBundle();
        }
    }

    /** Writes the PersistableBundle containing stats config keys and versions to disk. */
    private void savePublisherState() {
        if (mSavedStatsConfigs.size() == 0) {
            mResultStore.removePublisherData(StatsPublisher.class.getSimpleName());
            return;
        }
        mResultStore.putPublisherData(StatsPublisher.class.getSimpleName(), mSavedStatsConfigs);
    }

    @Override
    public void addDataSubscriber(@NonNull DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        Preconditions.checkArgument(
                publisherParam.getPublisherCase() == PublisherCase.STATS,
                "Subscribers only with StatsPublisher are supported by this class.");

        long configKey = buildConfigKey(subscriber);
        mConfigKeyToSubscribers.put(configKey, subscriber);
        addStatsConfig(configKey, subscriber);

        if (!mIsPullingReports) {
            if (DEBUG) {
                Slogf.d(CarLog.TAG_TELEMETRY, "Triggering pull stats reports");
            }
            mIsPullingReports = true;
            mTelemetryHandler.post(mPullReportsPeriodically);
        }
    }

    private void processReport(
            long configKey, @NonNull StatsLogProto.ConfigMetricsReportList report) {
        Slogf.i(CarLog.TAG_TELEMETRY, "Received reports: " + report.getReportsCount());
        if (report.getReportsCount() == 0) {
            return;
        }
        DataSubscriber subscriber = mConfigKeyToSubscribers.get(configKey);
        if (subscriber == null) {
            Slogf.w(CarLog.TAG_TELEMETRY, "No subscribers found for config " + configKey);
            return;
        }
        TimingsTraceLog traceLog = new TimingsTraceLog(
                CarLog.TAG_TELEMETRY, TraceHelper.TRACE_TAG_CAR_SERVICE);
        Map<Long, PersistableBundle> metricBundles = null;
        try {
            traceLog.traceBegin("convert stats report");
            metricBundles = ConfigMetricsReportListConverter.convert(report);
            traceLog.traceEnd();
        } catch (StatsConversionException ex) {
            traceLog.traceEnd();
            Slogf.e(CarLog.TAG_TELEMETRY, "Stats conversion exception for config " + configKey, ex);
            return;
        }
        Long metricId;
        switch (subscriber.getPublisherParam().getStats().getSystemMetric()) {
            case APP_START_MEMORY_STATE_CAPTURED:
                metricId = APP_START_MEMORY_STATE_CAPTURED_EVENT_METRIC_ID;
                break;
            case PROCESS_MEMORY_STATE:
                metricId = PROCESS_MEMORY_STATE_GAUGE_METRIC_ID;
                break;
            case ACTIVITY_FOREGROUND_STATE_CHANGED:
                metricId = ACTIVITY_FOREGROUND_STATE_CHANGED_EVENT_METRIC_ID;
                break;
            case PROCESS_CPU_TIME:
                metricId = PROCESS_CPU_TIME_GAUGE_METRIC_ID;
                break;
            case APP_CRASH_OCCURRED:
                metricId = APP_CRASH_OCCURRED_EVENT_METRIC_ID;
                break;
            case ANR_OCCURRED:
                metricId = ANR_OCCURRED_EVENT_METRIC_ID;
                break;
            case WTF_OCCURRED:
                metricId = WTF_OCCURRED_EVENT_METRIC_ID;
                break;
            case PROCESS_MEMORY_SNAPSHOT:
                metricId = PROCESS_MEMORY_SNAPSHOT_GAUGE_METRIC_ID;
                break;
            case PROCESS_START_TIME:
                metricId = PROCESS_START_TIME_EVENT_METRIC_ID;
                break;
            default:
                return;
        }
        if (!metricBundles.containsKey(metricId)) {
            Slogf.w(CarLog.TAG_TELEMETRY,
                    "No reports for metric id " + metricId + " ("
                            + subscriber.getPublisherParam().getStats().getSystemMetric()
                            + ") for config " + configKey);
            return;
        }
        PersistableBundle bundle = metricBundles.get(metricId);
        subscriber.push(bundle, isBundleLargeData(bundle));
    }

    @VisibleForTesting
    boolean isBundleLargeData(@NonNull PersistableBundle bundle) {
        String[] keys = bundle.keySet().toArray(new String[0]);
        int bytes = 0;
        for (int i = 0; i < keys.length; ++i) {
            Object array = bundle.get(keys[i]);
            if (array instanceof boolean[]) {
                boolean[] boolArray = (boolean[]) array;
                bytes += boolArray.length;  // Java boolean is 1 byte
            } else if (array instanceof long[]) {
                long[] longArray = (long[]) array;
                bytes += longArray.length * Long.BYTES;
            } else if (array instanceof int[]) {
                int[] intArray = (int[]) array;
                bytes += intArray.length * Integer.BYTES;
            } else if (array instanceof double[]) {
                double[] doubleArray = (double[]) array;
                bytes += doubleArray.length * Double.BYTES;
            } else if (array instanceof String[]) {
                String[] stringArray = (String[]) array;
                for (String str : stringArray) {
                    bytes += str.getBytes(UTF_16).length;
                }
            }
        }

        return bytes >= DataSubscriber.SCRIPT_INPUT_SIZE_THRESHOLD_BYTES;
    }

    private void processStatsMetadata(@NonNull StatsLogProto.StatsdStatsReport statsReport) {
        int myUid = Process.myUid();
        // configKey and StatsdConfig.id are the same, see this#addStatsConfig().
        HashSet<Long> activeConfigKeys = new HashSet<>(getActiveConfigKeys());
        HashSet<TelemetryProto.MetricsConfig> failedConfigs = new HashSet<>();
        for (int i = 0; i < statsReport.getConfigStatsCount(); i++) {
            StatsLogProto.StatsdStatsReport.ConfigStats stats = statsReport.getConfigStats(i);
            if (stats.getUid() != myUid || !activeConfigKeys.contains(stats.getId())) {
                continue;
            }
            if (!stats.getIsValid()) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Config key " + stats.getId() + " is invalid.");
                failedConfigs.add(mConfigKeyToSubscribers.get(stats.getId()).getMetricsConfig());
            }
        }
        if (!failedConfigs.isEmpty()) {
            // Notify DataBroker so it can disable invalid MetricsConfigs.
            onPublisherFailure(
                    new ArrayList<>(failedConfigs),
                    new IllegalStateException("Found invalid configs"));
        }
    }

    private void pullReportsPeriodically() {
        if (!mIsPullingReports) {
            return;
        }

        TimingsTraceLog traceLog = new TimingsTraceLog(
                CarLog.TAG_TELEMETRY, TraceHelper.TRACE_TAG_CAR_SERVICE);
        try {
            traceLog.traceBegin("pull stats report");
            // TODO(b/202131100): Get the active list of configs using
            //                    StatsManager#setActiveConfigsChangedOperation()
            processStatsMetadata(
                    StatsLogProto.StatsdStatsReport.parseFrom(mStatsManager.getStatsMetadata()));

            for (long configKey : getActiveConfigKeys()) {
                processReport(configKey, StatsLogProto.ConfigMetricsReportList.parseFrom(
                        mStatsManager.getReports(configKey)));
            }
            traceLog.traceEnd();
        } catch (InvalidProtocolBufferException | StatsUnavailableException e) {
            traceLog.traceEnd();
            // If the StatsD is not available, retry in the next pullReportsPeriodically call.
            Slogf.w(CarLog.TAG_TELEMETRY, e);
        }

        if (DEBUG) {
            Slogf.d(CarLog.TAG_TELEMETRY, "Stats report will be pulled in "
                    + PULL_REPORTS_PERIOD.toMinutes() + " minutes.");
        }
        mTelemetryHandler.postDelayed(mPullReportsPeriodically, PULL_REPORTS_PERIOD.toMillis());
    }

    @NonNull
    private List<Long> getActiveConfigKeys() {
        ArrayList<Long> result = new ArrayList<>();
        for (String key : mSavedStatsConfigs.keySet()) {
            // filter out all the config versions
            if (!key.startsWith(BUNDLE_CONFIG_KEY_PREFIX)) {
                continue;
            }
            // the remaining values are config keys
            result.add(mSavedStatsConfigs.getLong(key));
        }
        return result;
    }

    /**
     * Removes the subscriber from the publisher and removes StatsdConfig from StatsD service.
     * If StatsdConfig is present in Statsd, it removes it even if the subscriber is not present
     * in the publisher (it happens when subscriber was added before and CarTelemetryService was
     * restarted and lost publisher state).
     */
    @Override
    public void removeDataSubscriber(@NonNull DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        if (publisherParam.getPublisherCase() != PublisherCase.STATS) {
            Slogf.w(CarLog.TAG_TELEMETRY,
                    "Expected STATS publisher, but received "
                            + publisherParam.getPublisherCase().name());
            return;
        }
        long configKey = removeStatsConfig(subscriber);
        mConfigKeyToSubscribers.remove(configKey);
        if (mConfigKeyToSubscribers.size() == 0) {
            mIsPullingReports = false;
            mTelemetryHandler.removeCallbacks(mPullReportsPeriodically);
        }
    }

    /**
     * Removes all the subscribers from the publisher removes StatsdConfigs from StatsD service.
     */
    @Override
    public void removeAllDataSubscribers() {
        for (String key : mSavedStatsConfigs.keySet()) {
            // filter out all the config versions
            if (key == null || !key.startsWith(BUNDLE_CONFIG_KEY_PREFIX)) {
                continue;
            }
            // the remaining values are config keys
            long configKey = mSavedStatsConfigs.getLong(key);
            try {
                mStatsManager.removeConfig(configKey);
                String bundleVersion = buildBundleConfigVersionKey(configKey);
                mSavedStatsConfigs.remove(key);
                mSavedStatsConfigs.remove(bundleVersion);
            } catch (StatsUnavailableException e) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to remove config " + configKey
                        + ". Ignoring the failure. Will retry removing again when"
                        + " removeAllDataSubscribers() is called.", e);
                // If it cannot remove statsd config, it's less likely it can delete it even if
                // retry. So we will just ignore the failures. The next call of this method
                // will ry deleting StatsD configs again.
            }
        }
        mSavedStatsConfigs.clear();
        savePublisherState();
        mIsPullingReports = false;
        mTelemetryHandler.removeCallbacks(mPullReportsPeriodically);
    }

    /**
     * Returns true if the publisher has the subscriber.
     */
    @Override
    public boolean hasDataSubscriber(@NonNull DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        if (publisherParam.getPublisherCase() != PublisherCase.STATS) {
            return false;
        }
        long configKey = buildConfigKey(subscriber);
        return mConfigKeyToSubscribers.indexOfKey(configKey) >= 0;
    }

    /** Returns all the {@link TelemetryProto.MetricsConfig} associated with added subscribers. */
    @NonNull
    private List<TelemetryProto.MetricsConfig> getMetricsConfigs() {
        HashSet<TelemetryProto.MetricsConfig> uniqueConfigs = new HashSet<>();
        for (int i = 0; i < mConfigKeyToSubscribers.size(); i++) {
            uniqueConfigs.add(mConfigKeyToSubscribers.valueAt(i).getMetricsConfig());
        }
        return new ArrayList<>(uniqueConfigs);
    }

    /**
     * Returns the key for PersistableBundle to store/retrieve configKey associated with the
     * subscriber.
     */
    private static String buildBundleConfigKey(@NonNull DataSubscriber subscriber) {
        return BUNDLE_CONFIG_KEY_PREFIX + subscriber.getMetricsConfig().getName() + "-"
                + subscriber.getSubscriber().getHandler();
    }

    /**
     * Returns the key for PersistableBundle to store/retrieve {@link TelemetryProto.MetricsConfig}
     * version associated with the configKey (which is generated per DataSubscriber).
     */
    private static String buildBundleConfigVersionKey(long configKey) {
        return BUNDLE_CONFIG_VERSION_PREFIX + configKey;
    }

    /**
     * This method can be called even if StatsdConfig was added to StatsD service before. It stores
     * previously added config_keys in the persistable bundle and only updates StatsD when
     * the MetricsConfig (of CarTelemetryService) has a new version.
     */
    private void addStatsConfig(long configKey, @NonNull DataSubscriber subscriber) {
        // Store MetricsConfig (of CarTelemetryService) version per handler_function.
        String bundleVersion = buildBundleConfigVersionKey(configKey);
        if (mSavedStatsConfigs.getInt(bundleVersion) != 0) {
            int currentVersion = mSavedStatsConfigs.getInt(bundleVersion);
            if (currentVersion >= subscriber.getMetricsConfig().getVersion()) {
                // It's trying to add current or older MetricsConfig version, just ignore it.
                return;
            }  // if the subscriber's MetricsConfig version is newer, it will replace the old one.
        }
        String bundleConfigKey = buildBundleConfigKey(subscriber);
        StatsdConfig config = buildStatsdConfig(subscriber, configKey);
        try {
            // It doesn't throw exception if the StatsdConfig is invalid. But it shouldn't happen,
            // as we generate well-tested StatsdConfig in this service.
            mStatsManager.addConfig(configKey, config.toByteArray());
            mSavedStatsConfigs.putInt(bundleVersion, subscriber.getMetricsConfig().getVersion());
            mSavedStatsConfigs.putLong(bundleConfigKey, configKey);
            savePublisherState();
        } catch (StatsUnavailableException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to add config" + configKey, e);
            // We will notify the failure immediately, as we're expecting StatsManager to be stable.
            onPublisherFailure(
                    getMetricsConfigs(),
                    new IllegalStateException("Failed to add config " + configKey, e));
        }
    }

    /** Removes StatsdConfig and returns configKey. */
    private long removeStatsConfig(@NonNull DataSubscriber subscriber) {
        String bundleConfigKey = buildBundleConfigKey(subscriber);
        long configKey = buildConfigKey(subscriber);
        // Store MetricsConfig (of CarTelemetryService) version per handler_function.
        String bundleVersion = buildBundleConfigVersionKey(configKey);
        try {
            mStatsManager.removeConfig(configKey);
            mSavedStatsConfigs.remove(bundleVersion);
            mSavedStatsConfigs.remove(bundleConfigKey);
            savePublisherState();
        } catch (StatsUnavailableException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to remove config " + configKey
                    + ". Ignoring the failure. Will retry removing again when"
                    + " removeAllDataSubscribers() is called.", e);
            // If it cannot remove statsd config, it's less likely it can delete it even if we
            // retry. So we will just ignore the failures. The next call of this method will
            // try deleting StatsD configs again.
        }
        return configKey;
    }

    /**
     * Builds StatsdConfig id (aka config_key) using subscriber handler name.
     *
     * <p>StatsD uses ConfigKey struct to uniquely identify StatsdConfigs. StatsD ConfigKey consists
     * of two parts: client uid and config_key number. The StatsdConfig is added to StatsD from
     * CarService - which has uid=1000. Currently there is no client under uid=1000 and there will
     * not be config_key collision.
     */
    private static long buildConfigKey(@NonNull DataSubscriber subscriber) {
        // Not to be confused with statsd metric, this one is a global CarTelemetry metric name.
        String metricConfigName = subscriber.getMetricsConfig().getName();
        String handlerFnName = subscriber.getSubscriber().getHandler();
        return HashUtils.sha256(metricConfigName + "-" + handlerFnName);
    }

    /** Builds {@link StatsdConfig} proto for given subscriber. */
    @VisibleForTesting
    @NonNull
    static StatsdConfig buildStatsdConfig(@NonNull DataSubscriber subscriber, long configId) {
        TelemetryProto.StatsPublisher.SystemMetric metric =
                subscriber.getPublisherParam().getStats().getSystemMetric();
        StatsdConfig.Builder builder = StatsdConfig.newBuilder()
                // This id is not used in StatsD, but let's make it the same as config_key
                // just in case.
                .setId(configId)
                .addAllowedLogSource("AID_SYSTEM");

        switch (metric) {
            case APP_START_MEMORY_STATE_CAPTURED:
                return buildAppStartMemoryStateStatsdConfig(builder);
            case PROCESS_MEMORY_STATE:
                return buildProcessMemoryStateStatsdConfig(builder);
            case ACTIVITY_FOREGROUND_STATE_CHANGED:
                return buildActivityForegroundStateStatsdConfig(builder);
            case PROCESS_CPU_TIME:
                return buildProcessCpuTimeStatsdConfig(builder);
            case APP_CRASH_OCCURRED:
                return buildAppCrashOccurredStatsdConfig(builder);
            case ANR_OCCURRED:
                return buildAnrOccurredStatsdConfig(builder);
            case WTF_OCCURRED:
                return buildWtfOccurredStatsdConfig(builder);
            case PROCESS_MEMORY_SNAPSHOT:
                return buildProcessMemorySnapshotStatsdConfig(builder);
            case PROCESS_START_TIME:
                return buildProcessStartTimeStatsdConfig(builder);
            default:
                throw new IllegalArgumentException("Unsupported metric " + metric.name());
        }
    }

    @NonNull
    private static StatsdConfig buildAppStartMemoryStateStatsdConfig(
            @NonNull StatsdConfig.Builder builder) {
        return builder
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        // The id must be unique within StatsdConfig/matchers
                        .setId(APP_START_MEMORY_STATE_CAPTURED_ATOM_MATCHER_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(APP_START_MEMORY_STATE_CAPTURED_FIELD_NUMBER)))
                .addEventMetric(StatsdConfigProto.EventMetric.newBuilder()
                        // The id must be unique within StatsdConfig/metrics
                        .setId(APP_START_MEMORY_STATE_CAPTURED_EVENT_METRIC_ID)
                        .setWhat(APP_START_MEMORY_STATE_CAPTURED_ATOM_MATCHER_ID))
                .build();
    }

    @NonNull
    private static StatsdConfig buildProcessMemoryStateStatsdConfig(
            @NonNull StatsdConfig.Builder builder) {
        return builder
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        // The id must be unique within StatsdConfig/matchers
                        .setId(PROCESS_MEMORY_STATE_MATCHER_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(PROCESS_MEMORY_STATE_FIELD_NUMBER)))
                .addGaugeMetric(StatsdConfigProto.GaugeMetric.newBuilder()
                        // The id must be unique within StatsdConfig/metrics
                        .setId(PROCESS_MEMORY_STATE_GAUGE_METRIC_ID)
                        .setWhat(PROCESS_MEMORY_STATE_MATCHER_ID)
                        .setDimensionsInWhat(StatsdConfigProto.FieldMatcher.newBuilder()
                                .setField(PROCESS_MEMORY_STATE_FIELD_NUMBER)
                                .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                                        .setField(ProcessMemoryState.UID_FIELD_NUMBER))
                                .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                                        .setField(ProcessMemoryState.PROCESS_NAME_FIELD_NUMBER))
                        )
                        .setGaugeFieldsFilter(StatsdConfigProto.FieldFilter.newBuilder()
                                .setFields(PROCESS_MEMORY_STATE_FIELDS_MATCHER)
                        )  // setGaugeFieldsFilter
                        .setSamplingType(
                                StatsdConfigProto.GaugeMetric.SamplingType.RANDOM_ONE_SAMPLE)
                        .setBucket(StatsdConfigProto.TimeUnit.FIVE_MINUTES)
                )
                .addPullAtomPackages(StatsdConfigProto.PullAtomPackages.newBuilder()
                        .setAtomId(PROCESS_MEMORY_STATE_FIELD_NUMBER)
                        .addPackages("AID_SYSTEM"))
                .build();
    }

    @NonNull
    private static StatsdConfig buildActivityForegroundStateStatsdConfig(
            @NonNull StatsdConfig.Builder builder) {
        return builder
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        // The id must be unique within StatsdConfig/matchers
                        .setId(ACTIVITY_FOREGROUND_STATE_CHANGED_ATOM_MATCHER_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(ACTIVITY_FOREGROUND_STATE_CHANGED_FIELD_NUMBER)))
                .addEventMetric(StatsdConfigProto.EventMetric.newBuilder()
                        // The id must be unique within StatsdConfig/metrics
                        .setId(ACTIVITY_FOREGROUND_STATE_CHANGED_EVENT_METRIC_ID)
                        .setWhat(ACTIVITY_FOREGROUND_STATE_CHANGED_ATOM_MATCHER_ID))
                .build();
    }

    @NonNull
    private static StatsdConfig buildProcessCpuTimeStatsdConfig(
            @NonNull StatsdConfig.Builder builder) {
        return builder
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        // The id must be unique within StatsdConfig/matchers
                        .setId(PROCESS_CPU_TIME_MATCHER_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(PROCESS_CPU_TIME_FIELD_NUMBER)))
                .addGaugeMetric(StatsdConfigProto.GaugeMetric.newBuilder()
                        // The id must be unique within StatsdConfig/metrics
                        .setId(PROCESS_CPU_TIME_GAUGE_METRIC_ID)
                        .setWhat(PROCESS_CPU_TIME_MATCHER_ID)
                        .setDimensionsInWhat(StatsdConfigProto.FieldMatcher.newBuilder()
                                .setField(PROCESS_CPU_TIME_FIELD_NUMBER)
                                .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                                        .setField(ProcessCpuTime.UID_FIELD_NUMBER))
                                .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                                        .setField(ProcessCpuTime.PROCESS_NAME_FIELD_NUMBER))
                        )
                        .setGaugeFieldsFilter(StatsdConfigProto.FieldFilter.newBuilder()
                                .setFields(PROCESS_CPU_TIME_FIELDS_MATCHER)
                        )  // setGaugeFieldsFilter
                        .setSamplingType(
                                StatsdConfigProto.GaugeMetric.SamplingType.RANDOM_ONE_SAMPLE)
                        .setBucket(StatsdConfigProto.TimeUnit.FIVE_MINUTES)
                )
                .addPullAtomPackages(StatsdConfigProto.PullAtomPackages.newBuilder()
                        .setAtomId(PROCESS_CPU_TIME_FIELD_NUMBER)
                        .addPackages("AID_SYSTEM"))
                .build();
    }

    @NonNull
    private static StatsdConfig buildAppCrashOccurredStatsdConfig(
            @NonNull StatsdConfig.Builder builder) {
        return builder
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        // The id must be unique within StatsdConfig/matchers
                        .setId(APP_CRASH_OCCURRED_ATOM_MATCHER_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(APP_CRASH_OCCURRED_FIELD_NUMBER)))
                .addEventMetric(StatsdConfigProto.EventMetric.newBuilder()
                        // The id must be unique within StatsdConfig/metrics
                        .setId(APP_CRASH_OCCURRED_EVENT_METRIC_ID)
                        .setWhat(APP_CRASH_OCCURRED_ATOM_MATCHER_ID))
                .build();
    }

    @NonNull
    private static StatsdConfig buildAnrOccurredStatsdConfig(
            @NonNull StatsdConfig.Builder builder) {
        return builder
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        // The id must be unique within StatsdConfig/matchers
                        .setId(ANR_OCCURRED_ATOM_MATCHER_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(ANR_OCCURRED_FIELD_NUMBER)))
                .addEventMetric(StatsdConfigProto.EventMetric.newBuilder()
                        // The id must be unique within StatsdConfig/metrics
                        .setId(ANR_OCCURRED_EVENT_METRIC_ID)
                        .setWhat(ANR_OCCURRED_ATOM_MATCHER_ID))
                .build();
    }

    @NonNull
    private static StatsdConfig buildWtfOccurredStatsdConfig(
            @NonNull StatsdConfig.Builder builder) {
        return builder
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        // The id must be unique within StatsdConfig/matchers
                        .setId(WTF_OCCURRED_ATOM_MATCHER_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(WTF_OCCURRED_FIELD_NUMBER)))
                .addEventMetric(StatsdConfigProto.EventMetric.newBuilder()
                        // The id must be unique within StatsdConfig/metrics
                        .setId(WTF_OCCURRED_EVENT_METRIC_ID)
                        .setWhat(WTF_OCCURRED_ATOM_MATCHER_ID))
                .build();
    }

    @NonNull
    private static StatsdConfig buildProcessMemorySnapshotStatsdConfig(
            @NonNull StatsdConfig.Builder builder) {
        return builder
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        // The id must be unique within StatsdConfig/matchers
                        .setId(PROCESS_MEMORY_SNAPSHOT_ATOM_MATCHER_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(PROCESS_MEMORY_SNAPSHOT_FIELD_NUMBER)))
                .addGaugeMetric(StatsdConfigProto.GaugeMetric.newBuilder()
                        // The id must be unique within StatsdConfig/metrics
                        .setId(PROCESS_MEMORY_SNAPSHOT_GAUGE_METRIC_ID)
                        .setWhat(PROCESS_MEMORY_SNAPSHOT_ATOM_MATCHER_ID)
                        .setDimensionsInWhat(StatsdConfigProto.FieldMatcher.newBuilder()
                                .setField(PROCESS_MEMORY_SNAPSHOT_FIELD_NUMBER)
                                .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                                        .setField(ProcessMemorySnapshot.UID_FIELD_NUMBER))
                                .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                                        .setField(ProcessMemorySnapshot.PROCESS_NAME_FIELD_NUMBER))
                        )
                        .setGaugeFieldsFilter(StatsdConfigProto.FieldFilter.newBuilder()
                                .setFields(PROCESS_MEMORY_SNAPSHOT_FIELDS_MATCHER)
                        )  // setGaugeFieldsFilter
                        .setSamplingType(
                                StatsdConfigProto.GaugeMetric.SamplingType.RANDOM_ONE_SAMPLE)
                        .setBucket(StatsdConfigProto.TimeUnit.FIVE_MINUTES)
                )
                .addPullAtomPackages(StatsdConfigProto.PullAtomPackages.newBuilder()
                        .setAtomId(PROCESS_MEMORY_SNAPSHOT_FIELD_NUMBER)
                        .addPackages("AID_SYSTEM"))
                .build();
    }

    @NonNull
    private static StatsdConfig buildProcessStartTimeStatsdConfig(
            @NonNull StatsdConfig.Builder builder) {
        return builder
                .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                        // The id must be unique within StatsdConfig/matchers
                        .setId(PROCESS_START_TIME_ATOM_MATCHER_ID)
                        .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(PROCESS_START_TIME_FIELD_NUMBER)))
                .addEventMetric(StatsdConfigProto.EventMetric.newBuilder()
                        // The id must be unique within StatsdConfig/metrics
                        .setId(PROCESS_START_TIME_EVENT_METRIC_ID)
                        .setWhat(PROCESS_START_TIME_ATOM_MATCHER_ID))
                .build();
    }

    @Override
    protected void handleSessionStateChange(SessionAnnotation annotation) {}
}
