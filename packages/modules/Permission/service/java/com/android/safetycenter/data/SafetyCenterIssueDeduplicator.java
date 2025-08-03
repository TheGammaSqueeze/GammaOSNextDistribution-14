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

import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

import android.annotation.UserIdInt;
import android.safetycenter.config.SafetySourcesGroup;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.safetycenter.SafetySourceIssueInfo;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/** Deduplicates issues based on deduplication info provided by the source and the issue. */
@NotThreadSafe
final class SafetyCenterIssueDeduplicator {

    private static final String TAG = "SafetyCenterDedup";

    private final SafetyCenterIssueDismissalRepository mSafetyCenterIssueDismissalRepository;

    SafetyCenterIssueDeduplicator(
            SafetyCenterIssueDismissalRepository safetyCenterIssueDismissalRepository) {
        this.mSafetyCenterIssueDismissalRepository = safetyCenterIssueDismissalRepository;
    }

    /**
     * Accepts a list of issues sorted by priority and filters out duplicates.
     *
     * <p>Issues are considered duplicate if they have the same deduplication id and were sent by
     * sources which are part of the same deduplication group. All but the highest priority
     * duplicate issue will be filtered out.
     *
     * <p>In case any issue, in the bucket of duplicate issues, was dismissed, all issues of the
     * same or lower severity will be dismissed as well.
     *
     * @return deduplicated list of issues, and some other information gathered in the deduplication
     *     process
     */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    DeduplicationInfo deduplicateIssues(List<SafetySourceIssueInfo> sortedIssues) {
        // (dedup key) -> list(issues)
        ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> dedupBuckets =
                createDedupBuckets(sortedIssues);

        // There is no further work to do when there are no dedup buckets
        if (dedupBuckets.isEmpty()) {
            return new DeduplicationInfo(new ArrayList<>(sortedIssues), emptyList(), emptyMap());
        }

        alignAllDismissals(dedupBuckets);

        ArraySet<SafetyCenterIssueKey> duplicatesToFilterOut =
                getDuplicatesToFilterOut(dedupBuckets);

        resurfaceHiddenIssuesIfNeeded(dedupBuckets);

        if (duplicatesToFilterOut.isEmpty()) {
            return new DeduplicationInfo(new ArrayList<>(sortedIssues), emptyList(), emptyMap());
        }

        ArrayMap<SafetyCenterIssueKey, Set<String>> issueToGroupMap =
                getTopIssueToGroupMapping(dedupBuckets);

        List<SafetySourceIssueInfo> filteredOut = new ArrayList<>(duplicatesToFilterOut.size());
        List<SafetySourceIssueInfo> deduplicatedIssues = new ArrayList<>();
        for (int i = 0; i < sortedIssues.size(); i++) {
            SafetySourceIssueInfo issueInfo = sortedIssues.get(i);
            SafetyCenterIssueKey issueKey = issueInfo.getSafetyCenterIssueKey();
            if (duplicatesToFilterOut.contains(issueKey)) {
                filteredOut.add(issueInfo);
                // mark as temporarily hidden, which will delay showing these issues if the top
                // issue gets resolved.
                mSafetyCenterIssueDismissalRepository.hideIssue(issueKey);
            } else {
                deduplicatedIssues.add(issueInfo);
            }
        }

        return new DeduplicationInfo(deduplicatedIssues, filteredOut, issueToGroupMap);
    }

    private void resurfaceHiddenIssuesIfNeeded(
            ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> dedupBuckets) {
        for (int i = 0; i < dedupBuckets.size(); i++) {
            List<SafetySourceIssueInfo> duplicates = dedupBuckets.valueAt(i);
            if (duplicates.isEmpty()) {
                Log.w(TAG, "List of duplicates in a deduplication bucket is empty");
                continue;
            }

            // top issue in the bucket, if hidden, should resurface after certain period
            SafetyCenterIssueKey topIssueKey = duplicates.get(0).getSafetyCenterIssueKey();
            if (mSafetyCenterIssueDismissalRepository.isIssueHidden(topIssueKey)) {
                mSafetyCenterIssueDismissalRepository.resurfaceHiddenIssueAfterPeriod(topIssueKey);
            }
        }
    }

