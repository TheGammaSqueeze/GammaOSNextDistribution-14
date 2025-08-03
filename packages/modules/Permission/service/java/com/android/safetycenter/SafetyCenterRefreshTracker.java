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

package com.android.safetycenter;

import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK;

import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT;
import static com.android.safetycenter.logging.SafetyCenterStatsdLogger.toSystemEventResult;

import android.annotation.ElapsedRealtimeLong;
import android.annotation.UserIdInt;
import android.content.Context;
import android.os.SystemClock;
import android.safetycenter.SafetyCenterManager.RefreshReason;
import android.safetycenter.SafetyCenterStatus;
import android.safetycenter.SafetyCenterStatus.RefreshStatus;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.safetycenter.logging.SafetyCenterStatsdLogger;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A class to store the state of a refresh of safety sources, if any is ongoing.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 *
 * @hide
 */
@NotThreadSafe
public final class SafetyCenterRefreshTracker {
    private static final String TAG = "SafetyCenterRefreshTrac";

    private final Context mContext;

    @Nullable
    // TODO(b/229060064): Should we allow one refresh at a time per UserProfileGroup rather than
    //  one global refresh?
    private RefreshInProgress mRefreshInProgress = null;

    private int mRefreshCounter = 0;

    SafetyCenterRefreshTracker(Context context) {
        mContext = context;
    }

    /**
     * Reports that a new refresh is in progress and returns the broadcast id associated with this
     * refresh.
     */
    String reportRefreshInProgress(
            @RefreshReason int refreshReason, UserProfileGroup userProfileGroup) {
        if (mRefreshInProgress != null) {
            Log.i(TAG, "Replacing ongoing refresh with id: " + mRefreshInProgress.getId());
        }

        String refreshBroadcastId = UUID.randomUUID() + "_" + mRefreshCounter++;
        Log.d(
                TAG,
                "Starting a new refresh with reason: "
                        + refreshReason
                        + ", and id: "
                        + refreshBroadcastId);

        mRefreshInProgress =
                new RefreshInProgress(
                        refreshBroadcastId,
                        refreshReason,
                        userProfileGroup,
                        SafetyCenterFlags.getUntrackedSourceIds());

        return refreshBroadcastId;
    }

    /** Returns the current refresh status. */
    @RefreshStatus
    int getRefreshStatus() {
        if (mRefreshInProgress == null || mRefreshInProgress.isComplete()) {
            return SafetyCenterStatus.REFRESH_STATUS_NONE;
        }

        if (mRefreshInProgress.getReason() == REFRESH_REASON_RESCAN_BUTTON_CLICK) {
            return SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS;
        }
        return SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS;
    }

    /**
     * Returns the {@link RefreshReason} for the current refresh, or {@code null} if none is in
     * progress.
     */
    @RefreshReason
    @Nullable
    public Integer getRefreshReason() {
        if (mRefreshInProgress != null) {
            return mRefreshInProgress.getReason();
        } else {
            return null;
        }
    }

    /**
     * Reports that refresh requests have been sent to a collection of sources.
     *
     * <p>When those sources respond call {@link #reportSourceRefreshCompleted} to mark the request
     * as complete.
     */
    void reportSourceRefreshesInFlight(
            String refreshBroadcastId, List<String> sourceIds, @UserIdInt int userId) {
        RefreshInProgress refreshInProgress =
                getRefreshInProgressWithId("reportSourceRefreshesInFlight", refreshBroadcastId);
        if (refreshInProgress == null) {
            return;
        }
        for (int i = 0; i < sourceIds.size(); i++) {
            SafetySourceKey key = SafetySourceKey.of(sourceIds.get(i), userId);
            refreshInProgress.markSourceRefreshInFlight(key);
        }
    }

