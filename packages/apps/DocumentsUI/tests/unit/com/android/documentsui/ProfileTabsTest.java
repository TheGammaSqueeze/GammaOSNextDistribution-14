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

package com.android.documentsui;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.Uri;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.InstrumentationRegistry;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.modules.utils.build.SdkLevel;

import com.google.android.material.tabs.TabLayout;
import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.List;
import java.util.Map;

@RunWith(Parameterized.class)
public class ProfileTabsTest {

    private final UserId mSystemUser = UserId.of(UserHandle.SYSTEM);
    private final UserId mManagedUser = UserId.of(100);
    private final UserId mPrivateUser = UserId.of(101);

    private ProfileTabs mProfileTabs;

    private Context mContext;
    private View mTabLayoutContainer;
    private TabLayout mTabLayout;
    private TestEnvironment mTestEnv;
    private State mState;
    private TestUserIdManager mTestUserIdManager;
    private TestUserManagerState mTestUserManagerState;
    private TestCommonAddons mTestCommonAddons;
    private boolean mIsListenerInvoked;
    private TestConfigStore mTestConfigStore;

    @Parameter(0)
    public boolean isPrivateSpaceEnabled;

    /**
     * Parametrize values for {@code isPrivateSpaceEnabled} to run all the tests twice once with
     * private space flag enabled and once with it disabled.
     */
    @Parameters(name = "privateSpaceEnabled={0}")
    public static Iterable<?> data() {
        return Lists.newArrayList(true, false);
    }

    @Before
    public void setUp() {
        TestEnv.create();
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mContext.setTheme(R.style.DocumentsTheme);
        mContext.getTheme().applyStyle(R.style.DocumentsDefaultTheme, false);
        LayoutInflater layoutInflater = LayoutInflater.from(mContext);
        mState = new State();
        mState.supportsCrossProfile = true;
        mState.stack.changeRoot(TestProvidersAccess.DOWNLOADS);
        mState.stack.push(TestEnv.FOLDER_0);
        View view = layoutInflater.inflate(R.layout.directory_header, null);

        mTabLayout = view.findViewById(R.id.tabs);
        mTabLayoutContainer = view.findViewById(R.id.tabs_container);
        mTestEnv = new TestEnvironment();
        mTestEnv.isSearchExpanded = false;

        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled;
        if (isPrivateSpaceEnabled) {
            mTestUserManagerState = new TestUserManagerState();
        } else {
            mTestUserIdManager = new TestUserIdManager();
        }

        mTestCommonAddons = new TestCommonAddons();
        mTestCommonAddons.mCurrentRoot = TestProvidersAccess.DOWNLOADS;
        mTestConfigStore = new TestConfigStore();
    }

    @Test
    public void testUpdateView_singleUser_shouldHide() {
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser);

        assertThat(mTabLayoutContainer.getVisibility()).isEqualTo(View.GONE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(0);
    }

    @Test
    public void testUpdateView_twoUsers_shouldShow() {
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser, mManagedUser);

        assertThat(mTabLayoutContainer.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(2);

        TabLayout.Tab tab1 = mTabLayout.getTabAt(0);
        assertThat(tab1.getTag()).isEqualTo(mSystemUser);
        assertThat(tab1.getText()).isEqualTo(mContext.getString(R.string.personal_tab));

        TabLayout.Tab tab2 = mTabLayout.getTabAt(1);
        assertThat(tab2.getTag()).isEqualTo(mManagedUser);
        assertThat(tab2.getText()).isEqualTo(mContext.getString(R.string.work_tab));
    }

