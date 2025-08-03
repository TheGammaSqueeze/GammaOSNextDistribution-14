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

package com.android.cts.appcompat.compatchanges;

import static android.os.Process.SYSTEM_UID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.compat.CompatChanges;
import android.app.compat.PackageOverride;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testng.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tests for the {@link android.app.compat.CompatChanges} SystemApi.
 *
 * These test methods have additional setup and post run checks done host side by
 * {@link com.android.cts.appcompat.CompatChangesSystemApiTest}.
 *
 * The setup adds an override for the change id being tested, and the post run step checks if
 * that change id has been logged to statsd.
 */
@RunWith(AndroidJUnit4.class)
public final class CompatChangesTest {
    private static final long CTS_SYSTEM_API_CHANGEID = 149391281L;
    private static final long CTS_SYSTEM_API_OVERRIDABLE_CHANGEID = 174043039L;
    private static final long UNKNOWN_CHANGEID = 123L;

    private static final String OVERRIDE_PACKAGE = "com.android.cts.appcompat.preinstalloverride";
    private static final String OVERRIDE_PACKAGE2 = "com.android.cts.appcompat.preinstalloverride2";

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.LOG_COMPAT_CHANGE,
                        Manifest.permission.READ_COMPAT_CHANGE_CONFIG,
                        Manifest.permission.OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD,
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /* Test run by CompatChangesSystemApiTest.testIsChangeEnabled */
    @Test
    public void isChangeEnabled_changeEnabled() {
        assertThat(CompatChanges.isChangeEnabled(CTS_SYSTEM_API_CHANGEID)).isTrue();
    }

    /* Test run by CompatChangesSystemApiTest.testIsChangeEnabledPackageName */
    @Test
    public void isChangeEnabledPackageName_changeEnabled() {
        Context context = InstrumentationRegistry.getTargetContext();
        assertThat(CompatChanges.isChangeEnabled(CTS_SYSTEM_API_CHANGEID, context.getPackageName(),
                context.getUser())).isTrue();
    }

    /* Test run by CompatChangesSystemApiTest.testIsChangeEnabledUid */
    @Test
    public void isChangeEnabledUid_changeEnabled() {
        assertThat(
                CompatChanges.isChangeEnabled(CTS_SYSTEM_API_CHANGEID, Process.myUid())).isTrue();
    }

    /* Test run by CompatChangesSystemApiTest.testIsChangeDisabled */
    @Test
    public void isChangeEnabled_changeDisabled() {
        assertThat(CompatChanges.isChangeEnabled(CTS_SYSTEM_API_CHANGEID)).isFalse();
    }

    /* Test run by CompatChangesSystemApiTest.testIsChangeDisabledPackageName */
    @Test
    public void isChangeEnabledPackageName_changeDisabled() {
        Context context = InstrumentationRegistry.getTargetContext();
        assertThat(CompatChanges.isChangeEnabled(CTS_SYSTEM_API_CHANGEID, context.getPackageName(),
                context.getUser())).isFalse();
    }

    /* Test run by CompatChangesSystemApiTest.testIsChangeDisabledUid */
    @Test
    public void isChangeEnabledUid_changeDisabled() {
        assertThat(
                CompatChanges.isChangeEnabled(CTS_SYSTEM_API_CHANGEID, Process.myUid())).isFalse();
    }

    @Test
    public void putPackageOverrides_securityExceptionForNonOverridableChangeId() {
        SecurityException e = assertThrows(SecurityException.class,
                () -> CompatChanges.putPackageOverrides(OVERRIDE_PACKAGE,
                        Collections.singletonMap(CTS_SYSTEM_API_CHANGEID,
                                new PackageOverride.Builder().setEnabled(true).build())));
        assertThat(e).hasMessageThat().contains("marked as Overridable");
    }

    @Test
    public void putPackageOverrides_doesNothingIfChangeIsUnknown() {
        CompatChanges.putPackageOverrides(OVERRIDE_PACKAGE,
                Collections.singletonMap(UNKNOWN_CHANGEID,
                        new PackageOverride.Builder().setEnabled(true).build()));
    }

    @Test
    public void putPackageOverrides_success() {
        CompatChanges.putPackageOverrides(OVERRIDE_PACKAGE,
                Collections.singletonMap(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID,
                        new PackageOverride.Builder().setEnabled(true).build()));
    }

    @Test
    public void putPackageOverrides_fromVersion2() {
        CompatChanges.putPackageOverrides(OVERRIDE_PACKAGE,
                Collections.singletonMap(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID,
                        new PackageOverride.Builder().setMinVersionCode(2).setEnabled(
                                true).build()));
    }

    @Test
    public void putPackageOverrides_untilVersion1() {
        CompatChanges.putPackageOverrides(OVERRIDE_PACKAGE,
                Collections.singletonMap(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID,
                        new PackageOverride.Builder().setMaxVersionCode(1).setEnabled(
                                true).build()));
    }

