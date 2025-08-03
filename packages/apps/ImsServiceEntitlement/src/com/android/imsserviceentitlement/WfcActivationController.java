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

package com.android.imsserviceentitlement;

import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__CANCELED;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__DISABLED;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__FAILED;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__INCOMPATIBLE;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__SUCCESSFUL;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__TIMEOUT;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__UNEXPECTED_RESULT;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__UNKNOWN_RESULT;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__PURPOSE__ACTIVATION;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__PURPOSE__UPDATE;
import static com.android.imsserviceentitlement.ImsServiceEntitlementStatsLog.IMS_SERVICE_ENTITLEMENT_UPDATED__SERVICE_TYPE__VOWIFI;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.imsserviceentitlement.entitlement.EntitlementResult;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus;
import com.android.imsserviceentitlement.utils.ImsUtils;
import com.android.imsserviceentitlement.utils.MetricsLogger;
import com.android.imsserviceentitlement.utils.TelephonyUtils;

import com.google.common.annotations.VisibleForTesting;

import java.time.Duration;

/**
 * The driver for WFC activation workflow: go/vowifi-entitlement-status-analysis.
 *
 * <p>One {@link WfcActivationActivity} owns one and only one controller instance.
 */
public class WfcActivationController {
    private static final String TAG = "IMSSE-WfcActivationController";

    // Entitlement status update retry
    private static final int ENTITLEMENT_STATUS_UPDATE_RETRY_MAX = 6;
    private static final long ENTITLEMENT_STATUS_UPDATE_RETRY_INTERVAL_MS =
            Duration.ofSeconds(5).toMillis();

    // Dependencies
    private final WfcActivationUi mActivationUi;
    private final TelephonyUtils mTelephonyUtils;
    private final ImsEntitlementApi mImsEntitlementApi;
    private final ImsUtils mImsUtils;
    private final Intent mStartIntent;
    private final MetricsLogger mMetricsLogger;

    // States
    private int mEvaluateTimes = 0;
    private int mAppResult = IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__UNKNOWN_RESULT;

    @MainThread
    public WfcActivationController(
            Context context,
            WfcActivationUi wfcActivationUi,
            ImsEntitlementApi imsEntitlementApi,
            Intent intent) {
        this.mStartIntent = intent;
        this.mActivationUi = wfcActivationUi;
        this.mImsEntitlementApi = imsEntitlementApi;
        this.mTelephonyUtils = new TelephonyUtils(context, getSubId());
        this.mImsUtils = ImsUtils.getInstance(context, getSubId());
        this.mMetricsLogger = new MetricsLogger(mTelephonyUtils);
    }

    @VisibleForTesting
    WfcActivationController(
            Context context,
            WfcActivationUi wfcActivationUi,
            ImsEntitlementApi imsEntitlementApi,
            Intent intent,
            ImsUtils imsUtils,
            MetricsLogger metricsLogger) {
        this.mStartIntent = intent;
        this.mActivationUi = wfcActivationUi;
        this.mImsEntitlementApi = imsEntitlementApi;
        this.mTelephonyUtils = new TelephonyUtils(context, getSubId());
        this.mImsUtils = imsUtils;
        this.mMetricsLogger = metricsLogger;
    }

    /** Indicates the controller to start WFC activation or emergency address update flow. */
    @MainThread
    public void startFlow() {
        showGeneralWaitingUi();
        evaluateEntitlementStatus();
        if (isActivationFlow()) {
            mMetricsLogger.start(IMS_SERVICE_ENTITLEMENT_UPDATED__PURPOSE__ACTIVATION);
        } else {
            mMetricsLogger.start(IMS_SERVICE_ENTITLEMENT_UPDATED__PURPOSE__UPDATE);
        }
    }

    /** Evaluates entitlement status for activation or update. */
    @MainThread
    public void evaluateEntitlementStatus() {
        if (!mTelephonyUtils.isNetworkConnected()) {
            handleInitialEntitlementStatus(null);
            return;
        }
        EntitlementUtils.entitlementCheck(
                mImsEntitlementApi, result -> handleInitialEntitlementStatus(result));
    }

    /**
     * Indicates the controller to re-evaluate WFC entitlement status after activation flow finished
     * successfully (ie. not canceled) by user.
     */
    @MainThread
    public void finishFlow() {
        showGeneralWaitingUi();
        reevaluateEntitlementStatus();
    }

