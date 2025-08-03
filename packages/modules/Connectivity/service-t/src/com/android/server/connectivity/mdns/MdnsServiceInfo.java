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

import static com.android.server.connectivity.mdns.MdnsSocket.INTERFACE_INDEX_UNSPECIFIED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Network;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.net.module.util.ByteUtils;

import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A class representing a discovered mDNS service instance.
 *
 * @hide
 */
public class MdnsServiceInfo implements Parcelable {
    private static final Charset US_ASCII = Charset.forName("us-ascii");
    private static final Charset UTF_8 = Charset.forName("utf-8");

    /** @hide */
    public static final Parcelable.Creator<MdnsServiceInfo> CREATOR =
            new Parcelable.Creator<MdnsServiceInfo>() {

                @Override
                public MdnsServiceInfo createFromParcel(Parcel source) {
                    return new MdnsServiceInfo(
                            source.readString(),
                            source.createStringArray(),
                            source.createStringArrayList(),
                            source.createStringArray(),
                            source.readInt(),
                            source.createStringArrayList(),
                            source.createStringArrayList(),
                            source.createStringArrayList(),
                            source.createTypedArrayList(TextEntry.CREATOR),
                            source.readInt(),
                            source.readParcelable(Network.class.getClassLoader()),
                            Instant.ofEpochSecond(source.readLong()));
                }

                @Override
                public MdnsServiceInfo[] newArray(int size) {
                    return new MdnsServiceInfo[size];
                }
            };

    private final String serviceInstanceName;
    private final String[] serviceType;
    private final List<String> subtypes;
    private final String[] hostName;
    private final int port;
    @NonNull
    private final List<String> ipv4Addresses;
    @NonNull
    private final List<String> ipv6Addresses;
    final List<String> textStrings;
    @Nullable
    final List<TextEntry> textEntries;
    private final int interfaceIndex;

    private final Map<String, byte[]> attributes;
    @Nullable
    private final Network network;

    @NonNull
    private final Instant expirationTime;

    /** Constructs a {@link MdnsServiceInfo} object with default values. */
    public MdnsServiceInfo(
            String serviceInstanceName,
            String[] serviceType,
            @Nullable List<String> subtypes,
            String[] hostName,
            int port,
            @Nullable String ipv4Address,
            @Nullable String ipv6Address,
            @Nullable List<String> textStrings) {
        this(
                serviceInstanceName,
                serviceType,
                subtypes,
                hostName,
                port,
                List.of(ipv4Address),
                List.of(ipv6Address),
                textStrings,
                /* textEntries= */ null,
                /* interfaceIndex= */ INTERFACE_INDEX_UNSPECIFIED,
                /* network= */ null,
                /* expirationTime= */ Instant.MAX);
    }

    /** Constructs a {@link MdnsServiceInfo} object with default values. */
    public MdnsServiceInfo(
            String serviceInstanceName,
            String[] serviceType,
            List<String> subtypes,
            String[] hostName,
            int port,
            @Nullable String ipv4Address,
            @Nullable String ipv6Address,
            @Nullable List<String> textStrings,
            @Nullable List<TextEntry> textEntries) {
        this(
                serviceInstanceName,
                serviceType,
                subtypes,
                hostName,
                port,
                List.of(ipv4Address),
                List.of(ipv6Address),
                textStrings,
                textEntries,
                /* interfaceIndex= */ INTERFACE_INDEX_UNSPECIFIED,
                /* network= */ null,
                /* expirationTime= */ Instant.MAX);
    }

    /**
     * Constructs a {@link MdnsServiceInfo} object with default values.
     *
     * @hide
     */
    public MdnsServiceInfo(
            String serviceInstanceName,
            String[] serviceType,
            @Nullable List<String> subtypes,
            String[] hostName,
            int port,
            @Nullable String ipv4Address,
            @Nullable String ipv6Address,
            @Nullable List<String> textStrings,
            @Nullable List<TextEntry> textEntries,
            int interfaceIndex) {
        this(
                serviceInstanceName,
                serviceType,
                subtypes,
                hostName,
                port,
                List.of(ipv4Address),
                List.of(ipv6Address),
                textStrings,
                textEntries,
                interfaceIndex,
                /* network= */ null,
                /* expirationTime= */ Instant.MAX);
    }

