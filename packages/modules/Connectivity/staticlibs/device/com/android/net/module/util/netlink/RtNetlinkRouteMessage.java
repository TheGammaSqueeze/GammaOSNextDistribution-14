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

package com.android.net.module.util.netlink;

import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;

import static com.android.net.module.util.NetworkStackConstants.IPV4_ADDR_ANY;
import static com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_ANY;
import static com.android.net.module.util.netlink.NetlinkConstants.RTNL_FAMILY_IP6MR;

import android.annotation.SuppressLint;
import android.net.IpPrefix;
import android.net.RouteInfo;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * A NetlinkMessage subclass for rtnetlink route messages.
 *
 * RtNetlinkRouteMessage.parse() must be called with a ByteBuffer that contains exactly one
 * netlink message.
 *
 * see also:
 *
 *     include/uapi/linux/rtnetlink.h
 *
 * @hide
 */
public class RtNetlinkRouteMessage extends NetlinkMessage {
    public static final short RTA_DST           = 1;
    public static final short RTA_SRC           = 2;
    public static final short RTA_IIF           = 3;
    public static final short RTA_OIF           = 4;
    public static final short RTA_GATEWAY       = 5;
    public static final short RTA_CACHEINFO     = 12;
    public static final short RTA_EXPIRES       = 23;

    public static final short RTNH_F_UNRESOLVED = 32;   // The multicast route is unresolved

    public static final String TAG = "NetlinkRouteMessage";

    // For multicast routes, whether the route is resolved or unresolved
    private boolean mIsResolved;
    // The interface index for incoming interface, this is set for multicast
    // routes, see common/net/ipv4/ipmr_base.c mr_fill_mroute
    private int mIifIndex; // Incoming interface of a route, for resolved multicast routes
    private int mOifIndex;
    @NonNull
    private StructRtMsg mRtmsg;
    @Nullable
    private IpPrefix mSource; // Source address of a route, for all multicast routes
    @Nullable
    private IpPrefix mDestination; // Destination of a route, can be null for RTM_GETROUTE
    @Nullable
    private InetAddress mGateway;
    @Nullable
    private StructRtaCacheInfo mRtaCacheInfo;
    private long mSinceLastUseMillis; // Milliseconds since the route was used,
                                      // for resolved multicast routes


    @VisibleForTesting
    public RtNetlinkRouteMessage(final StructNlMsgHdr header, final StructRtMsg rtMsg,
            final IpPrefix source, final IpPrefix destination, final InetAddress gateway,
            int iif, int oif, final StructRtaCacheInfo cacheInfo) {
        super(header);
        mRtmsg = rtMsg;
        mSource = source;
        mDestination = destination;
        mGateway = gateway;
        mIifIndex = iif;
        mOifIndex = oif;
        mRtaCacheInfo = cacheInfo;
        mSinceLastUseMillis = -1;
    }

    public RtNetlinkRouteMessage(StructNlMsgHdr header, StructRtMsg rtMsg) {
        this(header, rtMsg, null /* source */, null /* destination */, null /* gateway */,
                0 /* iif */, 0 /* oif */, null /* cacheInfo */);
    }

    /**
     * Returns the rtnetlink family.
     */
    public short getRtmFamily() {
        return mRtmsg.family;
    }

    /**
     * Returns if the route is resolved. This is always true for unicast,
     * and may be false only for multicast routes.
     */
    public boolean isResolved() {
        return mIsResolved;
    }

    public int getIifIndex() {
        return mIifIndex;
    }

    public int getInterfaceIndex() {
        return mOifIndex;
    }

    @NonNull
    public StructRtMsg getRtMsgHeader() {
        return mRtmsg;
    }

    @NonNull
    public IpPrefix getDestination() {
        return mDestination;
    }

    /**
     * Get source address of a route. This is for multicast routes.
     */
    @NonNull
    public IpPrefix getSource() {
        return mSource;
    }

    @Nullable
    public InetAddress getGateway() {
        return mGateway;
    }

    @Nullable
    public StructRtaCacheInfo getRtaCacheInfo() {
        return mRtaCacheInfo;
    }

    /**
     * RTA_EXPIRES attribute returned by kernel to indicate the clock ticks
     * from the route was last used to now, converted to milliseconds.
     * This is set for multicast routes.
     *
     * Note that this value is not updated with the passage of time. It always
     * returns the value that was read when the netlink message was parsed.
     */
    public long getSinceLastUseMillis() {
        return mSinceLastUseMillis;
    }

