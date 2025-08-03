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

package com.android.car.telemetry.publisher;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimingsTraceLog;
import android.car.telemetry.TelemetryProto;
import android.car.telemetry.TelemetryProto.Publisher.PublisherCase;
import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.PersistableBundle;
import android.provider.Settings;

import com.android.car.CarLog;
import com.android.car.telemetry.CarTelemetryService;
import com.android.car.telemetry.ResultStore;
import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.car.telemetry.sessioncontroller.SessionAnnotation;
import com.android.car.telemetry.sessioncontroller.SessionController;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publisher implementation for {@link TelemetryProto.MemoryPublisher}.
 *
 * <p>It pulls data from /proc/meminfo periodically and sends the file as String to the Lua script.
 *
 * <p>This publisher only allows for one Subscriber at a time. It will pull data until the
 * MetricsConfig is removed or until the maximum snapshot is reached.
 *
 * <p>Failure to read from /proc/meminfo will cause a {@link TelemetryProto.TelemetryError} to be
 * returned to the client.
 *
 * <p>The published data format is:
 * <code>
 * - mem.timestamp_millis :              long
 * - mem.meminfo :                       string (device meminfo)
 * - package_name:uid:process_name : PersistableBundle (parsed from Debug.MemInfo, listed below)
 *   - mem.summary.total-pss : int
 *   - mem.summary.java-heap : int
 *   - mem.summary.native-heap : int
 *   - mem.summary.stack : int
 *   - mem.summary.system : int
 *   - mem.summary.code : int
 *   - mem.summary.graphics : int
 *   - mem.summary.private-other : int
 *   - mem.summary.total-swap : int
 *   - mem.total_shared_clean : int
 *   - mem.total_shared_dirty : int
 *   - mem.total_private_clean : int
 *   - mem.total_private_dirty : int
 *   - mem.total_swappable_pss : int
 * </code>
 */
public class MemoryPublisher extends AbstractPublisher {

    private static final int MILLIS_IN_SECOND = 1000;
    private static final String ACTIVITY_MANAGER_CONSTANTS = "activity_manager_constants";
    @VisibleForTesting
    static final int THROTTLE_MILLIS = 60 * MILLIS_IN_SECOND;
    @VisibleForTesting
    static final String BUNDLE_KEY_NUM_SNAPSHOTS_UNTIL_FINISH = "num_snapshots_left";
    @VisibleForTesting
    static final String BUNDLE_KEY_COLLECT_INDEFINITELY = "collect_indefinitely";

    private final ActivityManager mActivityManager;
    private final Context mContext;
    private final Handler mTelemetryHandler;
    private final Path mMeminfoPath;
    private final ResultStore mResultStore;
    private final Runnable mReadMeminfoRunnable = this::readMemInfo;
    private final TimingsTraceLog mTraceLog;

    private MemorySubscriberWrapper mSubscriber;
    private PersistableBundle mPublisherState;
    private SessionAnnotation mSessionAnnotation;

    MemoryPublisher(
            @NonNull Context context,
            @NonNull PublisherListener listener,
            @NonNull Handler telemetryHandler,
            @NonNull ResultStore resultStore,
            @NonNull SessionController sessionController) {
        this(context, listener, telemetryHandler, resultStore, sessionController,
                Paths.get("/proc/meminfo"));
    }

    @VisibleForTesting
    MemoryPublisher(
            @NonNull Context context,
            @NonNull PublisherListener listener,
            @NonNull Handler telemetryHandler,
            @NonNull ResultStore resultStore,
            @NonNull SessionController sessionController,
            @NonNull Path meminfoPath) {
        super(listener);
        mContext = context;
        mTelemetryHandler = telemetryHandler;
        mResultStore = resultStore;
        mMeminfoPath = meminfoPath;
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mPublisherState = mResultStore.getPublisherData(
                MemoryPublisher.class.getSimpleName(), false);
        mTraceLog = new TimingsTraceLog(
                CarLog.TAG_TELEMETRY, TraceHelper.TRACE_TAG_CAR_SERVICE);
        // Subscribes the publisher to driving session updates by SessionController.
        sessionController.registerCallback(this::handleSessionStateChange);
    }

