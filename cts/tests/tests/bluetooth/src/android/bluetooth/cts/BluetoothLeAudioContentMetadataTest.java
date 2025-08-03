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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeAudioContentMetadataTest {
    // "Test" in UTF-8 is "54 65 73 74"
    private static final String TEST_PROGRAM_INFO = "Test";
    // German language code in ISO 639-3
    // In byte it is ASCII, 0x64, 0x65, 0x75
    private static final String TEST_LANGUAGE = "deu";
    // Same as TEST_LANGUAGE, but with whitespace before and after
    private static final String TEST_LANGUAGE_WITH_WHITESPACE = "   deu     ";
    // See Page 6 of Generic Audio assigned number specification
    private static final byte[] TEST_METADATA_BYTES = {
            // length is 0x05, type is 0x03, data is "Test" in UTF-8 "54 65 73 74" hex
            0x05, 0x03, 0x54, 0x65, 0x73, 0x74,
            // length is 0x04, type is 0x04, data is "deu" in ASCII "64 65 75" hex
            0x04, 0x04, 0x64, 0x65, 0x75
    };

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
                    TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
            assertTrue("Config must be true when profile is supported",
                    isBroadcastSourceEnabledInConfig);
        }

        Assume.assumeTrue(mIsBroadcastAssistantSupported || mIsBroadcastSourceSupported);
    }

    @After
    public void tearDown() {
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createContentMetadataFromBuilder() {
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).setLanguage(TEST_LANGUAGE).build();
        assertEquals(TEST_PROGRAM_INFO, contentMetadata.getProgramInfo());
        assertEquals(TEST_LANGUAGE, contentMetadata.getLanguage());
        assertArrayEquals(TEST_METADATA_BYTES, contentMetadata.getRawMetadata());

        // Verifies that the language string is stripped when generating the raw metadata
        BluetoothLeAudioContentMetadata contentMetadataStrippedLanguage =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO)
                        .setLanguage(TEST_LANGUAGE_WITH_WHITESPACE.toLowerCase().strip())
                        .build();
        assertArrayEquals(contentMetadata.getRawMetadata(),
                contentMetadataStrippedLanguage.getRawMetadata());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createContentMetadataFromCopy() {
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).setLanguage(TEST_LANGUAGE).build();
        BluetoothLeAudioContentMetadata contentMetadataCopy =
                new BluetoothLeAudioContentMetadata.Builder(contentMetadata).build();
        assertEquals(TEST_PROGRAM_INFO, contentMetadataCopy.getProgramInfo());
        assertEquals(TEST_LANGUAGE, contentMetadataCopy.getLanguage());
        assertArrayEquals(TEST_METADATA_BYTES, contentMetadataCopy.getRawMetadata());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createContentMetadataFromBytes() {
        BluetoothLeAudioContentMetadata contentMetadata =
                BluetoothLeAudioContentMetadata.fromRawBytes(TEST_METADATA_BYTES);
        byte[] metadataBytes = contentMetadata.getRawMetadata();
        assertNotNull(metadataBytes);
        assertArrayEquals(TEST_METADATA_BYTES, metadataBytes);
        assertEquals(TEST_PROGRAM_INFO, contentMetadata.getProgramInfo());
        assertEquals(TEST_LANGUAGE, contentMetadata.getLanguage());
    }
}
