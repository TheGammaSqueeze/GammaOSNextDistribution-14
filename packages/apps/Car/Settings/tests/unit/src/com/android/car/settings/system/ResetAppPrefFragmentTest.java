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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.AppOpsManager;
import android.app.INotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.fragment.app.FragmentManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.settings.testutils.PollingCheck;
import com.android.car.ui.toolbar.MenuItem;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ResetAppPrefFragmentTest {
    private static final String ENABLED_PKG_NAME = "package.name.1";
    private static final String DISABLED_PKG_NAME = "package.name.2";
    private static final int UID = 1001010;

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private ResetAppPrefFragment mFragment;
    private BaseCarSettingsTestActivity mActivity;
    private FragmentManager mFragmentManager;
    private MenuItem mResetButton;
    private MockitoSession mSession;
    private List<ApplicationInfo> mAppInfoList;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Mock
    private PackageManager mMockPm;
    @Mock
    private INotificationManager mINotificationManager;
    @Mock
    private IPackageManager mIPackageManager;
    @Mock
    private AppOpsManager mAppOpsManager;
    @Mock
    private RestrictedLockUtils.EnforcedAdmin mEnforcedAdmin;
    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(ServiceManager.class, withSettings().lenient())
                .mockStatic(RestrictedLockUtilsInternal.class, withSettings().lenient())
                .mockStatic(UserHandle.class, withSettings().lenient())
                .startMocking();
        IBinder notificationBinder = mock(IBinder.class, withSettings().lenient());
        IBinder packageManagerBinder = mock(IBinder.class, withSettings().lenient());
        when(notificationBinder.queryLocalInterface("android.app.INotificationManager")).thenReturn(
                mINotificationManager);
        when(packageManagerBinder.queryLocalInterface(
                "android.content.pm.IPackageManager")).thenReturn(mIPackageManager);
        when(ServiceManager.getService(Context.NOTIFICATION_SERVICE)).thenReturn(
                notificationBinder);
        when(ServiceManager.getService("package")).thenReturn(packageManagerBinder);

        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivity.getSupportFragmentManager();
        setUpFragment();
        ExtendedMockito.lenient().when(mFragment.requireContext()).thenReturn(mContext);
        when(mContext.getApplicationContext()).thenReturn(mContext);
        when(mContext.getSystemService(AppOpsManager.class)).thenReturn(mAppOpsManager);
        when(mContext.getPackageManager()).thenReturn(mMockPm);
        mAppInfoList = new ArrayList<>();
        mAppInfoList.add(createApplicationInfo(ENABLED_PKG_NAME, true));
        mAppInfoList.add(createApplicationInfo(DISABLED_PKG_NAME, false));
        when(mMockPm.getInstalledApplications(anyInt())).thenReturn(mAppInfoList);
        when(UserHandle.myUserId()).thenReturn(100);

        mResetButton = mActivity.getToolbar().getMenuItems().get(0);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void resetClicked_resetsNotificationsForApps() throws Throwable {
        triggerResetButtonAndWaitForTask();

        verify(mINotificationManager)
                .setNotificationsEnabledForPackage(ENABLED_PKG_NAME, UID, true);
        verify(mINotificationManager)
                .setNotificationsEnabledForPackage(DISABLED_PKG_NAME, UID, true);
    }

    @Test
    public void resetClicked_appEnabled_doesNotEnable() throws Throwable {
        when(mMockPm.getApplicationEnabledSetting(ENABLED_PKG_NAME))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);

        triggerResetButtonAndWaitForTask();

        verify(mMockPm, never()).setApplicationEnabledSetting(ENABLED_PKG_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
    }

    @Test
    public void resetClicked_appDisabled_doesEnable() throws Throwable {
        when(mMockPm.getApplicationEnabledSetting(DISABLED_PKG_NAME))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);

        triggerResetButtonAndWaitForTask();

        verify(mMockPm).setApplicationEnabledSetting(DISABLED_PKG_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
    }

    @Test
    public void resetClicked_resetsApplicationPreferences() throws Throwable {
        triggerResetButtonAndWaitForTask();

        verify(mIPackageManager).resetApplicationPreferences(anyInt());
    }

    @Test
    public void resetClicked_adminDisallowResetsApplicationPreferences() throws Throwable {
        ExtendedMockito.when(RestrictedLockUtilsInternal.checkIfRestrictionEnforced(
                        mActivity,
                        UserManager.DISALLOW_APPS_CONTROL, UserHandle.myUserId()))
                .thenReturn(mEnforcedAdmin);
        ExtendedMockito.when(RestrictedLockUtilsInternal.hasBaseUserRestriction(mActivity,
                        UserManager.DISALLOW_APPS_CONTROL, UserHandle.myUserId()))
                .thenReturn(false);
        assertThrows(NullPointerException.class, ()->triggerResetButtonAndWaitForTask());
        verify(mIPackageManager, never()).resetApplicationPreferences(anyInt());
    }

    @Test
    public void resetClicked_resetsAllModes() throws Throwable {
        triggerResetButtonAndWaitForTask();

        verify(mAppOpsManager).resetAllModes();
    }

    private void triggerResetButtonAndWaitForTask() throws Throwable {
        mActivityTestRule.runOnUiThread(() -> mResetButton.performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // wait for async task
        PollingCheck.waitFor(
                () -> mFragment.mResetTask.getStatus() == AsyncTask.Status.FINISHED);
    }

    private void setUpFragment() throws Throwable {
        String resetPrefFragmentTag = "reset_app_pref_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(
                            R.id.fragment_container, new ResetAppPrefFragment(),
                            resetPrefFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (ResetAppPrefFragment)
                mFragmentManager.findFragmentByTag(resetPrefFragmentTag);
        ExtendedMockito.spyOn(mFragment);
    }

    private ApplicationInfo createApplicationInfo(String pkgName, boolean enabled) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = pkgName;
        applicationInfo.uid = UID;
        applicationInfo.sourceDir = "";
        applicationInfo.enabled = enabled;
        return applicationInfo;
    }
}
