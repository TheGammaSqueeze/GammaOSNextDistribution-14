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
package com.android.cts.deviceowner;

import static android.app.admin.DevicePolicyManager.OPERATION_CREATE_AND_MANAGE_USER;
import static android.app.admin.DevicePolicyManager.OPERATION_REBOOT;
import static android.app.admin.DevicePolicyManager.OPERATION_REMOVE_USER;
import static android.app.admin.DevicePolicyManager.OPERATION_REQUEST_BUGREPORT;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_APPLICATION_HIDDEN;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_APPLICATION_RESTRICTIONS;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_CAMERA_DISABLED;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_FACTORY_RESET_PROTECTION_POLICY;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_GLOBAL_PRIVATE_DNS;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_KEEP_UNINSTALLED_PACKAGES;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_KEYGUARD_DISABLED;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_LOCK_TASK_FEATURES;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_LOCK_TASK_PACKAGES;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_LOGOUT_ENABLED;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_OVERRIDE_APNS_ENABLED;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_PACKAGES_SUSPENDED;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_STATUS_BAR_DISABLED;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_SYSTEM_SETTING;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_SYSTEM_UPDATE_POLICY;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_TRUST_AGENT_CONFIGURATION;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_USER_CONTROL_DISABLED_PACKAGES;
import static android.app.admin.DevicePolicyManager.OPERATION_START_USER_IN_BACKGROUND;
import static android.app.admin.DevicePolicyManager.OPERATION_STOP_USER;
import static android.app.admin.DevicePolicyManager.OPERATION_SWITCH_USER;
import static android.app.admin.DevicePolicyManager.OPERATION_UNINSTALL_CA_CERT;
import static android.app.admin.DevicePolicyManager.OPERATION_WIPE_DATA;

import android.app.admin.DevicePolicyManager;
import android.app.admin.FactoryResetProtectionPolicy;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.cts.devicepolicy.DevicePolicySafetyCheckerIntegrationTester;
import com.android.internal.util.ArrayUtils;

import java.util.Arrays;
import java.util.List;

// TODO(b/174859111): move to automotive-only section
/**
 * Tests that DPM calls fail when determined by the
 * {@link android.app.admin.DevicePolicySafetyChecker}.
 */