    /**
     * Check whether the address families of destination and gateway match rtm_family in
     * StructRtmsg.
     *
     * For example, IPv4-mapped IPv6 addresses as an IPv6 address will be always converted to IPv4
     * address, that's incorrect when upper layer creates a new {@link RouteInfo} class instance
     * for IPv6 route with the converted IPv4 gateway.
     */
    private static boolean matchRouteAddressFamily(@NonNull final InetAddress address,
            int family) {
        return ((address instanceof Inet4Address) && (family == AF_INET))
                || ((address instanceof Inet6Address) &&
                        (family == AF_INET6 || family == RTNL_FAMILY_IP6MR));
    }

    /**
     * Parse rtnetlink route message from {@link ByteBuffer}. This method must be called with a
     * ByteBuffer that contains exactly one netlink message.
     *
     * @param header netlink message header.
     * @param byteBuffer the ByteBuffer instance that wraps the raw netlink message bytes.
     */
    @SuppressLint("NewApi")
    @Nullable
    public static RtNetlinkRouteMessage parse(@NonNull final StructNlMsgHdr header,
            @NonNull final ByteBuffer byteBuffer) {
        final StructRtMsg rtmsg = StructRtMsg.parse(byteBuffer);
        if (rtmsg == null) return null;
        final RtNetlinkRouteMessage routeMsg = new RtNetlinkRouteMessage(header, rtmsg);
        int rtmFamily = routeMsg.mRtmsg.family;
        routeMsg.mIsResolved = ((routeMsg.mRtmsg.flags & RTNH_F_UNRESOLVED) == 0);

        // RTA_DST
        final int baseOffset = byteBuffer.position();
        StructNlAttr nlAttr = StructNlAttr.findNextAttrOfType(RTA_DST, byteBuffer);
        if (nlAttr != null) {
            final InetAddress destination = nlAttr.getValueAsInetAddress();
            // If the RTA_DST attribute is malformed, return null.
            if (destination == null) return null;
            // If the address family of destination doesn't match rtm_family, return null.
            if (!matchRouteAddressFamily(destination, rtmFamily)) return null;
            routeMsg.mDestination = new IpPrefix(destination, routeMsg.mRtmsg.dstLen);
        } else if (rtmFamily == AF_INET) {
            routeMsg.mDestination = new IpPrefix(IPV4_ADDR_ANY, 0);
        } else if (rtmFamily == AF_INET6 || rtmFamily == RTNL_FAMILY_IP6MR) {
            routeMsg.mDestination = new IpPrefix(IPV6_ADDR_ANY, 0);
        } else {
            return null;
        }

        // RTA_SRC
        byteBuffer.position(baseOffset);
        nlAttr = StructNlAttr.findNextAttrOfType(RTA_SRC, byteBuffer);
        if (nlAttr != null) {
            final InetAddress source = nlAttr.getValueAsInetAddress();
            // If the RTA_SRC attribute is malformed, return null.
            if (source == null) return null;
            // If the address family of destination doesn't match rtm_family, return null.
            if (!matchRouteAddressFamily(source, rtmFamily)) return null;
            routeMsg.mSource = new IpPrefix(source, routeMsg.mRtmsg.srcLen);
        }

        // RTA_GATEWAY
        byteBuffer.position(baseOffset);
        nlAttr = StructNlAttr.findNextAttrOfType(RTA_GATEWAY, byteBuffer);
        if (nlAttr != null) {
            routeMsg.mGateway = nlAttr.getValueAsInetAddress();
            // If the RTA_GATEWAY attribute is malformed, return null.
            if (routeMsg.mGateway == null) return null;
            // If the address family of gateway doesn't match rtm_family, return null.
            if (!matchRouteAddressFamily(routeMsg.mGateway, rtmFamily)) return null;
        }

        // RTA_IIF
        byteBuffer.position(baseOffset);
        nlAttr = StructNlAttr.findNextAttrOfType(RTA_IIF, byteBuffer);
        if (nlAttr != null) {
            Integer iifInteger = nlAttr.getValueAsInteger();
            if (iifInteger == null) {
                return null;
            }
            routeMsg.mIifIndex = iifInteger;
        }

        // RTA_OIF
        byteBuffer.position(baseOffset);
        nlAttr = StructNlAttr.findNextAttrOfType(RTA_OIF, byteBuffer);
        if (nlAttr != null) {
            // Any callers that deal with interface names are responsible for converting
            // the interface index to a name themselves. This may not succeed or may be
            // incorrect, because the interface might have been deleted, or even deleted
            // and re-added with a different index, since the netlink message was sent.
            routeMsg.mOifIndex = nlAttr.getValueAsInt(0 /* 0 isn't a valid ifindex */);
        }

        // RTA_CACHEINFO
        byteBuffer.position(baseOffset);
        nlAttr = StructNlAttr.findNextAttrOfType(RTA_CACHEINFO, byteBuffer);
        if (nlAttr != null) {
            routeMsg.mRtaCacheInfo = StructRtaCacheInfo.parse(nlAttr.getValueAsByteBuffer());
        }

        // RTA_EXPIRES
        byteBuffer.position(baseOffset);
        nlAttr = StructNlAttr.findNextAttrOfType(RTA_EXPIRES, byteBuffer);
        if (nlAttr != null) {
            final Long sinceLastUseCentis = nlAttr.getValueAsLong();
            // If the RTA_EXPIRES attribute is malformed, return null.
            if (sinceLastUseCentis == null) return null;
            // RTA_EXPIRES returns time in clock ticks of USER_HZ(100), which is centiseconds
            routeMsg.mSinceLastUseMillis = sinceLastUseCentis * 10;
        }

        return routeMsg;
    }

