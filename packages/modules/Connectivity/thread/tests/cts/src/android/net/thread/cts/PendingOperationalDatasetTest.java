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

package android.net.thread.cts;

import static com.android.testutils.ParcelUtils.assertParcelingIsLossless;

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.IpPrefix;
import android.net.thread.ActiveOperationalDataset;
import android.net.thread.ActiveOperationalDataset.SecurityPolicy;
import android.net.thread.OperationalDatasetTimestamp;
import android.net.thread.PendingOperationalDataset;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.common.primitives.Bytes;
import com.google.common.testing.EqualsTester;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.time.Duration;

/** Tests for {@link PendingOperationalDataset}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class PendingOperationalDatasetTest {
    private static ActiveOperationalDataset createActiveDataset() throws Exception {
        SparseArray<byte[]> channelMask = new SparseArray<>(1);
        channelMask.put(0, new byte[] {0x00, 0x1f, (byte) 0xff, (byte) 0xe0});

        return new ActiveOperationalDataset.Builder()
                .setActiveTimestamp(new OperationalDatasetTimestamp(100, 10, false))
                .setExtendedPanId(new byte[] {0, 1, 2, 3, 4, 5, 6, 7})
                .setPanId(12345)
                .setNetworkName("defaultNet")
                .setChannel(0, 18)
                .setChannelMask(channelMask)
                .setPskc(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15})
                .setNetworkKey(new byte[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                .setMeshLocalPrefix(new IpPrefix(InetAddress.getByName("fd00::1"), 64))
                .setSecurityPolicy(new SecurityPolicy(672, new byte[] {(byte) 0xff, (byte) 0xf8}))
                .build();
    }

    @Test
    public void parcelable_parcelingIsLossLess() throws Exception {
        PendingOperationalDataset dataset =
                new PendingOperationalDataset(
                        createActiveDataset(),
                        new OperationalDatasetTimestamp(31536000, 200, false),
                        Duration.ofHours(100));

        assertParcelingIsLossless(dataset);
    }

    @Test
    public void equalityTests() throws Exception {
        ActiveOperationalDataset activeDataset1 =
                new ActiveOperationalDataset.Builder(createActiveDataset())
                        .setNetworkName("net1")
                        .build();
        ActiveOperationalDataset activeDataset2 =
                new ActiveOperationalDataset.Builder(createActiveDataset())
                        .setNetworkName("net2")
                        .build();

        new EqualsTester()
                .addEqualityGroup(
                        new PendingOperationalDataset(
                                activeDataset1,
                                new OperationalDatasetTimestamp(31536000, 100, false),
                                Duration.ofMillis(0)),
                        new PendingOperationalDataset(
                                activeDataset1,
                                new OperationalDatasetTimestamp(31536000, 100, false),
                                Duration.ofMillis(0)))
                .addEqualityGroup(
                        new PendingOperationalDataset(
                                activeDataset2,
                                new OperationalDatasetTimestamp(31536000, 100, false),
                                Duration.ofMillis(0)),
                        new PendingOperationalDataset(
                                activeDataset2,
                                new OperationalDatasetTimestamp(31536000, 100, false),
                                Duration.ofMillis(0)))
                .addEqualityGroup(
                        new PendingOperationalDataset(
                                activeDataset2,
                                new OperationalDatasetTimestamp(15768000, 0, false),
                                Duration.ofMillis(0)),
                        new PendingOperationalDataset(
                                activeDataset2,
                                new OperationalDatasetTimestamp(15768000, 0, false),
                                Duration.ofMillis(0)))
                .addEqualityGroup(
                        new PendingOperationalDataset(
                                activeDataset2,
                                new OperationalDatasetTimestamp(15768000, 0, false),
                                Duration.ofMillis(100)),
                        new PendingOperationalDataset(
                                activeDataset2,
                                new OperationalDatasetTimestamp(15768000, 0, false),
                                Duration.ofMillis(100)))
                .testEquals();
    }

    @Test
    public void constructor_correctValuesAreSet() throws Exception {
        final ActiveOperationalDataset activeDataset = createActiveDataset();
        PendingOperationalDataset dataset =
                new PendingOperationalDataset(
                        activeDataset,
                        new OperationalDatasetTimestamp(31536000, 200, false),
                        Duration.ofHours(100));

        assertThat(dataset.getActiveOperationalDataset()).isEqualTo(activeDataset);
        assertThat(dataset.getPendingTimestamp())
                .isEqualTo(new OperationalDatasetTimestamp(31536000, 200, false));
        assertThat(dataset.getDelayTimer()).isEqualTo(Duration.ofHours(100));
    }

    @Test
    public void fromThreadTlvs_openthreadTlvs_success() {
        // An example Pending Operational Dataset which is generated with OpenThread CLI:
        // Pending Timestamp: 2
        // Active Timestamp: 1
        // Channel: 26
        // Channel Mask: 0x07fff800
        // Delay: 46354
        // Ext PAN ID: a74182f4d3f4de41
        // Mesh Local Prefix: fd46:c1b9:e159:5574::/64
        // Network Key: ed916e454d96fd00184f10a6f5c9e1d3
        // Network Name: OpenThread-bff8
        // PAN ID: 0xbff8
        // PSKc: 264f78414adc683191863d968f72d1b7
        // Security Policy: 672 onrc
        final byte[] OPENTHREAD_PENDING_DATASET_TLVS =
                base16().lowerCase()
                        .decode(
                                "0e0800000000000100003308000000000002000034040000b51200030000"
                                        + "1a35060004001fffe00208a74182f4d3f4de410708fd46c1b9"
                                        + "e15955740510ed916e454d96fd00184f10a6f5c9e1d3030f4f"
                                        + "70656e5468726561642d626666380102bff80410264f78414a"
                                        + "dc683191863d968f72d1b70c0402a0f7f8");

        PendingOperationalDataset pendingDataset =
                PendingOperationalDataset.fromThreadTlvs(OPENTHREAD_PENDING_DATASET_TLVS);

        ActiveOperationalDataset activeDataset = pendingDataset.getActiveOperationalDataset();
        assertThat(pendingDataset.getPendingTimestamp().getSeconds()).isEqualTo(2L);
        assertThat(activeDataset.getActiveTimestamp().getSeconds()).isEqualTo(1L);
        assertThat(activeDataset.getChannel()).isEqualTo(26);
        assertThat(activeDataset.getChannelMask().get(0))
                .isEqualTo(new byte[] {0x00, 0x1f, (byte) 0xff, (byte) 0xe0});
        assertThat(pendingDataset.getDelayTimer().toMillis()).isEqualTo(46354);
        assertThat(activeDataset.getExtendedPanId())
                .isEqualTo(base16().lowerCase().decode("a74182f4d3f4de41"));
        assertThat(activeDataset.getMeshLocalPrefix())
                .isEqualTo(new IpPrefix("fd46:c1b9:e159:5574::/64"));
        assertThat(activeDataset.getNetworkKey())
                .isEqualTo(base16().lowerCase().decode("ed916e454d96fd00184f10a6f5c9e1d3"));
        assertThat(activeDataset.getNetworkName()).isEqualTo("OpenThread-bff8");
        assertThat(activeDataset.getPanId()).isEqualTo(0xbff8);
        assertThat(activeDataset.getPskc())
                .isEqualTo(base16().lowerCase().decode("264f78414adc683191863d968f72d1b7"));
        assertThat(activeDataset.getSecurityPolicy().getRotationTimeHours()).isEqualTo(672);
        assertThat(activeDataset.getSecurityPolicy().getFlags())
                .isEqualTo(new byte[] {(byte) 0xf7, (byte) 0xf8});
    }

    @Test
    public void fromThreadTlvs_completePendingDatasetTlvs_success() throws Exception {
        final ActiveOperationalDataset activeDataset = createActiveDataset();

        // Type Length Value
        // 0x33 0x08 0x0000000000010000 (Pending Timestamp TLV)
        // 0x34 0x04 0x0000012C (Delay Timer TLV)
        final byte[] pendingTimestampAndDelayTimerTlvs =
                base16().decode("3308000000000001000034040000012C");
        final byte[] pendingDatasetTlvs =
                Bytes.concat(pendingTimestampAndDelayTimerTlvs, activeDataset.toThreadTlvs());

        PendingOperationalDataset dataset =
                PendingOperationalDataset.fromThreadTlvs(pendingDatasetTlvs);

        assertThat(dataset.getActiveOperationalDataset()).isEqualTo(activeDataset);
        assertThat(dataset.getPendingTimestamp())
                .isEqualTo(new OperationalDatasetTimestamp(1, 0, false));
        assertThat(dataset.getDelayTimer()).isEqualTo(Duration.ofMillis(300));
    }

    @Test
    public void fromThreadTlvs_PendingTimestampTlvIsMissing_throwsIllegalArgument()
            throws Exception {
        // Type Length Value
        // 0x34 0x04 0x00000064 (Delay Timer TLV)
        final byte[] pendingTimestampAndDelayTimerTlvs = base16().decode("34040000012C");
        final byte[] pendingDatasetTlvs =
                Bytes.concat(
                        pendingTimestampAndDelayTimerTlvs, createActiveDataset().toThreadTlvs());

        assertThrows(
                IllegalArgumentException.class,
                () -> PendingOperationalDataset.fromThreadTlvs(pendingDatasetTlvs));
    }

    @Test
    public void fromThreadTlvs_delayTimerTlvIsMissing_throwsIllegalArgument() throws Exception {
        // Type Length Value
        // 0x33 0x08 0x0000000000010000 (Pending Timestamp TLV)
        final byte[] pendingTimestampAndDelayTimerTlvs = base16().decode("33080000000000010000");
        final byte[] pendingDatasetTlvs =
                Bytes.concat(
                        pendingTimestampAndDelayTimerTlvs, createActiveDataset().toThreadTlvs());

        assertThrows(
                IllegalArgumentException.class,
                () -> PendingOperationalDataset.fromThreadTlvs(pendingDatasetTlvs));
    }

    @Test
    public void fromThreadTlvs_activeDatasetTlvs_throwsIllegalArgument() throws Exception {
        final byte[] activeDatasetTlvs = createActiveDataset().toThreadTlvs();

        assertThrows(
                IllegalArgumentException.class,
                () -> PendingOperationalDataset.fromThreadTlvs(activeDatasetTlvs));
    }

    @Test
    public void fromThreadTlvs_malformedTlvs_throwsIllegalArgument() {
        final byte[] invalidTlvs = new byte[] {0x00};

        assertThrows(
                IllegalArgumentException.class,
                () -> PendingOperationalDataset.fromThreadTlvs(invalidTlvs));
    }

    @Test
    public void toThreadTlvs_conversionIsLossLess() throws Exception {
        PendingOperationalDataset dataset1 =
                new PendingOperationalDataset(
                        createActiveDataset(),
                        new OperationalDatasetTimestamp(31536000, 200, false),
                        Duration.ofHours(100));

        PendingOperationalDataset dataset2 =
                PendingOperationalDataset.fromThreadTlvs(dataset1.toThreadTlvs());

        assertThat(dataset2).isEqualTo(dataset1);
    }
}
