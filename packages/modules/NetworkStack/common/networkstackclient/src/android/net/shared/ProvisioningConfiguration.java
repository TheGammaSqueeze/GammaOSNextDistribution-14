/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net.shared;

import static android.net.ip.IIpClient.HOSTNAME_SETTING_UNSET;
import static android.net.ip.IIpClient.PROV_IPV4_DHCP;
import static android.net.ip.IIpClient.PROV_IPV4_DISABLED;
import static android.net.ip.IIpClient.PROV_IPV4_STATIC;
import static android.net.ip.IIpClient.PROV_IPV6_DISABLED;
import static android.net.ip.IIpClient.PROV_IPV6_LINKLOCAL;
import static android.net.ip.IIpClient.PROV_IPV6_SLAAC;
import static android.net.shared.ParcelableUtil.fromParcelableArray;
import static android.net.shared.ParcelableUtil.toParcelableArray;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.InformationElementParcelable;
import android.net.Network;
import android.net.ProvisioningConfigurationParcelable;
import android.net.ScanResultInfoParcelable;
import android.net.StaticIpConfiguration;
import android.net.apf.ApfCapabilities;
import android.net.ip.IIpClient;
import android.net.networkstack.aidl.dhcp.DhcpOption;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * This class encapsulates parameters to be passed to
 * IpClient#startProvisioning(). A defensive copy is made by IpClient
 * and the values specified herein are in force until IpClient#stop()
 * is called.
 *
 * Example use:
 *
 *     final ProvisioningConfiguration config =
 *             new ProvisioningConfiguration.Builder()
 *                     .withPreDhcpAction()
 *                     .withProvisioningTimeoutMs(36 * 1000)
 *                     .build();
 *     mIpClient.startProvisioning(config.toStableParcelable());
 *     ...
 *     mIpClient.stop();
 *
 * The specified provisioning configuration will only be active until
 * IIpClient#stop() is called. Future calls to IIpClient#startProvisioning()
 * must specify the configuration again.
 * @hide
 */
public class ProvisioningConfiguration {
    private static final String TAG = "ProvisioningConfiguration";

    // TODO: Delete this default timeout once those callers that care are
    // fixed to pass in their preferred timeout.
    //
    // We pick 18 seconds so we can send DHCP requests at
    //
    //     t=0, t=1, t=3, t=7, t=16
    //
    // allowing for 10% jitter.
    private static final int DEFAULT_TIMEOUT_MS = 18 * 1000;

    // TODO: These cannot be imported from INetd.aidl, because networkstack-client cannot depend on
    // INetd, as there are users of IpClient that depend on INetd directly (potentially at a
    // different version, which is not allowed by the build system).
    // Find a better way to express these constants.
    public static final int IPV6_ADDR_GEN_MODE_EUI64 = 0;
    public static final int IPV6_ADDR_GEN_MODE_STABLE_PRIVACY = 2;

    // ipv4ProvisioningMode and ipv6ProvisioningMode members are introduced since
    // networkstack-aidl-interfaces-v12.
    public static final int VERSION_ADDED_PROVISIONING_ENUM = 12;

    /**
     * Builder to create a {@link ProvisioningConfiguration}.
     */
    public static class Builder {
        protected ProvisioningConfiguration mConfig = new ProvisioningConfiguration();

        /**
         * Specify that the configuration should not enable IPv4. It is enabled by default.
         */
        public Builder withoutIPv4() {
            mConfig.mIPv4ProvisioningMode = PROV_IPV4_DISABLED;
            return this;
        }

        /**
         * Specify that the configuration should not enable IPv6. It is enabled by default.
         */
        public Builder withoutIPv6() {
            mConfig.mIPv6ProvisioningMode = PROV_IPV6_DISABLED;
            return this;
        }

        /**
         * Specify that the configuration should not use a MultinetworkPolicyTracker. It is used
         * by default.
         */
        public Builder withoutMultinetworkPolicyTracker() {
            mConfig.mUsingMultinetworkPolicyTracker = false;
            return this;
        }

