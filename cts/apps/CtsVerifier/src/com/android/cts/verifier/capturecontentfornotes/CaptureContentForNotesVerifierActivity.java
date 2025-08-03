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

package com.android.cts.verifier.capturecontentfornotes;

import static com.android.cts.verifier.capturecontentfornotes.DeviceAdminCommandExecutorActivity.ACTION;
import static com.android.cts.verifier.capturecontentfornotes.DeviceAdminCommandExecutorActivity.COMMAND_CLEAR_DEVICE_OWNER;
import static com.android.cts.verifier.capturecontentfornotes.DeviceAdminCommandExecutorActivity.COMMAND_DISABLE_SCREEN_CAPTURE;
import static com.android.cts.verifier.capturecontentfornotes.DeviceAdminCommandExecutorActivity.COMMAND_ENABLE_SCREEN_CAPTURE;
import static com.android.cts.verifier.capturecontentfornotes.DeviceAdminCommandExecutorActivity.EXTRA_COMMAND;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ApiTest;
import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.IntentDrivenTestActivity;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;
import com.android.cts.verifier.IntentDrivenTestActivity.TestInfo;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter;

/**
 * Activity that tests the {@link Intent#ACTION_LAUNCH_CAPTURE_CONTENT_ACTIVITY_FOR_NOTE} and
 * {@link android.app.StatusBarManager#canLaunchCaptureContentActivityForNote(Activity)} APIs.
 */
@ApiTest(apis = {"android.app.StatusBarManager#canLaunchCaptureContentActivityForNote(Activity)",
                 "Intent#ACTION_LAUNCH_CAPTURE_CONTENT_ACTIVITY_FOR_NOTE",
                 "Intent#EXTRA_CAPTURE_CONTENT_FOR_NOTE_STATUS_CODE",
                 "Intent#CAPTURE_CONTENT_FOR_NOTE_USER_CANCELED",
                 "Intent#CAPTURE_CONTENT_FOR_NOTE_WINDOW_MODE_UNSUPPORTED",
                 "Intent#CAPTURE_CONTENT_FOR_NOTE_BLOCKED_BY_ADMIN"})
