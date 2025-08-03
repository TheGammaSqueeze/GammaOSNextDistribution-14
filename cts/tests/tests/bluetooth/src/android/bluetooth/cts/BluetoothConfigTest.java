/*
 * Copyright 2022 The Android Open Source Project
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
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.invoke.MethodHandles;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothConfigTest {
    private static final String TAG = MethodHandles.lookup().lookupClass().getSimpleName();

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mHasBluetooth = TestUtils.hasBluetooth();
        if (!mHasBluetooth) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
    }

    @After
    public void tearDown() throws Exception {
        if (!mHasBluetooth) return;

        mAdapter = null;
        mUiAutomation.dropShellPermissionIdentity();
    }

    private int checkIsProfileEnabledInList(int profile, List<Integer> supportedProfiles) {
        final boolean isEnabled = TestUtils.isProfileEnabled(profile);
        final boolean isSupported = supportedProfiles.contains(profile);

        // isEnabled => isSupported
        if (!isEnabled || isSupported) {
            return 0;
        }
        Log.e(TAG, "Profile config does not match for profile: "
                + BluetoothProfile.getProfileName(profile)
                + ". Config currently return: " + isEnabled
                + ". Is profile in the list: " + isSupported);
        return 1;
    }

    @Test
    public void profileEnabledValueInList() {
        assumeTrue(mHasBluetooth);

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        final List<Integer> pList = mAdapter.getSupportedProfiles();
        int wrong_config_in_list = checkIsProfileEnabledInList(BluetoothProfile.A2DP, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.A2DP_SINK, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.AVRCP_CONTROLLER, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.CSIP_SET_COORDINATOR, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.GATT, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.HAP_CLIENT, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.HEADSET, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.HEADSET_CLIENT, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.HEARING_AID, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.HID_DEVICE, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.HID_HOST, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.LE_AUDIO, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.LE_AUDIO_BROADCAST, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.MAP, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.MAP_CLIENT, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.OPP, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.PAN, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.PBAP, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.PBAP_CLIENT, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.SAP, pList)
            + checkIsProfileEnabledInList(BluetoothProfile.VOLUME_CONTROL, pList);

        assertEquals("Config does not match adapter hardware support. CHECK THE PREVIOUS LOGS.",
                0, wrong_config_in_list);
    }

    private int checkIsProfileEnabled(int profile, int adapterSupport) {
        final boolean isEnabled = TestUtils.isProfileEnabled(profile);
        final boolean isSupported = BluetoothStatusCodes.FEATURE_SUPPORTED == adapterSupport;

        // isEnabled => isSupported
        if (!isEnabled || isSupported) {
            return 0;
        }
        Log.e(TAG, "Profile config does not match for profile: "
                + BluetoothProfile.getProfileName(profile)
                + ". Config currently return: " + TestUtils.isProfileEnabled(profile)
                + ". Adapter support return: " + adapterSupport);
        return 1;
    }

    @Test
    public void profileEnabledValue() {
        assumeTrue(mHasBluetooth);

        int wrong_config =
            checkIsProfileEnabled(BluetoothProfile.LE_AUDIO,
                    mAdapter.isLeAudioSupported())
            + checkIsProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST,
                    mAdapter.isLeAudioBroadcastSourceSupported())
            + checkIsProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT,
                    mAdapter.isLeAudioBroadcastAssistantSupported());

        assertEquals("Config does not match adapter hardware support. CHECK THE PREVIOUS LOGS.",
                0, wrong_config);
    }

    @Test
    public void bleCDDRequirement() {
        assumeTrue(mHasBluetooth);

        // If device implementations return true for isLeAudioSupported():
        // [C-7-5] MUST enable simultaneously:
        //      BAP unicast client,
        //      CSIP set coordinator,
        //      MCP server,
        //      VCP controller,
        //      CCP server,
        if (mAdapter.isLeAudioSupported()
                == BluetoothStatusCodes.FEATURE_SUPPORTED) {
            assertTrue("BAP unicast config must be true when LeAudio is supported. [C-7-5]",
                    BluetoothProperties.isProfileBapUnicastClientEnabled().orElse(false));
            assertTrue("CSIP config must be true when LeAudio is supported. [C-7-5]",
                    BluetoothProperties.isProfileCsipSetCoordinatorEnabled().orElse(false));
            assertTrue("MCP config must be true when LeAudio is supported. [C-7-5]",
                    BluetoothProperties.isProfileMcpServerEnabled().orElse(false));
            assertTrue("VCP config must be true when LeAudio is supported. [C-7-5]",
                    BluetoothProperties.isProfileVcpControllerEnabled().orElse(false));
            assertTrue("CCP config must be true when LeAudio is supported. [C-7-5]",
                    BluetoothProperties.isProfileCcpServerEnabled().orElse(false));
        }

        // If device implementations return true for isLeAudioBroadcastSourceSupported():
        // [C-8-2] MUST enable simultaneously:
        //      BAP broadcast source,
        //      BAP broadcast assistant
        if (mAdapter.isLeAudioBroadcastSourceSupported()
                == BluetoothStatusCodes.FEATURE_SUPPORTED) {
            assertTrue("BAP broadcast source config must be true when adapter support "
                    + "BroadcastSource. [C-8-2]",
                    BluetoothProperties.isProfileBapBroadcastSourceEnabled().orElse(false));
            assertTrue("BAP broadcast assistant config must be true when adapter support "
                    + "BroadcastSource. [C-8-2]",
                    BluetoothProperties.isProfileBapBroadcastAssistEnabled().orElse(false));
        }
    }
}
