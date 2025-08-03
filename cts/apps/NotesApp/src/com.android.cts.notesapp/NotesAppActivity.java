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

package com.android.cts.notesapp;

import android.Manifest;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

/**
 * A test activity to be used as the Default notes app for CTS Verifier test.
 */
public class NotesAppActivity extends Activity {

    private static final Intent API_ACTION =
            new Intent(Intent.ACTION_LAUNCH_CAPTURE_CONTENT_ACTIVITY_FOR_NOTE);
    private static final int REQUEST_CODE = 42;

    private StatusBarManager mStatusBarManager;
    private TextView mStatusMessageTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mStatusMessageTextView = findViewById(R.id.status_message);

        // Set up button firing the capture content intent action.
        Button fireIntentActionButton = findViewById(R.id.fire_intent_action);
        fireIntentActionButton.setOnClickListener(unused -> {
            mStatusMessageTextView.setVisibility(View.INVISIBLE);
            startActivityForResult(API_ACTION, REQUEST_CODE);
        });

        // Set up button that calls the can-use API.
        mStatusBarManager = getSystemService(StatusBarManager.class);
        Button callCanUseApiButton = findViewById(R.id.call_can_use_api);
        callCanUseApiButton.setOnClickListener(unused -> {
            mStatusMessageTextView.setVisibility(View.INVISIBLE);

            // Check for permission before making the API call.
            if (ActivityCompat.checkSelfPermission(NotesAppActivity.this,
                    Manifest.permission.LAUNCH_CAPTURE_CONTENT_ACTIVITY_FOR_NOTE)
                    != PackageManager.PERMISSION_GRANTED) {
                mStatusMessageTextView.setText(R.string.permission_not_available);
                mStatusMessageTextView.setVisibility(View.VISIBLE);
                return;
            }

            // Perform the API call and update UI state.
            boolean canUseApiResponse =
                    mStatusBarManager.canLaunchCaptureContentActivityForNote(NotesAppActivity.this);
            if (canUseApiResponse) {
                mStatusMessageTextView.setText(R.string.can_use_api_returned_true);
            } else {
                mStatusMessageTextView.setText(R.string.can_use_api_returned_false);
            }

            mStatusMessageTextView.setVisibility(View.VISIBLE);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Return early for unrelated request codes.
        if (requestCode != REQUEST_CODE) {
            return;
        }

        // Handle API call failures indicated by RESULT_CANCELED result code.
        if (resultCode == Activity.RESULT_CANCELED) {
            mStatusMessageTextView.setText(R.string.api_call_failed);
            mStatusMessageTextView.setVisibility(View.VISIBLE);
            return;
        }

        // Get the response code from API call and update UI in a switch statement.
        int apiResponseCode =
                data.getIntExtra(Intent.EXTRA_CAPTURE_CONTENT_FOR_NOTE_STATUS_CODE, -1);
        switch (apiResponseCode) {
            case Intent.CAPTURE_CONTENT_FOR_NOTE_SUCCESS:
                if (data.getData() == null) {
                    // In case there is no screenshot URI returned, set status to API failed.
                    mStatusMessageTextView.setText(R.string.api_call_failed);
                } else {
                    mStatusMessageTextView.setText(R.string.launch_and_add);
                }
                break;

            case Intent.CAPTURE_CONTENT_FOR_NOTE_USER_CANCELED:
                mStatusMessageTextView.setText(R.string.launch_and_cancel);
                break;

            case Intent.CAPTURE_CONTENT_FOR_NOTE_WINDOW_MODE_UNSUPPORTED:
                mStatusMessageTextView.setText(R.string.launch_window_unsupported);
                break;

            case Intent.CAPTURE_CONTENT_FOR_NOTE_BLOCKED_BY_ADMIN:
                mStatusMessageTextView.setText(R.string.launch_blocked_by_admin);
                break;

            default:
                mStatusMessageTextView.setText(R.string.api_call_failed);
        }

        mStatusMessageTextView.setVisibility(View.VISIBLE);
    }
}
