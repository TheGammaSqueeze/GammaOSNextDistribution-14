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

package com.android.safetycenter;

import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import android.annotation.UserIdInt;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;

import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

import java.util.Objects;

/**
 * Contains various information about a {@link SafetySourceIssue}.
 *
 * @hide
 */
public final class SafetySourceIssueInfo {

    private final SafetySourceIssue mSafetySourceIssue;
    private final SafetySource mSafetySource;
    private final SafetySourcesGroup mSafetySourcesGroup;
    private final SafetyCenterIssueKey mSafetyCenterIssueKey;

    /** Creates a new {@link SafetySourceIssueInfo} instance. */
    public SafetySourceIssueInfo(
            SafetySourceIssue safetySourceIssue,
            SafetySource safetySource,
            SafetySourcesGroup safetySourcesGroup,
            @UserIdInt int userId) {
        mSafetySourceIssue = safetySourceIssue;
        mSafetySource = safetySource;
        mSafetySourcesGroup = safetySourcesGroup;
        mSafetyCenterIssueKey =
                SafetyCenterIssueKey.newBuilder()
                        .setSafetySourceId(safetySource.getId())
                        .setSafetySourceIssueId(safetySourceIssue.getId())
                        .setUserId(userId)
                        .build();
    }

    /** Returns the {@link SafetyCenterIssueKey} related to this {@link SafetySourceIssue}. */
    public SafetyCenterIssueKey getSafetyCenterIssueKey() {
        return mSafetyCenterIssueKey;
    }

    /** Returns the {@link SafetySourceIssue}. */
    public SafetySourceIssue getSafetySourceIssue() {
        return mSafetySourceIssue;
    }

    /** Returns the {@link SafetySource} related to this {@link SafetySourceIssue}. */
    public SafetySource getSafetySource() {
        return mSafetySource;
    }

    /** Returns the {@link SafetySourcesGroup} related to this {@link SafetySourceIssue}. */
    public SafetySourcesGroup getSafetySourcesGroup() {
        return mSafetySourcesGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetySourceIssueInfo)) return false;
        SafetySourceIssueInfo that = (SafetySourceIssueInfo) o;
        return mSafetySourceIssue.equals(that.mSafetySourceIssue)
                && mSafetySource.equals(that.mSafetySource)
                && mSafetySourcesGroup.equals(that.mSafetySourcesGroup)
                && mSafetyCenterIssueKey.equals(that.mSafetyCenterIssueKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mSafetySourceIssue, mSafetySource, mSafetySourcesGroup, mSafetyCenterIssueKey);
    }

    @Override
    public String toString() {
        return "SafetySourceIssueInfo{"
                + "mSafetySourceIssue="
                + mSafetySourceIssue
                + ", mSafetySource="
                + mSafetySource
                + ", mSafetySourcesGroup="
                + mSafetySourcesGroup
                + ", mSafetyCenterIssueKey="
                + toUserFriendlyString(mSafetyCenterIssueKey)
                + '}';
    }
}
