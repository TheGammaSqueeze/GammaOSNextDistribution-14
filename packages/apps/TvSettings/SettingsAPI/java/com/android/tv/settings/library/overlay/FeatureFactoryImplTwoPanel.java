/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.app.AppGlobals;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.VpnManager;
import android.os.UserManager;

import androidx.annotation.Keep;

import com.android.tv.settings.library.basic.BasicModeFeatureProvider;
import com.android.tv.settings.library.basic.BasicModeFeatureProviderImpl;
import com.android.tv.settings.library.enterprise.EnterprisePrivacyFeatureProvider;
import com.android.tv.settings.library.enterprise.EnterprisePrivacyFeatureProviderImpl;
import com.android.tv.settings.library.enterprise.apps.ApplicationFeatureProvider;
import com.android.tv.settings.library.enterprise.apps.ApplicationFeatureProviderImpl;
import com.android.tv.settings.library.help.SupportFeatureProvider;
import com.android.tv.settings.library.help.SupportFeatureProviderImpl;
import com.android.tv.settings.library.startup.startup.StartupVerificationFeatureProvider;
import com.android.tv.settings.library.startup.startup.StartupVerificationFeatureProviderImpl;

/** Two panel customized implementation of the feature factory. */
@Keep
public class FeatureFactoryImplTwoPanel implements FeatureFactory {

    protected static final String TAG = "FeatureFactoryImplTwoP";

    private EnterprisePrivacyFeatureProvider mEnterprisePrivacyFeatureProvider;
    private ApplicationFeatureProvider mApplicationFeatureProvider;


    @Override
    public SupportFeatureProvider getSupportFeatureProvider() {
        return new SupportFeatureProviderImpl();
    }

    @Override
    public BasicModeFeatureProvider getBasicModeFeatureProvider() {
        return new BasicModeFeatureProviderImpl();
    }

    @Override
    public StartupVerificationFeatureProvider getStartupVerificationFeatureProvider() {
        return new StartupVerificationFeatureProviderImpl();
    }

    @Override
    public EnterprisePrivacyFeatureProvider getEnterprisePrivacyFeatureProvider(Context context) {
        if (mEnterprisePrivacyFeatureProvider == null) {
            final Context appContext = context.getApplicationContext();
            mEnterprisePrivacyFeatureProvider = new EnterprisePrivacyFeatureProviderImpl(appContext,
                    appContext.getSystemService(DevicePolicyManager.class),
                    appContext.getPackageManager(),
                    UserManager.get(appContext),
                    appContext.getSystemService(ConnectivityManager.class),
                    appContext.getSystemService(VpnManager.class),
                    appContext.getResources());
        }
        return mEnterprisePrivacyFeatureProvider;
    }

    @Override
    public ApplicationFeatureProvider getApplicationFeatureProvider(Context context) {
        if (mApplicationFeatureProvider == null) {
            final Context appContext = context.getApplicationContext();
            mApplicationFeatureProvider = new ApplicationFeatureProviderImpl(appContext,
                    appContext.getPackageManager(),
                    AppGlobals.getPackageManager(),
                    appContext.getSystemService(DevicePolicyManager.class));
        }
        return mApplicationFeatureProvider;
    }
}
