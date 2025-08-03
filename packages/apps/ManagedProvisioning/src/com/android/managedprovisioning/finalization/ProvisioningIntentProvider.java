/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.finalization;

import static android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_UNSPECIFIED;

import static com.android.managedprovisioning.model.ProvisioningParams.FLOW_TYPE_ADMIN_INTEGRATED;
import static com.android.managedprovisioning.provisioning.Constants.EXTRA_PROVISIONING_COLOR_PALETTE;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ColorPaletteHelper;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.PolicyComplianceUtils;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.HashMap;

/**
 * Helper class for creating intents in finalization controller.
 */
class ProvisioningIntentProvider {
    void maybeLaunchDpc(ProvisioningParams params, int userId, Utils utils, Context context,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker,
            PolicyComplianceUtils policyComplianceUtils,
            SettingsFacade settingsFacade) {
        if (shouldLaunchPolicyCompliance(
                context, settingsFacade, params, policyComplianceUtils, utils, userId)) {
            launchPolicyComplianceDpcHandler(
                    context, params, utils, provisioningAnalyticsTracker, policyComplianceUtils);
        } else {
            launchProvisioningSuccessfulDpcHandler(
                    params, userId, utils, context, provisioningAnalyticsTracker);
        }
    }

    // TODO(b/184855881): Make manually-installed-and-started-DPC provisioning use the
    // policy compliance screen. Then here we will only check if we're doing the admin-integrated
    // flow.
    private boolean shouldLaunchPolicyCompliance(
            Context context, SettingsFacade settingsFacade, ProvisioningParams params,
            PolicyComplianceUtils policyComplianceUtils, Utils utils, int userId) {
        // If we're performing the admin-integrated flow, we've already validated that
        // the policy compliance handler exists.
        if (params.flowType == FLOW_TYPE_ADMIN_INTEGRATED) {
            return true;
        }
        if (!policyComplianceUtils.isPolicyComplianceActivityResolvableForUser(
                context, params, utils, UserHandle.of(userId))) {
            return false;
        }
        // TODO(b/184933215): Remove logic specific to legacy managed account provisioning
        if (params.provisioningTrigger != PROVISIONING_TRIGGER_UNSPECIFIED) {
            return false;
        }
        return settingsFacade.isDuringSetupWizard(context);
    }

    private void launchPolicyComplianceDpcHandler(
            Context context, ProvisioningParams params, Utils utils,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker,
            PolicyComplianceUtils policyComplianceUtils) {
        policyComplianceUtils.startPolicyComplianceActivityIfResolved(
                context, params, utils, provisioningAnalyticsTracker);
    }

    private void launchProvisioningSuccessfulDpcHandler(ProvisioningParams params, int userId,
            Utils utils, Context context,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker) {
        final Intent dpcLaunchIntent = createDpcLaunchIntent(params, context, utils);
        if (utils.canResolveIntentAsUser(context, dpcLaunchIntent, userId)) {
            context.startActivityAsUser(
                    createDpcLaunchIntent(params, context, utils), UserHandle.of(userId));
            ProvisionLogger.logd("Dpc was launched for user: " + userId);
            provisioningAnalyticsTracker.logDpcSetupStarted(context, dpcLaunchIntent.getAction());
        }
    }

    boolean canLaunchDpc(ProvisioningParams params, int userId, Utils utils, Context context) {
        final Intent dpcLaunchIntent = createDpcLaunchIntent(params, context, utils);
        return utils.canResolveIntentAsUser(context, dpcLaunchIntent, userId);
    }

    Intent createProvisioningCompleteIntent(
            @NonNull ProvisioningParams params, int userId, Utils utils, Context context) {
        Intent intent = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
        try {
            intent.setComponent(params.inferDeviceAdminComponentName(utils, context, userId));
        } catch (IllegalProvisioningArgumentException e) {
            ProvisionLogger.loge("Failed to infer the device admin component name", e);
            return null;
        }
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
        addExtrasToIntent(intent, params, context);
        return intent;
    }

    private Intent createDpcLaunchIntent(
            @NonNull ProvisioningParams params, Context context, Utils utils) {
        Intent intent = new Intent(ACTION_PROVISIONING_SUCCESSFUL);
        final String packageName = params.inferDeviceAdminPackageName();
        if (packageName == null) {
            ProvisionLogger.loge("Device admin package name is null");
            return null;
        }
        intent.setPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        addExtrasToIntent(intent, params, context);
        return intent;
    }

    private void addExtrasToIntent(Intent intent, ProvisioningParams params,
            Context context) {
        intent.putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, params.adminExtrasBundle);
        // TODO(b/192254845): Remove EXTRA_PROVISIONING_COLOR_PALETTE when framework
        //  fix available
        HashMap<Integer, Integer> colorPaletteMap =
                new ColorPaletteHelper().createColorPaletteMap(
                        context,
                        new ManagedProvisioningSharedPreferences(context));
        intent.putExtra(EXTRA_PROVISIONING_COLOR_PALETTE, colorPaletteMap);
    }
}
