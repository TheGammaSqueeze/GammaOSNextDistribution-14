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

import static com.android.safetycenter.data.SafetyCenterIssueDeduplicator.DeduplicationInfo;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

import android.annotation.UserIdInt;
import android.content.Context;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.util.SparseArray;

import com.android.modules.utils.build.SdkLevel;
import com.android.safetycenter.SafetyCenterConfigReader;
import com.android.safetycenter.SafetySourceIssueInfo;
import com.android.safetycenter.SafetySourceKey;
import com.android.safetycenter.SafetySources;
import com.android.safetycenter.UserProfileGroup;
import com.android.safetycenter.UserProfileGroup.ProfileType;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Contains issue related data.
 *
 * <p>Responsible for generating lists of issues and deduplication of issues.
 */
@NotThreadSafe
final class SafetyCenterIssueRepository {

    private static final SafetySourceIssuesInfoBySeverityDescending
            SAFETY_SOURCE_ISSUES_INFO_BY_SEVERITY_DESCENDING =
                    new SafetySourceIssuesInfoBySeverityDescending();

    private static final DeduplicationInfo EMPTY_DEDUP_INFO =
            new DeduplicationInfo(emptyList(), emptyList(), emptyMap());

    private final Context mContext;
    private final SafetySourceDataRepository mSafetySourceDataRepository;
    private final SafetyCenterConfigReader mSafetyCenterConfigReader;
    private final SafetyCenterIssueDismissalRepository mSafetyCenterIssueDismissalRepository;
    private final SafetyCenterIssueDeduplicator mSafetyCenterIssueDeduplicator;

    private final SparseArray<DeduplicationInfo> mUserIdToDedupInfo = new SparseArray<>();

    SafetyCenterIssueRepository(
            Context context,
            SafetySourceDataRepository safetySourceDataRepository,
            SafetyCenterConfigReader safetyCenterConfigReader,
            SafetyCenterIssueDismissalRepository safetyCenterIssueDismissalRepository,
            SafetyCenterIssueDeduplicator safetyCenterIssueDeduplicator) {
        mContext = context;
        mSafetySourceDataRepository = safetySourceDataRepository;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
        mSafetyCenterIssueDismissalRepository = safetyCenterIssueDismissalRepository;
        mSafetyCenterIssueDeduplicator = safetyCenterIssueDeduplicator;
    }

    /**
     * Updates the class as per the current state of issues. Should be called after any state update
     * that can affect issues.
     */
    void updateIssues(@UserIdInt int userId) {
        updateIssues(userId, UserProfileGroup.getProfileTypeOfUser(userId, mContext));
    }

    private void updateIssues(@UserIdInt int userId, @ProfileType int profileType) {
        List<SafetySourceIssueInfo> issues =
                getAllStoredIssuesFromRawSourceData(userId, profileType);

        issues.sort(SAFETY_SOURCE_ISSUES_INFO_BY_SEVERITY_DESCENDING);

        mUserIdToDedupInfo.put(userId, produceDedupInfo(issues));
    }

    private DeduplicationInfo produceDedupInfo(List<SafetySourceIssueInfo> issues) {
        if (SdkLevel.isAtLeastU()) {
            return mSafetyCenterIssueDeduplicator.deduplicateIssues(issues);
        }
        return new DeduplicationInfo(issues, emptyList(), emptyMap());
    }

    /**
     * Fetches a list of issues related to the given {@link UserProfileGroup}.
     *
     * <p>Issues in the list are sorted in descending order and deduplicated (if applicable, only on
     * Android U+).
     *
     * <p>Only includes issues related to active/running {@code userId}s in the given {@link
     * UserProfileGroup}.
     */
    List<SafetySourceIssueInfo> getIssuesDedupedSortedDescFor(UserProfileGroup userProfileGroup) {
        List<SafetySourceIssueInfo> issuesInfo = getIssuesFor(userProfileGroup);
        issuesInfo.sort(SAFETY_SOURCE_ISSUES_INFO_BY_SEVERITY_DESCENDING);
        return issuesInfo;
    }

    /**
     * Counts the total number of issues from loggable sources, in the given {@link
     * UserProfileGroup}.
     *
     * <p>Only includes issues related to active/running {@code userId}s in the given {@link
     * UserProfileGroup}.
     */
    int countLoggableIssuesFor(UserProfileGroup userProfileGroup) {
        List<SafetySourceIssueInfo> relevantIssues = getIssuesFor(userProfileGroup);
        int issueCount = 0;
        for (int i = 0; i < relevantIssues.size(); i++) {
            SafetySourceIssueInfo safetySourceIssueInfo = relevantIssues.get(i);
            if (SafetySources.isLoggable(safetySourceIssueInfo.getSafetySource())) {
                issueCount++;
            }
        }
        return issueCount;
    }

    /** Gets a list of all issues for the given {@code userId}. */
    List<SafetySourceIssueInfo> getIssuesForUser(@UserIdInt int userId) {
        return filterOutHiddenIssues(
                mUserIdToDedupInfo.get(userId, EMPTY_DEDUP_INFO).getDeduplicatedIssues());
    }

    /**
     * Returns a set of {@link SafetySourcesGroup} IDs that the given {@link SafetyCenterIssueKey}
     * is mapped to, or an empty list if no such mapping is configured.
     */
    Set<String> getGroupMappingFor(SafetyCenterIssueKey issueKey) {
        return mUserIdToDedupInfo
                .get(issueKey.getUserId(), EMPTY_DEDUP_INFO)
                .getIssueToGroupMapping()
                .getOrDefault(issueKey, emptySet());
    }