    /** Re-evaluate entitlement status after updating. */
    @MainThread
    public void reevaluateEntitlementStatus() {
        EntitlementUtils.entitlementCheck(
                mImsEntitlementApi, result -> handleReevaluationEntitlementStatus(result));
    }

    /** The interface for handling the entitlement check result. */
    public interface EntitlementResultCallback {
        void onEntitlementResult(EntitlementResult result);
    }

    /** Indicates the controller to finish on-going tasks and get ready to be destroyed. */
    @MainThread
    public void finish() {
        EntitlementUtils.cancelEntitlementCheck();

        // If no result set, it must be cancelled by user pressing back button.
        if (mAppResult == IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__UNKNOWN_RESULT) {
            mAppResult = IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__CANCELED;
        }
        mMetricsLogger.write(IMS_SERVICE_ENTITLEMENT_UPDATED__SERVICE_TYPE__VOWIFI, mAppResult);
    }

    /**
     * Returns {@code true} if the app is launched for WFC activation; {@code false} for emergency
     * address update.
     */
    private boolean isActivationFlow() {
        return ActivityConstants.isActivationFlow(mStartIntent);
    }

    private int getSubId() {
        return ActivityConstants.getSubId(mStartIntent);
    }

    /** Returns UI title string resource ID based on {@link #isActivationFlow()}. */
    @StringRes
    private int getUiTitle() {
        int intention = ActivityConstants.getLaunchIntention(mStartIntent);
        if (intention == ActivityConstants.LAUNCH_APP_ACTIVATE) {
            return R.string.activate_title;
        }
        if (intention == ActivityConstants.LAUNCH_APP_SHOW_TC) {
            return R.string.tos_title;
        }
        // LAUNCH_APP_UPDATE or otherwise
        return R.string.e911_title;
    }

    /** Returns general error string resource ID based on {@link #isActivationFlow()}. */
    @StringRes
    private int getGeneralErrorText() {
        int intention = ActivityConstants.getLaunchIntention(mStartIntent);
        if (intention == ActivityConstants.LAUNCH_APP_ACTIVATE) {
            return R.string.wfc_activation_error;
        } else if (intention == ActivityConstants.LAUNCH_APP_SHOW_TC) {
            return R.string.show_terms_and_condition_error;
        }
        // LAUNCH_APP_UPDATE or otherwise
        return R.string.address_update_error;
    }

    private void showErrorUi(@StringRes int errorMessage) {
        mActivationUi.showActivationUi(
                getUiTitle(), errorMessage, false, R.string.ok, WfcActivationUi.RESULT_FAILURE, 0);
    }

    private void showGeneralErrorUi() {
        showErrorUi(getGeneralErrorText());
    }

    private void showGeneralWaitingUi() {
        mActivationUi.showActivationUi(getUiTitle(), R.string.progress_text, true, 0, 0, 0);
    }