    /**
     * Write a rtnetlink address message to {@link ByteBuffer}.
     */
    public void pack(ByteBuffer byteBuffer) {
        getHeader().pack(byteBuffer);
        mRtmsg.pack(byteBuffer);

        if (mSource != null) {
            final StructNlAttr source = new StructNlAttr(RTA_SRC, mSource.getAddress());
            source.pack(byteBuffer);
        }

        if (mDestination != null) {
            final StructNlAttr destination = new StructNlAttr(RTA_DST, mDestination.getAddress());
            destination.pack(byteBuffer);
        }

        if (mGateway != null) {
            final StructNlAttr gateway = new StructNlAttr(RTA_GATEWAY, mGateway.getAddress());
            gateway.pack(byteBuffer);
        }
        if (mIifIndex != 0) {
            final StructNlAttr iifindex = new StructNlAttr(RTA_IIF, mIifIndex);
            iifindex.pack(byteBuffer);
        }
        if (mOifIndex != 0) {
            final StructNlAttr oifindex = new StructNlAttr(RTA_OIF, mOifIndex);
            oifindex.pack(byteBuffer);
        }
        if (mRtaCacheInfo != null) {
            final StructNlAttr cacheInfo = new StructNlAttr(RTA_CACHEINFO,
                    mRtaCacheInfo.writeToBytes());
            cacheInfo.pack(byteBuffer);
        }
        if (mSinceLastUseMillis >= 0) {
            final long sinceLastUseCentis = mSinceLastUseMillis / 10;
            final StructNlAttr expires = new StructNlAttr(RTA_EXPIRES, sinceLastUseCentis);
            expires.pack(byteBuffer);
        }
    }

    @Override
    public String toString() {
        return "RtNetlinkRouteMessage{ "
                + "nlmsghdr{" + mHeader.toString(OsConstants.NETLINK_ROUTE) + "}, "
                + "Rtmsg{" + mRtmsg.toString() + "}, "
                + (mSource == null ? "" : "source{" + mSource.getAddress().getHostAddress() + "}, ")
                + (mDestination == null ?
                        "" : "destination{" + mDestination.getAddress().getHostAddress() + "}, ")
                + "gateway{" + (mGateway == null ? "" : mGateway.getHostAddress()) + "}, "
                + (mIifIndex == 0 ? "" : "iifindex{" + mIifIndex + "}, ")
                + "oifindex{" + mOifIndex + "}, "
                + "rta_cacheinfo{" + (mRtaCacheInfo == null ? "" : mRtaCacheInfo.toString()) + "} "
                + (mSinceLastUseMillis < 0 ? "" : "sinceLastUseMillis{" + mSinceLastUseMillis + "}")
                + "}";
    }
}