    /**
     * Returns the list of issues for the given {@code userId} which were removed from the given
     * list of issues in the most recent {@link SafetyCenterIssueDeduplicator#deduplicateIssues}
     * call. These issues were removed because they were duplicates of other issues.
     *
     * <p>If this method is called before any calls to {@link
     * SafetyCenterIssueDeduplicator#deduplicateIssues} then an empty list is returned.
     */
    List<SafetySourceIssueInfo> getLatestDuplicates(@UserIdInt int userId) {
        return mUserIdToDedupInfo.get(userId, EMPTY_DEDUP_INFO).getFilteredOutDuplicateIssues();
    }

    private List<SafetySourceIssueInfo> filterOutHiddenIssues(List<SafetySourceIssueInfo> issues) {
        List<SafetySourceIssueInfo> result = new ArrayList<>();
        for (int i = 0; i < issues.size(); i++) {
            SafetySourceIssueInfo issueInfo = issues.get(i);
            if (!mSafetyCenterIssueDismissalRepository.isIssueHidden(
                    issueInfo.getSafetyCenterIssueKey())) {
                result.add(issueInfo);
            }
        }
        return result;
    }

    private List<SafetySourceIssueInfo> getAllStoredIssuesFromRawSourceData(
            @UserIdInt int userId, @ProfileType int profileType) {
        List<SafetySourceIssueInfo> allIssuesInfo = new ArrayList<>();

        List<SafetySourcesGroup> safetySourcesGroups =
                mSafetyCenterConfigReader.getSafetySourcesGroups();
        for (int j = 0; j < safetySourcesGroups.size(); j++) {
            addSafetySourceIssuesInfo(
                    allIssuesInfo, safetySourcesGroups.get(j), userId, profileType);
        }

        return allIssuesInfo;
    }

    private void addSafetySourceIssuesInfo(
            List<SafetySourceIssueInfo> issuesInfo,
            SafetySourcesGroup safetySourcesGroup,
            @UserIdInt int userId,
            @ProfileType int profileType) {
        List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
        for (int i = 0; i < safetySources.size(); i++) {
            SafetySource safetySource = safetySources.get(i);

            if (!SafetySources.isExternal(safetySource)) {
                continue;
            }
            if (!SafetySources.supportsProfileType(safetySource, profileType)) {
                continue;
            }

            addSafetySourceIssuesInfo(issuesInfo, safetySource, safetySourcesGroup, userId);
        }
    }

    private void addSafetySourceIssuesInfo(
            List<SafetySourceIssueInfo> issuesInfo,
            SafetySource safetySource,
            SafetySourcesGroup safetySourcesGroup,
            @UserIdInt int userId) {
        SafetySourceKey key = SafetySourceKey.of(safetySource.getId(), userId);
        SafetySourceData safetySourceData = mSafetySourceDataRepository.getSafetySourceData(key);

        if (safetySourceData == null) {
            return;
        }

        List<SafetySourceIssue> safetySourceIssues = safetySourceData.getIssues();
        for (int i = 0; i < safetySourceIssues.size(); i++) {
            SafetySourceIssue safetySourceIssue = safetySourceIssues.get(i);

            SafetySourceIssueInfo safetySourceIssueInfo =
                    new SafetySourceIssueInfo(
                            safetySourceIssue, safetySource, safetySourcesGroup, userId);
            issuesInfo.add(safetySourceIssueInfo);
        }
    }

    /**
     * Only includes issues related to active/running {@code userId}s in the given {@link
     * UserProfileGroup}.
     */
    private List<SafetySourceIssueInfo> getIssuesFor(UserProfileGroup userProfileGroup) {
        List<SafetySourceIssueInfo> issues = new ArrayList<>();

        int[] allRunningProfileUserIds = userProfileGroup.getAllRunningProfilesUserIds();
        for (int i = 0; i < allRunningProfileUserIds.length; i++) {
            issues.addAll(getIssuesForUser(allRunningProfileUserIds[i]));
        }

        return issues;
    }

    /** A comparator to order {@link SafetySourceIssueInfo} by severity level descending. */
    private static final class SafetySourceIssuesInfoBySeverityDescending
            implements Comparator<SafetySourceIssueInfo> {

        private SafetySourceIssuesInfoBySeverityDescending() {}

        @Override
        public int compare(SafetySourceIssueInfo left, SafetySourceIssueInfo right) {
            return Integer.compare(
                    right.getSafetySourceIssue().getSeverityLevel(),
                    left.getSafetySourceIssue().getSeverityLevel());
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(PrintWriter fout) {
        fout.println("ISSUE REPOSITORY");
        for (int i = 0; i < mUserIdToDedupInfo.size(); i++) {
            fout.println();
            fout.println("\tUSER ID=" + mUserIdToDedupInfo.keyAt(i));
            fout.println("\tDEDUPLICATION INFO=" + mUserIdToDedupInfo.valueAt(i));
        }
        fout.println();
    }

    /** Clears all the data from the repository. */
    void clear() {
        mUserIdToDedupInfo.clear();
    }

    /** Clears all data related to the given {@code userId}. */
    void clearForUser(@UserIdInt int userId) {
        mUserIdToDedupInfo.delete(userId);
    }
}
