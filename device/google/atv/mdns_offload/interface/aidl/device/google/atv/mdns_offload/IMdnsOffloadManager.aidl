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

package device.google.atv.mdns_offload;

/**
 * Offload interface to collect, prioritize and offload mDNS responses.
 * All methods act asynchronously.
 * They all consume a clientToken binder, to cleanup offload memory if the client app dies.
 */
@VintfStability
interface IMdnsOffloadManager {
    /**
     * Data class to collect mDNS information to offload on the network interfaces.
     */
    parcelable OffloadServiceInfo {
        String serviceName;
        String serviceType;
        String deviceHostName;
        byte[] rawOffloadPacket;
    }

    /**
     * Collects the offload responses intent for a given network interface and
     * returns a key representing the intent. The system will do its best to offload
     * the given responses asynchronously.
     *
     * If the binder instance dies, the records will be automatically cleaned up from
     * the offload service and the offload manager.
     */
    int addProtocolResponses(
            String networkInterface, in OffloadServiceInfo offloadData, IBinder clientToken);

    /**
     * Removes the offload intent from the manager. Any offloaded responses will also
     * be removed from the offload service.
     * If the binder doesn't match with the one used for registering, nothing
     * happens.
     */
    void removeProtocolResponses(int recordKey, IBinder clientToken);

    /**
     * Collects a passthrough intent for a given network interface. The system will do its best to
     * allow the passtrough.
     *
     * If the binder instance dies, the passthrough intents will be automatically
     * cleaned up from the offload service and the offload manager.
     */
    void addToPassthroughList(String networkInterface, String qname, IBinder clientToken);

    /**
     * Remove the passthrough intent for the given network interface. If the passthrough was active
     * it is immediately discarded from the offload service.
     *
     * If the binder instance doesn't match with the one used for registering, nothing happens.
     */
    void removeFromPassthroughList(String networkInterface, String qname, IBinder clientToken);
}
