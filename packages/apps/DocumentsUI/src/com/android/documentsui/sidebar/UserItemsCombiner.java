/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.documentsui.sidebar;

import static androidx.core.util.Preconditions.checkArgument;
import static androidx.core.util.Preconditions.checkNotNull;

import static com.android.documentsui.DevicePolicyResources.Strings.PERSONAL_TAB;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_TAB;

import android.app.admin.DevicePolicyManager;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.android.documentsui.R;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.modules.utils.build.SdkLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts user-specific lists of items into a single merged list appropriate for displaying in the
 * UI, including the relevant headers.
 */
class UserItemsCombiner {

    private UserId mCurrentUser;
    private final Resources mResources;
    private final DevicePolicyManager mDpm;
    private final State mState;
    private List<Item> mRootList;
    private List<Item> mRootListOtherUser;
    private List<List<Item>> mRootListAllUsers;

    UserItemsCombiner(Resources resources, DevicePolicyManager dpm, State state) {
        mCurrentUser = UserId.CURRENT_USER;
        mResources = checkNotNull(resources);
        mDpm = dpm;
        mState = checkNotNull(state);
    }

    @VisibleForTesting
    UserItemsCombiner overrideCurrentUserForTest(UserId userId) {
        mCurrentUser = checkNotNull(userId);
        return this;
    }

    UserItemsCombiner setRootListForCurrentUser(List<Item> rootList) {
        mRootList = checkNotNull(rootList);
        return this;
    }

    UserItemsCombiner setRootListForOtherUser(List<Item> rootList) {
        mRootListOtherUser = checkNotNull(rootList);
        return this;
    }

    UserItemsCombiner setRootListForAllUsers(List<List<Item>> listOfRootLists) {
        mRootListAllUsers = checkNotNull(listOfRootLists);
        return this;
    }

    /**
     * Returns a combined lists from the provided lists. {@link HeaderItem}s indicating profile
     * will be added if the list of current user and the other user are not empty.
     */
    public List<Item> createPresentableList() {
        checkArgument(mRootList != null, "RootListForCurrentUser is not set");
        checkArgument(mRootListOtherUser != null, "setRootListForOtherUser is not set");

        final List<Item> result = new ArrayList<>();
        if (mState.supportsCrossProfile() && mState.canShareAcrossProfile) {
            if (!mRootList.isEmpty() && !mRootListOtherUser.isEmpty()) {
                // Identify personal and work root list.
                final List<Item> personalRootList;
                final List<Item> workRootList;
                if (mCurrentUser.isSystem()) {
                    personalRootList = mRootList;
                    workRootList = mRootListOtherUser;
                } else {
                    personalRootList = mRootListOtherUser;
                    workRootList = mRootList;
                }
                result.add(new HeaderItem(getEnterpriseString(
                        PERSONAL_TAB, R.string.personal_tab)));
                result.addAll(personalRootList);
                result.add(new HeaderItem(getEnterpriseString(WORK_TAB, R.string.work_tab)));
                result.addAll(workRootList);
            } else {
                result.addAll(mRootList);
                result.addAll(mRootListOtherUser);
            }
        } else {
            result.addAll(mRootList);
        }
        return result;
    }

    public List<Item> createPresentableListForAllUsers(List<UserId> userIds,
            Map<UserId, String> userIdToLabelMap) {

        checkArgument(mRootListAllUsers != null, "RootListForAllUsers is not set");

        final List<Item> result = new ArrayList<>();
        if (mState.supportsCrossProfile()) {
            // headerItemList will hold headers for userIds that are accessible, and
            final List<Item> headerItemList = new ArrayList<>();
            int accessibleProfilesCount = 0;
            for (int i = 0; i < userIds.size(); ++i) {
                // The received user id list contains all users present on the device,
                // the headerItemList will contain header item or null at the same index as
                // the user id in the received list
                if (mState.canInteractWith(userIds.get(i))
                        && !mRootListAllUsers.get(i).isEmpty()) {
                    accessibleProfilesCount += 1;
                    headerItemList.add(new HeaderItem(userIdToLabelMap.get(userIds.get(i))));
                } else {
                    headerItemList.add(null);
                }
            }
            // Do not add header item if:
            // 1. only the current profile is accessible
            // 2. only one profile has non-empty root item list
            if (accessibleProfilesCount == 1) {
                for (int i = 0; i < userIds.size(); ++i) {
                    if (headerItemList.get(i) == null) continue;
                    result.addAll(mRootListAllUsers.get(i));
                    break;
                }
            } else {
                for (int i = 0; i < userIds.size(); ++i) {
                    // Since the header item and the corresponding accessible user id share the same
                    // index we add the user id along with its non-null header to the result.
                    if (headerItemList.get(i) == null) continue;
                    result.add(headerItemList.get(i));
                    result.addAll(mRootListAllUsers.get(i));
                }
            }
        } else {
            result.addAll(mRootListAllUsers.get(userIds.indexOf(mCurrentUser)));
        }
        return result;
    }

    private String getEnterpriseString(String updatableStringId, int defaultStringId) {
        if (SdkLevel.isAtLeastT()) {
            return getUpdatableEnterpriseString(updatableStringId, defaultStringId);
        } else {
            return mResources.getString(defaultStringId);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private String getUpdatableEnterpriseString(String updatableStringId, int defaultStringId) {
        return mDpm.getResources().getString(
                updatableStringId, () -> mResources.getString(defaultStringId));
    }
}
