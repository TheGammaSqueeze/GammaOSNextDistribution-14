/*
 * Copyright 2016, The Android Open Source Project
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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;

import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.ViewGroup;

import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.ManagedProvisioningScreens;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.PolicyComplianceUtils;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.finalization.PreFinalizationController;
import com.android.managedprovisioning.finalization.UserProvisioningStateHelper;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.TransitionAnimationCallback;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.TransitionAnimationStateManager;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.setupcompat.logging.ScreenKey;
import com.google.android.setupcompat.logging.SetupMetric;
import com.google.android.setupcompat.logging.SetupMetricsLogger;
import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.util.Partner;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Progress activity shown whilst provisioning is ongoing.
 *
 * <p>This activity registers for updates of the provisioning process from the
 * {@link ProvisioningManager}. It shows progress updates as provisioning progresses and handles
 * showing of cancel and error dialogs.</p>
 */
public class ProvisioningActivity extends AbstractProvisioningActivity
        implements TransitionAnimationCallback, TransitionAnimationStateManager {
    private static final int RESULT_CODE_COMPLETE_DEVICE_FINANCE = 121;
    /*
     * Returned after the work profile has been completed. Note this is before launching the DPC.
     */
    @VisibleForTesting
    static final int RESULT_CODE_WORK_PROFILE_CREATED = 122;
    /*
     * Returned after the device owner has been set. Note this is before launching the DPC.
     */
    @VisibleForTesting
    static final int RESULT_CODE_DEVICE_OWNER_SET = 123;

    static final int PROVISIONING_MODE_WORK_PROFILE = 1;
    static final int PROVISIONING_MODE_FULLY_MANAGED_DEVICE = 2;
    static final int PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE = 3;
    static final int PROVISIONING_MODE_FINANCED_DEVICE = 4;
    static final int PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE = 5;
    private static final String SETUP_METRIC_PROVISIONING_SCREEN_NAME = "ShowProvisioningScreens";
    private static String SETUP_METRIC_PROVISIONING_ENDED_NAME = "ProvisioningEnded";
    private ViewGroup mButtonFooterContainer;

    @IntDef(prefix = { "PROVISIONING_MODE_" }, value = {
        PROVISIONING_MODE_WORK_PROFILE,
        PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
        PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE,
        PROVISIONING_MODE_FINANCED_DEVICE,
        PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface ProvisioningMode {}

    private static final Map<Integer, Integer> PROVISIONING_MODE_TO_PROGRESS_LABEL = Map.of(
                PROVISIONING_MODE_WORK_PROFILE, R.string.work_profile_provisioning_progress_label,
                PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
                    R.string.fully_managed_device_provisioning_progress_label,
                PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE,
                    R.string.fully_managed_device_provisioning_progress_label,
                PROVISIONING_MODE_FINANCED_DEVICE, R.string.just_a_sec,
                PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE,
                    R.string.work_profile_provisioning_progress_label);

    private UserProvisioningStateHelper mUserProvisioningStateHelper;
    private PolicyComplianceUtils mPolicyComplianceUtils;
    private ProvisioningManager mProvisioningManager;
    private ProvisioningActivityBridge mBridge;

    public ProvisioningActivity() {
        this(
                /* provisioningManager */ null, // defined in getProvisioningManager()
                new Utils(),
                /* userProvisioningStateHelper */ null, // defined in onCreate()
                new PolicyComplianceUtils(),
                new SettingsFacade(),
                new ThemeHelper(new DefaultNightModeChecker(), new DefaultSetupWizardBridge()));
    }

    @VisibleForTesting
    public ProvisioningActivity(ProvisioningManager provisioningManager,
            Utils utils,
            UserProvisioningStateHelper userProvisioningStateHelper,
            PolicyComplianceUtils policyComplianceUtils,
            SettingsFacade settingsFacade,
            ThemeHelper themeHelper) {
        super(utils, settingsFacade, themeHelper);
        mProvisioningManager = provisioningManager;
        mUserProvisioningStateHelper = userProvisioningStateHelper;
        mPolicyComplianceUtils = checkNotNull(policyComplianceUtils);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupMetricScreenName = SETUP_METRIC_PROVISIONING_SCREEN_NAME;
        mScreenKey = ScreenKey.of(setupMetricScreenName, this);

        SetupMetricsLogger.logMetrics(this, mScreenKey,
                SetupMetric.ofImpression(setupMetricScreenName));

        mBridge = createBridge();
        mBridge.initiateUi(/* activity= */ this);

        // assign this Activity as the view store owner to access saved state and receive updates
        getProvisioningManager().setViewModelStoreOwner(this);

        if (mUserProvisioningStateHelper == null) {
            mUserProvisioningStateHelper = new UserProvisioningStateHelper(this);
        }

        if (mState == STATE_PROVISIONING_FINALIZED) {
            updateProvisioningFinalizedScreen();
        }

        writeSharedPreferences();
    }

    private void writeSharedPreferences() {
        ManagedProvisioningSharedPreferences sharedPreferences =
                new ManagedProvisioningSharedPreferences(this);
        sharedPreferences.writeNavigationBarColor(getWindow().getNavigationBarColor());
        sharedPreferences.writeNavigationBarDividerColor(
                getWindow().getNavigationBarDividerColor());
        sharedPreferences.writeTextPrimaryColor(mUtils.getTextPrimaryColor(this));
        sharedPreferences.writeTextSecondaryColor(mUtils.getTextSecondaryColor(this));
        sharedPreferences.writeBackgroundColor(mUtils.getBackgroundColor(this));
        sharedPreferences.writeAccentColor(mUtils.getAccentColor(this));
        sharedPreferences.writeNotificationBackgroundColor(
                Partner.getColor(this, R.color.setup_notification_bg_color));
    }

    protected ProvisioningActivityBridge createBridge() {
        return ProvisioningActivityBridgeImpl.builder()
                .setParams(mParams)
                .setUtils(mUtils)
                .setProvisioningMode(getProvisioningMode())
                .setProvisioningManager(getProvisioningManager())
                .setTransitionAnimationCallback(this)
                .setInitializeLayoutParamsConsumer(
                        ProvisioningActivity.this::initializeLayoutParams)
                .setShouldSkipEducationScreens(shouldSkipEducationScreens())
                .setProgressLabelResId(getProgressLabelResId())
                .setBridgeCallbacks(createCallbacks())
                .setStateManager(this)
                .build();
    }

    protected Integer getProgressLabelResId() {
        return PROVISIONING_MODE_TO_PROGRESS_LABEL.get(getProvisioningMode());
    }

    protected final ProvisioningActivityBridgeCallbacks createCallbacks() {
        return new ProvisioningActivityBridgeCallbacks() {
            @Override
            public void onNextButtonClicked() {
                ProvisioningActivity.this.onNextButtonClicked();
            }

            @Override
            public void onAbortButtonClicked() {
                ProvisioningActivity.this.onAbortButtonClicked();
            }

            @Override
            public boolean isProvisioningFinalized() {
                return mState == STATE_PROVISIONING_FINALIZED;
            }
        };
    }

    @Override
    protected ProvisioningManager getProvisioningManager() {
        if (mProvisioningManager == null) {
            mProvisioningManager = ProvisioningManager.getInstance(this);
        }
        return mProvisioningManager;
    }

    @VisibleForTesting
    protected void setProvisioningManager(ProvisioningManager provisioningManager) {
        mProvisioningManager = requireNonNull(provisioningManager);
    }

    @Override
    public void preFinalizationCompleted() {
        if (mState == STATE_PROVISIONING_COMPLETED || mState == STATE_PROVISIONING_FINALIZED) {
            return;
        }

        if (!validatePolicyComplianceExists()) {
            ProvisionLogger.loge("POLICY_COMPLIANCE handler not implemented by the admin app.");
            error(R.string.cant_set_up_device,
                    R.string.contact_your_admin_for_help,
                    /* resetRequired */ mParams.isOrganizationOwnedProvisioning);
            return;
        }

        ProvisionLogger.logi("ProvisioningActivity pre-finalization completed");

        // TODO(183094412): Decouple state from AbstractProvisioningActivity
        mState = STATE_PROVISIONING_COMPLETED;

        if (shouldSkipEducationScreens()
                || mBridge.shouldShowButtonsWhenPreProvisioningCompletes()) {
            updateProvisioningFinalizedScreen();
        }
    }

    // Enforces DPCs to implement the POLICY_COMPLIANCE handler for NFC and financed device
    // provisioning, since we no longer set up the DPC on setup wizard's exit procedure.
    // No need to verify it for the other flows, as that was already done earlier.
    // TODO(b/177849035): Remove financed device-specific logic
    private boolean validatePolicyComplianceExists() {
        if (!mUtils.isFinancedDeviceAction(mParams.provisioningAction)) {
            return true;
        }
        return mPolicyComplianceUtils.isPolicyComplianceActivityResolvableForManagedUser(
                this, mParams, mUtils);
    }

    protected final void updateProvisioningFinalizedScreen() {
        mBridge.onProvisioningFinalized(/* activity= */ this);

        SetupMetricsLogger.logMetrics(this, mScreenKey,
                SetupMetric.ofWaitingEnd(SETUP_METRIC_PROVISIONING_ENDED_NAME));

        // TODO(183094412): Decouple state from AbstractProvisioningActivity
        mState = STATE_PROVISIONING_FINALIZED;
    }

    @VisibleForTesting
    protected void onNextButtonClicked() {
        markDeviceManagementEstablishedAndFinish();
    }

    @VisibleForTesting
    protected void onAbortButtonClicked() {
        final Intent intent = new Intent(this,
                getActivityForScreen(ManagedProvisioningScreens.RESET_AND_RETURN_DEVICE));
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
        getTransitionHelper().startActivityWithTransition(this, intent);
    }

    private void finishActivity() {
        if (mParams.provisioningAction.equals(ACTION_PROVISION_FINANCED_DEVICE)) {
            setResult(RESULT_CODE_COMPLETE_DEVICE_FINANCE);
        } else {
            setResult(Activity.RESULT_OK);
        }
        getTransitionHelper().finishActivity(this);
    }

    private void markDeviceManagementEstablishedAndFinish() {
        new PreFinalizationController(this, mUserProvisioningStateHelper)
                .deviceManagementEstablished(mParams);
        if (mParams.flowType == ProvisioningParams.FLOW_TYPE_ADMIN_INTEGRATED) {
            if (mUtils.isProfileOwnerAction(mParams.provisioningAction)) {
                setResult(RESULT_CODE_WORK_PROFILE_CREATED);
            } else if (mUtils.isDeviceOwnerAction(mParams.provisioningAction)) {
                setResult(RESULT_CODE_DEVICE_OWNER_SET);
            } else if (mUtils.isFinancedDeviceAction(mParams.provisioningAction)) {
                setResult(RESULT_CODE_COMPLETE_DEVICE_FINANCE);
            }
            getTransitionHelper().finishActivity(this);
        } else {
            finishActivity();
        }
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS;
    }

    @Override
    protected void decideCancelProvisioningDialog() {
        // TODO(b/213306538): Improve behaviour when cancelling BYOD mid-provisioning
        if (!mParams.isOrganizationOwnedProvisioning) {
            return;
        }

        if (getUtils().isDeviceOwnerAction(mParams.provisioningAction)
                || mParams.isOrganizationOwnedProvisioning) {
            showCancelProvisioningDialog(/* resetRequired = */true);
        } else {
            showCancelProvisioningDialog(/* resetRequired = */false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBridge.onStart(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mBridge.onStop();
        // remove this Activity as the view store owner to avoid memory leaks
        if (isFinishing()) {
            getProvisioningManager().clearViewModelStoreOwner();
        }
    }

    @Override
    public void onAllTransitionsShown() {
        if (mState == STATE_PROVISIONING_COMPLETED) {
            updateProvisioningFinalizedScreen();
        }
    }

    @Override
    public void onAnimationSetup(LottieAnimationView animationView) {
        getThemeHelper().setupAnimationDynamicColors(this, animationView, getIntent());
    }

    @Override
    public void saveState(TransitionAnimationHelper.TransitionAnimationState state) {
        getProvisioningManager().saveTransitionAnimationState(state);
    }

    @Override
    public TransitionAnimationHelper.TransitionAnimationState restoreState() {
        return getProvisioningManager().restoreTransitionAnimationState();
    }

    @Override
    protected boolean isWaitingScreen() {
        return shouldSkipEducationScreens();
    }

    protected @ProvisioningMode int getProvisioningMode() {
        int provisioningMode = 0;
        final boolean isProfileOwnerAction =
                mUtils.isProfileOwnerAction(mParams.provisioningAction);
        if (isProfileOwnerAction) {
            if (getSystemService(DevicePolicyManager.class).isDeviceManaged()) {
                provisioningMode = PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE;
            } else if (mParams.isOrganizationOwnedProvisioning) {
                provisioningMode = PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE;
            } else {
                provisioningMode = PROVISIONING_MODE_WORK_PROFILE;
            }
        } else if (mUtils.isDeviceOwnerAction(mParams.provisioningAction)) {
            provisioningMode = PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
        } else if (mUtils.isFinancedDeviceAction(mParams.provisioningAction)) {
            provisioningMode = PROVISIONING_MODE_FINANCED_DEVICE;
        }
        return provisioningMode;
    }

    protected boolean shouldSkipEducationScreens() {
        return mParams.skipEducationScreens
                || getProvisioningMode() == PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE
                || getProvisioningMode() == PROVISIONING_MODE_FINANCED_DEVICE;
    }
}
