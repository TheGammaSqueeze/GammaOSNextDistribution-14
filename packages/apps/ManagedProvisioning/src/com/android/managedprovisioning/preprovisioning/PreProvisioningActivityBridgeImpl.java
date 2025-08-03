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

package com.android.managedprovisioning.preprovisioning;

import static java.util.Objects.requireNonNull;

import android.app.Activity;

import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.preprovisioning.consent.ConsentUiHelper;
import com.android.managedprovisioning.preprovisioning.consent.ConsentUiHelperCallback;
import com.android.managedprovisioning.preprovisioning.consent.ConsentUiHelperFactory;

/**
 * A {@link PreProvisioningActivityBridge} implementation meant for the base {@code
 * ManagedProvisioning}.
 */
final class PreProvisioningActivityBridgeImpl implements PreProvisioningActivityBridge {
    private final ConsentUiHelper mConsentUiHelper;

    PreProvisioningActivityBridgeImpl(
            Activity activity,
            Utils utils,
            ConsentUiHelperCallback callback,
            PreProvisioningActivityBridgeCallbacks bridgeCallbacks,
            ThemeHelper themeHelper,
            String setupMetricScreenName) {
        mConsentUiHelper = ConsentUiHelperFactory.getInstance(
                requireNonNull(activity),
                requireNonNull(callback),
                requireNonNull(utils),
                requireNonNull(bridgeCallbacks),
                requireNonNull(themeHelper),
                setupMetricScreenName);
    }

    @Override
    public void initiateUi(PreProvisioningActivityController.UiParams uiParams) {
        mConsentUiHelper.initiateUi(uiParams);
    }

    @Override
    public void onStart() {}

    @Override
    public void onStop() {}
}
