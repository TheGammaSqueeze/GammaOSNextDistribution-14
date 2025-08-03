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

import static com.android.car.settings.accounts.ChooseAccountPreferenceController.ADD_ACCOUNT_REQUEST_CODE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.profiles.ProfileHelper;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.accounts.AuthenticatorHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Arrays;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class ChooseAccountPreferenceControllerTest {
    private static final int USER_ID = 0;
    private static final String USER_NAME = "name";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private LogicalPreferenceGroup mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private ChooseAccountPreferenceController mController;
    private MockitoSession mSession;
    private Map<String, String> mAccountTypeToLabelMap;
    private Account[] mAccounts;
    private AuthenticatorDescription[] mAuthenticatorDescriptions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private AuthenticatorHelper mMockAuthenticatorHelper;
    @Mock
    private ProfileHelper mMockProfileHelper;
    @Mock
    private AccountManager mMockAccountManager;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreference = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreference);
        initMocks();
        mController = new ChooseAccountPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        mController.setAuthenticatorHelper(mMockAuthenticatorHelper);
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
    public void onCreate_authenticatorPreferencesShouldBeSet() {
        mController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);

        Preference acct1Pref = mPreference.getPreference(0);
        assertThat(acct1Pref.getTitle().toString()).isEqualTo(
                mContext.getString(R.string.account_type1_label));

        Preference acct2Pref = mPreference.getPreference(1);
        assertThat(acct2Pref.getTitle().toString()).isEqualTo(
                mContext.getString(R.string.account_type2_label));
    }

    @Test
    public void onAccountsUpdate_currentUserUpdated_shouldForceUpdate() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);

        mAuthenticatorDescriptions = Arrays.copyOf(mAuthenticatorDescriptions,
                mAuthenticatorDescriptions.length + 1);
        mAuthenticatorDescriptions[mAuthenticatorDescriptions.length - 1] =
                new AuthenticatorDescription("com.acct3", "com.android.car.settings",
                        R.string.account_type3_label, 0, 0, 0, false);
        when(mMockAccountManager.getAuthenticatorTypesAsUser(anyInt())).thenReturn(
                mAuthenticatorDescriptions);

        mAccounts = Arrays.copyOf(mAccounts, mAccounts.length + 1);
        mAccounts[mAccounts.length - 1] = new Account(
                mContext.getString(R.string.account_type3_label), "com.acct3");
        when(mMockAccountManager.getAccountsAsUser(anyInt())).thenReturn(mAccounts);

        mController.getAccountTypesHelper().updateAuthorizedAccountTypes(false);

        assertThat(mPreference.getPreferenceCount()).isEqualTo(3);
        Preference acct3Pref = mPreference.getPreference(2);
        assertThat(acct3Pref.getTitle().toString()).isEqualTo(
                mContext.getString(R.string.account_type3_label));
    }

    @Test
    public void onPreferenceClick_shouldStartActivity() {
        mController.onCreate(mLifecycleOwner);
        Preference acct1Pref = mPreference.getPreference(0);
        acct1Pref.performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);

        verify(mFragmentController).startActivityForResult(captor.capture(),
                anyInt(), any(ActivityResultCallback.class));

        Intent intent = captor.getValue();
        assertThat(intent.getComponent().getClassName()).isEqualTo(
                AddAccountActivity.class.getName());
        assertThat(intent.getStringExtra(AddAccountActivity.EXTRA_SELECTED_ACCOUNT)).isEqualTo(
                "com.acct1");
    }

    @Test
    public void onAccountAdded_shouldGoBack() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);

        mController.processActivityResult(ADD_ACCOUNT_REQUEST_CODE, /* resultCode= */ 0, /* data= */
                null);

        verify(mFragmentController).goBack();
    }

    private void initMocks() {
        mAccountTypeToLabelMap = Map.of(
                "com.acct1", mContext.getString(R.string.account_type1_label),
                "com.acct2", mContext.getString(R.string.account_type2_label),
                "com.acct3", mContext.getString(R.string.account_type3_label));
        when(mMockAuthenticatorHelper.getLabelForType(any(), any())).then(invocation -> {
            Object[] args = invocation.getArguments();
            return mAccountTypeToLabelMap.get((String) args[1]);
        });
        when(mMockAuthenticatorHelper.getDrawableForType(any(), any())).thenReturn(null);

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(ProfileHelper.class, withSettings().lenient())
                .mockStatic(AccountManager.class, withSettings().lenient())
                .startMocking();

        when(ProfileHelper.getInstance(mContext)).thenReturn(mMockProfileHelper);
        UserInfo userInfo = new UserInfo(USER_ID, USER_NAME, 0);
        when(mMockProfileHelper.getCurrentProcessUserInfo()).thenReturn(userInfo);
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);

        when(AccountManager.get(mContext)).thenReturn(mMockAccountManager);
        mAuthenticatorDescriptions = new AuthenticatorDescription[]{
                new AuthenticatorDescription("com.acct1", "com.android.car.settings",
                        R.string.account_type1_label, 0, 0, 0, false),
                new AuthenticatorDescription("com.acct2", "com.android.car.settings",
                        R.string.account_type2_label, 0, 0, 0, false)
        };
        mAccounts = new Account[]{
                new Account(mContext.getString(R.string.account_type1_label), "com.acct1"),
                new Account(mContext.getString(R.string.account_type2_label), "com.acct2")
        };
        when(mMockAccountManager.getAuthenticatorTypesAsUser(anyInt())).thenReturn(
                mAuthenticatorDescriptions);
        when(mMockAccountManager.getAccountsAsUser(anyInt())).thenReturn(mAccounts);
    }
}
