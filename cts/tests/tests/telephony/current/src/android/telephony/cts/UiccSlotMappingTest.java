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

package android.telephony.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.UiccSlotMapping;

import org.junit.Test;

public class UiccSlotMappingTest {
    private static final int PORT_INDEX = 0;
    private static final int PHYSICAL_SLOT_INDEX = 0;
    private static final int LOGICAL_SLOT_INDEX = 0;

    @Test
    public void testConstructorAndGetters() {
        UiccSlotMapping uiccSlotMapping = new UiccSlotMapping(
                PORT_INDEX, PHYSICAL_SLOT_INDEX, LOGICAL_SLOT_INDEX);

        assertThat(uiccSlotMapping.getPortIndex()).isEqualTo(PORT_INDEX);
        assertThat(uiccSlotMapping.getPhysicalSlotIndex()).isEqualTo(PHYSICAL_SLOT_INDEX);
        assertThat(uiccSlotMapping.getLogicalSlotIndex()).isEqualTo(LOGICAL_SLOT_INDEX);
    }

    @Test
    public void testEquals() {
        UiccSlotMapping uiccSlotMappingObject = new UiccSlotMapping(
                PORT_INDEX, PHYSICAL_SLOT_INDEX, LOGICAL_SLOT_INDEX);
        UiccSlotMapping uiccSlotMappingEqualObject = new UiccSlotMapping(
                PORT_INDEX, PHYSICAL_SLOT_INDEX, LOGICAL_SLOT_INDEX);

        assertThat(uiccSlotMappingObject).isEqualTo(uiccSlotMappingEqualObject);
    }

    @Test
    public void testNotEqual() {
        UiccSlotMapping uiccSlotMappingObject = new UiccSlotMapping(
                PORT_INDEX, PHYSICAL_SLOT_INDEX, LOGICAL_SLOT_INDEX);
        UiccSlotMapping uiccSlotMappingNotEqualObject = new UiccSlotMapping(
                /* portIndex= */ 0, /* phycalSlotIndex= */1, /* logicalSlotIndex= */ 1);

        assertThat(uiccSlotMappingObject).isNotEqualTo(uiccSlotMappingNotEqualObject);
    }

    @Test
    public void testParcel() {
        UiccSlotMapping uiccSlotMapping = new UiccSlotMapping(
                PORT_INDEX, PHYSICAL_SLOT_INDEX, LOGICAL_SLOT_INDEX);

        Parcel parcel = Parcel.obtain();
        uiccSlotMapping.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        UiccSlotMapping uiccSlotMappingParcel = UiccSlotMapping.CREATOR.createFromParcel(parcel);

        assertThat(uiccSlotMapping).isEqualTo(uiccSlotMappingParcel);
    }
}
