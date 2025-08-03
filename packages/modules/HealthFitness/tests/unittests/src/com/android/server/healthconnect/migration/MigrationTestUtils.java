/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.healthconnect.migration;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.health.connect.HealthConnectManager;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;

import java.util.ArrayList;
import java.util.List;

/** Common methods and variables used by migration unit tests. */
public class MigrationTestUtils {
    public static final String MOCK_CONFIGURED_PACKAGE = "com.configured.app";
    static final String MOCK_UNCONFIGURED_PACKAGE_ONE = "com.unconfigured.app";
    static final String MOCK_UNCONFIGURED_PACKAGE_TWO = "com.unconfigured.apptwo";
    static final String MOCK_QUERIED_BROADCAST_RECEIVER_ONE = ".SampleReceiverOne";
    static final String MOCK_QUERIED_BROADCAST_RECEIVER_TWO = ".SampleReceiverTwo";
    static final String MOCK_CERTIFICATE_ONE =
            "962F386525EE206D5ED146A3433411042E7F91D0C267C9DD08BA4F1F5E354076";
    static final String MOCK_CERTIFICATE_TWO =
            "962F386525EE206D5ED146A3433411042E7F91D0C267C9DD08BA4F1F5E354000";
    static final String MOCK_CERTIFICATE_THREE =
            "962F386525EE206D5ED146A3433411042E7F91D0C267C9DD08BA4F1F5E350000";
    static final String[] PERMISSIONS_TO_CHECK =
            new String[] {Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA};

    static long getTimeoutPeriodBuffer() {
        return HealthConnectDeviceConfigManager.getInitialisedInstance().getExecutionTimeBuffer()
                * 2;
    }

    static List<ResolveInfo> createResolveInfoList(
            boolean nullActivityInfo, String packageName, String... broadcastReceivers) {
        List<ResolveInfo> resolveInfoArray = new ArrayList<ResolveInfo>();
        for (String broadcastReceiver : broadcastReceivers) {
            ResolveInfo resolveInfo = new ResolveInfo();
            if (!nullActivityInfo) {
                resolveInfo.activityInfo = new ActivityInfo();
                resolveInfo.activityInfo.packageName = packageName;
                resolveInfo.activityInfo.name = packageName + broadcastReceiver;
            }
            resolveInfoArray.add(resolveInfo);
        }
        return resolveInfoArray;
    }

    public static void setResolveActivityResult(ResolveInfo result, PackageManager packageManager) {
        setResolveActivityResult(result, packageManager, PackageManager.MATCH_ALL);
    }

    static void setResolveActivityResult(
            ResolveInfo result, PackageManager packageManager, int flags) {
        when(packageManager.resolveActivity(
                        argThat(
                                intent ->
                                        (HealthConnectManager.ACTION_SHOW_MIGRATION_INFO.equals(
                                                intent.getAction()))),
                        argThat(flag -> (flag.getValue() == flags))))
                .thenReturn(result);
    }
}
