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

import static com.android.managedprovisioning.finalization.FinalizationController.PROVISIONING_FINALIZED_RESULT_CHILD_ACTIVITY_LAUNCHED;
import static com.android.managedprovisioning.finalization.FinalizationController.PROVISIONING_FINALIZED_RESULT_NO_CHILD_ACTIVITY_LAUNCHED;
import static com.android.managedprovisioning.finalization.FinalizationController.ProvisioningFinalizedResult;

import android.app.Activity;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.PolicyComplianceUtils;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.StartDpcInsideSuwServiceConnection;
import com.android.managedprovisioning.common.TransitionHelper;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * This controller is used to finalize managed provisioning before the end of Setup Wizard.
 */
public class FinalizationInsideSuwControllerLogic implements FinalizationControllerLogic {
    private static final String START_DPC_SERVICE_STATE_KEY = "start_dpc_service_state";
    private static final String LAST_REQUEST_CODE_KEY = "last_request_code";

    private final Activity mActivity;
    private final Utils mUtils;
    private final PolicyComplianceUtils mPolicyComplianceUtils;
    private final ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;
    private final TransitionHelper mTransitionHelper;
    private StartDpcInsideSuwServiceConnection mStartDpcInsideSuwServiceConnection;
    private int mLastRequestCode = 0;

    public FinalizationInsideSuwControllerLogic(Activity activity) {
        this(activity, new Utils(), new PolicyComplianceUtils(),
                new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(activity, new SettingsFacade()),
                        new ManagedProvisioningSharedPreferences(activity)),
                new TransitionHelper());
    }

    public FinalizationInsideSuwControllerLogic(Activity activity, Utils utils,
            PolicyComplianceUtils policyComplianceUtils,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker,
            TransitionHelper transitionHelper) {
        mActivity = activity;
        mUtils = utils;
        mPolicyComplianceUtils = policyComplianceUtils;
        mProvisioningAnalyticsTracker = provisioningAnalyticsTracker;
        mTransitionHelper = transitionHelper;
    }

    @Override
    public @ProvisioningFinalizedResult int notifyDpcManagedProfile(
            ProvisioningParams params, int requestCode) {
        return startPolicyComplianceActivityForResultIfResolved(params, requestCode);
    }

    @Override
    public @ProvisioningFinalizedResult int notifyDpcManagedDeviceOrUser(
            ProvisioningParams params, int requestCode) {
        return startPolicyComplianceActivityForResultIfResolved(params, requestCode);
    }

    @Override
    public boolean shouldFinalizePrimaryProfile(ProvisioningParams params) {
        return true;
    }

    @Override
    public void saveInstanceState(Bundle outState) {
        outState.putInt(LAST_REQUEST_CODE_KEY, mLastRequestCode);

        if (mStartDpcInsideSuwServiceConnection != null) {
            final Bundle startDpcServiceState = new Bundle();
            outState.putBundle(START_DPC_SERVICE_STATE_KEY, startDpcServiceState);
            mStartDpcInsideSuwServiceConnection.saveInstanceState(startDpcServiceState);
        }
    }

    @Override
    public void restoreInstanceState(Bundle savedInstanceState, ProvisioningParams params) {
        mLastRequestCode = savedInstanceState.getInt(LAST_REQUEST_CODE_KEY);

        final Bundle startDpcServiceState =
                savedInstanceState.getBundle(START_DPC_SERVICE_STATE_KEY);

        if (startDpcServiceState != null) {
            mStartDpcInsideSuwServiceConnection = new StartDpcInsideSuwServiceConnection(
                    mActivity,
                    startDpcServiceState,
                    getDpcIntentSender(params, mLastRequestCode));
        }
    }

    @Override
    public void activityDestroyed(boolean isFinishing) {
        if (mStartDpcInsideSuwServiceConnection != null) {
            if (isFinishing) {
                mStartDpcInsideSuwServiceConnection.dpcFinished();
            }

            mStartDpcInsideSuwServiceConnection.unbind(mActivity);

            if (isFinishing) {
                mStartDpcInsideSuwServiceConnection = null;
            }
        }
    }

    private @ProvisioningFinalizedResult int startPolicyComplianceActivityForResultIfResolved(
            ProvisioningParams params, int requestCode) {
        if (!mPolicyComplianceUtils.isPolicyComplianceActivityResolvableForManagedUser(
                mActivity, params, mUtils)) {
            return PROVISIONING_FINALIZED_RESULT_NO_CHILD_ACTIVITY_LAUNCHED;
        }

        if (mStartDpcInsideSuwServiceConnection != null) {
            ProvisionLogger.logw("Attempted to start DPC again before result received - ignoring");
            return PROVISIONING_FINALIZED_RESULT_NO_CHILD_ACTIVITY_LAUNCHED;
        }

        // Connect to a SUW service to disable network intent interception before starting the DPC.
        mStartDpcInsideSuwServiceConnection = new StartDpcInsideSuwServiceConnection();
        mStartDpcInsideSuwServiceConnection.triggerDpcStart(mActivity,
                getDpcIntentSender(params, requestCode));

        mLastRequestCode = requestCode;

        return PROVISIONING_FINALIZED_RESULT_CHILD_ACTIVITY_LAUNCHED;
    }

    private Runnable getDpcIntentSender(ProvisioningParams params, int requestCode) {
        return () ->
                mPolicyComplianceUtils.startPolicyComplianceActivityForResultIfResolved(
                        mActivity, params, requestCode, mUtils, mProvisioningAnalyticsTracker,
                        mTransitionHelper);
    }
}
