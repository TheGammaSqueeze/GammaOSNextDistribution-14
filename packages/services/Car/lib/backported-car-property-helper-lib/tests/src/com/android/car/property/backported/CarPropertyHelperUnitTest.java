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

package com.android.car.property.backported;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.car.VehiclePropertyIds.LOCATION_CHARACTERIZATION;
import static android.car.hardware.property.VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.CarPropertyManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.class)
public final class CarPropertyHelperUnitTest {

    private static final int VENDOR_LOCATION_CHARACTERIZATION = 0x31400c10;
    private static final String PROPERTY_NAME = "LOCATION_CHARACTERIZATION";

    private MockitoSession mMockitoSession;

    @Mock
    private CarPropertyManager mMockCarPropertyManager;
    @Mock
    private CarPropertyConfig mMockCarPropertyConfig;

    private CarPropertyHelper mCarPropertyHelper;


    @Before
    public void setUp() {
        mMockitoSession = mockitoSession()
            .strictness(Strictness.LENIENT)
            .initMocks(this)
            .startMocking();
        mCarPropertyHelper = new CarPropertyHelper(mMockCarPropertyManager);
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testGetPropertyId_systemPropertyIdSupported() {
        when(mMockCarPropertyManager.getCarPropertyConfig(LOCATION_CHARACTERIZATION))
                .thenReturn(mMockCarPropertyConfig);

        assertThat(mCarPropertyHelper.getPropertyId(PROPERTY_NAME))
                .isEqualTo(LOCATION_CHARACTERIZATION);
    }

    @Test
    public void testGetPropertyId_vendorPropertyIdSupported() {
        when(mMockCarPropertyManager.getCarPropertyConfig(LOCATION_CHARACTERIZATION))
                .thenReturn(null);
        when(mMockCarPropertyManager.getCarPropertyConfig(VENDOR_LOCATION_CHARACTERIZATION))
                .thenReturn(mMockCarPropertyConfig);

        assertThat(mCarPropertyHelper.getPropertyId(PROPERTY_NAME))
                .isEqualTo(VENDOR_LOCATION_CHARACTERIZATION);
    }

    @Test
    public void testGetPropertyId_vendorPropertyNotSupported() {
        when(mMockCarPropertyManager.getCarPropertyConfig(LOCATION_CHARACTERIZATION))
                .thenReturn(null);
        when(mMockCarPropertyManager.getCarPropertyConfig(VENDOR_LOCATION_CHARACTERIZATION))
                .thenReturn(null);

        assertThat(mCarPropertyHelper.getPropertyId(PROPERTY_NAME)).isNull();
    }

    @Test
    public void testGetPropertyId_invalidPropertyName() {
        assertThat(mCarPropertyHelper.getPropertyId("Invalid_property")).isNull();
    }

    @Test
    public void testGetReadPermissions_systemPermission() {
        when(mMockCarPropertyManager.getCarPropertyConfig(LOCATION_CHARACTERIZATION))
                .thenReturn(mMockCarPropertyConfig);

        assertThat(mCarPropertyHelper.getReadPermission(PROPERTY_NAME))
                .isEqualTo(ACCESS_FINE_LOCATION);
    }

    @Test
    public void testGetReadPermissions_vendorPermission() {
        when(mMockCarPropertyManager.getCarPropertyConfig(LOCATION_CHARACTERIZATION))
                .thenReturn(null);
        when(mMockCarPropertyManager.getCarPropertyConfig(VENDOR_LOCATION_CHARACTERIZATION))
                .thenReturn(mMockCarPropertyConfig);

        assertThat(mCarPropertyHelper.getReadPermission(PROPERTY_NAME))
                .isEqualTo(PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO);
    }

    @Test
    public void testGetWritePermissions_onlyReadable() {
        when(mMockCarPropertyManager.getCarPropertyConfig(LOCATION_CHARACTERIZATION))
                .thenReturn(mMockCarPropertyConfig);

        assertThat(mCarPropertyHelper.getWritePermission(PROPERTY_NAME))
                .isNull();
    }
}
