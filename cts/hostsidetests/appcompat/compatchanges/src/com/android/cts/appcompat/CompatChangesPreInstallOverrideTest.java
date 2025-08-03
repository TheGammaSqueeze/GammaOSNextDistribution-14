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

package com.android.cts.appcompat;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeThat;

import android.compat.cts.Change;
import android.compat.cts.CompatChangeGatingTestCase;

import java.util.List;

public final class CompatChangesPreInstallOverrideTest extends CompatChangeGatingTestCase {
    private static final String TEST_PACKAGE = "com.android.cts.appcompat.preinstalloverride";
    private static final String CTS_CHANGE_ID_NAME = "CTS_SYSTEM_API_CHANGEID";
    private static final long CTS_CHANGE_ID = 149391281L;

    @Override
    protected void setUp() throws Exception {
        uninstallPackage(TEST_PACKAGE, false);
        runCommand("am compat reset-all " + TEST_PACKAGE);
        runCommand("settings put global force_non_debuggable_final_build_for_compat 1");
    }
    @Override
    protected void tearDown() throws Exception {
        uninstallPackage(TEST_PACKAGE, false);
        runCommand("am compat reset-all " + TEST_PACKAGE);
        runCommand("settings put global force_non_debuggable_final_build_for_compat 0");
    }

    public void testDeferEnablingChangeIdAppNotInstalled() throws Exception {
        runCommand("am compat enable " + CTS_CHANGE_ID + " " + TEST_PACKAGE);
        try {
            Change ctsChange = getCtsChange();
            assertWithMessage("CTS specific change %s not found on device", CTS_CHANGE_ID_NAME)
                .that(ctsChange).isNotNull();
            assertThat(ctsChange.hasRawOverrides).isTrue();
            assertThat(ctsChange.rawOverrideStr).isEqualTo("{" + TEST_PACKAGE + "=true}");
            assertThat(ctsChange.hasOverrides).isFalse();

        } finally {
            runCommand("am compat reset " + CTS_CHANGE_ID + " " + TEST_PACKAGE);
        }
    }

    public void testDeferredOverrideBecomesRegularOverridePostInstall() throws Exception {
        runCommand("am compat enable " + CTS_CHANGE_ID + " " + TEST_PACKAGE);
        try {
            installPackage("appcompat_preinstall_override_versioncode1_debuggable.apk", false);

            Change ctsChange = getCtsChange();
            assertWithMessage("CTS specific change %s not found on device", CTS_CHANGE_ID_NAME)
                .that(ctsChange).isNotNull();
            assertThat(ctsChange.hasRawOverrides).isTrue();
            assertThat(ctsChange.hasOverrides).isTrue();
            assertThat(ctsChange.overridesStr).isEqualTo("{" + TEST_PACKAGE + "=true}");

        } finally {
            runCommand("am compat reset " + CTS_CHANGE_ID + " " + TEST_PACKAGE);
        }
    }

    public void testDeferredOverrideRemainsDeferredPostInstall() throws Exception {
        runCommand("am compat enable " + CTS_CHANGE_ID + " " + TEST_PACKAGE);
        try {
            installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

            Change ctsChange = getCtsChange();
            assertWithMessage("CTS specific change %s not found on device", CTS_CHANGE_ID_NAME)
                .that(ctsChange).isNotNull();
            assertThat(ctsChange.hasRawOverrides).isTrue();
            assertThat(ctsChange.rawOverrideStr).isEqualTo("{" + TEST_PACKAGE + "=true}");
            assertThat(ctsChange.hasOverrides).isFalse();

        } finally {
            runCommand("am compat reset " + CTS_CHANGE_ID + " " + TEST_PACKAGE);
        }
    }

    public void testDeferredOverrideBecomesRegularOverridePostUpdate() throws Exception {
        runCommand("am compat enable " + CTS_CHANGE_ID + " " + TEST_PACKAGE);
        try {
            installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);
            installPackage("appcompat_preinstall_override_versioncode2_debuggable.apk", false);

            Change ctsChange = getCtsChange();
            assertWithMessage("CTS specific change %s not found on device", CTS_CHANGE_ID_NAME)
                .that(ctsChange).isNotNull();
            assertThat(ctsChange.hasRawOverrides).isTrue();
            assertThat(ctsChange.hasOverrides).isTrue();
            assertThat(ctsChange.overridesStr).isEqualTo("{" + TEST_PACKAGE + "=true}");

        } finally {
            runCommand("am compat reset " + CTS_CHANGE_ID + " " + TEST_PACKAGE);
        }
    }

    public void testOverrideBecomesDeferredPostUpdate() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_debuggable.apk", false);
        runCommand("am compat enable " + CTS_CHANGE_ID + " " + TEST_PACKAGE);
        try {
            installPackage("appcompat_preinstall_override_versioncode2_release.apk", false);

            Change ctsChange = getCtsChange();
            assertWithMessage("CTS specific change %s not found on device", CTS_CHANGE_ID_NAME)
                .that(ctsChange).isNotNull();
            assertThat(ctsChange.hasRawOverrides).isTrue();
            assertThat(ctsChange.rawOverrideStr).isEqualTo("{" + TEST_PACKAGE + "=true}");
            assertThat(ctsChange.hasOverrides).isFalse();

        } finally {
            runCommand("am compat reset " + CTS_CHANGE_ID + " " + TEST_PACKAGE);
        }
    }

    private Change getCtsChange() throws Exception {
        return getOnDeviceChangeIdConfig(CTS_CHANGE_ID);
    }
}