    /**
     * Reports that a source has completed its refresh, and returns {@code true} if the whole
     * current refresh is now complete.
     *
     * <p>If a source calls {@code reportSafetySourceError}, then this method is also used to mark
     * the refresh as completed. The {@code successful} parameter indicates whether the refresh
     * completed successfully or not. The {@code dataChanged} parameter indicates whether this
     * source's data changed or not.
     *
     * <p>Completed refreshes are logged to statsd.
     */
    public boolean reportSourceRefreshCompleted(
            String refreshBroadcastId,
            SafetySourceKey safetySourceKey,
            boolean successful,
            boolean dataChanged) {
        RefreshInProgress refreshInProgress =
                getRefreshInProgressWithId("reportSourceRefreshCompleted", refreshBroadcastId);
        if (refreshInProgress == null) {
            return false;
        }

        Duration duration =
                refreshInProgress.markSourceRefreshComplete(
                        safetySourceKey, successful, dataChanged);
        int refreshReason = refreshInProgress.getReason();
        int requestType = RefreshReasons.toRefreshRequestType(refreshReason);

        if (duration != null) {
            int sourceResult = toSystemEventResult(successful);
            SafetyCenterStatsdLogger.writeSourceRefreshSystemEvent(
                    requestType,
                    safetySourceKey.getSourceId(),
                    UserProfileGroup.getProfileTypeOfUser(safetySourceKey.getUserId(), mContext),
                    duration,
                    sourceResult,
                    refreshReason,
                    dataChanged);
        }

        if (!refreshInProgress.isComplete()) {
            return false;
        }

        Log.v(TAG, "Refresh with id: " + refreshInProgress.getId() + " completed");
        int wholeResult =
                toSystemEventResult(/* success= */ !refreshInProgress.hasAnyTrackedSourceErrors());
        SafetyCenterStatsdLogger.writeWholeRefreshSystemEvent(
                requestType,
                refreshInProgress.getDurationSinceStart(),
                wholeResult,
                refreshReason,
                refreshInProgress.hasAnyTrackedSourceDataChanged());
        mRefreshInProgress = null;
        return true;
    }

    /**
     * Clears any ongoing refresh in progress, if any.
     *
     * <p>Note that this method simply clears the tracking of a refresh, and does not prevent
     * scheduled broadcasts being sent by {@link
     * android.safetycenter.SafetyCenterManager#refreshSafetySources}.
     */
    void clearRefresh() {
        clearRefreshInternal();
    }

    /**
     * Clears the refresh in progress, if there is any with the given id.
     *
     * <p>Note that this method simply clears the tracking of a refresh, and does not prevent
     * scheduled broadcasts being sent by {@link
     * android.safetycenter.SafetyCenterManager#refreshSafetySources}.
     */
    void clearRefresh(String refreshBroadcastId) {
        if (!checkRefreshInProgress("clearRefresh", refreshBroadcastId)) {
            return;
        }
        clearRefreshInternal();
    }

    /**
     * Clears any ongoing refresh in progress for the given user.
     *
     * <p>Note that this method simply clears the tracking of a refresh, and does not prevent
     * scheduled broadcasts being sent by {@link
     * android.safetycenter.SafetyCenterManager#refreshSafetySources}.
     */
    void clearRefreshForUser(@UserIdInt int userId) {
        if (mRefreshInProgress == null) {
            Log.d(TAG, "Clear refresh for user called but no refresh in progress");
            return;
        }
        if (mRefreshInProgress.clearForUser(userId)) {
            clearRefreshInternal();
        }
    }

    /**
     * Clears the refresh in progress with the given id, and returns the {@link SafetySourceKey}s
     * that were still in-flight prior to doing that, if any.
     *
     * <p>Returns {@code null} if there was no refresh in progress with the given {@code
     * refreshBroadcastId}, or if it was already complete.
     *
     * <p>Note that this method simply clears the tracking of a refresh, and does not prevent
     * scheduled broadcasts being sent by {@link
     * android.safetycenter.SafetyCenterManager#refreshSafetySources}.
     */
    @Nullable
    ArraySet<SafetySourceKey> timeoutRefresh(String refreshBroadcastId) {
        if (!checkRefreshInProgress("timeoutRefresh", refreshBroadcastId)) {
            return null;
        }

        RefreshInProgress clearedRefresh = clearRefreshInternal();

        if (clearedRefresh == null || clearedRefresh.isComplete()) {
            return null;
        }

        ArraySet<SafetySourceKey> timedOutSources = clearedRefresh.getSourceRefreshesInFlight();
        int refreshReason = clearedRefresh.getReason();
        int requestType = RefreshReasons.toRefreshRequestType(refreshReason);

        Log.w(
                TAG,
                "Timeout after "
                        + clearedRefresh.getDurationSinceStart()
                        + " for refresh with reason: "
                        + refreshReason
                        + ", and id: "
                        + clearedRefresh.getId());

        for (int i = 0; i < timedOutSources.size(); i++) {
            SafetySourceKey sourceKey = timedOutSources.valueAt(i);
            Duration duration = clearedRefresh.getDurationSinceSourceStart(sourceKey);
            if (duration != null) {
                SafetyCenterStatsdLogger.writeSourceRefreshSystemEvent(
                        requestType,
                        sourceKey.getSourceId(),
                        UserProfileGroup.getProfileTypeOfUser(sourceKey.getUserId(), mContext),
                        duration,
                        SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT,
                        refreshReason,
                        false);
            }

            Log.w(
                    TAG,
                    "Refresh with id: "
                            + clearedRefresh.getId()
                            + " timed out for tracked source id: "
                            + sourceKey.getSourceId()
                            + ", and user id: "
                            + sourceKey.getUserId());
        }

        SafetyCenterStatsdLogger.writeWholeRefreshSystemEvent(
                requestType,
                clearedRefresh.getDurationSinceStart(),
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT,
                refreshReason,
                clearedRefresh.hasAnyTrackedSourceDataChanged());

        return timedOutSources;
    }

