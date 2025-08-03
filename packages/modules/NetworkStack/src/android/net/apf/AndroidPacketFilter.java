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
package android.net.apf;

import android.net.LinkProperties;
import android.net.NattKeepalivePacketDataParcelable;
import android.net.TcpKeepalivePacketDataParcelable;

import com.android.internal.util.IndentingPrintWriter;

/**
 * The interface for AndroidPacketFilter
 */
public interface AndroidPacketFilter {
    /**
     * Update the LinkProperties that will be used by APF.
     */
    void setLinkProperties(LinkProperties lp);

    /**
     * Shutdown the APF.
     */
    void shutdown();

    /**
     * Switch for the multicast filter.
     * @param isEnabled if  the multicast filter should be enabled or not.
     */
    void setMulticastFilter(boolean isEnabled);

    /**
     * Set the APF data snapshot.
     */
    void setDataSnapshot(byte[] data);

    /**
     * Add TCP keepalive ack packet filter.
     * This will add a filter to drop acks to the keepalive packet passed as an argument.
     *
     * @param slot The index used to access the filter.
     * @param sentKeepalivePacket The attributes of the sent keepalive packet.
     */
    void addTcpKeepalivePacketFilter(int slot,
            TcpKeepalivePacketDataParcelable sentKeepalivePacket);

    /**
     * Add NAT-T keepalive packet filter.
     * This will add a filter to drop NAT-T keepalive packet which is passed as an argument.
     *
     * @param slot The index used to access the filter.
     * @param sentKeepalivePacket The attributes of the sent keepalive packet.
     */
    void addNattKeepalivePacketFilter(int slot,
            NattKeepalivePacketDataParcelable sentKeepalivePacket);

    /**
     * Remove keepalive packet filter.
     *
     * @param slot The index used to access the filter.
     */
    void removeKeepalivePacketFilter(int slot);

    /**
     * Dump the status of APF.
     */
    void dump(IndentingPrintWriter pw);
}
