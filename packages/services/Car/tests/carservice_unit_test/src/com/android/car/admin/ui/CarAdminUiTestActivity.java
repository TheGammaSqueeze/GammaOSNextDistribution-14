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

package com.android.car.admin.ui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.carservice_unittest.R;


/**
 * A helper activity to facilitate testing of {@link UserAvatarView} and {@link
 * ManagedDeviceTextView}.
 */
public final class CarAdminUiTestActivity extends Activity {

    public UserAvatarView mUserAvatarView;
    public PackageManager mSpyPackageManager;
    public DevicePolicyManager mMockDevicePolicyManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSpyPackageManager = spy(getPackageManager());
        mMockDevicePolicyManager = mock(DevicePolicyManager.class);
        setContentView(R.layout.car_admin_ui_test_activity);
        mUserAvatarView = findViewById(R.id.view_user_avatar);
    }

    @Override
    public PackageManager getPackageManager() {
        if (mSpyPackageManager != null) {
            return mSpyPackageManager;
        }
        return super.getPackageManager();
    }

    @Override
    public Object getSystemService(@NonNull String name) {
        if (Context.DEVICE_POLICY_SERVICE.equals(name)) {
            if (mMockDevicePolicyManager != null) {
                return mMockDevicePolicyManager;
            }
        }
        return super.getSystemService(name);
    }
}
