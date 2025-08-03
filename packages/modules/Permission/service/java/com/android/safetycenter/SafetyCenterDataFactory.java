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

import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM;

import static com.android.safetycenter.UserProfileGroup.PROFILE_TYPE_MANAGED;
import static com.android.safetycenter.UserProfileGroup.PROFILE_TYPE_PRIMARY;
import static com.android.safetycenter.UserProfileGroup.PROFILE_TYPE_PRIVATE;
import static com.android.safetycenter.internaldata.SafetyCenterBundles.ISSUES_TO_GROUPS_BUNDLE_KEY;
import static com.android.safetycenter.internaldata.SafetyCenterBundles.STATIC_ENTRIES_TO_IDS_BUNDLE_KEY;

import static java.util.Collections.emptyList;

import android.annotation.TargetApi;
import android.annotation.UserIdInt;
import android.app.PendingIntent;
import android.content.Context;
import android.icu.text.ListFormatter;
import android.icu.text.MessageFormat;
import android.icu.util.ULocale;
import android.os.Bundle;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterEntry;
import android.safetycenter.SafetyCenterEntryGroup;
import android.safetycenter.SafetyCenterEntryOrGroup;
import android.safetycenter.SafetyCenterIssue;
import android.safetycenter.SafetyCenterIssue.Action.ConfirmationDialogDetails;
import android.safetycenter.SafetyCenterStaticEntry;
import android.safetycenter.SafetyCenterStaticEntryGroup;
import android.safetycenter.SafetyCenterStatus;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.SafetySourceStatus;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;
import com.android.permission.flags.Flags;
import com.android.safetycenter.UserProfileGroup.ProfileType;
import com.android.safetycenter.data.SafetyCenterDataManager;
import com.android.safetycenter.internaldata.SafetyCenterBundles;
import com.android.safetycenter.internaldata.SafetyCenterEntryId;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.resources.SafetyCenterResourcesApk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Aggregates {@link SafetySourceData} to build {@link SafetyCenterData} instances which are shared
 * with Safety Center listeners, including PermissionController.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 *
 * @hide
 */
@NotThreadSafe
public final class SafetyCenterDataFactory {

    private static final String TAG = "SafetyCenterDataFactory";

    private static final String ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID = "AndroidLockScreenSources";

    private final Context mContext;
    private final SafetyCenterResourcesApk mSafetyCenterResourcesApk;
    private final SafetyCenterConfigReader mSafetyCenterConfigReader;
    private final SafetyCenterRefreshTracker mSafetyCenterRefreshTracker;
    private final PendingIntentFactory mPendingIntentFactory;

    private final SafetyCenterDataManager mSafetyCenterDataManager;

    SafetyCenterDataFactory(
            Context context,
            SafetyCenterResourcesApk safetyCenterResourcesApk,
            SafetyCenterConfigReader safetyCenterConfigReader,
            SafetyCenterRefreshTracker safetyCenterRefreshTracker,
            PendingIntentFactory pendingIntentFactory,
            SafetyCenterDataManager safetyCenterDataManager) {
        mContext = context;
        mSafetyCenterResourcesApk = safetyCenterResourcesApk;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
        mSafetyCenterRefreshTracker = safetyCenterRefreshTracker;
        mPendingIntentFactory = pendingIntentFactory;
        mSafetyCenterDataManager = safetyCenterDataManager;
    }

    /**
     * Returns a default {@link SafetyCenterData} object to be returned when the API is disabled.
     */
    static SafetyCenterData getDefaultSafetyCenterData() {
        SafetyCenterStatus defaultSafetyCenterStatus =
                new SafetyCenterStatus.Builder("", "")
                        .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
                        .build();
        if (SdkLevel.isAtLeastU()) {
            return new SafetyCenterData.Builder(defaultSafetyCenterStatus).build();
        } else {
            return new SafetyCenterData(
                    defaultSafetyCenterStatus, emptyList(), emptyList(), emptyList());
        }
    }

    /**
     * Returns the current {@link SafetyCenterData} for the given {@code packageName} and {@link
     * UserProfileGroup}, aggregated from all the {@link SafetySourceData} set so far.
     *
     * <p>If a {@link SafetySourceData} was not set, the default value from the {@link
     * SafetyCenterConfig} is used.
     */
    SafetyCenterData assembleSafetyCenterData(
            String packageName, UserProfileGroup userProfileGroup) {
        return assembleSafetyCenterData(packageName, userProfileGroup, getAllGroups());
    }

