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

import static android.Manifest.permission.MANAGE_SAFETY_CENTER;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.safetycenter.logging.SafetyCenterStatsdLogger.toSystemEventResult;

import android.annotation.UserIdInt;
import android.content.Context;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceErrorDetails;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySourcesGroup;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.safetycenter.ApiLock;
import com.android.safetycenter.SafetyCenterConfigReader;
import com.android.safetycenter.SafetyCenterRefreshTracker;
import com.android.safetycenter.SafetySourceIssueInfo;
import com.android.safetycenter.SafetySourceKey;
import com.android.safetycenter.UserProfileGroup;
import com.android.safetycenter.UserProfileGroup.ProfileType;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.logging.SafetyCenterStatsdLogger;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Manages updates and access to all data in the data subpackage.
 *
 * <p>Data entails what safety sources reported to safety center, including issues, entries,
 * dismissals, errors, in-flight actions, etc.
 *
 * @hide
 */
@NotThreadSafe
public final class SafetyCenterDataManager {

    private static final String TAG = "SafetyCenterDataManager";

    private final Context mContext;
    private final SafetyCenterRefreshTracker mSafetyCenterRefreshTracker;
    private final SafetySourceDataRepository mSafetySourceDataRepository;
    private final SafetyCenterIssueDismissalRepository mSafetyCenterIssueDismissalRepository;
    private final SafetyCenterIssueRepository mSafetyCenterIssueRepository;
    private final SafetyCenterInFlightIssueActionRepository
            mSafetyCenterInFlightIssueActionRepository;
    private final SafetySourceDataValidator mSafetySourceDataValidator;
    private final SafetySourceStateCollectedLogger mSafetySourceStateCollectedLogger;

    /** Creates an instance of {@link SafetyCenterDataManager}. */
    public SafetyCenterDataManager(
            Context context,
            SafetyCenterConfigReader safetyCenterConfigReader,
            SafetyCenterRefreshTracker safetyCenterRefreshTracker,
            ApiLock apiLock) {
        mContext = context;
        mSafetyCenterRefreshTracker = safetyCenterRefreshTracker;
        mSafetyCenterInFlightIssueActionRepository =
                new SafetyCenterInFlightIssueActionRepository(context);
        mSafetyCenterIssueDismissalRepository =
                new SafetyCenterIssueDismissalRepository(apiLock, safetyCenterConfigReader);
        mSafetySourceDataRepository =
                new SafetySourceDataRepository(
                        mSafetyCenterInFlightIssueActionRepository,
                        mSafetyCenterIssueDismissalRepository);
        mSafetyCenterIssueRepository =
                new SafetyCenterIssueRepository(
                        context,
                        mSafetySourceDataRepository,
                        safetyCenterConfigReader,
                        mSafetyCenterIssueDismissalRepository,
                        new SafetyCenterIssueDeduplicator(mSafetyCenterIssueDismissalRepository));
        mSafetySourceDataValidator =
                new SafetySourceDataValidator(context, safetyCenterConfigReader);
        mSafetySourceStateCollectedLogger =
                new SafetySourceStateCollectedLogger(
                        context,
                        mSafetySourceDataRepository,
                        mSafetyCenterIssueDismissalRepository,
                        mSafetyCenterIssueRepository);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////   STATE UPDATES ////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sets the latest {@link SafetySourceData} for the given {@code safetySourceId}, {@link
     * SafetyEvent}, {@code packageName} and {@code userId}, and returns {@code true} if this caused
     * any changes which would alter {@link SafetyCenterData}.
     *
     * <p>Throws if the request is invalid based on the {@link SafetyCenterConfig}: the given {@code
     * safetySourceId}, {@code packageName} and/or {@code userId} are unexpected; or the {@link
     * SafetySourceData} does not respect all constraints defined in the config.
     *
     * <p>Setting a {@code null} {@link SafetySourceData} evicts the current {@link
     * SafetySourceData} entry and clears the {@link SafetyCenterIssueDismissalRepository} for the
     * source.
     *
     * <p>This method may modify the {@link SafetyCenterIssueDismissalRepository}.
     */
    public boolean setSafetySourceData(
            @Nullable SafetySourceData safetySourceData,
            String safetySourceId,
            SafetyEvent safetyEvent,
            String packageName,
            @UserIdInt int userId) {
        if (!mSafetySourceDataValidator.validateRequest(
                safetySourceData,
                /* callerCanAccessAnySource= */ false,
                safetySourceId,
                packageName,
                userId)) {
            return false;
        }
        SafetySourceKey safetySourceKey = SafetySourceKey.of(safetySourceId, userId);

        // Must fetch refresh reason before calling processSafetyEvent because the latter may
        // complete and clear the current refresh.
        // TODO(b/277174417): Restructure this code to avoid this error-prone sequencing concern
        Integer refreshReason = null;
        if (safetyEvent.getType() == SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED) {
            refreshReason = mSafetyCenterRefreshTracker.getRefreshReason();
        }

        // It is important to process the event first as it relies on the data available prior to
        // changing it.
        boolean sourceDataWillChange =
                !mSafetySourceDataRepository.sourceHasData(safetySourceKey, safetySourceData);
        boolean eventCausedChange =
                processSafetyEvent(
                        safetySourceKey, safetyEvent, /* isError= */ false, sourceDataWillChange);
        boolean sourceDataDiffers =
                mSafetySourceDataRepository.setSafetySourceData(safetySourceKey, safetySourceData);
        boolean safetyCenterDataChanged = sourceDataDiffers || eventCausedChange;

        if (safetyCenterDataChanged) {
            mSafetyCenterIssueRepository.updateIssues(userId);
        }

        mSafetySourceStateCollectedLogger.writeSourceUpdatedAtom(
                safetySourceKey,
                safetySourceData,
                refreshReason,
                sourceDataDiffers,
                userId,
                safetyEvent);

        return safetyCenterDataChanged;
    }