    @Test
    public void testUpdateView_multiUsers_shouldShow() {
        assumeTrue(SdkLevel.isAtLeastV() && isPrivateSpaceEnabled);
        initializeWithUsers(true, mSystemUser, mManagedUser, mPrivateUser);

        assertThat(mTabLayoutContainer.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(3);

        final Map<UserId, String> userIdToLabelMap = mTestUserManagerState.getUserIdToLabelMap();
        for (int i = 0; i < 3; ++i) {
            TabLayout.Tab tab = mTabLayout.getTabAt(i);
            assertThat(tab).isNotNull();
            UserId userId = (UserId) tab.getTag();
            assertThat(tab.getText()).isEqualTo(userIdToLabelMap.get(userId));
        }
    }

    @Test
    public void testUpdateView_twoUsers_doesNotSupportCrossProfile_shouldHide() {
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser, mManagedUser);

        mState.supportsCrossProfile = false;
        mProfileTabs.updateView();

        assertThat(mTabLayoutContainer.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testUpdateView_twoUsers_subFolder_shouldHide() {
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser, mManagedUser);

        // Push 1 more folder. Now the stack has size of 2.
        mState.stack.push(TestEnv.FOLDER_1);

        mProfileTabs.updateView();
        assertThat(mTabLayoutContainer.getVisibility()).isEqualTo(View.GONE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(2);
    }

    @Test
    public void testUpdateView_twoUsers_recents_subFolder_shouldHide() {
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser, mManagedUser);

        mState.stack.changeRoot(TestProvidersAccess.RECENTS);
        // This(stack of size 2 in Recents) may not happen in real world.
        mState.stack.push((TestEnv.FOLDER_0));

        mProfileTabs.updateView();
        assertThat(mTabLayoutContainer.getVisibility()).isEqualTo(View.GONE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(2);
    }

    @Test
    public void testUpdateView_twoUsers_thirdParty_shouldHide() {
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser, mManagedUser);

        mState.stack.changeRoot(TestProvidersAccess.PICKLES);
        mState.stack.push((TestEnv.FOLDER_0));

        mProfileTabs.updateView();
        assertThat(mTabLayoutContainer.getVisibility()).isEqualTo(View.GONE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(2);
    }

    @Test
    public void testUpdateView_twoUsers_isSearching_shouldHide() {
        mTestEnv.isSearchExpanded = true;
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser, mManagedUser);

        assertThat(mTabLayoutContainer.getVisibility()).isEqualTo(View.GONE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(2);
    }

    @Test
    public void testUpdateView_getSelectedUser_afterUsersChanged() {
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser, mManagedUser);
        mProfileTabs.updateView();
        mTabLayout.selectTab(mTabLayout.getTabAt(1));
        assertThat(mTabLayoutContainer.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mManagedUser);

        if (isPrivateSpaceEnabled) {
            mTestUserManagerState.userIds = Lists.newArrayList(mSystemUser);
        } else {
            mTestUserIdManager.userIds = Lists.newArrayList(mSystemUser);
        }
        mProfileTabs.updateView();
        assertThat(mTabLayoutContainer.getVisibility()).isEqualTo(View.GONE);
        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mSystemUser);
    }

