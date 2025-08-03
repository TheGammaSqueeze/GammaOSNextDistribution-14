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

package com.android.devicelockcontroller.common;

import androidx.annotation.IntDef;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Constants being used by more than one class in the Device Lock application. */
public final class DeviceLockConstants {
    /** Device reset count down minute when mandatory provision fails */
    public static final int MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE = 2;
    /** Device reset count down minute when non-mandatory provision fails */
    public static final int NON_MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE = 30;

    // Constants related to unique device identifiers.
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            DeviceIdType.DEVICE_ID_TYPE_UNSPECIFIED,
            DeviceIdType.DEVICE_ID_TYPE_IMEI,
            DeviceIdType.DEVICE_ID_TYPE_MEID,
    })
    public @interface DeviceIdType {
        // The device id type is unspecified
        int DEVICE_ID_TYPE_UNSPECIFIED = -1;
        // The device id is a IMEI
        int DEVICE_ID_TYPE_IMEI = 0;
        // The device id is a MEID
        int DEVICE_ID_TYPE_MEID = 1;
    }

    @DeviceIdType
    private static final int LAST_DEVICE_ID_TYPE = DeviceIdType.DEVICE_ID_TYPE_MEID;
    public static final int TOTAL_DEVICE_ID_TYPES = LAST_DEVICE_ID_TYPE + 1;

    // Constants related to unique device identifiers.
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            STATUS_UNSPECIFIED,
            RETRY_CHECK_IN,
            READY_FOR_PROVISION,
            STOP_CHECK_IN,
    })
    public @interface DeviceCheckInStatus {
    }

    public static final int STATUS_UNSPECIFIED = 0;
    public static final int RETRY_CHECK_IN = 1;
    public static final int READY_FOR_PROVISION = 2;
    public static final int STOP_CHECK_IN = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            REASON_UNSPECIFIED,
            USER_DEFERRED_DEVICE_PROVISIONING,
    })
    public @interface PauseDeviceProvisioningReason {
    }

    public static final int REASON_UNSPECIFIED = 0;
    public static final int USER_DEFERRED_DEVICE_PROVISIONING = 1;

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            ProvisioningType.TYPE_UNDEFINED,
            ProvisioningType.TYPE_FINANCED,
            ProvisioningType.TYPE_SUBSIDY,
    })
    public @interface ProvisioningType {
        int TYPE_UNDEFINED = 0;
        int TYPE_FINANCED = 1;
        int TYPE_SUBSIDY = 2;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            ProvisionFailureReason.UNKNOWN_REASON,
            ProvisionFailureReason.PLAY_TASK_UNAVAILABLE,
            ProvisionFailureReason.PLAY_INSTALLATION_FAILED,
            ProvisionFailureReason.COUNTRY_INFO_UNAVAILABLE,
            ProvisionFailureReason.NOT_IN_ELIGIBLE_COUNTRY,
            ProvisionFailureReason.POLICY_ENFORCEMENT_FAILED,
    })
    public @interface ProvisionFailureReason {
        int UNKNOWN_REASON = 0;
        int PLAY_TASK_UNAVAILABLE = 1;
        int PLAY_INSTALLATION_FAILED = 2;
        int COUNTRY_INFO_UNAVAILABLE = 3;
        int NOT_IN_ELIGIBLE_COUNTRY = 4;
        int POLICY_ENFORCEMENT_FAILED = 5;
        int DEADLINE_PASSED = 6;
    }

    public static final String EXTRA_KIOSK_PACKAGE =
            "com.android.devicelockcontroller.KIOSK_PACKAGE";
    public static final String EXTRA_KIOSK_DISABLE_OUTGOING_CALLS =
            "com.android.devicelockcontroller.KIOSK_DISABLE_OUTGOING_CALLS";
    /**
     * Used to control if notifications are enabled in lock task mode. The default value is false.
     *
     * @see android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_NOTIFICATIONS
     */
    public static final String EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE =
            "com.android.devicelockcontroller.KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE";
    public static final String EXTRA_KIOSK_ALLOWLIST =
            "com.android.devicelockcontroller.KIOSK_ALLOWLIST";
    public static final String EXTRA_PROVISIONING_TYPE =
            "com.android.devicelockcontroller.PROVISIONING_TYPE";
    public static final String EXTRA_MANDATORY_PROVISION =
            "com.android.devicelockcontroller.MANDATORY_PROVISION";
    public static final String EXTRA_KIOSK_APP_PROVIDER_NAME =
            "com.android.devicelockcontroller.KIOSK_APP_PROVIDER_NAME";
    public static final String EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES =
            "com.android.devicelockcontroller.DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES";

    public static final String EXTRA_TERMS_AND_CONDITIONS_URL =
            "com.android.devicelockcontroller.TERMS_AND_CONDITIONS_URL";

    public static final String EXTRA_SUPPORT_URL = "com.android.devicelockcontroller.SUPPORT_URL";

    public static final String ACTION_START_DEVICE_FINANCING_PROVISIONING =
            "com.android.devicelockcontroller.action.START_DEVICE_FINANCING_PROVISIONING";

    public static final String ACTION_START_DEVICE_SUBSIDY_PROVISIONING =
            "com.android.devicelockcontroller.action.START_DEVICE_SUBSIDY_PROVISIONING";

    /** Definitions for device provision states. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    DeviceProvisionState.PROVISION_STATE_UNSPECIFIED,
                    DeviceProvisionState.PROVISION_STATE_RETRY,
                    DeviceProvisionState.PROVISION_STATE_DISMISSIBLE_UI,
                    DeviceProvisionState.PROVISION_STATE_PERSISTENT_UI,
                    DeviceProvisionState.PROVISION_STATE_FACTORY_RESET,
                    DeviceProvisionState.PROVISION_STATE_SUCCESS,
            })
    public @interface DeviceProvisionState {
        /** The provision state of the device is unspecified */
        int PROVISION_STATE_UNSPECIFIED = 0;
        /** The Device need retry to provision the device. */
        int PROVISION_STATE_RETRY = 1;
        /**
         * The Device need inform the user that there has been an issue with device provisioning.
         * The user can dismiss this.
         */
        int PROVISION_STATE_DISMISSIBLE_UI = 2;
        /**
         * The Device need inform the user that there has been an issue with device provisioning.
         * The user cannot dismiss this.
         */
        int PROVISION_STATE_PERSISTENT_UI = 3;
        /** The Device need factory reset because device provisioning could not be done. */
        int PROVISION_STATE_FACTORY_RESET = 4;
        /** Device provisioning was a success. */
        int PROVISION_STATE_SUCCESS = 5;
    }

    /** Prevent instantiation. */
    private DeviceLockConstants() {
    }
}
