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

import android.Manifest;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;

/**
 * Base fragment for displaying a list of applications on a device.
 *
 * <p>Inner static classes are concrete implementations.
 */
public abstract class ApplicationListFragment extends SettingsFragment {

    private abstract static class AdminGrantedPermission extends ApplicationListFragment {
        private final String[] mPermissions;

        AdminGrantedPermission(String[] permissions) {
            mPermissions = permissions;
        }
    }

    /**
     * Fragment for camera permissions.
     */
    public static class AdminGrantedCameraPermission extends AdminGrantedPermission {
        public AdminGrantedCameraPermission() {
            super(new String[] {Manifest.permission.CAMERA});
        }

        @Override
        protected int getPreferenceScreenResId() {
            return R.xml.enterprise_privacy_granted_camera_permission_apps_fragment;
        }
    }

    /**
     * Fragment for location permissions.
     */
    public static class AdminGrantedLocationPermissions extends AdminGrantedPermission {
        public AdminGrantedLocationPermissions() {
            super(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION});
        }

        @Override
        protected int getPreferenceScreenResId() {
            return R.xml.enterprise_privacy_granted_location_permissions_apps_fragment;
        }
    }

    /**
     * Fragment for microphone permissions.
     */
    public static class AdminGrantedMicrophonePermission extends AdminGrantedPermission {
        public AdminGrantedMicrophonePermission() {
            super(new String[] {Manifest.permission.RECORD_AUDIO});
        }

        @Override
        protected int getPreferenceScreenResId() {
            return R.xml.enterprise_privacy_granted_microphone_permission_apps_fragment;
        }
    }

    /**
     * Fragment for application list installed by enterprise.
     */
    public static class EnterpriseInstalledPackages extends ApplicationListFragment {
        @Override
        protected int getPreferenceScreenResId() {
            return R.xml.enterprise_privacy_installed_packages_fragment;
        }
    }
}
