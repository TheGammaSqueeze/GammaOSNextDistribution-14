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

package android.net.ipsec.ike.ike3gpp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.internal.util.HexDump;

import org.junit.Before;
import org.junit.Test;

public class Ike3gppN1ModeInformationTest {
    private static final byte[] SNSSAI = HexDump.hexStringToByteArray("1122334455");

    private Ike3gppN1ModeInformation mN1ModeInformation;

    public static Ike3gppN1ModeInformation newN1ModeInformation() {
        return new Ike3gppN1ModeInformation(SNSSAI);
    }

    @Before
    public void setUp() {
        mN1ModeInformation = newN1ModeInformation();
    }

    @Test
    public void testGetDataType() {
        assertEquals(
                Ike3gppData.DATA_TYPE_NOTIFY_N1_MODE_INFORMATION, mN1ModeInformation.getDataType());
    }

    @Test
    public void testGetSnssai() {
        assertArrayEquals(SNSSAI, mN1ModeInformation.getSnssai());
    }
}