    /**
     * Constructs a {@link MdnsServiceInfo} object with default values.
     *
     * @hide
     */
    public MdnsServiceInfo(
            String serviceInstanceName,
            String[] serviceType,
            @Nullable List<String> subtypes,
            String[] hostName,
            int port,
            @NonNull List<String> ipv4Addresses,
            @NonNull List<String> ipv6Addresses,
            @Nullable List<String> textStrings,
            @Nullable List<TextEntry> textEntries,
            int interfaceIndex,
            @Nullable Network network,
            @NonNull Instant expirationTime) {
        this.serviceInstanceName = serviceInstanceName;
        this.serviceType = serviceType;
        this.subtypes = new ArrayList<>();
        if (subtypes != null) {
            this.subtypes.addAll(subtypes);
        }
        this.hostName = hostName;
        this.port = port;
        this.ipv4Addresses = new ArrayList<>(ipv4Addresses);
        this.ipv6Addresses = new ArrayList<>(ipv6Addresses);
        this.textStrings = new ArrayList<>();
        if (textStrings != null) {
            this.textStrings.addAll(textStrings);
        }
        this.textEntries = (textEntries == null) ? null : new ArrayList<>(textEntries);

        // The module side sends both {@code textStrings} and {@code textEntries} for backward
        // compatibility. We should prefer only {@code textEntries} if it's not null.
        List<TextEntry> entries =
                (this.textEntries != null) ? this.textEntries : parseTextStrings(this.textStrings);
        // The map of attributes is case-insensitive.
        final Map<String, byte[]> attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (TextEntry entry : entries) {
            // Per https://datatracker.ietf.org/doc/html/rfc6763#section-6.4, only the first entry
            // of the same key should be accepted:
            // If a client receives a TXT record containing the same key more than once, then the
            // client MUST silently ignore all but the first occurrence of that attribute.
            attributes.putIfAbsent(entry.getKey(), entry.getValue());
        }
        this.attributes = Collections.unmodifiableMap(attributes);
        this.interfaceIndex = interfaceIndex;
        this.network = network;
        this.expirationTime = Instant.ofEpochSecond(expirationTime.getEpochSecond());
    }

    private static List<TextEntry> parseTextStrings(List<String> textStrings) {
        List<TextEntry> list = new ArrayList(textStrings.size());
        for (String textString : textStrings) {
            TextEntry entry = TextEntry.fromString(textString);
            if (entry != null) {
                list.add(entry);
            }
        }
        return Collections.unmodifiableList(list);
    }

    /** Returns the name of this service instance. */
    public String getServiceInstanceName() {
        return serviceInstanceName;
    }

    /** Returns the type of this service instance. */
    public String[] getServiceType() {
        return serviceType;
    }

    /** Returns the list of subtypes supported by this service instance. */
    public List<String> getSubtypes() {
        return new ArrayList<>(subtypes);
    }

    /** Returns {@code true} if this service instance supports any subtypes. */
    public boolean hasSubtypes() {
        return !subtypes.isEmpty();
    }

    /** Returns the host name of this service instance. */
    public String[] getHostName() {
        return hostName;
    }

    /** Returns the port number of this service instance. */
    public int getPort() {
        return port;
    }

    /** Returns the IPV4 addresses of this service instance. */
    @NonNull
    public List<String> getIpv4Addresses() {
        return Collections.unmodifiableList(ipv4Addresses);
    }

    /**
     * Returns the first IPV4 address of this service instance.
     *
     * @deprecated Use {@link #getIpv4Addresses()} to get the entire list of IPV4
     * addresses for
     * the host.
     */
    @Nullable
    @Deprecated
    public String getIpv4Address() {
        return ipv4Addresses.isEmpty() ? null : ipv4Addresses.get(0);
    }

    /** Returns the IPV6 address of this service instance. */
    @NonNull
    public List<String> getIpv6Addresses() {
        return Collections.unmodifiableList(ipv6Addresses);
    }

    /**
     * Returns the first IPV6 address of this service instance.
     *
     * @deprecated Use {@link #getIpv6Addresses()} to get the entire list of IPV6 addresses for
     * the host.
     */
    @Nullable
    @Deprecated
    public String getIpv6Address() {
        return ipv6Addresses.isEmpty() ? null : ipv6Addresses.get(0);
    }

    /**
     * Returns the index of the network interface at which this response was received, or -1 if the
     * index is not known.
     */
    public int getInterfaceIndex() {
        return interfaceIndex;
    }

    /**
     * Returns the network at which this response was received, or null if the network is unknown.
     */
    @Nullable
    public Network getNetwork() {
        return network;
    }

    /**
     * Returns the timestamp after when this service is expired or {@code null} if the expiration
     * time is unknown.
     *
     * A service is considered expired if any of its DNS record is expired.
     */
    @NonNull
    public Instant getExpirationTime() {
        return expirationTime;
    }

    /**
     * Returns attribute value for {@code key} as a UTF-8 string. It's the caller who must make sure
     * that the value of {@code key} is indeed a UTF-8 string. {@code null} will be returned if no
     * attribute value exists for {@code key}.
     */
    @Nullable
    public String getAttributeByKey(@NonNull String key) {
        byte[] value = getAttributeAsBytes(key);
        if (value == null) {
            return null;
        }
        return new String(value, UTF_8);
    }

