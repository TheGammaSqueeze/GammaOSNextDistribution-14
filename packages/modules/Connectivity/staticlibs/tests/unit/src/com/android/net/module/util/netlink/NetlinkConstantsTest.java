/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.system.OsConstants.NETLINK_INET_DIAG;
import static android.system.OsConstants.NETLINK_NETFILTER;
import static android.system.OsConstants.NETLINK_ROUTE;

import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_DELETE;
import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_GET;
import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_GET_CTRZERO;
import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_GET_DYING;
import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_GET_STATS;
import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_GET_STATS_CPU;
import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_GET_UNCONFIRMED;
import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_NEW;
import static com.android.net.module.util.netlink.NetlinkConstants.NFNL_SUBSYS_CTNETLINK;
import static com.android.net.module.util.netlink.NetlinkConstants.NLMSG_DONE;
import static com.android.net.module.util.netlink.NetlinkConstants.NLMSG_ERROR;
import static com.android.net.module.util.netlink.NetlinkConstants.NLMSG_NOOP;
import static com.android.net.module.util.netlink.NetlinkConstants.NLMSG_OVERRUN;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_DELADDR;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_DELLINK;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_DELNEIGH;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_DELROUTE;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_DELRULE;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_GETADDR;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_GETLINK;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_GETNEIGH;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_GETROUTE;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_GETRULE;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_NEWADDR;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_NEWLINK;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_NEWNDUSEROPT;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_NEWNEIGH;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_NEWROUTE;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_NEWRULE;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_SETLINK;
import static com.android.net.module.util.netlink.NetlinkConstants.SOCK_DIAG_BY_FAMILY;
import static com.android.net.module.util.netlink.NetlinkConstants.stringForNlMsgType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class NetlinkConstantsTest {
    private static final short UNKNOWN_FAMILY = 1234;

    private short makeCtType(short msgType) {
        return (short) (NFNL_SUBSYS_CTNETLINK << 8 | (byte) msgType);
    }

    @Test
    public void testStringForNlMsgType() {
        assertEquals("RTM_NEWLINK", stringForNlMsgType(RTM_NEWLINK, NETLINK_ROUTE));
        assertEquals("RTM_DELLINK", stringForNlMsgType(RTM_DELLINK, NETLINK_ROUTE));
        assertEquals("RTM_GETLINK", stringForNlMsgType(RTM_GETLINK, NETLINK_ROUTE));
        assertEquals("RTM_SETLINK", stringForNlMsgType(RTM_SETLINK, NETLINK_ROUTE));
        assertEquals("RTM_NEWADDR", stringForNlMsgType(RTM_NEWADDR, NETLINK_ROUTE));
        assertEquals("RTM_DELADDR", stringForNlMsgType(RTM_DELADDR, NETLINK_ROUTE));
        assertEquals("RTM_GETADDR", stringForNlMsgType(RTM_GETADDR, NETLINK_ROUTE));
        assertEquals("RTM_NEWROUTE", stringForNlMsgType(RTM_NEWROUTE, NETLINK_ROUTE));
        assertEquals("RTM_DELROUTE", stringForNlMsgType(RTM_DELROUTE, NETLINK_ROUTE));
        assertEquals("RTM_GETROUTE", stringForNlMsgType(RTM_GETROUTE, NETLINK_ROUTE));
        assertEquals("RTM_NEWNEIGH", stringForNlMsgType(RTM_NEWNEIGH, NETLINK_ROUTE));
        assertEquals("RTM_DELNEIGH", stringForNlMsgType(RTM_DELNEIGH, NETLINK_ROUTE));
        assertEquals("RTM_GETNEIGH", stringForNlMsgType(RTM_GETNEIGH, NETLINK_ROUTE));
        assertEquals("RTM_NEWRULE", stringForNlMsgType(RTM_NEWRULE, NETLINK_ROUTE));
        assertEquals("RTM_DELRULE", stringForNlMsgType(RTM_DELRULE, NETLINK_ROUTE));
        assertEquals("RTM_GETRULE", stringForNlMsgType(RTM_GETRULE, NETLINK_ROUTE));
        assertEquals("RTM_NEWNDUSEROPT", stringForNlMsgType(RTM_NEWNDUSEROPT, NETLINK_ROUTE));

        assertEquals("SOCK_DIAG_BY_FAMILY",
                stringForNlMsgType(SOCK_DIAG_BY_FAMILY, NETLINK_INET_DIAG));

        assertEquals("IPCTNL_MSG_CT_NEW",
                stringForNlMsgType(makeCtType(IPCTNL_MSG_CT_NEW), NETLINK_NETFILTER));
        assertEquals("IPCTNL_MSG_CT_GET",
                stringForNlMsgType(makeCtType(IPCTNL_MSG_CT_GET), NETLINK_NETFILTER));
        assertEquals("IPCTNL_MSG_CT_DELETE",
                stringForNlMsgType(makeCtType(IPCTNL_MSG_CT_DELETE), NETLINK_NETFILTER));
        assertEquals("IPCTNL_MSG_CT_GET_CTRZERO",
                stringForNlMsgType(makeCtType(IPCTNL_MSG_CT_GET_CTRZERO), NETLINK_NETFILTER));
        assertEquals("IPCTNL_MSG_CT_GET_STATS_CPU",
                stringForNlMsgType(makeCtType(IPCTNL_MSG_CT_GET_STATS_CPU), NETLINK_NETFILTER));
        assertEquals("IPCTNL_MSG_CT_GET_STATS",
                stringForNlMsgType(makeCtType(IPCTNL_MSG_CT_GET_STATS), NETLINK_NETFILTER));
        assertEquals("IPCTNL_MSG_CT_GET_DYING",
                stringForNlMsgType(makeCtType(IPCTNL_MSG_CT_GET_DYING), NETLINK_NETFILTER));
        assertEquals("IPCTNL_MSG_CT_GET_UNCONFIRMED",
                stringForNlMsgType(makeCtType(IPCTNL_MSG_CT_GET_UNCONFIRMED), NETLINK_NETFILTER));
    }

    @Test
    public void testStringForNlMsgType_ControlMessage() {
        for (int family : new int[]{NETLINK_ROUTE, NETLINK_INET_DIAG, NETLINK_NETFILTER}) {
            assertEquals("NLMSG_NOOP", stringForNlMsgType(NLMSG_NOOP, family));
            assertEquals("NLMSG_ERROR", stringForNlMsgType(NLMSG_ERROR, family));
            assertEquals("NLMSG_DONE", stringForNlMsgType(NLMSG_DONE, family));
            assertEquals("NLMSG_OVERRUN", stringForNlMsgType(NLMSG_OVERRUN, family));
        }
    }

    @Test
    public void testStringForNlMsgType_UnknownFamily() {
        assertTrue(stringForNlMsgType(RTM_NEWLINK, UNKNOWN_FAMILY).startsWith("unknown"));
        assertTrue(stringForNlMsgType(SOCK_DIAG_BY_FAMILY, UNKNOWN_FAMILY).startsWith("unknown"));
        assertTrue(stringForNlMsgType(makeCtType(IPCTNL_MSG_CT_NEW), UNKNOWN_FAMILY)
                .startsWith("unknown"));
    }
}
