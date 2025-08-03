/*
 * Copyright 2016 The Android Open Source Project
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
 * binder_test.cpp - unit tests for netd binder RPCs.
 */

#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cinttypes>
#include <condition_variable>
#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <mutex>
#include <numeric>
#include <regex>
#include <set>
#include <string>
#include <vector>

#include <dirent.h>
#include <fcntl.h>
#include <ifaddrs.h>
#include <linux/if.h>
#include <linux/if_tun.h>
#include <net/ethernet.h>
#include <net/if.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <openssl/base64.h>
#include <sys/socket.h>
#include <sys/types.h>

#include <android-base/file.h>
#include <android-base/format.h>
#include <android-base/macros.h>
#include <android-base/scopeguard.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/test_utils.h>
#include <android/multinetwork.h>
#include <binder/IPCThreadState.h>
#include <bpf/KernelUtils.h>
#include <com/android/internal/net/BnOemNetdUnsolicitedEventListener.h>
#include <com/android/internal/net/IOemNetd.h>
#include <cutils/multiuser.h>
#include <gtest/gtest.h>
#include <netdutils/NetNativeTestBase.h>
#include <netutils/ifc.h>
#include <utils/Errors.h>
#include "Fwmark.h"
#include "InterfaceController.h"
#include "NetdClient.h"
#include "NetdConstants.h"
#include "NetworkController.h"
#include "RouteController.h"
#include "SockDiag.h"
#include "TestUnsolService.h"
#include "XfrmController.h"
#include "android/net/INetd.h"
#include "binder/IServiceManager.h"
#include "netdutils/InternetAddresses.h"
#include "netdutils/Stopwatch.h"
#include "netdutils/Syscalls.h"
#include "netdutils/Utils.h"
#include "netid_client.h"  // NETID_UNSET
#include "nettestutils/DumpService.h"
#include "test_utils.h"
#include "tun_interface.h"

#define IP6TABLES_PATH "/system/bin/ip6tables"
#define IPTABLES_PATH "/system/bin/iptables"
#define RAW_TABLE "raw"
#define MANGLE_TABLE "mangle"
#define FILTER_TABLE "filter"
#define NAT_TABLE "nat"

namespace binder = android::binder;

using android::IBinder;
using android::IServiceManager;
using android::sp;
using android::String16;
using android::String8;
using android::base::Join;
using android::base::make_scope_guard;
using android::base::ReadFileToString;
using android::base::StartsWith;
using android::base::StringPrintf;
using android::base::Trim;
using android::base::unique_fd;
using android::binder::Status;
using android::bpf::isAtLeastKernelVersion;
using android::net::INetd;
using android::net::InterfaceConfigurationParcel;
using android::net::InterfaceController;
using android::net::MarkMaskParcel;
using android::net::NativeNetworkConfig;
using android::net::NativeNetworkType;
using android::net::NativeVpnType;
using android::net::NetworkController;
using android::net::RULE_PRIORITY_BYPASSABLE_VPN_LOCAL_EXCLUSION;
using android::net::RULE_PRIORITY_BYPASSABLE_VPN_NO_LOCAL_EXCLUSION;
using android::net::RULE_PRIORITY_DEFAULT_NETWORK;
using android::net::RULE_PRIORITY_EXPLICIT_NETWORK;
using android::net::RULE_PRIORITY_LOCAL_NETWORK;
using android::net::RULE_PRIORITY_LOCAL_ROUTES;
using android::net::RULE_PRIORITY_OUTPUT_INTERFACE;
using android::net::RULE_PRIORITY_PROHIBIT_NON_VPN;
using android::net::RULE_PRIORITY_SECURE_VPN;
using android::net::RULE_PRIORITY_TETHERING;
using android::net::RULE_PRIORITY_UID_DEFAULT_NETWORK;
using android::net::RULE_PRIORITY_UID_DEFAULT_UNREACHABLE;
using android::net::RULE_PRIORITY_UID_EXPLICIT_NETWORK;
using android::net::RULE_PRIORITY_UID_IMPLICIT_NETWORK;
using android::net::RULE_PRIORITY_UID_LOCAL_ROUTES;
using android::net::RULE_PRIORITY_VPN_FALLTHROUGH;
using android::net::SockDiag;
using android::net::TetherOffloadRuleParcel;
using android::net::TetherStatsParcel;
using android::net::TunInterface;
using android::net::UidRangeParcel;
using android::net::UidRanges;
using android::net::netd::aidl::NativeUidRangeConfig;
using android::netdutils::getIfaceNames;
using android::netdutils::IPAddress;
using android::netdutils::IPSockAddr;
using android::netdutils::ScopedAddrinfo;
using android::netdutils::sSyscalls;
using android::netdutils::Stopwatch;

static const char* IP_RULE_V4 = "-4";
static const char* IP_RULE_V6 = "-6";
static const int TEST_NETID1 = 65501;
static const int TEST_NETID2 = 65502;
static const int TEST_NETID3 = 65503;
static const int TEST_NETID4 = 65504;
static const int TEST_DUMP_NETID = 65123;
static const char* DNSMASQ = "dnsmasq";

// Use maximum reserved appId for applications to avoid conflict with existing
// uids.
static const int TEST_UID1 = 99999;
static const int TEST_UID2 = 99998;
static const int TEST_UID3 = 99997;
static const int TEST_UID4 = 99996;
static const int TEST_UID5 = 99995;
static const int TEST_UID6 = 99994;

constexpr int BASE_UID = AID_USER_OFFSET * 5;

static const std::string NO_SOCKET_ALLOW_RULE("! owner UID match 0-4294967294");
static const std::string ESP_ALLOW_RULE("esp");

static const in6_addr V6_ADDR = {
        {// 2001:db8:cafe::8888
         .u6_addr8 = {0x20, 0x01, 0x0d, 0xb8, 0xca, 0xfe, 0, 0, 0, 0, 0, 0, 0, 0, 0x88, 0x88}}};

typedef enum { ALL_EXIST, NONE_EXIST } ExistMode;

class NetdBinderTest : public NetNativeTestBase {
  public:
    NetdBinderTest() {
        sp<IServiceManager> sm = android::defaultServiceManager();
        sp<IBinder> binder = sm->getService(String16("netd"));
        if (binder != nullptr) {
            mNetd = android::interface_cast<INetd>(binder);
        }
    }

    void SetUp() override {
        ASSERT_NE(nullptr, mNetd.get());
    }

    void TearDown() override {
        mNetd->networkDestroy(TEST_NETID1);
        mNetd->networkDestroy(TEST_NETID2);
        mNetd->networkDestroy(TEST_NETID3);
        mNetd->networkDestroy(TEST_NETID4);
        setNetworkForProcess(NETID_UNSET);
        // Restore default network
        if (mStoredDefaultNetwork >= 0) mNetd->networkSetDefault(mStoredDefaultNetwork);
    }

    bool allocateIpSecResources(bool expectOk, int32_t* spi);

    // Static because setting up the tun interface takes about 40ms.
    static void SetUpTestCase() {
        setBackgroundNetworkingEnabledForUid(TEST_UID1, true);
        setBackgroundNetworkingEnabledForUid(TEST_UID2, true);
        setBackgroundNetworkingEnabledForUid(TEST_UID3, true);
        setBackgroundNetworkingEnabledForUid(TEST_UID4, true);
        setBackgroundNetworkingEnabledForUid(TEST_UID5, true);
        setBackgroundNetworkingEnabledForUid(TEST_UID6, true);

        ASSERT_EQ(0, sTun.init());
        ASSERT_EQ(0, sTun2.init());
        ASSERT_EQ(0, sTun3.init());
        ASSERT_EQ(0, sTun4.init());
        ASSERT_LE(sTun.name().size(), static_cast<size_t>(IFNAMSIZ));
        ASSERT_LE(sTun2.name().size(), static_cast<size_t>(IFNAMSIZ));
        ASSERT_LE(sTun3.name().size(), static_cast<size_t>(IFNAMSIZ));
        ASSERT_LE(sTun4.name().size(), static_cast<size_t>(IFNAMSIZ));
    }

    static void TearDownTestCase() {
        setBackgroundNetworkingEnabledForUid(TEST_UID1, false);
        setBackgroundNetworkingEnabledForUid(TEST_UID2, false);
        setBackgroundNetworkingEnabledForUid(TEST_UID3, false);
        setBackgroundNetworkingEnabledForUid(TEST_UID4, false);
        setBackgroundNetworkingEnabledForUid(TEST_UID5, false);
        setBackgroundNetworkingEnabledForUid(TEST_UID6, false);

        // Closing the socket removes the interface and IP addresses.
        sTun.destroy();
        sTun2.destroy();
        sTun3.destroy();
        sTun4.destroy();
    }

    static void fakeRemoteSocketPair(unique_fd* clientSocket, unique_fd* serverSocket,
                                     unique_fd* acceptedSocket);

    void createVpnNetworkWithUid(bool secure, uid_t uid, int vpnNetId = TEST_NETID2,
                                 int fallthroughNetId = TEST_NETID1,
                                 int nonDefaultNetId = TEST_NETID3);

    void createAndSetDefaultNetwork(int netId, const std::string& interface,
                                    int permission = INetd::PERMISSION_NONE);

    void createPhysicalNetwork(int netId, const std::string& interface,
                               int permission = INetd::PERMISSION_NONE);

    void createDefaultAndOtherPhysicalNetwork(int defaultNetId, int otherNetId);

    void createVpnAndOtherPhysicalNetwork(int systemDefaultNetId, int otherNetId, int vpnNetId,
                                          bool secure);

    void createVpnAndAppDefaultNetworkWithUid(int systemDefaultNetId, int appDefaultNetId,
                                              int vpnNetId, bool secure,
                                              std::vector<UidRangeParcel>&& appDefaultUidRanges,
                                              std::vector<UidRangeParcel>&& vpnUidRanges);

    void setupNetworkRoutesForVpnAndDefaultNetworks(
            int systemDefaultNetId, int appDefaultNetId, int vpnNetId, int otherNetId, bool secure,
            bool testV6, bool differentLocalRoutes,
            std::vector<UidRangeParcel>&& appDefaultUidRanges,
            std::vector<UidRangeParcel>&& vpnUidRanges);

  protected:
    // Use -1 to represent that default network was not modified because
    // real netId must be an unsigned value.
    int mStoredDefaultNetwork = -1;
    sp<INetd> mNetd;
    static TunInterface sTun;
    static TunInterface sTun2;
    static TunInterface sTun3;
    static TunInterface sTun4;

  private:
    static void setBackgroundNetworkingEnabledForUid(int uid, bool enabled) {
        runBinderCommand("connectivity",
                         StringPrintf("set-background-networking-enabled-for-uid %d %s", uid,
                                      enabled ? "true" : "false"));
    }
};

TunInterface NetdBinderTest::sTun;
TunInterface NetdBinderTest::sTun2;
TunInterface NetdBinderTest::sTun3;
TunInterface NetdBinderTest::sTun4;

class TimedOperation : public Stopwatch {
  public:
    explicit TimedOperation(const std::string &name): mName(name) {}
    virtual ~TimedOperation() {
        std::cerr << "    " << mName << ": " << timeTakenUs() << "us" << std::endl;
    }

  private:
    std::string mName;
};

TEST_F(NetdBinderTest, IsAlive) {
    TimedOperation t("isAlive RPC");
    bool isAlive = false;
    mNetd->isAlive(&isAlive);
    ASSERT_TRUE(isAlive);
}

namespace {

NativeNetworkConfig makeNativeNetworkConfig(int netId, NativeNetworkType networkType,
                                            int permission, bool secure, bool excludeLocalRoutes) {
    NativeNetworkConfig config = {};
    config.netId = netId;
    config.networkType = networkType;
    config.permission = permission;
    config.secure = secure;
    // The vpnType doesn't matter in AOSP. Just pick a well defined one from INetd.
    config.vpnType = NativeVpnType::PLATFORM;
    config.excludeLocalRoutes = excludeLocalRoutes;
    return config;
}

}  // namespace

bool testNetworkExistsButCannotConnect(const sp<INetd>& netd, TunInterface& ifc, const int netId) {
    // If this network exists, we should definitely not be able to create it.
    // Note that this networkCreate is never allowed to create reserved network IDs, so
    // this call may fail for other reasons than the network already existing.
    const auto& config = makeNativeNetworkConfig(netId, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_FALSE(netd->networkCreate(config).isOk());
    // Test if the network exist by adding interface. INetd has no dedicated method to query. When
    // the network exists and the interface can be added, the function succeeds. When the network
    // exists but the interface cannot be added, it fails with EINVAL, otherwise it is ENONET.
    binder::Status status = netd->networkAddInterface(netId, ifc.name());
    if (status.isOk()) {  // clean up
        EXPECT_TRUE(netd->networkRemoveInterface(netId, ifc.name()).isOk());
    } else if (status.serviceSpecificErrorCode() == ENONET) {
        return false;
    }

    const sockaddr_in6 sin6 = {.sin6_family = AF_INET6,
                               .sin6_port = htons(53),
                               .sin6_addr = {{.u6_addr32 = {htonl(0x20010db8), 0, 0, 0}}},
                              };
    const int s = socket(AF_INET6, SOCK_DGRAM, 0);
    EXPECT_NE(-1, s);
    if (s == -1) return true;
    Fwmark fwmark;
    fwmark.explicitlySelected = true;
    fwmark.netId = netId;
    EXPECT_EQ(0, setsockopt(s, SOL_SOCKET, SO_MARK, &fwmark.intValue, sizeof(fwmark.intValue)));
    const int ret = connect(s, (struct sockaddr*)&sin6, sizeof(sin6));
    const int err = errno;
    EXPECT_EQ(-1, ret);
    EXPECT_EQ(ENETUNREACH, err);
    close(s);
    return true;
}

TEST_F(NetdBinderTest, InitialNetworksExist) {
    EXPECT_TRUE(testNetworkExistsButCannotConnect(mNetd, sTun, INetd::DUMMY_NET_ID));
    EXPECT_TRUE(testNetworkExistsButCannotConnect(mNetd, sTun, INetd::LOCAL_NET_ID));
    EXPECT_TRUE(testNetworkExistsButCannotConnect(mNetd, sTun, INetd::UNREACHABLE_NET_ID));
    EXPECT_FALSE(testNetworkExistsButCannotConnect(mNetd, sTun, 77 /* not exist */));
}

TEST_F(NetdBinderTest, IpSecTunnelInterface) {
    const struct TestData {
        const std::string family;
        const std::string deviceName;
        const std::string localAddress;
        const std::string remoteAddress;
        int32_t iKey;
        int32_t oKey;
        int32_t ifId;
    } kTestData[] = {
            {"IPV4", "ipsec_test", "127.0.0.1", "8.8.8.8", 0x1234 + 53, 0x1234 + 53, 0xFFFE},
            {"IPV6", "ipsec_test6", "::1", "2001:4860:4860::8888", 0x1234 + 50, 0x1234 + 50,
             0xFFFE},
    };

    for (size_t i = 0; i < std::size(kTestData); i++) {
        const auto& td = kTestData[i];

        binder::Status status;

        // Create Tunnel Interface.
        status = mNetd->ipSecAddTunnelInterface(td.deviceName, td.localAddress, td.remoteAddress,
                                                td.iKey, td.oKey, td.ifId);
        EXPECT_TRUE(status.isOk()) << td.family << status.exceptionMessage();

        // Check that the interface exists
        EXPECT_NE(0U, if_nametoindex(td.deviceName.c_str()));

        // Update Tunnel Interface.
        status = mNetd->ipSecUpdateTunnelInterface(td.deviceName, td.localAddress, td.remoteAddress,
                                                   td.iKey, td.oKey, td.ifId);
        EXPECT_TRUE(status.isOk()) << td.family << status.exceptionMessage();

        // Remove Tunnel Interface.
        status = mNetd->ipSecRemoveTunnelInterface(td.deviceName);
        EXPECT_TRUE(status.isOk()) << td.family << status.exceptionMessage();

        // Check that the interface no longer exists
        EXPECT_EQ(0U, if_nametoindex(td.deviceName.c_str()));
    }
}

TEST_F(NetdBinderTest, IpSecSetEncapSocketOwner) {
    unique_fd uniqueFd(socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0));
    android::os::ParcelFileDescriptor sockFd(std::move(uniqueFd));

    int sockOptVal = UDP_ENCAP_ESPINUDP;
    setsockopt(sockFd.get(), IPPROTO_UDP, UDP_ENCAP, &sockOptVal, sizeof(sockOptVal));

    binder::Status res = mNetd->ipSecSetEncapSocketOwner(sockFd, 1001);
    EXPECT_TRUE(res.isOk());

    struct stat info;
    EXPECT_EQ(0, fstat(sockFd.get(), &info));
    EXPECT_EQ(1001, (int) info.st_uid);
}

// IPsec tests are not run in 32 bit mode; both 32-bit kernels and
// mismatched ABIs (64-bit kernel with 32-bit userspace) are unsupported.
#if INTPTR_MAX != INT32_MAX

using android::net::XfrmController;

static const int XFRM_DIRECTIONS[] = {static_cast<int>(android::net::XfrmDirection::IN),
                                      static_cast<int>(android::net::XfrmDirection::OUT)};
static const int ADDRESS_FAMILIES[] = {AF_INET, AF_INET6};

#define RETURN_FALSE_IF_NEQ(_expect_, _ret_) \
        do { if ((_expect_) != (_ret_)) return false; } while(false)
bool NetdBinderTest::allocateIpSecResources(bool expectOk, int32_t* spi) {
    android::netdutils::Status status = XfrmController::ipSecAllocateSpi(0, "::", "::1", 123, spi);
    SCOPED_TRACE(status);
    RETURN_FALSE_IF_NEQ(status.ok(), expectOk);

    // Add a policy
    status = XfrmController::ipSecAddSecurityPolicy(0, AF_INET6, 0, "::", "::1", 123, 0, 0, 0);
    SCOPED_TRACE(status);
    RETURN_FALSE_IF_NEQ(status.ok(), expectOk);

    // Add an ipsec interface
    return expectOk == XfrmController::ipSecAddTunnelInterface("ipsec_test", "::", "::1", 0xF00D,
                                                               0xD00D, 0xE00D, false)
                               .ok();
}

TEST_F(NetdBinderTest, XfrmDualSelectorTunnelModePoliciesV4) {
    android::binder::Status status;

    // Repeat to ensure cleanup and recreation works correctly
    for (int i = 0; i < 2; i++) {
        for (int direction : XFRM_DIRECTIONS) {
            for (int addrFamily : ADDRESS_FAMILIES) {
                status = mNetd->ipSecAddSecurityPolicy(0, addrFamily, direction, "127.0.0.5",
                                                       "127.0.0.6", 123, 0, 0, 0);
                EXPECT_TRUE(status.isOk())
                        << " family: " << addrFamily << " direction: " << direction;
            }
        }

        // Cleanup
        for (int direction : XFRM_DIRECTIONS) {
            for (int addrFamily : ADDRESS_FAMILIES) {
                status = mNetd->ipSecDeleteSecurityPolicy(0, addrFamily, direction, 0, 0, 0);
                EXPECT_TRUE(status.isOk());
            }
        }
    }
}

TEST_F(NetdBinderTest, XfrmDualSelectorTunnelModePoliciesV6) {
    binder::Status status;

    // Repeat to ensure cleanup and recreation works correctly
    for (int i = 0; i < 2; i++) {
        for (int direction : XFRM_DIRECTIONS) {
            for (int addrFamily : ADDRESS_FAMILIES) {
                status = mNetd->ipSecAddSecurityPolicy(0, addrFamily, direction, "2001:db8::f00d",
                                                       "2001:db8::d00d", 123, 0, 0, 0);
                EXPECT_TRUE(status.isOk())
                        << " family: " << addrFamily << " direction: " << direction;
            }
        }

        // Cleanup
        for (int direction : XFRM_DIRECTIONS) {
            for (int addrFamily : ADDRESS_FAMILIES) {
                status = mNetd->ipSecDeleteSecurityPolicy(0, addrFamily, direction, 0, 0, 0);
                EXPECT_TRUE(status.isOk());
            }
        }
    }
}

TEST_F(NetdBinderTest, XfrmControllerInit) {
    android::netdutils::Status status;
    status = XfrmController::Init();
    SCOPED_TRACE(status);

    // Older devices or devices with mismatched Kernel/User ABI cannot support the IPsec
    // feature.
    if (status.code() == EOPNOTSUPP) return;

    ASSERT_TRUE(status.ok());

    int32_t spi = 0;

    ASSERT_TRUE(allocateIpSecResources(true, &spi));
    ASSERT_TRUE(allocateIpSecResources(false, &spi));

    status = XfrmController::Init();
    ASSERT_TRUE(status.ok());
    ASSERT_TRUE(allocateIpSecResources(true, &spi));

    // Clean up
    status = XfrmController::ipSecDeleteSecurityAssociation(0, "::", "::1", 123, spi, 0, 0);
    SCOPED_TRACE(status);
    ASSERT_TRUE(status.ok());

    status = XfrmController::ipSecDeleteSecurityPolicy(0, AF_INET6, 0, 0, 0, 0);
    SCOPED_TRACE(status);
    ASSERT_TRUE(status.ok());

    // Remove Virtual Tunnel Interface.
    ASSERT_TRUE(XfrmController::ipSecRemoveTunnelInterface("ipsec_test").ok());
}

// Two kernel fixes have been added in 5.17 to allow XFRM_MIGRATE to work correctly
// when (1) there are multiple tunnels with the same selectors; and (2) addresses
// are updated to a different IP family. These two fixes were pulled into upstream
// LTS releases 4.14.273, 4.19.236, 5.4.186, 5.10.107 and 5.15.30, from whence they
// flowed into the Android Common Kernel (via standard LTS merges).
// As such we require 4.14.273+, 4.19.236+, 5.4.186+, 5.10.107+, 5.15.30+ and 5.17+
// to have these fixes.
bool hasXfrmMigrateKernelFixes() {
    return (isAtLeastKernelVersion(4, 14, 273) && !isAtLeastKernelVersion(4, 19, 0)) ||
           (isAtLeastKernelVersion(4, 19, 236) && !isAtLeastKernelVersion(5, 4, 0)) ||
           (isAtLeastKernelVersion(5, 4, 186) && !isAtLeastKernelVersion(5, 10, 0)) ||
           (isAtLeastKernelVersion(5, 10, 107) && !isAtLeastKernelVersion(5, 15, 0)) ||
           isAtLeastKernelVersion(5, 15, 30);
}

// Does the kernel support CONFIG_XFRM_MIGRATE and include the kernel fixes?
bool supportsXfrmMigrate() {
    if (!hasXfrmMigrateKernelFixes()) return false;

    // 5.10+ VINTF requires CONFIG_XFRM_MIGRATE enabled
    if (isAtLeastKernelVersion(5, 10, 0)) return true;

    const std::string wildcardAddr = "::";

    // Expect migration to fail with EINVAL because it is trying to migrate a
    // non-existent SA.
    auto status = XfrmController::ipSecMigrate(
            0 /* resourceId */, AF_INET6, 0 /* direction == out */,
            wildcardAddr /* sourceAddress */, wildcardAddr /* destinationAddress */,
            wildcardAddr /* newSourceAddress */, wildcardAddr /* newDestinationAddress */,
            0 /* xfrmInterfaceId */);

    if (android::netdutils::equalToErrno(status, EINVAL)) {
        return true;
    } else if (android::netdutils::equalToErrno(status, ENOPROTOOPT)) {
        return false;
    } else {
        GTEST_LOG_(WARNING) << "Unexpected migration result: "
                            << android::netdutils::toString(status)
                            << "Assuming XFRM_MIGRATE is enabled.";
        return true;
    }
}

#define SKIP_IF_XFRM_MIGRATE_NOT_SUPPORTED                                     \
    do {                                                                       \
        if (!supportsXfrmMigrate())                                            \
            GTEST_SKIP() << "This test is skipped since xfrm migrate feature " \
                         << "not supported\n";                                 \
    } while (0)

TEST_F(NetdBinderTest, XfrmMigrate) {
    SKIP_IF_XFRM_MIGRATE_NOT_SUPPORTED;

    static const struct TestData {
        const int32_t addrFamily;
        const int32_t newAddrFamily;
        const std::string srcAddr;
        const std::string dstAddr;
        const std::string newSrcAddr;
        const std::string newDstAddr;
    } kTestData[] = {
            {AF_INET, AF_INET, "192.0.2.1", "192.0.2.2", "192.0.2.101", "192.0.2.102"},
            {AF_INET, AF_INET6, "192.0.2.1", "192.0.2.2", "2001:db8::101", "2001:db8::102"},
            {AF_INET6, AF_INET6, "2001:db8::1", "2001:db8::2", "2001:db8::101", "2001:db8::102"},
            {AF_INET6, AF_INET, "2001:db8::1", "2001:db8::2", "192.0.2.101", "192.0.2.102"},
    };

    const int32_t xfrmInterfaceId = 0xFFFE;
    const std::string tunnelDeviceName = "ipsec_test";

    auto status = mNetd->ipSecAddTunnelInterface(tunnelDeviceName, "2001:db8::fe", "2001:db8::ff",
                                                 0x1234 + 50 /* iKey */, 0x1234 + 50 /* oKey */,
                                                 xfrmInterfaceId);

    SCOPED_TRACE(status);
    ASSERT_TRUE(status.isOk());

    for (auto& td : kTestData) {
        const int32_t direction = static_cast<int>(android::net::XfrmDirection::OUT);
        const int32_t resourceId = 0;
        const int32_t spiReq = 123;
        int32_t spi = 0;

        status = mNetd->ipSecAllocateSpi(resourceId, td.srcAddr, td.dstAddr, spiReq, &spi);
        SCOPED_TRACE(status);
        ASSERT_TRUE(status.isOk());

        status = mNetd->ipSecAddSecurityAssociation(
                resourceId, static_cast<int32_t>(android::net::XfrmMode::TUNNEL), td.srcAddr,
                td.dstAddr, 100 /* underlyingNetid */, spiReq, 0 /* markValue */, 0 /* markMask */,
                "digest_null" /* authAlgo */, {} /* authKey */, 0 /* authTruncBits */,
                "ecb(cipher_null)" /* cryptAlgo */, {} /* cryptKey */, 0 /* cryptTruncBits */,
                "" /* aeadAlgo */, {} /* aeadKey */, 0 /* aeadIcvBits */,
                0 /* encapType == ENCAP_NONE */, 0 /* encapLocalPort */, 0 /* encapRemotePort */,
                xfrmInterfaceId);
        SCOPED_TRACE(status);
        ASSERT_TRUE(status.isOk());

        for (int addrFamily : ADDRESS_FAMILIES) {
            // Add a policy
            status = mNetd->ipSecAddSecurityPolicy(resourceId, addrFamily, direction, td.srcAddr,
                                                   td.dstAddr, spiReq, 0 /* markValue */,
                                                   0 /* markMask */, xfrmInterfaceId);
            SCOPED_TRACE(status);
            ASSERT_TRUE(status.isOk());

            // Migrate tunnel mode SA
            android::net::IpSecMigrateInfoParcel parcel;
            parcel.requestId = resourceId;
            parcel.selAddrFamily = addrFamily;
            parcel.direction = direction;
            parcel.oldSourceAddress = td.srcAddr;
            parcel.oldDestinationAddress = td.dstAddr;
            parcel.newSourceAddress = td.newSrcAddr;
            parcel.newDestinationAddress = td.newDstAddr;
            parcel.interfaceId = xfrmInterfaceId;

            status = mNetd->ipSecMigrate(parcel);
            SCOPED_TRACE(status);
            ASSERT_TRUE(status.isOk());
        }

        // Clean up
        status = mNetd->ipSecDeleteSecurityAssociation(resourceId, td.newSrcAddr, td.newDstAddr,
                                                       spiReq, 0 /* markValue */, 0 /* markMask */,
                                                       xfrmInterfaceId);
        SCOPED_TRACE(status);
        ASSERT_TRUE(status.isOk());

        for (int addrFamily : ADDRESS_FAMILIES) {
            status = mNetd->ipSecDeleteSecurityPolicy(resourceId, addrFamily, direction,
                                                      0 /* markValue */, 0 /* markMask */,
                                                      xfrmInterfaceId);
            SCOPED_TRACE(status);
            ASSERT_TRUE(status.isOk());
        }
    }

    // Remove Tunnel Interface.
    status = mNetd->ipSecRemoveTunnelInterface(tunnelDeviceName);
    SCOPED_TRACE(status);
    EXPECT_TRUE(status.isOk());
}
#endif  // INTPTR_MAX != INT32_MAX

static int bandwidthDataSaverEnabled(const char *binary) {
    std::vector<std::string> lines = listIptablesRule(binary, "bw_data_saver");

    // Output looks like this:
    //
    // Chain bw_data_saver (1 references)
    // target     prot opt source               destination
    // RETURN     all  --  0.0.0.0/0            0.0.0.0/0
    //
    // or:
    //
    // Chain bw_data_saver (1 references)
    // target     prot opt source               destination
    // ... possibly connectivity critical packet rules here ...
    // REJECT     all  --  ::/0            ::/0

    EXPECT_GE(lines.size(), 3U);

    if (lines.size() == 3 && StartsWith(lines[2], "RETURN ")) {
        // Data saver disabled.
        return 0;
    }

    size_t minSize = (std::string(binary) == IPTABLES_PATH) ? 3 : 9;

    if (lines.size() >= minSize && StartsWith(lines[lines.size() -1], "REJECT ")) {
        // Data saver enabled.
        return 1;
    }

    return -1;
}

bool enableDataSaver(sp<INetd>& netd, bool enable) {
    TimedOperation op(enable ? " Enabling data saver" : "Disabling data saver");
    bool ret;
    netd->bandwidthEnableDataSaver(enable, &ret);
    return ret;
}

int getDataSaverState() {
    const int enabled4 = bandwidthDataSaverEnabled(IPTABLES_PATH);
    const int enabled6 = bandwidthDataSaverEnabled(IP6TABLES_PATH);
    EXPECT_EQ(enabled4, enabled6);
    EXPECT_NE(-1, enabled4);
    EXPECT_NE(-1, enabled6);
    if (enabled4 != enabled6 || (enabled6 != 0 && enabled6 != 1)) {
        return -1;
    }
    return enabled6;
}

TEST_F(NetdBinderTest, BandwidthEnableDataSaver) {
    const int wasEnabled = getDataSaverState();
    ASSERT_NE(-1, wasEnabled);

    if (wasEnabled) {
        ASSERT_TRUE(enableDataSaver(mNetd, false));
        EXPECT_EQ(0, getDataSaverState());
    }

    ASSERT_TRUE(enableDataSaver(mNetd, false));
    EXPECT_EQ(0, getDataSaverState());

    ASSERT_TRUE(enableDataSaver(mNetd, true));
    EXPECT_EQ(1, getDataSaverState());

    ASSERT_TRUE(enableDataSaver(mNetd, true));
    EXPECT_EQ(1, getDataSaverState());

    if (!wasEnabled) {
        ASSERT_TRUE(enableDataSaver(mNetd, false));
        EXPECT_EQ(0, getDataSaverState());
    }
}

static bool ipRuleExists(const char* ipVersion, const std::string& ipRule) {
    std::vector<std::string> rules = listIpRules(ipVersion);
    return std::find(rules.begin(), rules.end(), ipRule) != rules.end();
}

static bool ipRuleExistsForRange(const uint32_t priority, const UidRangeParcel& range,
                                 const std::string& action, const char* ipVersion,
                                 const char* oif) {
    // Output looks like this:
    //   "<priority>:\tfrom all iif lo oif netdc0ca6 uidrange 500000-500000 lookup netdc0ca6"
    //   "<priority>:\tfrom all fwmark 0x0/0x20000 iif lo uidrange 1000-2000 prohibit"
    std::vector<std::string> rules = listIpRules(ipVersion);

    std::string prefix = StringPrintf("%" PRIu32 ":", priority);
    std::string suffix;
    if (oif) {
        suffix = StringPrintf(" iif lo oif %s uidrange %d-%d %s", oif, range.start, range.stop,
                              action.c_str());
    } else {
        suffix = StringPrintf(" iif lo uidrange %d-%d %s", range.start, range.stop, action.c_str());
    }
    for (const auto& line : rules) {
        if (android::base::StartsWith(line, prefix) && android::base::EndsWith(line, suffix)) {
            return true;
        }
    }
    return false;
}

