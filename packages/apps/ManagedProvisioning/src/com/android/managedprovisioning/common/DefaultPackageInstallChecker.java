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

package com.android.managedprovisioning.common;

import static java.util.Objects.requireNonNull;

import android.content.pm.PackageManager;

/**
 * A default implementation of {@link PackageInstallChecker}.
 */
public final class DefaultPackageInstallChecker implements PackageInstallChecker {
    private final PackageManager mPackageManager;
    private final Utils mUtils;

    public DefaultPackageInstallChecker(PackageManager packageManager, Utils utils) {
        mPackageManager = requireNonNull(packageManager);
        mUtils = requireNonNull(utils);
    }

    @Override
    public boolean isPackageInstalled(String packageName) {
        return mUtils.isPackageInstalled(packageName, mPackageManager);
    }
}