    /**
     * Marks the issue with the given key as dismissed.
     *
     * <p>That issue's notification (if any) is also marked as dismissed.
     */
    public void dismissSafetyCenterIssue(SafetyCenterIssueKey safetyCenterIssueKey) {
        mSafetyCenterIssueDismissalRepository.dismissIssue(safetyCenterIssueKey);
        mSafetyCenterIssueRepository.updateIssues(safetyCenterIssueKey.getUserId());
    }

    /**
     * Marks the notification (if any) of the issue with the given key as dismissed.
     *
     * <p>The issue itself is <strong>not</strong> marked as dismissed and its warning card can
     * still appear in the Safety Center UI.
     */
    public void dismissNotification(SafetyCenterIssueKey safetyCenterIssueKey) {
        mSafetyCenterIssueDismissalRepository.dismissNotification(safetyCenterIssueKey);
        mSafetyCenterIssueRepository.updateIssues(safetyCenterIssueKey.getUserId());
    }

    /**
     * Reports the given {@link SafetySourceErrorDetails} for the given {@code safetySourceId} and
     * {@code userId}, and returns whether there was a change to the underlying {@link
     * SafetyCenterData}.
     *
     * <p>Throws if the request is invalid based on the {@link SafetyCenterConfig}: the given {@code
     * safetySourceId}, {@code packageName} and/or {@code userId} are unexpected.
     */
    public boolean reportSafetySourceError(
            SafetySourceErrorDetails safetySourceErrorDetails,
            String safetySourceId,
            String packageName,
            @UserIdInt int userId) {
        if (!mSafetySourceDataValidator.validateRequest(
                /* safetySourceData= */ null,
                /* callerCanAccessAnySource= */ false,
                safetySourceId,
                packageName,
                userId)) {
            return false;
        }
        SafetyEvent safetyEvent = safetySourceErrorDetails.getSafetyEvent();
        SafetySourceKey safetySourceKey = SafetySourceKey.of(safetySourceId, userId);

        // Must fetch refresh reason before calling processSafetyEvent because the latter may
        // complete and clear the current refresh.
        // TODO(b/277174417): Restructure this code to avoid this error-prone sequencing concern
        Integer refreshReason = null;
        if (safetyEvent.getType() == SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED) {
            refreshReason = mSafetyCenterRefreshTracker.getRefreshReason();
        }

        // It is important to process the event first as it relies on the data available prior to
        // changing it.
        boolean sourceDataWillChange = !mSafetySourceDataRepository.sourceHasError(safetySourceKey);
        boolean eventCausedChange =
                processSafetyEvent(
                        safetySourceKey, safetyEvent, /* isError= */ true, sourceDataWillChange);
        boolean sourceDataDiffers =
                mSafetySourceDataRepository.reportSafetySourceError(
                        safetySourceKey, safetySourceErrorDetails);
        boolean safetyCenterDataChanged = sourceDataDiffers || eventCausedChange;

        if (safetyCenterDataChanged) {
            mSafetyCenterIssueRepository.updateIssues(userId);
        }

        mSafetySourceStateCollectedLogger.writeSourceUpdatedAtom(
                safetySourceKey,
                /* safetySourceData= */ null,
                refreshReason,
                sourceDataDiffers,
                userId,
                safetyEvent);

        return safetyCenterDataChanged;
    }