// Overloads function with oif parameter for VPN rules compare.
static bool ipRuleExistsForRange(const uint32_t priority, const UidRangeParcel& range,
                                 const std::string& action, const char* oif) {
    bool existsIp4 = ipRuleExistsForRange(priority, range, action, IP_RULE_V4, oif);
    bool existsIp6 = ipRuleExistsForRange(priority, range, action, IP_RULE_V6, oif);
    EXPECT_EQ(existsIp4, existsIp6);
    return existsIp4;
}

static bool ipRuleExistsForRange(const uint32_t priority, const UidRangeParcel& range,
                                 const std::string& action) {
    return ipRuleExistsForRange(priority, range, action, nullptr);
}

static void expectRuleForV4AndV6(ExistMode mode, const std::string& rule) {
    for (const auto& ipVersion : {IP_RULE_V4, IP_RULE_V6}) {
        if (mode == ALL_EXIST) {
            EXPECT_TRUE(ipRuleExists(ipVersion, rule));
        } else {
            EXPECT_FALSE(ipRuleExists(ipVersion, rule));
        }
    }
}

static void expectLocalIpRuleExists(ExistMode mode, const std::string& ifName) {
    std::string localIpRule = StringPrintf("%u:\tfrom all fwmark 0x0/0x10000 lookup %s",
                                           RULE_PRIORITY_LOCAL_NETWORK, ifName.c_str());
    expectRuleForV4AndV6(mode, localIpRule);

    std::string dnsMasqRule = StringPrintf("%u:\tfrom all fwmark 0x10063/0x1ffff iif lo lookup %s",
                                           RULE_PRIORITY_EXPLICIT_NETWORK, ifName.c_str());
    expectRuleForV4AndV6(mode, dnsMasqRule);
}

namespace {

UidRangeParcel makeUidRangeParcel(int start, int stop) {
    UidRangeParcel res;
    res.start = start;
    res.stop = stop;

    return res;
}

UidRangeParcel makeUidRangeParcel(int uid) {
    return makeUidRangeParcel(uid, uid);
}

NativeUidRangeConfig makeNativeUidRangeConfig(unsigned netId, std::vector<UidRangeParcel> uidRanges,
                                              int32_t subPriority) {
    NativeUidRangeConfig res;
    res.netId = netId;
    res.uidRanges = std::move(uidRanges);
    res.subPriority = subPriority;

    return res;
}

}  // namespace

TEST_F(NetdBinderTest, NetworkInterfaces) {
    auto config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                          INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_EQ(EEXIST, mNetd->networkCreate(config).serviceSpecificErrorCode());

    config.networkType = NativeNetworkType::VIRTUAL;
    config.secure = true;
    EXPECT_EQ(EEXIST, mNetd->networkCreate(config).serviceSpecificErrorCode());

    config.netId = TEST_NETID2;
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());

    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());
    EXPECT_EQ(EBUSY,
              mNetd->networkAddInterface(TEST_NETID2, sTun.name()).serviceSpecificErrorCode());

    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID2, sTun.name()).isOk());
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID2).isOk());
    EXPECT_EQ(ENONET, mNetd->networkDestroy(TEST_NETID1).serviceSpecificErrorCode());
}

TEST_F(NetdBinderTest, NetworkUidRules) {
    auto config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::VIRTUAL,
                                          INetd::PERMISSION_NONE, true, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_EQ(EEXIST, mNetd->networkCreate(config).serviceSpecificErrorCode());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    std::vector<UidRangeParcel> uidRanges = {makeUidRangeParcel(BASE_UID + 8005, BASE_UID + 8012),
                                             makeUidRangeParcel(BASE_UID + 8090, BASE_UID + 8099)};
    UidRangeParcel otherRange = makeUidRangeParcel(BASE_UID + 8190, BASE_UID + 8299);
    std::string action = StringPrintf("lookup %s", sTun.name().c_str());

    EXPECT_TRUE(mNetd->networkAddUidRanges(TEST_NETID1, uidRanges).isOk());

    EXPECT_TRUE(ipRuleExistsForRange(RULE_PRIORITY_SECURE_VPN, uidRanges[0], action));
    EXPECT_FALSE(ipRuleExistsForRange(RULE_PRIORITY_SECURE_VPN, otherRange, action));
    EXPECT_TRUE(mNetd->networkRemoveUidRanges(TEST_NETID1, uidRanges).isOk());
    EXPECT_FALSE(ipRuleExistsForRange(RULE_PRIORITY_SECURE_VPN, uidRanges[0], action));

    EXPECT_TRUE(mNetd->networkAddUidRanges(TEST_NETID1, uidRanges).isOk());
    EXPECT_TRUE(ipRuleExistsForRange(RULE_PRIORITY_SECURE_VPN, uidRanges[1], action));
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
    EXPECT_FALSE(ipRuleExistsForRange(RULE_PRIORITY_SECURE_VPN, uidRanges[1], action));

    EXPECT_EQ(ENONET, mNetd->networkDestroy(TEST_NETID1).serviceSpecificErrorCode());
}

class LocalNetworkParameterizedTest : public NetdBinderTest,
                                      public testing::WithParamInterface<bool> {};

// Exercise both local and non-local networks
INSTANTIATE_TEST_SUITE_P(LocalNetworkTests, LocalNetworkParameterizedTest, testing::Bool(),
                         [](const testing::TestParamInfo<bool>& info) {
                             return info.param ? "Local" : "NonLocal";
                         });

TEST_P(LocalNetworkParameterizedTest, LocalNetworkUidRules) {
    const bool local = GetParam();
    const auto type = local ? NativeNetworkType::PHYSICAL_LOCAL : NativeNetworkType::PHYSICAL;
    auto config = makeNativeNetworkConfig(TEST_NETID1, type, INetd::PERMISSION_NONE,
                                          false /* secure */, false /* excludeLocalRoutes */);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    expectLocalIpRuleExists(local ? ALL_EXIST : NONE_EXIST, sTun.name());
}

TEST_F(NetdBinderTest, NetworkRejectNonSecureVpn) {
    std::vector<UidRangeParcel> uidRanges = {makeUidRangeParcel(BASE_UID + 150, BASE_UID + 224),
                                             makeUidRangeParcel(BASE_UID + 226, BASE_UID + 300)};
    // Make sure no rules existed before calling commands.
    for (auto const& range : uidRanges) {
        EXPECT_FALSE(ipRuleExistsForRange(RULE_PRIORITY_PROHIBIT_NON_VPN, range, "prohibit"));
    }
    // Create two valid rules.
    ASSERT_TRUE(mNetd->networkRejectNonSecureVpn(true, uidRanges).isOk());
    for (auto const& range : uidRanges) {
        EXPECT_TRUE(ipRuleExistsForRange(RULE_PRIORITY_PROHIBIT_NON_VPN, range, "prohibit"));
    }

    // Remove the rules.
    ASSERT_TRUE(mNetd->networkRejectNonSecureVpn(false, uidRanges).isOk());
    for (auto const& range : uidRanges) {
        EXPECT_FALSE(ipRuleExistsForRange(RULE_PRIORITY_PROHIBIT_NON_VPN, range, "prohibit"));
    }

    // Fail to remove the rules a second time after they are already deleted.
    binder::Status status = mNetd->networkRejectNonSecureVpn(false, uidRanges);
    ASSERT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
    EXPECT_EQ(ENOENT, status.serviceSpecificErrorCode());
}

// Create a socket pair that isLoopbackSocket won't think is local.
void NetdBinderTest::fakeRemoteSocketPair(unique_fd* clientSocket, unique_fd* serverSocket,
                                          unique_fd* acceptedSocket) {
    serverSocket->reset(socket(AF_INET6, SOCK_STREAM | SOCK_CLOEXEC, 0));
    struct sockaddr_in6 server6 = { .sin6_family = AF_INET6, .sin6_addr = sTun.dstAddr() };
    ASSERT_EQ(0, bind(*serverSocket, (struct sockaddr *) &server6, sizeof(server6)));

    socklen_t addrlen = sizeof(server6);
    ASSERT_EQ(0, getsockname(*serverSocket, (struct sockaddr *) &server6, &addrlen));
    ASSERT_EQ(0, listen(*serverSocket, 10));

    clientSocket->reset(socket(AF_INET6, SOCK_STREAM | SOCK_CLOEXEC, 0));
    struct sockaddr_in6 client6 = { .sin6_family = AF_INET6, .sin6_addr = sTun.srcAddr() };
    ASSERT_EQ(0, bind(*clientSocket, (struct sockaddr *) &client6, sizeof(client6)));
    ASSERT_EQ(0, connect(*clientSocket, (struct sockaddr *) &server6, sizeof(server6)));
    ASSERT_EQ(0, getsockname(*clientSocket, (struct sockaddr *) &client6, &addrlen));

    acceptedSocket->reset(
            accept4(*serverSocket, (struct sockaddr*)&server6, &addrlen, SOCK_CLOEXEC));
    ASSERT_NE(-1, *acceptedSocket);

    ASSERT_EQ(0, memcmp(&client6, &server6, sizeof(client6)));
}

void checkSocketpairOpen(int clientSocket, int acceptedSocket) {
    char buf[4096];
    EXPECT_EQ(4, write(clientSocket, "foo", sizeof("foo")));
    EXPECT_EQ(4, read(acceptedSocket, buf, sizeof(buf)));
    EXPECT_EQ(0, memcmp(buf, "foo", sizeof("foo")));
}

void checkSocketpairClosed(int clientSocket, int acceptedSocket) {
    // Check that the client socket was closed with ECONNABORTED.
    int ret = write(clientSocket, "foo", sizeof("foo"));
    int err = errno;
    EXPECT_EQ(-1, ret);
    EXPECT_EQ(ECONNABORTED, err);

    // Check that it sent a RST to the server.
    ret = write(acceptedSocket, "foo", sizeof("foo"));
    err = errno;
    EXPECT_EQ(-1, ret);
    EXPECT_EQ(ECONNRESET, err);
}

TEST_F(NetdBinderTest, SocketDestroy) {
    unique_fd clientSocket, serverSocket, acceptedSocket;
    ASSERT_NO_FATAL_FAILURE(fakeRemoteSocketPair(&clientSocket, &serverSocket, &acceptedSocket));

    // Pick a random UID in the system UID range.
    constexpr int baseUid = AID_APP - 2000;
    static_assert(baseUid > 0, "Not enough UIDs? Please fix this test.");
    int uid = baseUid + 500 + arc4random_uniform(1000);
    EXPECT_EQ(0, fchown(clientSocket, uid, -1));

    // UID ranges that don't contain uid.
    std::vector<UidRangeParcel> uidRanges = {
            makeUidRangeParcel(baseUid + 42, baseUid + 449),
            makeUidRangeParcel(baseUid + 1536, AID_APP - 4),
            makeUidRangeParcel(baseUid + 498, uid - 1),
            makeUidRangeParcel(uid + 1, baseUid + 1520),
    };
    // A skip list that doesn't contain UID.
    std::vector<int32_t> skipUids { baseUid + 123, baseUid + 1600 };

    // Close sockets. Our test socket should be intact.
    EXPECT_TRUE(mNetd->socketDestroy(uidRanges, skipUids).isOk());
    checkSocketpairOpen(clientSocket, acceptedSocket);

    // UID ranges that do contain uid.
    uidRanges = {
            makeUidRangeParcel(baseUid + 42, baseUid + 449),
            makeUidRangeParcel(baseUid + 1536, AID_APP - 4),
            makeUidRangeParcel(baseUid + 498, baseUid + 1520),
    };
    // Add uid to the skip list.
    skipUids.push_back(uid);

    // Close sockets. Our test socket should still be intact because it's in the skip list.
    EXPECT_TRUE(mNetd->socketDestroy(uidRanges, skipUids).isOk());
    checkSocketpairOpen(clientSocket, acceptedSocket);

    // Now remove uid from skipUids, and close sockets. Our test socket should have been closed.
    skipUids.resize(skipUids.size() - 1);
    EXPECT_TRUE(mNetd->socketDestroy(uidRanges, skipUids).isOk());
    checkSocketpairClosed(clientSocket, acceptedSocket);
}

TEST_F(NetdBinderTest, SocketDestroyLinkLocal) {
    // Add the same link-local address to two interfaces.
    const char* kLinkLocalAddress = "fe80::ace:d00d";

    const struct addrinfo hints = {
            .ai_flags = AI_NUMERICHOST,
            .ai_family = AF_INET6,
            .ai_socktype = SOCK_STREAM,
    };

    // The ~ in ~64 enables 'nodad' which makes these operations faster and reduces test flakiness.
    // Unfortunately even with 'nodad' these are still very slightly asynchronous.
    binder::Status status = mNetd->interfaceAddAddress(sTun.name(), kLinkLocalAddress, ~64);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    status = mNetd->interfaceAddAddress(sTun2.name(), kLinkLocalAddress, ~64);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    // Add IPs to two more interfaces - purely to slow things down a little bit more.
    status = mNetd->interfaceAddAddress(sTun3.name(), kLinkLocalAddress, ~64);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    status = mNetd->interfaceAddAddress(sTun4.name(), kLinkLocalAddress, ~64);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    // Bind a listening socket to the address on each of the interfaces.
    // The sockets must be open at the same time, because this test checks that SOCK_DESTROY only
    // destroys the sockets on the interface where the address is deleted.
    struct addrinfo* addrinfoList = nullptr;
    int ret = getaddrinfo(kLinkLocalAddress, nullptr, &hints, &addrinfoList);
    ScopedAddrinfo addrinfoCleanup(addrinfoList);
    ASSERT_EQ(0, ret) << "errno:" << errno;

    socklen_t len = addrinfoList[0].ai_addrlen;
    sockaddr_in6 sin6_1 = *reinterpret_cast<sockaddr_in6*>(addrinfoList[0].ai_addr);
    sockaddr_in6 sin6_2 = sin6_1;
    sockaddr_in6 sin6_3 = sin6_1;
    sockaddr_in6 sin6_4 = sin6_1;
    sin6_1.sin6_scope_id = if_nametoindex(sTun.name().c_str());
    sin6_2.sin6_scope_id = if_nametoindex(sTun2.name().c_str());
    sin6_3.sin6_scope_id = if_nametoindex(sTun3.name().c_str());
    sin6_4.sin6_scope_id = if_nametoindex(sTun4.name().c_str());

    int s1 = socket(AF_INET6, SOCK_STREAM | SOCK_NONBLOCK, 0);
    ASSERT_EQ(0, bind(s1, reinterpret_cast<sockaddr*>(&sin6_1), len)) << "errno:" << errno;
    ASSERT_EQ(0, getsockname(s1, reinterpret_cast<sockaddr*>(&sin6_1), &len)) << "errno:" << errno;
    // getsockname technically writes to len, but sizeof(sockaddr_in6) doesn't change.

    int s2 = socket(AF_INET6, SOCK_STREAM | SOCK_NONBLOCK, 0);
    ASSERT_EQ(0, bind(s2, reinterpret_cast<sockaddr*>(&sin6_2), len)) << "errno:" << errno;
    ASSERT_EQ(0, getsockname(s2, reinterpret_cast<sockaddr*>(&sin6_2), &len)) << "errno:" << errno;

    int s3 = socket(AF_INET6, SOCK_STREAM | SOCK_NONBLOCK, 0);
    ASSERT_EQ(0, bind(s3, reinterpret_cast<sockaddr*>(&sin6_3), len)) << "errno:" << errno;
    ASSERT_EQ(0, getsockname(s3, reinterpret_cast<sockaddr*>(&sin6_3), &len)) << "errno:" << errno;

    int s4 = socket(AF_INET6, SOCK_STREAM | SOCK_NONBLOCK, 0);
    ASSERT_EQ(0, bind(s4, reinterpret_cast<sockaddr*>(&sin6_4), len)) << "errno:" << errno;
    ASSERT_EQ(0, getsockname(s4, reinterpret_cast<sockaddr*>(&sin6_4), &len)) << "errno:" << errno;

    ASSERT_EQ(0, listen(s1, 10)) << "errno:" << errno;
    ASSERT_EQ(0, listen(s2, 10)) << "errno:" << errno;
    ASSERT_EQ(0, listen(s3, 10)) << "errno:" << errno;
    ASSERT_EQ(0, listen(s4, 10)) << "errno:" << errno;

    // Connect one client socket to each and accept the connections.
    int c1 = socket(AF_INET6, SOCK_STREAM, 0);
    int c2 = socket(AF_INET6, SOCK_STREAM, 0);

    // ~0.1% chance the kernel isn't quite done yet with adding the IPs.
    // In such a case these may fail with -1, errno = 101 (ENETUNREACH)
    ASSERT_EQ(0, connect(c1, reinterpret_cast<sockaddr*>(&sin6_1), len)) << "errno:" << errno;
    ASSERT_EQ(0, connect(c2, reinterpret_cast<sockaddr*>(&sin6_2), len)) << "errno:" << errno;
    int a1 = accept(s1, nullptr, 0);
    ASSERT_NE(-1, a1) << "errno:" << errno;
    int a2 = accept(s2, nullptr, 0);
    ASSERT_NE(-1, a2) << "errno:" << errno;

    // Delete the address on sTun2.
    status = mNetd->interfaceDelAddress(sTun2.name(), kLinkLocalAddress, ~64);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    // The client sockets on sTun2 are closed, but the ones on sTun1 remain open.
    char buf[1024];
    EXPECT_EQ(-1, read(c2, buf, sizeof(buf)));
    EXPECT_TRUE(errno == ECONNABORTED || errno == ECONNRESET) << "errno:" << errno;
    // The blocking read above ensures that SOCK_DESTROY has completed.

    EXPECT_EQ(3, write(a1, "foo", 3)) << "errno:" << errno;
    EXPECT_EQ(3, read(c1, buf, sizeof(buf))) << "errno:" << errno;
    EXPECT_EQ(-1, write(a2, "foo", 3));
    EXPECT_TRUE(errno == ECONNABORTED || errno == ECONNRESET) << "errno:" << errno;

    // Check the server sockets too.
    EXPECT_EQ(-1, accept(s1, nullptr, 0));
    EXPECT_EQ(EAGAIN, errno);
    EXPECT_EQ(-1, accept(s2, nullptr, 0));
    EXPECT_EQ(EINVAL, errno);

    close(a1);
    close(a2);

    close(c1);
    close(c2);

    close(s1);
    close(s2);
    close(s3);
    close(s4);
}

namespace {

int netmaskToPrefixLength(const uint8_t *buf, size_t buflen) {
    if (buf == nullptr) return -1;

    int prefixLength = 0;
    bool endOfContiguousBits = false;
    for (unsigned int i = 0; i < buflen; i++) {
        const uint8_t value = buf[i];

        // Bad bit sequence: check for a contiguous set of bits from the high
        // end by verifying that the inverted value + 1 is a power of 2
        // (power of 2 iff. (v & (v - 1)) == 0).
        const uint8_t inverse = ~value + 1;
        if ((inverse & (inverse - 1)) != 0) return -1;

        prefixLength += (value == 0) ? 0 : CHAR_BIT - ffs(value) + 1;

        // Bogus netmask.
        if (endOfContiguousBits && value != 0) return -1;

        if (value != 0xff) endOfContiguousBits = true;
    }

    return prefixLength;
}

template<typename T>
int netmaskToPrefixLength(const T *p) {
    return netmaskToPrefixLength(reinterpret_cast<const uint8_t*>(p), sizeof(T));
}


static bool interfaceHasAddress(
        const std::string &ifname, const char *addrString, int prefixLength) {
    struct addrinfo *addrinfoList = nullptr;

    const struct addrinfo hints = {
        .ai_flags    = AI_NUMERICHOST,
        .ai_family   = AF_UNSPEC,
        .ai_socktype = SOCK_DGRAM,
    };
    if (getaddrinfo(addrString, nullptr, &hints, &addrinfoList) != 0 ||
        addrinfoList == nullptr || addrinfoList->ai_addr == nullptr) {
        return false;
    }
    ScopedAddrinfo addrinfoCleanup(addrinfoList);

    struct ifaddrs *ifaddrsList = nullptr;
    ScopedIfaddrs ifaddrsCleanup(ifaddrsList);

    if (getifaddrs(&ifaddrsList) != 0) {
        return false;
    }

    for (struct ifaddrs *addr = ifaddrsList; addr != nullptr; addr = addr->ifa_next) {
        if (std::string(addr->ifa_name) != ifname ||
            addr->ifa_addr == nullptr ||
            addr->ifa_addr->sa_family != addrinfoList->ai_addr->sa_family) {
            continue;
        }

        switch (addr->ifa_addr->sa_family) {
        case AF_INET: {
            auto *addr4 = reinterpret_cast<const struct sockaddr_in*>(addr->ifa_addr);
            auto *want = reinterpret_cast<const struct sockaddr_in*>(addrinfoList->ai_addr);
            if (memcmp(&addr4->sin_addr, &want->sin_addr, sizeof(want->sin_addr)) != 0) {
                continue;
            }

            if (prefixLength < 0) return true;  // not checking prefix lengths

            if (addr->ifa_netmask == nullptr) return false;
            auto *nm = reinterpret_cast<const struct sockaddr_in*>(addr->ifa_netmask);
            EXPECT_EQ(prefixLength, netmaskToPrefixLength(&nm->sin_addr));
            return (prefixLength == netmaskToPrefixLength(&nm->sin_addr));
        }
        case AF_INET6: {
            auto *addr6 = reinterpret_cast<const struct sockaddr_in6*>(addr->ifa_addr);
            auto *want = reinterpret_cast<const struct sockaddr_in6*>(addrinfoList->ai_addr);
            if (memcmp(&addr6->sin6_addr, &want->sin6_addr, sizeof(want->sin6_addr)) != 0) {
                continue;
            }

            if (prefixLength < 0) return true;  // not checking prefix lengths

            if (addr->ifa_netmask == nullptr) return false;
            auto *nm = reinterpret_cast<const struct sockaddr_in6*>(addr->ifa_netmask);
            EXPECT_EQ(prefixLength, netmaskToPrefixLength(&nm->sin6_addr));
            return (prefixLength == netmaskToPrefixLength(&nm->sin6_addr));
        }
        default:
            // Cannot happen because we have already screened for matching
            // address families at the top of each iteration.
            continue;
        }
    }

    return false;
}

}  // namespace

TEST_F(NetdBinderTest, InterfaceAddRemoveAddress) {
    static const struct TestData {
        const char *addrString;
        const int   prefixLength;
        const int expectAddResult;
        const int expectRemoveResult;
    } kTestData[] = {
            {"192.0.2.1", 24, 0, 0},
            {"192.0.2.2", 25, 0, 0},
            {"192.0.2.3", 32, 0, 0},
            {"192.0.2.4", 33, EINVAL, EADDRNOTAVAIL},
            {"192.not.an.ip", 24, EINVAL, EINVAL},
            {"2001:db8::1", 64, 0, 0},
            {"2001:db8::2", 65, 0, 0},
            {"2001:db8::3", 128, 0, 0},
            {"fe80::1234", 64, 0, 0},
            {"2001:db8::4", 129, EINVAL, EINVAL},
            {"foo:bar::bad", 64, EINVAL, EINVAL},
            {"2001:db8::1/64", 64, EINVAL, EINVAL},
    };

    for (size_t i = 0; i < std::size(kTestData); i++) {
        const auto &td = kTestData[i];

        SCOPED_TRACE(String8::format("Offending IP address %s/%d", td.addrString, td.prefixLength));

        // [1.a] Add the address.
        binder::Status status = mNetd->interfaceAddAddress(
                sTun.name(), td.addrString, td.prefixLength);
        if (td.expectAddResult == 0) {
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        } else {
            ASSERT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            ASSERT_EQ(td.expectAddResult, status.serviceSpecificErrorCode());
        }

        // [1.b] Verify the addition meets the expectation.
        if (td.expectAddResult == 0) {
            EXPECT_TRUE(interfaceHasAddress(sTun.name(), td.addrString, td.prefixLength));
        } else {
            EXPECT_FALSE(interfaceHasAddress(sTun.name(), td.addrString, -1));
        }

        // [2.a] Try to remove the address.  If it was not previously added, removing it fails.
        status = mNetd->interfaceDelAddress(sTun.name(), td.addrString, td.prefixLength);
        if (td.expectRemoveResult == 0) {
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        } else {
            ASSERT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            ASSERT_EQ(td.expectRemoveResult, status.serviceSpecificErrorCode());
        }

        // [2.b] No matter what, the address should not be present.
        EXPECT_FALSE(interfaceHasAddress(sTun.name(), td.addrString, -1));
    }

    // Check that netlink errors are returned correctly.
    // We do this by attempting to create an IPv6 address on an interface that has IPv6 disabled,
    // which returns EACCES.
    TunInterface tun;
    ASSERT_EQ(0, tun.init());
    binder::Status status =
            mNetd->setProcSysNet(INetd::IPV6, INetd::CONF, tun.name(), "disable_ipv6", "1");
    ASSERT_TRUE(status.isOk()) << status.exceptionMessage();
    status = mNetd->interfaceAddAddress(tun.name(), "2001:db8::1", 64);
    EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
    EXPECT_EQ(EACCES, status.serviceSpecificErrorCode());
    tun.destroy();
}

TEST_F(NetdBinderTest, GetProcSysNet) {
    const char* LOOPBACK = "lo";
    static const struct {
        const int ipversion;
        const int which;
        const char* ifname;
        const char* parameter;
        const char* expectedValue;
        const int expectedReturnCode;
    } kTestData[] = {
            {INetd::IPV4, INetd::CONF, LOOPBACK, "arp_ignore", "0", 0},
            {-1, INetd::CONF, sTun.name().c_str(), "arp_ignore", nullptr, EAFNOSUPPORT},
            {INetd::IPV4, -1, sTun.name().c_str(), "arp_ignore", nullptr, EINVAL},
            {INetd::IPV4, INetd::CONF, "..", "conf/lo/arp_ignore", nullptr, EINVAL},
            {INetd::IPV4, INetd::CONF, ".", "lo/arp_ignore", nullptr, EINVAL},
            {INetd::IPV4, INetd::CONF, sTun.name().c_str(), "../all/arp_ignore", nullptr, EINVAL},
            {INetd::IPV6, INetd::NEIGH, LOOPBACK, "ucast_solicit", "3", 0},
    };

    for (size_t i = 0; i < std::size(kTestData); i++) {
        const auto& td = kTestData[i];

        std::string value;
        const binder::Status status =
                mNetd->getProcSysNet(td.ipversion, td.which, td.ifname, td.parameter, &value);

        if (td.expectedReturnCode == 0) {
            SCOPED_TRACE(String8::format("test case %zu should have passed", i));
            EXPECT_EQ(0, status.exceptionCode());
            EXPECT_EQ(0, status.serviceSpecificErrorCode());
            EXPECT_EQ(td.expectedValue, value);
        } else {
            SCOPED_TRACE(String8::format("test case %zu should have failed", i));
            EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            EXPECT_EQ(td.expectedReturnCode, status.serviceSpecificErrorCode());
        }
    }
}

TEST_F(NetdBinderTest, SetProcSysNet) {
    static const struct {
        const int ipversion;
        const int which;
        const char* ifname;
        const char* parameter;
        const char* value;
        const int expectedReturnCode;
    } kTestData[] = {
            {INetd::IPV4, INetd::CONF, sTun.name().c_str(), "arp_ignore", "1", 0},
            {-1, INetd::CONF, sTun.name().c_str(), "arp_ignore", "1", EAFNOSUPPORT},
            {INetd::IPV4, -1, sTun.name().c_str(), "arp_ignore", "1", EINVAL},
            {INetd::IPV4, INetd::CONF, "..", "conf/lo/arp_ignore", "1", EINVAL},
            {INetd::IPV4, INetd::CONF, ".", "lo/arp_ignore", "1", EINVAL},
            {INetd::IPV4, INetd::CONF, sTun.name().c_str(), "../all/arp_ignore", "1", EINVAL},
            {INetd::IPV6, INetd::NEIGH, sTun.name().c_str(), "ucast_solicit", "7", 0},
    };

    for (size_t i = 0; i < std::size(kTestData); i++) {
        const auto& td = kTestData[i];
        const binder::Status status =
                mNetd->setProcSysNet(td.ipversion, td.which, td.ifname, td.parameter, td.value);

        if (td.expectedReturnCode == 0) {
            SCOPED_TRACE(String8::format("test case %zu should have passed", i));
            EXPECT_EQ(0, status.exceptionCode());
            EXPECT_EQ(0, status.serviceSpecificErrorCode());
        } else {
            SCOPED_TRACE(String8::format("test case %zu should have failed", i));
            EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            EXPECT_EQ(td.expectedReturnCode, status.serviceSpecificErrorCode());
        }
    }
}

TEST_F(NetdBinderTest, GetSetProcSysNet) {
    const int ipversion = INetd::IPV6;
    const int category = INetd::NEIGH;
    const std::string& tun = sTun.name();
    const std::string parameter("ucast_solicit");

    std::string value{};
    EXPECT_TRUE(mNetd->getProcSysNet(ipversion, category, tun, parameter, &value).isOk());
    ASSERT_FALSE(value.empty());
    const int ival = std::stoi(value);
    EXPECT_GT(ival, 0);
    // Try doubling the parameter value (always best!).
    EXPECT_TRUE(mNetd->setProcSysNet(ipversion, category, tun, parameter, std::to_string(2 * ival))
            .isOk());
    EXPECT_TRUE(mNetd->getProcSysNet(ipversion, category, tun, parameter, &value).isOk());
    EXPECT_EQ(2 * ival, std::stoi(value));
    // Try resetting the parameter.
    EXPECT_TRUE(mNetd->setProcSysNet(ipversion, category, tun, parameter, std::to_string(ival))
            .isOk());
    EXPECT_TRUE(mNetd->getProcSysNet(ipversion, category, tun, parameter, &value).isOk());
    EXPECT_EQ(ival, std::stoi(value));
}

namespace {

void expectNoTestCounterRules() {
    for (const auto& binary : { IPTABLES_PATH, IP6TABLES_PATH }) {
        std::string command = StringPrintf("%s -w -nvL tetherctrl_counters", binary);
        std::string allRules = Join(runCommand(command), "\n");
        EXPECT_EQ(std::string::npos, allRules.find("netdtest_"));
    }
}

void addTetherCounterValues(const char* path, const std::string& if1, const std::string& if2,
                            int byte, int pkt) {
    runCommand(StringPrintf("%s -w -A tetherctrl_counters -i %s -o %s -j RETURN -c %d %d",
                            path, if1.c_str(), if2.c_str(), pkt, byte));
}

void delTetherCounterValues(const char* path, const std::string& if1, const std::string& if2) {
    runCommand(StringPrintf("%s -w -D tetherctrl_counters -i %s -o %s -j RETURN",
                            path, if1.c_str(), if2.c_str()));
    runCommand(StringPrintf("%s -w -D tetherctrl_counters -i %s -o %s -j RETURN",
                            path, if2.c_str(), if1.c_str()));
}

std::vector<int64_t> getStatsVectorByIf(const std::vector<TetherStatsParcel>& statsVec,
                                        const std::string& iface) {
    for (auto& stats : statsVec) {
        if (stats.iface == iface) {
            return {stats.rxBytes, stats.rxPackets, stats.txBytes, stats.txPackets};
        }
    }
    return {};
}

}  // namespace

