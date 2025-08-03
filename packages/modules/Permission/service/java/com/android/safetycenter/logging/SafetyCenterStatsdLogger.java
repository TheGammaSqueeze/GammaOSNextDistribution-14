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

package com.android.safetycenter.logging;

import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__ACTION__ISSUE_PRIMARY_ACTION_CLICKED;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__ACTION__ISSUE_SECONDARY_ACTION_CLICKED;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__ACTION__NOTIFICATION_DISMISSED;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__ACTION__NOTIFICATION_POSTED;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__ISSUE_STATE__ACTIVE;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__NAVIGATION_SOURCE__SOURCE_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PRIVATE;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__SENSOR__SENSOR_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__SEVERITY_LEVEL__SAFETY_SEVERITY_UNSPECIFIED;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_INTERACTION_REPORTED__VIEW_TYPE__VIEW_TYPE_NOTIFICATION;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__COMPLETE_GET_NEW_DATA;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__COMPLETE_RESCAN;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__INLINE_ACTION;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__SINGLE_SOURCE_GET_NEW_DATA;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__SINGLE_SOURCE_RESCAN;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__ERROR;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__SUCCESS;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PRIVATE;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__COLLECTION_TYPE__AUTOMATIC;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__COLLECTION_TYPE__SOURCE_UPDATED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PRIVATE;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_UNSPECIFIED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__DATA_PROVIDED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__NO_DATA_PROVIDED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__REFRESH_ERROR;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__REFRESH_TIMEOUT;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_CLEARED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_ERROR;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_STATE_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__UPDATE_TYPE__REFRESH_RESPONSE;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__UPDATE_TYPE__SELF_INITIATED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__UPDATE_TYPE__UPDATE_TYPE_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;
import static com.android.safetycenter.UserProfileGroup.PROFILE_TYPE_MANAGED;
import static com.android.safetycenter.UserProfileGroup.PROFILE_TYPE_PRIMARY;
import static com.android.safetycenter.UserProfileGroup.PROFILE_TYPE_PRIVATE;

import android.annotation.ElapsedRealtimeLong;
import android.annotation.IntDef;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyCenterManager.RefreshRequestType;
import android.safetycenter.SafetyCenterStatus;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceData;
import android.util.Log;
import android.util.StatsEvent;

import androidx.annotation.Nullable;

import com.android.permission.PermissionStatsLog;
import com.android.safetycenter.SafetyCenterFlags;
import com.android.safetycenter.UserProfileGroup.ProfileType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * Marshalls and writes statsd atoms. Contains implementation details of how atom parameters are
 * encoded and provides a better-typed interface for other classes to call.
 *
 * @hide
 */
public final class SafetyCenterStatsdLogger {

    private static final String TAG = "SafetyCenterStatsdLog";
    private static final long UNSET_SOURCE_ID = 0;
    private static final long UNSET_ISSUE_TYPE_ID = 0;
    private static final long UNSET_SESSION_ID = 0;
    private static final long UNSET_SOURCE_GROUP_ID = 0;
    private static final long UNSET_REFRESH_REASON = 0L;
    private static final boolean UNSET_DATA_CHANGED = false;
    private static final long UNSET_LAST_UPDATED_ELAPSED_TIME_MILLIS = 0L;

