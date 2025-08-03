/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.net;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import com.android.net.module.util.InetAddressUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A simple object for retrieving the results of a DHCP request.
 * Optimized (attempted) for that jni interface
 * TODO: remove this class and replace with other existing constructs
 * @hide
 */
public final class DhcpResults implements Parcelable {
    private static final String TAG = "DhcpResults";

    public LinkAddress ipAddress;

    public InetAddress gateway;

    public final ArrayList<InetAddress> dnsServers = new ArrayList<>();

    public String domains;

    public Inet4Address serverAddress;

    /** Vendor specific information (from RFC 2132). */
    public String vendorInfo;

    public int leaseDuration;

    /** Link MTU option. 0 means unset. */
    public int mtu;

    public String serverHostName;

    @Nullable
    public String captivePortalApiUrl;

    public ArrayList<String> dmnsrchList = new ArrayList<>();

    public DhcpResults() {
        super();
    }

    /**
     * Create a {@link StaticIpConfiguration} based on the DhcpResults.
     */
    @SuppressLint("NewApi") // TODO: b/193460475 remove once fixed
    public StaticIpConfiguration toStaticIpConfiguration() {
        return new StaticIpConfiguration.Builder()
                .setIpAddress(ipAddress)
                .setGateway(gateway)
                .setDnsServers(dnsServers)
                .setDomains(domains)
                .build();
    }

    @SuppressLint("NewApi") // TODO: b/193460475 remove once fixed
    public DhcpResults(StaticIpConfiguration source) {
        if (source != null) {
            ipAddress = source.getIpAddress();
            gateway = source.getGateway();
            dnsServers.addAll(source.getDnsServers());
            domains = source.getDomains();
        }
    }

    /** copy constructor */
    public DhcpResults(DhcpResults source) {
        this(source == null ? null : source.toStaticIpConfiguration());
        if (source != null) {
            serverAddress = source.serverAddress;
            vendorInfo = source.vendorInfo;
            leaseDuration = source.leaseDuration;
            mtu = source.mtu;
            serverHostName = source.serverHostName;
            captivePortalApiUrl = source.captivePortalApiUrl;
            dmnsrchList = source.dmnsrchList;
        }
    }

    /**
     * @see StaticIpConfiguration#getRoutes(String)
     * @hide
     */
    public List<RouteInfo> getRoutes(String iface) {
        return toStaticIpConfiguration().getRoutes(iface);
    }

    /**
     * Test if this DHCP lease includes vendor hint that network link is
     * metered, and sensitive to heavy data transfers.
     */
    public boolean hasMeteredHint() {
        if (vendorInfo != null) {
            return vendorInfo.contains("ANDROID_METERED");
        } else {
            return false;
        }
    }

    /** Clears all data and resets this object to its initial state. */
    public void clear() {
        ipAddress = null;
        gateway = null;
        dnsServers.clear();
        domains = null;
        serverAddress = null;
        vendorInfo = null;
        leaseDuration = 0;
        mtu = 0;
        serverHostName = null;
        captivePortalApiUrl = null;
        dmnsrchList.clear();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder(super.toString());

        str.append(" DHCP server ").append(serverAddress);
        str.append(" Vendor info ").append(vendorInfo);
        str.append(" lease ").append(leaseDuration).append(" seconds");
        if (mtu != 0) str.append(" MTU ").append(mtu);
        str.append(" Servername ").append(serverHostName);
        if (captivePortalApiUrl != null) {
            str.append(" CaptivePortalApiUrl ").append(captivePortalApiUrl);
        }

        return str.toString();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;

        if (!(obj instanceof DhcpResults)) return false;

        DhcpResults target = (DhcpResults) obj;

        return toStaticIpConfiguration().equals(target.toStaticIpConfiguration())
                && Objects.equals(serverAddress, target.serverAddress)
                && Objects.equals(vendorInfo, target.vendorInfo)
                && Objects.equals(serverHostName, target.serverHostName)
                && leaseDuration == target.leaseDuration
                && mtu == target.mtu
                && Objects.equals(captivePortalApiUrl, target.captivePortalApiUrl)
                && dmnsrchList.equals(target.dmnsrchList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ipAddress, gateway, dnsServers, domains, serverAddress, vendorInfo,
            serverHostName, captivePortalApiUrl, dmnsrchList) + 43 *  leaseDuration + 67 * mtu;
    }

