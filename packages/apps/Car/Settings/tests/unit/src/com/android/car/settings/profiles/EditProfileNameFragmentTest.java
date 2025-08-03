/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.mockito.Mockito.verify;

import android.content.pm.UserInfo;
import android.os.UserManager;
import android.widget.EditText;

import androidx.fragment.app.FragmentManager;
import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.ui.toolbar.MenuItem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class EditProfileNameFragmentTest {

    private EditProfileNameFragment mFragment;
    private BaseCarSettingsTestActivity mActivity;
    private FragmentManager mFragmentManager;

    @Mock
    private UserManager mUserManager;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
    }

    /**
     * Tests that user name of the profile in question is displayed in the TextInputEditTest field.
     */
    @Test
    public void testUserNameDisplayedInDetails() throws Throwable {
        String testUserName = "test_user";
        UserInfo testUser = new UserInfo(/* id= */ 10, testUserName, /* flags= */ 0);
        setUpFragment(testUser);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        EditText userNameEditText = mActivity.findViewById(R.id.profile_name_text_edit);
        assertThat(userNameEditText.getText().toString()).isEqualTo(testUserName);
    }

    /**
     * Tests that clicking OK saves the new name for the user.
     */
    @Test
    @UiThreadTest
    public void testClickingOkSavesNewUserName() throws Throwable {
        int userId = 10;
        UserInfo testUser = new UserInfo(userId, "user_name", /* flags= */ 0);
        setUpFragment(testUser);
        EditText userNameEditText = mActivity.findViewById(R.id.profile_name_text_edit);
        MenuItem okButton = mFragment.getToolbarMenuItems().get(0);

        String newUserName = "new_user_name";
        userNameEditText.requestFocus();
        userNameEditText.setText(newUserName);
        assertThat(okButton.isEnabled()).isTrue();
        okButton.performClick();

        verify(mUserManager).setUserName(userId, newUserName);
    }

    @Test
    @UiThreadTest
    public void testEmptyUsernameCannotBeSaved() throws Throwable {
        UserInfo testUser = new UserInfo(/* id= */ 10, "user_name", /* flags= */ 0);
        setUpFragment(testUser);
        EditText userNameEditText = mActivity.findViewById(R.id.profile_name_text_edit);
        MenuItem okButton = mFragment.getToolbarMenuItems().get(0);

        userNameEditText.requestFocus();
        userNameEditText.setText("");

        assertThat(okButton.isEnabled()).isFalse();
    }

    private void setUpFragment(UserInfo userInfo) throws Throwable {
        String editProfileFragment = "edit_profile_fragment";
        mFragment = EditProfileNameFragment.newInstance(userInfo);
        mFragment.mUserManager = mUserManager;
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, mFragment, editProfileFragment)
                    .commitNow();
        });
    }
}