    /**
     * Creates a mapping from the top issue in each dedupBucket to all groups in that dedupBucket.
     */
    private ArrayMap<SafetyCenterIssueKey, Set<String>> getTopIssueToGroupMapping(
            ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> dedupBuckets) {
        ArrayMap<SafetyCenterIssueKey, Set<String>> issueToGroupMap = new ArrayMap<>();
        for (int i = 0; i < dedupBuckets.size(); i++) {
            List<SafetySourceIssueInfo> duplicates = dedupBuckets.valueAt(i);

            boolean noMappingBecauseNoDuplicates = duplicates.size() < 2;
            if (noMappingBecauseNoDuplicates) {
                continue;
            }

            SafetyCenterIssueKey topIssueKey = duplicates.get(0).getSafetyCenterIssueKey();
            for (int j = 0; j < duplicates.size(); j++) {
                Set<String> groups = issueToGroupMap.getOrDefault(topIssueKey, new ArraySet<>());
                groups.add(duplicates.get(j).getSafetySourcesGroup().getId());
                if (j == duplicates.size() - 1) { // last element, no more modifications
                    groups = unmodifiableSet(groups);
                }
                issueToGroupMap.put(topIssueKey, groups);
            }
        }

        return issueToGroupMap;
    }

    /**
     * Handles dismissals logic: in each bucket, dismissal details of the highest priority (top)
     * dismissed issue will be copied to all other duplicate issues in that bucket, that are of
     * equal or lower severity (not priority). Notification-dismissal details are handled similarly.
     */
    private void alignAllDismissals(
            ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> dedupBuckets) {
        for (int i = 0; i < dedupBuckets.size(); i++) {
            List<SafetySourceIssueInfo> duplicates = dedupBuckets.valueAt(i);
            if (duplicates.size() < 2) {
                continue;
            }
            SafetySourceIssueInfo topDismissed = getHighestPriorityDismissedIssue(duplicates);
            SafetySourceIssueInfo topNotificationDismissed =
                    getHighestPriorityNotificationDismissedIssue(duplicates);
            alignDismissalsInBucket(topDismissed, duplicates);
            alignNotificationDismissalsInBucket(topNotificationDismissed, duplicates);
        }
    }

    /**
     * Dismisses all recipient issues of lower or equal severity than the given top dismissed issue
     * in the bucket.
     */
    private void alignDismissalsInBucket(
            @Nullable SafetySourceIssueInfo topDismissed, List<SafetySourceIssueInfo> duplicates) {
        if (topDismissed == null) {
            return;
        }
        SafetyCenterIssueKey topDismissedKey = topDismissed.getSafetyCenterIssueKey();
        List<SafetyCenterIssueKey> recipients = getRecipientKeys(topDismissed, duplicates);
        for (int i = 0; i < recipients.size(); i++) {
            mSafetyCenterIssueDismissalRepository.copyDismissalData(
                    topDismissedKey, recipients.get(i));
        }
    }

    /**
     * Dismisses notifications for all recipient issues of lower or equal severity than the given
     * top notification-dismissed issue in the bucket.
     */
    private void alignNotificationDismissalsInBucket(
            @Nullable SafetySourceIssueInfo topNotificationDismissed,
            List<SafetySourceIssueInfo> duplicates) {
        if (topNotificationDismissed == null) {
            return;
        }
        SafetyCenterIssueKey topNotificationDismissedKey =
                topNotificationDismissed.getSafetyCenterIssueKey();
        List<SafetyCenterIssueKey> recipients =
                getRecipientKeys(topNotificationDismissed, duplicates);
        for (int i = 0; i < recipients.size(); i++) {
            mSafetyCenterIssueDismissalRepository.copyNotificationDismissalData(
                    topNotificationDismissedKey, recipients.get(i));
        }
    }

