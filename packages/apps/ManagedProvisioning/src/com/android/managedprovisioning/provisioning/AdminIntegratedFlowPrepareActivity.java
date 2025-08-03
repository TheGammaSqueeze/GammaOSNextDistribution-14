/*
 * Copyright 2019, The Android Open Source Project
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
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupcompat.logging.ScreenKey;
import com.google.android.setupcompat.logging.SetupMetric;
import com.google.android.setupcompat.logging.SetupMetricsLogger;
import com.google.android.setupdesign.util.DeviceHelper;

/**
 * Progress activity shown whilst network setup, downloading, verifying and installing the
 * admin package is ongoing.
 *
 * <p>This activity registers for updates of the provisioning process from the
 * {@link AdminIntegratedFlowPrepareManager}. It shows progress updates as provisioning
 * progresses and handles showing of cancel and error dialogs.</p>
 */
public class AdminIntegratedFlowPrepareActivity extends AbstractProvisioningActivity {

    private static final String SETUP_METRIC_ADMIN_INTEGRATED_SCREEN_NAME =
            "ShowAdminIntegratedFlowScreen";
    private AdminIntegratedFlowPrepareManager mProvisioningManager;

    public AdminIntegratedFlowPrepareActivity() {
        this(new Utils(), new SettingsFacade(),
                new ThemeHelper(new DefaultNightModeChecker(), new DefaultSetupWizardBridge()));
    }

    public static boolean shouldRunPrepareActivity(
            Utils utils, Context context, ProvisioningParams params) {
        // TODO(b/177849035): Remove NFC-specific logic
        if (params.isNfc) {
            return false;
        }
        if (params.wifiInfo != null) {
            return true;
        }
        if (params.useMobileData) {
            return true;
        }
        if (params.deviceAdminDownloadInfo != null) {
            // Only prepare if a download is actually required
            if (utils.packageRequiresUpdate(
                    params.inferDeviceAdminPackageName(),
                    params.deviceAdminDownloadInfo.minVersion,
                    context)) {
                return true;
            }
        }
        return false;
    }

    @VisibleForTesting
    protected AdminIntegratedFlowPrepareActivity(
            Utils utils, SettingsFacade settingsFacade, ThemeHelper themeHelper) {
        super(utils, settingsFacade, themeHelper);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupMetricScreenName = SETUP_METRIC_ADMIN_INTEGRATED_SCREEN_NAME;
        mScreenKey = ScreenKey.of(setupMetricScreenName, this);
        SetupMetricsLogger.logMetrics(this, mScreenKey,
                SetupMetric.ofImpression(setupMetricScreenName));
        initializeUi();
    }

    @Override
    protected ProvisioningManagerInterface getProvisioningManager() {
        if (mProvisioningManager == null) {
            mProvisioningManager = AdminIntegratedFlowPrepareManager.getInstance(this);
        }
        return mProvisioningManager;

    }

    @Override
    public void preFinalizationCompleted() {
        ProvisionLogger.logi("AdminIntegratedFlowPrepareActivity pre-finalization completed");
        setResult(Activity.RESULT_OK);
        this.overridePendingTransition(
                com.google.android.setupdesign.R.anim.sud_slide_next_in,
                com.google.android.setupdesign.R.anim.sud_slide_next_out);
        this.finish();
    }

    @Override
    protected void decideCancelProvisioningDialog() {
        if (getUtils().isDeviceOwnerAction(mParams.provisioningAction)
                || getUtils().isOrganizationOwnedAllowed(mParams)) {
            showCancelProvisioningDialog(/* resetRequired= */ true);
        } else {
            showCancelProvisioningDialog(/* resetRequired= */ false);
        }
    }


    protected void initializeUi() {
        final int headerResId = R.string.downloading_administrator_header;
        CharSequence deviceName = DeviceHelper.getDeviceName(getApplicationContext());
        final String title = getString(R.string.setup_device_progress, deviceName);
        initializeLayoutParams(R.layout.empty_loading_layout, headerResId);
        setTitle(title);
    }

    @Override
    protected boolean isWaitingScreen() {
        return true;
    }
}
