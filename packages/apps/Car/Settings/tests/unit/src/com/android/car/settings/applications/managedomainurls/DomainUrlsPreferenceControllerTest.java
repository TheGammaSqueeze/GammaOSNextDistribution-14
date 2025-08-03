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

package com.android.car.settings.applications.managedomainurls;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.ArraySet;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class DomainUrlsPreferenceControllerTest {
    private static final String TEST_PACKAGE_NAME = "com.example.test";
    private static final int TEST_PACKAGE_ID = 1;
    private static final String TEST_PATH = "TEST_PATH";
    private static final String TEST_DOMAIN = "example.com";
    private static final String TEST_SUMMARY = "test_summary";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;
    private DomainUrlsPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;
    private ApplicationsState.AppEntry mAppEntry;
    private ArraySet<String> mMockDomains;
    private boolean mIsBrowserApp;

    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new CarUiPreference(mContext);
        mPreferenceController = new TestDomainUrlsPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);

        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        mAppEntry = new ApplicationsState.AppEntry(mContext, info, TEST_PACKAGE_ID);
        mMockDomains = new ArraySet<>();

        mSession = ExtendedMockito.mockitoSession().mockStatic(DomainUrlsUtils.class,
                withSettings().lenient()).startMocking();
        when(DomainUrlsUtils.getHandledDomains(any(PackageManager.class),
                eq(TEST_PACKAGE_NAME))).thenReturn(mMockDomains);
        when(DomainUrlsUtils.getDomainsSummary(mContext, TEST_PACKAGE_NAME, UserHandle.myUserId(),
                mMockDomains)).thenReturn(TEST_SUMMARY);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onCreate_isBrowserApp_isDisabled() {
        mIsBrowserApp = true;
        mMockDomains.add(TEST_DOMAIN);
        mPreferenceController.setAppEntry(mAppEntry);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void onCreate_isNotBrowserApp_isEnabled() {
        mIsBrowserApp = false;
        mMockDomains.add(TEST_DOMAIN);
        mPreferenceController.setAppEntry(mAppEntry);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void onCreate_hasNoDomains_isDisabled() {
        mIsBrowserApp = false;
        mPreferenceController.setAppEntry(mAppEntry);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void onCreate_isBrowserApp_summarySet() {
        mIsBrowserApp = true;
        mMockDomains.add(TEST_DOMAIN);
        mPreferenceController.setAppEntry(mAppEntry);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getSummary()).isEqualTo(TEST_SUMMARY);
    }

    @Test
    public void onCreate_isNotBrowserApp_summarySet() {
        mIsBrowserApp = false;
        mMockDomains.add(TEST_DOMAIN);
        mPreferenceController.setAppEntry(mAppEntry);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getSummary()).isEqualTo(TEST_SUMMARY);
    }

    @Test
    public void performClick_isNotBrowserApp_opensDialog() {
        mIsBrowserApp = false;
        mMockDomains.add(TEST_DOMAIN);
        mPreferenceController.setAppEntry(mAppEntry);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        verify(mFragmentController).showDialog(
                any(ConfirmationDialogFragment.class), eq(ConfirmationDialogFragment.TAG));
    }

    private class TestDomainUrlsPreferenceController extends DomainUrlsPreferenceController {

        TestDomainUrlsPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected boolean isBrowserApp() {
            return mIsBrowserApp;
        }
    }
}