TEST_F(NetdBinderTest, TetherGetStats) {
    expectNoTestCounterRules();

    // TODO: fold this into more comprehensive tests once we have binder RPCs for enabling and
    // disabling tethering. We don't check the return value because these commands will fail if
    // tethering is already enabled.
    runCommand(StringPrintf("%s -w -N tetherctrl_counters", IPTABLES_PATH));
    runCommand(StringPrintf("%s -w -N tetherctrl_counters", IP6TABLES_PATH));

    std::string intIface1 = StringPrintf("netdtest_%u", arc4random_uniform(10000));
    std::string intIface2 = StringPrintf("netdtest_%u", arc4random_uniform(10000));
    std::string intIface3 = StringPrintf("netdtest_%u", arc4random_uniform(10000));

    // Ensure we won't use the same interface name, otherwise the test will fail.
    u_int32_t rNumber = arc4random_uniform(10000);
    std::string extIface1 = StringPrintf("netdtest_%u", rNumber);
    std::string extIface2 = StringPrintf("netdtest_%u", rNumber + 1);

    addTetherCounterValues(IPTABLES_PATH,  intIface1, extIface1, 123, 111);
    addTetherCounterValues(IP6TABLES_PATH, intIface1, extIface1, 456,  10);
    addTetherCounterValues(IPTABLES_PATH,  extIface1, intIface1, 321, 222);
    addTetherCounterValues(IP6TABLES_PATH, extIface1, intIface1, 654,  20);
    // RX is from external to internal, and TX is from internal to external.
    // So rxBytes is 321 + 654  = 975, txBytes is 123 + 456 = 579, etc.
    std::vector<int64_t> expected1 = { 975, 242, 579, 121 };

    addTetherCounterValues(IPTABLES_PATH,  intIface2, extIface2, 1000, 333);
    addTetherCounterValues(IP6TABLES_PATH, intIface2, extIface2, 3000,  30);

    addTetherCounterValues(IPTABLES_PATH,  extIface2, intIface2, 2000, 444);
    addTetherCounterValues(IP6TABLES_PATH, extIface2, intIface2, 4000,  40);

    addTetherCounterValues(IP6TABLES_PATH, intIface3, extIface2, 1000,  25);
    addTetherCounterValues(IP6TABLES_PATH, extIface2, intIface3, 2000,  35);
    std::vector<int64_t> expected2 = { 8000, 519, 5000, 388 };

    std::vector<TetherStatsParcel> statsVec;
    binder::Status status = mNetd->tetherGetStats(&statsVec);
    EXPECT_TRUE(status.isOk()) << "Getting tethering stats failed: " << status;

    EXPECT_EQ(expected1, getStatsVectorByIf(statsVec, extIface1));

    EXPECT_EQ(expected2, getStatsVectorByIf(statsVec, extIface2));

    for (const auto& path : { IPTABLES_PATH, IP6TABLES_PATH }) {
        delTetherCounterValues(path, intIface1, extIface1);
        delTetherCounterValues(path, intIface2, extIface2);
        if (strcmp(path, IP6TABLES_PATH) == 0) {
            delTetherCounterValues(path, intIface3, extIface2);
        }
    }

    expectNoTestCounterRules();
}

namespace {

constexpr char IDLETIMER_RAW_PREROUTING[] = "idletimer_raw_PREROUTING";
constexpr char IDLETIMER_MANGLE_POSTROUTING[] = "idletimer_mangle_POSTROUTING";

static std::vector<std::string> listIptablesRuleByTable(const char* binary, const char* table,
                                                        const char* chainName) {
    std::string command = StringPrintf("%s -t %s -w -n -v -L %s", binary, table, chainName);
    return runCommand(command);
}

bool iptablesInterfaceRuleExists(const char* binary, const char* chainName,
                                 const std::string& expectedInterface,
                                 const std::string& expectedRule, const char* table) {
    std::vector<std::string> rules = listIptablesRuleByTable(binary, table, chainName);
    for (const auto& rule : rules) {
        if (rule.find(expectedInterface) != std::string::npos) {
            if (rule.find(expectedRule) != std::string::npos) {
                return true;
            }
        }
    }
    return false;
}

void expectIdletimerInterfaceRuleExists(const std::string& ifname, int timeout,
                                        const std::string& classLabel) {
    std::string IdletimerRule =
            StringPrintf("timeout:%u label:%s send_nl_msg", timeout, classLabel.c_str());
    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_TRUE(iptablesInterfaceRuleExists(binary, IDLETIMER_RAW_PREROUTING, ifname,
                                                IdletimerRule, RAW_TABLE));
        EXPECT_TRUE(iptablesInterfaceRuleExists(binary, IDLETIMER_MANGLE_POSTROUTING, ifname,
                                                IdletimerRule, MANGLE_TABLE));
    }
}

void expectIdletimerInterfaceRuleNotExists(const std::string& ifname, int timeout,
                                           const std::string& classLabel) {
    std::string IdletimerRule =
            StringPrintf("timeout:%u label:%s send_nl_msg", timeout, classLabel.c_str());
    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_FALSE(iptablesInterfaceRuleExists(binary, IDLETIMER_RAW_PREROUTING, ifname,
                                                 IdletimerRule, RAW_TABLE));
        EXPECT_FALSE(iptablesInterfaceRuleExists(binary, IDLETIMER_MANGLE_POSTROUTING, ifname,
                                                 IdletimerRule, MANGLE_TABLE));
    }
}

}  // namespace

TEST_F(NetdBinderTest, IdletimerAddRemoveInterface) {
    // TODO: We will get error in if expectIdletimerInterfaceRuleNotExists if there are the same
    // rule in the table. Because we only check the result after calling remove function. We might
    // check the actual rule which is removed by our function (maybe compare the results between
    // calling function before and after)
    binder::Status status;
    const struct TestData {
        const std::string ifname;
        int32_t timeout;
        const std::string classLabel;
    } idleTestData[] = {
            {"wlan0", 1234, "happyday"},
            {"rmnet_data0", 4567, "friday"},
    };
    for (const auto& td : idleTestData) {
        status = mNetd->idletimerAddInterface(td.ifname, td.timeout, td.classLabel);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        expectIdletimerInterfaceRuleExists(td.ifname, td.timeout, td.classLabel);

        status = mNetd->idletimerRemoveInterface(td.ifname, td.timeout, td.classLabel);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        expectIdletimerInterfaceRuleNotExists(td.ifname, td.timeout, td.classLabel);
    }
}

namespace {

constexpr char STRICT_OUTPUT[] = "st_OUTPUT";
constexpr char STRICT_CLEAR_CAUGHT[] = "st_clear_caught";

// Output looks like this:
//
// IPv4:
//
// throw        dst                         proto static    scope link
// unreachable  dst                         proto static    scope link
//              dst via nextHop dev ifName  proto static
//              dst             dev ifName  proto static    scope link
//
// IPv6:
//
// throw        dst             dev lo      proto static    metric 1024
// unreachable  dst             dev lo      proto static    metric 1024
//              dst via nextHop dev ifName  proto static    metric 1024
//              dst             dev ifName  proto static    metric 1024
std::string ipRoutePrefix(const std::string& ifName, const std::string& dst,
                          const std::string& nextHop) {
    std::string prefixString;

    bool isThrow = nextHop == "throw";
    bool isUnreachable = nextHop == "unreachable";
    bool isDefault = (dst == "0.0.0.0/0" || dst == "::/0");
    bool isIPv6 = dst.find(':') != std::string::npos;
    bool isThrowOrUnreachable = isThrow || isUnreachable;

    if (isThrowOrUnreachable) {
        prefixString += nextHop + " ";
    }

    prefixString += isDefault ? "default" : dst;

    if (!nextHop.empty() && !isThrowOrUnreachable) {
        prefixString += " via " + nextHop;
    }

    if (isThrowOrUnreachable) {
        if (isIPv6) {
            prefixString += " dev lo";
        }
    } else {
        prefixString += " dev " + ifName;
    }

    prefixString += " proto static";

    // IPv6 routes report the metric, IPv4 routes report the scope.
    if (isIPv6) {
        prefixString += " metric 1024";
    } else {
        if (nextHop.empty() || isThrowOrUnreachable) {
            prefixString += " scope link";
        }
    }

    return prefixString;
}

void expectStrictSetUidAccept(const int uid) {
    std::string uidRule = StringPrintf("owner UID match %u", uid);
    std::string perUidChain = StringPrintf("st_clear_caught_%u", uid);
    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_FALSE(iptablesRuleExists(binary, STRICT_OUTPUT, uidRule));
        EXPECT_FALSE(iptablesRuleExists(binary, STRICT_CLEAR_CAUGHT, uidRule));
        EXPECT_EQ(0, iptablesRuleLineLength(binary, perUidChain.c_str()));
    }
}

void expectStrictSetUidLog(const int uid) {
    static const char logRule[] = "st_penalty_log  all";
    std::string uidRule = StringPrintf("owner UID match %u", uid);
    std::string perUidChain = StringPrintf("st_clear_caught_%u", uid);
    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_TRUE(iptablesRuleExists(binary, STRICT_OUTPUT, uidRule));
        EXPECT_TRUE(iptablesRuleExists(binary, STRICT_CLEAR_CAUGHT, uidRule));
        EXPECT_TRUE(iptablesRuleExists(binary, perUidChain.c_str(), logRule));
    }
}

void expectStrictSetUidReject(const int uid) {
    static const char rejectRule[] = "st_penalty_reject  all";
    std::string uidRule = StringPrintf("owner UID match %u", uid);
    std::string perUidChain = StringPrintf("st_clear_caught_%u", uid);
    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_TRUE(iptablesRuleExists(binary, STRICT_OUTPUT, uidRule));
        EXPECT_TRUE(iptablesRuleExists(binary, STRICT_CLEAR_CAUGHT, uidRule));
        EXPECT_TRUE(iptablesRuleExists(binary, perUidChain.c_str(), rejectRule));
    }
}

std::vector<std::string> ipRouteSubstrings(const std::string& ifName, const std::string& dst,
                                           const std::string& nextHop, const std::string& mtu) {
    std::vector<std::string> routeSubstrings;

    routeSubstrings.push_back(ipRoutePrefix(ifName, dst, nextHop));

    if (!mtu.empty()) {
        // Add separate substring to match mtu value.
        // This is needed because on some devices "error -11"/"error -113" appears between ip prefix
        // and mtu for throw/unreachable routes.
        routeSubstrings.push_back("mtu " + mtu);
    }

    return routeSubstrings;
}

void expectNetworkRouteDoesNotExistWithMtu(const char* ipVersion, const std::string& ifName,
                                           const std::string& dst, const std::string& nextHop,
                                           const std::string& mtu, const char* table) {
    std::vector<std::string> routeSubstrings = ipRouteSubstrings(ifName, dst, nextHop, mtu);
    EXPECT_FALSE(ipRouteExists(ipVersion, table, routeSubstrings))
            << "Found unexpected route [" << Join(routeSubstrings, ", ") << "] in table " << table;
}

void expectNetworkRouteExistsWithMtu(const char* ipVersion, const std::string& ifName,
                                     const std::string& dst, const std::string& nextHop,
                                     const std::string& mtu, const char* table) {
    std::vector<std::string> routeSubstrings = ipRouteSubstrings(ifName, dst, nextHop, mtu);
    EXPECT_TRUE(ipRouteExists(ipVersion, table, routeSubstrings))
            << "Couldn't find route to " << dst << ": [" << Join(routeSubstrings, ", ")
            << "] in table " << table;
}

void expectVpnLocalExclusionRuleExists(const std::string& ifName, bool expectExists) {
    std::string tableName = std::string(ifName + "_local");
    // Check if rule exists
    std::string vpnLocalExclusionRule =
            StringPrintf("%d:\tfrom all fwmark 0x0/0x10000 iif lo lookup %s",
                         RULE_PRIORITY_LOCAL_ROUTES, tableName.c_str());
    for (const auto& ipVersion : {IP_RULE_V4, IP_RULE_V6}) {
        EXPECT_EQ(expectExists, ipRuleExists(ipVersion, vpnLocalExclusionRule));
    }
}

void expectNetworkRouteExists(const char* ipVersion, const std::string& ifName,
                              const std::string& dst, const std::string& nextHop,
                              const char* table) {
    expectNetworkRouteExistsWithMtu(ipVersion, ifName, dst, nextHop, "", table);
}

void expectNetworkRouteDoesNotExist(const char* ipVersion, const std::string& ifName,
                                    const std::string& dst, const std::string& nextHop,
                                    const char* table) {
    expectNetworkRouteDoesNotExistWithMtu(ipVersion, ifName, dst, nextHop, "", table);
}

void expectNetworkDefaultIpRuleExists(const char* ifName) {
    std::string networkDefaultRule =
            StringPrintf("%u:\tfrom all fwmark 0x0/0xffff iif lo lookup %s",
                         RULE_PRIORITY_DEFAULT_NETWORK, ifName);

    expectRuleForV4AndV6(ALL_EXIST, networkDefaultRule);
}

void expectNetworkDefaultIpRuleDoesNotExist() {
    std::string networkDefaultRule =
            StringPrintf("%u:\tfrom all fwmark 0x0/0xffff iif lo", RULE_PRIORITY_DEFAULT_NETWORK);

    for (const auto& ipVersion : {IP_RULE_V4, IP_RULE_V6}) {
        std::vector<std::string> rules = listIpRules(ipVersion);
        for (const auto& line : rules) {
            if (android::base::StartsWith(line, networkDefaultRule)) {
                FAIL();
            }
        }
    }
}

void expectNetworkPermissionIpRuleExists(const char* ifName, int permission) {
    std::string networkPermissionRule = "";
    switch (permission) {
        case INetd::PERMISSION_NONE:
            networkPermissionRule =
                    StringPrintf("%u:\tfrom all fwmark 0x1ffdd/0x1ffff iif lo lookup %s",
                                 RULE_PRIORITY_EXPLICIT_NETWORK, ifName);
            break;
        case INetd::PERMISSION_NETWORK:
            networkPermissionRule =
                    StringPrintf("%u:\tfrom all fwmark 0x5ffdd/0x5ffff iif lo lookup %s",
                                 RULE_PRIORITY_EXPLICIT_NETWORK, ifName);
            break;
        case INetd::PERMISSION_SYSTEM:
            networkPermissionRule =
                    StringPrintf("%u:\tfrom all fwmark 0xdffdd/0xdffff iif lo lookup %s",
                                 RULE_PRIORITY_EXPLICIT_NETWORK, ifName);
            break;
    }

    expectRuleForV4AndV6(ALL_EXIST, networkPermissionRule);
}

void expectNetworkPermissionIptablesRuleExists(const char* ifName, int permission) {
    static const char ROUTECTRL_INPUT[] = "routectrl_mangle_INPUT";
    std::string networkIncomingPacketMarkRule = "";
    switch (permission) {
        case INetd::PERMISSION_NONE:
            networkIncomingPacketMarkRule = "MARK xset 0x3ffdd/0xffefffff";
            break;
        case INetd::PERMISSION_NETWORK:
            networkIncomingPacketMarkRule = "MARK xset 0x7ffdd/0xffefffff";
            break;
        case INetd::PERMISSION_SYSTEM:
            networkIncomingPacketMarkRule = "MARK xset 0xfffdd/0xffefffff";
            break;
    }

    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_TRUE(iptablesInterfaceRuleExists(binary, ROUTECTRL_INPUT, ifName,
                                                networkIncomingPacketMarkRule, MANGLE_TABLE));
    }
}

}  // namespace

TEST_F(NetdBinderTest, StrictSetUidCleartextPenalty) {
    binder::Status status;
    int32_t uid = randomUid();

    // setUidCleartextPenalty Policy:Log with randomUid
    status = mNetd->strictUidCleartextPenalty(uid, INetd::PENALTY_POLICY_LOG);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectStrictSetUidLog(uid);

    // setUidCleartextPenalty Policy:Accept with randomUid
    status = mNetd->strictUidCleartextPenalty(uid, INetd::PENALTY_POLICY_ACCEPT);
    expectStrictSetUidAccept(uid);

    // setUidCleartextPenalty Policy:Reject with randomUid
    status = mNetd->strictUidCleartextPenalty(uid, INetd::PENALTY_POLICY_REJECT);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectStrictSetUidReject(uid);

    // setUidCleartextPenalty Policy:Accept with randomUid
    status = mNetd->strictUidCleartextPenalty(uid, INetd::PENALTY_POLICY_ACCEPT);
    expectStrictSetUidAccept(uid);

    // test wrong policy
    int32_t wrongPolicy = -123;
    status = mNetd->strictUidCleartextPenalty(uid, wrongPolicy);
    EXPECT_EQ(EINVAL, status.serviceSpecificErrorCode());
}

namespace {

std::vector<std::string> tryToFindProcesses(const std::string& processName, uint32_t maxTries = 1,
                                            uint32_t intervalMs = 50) {
    // Output looks like:(clatd)
    // clat          4963   850 1 12:16:51 ?     00:00:00 clatd-netd10a88 -i netd10a88 ...
    // ...
    // root          5221  5219 0 12:18:12 ?     00:00:00 sh -c ps -Af | grep ' clatd-netdcc1a0'

    // (dnsmasq)
    // dns_tether    4620   792 0 16:51:28 ?     00:00:00 dnsmasq --keep-in-foreground ...

    if (maxTries == 0) return {};

    std::string cmd = StringPrintf("ps -Af | grep '[0-9] %s'", processName.c_str());
    std::vector<std::string> result;
    for (uint32_t run = 1;;) {
        result = runCommand(cmd);
        if (result.size() || ++run > maxTries) {
            break;
        }

        usleep(intervalMs * 1000);
    }
    return result;
}

void expectProcessExists(const std::string& processName) {
    EXPECT_EQ(1U, tryToFindProcesses(processName, 5 /*maxTries*/).size());
}

void expectProcessDoesNotExist(const std::string& processName) {
    EXPECT_FALSE(tryToFindProcesses(processName).size());
}

}  // namespace

TEST_F(NetdBinderTest, NetworkAddRemoveRouteToLocalExcludeTable) {
    static const struct {
        const char* ipVersion;
        const char* testDest;
        const char* testNextHop;
        const bool expectInLocalTable;
    } kTestData[] = {{IP_RULE_V6, "::/0", "fe80::", false},
                     {IP_RULE_V6, "::/0", "", false},
                     {IP_RULE_V6, "2001:db8:cafe::/64", "fe80::", false},
                     {IP_RULE_V6, "fe80::/64", "", true},
                     {IP_RULE_V6, "2001:db8:cafe::/48", "", true},
                     {IP_RULE_V6, "2001:db8:cafe::/64", "unreachable", false},
                     {IP_RULE_V6, "2001:db8:ca00::/40", "", true},
                     {IP_RULE_V4, "0.0.0.0/0", "10.251.10.1", false},
                     {IP_RULE_V4, "192.1.0.0/16", "", false},
                     {IP_RULE_V4, "192.168.0.0/15", "", false},
                     {IP_RULE_V4, "192.168.0.0/16", "", true},
                     {IP_RULE_V4, "192.168.0.0/24", "", true},
                     {IP_RULE_V4, "100.1.0.0/16", "", false},
                     {IP_RULE_V4, "100.0.0.0/8", "", false},
                     {IP_RULE_V4, "100.64.0.0/10", "", true},
                     {IP_RULE_V4, "100.64.0.0/16", "", true},
                     {IP_RULE_V4, "100.64.0.0/10", "throw", false},
                     {IP_RULE_V4, "172.0.0.0/8", "", false},
                     {IP_RULE_V4, "172.16.0.0/12", "", true},
                     {IP_RULE_V4, "172.16.0.0/16", "", true},
                     {IP_RULE_V4, "172.16.0.0/12", "unreachable", false},
                     {IP_RULE_V4, "172.32.0.0/12", "", false},
                     {IP_RULE_V4, "169.0.0.0/8", "", false},
                     {IP_RULE_V4, "169.254.0.0/16", "", true},
                     {IP_RULE_V4, "169.254.0.0/20", "", true},
                     {IP_RULE_V4, "169.254.3.0/24", "", true},
                     {IP_RULE_V4, "170.254.0.0/16", "", false},
                     {IP_RULE_V4, "10.0.0.0/8", "", true},
                     {IP_RULE_V4, "10.0.0.0/7", "", false},
                     {IP_RULE_V4, "10.0.0.0/16", "", true},
                     {IP_RULE_V4, "10.251.0.0/16", "", true},
                     {IP_RULE_V4, "10.251.250.0/24", "", true},
                     {IP_RULE_V4, "10.251.10.2/31", "throw", false},
                     {IP_RULE_V4, "10.251.10.2/31", "unreachable", false}};

    // To ensure that the nexthops for the above are reachable.
    // Otherwise, the routes can't be created.
    static const struct {
        const char* ipVersion;
        const char* testDest;
        const char* testNextHop;
    } kDirectlyConnectedRoutes[] = {
            {IP_RULE_V4, "10.251.10.0/30", ""},
            {IP_RULE_V6, "2001:db8::/32", ""},
    };

    // This should ba aligned with V4_FIXED_LOCAL_PREFIXES in system/netd/server/RouteController.cpp
    // An expandable array for fixed local prefix though it's only one element now.
    static const char* kV4LocalPrefixes[] = {"224.0.0.0/24"};

    // Add test physical network
    const auto& config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    // Get current default network NetId
    binder::Status status = mNetd->networkGetDefault(&mStoredDefaultNetwork);
    ASSERT_TRUE(status.isOk()) << status.exceptionMessage();

    // Set default network
    EXPECT_TRUE(mNetd->networkSetDefault(TEST_NETID1).isOk());

    std::string localTableName = std::string(sTun.name() + "_local");

    // Verify the fixed routes exist in the local table.
    for (size_t i = 0; i < std::size(kV4LocalPrefixes); i++) {
        expectNetworkRouteExists(IP_RULE_V4, sTun.name(), kV4LocalPrefixes[i], "",
                                 localTableName.c_str());
    }

    // Set up link-local routes for connectivity to the "gateway"
    for (size_t i = 0; i < std::size(kDirectlyConnectedRoutes); i++) {
        const auto& td = kDirectlyConnectedRoutes[i];

        binder::Status status =
                mNetd->networkAddRoute(TEST_NETID1, sTun.name(), td.testDest, td.testNextHop);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        expectNetworkRouteExists(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                 sTun.name().c_str());
        // Verify routes in local table
        expectNetworkRouteExists(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                 localTableName.c_str());
    }

    for (size_t i = 0; i < std::size(kTestData); i++) {
        const auto& td = kTestData[i];
        SCOPED_TRACE(StringPrintf("case ip:%s, dest:%s, nexHop:%s, expect:%d", td.ipVersion,
                                  td.testDest, td.testNextHop, td.expectInLocalTable));
        binder::Status status =
                mNetd->networkAddRoute(TEST_NETID1, sTun.name(), td.testDest, td.testNextHop);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        // Verify routes in local table
        if (td.expectInLocalTable) {
            expectNetworkRouteExists(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                     localTableName.c_str());
        } else {
            expectNetworkRouteDoesNotExist(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                           localTableName.c_str());
        }

        status = mNetd->networkRemoveRoute(TEST_NETID1, sTun.name(), td.testDest, td.testNextHop);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        expectNetworkRouteDoesNotExist(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                       localTableName.c_str());
    }

    for (size_t i = 0; i < std::size(kDirectlyConnectedRoutes); i++) {
        const auto& td = kDirectlyConnectedRoutes[i];
        status = mNetd->networkRemoveRoute(TEST_NETID1, sTun.name(), td.testDest, td.testNextHop);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    }

    // Set default network back
    status = mNetd->networkSetDefault(mStoredDefaultNetwork);

    // Remove test physical network
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

namespace {

bool getIpfwdV4Enable() {
    static const char ipv4IpfwdCmd[] = "cat /proc/sys/net/ipv4/ip_forward";
    std::vector<std::string> result = runCommand(ipv4IpfwdCmd);
    EXPECT_TRUE(!result.empty());
    int v4Enable = std::stoi(result[0]);
    return v4Enable;
}

bool getIpfwdV6Enable() {
    static const char ipv6IpfwdCmd[] = "cat /proc/sys/net/ipv6/conf/all/forwarding";
    std::vector<std::string> result = runCommand(ipv6IpfwdCmd);
    EXPECT_TRUE(!result.empty());
    int v6Enable = std::stoi(result[0]);
    return v6Enable;
}

void expectIpfwdEnable(bool enable) {
    int enableIPv4 = getIpfwdV4Enable();
    int enableIPv6 = getIpfwdV6Enable();
    EXPECT_EQ(enable, enableIPv4);
    EXPECT_EQ(enable, enableIPv6);
}

bool ipRuleIpfwdExists(const char* ipVersion, const std::string& ipfwdRule) {
    std::vector<std::string> rules = listIpRules(ipVersion);
    for (const auto& rule : rules) {
        if (rule.find(ipfwdRule) != std::string::npos) {
            return true;
        }
    }
    return false;
}

void expectIpfwdRuleExists(const char* fromIf, const char* toIf) {
    std::string ipfwdRule =
            StringPrintf("%u:\tfrom all iif %s lookup %s", RULE_PRIORITY_TETHERING, fromIf, toIf);

    for (const auto& ipVersion : {IP_RULE_V4, IP_RULE_V6}) {
        EXPECT_TRUE(ipRuleIpfwdExists(ipVersion, ipfwdRule));
    }
}

void expectIpfwdRuleNotExists(const char* fromIf, const char* toIf) {
    std::string ipfwdRule =
            StringPrintf("%u:\tfrom all iif %s lookup %s", RULE_PRIORITY_TETHERING, fromIf, toIf);

    for (const auto& ipVersion : {IP_RULE_V4, IP_RULE_V6}) {
        EXPECT_FALSE(ipRuleIpfwdExists(ipVersion, ipfwdRule));
    }
}

}  // namespace

TEST_F(NetdBinderTest, TestIpfwdEnableDisableStatusForwarding) {
    // Get ipfwd requester list from Netd
    std::vector<std::string> requesterList;
    binder::Status status = mNetd->ipfwdGetRequesterList(&requesterList);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    bool ipfwdEnabled;
    if (requesterList.size() == 0) {
        // No requester in Netd, ipfwd should be disabled
        // So add one test requester and verify
        status = mNetd->ipfwdEnableForwarding("TestRequester");
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

        expectIpfwdEnable(true);
        status = mNetd->ipfwdEnabled(&ipfwdEnabled);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        EXPECT_TRUE(ipfwdEnabled);

        // Remove test one, verify again
        status = mNetd->ipfwdDisableForwarding("TestRequester");
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

        expectIpfwdEnable(false);
        status = mNetd->ipfwdEnabled(&ipfwdEnabled);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        EXPECT_FALSE(ipfwdEnabled);
    } else {
        // Disable all requesters
        for (const auto& requester : requesterList) {
            status = mNetd->ipfwdDisableForwarding(requester);
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        }

        // After disable all requester, ipfwd should be disabled
        expectIpfwdEnable(false);
        status = mNetd->ipfwdEnabled(&ipfwdEnabled);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        EXPECT_FALSE(ipfwdEnabled);

        // Enable them back
        for (const auto& requester : requesterList) {
            status = mNetd->ipfwdEnableForwarding(requester);
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        }

        // ipfwd should be enabled
        expectIpfwdEnable(true);
        status = mNetd->ipfwdEnabled(&ipfwdEnabled);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        EXPECT_TRUE(ipfwdEnabled);
    }
}

TEST_F(NetdBinderTest, TestIpfwdAddRemoveInterfaceForward) {
    // Add test physical network
    auto config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                          INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    config.netId = TEST_NETID2;
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID2, sTun2.name()).isOk());

    binder::Status status = mNetd->ipfwdAddInterfaceForward(sTun.name(), sTun2.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectIpfwdRuleExists(sTun.name().c_str(), sTun2.name().c_str());

    status = mNetd->ipfwdRemoveInterfaceForward(sTun.name(), sTun2.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectIpfwdRuleNotExists(sTun.name().c_str(), sTun2.name().c_str());
}

namespace {

constexpr char BANDWIDTH_INPUT[] = "bw_INPUT";
constexpr char BANDWIDTH_OUTPUT[] = "bw_OUTPUT";
constexpr char BANDWIDTH_FORWARD[] = "bw_FORWARD";
constexpr char BANDWIDTH_NAUGHTY[] = "bw_penalty_box";
constexpr char BANDWIDTH_ALERT[] = "bw_global_alert";

// TODO: Move iptablesTargetsExists and listIptablesRuleByTable to the top.
//       Use either a std::vector<std::string> of things to match, or a variadic function.
bool iptablesTargetsExists(const char* binary, int expectedCount, const char* table,
                           const char* chainName, const std::string& expectedTargetA,
                           const std::string& expectedTargetB) {
    std::vector<std::string> rules = listIptablesRuleByTable(binary, table, chainName);
    int matchCount = 0;

    for (const auto& rule : rules) {
        if (rule.find(expectedTargetA) != std::string::npos) {
            if (rule.find(expectedTargetB) != std::string::npos) {
                matchCount++;
            }
        }
    }
    return matchCount == expectedCount;
}

void expectXtQuotaValueEqual(const char* ifname, long quotaBytes) {
    std::string path = StringPrintf("/proc/net/xt_quota/%s", ifname);
    std::string result = "";

    EXPECT_TRUE(ReadFileToString(path, &result));
    // Quota value might be decreased while matching packets
    EXPECT_GE(quotaBytes, std::stol(Trim(result)));
}

void expectBandwidthInterfaceQuotaRuleExists(const char* ifname, long quotaBytes) {
    std::string BANDWIDTH_COSTLY_IF = StringPrintf("bw_costly_%s", ifname);
    std::string quotaRule = StringPrintf("quota %s", ifname);

    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_TRUE(iptablesTargetsExists(binary, 1, FILTER_TABLE, BANDWIDTH_INPUT, ifname,
                                          BANDWIDTH_COSTLY_IF));
        EXPECT_TRUE(iptablesTargetsExists(binary, 1, FILTER_TABLE, BANDWIDTH_OUTPUT, ifname,
                                          BANDWIDTH_COSTLY_IF));
        EXPECT_TRUE(iptablesTargetsExists(binary, 2, FILTER_TABLE, BANDWIDTH_FORWARD, ifname,
                                          BANDWIDTH_COSTLY_IF));
        EXPECT_TRUE(iptablesRuleExists(binary, BANDWIDTH_COSTLY_IF.c_str(), BANDWIDTH_NAUGHTY));
        EXPECT_TRUE(iptablesRuleExists(binary, BANDWIDTH_COSTLY_IF.c_str(), quotaRule));
    }
    expectXtQuotaValueEqual(ifname, quotaBytes);
}

void expectBandwidthInterfaceQuotaRuleDoesNotExist(const char* ifname) {
    std::string BANDWIDTH_COSTLY_IF = StringPrintf("bw_costly_%s", ifname);
    std::string quotaRule = StringPrintf("quota %s", ifname);

    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_FALSE(iptablesTargetsExists(binary, 1, FILTER_TABLE, BANDWIDTH_INPUT, ifname,
                                           BANDWIDTH_COSTLY_IF));
        EXPECT_FALSE(iptablesTargetsExists(binary, 1, FILTER_TABLE, BANDWIDTH_OUTPUT, ifname,
                                           BANDWIDTH_COSTLY_IF));
        EXPECT_FALSE(iptablesTargetsExists(binary, 2, FILTER_TABLE, BANDWIDTH_FORWARD, ifname,
                                           BANDWIDTH_COSTLY_IF));
        EXPECT_FALSE(iptablesRuleExists(binary, BANDWIDTH_COSTLY_IF.c_str(), BANDWIDTH_NAUGHTY));
        EXPECT_FALSE(iptablesRuleExists(binary, BANDWIDTH_COSTLY_IF.c_str(), quotaRule));
    }
}

void expectBandwidthInterfaceAlertRuleExists(const char* ifname, long alertBytes) {
    std::string BANDWIDTH_COSTLY_IF = StringPrintf("bw_costly_%s", ifname);
    std::string alertRule = StringPrintf("quota %sAlert", ifname);
    std::string alertName = StringPrintf("%sAlert", ifname);

    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_TRUE(iptablesRuleExists(binary, BANDWIDTH_COSTLY_IF.c_str(), alertRule));
    }
    expectXtQuotaValueEqual(alertName.c_str(), alertBytes);
}

void expectBandwidthInterfaceAlertRuleDoesNotExist(const char* ifname) {
    std::string BANDWIDTH_COSTLY_IF = StringPrintf("bw_costly_%s", ifname);
    std::string alertRule = StringPrintf("quota %sAlert", ifname);

    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_FALSE(iptablesRuleExists(binary, BANDWIDTH_COSTLY_IF.c_str(), alertRule));
    }
}

void expectBandwidthGlobalAlertRuleExists(long alertBytes) {
    static const char globalAlertRule[] = "quota globalAlert";
    static const char globalAlertName[] = "globalAlert";

    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_TRUE(iptablesRuleExists(binary, BANDWIDTH_ALERT, globalAlertRule));
    }
    expectXtQuotaValueEqual(globalAlertName, alertBytes);
}

}  // namespace

