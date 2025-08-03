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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastSubgroupTest {
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

    @Test
    public void createBroadcastSubgroupFromBuilder() {
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
        BluetoothLeBroadcastSubgroup subgroup = builder.build();
        assertEquals(TEST_CODEC_ID, subgroup.getCodecId());
        assertEquals(codecMetadata, subgroup.getCodecSpecificConfig());
        assertEquals(contentMetadata, subgroup.getContentMetadata());
        assertTrue(subgroup.hasChannelPreference());
        assertArrayEquals(TEST_CHANNELS,
                subgroup.getChannels().toArray(new BluetoothLeBroadcastChannel[0]));
        builder.clearChannel();
        // builder expect at least one channel
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    public void createBroadcastSubgroupFromCopy() {
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
        BluetoothLeBroadcastSubgroup subgroup = builder.build();
        BluetoothLeBroadcastSubgroup subgroupCopy =
                new BluetoothLeBroadcastSubgroup.Builder(subgroup).build();
        assertEquals(TEST_CODEC_ID, subgroupCopy.getCodecId());
        assertEquals(codecMetadata, subgroupCopy.getCodecSpecificConfig());
        assertEquals(contentMetadata, subgroupCopy.getContentMetadata());
        assertTrue(subgroupCopy.hasChannelPreference());
        assertArrayEquals(TEST_CHANNELS,
                subgroupCopy.getChannels().toArray(new BluetoothLeBroadcastChannel[0]));
        builder.clearChannel();
        // builder expect at least one channel
        assertThrows(IllegalArgumentException.class, builder::build);
    }
}
