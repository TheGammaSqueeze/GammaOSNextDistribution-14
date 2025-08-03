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

package com.android.cts.appcompat;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.compat.cts.Change;
import android.compat.cts.CompatChangeGatingTestCase;
import android.platform.test.annotations.Presubmit;

/**
 * Tests that App Compat overrides are added/removed for a test package via the Device Config flags
 * in the 'app_compat_overrides' namespace, following both flag changes and package updates.
 *
 * <p>Build/Install/Run:
 * atest com.android.cts.appcompat.AppCompatOverridesServiceTest
 */
@Presubmit
public class AppCompatOverridesServiceTest extends CompatChangeGatingTestCase {
    private static final String TEST_APK = "CtsHostsideCompatChangeTestsApp.apk";
    private static final String TEST_PKG = "com.android.cts.appcompat.compatchanges";

    private static final String OVERRIDE_PKG = "com.android.cts.appcompat.preinstalloverride";

    private static final String OVERRIDE_PKG_VERSION_1_FILENAME =
            "appcompat_preinstall_override_versioncode1_release.apk";
    private static final String OVERRIDE_PKG_VERSION_2_FILENAME =
            "appcompat_preinstall_override_versioncode2_release.apk";

    private static final long CTS_CHANGE_ID = 174043039L;

    private static final String DEVICE_CONFIG_ADB_COMMAND_FORMAT =
            "device_config %s app_compat_overrides %s";

    private static final String OVERRIDE_FORMAT = "{%s=%b}";
    private static final String OVERRIDE_WITH_VERSIONS_FORMAT = "{%s=[%d,%d,%b]}";

    private static final String FLAG_OWNED_CHANGE_IDS = "owned_change_ids";
    private static final String FLAG_REMOVE_OVERRIDES = "remove_overrides";

    public static final int WAIT_TIME_MS = 1_000;

    private String initialOwnedChangeIdsValue;
    private String initialRemoveOverridesValue;

    @Override
    protected final void setUp() throws Exception {
        super.setUp();
        installPackage(TEST_APK, true);
        runCommand("am compat reset-all " + OVERRIDE_PKG);
        runCommand("settings put global force_non_debuggable_final_build_for_compat 1");
        deleteFlagValue(OVERRIDE_PKG);
        initialOwnedChangeIdsValue = getFlagValue(FLAG_OWNED_CHANGE_IDS);
        putFlagValue(FLAG_OWNED_CHANGE_IDS, String.valueOf(CTS_CHANGE_ID));
        initialRemoveOverridesValue = getFlagValue(FLAG_REMOVE_OVERRIDES);
        deleteFlagValue(FLAG_REMOVE_OVERRIDES);
    }

    @Override
    protected final void tearDown() throws Exception {
        super.tearDown();
        runCommand("am compat reset-all " + OVERRIDE_PKG);
        uninstallPackage(TEST_PKG, true);
        uninstallPackage(OVERRIDE_PKG, false);
        runCommand("settings put global force_non_debuggable_final_build_for_compat 0");
        deleteFlagValue(OVERRIDE_PKG);
        restoreFlagValue(FLAG_OWNED_CHANGE_IDS, initialOwnedChangeIdsValue);
        restoreFlagValue(FLAG_REMOVE_OVERRIDES, initialRemoveOverridesValue);
    }

    public void testPackageOverrideFlagPackageInstalledAfterFlagAdded() throws Exception {
        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + ":::true");

        Change ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isFalse();

