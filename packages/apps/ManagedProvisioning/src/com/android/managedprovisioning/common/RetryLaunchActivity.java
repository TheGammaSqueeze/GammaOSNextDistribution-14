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

package com.android.managedprovisioning.common;

import static java.util.Objects.requireNonNull;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.RetryLaunchViewModel.Config;
import com.android.managedprovisioning.common.RetryLaunchViewModel.LaunchActivityEvent;
import com.android.managedprovisioning.common.RetryLaunchViewModel.RetryLaunchViewModelFactory;

/**
 * An {@link Activity} which tries to start the {@link #EXTRA_INTENT_TO_LAUNCH} intent
 * {@link #EXTRA_MAX_RETRIES} times every {@link #EXTRA_RETRY_PERIOD_MS} milliseconds.
 *
 * <p>This {@link Activity} is meant to be used in cases where there is a possibility the {@link
 * #EXTRA_INTENT_TO_LAUNCH} intent may not be available the first time, for example if the app
 * that resolves the {@link Intent} is getting updated.
 *
 * <p>This {@link Activity} forwards the result code of the {@link Activity} that was resolved from
 * the {@link #EXTRA_INTENT_TO_LAUNCH}. Upon failure to launch the {@link Intent}, this {@link
 * Activity} returns {@link #RESULT_CANCELED}.
 */
public final class RetryLaunchActivity extends SetupGlifLayoutActivity {

    /**
     * A {@link Parcelable} extra describing the {@link Intent} to be launched.
     *
     * <p>This is a required extra. Not supplying it will throw an {@link IllegalStateException}.
     */
    public static final String EXTRA_INTENT_TO_LAUNCH =
            "com.android.managedprovisioning.extra.INTENT_TO_LAUNCH";

    /**
     * An {@code int} extra which determines how many times to retry the activity relaunch.
     *
     * <p>Must be a non-negative number.
     *
     * <p>Default value is {@code 3}.
     */
    public static final String EXTRA_MAX_RETRIES =
            "com.android.managedprovisioning.extra.MAX_RETRIES";

    /**
     * A {@code long} extra which determines how long to wait between each retry, in milliseconds.
     *
     * <p>Must be a non-negative number.
     *
     * <p>Default value is {@code 60000} (one minute).
     */
    public static final String EXTRA_RETRY_PERIOD_MS =
            "com.android.managedprovisioning.extra.RETRY_PERIOD_MS";

    private static final int DEFAULT_MAX_RETRIES = 3;

    private static final long DEFAULT_RETRY_PERIOD_MS = 60_000L;

    private static final int LAUNCH_ACTIVITY_REQUEST_CODE = 1;

    private RetryLaunchViewModel mViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getIntent().hasExtra(EXTRA_INTENT_TO_LAUNCH)) {
            throw new IllegalStateException("EXTRA_INTENT must be supplied.");
        }
        Intent activityIntent = getIntent().getParcelableExtra(EXTRA_INTENT_TO_LAUNCH);
        requireNonNull(activityIntent);

        Config config = createConfigFromIntent(getIntent());
        mViewModel = createViewModelWithIntent(activityIntent, config);
        setupViewModelObservation();

        if (savedInstanceState == null) {
            mViewModel.tryStartActivity();
        }
    }

    private Config createConfigFromIntent(Intent intent) {
        final int maxRetries = getMaxRetries(intent);
        final long retryPeriodMs = getRetryPeriodMs(intent);
        return new Config() {
            @Override
            public long getLaunchActivityRetryMillis() {
                return retryPeriodMs;
            }

            @Override
            public int getLaunchActivityMaxRetries() {
                return maxRetries;
            }
        };
    }

    private int getMaxRetries(Intent intent) {
        int maxRetries = intent.getIntExtra(EXTRA_MAX_RETRIES, DEFAULT_MAX_RETRIES);
        if (maxRetries < 0) {
            ProvisionLogger.loge("Invalid value passed for " + EXTRA_MAX_RETRIES + ". Expected a "
                    + "non-negative value but got " + maxRetries);
            maxRetries = DEFAULT_MAX_RETRIES;
        }
        return maxRetries;
    }

    private long getRetryPeriodMs(Intent intent) {
        long retryPeriodMs = intent.getLongExtra(EXTRA_RETRY_PERIOD_MS, DEFAULT_RETRY_PERIOD_MS);
        if (retryPeriodMs < 0) {
            ProvisionLogger.loge("Invalid value passed for " + EXTRA_RETRY_PERIOD_MS + ". Expected "
                    + "a non-negative value but got " + retryPeriodMs);
            retryPeriodMs = DEFAULT_RETRY_PERIOD_MS;
        }
        return retryPeriodMs;
    }

    private void setupViewModelObservation() {
        mViewModel.observeViewModelEvents().observe(this, viewModelEvent -> {
            switch (viewModelEvent.getType()) {
                case RetryLaunchViewModel.VIEW_MODEL_EVENT_LAUNCH_ACTIVITY:
                    if (!mViewModel.isWaitingForActivityResult()) {
                        launchActivity(((LaunchActivityEvent) viewModelEvent).getIntent());
                        mViewModel.markWaitingForActivityResult();
                    }
                    break;
                case RetryLaunchViewModel.VIEW_MODEL_EVENT_WAITING_FOR_RETRY:
                    initializeUi();
                    break;
                case RetryLaunchViewModel.VIEW_MODEL_EVENT_LAUNCH_FAILURE:
                    finishWithCancelResult();
                    break;
            }
        });
    }

    private RetryLaunchViewModel createViewModelWithIntent(Intent activityIntent, Config config) {
        return new ViewModelProvider(this,
                new RetryLaunchViewModelFactory(
                        getApplication(),
                        activityIntent,
                        config,
                        mUtils))
                .get(RetryLaunchViewModel.class);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mViewModel.stopLaunchRetries();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode != LAUNCH_ACTIVITY_REQUEST_CODE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        ProvisionLogger.logi("Retry launcher activity result code: " + resultCode);
        finishWithResult(resultCode, data);
    }

    private void initializeUi() {
        int headerResId = R.string.just_a_sec;
        int titleResId = R.string.just_a_sec;
        initializeLayoutParams(R.layout.empty_loading_layout, headerResId);
        setTitle(titleResId);
    }

    private void launchActivity(Intent intent) {
        getTransitionHelper().startActivityForResultWithTransition(
                this,
                intent,
                LAUNCH_ACTIVITY_REQUEST_CODE);
    }

    private void finishWithResult(int resultCode, Intent data) {
        setResult(resultCode, data);
        getTransitionHelper().finishActivity(this);
    }

    private void finishWithCancelResult() {
        finishWithResult(RESULT_CANCELED, /*data=*/ null);
    }
}
