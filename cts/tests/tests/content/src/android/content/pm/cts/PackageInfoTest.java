/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.content.pm.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.Attribution;
import android.content.pm.ComponentInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.Flags;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@AppModeFull // TODO(Instant) Figure out which APIs should work.
public class PackageInfoTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    private static final String PACKAGE_NAME = "android.content.cts";

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private PackageInfo getPackageInfo() throws Exception {
        return getContext().getPackageManager().getPackageInfo(PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_GIDS
                                | PackageManager.GET_CONFIGURATIONS
                                | PackageManager.GET_INSTRUMENTATION
                                | PackageManager.GET_PERMISSIONS
                                | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS
                                | PackageManager.GET_SERVICES | PackageManager.GET_ATTRIBUTIONS_LONG
                                | PackageManager.GET_SIGNATURES
                                | PackageManager.GET_UNINSTALLED_PACKAGES));
    }

    @Test
    public void testSimple() {
        PackageInfo info = new PackageInfo();
        assertNotNull(info.toString());
    }

    @Test
    public void testPackageInfoOp() {
        // Test constructor, describeContents, toString
        final PackageInfo packageInfo = new PackageInfo();
        assertEquals(0, packageInfo.describeContents());
        assertNotNull(packageInfo.toString());

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        packageInfo.writeToParcel(p, 0);
        p.setDataPosition(0);
        final PackageInfo packageInfoCmp = PackageInfo.CREATOR.createFromParcel(p);
        checkPkgInfoSame(packageInfo, packageInfoCmp);
        p.recycle();
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testApplicationInfoSame() throws Exception {
        PackageInfo packageInfo = getPackageInfo();
        ApplicationInfo ai = packageInfo.applicationInfo;

        // Make sure all the components in it has the same ApplicationInfo.
        for (ComponentInfo[] ar : new ComponentInfo[][]{
                packageInfo.activities, packageInfo.services, packageInfo.providers,
                packageInfo.receivers}) {
            for (ComponentInfo ci : ar) {
                assertSame("component=" + ci.getComponentName(), ai, ci.applicationInfo);
            }
        }
    }

    private void checkPkgInfoSame(PackageInfo expected, PackageInfo actual) {
        assertEquals(expected.packageName, actual.packageName);
        assertEquals(expected.getLongVersionCode(), actual.getLongVersionCode());
        assertEquals(expected.versionName, actual.versionName);
        assertEquals(expected.sharedUserId, actual.sharedUserId);
        assertEquals(expected.sharedUserLabel, actual.sharedUserLabel);
        if (Flags.provideInfoOfApkInApex()) {
            assertThat(expected.getApexPackageName()).isEqualTo(
                    actual.getApexPackageName());
        }
        if (expected.applicationInfo != null) {
            assertNotNull(actual.applicationInfo);
            checkAppInfo(expected.applicationInfo, actual.applicationInfo);
        } else {
            assertNull(actual.applicationInfo);
        }
        assertTrue(Arrays.equals(expected.gids, actual.gids));
        checkInfoArray(expected.activities, actual.activities);
        checkInfoArray(expected.receivers, actual.receivers);
        checkInfoArray(expected.services, actual.services);
        checkInfoArray(expected.providers, actual.providers);
        checkInfoArray(expected.instrumentation, actual.instrumentation);
        checkInfoArray(expected.permissions, actual.permissions);
        assertTrue(Arrays.equals(expected.requestedPermissions, actual.requestedPermissions));
        checkSignatureInfo(expected.signatures, actual.signatures);
        checkConfigInfo(expected.configPreferences, actual.configPreferences);
        checkAttributionInfo(expected.attributions, actual.attributions);
    }

    private void checkAppInfo(ApplicationInfo expected, ApplicationInfo actual) {
        assertEquals(expected.taskAffinity, actual.taskAffinity);
        assertEquals(expected.permission, actual.permission);
        assertEquals(expected.processName, actual.processName);
        assertEquals(expected.className, actual.className);
        assertEquals(expected.theme, actual.theme);
        assertEquals(expected.flags, actual.flags);
        assertEquals(expected.sourceDir, actual.sourceDir);
        assertEquals(expected.publicSourceDir, actual.publicSourceDir);
        assertArrayEquals(expected.sharedLibraryFiles, actual.sharedLibraryFiles);
        assertEquals(expected.dataDir, actual.dataDir);
        assertEquals(expected.uid, actual.uid);
        assertEquals(expected.enabled, actual.enabled);
        assertEquals(expected.manageSpaceActivityName, actual.manageSpaceActivityName);
        assertEquals(expected.descriptionRes, actual.descriptionRes);
    }

    private void checkInfoArray(PackageItemInfo[] expected, PackageItemInfo[] actual) {
        if (expected != null && expected.length > 0) {
            assertNotNull(actual);
            assertEquals(expected.length, actual.length);
            for (int i = 0; i < expected.length; i++) {
                assertEquals(expected[i].name, actual[i].name);
            }
        } else if (expected == null) {
            assertNull(actual);
        } else {
            assertEquals(0, actual.length);
        }
    }

    private void checkSignatureInfo(Signature[] expected, Signature[] actual) {
        if (expected != null && expected.length > 0) {
            assertNotNull(actual);
            assertEquals(expected.length, actual.length);
            for (int i = 0; i < expected.length; i++) {
                assertEquals(expected[i], actual[i]);
            }
        } else if (expected == null) {
            assertNull(actual);
        } else {
            assertEquals(0, actual.length);
        }
    }

    private void checkConfigInfo(ConfigurationInfo[] expected, ConfigurationInfo[] actual) {
        if (expected != null && expected.length > 0) {
            assertNotNull(actual);
            assertEquals(expected.length, actual.length);
            for (int i = 0; i < expected.length; i++) {
                assertEquals(expected[i].reqKeyboardType, actual[i].reqKeyboardType);
                assertEquals(expected[i].reqTouchScreen, actual[i].reqTouchScreen);
                assertEquals(expected[i].reqInputFeatures, actual[i].reqInputFeatures);
                assertEquals(expected[i].reqNavigation, actual[i].reqNavigation);
            }
        } else if (expected == null) {
            assertNull(actual);
        } else {
            assertEquals(0, actual.length);
        }
    }

    private void checkAttributionInfo(Attribution[] expected, Attribution[] actual) {
        if (expected != null && expected.length > 0) {
            assertNotNull(actual);
            assertEquals(expected.length, actual.length);
            for (int i = 0; i < expected.length; i++) {
                assertEquals(actual[i].getTag(), expected[i].getTag());
                assertEquals(actual[i].getTag(), expected[i].getTag());
            }
        } else if (expected == null) {
            assertNull(actual);
        } else {
            assertEquals(0, actual.length);
        }
    }
}
