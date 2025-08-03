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

package com.android.devicelockcontroller.provision.grpc;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_ALLOWLIST;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_APP_PROVIDER_NAME;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_DISABLE_OUTGOING_CALLS;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_SUPPORT_URL;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_TERMS_AND_CONDITIONS_URL;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * A data structure class that contains information necessary to device provisioning for DeviceLock
 * program.
 */
public final class ProvisioningConfiguration {

    // The package name of the kiosk app, e.g. "com.foo.bar".
    private final String mKioskAppProviderName;
    // The name of the provider of the kiosk app, e.g. "Foo Bar Inc".

    private final String mKioskAppPackageName;
    // The list of apps that a user can use when the device is locked.
    private final List<String> mKioskAppAllowlistPackages;
    // Whether the user can make phone calls when the device is locked.
    private final boolean mKioskAppEnableOutgoingCalls;

    // Whether notifications are shown to the user when the device is locked.
    private final boolean mKioskAppEnableEnableNotifications;

    // Whether installing application from unknown sources is disallowed on this device once
    // provisioned.
    private final boolean mDisallowInstallingFromUnknownSources;

    // The URL to the Terms and Conditions of the partner for enrolling in a
    // Device Lock program.
    private final String mTermsAndConditionsUrl;

    // The URL to the support page the user can use to get help.
    private final String mSupportUrl;

    public ProvisioningConfiguration(
            String kioskAppProviderName,
            String kioskAppPackageName,
            List<String> kioskAppAllowlistPackages,
            boolean kioskAppEnableOutgoingCalls, boolean kioskAppEnableEnableNotifications,
            boolean disallowInstallingFromUnknownSources, String termsAndConditionsUrl,
            String supportUrl) {
        mKioskAppProviderName = kioskAppProviderName;
        mKioskAppPackageName = kioskAppPackageName;
        mKioskAppAllowlistPackages = kioskAppAllowlistPackages;
        mKioskAppEnableOutgoingCalls = kioskAppEnableOutgoingCalls;
        mKioskAppEnableEnableNotifications = kioskAppEnableEnableNotifications;
        mDisallowInstallingFromUnknownSources = disallowInstallingFromUnknownSources;
        mTermsAndConditionsUrl = termsAndConditionsUrl;
        mSupportUrl = supportUrl;
    }

    /**
     * Create a bundle containing the provisioning information.
     */
    public Bundle toBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, mKioskAppPackageName);
        bundle.putBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS, !mKioskAppEnableOutgoingCalls);
        bundle.putBoolean(EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE,
                mKioskAppEnableEnableNotifications);
        bundle.putStringArrayList(EXTRA_KIOSK_ALLOWLIST,
                new ArrayList<>(mKioskAppAllowlistPackages));
        bundle.putString(EXTRA_KIOSK_APP_PROVIDER_NAME, mKioskAppProviderName);
        bundle.putBoolean(EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES,
                mDisallowInstallingFromUnknownSources);
        bundle.putString(EXTRA_TERMS_AND_CONDITIONS_URL, mTermsAndConditionsUrl);
        bundle.putString(EXTRA_SUPPORT_URL, mSupportUrl);
        return bundle;
    }
}
