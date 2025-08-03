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

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.content.res.Resources;
import android.view.View;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.R;
import com.android.documentsui.TestConfigStore;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;
import com.google.common.truth.Correspondence;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A unit test for {@link UserItemsCombiner}.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class UserItemsCombinerTest {
    private static final UserId PERSONAL_USER = TestProvidersAccess.USER_ID;
    private static final UserId WORK_USER = TestProvidersAccess.OtherUser.USER_ID;
    private static final UserId PRIVATE_USER = TestProvidersAccess.AnotherUser.USER_ID;

    private static final List<Item> PERSONAL_ITEMS = Lists.newArrayList(
            personalItem("personal 1"),
            personalItem("personal 2"),
            personalItem("personal 3")
    );

    private static final List<Item> WORK_ITEMS = Lists.newArrayList(
            workItem("work 1")
    );

    private static final List<Item> PRIVATE_ITEMS = Lists.newArrayList(
            privateItem("private1")
    );

    private static final Correspondence<Item, Item> ITEM_CORRESPONDENCE =
            Correspondence.from((Item actual, Item expected) -> {
                return Objects.equals(actual.title, expected.title)
                        && Objects.equals(actual.userId, expected.userId);
            }, "has same title and userId as in");

    private final State mState = new State();
    private final Resources mResources =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getResources();
    private final DevicePolicyManager mDpm =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(
                    DevicePolicyManager.class);
    private final List<UserId> mUserIds = new ArrayList<>();
    private final Map<UserId, String> mUserIdToLabelMap = new HashMap<>();
    private final TestConfigStore mTestConfigStore = new TestConfigStore();
    private UserItemsCombiner mCombiner;

    @Before
    public void setUp() {
        mState.canShareAcrossProfile = true;
        mState.supportsCrossProfile = true;
        mUserIds.add(PERSONAL_USER);
        mUserIds.add(WORK_USER);
        mUserIdToLabelMap.put(PERSONAL_USER, "Personal");
        mUserIdToLabelMap.put(WORK_USER, "Work");
        mState.canForwardToProfileIdMap.put(PERSONAL_USER, true);
        mState.canForwardToProfileIdMap.put(WORK_USER, true);
        mState.configStore = mTestConfigStore;

        if (SdkLevel.isAtLeastV()) {
            mUserIds.add(PRIVATE_USER);
            mUserIdToLabelMap.put(PRIVATE_USER, "Private");
            mState.canForwardToProfileIdMap.put(PRIVATE_USER, true);
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
        }
    }

    @Test
    public void testCreatePresentableList_empty() {
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .setRootListForCurrentUser(Collections.emptyList())
                .setRootListForOtherUser(Collections.emptyList());
        assertThat(mCombiner.createPresentableList()).isEmpty();
    }

    @Test
    public void testCreatePresentableListForAllUsers_empty() {
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(Collections.emptyList());
        rootListAllUsers.add(Collections.emptyList());
        if (SdkLevel.isAtLeastV()) {
            rootListAllUsers.add(Collections.emptyList());
        }
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .setRootListForAllUsers(rootListAllUsers);
        assertThat(
                mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap)).isEmpty();
    }

    @Test
    public void testCreatePresentableList_currentIsPersonal_personalItemsOnly() {
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .setRootListForCurrentUser(PERSONAL_ITEMS)
                .setRootListForOtherUser(Collections.emptyList());
        assertThat(mCombiner.createPresentableList())
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(PERSONAL_ITEMS)
                .inOrder();
    }

    @Test
    public void testCreatePresentableList_currentIsWork_personalItemsOnly() {
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .overrideCurrentUserForTest(WORK_USER)
                .setRootListForCurrentUser(Collections.emptyList())
                .setRootListForOtherUser(PERSONAL_ITEMS);
        assertThat(mCombiner.createPresentableList())
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(PERSONAL_ITEMS)
                .inOrder();
    }

    @Test
    public void testCreatePresentableListForAllUsers_currentIsPersonal_personalItemsOnly() {
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(Lists.newArrayList(PERSONAL_ITEMS));
        rootListAllUsers.add(Collections.emptyList());
        if (SdkLevel.isAtLeastV()) {
            rootListAllUsers.add(Collections.emptyList());
        }
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .setRootListForAllUsers(rootListAllUsers);
        assertThat(mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap))
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(PERSONAL_ITEMS)
                .inOrder();
    }

    @Test
    public void testCreatePresentableListForAllUsers_currentIsWork_personalItemsOnly() {
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(Lists.newArrayList(PERSONAL_ITEMS));
        rootListAllUsers.add(Collections.emptyList());
        if (SdkLevel.isAtLeastV()) {
            rootListAllUsers.add(Collections.emptyList());
        }
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .overrideCurrentUserForTest(WORK_USER)
                .setRootListForAllUsers(rootListAllUsers);
        assertThat(mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap))
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(PERSONAL_ITEMS)
                .inOrder();
    }

    @Test
    public void testCreatePresentableListForAllUsers_currentIsPrivate_personalItemsOnly() {
        if (!SdkLevel.isAtLeastV()) return;
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(Lists.newArrayList(PERSONAL_ITEMS));
        rootListAllUsers.add(Collections.emptyList());
        rootListAllUsers.add(Collections.emptyList());
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .overrideCurrentUserForTest(PRIVATE_USER)
                .setRootListForAllUsers(rootListAllUsers);
        assertThat(mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap))
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(PERSONAL_ITEMS)
                .inOrder();
    }

    @Test
    public void testCreatePresentableList_currentIsPersonal_workItemsOnly() {
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .setRootListForCurrentUser(Collections.emptyList())
                .setRootListForOtherUser(WORK_ITEMS);
        assertThat(mCombiner.createPresentableList())
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(WORK_ITEMS)
                .inOrder();
    }

    @Test
    public void testCreatePresentableList_currentIsWork_workItemsOnly() {
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .overrideCurrentUserForTest(WORK_USER)
                .setRootListForCurrentUser(WORK_ITEMS)
                .setRootListForOtherUser(Collections.emptyList());
        assertThat(mCombiner.createPresentableList())
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(WORK_ITEMS)
                .inOrder();
    }

    @Test
    public void testCreatePresentableListForAllUsers_currentIsPersonal_workItemsOnly() {
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(Collections.emptyList());
        rootListAllUsers.add(Lists.newArrayList(WORK_ITEMS));
        if (SdkLevel.isAtLeastV()) {
            rootListAllUsers.add(Collections.emptyList());
        }
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .overrideCurrentUserForTest(PERSONAL_USER)
                .setRootListForAllUsers(rootListAllUsers);
        assertThat(mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap))
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(WORK_ITEMS)
                .inOrder();
    }

    @Test
    public void testCreatePresentableListForAllUsers_currentIsWork_workItemsOnly() {
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(Collections.emptyList());
        rootListAllUsers.add(Lists.newArrayList(WORK_ITEMS));
        if (SdkLevel.isAtLeastV()) {
            rootListAllUsers.add(Collections.emptyList());
        }
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .overrideCurrentUserForTest(WORK_USER)
                .setRootListForAllUsers(rootListAllUsers);
        assertThat(mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap))
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(WORK_ITEMS)
                .inOrder();
    }

    @Test
    public void testCreatePresentableListForAllUsers_currentIsPrivate_workItemsOnly() {
        if (!SdkLevel.isAtLeastV()) return;
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(Collections.emptyList());
        rootListAllUsers.add(Lists.newArrayList(WORK_ITEMS));
        rootListAllUsers.add(Collections.emptyList());
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .overrideCurrentUserForTest(PRIVATE_USER)
                .setRootListForAllUsers(rootListAllUsers);
        assertThat(mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap))
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(WORK_ITEMS)
                .inOrder();
    }

    @Test
    public void testCreatePresentableList_currentIsPersonal_personalAndWorkItems() {
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .setRootListForCurrentUser(PERSONAL_ITEMS)
                .setRootListForOtherUser(WORK_ITEMS);

        List<Item> expected = Lists.newArrayList();
        expected.add(new HeaderItem(mResources.getString(R.string.personal_tab)));
        expected.addAll(PERSONAL_ITEMS);
        expected.add(new HeaderItem(mResources.getString(R.string.work_tab)));
        expected.addAll(WORK_ITEMS);

        assertThat(mCombiner.createPresentableList())
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(expected)
                .inOrder();
    }

    @Test
    public void testCreatePresentableList_currentIsWork_personalAndWorkItems() {
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .overrideCurrentUserForTest(WORK_USER)
                .setRootListForCurrentUser(WORK_ITEMS)
                .setRootListForOtherUser(PERSONAL_ITEMS);

        List<Item> expected = Lists.newArrayList();
        expected.add(new HeaderItem(mResources.getString(R.string.personal_tab)));
        expected.addAll(PERSONAL_ITEMS);
        expected.add(new HeaderItem(mResources.getString(R.string.work_tab)));
        expected.addAll(WORK_ITEMS);

        assertThat(mCombiner.createPresentableList())
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(expected)
                .inOrder();
    }

    @Test
    public void testCreatePresentableListForAllUsers_currentIsPersonal_allUsersItems() {
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(PERSONAL_ITEMS);
        rootListAllUsers.add(Lists.newArrayList(WORK_ITEMS));
        if (SdkLevel.isAtLeastV()) {
            rootListAllUsers.add(PRIVATE_ITEMS);
        }
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .setRootListForAllUsers(rootListAllUsers);

        List<Item> expected = Lists.newArrayList();
        expected.add(new HeaderItem("Personal"));
        expected.addAll(PERSONAL_ITEMS);
        expected.add(new HeaderItem("Work"));
        expected.addAll(WORK_ITEMS);
        if (SdkLevel.isAtLeastV()) {
            expected.add(new HeaderItem("Private"));
            expected.addAll(PRIVATE_ITEMS);
        }

        assertThat(mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap))
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(expected)
                .inOrder();
    }

    @Test
    public void testCreatePresentableListForAllUsers_currentIsWork_allUsersItems() {
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(PERSONAL_ITEMS);
        rootListAllUsers.add(Lists.newArrayList(WORK_ITEMS));
        if (SdkLevel.isAtLeastV()) {
            rootListAllUsers.add(PRIVATE_ITEMS);
        }
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .overrideCurrentUserForTest(WORK_USER)
                .setRootListForAllUsers(rootListAllUsers);

        List<Item> expected = Lists.newArrayList();
        expected.add(new HeaderItem("Personal"));
        expected.addAll(PERSONAL_ITEMS);
        expected.add(new HeaderItem("Work"));
        expected.addAll(WORK_ITEMS);
        if (SdkLevel.isAtLeastV()) {
            expected.add(new HeaderItem("Private"));
            expected.addAll(PRIVATE_ITEMS);
        }

        assertThat(mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap))
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(expected)
                .inOrder();
    }

    @Test
    public void testCreatePresentableListForAllUsers_currentIsPrivate_allUsersItems() {
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(PERSONAL_ITEMS);
        rootListAllUsers.add(Lists.newArrayList(WORK_ITEMS));
        if (SdkLevel.isAtLeastV()) {
            rootListAllUsers.add(PRIVATE_ITEMS);
        }
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .overrideCurrentUserForTest(PRIVATE_USER)
                .setRootListForAllUsers(rootListAllUsers);

        List<Item> expected = Lists.newArrayList();
        expected.add(new HeaderItem("Personal"));
        expected.addAll(PERSONAL_ITEMS);
        expected.add(new HeaderItem("Work"));
        expected.addAll(WORK_ITEMS);
        if (SdkLevel.isAtLeastV()) {
            expected.add(new HeaderItem("Private"));
            expected.addAll(PRIVATE_ITEMS);
        }

        assertThat(mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap))
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(expected)
                .inOrder();
    }

    @Test
    public void testCreatePresentableList_currentIsPersonal_personalAndWorkItems_cannotShare() {
        mState.canShareAcrossProfile = false;
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .setRootListForCurrentUser(PERSONAL_ITEMS)
                .setRootListForOtherUser(WORK_ITEMS);

        assertThat(mCombiner.createPresentableList())
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(PERSONAL_ITEMS)
                .inOrder();
    }

    @Test
    public void testCreatePresentableList_currentIsWork_personalItemsOnly_cannotShare() {
        mState.canShareAcrossProfile = false;
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .overrideCurrentUserForTest(WORK_USER)
                .setRootListForCurrentUser(Collections.emptyList())
                .setRootListForOtherUser(PERSONAL_ITEMS);

        assertThat(mCombiner.createPresentableList()).isEmpty();
    }

    @Test
    public void testCreatePresentableListForAllUsers_currentIsPersonal_cannotShareToWork() {
        if (!SdkLevel.isAtLeastV()) return;
        mState.canForwardToProfileIdMap.put(WORK_USER, false);
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(PERSONAL_ITEMS);
        rootListAllUsers.add(Lists.newArrayList(WORK_ITEMS));
        if (SdkLevel.isAtLeastV()) {
            rootListAllUsers.add(PRIVATE_ITEMS);
        }
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .setRootListForAllUsers(rootListAllUsers);

        List<Item> expected = Lists.newArrayList();
        expected.add(new HeaderItem("Personal"));
        expected.addAll(PERSONAL_ITEMS);
        if (SdkLevel.isAtLeastV()) {
            expected.add(new HeaderItem("Private"));
            expected.addAll(PRIVATE_ITEMS);
        }

        assertThat(mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap))
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(expected)
                .inOrder();
    }

    @Test
    public void testCreatePresentableListForAllUsers_currentIsWork_AllItems_cannotSharePersonal() {
        if (!SdkLevel.isAtLeastV()) return;
        mState.canForwardToProfileIdMap.put(PERSONAL_USER, false);
        // In the current implementation of cross profile content sharing strategy work profile will
        // be able to share to all the child profiles of the parent/personal profile only if it is
        // able to share with parent/personal profile
        mState.canForwardToProfileIdMap.put(PRIVATE_USER, false);
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        rootListAllUsers.add(PERSONAL_ITEMS);
        rootListAllUsers.add(Lists.newArrayList(WORK_ITEMS));
        if (SdkLevel.isAtLeastV()) {
            rootListAllUsers.add(PRIVATE_ITEMS);
        }
        mCombiner = new UserItemsCombiner(mResources, mDpm, mState)
                .setRootListForAllUsers(rootListAllUsers);

        assertThat(mCombiner.createPresentableListForAllUsers(mUserIds, mUserIdToLabelMap))
                .comparingElementsUsing(ITEM_CORRESPONDENCE)
                .containsExactlyElementsIn(WORK_ITEMS)
                .inOrder();
    }

    private static TestItem personalItem(String title) {
        return new TestItem(title, PERSONAL_USER);
    }

    private static TestItem workItem(String title) {
        return new TestItem(title, WORK_USER);
    }

    private static TestItem privateItem(String title) {
        return new TestItem(title, PRIVATE_USER);
    }

    private static class TestItem extends Item {

        TestItem(String title, UserId userId) {
            super(/* layoutId= */ 0, title, /* stringId= */ "", userId);
        }

        @Override
        void bindView(View convertView) {
        }

        @Override
        boolean isRoot() {
            return false;
        }

        @Override
        void open() {
        }

        @Override
        public String toString() {
            return title + "(" + userId + ")";
        }
    }
}