public class CaptureContentForNotesVerifierActivity extends PassFailButtons.TestListActivity
        implements View.OnClickListener {

    private static final String TAG = CaptureContentForNotesVerifierActivity.class.getSimpleName();
    private static final Intent CLEAR_DEVICE_OWNER_INTENT =
            new Intent(ACTION).putExtra(EXTRA_COMMAND, COMMAND_CLEAR_DEVICE_OWNER);

    private static final String LAUNCH_AND_ADD = "LAUNCH_AND_ADD";
    private static final String LAUNCH_AND_CANCEL = "LAUNCH_AND_CANCEL";
    private static final String LAUNCH_WINDOW_UNSUPPORTED = "LAUNCH_WINDOW_UNSUPPORTED";
    private static final String LAUNCH_BLOCKED_BY_ADMIN = "LAUNCH_BLOCKED_BY_ADMIN";

    private static final String CALL_CAN_USE_API_TRUE = "CALL_CAN_USE_API_TRUE";
    private static final String CALL_CAN_USE_API_WINDOW_UNSUPPORTED =
            "CALL_CAN_USE_API_WINDOW_UNSUPPORTED";
    private static final String CALL_CAN_USE_API_BLOCKED_BY_ADMIN =
            "CALL_CAN_USE_API_BLOCKED_BY_ADMIN";

    private static final ButtonInfo[] ADMIN_POLICY_BUTTONS =
            new ButtonInfo[] {
                    new ButtonInfo(
                            R.string.disable_screenshot_button_label,
                            new Intent(ACTION)
                                    .putExtra(EXTRA_COMMAND, COMMAND_DISABLE_SCREEN_CAPTURE)),
                    new ButtonInfo(R.string.enable_screenshot_button_label,
                            new Intent(ACTION)
                                    .putExtra(EXTRA_COMMAND, COMMAND_ENABLE_SCREEN_CAPTURE))
            };

    private static final TestInfo[] TEST_INFOS =
            new TestInfo[] {
                    // Add intent action related tests.
                    new TestInfo(
                            LAUNCH_AND_ADD,
                            R.string.ccfn_launch_and_add_test,
                            R.string.ccfn_launch_and_add_test_info),
                    new TestInfo(
                            LAUNCH_AND_CANCEL,
                            R.string.ccfn_launch_and_cancel_test,
                            R.string.ccfn_launch_and_cancel_test_info),
                    new TestInfo(
                            LAUNCH_WINDOW_UNSUPPORTED,
                            R.string.ccfn_launch_window_unsupported,
                            R.string.ccfn_launch_window_unsupported_info),
                    new TestInfo(
                            LAUNCH_BLOCKED_BY_ADMIN,
                            R.string.ccfn_launch_blocked_by_admin,
                            R.string.ccfn_launch_blocked_blocked_by_admin_info,
                            ADMIN_POLICY_BUTTONS),

                    // Add can use API related tests.
                    new TestInfo(
                            CALL_CAN_USE_API_TRUE,
                            R.string.ccfn_call_can_use_api_true,
                            R.string.ccfn_call_can_use_api_true_info),
                    new TestInfo(
                            CALL_CAN_USE_API_WINDOW_UNSUPPORTED,
                            R.string.ccfn_call_can_use_api_window_unsupported,
                            R.string.ccfn_call_can_use_api_window_unsupported_info),
                    new TestInfo(
                            CALL_CAN_USE_API_BLOCKED_BY_ADMIN,
                            R.string.ccfn_call_can_use_api_blocked_by_admin,
                            R.string.ccfn_call_can_use_api_blocked_by_admin_info,
                            ADMIN_POLICY_BUTTONS),
            };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.capture_content_for_notes);
        setPassFailButtonClickListeners();

        ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        setTestListAdapter(adapter);
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        TextView testInstructionsView = findViewById(R.id.test_instructions);

        if (isNotesRoleAvailable()) {
            // If the notes role is available, disable the pass button and setup tests.
            getPassButton().setEnabled(false);
            addTestsToAdapter(adapter);

            // Set up test info resources to ask user to go through the test cases.
            setInfoResources(R.string.ccfn_tests, R.string.ccfn_tests_info, 0);
            testInstructionsView.setText(R.string.ccfn_tests_info);

            // Add these buttons only if the Notes role is available to avoid confusion.
            Button setDefaultNotesButton = findViewById(R.id.set_default_notes);
            setDefaultNotesButton.setOnClickListener(this);
            setDefaultNotesButton.setVisibility(View.VISIBLE);

            Button setupDeviceOwner = findViewById(R.id.setup_device_owner);
            setupDeviceOwner.setOnClickListener(this);
            setupDeviceOwner.setVisibility(View.VISIBLE);

            Button clearDeviceOwner = findViewById(R.id.clear_device_owner);
            clearDeviceOwner.setOnClickListener(this);
            clearDeviceOwner.setVisibility(View.VISIBLE);
        } else {
            // Notes role is unavailable, let the verifier skip this test altogether.
            getPassButton().setEnabled(true);

            // Set up test info resources to ask user to skip the test.
            setInfoResources(R.string.ccfn_tests, R.string.ccfn_tests_info_skip, 0);
            testInstructionsView.setText(R.string.ccfn_tests_info_skip);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.set_default_notes) {
            showAlertDialog(R.string.set_default_notes_button_label,
                    R.string.set_default_notes_button_info);
        } else if (id == R.id.setup_device_owner) {
            showAlertDialog(R.string.setup_device_owner_button_label,
                    R.string.setup_device_owner_button_info);
        } else if (id == R.id.clear_device_owner) {
            startActivity(CLEAR_DEVICE_OWNER_INTENT);
        }
    }

    /** Returns {@code true} if the {@link RoleManager#ROLE_NOTES} is available. */
    private boolean isNotesRoleAvailable() {
        RoleManager roleManager = getSystemService(RoleManager.class);
        return roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_NOTES);
    }

    /** Shows an {@link AlertDialog} with provided title an msg. */
    private void showAlertDialog(int titleId, int msgId) {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(titleId)
                .setMessage(msgId)
                .setPositiveButton(android.R.string.ok, /* listener= */ null)
                .show();
    }

    /** Adds the tests from {@link #TEST_INFOS} into the {@link ArrayTestListAdapter}. */
    private void addTestsToAdapter(ArrayTestListAdapter adapter) {
        for (TestInfo info : TEST_INFOS) {
            int title = info.getTitle();
            String testId = info.getTestId();
            Intent intent = IntentDrivenTestActivity.newIntent(this, testId, title,
                    info.getInfoText(), info.getButtons());
            Log.d(TAG, "Adding test with " + IntentDrivenTestActivity.toString(this, intent));
            adapter.add(TestListAdapter.TestListItem.newTest(this, title, testId, intent,
                    /* applicableFeatures= */ null));
        }
    }
}
