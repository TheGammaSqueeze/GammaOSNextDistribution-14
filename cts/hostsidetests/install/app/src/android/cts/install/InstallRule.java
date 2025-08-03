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

package android.cts.install;

import static com.android.cts.install.lib.InstallUtils.getPackageInstaller;
import static com.android.cts.shim.lib.ShimPackage.SHIM_APEX_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import org.junit.rules.ExternalResource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adopts needed permissions for testing install flow and provides test packages mappings
 * corresponding to {@link INSTALL_TYPE}.
 */
final class InstallRule extends ExternalResource {
    private static final String TAG = InstallRule.class.getSimpleName();

    static final int VERSION_CODE_INVALID = -1;
    static final int VERSION_CODE_DEFAULT = 1;

    /** Indexes test apps with package name and versionCode */
    private static final Table<String, Integer, TestApp> sTestAppMap = getTestAppTable();

    @Override
    protected void before() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INSTALL_PACKAGES,
                        Manifest.permission.DELETE_PACKAGES);
    }

    @Override
    protected void after() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Performs cleanup phase for test installations. Actual purpose of this method is to be called
     * before and after each host-side test to reduce tests flakiness.
     */
    void cleanUp() throws Exception {
        PackageInstaller packageInstaller = getPackageInstaller();
        packageInstaller.getStagedSessions().stream()
                .filter(sessionInfo -> !sessionInfo.hasParentSessionId())
                .forEach(sessionInfo -> {
                    try {
                        Log.i(TAG, "abandoning session " + sessionInfo.getSessionId());
                        packageInstaller.abandonSession(sessionInfo.getSessionId());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to abandon session " + sessionInfo.getSessionId(), e);
                    }
                });
        Uninstall.packages(TestApp.A, TestApp.B);
    }

    /** Resolves corresponding test apps with specific install type and version. */
    List<TestApp> getTestApps(INSTALL_TYPE installType, int versionCode) {
        return getTestPackageNames(installType).stream()
                .map(packageName -> getTestApp(packageName, versionCode))
                .collect(Collectors.toList());
    }

    /** Asserts {@code packageNames} has been installed with expected version. */
    void assertPackageVersion(INSTALL_TYPE installType, int version) {
        getTestPackageNames(installType).stream().forEach(packageName -> {
            long installedVersion = VERSION_CODE_INVALID;
            long expectedVersion = version;

            PackageInfo info = InstallUtils.getPackageInfo(packageName);
            if (info != null) {
                installedVersion = info.getLongVersionCode();
                if (version == VERSION_CODE_INVALID && info.isApex) {
                    // Apex cannot be fully uninstalled, verify default version instead.
                    expectedVersion = VERSION_CODE_DEFAULT;
                }
            }

            assertWithMessage(
                    String.format("%s's versionCode expected to be %d, but was %d",
                            packageName, expectedVersion, installedVersion))
                    .that(installedVersion).isEqualTo(expectedVersion);
        });
    }

    /**
     * Resolves corresponding test packages.
     *
     * @note This method should be aligned with {@link INSTALL_TYPE}
     */
    private static List<String> getTestPackageNames(INSTALL_TYPE installType) {
        switch (installType) {
            case SINGLE_APK:
                return Arrays.asList(TestApp.A);
            case SINGLE_APEX:
                return Arrays.asList(SHIM_APEX_PACKAGE_NAME);
            case MULTIPLE_APKS:
                return Arrays.asList(TestApp.A, TestApp.B);
            case MULTIPLE_MIX:
                return Arrays.asList(TestApp.A, SHIM_APEX_PACKAGE_NAME);
            default:
                throw new AssertionError("Unknown install type");
        }
    }

    private static TestApp getTestApp(String packageName, int version) {
        if (!sTestAppMap.contains(packageName, version)) {
            throw new AssertionError("Unknown test app");
        }
        return sTestAppMap.get(packageName, version);
    }

    private static Table<String, Integer, TestApp> getTestAppTable() {
        Table<String, Integer, TestApp> testAppMap = HashBasedTable.create();
        testAppMap.put(TestApp.A, 1, TestApp.A1);
        testAppMap.put(TestApp.A, 2, TestApp.A2);
        testAppMap.put(TestApp.A, 3, TestApp.A3);
        testAppMap.put(TestApp.B, 1, TestApp.B1);
        testAppMap.put(TestApp.B, 2, TestApp.B2);
        testAppMap.put(TestApp.B, 3, TestApp.B3);
        testAppMap.put(SHIM_APEX_PACKAGE_NAME, 1, TestApp.Apex1);
        testAppMap.put(SHIM_APEX_PACKAGE_NAME, 2, TestApp.Apex2);
        testAppMap.put(SHIM_APEX_PACKAGE_NAME, 3, TestApp.Apex3);
        return testAppMap;
    }
}
