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

package com.android.car.settings.system;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit test for {@link FactoryResetAccountsPreferenceController}. */
@RunWith(AndroidJUnit4.class)
public class FactoryResetAccountsPreferenceControllerTest {
    private final int mUserId = UserHandle.myUserId();

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreference;
    private FactoryResetAccountsPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    private Map<Integer, List<AuthenticatorDescription>> mAuthenticatorDescriptionMap;
    private Map<Integer, List<Account>> mAccountMap;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private AccountManager mMockAccountManager;
    @Mock
    private UserManager mMockUserManager;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mAuthenticatorDescriptionMap = new HashMap<>();
        mAccountMap = new HashMap<>();
        mAuthenticatorDescriptionMap.put(mUserId, new ArrayList<>());
        mAccountMap.put(mUserId, new ArrayList<>());
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(AccountManager.class, withSettings().lenient())
                .mockStatic(UserManager.class, withSettings().lenient())
                .startMocking();
        when(AccountManager.get(mContext)).thenReturn(mMockAccountManager);
        updateAccountManagerMocks();
        when(UserManager.get(mContext)).thenCallRealMethod();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreference = new PreferenceCategory(mContext);
        screen.addPreference(mPreference);
        mPreferenceController = new FactoryResetAccountsPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onCreate_addsTitlePreference() {
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreference.getPreference(0).getTitle()).isEqualTo(
                mContext.getString(R.string.factory_reset_accounts));
    }

    @Test
    public void refreshUi_accountsPresent_showsGroup() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        addAccountAndDescription(mUserId, "accountName");

        mPreferenceController.refreshUi();

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void refreshUi_noAccountsPresent_hidesGroup() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mPreferenceController.refreshUi();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void refreshUi_multipleProfiles_showsAllAccounts() {
        int profileId1 = 112;
        int profileId2 = 113;
        List<UserInfo> profiles = new ArrayList<>();
        profiles.add(new UserInfo(profileId1, String.valueOf(profileId1), 0));
        profiles.add(new UserInfo(profileId2, String.valueOf(profileId2), 0));
        String accountName1 = "accountName1";
        addAccountAndDescription(profileId1, accountName1);
        String accountName2 = "accountName2";
        addAccountAndDescription(profileId2, accountName2);

        when(UserManager.get(mContext)).thenReturn(mMockUserManager);
        when(mMockUserManager.getProfiles(mUserId)).thenReturn(profiles);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mPreferenceController.refreshUi();

        // Title + two profiles with one account each.
        assertThat(mPreference.getPreferenceCount()).isEqualTo(3);
        assertThat(mPreference.getPreference(1).getTitle()).isEqualTo(accountName1);
        assertThat(mPreference.getPreference(2).getTitle()).isEqualTo(accountName2);
    }

    @Test
    public void refreshUi_missingAccountDescription_skipsAccount() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        addAccountAndDescription(mUserId, "account name with desc");
        String accountNameNoDesc = "account name no desc";
        addAccount(mUserId, accountNameNoDesc);

        mPreferenceController.refreshUi();

        // Title + one account with valid description.
        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);
        assertThat(mPreference.getPreference(1).getTitle()).isNotEqualTo(accountNameNoDesc);
    }

    @Test
    public void refreshUi_accountAdded_addsPreferenceToGroup() {
        addAccountAndDescription(mUserId, "accountName");
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);

        String addedAccountName = "added account name";
        addAccountAndDescription(mUserId, addedAccountName);
        mPreferenceController.refreshUi();

        // Title + one already present account + one newly added account.
        assertThat(mPreference.getPreferenceCount()).isEqualTo(3);
        assertThat(mPreference.getPreference(2).getTitle()).isEqualTo(addedAccountName);
    }

    @Test
    public void refreshUi_accountRemoved_removesPreferenceFromGroup() {
        String accountNameToRemove = "account name to remove";
        addAccountAndDescription(mUserId, accountNameToRemove);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);

        clearAllAccounts();
        mPreferenceController.refreshUi();

        // Title only, all accounts removed.
        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreference.getPreference(0).getTitle()).isNotEqualTo(accountNameToRemove);
    }

    private void addAccountAndDescription(int profileId, String accountName) {
        addDescription(profileId, accountName);
        addAccount(profileId, accountName);
    }

    private void addAccount(int profileId, String accountName) {
        String type = accountName + "_type";
        if (!mAccountMap.containsKey(profileId)) {
            mAccountMap.put(profileId, new ArrayList<>());
        }
        mAccountMap.get(profileId).add(new Account(accountName, type));
        updateAccountManagerMocks();
    }

    private void addDescription(int profileId, String accountName) {
        String type = accountName + "_type";
        if (!mAuthenticatorDescriptionMap.containsKey(profileId)) {
            mAuthenticatorDescriptionMap.put(profileId, new ArrayList<>());
        }
        mAuthenticatorDescriptionMap.get(profileId).add(
                new AuthenticatorDescription(type, "packageName",
                        /* labelId= */ 0, /* iconId= */ 0, /* smallIconId= */ 0, /* prefId= */ 0));
        updateAccountManagerMocks();
    }

    private void clearAllAccounts() {
        mAuthenticatorDescriptionMap.clear();
        mAccountMap.clear();
        updateAccountManagerMocks();
    }

    private void updateAccountManagerMocks() {
        when(mMockAccountManager.getAuthenticatorTypesAsUser(anyInt())).then(
                (Answer<AuthenticatorDescription[]>) invocation -> {
                    Object[] args = invocation.getArguments();
                    int profileId = (Integer) args[0];
                    return mAuthenticatorDescriptionMap.containsKey(profileId)
                            ? mAuthenticatorDescriptionMap.get(profileId).toArray(
                            new AuthenticatorDescription[0])
                            : new AuthenticatorDescription[0];
                });
        when(mMockAccountManager.getAccountsAsUser(anyInt())).then(
                (Answer<Account[]>) invocation -> {
                    Object[] args = invocation.getArguments();
                    int profileId = (Integer) args[0];
                    return mAccountMap.containsKey(profileId)
                            ? mAccountMap.get(profileId).toArray(new Account[0])
                            : new Account[0];
                });
    }
}
