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

package com.google.android.car.kitchensink.bluetooth;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

public final class BluetoothPermissionChecker {
    private BluetoothPermissionChecker() {
    }

    /**
     * Determines if the activity has the required permissions
     *
     * @param mActivity activity to query for permission
     * @param permission permission to verify
     * @return {@code true} if the activity has the permission, {@code false} otherwise
     */
    public static boolean isPermissionGranted(Activity mActivity, String permission) {
        return mActivity.checkSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request permission from user
     *
     * @param permission permission to request
     * @param fragment parent fragment where request originated
     * @param isGrantedRunnable callback that is triggered if the permission is grated
     * @param isNotGrantedRunnable callback that is triggered is the permission is not grated
     */
    public static void requestPermission(String permission, Fragment fragment,
            @Nullable Runnable isGrantedRunnable, @Nullable Runnable isNotGrantedRunnable) {
        fragment.registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        if (isGrantedRunnable != null) {
                            isGrantedRunnable.run();
                        }
                    } else {
                        if (isNotGrantedRunnable != null) {
                            isNotGrantedRunnable.run();
                        }
                    }
                }).launch(permission);
    }

    static void requestMultiplePermissions(String[] permissions, Fragment fragment,
            @Nullable Runnable isGrantedRunnable, @Nullable Runnable isNotGrantedRunnable) {
        fragment.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                isGrantedMap -> {
                    // I.e., true if *all* permissions in the map have been granted
                    if (!isGrantedMap.containsValue(false)) {
                        if (isGrantedRunnable != null) {
                            isGrantedRunnable.run();
                        }
                    } else {
                        if (isNotGrantedRunnable != null) {
                            isNotGrantedRunnable.run();
                        }
                    }
                }).launch(permissions);
    }
}
