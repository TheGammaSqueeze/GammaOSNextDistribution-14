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
package com.android.internal.car;

import static android.app.admin.DevicePolicyManager.operationToString;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.verify;

import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManager.DevicePolicyOperation;
import android.app.admin.DevicePolicyManagerLiteInternal;
import android.app.admin.DevicePolicySafetyChecker;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class CarDevicePolicySafetyCheckerTest {

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private DevicePolicySafetyChecker mCheckerImplementation;

    @Mock
    private DevicePolicyManagerLiteInternal mDpmi;

    private CarDevicePolicySafetyChecker mChecker;

    private final @DevicePolicyOperation int mOperation;
    private final boolean mSafe;

    @Before
    public void setFixtures() {
        mChecker = new CarDevicePolicySafetyChecker(mCheckerImplementation, mDpmi);
    }

    @Parameterized.Parameters
    public static Collection<?> packageManagers() {
        return Arrays.asList(new Object[][] {
                // unsafe operations
                { DevicePolicyManager.OPERATION_CLEAR_APPLICATION_USER_DATA, false },
                { DevicePolicyManager.OPERATION_LOGOUT_USER, false },
                { DevicePolicyManager.OPERATION_REBOOT, false },
                { DevicePolicyManager.OPERATION_REQUEST_BUGREPORT, false },
                { DevicePolicyManager.OPERATION_SET_APPLICATION_HIDDEN, false },
                { DevicePolicyManager.OPERATION_SET_APPLICATION_RESTRICTIONS, false },
                { DevicePolicyManager.OPERATION_SET_KEYGUARD_DISABLED, false },
                { DevicePolicyManager.OPERATION_SET_PACKAGES_SUSPENDED, false },
                { DevicePolicyManager.OPERATION_SET_STATUS_BAR_DISABLED, false },
                { DevicePolicyManager.OPERATION_SET_SYSTEM_SETTING, false },
                { DevicePolicyManager.OPERATION_SWITCH_USER, false },
                { DevicePolicyManager.OPERATION_SET_LOCK_TASK_FEATURES, false },
                { DevicePolicyManager.OPERATION_SET_LOCK_TASK_PACKAGES, false },

                // safe operations
                { DevicePolicyManager.OPERATION_WIPE_DATA, true }, // Safe because it'll be delayed
                { DevicePolicyManager.OPERATION_CREATE_AND_MANAGE_USER, true },
                { DevicePolicyManager.OPERATION_INSTALL_CA_CERT, true },
                { DevicePolicyManager.OPERATION_INSTALL_KEY_PAIR, true },
                { DevicePolicyManager.OPERATION_INSTALL_SYSTEM_UPDATE, true },
                { DevicePolicyManager.OPERATION_LOCK_NOW, true },
                { DevicePolicyManager.OPERATION_REMOVE_ACTIVE_ADMIN, true },
                { DevicePolicyManager.OPERATION_REMOVE_KEY_PAIR, true },
                { DevicePolicyManager.OPERATION_REMOVE_USER, true },
                { DevicePolicyManager.OPERATION_SET_ALWAYS_ON_VPN_PACKAGE, true },
                { DevicePolicyManager.OPERATION_SET_CAMERA_DISABLED, true },
                { DevicePolicyManager.OPERATION_SET_FACTORY_RESET_PROTECTION_POLICY, true },
                { DevicePolicyManager.OPERATION_SET_GLOBAL_PRIVATE_DNS, true },
                { DevicePolicyManager.OPERATION_SET_KEEP_UNINSTALLED_PACKAGES, true },
                { DevicePolicyManager.OPERATION_SET_LOGOUT_ENABLED, true },
                { DevicePolicyManager.OPERATION_SET_MASTER_VOLUME_MUTED, true },
                { DevicePolicyManager.OPERATION_SET_OVERRIDE_APNS_ENABLED, true },
                { DevicePolicyManager.OPERATION_SET_PERMISSION_GRANT_STATE, true },
                { DevicePolicyManager.OPERATION_SET_PERMISSION_POLICY, true },
                { DevicePolicyManager.OPERATION_SET_RESTRICTIONS_PROVIDER, true },
                { DevicePolicyManager.OPERATION_SET_SYSTEM_UPDATE_POLICY, true },
                { DevicePolicyManager.OPERATION_SET_TRUST_AGENT_CONFIGURATION, true },
                { DevicePolicyManager.OPERATION_SET_USER_CONTROL_DISABLED_PACKAGES, true },
                { DevicePolicyManager.OPERATION_SET_USER_RESTRICTION, true },
                { DevicePolicyManager.OPERATION_START_USER_IN_BACKGROUND, true },
                { DevicePolicyManager.OPERATION_STOP_USER, true },
                { DevicePolicyManager.OPERATION_UNINSTALL_CA_CERT, true }
        });
    }

    public CarDevicePolicySafetyCheckerTest(@DevicePolicyOperation int operation, boolean safe) {
        mOperation = operation;
        mSafe = safe;
    }

    @Test
    public void testSafe() throws Exception {
        boolean isSafe = true;
        mChecker.setSafe(isSafe);

        assertWithMessage("safety of %s when car is safe", operationToString(mOperation))
                .that(mChecker.isDevicePolicyOperationSafe(mOperation)).isTrue();

        verifySafetyNofiticationSend(isSafe);
    }

    @Test
    public void testUnsafe() throws Exception {
        boolean isSafe = false;
        mChecker.setSafe(isSafe);

        if (mSafe) {
            assertWithMessage("safety of %s EVEN when car isn't safe",
                    operationToString(mOperation))
                            .that(mChecker.isDevicePolicyOperationSafe(mOperation)).isTrue();
        } else {
            assertWithMessage("safety of %s when car isn't safe",
                    operationToString(mOperation))
                            .that(mChecker.isDevicePolicyOperationSafe(mOperation)).isFalse();
        }

        verifySafetyNofiticationSend(isSafe);
    }

    private void verifySafetyNofiticationSend(boolean isSafe) {
        verify(mDpmi).notifyUnsafeOperationStateChanged(mCheckerImplementation,
                DevicePolicyManager.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION, isSafe);
    }
}
