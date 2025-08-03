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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.IUsbManager;
import android.os.RemoteException;
import android.os.UserHandle;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.applications.AppUtils;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class ClearDefaultsPreferenceControllerTest {

    private static final String TEST_PACKAGE_NAME = "com.example.test";
    private static final String TEST_OTHER_PACKAGE_NAME = "com.example.other.test";
    private static final int TEST_PACKAGE_ID = 1;
    private static final String TEST_PATH = "TEST_PATH";
    private final int mUserId = UserHandle.myUserId();

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUiPreference mPreference;
    private ClearDefaultsPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private IUsbManager mMockIUsbManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mPreference = new CarUiPreference(mContext);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new ClearDefaultsPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mMockPackageManager, mMockIUsbManager);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mSession = ExtendedMockito.mockitoSession().mockStatic(
                AppUtils.class, withSettings().lenient()).startMocking();
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onCreate_hasPreferredActivities_hasSummary() {
        ExtendedMockito.when(AppUtils.hasPreferredActivities(mMockPackageManager,
                TEST_PACKAGE_NAME)).thenReturn(true);

        setupPreferenceController();

        assertThat(mPreference.getSummary().toString()).isNotEmpty();
    }

    @Test
    public void onCreate_isDefaultBrowser_hasSummary() {
        ExtendedMockito.when(AppUtils.hasPreferredActivities(mMockPackageManager,
                TEST_PACKAGE_NAME)).thenReturn(false);
        when(mMockPackageManager.getDefaultBrowserPackageNameAsUser(mUserId))
                .thenReturn(TEST_PACKAGE_NAME);

        setupPreferenceController();

        assertThat(mPreference.getSummary().toString()).isNotEmpty();
    }

    @Test
    public void onCreate_hasUsbDefaults_hasSummary() throws RemoteException {
        ExtendedMockito.when(AppUtils.hasPreferredActivities(mMockPackageManager,
                TEST_PACKAGE_NAME)).thenReturn(false);
        when(mMockPackageManager.getDefaultBrowserPackageNameAsUser(mUserId))
                .thenReturn(TEST_OTHER_PACKAGE_NAME);
        when(mMockIUsbManager.hasDefaults(TEST_PACKAGE_NAME, mUserId)).thenReturn(true);

        setupPreferenceController();

        assertThat(mPreference.getSummary().toString()).isNotEmpty();
    }

    @Test
    public void onCreate_autoLaunchDisabled_hasNoSummary() throws RemoteException {
        ExtendedMockito.when(AppUtils.hasPreferredActivities(mMockPackageManager,
                TEST_PACKAGE_NAME)).thenReturn(false);
        when(mMockPackageManager.getDefaultBrowserPackageNameAsUser(mUserId))
                .thenReturn(TEST_OTHER_PACKAGE_NAME);
        when(mMockIUsbManager.hasDefaults(TEST_PACKAGE_NAME, mUserId)).thenReturn(false);

        setupPreferenceController();

        assertThat(mPreference.getSummary()).isNull();
    }

    @Test
    public void performClick_hasUsbManager_hasPreferredActivities_clearsPreferredActivities() {
        ExtendedMockito.when(AppUtils.hasPreferredActivities(mMockPackageManager,
                TEST_PACKAGE_NAME)).thenReturn(true);
        when(mMockPackageManager.getDefaultBrowserPackageNameAsUser(mUserId))
                .thenReturn(TEST_OTHER_PACKAGE_NAME);

        setupPreferenceController();
        mPreference.performClick();

        verify(mMockPackageManager).clearPackagePreferredActivities(TEST_PACKAGE_NAME);
    }

    @Test
    public void performClick_hasUsbManager_isDefaultBrowser_clearsDefaultBrowser() {
        ExtendedMockito.when(AppUtils.hasPreferredActivities(mMockPackageManager,
                TEST_PACKAGE_NAME)).thenReturn(false);
        when(mMockPackageManager.getDefaultBrowserPackageNameAsUser(mUserId))
                .thenReturn(TEST_PACKAGE_NAME);

        setupPreferenceController();
        mPreference.performClick();

        verify(mMockPackageManager).setDefaultBrowserPackageNameAsUser(/* packageName= */ null,
                mUserId);
    }

    @Test
    public void performClick_hasUsbDefaults_clearsUsbDefaults() throws RemoteException {
        ExtendedMockito.when(AppUtils.hasPreferredActivities(mMockPackageManager,
                TEST_PACKAGE_NAME)).thenReturn(false);
        when(mMockPackageManager.getDefaultBrowserPackageNameAsUser(mUserId))
                .thenReturn(TEST_OTHER_PACKAGE_NAME);
        when(mMockIUsbManager.hasDefaults(TEST_PACKAGE_NAME, mUserId)).thenReturn(true);

        setupPreferenceController();
        mPreference.performClick();

        verify(mMockIUsbManager).clearDefaults(TEST_PACKAGE_NAME, mUserId);
    }

    private void setupPreferenceController() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        mPreferenceController.setAppEntry(entry);
        mPreferenceController.onCreate(mLifecycleOwner);
    }
}
