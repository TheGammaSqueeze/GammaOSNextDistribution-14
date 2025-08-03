/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.uidmigration.cts

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.pm.SharedUidMigration.BEST_EFFORT
import com.android.server.pm.SharedUidMigration.NEW_INSTALL_ONLY
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedUserMigrationTest {

    private lateinit var mContext: Context
    private lateinit var mPm: PackageManager

    @Before
    fun setup() {
        mContext = ApplicationProvider.getApplicationContext<Context>()
        mPm = mContext.packageManager
    }

    @After
    fun tearDown() {
        uninstallPackage(Const.INSTALL_TEST_PKG)
        uninstallPackage(Const.INSTALL_TEST_PKG2)
    }

    // Restore and ensure both test apps are sharing UID.
    private fun reset(uid: Int) {
        uninstallPackage(Const.INSTALL_TEST_PKG)
        assertTrue(installPackage(InstallTest.APK))
        val pkgs = mPm.getPackagesForUid(uid).assertNotNull()
        assertTrue(pkgs.sameAs(Const.INSTALL_TEST_PKG, Const.INSTALL_TEST_PKG2))
    }

    private fun testNewInstallOnly(uid: Int) {
        migrationStrategy = NEW_INSTALL_ONLY

        // Should not allow upgrading to an APK that directly removes sharedUserId.
        assertFalse(installPackage(InstallTest.APK3))

        // Directly parsing APK4 should return no sharedUserId.
        var pkgInfo = mPm.getPackageArchiveInfo(InstallTest.APK4, FLAG_ZERO).assertNotNull()
        assertNull(pkgInfo.sharedUserId)

        assertTrue(installPackage(InstallTest.APK4))
        var pkgs = mPm.getPackagesForUid(uid).assertNotNull()
        // With NEW_INSTALL_ONLY, upgrades should not change appId.
        assertTrue(pkgs.sameAs(Const.INSTALL_TEST_PKG, Const.INSTALL_TEST_PKG2))
        pkgInfo = mPm.getPackageInfo(Const.INSTALL_TEST_PKG, FLAG_ZERO)
        assertNotNull(pkgInfo.sharedUserId)

        // Should not allow re-joining sharedUserId.
        assertFalse(installPackage(InstallTest.APK))

        // Uninstall and install a new pkg leaving shared UID
        uninstallPackage(Const.INSTALL_TEST_PKG)
        assertTrue(installPackage(InstallTest.APK4))
        pkgs = mPm.getPackagesForUid(uid).assertNotNull()
        // Newly installed apps with sharedUserMaxSdkVersion set should not join shared UID.
        assertTrue(pkgs.sameAs(Const.INSTALL_TEST_PKG2))
        pkgInfo = mPm.getPackageInfo(Const.INSTALL_TEST_PKG, FLAG_ZERO)
        assertNull(pkgInfo.sharedUserId)
        // Upgrading an APK with sharedUserMaxSdkVersion set should not change its UID.
        assertTrue(installPackage(InstallTest.APK4))
        val newPkgInfo = mPm.getPackageInfo(Const.INSTALL_TEST_PKG, FLAG_ZERO)
        assertNull(newPkgInfo.sharedUserId)
        assertEquals(checkNotNull(pkgInfo.applicationInfo).uid,
		     checkNotNull(newPkgInfo.applicationInfo).uid)
    }

    private fun testBestEffort(uid: Int) {
        migrationStrategy = BEST_EFFORT

        assertTrue(installPackage(InstallTest.APK4))
        var pkgs = mPm.getPackagesForUid(uid).assertNotNull()
        // With BEST_EFFORT, upgrades should also not change appId.
        assertTrue(pkgs.sameAs(Const.INSTALL_TEST_PKG, Const.INSTALL_TEST_PKG2))
        var pkgInfo = mPm.getPackageInfo(Const.INSTALL_TEST_PKG, FLAG_ZERO)
        assertNotNull(pkgInfo.sharedUserId)

        val oldUidName = mPm.getNameForUid(uid)
        uninstallPackage(Const.INSTALL_TEST_PKG2)

        // There should be only 1 package left in the shared UID group.
        // This should trigger the transparent shared UID migration.
        pkgs = mPm.getPackagesForUid(uid).assertNotNull()
        assertTrue(pkgs.sameAs(Const.INSTALL_TEST_PKG))

        // Confirm that the internal PackageSetting is actually migrated.
        val newUidName = mPm.getNameForUid(uid)
        assertNotEquals(oldUidName, newUidName)
        pkgInfo = mPm.getPackageInfo(Const.INSTALL_TEST_PKG, FLAG_ZERO)
        assertNull(pkgInfo.sharedUserId)

        // Even installing another shared UID app, the appId shall not be reused.
        assertTrue(installPackage(InstallTest.APK2))
        pkgs = mPm.getPackagesForUid(uid).assertNotNull()
        assertTrue(pkgs.sameAs(Const.INSTALL_TEST_PKG))
    }

    @Test
    fun testAppInstall() = withStrategy {
        assertTrue(installPackage(InstallTest.APK))
        assertTrue(installPackage(InstallTest.APK2))

        // Both app should share the same UID.
        val uid = mPm.getPackageUid(Const.INSTALL_TEST_PKG, FLAG_ZERO)
        val pkgs = mPm.getPackagesForUid(uid).assertNotNull()
        assertTrue(pkgs.sameAs(Const.INSTALL_TEST_PKG, Const.INSTALL_TEST_PKG2))

        if (Build.IS_USERDEBUG) {
            testNewInstallOnly(uid)
            reset(uid)
            testBestEffort(uid)
        } else {
            when (migrationStrategy) {
                NEW_INSTALL_ONLY -> testNewInstallOnly(uid)
                BEST_EFFORT -> testBestEffort(uid)
            }
        }

        tearDown()
    }
}
