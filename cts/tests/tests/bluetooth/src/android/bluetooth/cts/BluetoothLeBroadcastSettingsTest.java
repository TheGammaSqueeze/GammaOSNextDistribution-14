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
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastSettings;
import android.bluetooth.BluetoothLeBroadcastSubgroupSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastSettingsTest {
    // For BluetoothLeAudioContentMetadata
    private static final String TEST_PROGRAM_INFO = "Test";
    // German language code in ISO 639-3
    private static final String TEST_LANGUAGE = "deu";

    private static final String TEST_MAC_ADDRESS = "00:11:22:33:44:55";
    private static final String TEST_BROADCAST_NAME = "TEST";
    private static final String TEST_INVALID_SHORT_BROADCAST_NAME = "TE";
    private static final String TEST_INVALID_LONG_BROADCAST_NAME =
                "TESTWITHLENGTH34TESTWITHLENGTH3434";
    private static final int TEST_QUALITY =
            BluetoothLeBroadcastSubgroupSettings.QUALITY_STANDARD;

    private Context mContext;
    private BluetoothAdapter mAdapter;
    private boolean mIsBroadcastSourceSupported;
    private boolean mIsBroadcastAssistantSupported;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
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

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void createBroadcastSettingsFromBuilder() {
        BluetoothLeAudioContentMetadata publicBroadcastMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).build();
        BluetoothLeBroadcastSettings.Builder builder = new BluetoothLeBroadcastSettings.Builder()
                        .setPublicBroadcast(false)
                        .setPublicBroadcastMetadata(publicBroadcastMetadata);
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);

        // test invalid code length 0
        assertThrows(IllegalArgumentException.class, () ->
                        builder.setBroadcastCode(new byte[0]));

        // test invalid code length 18
        assertThrows(IllegalArgumentException.class, () ->
                        builder.setBroadcastCode(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05,
                                                             0x06, 0x07, 0x08, 0x09, 0x0A,
                                                             0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                                                             0x10, 0x11, 0x12}));

        // test invalid name length 2
        assertThrows(IllegalArgumentException.class, () ->
                        builder.setBroadcastName(TEST_INVALID_SHORT_BROADCAST_NAME));

        // test invalid name length 34
        assertThrows(IllegalArgumentException.class, () ->
                        builder.setBroadcastName(TEST_INVALID_LONG_BROADCAST_NAME));

        builder.setBroadcastCode(null);
        builder.setBroadcastName(null);

        BluetoothLeBroadcastSubgroupSettings[] subgroupSettings =
                new BluetoothLeBroadcastSubgroupSettings[] {
                    createBroadcastSubgroupSettings()
                };
        for (BluetoothLeBroadcastSubgroupSettings setting : subgroupSettings) {
            builder.addSubgroupSettings(setting);
        }
        BluetoothLeBroadcastSettings broadcastSettings = builder.build();
        assertFalse(broadcastSettings.isPublicBroadcast());
        assertNull(broadcastSettings.getBroadcastName());
        assertNull(broadcastSettings.getBroadcastCode());
        assertEquals(publicBroadcastMetadata, broadcastSettings.getPublicBroadcastMetadata());
        assertArrayEquals(subgroupSettings,
                broadcastSettings.getSubgroupSettings()
                .toArray(new BluetoothLeBroadcastSubgroupSettings[0]));
        builder.clearSubgroupSettings();
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void createBroadcastSettingsFromCopy() {
        BluetoothLeAudioContentMetadata publicBroadcastMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).build();
        BluetoothLeBroadcastSettings.Builder builder =
                        new BluetoothLeBroadcastSettings.Builder()
                        .setPublicBroadcast(false)
                        .setBroadcastName(TEST_BROADCAST_NAME)
                        .setBroadcastCode(null)
                        .setPublicBroadcastMetadata(publicBroadcastMetadata);
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
        BluetoothLeBroadcastSubgroupSettings[] subgroupSettings =
                new BluetoothLeBroadcastSubgroupSettings[] {
                    createBroadcastSubgroupSettings()
                };
        for (BluetoothLeBroadcastSubgroupSettings setting : subgroupSettings) {
            builder.addSubgroupSettings(setting);
        }
        BluetoothLeBroadcastSettings broadcastSettings = builder.build();
        BluetoothLeBroadcastSettings broadcastSettingsCopy =
                new BluetoothLeBroadcastSettings.Builder(broadcastSettings).build();
        assertFalse(broadcastSettingsCopy.isPublicBroadcast());
        assertEquals(TEST_BROADCAST_NAME, broadcastSettingsCopy.getBroadcastName());
        assertNull(broadcastSettingsCopy.getBroadcastCode());
        assertEquals(publicBroadcastMetadata, broadcastSettingsCopy.getPublicBroadcastMetadata());
        assertArrayEquals(subgroupSettings,
                broadcastSettingsCopy.getSubgroupSettings()
                .toArray(new BluetoothLeBroadcastSubgroupSettings[0]));
        builder.clearSubgroupSettings();
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    static BluetoothLeBroadcastSubgroupSettings createBroadcastSubgroupSettings() {
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).setLanguage(TEST_LANGUAGE).build();
        BluetoothLeBroadcastSubgroupSettings.Builder builder =
                new BluetoothLeBroadcastSubgroupSettings.Builder()
                .setPreferredQuality(TEST_QUALITY)
                .setContentMetadata(contentMetadata);
        return builder.build();
    }

}
