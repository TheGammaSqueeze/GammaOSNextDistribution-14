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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastMetadataTest {
    private static final String TEST_MAC_ADDRESS = "00:11:22:33:44:55";
    private static final int TEST_BROADCAST_ID = 42;
    private static final int TEST_ADVERTISER_SID = 1234;
    private static final int TEST_PA_SYNC_INTERVAL = 100;
    private static final int TEST_PRESENTATION_DELAY_MS = 345;
    private static final int TEST_AUDIO_QUALITY_STANDARD = 0x1 << 0;
    private static final int TEST_RSSI_DBM = -40;
    private static final String TEST_BROADCAST_NAME = "TEST";

    private static final int TEST_CODEC_ID = 42;
    private static final BluetoothLeBroadcastChannel[] TEST_CHANNELS = {
        new BluetoothLeBroadcastChannel.Builder().setChannelIndex(42).setSelected(true)
                .setCodecMetadata(new BluetoothLeAudioCodecConfigMetadata.Builder().build())
                .build()
    };

    // For BluetoothLeAudioCodecConfigMetadata
    private static final long TEST_AUDIO_LOCATION_FRONT_LEFT = 0x01;
    private static final int TEST_SAMPLE_RATE_44100 = 0x01 << 6;
    private static final int TEST_FRAME_DURATION_10000 = 0x01 << 1;
    private static final int TEST_OCTETS_PER_FRAME = 100;

    // For BluetoothLeAudioContentMetadata
    private static final String TEST_PROGRAM_INFO = "Test";
    // German language code in ISO 639-3
    private static final String TEST_LANGUAGE = "deu";

    private Context mContext;
    private BluetoothAdapter mAdapter;
    private boolean mIsBroadcastSourceSupported;
    private boolean mIsBroadcastAssistantSupported;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mIsBroadcastAssistantSupported =
                mAdapter.isLeAudioBroadcastAssistantSupported() == FEATURE_SUPPORTED;
        if (mIsBroadcastAssistantSupported) {
            boolean isBroadcastAssistantEnabledInConfig =
                    TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
            assertTrue("Config must be true when profile is supported",
                    isBroadcastAssistantEnabledInConfig);
        }

        mIsBroadcastSourceSupported =
                mAdapter.isLeAudioBroadcastSourceSupported() == FEATURE_SUPPORTED;
        if (mIsBroadcastSourceSupported) {
            boolean isBroadcastSourceEnabledInConfig =
                    TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST);
            assertTrue("Config must be true when profile is supported",
                    isBroadcastSourceEnabledInConfig);
        }

        Assume.assumeTrue(mIsBroadcastAssistantSupported || mIsBroadcastSourceSupported);
    }

    @After
    public void tearDown() {
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2", "7.4.3/C-9-1"})
    @Test
    public void createMetadataFromBuilder() {
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);
        BluetoothLeAudioContentMetadata publicBroadcastMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).build();
        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder()
                        .setEncrypted(false)
                        .setPublicBroadcast(false)
                        .setBroadcastName(TEST_BROADCAST_NAME)
                        .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                        .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                        .setBroadcastId(TEST_BROADCAST_ID)
                        .setBroadcastCode(null)
                        .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                        .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS)
                        .setAudioConfigQuality(TEST_AUDIO_QUALITY_STANDARD)
                        .setPublicBroadcastMetadata(publicBroadcastMetadata);
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
        BluetoothLeBroadcastSubgroup[] subgroups = new BluetoothLeBroadcastSubgroup[] {
                createBroadcastSubgroup()
        };
        for (BluetoothLeBroadcastSubgroup subgroup : subgroups) {
            builder.addSubgroup(subgroup);
        }
        BluetoothLeBroadcastMetadata metadata = builder.build();
        assertFalse(metadata.isEncrypted());
        assertFalse(metadata.isPublicBroadcast());
        assertEquals(TEST_BROADCAST_NAME, metadata.getBroadcastName());
        assertEquals(testDevice, metadata.getSourceDevice());
        assertEquals(BluetoothDevice.ADDRESS_TYPE_RANDOM, metadata.getSourceAddressType());
        assertEquals(TEST_ADVERTISER_SID, metadata.getSourceAdvertisingSid());
        assertEquals(TEST_BROADCAST_ID, metadata.getBroadcastId());
        assertNull(metadata.getBroadcastCode());
        assertEquals(TEST_PA_SYNC_INTERVAL, metadata.getPaSyncInterval());
        assertEquals(TEST_PRESENTATION_DELAY_MS, metadata.getPresentationDelayMicros());
        assertEquals(TEST_AUDIO_QUALITY_STANDARD, metadata.getAudioConfigQuality());
        assertEquals(publicBroadcastMetadata, metadata.getPublicBroadcastMetadata());
        assertArrayEquals(subgroups,
                metadata.getSubgroups().toArray(new BluetoothLeBroadcastSubgroup[0]));
        builder.clearSubgroup();
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2", "7.4.3/C-9-1"})
    @Test
    public void createMetadataFromCopy() {
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);
        BluetoothLeAudioContentMetadata publicBroadcastMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).build();
        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder()
                .setEncrypted(false)
                .setPublicBroadcast(false)
                .setBroadcastName(TEST_BROADCAST_NAME)
                .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                .setBroadcastId(TEST_BROADCAST_ID)
                .setBroadcastCode(null)
                .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS)
                .setAudioConfigQuality(TEST_AUDIO_QUALITY_STANDARD)
                .setPublicBroadcastMetadata(publicBroadcastMetadata);

        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
        BluetoothLeBroadcastSubgroup[] subgroups = new BluetoothLeBroadcastSubgroup[] {
                createBroadcastSubgroup()
        };
        for (BluetoothLeBroadcastSubgroup subgroup : subgroups) {
            builder.addSubgroup(subgroup);
        }
        BluetoothLeBroadcastMetadata metadata = builder.build();
        BluetoothLeBroadcastMetadata metadataCopy =
                new BluetoothLeBroadcastMetadata.Builder(metadata).build();
        assertFalse(metadataCopy.isEncrypted());
        assertFalse(metadataCopy.isPublicBroadcast());
        assertEquals(TEST_BROADCAST_NAME, metadataCopy.getBroadcastName());
        assertEquals(testDevice, metadataCopy.getSourceDevice());
        assertEquals(BluetoothDevice.ADDRESS_TYPE_RANDOM, metadataCopy.getSourceAddressType());
        assertEquals(TEST_ADVERTISER_SID, metadataCopy.getSourceAdvertisingSid());
        assertEquals(TEST_BROADCAST_ID, metadataCopy.getBroadcastId());
        assertNull(metadataCopy.getBroadcastCode());
        assertEquals(TEST_PA_SYNC_INTERVAL, metadataCopy.getPaSyncInterval());
        assertEquals(TEST_PRESENTATION_DELAY_MS, metadataCopy.getPresentationDelayMicros());
        assertEquals(TEST_AUDIO_QUALITY_STANDARD, metadataCopy.getAudioConfigQuality());
        assertEquals(publicBroadcastMetadata, metadataCopy.getPublicBroadcastMetadata());
        assertArrayEquals(subgroups,
                metadataCopy.getSubgroups().toArray(new BluetoothLeBroadcastSubgroup[0]));
        builder.clearSubgroup();
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_BROADCAST_MONITOR_SOURCE_SYNC_STATUS)
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2", "7.4.3/C-9-1"})
    @Test
    public void createMetadataFromBuilderAndCheckRssi() {
        final int testRssiInvalidMin = -128;
        final int testRssiInvalidMax = 128;
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);
        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder()
                        .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM);
        BluetoothLeBroadcastSubgroup[] subgroups = new BluetoothLeBroadcastSubgroup[] {
                createBroadcastSubgroup()
        };
        for (BluetoothLeBroadcastSubgroup subgroup : subgroups) {
            builder.addSubgroup(subgroup);
        }
        // validate RSSI is unknown if not set
        BluetoothLeBroadcastMetadata metadata = builder.build();
        assertEquals(BluetoothLeBroadcastMetadata.RSSI_UNKNOWN, metadata.getRssi());

        // builder expect rssi is in range [-127, 127]
        assertThrows(IllegalArgumentException.class, () ->
                        builder.setRssi(testRssiInvalidMin));
        assertThrows(IllegalArgumentException.class, () ->
                        builder.setRssi(testRssiInvalidMax));

        builder.setRssi(TEST_RSSI_DBM);
        metadata = builder.build();
        assertEquals(TEST_RSSI_DBM, metadata.getRssi());
    }

    static BluetoothLeBroadcastSubgroup createBroadcastSubgroup() {
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_LEFT)
                        .setSampleRate(TEST_SAMPLE_RATE_44100)
                        .setFrameDuration(TEST_FRAME_DURATION_10000)
                        .setOctetsPerFrame(TEST_OCTETS_PER_FRAME)
                        .build();
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).setLanguage(TEST_LANGUAGE).build();
        BluetoothLeBroadcastSubgroup.Builder builder = new BluetoothLeBroadcastSubgroup.Builder()
                .setCodecId(TEST_CODEC_ID)
                .setCodecSpecificConfig(codecMetadata)
                .setContentMetadata(contentMetadata);
        for (BluetoothLeBroadcastChannel channel : TEST_CHANNELS) {
            builder.addChannel(channel);
        }
        return builder.build();
    }

}
