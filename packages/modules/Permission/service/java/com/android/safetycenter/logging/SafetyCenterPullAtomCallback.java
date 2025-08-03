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

import static com.android.permission.PermissionStatsLog.SAFETY_STATE;

import android.annotation.UserIdInt;
import android.app.StatsManager;
import android.app.StatsManager.StatsPullAtomCallback;
import android.content.Context;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.util.Log;
import android.util.StatsEvent;

import com.android.internal.annotations.GuardedBy;
import com.android.modules.utils.build.SdkLevel;
import com.android.permission.PermissionStatsLog;
import com.android.safetycenter.ApiLock;
import com.android.safetycenter.SafetyCenterConfigReader;
import com.android.safetycenter.SafetyCenterDataFactory;
import com.android.safetycenter.SafetyCenterFlags;
import com.android.safetycenter.SafetySourceKey;
import com.android.safetycenter.SafetySources;
import com.android.safetycenter.UserProfileGroup;
import com.android.safetycenter.UserProfileGroup.ProfileType;
import com.android.safetycenter.data.SafetyCenterDataManager;

import java.util.List;

/**
 * A {@link StatsPullAtomCallback} that provides a {@link PermissionStatsLog#SAFETY_STATE} atom that
 * when requested by the {@link StatsManager}.
 *
 * <p>Whenever that atom, which describes the overall Safety Center, is pulled this class also
 * separately writes one {@code SAFETY_SOURCE_STATE_COLLECTED} atom for each active source (per
 * profile).
 *
 * @hide
 */
public final class SafetyCenterPullAtomCallback implements StatsPullAtomCallback {

    private static final String TAG = "SafetyCenterPullAtom";

    private final Context mContext;
    private final ApiLock mApiLock;

    @GuardedBy("mApiLock")
    private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    @GuardedBy("mApiLock")
    private final SafetyCenterDataFactory mDataFactory;

    @GuardedBy("mApiLock")
    private final SafetyCenterDataManager mDataManager;

    public SafetyCenterPullAtomCallback(
            Context context,
            ApiLock apiLock,
            SafetyCenterConfigReader safetyCenterConfigReader,
            SafetyCenterDataFactory dataFactory,
            SafetyCenterDataManager dataManager) {
        mContext = context;
        mApiLock = apiLock;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
        mDataFactory = dataFactory;
        mDataManager = dataManager;
    }

    @Override
    public int onPullAtom(int atomTag, List<StatsEvent> statsEvents) {
        if (atomTag != SAFETY_STATE) {
            Log.w(TAG, "Attempt to pull atom: " + atomTag + ", but only SAFETY_STATE is supported");
            return StatsManager.PULL_SKIP;
        }
        if (!SafetyCenterFlags.getSafetyCenterEnabled()) {
            Log.i(TAG, "Attempt to pull SAFETY_STATE, but Safety Center is disabled");
            return StatsManager.PULL_SKIP;
        }
        List<UserProfileGroup> userProfileGroups =
                UserProfileGroup.getAllUserProfileGroups(mContext);
        synchronized (mApiLock) {
            if (!SafetyCenterFlags.getAllowStatsdLogging()) {
                Log.i(TAG, "Skipping pulling and writing atoms due to logging being disabled");
                return StatsManager.PULL_SKIP;
            }
            Log.d(TAG, "Pulling and writing atoms…");
            for (int i = 0; i < userProfileGroups.size(); i++) {
                UserProfileGroup userProfileGroup = userProfileGroups.get(i);
                List<SafetySourcesGroup> loggableGroups =
                        mSafetyCenterConfigReader.getLoggableSafetySourcesGroups();
                statsEvents.add(
                        createOverallSafetyStateAtomLocked(userProfileGroup, loggableGroups));
                // The SAFETY_SOURCE_STATE_COLLECTED atoms are written instead of being pulled,
                // as they do not support pull. We still want to collect them at the same time as
                // the above pulled atom, which is why they're written here.
                writeSafetySourceStateCollectedAtomsLocked(userProfileGroup, loggableGroups);
            }
        }
        return StatsManager.PULL_SUCCESS;
    }

    @GuardedBy("mApiLock")
    private StatsEvent createOverallSafetyStateAtomLocked(
            UserProfileGroup userProfileGroup, List<SafetySourcesGroup> loggableGroups) {
        SafetyCenterData loggableData =
                mDataFactory.assembleSafetyCenterData("android", userProfileGroup, loggableGroups);
        long openIssuesCount = loggableData.getIssues().size();
        long dismissedIssuesCount = getDismissedIssuesCountLocked(loggableData, userProfileGroup);

        return SafetyCenterStatsdLogger.createSafetyStateEvent(
                loggableData.getStatus().getSeverityLevel(), openIssuesCount, dismissedIssuesCount);
    }

    @GuardedBy("mApiLock")
    private long getDismissedIssuesCountLocked(
            SafetyCenterData loggableData, UserProfileGroup userProfileGroup) {
        if (SdkLevel.isAtLeastU()) {
            return loggableData.getDismissedIssues().size();
        }
        long openIssuesCount = loggableData.getIssues().size();
        return mDataManager.countLoggableIssuesFor(userProfileGroup) - openIssuesCount;
    }

    @GuardedBy("mApiLock")
    private void writeSafetySourceStateCollectedAtomsLocked(
            UserProfileGroup userProfileGroup, List<SafetySourcesGroup> loggableGroups) {
        for (int i = 0; i < loggableGroups.size(); i++) {
            List<SafetySource> loggableSources = loggableGroups.get(i).getSafetySources();

            for (int j = 0; j < loggableSources.size(); j++) {
                SafetySource loggableSource = loggableSources.get(j);

                if (!SafetySources.isExternal(loggableSource)) {
                    continue;
                }

                for (int profileTypeIdx = 0;
                        profileTypeIdx < ProfileType.ALL_PROFILE_TYPES.length;
                        ++profileTypeIdx) {
                    @ProfileType int profileType = ProfileType.ALL_PROFILE_TYPES[profileTypeIdx];
                    if (!SafetySources.supportsProfileType(loggableSource, profileType)) {
                        continue;
                    }

                    int[] profileIds = userProfileGroup.getProfilesOfType(profileType);
                    for (int profileIdx = 0; profileIdx < profileIds.length; profileIdx++) {
                        writeSafetySourceStateCollectedAtomLocked(
                                loggableSource, profileIds[profileIdx], profileType);
                    }
                }
            }
        }
    }

    @GuardedBy("mApiLock")
    private void writeSafetySourceStateCollectedAtomLocked(
            SafetySource safetySource, @UserIdInt int userId, @ProfileType int profileType) {
        SafetySourceKey sourceKey = SafetySourceKey.of(safetySource.getId(), userId);
        mDataManager.logSafetySourceStateCollectedAutomatic(sourceKey, profileType);
    }
}
