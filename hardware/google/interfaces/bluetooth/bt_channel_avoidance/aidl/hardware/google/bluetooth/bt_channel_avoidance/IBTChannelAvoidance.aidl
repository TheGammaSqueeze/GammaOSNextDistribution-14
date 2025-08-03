/*
 * Copyright 2023 The Android Open Source Project
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

/**
 * This is pixel only extension for Bluetooth HAL.
 */
package hardware.google.bluetooth.bt_channel_avoidance;

@VintfStability
interface IBTChannelAvoidance {
    /**
     * API to set Bluetooth channel map.
     *
     * This API must be invoked whenever the Bluetooth channel map needs to be
     * changed because Bluetooth channels have conflict with wifi or cellular.
     *
     * @param channelMap The lower 79 bits of the 80-bit parameter represents
     *        the status of 79 channels. 0 means the channel is bad and 1 means
     *        unknown. The most significant bit (bit 79) is reserved for future
     *        use.
     */
    oneway void setBluetoothChannelStatus(in byte[10] channelMap);
}
