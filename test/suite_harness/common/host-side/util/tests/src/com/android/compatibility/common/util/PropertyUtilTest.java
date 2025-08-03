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
 * limitations under the License
 */

package com.android.compatibility.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link PropertyUtil} */
@RunWith(JUnit4.class)
public class PropertyUtilTest {
    private static final String BOARD_API_LEVEL = "ro.board.api_level";
    private static final String BOARD_FIRST_API_LEVEL = "ro.board.first_api_level";
    private static final String FIRST_API_LEVEL = "ro.product.first_api_level";
    private static final String VENDOR_API_LEVEL = "ro.vendor.api_level";
    private static final String VNDK_VERSION = "ro.vndk.version";

    private ITestDevice mMockDevice;

    @Before
    public void setUp() {
        mMockDevice = Mockito.mock(ITestDevice.class);
    }

    @Test
    public void testGetFirstApiLevelFromProductFirstApiLevel() throws DeviceNotAvailableException {
        when(mMockDevice.getProperty(FIRST_API_LEVEL)).thenReturn("31");
        assertEquals(31, PropertyUtil.getFirstApiLevel(mMockDevice));
    }

    @Test
    public void testGetFirstApiLevelFromSdkVersion() throws DeviceNotAvailableException {
        when(mMockDevice.getProperty(FIRST_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getApiLevel()).thenReturn(31);

        assertEquals(31, PropertyUtil.getFirstApiLevel(mMockDevice));
    }

    @Test
    public void testGetVendorApiLevelFromBoardApiLevel() throws DeviceNotAvailableException {
        when(mMockDevice.getProperty(BOARD_API_LEVEL)).thenReturn("31");

        assertEquals(31, PropertyUtil.getVendorApiLevel(mMockDevice));
    }

    @Test
    public void testGetVendorApiLevelFromBoardFirstApiLevel() throws DeviceNotAvailableException {
        when(mMockDevice.getProperty(BOARD_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getProperty(BOARD_FIRST_API_LEVEL)).thenReturn("31");

        assertEquals(31, PropertyUtil.getVendorApiLevel(mMockDevice));
    }

    @Test
    public void testGetVendorApiLevelFromVndkVersion() throws DeviceNotAvailableException {
        when(mMockDevice.getProperty(BOARD_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getProperty(BOARD_FIRST_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getProperty(VNDK_VERSION)).thenReturn("31");

        assertEquals(31, PropertyUtil.getVendorApiLevel(mMockDevice));
    }

    @Test
    public void testGetVendorApiLevelCurrent() throws DeviceNotAvailableException {
        when(mMockDevice.getProperty(BOARD_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getProperty(BOARD_FIRST_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getProperty(VNDK_VERSION)).thenReturn("S");

        assertEquals(PropertyUtil.API_LEVEL_CURRENT, PropertyUtil.getVendorApiLevel(mMockDevice));
    }

    @Test
    public void testIsVendorApiLevelNewerThan() throws DeviceNotAvailableException {
        when(mMockDevice.getProperty(BOARD_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getProperty(BOARD_FIRST_API_LEVEL)).thenReturn("30");

        assertFalse(PropertyUtil.isVendorApiLevelNewerThan(mMockDevice, 30));
    }

    @Test
    public void testIsVendorApiLevelAtLeast() throws DeviceNotAvailableException {
        when(mMockDevice.getProperty(BOARD_API_LEVEL)).thenReturn("30");

        assertTrue(PropertyUtil.isVendorApiLevelAtLeast(mMockDevice, 30));
    }

    @Test
    public void testGetVsrApiLevelEmptyBoardVersions() throws DeviceNotAvailableException {
        when(mMockDevice.getProperty(VENDOR_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getProperty(BOARD_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getProperty(BOARD_FIRST_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getProperty(FIRST_API_LEVEL)).thenReturn("30");

        assertEquals(30, PropertyUtil.getVsrApiLevel(mMockDevice));
    }

    @Test
    public void testGetVsrApiLevelFromBoardVersions() throws DeviceNotAvailableException {
        when(mMockDevice.getProperty(VENDOR_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getProperty(BOARD_API_LEVEL)).thenReturn(null);
        when(mMockDevice.getProperty(BOARD_FIRST_API_LEVEL)).thenReturn("30");
        when(mMockDevice.getProperty(FIRST_API_LEVEL)).thenReturn("31");

        assertEquals(30, PropertyUtil.getVsrApiLevel(mMockDevice));
    }

    @Test
    public void testGetVsrApiLevelFromVendorApiLevel() throws DeviceNotAvailableException {
        when(mMockDevice.getProperty(VENDOR_API_LEVEL)).thenReturn("202404");

        assertEquals(202404, PropertyUtil.getVsrApiLevel(mMockDevice));
    }
}
