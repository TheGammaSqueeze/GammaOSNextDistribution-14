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

package com.android.devicelockcontroller.storage;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_ALLOWLIST;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_APP_PROVIDER_NAME;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_DISABLE_OUTGOING_CALLS;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_MANDATORY_PROVISION;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_PROVISIONING_TYPE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_SUPPORT_URL;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_TERMS_AND_CONDITIONS_URL;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType.TYPE_FINANCED;

import android.os.Bundle;

import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType;

import java.util.ArrayList;

abstract class AbstractSetupParametersTestBase {
    protected static final String KIOSK_PACKAGE = "package";
    protected static final String KIOSK_OVERRIDE_PACKAGE = "override.package";
    protected static final boolean DISABLE_OUTGOING_CALLS = true;
    protected static final boolean ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE = true;
    protected static final String KIOSK_ALLOWLIST_PACKAGE_0 = "package.name.0";
    protected static final String KIOSK_ALLOWLIST_PACKAGE_1 = "package.name.1";
    @ProvisioningType
    protected static final int PROVISIONING_TYPE = TYPE_FINANCED;
    protected static final boolean MANDATORY_PROVISION = true;
    protected static final boolean DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES = true;
    protected static final String TERMS_AND_CONDITIONS_URL = "https://www.example.com/terms";
    protected static final String SUPPORT_URL = "https://www.example.com/support";
    protected static final String KIOSK_APP_PROVIDER_NAME = "test name";

    protected static Bundle createParamsBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, KIOSK_PACKAGE);
        bundle.putBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS, DISABLE_OUTGOING_CALLS);
        bundle.putBoolean(
                EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE,
                ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE);
        final ArrayList<String> actualKioskAllowlist = new ArrayList<>();
        actualKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_0);
        actualKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_1);
        bundle.putStringArrayList(EXTRA_KIOSK_ALLOWLIST, actualKioskAllowlist);
        bundle.putInt(EXTRA_PROVISIONING_TYPE, PROVISIONING_TYPE);
        bundle.putBoolean(EXTRA_MANDATORY_PROVISION, MANDATORY_PROVISION);
        bundle.putString(EXTRA_KIOSK_APP_PROVIDER_NAME, KIOSK_APP_PROVIDER_NAME);
        bundle.putBoolean(EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES,
                DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES);
        bundle.putString(EXTRA_TERMS_AND_CONDITIONS_URL, TERMS_AND_CONDITIONS_URL);
        bundle.putString(EXTRA_SUPPORT_URL, SUPPORT_URL);
        return bundle;
    }

    protected static Bundle createParamsOverrideBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, KIOSK_OVERRIDE_PACKAGE);
        return bundle;
    }
}
