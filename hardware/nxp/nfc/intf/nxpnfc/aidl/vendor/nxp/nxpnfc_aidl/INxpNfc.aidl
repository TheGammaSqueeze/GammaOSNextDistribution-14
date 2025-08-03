/******************************************************************************
 *
 *  Copyright 2022 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package vendor.nxp.nxpnfc_aidl;

@VintfStability
interface INxpNfc {
    /**
     * Gets vendor params values whose Key has been provided.
     *
     * @param string
     * @param out output data as string
     */
    String getVendorParam(in String key);

    /**
     * reset the ese based on resettype
     *
     * @param uint64_t to specify resetType
     * @return as a boolean, true if success, false if failed
     */
    boolean resetEse(in long resetType);

    /**
     * Sets Transit config value
     *
     * @param string transit config value
     * @return as a boolean, true if success, false if failed
     */
    boolean setNxpTransitConfig(in String transitConfValue);

    /**
     * Saves the vendor params provided as key-value pair
     *
     * @param string key string value
     * @return as a boolean, true if success, false if failed
     */
    boolean setVendorParam(in String key, in String value);
}
