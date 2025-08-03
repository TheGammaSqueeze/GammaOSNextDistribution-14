/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.telephony.ModemInfo;
import android.telephony.PhoneCapability;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PhoneCapabilityTest {
    @Test
    @SmallTest
    public void parcelReadWrite() throws Exception {
        int maxActiveVoice = 1;
        int maxActiveData = 2;
        ModemInfo modemInfo = new ModemInfo(1, 2, true, false);
        List<ModemInfo> logicalModemList = new ArrayList<>();
        logicalModemList.add(modemInfo);
        int[] deviceNrCapabilities = new int[]{};

        Parcel parcel = Parcel.obtain();
        parcel.writeInt(maxActiveVoice);
        parcel.writeInt(maxActiveData);
        parcel.writeBoolean(false);
        parcel.writeList(logicalModemList);
        parcel.writeIntArray(deviceNrCapabilities);

        parcel.setDataPosition(0);
        PhoneCapability toCompare = PhoneCapability.CREATOR.createFromParcel(parcel);

        assertEquals(maxActiveVoice, toCompare.getMaxActiveVoiceSubscriptions());
        assertEquals(maxActiveData, toCompare.getMaxActiveDataSubscriptions());
        assertArrayEquals(deviceNrCapabilities, toCompare.getDeviceNrCapabilities());
    }
}
