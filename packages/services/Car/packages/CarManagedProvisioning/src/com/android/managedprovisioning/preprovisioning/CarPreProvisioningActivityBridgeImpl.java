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

import android.app.Activity;

import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.preprovisioning.consent.CarConsentUiHelper;
import com.android.managedprovisioning.preprovisioning.consent.ConsentUiHelperCallback;

/**
 * A {@link PreProvisioningActivityBridge} implementation meant for the base {@code
 * ManagedProvisioning}.
 */
public final class CarPreProvisioningActivityBridgeImpl implements PreProvisioningActivityBridge {

    private final CarConsentUiHelper mConsentUiHelper;

    CarPreProvisioningActivityBridgeImpl(
            Activity activity,
            Utils utils,
            ConsentUiHelperCallback callback,
            PreProvisioningActivityBridgeCallbacks bridgeCallbacks) {

        mConsentUiHelper = new CarConsentUiHelper(activity, callback, utils, bridgeCallbacks);
    }

    @Override
    public void initiateUi(PreProvisioningActivityController.UiParams uiParams) {
        mConsentUiHelper.initiateUi(uiParams);
    }

    @Override
    public void onStart() {
        mConsentUiHelper.onStart();
    }

    @Override
    public void onStop() {
        mConsentUiHelper.onStop();
    }
}