    /**
     * Returns the current {@link SafetyCenterData} for the given {@code packageName} and {@link
     * UserProfileGroup}, aggregated from {@link SafetySourceData} set by the specified {@link
     * SafetySourcesGroup}s.
     *
     * <p>If a {@link SafetySourceData} was not set, the default value from the {@link
     * SafetyCenterConfig} is used.
     */
    public SafetyCenterData assembleSafetyCenterData(
            String packageName,
            UserProfileGroup userProfileGroup,
            List<SafetySourcesGroup> safetySourcesGroups) {
        List<SafetyCenterEntryOrGroup> safetyCenterEntryOrGroups = new ArrayList<>();
        List<SafetyCenterStaticEntryGroup> safetyCenterStaticEntryGroups = new ArrayList<>();
        SafetyCenterOverallState safetyCenterOverallState = new SafetyCenterOverallState();
        Bundle staticEntriesToIds = new Bundle();

        for (int i = 0; i < safetySourcesGroups.size(); i++) {
            SafetySourcesGroup safetySourcesGroup = safetySourcesGroups.get(i);

            int safetySourcesGroupType = safetySourcesGroup.getType();
            switch (safetySourcesGroupType) {
                case SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL:
                    addSafetyCenterEntryGroup(
                            safetyCenterOverallState,
                            safetyCenterEntryOrGroups,
                            safetySourcesGroup,
                            packageName,
                            userProfileGroup);
                    break;
                case SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATELESS:
                    addSafetyCenterStaticEntryGroup(
                            staticEntriesToIds,
                            safetyCenterOverallState,
                            safetyCenterStaticEntryGroups,
                            safetySourcesGroup,
                            packageName,
                            userProfileGroup);
                    break;
                case SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN:
                    break;
                default:
                    Log.w(TAG, "Unexpected SafetySourceGroupType: " + safetySourcesGroupType);
                    break;
            }
        }

        List<SafetySourceIssueInfo> issuesInfo =
                mSafetyCenterDataManager.getIssuesDedupedSortedDescFor(userProfileGroup);

        List<SafetyCenterIssue> safetyCenterIssues = new ArrayList<>();
        List<SafetyCenterIssue> safetyCenterDismissedIssues = new ArrayList<>();
        SafetySourceIssueInfo topNonDismissedIssueInfo = null;
        int numTipIssues = 0;
        int numAutomaticIssues = 0;
        Bundle issuesToGroups = new Bundle();

        for (int i = 0; i < issuesInfo.size(); i++) {
            SafetySourceIssueInfo issueInfo = issuesInfo.get(i);
            SafetyCenterIssue safetyCenterIssue =
                    toSafetyCenterIssue(
                            issueInfo.getSafetySourceIssue(),
                            issueInfo.getSafetySourcesGroup(),
                            issueInfo.getSafetyCenterIssueKey());

            if (mSafetyCenterDataManager.isIssueDismissed(
                    issueInfo.getSafetyCenterIssueKey(),
                    issueInfo.getSafetySourceIssue().getSeverityLevel())) {
                safetyCenterDismissedIssues.add(safetyCenterIssue);
            } else {
                safetyCenterIssues.add(safetyCenterIssue);
                safetyCenterOverallState.addIssueOverallSeverityLevel(
                        toSafetyCenterStatusOverallSeverityLevel(
                                issueInfo.getSafetySourceIssue().getSeverityLevel()));
                if (topNonDismissedIssueInfo == null) {
                    topNonDismissedIssueInfo = issueInfo;
                }
                if (isTip(issueInfo.getSafetySourceIssue())) {
                    numTipIssues++;
                } else if (isAutomatic(issueInfo.getSafetySourceIssue())) {
                    numAutomaticIssues++;
                }
            }

            if (SdkLevel.isAtLeastU()) {
                updateIssuesToGroups(
                        issuesToGroups,
                        issueInfo.getSafetyCenterIssueKey(),
                        safetyCenterIssue.getId());
            }
        }

        int refreshStatus = mSafetyCenterRefreshTracker.getRefreshStatus();
        SafetyCenterStatus safetyCenterStatus =
                new SafetyCenterStatus.Builder(
                                getSafetyCenterStatusTitle(
                                        safetyCenterOverallState.getOverallSeverityLevel(),
                                        topNonDismissedIssueInfo,
                                        refreshStatus,
                                        safetyCenterOverallState.hasSettingsToReview()),
                                getSafetyCenterStatusSummary(
                                        safetyCenterOverallState,
                                        topNonDismissedIssueInfo,
                                        refreshStatus,
                                        numTipIssues,
                                        numAutomaticIssues,
                                        safetyCenterIssues.size()))
                        .setSeverityLevel(safetyCenterOverallState.getOverallSeverityLevel())
                        .setRefreshStatus(refreshStatus)
                        .build();

        if (SdkLevel.isAtLeastU()) {
            SafetyCenterData.Builder builder = new SafetyCenterData.Builder(safetyCenterStatus);
            for (int i = 0; i < safetyCenterIssues.size(); i++) {
                builder.addIssue(safetyCenterIssues.get(i));
            }
            for (int i = 0; i < safetyCenterEntryOrGroups.size(); i++) {
                builder.addEntryOrGroup(safetyCenterEntryOrGroups.get(i));
            }
            for (int i = 0; i < safetyCenterStaticEntryGroups.size(); i++) {
                builder.addStaticEntryGroup(safetyCenterStaticEntryGroups.get(i));
            }
            for (int i = 0; i < safetyCenterDismissedIssues.size(); i++) {
                builder.addDismissedIssue(safetyCenterDismissedIssues.get(i));
            }

            Bundle extras = new Bundle();
            if (!issuesToGroups.isEmpty()) {
                extras.putBundle(ISSUES_TO_GROUPS_BUNDLE_KEY, issuesToGroups);
            }
            if (!staticEntriesToIds.isEmpty()) {
                extras.putBundle(STATIC_ENTRIES_TO_IDS_BUNDLE_KEY, staticEntriesToIds);
            }
            if (!issuesToGroups.isEmpty() || !staticEntriesToIds.isEmpty()) {
                builder.setExtras(extras);
            }

            return builder.build();
        } else {
            return new SafetyCenterData(
                    safetyCenterStatus,
                    safetyCenterIssues,
                    safetyCenterEntryOrGroups,
                    safetyCenterStaticEntryGroups);
        }
    }

    private List<SafetySourcesGroup> getAllGroups() {
        return mSafetyCenterConfigReader.getSafetySourcesGroups();
    }

    private void updateIssuesToGroups(
            Bundle issuesToGroups, SafetyCenterIssueKey issueKey, String safetyCenterIssueId) {
        Set<String> groups = mSafetyCenterDataManager.getGroupMappingFor(issueKey);
        if (!groups.isEmpty()) {
            issuesToGroups.putStringArrayList(safetyCenterIssueId, new ArrayList<>(groups));
        }
    }

