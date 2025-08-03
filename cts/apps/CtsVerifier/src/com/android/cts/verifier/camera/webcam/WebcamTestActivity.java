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

package com.android.cts.verifier.camera.webcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Test for Device as Webcam feature.
 * This test activity requires a USB connection to a computer, and a corresponding host-side run of
 * the python scripts found in the DeviceAsWebcam directory.
 */
public class WebcamTestActivity extends PassFailButtons.Activity {
    private static final String TAG = "WebcamTestActivity";
    private static final String ACTION_WEBCAM_RESULT =
            "com.android.cts.verifier.camera.webcam.ACTION_WEBCAM_RESULT";
    private static final String WEBCAM_RESULTS = "camera.webcam.extra.RESULTS";

    private static final String RESULT_PASS = "PASS";
    private static final String RESULT_FAIL = "FAIL";
    private static final String RESULT_NOT_EXECUTED = "NOT_EXECUTED";

    private final ResultReceiver mResultsReceiver = new ResultReceiver();
    private boolean mReceiverRegistered = false;

    private TestState mTestState;

    private Button mYesButton;
    private Button mNoButton;
    private Button mDoneButton;
    private View mPassButton;
    private TextView mInstructionTextView;

    private String mResultsFromScript = RESULT_FAIL;

    private enum TestState {
        ON_CREATE,
        RESULTS_RECEIVED,
        WEBCAM_NOT_SUPPORTED,
        RESULTS_PASS_FRAMES_PASS,
        RESULTS_FAIL_FRAMES_PASS,
        RESULTS_PASS_FRAMES_FAIL,
        RESULTS_FAIL_FRAMES_FAIL
    }

    private final View.OnClickListener mYesButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mResultsFromScript.equals(RESULT_PASS)) {
                mTestState = TestState.RESULTS_PASS_FRAMES_PASS;
            } else {
                mTestState = TestState.RESULTS_FAIL_FRAMES_PASS;
            }
            updateButtonsAndInstructions();
        }
    };

    private final View.OnClickListener mNoButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mResultsFromScript.equals(RESULT_PASS)) {
                mTestState = TestState.RESULTS_PASS_FRAMES_FAIL;
            } else {
                mTestState = TestState.RESULTS_FAIL_FRAMES_FAIL;
            }
            updateButtonsAndInstructions();
        }
    };

    private final View.OnClickListener mDoneButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (mTestState) {
                case WEBCAM_NOT_SUPPORTED:
                    setTestResultAndFinish(true);
                    break;
                case RESULTS_PASS_FRAMES_PASS:
                    setTestResultAndFinish(true);
                    break;
                case RESULTS_FAIL_FRAMES_PASS:
                    setTestResultAndFinish(false);
                    break;
                case RESULTS_PASS_FRAMES_FAIL:
                    setTestResultAndFinish(false);
                    break;
                case RESULTS_FAIL_FRAMES_FAIL:
                    setTestResultAndFinish(false);
                    break;
                default:
                    break;
            }
        }
    };

    class ResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_WEBCAM_RESULT.equals(intent.getAction())) {

                mTestState = TestState.RESULTS_RECEIVED;

                String results = intent.getStringExtra(WEBCAM_RESULTS);
                if (results.equals(RESULT_PASS)) {
                    mResultsFromScript = RESULT_PASS;
                } else if (results.equals(RESULT_NOT_EXECUTED)) {
                    mTestState = TestState.WEBCAM_NOT_SUPPORTED;
                } else {
                    mResultsFromScript = RESULT_FAIL;
                }

                updateButtonsAndInstructions();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.camera_webcam);

        setInfoResources(R.string.camera_webcam_test, R.string.camera_webcam_test_info, -1);

        mYesButton = (Button) findViewById(R.id.frames_pass_button);
        mYesButton.setOnClickListener(mYesButtonListener);

        mNoButton = (Button) findViewById(R.id.frames_fail_button);
        mNoButton.setOnClickListener(mNoButtonListener);

        mPassButton = getPassButton();
        setPassFailButtonClickListeners();

        mInstructionTextView = (TextView) findViewById(R.id.webcam_instruction_text);

        mDoneButton = (Button) findViewById(R.id.camera_webcam_done_button_id);
        mDoneButton.setOnClickListener(mDoneButtonListener);

        mTestState = TestState.ON_CREATE;
        updateButtonsAndInstructions();
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter(ACTION_WEBCAM_RESULT);
        registerReceiver(mResultsReceiver, filter, Context.RECEIVER_EXPORTED);
        mReceiverRegistered = true;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mReceiverRegistered) {
            unregisterReceiver(mResultsReceiver);
        }
    }

    private void updateButtonsAndInstructions() {
        switch (mTestState) {
            case ON_CREATE:
                mPassButton.setEnabled(false);
                mYesButton.setEnabled(false);
                mNoButton.setEnabled(false);
                mDoneButton.setEnabled(false);
                mInstructionTextView.setText(R.string.camera_webcam_start_text);
                break;
            case RESULTS_RECEIVED:
                // Once the results are received when the script is complete,
                // enable the buttons that will allow the user to indicate
                // whether the frames from the webcam that were displayed as part
                // of the test had any issues
                mYesButton.setEnabled(true);
                mNoButton.setEnabled(true);
                mInstructionTextView.setText(R.string.camera_webcam_confirm_frames_text);
                break;
            case WEBCAM_NOT_SUPPORTED:
                mInstructionTextView.setText(R.string.camera_webcam_not_supported_text);
                mDoneButton.setEnabled(true);
                break;
            case RESULTS_PASS_FRAMES_PASS:
                mInstructionTextView.setText(R.string.camera_webcam_results_pass_frames_pass_text);
                mDoneButton.setEnabled(true);
                break;
            case RESULTS_FAIL_FRAMES_PASS:
                mInstructionTextView.setText(R.string.camera_webcam_results_fail_frames_pass_text);
                mDoneButton.setEnabled(true);
                break;
            case RESULTS_PASS_FRAMES_FAIL:
                mInstructionTextView.setText(R.string.camera_webcam_results_pass_frames_fail_text);
                mDoneButton.setEnabled(true);
                break;
            case RESULTS_FAIL_FRAMES_FAIL:
                mInstructionTextView.setText(R.string.camera_webcam_results_fail_frames_fail_text);
                mDoneButton.setEnabled(true);
                break;
            default:
                break;
        }
    }
}