    @Override
    protected void handleSessionStateChange(@NonNull SessionAnnotation annotation) {
        mSessionAnnotation = annotation;
    }

    @Override
    public void addDataSubscriber(@NonNull DataSubscriber subscriber) {
        if (mSubscriber != null) {
            throw new IllegalStateException("Only one subscriber is allowed for MemoryPublisher.");
        }
        Preconditions.checkArgument(
                subscriber.getPublisherParam().getPublisherCase() == PublisherCase.MEMORY,
                "Only subscribers for memory statistics are supported by this class.");
        // the minimum allowed read_rate is 1, i.e. one snapshot per second
        if (subscriber.getPublisherParam().getMemory().getReadIntervalSec() <= 0) {
            throw new IllegalArgumentException("MemoryPublisher read_rate must be at least 1");
        }
        if (subscriber.getPublisherParam().getMemory().getMaxPendingTasks() <= 0) {
            throw new IllegalArgumentException("max_pending_tasks in MemoryPublisher must be set"
                    + " as a throttling threshold");
        }
        // if the subscriber is new, i.e. it is added from CarTelemetryManager#addMetricsConifg(),
        // then the protobuf max_snapshots field is the number of snapshots left
        int numSnapshotsLeft = subscriber.getPublisherParam().getMemory().getMaxSnapshots();
        // if client does not specify max_snapshots, the publisher will publish until the
        // MetricsConfig's lifecycle ends via Lua callback or when the MetricsConfig is removed
        boolean collectIndefinitely = numSnapshotsLeft <= 0;
        // if the subscriber is not new, i.e. it is loaded from disk, then the number of snapshots
        // left is whatever value from last time, which is stored in the publisher state
        if (mPublisherState != null) {
            numSnapshotsLeft = mPublisherState.getInt(
                    BUNDLE_KEY_NUM_SNAPSHOTS_UNTIL_FINISH, numSnapshotsLeft);
            collectIndefinitely = mPublisherState.getBoolean(
                    BUNDLE_KEY_COLLECT_INDEFINITELY, numSnapshotsLeft <= 0);
        }
        mSubscriber = new MemorySubscriberWrapper(
                subscriber, numSnapshotsLeft, collectIndefinitely);
        readMemInfo();
    }

    @Override
    public void removeDataSubscriber(@NonNull DataSubscriber subscriber) {
        if (mSubscriber == null || !mSubscriber.mDataSubscriber.equals(subscriber)) {
            return;
        }
        resetPublisher();
    }

    @Override
    public void removeAllDataSubscribers() {
        resetPublisher();
    }

    @Override
    public boolean hasDataSubscriber(@NonNull DataSubscriber subscriber) {
        return mSubscriber != null && mSubscriber.mDataSubscriber.equals(subscriber);
    }

    private void resetPublisher() {
        mTelemetryHandler.removeCallbacks(mReadMeminfoRunnable);
        mSubscriber = null;
        mPublisherState = null;
        mResultStore.removePublisherData(MemoryPublisher.class.getSimpleName());
    }

