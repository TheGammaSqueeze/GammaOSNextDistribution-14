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

package com.android.car.settings.enterprise;

import androidx.fragment.app.Fragment;

import com.android.car.settings.common.BaseCarSettingsActivity;

/**
 * A screen that shows details about a device administrator.
 */
public final class DeviceAdminAddActivity extends BaseCarSettingsActivity {

    /**
     * Optional key to map to the package name of the Device Admin.
     * <p>Currently only used when uninstalling an active device admin.
     */
    public static final String EXTRA_DEVICE_ADMIN_PACKAGE_NAME =
            "android.app.extra.DEVICE_ADMIN_PACKAGE_NAME";

    static final String EXTRA_CALLED_FROM_SUPPORT_DIALOG =
            "android.app.extra.CALLED_FROM_SUPPORT_DIALOG";

    @Override
    protected Fragment getInitialFragment() {
        return new DeviceAdminAddFragment();
    }
}
