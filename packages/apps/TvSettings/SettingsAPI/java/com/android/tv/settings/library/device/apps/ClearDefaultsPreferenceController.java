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

package com.android.tv.settings.library.device.apps;

import static com.android.tv.settings.library.device.apps.AppManagementState.REQUEST_CLEAR_DEFAULTS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.IUsbManager;
import android.os.IBinder;
import android.os.ServiceManager;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.ResourcesUtil;

/** Preference controller class to handle clear defaults preference. */
public class ClearDefaultsPreferenceController extends AppActionPreferenceController {
    private static final String KEY_CLEAR_DEFAULTS = "clearDefaults";

    private final IUsbManager mUsbManager;
    private final PackageManager mPackageManager;

    public ClearDefaultsPreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            ApplicationsState.AppEntry appEntry, PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, appEntry, preferenceCompatManager);
        final IBinder usbBinder = ServiceManager.getService(Context.USB_SERVICE);
        mUsbManager = IUsbManager.Stub.asInterface(usbBinder);
        mPackageManager = context.getPackageManager();

    }

    @Override
    public void update() {
        if (mAppEntry == null) {
            return;
        }
        mPreferenceCompat.setTitle(ResourcesUtil.getString(
                mContext, "device_apps_app_management_clear_default"));
        mPreferenceCompat.setSummary(AppUtils.getLaunchByDefaultSummary(
                mAppEntry, mUsbManager, mPackageManager, mContext).toString());
        super.update();
    }

    @Override
    public boolean handlePreferenceTreeClick(boolean status) {
        if (!mDisabledByAdmin) {
            Intent i = new Intent(INTENT_CONFIRMATION);
            i.putExtra(EXTRA_GUIDANCE_TITLE, ResourcesUtil.getString(
                    mContext, "device_apps_app_management_clear_default"));
            i.putExtra(EXTRA_GUIDANCE_BREADCRUMB, getAppName());
            ((Activity) mContext).startActivityForResult(i,
                    ManagerUtil.calculateCompoundCode(
                            mStateIdentifier, REQUEST_CLEAR_DEFAULTS));
            return true;
        }
        return super.handlePreferenceTreeClick(status);
    }

    @Override
    public boolean useAdminDisabledSummary() {
        return false;
    }

    @Override
    public String getAttrUserRestriction() {
        return null;
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_CLEAR_DEFAULTS};
    }
}
