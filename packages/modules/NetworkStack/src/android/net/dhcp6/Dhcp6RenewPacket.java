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

package android.net.dhcp6;

import static com.android.net.module.util.NetworkStackConstants.DHCP_MAX_LENGTH;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

/**
 * DHCPv6 RENEW packet class, a client sends an Renew message to the server that originally
 * provided the client's leases and configuration parameters to extend the lifetimes on the
 * leases assigned to the client and to update other configuration parameters.
 *
 * https://tools.ietf.org/html/rfc8415#page-24
 */
public class Dhcp6RenewPacket extends Dhcp6Packet {
    /**
     * Generates a renew packet with the specified parameters.
     */
    Dhcp6RenewPacket(int transId, int elapsedTime, @NonNull final byte[] clientDuid,
            @NonNull final byte[] serverDuid, final byte[] iapd) {
        super(transId, elapsedTime, clientDuid, serverDuid, iapd);
    }

    /**
     * Build a DHCPv6 Renew message with the specific parameters.
     */
    public ByteBuffer buildPacket() {
        final ByteBuffer packet = ByteBuffer.allocate(DHCP_MAX_LENGTH);
        final int msgTypeAndTransId = (DHCP6_MESSAGE_TYPE_RENEW << 24) | mTransId;
        packet.putInt(msgTypeAndTransId);

        addTlv(packet, DHCP6_SERVER_IDENTIFIER, mServerDuid);
        addTlv(packet, DHCP6_CLIENT_IDENTIFIER, mClientDuid);
        addTlv(packet, DHCP6_ELAPSED_TIME, (short) (mElapsedTime & 0xFFFF));
        addTlv(packet, DHCP6_IA_PD, mIaPd);

        packet.flip();
        return packet;
    }
}
