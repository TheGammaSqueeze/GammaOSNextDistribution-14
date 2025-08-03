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

package com.android.tests.packagemanager.multiuser.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.ModuleInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.install.lib.LocalIntentSender;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class PackageManagerMultiUserTest {
    private static final String ARG_PACKAGE_NAME = "pkgName";

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testUninstallExistingPackage() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.DELETE_PACKAGES);
        String pkgName = InstrumentationRegistry.getArguments().getString(ARG_PACKAGE_NAME);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager packageManager = context.getPackageManager();
        PackageInstaller packageInstaller = packageManager.getPackageInstaller();

        LocalIntentSender sender = new LocalIntentSender();
        packageInstaller.uninstallExistingPackage(pkgName, sender.getIntentSender());
        sender.getResult(); // Wait for the operation to complete
    }

    /**
     * Returns a list of installed modules to the host-side.
     */
    @Test
    public void testGetInstalledModules() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager packageManager = context.getPackageManager();
        List<ModuleInfo> modules = packageManager.getInstalledModules(0);
        List<String> names =
                modules.stream().map(info -> info.getPackageName()).collect(Collectors.toList());
        final Bundle results = new Bundle();
        results.putStringArrayList("installedModules", new ArrayList<>(names));
        InstrumentationRegistry.getInstrumentation().addResults(results);
    }
}
