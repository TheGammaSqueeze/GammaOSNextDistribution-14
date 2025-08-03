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

package com.android.safetycenter.data;

import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__DATA_PROVIDED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__NO_DATA_PROVIDED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__REFRESH_TIMEOUT;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_CLEARED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_ERROR;

import android.annotation.UptimeMillisLong;
import android.annotation.UserIdInt;
import android.os.SystemClock;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceErrorDetails;
import android.safetycenter.SafetySourceIssue;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.safetycenter.SafetySourceKey;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.logging.SafetyCenterStatsdLogger;

import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Repository for {@link SafetySourceData} and other data managed by Safety Center including {@link
 * SafetySourceErrorDetails}.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@NotThreadSafe
final class SafetySourceDataRepository {

    private static final String TAG = "SafetySourceDataRepo";

    private final ArrayMap<SafetySourceKey, SafetySourceData> mSafetySourceData = new ArrayMap<>();
    private final ArraySet<SafetySourceKey> mSafetySourceErrors = new ArraySet<>();
    private final ArrayMap<SafetySourceKey, Long> mSafetySourceLastUpdated = new ArrayMap<>();
    private final ArrayMap<SafetySourceKey, Integer> mSourceStates = new ArrayMap<>();

    private final SafetyCenterInFlightIssueActionRepository
            mSafetyCenterInFlightIssueActionRepository;
    private final SafetyCenterIssueDismissalRepository mSafetyCenterIssueDismissalRepository;

    SafetySourceDataRepository(
            SafetyCenterInFlightIssueActionRepository safetyCenterInFlightIssueActionRepository,
            SafetyCenterIssueDismissalRepository safetyCenterIssueDismissalRepository) {
        mSafetyCenterInFlightIssueActionRepository = safetyCenterInFlightIssueActionRepository;
        mSafetyCenterIssueDismissalRepository = safetyCenterIssueDismissalRepository;
    }

    /**
     * Sets the latest {@link SafetySourceData} for the given {@link SafetySourceKey}, and returns
     * {@code true} if this caused any changes which would alter {@link SafetyCenterData}.
     *
     * <p>This method does not perform any validation, {@link
     * SafetyCenterDataManager#setSafetySourceData(SafetySourceData, String, SafetyEvent, String,
     * int)} should be called wherever validation is required.
     *
     * <p>Setting a {@code null} {@link SafetySourceData} evicts the current {@link
     * SafetySourceData} entry and clears the {@link SafetyCenterIssueDismissalRepository} for the
     * source.
     *
     * <p>This method may modify the {@link SafetyCenterIssueDismissalRepository}.
     */
    boolean setSafetySourceData(
            SafetySourceKey safetySourceKey, @Nullable SafetySourceData safetySourceData) {
        boolean sourceDataDiffers =
                !Objects.equals(safetySourceData, mSafetySourceData.get(safetySourceKey));
        boolean removedSourceError = mSafetySourceErrors.remove(safetySourceKey);

        if (sourceDataDiffers) {
            setSafetySourceDataInternal(safetySourceKey, safetySourceData);
        }

        setLastUpdatedNow(safetySourceKey);
        return sourceDataDiffers || removedSourceError;
    }

    private void setSafetySourceDataInternal(SafetySourceKey key, @Nullable SafetySourceData data) {
        ArraySet<String> issueIds = new ArraySet<>();
        if (data == null) {
            mSafetySourceData.remove(key);
            mSourceStates.put(key, SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_CLEARED);
        } else {
            mSafetySourceData.put(key, data);
            for (int i = 0; i < data.getIssues().size(); i++) {
                issueIds.add(data.getIssues().get(i).getId());
            }
            mSourceStates.put(key, SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__DATA_PROVIDED);
        }
        mSafetyCenterIssueDismissalRepository.updateIssuesForSource(
                issueIds, key.getSourceId(), key.getUserId());
    }

    /**
     * Returns the latest {@link SafetySourceData} that was set by {@link #setSafetySourceData} for
     * the given {@link SafetySourceKey}.
     *
     * <p>This method does not perform any validation, {@link
     * SafetyCenterDataManager#getSafetySourceData(String, String, int)} should be called wherever
     * validation is required.
     *
     * <p>Returns {@code null} if it was never set since boot, or if the entry was evicted using
     * {@link #setSafetySourceData} with a {@code null} value.
     */
    @Nullable
    SafetySourceData getSafetySourceData(SafetySourceKey safetySourceKey) {
        return mSafetySourceData.get(safetySourceKey);
    }

