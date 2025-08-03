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

package android.devicepolicy.cts;


import static android.app.admin.DevicePolicyManager.ACTION_DEVICE_FINANCING_STATE_CHANGED;
import static android.app.role.RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP;
import static android.app.role.RoleManager.ROLE_FINANCED_DEVICE_KIOSK;

import static com.android.bedstead.nene.TestApis.context;
import static com.android.bedstead.nene.TestApis.permissions;
import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.ENABLE_DEVICE_POLICY_ENGINE_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.NAMESPACE_DEVICE_POLICY_MANAGER;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.eventlib.truth.EventLogsSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.RequireFeatureFlagEnabled;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.CheckFinance;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContextImpl;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RunWith(BedsteadJUnit4.class)
public class CheckFinancedTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = context().instrumentedContext();
    private static final RoleManager sRoleManager =
            sContext.getSystemService(RoleManager.class);

    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    private static final TestApp sTestApp = sDeviceState.testApps().any();

    private String mOriginalRoleHolderPackage;

    @CanSetPolicyTest(policy = CheckFinance.class)
    public void isDeviceFinanced_isNotFinanced_returnsFalse()
            throws ExecutionException, InterruptedException {
        try (TestAppInstance testApp = sTestApp.install()) {
            clearFinancedDeviceKioskRole();
            assertThat(sDeviceState.dpc().devicePolicyManager().isDeviceFinanced()).isFalse();
        } finally {
            resetFinancedDevicesKioskRole();
        }
    }

    @CanSetPolicyTest(policy = CheckFinance.class)
    public void isDeviceFinanced_isFinanced_returnsTrue()
            throws ExecutionException, InterruptedException {
        try (TestAppInstance testApp = sTestApp.install()) {
            setUpFinancedDeviceKioskRole(testApp.packageName());

            Poll.forValue("isDeviceFinanced",
                            () -> sDeviceState.dpc().devicePolicyManager().isDeviceFinanced())
                    .errorOnFail()
                    .await();
        } finally {
            resetFinancedDevicesKioskRole();
        }
    }

    @CannotSetPolicyTest(policy = CheckFinance.class)
    public void isDeviceFinanced_callerNotPermitted_throwsSecurityException()
            throws ExecutionException, InterruptedException {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().isDeviceFinanced());
    }

    //TODO(b/273706582): Investigate why this annotation doesn't seem to be working.
//    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    public void getFinancedDeviceKioskRoleHolder_isNotFinanced_returnsNull()
            throws ExecutionException, InterruptedException {
        try {
            clearFinancedDeviceKioskRole();
            try (PermissionContextImpl p =
                         permissions().withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
                assertThat(sDevicePolicyManager.getFinancedDeviceKioskRoleHolder()).isNull();
            }
        } finally {
            resetFinancedDevicesKioskRole();
        }
    }

    //TODO(b/273706582): Re-enabled the permission annotation.
//    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    public void getFinancedDeviceKioskRoleHolder_isFinanced_returnsRoleHolder()
            throws ExecutionException, InterruptedException {
        try (TestAppInstance testApp = sTestApp.install();
             TestAppInstance testAppSystem = sTestApp.install(TestApis.users().system())) {
            setUpFinancedDeviceKioskRole(testApp.packageName());
            try (PermissionContextImpl p =
                         permissions().withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
                assertThat(sDevicePolicyManager.getFinancedDeviceKioskRoleHolder())
                        .isEqualTo(testApp.packageName());
            }
        } finally {
            resetFinancedDevicesKioskRole();
        }
    }

    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    public void getFinancedDeviceKioskRoleHolder_callerNotPermitted_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDevicePolicyManager.getFinancedDeviceKioskRoleHolder());
    }

    @CanSetPolicyTest(policy = CheckFinance.class)
    @RequireFeatureFlagEnabled(
            namespace = NAMESPACE_DEVICE_POLICY_MANAGER,
            key = ENABLE_DEVICE_POLICY_ENGINE_FLAG)
    public void deviceFinancingStateChanged_roleAdded_ReceivesBroadcast()
            throws ExecutionException, InterruptedException {
        try (TestAppInstance testApp = sTestApp.install()) {
            setUpFinancedDeviceKioskRole(testApp.packageName());

            assertThat(sDeviceState.dpc().events().broadcastReceived()
                    .whereIntent().action()
                    .isEqualTo(ACTION_DEVICE_FINANCING_STATE_CHANGED))
                    .eventOccurred();
        } finally {
            resetFinancedDevicesKioskRole();
        }
    }

    //TODO(b/273275857): Replace use of this method with an
    // EnsureDoesNotHaveFinancedDeviceKioskRoleHolder annotation.
    private void clearFinancedDeviceKioskRole()
            throws ExecutionException, InterruptedException {
        CompletableFuture<Boolean> originalRoleClearedFuture = new CompletableFuture<>();

        SystemUtil.runWithShellPermissionIdentity(() -> {
            List<String> roleHolders = sRoleManager.getRoleHolders(ROLE_FINANCED_DEVICE_KIOSK);
            if (roleHolders != null && roleHolders.size() > 0) {
                mOriginalRoleHolderPackage = roleHolders.get(0);
                sRoleManager.clearRoleHoldersAsUser(
                        ROLE_FINANCED_DEVICE_KIOSK,
                        MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                        TestApis.users().instrumented().userHandle(),
                        sContext.getMainExecutor(),
                        originalRoleClearedFuture::complete
                );

                // Wait for the future to complete.
                originalRoleClearedFuture.get();
            }
        });
    }


    //TODO(b/273275857): Replace use of this method with an EnsureHasFinancedDeviceKioskRoleHolder
    // annotation.
    private void setUpFinancedDeviceKioskRole(String packageName)
            throws ExecutionException, InterruptedException {
        clearFinancedDeviceKioskRole();

        TestApis.packages().find(packageName)
                .setAsRoleHolder(ROLE_FINANCED_DEVICE_KIOSK);
    }

    private void resetFinancedDevicesKioskRole() throws ExecutionException, InterruptedException {
        CompletableFuture<Boolean> testRoleClearedFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> roleUpdateFuture = new CompletableFuture<>();
        SystemUtil.runWithShellPermissionIdentity(() -> {
            sRoleManager.clearRoleHoldersAsUser(
                    ROLE_FINANCED_DEVICE_KIOSK,
                    MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                    TestApis.users().instrumented().userHandle(),
                    sContext.getMainExecutor(),
                    testRoleClearedFuture::complete
            );

            // Wait for the future to complete.
            testRoleClearedFuture.get();

            if (mOriginalRoleHolderPackage == null) {
                roleUpdateFuture.complete(true);
                return;
            }

            sRoleManager.addRoleHolderAsUser(
                    ROLE_FINANCED_DEVICE_KIOSK,
                    mOriginalRoleHolderPackage,
                    MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                    TestApis.users().instrumented().userHandle(),
                    sContext.getMainExecutor(),
                    roleUpdateFuture::complete
            );

            // Reset the local previous role variable.
            mOriginalRoleHolderPackage = null;
        });

        // Wait for this future in the test's thread for synchronous behavior.
        roleUpdateFuture.get();
    }
}