    private void readMemInfo() {
        if (mSubscriber == null) {
            return;
        }
        if (mSubscriber.isDone()) {
            // terminate the MetricsConfig
            onConfigFinished(mSubscriber.mMetricsConfig);
            resetPublisher();
            return;
        }
        mTraceLog.traceBegin("Reading meminfo and publishing");
        // Read timestamp and meminfo and create published data
        PersistableBundle data = new PersistableBundle();
        data.putLong(Constants.MEMORY_BUNDLE_KEY_TIMESTAMP, System.currentTimeMillis());
        String meminfo;
        try {
            meminfo = new String(Files.readAllBytes(mMeminfoPath));
        } catch (IOException e) {
            // Return failure to client as error
            onPublisherFailure(Arrays.asList(mSubscriber.mMetricsConfig), e);
            resetPublisher();
            mTraceLog.traceEnd();
            return;
        }
        data.putString(Constants.MEMORY_BUNDLE_KEY_MEMINFO, meminfo);

        if (!mSubscriber.mPackageNames.isEmpty()) {
            readProcessMeminfo(data);
        }

        // add sessions info to published data if available
        if (mSessionAnnotation != null) {
            mSessionAnnotation.addAnnotationsToBundle(data);
        }
        // publish data, enqueue data for script execution
        int numPendingTasks = mSubscriber.push(data);

        // update publisher state
        if (mPublisherState == null) {
            mPublisherState = new PersistableBundle();
        }
        mPublisherState.putBoolean(
                BUNDLE_KEY_COLLECT_INDEFINITELY,
                mSubscriber.mCollectIndefinitely);
        mPublisherState.putInt(
                BUNDLE_KEY_NUM_SNAPSHOTS_UNTIL_FINISH,
                mSubscriber.mNumSnapshotsLeft);
        mResultStore.putPublisherData(MemoryPublisher.class.getSimpleName(), mPublisherState);

        // if there are too many pending tasks from this publisher, throttle this publisher
        // by reducing the publishing frequency
        int delayMillis = numPendingTasks < mSubscriber.mMaxPendingTasks
                ? mSubscriber.mPublisherProto.getReadIntervalSec() * MILLIS_IN_SECOND
                : THROTTLE_MILLIS;
        // schedule the next Runnable to read meminfo
        mTelemetryHandler.postDelayed(mReadMeminfoRunnable, delayMillis);
        mTraceLog.traceEnd();
    }

