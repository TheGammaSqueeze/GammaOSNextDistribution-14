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

import android.annotation.ElapsedRealtimeLong;
import android.content.Context;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.SafetySourceStatus;

import androidx.annotation.Nullable;

import com.android.safetycenter.SafetySourceIssueInfo;
import com.android.safetycenter.SafetySourceKey;
import com.android.safetycenter.UserProfileGroup;
import com.android.safetycenter.UserProfileGroup.ProfileType;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.logging.SafetyCenterStatsdLogger;

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Collates information from various data-related classes and uses that information to log {@code
 * SafetySourceStateCollected} atoms.
 */
@NotThreadSafe
final class SafetySourceStateCollectedLogger {

    private final Context mContext;
    private final SafetySourceDataRepository mSourceDataRepository;
    private final SafetyCenterIssueDismissalRepository mIssueDismissalRepository;
    private final SafetyCenterIssueRepository mIssueRepository;

    SafetySourceStateCollectedLogger(
            Context context,
            SafetySourceDataRepository sourceDataRepository,
            SafetyCenterIssueDismissalRepository issueDismissalRepository,
            SafetyCenterIssueRepository issueRepository) {
        mContext = context;
        mSourceDataRepository = sourceDataRepository;
        mIssueDismissalRepository = issueDismissalRepository;
        mIssueRepository = issueRepository;
    }

    /**
     * Writes a SafetySourceStateCollected atom for the given source in response to a stats pull.
     */
    void writeAutomaticAtom(SafetySourceKey sourceKey, @ProfileType int profileType) {
        logSafetySourceStateCollected(
                sourceKey,
                mSourceDataRepository.getSafetySourceData(sourceKey),
                /* refreshReason= */ null,
                /* sourceDataDiffers= */ false,
                profileType,
                /* safetyEvent= */ null,
                mSourceDataRepository.getSafetySourceLastUpdated(sourceKey));
    }

    /**
     * Writes a SafetySourceStateCollected atom for the given source in response to that source
     * updating its own state.
     */
    void writeSourceUpdatedAtom(
            SafetySourceKey key,
            @Nullable SafetySourceData safetySourceData,
            @Nullable @SafetyCenterManager.RefreshReason Integer refreshReason,
            boolean sourceDataDiffers,
            int userId,
            SafetyEvent safetyEvent) {
        logSafetySourceStateCollected(
                key,
                safetySourceData,
                refreshReason,
                sourceDataDiffers,
                UserProfileGroup.getProfileTypeOfUser(userId, mContext),
                safetyEvent,
                /* lastUpdatedElapsedTimeMillis= */ null);
    }

    private void logSafetySourceStateCollected(
            SafetySourceKey sourceKey,
            @Nullable SafetySourceData sourceData,
            @Nullable @SafetyCenterManager.RefreshReason Integer refreshReason,
            boolean sourceDataDiffers,
            @ProfileType int profileType,
            @Nullable SafetyEvent safetyEvent,
            @Nullable @ElapsedRealtimeLong Long lastUpdatedElapsedTimeMillis) {
        SafetySourceStatus sourceStatus = sourceData == null ? null : sourceData.getStatus();
        List<SafetySourceIssue> sourceIssues =
                sourceData == null ? Collections.emptyList() : sourceData.getIssues();

        int maxSeverityLevel = Integer.MIN_VALUE;
        if (sourceStatus != null) {
            maxSeverityLevel = sourceStatus.getSeverityLevel();
        } else if (sourceData != null) {
            // In this case we know we have an issue-only source because of the checks carried out
            // in the validateRequest function.
            maxSeverityLevel = SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED;
        }

        long openIssuesCount = 0;
        long dismissedIssuesCount = 0;
        for (int i = 0; i < sourceIssues.size(); i++) {
            SafetySourceIssue issue = sourceIssues.get(i);
            if (isIssueDismissed(issue, sourceKey)) {
                dismissedIssuesCount++;
            } else {
                openIssuesCount++;
                maxSeverityLevel = Math.max(maxSeverityLevel, issue.getSeverityLevel());
            }
        }

        Integer severityLevel = maxSeverityLevel > Integer.MIN_VALUE ? maxSeverityLevel : null;
        SafetyCenterStatsdLogger.writeSafetySourceStateCollected(
                sourceKey.getSourceId(),
                profileType,
                severityLevel,
                openIssuesCount,
                dismissedIssuesCount,
                getDuplicateCount(sourceKey),
                mSourceDataRepository.getSourceState(sourceKey),
                safetyEvent,
                refreshReason,
                sourceDataDiffers,
                lastUpdatedElapsedTimeMillis);
    }

    private boolean isIssueDismissed(SafetySourceIssue issue, SafetySourceKey sourceKey) {
        SafetyCenterIssueKey issueKey =
                SafetyCenterIssueKey.newBuilder()
                        .setSafetySourceId(sourceKey.getSourceId())
                        .setSafetySourceIssueId(issue.getId())
                        .setUserId(sourceKey.getUserId())
                        .build();
        return mIssueDismissalRepository.isIssueDismissed(issueKey, issue.getSeverityLevel());
    }

    private long getDuplicateCount(SafetySourceKey sourceKey) {
        long count = 0;
        List<SafetySourceIssueInfo> duplicates =
                mIssueRepository.getLatestDuplicates(sourceKey.getUserId());
        for (int i = 0; i < duplicates.size(); i++) {
            if (duplicates.get(i).getSafetySource().getId().equals(sourceKey.getSourceId())) {
                count++;
            }
        }
        return count;
    }
}
