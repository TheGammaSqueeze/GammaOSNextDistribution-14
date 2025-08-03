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

package com.android.car.settings.accounts;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.accounts.Account;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserHandle;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionIconPreference;
import com.android.settingslib.accounts.AuthenticatorHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class AccountDetailsBasePreferenceControllerTest {
    private static final String ACCOUNT_NAME = "Name";
    private static final String ACCOUNT_TYPE = "com.acct";
    private final Account mAccount = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
    private final UserHandle mUserHandle = new UserHandle(0);

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUiTwoActionIconPreference mPreference;
    private AccountDetailsBasePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private AuthenticatorHelper mAuthenticatorHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new CarUiTwoActionIconPreference(mContext);
        mPreferenceController = new TestAccountDetailsBasePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
    }

    @Test
    public void checkInitialized_accountSetAndUserHandleSet_doesNothing() {
        mPreferenceController.setAccount(mAccount);
        mPreferenceController.setUserHandle(mUserHandle);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void checkInitialized_nullAccount_throwsIllegalStateException() {
        mPreferenceController.setUserHandle(mUserHandle);
        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                        mPreference));
    }

    @Test
    public void checkInitialized_nullUserHandle_throwsIllegalStateException() {
        mPreferenceController.setAccount(mAccount);
        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                        mPreference));
    }

    @Test
    public void onCreate_shouldSetTitle() {
        mPreferenceController.setAccount(mAccount);
        mPreferenceController.setUserHandle(mUserHandle);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getTitle().toString()).isEqualTo(ACCOUNT_NAME);
    }

    @Test
    public void onCreate_shouldSetIcon() {
        mPreferenceController.setAccount(mAccount);
        mPreferenceController.setUserHandle(mUserHandle);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        when(mAuthenticatorHelper.getDrawableForType(mContext, mAccount.type)).thenReturn(
                mContext.getDrawable(R.drawable.ic_add));

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getIcon()).isNotNull();
    }

    private class TestAccountDetailsBasePreferenceController
            extends AccountDetailsBasePreferenceController {

        TestAccountDetailsBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        AuthenticatorHelper getAuthenticatorHelper() {
            return mAuthenticatorHelper;
        }
    }
}
