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
import android.content.Context;
import android.widget.TextView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.CarSetupWizardLayoutHelper;
import com.android.managedprovisioning.common.InitializeLayoutConsumerHandler;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.auto.value.AutoValue;

/**
 * AAOS implementation of LandingActivityBridge with Car SUW layout and only device owner support,
 * i.e. BYOD use case is not suppoerted yet.
 */
@AutoValue
abstract class CarLandingActivityBridgeImpl implements LandingActivityBridge {

    abstract Utils getUtils();
    abstract ProvisioningParams getParams();
    abstract LandingActivityBridgeCallbacks getBridgeCallbacks();
    abstract AccessibilityContextMenuMaker getAccessibilityContextMenuMaker();
    abstract InitializeLayoutConsumerHandler getInitializeLayoutParamsConsumer();
    abstract CarSetupWizardLayoutHelper getLayoutHelper();

    @Override
    public void initiateUi(Activity activity) {
        int headerResId = R.string.brand_screen_header;
        int mainLayoutId = CarSetupWizardLayoutHelper.MAIN_LAYOUT_RES_ID;
        int subLayoutId = R.layout.empty_layout;

        getInitializeLayoutParamsConsumer()
                .initializeLayoutParams(mainLayoutId, headerResId);

        CarSetupWizardLayoutHelper layoutHelper = getLayoutHelper();
        layoutHelper.setBaseLayout(subLayoutId, /* isDoubleColumnAllowed= */ false);

        layoutHelper.setupPrimaryToolbarButton(R.string.next,
                v -> getBridgeCallbacks().onNextButtonClicked());

        TextView descriptionTitle = activity.findViewById(R.id.description_title);
        descriptionTitle.setText(headerResId);
        TextView description = activity.findViewById(R.id.description);
        CustomizationParams customizationParams =
                CustomizationParams.createInstance(getParams(), activity, getUtils());
        handleSupportUrl(activity, customizationParams, description, getUtils());
    }

    private void handleSupportUrl(
            Context context,
            CustomizationParams customizationParams,
            TextView info,
            Utils utils) {
        String deviceProvider = context.getString(R.string.organization_admin);
        String contactDeviceProvider =
                context.getString(R.string.contact_device_provider, deviceProvider);
        utils.handleSupportUrl(
                context,
                customizationParams,
                getAccessibilityContextMenuMaker(),
                info,
                deviceProvider,
                contactDeviceProvider, intent ->
                        getBridgeCallbacks().onContactYourItAdminClicked(intent));
    }

    static Builder builder() {
        return new AutoValue_CarLandingActivityBridgeImpl.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setUtils(Utils utils);
        abstract Builder setParams(ProvisioningParams params);
        abstract Builder setInitializeLayoutParamsConsumer(
                InitializeLayoutConsumerHandler initializeLayoutParamsConsumer);
        abstract Builder setLayoutHelper(CarSetupWizardLayoutHelper layoutHelper);
        abstract Builder setBridgeCallbacks(LandingActivityBridgeCallbacks callbacks);
        abstract Builder setAccessibilityContextMenuMaker(
                AccessibilityContextMenuMaker accessibilityContextMenuMaker);
        abstract CarLandingActivityBridgeImpl build();
    }
}
