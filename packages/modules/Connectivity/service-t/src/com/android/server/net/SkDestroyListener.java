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

package com.android.server.net;

import static android.system.OsConstants.NETLINK_INET_DIAG;

import android.os.Handler;
import android.system.ErrnoException;

import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.bpf.CookieTagMapKey;
import com.android.net.module.util.bpf.CookieTagMapValue;
import com.android.net.module.util.ip.NetlinkMonitor;
import com.android.net.module.util.netlink.InetDiagMessage;
import com.android.net.module.util.netlink.NetlinkMessage;
import com.android.net.module.util.netlink.StructInetDiagSockId;

import java.io.PrintWriter;

/**
 * Monitor socket destroy and delete entry from cookie tag bpf map.
 */
public class SkDestroyListener extends NetlinkMonitor {
    private static final int SKNLGRP_INET_TCP_DESTROY = 1;
    private static final int SKNLGRP_INET_UDP_DESTROY = 2;
    private static final int SKNLGRP_INET6_TCP_DESTROY = 3;
    private static final int SKNLGRP_INET6_UDP_DESTROY = 4;

    // TODO: if too many sockets are closed too quickly, this can overflow the socket buffer, and
    // some entries in mCookieTagMap will not be freed. In order to fix this it would be needed to
    // periodically dump all sockets and remove the tag entries for sockets that have been closed.
    // For now, set a large-enough buffer that hundreds of sockets can be closed without getting
    // ENOBUFS and leaking mCookieTagMap entries.
    private static final int SOCK_RCV_BUF_SIZE = 512 * 1024;

    private final IBpfMap<CookieTagMapKey, CookieTagMapValue> mCookieTagMap;

    SkDestroyListener(final IBpfMap<CookieTagMapKey, CookieTagMapValue> cookieTagMap,
            final Handler handler, final SharedLog log) {
        super(handler, log, "SkDestroyListener", NETLINK_INET_DIAG,
                1 << (SKNLGRP_INET_TCP_DESTROY - 1)
                        | 1 << (SKNLGRP_INET_UDP_DESTROY - 1)
                        | 1 << (SKNLGRP_INET6_TCP_DESTROY - 1)
                        | 1 << (SKNLGRP_INET6_UDP_DESTROY - 1),
                SOCK_RCV_BUF_SIZE);
        mCookieTagMap = cookieTagMap;
    }

    @Override
    public void processNetlinkMessage(final NetlinkMessage nlMsg, final long whenMs) {
        if (!(nlMsg instanceof InetDiagMessage)) {
            mLog.e("Received non InetDiagMessage");
            return;
        }
        final StructInetDiagSockId sockId = ((InetDiagMessage) nlMsg).inetDiagMsg.id;
        try {
            mCookieTagMap.deleteEntry(new CookieTagMapKey(sockId.cookie));
        } catch (ErrnoException e) {
            mLog.e("Failed to delete CookieTagMap entry for " + sockId.cookie  + ": " + e);
        }
    }

    /**
     * Dump the contents of SkDestroyListener log.
     */
    public void dump(PrintWriter pw) {
        mLog.reverseDump(pw);
    }
}
