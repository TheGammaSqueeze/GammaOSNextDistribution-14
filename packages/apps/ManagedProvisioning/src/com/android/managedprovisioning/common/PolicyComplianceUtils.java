/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.managedprovisioning.common;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static com.android.managedprovisioning.provisioning.Constants.EXTRA_PROVISIONING_COLOR_PALETTE;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupcompat.util.WizardManagerHelper;

import java.util.HashMap;
import java.util.function.BiConsumer;

/**
 * Class containing utility methods for starting up a DPC during Setup Wizard.
 */
public class PolicyComplianceUtils {

    /**
     * Returns whether the DPC handles the policy compliance activity on the managed user.
     */
    public boolean isPolicyComplianceActivityResolvableForManagedUser(
            Context context, ProvisioningParams params, Utils utils) {
        UserHandle userHandle = getPolicyComplianceUserHandle(context, params, utils);
        return getPolicyComplianceIntentIfResolvable(context, params, utils, userHandle) != null;
    }

    /**
     * Returns whether the DPC handles the policy compliance activity on a given user.
     */
    public boolean isPolicyComplianceActivityResolvableForUser(
            Context context, ProvisioningParams params, Utils utils, UserHandle userHandle) {
        return getPolicyComplianceIntentIfResolvable(context, params, utils, userHandle) != null;
    }

    /**
     * Starts the policy compliance activity if it can be resolved, and returns whether the
     * activity was started.
     */
    public boolean startPolicyComplianceActivityForResultIfResolved(Activity parentActivity,
            ProvisioningParams params, int requestCode, Utils utils,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker,
            TransitionHelper transitionHelper) {
        return startPolicyComplianceActivityIfResolvedInternal(parentActivity, params, utils,
                provisioningAnalyticsTracker, (Intent intent, UserHandle userHandle) -> {
                    if (parentActivity.getIntent() != null) {
                        WizardManagerHelper.copyWizardManagerExtras(
                                parentActivity.getIntent(), intent);
                    }
                    transitionHelper.startActivityForResultAsUserWithTransition(
                            parentActivity, intent, requestCode, userHandle);
                });
    }

    /**
     * Starts the policy compliance activity if it can be resolved, and returns whether the
     * activity was started.
     */
    public boolean startPolicyComplianceActivityIfResolved(Context context,
            ProvisioningParams params, Utils utils,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker) {
        return startPolicyComplianceActivityIfResolvedInternal(
                context,
                params,
                utils,
                provisioningAnalyticsTracker,
                context::startActivityAsUser);
    }

    private boolean startPolicyComplianceActivityIfResolvedInternal(
            Context context, ProvisioningParams params, Utils utils,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker,
            BiConsumer<Intent, UserHandle> startActivityFunc) {
        final UserHandle userHandle = getPolicyComplianceUserHandle(context, params, utils);
        final Intent policyComplianceIntent = getPolicyComplianceIntentIfResolvable(
                context, params, utils, userHandle);

        if (policyComplianceIntent != null) {
            startActivityFunc.accept(policyComplianceIntent, userHandle);
            ProvisionLogger.logd(
                    "The DPC POLICY_COMPLIANCE handler was launched on user " + userHandle);
            provisioningAnalyticsTracker.logDpcSetupStarted(
                    context, policyComplianceIntent.getAction());
            return true;
        }

        return false;
    }

    private Intent getPolicyComplianceIntentIfResolvable(Context context,
            ProvisioningParams params, Utils utils, UserHandle userHandle) {
        final Intent policyComplianceIntent = getPolicyComplianceIntent(params, context);
        final boolean intentResolvable = utils.canResolveIntentAsUser(context,
                policyComplianceIntent, userHandle.getIdentifier());
        // Calling startActivity() from outside of an Activity context requires
        // the FLAG_ACTIVITY_NEW_TASK flag.
        if (!(context instanceof Activity)) {
            policyComplianceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intentResolvable ? policyComplianceIntent : null;
    }

    private Intent getPolicyComplianceIntent(
            ProvisioningParams params, Context context) {
        final String adminPackage = params.inferDeviceAdminPackageName();
        final Intent policyComplianceIntent =
                new Intent(DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE);
        policyComplianceIntent.putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                params.adminExtrasBundle);
        // TODO(b/192254845): Remove EXTRA_PROVISIONING_COLOR_PALETTE when framework
        //  fix available
        HashMap<Integer, Integer> colorPaletteMap =
                new ColorPaletteHelper().createColorPaletteMap(
                        context,
                        new ManagedProvisioningSharedPreferences(context));
        policyComplianceIntent.putExtra(EXTRA_PROVISIONING_COLOR_PALETTE, colorPaletteMap);
        policyComplianceIntent.setPackage(adminPackage);
        return policyComplianceIntent;
    }

    private UserHandle getPolicyComplianceUserHandle(Context context,
            ProvisioningParams params, Utils utils) {
        return params.provisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)
                ? utils.getManagedProfile(context)
                : UserHandle.of(UserHandle.myUserId());
    }
}
