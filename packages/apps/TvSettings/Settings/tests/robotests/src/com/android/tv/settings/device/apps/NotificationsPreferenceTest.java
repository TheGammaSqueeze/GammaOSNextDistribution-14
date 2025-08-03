/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tv.settings.device.apps;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.INotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.RemoteException;

import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowUtils.class, ShadowNotificationManager.class})
public class NotificationsPreferenceTest {

    private static final String PACKAGE_NAME = "com.example.test";
    private static final int PACKAGE_UID = 20;

    @Mock
    ApplicationInfo mApplicationInfo;
    @Mock
    ApplicationsState.AppEntry mAppEntry;
    @Mock
    PackageInfo mPackageInfo;
    @Mock
    PackageManager mPackageManager;
    @Mock
    INotificationManager mNotificationManager;
    @Mock
    Resources mResources;

    private Context mContext;
    private NotificationsPreference mNotificationsPreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = spy(RuntimeEnvironment.application);
        doReturn(mPackageManager).when(mContext).getPackageManager();
        try {
            when(mPackageManager.getPackageInfo(eq(PACKAGE_NAME), anyInt()))
                    .thenReturn(mPackageInfo);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        when(mContext.getResources()).thenReturn(mResources);
        ShadowNotificationManager.setINotificationManager(mNotificationManager);
        mApplicationInfo.packageName = PACKAGE_NAME;
        mApplicationInfo.uid = PACKAGE_UID;
        mAppEntry.info = mApplicationInfo;
        mPackageInfo.applicationInfo = mApplicationInfo;
        mPackageInfo.packageName = PACKAGE_NAME;
    }

    @Test
    public void testNotificationsPreference_systemAppNotificationsEnabledInRes() {
        setSystemApp(true);
        setNotificationsEnabledForPackage(PACKAGE_NAME, true);
        setNotificationsUnblockableByImportanceLocked(mApplicationInfo, true);
        mNotificationsPreference = new NotificationsPreference(mContext, mAppEntry);

        mNotificationsPreference.refresh();

        assertFalse(mNotificationsPreference.isEnabled());
    }

    @Test
    public void testNotificationsPreference_systemAppNotificationsEnabledNotInRes() {
        setSystemApp(true);
        setNotificationsEnabledForPackage(PACKAGE_NAME, true);
        setNotificationsUnblockableByImportanceLocked(mApplicationInfo, false);
        mNotificationsPreference = new NotificationsPreference(mContext, mAppEntry);

        mNotificationsPreference.refresh();

        assertFalse(mNotificationsPreference.isEnabled());
    }

    @Test
    public void testNotificationsPreference_notSystemAppNotificationsEnabledInRes() {
        setSystemApp(false);
        setNotificationsEnabledForPackage(PACKAGE_NAME, true);
        setNotificationsUnblockableByImportanceLocked(mApplicationInfo, true);
        mNotificationsPreference = new NotificationsPreference(mContext, mAppEntry);

        mNotificationsPreference.refresh();

        assertFalse(mNotificationsPreference.isEnabled());
    }

    @Test
    public void testNotificationsPreference_notSystemAppNotificationsEnabledNotInRes() {
        setSystemApp(false);
        setNotificationsEnabledForPackage(PACKAGE_NAME, true);
        setNotificationsUnblockableByImportanceLocked(mApplicationInfo, false);
        mNotificationsPreference = new NotificationsPreference(mContext, mAppEntry);

        mNotificationsPreference.refresh();

        assertTrue(mNotificationsPreference.isEnabled());
    }

    @Test
    public void testNotificationsPreference_systemAppNotificationsDisabledNotInRes() {
        setSystemApp(true);
        setNotificationsEnabledForPackage(PACKAGE_NAME, false);
        setNotificationsUnblockableByImportanceLocked(mApplicationInfo, false);
        mNotificationsPreference = new NotificationsPreference(mContext, mAppEntry);

        mNotificationsPreference.refresh();

        assertTrue(mNotificationsPreference.isEnabled());
    }

    @Test
    public void testNotificationsPreference_notSystemAppNotificationsEDisabledInRes() {
        setSystemApp(false);
        setNotificationsEnabledForPackage(PACKAGE_NAME, false);
        setNotificationsUnblockableByImportanceLocked(mApplicationInfo, true);
        mNotificationsPreference = new NotificationsPreference(mContext, mAppEntry);

        mNotificationsPreference.refresh();

        assertTrue(mNotificationsPreference.isEnabled());
    }

    @Test
    public void testNotificationsPreference_notSystemAppNotificationsDisabledNotInRes() {
        setSystemApp(false);
        setNotificationsEnabledForPackage(PACKAGE_NAME, false);
        setNotificationsUnblockableByImportanceLocked(mApplicationInfo, false);
        mNotificationsPreference = new NotificationsPreference(mContext, mAppEntry);

        mNotificationsPreference.refresh();

        assertTrue(mNotificationsPreference.isEnabled());
    }

    @Test
    public void testNotificationsPreference_systemAppNotificationsDisabledInRes() {
        setSystemApp(true);
        setNotificationsEnabledForPackage(PACKAGE_NAME, false);
        setNotificationsUnblockableByImportanceLocked(mApplicationInfo, true);
        mNotificationsPreference = new NotificationsPreference(mContext, mAppEntry);

        mNotificationsPreference.refresh();

        assertTrue(mNotificationsPreference.isEnabled());
    }

    private void setSystemApp(boolean systemApp) {
        ShadowUtils.setSystemPackage(systemApp);
    }

    private void setNotificationsEnabledForPackage(String packageName,
            boolean notificationsEnabled) {
        try {
            when(mNotificationManager.areNotificationsEnabledForPackage(eq(packageName),
                    anyInt())).thenReturn(notificationsEnabled);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void setNotificationsUnblockableByImportanceLocked(ApplicationInfo applicationInfo,
            boolean isUnblockable) {
        try {
            when(mNotificationManager.isImportanceLocked(applicationInfo.packageName,
                    applicationInfo.uid)).thenReturn(isUnblockable);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}

