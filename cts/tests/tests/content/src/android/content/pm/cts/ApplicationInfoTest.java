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

import static android.content.pm.ApplicationInfo.CATEGORY_ACCESSIBILITY;
import static android.content.pm.ApplicationInfo.CATEGORY_MAPS;
import static android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY;
import static android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED;
import static android.content.pm.ApplicationInfo.FLAG_MULTIARCH;
import static android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.os.Parcel;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.StringBuilderPrinter;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link ApplicationInfo}.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull // TODO(Instant) Figure out which APIs should work.
public class ApplicationInfoTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    private static final String SYNC_ACCOUNT_ACCESS_STUB_PACKAGE_NAME = "com.android.cts.stub";
    private static final String DIRECT_BOOT_UNAWARE_PACKAGE_NAME =
            "android.content.cts.directbootunaware";
    private static final String PARTIALLY_DIRECT_BOOT_AWARE_PACKAGE_NAME =
            "android.content.cts.partiallydirectbootaware";
    private static final String NO_APPLICATION_PACKAGE_NAME =
            "android.content.cts.emptytestapp.stub";

    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";

    private static final String STUB_PACKAGE_APK = SAMPLE_APK_BASE
            + "CtsSyncAccountAccessStubs.apk";

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private String getPackageName() {
        return getContext().getPackageName();
    }

    @Test
    public void testConstructor() {
        ApplicationInfo info = new ApplicationInfo();
        // simple test to ensure packageName is copied by copy constructor
        // TODO: consider expanding to check all member variables
        info.packageName = "com.example";
        ApplicationInfo copy = new ApplicationInfo(info);
        assertEquals(info.packageName, copy.packageName);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testWriteToParcel() throws NameNotFoundException {
        ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(
                getPackageName(), PackageManager.ApplicationInfoFlags.of(0));

        Parcel p = Parcel.obtain();
        applicationInfo.writeToParcel(p, 0);

        p.setDataPosition(0);
        ApplicationInfo info = ApplicationInfo.CREATOR.createFromParcel(p);
        assertEquals(applicationInfo.taskAffinity, info.taskAffinity);
        assertEquals(applicationInfo.permission, info.permission);
        assertEquals(applicationInfo.processName, info.processName);
        assertEquals(applicationInfo.className, info.className);
        assertEquals(applicationInfo.theme, info.theme);
        assertEquals(applicationInfo.flags, info.flags);
        assertEquals(applicationInfo.sourceDir, info.sourceDir);
        assertEquals(applicationInfo.publicSourceDir, info.publicSourceDir);
        assertEquals(applicationInfo.sharedLibraryFiles, info.sharedLibraryFiles);
        assertEquals(applicationInfo.dataDir, info.dataDir);
        assertEquals(applicationInfo.uid, info.uid);
        assertEquals(applicationInfo.enabled, info.enabled);
        assertEquals(applicationInfo.manageSpaceActivityName, info.manageSpaceActivityName);
        assertEquals(applicationInfo.descriptionRes, info.descriptionRes);
    }

    @Test
    public void testToString() {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        assertNotNull(applicationInfo.toString());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testDescribeContents() throws NameNotFoundException {
        ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(
                getPackageName(), PackageManager.ApplicationInfoFlags.of(0));

        assertEquals(0, applicationInfo.describeContents());
    }

    @Test
    public void testDump() {
        ApplicationInfo applicationInfo = new ApplicationInfo();

        StringBuilder sb = new StringBuilder();
        assertEquals(0, sb.length());
        StringBuilderPrinter p = new StringBuilderPrinter(sb);

        String prefix = "";
        applicationInfo.dump(p, prefix);
        assertNotNull(sb.toString());
        assertTrue(sb.length() > 0);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testLoadDescription() throws NameNotFoundException {
        ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(
                getPackageName(), PackageManager.ApplicationInfoFlags.of(0));

        assertNull(applicationInfo.loadDescription(getContext().getPackageManager()));

        final int resHelloWorld = getContext().getResources()
                .getIdentifier("hello_world", "string", getContext().getPackageName());
        applicationInfo.descriptionRes = resHelloWorld;
        assertEquals(getContext().getResources().getString(resHelloWorld),
                applicationInfo.loadDescription(getContext().getPackageManager()));
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void verifyOwnInfo() throws NameNotFoundException {
        ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(
                getPackageName(), PackageManager.ApplicationInfoFlags.of(0));

        assertEquals("Android TestCase", applicationInfo.nonLocalizedLabel);
        final int resSize4848 = getContext().getResources()
                .getIdentifier("size_48x48", "drawable", getContext().getPackageName());
        assertEquals(resSize4848, applicationInfo.icon);
        assertEquals("android.content.cts.MockApplication", applicationInfo.name);
        int flags = FLAG_MULTIARCH | FLAG_SUPPORTS_RTL;
        assertEquals(flags, applicationInfo.flags & flags);
        assertEquals(CATEGORY_PRODUCTIVITY, applicationInfo.category);
    }

    private void installPackage(String apkPath) {
        assertEquals("Success\n", SystemUtil.runShellCommand(
                "pm install -t " + apkPath));
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void verifyDefaultValues() throws NameNotFoundException {
        // Make sure we install the original version of com.android.cts.stub.
        installPackage(STUB_PACKAGE_APK);

        // The application "com.android.cts.stub" does not have any attributes set
        ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(
                SYNC_ACCOUNT_ACCESS_STUB_PACKAGE_NAME, PackageManager.ApplicationInfoFlags.of(0));
        int currentUserId = Process.myUserHandle().getIdentifier();

        assertNull(applicationInfo.className);
        assertNull(applicationInfo.permission);
        assertEquals(SYNC_ACCOUNT_ACCESS_STUB_PACKAGE_NAME, applicationInfo.packageName);
        assertEquals(SYNC_ACCOUNT_ACCESS_STUB_PACKAGE_NAME, applicationInfo.processName);
        assertEquals(SYNC_ACCOUNT_ACCESS_STUB_PACKAGE_NAME, applicationInfo.taskAffinity);
        assertTrue(UserHandle.isApp(applicationInfo.uid));
        assertEquals(0, applicationInfo.theme);
        assertEquals(0, applicationInfo.requiresSmallestWidthDp);
        assertEquals(0, applicationInfo.compatibleWidthLimitDp);
        assertEquals(0, applicationInfo.largestWidthLimitDp);
        assertNotNull(applicationInfo.sourceDir);
        assertEquals(applicationInfo.sourceDir, applicationInfo.publicSourceDir);
        assertNull(applicationInfo.splitSourceDirs);
        assertArrayEquals(applicationInfo.splitSourceDirs, applicationInfo.splitPublicSourceDirs);
        assertEquals("/data/user/" + currentUserId + "/" + SYNC_ACCOUNT_ACCESS_STUB_PACKAGE_NAME,
                applicationInfo.dataDir);
        assertEquals("/data/user_de/" + currentUserId + "/" + SYNC_ACCOUNT_ACCESS_STUB_PACKAGE_NAME,
                applicationInfo.deviceProtectedDataDir);
        assertEquals("/data/user/" + currentUserId + "/" + SYNC_ACCOUNT_ACCESS_STUB_PACKAGE_NAME,
                applicationInfo.credentialProtectedDataDir);
        assertNull(applicationInfo.sharedLibraryFiles);
        assertTrue(applicationInfo.enabled);
        assertNull(applicationInfo.manageSpaceActivityName);
        assertEquals(0, applicationInfo.descriptionRes);
        assertEquals(0, applicationInfo.uiOptions);
        assertEquals(CATEGORY_UNDEFINED, applicationInfo.category);
        assertFalse(applicationInfo.hasFragileUserData());
    }

    @Test(expected=IllegalArgumentException.class)
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void setOwnAppCategory() throws Exception {
        getContext().getPackageManager().setApplicationCategoryHint(getContext().getPackageName(),
                CATEGORY_MAPS);
    }

    @Test(expected=IllegalArgumentException.class)
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void setAppCategoryByNotInstaller() throws Exception {
        getContext().getPackageManager().setApplicationCategoryHint(
                SYNC_ACCOUNT_ACCESS_STUB_PACKAGE_NAME, CATEGORY_MAPS);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testDirectBootUnawareAppIsNotEncryptionAware() throws Exception {
        ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(
                DIRECT_BOOT_UNAWARE_PACKAGE_NAME, PackageManager.ApplicationInfoFlags.of(0));
        assertFalse(applicationInfo.isEncryptionAware());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testDirectBootUnawareAppCategoryIsAccessibility() throws Exception {
        ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(
                DIRECT_BOOT_UNAWARE_PACKAGE_NAME, PackageManager.ApplicationInfoFlags.of(0));
        assertEquals(CATEGORY_ACCESSIBILITY, applicationInfo.category);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testDefaultAppCategoryIsUndefined() throws Exception {
        final ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(
                NO_APPLICATION_PACKAGE_NAME, PackageManager.ApplicationInfoFlags.of(0));
        assertEquals(CATEGORY_UNDEFINED, applicationInfo.category);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testPartiallyDirectBootAwareAppIsEncryptionAware() throws Exception {
        ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(
                PARTIALLY_DIRECT_BOOT_AWARE_PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0));
        assertTrue(applicationInfo.isEncryptionAware());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testWriteToParcelDontSquash() throws Exception {
        // Make sure ApplicationInfo.writeToParcel() doesn't do the "squashing",
        // because Parcel.allowSquashing() isn't called.

        ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(
                getPackageName(), PackageManager.ApplicationInfoFlags.of(0));

        final Parcel p = Parcel.obtain();
        applicationInfo.writeToParcel(p, 0);
        applicationInfo.writeToParcel(p, 0);

        // Don't call Parcel.allowSquashing()

        p.setDataPosition(0);
        final ApplicationInfo copy1 = ApplicationInfo.CREATOR.createFromParcel(p);
        final ApplicationInfo copy2 = ApplicationInfo.CREATOR.createFromParcel(p);

        p.recycle();

        assertNotSame(applicationInfo, copy1);

        // writeToParcel() doesn't do the squashing, so copy1 and copy2 will be different.
        assertNotSame(copy1, copy2);

        // Check several fields to make sure they're properly copied.
        assertEquals(applicationInfo.packageName, copy2.packageName);
        assertEquals(copy1.packageName, copy2.packageName);

        assertEquals(applicationInfo.flags, copy2.flags);
        assertEquals(copy1.flags, copy2.flags);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testWriteToParcelSquash() throws Exception {
        // Make sure ApplicationInfo.writeToParcel() does the "squashing", after
        // Parcel.allowSquashing() is called.

        ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(
                getPackageName(), PackageManager.ApplicationInfoFlags.of(0));

        final Parcel p = Parcel.obtain();

        final boolean prevSquashing = p.allowSquashing(); // Allow squashing.

        applicationInfo.writeToParcel(p, 0);
        applicationInfo.writeToParcel(p, 0);

        p.setDataPosition(0);
        final ApplicationInfo copy1 = ApplicationInfo.CREATOR.createFromParcel(p);
        final ApplicationInfo copy2 = ApplicationInfo.CREATOR.createFromParcel(p);

        p.recycle();

        assertNotSame(applicationInfo, copy1);
        assertSame(copy1, copy2); //

        p.restoreAllowSquashing(prevSquashing);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testIsProduct() throws Exception {
        // The product flag is supported since P. Suppose that devices lauch on Android P may not
        // have product partition.
        assumeFalse(Build.VERSION.DEVICE_INITIAL_SDK_INT <= Build.VERSION_CODES.P);

        final String systemPath = Environment.getRootDirectory().getAbsolutePath();
        final String productPath = Environment.getProductDirectory().getAbsolutePath();
        final String packageName = getPartitionFirstPackageName(systemPath, productPath);
        assertNotNull("Can not find any product packages on " + productPath + " or "
                + systemPath + productPath, packageName);

        final PackageInfo info = getContext().getPackageManager().getPackageInfo(
                packageName.trim(), 0 /* flags */);
        assertTrue(packageName + " is not product package.", info.applicationInfo.isProduct());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testIsVendor() throws Exception {
        final String systemPath = Environment.getRootDirectory().getAbsolutePath();
        final String vendorPath = Environment.getVendorDirectory().getAbsolutePath();
        final String packageName = getPartitionFirstPackageName(systemPath, vendorPath);
        // vendor package may not exist in every builds
        assumeNotNull(packageName);

        final PackageInfo info = getContext().getPackageManager().getPackageInfo(
                packageName.trim(), PackageManager.PackageInfoFlags.of(0));
        assertTrue(packageName + " is not vendor package.", info.applicationInfo.isVendor());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testIsOem() throws Exception {
        final String systemPath = Environment.getRootDirectory().getAbsolutePath();
        final String oemPath = Environment.getOemDirectory().getAbsolutePath();
        final String packageName = getPartitionFirstPackageName(systemPath, oemPath);
        // Oem package may not exist in every builds like aosp.
        assumeNotNull(packageName);

        final PackageInfo info = getContext().getPackageManager().getPackageInfo(
                packageName.trim(), PackageManager.PackageInfoFlags.of(0));
        assertTrue(packageName + " is not oem package.", info.applicationInfo.isOem());
    }

    private String getPartitionFirstPackageName(final String system, final String partition)
            throws Exception {
        // List package with "-f" option which contains package direction, use that to distinguish
        // package partition and find out target package.
        final String output = SystemUtil.runShellCommand(
                InstrumentationRegistry.getInstrumentation(), "pm list package -f -s");
        final String[] packages = output.split("package:");
        assertTrue("No system packages.", packages.length > 0);

        for (int i = 0; i < packages.length; i++) {
            // Split package info to direction and name.
            String[] info = packages[i].split("\\.apk=");
            if (info.length != 2) continue; // Package info need include direction and name.
            if (info[0] != null
                    && (info[0].startsWith(partition + "/") || info[0].startsWith(system + partition + "/"))) {
                return info[1]; // Package name.
            }
        }
        return null;
    }
}
