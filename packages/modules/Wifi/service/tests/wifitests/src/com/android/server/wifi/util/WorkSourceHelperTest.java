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
package com.android.server.wifi.util;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Process;
import android.os.WorkSource;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiBaseTest;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link WorkSourceHelper}. */
@RunWith(JUnit4.class)
@SmallTest
public class WorkSourceHelperTest extends WifiBaseTest {
    private static final int TEST_UID_1 = 456456;
    private static final int TEST_UID_2 = 456456;
    private static final String TEST_PACKAGE_1 = "com.android.test.1";
    private static final String TEST_PACKAGE_2 = "com.android.test.2";

    @Mock private WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock private ActivityManager mActivityManager;
    @Mock private PackageManager mPackageManager;
    @Mock private Resources mResources;

    @Before public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mActivityManager.getPackageImportance(null)).thenReturn(IMPORTANCE_CACHED);
        when(mPackageManager.getApplicationInfoAsUser(any(), anyInt(), any())).thenReturn(
                Mockito.mock(ApplicationInfo.class));
    }

    @Test
    public void testGetRequestorWsPriority() throws Exception {
        // PRIORITY_INTERNAL
        WorkSource ws = new WorkSource(Process.WIFI_UID, "com.android.wifi");
        WorkSourceHelper wsHelper = new WorkSourceHelper(
                ws, mWifiPermissionsUtil, mActivityManager, mPackageManager, mResources);
        assertEquals(wsHelper.getRequestorWsPriority(), WorkSourceHelper.PRIORITY_INTERNAL);

        // PRIORITY_BG
        ws.add(new WorkSource(TEST_UID_1, TEST_PACKAGE_1));
        ws.add(new WorkSource(TEST_UID_2, TEST_PACKAGE_2));
        when(mActivityManager.getPackageImportance(TEST_PACKAGE_1)).thenReturn(IMPORTANCE_CACHED);
        when(mActivityManager.getPackageImportance(TEST_PACKAGE_2)).thenReturn(IMPORTANCE_CACHED);
        assertEquals(WorkSourceHelper.PRIORITY_BG, wsHelper.getRequestorWsPriority());

        // PRIORITY_FG_SERVICE
        when(mActivityManager.getPackageImportance(TEST_PACKAGE_1))
                .thenReturn(IMPORTANCE_FOREGROUND_SERVICE);
        assertEquals(WorkSourceHelper.PRIORITY_FG_SERVICE, wsHelper.getRequestorWsPriority());

        // PRIORITY_FG_APP
        when(mActivityManager.getPackageImportance(TEST_PACKAGE_1))
                .thenReturn(IMPORTANCE_FOREGROUND);
        assertEquals(WorkSourceHelper.PRIORITY_FG_APP, wsHelper.getRequestorWsPriority());

        // PRIORITY_FG_APP with "treat as foreground" package
        when(mActivityManager.getPackageImportance(TEST_PACKAGE_1))
                .thenReturn(IMPORTANCE_BACKGROUND);
        when(mResources.getStringArray(
                R.array.config_wifiInterfacePriorityTreatAsForegroundList)).thenReturn(
                new String[]{TEST_PACKAGE_2});
        assertEquals(WorkSourceHelper.PRIORITY_FG_APP, wsHelper.getRequestorWsPriority());

        // PRIORITY_SYSTEM
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        when(mPackageManager.getApplicationInfoAsUser(any(), anyInt(), any())).thenReturn(appInfo);
        assertEquals(WorkSourceHelper.PRIORITY_SYSTEM, wsHelper.getRequestorWsPriority());

        // PRIORITY_PRIVILEGED
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(TEST_UID_1)).thenReturn(true);
        assertEquals(WorkSourceHelper.PRIORITY_PRIVILEGED, wsHelper.getRequestorWsPriority());
    }
}
