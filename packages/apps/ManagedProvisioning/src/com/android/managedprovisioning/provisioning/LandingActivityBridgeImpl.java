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

import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_PERSONALLY_OWNED;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.InitializeLayoutConsumerHandler;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupdesign.GlifLayout;
import com.google.android.setupdesign.util.DeviceHelper;
import com.google.auto.value.AutoValue;

@AutoValue
abstract class LandingActivityBridgeImpl implements LandingActivityBridge {

    abstract Utils getUtils();
    abstract ProvisioningParams getParams();
    abstract LandingActivityBridgeCallbacks getBridgeCallbacks();
    abstract AccessibilityContextMenuMaker getAccessibilityContextMenuMaker();
    abstract InitializeLayoutConsumerHandler getInitializeLayoutParamsConsumer();

    @Override
    public void initiateUi(Activity activity) {
        int headerResId = R.string.brand_screen_header;
        CharSequence deviceName = DeviceHelper.getDeviceName(activity.getApplicationContext());
        String title = activity.getString(R.string.setup_device_progress, deviceName);

        if (shouldShowAccountManagementDisclaimer(
                getParams().initiatorRequestedProvisioningModes, getUtils())) {
            headerResId = R.string.account_management_disclaimer_header;
        }

        CustomizationParams customizationParams =
                CustomizationParams.createInstance(getParams(), activity, getUtils());
        getInitializeLayoutParamsConsumer()
                .initializeLayoutParams(R.layout.landing_screen, headerResId);
        activity.setTitle(title);

        setupSubtitleText(activity, getParams(), customizationParams);

        GlifLayout layout = activity.findViewById(R.id.setup_wizard_layout);
        Utils.addNextButton(layout, v -> getBridgeCallbacks().onNextButtonClicked());
    }

    private void setupSubtitleText(Activity activity, ProvisioningParams params,
            CustomizationParams customizationParams) {
        TextView info = activity.findViewById(
                com.google.android.setupdesign.R.id.sud_layout_subtitle);
        info.setVisibility(View.VISIBLE);
        if (shouldShowAccountManagementDisclaimer(
                params.initiatorRequestedProvisioningModes, getUtils())) {
            info.setText(R.string.account_management_disclaimer_subheader);
        } else {
            handleSupportUrl(activity, customizationParams, info, getUtils());
        }
    }

    private boolean shouldShowAccountManagementDisclaimer(int provisioningModes, Utils  utils) {
        return utils.containsBinaryFlags(provisioningModes, FLAG_SUPPORTED_MODES_PERSONALLY_OWNED);
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
        return new AutoValue_LandingActivityBridgeImpl.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setUtils(Utils utils);
        abstract Builder setParams(ProvisioningParams params);
        abstract Builder setInitializeLayoutParamsConsumer(
                InitializeLayoutConsumerHandler initializeLayoutParamsConsumer);
        abstract Builder setBridgeCallbacks(LandingActivityBridgeCallbacks callbacks);
        abstract Builder setAccessibilityContextMenuMaker(
                AccessibilityContextMenuMaker accessibilityContextMenuMaker);
        abstract LandingActivityBridgeImpl build();
    }
}
