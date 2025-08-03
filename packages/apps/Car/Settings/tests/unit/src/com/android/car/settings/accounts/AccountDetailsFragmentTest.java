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

package com.android.car.settings.accounts;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.pm.UserInfo;

import androidx.fragment.app.FragmentManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.ui.toolbar.ToolbarController;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class AccountDetailsFragmentTest {
    private final Account mAccount = new Account("Name", "com.acct");
    private final UserInfo mUserInfo = new UserInfo(/* id= */ 0, /* name= */ "name",
            /* flags= */ 0);
    private final CharSequence mAccountLabel = "Type 1";

    private AccountDetailsFragment mFragment;
    private BaseCarSettingsTestActivity mActivity;
    private FragmentManager mFragmentManager;
    private MockitoSession mSession;

    @Mock
    private AccountManager mMockAccountManager;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        setUpFragment();
        mSession = ExtendedMockito.mockitoSession().mockStatic(AccountManager.class,
                withSettings().lenient()).startMocking();
        when(AccountManager.get(any())).thenReturn(mMockAccountManager);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onActivityCreated_titleShouldBeSet() {
        addAccounts(/* hasAccount= */ true);
        ToolbarController toolbar = mActivity.getToolbar();
        assertThat(toolbar.getTitle().toString()).isEqualTo(mAccountLabel.toString());
    }

    @Test
    public void accountExists_accountStillExists_shouldBeTrue() {
        addAccounts(/* hasAccount= */ true);
        assertThat(mFragment.accountExists()).isTrue();
    }

    @Test
    public void accountExists_accountWasRemoved_shouldBeFalse() {
        addAccounts(/* hasAccount= */ false);
        assertThat(mFragment.accountExists()).isFalse();
    }

    @Test
    public void onAccountsUpdate_accountDoesNotExist_shouldGoBack() throws Throwable {
        addAccounts(/* hasAccount= */ false);
        mActivityTestRule.runOnUiThread(() -> mFragment.onAccountsUpdate(null));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(mActivity.getOnBackPressedFlag()).isTrue();
    }

    private void setUpFragment() throws Throwable {
        String accountDetailsFragmentTag = "account_details_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container,
                            AccountDetailsFragment.newInstance(mAccount, mAccountLabel, mUserInfo),
                            accountDetailsFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (AccountDetailsFragment) mFragmentManager.findFragmentByTag(
                accountDetailsFragmentTag);
    }

    private void addAccounts(boolean hasAccount) {
        Account[] accounts;
        if (hasAccount) {
            accounts = new Account[]{mAccount};
        } else {
            accounts = new Account[0];
        }
        when(mMockAccountManager.getAccountsByTypeAsUser(anyString(), any())).thenReturn(accounts);
    }
}