    /**
     * The different results for a system event reported by Safety Center.
     *
     * @hide
     */
    @IntDef(
            prefix = {"SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__"},
            value = {
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__SUCCESS,
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__ERROR,
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SystemEventResult {}

    /**
     * The different results for a system event reported by Safety Center.
     *
     * @hide
     */
    @IntDef(
            prefix = {"SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__"},
            value = {
                SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_STATE_UNKNOWN,
                SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__DATA_PROVIDED,
                SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__NO_DATA_PROVIDED,
                SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__REFRESH_TIMEOUT,
                SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__REFRESH_ERROR,
                SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_ERROR,
                SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_CLEARED
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SourceState {}

    /**
     * Creates a {@link PermissionStatsLog#SAFETY_STATE} {@link StatsEvent} with the given
     * parameters.
     */
    static StatsEvent createSafetyStateEvent(
            @SafetyCenterStatus.OverallSeverityLevel int severityLevel,
            long openIssueCount,
            long dismissedIssueCount) {
        return PermissionStatsLog.buildStatsEvent(
                SAFETY_STATE,
                toSafetyStateOverallSeverityLevel(severityLevel),
                openIssueCount,
                dismissedIssueCount);
    }

    /** Writes a {@link PermissionStatsLog#SAFETY_SOURCE_STATE_COLLECTED} atom. */
    public static void writeSafetySourceStateCollected(
            String sourceId,
            @ProfileType int profileType,
            @Nullable @SafetySourceData.SeverityLevel Integer sourceSeverityLevel,
            long openIssuesCount,
            long dismissedIssuesCount,
            long duplicateFilteredOutIssuesCount,
            @SourceState int sourceState,
            @Nullable SafetyEvent safetyEvent,
            @Nullable @SafetyCenterManager.RefreshReason Integer refreshReason,
            boolean dataChanged,
            @Nullable @ElapsedRealtimeLong Long lastUpdatedElapsedTimeMillis) {
        if (!SafetyCenterFlags.getAllowStatsdLogging()) {
            return;
        }
        int collectionType =
                safetyEvent != null
                        ? SAFETY_SOURCE_STATE_COLLECTED__COLLECTION_TYPE__SOURCE_UPDATED
                        : SAFETY_SOURCE_STATE_COLLECTED__COLLECTION_TYPE__AUTOMATIC;
        PermissionStatsLog.write(
                SAFETY_SOURCE_STATE_COLLECTED,
                idStringToLong(sourceId),
                toSourceStateCollectedProfileType(profileType),
                toSafetySourceStateCollectedSeverityLevel(sourceSeverityLevel),
                openIssuesCount,
                dismissedIssuesCount,
                duplicateFilteredOutIssuesCount,
                sourceState,
                collectionType,
                toSafetySourceStateCollectedCollectionType(safetyEvent),
                refreshReason != null ? refreshReason : UNSET_REFRESH_REASON,
                dataChanged,
                lastUpdatedElapsedTimeMillis != null
                        ? lastUpdatedElapsedTimeMillis
                        : UNSET_LAST_UPDATED_ELAPSED_TIME_MILLIS);
    }

    /**
     * Writes a {@link PermissionStatsLog#SAFETY_CENTER_SYSTEM_EVENT_REPORTED} atom of type {@code
     * SINGLE_SOURCE_RESCAN} or {@code SINGLE_SOURCE_GET_DATA}.
     */
    public static void writeSourceRefreshSystemEvent(
            @RefreshRequestType int refreshType,
            String sourceId,
            @ProfileType int profileType,
            Duration duration,
            @SystemEventResult int result,
            long refreshReason,
            boolean dataChanged) {
        if (!SafetyCenterFlags.getAllowStatsdLogging()) {
            return;
        }
        PermissionStatsLog.write(
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED,
                toSourceRefreshEventType(refreshType),
                idStringToLong(sourceId),
                toSystemEventProfileType(profileType),
                UNSET_ISSUE_TYPE_ID,
                duration.toMillis(),
                result,
                refreshReason,
                dataChanged);
    }

    /**
     * Writes a {@link PermissionStatsLog#SAFETY_CENTER_SYSTEM_EVENT_REPORTED} atom of type {@code
     * COMPLETE_RESCAN} or {@code COMPLETE_GET_DATA}.
     */
    public static void writeWholeRefreshSystemEvent(
            @RefreshRequestType int refreshType,
            Duration duration,
            @SystemEventResult int result,
            long refreshReason,
            boolean dataChanged) {
        if (!SafetyCenterFlags.getAllowStatsdLogging()) {
            return;
        }
        PermissionStatsLog.write(
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED,
                toWholeRefreshEventType(refreshType),
                UNSET_SOURCE_ID,
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_UNKNOWN,
                UNSET_ISSUE_TYPE_ID,
                duration.toMillis(),
                result,
                refreshReason,
                dataChanged);
    }

    /**
     * Writes a {@link PermissionStatsLog#SAFETY_CENTER_SYSTEM_EVENT_REPORTED} atom of type {@code
     * INLINE_ACTION}.
     */
    public static void writeInlineActionSystemEvent(
            String sourceId,
            @ProfileType int profileType,
            @Nullable String issueTypeId,
            Duration duration,
            @SystemEventResult int result) {
        if (!SafetyCenterFlags.getAllowStatsdLogging()) {
            return;
        }
        PermissionStatsLog.write(
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED,
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__INLINE_ACTION,
                idStringToLong(sourceId),
                toSystemEventProfileType(profileType),
                issueTypeId == null ? UNSET_ISSUE_TYPE_ID : idStringToLong(issueTypeId),
                duration.toMillis(),
                result,
                UNSET_REFRESH_REASON,
                UNSET_DATA_CHANGED);
    }

    /**
     * Writes a {@link PermissionStatsLog#SAFETY_CENTER_INTERACTION_REPORTED} atom with the action
     * {@code NOTIFICATION_POSTED}.
     */
    public static void writeNotificationPostedEvent(
            String sourceId,
            @ProfileType int profileType,
            String issueTypeId,
            @SafetySourceData.SeverityLevel int sourceSeverityLevel) {
        writeNotificationInteractionReportedEvent(
                SAFETY_CENTER_INTERACTION_REPORTED__ACTION__NOTIFICATION_POSTED,
                sourceId,
                profileType,
                issueTypeId,
                sourceSeverityLevel);
    }

    /**
     * Writes a {@link PermissionStatsLog#SAFETY_CENTER_INTERACTION_REPORTED} atom with the action
     * {@code NOTIFICATION_DISMISSED}.
     */
    public static void writeNotificationDismissedEvent(
            String sourceId,
            @ProfileType int profileType,
            String issueTypeId,
            @SafetySourceData.SeverityLevel int sourceSeverityLevel) {
        writeNotificationInteractionReportedEvent(
                SAFETY_CENTER_INTERACTION_REPORTED__ACTION__NOTIFICATION_DISMISSED,
                sourceId,
                profileType,
                issueTypeId,
                sourceSeverityLevel);
    }

    /**
     * Writes a {@link PermissionStatsLog#SAFETY_CENTER_INTERACTION_REPORTED} atom with the action
     * {@code ISSUE_PRIMARY_ACTION_CLICKED} or {@code ISSUE_SECONDARY_ACTION_CLICKED}.
     */
    public static void writeNotificationActionClickedEvent(
            String sourceId,
            @ProfileType int profileType,
            String issueTypeId,
            @SafetySourceData.SeverityLevel int sourceSeverityLevel,
            boolean isPrimaryAction) {
        int action =
                isPrimaryAction
                        ? SAFETY_CENTER_INTERACTION_REPORTED__ACTION__ISSUE_PRIMARY_ACTION_CLICKED
                        : SAFETY_CENTER_INTERACTION_REPORTED__ACTION__ISSUE_SECONDARY_ACTION_CLICKED;
        writeNotificationInteractionReportedEvent(
                action, sourceId, profileType, issueTypeId, sourceSeverityLevel);
    }

    private static void writeNotificationInteractionReportedEvent(
            int interactionReportedAction,
            String sourceId,
            @ProfileType int profileType,
            String issueTypeId,
            @SafetySourceData.SeverityLevel int sourceSeverityLevel) {
        if (!SafetyCenterFlags.getAllowStatsdLogging()) {
            return;
        }
        PermissionStatsLog.write(
                SAFETY_CENTER_INTERACTION_REPORTED,
                UNSET_SESSION_ID,
                interactionReportedAction,
                SAFETY_CENTER_INTERACTION_REPORTED__VIEW_TYPE__VIEW_TYPE_NOTIFICATION,
                SAFETY_CENTER_INTERACTION_REPORTED__NAVIGATION_SOURCE__SOURCE_UNKNOWN,
                toInteractionReportedSeverityLevel(sourceSeverityLevel),
                idStringToLong(sourceId),
                toInteractionReportedProfileType(profileType),
                idStringToLong(issueTypeId),
                SAFETY_CENTER_INTERACTION_REPORTED__SENSOR__SENSOR_UNKNOWN,
                UNSET_SOURCE_GROUP_ID,
                SAFETY_CENTER_INTERACTION_REPORTED__ISSUE_STATE__ACTIVE);
    }

    /**
     * Returns a {@link SystemEventResult} based on whether the given operation was {@code
     * successful}.
     */
    @SystemEventResult
    public static int toSystemEventResult(boolean success) {
        return success
                ? SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__SUCCESS
                : SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__ERROR;
    }

    private static int toSourceRefreshEventType(@RefreshRequestType int refreshType) {
        switch (refreshType) {
            case SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA:
                return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__SINGLE_SOURCE_GET_NEW_DATA;
            case SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA:
                return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__SINGLE_SOURCE_RESCAN;
        }
        Log.w(TAG, "Unexpected SafetyCenterManager.RefreshRequestType: " + refreshType);
        return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_UNKNOWN;
    }

    private static int toWholeRefreshEventType(@RefreshRequestType int refreshType) {
        switch (refreshType) {
            case SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA:
                return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__COMPLETE_GET_NEW_DATA;
            case SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA:
                return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__COMPLETE_RESCAN;
        }
        Log.w(TAG, "Unexpected SafetyCenterManager.RefreshRequestType: " + refreshType);
        return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_UNKNOWN;
    }

    private static int toSourceStateCollectedProfileType(@ProfileType int profileType) {
        switch (profileType) {
            case PROFILE_TYPE_PRIMARY:
                return SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
            case PROFILE_TYPE_MANAGED:
                return SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED;
            case PROFILE_TYPE_PRIVATE:
                return SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PRIVATE;
        }
        Log.w(TAG, "state collect arg requested for unknown profile type " + profileType);
        return SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_UNKNOWN;
    }

    private static int toSystemEventProfileType(@ProfileType int profileType) {
        switch (profileType) {
            case PROFILE_TYPE_PRIMARY:
                return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
            case PROFILE_TYPE_MANAGED:
                return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED;
            case PROFILE_TYPE_PRIVATE:
                return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PRIVATE;
        }
        Log.w(TAG, "system event arg requested for unknown profile type " + profileType);
        return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_UNKNOWN;
    }

    private static int toInteractionReportedProfileType(@ProfileType int profileType) {
        switch (profileType) {
            case PROFILE_TYPE_PRIMARY:
                return SAFETY_CENTER_INTERACTION_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
            case PROFILE_TYPE_MANAGED:
                return SAFETY_CENTER_INTERACTION_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED;
            case PROFILE_TYPE_PRIVATE:
                return SAFETY_CENTER_INTERACTION_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PRIVATE;
        }
        Log.w(TAG, "interaction enum requested for unknown profile type " + profileType);
        return SAFETY_CENTER_INTERACTION_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_UNKNOWN;
    }

    /**
     * Converts a {@link String} ID (e.g. a Safety Source ID) to a {@code long} suitable for logging
     * to statsd.
     */
    private static long idStringToLong(String id) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.w(TAG, "Couldn't encode safety source id: " + id, e);
            return 0;
        }
        messageDigest.update(id.getBytes());
        return new BigInteger(messageDigest.digest()).longValue();
    }

    private static int toSafetyStateOverallSeverityLevel(
            @SafetyCenterStatus.OverallSeverityLevel int severityLevel) {
        switch (severityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
                return SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                return SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
        }
        Log.w(TAG, "Unexpected SafetyCenterStatus.OverallSeverityLevel: " + severityLevel);
        return SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
    }

    private static int toSafetySourceStateCollectedSeverityLevel(
            @Nullable @SafetySourceData.SeverityLevel Integer safetySourceSeverityLevel) {
        if (safetySourceSeverityLevel == null) {
            return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
        }
        switch (safetySourceSeverityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
                return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_UNSPECIFIED;
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
        }
        Log.w(TAG, "Unexpected SafetySourceData.SeverityLevel: " + safetySourceSeverityLevel);
        return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
    }

    private static int toInteractionReportedSeverityLevel(
            @SafetySourceData.SeverityLevel int severityLevel) {
        switch (severityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
                return SAFETY_CENTER_INTERACTION_REPORTED__SEVERITY_LEVEL__SAFETY_SEVERITY_UNSPECIFIED;
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return SAFETY_CENTER_INTERACTION_REPORTED__SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return SAFETY_CENTER_INTERACTION_REPORTED__SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return SAFETY_CENTER_INTERACTION_REPORTED__SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
        }
        Log.w(TAG, "Unexpected SafetySourceData.SeverityLevel: " + severityLevel);
        return SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
    }

    private static int toSafetySourceStateCollectedCollectionType(
            @Nullable SafetyEvent safetyEvent) {
        if (safetyEvent == null) {
            return SAFETY_SOURCE_STATE_COLLECTED__UPDATE_TYPE__UPDATE_TYPE_UNKNOWN;
        }
        if (safetyEvent.getType() == SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED) {
            return SAFETY_SOURCE_STATE_COLLECTED__UPDATE_TYPE__REFRESH_RESPONSE;
        } else {
            return SAFETY_SOURCE_STATE_COLLECTED__UPDATE_TYPE__SELF_INITIATED;
        }
    }
}
