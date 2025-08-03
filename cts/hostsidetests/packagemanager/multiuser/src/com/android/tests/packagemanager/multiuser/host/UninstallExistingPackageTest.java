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

package com.android.tests.packagemanager.multiuser.host;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(DeviceJUnit4ClassRunner.class)
public class UninstallExistingPackageTest extends PackageManagerMultiUserTestBase {
    private static final String EMPTY_TEST_APP_APK = "CtsPackageManagerMultiUserEmptyTestApp.apk";
    private static final String EMPTY_TEST_APP_PKG =
            "android.packagemanager.multiuser.emptytestapp.cts";
    private static final String ARG_PACKAGE_NAME = "pkgName";

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        installPackageAsUser(EMPTY_TEST_APP_APK, true, mUserId);
        assertTrue(isPackageInstalledForUser(EMPTY_TEST_APP_PKG, mUserId));
    }

    @Test
    @AppModeFull
    public void testUninstallExistingPackage_succeedsIfInstalledInAnotherUser() throws Exception {
        // create a  user
        int newUserId = createUser();

        // install empty test app for both users
        installExistingPackageForUser(EMPTY_TEST_APP_PKG, newUserId);
        assertTrue("Package is not installed for user " + mUserId,
                isPackageInstalledForUser(EMPTY_TEST_APP_PKG, mUserId));
        assertTrue("Package is not installed for user " + newUserId,
                isPackageInstalledForUser(EMPTY_TEST_APP_PKG, newUserId));

        // run uninstallExistingPackage from current user, expect package is uninstalled
        runDeviceTestAsUser("testUninstallExistingPackage", mUserId,
                Collections.singletonMap(ARG_PACKAGE_NAME, EMPTY_TEST_APP_PKG));
        assertFalse(isPackageInstalledForUser(EMPTY_TEST_APP_PKG, mUserId));
        assertTrue(isPackageInstalledForUser(EMPTY_TEST_APP_PKG, newUserId));
    }

    @Test
    @AppModeFull
    public void testUninstallExistingPackage_failsIfInstalledInOnlyOneUser() throws Exception {
        // create a  user
        int newUserId = createUser();

        // assert package is only installed for current user
        assertTrue(isPackageInstalledForUser(EMPTY_TEST_APP_PKG, mUserId));
        assertFalse(isPackageInstalledForUser(EMPTY_TEST_APP_PKG, newUserId));

        // run uninstallExistingPackage from current user, expect package is not uninstalled
        runDeviceTestAsUser("testUninstallExistingPackage", mUserId,
                Collections.singletonMap(ARG_PACKAGE_NAME, EMPTY_TEST_APP_PKG));
        assertTrue(isPackageInstalledForUser(EMPTY_TEST_APP_PKG, mUserId));
    }
}