TEST_F(NetdBinderTest, BandwidthSetRemoveInterfaceQuota) {
    long testQuotaBytes = 5550;

    // Add test physical network
    const auto& config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    binder::Status status = mNetd->bandwidthSetInterfaceQuota(sTun.name(), testQuotaBytes);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectBandwidthInterfaceQuotaRuleExists(sTun.name().c_str(), testQuotaBytes);

    status = mNetd->bandwidthRemoveInterfaceQuota(sTun.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectBandwidthInterfaceQuotaRuleDoesNotExist(sTun.name().c_str());

    // Remove test physical network
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

TEST_F(NetdBinderTest, BandwidthSetRemoveInterfaceAlert) {
    long testAlertBytes = 373;
    // Add test physical network
    const auto& config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());
    // Need to have a prior interface quota set to set an alert
    binder::Status status = mNetd->bandwidthSetInterfaceQuota(sTun.name(), testAlertBytes);
    status = mNetd->bandwidthSetInterfaceAlert(sTun.name(), testAlertBytes);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectBandwidthInterfaceAlertRuleExists(sTun.name().c_str(), testAlertBytes);

    status = mNetd->bandwidthRemoveInterfaceAlert(sTun.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectBandwidthInterfaceAlertRuleDoesNotExist(sTun.name().c_str());

    // Remove interface quota
    status = mNetd->bandwidthRemoveInterfaceQuota(sTun.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectBandwidthInterfaceQuotaRuleDoesNotExist(sTun.name().c_str());

    // Remove test physical network
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

TEST_F(NetdBinderTest, BandwidthSetGlobalAlert) {
    int64_t testAlertBytes = 2097200;

    binder::Status status = mNetd->bandwidthSetGlobalAlert(testAlertBytes);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectBandwidthGlobalAlertRuleExists(testAlertBytes);

    testAlertBytes = 2098230;
    status = mNetd->bandwidthSetGlobalAlert(testAlertBytes);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectBandwidthGlobalAlertRuleExists(testAlertBytes);
}

TEST_F(NetdBinderTest, NetworkAddRemoveRouteUserPermission) {
    static const struct {
        const char* ipVersion;
        const char* testDest;
        const char* testNextHop;
        const bool expectSuccess;
    } kTestData[] = {
            {IP_RULE_V4, "0.0.0.0/0", "", true},
            {IP_RULE_V4, "0.0.0.0/0", "10.251.10.0", true},
            {IP_RULE_V4, "10.251.0.0/16", "", true},
            {IP_RULE_V4, "10.251.0.0/16", "10.251.10.0", true},
            {IP_RULE_V4, "10.251.0.0/16", "fe80::/64", false},
            {IP_RULE_V6, "::/0", "", true},
            {IP_RULE_V6, "::/0", "2001:db8::", true},
            {IP_RULE_V6, "2001:db8:cafe::/64", "2001:db8::", true},
            {IP_RULE_V4, "fe80::/64", "0.0.0.0", false},
            {IP_RULE_V4, "10.251.10.2/31", "throw", true},
            {IP_RULE_V4, "10.251.10.2/31", "unreachable", true},
            {IP_RULE_V4, "0.0.0.0/0", "throw", true},
            {IP_RULE_V4, "0.0.0.0/0", "unreachable", true},
            {IP_RULE_V6, "::/0", "throw", true},
            {IP_RULE_V6, "::/0", "unreachable", true},
            {IP_RULE_V6, "2001:db8:cafe::/64", "throw", true},
            {IP_RULE_V6, "2001:db8:cafe::/64", "unreachable", true},
    };

    static const struct {
        const char* ipVersion;
        const char* testDest;
        const char* testNextHop;
    } kTestDataWithNextHop[] = {
            {IP_RULE_V4, "10.251.10.0/30", ""},
            {IP_RULE_V6, "2001:db8::/32", ""},
    };

    static const char testTableLegacySystem[] = "legacy_system";
    static const char testTableLegacyNetwork[] = "legacy_network";
    const int testUid = randomUid();
    const std::vector<int32_t> testUids = {testUid};

    // Add test physical network
    const auto& config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    // Setup route for testing nextHop
    for (size_t i = 0; i < std::size(kTestDataWithNextHop); i++) {
        const auto& td = kTestDataWithNextHop[i];

        // All route for test tun will disappear once the tun interface is deleted.
        binder::Status status =
                mNetd->networkAddRoute(TEST_NETID1, sTun.name(), td.testDest, td.testNextHop);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        expectNetworkRouteExists(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                 sTun.name().c_str());

        // Add system permission for test uid, setup route in legacy system table.
        EXPECT_TRUE(mNetd->networkSetPermissionForUser(INetd::PERMISSION_SYSTEM, testUids).isOk());

        status = mNetd->networkAddLegacyRoute(TEST_NETID1, sTun.name(), td.testDest, td.testNextHop,
                                              testUid);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        expectNetworkRouteExists(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                 testTableLegacySystem);

        // Remove system permission for test uid, setup route in legacy network table.
        EXPECT_TRUE(mNetd->networkClearPermissionForUser(testUids).isOk());

        status = mNetd->networkAddLegacyRoute(TEST_NETID1, sTun.name(), td.testDest, td.testNextHop,
                                              testUid);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        expectNetworkRouteExists(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                 testTableLegacyNetwork);
    }

    for (size_t i = 0; i < std::size(kTestData); i++) {
        const auto& td = kTestData[i];

        binder::Status status =
                mNetd->networkAddRoute(TEST_NETID1, sTun.name(), td.testDest, td.testNextHop);
        if (td.expectSuccess) {
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
            expectNetworkRouteExists(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                     sTun.name().c_str());
        } else {
            EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            EXPECT_NE(0, status.serviceSpecificErrorCode());
        }

        status = mNetd->networkRemoveRoute(TEST_NETID1, sTun.name(), td.testDest, td.testNextHop);
        if (td.expectSuccess) {
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
            expectNetworkRouteDoesNotExist(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                           sTun.name().c_str());
        } else {
            EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            EXPECT_NE(0, status.serviceSpecificErrorCode());
        }

        // Add system permission for test uid, route will be added into legacy system table.
        EXPECT_TRUE(mNetd->networkSetPermissionForUser(INetd::PERMISSION_SYSTEM, testUids).isOk());

        status = mNetd->networkAddLegacyRoute(TEST_NETID1, sTun.name(), td.testDest, td.testNextHop,
                                              testUid);
        if (td.expectSuccess) {
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
            expectNetworkRouteExists(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                     testTableLegacySystem);
        } else {
            EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            EXPECT_NE(0, status.serviceSpecificErrorCode());
        }

        status = mNetd->networkRemoveLegacyRoute(TEST_NETID1, sTun.name(), td.testDest,
                                                 td.testNextHop, testUid);
        if (td.expectSuccess) {
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
            expectNetworkRouteDoesNotExist(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                           testTableLegacySystem);
        } else {
            EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            EXPECT_NE(0, status.serviceSpecificErrorCode());
        }

        // Remove system permission for test uid, route will be added into legacy network table.
        EXPECT_TRUE(mNetd->networkClearPermissionForUser(testUids).isOk());

        status = mNetd->networkAddLegacyRoute(TEST_NETID1, sTun.name(), td.testDest, td.testNextHop,
                                              testUid);
        if (td.expectSuccess) {
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
            expectNetworkRouteExists(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                     testTableLegacyNetwork);
        } else {
            EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            EXPECT_NE(0, status.serviceSpecificErrorCode());
        }

        status = mNetd->networkRemoveLegacyRoute(TEST_NETID1, sTun.name(), td.testDest,
                                                 td.testNextHop, testUid);
        if (td.expectSuccess) {
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
            expectNetworkRouteDoesNotExist(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                           testTableLegacyNetwork);
        } else {
            EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            EXPECT_NE(0, status.serviceSpecificErrorCode());
        }
    }

    /*
     * Test networkUpdateRouteParcel behavior in case of route MTU change.
     *
     * Change of route MTU should be treated as an update of the route:
     * - networkUpdateRouteParcel should succeed and update route MTU.
     */
    for (size_t i = 0; i < std::size(kTestData); i++) {
        const auto& td = kTestData[i];
        int mtu = (i % 2) ? 1480 : 1280;

        android::net::RouteInfoParcel parcel;
        parcel.ifName = sTun.name();
        parcel.destination = td.testDest;
        parcel.nextHop = td.testNextHop;
        parcel.mtu = mtu;
        binder::Status status = mNetd->networkAddRouteParcel(TEST_NETID1, parcel);
        if (td.expectSuccess) {
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
            expectNetworkRouteExistsWithMtu(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                            std::to_string(parcel.mtu), sTun.name().c_str());
        } else {
            EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            EXPECT_NE(0, status.serviceSpecificErrorCode());
        }

        parcel.mtu = 1337;
        status = mNetd->networkUpdateRouteParcel(TEST_NETID1, parcel);
        if (td.expectSuccess) {
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
            expectNetworkRouteExistsWithMtu(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                            std::to_string(parcel.mtu), sTun.name().c_str());
        } else {
            EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            EXPECT_NE(0, status.serviceSpecificErrorCode());
        }

        status = mNetd->networkRemoveRouteParcel(TEST_NETID1, parcel);
        if (td.expectSuccess) {
            EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
            expectNetworkRouteDoesNotExist(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                           sTun.name().c_str());
        } else {
            EXPECT_EQ(binder::Status::EX_SERVICE_SPECIFIC, status.exceptionCode());
            EXPECT_NE(0, status.serviceSpecificErrorCode());
        }
    }

    /*
     * Test network[Update|Add]RouteParcel behavior in case of route type change.
     *
     * Change of route type should be treated as an update of the route:
     * - networkUpdateRouteParcel should succeed and update route type.
     * - networkAddRouteParcel should silently fail, because the route already exists. Route type
     *   should not be changed in this case.
     */
    for (size_t i = 0; i < std::size(kTestData); i++) {
        const auto& td = kTestData[i];

        if (!td.expectSuccess) {
            continue;
        }

        android::net::RouteInfoParcel parcel;
        parcel.ifName = sTun.name();
        parcel.destination = td.testDest;
        parcel.nextHop = td.testNextHop;
        parcel.mtu = 1280;
        binder::Status status = mNetd->networkAddRouteParcel(TEST_NETID1, parcel);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        expectNetworkRouteExistsWithMtu(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                        std::to_string(parcel.mtu), sTun.name().c_str());

        parcel.nextHop = parcel.nextHop == "throw" ? "unreachable" : "throw";
        const char* oldNextHop = td.testNextHop;
        const char* newNextHop = parcel.nextHop.c_str();

        // Trying to add same route with changed type, this should silently fail.
        status = mNetd->networkAddRouteParcel(TEST_NETID1, parcel);
        // No error reported.
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        // Old route still exists.
        expectNetworkRouteExistsWithMtu(td.ipVersion, sTun.name(), td.testDest, oldNextHop,
                                        std::to_string(parcel.mtu), sTun.name().c_str());
        // New route was not actually added.
        expectNetworkRouteDoesNotExistWithMtu(td.ipVersion, sTun.name(), td.testDest, newNextHop,
                                              std::to_string(parcel.mtu), sTun.name().c_str());

        // Update should succeed.
        status = mNetd->networkUpdateRouteParcel(TEST_NETID1, parcel);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        expectNetworkRouteExistsWithMtu(td.ipVersion, sTun.name(), td.testDest, newNextHop,
                                        std::to_string(parcel.mtu), sTun.name().c_str());
        expectNetworkRouteDoesNotExistWithMtu(td.ipVersion, sTun.name(), td.testDest, oldNextHop,
                                              std::to_string(parcel.mtu), sTun.name().c_str());

        status = mNetd->networkRemoveRouteParcel(TEST_NETID1, parcel);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        expectNetworkRouteDoesNotExistWithMtu(td.ipVersion, sTun.name(), td.testDest, newNextHop,
                                              std::to_string(parcel.mtu), sTun.name().c_str());
    }

    // Remove test physical network
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

TEST_F(NetdBinderTest, NetworkPermissionDefault) {
    // Add test physical network
    const auto& config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    // Get current default network NetId
    binder::Status status = mNetd->networkGetDefault(&mStoredDefaultNetwork);
    ASSERT_TRUE(status.isOk()) << status.exceptionMessage();

    // Test SetDefault
    status = mNetd->networkSetDefault(TEST_NETID1);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectNetworkDefaultIpRuleExists(sTun.name().c_str());

    status = mNetd->networkClearDefault();
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectNetworkDefaultIpRuleDoesNotExist();

    // Set default network back
    status = mNetd->networkSetDefault(mStoredDefaultNetwork);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    // Test SetPermission
    status = mNetd->networkSetPermissionForNetwork(TEST_NETID1, INetd::PERMISSION_SYSTEM);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectNetworkPermissionIpRuleExists(sTun.name().c_str(), INetd::PERMISSION_SYSTEM);
    expectNetworkPermissionIptablesRuleExists(sTun.name().c_str(), INetd::PERMISSION_SYSTEM);

    status = mNetd->networkSetPermissionForNetwork(TEST_NETID1, INetd::PERMISSION_NONE);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectNetworkPermissionIpRuleExists(sTun.name().c_str(), INetd::PERMISSION_NONE);
    expectNetworkPermissionIptablesRuleExists(sTun.name().c_str(), INetd::PERMISSION_NONE);

    // Remove test physical network
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

TEST_F(NetdBinderTest, NetworkSetProtectAllowDeny) {
    binder::Status status = mNetd->networkSetProtectAllow(TEST_UID1);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    bool ret = false;
    status = mNetd->networkCanProtect(TEST_UID1, &ret);
    EXPECT_TRUE(ret);

    status = mNetd->networkSetProtectDeny(TEST_UID1);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    // Clear uid permission before calling networkCanProtect to ensure
    // the call won't be affected by uid permission.
    EXPECT_TRUE(mNetd->networkClearPermissionForUser({TEST_UID1}).isOk());

    status = mNetd->networkCanProtect(TEST_UID1, &ret);
    EXPECT_FALSE(ret);
}

namespace {

int readIntFromPath(const std::string& path) {
    std::string result = "";
    EXPECT_TRUE(ReadFileToString(path, &result));
    return std::stoi(result);
}

int getTetherAcceptIPv6Ra(const std::string& ifName) {
    std::string path = StringPrintf("/proc/sys/net/ipv6/conf/%s/accept_ra", ifName.c_str());
    return readIntFromPath(path);
}

bool getTetherAcceptIPv6Dad(const std::string& ifName) {
    std::string path = StringPrintf("/proc/sys/net/ipv6/conf/%s/accept_dad", ifName.c_str());
    return readIntFromPath(path);
}

int getTetherIPv6DadTransmits(const std::string& ifName) {
    std::string path = StringPrintf("/proc/sys/net/ipv6/conf/%s/dad_transmits", ifName.c_str());
    return readIntFromPath(path);
}

bool getTetherEnableIPv6(const std::string& ifName) {
    std::string path = StringPrintf("/proc/sys/net/ipv6/conf/%s/disable_ipv6", ifName.c_str());
    int disableIPv6 = readIntFromPath(path);
    return !disableIPv6;
}

bool interfaceListContains(const std::vector<std::string>& ifList, const std::string& ifName) {
    for (const auto& iface : ifList) {
        if (iface == ifName) {
            return true;
        }
    }
    return false;
}

void expectTetherInterfaceConfigureForIPv6Router(const std::string& ifName) {
    EXPECT_EQ(getTetherAcceptIPv6Ra(ifName), 0);
    EXPECT_FALSE(getTetherAcceptIPv6Dad(ifName));
    EXPECT_EQ(getTetherIPv6DadTransmits(ifName), 0);
    EXPECT_TRUE(getTetherEnableIPv6(ifName));
}

void expectTetherInterfaceConfigureForIPv6Client(const std::string& ifName) {
    EXPECT_EQ(getTetherAcceptIPv6Ra(ifName), 2);
    EXPECT_TRUE(getTetherAcceptIPv6Dad(ifName));
    EXPECT_EQ(getTetherIPv6DadTransmits(ifName), 1);
    EXPECT_FALSE(getTetherEnableIPv6(ifName));
}

void expectTetherInterfaceExists(const std::vector<std::string>& ifList,
                                 const std::string& ifName) {
    EXPECT_TRUE(interfaceListContains(ifList, ifName));
}

void expectTetherInterfaceNotExists(const std::vector<std::string>& ifList,
                                    const std::string& ifName) {
    EXPECT_FALSE(interfaceListContains(ifList, ifName));
}

void expectTetherDnsListEquals(const std::vector<std::string>& dnsList,
                               const std::vector<std::string>& testDnsAddrs) {
    EXPECT_TRUE(dnsList == testDnsAddrs);
}

}  // namespace

TEST_F(NetdBinderTest, TetherStartStopStatus) {
    std::vector<std::string> noDhcpRange = {};
    for (bool usingLegacyDnsProxy : {true, false}) {
        android::net::TetherConfigParcel config;
        config.usingLegacyDnsProxy = usingLegacyDnsProxy;
        config.dhcpRanges = noDhcpRange;
        binder::Status status = mNetd->tetherStartWithConfiguration(config);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        SCOPED_TRACE(StringPrintf("usingLegacyDnsProxy: %d", usingLegacyDnsProxy));
        if (usingLegacyDnsProxy == true) {
            expectProcessExists(DNSMASQ);
        } else {
            expectProcessDoesNotExist(DNSMASQ);
        }

        bool tetherEnabled;
        status = mNetd->tetherIsEnabled(&tetherEnabled);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        EXPECT_TRUE(tetherEnabled);

        status = mNetd->tetherStop();
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        expectProcessDoesNotExist(DNSMASQ);

        status = mNetd->tetherIsEnabled(&tetherEnabled);
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
        EXPECT_FALSE(tetherEnabled);
    }
}

TEST_F(NetdBinderTest, TetherInterfaceAddRemoveList) {
    // TODO: verify if dnsmasq update interface successfully

    binder::Status status = mNetd->tetherInterfaceAdd(sTun.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectTetherInterfaceConfigureForIPv6Router(sTun.name());

    std::vector<std::string> ifList;
    status = mNetd->tetherInterfaceList(&ifList);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectTetherInterfaceExists(ifList, sTun.name());

    status = mNetd->tetherInterfaceRemove(sTun.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectTetherInterfaceConfigureForIPv6Client(sTun.name());

    status = mNetd->tetherInterfaceList(&ifList);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectTetherInterfaceNotExists(ifList, sTun.name());

    // Disable IPv6 tethering will disable IPv6 abilities by changing IPv6 settings(accept_ra,
    // dad_transmits, accept_dad, disable_ipv6). See tetherInterfaceRemove in details.
    // Re-init sTun to reset the interface to prevent affecting other test that requires IPv6 with
    // the same interface.
    sTun.destroy();
    sTun.init();
}

TEST_F(NetdBinderTest, TetherDnsSetList) {
    // TODO: verify if dnsmasq update dns successfully
    std::vector<std::string> testDnsAddrs = {"192.168.1.37", "213.137.100.3",
                                             "fe80::1%" + sTun.name()};

    binder::Status status = mNetd->tetherDnsSet(TEST_NETID1, testDnsAddrs);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    std::vector<std::string> dnsList;
    status = mNetd->tetherDnsList(&dnsList);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectTetherDnsListEquals(dnsList, testDnsAddrs);
}

namespace {

std::vector<IPAddress> findDnsSockets(SockDiag* sd, unsigned numExpected) {
    std::vector<IPAddress> listenAddrs;

    // Callback lambda that finds all IPv4 sockets with source port 53.
    auto findDnsSockets = [&](uint8_t /* proto */, const inet_diag_msg* msg) {
        // Always return false, which means do not destroy this socket.
        if (msg->id.idiag_sport != htons(53)) return false;
        IPAddress addr(*(in_addr*)msg->id.idiag_src);
        listenAddrs.push_back(addr);
        return false;
    };

    // There is no way to know if dnsmasq has finished processing the update_interfaces command and
    // opened listening sockets. So, just spin a few times and return the first list of sockets
    // that is at least numExpected long.
    // Pick a relatively large timeout to avoid flaky tests, particularly when running on shared
    // devices.
    constexpr int kMaxAttempts = 50;
    constexpr int kSleepMs = 100;
    for (int i = 0; i < kMaxAttempts; i++) {
        listenAddrs.clear();
        EXPECT_EQ(0, sd->sendDumpRequest(IPPROTO_TCP, AF_INET, 1 << TCP_LISTEN))
                << "Failed to dump sockets, attempt " << i << " of " << kMaxAttempts;
        sd->readDiagMsg(IPPROTO_TCP, findDnsSockets);
        if (listenAddrs.size() >= numExpected) {
            break;
        }
        usleep(kSleepMs * 1000);
    }

    return listenAddrs;
}

}  // namespace

// Checks that when starting dnsmasq on an interface that no longer exists, it doesn't attempt to
// start on other interfaces instead.
TEST_F(NetdBinderTest, TetherDeletedInterface) {
    // Do this first so we don't need to clean up anything else if it fails.
    SockDiag sd;
    ASSERT_TRUE(sd.open()) << "Failed to open SOCK_DIAG socket";

    // Create our own TunInterfaces (so we can delete them without affecting other tests), and add
    // IP addresses to them. They must be IPv4 because tethering an interface disables and
    // re-enables IPv6 on the interface, which clears all addresses.
    TunInterface tun1, tun2;
    ASSERT_EQ(0, tun1.init());
    ASSERT_EQ(0, tun2.init());

    // Clean up. It is safe to call TunInterface::destroy multiple times.
    auto guard = android::base::make_scope_guard([&] {
        tun1.destroy();
        tun2.destroy();
        mNetd->tetherStop();
        mNetd->tetherInterfaceRemove(tun1.name());
        mNetd->tetherInterfaceRemove(tun2.name());
    });

    IPAddress addr1, addr2;
    ASSERT_TRUE(IPAddress::forString("192.0.2.1", &addr1));
    ASSERT_TRUE(IPAddress::forString("192.0.2.2", &addr2));
    EXPECT_EQ(0, tun1.addAddress(addr1.toString(), 32));
    EXPECT_EQ(0, tun2.addAddress(addr2.toString(), 32));

    // Stop tethering.
    binder::Status status = mNetd->tetherStop();
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    // Start dnsmasq on an interface that doesn't exist.
    // First, tether our tun interface...
    status = mNetd->tetherInterfaceAdd(tun1.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectTetherInterfaceConfigureForIPv6Router(tun1.name());

    // ... then delete it...
    tun1.destroy();

    // ... then start dnsmasq.
    android::net::TetherConfigParcel config;
    config.usingLegacyDnsProxy = true;
    config.dhcpRanges = {};
    status = mNetd->tetherStartWithConfiguration(config);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    // Wait for dnsmasq to start.
    expectProcessExists(DNSMASQ);

    // Make sure that netd thinks the interface is tethered (even though it doesn't exist).
    std::vector<std::string> ifList;
    status = mNetd->tetherInterfaceList(&ifList);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    ASSERT_EQ(1U, ifList.size());
    EXPECT_EQ(tun1.name(), ifList[0]);

    // Give dnsmasq some time to start up.
    usleep(200 * 1000);

    // Check that dnsmasq is not listening on any IP addresses. It shouldn't, because it was only
    // told to run on tun1, and tun1 does not exist. Ensure it stays running and doesn't listen on
    // any IP addresses.
    std::vector<IPAddress> listenAddrs = findDnsSockets(&sd, 0);
    EXPECT_EQ(0U, listenAddrs.size()) << "Unexpectedly found IPv4 socket(s) listening on port 53";

    // Now add an interface to dnsmasq and check that we can see the sockets. This confirms that
    // findDnsSockets is actually able to see sockets when they exist.
    status = mNetd->tetherInterfaceAdd(tun2.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    in_addr loopback = {htonl(INADDR_LOOPBACK)};
    listenAddrs = findDnsSockets(&sd, 2);
    EXPECT_EQ(2U, listenAddrs.size()) << "Expected exactly 2 IPv4 sockets listening on port 53";
    EXPECT_EQ(1, std::count(listenAddrs.begin(), listenAddrs.end(), addr2));
    EXPECT_EQ(1, std::count(listenAddrs.begin(), listenAddrs.end(), IPAddress(loopback)));

    // Clean up.
    status = mNetd->tetherStop();
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    expectProcessDoesNotExist(DNSMASQ);

    status = mNetd->tetherInterfaceRemove(tun1.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    status = mNetd->tetherInterfaceRemove(tun2.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
}

namespace {

constexpr char FIREWALL_INPUT[] = "fw_INPUT";
constexpr char FIREWALL_OUTPUT[] = "fw_OUTPUT";
constexpr char FIREWALL_FORWARD[] = "fw_FORWARD";

void expectFirewallAllowlistMode() {
    static const char dropRule[] = "DROP       all";
    static const char rejectRule[] = "REJECT     all";
    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_TRUE(iptablesRuleExists(binary, FIREWALL_INPUT, dropRule));
        EXPECT_TRUE(iptablesRuleExists(binary, FIREWALL_OUTPUT, rejectRule));
        EXPECT_TRUE(iptablesRuleExists(binary, FIREWALL_FORWARD, rejectRule));
    }
}

void expectFirewallDenylistMode() {
    EXPECT_EQ(2, iptablesRuleLineLength(IPTABLES_PATH, FIREWALL_INPUT));
    EXPECT_EQ(2, iptablesRuleLineLength(IPTABLES_PATH, FIREWALL_OUTPUT));
    EXPECT_EQ(2, iptablesRuleLineLength(IPTABLES_PATH, FIREWALL_FORWARD));

    // for IPv6 there is an extra OUTPUT rule to DROP ::1 sourced packets to non-loopback devices
    EXPECT_EQ(2, iptablesRuleLineLength(IP6TABLES_PATH, FIREWALL_INPUT));
    EXPECT_EQ(3, iptablesRuleLineLength(IP6TABLES_PATH, FIREWALL_OUTPUT));
    EXPECT_EQ(2, iptablesRuleLineLength(IP6TABLES_PATH, FIREWALL_FORWARD));
}

bool iptablesFirewallInterfaceFirstRuleExists(const char* binary, const char* chainName,
                                              const std::string& expectedInterface,
                                              const std::string& expectedRule) {
    std::vector<std::string> rules = listIptablesRuleByTable(binary, FILTER_TABLE, chainName);
    // Expected rule:
    // Chain fw_INPUT (1 references)
    // pkts bytes target     prot opt in     out     source               destination
    // 0     0 RETURN     all  --  expectedInterface *       0.0.0.0/0            0.0.0.0/0
    // 0     0 DROP       all  --  *      *       0.0.0.0/0            0.0.0.0/0
    int firstRuleIndex = 2;
    if (rules.size() < 4) return false;
    if (rules[firstRuleIndex].find(expectedInterface) != std::string::npos) {
        if (rules[firstRuleIndex].find(expectedRule) != std::string::npos) {
            return true;
        }
    }
    return false;
}

void expectFirewallInterfaceRuleAllowExists(const std::string& ifname) {
    static const char returnRule[] = "RETURN     all";
    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_TRUE(iptablesFirewallInterfaceFirstRuleExists(binary, FIREWALL_INPUT, ifname,
                                                             returnRule));
        EXPECT_TRUE(iptablesFirewallInterfaceFirstRuleExists(binary, FIREWALL_OUTPUT, ifname,
                                                             returnRule));
    }
}

void expectFireWallInterfaceRuleAllowDoesNotExist(const std::string& ifname) {
    static const char returnRule[] = "RETURN     all";
    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_FALSE(iptablesInterfaceRuleExists(binary, FIREWALL_INPUT, ifname, returnRule,
                                                 FILTER_TABLE));
        EXPECT_FALSE(iptablesInterfaceRuleExists(binary, FIREWALL_OUTPUT, ifname, returnRule,
                                                 FILTER_TABLE));
    }
}

}  // namespace

TEST_F(NetdBinderTest, FirewallSetFirewallType) {
    binder::Status status = mNetd->firewallSetFirewallType(INetd::FIREWALL_ALLOWLIST);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectFirewallAllowlistMode();

    status = mNetd->firewallSetFirewallType(INetd::FIREWALL_DENYLIST);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectFirewallDenylistMode();

    // set firewall type blacklist twice
    mNetd->firewallSetFirewallType(INetd::FIREWALL_DENYLIST);
    status = mNetd->firewallSetFirewallType(INetd::FIREWALL_DENYLIST);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectFirewallDenylistMode();

    // set firewall type whitelist twice
    mNetd->firewallSetFirewallType(INetd::FIREWALL_ALLOWLIST);
    status = mNetd->firewallSetFirewallType(INetd::FIREWALL_ALLOWLIST);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectFirewallAllowlistMode();

    // reset firewall type to default
    status = mNetd->firewallSetFirewallType(INetd::FIREWALL_DENYLIST);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectFirewallDenylistMode();
}

TEST_F(NetdBinderTest, FirewallSetInterfaceRule) {
    // setinterfaceRule is not supported in BLACKLIST MODE
    binder::Status status = mNetd->firewallSetFirewallType(INetd::FIREWALL_DENYLIST);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    status = mNetd->firewallSetInterfaceRule(sTun.name(), INetd::FIREWALL_RULE_ALLOW);
    EXPECT_FALSE(status.isOk()) << status.exceptionMessage();

    // set WHITELIST mode first
    status = mNetd->firewallSetFirewallType(INetd::FIREWALL_ALLOWLIST);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    status = mNetd->firewallSetInterfaceRule(sTun.name(), INetd::FIREWALL_RULE_ALLOW);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectFirewallInterfaceRuleAllowExists(sTun.name());

    status = mNetd->firewallSetInterfaceRule(sTun.name(), INetd::FIREWALL_RULE_DENY);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectFireWallInterfaceRuleAllowDoesNotExist(sTun.name());

    // reset firewall mode to default
    status = mNetd->firewallSetFirewallType(INetd::FIREWALL_DENYLIST);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectFirewallDenylistMode();
}

namespace {

std::string hwAddrToStr(unsigned char* hwaddr) {
    return StringPrintf("%02x:%02x:%02x:%02x:%02x:%02x", hwaddr[0], hwaddr[1], hwaddr[2], hwaddr[3],
                        hwaddr[4], hwaddr[5]);
}

int ipv4NetmaskToPrefixLength(in_addr_t mask) {
    int prefixLength = 0;
    uint32_t m = ntohl(mask);
    while (m & (1 << 31)) {
        prefixLength++;
        m = m << 1;
    }
    return prefixLength;
}

std::string toStdString(const String16& s) {
    return std::string(String8(s.c_str()));
}

android::netdutils::StatusOr<ifreq> ioctlByIfName(const std::string& ifName, unsigned long flag) {
    const auto& sys = sSyscalls.get();
    auto fd = sys.socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    EXPECT_TRUE(isOk(fd.status()));

    struct ifreq ifr = {};
    strlcpy(ifr.ifr_name, ifName.c_str(), IFNAMSIZ);

    return sys.ioctl(fd.value(), flag, &ifr);
}

std::string getInterfaceHwAddr(const std::string& ifName) {
    auto res = ioctlByIfName(ifName, SIOCGIFHWADDR);

    unsigned char hwaddr[ETH_ALEN] = {};
    if (isOk(res.status())) {
        memcpy((void*) hwaddr, &res.value().ifr_hwaddr.sa_data, ETH_ALEN);
    }

    return hwAddrToStr(hwaddr);
}

int getInterfaceIPv4Prefix(const std::string& ifName) {
    auto res = ioctlByIfName(ifName, SIOCGIFNETMASK);

    int prefixLength = 0;
    if (isOk(res.status())) {
        prefixLength = ipv4NetmaskToPrefixLength(
                ((struct sockaddr_in*) &res.value().ifr_addr)->sin_addr.s_addr);
    }

    return prefixLength;
}

std::string getInterfaceIPv4Addr(const std::string& ifName) {
    auto res = ioctlByIfName(ifName, SIOCGIFADDR);

    struct in_addr addr = {};
    if (isOk(res.status())) {
        addr.s_addr = ((struct sockaddr_in*) &res.value().ifr_addr)->sin_addr.s_addr;
    }

    return std::string(inet_ntoa(addr));
}

std::vector<std::string> getInterfaceFlags(const std::string& ifName) {
    auto res = ioctlByIfName(ifName, SIOCGIFFLAGS);

    unsigned flags = 0;
    if (isOk(res.status())) {
        flags = res.value().ifr_flags;
    }

    std::vector<std::string> ifFlags;
    ifFlags.push_back(flags & IFF_UP ? toStdString(INetd::IF_STATE_UP())
                                     : toStdString(INetd::IF_STATE_DOWN()));

    if (flags & IFF_BROADCAST) ifFlags.push_back(toStdString(INetd::IF_FLAG_BROADCAST()));
    if (flags & IFF_LOOPBACK) ifFlags.push_back(toStdString(INetd::IF_FLAG_LOOPBACK()));
    if (flags & IFF_POINTOPOINT) ifFlags.push_back(toStdString(INetd::IF_FLAG_POINTOPOINT()));
    if (flags & IFF_RUNNING) ifFlags.push_back(toStdString(INetd::IF_FLAG_RUNNING()));
    if (flags & IFF_MULTICAST) ifFlags.push_back(toStdString(INetd::IF_FLAG_MULTICAST()));

    return ifFlags;
}

bool compareListInterface(const std::vector<std::string>& interfaceList) {
    const auto& res = getIfaceNames();
    EXPECT_TRUE(isOk(res));

    std::vector<std::string> resIfList;
    resIfList.reserve(res.value().size());
    resIfList.insert(end(resIfList), begin(res.value()), end(res.value()));

    return resIfList == interfaceList;
}

int getInterfaceIPv6PrivacyExtensions(const std::string& ifName) {
    std::string path = StringPrintf("/proc/sys/net/ipv6/conf/%s/use_tempaddr", ifName.c_str());
    return readIntFromPath(path);
}

bool getInterfaceEnableIPv6(const std::string& ifName) {
    std::string path = StringPrintf("/proc/sys/net/ipv6/conf/%s/disable_ipv6", ifName.c_str());

    int disableIPv6 = readIntFromPath(path);
    return !disableIPv6;
}

int getInterfaceMtu(const std::string& ifName) {
    std::string path = StringPrintf("/sys/class/net/%s/mtu", ifName.c_str());
    return readIntFromPath(path);
}

void expectInterfaceList(const std::vector<std::string>& interfaceList) {
    EXPECT_TRUE(compareListInterface(interfaceList));
}

void expectCurrentInterfaceConfigurationEquals(const std::string& ifName,
                                               const InterfaceConfigurationParcel& interfaceCfg) {
    EXPECT_EQ(getInterfaceIPv4Addr(ifName), interfaceCfg.ipv4Addr);
    EXPECT_EQ(getInterfaceIPv4Prefix(ifName), interfaceCfg.prefixLength);
    EXPECT_EQ(getInterfaceHwAddr(ifName), interfaceCfg.hwAddr);
    EXPECT_EQ(getInterfaceFlags(ifName), interfaceCfg.flags);
}

void expectCurrentInterfaceConfigurationAlmostEqual(const InterfaceConfigurationParcel& setCfg) {
    EXPECT_EQ(getInterfaceIPv4Addr(setCfg.ifName), setCfg.ipv4Addr);
    EXPECT_EQ(getInterfaceIPv4Prefix(setCfg.ifName), setCfg.prefixLength);

    const auto& ifFlags = getInterfaceFlags(setCfg.ifName);
    for (const auto& flag : setCfg.flags) {
        EXPECT_TRUE(std::find(ifFlags.begin(), ifFlags.end(), flag) != ifFlags.end());
    }
}

void expectInterfaceIPv6PrivacyExtensions(const std::string& ifName, bool enable) {
    int v6PrivacyExtensions = getInterfaceIPv6PrivacyExtensions(ifName);
    EXPECT_EQ(v6PrivacyExtensions, enable ? 2 : 0);
}

void expectInterfaceNoAddr(const std::string& ifName) {
    // noAddr
    EXPECT_EQ(getInterfaceIPv4Addr(ifName), "0.0.0.0");
    // noPrefix
    EXPECT_EQ(getInterfaceIPv4Prefix(ifName), 0);
}

void expectInterfaceEnableIPv6(const std::string& ifName, bool enable) {
    int enableIPv6 = getInterfaceEnableIPv6(ifName);
    EXPECT_EQ(enableIPv6, enable);
}

void expectInterfaceMtu(const std::string& ifName, const int mtu) {
    int mtuSize = getInterfaceMtu(ifName);
    EXPECT_EQ(mtu, mtuSize);
}

InterfaceConfigurationParcel makeInterfaceCfgParcel(const std::string& ifName,
                                                    const std::string& addr, int prefixLength,
                                                    const std::vector<std::string>& flags) {
    InterfaceConfigurationParcel cfg;
    cfg.ifName = ifName;
    cfg.hwAddr = "";
    cfg.ipv4Addr = addr;
    cfg.prefixLength = prefixLength;
    cfg.flags = flags;
    return cfg;
}

void expectTunFlags(const InterfaceConfigurationParcel& interfaceCfg) {
    std::vector<std::string> expectedFlags = {"up", "point-to-point", "running", "multicast"};
    std::vector<std::string> unexpectedFlags = {"down", "broadcast"};

    for (const auto& flag : expectedFlags) {
        EXPECT_TRUE(std::find(interfaceCfg.flags.begin(), interfaceCfg.flags.end(), flag) !=
                    interfaceCfg.flags.end());
    }

    for (const auto& flag : unexpectedFlags) {
        EXPECT_TRUE(std::find(interfaceCfg.flags.begin(), interfaceCfg.flags.end(), flag) ==
                    interfaceCfg.flags.end());
    }
}

}  // namespace

TEST_F(NetdBinderTest, InterfaceList) {
    std::vector<std::string> interfaceListResult;

    binder::Status status = mNetd->interfaceGetList(&interfaceListResult);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectInterfaceList(interfaceListResult);
}

TEST_F(NetdBinderTest, InterfaceGetCfg) {
    InterfaceConfigurationParcel interfaceCfgResult;

    // Add test physical network
    const auto& config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    binder::Status status = mNetd->interfaceGetCfg(sTun.name(), &interfaceCfgResult);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectCurrentInterfaceConfigurationEquals(sTun.name(), interfaceCfgResult);
    expectTunFlags(interfaceCfgResult);

    // Remove test physical network
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

TEST_F(NetdBinderTest, InterfaceSetCfg) {
    const std::string testAddr = "192.0.2.3";
    const int testPrefixLength = 24;
    std::vector<std::string> upFlags = {"up"};
    std::vector<std::string> downFlags = {"down"};

    // Add test physical network
    const auto& config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    // Set tun interface down.
    auto interfaceCfg = makeInterfaceCfgParcel(sTun.name(), testAddr, testPrefixLength, downFlags);
    binder::Status status = mNetd->interfaceSetCfg(interfaceCfg);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectCurrentInterfaceConfigurationAlmostEqual(interfaceCfg);

    // Set tun interface up again.
    interfaceCfg = makeInterfaceCfgParcel(sTun.name(), testAddr, testPrefixLength, upFlags);
    status = mNetd->interfaceSetCfg(interfaceCfg);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    status = mNetd->interfaceClearAddrs(sTun.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    // Remove test physical network
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

TEST_F(NetdBinderTest, InterfaceSetIPv6PrivacyExtensions) {
    // enable
    binder::Status status = mNetd->interfaceSetIPv6PrivacyExtensions(sTun.name(), true);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectInterfaceIPv6PrivacyExtensions(sTun.name(), true);

    // disable
    status = mNetd->interfaceSetIPv6PrivacyExtensions(sTun.name(), false);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectInterfaceIPv6PrivacyExtensions(sTun.name(), false);
}

TEST_F(NetdBinderTest, InterfaceClearAddr) {
    const std::string testAddr = "192.0.2.3";
    const int testPrefixLength = 24;
    std::vector<std::string> noFlags{};

    // Add test physical network
    const auto& config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    auto interfaceCfg = makeInterfaceCfgParcel(sTun.name(), testAddr, testPrefixLength, noFlags);
    binder::Status status = mNetd->interfaceSetCfg(interfaceCfg);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectCurrentInterfaceConfigurationAlmostEqual(interfaceCfg);

    status = mNetd->interfaceClearAddrs(sTun.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectInterfaceNoAddr(sTun.name());

    // Remove test physical network
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

TEST_F(NetdBinderTest, InterfaceSetEnableIPv6) {
    // Add test physical network
    const auto& config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    // disable
    binder::Status status = mNetd->interfaceSetEnableIPv6(sTun.name(), false);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectInterfaceEnableIPv6(sTun.name(), false);

    // enable
    status = mNetd->interfaceSetEnableIPv6(sTun.name(), true);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectInterfaceEnableIPv6(sTun.name(), true);

    // Remove test physical network
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

TEST_F(NetdBinderTest, InterfaceSetMtu) {
    const int currentMtu = getInterfaceMtu(sTun.name());
    const int testMtu = 1200;

    // Add test physical network
    const auto& config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    binder::Status status = mNetd->interfaceSetMtu(sTun.name(), testMtu);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectInterfaceMtu(sTun.name(), testMtu);

    // restore the MTU back
    status = mNetd->interfaceSetMtu(sTun.name(), currentMtu);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    // Remove test physical network
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

namespace {

constexpr const char TETHER_FORWARD[] = "tetherctrl_FORWARD";
constexpr const char TETHER_NAT_POSTROUTING[] = "tetherctrl_nat_POSTROUTING";
constexpr const char TETHER_RAW_PREROUTING[] = "tetherctrl_raw_PREROUTING";
constexpr const char TETHER_COUNTERS_CHAIN[] = "tetherctrl_counters";

int iptablesCountRules(const char* binary, const char* table, const char* chainName) {
    return listIptablesRuleByTable(binary, table, chainName).size();
}

bool iptablesChainMatch(const char* binary, const char* table, const char* chainName,
                        const std::vector<std::string>& targetVec) {
    std::vector<std::string> rules = listIptablesRuleByTable(binary, table, chainName);
    if (targetVec.size() != rules.size() - 2) {
        return false;
    }

    /*
     * Check that the rules match. Note that this function matches substrings, not entire rules,
     * because otherwise rules where "pkts" or "bytes" are nonzero would not match.
     * Skip first two lines since rules start from third line.
     * Chain chainName (x references)
     * pkts bytes target     prot opt in     out     source               destination
     * ...
     */
    int rIndex = 2;
    for (const auto& target : targetVec) {
        if (rules[rIndex].find(target) == std::string::npos) {
            return false;
        }
        rIndex++;
    }
    return true;
}

void expectNatEnable(const std::string& intIf, const std::string& extIf) {
    std::vector<std::string> postroutingV4Match = {"MASQUERADE"};
    std::vector<std::string> preroutingV4Match = {"CT helper ftp", "CT helper pptp"};
    std::vector<std::string> forwardV4Match = {
            "bw_global_alert", "state RELATED", "state INVALID",
            StringPrintf("tetherctrl_counters  all  --  %s %s", intIf.c_str(), extIf.c_str()),
            "DROP"};

    // V4
    EXPECT_TRUE(iptablesChainMatch(IPTABLES_PATH, NAT_TABLE, TETHER_NAT_POSTROUTING,
                                   postroutingV4Match));
    EXPECT_TRUE(
            iptablesChainMatch(IPTABLES_PATH, RAW_TABLE, TETHER_RAW_PREROUTING, preroutingV4Match));
    EXPECT_TRUE(iptablesChainMatch(IPTABLES_PATH, FILTER_TABLE, TETHER_FORWARD, forwardV4Match));

    std::vector<std::string> forwardV6Match = {"bw_global_alert", "tetherctrl_counters"};
    std::vector<std::string> preroutingV6Match = {"rpfilter invert"};

    // V6
    EXPECT_TRUE(iptablesChainMatch(IP6TABLES_PATH, FILTER_TABLE, TETHER_FORWARD, forwardV6Match));
    EXPECT_TRUE(iptablesChainMatch(IP6TABLES_PATH, RAW_TABLE, TETHER_RAW_PREROUTING,
                                   preroutingV6Match));

    for (const auto& binary : {IPTABLES_PATH, IP6TABLES_PATH}) {
        EXPECT_TRUE(iptablesTargetsExists(binary, 2, FILTER_TABLE, TETHER_COUNTERS_CHAIN, intIf,
                                          extIf));
    }
}

void expectNatDisable() {
    // It is the default DROP rule with tethering disable.
    // Chain tetherctrl_FORWARD (1 references)
    // pkts bytes target     prot opt in     out     source               destination
    //    0     0 DROP       all  --  *      *       0.0.0.0/0            0.0.0.0/0
    std::vector<std::string> forwardV4Match = {"DROP"};
    EXPECT_TRUE(iptablesChainMatch(IPTABLES_PATH, FILTER_TABLE, TETHER_FORWARD, forwardV4Match));

    // We expect that these chains should be empty.
    EXPECT_EQ(2, iptablesCountRules(IPTABLES_PATH, NAT_TABLE, TETHER_NAT_POSTROUTING));
    EXPECT_EQ(2, iptablesCountRules(IPTABLES_PATH, RAW_TABLE, TETHER_RAW_PREROUTING));

    EXPECT_EQ(2, iptablesCountRules(IP6TABLES_PATH, FILTER_TABLE, TETHER_FORWARD));
    EXPECT_EQ(2, iptablesCountRules(IP6TABLES_PATH, RAW_TABLE, TETHER_RAW_PREROUTING));

    // Netd won't clear tether quota rule, we don't care rule in tetherctrl_counters.
}

}  // namespace

TEST_F(NetdBinderTest, TetherForwardAddRemove) {
    binder::Status status = mNetd->tetherAddForward(sTun.name(), sTun2.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectNatEnable(sTun.name(), sTun2.name());

    status = mNetd->tetherRemoveForward(sTun.name(), sTun2.name());
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    expectNatDisable();
}

namespace {

using TripleInt = std::array<int, 3>;

TripleInt readProcFileToTripleInt(const std::string& path) {
    std::string valueString;
    int min, def, max;
    EXPECT_TRUE(ReadFileToString(path, &valueString));
    EXPECT_EQ(3, sscanf(valueString.c_str(), "%d %d %d", &min, &def, &max));
    return {min, def, max};
}

void updateAndCheckTcpBuffer(sp<INetd>& netd, TripleInt& rmemValues, TripleInt& wmemValues) {
    std::string testRmemValues =
            StringPrintf("%u %u %u", rmemValues[0], rmemValues[1], rmemValues[2]);
    std::string testWmemValues =
            StringPrintf("%u %u %u", wmemValues[0], wmemValues[1], wmemValues[2]);
    EXPECT_TRUE(netd->setTcpRWmemorySize(testRmemValues, testWmemValues).isOk());

    TripleInt newRmemValues = readProcFileToTripleInt(TCP_RMEM_PROC_FILE);
    TripleInt newWmemValues = readProcFileToTripleInt(TCP_WMEM_PROC_FILE);

    for (int i = 0; i < 3; i++) {
        SCOPED_TRACE(StringPrintf("tcp_mem value %d should be equal", i));
        EXPECT_EQ(rmemValues[i], newRmemValues[i]);
        EXPECT_EQ(wmemValues[i], newWmemValues[i]);
    }
}

}  // namespace

TEST_F(NetdBinderTest, TcpBufferSet) {
    TripleInt rmemValue = readProcFileToTripleInt(TCP_RMEM_PROC_FILE);
    TripleInt testRmemValue{rmemValue[0] + 42, rmemValue[1] + 42, rmemValue[2] + 42};
    TripleInt wmemValue = readProcFileToTripleInt(TCP_WMEM_PROC_FILE);
    TripleInt testWmemValue{wmemValue[0] + 42, wmemValue[1] + 42, wmemValue[2] + 42};

    updateAndCheckTcpBuffer(mNetd, testRmemValue, testWmemValue);
    updateAndCheckTcpBuffer(mNetd, rmemValue, wmemValue);
}

TEST_F(NetdBinderTest, UnsolEvents) {
    auto testUnsolService = android::net::TestUnsolService::start();
    std::string oldTunName = sTun.name();
    std::string newTunName = "unsolTest";
    testUnsolService->tarVec.push_back(oldTunName);
    testUnsolService->tarVec.push_back(newTunName);
    auto& cv = testUnsolService->getCv();
    auto& cvMutex = testUnsolService->getCvMutex();
    binder::Status status = mNetd->registerUnsolicitedEventListener(
            android::interface_cast<android::net::INetdUnsolicitedEventListener>(testUnsolService));
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

    // TODO: Add test for below events
    //       StrictCleartextDetected / InterfaceDnsServersAdded
    //       InterfaceClassActivity / QuotaLimitReached / InterfaceAddressRemoved

    {
        std::unique_lock lock(cvMutex);

        // Re-init test Tun, and we expect that we will get some unsol events.
        // Use the test Tun device name to verify if we receive its unsol events.
        sTun.destroy();
        // Use predefined name
        sTun.init(newTunName);

        EXPECT_EQ(std::cv_status::no_timeout, cv.wait_for(lock, std::chrono::seconds(2)));
    }

    // bit mask 1101101000
    // Test only covers below events currently
    const uint32_t kExpectedEvents = InterfaceAddressUpdated | InterfaceAdded | InterfaceRemoved |
                                     InterfaceLinkStatusChanged | RouteChanged;
    EXPECT_EQ(kExpectedEvents, testUnsolService->getReceived());

    // Re-init sTun to clear predefined name
    sTun.destroy();
    sTun.init();
}

TEST_F(NetdBinderTest, NDC) {
    struct Command {
        const std::string cmdString;
        const std::string expectedResult;
    };

    // clang-format off
    // Do not change the commands order
    const Command networkCmds[] = {
            {StringPrintf("ndc network create %d", TEST_NETID1),
             "200 0 success"},
            {StringPrintf("ndc network interface add %d %s", TEST_NETID1, sTun.name().c_str()),
             "200 0 success"},
            {StringPrintf("ndc network interface remove %d %s", TEST_NETID1, sTun.name().c_str()),
             "200 0 success"},
            {StringPrintf("ndc network interface add %d %s", TEST_NETID2, sTun.name().c_str()),
             "400 0 addInterfaceToNetwork() failed (Machine is not on the network)"},
            {StringPrintf("ndc network destroy %d", TEST_NETID1),
             "200 0 success"},
    };

    const std::vector<Command> ipfwdCmds = {
            {"ndc ipfwd enable " + sTun.name(),
             "200 0 ipfwd operation succeeded"},
            {"ndc ipfwd disable " + sTun.name(),
             "200 0 ipfwd operation succeeded"},
            {"ndc ipfwd add lo2 lo3",
             "400 0 ipfwd operation failed (No such process)"},
            {"ndc ipfwd add " + sTun.name() + " " + sTun2.name(),
             "200 0 ipfwd operation succeeded"},
            {"ndc ipfwd remove " + sTun.name() + " " + sTun2.name(),
             "200 0 ipfwd operation succeeded"},
    };

    static const struct {
        const char* ipVersion;
        const char* testDest;
        const char* testNextHop;
        const bool expectSuccess;
        const std::string expectedResult;
    } kTestData[] = {
            {IP_RULE_V4, "0.0.0.0/0",          "",            true,
             "200 0 success"},
            {IP_RULE_V4, "10.251.0.0/16",      "",            true,
             "200 0 success"},
            {IP_RULE_V4, "10.251.0.0/16",      "fe80::/64",   false,
             "400 0 addRoute() failed (Invalid argument)",},
            {IP_RULE_V6, "::/0",               "",            true,
             "200 0 success"},
            {IP_RULE_V6, "2001:db8:cafe::/64", "",            true,
             "200 0 success"},
            {IP_RULE_V6, "fe80::/64",          "0.0.0.0",     false,
             "400 0 addRoute() failed (Invalid argument)"},
    };
    // clang-format on

    for (const auto& cmd : networkCmds) {
        const std::vector<std::string> result = runCommand(cmd.cmdString);
        SCOPED_TRACE(cmd.cmdString);
        EXPECT_EQ(result.size(), 1U);
        EXPECT_EQ(cmd.expectedResult, Trim(result[0]));
    }

    for (const auto& cmd : ipfwdCmds) {
        const std::vector<std::string> result = runCommand(cmd.cmdString);
        SCOPED_TRACE(cmd.cmdString);
        EXPECT_EQ(result.size(), 1U);
        EXPECT_EQ(cmd.expectedResult, Trim(result[0]));
    }

    // Add test physical network
    const auto& config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());

    for (const auto& td : kTestData) {
        const std::string routeAddCmd =
                StringPrintf("ndc network route add %d %s %s %s", TEST_NETID1, sTun.name().c_str(),
                             td.testDest, td.testNextHop);
        const std::string routeRemoveCmd =
                StringPrintf("ndc network route remove %d %s %s %s", TEST_NETID1,
                             sTun.name().c_str(), td.testDest, td.testNextHop);
        std::vector<std::string> result = runCommand(routeAddCmd);
        SCOPED_TRACE(routeAddCmd);
        EXPECT_EQ(result.size(), 1U);
        EXPECT_EQ(td.expectedResult, Trim(result[0]));
        if (td.expectSuccess) {
            expectNetworkRouteExists(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                     sTun.name().c_str());
            result = runCommand(routeRemoveCmd);
            EXPECT_EQ(result.size(), 1U);
            EXPECT_EQ(td.expectedResult, Trim(result[0]));
            expectNetworkRouteDoesNotExist(td.ipVersion, sTun.name(), td.testDest, td.testNextHop,
                                           sTun.name().c_str());
        }
    }
    // Remove test physical network
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

TEST_F(NetdBinderTest, OemNetdRelated) {
    sp<IBinder> binder;
    binder::Status status = mNetd->getOemNetd(&binder);
    EXPECT_TRUE(status.isOk()) << status.exceptionMessage();
    sp<com::android::internal::net::IOemNetd> oemNetd;
    if (binder != nullptr) {
        oemNetd = android::interface_cast<com::android::internal::net::IOemNetd>(binder);
    }
    ASSERT_NE(nullptr, oemNetd.get());

    TimedOperation t("OemNetd isAlive RPC");
    bool isAlive = false;
    oemNetd->isAlive(&isAlive);
    ASSERT_TRUE(isAlive);

    class TestOemUnsolListener
        : public com::android::internal::net::BnOemNetdUnsolicitedEventListener {
      public:
        android::binder::Status onRegistered() override {
            std::lock_guard lock(mCvMutex);
            mCv.notify_one();
            return android::binder::Status::ok();
        }
        std::condition_variable& getCv() { return mCv; }
        std::mutex& getCvMutex() { return mCvMutex; }

      private:
        std::mutex mCvMutex;
        std::condition_variable mCv;
    };

    // Start the Binder thread pool.
    android::ProcessState::self()->startThreadPool();

    android::sp<TestOemUnsolListener> testListener = new TestOemUnsolListener();

    auto& cv = testListener->getCv();
    auto& cvMutex = testListener->getCvMutex();

    {
        std::unique_lock lock(cvMutex);

        status = oemNetd->registerOemUnsolicitedEventListener(
                ::android::interface_cast<
                        com::android::internal::net::IOemNetdUnsolicitedEventListener>(
                        testListener));
        EXPECT_TRUE(status.isOk()) << status.exceptionMessage();

        // Wait for receiving expected events.
        EXPECT_EQ(std::cv_status::no_timeout, cv.wait_for(lock, std::chrono::seconds(2)));
    }
}

void NetdBinderTest::createVpnNetworkWithUid(bool secure, uid_t uid, int vpnNetId,
                                             int fallthroughNetId, int nonDefaultNetId) {
    // Re-init sTun* to ensure route rule exists.
    sTun.destroy();
    sTun.init();
    sTun2.destroy();
    sTun2.init();
    sTun3.destroy();
    sTun3.init();

    // Create physical network with fallthroughNetId but not set it as default network
    auto config = makeNativeNetworkConfig(fallthroughNetId, NativeNetworkType::PHYSICAL,
                                          INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(fallthroughNetId, sTun.name()).isOk());
    // Create another physical network in order to test VPN behaviour with multiple networks
    // connected, of which one may be the default.
    auto nonDefaultNetworkConfig = makeNativeNetworkConfig(
            nonDefaultNetId, NativeNetworkType::PHYSICAL, INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(nonDefaultNetworkConfig).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(nonDefaultNetId, sTun3.name()).isOk());

    // Create VPN with vpnNetId
    config.netId = vpnNetId;
    config.networkType = NativeNetworkType::VIRTUAL;
    config.secure = secure;
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());

    // Add uid to VPN
    EXPECT_TRUE(mNetd->networkAddUidRanges(vpnNetId, {makeUidRangeParcel(uid, uid)}).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(vpnNetId, sTun2.name()).isOk());

    // Add default route to fallthroughNetwork
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID1, sTun.name(), "::/0", "").isOk());
    // Add limited route
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID2, sTun2.name(), "2001:db8::/32", "").isOk());

    // Also add default route to non-default network for per app default use.
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID3, sTun3.name(), "::/0", "").isOk());
}

void NetdBinderTest::createAndSetDefaultNetwork(int netId, const std::string& interface,
                                                int permission) {
    // backup current default network.
    ASSERT_TRUE(mNetd->networkGetDefault(&mStoredDefaultNetwork).isOk());

    const auto& config =
            makeNativeNetworkConfig(netId, NativeNetworkType::PHYSICAL, permission, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(netId, interface).isOk());
    EXPECT_TRUE(mNetd->networkSetDefault(netId).isOk());
}

void NetdBinderTest::createPhysicalNetwork(int netId, const std::string& interface,
                                           int permission) {
    const auto& config =
            makeNativeNetworkConfig(netId, NativeNetworkType::PHYSICAL, permission, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(netId, interface).isOk());
}

// 1. Create a physical network on sTun, and set it as the system default network.
// 2. Create another physical network on sTun2.
void NetdBinderTest::createDefaultAndOtherPhysicalNetwork(int defaultNetId, int otherNetId) {
    createAndSetDefaultNetwork(defaultNetId, sTun.name());
    EXPECT_TRUE(mNetd->networkAddRoute(defaultNetId, sTun.name(), "::/0", "").isOk());

    createPhysicalNetwork(otherNetId, sTun2.name());
    EXPECT_TRUE(mNetd->networkAddRoute(otherNetId, sTun2.name(), "::/0", "").isOk());
}

// 1. Create a system default network and a physical network.
// 2. Create a VPN on sTun3.
void NetdBinderTest::createVpnAndOtherPhysicalNetwork(int systemDefaultNetId, int otherNetId,
                                                      int vpnNetId, bool secure) {
    createDefaultAndOtherPhysicalNetwork(systemDefaultNetId, otherNetId);

    auto config = makeNativeNetworkConfig(vpnNetId, NativeNetworkType::VIRTUAL,
                                          INetd::PERMISSION_NONE, secure, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(vpnNetId, sTun3.name()).isOk());
    EXPECT_TRUE(mNetd->networkAddRoute(vpnNetId, sTun3.name(), "2001:db8::/32", "").isOk());
}

// 1. Create system default network, a physical network (for per-app default), and a VPN.
// 2. Add per-app uid ranges and VPN ranges.
void NetdBinderTest::createVpnAndAppDefaultNetworkWithUid(
        int systemDefaultNetId, int appDefaultNetId, int vpnNetId, bool secure,
        std::vector<UidRangeParcel>&& appDefaultUidRanges,
        std::vector<UidRangeParcel>&& vpnUidRanges) {
    createVpnAndOtherPhysicalNetwork(systemDefaultNetId, appDefaultNetId, vpnNetId, secure);
    // add per-app uid ranges.
    EXPECT_TRUE(mNetd->networkAddUidRanges(appDefaultNetId, appDefaultUidRanges).isOk());
    // add VPN uid ranges.
    EXPECT_TRUE(mNetd->networkAddUidRanges(vpnNetId, vpnUidRanges).isOk());
}

namespace {

void clearQueue(int tunFd) {
    char buf[4096];
    int ret;
    do {
        ret = read(tunFd, buf, sizeof(buf));
    } while (ret > 0);
}

void checkDataReceived(int udpSocket, int tunFd, sockaddr* dstAddr, int addrLen) {
    char buf[4096] = {};
    // Clear tunFd's queue before write something because there might be some
    // arbitrary packets in the queue. (e.g. ICMPv6 packet)
    clearQueue(tunFd);
    EXPECT_EQ(4, sendto(udpSocket, "foo", sizeof("foo"), 0, dstAddr, addrLen));
    // TODO: extract header and verify data
    EXPECT_GT(read(tunFd, buf, sizeof(buf)), 0);
}

bool sendPacketFromUid(uid_t uid, IPSockAddr& dstAddr, Fwmark* fwmark, int tunFd,
                       bool doConnect = true) {
    int family = dstAddr.family();
    ScopedUidChange scopedUidChange(uid);
    unique_fd testSocket(socket(family, SOCK_DGRAM | SOCK_CLOEXEC, 0));

    if (testSocket < 0) return false;
    const sockaddr_storage dst = IPSockAddr(dstAddr.ip(), dstAddr.port());
    if (doConnect && connect(testSocket, (sockaddr*)&dst, sizeof(dst)) == -1) return false;

    socklen_t fwmarkLen = sizeof(fwmark->intValue);
    EXPECT_NE(-1, getsockopt(testSocket, SOL_SOCKET, SO_MARK, &(fwmark->intValue), &fwmarkLen));

    int addr_len = (family == AF_INET) ? INET_ADDRSTRLEN : INET6_ADDRSTRLEN;
    char addr[addr_len];
    inet_ntop(family, &dstAddr, addr, addr_len);
    SCOPED_TRACE(StringPrintf("sendPacket, addr: %s, uid: %u, doConnect: %s", addr, uid,
                              doConnect ? "true" : "false"));
    if (doConnect) {
        checkDataReceived(testSocket, tunFd, nullptr, 0);
    } else {
        checkDataReceived(testSocket, tunFd, (sockaddr*)&dst, sizeof(dst));
    }

    return true;
}

bool sendIPv4PacketFromUid(uid_t uid, const in_addr& dstAddr, Fwmark* fwmark, int tunFd,
                           bool doConnect = true) {
    const sockaddr_in dst = {.sin_family = AF_INET, .sin_port = 42, .sin_addr = dstAddr};
    IPSockAddr addr = IPSockAddr(dst);

    return sendPacketFromUid(uid, addr, fwmark, tunFd, doConnect);
}

bool sendIPv6PacketFromUid(uid_t uid, const in6_addr& dstAddr, Fwmark* fwmark, int tunFd,
                           bool doConnect = true) {
    const sockaddr_in6 dst6 = {
            .sin6_family = AF_INET6,
            .sin6_port = 42,
            .sin6_addr = dstAddr,
    };
    IPSockAddr addr = IPSockAddr(dst6);

    return sendPacketFromUid(uid, addr, fwmark, tunFd, doConnect);
}

// Send an IPv6 packet from the uid. Expect to fail and get specified errno.
bool sendIPv6PacketFromUidFail(uid_t uid, const in6_addr& dstAddr, Fwmark* fwmark, bool doConnect,
                               int expectedErr) {
    ScopedUidChange scopedUidChange(uid);
    unique_fd s(socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0));
    if (s < 0) return false;

    const sockaddr_in6 dst6 = {
            .sin6_family = AF_INET6,
            .sin6_port = 42,
            .sin6_addr = dstAddr,
    };
    if (doConnect) {
        if (connect(s, (sockaddr*)&dst6, sizeof(dst6)) == 0) return false;
        if (errno != expectedErr) return false;
    }

    socklen_t fwmarkLen = sizeof(fwmark->intValue);
    EXPECT_NE(-1, getsockopt(s, SOL_SOCKET, SO_MARK, &(fwmark->intValue), &fwmarkLen));

    char addr[INET6_ADDRSTRLEN];
    inet_ntop(AF_INET6, &dstAddr, addr, INET6_ADDRSTRLEN);
    SCOPED_TRACE(StringPrintf("sendIPv6PacketFail, addr: %s, uid: %u, doConnect: %s", addr, uid,
                              doConnect ? "true" : "false"));
    if (!doConnect) {
        if (sendto(s, "foo", sizeof("foo"), 0, (sockaddr*)&dst6, sizeof(dst6)) == 0) return false;
        if (errno != expectedErr) return false;
    }
    return true;
}

void expectVpnFallthroughRuleExists(const std::string& ifName, int vpnNetId) {
    std::string vpnFallthroughRule =
            StringPrintf("%d:\tfrom all fwmark 0x%x/0xffff lookup %s",
                         RULE_PRIORITY_VPN_FALLTHROUGH, vpnNetId, ifName.c_str());
    for (const auto& ipVersion : {IP_RULE_V4, IP_RULE_V6}) {
        EXPECT_TRUE(ipRuleExists(ipVersion, vpnFallthroughRule));
    }
}

void expectVpnFallthroughWorks(android::net::INetd* netdService, bool bypassable, uid_t uid,
                               uid_t uidNotInVpn, const TunInterface& fallthroughNetwork,
                               const TunInterface& vpnNetwork, const TunInterface& otherNetwork,
                               int vpnNetId = TEST_NETID2, int fallthroughNetId = TEST_NETID1,
                               int otherNetId = TEST_NETID3) {
    // Set default network to NETID_UNSET
    EXPECT_TRUE(netdService->networkSetDefault(NETID_UNSET).isOk());

    // insideVpnAddr based on the route we added in createVpnNetworkWithUid
    in6_addr insideVpnAddr = {
            {// 2001:db8:cafe::1
             .u6_addr8 = {0x20, 0x01, 0x0d, 0xb8, 0xca, 0xfe, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}}};
    // outsideVpnAddr will hit the route in the fallthrough network route table
    // because we added default route in createVpnNetworkWithUid
    in6_addr outsideVpnAddr = {
            {// 2607:f0d0:1002::4
             .u6_addr8 = {0x26, 0x07, 0xf0, 0xd0, 0x10, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4}}};

    int fallthroughFd = fallthroughNetwork.getFdForTesting();
    int vpnFd = vpnNetwork.getFdForTesting();
    // Expect all connections to fail because UID 0 is not routed to the VPN and there is no
    // default network.
    Fwmark fwmark;
    EXPECT_FALSE(sendIPv6PacketFromUid(0, outsideVpnAddr, &fwmark, fallthroughFd));
    EXPECT_FALSE(sendIPv6PacketFromUid(0, insideVpnAddr, &fwmark, fallthroughFd));

    // Set default network
    EXPECT_TRUE(netdService->networkSetDefault(fallthroughNetId).isOk());

    // Connections go on the default network because UID 0 is not subject to the VPN.
    EXPECT_TRUE(sendIPv6PacketFromUid(0, outsideVpnAddr, &fwmark, fallthroughFd));
    EXPECT_EQ(fallthroughNetId | 0xC0000, static_cast<int>(fwmark.intValue));
    EXPECT_TRUE(sendIPv6PacketFromUid(0, insideVpnAddr, &fwmark, fallthroughFd));
    EXPECT_EQ(fallthroughNetId | 0xC0000, static_cast<int>(fwmark.intValue));

    // Check if fallthrough rule exists
    expectVpnFallthroughRuleExists(fallthroughNetwork.name(), vpnNetId);

    // Check if local exclusion rule exists for default network
    expectVpnLocalExclusionRuleExists(fallthroughNetwork.name(), true);
    // No local exclusion rule for non-default network
    expectVpnLocalExclusionRuleExists(otherNetwork.name(), false);

    // Expect fallthrough to default network
    // The fwmark differs depending on whether the VPN is bypassable or not.
    EXPECT_TRUE(sendIPv6PacketFromUid(uid, outsideVpnAddr, &fwmark, fallthroughFd));
    EXPECT_EQ(bypassable ? vpnNetId : fallthroughNetId, static_cast<int>(fwmark.intValue));

    // Expect connect success, packet will be sent to vpnFd.
    EXPECT_TRUE(sendIPv6PacketFromUid(uid, insideVpnAddr, &fwmark, vpnFd));
    EXPECT_EQ(bypassable ? vpnNetId : fallthroughNetId, static_cast<int>(fwmark.intValue));

    // Explicitly select vpn network
    setNetworkForProcess(vpnNetId);

    // Expect fallthrough to default network
    EXPECT_TRUE(sendIPv6PacketFromUid(0, outsideVpnAddr, &fwmark, fallthroughFd));
    // Expect the mark contains all the bit because we've selected network.
    EXPECT_EQ(vpnNetId | 0xF0000, static_cast<int>(fwmark.intValue));

    // Expect connect success, packet will be sent to vpnFd.
    EXPECT_TRUE(sendIPv6PacketFromUid(0, insideVpnAddr, &fwmark, vpnFd));
    // Expect the mark contains all the bit because we've selected network.
    EXPECT_EQ(vpnNetId | 0xF0000, static_cast<int>(fwmark.intValue));

    // Explicitly select fallthrough network
    setNetworkForProcess(fallthroughNetId);

    // The mark is set to fallthrough network because we've selected it.
    EXPECT_TRUE(sendIPv6PacketFromUid(0, outsideVpnAddr, &fwmark, fallthroughFd));
    EXPECT_TRUE(sendIPv6PacketFromUid(0, insideVpnAddr, &fwmark, fallthroughFd));

    // If vpn is BypassableVPN, connections can also go on the fallthrough network under vpn uid.
    if (bypassable) {
        EXPECT_TRUE(sendIPv6PacketFromUid(uid, outsideVpnAddr, &fwmark, fallthroughFd));
        EXPECT_TRUE(sendIPv6PacketFromUid(uid, insideVpnAddr, &fwmark, fallthroughFd));
    } else {
        // If not, no permission to bypass vpn.
        EXPECT_FALSE(sendIPv6PacketFromUid(uid, outsideVpnAddr, &fwmark, fallthroughFd));
        EXPECT_FALSE(sendIPv6PacketFromUid(uid, insideVpnAddr, &fwmark, fallthroughFd));
    }

    // Add per-app uid ranges.
    EXPECT_TRUE(netdService
                        ->networkAddUidRanges(otherNetId,
                                              {makeUidRangeParcel(uidNotInVpn, uidNotInVpn)})
                        .isOk());

    int appDefaultFd = otherNetwork.getFdForTesting();

    // UID is not inside the VPN range, so it won't go to vpn network.
    // It won't fall into per app local rule because it's explicitly selected.
    EXPECT_TRUE(sendIPv6PacketFromUid(uidNotInVpn, outsideVpnAddr, &fwmark, fallthroughFd));
    EXPECT_TRUE(sendIPv6PacketFromUid(uidNotInVpn, insideVpnAddr, &fwmark, fallthroughFd));

    // Reset explicitly selection.
    setNetworkForProcess(NETID_UNSET);
    // Connections can go to app default network.
    EXPECT_TRUE(sendIPv6PacketFromUid(uidNotInVpn, insideVpnAddr, &fwmark, appDefaultFd));
    EXPECT_TRUE(sendIPv6PacketFromUid(uidNotInVpn, outsideVpnAddr, &fwmark, appDefaultFd));
}

}  // namespace

TEST_F(NetdBinderTest, SecureVPNFallthrough) {
    createVpnNetworkWithUid(true /* secure */, TEST_UID1);
    // Get current default network NetId
    ASSERT_TRUE(mNetd->networkGetDefault(&mStoredDefaultNetwork).isOk());
    expectVpnFallthroughWorks(mNetd.get(), false /* bypassable */, TEST_UID1, TEST_UID2, sTun,
                              sTun2, sTun3);
}

TEST_F(NetdBinderTest, BypassableVPNFallthrough) {
    createVpnNetworkWithUid(false /* secure */, TEST_UID1);
    // Get current default network NetId
    ASSERT_TRUE(mNetd->networkGetDefault(&mStoredDefaultNetwork).isOk());
    expectVpnFallthroughWorks(mNetd.get(), true /* bypassable */, TEST_UID1, TEST_UID2, sTun, sTun2,
                              sTun3);
}

namespace {

int32_t createIpv6SocketAndCheckMark(int type, const in6_addr& dstAddr) {
    const sockaddr_in6 dst6 = {
            .sin6_family = AF_INET6,
            .sin6_port = 1234,
            .sin6_addr = dstAddr,
    };
    // create non-blocking socket.
    int sockFd = socket(AF_INET6, type | SOCK_NONBLOCK, 0);
    EXPECT_NE(-1, sockFd);
    EXPECT_EQ((type == SOCK_STREAM) ? -1 : 0, connect(sockFd, (sockaddr*)&dst6, sizeof(dst6)));

    // Get socket fwmark.
    Fwmark fwmark;
    socklen_t fwmarkLen = sizeof(fwmark.intValue);
    EXPECT_EQ(0, getsockopt(sockFd, SOL_SOCKET, SO_MARK, &fwmark.intValue, &fwmarkLen));
    EXPECT_EQ(0, close(sockFd));
    return fwmark.intValue;
}

}  // namespace

TEST_F(NetdBinderTest, GetFwmarkForNetwork) {
    // Save current default network.
    ASSERT_TRUE(mNetd->networkGetDefault(&mStoredDefaultNetwork).isOk());

    // Add test physical network 1 and set as default network.
    auto config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                          INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID1, sTun.name()).isOk());
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID1, sTun.name(), "2001:db8::/32", "").isOk());
    EXPECT_TRUE(mNetd->networkSetDefault(TEST_NETID1).isOk());
    // Add test physical network 2
    config.netId = TEST_NETID2;
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(TEST_NETID2, sTun2.name()).isOk());

    // Get fwmark for network 1.
    MarkMaskParcel maskMarkNet1;
    ASSERT_TRUE(mNetd->getFwmarkForNetwork(TEST_NETID1, &maskMarkNet1).isOk());

    uint32_t fwmarkTcp = createIpv6SocketAndCheckMark(SOCK_STREAM, V6_ADDR);
    uint32_t fwmarkUdp = createIpv6SocketAndCheckMark(SOCK_DGRAM, V6_ADDR);
    EXPECT_EQ(maskMarkNet1.mark, static_cast<int>(fwmarkTcp & maskMarkNet1.mask));
    EXPECT_EQ(maskMarkNet1.mark, static_cast<int>(fwmarkUdp & maskMarkNet1.mask));

    // Get fwmark for network 2.
    MarkMaskParcel maskMarkNet2;
    ASSERT_TRUE(mNetd->getFwmarkForNetwork(TEST_NETID2, &maskMarkNet2).isOk());
    EXPECT_NE(maskMarkNet2.mark, static_cast<int>(fwmarkTcp & maskMarkNet2.mask));
    EXPECT_NE(maskMarkNet2.mark, static_cast<int>(fwmarkUdp & maskMarkNet2.mask));

    // Remove test physical network.
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID2).isOk());
    EXPECT_TRUE(mNetd->networkDestroy(TEST_NETID1).isOk());
}

TEST_F(NetdBinderTest, TestServiceDump) {
    sp<IBinder> binder = INetd::asBinder(mNetd);
    ASSERT_NE(nullptr, binder);

    struct TestData {
        // Expected contents of the dump command.
        const std::string output;
        // A regex that might be helpful in matching relevant lines in the output.
        // Used to make it easier to add test cases for this code.
        const std::string hintRegex;
    };
    std::vector<TestData> testData;

    // Send some IPCs and for each one add an element to testData telling us what to expect.
    const auto& config = makeNativeNetworkConfig(TEST_DUMP_NETID, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    testData.push_back(
            {"networkCreate(NativeNetworkConfig{netId: 65123, networkType: PHYSICAL, "
             "permission: 0, secure: false, vpnType: PLATFORM, excludeLocalRoutes: false})",
             "networkCreate.*65123"});

    EXPECT_EQ(EEXIST, mNetd->networkCreate(config).serviceSpecificErrorCode());
    testData.push_back(
            {"networkCreate(NativeNetworkConfig{netId: 65123, networkType: PHYSICAL, "
             "permission: 0, secure: false, vpnType: PLATFORM, excludeLocalRoutes: false}) "
             "-> ServiceSpecificException(17, \"File exists\")",
             "networkCreate.*65123.*17"});

    EXPECT_TRUE(mNetd->networkAddInterface(TEST_DUMP_NETID, sTun.name()).isOk());
    testData.push_back({StringPrintf("networkAddInterface(65123, %s)", sTun.name().c_str()),
                        StringPrintf("networkAddInterface.*65123.*%s", sTun.name().c_str())});

    android::net::RouteInfoParcel parcel;
    parcel.ifName = sTun.name();
    parcel.destination = "2001:db8:dead:beef::/64";
    parcel.nextHop = "fe80::dead:beef";
    parcel.mtu = 1234;
    EXPECT_TRUE(mNetd->networkAddRouteParcel(TEST_DUMP_NETID, parcel).isOk());
    testData.push_back(
            {StringPrintf("networkAddRouteParcel(65123, RouteInfoParcel{destination:"
                          " 2001:db8:dead:beef::/64, ifName: %s, nextHop: fe80::dead:beef,"
                          " mtu: 1234})",
                          sTun.name().c_str()),
             "networkAddRouteParcel.*65123.*dead:beef"});

    EXPECT_TRUE(mNetd->networkDestroy(TEST_DUMP_NETID).isOk());
    testData.push_back({"networkDestroy(65123)", "networkDestroy.*65123"});

    // Send the service dump request to netd.
    std::vector<std::string> lines = {};
    android::status_t ret = dumpService(binder, {}, lines);
    ASSERT_EQ(android::OK, ret) << "Error dumping service: " << android::statusToString(ret);

    // Basic regexp to match dump output lines. Matches the beginning and end of the line, and
    // puts the output of the command itself into the first match group.
    // Example: "      11-05 00:23:39.481 myCommand(args) <2.02ms>".
    const std::basic_regex lineRegex(
            "^      [0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}[.][0-9]{3} "
            "(.*)"
            " <[0-9]+[.][0-9]{2}ms>$");

    // For each element of testdata, check that the expected output appears in the dump output.
    // If not, fail the test and use hintRegex to print similar lines to assist in debugging.
    for (const TestData& td : testData) {
        const bool found = std::any_of(lines.begin(), lines.end(), [&](const std::string& line) {
            std::smatch match;
            if (!std::regex_match(line, match, lineRegex)) return false;
            return (match.size() == 2) && (match[1].str() == td.output);
        });
        EXPECT_TRUE(found) << "Didn't find line '" << td.output << "' in dumpsys output.";
        if (found) continue;
        std::cerr << "Similar lines" << std::endl;
        for (const auto& line : lines) {
            if (std::regex_search(line, std::basic_regex(td.hintRegex))) {
                std::cerr << line << std::endl;
            }
        }
    }
}

namespace {

// aliases for better reading
#define SYSTEM_DEFAULT_NETID TEST_NETID1
#define APP_DEFAULT_NETID TEST_NETID2
#define VPN_NETID TEST_NETID3

#define ENTERPRISE_NETID_1 TEST_NETID2
#define ENTERPRISE_NETID_2 TEST_NETID3
#define ENTERPRISE_NETID_3 TEST_NETID4

void verifyAppUidRules(std::vector<bool>&& expectedResults, std::vector<UidRangeParcel>& uidRanges,
                       const std::string& iface, int32_t subPriority) {
    ASSERT_EQ(expectedResults.size(), uidRanges.size());
    if (iface.size()) {
        std::string action = StringPrintf("lookup %s", iface.c_str());
        std::string action_local = StringPrintf("lookup %s_local", iface.c_str());
        for (unsigned long i = 0; i < uidRanges.size(); i++) {
            EXPECT_EQ(expectedResults[i],
                      ipRuleExistsForRange(RULE_PRIORITY_UID_EXPLICIT_NETWORK + subPriority,
                                           uidRanges[i], action));
            EXPECT_EQ(expectedResults[i],
                      ipRuleExistsForRange(RULE_PRIORITY_UID_IMPLICIT_NETWORK + subPriority,
                                           uidRanges[i], action));
            EXPECT_EQ(expectedResults[i],
                      ipRuleExistsForRange(RULE_PRIORITY_UID_DEFAULT_NETWORK + subPriority,
                                           uidRanges[i], action));
            EXPECT_EQ(expectedResults[i], ipRuleExistsForRange(RULE_PRIORITY_UID_LOCAL_ROUTES,
                                                               uidRanges[i], action_local));
        }
    } else {
        std::string action = "unreachable";
        for (unsigned long i = 0; i < uidRanges.size(); i++) {
            EXPECT_EQ(expectedResults[i],
                      ipRuleExistsForRange(RULE_PRIORITY_UID_EXPLICIT_NETWORK + subPriority,
                                           uidRanges[i], action));
            EXPECT_EQ(expectedResults[i],
                      ipRuleExistsForRange(RULE_PRIORITY_UID_IMPLICIT_NETWORK + subPriority,
                                           uidRanges[i], action));
            EXPECT_EQ(expectedResults[i],
                      ipRuleExistsForRange(RULE_PRIORITY_UID_DEFAULT_UNREACHABLE + subPriority,
                                           uidRanges[i], action));
        }
    }
}

void verifyAppUidRules(std::vector<bool>&& expectedResults, NativeUidRangeConfig& uidRangeConfig,
                       const std::string& iface) {
    verifyAppUidRules(std::move(expectedResults), uidRangeConfig.uidRanges, iface,
                      uidRangeConfig.subPriority);
}

void verifyVpnUidRules(std::vector<bool>&& expectedResults, NativeUidRangeConfig& uidRangeConfig,
                       const std::string& iface, bool secure, bool excludeLocalRoutes) {
    ASSERT_EQ(expectedResults.size(), uidRangeConfig.uidRanges.size());
    std::string action = StringPrintf("lookup %s", iface.c_str());

    int32_t priority;
    if (secure) {
        priority = RULE_PRIORITY_SECURE_VPN;
    } else {
        // Set to no local exclusion here to reflect the default value of local exclusion.
        priority = excludeLocalRoutes ? RULE_PRIORITY_BYPASSABLE_VPN_LOCAL_EXCLUSION
                                      : RULE_PRIORITY_BYPASSABLE_VPN_NO_LOCAL_EXCLUSION;
    }
    for (unsigned long i = 0; i < uidRangeConfig.uidRanges.size(); i++) {
        EXPECT_EQ(expectedResults[i], ipRuleExistsForRange(priority + uidRangeConfig.subPriority,
                                                           uidRangeConfig.uidRanges[i], action));
        EXPECT_EQ(expectedResults[i],
                  ipRuleExistsForRange(RULE_PRIORITY_EXPLICIT_NETWORK + uidRangeConfig.subPriority,
                                       uidRangeConfig.uidRanges[i], action));
        EXPECT_EQ(expectedResults[i],
                  ipRuleExistsForRange(RULE_PRIORITY_OUTPUT_INTERFACE + uidRangeConfig.subPriority,
                                       uidRangeConfig.uidRanges[i], action, iface.c_str()));
    }
}

constexpr int SUB_PRIORITY_1 = UidRanges::SUB_PRIORITY_HIGHEST + 1;
constexpr int SUB_PRIORITY_2 = UidRanges::SUB_PRIORITY_HIGHEST + 2;

constexpr int IMPLICITLY_SELECT = 0;
constexpr int EXPLICITLY_SELECT = 1;
constexpr int UNCONNECTED_SOCKET = 2;

// 1. Send data with the specified UID, on a connected or unconnected socket.
// 2. Verify if data is received from the specified fd. The fd should belong to a TUN, which has
//    been assigned to the test network.
// 3. Verify if fwmark of data is correct.
// Note: This is a helper function used by per-app default network tests. It does not implement full
// fwmark logic in netd, and it's currently sufficient. Extension may be required for more
// complicated tests.
void expectPacketSentOnNetId(uid_t uid, unsigned netId, int fd, int selectionMode) {
    Fwmark fwmark;
    const bool doConnect = (selectionMode != UNCONNECTED_SOCKET);
    EXPECT_TRUE(sendIPv6PacketFromUid(uid, V6_ADDR, &fwmark, fd, doConnect));

    Fwmark expected;
    expected.netId = netId;
    expected.explicitlySelected = (selectionMode == EXPLICITLY_SELECT);
    if (uid == AID_ROOT && selectionMode == EXPLICITLY_SELECT) {
        expected.protectedFromVpn = true;
    } else {
        expected.protectedFromVpn = false;
    }
    if (selectionMode == UNCONNECTED_SOCKET) {
        expected.permission = PERMISSION_NONE;
    } else {
        expected.permission = (uid == AID_ROOT) ? PERMISSION_SYSTEM : PERMISSION_NONE;
    }

    EXPECT_EQ(expected.intValue, fwmark.intValue);
}

void expectUnreachableError(uid_t uid, unsigned netId, int selectionMode) {
    Fwmark fwmark;
    const bool doConnect = (selectionMode != UNCONNECTED_SOCKET);
    EXPECT_TRUE(sendIPv6PacketFromUidFail(uid, V6_ADDR, &fwmark, doConnect, ENETUNREACH));

    Fwmark expected;
    expected.netId = netId;
    expected.explicitlySelected = (selectionMode == EXPLICITLY_SELECT);
    if (uid == AID_ROOT && selectionMode == EXPLICITLY_SELECT) {
        expected.protectedFromVpn = true;
    } else {
        expected.protectedFromVpn = false;
    }
    if (selectionMode == UNCONNECTED_SOCKET) {
        expected.permission = PERMISSION_NONE;
    } else {
        expected.permission = (uid == AID_ROOT) ? PERMISSION_SYSTEM : PERMISSION_NONE;
    }

    EXPECT_EQ(expected.intValue, fwmark.intValue);
}

}  // namespace

// Verify how the API handle overlapped UID ranges
TEST_F(NetdBinderTest, PerAppDefaultNetwork_OverlappedUidRanges) {
    const auto& config = makeNativeNetworkConfig(APP_DEFAULT_NETID, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(APP_DEFAULT_NETID, sTun.name()).isOk());

    std::vector<UidRangeParcel> uidRanges = {makeUidRangeParcel(BASE_UID + 1, BASE_UID + 1),
                                             makeUidRangeParcel(BASE_UID + 10, BASE_UID + 12)};
    EXPECT_TRUE(mNetd->networkAddUidRanges(APP_DEFAULT_NETID, uidRanges).isOk());

    binder::Status status;
    status = mNetd->networkAddUidRanges(APP_DEFAULT_NETID,
                                        {makeUidRangeParcel(BASE_UID + 1, BASE_UID + 1)});
    EXPECT_TRUE(status.isOk());

    status = mNetd->networkAddUidRanges(APP_DEFAULT_NETID,
                                        {makeUidRangeParcel(BASE_UID + 9, BASE_UID + 10)});
    EXPECT_TRUE(status.isOk());

    status = mNetd->networkAddUidRanges(APP_DEFAULT_NETID,
                                        {makeUidRangeParcel(BASE_UID + 11, BASE_UID + 11)});
    EXPECT_TRUE(status.isOk());

    status = mNetd->networkAddUidRanges(APP_DEFAULT_NETID,
                                        {makeUidRangeParcel(BASE_UID + 12, BASE_UID + 13)});
    EXPECT_TRUE(status.isOk());

    status = mNetd->networkAddUidRanges(APP_DEFAULT_NETID,
                                        {makeUidRangeParcel(BASE_UID + 9, BASE_UID + 13)});
    EXPECT_TRUE(status.isOk());

    std::vector<UidRangeParcel> selfOverlappedUidRanges = {
            makeUidRangeParcel(BASE_UID + 20, BASE_UID + 20),
            makeUidRangeParcel(BASE_UID + 20, BASE_UID + 21)};
    status = mNetd->networkAddUidRanges(APP_DEFAULT_NETID, selfOverlappedUidRanges);
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EINVAL, status.serviceSpecificErrorCode());
}

// Verify whether IP rules for app default network are correctly configured.
TEST_F(NetdBinderTest, PerAppDefaultNetwork_VerifyIpRules) {
    const auto& config = makeNativeNetworkConfig(APP_DEFAULT_NETID, NativeNetworkType::PHYSICAL,
                                                 INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(APP_DEFAULT_NETID, sTun.name()).isOk());

    std::vector<UidRangeParcel> uidRanges = {makeUidRangeParcel(BASE_UID + 8005, BASE_UID + 8012),
                                             makeUidRangeParcel(BASE_UID + 8090, BASE_UID + 8099)};

    EXPECT_TRUE(mNetd->networkAddUidRanges(APP_DEFAULT_NETID, uidRanges).isOk());
    verifyAppUidRules({true, true} /*expectedResults*/, uidRanges, sTun.name(),
                      UidRanges::SUB_PRIORITY_HIGHEST);
    EXPECT_TRUE(mNetd->networkRemoveUidRanges(APP_DEFAULT_NETID, {uidRanges.at(0)}).isOk());
    verifyAppUidRules({false, true} /*expectedResults*/, uidRanges, sTun.name(),
                      UidRanges::SUB_PRIORITY_HIGHEST);
    EXPECT_TRUE(mNetd->networkRemoveUidRanges(APP_DEFAULT_NETID, {uidRanges.at(1)}).isOk());
    verifyAppUidRules({false, false} /*expectedResults*/, uidRanges, sTun.name(),
                      UidRanges::SUB_PRIORITY_HIGHEST);

    EXPECT_TRUE(mNetd->networkAddUidRanges(INetd::UNREACHABLE_NET_ID, uidRanges).isOk());
    verifyAppUidRules({true, true} /*expectedResults*/, uidRanges, "",
                      UidRanges::SUB_PRIORITY_HIGHEST);
    EXPECT_TRUE(mNetd->networkRemoveUidRanges(INetd::UNREACHABLE_NET_ID, {uidRanges.at(0)}).isOk());
    verifyAppUidRules({false, true} /*expectedResults*/, uidRanges, "",
                      UidRanges::SUB_PRIORITY_HIGHEST);
    EXPECT_TRUE(mNetd->networkRemoveUidRanges(INetd::UNREACHABLE_NET_ID, {uidRanges.at(1)}).isOk());
    verifyAppUidRules({false, false} /*expectedResults*/, uidRanges, "",
                      UidRanges::SUB_PRIORITY_HIGHEST);
}

// Verify whether packets go through the right network with and without per-app default network.
// Meaning of Fwmark bits (from Fwmark.h):
// 0x0000ffff - Network ID
// 0x00010000 - Explicit mark bit
// 0x00020000 - VPN protect bit
// 0x000c0000 - Permission bits
TEST_F(NetdBinderTest, PerAppDefaultNetwork_ImplicitlySelectNetwork) {
    createDefaultAndOtherPhysicalNetwork(SYSTEM_DEFAULT_NETID, APP_DEFAULT_NETID);

    int systemDefaultFd = sTun.getFdForTesting();
    int appDefaultFd = sTun2.getFdForTesting();

    // Connections go through the system default network.
    expectPacketSentOnNetId(AID_ROOT, SYSTEM_DEFAULT_NETID, systemDefaultFd, IMPLICITLY_SELECT);
    expectPacketSentOnNetId(TEST_UID1, SYSTEM_DEFAULT_NETID, systemDefaultFd, IMPLICITLY_SELECT);

    // Add TEST_UID1 to per-app default network.
    EXPECT_TRUE(mNetd->networkAddUidRanges(APP_DEFAULT_NETID,
                                           {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());
    expectPacketSentOnNetId(AID_ROOT, SYSTEM_DEFAULT_NETID, systemDefaultFd, IMPLICITLY_SELECT);
    expectPacketSentOnNetId(TEST_UID1, APP_DEFAULT_NETID, appDefaultFd, IMPLICITLY_SELECT);

    // Remove TEST_UID1 from per-app default network.
    EXPECT_TRUE(mNetd->networkRemoveUidRanges(APP_DEFAULT_NETID,
                                              {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());
    expectPacketSentOnNetId(AID_ROOT, SYSTEM_DEFAULT_NETID, systemDefaultFd, IMPLICITLY_SELECT);
    expectPacketSentOnNetId(TEST_UID1, SYSTEM_DEFAULT_NETID, systemDefaultFd, IMPLICITLY_SELECT);

    // Prohibit TEST_UID1 from using the default network.
    EXPECT_TRUE(mNetd->networkAddUidRanges(INetd::UNREACHABLE_NET_ID,
                                           {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());
    expectPacketSentOnNetId(AID_ROOT, SYSTEM_DEFAULT_NETID, systemDefaultFd, IMPLICITLY_SELECT);
    expectUnreachableError(TEST_UID1, INetd::UNREACHABLE_NET_ID, IMPLICITLY_SELECT);

    // restore IP rules
    EXPECT_TRUE(mNetd->networkRemoveUidRanges(INetd::UNREACHABLE_NET_ID,
                                              {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());
}

// Verify whether packets go through the right network when app explicitly selects a network.
TEST_F(NetdBinderTest, PerAppDefaultNetwork_ExplicitlySelectNetwork) {
    createDefaultAndOtherPhysicalNetwork(SYSTEM_DEFAULT_NETID, APP_DEFAULT_NETID);

    int systemDefaultFd = sTun.getFdForTesting();
    int appDefaultFd = sTun2.getFdForTesting();

    // Explicitly select the system default network.
    setNetworkForProcess(SYSTEM_DEFAULT_NETID);
    // Connections go through the system default network.
    expectPacketSentOnNetId(AID_ROOT, SYSTEM_DEFAULT_NETID, systemDefaultFd, EXPLICITLY_SELECT);
    expectPacketSentOnNetId(TEST_UID1, SYSTEM_DEFAULT_NETID, systemDefaultFd, EXPLICITLY_SELECT);

    // Set TEST_UID1 to default unreachable, which won't affect the explicitly selected network.
    // Connections go through the system default network.
    EXPECT_TRUE(mNetd->networkAddUidRanges(INetd::UNREACHABLE_NET_ID,
                                           {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());
    expectPacketSentOnNetId(AID_ROOT, SYSTEM_DEFAULT_NETID, systemDefaultFd, EXPLICITLY_SELECT);
    expectPacketSentOnNetId(TEST_UID1, SYSTEM_DEFAULT_NETID, systemDefaultFd, EXPLICITLY_SELECT);

    // restore IP rules
    EXPECT_TRUE(mNetd->networkRemoveUidRanges(INetd::UNREACHABLE_NET_ID,
                                              {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());

    // Add TEST_UID1 to per-app default network, which won't affect the explicitly selected network.
    EXPECT_TRUE(mNetd->networkAddUidRanges(APP_DEFAULT_NETID,
                                           {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());
    expectPacketSentOnNetId(AID_ROOT, SYSTEM_DEFAULT_NETID, systemDefaultFd, EXPLICITLY_SELECT);
    expectPacketSentOnNetId(TEST_UID1, SYSTEM_DEFAULT_NETID, systemDefaultFd, EXPLICITLY_SELECT);

    // Explicitly select the per-app default network.
    setNetworkForProcess(APP_DEFAULT_NETID);
    // Connections go through the per-app default network.
    expectPacketSentOnNetId(AID_ROOT, APP_DEFAULT_NETID, appDefaultFd, EXPLICITLY_SELECT);
    expectPacketSentOnNetId(TEST_UID1, APP_DEFAULT_NETID, appDefaultFd, EXPLICITLY_SELECT);
}

// Verify whether packets go through the right network if app does not implicitly or explicitly
// select any network.
TEST_F(NetdBinderTest, PerAppDefaultNetwork_UnconnectedSocket) {
    createDefaultAndOtherPhysicalNetwork(SYSTEM_DEFAULT_NETID, APP_DEFAULT_NETID);

    int systemDefaultFd = sTun.getFdForTesting();
    int appDefaultFd = sTun2.getFdForTesting();

    // Connections go through the system default network.
    expectPacketSentOnNetId(AID_ROOT, NETID_UNSET, systemDefaultFd, UNCONNECTED_SOCKET);
    expectPacketSentOnNetId(TEST_UID1, NETID_UNSET, systemDefaultFd, UNCONNECTED_SOCKET);

    // Add TEST_UID1 to per-app default network. Traffic should go through the per-app default
    // network if UID is in range. Otherwise, go through the system default network.
    EXPECT_TRUE(mNetd->networkAddUidRanges(APP_DEFAULT_NETID,
                                           {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());
    expectPacketSentOnNetId(AID_ROOT, NETID_UNSET, systemDefaultFd, UNCONNECTED_SOCKET);
    expectPacketSentOnNetId(TEST_UID1, NETID_UNSET, appDefaultFd, UNCONNECTED_SOCKET);

    // Set TEST_UID1's default network to unreachable. Its traffic should still go through the
    // per-app default network. Other traffic go through the system default network.
    // PS: per-app default network take precedence over unreachable network. This should happens
    //     only in the transition period when both rules are briefly set.
    EXPECT_TRUE(mNetd->networkAddUidRanges(INetd::UNREACHABLE_NET_ID,
                                           {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());
    expectPacketSentOnNetId(AID_ROOT, NETID_UNSET, systemDefaultFd, UNCONNECTED_SOCKET);
    expectPacketSentOnNetId(TEST_UID1, NETID_UNSET, appDefaultFd, UNCONNECTED_SOCKET);

    // Remove TEST_UID1's default network from OEM-paid network. Its traffic should get ENETUNREACH
    // error. Other traffic still go through the system default network.
    EXPECT_TRUE(mNetd->networkRemoveUidRanges(APP_DEFAULT_NETID,
                                              {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());
    expectPacketSentOnNetId(AID_ROOT, NETID_UNSET, systemDefaultFd, UNCONNECTED_SOCKET);
    expectUnreachableError(TEST_UID1, NETID_UNSET, UNCONNECTED_SOCKET);

    // restore IP rules
    EXPECT_TRUE(mNetd->networkRemoveUidRanges(INetd::UNREACHABLE_NET_ID,
                                              {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());
}

TEST_F(NetdBinderTest, PerAppDefaultNetwork_PermissionCheck) {
    createPhysicalNetwork(APP_DEFAULT_NETID, sTun2.name(), INetd::PERMISSION_SYSTEM);

    {  // uid is not in app range. Can not set network for process.
        ScopedUidChange scopedUidChange(TEST_UID1);
        EXPECT_EQ(-EACCES, setNetworkForProcess(APP_DEFAULT_NETID));
    }

    EXPECT_TRUE(mNetd->networkAddUidRanges(APP_DEFAULT_NETID,
                                           {makeUidRangeParcel(TEST_UID1, TEST_UID1)})
                        .isOk());

    {  // uid is in app range. Can set network for process.
        ScopedUidChange scopedUidChange(TEST_UID1);
        EXPECT_EQ(0, setNetworkForProcess(APP_DEFAULT_NETID));
    }
}

class VpnParameterizedTest : public NetdBinderTest, public testing::WithParamInterface<bool> {};

// Exercise secure and bypassable VPN.
INSTANTIATE_TEST_SUITE_P(PerAppDefaultNetwork, VpnParameterizedTest, testing::Bool(),
                         [](const testing::TestParamInfo<bool>& info) {
                             return info.param ? "SecureVPN" : "BypassableVPN";
                         });

// Verify per-app default network + VPN.
TEST_P(VpnParameterizedTest, ImplicitlySelectNetwork) {
    const bool isSecureVPN = GetParam();
    createVpnAndAppDefaultNetworkWithUid(
            SYSTEM_DEFAULT_NETID, APP_DEFAULT_NETID, VPN_NETID, isSecureVPN,
            {makeUidRangeParcel(TEST_UID2, TEST_UID1)} /* app range */,
            {makeUidRangeParcel(TEST_UID3, TEST_UID2)} /* VPN range */);

    int systemDefaultFd = sTun.getFdForTesting();
    int appDefaultFd = sTun2.getFdForTesting();
    int vpnFd = sTun3.getFdForTesting();

    // uid is neither in app range, nor in VPN range. Traffic goes through system default network.
    expectPacketSentOnNetId(AID_ROOT, SYSTEM_DEFAULT_NETID, systemDefaultFd, IMPLICITLY_SELECT);
    // uid is in VPN range, not in app range. Traffic goes through VPN.
    expectPacketSentOnNetId(TEST_UID3, (isSecureVPN ? SYSTEM_DEFAULT_NETID : VPN_NETID), vpnFd,
                            IMPLICITLY_SELECT);
    // uid is in app range, not in VPN range. Traffic goes through per-app default network.
    expectPacketSentOnNetId(TEST_UID1, APP_DEFAULT_NETID, appDefaultFd, IMPLICITLY_SELECT);
    // uid is in both app and VPN range. Traffic goes through VPN.
    expectPacketSentOnNetId(TEST_UID2, (isSecureVPN ? APP_DEFAULT_NETID : VPN_NETID), vpnFd,
                            IMPLICITLY_SELECT);
}

class VpnAndSelectNetworkParameterizedTest
    : public NetdBinderTest,
      public testing::WithParamInterface<std::tuple<bool, int>> {};

// Exercise the combination of different VPN types and different user selected networks. e.g.
// secure VPN + select on system default network
// secure VPN + select on app default network
// secure VPN + select on VPN
// bypassable VPN + select on system default network
// ...
INSTANTIATE_TEST_SUITE_P(PerAppDefaultNetwork, VpnAndSelectNetworkParameterizedTest,
                         testing::Combine(testing::Bool(),
                                          testing::Values(SYSTEM_DEFAULT_NETID, APP_DEFAULT_NETID,
                                                          VPN_NETID)),
                         [](const testing::TestParamInfo<std::tuple<bool, int>>& info) {
                             const std::string vpnType = std::get<0>(info.param)
                                                                 ? std::string("SecureVPN")
                                                                 : std::string("BypassableVPN");
                             std::string selectedNetwork;
                             switch (std::get<1>(info.param)) {
                                 case SYSTEM_DEFAULT_NETID:
                                     selectedNetwork = "SystemDefaultNetwork";
                                     break;
                                 case APP_DEFAULT_NETID:
                                     selectedNetwork = "AppDefaultNetwork";
                                     break;
                                 case VPN_NETID:
                                     selectedNetwork = "VPN";
                                     break;
                                 default:
                                     selectedNetwork = "InvalidParameter";  // Should not happen.
                             }
                             return vpnType + "_select" + selectedNetwork;
                         });

TEST_P(VpnAndSelectNetworkParameterizedTest, ExplicitlySelectNetwork) {
    bool isSecureVPN;
    int selectedNetId;
    std::tie(isSecureVPN, selectedNetId) = GetParam();
    createVpnAndAppDefaultNetworkWithUid(
            SYSTEM_DEFAULT_NETID, APP_DEFAULT_NETID, VPN_NETID, isSecureVPN,
            {makeUidRangeParcel(TEST_UID2, TEST_UID1)} /* app range */,
            {makeUidRangeParcel(TEST_UID3, TEST_UID2)} /* VPN range */);

    int expectedFd = -1;
    switch (selectedNetId) {
        case SYSTEM_DEFAULT_NETID:
            expectedFd = sTun.getFdForTesting();
            break;
        case APP_DEFAULT_NETID:
            expectedFd = sTun2.getFdForTesting();
            break;
        case VPN_NETID:
            expectedFd = sTun3.getFdForTesting();
            break;
        default:
            GTEST_LOG_(ERROR) << "unexpected netId:" << selectedNetId;  // Should not happen.
    }

    // In all following permutations, Traffic should go through the specified network if a process
    // can select network for itself. The fwmark should contain process UID and the explicit select
    // bit.
    {  // uid is neither in app range, nor in VPN range. Permission bits, protect bit, and explicit
       // select bit are all set because of AID_ROOT.
        ScopedUidChange scopedUidChange(AID_ROOT);
        EXPECT_EQ(0, setNetworkForProcess(selectedNetId));
        expectPacketSentOnNetId(AID_ROOT, selectedNetId, expectedFd, EXPLICITLY_SELECT);
    }
    {  // uid is in VPN range, not in app range.
        ScopedUidChange scopedUidChange(TEST_UID3);
        // Cannot select non-VPN networks when uid is subject to secure VPN.
        if (isSecureVPN && selectedNetId != VPN_NETID) {
            EXPECT_EQ(-EPERM, setNetworkForProcess(selectedNetId));
        } else {
            EXPECT_EQ(0, setNetworkForProcess(selectedNetId));
            expectPacketSentOnNetId(TEST_UID3, selectedNetId, expectedFd, EXPLICITLY_SELECT);
        }
    }
    {  // uid is in app range, not in VPN range.
        ScopedUidChange scopedUidChange(TEST_UID1);
        // Cannot select the VPN because the VPN does not applies to the UID.
        if (selectedNetId == VPN_NETID) {
            EXPECT_EQ(-EPERM, setNetworkForProcess(selectedNetId));
        } else {
            EXPECT_EQ(0, setNetworkForProcess(selectedNetId));
            expectPacketSentOnNetId(TEST_UID1, selectedNetId, expectedFd, EXPLICITLY_SELECT);
        }
    }
    {  // uid is in both app range and VPN range.
        ScopedUidChange scopedUidChange(TEST_UID2);
        // Cannot select non-VPN networks when uid is subject to secure VPN.
        if (isSecureVPN && selectedNetId != VPN_NETID) {
            EXPECT_EQ(-EPERM, setNetworkForProcess(selectedNetId));
        } else {
            EXPECT_EQ(0, setNetworkForProcess(selectedNetId));
            expectPacketSentOnNetId(TEST_UID2, selectedNetId, expectedFd, EXPLICITLY_SELECT);
        }
    }
}

TEST_P(VpnParameterizedTest, UnconnectedSocket) {
    const bool isSecureVPN = GetParam();
    createVpnAndAppDefaultNetworkWithUid(
            SYSTEM_DEFAULT_NETID, APP_DEFAULT_NETID, VPN_NETID, isSecureVPN,
            {makeUidRangeParcel(TEST_UID2, TEST_UID1)} /* app range */,
            {makeUidRangeParcel(TEST_UID3, TEST_UID2)} /* VPN range */);

    int systemDefaultFd = sTun.getFdForTesting();
    int appDefaultFd = sTun2.getFdForTesting();
    int vpnFd = sTun3.getFdForTesting();

    // uid is neither in app range, nor in VPN range. Traffic goes through system default network.
    expectPacketSentOnNetId(AID_ROOT, NETID_UNSET, systemDefaultFd, UNCONNECTED_SOCKET);
    // uid is in VPN range, not in app range. Traffic goes through VPN.
    expectPacketSentOnNetId(TEST_UID3, NETID_UNSET, vpnFd, UNCONNECTED_SOCKET);
    // uid is in app range, not in VPN range. Traffic goes through per-app default network.
    expectPacketSentOnNetId(TEST_UID1, NETID_UNSET, appDefaultFd, UNCONNECTED_SOCKET);
    // uid is in both app and VPN range. Traffic goes through VPN.
    expectPacketSentOnNetId(TEST_UID2, NETID_UNSET, vpnFd, UNCONNECTED_SOCKET);
}

class VpnLocalRoutesParameterizedTest
    : public NetdBinderTest,
      public testing::WithParamInterface<std::tuple<int, int, bool, bool, bool, bool>> {
  protected:
    // Local/non-local addresses based on the route added in
    // setupNetworkRoutesForVpnAndDefaultNetworks.
    in_addr V4_LOCAL_ADDR = {htonl(0xC0A80008)};      // 192.168.0.8
    in_addr V4_APP_LOCAL_ADDR = {htonl(0xAC100008)};  // 172.16.0.8
    in_addr V4_GLOBAL_ADDR = {htonl(0x08080808)};     // 8.8.8.8

    in6_addr V6_LOCAL_ADDR = {
            {// 2001:db8:cafe::1
             .u6_addr8 = {0x20, 0x01, 0x0d, 0xb8, 0xca, 0xfe, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}}};
    in6_addr V6_APP_LOCAL_ADDR = {
            {// 2607:f0d0:1234::4
             .u6_addr8 = {0x26, 0x07, 0xf0, 0xd0, 0x12, 0x34, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4}}};
    in6_addr V6_GLOBAL_ADDR = {
            {// 2607:1234:1002::4
             .u6_addr8 = {0x26, 0x07, 0x12, 0x34, 0x10, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4}}};
};

const int SEND_TO_GLOBAL = 0;
const int SEND_TO_SYSTEM_DEFAULT_LOCAL = 1;
const int SEND_TO_PER_APP_DEFAULT_LOCAL = 2;

// Exercise the combination of different explicitly selected network, different uid, local/non-local
// address on local route exclusion VPN. E.g.
// explicitlySelected systemDefault + uid in VPN range + no app default + non local address
// explicitlySelected systemDefault + uid in VPN range + has app default + non local address
// explicitlySelected systemDefault + uid in VPN range + has app default + local address
// explicitlySelected appDefault + uid not in VPN range + has app default + non local address
INSTANTIATE_TEST_SUITE_P(
        PerAppDefaultNetwork, VpnLocalRoutesParameterizedTest,
        testing::Combine(testing::Values(SYSTEM_DEFAULT_NETID, APP_DEFAULT_NETID, NETID_UNSET),
                         testing::Values(SEND_TO_GLOBAL, SEND_TO_SYSTEM_DEFAULT_LOCAL,
                                         SEND_TO_PER_APP_DEFAULT_LOCAL),
                         testing::Bool(), testing::Bool(), testing::Bool(), testing::Bool()),
        [](const testing::TestParamInfo<std::tuple<int, int, bool, bool, bool, bool>>& info) {
            std::string explicitlySelected;
            switch (std::get<0>(info.param)) {
                case SYSTEM_DEFAULT_NETID:
                    explicitlySelected = "explicitlySelectedSystemDefault";
                    break;
                case APP_DEFAULT_NETID:
                    explicitlySelected = "explicitlySelectedAppDefault";
                    break;
                case NETID_UNSET:
                    explicitlySelected = "implicitlySelected";
                    break;
                default:
                    explicitlySelected = "InvalidParameter";  // Should not happen.
            }

            std::string sendToAddr;
            switch (std::get<1>(info.param)) {
                case SEND_TO_GLOBAL:
                    sendToAddr = "GlobalAddr";
                    break;
                case SEND_TO_SYSTEM_DEFAULT_LOCAL:
                    sendToAddr = "SystemLocal";
                    break;
                case SEND_TO_PER_APP_DEFAULT_LOCAL:
                    sendToAddr = "AppLocal";
                    break;
                default:
                    sendToAddr = "InvalidAddr";  // Should not happen.
            }

            const std::string isSubjectToVpn = std::get<2>(info.param)
                                                       ? std::string("SubjectToVpn")
                                                       : std::string("NotSubjectToVpn");

            const std::string hasAppDefaultNetwork = std::get<3>(info.param)
                                                             ? std::string("HasAppDefault")
                                                             : std::string("NothasAppDefault");

            const std::string testV6 =
                    std::get<4>(info.param) ? std::string("v6") : std::string("v4");

            // Apply the same or different local address in app default and system default.
            const std::string differentLocalRoutes = std::get<5>(info.param)
                                                             ? std::string("DifferentLocalRoutes")
                                                             : std::string("SameLocalAddr");

            return explicitlySelected + "_uid" + isSubjectToVpn + hasAppDefaultNetwork +
                   "Range_with" + testV6 + sendToAddr + differentLocalRoutes;
        });

int getTargetIfaceForLocalRoutesExclusion(bool isSubjectToVpn, bool hasAppDefaultNetwork,
                                          bool differentLocalRoutes, int sendToAddr,
                                          int selectedNetId, int fallthroughFd, int appDefaultFd,
                                          int vpnFd) {
    int expectedIface;

    // Setup the expected interface based on the condition.
    if (isSubjectToVpn && hasAppDefaultNetwork) {
        switch (sendToAddr) {
            case SEND_TO_GLOBAL:
                expectedIface = vpnFd;
                break;
            case SEND_TO_SYSTEM_DEFAULT_LOCAL:
                // Go to app default if the app default and system default are the same range
                // TODO(b/237351736): It should go to VPN if the system local and app local are
                // different.
                expectedIface = differentLocalRoutes ? fallthroughFd : appDefaultFd;
                break;
            case SEND_TO_PER_APP_DEFAULT_LOCAL:
                expectedIface = appDefaultFd;
                break;
            default:
                expectedIface = -1;  // should not happen
        }
    } else if (isSubjectToVpn && !hasAppDefaultNetwork) {
        switch (sendToAddr) {
            case SEND_TO_GLOBAL:
                expectedIface = vpnFd;
                break;
            case SEND_TO_SYSTEM_DEFAULT_LOCAL:
                // TODO(b/237351736): It should go to app default if the system local and app local
                // are different.
                expectedIface = fallthroughFd;
                break;
            case SEND_TO_PER_APP_DEFAULT_LOCAL:
                // Go to system default if the system default and app default are the same range.
                expectedIface = differentLocalRoutes ? vpnFd : fallthroughFd;
                break;
            default:
                expectedIface = -1;  // should not happen
        }
    } else if (!isSubjectToVpn && hasAppDefaultNetwork) {
        expectedIface = appDefaultFd;
    } else {  // !isSubjectToVpn && !hasAppDefaultNetwork
        expectedIface = fallthroughFd;
    }

    // Override the target if it's explicitly selected.
    switch (selectedNetId) {
        case SYSTEM_DEFAULT_NETID:
            expectedIface = fallthroughFd;
            break;
        case APP_DEFAULT_NETID:
            expectedIface = appDefaultFd;
            break;
        default:
            break;
            // Based on the uid range.
    }

    return expectedIface;
}

// Routes configured on the system default network and on the VPN.
// This allows the test to verify the worst case where the physical network and the VPN configure
// the same routes. This ensures that routing is determined by the IP rules and doesn't just happen
// to work because the routes don't overlap. If differentLocalRoutes is false, these routes are also
// configured on the per-app default network.
// For both IPv4 and IPv6, the first route is local, the second is not.
std::vector<std::string> SYSTEM_DEFAULT_ROUTES = {"192.168.0.0/16", "0.0.0.0/0",
                                                  "2001:db8:cafe::/48", "::/0"};
// Routes configured on the per-app default network if differentLocalRoutes is true.
// For both IPv4 and IPv6, the first route is local, the second is not.
std::vector<std::string> APP_DEFAULT_ROUTES = {"172.16.0.0/16", "0.0.0.0/0", "2607:f0d0:1234::/48",
                                               "::/0"};
void NetdBinderTest::setupNetworkRoutesForVpnAndDefaultNetworks(
        int systemDefaultNetId, int appDefaultNetId, int vpnNetId, int otherNetId, bool secure,
        bool testV6, bool differentLocalRoutes, std::vector<UidRangeParcel>&& appDefaultUidRanges,
        std::vector<UidRangeParcel>&& vpnUidRanges) {
    // Create a physical network on sTun, and set it as the system default network
    createAndSetDefaultNetwork(systemDefaultNetId, sTun.name());

    // Routes are configured to system default, app default and vpn network to verify if the packets
    // are routed correctly.

    // Setup system default routing.
    for (const auto& route : SYSTEM_DEFAULT_ROUTES) {
        EXPECT_TRUE(mNetd->networkAddRoute(systemDefaultNetId, sTun.name(), route, "").isOk());
    }

    // Create another physical network on sTun2 as per app default network
    createPhysicalNetwork(appDefaultNetId, sTun2.name());

    // Setup app default routing.
    std::vector<std::string> appDefaultRoutes =
            (differentLocalRoutes ? APP_DEFAULT_ROUTES : SYSTEM_DEFAULT_ROUTES);
    for (const auto& route : appDefaultRoutes) {
        EXPECT_TRUE(mNetd->networkAddRoute(appDefaultNetId, sTun2.name(), route, "").isOk());
    }

    // Create a bypassable VPN on sTun3.
    auto config = makeNativeNetworkConfig(vpnNetId, NativeNetworkType::VIRTUAL,
                                          INetd::PERMISSION_NONE, secure, true);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(vpnNetId, sTun3.name()).isOk());

    // Setup vpn routing.
    for (const auto& route : SYSTEM_DEFAULT_ROUTES) {
        EXPECT_TRUE(mNetd->networkAddRoute(vpnNetId, sTun3.name(), route, "").isOk());
    }

    // Create another interface that is neither system default nor the app default to make sure
    // the traffic won't be mis-routed.
    createPhysicalNetwork(otherNetId, sTun4.name());
    EXPECT_TRUE(mNetd->networkAddRoute(otherNetId, sTun4.name(), testV6 ? "::/0" : "0.0.0.0/0", "")
                        .isOk());
    // Add per-app uid ranges.
    EXPECT_TRUE(mNetd->networkAddUidRanges(appDefaultNetId, appDefaultUidRanges).isOk());

    // Add VPN uid ranges.
    EXPECT_TRUE(mNetd->networkAddUidRanges(vpnNetId, vpnUidRanges).isOk());
}

// Rules are in approximately the following order for bypassable VPNs that allow local network
// access:
//    - Local routes to the per-app default network (UID guarded)
//    - Local routes to the system default network
//    - Both local and global routs to VPN network (UID guarded)
//    - Global routes to per-app default network(UID guarded)
//    - Global routes to system default network
TEST_P(VpnLocalRoutesParameterizedTest, localRoutesExclusion) {
    int selectedNetId;
    int sendToAddr;
    bool isSubjectToVpn;
    bool hasAppDefaultNetwork;
    bool testV6;
    bool differentLocalRoutes;

    std::tie(selectedNetId, sendToAddr, isSubjectToVpn, hasAppDefaultNetwork, testV6,
             differentLocalRoutes) = GetParam();

    setupNetworkRoutesForVpnAndDefaultNetworks(
            SYSTEM_DEFAULT_NETID, APP_DEFAULT_NETID, VPN_NETID, TEST_NETID4, false /* secure */,
            testV6, differentLocalRoutes,
            // Setup uid ranges for app default and VPN. Configure TEST_UID2 into both app default
            // and VPN to verify the behavior when the uid exists in both network.
            {makeUidRangeParcel(TEST_UID2, TEST_UID1)}, {makeUidRangeParcel(TEST_UID3, TEST_UID2)});

    int fallthroughFd = sTun.getFdForTesting();
    int appDefaultFd = sTun2.getFdForTesting();
    int vpnFd = sTun3.getFdForTesting();

    // Explicitly select network
    setNetworkForProcess(selectedNetId);

    int targetUid;

    // Setup the expected testing uid
    if (isSubjectToVpn) {
        if (hasAppDefaultNetwork) {
            targetUid = TEST_UID2;
        } else {
            targetUid = TEST_UID3;
        }
    } else {
        if (hasAppDefaultNetwork) {
            targetUid = TEST_UID1;
        } else {
            targetUid = TEST_UID4;  // Not in any of the UID ranges.
        }
    }

    // Get expected interface for the traffic.
    int expectedIface = getTargetIfaceForLocalRoutesExclusion(
            isSubjectToVpn, hasAppDefaultNetwork, differentLocalRoutes, sendToAddr, selectedNetId,
            fallthroughFd, appDefaultFd, vpnFd);

    // Verify the packets are sent to the expected interface.
    Fwmark fwmark;
    if (testV6) {
        in6_addr addr;
        switch (sendToAddr) {
            case SEND_TO_GLOBAL:
                addr = V6_GLOBAL_ADDR;
                break;
            case SEND_TO_SYSTEM_DEFAULT_LOCAL:
                addr = V6_LOCAL_ADDR;
                break;
            case SEND_TO_PER_APP_DEFAULT_LOCAL:
                addr = differentLocalRoutes ? V6_APP_LOCAL_ADDR : V6_LOCAL_ADDR;
                break;
            default:
                break;
                // should not happen
        }
        EXPECT_TRUE(sendIPv6PacketFromUid(targetUid, addr, &fwmark, expectedIface));
    } else {
        in_addr addr;
        switch (sendToAddr) {
            case SEND_TO_GLOBAL:
                addr = V4_GLOBAL_ADDR;
                break;
            case SEND_TO_SYSTEM_DEFAULT_LOCAL:
                addr = V4_LOCAL_ADDR;
                break;
            case SEND_TO_PER_APP_DEFAULT_LOCAL:
                addr = differentLocalRoutes ? V4_APP_LOCAL_ADDR : V4_LOCAL_ADDR;
                break;
            default:
                break;
                // should not happen
        }

        EXPECT_TRUE(sendIPv4PacketFromUid(targetUid, addr, &fwmark, expectedIface));
    }
}

TEST_F(NetdBinderTest, NetworkCreate) {
    auto config = makeNativeNetworkConfig(TEST_NETID1, NativeNetworkType::PHYSICAL,
                                          INetd::PERMISSION_NONE, false, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkDestroy(config.netId).isOk());

    config.networkType = NativeNetworkType::VIRTUAL;
    config.secure = true;
    config.vpnType = NativeVpnType::OEM;
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());

    // invalid network type
    auto wrongConfig = makeNativeNetworkConfig(TEST_NETID2, static_cast<NativeNetworkType>(-1),
                                               INetd::PERMISSION_NONE, false, false);
    EXPECT_EQ(EINVAL, mNetd->networkCreate(wrongConfig).serviceSpecificErrorCode());

    // invalid VPN type
    wrongConfig.networkType = NativeNetworkType::VIRTUAL;
    wrongConfig.vpnType = static_cast<NativeVpnType>(-1);
    EXPECT_EQ(EINVAL, mNetd->networkCreate(wrongConfig).serviceSpecificErrorCode());
}

// Verifies valid and invalid inputs on networkAddUidRangesParcel method.
TEST_F(NetdBinderTest, UidRangeSubPriority_ValidateInputs) {
    createVpnAndOtherPhysicalNetwork(SYSTEM_DEFAULT_NETID, APP_DEFAULT_NETID, VPN_NETID,
                                     /*isSecureVPN=*/true);
    // Invalid priority -10 on a physical network.
    NativeUidRangeConfig uidRangeConfig =
            makeNativeUidRangeConfig(APP_DEFAULT_NETID, {makeUidRangeParcel(BASE_UID, BASE_UID)},
                                     UidRanges::SUB_PRIORITY_HIGHEST - 10);
    binder::Status status = mNetd->networkAddUidRangesParcel(uidRangeConfig);
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EINVAL, status.serviceSpecificErrorCode());

    // Invalid priority 1000 on a physical network.
    uidRangeConfig.subPriority = UidRanges::SUB_PRIORITY_NO_DEFAULT + 1;
    status = mNetd->networkAddUidRangesParcel(uidRangeConfig);
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EINVAL, status.serviceSpecificErrorCode());

    // Virtual networks support only default priority.
    uidRangeConfig.netId = VPN_NETID;
    uidRangeConfig.subPriority = SUB_PRIORITY_1;
    status = mNetd->networkAddUidRangesParcel(uidRangeConfig);
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EINVAL, status.serviceSpecificErrorCode());

    // For a single network, identical UID ranges with different priorities are allowed.
    uidRangeConfig.netId = APP_DEFAULT_NETID;
    uidRangeConfig.subPriority = SUB_PRIORITY_1;
    EXPECT_TRUE(mNetd->networkAddUidRangesParcel(uidRangeConfig).isOk());
    uidRangeConfig.subPriority = SUB_PRIORITY_2;
    EXPECT_TRUE(mNetd->networkAddUidRangesParcel(uidRangeConfig).isOk());

    // Overlapping ranges is invalid.
    uidRangeConfig.uidRanges = {makeUidRangeParcel(BASE_UID + 1, BASE_UID + 1),
                                makeUidRangeParcel(BASE_UID + 1, BASE_UID + 1)};
    status = mNetd->networkAddUidRangesParcel(uidRangeConfig);
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EINVAL, status.serviceSpecificErrorCode());
}

// Examines whether IP rules for app default network with subsidiary priorities are correctly added
// and removed.
TEST_F(NetdBinderTest, UidRangeSubPriority_VerifyPhysicalNwIpRules) {
    createPhysicalNetwork(TEST_NETID1, sTun.name());
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID1, sTun.name(), "::/0", "").isOk());
    createPhysicalNetwork(TEST_NETID2, sTun2.name());
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID2, sTun2.name(), "::/0", "").isOk());

    // Adds priority 1 setting
    NativeUidRangeConfig uidRangeConfig1 = makeNativeUidRangeConfig(
            TEST_NETID1, {makeUidRangeParcel(BASE_UID, BASE_UID)}, SUB_PRIORITY_1);
    EXPECT_TRUE(mNetd->networkAddUidRangesParcel(uidRangeConfig1).isOk());
    verifyAppUidRules({true}, uidRangeConfig1, sTun.name());
    // Adds priority 2 setting
    NativeUidRangeConfig uidRangeConfig2 = makeNativeUidRangeConfig(
            TEST_NETID2, {makeUidRangeParcel(BASE_UID + 1, BASE_UID + 1)}, SUB_PRIORITY_2);
    EXPECT_TRUE(mNetd->networkAddUidRangesParcel(uidRangeConfig2).isOk());
    verifyAppUidRules({true}, uidRangeConfig2, sTun2.name());
    // Adds another priority 2 setting
    NativeUidRangeConfig uidRangeConfig3 = makeNativeUidRangeConfig(
            INetd::UNREACHABLE_NET_ID, {makeUidRangeParcel(BASE_UID + 2, BASE_UID + 2)},
            SUB_PRIORITY_2);
    EXPECT_TRUE(mNetd->networkAddUidRangesParcel(uidRangeConfig3).isOk());
    verifyAppUidRules({true}, uidRangeConfig3, "");

    // Removes.
    EXPECT_TRUE(mNetd->networkRemoveUidRangesParcel(uidRangeConfig1).isOk());
    verifyAppUidRules({false}, uidRangeConfig1, sTun.name());
    verifyAppUidRules({true}, uidRangeConfig2, sTun2.name());
    verifyAppUidRules({true}, uidRangeConfig3, "");
    EXPECT_TRUE(mNetd->networkRemoveUidRangesParcel(uidRangeConfig2).isOk());
    verifyAppUidRules({false}, uidRangeConfig1, sTun.name());
    verifyAppUidRules({false}, uidRangeConfig2, sTun2.name());
    verifyAppUidRules({true}, uidRangeConfig3, "");
    EXPECT_TRUE(mNetd->networkRemoveUidRangesParcel(uidRangeConfig3).isOk());
    verifyAppUidRules({false}, uidRangeConfig1, sTun.name());
    verifyAppUidRules({false}, uidRangeConfig2, sTun2.name());
    verifyAppUidRules({false}, uidRangeConfig3, "");
}

// Verify uid range rules on virtual network.
TEST_P(VpnParameterizedTest, UidRangeSubPriority_VerifyVpnIpRules) {
    const bool isSecureVPN = GetParam();
    constexpr int VPN_NETID2 = TEST_NETID2;

    // Create 2 VPNs, using sTun and sTun2.
    auto config = makeNativeNetworkConfig(VPN_NETID, NativeNetworkType::VIRTUAL,
                                          INetd::PERMISSION_NONE, isSecureVPN, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(VPN_NETID, sTun.name()).isOk());

    config = makeNativeNetworkConfig(VPN_NETID2, NativeNetworkType::VIRTUAL, INetd::PERMISSION_NONE,
                                     isSecureVPN, false);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(VPN_NETID2, sTun2.name()).isOk());

    // Assign uid ranges to different VPNs. Check if rules match.
    NativeUidRangeConfig uidRangeConfig1 = makeNativeUidRangeConfig(
            VPN_NETID, {makeUidRangeParcel(BASE_UID, BASE_UID)}, UidRanges::SUB_PRIORITY_HIGHEST);
    EXPECT_TRUE(mNetd->networkAddUidRangesParcel(uidRangeConfig1).isOk());
    verifyVpnUidRules({true}, uidRangeConfig1, sTun.name(), isSecureVPN, false);

    NativeUidRangeConfig uidRangeConfig2 =
            makeNativeUidRangeConfig(VPN_NETID2, {makeUidRangeParcel(BASE_UID + 1, BASE_UID + 1)},
                                     UidRanges::SUB_PRIORITY_HIGHEST);
    EXPECT_TRUE(mNetd->networkAddUidRangesParcel(uidRangeConfig2).isOk());
    verifyVpnUidRules({true}, uidRangeConfig2, sTun2.name(), isSecureVPN, false);

    // Remove uid configs one-by-one. Check if rules match.
    EXPECT_TRUE(mNetd->networkRemoveUidRangesParcel(uidRangeConfig1).isOk());
    verifyVpnUidRules({false}, uidRangeConfig1, sTun.name(), isSecureVPN, false);
    verifyVpnUidRules({true}, uidRangeConfig2, sTun2.name(), isSecureVPN, false);
    EXPECT_TRUE(mNetd->networkRemoveUidRangesParcel(uidRangeConfig2).isOk());
    verifyVpnUidRules({false}, uidRangeConfig1, sTun.name(), isSecureVPN, false);
    verifyVpnUidRules({false}, uidRangeConfig2, sTun2.name(), isSecureVPN, false);
}

// Verify VPN ip rule on bypassable/secureVPN virtual network with local routes excluded
TEST_P(VpnParameterizedTest, VerifyVpnIpRules_excludeLocalRoutes) {
    const bool isSecureVPN = GetParam();
    // Create VPN with local route excluded
    auto config = makeNativeNetworkConfig(VPN_NETID, NativeNetworkType::VIRTUAL,
                                          INetd::PERMISSION_NONE, isSecureVPN, true);
    EXPECT_TRUE(mNetd->networkCreate(config).isOk());
    EXPECT_TRUE(mNetd->networkAddInterface(VPN_NETID, sTun.name()).isOk());

    // Assign uid ranges to VPN. Check if rules match.
    NativeUidRangeConfig uidRangeConfig1 = makeNativeUidRangeConfig(
            VPN_NETID, {makeUidRangeParcel(BASE_UID, BASE_UID)}, UidRanges::SUB_PRIORITY_HIGHEST);
    EXPECT_TRUE(mNetd->networkAddUidRangesParcel(uidRangeConfig1).isOk());
    verifyVpnUidRules({true}, uidRangeConfig1, sTun.name(), isSecureVPN, true);

    // Remove uid configs. Check if rules match.
    EXPECT_TRUE(mNetd->networkRemoveUidRangesParcel(uidRangeConfig1).isOk());
    verifyVpnUidRules({false}, uidRangeConfig1, sTun.name(), isSecureVPN, true);
}

// Verify if packets go through the right network when subsidiary priority and VPN works together.
//
// Test config:
// +----------+------------------------+-------------------------------------------+
// | Priority |          UID           |             Assigned Network              |
// +----------+------------------------+-------------------------------------------+
// |        0 | TEST_UID1              | VPN bypassable (VPN_NETID)                |
// +----------+------------------------+-------------------------------------------+
// |        1 | TEST_UID1, TEST_UID2,  | Physical Network 1 (APP_DEFAULT_1_NETID)  |
// |        1 | TEST_UID3              | Physical Network 2 (APP_DEFAULT_2_NETID)  |
// |        1 | TEST_UID5              | Unreachable Network (UNREACHABLE_NET_ID)  |
// +----------+------------------------+-------------------------------------------+
// |        2 | TEST_UID3              | Physical Network 1 (APP_DEFAULT_1_NETID)  |
// |        2 | TEST_UID4, TEST_UID5   | Physical Network 2 (APP_DEFAULT_2_NETID)  |
// +----------+------------------------+-------------------------------------------+
//
// Expected results:
// +-----------+------------------------+
// |    UID    |    Using Network       |
// +-----------+------------------------+
// | TEST_UID1 | VPN                    |
// | TEST_UID2 | Physical Network 1     |
// | TEST_UID3 | Physical Network 2     |
// | TEST_UID4 | Physical Network 2     |
// | TEST_UID5 | Unreachable Network    |
// | TEST_UID6 | System Default Network |
// +-----------+------------------------+
//
// SYSTEM_DEFAULT_NETID uses sTun.
// APP_DEFAULT_1_NETID uses sTun2.
// VPN_NETID uses sTun3.
// APP_DEFAULT_2_NETID uses sTun4.
//
TEST_F(NetdBinderTest, UidRangeSubPriority_ImplicitlySelectNetwork) {
    constexpr int APP_DEFAULT_1_NETID = TEST_NETID2;
    constexpr int APP_DEFAULT_2_NETID = TEST_NETID4;

    static const struct TestData {
        uint32_t subPriority;
        std::vector<UidRangeParcel> uidRanges;
        unsigned int netId;
    } kTestData[] = {
            {UidRanges::SUB_PRIORITY_HIGHEST, {makeUidRangeParcel(TEST_UID1)}, VPN_NETID},
            {SUB_PRIORITY_1,
             {makeUidRangeParcel(TEST_UID1), makeUidRangeParcel(TEST_UID2)},
             APP_DEFAULT_1_NETID},
            {SUB_PRIORITY_1, {makeUidRangeParcel(TEST_UID3)}, APP_DEFAULT_2_NETID},
            {SUB_PRIORITY_1, {makeUidRangeParcel(TEST_UID5)}, INetd::UNREACHABLE_NET_ID},
            {SUB_PRIORITY_2, {makeUidRangeParcel(TEST_UID3)}, APP_DEFAULT_1_NETID},
            {SUB_PRIORITY_2,
             {makeUidRangeParcel(TEST_UID4), makeUidRangeParcel(TEST_UID5)},
             APP_DEFAULT_2_NETID},
    };

    // Creates 4 networks.
    createVpnAndOtherPhysicalNetwork(SYSTEM_DEFAULT_NETID, APP_DEFAULT_1_NETID, VPN_NETID,
                                     /*isSecureVPN=*/false);
    createPhysicalNetwork(APP_DEFAULT_2_NETID, sTun4.name());
    EXPECT_TRUE(mNetd->networkAddRoute(APP_DEFAULT_2_NETID, sTun4.name(), "::/0", "").isOk());

    for (const auto& td : kTestData) {
        NativeUidRangeConfig uidRangeConfig =
                makeNativeUidRangeConfig(td.netId, td.uidRanges, td.subPriority);
        EXPECT_TRUE(mNetd->networkAddUidRangesParcel(uidRangeConfig).isOk());
    }

    int systemDefaultFd = sTun.getFdForTesting();
    int appDefault_1_Fd = sTun2.getFdForTesting();
    int vpnFd = sTun3.getFdForTesting();
    int appDefault_2_Fd = sTun4.getFdForTesting();
    // Verify routings.
    expectPacketSentOnNetId(TEST_UID1, VPN_NETID, vpnFd, IMPLICITLY_SELECT);
    expectPacketSentOnNetId(TEST_UID2, APP_DEFAULT_1_NETID, appDefault_1_Fd, IMPLICITLY_SELECT);
    expectPacketSentOnNetId(TEST_UID3, APP_DEFAULT_2_NETID, appDefault_2_Fd, IMPLICITLY_SELECT);
    expectPacketSentOnNetId(TEST_UID4, APP_DEFAULT_2_NETID, appDefault_2_Fd, IMPLICITLY_SELECT);
    expectUnreachableError(TEST_UID5, INetd::UNREACHABLE_NET_ID, IMPLICITLY_SELECT);
    expectPacketSentOnNetId(TEST_UID6, SYSTEM_DEFAULT_NETID, systemDefaultFd, IMPLICITLY_SELECT);

    // Remove test rules from the unreachable network.
    for (const auto& td : kTestData) {
        if (td.netId == INetd::UNREACHABLE_NET_ID) {
            NativeUidRangeConfig uidRangeConfig =
                    makeNativeUidRangeConfig(td.netId, td.uidRanges, td.subPriority);
            EXPECT_TRUE(mNetd->networkRemoveUidRangesParcel(uidRangeConfig).isOk());
        }
    }
}

class PerAppNetworkPermissionsTest : public NetdBinderTest {
  public:
    int bindSocketToNetwork(int sock, int netId, bool explicitlySelected) {
        ScopedUidChange uidChange(AID_ROOT);
        Fwmark fwmark;
        fwmark.explicitlySelected = explicitlySelected;
        fwmark.netId = netId;
        return setsockopt(sock, SOL_SOCKET, SO_MARK, &(fwmark.intValue), sizeof(fwmark.intValue));
    }

    void changeNetworkPermissionForUid(int netId, int uid, bool add) {
        auto nativeUidRangeConfig = makeNativeUidRangeConfig(netId, {makeUidRangeParcel(uid, uid)},
                                                             UidRanges::SUB_PRIORITY_NO_DEFAULT);
        ScopedUidChange rootUid(AID_ROOT);
        if (add) {
            EXPECT_TRUE(mNetd->networkAddUidRangesParcel(nativeUidRangeConfig).isOk());
        } else {
            EXPECT_TRUE(mNetd->networkRemoveUidRangesParcel(nativeUidRangeConfig).isOk());
        }
    }

  protected:
    static inline const sockaddr_in6 TEST_SOCKADDR_IN6 = {
            .sin6_family = AF_INET6,
            .sin6_port = 42,
            .sin6_addr = V6_ADDR,
    };
    std::array<char, 4096> mTestBuf;
};

TEST_F(PerAppNetworkPermissionsTest, HasExplicitAccess) {
    // TEST_NETID1 -> restricted network
    createPhysicalNetwork(TEST_NETID1, sTun.name(), INetd::PERMISSION_SYSTEM);
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID1, sTun.name(), "::/0", "").isOk());

    // Change uid to uid without PERMISSION_SYSTEM
    ScopedUidChange testUid(TEST_UID1);
    unique_fd sock(socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0));
    EXPECT_EQ(bindSocketToNetwork(sock, TEST_NETID1, true /*explicitlySelected*/), 0);

    // Test without permissions should fail
    EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), -1);

    // Test access with permission succeeds and packet is routed correctly
    changeNetworkPermissionForUid(TEST_NETID1, TEST_UID1, true /*add*/);
    EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), 0);
    EXPECT_EQ(send(sock, "foo", sizeof("foo"), 0), (int)sizeof("foo"));
    EXPECT_GT(read(sTun.getFdForTesting(), mTestBuf.data(), mTestBuf.size()), 0);

    // Test removing permissions.
    // Note: Send will still succeed as the destination is cached in
    // sock.sk_dest_cache. Try another connect instead.
    changeNetworkPermissionForUid(TEST_NETID1, TEST_UID1, false /*add*/);
    EXPECT_EQ(-1, connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)));
}

TEST_F(PerAppNetworkPermissionsTest, HasImplicitAccess) {
    // TEST_NETID1 -> restricted network
    createPhysicalNetwork(TEST_NETID1, sTun.name(), INetd::PERMISSION_SYSTEM);
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID1, sTun.name(), "::/0", "").isOk());

    // Change uid to uid without PERMISSION_SYSTEM
    ScopedUidChange testUid(TEST_UID1);
    unique_fd sock(socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0));
    EXPECT_EQ(bindSocketToNetwork(sock, TEST_NETID1, false /*explicitlySelected*/), 0);

    // Note: we cannot call connect() when implicitly selecting the network as
    // the fwmark would get reset to the default network.
    // Call connect which should bind socket to default network
    EXPECT_EQ(sendto(sock, "foo", sizeof("foo"), 0, (sockaddr*)&TEST_SOCKADDR_IN6,
                     sizeof(TEST_SOCKADDR_IN6)),
              -1);

    // Test access with permission succeeds and packet is routed correctly
    changeNetworkPermissionForUid(TEST_NETID1, TEST_UID1, true /*add*/);
    EXPECT_EQ(sendto(sock, "foo", sizeof("foo"), 0, (sockaddr*)&TEST_SOCKADDR_IN6,
                     sizeof(TEST_SOCKADDR_IN6)),
              (int)sizeof("foo"));
    EXPECT_GT(read(sTun.getFdForTesting(), mTestBuf.data(), mTestBuf.size()), 0);
}

TEST_F(PerAppNetworkPermissionsTest, DoesNotAffectDefaultNetworkSelection) {
    // TEST_NETID1 -> default network
    // TEST_NETID2 -> restricted network
    createPhysicalNetwork(TEST_NETID1, sTun.name(), INetd::PERMISSION_NONE);
    createPhysicalNetwork(TEST_NETID2, sTun2.name(), INetd::PERMISSION_SYSTEM);
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID1, sTun.name(), "::/0", "").isOk());
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID2, sTun2.name(), "::/0", "").isOk());
    mNetd->networkSetDefault(TEST_NETID1);

    changeNetworkPermissionForUid(TEST_NETID2, TEST_UID1, true /*add*/);

    // Change uid to uid without PERMISSION_SYSTEM
    ScopedUidChange testUid(TEST_UID1);
    unique_fd sock(socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0));

    // Connect should select default network
    EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), 0);
    EXPECT_EQ(send(sock, "foo", sizeof("foo"), 0), (int)sizeof("foo"));
    EXPECT_GT(read(sTun.getFdForTesting(), mTestBuf.data(), mTestBuf.size()), 0);
}

