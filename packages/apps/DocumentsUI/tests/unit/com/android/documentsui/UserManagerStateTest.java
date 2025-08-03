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

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.SOLID_COLORED;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static com.android.documentsui.DevicePolicyResources.Strings.PERSONAL_TAB;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_TAB;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResourcesManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserProperties;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.base.UserId;
import com.android.documentsui.testing.UserManagers;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SmallTest
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class UserManagerStateTest {

    private static final String PERSONAL = "Personal";
    private static final String WORK = "Work";
    private static final String PRIVATE = "Private";

    private final UserHandle mSystemUser = UserHandle.SYSTEM;
    private final UserHandle mManagedUser = UserHandle.of(100);
    private final UserHandle mPrivateUser = UserHandle.of(101);
    private final UserHandle mOtherUser = UserHandle.of(102);
    private final UserHandle mNormalUser = UserHandle.of(103);

    private final ResolveInfo mMockInfo1 = mock(ResolveInfo.class);
    private final ResolveInfo mMockInfo2 = mock(ResolveInfo.class);
    private final ResolveInfo mMockInfo3 = mock(ResolveInfo.class);

    private final Context mMockContext = mock(Context.class);
    private final Intent mMockIntent = mock(Intent.class);
    private final UserManager mMockUserManager = UserManagers.create();
    private final PackageManager mMockPackageManager = mock(PackageManager.class);
    private final DevicePolicyManager mDevicePolicyManager = mock(DevicePolicyManager.class);
    private UserManagerState mUserManagerState;

    @Before
    public void setup() throws Exception {
        when(mMockContext.getApplicationContext()).thenReturn(mMockContext);

        when(mMockUserManager.isManagedProfile(mManagedUser.getIdentifier())).thenReturn(true);
        when(mMockUserManager.isManagedProfile(mSystemUser.getIdentifier())).thenReturn(false);
        when(mMockUserManager.isManagedProfile(mPrivateUser.getIdentifier())).thenReturn(false);
        when(mMockUserManager.isManagedProfile(mOtherUser.getIdentifier())).thenReturn(false);

        if (SdkLevel.isAtLeastV()) {
            UserProperties systemUserProperties = new UserProperties.Builder()
                    .setShowInSharingSurfaces(UserProperties.SHOW_IN_SHARING_SURFACES_SEPARATE)
                    .setCrossProfileContentSharingStrategy(
                            UserProperties.CROSS_PROFILE_CONTENT_SHARING_NO_DELEGATION)
                    .build();
            UserProperties managedUserProperties = new UserProperties.Builder()
                    .setShowInSharingSurfaces(UserProperties.SHOW_IN_SHARING_SURFACES_SEPARATE)
                    .setCrossProfileContentSharingStrategy(
                            UserProperties.CROSS_PROFILE_CONTENT_SHARING_NO_DELEGATION)
                    .setShowInQuietMode(UserProperties.SHOW_IN_QUIET_MODE_PAUSED)
                    .build();
            UserProperties privateUserProperties = new UserProperties.Builder()
                    .setShowInSharingSurfaces(UserProperties.SHOW_IN_SHARING_SURFACES_SEPARATE)
                    .setCrossProfileContentSharingStrategy(
                            UserProperties.CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT)
                    .setShowInQuietMode(UserProperties.SHOW_IN_QUIET_MODE_HIDDEN)
                    .build();
            UserProperties otherUserProperties = new UserProperties.Builder()
                    .setShowInSharingSurfaces(UserProperties.SHOW_IN_SHARING_SURFACES_WITH_PARENT)
                    .setCrossProfileContentSharingStrategy(
                            UserProperties.CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT)
                    .build();
            UserProperties normalUserProperties = new UserProperties.Builder()
                    .setShowInSharingSurfaces(UserProperties.SHOW_IN_SHARING_SURFACES_NO)
                    .setCrossProfileContentSharingStrategy(
                            UserProperties.CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT)
                    .build();
            when(mMockUserManager.getUserProperties(mSystemUser)).thenReturn(systemUserProperties);
            when(mMockUserManager.getUserProperties(mManagedUser)).thenReturn(
                    managedUserProperties);
            when(mMockUserManager.getUserProperties(mPrivateUser)).thenReturn(
                    privateUserProperties);
            when(mMockUserManager.getUserProperties(mOtherUser)).thenReturn(otherUserProperties);
            when(mMockUserManager.getUserProperties(mNormalUser)).thenReturn(normalUserProperties);
        }

        when(mMockUserManager.getProfileParent(mSystemUser)).thenReturn(null);
        when(mMockUserManager.getProfileParent(mManagedUser)).thenReturn(mSystemUser);
        when(mMockUserManager.getProfileParent(mPrivateUser)).thenReturn(mSystemUser);
        when(mMockUserManager.getProfileParent(mOtherUser)).thenReturn(mSystemUser);
        when(mMockUserManager.getProfileParent(mNormalUser)).thenReturn(null);

        if (SdkLevel.isAtLeastR()) {
            when(mMockInfo1.isCrossProfileIntentForwarderActivity()).thenReturn(true);
            when(mMockInfo2.isCrossProfileIntentForwarderActivity()).thenReturn(false);
            when(mMockInfo3.isCrossProfileIntentForwarderActivity()).thenReturn(false);
        }

        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockContext.getSystemServiceName(UserManager.class)).thenReturn("mMockUserManager");
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mMockContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(mDevicePolicyManager);
        when(mMockContext.getResources()).thenReturn(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getResources());
    }

    @Test
    public void testGetUserIds_onlySystemUser_returnsSystemUser() {
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds()).containsExactly(UserId.of(mSystemUser));
    }

    @Test
    public void testGetUserIds_allProfilesCurrentUserSystem_allShowInSharingSurfacesSeparate() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser,
                        mNormalUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser),
                        UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_allProfilesCurrentUserManaged_allShowInSharingSurfacesSeparate() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mManagedUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser,
                        mNormalUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser),
                        UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_allProfilesCurrentUserPrivate_allShowInSharingSurfacesSeparate() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser),
                        UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_systemAndManagedUserCurrentUserSystem_returnsBoth() {
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser));
    }

    @Test
    public void testGetUserIds_systemAndManagedUserCurrentUserManaged_returnsBoth() {
        UserId currentUser = UserId.of(mManagedUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser));
    }

    @Test
    public void testGetUserIds_systemAndPrivateUserCurrentUserSystem_returnsBoth() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mPrivateUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_systemAndPrivateUserCurrentUserPrivate_returnsBoth() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mPrivateUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_systemAndOtherUserCurrentUserOtherPreV_returnsCurrentUser() {
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mOtherUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mOtherUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(currentUser);
    }

    @Test
    public void testGetUserIds_systemAndOtherUserCurrentUserOtherPostV_returnsSystemUser() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mOtherUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mOtherUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser));
    }

    @Test
    public void testGetUserIds_normalAndOtherUserCurrentUserNormal_returnsCurrentUser() {
        // since both users do not have show in sharing surfaces separate, returns current user
        UserId currentUser = UserId.of(mNormalUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mOtherUser, mNormalUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mNormalUser));
    }

    @Test
    public void testGetUserIds_systemAndManagedUserCurrentUserSystem_returnsBothInOrder() {
        // Returns the both if there are system and managed users.
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser)).inOrder();
    }

    @Test
    public void testGetUserIds_systemAndManagedUserCurrentUserManaged_returnsBothInOrder() {
        // Returns the both if there are system and managed users.
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mManagedUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser)).inOrder();
    }

    @Test
    public void testGetUserIds_managedAndSystemUserCurrentUserSystem_returnsBothInOrder() {
        // Returns the both if there are system and managed users, regardless of input list order.
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mManagedUser, mSystemUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser)).inOrder();
    }

    @Test
    public void testGetUserIds_otherAndManagedUserCurrentUserOtherPreV_returnsCurrentUser() {
        // When there is no system user, returns the current user.
        // This is a case theoretically can happen but we don't expect. So we return the current
        // user only.
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mOtherUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mOtherUser, mManagedUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds()).containsExactly(currentUser);
    }

    @Test
    public void testGetUserIds_otherAndManagedUserCurrentUserOtherPostV_returnsManagedUser() {
        // Only the users with show in sharing surfaces separate are eligible to be returned
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mOtherUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mOtherUser, mManagedUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds()).containsExactly(UserId.of(mManagedUser));
    }

    @Test
    public void testGetUserIds_otherAndManagedUserCurrentUserManaged_returnsCurrentUser() {
        // When there is no system user, returns the current user.
        // This is a case theoretically can happen, but we don't expect. So we return the current
        // user only.
        UserId currentUser = UserId.of(mManagedUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mOtherUser, mManagedUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds()).containsExactly(currentUser);
    }

    @Test
    public void testGetUserIds_unsupportedDeviceCurrent_returnsCurrentUser() {
        // This test only tests for Android R or later. This test case always passes before R.
        if (VersionUtils.isAtLeastR()) {
            // When permission is denied, only returns the current user.
            when(mMockContext.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS))
                    .thenReturn(PackageManager.PERMISSION_DENIED);
            UserId currentUser = UserId.of(mSystemUser);
            when(mMockUserManager.getUserProfiles()).thenReturn(
                    Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser));
            mUserManagerState = UserManagerState.create(mMockContext);
            assertWithMessage("Unsupported device should have returned only the current user")
                    .that(mUserManagerState.getUserIds()).containsExactly(currentUser);
        }
    }

    @Test
    public void testGetUserIds_returnCachedList() {
        // Returns all three if there are system, managed and private users.
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser));
        assertWithMessage("getUserIds does not return cached instance")
                .that(mUserManagerState.getUserIds())
                .isSameInstanceAs(mUserManagerState.getUserIds());
    }

    @Test
    public void testGetCanForwardToProfileIdMap_systemUserCanForwardToAll() {
        UserId currentUser = UserId.of(mSystemUser);
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfo1, mMockInfo2);
        if (SdkLevel.isAtLeastV()) {
            initializeUserManagerState(currentUser,
                    Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
            when(mMockPackageManager.queryIntentActivitiesAsUser(mMockIntent,
                    PackageManager.MATCH_DEFAULT_ONLY, mSystemUser)).thenReturn(
                    mMockResolveInfoList);
        } else {
            initializeUserManagerState(currentUser,
                    Lists.newArrayList(mSystemUser, mManagedUser));
            when(mMockPackageManager.queryIntentActivities(mMockIntent,
                    PackageManager.MATCH_DEFAULT_ONLY)).thenReturn(mMockResolveInfoList);
        }

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);
        if (SdkLevel.isAtLeastV()) {
            expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);
        }

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_systemUserCanForwardToManaged() {
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfo1, mMockInfo2);
        if (SdkLevel.isAtLeastV()) {
            when(mMockPackageManager.queryIntentActivitiesAsUser(mMockIntent,
                    PackageManager.MATCH_DEFAULT_ONLY, mSystemUser)).thenReturn(
                    mMockResolveInfoList);
        } else {
            when(mMockPackageManager.queryIntentActivities(mMockIntent,
                    PackageManager.MATCH_DEFAULT_ONLY)).thenReturn(mMockResolveInfoList);
        }

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_systemUserCanAlwaysForwardToPrivate() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mPrivateUser));

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_systemUserCanNotForwardToManagedUser() {
        UserId currentUser = UserId.of(mSystemUser);
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfo2, mMockInfo3);
        if (SdkLevel.isAtLeastV()) {
            initializeUserManagerState(currentUser,
                    Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
            when(mMockPackageManager.queryIntentActivitiesAsUser(mMockIntent,
                    PackageManager.MATCH_DEFAULT_ONLY, mSystemUser)).thenReturn(
                    mMockResolveInfoList);
        } else {
            initializeUserManagerState(currentUser,
                    Lists.newArrayList(mSystemUser, mManagedUser));
            when(mMockPackageManager.queryIntentActivities(mMockIntent,
                    PackageManager.MATCH_DEFAULT_ONLY)).thenReturn(mMockResolveInfoList);
        }

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), false);
        if (SdkLevel.isAtLeastV()) {
            expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);
        }

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_managedCanForwardToAll() {
        UserId currentUser = UserId.of(mManagedUser);
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfo1, mMockInfo2);
        if (SdkLevel.isAtLeastV()) {
            initializeUserManagerState(currentUser,
                    Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
            when(mMockPackageManager.queryIntentActivitiesAsUser(mMockIntent,
                    PackageManager.MATCH_DEFAULT_ONLY, mManagedUser)).thenReturn(
                    mMockResolveInfoList);
        } else {
            initializeUserManagerState(currentUser,
                    Lists.newArrayList(mSystemUser, mManagedUser));
            when(mMockPackageManager.queryIntentActivities(mMockIntent,
                    PackageManager.MATCH_DEFAULT_ONLY)).thenReturn(mMockResolveInfoList);
        }

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);
        if (SdkLevel.isAtLeastV()) {
            expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);
        }

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_managedCanNotForwardToAll() {
        UserId currentUser = UserId.of(mManagedUser);
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfo2, mMockInfo3);

        if (SdkLevel.isAtLeastV()) {
            initializeUserManagerState(currentUser,
                    Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
            when(mMockPackageManager.queryIntentActivitiesAsUser(mMockIntent,
                    PackageManager.MATCH_DEFAULT_ONLY, mSystemUser)).thenReturn(
                    mMockResolveInfoList);
        } else {
            initializeUserManagerState(currentUser,
                    Lists.newArrayList(mSystemUser, mManagedUser));
            when(mMockPackageManager.queryIntentActivities(mMockIntent,
                    PackageManager.MATCH_DEFAULT_ONLY)).thenReturn(mMockResolveInfoList);
        }

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), false);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);
        if (SdkLevel.isAtLeastV()) {
            expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), false);
        }

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_privateCanForwardToAll() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfo1, mMockInfo2);
        when(mMockPackageManager.queryIntentActivitiesAsUser(mMockIntent,
                PackageManager.MATCH_DEFAULT_ONLY, mSystemUser)).thenReturn(mMockResolveInfoList);

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_privateCanNotForwardToManagedUser() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfo2, mMockInfo3);
        when(mMockPackageManager.queryIntentActivitiesAsUser(mMockIntent,
                PackageManager.MATCH_DEFAULT_ONLY, mSystemUser)).thenReturn(mMockResolveInfoList);

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), false);
        expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_privateCanAlwaysForwardToSystemUser() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mPrivateUser));

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testOnProfileStatusChange_anyIntentActionForManagedProfile() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));

        // UserManagerState#mUserId and UserManagerState#mCanForwardToProfileIdMap will empty
        // by default if the getters of these member variables have not been called
        List<UserId> userIdsBeforeIntent = new ArrayList<>(mUserManagerState.getUserIds());
        Map<UserId, Boolean> canForwardToProfileIdMapBeforeIntent = new HashMap<>(
                mUserManagerState.getCanForwardToProfileIdMap(mMockIntent));

        String action = "any_intent";
        mUserManagerState.onProfileActionStatusChange(action, UserId.of(mManagedUser));

        assertWithMessage("Unexpected changes to user id list on receiving intent: " + action)
                .that(mUserManagerState.getUserIds()).isEqualTo(userIdsBeforeIntent);
        assertWithMessage(
                "Unexpected changes to canForwardToProfileIdMap on receiving intent: " + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent)).isEqualTo(
                        canForwardToProfileIdMapBeforeIntent);
    }

    @Test
    public void testOnProfileStatusChange_actionProfileUnavailableForPrivateProfile() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        UserId managedUser = UserId.of(mManagedUser);
        UserId privateUser = UserId.of(mPrivateUser);
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfo1, mMockInfo2);
        when(mMockPackageManager.queryIntentActivitiesAsUser(mMockIntent,
                PackageManager.MATCH_DEFAULT_ONLY, mSystemUser)).thenReturn(
                mMockResolveInfoList);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));

        // UserManagerState#mUserId and UserManagerState#mCanForwardToProfileIdMap will empty
        // by default if the getters of these member variables have not been called
        List<UserId> userIdsBeforeIntent = new ArrayList<>(mUserManagerState.getUserIds());
        Map<UserId, Boolean> canForwardToProfileIdMapBeforeIntent = new HashMap<>(
                mUserManagerState.getCanForwardToProfileIdMap(mMockIntent));

        List<UserId> expectedUserIdsAfterIntent = Lists.newArrayList(currentUser, managedUser);

        String action = Intent.ACTION_PROFILE_UNAVAILABLE;
        mUserManagerState.onProfileActionStatusChange(action, privateUser);

        assertWithMessage(
                "UserIds list should not be same before and after receiving intent: " + action)
                .that(mUserManagerState.getUserIds()).isNotEqualTo(userIdsBeforeIntent);
        assertWithMessage("Unexpected changes to user id list on receiving intent: " + action)
                .that(mUserManagerState.getUserIds()).isEqualTo(expectedUserIdsAfterIntent);
        assertWithMessage("CanForwardToLabelMap should be same before and after receiving intent: "
                + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent)).isEqualTo(
                        canForwardToProfileIdMapBeforeIntent);
    }

    @Test
    public void testOnProfileStatusChange_actionProfileAvailable_profileInitialised() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        UserId managedUser = UserId.of(mManagedUser);
        UserId privateUser = UserId.of(mPrivateUser);
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfo1, mMockInfo2);
        when(mMockPackageManager.queryIntentActivitiesAsUser(mMockIntent,
                PackageManager.MATCH_DEFAULT_ONLY, mSystemUser)).thenReturn(
                mMockResolveInfoList);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));

        // initialising the userIds list and canForwardToProfileIdMap
        mUserManagerState.getUserIds();
        mUserManagerState.getCanForwardToProfileIdMap(mMockIntent);

        // Making the private profile unavailable after it has been initialised
        mUserManagerState.onProfileActionStatusChange(Intent.ACTION_PROFILE_UNAVAILABLE,
                privateUser);

        List<UserId> userIdsBeforeIntent = new ArrayList<>(mUserManagerState.getUserIds());
        Map<UserId, Boolean> canForwardToProfileIdMapBeforeIntent = new HashMap<>(
                mUserManagerState.getCanForwardToProfileIdMap(mMockIntent));

        List<UserId> expectedUserIdsAfterIntent = Lists.newArrayList(currentUser, managedUser,
                privateUser);

        String action = Intent.ACTION_PROFILE_AVAILABLE;
        mUserManagerState.onProfileActionStatusChange(action, privateUser);

        assertWithMessage(
                "UserIds list should not be same before and after receiving intent: " + action)
                .that(mUserManagerState.getUserIds()).isNotEqualTo(userIdsBeforeIntent);
        assertWithMessage("Unexpected changes to user id list on receiving intent: " + action)
                .that(mUserManagerState.getUserIds()).isEqualTo(expectedUserIdsAfterIntent);
        assertWithMessage("CanForwardToLabelMap should be same before and after receiving intent: "
                + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent)).isEqualTo(
                        canForwardToProfileIdMapBeforeIntent);
    }

    @Test
    public void testOnProfileStatusChange_actionProfileAvailable_profileNotInitialised() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        UserId managedUser = UserId.of(mManagedUser);
        UserId privateUser = UserId.of(mPrivateUser);
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfo1, mMockInfo2);
        when(mMockPackageManager.queryIntentActivitiesAsUser(mMockIntent,
                PackageManager.MATCH_DEFAULT_ONLY, mSystemUser)).thenReturn(
                mMockResolveInfoList);

        // Private user will not be initialised if it is in quiet mode
        when(mMockUserManager.isQuietModeEnabled(mPrivateUser)).thenReturn(true);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));

        // UserManagerState#mUserId and UserManagerState#mCanForwardToProfileIdMap will be empty
        // by default if the getters of these member variables have not been called
        List<UserId> userIdsBeforeIntent = new ArrayList<>(mUserManagerState.getUserIds());
        Map<UserId, Boolean> canForwardToProfileIdMapBeforeIntent = new HashMap<>(
                mUserManagerState.getCanForwardToProfileIdMap(mMockIntent));

        List<UserId> expectedUserIdsAfterIntent = Lists.newArrayList(currentUser, managedUser,
                privateUser);
        Map<UserId, Boolean> expectedCanForwardToProfileIdMapAfterIntent = new HashMap<>();
        expectedCanForwardToProfileIdMapAfterIntent.put(currentUser, true);
        expectedCanForwardToProfileIdMapAfterIntent.put(managedUser, true);
        expectedCanForwardToProfileIdMapAfterIntent.put(privateUser, true);

        String action = Intent.ACTION_PROFILE_AVAILABLE;
        mUserManagerState.onProfileActionStatusChange(action, privateUser);

        assertWithMessage(
                "UserIds list should not be same before and after receiving intent: " + action)
                .that(mUserManagerState.getUserIds()).isNotEqualTo(userIdsBeforeIntent);
        assertWithMessage("Unexpected changes to user id list on receiving intent: " + action)
                .that(mUserManagerState.getUserIds()).isEqualTo(expectedUserIdsAfterIntent);
        assertWithMessage(
                "CanForwardToLabelMap should not be same before and after receiving intent: "
                        + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent)).isNotEqualTo(
                        canForwardToProfileIdMapBeforeIntent);
        assertWithMessage(
                "Unexpected changes to canForwardToProfileIdMap on receiving intent: " + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent)).isEqualTo(
                        expectedCanForwardToProfileIdMapAfterIntent);
    }

    @Test
    public void testGetUserIdToLabelMap_systemUserAndManagedUser_PreV() {
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser));
        if (SdkLevel.isAtLeastT()) {
            DevicePolicyResourcesManager devicePolicyResourcesManager = mock(
                    DevicePolicyResourcesManager.class);
            when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);
            when(devicePolicyResourcesManager.getString(eq(PERSONAL_TAB), any())).thenReturn(
                    PERSONAL);
            when(devicePolicyResourcesManager.getString(eq(WORK_TAB), any())).thenReturn(WORK);
        }

        Map<UserId, String> userIdToLabelMap = mUserManagerState.getUserIdToLabelMap();

        assertWithMessage("Incorrect label returned for user id " + mSystemUser)
                .that(userIdToLabelMap.get(UserId.of(mSystemUser))).isEqualTo(PERSONAL);
        assertWithMessage("Incorrect label returned for user id " + mManagedUser)
                .that(userIdToLabelMap.get(UserId.of(mManagedUser))).isEqualTo(WORK);
    }

    @Test
    public void testGetUserIdToLabelMap_systemUserManagedUserPrivateUser_PostV() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
        if (SdkLevel.isAtLeastT()) {
            DevicePolicyResourcesManager devicePolicyResourcesManager = mock(
                    DevicePolicyResourcesManager.class);
            when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);
            when(devicePolicyResourcesManager.getString(eq(PERSONAL_TAB), any())).thenReturn(
                    PERSONAL);
        }
        UserManager managedUserManager = getUserManagerForManagedUser();
        UserManager privateUserManager = getUserManagerForPrivateUser();
        when(managedUserManager.getProfileLabel()).thenReturn(WORK);
        when(privateUserManager.getProfileLabel()).thenReturn(PRIVATE);

        Map<UserId, String> userIdToLabelMap = mUserManagerState.getUserIdToLabelMap();

        assertWithMessage("Incorrect label returned for user id " + mSystemUser)
                .that(userIdToLabelMap.get(UserId.of(mSystemUser))).isEqualTo(PERSONAL);
        assertWithMessage("Incorrect label returned for user id " + mManagedUser)
                .that(userIdToLabelMap.get(UserId.of(mManagedUser))).isEqualTo(WORK);
        assertWithMessage("Incorrect label returned for user id " + mPrivateUser)
                .that(userIdToLabelMap.get(UserId.of(mPrivateUser))).isEqualTo(PRIVATE);
    }

    @Test
    public void testGetUserIdToBadgeMap_systemUserManagedUser_PreV() {
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser));
        Drawable workBadge = mMockContext.getDrawable(R.drawable.ic_briefcase);
        if (SdkLevel.isAtLeastT()) {
            DevicePolicyResourcesManager devicePolicyResourcesManager = mock(
                    DevicePolicyResourcesManager.class);
            when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);
            when(devicePolicyResourcesManager.getDrawable(eq(WORK_PROFILE_ICON), eq(SOLID_COLORED),
                    any())).thenReturn(workBadge);
        }

        Map<UserId, Drawable> userIdToBadgeMap = mUserManagerState.getUserIdToBadgeMap();

        assertWithMessage("There should be no badge present for personal user")
                .that(userIdToBadgeMap.containsKey(UserId.of(mSystemUser))).isFalse();
        assertWithMessage("Incorrect badge returned for user id " + mManagedUser)
                .that(userIdToBadgeMap.get(UserId.of(mManagedUser))).isEqualTo(workBadge);
    }

    @Test
    public void testGetUserIdToBadgeMap_systemUserManagedUserPrivateUser_PostV() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
        Drawable workBadge = mock(Drawable.class);
        Drawable privateBadge = mock(Drawable.class);
        UserManager managedUserManager = getUserManagerForManagedUser();
        UserManager privateUserManager = getUserManagerForPrivateUser();
        when(managedUserManager.getUserBadge()).thenReturn(workBadge);
        when(privateUserManager.getUserBadge()).thenReturn(privateBadge);

        Map<UserId, Drawable> userIdToBadgeMap = mUserManagerState.getUserIdToBadgeMap();

        assertWithMessage("There should be no badge present for personal user")
                .that(userIdToBadgeMap.get(UserId.of(mSystemUser))).isNull();
        assertWithMessage("Incorrect badge returned for user id " + mManagedUser)
                .that(userIdToBadgeMap.get(UserId.of(mManagedUser))).isEqualTo(workBadge);
        assertWithMessage("Incorrect badge returned for user id " + mPrivateUser)
                .that(userIdToBadgeMap.get(UserId.of(mPrivateUser))).isEqualTo(privateBadge);
    }

    private void initializeUserManagerState(UserId current, List<UserHandle> usersOnDevice) {
        when(mMockUserManager.getUserProfiles()).thenReturn(usersOnDevice);
        TestConfigStore testConfigStore = new TestConfigStore();
        testConfigStore.enablePrivateSpaceInPhotoPicker();
        mUserManagerState = new UserManagerState.RuntimeUserManagerState(mMockContext, current,
                true, testConfigStore);
    }

    private UserManager getUserManagerForManagedUser() {
        Context managedUserContext = mock(Context.class);
        when(mMockContext.createContextAsUser(mManagedUser, 0)).thenReturn(managedUserContext);
        UserManager managedUserManager = mock(UserManager.class);
        when(managedUserContext.getSystemServiceName(UserManager.class))
                .thenReturn("managedUserManager");
        when(managedUserContext.getSystemService(UserManager.class)).thenReturn(managedUserManager);
        return managedUserManager;
    }

    private UserManager getUserManagerForPrivateUser() {
        Context privateUserContext = mock(Context.class);
        when(mMockContext.createContextAsUser(mPrivateUser, 0)).thenReturn(privateUserContext);
        UserManager privateUserManager = mock(UserManager.class);
        when(privateUserContext.getSystemServiceName(UserManager.class))
                .thenReturn("privateUserManager");
        when(privateUserContext.getSystemService(UserManager.class)).thenReturn(privateUserManager);
        return privateUserManager;
    }
}
