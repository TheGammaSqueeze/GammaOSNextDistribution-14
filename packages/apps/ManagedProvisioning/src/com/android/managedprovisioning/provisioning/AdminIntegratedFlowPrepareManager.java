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

package com.android.managedprovisioning.provisioning;

import static android.stats.devicepolicy.DevicePolicyEnums.PROVISIONING_PREPARE_TOTAL_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker.CANCELLED_DURING_PROVISIONING_PREPARE;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.os.UserHandle;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupcompat.logging.ScreenKey;
import com.google.android.setupcompat.logging.SetupMetric;
import com.google.android.setupcompat.logging.SetupMetricsLogger;

import java.util.Objects;

/**
 * Singleton instance that provides communications between the ongoing admin integrated flow
 * preparing process and the UI layer.
 */
// TODO(b/123288153): Rearrange provisioning activity, manager, controller classes.
class AdminIntegratedFlowPrepareManager implements ProvisioningControllerCallback,
        ProvisioningManagerInterface {

    private static AdminIntegratedFlowPrepareManager sInstance;

    private final Context mContext;
    private final ProvisioningManagerHelper mHelper;
    private final ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;
    private final TimeLogger mTimeLogger;
    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;
    private final ScreenKey mScreenKey;
    private static String setupMetricScreenName = "AdminIntegratedProvisioningScreen";
    private static String setupMetricProvisioningStartedName = "AdminIntegratedProvisioningStarted";
    private static String setupMetricProvisioningEndedName = "AdminIntegratedProvisioningEnded";

    @GuardedBy("this")
    private AbstractProvisioningController mController;

    static AdminIntegratedFlowPrepareManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AdminIntegratedFlowPrepareManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private AdminIntegratedFlowPrepareManager(Context context) {
        this(
                context,
                new ProvisioningManagerHelper(),
                new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(context, new SettingsFacade()),
                        new ManagedProvisioningSharedPreferences(context)),
                new TimeLogger(context, PROVISIONING_PREPARE_TOTAL_TIME_MS),
                new Utils(),
                new SettingsFacade());
    }

    @VisibleForTesting
    AdminIntegratedFlowPrepareManager(
            Context context,
            ProvisioningManagerHelper helper,
            ProvisioningAnalyticsTracker analyticsTracker,
            TimeLogger timeLogger,
            Utils utils,
            SettingsFacade settingsFacade) {
        mContext = requireNonNull(context);
        mHelper = requireNonNull(helper);
        mProvisioningAnalyticsTracker = requireNonNull(analyticsTracker);
        mTimeLogger = requireNonNull(timeLogger);
        mUtils = requireNonNull(utils);
        mSettingsFacade = requireNonNull(settingsFacade);
        mScreenKey = ScreenKey.of(setupMetricScreenName, context);
    }

    @Override
    public void maybeStartProvisioning(ProvisioningParams params) {
        synchronized (this) {
            if (mController == null) {
                mController = getController(params);
                mHelper.startNewProvisioningLocked(mController);

                SetupMetricsLogger.logMetrics(mContext, mScreenKey,
                        SetupMetric.ofWaitingStart(setupMetricProvisioningStartedName));
                mTimeLogger.start();

                mProvisioningAnalyticsTracker.logProvisioningPrepareStarted();
            } else {
                ProvisionLogger.loge("Trying to start admin integrated flow preparing, "
                        + "but it's already running");
            }
        }
    }

    @Override
    public void registerListener(ProvisioningManagerCallback callback) {
        mHelper.registerListener(callback);
    }

    @Override
    public void unregisterListener(ProvisioningManagerCallback callback) {
        mHelper.unregisterListener(callback);
    }

    @Override
    public void cancelProvisioning() {
        final boolean prepareCancelled = mHelper.cancelProvisioning(mController);
        if (prepareCancelled) {
            mProvisioningAnalyticsTracker.logProvisioningCancelled(mContext,
                    CANCELLED_DURING_PROVISIONING_PREPARE);
        }
    }

    @Override
    public void provisioningTasksCompleted() {
        mTimeLogger.stop();
        preFinalizationCompleted();
    }

    @Override
    public void preFinalizationCompleted() {
        synchronized (this) {
            mHelper.notifyPreFinalizationCompleted();
            mProvisioningAnalyticsTracker.logProvisioningPrepareCompleted();
            clearControllerLocked();
            ProvisionLogger.logi("AdminIntegratedFlowPrepareManager pre-finalization completed");
            SetupMetricsLogger.logMetrics(mContext, mScreenKey,
                    SetupMetric.ofWaitingEnd(setupMetricProvisioningEndedName));
        }
    }

    @Override
    public void cleanUpCompleted() {
        synchronized (this) {
            clearControllerLocked();
        }
    }

    @Override
    public void error(int titleId, int messageId, boolean factoryResetRequired) {
        mHelper.error(titleId, messageId, factoryResetRequired);
    }

    @Override
    public void error(int titleId, String message, boolean factoryResetRequired) {
        mHelper.error(titleId, message, factoryResetRequired);
    }

    private AbstractProvisioningController getController(ProvisioningParams params) {
        return AdminIntegratedFlowPrepareController.createInstance(
                mContext,
                params,
                UserHandle.myUserId(),
                this,
                mUtils,
                mSettingsFacade);
    }

    private void clearControllerLocked() {
        mController = null;
        mHelper.clearResourcesLocked();
    }
}
