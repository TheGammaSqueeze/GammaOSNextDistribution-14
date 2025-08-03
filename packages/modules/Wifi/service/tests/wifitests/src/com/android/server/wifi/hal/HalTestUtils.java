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

package com.android.server.wifi.hal;

import static junit.framework.Assert.assertEquals;

import static org.mockito.Mockito.when;

import android.hardware.wifi.common.OuiKeyedData;
import android.net.wifi.util.PersistableBundleUtils;
import android.os.PersistableBundle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class HalTestUtils {
    private static final String BUNDLE_STRING_FIELD_KEY = "stringField";
    private static final String BUNDLE_ARRAY_FIELD_KEY = "arrayField";

    private static final String BUNDLE_STRING_FIELD_VALUE = "someString";
    private static final int[] BUNDLE_ARRAY_FIELD_VALUE = new int[] {1, 2, 3};

    /**
     * Check that we get the expected return value when the specified method is called.
     *
     * @param calledMethod Method to call on mDut.
     * @param mockedMethod Method called by mDut to retrieve the value.
     * @param value Value that the mockedMethod should return.
     */
    public static <T> void verifyReturnValue(Supplier<T> calledMethod, T mockedMethod, T value) {
        when(mockedMethod).thenReturn(value);
        T retrievedValue = calledMethod.get();
        assertEquals(value, retrievedValue);
    }

    /**
     * Generate a single PersistableBundle containing several test fields.
     */
    private static PersistableBundle createTestPersistableBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(BUNDLE_STRING_FIELD_KEY, BUNDLE_STRING_FIELD_VALUE);
        bundle.putIntArray(BUNDLE_ARRAY_FIELD_KEY, BUNDLE_ARRAY_FIELD_VALUE);
        return bundle;
    }

    /**
     * Generate a list of HAL OuiKeyedData objects, each containing several test fields.
     */
    public static OuiKeyedData[] createHalOuiKeyedDataList(int size) {
        OuiKeyedData[] ouiKeyedDataList = new OuiKeyedData[size];
        for (int i = 0; i < size; i++) {
            OuiKeyedData ouiKeyedData = new OuiKeyedData();
            ouiKeyedData.oui = i + 1;
            ouiKeyedData.vendorData = createTestPersistableBundle();
            ouiKeyedDataList[i] = ouiKeyedData;
        }
        return ouiKeyedDataList;
    }

    /**
     * Generate a list of framework OuiKeyedData objects, each containing several test fields.
     */
    public static List<android.net.wifi.OuiKeyedData> createFrameworkOuiKeyedDataList(int size) {
        List<android.net.wifi.OuiKeyedData> ouiKeyedDataList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            android.net.wifi.OuiKeyedData ouiKeyedData = new android.net.wifi.OuiKeyedData.Builder(
                    i + 1, createTestPersistableBundle()).build();
            ouiKeyedDataList.add(ouiKeyedData);
        }
        return ouiKeyedDataList;
    }

    /**
     * Check whether a HAL and framework OuiKeyedData object are equivalent.
     */
    public static boolean ouiKeyedDataEquals(
            OuiKeyedData halData, android.net.wifi.OuiKeyedData frameworkData) {
        return halData.oui == frameworkData.getOui()
                && PersistableBundleUtils.isEqual(halData.vendorData, frameworkData.getData());
    }

    /**
     * Check whether a list of HAL and framework OuiKeyedData objects are equivalent.
     */
    public static boolean ouiKeyedDataListEquals(
            OuiKeyedData[] halDataList, List<android.net.wifi.OuiKeyedData> frameworkDataList) {
        if (halDataList.length != frameworkDataList.size()) {
            return false;
        }

        for (int i = 0; i < halDataList.length; i++) {
            if (!ouiKeyedDataEquals(halDataList[i], frameworkDataList.get(i))) {
                return false;
            }
        }
        return true;
    }
}
