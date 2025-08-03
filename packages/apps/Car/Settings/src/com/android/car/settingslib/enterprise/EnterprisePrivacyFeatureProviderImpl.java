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
package com.android.car.settingslib.enterprise;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.car.settings.common.Logger;

import java.util.Date;

/**
 * TODO(b/208511815): copied from phone (but stripped what's not used), should be moved to
 * SettingsLib
 */
public final class EnterprisePrivacyFeatureProviderImpl
        implements EnterprisePrivacyFeatureProvider {

    private static final int MY_USER_ID = UserHandle.myUserId();

    private final Logger mLogger = new Logger(getClass());
    private final Context mContext;
    private final DevicePolicyManager mDpm;
    private final PackageManager mPm;

    public EnterprisePrivacyFeatureProviderImpl(Context context, DevicePolicyManager dpm,
            PackageManager pm) {
        mContext = context;
        mDpm = dpm;
        mPm = pm;
    }

    @Override
    public Date getLastSecurityLogRetrievalTime() {
        long timestamp = mDpm.getLastSecurityLogRetrievalTime();
        return timestamp < 0 ? null : new Date(timestamp);
    }

    @Override
    public Date getLastBugReportRequestTime() {
        long timestamp = mDpm.getLastBugReportRequestTime();
        return timestamp < 0 ? null : new Date(timestamp);
    }

    @Override
    public Date getLastNetworkLogRetrievalTime() {
        long timestamp = mDpm.getLastNetworkLogRetrievalTime();
        return timestamp < 0 ? null : new Date(timestamp);
    }

    @Override
    public String getImeLabelIfOwnerSet() {
        if (!mDpm.isCurrentInputMethodSetByOwner()) {
            return null;
        }
        String packageName = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD, MY_USER_ID);
        if (packageName == null) {
            return null;
        }
        try {
            return mPm.getApplicationInfoAsUser(packageName, /* flags= */ 0, MY_USER_ID)
                    .loadLabel(mPm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            mLogger.w("Could not get label for " + packageName + " and user " + MY_USER_ID, e);
            return null;
        }
    }

    @Override
    public int getMaximumFailedPasswordsBeforeWipeInCurrentUser() {
        ComponentName owner = mDpm.getDeviceOwnerComponentOnCallingUser();
        if (owner == null) {
            owner = mDpm.getProfileOwnerAsUser(MY_USER_ID);
        }
        if (owner == null) {
            return 0;
        }
        return mDpm.getMaximumFailedPasswordsForWipe(owner, MY_USER_ID);
    };
}
