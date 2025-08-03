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

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.UserHandle;

import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.PolicyComplianceUtils;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.finalization.DpcReceivedSuccessReceiver.Callback;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * A {@link Service} which sends the
 * {@link android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE} broadcast to
 * the DPC. It keeps the ManagedProvisioning process alive while the user is on the DPC screen.
 */
public class SendDpcBroadcastService extends Service implements Callback {

    public static String EXTRA_PROVISIONING_PARAMS =
            "com.android.managedprovisioning.PROVISIONING_PARAMS";
    private SettingsFacade mSettingsFacade = new SettingsFacade();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final Context context = getApplicationContext();
        ProvisioningParams params = intent.getParcelableExtra(EXTRA_PROVISIONING_PARAMS);
        Utils utils = new Utils();
        ProvisioningIntentProvider helper = new ProvisioningIntentProvider();
        UserHandle managedProfileUserHandle = utils.getManagedProfile(context);
        if (params.flowType == ProvisioningParams.FLOW_TYPE_ADMIN_INTEGRATED) {
            new PrimaryProfileFinalizationHelper(params.accountToMigrate, managedProfileUserHandle,
                    params.inferDeviceAdminPackageName())
                .finalizeProvisioningInPrimaryProfile(/* context */ this, /* callback */ this);
        } else {
            sendDpcReceivedSuccessReceiver(
                    context, params, utils, helper, managedProfileUserHandle);
        }

        maybeLaunchDpc(context, params, utils, helper, managedProfileUserHandle);

        return START_STICKY;
    }

    private void maybeLaunchDpc(Context context, ProvisioningParams params, Utils utils,
            ProvisioningIntentProvider helper, UserHandle managedProfileUserHandle) {
        final ProvisioningAnalyticsTracker provisioningAnalyticsTracker =
                new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(context, mSettingsFacade),
                        new ManagedProvisioningSharedPreferences(context));

        PolicyComplianceUtils policyComplianceUtils = new PolicyComplianceUtils();
        helper.maybeLaunchDpc(
                params, managedProfileUserHandle.getIdentifier(),
                utils, context, provisioningAnalyticsTracker, policyComplianceUtils,
                mSettingsFacade);
    }

    private void sendDpcReceivedSuccessReceiver(Context context, ProvisioningParams params,
            Utils utils, ProvisioningIntentProvider helper, UserHandle managedProfileUserHandle) {
        Intent completeIntent =
                helper.createProvisioningCompleteIntent(params,
                        managedProfileUserHandle.getIdentifier(), utils, context);
        // Use an ordered broadcast, so that we only finish when the DPC has received it.
        // Avoids a lag in the transition between provisioning and the DPC.
        BroadcastReceiver dpcReceivedSuccessReceiver =
                new DpcReceivedSuccessReceiver(params.accountToMigrate,
                        managedProfileUserHandle, params.inferDeviceAdminPackageName(),
                        this);
        sendOrderedBroadcastAsUser(completeIntent, managedProfileUserHandle, null,
                dpcReceivedSuccessReceiver, null, Activity.RESULT_OK, null, null);
        ProvisionLogger.logd("Provisioning complete broadcast has been sent to user "
                + managedProfileUserHandle.getIdentifier());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void cleanup() {
        stopSelf();
    }
}
