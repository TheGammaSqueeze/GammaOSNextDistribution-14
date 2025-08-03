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

import static com.android.bluetooth.flags.Flags.leaudioBroadcastFeatureSupport;
import static com.android.modules.utils.build.SdkLevel.isAtLeastV;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.errorprone.annotations.InlineMe;

public class TestUtils extends android.bluetooth.test_utils.TestUtils {
    /**
     * Get the current enabled status of a given profile.
     *
     * <p>This method also adds default value to profile enable state based on the designed logic of
     * the Android framework. For example: <br>
     * {@link BluetoothProfile#GATT} is always enabled by default <br>
     * {@link BluetoothProfile#HEARING_AID} should be enabled by default except for Watch,
     * Automotive, and TV
     *
     * @param profile a Bluetooth profile from {@link BluetoothProfile}
     * @return true if the profile should be enabled
     * @deprecated one should use functions from {@link BluetoothProperties} as much as possible
     */
    @Deprecated
    public static boolean isProfileEnabled(int profile) {
        switch (profile) {
            case BluetoothProfile.A2DP -> {
                return BluetoothProperties.isProfileA2dpSourceEnabled().orElse(false);
            }
            case BluetoothProfile.A2DP_SINK -> {
                return BluetoothProperties.isProfileA2dpSinkEnabled().orElse(false);
            }
                // Hidden profile
                // case BluetoothProfile.AVRCP:
                //     return BluetoothProperties.isProfileAvrcpTargetEnabled().orElse(false);
            case BluetoothProfile.AVRCP_CONTROLLER -> {
                return BluetoothProperties.isProfileAvrcpControllerEnabled().orElse(false);
            }
            case BluetoothProfile.CSIP_SET_COORDINATOR -> {
                return BluetoothProperties.isProfileCsipSetCoordinatorEnabled().orElse(false);
            }
            case BluetoothProfile.GATT -> {
                return BluetoothProperties.isProfileGattEnabled().orElse(true);
            }
            case BluetoothProfile.HAP_CLIENT -> {
                return BluetoothProperties.isProfileHapClientEnabled().orElse(false);
            }
            case BluetoothProfile.HEADSET -> {
                return BluetoothProperties.isProfileHfpAgEnabled().orElse(false);
            }
            case BluetoothProfile.HEADSET_CLIENT -> {
                return BluetoothProperties.isProfileHfpHfEnabled().orElse(false);
            }
            case BluetoothProfile.HEARING_AID -> {
                Context context = InstrumentationRegistry.getInstrumentation().getContext();
                if (!isBleSupported(context)) {
                    return false;
                }
                boolean default_value = true;
                if (isAutomotive(context) || isWatch(context) || isTv(context)) {
                    default_value = false;
                }
                return BluetoothProperties.isProfileAshaCentralEnabled().orElse(default_value);
            }
            case BluetoothProfile.HID_DEVICE -> {
                return BluetoothProperties.isProfileHidDeviceEnabled().orElse(false);
            }
            case BluetoothProfile.HID_HOST -> {
                return BluetoothProperties.isProfileHidHostEnabled().orElse(false);
            }
            case BluetoothProfile.LE_AUDIO -> {
                return BluetoothProperties.isProfileBapUnicastClientEnabled().orElse(false);
            }
            case BluetoothProfile.LE_AUDIO_BROADCAST -> {
                return isAtLeastV()
                        && leaudioBroadcastFeatureSupport()
                        && BluetoothProperties.isProfileBapBroadcastSourceEnabled().orElse(false);
            }
            case BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT -> {
                return isAtLeastV()
                        && leaudioBroadcastFeatureSupport()
                        && BluetoothProperties.isProfileBapBroadcastAssistEnabled().orElse(false);
            }
                // Hidden profile
                // case BluetoothProfile.LE_CALL_CONTROL:
                //     return BluetoothProperties.isProfileCcpServerEnabled().orElse(false);
            case BluetoothProfile.MAP -> {
                return BluetoothProperties.isProfileMapServerEnabled().orElse(false);
            }
            case BluetoothProfile.MAP_CLIENT -> {
                return BluetoothProperties.isProfileMapClientEnabled().orElse(false);
            }
                // Hidden profile
                // case BluetoothProfile.MCP_SERVER:
                //     return BluetoothProperties.isProfileMcpServerEnabled().orElse(false);
            case BluetoothProfile.OPP -> {
                return BluetoothProperties.isProfileOppEnabled().orElse(false);
            }
            case BluetoothProfile.PAN -> {
                return BluetoothProperties.isProfilePanNapEnabled().orElse(false)
                        || BluetoothProperties.isProfilePanPanuEnabled().orElse(false);
            }
            case BluetoothProfile.PBAP -> {
                return BluetoothProperties.isProfilePbapServerEnabled().orElse(false);
            }
            case BluetoothProfile.PBAP_CLIENT -> {
                return BluetoothProperties.isProfilePbapClientEnabled().orElse(false);
            }
            case BluetoothProfile.SAP -> {
                return BluetoothProperties.isProfileSapServerEnabled().orElse(false);
            }
            case BluetoothProfile.VOLUME_CONTROL -> {
                return BluetoothProperties.isProfileVcpControllerEnabled().orElse(false);
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Get {@link BluetoothAdapter} via {@link android.bluetooth.BluetoothManager} Fail the test if
     * {@link BluetoothAdapter} is null
     *
     * @return instance of {@link BluetoothAdapter}
     * @deprecated keeping assert here as many tests currently depend on this method to fail if
     *     adapter is null
     */
    @Deprecated
    @NonNull
    public static BluetoothAdapter getBluetoothAdapterOrDie() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        assertThat(manager).isNotNull();
        BluetoothAdapter adapter = manager.getAdapter();
        assertThat(adapter).isNotNull();
        return adapter;
    }

    /**
     * Utility method to assert two byte arrays are equal.
     *
     * @param expected expected value
     * @param actual actual value
     * @deprecated Please use {@link com.google.common.truth.Truth},
     *     "assertThat(actual).isEqualTo(expected)". Keeping it here since some tests are still
     *     using it.
     */
    @Deprecated
    @InlineMe(
            replacement = "assertThat(actual).isEqualTo(expected)",
            staticImports = "com.google.common.truth.Truth.assertThat")
    public static void assertArrayEquals(byte[] expected, byte[] actual) {
        assertThat(actual).isEqualTo(expected);
    }

    /**
     * DANGER: Put the current thread to sleep. Please only use this when it is ok to block the
     * current thread.
     *
     * @param sleepMillis number of milliseconds to sleep for
     * @deprecated Please try to avoid using this method at all cost, but use asynchronous wait to
     *     handle timing conditions
     */
    @Deprecated
    public static void sleep(int sleepMillis) {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted", e);
        }
    }
}
