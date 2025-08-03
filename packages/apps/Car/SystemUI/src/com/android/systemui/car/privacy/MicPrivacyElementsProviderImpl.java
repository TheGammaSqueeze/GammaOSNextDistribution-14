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

package com.android.systemui.car.privacy;

import android.content.Context;
import android.content.pm.PackageManager;
import android.permission.PermissionManager;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.privacy.logging.PrivacyLogger;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

/**
 * Implementation of {@link
 * com.android.systemui.car.privacy.SensorQcPanel.SensorPrivacyElementsProvider} for microphone.
 */
@SysUISingleton
public class MicPrivacyElementsProviderImpl extends PrivacyElementsProviderImpl {

    @Inject
    public MicPrivacyElementsProviderImpl(
            Context context,
            PermissionManager permissionManager,
            PackageManager packageManager,
            PrivacyItemController privacyItemController,
            UserTracker userTracker,
            PrivacyLogger privacyLogger) {
        super(context, permissionManager, packageManager, privacyItemController, userTracker,
                privacyLogger);
    }

    @Override
    protected PrivacyType getProviderPrivacyType() {
        return PrivacyType.TYPE_MICROPHONE;
    }
}
