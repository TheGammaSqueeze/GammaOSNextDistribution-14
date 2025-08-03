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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.accounts.Account;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.SyncAdapterType;
import android.content.SyncInfo;
import android.os.UserHandle;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.ResourceTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionIconPreference;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class AccountDetailsWithSyncStatusPreferenceControllerTest {
    private static final int USER_ID = 3;
    private static final String ACCOUNT_NAME = "Name";
    private static final String ACCOUNT_TYPE = "com.acct";
    private static final String AUTHORITY = "authority";
    private static final String AUTHORITY_2 = "authority2";
    private final Account mAccount = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
    private final UserHandle mUserHandle = new UserHandle(USER_ID);

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUiTwoActionIconPreference mPreference;
    private AccountDetailsWithSyncStatusPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    private List<SyncInfo> mCurrentSyncs;
    private Set<SyncAdapterType> mSyncableAdapters;
    private Set<SyncAdapterType> mVisibleSyncAdapters;
    private List<String> mSyncsRequested;
    private List<String> mSyncsCanceled;

    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new CarUiTwoActionIconPreference(mContext);
        mPreferenceController = new TestAccountDetailsWithSyncStatusPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        mPreferenceController.setAccount(mAccount);
        mPreferenceController.setUserHandle(mUserHandle);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mCurrentSyncs = new ArrayList<>();
        mVisibleSyncAdapters = new HashSet<>();
        mSyncableAdapters = new HashSet<>();
        mSyncsRequested = new ArrayList<>();
        mSyncsCanceled = new ArrayList<>();

        mSession = ExtendedMockito.mockitoSession().mockStatic(AccountSyncHelper.class,
                withSettings().lenient()).startMocking();
        when(AccountSyncHelper.getVisibleSyncAdaptersForAccount(mContext, mAccount, mUserHandle))
                .thenReturn(mVisibleSyncAdapters);
        when(AccountSyncHelper.getSyncableSyncAdaptersForAccount(mAccount, mUserHandle))
                .thenReturn(mSyncableAdapters);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onCreate_syncIsNotFailing_summaryShouldBeBlank() {
        setUpVisibleSyncAdapters(AUTHORITY);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getSummary()).isEqualTo("");
    }

    @Test
    public void onCreate_syncIsFailing_summaryShouldBeSet() {
        setUpVisibleSyncAdapters(AUTHORITY);
        when(AccountSyncHelper.getSyncState(any(), anyBoolean(), anyBoolean()))
                .thenReturn(AccountSyncHelper.SyncState.FAILED);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getSummary()).isEqualTo(
                ResourceTestUtils.getString(mContext, "sync_is_failing"));
    }

    @Test
    public void onSyncStatusChanged_summaryShouldUpdated() {
        setUpVisibleSyncAdapters(AUTHORITY);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getSummary()).isEqualTo("");

        when(AccountSyncHelper.getSyncState(any(), anyBoolean(), anyBoolean()))
                .thenReturn(AccountSyncHelper.SyncState.FAILED);

        mPreferenceController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(
                ResourceTestUtils.getString(mContext, "sync_is_failing"));
    }

    @Test
    public void onButtonClicked_doesNotHaveActiveSyncs_shouldSyncSyncableAdapters() {
        when(AccountSyncHelper.syncIsAllowed(eq(mAccount), anyString(), eq(USER_ID))).thenReturn(
                true);
        when(AccountSyncHelper.requestSyncIfAllowed(any(), anyString(),
                anyInt())).thenCallRealMethod();

        setUpSyncAdapters(AUTHORITY, AUTHORITY_2);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performSecondaryActionClick();

        assertThat(mSyncsRequested).contains(AUTHORITY);
        assertThat(mSyncsRequested).contains(AUTHORITY_2);
    }

    @Test
    public void onButtonClicked_doesNotHaveActiveSyncs_syncNotAllowed_shouldNotSyncAdapter() {
        when(AccountSyncHelper.syncIsAllowed(mAccount, AUTHORITY, USER_ID)).thenReturn(true);
        when(AccountSyncHelper.syncIsAllowed(mAccount, AUTHORITY_2, USER_ID)).thenReturn(false);
        when(AccountSyncHelper.requestSyncIfAllowed(any(), anyString(),
                anyInt())).thenCallRealMethod();

        setUpSyncAdapters(AUTHORITY, AUTHORITY_2);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performSecondaryActionClick();

        assertThat(mSyncsRequested).contains(AUTHORITY);
        assertThat(mSyncsRequested).doesNotContain(AUTHORITY_2);
    }

    @Test
    public void onButtonClicked_doesHaveActiveSyncs_shouldCancelSyncForSyncableAdapters() {
        // Add active syncs
        SyncInfo syncInfo = mock(SyncInfo.class);
        mCurrentSyncs.add(syncInfo);
        setUpSyncAdapters(AUTHORITY, AUTHORITY_2);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performSecondaryActionClick();

        assertThat(mSyncsCanceled).contains(AUTHORITY);
        assertThat(mSyncsCanceled).contains(AUTHORITY_2);
    }

    private void setUpSyncAdapters(String... authorities) {
        for (String authority : authorities) {
            // Adds a sync adapter type that has the right account type and is visible.
            SyncAdapterType syncAdapterType = new SyncAdapterType(authority,
                    ACCOUNT_TYPE, /* userVisible= */ true, /* supportsUploading= */ true);
            mSyncableAdapters.add(syncAdapterType);
        }
    }

    private void setUpVisibleSyncAdapters(String... authorities) {
        for (String authority : authorities) {
            // Adds a sync adapter type that has the right account type and is visible.
            SyncAdapterType syncAdapterType = new SyncAdapterType(authority,
                    ACCOUNT_TYPE, /* userVisible= */ true, /* supportsUploading= */ true);
            mVisibleSyncAdapters.add(syncAdapterType);
        }
    }

    private class TestAccountDetailsWithSyncStatusPreferenceController
            extends AccountDetailsWithSyncStatusPreferenceController {

        TestAccountDetailsWithSyncStatusPreferenceController(Context context,
                String preferenceKey, FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        List<SyncInfo> getCurrentSyncs(int userId) {
            return mCurrentSyncs;
        }

        @Override
        void requestSync(String authority, int userId) {
            if (AccountSyncHelper.requestSyncIfAllowed(getAccount(), authority, userId)) {
                mSyncsRequested.add(authority);
            }
        }

        @Override
        void cancelSync(String authority, int userId) {
            mSyncsCanceled.add(authority);
        }
    }
}