    private SafetyCenterIssue toSafetyCenterIssue(
            SafetySourceIssue safetySourceIssue,
            SafetySourcesGroup safetySourcesGroup,
            SafetyCenterIssueKey safetyCenterIssueKey) {
        SafetyCenterIssueId safetyCenterIssueId =
                SafetyCenterIssueId.newBuilder()
                        .setSafetyCenterIssueKey(safetyCenterIssueKey)
                        .setIssueTypeId(safetySourceIssue.getIssueTypeId())
                        .build();

        List<SafetySourceIssue.Action> safetySourceIssueActions = safetySourceIssue.getActions();
        List<SafetyCenterIssue.Action> safetyCenterIssueActions =
                new ArrayList<>(safetySourceIssueActions.size());
        for (int i = 0; i < safetySourceIssueActions.size(); i++) {
            SafetySourceIssue.Action safetySourceIssueAction = safetySourceIssueActions.get(i);

            safetyCenterIssueActions.add(
                    toSafetyCenterIssueAction(
                            safetySourceIssueAction,
                            safetyCenterIssueId.getSafetyCenterIssueKey()));
        }

        int safetyCenterIssueSeverityLevel =
                toSafetyCenterIssueSeverityLevel(safetySourceIssue.getSeverityLevel());
        SafetyCenterIssue.Builder safetyCenterIssueBuilder =
                new SafetyCenterIssue.Builder(
                                SafetyCenterIds.encodeToString(safetyCenterIssueId),
                                safetySourceIssue.getTitle(),
                                safetySourceIssue.getSummary())
                        .setSeverityLevel(safetyCenterIssueSeverityLevel)
                        .setShouldConfirmDismissal(
                                safetyCenterIssueSeverityLevel
                                        > SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
                        .setSubtitle(safetySourceIssue.getSubtitle())
                        .setActions(safetyCenterIssueActions);
        if (SdkLevel.isAtLeastU()) {
            CharSequence issueAttributionTitle =
                    TextUtils.isEmpty(safetySourceIssue.getAttributionTitle())
                            ? mSafetyCenterResourcesApk.getOptionalString(
                                    safetySourcesGroup.getTitleResId())
                            : safetySourceIssue.getAttributionTitle();
            safetyCenterIssueBuilder.setAttributionTitle(issueAttributionTitle);
            safetyCenterIssueBuilder.setGroupId(safetySourcesGroup.getId());
        }
        return safetyCenterIssueBuilder.build();
    }

    private SafetyCenterIssue.Action toSafetyCenterIssueAction(
            SafetySourceIssue.Action safetySourceIssueAction,
            SafetyCenterIssueKey safetyCenterIssueKey) {
        SafetyCenterIssueActionId safetyCenterIssueActionId =
                SafetyCenterIssueActionId.newBuilder()
                        .setSafetyCenterIssueKey(safetyCenterIssueKey)
                        .setSafetySourceIssueActionId(safetySourceIssueAction.getId())
                        .build();

        SafetyCenterIssue.Action.Builder builder =
                new SafetyCenterIssue.Action.Builder(
                                SafetyCenterIds.encodeToString(safetyCenterIssueActionId),
                                safetySourceIssueAction.getLabel(),
                                safetySourceIssueAction.getPendingIntent())
                        .setSuccessMessage(safetySourceIssueAction.getSuccessMessage())
                        .setIsInFlight(
                                mSafetyCenterDataManager.actionIsInFlight(
                                        safetyCenterIssueActionId))
                        .setWillResolve(safetySourceIssueAction.willResolve());
        if (SdkLevel.isAtLeastU()) {
            if (safetySourceIssueAction.getConfirmationDialogDetails() != null) {
                SafetySourceIssue.Action.ConfirmationDialogDetails detailsFrom =
                        safetySourceIssueAction.getConfirmationDialogDetails();
                ConfirmationDialogDetails detailsTo =
                        new ConfirmationDialogDetails(
                                detailsFrom.getTitle(),
                                detailsFrom.getText(),
                                detailsFrom.getAcceptButtonText(),
                                detailsFrom.getDenyButtonText());
                builder.setConfirmationDialogDetails(detailsTo);
            }
        }
        return builder.build();
    }

    private void addSafetyCenterEntryGroup(
            SafetyCenterOverallState safetyCenterOverallState,
            List<SafetyCenterEntryOrGroup> safetyCenterEntryOrGroups,
            SafetySourcesGroup safetySourcesGroup,
            String defaultPackageName,
            UserProfileGroup userProfileGroup) {
        int groupSafetyCenterEntryLevel = SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED;

        List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
        List<SafetyCenterEntry> entries = new ArrayList<>(safetySources.size());
        for (int safetySourceIdx = 0; safetySourceIdx < safetySources.size(); ++safetySourceIdx) {
            SafetySource safetySource = safetySources.get(safetySourceIdx);
            for (int profileTypeIdx = 0;
                    profileTypeIdx < ProfileType.ALL_PROFILE_TYPES.length;
                    ++profileTypeIdx) {
                @ProfileType int profileType = ProfileType.ALL_PROFILE_TYPES[profileTypeIdx];
                if (!SafetySources.supportsProfileType(safetySource, profileType)) {
                    continue;
                }

                int[] profileIds = userProfileGroup.getProfilesOfType(profileType);
                for (int profileIdx = 0; profileIdx < profileIds.length; profileIdx++) {
                    int profileId = profileIds[profileIdx];
                    boolean isUserRunning =
                            userProfileGroup.containsRunningUserId(profileId, profileType);
                    if (profileType == PROFILE_TYPE_PRIVATE && !isUserRunning) {
                        continue;
                    }
                    groupSafetyCenterEntryLevel =
                            mergeSafetyCenterEntrySeverityLevels(
                                    groupSafetyCenterEntryLevel,
                                    addSafetyCenterEntry(
                                            safetyCenterOverallState,
                                            entries,
                                            safetySource,
                                            defaultPackageName,
                                            profileId,
                                            profileType,
                                            isUserRunning));
                }
            }
        }

        if (entries.size() == 0) {
            return;
        }

        if (!SafetyCenterFlags.getShowSubpages() && entries.size() == 1) {
            safetyCenterEntryOrGroups.add(new SafetyCenterEntryOrGroup(entries.get(0)));
            return;
        }

        CharSequence groupSummary =
                getSafetyCenterEntryGroupSummary(
                        safetySourcesGroup, groupSafetyCenterEntryLevel, entries);
        safetyCenterEntryOrGroups.add(
                new SafetyCenterEntryOrGroup(
                        new SafetyCenterEntryGroup.Builder(
                                        safetySourcesGroup.getId(),
                                        mSafetyCenterResourcesApk.getString(
                                                safetySourcesGroup.getTitleResId()))
                                .setSeverityLevel(groupSafetyCenterEntryLevel)
                                .setSummary(groupSummary)
                                .setEntries(entries)
                                .setSeverityUnspecifiedIconType(
                                        toGroupSeverityUnspecifiedIconType(
                                                safetySourcesGroup.getStatelessIconType()))
                                .build()));
    }

    @SafetyCenterEntry.EntrySeverityLevel
    private static int mergeSafetyCenterEntrySeverityLevels(
            @SafetyCenterEntry.EntrySeverityLevel int left,
            @SafetyCenterEntry.EntrySeverityLevel int right) {
        if (left > SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK
                || right > SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK) {
            return Math.max(left, right);
        }
        if (left == SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN
                || right == SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN) {
            return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
        }
        return Math.max(left, right);
    }

    @Nullable
    private CharSequence getSafetyCenterEntryGroupSummary(
            SafetySourcesGroup safetySourcesGroup,
            @SafetyCenterEntry.EntrySeverityLevel int groupSafetyCenterEntryLevel,
            List<SafetyCenterEntry> entries) {
        switch (groupSafetyCenterEntryLevel) {
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING:
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION:
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK:
                for (int i = 0; i < entries.size(); i++) {
                    SafetyCenterEntry entry = entries.get(i);

                    CharSequence entrySummary = entry.getSummary();
                    if (entry.getSeverityLevel() != groupSafetyCenterEntryLevel
                            || entrySummary == null) {
                        continue;
                    }

                    if (groupSafetyCenterEntryLevel > SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK) {
                        return entrySummary;
                    }

                    SafetySourceKey key = toSafetySourceKey(entry.getId());
                    SafetySourceData safetySourceData =
                            mSafetyCenterDataManager.getSafetySourceDataInternal(key);
                    boolean hasIssues =
                            safetySourceData != null && !safetySourceData.getIssues().isEmpty();

                    if (hasIssues) {
                        return entrySummary;
                    }
                }

                return getDefaultGroupSummary(safetySourcesGroup, entries);
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED:
                return getDefaultGroupSummary(safetySourcesGroup, entries);
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN:
                for (int i = 0; i < entries.size(); i++) {
                    SafetySourceKey key = toSafetySourceKey(entries.get(i).getId());
                    if (mSafetyCenterDataManager.sourceHasError(key)) {
                        return getRefreshErrorString();
                    }
                }
                return mSafetyCenterResourcesApk.getStringByName("group_unknown_summary");
        }

        Log.w(
                TAG,
                "Unexpected SafetyCenterEntry.EntrySeverityLevel for SafetyCenterEntryGroup: "
                        + groupSafetyCenterEntryLevel);
        return getDefaultGroupSummary(safetySourcesGroup, entries);
    }

    @Nullable
    private CharSequence getDefaultGroupSummary(
            SafetySourcesGroup safetySourcesGroup, List<SafetyCenterEntry> entries) {
        CharSequence groupSummary =
                mSafetyCenterResourcesApk.getOptionalString(safetySourcesGroup.getSummaryResId());

        if (safetySourcesGroup.getId().equals(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)
                && TextUtils.isEmpty(groupSummary)) {
            List<CharSequence> titles = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                SafetyCenterEntry entry = entries.get(i);
                SafetyCenterEntryId entryId = SafetyCenterIds.entryIdFromString(entry.getId());

                if (UserProfileGroup.getProfileTypeOfUser(entryId.getUserId(), mContext)
                        != PROFILE_TYPE_PRIMARY) {
                    continue;
                }

                titles.add(entry.getTitle());
            }
            groupSummary =
                    ListFormatter.getInstance(
                                    ULocale.getDefault(ULocale.Category.FORMAT),
                                    ListFormatter.Type.AND,
                                    ListFormatter.Width.NARROW)
                            .format(titles);
        }

        return groupSummary;
    }