    /** Returns {@code true} if the given source has an error. */
    boolean sourceHasError(SafetySourceKey safetySourceKey) {
        return mSafetySourceErrors.contains(safetySourceKey);
    }

    /**
     * Returns whether the repository has the given {@link SafetySourceData} for the given {@link
     * SafetySourceKey}.
     */
    boolean sourceHasData(
            SafetySourceKey safetySourceKey, @Nullable SafetySourceData safetySourceData) {
        if (mSafetySourceErrors.contains(safetySourceKey)) {
            // Any error will cause the SafetySourceData to be discarded in favor of an error
            // message, so it can't possibly match the SafetySourceData passed in parameter.
            return false;
        }
        return Objects.equals(safetySourceData, mSafetySourceData.get(safetySourceKey));
    }

    /**
     * Reports the given {@link SafetySourceErrorDetails} for the given {@link SafetySourceKey}, and
     * returns {@code true} if this changed the repository's data.
     *
     * <p>This method does not perform any validation, {@link
     * SafetyCenterDataManager#reportSafetySourceError(SafetySourceErrorDetails, String, String,
     * int)} should be called wherever validation is required.
     */
    boolean reportSafetySourceError(
            SafetySourceKey safetySourceKey, SafetySourceErrorDetails safetySourceErrorDetails) {
        SafetyEvent safetyEvent = safetySourceErrorDetails.getSafetyEvent();
        Log.w(
                TAG,
                "Error reported from source: " + safetySourceKey + ", for event: " + safetyEvent);

        int safetyEventType = safetyEvent.getType();
        if (safetyEventType == SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED
                || safetyEventType == SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED) {
            return false;
        }

        mSourceStates.put(
                safetySourceKey, SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_ERROR);
        return setSafetySourceError(safetySourceKey);
    }

    /**
     * Marks the given {@link SafetySourceKey} as having timed out during a refresh, and returns
     * {@code true} if it caused a change to the stored data.
     *
     * @param setError whether we should clear the data associated with the source and set an error
     */
    boolean markSafetySourceRefreshTimedOut(SafetySourceKey sourceKey, boolean setError) {
        mSourceStates.put(sourceKey, SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__REFRESH_TIMEOUT);
        if (!setError) {
            return false;
        }
        return setSafetySourceError(sourceKey);
    }

    /**
     * Marks the given {@link SafetySourceKey} as being in an error state and returns {@code true}
     * if this changed the repository's data.
     */
    private boolean setSafetySourceError(SafetySourceKey safetySourceKey) {
        setLastUpdatedNow(safetySourceKey);
        boolean removingSafetySourceDataChangedSafetyCenterData =
                mSafetySourceData.remove(safetySourceKey) != null;
        boolean addingSafetySourceErrorChangedSafetyCenterData =
                mSafetySourceErrors.add(safetySourceKey);
        return removingSafetySourceDataChangedSafetyCenterData
                || addingSafetySourceErrorChangedSafetyCenterData;
    }

    /**
     * Returns the {@link SafetySourceIssue} associated with the given {@link SafetyCenterIssueKey}.
     *
     * <p>Returns {@code null} if there is no such {@link SafetySourceIssue}.
     */
    @Nullable
    SafetySourceIssue getSafetySourceIssue(SafetyCenterIssueKey safetyCenterIssueKey) {
        SafetySourceKey key =
                SafetySourceKey.of(
                        safetyCenterIssueKey.getSafetySourceId(), safetyCenterIssueKey.getUserId());
        SafetySourceData safetySourceData = mSafetySourceData.get(key);
        if (safetySourceData == null) {
            return null;
        }
        List<SafetySourceIssue> safetySourceIssues = safetySourceData.getIssues();

        SafetySourceIssue targetIssue = null;
        for (int i = 0; i < safetySourceIssues.size(); i++) {
            SafetySourceIssue safetySourceIssue = safetySourceIssues.get(i);

            if (safetyCenterIssueKey.getSafetySourceIssueId().equals(safetySourceIssue.getId())) {
                targetIssue = safetySourceIssue;
                break;
            }
        }

        return targetIssue;
    }

