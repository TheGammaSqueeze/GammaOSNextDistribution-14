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

package com.android.tv.settings.enterprise;

import android.Manifest;
import android.content.Context;

import androidx.annotation.Keep;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.tv.settings.PreferenceControllerFragment;
import com.android.tv.settings.R;
import com.android.tv.settings.enterprise.apps.ApplicationFeatureProvider;
import com.android.tv.settings.overlay.FlavorUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Base fragment for displaying a list of applications on a device.
 * Inner static classes are concrete implementations.
 *
 * Forked from:
 * Settings/src/com/android/settings/enterprise/ApplicationListFragment.java
 */
@Keep
public abstract class ApplicationListFragment extends PreferenceControllerFragment
        implements ApplicationListPreferenceController.ApplicationListBuilder {

    static final String TAG = "EnterprisePrivacySettings";

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.app_list_disclosure_settings;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        ApplicationListPreferenceController controller = new ApplicationListPreferenceController(
                context, this, context.getPackageManager(), this);
        controllers.add(controller);
        return controllers;
    }

    private abstract static class AdminGrantedPermission extends ApplicationListFragment {
        private final String[] mPermissions;

        public AdminGrantedPermission(String[] permissions) {
            mPermissions = permissions;
        }

        @Override
        public void buildApplicationList(Context context,
                ApplicationFeatureProvider.ListOfAppsCallback callback) {
            FlavorUtils.getFeatureFactory(context).getApplicationFeatureProvider(context)
                    .listAppsWithAdminGrantedPermissions(mPermissions, callback);
        }
    }

    @Keep
    public static class AdminGrantedPermissionCamera extends AdminGrantedPermission {
        public static AdminGrantedPermissionCamera newInstance() {
            return new AdminGrantedPermissionCamera();
        }

        public AdminGrantedPermissionCamera() {
            super(new String[] {Manifest.permission.CAMERA});
        }
    }

    @Keep
    public static class AdminGrantedPermissionLocation extends AdminGrantedPermission {
        public static AdminGrantedPermissionLocation newInstance() {
            return new AdminGrantedPermissionLocation();
        }

        public AdminGrantedPermissionLocation() {
            super(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION});
        }
    }

    @Keep
    public static class AdminGrantedPermissionMicrophone extends AdminGrantedPermission {
        public static AdminGrantedPermissionMicrophone newInstance() {
            return new AdminGrantedPermissionMicrophone();
        }

        public AdminGrantedPermissionMicrophone() {
            super(new String[] {Manifest.permission.RECORD_AUDIO});
        }
    }

    @Keep
    public static class EnterpriseInstalledPackages extends ApplicationListFragment {
        public static EnterpriseInstalledPackages newInstance() {
            return new EnterpriseInstalledPackages();
        }

        public EnterpriseInstalledPackages() {
        }

        @Override
        public void buildApplicationList(Context context,
                ApplicationFeatureProvider.ListOfAppsCallback callback) {
            FlavorUtils.getFeatureFactory(context).getApplicationFeatureProvider(
                    context).listPolicyInstalledApps(callback);
        }
    }
}
