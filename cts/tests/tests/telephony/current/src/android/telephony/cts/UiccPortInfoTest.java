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

package android.telephony.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.UiccPortInfo;
import android.test.AndroidTestCase;

public class UiccPortInfoTest extends AndroidTestCase {
    /**
     * Create fake UiccPortInfo objects run basic tests.
     */
    public void testFakeUiccSlotInfoObject() {
        String iccId = "FAKE_ICC_ID";
        int portIndex = 0;
        int logicalSlotIndex = 0;
        boolean isActive = true;
        UiccPortInfo uiccPortInfo = new UiccPortInfo(
                iccId,            /* ICCID */
                portIndex,      /* portIndex */
                logicalSlotIndex, /* logicalSlotIndex */
                isActive     /* isActive */
        );

        //Getters.
        assertThat(uiccPortInfo.getIccId()).isEqualTo(iccId);
        assertThat(uiccPortInfo.getPortIndex()).isEqualTo(portIndex);
        assertThat(uiccPortInfo.getLogicalSlotIndex()).isEqualTo(logicalSlotIndex);
        assertThat(uiccPortInfo.isActive()).isEqualTo(isActive);

        // Other common methods.
        assertThat(uiccPortInfo.describeContents()).isEqualTo(0);
        assertThat(uiccPortInfo.hashCode()).isNotEqualTo(0);
        assertThat(uiccPortInfo.toString()).isNotEmpty();

        // Parcel read and write.
        Parcel parcel = Parcel.obtain();
        uiccPortInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        UiccPortInfo toCompare = uiccPortInfo.CREATOR.createFromParcel(parcel);
        assertThat(uiccPortInfo.hashCode()).isEqualTo(toCompare.hashCode());
        assertThat(uiccPortInfo).isEqualTo(toCompare);
    }
}
