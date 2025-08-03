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

package hardware.google.ril_ext;

import hardware.google.ril_ext.CarrierConfig;

@VintfStability
interface IRilExt {
    /**
     * Set response functions for requests and indications.
     *
     * @param rilExtResponse Object containing response functions
     * @param rilExtIndication Object containing radio indications
     */
    void setCallback(in hardware.google.ril_ext.IRilExtResponse rilExtResponse,
            in hardware.google.ril_ext.IRilExtIndication rilExtIndication);
    /**
     * Set carrier ID to RIL HAL.
     *
     * @param serial Serial number of request
     * @param carrierId Android carrier ID
     *
     * Response function is IRilExtResponse.sendCarrierIdResponse()
     */
    void sendCarrierId(in int serial, in int carrierId);
    /**
     * Forward a list of the interested carrier configuration to RIL HAL.
     *
     * @param serial Serial number of request
     * @param carrierConfigs A list of carrier configs which are key/value pairs
     *
     * Response function is IRilExtResponse.sendCarrierConfigsResponse().
     */
    void sendCarrierConfigs(in int serial, in CarrierConfig[] carrierConfigs);
}