TEST_F(PerAppNetworkPermissionsTest, PermissionDoesNotAffectPerAppDefaultNetworkSelection) {
    // TEST_NETID1 -> restricted app default network
    // TEST_NETID2 -> restricted network
    createPhysicalNetwork(TEST_NETID1, sTun.name(), INetd::PERMISSION_SYSTEM);
    createPhysicalNetwork(TEST_NETID2, sTun2.name(), INetd::PERMISSION_SYSTEM);
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID1, sTun.name(), "::/0", "").isOk());
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID2, sTun2.name(), "::/0", "").isOk());

    auto nativeUidRangeConfig = makeNativeUidRangeConfig(
            TEST_NETID1, {makeUidRangeParcel(TEST_UID1, TEST_UID1)}, 0 /*subPriority*/);
    EXPECT_TRUE(mNetd->networkAddUidRangesParcel(nativeUidRangeConfig).isOk());
    changeNetworkPermissionForUid(TEST_NETID2, TEST_UID1, true /*add*/);

    // Change uid to uid without PERMISSION_SYSTEM
    ScopedUidChange testUid(TEST_UID1);
    unique_fd sock(socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0));

    // Connect should select app default network
    EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), 0);
    EXPECT_EQ(send(sock, "foo", sizeof("foo"), 0), (int)sizeof("foo"));
    EXPECT_GT(read(sTun.getFdForTesting(), mTestBuf.data(), mTestBuf.size()), 0);
}

