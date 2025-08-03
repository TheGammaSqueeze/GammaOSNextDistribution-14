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

package com.android.car.carlauncher.calmmode;

import android.annotation.IntDef;
import android.os.Build;
import android.util.Log;

import com.android.car.carlauncher.CarLauncherStatsLog;

import java.util.UUID;

/**
 * Helper class that directly interacts with {@link CarLauncherStatsLog}, a generated class that
 * contains logging methods for CalmModeActivity.
 * logging methods for CalmModeActivity.
 */
public class CalmModeStatsLogHelper {
    private static final String TAG = CalmModeStatsLogHelper.class.getSimpleName();
    public static final String INTENT_EXTRA_CALM_MODE_LAUNCH_TYPE =
            CalmModeLaunchType.class.getSimpleName();
    private static CalmModeStatsLogHelper sInstance;
    private long mSessionId;

    /**
     * IntDef representing enum values of CarCalmModeEventReported.event_type.
     */
    @IntDef({
            CalmModeEventType.UNSPECIFIED_EVENT_TYPE,
            CalmModeEventType.SESSION_STARTED,
            CalmModeEventType.SESSION_FINISHED,
    })
    public @interface CalmModeEventType {
        int UNSPECIFIED_EVENT_TYPE =
                CarLauncherStatsLog
                        .CAR_CALM_MODE_EVENT_REPORTED__EVENT_TYPE__UNSPECIFIED_EVENT_TYPE;
        int SESSION_STARTED =
                CarLauncherStatsLog.CAR_CALM_MODE_EVENT_REPORTED__EVENT_TYPE__SESSION_STARTED;
        int SESSION_FINISHED =
                CarLauncherStatsLog.CAR_CALM_MODE_EVENT_REPORTED__EVENT_TYPE__SESSION_FINISHED;
    }

    /**
     * IntDef representing enum values of CarCalmModeEventReported.launch_type.
     */
    @IntDef({
            CalmModeLaunchType.UNSPECIFIED_LAUNCH_TYPE,
            CalmModeLaunchType.SETTINGS,
            CalmModeLaunchType.QUICK_CONTROLS,
    })
    public @interface CalmModeLaunchType {
        int UNSPECIFIED_LAUNCH_TYPE =
                CarLauncherStatsLog
                        .CAR_CALM_MODE_EVENT_REPORTED__LAUNCH_TYPE__UNSPECIFIED_LAUNCH_TYPE;
        int SETTINGS =
                CarLauncherStatsLog.CAR_CALM_MODE_EVENT_REPORTED__LAUNCH_TYPE__SETTINGS;
        int QUICK_CONTROLS =
                CarLauncherStatsLog.CAR_CALM_MODE_EVENT_REPORTED__LAUNCH_TYPE__QUICK_CONTROLS;
    }

    /**
     * Returns the current logging instance of CalmModeStatsLogHelper to write this devices'
     * CarLauncherStatsModule.
     *
     * @return the logging instance of CalmModeStatsLogHelper.
     */
    public static CalmModeStatsLogHelper getInstance() {
        if (sInstance == null) {
            sInstance = new CalmModeStatsLogHelper();
        }
        return sInstance;
    }

    /**
     * Logs that a new Calm mode session has started. Additionally, resets measurements and IDs such
     * as session ID and start time.
     */
    public void logSessionStarted(@CalmModeLaunchType int launchType) {
        mSessionId = UUID.randomUUID().getMostSignificantBits();
        writeCarCalmModeEventReported(CalmModeEventType.SESSION_STARTED, launchType);
    }

    /**
     * Logs that the current Calm mode session has finished.
     */
    public void logSessionFinished() {
        writeCarCalmModeEventReported(CalmModeEventType.SESSION_FINISHED);
    }

    /**
     * Writes to CarCalmModeEvent atom with {@code eventType} as the only field, and log all other
     * fields as unspecified.
     *
     * @param eventType one of {@link CalmModeEventType}
     */
    private void writeCarCalmModeEventReported(int eventType) {
        writeCarCalmModeEventReported(
                eventType, /* launchType */ CalmModeLaunchType.UNSPECIFIED_LAUNCH_TYPE);
    }

    /**
     * Writes to CarCalmModeEvent atom with all the optional fields filled.
     *
     * @param eventType one of {@link CalmModeEventType}
     * @param launchType one of {@link CalmModeLaunchType}
     */
    private void writeCarCalmModeEventReported(int eventType, int launchType) {
        long eventId = UUID.randomUUID().getMostSignificantBits();
        if (Build.isDebuggable()) {
            Log.v(TAG, "writing CAR_CALM_MODE_EVENT_REPORTED. sessionId=" + mSessionId
                    + ", eventId=" + eventId + "eventType= " + eventType
                    + ", launchType=" + launchType);
        }
        CarLauncherStatsLog.write(
                /* atomId */ CarLauncherStatsLog.CAR_CALM_MODE_EVENT_REPORTED,
                /* sessionId */ mSessionId,
                /* eventId */ eventId,
                /* eventType */ eventType,
                /* launchType */ launchType);
    }
}