        // Now install the app and the override should be applied
        installPackage(OVERRIDE_PKG_VERSION_1_FILENAME, false);
        RunUtil.getDefault().sleep(WAIT_TIME_MS);

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));

        // Now uninstall the app and the override should be removed
        uninstallPackage(OVERRIDE_PKG, false);
        RunUtil.getDefault().sleep(WAIT_TIME_MS);

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isFalse();
    }

    public void testPackageOverrideFlagForAllVersions() throws Exception {
        installPackage(OVERRIDE_PKG_VERSION_1_FILENAME, false);

        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + ":::true");

        Change ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));

        // Now update to newer app version and the override should still be applied
        installPackage(OVERRIDE_PKG_VERSION_2_FILENAME, false);

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));
    }

    public void testPackageOverrideFlagForNewerVersion() throws Exception {
        installPackage(OVERRIDE_PKG_VERSION_1_FILENAME, false);

        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + ":2::true");

        Change ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_WITH_VERSIONS_FORMAT, OVERRIDE_PKG, 2, Long.MAX_VALUE,
                        true));
        assertThat(ctsChange.hasOverrides).isFalse();

        // Now update to newer app version and the override should be applied
        installPackage(OVERRIDE_PKG_VERSION_2_FILENAME, false);

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_WITH_VERSIONS_FORMAT, OVERRIDE_PKG, 2, Long.MAX_VALUE,
                        true));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));
    }

    public void testPackageOverrideFlagForOlderVersion() throws Exception {
        installPackage(OVERRIDE_PKG_VERSION_1_FILENAME, false);

        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + "::1:true");

        Change ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_WITH_VERSIONS_FORMAT, OVERRIDE_PKG, Long.MIN_VALUE, 1,
                        true));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));

        // Now update to newer app version and the override should no longer be applied
        installPackage(OVERRIDE_PKG_VERSION_2_FILENAME, false);

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_WITH_VERSIONS_FORMAT, OVERRIDE_PKG, Long.MIN_VALUE, 1,
                        true));
        assertThat(ctsChange.hasOverrides).isFalse();
    }

    public void testPackageOverrideFlagWithMultipleVersionRanges() throws Exception {
        installPackage(OVERRIDE_PKG_VERSION_1_FILENAME, false);

        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + "::1:true," + CTS_CHANGE_ID + ":2::false");

        Change ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_WITH_VERSIONS_FORMAT, OVERRIDE_PKG, Long.MIN_VALUE, 1,
                        true));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));

        // Now update to newer app version and another override should be applied
        installPackage(OVERRIDE_PKG_VERSION_2_FILENAME, false);

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_WITH_VERSIONS_FORMAT, OVERRIDE_PKG, 2, Long.MAX_VALUE,
                        false));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, false));
    }

    public void testPackageOverrideFlagChangedToEmpty() throws Exception {
        installPackage(OVERRIDE_PKG_VERSION_1_FILENAME, false);

        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + ":::true");

        Change ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));

        // Now remove entry in flag and the override should be removed
        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + "");

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isFalse();
    }

    public void testPackageOverrideFlagChangedToEmptyThenPackageUninstalled()
            throws Exception {
        installPackage(OVERRIDE_PKG_VERSION_1_FILENAME, false);

        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + ":::true");

        Change ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));

        // Now remove entry in flag and the override should be removed
        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + "");
        uninstallPackage(OVERRIDE_PKG, false);
        RunUtil.getDefault().sleep(WAIT_TIME_MS);

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isFalse();
    }

    public void testPackageOverrideFlagPackageUninstalledAndThenFlagChangedToEmpty()
            throws Exception {
        installPackage(OVERRIDE_PKG_VERSION_1_FILENAME, false);

        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + ":::true");

        Change ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));

        // Now uninstall the app and the override should be removed
        uninstallPackage(OVERRIDE_PKG, false);
        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + "");
        RunUtil.getDefault().sleep(WAIT_TIME_MS);

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isFalse();
    }

    public void testRemoveOverridesFlag() throws Exception {
        installPackage(OVERRIDE_PKG_VERSION_1_FILENAME, false);

        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + ":::false");

        Change ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, false));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, false));

        // Now add the package to the remove overrides flag and the override should be removed
        putFlagValue(FLAG_REMOVE_OVERRIDES, OVERRIDE_PKG + "=*");

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isFalse();

        // Now change the package override flag and the override should still be removed
        putFlagValue(OVERRIDE_PKG, CTS_CHANGE_ID + ":::true");

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isFalse();

        // Now delete the remove overrides flag and the override should be applied again
        deleteFlagValue(FLAG_REMOVE_OVERRIDES);

        ctsChange = getChange(CTS_CHANGE_ID);
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                String.format(OVERRIDE_FORMAT, OVERRIDE_PKG, true));
    }

    private Change getChange(long changeId) throws Exception {
        // Data put by device_config app_compat_overrides need some time to update the data
        // for dumpsys platform_compat so adding some sleep time
        Thread.sleep(WAIT_TIME_MS);
        Change ctsChange = getOnDeviceChangeIdConfig(changeId);
        assertWithMessage("CTS specific change %s not found on device", changeId)
                .that(ctsChange).isNotNull();
        return ctsChange;
    }

    private void restoreFlagValue(String flagName, String initialValue) throws Exception {
        if (initialValue.equals("null")) {
            deleteFlagValue(flagName);
        } else {
            putFlagValue(flagName, initialValue);
        }
    }

    private String getFlagValue(String flagName) throws Exception {
        return runCommand(String.format(DEVICE_CONFIG_ADB_COMMAND_FORMAT, "get", flagName));
    }

    private void deleteFlagValue(String flagName) throws Exception {
        runCommand(String.format(DEVICE_CONFIG_ADB_COMMAND_FORMAT, "delete", flagName));
    }

    private void putFlagValue(String flagName, String value) throws Exception {
        runCommand(String.format(DEVICE_CONFIG_ADB_COMMAND_FORMAT, "put", flagName + " " + value));
    }
}