    /**
     * Marks the given {@link SafetySourceKey} as having timed out during a refresh.
     *
     * @param setError whether we should clear the data associated with the source and set an error
     */
    public void markSafetySourceRefreshTimedOut(SafetySourceKey safetySourceKey, boolean setError) {
        boolean dataUpdated =
                mSafetySourceDataRepository.markSafetySourceRefreshTimedOut(
                        safetySourceKey, setError);
        if (dataUpdated) {
            mSafetyCenterIssueRepository.updateIssues(safetySourceKey.getUserId());
        }
    }

    /** Marks the given {@link SafetyCenterIssueActionId} as in-flight. */
    public void markSafetyCenterIssueActionInFlight(
            SafetyCenterIssueActionId safetyCenterIssueActionId) {
        mSafetyCenterInFlightIssueActionRepository.markSafetyCenterIssueActionInFlight(
                safetyCenterIssueActionId);
        mSafetyCenterIssueRepository.updateIssues(
                safetyCenterIssueActionId.getSafetyCenterIssueKey().getUserId());
    }

    /**
     * Unmarks the given {@link SafetyCenterIssueActionId} as in-flight and returns {@code true} if
     * this caused any changes which would alter {@link SafetyCenterData}.
     *
     * <p>Also logs an event to statsd with the given {@code result} value.
     */
    public boolean unmarkSafetyCenterIssueActionInFlight(
            SafetyCenterIssueActionId safetyCenterIssueActionId,
            @Nullable SafetySourceIssue safetySourceIssue,
            @SafetyCenterStatsdLogger.SystemEventResult int result) {
        boolean dataUpdated =
                mSafetyCenterInFlightIssueActionRepository.unmarkSafetyCenterIssueActionInFlight(
                        safetyCenterIssueActionId, safetySourceIssue, result);
        if (dataUpdated) {
            mSafetyCenterIssueRepository.updateIssues(
                    safetyCenterIssueActionId.getSafetyCenterIssueKey().getUserId());
        }
        return dataUpdated;
    }

    /** Clears all data related to the given {@code userId}. */
    public void clearForUser(@UserIdInt int userId) {
        mSafetySourceDataRepository.clearForUser(userId);
        mSafetyCenterInFlightIssueActionRepository.clearForUser(userId);
        mSafetyCenterIssueDismissalRepository.clearForUser(userId);
        mSafetyCenterIssueRepository.clearForUser(userId);
    }

