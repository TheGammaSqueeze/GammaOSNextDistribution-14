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

package com.android.managedprovisioning.provisioning;

import android.app.Activity;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.CarSetupWizardLayoutHelper;
import com.android.managedprovisioning.common.InitializeLayoutConsumerHandler;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.auto.value.AutoValue;

/**
 * AAOS implementation of ResetAndReturnDeviceActivityBridge with Car SUW layout and only
 * device owner support, i.e. BYOD use case is not suppoerted yet.
 */
@AutoValue
abstract class CarResetAndReturnDeviceActivityBridgeImpl
        implements ResetAndReturnDeviceActivityBridge {

    abstract Utils getUtils();

    abstract ProvisioningParams getParams();

    abstract ResetAndReturnDeviceActivityBridgeCallback getBridgeCallback();

    abstract InitializeLayoutConsumerHandler getInitializeLayoutParamsConsumer();

    @Override
    public void initiateUi(Activity activity) {
        // Set the base layout
        CarSetupWizardLayoutHelper layoutHelper = new CarSetupWizardLayoutHelper(activity);
        layoutHelper.setBaseLayout(
                /* mainLayoutId= */ CarSetupWizardLayoutHelper.MAIN_LAYOUT_RES_ID,
                /* columnLayoutId= */ R.layout.single_column_left_layout,
                /* subLayoutId= */ R.layout.empty_layout);
        layoutHelper.setHeaderText(
                R.string.fully_managed_device_provisioning_return_device_title,
                R.string.fully_managed_device_provisioning_return_device_subheader);
        layoutHelper.setupLogo(com.android.internal.R.drawable.stat_sys_certificate_info);
        layoutHelper.setupPrimaryToolbarButton(
                R.string.fully_managed_device_reset_and_return_button,
                v -> getBridgeCallback().onResetButtonClicked());
    }

    static Builder builder() {
        return new AutoValue_CarResetAndReturnDeviceActivityBridgeImpl.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setUtils(Utils utils);

        abstract Builder setParams(ProvisioningParams params);

        abstract Builder setInitializeLayoutParamsConsumer(
                InitializeLayoutConsumerHandler initializeLayoutParamsConsumer);

        abstract Builder setBridgeCallback(ResetAndReturnDeviceActivityBridgeCallback callback);

        abstract CarResetAndReturnDeviceActivityBridgeImpl build();
    }
}