    @Test
    public void testUpdateView_afterCurrentRootChanged_shouldChangeSelectedUser() {
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser, mManagedUser);
        mProfileTabs.updateView();

        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mSystemUser);

        RootInfo newRoot = RootInfo.copyRootInfo(mTestCommonAddons.mCurrentRoot);
        newRoot.userId = mManagedUser;
        mTestCommonAddons.mCurrentRoot = newRoot;
        mProfileTabs.updateView();

        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mManagedUser);
        // updating view should not trigger listener callback.
        assertThat(mIsListenerInvoked).isFalse();
    }

    @Test
    public void testUpdateView_afterCurrentRootChangedMultiUser_shouldChangeSelectedUser() {
        assumeTrue(SdkLevel.isAtLeastV() && isPrivateSpaceEnabled);
        initializeWithUsers(true, mSystemUser, mManagedUser, mPrivateUser);
        mProfileTabs.updateView();

        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mSystemUser);

        for (UserId userId : Lists.newArrayList(mManagedUser, mPrivateUser)) {
            RootInfo newRoot = RootInfo.copyRootInfo(mTestCommonAddons.mCurrentRoot);
            newRoot.userId = userId;
            mTestCommonAddons.mCurrentRoot = newRoot;
            mProfileTabs.updateView();

            assertThat(mProfileTabs.getSelectedUser()).isEqualTo(userId);
            // updating view should not trigger listener callback.
            assertThat(mIsListenerInvoked).isFalse();
        }
    }

    @Test
    public void testUpdateView_afterSelectedUserBecomesUnavailable_shouldSwitchToCurrentUser() {
        // here current user refers to UserId.CURRENT_USER, which in this case will be mSystemUser
        assumeTrue(SdkLevel.isAtLeastV() && isPrivateSpaceEnabled);
        initializeWithUsers(true, mSystemUser, mManagedUser, mPrivateUser);

        mTabLayout.selectTab(mTabLayout.getTabAt(2));
        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mPrivateUser);

        mTestUserManagerState.userIds.remove(mPrivateUser);
        mTestUserManagerState.userIdToLabelMap.remove(mPrivateUser);
        mProfileTabs.updateView();

        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mSystemUser);
    }

    @Test
    public void testGetSelectedUser_twoUsers() {
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser, mManagedUser);

        mTabLayout.selectTab(mTabLayout.getTabAt(0));
        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mSystemUser);

        mTabLayout.selectTab(mTabLayout.getTabAt(1));
        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mManagedUser);
        assertThat(mIsListenerInvoked).isTrue();
    }

    @Test
    public void testGetSelectedUser_multiUsers() {
        assumeTrue(SdkLevel.isAtLeastV() && isPrivateSpaceEnabled);
        initializeWithUsers(true, mSystemUser, mManagedUser, mPrivateUser);

        List<UserId> expectedProfiles = Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser);

        for (int i = 0; i < 3; ++i) {
            mTabLayout.selectTab(mTabLayout.getTabAt(i));
            assertThat(mProfileTabs.getSelectedUser()).isEqualTo(expectedProfiles.get(i));
            if (i == 0) continue;
            assertThat(mIsListenerInvoked).isTrue();
        }
    }

    @Test
    public void testReselectedUser_doesNotInvokeListener() {
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser, mManagedUser);

        assertThat(mTabLayout.getSelectedTabPosition()).isAtLeast(0);
        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mSystemUser);

        mTabLayout.selectTab(mTabLayout.getTabAt(0));
        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mSystemUser);
        assertThat(mIsListenerInvoked).isFalse();
    }

    @Test
    public void testGetSelectedUser_singleUsers() {
        initializeWithUsers(isPrivateSpaceEnabled, mSystemUser);

        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(mSystemUser);
    }

    private void initializeWithUsers(boolean isPrivateSpaceEnabled, UserId... userIds) {
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            mTestUserManagerState.userIds = Lists.newArrayList(userIds);
            for (UserId userId : userIds) {
                if (userId.isSystem()) {
                    mTestUserManagerState.userIdToLabelMap.put(userId, "Personal");
                } else if (userId.getIdentifier() == 100) {
                    mTestUserManagerState.userIdToLabelMap.put(userId, "Work");
                } else {
                    mTestUserManagerState.userIdToLabelMap.put(userId, "Private");
                }
            }
            mProfileTabs = new ProfileTabs(mTabLayoutContainer, mState, mTestUserManagerState,
                    mTestEnv, mTestCommonAddons, mTestConfigStore);
        } else {
            mTestConfigStore.disablePrivateSpaceInPhotoPicker();
            mTestUserIdManager.userIds = Lists.newArrayList(userIds);
            for (UserId userId : userIds) {
                if (userId.isSystem()) {
                    mTestUserIdManager.systemUser = userId;
                } else {
                    mTestUserIdManager.managedUser = userId;
                }
            }
            mProfileTabs = new ProfileTabs(mTabLayoutContainer, mState, mTestUserIdManager,
                    mTestEnv, mTestCommonAddons, mTestConfigStore);
        }
        mProfileTabs.updateView();
        mProfileTabs.setListener(userId -> mIsListenerInvoked = true);
    }

    /**
     * A test implementation of {@link NavigationViewManager.Environment}.
     */
    private static class TestEnvironment implements NavigationViewManager.Environment {

        public boolean isSearchExpanded = false;

        @Override
        public RootInfo getCurrentRoot() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public String getDrawerTitle() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void refreshCurrentRootAndDirectory(int animation) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean isSearchExpanded() {
            return isSearchExpanded;
        }
    }

    private static class TestCommonAddons implements AbstractActionHandler.CommonAddons {

        private RootInfo mCurrentRoot;

        @Override
        public void restoreRootAndDirectory() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void refreshCurrentRootAndDirectory(int anim) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void onRootPicked(RootInfo root) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void onDocumentsPicked(List<DocumentInfo> docs) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void onDocumentPicked(DocumentInfo doc) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public RootInfo getCurrentRoot() {
            return mCurrentRoot;
        }

        @Override
        public DocumentInfo getCurrentDirectory() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public UserId getSelectedUser() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean isInRecents() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void setRootsDrawerOpen(boolean open) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void setRootsDrawerLocked(boolean locked) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void updateNavigator() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void notifyDirectoryNavigated(Uri docUri) {
            throw new UnsupportedOperationException("not implemented");
        }
    }
}

