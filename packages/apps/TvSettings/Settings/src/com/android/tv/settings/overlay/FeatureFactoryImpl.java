/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tv.settings.overlay;

import android.app.AppGlobals;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.VpnManager;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Log;

import androidx.fragment.app.Fragment;

import com.android.tv.settings.BaseSettingsFragment;
import com.android.tv.settings.SettingsFragmentProvider;
import com.android.tv.settings.basic.BasicModeFeatureProvider;
import com.android.tv.settings.basic.BasicModeFeatureProviderImpl;
import com.android.tv.settings.enterprise.EnterprisePrivacyFeatureProvider;
import com.android.tv.settings.enterprise.EnterprisePrivacyFeatureProviderImpl;
import com.android.tv.settings.enterprise.apps.ApplicationFeatureProvider;
import com.android.tv.settings.enterprise.apps.ApplicationFeatureProviderImpl;
import com.android.tv.settings.help.SupportFeatureProvider;
import com.android.tv.settings.help.SupportFeatureProviderImpl;
import com.android.tv.settings.startup.StartupVerificationFeatureProvider;
import com.android.tv.settings.startup.StartupVerificationFeatureProviderImpl;

/**
 * Default implementation of the feature factory.
 */
public class FeatureFactoryImpl implements FeatureFactory {

    private static final String TAG = "FeatureFactoryImpl";

    private EnterprisePrivacyFeatureProvider mEnterprisePrivacyFeatureProvider;
    private ApplicationFeatureProvider mApplicationFeatureProvider;


    @Override
    public SettingsFragmentProvider getSettingsFragmentProvider() {
        return SettingsFragment::newInstance;
    }

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
                    appContext.getPackageManager(), UserManager.get(appContext),
                    appContext.getSystemService(ConnectivityManager.class),
                    appContext.getSystemService(VpnManager.class), appContext.getResources());
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

    /** A settings fragment suitable for displaying in the default (one panel) layout. */
    public static class SettingsFragment extends BaseSettingsFragment {

        private static final String EXTRA_FRAGMENT_CLASS_NAME = "fragmentClassName";

        public SettingsFragment() {}

        /** Constructs a new instance of a settings fragment. */
        public static SettingsFragment newInstance(String className, Bundle arguments) {
            SettingsFragment fragment = new SettingsFragment();
            Bundle args = arguments == null ? new Bundle() : new Bundle(arguments);
            args.putString(EXTRA_FRAGMENT_CLASS_NAME, className);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onPreferenceStartInitialScreen() {
            try {
                String className = getArguments().getString(EXTRA_FRAGMENT_CLASS_NAME);
                final Fragment fragment = (Fragment) Class.forName(className).newInstance();
                fragment.setArguments(getArguments());
                startPreferenceFragment(fragment);
            } catch (IllegalAccessException | ClassNotFoundException
                    | java.lang.InstantiationException e) {
                Log.e(TAG, "Unable to start initial preference screen.", e);
            }
        }
    }

}
