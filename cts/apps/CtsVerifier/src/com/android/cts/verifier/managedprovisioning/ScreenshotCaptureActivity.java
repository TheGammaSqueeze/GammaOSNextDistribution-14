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

package com.android.cts.verifier.managedprovisioning;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallback;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.cts.verifier.R;

/**
 * Displays sample content and prompts the tester to take a screenshot.
 * <p>
 * Returns the captured image file descriptor via RemoteCallback.
 */
public class ScreenshotCaptureActivity extends Activity {

    public static final String ACTION_CAPTURE_SCREENSHOT =
            "com.android.cts.verifier.managedprovisioning.CAPTURE_SCREENSHOT";
    public static final String EXTRA_CALLBACK = "remote_callback";
    public static final String EXTRA_FILE_DESCRIPTOR = "file_descriptor";
    public static final String EXTRA_SUCCESS = "success";
    public static final String EXTRA_MESSAGE = "extra_message";

    private static final int REQUEST_OPEN_SCREEENSHOT = 100;

    private ScreenCaptureCallback mScreenCaptureCallback;
    private RemoteCallback mCallback;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screenshot_capture);
        Intent intent = getIntent();
        mCallback = intent.getParcelableExtra(EXTRA_CALLBACK, RemoteCallback.class);

        Button b = requireViewById(R.id.provisioning_byod_screenshot_capture_button);
        b.setVisibility(View.VISIBLE);
        b.setOnClickListener((v) -> {
            Bundle result = new Bundle();
            result.putString(EXTRA_MESSAGE, "Screenshot capture was canceled");
            mCallback.sendResult(result);
            reset();
            finish();
        });

        mScreenCaptureCallback = () -> {
            TextView info = requireViewById(R.id.provisioning_byod_screenshot_capture_text);
            info.setText(R.string.provisioning_byod_screenshot_select_message);
            b.setText(R.string.provisioning_byod_screenshot_capture_open);
            b.setVisibility(View.VISIBLE);
            b.setOnClickListener((v) -> openDocument());
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerScreenCaptureCallback(getMainExecutor(), mScreenCaptureCallback);
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_OPEN_SCREEENSHOT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Button b = requireViewById(R.id.provisioning_byod_screenshot_capture_button);
        b.setVisibility(View.GONE);

        Bundle result = new Bundle();
        if (resultCode == RESULT_CANCELED) {
            result.putString(EXTRA_MESSAGE, "Screenshot selection was canceled."
                    + " Saved to wrong profile?");
        } else {
            Uri uri = data.getData();
            try {
                ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r");
                result.putBoolean(EXTRA_SUCCESS, true);
                result.putParcelable(EXTRA_FILE_DESCRIPTOR, fd);
            } catch (Exception e) {
                result.putString(EXTRA_MESSAGE, "Failed to read screenshot image: " + e.toString());
            }
        }
        mCallback.sendResult(result);
        reset();
        finish();
    }

    private void reset() {
        TextView info = requireViewById(R.id.provisioning_byod_screenshot_capture_text);
        info.setText(R.string.provisioning_byod_screenshot_capture_title);

        Button b = requireViewById(R.id.provisioning_byod_screenshot_capture_button);
        b.setVisibility(View.VISIBLE);
        b.setText(R.string.provisioning_byod_screenshot_capture_cancel);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterScreenCaptureCallback(mScreenCaptureCallback);
    }
}