    /**
     * Clears the refresh in progress and returns it for the caller to do what it needs to.
     *
     * <p>If there was no refresh in progress then {@code null} is returned.
     */
    @Nullable
    private RefreshInProgress clearRefreshInternal() {
        RefreshInProgress refreshToClear = mRefreshInProgress;
        if (refreshToClear == null) {
            Log.d(TAG, "Clear refresh called but no refresh in progress");
            return null;
        }

        Log.v(TAG, "Clearing refresh with id: " + refreshToClear.getId());
        mRefreshInProgress = null;
        return refreshToClear;
    }

    /**
     * Returns the current {@link RefreshInProgress} if it has the given ID, or logs and returns
     * {@code null} if not.
     */
    @Nullable
    private RefreshInProgress getRefreshInProgressWithId(
            String methodName, String refreshBroadcastId) {
        RefreshInProgress refreshInProgress = mRefreshInProgress;
        if (refreshInProgress == null || !refreshInProgress.getId().equals(refreshBroadcastId)) {
            Log.i(TAG, methodName + " called with invalid refresh id: " + refreshBroadcastId);
            return null;
        }
        return refreshInProgress;
    }

    private boolean checkRefreshInProgress(String methodName, String refreshBroadcastId) {
        return getRefreshInProgressWithId(methodName, refreshBroadcastId) != null;
    }

    /** Dumps state for debugging purposes. */
    void dump(PrintWriter fout) {
        fout.println(
                "REFRESH IN PROGRESS ("
                        + (mRefreshInProgress != null)
                        + ", counter="
                        + mRefreshCounter
                        + ")");
        if (mRefreshInProgress != null) {
            fout.println("\t" + mRefreshInProgress);
        }
        fout.println();
    }

    /** Class representing the state of a refresh in progress. */
    private static final class RefreshInProgress {

        private final String mId;
        @RefreshReason private final int mReason;
        private final UserProfileGroup mUserProfileGroup;
        private final ArraySet<String> mUntrackedSourcesIds;
        @ElapsedRealtimeLong private final long mStartElapsedMillis;

        // The values in this map are the start times of each source refresh. The alternative of
        // using mStartTime as the start time of all source refreshes was considered, but this
        // approach is less sensitive to delays/implementation changes in broadcast dispatch.
        private final ArrayMap<SafetySourceKey, Long> mSourceRefreshesInFlight = new ArrayMap<>();

        private boolean mAnyTrackedSourceErrors = false;
        private boolean mAnyTrackedSourceDataChanged = false;

        RefreshInProgress(
                String id,
                @RefreshReason int reason,
                UserProfileGroup userProfileGroup,
                ArraySet<String> untrackedSourceIds) {
            mId = id;
            mReason = reason;
            mUserProfileGroup = userProfileGroup;
            mUntrackedSourcesIds = untrackedSourceIds;
            mStartElapsedMillis = SystemClock.elapsedRealtime();
        }

        /**
         * Returns the id of the {@link RefreshInProgress}, which corresponds to the {@link
         * android.safetycenter.SafetyCenterManager#EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID} used
         * in the refresh.
         */
        private String getId() {
            return mId;
        }

        /** Returns the {@link RefreshReason} that was given for this {@link RefreshInProgress}. */
        @RefreshReason
        private int getReason() {
            return mReason;
        }

        /** Returns the {@link Duration} since this refresh started. */
        private Duration getDurationSinceStart() {
            return Duration.ofMillis(SystemClock.elapsedRealtime() - mStartElapsedMillis);
        }

