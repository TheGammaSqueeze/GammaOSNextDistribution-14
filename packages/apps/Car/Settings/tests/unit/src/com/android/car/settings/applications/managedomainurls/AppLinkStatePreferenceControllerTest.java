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

import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED;

import static com.android.car.settings.applications.managedomainurls.AppLaunchSettingsBasePreferenceController.sBrowserIntent;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class AppLinkStatePreferenceControllerTest {

    private static final String TEST_PACKAGE_NAME = "com.example.test";
    private static final int TEST_PACKAGE_ID = 1;
    private static final String TEST_PATH = "TEST_PATH";
    private final int mUserId = UserHandle.myUserId();

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private ListPreference mPreference;
    private AppLinkStatePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private PackageManager mMockPackageManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mPreference = new ListPreference(mContext);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new AppLinkStatePreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mMockPackageManager);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void onCreate_isBrowserApp_isDisabled() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(true);

        mPreferenceController.setAppEntry(entry);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void onCreate_isNotBrowserApp_noDomainUrls_isDisabled() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = 0;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);

        mPreferenceController.setAppEntry(entry);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void onCreate_isNotBrowserApp_hasDomainUrls_isEnabled() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);

        mPreferenceController.setAppEntry(entry);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void onCreate_isNotBrowserApp_hasDomainUrls_defaultState_entrySetToAsk() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);
        when(mMockPackageManager.getIntentVerificationStatusAsUser(TEST_PACKAGE_NAME, mUserId))
                .thenReturn(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED);

        mPreferenceController.setAppEntry(entry);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getEntry()).isEqualTo(
                mContext.getString(R.string.app_link_open_ask));
    }

    @Test
    public void onCreate_isNotBrowserApp_hasDomainUrls_askState_entrySetToAsk() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);
        when(mMockPackageManager.getIntentVerificationStatusAsUser(TEST_PACKAGE_NAME, mUserId))
                .thenReturn(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK);

        mPreferenceController.setAppEntry(entry);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getEntry()).isEqualTo(
                mContext.getString(R.string.app_link_open_ask));
    }

    @Test
    public void onCreate_isNotBrowserApp_hasDomainUrls_alwaysState_entrySetToAlways() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);
        when(mMockPackageManager.getIntentVerificationStatusAsUser(TEST_PACKAGE_NAME, mUserId))
                .thenReturn(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS);

        mPreferenceController.setAppEntry(entry);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getEntry()).isEqualTo(
                mContext.getString(R.string.app_link_open_always));
    }

    @Test
    public void onCreate_isNotBrowserApp_hasDomainUrls_neverState_entrySetToNever() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);
        when(mMockPackageManager.getIntentVerificationStatusAsUser(TEST_PACKAGE_NAME, mUserId))
                .thenReturn(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER);

        mPreferenceController.setAppEntry(entry);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getEntry()).isEqualTo(
                mContext.getString(R.string.app_link_open_never));
    }

    private void setupIsBrowserApp(boolean isBrowserApp) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.handleAllWebDataURI = isBrowserApp;
        sBrowserIntent.setPackage(TEST_PACKAGE_NAME);

        when(mMockPackageManager.queryIntentActivitiesAsUser(sBrowserIntent,
                PackageManager.MATCH_ALL, mUserId)).thenReturn(
                Collections.singletonList(resolveInfo));
    }
}