    /**
     * Returns the "recipient" issues for the given top issue from a bucket of duplicates.
     * Recipients are those issues with a lower or equal severity level. The top issue is not its
     * own recipient.
     */
    private List<SafetyCenterIssueKey> getRecipientKeys(
            SafetySourceIssueInfo topIssue, List<SafetySourceIssueInfo> duplicates) {
        ArrayList<SafetyCenterIssueKey> recipients = new ArrayList<>();
        SafetyCenterIssueKey topKey = topIssue.getSafetyCenterIssueKey();
        int topSeverity = topIssue.getSafetySourceIssue().getSeverityLevel();

        for (int i = 0; i < duplicates.size(); i++) {
            SafetySourceIssueInfo issueInfo = duplicates.get(i);
            SafetyCenterIssueKey issueKey = issueInfo.getSafetyCenterIssueKey();
            if (!issueKey.equals(topKey)
                    && issueInfo.getSafetySourceIssue().getSeverityLevel() <= topSeverity) {
                recipients.add(issueKey);
            }
        }
        return recipients;
    }

    @Nullable
    private SafetySourceIssueInfo getHighestPriorityDismissedIssue(
            List<SafetySourceIssueInfo> duplicates) {
        for (int i = 0; i < duplicates.size(); i++) {
            SafetySourceIssueInfo issueInfo = duplicates.get(i);
            if (mSafetyCenterIssueDismissalRepository.isIssueDismissed(
                    issueInfo.getSafetyCenterIssueKey(),
                    issueInfo.getSafetySourceIssue().getSeverityLevel())) {
                return issueInfo;
            }
        }

        return null;
    }

    @Nullable
    private SafetySourceIssueInfo getHighestPriorityNotificationDismissedIssue(
            List<SafetySourceIssueInfo> duplicates) {
        for (int i = 0; i < duplicates.size(); i++) {
            SafetySourceIssueInfo issueInfo = duplicates.get(i);
            if (mSafetyCenterIssueDismissalRepository.isNotificationDismissedNow(
                    issueInfo.getSafetyCenterIssueKey(),
                    issueInfo.getSafetySourceIssue().getSeverityLevel())) {
                return issueInfo;
            }
        }

        return null;
    }

    /** Returns a set of duplicate issues that need to be filtered out. */
    private ArraySet<SafetyCenterIssueKey> getDuplicatesToFilterOut(
            ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> dedupBuckets) {
        ArraySet<SafetyCenterIssueKey> duplicatesToFilterOut = new ArraySet<>();

        for (int i = 0; i < dedupBuckets.size(); i++) {
            List<SafetySourceIssueInfo> duplicates = dedupBuckets.valueAt(i);

            // all but the top one in the bucket
            for (int j = 1; j < duplicates.size(); j++) {
                SafetyCenterIssueKey issueKey = duplicates.get(j).getSafetyCenterIssueKey();
                duplicatesToFilterOut.add(issueKey);
            }
        }

        return duplicatesToFilterOut;
    }

    /** Returns a mapping (dedup key) -> list(issues). */
    private static ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> createDedupBuckets(
            List<SafetySourceIssueInfo> sortedIssues) {
        ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> dedupBuckets = new ArrayMap<>();

        for (int i = 0; i < sortedIssues.size(); i++) {
            SafetySourceIssueInfo issueInfo = sortedIssues.get(i);
            DeduplicationKey dedupKey = getDedupKey(issueInfo);
            if (dedupKey == null) {
                continue;
            }

            // each bucket will remain sorted
            List<SafetySourceIssueInfo> bucket =
                    dedupBuckets.getOrDefault(dedupKey, new ArrayList<>());
            bucket.add(issueInfo);

            dedupBuckets.put(dedupKey, bucket);
        }

        return dedupBuckets;
    }

    /** Returns deduplication key of the given {@code issueInfo}. */
    @Nullable
    private static DeduplicationKey getDedupKey(SafetySourceIssueInfo issueInfo) {
        String deduplicationGroup = issueInfo.getSafetySource().getDeduplicationGroup();
        String deduplicationId = issueInfo.getSafetySourceIssue().getDeduplicationId();

        if (deduplicationGroup == null || deduplicationId == null) {
            return null;
        }
        return new DeduplicationKey(
                deduplicationGroup,
                deduplicationId,
                issueInfo.getSafetyCenterIssueKey().getUserId());
    }

