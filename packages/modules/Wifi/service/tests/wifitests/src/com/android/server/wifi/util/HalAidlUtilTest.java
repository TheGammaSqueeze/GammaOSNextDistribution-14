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

package com.android.server.wifi.util;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.hardware.wifi.common.OuiKeyedData;
import android.net.wifi.util.PersistableBundleUtils;
import android.os.PersistableBundle;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link HalAidlUtil}.
 */
public class HalAidlUtilTest {
    private static final int TEST_VENDOR_DATA_LIST_SIZE = 10;

    @Before
    public void setUp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
    }

    private static PersistableBundle createTestPersistableBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("stringKey", "someStringData");
        bundle.putInt("intKey", 55);
        bundle.putIntArray("arrayKey", new int[]{1, 2, 3});
        return bundle;
    }

    private static android.net.wifi.OuiKeyedData createFrameworkOuiKeyedData(int oui) {
        return new android.net.wifi.OuiKeyedData.Builder(
                oui, createTestPersistableBundle()).build();
    }

    private static OuiKeyedData createHalOuiKeyedData(int oui) {
        OuiKeyedData data = new OuiKeyedData();
        data.oui = oui;
        data.vendorData = createTestPersistableBundle();
        return data;
    }

    private static boolean frameworkAndHalOuiKeyedDataEqual(
            android.net.wifi.OuiKeyedData frameworkData,
            android.hardware.wifi.common.OuiKeyedData halData) {
        return (frameworkData.getOui() == halData.oui)
                && PersistableBundleUtils.isEqual(frameworkData.getData(), halData.vendorData);
    }

    /**
     * Test the conversion of a valid framework OuiKeyedData list to its HAL equivalent.
     */
    @Test
    public void testConvertOuiKeyedDataToHal() {
        List<android.net.wifi.OuiKeyedData> frameworkList = new ArrayList<>();
        for (int i = 0; i < TEST_VENDOR_DATA_LIST_SIZE; i++) {
            frameworkList.add(createFrameworkOuiKeyedData(i + 1));
        }

        android.hardware.wifi.common.OuiKeyedData[] halList =
                HalAidlUtil.frameworkToHalOuiKeyedDataList(frameworkList);
        assertEquals(frameworkList.size(), halList.length);
        for (int i = 0; i < TEST_VENDOR_DATA_LIST_SIZE; i++) {
            assertTrue(frameworkAndHalOuiKeyedDataEqual(frameworkList.get(i), halList[i]));
        }
    }

    /**
     * Test the conversion of an invalid OuiKeyedData list. Invalid entries should be ignored.
     */
    @Test
    public void testConvertOuiKeyedDataToHal_invalid() {
        List<android.net.wifi.OuiKeyedData> frameworkList = new ArrayList<>();
        for (int i = 0; i < TEST_VENDOR_DATA_LIST_SIZE; i++) {
            // Fill framework list with null entries.
            frameworkList.add(null);
        }

        // No entries should appear in the converted list.
        android.hardware.wifi.common.OuiKeyedData[] halList =
                HalAidlUtil.frameworkToHalOuiKeyedDataList(frameworkList);
        assertEquals(0, halList.length);
    }

    /**
     * Test the conversion of a valid HAL OuiKeyedData list to its framework equivalent.
     */
    @Test
    public void testConvertOuiKeyedDataToFramework() {
        OuiKeyedData[] halList = new OuiKeyedData[TEST_VENDOR_DATA_LIST_SIZE];
        for (int i = 0; i < TEST_VENDOR_DATA_LIST_SIZE; i++) {
            halList[i] = createHalOuiKeyedData(i + 1);
        }

        List<android.net.wifi.OuiKeyedData> frameworkList =
                HalAidlUtil.halToFrameworkOuiKeyedDataList(halList);
        assertEquals(frameworkList.size(), halList.length);
        for (int i = 0; i < TEST_VENDOR_DATA_LIST_SIZE; i++) {
            assertTrue(frameworkAndHalOuiKeyedDataEqual(frameworkList.get(i), halList[i]));
        }
    }

    /**
     * Test the conversion of an invalid OuiKeyedData list. Invalid entries should be ignored.
     */
    @Test
    public void testConvertOuiKeyedDataToFramework_invalid() {
        OuiKeyedData[] halList = new OuiKeyedData[TEST_VENDOR_DATA_LIST_SIZE];
        for (int i = 0; i < TEST_VENDOR_DATA_LIST_SIZE; i++) {
            // Fill list with entries that have an invalid OUI.
            halList[i] = createHalOuiKeyedData(0);
        }

        // No entries should appear in the converted list.
        List<android.net.wifi.OuiKeyedData> frameworkList =
                HalAidlUtil.halToFrameworkOuiKeyedDataList(halList);
        assertEquals(0, frameworkList.size());
    }
}
