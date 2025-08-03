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

package com.android.cts.verifier.input;

import android.hardware.display.DisplayManager;
import android.hardware.input.HostUsiVersion;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.util.ArrayMap;
import android.view.Display;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.ApiTest;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.Objects;

/**
 * Test for verifying that all built-in styluses that use the
 * <a href="https://universalstylus.org">Universal Stylus Initiative (USI)</a> protocol
 * correctly report their USI version.
 */
@ApiTest(apis = {"android.hardware.input.InputManager#getHostUsiVersion"})
public class UsiVersionActivity extends PassFailButtons.Activity {

    TextView mInstructionsTextView;
    Button mYesButton;
    Button mNoButton;

    TestStage mCurrentStage;
    final ArrayMap<Display, HostUsiVersion> mInternalDisplays = new ArrayMap<>();

    /** Used for {@link TestStage#VERIFY_USI_VERSIONS}. */
    int mCurrentDisplayIndex;

    String mFailReason;

    private enum TestStage {
        // Ask whether the tester is ready to start.
        START_INSTRUCTIONS,
        // Ask if the current display supports USI.
        VERIFY_USI_SUPPORT,
        // If the current display does support USI, ask if the reported USI version is correct.
        VERIFY_USI_VERSION,
        // Test passed.
        PASSED,
        // Test failed.
        FAILED,
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.usi_version);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.usi_version_test, R.string.usi_version_test_info, -1);

        mInstructionsTextView = findViewById(R.id.usi_instructions_text);
        mYesButton = findViewById(R.id.usi_yes_button);
        mYesButton.setOnClickListener((v) -> advanceTestStage(true /*fromYesButton*/));
        mNoButton = findViewById(R.id.usi_no_button);
        mNoButton.setOnClickListener((v) -> advanceTestStage(false /*fromYesButton*/));

        mCurrentStage = TestStage.START_INSTRUCTIONS;
        processTestStage();
    }

    private void advanceTestStage(boolean fromYesButton) {
        switch (mCurrentStage) {
            case START_INSTRUCTIONS:
                mCurrentStage = fromYesButton
                        ? TestStage.VERIFY_USI_SUPPORT
                        : getFailState(R.string.usi_fail_reason_not_ready);
                break;

            case VERIFY_USI_SUPPORT:
                final var usiVersion = mInternalDisplays.valueAt(mCurrentDisplayIndex);
                if (fromYesButton) {
                    // The user said that the display supports USI.
                    if (usiVersion != null) {
                        mCurrentStage = TestStage.VERIFY_USI_VERSION;
                    } else {
                        mCurrentStage = getFailState(R.string.usi_fail_reason_version_not_found);
                    }
                } else {
                    // The user said that the display DOES NOT support USI.
                    if (usiVersion == null)  {
                        mCurrentDisplayIndex++;
                        mCurrentStage = mCurrentDisplayIndex >= mInternalDisplays.size()
                                ? TestStage.PASSED
                                : TestStage.VERIFY_USI_SUPPORT;
                    } else {
                        mCurrentStage = getFailState(
                                R.string.usi_fail_reason_found_unexpected_version);
                    }
                }
                break;

            case VERIFY_USI_VERSION:
                if (fromYesButton) {
                    mCurrentDisplayIndex++;
                    mCurrentStage = mCurrentDisplayIndex >= mInternalDisplays.size()
                            ? TestStage.PASSED
                            : TestStage.VERIFY_USI_SUPPORT;
                } else {
                    mCurrentStage = getFailState(R.string.usi_fail_reason_incorrect_version);
                }
                break;

            case PASSED:
            case FAILED:
                break;
        }

        processTestStage();
    }

    private TestStage getFailState(int reasonResId) {
        mFailReason = getString(reasonResId);
        return TestStage.FAILED;
    }

    private void processTestStage() {
        switch (mCurrentStage) {
            case START_INSTRUCTIONS: {
                mFailReason = null;
                mCurrentDisplayIndex = 0;
                populateInternalDisplays();
                mInstructionsTextView.setText(
                        getString(R.string.usi_test_start, mInternalDisplays.size()));
                getPassButton().setEnabled(false);
                mYesButton.setEnabled(true);
                mNoButton.setEnabled(true);
                break;
            }

            case VERIFY_USI_SUPPORT: {
                final Display display = mInternalDisplays.keyAt(mCurrentDisplayIndex);
                mInstructionsTextView.setText(
                        getString(R.string.usi_test_verify_display_usi_support, display.getName()));
                break;
            }

            case VERIFY_USI_VERSION: {
                final Display display = mInternalDisplays.keyAt(mCurrentDisplayIndex);
                final HostUsiVersion usiVersion = mInternalDisplays.valueAt(mCurrentDisplayIndex);
                assert (usiVersion != null);

                final String version =
                        usiVersion.getMajorVersion() + "." + usiVersion.getMinorVersion();
                mInstructionsTextView.setText(
                        getString(R.string.usi_test_verify_display_usi_version, display.getName(),
                                version));
                break;
            }

            case PASSED: {
                if (mFailReason != null) throw new IllegalStateException("Fail reason is non null");
                mInstructionsTextView.setText(getString(R.string.usi_test_passed));
                getPassButton().setEnabled(true);
                mYesButton.setEnabled(false);
                mNoButton.setEnabled(false);
                break;
            }

            case FAILED: {
                Objects.requireNonNull(mFailReason);
                mInstructionsTextView.setText(getString(R.string.usi_test_failed, mFailReason));
                getPassButton().setEnabled(false);
                mYesButton.setEnabled(false);
                mNoButton.setEnabled(false);
                break;
            }
        }
    }

    private void populateInternalDisplays() {
        final Display[] displays = Objects.requireNonNull(getSystemService(DisplayManager.class))
                .getDisplays();
        final var inputManager = Objects.requireNonNull(getSystemService(InputManager.class));
        for (Display display : displays) {
            if (display.getType() == Display.TYPE_INTERNAL) {
                mInternalDisplays.put(display, inputManager.getHostUsiVersion(display));
            }
        }
    }
}
