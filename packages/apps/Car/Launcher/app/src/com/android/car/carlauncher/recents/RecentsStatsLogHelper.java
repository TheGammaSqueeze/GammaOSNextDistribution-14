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

package com.android.car.carlauncher.recents;

import android.annotation.IntDef;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.android.car.carlauncher.CarLauncherStatsLog;

import java.util.UUID;

/**
 * Helper class that directly interacts with CarLauncherStatsLog, a generated class that contains
 * logging methods for CarRecentsActivity.
 */
public class RecentsStatsLogHelper {
    public static final String TAG = "RecentsStatsLogHelper";
    private static RecentsStatsLogHelper sInstance;
    private PackageManager mPackageManager;
    private long mSessionId;
    private long mStartTimeMs;
    // Integer for taskIndex, to be logged by CarRecentsEventReported
    public static final int UNSPECIFIED_INDEX = -1;
    // Integer for totalTaskCount, same as above
    public static final int UNSPECIFIED_COUNT = -1;
    // String to be logged as packageName when packageName is not relevant to recents event
    public static final String UNSPECIFIED_PACKAGE_NAME = "_PACKAGE_NAME_NOT_LOGGED";
    // Uid to be logged as uid when packageName is not relevant or cannot be resolved
    public static final int UNSPECIFIED_PACKAGE_UID = -1;

    /**
     * IntDef representing enum values of CarRecentsEventReported.event_type.
     */
    @IntDef({
            RecentsEventType.UNSPECIFIED,
            RecentsEventType.SESSION_STARTED,
            RecentsEventType.SESSION_FINISHED,
            RecentsEventType.APP_LAUNCHED,
            RecentsEventType.APP_DISMISSED,
            RecentsEventType.CLEAR_ALL,
    })
    public @interface RecentsEventType {
        int UNSPECIFIED = CarLauncherStatsLog.CAR_RECENTS_EVENT_REPORTED__EVENT_TYPE__UNSPECIFIED;
        int SESSION_STARTED =
                CarLauncherStatsLog.CAR_RECENTS_EVENT_REPORTED__EVENT_TYPE__SESSION_STARTED;
        int SESSION_FINISHED =
                CarLauncherStatsLog.CAR_RECENTS_EVENT_REPORTED__EVENT_TYPE__SESSION_FINISHED;
        int APP_LAUNCHED =
                CarLauncherStatsLog.CAR_RECENTS_EVENT_REPORTED__EVENT_TYPE__APP_LAUNCHED;
        int APP_DISMISSED =
                CarLauncherStatsLog.CAR_RECENTS_EVENT_REPORTED__EVENT_TYPE__APP_DISMISSED;
        int CLEAR_ALL =
                CarLauncherStatsLog.CAR_RECENTS_EVENT_REPORTED__EVENT_TYPE__CLEAR_ALL;
    }

    /**
     * Returns the current logging instance of RecentsStatsLogHelper to write this devices'
     * CarLauncherStatsModule.
     *
     * @return the logging instance of RecentsStatsLogHelper.
     */
    public static RecentsStatsLogHelper getInstance() {
        if (sInstance == null) {
            sInstance = new RecentsStatsLogHelper();
        }
        return sInstance;
    }

    public void setPackageManager(PackageManager packageManager) {
        mPackageManager = packageManager;
    }

    /**
     * Logs that a new recents session has started. Additionally, resets measurements and IDs such
     * as session ID and start time.
     */
    public void logSessionStarted() {
        mSessionId = UUID.randomUUID().getMostSignificantBits();
        mStartTimeMs = System.currentTimeMillis();
        writeCarRecentsEventReported(RecentsEventType.SESSION_STARTED);
    }

    /**
     * Logs that an app launch interaction has occurred, along with the launched app's package name,
     * the total open task count in recents, and the launched app's position.
     */
    public void logAppLaunched(int totalTaskCount, int eventTaskIndex, String packageName) {
        writeCarRecentsEventReported(
                /* eventType */ RecentsEventType.APP_LAUNCHED,
                /* totalTaskCount */ totalTaskCount,
                /* eventTaskIndex */ eventTaskIndex,
                /* packageName */ packageName);
    }