public final class DevicePolicySafetyCheckerIntegrationTest extends BaseDeviceOwnerTest {
    private static final int NO_FLAGS = 0;
    private static final UserHandle USER_HANDLE = UserHandle.of(42);
    public static final String TEST_PACKAGE = BasicAdminReceiver.class.getPackage().getName();
    public static final ComponentName TEST_COMPONENT = new ComponentName(
            TEST_PACKAGE, BasicAdminReceiver.class.getName());
    public static final List<String> TEST_ACCOUNTS = Arrays.asList("Account 1");
    public static final List<String> TEST_PACKAGES = Arrays.asList(TEST_PACKAGE);
    private static final String TEST_CA =
            "-----BEGIN CERTIFICATE-----\n"
            + "MIICVzCCAgGgAwIBAgIJAMvnLHnnfO/IMA0GCSqGSIb3DQEBBQUAMIGGMQswCQYD\n"
            + "VQQGEwJJTjELMAkGA1UECAwCQVAxDDAKBgNVBAcMA0hZRDEVMBMGA1UECgwMSU1G\n"
            + "TCBQVlQgTFREMRAwDgYDVQQLDAdJTUZMIE9VMRIwEAYDVQQDDAlJTUZMLklORk8x\n"
            + "HzAdBgkqhkiG9w0BCQEWEHJhbWVzaEBpbWZsLmluZm8wHhcNMTMwODI4MDk0NDA5\n"
            + "WhcNMjMwODI2MDk0NDA5WjCBhjELMAkGA1UEBhMCSU4xCzAJBgNVBAgMAkFQMQww\n"
            + "CgYDVQQHDANIWUQxFTATBgNVBAoMDElNRkwgUFZUIExURDEQMA4GA1UECwwHSU1G\n"
            + "TCBPVTESMBAGA1UEAwwJSU1GTC5JTkZPMR8wHQYJKoZIhvcNAQkBFhByYW1lc2hA\n"
            + "aW1mbC5pbmZvMFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAJ738cbTQlNIO7O6nV/f\n"
            + "DJTMvWbPkyHYX8CQ7yXiAzEiZ5bzKJjDJmpRAkUrVinljKns2l6C4++l/5A7pFOO\n"
            + "33kCAwEAAaNQME4wHQYDVR0OBBYEFOdbZP7LaMbgeZYPuds2CeSonmYxMB8GA1Ud\n"
            + "IwQYMBaAFOdbZP7LaMbgeZYPuds2CeSonmYxMAwGA1UdEwQFMAMBAf8wDQYJKoZI\n"
            + "hvcNAQEFBQADQQBdrk6J9koyylMtl/zRfiMAc2zgeC825fgP6421NTxs1rjLs1HG\n"
            + "VcUyQ1/e7WQgOaBHi9TefUJi+4PSVSluOXon\n"
            + "-----END CERTIFICATE-----";
    private final DevicePolicySafetyCheckerIntegrationTester mTester =
            new DevicePolicySafetyCheckerIntegrationTester(/* forDeviceOwner= */ true) {

        @Override
        protected int[] getSafetyAwareOperations() {
            int[] operations = new int [] {
                    OPERATION_CREATE_AND_MANAGE_USER,
                    // TODO(b/175245108) Add test for this operation; testing
                    // dpm.installSystemUpdate will require upload a test system update file.
                    // OPERATION_INSTALL_SYSTEM_UPDATE,
                    OPERATION_REBOOT,
                    OPERATION_REMOVE_USER,
                    OPERATION_REQUEST_BUGREPORT,
                    OPERATION_SET_APPLICATION_HIDDEN,
                    OPERATION_SET_APPLICATION_RESTRICTIONS,
                    OPERATION_SET_CAMERA_DISABLED,
                    OPERATION_SET_GLOBAL_PRIVATE_DNS,
                    OPERATION_SET_KEEP_UNINSTALLED_PACKAGES,
                    OPERATION_SET_KEYGUARD_DISABLED,
                    OPERATION_SET_LOCK_TASK_FEATURES,
                    OPERATION_SET_LOCK_TASK_PACKAGES,
                    OPERATION_SET_LOGOUT_ENABLED,
                    OPERATION_SET_PACKAGES_SUSPENDED,
                    OPERATION_SET_STATUS_BAR_DISABLED,
                    OPERATION_SET_SYSTEM_SETTING,
                    OPERATION_SET_SYSTEM_UPDATE_POLICY,
                    OPERATION_SET_USER_CONTROL_DISABLED_PACKAGES,
                    OPERATION_START_USER_IN_BACKGROUND,
                    OPERATION_STOP_USER,
                    OPERATION_SWITCH_USER,
                    OPERATION_UNINSTALL_CA_CERT,
                    OPERATION_WIPE_DATA
            };

            if (mHasTelephonyFeature) {
                operations = ArrayUtils.appendInt(operations, OPERATION_SET_OVERRIDE_APNS_ENABLED);
            }
            if (mHasSecureLockScreen) {
                operations = ArrayUtils.appendInt(operations,
                        OPERATION_SET_TRUST_AGENT_CONFIGURATION);
            }
            if (mDevicePolicyManager.isFactoryResetProtectionPolicySupported()) {
                operations = ArrayUtils.appendInt(operations,
                        OPERATION_SET_FACTORY_RESET_PROTECTION_POLICY);
            }

            return operations;
        }

        @Override
        protected int[] getOverloadedSafetyAwareOperations() {
            return new int [] {
                OPERATION_WIPE_DATA
            };
        }

        @Override
        protected void runOperation(DevicePolicyManager dpm, ComponentName admin, int operation,
                boolean overloaded) {
            switch (operation) {
                case OPERATION_CREATE_AND_MANAGE_USER:
                    dpm.createAndManageUser(admin, /* name= */ null, admin, /* adminExtras= */ null,
                            NO_FLAGS);
                    break;
                case OPERATION_REBOOT:
                    dpm.reboot(admin);
                    break;
                case OPERATION_REMOVE_USER:
                    dpm.removeUser(admin, USER_HANDLE);
                    break;
                case OPERATION_REQUEST_BUGREPORT:
                    dpm.requestBugreport(admin);
                    break;
                case OPERATION_SET_APPLICATION_HIDDEN:
                    dpm.setApplicationHidden(admin, TEST_PACKAGE, /* hidden= */true);
                    break;
                case OPERATION_SET_APPLICATION_RESTRICTIONS:
                    dpm.setApplicationRestrictions(admin, TEST_PACKAGE, new Bundle());
                    break;
                case OPERATION_SET_CAMERA_DISABLED:
                    dpm.setCameraDisabled(admin, /* disabled= */ true);
                    break;
                case OPERATION_SET_FACTORY_RESET_PROTECTION_POLICY:
                    dpm.setFactoryResetProtectionPolicy(admin,
                            new FactoryResetProtectionPolicy.Builder()
                                    .setFactoryResetProtectionAccounts(TEST_ACCOUNTS)
                                    .setFactoryResetProtectionEnabled(false)
                                    .build());
                    break;
                case OPERATION_SET_GLOBAL_PRIVATE_DNS:
                    dpm.setGlobalPrivateDnsModeOpportunistic(admin);
                    break;
                case OPERATION_SET_KEEP_UNINSTALLED_PACKAGES:
                    dpm.setKeepUninstalledPackages(admin, TEST_PACKAGES);
                    break;
                case OPERATION_SET_KEYGUARD_DISABLED:
                    dpm.setKeyguardDisabled(admin, true);
                    break;
                case OPERATION_SET_LOCK_TASK_FEATURES:
                    dpm.setLockTaskFeatures(admin, NO_FLAGS);
                    break;
                case OPERATION_SET_LOCK_TASK_PACKAGES:
                    dpm.setLockTaskPackages(admin, new String[] { TEST_PACKAGE });
                    break;
                case OPERATION_SET_LOGOUT_ENABLED:
                    dpm.setLogoutEnabled(admin, /* enabled */ true);
                    break;
                case OPERATION_SET_OVERRIDE_APNS_ENABLED:
                    dpm.setOverrideApnsEnabled(admin, /* enabled */ true);
                    break;
                case OPERATION_SET_PACKAGES_SUSPENDED:
                    dpm.setPackagesSuspended(admin,  new String[] { TEST_PACKAGE },
                            /* suspend= */ true);
                    break;
                case OPERATION_SET_STATUS_BAR_DISABLED:
                    dpm.setStatusBarDisabled(admin, true);
                    break;
                case OPERATION_SET_SYSTEM_SETTING:
                    dpm.setSystemSetting(admin, "TestSetting", "0");
                    break;
                case OPERATION_SET_SYSTEM_UPDATE_POLICY:
                    dpm.setSystemUpdatePolicy(admin, null);
                    break;
                case OPERATION_SET_TRUST_AGENT_CONFIGURATION:
                    dpm.setTrustAgentConfiguration(admin, TEST_COMPONENT,
                            /* configuration= */ null);
                    break;
                case OPERATION_SET_USER_CONTROL_DISABLED_PACKAGES:
                    dpm.setUserControlDisabledPackages(admin, TEST_PACKAGES);
                    break;
                case OPERATION_START_USER_IN_BACKGROUND:
                    dpm.startUserInBackground(admin, USER_HANDLE);
                    break;
                case OPERATION_STOP_USER:
                    dpm.stopUser(admin, USER_HANDLE);
                    break;
                case OPERATION_SWITCH_USER:
                    dpm.switchUser(admin, USER_HANDLE);
                    break;
                case OPERATION_UNINSTALL_CA_CERT:
                    dpm.uninstallCaCert(admin, TEST_CA.getBytes());
                    break;
                case OPERATION_WIPE_DATA:
                    if (overloaded) {
                        dpm.wipeData(NO_FLAGS,
                                /* reason= */ "DevicePolicySafetyCheckerIntegrationTest");
                    } else {
                        dpm.wipeData(NO_FLAGS);
                    }
                    break;
                default:
                    throwUnsupportedOperationException(operation, overloaded);
            }
        }
    };

    /**
     * Tests that all safety-aware operations are properly implemented.
     */
    public void testAllOperations() {
        mTester.testAllOperations(mDevicePolicyManager, getWho());
    }

    /**
     * Tests {@link android.app.admin.UnsafeStateException} properties.
     */
    public void testUnsafeStateException() {
        mTester.testUnsafeStateException(mDevicePolicyManager, getWho());
    }

    /**
     * Tests {@link android.app.admin.DeviceAdminReceiver#onOperationSafetyStateChanged()}.
     */
    public void testOnOperationSafetyStateChanged() {
        mTester.testOnOperationSafetyStateChanged(mContext, mDevicePolicyManager);
    }
}