    @SafetyCenterEntry.EntrySeverityLevel
    private int addSafetyCenterEntry(
            SafetyCenterOverallState safetyCenterOverallState,
            List<SafetyCenterEntry> entries,
            SafetySource safetySource,
            String defaultPackageName,
            @UserIdInt int userId,
            @ProfileType int profileType,
            boolean isUserRunning) {
        SafetyCenterEntry safetyCenterEntry =
                toSafetyCenterEntry(
                        safetySource,
                        defaultPackageName,
                        userId,
                        profileType,
                        isUserRunning);
        if (safetyCenterEntry == null) {
            return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED;
        }

        safetyCenterOverallState.addEntryOverallSeverityLevel(
                entryToSafetyCenterStatusOverallSeverityLevel(
                        safetyCenterEntry.getSeverityLevel()));
        entries.add(safetyCenterEntry);

        return safetyCenterEntry.getSeverityLevel();
    }

    @Nullable
    private SafetyCenterEntry toSafetyCenterEntry(
            SafetySource safetySource,
            String defaultPackageName,
            @UserIdInt int userId,
            @ProfileType int profileType,
            boolean isUserRunning) {
        switch (safetySource.getType()) {
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY:
                return null;
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC:
                SafetySourceKey key = SafetySourceKey.of(safetySource.getId(), userId);
                SafetySourceStatus safetySourceStatus =
                        getSafetySourceStatus(
                                mSafetyCenterDataManager.getSafetySourceDataInternal(key));
                boolean inQuietMode = (PROFILE_TYPE_MANAGED == profileType) && !isUserRunning;
                if (safetySourceStatus == null) {
                    int severityLevel =
                            inQuietMode
                                    ? SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
                                    : SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
                    return toDefaultSafetyCenterEntry(
                            safetySource,
                            safetySource.getPackageName(),
                            severityLevel,
                            SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION,
                            userId,
                            profileType,
                            isUserRunning);
                }
                PendingIntent sourceProvidedPendingIntent =
                        inQuietMode ? null : safetySourceStatus.getPendingIntent();
                PendingIntent entryPendingIntent =
                        sourceProvidedPendingIntent != null
                                ? sourceProvidedPendingIntent
                                : mPendingIntentFactory.getPendingIntent(
                                        safetySource.getId(),
                                        safetySource.getIntentAction(),
                                        safetySource.getPackageName(),
                                        userId,
                                        inQuietMode);
                boolean enabled =
                        safetySourceStatus.isEnabled()
                                && !inQuietMode
                                && entryPendingIntent != null;
                SafetyCenterEntryId safetyCenterEntryId =
                        SafetyCenterEntryId.newBuilder()
                                .setSafetySourceId(safetySource.getId())
                                .setUserId(userId)
                                .build();
                int severityUnspecifiedIconType =
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION;
                int severityLevel =
                        enabled
                                ? toSafetyCenterEntrySeverityLevel(
                                        safetySourceStatus.getSeverityLevel())
                                : SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED;
                SafetyCenterEntry.Builder builder =
                        new SafetyCenterEntry.Builder(
                                        SafetyCenterIds.encodeToString(safetyCenterEntryId),
                                        safetySourceStatus.getTitle())
                                .setSeverityLevel(severityLevel)
                                .setSummary(
                                        inQuietMode
                                                ? DevicePolicyResources.getWorkProfilePausedString(
                                                        mSafetyCenterResourcesApk)
                                                : safetySourceStatus.getSummary())
                                .setEnabled(enabled)
                                .setSeverityUnspecifiedIconType(severityUnspecifiedIconType)
                                .setPendingIntent(entryPendingIntent);
                SafetySourceStatus.IconAction iconAction = safetySourceStatus.getIconAction();
                if (iconAction == null) {
                    return builder.build();
                }
                return builder.setIconAction(
                                new SafetyCenterEntry.IconAction(
                                        toSafetyCenterEntryIconActionType(iconAction.getIconType()),
                                        iconAction.getPendingIntent()))
                        .build();
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC:
                return toDefaultSafetyCenterEntry(
                        safetySource,
                        getStaticSourcePackageNameOrDefault(safetySource, defaultPackageName),
                        SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED,
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON,
                        userId,
                        profileType,
                        isUserRunning);
        }
        Log.w(
                TAG,
                "Unknown safety source type found in collapsible group: " + safetySource.getType());
        return null;
    }

