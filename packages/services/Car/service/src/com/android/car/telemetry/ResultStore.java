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

package com.android.car.telemetry;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.telemetry.TelemetryProto;
import android.content.Context;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.AtomicFile;

import com.android.car.CarLog;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.telemetry.MetricsReportProto.MetricsReportContainer;
import com.android.car.telemetry.MetricsReportProto.MetricsReportList;
import com.android.car.telemetry.util.IoUtils;
import com.android.car.telemetry.util.MetricsReportProtoUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Disk storage for interim and final metrics statistics, as well as for internal data.
 * All methods in this class should be invoked from the telemetry thread.
 */
public class ResultStore {

    private static final long STALE_THRESHOLD_MILLIS =
            TimeUnit.MILLISECONDS.convert(30, TimeUnit.DAYS);
    @VisibleForTesting
    static final String INTERIM_RESULT_DIR = "interim";
    @VisibleForTesting
    static final String ERROR_RESULT_DIR = "error";
    @VisibleForTesting
    static final String FINAL_RESULT_DIR = "final";
    @VisibleForTesting
    static final String PUBLISHER_STORAGE_DIR = "publisher";
    /**
     * The following are bundle keys for the annotations.
     * The metrics report is annotated with the boot count, id, and timestamp.
     * Together, boot count and id will help clients determine if any report had been dropped.
     */
    @VisibleForTesting
    static final String BUNDLE_KEY_BOOT_COUNT = "metrics.report.boot_count";
    @VisibleForTesting
    static final String BUNDLE_KEY_ID = "metrics.report.id";
    @VisibleForTesting
    static final String BUNDLE_KEY_TIMESTAMP = "metrics.report.timestamp_millis";

    /** Map keys are MetricsConfig names, which are also the file names in disk. */
    private final ArrayMap<String, InterimResult> mInterimResultCache = new ArrayMap<>();
    private final ArrayMap<String, MetricsReportList.Builder> mMetricsReportCache =
            new ArrayMap<>();
    private final ArrayMap<String, TelemetryProto.TelemetryError> mErrorCache = new ArrayMap<>();
    /** Keyed by publisher's class name. */
    private final ArrayMap<String, PersistableBundle> mPublisherCache = new ArrayMap<>();
    /** Keyed by metrics config name, value is how many reports it produced since boot. */
    private final ArrayMap<String, Integer> mReportCountMap = new ArrayMap<>();

    private final Context mContext;
    private final File mInterimResultDirectory;
    private final File mErrorResultDirectory;
    private final File mMetricsReportDirectory;
    private final File mPublisherDataDirectory;

    public ResultStore(@NonNull Context context, @NonNull File rootDirectory) {
        mContext = context;
        mInterimResultDirectory = new File(rootDirectory, INTERIM_RESULT_DIR);
        mErrorResultDirectory = new File(rootDirectory, ERROR_RESULT_DIR);
        mMetricsReportDirectory = new File(rootDirectory, FINAL_RESULT_DIR);
        mPublisherDataDirectory = new File(rootDirectory, PUBLISHER_STORAGE_DIR);
        mInterimResultDirectory.mkdirs();
        mErrorResultDirectory.mkdirs();
        mMetricsReportDirectory.mkdirs();
        mPublisherDataDirectory.mkdir();
        // load interim results and internal data into memory to reduce the frequency of disk access
        loadInterimResultsIntoMemory();
    }