        /**
         * Specify that the configuration should not use a IpReachabilityMonitor. It is used by
         * default.
         */
        public Builder withoutIpReachabilityMonitor() {
            mConfig.mUsingIpReachabilityMonitor = false;
            return this;
        }

        /**
         * Identical to {@link #withPreDhcpAction(int)}, using a default timeout.
         * @see #withPreDhcpAction(int)
         */
        public Builder withPreDhcpAction() {
            mConfig.mRequestedPreDhcpActionMs = DEFAULT_TIMEOUT_MS;
            return this;
        }

        /**
         * Specify that {@link IpClientCallbacks#onPreDhcpAction()} should be called. Clients must
         * call {@link IIpClient#completedPreDhcpAction()} when the callback called. This behavior
         * is disabled by default.
         * @param dhcpActionTimeoutMs Timeout for clients to call completedPreDhcpAction().
         */
        public Builder withPreDhcpAction(int dhcpActionTimeoutMs) {
            mConfig.mRequestedPreDhcpActionMs = dhcpActionTimeoutMs;
            return this;
        }

        /**
         * Specify that preconnection feature would be enabled. It's not used by default.
         */
        public Builder withPreconnection() {
            mConfig.mEnablePreconnection = true;
            return this;
        }

        /**
         * Specify the initial provisioning configuration.
         */
        public Builder withInitialConfiguration(InitialConfiguration initialConfig) {
            mConfig.mInitialConfig = initialConfig;
            return this;
        }

        /**
         * Specify a static configuration for provisioning.
         */
        public Builder withStaticConfiguration(StaticIpConfiguration staticConfig) {
            mConfig.mIPv4ProvisioningMode = PROV_IPV4_STATIC;
            mConfig.mStaticIpConfig = staticConfig;
            return this;
        }

        /**
         * Specify ApfCapabilities.
         */
        public Builder withApfCapabilities(ApfCapabilities apfCapabilities) {
            mConfig.mApfCapabilities = apfCapabilities;
            return this;
        }

        /**
         * Specify the timeout to use for provisioning.
         */
        public Builder withProvisioningTimeoutMs(int timeoutMs) {
            mConfig.mProvisioningTimeoutMs = timeoutMs;
            return this;
        }

        /**
         * Specify that IPv6 address generation should use a random MAC address.
         */
        public Builder withRandomMacAddress() {
            mConfig.mIPv6AddrGenMode = IPV6_ADDR_GEN_MODE_EUI64;
            return this;
        }

        /**
         * Specify that IPv6 address generation should use a stable MAC address.
         */
        public Builder withStableMacAddress() {
            mConfig.mIPv6AddrGenMode = IPV6_ADDR_GEN_MODE_STABLE_PRIVACY;
            return this;
        }

        /**
         * Specify the network to use for provisioning.
         */
        public Builder withNetwork(Network network) {
            mConfig.mNetwork = network;
            return this;
        }

        /**
         * Specify the display name that the IpClient should use.
         */
        public Builder withDisplayName(String displayName) {
            mConfig.mDisplayName = displayName;
            return this;
        }

        /**
         * Specify the UID of the remote entity that created this Network.
         */
        public Builder withCreatorUid(int creatoruid) {
            mConfig.mCreatorUid = creatoruid;
            return this;
        }

        /**
         * Specify the information elements included in wifi scan result that was obtained
         * prior to connecting to the access point, if this is a WiFi network.
         *
         * <p>The scan result can be used to infer whether the network is metered.
         */
        public Builder withScanResultInfo(ScanResultInfo scanResultInfo) {
            mConfig.mScanResultInfo = scanResultInfo;
            return this;
        }

        /**
         * Specify the L2 information(bssid, l2key and cluster) that the IpClient should use.
         */
        public Builder withLayer2Information(Layer2Information layer2Info) {
            mConfig.mLayer2Info = layer2Info;
            return this;
        }