    @Nullable
    private SafetyCenterEntry toDefaultSafetyCenterEntry(
            SafetySource safetySource,
            String packageName,
            @SafetyCenterEntry.EntrySeverityLevel int entrySeverityLevel,
            @SafetyCenterEntry.SeverityUnspecifiedIconType int severityUnspecifiedIconType,
            @UserIdInt int userId,
            @ProfileType int profileType,
            boolean isUserRunning) {
        if (SafetySources.isDefaultEntryHidden(safetySource)) {
            return null;
        }

        SafetyCenterEntryId safetyCenterEntryId =
                SafetyCenterEntryId.newBuilder()
                        .setSafetySourceId(safetySource.getId())
                        .setUserId(userId)
                        .build();
        boolean isQuietModeEnabled = (PROFILE_TYPE_MANAGED == profileType) && !isUserRunning;
        PendingIntent pendingIntent =
                mPendingIntentFactory.getPendingIntent(
                        safetySource.getId(),
                        safetySource.getIntentAction(),
                        packageName,
                        userId,
                        isQuietModeEnabled);
        boolean enabled =
                pendingIntent != null && !SafetySources.isDefaultEntryDisabled(safetySource);
        CharSequence title = getTitleForProfileType(profileType, safetySource);
        CharSequence summary =
                mSafetyCenterDataManager.sourceHasError(
                                SafetySourceKey.of(safetySource.getId(), userId))
                        ? getRefreshErrorString()
                        : mSafetyCenterResourcesApk.getOptionalString(
                                safetySource.getSummaryResId());
        if (isQuietModeEnabled) {
            enabled = false;
            summary = DevicePolicyResources.getWorkProfilePausedString(mSafetyCenterResourcesApk);
        }
        return new SafetyCenterEntry.Builder(
                        SafetyCenterIds.encodeToString(safetyCenterEntryId), title)
                .setSeverityLevel(entrySeverityLevel)
                .setSummary(summary)
                .setEnabled(enabled)
                .setPendingIntent(pendingIntent)
                .setSeverityUnspecifiedIconType(severityUnspecifiedIconType)
                .build();
    }

    private void addSafetyCenterStaticEntryGroup(
            Bundle staticEntriesToIds,
            SafetyCenterOverallState safetyCenterOverallState,
            List<SafetyCenterStaticEntryGroup> safetyCenterStaticEntryGroups,
            SafetySourcesGroup safetySourcesGroup,
            String defaultPackageName,
            UserProfileGroup userProfileGroup) {
        List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
        List<SafetyCenterStaticEntry> staticEntries = new ArrayList<>(safetySources.size());
        for (int safetySourceIdx = 0; safetySourceIdx < safetySources.size(); safetySourceIdx++) {
            SafetySource safetySource = safetySources.get(safetySourceIdx);
            for (int profileTypeIdx = 0;
                    profileTypeIdx < ProfileType.ALL_PROFILE_TYPES.length;
                    ++profileTypeIdx) {
                @ProfileType int profileType = ProfileType.ALL_PROFILE_TYPES[profileTypeIdx];
                if (!SafetySources.supportsProfileType(safetySource, profileType)) {
                    continue;
                }
                int[] profileIds = userProfileGroup.getProfilesOfType(profileType);
                for (int profileIdx = 0; profileIdx < profileIds.length; ++profileIdx) {
                    int profileId = profileIds[profileIdx];
                    boolean isUserRunning =
                            userProfileGroup.containsRunningUserId(profileId, profileType);
                    if (profileType == PROFILE_TYPE_PRIVATE && !isUserRunning) {
                        continue;
                    }
                    addSafetyCenterStaticEntry(
                            staticEntriesToIds,
                            safetyCenterOverallState,
                            staticEntries,
                            safetySource,
                            defaultPackageName,
                            profileId,
                            profileType,
                            isUserRunning);
                }
            }
        }

        if (staticEntries.isEmpty()) {
            return;
        }

        safetyCenterStaticEntryGroups.add(
                new SafetyCenterStaticEntryGroup(
                        mSafetyCenterResourcesApk.getString(safetySourcesGroup.getTitleResId()),
                        staticEntries));
    }

    private void addSafetyCenterStaticEntry(
            Bundle staticEntriesToIds,
            SafetyCenterOverallState safetyCenterOverallState,
            List<SafetyCenterStaticEntry> staticEntries,
            SafetySource safetySource,
            String defaultPackageName,
            @UserIdInt int userId,
            @ProfileType int profileType,
            boolean isUserRunning) {
        SafetyCenterStaticEntry staticEntry =
                toSafetyCenterStaticEntry(
                        safetySource,
                        defaultPackageName,
                        userId,
                        profileType,
                        isUserRunning);
        if (staticEntry == null) {
            return;
        }
        if (SdkLevel.isAtLeastU()) {
            staticEntriesToIds.putString(
                    SafetyCenterBundles.toBundleKey(staticEntry),
                    SafetyCenterIds.encodeToString(
                            SafetyCenterEntryId.newBuilder()
                                    .setSafetySourceId(safetySource.getId())
                                    .setUserId(userId)
                                    .build()));
        }
        boolean hasError =
                mSafetyCenterDataManager.sourceHasError(
                        SafetySourceKey.of(safetySource.getId(), userId));
        if (hasError) {
            safetyCenterOverallState.addEntryOverallSeverityLevel(
                    SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN);
        }
        staticEntries.add(staticEntry);
    }

