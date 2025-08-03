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
package android.car.cts.permissiontest;

import static org.junit.Assert.assertThrows;

import android.car.Car;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class contains security permission tests for the {@link Car}'s system APIs.
 */
@RunWith(AndroidJUnit4.class)
public class CarPermissionTest extends AbstractCarManagerPermissionTest {

    @Before
    public void setUp() {
        super.connectCar();
    }

    @Test
    public void testEnableFeaturePermission() throws Exception {
        assertThrows(SecurityException.class, () -> mCar.enableFeature("some feature"));
    }

    @Test
    public void testDisableFeaturePermission() throws Exception {
        assertThrows(SecurityException.class, () -> mCar.disableFeature("some feature"));
    }

    @Test
    public void testGetAllEnabledFeaturesPermission() throws Exception {
        assertThrows(SecurityException.class, () -> mCar.getAllEnabledFeatures());
    }

    @Test
    public void testGetAllPendingDisabledFeaturesPermission() throws Exception {
        assertThrows(SecurityException.class, () -> mCar.getAllPendingDisabledFeatures());
    }

    @Test
    public void testGetAllPendingEnabledFeaturesPermission() throws Exception {
        assertThrows(SecurityException.class, () -> mCar.getAllPendingEnabledFeatures());
    }
}
