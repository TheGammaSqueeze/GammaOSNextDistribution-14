/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.connectivity.mdns.internal;

import android.annotation.NonNull;
import android.net.LinkAddress;
import android.os.Handler;
import android.system.OsConstants;

import com.android.net.module.util.SharedLog;
import com.android.net.module.util.ip.NetlinkMonitor;
import com.android.net.module.util.netlink.NetlinkConstants;
import com.android.net.module.util.netlink.NetlinkMessage;
import com.android.net.module.util.netlink.RtNetlinkAddressMessage;
import com.android.net.module.util.netlink.StructIfaddrMsg;
import com.android.server.connectivity.mdns.AbstractSocketNetlinkMonitor;
import com.android.server.connectivity.mdns.MdnsSocketProvider;

/**
 * The netlink monitor for MdnsSocketProvider.
 */
public class SocketNetlinkMonitor extends NetlinkMonitor implements AbstractSocketNetlinkMonitor {

    public static final String TAG = SocketNetlinkMonitor.class.getSimpleName();
    @NonNull
    private final SharedLog mSharedLog;

    @NonNull
    private final MdnsSocketProvider.NetLinkMonitorCallBack mCb;
    public SocketNetlinkMonitor(@NonNull final Handler handler,
            @NonNull SharedLog log,
            @NonNull final MdnsSocketProvider.NetLinkMonitorCallBack cb) {
        super(handler, log, TAG, OsConstants.NETLINK_ROUTE,
                NetlinkConstants.RTMGRP_IPV4_IFADDR | NetlinkConstants.RTMGRP_IPV6_IFADDR);
        mCb = cb;
        mSharedLog = log;
    }
    @Override
    public void processNetlinkMessage(NetlinkMessage nlMsg, long whenMs) {
        if (nlMsg instanceof RtNetlinkAddressMessage) {
            processRtNetlinkAddressMessage((RtNetlinkAddressMessage) nlMsg);
        }
    }

    /**
     * Process the RTM_NEWADDR and RTM_DELADDR netlink message.
     */
    private void processRtNetlinkAddressMessage(RtNetlinkAddressMessage msg) {
        final StructIfaddrMsg ifaddrMsg = msg.getIfaddrHeader();
        final LinkAddress la = new LinkAddress(msg.getIpAddress(), ifaddrMsg.prefixLen,
                msg.getFlags(), ifaddrMsg.scope);
        switch (msg.getHeader().nlmsg_type) {
            case NetlinkConstants.RTM_NEWADDR:
                if (la.isPreferred()) {
                    mCb.addOrUpdateInterfaceAddress(ifaddrMsg.index, la);
                }
                break;
            case NetlinkConstants.RTM_DELADDR:
                mCb.deleteInterfaceAddress(ifaddrMsg.index, la);
                break;
            default:
                mSharedLog.e("Unknown rtnetlink address msg type " + msg.getHeader().nlmsg_type);
        }
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public void startMonitoring() {
        this.start();
    }

    @Override
    public void stopMonitoring() {
        this.stop();
    }
}
