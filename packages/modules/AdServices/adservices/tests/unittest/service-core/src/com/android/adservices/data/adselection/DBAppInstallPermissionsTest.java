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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class DBAppInstallPermissionsTest {
    public static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("example.com");
    public static final String PACKAGE_NAME = "package.name";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildDBAppInstall() {
        DBAppInstallPermissions appInstall =
                new DBAppInstallPermissions.Builder()
                        .setBuyer(BUYER)
                        .setPackageName(PACKAGE_NAME)
                        .build();

        assertEquals(BUYER, appInstall.getBuyer());
        assertEquals(PACKAGE_NAME, appInstall.getPackageName());
    }

    @Test
    public void testEquals() {
        DBAppInstallPermissions appInstall1 =
                new DBAppInstallPermissions.Builder()
                        .setBuyer(BUYER)
                        .setPackageName(PACKAGE_NAME)
                        .build();
        DBAppInstallPermissions appInstall2 =
                new DBAppInstallPermissions.Builder()
                        .setBuyer(BUYER)
                        .setPackageName(PACKAGE_NAME)
                        .build();

        assertEquals(appInstall1, appInstall2);
    }

    @Test
    public void testNotEqualDifferentType() {
        DBAppInstallPermissions appInstall =
                new DBAppInstallPermissions.Builder()
                        .setBuyer(BUYER)
                        .setPackageName(PACKAGE_NAME)
                        .build();

        assertNotEquals(new Object(), appInstall);
    }

    @Test
    public void testNotEqualSameType() {
        DBAppInstallPermissions appInstall1 =
                new DBAppInstallPermissions.Builder()
                        .setBuyer(BUYER)
                        .setPackageName(PACKAGE_NAME)
                        .build();
        DBAppInstallPermissions appInstall2 =
                new DBAppInstallPermissions.Builder()
                        .setBuyer(BUYER)
                        .setPackageName(PACKAGE_NAME + "offset")
                        .build();

        assertNotEquals(appInstall2, appInstall1);
    }

    @Test
    public void testToString() {
        DBAppInstallPermissions appInstall =
                new DBAppInstallPermissions.Builder()
                        .setBuyer(BUYER)
                        .setPackageName(PACKAGE_NAME)
                        .build();

        assertEquals(
                "DBAppInstallPermissions{mBuyer="
                        + BUYER
                        + ", mPackageName='"
                        + PACKAGE_NAME
                        + "'}",
                appInstall.toString());
    }

    @Test
    public void testFailsWithNoBuyer() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new DBAppInstallPermissions.Builder().setPackageName(PACKAGE_NAME).build();
                });
    }

    @Test
    public void testFailsWithNoPackageName() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new DBAppInstallPermissions.Builder().setBuyer(BUYER).build();
                });
    }

    @Test
    public void testEqualDBAppInstallObjectsHaveSameHashCode() {
        DBAppInstallPermissions obj1 =
                new DBAppInstallPermissions.Builder()
                        .setBuyer(BUYER)
                        .setPackageName(PACKAGE_NAME)
                        .build();

        DBAppInstallPermissions obj2 =
                new DBAppInstallPermissions.Builder()
                        .setBuyer(BUYER)
                        .setPackageName(PACKAGE_NAME)
                        .build();

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }
}
