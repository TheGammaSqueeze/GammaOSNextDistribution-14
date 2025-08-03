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

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.profiles.ProfileHelper;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.ResourceTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.accounts.AuthenticatorHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// TODO(b/201465719) add unittest to show ActionDisabledByAdminDialog
@RunWith(AndroidJUnit4.class)
public class AccountListPreferenceControllerTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;

    private static final int USER_ID = 0;
    private static final String USER_NAME = "name";
    private static final int NOT_THIS_USER_ID = 1;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private PreferenceCategory mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private AccountListPreferenceController mController;
    private MockitoSession mSession;
    private Set<String> mAuthenticatedAccountTypes;
    private Map<String, String> mAccountTypeToLabelMap;
    private Map<String, List<String>> mAccountTypeToNameMap;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private AuthenticatorHelper mMockAuthenticatorHelper;
    @Mock
    private ProfileHelper mMockProfileHelper;
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

        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreference = new PreferenceCategory(mContext);
        screen.addPreference(mPreference);
        initMocks();
        mController = new TestAccountListPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onCreate_preferenceCategoryTitleShouldBeSet() {
        mController.onCreate(mLifecycleOwner);
        String expectedTitle = ResourceTestUtils.getString(mContext, "account_list_title",
                USER_NAME);
        assertThat(mPreference.getTitle().toString()).isEqualTo(expectedTitle);
    }

    @Test
    public void onCreate_hasNoAccounts_shouldDisplayNoAccountPref() {
        mController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
        Preference noAccountPref = mPreference.getPreference(0);

        assertThat(noAccountPref.getTitle().toString()).isEqualTo(
                ResourceTestUtils.getString(mContext, "no_accounts_added"));
    }

    @Test
    public void onCreate_hasAccounts_shouldDisplayAccounts() {
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        addAccount(/* name= */ "Account2", /* type= */ "com.acct2");

        mController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);

        Preference firstPref = mPreference.getPreference(0);
        assertThat(firstPref.getTitle().toString()).isEqualTo("Account1");
        assertThat(firstPref.getSummary().toString()).isEqualTo(
                mContext.getString(R.string.account_type1_label));

        Preference secondPref = mPreference.getPreference(1);
        assertThat(secondPref.getTitle().toString()).isEqualTo("Account2");
        assertThat(secondPref.getSummary().toString()).isEqualTo(
                mContext.getString(R.string.account_type2_label));
    }

    @Test
    public void onCreate_hasUnauthenticatedAccount_shouldNotDisplayAccount() {
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        addAccount(/* name= */ "Account2", /* type= */ "com.acct2");
        addAccount(/* name= */ "Account3", /* type= */ "com.acct3");

        mController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);

        Preference firstPref = mPreference.getPreference(0);
        assertThat(firstPref.getTitle().toString()).isEqualTo("Account1");
        assertThat(firstPref.getSummary().toString()).isEqualTo(
                mContext.getString(R.string.account_type1_label));

        Preference secondPref = mPreference.getPreference(1);
        assertThat(secondPref.getTitle().toString()).isEqualTo("Account2");
        assertThat(secondPref.getSummary().toString()).isEqualTo(
                mContext.getString(R.string.account_type2_label));
    }

    @Test
    public void onAccountsUpdate_isThisUser_shouldForceUpdate() {
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        addAccount(/* name= */ "Account2", /* type= */ "com.acct2");

        mController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);

        removeAllAccounts();
        addAccount(/* name= */ "Account3", /* type= */ "com.acct1");

        mController.onAccountsUpdate(new UserHandle(USER_ID));

        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
        Preference firstPref = mPreference.getPreference(0);
        assertThat(firstPref.getTitle().toString()).isEqualTo("Account3");
        assertThat(firstPref.getSummary().toString()).isEqualTo(
                mContext.getString(R.string.account_type1_label));
    }

    @Test
    public void onAccountsUpdate_updatedUserIsNotCurrentUser_shouldNotForceUpdate() {
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        addAccount(/* name= */ "Account2", /* type= */ "com.acct2");

        mController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);

        removeAllAccounts();
        addAccount(/* name= */ "Account3", /* type= */ "com.acct1");

        mController.onAccountsUpdate(new UserHandle(NOT_THIS_USER_ID));

        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);
    }

    @Test
    public void onUsersUpdate_shouldForceUpdate() {
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        addAccount(/* name= */ "Account2", /* type= */ "com.acct2");

        mController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);

        removeAllAccounts();
        addAccount(/* name= */ "Account3", /* type= */ "com.acct1");

        mController.onUsersUpdate();

        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
        Preference firstPref = mPreference.getPreference(0);
        assertThat(firstPref.getTitle().toString()).isEqualTo("Account3");
        assertThat(firstPref.getSummary().toString()).isEqualTo(
                mContext.getString(R.string.account_type1_label));
    }

    @Test
    @UiThreadTest
    public void onAccountPreferenceClicked_shouldLaunchAccountDetailsFragment() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        mController.onCreate(mLifecycleOwner);

        Preference firstPref = mPreference.getPreference(0);
        firstPref.performClick();

        verify(mFragmentController).launchFragment(any(AccountDetailsFragment.class));
    }

    private void addAccount(String name, String type) {
        if (mAccountTypeToNameMap.containsKey(type)) {
            mAccountTypeToNameMap.get(type).add(name);
        } else {
            mAccountTypeToNameMap.put(type, Collections.singletonList(name));
        }
        updateEnabledAccountTypes();
    }

    private void removeAllAccounts() {
        mAccountTypeToNameMap.clear();
        updateEnabledAccountTypes();
    }

    @Test
    public void getAvailabilityStatus_demoOrGuest_notAvailable() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_demoOrGuest_notAvailable_zoneWrite() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_demoOrGuest_notAvailable_zoneRead() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_demoOrGuest_notAvailable_zoneHidden() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByUm_notAvailable() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByUm_notAvailable_zoneWrite() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByUm_notAvailable_zoneRead() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByUm_notAvailable_zoneHidden() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_demoOrGuest_restrictedByDpm_notAvailable() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_demoOrGuest_restrictedByDpm_notAvailable_zoneWrite() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_demoOrGuest_restrictedByDpm_notAvailable_zoneRead() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_demoOrGuest_restrictedByDpm_notAvailable_zoneHidden() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByDpm_availableForViewing() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_restrictedByDpm_availableForViewing_zoneWrite() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_restrictedByDpm_availableForViewing_zoneRead() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_restrictedByDpm_availableForViewing_zoneHidden() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_canModifyAccounts_available() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_canModifyAccounts_available_zoneWrite() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_canModifyAccounts_available_zoneRead() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_canModifyAccounts_available_zoneHidden() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    private void initMocks() {
        mAccountTypeToLabelMap = Map.of(
                "com.acct1", mContext.getString(R.string.account_type1_label),
                "com.acct2", mContext.getString(R.string.account_type2_label),
                "com.acct3", mContext.getString(R.string.account_type3_label));
        mAuthenticatedAccountTypes = Set.of("com.acct1", "com.acct2");
        mAccountTypeToNameMap = new HashMap<>();
        updateEnabledAccountTypes();
        when(mMockAuthenticatorHelper.getLabelForType(any(), any())).then(invocation -> {
            Object[] args = invocation.getArguments();
            String type = (String) args[1];
            return mAuthenticatedAccountTypes.contains(type)
                    ? mAccountTypeToLabelMap.get(type)
                    : null;
        });
        when(mMockAuthenticatorHelper.getDrawableForType(any(), any())).thenReturn(null);

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(ProfileHelper.class, withSettings().lenient())
                .mockStatic(AccountManager.class, withSettings().lenient())
                .startMocking();

        when(ProfileHelper.getInstance(mContext)).thenReturn(mMockProfileHelper);
        UserInfo userInfo = new UserInfo(USER_ID, USER_NAME, 0);
        when(mMockProfileHelper.getCurrentProcessUserInfo()).thenReturn(userInfo);

        when(AccountManager.get(mContext)).thenReturn(mMockAccountManager);
        when(mMockAccountManager.getAccountsByTypeAsUser(any(), any())).then(invocation -> {
            Object[] args = invocation.getArguments();
            String type = (String) args[0];
            List<String> accountNames = mAccountTypeToNameMap.get(type);
            Account[] accounts = new Account[accountNames.size()];
            for (int i = 0; i < accountNames.size(); i++) {
                accounts[i] = new Account(accountNames.get(i), type);
            }
            return accounts;
        });
    }

    private void updateEnabledAccountTypes() {
        when(mMockAuthenticatorHelper.getEnabledAccountTypes()).thenReturn(
                mAccountTypeToNameMap.keySet().toArray(new String[0]));
    }

    private class TestAccountListPreferenceController extends AccountListPreferenceController {

        TestAccountListPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        AuthenticatorHelper createAuthenticatorHelper() {
            return mMockAuthenticatorHelper;
        }
    }
}
