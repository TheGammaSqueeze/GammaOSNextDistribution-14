/*
 * Copyright 2020 The Android Open Source Project
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
package com.android.car.admin.ui;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/**
 * Custom view used to display a disclaimer when a device is managed by a device owner.
 */
public final class ManagedDeviceTextView extends TextView {

    private static final String TAG = ManagedDeviceTextView.class.getSimpleName();

    private static final boolean DEBUG = false;

    public ManagedDeviceTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ManagedDeviceTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ManagedDeviceTextView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        if (!isManagedDevice(context)) {
            setVisibility(View.GONE);
            return;
        }

        CharSequence text = getManagedDeviceText(context);
        if (DEBUG) Log.d(TAG, "setting text to '" + text + "'");

        setText(text);
    }

    /**
     * Gets the text indicating that the device is managed by an organization.
     */
    public static CharSequence getManagedDeviceText(Context context) {
        Resources res = context.getResources();
        try {
            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            CharSequence orgName = dpm.getDeviceOwnerOrganizationName();
            if (orgName != null) {
                return res.getString(R.string.car_admin_ui_managed_device_message_by_org, orgName);
            }
            if (DEBUG) {
                Log.d(TAG, "organization name not set, using device owner app name instead");
            }
            return res.getString(R.string.car_admin_ui_managed_device_message_generic);
        } catch (Exception e) {
            Log.w(TAG, "error getting name of device owner organization", e);
            return res.getString(R.string.car_admin_ui_managed_device_message_generic);
        }
    }

    private static boolean isManagedDevice(Context context) {
        PackageManager pm = context.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)) return false;

        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.isDeviceManaged();
    }
}
