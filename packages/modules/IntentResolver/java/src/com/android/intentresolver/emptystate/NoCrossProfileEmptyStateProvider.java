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

package com.android.intentresolver.emptystate;

import android.app.admin.DevicePolicyEventLogger;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.intentresolver.ResolverListAdapter;

/**
 * Empty state provider that does not allow cross profile sharing, it will return a blocker
 * in case if the profile of the current tab is not the same as the profile of the calling app.
 */
public class NoCrossProfileEmptyStateProvider implements EmptyStateProvider {

    private final UserHandle mPersonalProfileUserHandle;
    private final EmptyState mNoWorkToPersonalEmptyState;
    private final EmptyState mNoPersonalToWorkEmptyState;
    private final CrossProfileIntentsChecker mCrossProfileIntentsChecker;
    private final UserHandle mTabOwnerUserHandleForLaunch;

    public NoCrossProfileEmptyStateProvider(UserHandle personalUserHandle,
            EmptyState noWorkToPersonalEmptyState,
            EmptyState noPersonalToWorkEmptyState,
            CrossProfileIntentsChecker crossProfileIntentsChecker,
            UserHandle tabOwnerUserHandleForLaunch) {
        mPersonalProfileUserHandle = personalUserHandle;
        mNoWorkToPersonalEmptyState = noWorkToPersonalEmptyState;
        mNoPersonalToWorkEmptyState = noPersonalToWorkEmptyState;
        mCrossProfileIntentsChecker = crossProfileIntentsChecker;
        mTabOwnerUserHandleForLaunch = tabOwnerUserHandleForLaunch;
    }

    @Nullable
    @Override
    public EmptyState getEmptyState(ResolverListAdapter resolverListAdapter) {
        boolean shouldShowBlocker =
                !mTabOwnerUserHandleForLaunch.equals(resolverListAdapter.getUserHandle())
                        && !mCrossProfileIntentsChecker
                        .hasCrossProfileIntents(resolverListAdapter.getIntents(),
                                mTabOwnerUserHandleForLaunch.getIdentifier(),
                                resolverListAdapter.getUserHandle().getIdentifier());

        if (!shouldShowBlocker) {
            return null;
        }

        if (resolverListAdapter.getUserHandle().equals(mPersonalProfileUserHandle)) {
            return mNoWorkToPersonalEmptyState;
        } else {
            return mNoPersonalToWorkEmptyState;
        }
    }


    /**
     * Empty state that gets strings from the device policy manager and tracks events into
     * event logger of the device policy events.
     */
    public static class DevicePolicyBlockerEmptyState implements EmptyState {

        @NonNull
        private final Context mContext;
        private final String mDevicePolicyStringTitleId;
        @StringRes
        private final int mDefaultTitleResource;
        private final String mDevicePolicyStringSubtitleId;
        @StringRes
        private final int mDefaultSubtitleResource;
        private final int mEventId;
        @NonNull
        private final String mEventCategory;

        public DevicePolicyBlockerEmptyState(
                @NonNull Context context,
                String devicePolicyStringTitleId,
                @StringRes int defaultTitleResource,
                String devicePolicyStringSubtitleId,
                @StringRes int defaultSubtitleResource,
                int devicePolicyEventId,
                @NonNull String devicePolicyEventCategory) {
            mContext = context;
            mDevicePolicyStringTitleId = devicePolicyStringTitleId;
            mDefaultTitleResource = defaultTitleResource;
            mDevicePolicyStringSubtitleId = devicePolicyStringSubtitleId;
            mDefaultSubtitleResource = defaultSubtitleResource;
            mEventId = devicePolicyEventId;
            mEventCategory = devicePolicyEventCategory;
        }

        @Nullable
        @Override
        public String getTitle() {
            return mContext.getSystemService(DevicePolicyManager.class).getResources().getString(
                    mDevicePolicyStringTitleId,
                    () -> mContext.getString(mDefaultTitleResource));
        }

        @Nullable
        @Override
        public String getSubtitle() {
            return mContext.getSystemService(DevicePolicyManager.class).getResources().getString(
                    mDevicePolicyStringSubtitleId,
                    () -> mContext.getString(mDefaultSubtitleResource));
        }

        @Override
        public void onEmptyStateShown() {
            DevicePolicyEventLogger.createEvent(mEventId)
                    .setStrings(mEventCategory)
                    .write();
        }

        @Override
        public boolean shouldSkipDataRebuild() {
            return true;
        }
    }
}
