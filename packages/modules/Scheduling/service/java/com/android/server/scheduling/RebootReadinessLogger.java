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

package com.android.server.scheduling;

import static com.android.server.scheduling.SchedulingStatsLog.LONG_REBOOT_BLOCKING_REPORTED;
import static com.android.server.scheduling.SchedulingStatsLog.LONG_REBOOT_BLOCKING_REPORTED__REBOOT_BLOCK_REASON__APP_UID;
import static com.android.server.scheduling.SchedulingStatsLog.LONG_REBOOT_BLOCKING_REPORTED__REBOOT_BLOCK_REASON__SYSTEM_COMPONENT;

import android.annotation.CurrentTimeMillisLong;
import android.annotation.IntDef;
import android.content.ApexEnvironment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;


/**
 * Class for handling the storage of logging information related to reboot readiness.
 */
final class RebootReadinessLogger {
    private static final String TAG = "RebootReadinessLogger";

    private static final String MODULE_NAME = "com.android.scheduling";

    private static final String REBOOT_STATS_FILE = "reboot-readiness/reboot-stats.xml";

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<BlockingEntityRecord> mBlockingApps = new SparseArray<>();

    @GuardedBy("mLock")
    private final ArrayMap<String, BlockingEntityRecord> mBlockingComponents = new ArrayMap<>();

    @GuardedBy("mLock")
    private long mStartTime;

    @GuardedBy("mLock")
    private long mReadyTime;

    @GuardedBy("mLock")
    private int mTimesBlockedByInteractivity;

    @GuardedBy("mLock")
    private int mTimesBlockedBySubsystems;

    @GuardedBy("mLock")
    private int mTimesBlockedByAppActivity;

    @GuardedBy("mLock")
    private long mTimeUntilNextInteractionMs;

    @GuardedBy("mLock")
    private boolean mNeedsToLogMetrics;

    @GuardedBy("mLock")
    private boolean mShouldDumpMetrics;

    // Directory for storing reboot readiness metrics. By default, this is the module's DE
    // data directory, but a different directory may be injected for testing purposes.
    private final File mDeDir;

    private final Context mContext;

    private final PackageManager mPackageManager;

    @VisibleForTesting
    RebootReadinessLogger(File deDir, Context context) {
        mDeDir = deDir;
        mContext = context;
        mPackageManager = mContext.getPackageManager();
    }

    RebootReadinessLogger(Context context) {
        mDeDir = ApexEnvironment.getApexEnvironment(MODULE_NAME).getDeviceProtectedDataDir();
        mContext = context;
        mPackageManager = mContext.getPackageManager();
    }

    /**
     * Persists important information about recent reboot readiness checks. This information is
     * stored when the device first becomes reboot-ready.
     *
     * @param startTime the time the reboot readiness state started to be polled
     * @param readyTime the time the device became reboot-ready
     * @param timesBlockedByInteractivity the number of times the reboot was blocked by device
     *                                    interactivity
     * @param timesBlockedBySubsystems the number of times the reboot was blocked by subsystems
     *                                 that registered callbacks
     * @param timesBlockedByAppActivity the number of times the reboot was blocked by background
     *                                  app activity
     */
    void writeAfterRebootReadyBroadcast(@CurrentTimeMillisLong long startTime,
            @CurrentTimeMillisLong long readyTime, int timesBlockedByInteractivity,
            int timesBlockedBySubsystems, int timesBlockedByAppActivity) {
        synchronized (mLock) {
            AtomicFile rebootStatsFile = new AtomicFile(new File(mDeDir, REBOOT_STATS_FILE));

            mStartTime = startTime;
            mReadyTime = readyTime;
            mTimesBlockedByInteractivity = timesBlockedByInteractivity;
            mTimesBlockedBySubsystems = timesBlockedBySubsystems;
            mTimesBlockedByAppActivity = timesBlockedByAppActivity;
            mShouldDumpMetrics = true;

            RebootStats rebootStats = new RebootStats();
            rebootStats.setStartTimeMs(startTime);
            rebootStats.setReadyTimeMs(readyTime);
            rebootStats.setTimesBlockedByInteractivity(timesBlockedByInteractivity);
            rebootStats.setTimesBlockedBySubsystems(timesBlockedBySubsystems);
            rebootStats.setTimesBlockedByAppActivity(timesBlockedByAppActivity);
            writeRebootStatsToFile(rebootStats, rebootStatsFile);
        }
    }