    /**
     * Helper method to get process meminfo statistics from the declared packages in
     * {@code mSubscriber}. Each process meminfo is a PersistableBundle and it will be put into the
     * parameter {@code data};
     */
    private void readProcessMeminfo(PersistableBundle data) {
        mTraceLog.traceBegin("reading process meminfo");
        // update the throttle time for ActivityManager#getProcessMemoryInfo API call
        String restore = Settings.Global.getString(
                mContext.getContentResolver(), ACTIVITY_MANAGER_CONSTANTS);
        Settings.Global.putString(
                mContext.getContentResolver(),
                ACTIVITY_MANAGER_CONSTANTS,
                "memory_info_throttle_time=1");
        // find PIDs from package names because the API accept an array of PIDs
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses =
                mActivityManager.getRunningAppProcesses();
        // the 2 lists run in parallel, where pidList.get(i) is the pid of the pkgList.get(i)
        List<Integer> pidList = new ArrayList<>();
        List<String> bundleKeys = new ArrayList<>();
        // find PIDs from package names specified by the MetricsConfig
        for (ActivityManager.RunningAppProcessInfo process : runningAppProcesses) {
            // Default apps use package name as process name, some apps will have the format
            // package_name:process_name, so we extract package name and process name with split
            String[] split = process.processName.split(":");
            if (mSubscriber.mPackageNames.contains(split[0])) {
                if (CarTelemetryService.DEBUG) {
                    Slogf.d(CarLog.TAG_TELEMETRY,
                            "MemoryPublisher found matching process " + process.processName);
                }
                pidList.add(process.pid);
                bundleKeys.add(split[0] + ":" + process.uid + ":" + split[split.length - 1]);
            }
        }

        // return early if no matching process found
        if (pidList.size() == 0) {
            return;
        }

        // convert ArrayList<Integer> into int[]
        int[] pids = new int[pidList.size()];
        for (int i = 0; i < pids.length; i++) {
            pids[i] = pidList.get(i);
        }
        // get process meminfo and parse it into a PersistableBundle
        Debug.MemoryInfo[] mis = mActivityManager.getProcessMemoryInfo(pids);
        for (int i = 0; i < mis.length; i++) {
            Debug.MemoryInfo mi = mis[i];
            PersistableBundle processMeminfoBundle = new PersistableBundle();
            processMeminfoBundle.putInt(
                    Constants.MEMORY_BUNDLE_KEY_TOTAL_SWAPPABLE_PSS,
                    Integer.valueOf(mi.getTotalSwappablePss()));
            processMeminfoBundle.putInt(
                    Constants.MEMORY_BUNDLE_KEY_TOTAL_PRIVATE_DIRTY,
                    Integer.valueOf(mi.getTotalPrivateDirty()));
            processMeminfoBundle.putInt(
                    Constants.MEMORY_BUNDLE_KEY_TOTAL_SHARED_DIRTY,
                    Integer.valueOf(mi.getTotalSharedDirty()));
            processMeminfoBundle.putInt(
                    Constants.MEMORY_BUNDLE_KEY_TOTAL_PRIVATE_CLEAN,
                    Integer.valueOf(mi.getTotalPrivateClean()));
            processMeminfoBundle.putInt(
                    Constants.MEMORY_BUNDLE_KEY_TOTAL_SHARED_CLEAN,
                    Integer.valueOf(mi.getTotalSharedClean()));
            Map<String, String> map = mi.getMemoryStats();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                processMeminfoBundle.putInt(Constants.MEMORY_BUNDLE_KEY_PREFIX + entry.getKey(),
                        Integer.valueOf(entry.getValue()));
            }
            data.putPersistableBundle(bundleKeys.get(i), processMeminfoBundle);
        }
        // restore the settings constant to the original state
        Settings.Global.putString(mContext.getContentResolver(),
                ACTIVITY_MANAGER_CONSTANTS, restore);
        mTraceLog.traceEnd();
    }

    private static final class MemorySubscriberWrapper {
        /**
         * Whether to keep collecting the meminfo snapshots until end of MetricsConfig lifecycle or
         * MetricsConfig removed.
         * This flag should be set to true when the max_snapshots field is unspecified in
         * {@link TelemetryProto.Publisher}.
         */
        private final boolean mCollectIndefinitely;
        /**
         * Maximum number of memory-related pending tasks before throttling this publisher
         */
        private final int mMaxPendingTasks;
        private final DataSubscriber mDataSubscriber;
        private final TelemetryProto.MetricsConfig mMetricsConfig;
        private final TelemetryProto.MemoryPublisher mPublisherProto;
        private final Set<String> mPackageNames = new HashSet<>();

        /**
         * Number of snapshots until the publisher stops collecting data.
         */
        private int mNumSnapshotsLeft;

        private MemorySubscriberWrapper(
                DataSubscriber dataSubscriber, int numSnapshotsLeft, boolean collectIndefinitely) {
            mDataSubscriber = dataSubscriber;
            mNumSnapshotsLeft = numSnapshotsLeft;
            mCollectIndefinitely = collectIndefinitely;
            mMetricsConfig = dataSubscriber.getMetricsConfig();
            mPublisherProto = dataSubscriber.getPublisherParam().getMemory();
            mMaxPendingTasks = mPublisherProto.getMaxPendingTasks();
            mPackageNames.addAll(mPublisherProto.getPackageNamesList());
        }

        /** Publishes data and returns the number of pending tasks by this publisher. */
        private int push(PersistableBundle data) {
            if (mNumSnapshotsLeft > 0) {
                mNumSnapshotsLeft--;
            }
            boolean isLargeData =
                    data.toString().length() >= DataSubscriber.SCRIPT_INPUT_SIZE_THRESHOLD_BYTES
                            ? true : false;
            return mDataSubscriber.push(data, isLargeData);
        }

        private boolean isDone() {
            if (mCollectIndefinitely) {
                return false;
            }
            return mNumSnapshotsLeft == 0;
        }
    }
}
