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

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallback;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.IOException;
import java.util.Objects;

/**
 * Test of work profile screenshots.
 */
public class ScreenshotTestActivity extends PassFailButtons.Activity {

    public static final String ACTION_SCREENSHOT_TEST =
            "com.android.cts.verifier.managedprovisioning.SCREENSHOT_TEST";

    private static final String KEY_CAPTURE_ERROR_MESSAGE = "key_capture_error_message";

    private ParcelFileDescriptor mScreenshotFile;
    private String mErrorMessage;
    private RemoteCallback mCallback;
    private boolean mStartedCapture;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screenshot_test);
        setInfoResources(R.string.provisioning_byod_screenshot,
                R.string.provisioning_byod_screenshot_info, -1);
        setPassFailButtonClickListeners();
        setResult(RESULT_CANCELED);
        Button capture = requireViewById(R.id.provisioning_byod_screenshot_capture);
        capture.setOnClickListener((v) -> {
            mStartedCapture = true;
            startCaptureActivity();
        });
    }

    // Note: This is an intentional workaround for b/274767956
    private void startCaptureActivity() {
        mCallback = new RemoteCallback((bundle) -> {
            boolean ok = bundle.getBoolean(ScreenshotCaptureActivity.EXTRA_SUCCESS, false);
            if (ok) {
                mScreenshotFile = bundle.getParcelable(
                        ScreenshotCaptureActivity.EXTRA_FILE_DESCRIPTOR,
                        ParcelFileDescriptor.class);
                // ...flow continues in onResume
            } else {
                mErrorMessage = bundle.getString(ScreenshotCaptureActivity.EXTRA_MESSAGE);
            }
        },
        new Handler(Looper.getMainLooper()));

        Intent intent = new Intent(ScreenshotCaptureActivity.ACTION_CAPTURE_SCREENSHOT);
        intent.putExtra(ScreenshotCaptureActivity.EXTRA_CALLBACK, mCallback);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mScreenshotFile != null) {
            try (ParcelFileDescriptor fd = mScreenshotFile) {
                mScreenshotFile = null;
                setScreenshotPreview(fd);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (mStartedCapture) {
            getReportLog().addValue(
                    KEY_CAPTURE_ERROR_MESSAGE,
                    Objects.requireNonNullElse(mErrorMessage,
                            "Screenshot capture step was canceled"),
                    ResultType.WARNING,
                    ResultUnit.NONE);
            reset();
        }
    }

    private void reset() {
        Button capture = requireViewById(R.id.provisioning_byod_screenshot_capture);
        capture.setVisibility(View.VISIBLE);

        TextView description = requireViewById(R.id.provisioning_byod_screenshot_description);
        description.setText(R.string.provisioning_byod_screenshot_start);

        View view = findViewById(R.id.provisioning_byod_screenshot_preview_label);
        view.setVisibility(View.GONE);

        ImageView image = requireViewById(R.id.provisioning_byod_screenshot_preview_image);
        image.setVisibility(View.GONE);
        image.setImageDrawable(null);
    }

    private void setScreenshotPreview(ParcelFileDescriptor fd) {
        final Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());

        Button capture = requireViewById(R.id.provisioning_byod_screenshot_capture);
        capture.setVisibility(View.GONE);

        TextView description = requireViewById(R.id.provisioning_byod_screenshot_description);
        description.setText(R.string.provisioning_byod_screenshot_verify);

        View view = findViewById(R.id.provisioning_byod_screenshot_preview_label);
        view.setVisibility(View.VISIBLE);

        ImageView image = requireViewById(R.id.provisioning_byod_screenshot_preview_image);
        image.setVisibility(View.VISIBLE);

        image.setImageBitmap(bitmap);
    }
}

