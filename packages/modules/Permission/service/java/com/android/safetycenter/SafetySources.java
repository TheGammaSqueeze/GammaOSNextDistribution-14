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

import android.safetycenter.SafetySourceData;
import android.safetycenter.config.SafetySource;
import android.util.Log;

import com.android.safetycenter.UserProfileGroup.ProfileType;

/**
 * A helper class to facilitate working with {@link SafetySource} objects.
 *
 * @hide
 */
public final class SafetySources {

    private static final String TAG = "SafetySources";

    /**
     * Returns whether a {@link SafetySource} is external, i.e. if {@link SafetySourceData} can be
     * provided for it.
     */
    public static boolean isExternal(SafetySource safetySource) {
        int safetySourceType = safetySource.getType();
        switch (safetySourceType) {
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC:
                return false;
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC:
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY:
                return true;
        }
        Log.w(TAG, "Unexpected safety source type: " + safetySourceType);
        return false;
    }

    /** Returns whether a {@link SafetySource} supports managed profiles. */
    public static boolean supportsManagedProfiles(SafetySource safetySource) {
        return supportsAllProfiles(safetySource);
    }

    /**
     * Returns whether a {@link SafetySource} supports the profile of the given type
     * {@code profileType}.
     */
    public static boolean supportsProfileType(
            SafetySource safetySource, @ProfileType int profileType) {
        if (UserProfileGroup.PROFILE_TYPE_PRIMARY == profileType) {
            return true;
        }
        return supportsAllProfiles(safetySource);
    }

    /** Returns whether a {@link SafetySource} supports all profiles. */
    private static boolean supportsAllProfiles(SafetySource safetySource) {
        int safetySourceProfile = safetySource.getProfile();
        switch (safetySourceProfile) {
            case SafetySource.PROFILE_PRIMARY:
            case SafetySource.PROFILE_NONE:
                return false;
            case SafetySource.PROFILE_ALL:
                return true;
        }
        Log.w(TAG, "Unexpected safety source profile: " + safetySourceProfile);
        return false;
    }

    /** Returns whether a {@link SafetySource} default entry should be hidden in the UI. */
    static boolean isDefaultEntryHidden(SafetySource safetySource) {
        int safetySourceType = safetySource.getType();
        switch (safetySourceType) {
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC:
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY:
                return false;
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC:
                return safetySource.getInitialDisplayState()
                        == SafetySource.INITIAL_DISPLAY_STATE_HIDDEN;
        }
        Log.w(TAG, "Unexpected safety source type: " + safetySourceType);
        return false;
    }

    /** Returns whether a {@link SafetySource} default entry should be disabled in the UI. */
    static boolean isDefaultEntryDisabled(SafetySource safetySource) {
        int safetySourceType = safetySource.getType();
        switch (safetySourceType) {
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC:
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY:
                return false;
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC:
                return safetySource.getInitialDisplayState()
                        == SafetySource.INITIAL_DISPLAY_STATE_DISABLED;
        }
        Log.w(TAG, "Unexpected safety source type: " + safetySourceType);
        return false;
    }

    /**
     * Returns whether a {@link SafetySource} can be logged, without requiring a check of source
     * type first.
     */
    public static boolean isLoggable(SafetySource safetySource) {
        // Only external sources can have logging allowed values. Non-external sources cannot have
        // their loggability configured. Unfortunately isLoggingAllowed throws if called on a
        // non-external source.
        if (isExternal(safetySource)) {
            return safetySource.isLoggingAllowed();
        } else {
            return true;
        }
    }

    private SafetySources() {}
}
