/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * RouteControllerTest.cpp - unit tests for RouteController.cpp
 */

#include <gtest/gtest.h>
#include <fstream>

#include "Fwmark.h"
#include "IptablesBaseTest.h"
#include "NetlinkCommands.h"
#include "RouteController.h"

#include <android-base/stringprintf.h>

using android::base::StringPrintf;

static const char* TEST_IFACE1 = "netdtest1";
static const char* TEST_IFACE2 = "netdtest2";
static const uint32_t TEST_IFACE1_INDEX = 901;
static const uint32_t TEST_IFACE2_INDEX = 902;
// See Linux kernel source in include/net/flow.h
#define LOOPBACK_IFINDEX 1

namespace android {
namespace net {

class RouteControllerTest : public IptablesBaseTest {
public:
    RouteControllerTest() {
        RouteController::iptablesRestoreCommandFunction = fakeExecIptablesRestoreCommand;
        RouteController::ifNameToIndexFunction = fakeIfaceNameToIndexFunction;
    }

    int flushRoutes(uint32_t a) {
        return RouteController::flushRoutes(a);
    }

    uint32_t static fakeIfaceNameToIndexFunction(const char* iface) {
        // "lo" is the same as the real one
        if (!strcmp(iface, "lo")) return LOOPBACK_IFINDEX;
        if (!strcmp(iface, TEST_IFACE1)) return TEST_IFACE1_INDEX;
        if (!strcmp(iface, TEST_IFACE2)) return TEST_IFACE2_INDEX;
        return 0;
    }
};

TEST_F(RouteControllerTest, TestGetRulePriority) {
    // Expect a rule dump for these two families to contain at least the following priorities.
    for (int family : {AF_INET, AF_INET6 }) {
        std::set<uint32_t> expectedPriorities = {
                0,
                RULE_PRIORITY_LEGACY_SYSTEM,
                RULE_PRIORITY_LEGACY_NETWORK,
                RULE_PRIORITY_UNREACHABLE,
        };

        NetlinkDumpCallback callback = [&expectedPriorities] (const nlmsghdr *nlh) {
            expectedPriorities.erase(getRulePriority(nlh));
        };

        rtmsg rtm = { .rtm_family = static_cast<uint8_t>(family) };
        iovec iov[] = {
            { nullptr, 0           },
            { &rtm,    sizeof(rtm) },
        };

        ASSERT_EQ(0, sendNetlinkRequest(RTM_GETRULE, NETLINK_DUMP_FLAGS,
                                        iov, ARRAY_SIZE(iov), &callback));

        EXPECT_TRUE(expectedPriorities.empty()) <<
            "Did not see rule with priority " << *expectedPriorities.begin() <<
            " in dump for address family " << family;
    }
}

TEST_F(RouteControllerTest, TestRouteFlush) {
    // Pick a table number that's not used by the system.
    const uint32_t table1 = 500;
    const uint32_t table2 = 600;
    static_assert(table1 < RouteController::ROUTE_TABLE_OFFSET_FROM_INDEX,
                  "Test table1 number too large");
    static_assert(table2 < RouteController::ROUTE_TABLE_OFFSET_FROM_INDEX,
                  "Test table2 number too large");

    EXPECT_EQ(0, modifyIpRoute(RTM_NEWROUTE, NETLINK_ROUTE_CREATE_FLAGS, table1, "lo",
                               "192.0.2.2/32", nullptr, 0 /* mtu */, 0 /* priority */));
    EXPECT_EQ(0, modifyIpRoute(RTM_NEWROUTE, NETLINK_ROUTE_CREATE_FLAGS, table1, "lo",
                               "192.0.2.3/32", nullptr, 0 /* mtu */, 0 /* priority */));
    EXPECT_EQ(0, modifyIpRoute(RTM_NEWROUTE, NETLINK_ROUTE_CREATE_FLAGS, table2, "lo",
                               "192.0.2.4/32", nullptr, 0 /* mtu */, 0 /* priority */));

    EXPECT_EQ(0, flushRoutes(table1));

    EXPECT_EQ(-ESRCH, modifyIpRoute(RTM_DELROUTE, NETLINK_REQUEST_FLAGS, table1, "lo",
                                    "192.0.2.2/32", nullptr, 0 /* mtu */, 0 /* priority */));
    EXPECT_EQ(-ESRCH, modifyIpRoute(RTM_DELROUTE, NETLINK_REQUEST_FLAGS, table1, "lo",
                                    "192.0.2.3/32", nullptr, 0 /* mtu */, 0 /* priority */));
    EXPECT_EQ(0, modifyIpRoute(RTM_DELROUTE, NETLINK_REQUEST_FLAGS, table2, "lo",
                               "192.0.2.4/32", nullptr, 0 /* mtu */, 0 /* priority */));
}

TEST_F(RouteControllerTest, TestModifyIncomingPacketMark) {
  uint32_t mask = ~Fwmark::getUidBillingMask();

  static constexpr int TEST_NETID = 30;
  EXPECT_EQ(0, modifyIncomingPacketMark(TEST_NETID, "netdtest0",
                                        PERMISSION_NONE, true));
  expectIptablesRestoreCommands({StringPrintf(
      "-t mangle -A routectrl_mangle_INPUT -i netdtest0 -j MARK --set-mark "
      "0x3001e/0x%x",
      mask)});

  EXPECT_EQ(0, modifyIncomingPacketMark(TEST_NETID, "netdtest0",
                                        PERMISSION_NONE, false));
  expectIptablesRestoreCommands({StringPrintf(
      "-t mangle -D routectrl_mangle_INPUT -i netdtest0 -j MARK --set-mark "
      "0x3001e/0x%x",
      mask)});
}

bool hasLocalInterfaceInRouteTable(const char* iface) {
    // Calculate the table index from interface index
    std::string index = std::to_string(RouteController::ROUTE_TABLE_OFFSET_FROM_INDEX_FOR_LOCAL +
                                       RouteController::ifNameToIndexFunction(iface));
    std::string localIface =
            index + " " + std::string(iface) + std::string(RouteController::INTERFACE_LOCAL_SUFFIX);
    std::string line;

    std::ifstream input(RouteController::RT_TABLES_PATH);
    while (std::getline(input, line)) {
        if (line.find(localIface) != std::string::npos) {
            return true;
        }
    }

    return false;
}

TEST_F(RouteControllerTest, TestCreateVirtualLocalInterfaceTable) {
    static constexpr int TEST_NETID = 65500;
    std::map<int32_t, UidRanges> uidRangeMap;
    EXPECT_EQ(0, RouteController::addInterfaceToVirtualNetwork(TEST_NETID, TEST_IFACE1, false,
                                                               uidRangeMap, false));
    // Expect to create <iface>_local in the routing table
    EXPECT_TRUE(hasLocalInterfaceInRouteTable(TEST_IFACE1));
    // Add another interface, <TEST_IFACE2>_local should also be created
    EXPECT_EQ(0, RouteController::addInterfaceToVirtualNetwork(TEST_NETID, TEST_IFACE2, false,
                                                               uidRangeMap, false));
    EXPECT_TRUE(hasLocalInterfaceInRouteTable(TEST_IFACE2));
    // Remove TEST_IFACE1
    EXPECT_EQ(0, RouteController::removeInterfaceFromVirtualNetwork(TEST_NETID, TEST_IFACE1, false,
                                                                    uidRangeMap, false));
    // Interface remove should also remove the virtual local interface for routing table
    EXPECT_FALSE(hasLocalInterfaceInRouteTable(TEST_IFACE1));
    // <TEST_IFACE2> should still in the routing table
    EXPECT_TRUE(hasLocalInterfaceInRouteTable(TEST_IFACE2));
    EXPECT_EQ(0, RouteController::removeInterfaceFromVirtualNetwork(TEST_NETID, TEST_IFACE2, false,
                                                                    uidRangeMap, false));
    EXPECT_FALSE(hasLocalInterfaceInRouteTable(TEST_IFACE2));
}

}  // namespace net
}  // namespace android