        /**
         * Specify the customized DHCP options to be put in the PRL or in the DHCP packet. Options
         * with null value will be put in the PRL.
         *
         * @param: options customized DHCP option stable parcelable list.
         */
        public Builder withDhcpOptions(@Nullable List<DhcpOption> options) {
            mConfig.mDhcpOptions = options;
            return this;
        }

        /**
         * Specify that the configuration should enable IPv6 link-local only mode used for
         * WiFi Neighbor Aware Networking and other link-local-only technologies. It's not
         * used by default, and IPv4 must be disabled when this mode is enabled.
         *
         * @note This API is only supported since Android T.
         */
        public Builder withIpv6LinkLocalOnly() {
            mConfig.mIPv6ProvisioningMode = PROV_IPV6_LINKLOCAL;
            return this;
        }

        /**
         * Specify that the configuration is for a network that only uses unique EUI-64
         * addresses (e.g., a link-local-only network where addresses are generated via
         * EUI-64 and where MAC addresses are guaranteed to be unique).
         * This will disable duplicate address detection if withLinkLocalOnly() and
         * withRandomMacAddress are also called.
         */
        public Builder withUniqueEui64AddressesOnly() {
            mConfig.mUniqueEui64AddressesOnly = true;
            return this;
        }

        /**
         * Specify the hostname setting to use during IP provisioning.
         *     - {@link IIpClient#HOSTNAME_SETTING_UNSET}: Default value.
         *     - {@link IIpClient#HOSTNAME_SETTING_SEND}: Send the hostname.
         *     - {@link IIpClient#HOSTNAME_SETTING_DO_NOT_SEND}: Don't send the hostname.
         */
        public Builder withHostnameSetting(int setting) {
            mConfig.mHostnameSetting = setting;
            return this;
        }

        /**
         * Build the configuration using previously specified parameters.
         */
        public ProvisioningConfiguration build() {
            if (mConfig.mIPv6ProvisioningMode == PROV_IPV6_LINKLOCAL
                    && mConfig.mIPv4ProvisioningMode != PROV_IPV4_DISABLED) {
                throw new IllegalArgumentException("IPv4 must be disabled in IPv6 link-local"
                        + "only mode.");
            }
            return new ProvisioningConfiguration(mConfig);
        }
    }

    /**
     * Class wrapper of {@link android.net.wifi.ScanResult} to encapsulate the SSID and
     * InformationElements fields of ScanResult.
     */
    public static class ScanResultInfo {
        @NonNull
        private final String mSsid;
        @NonNull
        private final String mBssid;
        @NonNull
        private final List<InformationElement> mInformationElements;

       /**
        * Class wrapper of {@link android.net.wifi.ScanResult.InformationElement} to encapsulate
        * the specific IE id and payload fields.
        */
        public static class InformationElement {
            private final int mId;
            @NonNull
            private final byte[] mPayload;

            public InformationElement(int id, @NonNull ByteBuffer payload) {
                mId = id;
                mPayload = convertToByteArray(payload.asReadOnlyBuffer());
            }

           /**
            * Get the element ID of the information element.
            */
            public int getId() {
                return mId;
            }

           /**
            * Get the specific content of the information element.
            */
            @NonNull
            public ByteBuffer getPayload() {
                return ByteBuffer.wrap(mPayload).asReadOnlyBuffer();
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) return true;
                if (!(o instanceof InformationElement)) return false;
                InformationElement other = (InformationElement) o;
                return mId == other.mId && Arrays.equals(mPayload, other.mPayload);
            }

            @Override
            public int hashCode() {
                return Objects.hash(mId, Arrays.hashCode(mPayload));
            }

            @Override
            public String toString() {
                return "ID: " + mId + ", " + Arrays.toString(mPayload);
            }

            /**
             * Convert this InformationElement to a {@link InformationElementParcelable}.
             */
            public InformationElementParcelable toStableParcelable() {
                final InformationElementParcelable p = new InformationElementParcelable();
                p.id = mId;
                p.payload = mPayload != null ? mPayload.clone() : null;
                return p;
            }

