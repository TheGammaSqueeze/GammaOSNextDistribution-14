/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.car.cts.permissiontest.wifi;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;
import android.car.feature.Flags;
import android.car.wifi.CarWifiManager;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;

/**
 * This class contains security permission tests for the {@link android.car.wifi.CarWifiManager}'s
 * APIs.
 */
@RunWith(AndroidJUnit4.class)
public class CarWifiManagerPermissionTest extends AbstractCarManagerPermissionTest {
    private CarWifiManager mCarWifiManager;

    @Before
    public void setUp() throws Exception {
        super.connectCar();
        mCarWifiManager = (CarWifiManager) mCar.getCarManager(Car.CAR_WIFI_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PERSIST_AP_SETTINGS)
    public void testCanControlPersistTetheringSettings() {
        expectPermissionException(Car.PERMISSION_READ_PERSIST_TETHERING_SETTINGS,
                () -> mCarWifiManager.canControlPersistTetheringSettings());
    }

    private void expectPermissionException(String permission, ThrowingRunnable runnable) {
        SecurityException thrown = assertThrows(SecurityException.class, runnable);
        assertThat(thrown.getMessage()).isEqualTo("requires " + permission);
    }
}
