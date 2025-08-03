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

package com.android.car.settings.applications.specialaccess;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.Manifest;
import android.app.AppOpsManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Looper;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;
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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AppOpsPreferenceControllerTest {

    private static final int APP_OP_CODE = AppOpsManager.OP_WRITE_SETTINGS;
    private static final String PERMISSION = Manifest.permission.WRITE_SETTINGS;
    private static final int NEGATIVE_MODE = AppOpsManager.MODE_ERRORED;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceGroup;
    private AppOpsPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private AppOpsManager mMockAppOpsManager;
    @Mock
    private AppEntryListManager mMockAppEntryListManager;
    @Mock
    private ApplicationsState mMockApplicationsState;
    @Captor
    private ArgumentCaptor<AppEntryListManager.Callback> mCallbackCaptor;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        when(mMockApplicationsState.getBackgroundLooper()).thenReturn(Looper.getMainLooper());
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new AppOpsPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mMockAppOpsManager);
    }

    @Test
    public void checkInitialized_noOpCode_throwsIllegalStateException() {
        mPreferenceController.init(AppOpsManager.OP_NONE, PERMISSION, NEGATIVE_MODE);

        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil
                        .assignPreference(mPreferenceController, mPreferenceGroup));
    }

    @Test
    public void checkInitialized_noPermission_throwsIllegalStateException() {
        mPreferenceController.init(APP_OP_CODE, /* permission= */ null, NEGATIVE_MODE);

        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil
                        .assignPreference(mPreferenceController, mPreferenceGroup));
    }

    @Test
    public void checkInitialized_noNegativeOpMode_throwsIllegalStateException() {
        mPreferenceController.init(APP_OP_CODE, PERMISSION, /* negativeOpMode= */-1);

        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil
                        .assignPreference(mPreferenceController, mPreferenceGroup));
    }

    @Test
    public void onStart_startsListManager() {
        setupPreferenceController();
        mPreferenceController.onStart(mLifecycleOwner);

        verify(mMockAppEntryListManager).start();
    }

    @Test
    public void onStop_stopsListManager() {
        setupPreferenceController();
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onStop(mLifecycleOwner);

        verify(mMockAppEntryListManager).stop();
    }

    @Test
    public void onDestroy_destroysListManager() {
        setupPreferenceController();
        mPreferenceController.onDestroy(mLifecycleOwner);

        verify(mMockAppEntryListManager).destroy();
    }

    @Test
    public void onAppEntryListChanged_addsPreferencesForEntries() {
        setupPreferenceController();
        mPreferenceController.onStart(mLifecycleOwner);
        List<ApplicationsState.AppEntry> entries = Arrays.asList(
                createAppEntry("test.package", /* uid= */ 1, /* isOpPermissible= */ true),
                createAppEntry("another.test.package", /* uid= */ 2, /* isOpPermissible= */ false));

        mCallbackCaptor.getValue().onAppEntryListChanged(entries);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);
        assertThat(((TwoStatePreference) mPreferenceGroup.getPreference(0)).isChecked()).isTrue();
        assertThat(((TwoStatePreference) mPreferenceGroup.getPreference(1)).isChecked()).isFalse();
    }

    @Test
    public void onPreferenceChange_checkedState_setsAppOpModeAllowed() {
        setupPreferenceController();
        mPreferenceController.onStart(mLifecycleOwner);
        String packageName = "test.package";
        int uid = 1;
        List<ApplicationsState.AppEntry> entries = Collections.singletonList(
                createAppEntry(packageName, uid, /* isOpPermissible= */ false));
        mCallbackCaptor.getValue().onAppEntryListChanged(entries);
        TwoStatePreference appPref = (TwoStatePreference) mPreferenceGroup.getPreference(0);

        appPref.performClick();

        verify(mMockAppOpsManager).setMode(APP_OP_CODE, uid, packageName,
                AppOpsManager.MODE_ALLOWED);
    }

    @Test
    public void onPreferenceChange_uncheckedState_setsNegativeAppOpMode() {
        setupPreferenceController();
        mPreferenceController.onStart(mLifecycleOwner);
        String packageName = "test.package";
        int uid = 1;
        List<ApplicationsState.AppEntry> entries = Collections.singletonList(
                createAppEntry(packageName, uid, /* isOpPermissible= */ true));
        mCallbackCaptor.getValue().onAppEntryListChanged(entries);
        TwoStatePreference appPref = (TwoStatePreference) mPreferenceGroup.getPreference(0);

        appPref.performClick();

        verify(mMockAppOpsManager).setMode(APP_OP_CODE, uid, packageName,
                NEGATIVE_MODE);
    }

    @Test
    public void onPreferenceChange_updatesEntry() {
        setupPreferenceController();
        mPreferenceController.onStart(mLifecycleOwner);
        List<ApplicationsState.AppEntry> entries = Collections.singletonList(
                createAppEntry("test.package", /* uid= */ 1, /* isOpPermissible= */ false));
        mCallbackCaptor.getValue().onAppEntryListChanged(entries);
        TwoStatePreference appPref = (TwoStatePreference) mPreferenceGroup.getPreference(0);

        appPref.performClick();

        verify(mMockAppEntryListManager).forceUpdate(entries.get(0));
    }

    @Test
    public void showSystem_updatesEntries() {
        setupPreferenceController();
        mPreferenceController.onStart(mLifecycleOwner);

        mPreferenceController.setShowSystem(true);

        verify(mMockAppEntryListManager).forceUpdate();
    }

    @Test
    public void appFilter_showingSystemApps_keepsSystemEntries() {
        setupPreferenceController();
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setShowSystem(true);
        ArgumentCaptor<AppEntryListManager.AppFilterProvider> filterCaptor =
                ArgumentCaptor.forClass(AppEntryListManager.AppFilterProvider.class);
        verify(mMockAppEntryListManager).init(any(), filterCaptor.capture(), any());
        ApplicationsState.AppFilter filter = filterCaptor.getValue().getAppFilter();

        ApplicationsState.AppEntry systemApp = createAppEntry("test.package",
                /* uid= */ 1, /* isOpPermissible= */false);
        systemApp.info.flags |= ApplicationInfo.FLAG_SYSTEM;

        assertThat(filter.filterApp(systemApp)).isTrue();
    }

    @Test
    public void appFilter_notShowingSystemApps_removesSystemEntries() {
        setupPreferenceController();
        mPreferenceController.onStart(mLifecycleOwner);
        // Not showing system by default.
        ArgumentCaptor<AppEntryListManager.AppFilterProvider> filterCaptor =
                ArgumentCaptor.forClass(AppEntryListManager.AppFilterProvider.class);
        verify(mMockAppEntryListManager).init(any(), filterCaptor.capture(), any());
        ApplicationsState.AppFilter filter = filterCaptor.getValue().getAppFilter();

        ApplicationsState.AppEntry systemApp = createAppEntry("test.package",
                /* uid= */ 1, /* isOpPermissible= */false);
        systemApp.info.flags |= ApplicationInfo.FLAG_SYSTEM;

        assertThat(filter.filterApp(systemApp)).isFalse();
    }

    @Test
    public void appFilter_removesNullExtraInfoEntries() {
        setupPreferenceController();
        mPreferenceController.onStart(mLifecycleOwner);
        ArgumentCaptor<AppEntryListManager.AppFilterProvider> filterCaptor =
                ArgumentCaptor.forClass(AppEntryListManager.AppFilterProvider.class);
        verify(mMockAppEntryListManager).init(any(), filterCaptor.capture(), any());
        ApplicationsState.AppFilter filter = filterCaptor.getValue().getAppFilter();

        ApplicationsState.AppEntry appEntry = createAppEntry("test.package",
                /* uid= */ 1, /* isOpPermissible= */false);
        appEntry.extraInfo = null;

        assertThat(filter.filterApp(appEntry)).isFalse();
    }

    private ApplicationsState.AppEntry createAppEntry(String packageName, int uid,
            boolean isOpPermissible) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        info.uid = uid;

        AppStateAppOpsBridge.PermissionState extraInfo = mock(
                AppStateAppOpsBridge.PermissionState.class);
        when(extraInfo.isPermissible()).thenReturn(isOpPermissible);

        ApplicationsState.AppEntry appEntry = mock(ApplicationsState.AppEntry.class);
        appEntry.info = info;
        appEntry.label = packageName;
        appEntry.extraInfo = extraInfo;

        return appEntry;
    }

    private void setupPreferenceController() {
        mPreferenceController.init(APP_OP_CODE, PERMISSION, NEGATIVE_MODE);
        mPreferenceController.mAppEntryListManager = mMockAppEntryListManager;

        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        verify(mMockAppEntryListManager).init(any(AppStateAppOpsBridge.class), any(),
                mCallbackCaptor.capture());
    }
}