    /**
     * Implement the Parcelable interface
     */
    @SuppressLint("NewApi") // TODO: b/193460475 remove once fixed
    public static final @android.annotation.NonNull Creator<DhcpResults> CREATOR =
            new Creator<DhcpResults>() {
                public DhcpResults createFromParcel(Parcel in) {
                    return readFromParcel(in);
                }

                public DhcpResults[] newArray(int size) {
                    return new DhcpResults[size];
                }
            };

    /** Implement the Parcelable interface */
    public void writeToParcel(Parcel dest, int flags) {
        toStaticIpConfiguration().writeToParcel(dest, flags);
        dest.writeInt(leaseDuration);
        dest.writeInt(mtu);
        InetAddressUtils.parcelInetAddress(dest, serverAddress, flags);
        dest.writeString(vendorInfo);
        dest.writeString(serverHostName);
        dest.writeString(captivePortalApiUrl);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @SuppressLint("NewApi") // TODO: b/193460475 remove once fixed
    private static DhcpResults readFromParcel(Parcel in) {
        final StaticIpConfiguration s = StaticIpConfiguration.CREATOR.createFromParcel(in);
        final DhcpResults dhcpResults = new DhcpResults(s);
        dhcpResults.leaseDuration = in.readInt();
        dhcpResults.mtu = in.readInt();
        dhcpResults.serverAddress = (Inet4Address) InetAddressUtils.unparcelInetAddress(in);
        dhcpResults.vendorInfo = in.readString();
        dhcpResults.serverHostName = in.readString();
        dhcpResults.captivePortalApiUrl = in.readString();
        return dhcpResults;
    }

    /**
     * Sets the IPv4 address.
     *
     * @param addrString the string representation of the IPv4 address
     * @param prefixLength the prefix length.
     * @return false on success, true on failure
     */
    public boolean setIpAddress(String addrString, int prefixLength) {
        try {
            Inet4Address addr = (Inet4Address) InetAddresses.parseNumericAddress(addrString);
            ipAddress = new LinkAddress(addr, prefixLength);
        } catch (IllegalArgumentException | ClassCastException e) {
            Log.e(TAG, "setIpAddress failed with addrString " + addrString + "/" + prefixLength);
            return true;
        }
        return false;
    }

    /**
     * Sets the gateway IPv4 address.
     *
     * @param addrString the string representation of the gateway IPv4 address
     * @return false on success, true on failure
     */
    public boolean setGateway(String addrString) {
        try {
            gateway = InetAddresses.parseNumericAddress(addrString);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "setGateway failed with addrString " + addrString);
            return true;
        }
        return false;
    }

    /**
     * Adds a DNS server to the list.
     *
     * @param addrString the string representation of the DNS server IPv4 address
     * @return false on success, true on failure
     */
    public boolean addDns(String addrString) {
        if (TextUtils.isEmpty(addrString)) return false;
        try {
            dnsServers.add(InetAddresses.parseNumericAddress(addrString));
            return false;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "addDns failed with addrString " + addrString);
            return true;
        }
    }

    public LinkAddress getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(LinkAddress ipAddress) {
        this.ipAddress = ipAddress;
    }

    public InetAddress getGateway() {
        return gateway;
    }

    public void setGateway(InetAddress gateway) {
        this.gateway = gateway;
    }

    public List<InetAddress> getDnsServers() {
        return dnsServers;
    }

    /**
     * Add a DNS server to this configuration.
     */
    public void addDnsServer(InetAddress server) {
        dnsServers.add(server);
    }

    public String getDomains() {
        return domains;
    }

    /**
     * Append the domain search list strings separated by space to domain string.
     */
    public String appendDomainsSearchList() {
        final String domainsPrefix = domains == null ? "" : domains;
        final String separator = domains != null && dmnsrchList.size() > 0 ? " " : "";
        return domainsPrefix + separator + TextUtils.join(" ", dmnsrchList);
    }

    public void setDomains(String domains) {
        this.domains = domains;
    }

    public Inet4Address getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(Inet4Address addr) {
        serverAddress = addr;
    }

    public int getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(int duration) {
        leaseDuration = duration;
    }

    public String getVendorInfo() {
        return vendorInfo;
    }

    public void setVendorInfo(String info) {
        vendorInfo = info;
    }

    public int getMtu() {
        return mtu;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public String getCaptivePortalApiUrl() {
        return captivePortalApiUrl;
    }

    public void setCaptivePortalApiUrl(String url) {
        captivePortalApiUrl = url;
    }
}