    /** Encapsulates deduplication result along with some additional information. */
    static final class DeduplicationInfo {
        private final List<SafetySourceIssueInfo> mDeduplicatedIssues;
        private final List<SafetySourceIssueInfo> mFilteredOutDuplicates;
        private final Map<SafetyCenterIssueKey, Set<String>> mIssueToGroup;

        /** Creates a new {@link DeduplicationInfo}. */
        DeduplicationInfo(
                List<SafetySourceIssueInfo> deduplicatedIssues,
                List<SafetySourceIssueInfo> filteredOutDuplicates,
                Map<SafetyCenterIssueKey, Set<String>> issueToGroup) {
            mDeduplicatedIssues = unmodifiableList(deduplicatedIssues);
            mFilteredOutDuplicates = unmodifiableList(filteredOutDuplicates);
            mIssueToGroup = unmodifiableMap(issueToGroup);
        }

        /**
         * Returns the list of issues which were removed from the given list of issues in the most
         * recent {@link SafetyCenterIssueDeduplicator#deduplicateIssues} call. These issues were
         * removed because they were duplicates of other issues.
         */
        List<SafetySourceIssueInfo> getFilteredOutDuplicateIssues() {
            return mFilteredOutDuplicates;
        }

        /**
         * Returns a mapping between a {@link SafetyCenterIssueKey} and {@link SafetySourcesGroup}
         * IDs, that was a result of the most recent {@link
         * SafetyCenterIssueDeduplicator#deduplicateIssues} call.
         *
         * <p>If present, such an entry represents an issue mapping to all the safety source groups
         * of others issues which were filtered out as its duplicates. It also contains a mapping to
         * its own source group.
         *
         * <p>If an issue didn't have any duplicates, it won't be present in the result.
         */
        Map<SafetyCenterIssueKey, Set<String>> getIssueToGroupMapping() {
            return mIssueToGroup;
        }

        /** Returns the deduplication result, the deduplicated list of issues. */
        List<SafetySourceIssueInfo> getDeduplicatedIssues() {
            return mDeduplicatedIssues;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DeduplicationInfo)) return false;
            DeduplicationInfo that = (DeduplicationInfo) o;
            return mDeduplicatedIssues.equals(that.mDeduplicatedIssues)
                    && mFilteredOutDuplicates.equals(that.mFilteredOutDuplicates)
                    && mIssueToGroup.equals(that.mIssueToGroup);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDeduplicatedIssues, mFilteredOutDuplicates, mIssueToGroup);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("DeduplicationInfo:");

            sb.append("\n\tDeduplicatedIssues:");
            for (int i = 0; i < mDeduplicatedIssues.size(); i++) {
                sb.append("\n\t\tSafetySourceIssueInfo=").append(mDeduplicatedIssues.get(i));
            }

            sb.append("\n\tFilteredOutDuplicates:");
            for (int i = 0; i < mFilteredOutDuplicates.size(); i++) {
                sb.append("\n\t\tSafetySourceIssueInfo=").append(mFilteredOutDuplicates.get(i));
            }

            sb.append("\n\tIssueToGroupMapping");
            for (Map.Entry<SafetyCenterIssueKey, Set<String>> entry : mIssueToGroup.entrySet()) {
                sb.append("\n\t\tSafetyCenterIssueKey=")
                        .append(toUserFriendlyString(entry.getKey()))
                        .append(" maps to groups: ");
                for (String group : entry.getValue()) {
                    sb.append(group).append(",");
                }
            }

            return sb.toString();
        }
    }

    private static final class DeduplicationKey {

        private final String mDeduplicationGroup;
        private final String mDeduplicationId;
        private final int mUserId;

        private DeduplicationKey(
                String deduplicationGroup, String deduplicationId, @UserIdInt int userId) {
            mDeduplicationGroup = deduplicationGroup;
            mDeduplicationId = deduplicationId;
            mUserId = userId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDeduplicationGroup, mDeduplicationId, mUserId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DeduplicationKey)) return false;
            DeduplicationKey dedupKey = (DeduplicationKey) o;
            return mDeduplicationGroup.equals(dedupKey.mDeduplicationGroup)
                    && mDeduplicationId.equals(dedupKey.mDeduplicationId)
                    && mUserId == dedupKey.mUserId;
        }
    }
}
