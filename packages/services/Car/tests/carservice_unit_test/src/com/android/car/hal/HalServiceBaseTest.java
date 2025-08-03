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

package com.android.car.hal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public final class HalServiceBaseTest {

    private static final int ONE_SUPPORTED_PROPERTY = 1;
    private static final int EXISTENT_MANAGER_PROP_ID = 1;
    private static final int EXISTENT_HAL_PROP_ID = 2;

    private final HalServiceBaseImpl mHalServiceBase = new HalServiceBaseImpl();

    @Test
    public void testGetDispatchList() {
        int[] propList = {1, 2};
        int[] areaIdList = {1, 2};
        List<HalPropValue> expectedList = new ArrayList<HalPropValue>();
        expectedList.add(mHalServiceBase.addAidlHalPropValue(propList[0], areaIdList[0]));
        expectedList.add(mHalServiceBase.addHidlHalPropValue(propList[1], areaIdList[1]));

        assertThat(mHalServiceBase.getDispatchList()).containsExactlyElementsIn(expectedList);
    }

    @Test
    public void testIsSupportedProperty_withReturnTrue() {
        boolean isSupportedProperty = mHalServiceBase.isSupportedProperty(ONE_SUPPORTED_PROPERTY);

        assertThat(isSupportedProperty).isTrue();
    }

    @Test
    public void testIsSupportedProperty_withReturnFalse() {
        boolean isSupportedProperty = mHalServiceBase
                .isSupportedProperty(HalServiceBase.NOT_SUPPORTED_PROPERTY);

        assertThat(isSupportedProperty).isFalse();
    }

    @Test
    public void testManagerToHalPropIdMap_create_succeedWithEvenNumberOfParams() {
        assertThat(HalServiceBase.ManagerToHalPropIdMap.create(1, 2)).isNotNull();
    }

    @Test
    public void testManagerToHalPropIdMap_create_failedWithOddNumberOfParams() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> HalServiceBase.ManagerToHalPropIdMap.create(1, 2, 3));

        assertThat(thrown).hasMessageThat().contains("Odd number of key-value elements");
    }

    @Test
    public void testManagerToHalPropIdMap_getHalPropId() {
        HalServiceBase.ManagerToHalPropIdMap halPropIdMap = createIdMap();

        assertThat(halPropIdMap.getHalPropId(EXISTENT_MANAGER_PROP_ID))
                .isEqualTo(EXISTENT_HAL_PROP_ID);
    }

    @Test
    public void testManagerToHalPropIdMap_getHalPropId_returnsDefaultHalPropId() {
        HalServiceBase.ManagerToHalPropIdMap halPropIdMap = createIdMap();

        assertWithMessage("hal prop id for nonexistent manager prop id")
                .that(halPropIdMap.getHalPropId(HalServiceBase.NOT_SUPPORTED_PROPERTY))
                .isEqualTo(HalServiceBase.NOT_SUPPORTED_PROPERTY);
    }

    @Test
    public void testManagerToHalPropIdMap_getManagerPropId() {
        HalServiceBase.ManagerToHalPropIdMap halPropIdMap = createIdMap();

        assertThat(halPropIdMap.getManagerPropId(EXISTENT_HAL_PROP_ID))
                .isEqualTo(EXISTENT_MANAGER_PROP_ID);
    }

    @Test
    public void testManagerToHalPropIdMap_getManagerPropId_returnsDefaultManagerPropId() {
        HalServiceBase.ManagerToHalPropIdMap halPropIdMap = createIdMap();

        assertWithMessage("manager prop id for nonexistent hal prop id")
                .that(halPropIdMap.getManagerPropId(HalServiceBase.NOT_SUPPORTED_PROPERTY))
                .isEqualTo(HalServiceBase.NOT_SUPPORTED_PROPERTY);
    }

    private static final class HalServiceBaseImpl extends HalServiceBase {
        private HalPropValueBuilder mAidlHalPropValueBuilder =
                new HalPropValueBuilder(/* isAidl= */ true);
        private HalPropValueBuilder mHidlHalPropValueBuilder =
                new HalPropValueBuilder(/* isAidl= */ false);

        @Override
        public void init() { }

        @Override
        public void release() { }

        @Override
        public int[] getAllSupportedProperties() {
            return new int[] {1, 2, 3};
        }

        @Override
        public void dump(PrintWriter writer) { }

        private HalPropValue addAidlHalPropValue(int prop, int areaId) {
            HalPropValue val = mAidlHalPropValueBuilder.build(prop, areaId);
            getDispatchList().add(val);
            return val;
        }

        private HalPropValue addHidlHalPropValue(int prop, int areaId) {
            HalPropValue val = mHidlHalPropValueBuilder.build(prop, areaId);
            getDispatchList().add(val);
            return val;
        }
    }

    private static HalServiceBase.ManagerToHalPropIdMap createIdMap() {
        return HalServiceBase.ManagerToHalPropIdMap
                .create(EXISTENT_MANAGER_PROP_ID, EXISTENT_HAL_PROP_ID);
    }
}