    @Nullable
    private SafetyCenterStaticEntry toSafetyCenterStaticEntry(
            SafetySource safetySource,
            String defaultPackageName,
            @UserIdInt int userId,
            @ProfileType int profileType,
            boolean isUserRunning) {
        switch (safetySource.getType()) {
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY:
                return null;
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC:
                SafetySourceKey key = SafetySourceKey.of(safetySource.getId(), userId);
                SafetySourceStatus safetySourceStatus =
                        getSafetySourceStatus(
                                mSafetyCenterDataManager.getSafetySourceDataInternal(key));
                boolean inQuietMode = (profileType == PROFILE_TYPE_MANAGED) && !isUserRunning;
                if (safetySourceStatus != null) {
                    PendingIntent sourceProvidedPendingIntent =
                            inQuietMode ? null : safetySourceStatus.getPendingIntent();
                    PendingIntent entryPendingIntent =
                            sourceProvidedPendingIntent != null
                                    ? sourceProvidedPendingIntent
                                    : mPendingIntentFactory.getPendingIntent(
                                            safetySource.getId(),
                                            safetySource.getIntentAction(),
                                            safetySource.getPackageName(),
                                            userId,
                                            inQuietMode);
                    if (entryPendingIntent == null) {
                        // TODO(b/222838784): Decide strategy for static entries when the intent is
                        //  null.
                        return null;
                    }
                    return new SafetyCenterStaticEntry.Builder(safetySourceStatus.getTitle())
                            .setSummary(
                                    inQuietMode
                                            ? DevicePolicyResources.getWorkProfilePausedString(
                                                    mSafetyCenterResourcesApk)
                                            : safetySourceStatus.getSummary())
                            .setPendingIntent(entryPendingIntent)
                            .build();
                }
                return toDefaultSafetyCenterStaticEntry(
                        safetySource,
                        safetySource.getPackageName(),
                        userId,
                        profileType,
                        isUserRunning);
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC:
                return toDefaultSafetyCenterStaticEntry(
                        safetySource,
                        getStaticSourcePackageNameOrDefault(safetySource, defaultPackageName),
                        userId,
                        profileType,
                        isUserRunning);
        }
        Log.w(TAG, "Unknown safety source type found in rigid group: " + safetySource.getType());
        return null;
    }

    @Nullable
    private SafetyCenterStaticEntry toDefaultSafetyCenterStaticEntry(
            SafetySource safetySource,
            String packageName,
            @UserIdInt int userId,
            @ProfileType int profileType,
            boolean isUserRunning) {
        if (SafetySources.isDefaultEntryHidden(safetySource)) {
            return null;
        }
        boolean isQuietModeEnabled = (profileType == PROFILE_TYPE_MANAGED) && !isUserRunning;
        PendingIntent pendingIntent =
                mPendingIntentFactory.getPendingIntent(
                        safetySource.getId(),
                        safetySource.getIntentAction(),
                        packageName,
                        userId,
                        isQuietModeEnabled);

        if (pendingIntent == null) {
            // TODO(b/222838784): Decide strategy for static entries when the intent is null.
            return null;
        }

        CharSequence title = getTitleForProfileType(profileType, safetySource);
        CharSequence summary =
                mSafetyCenterDataManager.sourceHasError(
                                SafetySourceKey.of(safetySource.getId(), userId))
                        ? getRefreshErrorString()
                        : mSafetyCenterResourcesApk.getOptionalString(
                                safetySource.getSummaryResId());
        if (isQuietModeEnabled) {
            summary = DevicePolicyResources.getWorkProfilePausedString(mSafetyCenterResourcesApk);
        }
        return new SafetyCenterStaticEntry.Builder(title)
                .setSummary(summary)
                .setPendingIntent(pendingIntent)
                .build();
    }

    @Nullable
    private static SafetySourceStatus getSafetySourceStatus(
            @Nullable SafetySourceData safetySourceData) {
        if (safetySourceData == null) {
            return null;
        }

        return safetySourceData.getStatus();
    }

    private static String getStaticSourcePackageNameOrDefault(
            SafetySource safetySource, String defaultPackageName) {
        if (!SdkLevel.isAtLeastU()) {
            return defaultPackageName;
        }
        String sourcePackageName = safetySource.getOptionalPackageName();
        if (sourcePackageName == null) {
            return defaultPackageName;
        }
        return sourcePackageName;
    }

