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

package com.android.sdksandbox.inprocess;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(JUnit4.class)
public class SdkSandboxInProcessPermissionsTest {

    @Before
    public void setUp() {
        assertWithMessage(Process.myUid() + " is not a SdkSandbox uid")
                .that(Process.isSdkSandbox())
                .isTrue();
    }

    @Test
    public void testAllowedPermissions() {
        final ArrayList<String> allowedPermissions =
                new ArrayList<>(
                        Arrays.asList(
                                Manifest.permission.INTERNET,
                                Manifest.permission.ACCESS_NETWORK_STATE,
                                Manifest.permission.READ_BASIC_PHONE_STATE));
        final Context ctx = getInstrumentation().getContext();

        for (String permission : allowedPermissions) {
            assertThat(
                            ctx.checkPermission(
                                    permission,
                                    /*pid=*/ -1 /*invalid pid*/,
                                    Process.toSdkSandboxUid(19999)))
                    .isEqualTo(PackageManager.PERMISSION_GRANTED);
        }
    }

    @Test
    public void testNotAllowedPermissions() {
        final ArrayList<String> permissionsNotGranted =
                new ArrayList<>(
                        Arrays.asList(
                                Manifest.permission.BIND_INPUT_METHOD,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS,
                                Manifest.permission.READ_SMS,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.WRITE_SECURE_SETTINGS,
                                Manifest.permission.MODIFY_PHONE_STATE,
                                Manifest.permission.POST_NOTIFICATIONS,
                                Manifest.permission.READ_PHONE_NUMBERS,
                                Manifest.permission.GET_ACCOUNTS,
                                Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE,
                                Manifest.permission.NEARBY_WIFI_DEVICES,
                                Manifest.permission.SEND_SMS,
                                Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.CLEAR_APP_USER_DATA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_CALL_LOG,
                                Manifest.permission.REQUEST_INSTALL_PACKAGES,
                                Manifest.permission.BIND_DEVICE_ADMIN,
                                Manifest.permission.READ_LOGS,
                                Manifest.permission.UPDATE_DEVICE_STATS,
                                Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.RECEIVE_MMS,
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.ACCESS_INSTANT_APPS,
                                Manifest.permission.RECEIVE_WAP_PUSH,
                                Manifest.permission.CAMERA,
                                Manifest.permission.DUMP,
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.SYSTEM_ALERT_WINDOW,
                                Manifest.permission.BROADCAST_WAP_PUSH,
                                Manifest.permission.HDMI_CEC,
                                Manifest.permission.BROADCAST_SMS,
                                Manifest.permission.PACKAGE_USAGE_STATS,
                                Manifest.permission.LOCAL_MAC_ADDRESS,
                                Manifest.permission.WRITE_CALENDAR,
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.ACCESS_MEDIA_LOCATION,
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.BIND_JOB_SERVICE,
                                Manifest.permission.ACCESS_WIFI_STATE));
        final Context ctx = getInstrumentation().getContext();

        for (String permission : permissionsNotGranted) {
            assertThat(
                            ctx.checkPermission(
                                    permission,
                                    /*pid=*/ -1 /*invalid pid*/,
                                    Process.toSdkSandboxUid(19999)))
                    .isEqualTo(PackageManager.PERMISSION_DENIED);
        }
    }
}
