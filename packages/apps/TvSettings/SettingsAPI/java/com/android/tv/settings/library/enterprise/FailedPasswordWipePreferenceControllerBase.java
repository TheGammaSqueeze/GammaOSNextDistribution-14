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

package com.android.tv.settings.library.enterprise;

import android.content.Context;

import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.overlay.FlavorUtils;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

public abstract class FailedPasswordWipePreferenceControllerBase
        extends AbstractPreferenceController {
    protected final EnterprisePrivacyFeatureProvider mFeatureProvider;

    public FailedPasswordWipePreferenceControllerBase(
            Context context, UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
        mFeatureProvider =
                FlavorUtils.getFeatureFactory(context).getEnterprisePrivacyFeatureProvider(context);
    }

    protected abstract int getMaximumFailedPasswordsBeforeWipe();

    @Override
    public void init() {
        update();
    }

    @Override
    public void update() {
        final int failedPasswordsBeforeWipe = getMaximumFailedPasswordsBeforeWipe();
        mPreferenceCompat.setSummary(ResourcesUtil.getQuantityString(mContext,
                "enterprise_privacy_number_failed_password_wipe", failedPasswordsBeforeWipe,
                failedPasswordsBeforeWipe));
    }

    @Override
    public boolean isAvailable() {
        return getMaximumFailedPasswordsBeforeWipe() > 0;
    }
}
