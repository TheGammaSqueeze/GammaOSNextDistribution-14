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

package com.android.cts.verifier.security;

import static android.os.Build.VERSION_CODES;

import static com.android.compatibility.common.util.PropertyUtil.getFirstApiLevel;
import static com.android.compatibility.common.util.PropertyUtil.getVendorApiLevel;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * This test confirms that handheld and tablet devices correctly declare the
 * {@link PackageManager#FEATURE_SECURITY_MODEL_COMPATIBLE} feature. Only enforced
 * on devices that launched with SC or later.
 */
public class SecurityModeFeatureVerifierActivity extends PassFailButtons.Activity {
    private ImageView mHandheldOrTabletImage;
    private TextView mHandheldOrTabletText;
    private Button mHandheldOrTabletOkButton;
    private Button mHandheldOrTabletNaButton;
    private boolean mFeatureAvailable;
    private boolean mDeviceLaunchedBeforeS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.security_mode_feature_verifier, null);
        setContentView(view);
        setInfoResources(R.string.security_mode_feature_verifier_test,
                R.string.security_mode_feature_verifier_instructions, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mHandheldOrTabletImage = (ImageView) findViewById(R.id.handheld_or_tablet_image);
        mHandheldOrTabletText = (TextView) findViewById(R.id.handheld_or_tablet_text);
        mHandheldOrTabletOkButton = (Button) findViewById(R.id.handheld_or_tablet_yes);
        mHandheldOrTabletNaButton = (Button) findViewById(R.id.handheld_or_tablet_not_applicable);

        // Devices launched before S will always pass the test.
        mDeviceLaunchedBeforeS = isLaunchedBeforeS();

        mFeatureAvailable = getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_SECURITY_MODEL_COMPATIBLE);

        mHandheldOrTabletNaButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setTestResultAndFinish(true);
            }
        });

        mHandheldOrTabletOkButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setTestResultAndFinish(mFeatureAvailable || mDeviceLaunchedBeforeS);
            }
        });
    }

    private static boolean isLaunchedBeforeS() {
        return Math.min(getFirstApiLevel(), getVendorApiLevel()) < VERSION_CODES.S;
    }
}
