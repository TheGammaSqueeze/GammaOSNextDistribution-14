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
package com.android.car.settings.enterprise;

import static com.android.car.settings.enterprise.EnterpriseUtils.getAdminWithinPackage;
import static com.android.car.settings.enterprise.EnterpriseUtils.getDeviceAdminInfo;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasDeviceOwner;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByUm;
import static com.android.car.settings.enterprise.EnterpriseUtils.isAdminUser;
import static com.android.car.settings.enterprise.EnterpriseUtils.isDemoUser;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import android.app.admin.DeviceAdminInfo;
import android.content.ComponentName;
import android.os.UserManager;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Test;

public final class EnterpriseUtilsTest extends BaseEnterpriseTestCase {

    private static final String TEST_RESTRICTION = "test_restriction";

    @Test
    public void testGetAdminWithinPackage_notFound() {
        ComponentName admin = getAdminWithinPackage(mSpiedContext, mPackageName);

        assertWithMessage("Admin for %s", mPackageName).that(admin).isNull();
    }

    @Test
    public void testGetAdminWithinPackage_found() {
        mockGetActiveAdmins(mDefaultAdmin);

        ComponentName admin = getAdminWithinPackage(mSpiedContext, mPackageName);

        assertWithMessage("Admin for %s", mPackageName).that(admin).isEqualTo(mDefaultAdmin);
    }

    @Test
    public void testGetDeviceAdminInfo_notFound() {
        ComponentName admin = new ComponentName("Bond", "James Bond");

        DeviceAdminInfo info = getDeviceAdminInfo(mSpiedContext, admin);

        assertWithMessage("Device admin for %s", admin).that(info).isNull();
    }

    @Test
    public void testGetDeviceAdminInfo_found() {
        DeviceAdminInfo info = getDeviceAdminInfo(mSpiedContext, mDefaultAdmin);

        assertWithMessage("Device admin for %s", mDefaultAdmin).that(info).isNotNull();
        assertWithMessage("Component for %s", info).that(info.getComponent())
                .isEqualTo(mDefaultAdmin);
    }

    @Test
    public void testIsDemoUser_notDemoMode_notDemoUser() {
        ExtendedMockito.doReturn(false)
                .when(() -> UserManager.isDeviceInDemoMode(mSpiedContext));
        when(mUm.isDemoUser()).thenReturn(false);

        assertThat(isDemoUser(mSpiedContext)).isFalse();
    }

    @Test
    public void testIsDemoUser_notDemoMode_demoUser() {
        ExtendedMockito.doReturn(false)
                .when(() -> UserManager.isDeviceInDemoMode(mSpiedContext));
        when(mUm.isDemoUser()).thenReturn(true);

        assertThat(isDemoUser(mSpiedContext)).isFalse();
    }

    @Test
    public void testIsDemoUser_demoMode_notDemoUser() {
        ExtendedMockito.doReturn(false)
                .when(() -> UserManager.isDeviceInDemoMode(mSpiedContext));
        when(mUm.isDemoUser()).thenReturn(false);

        assertThat(isDemoUser(mSpiedContext)).isFalse();
    }

    @Test
    public void testIsDemoUser_demoMode_demoUser() {
        ExtendedMockito.doReturn(true)
                .when(() -> UserManager.isDeviceInDemoMode(mSpiedContext));
        when(mUm.isDemoUser()).thenReturn(true);

        assertThat(isDemoUser(mSpiedContext)).isTrue();
    }

    @Test
    public void testIsAdminUser_true() {
        when(mUm.isAdminUser()).thenReturn(true);

        assertThat(isAdminUser(mSpiedContext)).isTrue();
    }

    @Test
    public void testUserRestriction_notBaseRestricted_notUmRestricted() {
        when(mUm.hasBaseUserRestriction(eq(TEST_RESTRICTION), any())).thenReturn(false);
        when(mUm.hasUserRestriction(TEST_RESTRICTION)).thenReturn(false);

        assertThat(hasUserRestrictionByUm(mSpiedContext, TEST_RESTRICTION)).isFalse();
        assertThat(hasUserRestrictionByDpm(mSpiedContext, TEST_RESTRICTION)).isFalse();
    }

    @Test
    public void testUserRestriction_baseRestricted_umRestricted() {
        when(mUm.hasBaseUserRestriction(eq(TEST_RESTRICTION), any())).thenReturn(true);

        assertThat(hasUserRestrictionByUm(mSpiedContext, TEST_RESTRICTION)).isTrue();
        assertThat(hasUserRestrictionByDpm(mSpiedContext, TEST_RESTRICTION)).isFalse();
    }

    @Test
    public void testUserRestriction_notBaseRestricted_umRestricted() {
        when(mUm.hasBaseUserRestriction(eq(TEST_RESTRICTION), any())).thenReturn(false);
        when(mUm.hasUserRestriction(TEST_RESTRICTION)).thenReturn(true);

        assertThat(hasUserRestrictionByUm(mSpiedContext, TEST_RESTRICTION)).isFalse();
        assertThat(hasUserRestrictionByDpm(mSpiedContext, TEST_RESTRICTION)).isTrue();
    }

    @Test
    public void testHasDeviceOwner_unmanagedDevice() {
        when(mDpm.isDeviceManaged()).thenReturn(false);

        assertThat(hasDeviceOwner(mSpiedContext)).isFalse();
    }

    @Test
    public void testHasDeviceOwner_managedDevice_noDeviceOwnerComponent() {
        when(mDpm.isDeviceManaged()).thenReturn(true);
        when(mDpm.getDeviceOwnerComponentOnAnyUser()).thenReturn(null);

        assertThat(hasDeviceOwner(mSpiedContext)).isFalse();
    }

    @Test
    public void testHasDeviceOwner() {
        when(mDpm.isDeviceManaged()).thenReturn(true);
        when(mDpm.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(new ComponentName("testPackage", "testClass"));

        assertThat(hasDeviceOwner(mSpiedContext)).isTrue();
    }
}
