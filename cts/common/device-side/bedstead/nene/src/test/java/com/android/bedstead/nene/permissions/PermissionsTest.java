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

package com.android.bedstead.nene.permissions;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.MANAGE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION_CODES.P;
import static android.os.Build.VERSION_CODES.Q;
import static android.os.Build.VERSION_CODES.R;
import static android.os.Build.VERSION_CODES.S;

import static com.android.bedstead.nene.appops.CommonAppOps.OPSTR_FINE_LOCATION;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveAppOp;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasAppOp;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.ShellCommandUtils;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class PermissionsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String APP_OP = OPSTR_FINE_LOCATION;
    private static final String PERMISSION_HELD_BY_SHELL =
            "android.permission.INTERACT_ACROSS_PROFILES";
    private static final String DIFFERENT_PERMISSION_HELD_BY_SHELL =
            "android.permission.INTERACT_ACROSS_USERS_FULL";
    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final String NON_EXISTING_PERMISSION = "permissionWhichDoesNotExist";

    // We expect these permissions are listed in the Manifest
    private static final String INSTALL_PERMISSION = "android.permission.CHANGE_WIFI_STATE";
    private static final String DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S =
            "android.permission.INTERNET";

    @Test
    @RequireSdkVersion(min = Q, reason = "drop shell permissions only available on Q+")
    public void default_permissionIsNotGranted() {
        ShellCommandUtils.uiAutomation().dropShellPermissionIdentity();

        assertThat(sContext.checkSelfPermission(PERMISSION_HELD_BY_SHELL))
                .isEqualTo(PERMISSION_DENIED);
    }

    @Test
    @RequireSdkVersion(min = Q, reason = "adopt shell permissions only available on Q+")
    public void withPermission_shellPermission_permissionIsGranted() {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(PERMISSION_HELD_BY_SHELL)) {
            assertThat(sContext.checkSelfPermission(PERMISSION_HELD_BY_SHELL))
                    .isEqualTo(PERMISSION_GRANTED);
        }
    }

    @Test
    @RequireSdkVersion(max = P, reason = "adopt shell permissions only available on Q+")
    public void withoutPermission_alreadyGranted_androidPreQ_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.permissions().withoutPermission(
                        DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S));
    }

    @Test
    @RequireSdkVersion(min = Q, reason = "adopt shell permissions only available on Q+")
    public void withoutPermission_permissionIsNotGranted() {
        try (PermissionContext p = TestApis.permissions().withPermission(PERMISSION_HELD_BY_SHELL);
             PermissionContext p2 = TestApis.permissions().withoutPermission(
                     PERMISSION_HELD_BY_SHELL)) {

            assertThat(sContext.checkSelfPermission(PERMISSION_HELD_BY_SHELL))
                    .isEqualTo(PERMISSION_DENIED);
        }
    }

    @Test
    @RequireSdkVersion(min = Q, reason = "adopt shell permissions only available on Q+")
    public void autoclose_withoutPermission_permissionIsGrantedAgain() {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(PERMISSION_HELD_BY_SHELL)) {
            try (PermissionContext p2 =
                         TestApis.permissions().withoutPermission(PERMISSION_HELD_BY_SHELL)) {
                // Intentionally empty as we're testing that autoclosing restores the permission
            }

            assertThat(sContext.checkSelfPermission(PERMISSION_HELD_BY_SHELL))
                    .isEqualTo(PERMISSION_GRANTED);
        }
    }

    @Test
    @RequireSdkVersion(max = P, reason = "adopt shell permissions only available on Q+")
    public void withoutPermission_installPermission_androidPreQ_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.permissions().withoutPermission(INSTALL_PERMISSION));
    }

    @Test
    @RequireSdkVersion(min = Q, max = R,
            reason = "adopt shell permissions only available on Q+ - after S - all available"
                    + " permissions are held by shell")
    public void withoutPermission_permissionIsAlreadyGrantedInInstrumentedApp_permissionIsNotGranted() {
        try (PermissionContext p =
                    TestApis.permissions().withoutPermission(
                            DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S)) {
            assertThat(
                    sContext.checkSelfPermission(DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S))
                    .isEqualTo(PERMISSION_DENIED);
        }
    }

    @Test
    @RequireSdkVersion(max = P, reason = "adopt shell permissions only available on Q+")
    public void withoutPermission_permissionIsAlreadyGrantedInInstrumentedApp_androidPreQ_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.permissions().withoutPermission(
                        DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S));
    }

    @Test
    public void withPermission_permissionIsAlreadyGrantedInInstrumentedApp_permissionIsGranted() {
        try (PermissionContext p =
                    TestApis.permissions().withPermission(
                            DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S)) {
            assertThat(
                    sContext.checkSelfPermission(DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S))
                    .isEqualTo(PERMISSION_GRANTED);
        }
    }

    @Test
    public void withPermission_nonExistingPermission_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.permissions().withPermission(NON_EXISTING_PERMISSION));
    }

    @Test
    public void withoutPermission_nonExistingPermission_doesNotThrowException() {
        try (PermissionContext p =
                     TestApis.permissions().withoutPermission(NON_EXISTING_PERMISSION)) {
            // Intentionally empty
        }
    }

    @Test
    @RequireSdkVersion(min = R)
    public void withPermissionAndWithoutPermission_bothApplied() {
        try (PermissionContext p = TestApis.permissions().withPermission(PERMISSION_HELD_BY_SHELL)
                .withoutPermission(DIFFERENT_PERMISSION_HELD_BY_SHELL)) {

            assertThat(sContext.checkSelfPermission(PERMISSION_HELD_BY_SHELL))
                    .isEqualTo(PERMISSION_GRANTED);
            assertThat(sContext.checkSelfPermission(DIFFERENT_PERMISSION_HELD_BY_SHELL))
                    .isEqualTo(PERMISSION_DENIED);
        }
    }

    @Test
    @RequireSdkVersion(min = R)
    public void withoutPermissionAndWithPermission_bothApplied() {
        try (PermissionContext p = TestApis.permissions()
                .withoutPermission(DIFFERENT_PERMISSION_HELD_BY_SHELL)
                .withPermission(PERMISSION_HELD_BY_SHELL)) {

            assertThat(sContext.checkSelfPermission(PERMISSION_HELD_BY_SHELL))
                    .isEqualTo(PERMISSION_GRANTED);
            assertThat(sContext.checkSelfPermission(DIFFERENT_PERMISSION_HELD_BY_SHELL))
                    .isEqualTo(PERMISSION_DENIED);
        }
    }

    @Test
    public void withPermissionAndWithoutPermission_contradictoryPermissions_throwsException() {
        assertThrows(NeneException.class, () -> TestApis.permissions()
                .withPermission(PERMISSION_HELD_BY_SHELL)
                .withoutPermission(PERMISSION_HELD_BY_SHELL));
    }

    @Test
    public void withoutPermissionAndWithPermission_contradictoryPermissions_throwsException() {
        assertThrows(NeneException.class, () -> TestApis.permissions()
                .withoutPermission(PERMISSION_HELD_BY_SHELL)
                .withPermission(PERMISSION_HELD_BY_SHELL));
    }

    @Test
    @RequireSdkVersion(min = S, reason = "restoring shell permissions only available on S+")
    public void withoutPermission_androidSAndAbove_restoresPreviousPermissionContext() {
        ShellCommandUtils.uiAutomation().adoptShellPermissionIdentity(PERMISSION_HELD_BY_SHELL);

        try {
            PermissionContext p =
                    TestApis.permissions()
                            .withoutPermission(PERMISSION_HELD_BY_SHELL);
            p.close();

            assertThat(sContext.checkSelfPermission(PERMISSION_HELD_BY_SHELL))
                    .isEqualTo(PERMISSION_GRANTED);
        } finally {
            ShellCommandUtils.uiAutomation().dropShellPermissionIdentity();
        }
    }

    // TODO(scottjonathan): Once we can install the testapp without granting all runtime
    //  permissions, add a test that this works pre-Q

    @Test
    @RequireSdkVersion(min = R, max = R)
    public void withPermissionOnVersion_onVersion_hasPermission() {
        try (PermissionContext p =
                     TestApis.permissions().withPermissionOnVersion(R, MANAGE_EXTERNAL_STORAGE)) {
            assertThat(sContext.checkSelfPermission(MANAGE_EXTERNAL_STORAGE))
                    .isEqualTo(PERMISSION_GRANTED);
        }
    }

    @Test
    @RequireSdkVersion(min = S)
    public void withPermissionOnVersion_notOnVersion_doesNotHavePermission() {
        try (PermissionContext p =
                     TestApis.permissions().withPermissionOnVersion(R, MANAGE_EXTERNAL_STORAGE)) {
            assertThat(sContext.checkSelfPermission(MANAGE_EXTERNAL_STORAGE))
                    .isNotEqualTo(PERMISSION_GRANTED);
        }
    }

    @Test
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    public void hasPermission_permissionIsGranted_returnsTrue() {
        assertThat(TestApis.permissions().hasPermission(INTERACT_ACROSS_USERS_FULL)).isTrue();
    }

    @Test
    @EnsureDoesNotHavePermission(INTERACT_ACROSS_USERS_FULL)
    public void hasPermission_permissionIsNotGranted_returnsFalse() {
        assertThat(TestApis.permissions().hasPermission(INTERACT_ACROSS_USERS_FULL)).isFalse();
    }

    @Test
    public void withAppOp_appOpIsGranted() {
        try (PermissionContext p = TestApis.permissions().withAppOp(APP_OP)) {
            assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isTrue();
        }
    }

    @Test
    public void withoutAppOp_appOpIsNotGranted() {
        try (PermissionContext p = TestApis.permissions().withoutAppOp(APP_OP)) {
            assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isFalse();
        }
    }

    @Test
    public void withAppOpAndPermission_hasBoth() {
        try (PermissionContext p = TestApis.permissions().withAppOp(APP_OP)
                .withPermission(INTERACT_ACROSS_USERS_FULL)) {
            assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isTrue();
            assertThat(TestApis.permissions().hasPermission(INTERACT_ACROSS_USERS_FULL)).isTrue();
        }
    }

    @Test
    public void withoutAppOpAndWithPermission_hasPermissionButNotAppOp() {
        try (PermissionContext p = TestApis.permissions().withoutAppOp(APP_OP)
                .withPermission(INTERACT_ACROSS_USERS_FULL)) {
            assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isFalse();
            assertThat(TestApis.permissions().hasPermission(INTERACT_ACROSS_USERS_FULL)).isTrue();
        }
    }

    @Test
    @EnsureHasAppOp(APP_OP)
    public void hasAppOpAllowed_appOpAllowed_isTrue() {
        assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isTrue();
    }

    @Test
    @EnsureDoesNotHaveAppOp(APP_OP)
    public void hasAppOpAllowed_appOpNotAllowed_isFalse() {
        assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isFalse();
    }

}
