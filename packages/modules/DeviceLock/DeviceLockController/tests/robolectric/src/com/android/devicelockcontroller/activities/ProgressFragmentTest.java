/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.devicelockcontroller.activities;

import static com.android.devicelockcontroller.common.DeviceLockConstants.MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.COUNTRY_INFO_UNAVAILABLE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.DEADLINE_PASSED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.NOT_IN_ELIGIBLE_COUNTRY;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.PLAY_INSTALLATION_FAILED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.PLAY_TASK_UNAVAILABLE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.POLICY_ENFORCEMENT_FAILED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.UNKNOWN_REASON;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_FAILURE;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker.KEY_PROVISION_FAILURE_REASON;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Html;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.ProvisionHelper;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker;

import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDrawable;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


@RunWith(ParameterizedRobolectricTestRunner.class)
public final class ProgressFragmentTest {

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    private static final String TEST_PROVIDER = "Test Provider";
    private static final String PROGRESS_FRAGMENT_TAG = "ProgressFragment";
    private static final String TEST_URL = "test.url";
    @ParameterizedRobolectricTestRunner.Parameter
    public ProvisioningProgress mProvisioningProgress;
    @Mock
    private ProvisionHelper mMockProvisionHelper;

    /** Get the input parameters of the test. */
    @ParameterizedRobolectricTestRunner.Parameters(name = "ProvisioningProgress is {0}")
    public static List<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
                {ProvisioningProgress.GETTING_DEVICE_READY},
                {ProvisioningProgress.INSTALLING_KIOSK_APP},
                {ProvisioningProgress.OPENING_KIOSK_APP},
                {ProvisioningProgress.getMandatoryProvisioningFailedProgress(
                        POLICY_ENFORCEMENT_FAILED)},
                {ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                        PLAY_TASK_UNAVAILABLE)},
                {ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                        PLAY_INSTALLATION_FAILED)},
                {ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                        COUNTRY_INFO_UNAVAILABLE)},
                {ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                        NOT_IN_ELIGIBLE_COUNTRY)},
                {ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                        POLICY_ENFORCEMENT_FAILED)},
                {ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                        DEADLINE_PASSED)}
        });
    }

    @Test
    public void onCreateView_viewsAndListenersShouldBeCorrectlySet()
            throws ExecutionException, InterruptedException {
        TestDeviceLockControllerApplication applicationContext =
                ApplicationProvider.getApplicationContext();

        WorkManagerTestInitHelper.initializeTestWorkManager(applicationContext,
                new Configuration.Builder().setWorkerFactory(getWorkerFactory()).build());

        ProvisionStateController provisionStateController =
                applicationContext.getProvisionStateController();
        when(provisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionStateController.ProvisionState.PROVISION_IN_PROGRESS));

        ActivityController<EmptyTestFragmentActivity> activityController =
                Robolectric.buildActivity(
                        EmptyTestFragmentActivity.class);
        EmptyTestFragmentActivity activity = activityController.get();
        activity.setFragment(new ProgressFragment(mMockProvisionHelper));
        activity.setFragmentTag(PROGRESS_FRAGMENT_TAG);

        activityController.setup();

        ProvisioningProgressViewModel provisioningProgressViewModel =
                new ViewModelProvider(activity).get(ProvisioningProgressViewModel.class);
        provisioningProgressViewModel.mProviderNameLiveData.setValue(TEST_PROVIDER);
        provisioningProgressViewModel.mSupportUrlLiveData.setValue(TEST_URL);
        provisioningProgressViewModel.setProvisioningProgress(mProvisioningProgress);

        ShadowLooper.runUiThreadTasks();

        // Check Header Icon
        if (mProvisioningProgress.mIconId != 0) {
            ShadowDrawable drawable = Shadows.shadowOf(((ImageView) activity.findViewById(
                    R.id.header_icon)).getDrawable());
            assertThat(drawable.getCreatedFromResId()).isEqualTo(
                    mProvisioningProgress.mIconId);
        }

        // Check header text
        if (mProvisioningProgress.mHeaderId != 0) {
            CharSequence actual = ((TextView) activity.findViewById(R.id.header_text)).getText();
            Truth.assertThat(actual).isEqualTo(activity.getString(mProvisioningProgress.mHeaderId,
                    provisioningProgressViewModel.mProviderNameLiveData.getValue()));
        }

        // Check sub-header text
        if (mProvisioningProgress.mSubheaderId != 0) {
            TextView subHeaderView = activity.findViewById(
                    R.id.subheader_text);
            CharSequence actualSubHeaderText = subHeaderView.getText();
            SpannableString actualUrlSubHeader = new SpannableString(
                    Html.fromHtml(String.valueOf(actualSubHeaderText),
                            Html.FROM_HTML_MODE_COMPACT));
            URLSpan[] spans = actualUrlSubHeader.getSpans(/* queryStart= */ 0,
                    actualUrlSubHeader.length(), URLSpan.class);

            assertThat(spans.length).isLessThan(2);
            if (spans.length == 1) {
                spans[0].onClick(subHeaderView);
                ShadowActivity shadowActivity = Shadows.shadowOf(activity);
                Intent next = shadowActivity.getNextStartedActivity();
                assertThat(next.getClass()).isEqualTo(HelpActivity.class);
                assertThat(next.getStringExtra(HelpActivity.EXTRA_URL_PARAM)).isEqualTo(TEST_URL);
            }
        }

        // Check progress bar visibility
        assertThat(activity.findViewById(R.id.progress_bar).getVisibility()).isEqualTo(
                mProvisioningProgress.mProgressBarVisible ? View.VISIBLE : View.GONE);

        // Check bottom views
        View bottomView = activity.findViewById(R.id.bottom);
        if (mProvisioningProgress.mBottomViewVisible) {
            assertThat(bottomView.getVisibility()).isEqualTo(View.VISIBLE);

            ((Button) activity.findViewById(R.id.button_retry)).callOnClick();
            verify(mMockProvisionHelper).scheduleKioskAppInstallation(any(), any(), eq(false));

            ((Button) activity.findViewById(R.id.button_exit)).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            verify(provisionStateController).postSetNextStateForEventRequest(eq(PROVISION_FAILURE));
            WorkManager workManager = WorkManager.getInstance(applicationContext);
            List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork(
                    REPORT_PROVISION_STATE_WORK_NAME).get();
            assertThat(workInfos.size()).isEqualTo(1);
        } else {
            assertThat(bottomView.getVisibility()).isEqualTo(View.GONE);
        }

        // Check count down timer
        Chronometer countDownTimer = activity.findViewById(R.id.countdown_text);
        if (mProvisioningProgress.mCountDownTimerVisible) {
            assertThat(countDownTimer.getVisibility()).isEqualTo(
                    View.VISIBLE);
            assertThat(countDownTimer.getBase()).isEqualTo(
                    SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(
                            MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE));
        } else {
            assertThat(countDownTimer.getVisibility()).isEqualTo(View.GONE);
        }
    }

    @NonNull
    private WorkerFactory getWorkerFactory() {
        return new WorkerFactory() {
            @Nullable
            @Override
            public ListenableWorker createWorker(@NonNull Context appContext,
                    @NonNull String workerClassName,
                    @NonNull WorkerParameters workerParameters) {
                if (workerClassName.equals(
                        ReportDeviceProvisionStateWorker.class.getName())) {
                    return new ListenableWorker(appContext, workerParameters) {
                        @NonNull
                        @Override
                        public ListenableFuture<Result> startWork() {
                            Data input = getInputData();
                            assertThat(input.getInt(KEY_PROVISION_FAILURE_REASON,
                                    UNKNOWN_REASON)).isEqualTo(
                                    mProvisioningProgress.mFailureReason);
                            return Futures.immediateFuture(Result.success());
                        }
                    };
                } else {
                    return null;
                }
            }
        };
    }

}
