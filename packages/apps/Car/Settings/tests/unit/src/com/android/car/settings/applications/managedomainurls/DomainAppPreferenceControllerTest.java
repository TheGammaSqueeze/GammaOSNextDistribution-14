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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class DomainAppPreferenceControllerTest {

    private static final String TEST_PACKAGE_NAME = "com.android.test.package";
    private static final int TEST_PACKAGE_ID = 1;
    private static final String TEST_LABEL = "Test App";
    private static final String TEST_PATH = "TEST_PATH";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceGroup;
    private DomainAppPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private ApplicationsState mMockApplicationsState;
    @Mock
    private ApplicationsState.Session mMockSession;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new DomainAppPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mMockApplicationsState, mMockPackageManager);
    }

    @Test
    public void checkInitialized_noLifecycle_throwsError() {
        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                        mPreferenceGroup));
    }

    @Test
    public void onRebuildComplete_sessionLoadsValues_preferenceGroupHasValues() {
        setupPreferenceController();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        ArrayList<ApplicationsState.AppEntry> apps = new ArrayList<>();
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);
        entry.label = TEST_LABEL;
        apps.add(entry);
        mPreferenceController.mApplicationStateCallbacks.onRebuildComplete(apps);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    @UiThreadTest
    public void performClick_startsApplicationLaunchSettingsFragmentWithPackageName() {
        setupPreferenceController();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        ArrayList<ApplicationsState.AppEntry> apps = new ArrayList<>();
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);
        entry.label = TEST_LABEL;
        apps.add(entry);
        mPreferenceController.mApplicationStateCallbacks.onRebuildComplete(apps);

        Preference preference = mPreferenceGroup.getPreference(0);
        preference.performClick();

        ArgumentCaptor<ApplicationLaunchSettingsFragment> captor = ArgumentCaptor.forClass(
                ApplicationLaunchSettingsFragment.class);
        verify(mMockFragmentController).launchFragment(captor.capture());

        String pkgName = captor.getValue().getArguments().getString(
                ApplicationLaunchSettingsFragment.ARG_PACKAGE_NAME);
        assertThat(pkgName).isEqualTo(TEST_PACKAGE_NAME);
    }

    private void setupPreferenceController() {
        when(mMockApplicationsState.newSession(any(), any())).thenReturn(mMockSession);
        mPreferenceController.setLifecycle(mLifecycleOwner.getLifecycle());
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
    }
}