    /**
     * Returns the attribute value for {@code key} as a byte array. {@code null} will be returned if
     * no attribute value exists for {@code key}.
     */
    @Nullable
    public byte[] getAttributeAsBytes(@NonNull String key) {
        return attributes.get(key);
    }

    /** Returns an immutable map of all attributes. */
    public Map<String, String> getAttributes() {
        Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, byte[]> kv : attributes.entrySet()) {
            final byte[] value = kv.getValue();
            map.put(kv.getKey(), value == null ? null : new String(value, UTF_8));
        }
        return Collections.unmodifiableMap(map);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(serviceInstanceName);
        out.writeStringArray(serviceType);
        out.writeStringList(subtypes);
        out.writeStringArray(hostName);
        out.writeInt(port);
        out.writeStringList(ipv4Addresses);
        out.writeStringList(ipv6Addresses);
        out.writeStringList(textStrings);
        out.writeTypedList(textEntries);
        out.writeInt(interfaceIndex);
        out.writeParcelable(network, 0);
        out.writeLong(expirationTime.getEpochSecond());
    }

    @Override
    public String toString() {
        return "Name: " + serviceInstanceName
                + ", type: " + TextUtils.join(".", serviceType)
                + ", subtypes: " + TextUtils.join(",", subtypes)
                + ", ip: " + ipv4Addresses
                + ", ipv6: " + ipv6Addresses
                + ", port: " + port
                + ", interfaceIndex: " + interfaceIndex
                + ", network: " + network
                + ", textStrings: " + textStrings
                + ", textEntries: " + textEntries
                + ", expirationTime: " + expirationTime;
    }


    /** Represents a DNS TXT key-value pair defined by RFC 6763. */
    public static final class TextEntry implements Parcelable {
        public static final Parcelable.Creator<TextEntry> CREATOR =
                new Parcelable.Creator<TextEntry>() {
                    @Override
                    public TextEntry createFromParcel(Parcel source) {
                        return new TextEntry(source);
                    }

                    @Override
                    public TextEntry[] newArray(int size) {
                        return new TextEntry[size];
                    }
                };

        private final String key;
        private final byte[] value;

        /** Creates a new {@link TextEntry} instance from a '=' separated string. */
        @Nullable
        public static TextEntry fromString(String textString) {
            return fromBytes(textString.getBytes(UTF_8));
        }

        /** Creates a new {@link TextEntry} instance from a '=' separated byte array. */
        @Nullable
        public static TextEntry fromBytes(byte[] textBytes) {
            int delimitPos = ByteUtils.indexOf(textBytes, (byte) '=');

            // Per https://datatracker.ietf.org/doc/html/rfc6763#section-6.4:
            // 1. The key MUST be at least one character.  DNS-SD TXT record strings
            // beginning with an '=' character (i.e., the key is missing) MUST be
            // silently ignored.
            // 2. If there is no '=' in a DNS-SD TXT record string, then it is a
            // boolean attribute, simply identified as being present, with no value.
            if (delimitPos < 0) {
                return new TextEntry(new String(textBytes, US_ASCII), (byte[]) null);
            } else if (delimitPos == 0) {
                return null;
            }
            return new TextEntry(
                    new String(Arrays.copyOf(textBytes, delimitPos), US_ASCII),
                    Arrays.copyOfRange(textBytes, delimitPos + 1, textBytes.length));
        }

        /** Creates a new {@link TextEntry} with given key and value of a UTF-8 string. */
        public TextEntry(String key, String value) {
            this(key, value == null ? null : value.getBytes(UTF_8));
        }

        /** Creates a new {@link TextEntry} with given key and value of a byte array. */
        public TextEntry(String key, byte[] value) {
            this.key = key;
            this.value = value == null ? null : value.clone();
        }

        private TextEntry(Parcel in) {
            key = in.readString();
            value = in.createByteArray();
        }

        public String getKey() {
            return key;
        }

        public byte[] getValue() {
            return value == null ? null : value.clone();
        }

        /** Converts this {@link TextEntry} instance to '=' separated byte array. */
        public byte[] toBytes() {
            final byte[] keyBytes = key.getBytes(US_ASCII);
            if (value == null) {
                return keyBytes;
            }
            return ByteUtils.concat(keyBytes, new byte[]{'='}, value);
        }

        /** Converts this {@link TextEntry} instance to '=' separated string. */
        @Override
        public String toString() {
            if (value == null) {
                return key;
            }
            return key + "=" + new String(value, UTF_8);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            } else if (!(other instanceof TextEntry)) {
                return false;
            }
            TextEntry otherEntry = (TextEntry) other;

            return key.equals(otherEntry.key) && Arrays.equals(value, otherEntry.value);
        }

        @Override
        public int hashCode() {
            return 31 * key.hashCode() + Arrays.hashCode(value);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeString(key);
            out.writeByteArray(value);
        }
    }
}