    @MainThread
    private void handleInitialEntitlementStatus(@Nullable EntitlementResult result) {
        Log.d(TAG, "Initial entitlement result: " + result);
        if (result == null) {
            showGeneralErrorUi();
            finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__FAILED);
            return;
        }
        if (isActivationFlow()) {
            handleEntitlementStatusForActivation(result);
        } else {
            handleEntitlementStatusForUpdating(result);
        }
    }

    @MainThread
    private void handleEntitlementStatusForActivation(EntitlementResult result) {
        Ts43VowifiStatus vowifiStatus = result.getVowifiStatus();
        if (vowifiStatus.vowifiEntitled()) {
            finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__SUCCESSFUL);
            mActivationUi.setResultAndFinish(Activity.RESULT_OK);
        } else {
            if (vowifiStatus.serverDataMissing()) {
                if (!TextUtils.isEmpty(result.getTermsAndConditionsWebUrl())) {
                    mActivationUi.showWebview(
                            result.getTermsAndConditionsWebUrl(), /* postData= */ null);
                } else {
                    mActivationUi.showWebview(
                            result.getEmergencyAddressWebUrl(),
                            result.getEmergencyAddressWebData());
                }
            } else if (vowifiStatus.incompatible()) {
                finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__INCOMPATIBLE);
                showErrorUi(R.string.failure_contact_carrier);
            } else {
                Log.e(TAG, "Unexpected status. Show error UI.");
                finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__UNEXPECTED_RESULT);
                showGeneralErrorUi();
            }
        }
    }

    @MainThread
    private void handleEntitlementStatusForUpdating(EntitlementResult result) {
        Ts43VowifiStatus vowifiStatus = result.getVowifiStatus();
        if (vowifiStatus.vowifiEntitled()) {
            int launchIntention = ActivityConstants.getLaunchIntention(mStartIntent);
            if (launchIntention == ActivityConstants.LAUNCH_APP_SHOW_TC) {
                mActivationUi.showWebview(
                        result.getTermsAndConditionsWebUrl(), /* postData= */ null);
            } else {
                mActivationUi.showWebview(
                        result.getEmergencyAddressWebUrl(), result.getEmergencyAddressWebData());
            }
        } else {
            if (vowifiStatus.incompatible()) {
                showErrorUi(R.string.failure_contact_carrier);
                mImsUtils.turnOffWfc(
                        () -> finishStatsLog(
                                IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__INCOMPATIBLE)
                );
            } else {
                Log.e(TAG, "Unexpected status. Show error UI.");
                finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__UNEXPECTED_RESULT);
                showGeneralErrorUi();
            }
        }
    }

    @MainThread
    private void handleReevaluationEntitlementStatus(@Nullable EntitlementResult result) {
        Log.d(TAG, "Reevaluation entitlement result: " + result);
        if (result == null) { // Network issue
            showGeneralErrorUi();
            finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__FAILED);
            return;
        }
        if (isActivationFlow()) {
            handleEntitlementStatusAfterActivation(result);
        } else {
            handleEntitlementStatusAfterUpdating(result);
        }
    }

    @MainThread
    private void handleEntitlementStatusAfterActivation(EntitlementResult result) {
        Ts43VowifiStatus vowifiStatus = result.getVowifiStatus();
        if (vowifiStatus.vowifiEntitled()) {
            mActivationUi.setResultAndFinish(Activity.RESULT_OK);
            finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__SUCCESSFUL);
        } else {
            if (vowifiStatus.serverDataMissing()) {
                // Check again after 5s, max retry 6 times
                if (mEvaluateTimes < ENTITLEMENT_STATUS_UPDATE_RETRY_MAX) {
                    mEvaluateTimes += 1;
                    postDelay(
                            getEntitlementStatusUpdateRetryIntervalMs(),
                            this::reevaluateEntitlementStatus);
                } else {
                    mEvaluateTimes = 0;
                    showGeneralErrorUi();
                    finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__TIMEOUT);
                }
            } else {
                // These should never happen, but nothing else we can do. Show general error.
                Log.e(TAG, "Unexpected status. Show error UI.");
                showGeneralErrorUi();
                finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__UNEXPECTED_RESULT);
            }
        }
    }

    private long getEntitlementStatusUpdateRetryIntervalMs() {
        return ENTITLEMENT_STATUS_UPDATE_RETRY_INTERVAL_MS;
    }

    @MainThread
    private void handleEntitlementStatusAfterUpdating(EntitlementResult result) {
        Ts43VowifiStatus vowifiStatus = result.getVowifiStatus();
        if (vowifiStatus.vowifiEntitled()) {
            mActivationUi.setResultAndFinish(Activity.RESULT_OK);
            finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__SUCCESSFUL);
        } else if (vowifiStatus.serverDataMissing()) {
            // Some carrier allows de-activating in updating flow.
            mImsUtils.turnOffWfc(
                    () -> {
                        finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__DISABLED);
                        mActivationUi.setResultAndFinish(Activity.RESULT_OK);
                    });
        } else {
            Log.e(TAG, "Unexpected status. Show error UI.");
            showGeneralErrorUi();
            finishStatsLog(IMS_SERVICE_ENTITLEMENT_UPDATED__APP_RESULT__UNEXPECTED_RESULT);
        }
    }

    /** Runs {@code action} on caller's thread after {@code delayMillis} ms. */
    private static void postDelay(long delayMillis, Runnable action) {
        new CountDownTimer(delayMillis, delayMillis + 100) {
            // Use a countDownInterval bigger than millisInFuture so onTick never fires.
            @Override
            public void onTick(long millisUntilFinished) {
                // Do nothing
            }

            @Override
            public void onFinish() {
                action.run();
            }
        }.start();
    }

    private void finishStatsLog(int result) {
        mAppResult = result;
    }
}
