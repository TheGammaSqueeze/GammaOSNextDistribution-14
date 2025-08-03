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

import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_DUMP;
import static org.junit.Assert.fail;

import android.system.OsConstants;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StructNlMsgHdrTest {

    public static final short TEST_NLMSG_LEN = 16;
    public static final short TEST_NLMSG_FLAGS = StructNlMsgHdr.NLM_F_REQUEST
            | StructNlMsgHdr.NLM_F_MULTI | StructNlMsgHdr.NLM_F_ACK | StructNlMsgHdr.NLM_F_ECHO;
    public static final short TEST_NLMSG_SEQ = 1234;
    public static final short TEST_NLMSG_PID = 5678;

    // Checking the header string nlmsg_{len, ..} of the number can make sure that the checking
    // number comes from the expected element.
    // TODO: Verify more flags once StructNlMsgHdr can distinguish the flags which have the same
    // value. For example, NLM_F_MATCH (0x200) and NLM_F_EXCL (0x200) can't be distinguished.
    // See StructNlMsgHdrTest#stringForNlMsgFlags.
    public static final String TEST_NLMSG_LEN_STR = "nlmsg_len{16}";
    public static final String TEST_NLMSG_FLAGS_STR =
            "NLM_F_REQUEST|NLM_F_MULTI|NLM_F_ACK|NLM_F_ECHO";
    public static final String TEST_NLMSG_SEQ_STR = "nlmsg_seq{1234}";
    public static final String TEST_NLMSG_PID_STR = "nlmsg_pid{5678}";

    private StructNlMsgHdr makeStructNlMsgHdr(short type) {
        return makeStructNlMsgHdr(type, TEST_NLMSG_FLAGS);
    }

    private StructNlMsgHdr makeStructNlMsgHdr(short type, short flags) {
        final StructNlMsgHdr struct = new StructNlMsgHdr();
        struct.nlmsg_len = TEST_NLMSG_LEN;
        struct.nlmsg_type = type;
        struct.nlmsg_flags = flags;
        struct.nlmsg_seq = TEST_NLMSG_SEQ;
        struct.nlmsg_pid = TEST_NLMSG_PID;
        return struct;
    }

    private static void assertContains(String actualValue, String expectedSubstring) {
        if (actualValue.contains(expectedSubstring)) return;
        fail("\"" + actualValue + "\" does not contain \"" + expectedSubstring + "\"");
    }

    private static void assertNotContains(String actualValue, String unexpectedSubstring) {
        if (!actualValue.contains(unexpectedSubstring)) return;
        fail("\"" + actualValue + "\" contains \"" + unexpectedSubstring + "\"");
    }

    @Test
    public void testToString() {
        StructNlMsgHdr struct = makeStructNlMsgHdr(NetlinkConstants.RTM_NEWADDR);
        String s = struct.toString();
        assertContains(s, TEST_NLMSG_LEN_STR);
        assertContains(s, TEST_NLMSG_FLAGS_STR);
        assertContains(s, TEST_NLMSG_SEQ_STR);
        assertContains(s, TEST_NLMSG_PID_STR);
        assertContains(s, "nlmsg_type{20()}");

        struct = makeStructNlMsgHdr(NetlinkConstants.SOCK_DIAG_BY_FAMILY);
        s = struct.toString();
        assertContains(s, TEST_NLMSG_LEN_STR);
        assertContains(s, TEST_NLMSG_FLAGS_STR);
        assertContains(s, TEST_NLMSG_SEQ_STR);
        assertContains(s, TEST_NLMSG_PID_STR);
        assertContains(s, "nlmsg_type{20()}");
    }

    @Test
    public void testToStringWithNetlinkFamily() {
        StructNlMsgHdr struct = makeStructNlMsgHdr(NetlinkConstants.RTM_NEWADDR);
        String s = struct.toString(OsConstants.NETLINK_ROUTE);
        assertContains(s, TEST_NLMSG_LEN_STR);
        assertContains(s, TEST_NLMSG_FLAGS_STR);
        assertContains(s, TEST_NLMSG_SEQ_STR);
        assertContains(s, TEST_NLMSG_PID_STR);
        assertContains(s, "nlmsg_type{20(RTM_NEWADDR)}");

        struct = makeStructNlMsgHdr(NetlinkConstants.SOCK_DIAG_BY_FAMILY);
        s = struct.toString(OsConstants.NETLINK_INET_DIAG);
        assertContains(s, TEST_NLMSG_LEN_STR);
        assertContains(s, TEST_NLMSG_FLAGS_STR);
        assertContains(s, TEST_NLMSG_SEQ_STR);
        assertContains(s, TEST_NLMSG_PID_STR);
        assertContains(s, "nlmsg_type{20(SOCK_DIAG_BY_FAMILY)}");
    }

    @Test
    public void testToString_flags_dumpRequest() {
        final short flags = StructNlMsgHdr.NLM_F_REQUEST | StructNlMsgHdr.NLM_F_DUMP;
        StructNlMsgHdr struct = makeStructNlMsgHdr(NetlinkConstants.RTM_GETROUTE, flags);

        String s = struct.toString(OsConstants.NETLINK_ROUTE);

        assertContains(s, "RTM_GETROUTE");
        assertContains(s, "NLM_F_REQUEST");
        assertContains(s, "NLM_F_DUMP");
        // NLM_F_DUMP = NLM_F_ROOT | NLM_F_MATCH;
        assertNotContains(s, "NLM_F_MATCH");
        assertNotContains(s, "NLM_F_ROOT");
    }

    @Test
    public void testToString_flags_root() {
        final short flags = StructNlMsgHdr.NLM_F_ROOT;
        StructNlMsgHdr struct = makeStructNlMsgHdr(NetlinkConstants.RTM_GETROUTE, flags);

        String s = struct.toString(OsConstants.NETLINK_ROUTE);

        assertContains(s, "NLM_F_ROOT");
        // NLM_F_DUMP = NLM_F_ROOT | NLM_F_MATCH;
        assertNotContains(s, "NLM_F_DUMP");
    }
}
