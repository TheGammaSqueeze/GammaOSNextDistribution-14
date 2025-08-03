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

package com.android.managedprovisioning.preprovisioning;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER;
import static android.app.admin.DevicePolicyManager.EXTRA_RESULT_LAUNCH_INTENT;
import static android.app.admin.DevicePolicyManager.EXTRA_ROLE_HOLDER_UPDATE_FAILURE_STRATEGY;
import static android.app.admin.DevicePolicyManager.EXTRA_ROLE_HOLDER_UPDATE_RESULT_CODE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_UNSPECIFIED;
import static android.app.admin.DevicePolicyManager.RESULT_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER_PROVISIONING_DISABLED;
import static android.app.admin.DevicePolicyManager.RESULT_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER_RECOVERABLE_ERROR;
import static android.app.admin.DevicePolicyManager.RESULT_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER_UNRECOVERABLE_ERROR;
import static android.app.admin.DevicePolicyManager.RESULT_UPDATE_ROLE_HOLDER;
import static android.app.admin.DevicePolicyManager.ROLE_HOLDER_UPDATE_FAILURE_STRATEGY_FAIL_PROVISIONING;
import static android.app.admin.DevicePolicyManager.ROLE_HOLDER_UPDATE_FAILURE_STRATEGY_FALLBACK_TO_PLATFORM_PROVISIONING;
import static android.content.res.Configuration.UI_MODE_NIGHT_MASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static com.android.managedprovisioning.ManagedProvisioningScreens.RETRY_LAUNCH;
import static com.android.managedprovisioning.common.ErrorDialogUtils.EXTRA_DIALOG_TITLE_ID;
import static com.android.managedprovisioning.common.ErrorDialogUtils.EXTRA_ERROR_MESSAGE_RES_ID;
import static com.android.managedprovisioning.common.ErrorDialogUtils.EXTRA_FACTORY_RESET_REQUIRED;
import static com.android.managedprovisioning.common.RetryLaunchActivity.EXTRA_INTENT_TO_LAUNCH;
import static com.android.managedprovisioning.model.ProvisioningParams.FLOW_TYPE_LEGACY;
import static com.android.managedprovisioning.preprovisioning.PreProvisioningViewModel.STATE_PREPROVISIONING_INITIALIZING;
import static com.android.managedprovisioning.preprovisioning.PreProvisioningViewModel.STATE_SHOWING_USER_CONSENT;
import static com.android.managedprovisioning.provisioning.Constants.PROVISIONING_SERVICE_INTENT;
import static com.google.android.setupcompat.util.WizardManagerHelper.EXTRA_IS_SETUP_FLOW;
import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.BackgroundServiceStartNotAllowedException;
import android.app.DialogFragment;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.ManagedProvisioningBaseApplication;
import com.android.managedprovisioning.ManagedProvisioningScreens;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.DefaultFeatureFlagChecker;
import com.android.managedprovisioning.common.DefaultIntentResolverChecker;
import com.android.managedprovisioning.common.DefaultPackageInstallChecker;
import com.android.managedprovisioning.common.DeviceManagementRoleHolderUpdaterHelper;
import com.android.managedprovisioning.common.Flags;
import com.android.managedprovisioning.common.GetProvisioningModeUtils;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.RetryLaunchActivity;
import com.android.managedprovisioning.common.RoleHolderProvider;
import com.android.managedprovisioning.common.RoleHolderUpdaterProvider;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.SimpleDialog;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.contracts.DownloadRoleHolderArguments;
import com.android.managedprovisioning.contracts.DownloadRoleHolderContract;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.preprovisioning.PreProvisioningActivityController.UiParams;
import com.android.managedprovisioning.provisioning.AdminIntegratedFlowPrepareActivity;
import com.android.managedprovisioning.provisioning.ProvisioningActivity;
import com.android.managedprovisioning.util.LazyStringResource;
import com.google.android.setupcompat.logging.ScreenKey;
import com.google.android.setupcompat.logging.SetupMetric;
import com.google.android.setupcompat.logging.SetupMetricsLogger;
import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.transition.TransitionHelper;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint(SetupGlifLayoutActivity.class)
public class PreProvisioningActivity extends Hilt_PreProvisioningActivity implements
        SimpleDialog.SimpleDialogListener, PreProvisioningActivityController.Ui {

    private static final int ENCRYPT_DEVICE_REQUEST_CODE = 1;
    @VisibleForTesting
    protected static final int PROVISIONING_REQUEST_CODE = 2;
    private static final int WIFI_REQUEST_CODE = 3;
    private static final int CHANGE_LAUNCHER_REQUEST_CODE = 4;
    private static final int ORGANIZATION_OWNED_LANDING_PAGE_REQUEST_CODE = 5;
    private static final int GET_PROVISIONING_MODE_REQUEST_CODE = 6;
    private static final int FINANCED_DEVICE_PREPARE_REQUEST_CODE = 7;
    private static final int ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE = 8;
    private static final int START_PLATFORM_REQUESTED_ROLE_HOLDER_UPDATE_REQUEST_CODE = 9;
    private static final int START_DEVICE_MANAGEMENT_ROLE_HOLDER_PROVISIONING_REQUEST_CODE = 10;
    private static final int DOWNLOAD_DEVICE_MANAGEMENT_ROLE_HOLDER_FROM_PLATFORM_REQUEST_CODE = 11;
    private static final int START_ROLE_HOLDER_REQUESTED_UPDATE_REQUEST_CODE = 12;

    // Note: must match the constant defined in HomeSettings
    private static final String EXTRA_SUPPORT_MANAGED_PROFILES = "support_managed_profiles";

    private static final String ERROR_AND_CLOSE_DIALOG = "PreProvErrorAndCloseDialog";
    private static final String BACK_PRESSED_DIALOG_RESET = "PreProvBackPressedDialogReset";
    private static final String BACK_PRESSED_DIALOG_CLOSE_ACTIVITY =
            "PreProvBackPressedDialogCloseActivity";
    private static final String LAUNCHER_INVALID_DIALOG = "PreProvCurrentLauncherInvalidDialog";
    private static final String SETUP_METRIC_PREPROVISIONING_SCREEN_NAME =
            "ShowPreProvisioningScreen";

    private PreProvisioningActivityController mController;
    private final ControllerProvider mControllerProvider;
    private final AccessibilityContextMenuMaker mContextMenuMaker;
    private PreProvisioningActivityBridge mBridge;
    private boolean mShouldForwardTransition;
    private final RoleHolderUpdaterProvider mRoleHolderUpdaterProvider;
    private final RoleHolderProvider mRoleHolderProvider;

    private static final String ERROR_DIALOG_RESET = "ErrorDialogReset";
    private static final int SETUP_METRIC_DEFAULT_ERROR_CODE = -1;
    private ProvisioningAnalyticsTracker mAnalyticsTracker;
    private boolean mAlreadyInitialized;
    protected ScreenKey mScreenKey;
    protected String setupMetricScreenName;

    @Inject
    protected Flags mFlags;
    @Inject
    protected DownloadRoleHolderContract mDownloadRoleHolderContract;

    public PreProvisioningActivity() {
        this(activity ->
                        new PreProvisioningActivityController(activity, activity),
                null,
                new Utils(),
                new SettingsFacade(),
                new ThemeHelper(
                        new DefaultNightModeChecker(),
                        new DefaultSetupWizardBridge()),
                RoleHolderProvider.DEFAULT,
                RoleHolderUpdaterProvider.DEFAULT);
    }

    @VisibleForTesting
    public PreProvisioningActivity(
            ControllerProvider controllerProvider,
            AccessibilityContextMenuMaker contextMenuMaker, Utils utils,
            SettingsFacade settingsFacade, ThemeHelper themeHelper,
            RoleHolderProvider roleHolderProvider,
            RoleHolderUpdaterProvider roleHolderUpdaterProvider) {
        super(utils, settingsFacade, themeHelper);
        mControllerProvider = requireNonNull(controllerProvider);
        mContextMenuMaker =
                contextMenuMaker != null ? contextMenuMaker : new AccessibilityContextMenuMaker(
                        this);
        mRoleHolderUpdaterProvider = requireNonNull(roleHolderUpdaterProvider);
        mRoleHolderProvider = requireNonNull(roleHolderProvider);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO(b/192074477): Remove deferred setup-specific logic after the managed account flow
        //  starts ManagedProvisioning with the isSetupFlow extra
        // This temporary fix only works when called before super.onCreate
        if (mSettingsFacade.isDeferredSetup(getApplicationContext())) {
            getIntent().putExtra(EXTRA_IS_SETUP_FLOW, true);
        }

        super.onCreate(savedInstanceState);
        setupMetricScreenName = SETUP_METRIC_PREPROVISIONING_SCREEN_NAME;
        mScreenKey = ScreenKey.of(setupMetricScreenName, this);

        if (savedInstanceState == null) {
            mAlreadyInitialized = false;
        }
        mController = mControllerProvider.getInstance(this);
        mBridge = createBridge();
        mController.getState().observe(this, this::onStateChanged);

        mAnalyticsTracker =
                new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(this, new SettingsFacade()),
                        new ManagedProvisioningSharedPreferences(this));
        logMetrics();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            getApplicationContext().startService(PROVISIONING_SERVICE_INTENT);
        } catch (BackgroundServiceStartNotAllowedException e) {
            ProvisionLogger.loge(e);
        }
        mController.getState().observe(this, this::onStateChanged);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SetupMetricsLogger.logMetrics(this, mScreenKey,
                SetupMetric.ofImpression(setupMetricScreenName));
        if (mShouldForwardTransition) {
            TransitionHelper.applyForwardTransition(
                    this, TransitionHelper.TRANSITION_FADE_THROUGH);
            mShouldForwardTransition = false;
        }
    }

    protected PreProvisioningActivityBridge createBridge() {
        return new PreProvisioningActivityBridgeImpl(
                /* activity= */ this,
                mUtils,
                PreProvisioningActivity.this::initializeLayoutParams,
                createBridgeCallbacks(),
                getThemeHelper(),
                setupMetricScreenName);
    }

    protected final PreProvisioningActivityBridgeCallbacks createBridgeCallbacks() {
        return new PreProvisioningActivityBridgeCallbacks() {
            @Override
            public void onTermsAccepted() {
                mController.continueProvisioningAfterUserConsent();
            }

            @Override
            public void onTermsButtonClicked() {
                getTransitionHelper()
                        .startActivityWithTransition(PreProvisioningActivity.this,
                                mController.createViewTermsIntent());
            }
        };
    }

    private void onStateChanged(Integer state) {
        switch (state) {
            case STATE_PREPROVISIONING_INITIALIZING:
                if (!mAlreadyInitialized) {
                    mController.initiateProvisioning(getIntent(), getCallingPackage());
                    mAlreadyInitialized = true;
                }
                break;
            case STATE_SHOWING_USER_CONSENT:
                mController.showUserConsentScreen();
                break;
        }
    }

    @Override
    public void finish() {
        // The user has backed out of provisioning, so we perform the necessary clean up steps.
        ProvisioningParams params = mController.getParams();
        if (params != null) {
            params.cleanUp();
        }
        getEncryptionController().cancelEncryptionReminder();
        getApplicationContext().stopService(PROVISIONING_SERVICE_INTENT);
        super.finish();
    }

    @SuppressWarnings("MissingSuperCall") // TODO: Fix me
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ENCRYPT_DEVICE_REQUEST_CODE:
                if (resultCode == RESULT_CANCELED) {
                    ProvisionLogger.loge("User canceled device encryption.");
                }
                break;
            case PROVISIONING_REQUEST_CODE:
                mController.onReturnFromProvisioning();
                setResult(resultCode);
                getTransitionHelper().finishActivity(this);
                break;
            case CHANGE_LAUNCHER_REQUEST_CODE:
                mController.continueProvisioningAfterUserConsent();
                break;
            case WIFI_REQUEST_CODE:
                if (resultCode == RESULT_CANCELED) {
                    ProvisionLogger.loge("User canceled wifi picking.");
                    setResult(resultCode);
                    getTransitionHelper().finishActivity(this);
                } else {
                    if (resultCode == RESULT_OK) {
                        ProvisionLogger.logd("Wifi request result is OK");
                    }
                    mController.initiateProvisioning(getIntent(), getCallingPackage());
                }
                break;
            case ORGANIZATION_OWNED_LANDING_PAGE_REQUEST_CODE:
            case ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    handleAdminIntegratedFlowPreparerResult();
                } else {
                    ProvisionLogger.loge(
                            "Provisioning was aborted in the preparation stage, "
                                    + "requestCode = " + requestCode);
                    SetupMetricsLogger.logMetrics(this, mScreenKey,
                            SetupMetric.ofError(setupMetricScreenName, resultCode));
                    if (isDpcInstalled()
                            && mUtils.isOrganizationOwnedAllowed(mController.getParams())) {
                        showFactoryResetDialog(R.string.cant_set_up_device,
                                R.string.contact_your_admin_for_help);
                    } else {
                        showErrorAndClose(
                                R.string.cant_set_up_device,
                                R.string.contact_your_admin_for_help,
                                "Failed provisioning device.");
                    }
                }
                break;
            case GET_PROVISIONING_MODE_REQUEST_CODE:
                mShouldForwardTransition = true;
                if (resultCode == RESULT_OK) {
                    if (data != null && mController.updateProvisioningParamsFromIntent(data)) {
                        mController.showUserConsentScreen();
                    } else {
                        ProvisionLogger.loge(
                                "Invalid data object returned from GET_PROVISIONING_MODE.");
                        SetupMetricsLogger.logMetrics(this, mScreenKey,
                                SetupMetric.ofError(setupMetricScreenName, resultCode));
                        if (mUtils.isOrganizationOwnedAllowed(mController.getParams())) {
                            showFactoryResetDialog(R.string.cant_set_up_device,
                                    R.string.contact_your_admin_for_help);
                        } else {
                            showErrorAndClose(
                                    R.string.cant_set_up_device,
                                    R.string.contact_your_admin_for_help,
                                    "Failed provisioning personally-owned device.");
                        }
                    }
                } else {
                    ProvisionLogger.loge("Invalid result code from GET_PROVISIONING_MODE. Expected "
                            + RESULT_OK + " but got " + resultCode + ".");
                    SetupMetricsLogger.logMetrics(this, mScreenKey,
                            SetupMetric.ofError(setupMetricScreenName, resultCode));
                    if (mUtils.isOrganizationOwnedAllowed(mController.getParams())) {
                        showFactoryResetDialog(R.string.cant_set_up_device,
                                R.string.contact_your_admin_for_help);
                    } else {
                        showErrorAndClose(
                                R.string.cant_set_up_device,
                                R.string.contact_your_admin_for_help,
                                "Failed to provision personally-owned device.");
                    }
                }
                break;
            case FINANCED_DEVICE_PREPARE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    startFinancedDeviceFlow();
                } else {
                    setResult(resultCode);
                    getTransitionHelper().finishActivity(this);
                }
                break;
            case START_PLATFORM_REQUESTED_ROLE_HOLDER_UPDATE_REQUEST_CODE:
                handlePlatformRequestedUpdateResult(resultCode, data);
                break;
            case START_ROLE_HOLDER_REQUESTED_UPDATE_REQUEST_CODE:
                handleRoleHolderRequestedUpdateResult(resultCode, data);
                break;
            case START_DEVICE_MANAGEMENT_ROLE_HOLDER_PROVISIONING_REQUEST_CODE:
                ProvisionLogger.logw("Role holder returned result code " + resultCode);
                mAnalyticsTracker.logRoleHolderProvisioningFinish();
                if (resultCode == RESULT_UPDATE_ROLE_HOLDER) {
                    if (handleUpdateRequestedWithNoRoleHolderUpdater(resultCode)) {
                        return;
                    }
                    if (handleUpdateRequestedWithWrongStateType(data)) {
                        return;
                    }
                    PersistableBundle roleHolderState =
                            data.getParcelableExtra(DevicePolicyManager.EXTRA_ROLE_HOLDER_STATE);
                    mController.resetRoleHolderUpdateRetryCount();
                    mController.startRoleHolderUpdater(
                            /* isRoleHolderRequestedUpdate= */ true, roleHolderState);
                } else {
                    maybeHandleLaunchIntent(resultCode, data);
                    getTransitionHelper().finishActivity(this);
                }
                break;
            case DOWNLOAD_DEVICE_MANAGEMENT_ROLE_HOLDER_FROM_PLATFORM_REQUEST_CODE:
                mAnalyticsTracker.logPlatformRoleHolderUpdateFinished(resultCode);
                if (resultCode == RESULT_OK
                        || mController.getParams().allowOffline) {
                    boolean isProvisioningStarted = mController.startAppropriateProvisioning(
                            getIntent(),
                            new Bundle(),
                            getCallingPackage());
                    if (!isProvisioningStarted) {
                        mAnalyticsTracker.logPlatformRoleHolderUpdateFailed();
                        ProvisionLogger.loge("Provisioning could not be started following "
                                + "platform-side role holder download.");
                        SetupMetricsLogger.logMetrics(this, mScreenKey,
                                SetupMetric.ofError(setupMetricScreenName, resultCode));
                        showRoleHolderDownloadFailedDialog(new Intent());
                    }
                } else if (data != null && data.hasExtra(EXTRA_ERROR_MESSAGE_RES_ID)) {
                    mAnalyticsTracker.logPlatformRoleHolderUpdateFailed();
                    ProvisionLogger.loge("Role holder download failed and offline provisioning is "
                            + "not allowed.");
                    SetupMetricsLogger.logMetrics(this, mScreenKey,
                            SetupMetric.ofError(setupMetricScreenName, resultCode));
                    showRoleHolderDownloadFailedDialog(data);
                } else {
                    mAnalyticsTracker.logPlatformRoleHolderUpdateFailed();
                    ProvisionLogger.loge("Role holder download failed and offline provisioning is "
                            + "not allowed.");
                    SetupMetricsLogger.logMetrics(this, mScreenKey,
                            SetupMetric.ofError(setupMetricScreenName, resultCode));
                    showRoleHolderDownloadFailedDialog(new Intent());
                }
                break;
            default:
                ProvisionLogger.logw("Unknown result code :" + resultCode);
                SetupMetricsLogger.logMetrics(this, mScreenKey,
                        SetupMetric.ofError(setupMetricScreenName, resultCode));
                break;
        }
    }

    private boolean handleUpdateRequestedWithWrongStateType(Intent data) {
        if (!(data.getParcelableExtra(DevicePolicyManager.EXTRA_ROLE_HOLDER_STATE)
                instanceof PersistableBundle)) {
            ProvisionLogger.loge("Failed to process role holder state result.");
            SetupMetricsLogger.logMetrics(this, mScreenKey,
                    SetupMetric.ofError(setupMetricScreenName, SETUP_METRIC_DEFAULT_ERROR_CODE));
            if (mUtils.isOrganizationOwnedAllowed(mController.getParams())) {
                showFactoryResetDialog(R.string.cant_set_up_device,
                        R.string.contact_your_admin_for_help);
            } else {
                showErrorAndClose(
                        R.string.cant_set_up_device,
                        R.string.contact_your_admin_for_help,
                        "Failed to process role holder state result.");
            }
            return true;
        }
        return false;
    }

    private boolean handleUpdateRequestedWithNoRoleHolderUpdater(int resultCode) {
        if (TextUtils.isEmpty(
                mRoleHolderUpdaterProvider.getPackageName(this))) {
            ProvisionLogger.logw("Role holder requested update, but there is no role "
                    + "holder updater present. Restarting the role holder.");
            boolean isProvisioningStarted = mController.startAppropriateProvisioning(
                    getIntent(),
                    createRoleHolderAdditionalExtras(resultCode),
                    getCallingPackage());
            if (!isProvisioningStarted) {
                failRoleHolderUpdate();
                ProvisionLogger.loge("Failed to start provisioning after a "
                        + "role holder-requested role holder update and no updater "
                        + "present. Result is " + resultCode + " and allow offline "
                        + "provisioning is " + mController.getParams().allowOffline);
                SetupMetricsLogger.logMetrics(this, mScreenKey,
                        SetupMetric.ofError(setupMetricScreenName, resultCode));
            }
            return true;
        }
        return false;
    }

    private Bundle createRoleHolderAdditionalExtras(int resultCode) {
        Bundle additionalExtras = new Bundle();
        additionalExtras.putInt(EXTRA_ROLE_HOLDER_UPDATE_RESULT_CODE, resultCode);
        return additionalExtras;
    }

    private void handlePlatformRequestedUpdateResult(int resultCode, @Nullable Intent resultData) {
        mAnalyticsTracker.logRoleHolderUpdaterUpdateFinish(resultCode);
        if ((resultCode
                == RESULT_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER_RECOVERABLE_ERROR
                || resultCode == RESULT_CANCELED)
                && mController.canRetryRoleHolderUpdate()) {
            mController.startRoleHolderUpdaterWithLastState(
                    /* isRoleHolderRequestedUpdate= */ false);
            mController.incrementRoleHolderUpdateRetryCount();
            mAnalyticsTracker.logRoleHolderUpdaterUpdateRetry();
        } else if (resultCode
                == RESULT_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER_PROVISIONING_DISABLED
        ) {
            mController.performPlatformProvidedProvisioning();
        } else if (resultCode
                != RESULT_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER_UNRECOVERABLE_ERROR) {
            mController.resetRoleHolderUpdateRetryCount();
            boolean isProvisioningStarted = mController.startAppropriateProvisioning(
                    getIntent(),
                    createRoleHolderAdditionalExtras(resultCode),
                    getCallingPackage());
            if (!isProvisioningStarted) {
                if (isRoleHolderUpdaterRequestingPlatformDrivenProvisioning(resultData)) {
                    ProvisionLogger.logi("Result is " + resultCode
                            + " and applied fallback strategy.");
                    mController.performPlatformProvidedProvisioning();
                } else {
                    mAnalyticsTracker.logRoleHolderUpdaterUpdateFailed();
                    failRoleHolderUpdate();
                    ProvisionLogger.loge("Failed to start provisioning after a "
                            + "platform-requested role holder update. Result is " + resultCode
                            + " and allow offline provisioning is "
                            + mController.getParams().allowOffline);
                    SetupMetricsLogger.logMetrics(this, mScreenKey,
                            SetupMetric.ofError(setupMetricScreenName, resultCode));
                }
            }
        } else if (mController.getParams().allowOffline) {
            ProvisionLogger.logi("Result is " + resultCode + ". Allowed offline provisioning.");
            mController.performPlatformProvidedProvisioning();
        } else if (isRoleHolderUpdaterRequestingPlatformDrivenProvisioning(resultData)) {
            ProvisionLogger.logi("Result is " + resultCode + " and applied fallback strategy.");
            mController.performPlatformProvidedProvisioning();
        } else {
            mAnalyticsTracker.logRoleHolderUpdaterUpdateFailed();
            failRoleHolderUpdate();
            ProvisionLogger.loge("Failed to perform a platform-requested role holder "
                    + "update. Result is " + resultCode + " and allow offline provisioning"
                    + " is " + mController.getParams().allowOffline);
            SetupMetricsLogger.logMetrics(this, mScreenKey,
                    SetupMetric.ofError(setupMetricScreenName, resultCode));
        }
    }

    private void handleRoleHolderRequestedUpdateResult(int resultCode, Intent resultData) {
        if ((resultCode
                == RESULT_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER_RECOVERABLE_ERROR
                || resultCode == RESULT_CANCELED)
                && mController.canRetryRoleHolderUpdate()) {
            mController.startRoleHolderUpdaterWithLastState(
                    /* isRoleHolderRequestedUpdate= */ true);
            mController.incrementRoleHolderUpdateRetryCount();
        } else if (resultCode
                == RESULT_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER_PROVISIONING_DISABLED) {
            mController.performPlatformProvidedProvisioning();
        } else if (resultCode
                != RESULT_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER_UNRECOVERABLE_ERROR) {
            boolean isProvisioningStarted = mController.startAppropriateProvisioning(
                    getIntent(),
                    createRoleHolderAdditionalExtras(resultCode),
                    getCallingPackage());
            if (!isProvisioningStarted) {
                if (isRoleHolderUpdaterRequestingPlatformDrivenProvisioning(resultData)) {
                    ProvisionLogger.logi("Result is " + resultCode
                            + " and applied fallback strategy.");
                    mController.performPlatformProvidedProvisioning();
                } else {
                    failRoleHolderUpdate();
                    ProvisionLogger.loge("Failed to start provisioning after a "
                            + "role holder-requested role holder update. Result is "
                            + resultCode + " and allow offline provisioning is "
                            + mController.getParams().allowOffline);
                    SetupMetricsLogger.logMetrics(this, mScreenKey,
                            SetupMetric.ofError(setupMetricScreenName, resultCode));
                }
            }
        } else if (mController.getParams().allowOffline) {
            ProvisionLogger.logi("Result is " + resultCode + ". Allowed offline provisioning.");
            mController.performPlatformProvidedProvisioning();
        } else if (isRoleHolderUpdaterRequestingPlatformDrivenProvisioning(resultData)) {
            ProvisionLogger.logi("Result is " + resultCode + " and applied fallback strategy.");
            mController.performPlatformProvidedProvisioning();
        } else {
            failRoleHolderUpdate();
            ProvisionLogger.loge("Failed to perform a role holder-requested role holder "
                    + "update. Result is " + resultCode + " and allow offline provisioning"
                    + " is " + mController.getParams().allowOffline);
            SetupMetricsLogger.logMetrics(this, mScreenKey,
                    SetupMetric.ofError(setupMetricScreenName, resultCode));
        }
    }

    private boolean isRoleHolderUpdaterRequestingPlatformDrivenProvisioning(
            @Nullable Intent resultData) {
        if (resultData == null) {
            return false;
        }
        return resultData.getIntExtra(
                EXTRA_ROLE_HOLDER_UPDATE_FAILURE_STRATEGY,
                ROLE_HOLDER_UPDATE_FAILURE_STRATEGY_FAIL_PROVISIONING)
                == ROLE_HOLDER_UPDATE_FAILURE_STRATEGY_FALLBACK_TO_PLATFORM_PROVISIONING;
    }

    private void maybeHandleLaunchIntent(int resultCode, Intent data) {
        if (data == null || !data.hasExtra(EXTRA_RESULT_LAUNCH_INTENT)) {
            setResult(resultCode);
            return;
        }

        Intent launchIntent = data.getParcelableExtra(EXTRA_RESULT_LAUNCH_INTENT, Intent.class);
        ProvisionLogger.logi("Role holder returned result intent: " + launchIntent);

        if (!mController.getParams().provisioningShouldLaunchResultIntent) {
            ProvisionLogger.logi("Role Holder result intent to be launched by "
                    + "provisioning initiator");
            setResult(resultCode, launchIntent);
            return;
        }

        ProvisionLogger.logi("Role Holder result intent launched by platform");
        startActivity(launchIntent);
    }

    private void failRoleHolderUpdate() {
        ProvisionLogger.loge("Update failed and offline provisioning is not allowed.");
        SetupMetricsLogger.logMetrics(this, mScreenKey,
                SetupMetric.ofError(setupMetricScreenName, SETUP_METRIC_DEFAULT_ERROR_CODE));
        if (mUtils.isOrganizationOwnedAllowed(mController.getParams())) {
            showFactoryResetDialog(R.string.cant_set_up_device,
                    R.string.contact_your_admin_for_help);
        } else {
            showErrorAndClose(
                    R.string.cant_set_up_device,
                    R.string.contact_your_admin_for_help,
                    "Failed to provision personally-owned device.");
        }
    }

    private void showRoleHolderDownloadFailedDialog(@NonNull Intent data) {
        int dialogTitleResId = data.getIntExtra(
                EXTRA_DIALOG_TITLE_ID,
                R.string.cant_set_up_device);
        int dialogMessageResId = data.getIntExtra(
                EXTRA_ERROR_MESSAGE_RES_ID,
                R.string.contact_your_admin_for_help);
        if (data.getBooleanExtra(EXTRA_FACTORY_RESET_REQUIRED, /* defaultValue= */ false)) {
            showFactoryResetDialog(
                    dialogTitleResId,
                    dialogMessageResId);
        } else {
            showErrorAndClose(
                    dialogTitleResId,
                    dialogMessageResId,
                    "Failed to provision personally-owned device.");
        }
    }

    private boolean isDpcInstalled() {
        String adminPackageName = mController.getParams().inferDeviceAdminPackageName();
        return mUtils.isPackageInstalled(adminPackageName, getPackageManager());
    }

    private void handleAdminIntegratedFlowPreparerResult() {
        if (isDpcInstalled()) {
            startAdminIntegratedFlowPostDpcInstall();
        } else {
            String adminPackageName = mController.getParams().inferDeviceAdminPackageName();
            showErrorAndClose(
                    R.string.cant_set_up_device,
                    R.string.contact_your_admin_for_help,
                    "Package name " + adminPackageName + " is not installed.");
        }
    }

    @Override
    public void showErrorAndClose(Integer titleId, int messageId, String logMessage) {
        showErrorAndClose(LazyStringResource.of(titleId), LazyStringResource.of(messageId),
                logMessage);
    }

    @Override
    public void showErrorAndClose(
            LazyStringResource title, LazyStringResource message, String logMessage) {
        SimpleDialog.Builder dialogBuilder =
                new SimpleDialog.Builder().setTitle(title).setMessage(message);
        setShowErrorAndCloseParams(dialogBuilder, logMessage);
    }

    private void setShowErrorAndCloseParams(SimpleDialog.Builder dialogBuilder, String logText) {
        ProvisionLogger.loge(logText);

        SimpleDialog.Builder builder =
                dialogBuilder
                        .setCancelable(false)
                        .setPositiveButtonMessage(R.string.device_owner_error_ok);
        showDialog(builder, ERROR_AND_CLOSE_DIALOG);
    }

    @Override
    public void onNegativeButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case BACK_PRESSED_DIALOG_CLOSE_ACTIVITY:
            case BACK_PRESSED_DIALOG_RESET:
                // user chose to continue. Do nothing
                break;
            case LAUNCHER_INVALID_DIALOG:
                dialog.dismiss();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    @Override
    public void onPositiveButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case ERROR_AND_CLOSE_DIALOG:
            case BACK_PRESSED_DIALOG_CLOSE_ACTIVITY:
                onProvisioningAborted();
                break;
            case BACK_PRESSED_DIALOG_RESET:
                mUtils.factoryReset(this, "Provisioning cancelled by user on consent screen");
                onProvisioningAborted();
                break;
            case LAUNCHER_INVALID_DIALOG:
                requestLauncherPick();
                break;
            case ERROR_DIALOG_RESET:
                getUtils().factoryReset(this, "Error during preprovisioning");
                setResult(Activity.RESULT_CANCELED);
                getTransitionHelper().finishActivity(this);
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    private void onProvisioningAborted() {
        setResult(Activity.RESULT_CANCELED);
        mController.logPreProvisioningCancelled();
        getTransitionHelper().finishActivity(this);
    }

    @Override
    public void requestEncryption(ProvisioningParams params) {
        Intent encryptIntent = new Intent(this,
                getActivityForScreen(ManagedProvisioningScreens.ENCRYPT));
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), encryptIntent);
        encryptIntent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        getTransitionHelper().startActivityForResultWithTransition(
                this, encryptIntent, ENCRYPT_DEVICE_REQUEST_CODE);
    }

    @Override
    public void requestWifiPick() {
        final Intent intent = mUtils.getWifiPickIntent();
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
        getTransitionHelper()
                .startActivityForResultWithTransition(this, intent, WIFI_REQUEST_CODE);
    }

    @Override
    public void showCurrentLauncherInvalid() {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setCancelable(false)
                .setTitle(R.string.change_device_launcher)
                .setMessage(R.string.launcher_app_cant_be_used_by_work_profile)
                .setNegativeButtonMessage(R.string.cancel_provisioning)
                .setPositiveButtonMessage(R.string.pick_launcher);
        showDialog(dialogBuilder, LAUNCHER_INVALID_DIALOG);
    }

    @Override
    public void abortProvisioning() {
        onProvisioningAborted();
    }

    @Override
    public void prepareAdminIntegratedFlow(ProvisioningParams params) {
        if (AdminIntegratedFlowPrepareActivity.shouldRunPrepareActivity(mUtils, this, params)) {
            Intent intent = new Intent(this,
                    getActivityForScreen(ManagedProvisioningScreens.ADMIN_INTEGRATED_PREPARE));
            WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
            intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
            getTransitionHelper().startActivityForResultWithTransition(
                    this, intent, ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE);
        } else {
            handleAdminIntegratedFlowPreparerResult();
        }
    }

    @Override
    public void startRoleHolderUpdater(boolean isRoleHolderRequestedUpdate) {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                new DeviceManagementRoleHolderUpdaterHelper(
                        mRoleHolderUpdaterProvider.getPackageName(this),
                        RoleHolderProvider.DEFAULT.getPackageName(this),
                        new DefaultPackageInstallChecker(getPackageManager(), mUtils),
                        new DefaultIntentResolverChecker(getPackageManager()),
                        new DefaultFeatureFlagChecker(getContentResolver()));
        Intent intent = new Intent(this, getActivityForScreen(RETRY_LAUNCH));
        intent.putExtra(
                EXTRA_INTENT_TO_LAUNCH,
                roleHolderUpdaterHelper.createRoleHolderUpdaterIntent(
                        getIntent(),
                        getIntent().getIntExtra(
                                EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_UNSPECIFIED),
                        isRoleHolderRequestedUpdate));
        mAnalyticsTracker.logRoleHolderUpdaterUpdateStart();
        getTransitionHelper().startActivityForResultWithTransition(
                this,
                intent,
                isRoleHolderRequestedUpdate
                        ? START_ROLE_HOLDER_REQUESTED_UPDATE_REQUEST_CODE
                        : START_PLATFORM_REQUESTED_ROLE_HOLDER_UPDATE_REQUEST_CODE);
    }

    @Override
    public void startRoleHolderProvisioning(Intent intent) {
        mAnalyticsTracker.logRoleHolderProvisioningStart();
        Intent retryLaunchIntent = new Intent(this, getActivityForScreen(RETRY_LAUNCH));
        retryLaunchIntent.putExtra(RetryLaunchActivity.EXTRA_INTENT_TO_LAUNCH, intent);
        getTransitionHelper().startActivityForResultWithTransition(
                /* activity= */ this,
                retryLaunchIntent,
                START_DEVICE_MANAGEMENT_ROLE_HOLDER_PROVISIONING_REQUEST_CODE);
    }

    @Override
    public void onParamsValidated(ProvisioningParams params) {
        ManagedProvisioningBaseApplication application =
                (ManagedProvisioningBaseApplication) getApplication();
        application.keepScreenOn(this);
    }

    @Override
    public void startPlatformDrivenRoleHolderDownload() {
        mAnalyticsTracker.logPlatformRoleHolderUpdateStart();

        Intent intent;
        if (mFlags.isCosmicRayEnabled()) {
            intent = mDownloadRoleHolderContract.createIntent(
                    this,
                    DownloadRoleHolderArguments.of(
                            mDownloadRoleHolderContract.getSuwArgumentsSerializer().read(
                                    getIntent()),
                            requireNonNull(mController.getParams())));
        } else {
            intent = new Intent(this,
                    getActivityForScreen(ManagedProvisioningScreens.DOWNLOAD_ROLE_HOLDER));
            WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
            intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS,
                    mController.getParams());
        }
        getTransitionHelper().startActivityForResultWithTransition(
                this, intent, DOWNLOAD_DEVICE_MANAGEMENT_ROLE_HOLDER_FROM_PLATFORM_REQUEST_CODE);
    }

    private void requestLauncherPick() {
        Intent changeLauncherIntent = new Intent(Settings.ACTION_HOME_SETTINGS);
        changeLauncherIntent.putExtra(EXTRA_SUPPORT_MANAGED_PROFILES, true);
        getTransitionHelper().startActivityForResultWithTransition(
                this, changeLauncherIntent, CHANGE_LAUNCHER_REQUEST_CODE);
    }

    /**
     * Starts {@link ProvisioningActivity}.
     */
    @Override
    public void startProvisioning(ProvisioningParams params) {
        Intent intent = new Intent(this,
                getActivityForScreen(ManagedProvisioningScreens.PROVISIONING));
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        getTransitionHelper().startActivityForResultWithTransition(
                this, intent, PROVISIONING_REQUEST_CODE);
    }

    // TODO: The below group of methods do not belong in the activity.
    // Move them to the controller instead.

    /**
     * Starts either the admin-integrated or the legacy flow, depending on the device state and
     * DPC capabilities.
     */
    private void startAdminIntegratedFlowPostDpcInstall() {
        boolean canPerformAdminIntegratedFlow = mUtils.canPerformAdminIntegratedFlow(
                this,
                mController.getParams(),
                mController.getPolicyComplianceUtils(),
                mController.getGetProvisioningModeUtils());
        if (canPerformAdminIntegratedFlow) {
            startAdminIntegratedFlowWithoutPredeterminedMode();
        } else {
            ProvisionLogger.loge("The admin app does not have handlers for both "
                    + "ACTION_GET_PROVISIONING_MODE and ACTION_ADMIN_POLICY_COMPLIANCE "
                    + "intent actions.");
            SetupMetricsLogger.logMetrics(this, mScreenKey,
                    SetupMetric.ofError(setupMetricScreenName, SETUP_METRIC_DEFAULT_ERROR_CODE));
            if (mUtils.isOrganizationOwnedAllowed(mController.getParams())) {
                showFactoryResetDialog(R.string.cant_set_up_device,
                        R.string.contact_your_admin_for_help);
            } else {
                showErrorAndClose(
                        R.string.cant_set_up_device,
                        R.string.contact_your_admin_for_help,
                        "Failed provisioning personally-owned device.");
            }
        }
        mController.logProvisioningFlowType();
    }

    private void startAdminIntegratedFlowWithoutPredeterminedMode() {
        ProvisionLogger.logi("Starting the admin-integrated flow.");
        GetProvisioningModeUtils provisioningModeUtils = mController.getGetProvisioningModeUtils();
        Bundle additionalExtras = mController.getAdditionalExtrasForGetProvisioningModeIntent();
        provisioningModeUtils.startGetProvisioningModeActivityIfResolved(
                this, mController.getParams(), additionalExtras,
                GET_PROVISIONING_MODE_REQUEST_CODE, getTransitionHelper());
    }

    private void startFinancedDeviceFlow() {
        ProvisionLogger.logi("Starting the financed device flow.");
        mController.updateProvisioningFlowState(FLOW_TYPE_LEGACY);
        mController.continueProvisioningAfterUserConsent();
    }

    @Override
    public void showFactoryResetDialog(Integer titleId, int messageId) {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setTitle(titleId)
                .setMessage(messageId)
                .setCancelable(false)
                .setPositiveButtonMessage(R.string.reset);

        showDialog(dialogBuilder, ERROR_DIALOG_RESET);
    }

    @Override
    public void initiateUi(UiParams uiParams) {
        mBridge.initiateUi(uiParams);
    }

    @Override
    public void showOwnershipDisclaimerScreen(ProvisioningParams params) {
        Intent intent = new Intent(this,
                getActivityForScreen(ManagedProvisioningScreens.LANDING));
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        getTransitionHelper().startActivityForResultWithTransition(
                this, intent, ORGANIZATION_OWNED_LANDING_PAGE_REQUEST_CODE);
    }

    @Override
    public void prepareFinancedDeviceFlow(ProvisioningParams params) {
        Intent intent = new Intent(this,
                getActivityForScreen(ManagedProvisioningScreens.FINANCED_DEVICE_LANDING));
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        getTransitionHelper().startActivityForResultWithTransition(
                this, intent, FINANCED_DEVICE_PREPARE_REQUEST_CODE);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v instanceof TextView) {
            mContextMenuMaker.populateMenuContent(menu, (TextView) v);
        }
    }

    @Override
    public void onBackPressed() {
        if (mUtils.isOrganizationOwnedAllowed(mController.getParams())) {
            showDialog(mUtils.createCancelProvisioningResetDialogBuilder(getApplicationContext()),
                    BACK_PRESSED_DIALOG_RESET);
        } else {
            showDialog(mUtils.createCancelProvisioningDialogBuilder(),
                    BACK_PRESSED_DIALOG_CLOSE_ACTIVITY);
        }
    }

    private void logMetrics() {
        int nightMode = getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK;
        mAnalyticsTracker.logIsNightMode(nightMode == UI_MODE_NIGHT_YES);
    }

    /**
     * Constructs {@link PreProvisioningActivityController} for a given {@link
     * PreProvisioningActivity}
     */
    interface ControllerProvider {
        /**
         * Constructs {@link PreProvisioningActivityController} for a given {@link
         * PreProvisioningActivity}
         */
        PreProvisioningActivityController getInstance(PreProvisioningActivity activity);
    }
}
