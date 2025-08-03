/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tv.settings.library.overlay;

import android.content.Context;

import androidx.annotation.Keep;

import com.android.tv.settings.library.basic.BasicModeFeatureProvider;
import com.android.tv.settings.library.enterprise.EnterprisePrivacyFeatureProvider;
import com.android.tv.settings.library.enterprise.apps.ApplicationFeatureProvider;
import com.android.tv.settings.library.help.SupportFeatureProvider;
import com.android.tv.settings.library.startup.startup.StartupVerificationFeatureProvider;

/** Interface for feature controllers. Allows customization of the settings app. */
@Keep
public interface FeatureFactory {


    /** Supplies a support provider. */
    SupportFeatureProvider getSupportFeatureProvider();

    /** Supplies a provider for basic mode feature. */
    BasicModeFeatureProvider getBasicModeFeatureProvider();

    /** Supplies a provider for startup verification feature. */
    StartupVerificationFeatureProvider getStartupVerificationFeatureProvider();

    /** Supplies a provider for enterprise privacy feature */
    EnterprisePrivacyFeatureProvider getEnterprisePrivacyFeatureProvider(Context context);

    /** Supplies a provider for application infos */
    ApplicationFeatureProvider getApplicationFeatureProvider(Context context);
}