    /**
     * Logs that an app dismiss interaction has occurred, along with the dismissed app's package
     * name, the total open task count in recents, and the dimissed app's position.
     */
    public void logAppDismissed(int totalTaskCount, int eventTaskIndex, String packageName) {
        writeCarRecentsEventReported(
                /* eventType */ RecentsStatsLogHelper.RecentsEventType.APP_DISMISSED,
                /* totalTaskCount */ totalTaskCount,
                /* eventTaskIndex */ eventTaskIndex,
                /* packageName */ packageName);
    }

    /**
     * Logs that clear all has been logged, along with the total open task count in recents.
     */
    public void logClearAll(int totalTaskCount) {
        writeCarRecentsEventReported(
                /* eventType */ RecentsStatsLogHelper.RecentsEventType.CLEAR_ALL,
                /* totalTaskCount */ totalTaskCount,
                /* eventTaskIndex */ UNSPECIFIED_INDEX,
                /* packageName */ UNSPECIFIED_PACKAGE_NAME);
    }

    /**
     * Logs that the current recents session has finished.
     */
    public void logSessionFinished() {
        writeCarRecentsEventReported(RecentsEventType.SESSION_FINISHED);
    }

    /**
     * Writes to CarRecentsEvent atom with {@code eventType} as the only field, and log all other
     * fields as unspecified.
     *
     * @param eventType one of {@link RecentsEventType}
     */
    private void writeCarRecentsEventReported(int eventType) {
        writeCarRecentsEventReported(eventType, /* totalTaskCount */ UNSPECIFIED_COUNT,
                /* eventTaskIndex */ UNSPECIFIED_INDEX, /* packageName */ UNSPECIFIED_PACKAGE_NAME);
    }

    /**
     * Writes to CarRecentsEvent atom with all the optional fields filled.
     *
     * @param eventType one of {@link RecentsEventType}
     * @param totalTaskCount the number of tasks displayed in recents screen
     * @param eventTaskIndex the index of the recents task of this interaction
     * @param packageName the package name of the app interacted with
     */
    private void writeCarRecentsEventReported(int eventType, int totalTaskCount,
            int eventTaskIndex, String packageName) {
        if (Build.isDebuggable()) {
            Log.v(TAG, "writing CAR_RECENTS_EVENT_REPORTED with eventType=" + eventType
                    + ", packageName=" + packageName);
        }
        writeCarRecentsEventReported(
                /* sessionId */ mSessionId,
                /* eventId */ UUID.randomUUID().getMostSignificantBits(),
                /* eventType */ eventType,
                /* totalTaskCount */ totalTaskCount,
                /* taskIndex */ eventTaskIndex,
                /* timeToEventMs */ System.currentTimeMillis() - mStartTimeMs,
                /* packageUid */ getPackageUid(packageName));
    }

    private int getPackageUid(String packageName) {
        if (packageName == null) {
            return UNSPECIFIED_PACKAGE_UID;
        }
        try {
            ApplicationInfo appInfo = mPackageManager.getApplicationInfo(packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA));
            return appInfo.uid;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "getPackageUid() on " + packageName + " was not found");
        }
        return UNSPECIFIED_PACKAGE_UID;
    }

    private void writeCarRecentsEventReported(long sessionId, long eventId, int eventType,
            int totalTaskCount, int eventTaskIndex, long timeToEventMs, int packageUid) {
        CarLauncherStatsLog.write(
                /* atomId */ CarLauncherStatsLog.CAR_RECENTS_EVENT_REPORTED,
                /* session_id */ sessionId,
                /* event_id */ eventId,
                /* event_type */ eventType,
                /* total_task_count */ totalTaskCount,
                /* event_task_index */ eventTaskIndex,
                /* long time_to_event_millis */ timeToEventMs,
                /* package_uid */ packageUid);
    }
}