    /**
     * Deletes any information that would have been logged post-reboot. This is called when
     * the device becomes not ready to reboot.
     */
    void deleteLoggingInformation() {
        synchronized (mLock) {
            AtomicFile rebootStatsFile = getRebootStatsFile();
            if (rebootStatsFile != null) {
                rebootStatsFile.delete();
                mNeedsToLogMetrics = false;
            }
        }
    }

    /**
     * Amend logging file when the device becomes not ready to reboot. If a logging file exists,
     * add information about the time between the device becoming ready to reboot and subsequently
     * not ready to reboot.
     */
    void writeAfterNotRebootReadyBroadcast() {
        synchronized (mLock) {
            AtomicFile rebootStatsFile = getRebootStatsFile();
            if (rebootStatsFile != null) {
                RebootStats rebootStats;
                try (FileInputStream stream = rebootStatsFile.openRead()) {
                    rebootStats = XmlParser.read(stream);
                } catch (Exception e) {
                    Log.e(TAG, "Could not read reboot readiness stats: " + e);
                    return;
                }

                mTimeUntilNextInteractionMs = System.currentTimeMillis() - mReadyTime;
                rebootStats.setTimeUntilNextInteractionMs(mTimeUntilNextInteractionMs);
                writeRebootStatsToFile(rebootStats, rebootStatsFile);
            }

        }
    }

    /**
     * If any metrics were stored before the last reboot, reads them into local variables. These
     * local variables will be logged when the device is first unlocked after reboot.
     */
    void readMetricsPostReboot() {
        synchronized (mLock) {
            AtomicFile rebootStatsFile = getRebootStatsFile();
            if (rebootStatsFile != null) {
                try (FileInputStream stream = rebootStatsFile.openRead()) {
                    RebootStats rebootStats = XmlParser.read(stream);
                    mReadyTime = rebootStats.getReadyTimeMs();
                    mStartTime = rebootStats.getStartTimeMs();
                    mTimesBlockedByInteractivity = rebootStats.getTimesBlockedByInteractivity();
                    mTimesBlockedBySubsystems = rebootStats.getTimesBlockedBySubsystems();
                    mTimesBlockedByAppActivity = rebootStats.getTimesBlockedByAppActivity();
                    if (rebootStats.hasTimeUntilNextInteractionMs()) {
                        mTimeUntilNextInteractionMs = rebootStats.getTimeUntilNextInteractionMs();
                    }
                    mNeedsToLogMetrics = true;
                } catch (Exception e) {
                    Log.e(TAG, "Could not read reboot readiness stats: " + e);
                } finally {
                    rebootStatsFile.delete();
                }
            }
        }
    }

    /**
     * Logs metrics which have been stored across reboots, if any exist. This method will be called
     * after the first time the device is unlocked after reboot.
     */
    void writePostRebootMetrics() {
        synchronized (mLock) {
            if (mNeedsToLogMetrics) {
                mNeedsToLogMetrics = false;
                long timeToUnlockMs = SystemClock.elapsedRealtime();
                long timeToRebootReadyMs = mReadyTime - mStartTime;
                long timeToNextInteractionMs = System.currentTimeMillis() - mReadyTime;
                if (mTimeUntilNextInteractionMs != 0) {
                    timeToNextInteractionMs = mTimeUntilNextInteractionMs;
                }
                Log.i(TAG, "UnattendedRebootOccurred"
                        + " rebootReadyMs=" + timeToRebootReadyMs
                        + " timeUntilFirstUnlockMs=" + timeToUnlockMs
                        + " blockedByInteractivity=" + mTimesBlockedByInteractivity
                        + " blockedBySubsystems=" + mTimesBlockedBySubsystems
                        + " blockedByAppActivity=" + mTimesBlockedByAppActivity
                        + " timeToNextInteractionMs=" + timeToNextInteractionMs);
                SchedulingStatsLog.write(SchedulingStatsLog.UNATTENDED_REBOOT_OCCURRED,
                        timeToRebootReadyMs,
                        timeToUnlockMs,
                        mTimesBlockedByAppActivity,
                        mTimesBlockedBySubsystems,
                        mTimesBlockedByInteractivity,
                        timeToNextInteractionMs);
                mShouldDumpMetrics = true;
            }
        }
    }

