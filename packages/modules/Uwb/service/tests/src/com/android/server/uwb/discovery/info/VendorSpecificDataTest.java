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

package com.android.server.uwb.discovery.info;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

/**
 * Unit test for {@link VendorSpecificData}
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class VendorSpecificDataTest {

    private static final int ID = 117;
    private static final byte[] DATA = new byte[] {0x10, 0x0A, (byte) 0x93, (byte) 0xFF};
    private static final byte[] BYTES =
            new byte[] {0x75, 0x00, 0x10, 0x0A, (byte) 0x93, (byte) 0xFF};

    @Test
    public void fromBytes_emptyData() {
        assertThat(VendorSpecificData.fromBytes(new byte[] {}, Optional.empty())).isNull();
    }

    @Test
    public void fromBytes_dataTooShort() {
        assertThat(VendorSpecificData.fromBytes(new byte[] {0x01}, Optional.empty())).isNull();
    }

    @Test
    public void fromBytes_succeed() {
        VendorSpecificData info = VendorSpecificData.fromBytes(BYTES, Optional.empty());
        assertThat(info).isNotNull();

        assertThat(info.vendorId).isEqualTo(ID);
        assertThat(info.vendorData).isEqualTo(DATA);
    }

    @Test
    public void fromBytes_succeedProvidedId() {
        VendorSpecificData info = VendorSpecificData.fromBytes(DATA, Optional.of(ID));
        assertThat(info).isNotNull();

        assertThat(info.vendorId).isEqualTo(ID);
        assertThat(info.vendorData).isEqualTo(DATA);
    }

    @Test
    public void toBytes_succeed() {
        VendorSpecificData info = new VendorSpecificData(ID, DATA);
        assertThat(info).isNotNull();

        byte[] result = VendorSpecificData.toBytes(info);
        assertThat(result).isEqualTo(BYTES);
    }

    @Test
    public void toBytes_emptyData() {
        VendorSpecificData info = new VendorSpecificData(ID, new byte[] {});
        assertThat(info).isNotNull();

        byte[] result = VendorSpecificData.toBytes(info);
        assertThat(result).isEqualTo(new byte[] {0x75, 0x00});
    }
}
