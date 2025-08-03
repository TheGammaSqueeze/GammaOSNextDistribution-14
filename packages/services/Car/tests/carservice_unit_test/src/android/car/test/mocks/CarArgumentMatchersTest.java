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

package android.car.test.mocks;

import static android.car.test.mocks.CarArgumentMatchers.intentFor;
import static android.car.test.mocks.CarArgumentMatchers.isProperty;
import static android.car.test.mocks.CarArgumentMatchers.isPropertyWithValues;
import static android.car.test.mocks.CarArgumentMatchers.isUserHandle;
import static android.car.test.mocks.CarArgumentMatchers.isUserInfo;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.test.util.UserTestingHelper;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.os.UserHandle;
import android.os.UserManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class CarArgumentMatchersTest {

    private static final int VEHICLE_PROP_NAME = 42;
    private static final int VEHICLE_PROP_VALUE = 100;
    private static final int USER_ID = 11;

    private static final String INTENT_PACKAGE_NAME =
            "android.car.test.mocks/.CarArgumentMatchersTest";

    private FakeService mFakeService;

    @Mock
    private UserManager mUserManager;

    @Mock
    private IVehicle mIVehicle;

    @Mock
    private IntentFirer mIntentFirer;

    @Mock
    private UserInfoCheck mUserInfoCheck;

    @Before
    public void setUp() {
        mFakeService = new FakeService(mUserManager, mIVehicle, mIntentFirer, mUserInfoCheck);
    }

    @Test
    public void testIsUserInfo() {
        UserTestingHelper.UserInfoBuilder userInfoBuilder =
                new UserTestingHelper.UserInfoBuilder(USER_ID);

        mFakeService.setUserInfo(userInfoBuilder.build());

        verify(mUserInfoCheck).setUserInfo(isUserInfo(USER_ID));
    }

    @Test
    public void testIsUserHandle() {
        when(mUserManager.isUserUnlockingOrUnlocked(
                isUserHandle(UserHandle.USER_SYSTEM))).thenReturn(true);

        assertThat(mFakeService.setUserHandle(UserHandle.SYSTEM)).isTrue();
        verify(mUserManager).isUserUnlockingOrUnlocked(UserHandle.SYSTEM);
    }

    @Test
    public void testIntentFor() {
        Intent intent = new Intent(Intent.ACTION_SEND).setPackage(INTENT_PACKAGE_NAME);

        mFakeService.fireIntent(intent);

        verify(mIntentFirer).fireIntent(
                intentFor(Intent.ACTION_SEND, INTENT_PACKAGE_NAME));
    }

    @Test
    public void testIsProperty() throws Exception {
        when(mIVehicle.set(isProperty(VEHICLE_PROP_NAME))).thenReturn(StatusCode.OK);

        VehiclePropValue prop = new VehiclePropValue();
        prop.prop = VEHICLE_PROP_NAME;
        prop.value = new RawPropValues();
        int actualStatusCode = mFakeService.setVehiclePropValue(prop);

        assertThat(actualStatusCode).isEqualTo(StatusCode.OK);
        verify(mIVehicle).set(prop);
    }

    @Test
    public void testIsPropertyWithValues() throws Exception {
        when(mIVehicle.set(isPropertyWithValues(VEHICLE_PROP_NAME, VEHICLE_PROP_VALUE))).thenReturn(
                StatusCode.OK);

        VehiclePropValue prop = new VehiclePropValue();
        prop.prop = VEHICLE_PROP_NAME;
        prop.value = new RawPropValues();
        prop.value.int32Values = new int[]{VEHICLE_PROP_VALUE};
        int actualStatusCode = mFakeService.setVehiclePropValue(prop);

        assertThat(actualStatusCode).isEqualTo(StatusCode.OK);
        verify(mIVehicle).set(prop);
    }

    private static class FakeService {

        private final UserManager mUserManager;
        private final IVehicle mIVehicle;
        private final IntentFirer mIntentFirer;
        private final UserInfoCheck mUserInfoCheck;

        FakeService(UserManager userManager, IVehicle iVehicle,
                IntentFirer intentFirer, UserInfoCheck userInfoCheck) {
            mUserManager = userManager;
            mIVehicle = iVehicle;
            mIntentFirer = intentFirer;
            mUserInfoCheck = userInfoCheck;
        }

        public boolean setUserHandle(UserHandle userHandle) {
            return mUserManager.isUserUnlockingOrUnlocked(userHandle);
        }

        public int setVehiclePropValue(VehiclePropValue vehicleProp) throws Exception {
            return mIVehicle.set(vehicleProp);
        }

        public void fireIntent(Intent intent) {
            mIntentFirer.fireIntent(intent);
        }

        public void setUserInfo(UserInfo userInfo) {
            mUserInfoCheck.setUserInfo(userInfo);
        }
    }

    private interface IntentFirer {
        void fireIntent(Intent intent);
    }

    private interface UserInfoCheck {
        void setUserInfo(UserInfo userInfo);
    }

    private interface IVehicle {
        int set(VehiclePropValue value);
    }
}
