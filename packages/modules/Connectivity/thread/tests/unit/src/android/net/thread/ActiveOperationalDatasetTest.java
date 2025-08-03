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

package android.net.thread;

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.thread.ActiveOperationalDataset.Builder;
import android.net.thread.ActiveOperationalDataset.SecurityPolicy;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.common.primitives.Bytes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link ActiveOperationalDataset}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ActiveOperationalDatasetTest {
    // A valid Thread Active Operational Dataset generated from OpenThread CLI "dataset new":
    // Active Timestamp: 1
    // Channel: 19
    // Channel Mask: 0x07FFF800
    // Ext PAN ID: ACC214689BC40BDF
    // Mesh Local Prefix: fd64:db12:25f4:7e0b::/64
    // Network Key: F26B3153760F519A63BAFDDFFC80D2AF
    // Network Name: OpenThread-d9a0
    // PAN ID: 0xD9A0
    // PSKc: A245479C836D551B9CA557F7B9D351B4
    // Security Policy: 672 onrcb
    private static final byte[] VALID_DATASET_TLVS =
            base16().decode(
                            "0E080000000000010000000300001335060004001FFFE002"
                                    + "08ACC214689BC40BDF0708FD64DB1225F47E0B0510F26B31"
                                    + "53760F519A63BAFDDFFC80D2AF030F4F70656E5468726561"
                                    + "642D643961300102D9A00410A245479C836D551B9CA557F7"
                                    + "B9D351B40C0402A0FFF8");

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    private static byte[] addTlv(byte[] dataset, String tlvHex) {
        return Bytes.concat(dataset, base16().decode(tlvHex));
    }

    @Test
    public void fromThreadTlvs_containsUnknownTlvs_unknownTlvsRetained() {
        byte[] datasetWithUnknownTlvs = addTlv(VALID_DATASET_TLVS, "AA01FFBB020102");

        ActiveOperationalDataset dataset1 =
                ActiveOperationalDataset.fromThreadTlvs(datasetWithUnknownTlvs);
        ActiveOperationalDataset dataset2 =
                ActiveOperationalDataset.fromThreadTlvs(dataset1.toThreadTlvs());

        SparseArray<byte[]> unknownTlvs = dataset2.getUnknownTlvs();
        assertThat(unknownTlvs.size()).isEqualTo(2);
        assertThat(unknownTlvs.get(0xAA)).isEqualTo(new byte[] {(byte) 0xFF});
        assertThat(unknownTlvs.get(0xBB)).isEqualTo(new byte[] {0x01, 0x02});
        assertThat(dataset2).isEqualTo(dataset1);
    }

    @Test
    public void builder_buildWithTooLongTlvs_throwsIllegalState() {
        Builder builder = new Builder(ActiveOperationalDataset.fromThreadTlvs(VALID_DATASET_TLVS));
        for (int i = 0; i < 10; i++) {
            builder.addUnknownTlv(i, new byte[20]);
        }

        assertThrows(IllegalStateException.class, () -> new Builder().build());
    }

    @Test
    public void builder_setUnknownTlvs_success() {
        ActiveOperationalDataset dataset1 =
                ActiveOperationalDataset.fromThreadTlvs(VALID_DATASET_TLVS);
        SparseArray<byte[]> unknownTlvs = new SparseArray<>(2);
        unknownTlvs.put(0x33, new byte[] {1, 2, 3});
        unknownTlvs.put(0x44, new byte[] {1, 2, 3, 4});

        ActiveOperationalDataset dataset2 =
                new ActiveOperationalDataset.Builder(dataset1).setUnknownTlvs(unknownTlvs).build();

        assertThat(dataset1.getUnknownTlvs().size()).isEqualTo(0);
        assertThat(dataset2.getUnknownTlvs().size()).isEqualTo(2);
        assertThat(dataset2.getUnknownTlvs().get(0x33)).isEqualTo(new byte[] {1, 2, 3});
        assertThat(dataset2.getUnknownTlvs().get(0x44)).isEqualTo(new byte[] {1, 2, 3, 4});
    }

    @Test
    public void securityPolicy_fromTooShortTlvValue_throwsIllegalArgument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SecurityPolicy.fromTlvValue(new byte[] {0x01}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SecurityPolicy.fromTlvValue(new byte[] {0x01, 0x02}));
    }

    @Test
    public void securityPolicy_toTlvValue_conversionIsLossLess() {
        SecurityPolicy policy1 = new SecurityPolicy(200, new byte[] {(byte) 0xFF, (byte) 0xF8});

        SecurityPolicy policy2 = SecurityPolicy.fromTlvValue(policy1.toTlvValue());

        assertThat(policy2).isEqualTo(policy1);
    }
}
