/*
 * Copyright 2019, The Android Open Source Project
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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * This controller is invoked, via a call to
 * {@link #deviceManagementEstablished(ProvisioningParams)}, immediately after a Device Owner or
 * Profile Owner is provisioned.  If the user is creating a managed profile on a device post-Setup
 * Wizard, then we immediately start the DPC app and set the provisioning state to finalized, so no
 * further finalization calls are necessary.  On the other hand, if we are still inside of Setup
 * Wizard, provisioning needs to be finalized after this using an instance of
 * {@link FinalizationController}.
 */
public final class PreFinalizationController {
    private final Context mContext;
    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;
    private final UserProvisioningStateHelper mUserProvisioningStateHelper;
    private final ProvisioningParamsUtils mProvisioningParamsUtils;
    private final SendDpcBroadcastServiceUtils mSendDpcBroadcastServiceUtils;

    public PreFinalizationController(Context context,
          UserProvisioningStateHelper userProvisioningStateHelper) {
        this(
                context,
                new Utils(),
                new SettingsFacade(),
                userProvisioningStateHelper,
                new ProvisioningParamsUtils(
                        ProvisioningParamsUtils.DEFAULT_PROVISIONING_PARAMS_FILE_PROVIDER),
                new SendDpcBroadcastServiceUtils());
    }

    public PreFinalizationController(Context context) {
        this(
                context,
                new Utils(),
                new SettingsFacade(),
                new UserProvisioningStateHelper(context),
                new ProvisioningParamsUtils(
                        ProvisioningParamsUtils.DEFAULT_PROVISIONING_PARAMS_FILE_PROVIDER),
                new SendDpcBroadcastServiceUtils());
    }

    @VisibleForTesting
    PreFinalizationController(Context context,
            Utils utils,
            SettingsFacade settingsFacade,
            UserProvisioningStateHelper helper,
            ProvisioningParamsUtils provisioningParamsUtils,
            SendDpcBroadcastServiceUtils sendDpcBroadcastServiceUtils) {
        mContext = checkNotNull(context);
        mUtils = checkNotNull(utils);
        mSettingsFacade = checkNotNull(settingsFacade);
        mUserProvisioningStateHelper = checkNotNull(helper);
        mProvisioningParamsUtils = provisioningParamsUtils;
        mSendDpcBroadcastServiceUtils = sendDpcBroadcastServiceUtils;
    }

    /**
     * This method is invoked after a Device Owner or Profile Owner has been set up.
     *
     * <p>If provisioning happens as part of SUW, we rely on an instance of
     * FinalizationControllerBase to be invoked later on. Otherwise, this method will finalize
     * provisioning. If called after SUW, this method notifies the DPC about the completed
     * provisioning; otherwise, it stores the provisioning params for later digestion.</p>
     *
     * <p>Note that fully managed device provisioning is only possible during SUW.
     *
     * @param params the provisioning params
     */
    public final void deviceManagementEstablished(ProvisioningParams params) {
        if (!mUserProvisioningStateHelper.isStateUnmanagedOrFinalized()) {
            // In any other state than STATE_USER_SETUP_FINALIZED, STATE_USER_PROFILE_FINALIZED and
            // STATE_USER_UNMANAGED, we've already run this method, so don't do anything.
            ProvisionLogger.logw("deviceManagementEstablished called, but state is not finalized "
                    + "or unmanaged");
            return;
        }

        mUserProvisioningStateHelper.markUserProvisioningStateInitiallyDone(params);
        if (ACTION_PROVISION_MANAGED_PROFILE.equals(params.provisioningAction)) {
            if (!params.returnBeforePolicyCompliance) {
                // If a managed profile was provisioned and the provisioning initiator has requested
                // managed profile provisioning and DPC setup to happen in one step, notify the
                // DPC straight away.
                mSendDpcBroadcastServiceUtils.startSendDpcBroadcastService(mContext, params);
            }
        }
        if (params.returnBeforePolicyCompliance) {
            // Store the information and wait for provisioningFinalized to be called
            storeProvisioningParams(params);
        }
    }

    private void storeProvisioningParams(ProvisioningParams params) {
        params.save(mProvisioningParamsUtils.getProvisioningParamsFile(mContext));
    }
}