    @SafetyCenterStatus.OverallSeverityLevel
    private static int toSafetyCenterStatusOverallSeverityLevel(
            @SafetySourceData.SeverityLevel int safetySourceSeverityLevel) {
        switch (safetySourceSeverityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(TAG, "Unexpected SafetySourceData.SeverityLevel: " + safetySourceSeverityLevel);
        return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;
    }

    @SafetyCenterStatus.OverallSeverityLevel
    private static int entryToSafetyCenterStatusOverallSeverityLevel(
            @SafetyCenterEntry.EntrySeverityLevel int safetyCenterEntrySeverityLevel) {
        switch (safetyCenterEntrySeverityLevel) {
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED:
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(
                TAG,
                "Unexpected SafetyCenterEntry.EntrySeverityLevel: "
                        + safetyCenterEntrySeverityLevel);
        return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;
    }

    @SafetyCenterEntry.EntrySeverityLevel
    private static int toSafetyCenterEntrySeverityLevel(
            @SafetySourceData.SeverityLevel int safetySourceSeverityLevel) {
        switch (safetySourceSeverityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED;
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(
                TAG,
                "Unexpected SafetySourceData.SeverityLevel in SafetySourceStatus: "
                        + safetySourceSeverityLevel);
        return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
    }

    @SafetyCenterIssue.IssueSeverityLevel
    private static int toSafetyCenterIssueSeverityLevel(
            @SafetySourceData.SeverityLevel int safetySourceIssueSeverityLevel) {
        switch (safetySourceIssueSeverityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
                Log.w(
                        TAG,
                        "Unexpected use of SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED in "
                                + "SafetySourceIssue");
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK;
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(
                TAG,
                "Unexpected SafetySourceData.SeverityLevel in SafetySourceIssue: "
                        + safetySourceIssueSeverityLevel);
        return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK;
    }

    @SafetyCenterEntry.SeverityUnspecifiedIconType
    private static int toGroupSeverityUnspecifiedIconType(
            @SafetySourcesGroup.StatelessIconType int statelessIconType) {
        switch (statelessIconType) {
            case SafetySourcesGroup.STATELESS_ICON_TYPE_NONE:
                return SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION;
            case SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY:
                return SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY;
        }

        Log.w(TAG, "Unexpected SafetySourcesGroup.StatelessIconType: " + statelessIconType);
        return SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON;
    }

    @SafetyCenterEntry.IconAction.IconActionType
    private static int toSafetyCenterEntryIconActionType(
            @SafetySourceStatus.IconAction.IconType int safetySourceIconActionType) {
        switch (safetySourceIconActionType) {
            case SafetySourceStatus.IconAction.ICON_TYPE_GEAR:
                return SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR;
            case SafetySourceStatus.IconAction.ICON_TYPE_INFO:
                return SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO;
        }

        Log.w(
                TAG,
                "Unexpected SafetySourceStatus.IconAction.IconActionType: "
                        + safetySourceIconActionType);
        return SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO;
    }

    private String getSafetyCenterStatusTitle(
            @SafetyCenterStatus.OverallSeverityLevel int overallSeverityLevel,
            @Nullable SafetySourceIssueInfo topNonDismissedIssueInfo,
            @SafetyCenterStatus.RefreshStatus int refreshStatus,
            boolean hasSettingsToReview) {
        boolean overallSeverityUnknown =
                overallSeverityLevel == SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;
        String refreshStatusTitle =
                getSafetyCenterRefreshStatusTitle(refreshStatus, overallSeverityUnknown);
        if (refreshStatusTitle != null) {
            return refreshStatusTitle;
        }
        switch (overallSeverityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                if (hasSettingsToReview) {
                    return mSafetyCenterResourcesApk.getStringByName(
                            "overall_severity_level_ok_review_title");
                }
                return mSafetyCenterResourcesApk.getStringByName("overall_severity_level_ok_title");
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return getStatusTitleFromIssueCategories(
                        topNonDismissedIssueInfo,
                        "overall_severity_level_device_recommendation_title",
                        "overall_severity_level_account_recommendation_title",
                        "overall_severity_level_safety_recommendation_title",
                        "overall_severity_level_data_recommendation_title",
                        "overall_severity_level_passwords_recommendation_title",
                        "overall_severity_level_personal_recommendation_title");
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return getStatusTitleFromIssueCategories(
                        topNonDismissedIssueInfo,
                        "overall_severity_level_critical_device_warning_title",
                        "overall_severity_level_critical_account_warning_title",
                        "overall_severity_level_critical_safety_warning_title",
                        "overall_severity_level_critical_data_warning_title",
                        "overall_severity_level_critical_passwords_warning_title",
                        "overall_severity_level_critical_personal_warning_title");
        }

        Log.w(TAG, "Unexpected SafetyCenterStatus.OverallSeverityLevel: " + overallSeverityLevel);
        return "";
    }

    @TargetApi(UPSIDE_DOWN_CAKE)
    private String getStatusTitleFromIssueCategories(
            @Nullable SafetySourceIssueInfo topNonDismissedIssueInfo,
            String deviceResourceName,
            String accountResourceName,
            String generalResourceName,
            String dataResourceName,
            String passwordsResourceName,
            String personalSafetyResourceName) {
        String generalString = mSafetyCenterResourcesApk.getStringByName(generalResourceName);
        if (topNonDismissedIssueInfo == null) {
            Log.w(TAG, "No safety center issues found in a non-green status");
            return generalString;
        }
        int issueCategory = topNonDismissedIssueInfo.getSafetySourceIssue().getIssueCategory();
        switch (issueCategory) {
            case SafetySourceIssue.ISSUE_CATEGORY_DEVICE:
                return mSafetyCenterResourcesApk.getStringByName(deviceResourceName);
            case SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT:
                return mSafetyCenterResourcesApk.getStringByName(accountResourceName);
            case SafetySourceIssue.ISSUE_CATEGORY_GENERAL:
                return generalString;
            case SafetySourceIssue.ISSUE_CATEGORY_DATA:
                return mSafetyCenterResourcesApk.getStringByName(dataResourceName);
            case SafetySourceIssue.ISSUE_CATEGORY_PASSWORDS:
                return mSafetyCenterResourcesApk.getStringByName(passwordsResourceName);
            case SafetySourceIssue.ISSUE_CATEGORY_PERSONAL_SAFETY:
                return mSafetyCenterResourcesApk.getStringByName(personalSafetyResourceName);
        }

        Log.w(TAG, "Unexpected SafetySourceIssue.IssueCategory: " + issueCategory);
        return generalString;
    }

    private String getSafetyCenterStatusSummary(
            SafetyCenterOverallState safetyCenterOverallState,
            @Nullable SafetySourceIssueInfo topNonDismissedIssue,
            @SafetyCenterStatus.RefreshStatus int refreshStatus,
            int numTipIssues,
            int numAutomaticIssues,
            int numIssues) {
        String refreshStatusSummary = getSafetyCenterRefreshStatusSummary(refreshStatus);
        if (refreshStatusSummary != null) {
            return refreshStatusSummary;
        }
        int overallSeverityLevel = safetyCenterOverallState.getOverallSeverityLevel();
        switch (overallSeverityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                if (topNonDismissedIssue == null) {
                    if (safetyCenterOverallState.hasSettingsToReview()) {
                        return mSafetyCenterResourcesApk.getStringByName(
                                "overall_severity_level_ok_review_summary");
                    }
                    return mSafetyCenterResourcesApk.getStringByName(
                            "overall_severity_level_ok_summary");
                } else if (isTip(topNonDismissedIssue.getSafetySourceIssue())) {
                    return getIcuPluralsString("overall_severity_level_tip_summary", numTipIssues);

                } else if (isAutomatic(topNonDismissedIssue.getSafetySourceIssue())) {
                    return getIcuPluralsString(
                            "overall_severity_level_action_taken_summary", numAutomaticIssues);
                }
                // Fall through.
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return getIcuPluralsString("overall_severity_n_alerts_summary", numIssues);
        }

        Log.w(TAG, "Unexpected SafetyCenterStatus.OverallSeverityLevel: " + overallSeverityLevel);
        return "";
    }

    private static boolean isTip(SafetySourceIssue safetySourceIssue) {
        return SdkLevel.isAtLeastU()
                && safetySourceIssue.getIssueActionability()
                        == SafetySourceIssue.ISSUE_ACTIONABILITY_TIP;
    }

    private static boolean isAutomatic(SafetySourceIssue safetySourceIssue) {
        return SdkLevel.isAtLeastU()
                && safetySourceIssue.getIssueActionability()
                        == SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC;
    }

    private String getRefreshErrorString() {
        return getIcuPluralsString("refresh_error", /* count= */ 1);
    }

    private String getIcuPluralsString(String name, int count, Object... formatArgs) {
        MessageFormat messageFormat =
                new MessageFormat(
                        mSafetyCenterResourcesApk.getStringByName(name, formatArgs),
                        Locale.getDefault());
        ArrayMap<String, Object> arguments = new ArrayMap<>();
        arguments.put("count", count);
        return messageFormat.format(arguments);
    }

    @Nullable
    private String getSafetyCenterRefreshStatusTitle(
            @SafetyCenterStatus.RefreshStatus int refreshStatus, boolean overallSeverityUnknown) {
        switch (refreshStatus) {
            case SafetyCenterStatus.REFRESH_STATUS_NONE:
                return null;
            case SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS:
                if (!overallSeverityUnknown) {
                    return null;
                }
                // Fall through.
            case SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS:
                return mSafetyCenterResourcesApk.getStringByName("scanning_title");
        }

        Log.w(TAG, "Unexpected SafetyCenterStatus.RefreshStatus: " + refreshStatus);
        return null;
    }

    @Nullable
    private String getSafetyCenterRefreshStatusSummary(
            @SafetyCenterStatus.RefreshStatus int refreshStatus) {
        switch (refreshStatus) {
            case SafetyCenterStatus.REFRESH_STATUS_NONE:
                return null;
            case SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS:
            case SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS:
                return mSafetyCenterResourcesApk.getStringByName("loading_summary");
        }

        Log.w(TAG, "Unexpected SafetyCenterStatus.RefreshStatus: " + refreshStatus);
        return null;
    }

    private CharSequence getTitleForProfileType(
            @ProfileType int profileType, SafetySource safetySource) {
        switch (profileType) {
            case PROFILE_TYPE_PRIMARY:
                return mSafetyCenterResourcesApk.getString(safetySource.getTitleResId());
            case PROFILE_TYPE_MANAGED:
                return DevicePolicyResources.getSafetySourceWorkString(
                    mSafetyCenterResourcesApk,
                    safetySource.getId(),
                    safetySource.getTitleForWorkResId());
            case PROFILE_TYPE_PRIVATE:
                if (SdkLevel.isAtLeastV() && Flags.privateProfileTitleApi()) {
                    return mSafetyCenterResourcesApk.getString(
                            safetySource.getTitleForPrivateProfileResId());
                }
                Log.w(TAG, "unsupported private profile type encountered");
                return mSafetyCenterResourcesApk.getString(safetySource.getTitleResId());
            default:
                Log.w(TAG, "unexpected value for the profile type " + profileType);
                return mSafetyCenterResourcesApk.getString(safetySource.getTitleResId());
        }
    }

    private static SafetySourceKey toSafetySourceKey(String safetyCenterEntryIdString) {
        SafetyCenterEntryId id = SafetyCenterIds.entryIdFromString(safetyCenterEntryIdString);
        return SafetySourceKey.of(id.getSafetySourceId(), id.getUserId());
    }

    /**
     * An internal mutable class to keep track of the overall {@link SafetyCenterStatus} severity
     * level and whether the list of entries provided requires attention.
     */
    private static final class SafetyCenterOverallState {

        @SafetyCenterStatus.OverallSeverityLevel
        private int mIssuesOverallSeverityLevel = SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;

        @SafetyCenterStatus.OverallSeverityLevel
        private int mEntriesOverallSeverityLevel = SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;

        /**
         * Adds a {@link SafetyCenterStatus.OverallSeverityLevel} computed from an issue.
         *
         * <p>The {@code overallSeverityLevel} provided cannot be {@link
         * SafetyCenterStatus#OVERALL_SEVERITY_LEVEL_UNKNOWN}. If the data for an issue is not
         * provided yet, this will be reflected when calling {@link
         * #addEntryOverallSeverityLevel(int)}. The exception to that are issue-only safety sources
         * but since they do not have user-visible entries they do not affect whether the overall
         * status is unknown.
         */
        private void addIssueOverallSeverityLevel(
                @SafetyCenterStatus.OverallSeverityLevel int issueOverallSeverityLevel) {
            if (issueOverallSeverityLevel == SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN) {
                return;
            }
            mIssuesOverallSeverityLevel =
                    mergeOverallSeverityLevels(
                            mIssuesOverallSeverityLevel, issueOverallSeverityLevel);
        }

        /**
         * Adds a {@link SafetyCenterStatus.OverallSeverityLevel} computed from an entry.
         *
         * <p>Entries may be unknown (e.g. due to an error or no data provided yet). In this case,
         * the overall status will be marked as unknown if there are no recommendations or critical
         * issues.
         */
        private void addEntryOverallSeverityLevel(
                @SafetyCenterStatus.OverallSeverityLevel int entryOverallSeverityLevel) {
            mEntriesOverallSeverityLevel =
                    mergeOverallSeverityLevels(
                            mEntriesOverallSeverityLevel, entryOverallSeverityLevel);
        }

        /**
         * Returns the {@link SafetyCenterStatus.OverallSeverityLevel} computed.
         *
         * <p>Returns {@link SafetyCenterStatus#OVERALL_SEVERITY_LEVEL_UNKNOWN} if any entry is
         * unknown / has errored-out and there are no recommendations or critical issues.
         *
         * <p>Otherwise, this is computed based on the maximum severity level of issues.
         */
        @SafetyCenterStatus.OverallSeverityLevel
        private int getOverallSeverityLevel() {
            if (mEntriesOverallSeverityLevel == SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
                    && mIssuesOverallSeverityLevel
                            <= SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK) {
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;
            }
            return mIssuesOverallSeverityLevel;
        }

        /**
         * Returns whether there are settings to review (i.e. at least one entry has a more severe
         * status than the overall status, or if any entry is not yet known / has errored-out).
         */
        private boolean hasSettingsToReview() {
            return mEntriesOverallSeverityLevel == SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
                    || mEntriesOverallSeverityLevel > mIssuesOverallSeverityLevel;
        }

        @SafetyCenterStatus.OverallSeverityLevel
        private static int mergeOverallSeverityLevels(
                @SafetyCenterStatus.OverallSeverityLevel int left,
                @SafetyCenterStatus.OverallSeverityLevel int right) {
            if (left == SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
                    || right == SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN) {
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;
            }
            return Math.max(left, right);
        }
    }
}
