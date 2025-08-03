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

import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.server.nearby.util.permissions.DiscoveryPermissions.PERMISSION_BLUETOOTH_SCAN;
import static com.android.server.nearby.util.permissions.DiscoveryPermissions.PERMISSION_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.AppOpsManager;
import android.content.Context;

import com.android.server.nearby.util.identity.CallerIdentity;
import com.android.server.nearby.util.permissions.DiscoveryPermissions;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/**
 * Unit test for {@link DiscoveryPermissions}
 */
public final class DiscoveryPermissionsTest {

    private static final String PACKAGE_NAME = "android.nearby.test";
    private static final int UID = 1234;
    private static final int PID = 5678;
    private CallerIdentity mCallerIdentity;

    @Mock
    private Context mMockContext;
    @Mock private AppOpsManager mMockAppOps;

    @Before
    public void setup() {
        initMocks(this);
        mCallerIdentity = CallerIdentity
                .forTest(UID, PID, PACKAGE_NAME, /* attributionTag= */ null);
    }

    @Test
    public void test_enforceCallerDiscoveryPermission_exception() {
        when(mMockContext.checkPermission(BLUETOOTH_SCAN, PID, UID)).thenReturn(PERMISSION_DENIED);

        assertThrows(SecurityException.class,
                () -> DiscoveryPermissions
                        .enforceDiscoveryPermission(mMockContext, mCallerIdentity));
    }

    @Test
    public void test_checkCallerDiscoveryPermission_granted() {
        when(mMockContext.checkPermission(BLUETOOTH_SCAN, PID, UID)).thenReturn(PERMISSION_GRANTED);

        assertThat(DiscoveryPermissions
                .checkCallerDiscoveryPermission(mMockContext, mCallerIdentity))
                .isTrue();
    }

    @Test
    public void test_checkCallerDiscoveryPermission_denied() {
        when(mMockContext.checkPermission(BLUETOOTH_SCAN, PID, UID)).thenReturn(PERMISSION_DENIED);

        assertThat(DiscoveryPermissions
                .checkCallerDiscoveryPermission(mMockContext, mCallerIdentity))
                .isFalse();
    }

    @Test
    public void test_checkNoteOpPermission_granted() {
        when(mMockAppOps.noteOp(DiscoveryPermissions.OPSTR_BLUETOOTH_SCAN, UID, PACKAGE_NAME,
                null, null)).thenReturn(AppOpsManager.MODE_ALLOWED);

        assertThat(DiscoveryPermissions
                .noteDiscoveryResultDelivery(mMockAppOps, mCallerIdentity))
                .isTrue();
    }

    @Test
    public void test_checkNoteOpPermission_denied() {
        when(mMockAppOps.noteOp(DiscoveryPermissions.OPSTR_BLUETOOTH_SCAN, UID, PACKAGE_NAME,
                null, null)).thenReturn(AppOpsManager.MODE_ERRORED);

        assertThat(DiscoveryPermissions
                .noteDiscoveryResultDelivery(mMockAppOps, mCallerIdentity))
                .isFalse();
    }

    @Test
    public void test_getPermissionLevel_none() {
        when(mMockContext.checkPermission(BLUETOOTH_SCAN, PID, UID)).thenReturn(PERMISSION_DENIED);

        assertThat(DiscoveryPermissions
                .getPermissionLevel(mMockContext, UID, PID))
                .isEqualTo(PERMISSION_NONE);
    }

    @Test
    public void test_getPermissionLevel_scan() {
        when(mMockContext.checkPermission(BLUETOOTH_SCAN, PID, UID))
                .thenReturn(PERMISSION_GRANTED);

        assertThat(DiscoveryPermissions
                .getPermissionLevel(mMockContext, UID, PID)).isEqualTo(PERMISSION_BLUETOOTH_SCAN);
    }
}