    /**
     * Returns the {@link SafetySourceIssue.Action} associated with the given {@link
     * SafetyCenterIssueActionId}.
     *
     * <p>Returns {@code null} if there is no associated {@link SafetySourceIssue}.
     *
     * <p>Returns {@code null} if the {@link SafetySourceIssue.Action} is currently in flight.
     */
    @Nullable
    SafetySourceIssue.Action getSafetySourceIssueAction(
            SafetyCenterIssueActionId safetyCenterIssueActionId) {
        SafetySourceIssue safetySourceIssue =
                getSafetySourceIssue(safetyCenterIssueActionId.getSafetyCenterIssueKey());

        if (safetySourceIssue == null) {
            return null;
        }

        return mSafetyCenterInFlightIssueActionRepository.getSafetySourceIssueAction(
                safetyCenterIssueActionId, safetySourceIssue);
    }

    /**
     * Returns the elapsed realtime millis of when the data of the given {@link SafetySourceKey} was
     * last updated, or {@code 0L} if no update has occurred.
     *
     * @see SystemClock#elapsedRealtime()
     */
    @UptimeMillisLong
    long getSafetySourceLastUpdated(SafetySourceKey sourceKey) {
        Long lastUpdated = mSafetySourceLastUpdated.get(sourceKey);
        if (lastUpdated != null) {
            return lastUpdated;
        } else {
            return 0L;
        }
    }

    private void setLastUpdatedNow(SafetySourceKey sourceKey) {
        mSafetySourceLastUpdated.put(sourceKey, SystemClock.elapsedRealtime());
    }

    /**
     * Returns the current {@link SafetyCenterStatsdLogger.SourceState} of the given {@link
     * SafetySourceKey}.
     */
    @SafetyCenterStatsdLogger.SourceState
    int getSourceState(SafetySourceKey sourceKey) {
        Integer sourceState = mSourceStates.get(sourceKey);
        if (sourceState != null) {
            return sourceState;
        } else {
            return SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__NO_DATA_PROVIDED;
        }
    }

    /** Clears all data for all users. */
    void clear() {
        mSafetySourceData.clear();
        mSafetySourceErrors.clear();
        mSafetySourceLastUpdated.clear();
        mSourceStates.clear();
    }

    /** Clears all data for the given user. */
    void clearForUser(@UserIdInt int userId) {
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mSafetySourceData.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSafetySourceData.keyAt(i);
            if (sourceKey.getUserId() == userId) {
                mSafetySourceData.removeAt(i);
            }
        }
        for (int i = mSafetySourceErrors.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSafetySourceErrors.valueAt(i);
            if (sourceKey.getUserId() == userId) {
                mSafetySourceErrors.removeAt(i);
            }
        }
        for (int i = mSafetySourceLastUpdated.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSafetySourceLastUpdated.keyAt(i);
            if (sourceKey.getUserId() == userId) {
                mSafetySourceLastUpdated.removeAt(i);
            }
        }
        for (int i = mSourceStates.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSourceStates.keyAt(i);
            if (sourceKey.getUserId() == userId) {
                mSourceStates.removeAt(i);
            }
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(PrintWriter fout) {
        dumpArrayMap(fout, mSafetySourceData, "SOURCE DATA");
        int errorCount = mSafetySourceErrors.size();
        fout.println("SOURCE ERRORS (" + errorCount + ")");
        for (int i = 0; i < errorCount; i++) {
            SafetySourceKey key = mSafetySourceErrors.valueAt(i);
            fout.println("\t[" + i + "] " + key);
        }
        fout.println();
        dumpArrayMap(fout, mSafetySourceLastUpdated, "LAST UPDATED");
        dumpArrayMap(fout, mSourceStates, "SOURCE STATES");
    }

    private static <K, V> void dumpArrayMap(PrintWriter fout, ArrayMap<K, V> map, String label) {
        int count = map.size();
        fout.println(label + " (" + count + ")");
        for (int i = 0; i < count; i++) {
            fout.println("\t[" + i + "] " + map.keyAt(i) + " -> " + map.valueAt(i));
        }
        fout.println();
    }
}
