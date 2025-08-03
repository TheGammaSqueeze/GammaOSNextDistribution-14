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

package hardware.google.bluetooth.sar;

@VintfStability
interface IBluetoothSar {
  /**
   * API to set Bluetooth Tx power cap.
   *
   * This API must be invoked whenever maximum Bluetooth Tx power needs to
   * changed for optimising performance purpose in SAR scenarios.
   *
   * @param cap Bluetooth Tx cap. Range is between 0 and 80 units,
   * each unit is 0.25 dBm.
   */
  oneway void setBluetoothTxPowerCap(in byte cap);

  /**
   * API to set Bluetooth Tx power cap of each tech.
   *
   * This API must be invoked whenever maximum Bluetooth Tx power for BR, EDR
   * and BLE needs to changed for optimising performance purpose in SAR
   * scenarios.
   *
   * @param brCap Bluetooth Tx cap. Range is between 0 and 80 units,
   * each unit is 0.25 dBm.
   * @param edrCap Bluetooth Tx cap. Range is between 0 and 80 units,
   * each unit is 0.25 dBm.
   * @param bleCap Bluetooth Tx cap. Range is between 0 and 80 units,
   * each unit is 0.25 dBm.
   */
  oneway void setBluetoothTechBasedTxPowerCap(in byte brCap, in byte edrCap,
                                              in byte bleCap);

  /**
   * API to set Bluetooth Tx power cap of each mode
   *
   * This API must be invoked whenever maximum Bluetooth Tx power of each mode
   * (BDR, EDR, BLE), including chain 0, chain 1 and beamforming mode, needs to
   * be changed for optimising performance purpose in SAR scenarios.
   *
   * @param chain0Cap Bluetooth Tx cap of chain 0 mode. Range is between 0 and
   * 80 units, each unit is 0.25 dBm.
   * @param chain1Cap Bluetooth Tx cap of chain 1 mode. Range is between 0 and
   * 80 units, each unit is 0.25 dBm.
   * @param beamformingCap Bluetooth Tx cap of beamforming mode. Range is
   * between 0 and 80 units, each unit is 0.25 dBm.
   */
  oneway void setBluetoothModeBasedTxPowerCap(in byte[3] chain0Cap,
                                              in byte[3] chain1Cap,
                                              in byte[6] beamformingCap);

  /**
   * API to set Bluetooth Tx power cap of each mode
   *
   * This API must be invoked whenever maximum Bluetooth Tx power of each mode
   * (BDR, EDR, BLE, HR), including chain 0, chain 1 and beamforming mode, needs
   * to be changed for optimising performance purpose in SAR scenarios.
   *
   * @param chain0Cap Bluetooth Tx cap of chain 0 mode. Range is between 0 and
   * 80 units, each unit is 0.25 dBm.
   * @param chain1Cap Bluetooth Tx cap of chain 1 mode. Range is between 0 and
   * 80 units, each unit is 0.25 dBm.
   * @param beamformingCap Bluetooth Tx cap of beamforming mode. Range is
   * between 0 and 80 units, each unit is 0.25 dBm.
   */

  oneway void setBluetoothModeBasedTxPowerCapPlusHR(in byte[4] chain0Cap,
                                                    in byte[4] chain1Cap,
                                                    in byte[8] beamformingCap);

  /**
   * API to set Bluetooth RF regulation area
   *
   * This API must be invoked whenever the maximum Bluetooth Tx power needs to
   * be changed due to changing of located area.
   *
   * @param a 3-digit country area code using the ISO 3166-1 numeric standard,
   * between 0-899. 900-999 may be user-assigned by vendor to add further names
   * of countries, territories, or regions.
   */
  oneway void setBluetoothAreaCode(in byte[3] areaCode);
}