TEST_F(PerAppNetworkPermissionsTest, PermissionOnlyAffectsUid) {
    // TEST_NETID1 -> restricted network
    // TEST_NETID2 -> restricted network
    createPhysicalNetwork(TEST_NETID1, sTun.name(), INetd::PERMISSION_SYSTEM);
    createPhysicalNetwork(TEST_NETID2, sTun2.name(), INetd::PERMISSION_SYSTEM);
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID1, sTun.name(), "::/0", "").isOk());
    EXPECT_TRUE(mNetd->networkAddRoute(TEST_NETID2, sTun2.name(), "::/0", "").isOk());

    // test that neither TEST_UID1, nor TEST_UID2 have access without permission
    {
        // TEST_UID1
        ScopedUidChange testUid(TEST_UID1);
        unique_fd sock(socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0));
        // TEST_NETID1
        EXPECT_EQ(bindSocketToNetwork(sock, TEST_NETID1, true /*explicitlySelected*/), 0);
        EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), -1);
        // TEST_NETID2
        EXPECT_EQ(bindSocketToNetwork(sock, TEST_NETID2, true /*explicitlySelected*/), 0);
        EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), -1);
    }
    {
        // TEST_UID2
        ScopedUidChange testUid(TEST_UID2);
        unique_fd sock(socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0));
        // TEST_NETID1
        EXPECT_EQ(bindSocketToNetwork(sock, TEST_NETID1, true /*explicitlySelected*/), 0);
        EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), -1);
        // TEST_NETID2
        EXPECT_EQ(bindSocketToNetwork(sock, TEST_NETID2, true /*explicitlySelected*/), 0);
        EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), -1);
    }

    changeNetworkPermissionForUid(TEST_NETID1, TEST_UID1, true);

    // test that TEST_UID1 has access to TEST_UID1
    {
        // TEST_UID1
        ScopedUidChange testUid(TEST_UID1);
        unique_fd sock(socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0));
        // TEST_NETID1
        EXPECT_EQ(bindSocketToNetwork(sock, TEST_NETID1, true /*explicitlySelected*/), 0);
        EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), 0);
        // TEST_NETID2
        EXPECT_EQ(bindSocketToNetwork(sock, TEST_NETID2, true /*explicitlySelected*/), 0);
        EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), -1);
    }
    {
        // TEST_UID2
        ScopedUidChange testUid(TEST_UID2);
        unique_fd sock(socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0));
        // TEST_NETID1
        EXPECT_EQ(bindSocketToNetwork(sock, TEST_NETID1, true /*explicitlySelected*/), 0);
        EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), -1);
        // TEST_NETID2
        EXPECT_EQ(bindSocketToNetwork(sock, TEST_NETID2, true /*explicitlySelected*/), 0);
        EXPECT_EQ(connect(sock, (sockaddr*)&TEST_SOCKADDR_IN6, sizeof(TEST_SOCKADDR_IN6)), -1);
    }
}

