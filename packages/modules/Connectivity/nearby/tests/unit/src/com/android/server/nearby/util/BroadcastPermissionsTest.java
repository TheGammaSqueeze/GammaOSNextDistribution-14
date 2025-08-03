/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.nearby.util;

import static android.Manifest.permission.BLUETOOTH_ADVERTISE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.server.nearby.util.permissions.BroadcastPermissions.PERMISSION_BLUETOOTH_ADVERTISE;
import static com.android.server.nearby.util.permissions.BroadcastPermissions.PERMISSION_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;

import com.android.server.nearby.util.identity.CallerIdentity;
import com.android.server.nearby.util.permissions.BroadcastPermissions;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/**
 * Unit test for {@link BroadcastPermissions}
 */
public final class BroadcastPermissionsTest {

    private static final String PACKAGE_NAME = "android.nearby.test";
    private static final int UID = 1234;
    private static final int PID = 5678;
    private CallerIdentity mCallerIdentity;

    @Mock private Context mMockContext;

    @Before
    public void setup() {
        initMocks(this);
        mCallerIdentity = CallerIdentity
                .forTest(UID, PID, PACKAGE_NAME, /* attributionTag= */ null);
    }

    @Test
    public void test_checkCallerBroadcastPermission_granted() {
        when(mMockContext.checkPermission(BLUETOOTH_ADVERTISE, PID, UID))
                .thenReturn(PERMISSION_GRANTED);

        assertThat(BroadcastPermissions
                .checkCallerBroadcastPermission(mMockContext, mCallerIdentity))
                .isTrue();
    }

    @Test
    public void test_checkCallerBroadcastPermission_deniedPermission() {
        when(mMockContext.checkPermission(BLUETOOTH_ADVERTISE, PID, UID))
                .thenReturn(PERMISSION_DENIED);

        assertThat(BroadcastPermissions
                .checkCallerBroadcastPermission(mMockContext, mCallerIdentity))
                .isFalse();
    }

    @Test
    public void test_getPermissionLevel_none() {
        when(mMockContext.checkPermission(BLUETOOTH_ADVERTISE, PID, UID))
                .thenReturn(PERMISSION_DENIED);

        assertThat(BroadcastPermissions.getPermissionLevel(mMockContext, UID, PID))
                .isEqualTo(PERMISSION_NONE);
    }

    @Test
    public void test_getPermissionLevel_advertising() {
        when(mMockContext.checkPermission(BLUETOOTH_ADVERTISE, PID, UID))
                .thenReturn(PERMISSION_GRANTED);

        assertThat(BroadcastPermissions.getPermissionLevel(mMockContext, UID, PID))
                .isEqualTo(PERMISSION_BLUETOOTH_ADVERTISE);
    }

    @Test
    public void test_enforceBroadcastPermission() {
        BroadcastPermissions.enforceBroadcastPermission(mMockContext, mCallerIdentity);
    }
}