        @Nullable
        private Duration getDurationSinceSourceStart(SafetySourceKey safetySourceKey) {
            Long startElapsedMillis = mSourceRefreshesInFlight.get(safetySourceKey);
            if (startElapsedMillis == null) {
                return null;
            }
            return Duration.ofMillis(SystemClock.elapsedRealtime() - startElapsedMillis);
        }

        /** Returns the {@link SafetySourceKey} of all in-flight source refreshes. */
        private ArraySet<SafetySourceKey> getSourceRefreshesInFlight() {
            return new ArraySet<>(mSourceRefreshesInFlight.keySet());
        }

        /** Returns {@code true} if any refresh of a tracked source completed with an error. */
        private boolean hasAnyTrackedSourceErrors() {
            return mAnyTrackedSourceErrors;
        }

        /** Returns {@code true} if any refresh of a tracked source changed that source's data. */
        private boolean hasAnyTrackedSourceDataChanged() {
            return mAnyTrackedSourceDataChanged;
        }

        private void markSourceRefreshInFlight(SafetySourceKey safetySourceKey) {
            boolean tracked = isTracked(safetySourceKey);
            long currentElapsedMillis = SystemClock.elapsedRealtime();
            if (tracked) {
                mSourceRefreshesInFlight.put(safetySourceKey, currentElapsedMillis);
            }
            Log.v(
                    TAG,
                    "Refresh with id: "
                            + mId
                            + " started for source id: "
                            + safetySourceKey.getSourceId()
                            + ", user id: "
                            + safetySourceKey.getUserId()
                            + ", elapsed millis: "
                            + currentElapsedMillis
                            + ", tracking: "
                            + tracked
                            + ", now "
                            + mSourceRefreshesInFlight.size()
                            + " tracked sources in flight");
        }

        @Nullable
        private Duration markSourceRefreshComplete(
                SafetySourceKey safetySourceKey, boolean successful, boolean dataChanged) {
            Long startElapsedMillis = mSourceRefreshesInFlight.remove(safetySourceKey);

            boolean tracked = isTracked(safetySourceKey);
            mAnyTrackedSourceErrors |= (tracked && !successful);
            mAnyTrackedSourceDataChanged |= dataChanged;
            Duration duration =
                    (startElapsedMillis == null)
                            ? null
                            : Duration.ofMillis(SystemClock.elapsedRealtime() - startElapsedMillis);
            Log.v(
                    TAG,
                    "Refresh with id: "
                            + mId
                            + " completed for source id: "
                            + safetySourceKey.getSourceId()
                            + ", user id: "
                            + safetySourceKey.getUserId()
                            + ", duration: "
                            + duration
                            + ", successful: "
                            + successful
                            + ", data changed: "
                            + dataChanged
                            + ", tracking: "
                            + tracked
                            + ", now "
                            + mSourceRefreshesInFlight.size()
                            + " tracked sources in flight");
            return duration;
        }

        private boolean isTracked(SafetySourceKey safetySourceKey) {
            return !mUntrackedSourcesIds.contains(safetySourceKey.getSourceId());
        }

        /**
         * Clears the data for the given {@code userId} and returns whether that caused the entire
         * refresh to complete.
         */
        private boolean clearForUser(@UserIdInt int userId) {
            if (mUserProfileGroup.getProfileParentUserId() == userId) {
                return true;
            }
            // Loop in reverse index order to be able to remove entries while iterating.
            for (int i = mSourceRefreshesInFlight.size() - 1; i >= 0; i--) {
                SafetySourceKey sourceKey = mSourceRefreshesInFlight.keyAt(i);
                if (sourceKey.getUserId() == userId) {
                    mSourceRefreshesInFlight.removeAt(i);
                }
            }
            return isComplete();
        }

        private boolean isComplete() {
            return mSourceRefreshesInFlight.isEmpty();
        }

        @Override
        public String toString() {
            return "RefreshInProgress{"
                    + "mId='"
                    + mId
                    + '\''
                    + ", mReason="
                    + mReason
                    + ", mUserProfileGroup="
                    + mUserProfileGroup
                    + ", mUntrackedSourcesIds="
                    + mUntrackedSourcesIds
                    + ", mSourceRefreshesInFlight="
                    + mSourceRefreshesInFlight
                    + ", mStartElapsedMillis="
                    + mStartElapsedMillis
                    + ", mAnyTrackedSourceErrors="
                    + mAnyTrackedSourceErrors
                    + ", mAnyTrackedSourceDataChanged="
                    + mAnyTrackedSourceDataChanged
                    + '}';
        }
    }
}
