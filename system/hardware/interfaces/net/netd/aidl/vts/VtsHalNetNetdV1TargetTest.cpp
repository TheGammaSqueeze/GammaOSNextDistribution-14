/*
 * Copyright 2022 The Android Open Source Project
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

#define LOG_TAG "netd_aidl_test"

#include <aidl/Gtest.h>
#include <aidl/Vintf.h>
#include <aidl/android/system/net/netd/INetd.h>
#include <android-base/stringprintf.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <gtest/gtest.h>
#include <linux/if.h>
#include <log/log.h>
#include <netutils/ifc.h>

#include "VtsHalNetNetdTestUtils.h"
#include "tun_interface.h"

using aidl::android::system::net::netd::INetd;
using android::base::StringPrintf;
using android::net::TunInterface;

namespace {
const net_handle_t INVALID_NET_HANDLE = 0x6600FACADE;

constexpr const char* IPV4_ROUTER = "192.0.2.1";
constexpr const char* IPV4_CONNECTED = "192.0.2.0/25";
constexpr const char* IPV4_SUBNET_1 = "192.0.2.192/28";
constexpr const char* IPV4_HOST_1 = "192.0.2.195";
constexpr const char* IPV4_SUBNET_2 = "192.0.2.240/28";
constexpr const char* IPV4_HOST_2 = "192.0.2.245";
constexpr const char* IPV4_UNREACHABLE = "192.0.2.239";

constexpr const char* IPV6_ROUTER = "2001:db8::cafe";
constexpr const char* IPV6_CONNECTED = "2001:db8::/64";
constexpr const char* IPV6_SUBNET_1 = "2001:db8:babe::/48";
constexpr const char* IPV6_HOST_1 = "2001:db8:babe::1";
constexpr const char* IPV6_SUBNET_2 = "2001:db8:d00d::/48";
constexpr const char* IPV6_HOST_2 = "2001:db8:d00d::1";
constexpr const char* IPV6_UNREACHABLE = "2001:db8:d0a::";

constexpr int NETD_STATUS_OK = 0;

std::vector<const char*> REACHABLE = {
    IPV4_ROUTER, IPV4_HOST_1, IPV4_HOST_2, IPV6_ROUTER, IPV6_HOST_1, IPV6_HOST_2,
};

void checkAllReachable(net_handle_t handle) {
    int ret;
    for (const auto& dst : REACHABLE) {
        ret = checkReachability(handle, dst);
        EXPECT_EQ(0, ret) << "Expected reachability to " << dst << " but got %s" << strerror(-ret);
    }
    for (const auto& dst : {IPV4_UNREACHABLE, IPV6_UNREACHABLE}) {
        EXPECT_EQ(-ENETUNREACH, checkReachability(handle, dst))
            << "Expected " << dst << " to be unreachable, but was reachable";
    }
}

void checkAllUnreachable(net_handle_t handle) {
    for (const auto& dst : REACHABLE) {
        EXPECT_EQ(-ENETUNREACH, checkReachability(handle, dst));
    }
    for (const auto& dst : {IPV4_UNREACHABLE, IPV6_UNREACHABLE}) {
        EXPECT_EQ(-ENETUNREACH, checkReachability(handle, dst));
    }
}

}  // namespace
class NetdAidlTest : public ::testing::TestWithParam<std::string> {
   public:
    // Netd HAL instance.
    std::shared_ptr<INetd> netd;

    // The nethandle of our test network, and its packet mark.
    net_handle_t mNetHandle;
    uint32_t mPacketMark;

    // Two interfaces that we can add and remove from our test network.
    static TunInterface sTun1;
    static TunInterface sTun2;

    // The interface name of sTun1, for convenience.
    static const char* sIfaceName;

    static void SetUpTestCase() {
        ASSERT_EQ(0, sTun1.init());
        ASSERT_EQ(0, sTun2.init());
        ASSERT_LE(sTun1.name().size(), static_cast<size_t>(IFNAMSIZ));
        ASSERT_LE(sTun2.name().size(), static_cast<size_t>(IFNAMSIZ));
        ifc_init();
        ASSERT_EQ(0, ifc_up(sTun1.name().c_str()));
        ASSERT_EQ(0, ifc_up(sTun2.name().c_str()));
        sIfaceName = sTun1.name().c_str();
    }

    static void TearDownTestCase() {
        sTun1.destroy();
        sTun2.destroy();
        ifc_close();
    }

    virtual void SetUp() override {
        netd =
            INetd::fromBinder(ndk::SpAIBinder(AServiceManager_waitForService(GetParam().c_str())));

        ASSERT_NE(netd, nullptr) << "Could not get AIDL instance";

        // Set up an OEM network.
        INetd::OemNetwork oemNetwork;
        auto ret = netd->createOemNetwork(&oemNetwork);
        mNetHandle = oemNetwork.networkHandle;
        mPacketMark = oemNetwork.packetMark;
        ASSERT_TRUE(ret.isOk());
        ASSERT_NE(NETWORK_UNSPECIFIED, mNetHandle);
        ASSERT_NE((uint32_t)0, mPacketMark);
    }

    virtual void TearDown() override {
        auto ret = netd->destroyOemNetwork(mNetHandle);
        ASSERT_TRUE(ret.isOk());
    }

    void expectAddRoute(int expectedStatus, net_handle_t handle, const char* iface,
                        const char* destination, const char* nexthop) {
        auto ret = netd->addRouteToOemNetwork(handle, iface, destination, nexthop);
        if (expectedStatus == NETD_STATUS_OK) {
            EXPECT_TRUE(ret.isOk());
        } else {
            EXPECT_EQ(expectedStatus, ret.getServiceSpecificError());
        }
    }

    void expectAddRouteSuccess(net_handle_t h, const char* i, const char* d, const char* n) {
        expectAddRoute(NETD_STATUS_OK, h, i, d, n);
    }

    void expectRemoveRoute(int expectedStatus, net_handle_t handle, const char* iface,
                           const char* destination, const char* nexthop) {
        auto ret = netd->removeRouteFromOemNetwork(handle, iface, destination, nexthop);
        if (expectedStatus == NETD_STATUS_OK) {
            EXPECT_TRUE(ret.isOk());
        } else {
            EXPECT_EQ(expectedStatus, ret.getServiceSpecificError());
        }
    }

    void expectRemoveRouteSuccess(net_handle_t h, const char* i, const char* d, const char* n) {
        expectRemoveRoute(NETD_STATUS_OK, h, i, d, n);
    }
};

TunInterface NetdAidlTest::sTun1;
TunInterface NetdAidlTest::sTun2;
const char* NetdAidlTest::sIfaceName;

// Tests adding and removing interfaces from the OEM network.
TEST_P(NetdAidlTest, TestAddRemoveInterfaces) {
    // HACK: mark out permissions bits.
    uint32_t packetMark = mPacketMark & 0xffff;

    EXPECT_EQ(0, checkNetworkExists(mNetHandle));
    EXPECT_EQ(0, countRulesForFwmark(packetMark));

    // Adding an interface creates the routing rules.
    auto ret = netd->addInterfaceToOemNetwork(mNetHandle, sIfaceName);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(0, checkNetworkExists(mNetHandle));
    EXPECT_EQ(2, countRulesForFwmark(packetMark));

    // Adding an interface again silently succeeds.
    ret = netd->addInterfaceToOemNetwork(mNetHandle, sIfaceName);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(0, checkNetworkExists(mNetHandle));
    EXPECT_EQ(2, countRulesForFwmark(packetMark));

    // More than one network can be created.

    INetd::OemNetwork oem;
    ret = netd->createOemNetwork(&oem);
    EXPECT_TRUE(ret.isOk());
    net_handle_t netHandle2 = oem.networkHandle;
    uint32_t packetMark2 = oem.packetMark & 0xffff;
    EXPECT_NE(mNetHandle, netHandle2);
    EXPECT_NE(packetMark, packetMark2);
    EXPECT_EQ(0, checkNetworkExists(netHandle2));
    EXPECT_EQ(0, countRulesForFwmark(packetMark2));

    // An interface can only be in one network.
    ret = netd->addInterfaceToOemNetwork(netHandle2, sIfaceName);
    EXPECT_FALSE(ret.isOk());
    EXPECT_EQ(INetd::STATUS_UNKNOWN_ERROR, ret.getServiceSpecificError());

    // Removing the interface removes the rules.
    ret = netd->removeInterfaceFromOemNetwork(mNetHandle, sIfaceName);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(0, countRulesForFwmark(packetMark));

    ret = netd->addInterfaceToOemNetwork(netHandle2, sIfaceName);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(2, countRulesForFwmark(packetMark2));

    // When a network is removed the interfaces are deleted.
    ret = netd->destroyOemNetwork(netHandle2);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(-ENONET, checkNetworkExists(netHandle2));
    EXPECT_EQ(0, countRulesForFwmark(packetMark2));

    // Adding an interface to a non-existent network fails.
    ret = netd->addInterfaceToOemNetwork(INVALID_NET_HANDLE, sIfaceName);
    EXPECT_FALSE(ret.isOk());
    EXPECT_EQ(INetd::STATUS_INVALID_ARGUMENTS, ret.getServiceSpecificError());
    ret = netd->removeInterfaceFromOemNetwork(INVALID_NET_HANDLE, sIfaceName);
    EXPECT_FALSE(ret.isOk());
    EXPECT_EQ(INetd::STATUS_INVALID_ARGUMENTS, ret.getServiceSpecificError());
}

TEST_P(NetdAidlTest, TestAddRemoveRoutes) {
    auto ret = netd->addInterfaceToOemNetwork(mNetHandle, sIfaceName);
    ASSERT_TRUE(ret.isOk());

    // Network exists, but has no routes and no connectivity.
    EXPECT_EQ(0, checkNetworkExists(mNetHandle));
    checkAllUnreachable(mNetHandle);

    // Add a directly-connected route and two gatewayed routes through it.
    expectAddRouteSuccess(mNetHandle, sIfaceName, IPV4_CONNECTED, "");
    expectAddRouteSuccess(mNetHandle, sIfaceName, IPV4_SUBNET_1, IPV4_ROUTER);
    expectAddRouteSuccess(mNetHandle, sIfaceName, IPV4_SUBNET_2, IPV4_ROUTER);
    expectAddRouteSuccess(mNetHandle, sIfaceName, IPV6_CONNECTED, "");
    expectAddRouteSuccess(mNetHandle, sIfaceName, IPV6_SUBNET_1, IPV6_ROUTER);
    expectAddRouteSuccess(mNetHandle, sIfaceName, IPV6_SUBNET_2, IPV6_ROUTER);

    // Test some destinations.
    checkAllReachable(mNetHandle);

    // Remove the directly-connected routes and everything is unreachable again.
    expectRemoveRouteSuccess(mNetHandle, sIfaceName, IPV4_CONNECTED, "");
    expectRemoveRouteSuccess(mNetHandle, sIfaceName, IPV6_CONNECTED, "");
    expectRemoveRouteSuccess(mNetHandle, sIfaceName, IPV4_SUBNET_1, IPV4_ROUTER);
    expectRemoveRouteSuccess(mNetHandle, sIfaceName, IPV4_SUBNET_2, IPV4_ROUTER);
    expectRemoveRouteSuccess(mNetHandle, sIfaceName, IPV6_SUBNET_1, IPV6_ROUTER);
    expectRemoveRouteSuccess(mNetHandle, sIfaceName, IPV6_SUBNET_2, IPV6_ROUTER);

    checkAllUnreachable(mNetHandle);

    // Invalid: route doesn't exist so can't be deleted.
    expectRemoveRoute(INetd::STATUS_UNKNOWN_ERROR, mNetHandle, sIfaceName, IPV4_CONNECTED, "");

    // Invalid: IP address instead of prefix.
    expectAddRoute(INetd::STATUS_INVALID_ARGUMENTS, mNetHandle, sIfaceName, IPV4_HOST_1, "");
    expectAddRoute(INetd::STATUS_INVALID_ARGUMENTS, mNetHandle, sIfaceName, IPV6_HOST_1, "");

    // Invalid: both nexthop and interface are empty.
    expectAddRoute(INetd::STATUS_UNKNOWN_ERROR, mNetHandle, "", IPV4_SUBNET_1, "");
    expectAddRoute(INetd::STATUS_UNKNOWN_ERROR, mNetHandle, "", IPV6_SUBNET_1, "");

    // The kernel deletes the routes when the interfaces go away.
}

// Tests enabling and disabling forwarding between interfaces.
TEST_P(NetdAidlTest, TestForwarding) {
    auto ret = netd->addInterfaceToOemNetwork(mNetHandle, sTun1.name().c_str());
    EXPECT_TRUE(ret.isOk());
    ret = netd->addInterfaceToOemNetwork(mNetHandle, sTun2.name().c_str());
    EXPECT_TRUE(ret.isOk());

    // TODO: move this test to netd and use ROUTE_TABLE_OFFSET_FROM_INDEX directly.
    uint32_t table1 = 1000 + sTun1.ifindex();
    uint32_t table2 = 1000 + sTun1.ifindex();
    const char* regexTemplate = "from all iif %s .*lookup (%s|%d)";
    std::string regex1 =
        StringPrintf(regexTemplate, sTun1.name().c_str(), sTun2.name().c_str(), table2);
    std::string regex2 =
        StringPrintf(regexTemplate, sTun2.name().c_str(), sTun1.name().c_str(), table1);

    EXPECT_EQ(0, countMatchingIpRules(regex1));
    EXPECT_EQ(0, countMatchingIpRules(regex2));

    ret = netd->setForwardingBetweenInterfaces(sTun1.name(), sTun2.name(), true);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(2, countMatchingIpRules(regex1));
    EXPECT_EQ(0, countMatchingIpRules(regex2));

    // No attempt at deduplicating rules is made.
    ret = netd->setForwardingBetweenInterfaces(sTun1.name(), sTun2.name(), true);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(4, countMatchingIpRules(regex1));

    ret = netd->setForwardingBetweenInterfaces(sTun1.name(), sTun2.name(), false);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(2, countMatchingIpRules(regex1));

    ret = netd->setForwardingBetweenInterfaces(sTun2.name(), sTun1.name(), true);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(2, countMatchingIpRules(regex1));
    EXPECT_EQ(2, countMatchingIpRules(regex2));

    ret = netd->setForwardingBetweenInterfaces(sTun1.name(), sTun2.name(), false);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(0, countMatchingIpRules(regex1));
    EXPECT_EQ(2, countMatchingIpRules(regex2));

    ret = netd->setForwardingBetweenInterfaces(sTun2.name(), sTun1.name(), false);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(0, countMatchingIpRules(regex1));
    EXPECT_EQ(0, countMatchingIpRules(regex2));

    // Deleting rules that don't exist fails.
    ret = netd->setForwardingBetweenInterfaces(sTun1.name(), sTun2.name(), false);
    EXPECT_FALSE(ret.isOk());
    EXPECT_EQ(INetd::STATUS_UNKNOWN_ERROR, ret.getServiceSpecificError());
    EXPECT_EQ(0, countMatchingIpRules(regex1));
    EXPECT_EQ(0, countMatchingIpRules(regex2));
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(NetdAidlTest);
INSTANTIATE_TEST_SUITE_P(PerInstance, NetdAidlTest,
                         testing::ValuesIn(::android::getAidlHalInstanceNames(INetd::descriptor)),
                         android::PrintInstanceNameToString);