    /**
     * Tracks any components which are currently blocking the reboot. If any of the components have
     * been blocking the reboot for longer than the given threshold, this information will be logged
     * to SchedulingStatsLog. Any components which previously blocked the reboot but are currently
     * not blocking the reboot will be pruned from the set of tracked components.
     *
     * @param blockingComponentNames list of component names which are blocking the reboot.
     * @param thresholdMs the time a component may block the reboot for before being logged.
     */
    void maybeLogLongBlockingComponents(List<String> blockingComponentNames, long thresholdMs) {
        synchronized (mLock) {
            for (String component : blockingComponentNames) {
                BlockingEntityRecord record = mBlockingComponents.get(component);
                if (record == null) {
                    record = new BlockingEntityRecord(component);
                    mBlockingComponents.put(component, record);
                }
                record.logLongRebootBlockingIfNecessary(thresholdMs);
            }

            for (String existingRecordName : mBlockingComponents.keySet()) {
                if (!blockingComponentNames.contains(existingRecordName)) {
                    mBlockingComponents.remove(existingRecordName);
                }
            }
        }
    }

    /**
     * Tracks any app uids which are currently blocking the reboot. If any of the apps have been
     * blocking the reboot for longer than the given threshold, this information will be logged
     * to SchedulingStatsLog. Any apps which previously blocked the reboot but are currently
     * not blocking the reboot will be pruned from the set of tracked uids.
     *
     * @param blockingAppUids list of app uids which are blocking the reboot.
     * @param thresholdMs the time an app may block the reboot for before being logged.
     *
     * TODO(b/184165442): Use IntArray instead.
     */
    void maybeLogLongBlockingApps(List<Integer> blockingAppUids, long thresholdMs) {
        synchronized (mLock) {
            for (Integer uid : blockingAppUids) {
                BlockingEntityRecord record = mBlockingApps.get(uid);
                if (record == null) {
                    record = new BlockingEntityRecord(uid);
                    mBlockingApps.put(uid, record);
                }
                record.logLongRebootBlockingIfNecessary(thresholdMs);
            }

            List<Integer> uidsToPrune = new ArrayList<>();
            for (int i = 0; i < mBlockingApps.size(); i++) {
                int uid = mBlockingApps.keyAt(i);
                if (!blockingAppUids.contains(uid)) {
                    uidsToPrune.add(uid);
                }
            }

            for (Integer uid : uidsToPrune) {
                mBlockingApps.remove(uid);
            }
        }
    }

    @VisibleForTesting
    AtomicFile getRebootStatsFile() {
        File file = new File(mDeDir, REBOOT_STATS_FILE);
        if (file.exists()) {
            return new AtomicFile(new File(mDeDir, REBOOT_STATS_FILE));
        } else {
            return null;
        }
    }

    /** Writes information about any UIDs which are blocking the reboot. */
    void writeBlockingUids(PrintWriter pw) {
        synchronized (mLock) {
            for (int i = 0; i < mBlockingApps.size(); i++) {
                int appUid = mBlockingApps.valueAt(i).mAppUid;
                String[] pkgs = mPackageManager.getPackagesForUid(appUid);
                pw.println("Blocking uid: " + appUid + ", package(s): " + String.join(",", pkgs));
            }
        }
    }

    /** Writes information about any subsystems which are blocking the reboot. */
    void writeBlockingSubsystems(PrintWriter pw) {
        synchronized (mLock) {
            for (String subsystem : mBlockingComponents.keySet()) {
                pw.println("Blocking subsystem: " + subsystem);
            }
        }
    }

    private void writeRebootStatsToFile(RebootStats rebootStats, AtomicFile rebootStatsFile) {
        FileOutputStream stream = null;
        try {
            stream = rebootStatsFile.startWrite();
            PrintWriter pw = new PrintWriter(stream);
            XmlWriter writer = new XmlWriter(pw);
            XmlWriter.write(writer, rebootStats);
            pw.flush();
            rebootStatsFile.finishWrite(stream);
        } catch (Exception e) {
            Log.e(TAG, "Could not write reboot readiness stats: " + e);
            rebootStatsFile.failWrite(stream);
        }
    }

