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

package com.android.bedstead.nene.devicepolicy;

/** Device Policy helper methods common to host and device. */
public class CommonDevicePolicy {
    CommonDevicePolicy() {

    }

    /** See {@code DevicePolicyManager#DELEGATION_CERT_INSTALL}. */
    public static final String DELEGATION_CERT_INSTALL = "delegation-cert-install";

    /** See {@code DevicePolicyManager#DELEGATION_APP_RESTRICTIONS}. */
    public static final String DELEGATION_APP_RESTRICTIONS = "delegation-app-restrictions";

    /** See {@code DevicePolicyManager#DELEGATION_BLOCK_UNINSTALL}. */
    public static final String DELEGATION_BLOCK_UNINSTALL = "delegation-block-uninstall";

    /** See {@code DevicePolicyManager#DELEGATION_PERMISSION_GRANT}. */
    public static final String DELEGATION_PERMISSION_GRANT = "delegation-permission-grant";

    /** See {@code DevicePolicyManager#DELEGATION_PACKAGE_ACCESS}. */
    public static final String DELEGATION_PACKAGE_ACCESS = "delegation-package-access";

    /** See {@code DevicePolicyManager#DELEGATION_ENABLE_SYSTEM_APP}. */
    public static final String DELEGATION_ENABLE_SYSTEM_APP = "delegation-enable-system-app";

    /** See {@code DevicePolicyManager#DELEGATION_INSTALL_EXISTING_PACKAGE}. */
    public static final String DELEGATION_INSTALL_EXISTING_PACKAGE =
            "delegation-install-existing-package";

    /** See {@code DevicePolicyManager#DELEGATION_KEEP_UNINSTALLED_PACKAGES}. */
    public static final String DELEGATION_KEEP_UNINSTALLED_PACKAGES =
            "delegation-keep-uninstalled-packages";

    /** See {@code DevicePolicyManager#DELEGATION_NETWORK_LOGGING}. */
    public static final String DELEGATION_NETWORK_LOGGING = "delegation-network-logging";

    /** See {@code DevicePolicyManager#DELEGATION_CERT_SELECTION}. */
    public static final String DELEGATION_CERT_SELECTION = "delegation-cert-selection";

    /** See {@code DevicePolicyManager#DELEGATION_SECURITY_LOGGING}. */
    public static final String DELEGATION_SECURITY_LOGGING = "delegation-security-logging";

    /** See {@code android.app.admin.DevicePolicyManager#DevicePolicyOperation}. */
    public enum DevicePolicyOperation {
        OPERATION_NONE(-1),
        OPERATION_LOCK_NOW(1),
        OPERATION_SWITCH_USER(2),
        OPERATION_START_USER_IN_BACKGROUND(3),
        OPERATION_STOP_USER(4),
        OPERATION_CREATE_AND_MANAGE_USER(5),
        OPERATION_REMOVE_USER(6),
        OPERATION_REBOOT(7),
        OPERATION_WIPE_DATA(8),
        OPERATION_LOGOUT_USER(9),
        OPERATION_SET_USER_RESTRICTION(10),
        OPERATION_SET_SYSTEM_SETTING(11),
        OPERATION_SET_KEYGUARD_DISABLED(12),
        OPERATION_SET_STATUS_BAR_DISABLED(13),
        OPERATION_SET_SYSTEM_UPDATE_POLICY(14),
        OPERATION_SET_APPLICATION_HIDDEN(15),
        OPERATION_SET_APPLICATION_RESTRICTIONS(16),
        OPERATION_SET_KEEP_UNINSTALLED_PACKAGES(17),
        OPERATION_SET_LOCK_TASK_FEATURES(18),
        OPERATION_SET_LOCK_TASK_PACKAGES(19),
        OPERATION_SET_PACKAGES_SUSPENDED(20),
        OPERATION_SET_TRUST_AGENT_CONFIGURATION(21),
        OPERATION_SET_USER_CONTROL_DISABLED_PACKAGES(22),
        OPERATION_CLEAR_APPLICATION_USER_DATA(23),
        OPERATION_INSTALL_CA_CERT(24),
        OPERATION_INSTALL_KEY_PAIR(25),
        OPERATION_INSTALL_SYSTEM_UPDATE(26),
        OPERATION_REMOVE_ACTIVE_ADMIN(27),
        OPERATION_REMOVE_KEY_PAIR(28),
        OPERATION_REQUEST_BUGREPORT(29),
        OPERATION_SET_ALWAYS_ON_VPN_PACKAGE(30),
        OPERATION_SET_CAMERA_DISABLED(31),
        OPERATION_SET_FACTORY_RESET_PROTECTION_POLICY(32),
        OPERATION_SET_GLOBAL_PRIVATE_DNS(33),
        OPERATION_SET_LOGOUT_ENABLED(34),
        OPERATION_SET_MASTER_VOLUME_MUTED(35),
        OPERATION_SET_OVERRIDE_APNS_ENABLED(36),
        OPERATION_SET_PERMISSION_GRANT_STATE(37),
        OPERATION_SET_PERMISSION_POLICY(38),
        OPERATION_SET_RESTRICTIONS_PROVIDER(39),
        OPERATION_UNINSTALL_CA_CERT(40);

        private final int mValue;

        DevicePolicyOperation(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /** See {@code android.app.admin.DevicePolicyManager#OperationSafetyReason}. */
    public enum OperationSafetyReason {
        OPERATION_SAFETY_REASON_NONE(-1),
        OPERATION_SAFETY_REASON_DRIVING_DISTRACTION(1);

        private final int mValue;

        OperationSafetyReason(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }
}
