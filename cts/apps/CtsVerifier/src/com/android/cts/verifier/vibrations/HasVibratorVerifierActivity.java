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

package com.android.cts.verifier.vibrations;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.compatibility.common.util.ApiTest;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.Locale;

/**
 * This activity validates the result of {@link Vibrator#hasVibrator} API.
 *
 * A test is considered a positive scenario when hasVibrator returns true, otherwise the test is
 * a negative scenario.
 */
@ApiTest(apis = {"android.os.Vibrator#hasVibrator"})
public class HasVibratorVerifierActivity extends PassFailButtons.Activity {

    private static final int TEST_DURATION = 4_000;
    private static final int COUNT_DOWN_INTERVAL = 1_000;

    private boolean mHasVibrator = false;
    private int mCounter = TEST_DURATION / COUNT_DOWN_INTERVAL;
    private Vibrator mVibrator;
    private Animation mShakeAnimation;
    private TextView mVibrateCountdownTextView;
    private TextView mTestResultTextView;
    private TextView mDidDeviceVibrateTextView;
    private LinearLayout mResultButtonsLayout;
    private Button mVibrateButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_has_vibrator);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mVibrateCountdownTextView = findViewById(R.id.vibrate_countdown_textview);
        mTestResultTextView = findViewById(R.id.test_result_textview);
        mDidDeviceVibrateTextView = findViewById(R.id.did_device_vibrate_textview);
        mResultButtonsLayout = findViewById(R.id.layout_result_buttons);
        mVibrateButton = findViewById(R.id.vibrate_button);
        TextView hasVibratorApiResultTextView = findViewById(R.id.has_vibrator_api_result_textview);
        Button yesButton = findViewById(R.id.yes_button);
        Button noButton = findViewById(R.id.no_button);

        mShakeAnimation = AnimationUtils.loadAnimation(this, R.anim.horizontal_shake);

        VibratorManager vibratorManager = getSystemService(VibratorManager.class);
        if (vibratorManager == null) {
            throw new IllegalStateException(
                    "Something went wrong while creating the VibratorManager");
        }
        mVibrator = vibratorManager.getDefaultVibrator();
        mHasVibrator = mVibrator.hasVibrator();

        hasVibratorApiResultTextView.setText(
                mHasVibrator ? R.string.yes_string : R.string.no_string);

        mVibrateButton.setOnClickListener(v -> {
            startVibrating();
            updateScreenStateToStartedTesting();
            startAnimationIfRequired();
            startTestCountdown();
        });

        yesButton.setOnClickListener(v -> onYesButtonClicked());
        noButton.setOnClickListener(v -> onNoButtonClicked());
    }

    private void startVibrating() {
        mVibrator.vibrate(
                VibrationEffect.createOneShot(TEST_DURATION, VibrationEffect.MAX_AMPLITUDE));
    }

    private void startTestCountdown() {
        new CountDownTimer(TEST_DURATION, COUNT_DOWN_INTERVAL) {
            public void onTick(long millisUntilFinished) {
                mVibrateCountdownTextView.setText(
                        String.format(Locale.getDefault(),
                                getString(R.string.has_vibrator_test_running_text), mCounter));
                mCounter--;
            }

            public void onFinish() {
                updateScreenStateToFinishedTesting();
                mCounter = TEST_DURATION / COUNT_DOWN_INTERVAL;
                mVibrateCountdownTextView.clearAnimation();
            }
        }.start();
    }

    /**
     * If Vibrator#hasVibrator API indicated the device has a vibrator, and the device vibrated,
     * then the test passed. Otherwise, if the device vibrated despite the API indicating the device
     * has no vibrator then the test failed.
     */
    private void onYesButtonClicked() {
        mTestResultTextView.setVisibility(View.VISIBLE);
        getPassButton().setEnabled(mHasVibrator);
        if (mHasVibrator) {
            mTestResultTextView.setText(R.string.has_vibrator_test_vibrate_and_pass_message);
        } else {
            mTestResultTextView.setText(R.string.has_vibrator_test_no_vibrate_and_fail_message);
        }
    }

    /**
     * If Vibrator#hasVibrator API indicated the device has no vibrator, and the device did not
     * vibrate, then the test passed. Otherwise, if the device did vibrate despite the API
     * indicating the device has no vibrator then the test failed.
     */
    private void onNoButtonClicked() {
        mTestResultTextView.setVisibility(View.VISIBLE);
        getPassButton().setEnabled(!mHasVibrator);
        if (!mHasVibrator) {
            mTestResultTextView.setText(R.string.has_vibrator_test_no_vibrate_and_pass_message);
        } else {
            mTestResultTextView.setText(R.string.has_vibrator_test_vibrate_and_fail_message);
        }
    }

    private void updateScreenStateToStartedTesting() {
        mTestResultTextView.setVisibility(View.GONE);
        mVibrateButton.setVisibility(View.GONE);
        mDidDeviceVibrateTextView.setVisibility(View.GONE);
        mResultButtonsLayout.setVisibility(View.GONE);
        mVibrateCountdownTextView.setVisibility(View.VISIBLE);
    }

    private void updateScreenStateToFinishedTesting() {
        mVibrateButton.setVisibility(View.VISIBLE);
        mDidDeviceVibrateTextView.setVisibility(View.VISIBLE);
        mResultButtonsLayout.setVisibility(View.VISIBLE);
        mVibrateCountdownTextView.setVisibility(View.GONE);
    }

    private void startAnimationIfRequired() {
        if (mHasVibrator) {
            mVibrateCountdownTextView.clearAnimation();
            mVibrateCountdownTextView.startAnimation(mShakeAnimation);
        }
    }
}