// Creates a system default network and 3 enterprise networks for two profiles. Check if network
// selection in compliance with network allow list settings.
//
// +-----------+-----------------------+----------------------------------------+
// |    UID    | UID's default network | UID can select networks                |
// +-----------+-----------------------+----------------------------------------+
// | TEST_UID1 | ENTERPRISE_NETID_1    | ENTERPRISE_NETID_1, ENTERPRISE_NETID_2 |
// | TEST_UID2 | ENTERPRISE_NETID_3    | ENTERPRISE_NETID_3                     |
// +-----------+-----------------------+----------------------------------------+
TEST_F(NetdBinderTest, PerProfileNetworkPermission) {
    // creates 4 networks
    createDefaultAndOtherPhysicalNetwork(SYSTEM_DEFAULT_NETID, ENTERPRISE_NETID_1);
    createPhysicalNetwork(ENTERPRISE_NETID_2, sTun3.name());
    EXPECT_TRUE(mNetd->networkAddRoute(ENTERPRISE_NETID_2, sTun3.name(), "::/0", "").isOk());
    createPhysicalNetwork(ENTERPRISE_NETID_3, sTun4.name());
    EXPECT_TRUE(mNetd->networkAddRoute(ENTERPRISE_NETID_3, sTun4.name(), "::/0", "").isOk());

    // profile#1
    // UidRanges::SUB_PRIORITY_HIGHEST + 20 = PREFERENCE_ORDER_PROFILE, which is defined in
    // ConnectivityService.java. The value here doesn't really matter because user allowed network
    // does not depends on specific sub-priority.
    NativeUidRangeConfig cfg1 =
            makeNativeUidRangeConfig(ENTERPRISE_NETID_1, {makeUidRangeParcel(TEST_UID1, TEST_UID1)},
                                     UidRanges::SUB_PRIORITY_HIGHEST + 20);
    EXPECT_TRUE(mNetd->networkAddUidRangesParcel(cfg1).isOk());

    // profile#2
    NativeUidRangeConfig cfg2 =
            makeNativeUidRangeConfig(ENTERPRISE_NETID_3, {makeUidRangeParcel(TEST_UID2, TEST_UID2)},
                                     UidRanges::SUB_PRIORITY_HIGHEST + 20);
    EXPECT_TRUE(mNetd->networkAddUidRangesParcel(cfg2).isOk());

    // setNetworkAllowlist at once
    // all uids except for TEST_UID2
    NativeUidRangeConfig nw1UserConfig = makeNativeUidRangeConfig(
            ENTERPRISE_NETID_1,
            {makeUidRangeParcel(0, TEST_UID3), makeUidRangeParcel(TEST_UID1, TEST_UID1)},
            /*unused*/ 0);
    NativeUidRangeConfig nw2UserConfig = makeNativeUidRangeConfig(
            ENTERPRISE_NETID_2,
            {makeUidRangeParcel(0, TEST_UID3), makeUidRangeParcel(TEST_UID1, TEST_UID1)},
            /*unused*/ 0);
    // all uids except for TEST_UID1
    NativeUidRangeConfig nw3UserConfig = makeNativeUidRangeConfig(
            ENTERPRISE_NETID_3, {makeUidRangeParcel(0, TEST_UID2)}, /*unused*/ 0);
    // all uids except for TEST_UID1 and TEST_UID2
    NativeUidRangeConfig nwDefaultUserConfig = makeNativeUidRangeConfig(
            SYSTEM_DEFAULT_NETID, {makeUidRangeParcel(0, TEST_UID3)}, /*unused*/ 0);
    EXPECT_TRUE(mNetd->setNetworkAllowlist(
                             {nw1UserConfig, nw2UserConfig, nw3UserConfig, nwDefaultUserConfig})
                        .isOk());

    {  // Can set network for process on allowed networks.
        ScopedUidChange scopedUidChange(TEST_UID1);
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_1));
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_2));
        // Can not set network for process on not allowed networks.
        EXPECT_EQ(-EACCES, setNetworkForProcess(SYSTEM_DEFAULT_NETID));
        EXPECT_EQ(-EACCES, setNetworkForProcess(ENTERPRISE_NETID_3));
    }
    {  // Can set network for process on allowed networks.
        ScopedUidChange scopedUidChange(TEST_UID2);
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_3));
        // Can not set network for process on not allowed networks.
        EXPECT_EQ(-EACCES, setNetworkForProcess(SYSTEM_DEFAULT_NETID));
        EXPECT_EQ(-EACCES, setNetworkForProcess(ENTERPRISE_NETID_1));
        EXPECT_EQ(-EACCES, setNetworkForProcess(ENTERPRISE_NETID_2));
    }
    {  // Root can use whatever network it wants.
        ScopedUidChange scopedUidChange(AID_ROOT);
        EXPECT_EQ(0, setNetworkForProcess(SYSTEM_DEFAULT_NETID));
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_1));
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_2));
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_3));
    }

    // Update setting: remove ENTERPRISE_NETID_2 from profile#1's allowed network list and add it to
    // profile#2's allowed network list.
    // +-----------+-----------------------+----------------------------------------+
    // |    UID    | UID's default network | UID can select networks                |
    // +-----------+-----------------------+----------------------------------------+
    // | TEST_UID1 | ENTERPRISE_NETID_1    | ENTERPRISE_NETID_1                     |
    // | TEST_UID2 | ENTERPRISE_NETID_3    | ENTERPRISE_NETID_2, ENTERPRISE_NETID_3 |
    // +-----------+-----------------------+----------------------------------------+

    // all uids except for TEST_UID2
    nw1UserConfig = makeNativeUidRangeConfig(
            ENTERPRISE_NETID_1,
            {makeUidRangeParcel(0, TEST_UID3), makeUidRangeParcel(TEST_UID1, TEST_UID1)},
            /*unused*/ 0);
    // all uids except for TEST_UID1
    nw2UserConfig = makeNativeUidRangeConfig(ENTERPRISE_NETID_2, {makeUidRangeParcel(0, TEST_UID2)},
                                             /*unused*/ 0);
    nw3UserConfig = makeNativeUidRangeConfig(ENTERPRISE_NETID_3, {makeUidRangeParcel(0, TEST_UID2)},
                                             /*unused*/ 0);
    // all uids except for TEST_UID1 and TEST_UID2
    nwDefaultUserConfig = makeNativeUidRangeConfig(
            SYSTEM_DEFAULT_NETID, {makeUidRangeParcel(0, TEST_UID3)}, /*unused*/ 0);
    EXPECT_TRUE(mNetd->setNetworkAllowlist(
                             {nw1UserConfig, nw2UserConfig, nw3UserConfig, nwDefaultUserConfig})
                        .isOk());

    {
        ScopedUidChange scopedUidChange(TEST_UID1);
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_1));
        EXPECT_EQ(-EACCES, setNetworkForProcess(SYSTEM_DEFAULT_NETID));
        EXPECT_EQ(-EACCES, setNetworkForProcess(ENTERPRISE_NETID_2));
        EXPECT_EQ(-EACCES, setNetworkForProcess(ENTERPRISE_NETID_3));
    }
    {
        ScopedUidChange scopedUidChange(TEST_UID2);
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_2));
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_3));
        EXPECT_EQ(-EACCES, setNetworkForProcess(SYSTEM_DEFAULT_NETID));
        EXPECT_EQ(-EACCES, setNetworkForProcess(ENTERPRISE_NETID_1));
    }

    // UID not restricted by allowed list can select all networks.
    {
        ScopedUidChange scopedUidChange(TEST_UID3);
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_1));
        EXPECT_EQ(0, setNetworkForProcess(SYSTEM_DEFAULT_NETID));
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_2));
        EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_3));
    }

    // Update setting: remove ENTERPRISE_NETID_1 from profile#1's allowed network list
    // +-----------+-----------------------+----------------------------------------+
    // |    UID    | UID's default network | UID can select networks                |
    // +-----------+-----------------------+----------------------------------------+
    // | TEST_UID2 | ENTERPRISE_NETID_3    | ENTERPRISE_NETID_2, ENTERPRISE_NETID_3 |
    // +-----------+-----------------------+----------------------------------------+
    EXPECT_TRUE(
            mNetd->setNetworkAllowlist({nw2UserConfig, nw3UserConfig, nwDefaultUserConfig}).isOk());

    // All UIDs should be able to use ENTERPRISE_NETID_1.
    for (const int uid : {TEST_UID1, TEST_UID2, TEST_UID3}) {
        {
            ScopedUidChange scopedUidChange(uid);
            EXPECT_EQ(0, setNetworkForProcess(ENTERPRISE_NETID_1));
        }
    }
}

