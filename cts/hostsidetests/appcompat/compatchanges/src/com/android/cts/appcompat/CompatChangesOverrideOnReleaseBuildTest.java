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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.compat.cts.Change;
import android.compat.cts.CompatChangeGatingTestCase;

import com.google.common.collect.ImmutableSet;

public class CompatChangesOverrideOnReleaseBuildTest extends CompatChangeGatingTestCase {
    private static final String TEST_APK = "CtsHostsideCompatChangeTestsApp.apk";
    private static final String TEST_PKG = "com.android.cts.appcompat.compatchanges";

    private static final String OVERRIDE_PKG = "com.android.cts.appcompat.preinstalloverride";
    private static final String OVERRIDE_PKG2 = "com.android.cts.appcompat.preinstalloverride2";

    private static final long CTS_OVERRIDABLE_CHANGE_ID = 174043039L;
    private static final long UNKNOWN_CHANGEID = 123L;

    @Override
    protected void setUp() throws Exception {
        installPackage(TEST_APK, true);
        runCommand("am compat reset-all " + OVERRIDE_PKG);
        runCommand("am compat reset-all " + OVERRIDE_PKG2);
        runCommand("settings put global force_non_debuggable_final_build_for_compat 1");
    }

    @Override
    protected void tearDown() throws Exception {
        runCommand("am compat reset-all " + OVERRIDE_PKG);
        runCommand("am compat reset-all " + OVERRIDE_PKG2);
        uninstallPackage(TEST_PKG, true);
        uninstallPackage(OVERRIDE_PKG, false);
        uninstallPackage(OVERRIDE_PKG2, false);
        runCommand("settings put global force_non_debuggable_final_build_for_compat 0");
    }

    public void testPutPackageOverridesSecurityExceptionNonOverridableChangeId() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "putPackageOverrides_securityExceptionForNonOverridableChangeId",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isFalse();
        assertThat(ctsChange.hasOverrides).isFalse();
    }

    public void testPutPackageOverridesSecurityExceptionMissingPermission() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "putPackageOverrides_securityExceptionForNotHoldingPermission",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isFalse();
        assertThat(ctsChange.hasOverrides).isFalse();
    }

    public void testPutPackageOverridesWhenChangeIdUnknown() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "putPackageOverrides_doesNothingIfChangeIsUnknown",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(UNKNOWN_CHANGEID);
        assertWithMessage("Unknown change %s is found on device", UNKNOWN_CHANGEID)
                .that(ctsChange).isNull();
    }

    public void testPutPackageOverridesForAllVersions() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "putPackageOverrides_success",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo("{" + OVERRIDE_PKG + "=true}");
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo("{" + OVERRIDE_PKG + "=true}");

        // Now update to newer app version and the override should be applied
        installPackage("appcompat_preinstall_override_versioncode2_release.apk", false);

        ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo("{" + OVERRIDE_PKG + "=true}");
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo("{" + OVERRIDE_PKG + "=true}");
    }

    public void testPutPackageOverridesForNewerVersion() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "putPackageOverrides_fromVersion2",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo("{" + OVERRIDE_PKG
                + "=[2,9223372036854775807,true]}");
        assertThat(ctsChange.hasOverrides).isFalse();

        // Now update to newer app version and the override should be applied
        installPackage("appcompat_preinstall_override_versioncode2_release.apk", false);

        ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo("{" + OVERRIDE_PKG
                + "=[2,9223372036854775807,true]}");
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo("{" + OVERRIDE_PKG + "=true}");
    }

    public void testPutPackageOverridesForOlderVersion() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "putPackageOverrides_untilVersion1",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo("{" + OVERRIDE_PKG
                + "=[-9223372036854775808,1,true]}");
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo("{" + OVERRIDE_PKG + "=true}");

        // Now update to newer app version and the override should no longer be applied
        installPackage("appcompat_preinstall_override_versioncode2_release.apk", false);
        ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo("{" + OVERRIDE_PKG
                + "=[-9223372036854775808,1,true]}");
        assertThat(ctsChange.hasOverrides).isFalse();
    }

    public void testPutAllPackageOverrides() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);
        installPackage("appcompat_preinstall_override2_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "putAllPackageOverrides_success",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo(
                "{" + OVERRIDE_PKG + "=true, " + OVERRIDE_PKG2 + "=true}");
        assertThat(ctsChange.hasOverrides).isTrue();
        assertThat(ctsChange.overridesStr).isEqualTo(
                "{" + OVERRIDE_PKG + "=true, " + OVERRIDE_PKG2 + "=true}");
    }

    public void testRemovePackageOverridesSecurityExceptionNonOverridableChangeId()
            throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "removePackageOverrides_securityExceptionForNonOverridableChangeId",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testRemovePackageOverridesSecurityExceptionMissingPermission() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "removePackageOverrides_securityExceptionForNotHoldingPermission",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isTrue();
        assertThat(ctsChange.rawOverrideStr).isEqualTo("{" + OVERRIDE_PKG + "=true}");
    }

    public void testRemovePackageOverridesWhenOverrideNotPresent() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "removePackageOverrides_doesNothingIfOverrideNotPresent",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isFalse();
        assertThat(ctsChange.hasOverrides).isFalse();
    }

    public void testRemovePackageOverridesWhenChangeIdUnknown() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "removePackageOverrides_doesNothingIfChangeIsUnknown",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(UNKNOWN_CHANGEID);
        assertWithMessage("Unknown change %s is found on device", UNKNOWN_CHANGEID)
                .that(ctsChange).isNull();
    }

    public void testRemovePackageOverridesWhenOverridePresent() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "removePackageOverrides_overridePresentSuccess",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isFalse();
        assertThat(ctsChange.hasOverrides).isFalse();
    }

    public void testRemoveAllPackageOverridesWhenOverridePresent() throws Exception {
        installPackage("appcompat_preinstall_override_versioncode1_release.apk", false);
        installPackage("appcompat_preinstall_override2_versioncode1_release.apk", false);

        runDeviceCompatTest(TEST_PKG, ".CompatChangesTest",
                "removeAllPackageOverrides_overridePresentSuccess",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        Change ctsChange = getOnDeviceChangeIdConfig(CTS_OVERRIDABLE_CHANGE_ID);
        assertWithMessage("CTS specific change %s not found on device", CTS_OVERRIDABLE_CHANGE_ID)
                .that(ctsChange).isNotNull();
        assertThat(ctsChange.hasRawOverrides).isFalse();
        assertThat(ctsChange.hasOverrides).isFalse();
    }
}
