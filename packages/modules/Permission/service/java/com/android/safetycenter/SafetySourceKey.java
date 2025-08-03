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

import android.annotation.UserIdInt;
import android.safetycenter.SafetySourceData;

import java.util.Objects;

/**
 * A key to identify a safety source providing {@link SafetySourceData}; based on the {@code
 * sourceId} and {@code userId}.
 *
 * @hide
 */
// TODO(b/219697341): Look into using AutoValue for this data class.

public final class SafetySourceKey {

    private final String mSourceId;
    @UserIdInt private final int mUserId;

    private SafetySourceKey(String sourceId, @UserIdInt int userId) {
        mSourceId = sourceId;
        mUserId = userId;
    }

    /** Creates a {@link SafetySourceKey} for the given {@code sourceId} and {@code userId}. */
    public static SafetySourceKey of(String sourceId, @UserIdInt int userId) {
        return new SafetySourceKey(sourceId, userId);
    }

    /** Returns the source id of this {@link SafetySourceKey}. */
    public String getSourceId() {
        return mSourceId;
    }

    /** Returns the user id of this {@link SafetySourceKey}. */
    @UserIdInt
    public int getUserId() {
        return mUserId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetySourceKey)) return false;
        SafetySourceKey safetySourceKey = (SafetySourceKey) o;
        return mSourceId.equals(safetySourceKey.mSourceId) && mUserId == safetySourceKey.mUserId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSourceId, mUserId);
    }

    @Override
    public String toString() {
        return "SafetySourceKey{" + "mSourceId='" + mSourceId + "', mUserId=" + mUserId + '}';
    }
}
