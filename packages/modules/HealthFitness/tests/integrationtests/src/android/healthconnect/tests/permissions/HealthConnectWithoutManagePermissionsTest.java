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

package android.healthconnect.tests.permissions;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.content.Context;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Integration tests for {@link HealthConnectManager} Permission-related APIs.
 *
 * <p><b>Note:</b> These tests operate while <b>not</b> holding {@link
 * HealthPermissions.MANAGE_HEALTH_PERMISSIONS}. For tests asserting that the API behaves as
 * expected for holders of the permission, please see {@link
 * android.healthconnect.tests.withmanagepermissions.HealthConnectWithManagePermissionsTest}.
 *
 * <p><b>Build/Install/Run:</b> {@code atest HealthFitnessIntegrationTests}.
 */
@RunWith(AndroidJUnit4.class)
public class HealthConnectWithoutManagePermissionsTest {
    private static final String DEFAULT_APP_PACKAGE = "android.healthconnect.test.app";
    private static final String DEFAULT_PERM = HealthPermissions.READ_ACTIVE_CALORIES_BURNED;

    private Context mContext;
    private HealthConnectManager mHealthConnectManager;

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mHealthConnectManager = mContext.getSystemService(HealthConnectManager.class);
    }

    @Test(expected = SecurityException.class)
    public void testGrantHealthPermission_noManageHealthPermissions_throwsSecurityException()
            throws Exception {
        mHealthConnectManager.grantHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        fail(
                "Expected SecurityException due to not holding"
                        + "android.permission.MANAGE_HEALTH_PERMISSIONS.");
    }

    @Test(expected = SecurityException.class)
    public void testRevokeHealthPermission_noManageHealthPermissions_throwsSecurityException()
            throws Exception {
        mHealthConnectManager.revokeHealthPermission(
                DEFAULT_APP_PACKAGE, DEFAULT_PERM, /* reason= */ null);
        fail(
                "Expected SecurityException due to not holding"
                        + "android.permission.MANAGE_HEALTH_PERMISSIONS.");
    }

    @Test(expected = SecurityException.class)
    public void testGetGrantedHealthPermissions_noManageHealthPermissions_throwsSecurityException()
            throws Exception {
        mHealthConnectManager.getGrantedHealthPermissions(DEFAULT_APP_PACKAGE);
        fail(
                "Expected SecurityException due to not holding"
                        + "android.permission.MANAGE_HEALTH_PERMISSIONS.");
    }

    @Test(expected = SecurityException.class)
    public void testRevokeAllHealthPermissions_noManageHealthPermissions_throwsSecurityException()
            throws Exception {
        mHealthConnectManager.revokeAllHealthPermissions(DEFAULT_APP_PACKAGE, /* reason= */ null);
        fail(
                "Expected SecurityException due to not holding"
                        + "android.permission.MANAGE_HEALTH_PERMISSIONS.");
    }

    @Test
    public void testGetHealthPermissionsFlags_noManageHealthPermissions_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () ->
                        mHealthConnectManager.getHealthPermissionsFlags(
                                DEFAULT_APP_PACKAGE, List.of()));
    }

    @Test
    public void
            setHealthPermissionsUserFixedFlagValue_noManageHealthPermissions_securityException() {
        assertThrows(
                SecurityException.class,
                () ->
                        mHealthConnectManager.setHealthPermissionsUserFixedFlagValue(
                                DEFAULT_APP_PACKAGE, List.of(), false));
    }
}