namespace {

class ScopedIfaceRouteOperation {
    using IfaceCmd = std::tuple<int32_t, const std::string>;
    using RouteCmd = std::tuple<int32_t, const std::string, const std::string, const std::string>;

    // Helper type for the visitor.
    template <class... Ts>
    struct overloaded : Ts... {
        using Ts::operator()...;
    };
    // Explicit deduction guide
    template <class... Ts>
    overloaded(Ts...) -> overloaded<Ts...>;

  public:
    ScopedIfaceRouteOperation(sp<INetd> netd) : mNetd(netd) {}

    binder::Status addInterface(int32_t netId, const std::string& iface) {
        const binder::Status status = mNetd->networkAddInterface(netId, iface);
        if (status.isOk()) {
            mCmds.push_back(std::make_tuple(netId, iface));
        }
        return status;
    }

    binder::Status addRoute(int32_t netId, const std::string& iface, const std::string& destination,
                            const std::string& nextHop) {
        const binder::Status status = mNetd->networkAddRoute(netId, iface, destination, nextHop);
        if (status.isOk()) {
            mCmds.push_back(std::make_tuple(netId, iface, destination, nextHop));
        }
        return status;
    }

    ~ScopedIfaceRouteOperation() {
        // Remove routes and interfaces in reverse order.
        for (std::vector<std::variant<IfaceCmd, RouteCmd>>::reverse_iterator iter = mCmds.rbegin();
             iter != mCmds.rend(); iter++) {
            // Do corresponding works according to the type of the command pointed by the iter.
            std::visit(overloaded{
                               [&](IfaceCmd& cmd) {
                                   mNetd->networkRemoveInterface(std::get<0>(cmd),
                                                                 std::get<1>(cmd));
                               },
                               [&](RouteCmd& cmd) {
                                   mNetd->networkRemoveRoute(std::get<0>(cmd), std::get<1>(cmd),
                                                             std::get<2>(cmd), std::get<3>(cmd));
                               },
                       },
                       *iter);
        }
    }

  private:
    sp<INetd> mNetd;
    std::vector<std::variant<IfaceCmd, RouteCmd>> mCmds;
};

std::optional<sockaddr_in6> getV6LinkLocalAddrFromIfIndex(const unsigned ifIndex) {
    struct ifaddrs* ifAddrList = nullptr;
    sockaddr_in6 linkLocalAddr{};

    if (getifaddrs(&ifAddrList) == -1) return std::nullopt;

    for (struct ifaddrs* ifa = ifAddrList; ifa != nullptr; ifa = ifa->ifa_next) {
        if (ifa->ifa_addr != nullptr && ifa->ifa_addr->sa_family == AF_INET6) {
            struct sockaddr_in6* addr = reinterpret_cast<struct sockaddr_in6*>(ifa->ifa_addr);
            if (addr->sin6_scope_id == ifIndex && IN6_IS_ADDR_LINKLOCAL(&(addr->sin6_addr))) {
                linkLocalAddr = *addr;
                freeifaddrs(ifAddrList);
                return linkLocalAddr;
            }
        }
    }

    freeifaddrs(ifAddrList);
    return std::nullopt;
}

int retry_bind(int sockfd, struct sockaddr* addr, socklen_t addrlen) {
    int ret = 0;

    for (int retry = 0; retry < 10; retry++) {
        ret = bind(sockfd, addr, addrlen);
        if (ret == 0 || (ret == -1 && errno != EADDRNOTAVAIL)) {
            break;
        }
        usleep(100 * 1000);
    }
    return ret;
}

}  // namespace

TEST_F(NetdBinderTest, V6LinkLocalFwmark) {
    createAndSetDefaultNetwork(TEST_NETID1, sTun.name());

    // Add an interface and route for Local network.
    ScopedIfaceRouteOperation scopedOperation(mNetd);
    EXPECT_TRUE(scopedOperation.addInterface(NetworkController::LOCAL_NET_ID, sTun2.name()).isOk());
    EXPECT_TRUE(
            scopedOperation.addRoute(NetworkController::LOCAL_NET_ID, sTun2.name(), "fe80::/64", "")
                    .isOk());

    // Bind a listening socket to the auto assigned link-local address of the Local network.
    std::optional<sockaddr_in6> v6LinkLocalAddr_1 = getV6LinkLocalAddrFromIfIndex(sTun2.ifindex());
    ASSERT_TRUE(v6LinkLocalAddr_1.has_value()) << "errno:" << errno;
    socklen_t len = sizeof(v6LinkLocalAddr_1.value());
    unique_fd s1(socket(AF_INET6, SOCK_STREAM | SOCK_NONBLOCK, 0));
    ASSERT_EQ(0, bind(s1, reinterpret_cast<sockaddr*>(&v6LinkLocalAddr_1.value()), len))
            << "errno:" << errno;
    ASSERT_EQ(0, getsockname(s1, reinterpret_cast<sockaddr*>(&v6LinkLocalAddr_1.value()), &len))
            << "errno:" << errno;
    ASSERT_EQ(0, listen(s1, 10)) << "errno:" << errno;

    // Add another v6 link-local address.
    const char* v6LinkLocalAddr_2 = "fe80::ace:d00d";
    EXPECT_TRUE(mNetd->interfaceAddAddress(sTun2.name(), v6LinkLocalAddr_2, 64).isOk());

    // Bind a client socket on the new added link-local address and connect it to the listen socket.
    // Have different src and dst addresses is needed because we want to test the behavior of fwmark
    // and destroying sockets. The same src and dst addresses are treated as loopbacks and won't be
    // destroyed in any way.
    const struct addrinfo hints = {
            .ai_flags = AI_NUMERICHOST,
            .ai_family = AF_INET6,
            .ai_socktype = SOCK_STREAM,
    };
    struct addrinfo* addrinfoList = nullptr;
    int ret = getaddrinfo(v6LinkLocalAddr_2, nullptr, &hints, &addrinfoList);
    ScopedAddrinfo addrinfoCleanup(addrinfoList);
    ASSERT_EQ(0, ret) << "errno:" << errno;

    len = addrinfoList[0].ai_addrlen;
    sockaddr_in6 sin6 = *reinterpret_cast<sockaddr_in6*>(addrinfoList[0].ai_addr);
    sin6.sin6_scope_id = sTun2.ifindex();

    unique_fd c1(socket(AF_INET6, SOCK_STREAM, 0));
    // Retry in case the newly added address is not ready yet.
    ASSERT_EQ(0, retry_bind(c1, reinterpret_cast<sockaddr*>(&sin6), len)) << "errno:" << errno;
    ASSERT_EQ(0, getsockname(c1, reinterpret_cast<sockaddr*>(&sin6), &len)) << "errno:" << errno;
    ASSERT_EQ(0, connect(c1, reinterpret_cast<sockaddr*>(&v6LinkLocalAddr_1.value()), len))
            << "errno:" << errno;

    // Verify netId in fwmark.
    Fwmark fwmark;
    socklen_t fwmarkLen = sizeof(fwmark.intValue);
    EXPECT_EQ(0, getsockopt(c1, SOL_SOCKET, SO_MARK, &fwmark.intValue, &fwmarkLen));
    EXPECT_EQ((unsigned)NetworkController::LOCAL_NET_ID, fwmark.netId);

    unique_fd a1(accept(s1, nullptr, 0));
    ASSERT_NE(-1, a1) << "errno:" << errno;
    EXPECT_EQ(0, getsockopt(a1, SOL_SOCKET, SO_MARK, &fwmark.intValue, &fwmarkLen));
    // TODO: Fix fwmark on the accept socket?
    fwmark.netId = NetworkController::LOCAL_NET_ID;
    EXPECT_EQ(0, setsockopt(a1, SOL_SOCKET, SO_MARK, &fwmark.intValue, sizeof(fwmark.intValue)));

    // Change permission on the default network. Client socket should not be destroyed.
    EXPECT_TRUE(
            mNetd->networkSetPermissionForNetwork(TEST_NETID1, INetd::PERMISSION_NETWORK).isOk());

    char buf[1024] = {};
    EXPECT_EQ(3, write(a1, "foo", 3)) << "errno:" << errno;
    EXPECT_EQ(3, read(c1, buf, sizeof(buf))) << "errno:" << errno;
}
