/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.connectivity.mdns;

import android.annotation.Nullable;

import androidx.annotation.VisibleForTesting;

import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** An mDNS "SRV" record, which contains service information. */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class MdnsServiceRecord extends MdnsRecord {
    public static final int PROTO_NONE = 0;
    public static final int PROTO_TCP = 1;
    public static final int PROTO_UDP = 2;
    private static final String PROTO_TOKEN_TCP = "_tcp";
    private static final String PROTO_TOKEN_UDP = "_udp";
    private int servicePriority;
    private int serviceWeight;
    private int servicePort;
    private String[] serviceHost;

    public MdnsServiceRecord(String[] name, MdnsPacketReader reader) throws IOException {
        this(name, reader, false);
    }

    public MdnsServiceRecord(String[] name, MdnsPacketReader reader, boolean isQuestion)
            throws IOException {
        super(name, TYPE_SRV, reader, isQuestion);
    }

    public MdnsServiceRecord(String[] name, boolean isUnicast) {
        super(name, TYPE_SRV,
                MdnsConstants.QCLASS_INTERNET | (isUnicast ? MdnsConstants.QCLASS_UNICAST : 0),
                0L /* receiptTimeMillis */, false /* cacheFlush */, 0L /* ttlMillis */);
    }

    public MdnsServiceRecord(String[] name, long receiptTimeMillis, boolean cacheFlush,
                    long ttlMillis, int servicePriority, int serviceWeight, int servicePort,
                    String[] serviceHost) {
        super(name, TYPE_SRV, MdnsConstants.QCLASS_INTERNET, receiptTimeMillis, cacheFlush,
                ttlMillis);
        this.servicePriority = servicePriority;
        this.serviceWeight = serviceWeight;
        this.servicePort = servicePort;
        this.serviceHost = serviceHost;
    }

    /** Returns the service's port number. */
    public int getServicePort() {
        return servicePort;
    }

    /** Returns the service's host name. */
    public String[] getServiceHost() {
        return serviceHost;
    }

    /** Returns the service's priority. */
    public int getServicePriority() {
        return servicePriority;
    }

    /** Returns the service's weight. */
    public int getServiceWeight() {
        return serviceWeight;
    }

    // Format of name is <instance-name>.<service-name>.<protocol>.<domain>

    /** Returns the service's instance name, which uniquely identifies the service instance. */
    public String getServiceInstanceName() {
        if (name.length < 1) {
            return null;
        }
        return name[0];
    }

    /** Returns the service's name. */
    public String getServiceName() {
        if (name.length < 2) {
            return null;
        }
        return name[1];
    }

    /** Returns the service's protocol. */
    public int getServiceProtocol() {
        if (name.length < 3) {
            return PROTO_NONE;
        }

        String protocol = name[2];
        if (protocol.equals(PROTO_TOKEN_TCP)) {
            return PROTO_TCP;
        }
        if (protocol.equals(PROTO_TOKEN_UDP)) {
            return PROTO_UDP;
        }
        return PROTO_NONE;
    }

    @Override
    protected void readData(MdnsPacketReader reader) throws IOException {
        servicePriority = reader.readUInt16();
        serviceWeight = reader.readUInt16();
        servicePort = reader.readUInt16();
        serviceHost = reader.readLabels();
    }

    @Override
    protected void writeData(MdnsPacketWriter writer) throws IOException {
        writer.writeUInt16(servicePriority);
        writer.writeUInt16(serviceWeight);
        writer.writeUInt16(servicePort);
        writer.writeLabels(serviceHost);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT,
                "SRV: %s:%d (prio=%d, weight=%d)",
                labelsToString(serviceHost),
                servicePort,
                servicePriority,
                serviceWeight);
    }

    @Override
    public int hashCode() {
        return (super.hashCode() * 31)
                + Objects.hash(servicePriority, serviceWeight,
                Arrays.hashCode(MdnsUtils.toDnsLabelsLowerCase(serviceHost)),
                servicePort);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MdnsServiceRecord)) {
            return false;
        }
        MdnsServiceRecord otherRecord = (MdnsServiceRecord) other;

        return super.equals(other)
                && (servicePriority == otherRecord.servicePriority)
                && (serviceWeight == otherRecord.serviceWeight)
                && MdnsUtils.equalsDnsLabelIgnoreDnsCase(serviceHost, otherRecord.serviceHost)
                && (servicePort == otherRecord.servicePort);
    }
}