    @Test
    public void putPackageOverrides_securityExceptionForNotHoldingPermission() {
        // Adopt the normal override permission that doesn't allow to clear overrides on release
        // builds
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.OVERRIDE_COMPAT_CHANGE_CONFIG);

        SecurityException e = assertThrows(SecurityException.class,
                () -> CompatChanges.putPackageOverrides(OVERRIDE_PACKAGE,
                        Collections.singletonMap(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID,
                                new PackageOverride.Builder().setEnabled(true).build())));
        assertThat(e).hasMessageThat().contains("Access denied");
    }

    @Test
    public void putAllPackageOverrides_success() {
        Map<String, Map<Long, PackageOverride>> packageNameToOverrides = new HashMap<>();
        packageNameToOverrides.put(OVERRIDE_PACKAGE,
                Collections.singletonMap(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID,
                        new PackageOverride.Builder().setEnabled(true).build()));
        packageNameToOverrides.put(OVERRIDE_PACKAGE2,
                Collections.singletonMap(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID,
                        new PackageOverride.Builder().setEnabled(true).build()));
        CompatChanges.putAllPackageOverrides(packageNameToOverrides);
    }

    @Test
    public void removePackageOverrides_securityExceptionForNonOverridableChangeId() {
        SecurityException e = assertThrows(SecurityException.class,
                () -> CompatChanges.removePackageOverrides(OVERRIDE_PACKAGE,
                        Collections.singleton(CTS_SYSTEM_API_CHANGEID)));
        assertThat(e).hasMessageThat().contains("marked as Overridable");
    }

    @Test
    public void removePackageOverrides_doesNothingIfOverrideNotPresent() {
        CompatChanges.removePackageOverrides(OVERRIDE_PACKAGE,
                Collections.singleton(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID));
    }

    @Test
    public void removePackageOverrides_doesNothingIfChangeIsUnknown() {
        CompatChanges.removePackageOverrides(OVERRIDE_PACKAGE,
                Collections.singleton(UNKNOWN_CHANGEID));
    }

    @Test
    public void removePackageOverrides_overridePresentSuccess() {
        CompatChanges.putPackageOverrides(OVERRIDE_PACKAGE,
                Collections.singletonMap(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID,
                        new PackageOverride.Builder().setEnabled(true).build()));
        CompatChanges.removePackageOverrides(OVERRIDE_PACKAGE,
                Collections.singleton(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID));
    }

    @Test
    public void removePackageOverrides_securityExceptionForNotHoldingPermission() {
        CompatChanges.putPackageOverrides(OVERRIDE_PACKAGE,
                Collections.singletonMap(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID,
                        new PackageOverride.Builder().setEnabled(true).build()));

        // Adopt the normal override permission that doesn't allow to clear overrides on release
        // builds
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.OVERRIDE_COMPAT_CHANGE_CONFIG);

        SecurityException e = assertThrows(SecurityException.class,
                () -> CompatChanges.removePackageOverrides(OVERRIDE_PACKAGE,
                        Collections.singleton(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID)));
        assertThat(e).hasMessageThat().contains("Access denied");
    }

    @Test
    public void removeAllPackageOverrides_overridePresentSuccess() {
        CompatChanges.putPackageOverrides(OVERRIDE_PACKAGE,
                Collections.singletonMap(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID,
                        new PackageOverride.Builder().setEnabled(true).build()));
        CompatChanges.putPackageOverrides(OVERRIDE_PACKAGE2,
                Collections.singletonMap(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID,
                        new PackageOverride.Builder().setEnabled(true).build()));

        Map<String, Set<Long>> packageNameToOverridesToRemove = new HashMap<>();
        packageNameToOverridesToRemove.put(OVERRIDE_PACKAGE,
                Collections.singleton(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID));
        packageNameToOverridesToRemove.put(OVERRIDE_PACKAGE2,
                Collections.singleton(CTS_SYSTEM_API_OVERRIDABLE_CHANGEID));
        CompatChanges.removeAllPackageOverrides(packageNameToOverridesToRemove);
    }

    @Ignore
    @Test
    public void testAllPackagesSharingSystemUidTargetLatestSdk()
            throws PackageManager.NameNotFoundException {
        Context context = InstrumentationRegistry.getTargetContext();
        String[] packages = context.getPackageManager().getPackagesForUid(SYSTEM_UID);
        for (String pkg : packages) {
            int targetSdk = context.getPackageManager().getTargetSdkVersion(pkg);
            Assert.assertEquals(targetSdk, Build.VERSION.SDK_INT,
                    pkg + " should target latest android version - " + Build.VERSION.SDK_INT
                            + "as it is sharing"
                            + "uid with system but it is targeting android version - " + targetSdk);

        }
    }
}
