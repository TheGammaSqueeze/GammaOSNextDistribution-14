/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.cts.permissiontest.admin;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.MANAGE_USERS;
import static android.car.Car.CAR_DEVICE_POLICY_SERVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.admin.CarDevicePolicyManager;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class contains security permission tests for the {@link CarDevicePolicyManager}'s APIs.
 */
@RunWith(AndroidJUnit4.class)
public final class CarDevicePolicyManagerPermissionTest extends AbstractCarManagerPermissionTest {

    private CarDevicePolicyManager mManager;

    @Before
    public void setUp() {
        super.connectCar();
        mManager = (CarDevicePolicyManager) mCar.getCarManager(CAR_DEVICE_POLICY_SERVICE);
    }

    @Test
    public void testRemoveUserPermission() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mManager.removeUser(UserHandle.of(100)));
        assertThat(e.getMessage()).contains(CREATE_USERS);
        assertThat(e.getMessage()).contains(MANAGE_USERS);
    }

    @Test
    public void testCreateUserPermission() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mManager.createUser("DaUser", CarDevicePolicyManager.USER_TYPE_REGULAR));
        assertThat(e.getMessage()).contains(CREATE_USERS);
        assertThat(e.getMessage()).contains(MANAGE_USERS);
    }

    @Test
    public void testStartUserInBackgroundPermission() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mManager.startUserInBackground(UserHandle.of(100)));
        assertThat(e.getMessage()).contains(CREATE_USERS);
        assertThat(e.getMessage()).contains(MANAGE_USERS);
    }

    @Test
    public void testStopUserPermission() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mManager.stopUser(UserHandle.of(100)));
        assertThat(e.getMessage()).contains(CREATE_USERS);
        assertThat(e.getMessage()).contains(MANAGE_USERS);
    }
}
