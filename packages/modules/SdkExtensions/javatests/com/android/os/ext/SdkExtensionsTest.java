/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.os.ext;

import static android.os.Build.VERSION_CODES;
import static android.os.Build.VERSION_CODES.R;
import static android.os.Build.VERSION_CODES.S;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.os.ext.SdkExtensions.AD_SERVICES;

import static com.android.os.ext.testing.CurrentVersion.CURRENT_TRAIN_VERSION;
import static com.android.os.ext.testing.CurrentVersion.R_BASE_VERSION;
import static com.android.os.ext.testing.CurrentVersion.S_BASE_VERSION;
import static com.android.os.ext.testing.CurrentVersion.T_BASE_VERSION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ModuleInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemProperties;
import android.os.ext.SdkExtensions;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;
import com.android.os.ext.testing.DeriveSdk;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class SdkExtensionsTest {

    private static final String TAG = "SdkExtensionsTest";

    private enum Expectation {
        /** Expect an extension to be the current / latest defined version */
        CURRENT,
        /** Expect an extension to be missing / version 0 */
        MISSING,
        /** Expect an extension to be at least the base extension version of the device */
        AT_LEAST_BASE,
    }

    private static final Expectation CURRENT = Expectation.CURRENT;
    private static final Expectation MISSING = Expectation.MISSING;
    private static final Expectation AT_LEAST_BASE = Expectation.AT_LEAST_BASE;

    private static void assertAtLeastBaseVersion(int version) {
        int minVersion = R_BASE_VERSION;
        if (SdkLevel.isAtLeastU()) {
            minVersion = CURRENT_TRAIN_VERSION;
        } else if (SdkLevel.isAtLeastT()) {
            minVersion = T_BASE_VERSION;
        } else if (SdkLevel.isAtLeastS()) {
            minVersion = S_BASE_VERSION;
        }
        assertThat(version).isAtLeast(minVersion);
        assertThat(version).isAtMost(CURRENT_TRAIN_VERSION);
    }

    private static void assertVersion(Expectation expectation, int version) {
        switch (expectation) {
            case CURRENT:
                assertEquals(CURRENT_TRAIN_VERSION, version);
                break;
            case AT_LEAST_BASE:
                assertAtLeastBaseVersion(version);
                break;
            case MISSING:
                assertEquals(0, version);
                break;
        }
    }

    private static void assertVersion(Expectation expectation, String propValue) {
        if (expectation == Expectation.MISSING) {
            assertEquals("", propValue);
        } else {
            int version = Integer.parseInt(propValue);
            assertVersion(expectation, version);
        }
    }

    public static final void assertVersion(Expectation expectation, int extension, String propId) {
        String prop = "build.version.extensions." + propId;
        assertVersion(expectation, SystemProperties.get(prop));
        assertVersion(expectation, SdkExtensions.getExtensionVersion(extension));
        if (expectation != Expectation.MISSING) {
            int v = SdkExtensions.getAllExtensionVersions().get(extension);
            assertVersion(expectation, v);
        }
    }

    /* This method runs the copy of the dump code that is bundled inside the test APK. */
    @BeforeClass
    public static void runTestDeriveSdkDump() {
        Log.i(TAG, "derive_sdk dump (bundled with test):");

        for (String line : DeriveSdk.dump()) {
            Log.i(TAG, "  " + line);
        }
    }

    /** Verify that getExtensionVersion only accepts valid extension SDKs */
    @Test
    public void testBadArgument() throws Exception {
        // R is the first SDK version with extensions. Ideally, we'd test all <R values,
        // but it would take too long, so take 10k samples.
        int step = (int) ((VERSION_CODES.R - (long) Integer.MIN_VALUE) / 10_000);
        for (int sdk = Integer.MIN_VALUE; sdk < VERSION_CODES.R; sdk += step) {
            final int finalSdk = sdk;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SdkExtensions.getExtensionVersion(finalSdk));
        }
    }

    /** Verifies that getExtensionVersion returns zero value for non-existing extensions */
    @Test
    public void testZeroValues() throws Exception {
        Set<Integer> assignedCodes = Set.of(R, S, TIRAMISU, UPSIDE_DOWN_CAKE, AD_SERVICES);
        for (int sdk = VERSION_CODES.R; sdk <= 1_000_000; sdk++) {
            if (assignedCodes.contains(sdk)) {
                continue;
            }
            // No extension SDKs yet.
            int version = SdkExtensions.getExtensionVersion(sdk);
            assertEquals("Extension ID " + sdk + " has non-zero version", 0, version);
        }
    }

    @Test
    public void testGetAllExtensionVersionsKeys() throws Exception {
        Set<Integer> expectedKeys = new HashSet<>();
        expectedKeys.add(VERSION_CODES.R);
        if (SdkLevel.isAtLeastS()) {
            expectedKeys.add(VERSION_CODES.S);
        }
        if (SdkLevel.isAtLeastT()) {
            expectedKeys.add(VERSION_CODES.TIRAMISU);
            expectedKeys.add(AD_SERVICES);
        }
        if (SdkLevel.isAtLeastU()) {
            expectedKeys.add(UPSIDE_DOWN_CAKE);
        }
        Set<Integer> actualKeys = SdkExtensions.getAllExtensionVersions().keySet();
        assertThat(actualKeys).containsExactlyElementsIn(expectedKeys);
    }

    @Test
    public void testExtensionR() throws Exception {
        Expectation expectation = dessertExpectation(true);
        assertVersion(expectation, R, "r");
    }

    @Test
    public void testExtensionS() throws Exception {
        Expectation expectation = dessertExpectation(SdkLevel.isAtLeastS());
        assertVersion(expectation, S, "s");
    }

    @Test
    public void testExtensionT() throws Exception {
        Expectation expectation = dessertExpectation(SdkLevel.isAtLeastT());
        assertVersion(expectation, TIRAMISU, "t");
    }

    @Test
    public void testExtensionU() throws Exception {
        Expectation expectation = dessertExpectation(SdkLevel.isAtLeastU());
        assertVersion(expectation, UPSIDE_DOWN_CAKE, "u");
    }

    @Test
    public void testExtensionAdServices() throws Exception {
        // Go trains do not ship the latest versions of AdServices, though they should. Temporarily
        // accept AT_LEAST_BASE of AdServices until the Go train situation has been resolved, then
        // revert back to expecting MISSING (before T) or CURRENT (on T+).
        Expectation expectation = dessertExpectation(SdkLevel.isAtLeastT());
        assertVersion(expectation, AD_SERVICES, "ad_services");
    }

    private Expectation dessertExpectation(boolean expectedPresent) throws Exception {
        if (!expectedPresent) {
            return MISSING;
        }
        // Go trains don't include all modules, so even when all trains for a particular release
        // have been installed correctly on a Go device, we can't generally expect the extension
        // version to be the current train version.
        return SdkLevel.isAtLeastT() && isGoWithSideloadedModules() ? AT_LEAST_BASE : CURRENT;
    }

    private boolean isGoWithSideloadedModules() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean isGoDevice = context.getSystemService(ActivityManager.class).isLowRamDevice();
        if (!isGoDevice) {
            return false;
        }

        PackageManager packageManager = context.getPackageManager();
        boolean anyApexesSideloaded = false;
        for (ModuleInfo module : packageManager.getInstalledModules(0)) {
            boolean sideloaded = isSideloadedApex(packageManager, module.getPackageName());
            anyApexesSideloaded |= sideloaded;
        }
        return anyApexesSideloaded;
    }

    private static boolean isSideloadedApex(PackageManager packageManager, String packageName)
            throws Exception {
        int flags = PackageManager.MATCH_APEX;
        PackageInfo currentInfo = packageManager.getPackageInfo(packageName, flags);
        if (!currentInfo.isApex) {
            return false;
        }
        flags |= PackageManager.MATCH_FACTORY_ONLY;
        PackageInfo factoryInfo = packageManager.getPackageInfo(packageName, flags);
        return !factoryInfo.applicationInfo.sourceDir.equals(currentInfo.applicationInfo.sourceDir);
    }
}