    void dump(PrintWriter pw) {
        synchronized (mLock) {
            if (mBlockingComponents.size() > 0) {
                writeBlockingSubsystems(pw);
            }

            if (mBlockingApps.size() > 0) {
                writeBlockingUids(pw);
            }

            if (mShouldDumpMetrics) {
                pw.println("Previous reboot readiness checks:");
                pw.println("    Start timestamp: " + mStartTime);
                pw.println("    Ready timestamp: " +  mReadyTime);
                pw.println("    Times blocked by interactivity " + mTimesBlockedByInteractivity);
                pw.println("    Times blocked by subsystems " + mTimesBlockedBySubsystems);
                pw.println("    Times blocked by app activity " + mTimesBlockedByAppActivity);
                pw.println("    Time until next interaction " + mTimeUntilNextInteractionMs);
            }
        }
    }


    /**
     * Class for tracking system components or app uids which are blocking the reboot. Handles
     * the tracking of how long an entity has blocked the reboot for, and handles the logging
     * of LongRebootBlockingReported events to SchedulingStatsLog.
     */
    private static final class BlockingEntityRecord {

        private final int mType;
        private String mComponentName;
        private int mAppUid;
        @CurrentTimeMillisLong private long mLastMetricLoggedTime;

        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
            ENTITY_TYPE_COMPONENT,
            ENTITY_TYPE_APP,
        })
        private @interface EntityType {}

        private static final int ENTITY_TYPE_COMPONENT = 1;
        private static final int ENTITY_TYPE_APP = 2;

        private BlockingEntityRecord(String name) {
            mType = ENTITY_TYPE_COMPONENT;
            mComponentName = name;
            mLastMetricLoggedTime = System.currentTimeMillis();
        }

        private BlockingEntityRecord(int uid) {
            mType = ENTITY_TYPE_APP;
            mComponentName = "";
            mAppUid = uid;
            mLastMetricLoggedTime = System.currentTimeMillis();
        }

        /**
         * Writes to SchedulingStatsLog if this entity has been blocking the reboot for longer
         * than the given threshold. If this entity has been previously written to
         * SchedulingStatsLog, the threshold will be compared with the time since the previous
         * metric was recorded.
         */
        private void logLongRebootBlockingIfNecessary(long thresholdMs) {
            final long now = System.currentTimeMillis();
            if ((now - mLastMetricLoggedTime) > thresholdMs) {
                int rebootBlockReason = mapEntityTypeToRebootBlockReason(mType);
                SchedulingStatsLog.write(LONG_REBOOT_BLOCKING_REPORTED, rebootBlockReason,
                        mComponentName, mAppUid);
                Log.i(TAG, "LongRebootBlockingReported "
                        + " rebootBlockReason=" + rebootBlockReasonToString(rebootBlockReason)
                        + " componentName=" + mComponentName
                        + " appUid=" + mAppUid);
                mLastMetricLoggedTime = now;
            }
        }

        /**
         * Returns the reboot block reason which should be logged to SchedulingStatsLog if a given
         * EntityType is blocking the reboot for a long time.
         */
        private static int mapEntityTypeToRebootBlockReason(@EntityType int type) {
            if (type == ENTITY_TYPE_COMPONENT) {
                return LONG_REBOOT_BLOCKING_REPORTED__REBOOT_BLOCK_REASON__SYSTEM_COMPONENT;
            } else {
                return LONG_REBOOT_BLOCKING_REPORTED__REBOOT_BLOCK_REASON__APP_UID;
            }
        }

        /**
         * Maps a reboot block reason to a readable string for logging purposes.
         */
        private static String rebootBlockReasonToString(int rebootBlockReason) {
            switch (rebootBlockReason) {
                case LONG_REBOOT_BLOCKING_REPORTED__REBOOT_BLOCK_REASON__APP_UID:
                    return "APP_UID";
                case LONG_REBOOT_BLOCKING_REPORTED__REBOOT_BLOCK_REASON__SYSTEM_COMPONENT:
                    return "SYSTEM_COMPONENT";
                default:
                    return "UNKNOWN";
            }
        }
    }
}
