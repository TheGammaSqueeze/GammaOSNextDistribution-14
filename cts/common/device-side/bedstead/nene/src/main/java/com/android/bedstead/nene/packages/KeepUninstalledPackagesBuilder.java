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

package com.android.bedstead.nene.packages;

import static android.Manifest.permission.KEEP_UNINSTALLED_PACKAGES;

import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.CheckResult;
import androidx.annotation.RequiresApi;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Builder for a list of packages which will not be cleaned up by the system even if they are not
 * installed on any user.
 */
@RequiresApi(Build.VERSION_CODES.S)
public final class KeepUninstalledPackagesBuilder {

    private final List<String> mPackages = new ArrayList<>();

    KeepUninstalledPackagesBuilder() {
    }

    /**
     * Commit the collection of packages which will not be cleaned up.
     *
     * <p>Packages previously in the list but not in the updated list may be removed at any time if
     * they are not installed on any user.
     */
    public void commit() {
        // TODO(scottjonathan): Investigate if we can make this backwards compatible by pulling the
        //  APK files and keeping them (either as a file or in memory) until needed to resolve or
        //  re-install
        PackageManager packageManager =
                TestApis.context().instrumentedContext().getPackageManager();

        try (PermissionContext p =
                    TestApis.permissions().withPermission(KEEP_UNINSTALLED_PACKAGES)) {
            packageManager.setKeepUninstalledPackages(mPackages);
        }
    }

    /**
     * Clear the list of packages which will not be cleaned up.
     *
     * <p>Packages previously in the list may be removed at any time if they are not installed on
     * any user.
     */
    public void clear() {
        mPackages.clear();
        commit();
    }

    /**
     * Add a package to the list of those which will not be cleaned up.
     */
    @CheckResult
    public KeepUninstalledPackagesBuilder add(Package pkg) {
        mPackages.add(pkg.packageName());
        return this;
    }

    /**
     * Add a package to the list of those which will not be cleaned up.
     */
    @CheckResult
    public KeepUninstalledPackagesBuilder add(String pkg) {
        return add(TestApis.packages().find(pkg));
    }

    /**
     * Add a collection of packages to the list of those which will not be cleaned up.
     */
    @CheckResult
    public KeepUninstalledPackagesBuilder add(Collection<Package> packages) {
        for (Package pkg : packages) {
            add(pkg);
        }
        return this;
    }

    /**
     * Add a collection of packages to the list of those which will not be cleaned up.
     */
    @CheckResult
    public KeepUninstalledPackagesBuilder addPackageNames(Collection<String> packages) {
        return add(packages.stream().map(
                (s) -> TestApis.packages().find(s)).collect(Collectors.toSet()));
    }
}