    /** Reads interim results into memory for faster access. */
    private void loadInterimResultsIntoMemory() {
        File[] files = mInterimResultDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                PersistableBundle interimResultBundle = IoUtils.readBundle(file);
                mInterimResultCache.put(file.getName(), new InterimResult(interimResultBundle));
            } catch (IOException e) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to read from disk.", e);
                // TODO(b/197153560): record failure
            }
        }
    }

    /**
     * Retrieves interim metrics for the given
     * {@link android.car.telemetry.TelemetryProto.MetricsConfig}.
     */
    @Nullable
    public PersistableBundle getInterimResult(@NonNull String metricsConfigName) {
        if (!mInterimResultCache.containsKey(metricsConfigName)) {
            return null;
        }
        return mInterimResultCache.get(metricsConfigName).getBundle();
    }

    /**
     * Retrieves final metrics for the given
     * {@link android.car.telemetry.TelemetryProto.MetricsConfig}.
     *
     * @param metricsConfigName name of the MetricsConfig.
     * @param deleteResult      if true, the final result will be deleted from disk.
     * @return {@link MetricsReportList} that contains all report for the given config.
     */
    @Nullable
    public MetricsReportList getMetricsReports(
            @NonNull String metricsConfigName, boolean deleteResult) {
        // the reports may have been stored in memory
        MetricsReportList.Builder reportList = mMetricsReportCache.get(metricsConfigName);
        // if not, the reports may have been stored in disk
        if (reportList == null) {
            reportList = readMetricsReportList(metricsConfigName);
        }
        if (deleteResult) {
            mMetricsReportCache.remove(metricsConfigName);
            IoUtils.deleteSilently(mMetricsReportDirectory, metricsConfigName);
        }
        return reportList == null ? null : reportList.build();
    }

    /**
     * Retrieves all metrics reports for all configs, keyed by each config name. This call is
     * not destructive, because this method is only used by
     * {@link CarTelemetryService#dump(IndentingPrintWriter)}.
     *
     * @return All available metrics reports keyed by config names.
     */
    @NonNull
    public ArrayMap<String, MetricsReportList> getAllMetricsReports() {
        // reports could be stored in two places, in memory and in disk
        ArrayMap<String, MetricsReportList> results = new ArrayMap<>();
        // first check the in-memory cache
        for (int i = 0; i < mMetricsReportCache.size(); i++) {
            results.put(mMetricsReportCache.keyAt(i), mMetricsReportCache.valueAt(i).build());
        }
        // also check the disk
        File[] files = mMetricsReportDirectory.listFiles();
        if (files == null) {
            return results;
        }
        for (File file : files) {
            // if the metrics reports exist in memory, they have already been added to `results`
            if (results.containsKey(file.getName())) {
                continue; // skip already-added results
            }
            MetricsReportList.Builder reportList = readMetricsReportList(file.getName());
            if (reportList != null) {
                results.put(file.getName(), reportList.build());
            }
        }
        return results;
    }

    /**
     * Returns the error result produced by the metrics config if exists, null otherwise.
     *
     * @param metricsConfigName name of the MetricsConfig.
     * @param deleteResult      if true, the error file will be deleted from disk.
     * @return the error result if exists, null otherwise.
     */
    @Nullable
    public TelemetryProto.TelemetryError getErrorResult(
            @NonNull String metricsConfigName, boolean deleteResult) {
        // check in memory storage
        TelemetryProto.TelemetryError result = mErrorCache.get(metricsConfigName);
        if (result != null) {
            if (deleteResult) {
                mErrorCache.remove(metricsConfigName);
            }
            return result;
        }
        // check persistent storage
        File file = new File(mErrorResultDirectory, metricsConfigName);
        // if no error exists for this metrics config, return immediately
        if (!file.exists()) {
            return null;
        }
        try {
            result = TelemetryProto.TelemetryError.parseFrom(new AtomicFile(file).readFully());
            if (deleteResult) {
                file.delete();
            }
            return result;
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to get error result from disk.", e);
            // TODO(b/197153560): record failure
        }
        return null;
    }

    /**
     * Retrieves all errors, mapped to each config name. This call is not destructive because
     * this method is only used by {@link CarTelemetryService#dump(IndentingPrintWriter)}.
     *
     * @return the map of errors to each config.
     */
    @NonNull
    public ArrayMap<String, TelemetryProto.TelemetryError> getAllErrorResults() {
        ArrayMap<String, TelemetryProto.TelemetryError> errors = new ArrayMap<>(mErrorCache);
        File[] files = mErrorResultDirectory.listFiles();
        if (files == null) {
            return errors;
        }
        for (File file : files) {
            try {
                TelemetryProto.TelemetryError error =
                        TelemetryProto.TelemetryError.parseFrom(new AtomicFile(file).readFully());
                errors.put(file.getName(), error);
            } catch (IOException e) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to read errors from disk.", e);
                // TODO(b/197153560): record failure
            }
        }
        return errors;
    }

    /**
     * Returns all data associated with the given publisher.
     *
     * @param publisherName Class name of the given publisher.
     * @param deleteData    If {@code true}, all data for the publisher will be deleted from cache
     *                      and disk.
     */
    @Nullable
    public PersistableBundle getPublisherData(@NonNull String publisherName, boolean deleteData) {
        PersistableBundle data = mPublisherCache.get(publisherName);
        if (data != null) {
            if (deleteData) {
                mPublisherCache.remove(publisherName);
            }
            return data;
        }
        // check persistent storage
        File file = new File(mPublisherDataDirectory, publisherName);
        // if no publisher data exists, return immediately
        if (!file.exists()) {
            return null;
        }
        try {
            data = IoUtils.readBundle(file);
            if (deleteData) {
                file.delete();
            }
            return data;
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to read from disk.", e);
            // TODO(b/197153560): record failure
        }
        return null;
    }

    /**
     * Stores interim metrics results in memory for the given
     * {@link android.car.telemetry.TelemetryProto.MetricsConfig}.
     */
    public void putInterimResult(
            @NonNull String metricsConfigName, @NonNull PersistableBundle result) {
        mInterimResultCache.put(metricsConfigName, new InterimResult(result, /* dirty = */ true));
    }

    /**
     * Stores metrics report in memory for the given
     * {@link android.car.telemetry.TelemetryProto.MetricsConfig}.
     *
     * If the report is produced via {@code on_metrics_report()} Lua callback, the config is not
     * considered finished. If the report is produced via {@code on_script_finished()} Lua
     * callback, the config is finished.
     */
    public void putMetricsReport(
            @NonNull String metricsConfigName,
            @NonNull PersistableBundle report,
            boolean finished) {
        // annotate the report with boot count, ID and timestamp
        annotateReport(metricsConfigName, report);
        // Every new report should be appended at the end of the report list. The previous reports
        // may exist in the cache or in the disk. We need to check both places.
        MetricsReportList.Builder reportList = mMetricsReportCache.get(metricsConfigName);
        // if no previous reports found in memory, check if there is previous report in disk
        if (reportList == null) {
            reportList = readMetricsReportList(metricsConfigName);
        }
        // if no previous report found in memory and in disk, create a new MetricsReportList
        if (reportList == null) {
            reportList = MetricsReportList.newBuilder();
        }
        // add new metrics report
        reportList = reportList.addReport(
                MetricsReportContainer.newBuilder()
                        .setReportBytes(MetricsReportProtoUtils.getByteString(report))
                        .setIsLastReport(finished));
        mMetricsReportCache.put(metricsConfigName, reportList);
    }

    /** Stores the error object produced by the script. */
    public void putErrorResult(
            @NonNull String metricsConfigName, @NonNull TelemetryProto.TelemetryError error) {
        removeInterimResult(metricsConfigName);
        mErrorCache.put(metricsConfigName, error);
    }

    /**
     * Stores PersistableBundle associated with the given publisher in disk-backed cache.
     *
     * @param publisherName Class name of the publisher.
     * @param data          PersistableBundle object that encapsulated all data to be stored for
     *                      this publisher.
     */
    public void putPublisherData(
            @NonNull String publisherName, @NonNull PersistableBundle data) {
        mPublisherCache.put(publisherName, data);
    }

    /**
     * Deletes interim result associated with the given MetricsConfig name.
     */
    public void removeInterimResult(@NonNull String metricsConfigName) {
        mInterimResultCache.remove(metricsConfigName);
        IoUtils.deleteSilently(mInterimResultDirectory, metricsConfigName);
    }

    /**
     * Deletes metrics reports associated with the given MetricsConfig name.
     */
    public void removeMetricsReports(@NonNull String metricsConfigName) {
        mMetricsReportCache.remove(metricsConfigName);
        IoUtils.deleteSilently(mMetricsReportDirectory, metricsConfigName);
    }

    /**
     * Deletes error result associated with the given MetricsConfig name.
     */
    public void removeErrorResult(@NonNull String metricsConfigName) {
        mErrorCache.remove(metricsConfigName);
        IoUtils.deleteSilently(mErrorResultDirectory, metricsConfigName);
    }

    /**
     * Deletes associated publisher data.
     */
    public void removePublisherData(@NonNull String publisherName) {
        mPublisherCache.remove(publisherName);
        IoUtils.deleteSilently(mPublisherDataDirectory, publisherName);
    }

    /**
     * Deletes all data associated with the given config name. If result does not exist, this
     * method does not do anything.
     */
    public void removeResult(@NonNull String metricsConfigName) {
        removeInterimResult(metricsConfigName);
        removeMetricsReports(metricsConfigName);
        removeErrorResult(metricsConfigName);
        mReportCountMap.remove(metricsConfigName);
    }

    /** Deletes all interim and final results. */
    public void removeAllResults() {
        mInterimResultCache.clear();
        mMetricsReportCache.clear();
        mErrorCache.clear();
        mPublisherCache.clear();
        IoUtils.deleteAllSilently(mInterimResultDirectory);
        IoUtils.deleteAllSilently(mMetricsReportDirectory);
        IoUtils.deleteAllSilently(mErrorResultDirectory);
        IoUtils.deleteAllSilently(mPublisherDataDirectory);
    }

    /**
     * Returns the names of MetricsConfigs whose script reached a terminal state.
     */
    @NonNull
    public Set<String> getFinishedMetricsConfigNames() {
        HashSet<String> configNames = new HashSet<>();
        configNames.addAll(mMetricsReportCache.keySet());
        configNames.addAll(mErrorCache.keySet());
        // prevent NPE
        String[] fileNames = mMetricsReportDirectory.list();
        if (fileNames != null) {
            configNames.addAll(Arrays.asList(fileNames));
        }
        fileNames = mErrorResultDirectory.list();
        if (fileNames != null) {
            configNames.addAll(Arrays.asList(fileNames));
        }
        return configNames;
    }

    /** Persists data to disk and deletes stale data. */
    public void flushToDisk() {
        writeInterimResultsToFile();
        writeMetricsReportsToFile();
        writeErrorsToFile();
        writePublisherCacheToFile();
        IoUtils.deleteOldFiles(STALE_THRESHOLD_MILLIS,
                mInterimResultDirectory, mMetricsReportDirectory, mErrorResultDirectory,
                mPublisherDataDirectory);
    }

    /** Writes dirty interim results to disk. */
    private void writeInterimResultsToFile() {
        mInterimResultCache.forEach((metricsConfigName, interimResult) -> {
            // only write dirty data
            if (!interimResult.isDirty()) {
                return;
            }
            try {
                IoUtils.writeBundle(
                        mInterimResultDirectory, metricsConfigName, interimResult.getBundle());
            } catch (IOException e) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to write result to file", e);
                // TODO(b/197153560): record failure
            }
        });
    }

    private void writeMetricsReportsToFile() {
        mMetricsReportCache.forEach((metricsConfigName, reportList) -> {
            try {
                IoUtils.writeProto(mMetricsReportDirectory, metricsConfigName, reportList.build());
            } catch (IOException e) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to write result to file", e);
                // TODO(b/197153560): record failure
            }
        });
    }

    private void writeErrorsToFile() {
        mErrorCache.forEach((metricsConfigName, telemetryError) -> {
            try {
                IoUtils.writeProto(mErrorResultDirectory, metricsConfigName, telemetryError);
            } catch (IOException e) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to write result to file", e);
                // TODO(b/197153560): record failure
            }
        });
    }

    private void writePublisherCacheToFile() {
        mPublisherCache.forEach((publisherName, bundle) -> {
            try {
                IoUtils.writeBundle(mPublisherDataDirectory, publisherName, bundle);
            } catch (IOException e) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to write publisher storage to file", e);
                // TODO(b/197153560): record failure
            }
        });
    }

    /**
     * Gets the {@link MetricsReportList} for the given metricsConfigName from disk.
     * If no report exists, return null.
     */
    @Nullable
    private MetricsReportList.Builder readMetricsReportList(@NonNull String metricsConfigName) {
        // check persistent storage
        File file = new File(mMetricsReportDirectory, metricsConfigName);
        // if no error exists for this metrics config, return immediately
        if (!file.exists()) {
            return null;
        }
        try {
            // return the mutable builder because ResultStore will be modifying the list frequently
            return MetricsReportList.parseFrom(new AtomicFile(file).readFully()).toBuilder();
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to get report list from disk.", e);
            // TODO(b/197153560): record failure
        }
        return null;
    }

    /**
     * Annotates the report with boot count, id, and timestamp.
     *
     * ResultStore will keep track of how many reports are produced by each config since boot.
     */
    private void annotateReport(
            @NonNull String metricsConfigName, @NonNull PersistableBundle report) {
        report.putLong(BUNDLE_KEY_TIMESTAMP, System.currentTimeMillis());
        report.putInt(
                BUNDLE_KEY_BOOT_COUNT,
                Settings.Global.getInt(
                        mContext.getContentResolver(), Settings.Global.BOOT_COUNT, -1));
        int id = mReportCountMap.getOrDefault(metricsConfigName, 0);
        id++;
        report.putInt(BUNDLE_KEY_ID, id);
        mReportCountMap.put(metricsConfigName, id);
    }

    /** Wrapper around a result and whether the result should be written to disk. */
    private static final class InterimResult {
        private final PersistableBundle mBundle;
        private final boolean mDirty;

        private InterimResult(@NonNull PersistableBundle bundle) {
            mBundle = bundle;
            mDirty = false;
        }

        private InterimResult(@NonNull PersistableBundle bundle, boolean dirty) {
            mBundle = bundle;
            mDirty = dirty;
        }

        @NonNull
        private PersistableBundle getBundle() {
            return mBundle;
        }

        private boolean isDirty() {
            return mDirty;
        }
    }
}