    /** Clears all stored data. */
    public void clear() {
        mSafetySourceDataRepository.clear();
        mSafetyCenterIssueDismissalRepository.clear();
        mSafetyCenterInFlightIssueActionRepository.clear();
        mSafetyCenterIssueRepository.clear();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////   SafetyCenterIssueDismissalRepository /////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns {@code true} if the issue with the given key and severity level is currently
     * dismissed.
     *
     * <p>An issue which is dismissed at one time may become "un-dismissed" later, after the
     * resurface delay (which depends on severity level) has elapsed.
     *
     * <p>If the given issue key is not found in the repository this method returns {@code false}.
     */
    public boolean isIssueDismissed(
            SafetyCenterIssueKey safetyCenterIssueKey,
            @SafetySourceData.SeverityLevel int safetySourceIssueSeverityLevel) {
        return mSafetyCenterIssueDismissalRepository.isIssueDismissed(
                safetyCenterIssueKey, safetySourceIssueSeverityLevel);
    }

    /** Returns {@code true} if an issue's notification is dismissed now. */
    // TODO(b/259084807): Consider extracting notification dismissal logic to separate class
    public boolean isNotificationDismissedNow(
            SafetyCenterIssueKey issueKey, @SafetySourceData.SeverityLevel int severityLevel) {
        return mSafetyCenterIssueDismissalRepository.isNotificationDismissedNow(
                issueKey, severityLevel);
    }

    /**
     * Load available persisted data state into memory.
     *
     * <p>Note: only some pieces of the data can be persisted, the rest won't be loaded.
     */
    public void loadPersistableDataStateFromFile() {
        mSafetyCenterIssueDismissalRepository.loadStateFromFile();
    }

    /**
     * Returns the {@link Instant} when the issue with the given key was first reported to Safety
     * Center.
     */
    @Nullable
    public Instant getIssueFirstSeenAt(SafetyCenterIssueKey safetyCenterIssueKey) {
        return mSafetyCenterIssueDismissalRepository.getIssueFirstSeenAt(safetyCenterIssueKey);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////   SafetyCenterIssueRepository  /////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Fetches a list of issues related to the given {@link UserProfileGroup}.
     *
     * <p>Issues in the list are sorted in descending order and deduplicated (if applicable, only on
     * Android U+).
     *
     * <p>Only includes issues related to active/running {@code userId}s in the given {@link
     * UserProfileGroup}.
     */
    public List<SafetySourceIssueInfo> getIssuesDedupedSortedDescFor(
            UserProfileGroup userProfileGroup) {
        return mSafetyCenterIssueRepository.getIssuesDedupedSortedDescFor(userProfileGroup);
    }

    /**
     * Counts the total number of issues from loggable sources, in the given {@link
     * UserProfileGroup}.
     *
     * <p>Only includes issues related to active/running {@code userId}s in the given {@link
     * UserProfileGroup}.
     */
    public int countLoggableIssuesFor(UserProfileGroup userProfileGroup) {
        return mSafetyCenterIssueRepository.countLoggableIssuesFor(userProfileGroup);
    }

    /** Gets an unmodifiable list of all issues for the given {@code userId}. */
    public List<SafetySourceIssueInfo> getIssuesForUser(@UserIdInt int userId) {
        return mSafetyCenterIssueRepository.getIssuesForUser(userId);
    }

    /**
     * Returns a set of {@link SafetySourcesGroup} IDs that the given {@link SafetyCenterIssueKey}
     * is mapped to, or an empty list of no such mapping is configured.
     *
     * <p>Issue being mapped to a group means that this issue is relevant to that group.
     */
    public Set<String> getGroupMappingFor(SafetyCenterIssueKey issueKey) {
        return mSafetyCenterIssueRepository.getGroupMappingFor(issueKey);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////   SafetyCenterInFlightIssueActionRepository ////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Returns {@code true} if the given issue action is in flight. */
    public boolean actionIsInFlight(SafetyCenterIssueActionId safetyCenterIssueActionId) {
        return mSafetyCenterInFlightIssueActionRepository.actionIsInFlight(
                safetyCenterIssueActionId);
    }

    /** Returns a list of IDs of in-flight actions for the given source and user */
    ArraySet<SafetyCenterIssueActionId> getInFlightActions(String sourceId, @UserIdInt int userId) {
        return mSafetyCenterInFlightIssueActionRepository.getInFlightActions(sourceId, userId);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////   SafetySourceDataRepository ///////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the latest {@link SafetySourceData} that was set by {@link #setSafetySourceData} for
     * the given {@code safetySourceId}, {@code packageName} and {@code userId}.
     *
     * <p>Throws if the request is invalid based on the {@link SafetyCenterConfig}: the given {@code
     * safetySourceId}, {@code packageName} and/or {@code userId} are unexpected.
     *
     * <p>Returns {@code null} if it was never set since boot, or if the entry was evicted using
     * {@link #setSafetySourceData} with a {@code null} value.
     */
    @Nullable
    public SafetySourceData getSafetySourceData(
            String safetySourceId, String packageName, @UserIdInt int userId) {
        boolean callerCanAccessAnySource =
                mContext.checkCallingOrSelfPermission(MANAGE_SAFETY_CENTER) == PERMISSION_GRANTED;
        if (!mSafetySourceDataValidator.validateRequest(
                /* safetySourceData= */ null,
                callerCanAccessAnySource,
                safetySourceId,
                packageName,
                userId)) {
            return null;
        }
        return mSafetySourceDataRepository.getSafetySourceData(
                SafetySourceKey.of(safetySourceId, userId));
    }

    /**
     * Returns the latest {@link SafetySourceData} that was set by {@link #setSafetySourceData} for
     * the given {@link SafetySourceKey}.
     *
     * <p>This method does not perform any validation, {@link #getSafetySourceData(String, String,
     * int)} should be called wherever validation is required.
     *
     * <p>Returns {@code null} if it was never set since boot, or if the entry was evicted using
     * {@link #setSafetySourceData} with a {@code null} value.
     */
    @Nullable
    public SafetySourceData getSafetySourceDataInternal(SafetySourceKey safetySourceKey) {
        return mSafetySourceDataRepository.getSafetySourceData(safetySourceKey);
    }

    /** Returns {@code true} if the given source has an error. */
    public boolean sourceHasError(SafetySourceKey safetySourceKey) {
        return mSafetySourceDataRepository.sourceHasError(safetySourceKey);
    }

    /**
     * Returns the {@link SafetySourceIssue} associated with the given {@link SafetyCenterIssueKey}.
     *
     * <p>Returns {@code null} if there is no such {@link SafetySourceIssue}.
     */
    @Nullable
    public SafetySourceIssue getSafetySourceIssue(SafetyCenterIssueKey safetyCenterIssueKey) {
        return mSafetySourceDataRepository.getSafetySourceIssue(safetyCenterIssueKey);
    }

    /**
     * Returns the {@link SafetySourceIssue.Action} associated with the given {@link
     * SafetyCenterIssueActionId}.
     *
     * <p>Returns {@code null} if there is no associated {@link SafetySourceIssue}, or if it's been
     * dismissed.
     *
     * <p>Returns {@code null} if the {@link SafetySourceIssue.Action} is currently in flight.
     */
    @Nullable
    public SafetySourceIssue.Action getSafetySourceIssueAction(
            SafetyCenterIssueActionId safetyCenterIssueActionId) {
        return mSafetySourceDataRepository.getSafetySourceIssueAction(safetyCenterIssueActionId);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////  Other   /////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Dumps state for debugging purposes. */
    public void dump(FileDescriptor fd, PrintWriter fout) {
        mSafetySourceDataRepository.dump(fout);
        mSafetyCenterIssueDismissalRepository.dump(fd, fout);
        mSafetyCenterInFlightIssueActionRepository.dump(fout);
        mSafetyCenterIssueRepository.dump(fout);
    }

    private boolean processSafetyEvent(
            SafetySourceKey safetySourceKey,
            SafetyEvent safetyEvent,
            boolean isError,
            boolean sourceDataWillChange) {
        int type = safetyEvent.getType();
        switch (type) {
            case SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED:
                String refreshBroadcastId = safetyEvent.getRefreshBroadcastId();
                if (refreshBroadcastId == null) {
                    Log.w(TAG, "No refresh broadcast id in SafetyEvent of type: " + type);
                    return false;
                }
                return mSafetyCenterRefreshTracker.reportSourceRefreshCompleted(
                        refreshBroadcastId, safetySourceKey, !isError, sourceDataWillChange);
            case SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED:
            case SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED:
                String safetySourceIssueId = safetyEvent.getSafetySourceIssueId();
                if (safetySourceIssueId == null) {
                    Log.w(TAG, "No safety source issue id in SafetyEvent of type: " + type);
                    return false;
                }
                String safetySourceIssueActionId = safetyEvent.getSafetySourceIssueActionId();
                if (safetySourceIssueActionId == null) {
                    Log.w(TAG, "No safety source issue action id in SafetyEvent of type: " + type);
                    return false;
                }
                SafetyCenterIssueKey safetyCenterIssueKey =
                        SafetyCenterIssueKey.newBuilder()
                                .setSafetySourceId(safetySourceKey.getSourceId())
                                .setSafetySourceIssueId(safetySourceIssueId)
                                .setUserId(safetySourceKey.getUserId())
                                .build();
                SafetyCenterIssueActionId safetyCenterIssueActionId =
                        SafetyCenterIssueActionId.newBuilder()
                                .setSafetyCenterIssueKey(safetyCenterIssueKey)
                                .setSafetySourceIssueActionId(safetySourceIssueActionId)
                                .build();
                boolean success = type == SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED;
                int result = toSystemEventResult(success);
                return mSafetyCenterInFlightIssueActionRepository
                        .unmarkSafetyCenterIssueActionInFlight(
                                safetyCenterIssueActionId,
                                getSafetySourceIssue(safetyCenterIssueKey),
                                result);
            case SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED:
            case SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_LOCALE_CHANGED:
            case SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED:
                return false;
        }
        Log.w(TAG, "Unexpected SafetyEvent.Type: " + type);
        return false;
    }

    /**
     * Writes a SafetySourceStateCollected atom for the given source in response to a stats pull.
     */
    public void logSafetySourceStateCollectedAutomatic(
            SafetySourceKey sourceKey, @ProfileType int profileType) {
        mSafetySourceStateCollectedLogger.writeAutomaticAtom(sourceKey, profileType);
    }
}
