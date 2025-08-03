/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.profiles;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.UserInfo;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.testutils.ShadowUserHelper;
import com.android.car.settings.testutils.ShadowUserIconProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowUserIconProvider.class, ShadowUserHelper.class})
public class ProfilesPreferenceProviderTest {

    private static final String TEST_CURRENT_USER_NAME = "Current User";
    private static final String TEST_OTHER_USER_1_NAME = "User 1";
    private static final String TEST_OTHER_USER_2_NAME = "User 2";
    private static final String TEST_GUEST_USER_1_NAME = "Guest 1";
    private static final String TEST_GUEST_USER_2_NAME = "Guest 2";

    private static final UserInfo TEST_CURRENT_USER = new UserInfo(/* id= */ 14,
            TEST_CURRENT_USER_NAME, /* flags= */ 0);
    private static final UserInfo TEST_OTHER_USER_1 = new UserInfo(/* id= */ 10,
            TEST_OTHER_USER_1_NAME, /* flags= */ 0);
    private static final UserInfo TEST_OTHER_USER_2 = new UserInfo(/* id= */ 11,
            TEST_OTHER_USER_2_NAME, /* flags= */ 0);
    private static final UserInfo TEST_GUEST_USER_1 = new UserInfo(/* id= */ 12,
            TEST_GUEST_USER_1_NAME, /* flags= */ UserInfo.FLAG_GUEST);
    private static final UserInfo TEST_GUEST_USER_2 = new UserInfo(/* id= */ 13,
            TEST_GUEST_USER_2_NAME, /* flags= */ UserInfo.FLAG_GUEST);


    private Context mContext;
    @Mock
    private ProfilesPreferenceProvider.ProfileClickListener mProfileClickListener;
    @Mock
    private ProfileHelper mProfileHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowUserHelper.setInstance(mProfileHelper);
        mContext = RuntimeEnvironment.application;

        List<UserInfo> users = Arrays.asList(TEST_OTHER_USER_1, TEST_GUEST_USER_1,
                TEST_GUEST_USER_2,
                TEST_OTHER_USER_2);

        when(mProfileHelper.getCurrentProcessUserInfo()).thenReturn(TEST_CURRENT_USER);
        when(mProfileHelper.isCurrentProcessUser(TEST_CURRENT_USER)).thenReturn(true);
        when(mProfileHelper.getAllSwitchableProfiles()).thenReturn(users);
        when(mProfileHelper.getAllLivingProfiles(any())).thenReturn(
                Arrays.asList(TEST_OTHER_USER_1, TEST_OTHER_USER_2));
    }

    @After
    public void tearDown() {
        ShadowUserHelper.reset();
    }

    @Test
    public void testCreateUserList_firstUserIsCurrentUser() {
        ProfilesPreferenceProvider provider = createProvider();

        Preference first = provider.createProfileList().get(0);
        assertThat(first.getTitle()).isEqualTo(
                mContext.getString(R.string.current_user_name, TEST_CURRENT_USER_NAME));
    }

    @Test
    public void testCreateUserList_repeatedGuestUserNotShown() {
        ProfilesPreferenceProvider provider = createProvider();

        List<Preference> userList = provider.createProfileList();
        assertThat(userList.size()).isEqualTo(4); // 3 real users + guest item.
        assertThat(userList.get(0).getTitle()).isEqualTo(
                mContext.getString(R.string.current_user_name, TEST_CURRENT_USER_NAME));
        assertThat(userList.get(1).getTitle()).isEqualTo(TEST_OTHER_USER_1_NAME);
        assertThat(userList.get(2).getTitle()).isEqualTo(TEST_OTHER_USER_2_NAME);
    }

    @Test
    public void testCreateUserList_guestShownAsSeparateLastElement() {
        ProfilesPreferenceProvider provider = createProvider();

        List<Preference> userList = provider.createProfileList();
        assertThat(userList.get(userList.size() - 1).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
    }

    @Test
    public void testCreateUserList_currentUserNotShown() {
        ProfilesPreferenceProvider provider = createProvider();
        provider.setIncludeCurrentProfile(false);

        List<Preference> userList = provider.createProfileList();
        assertThat(userList.size()).isEqualTo(3); // 3 real users + guest item.
        assertThat(userList.get(0).getTitle()).isEqualTo(TEST_OTHER_USER_1_NAME);
        assertThat(userList.get(1).getTitle()).isEqualTo(TEST_OTHER_USER_2_NAME);
        assertThat(userList.get(2).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
    }

    @Test
    public void testCreateUserList_guestNotShown() {
        ProfilesPreferenceProvider provider = createProvider();
        provider.setIncludeGuest(false);

        List<Preference> userList = provider.createProfileList();
        assertThat(userList.size()).isEqualTo(3); // 3 real users.
        assertThat(userList.get(0).getTitle()).isEqualTo(
                mContext.getString(R.string.current_user_name, TEST_CURRENT_USER_NAME));
        assertThat(userList.get(1).getTitle()).isEqualTo(TEST_OTHER_USER_1_NAME);
        assertThat(userList.get(2).getTitle()).isEqualTo(TEST_OTHER_USER_2_NAME);
    }

    @Test
    public void testPerformClick_currentUser_invokesUserClickListener() {
        ProfilesPreferenceProvider provider = createProvider();

        List<Preference> userList = provider.createProfileList();
        userList.get(0).performClick();
        verify(mProfileClickListener).onProfileClicked(TEST_CURRENT_USER);
    }

    @Test
    public void testPerformClick_otherUser_invokesUserClickListener() {
        ProfilesPreferenceProvider provider = createProvider();

        List<Preference> userList = provider.createProfileList();
        userList.get(1).performClick();
        verify(mProfileClickListener).onProfileClicked(TEST_OTHER_USER_1);
    }

    @Test
    public void testPerformClick_guestUser_doesntInvokeUserClickListener() {
        ProfilesPreferenceProvider provider = createProvider();

        List<Preference> userList = provider.createProfileList();
        userList.get(userList.size() - 1).performClick();
        verify(mProfileClickListener, never()).onProfileClicked(any(UserInfo.class));
    }

    private ProfilesPreferenceProvider createProvider() {
        return new ProfilesPreferenceProvider(mContext, mProfileClickListener);
    }
}