            /**
             * Create an instance of {@link InformationElement} based on the contents of the
             * specified {@link InformationElementParcelable}.
             */
            @Nullable
            public static InformationElement fromStableParcelable(InformationElementParcelable p) {
                if (p == null) return null;
                return new InformationElement(p.id,
                        ByteBuffer.wrap(p.payload.clone()).asReadOnlyBuffer());
            }
        }

        public ScanResultInfo(@NonNull String ssid, @NonNull String bssid,
                @NonNull List<InformationElement> informationElements) {
            Objects.requireNonNull(ssid, "ssid must not be null.");
            Objects.requireNonNull(bssid, "bssid must not be null.");
            mSsid = ssid;
            mBssid = bssid;
            mInformationElements =
                    Collections.unmodifiableList(new ArrayList<>(informationElements));
        }

        /**
         * Get the scanned network name.
         */
        @NonNull
        public String getSsid() {
            return mSsid;
        }

        /**
         * Get the address of the access point.
         */
        @NonNull
        public String getBssid() {
            return mBssid;
        }

        /**
         * Get all information elements found in the beacon.
         */
        @NonNull
        public List<InformationElement> getInformationElements() {
            return mInformationElements;
        }

        @Override
        public String toString() {
            StringBuffer str = new StringBuffer();
            str.append("SSID: ").append(mSsid);
            str.append(", BSSID: ").append(mBssid);
            str.append(", Information Elements: {");
            for (InformationElement ie : mInformationElements) {
                str.append("[").append(ie.toString()).append("]");
            }
            str.append("}");
            return str.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof ScanResultInfo)) return false;
            ScanResultInfo other = (ScanResultInfo) o;
            return Objects.equals(mSsid, other.mSsid)
                    && Objects.equals(mBssid, other.mBssid)
                    && mInformationElements.equals(other.mInformationElements);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mSsid, mBssid, mInformationElements);
        }

        /**
         * Convert this ScanResultInfo to a {@link ScanResultInfoParcelable}.
         */
        public ScanResultInfoParcelable toStableParcelable() {
            final ScanResultInfoParcelable p = new ScanResultInfoParcelable();
            p.ssid = mSsid;
            p.bssid = mBssid;
            p.informationElements = toParcelableArray(mInformationElements,
                    InformationElement::toStableParcelable, InformationElementParcelable.class);
            return p;
        }

        /**
         * Create an instance of {@link ScanResultInfo} based on the contents of the specified
         * {@link ScanResultInfoParcelable}.
         */
        public static ScanResultInfo fromStableParcelable(ScanResultInfoParcelable p) {
            if (p == null) return null;
            final List<InformationElement> ies = new ArrayList<InformationElement>();
            ies.addAll(fromParcelableArray(p.informationElements,
                    InformationElement::fromStableParcelable));
            return new ScanResultInfo(p.ssid, p.bssid, ies);
        }

        private static byte[] convertToByteArray(@NonNull final ByteBuffer buffer) {
            final byte[] bytes = new byte[buffer.limit()];
            final ByteBuffer copy = buffer.asReadOnlyBuffer();
            try {
                copy.position(0);
                copy.get(bytes);
            } catch (BufferUnderflowException e) {
                Log.wtf(TAG, "Buffer under flow exception should never happen.");
            } finally {
                return bytes;
            }
        }
    }

    public boolean mUniqueEui64AddressesOnly = false;
    public boolean mEnablePreconnection = false;
    public boolean mUsingMultinetworkPolicyTracker = true;
    public boolean mUsingIpReachabilityMonitor = true;
    public int mRequestedPreDhcpActionMs;
    public InitialConfiguration mInitialConfig;
    public StaticIpConfiguration mStaticIpConfig;
    public ApfCapabilities mApfCapabilities;
    public int mProvisioningTimeoutMs = DEFAULT_TIMEOUT_MS;
    public int mIPv6AddrGenMode = IPV6_ADDR_GEN_MODE_STABLE_PRIVACY;
    public Network mNetwork = null;
    public String mDisplayName = null;
    public ScanResultInfo mScanResultInfo;
    public Layer2Information mLayer2Info;
    public List<DhcpOption> mDhcpOptions;
    public int mIPv4ProvisioningMode = PROV_IPV4_DHCP;
    public int mIPv6ProvisioningMode = PROV_IPV6_SLAAC;
    public int mCreatorUid;
    public int mHostnameSetting = HOSTNAME_SETTING_UNSET;

    public ProvisioningConfiguration() {} // used by Builder

    public ProvisioningConfiguration(ProvisioningConfiguration other) {
        mUniqueEui64AddressesOnly = other.mUniqueEui64AddressesOnly;
        mEnablePreconnection = other.mEnablePreconnection;
        mUsingMultinetworkPolicyTracker = other.mUsingMultinetworkPolicyTracker;
        mUsingIpReachabilityMonitor = other.mUsingIpReachabilityMonitor;
        mRequestedPreDhcpActionMs = other.mRequestedPreDhcpActionMs;
        mInitialConfig = InitialConfiguration.copy(other.mInitialConfig);
        mStaticIpConfig = other.mStaticIpConfig == null
                ? null
                : new StaticIpConfiguration(other.mStaticIpConfig);
        mApfCapabilities = other.mApfCapabilities;
        mProvisioningTimeoutMs = other.mProvisioningTimeoutMs;
        mIPv6AddrGenMode = other.mIPv6AddrGenMode;
        mNetwork = other.mNetwork;
        mDisplayName = other.mDisplayName;
        mCreatorUid = other.mCreatorUid;
        mScanResultInfo = other.mScanResultInfo;
        mLayer2Info = other.mLayer2Info;
        mDhcpOptions = other.mDhcpOptions;
        mIPv4ProvisioningMode = other.mIPv4ProvisioningMode;
        mIPv6ProvisioningMode = other.mIPv6ProvisioningMode;
        mHostnameSetting = other.mHostnameSetting;
    }

    /**
     * Create a ProvisioningConfigurationParcelable from this ProvisioningConfiguration.
     */
    public ProvisioningConfigurationParcelable toStableParcelable() {
        final ProvisioningConfigurationParcelable p = new ProvisioningConfigurationParcelable();
        p.enableIPv4 = (mIPv4ProvisioningMode != PROV_IPV4_DISABLED);
        p.ipv4ProvisioningMode = mIPv4ProvisioningMode;
        p.enableIPv6 = (mIPv6ProvisioningMode != PROV_IPV6_DISABLED);
        p.ipv6ProvisioningMode = mIPv6ProvisioningMode;
        p.uniqueEui64AddressesOnly = mUniqueEui64AddressesOnly;
        p.enablePreconnection = mEnablePreconnection;
        p.usingMultinetworkPolicyTracker = mUsingMultinetworkPolicyTracker;
        p.usingIpReachabilityMonitor = mUsingIpReachabilityMonitor;
        p.requestedPreDhcpActionMs = mRequestedPreDhcpActionMs;
        p.initialConfig = (mInitialConfig == null) ? null : mInitialConfig.toStableParcelable();
        p.staticIpConfig = (mStaticIpConfig == null)
                ? null
                : new StaticIpConfiguration(mStaticIpConfig);
        p.apfCapabilities = mApfCapabilities; // ApfCapabilities is immutable
        p.provisioningTimeoutMs = mProvisioningTimeoutMs;
        p.ipv6AddrGenMode = mIPv6AddrGenMode;
        p.network = mNetwork;
        p.displayName = mDisplayName;
        p.creatorUid = mCreatorUid;
        p.scanResultInfo = (mScanResultInfo == null) ? null : mScanResultInfo.toStableParcelable();
        p.layer2Info = (mLayer2Info == null) ? null : mLayer2Info.toStableParcelable();
        p.options = (mDhcpOptions == null) ? null : new ArrayList<>(mDhcpOptions);
        p.hostnameSetting = mHostnameSetting;
        return p;
    }

    /**
     * Create a ProvisioningConfiguration from a ProvisioningConfigurationParcelable.
     *
     * @param p stable parcelable instance to be converted to a {@link ProvisioningConfiguration}.
     * @param interfaceVersion IIpClientCallbacks interface version called by the remote peer,
     *                         which is used to determine the appropriate parcelable members for
     *                         backwards compatibility.
     */
    public static ProvisioningConfiguration fromStableParcelable(
            @Nullable ProvisioningConfigurationParcelable p, int interfaceVersion) {
        if (p == null) return null;
        final ProvisioningConfiguration config = new ProvisioningConfiguration();
        config.mUniqueEui64AddressesOnly = p.uniqueEui64AddressesOnly;
        config.mEnablePreconnection = p.enablePreconnection;
        config.mUsingMultinetworkPolicyTracker = p.usingMultinetworkPolicyTracker;
        config.mUsingIpReachabilityMonitor = p.usingIpReachabilityMonitor;
        config.mRequestedPreDhcpActionMs = p.requestedPreDhcpActionMs;
        config.mInitialConfig = InitialConfiguration.fromStableParcelable(p.initialConfig);
        config.mStaticIpConfig = (p.staticIpConfig == null)
                ? null
                : new StaticIpConfiguration(p.staticIpConfig);
        config.mApfCapabilities = p.apfCapabilities; // ApfCapabilities is immutable
        config.mProvisioningTimeoutMs = p.provisioningTimeoutMs;
        config.mIPv6AddrGenMode = p.ipv6AddrGenMode;
        config.mNetwork = p.network;
        config.mDisplayName = p.displayName;
        config.mCreatorUid = p.creatorUid;
        config.mScanResultInfo = ScanResultInfo.fromStableParcelable(p.scanResultInfo);
        config.mLayer2Info = Layer2Information.fromStableParcelable(p.layer2Info);
        config.mDhcpOptions = (p.options == null) ? null : new ArrayList<>(p.options);
        if (interfaceVersion < VERSION_ADDED_PROVISIONING_ENUM) {
            config.mIPv4ProvisioningMode = p.enableIPv4 ? PROV_IPV4_DHCP : PROV_IPV4_DISABLED;
            config.mIPv6ProvisioningMode = p.enableIPv6 ? PROV_IPV6_SLAAC : PROV_IPV6_DISABLED;
        } else {
            config.mIPv4ProvisioningMode = p.ipv4ProvisioningMode;
            config.mIPv6ProvisioningMode = p.ipv6ProvisioningMode;
        }
        config.mHostnameSetting = p.hostnameSetting;
        return config;
    }

    @VisibleForTesting
    static String ipv4ProvisioningModeToString(int mode) {
        switch (mode) {
            case PROV_IPV4_DISABLED:
                return "disabled";
            case PROV_IPV4_STATIC:
                return "static";
            case PROV_IPV4_DHCP:
                return "dhcp";
            default:
                return "unknown";
        }
    }

    @VisibleForTesting
    static String ipv6ProvisioningModeToString(int mode) {
        switch (mode) {
            case PROV_IPV6_DISABLED:
                return "disabled";
            case PROV_IPV6_SLAAC:
                return "slaac";
            case PROV_IPV6_LINKLOCAL:
                return "link-local";
            default:
                return "unknown";
        }
    }

    @Override
    public String toString() {
        final String ipv4ProvisioningMode = ipv4ProvisioningModeToString(mIPv4ProvisioningMode);
        final String ipv6ProvisioningMode = ipv6ProvisioningModeToString(mIPv6ProvisioningMode);
        return new StringJoiner(", ", getClass().getSimpleName() + "{", "}")
                .add("mUniqueEui64AddressesOnly: " + mUniqueEui64AddressesOnly)
                .add("mEnablePreconnection: " + mEnablePreconnection)
                .add("mUsingMultinetworkPolicyTracker: " + mUsingMultinetworkPolicyTracker)
                .add("mUsingIpReachabilityMonitor: " + mUsingIpReachabilityMonitor)
                .add("mRequestedPreDhcpActionMs: " + mRequestedPreDhcpActionMs)
                .add("mInitialConfig: " + mInitialConfig)
                .add("mStaticIpConfig: " + mStaticIpConfig)
                .add("mApfCapabilities: " + mApfCapabilities)
                .add("mProvisioningTimeoutMs: " + mProvisioningTimeoutMs)
                .add("mIPv6AddrGenMode: " + mIPv6AddrGenMode)
                .add("mNetwork: " + mNetwork)
                .add("mDisplayName: " + mDisplayName)
                .add("mCreatorUid:" + mCreatorUid)
                .add("mScanResultInfo: " + mScanResultInfo)
                .add("mLayer2Info: " + mLayer2Info)
                .add("mDhcpOptions: " + mDhcpOptions)
                .add("mIPv4ProvisioningMode: " + ipv4ProvisioningMode)
                .add("mIPv6ProvisioningMode: " + ipv6ProvisioningMode)
                .add("mHostnameSetting: " + mHostnameSetting)
                .toString();
    }

    // TODO: mark DhcpOption stable parcelable with @JavaDerive(equals=true, toString=true)
    // and @JavaOnlyImmutable.
    private static boolean dhcpOptionEquals(@Nullable DhcpOption obj1, @Nullable DhcpOption obj2) {
        if (obj1 == obj2) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.type == obj2.type && Arrays.equals(obj1.value, obj2.value);
    }

    // TODO: use Objects.equals(List<DhcpOption>, List<DhcpOption>) method instead once
    // auto-generated equals() method of stable parcelable is supported in mainline-prod.
    private static boolean dhcpOptionListEquals(@Nullable List<DhcpOption> l1,
            @Nullable List<DhcpOption> l2) {
        if (l1 == l2) return true;
        if (l1 == null || l2 == null) return false;
        if (l1.size() != l2.size()) return false;

        for (int i = 0; i < l1.size(); i++) {
            if (!dhcpOptionEquals(l1.get(i), l2.get(i))) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProvisioningConfiguration)) return false;
        final ProvisioningConfiguration other = (ProvisioningConfiguration) obj;
        return mUniqueEui64AddressesOnly == other.mUniqueEui64AddressesOnly
                && mEnablePreconnection == other.mEnablePreconnection
                && mUsingMultinetworkPolicyTracker == other.mUsingMultinetworkPolicyTracker
                && mUsingIpReachabilityMonitor == other.mUsingIpReachabilityMonitor
                && mRequestedPreDhcpActionMs == other.mRequestedPreDhcpActionMs
                && Objects.equals(mInitialConfig, other.mInitialConfig)
                && Objects.equals(mStaticIpConfig, other.mStaticIpConfig)
                && Objects.equals(mApfCapabilities, other.mApfCapabilities)
                && mProvisioningTimeoutMs == other.mProvisioningTimeoutMs
                && mIPv6AddrGenMode == other.mIPv6AddrGenMode
                && Objects.equals(mNetwork, other.mNetwork)
                && Objects.equals(mDisplayName, other.mDisplayName)
                && Objects.equals(mScanResultInfo, other.mScanResultInfo)
                && Objects.equals(mLayer2Info, other.mLayer2Info)
                && dhcpOptionListEquals(mDhcpOptions, other.mDhcpOptions)
                && mIPv4ProvisioningMode == other.mIPv4ProvisioningMode
                && mIPv6ProvisioningMode == other.mIPv6ProvisioningMode
                && mCreatorUid == other.mCreatorUid
                && mHostnameSetting == other.mHostnameSetting;
    }

    public boolean isValid() {
        return (mInitialConfig == null) || mInitialConfig.isValid();
    }
}
