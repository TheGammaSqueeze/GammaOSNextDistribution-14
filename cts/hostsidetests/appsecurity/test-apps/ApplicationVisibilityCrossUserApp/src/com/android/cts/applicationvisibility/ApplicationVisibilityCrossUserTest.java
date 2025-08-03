/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.cts.applicationvisibility;

import static android.content.pm.PackageManager.MATCH_KNOWN_PACKAGES;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public class ApplicationVisibilityCrossUserTest {
    private String TINY_PKG = "android.appsecurity.cts.tinyapp";
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
    }

    /** Tests getting installed packages for the current user */
    @Test
    public void testPackageVisibility_currentUser() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final List<PackageInfo> packageList =
                pm.getInstalledPackagesAsUser(0, mContext.getUserId());
        assertFalse(isAppInPackageList(TINY_PKG, packageList));
    }

    /** Tests getting installed packages for all users, with cross user permission granted */
    @Test
    public void testPackageVisibility_anyUserCrossUserGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final List<PackageInfo> packageList =
                pm.getInstalledPackagesAsUser(MATCH_KNOWN_PACKAGES, mContext.getUserId());
        assertTrue(isAppInPackageList(TINY_PKG, packageList));
    }

    /** Tests getting installed packages for all users, with cross user permission revoked */
    @Test
    public void testPackageVisibility_anyUserCrossUserNoGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        try {
            ungrantAcrossUsersPermission();
            final List<PackageInfo> packageList =
                    pm.getInstalledPackagesAsUser(MATCH_KNOWN_PACKAGES, mContext.getUserId());
            fail("Should have received a security exception");
        } catch (SecurityException ignore) {}
    }

    /** Tests getting installed packages for another user, with cross user permission granted */
    @Test
    public void testPackageVisibility_otherUserGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final List<PackageInfo> packageList =
                pm.getInstalledPackagesAsUser(0, getTestUser());
        assertTrue(isAppInPackageList(TINY_PKG, packageList));
    }

    /** Tests getting installed packages for another user, with cross user permission revoked */
    @Test
    public void testPackageVisibility_otherUserNoGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        try {
            ungrantAcrossUsersPermission();
            final List<PackageInfo> packageList =
                    pm.getInstalledPackagesAsUser(0, getTestUser());
            fail("Should have received a security exception");
        } catch (SecurityException ignore) {}
    }

    /** Tests getting installed applications for the current user */
    @Test
    public void testApplicationVisibility_currentUser() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final List<ApplicationInfo> applicationList =
                pm.getInstalledApplicationsAsUser(0, mContext.getUserId());
        assertFalse(isAppInApplicationList(TINY_PKG, applicationList));
    }

    /** Tests getting installed applications for all users, with cross user permission granted */
    @Test
    public void testApplicationVisibility_anyUserCrossUserGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final List<ApplicationInfo> applicationList =
                pm.getInstalledApplicationsAsUser(MATCH_KNOWN_PACKAGES, mContext.getUserId());
        assertTrue(isAppInApplicationList(TINY_PKG, applicationList));
    }

    /** Tests getting installed applications for all users, with cross user permission revoked */
    @Test
    public void testApplicationVisibility_anyUserCrossUserNoGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        try {
            ungrantAcrossUsersPermission();
            final List<ApplicationInfo> applicationList =
                    pm.getInstalledApplicationsAsUser(MATCH_KNOWN_PACKAGES, mContext.getUserId());
            fail("Should have received a security exception");
        } catch (SecurityException ignore) {}
    }

    /** Tests getting installed applications for another user, with cross user permission granted */
    @Test
    public void testApplicationVisibility_otherUserGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final List<ApplicationInfo> applicationList =
                pm.getInstalledApplicationsAsUser(0, getTestUser());
        assertTrue(isAppInApplicationList(TINY_PKG, applicationList));
    }

    /** Tests getting installed applications for another user, with cross user permission revoked */
    @Test
    public void testApplicationVisibility_otherUserNoGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        try {
            ungrantAcrossUsersPermission();
            final List<ApplicationInfo> applicationList =
                    pm.getInstalledApplicationsAsUser(0, getTestUser());
            fail("Should have received a security exception");
        } catch (SecurityException ignore) {}
    }

    /** Tests getting installed packages for the current user */
    @Test
    public void testGetPackagesForUidVisibility_currentUser() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final int userId = mContext.getUserId();
        final int firstAppUid = UserHandle.getUid(userId, Process.FIRST_APPLICATION_UID);
        final int lastAppUid = UserHandle.getUid(userId, Process.LAST_APPLICATION_UID);
        boolean found = false;
        for (int appUid = firstAppUid; appUid < lastAppUid; appUid++) {
            found = isAppInPackageNamesArray(TINY_PKG, pm.getPackagesForUid(appUid));
            if (found) break;
        }
        assertFalse(found);
    }

    /** Tests getting installed packages for primary user, with cross user permission granted */
    @Test
    public void testGetPackagesForUidVisibility_anotherUserCrossUserGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        boolean found = false;
        for (int appUid = Process.FIRST_APPLICATION_UID; appUid < Process.LAST_APPLICATION_UID;
                appUid++) {
            found = isAppInPackageNamesArray(TINY_PKG, pm.getPackagesForUid(appUid));
            if (found) break;
        }
        assertTrue(found);
    }

    /** Tests getting installed packages for primary user, with cross user permission revoked */
    @Test
    public void testGetPackagesForUidVisibility_anotherUserCrossUserNoGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        ungrantAcrossUsersPermission();
        try {
            for (int appUid = Process.FIRST_APPLICATION_UID; appUid < Process.LAST_APPLICATION_UID;
                    appUid++) {
                isAppInPackageNamesArray(TINY_PKG, pm.getPackagesForUid(appUid));
            }
            fail("Should have received a security exception");
        } catch (SecurityException e) {}
    }

    /**
     * Tests getting the uid of the installed packages for the current user.
     **/
    @Test
    public void testGetPackageUidVisibility_currentUser() {
        final PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageUid(TINY_PKG, 0 /*flags*/);
            fail("Should have received a NameNotFoundException");
        } catch (PackageManager.NameNotFoundException e) {
            // Expected
        }
    }

    /**
     * Tests getting the uid of the installed packages for primary user,
     * with cross user permission granted.
     **/
    @Test
    public void testGetPackageUidVisibility_anotherUserCrossUserGrant() {
        final PackageManager pm = mContext.createContextAsUser(UserHandle.of(getTestUser()),
                0 /*flags*/).getPackageManager();
        try {
            pm.getPackageUid(TINY_PKG, MATCH_KNOWN_PACKAGES);
        } catch (PackageManager.NameNotFoundException e) {
            fail("Should not receive a NameNotFoundException");
        }
    }

    /**
     * Tests getting the uid of the installed packages for primary user,
     * with cross user permission revoked.
     **/
    @Test
    public void testGetPackageUidVisibility_anotherUserCrossUserNoGrant()
            throws PackageManager.NameNotFoundException {
        final PackageManager pm = mContext.createContextAsUser(UserHandle.of(getTestUser()),
                0 /*flags*/).getPackageManager();
        ungrantAcrossUsersPermission();
        try {
            pm.getPackageUid(TINY_PKG, MATCH_KNOWN_PACKAGES);
            fail("Should have received a SecurityException");
        } catch (SecurityException e) {
            // Expected
        }
    }

    /**
     * Tests getting the gids of the installed packages for the current user.
     **/
    @Test
    public void testGetPackageGidsVisibility_currentUser() {
        final PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageGids(TINY_PKG, 0 /*flags*/);
            fail("Should have received a NameNotFoundException");
        } catch (PackageManager.NameNotFoundException e) {
            // Expected
        }
    }

    /**
     * Tests getting the gids of the installed packages for primary user,
     * with cross user permission granted.
     **/
    @Test
    public void testGetPackageGidsVisibility_anotherUserCrossUserGrant() {
        final PackageManager pm = mContext.createContextAsUser(UserHandle.of(getTestUser()),
                0 /*flags*/).getPackageManager();
        try {
            pm.getPackageUid(TINY_PKG, MATCH_KNOWN_PACKAGES);
        } catch (PackageManager.NameNotFoundException e) {
            fail("Should not receive a NameNotFoundException");
        }
    }

    /**
     * Tests getting the gids of the installed packages for primary user,
     * with cross user permission revoked.
     **/
    @Test
    public void testGetPackageGidsVisibility_anotherUserCrossUserNoGrant()
            throws PackageManager.NameNotFoundException {
        final PackageManager pm = mContext.createContextAsUser(UserHandle.of(getTestUser()),
                0 /*flags*/).getPackageManager();
        ungrantAcrossUsersPermission();
        try {
            pm.getPackageUid(TINY_PKG, MATCH_KNOWN_PACKAGES);
            fail("Should have received a SecurityException");
        } catch (SecurityException e) {
            // Expected
        }
    }

    private boolean isAppInPackageList(String packageName,
            List<PackageInfo> packageList) {
        for (PackageInfo pkgInfo : packageList) {
            if (pkgInfo.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAppInApplicationList(
            String packageName, List<ApplicationInfo> applicationList) {
        for (ApplicationInfo appInfo : applicationList) {
            if (appInfo.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAppInPackageNamesArray(String packageName, String[] packageNames) {
        return packageNames != null && Stream.of(packageNames).anyMatch(
                name -> name.equals(packageName));
    }

    private int getTestUser() {
        final Bundle testArguments = InstrumentationRegistry.getArguments();
        if (testArguments.containsKey("testUser")) {
            try {
                return Integer.parseInt(testArguments.getString("testUser"));
            } catch (NumberFormatException ignore) {}
        }
        return mContext.getUserId();
    }

    private static void ungrantAcrossUsersPermission() {
        final Context context = InstrumentationRegistry.getContext();
        final PackageManager pm = context.getPackageManager();
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            pm.revokeRuntimePermission(context.getPackageName(),
                    "android.permission.INTERACT_ACROSS_USERS", Process.myUserHandle());
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
