/*
 * Copyright (C) 2016 The Android Open Source Project
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
 *
 */

#define LOG_TAG "resolv_integration_test"

#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/result.h>
#include <android-base/unique_fd.h>
#include <android/multinetwork.h>  // ResNsendFlags
#include <arpa/inet.h>
#include <arpa/nameser.h>
#include <binder/ProcessState.h>
#include <cutils/sockets.h>
#include <gmock/gmock-matchers.h>
#include <gtest/gtest.h>
#include <netdb.h>
#include <netdutils/InternetAddresses.h>
#include <netdutils/NetNativeTestBase.h>
#include <netdutils/NetworkConstants.h>  // SHA256_SIZE
#include <netdutils/ResponseCode.h>
#include <netdutils/Slice.h>
#include <netdutils/SocketOption.h>
#include <netdutils/Stopwatch.h>
#include <netinet/in.h>
#include <poll.h> /* poll */
#include <private/android_filesystem_config.h>
#include <resolv.h>
#include <stdarg.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include <algorithm>
#include <chrono>
#include <functional>
#include <iterator>
#include <numeric>
#include <string_view>
#include <thread>
#include <unordered_set>

#include <DnsProxydProtocol.h>  // NETID_USE_LOCAL_NAMESERVERS
#include <aidl/android/net/IDnsResolver.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <bpf/BpfUtils.h>
#include <util.h>  // getApiLevel
#include "Experiments.h"
#include "NetdClient.h"
#include "ResolverStats.h"
#include "netid_client.h"  // NETID_UNSET
#include "params.h"        // MAXNS
#include "stats.h"         // RCODE_TIMEOUT
#include "tests/dns_metrics_listener/dns_metrics_listener.h"
#include "tests/dns_responder/dns_responder.h"
#include "tests/dns_responder/dns_responder_client_ndk.h"
#include "tests/dns_responder/dns_tls_certificate.h"
#include "tests/dns_responder/dns_tls_frontend.h"
#include "tests/resolv_test_utils.h"
#include "tests/tun_forwarder.h"
#include "tests/unsolicited_listener/unsolicited_event_listener.h"

// This mainline module test still needs to be able to run on pre-S devices,
// and thus may run across pre-4.9 non-eBPF capable devices like the Pixel 2.
#define SKIP_IF_BPF_NOT_SUPPORTED                           \
    do {                                                    \
        if (!android::bpf::isAtLeastKernelVersion(4, 9, 0)) \
            GTEST_SKIP() << "Skip: bpf is not supported.";  \
    } while (0)

// Valid VPN netId range is 100 ~ 65535
constexpr int TEST_VPN_NETID = 65502;
constexpr int MAXPACKET = (8 * 1024);

// Sync from packages/modules/DnsResolver/resolv_private.h
constexpr int RES_TIMEOUT = 5000; /* min. milliseconds between retries */
constexpr int RES_DFLRETRY = 2;   /* Default #/tries. */

// Semi-public Bionic hook used by the NDK (frameworks/base/native/android/net.c)
// Tested here for convenience.
extern "C" int android_getaddrinfofornet(const char* hostname, const char* servname,
                                         const addrinfo* hints, unsigned netid, unsigned mark,
                                         struct addrinfo** result);

using namespace std::chrono_literals;

using aidl::android::net::IDnsResolver;
using aidl::android::net::INetd;
using aidl::android::net::ResolverOptionsParcel;
using aidl::android::net::ResolverParamsParcel;
using aidl::android::net::UidRangeParcel;
using aidl::android::net::metrics::INetdEventListener;
using aidl::android::net::netd::aidl::NativeUidRangeConfig;
using aidl::android::net::resolv::aidl::DnsHealthEventParcel;
using aidl::android::net::resolv::aidl::IDnsResolverUnsolicitedEventListener;
using aidl::android::net::resolv::aidl::Nat64PrefixEventParcel;
using aidl::android::net::resolv::aidl::PrivateDnsValidationEventParcel;
using android::base::Error;
using android::base::GetProperty;
using android::base::ParseInt;
using android::base::Result;
using android::base::unique_fd;
using android::net::ResolverStats;
using android::net::TunForwarder;
using android::net::metrics::DnsMetricsListener;
using android::net::resolv::aidl::UnsolicitedEventListener;
using android::netdutils::enableSockopt;
using android::netdutils::makeSlice;
using android::netdutils::ResponseCode;
using android::netdutils::ScopedAddrinfo;
using android::netdutils::Stopwatch;
using android::netdutils::toHex;

namespace fs = std::filesystem;

namespace {

std::pair<ScopedAddrinfo, int> safe_getaddrinfo_time_taken(const char* node, const char* service,
                                                           const addrinfo& hints) {
    Stopwatch s;
    ScopedAddrinfo result = safe_getaddrinfo(node, service, &hints);
    return {std::move(result), s.timeTakenUs() / 1000};
}

struct NameserverStats {
    NameserverStats() = delete;
    NameserverStats(const std::string server) : server(server) {}
    NameserverStats& setSuccesses(int val) {
        successes = val;
        return *this;
    }
    NameserverStats& setErrors(int val) {
        errors = val;
        return *this;
    }
    NameserverStats& setTimeouts(int val) {
        timeouts = val;
        return *this;
    }
    NameserverStats& setInternalErrors(int val) {
        internal_errors = val;
        return *this;
    }
    NameserverStats& setRttAvg(int val) {
        rtt_avg = val;
        return *this;
    }

    const std::string server;
    int successes = 0;
    int errors = 0;
    int timeouts = 0;
    int internal_errors = 0;
    int rtt_avg = -1;
};

const bool isAtLeastR = (getApiLevel() >= 30);

#define SKIP_IF_KERNEL_VERSION_LOWER_THAN(major, minor, sub)                                  \
    do {                                                                                      \
        if (!android::bpf::isAtLeastKernelVersion(major, minor, sub))                         \
            GTEST_SKIP() << "Required kernel: " << (major) << "." << (minor) << "." << (sub); \
    } while (0)

}  // namespace

class ResolverTest : public NetNativeTestBase {
  public:
    static void SetUpTestSuite() {
        // Get binder service.
        // Note that |mDnsClient| is not used for getting binder service in this static function.
        // The reason is that wants to keep |mDnsClient| as a non-static data member. |mDnsClient|
        // which sets up device network configuration could be independent from every test.
        // TODO: Perhaps add a static function in resolv_test_binder_utils.{cpp,h} to get binder
        // service.

        AIBinder* binder = AServiceManager_getService("dnsresolver");
        sResolvBinder = ndk::SpAIBinder(binder);
        auto resolvService = aidl::android::net::IDnsResolver::fromBinder(sResolvBinder);
        ASSERT_NE(nullptr, resolvService.get());

        // Subscribe the death recipient to the service IDnsResolver for detecting Netd death.
        // GTEST assertion macros are not invoked for generating a test failure in the death
        // recipient because the macros can't indicate failed test if Netd died between tests.
        // Moreover, continuing testing may have no meaningful after Netd death. Therefore, the
        // death recipient aborts process by GTEST_LOG_(FATAL) once Netd died.
        sResolvDeathRecipient = AIBinder_DeathRecipient_new([](void*) {
            constexpr char errorMessage[] = "Netd died";
            LOG(ERROR) << errorMessage;
            GTEST_LOG_(FATAL) << errorMessage;
        });
        ASSERT_EQ(STATUS_OK, AIBinder_linkToDeath(binder, sResolvDeathRecipient, nullptr));

        // Subscribe the DNS listener for verifying DNS metrics event contents.
        sDnsMetricsListener = ndk::SharedRefBase::make<DnsMetricsListener>(
                TEST_NETID /*monitor specific network*/);
        ASSERT_TRUE(resolvService->registerEventListener(sDnsMetricsListener).isOk());

        // Subscribe the unsolicited event listener for verifying unsolicited event contents.
        sUnsolicitedEventListener = ndk::SharedRefBase::make<UnsolicitedEventListener>(
                TEST_NETID /*monitor specific network*/);
        ASSERT_TRUE(
                resolvService->registerUnsolicitedEventListener(sUnsolicitedEventListener).isOk());

        // Start the binder thread pool for listening DNS metrics events and receiving death
        // recipient.
        ABinderProcess_startThreadPool();
        AllowNetworkInBackground(TEST_UID, true);
        AllowNetworkInBackground(TEST_UID2, true);
    }
    static void TearDownTestSuite() {
        AIBinder_DeathRecipient_delete(sResolvDeathRecipient);
        AllowNetworkInBackground(TEST_UID, false);
        AllowNetworkInBackground(TEST_UID2, false);
    }

  protected:
    void SetUp() {
        mDnsClient.SetUp();
        sDnsMetricsListener->reset();
        sUnsolicitedEventListener->reset();
        SetMdnsRoute();
        mIsResolverOptionIPCSupported =
                DnsResponderClient::isRemoteVersionSupported(mDnsClient.resolvService(), 9);
    }

    void TearDown() {
        // Ensure the dump works at the end of each test.
        DumpResolverService();
        RemoveMdnsRoute();
        mDnsClient.TearDown();
    }

    void resetNetwork() {
        EXPECT_EQ(mDnsClient.TearDownOemNetwork(TEST_NETID), 0);
        EXPECT_EQ(mDnsClient.SetupOemNetwork(TEST_NETID), 0);
    }

    void StartDns(test::DNSResponder& dns, const std::vector<DnsRecord>& records) {
        for (const auto& r : records) {
            dns.addMapping(r.host_name, r.type, r.addr);
        }

        ASSERT_TRUE(dns.startServer());
        dns.clearQueries();
    }

    void DumpResolverService() {
        unique_fd fd(open("/dev/null", O_WRONLY));
        EXPECT_EQ(mDnsClient.resolvService()->dump(fd, nullptr, 0), 0);

        const char* querylogCmd[] = {"querylog"};  // Keep it sync with DnsQueryLog::DUMP_KEYWORD.
        EXPECT_EQ(mDnsClient.resolvService()->dump(fd, querylogCmd, std::size(querylogCmd)), 0);
    }

    bool WaitForNat64Prefix(ExpectNat64PrefixStatus status,
                            std::chrono::milliseconds timeout = std::chrono::milliseconds(1000)) {
        return sDnsMetricsListener->waitForNat64Prefix(status, timeout) &&
               sUnsolicitedEventListener->waitForNat64Prefix(
                       status == EXPECT_FOUND
                               ? IDnsResolverUnsolicitedEventListener::PREFIX_OPERATION_ADDED
                               : IDnsResolverUnsolicitedEventListener::PREFIX_OPERATION_REMOVED,
                       timeout);
    }

    bool WaitForPrivateDnsValidation(std::string serverAddr, bool validated) {
        return sDnsMetricsListener->waitForPrivateDnsValidation(serverAddr, validated) &&
               sUnsolicitedEventListener->waitForPrivateDnsValidation(
                       serverAddr,
                       validated ? IDnsResolverUnsolicitedEventListener::VALIDATION_RESULT_SUCCESS
                                 : IDnsResolverUnsolicitedEventListener::VALIDATION_RESULT_FAILURE,
                       IDnsResolverUnsolicitedEventListener::PROTOCOL_DOT);
    }

    bool hasUncaughtPrivateDnsValidation(const std::string& serverAddr) {
        return sDnsMetricsListener->findValidationRecord(serverAddr) &&
               sUnsolicitedEventListener->findValidationRecord(
                       serverAddr, IDnsResolverUnsolicitedEventListener::PROTOCOL_DOT);
    }

    void ExpectDnsEvent(int32_t eventType, int32_t returnCode, const std::string& hostname,
                        const std::vector<std::string>& ipAddresses) {
        const DnsMetricsListener::DnsEvent expect = {
                TEST_NETID, eventType,   returnCode,
                hostname,   ipAddresses, static_cast<int32_t>(ipAddresses.size())};
        do {
            // Blocking call until timeout.
            const auto dnsEvent = sDnsMetricsListener->popDnsEvent();
            ASSERT_TRUE(dnsEvent.has_value()) << "Expected DnsEvent " << expect;
            if (dnsEvent.value() == expect) break;
            LOG(INFO) << "Skip unexpected DnsEvent: " << dnsEvent.value();
        } while (true);

        while (returnCode == 0 || returnCode == RCODE_TIMEOUT) {
            // Blocking call until timeout.
            Result<int> result = sUnsolicitedEventListener->popDnsHealthResult();
            ASSERT_TRUE(result.ok()) << "Expected dns health result is " << returnCode;
            if ((returnCode == 0 &&
                 result.value() == IDnsResolverUnsolicitedEventListener::DNS_HEALTH_RESULT_OK) ||
                (returnCode == RCODE_TIMEOUT &&
                 result.value() ==
                         IDnsResolverUnsolicitedEventListener::DNS_HEALTH_RESULT_TIMEOUT)) {
                break;
            }
            LOG(INFO) << "Skip unexpected dns health result:" << result.value();
        }
    }

    enum class StatsCmp { LE, EQ };

    bool expectStatsNotGreaterThan(const std::vector<NameserverStats>& nameserversStats) {
        return expectStatsFromGetResolverInfo(nameserversStats, StatsCmp::LE);
    }

    bool expectStatsEqualTo(const std::vector<NameserverStats>& nameserversStats) {
        return expectStatsFromGetResolverInfo(nameserversStats, StatsCmp::EQ);
    }

    bool expectStatsFromGetResolverInfo(const std::vector<NameserverStats>& nameserversStats,
                                        const StatsCmp cmp) {
        constexpr int RTT_TOLERANCE_MS = 200;
        const auto resolvInfo = mDnsClient.getResolverInfo();
        if (!resolvInfo.ok()) {
            ADD_FAILURE() << resolvInfo.error().message();
            return false;
        }
        const std::vector<std::string>& res_servers = resolvInfo.value().dnsServers;
        const std::vector<ResolverStats>& res_stats = resolvInfo.value().stats;

        if (res_servers.size() != res_stats.size()) {
            ADD_FAILURE() << fmt::format("res_servers.size() != res_stats.size(): {} != {}",
                                         res_servers.size(), res_stats.size());
            return false;
        }
        if (res_servers.size() != nameserversStats.size()) {
            ADD_FAILURE() << fmt::format("res_servers.size() != nameserversStats.size(): {} != {}",
                                         res_servers.size(), nameserversStats.size());
            return false;
        }

        for (const auto& stats : nameserversStats) {
            SCOPED_TRACE(stats.server);
            const auto it = std::find(res_servers.begin(), res_servers.end(), stats.server);
            if (it == res_servers.end()) {
                ADD_FAILURE() << fmt::format("nameserver {} not found in the list {{{}}}",
                                             stats.server, fmt::join(res_servers, ", "));
                return false;
            }
            const int index = std::distance(res_servers.begin(), it);

            // The check excludes last_sample_time and usable since they will be obsolete
            // after |res_stats| is retrieved from NetConfig.dnsStats rather than NetConfig.nsstats.
            switch (cmp) {
                case StatsCmp::EQ:
                    EXPECT_EQ(res_stats[index].successes, stats.successes);
                    EXPECT_EQ(res_stats[index].errors, stats.errors);
                    EXPECT_EQ(res_stats[index].timeouts, stats.timeouts);
                    EXPECT_EQ(res_stats[index].internal_errors, stats.internal_errors);
                    // A negative rtt_avg means that there is no effective rtt in the
                    // stats. The value should be deterministic.
                    // See android_net_res_stats_aggregate() for mor details.
                    if (res_stats[index].rtt_avg < 0 || stats.rtt_avg < 0) {
                        EXPECT_EQ(res_stats[index].rtt_avg, stats.rtt_avg);
                    } else {
                        EXPECT_NEAR(res_stats[index].rtt_avg, stats.rtt_avg, RTT_TOLERANCE_MS);
                    }
                    break;
                case StatsCmp::LE:
                    EXPECT_LE(res_stats[index].successes, stats.successes);
                    EXPECT_LE(res_stats[index].errors, stats.errors);
                    EXPECT_LE(res_stats[index].timeouts, stats.timeouts);
                    EXPECT_LE(res_stats[index].internal_errors, stats.internal_errors);
                    EXPECT_LE(res_stats[index].rtt_avg, stats.rtt_avg + RTT_TOLERANCE_MS);
                    break;
                default:
                    ADD_FAILURE() << "Unknown comparator " << static_cast<int>(cmp);
                    return false;
            }
        }

        return true;
    }

    // Since there's no way to terminate private DNS validation threads at any time. Tests that
    // focus on the results of private DNS validation can interfere with each other if they use the
    // same IP address for test servers. getUniqueIPv4Address() is a workaround to reduce the
    // possibility of tests being flaky. A feasible solution is to forbid the validation threads,
    // which are considered as outdated (e.g. switch the resolver to private DNS OFF mode), updating
    // the result to the PrivateDnsConfiguration instance.
    static std::string getUniqueIPv4Address() {
        static int counter = 0;
        return fmt::format("127.0.100.{}", (++counter & 0xff));
    }

    DnsResponderClient mDnsClient;

    bool mIsResolverOptionIPCSupported = false;

    // Use a shared static DNS listener for all tests to avoid registering lots of listeners
    // which may be released late until process terminated. Currently, registered DNS listener
    // is removed by binder death notification which is fired when the process hosting an
    // IBinder has gone away. If every test in ResolverTest registers its DNS listener, Netd
    // may temporarily hold lots of dead listeners until the unit test process terminates.
    // TODO: Perhaps add an unregistering listener binder call or fork a listener process which
    // could be terminated earlier.
    static std::shared_ptr<DnsMetricsListener>
            sDnsMetricsListener;  // Initialized in SetUpTestSuite.

    inline static std::shared_ptr<UnsolicitedEventListener>
            sUnsolicitedEventListener;  // Initialized in SetUpTestSuite.

    // Use a shared static death recipient to monitor the service death. The static death
    // recipient could monitor the death not only during the test but also between tests.
    static AIBinder_DeathRecipient* sResolvDeathRecipient;  // Initialized in SetUpTestSuite.

    // The linked AIBinder_DeathRecipient will be automatically unlinked if the binder is deleted.
    // The binder needs to be retained throughout tests.
    static ndk::SpAIBinder sResolvBinder;
};

// Initialize static member of class.
std::shared_ptr<DnsMetricsListener> ResolverTest::sDnsMetricsListener;
AIBinder_DeathRecipient* ResolverTest::sResolvDeathRecipient;
ndk::SpAIBinder ResolverTest::sResolvBinder;

TEST_F(ResolverTest, GetHostByName) {
    constexpr char nonexistent_host_name[] = "nonexistent.example.com.";

    test::DNSResponder dns;
    StartDns(dns, {{kHelloExampleCom, ns_type::ns_t_a, "1.2.3.3"}});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    const hostent* result;
    result = gethostbyname("nonexistent");
    EXPECT_EQ(1U, GetNumQueriesForType(dns, ns_type::ns_t_a, nonexistent_host_name));
    ASSERT_TRUE(result == nullptr);
    EXPECT_EQ(HOST_NOT_FOUND, h_errno);

    dns.clearQueries();
    result = gethostbyname("hello");
    EXPECT_EQ(1U, GetNumQueriesForType(dns, ns_type::ns_t_a, kHelloExampleCom));
    ASSERT_FALSE(result == nullptr);
    ASSERT_EQ(4, result->h_length);
    ASSERT_FALSE(result->h_addr_list[0] == nullptr);
    EXPECT_EQ("1.2.3.3", ToString(result));
    EXPECT_TRUE(result->h_addr_list[1] == nullptr);
}

TEST_F(ResolverTest, GetHostByName_NULL) {
    // Most libc implementations would just crash on gethostbyname(NULL). Instead, Bionic
    // serializes the null argument over dnsproxyd, causing the server-side to crash!
    // This is a regression test.
    const char* const testcases[] = {nullptr, "", "^"};
    for (const char* name : testcases) {
        SCOPED_TRACE(fmt::format("gethostbyname({})", name ? name : "NULL"));
        const hostent* result = gethostbyname(name);
        EXPECT_TRUE(result == nullptr);
        EXPECT_EQ(HOST_NOT_FOUND, h_errno);
    }
}

TEST_F(ResolverTest, GetHostByName_cnames) {
    constexpr char host_name[] = "host.example.com.";
    size_t cnamecount = 0;
    test::DNSResponder dns;

    const std::vector<DnsRecord> records = {
            {kHelloExampleCom, ns_type::ns_t_cname, "a.example.com."},
            {"a.example.com.", ns_type::ns_t_cname, "b.example.com."},
            {"b.example.com.", ns_type::ns_t_cname, "c.example.com."},
            {"c.example.com.", ns_type::ns_t_cname, "d.example.com."},
            {"d.example.com.", ns_type::ns_t_cname, "e.example.com."},
            {"e.example.com.", ns_type::ns_t_cname, host_name},
            {host_name, ns_type::ns_t_a, "1.2.3.3"},
            {host_name, ns_type::ns_t_aaaa, "2001:db8::42"},
    };
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    // using gethostbyname2() to resolve ipv4 hello.example.com. to 1.2.3.3
    // Ensure the v4 address and cnames are correct
    const hostent* result;
    result = gethostbyname2("hello", AF_INET);
    ASSERT_FALSE(result == nullptr);

    for (int i = 0; result != nullptr && result->h_aliases[i] != nullptr; i++) {
        std::string domain_name = records[i].host_name.substr(0, records[i].host_name.size() - 1);
        EXPECT_EQ(result->h_aliases[i], domain_name);
        cnamecount++;
    }
    // The size of "Non-cname type" record in DNS records is 2
    ASSERT_EQ(cnamecount, records.size() - 2);
    ASSERT_EQ(4, result->h_length);
    ASSERT_FALSE(result->h_addr_list[0] == nullptr);
    EXPECT_EQ("1.2.3.3", ToString(result));
    EXPECT_TRUE(result->h_addr_list[1] == nullptr);
    EXPECT_EQ(1U, dns.queries().size()) << dns.dumpQueries();

    // using gethostbyname2() to resolve ipv6 hello.example.com. to 2001:db8::42
    // Ensure the v6 address and cnames are correct
    cnamecount = 0;
    dns.clearQueries();
    result = gethostbyname2("hello", AF_INET6);
    for (unsigned i = 0; result != nullptr && result->h_aliases[i] != nullptr; i++) {
        std::string domain_name = records[i].host_name.substr(0, records[i].host_name.size() - 1);
        EXPECT_EQ(result->h_aliases[i], domain_name);
        cnamecount++;
    }
    // The size of "Non-cname type" DNS record in records is 2
    ASSERT_EQ(cnamecount, records.size() - 2);
    ASSERT_FALSE(result == nullptr);
    ASSERT_EQ(16, result->h_length);
    ASSERT_FALSE(result->h_addr_list[0] == nullptr);
    EXPECT_EQ("2001:db8::42", ToString(result));
    EXPECT_TRUE(result->h_addr_list[1] == nullptr);
}

TEST_F(ResolverTest, GetHostByName_cnamesInfiniteLoop) {
    test::DNSResponder dns;
    const std::vector<DnsRecord> records = {
            {kHelloExampleCom, ns_type::ns_t_cname, "a.example.com."},
            {"a.example.com.", ns_type::ns_t_cname, kHelloExampleCom},
    };
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    const hostent* result;
    result = gethostbyname2("hello", AF_INET);
    ASSERT_TRUE(result == nullptr);

    dns.clearQueries();
    result = gethostbyname2("hello", AF_INET6);
    ASSERT_TRUE(result == nullptr);
}

TEST_F(ResolverTest, GetHostByName_localhost) {
    constexpr char name_camelcase[] = "LocalHost";
    constexpr char name_ip6_dot[] = "ip6-localhost.";
    constexpr char name_ip6_fqdn[] = "ip6-localhost.example.com.";

    // Add a no-op nameserver which shouldn't receive any queries
    test::DNSResponder dns;
    StartDns(dns, {});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    // Expect no DNS queries; localhost is resolved via /etc/hosts
    const hostent* result = gethostbyname(kLocalHost);
    EXPECT_TRUE(dns.queries().empty()) << dns.dumpQueries();
    ASSERT_FALSE(result == nullptr);
    ASSERT_EQ(4, result->h_length);
    ASSERT_FALSE(result->h_addr_list[0] == nullptr);
    EXPECT_EQ(kLocalHostAddr, ToString(result));
    EXPECT_TRUE(result->h_addr_list[1] == nullptr);

    // Ensure the hosts file resolver ignores case of hostnames
    result = gethostbyname(name_camelcase);
    EXPECT_TRUE(dns.queries().empty()) << dns.dumpQueries();
    ASSERT_FALSE(result == nullptr);
    ASSERT_EQ(4, result->h_length);
    ASSERT_FALSE(result->h_addr_list[0] == nullptr);
    EXPECT_EQ(kLocalHostAddr, ToString(result));
    EXPECT_TRUE(result->h_addr_list[1] == nullptr);

    // The hosts file also contains ip6-localhost, but gethostbyname() won't
    // return it. This would be easy to
    // change, but there's no point in changing the legacy behavior; new code
    // should be calling getaddrinfo() anyway.
    // So we check the legacy behavior, which results in amusing A-record
    // lookups for ip6-localhost, with and without search domains appended.
    dns.clearQueries();
    result = gethostbyname(kIp6LocalHost);
    EXPECT_EQ(2U, dns.queries().size()) << dns.dumpQueries();
    EXPECT_EQ(1U, GetNumQueriesForType(dns, ns_type::ns_t_a, name_ip6_dot)) << dns.dumpQueries();
    EXPECT_EQ(1U, GetNumQueriesForType(dns, ns_type::ns_t_a, name_ip6_fqdn)) << dns.dumpQueries();
    ASSERT_TRUE(result == nullptr);

    // Finally, use gethostbyname2() to resolve ip6-localhost to ::1 from
    // the hosts file.
    dns.clearQueries();
    result = gethostbyname2(kIp6LocalHost, AF_INET6);
    EXPECT_TRUE(dns.queries().empty()) << dns.dumpQueries();
    ASSERT_FALSE(result == nullptr);
    ASSERT_EQ(16, result->h_length);
    ASSERT_FALSE(result->h_addr_list[0] == nullptr);
    EXPECT_EQ(kIp6LocalHostAddr, ToString(result));
    EXPECT_TRUE(result->h_addr_list[1] == nullptr);
}

TEST_F(ResolverTest, GetHostByName_numeric) {
    // Add a no-op nameserver which shouldn't receive any queries
    test::DNSResponder dns;
    StartDns(dns, {});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    // Numeric v4 address: expect no DNS queries
    constexpr char numeric_v4[] = "192.168.0.1";
    const hostent* result = gethostbyname(numeric_v4);
    EXPECT_EQ(0U, dns.queries().size());
    ASSERT_FALSE(result == nullptr);
    ASSERT_EQ(4, result->h_length);  // v4
    ASSERT_FALSE(result->h_addr_list[0] == nullptr);
    EXPECT_EQ(numeric_v4, ToString(result));
    EXPECT_TRUE(result->h_addr_list[1] == nullptr);

    // gethostbyname() recognizes a v6 address, and fails with no DNS queries
    constexpr char numeric_v6[] = "2001:db8::42";
    dns.clearQueries();
    result = gethostbyname(numeric_v6);
    EXPECT_EQ(0U, dns.queries().size());
    EXPECT_TRUE(result == nullptr);

    // Numeric v6 address with gethostbyname2(): succeeds with no DNS queries
    dns.clearQueries();
    result = gethostbyname2(numeric_v6, AF_INET6);
    EXPECT_EQ(0U, dns.queries().size());
    ASSERT_FALSE(result == nullptr);
    ASSERT_EQ(16, result->h_length);  // v6
    ASSERT_FALSE(result->h_addr_list[0] == nullptr);
    EXPECT_EQ(numeric_v6, ToString(result));
    EXPECT_TRUE(result->h_addr_list[1] == nullptr);

    // Numeric v6 address with scope work with getaddrinfo(),
    // but gethostbyname2() does not understand them; it issues two dns
    // queries, then fails. This hardly ever happens, there's no point
    // in fixing this. This test simply verifies the current (bogus)
    // behavior to avoid further regressions (like crashes, or leaks).
    constexpr char numeric_v6_scope[] = "fe80::1%lo";
    dns.clearQueries();
    result = gethostbyname2(numeric_v6_scope, AF_INET6);
    EXPECT_EQ(2U, dns.queries().size());  // OUCH!
    ASSERT_TRUE(result == nullptr);
}

TEST_F(ResolverTest, BinderSerialization) {
    std::array<int, IDnsResolver::RESOLVER_PARAMS_COUNT> params_offsets = {
            IDnsResolver::RESOLVER_PARAMS_SAMPLE_VALIDITY,
            IDnsResolver::RESOLVER_PARAMS_SUCCESS_THRESHOLD,
            IDnsResolver::RESOLVER_PARAMS_MIN_SAMPLES,
            IDnsResolver::RESOLVER_PARAMS_MAX_SAMPLES,
            IDnsResolver::RESOLVER_PARAMS_BASE_TIMEOUT_MSEC,
            IDnsResolver::RESOLVER_PARAMS_RETRY_COUNT,
    };
    const int size = static_cast<int>(params_offsets.size());
    EXPECT_EQ(size, IDnsResolver::RESOLVER_PARAMS_COUNT);
    std::sort(params_offsets.begin(), params_offsets.end());
    for (int i = 0; i < size; ++i) {
        EXPECT_EQ(params_offsets[i], i);
    }
}

TEST_F(ResolverTest, GetHostByName_Binder) {
    std::vector<std::string> domains = {"example.com"};
    std::vector<std::unique_ptr<test::DNSResponder>> dns;
    std::vector<std::string> servers;
    std::vector<DnsResponderClient::Mapping> mappings;
    ASSERT_NO_FATAL_FAILURE(mDnsClient.SetupMappings(1, domains, &mappings));
    ASSERT_NO_FATAL_FAILURE(mDnsClient.SetupDNSServers(4, mappings, &dns, &servers));
    ASSERT_EQ(1U, mappings.size());
    const DnsResponderClient::Mapping& mapping = mappings[0];

    const auto resolverParams = ResolverParams::Builder()
                                        .setDomains(domains)
                                        .setDnsServers(servers)
                                        .setDotServers({})
                                        .build();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(resolverParams));

    const hostent* result = gethostbyname(mapping.host.c_str());
    const size_t total_queries =
            std::accumulate(dns.begin(), dns.end(), 0, [&mapping](size_t total, auto& d) {
                return total + GetNumQueriesForType(*d, ns_type::ns_t_a, mapping.entry.c_str());
            });

    EXPECT_LE(1U, total_queries);
    ASSERT_FALSE(result == nullptr);
    ASSERT_EQ(4, result->h_length);
    ASSERT_FALSE(result->h_addr_list[0] == nullptr);
    EXPECT_EQ(mapping.ip4, ToString(result));
    EXPECT_TRUE(result->h_addr_list[1] == nullptr);

    const auto resolvInfo = mDnsClient.getResolverInfo();
    ASSERT_RESULT_OK(resolvInfo);
    EXPECT_EQ(servers.size(), resolvInfo.value().dnsServers.size());
    EXPECT_EQ(domains.size(), resolvInfo.value().domains.size());
    EXPECT_TRUE(resolvInfo.value().dotServers.empty());
    EXPECT_EQ(resolverParams.sampleValiditySeconds, resolvInfo.value().params.sample_validity);
    EXPECT_EQ(resolverParams.successThreshold, resolvInfo.value().params.success_threshold);
    EXPECT_EQ(resolverParams.minSamples, resolvInfo.value().params.min_samples);
    EXPECT_EQ(resolverParams.maxSamples, resolvInfo.value().params.max_samples);
    EXPECT_EQ(resolverParams.baseTimeoutMsec, resolvInfo.value().params.base_timeout_msec);
    EXPECT_EQ(resolverParams.retryCount, resolvInfo.value().params.retry_count);
    EXPECT_EQ(servers.size(), resolvInfo.value().stats.size());

    EXPECT_THAT(resolvInfo.value().dnsServers, testing::UnorderedElementsAreArray(servers));
    EXPECT_THAT(resolvInfo.value().domains, testing::UnorderedElementsAreArray(domains));
}

TEST_F(ResolverTest, GetAddrInfo) {
    constexpr char listen_addr[] = "127.0.0.4";
    constexpr char listen_addr2[] = "127.0.0.5";
    constexpr char host_name[] = "howdy.example.com.";

    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };
    test::DNSResponder dns(listen_addr);
    test::DNSResponder dns2(listen_addr2);
    StartDns(dns, records);
    StartDns(dns2, records);

    ASSERT_TRUE(mDnsClient.SetResolversForNetwork({listen_addr}));
    dns.clearQueries();
    dns2.clearQueries();

    ScopedAddrinfo result = safe_getaddrinfo("howdy", nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    size_t found = GetNumQueries(dns, host_name);
    EXPECT_LE(1U, found);
    // Could be A or AAAA
    std::string result_str = ToString(result);
    EXPECT_TRUE(result_str == "1.2.3.4" || result_str == "::1.2.3.4")
            << ", result_str='" << result_str << "'";

    // Verify that the name is cached.
    size_t old_found = found;
    result = safe_getaddrinfo("howdy", nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    found = GetNumQueries(dns, host_name);
    EXPECT_LE(1U, found);
    EXPECT_EQ(old_found, found);
    result_str = ToString(result);
    EXPECT_TRUE(result_str == "1.2.3.4" || result_str == "::1.2.3.4") << result_str;

    // Change the DNS resolver, ensure that queries are still cached.
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork({listen_addr2}));
    dns.clearQueries();
    dns2.clearQueries();

    result = safe_getaddrinfo("howdy", nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    found = GetNumQueries(dns, host_name);
    size_t found2 = GetNumQueries(dns2, host_name);
    EXPECT_EQ(0U, found);
    EXPECT_LE(0U, found2);

    // Could be A or AAAA
    result_str = ToString(result);
    EXPECT_TRUE(result_str == "1.2.3.4" || result_str == "::1.2.3.4")
            << ", result_str='" << result_str << "'";
}

TEST_F(ResolverTest, GetAddrInfoV4) {
    test::DNSResponder dns;
    StartDns(dns, {{kHelloExampleCom, ns_type::ns_t_a, "1.2.3.5"}});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    const addrinfo hints = {.ai_family = AF_INET};
    ScopedAddrinfo result = safe_getaddrinfo("hello", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ(1U, GetNumQueries(dns, kHelloExampleCom));
    EXPECT_EQ("1.2.3.5", ToString(result));
}

TEST_F(ResolverTest, GetAddrInfo_localhost) {
    // Add a no-op nameserver which shouldn't receive any queries
    test::DNSResponder dns;
    StartDns(dns, {});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    ScopedAddrinfo result = safe_getaddrinfo(kLocalHost, nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    // Expect no DNS queries; localhost is resolved via /etc/hosts
    EXPECT_TRUE(dns.queries().empty()) << dns.dumpQueries();
    EXPECT_EQ(kLocalHostAddr, ToString(result));

    result = safe_getaddrinfo(kIp6LocalHost, nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    // Expect no DNS queries; ip6-localhost is resolved via /etc/hosts
    EXPECT_TRUE(dns.queries().empty()) << dns.dumpQueries();
    EXPECT_EQ(kIp6LocalHostAddr, ToString(result));
}

TEST_F(ResolverTest, GetAddrInfo_NumericHostname) {
    // Add a no-op nameserver which shouldn't receive any queries
    test::DNSResponder dns;
    StartDns(dns, {});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    ScopedAddrinfo result = safe_getaddrinfo("1.2.3.4", nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    // Expect no DNS queries. Numeric hostname doesn't need to resolve.
    EXPECT_TRUE(dns.queries().empty()) << dns.dumpQueries();
    EXPECT_EQ("1.2.3.4", ToString(result));

    result = safe_getaddrinfo("2001:db8::1", nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    // Expect no DNS queries. Numeric hostname doesn't need to resolve.
    EXPECT_TRUE(dns.queries().empty()) << dns.dumpQueries();
    EXPECT_EQ("2001:db8::1", ToString(result));
}

TEST_F(ResolverTest, GetAddrInfo_InvalidSocketType) {
    test::DNSResponder dns;
    StartDns(dns, {{kHelloExampleCom, ns_type::ns_t_a, "1.2.3.5"}});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    // TODO: Test other invalid socket types.
    const addrinfo hints = {
            .ai_family = AF_UNSPEC,
            .ai_socktype = SOCK_PACKET,
    };
    addrinfo* result = nullptr;
    // This is a valid hint, but the query won't be sent because the socket type is
    // not supported.
    EXPECT_EQ(EAI_NODATA, getaddrinfo("hello", nullptr, &hints, &result));
    ScopedAddrinfo result_cleanup(result);
    EXPECT_EQ(nullptr, result);
}

// Verify if the resolver correctly handle multiple queries simultaneously
// step 1: set dns server#1 into deferred responding mode.
// step 2: thread#1 query "hello.example.com." --> resolver send query to server#1.
// step 3: thread#2 query "hello.example.com." --> resolver hold the request and wait for
//           response of previous pending query sent by thread#1.
// step 4: thread#3 query "konbanha.example.com." --> resolver send query to server#3. Server
//           respond to resolver immediately.
// step 5: check if server#1 get 1 query by thread#1, server#2 get 0 query, server#3 get 1 query.
// step 6: resume dns server#1 to respond dns query in step#2.
// step 7: thread#1 and #2 should get returned from DNS query after step#6. Also, check the
//           number of queries in server#2 is 0 to ensure thread#2 does not wake up unexpectedly
//           before signaled by thread#1.
TEST_F(ResolverTest, GetAddrInfoV4_deferred_resp) {
    const char* listen_addr1 = "127.0.0.9";
    const char* listen_addr2 = "127.0.0.10";
    const char* listen_addr3 = "127.0.0.11";
    const char* listen_srv = "53";
    const char* host_name_deferred = "hello.example.com.";
    const char* host_name_normal = "konbanha.example.com.";
    test::DNSResponder dns1(listen_addr1, listen_srv, ns_rcode::ns_r_servfail);
    test::DNSResponder dns2(listen_addr2, listen_srv, ns_rcode::ns_r_servfail);
    test::DNSResponder dns3(listen_addr3, listen_srv, ns_rcode::ns_r_servfail);
    dns1.addMapping(host_name_deferred, ns_type::ns_t_a, "1.2.3.4");
    dns2.addMapping(host_name_deferred, ns_type::ns_t_a, "1.2.3.4");
    dns3.addMapping(host_name_normal, ns_type::ns_t_a, "1.2.3.5");
    ASSERT_TRUE(dns1.startServer());
    ASSERT_TRUE(dns2.startServer());
    ASSERT_TRUE(dns3.startServer());
    const std::vector<std::string> servers_for_t1 = {listen_addr1};
    const std::vector<std::string> servers_for_t2 = {listen_addr2};
    const std::vector<std::string> servers_for_t3 = {listen_addr3};
    addrinfo hints = {.ai_family = AF_INET};
    const std::array<int, IDnsResolver::RESOLVER_PARAMS_COUNT> params = {300, 25, 8, 8, 5000, 0};
    bool t3_task_done = false;
    bool t2_sv_setup_done = false;

    dns1.setDeferredResp(true);
    std::thread t1([&, this]() {
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParams::Builder()
                                                              .setDnsServers(servers_for_t1)
                                                              .setDotServers({})
                                                              .setParams(params)
                                                              .build()));
        ScopedAddrinfo result = safe_getaddrinfo(host_name_deferred, nullptr, &hints);
        // t3's dns query should got returned first
        EXPECT_TRUE(t3_task_done);
        EXPECT_EQ(1U, GetNumQueries(dns1, host_name_deferred));
        EXPECT_TRUE(result != nullptr);
        EXPECT_EQ("1.2.3.4", ToString(result));
    });

    // ensuring t1 and t2 handler functions are processed in order
    EXPECT_TRUE(PollForCondition([&]() { return GetNumQueries(dns1, host_name_deferred); }));
    std::thread t2([&, this]() {
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParams::Builder()
                                                              .setDnsServers(servers_for_t2)
                                                              .setDotServers({})
                                                              .setParams(params)
                                                              .build()));
        t2_sv_setup_done = true;
        ScopedAddrinfo result = safe_getaddrinfo(host_name_deferred, nullptr, &hints);
        EXPECT_TRUE(t3_task_done);
        EXPECT_EQ(0U, GetNumQueries(dns2, host_name_deferred));
        EXPECT_TRUE(result != nullptr);
        EXPECT_EQ("1.2.3.4", ToString(result));
        const auto resolvInfo = mDnsClient.getResolverInfo();
        ASSERT_RESULT_OK(resolvInfo);
        EXPECT_EQ(0, resolvInfo.value().waitForPendingReqTimeoutCount);
    });

    // ensuring t2 and t3 handler functions are processed in order
    EXPECT_TRUE(PollForCondition([&]() { return t2_sv_setup_done; }));
    std::thread t3([&, this]() {
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParams::Builder()
                                                              .setDnsServers(servers_for_t3)
                                                              .setDotServers({})
                                                              .setParams(params)
                                                              .build()));
        ScopedAddrinfo result = safe_getaddrinfo(host_name_normal, nullptr, &hints);
        EXPECT_EQ(1U, GetNumQueries(dns1, host_name_deferred));
        EXPECT_EQ(0U, GetNumQueries(dns2, host_name_deferred));
        EXPECT_EQ(1U, GetNumQueries(dns3, host_name_normal));
        EXPECT_TRUE(result != nullptr);
        EXPECT_EQ("1.2.3.5", ToString(result));

        t3_task_done = true;
        dns1.setDeferredResp(false);
    });
    t3.join();
    t1.join();
    t2.join();
}

TEST_F(ResolverTest, GetAddrInfoV4_MultiAnswers) {
    test::DNSResponder dns(test::DNSResponder::MappingType::BINARY_PACKET);
    dns.addMappingBinaryPacket(kHelloExampleComQueryV4, kHelloExampleComResponsesV4);
    StartDns(dns, {});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_DGRAM};
    ScopedAddrinfo result = safe_getaddrinfo(kHelloExampleCom, nullptr, &hints);
    ASSERT_FALSE(result == nullptr);

    // Expect the DNS result order is the same as the RR order in the DNS response
    // |kHelloExampleComResponsesV4| because none of rule in the native sorting
    // function _rfc6724_compare() is matched.
    //
    // The reason is here for the sorting result from _rfc6724_compare.
    // For rule 1: avoid unusable destinations, all addresses are unusable on a fake test network.
    // For rule 2: prefer matching scope, all addresses don't match because of no source address.
    //             See rule#1 as well.
    // (rule 3 is not implemented)
    // (rule 4 is not implemented)
    // For rule 5: prefer matching label, all addresses get the same label 4 for AF_INET.
    // For rule 6: prefer higher precedence, all addresses get the same precedence 35 for AF_INET.
    // (rule 7 is not implemented)
    // For rule 8: prefer smaller scope, all destination addresses has the same scope.
    // For rule 9: use longest matching prefix, IPv6 only.
    // For rule 10: leave the order unchanged, these IPv4 DNS addresses meet this rule.
    //
    // See packages/modules/DnsResolver/getaddrinfo.cpp
    EXPECT_THAT(ToStrings(result),
                testing::ElementsAre(kHelloExampleComAddrV4, kHelloExampleComAddrV4_2,
                                     kHelloExampleComAddrV4_3));

    // .ai_socktype will be 0.
    hints = {.ai_family = AF_UNSPEC};
    result = safe_getaddrinfo(kHelloExampleCom, nullptr, &hints);
    ASSERT_FALSE(result == nullptr);

    // The results are sorted in every querying by explore_options and then concatenates all sorted
    // results. resolv_getaddrinfo() calls explore_fqdn() many times by the different
    // explore_options. It means that resolv_rfc6724_sort() only sorts the ordering in the results
    // of each explore_options and concatenates all sorted results into one link list. The address
    // order of the output addrinfo is:
    //   1.2.3.4 (socktype=2, protocol=17) ->
    //   8.8.8.8 (socktype=2, protocol=17) ->
    //   81.117.21.202 (socktype=2, protocol=17) ->
    //   1.2.3.4 (socktype=1, protocol=6) ->
    //   8.8.8.8 (socktype=1, protocol=6) ->
    //   81.117.21.202 (socktype=1, protocol=6)
    //
    // See resolv_getaddrinfo, explore_fqdn and dns_getaddrinfo.
    EXPECT_THAT(ToStrings(result),
                testing::ElementsAre(kHelloExampleComAddrV4, kHelloExampleComAddrV4_2,
                                     kHelloExampleComAddrV4_3, kHelloExampleComAddrV4,
                                     kHelloExampleComAddrV4_2, kHelloExampleComAddrV4_3));
}

TEST_F(ResolverTest, GetAddrInfoV6_MultiAnswers) {
    test::DNSResponder dns(test::DNSResponder::MappingType::BINARY_PACKET);
    dns.addMappingBinaryPacket(kHelloExampleComQueryV6, kHelloExampleComResponsesV6);
    StartDns(dns, {});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_DGRAM};
    ScopedAddrinfo result = safe_getaddrinfo(kHelloExampleCom, nullptr, &hints);
    ASSERT_FALSE(result == nullptr);

    // Expect the DNS result order is GUA, teredo tunneling address and IPv4-compatible address
    // because of the precedence comparison of RFC 6724.
    //
    // The reason is here for the sorting result from _rfc6724_compare.
    // For rule 1: avoid unusable destinations, all addresses are unusable on a fake test network.
    // For rule 2: prefer matching scope, all addresses don't match because of no source address.
    //             See rule#1 as well.
    // (rule 3 is not implemented)
    // (rule 4 is not implemented)
    // For rule 5: prefer matching label, the source address is not valid and can't match the dns
    //             reply addresses. See rule#1 as well.
    // For rule 6: prefer higher precedence, sorted by the order: gua(40), teredo(5) and
    //             ipv4-compatible(1).
    // Ignore from rule 7 to rule 10 because the results has been sorted by rule 6.
    //
    // See _get_precedence, _rfc6724_compare in packages/modules/DnsResolver/getaddrinfo.cpp
    EXPECT_THAT(ToStrings(result),
                testing::ElementsAre(kHelloExampleComAddrV6_GUA, kHelloExampleComAddrV6_TEREDO,
                                     kHelloExampleComAddrV6_IPV4COMPAT));

    hints = {.ai_family = AF_UNSPEC};
    result = safe_getaddrinfo(kHelloExampleCom, nullptr, &hints);
    ASSERT_FALSE(result == nullptr);

    // The results are sorted in every querying by explore_options and then concatenates all sorted
    // results. resolv_getaddrinfo() calls explore_fqdn() many times by the different
    // explore_options. It means that resolv_rfc6724_sort() only sorts the ordering in the results
    // of each explore_options and concatenates all sorted results into one link list. The address
    // order of the output addrinfo is:
    //   2404:6800::5175:15ca (socktype=2, protocol=17) ->
    //   2001::47c1 (socktype=2, protocol=17) ->
    //   ::1.2.3.4 (socktype=2, protocol=17) ->
    //   2404:6800::5175:15ca (socktype=1, protocol=6) ->
    //   2001::47c1 (socktype=1, protocol=6) ->
    //   ::1.2.3.4 (socktype=1, protocol=6)
    //
    // See resolv_getaddrinfo, explore_fqdn and dns_getaddrinfo.
    EXPECT_THAT(
            ToStrings(result),
            testing::ElementsAre(kHelloExampleComAddrV6_GUA, kHelloExampleComAddrV6_TEREDO,
                                 kHelloExampleComAddrV6_IPV4COMPAT, kHelloExampleComAddrV6_GUA,
                                 kHelloExampleComAddrV6_TEREDO, kHelloExampleComAddrV6_IPV4COMPAT));
}

TEST_F(ResolverTest, GetAddrInfoV4V6_MultiAnswers) {
    test::DNSResponder dns(test::DNSResponder::MappingType::BINARY_PACKET);
    // Use one IPv4 address only because we can't control IPv4 address ordering in this test
    // which uses a fake network.
    dns.addMappingBinaryPacket(kHelloExampleComQueryV4, kHelloExampleComResponseV4);
    dns.addMappingBinaryPacket(kHelloExampleComQueryV6, kHelloExampleComResponsesV6);
    StartDns(dns, {});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_DGRAM};
    ScopedAddrinfo result = safe_getaddrinfo(kHelloExampleCom, nullptr, &hints);
    ASSERT_FALSE(result == nullptr);

    // Expect the DNS result order is ipv6 global unicast address, IPv4 address, IPv6 teredo
    // tunneling address and IPv4-compatible IPv6 address because of the precedence comparison
    // of RFC 6724.
    //
    // The reason is here for the sorting result from _rfc6724_compare.
    // For rule 1: avoid unusable destinations, all addresses are unusable on a fake test network.
    // For rule 2: prefer matching scope, all addresses don't match because of no source address.
    //             See rule#1 as well.
    // (rule 3 is not implemented)
    // (rule 4 is not implemented)
    // For rule 5: prefer matching label, the source address is not valid and can't match the dns
    //             reply addresses. See rule#1 as well.
    // For rule 6: prefer higher precedence, sorted by the order: gua(40), ipv4(35), teredo(5) and
    //             ipv4-compatible(1).
    // Ignore from rule 7 to rule 10 because the results has been sorted by rule 6.
    //
    // See _get_precedence, _rfc6724_compare in packages/modules/DnsResolver/getaddrinfo.cpp
    EXPECT_THAT(
            ToStrings(result),
            testing::ElementsAre(kHelloExampleComAddrV6_GUA, kHelloExampleComAddrV4,
                                 kHelloExampleComAddrV6_TEREDO, kHelloExampleComAddrV6_IPV4COMPAT));

    hints = {.ai_family = AF_UNSPEC};
    result = safe_getaddrinfo(kHelloExampleCom, nullptr, &hints);
    ASSERT_FALSE(result == nullptr);

    // The results are sorted in every querying by explore_options and then concatenates all sorted
    // results. resolv_getaddrinfo() calls explore_fqdn() many times by the different
    // explore_options. It means that resolv_rfc6724_sort() only sorts the ordering in the results
    // of each explore_options and concatenates all sorted results into one link list. The address
    // order of the output addrinfo is:
    //   2404:6800::5175:15ca (socktype=2, protocol=17) ->
    //   1.2.3.4 (socktype=2, protocol=17) ->
    //   2001::47c1 (socktype=2, protocol=17) ->
    //   ::1.2.3.4 (socktype=2, protocol=17) ->
    //   2404:6800::5175:15ca (socktype=1, protocol=6) ->
    //   1.2.3.4 (socktype=1, protocol=6) ->
    //   2001::47c1 (socktype=1, protocol=6) ->
    //   ::1.2.3.4 (socktype=1, protocol=6)
    //
    // See resolv_getaddrinfo, explore_fqdn and dns_getaddrinfo.
    EXPECT_THAT(
            ToStrings(result),
            testing::ElementsAre(kHelloExampleComAddrV6_GUA, kHelloExampleComAddrV4,
                                 kHelloExampleComAddrV6_TEREDO, kHelloExampleComAddrV6_IPV4COMPAT,
                                 kHelloExampleComAddrV6_GUA, kHelloExampleComAddrV4,
                                 kHelloExampleComAddrV6_TEREDO, kHelloExampleComAddrV6_IPV4COMPAT));
}

TEST_F(ResolverTest, GetAddrInfo_cnames) {
    constexpr char host_name[] = "host.example.com.";
    test::DNSResponder dns;
    const std::vector<DnsRecord> records = {
            {kHelloExampleCom, ns_type::ns_t_cname, "a.example.com."},
            {"a.example.com.", ns_type::ns_t_cname, "b.example.com."},
            {"b.example.com.", ns_type::ns_t_cname, "c.example.com."},
            {"c.example.com.", ns_type::ns_t_cname, "d.example.com."},
            {"d.example.com.", ns_type::ns_t_cname, "e.example.com."},
            {"e.example.com.", ns_type::ns_t_cname, host_name},
            {host_name, ns_type::ns_t_a, "1.2.3.3"},
            {host_name, ns_type::ns_t_aaaa, "2001:db8::42"},
    };
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    addrinfo hints = {.ai_family = AF_INET};
    ScopedAddrinfo result = safe_getaddrinfo("hello", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ("1.2.3.3", ToString(result));

    dns.clearQueries();
    hints = {.ai_family = AF_INET6};
    result = safe_getaddrinfo("hello", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ("2001:db8::42", ToString(result));
}

TEST_F(ResolverTest, GetAddrInfo_cnamesNoIpAddress) {
    test::DNSResponder dns;
    const std::vector<DnsRecord> records = {
            {kHelloExampleCom, ns_type::ns_t_cname, "a.example.com."},
    };
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    addrinfo hints = {.ai_family = AF_INET};
    ScopedAddrinfo result = safe_getaddrinfo("hello", nullptr, &hints);
    EXPECT_TRUE(result == nullptr);

    dns.clearQueries();
    hints = {.ai_family = AF_INET6};
    result = safe_getaddrinfo("hello", nullptr, &hints);
    EXPECT_TRUE(result == nullptr);
}

TEST_F(ResolverTest, GetAddrInfo_cnamesIllegalRdata) {
    test::DNSResponder dns;
    const std::vector<DnsRecord> records = {
            {kHelloExampleCom, ns_type::ns_t_cname, ".!#?"},
    };
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    addrinfo hints = {.ai_family = AF_INET};
    ScopedAddrinfo result = safe_getaddrinfo("hello", nullptr, &hints);
    EXPECT_TRUE(result == nullptr);

    dns.clearQueries();
    hints = {.ai_family = AF_INET6};
    result = safe_getaddrinfo("hello", nullptr, &hints);
    EXPECT_TRUE(result == nullptr);
}

TEST_F(ResolverTest, GetAddrInfoForCaseInSensitiveDomains) {
    test::DNSResponder dns;
    const char* host_name = "howdy.example.com.";
    const char* host_name2 = "HOWDY.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
            {host_name2, ns_type::ns_t_a, "1.2.3.5"},
            {host_name2, ns_type::ns_t_aaaa, "::1.2.3.5"},
    };
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    ScopedAddrinfo hostname_result = safe_getaddrinfo("howdy", nullptr, nullptr);
    EXPECT_TRUE(hostname_result != nullptr);
    const size_t hostname1_count_after_first_query = GetNumQueries(dns, host_name);
    EXPECT_LE(1U, hostname1_count_after_first_query);
    // Could be A or AAAA
    std::string hostname_result_str = ToString(hostname_result);
    EXPECT_TRUE(hostname_result_str == "1.2.3.4" || hostname_result_str == "::1.2.3.4");

    // Verify that the name is cached.
    ScopedAddrinfo hostname2_result = safe_getaddrinfo("HOWDY", nullptr, nullptr);
    EXPECT_TRUE(hostname2_result != nullptr);
    const size_t hostname1_count_after_second_query = GetNumQueries(dns, host_name);
    EXPECT_LE(1U, hostname1_count_after_second_query);

    // verify that there is no change in num of queries for howdy.example.com
    EXPECT_EQ(hostname1_count_after_first_query, hostname1_count_after_second_query);

    // Number of queries for HOWDY.example.com would be >= 1 if domain names
    // are considered case-sensitive, else number of queries should be 0.
    const size_t hostname2_count = GetNumQueries(dns, host_name2);
    EXPECT_EQ(0U,hostname2_count);
    std::string hostname2_result_str = ToString(hostname2_result);
    EXPECT_TRUE(hostname2_result_str == "1.2.3.4" || hostname2_result_str == "::1.2.3.4");

    // verify that the result is still the same address even though
    // mixed-case string is not in the DNS
    ScopedAddrinfo result = safe_getaddrinfo("HowDY", nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    std::string result_str = ToString(result);
    EXPECT_TRUE(result_str == "1.2.3.4" || result_str == "::1.2.3.4");
}

TEST_F(ResolverTest, MultidomainResolution) {
    constexpr char host_name[] = "nihao.example2.com.";
    std::vector<std::string> searchDomains = {"example1.com", "example2.com", "example3.com"};

    test::DNSResponder dns("127.0.0.6");
    StartDns(dns, {{host_name, ns_type::ns_t_a, "1.2.3.3"}});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork({"127.0.0.6"}, searchDomains));

    const hostent* result = gethostbyname("nihao");

    EXPECT_EQ(1U, GetNumQueriesForType(dns, ns_type::ns_t_a, host_name));
    ASSERT_FALSE(result == nullptr);
    ASSERT_EQ(4, result->h_length);
    ASSERT_FALSE(result->h_addr_list[0] == nullptr);
    EXPECT_EQ("1.2.3.3", ToString(result));
    EXPECT_TRUE(result->h_addr_list[1] == nullptr);
}

TEST_F(ResolverTest, GetAddrInfoV6_numeric) {
    constexpr char host_name[] = "ohayou.example.com.";
    constexpr char numeric_addr[] = "fe80::1%lo";

    test::DNSResponder dns;
    dns.setResponseProbability(0.0);
    StartDns(dns, {{host_name, ns_type::ns_t_aaaa, "2001:db8::5"}});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    addrinfo hints = {.ai_family = AF_INET6};
    ScopedAddrinfo result = safe_getaddrinfo(numeric_addr, nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ(numeric_addr, ToString(result));
    EXPECT_TRUE(dns.queries().empty());  // Ensure no DNS queries were sent out

    // Now try a non-numeric hostname query with the AI_NUMERICHOST flag set.
    // We should fail without sending out a DNS query.
    hints.ai_flags |= AI_NUMERICHOST;
    result = safe_getaddrinfo(host_name, nullptr, &hints);
    EXPECT_TRUE(result == nullptr);
    EXPECT_TRUE(dns.queries().empty());  // Ensure no DNS queries were sent out
}

TEST_F(ResolverTest, GetAddrInfoV6_failing) {
    constexpr char listen_addr0[] = "127.0.0.7";
    constexpr char listen_addr1[] = "127.0.0.8";
    const char* host_name = "ohayou.example.com.";

    test::DNSResponder dns0(listen_addr0);
    test::DNSResponder dns1(listen_addr1);
    dns0.setResponseProbability(0.0);
    StartDns(dns0, {{host_name, ns_type::ns_t_aaaa, "2001:db8::5"}});
    StartDns(dns1, {{host_name, ns_type::ns_t_aaaa, "2001:db8::6"}});

    std::vector<std::string> servers = {listen_addr0, listen_addr1};
    // <sample validity in s> <success threshold in percent> <min samples> <max samples>
    int sample_count = 8;
    const std::array<int, IDnsResolver::RESOLVER_PARAMS_COUNT> params = {
            300, 25, sample_count, sample_count, 0, 0};
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParams::Builder()
                                                          .setDnsServers(servers)
                                                          .setDotServers({})
                                                          .setParams(params)
                                                          .build()));

    // Repeatedly perform resolutions for non-existing domains until MAXNSSAMPLES resolutions have
    // reached the dns0, which is set to fail. No more requests should then arrive at that server
    // for the next sample_lifetime seconds.
    // TODO: This approach is implementation-dependent, change once metrics reporting is available.
    const addrinfo hints = {.ai_family = AF_INET6};
    for (int i = 0; i < sample_count; ++i) {
        std::string domain = fmt::format("nonexistent{}", i);
        ScopedAddrinfo result = safe_getaddrinfo(domain.c_str(), nullptr, &hints);
    }
    // Due to 100% errors for all possible samples, the server should be ignored from now on and
    // only the second one used for all following queries, until NSSAMPLE_VALIDITY is reached.
    dns0.clearQueries();
    dns1.clearQueries();
    ScopedAddrinfo result = safe_getaddrinfo("ohayou", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ(0U, GetNumQueries(dns0, host_name));
    EXPECT_EQ(1U, GetNumQueries(dns1, host_name));
}

TEST_F(ResolverTest, GetAddrInfoV6_nonresponsive) {
    constexpr char listen_addr0[] = "127.0.0.7";
    constexpr char listen_addr1[] = "127.0.0.8";
    constexpr char listen_srv[] = "53";
    constexpr char host_name1[] = "ohayou.example.com.";
    constexpr char host_name2[] = "ciao.example.com.";
    const std::vector<std::string> defaultSearchDomain = {"example.com"};
    // The minimal timeout is 1000ms, so we can't decrease timeout
    // So reduce retry count.
    const std::array<int, IDnsResolver::RESOLVER_PARAMS_COUNT> reduceRetryParams = {
            300,      // sample validity in seconds
            25,       // success threshod in percent
            8,    8,  // {MIN,MAX}_SAMPLES
            1000,     // BASE_TIMEOUT_MSEC
            1,        // retry count
    };
    const std::vector<DnsRecord> records0 = {
            {host_name1, ns_type::ns_t_aaaa, "2001:db8::5"},
            {host_name2, ns_type::ns_t_aaaa, "2001:db8::5"},
    };
    const std::vector<DnsRecord> records1 = {
            {host_name1, ns_type::ns_t_aaaa, "2001:db8::6"},
            {host_name2, ns_type::ns_t_aaaa, "2001:db8::6"},
    };

    // dns0 does not respond with 100% probability, while
    // dns1 responds normally, at least initially.
    test::DNSResponder dns0(listen_addr0, listen_srv, static_cast<ns_rcode>(-1));
    test::DNSResponder dns1(listen_addr1, listen_srv, static_cast<ns_rcode>(-1));
    dns0.setResponseProbability(0.0);
    StartDns(dns0, records0);
    StartDns(dns1, records1);
    ASSERT_TRUE(
            mDnsClient.SetResolversFromParcel(ResolverParams::Builder()
                                                      .setDnsServers({listen_addr0, listen_addr1})
                                                      .setDotServers({})
                                                      .setParams(reduceRetryParams)
                                                      .build()));

    // Specify ai_socktype to make getaddrinfo will only query 1 time
    const addrinfo hints = {.ai_family = AF_INET6, .ai_socktype = SOCK_STREAM};

    // dns0 will ignore the request, and we'll fallback to dns1 after the first
    // retry.
    ScopedAddrinfo result = safe_getaddrinfo(host_name1, nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ(1U, GetNumQueries(dns0, host_name1));
    EXPECT_EQ(1U, GetNumQueries(dns1, host_name1));
    ExpectDnsEvent(INetdEventListener::EVENT_GETADDRINFO, 0, host_name1, {"2001:db8::6"});

    // Now make dns1 also ignore 100% requests... The resolve should alternate
    // queries between the nameservers and fail
    dns1.setResponseProbability(0.0);
    addrinfo* result2 = nullptr;
    EXPECT_EQ(EAI_NODATA, getaddrinfo(host_name2, nullptr, &hints, &result2));
    EXPECT_EQ(nullptr, result2);
    EXPECT_EQ(1U, GetNumQueries(dns0, host_name2));
    EXPECT_EQ(1U, GetNumQueries(dns1, host_name2));
    ExpectDnsEvent(INetdEventListener::EVENT_GETADDRINFO, RCODE_TIMEOUT, host_name2, {});
}

TEST_F(ResolverTest, GetAddrInfoV6_concurrent) {
    constexpr char listen_addr0[] = "127.0.0.9";
    constexpr char listen_addr1[] = "127.0.0.10";
    constexpr char listen_addr2[] = "127.0.0.11";
    constexpr char host_name[] = "konbanha.example.com.";

    test::DNSResponder dns0(listen_addr0);
    test::DNSResponder dns1(listen_addr1);
    test::DNSResponder dns2(listen_addr2);
    StartDns(dns0, {{host_name, ns_type::ns_t_aaaa, "2001:db8::5"}});
    StartDns(dns1, {{host_name, ns_type::ns_t_aaaa, "2001:db8::6"}});
    StartDns(dns2, {{host_name, ns_type::ns_t_aaaa, "2001:db8::7"}});

    const std::vector<std::string> servers = {listen_addr0, listen_addr1, listen_addr2};
    std::vector<std::thread> threads(10);
    for (std::thread& thread : threads) {
        thread = std::thread([this, &servers]() {
            unsigned delay = arc4random_uniform(1 * 1000 * 1000);  // <= 1s
            usleep(delay);
            std::vector<std::string> serverSubset;
            for (const auto& server : servers) {
                if (arc4random_uniform(2)) {
                    serverSubset.push_back(server);
                }
            }
            if (serverSubset.empty()) serverSubset = servers;
            ASSERT_TRUE(mDnsClient.SetResolversForNetwork(serverSubset));
            const addrinfo hints = {.ai_family = AF_INET6};
            addrinfo* result = nullptr;
            int rv = getaddrinfo("konbanha", nullptr, &hints, &result);
            EXPECT_EQ(0, rv) << "error [" << rv << "] " << gai_strerror(rv);
            if (result) {
                freeaddrinfo(result);
                result = nullptr;
            }
        });
    }
    for (std::thread& thread : threads) {
        thread.join();
    }

    const auto resolvInfo = mDnsClient.getResolverInfo();
    ASSERT_RESULT_OK(resolvInfo);
    EXPECT_EQ(0, resolvInfo.value().waitForPendingReqTimeoutCount);
}

TEST_F(ResolverTest, SkipBadServersDueToInternalError) {
    constexpr char listen_addr1[] = "fe80::1";
    constexpr char listen_addr2[] = "255.255.255.255";
    constexpr char listen_addr3[] = "127.0.0.3";
    int counter = 0;  // To generate unique hostnames.
    test::DNSResponder dns(listen_addr3);
    ASSERT_TRUE(dns.startServer());

    ResolverParamsParcel setupParams = DnsResponderClient::GetDefaultResolverParamsParcel();
    setupParams.servers = {listen_addr1, listen_addr2, listen_addr3};
    setupParams.minSamples = 2;  // Recognize bad servers in two attempts when sorting not enabled.

    ResolverParamsParcel cleanupParams = DnsResponderClient::GetDefaultResolverParamsParcel();
    cleanupParams.servers.clear();
    cleanupParams.tlsServers.clear();

    for (const auto& sortNameserversFlag : {"" /* unset */, "0" /* off */, "1" /* on */}) {
        SCOPED_TRACE(fmt::format("sortNameversFlag_{}", sortNameserversFlag));
        ScopedSystemProperties scopedSystemProperties(kSortNameserversFlag, sortNameserversFlag);

        // Re-setup test network to make experiment flag take effect.
        resetNetwork();

        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(setupParams));

        // Start sending synchronized querying.
        for (int i = 0; i < 100; i++) {
            std::string hostName = fmt::format("hello{}.com.", counter++);
            dns.addMapping(hostName, ns_type::ns_t_a, "1.2.3.4");
            const addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
            EXPECT_TRUE(safe_getaddrinfo(hostName.c_str(), nullptr, &hints) != nullptr);
        }

        const std::vector<NameserverStats> targetStats = {
                NameserverStats(listen_addr1).setInternalErrors(5),
                NameserverStats(listen_addr2).setInternalErrors(5),
                NameserverStats(listen_addr3).setSuccesses(setupParams.maxSamples).setRttAvg(1),
        };
        EXPECT_TRUE(expectStatsNotGreaterThan(targetStats));

        // Also verify the number of queries received in the server because res_stats.successes has
        // a maximum.
        EXPECT_EQ(dns.queries().size(), 100U);

        // Reset the state.
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(cleanupParams));
        dns.clearQueries();
    }
}

TEST_F(ResolverTest, SkipBadServersDueToTimeout) {
    constexpr char listen_addr1[] = "127.0.0.3";
    constexpr char listen_addr2[] = "127.0.0.4";
    int counter = 0;  // To generate unique hostnames.

    ResolverParamsParcel setupParams = DnsResponderClient::GetDefaultResolverParamsParcel();
    setupParams.servers = {listen_addr1, listen_addr2};
    setupParams.minSamples = 2;  // Recognize bad servers in two attempts when sorting not enabled.

    ResolverParamsParcel cleanupParams = DnsResponderClient::GetDefaultResolverParamsParcel();
    cleanupParams.servers.clear();
    cleanupParams.tlsServers.clear();

    // Set dns1 non-responsive and dns2 workable.
    test::DNSResponder dns1(listen_addr1, test::kDefaultListenService, static_cast<ns_rcode>(-1));
    test::DNSResponder dns2(listen_addr2);
    dns1.setResponseProbability(0.0);
    ASSERT_TRUE(dns1.startServer());
    ASSERT_TRUE(dns2.startServer());

    for (const auto& sortNameserversFlag : {"" /* unset */, "0" /* off */, "1" /* on */}) {
        SCOPED_TRACE(fmt::format("sortNameversFlag_{}", sortNameserversFlag));
        ScopedSystemProperties scopedSystemProperties(kSortNameserversFlag, sortNameserversFlag);

        // Re-setup test network to make experiment flag take effect.
        resetNetwork();

        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(setupParams));

        // Start sending synchronized querying.
        for (int i = 0; i < 100; i++) {
            std::string hostName = fmt::format("hello{}.com.", counter++);
            dns1.addMapping(hostName, ns_type::ns_t_a, "1.2.3.4");
            dns2.addMapping(hostName, ns_type::ns_t_a, "1.2.3.5");
            const addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
            EXPECT_TRUE(safe_getaddrinfo(hostName.c_str(), nullptr, &hints) != nullptr);
        }

        const std::vector<NameserverStats> targetStats = {
                NameserverStats(listen_addr1).setTimeouts(5),
                NameserverStats(listen_addr2).setSuccesses(setupParams.maxSamples).setRttAvg(1),
        };
        EXPECT_TRUE(expectStatsNotGreaterThan(targetStats));

        // Also verify the number of queries received in the server because res_stats.successes has
        // an upper bound.
        EXPECT_GT(dns1.queries().size(), 0U);
        EXPECT_LT(dns1.queries().size(), 5U);
        EXPECT_EQ(dns2.queries().size(), 100U);

        // Reset the state.
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(cleanupParams));
        dns1.clearQueries();
        dns2.clearQueries();
    }
}

TEST_F(ResolverTest, GetAddrInfoFromCustTable_InvalidInput) {
    constexpr char hostnameNoip[] = "noip.example.com.";
    constexpr char hostnameInvalidip[] = "invalidip.example.com.";
    const std::vector<aidl::android::net::ResolverHostsParcel> invalidCustHosts = {
            {"", hostnameNoip},
            {"wrong IP", hostnameInvalidip},
    };
    test::DNSResponder dns;
    StartDns(dns, {});
    auto resolverParams = DnsResponderClient::GetDefaultResolverParamsParcel();

    ResolverOptionsParcel resolverOptions;
    resolverOptions.hosts = invalidCustHosts;
    if (!mIsResolverOptionIPCSupported) {
        resolverParams.resolverOptions = resolverOptions;
    }
    ASSERT_TRUE(mDnsClient.resolvService()->setResolverConfiguration(resolverParams).isOk());
    if (mIsResolverOptionIPCSupported) {
        ASSERT_TRUE(mDnsClient.resolvService()
                            ->setResolverOptions(resolverParams.netId, resolverOptions)
                            .isOk());
    }
    for (const auto& hostname : {hostnameNoip, hostnameInvalidip}) {
        // The query won't get data from customized table because of invalid customized table
        // and DNSResponder also has no records. hostnameNoip has never registered and
        // hostnameInvalidip has registered but wrong IP.
        const addrinfo hints = {.ai_family = AF_UNSPEC};
        ScopedAddrinfo result = safe_getaddrinfo(hostname, nullptr, &hints);
        ASSERT_TRUE(result == nullptr);
        EXPECT_EQ(4U, GetNumQueries(dns, hostname));
    }
}

TEST_F(ResolverTest, GetAddrInfoFromCustTable) {
    constexpr char hostnameV4[] = "v4only.example.com.";
    constexpr char hostnameV6[] = "v6only.example.com.";
    constexpr char hostnameV4V6[] = "v4v6.example.com.";
    constexpr char custAddrV4[] = "1.2.3.4";
    constexpr char custAddrV6[] = "::1.2.3.4";
    constexpr char dnsSvAddrV4[] = "1.2.3.5";
    constexpr char dnsSvAddrV6[] = "::1.2.3.5";
    const std::vector<aidl::android::net::ResolverHostsParcel> custHostV4 = {
            {custAddrV4, hostnameV4},
    };
    const std::vector<aidl::android::net::ResolverHostsParcel> custHostV6 = {
            {custAddrV6, hostnameV6},
    };
    const std::vector<aidl::android::net::ResolverHostsParcel> custHostV4V6 = {
            {custAddrV4, hostnameV4V6},
            {custAddrV6, hostnameV4V6},
    };
    const std::vector<DnsRecord> dnsSvHostV4 = {
            {hostnameV4, ns_type::ns_t_a, dnsSvAddrV4},
    };
    const std::vector<DnsRecord> dnsSvHostV6 = {
            {hostnameV6, ns_type::ns_t_aaaa, dnsSvAddrV6},
    };
    const std::vector<DnsRecord> dnsSvHostV4V6 = {
            {hostnameV4V6, ns_type::ns_t_a, dnsSvAddrV4},
            {hostnameV4V6, ns_type::ns_t_aaaa, dnsSvAddrV6},
    };
    struct TestConfig {
        const std::string name;
        const std::vector<aidl::android::net::ResolverHostsParcel> customizedHosts;
        const std::vector<DnsRecord> dnsserverHosts;
        const std::vector<std::string> queryResult;
        std::string asParameters() const {
            return fmt::format("name: {}, customizedHosts: {}, dnsserverHosts: {}", name,
                               customizedHosts.empty() ? "No" : "Yes",
                               dnsserverHosts.empty() ? "No" : "Yes");
        }
    } testConfigs[]{
            // clang-format off
            {hostnameV4,    {},            {},             {}},
            {hostnameV4,    {},            dnsSvHostV4,    {dnsSvAddrV4}},
            {hostnameV4,    custHostV4,    {},             {custAddrV4}},
            {hostnameV4,    custHostV4,    dnsSvHostV4,    {custAddrV4}},
            {hostnameV6,    {},            {},             {}},
            {hostnameV6,    {},            dnsSvHostV6,    {dnsSvAddrV6}},
            {hostnameV6,    custHostV6,    {},             {custAddrV6}},
            {hostnameV6,    custHostV6,    dnsSvHostV6,    {custAddrV6}},
            {hostnameV4V6,  {},            {},             {}},
            {hostnameV4V6,  {},            dnsSvHostV4V6,  {dnsSvAddrV4, dnsSvAddrV6}},
            {hostnameV4V6,  custHostV4V6,  {},             {custAddrV4, custAddrV6}},
            {hostnameV4V6,  custHostV4V6,  dnsSvHostV4V6,  {custAddrV4, custAddrV6}},
            // clang-format on
    };

    for (const auto& config : testConfigs) {
        SCOPED_TRACE(config.asParameters());

        test::DNSResponder dns;
        StartDns(dns, config.dnsserverHosts);

        auto resolverParams = DnsResponderClient::GetDefaultResolverParamsParcel();
        ResolverOptionsParcel resolverOptions;
        resolverOptions.hosts = config.customizedHosts;
        if (!mIsResolverOptionIPCSupported) {
            resolverParams.resolverOptions = resolverOptions;
        }
        ASSERT_TRUE(mDnsClient.resolvService()->setResolverConfiguration(resolverParams).isOk());

        if (mIsResolverOptionIPCSupported) {
            ASSERT_TRUE(mDnsClient.resolvService()
                                ->setResolverOptions(resolverParams.netId, resolverOptions)
                                .isOk());
        }
        const addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_STREAM};
        ScopedAddrinfo result = safe_getaddrinfo(config.name.c_str(), nullptr, &hints);
        if (config.customizedHosts.empty() && config.dnsserverHosts.empty()) {
            ASSERT_TRUE(result == nullptr);
            EXPECT_EQ(2U, GetNumQueries(dns, config.name.c_str()));
        } else {
            ASSERT_TRUE(result != nullptr);
            EXPECT_THAT(ToStrings(result), testing::UnorderedElementsAreArray(config.queryResult));
            EXPECT_EQ(config.customizedHosts.empty() ? 2U : 0U,
                      GetNumQueries(dns, config.name.c_str()));
        }

        EXPECT_TRUE(mDnsClient.resolvService()->flushNetworkCache(TEST_NETID).isOk());
    }
}

TEST_F(ResolverTest, GetAddrInfoFromCustTable_Modify) {
    constexpr char hostnameV4V6[] = "v4v6.example.com.";
    constexpr char custAddrV4[] = "1.2.3.4";
    constexpr char custAddrV6[] = "::1.2.3.4";
    constexpr char dnsSvAddrV4[] = "1.2.3.5";
    constexpr char dnsSvAddrV6[] = "::1.2.3.5";
    const std::vector<DnsRecord> dnsSvHostV4V6 = {
            {hostnameV4V6, ns_type::ns_t_a, dnsSvAddrV4},
            {hostnameV4V6, ns_type::ns_t_aaaa, dnsSvAddrV6},
    };
    const std::vector<aidl::android::net::ResolverHostsParcel> custHostV4V6 = {
            {custAddrV4, hostnameV4V6},
            {custAddrV6, hostnameV4V6},
    };
    test::DNSResponder dns;
    StartDns(dns, dnsSvHostV4V6);
    auto resolverParams = DnsResponderClient::GetDefaultResolverParamsParcel();

    ResolverOptionsParcel resolverOptions;
    resolverOptions.hosts = custHostV4V6;
    if (!mIsResolverOptionIPCSupported) {
        resolverParams.resolverOptions = resolverOptions;
    }
    ASSERT_TRUE(mDnsClient.resolvService()->setResolverConfiguration(resolverParams).isOk());

    if (mIsResolverOptionIPCSupported) {
        ASSERT_TRUE(mDnsClient.resolvService()
                            ->setResolverOptions(resolverParams.netId, resolverOptions)
                            .isOk());
    }

    const addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_STREAM};
    ScopedAddrinfo result = safe_getaddrinfo(hostnameV4V6, nullptr, &hints);
    ASSERT_TRUE(result != nullptr);
    EXPECT_THAT(ToStrings(result), testing::UnorderedElementsAreArray({custAddrV4, custAddrV6}));
    EXPECT_EQ(0U, GetNumQueries(dns, hostnameV4V6));

    resolverOptions.hosts = {};
    if (!mIsResolverOptionIPCSupported) {
        resolverParams.resolverOptions = resolverOptions;
        ASSERT_TRUE(mDnsClient.resolvService()->setResolverConfiguration(resolverParams).isOk());
    } else {
        ASSERT_TRUE(mDnsClient.resolvService()
                            ->setResolverOptions(resolverParams.netId, resolverOptions)
                            .isOk());
    }
    result = safe_getaddrinfo(hostnameV4V6, nullptr, &hints);
    ASSERT_TRUE(result != nullptr);
    EXPECT_THAT(ToStrings(result), testing::UnorderedElementsAreArray({dnsSvAddrV4, dnsSvAddrV6}));
    EXPECT_EQ(2U, GetNumQueries(dns, hostnameV4V6));
}

TEST_F(ResolverTest, GetAddrInfoV4V6FromCustTable_MultiAnswers) {
    test::DNSResponder dns;
    StartDns(dns, {});

    auto resolverParams = DnsResponderClient::GetDefaultResolverParamsParcel();
    ResolverOptionsParcel resolverOptions;
    resolverOptions.hosts = {
            {kHelloExampleComAddrV4, kHelloExampleCom},
            {kHelloExampleComAddrV6_GUA, kHelloExampleCom},
            {kHelloExampleComAddrV6_IPV4COMPAT, kHelloExampleCom},
            {kHelloExampleComAddrV6_TEREDO, kHelloExampleCom},
    };
    if (!mIsResolverOptionIPCSupported) {
        resolverParams.resolverOptions = resolverOptions;
    }
    ASSERT_TRUE(mDnsClient.resolvService()->setResolverConfiguration(resolverParams).isOk());

    if (mIsResolverOptionIPCSupported) {
        ASSERT_TRUE(mDnsClient.resolvService()
                            ->setResolverOptions(resolverParams.netId, resolverOptions)
                            .isOk());
    }

    addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_STREAM};
    ScopedAddrinfo result = safe_getaddrinfo(kHelloExampleCom, nullptr, &hints);
    ASSERT_TRUE(result != nullptr);
    // Expect the order is the same as the order of record insertion because the custom table uses
    // std::multimap to store and the queried results are not sorted by RFC 6724.
    // See getCustomHosts in packages/modules/DnsResolver/getaddrinfo.cpp
    EXPECT_THAT(ToStrings(result),
                testing::ElementsAreArray({kHelloExampleComAddrV4, kHelloExampleComAddrV6_GUA,
                                           kHelloExampleComAddrV6_IPV4COMPAT,
                                           kHelloExampleComAddrV6_TEREDO}));
    EXPECT_EQ(0U, GetNumQueries(dns, kHelloExampleCom));

    hints = {.ai_family = AF_UNSPEC};
    result = safe_getaddrinfo(kHelloExampleCom, nullptr, &hints);
    ASSERT_TRUE(result != nullptr);
    // The overall result is the concatenation of each result from explore_fqdn().
    // resolv_getaddrinfo() calls explore_fqdn() many times by the different explore_options.
    // It means that the results of each explore_options keep the order and concatenates
    // all results into one link list. The address order of the output addrinfo is:
    //   1.2.3.4 (socktype=2, protocol=17) ->
    //   2404:6800::5175:15ca (socktype=2, protocol=17) ->
    //   ::1.2.3.4 (socktype=2, protocol=17) ->
    //   2001::47c1 (socktype=2, protocol=17) ->
    //   1.2.3.4 (socktype=1, protocol=6) ->
    //   2404:6800::5175:15ca (socktype=1, protocol=6) ->
    //   ::1.2.3.4 (socktype=1, protocol=6) ->
    //   2001::47c1 (socktype=1, protocol=6)
    //
    // See resolv_getaddrinfo, explore_fqdn and dns_getaddrinfo.
    EXPECT_THAT(ToStrings(result),
                testing::ElementsAreArray(
                        {kHelloExampleComAddrV4, kHelloExampleComAddrV6_GUA,
                         kHelloExampleComAddrV6_IPV4COMPAT, kHelloExampleComAddrV6_TEREDO,
                         kHelloExampleComAddrV4, kHelloExampleComAddrV6_GUA,
                         kHelloExampleComAddrV6_IPV4COMPAT, kHelloExampleComAddrV6_TEREDO}));
    EXPECT_EQ(0U, GetNumQueries(dns, kHelloExampleCom));
}

TEST_F(ResolverTest, EmptySetup) {
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParamsParcel{.netId = TEST_NETID}));
    const auto resolvInfo = mDnsClient.getResolverInfo();
    ASSERT_RESULT_OK(resolvInfo);
    EXPECT_TRUE(resolvInfo.value().dnsServers.empty());
    EXPECT_TRUE(resolvInfo.value().domains.empty());
    EXPECT_TRUE(resolvInfo.value().dotServers.empty());
    EXPECT_EQ(0U, resolvInfo.value().params.sample_validity);
    EXPECT_EQ(0U, resolvInfo.value().params.success_threshold);
    EXPECT_EQ(0U, resolvInfo.value().params.min_samples);
    EXPECT_EQ(0U, resolvInfo.value().params.max_samples);
    // We don't check baseTimeoutMsec and retryCount because their value differ depending on
    // the experiment flags.
}

TEST_F(ResolverTest, SearchPathChange) {
    constexpr char listen_addr[] = "127.0.0.13";
    constexpr char host_name1[] = "test13.domain1.org.";
    constexpr char host_name2[] = "test13.domain2.org.";
    std::vector<std::string> servers = {listen_addr};
    std::vector<std::string> domains = {"domain1.org"};

    const std::vector<DnsRecord> records = {
            {host_name1, ns_type::ns_t_aaaa, "2001:db8::13"},
            {host_name2, ns_type::ns_t_aaaa, "2001:db8::1:13"},
    };
    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers, domains));

    const addrinfo hints = {.ai_family = AF_INET6};
    ScopedAddrinfo result = safe_getaddrinfo("test13", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ(1U, dns.queries().size());
    EXPECT_EQ(1U, GetNumQueries(dns, host_name1));
    EXPECT_EQ("2001:db8::13", ToString(result));

    // Test that changing the domain search path on its own works.
    domains = {"domain2.org"};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers, domains));
    dns.clearQueries();

    result = safe_getaddrinfo("test13", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ(1U, dns.queries().size());
    EXPECT_EQ(1U, GetNumQueries(dns, host_name2));
    EXPECT_EQ("2001:db8::1:13", ToString(result));
}

TEST_F(ResolverTest, SearchPathPrune) {
    constexpr size_t DUPLICATED_DOMAIN_NUM = 3;
    constexpr char listen_addr[] = "127.0.0.13";
    constexpr char domian_name1[] = "domain13.org.";
    constexpr char domian_name2[] = "domain14.org.";
    constexpr char host_name1[] = "test13.domain13.org.";
    constexpr char host_name2[] = "test14.domain14.org.";
    std::vector<std::string> servers = {listen_addr};

    std::vector<std::string> testDomains1;
    std::vector<std::string> testDomains2;
    // Domain length should be <= 255
    // Max number of domains in search path is 6
    for (size_t i = 0; i < MAXDNSRCH + 1; i++) {
        // Fill up with invalid domain
        testDomains1.push_back(std::string(300, i + '0'));
        // Fill up with valid but duplicated domain
        testDomains2.push_back(fmt::format("domain{}.org", i % DUPLICATED_DOMAIN_NUM));
    }

    // Add valid domain used for query.
    testDomains1.push_back(domian_name1);

    // Add valid domain twice used for query.
    testDomains2.push_back(domian_name2);
    testDomains2.push_back(domian_name2);

    const std::vector<DnsRecord> records = {
            {host_name1, ns_type::ns_t_aaaa, "2001:db8::13"},
            {host_name2, ns_type::ns_t_aaaa, "2001:db8::1:13"},
    };
    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers, testDomains1));

    const addrinfo hints = {.ai_family = AF_INET6};
    ScopedAddrinfo result = safe_getaddrinfo("test13", nullptr, &hints);

    EXPECT_TRUE(result != nullptr);

    EXPECT_EQ(1U, dns.queries().size());
    EXPECT_EQ(1U, GetNumQueries(dns, host_name1));
    EXPECT_EQ("2001:db8::13", ToString(result));

    auto resolvInfo = mDnsClient.getResolverInfo();
    ASSERT_RESULT_OK(resolvInfo);
    const auto& res_domains1 = resolvInfo.value().domains;
    // Expect 1 valid domain, invalid domains are removed.
    ASSERT_EQ(1U, res_domains1.size());
    EXPECT_EQ(domian_name1, res_domains1[0]);

    dns.clearQueries();

    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers, testDomains2));

    result = safe_getaddrinfo("test14", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);

    // (3 domains * 2 retries) + 1 success query = 7
    EXPECT_EQ(7U, dns.queries().size());
    EXPECT_EQ(1U, GetNumQueries(dns, host_name2));
    EXPECT_EQ("2001:db8::1:13", ToString(result));

    resolvInfo = mDnsClient.getResolverInfo();
    ASSERT_RESULT_OK(resolvInfo);
    const auto& res_domains2 = resolvInfo.value().domains;
    // Expect 4 valid domain, duplicate domains are removed.
    EXPECT_EQ(DUPLICATED_DOMAIN_NUM + 1U, res_domains2.size());
    EXPECT_THAT(
            std::vector<std::string>({"domain0.org", "domain1.org", "domain2.org", domian_name2}),
            testing::ElementsAreArray(res_domains2));
}

// If we move this function to dns_responder_client, it will complicate the dependency need of
// dns_tls_frontend.h.
static void setupTlsServers(const std::vector<std::string>& servers,
                            std::vector<std::unique_ptr<test::DnsTlsFrontend>>* tls) {
    constexpr char listen_udp[] = "53";
    constexpr char listen_tls[] = "853";

    for (const auto& server : servers) {
        auto t = std::make_unique<test::DnsTlsFrontend>(server, listen_tls, server, listen_udp);
        t = std::make_unique<test::DnsTlsFrontend>(server, listen_tls, server, listen_udp);
        t->startServer();
        tls->push_back(std::move(t));
    }
}

TEST_F(ResolverTest, MaxServerPrune_Binder) {
    std::vector<std::string> domains;
    std::vector<std::unique_ptr<test::DNSResponder>> dns;
    std::vector<std::unique_ptr<test::DnsTlsFrontend>> tls;
    std::vector<std::string> servers;
    std::vector<DnsResponderClient::Mapping> mappings;

    for (unsigned i = 0; i < MAXDNSRCH + 1; i++) {
        domains.push_back(fmt::format("example{}.com", i));
    }
    ASSERT_NO_FATAL_FAILURE(mDnsClient.SetupMappings(1, domains, &mappings));
    ASSERT_NO_FATAL_FAILURE(mDnsClient.SetupDNSServers(MAXNS + 1, mappings, &dns, &servers));
    ASSERT_NO_FATAL_FAILURE(setupTlsServers(servers, &tls));

    const auto parcel = ResolverParams::Builder()
                                .setDomains(domains)
                                .setDnsServers(servers)
                                .setDotServers(servers)
                                .setPrivateDnsProvider(kDefaultPrivateDnsHostName)
                                .build();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

    // If the private DNS validation hasn't completed yet before backend DNS servers stop,
    // TLS servers will get stuck in handleOneRequest(), which causes this test stuck in
    // ~DnsTlsFrontend() because the TLS server loop threads can't be terminated.
    // So, wait for private DNS validation done before stopping backend DNS servers.
    for (int i = 0; i < MAXNS; i++) {
        LOG(INFO) << "Waiting for private DNS validation on " << tls[i]->listen_address() << ".";
        EXPECT_TRUE(WaitForPrivateDnsValidation(tls[i]->listen_address(), true));
        LOG(INFO) << "private DNS validation on " << tls[i]->listen_address() << " done.";
    }

    const auto resolvInfo = mDnsClient.getResolverInfo();
    ASSERT_RESULT_OK(resolvInfo);
    EXPECT_EQ(static_cast<size_t>(MAXNS), resolvInfo.value().dnsServers.size());
    EXPECT_EQ(static_cast<size_t>(MAXNS), resolvInfo.value().dotServers.size());
    EXPECT_EQ(static_cast<size_t>(MAXDNSRCH), resolvInfo.value().domains.size());
    EXPECT_TRUE(std::equal(servers.begin(), servers.begin() + MAXNS,
                           resolvInfo.value().dnsServers.begin()));
    EXPECT_TRUE(std::equal(servers.begin(), servers.begin() + MAXNS,
                           resolvInfo.value().dotServers.begin()));
    EXPECT_TRUE(std::equal(domains.begin(), domains.begin() + MAXDNSRCH,
                           resolvInfo.value().domains.begin()));
}

TEST_F(ResolverTest, ResolverStats) {
    constexpr char listen_addr1[] = "127.0.0.4";
    constexpr char listen_addr2[] = "127.0.0.5";
    constexpr char listen_addr3[] = "127.0.0.6";

    // Set server 1 timeout.
    test::DNSResponder dns1(listen_addr1, "53", static_cast<ns_rcode>(-1));
    dns1.setResponseProbability(0.0);
    ASSERT_TRUE(dns1.startServer());

    // Set server 2 responding server failure.
    test::DNSResponder dns2(listen_addr2);
    dns2.setResponseProbability(0.0);
    ASSERT_TRUE(dns2.startServer());

    // Set server 3 workable.
    test::DNSResponder dns3(listen_addr3);
    dns3.addMapping(kHelloExampleCom, ns_type::ns_t_a, "1.2.3.4");
    ASSERT_TRUE(dns3.startServer());

    std::vector<std::string> servers = {listen_addr1, listen_addr2, listen_addr3};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));

    dns3.clearQueries();
    const addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
    ScopedAddrinfo result = safe_getaddrinfo("hello", nullptr, &hints);
    size_t found = GetNumQueries(dns3, kHelloExampleCom);
    EXPECT_LE(1U, found);
    std::string result_str = ToString(result);
    EXPECT_TRUE(result_str == "1.2.3.4") << ", result_str='" << result_str << "'";

    const std::vector<NameserverStats> expectedCleartextDnsStats = {
            NameserverStats(listen_addr1).setTimeouts(1),
            NameserverStats(listen_addr2).setErrors(1),
            NameserverStats(listen_addr3).setSuccesses(1).setRttAvg(1),
    };
    EXPECT_TRUE(expectStatsEqualTo(expectedCleartextDnsStats));
}

TEST_F(ResolverTest, AlwaysUseLatestSetupParamsInLookups) {
    constexpr char listen_addr1[] = "127.0.0.3";
    constexpr char listen_addr2[] = "255.255.255.255";
    constexpr char listen_addr3[] = "127.0.0.4";
    constexpr char hostname[] = "hello";
    constexpr char fqdn_with_search_domain[] = "hello.domain2.com.";

    test::DNSResponder dns1(listen_addr1, test::kDefaultListenService, static_cast<ns_rcode>(-1));
    dns1.setResponseProbability(0.0);
    ASSERT_TRUE(dns1.startServer());

    test::DNSResponder dns3(listen_addr3);
    StartDns(dns3, {{fqdn_with_search_domain, ns_type::ns_t_a, "1.2.3.4"}});

    ResolverParamsParcel parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    parcel.tlsServers.clear();
    parcel.servers = {listen_addr1, listen_addr2};
    parcel.domains = {"domain1.com", "domain2.com"};
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

    // Expect the things happening in t1:
    //   1. The lookup starts using the first domain for query. It sends queries to the populated
    //      nameserver list {listen_addr1, listen_addr2} for the hostname "hello.domain1.com".
    //   2. A different list of nameservers is updated to the resolver. Revision ID is incremented.
    //   3. The query for the hostname times out. The lookup fails to add the timeout record to the
    //      the stats because of the unmatched revision ID.
    //   4. The lookup starts using the second domain for query. It sends queries to the populated
    //      nameserver list {listen_addr3, listen_addr1, listen_addr2} for another hostname
    //      "hello.domain2.com".
    //   5. The lookup gets the answer and updates a success record to the stats.
    std::thread t1([&hostname]() {
        const addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
        ScopedAddrinfo result = safe_getaddrinfo(hostname, nullptr, &hints);
        EXPECT_NE(result.get(), nullptr);
        EXPECT_EQ(ToString(result), "1.2.3.4");
    });

    // Wait for t1 to start the step 1.
    while (dns1.queries().size() == 0) {
        usleep(1000);
    }

    // Update the resolver with three nameservers. This will increment the revision ID.
    parcel.servers = {listen_addr3, listen_addr1, listen_addr2};
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

    t1.join();
    EXPECT_EQ(0U, GetNumQueriesForType(dns3, ns_type::ns_t_aaaa, fqdn_with_search_domain));
    EXPECT_EQ(1U, GetNumQueriesForType(dns3, ns_type::ns_t_a, fqdn_with_search_domain));

    const std::vector<NameserverStats> expectedCleartextDnsStats = {
            NameserverStats(listen_addr1),
            NameserverStats(listen_addr2),
            NameserverStats(listen_addr3).setSuccesses(1).setRttAvg(1),
    };
    EXPECT_TRUE(expectStatsEqualTo(expectedCleartextDnsStats));
}

// Test what happens if the specified TLS server is nonexistent.
TEST_F(ResolverTest, GetHostByName_TlsMissing) {
    constexpr char host_name[] = "tlsmissing.example.com.";
    test::DNSResponder dns;
    StartDns(dns, {{host_name, ns_type::ns_t_a, "1.2.3.3"}});

    // There's nothing listening on this address, so validation will either fail or
    /// hang.  Either way, queries will continue to flow to the DNSResponder.
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParams::Builder().build()));

    const hostent* result;
    result = gethostbyname("tlsmissing");
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("1.2.3.3", ToString(result));
}

// Test what happens if the specified TLS server replies with garbage.
TEST_F(ResolverTest, GetHostByName_TlsBroken) {
    constexpr char host_name1[] = "tlsbroken1.example.com.";
    constexpr char host_name2[] = "tlsbroken2.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name1, ns_type::ns_t_a, "1.2.3.1"},
            {host_name2, ns_type::ns_t_a, "1.2.3.2"},
    };

    test::DNSResponder dns;
    StartDns(dns, records);

    // Bind the specified private DNS socket but don't respond to any client sockets yet.
    int s = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, IPPROTO_TCP);
    ASSERT_TRUE(s >= 0);
    struct sockaddr_in tlsServer = {
            .sin_family = AF_INET,
            .sin_port = htons(853),
    };
    ASSERT_TRUE(inet_pton(AF_INET, kDefaultServer, &tlsServer.sin_addr));
    ASSERT_TRUE(enableSockopt(s, SOL_SOCKET, SO_REUSEPORT).ok());
    ASSERT_TRUE(enableSockopt(s, SOL_SOCKET, SO_REUSEADDR).ok());
    ASSERT_FALSE(bind(s, reinterpret_cast<struct sockaddr*>(&tlsServer), sizeof(tlsServer)));
    ASSERT_FALSE(listen(s, 1));

    // Trigger TLS validation.
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParams::Builder().build()));

    struct sockaddr_storage cliaddr;
    socklen_t sin_size = sizeof(cliaddr);
    int new_fd = accept4(s, reinterpret_cast<struct sockaddr*>(&cliaddr), &sin_size, SOCK_CLOEXEC);
    ASSERT_TRUE(new_fd > 0);

    // We've received the new file descriptor but not written to it or closed, so the
    // validation is still pending.  Queries should still flow correctly because the
    // server is not used until validation succeeds.
    const hostent* result;
    result = gethostbyname("tlsbroken1");
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("1.2.3.1", ToString(result));

    // Now we cause the validation to fail.
    std::string garbage = "definitely not a valid TLS ServerHello";
    write(new_fd, garbage.data(), garbage.size());
    close(new_fd);

    // Validation failure shouldn't interfere with lookups, because lookups won't be sent
    // to the TLS server unless validation succeeds.
    result = gethostbyname("tlsbroken2");
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("1.2.3.2", ToString(result));

    // Clear TLS bit.
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());
    close(s);
}

TEST_F(ResolverTest, GetHostByName_Tls) {
    constexpr char listen_udp[] = "53";
    constexpr char listen_tls[] = "853";
    constexpr char host_name1[] = "tls1.example.com.";
    constexpr char host_name2[] = "tls2.example.com.";
    constexpr char host_name3[] = "tls3.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name1, ns_type::ns_t_a, "1.2.3.1"},
            {host_name2, ns_type::ns_t_a, "1.2.3.2"},
            {host_name3, ns_type::ns_t_a, "1.2.3.3"},
    };

    test::DNSResponder dns;
    StartDns(dns, records);

    test::DnsTlsFrontend tls(kDefaultServer, listen_tls, kDefaultServer, listen_udp);
    ASSERT_TRUE(tls.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParams::Builder().build()));
    EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));

    const hostent* result = gethostbyname("tls1");
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("1.2.3.1", ToString(result));

    // Wait for query to get counted.
    EXPECT_TRUE(tls.waitForQueries(2));

    // Stop the TLS server.  Since we're in opportunistic mode, queries will
    // fall back to the locally-assigned (clear text) nameservers.
    tls.stopServer();

    dns.clearQueries();
    result = gethostbyname("tls2");
    EXPECT_FALSE(result == nullptr);
    EXPECT_EQ("1.2.3.2", ToString(result));
    const auto queries = dns.queries();
    EXPECT_EQ(1U, queries.size());
    EXPECT_EQ("tls2.example.com.", queries[0].name);
    EXPECT_EQ(ns_t_a, queries[0].type);

    // Reset the resolvers without enabling TLS.  Queries should still be routed
    // to the UDP endpoint.
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    result = gethostbyname("tls3");
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("1.2.3.3", ToString(result));
}

TEST_F(ResolverTest, GetHostByName_TlsFailover) {
    constexpr char listen_addr1[] = "127.0.0.3";
    constexpr char listen_addr2[] = "127.0.0.4";
    constexpr char listen_udp[] = "53";
    constexpr char listen_tls[] = "853";
    constexpr char host_name1[] = "tlsfailover1.example.com.";
    constexpr char host_name2[] = "tlsfailover2.example.com.";
    const std::vector<DnsRecord> records1 = {
            {host_name1, ns_type::ns_t_a, "1.2.3.1"},
            {host_name2, ns_type::ns_t_a, "1.2.3.2"},
    };
    const std::vector<DnsRecord> records2 = {
            {host_name1, ns_type::ns_t_a, "1.2.3.3"},
            {host_name2, ns_type::ns_t_a, "1.2.3.4"},
    };

    test::DNSResponder dns1(listen_addr1);
    test::DNSResponder dns2(listen_addr2);
    StartDns(dns1, records1);
    StartDns(dns2, records2);

    std::vector<std::string> servers = {listen_addr1, listen_addr2};

    test::DnsTlsFrontend tls1(listen_addr1, listen_tls, listen_addr1, listen_udp);
    test::DnsTlsFrontend tls2(listen_addr2, listen_tls, listen_addr2, listen_udp);
    ASSERT_TRUE(tls1.startServer());
    ASSERT_TRUE(tls2.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(
            ResolverParams::Builder()
                    .setDnsServers(servers)
                    .setDotServers(servers)
                    .setPrivateDnsProvider(kDefaultPrivateDnsHostName)
                    .build()));
    EXPECT_TRUE(WaitForPrivateDnsValidation(tls1.listen_address(), true));
    EXPECT_TRUE(WaitForPrivateDnsValidation(tls2.listen_address(), true));

    const hostent* result = gethostbyname("tlsfailover1");
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("1.2.3.1", ToString(result));

    // Wait for query to get counted.
    EXPECT_TRUE(tls1.waitForQueries(2));
    // No new queries should have reached tls2.
    EXPECT_TRUE(tls2.waitForQueries(1));

    // Stop tls1.  Subsequent queries should attempt to reach tls1, fail, and retry to tls2.
    tls1.stopServer();

    result = gethostbyname("tlsfailover2");
    EXPECT_EQ("1.2.3.4", ToString(result));

    // Wait for query to get counted.
    EXPECT_TRUE(tls2.waitForQueries(2));

    // No additional queries should have reached the insecure servers.
    EXPECT_EQ(2U, dns1.queries().size());
    EXPECT_EQ(2U, dns2.queries().size());

    // Clear TLS bit.
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
}

TEST_F(ResolverTest, GetHostByName_BadTlsName) {
    constexpr char listen_udp[] = "53";
    constexpr char listen_tls[] = "853";
    constexpr char host_name[] = "badtlsname.example.com.";

    test::DNSResponder dns;
    StartDns(dns, {{host_name, ns_type::ns_t_a, "1.2.3.1"}});

    test::DnsTlsFrontend tls(kDefaultServer, listen_tls, kDefaultServer, listen_udp);
    ASSERT_TRUE(tls.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(
            ResolverParams::Builder()
                    .setPrivateDnsProvider(kDefaultIncorrectPrivateDnsHostName)
                    .build()));

    // The TLS handshake would fail because the name of TLS server doesn't
    // match with TLS server's certificate.
    EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), false));

    // The query should fail hard, because a name was specified.
    EXPECT_EQ(nullptr, gethostbyname("badtlsname"));

    // Clear TLS bit.
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());
}

TEST_F(ResolverTest, GetAddrInfo_Tls) {
    constexpr char listen_udp[] = "53";
    constexpr char listen_tls[] = "853";
    constexpr char host_name[] = "addrinfotls.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };

    test::DNSResponder dns;
    StartDns(dns, records);

    test::DnsTlsFrontend tls(kDefaultServer, listen_tls, kDefaultServer, listen_udp);
    ASSERT_TRUE(tls.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(
            ResolverParams::Builder().setPrivateDnsProvider(kDefaultPrivateDnsHostName).build()));
    EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));

    dns.clearQueries();
    ScopedAddrinfo result = safe_getaddrinfo("addrinfotls", nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    size_t found = GetNumQueries(dns, host_name);
    EXPECT_LE(1U, found);
    // Could be A or AAAA
    std::string result_str = ToString(result);
    EXPECT_TRUE(result_str == "1.2.3.4" || result_str == "::1.2.3.4")
            << ", result_str='" << result_str << "'";
    // Wait for both A and AAAA queries to get counted.
    EXPECT_TRUE(tls.waitForQueries(3));
}

TEST_F(ResolverTest, TlsBypass) {
    const char OFF[] = "off";
    const char OPPORTUNISTIC[] = "opportunistic";
    const char STRICT[] = "strict";

    const char GETHOSTBYNAME[] = "gethostbyname";
    const char GETADDRINFO[] = "getaddrinfo";
    const char GETADDRINFOFORNET[] = "getaddrinfofornet";

    const unsigned BYPASS_NETID = NETID_USE_LOCAL_NAMESERVERS | TEST_NETID;

    const char ADDR4[] = "192.0.2.1";
    const char ADDR6[] = "2001:db8::1";

    const char cleartext_addr[] = "127.0.0.53";
    const char cleartext_port[] = "53";
    const char tls_port[] = "853";
    const std::vector<std::string> servers = {cleartext_addr};

    test::DNSResponder dns(cleartext_addr);
    ASSERT_TRUE(dns.startServer());

    test::DnsTlsFrontend tls(cleartext_addr, tls_port, cleartext_addr, cleartext_port);
    ASSERT_TRUE(tls.startServer());

    // clang-format off
    struct TestConfig {
        const std::string mode;
        const bool withWorkingTLS;
        const std::string method;

        std::string asHostName() const {
            return fmt::format("{}.{}.{}.", mode, withWorkingTLS ? "tlsOn" : "tlsOff",
                                method);
        }
    } testConfigs[]{
        {OFF,           true,  GETHOSTBYNAME},
        {OPPORTUNISTIC, true,  GETHOSTBYNAME},
        {STRICT,        true,  GETHOSTBYNAME},
        {OFF,           true,  GETADDRINFO},
        {OPPORTUNISTIC, true,  GETADDRINFO},
        {STRICT,        true,  GETADDRINFO},
        {OFF,           true,  GETADDRINFOFORNET},
        {OPPORTUNISTIC, true,  GETADDRINFOFORNET},
        {STRICT,        true,  GETADDRINFOFORNET},
        {OFF,           false, GETHOSTBYNAME},
        {OPPORTUNISTIC, false, GETHOSTBYNAME},
        {STRICT,        false, GETHOSTBYNAME},
        {OFF,           false, GETADDRINFO},
        {OPPORTUNISTIC, false, GETADDRINFO},
        {STRICT,        false, GETADDRINFO},
        {OFF,           false, GETADDRINFOFORNET},
        {OPPORTUNISTIC, false, GETADDRINFOFORNET},
        {STRICT,        false, GETADDRINFOFORNET},
    };
    // clang-format on

    for (const auto& config : testConfigs) {
        const std::string testHostName = config.asHostName();
        SCOPED_TRACE(testHostName);

        // Don't tempt test bugs due to caching.
        const char* host_name = testHostName.c_str();
        dns.addMapping(host_name, ns_type::ns_t_a, ADDR4);
        dns.addMapping(host_name, ns_type::ns_t_aaaa, ADDR6);

        if (config.withWorkingTLS) {
            if (!tls.running()) {
                ASSERT_TRUE(tls.startServer());
            }
        } else {
            if (tls.running()) {
                ASSERT_TRUE(tls.stopServer());
            }
        }

        if (config.mode == OFF) {
            ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
        } else /* OPPORTUNISTIC or STRICT */ {
            auto builder = ResolverParams::Builder().setDnsServers(servers).setDotServers(servers);
            if (config.mode == STRICT) builder.setPrivateDnsProvider(kDefaultPrivateDnsHostName);
            ASSERT_TRUE(mDnsClient.SetResolversFromParcel(builder.build()));

            // Wait for the validation event. If the server is running, the validation should
            // succeed; otherwise, the validation should fail.
            EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), config.withWorkingTLS));
            if (config.withWorkingTLS) {
                EXPECT_TRUE(tls.waitForQueries(1));
                tls.clearQueries();
            }
        }

        const hostent* h_result = nullptr;
        ScopedAddrinfo ai_result;

        if (config.method == GETHOSTBYNAME) {
            ASSERT_EQ(0, setNetworkForResolv(BYPASS_NETID));
            h_result = gethostbyname(host_name);

            EXPECT_EQ(1U, GetNumQueriesForType(dns, ns_type::ns_t_a, host_name));
            ASSERT_FALSE(h_result == nullptr);
            ASSERT_EQ(4, h_result->h_length);
            ASSERT_FALSE(h_result->h_addr_list[0] == nullptr);
            EXPECT_EQ(ADDR4, ToString(h_result));
            EXPECT_TRUE(h_result->h_addr_list[1] == nullptr);
        } else if (config.method == GETADDRINFO) {
            ASSERT_EQ(0, setNetworkForResolv(BYPASS_NETID));
            ai_result = safe_getaddrinfo(host_name, nullptr, nullptr);
            EXPECT_TRUE(ai_result != nullptr);

            EXPECT_LE(1U, GetNumQueries(dns, host_name));
            // Could be A or AAAA
            const std::string result_str = ToString(ai_result);
            EXPECT_TRUE(result_str == ADDR4 || result_str == ADDR6)
                    << ", result_str='" << result_str << "'";
        } else if (config.method == GETADDRINFOFORNET) {
            addrinfo* raw_ai_result = nullptr;
            EXPECT_EQ(0, android_getaddrinfofornet(host_name, /*servname=*/nullptr,
                                                   /*hints=*/nullptr, BYPASS_NETID, MARK_UNSET,
                                                   &raw_ai_result));
            ai_result.reset(raw_ai_result);

            EXPECT_LE(1U, GetNumQueries(dns, host_name));
            // Could be A or AAAA
            const std::string result_str = ToString(ai_result);
            EXPECT_TRUE(result_str == ADDR4 || result_str == ADDR6)
                    << ", result_str='" << result_str << "'";
        }

        EXPECT_EQ(0, tls.queries());

        // Clear per-process resolv netid.
        ASSERT_EQ(0, setNetworkForResolv(NETID_UNSET));
        dns.clearQueries();
    }
}

TEST_F(ResolverTest, StrictMode_NoTlsServers) {
    constexpr char host_name[] = "strictmode.notlsips.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };

    test::DNSResponder dns(kDefaultServer);
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(
            ResolverParams::Builder()
                    .setPrivateDnsProvider(kDefaultIncorrectPrivateDnsHostName)
                    .build()));

    addrinfo* ai_result = nullptr;
    EXPECT_NE(0, getaddrinfo(host_name, nullptr, nullptr, &ai_result));
    EXPECT_EQ(0U, GetNumQueries(dns, host_name));
}

namespace {

int getAsyncResponse(int fd, int* rcode, uint8_t* buf, int bufLen) {
    struct pollfd wait_fd[1];
    wait_fd[0].fd = fd;
    wait_fd[0].events = POLLIN;
    short revents;

    if (int ret = poll(wait_fd, 1, -1); ret <= 0) {
        return -1;
    }
    revents = wait_fd[0].revents;
    if (revents & POLLIN) {
        return resNetworkResult(fd, rcode, buf, bufLen);
    }
    return -1;
}

std::string toString(uint8_t* buf, int bufLen, int ipType) {
    ns_msg handle;
    ns_rr rr;

    if (ns_initparse((const uint8_t*)buf, bufLen, &handle) >= 0) {
        if (ns_parserr(&handle, ns_s_an, 0, &rr) == 0) {
            const uint8_t* rdata = ns_rr_rdata(rr);
            char buffer[INET6_ADDRSTRLEN];
            if (inet_ntop(ipType, (const char*)rdata, buffer, sizeof(buffer))) {
                return buffer;
            }
        }
    }
    return "";
}

int dns_open_proxy() {
    int s = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (s == -1) {
        return -1;
    }
    const int one = 1;
    setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));

    static const struct sockaddr_un proxy_addr = {
            .sun_family = AF_UNIX,
            .sun_path = "/dev/socket/dnsproxyd",
    };

    if (TEMP_FAILURE_RETRY(connect(s, (const struct sockaddr*)&proxy_addr, sizeof(proxy_addr))) !=
        0) {
        close(s);
        return -1;
    }

    return s;
}

void expectAnswersValid(int fd, int ipType, const std::string& expectedAnswer) {
    int rcode = -1;
    uint8_t buf[MAXPACKET] = {};

    int res = getAsyncResponse(fd, &rcode, buf, MAXPACKET);
    EXPECT_GT(res, 0);
    EXPECT_EQ(expectedAnswer, toString(buf, res, ipType));
}

void expectAnswersNotValid(int fd, int expectedErrno) {
    int rcode = -1;
    uint8_t buf[MAXPACKET] = {};

    int res = getAsyncResponse(fd, &rcode, buf, MAXPACKET);
    EXPECT_EQ(expectedErrno, res);
}

}  // namespace

TEST_F(ResolverTest, Async_NormalQueryV4V6) {
    constexpr char listen_addr[] = "127.0.0.4";
    constexpr char host_name[] = "howdy.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));

    int fd1 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a, 0);
    int fd2 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_aaaa, 0);
    EXPECT_TRUE(fd1 != -1);
    EXPECT_TRUE(fd2 != -1);

    uint8_t buf[MAXPACKET] = {};
    int rcode;
    int res = getAsyncResponse(fd2, &rcode, buf, MAXPACKET);
    EXPECT_GT(res, 0);
    EXPECT_EQ("::1.2.3.4", toString(buf, res, AF_INET6));

    res = getAsyncResponse(fd1, &rcode, buf, MAXPACKET);
    EXPECT_GT(res, 0);
    EXPECT_EQ("1.2.3.4", toString(buf, res, AF_INET));

    EXPECT_EQ(2U, GetNumQueries(dns, host_name));

    // Re-query verify cache works
    fd1 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a, 0);
    fd2 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_aaaa, 0);

    EXPECT_TRUE(fd1 != -1);
    EXPECT_TRUE(fd2 != -1);

    res = getAsyncResponse(fd2, &rcode, buf, MAXPACKET);
    EXPECT_GT(res, 0);
    EXPECT_EQ("::1.2.3.4", toString(buf, res, AF_INET6));

    res = getAsyncResponse(fd1, &rcode, buf, MAXPACKET);
    EXPECT_GT(res, 0);
    EXPECT_EQ("1.2.3.4", toString(buf, res, AF_INET));

    EXPECT_EQ(2U, GetNumQueries(dns, host_name));
}

TEST_F(ResolverTest, Async_BadQuery) {
    constexpr char listen_addr[] = "127.0.0.4";
    constexpr char host_name[] = "howdy.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));

    static struct {
        int fd;
        const char* dname;
        const int queryType;
        const int expectRcode;
    } kTestData[] = {
            {-1, "", ns_t_aaaa, 0},
            {-1, "as65ass46", ns_t_aaaa, 0},
            {-1, "454564564564", ns_t_aaaa, 0},
            {-1, "h645235", ns_t_a, 0},
            {-1, "www.google.com", ns_t_a, 0},
    };

    for (auto& td : kTestData) {
        SCOPED_TRACE(td.dname);
        td.fd = resNetworkQuery(TEST_NETID, td.dname, ns_c_in, td.queryType, 0);
        EXPECT_TRUE(td.fd != -1);
    }

    // dns_responder return empty resp(packet only contains query part) with no error currently
    for (const auto& td : kTestData) {
        uint8_t buf[MAXPACKET] = {};
        int rcode;
        SCOPED_TRACE(td.dname);
        int res = getAsyncResponse(td.fd, &rcode, buf, MAXPACKET);
        EXPECT_GT(res, 0);
        EXPECT_EQ(rcode, td.expectRcode);
    }
}

TEST_F(ResolverTest, Async_EmptyAnswer) {
    constexpr char listen_addr[] = "127.0.0.4";
    constexpr char host_name[] = "howdy.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));

    // TODO: Disable retry to make this test explicit.
    auto& cv = dns.getCv();
    auto& cvMutex = dns.getCvMutex();
    int fd1;
    // Wait on the condition variable to ensure that the DNS server has handled our first query.
    {
        std::unique_lock lk(cvMutex);
        fd1 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_aaaa, 0);
        EXPECT_TRUE(fd1 != -1);
        EXPECT_EQ(std::cv_status::no_timeout, cv.wait_for(lk, std::chrono::seconds(1)));
    }

    ExpectDnsEvent(INetdEventListener::EVENT_RES_NSEND, 0, "howdy.example.com", {"::1.2.3.4"});
    dns.setResponseProbability(0.0);

    int fd2 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a, 0);
    EXPECT_TRUE(fd2 != -1);
    ExpectDnsEvent(INetdEventListener::EVENT_RES_NSEND, RCODE_TIMEOUT, "howdy.example.com", {});

    int fd3 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a, 0);
    EXPECT_TRUE(fd3 != -1);
    ExpectDnsEvent(INetdEventListener::EVENT_RES_NSEND, RCODE_TIMEOUT, "howdy.example.com", {});

    uint8_t buf[MAXPACKET] = {};
    int rcode;

    // expect no response
    int res = getAsyncResponse(fd3, &rcode, buf, MAXPACKET);
    EXPECT_EQ(-ETIMEDOUT, res);

    // expect no response
    memset(buf, 0, MAXPACKET);
    res = getAsyncResponse(fd2, &rcode, buf, MAXPACKET);
    EXPECT_EQ(-ETIMEDOUT, res);

    dns.setResponseProbability(1.0);

    int fd4 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a, 0);
    EXPECT_TRUE(fd4 != -1);
    ExpectDnsEvent(INetdEventListener::EVENT_RES_NSEND, 0, "howdy.example.com", {"1.2.3.4"});

    memset(buf, 0, MAXPACKET);
    res = getAsyncResponse(fd4, &rcode, buf, MAXPACKET);
    EXPECT_GT(res, 0);
    EXPECT_EQ("1.2.3.4", toString(buf, res, AF_INET));

    memset(buf, 0, MAXPACKET);
    res = getAsyncResponse(fd1, &rcode, buf, MAXPACKET);
    EXPECT_GT(res, 0);
    EXPECT_EQ("::1.2.3.4", toString(buf, res, AF_INET6));
}

TEST_F(ResolverTest, Async_MalformedQuery) {
    constexpr char listen_addr[] = "127.0.0.4";
    constexpr char host_name[] = "howdy.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));

    int fd = dns_open_proxy();
    EXPECT_TRUE(fd > 0);

    const std::string badMsg = "16-52512#";
    static const struct {
        const std::string cmd;
        const int expectErr;
    } kTestData[] = {
            // Too few arguments
            {"resnsend " + badMsg + '\0', -EINVAL},
            // Bad netId
            {"resnsend badnetId 0 " + badMsg + '\0', -EINVAL},
            // Bad raw data
            {"resnsend " + std::to_string(TEST_NETID) + " 0 " + badMsg + '\0', -EILSEQ},
    };

    for (unsigned int i = 0; i < std::size(kTestData); i++) {
        auto& td = kTestData[i];
        SCOPED_TRACE(td.cmd);
        ssize_t rc = TEMP_FAILURE_RETRY(write(fd, td.cmd.c_str(), td.cmd.size()));
        EXPECT_EQ(rc, static_cast<ssize_t>(td.cmd.size()));

        int32_t tmp;
        rc = TEMP_FAILURE_RETRY(read(fd, &tmp, sizeof(tmp)));
        EXPECT_TRUE(rc > 0);
        EXPECT_EQ(static_cast<int>(ntohl(tmp)), td.expectErr);
    }
    // Normal query with answer buffer
    // This is raw data of query "howdy.example.com" type 1 class 1
    std::string query = "81sBAAABAAAAAAAABWhvd2R5B2V4YW1wbGUDY29tAAABAAE=";
    std::string cmd = "resnsend " + std::to_string(TEST_NETID) + " 0 " + query + '\0';
    ssize_t rc = TEMP_FAILURE_RETRY(write(fd, cmd.c_str(), cmd.size()));
    EXPECT_EQ(rc, static_cast<ssize_t>(cmd.size()));

    uint8_t smallBuf[1] = {};
    int rcode;
    rc = getAsyncResponse(fd, &rcode, smallBuf, 1);
    EXPECT_EQ(-EMSGSIZE, rc);

    // Do the normal test with large buffer again
    fd = dns_open_proxy();
    EXPECT_TRUE(fd > 0);
    rc = TEMP_FAILURE_RETRY(write(fd, cmd.c_str(), cmd.size()));
    EXPECT_EQ(rc, static_cast<ssize_t>(cmd.size()));
    uint8_t buf[MAXPACKET] = {};
    rc = getAsyncResponse(fd, &rcode, buf, MAXPACKET);
    EXPECT_EQ("1.2.3.4", toString(buf, rc, AF_INET));
}

TEST_F(ResolverTest, Async_CacheFlags) {
    constexpr char listen_addr[] = "127.0.0.4";
    constexpr char host_name1[] = "howdy.example.com.";
    constexpr char host_name2[] = "howdy.example2.com.";
    constexpr char host_name3[] = "howdy.example3.com.";
    const std::vector<DnsRecord> records = {
            {host_name1, ns_type::ns_t_a, "1.2.3.4"}, {host_name1, ns_type::ns_t_aaaa, "::1.2.3.4"},
            {host_name2, ns_type::ns_t_a, "1.2.3.5"}, {host_name2, ns_type::ns_t_aaaa, "::1.2.3.5"},
            {host_name3, ns_type::ns_t_a, "1.2.3.6"}, {host_name3, ns_type::ns_t_aaaa, "::1.2.3.6"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));

    // ANDROID_RESOLV_NO_CACHE_STORE
    int fd1 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a,
                              ANDROID_RESOLV_NO_CACHE_STORE);
    EXPECT_TRUE(fd1 != -1);
    int fd2 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a,
                              ANDROID_RESOLV_NO_CACHE_STORE);
    EXPECT_TRUE(fd2 != -1);
    int fd3 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a,
                              ANDROID_RESOLV_NO_CACHE_STORE);
    EXPECT_TRUE(fd3 != -1);

    expectAnswersValid(fd3, AF_INET, "1.2.3.4");
    expectAnswersValid(fd2, AF_INET, "1.2.3.4");
    expectAnswersValid(fd1, AF_INET, "1.2.3.4");

    // No cache exists, expect 3 queries
    EXPECT_EQ(3U, GetNumQueries(dns, host_name1));

    // Raise a query with no flags to ensure no cache exists. Also make an cache entry for the
    // query.
    fd1 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a, 0);

    EXPECT_TRUE(fd1 != -1);

    expectAnswersValid(fd1, AF_INET, "1.2.3.4");

    // Expect 4 queries because there should be no cache before this query.
    EXPECT_EQ(4U, GetNumQueries(dns, host_name1));

    // Now we have the cache entry, re-query with ANDROID_RESOLV_NO_CACHE_STORE to ensure
    // that ANDROID_RESOLV_NO_CACHE_STORE implied ANDROID_RESOLV_NO_CACHE_LOOKUP.
    fd1 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a,
                          ANDROID_RESOLV_NO_CACHE_STORE);
    EXPECT_TRUE(fd1 != -1);
    expectAnswersValid(fd1, AF_INET, "1.2.3.4");
    // Expect 5 queries because we shouldn't do cache lookup for the query which has
    // ANDROID_RESOLV_NO_CACHE_STORE.
    EXPECT_EQ(5U, GetNumQueries(dns, host_name1));

    // ANDROID_RESOLV_NO_CACHE_LOOKUP
    fd1 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a,
                          ANDROID_RESOLV_NO_CACHE_LOOKUP);
    fd2 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a,
                          ANDROID_RESOLV_NO_CACHE_LOOKUP);

    EXPECT_TRUE(fd1 != -1);
    EXPECT_TRUE(fd2 != -1);

    expectAnswersValid(fd2, AF_INET, "1.2.3.4");
    expectAnswersValid(fd1, AF_INET, "1.2.3.4");

    // Cache was skipped, expect 2 more queries.
    EXPECT_EQ(7U, GetNumQueries(dns, host_name1));

    // Re-query verify cache works
    fd1 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a, 0);
    EXPECT_TRUE(fd1 != -1);
    expectAnswersValid(fd1, AF_INET, "1.2.3.4");

    // Cache hits,  expect still 7 queries
    EXPECT_EQ(7U, GetNumQueries(dns, host_name1));

    // Start to verify if ANDROID_RESOLV_NO_CACHE_LOOKUP does write response into cache
    dns.clearQueries();

    fd1 = resNetworkQuery(TEST_NETID, "howdy.example2.com", ns_c_in, ns_t_aaaa,
                          ANDROID_RESOLV_NO_CACHE_LOOKUP);
    fd2 = resNetworkQuery(TEST_NETID, "howdy.example2.com", ns_c_in, ns_t_aaaa,
                          ANDROID_RESOLV_NO_CACHE_LOOKUP);

    EXPECT_TRUE(fd1 != -1);
    EXPECT_TRUE(fd2 != -1);

    expectAnswersValid(fd2, AF_INET6, "::1.2.3.5");
    expectAnswersValid(fd1, AF_INET6, "::1.2.3.5");

    // Skip cache, expect 2 queries
    EXPECT_EQ(2U, GetNumQueries(dns, host_name2));

    // Re-query without flags
    fd1 = resNetworkQuery(TEST_NETID, "howdy.example2.com", ns_c_in, ns_t_aaaa, 0);
    fd2 = resNetworkQuery(TEST_NETID, "howdy.example2.com", ns_c_in, ns_t_aaaa, 0);

    EXPECT_TRUE(fd1 != -1);
    EXPECT_TRUE(fd2 != -1);

    expectAnswersValid(fd2, AF_INET6, "::1.2.3.5");
    expectAnswersValid(fd1, AF_INET6, "::1.2.3.5");

    // Cache hits, expect still 2 queries
    EXPECT_EQ(2U, GetNumQueries(dns, host_name2));

    // Test both ANDROID_RESOLV_NO_CACHE_STORE and ANDROID_RESOLV_NO_CACHE_LOOKUP are set
    dns.clearQueries();

    // Make sure that the cache of "howdy.example3.com" exists.
    fd1 = resNetworkQuery(TEST_NETID, "howdy.example3.com", ns_c_in, ns_t_aaaa, 0);
    EXPECT_TRUE(fd1 != -1);
    expectAnswersValid(fd1, AF_INET6, "::1.2.3.6");
    EXPECT_EQ(1U, GetNumQueries(dns, host_name3));

    // Re-query with testFlags
    const int testFlag = ANDROID_RESOLV_NO_CACHE_STORE | ANDROID_RESOLV_NO_CACHE_LOOKUP;
    fd1 = resNetworkQuery(TEST_NETID, "howdy.example3.com", ns_c_in, ns_t_aaaa, testFlag);
    EXPECT_TRUE(fd1 != -1);
    expectAnswersValid(fd1, AF_INET6, "::1.2.3.6");
    // Expect cache lookup is skipped.
    EXPECT_EQ(2U, GetNumQueries(dns, host_name3));

    // Do another query with testFlags
    fd1 = resNetworkQuery(TEST_NETID, "howdy.example3.com", ns_c_in, ns_t_a, testFlag);
    EXPECT_TRUE(fd1 != -1);
    expectAnswersValid(fd1, AF_INET, "1.2.3.6");
    // Expect cache lookup is skipped.
    EXPECT_EQ(3U, GetNumQueries(dns, host_name3));

    // Re-query with no flags
    fd1 = resNetworkQuery(TEST_NETID, "howdy.example3.com", ns_c_in, ns_t_a, 0);
    EXPECT_TRUE(fd1 != -1);
    expectAnswersValid(fd1, AF_INET, "1.2.3.6");
    // Expect no cache hit because cache storing is also skipped in previous query.
    EXPECT_EQ(4U, GetNumQueries(dns, host_name3));
}

TEST_F(ResolverTest, Async_NoCacheStoreFlagDoesNotRefreshStaleCacheEntry) {
    constexpr char listen_addr[] = "127.0.0.4";
    constexpr char host_name[] = "howdy.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));

    const unsigned SHORT_TTL_SEC = 1;
    dns.setTtl(SHORT_TTL_SEC);

    // Refer to b/148842821 for the purpose of below test steps.
    // Basically, this test is used to ensure stale cache case is handled
    // correctly with ANDROID_RESOLV_NO_CACHE_STORE.
    int fd = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a, 0);
    EXPECT_TRUE(fd != -1);
    expectAnswersValid(fd, AF_INET, "1.2.3.4");

    EXPECT_EQ(1U, GetNumQueries(dns, host_name));
    dns.clearQueries();

    // Wait until cache expired
    sleep(SHORT_TTL_SEC + 0.5);

    // Now request the same hostname again.
    // We should see a new DNS query because the entry in cache has become stale.
    // Due to ANDROID_RESOLV_NO_CACHE_STORE, this query must *not* refresh that stale entry.
    fd = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a,
                         ANDROID_RESOLV_NO_CACHE_STORE);
    EXPECT_TRUE(fd != -1);
    expectAnswersValid(fd, AF_INET, "1.2.3.4");
    EXPECT_EQ(1U, GetNumQueries(dns, host_name));
    dns.clearQueries();

    // If the cache is still stale, we expect to see one more DNS query
    // (this time the cache will be refreshed, but we're not checking for it).
    fd = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a, 0);
    EXPECT_TRUE(fd != -1);
    expectAnswersValid(fd, AF_INET, "1.2.3.4");
    EXPECT_EQ(1U, GetNumQueries(dns, host_name));
}

TEST_F(ResolverTest, Async_NoRetryFlag) {
    constexpr char listen_addr0[] = "127.0.0.4";
    constexpr char listen_addr1[] = "127.0.0.6";
    constexpr char host_name[] = "howdy.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };

    test::DNSResponder dns0(listen_addr0);
    test::DNSResponder dns1(listen_addr1);
    StartDns(dns0, records);
    StartDns(dns1, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork({listen_addr0, listen_addr1}));

    dns0.clearQueries();
    dns1.clearQueries();

    dns0.setResponseProbability(0.0);
    dns1.setResponseProbability(0.0);

    int fd1 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a,
                              ANDROID_RESOLV_NO_RETRY);
    EXPECT_TRUE(fd1 != -1);

    int fd2 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_aaaa,
                              ANDROID_RESOLV_NO_RETRY);
    EXPECT_TRUE(fd2 != -1);

    // expect no response
    expectAnswersNotValid(fd1, -ETIMEDOUT);
    expectAnswersNotValid(fd2, -ETIMEDOUT);
    ExpectDnsEvent(INetdEventListener::EVENT_RES_NSEND, RCODE_TIMEOUT, "howdy.example.com", {});
    ExpectDnsEvent(INetdEventListener::EVENT_RES_NSEND, RCODE_TIMEOUT, "howdy.example.com", {});

    // No retry case, expect total 2 queries. The server is selected randomly.
    EXPECT_EQ(2U, GetNumQueries(dns0, host_name) + GetNumQueries(dns1, host_name));

    dns0.clearQueries();
    dns1.clearQueries();

    fd1 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_a, 0);
    EXPECT_TRUE(fd1 != -1);

    fd2 = resNetworkQuery(TEST_NETID, "howdy.example.com", ns_c_in, ns_t_aaaa, 0);
    EXPECT_TRUE(fd2 != -1);

    // expect no response
    expectAnswersNotValid(fd1, -ETIMEDOUT);
    expectAnswersNotValid(fd2, -ETIMEDOUT);
    ExpectDnsEvent(INetdEventListener::EVENT_RES_NSEND, RCODE_TIMEOUT, "howdy.example.com", {});
    ExpectDnsEvent(INetdEventListener::EVENT_RES_NSEND, RCODE_TIMEOUT, "howdy.example.com", {});

    // Retry case, expect 4 queries
    EXPECT_EQ(4U, GetNumQueries(dns0, host_name));
    EXPECT_EQ(4U, GetNumQueries(dns1, host_name));
}

TEST_F(ResolverTest, Async_VerifyQueryID) {
    constexpr char listen_addr[] = "127.0.0.4";
    constexpr char host_name[] = "howdy.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));

    const uint8_t queryBuf1[] = {
            /* Header */
            0x55, 0x66, /* Transaction ID */
            0x01, 0x00, /* Flags */
            0x00, 0x01, /* Questions */
            0x00, 0x00, /* Answer RRs */
            0x00, 0x00, /* Authority RRs */
            0x00, 0x00, /* Additional RRs */
            /* Queries */
            0x05, 0x68, 0x6f, 0x77, 0x64, 0x79, 0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65,
            0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
            0x00, 0x01,                   /* Type */
            0x00, 0x01                    /* Class */
    };

    int fd = resNetworkSend(TEST_NETID, queryBuf1, sizeof(queryBuf1), 0);
    EXPECT_TRUE(fd != -1);

    uint8_t buf[MAXPACKET] = {};
    int rcode;

    int res = getAsyncResponse(fd, &rcode, buf, MAXPACKET);
    EXPECT_GT(res, 0);
    EXPECT_EQ("1.2.3.4", toString(buf, res, AF_INET));

    auto hp = reinterpret_cast<HEADER*>(buf);
    EXPECT_EQ(21862U, htons(hp->id));

    EXPECT_EQ(1U, GetNumQueries(dns, host_name));

    const uint8_t queryBuf2[] = {
            /* Header */
            0x00, 0x53, /* Transaction ID */
            0x01, 0x00, /* Flags */
            0x00, 0x01, /* Questions */
            0x00, 0x00, /* Answer RRs */
            0x00, 0x00, /* Authority RRs */
            0x00, 0x00, /* Additional RRs */
            /* Queries */
            0x05, 0x68, 0x6f, 0x77, 0x64, 0x79, 0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65,
            0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
            0x00, 0x01,                   /* Type */
            0x00, 0x01                    /* Class */
    };

    // Re-query verify cache works and query id is correct
    fd = resNetworkSend(TEST_NETID, queryBuf2, sizeof(queryBuf2), 0);

    EXPECT_TRUE(fd != -1);

    res = getAsyncResponse(fd, &rcode, buf, MAXPACKET);
    EXPECT_GT(res, 0);
    EXPECT_EQ("1.2.3.4", toString(buf, res, AF_INET));

    EXPECT_EQ(0x0053U, htons(hp->id));

    EXPECT_EQ(1U, GetNumQueries(dns, host_name));
}

// This test checks that the resolver should not generate the request containing OPT RR when using
// cleartext DNS. If we query the DNS server not supporting EDNS0 and it reponds with
// FORMERR_ON_EDNS, we will fallback to no EDNS0 and try again. If the server does no response, we
// won't retry so that we get no answer.
TEST_F(ResolverTest, BrokenEdns) {
    typedef test::DNSResponder::Edns Edns;
    enum ExpectResult { EXPECT_FAILURE, EXPECT_SUCCESS };

    // Perform cleartext query in off mode.
    const char OFF[] = "off";

    // Perform cleartext query when there's no private DNS server validated in opportunistic mode.
    const char OPPORTUNISTIC_UDP[] = "opportunistic_udp";

    // Perform cleartext query when there is a private DNS server validated in opportunistic mode.
    const char OPPORTUNISTIC_FALLBACK_UDP[] = "opportunistic_fallback_udp";

    // Perform cyphertext query in opportunistic mode.
    const char OPPORTUNISTIC_TLS[] = "opportunistic_tls";

    // Perform cyphertext query in strict mode.
    const char STRICT[] = "strict";

    const char GETHOSTBYNAME[] = "gethostbyname";
    const char GETADDRINFO[] = "getaddrinfo";
    const char ADDR4[] = "192.0.2.1";
    test::DNSResponder dns(kDefaultServer, "53", ns_rcode::ns_r_servfail);
    test::DnsTlsFrontend tls(kDefaultServer, "853", kDefaultServer, "53");
    ASSERT_TRUE(dns.startServer());

    // clang-format off
    static const struct TestConfig {
        std::string mode;
        std::string method;
        Edns edns;
        ExpectResult expectResult;

        std::string asHostName() const {
            const char* ednsString;
            switch (edns) {
                case Edns::ON:
                    ednsString = "ednsOn";
                    break;
                case Edns::FORMERR_ON_EDNS:
                    ednsString = "ednsFormerr";
                    break;
                case Edns::DROP:
                    ednsString = "ednsDrop";
                    break;
                default:
                    ednsString = "";
                    break;
            }
            return fmt::format("{}.{}.{}.", mode, method, ednsString);
        }
    } testConfigs[] = {
            // In OPPORTUNISTIC_TLS, if the DNS server doesn't support EDNS0 but TLS, the lookup
            // fails. Could such server exist? if so, we might need to fix it to fallback to
            // cleartext query. If the server still make no response for the queries with EDNS0, we
            // might also need to fix it to retry without EDNS0.
            // Another thing is that {OPPORTUNISTIC_TLS, Edns::DROP} and {STRICT, Edns::DROP} are
            // commented out since TLS timeout is not configurable.
            // TODO: Uncomment them after TLS timeout is configurable.
            {OFF,                        GETHOSTBYNAME, Edns::ON,      EXPECT_SUCCESS},
            {OPPORTUNISTIC_UDP,          GETHOSTBYNAME, Edns::ON,      EXPECT_SUCCESS},
            {OPPORTUNISTIC_FALLBACK_UDP, GETHOSTBYNAME, Edns::ON,      EXPECT_SUCCESS},
            {OPPORTUNISTIC_TLS,          GETHOSTBYNAME, Edns::ON,      EXPECT_SUCCESS},
            {STRICT,                     GETHOSTBYNAME, Edns::ON,      EXPECT_SUCCESS},
            {OFF,                        GETHOSTBYNAME, Edns::FORMERR_ON_EDNS, EXPECT_SUCCESS},
            {OPPORTUNISTIC_UDP,          GETHOSTBYNAME, Edns::FORMERR_ON_EDNS, EXPECT_SUCCESS},
            {OPPORTUNISTIC_FALLBACK_UDP, GETHOSTBYNAME, Edns::FORMERR_ON_EDNS, EXPECT_SUCCESS},
            {OPPORTUNISTIC_TLS,          GETHOSTBYNAME, Edns::FORMERR_ON_EDNS, EXPECT_FAILURE},
            {STRICT,                     GETHOSTBYNAME, Edns::FORMERR_ON_EDNS, EXPECT_FAILURE},
            {OFF,                        GETHOSTBYNAME, Edns::DROP,    EXPECT_SUCCESS},
            {OPPORTUNISTIC_UDP,          GETHOSTBYNAME, Edns::DROP,    EXPECT_SUCCESS},

            // The failure is due to no retry on timeout. Maybe fix it?
            {OPPORTUNISTIC_FALLBACK_UDP, GETHOSTBYNAME, Edns::DROP,    EXPECT_FAILURE},

            //{OPPORTUNISTIC_TLS,        GETHOSTBYNAME, Edns::DROP,    EXPECT_FAILURE},
            //{STRICT,                   GETHOSTBYNAME, Edns::DROP,    EXPECT_FAILURE},
            {OFF,                        GETADDRINFO,   Edns::ON,      EXPECT_SUCCESS},
            {OPPORTUNISTIC_UDP,          GETADDRINFO,   Edns::ON,      EXPECT_SUCCESS},
            {OPPORTUNISTIC_FALLBACK_UDP, GETADDRINFO,   Edns::ON,      EXPECT_SUCCESS},
            {OPPORTUNISTIC_TLS,          GETADDRINFO,   Edns::ON,      EXPECT_SUCCESS},
            {STRICT,                     GETADDRINFO,   Edns::ON,      EXPECT_SUCCESS},
            {OFF,                        GETADDRINFO,   Edns::FORMERR_ON_EDNS, EXPECT_SUCCESS},
            {OPPORTUNISTIC_UDP,          GETADDRINFO,   Edns::FORMERR_ON_EDNS, EXPECT_SUCCESS},
            {OPPORTUNISTIC_FALLBACK_UDP, GETADDRINFO,   Edns::FORMERR_ON_EDNS, EXPECT_SUCCESS},
            {OPPORTUNISTIC_TLS,          GETADDRINFO,   Edns::FORMERR_ON_EDNS, EXPECT_FAILURE},
            {STRICT,                     GETADDRINFO,   Edns::FORMERR_ON_EDNS, EXPECT_FAILURE},
            {OFF,                        GETADDRINFO,   Edns::DROP,    EXPECT_SUCCESS},
            {OPPORTUNISTIC_UDP,          GETADDRINFO,   Edns::DROP,    EXPECT_SUCCESS},

            // The failure is due to no retry on timeout. Maybe fix it?
            {OPPORTUNISTIC_FALLBACK_UDP, GETADDRINFO,   Edns::DROP,    EXPECT_FAILURE},

            //{OPPORTUNISTIC_TLS, GETADDRINFO,   Edns::DROP,   EXPECT_FAILURE},
            //{STRICT,            GETADDRINFO,   Edns::DROP,   EXPECT_FAILURE},
    };
    // clang-format on

    for (const auto& config : testConfigs) {
        const std::string testHostName = config.asHostName();
        SCOPED_TRACE(testHostName);

        // Don't skip unusable DoT servers and disable revalidation for this test.
        ScopedSystemProperties sp1(kDotXportUnusableThresholdFlag, "-1");
        ScopedSystemProperties sp2(kDotRevalidationThresholdFlag, "-1");
        resetNetwork();

        const char* host_name = testHostName.c_str();
        dns.addMapping(host_name, ns_type::ns_t_a, ADDR4);
        dns.setEdns(config.edns);

        if (config.mode == OFF) {
            if (tls.running()) {
                ASSERT_TRUE(tls.stopServer());
            }
            ASSERT_TRUE(mDnsClient.SetResolversForNetwork());
        } else if (config.mode == OPPORTUNISTIC_UDP) {
            if (tls.running()) {
                ASSERT_TRUE(tls.stopServer());
            }
            ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParams::Builder().build()));
            EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), false));
        } else if (config.mode == OPPORTUNISTIC_TLS || config.mode == OPPORTUNISTIC_FALLBACK_UDP) {
            if (!tls.running()) {
                ASSERT_TRUE(tls.startServer());
            }
            ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParams::Builder().build()));
            EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));

            if (config.mode == OPPORTUNISTIC_FALLBACK_UDP) {
                // Force the resolver to fallback to cleartext queries.
                ASSERT_TRUE(tls.stopServer());
            }
        } else if (config.mode == STRICT) {
            if (!tls.running()) {
                ASSERT_TRUE(tls.startServer());
            }
            ASSERT_TRUE(mDnsClient.SetResolversFromParcel(
                    ResolverParams::Builder()
                            .setPrivateDnsProvider(kDefaultPrivateDnsHostName)
                            .build()));
            EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));
        }

        if (config.method == GETHOSTBYNAME) {
            const hostent* h_result = gethostbyname(host_name);
            if (config.expectResult == EXPECT_SUCCESS) {
                EXPECT_LE(1U, GetNumQueries(dns, host_name));
                ASSERT_TRUE(h_result != nullptr);
                ASSERT_EQ(4, h_result->h_length);
                ASSERT_FALSE(h_result->h_addr_list[0] == nullptr);
                EXPECT_EQ(ADDR4, ToString(h_result));
                EXPECT_TRUE(h_result->h_addr_list[1] == nullptr);
                ExpectDnsEvent(INetdEventListener::EVENT_GETHOSTBYNAME, 0, host_name, {ADDR4});
            } else {
                EXPECT_EQ(0U, GetNumQueriesForType(dns, ns_type::ns_t_a, host_name));
                ASSERT_TRUE(h_result == nullptr);
                ASSERT_EQ(HOST_NOT_FOUND, h_errno);
                int returnCode = (config.edns == Edns::DROP) ? RCODE_TIMEOUT : EAI_FAIL;
                ExpectDnsEvent(INetdEventListener::EVENT_GETHOSTBYNAME, returnCode, host_name, {});
            }
        } else if (config.method == GETADDRINFO) {
            ScopedAddrinfo ai_result;
            addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
            ai_result = safe_getaddrinfo(host_name, nullptr, &hints);
            if (config.expectResult == EXPECT_SUCCESS) {
                EXPECT_TRUE(ai_result != nullptr);
                EXPECT_EQ(1U, GetNumQueries(dns, host_name));
                const std::string result_str = ToString(ai_result);
                EXPECT_EQ(ADDR4, result_str);
                ExpectDnsEvent(INetdEventListener::EVENT_GETADDRINFO, 0, host_name, {ADDR4});
            } else {
                EXPECT_TRUE(ai_result == nullptr);
                EXPECT_EQ(0U, GetNumQueries(dns, host_name));
                int returnCode = (config.edns == Edns::DROP) ? RCODE_TIMEOUT : EAI_FAIL;
                ExpectDnsEvent(INetdEventListener::EVENT_GETADDRINFO, returnCode, host_name, {});
            }
        } else {
            FAIL() << "Unsupported query method: " << config.method;
        }

        tls.clearQueries();
        dns.clearQueries();

        // Set the configuration to OFF mode, so the resolver can validate private DNS servers in
        // every test config.
        ASSERT_TRUE(mDnsClient.SetResolversForNetwork());
    }
}

// DNS-over-TLS validation success, but server does not respond to TLS query after a while.
// Resolver should have a reasonable number of retries instead of spinning forever. We don't have
// an efficient way to know if resolver is stuck in an infinite loop. However, test case will be
// failed due to timeout.
TEST_F(ResolverTest, UnstableTls) {
    const char CLEARTEXT_PORT[] = "53";
    const char TLS_PORT[] = "853";
    const char* host_name1 = "nonexistent1.example.com.";
    const char* host_name2 = "nonexistent2.example.com.";

    test::DNSResponder dns(kDefaultServer, CLEARTEXT_PORT, ns_rcode::ns_r_servfail);
    ASSERT_TRUE(dns.startServer());
    dns.setEdns(test::DNSResponder::Edns::FORMERR_ON_EDNS);
    test::DnsTlsFrontend tls(kDefaultServer, TLS_PORT, kDefaultServer, CLEARTEXT_PORT);
    ASSERT_TRUE(tls.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParams::Builder().build()));
    EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));

    // Shutdown TLS server to get an error. It's similar to no response case but without waiting.
    tls.stopServer();

    const hostent* h_result = gethostbyname(host_name1);
    EXPECT_EQ(1U, GetNumQueries(dns, host_name1));
    ASSERT_TRUE(h_result == nullptr);
    ASSERT_EQ(HOST_NOT_FOUND, h_errno);

    addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
    ScopedAddrinfo ai_result = safe_getaddrinfo(host_name2, nullptr, &hints);
    EXPECT_TRUE(ai_result == nullptr);
    EXPECT_EQ(1U, GetNumQueries(dns, host_name2));
}

// DNS-over-TLS validation success, but server does not respond to TLS query after a while.
// Moreover, server responds RCODE=FORMERR even on non-EDNS query.
TEST_F(ResolverTest, BogusDnsServer) {
    const char CLEARTEXT_PORT[] = "53";
    const char TLS_PORT[] = "853";
    const char* host_name1 = "nonexistent1.example.com.";
    const char* host_name2 = "nonexistent2.example.com.";

    test::DNSResponder dns(kDefaultServer, CLEARTEXT_PORT, ns_rcode::ns_r_servfail);
    ASSERT_TRUE(dns.startServer());
    test::DnsTlsFrontend tls(kDefaultServer, TLS_PORT, kDefaultServer, CLEARTEXT_PORT);
    ASSERT_TRUE(tls.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(ResolverParams::Builder().build()));
    EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));

    // Shutdown TLS server to get an error. It's similar to no response case but without waiting.
    tls.stopServer();
    dns.setEdns(test::DNSResponder::Edns::FORMERR_UNCOND);

    const hostent* h_result = gethostbyname(host_name1);
    EXPECT_EQ(0U, GetNumQueries(dns, host_name1));
    ASSERT_TRUE(h_result == nullptr);
    ASSERT_EQ(HOST_NOT_FOUND, h_errno);

    addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
    ScopedAddrinfo ai_result = safe_getaddrinfo(host_name2, nullptr, &hints);
    EXPECT_TRUE(ai_result == nullptr);
    EXPECT_EQ(0U, GetNumQueries(dns, host_name2));
}

TEST_F(ResolverTest, GetAddrInfo_Dns64Synthesize) {
    constexpr char listen_addr[] = "::1";
    constexpr char host_name[] = "v4only.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);

    std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // If the socket type is not specified, every address will appear twice, once for
    // SOCK_STREAM and one for SOCK_DGRAM. Just pick one because the addresses for
    // the second query of different socket type are responded by the cache.
    // See android_getaddrinfofornetcontext in packages/modules/DnsResolver/getaddrinfo.cpp
    const addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_DGRAM};
    ScopedAddrinfo result = safe_getaddrinfo("v4only", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    // Expect that there are two queries, one AAAA (which returns no records) and one A
    // (which returns 1.2.3.4).
    EXPECT_EQ(2U, GetNumQueries(dns, host_name));
    EXPECT_THAT(ToStrings(result), testing::ElementsAre("64:ff9b::102:304", "1.2.3.4"));

    // Stopping NAT64 prefix discovery disables synthesis.
    EXPECT_TRUE(mDnsClient.resolvService()->stopPrefix64Discovery(TEST_NETID).isOk());
    EXPECT_TRUE(WaitForNat64Prefix(EXPECT_NOT_FOUND));
    dns.clearQueries();

    result = safe_getaddrinfo("v4only", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    // Expect that there is one query, an AAAA (which returns no records), because the
    // A is already cached.
    EXPECT_EQ(1U, GetNumQueries(dns, host_name));
    EXPECT_EQ(ToString(result), "1.2.3.4");
}

// TODO: merge to #GetAddrInfo_Dns64Synthesize once DNSResponder supports multi DnsRecords for a
// hostname.
TEST_F(ResolverTest, GetAddrInfo_Dns64SynthesizeMultiAnswers) {
    test::DNSResponder dns(test::DNSResponder::MappingType::BINARY_PACKET);
    dns.addMappingBinaryPacket(kHelloExampleComQueryV4, kHelloExampleComResponsesV4);
    StartDns(dns, {});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    const addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_DGRAM};
    ScopedAddrinfo result = safe_getaddrinfo(kHelloExampleCom, nullptr, &hints);
    ASSERT_FALSE(result == nullptr);

    // Synthesize AAAA if there's no AAAA answer and AF_UNSPEC is specified.
    EXPECT_THAT(ToStrings(result),
                testing::ElementsAre("64:ff9b::102:304", "64:ff9b::808:808", "64:ff9b::5175:15ca",
                                     "1.2.3.4", "8.8.8.8", "81.117.21.202"));
}

TEST_F(ResolverTest, GetAddrInfo_Dns64Canonname) {
    constexpr char listen_addr[] = "::1";
    constexpr char host_name[] = "v4only.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);

    std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // clang-format off
    static const struct TestConfig {
        int family;
        int flags;
        std::vector<std::string> expectedAddresses;
        const char* expectedCanonname;

        std::string asParameters() const {
            return fmt::format("family={}, flags={}", family, flags);
        }
    } testConfigs[]{
        {AF_UNSPEC,            0, {"64:ff9b::102:304", "1.2.3.4"}, nullptr},
        {AF_UNSPEC, AI_CANONNAME, {"64:ff9b::102:304", "1.2.3.4"}, "v4only.example.com"},
        {AF_INET6,             0, {"64:ff9b::102:304"}           , nullptr},
        {AF_INET6,  AI_CANONNAME, {"64:ff9b::102:304"}           , "v4only.example.com"},
    };
    // clang-format on

    for (const auto& config : testConfigs) {
        SCOPED_TRACE(config.asParameters());

        const addrinfo hints = {
                .ai_family = config.family, .ai_flags = config.flags, .ai_socktype = SOCK_DGRAM};
        ScopedAddrinfo result = safe_getaddrinfo("v4only", nullptr, &hints);
        ASSERT_TRUE(result != nullptr);
        EXPECT_THAT(ToStrings(result), testing::ElementsAreArray(config.expectedAddresses));
        for (const auto* ai = result.get(); ai != nullptr; ai = ai->ai_next) {
            EXPECT_STREQ(ai->ai_canonname, config.expectedCanonname);
        }
    }
}

TEST_F(ResolverTest, GetAddrInfo_Dns64QuerySpecified) {
    constexpr char listen_addr[] = "::1";
    constexpr char host_name[] = "v4only.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // Synthesize AAAA if AF_INET6 is specified and there is A record only. Make sure that A record
    // is not returned as well.
    addrinfo hints = {.ai_family = AF_INET6, .ai_socktype = SOCK_DGRAM};
    ScopedAddrinfo result = safe_getaddrinfo("v4only", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    // One for AAAA query without an answer and one for A query which is used for DNS64 synthesis.
    EXPECT_EQ(2U, GetNumQueries(dns, host_name));
    EXPECT_EQ(ToString(result), "64:ff9b::102:304");
    dns.clearQueries();

    // Don't synthesize AAAA if AF_INET is specified and there is A record only.
    hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
    result = safe_getaddrinfo("v4only", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ(0U /*cached in previous queries*/, GetNumQueries(dns, host_name));
    EXPECT_EQ(ToString(result), "1.2.3.4");
}

TEST_F(ResolverTest, GetAddrInfo_Dns64QueryUnspecifiedV6) {
    constexpr char listen_addr[] = "::1";
    constexpr char host_name[] = "v4v6.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "2001:db8::1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    const addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_DGRAM};
    ScopedAddrinfo result = safe_getaddrinfo("v4v6", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ(2U, GetNumQueries(dns, host_name));

    // Do not synthesize AAAA if there's at least one AAAA answer.
    EXPECT_THAT(ToStrings(result), testing::ElementsAre("2001:db8::102:304", "1.2.3.4"));
}

TEST_F(ResolverTest, GetAddrInfo_Dns64QueryUnspecifiedNoV6) {
    constexpr char listen_addr[] = "::1";
    constexpr char host_name[] = "v4v6.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    const addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_DGRAM};
    ScopedAddrinfo result = safe_getaddrinfo("v4v6", nullptr, &hints);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ(2U, GetNumQueries(dns, host_name));

    // Synthesize AAAA if there's no AAAA answer and AF_UNSPEC is specified.
    EXPECT_THAT(ToStrings(result), testing::ElementsAre("64:ff9b::102:304", "1.2.3.4"));
}

TEST_F(ResolverTest, GetAddrInfo_Dns64QuerySpecialUseIPv4Addresses) {
    constexpr char THIS_NETWORK[] = "this_network";
    constexpr char LOOPBACK[] = "loopback";
    constexpr char LINK_LOCAL[] = "link_local";
    constexpr char MULTICAST[] = "multicast";
    constexpr char LIMITED_BROADCAST[] = "limited_broadcast";

    constexpr char ADDR_THIS_NETWORK[] = "0.0.0.1";
    constexpr char ADDR_LOOPBACK[] = "127.0.0.1";
    constexpr char ADDR_LINK_LOCAL[] = "169.254.0.1";
    constexpr char ADDR_MULTICAST[] = "224.0.0.1";
    constexpr char ADDR_LIMITED_BROADCAST[] = "255.255.255.255";

    constexpr char listen_addr[] = "::1";

    test::DNSResponder dns(listen_addr);
    StartDns(dns, {});
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // clang-format off
    static const struct TestConfig {
        std::string name;
        std::string addr;

        std::string asHostName() const { return fmt::format("{}.example.com.", name); }
    } testConfigs[]{
        {THIS_NETWORK,      ADDR_THIS_NETWORK},
        {LOOPBACK,          ADDR_LOOPBACK},
        {LINK_LOCAL,        ADDR_LINK_LOCAL},
        {MULTICAST,         ADDR_MULTICAST},
        {LIMITED_BROADCAST, ADDR_LIMITED_BROADCAST}
    };
    // clang-format on

    for (const auto& config : testConfigs) {
        const std::string testHostName = config.asHostName();
        SCOPED_TRACE(testHostName);

        const char* host_name = testHostName.c_str();
        dns.addMapping(host_name, ns_type::ns_t_a, config.addr.c_str());

        // Expect no result because AF_INET6 is specified and don't synthesize special use IPv4
        // address.
        addrinfo hints = {.ai_family = AF_INET6, .ai_socktype = SOCK_DGRAM};
        ScopedAddrinfo result = safe_getaddrinfo(config.name.c_str(), nullptr, &hints);
        EXPECT_TRUE(result == nullptr);
        EXPECT_EQ(2U, GetNumQueries(dns, host_name));
        dns.clearQueries();

        // Expect special use IPv4 address only because AF_UNSPEC is specified and don't synthesize
        // special use IPv4 address.
        hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_DGRAM};
        result = safe_getaddrinfo(config.name.c_str(), nullptr, &hints);
        EXPECT_TRUE(result != nullptr);
        // Expect IPv6 query only. IPv4 answer has been cached in previous query.
        EXPECT_EQ(1U, GetNumQueries(dns, host_name));
        EXPECT_EQ(ToString(result), config.addr);
        dns.clearQueries();
    }
}

TEST_F(ResolverTest, GetAddrInfo_Dns64QueryWithNullArgumentHints) {
    constexpr char listen_addr[] = "::1";
    constexpr char host_name[] = "v4only.example.com.";
    constexpr char host_name2[] = "v4v6.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name2, ns_type::ns_t_a, "1.2.3.4"},
            {host_name2, ns_type::ns_t_aaaa, "2001:db8::1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // Synthesize AAAA if there is A answer only and AF_UNSPEC (hints NULL) is specified.
    // Assign argument hints of getaddrinfo() as null is equivalent to set ai_family AF_UNSPEC,
    // ai_socktype 0 (any), and ai_protocol 0 (any). Note the setting ai_socktype 0 (any) causes
    // that every address will appear twice, once for SOCK_STREAM and one for SOCK_DGRAM.
    // See resolv_getaddrinfo in packages/modules/DnsResolver/getaddrinfo.cpp.
    ScopedAddrinfo result = safe_getaddrinfo("v4only", nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    EXPECT_LE(2U, GetNumQueries(dns, host_name));
    EXPECT_THAT(ToStrings(result),
                testing::ElementsAre("64:ff9b::102:304", "64:ff9b::102:304", "1.2.3.4", "1.2.3.4"));
    dns.clearQueries();

    // Do not synthesize AAAA if there's at least one AAAA answer.
    // The reason which the addresses appear twice is as mentioned above.
    result = safe_getaddrinfo("v4v6", nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    EXPECT_LE(2U, GetNumQueries(dns, host_name2));
    EXPECT_THAT(ToStrings(result),
                testing::UnorderedElementsAre("2001:db8::102:304", "2001:db8::102:304", "1.2.3.4",
                                              "1.2.3.4"));
}

TEST_F(ResolverTest, GetAddrInfo_Dns64QueryNullArgumentNode) {
    constexpr char ADDR_ANYADDR_V4[] = "0.0.0.0";
    constexpr char ADDR_ANYADDR_V6[] = "::";
    constexpr char ADDR_LOCALHOST_V4[] = "127.0.0.1";
    constexpr char ADDR_LOCALHOST_V6[] = "::1";

    constexpr char PORT_NAME_HTTP[] = "http";
    constexpr char PORT_NUMBER_HTTP[] = "80";

    constexpr char listen_addr[] = "::1";

    test::DNSResponder dns(listen_addr);
    StartDns(dns, {});
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // clang-format off
    // If node is null, return address is listed by libc/getaddrinfo.c as follows.
    // - passive socket -> anyaddr (0.0.0.0 or ::)
    // - non-passive socket -> localhost (127.0.0.1 or ::1)
    static const struct TestConfig {
        int flag;
        std::string addr_v4;
        std::string addr_v6;

        std::string asParameters() const {
            return fmt::format("flag={}, addr_v4={}, addr_v6={}", flag, addr_v4,
                                addr_v6);
        }
    } testConfigs[]{
        {0 /* non-passive */, ADDR_LOCALHOST_V4, ADDR_LOCALHOST_V6},
        {AI_PASSIVE,          ADDR_ANYADDR_V4,   ADDR_ANYADDR_V6}
    };
    // clang-format on

    for (const auto& config : testConfigs) {
        SCOPED_TRACE(config.asParameters());

        addrinfo hints = {
                .ai_flags = config.flag,
                .ai_family = AF_UNSPEC,  // any address family
                .ai_socktype = 0,        // any type
                .ai_protocol = 0,        // any protocol
        };

        // Assign hostname as null and service as port name.
        ScopedAddrinfo result = safe_getaddrinfo(nullptr, PORT_NAME_HTTP, &hints);
        ASSERT_TRUE(result != nullptr);

        // Can't be synthesized because it should not get into Netd.
        // Every address appears twice, once for SOCK_STREAM and one for SOCK_DGRAM because the
        // socket type is not specified.
        EXPECT_THAT(ToStrings(result),
                    testing::UnorderedElementsAre(config.addr_v4, config.addr_v4, config.addr_v6,
                                                  config.addr_v6));

        // Assign hostname as null and service as numeric port number.
        hints.ai_flags = config.flag | AI_NUMERICSERV;
        result = safe_getaddrinfo(nullptr, PORT_NUMBER_HTTP, &hints);
        ASSERT_TRUE(result != nullptr);

        // Can't be synthesized because it should not get into Netd.
        // The reason which the addresses appear twice is as mentioned above.
        EXPECT_THAT(ToStrings(result),
                    testing::UnorderedElementsAre(config.addr_v4, config.addr_v4, config.addr_v6,
                                                  config.addr_v6));
    }
}

TEST_F(ResolverTest, GetHostByAddr_ReverseDnsQueryWithHavingNat64Prefix) {
    struct hostent* result = nullptr;
    struct in_addr v4addr;
    struct in6_addr v6addr;

    constexpr char listen_addr[] = "::1";
    constexpr char ptr_name[] = "v4v6.example.com.";
    // PTR record for IPv4 address 1.2.3.4
    constexpr char ptr_addr_v4[] = "4.3.2.1.in-addr.arpa.";
    // PTR record for IPv6 address 2001:db8::102:304
    constexpr char ptr_addr_v6[] =
            "4.0.3.0.2.0.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.";
    const std::vector<DnsRecord> records = {
            {ptr_addr_v4, ns_type::ns_t_ptr, ptr_name},
            {ptr_addr_v6, ns_type::ns_t_ptr, ptr_name},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // Reverse IPv4 DNS query. Prefix should have no effect on it.
    inet_pton(AF_INET, "1.2.3.4", &v4addr);
    result = gethostbyaddr(&v4addr, sizeof(v4addr), AF_INET);
    ASSERT_TRUE(result != nullptr);
    std::string result_str = result->h_name ? result->h_name : "null";
    EXPECT_EQ(result_str, "v4v6.example.com");

    // Reverse IPv6 DNS query. Prefix should have no effect on it.
    inet_pton(AF_INET6, "2001:db8::102:304", &v6addr);
    result = gethostbyaddr(&v6addr, sizeof(v6addr), AF_INET6);
    ASSERT_TRUE(result != nullptr);
    result_str = result->h_name ? result->h_name : "null";
    EXPECT_EQ(result_str, "v4v6.example.com");
}

TEST_F(ResolverTest, GetHostByAddr_ReverseDns64Query) {
    constexpr char listen_addr[] = "::1";
    constexpr char ptr_name[] = "v4only.example.com.";
    // PTR record for IPv4 address 1.2.3.4
    constexpr char ptr_addr_v4[] = "4.3.2.1.in-addr.arpa.";
    // PTR record for IPv6 address 64:ff9b::1.2.3.4
    constexpr char ptr_addr_v6_nomapping[] =
            "4.0.3.0.2.0.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.b.9.f.f.4.6.0.0.ip6.arpa.";
    constexpr char ptr_name_v6_synthesis[] = "v6synthesis.example.com.";
    // PTR record for IPv6 address 64:ff9b::5.6.7.8
    constexpr char ptr_addr_v6_synthesis[] =
            "8.0.7.0.6.0.5.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.b.9.f.f.4.6.0.0.ip6.arpa.";
    const std::vector<DnsRecord> records = {
            {ptr_addr_v4, ns_type::ns_t_ptr, ptr_name},
            {ptr_addr_v6_synthesis, ns_type::ns_t_ptr, ptr_name_v6_synthesis},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    // "ptr_addr_v6_nomapping" is not mapped in DNS server
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // Synthesized PTR record doesn't exist on DNS server
    // Reverse IPv6 DNS64 query while DNS server doesn't have an answer for synthesized address.
    // After querying synthesized address failed, expect that prefix is removed from IPv6
    // synthesized address and do reverse IPv4 query instead.
    struct in6_addr v6addr;
    inet_pton(AF_INET6, "64:ff9b::1.2.3.4", &v6addr);
    struct hostent* result = gethostbyaddr(&v6addr, sizeof(v6addr), AF_INET6);
    ASSERT_TRUE(result != nullptr);
    EXPECT_LE(1U, GetNumQueries(dns, ptr_addr_v6_nomapping));  // PTR record not exist
    EXPECT_LE(1U, GetNumQueries(dns, ptr_addr_v4));            // PTR record exist
    std::string result_str = result->h_name ? result->h_name : "null";
    EXPECT_EQ(result_str, "v4only.example.com");
    // Check that return address has been mapped from IPv4 to IPv6 address because Netd
    // removes NAT64 prefix and does IPv4 DNS reverse lookup in this case. Then, Netd
    // fakes the return IPv4 address as original queried IPv6 address.
    result_str = ToString(result);
    EXPECT_EQ(result_str, "64:ff9b::102:304");
    dns.clearQueries();

    // Synthesized PTR record exists on DNS server
    // Reverse IPv6 DNS64 query while DNS server has an answer for synthesized address.
    // Expect to Netd pass through synthesized address for DNS queries.
    inet_pton(AF_INET6, "64:ff9b::5.6.7.8", &v6addr);
    result = gethostbyaddr(&v6addr, sizeof(v6addr), AF_INET6);
    ASSERT_TRUE(result != nullptr);
    EXPECT_LE(1U, GetNumQueries(dns, ptr_addr_v6_synthesis));
    result_str = result->h_name ? result->h_name : "null";
    EXPECT_EQ(result_str, "v6synthesis.example.com");
}

TEST_F(ResolverTest, GetHostByAddr_ReverseDns64QueryFromHostFile) {
    constexpr char host_name[] = "localhost";
    // The address is synthesized by prefix64:localhost.
    constexpr char host_addr[] = "64:ff9b::7f00:1";
    constexpr char listen_addr[] = "::1";

    test::DNSResponder dns(listen_addr);
    StartDns(dns, {});
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // Using synthesized "localhost" address to be a trick for resolving host name
    // from host file /etc/hosts and "localhost" is the only name in /etc/hosts. Note that this is
    // not realistic: the code never synthesizes AAAA records for addresses in 127.0.0.0/8.
    struct in6_addr v6addr;
    inet_pton(AF_INET6, host_addr, &v6addr);
    struct hostent* result = gethostbyaddr(&v6addr, sizeof(v6addr), AF_INET6);
    ASSERT_TRUE(result != nullptr);
    // Expect no DNS queries; localhost is resolved via /etc/hosts.
    EXPECT_EQ(0U, GetNumQueries(dns, host_name));

    ASSERT_EQ(sizeof(in6_addr), (unsigned)result->h_length);
    ASSERT_EQ(AF_INET6, result->h_addrtype);
    std::string result_str = ToString(result);
    EXPECT_EQ(result_str, host_addr);
    result_str = result->h_name ? result->h_name : "null";
    EXPECT_EQ(result_str, host_name);
}

TEST_F(ResolverTest, GetHostByAddr_cnamesClasslessReverseDelegation) {
    // IPv4 addresses in the subnet with notation '/' or '-'.
    constexpr char addr_slash[] = "192.0.2.1";
    constexpr char addr_hyphen[] = "192.0.3.1";

    // Used to verify DNS reverse query for classless reverse lookup zone. See detail in RFC 2317
    // section 4.
    const static std::vector<DnsRecord> records = {
            // The records for reverse querying "192.0.2.1" in the subnet with notation '/'.
            {"1.2.0.192.in-addr.arpa.", ns_type::ns_t_cname, "1.0/25.2.0.192.in-addr.arpa."},
            {"1.0/25.2.0.192.in-addr.arpa.", ns_type::ns_t_ptr, kHelloExampleCom},

            // The records for reverse querying "192.0.3.1" in the subnet with notation '-'.
            {"1.3.0.192.in-addr.arpa.", ns_type::ns_t_cname, "1.0-127.3.0.192.in-addr.arpa."},
            {"1.0-127.3.0.192.in-addr.arpa.", ns_type::ns_t_ptr, kHelloExampleCom},
    };

    test::DNSResponder dns;
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    for (const auto& address : {addr_slash, addr_hyphen}) {
        SCOPED_TRACE(address);

        in_addr v4addr;
        ASSERT_TRUE(inet_pton(AF_INET, address, &v4addr));
        hostent* result = gethostbyaddr(&v4addr, sizeof(v4addr), AF_INET);
        ASSERT_TRUE(result != nullptr);
        EXPECT_STREQ("hello.example.com", result->h_name);
    }
}

TEST_F(ResolverTest, GetNameInfo_ReverseDnsQueryWithHavingNat64Prefix) {
    constexpr char listen_addr[] = "::1";
    constexpr char ptr_name[] = "v4v6.example.com.";
    // PTR record for IPv4 address 1.2.3.4
    constexpr char ptr_addr_v4[] = "4.3.2.1.in-addr.arpa.";
    // PTR record for IPv6 address 2001:db8::102:304
    constexpr char ptr_addr_v6[] =
            "4.0.3.0.2.0.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.";
    const std::vector<DnsRecord> records = {
            {ptr_addr_v4, ns_type::ns_t_ptr, ptr_name},
            {ptr_addr_v6, ns_type::ns_t_ptr, ptr_name},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // clang-format off
    static const struct TestConfig {
        int flag;
        int family;
        std::string addr;
        std::string host;

        std::string asParameters() const {
            return fmt::format("flag={}, family={}, addr={}, host={}", flag, family, addr,
                                host);
        }
    } testConfigs[]{
        {NI_NAMEREQD,    AF_INET,  "1.2.3.4",           "v4v6.example.com"},
        {NI_NUMERICHOST, AF_INET,  "1.2.3.4",           "1.2.3.4"},
        {0,              AF_INET,  "1.2.3.4",           "v4v6.example.com"},
        {0,              AF_INET,  "5.6.7.8",           "5.6.7.8"},           // unmapped
        {NI_NAMEREQD,    AF_INET6, "2001:db8::102:304", "v4v6.example.com"},
        {NI_NUMERICHOST, AF_INET6, "2001:db8::102:304", "2001:db8::102:304"},
        {0,              AF_INET6, "2001:db8::102:304", "v4v6.example.com"},
        {0,              AF_INET6, "2001:db8::506:708", "2001:db8::506:708"}, // unmapped
    };
    // clang-format on

    // Reverse IPv4/IPv6 DNS query. Prefix should have no effect on it.
    for (const auto& config : testConfigs) {
        SCOPED_TRACE(config.asParameters());

        int rv;
        char host[NI_MAXHOST];
        struct sockaddr_in sin;
        struct sockaddr_in6 sin6;
        if (config.family == AF_INET) {
            memset(&sin, 0, sizeof(sin));
            sin.sin_family = AF_INET;
            inet_pton(AF_INET, config.addr.c_str(), &sin.sin_addr);
            rv = getnameinfo((const struct sockaddr*)&sin, sizeof(sin), host, sizeof(host), nullptr,
                             0, config.flag);
            if (config.flag == NI_NAMEREQD) EXPECT_LE(1U, GetNumQueries(dns, ptr_addr_v4));
        } else if (config.family == AF_INET6) {
            memset(&sin6, 0, sizeof(sin6));
            sin6.sin6_family = AF_INET6;
            inet_pton(AF_INET6, config.addr.c_str(), &sin6.sin6_addr);
            rv = getnameinfo((const struct sockaddr*)&sin6, sizeof(sin6), host, sizeof(host),
                             nullptr, 0, config.flag);
            if (config.flag == NI_NAMEREQD) EXPECT_LE(1U, GetNumQueries(dns, ptr_addr_v6));
        }
        ASSERT_EQ(0, rv);
        std::string result_str = host;
        EXPECT_EQ(result_str, config.host);
        dns.clearQueries();
    }
}

TEST_F(ResolverTest, GetNameInfo_ReverseDns64Query) {
    constexpr char listen_addr[] = "::1";
    constexpr char ptr_name[] = "v4only.example.com.";
    // PTR record for IPv4 address 1.2.3.4
    constexpr char ptr_addr_v4[] = "4.3.2.1.in-addr.arpa.";
    // PTR record for IPv6 address 64:ff9b::1.2.3.4
    constexpr char ptr_addr_v6_nomapping[] =
            "4.0.3.0.2.0.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.b.9.f.f.4.6.0.0.ip6.arpa.";
    constexpr char ptr_name_v6_synthesis[] = "v6synthesis.example.com.";
    // PTR record for IPv6 address 64:ff9b::5.6.7.8
    constexpr char ptr_addr_v6_synthesis[] =
            "8.0.7.0.6.0.5.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.b.9.f.f.4.6.0.0.ip6.arpa.";
    const std::vector<DnsRecord> records = {
            {ptr_addr_v4, ns_type::ns_t_ptr, ptr_name},
            {ptr_addr_v6_synthesis, ns_type::ns_t_ptr, ptr_name_v6_synthesis},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // clang-format off
    static const struct TestConfig {
        bool hasSynthesizedPtrRecord;
        int flag;
        std::string addr;
        std::string host;

        std::string asParameters() const {
            return fmt::format("hasSynthesizedPtrRecord={}, flag={}, addr={}, host={}",
                                hasSynthesizedPtrRecord, flag, addr, host);
        }
    } testConfigs[]{
        {false, NI_NAMEREQD,    "64:ff9b::102:304", "v4only.example.com"},
        {false, NI_NUMERICHOST, "64:ff9b::102:304", "64:ff9b::102:304"},
        {false, 0,              "64:ff9b::102:304", "v4only.example.com"},
        {true,  NI_NAMEREQD,    "64:ff9b::506:708", "v6synthesis.example.com"},
        {true,  NI_NUMERICHOST, "64:ff9b::506:708", "64:ff9b::506:708"},
        {true,  0,              "64:ff9b::506:708", "v6synthesis.example.com"}
    };
    // clang-format on

    // hasSynthesizedPtrRecord = false
    //   Synthesized PTR record doesn't exist on DNS server
    //   Reverse IPv6 DNS64 query while DNS server doesn't have an answer for synthesized address.
    //   After querying synthesized address failed, expect that prefix is removed from IPv6
    //   synthesized address and do reverse IPv4 query instead.
    //
    // hasSynthesizedPtrRecord = true
    //   Synthesized PTR record exists on DNS server
    //   Reverse IPv6 DNS64 query while DNS server has an answer for synthesized address.
    //   Expect to just pass through synthesized address for DNS queries.
    for (const auto& config : testConfigs) {
        SCOPED_TRACE(config.asParameters());

        char host[NI_MAXHOST];
        struct sockaddr_in6 sin6;
        memset(&sin6, 0, sizeof(sin6));
        sin6.sin6_family = AF_INET6;
        inet_pton(AF_INET6, config.addr.c_str(), &sin6.sin6_addr);
        int rv = getnameinfo((const struct sockaddr*)&sin6, sizeof(sin6), host, sizeof(host),
                             nullptr, 0, config.flag);
        ASSERT_EQ(0, rv);
        if (config.flag == NI_NAMEREQD) {
            if (config.hasSynthesizedPtrRecord) {
                EXPECT_LE(1U, GetNumQueries(dns, ptr_addr_v6_synthesis));
            } else {
                EXPECT_LE(1U, GetNumQueries(dns, ptr_addr_v6_nomapping));  // PTR record not exist.
                EXPECT_LE(1U, GetNumQueries(dns, ptr_addr_v4));            // PTR record exist.
            }
        }
        std::string result_str = host;
        EXPECT_EQ(result_str, config.host);
        dns.clearQueries();
    }
}

TEST_F(ResolverTest, GetNameInfo_ReverseDns64QueryFromHostFile) {
    constexpr char host_name[] = "localhost";
    // The address is synthesized by prefix64:localhost.
    constexpr char host_addr[] = "64:ff9b::7f00:1";
    constexpr char listen_addr[] = "::1";

    test::DNSResponder dns(listen_addr);

    StartDns(dns, {});
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // Using synthesized "localhost" address to be a trick for resolving host name
    // from host file /etc/hosts and "localhost" is the only name in /etc/hosts. Note that this is
    // not realistic: the code never synthesizes AAAA records for addresses in 127.0.0.0/8.
    char host[NI_MAXHOST];
    struct sockaddr_in6 sin6 = {.sin6_family = AF_INET6};
    inet_pton(AF_INET6, host_addr, &sin6.sin6_addr);
    int rv = getnameinfo((const struct sockaddr*)&sin6, sizeof(sin6), host, sizeof(host), nullptr,
                         0, NI_NAMEREQD);
    ASSERT_EQ(0, rv);
    // Expect no DNS queries; localhost is resolved via /etc/hosts.
    EXPECT_EQ(0U, GetNumQueries(dns, host_name));

    std::string result_str = host;
    EXPECT_EQ(result_str, host_name);
}

TEST_F(ResolverTest, GetNameInfo_cnamesClasslessReverseDelegation) {
    // IPv4 addresses in the subnet with notation '/' or '-'.
    constexpr char addr_slash[] = "192.0.2.1";
    constexpr char addr_hyphen[] = "192.0.3.1";

    // Used to verify DNS reverse query for classless reverse lookup zone. See detail in RFC 2317
    // section 4.
    const static std::vector<DnsRecord> records = {
            // The records for reverse querying "192.0.2.1" in the subnet with notation '/'.
            {"1.2.0.192.in-addr.arpa.", ns_type::ns_t_cname, "1.0/25.2.0.192.in-addr.arpa."},
            {"1.0/25.2.0.192.in-addr.arpa.", ns_type::ns_t_ptr, kHelloExampleCom},

            // The records for reverse querying "192.0.3.1" in the subnet with notation '-'.
            {"1.3.0.192.in-addr.arpa.", ns_type::ns_t_cname, "1.0-127.3.0.192.in-addr.arpa."},
            {"1.0-127.3.0.192.in-addr.arpa.", ns_type::ns_t_ptr, kHelloExampleCom},
    };

    test::DNSResponder dns;
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    for (const auto& address : {addr_slash, addr_hyphen}) {
        SCOPED_TRACE(address);

        char host[NI_MAXHOST];
        sockaddr_in sin = {.sin_family = AF_INET};
        ASSERT_TRUE(inet_pton(AF_INET, address, &sin.sin_addr));
        int rv = getnameinfo((const sockaddr*)&sin, sizeof(sin), host, sizeof(host), nullptr, 0,
                             NI_NAMEREQD);
        ASSERT_EQ(0, rv);
        EXPECT_STREQ("hello.example.com", host);
    }
}

TEST_F(ResolverTest, GetHostByName2_Dns64Synthesize) {
    constexpr char listen_addr[] = "::1";
    constexpr char host_name[] = "ipv4only.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // Query an IPv4-only hostname. Expect that gets a synthesized address.
    struct hostent* result = gethostbyname2("ipv4only", AF_INET6);
    ASSERT_TRUE(result != nullptr);
    EXPECT_LE(1U, GetNumQueries(dns, host_name));
    std::string result_str = ToString(result);
    EXPECT_EQ(result_str, "64:ff9b::102:304");
}

TEST_F(ResolverTest, GetHostByName2_DnsQueryWithHavingNat64Prefix) {
    constexpr char host_name[] = "v4v6.example.com.";
    constexpr char listen_addr[] = "::1";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "2001:db8::1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // IPv4 DNS query. Prefix should have no effect on it.
    struct hostent* result = gethostbyname2("v4v6", AF_INET);
    ASSERT_TRUE(result != nullptr);
    EXPECT_LE(1U, GetNumQueries(dns, host_name));
    std::string result_str = ToString(result);
    EXPECT_EQ(result_str, "1.2.3.4");
    dns.clearQueries();

    // IPv6 DNS query. Prefix should have no effect on it.
    result = gethostbyname2("v4v6", AF_INET6);
    ASSERT_TRUE(result != nullptr);
    EXPECT_LE(1U, GetNumQueries(dns, host_name));
    result_str = ToString(result);
    EXPECT_EQ(result_str, "2001:db8::102:304");
}

TEST_F(ResolverTest, GetHostByName2_Dns64QuerySpecialUseIPv4Addresses) {
    constexpr char THIS_NETWORK[] = "this_network";
    constexpr char LOOPBACK[] = "loopback";
    constexpr char LINK_LOCAL[] = "link_local";
    constexpr char MULTICAST[] = "multicast";
    constexpr char LIMITED_BROADCAST[] = "limited_broadcast";

    constexpr char ADDR_THIS_NETWORK[] = "0.0.0.1";
    constexpr char ADDR_LOOPBACK[] = "127.0.0.1";
    constexpr char ADDR_LINK_LOCAL[] = "169.254.0.1";
    constexpr char ADDR_MULTICAST[] = "224.0.0.1";
    constexpr char ADDR_LIMITED_BROADCAST[] = "255.255.255.255";

    constexpr char listen_addr[] = "::1";

    test::DNSResponder dns(listen_addr);
    StartDns(dns, {});
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    ASSERT_TRUE(mDnsClient.resolvService()->setPrefix64(TEST_NETID, kNat64Prefix).isOk());

    // clang-format off
    static const struct TestConfig {
        std::string name;
        std::string addr;

        std::string asHostName() const {
            return fmt::format("{}.example.com.", name);
        }
    } testConfigs[]{
        {THIS_NETWORK,      ADDR_THIS_NETWORK},
        {LOOPBACK,          ADDR_LOOPBACK},
        {LINK_LOCAL,        ADDR_LINK_LOCAL},
        {MULTICAST,         ADDR_MULTICAST},
        {LIMITED_BROADCAST, ADDR_LIMITED_BROADCAST}
    };
    // clang-format on

    for (const auto& config : testConfigs) {
        const std::string testHostName = config.asHostName();
        SCOPED_TRACE(testHostName);

        const char* host_name = testHostName.c_str();
        dns.addMapping(host_name, ns_type::ns_t_a, config.addr.c_str());

        struct hostent* result = gethostbyname2(config.name.c_str(), AF_INET6);
        EXPECT_LE(1U, GetNumQueries(dns, host_name));

        // In AF_INET6 case, don't synthesize special use IPv4 address.
        // Expect to have no answer
        EXPECT_EQ(nullptr, result);

        dns.clearQueries();
    }
}

TEST_F(ResolverTest, PrefixDiscoveryBypassTls) {
    constexpr char listen_addr[] = "::1";
    constexpr char dns64_name[] = "ipv4only.arpa.";
    const std::vector<std::string> servers = {listen_addr};

    test::DNSResponder dns(listen_addr);
    StartDns(dns, {{dns64_name, ns_type::ns_t_aaaa, "64:ff9b::192.0.0.170"}});
    test::DnsTlsFrontend tls(listen_addr, "853", listen_addr, "53");
    ASSERT_TRUE(tls.startServer());

    for (const std::string_view dnsMode : {"OPPORTUNISTIC", "STRICT"}) {
        SCOPED_TRACE(fmt::format("testConfig: [{}]", dnsMode));
        auto builder = ResolverParams::Builder().setDnsServers(servers).setDotServers(servers);
        if (dnsMode == "STRICT") {
            builder.setPrivateDnsProvider(kDefaultPrivateDnsHostName);
        }
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(builder.build()));
        EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));
        EXPECT_TRUE(tls.waitForQueries(1));
        tls.clearQueries();

        // Start NAT64 prefix discovery.
        EXPECT_TRUE(mDnsClient.resolvService()->startPrefix64Discovery(TEST_NETID).isOk());
        EXPECT_TRUE(WaitForNat64Prefix(EXPECT_FOUND));

        // Verify that the DNS query for the NAT64 prefix bypassed private DNS.
        EXPECT_EQ(0, tls.queries()) << dns.dumpQueries();
        EXPECT_EQ(1U, GetNumQueries(dns, dns64_name)) << dns.dumpQueries();

        // Stop the prefix discovery to make DnsResolver send the prefix-removed event
        // earlier. Without this, DnsResolver still sends the event once the network
        // is destroyed; however, it will fail the next test if the test unexpectedly
        // receives the event that it doesn't want.
        EXPECT_TRUE(mDnsClient.resolvService()->stopPrefix64Discovery(TEST_NETID).isOk());
        EXPECT_TRUE(WaitForNat64Prefix(EXPECT_NOT_FOUND));

        dns.clearQueries();
        EXPECT_TRUE(mDnsClient.resolvService()->flushNetworkCache(TEST_NETID).isOk());
    }

    EXPECT_EQ(0, sDnsMetricsListener->getUnexpectedNat64PrefixUpdates());
    EXPECT_EQ(0, sUnsolicitedEventListener->getUnexpectedNat64PrefixUpdates());
}

TEST_F(ResolverTest, SetAndClearNat64Prefix) {
    constexpr char host_name[] = "v4.example.com.";
    constexpr char listen_addr[] = "::1";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, records);
    const std::vector<std::string> servers = {listen_addr};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));

    auto resolvService = mDnsClient.resolvService();
    addrinfo hints = {.ai_family = AF_INET6};

    // No NAT64 prefix, no AAAA record.
    ScopedAddrinfo result = safe_getaddrinfo("v4.example.com", nullptr, &hints);
    ASSERT_TRUE(result == nullptr);

    // Set the prefix, and expect to get a synthesized AAAA record.
    EXPECT_TRUE(resolvService->setPrefix64(TEST_NETID, kNat64Prefix2).isOk());
    result = safe_getaddrinfo("v4.example.com", nullptr, &hints);
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("2001:db8:6464::102:304", ToString(result));

    // Update the prefix, expect to see AAAA records from the new prefix.
    EXPECT_TRUE(resolvService->setPrefix64(TEST_NETID, kNat64Prefix).isOk());
    result = safe_getaddrinfo("v4.example.com", nullptr, &hints);
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("64:ff9b::102:304", ToString(result));

    // Non-/96 prefixes are ignored.
    auto status = resolvService->setPrefix64(TEST_NETID, "64:ff9b::/64");
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EX_SERVICE_SPECIFIC, status.getExceptionCode());
    EXPECT_EQ(EINVAL, status.getServiceSpecificError());

    // Invalid prefixes are ignored.
    status = resolvService->setPrefix64(TEST_NETID, "192.0.2.0/24");
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EX_SERVICE_SPECIFIC, status.getExceptionCode());
    EXPECT_EQ(EINVAL, status.getServiceSpecificError());

    status = resolvService->setPrefix64(TEST_NETID, "192.0.2.1");
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EX_SERVICE_SPECIFIC, status.getExceptionCode());
    EXPECT_EQ(EINVAL, status.getServiceSpecificError());

    status = resolvService->setPrefix64(TEST_NETID, "hello");
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EX_SERVICE_SPECIFIC, status.getExceptionCode());
    EXPECT_EQ(EINVAL, status.getServiceSpecificError());

    // DNS64 synthesis is still working.
    result = safe_getaddrinfo("v4.example.com", nullptr, &hints);
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("64:ff9b::102:304", ToString(result));

    // Clear the prefix. No AAAA records any more.
    EXPECT_TRUE(resolvService->setPrefix64(TEST_NETID, "").isOk());
    result = safe_getaddrinfo("v4.example.com", nullptr, &hints);
    EXPECT_TRUE(result == nullptr);

    // Calling startPrefix64Discovery clears the prefix.
    EXPECT_TRUE(resolvService->setPrefix64(TEST_NETID, kNat64Prefix).isOk());
    result = safe_getaddrinfo("v4.example.com", nullptr, &hints);
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("64:ff9b::102:304", ToString(result));

    EXPECT_TRUE(resolvService->startPrefix64Discovery(TEST_NETID).isOk());
    result = safe_getaddrinfo("v4.example.com", nullptr, &hints);
    ASSERT_TRUE(result == nullptr);

    // setPrefix64 fails if prefix discovery is started, even if no prefix is yet discovered...
    status = resolvService->setPrefix64(TEST_NETID, kNat64Prefix);
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EX_SERVICE_SPECIFIC, status.getExceptionCode());
    EXPECT_EQ(EEXIST, status.getServiceSpecificError());

    // .. and clearing the prefix also has no effect.
    status = resolvService->setPrefix64(TEST_NETID, "");
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EX_SERVICE_SPECIFIC, status.getExceptionCode());
    EXPECT_EQ(ENOENT, status.getServiceSpecificError());

    // setPrefix64 succeeds again when prefix discovery is stopped.
    EXPECT_TRUE(resolvService->stopPrefix64Discovery(TEST_NETID).isOk());
    EXPECT_TRUE(resolvService->setPrefix64(TEST_NETID, kNat64Prefix).isOk());
    result = safe_getaddrinfo("v4.example.com", nullptr, &hints);
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("64:ff9b::102:304", ToString(result));

    // Calling stopPrefix64Discovery clears the prefix.
    EXPECT_TRUE(resolvService->stopPrefix64Discovery(TEST_NETID).isOk());
    result = safe_getaddrinfo("v4.example.com", nullptr, &hints);
    ASSERT_TRUE(result == nullptr);

    // Set up NAT64 prefix discovery.
    constexpr char dns64_name[] = "ipv4only.arpa.";
    const std::vector<DnsRecord> newRecords = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {dns64_name, ns_type::ns_t_aaaa, "64:ff9b::192.0.0.170"},
    };
    dns.stopServer();
    StartDns(dns, newRecords);

    EXPECT_TRUE(resolvService->startPrefix64Discovery(TEST_NETID).isOk());
    EXPECT_TRUE(WaitForNat64Prefix(EXPECT_FOUND));
    result = safe_getaddrinfo("v4.example.com", nullptr, &hints);
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("64:ff9b::102:304", ToString(result));

    // setPrefix64 fails if NAT64 prefix discovery has succeeded, and the discovered prefix
    // continues to be used.
    status = resolvService->setPrefix64(TEST_NETID, kNat64Prefix2);
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EX_SERVICE_SPECIFIC, status.getExceptionCode());
    EXPECT_EQ(EEXIST, status.getServiceSpecificError());

    // Clearing the prefix also has no effect if discovery is started.
    status = resolvService->setPrefix64(TEST_NETID, "");
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(EX_SERVICE_SPECIFIC, status.getExceptionCode());
    EXPECT_EQ(ENOENT, status.getServiceSpecificError());

    result = safe_getaddrinfo("v4.example.com", nullptr, &hints);
    ASSERT_FALSE(result == nullptr);
    EXPECT_EQ("64:ff9b::102:304", ToString(result));

    EXPECT_TRUE(resolvService->stopPrefix64Discovery(TEST_NETID).isOk());
    EXPECT_TRUE(WaitForNat64Prefix(EXPECT_NOT_FOUND));

    EXPECT_EQ(0, sDnsMetricsListener->getUnexpectedNat64PrefixUpdates());
    EXPECT_EQ(0, sUnsolicitedEventListener->getUnexpectedNat64PrefixUpdates());
}

namespace {

class ScopedSetNetworkForProcess {
  public:
    explicit ScopedSetNetworkForProcess(unsigned netId) {
        mStoredNetId = getNetworkForProcess();
        if (netId == mStoredNetId) return;
        EXPECT_EQ(0, setNetworkForProcess(netId));
    }
    ~ScopedSetNetworkForProcess() { EXPECT_EQ(0, setNetworkForProcess(mStoredNetId)); }

  private:
    unsigned mStoredNetId;
};

class ScopedSetNetworkForResolv {
  public:
    explicit ScopedSetNetworkForResolv(unsigned netId) { EXPECT_EQ(0, setNetworkForResolv(netId)); }
    ~ScopedSetNetworkForResolv() { EXPECT_EQ(0, setNetworkForResolv(NETID_UNSET)); }
};

void sendCommand(int fd, const std::string& cmd) {
    ssize_t rc = TEMP_FAILURE_RETRY(write(fd, cmd.c_str(), cmd.size() + 1));
    EXPECT_EQ(rc, static_cast<ssize_t>(cmd.size() + 1));
}

int32_t readBE32(int fd) {
    int32_t tmp;
    int n = TEMP_FAILURE_RETRY(read(fd, &tmp, sizeof(tmp)));
    EXPECT_TRUE(n > 0);
    return ntohl(tmp);
}

int readResponseCode(int fd) {
    char buf[4];
    int n = TEMP_FAILURE_RETRY(read(fd, &buf, sizeof(buf)));
    EXPECT_TRUE(n > 0);
    // The format of response code is that 4 bytes for the code & null.
    buf[3] = '\0';
    int result;
    EXPECT_TRUE(ParseInt(buf, &result));
    return result;
}

bool checkAndClearUseLocalNameserversFlag(unsigned* netid) {
    if (netid == nullptr || ((*netid) & NETID_USE_LOCAL_NAMESERVERS) == 0) {
        return false;
    }
    *netid = (*netid) & ~NETID_USE_LOCAL_NAMESERVERS;
    return true;
}

aidl::android::net::UidRangeParcel makeUidRangeParcel(int start, int stop) {
    aidl::android::net::UidRangeParcel res;
    res.start = start;
    res.stop = stop;

    return res;
}

void expectNetIdWithLocalNameserversFlag(unsigned netId) {
    unsigned dnsNetId = 0;
    EXPECT_EQ(0, getNetworkForDns(&dnsNetId));
    EXPECT_TRUE(checkAndClearUseLocalNameserversFlag(&dnsNetId));
    EXPECT_EQ(netId, static_cast<unsigned>(dnsNetId));
}

void expectDnsNetIdEquals(unsigned netId) {
    unsigned dnsNetId = 0;
    EXPECT_EQ(0, getNetworkForDns(&dnsNetId));
    EXPECT_EQ(netId, static_cast<unsigned>(dnsNetId));
}

void expectDnsNetIdIsDefaultNetwork(INetd* netdService) {
    int currentNetid;
    EXPECT_TRUE(netdService->networkGetDefault(&currentNetid).isOk());
    expectDnsNetIdEquals(currentNetid);
}

void expectDnsNetIdWithVpn(INetd* netdService, unsigned vpnNetId, unsigned expectedNetId) {
    if (DnsResponderClient::isRemoteVersionSupported(netdService, 6)) {
        const auto& config = DnsResponderClient::makeNativeNetworkConfig(
                vpnNetId, NativeNetworkType::VIRTUAL, INetd::PERMISSION_NONE, /*secure=*/false);
        EXPECT_TRUE(netdService->networkCreate(config).isOk());
    } else {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
        EXPECT_TRUE(netdService->networkCreateVpn(vpnNetId, false /* secure */).isOk());
#pragma clang diagnostic pop
    }

    uid_t uid = getuid();
    // Add uid to VPN
    EXPECT_TRUE(netdService->networkAddUidRanges(vpnNetId, {makeUidRangeParcel(uid, uid)}).isOk());
    expectDnsNetIdEquals(expectedNetId);
    EXPECT_TRUE(netdService->networkDestroy(vpnNetId).isOk());
}

}  // namespace

TEST_F(ResolverTest, getDnsNetId) {
    // We've called setNetworkForProcess in SetupOemNetwork, so reset to default first.
    setNetworkForProcess(NETID_UNSET);

    expectDnsNetIdIsDefaultNetwork(mDnsClient.netdService());
    expectDnsNetIdWithVpn(mDnsClient.netdService(), TEST_VPN_NETID, TEST_VPN_NETID);

    // Test with setNetworkForProcess
    {
        ScopedSetNetworkForProcess scopedSetNetworkForProcess(TEST_NETID);
        expectDnsNetIdEquals(TEST_NETID);
    }

    // Test with setNetworkForProcess with NETID_USE_LOCAL_NAMESERVERS
    {
        ScopedSetNetworkForProcess scopedSetNetworkForProcess(TEST_NETID |
                                                              NETID_USE_LOCAL_NAMESERVERS);
        expectNetIdWithLocalNameserversFlag(TEST_NETID);
    }

    // Test with setNetworkForResolv
    {
        ScopedSetNetworkForResolv scopedSetNetworkForResolv(TEST_NETID);
        expectDnsNetIdEquals(TEST_NETID);
    }

    // Test with setNetworkForResolv with NETID_USE_LOCAL_NAMESERVERS
    {
        ScopedSetNetworkForResolv scopedSetNetworkForResolv(TEST_NETID |
                                                            NETID_USE_LOCAL_NAMESERVERS);
        expectNetIdWithLocalNameserversFlag(TEST_NETID);
    }

    // Test with setNetworkForResolv under bypassable vpn
    {
        ScopedSetNetworkForResolv scopedSetNetworkForResolv(TEST_NETID);
        expectDnsNetIdWithVpn(mDnsClient.netdService(), TEST_VPN_NETID, TEST_NETID);
    }

    // Create socket connected to DnsProxyListener
    int fd = dns_open_proxy();
    EXPECT_TRUE(fd > 0);
    unique_fd ufd(fd);

    // Test command with wrong netId
    sendCommand(fd, "getdnsnetid abc");
    EXPECT_EQ(ResponseCode::DnsProxyQueryResult, readResponseCode(fd));
    EXPECT_EQ(-EINVAL, readBE32(fd));

    // Test unsupported command
    sendCommand(fd, "getdnsnetidNotSupported");
    // Keep in sync with FrameworkListener.cpp (500, "Command not recognized")
    EXPECT_EQ(500, readResponseCode(fd));
}

TEST_F(ResolverTest, BlockDnsQueryWithUidRule) {
    SKIP_IF_BPF_NOT_SUPPORTED;
    constexpr char listen_addr1[] = "127.0.0.4";
    constexpr char listen_addr2[] = "::1";
    constexpr char host_name[] = "howdy.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };
    INetd* netdService = mDnsClient.netdService();

    test::DNSResponder dns1(listen_addr1);
    test::DNSResponder dns2(listen_addr2);
    StartDns(dns1, records);
    StartDns(dns2, records);

    std::vector<std::string> servers = {listen_addr1, listen_addr2};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    dns1.clearQueries();
    dns2.clearQueries();

    ScopeBlockedUIDRule scopeBlockUidRule(netdService, TEST_UID);
    // Dns Query
    int fd1 = resNetworkQuery(TEST_NETID, host_name, ns_c_in, ns_t_a, 0);
    int fd2 = resNetworkQuery(TEST_NETID, host_name, ns_c_in, ns_t_aaaa, 0);
    EXPECT_TRUE(fd1 != -1);
    EXPECT_TRUE(fd2 != -1);

    uint8_t buf1[MAXPACKET] = {};
    uint8_t buf2[MAXPACKET] = {};
    int rcode;
    int res2 = getAsyncResponse(fd2, &rcode, buf2, MAXPACKET);
    int res1 = getAsyncResponse(fd1, &rcode, buf1, MAXPACKET);
    // If API level >= 30 (R+), these queries should be blocked.
    if (isAtLeastR) {
        EXPECT_EQ(res2, -ECONNREFUSED);
        EXPECT_EQ(res1, -ECONNREFUSED);
        ExpectDnsEvent(INetdEventListener::EVENT_RES_NSEND, EAI_SYSTEM, "howdy.example.com", {});
        ExpectDnsEvent(INetdEventListener::EVENT_RES_NSEND, EAI_SYSTEM, "howdy.example.com", {});
    } else {
        EXPECT_GT(res2, 0);
        EXPECT_EQ("::1.2.3.4", toString(buf2, res2, AF_INET6));
        EXPECT_GT(res1, 0);
        EXPECT_EQ("1.2.3.4", toString(buf1, res1, AF_INET));
        // To avoid flaky test, do not evaluate DnsEvent since event order is not guaranteed.
    }
}

TEST_F(ResolverTest, GetAddrinfo_BlockDnsQueryWithUidRule) {
    SKIP_IF_BPF_NOT_SUPPORTED;
    constexpr char listen_addr1[] = "127.0.0.4";
    constexpr char listen_addr2[] = "::1";
    constexpr char host_name[] = "howdy.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };
    test::DNSResponder dns1(listen_addr1);
    test::DNSResponder dns2(listen_addr2);
    StartDns(dns1, records);
    StartDns(dns2, records);

    std::vector<std::string> servers = {listen_addr1, listen_addr2};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));

    const addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_DGRAM};

    static struct {
        const char* hname;
        const int expectedErrorCode;
    } kTestData[] = {
            {host_name, (isAtLeastT() && fs::exists(DNS_HELPER)) ? EAI_FAIL : EAI_NODATA},
            // To test the query with search domain.
            {"howdy", (isAtLeastT() && fs::exists(DNS_HELPER)) ? EAI_FAIL : EAI_AGAIN},
    };

    INetd* netdService = mDnsClient.netdService();
    for (auto& td : kTestData) {
        SCOPED_TRACE(td.hname);
        ScopeBlockedUIDRule scopeBlockUidRule(netdService, TEST_UID);
        // If API level >= 30 (R+), these queries should be blocked.
        if (isAtLeastR) {
            addrinfo* result = nullptr;
            // getaddrinfo() in bionic would convert all errors to EAI_NODATA
            // except EAI_SYSTEM.
            EXPECT_EQ(EAI_NODATA, getaddrinfo(td.hname, nullptr, &hints, &result));
            ExpectDnsEvent(INetdEventListener::EVENT_GETADDRINFO, td.expectedErrorCode, td.hname,
                           {});
        } else {
            ScopedAddrinfo result = safe_getaddrinfo(td.hname, nullptr, &hints);
            EXPECT_NE(nullptr, result);
            EXPECT_THAT(ToStrings(result),
                        testing::UnorderedElementsAreArray({"1.2.3.4", "::1.2.3.4"}));
            // To avoid flaky test, do not evaluate DnsEvent since event order is not guaranteed.
        }
    }
}

TEST_F(ResolverTest, EnforceDnsUid) {
    SKIP_IF_BPF_NOT_SUPPORTED;
    constexpr char listen_addr1[] = "127.0.0.4";
    constexpr char listen_addr2[] = "::1";
    constexpr char host_name[] = "howdy.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };
    INetd* netdService = mDnsClient.netdService();

    test::DNSResponder dns1(listen_addr1);
    test::DNSResponder dns2(listen_addr2);
    StartDns(dns1, records);
    StartDns(dns2, records);

    // switch uid of DNS queries from applications to AID_DNS
    ResolverParamsParcel parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    parcel.servers = {listen_addr1, listen_addr2};
    ASSERT_TRUE(mDnsClient.resolvService()->setResolverConfiguration(parcel).isOk());

    uint8_t buf[MAXPACKET] = {};
    uint8_t buf2[MAXPACKET] = {};
    int rcode;
    {
        ScopeBlockedUIDRule scopeBlockUidRule(netdService, TEST_UID);
        // Dns Queries should be blocked
        const int fd1 = resNetworkQuery(TEST_NETID, host_name, ns_c_in, ns_t_a, 0);
        const int fd2 = resNetworkQuery(TEST_NETID, host_name, ns_c_in, ns_t_aaaa, 0);
        EXPECT_TRUE(fd1 != -1);
        EXPECT_TRUE(fd2 != -1);

        const int res2 = getAsyncResponse(fd2, &rcode, buf2, MAXPACKET);
        const int res1 = getAsyncResponse(fd1, &rcode, buf, MAXPACKET);
        // If API level >= 30 (R+), the query should be blocked.
        if (isAtLeastR) {
            EXPECT_EQ(res2, -ECONNREFUSED);
            EXPECT_EQ(res1, -ECONNREFUSED);
        } else {
            EXPECT_GT(res2, 0);
            EXPECT_EQ("::1.2.3.4", toString(buf2, res2, AF_INET6));
            EXPECT_GT(res1, 0);
            EXPECT_EQ("1.2.3.4", toString(buf, res1, AF_INET));
        }
    }

    memset(buf, 0, MAXPACKET);
    ResolverOptionsParcel resolverOptions;
    resolverOptions.enforceDnsUid = true;
    if (!mIsResolverOptionIPCSupported) {
        parcel.resolverOptions = resolverOptions;
        ASSERT_TRUE(mDnsClient.resolvService()->setResolverConfiguration(parcel).isOk());
    } else {
        ASSERT_TRUE(mDnsClient.resolvService()
                            ->setResolverOptions(parcel.netId, resolverOptions)
                            .isOk());
    }

    {
        ScopeBlockedUIDRule scopeBlockUidRule(netdService, TEST_UID);
        // Dns Queries should NOT be blocked
        int fd1 = resNetworkQuery(TEST_NETID, host_name, ns_c_in, ns_t_a, 0);
        int fd2 = resNetworkQuery(TEST_NETID, host_name, ns_c_in, ns_t_aaaa, 0);
        EXPECT_TRUE(fd1 != -1);
        EXPECT_TRUE(fd2 != -1);

        int res = getAsyncResponse(fd2, &rcode, buf, MAXPACKET);
        EXPECT_EQ("::1.2.3.4", toString(buf, res, AF_INET6));

        memset(buf, 0, MAXPACKET);
        res = getAsyncResponse(fd1, &rcode, buf, MAXPACKET);
        EXPECT_EQ("1.2.3.4", toString(buf, res, AF_INET));

        // @TODO: So far we know that uid of DNS queries are no more set to DNS requester. But we
        // don't check if they are actually being set to AID_DNS, because system uids are always
        // allowed in bpf_owner_match(). Audit by firewallSetUidRule(AID_DNS) + sending queries is
        // infeasible. Fix it if the behavior of bpf_owner_match() is changed in the future, or if
        // we have better idea to deal with this.
    }
}

TEST_F(ResolverTest, ConnectTlsServerTimeout) {
    constexpr char hostname1[] = "query1.example.com.";
    constexpr char hostname2[] = "query2.example.com.";
    const std::vector<DnsRecord> records = {
            {hostname1, ns_type::ns_t_a, "1.2.3.4"},
            {hostname2, ns_type::ns_t_a, "1.2.3.5"},
    };

    // TODO: Remove it after b/254186357 is clarified.
    ASSERT_TRUE(mDnsClient.resolvService()
                        ->setLogSeverity(aidl::android::net::IDnsResolver::DNS_RESOLVER_LOG_VERBOSE)
                        .isOk());

    static const struct TestConfig {
        bool asyncHandshake;
        int maxRetries;

        // if asyncHandshake:
        //   expectedTimeout = Min(DotQueryTimeoutMs, dotConnectTimeoutMs * maxRetries)
        // otherwise:
        //   expectedTimeout = dotConnectTimeoutMs
        int expectedTimeout;
    } testConfigs[] = {
            // Test mis-configured dot_maxtries flag.
            {false, 0, 1000}, {true, 0, 1000},

            {false, 1, 1000}, {false, 3, 1000}, {true, 1, 1000}, {true, 3, 3000},
    };

    for (const auto& config : testConfigs) {
        SCOPED_TRACE(fmt::format("testConfig: [{}, {}]", config.asyncHandshake, config.maxRetries));

        // Because a DnsTlsTransport lasts at least 5 minutes in spite of network
        // destroyed, let the resolver creates an unique DnsTlsTransport every time
        // so that the DnsTlsTransport won't interfere the other tests.
        const std::string addr = getUniqueIPv4Address();
        test::DNSResponder dns(addr);
        StartDns(dns, records);
        test::DnsTlsFrontend tls(addr, "853", addr, "53");
        ASSERT_TRUE(tls.startServer());

        // The resolver will adjust the timeout value to 1000ms since the value is too small.
        ScopedSystemProperties sp1(kDotConnectTimeoutMsFlag, "100");

        // Infinite timeout.
        ScopedSystemProperties sp2(kDotQueryTimeoutMsFlag, "-1");

        ScopedSystemProperties sp3(kDotAsyncHandshakeFlag, config.asyncHandshake ? "1" : "0");
        ScopedSystemProperties sp4(kDotMaxretriesFlag, std::to_string(config.maxRetries));

        // Don't skip unusable DoT servers and disable revalidation for this test.
        ScopedSystemProperties sp5(kDotXportUnusableThresholdFlag, "-1");
        ScopedSystemProperties sp6(kDotRevalidationThresholdFlag, "-1");
        resetNetwork();

        // Set up resolver to opportunistic mode.
        auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
        parcel.servers = {addr};
        parcel.tlsServers = {addr};
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
        EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));
        EXPECT_TRUE(tls.waitForQueries(1));
        tls.clearQueries();
        dns.clearQueries();

        // The server becomes unresponsive to the handshake request.
        tls.setHangOnHandshakeForTesting(true);

        // Expect the things happening in getaddrinfo():
        //   1. Connect to the private DNS server.
        //   2. SSL handshake times out.
        //   3. Fallback to UDP transport, and then get the answer.
        const addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
        auto [result, timeTakenMs] = safe_getaddrinfo_time_taken(hostname1, nullptr, hints);

        EXPECT_NE(nullptr, result);
        EXPECT_EQ(0, tls.queries());
        EXPECT_EQ(1U, GetNumQueries(dns, hostname1));
        EXPECT_EQ(records.at(0).addr, ToString(result));

        // A loose upper bound is set by adding 1000ms buffer time. Theoretically, getaddrinfo()
        // should just take a bit more than expetTimeout milliseconds.
        EXPECT_GE(timeTakenMs, config.expectedTimeout);
        EXPECT_LE(timeTakenMs, config.expectedTimeout + 1000);

        // Set the server to be responsive. Verify that the resolver will attempt to reconnect
        // to the server and then get the result within the timeout.
        tls.setHangOnHandshakeForTesting(false);
        std::tie(result, timeTakenMs) = safe_getaddrinfo_time_taken(hostname2, nullptr, hints);

        EXPECT_NE(nullptr, result);
        EXPECT_TRUE(tls.waitForQueries(1));
        EXPECT_EQ(1U, GetNumQueries(dns, hostname2));
        EXPECT_EQ(records.at(1).addr, ToString(result));

        EXPECT_LE(timeTakenMs, 1000);
    }

    // TODO: Remove it after b/254186357 is clarified.
    ASSERT_TRUE(mDnsClient.resolvService()
                        ->setLogSeverity(aidl::android::net::IDnsResolver::DNS_RESOLVER_LOG_INFO)
                        .isOk());
}

TEST_F(ResolverTest, ConnectTlsServerTimeout_ConcurrentQueries) {
    constexpr uint32_t cacheFlag = ANDROID_RESOLV_NO_CACHE_LOOKUP;
    constexpr char hostname[] = "hello.example.com.";
    const std::vector<DnsRecord> records = {
            {hostname, ns_type::ns_t_a, "1.2.3.4"},
    };
    int testConfigCount = 0;

    static const struct TestConfig {
        bool asyncHandshake;
        int dotConnectTimeoutMs;
        int dotQueryTimeoutMs;
        int maxRetries;
        int concurrency;

        // if asyncHandshake:
        //   expectedTimeout = Min(DotQueryTimeoutMs, dotConnectTimeoutMs * maxRetries)
        // otherwise:
        //   expectedTimeout = dotConnectTimeoutMs * concurrency
        int expectedTimeout;
    } testConfigs[] = {
            // clang-format off
            {false, 1000, 3000, 1, 5,  5000},
            {false, 1000, 3000, 3, 5,  5000},
            {false, 2000, 1500, 3, 2,  4000},
            {true,  1000, 3000, 1, 5,  1000},
            {true,  2500, 1500, 1, 10, 1500},
            {true,  1000, 5000, 3, 5,  3000},
            // clang-format on
    };

    // Launch query threads. Expected behaviors are:
    // - when dot_async_handshake is disabled, one of the query threads triggers a
    //   handshake and then times out. Then same as another query thread, and so forth.
    // - when dot_async_handshake is enabled, only one handshake is triggered, and then
    //   all of the query threads time out at the same time.
    for (const auto& config : testConfigs) {
        testConfigCount++;
        ScopedSystemProperties sp1(kDotQueryTimeoutMsFlag,
                                   std::to_string(config.dotQueryTimeoutMs));
        ScopedSystemProperties sp2(kDotConnectTimeoutMsFlag,
                                   std::to_string(config.dotConnectTimeoutMs));
        ScopedSystemProperties sp3(kDotAsyncHandshakeFlag, config.asyncHandshake ? "1" : "0");
        ScopedSystemProperties sp4(kDotMaxretriesFlag, std::to_string(config.maxRetries));

        // Don't skip unusable DoT servers and disable revalidation for this test.
        ScopedSystemProperties sp5(kDotXportUnusableThresholdFlag, "-1");
        ScopedSystemProperties sp6(kDotRevalidationThresholdFlag, "-1");
        resetNetwork();

        for (const std::string_view dnsMode : {"OPPORTUNISTIC", "STRICT"}) {
            SCOPED_TRACE(fmt::format("testConfig: [{}, {}]", testConfigCount, dnsMode));

            // Because a DnsTlsTransport lasts at least 5 minutes in spite of network
            // destroyed, let the resolver creates an unique DnsTlsTransport every time
            // so that the DnsTlsTransport won't interfere the other tests.
            const std::string addr = getUniqueIPv4Address();
            test::DNSResponder dns(addr);
            StartDns(dns, records);
            test::DnsTlsFrontend tls(addr, "853", addr, "53");
            ASSERT_TRUE(tls.startServer());

            auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
            parcel.servers = {addr};
            parcel.tlsServers = {addr};
            if (dnsMode == "STRICT") parcel.tlsName = kDefaultPrivateDnsHostName;
            ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
            EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));
            EXPECT_TRUE(tls.waitForQueries(1));

            // The server becomes unresponsive to the handshake request.
            tls.setHangOnHandshakeForTesting(true);

            Stopwatch s;
            std::vector<std::thread> threads(config.concurrency);
            for (std::thread& thread : threads) {
                thread = std::thread([&]() {
                    int fd = resNetworkQuery(TEST_NETID, hostname, ns_c_in, ns_t_a, cacheFlag);
                    dnsMode == "STRICT" ? expectAnswersNotValid(fd, -ETIMEDOUT)
                                        : expectAnswersValid(fd, AF_INET, "1.2.3.4");
                });
            }
            for (std::thread& thread : threads) {
                thread.join();
            }

            const int timeTakenMs = s.timeTakenUs() / 1000;
            // A loose upper bound is set by adding 1000ms buffer time. Theoretically, it should
            // just take a bit more than expetTimeout milliseconds for the result.
            EXPECT_GE(timeTakenMs, config.expectedTimeout);
            EXPECT_LE(timeTakenMs, config.expectedTimeout + 1000);

            // Recover the server from being unresponsive and try again.
            tls.setHangOnHandshakeForTesting(false);
            int fd = resNetworkQuery(TEST_NETID, hostname, ns_c_in, ns_t_a, cacheFlag);
            if (dnsMode == "STRICT" && config.asyncHandshake &&
                config.dotQueryTimeoutMs < (config.dotConnectTimeoutMs * config.maxRetries)) {
                // In this case, the connection handshake is supposed to be in progress. Queries
                // sent before the handshake finishes will time out (either due to connect timeout
                // or query timeout).
                expectAnswersNotValid(fd, -ETIMEDOUT);
            } else {
                expectAnswersValid(fd, AF_INET, "1.2.3.4");
            }
        }
    }
}

// Tests that the DoT query timeout is configurable via the feature flag "dot_query_timeout_ms".
// The test DoT server is configured to postpone DNS queries for DOT_SERVER_UNRESPONSIVE_TIME_MS
// (2s). If the feature flag is set to a positive value smaller than
// DOT_SERVER_UNRESPONSIVE_TIME_MS, DoT queries should timeout.
TEST_F(ResolverTest, QueryTlsServerTimeout) {
    constexpr int DOT_SERVER_UNRESPONSIVE_TIME_MS = 2000;
    constexpr int TIMING_TOLERANCE_MS = 500;
    constexpr char hostname1[] = "query1.example.com.";
    const std::vector<DnsRecord> records = {
            {hostname1, ns_type::ns_t_a, "1.2.3.4"},
    };

    static const struct TestConfig {
        std::string dnsMode;
        int queryTimeoutMs;
        int expectResultTimedOut;
        int expectedTimeTakenMs;
    } testConfigs[] = {
            // clang-format off
            {"OPPORTUNISTIC",   -1, false, DOT_SERVER_UNRESPONSIVE_TIME_MS},
            {"OPPORTUNISTIC", 1000, false,                            1000},
            {"STRICT",          -1, false, DOT_SERVER_UNRESPONSIVE_TIME_MS},
            // `expectResultTimedOut` is true in the following testcase because in strict mode
            // DnsResolver doesn't try Do53 servers after the DoT query is timed out.
            {"STRICT",        1000,  true,                            1000},
            // clang-format on
    };
    for (const auto& config : testConfigs) {
        SCOPED_TRACE(fmt::format("testConfig: [{}] [{}]", config.dnsMode, config.queryTimeoutMs));

        const std::string addr = getUniqueIPv4Address();
        test::DNSResponder dns(addr);
        StartDns(dns, records);
        test::DnsTlsFrontend tls(addr, "853", addr, "53");
        ASSERT_TRUE(tls.startServer());

        ScopedSystemProperties sp(kDotQueryTimeoutMsFlag, std::to_string(config.queryTimeoutMs));

        // Don't skip unusable DoT servers and disable revalidation for this test.
        ScopedSystemProperties sp2(kDotXportUnusableThresholdFlag, "-1");
        ScopedSystemProperties sp3(kDotRevalidationThresholdFlag, "-1");
        resetNetwork();

        auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
        parcel.servers = {addr};
        parcel.tlsServers = {addr};
        if (config.dnsMode == "STRICT") parcel.tlsName = kDefaultPrivateDnsHostName;

        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
        EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));
        EXPECT_TRUE(tls.waitForQueries(1));
        tls.clearQueries();

        // Set the DoT server to be unresponsive to DNS queries for
        // `DOT_SERVER_UNRESPONSIVE_TIME_MS` ms.
        tls.setDelayQueries(999);
        tls.setDelayQueriesTimeout(DOT_SERVER_UNRESPONSIVE_TIME_MS);

        // Send a DNS query, and then check the result and the response time.
        Stopwatch s;
        int fd = resNetworkQuery(TEST_NETID, hostname1, ns_c_in, ns_t_a,
                                 ANDROID_RESOLV_NO_CACHE_LOOKUP);
        if (config.expectResultTimedOut) {
            expectAnswersNotValid(fd, -ETIMEDOUT);
        } else {
            expectAnswersValid(fd, AF_INET, "1.2.3.4");
        }
        const int timeTakenMs = s.getTimeAndResetUs() / 1000;
        EXPECT_NEAR(config.expectedTimeTakenMs, timeTakenMs, TIMING_TOLERANCE_MS);
        EXPECT_TRUE(tls.waitForQueries(1));
    }
}

// Tests that the DnsResolver can skip using unusable DoT servers if dot_xport_unusable_threshold
// flag is set. In this test, we make test DoT servers unresponsive during connection handshake,
// so the DnsResolver will skip using a DoT server if the number of timed out queries reaches
// the threshold.
TEST_F(ResolverTest, SkipUnusableTlsServer) {
    constexpr int DOT_CONNECT_TIMEOUT_MS = 1000;

    static const struct TestConfig {
        int dotXportUnusableThreshold;
        int queries;
        int expectedQueriesSentToDot1;
        int expectedQueriesSentToDot2;
    } testConfigs[] = {
            // clang-format off
            // expectedQueriesSentToDot2 is 0 because dot_quick_fallback flag is set.
            {-1,  3, 3, 0},
            { 1,  3, 1, 1},
            { 3, 10, 3, 3},
            // clang-format on
    };

    for (const auto& config : testConfigs) {
        SCOPED_TRACE(fmt::format("testConfig: [{}, {}, {}, {}]", config.dotXportUnusableThreshold,
                                 config.queries, config.expectedQueriesSentToDot1,
                                 config.expectedQueriesSentToDot2));

        const std::string addr1 = getUniqueIPv4Address();
        const std::string addr2 = getUniqueIPv4Address();
        test::DNSResponder dns1(addr1);
        test::DNSResponder dns2(addr2);
        test::DnsTlsFrontend dot1(addr1, "853", addr1, "53");
        test::DnsTlsFrontend dot2(addr2, "853", addr2, "53");
        dns1.addMapping(kHelloExampleCom, ns_type::ns_t_aaaa, kHelloExampleComAddrV6);
        dns2.addMapping(kHelloExampleCom, ns_type::ns_t_aaaa, kHelloExampleComAddrV6);
        ASSERT_TRUE(dns1.startServer());
        ASSERT_TRUE(dns2.startServer());
        ASSERT_TRUE(dot1.startServer());
        ASSERT_TRUE(dot2.startServer());

        ScopedSystemProperties sp1(kDotConnectTimeoutMsFlag,
                                   std::to_string(DOT_CONNECT_TIMEOUT_MS));
        ScopedSystemProperties sp2(kDotXportUnusableThresholdFlag,
                                   std::to_string(config.dotXportUnusableThreshold));
        ScopedSystemProperties sp3(kDotQuickFallbackFlag, "1");
        ScopedSystemProperties sp4(kDotRevalidationThresholdFlag, "-1");
        resetNetwork();

        // Private DNS opportunistic mode.
        auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
        parcel.servers = {addr1, addr2};
        parcel.tlsServers = {addr1, addr2};
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

        EXPECT_TRUE(WaitForPrivateDnsValidation(dot1.listen_address(), true));
        EXPECT_TRUE(WaitForPrivateDnsValidation(dot2.listen_address(), true));
        EXPECT_TRUE(dot1.waitForQueries(1));
        EXPECT_TRUE(dot2.waitForQueries(1));
        dot1.clearQueries();
        dot2.clearQueries();
        dot1.clearConnectionsCount();
        dot2.clearConnectionsCount();

        // Set the DoT servers as unresponsive to connection handshake.
        dot1.setHangOnHandshakeForTesting(true);
        dot2.setHangOnHandshakeForTesting(true);

        // Send sequential queries
        for (int i = 0; i < config.queries; i++) {
            int fd = resNetworkQuery(TEST_NETID, kHelloExampleCom, ns_c_in, ns_t_aaaa,
                                     ANDROID_RESOLV_NO_CACHE_LOOKUP);
            expectAnswersValid(fd, AF_INET6, kHelloExampleComAddrV6);
        }

        if (GetProperty(kDotAsyncHandshakeFlag, "0") == "0") {
            EXPECT_EQ(dot1.acceptConnectionsCount(), config.expectedQueriesSentToDot1);
            EXPECT_EQ(dot2.acceptConnectionsCount(), config.expectedQueriesSentToDot2);
        } else {
            // If the flag dot_async_handshake is set to 1, the DnsResolver will try
            // DoT connection establishment at most |retries| times.
            const int retries = std::stoi(GetProperty(kDotMaxretriesFlag, "3"));
            EXPECT_EQ(dot1.acceptConnectionsCount(), config.expectedQueriesSentToDot1 * retries);
            EXPECT_EQ(dot2.acceptConnectionsCount(), config.expectedQueriesSentToDot2 * retries);
        }
    }
}

// Verifies that the DnsResolver re-validates the DoT server when several DNS queries to
// the server fails in a row.
TEST_F(ResolverTest, TlsServerRevalidation) {
    constexpr uint32_t cacheFlag = ANDROID_RESOLV_NO_CACHE_LOOKUP;
    constexpr int dotXportUnusableThreshold = 10;
    constexpr int dotQueryTimeoutMs = 1000;
    constexpr char hostname[] = "hello.example.com.";
    const std::vector<DnsRecord> records = {
            {hostname, ns_type::ns_t_a, "1.2.3.4"},
    };

    static const struct TestConfig {
        std::string dnsMode;
        int validationThreshold;
        int queries;

        // Expected behavior in the DnsResolver.
        bool expectRevalidationHappen;
        bool expectDotUnusable;
    } testConfigs[] = {
            // clang-format off
            {"OPPORTUNISTIC", -1,  5, false, false},
            {"OPPORTUNISTIC", -1, 10, false,  true},
            {"OPPORTUNISTIC",  5,  5,  true, false},
            {"OPPORTUNISTIC",  5, 10,  true,  true},
            {"STRICT",        -1,  5, false, false},
            {"STRICT",        -1, 10, false, false},
            {"STRICT",         5,  5, false, false},
            {"STRICT",         5, 10, false, false},
            // clang-format on
    };

    for (const auto& config : testConfigs) {
        SCOPED_TRACE(fmt::format("testConfig: [{}, {}, {}]", config.dnsMode,
                                 config.validationThreshold, config.queries));
        const int queries = config.queries;
        const int delayQueriesTimeout = dotQueryTimeoutMs + 1000;

        ScopedSystemProperties sp1(kDotRevalidationThresholdFlag,
                                   std::to_string(config.validationThreshold));
        ScopedSystemProperties sp2(kDotXportUnusableThresholdFlag,
                                   std::to_string(dotXportUnusableThreshold));
        ScopedSystemProperties sp3(kDotQueryTimeoutMsFlag, std::to_string(dotQueryTimeoutMs));
        resetNetwork();

        // This test is sensitive to the number of queries sent in DoT validation.
        int latencyFactor;
        int latencyOffsetMs;
        if (isAtLeastR) {
            // The feature is enabled by default in R.
            latencyFactor = std::stoi(GetProperty(kDotValidationLatencyFactorFlag, "3"));
            latencyOffsetMs = std::stoi(GetProperty(kDotValidationLatencyOffsetMsFlag, "100"));
        } else {
            // The feature is disabled by default in Q.
            latencyFactor = std::stoi(GetProperty(kDotValidationLatencyFactorFlag, "-1"));
            latencyOffsetMs = std::stoi(GetProperty(kDotValidationLatencyOffsetMsFlag, "-1"));
        }
        const bool dotValidationExtraProbes = (config.dnsMode == "OPPORTUNISTIC") &&
                                              (latencyFactor >= 0 && latencyOffsetMs >= 0 &&
                                               latencyFactor + latencyOffsetMs != 0);

        const std::string addr = getUniqueIPv4Address();
        test::DNSResponder dns(addr);
        StartDns(dns, records);
        test::DnsTlsFrontend tls(addr, "853", addr, "53");
        ASSERT_TRUE(tls.startServer());

        auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
        parcel.servers = {addr};
        parcel.tlsServers = {addr};
        if (config.dnsMode == "STRICT") parcel.tlsName = kDefaultPrivateDnsHostName;
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
        EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));
        if (dotValidationExtraProbes) {
            EXPECT_TRUE(tls.waitForQueries(2));
        } else {
            EXPECT_TRUE(tls.waitForQueries(1));
        }
        tls.clearQueries();
        dns.clearQueries();

        // Expect the things happening in order:
        // 1. Configure the DoT server to postpone |queries + 1| DNS queries.
        // 2. Send |queries| DNS queries, they will time out in 1 second.
        // 3. 1 second later, the DoT server still waits for one more DNS query until
        //    |delayQueriesTimeout| times out.
        // 4. (opportunistic mode only) Meanwhile, DoT revalidation happens. The DnsResolver
        //    creates a new connection and sends a query to the DoT server.
        // 5. 1 second later, |delayQueriesTimeout| times out. The DoT server flushes all of the
        //    postponed DNS queries, and handles the query which comes from the revalidation.
        // 6. (opportunistic mode only) The revalidation succeeds.
        // 7. Send another DNS query, and expect it will succeed.
        // 8. (opportunistic mode only) If the DoT server has been deemed as unusable, the
        //    DnsResolver skips trying the DoT server.

        // Step 1.
        tls.setDelayQueries(queries + 1);
        tls.setDelayQueriesTimeout(delayQueriesTimeout);

        // Step 2.
        std::vector<std::thread> threads1(queries);
        for (std::thread& thread : threads1) {
            thread = std::thread([&]() {
                int fd = resNetworkQuery(TEST_NETID, hostname, ns_c_in, ns_t_a, cacheFlag);
                config.dnsMode == "STRICT" ? expectAnswersNotValid(fd, -ETIMEDOUT)
                                           : expectAnswersValid(fd, AF_INET, "1.2.3.4");
            });
        }

        // Step 3 and 4.
        for (std::thread& thread : threads1) {
            thread.join();
        }

        // Recover the config to make the revalidation can succeed.
        tls.setDelayQueries(1);

        // Step 5 and 6.
        int expectedDotQueries = queries;
        int extraDnsProbe = 0;
        if (config.expectRevalidationHappen) {
            EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));
            expectedDotQueries++;

            if (dotValidationExtraProbes) {
                expectedDotQueries++;
                extraDnsProbe = 1;
            }
        }

        // Step 7 and 8.
        int fd = resNetworkQuery(TEST_NETID, hostname, ns_c_in, ns_t_a, cacheFlag);
        expectAnswersValid(fd, AF_INET, "1.2.3.4");
        expectedDotQueries++;

        const int expectedDo53Queries =
                expectedDotQueries +
                (config.dnsMode == "OPPORTUNISTIC" ? (queries + extraDnsProbe) : 0);

        if (config.expectDotUnusable) {
            // A DoT server can be deemed as unusable only in opportunistic mode. When it happens,
            // the DnsResolver doesn't use the DoT server for a certain period of time.
            expectedDotQueries--;
        }

        // This code makes the test more robust to race condition.
        EXPECT_TRUE(tls.waitForQueries(expectedDotQueries));

        EXPECT_EQ(dns.queries().size(), static_cast<unsigned>(expectedDo53Queries));
        EXPECT_EQ(tls.queries(), expectedDotQueries);
    }
}

// Verifies that private DNS validation fails if DoT server is much slower than cleartext server.
TEST_F(ResolverTest, TlsServerValidation_UdpProbe) {
    constexpr char backend_addr[] = "127.0.0.3";
    test::DNSResponder backend(backend_addr);
    backend.setResponseDelayMs(200);
    ASSERT_TRUE(backend.startServer());

    static const struct TestConfig {
        int latencyFactor;
        int latencyOffsetMs;
        bool udpProbeLost;
        size_t expectedUdpProbes;
        bool expectedValidationPass;
    } testConfigs[] = {
            // clang-format off
            {-1, -1,  false, 0, true},
            {0,  0,   false, 0, true},
            {1,  10,  false, 1, false},
            {1,  10,  true,  2, false},
            {5,  300, false, 1, true},
            {5,  300, true,  2, true},
            // clang-format on
    };

    for (const auto& config : testConfigs) {
        SCOPED_TRACE(fmt::format("testConfig: [{}, {}, {}]", config.latencyFactor,
                                 config.latencyOffsetMs, config.udpProbeLost));

        const std::string addr = getUniqueIPv4Address();
        test::DNSResponder dns(addr, "53", static_cast<ns_rcode>(-1));
        test::DnsTlsFrontend tls(addr, "853", backend_addr, "53");
        dns.setResponseDelayMs(10);
        ASSERT_TRUE(dns.startServer());
        ASSERT_TRUE(tls.startServer());

        ScopedSystemProperties sp1(kDotValidationLatencyFactorFlag,
                                   std::to_string(config.latencyFactor));
        ScopedSystemProperties sp2(kDotValidationLatencyOffsetMsFlag,
                                   std::to_string(config.latencyOffsetMs));
        resetNetwork();

        std::unique_ptr<std::thread> thread;
        if (config.udpProbeLost) {
            thread.reset(new std::thread([&dns]() {
                // Simulate that the first UDP probe is lost and the second UDP probe succeeds.
                dns.setResponseProbability(0.0);
                std::this_thread::sleep_for(std::chrono::seconds(2));
                dns.setResponseProbability(1.0);
            }));
        }

        // Set up opportunistic mode, and wait for the validation complete.
        auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
        parcel.servers = {addr};
        parcel.tlsServers = {addr};
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

        // The timeout of WaitForPrivateDnsValidation is 5 seconds which is still enough for
        // the testcase of UDP probe lost because the retry of UDP probe happens after 3 seconds.
        EXPECT_TRUE(
                WaitForPrivateDnsValidation(tls.listen_address(), config.expectedValidationPass));
        EXPECT_EQ(dns.queries().size(), config.expectedUdpProbes);
        dns.clearQueries();

        // Test that Private DNS validation always pass in strict mode.
        parcel.tlsName = kDefaultPrivateDnsHostName;
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
        EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));
        EXPECT_EQ(dns.queries().size(), 0U);

        if (thread) {
            thread->join();
            thread.reset();
        }
    }
}

// Verifies that DNS queries can quick fall back to UDP if the first DoT server is unresponsive.
TEST_F(ResolverTest, DotQuickFallback) {
    constexpr int DOT_CONNECT_TIMEOUT_MS = 1000;
    const std::string addr1 = getUniqueIPv4Address();
    const std::string addr2 = getUniqueIPv4Address();
    test::DNSResponder dns1(addr1);
    test::DNSResponder dns2(addr2);
    test::DnsTlsFrontend dot1(addr1, "853", addr1, "53");
    test::DnsTlsFrontend dot2(addr2, "853", addr2, "53");

    dns1.addMapping(kHelloExampleCom, ns_type::ns_t_aaaa, kHelloExampleComAddrV6);
    dns2.addMapping(kHelloExampleCom, ns_type::ns_t_aaaa, kHelloExampleComAddrV6);
    ASSERT_TRUE(dns1.startServer());
    ASSERT_TRUE(dns2.startServer());
    ASSERT_TRUE(dot1.startServer());
    ASSERT_TRUE(dot2.startServer());

    static const struct TestConfig {
        std::string privateDnsMode;
        int dotQuickFallbackFlag;
    } testConfigs[] = {
            // clang-format off
            {"OPPORTUNISTIC", 0},
            {"OPPORTUNISTIC", 1},
            {"STRICT",        0},
            {"STRICT",        1},
            // clang-format on
    };

    for (const auto& config : testConfigs) {
        SCOPED_TRACE(fmt::format("testConfig: [{}, {}]", config.privateDnsMode,
                                 config.dotQuickFallbackFlag));

        const bool canQuickFallback =
                (config.dotQuickFallbackFlag == 1) && (config.privateDnsMode == "OPPORTUNISTIC");
        ScopedSystemProperties sp1(kDotConnectTimeoutMsFlag,
                                   std::to_string(DOT_CONNECT_TIMEOUT_MS));
        ScopedSystemProperties sp2(kDotQuickFallbackFlag,
                                   std::to_string(config.dotQuickFallbackFlag));

        // Disable revalidation because we are reusing the same IP address of DoT servers.
        ScopedSystemProperties sp3(kDotRevalidationThresholdFlag, "-1");

        // TODO: Remove the flags and fix the test.
        ScopedSystemProperties sp4(kDotAsyncHandshakeFlag, "0");
        ScopedSystemProperties sp5(kDotMaxretriesFlag, "3");
        resetNetwork();

        resetNetwork();

        auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
        parcel.servers = {addr1, addr2};
        parcel.tlsServers = {addr1, addr2};
        parcel.tlsName = (config.privateDnsMode == "STRICT") ? kDefaultPrivateDnsHostName : "";
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

        EXPECT_TRUE(WaitForPrivateDnsValidation(dot1.listen_address(), true));
        EXPECT_TRUE(WaitForPrivateDnsValidation(dot2.listen_address(), true));
        EXPECT_TRUE(dot1.waitForQueries(1));
        EXPECT_TRUE(dot2.waitForQueries(1));
        dot1.clearQueries();
        dot2.clearQueries();
        dot1.clearConnectionsCount();
        dot2.clearConnectionsCount();

        // Set the DoT server unresponsive to connection handshake.
        dot1.setHangOnHandshakeForTesting(true);

        int fd = resNetworkQuery(TEST_NETID, kHelloExampleCom, ns_c_in, ns_t_aaaa,
                                 ANDROID_RESOLV_NO_CACHE_LOOKUP);
        expectAnswersValid(fd, AF_INET6, kHelloExampleComAddrV6);

        EXPECT_EQ(dot1.acceptConnectionsCount(), 1);
        EXPECT_EQ(dot2.acceptConnectionsCount(), canQuickFallback ? 0 : 1);
        EXPECT_TRUE(dot2.waitForQueries(canQuickFallback ? 0 : 1));

        dot1.setHangOnHandshakeForTesting(false);
    }
}

TEST_F(ResolverTest, FlushNetworkCache) {
    SKIP_IF_REMOTE_VERSION_LESS_THAN(mDnsClient.resolvService(), 4);
    test::DNSResponder dns;
    StartDns(dns, {{kHelloExampleCom, ns_type::ns_t_a, kHelloExampleComAddrV4}});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    const hostent* result = gethostbyname("hello");
    EXPECT_EQ(1U, GetNumQueriesForType(dns, ns_type::ns_t_a, kHelloExampleCom));
    std::function<bool()> HasTheExpectedResult = [result]() -> bool {
        if (result == nullptr) return false;
        EXPECT_EQ(4, result->h_length);
        if (result->h_addr_list[0] == nullptr) return false;
        EXPECT_EQ(kHelloExampleComAddrV4, ToString(result));
        EXPECT_TRUE(result->h_addr_list[1] == nullptr);
        return true;
    };
    ASSERT_TRUE(HasTheExpectedResult());

    // get result from cache
    result = gethostbyname("hello");
    EXPECT_EQ(1U, GetNumQueriesForType(dns, ns_type::ns_t_a, kHelloExampleCom));
    ASSERT_TRUE(HasTheExpectedResult());

    EXPECT_TRUE(mDnsClient.resolvService()->flushNetworkCache(TEST_NETID).isOk());

    result = gethostbyname("hello");
    EXPECT_EQ(2U, GetNumQueriesForType(dns, ns_type::ns_t_a, kHelloExampleCom));
    ASSERT_TRUE(HasTheExpectedResult());
}

TEST_F(ResolverTest, FlushNetworkCache_random) {
    SKIP_IF_REMOTE_VERSION_LESS_THAN(mDnsClient.resolvService(), 4);
    constexpr int num_flush = 10;
    constexpr int num_queries = 20;
    test::DNSResponder dns;
    StartDns(dns, {{kHelloExampleCom, ns_type::ns_t_a, kHelloExampleComAddrV4}});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());
    const addrinfo hints = {.ai_family = AF_INET};

    std::thread t([this]() {
        for (int i = 0; i < num_flush; ++i) {
            unsigned delay = arc4random_uniform(10 * 1000);  // 10ms
            usleep(delay);
            EXPECT_TRUE(mDnsClient.resolvService()->flushNetworkCache(TEST_NETID).isOk());
        }
    });

    for (int i = 0; i < num_queries; ++i) {
        ScopedAddrinfo result = safe_getaddrinfo("hello", nullptr, &hints);
        EXPECT_TRUE(result != nullptr);
        EXPECT_EQ(kHelloExampleComAddrV4, ToString(result));
    }
    t.join();
}

// flush cache while one query is wait-for-response, another is pending.
TEST_F(ResolverTest, FlushNetworkCache_concurrent) {
    SKIP_IF_REMOTE_VERSION_LESS_THAN(mDnsClient.resolvService(), 4);
    const char* listen_addr1 = "127.0.0.9";
    const char* listen_addr2 = "127.0.0.10";
    test::DNSResponder dns1(listen_addr1);
    test::DNSResponder dns2(listen_addr2);
    StartDns(dns1, {{kHelloExampleCom, ns_type::ns_t_a, kHelloExampleComAddrV4}});
    StartDns(dns2, {{kHelloExampleCom, ns_type::ns_t_a, kHelloExampleComAddrV4}});
    addrinfo hints = {.ai_family = AF_INET};

    // step 1: set server#1 into deferred responding mode
    dns1.setDeferredResp(true);
    std::thread t1([&listen_addr1, &hints, this]() {
        ASSERT_TRUE(mDnsClient.SetResolversForNetwork({listen_addr1}));
        // step 3: query
        ScopedAddrinfo result = safe_getaddrinfo("hello", nullptr, &hints);
        // step 9: check result
        EXPECT_TRUE(result != nullptr);
        EXPECT_EQ(kHelloExampleComAddrV4, ToString(result));
    });

    // step 2: wait for the query to reach the server
    while (GetNumQueries(dns1, kHelloExampleCom) == 0) {
        usleep(1000);  // 1ms
    }

    std::thread t2([&listen_addr2, &hints, &dns2, this]() {
        ASSERT_TRUE(mDnsClient.SetResolversForNetwork({listen_addr2}));
        // step 5: query (should be blocked in resolver)
        ScopedAddrinfo result = safe_getaddrinfo("hello", nullptr, &hints);
        // step 7: check result
        EXPECT_TRUE(result != nullptr);
        EXPECT_EQ(kHelloExampleComAddrV4, ToString(result));
        EXPECT_EQ(1U, GetNumQueriesForType(dns2, ns_type::ns_t_a, kHelloExampleCom));
    });

    // step 4: wait a bit for the 2nd query to enter pending state
    usleep(100 * 1000);  // 100ms
    // step 6: flush cache (will unblock pending queries)
    EXPECT_TRUE(mDnsClient.resolvService()->flushNetworkCache(TEST_NETID).isOk());
    t2.join();

    // step 8: resume server#1
    dns1.setDeferredResp(false);
    t1.join();

    // step 10: verify if result is correctly cached
    dns2.clearQueries();
    ScopedAddrinfo result = safe_getaddrinfo("hello", nullptr, &hints);
    EXPECT_EQ(0U, GetNumQueries(dns2, kHelloExampleCom));
    EXPECT_EQ(kHelloExampleComAddrV4, ToString(result));
}

// TODO: Perhaps to have a boundary conditions test for TCP and UDP.
TEST_F(ResolverTest, TcpQueryWithOversizePayload) {
    test::DNSResponder dns;
    StartDns(dns, {{kHelloExampleCom, ns_type::ns_t_a, kHelloExampleComAddrV4}});
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    int fd = dns_open_proxy();
    ASSERT_TRUE(fd > 0);

    // Sending DNS query over TCP once the packet sizes exceed 512 bytes.
    // The raw data is combined with Question section and Additional section
    // Question section : query "hello.example.com", type A, class IN
    // Additional section : type OPT (41), Option PADDING, Option Length 546
    // Padding option which allows DNS clients and servers to artificially
    // increase the size of a DNS message by a variable number of bytes.
    // See also RFC7830, section 3
    const std::string query =
            "+c0BAAABAAAAAAABBWhlbGxvB2V4YW1wbGUDY29tAAABAAEAACkgAAAAgAACJgAMAiIAAAAAAAAAAAAAAAAAA"
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    const std::string cmd =
            "resnsend " + std::to_string(TEST_NETID) + " 0 " /* ResNsendFlags */ + query + '\0';
    ssize_t rc = TEMP_FAILURE_RETRY(write(fd, cmd.c_str(), cmd.size()));
    EXPECT_EQ(rc, static_cast<ssize_t>(cmd.size()));
    expectAnswersValid(fd, AF_INET, kHelloExampleComAddrV4);
    EXPECT_EQ(1U, GetNumQueriesForProtocol(dns, IPPROTO_TCP, kHelloExampleCom));
    EXPECT_EQ(0U, GetNumQueriesForProtocol(dns, IPPROTO_UDP, kHelloExampleCom));
}

TEST_F(ResolverTest, TruncatedRspMode) {
    constexpr char listen_addr[] = "127.0.0.4";
    constexpr char listen_addr2[] = "127.0.0.5";
    constexpr char listen_srv[] = "53";

    test::DNSResponder dns(listen_addr, listen_srv, static_cast<ns_rcode>(-1));
    test::DNSResponder dns2(listen_addr2, listen_srv, static_cast<ns_rcode>(-1));
    // dns supports UDP only, dns2 support UDP and TCP
    dns.setResponseProbability(0.0, IPPROTO_TCP);
    StartDns(dns, kLargeCnameChainRecords);
    StartDns(dns2, kLargeCnameChainRecords);

    const struct TestConfig {
        const std::optional<int32_t> tcMode;
        const bool ret;
        const unsigned numQueries;
        std::string asParameters() const {
            return fmt::format("tcMode: {}, ret: {}, numQueries: {}", tcMode.value_or(-1),
                               ret ? "true" : "false", numQueries);
        }
    } testConfigs[]{
            // clang-format off
            {std::nullopt,                                      true,  0}, /* mode unset */
            {aidl::android::net::IDnsResolver::TC_MODE_DEFAULT, true,  0}, /* default mode */
            {-666,                                              false, 0}, /* invalid input */
            {aidl::android::net::IDnsResolver::TC_MODE_UDP_TCP, true,  1}, /* alternative mode */
            // clang-format on
    };

    for (const auto& config : testConfigs) {
        SCOPED_TRACE(config.asParameters());

        ResolverParamsParcel parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
        parcel.servers = {listen_addr, listen_addr2};
        ResolverOptionsParcel resolverOptions;
        if (config.tcMode.has_value()) resolverOptions.tcMode = config.tcMode.value();
        if (!mIsResolverOptionIPCSupported) {
            parcel.resolverOptions = resolverOptions;
            ASSERT_EQ(mDnsClient.resolvService()->setResolverConfiguration(parcel).isOk(),
                      config.ret);
        } else {
            ASSERT_TRUE(mDnsClient.resolvService()->setResolverConfiguration(parcel).isOk());
        }
        if (mIsResolverOptionIPCSupported) {
            ASSERT_EQ(mDnsClient.resolvService()
                              ->setResolverOptions(parcel.netId, resolverOptions)
                              .isOk(),
                      config.ret);
        }

        const addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
        ScopedAddrinfo result = safe_getaddrinfo("hello", nullptr, &hints);
        ASSERT_TRUE(result != nullptr);
        EXPECT_EQ(ToString(result), kHelloExampleComAddrV4);
        // TC_MODE_DEFAULT: resolver retries on TCP-only on each name server.
        // TC_MODE_UDP_TCP: resolver retries on TCP on the same server, falls back to UDP from next.
        ASSERT_EQ(GetNumQueriesForProtocol(dns, IPPROTO_UDP, kHelloExampleCom), 1U);
        ASSERT_EQ(GetNumQueriesForProtocol(dns, IPPROTO_TCP, kHelloExampleCom), 1U);
        ASSERT_EQ(GetNumQueriesForProtocol(dns2, IPPROTO_UDP, kHelloExampleCom), config.numQueries);
        ASSERT_EQ(GetNumQueriesForProtocol(dns2, IPPROTO_TCP, kHelloExampleCom), 1U);

        dns.clearQueries();
        dns2.clearQueries();
        ASSERT_TRUE(mDnsClient.resolvService()->flushNetworkCache(TEST_NETID).isOk());

        // Clear the stats to make the resolver always choose the same server for the first query.
        parcel.servers.clear();
        parcel.tlsServers.clear();
        if (!mIsResolverOptionIPCSupported) {
            ASSERT_EQ(mDnsClient.resolvService()->setResolverConfiguration(parcel).isOk(),
                      config.ret);
        } else {
            ASSERT_TRUE(mDnsClient.resolvService()->setResolverConfiguration(parcel).isOk());
        }
    }
}

TEST_F(ResolverTest, RepeatedSetup_ResolverStatusRemains) {
    constexpr char unusable_listen_addr[] = "127.0.0.3";
    constexpr char listen_addr[] = "127.0.0.4";
    constexpr char hostname[] = "a.hello.query.";
    const auto repeatedSetResolversFromParcel = [&](const ResolverParamsParcel& parcel) {
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    };

    test::DNSResponder dns(listen_addr);
    StartDns(dns, {{hostname, ns_type::ns_t_a, "1.2.3.3"}});
    test::DnsTlsFrontend tls1(listen_addr, "853", listen_addr, "53");
    ASSERT_TRUE(tls1.startServer());

    // Private DNS off mode.
    ResolverParamsParcel parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    parcel.servers = {unusable_listen_addr, listen_addr};
    parcel.tlsServers.clear();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

    // Send a query.
    const addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
    EXPECT_NE(safe_getaddrinfo(hostname, nullptr, &hints), nullptr);

    // Check the stats as expected.
    const std::vector<NameserverStats> expectedCleartextDnsStats = {
            NameserverStats(unusable_listen_addr).setInternalErrors(1),
            NameserverStats(listen_addr).setSuccesses(1).setRttAvg(1),
    };
    EXPECT_TRUE(expectStatsEqualTo(expectedCleartextDnsStats));
    EXPECT_EQ(GetNumQueries(dns, hostname), 1U);

    // The stats is supposed to remain as long as the list of cleartext DNS servers is unchanged.
    static const struct TestConfig {
        std::vector<std::string> servers;
        std::vector<std::string> tlsServers;
        std::string tlsName;
    } testConfigs[] = {
            // Private DNS opportunistic mode.
            {{listen_addr, unusable_listen_addr}, {listen_addr, unusable_listen_addr}, ""},
            {{unusable_listen_addr, listen_addr}, {unusable_listen_addr, listen_addr}, ""},

            // Private DNS strict mode.
            {{listen_addr, unusable_listen_addr}, {"127.0.0.100"}, kDefaultPrivateDnsHostName},
            {{unusable_listen_addr, listen_addr}, {"127.0.0.100"}, kDefaultPrivateDnsHostName},

            // Private DNS off mode.
            {{unusable_listen_addr, listen_addr}, {}, ""},
            {{listen_addr, unusable_listen_addr}, {}, ""},
    };

    for (const auto& config : testConfigs) {
        SCOPED_TRACE(fmt::format("testConfig: [{}] [{}] [{}]", fmt::join(config.servers, ","),
                                 fmt::join(config.tlsServers, ","), config.tlsName));
        parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
        parcel.servers = config.servers;
        parcel.tlsServers = config.tlsServers;
        parcel.tlsName = config.tlsName;
        repeatedSetResolversFromParcel(parcel);
        EXPECT_TRUE(expectStatsEqualTo(expectedCleartextDnsStats));

        // The stats remains when the list of search domains changes.
        parcel.domains.push_back("tmp.domains");
        repeatedSetResolversFromParcel(parcel);
        EXPECT_TRUE(expectStatsEqualTo(expectedCleartextDnsStats));

        // The stats remains when the parameters change (except maxSamples).
        parcel.sampleValiditySeconds++;
        parcel.successThreshold++;
        parcel.minSamples++;
        parcel.baseTimeoutMsec++;
        parcel.retryCount++;
        repeatedSetResolversFromParcel(parcel);
        EXPECT_TRUE(expectStatsEqualTo(expectedCleartextDnsStats));
    }

    // The cache remains.
    EXPECT_NE(safe_getaddrinfo(hostname, nullptr, &hints), nullptr);
    EXPECT_EQ(GetNumQueries(dns, hostname), 1U);
}

TEST_F(ResolverTest, RepeatedSetup_NoRedundantPrivateDnsValidation) {
    const std::string addr1 = getUniqueIPv4Address();  // For a workable DNS server.
    const std::string addr2 = getUniqueIPv4Address();  // For an unresponsive DNS server.
    const std::string unusable_addr = getUniqueIPv4Address();
    const auto waitForPrivateDnsStateUpdated = []() {
        // A buffer time for the PrivateDnsConfiguration instance to update its map,
        // mPrivateDnsValidateThreads, which is used for tracking validation threads.
        // Since there is a time gap between when PrivateDnsConfiguration reports
        // onPrivateDnsValidationEvent and when PrivateDnsConfiguration updates the map, this is a
        // workaround to avoid the test starts a subsequent resolver setup during the time gap.
        // TODO: Report onPrivateDnsValidationEvent after all the relevant updates are complete.
        // Reference to b/152009023.
        std::this_thread::sleep_for(20ms);
    };

    test::DNSResponder dns1(addr1);
    test::DNSResponder dns2(addr2);
    StartDns(dns1, {});
    StartDns(dns2, {});
    test::DnsTlsFrontend workableTls(addr1, "853", addr1, "53");
    test::DnsTlsFrontend unresponsiveTls(addr2, "853", addr2, "53");
    unresponsiveTls.setHangOnHandshakeForTesting(true);
    ASSERT_TRUE(workableTls.startServer());
    ASSERT_TRUE(unresponsiveTls.startServer());

    // First setup.
    ResolverParamsParcel parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    parcel.servers = {addr1, addr2, unusable_addr};
    parcel.tlsServers = {addr1, addr2, unusable_addr};
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

    // Check the validation status before proceed. The validation for `unresponsiveTls`
    // should be running, and the other two should be finished.
    EXPECT_TRUE(WaitForPrivateDnsValidation(workableTls.listen_address(), true));
    EXPECT_TRUE(WaitForPrivateDnsValidation(unusable_addr, false));
    EXPECT_TRUE(PollForCondition([&]() { return unresponsiveTls.acceptConnectionsCount() == 1; }));
    unresponsiveTls.clearConnectionsCount();

    static const struct TestConfig {
        std::vector<std::string> tlsServers;
        std::string tlsName;
    } testConfigs[] = {
            {{addr1, addr2, unusable_addr}, ""},
            {{unusable_addr, addr1, addr2}, ""},
            {{unusable_addr, addr1, addr2}, kDefaultPrivateDnsHostName},
            {{addr1, addr2, unusable_addr}, kDefaultPrivateDnsHostName},
    };

    std::string TlsNameLastTime;
    for (const auto& config : testConfigs) {
        SCOPED_TRACE(fmt::format("testConfig: [{}] [{}]", fmt::join(config.tlsServers, ","),
                                 config.tlsName));
        parcel.servers = config.tlsServers;
        parcel.tlsServers = config.tlsServers;
        parcel.tlsName = config.tlsName;
        parcel.caCertificate = config.tlsName.empty() ? "" : kCaCert;

        const bool dnsModeChanged = (TlsNameLastTime != config.tlsName);
        bool validationAttemptToUnresponsiveTls = false;

        waitForPrivateDnsStateUpdated();
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

        for (const auto& serverAddr : parcel.tlsServers) {
            SCOPED_TRACE(serverAddr);
            if (serverAddr == workableTls.listen_address()) {
                if (dnsModeChanged) {
                    // Despite the identical IP address, the server is regarded as a different
                    // server when DnsTlsServer.name is different. The resolver treats it as a
                    // different object and begins the validation process.
                    EXPECT_TRUE(WaitForPrivateDnsValidation(serverAddr, true));
                }
            } else if (serverAddr == unresponsiveTls.listen_address()) {
                if (dnsModeChanged) {
                    // Despite the identical IP address, the server is regarded as a different
                    // server when DnsTlsServer.name is different. The resolver treats it as a
                    // different object and begins the validation process.
                    validationAttemptToUnresponsiveTls = true;

                    // This is the limitation from DnsTlsFrontend. DnsTlsFrontend can't operate
                    // concurrently. As soon as there's another connection request,
                    // DnsTlsFrontend resets the unique_fd to the new connection.
                    EXPECT_TRUE(WaitForPrivateDnsValidation(serverAddr, false));
                }
            } else {
                // Must be unusable_addr.
                // In opportunistic mode, when a validation for a private DNS server fails, the
                // resolver just marks the server as failed and doesn't re-evaluate it, but the
                // server can be re-evaluated when setResolverConfiguration() is called.
                // However, in strict mode, the resolver automatically re-evaluates the server and
                // marks the server as in_progress until the validation succeeds, so repeated setup
                // makes no effect.
                if (dnsModeChanged || config.tlsName.empty() /* not in strict mode */) {
                    EXPECT_TRUE(WaitForPrivateDnsValidation(serverAddr, false));
                }
            }
        }

        // Repeated setups make no effect in strict mode.
        waitForPrivateDnsStateUpdated();
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
        if (config.tlsName.empty()) {
            EXPECT_TRUE(WaitForPrivateDnsValidation(unusable_addr, false));
        }
        waitForPrivateDnsStateUpdated();
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
        if (config.tlsName.empty()) {
            EXPECT_TRUE(WaitForPrivateDnsValidation(unusable_addr, false));
        }

        if (validationAttemptToUnresponsiveTls) {
            EXPECT_TRUE(PollForCondition(
                    [&]() { return unresponsiveTls.acceptConnectionsCount() > 0; }));
        } else {
            EXPECT_EQ(unresponsiveTls.acceptConnectionsCount(), 0);
        }

        TlsNameLastTime = config.tlsName;
        unresponsiveTls.clearConnectionsCount();
    }

    // Check that all the validation results are caught.
    // Note: it doesn't mean no validation being in progress.
    EXPECT_FALSE(hasUncaughtPrivateDnsValidation(addr1));
    EXPECT_FALSE(hasUncaughtPrivateDnsValidation(addr2));
    EXPECT_FALSE(hasUncaughtPrivateDnsValidation(unusable_addr));
}

TEST_F(ResolverTest, RepeatedSetup_KeepChangingPrivateDnsServers) {
    enum TlsServerState { WORKING, UNSUPPORTED, UNRESPONSIVE };
    const std::string addr1 = getUniqueIPv4Address();
    const std::string addr2 = getUniqueIPv4Address();
    const auto waitForPrivateDnsStateUpdated = []() {
        // A buffer time for PrivateDnsConfiguration to update its state. It prevents this test
        // being flaky. See b/152009023 for the reason.
        std::this_thread::sleep_for(20ms);
    };

    test::DNSResponder dns1(addr1);
    test::DNSResponder dns2(addr2);
    StartDns(dns1, {});
    StartDns(dns2, {});
    test::DnsTlsFrontend tls1(addr1, "853", addr1, "53");
    test::DnsTlsFrontend tls2(addr2, "853", addr2, "53");
    ASSERT_TRUE(tls1.startServer());
    ASSERT_TRUE(tls2.startServer());

    static const struct TestConfig {
        std::string tlsServer;
        std::string tlsName;
        bool expectNothingHappenWhenServerUnsupported;
        bool expectNothingHappenWhenServerUnresponsive;
        std::string asTestName() const {
            return fmt::format("{}, {}, {}, {}", tlsServer, tlsName,
                               expectNothingHappenWhenServerUnsupported,
                               expectNothingHappenWhenServerUnresponsive);
        }
    } testConfigs[] = {
            {{addr1}, "", false, false},
            {{addr2}, "", false, false},
            {{addr1}, "", false, true},
            {{addr2}, "", false, true},

            // expectNothingHappenWhenServerUnresponsive is false in the two cases because of the
            // limitation from DnsTlsFrontend which can't operate concurrently.
            {{addr1}, kDefaultPrivateDnsHostName, false, false},
            {{addr2}, kDefaultPrivateDnsHostName, false, false},
            {{addr1}, kDefaultPrivateDnsHostName, true, true},
            {{addr2}, kDefaultPrivateDnsHostName, true, true},

            // expectNothingHappenWhenServerUnresponsive is true in the two cases because of the
            // limitation from DnsTlsFrontend which can't operate concurrently.
            {{addr1}, "", true, false},
            {{addr2}, "", true, false},
            {{addr1}, "", true, true},
            {{addr2}, "", true, true},
    };

    for (const auto& serverState : {WORKING, UNSUPPORTED, UNRESPONSIVE}) {
        int testIndex = 0;
        for (const auto& config : testConfigs) {
            SCOPED_TRACE(fmt::format("serverState:{} testIndex:{} testConfig:[{}]",
                                     static_cast<int>(serverState), testIndex++,
                                     config.asTestName()));
            auto& tls = (config.tlsServer == addr1) ? tls1 : tls2;

            if (serverState == UNSUPPORTED && tls.running()) ASSERT_TRUE(tls.stopServer());
            if (serverState != UNSUPPORTED && !tls.running()) ASSERT_TRUE(tls.startServer());

            tls.setHangOnHandshakeForTesting(serverState == UNRESPONSIVE);
            const int connectCountsBefore = tls.acceptConnectionsCount();

            waitForPrivateDnsStateUpdated();
            ResolverParamsParcel parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
            parcel.servers = {config.tlsServer};
            parcel.tlsServers = {config.tlsServer};
            parcel.tlsName = config.tlsName;
            parcel.caCertificate = config.tlsName.empty() ? "" : kCaCert;
            ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

            if (serverState == WORKING) {
                EXPECT_TRUE(WaitForPrivateDnsValidation(config.tlsServer, true));
            } else if (serverState == UNSUPPORTED) {
                if (config.expectNothingHappenWhenServerUnsupported) {
                    // It's possible that the resolver hasn't yet started to
                    // connect. Wait a while.
                    // TODO: See if we can get rid of the hard waiting time, such as comparing
                    // the CountDiff across two tests.
                    std::this_thread::sleep_for(100ms);
                    EXPECT_EQ(tls.acceptConnectionsCount(), connectCountsBefore);
                } else {
                    EXPECT_TRUE(WaitForPrivateDnsValidation(config.tlsServer, false));
                }
            } else {
                // Must be UNRESPONSIVE.
                // DnsTlsFrontend is the only signal for checking whether or not the resolver starts
                // another validation when the server is unresponsive.

                // Wait for a while to avoid running the checker code too early.
                std::this_thread::sleep_for(200ms);
                if (!config.expectNothingHappenWhenServerUnresponsive) {
                    EXPECT_TRUE(WaitForPrivateDnsValidation(config.tlsServer, false));
                }
                const auto condition = [&]() {
                    const int connectCountsAfter = tls.acceptConnectionsCount();
                    return config.expectNothingHappenWhenServerUnresponsive
                                   ? (connectCountsAfter == connectCountsBefore)
                                   : (connectCountsAfter > connectCountsBefore);
                };
                EXPECT_TRUE(PollForCondition(condition));
            }
        }

        // Set to off mode to reset the PrivateDnsConfiguration state.
        ResolverParamsParcel setupOffmode = DnsResponderClient::GetDefaultResolverParamsParcel();
        setupOffmode.tlsServers.clear();
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(setupOffmode));
    }

    // Check that all the validation results are caught.
    // Note: it doesn't mean no validation being in progress.
    EXPECT_FALSE(hasUncaughtPrivateDnsValidation(addr1));
    EXPECT_FALSE(hasUncaughtPrivateDnsValidation(addr2));
}

TEST_F(ResolverTest, PermissionCheckOnCertificateInjection) {
    ResolverParamsParcel parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    parcel.caCertificate = kCaCert;
    ASSERT_TRUE(mDnsClient.resolvService()->setResolverConfiguration(parcel).isOk());

    for (const uid_t uid : {AID_SYSTEM, TEST_UID}) {
        ScopedChangeUID scopedChangeUID(uid);
        auto status = mDnsClient.resolvService()->setResolverConfiguration(parcel);
        EXPECT_EQ(status.getExceptionCode(), EX_SECURITY);
    }
}

// Parameterized tests.
// TODO: Merge the existing tests as parameterized test if possible.
// TODO: Perhaps move parameterized tests to an independent file.
enum class CallType { GETADDRINFO, GETHOSTBYNAME };
class ResolverParameterizedTest : public ResolverTest,
                                  public testing::WithParamInterface<CallType> {
  protected:
    void VerifyQueryHelloExampleComV4(const test::DNSResponder& dns, const CallType calltype,
                                      const bool verifyNumQueries = true) {
        if (calltype == CallType::GETADDRINFO) {
            const addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
            ScopedAddrinfo result = safe_getaddrinfo("hello", nullptr, &hints);
            ASSERT_TRUE(result != nullptr);
            EXPECT_EQ(kHelloExampleComAddrV4, ToString(result));
        } else if (calltype == CallType::GETHOSTBYNAME) {
            const hostent* result = gethostbyname("hello");
            ASSERT_TRUE(result != nullptr);
            ASSERT_EQ(4, result->h_length);
            ASSERT_FALSE(result->h_addr_list[0] == nullptr);
            EXPECT_EQ(kHelloExampleComAddrV4, ToString(result));
            EXPECT_TRUE(result->h_addr_list[1] == nullptr);
        } else {
            FAIL() << "Unsupported call type: " << static_cast<uint32_t>(calltype);
        }
        if (verifyNumQueries) EXPECT_EQ(1U, GetNumQueries(dns, kHelloExampleCom));
    }
};

INSTANTIATE_TEST_SUITE_P(QueryCallTest, ResolverParameterizedTest,
                         testing::Values(CallType::GETADDRINFO, CallType::GETHOSTBYNAME),
                         [](const testing::TestParamInfo<CallType>& info) {
                             switch (info.param) {
                                 case CallType::GETADDRINFO:
                                     return "GetAddrInfo";
                                 case CallType::GETHOSTBYNAME:
                                     return "GetHostByName";
                                 default:
                                     return "InvalidParameter";  // Should not happen.
                             }
                         });

TEST_P(ResolverParameterizedTest, AuthoritySectionAndAdditionalSection) {
    // DNS response may have more information in authority section and additional section.
    // Currently, getanswer() of packages/modules/DnsResolver/getaddrinfo.cpp doesn't parse the
    // content of authority section and additional section. Test these sections if they crash
    // the resolver, just in case. See also RFC 1035 section 4.1.
    const auto& calltype = GetParam();
    test::DNSHeader header(kDefaultDnsHeader);

    // Create a DNS response which has a authoritative nameserver record in authority
    // section and its relevant address record in additional section.
    //
    // Question
    //   hello.example.com.     IN      A
    // Answer
    //   hello.example.com.     IN      A   1.2.3.4
    // Authority:
    //   hello.example.com.     IN      NS  ns1.example.com.
    // Additional:
    //   ns1.example.com.       IN      A   5.6.7.8
    //
    // A response may have only question, answer, and authority section. Current testing response
    // should be able to cover this condition.

    // Question section.
    test::DNSQuestion question{
            .qname = {.name = kHelloExampleCom},
            .qtype = ns_type::ns_t_a,
            .qclass = ns_c_in,
    };
    header.questions.push_back(std::move(question));

    // Answer section.
    test::DNSRecord recordAnswer{
            .name = {.name = kHelloExampleCom},
            .rtype = ns_type::ns_t_a,
            .rclass = ns_c_in,
            .ttl = 0,  // no cache
    };
    EXPECT_TRUE(test::DNSResponder::fillRdata(kHelloExampleComAddrV4, recordAnswer));
    header.answers.push_back(std::move(recordAnswer));

    // Authority section.
    test::DNSRecord recordAuthority{
            .name = {.name = kHelloExampleCom},
            .rtype = ns_type::ns_t_ns,
            .rclass = ns_c_in,
            .ttl = 0,  // no cache
    };
    EXPECT_TRUE(test::DNSResponder::fillRdata("ns1.example.com.", recordAuthority));
    header.authorities.push_back(std::move(recordAuthority));

    // Additional section.
    test::DNSRecord recordAdditional{
            .name = {.name = "ns1.example.com."},
            .rtype = ns_type::ns_t_a,
            .rclass = ns_c_in,
            .ttl = 0,  // no cache
    };
    EXPECT_TRUE(test::DNSResponder::fillRdata("5.6.7.8", recordAdditional));
    header.additionals.push_back(std::move(recordAdditional));

    // Start DNS server.
    test::DNSResponder dns(test::DNSResponder::MappingType::DNS_HEADER);
    dns.addMappingDnsHeader(kHelloExampleCom, ns_type::ns_t_a, header);
    ASSERT_TRUE(dns.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());
    dns.clearQueries();

    // Expect that get the address and the resolver doesn't crash.
    VerifyQueryHelloExampleComV4(dns, calltype);
}

TEST_P(ResolverParameterizedTest, MessageCompression) {
    const auto& calltype = GetParam();

    // The response with compressed domain name by a pointer. See RFC 1035 section 4.1.4.
    //
    // Ignoring the other fields of the message, the domain name of question section and answer
    // section are presented as:
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 12 |           5           |           h           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 14 |           e           |           l           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 16 |           l           |           o           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 18 |           7           |           e           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 20 |           x           |           a           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 22 |           m           |           p           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 24 |           l           |           e           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 26 |           3           |           c           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 28 |           o           |           m           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 30 |           0           |          ...          |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    //
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 35 | 1  1|                12                       |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    const std::vector<uint8_t> kResponseAPointer = {
            /* Header */
            0x00, 0x00, /* Transaction ID: 0x0000 */
            0x81, 0x80, /* Flags: qr rd ra */
            0x00, 0x01, /* Questions: 1 */
            0x00, 0x01, /* Answer RRs: 1 */
            0x00, 0x00, /* Authority RRs: 0 */
            0x00, 0x00, /* Additional RRs: 0 */
            /* Queries */
            0x05, 0x68, 0x65, 0x6c, 0x6c, 0x6f, 0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65,
            0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name: hello.example.com */
            0x00, 0x01,                   /* Type: A */
            0x00, 0x01,                   /* Class: IN */
            /* Answers */
            0xc0, 0x0c,             /* Name: hello.example.com (a pointer) */
            0x00, 0x01,             /* Type: A */
            0x00, 0x01,             /* Class: IN */
            0x00, 0x00, 0x00, 0x00, /* Time to live: 0 */
            0x00, 0x04,             /* Data length: 4 */
            0x01, 0x02, 0x03, 0x04  /* Address: 1.2.3.4 */
    };

    // The response with compressed domain name by a sequence of labels ending with a pointer. See
    // RFC 1035 section 4.1.4.
    //
    // Ignoring the other fields of the message, the domain name of question section and answer
    // section are presented as:
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 12 |           5           |           h           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 14 |           e           |           l           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 16 |           l           |           o           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 18 |           7           |           e           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 20 |           x           |           a           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 22 |           m           |           p           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 24 |           l           |           e           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 26 |           3           |           c           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 28 |           o           |           m           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 30 |           0           |          ...          |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    //
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 35 |           5           |           h           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 37 |           e           |           l           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 39 |           l           |           o           |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    // 41 | 1  1|                18                       |
    //    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    const std::vector<uint8_t> kResponseLabelEndingWithAPointer = {
            /* Header */
            0x00, 0x00, /* Transaction ID: 0x0000 */
            0x81, 0x80, /* Flags: qr rd ra */
            0x00, 0x01, /* Questions: 1 */
            0x00, 0x01, /* Answer RRs: 1 */
            0x00, 0x00, /* Authority RRs: 0 */
            0x00, 0x00, /* Additional RRs: 0 */
            /* Queries */
            0x05, 0x68, 0x65, 0x6c, 0x6c, 0x6f, 0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65,
            0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name: hello.example.com */
            0x00, 0x01,                   /* Type: A */
            0x00, 0x01,                   /* Class: IN */
            /* Answers */
            0x05, 0x68, 0x65, 0x6c, 0x6c, 0x6f, 0xc0,
            0x12,                   /* Name: hello.example.com (a label ending with a pointer) */
            0x00, 0x01,             /* Type: A */
            0x00, 0x01,             /* Class: IN */
            0x00, 0x00, 0x00, 0x00, /* Time to live: 0 */
            0x00, 0x04,             /* Data length: 4 */
            0x01, 0x02, 0x03, 0x04  /* Address: 1.2.3.4 */
    };

    for (const auto& response : {kResponseAPointer, kResponseLabelEndingWithAPointer}) {
        SCOPED_TRACE(fmt::format("Hex dump: {}", toHex(makeSlice(response))));

        test::DNSResponder dns(test::DNSResponder::MappingType::BINARY_PACKET);
        dns.addMappingBinaryPacket(kHelloExampleComQueryV4, response);
        StartDns(dns, {});
        ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

        // Expect no cache because the TTL of testing responses are 0.
        VerifyQueryHelloExampleComV4(dns, calltype);
    }
}

TEST_P(ResolverParameterizedTest, TruncatedResponse) {
    const auto& calltype = GetParam();

    test::DNSResponder dns;
    StartDns(dns, kLargeCnameChainRecords);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    // Expect UDP response is truncated. The resolver retries over TCP. See RFC 1035 section 4.2.1.
    VerifyQueryHelloExampleComV4(dns, calltype, false);
    EXPECT_EQ(1U, GetNumQueriesForProtocol(dns, IPPROTO_UDP, kHelloExampleCom));
    EXPECT_EQ(1U, GetNumQueriesForProtocol(dns, IPPROTO_TCP, kHelloExampleCom));
}

// Tests that the DnsResolver can keep listening to the DNS response from previous DNS servers.
// Test scenarios (The timeout for each server is 1 second):
//   1. (During the first iteration of DNS servers) While waiting for the DNS response from the
//      second server, the DnsResolver receives the DNS response from the first server.
//   2. (During the second iteration of DNS servers) While waiting for the DNS response from the
//      second server, the DnsResolver receives the DNS response from the first server.
TEST_F(ResolverTest, KeepListeningUDP) {
    constexpr char listen_addr1[] = "127.0.0.4";
    constexpr char listen_addr2[] = "127.0.0.5";
    constexpr char host_name[] = "howdy.example.com.";
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };
    auto builder =
            ResolverParams::Builder().setDnsServers({listen_addr1, listen_addr2}).setDotServers({});

    test::DNSResponder neverRespondDns(listen_addr2, "53", static_cast<ns_rcode>(-1));
    neverRespondDns.setResponseProbability(0.0);
    StartDns(neverRespondDns, records);
    test::DNSResponder delayedDns(listen_addr1);
    StartDns(delayedDns, records);

    const struct TestConfig {
        int retryCount;
        int delayTimeMs;
        int expectedDns1Successes;
        int expectedDns1Timeouts;
        int expectedDns2Timeouts;
    } testConfigs[]{
            {1, 1500, 1, 1, 0},
            // Actually, there will be two timeouts and one success for DNS1. However, the
            // DnsResolver doesn't record the stats during the second iteration of DNS servers, so
            // the success and timeout of DNS1 is 0 and 1, respectively.
            {2, 3500, 0, 1, 1},
    };
    for (const std::string_view callType : {"getaddrinfo", "resnsend"}) {
        for (const auto& cfg : testConfigs) {
            SCOPED_TRACE(fmt::format("callType={}, retryCount={}, delayTimeMs={}", callType,
                                     cfg.retryCount, cfg.delayTimeMs));
            const std::array<int, IDnsResolver::RESOLVER_PARAMS_COUNT> params = {
                    300, 25, 8, 8, 1000 /* BASE_TIMEOUT_MSEC */, cfg.retryCount /* retry count */};

            ScopedSystemProperties sp(kKeepListeningUdpFlag, "1");
            resetNetwork();
            ASSERT_TRUE(mDnsClient.SetResolversFromParcel(builder.setParams(params).build()));

            delayedDns.setDeferredResp(true);
            std::thread thread([&]() {
                std::this_thread::sleep_for(std::chrono::milliseconds(cfg.delayTimeMs));
                delayedDns.setDeferredResp(false);
            });

            if (callType == "getaddrinfo") {
                const addrinfo hints = {.ai_family = AF_INET6, .ai_socktype = SOCK_DGRAM};
                ScopedAddrinfo result = safe_getaddrinfo(host_name, nullptr, &hints);
                EXPECT_EQ("::1.2.3.4", ToString(result));
            } else {
                int fd = resNetworkQuery(TEST_NETID, host_name, ns_c_in, ns_t_aaaa, 0);
                expectAnswersValid(fd, AF_INET6, "::1.2.3.4");
            }
            const std::vector<NameserverStats> expectedCleartextDnsStats = {
                    NameserverStats(listen_addr1)
                            .setSuccesses(cfg.expectedDns1Successes)
                            .setTimeouts(cfg.expectedDns1Timeouts)
                            .setRttAvg(cfg.retryCount == 1 ? 1500 : -1),
                    NameserverStats(listen_addr2)
                            .setTimeouts(cfg.expectedDns2Timeouts)
                            .setRttAvg(-1),
            };
            EXPECT_TRUE(expectStatsEqualTo(expectedCleartextDnsStats));
            thread.join();
        }
    }
}

TEST_F(ResolverTest, GetAddrInfoParallelLookupTimeout) {
    constexpr char host_name[] = "howdy.example.com.";
    constexpr int TIMING_TOLERANCE_MS = 200;
    constexpr int DNS_TIMEOUT_MS = 1000;
    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, "1.2.3.4"},
            {host_name, ns_type::ns_t_aaaa, "::1.2.3.4"},
    };
    const std::array<int, IDnsResolver::RESOLVER_PARAMS_COUNT> params = {
            300, 25, 8, 8, DNS_TIMEOUT_MS /* BASE_TIMEOUT_MSEC */, 1 /* retry count */};
    test::DNSResponder neverRespondDns(kDefaultServer, "53", static_cast<ns_rcode>(-1));
    neverRespondDns.setResponseProbability(0.0);
    StartDns(neverRespondDns, records);

    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(
            ResolverParams::Builder().setDotServers({}).setParams(params).build()));
    neverRespondDns.clearQueries();

    // Use a never respond DNS server to verify if the A/AAAA queries are sent in parallel.
    // The resolver parameters are set to timeout 1s and retry 1 times.
    // So we expect the safe_getaddrinfo_time_taken() might take ~1s to
    // return when parallel lookup is enabled. And the DNS server should receive 2 queries.
    const addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_DGRAM};
    auto [result, timeTakenMs] = safe_getaddrinfo_time_taken(host_name, nullptr, hints);

    EXPECT_TRUE(result == nullptr);
    EXPECT_NEAR(DNS_TIMEOUT_MS, timeTakenMs, TIMING_TOLERANCE_MS)
            << "took time should approximate equal timeout";
    EXPECT_EQ(2U, GetNumQueries(neverRespondDns, host_name));
    ExpectDnsEvent(INetdEventListener::EVENT_GETADDRINFO, RCODE_TIMEOUT, host_name, {});
}

TEST_F(ResolverTest, GetAddrInfoParallelLookupSleepTime) {
    constexpr int TIMING_TOLERANCE_MS = 200;
    const std::vector<DnsRecord> records = {
            {kHelloExampleCom, ns_type::ns_t_a, kHelloExampleComAddrV4},
            {kHelloExampleCom, ns_type::ns_t_aaaa, kHelloExampleComAddrV6},
    };
    const std::array<int, IDnsResolver::RESOLVER_PARAMS_COUNT> params = {
            300, 25, 8, 8, 1000 /* BASE_TIMEOUT_MSEC */, 1 /* retry count */};
    test::DNSResponder dns(kDefaultServer);
    StartDns(dns, records);
    constexpr int PARALLEL_LOOKUP_SLEEP_TIME_MS = 500;
    ScopedSystemProperties sp2(kParallelLookupSleepTimeFlag,
                               std::to_string(PARALLEL_LOOKUP_SLEEP_TIME_MS));
    // Re-setup test network to make experiment flag take effect.
    resetNetwork();

    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(
            ResolverParams::Builder().setDotServers({}).setParams(params).build()));
    dns.clearQueries();

    // Expect the safe_getaddrinfo_time_taken() might take ~500ms to return because we set
    // parallel_lookup_sleep_time to 500ms.
    const addrinfo hints = {.ai_family = AF_UNSPEC, .ai_socktype = SOCK_DGRAM};
    auto [result, timeTakenMs] = safe_getaddrinfo_time_taken(kHelloExampleCom, nullptr, hints);

    EXPECT_NE(nullptr, result);
    EXPECT_THAT(ToStrings(result), testing::UnorderedElementsAreArray(
                                           {kHelloExampleComAddrV4, kHelloExampleComAddrV6}));
    EXPECT_NEAR(PARALLEL_LOOKUP_SLEEP_TIME_MS, timeTakenMs, TIMING_TOLERANCE_MS)
            << "took time should approximate equal timeout";
    EXPECT_EQ(2U, GetNumQueries(dns, kHelloExampleCom));

    // Expect the PARALLEL_LOOKUP_SLEEP_TIME_MS won't affect the query under cache hit case.
    dns.clearQueries();
    std::tie(result, timeTakenMs) = safe_getaddrinfo_time_taken(kHelloExampleCom, nullptr, hints);
    EXPECT_NE(nullptr, result);
    EXPECT_THAT(ToStrings(result), testing::UnorderedElementsAreArray(
                                           {kHelloExampleComAddrV4, kHelloExampleComAddrV6}));
    EXPECT_GT(PARALLEL_LOOKUP_SLEEP_TIME_MS, timeTakenMs);
    EXPECT_EQ(0U, GetNumQueries(dns, kHelloExampleCom));
}

TEST_F(ResolverTest, BlockDnsQueryUidDoesNotLeadToBadServer) {
    SKIP_IF_BPF_NOT_SUPPORTED;
    constexpr char listen_addr1[] = "127.0.0.4";
    constexpr char listen_addr2[] = "::1";
    test::DNSResponder dns1(listen_addr1);
    test::DNSResponder dns2(listen_addr2);
    StartDns(dns1, {});
    StartDns(dns2, {});

    std::vector<std::string> servers = {listen_addr1, listen_addr2};
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork(servers));
    dns1.clearQueries();
    dns2.clearQueries();
    {
        ScopeBlockedUIDRule scopeBlockUidRule(mDnsClient.netdService(), TEST_UID);
        // Start querying ten times.
        for (int i = 0; i < 10; i++) {
            std::string hostName = fmt::format("blocked{}.com", i);
            const addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
            // The query result between R+ and Q would be different, but we don't really care
            // about the result here because this test is only used to ensure blocked uid rule
            // won't cause bad servers.
            safe_getaddrinfo(hostName.c_str(), nullptr, &hints);
        }
    }
    ResolverParamsParcel setupParams = DnsResponderClient::GetDefaultResolverParamsParcel();
    // If api level >= 30 (R+), expect all query packets to be blocked, hence we should not see any
    // of their stats show up. Otherwise, all queries should succeed.
    const std::vector<NameserverStats> expectedDnsStats = {
            NameserverStats(listen_addr1)
                    .setSuccesses(isAtLeastR ? 0 : setupParams.maxSamples)
                    .setRttAvg(isAtLeastR ? -1 : 1),
            NameserverStats(listen_addr2),
    };
    expectStatsEqualTo(expectedDnsStats);
    // If api level >= 30 (R+), expect server won't receive any queries,
    // otherwise expect 20 == 10 * (setupParams.domains.size() + 1) queries.
    EXPECT_EQ(dns1.queries().size(), isAtLeastR ? 0U : 10 * (setupParams.domains.size() + 1));
    EXPECT_EQ(dns2.queries().size(), 0U);
}

TEST_F(ResolverTest, DnsServerSelection) {
    test::DNSResponder dns1("127.0.0.3");
    test::DNSResponder dns2("127.0.0.4");
    test::DNSResponder dns3("127.0.0.5");

    dns1.setResponseDelayMs(10);
    dns2.setResponseDelayMs(50);
    dns3.setResponseDelayMs(100);
    StartDns(dns1, {{kHelloExampleCom, ns_type::ns_t_a, kHelloExampleComAddrV4}});
    StartDns(dns2, {{kHelloExampleCom, ns_type::ns_t_a, kHelloExampleComAddrV4}});
    StartDns(dns3, {{kHelloExampleCom, ns_type::ns_t_a, kHelloExampleComAddrV4}});

    // NOTE: the servers must be sorted alphabetically.
    std::vector<std::string> serverList = {
            dns1.listen_address(),
            dns2.listen_address(),
            dns3.listen_address(),
    };

    do {
        SCOPED_TRACE(fmt::format("testConfig: [{}]", fmt::join(serverList, ", ")));
        const int queryNum = 50;
        int64_t accumulatedTime = 0;

        // The flag can be reset any time. It's better to re-setup the flag in each iteration.
        ScopedSystemProperties scopedSystemProperties(kSortNameserversFlag, "1");

        // Restart the testing network to 1) make the flag take effect and 2) reset the statistics.
        resetNetwork();

        // DnsServerSelection doesn't apply to private DNS.
        ResolverParamsParcel setupParams = DnsResponderClient::GetDefaultResolverParamsParcel();
        setupParams.servers = serverList;
        setupParams.tlsServers.clear();
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(setupParams));

        // DNSResponder doesn't handle queries concurrently, so don't allow more than
        // one in-flight query.
        for (int i = 0; i < queryNum; i++) {
            Stopwatch s;
            int fd = resNetworkQuery(TEST_NETID, kHelloExampleCom, ns_c_in, ns_t_a,
                                     ANDROID_RESOLV_NO_CACHE_LOOKUP);
            expectAnswersValid(fd, AF_INET, kHelloExampleComAddrV4);
            accumulatedTime += s.timeTakenUs();
        }

        const int dns1Count = dns1.queries().size();
        const int dns2Count = dns2.queries().size();
        const int dns3Count = dns3.queries().size();

        // All of the servers have ever been selected. In addition, the less latency server
        // is selected more frequently.
        EXPECT_GT(dns1Count, 0);
        EXPECT_GT(dns2Count, 0);
        EXPECT_GT(dns3Count, 0);
        EXPECT_GE(dns1Count, dns2Count);
        EXPECT_GE(dns2Count, dns3Count);

        const int averageTime = accumulatedTime / queryNum;
        LOG(INFO) << "ResolverTest#DnsServerSelection: averageTime " << averageTime << "us";

        dns1.clearQueries();
        dns2.clearQueries();
        dns3.clearQueries();
    } while (std::next_permutation(serverList.begin(), serverList.end()));
}

TEST_F(ResolverTest, MultipleDotQueriesInOnePacket) {
    constexpr char hostname1[] = "query1.example.com.";
    constexpr char hostname2[] = "query2.example.com.";
    const std::vector<DnsRecord> records = {
            {hostname1, ns_type::ns_t_a, "1.2.3.4"},
            {hostname2, ns_type::ns_t_a, "1.2.3.5"},
    };

    const std::string addr = getUniqueIPv4Address();
    test::DNSResponder dns(addr);
    StartDns(dns, records);
    test::DnsTlsFrontend tls(addr, "853", addr, "53");
    ASSERT_TRUE(tls.startServer());

    // Set up resolver to strict mode.
    auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    parcel.servers = {addr};
    parcel.tlsServers = {addr};
    parcel.tlsName = kDefaultPrivateDnsHostName;
    parcel.caCertificate = kCaCert;
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForPrivateDnsValidation(tls.listen_address(), true));
    EXPECT_TRUE(tls.waitForQueries(1));
    tls.clearQueries();
    dns.clearQueries();

    const auto queryAndCheck = [&](const std::string& hostname,
                                   const std::vector<DnsRecord>& records) {
        SCOPED_TRACE(hostname);

        const addrinfo hints = {.ai_family = AF_INET, .ai_socktype = SOCK_DGRAM};
        auto [result, timeTakenMs] = safe_getaddrinfo_time_taken(hostname.c_str(), nullptr, hints);

        std::vector<std::string> expectedAnswers;
        for (const auto& r : records) {
            if (r.host_name == hostname) expectedAnswers.push_back(r.addr);
        }

        EXPECT_LE(timeTakenMs, 200);
        ASSERT_NE(result, nullptr);
        EXPECT_THAT(ToStrings(result), testing::UnorderedElementsAreArray(expectedAnswers));
    };

    // Set tls to reply DNS responses in one TCP packet and not to close the connection from its
    // side.
    tls.setDelayQueries(2);
    tls.setDelayQueriesTimeout(500);
    tls.setPassiveClose(true);

    // Start sending DNS requests at the same time.
    std::array<std::thread, 2> threads;
    threads[0] = std::thread(queryAndCheck, hostname1, records);
    threads[1] = std::thread(queryAndCheck, hostname2, records);

    threads[0].join();
    threads[1].join();

    // Also check no additional queries due to DoT reconnection.
    EXPECT_TRUE(tls.waitForQueries(2));
}

TEST_F(ResolverTest, MdnsGetHostByName) {
    constexpr char v6addr[] = "::127.0.0.3";
    constexpr char v4addr[] = "127.0.0.3";
    constexpr char host_name[] = "hello.local.";
    constexpr char nonexistent_host_name[] = "nonexistent.local.";

    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService);
    mdnsv4.addMapping(host_name, ns_type::ns_t_a, v4addr);
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService);
    mdnsv6.addMapping(host_name, ns_type::ns_t_aaaa, v6addr);

    ASSERT_TRUE(mdnsv4.startServer());
    ASSERT_TRUE(mdnsv6.startServer());
    mdnsv4.clearQueries();
    mdnsv6.clearQueries();

    std::vector<bool> keep_listening_udp_enable = {false, true};
    for (int value : keep_listening_udp_enable) {
        if (value == true) {
            // Set keep_listening_udp enable
            ScopedSystemProperties sp(kKeepListeningUdpFlag, "1");
            // Re-setup test network to make experiment flag take effect.
            resetNetwork();
        }
        ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

        static const struct TestConfig {
            int ai_family;
            const std::string expected_addr;
        } testConfigs[]{
                {AF_INET, v4addr},
                {AF_INET6, v6addr},
        };

        for (const auto& config : testConfigs) {
            SCOPED_TRACE(fmt::format("family: {}", config.ai_family));
            const hostent* result = nullptr;

            // No response for "nonexistent.local".
            result = gethostbyname2("nonexistent.local", config.ai_family);
            ASSERT_TRUE(result == nullptr);
            test::DNSResponder& mdns = config.ai_family == AF_INET ? mdnsv4 : mdnsv6;
            EXPECT_EQ(1U, GetNumQueries(mdns, nonexistent_host_name));
            mdns.clearQueries();
            EXPECT_EQ(HOST_NOT_FOUND, h_errno);

            // Normal mDns query
            result = gethostbyname2("hello.local", config.ai_family);
            ASSERT_FALSE(result == nullptr);
            EXPECT_EQ(1U, GetNumQueries(mdns, host_name));
            int length = config.ai_family == AF_INET ? 4 : 16;
            ASSERT_EQ(length, result->h_length);
            ASSERT_FALSE(result->h_addr_list[0] == nullptr);
            EXPECT_EQ(config.expected_addr, ToString(result));
            EXPECT_TRUE(result->h_addr_list[1] == nullptr);
            mdns.clearQueries();

            // Ensure the query result is still cached.
            result = gethostbyname2("hello.local", config.ai_family);
            EXPECT_EQ(0U, GetNumQueries(mdnsv4, "hello.local."));
            ASSERT_FALSE(result == nullptr);
            EXPECT_EQ(config.expected_addr, ToString(result));
            ASSERT_TRUE(mDnsClient.resolvService()->flushNetworkCache(TEST_NETID).isOk());
        }
    }
}

namespace {

static const struct TransportTypeConfig {
    const std::vector<int32_t>& transportTypes;
    bool useMdns;
} transportTypeConfig[]{
        // clang-format off
        {{}, true},
        {{IDnsResolver::TRANSPORT_CELLULAR}, false},
        {{IDnsResolver::TRANSPORT_WIFI}, true},
        {{IDnsResolver::TRANSPORT_BLUETOOTH}, true},
        {{IDnsResolver::TRANSPORT_ETHERNET}, true},
        {{IDnsResolver::TRANSPORT_VPN}, false},
        {{IDnsResolver::TRANSPORT_WIFI_AWARE}, true},
        {{IDnsResolver::TRANSPORT_LOWPAN}, true},
        {{IDnsResolver::TRANSPORT_TEST}, true},
        {{IDnsResolver::TRANSPORT_USB}, true},
        {{IDnsResolver::TRANSPORT_CELLULAR, IDnsResolver::TRANSPORT_VPN}, false},
        {{IDnsResolver::TRANSPORT_WIFI, IDnsResolver::TRANSPORT_VPN}, false},
        {{IDnsResolver::TRANSPORT_BLUETOOTH, IDnsResolver::TRANSPORT_VPN}, false},
        {{IDnsResolver::TRANSPORT_ETHERNET, IDnsResolver::TRANSPORT_VPN}, false},
        {{IDnsResolver::TRANSPORT_CELLULAR, IDnsResolver::TRANSPORT_WIFI,
          IDnsResolver::TRANSPORT_VPN}, false},
        {{IDnsResolver::TRANSPORT_WIFI_AWARE, IDnsResolver::TRANSPORT_VPN}, false},
        {{IDnsResolver::TRANSPORT_LOWPAN, IDnsResolver::TRANSPORT_VPN}, false},
        {{IDnsResolver::TRANSPORT_TEST, IDnsResolver::TRANSPORT_VPN}, false},
        {{IDnsResolver::TRANSPORT_USB, IDnsResolver::TRANSPORT_VPN}, false},
        // clang-format on
};

}  // namespace

TEST_F(ResolverTest, MdnsGetHostByName_transportTypes) {
    constexpr char v6addr[] = "::127.0.0.3";
    constexpr char v4addr[] = "127.0.0.3";
    constexpr char host_name[] = "hello.local.";

    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService);
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService);
    mdnsv4.addMapping(host_name, ns_type::ns_t_a, v4addr);
    mdnsv6.addMapping(host_name, ns_type::ns_t_aaaa, v6addr);
    ASSERT_TRUE(mdnsv4.startServer());
    ASSERT_TRUE(mdnsv6.startServer());

    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, v4addr},
            {host_name, ns_type::ns_t_aaaa, v6addr},
    };
    test::DNSResponder dns(v4addr);
    StartDns(dns, records);

    for (const auto& tpConfig : transportTypeConfig) {
        SCOPED_TRACE(fmt::format("transportTypes: [{}], useMdns: {}",
                                 fmt::join(tpConfig.transportTypes, ","), tpConfig.useMdns));
        ResolverParamsParcel setupParams = DnsResponderClient::GetDefaultResolverParamsParcel();
        setupParams.transportTypes = tpConfig.transportTypes;
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(setupParams));

        static const struct TestConfig {
            int ai_family;
            const std::string expected_addr;
        } testConfigs[]{
                {AF_INET, v4addr},
                {AF_INET6, v6addr},
        };

        for (const auto& config : testConfigs) {
            SCOPED_TRACE(fmt::format("family: {}", config.ai_family));
            const hostent* result = nullptr;
            test::DNSResponder& mdns = config.ai_family == AF_INET ? mdnsv4 : mdnsv6;

            result = gethostbyname2("hello.local", config.ai_family);
            ASSERT_FALSE(result == nullptr);
            if (tpConfig.useMdns) {
                EXPECT_EQ(1U, GetNumQueries(mdns, host_name));
                EXPECT_EQ(0U, GetNumQueries(dns, host_name));
            } else {
                EXPECT_EQ(0U, GetNumQueries(mdns, host_name));
                EXPECT_EQ(1U, GetNumQueries(dns, host_name));
            }
            int length = config.ai_family == AF_INET ? 4 : 16;
            ASSERT_EQ(length, result->h_length);
            ASSERT_FALSE(result->h_addr_list[0] == nullptr);
            EXPECT_EQ(config.expected_addr, ToString(result));
            EXPECT_TRUE(result->h_addr_list[1] == nullptr);

            mdns.clearQueries();
            dns.clearQueries();
            ASSERT_TRUE(mDnsClient.resolvService()->flushNetworkCache(TEST_NETID).isOk());
        }
    }
}

TEST_F(ResolverTest, MdnsGetHostByName_cnames) {
    constexpr char v6addr[] = "::127.0.0.3";
    constexpr char v4addr[] = "127.0.0.3";
    constexpr char host_name[] = "hello.local.";
    const std::vector<DnsRecord> records = {
            {"hi.local.", ns_type::ns_t_cname, "a.local."},
            {"a.local.", ns_type::ns_t_cname, "b.local."},
            {"b.local.", ns_type::ns_t_cname, "c.local."},
            {"c.local.", ns_type::ns_t_cname, "d.local."},
            {"d.local.", ns_type::ns_t_cname, "e.local."},
            {"e.local.", ns_type::ns_t_cname, host_name},
            {host_name, ns_type::ns_t_a, v4addr},
            {host_name, ns_type::ns_t_aaaa, v6addr},
    };
    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService);
    for (const auto& r : records) {
        mdnsv4.addMapping(r.host_name, r.type, r.addr);
    }
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService);
    for (const auto& r : records) {
        mdnsv6.addMapping(r.host_name, r.type, r.addr);
    }
    ASSERT_TRUE(mdnsv4.startServer());
    ASSERT_TRUE(mdnsv6.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());
    mdnsv4.clearQueries();
    mdnsv6.clearQueries();

    static const struct TestConfig {
        int ai_family;
        const std::string expected_addr;
    } testConfigs[]{
            {AF_INET, v4addr},
            {AF_INET6, v6addr},
    };

    for (const auto& config : testConfigs) {
        size_t cnamecount = 0;
        // using gethostbyname2() to resolve ipv4 hello.local. to 127.0.0.3
        // or ipv6 hello.local. to ::127.0.0.3.
        // Ensure the v4 address and cnames are correct
        const hostent* result;
        result = gethostbyname2("hi.local", config.ai_family);
        ASSERT_FALSE(result == nullptr);

        for (int i = 0; result != nullptr && result->h_aliases[i] != nullptr; i++) {
            std::string domain_name =
                    records[i].host_name.substr(0, records[i].host_name.size() - 1);
            EXPECT_EQ(result->h_aliases[i], domain_name);
            cnamecount++;
        }
        // The size of "Non-cname type" record in DNS records is 2
        ASSERT_EQ(cnamecount, records.size() - 2);
        test::DNSResponder& mdns = config.ai_family == AF_INET ? mdnsv4 : mdnsv6;
        EXPECT_EQ(1U, mdnsv4.queries().size()) << mdns.dumpQueries();
        int length = config.ai_family == AF_INET ? 4 : 16;
        ASSERT_EQ(length, result->h_length);

        ASSERT_FALSE(result->h_addr_list[0] == nullptr);
        EXPECT_EQ(config.expected_addr, ToString(result));
        EXPECT_TRUE(result->h_addr_list[1] == nullptr);
    }
}

TEST_F(ResolverTest, MdnsGetHostByName_cnamesInfiniteLoop) {
    constexpr char host_name1[] = "hello.local.";
    constexpr char host_name2[] = "hi.local.";
    const std::vector<DnsRecord> records = {
            {host_name1, ns_type::ns_t_cname, host_name2},
            {host_name2, ns_type::ns_t_cname, host_name1},
    };

    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService);
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService);
    for (const auto& r : records) {
        mdnsv4.addMapping(r.host_name, r.type, r.addr);
    }
    for (const auto& r : records) {
        mdnsv6.addMapping(r.host_name, r.type, r.addr);
    }
    ASSERT_TRUE(mdnsv4.startServer());
    ASSERT_TRUE(mdnsv6.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());
    mdnsv4.clearQueries();
    mdnsv6.clearQueries();

    const hostent* result;
    result = gethostbyname2("hello.local", AF_INET);
    ASSERT_TRUE(result == nullptr);

    result = gethostbyname2("hello.local", AF_INET6);
    ASSERT_TRUE(result == nullptr);
}

TEST_F(ResolverTest, MdnsGetAddrInfo) {
    constexpr char v6addr[] = "::127.0.0.3";
    constexpr char v4addr[] = "127.0.0.3";
    constexpr char host_name[] = "hello.local.";
    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService);
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService);
    mdnsv4.addMapping(host_name, ns_type::ns_t_a, v4addr);
    mdnsv6.addMapping(host_name, ns_type::ns_t_aaaa, v6addr);
    ASSERT_TRUE(mdnsv4.startServer());
    ASSERT_TRUE(mdnsv6.startServer());

    std::vector<bool> keep_listening_udp_enable = {false, true};
    for (int value : keep_listening_udp_enable) {
        if (value == true) {
            // Set keep_listening_udp enable
            ScopedSystemProperties sp(kKeepListeningUdpFlag, "1");
            // Re-setup test network to make experiment flag take effect.
            resetNetwork();
        }

        ASSERT_TRUE(mDnsClient.SetResolversForNetwork());
        static const struct TestConfig {
            int ai_family;
            const std::vector<std::string> expected_addr;
        } testConfigs[]{
                {AF_INET, {v4addr}},
                {AF_INET6, {v6addr}},
                {AF_UNSPEC, {v4addr, v6addr}},
        };

        for (const auto& config : testConfigs) {
            mdnsv4.clearQueries();
            mdnsv6.clearQueries();
            addrinfo hints = {.ai_family = config.ai_family, .ai_socktype = SOCK_DGRAM};
            ScopedAddrinfo result = safe_getaddrinfo("hello.local", nullptr, &hints);

            EXPECT_TRUE(result != nullptr);
            if (config.ai_family == AF_INET) {
                EXPECT_EQ(1U, GetNumQueries(mdnsv4, host_name));
                mdnsv4.clearQueries();
            } else if (config.ai_family == AF_INET6) {
                EXPECT_EQ(1U, GetNumQueries(mdnsv6, host_name));
                mdnsv6.clearQueries();
            } else if (config.ai_family == AF_UNSPEC) {
                EXPECT_EQ(1U, GetNumQueries(mdnsv4, host_name));
                EXPECT_EQ(1U, GetNumQueries(mdnsv6, host_name));
                mdnsv4.clearQueries();
                mdnsv6.clearQueries();
            }
            std::string result_str = ToString(result);
            EXPECT_THAT(ToStrings(result),
                        testing::UnorderedElementsAreArray(config.expected_addr));

            // Ensure the query results are still cached.
            result = safe_getaddrinfo("hello.local", nullptr, &hints);
            EXPECT_TRUE(result != nullptr);
            if (config.ai_family == AF_INET)
                EXPECT_EQ(0U, GetNumQueries(mdnsv4, host_name));
            else if (config.ai_family == AF_INET6)
                EXPECT_EQ(0U, GetNumQueries(mdnsv6, host_name));
            else if (config.ai_family == AF_UNSPEC) {
                EXPECT_EQ(0U, GetNumQueries(mdnsv4, host_name));
                EXPECT_EQ(0U, GetNumQueries(mdnsv6, host_name));
            }
            result_str = ToString(result);
            EXPECT_THAT(ToStrings(result),
                        testing::UnorderedElementsAreArray(config.expected_addr));
            ASSERT_TRUE(mDnsClient.resolvService()->flushNetworkCache(TEST_NETID).isOk());
        }
    }
}

TEST_F(ResolverTest, MdnsGetAddrInfo_transportTypes) {
    constexpr char v6addr[] = "::127.0.0.3";
    constexpr char v4addr[] = "127.0.0.3";
    constexpr char host_name[] = "hello.local.";
    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService);
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService);
    mdnsv4.addMapping(host_name, ns_type::ns_t_a, v4addr);
    mdnsv6.addMapping(host_name, ns_type::ns_t_aaaa, v6addr);
    ASSERT_TRUE(mdnsv4.startServer());
    ASSERT_TRUE(mdnsv6.startServer());

    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, v4addr},
            {host_name, ns_type::ns_t_aaaa, v6addr},
    };
    test::DNSResponder dns(v4addr);
    StartDns(dns, records);

    for (const auto& tpConfig : transportTypeConfig) {
        SCOPED_TRACE(fmt::format("transportTypes: [{}], useMdns: {}",
                                 fmt::join(tpConfig.transportTypes, ","), tpConfig.useMdns));
        ResolverParamsParcel setupParams = DnsResponderClient::GetDefaultResolverParamsParcel();
        setupParams.transportTypes = tpConfig.transportTypes;
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(setupParams));

        static const struct TestConfig {
            int ai_family;
            const std::vector<std::string> expected_addr;
        } testConfigs[]{
                {AF_INET, {v4addr}},
                {AF_INET6, {v6addr}},
                {AF_UNSPEC, {v4addr, v6addr}},
        };

        for (const auto& config : testConfigs) {
            addrinfo hints = {.ai_family = config.ai_family, .ai_socktype = SOCK_DGRAM};
            ScopedAddrinfo result = safe_getaddrinfo("hello.local", nullptr, &hints);

            EXPECT_TRUE(result != nullptr);
            if (tpConfig.useMdns) {
                if (config.ai_family == AF_INET) {
                    EXPECT_EQ(1U, GetNumQueries(mdnsv4, host_name));
                    EXPECT_EQ(0U, GetNumQueries(mdnsv6, host_name));
                } else if (config.ai_family == AF_INET6) {
                    EXPECT_EQ(0U, GetNumQueries(mdnsv4, host_name));
                    EXPECT_EQ(1U, GetNumQueries(mdnsv6, host_name));
                } else {
                    EXPECT_EQ(1U, GetNumQueries(mdnsv4, host_name));
                    EXPECT_EQ(1U, GetNumQueries(mdnsv6, host_name));
                }
                EXPECT_EQ(0U, GetNumQueries(dns, host_name));
            } else {
                EXPECT_EQ(0U, GetNumQueries(mdnsv4, host_name));
                EXPECT_EQ(0U, GetNumQueries(mdnsv6, host_name));
                if (config.ai_family == AF_INET || config.ai_family == AF_INET6) {
                    EXPECT_EQ(1U, GetNumQueries(dns, host_name));
                } else {
                    EXPECT_EQ(2U, GetNumQueries(dns, host_name));
                }
            }
            std::string result_str = ToString(result);
            EXPECT_THAT(ToStrings(result),
                        testing::UnorderedElementsAreArray(config.expected_addr));

            mdnsv4.clearQueries();
            mdnsv6.clearQueries();
            dns.clearQueries();
            ASSERT_TRUE(mDnsClient.resolvService()->flushNetworkCache(TEST_NETID).isOk());
        }
    }
}

TEST_F(ResolverTest, MdnsGetAddrInfo_InvalidSocketType) {
    constexpr char v6addr[] = "::127.0.0.3";
    constexpr char host_name[] = "hello.local.";

    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService);
    mdnsv6.addMapping(host_name, ns_type::ns_t_aaaa, v6addr);
    ASSERT_TRUE(mdnsv6.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    // TODO: Test other invalid socket types.
    const addrinfo hints = {
            .ai_family = AF_UNSPEC,
            .ai_socktype = SOCK_PACKET,
    };
    addrinfo* result = nullptr;
    // This is a valid hint, but the query won't be sent because the socket type is
    // not supported.
    EXPECT_EQ(EAI_NODATA, getaddrinfo("howdy.local", nullptr, &hints, &result));
    ScopedAddrinfo result_cleanup(result);
    EXPECT_EQ(nullptr, result);
}

TEST_F(ResolverTest, MdnsGetAddrInfo_cnames) {
    constexpr char v6addr[] = "::127.0.0.3";
    constexpr char v4addr[] = "127.0.0.3";
    constexpr char host_name[] = "hello.local.";
    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService);
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService);
    const std::vector<DnsRecord> records = {
            {"hi.local.", ns_type::ns_t_cname, "a.local."},
            {"a.local.", ns_type::ns_t_cname, "b.local."},
            {"b.local.", ns_type::ns_t_cname, "c.local."},
            {"c.local.", ns_type::ns_t_cname, "d.local."},
            {"d.local.", ns_type::ns_t_cname, "e.local."},
            {"e.local.", ns_type::ns_t_cname, host_name},
            {host_name, ns_type::ns_t_a, v4addr},
            {host_name, ns_type::ns_t_aaaa, v6addr},
    };
    for (const auto& r : records) {
        mdnsv4.addMapping(r.host_name, r.type, r.addr);
    }
    for (const auto& r : records) {
        mdnsv6.addMapping(r.host_name, r.type, r.addr);
    }
    ASSERT_TRUE(mdnsv4.startServer());
    ASSERT_TRUE(mdnsv6.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    static const struct TestConfig {
        int ai_family;
        const std::vector<std::string> expected_addr;
    } testConfigs[]{
            {AF_INET, {v4addr}},
            {AF_INET6, {v6addr}},
            {AF_UNSPEC, {v4addr, v6addr}},
    };
    for (const auto& config : testConfigs) {
        mdnsv4.clearQueries();
        mdnsv6.clearQueries();
        addrinfo hints = {.ai_family = config.ai_family, .ai_socktype = SOCK_DGRAM};
        ScopedAddrinfo result = safe_getaddrinfo("hi.local", nullptr, &hints);
        EXPECT_TRUE(result != nullptr);
        EXPECT_THAT(ToStrings(result), testing::UnorderedElementsAreArray(config.expected_addr));
    }
}

TEST_F(ResolverTest, MdnsGetAddrInfo_cnamesNoIpAddress) {
    constexpr char host_name[] = "hello.local.";
    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService);
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService);
    mdnsv4.addMapping(host_name, ns_type::ns_t_cname, "a.local.");
    mdnsv6.addMapping(host_name, ns_type::ns_t_cname, "a.local.");
    ASSERT_TRUE(mdnsv4.startServer());
    ASSERT_TRUE(mdnsv6.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    addrinfo hints = {.ai_family = AF_INET};
    ScopedAddrinfo result = safe_getaddrinfo("hello.local", nullptr, &hints);
    EXPECT_TRUE(result == nullptr);

    mdnsv4.clearQueries();
    hints = {.ai_family = AF_INET6};
    result = safe_getaddrinfo("hello.local", nullptr, &hints);
    EXPECT_TRUE(result == nullptr);

    mdnsv6.clearQueries();
    hints = {.ai_family = AF_UNSPEC};
    result = safe_getaddrinfo("hello.local", nullptr, &hints);
    EXPECT_TRUE(result == nullptr);
}

TEST_F(ResolverTest, MdnsGetAddrInfo_cnamesIllegalRdata) {
    constexpr char host_name[] = "hello.local.";
    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService);
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService);
    mdnsv4.addMapping(host_name, ns_type::ns_t_cname, ".!#?");
    mdnsv6.addMapping(host_name, ns_type::ns_t_cname, ".!#?");
    ASSERT_TRUE(mdnsv4.startServer());
    ASSERT_TRUE(mdnsv6.startServer());
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    addrinfo hints = {.ai_family = AF_INET};
    ScopedAddrinfo result = safe_getaddrinfo("hello.local", nullptr, &hints);
    EXPECT_TRUE(result == nullptr);

    mdnsv4.clearQueries();
    hints = {.ai_family = AF_INET6};
    result = safe_getaddrinfo("hello.local", nullptr, &hints);
    EXPECT_TRUE(result == nullptr);

    mdnsv6.clearQueries();
    hints = {.ai_family = AF_UNSPEC};
    result = safe_getaddrinfo("hello.local", nullptr, &hints);
    EXPECT_TRUE(result == nullptr);
}

// Test if .local resolution will try unicast when multicast is failed.
TEST_F(ResolverTest, MdnsGetAddrInfo_fallback) {
    constexpr char v6addr[] = "::1.2.3.4";
    constexpr char v4addr[] = "1.2.3.4";
    constexpr char host_name[] = "hello.local.";
    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService,
                              static_cast<ns_rcode>(-1));
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService, static_cast<ns_rcode>(-1));
    // Set unresponsive on multicast.
    mdnsv4.setResponseProbability(0.0);
    mdnsv6.setResponseProbability(0.0);
    ASSERT_TRUE(mdnsv4.startServer());
    ASSERT_TRUE(mdnsv6.startServer());

    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, v4addr},
            {host_name, ns_type::ns_t_aaaa, v6addr},
    };
    test::DNSResponder dns("127.0.0.3");
    StartDns(dns, records);
    ASSERT_TRUE(mDnsClient.SetResolversForNetwork());

    static const struct TestConfig {
        int ai_family;
        const std::vector<std::string> expected_addr;
    } testConfigs[]{
            {AF_INET, {v4addr}},
            {AF_INET6, {v6addr}},
            {AF_UNSPEC, {v4addr, v6addr}},
    };

    for (const auto& config : testConfigs) {
        SCOPED_TRACE(fmt::format("family: {}", config.ai_family));
        addrinfo hints = {.ai_family = config.ai_family, .ai_socktype = SOCK_DGRAM};
        ScopedAddrinfo result = safe_getaddrinfo("hello.local", nullptr, &hints);
        EXPECT_TRUE(result != nullptr);
        if (config.ai_family == AF_INET) {
            EXPECT_EQ(1U, GetNumQueries(mdnsv4, host_name));
            EXPECT_EQ(0U, GetNumQueries(mdnsv6, host_name));
            EXPECT_EQ(1U, GetNumQueries(dns, host_name));
        } else if (config.ai_family == AF_INET6) {
            EXPECT_EQ(0U, GetNumQueries(mdnsv4, host_name));
            EXPECT_EQ(1U, GetNumQueries(mdnsv6, host_name));
            EXPECT_EQ(1U, GetNumQueries(dns, host_name));
        } else {
            EXPECT_EQ(1U, GetNumQueries(mdnsv4, host_name));
            EXPECT_EQ(1U, GetNumQueries(mdnsv6, host_name));
            EXPECT_EQ(2U, GetNumQueries(dns, host_name));
        }
        EXPECT_THAT(ToStrings(result), testing::UnorderedElementsAreArray(config.expected_addr));

        mdnsv4.clearQueries();
        mdnsv6.clearQueries();
        dns.clearQueries();
        ASSERT_TRUE(mDnsClient.resolvService()->flushNetworkCache(TEST_NETID).isOk());
    }
}

// ResolverMultinetworkTest is used to verify multinetwork functionality. Here's how it works:
// The resolver sends queries to address A, and then there will be a TunForwarder helping forward
// the packets to address B, which is the address on which the testing server is listening. The
// answer packets responded from the testing server go through the reverse path back to the
// resolver.
//
// To achieve the that, it needs to set up a interface with routing rules. Tests are not
// supposed to initiate DNS servers on their own; instead, some utilities are added to the class to
// help the setup.
//
// An example of how to use it:
// TEST_F() {
//     ScopedPhysicalNetwork network = CreateScopedPhysicalNetwork(V4);
//     network.init();
//
//     auto dns = network.addIpv4Dns();
//     StartDns(dns.dnsServer, {});
//
//     network.setDnsConfiguration();
//     network.startTunForwarder();
//
//     // Send queries here
// }

class ResolverMultinetworkTest : public ResolverTest {
  protected:
    enum class ConnectivityType { V4, V6, V4V6 };
    static constexpr int TEST_NETID_BASE = 10000;

    struct DnsServerPair {
        DnsServerPair(std::shared_ptr<test::DNSResponder> server, std::string addr)
            : dnsServer(server), dnsAddr(addr) {}
        std::shared_ptr<test::DNSResponder> dnsServer;
        std::string dnsAddr;  // The DNS server address used for setResolverConfiguration().
        // TODO: Add test::DnsTlsFrontend* and std::string for DoT.
    };

    class ScopedNetwork {
      public:
        ScopedNetwork(unsigned netId, ConnectivityType type, INetd* netdSrv,
                      IDnsResolver* dnsResolvSrv, const char* networkName)
            : mNetId(netId),
              mConnectivityType(type),
              mNetdSrv(netdSrv),
              mDnsResolvSrv(dnsResolvSrv),
              mNetworkName(networkName) {
            mIfname = fmt::format("testtun{}", netId);
        }
        virtual ~ScopedNetwork() {
            if (mNetdSrv != nullptr) mNetdSrv->networkDestroy(mNetId);
            if (mDnsResolvSrv != nullptr) mDnsResolvSrv->destroyNetworkCache(mNetId);
        }

        Result<void> init();
        Result<DnsServerPair> addIpv4Dns() { return addDns(ConnectivityType::V4); }
        Result<DnsServerPair> addIpv6Dns() { return addDns(ConnectivityType::V6); }
        bool startTunForwarder() { return mTunForwarder->startForwarding(); }
        bool setDnsConfiguration() const;
        bool clearDnsConfiguration() const;
        unsigned netId() const { return mNetId; }
        std::string name() const { return mNetworkName; }
        Result<void> addUser(uid_t uid) const { return addUidRange(uid, uid); }
        Result<void> addUidRange(uid_t from, uid_t to) const {
            if (auto r = mNetdSrv->networkAddUidRanges(mNetId, {makeUidRangeParcel(from, to)});
                !r.isOk()) {
                return Error() << r.getMessage();
            }
            return {};
        }

        Result<void> addUserFromParcel(uid_t uid, int32_t subPriority) const {
            return addUidRangeFromParcel(uid, uid, subPriority);
        }

        Result<void> addUidRangeFromParcel(uid_t from, uid_t to, int32_t subPriority) const {
            NativeUidRangeConfig cfg =
                    makeNativeUidRangeConfig(mNetId, {makeUidRangeParcel(from, to)}, subPriority);
            if (auto r = mNetdSrv->networkAddUidRangesParcel(cfg); !r.isOk()) {
                return Error() << r.getMessage();
            }
            return {};
        }

        const std::string& ifname() { return mIfname; }
        // Assuming mNetId is unique during ResolverMultinetworkTest, make the
        // address based on it to avoid conflicts.
        std::string makeIpv4AddrString(uint8_t n) const {
            return fmt::format("192.168.{}.{}", (mNetId - TEST_NETID_BASE), n);
        }
        std::string makeIpv6AddrString(uint8_t n) const {
            return fmt::format("2001:db8:{}::{}", (mNetId - TEST_NETID_BASE), n);
        }

      protected:
        // Subclasses should implement it to decide which network should be create.
        virtual Result<void> createNetwork() const = 0;

        const unsigned mNetId;
        const ConnectivityType mConnectivityType;
        INetd* mNetdSrv;
        IDnsResolver* mDnsResolvSrv;
        const std::string mNetworkName;
        std::string mIfname;
        std::unique_ptr<TunForwarder> mTunForwarder;
        std::vector<DnsServerPair> mDnsServerPairs;

      private:
        Result<DnsServerPair> addDns(ConnectivityType connectivity);
    };

    class ScopedPhysicalNetwork : public ScopedNetwork {
      public:
        ScopedPhysicalNetwork(unsigned netId, const char* networkName)
            : ScopedNetwork(netId, ConnectivityType::V4V6, nullptr, nullptr, networkName) {}
        ScopedPhysicalNetwork(unsigned netId, ConnectivityType type, INetd* netdSrv,
                              IDnsResolver* dnsResolvSrv, const char* name = "Physical")
            : ScopedNetwork(netId, type, netdSrv, dnsResolvSrv, name) {}

      protected:
        Result<void> createNetwork() const override {
            ::ndk::ScopedAStatus r;
            if (DnsResponderClient::isRemoteVersionSupported(mNetdSrv, 6)) {
                const auto& config = DnsResponderClient::makeNativeNetworkConfig(
                        mNetId, NativeNetworkType::PHYSICAL, INetd::PERMISSION_NONE,
                        /*secure=*/false);
                r = mNetdSrv->networkCreate(config);
            } else {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
                r = mNetdSrv->networkCreatePhysical(mNetId, INetd::PERMISSION_NONE);
#pragma clang diagnostic pop
            }

            if (!r.isOk()) {
                return Error() << r.getMessage();
            }
            return {};
        }
    };

    class ScopedVirtualNetwork : public ScopedNetwork {
      public:
        ScopedVirtualNetwork(unsigned netId, ConnectivityType type, INetd* netdSrv,
                             IDnsResolver* dnsResolvSrv, const char* name, bool isSecure)
            : ScopedNetwork(netId, type, netdSrv, dnsResolvSrv, name), mIsSecure(isSecure) {
            if (android::modules::sdklevel::IsAtLeastT()) {
                mFw = Firewall::getInstance();
            }
        }
        ~ScopedVirtualNetwork() {
            if (!mVpnIsolationUids.empty()) {
                const std::vector<int> tmpUids(mVpnIsolationUids.begin(), mVpnIsolationUids.end());
                mNetdSrv->firewallRemoveUidInterfaceRules(tmpUids);
            }
        }
        // Enable VPN isolation. Ensures that uid can only receive packets on mIfname.
        Result<void> enableVpnIsolation(int uid) {
            if (android::modules::sdklevel::IsAtLeastT()) {
                if (auto r = mFw->addUidInterfaceRules(mIfname, {uid}); !r.ok()) {
                    return r;
                }
            } else if (auto r = mNetdSrv->firewallAddUidInterfaceRules(mIfname, {uid}); !r.isOk()) {
                return Error() << r.getMessage();
            }
            mVpnIsolationUids.insert(uid);
            return {};
        }
        Result<void> disableVpnIsolation(int uid) {
            if (android::modules::sdklevel::IsAtLeastT()) {
                if (auto r = mFw->removeUidInterfaceRules({uid}); !r.ok()) {
                    return r;
                }
            } else if (auto r = mNetdSrv->firewallRemoveUidInterfaceRules({uid}); !r.isOk()) {
                return Error() << r.getMessage();
            }
            mVpnIsolationUids.erase(uid);
            return {};
        }

      protected:
        Result<void> createNetwork() const override {
            ::ndk::ScopedAStatus r;
            if (DnsResponderClient::isRemoteVersionSupported(mNetdSrv, 6)) {
                const auto& config = DnsResponderClient::makeNativeNetworkConfig(
                        mNetId, NativeNetworkType::VIRTUAL, INetd::PERMISSION_NONE, mIsSecure);
                r = mNetdSrv->networkCreate(config);
            } else {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
                r = mNetdSrv->networkCreateVpn(mNetId, mIsSecure);
#pragma clang diagnostic pop
            }

            if (!r.isOk()) {
                return Error() << r.getMessage();
            }
            return {};
        }

        bool mIsSecure = false;
        std::unordered_set<int> mVpnIsolationUids;
        Firewall* mFw;
    };

    void SetUp() override {
        ResolverTest::SetUp();
        ASSERT_NE(mDnsClient.netdService(), nullptr);
        ASSERT_NE(mDnsClient.resolvService(), nullptr);
    }

    void TearDown() override {
        ResolverTest::TearDown();
        // Restore default network
        if (mStoredDefaultNetwork >= 0) {
            mDnsClient.netdService()->networkSetDefault(mStoredDefaultNetwork);
        }
    }

    ScopedPhysicalNetwork CreateScopedPhysicalNetwork(ConnectivityType type,
                                                      const char* name = "Physical") {
        return {getFreeNetId(), type, mDnsClient.netdService(), mDnsClient.resolvService(), name};
    }
    ScopedVirtualNetwork CreateScopedVirtualNetwork(ConnectivityType type, bool isSecure,
                                                    const char* name = "Virtual") {
        return {getFreeNetId(), type,    mDnsClient.netdService(), mDnsClient.resolvService(),
                name,           isSecure};
    }
    void StartDns(test::DNSResponder& dns, const std::vector<DnsRecord>& records);
    void setDefaultNetwork(int netId) {
        // Save current default network at the first call.
        std::call_once(defaultNetworkFlag, [&]() {
            ASSERT_TRUE(mDnsClient.netdService()->networkGetDefault(&mStoredDefaultNetwork).isOk());
        });
        ASSERT_TRUE(mDnsClient.netdService()->networkSetDefault(netId).isOk());
    }
    unsigned getFreeNetId() {
        if (mNextNetId == TEST_NETID_BASE + 256) mNextNetId = TEST_NETID_BASE;
        return mNextNetId++;
    }
    Result<std::shared_ptr<test::DNSResponder>> setupDns(ConnectivityType type, ScopedNetwork* nw,
                                                         const char* host_name,
                                                         const char* ipv4_addr,
                                                         const char* ipv6_addr);

    void expectDnsQueryCountsFn(std::shared_ptr<test::DNSResponder> dnsServer,
                                const char* host_name, size_t count, unsigned expectedDnsNetId) {
        EXPECT_EQ(GetNumQueries(*dnsServer, host_name), count);
        EXPECT_TRUE(mDnsClient.resolvService()->flushNetworkCache(expectedDnsNetId).isOk());
        dnsServer->clearQueries();
    }

    static NativeUidRangeConfig makeNativeUidRangeConfig(unsigned netId,
                                                         std::vector<UidRangeParcel> uidRanges,
                                                         int32_t subPriority) {
        NativeUidRangeConfig res;
        res.netId = netId;
        res.uidRanges = std::move(uidRanges);
        res.subPriority = subPriority;

        return res;
    }

  private:
    // Use a different netId because this class inherits from the class ResolverTest which
    // always creates TEST_NETID in setup. It's incremented when CreateScoped{Physical,
    // Virtual}Network() is called.
    // Note: 255 is the maximum number of (mNextNetId - TEST_NETID_BASE) here as mNextNetId
    // is used to create address.
    unsigned mNextNetId = TEST_NETID_BASE;
    // Use -1 to represent that default network was not modified because
    // real netId must be an unsigned value.
    int mStoredDefaultNetwork = -1;
    std::once_flag defaultNetworkFlag;
};

Result<void> ResolverMultinetworkTest::ScopedNetwork::init() {
    if (mNetdSrv == nullptr || mDnsResolvSrv == nullptr) return Error() << "srv not available";
    unique_fd ufd = TunForwarder::createTun(mIfname);
    if (!ufd.ok()) {
        return Errorf("createTun for {} failed", mIfname);
    }
    mTunForwarder = std::make_unique<TunForwarder>(std::move(ufd));

    if (auto r = createNetwork(); !r.ok()) {
        return r;
    }
    if (auto r = mDnsResolvSrv->createNetworkCache(mNetId); !r.isOk()) {
        return Error() << r.getMessage();
    }
    if (auto r = mNetdSrv->networkAddInterface(mNetId, mIfname); !r.isOk()) {
        return Error() << r.getMessage();
    }

    if (mConnectivityType == ConnectivityType::V4 || mConnectivityType == ConnectivityType::V4V6) {
        const std::string v4Addr = makeIpv4AddrString(1);
        if (auto r = mNetdSrv->interfaceAddAddress(mIfname, v4Addr, 32); !r.isOk()) {
            return Error() << r.getMessage();
        }
        if (auto r = mNetdSrv->networkAddRoute(mNetId, mIfname, "0.0.0.0/0", ""); !r.isOk()) {
            return Error() << r.getMessage();
        }
    }
    if (mConnectivityType == ConnectivityType::V6 || mConnectivityType == ConnectivityType::V4V6) {
        const std::string v6Addr = makeIpv6AddrString(1);
        if (auto r = mNetdSrv->interfaceAddAddress(mIfname, v6Addr, 128); !r.isOk()) {
            return Error() << r.getMessage();
        }
        if (auto r = mNetdSrv->networkAddRoute(mNetId, mIfname, "::/0", ""); !r.isOk()) {
            return Error() << r.getMessage();
        }
    }

    return {};
}

void ResolverMultinetworkTest::StartDns(test::DNSResponder& dns,
                                        const std::vector<DnsRecord>& records) {
    ResolverTest::StartDns(dns, records);

    // Bind the DNSResponder's sockets to the network if specified.
    if (std::optional<unsigned> netId = dns.getNetwork(); netId.has_value()) {
        setNetworkForSocket(netId.value(), dns.getUdpSocket());
        setNetworkForSocket(netId.value(), dns.getTcpSocket());
    }
}

Result<std::shared_ptr<test::DNSResponder>> ResolverMultinetworkTest::setupDns(
        ConnectivityType type, ScopedNetwork* nw, const char* host_name, const char* ipv4_addr,
        const char* ipv6_addr) {
    // Add a testing DNS server to networks.
    const Result<DnsServerPair> dnsSvPair =
            (type == ConnectivityType::V4) ? nw->addIpv4Dns() : nw->addIpv6Dns();
    if (!dnsSvPair.ok()) return Error() << dnsSvPair.error();

    StartDns(*dnsSvPair->dnsServer,
             {{host_name, ns_type::ns_t_a, ipv4_addr}, {host_name, ns_type::ns_t_aaaa, ipv6_addr}});
    if (!nw->setDnsConfiguration()) return Error() << "setDnsConfiguration() failed";
    if (!nw->startTunForwarder()) return Error() << "startTunForwarder() failed";
    return dnsSvPair->dnsServer;
}

Result<ResolverMultinetworkTest::DnsServerPair> ResolverMultinetworkTest::ScopedNetwork::addDns(
        ConnectivityType type) {
    const int index = mDnsServerPairs.size();
    const int prefixLen = (type == ConnectivityType::V4) ? 32 : 128;

    const std::function<std::string(unsigned)> makeIpString =
            std::bind((type == ConnectivityType::V4) ? &ScopedNetwork::makeIpv4AddrString
                                                     : &ScopedNetwork::makeIpv6AddrString,
                      this, std::placeholders::_1);

    std::string src1 = makeIpString(1);            // The address from which the resolver will send.
    std::string dst1 = makeIpString(
            index + 100 +
            (mNetId - TEST_NETID_BASE));           // The address to which the resolver will send.
    std::string src2 = dst1;                       // The address translated from src1.
    std::string dst2 = makeIpString(
            index + 200 + (mNetId - TEST_NETID_BASE));  // The address translated from dst2.

    if (!mTunForwarder->addForwardingRule({src1, dst1}, {src2, dst2}) ||
        !mTunForwarder->addForwardingRule({dst2, src2}, {dst1, src1})) {
        return Errorf("Failed to add the rules ({}, {}, {}, {})", src1, dst1, src2, dst2);
    }

    if (!mNetdSrv->interfaceAddAddress(mIfname, dst2, prefixLen).isOk()) {
        return Errorf("interfaceAddAddress({}, {}, {}) failed", mIfname, dst2, prefixLen);
    }

    return mDnsServerPairs.emplace_back(std::make_shared<test::DNSResponder>(mNetId, dst2), dst1);
}

bool ResolverMultinetworkTest::ScopedNetwork::setDnsConfiguration() const {
    if (mDnsResolvSrv == nullptr) return false;
    ResolverParamsParcel parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    parcel.tlsServers.clear();
    parcel.netId = mNetId;
    parcel.servers.clear();
    for (const auto& pair : mDnsServerPairs) {
        parcel.servers.push_back(pair.dnsAddr);
    }
    return mDnsResolvSrv->setResolverConfiguration(parcel).isOk();
}

bool ResolverMultinetworkTest::ScopedNetwork::clearDnsConfiguration() const {
    if (mDnsResolvSrv == nullptr) return false;
    return mDnsResolvSrv->destroyNetworkCache(mNetId).isOk() &&
           mDnsResolvSrv->createNetworkCache(mNetId).isOk();
}

namespace {

// Convenient wrapper for making getaddrinfo call like framework.
Result<ScopedAddrinfo> android_getaddrinfofornet_wrapper(const char* name, int netId) {
    // Use the same parameter as libcore/ojluni/src/main/java/java/net/Inet6AddressImpl.java.
    static const addrinfo hints = {
            .ai_flags = AI_ADDRCONFIG,
            .ai_family = AF_UNSPEC,
            .ai_socktype = SOCK_STREAM,
    };
    addrinfo* result = nullptr;
    if (int r = android_getaddrinfofornet(name, nullptr, &hints, netId, MARK_UNSET, &result)) {
        return Error() << r;
    }
    return ScopedAddrinfo(result);
}

void expectDnsWorksForUid(const char* name, unsigned netId, uid_t uid,
                          const std::vector<std::string>& expectedResult) {
    ScopedChangeUID scopedChangeUID(uid);
    auto result = android_getaddrinfofornet_wrapper(name, netId);
    ASSERT_RESULT_OK(result);
    ScopedAddrinfo ai_result(std::move(result.value()));
    std::vector<std::string> result_strs = ToStrings(ai_result);
    EXPECT_THAT(result_strs, testing::UnorderedElementsAreArray(expectedResult));
}

void expectDnsFailedForUid(const char* name, unsigned netId, uid_t uid) {
    ScopedChangeUID scopedChangeUID(uid);
    ASSERT_FALSE(android_getaddrinfofornet_wrapper(name, netId).ok());
}

}  // namespace

TEST_F(ResolverMultinetworkTest, GetAddrInfo_AI_ADDRCONFIG) {
    constexpr char host_name[] = "ohayou.example.com.";

    const std::array<ConnectivityType, 3> allTypes = {
            ConnectivityType::V4,
            ConnectivityType::V6,
            ConnectivityType::V4V6,
    };
    for (const auto& type : allTypes) {
        SCOPED_TRACE(fmt::format("ConnectivityType: {}", static_cast<int>(type)));

        // Create a network.
        ScopedPhysicalNetwork network = CreateScopedPhysicalNetwork(type);
        ASSERT_RESULT_OK(network.init());

        // Add a testing DNS server.
        const Result<DnsServerPair> dnsPair =
                (type == ConnectivityType::V4) ? network.addIpv4Dns() : network.addIpv6Dns();
        ASSERT_RESULT_OK(dnsPair);
        StartDns(*dnsPair->dnsServer, {{host_name, ns_type::ns_t_a, "192.0.2.0"},
                                       {host_name, ns_type::ns_t_aaaa, "2001:db8:cafe:d00d::31"}});

        // Set up resolver and start forwarding.
        ASSERT_TRUE(network.setDnsConfiguration());
        ASSERT_TRUE(network.startTunForwarder());

        auto result = android_getaddrinfofornet_wrapper(host_name, network.netId());
        ASSERT_RESULT_OK(result);
        ScopedAddrinfo ai_result(std::move(result.value()));
        std::vector<std::string> result_strs = ToStrings(ai_result);
        std::vector<std::string> expectedResult;
        size_t expectedQueries = 0;

        if (type == ConnectivityType::V6 || type == ConnectivityType::V4V6) {
            expectedResult.emplace_back("2001:db8:cafe:d00d::31");
            expectedQueries++;
        }
        if (type == ConnectivityType::V4 || type == ConnectivityType::V4V6) {
            expectedResult.emplace_back("192.0.2.0");
            expectedQueries++;
        }
        EXPECT_THAT(result_strs, testing::UnorderedElementsAreArray(expectedResult));
        EXPECT_EQ(GetNumQueries(*dnsPair->dnsServer, host_name), expectedQueries);
    }
}

TEST_F(ResolverMultinetworkTest, NetworkDestroyedDuringQueryInFlight) {
    constexpr char host_name[] = "ohayou.example.com.";

    // Create a network and add an ipv4 DNS server.
    auto network = std::make_unique<ScopedPhysicalNetwork>(getFreeNetId(), ConnectivityType::V4V6,
                                                           mDnsClient.netdService(),
                                                           mDnsClient.resolvService());
    ASSERT_RESULT_OK(network->init());
    const Result<DnsServerPair> dnsPair = network->addIpv4Dns();
    ASSERT_RESULT_OK(dnsPair);

    // Set the DNS server unresponsive.
    dnsPair->dnsServer->setResponseProbability(0.0);
    dnsPair->dnsServer->setErrorRcode(static_cast<ns_rcode>(-1));
    StartDns(*dnsPair->dnsServer, {});

    // Set up resolver and start forwarding.
    ASSERT_TRUE(network->setDnsConfiguration());
    ASSERT_TRUE(network->startTunForwarder());

    // Expect the things happening in order:
    // 1. The thread sends the query to the dns server which is unresponsive.
    // 2. The network is destroyed while the thread is waiting for the response from the dns server.
    // 3. After the dns server timeout, the thread retries but fails to connect.
    std::thread lookup([&]() {
        int fd = resNetworkQuery(network->netId(), host_name, ns_c_in, ns_t_a, 0);
        EXPECT_TRUE(fd != -1);
        expectAnswersNotValid(fd, -ETIMEDOUT);
    });

    // Tear down the network as soon as the dns server receives the query.
    const auto condition = [&]() { return GetNumQueries(*dnsPair->dnsServer, host_name) == 1U; };
    EXPECT_TRUE(PollForCondition(condition));
    network.reset();

    lookup.join();
}

TEST_F(ResolverMultinetworkTest, OneCachePerNetwork) {
    SKIP_IF_REMOTE_VERSION_LESS_THAN(mDnsClient.resolvService(), 4);
    constexpr char host_name[] = "ohayou.example.com.";

    ScopedPhysicalNetwork network1 = CreateScopedPhysicalNetwork(ConnectivityType::V4V6);
    ScopedPhysicalNetwork network2 = CreateScopedPhysicalNetwork(ConnectivityType::V4V6);
    ASSERT_RESULT_OK(network1.init());
    ASSERT_RESULT_OK(network2.init());

    const Result<DnsServerPair> dnsPair1 = network1.addIpv4Dns();
    const Result<DnsServerPair> dnsPair2 = network2.addIpv4Dns();
    ASSERT_RESULT_OK(dnsPair1);
    ASSERT_RESULT_OK(dnsPair2);
    StartDns(*dnsPair1->dnsServer, {{host_name, ns_type::ns_t_a, "192.0.2.0"}});
    StartDns(*dnsPair2->dnsServer, {{host_name, ns_type::ns_t_a, "192.0.2.1"}});

    // Set up resolver for network 1 and start forwarding.
    ASSERT_TRUE(network1.setDnsConfiguration());
    ASSERT_TRUE(network1.startTunForwarder());

    // Set up resolver for network 2 and start forwarding.
    ASSERT_TRUE(network2.setDnsConfiguration());
    ASSERT_TRUE(network2.startTunForwarder());

    // Send the same queries to both networks.
    int fd1 = resNetworkQuery(network1.netId(), host_name, ns_c_in, ns_t_a, 0);
    int fd2 = resNetworkQuery(network2.netId(), host_name, ns_c_in, ns_t_a, 0);

    expectAnswersValid(fd1, AF_INET, "192.0.2.0");
    expectAnswersValid(fd2, AF_INET, "192.0.2.1");
    EXPECT_EQ(GetNumQueries(*dnsPair1->dnsServer, host_name), 1U);
    EXPECT_EQ(GetNumQueries(*dnsPair2->dnsServer, host_name), 1U);

    // Flush the cache of network 1, and send the queries again.
    EXPECT_TRUE(mDnsClient.resolvService()->flushNetworkCache(network1.netId()).isOk());
    fd1 = resNetworkQuery(network1.netId(), host_name, ns_c_in, ns_t_a, 0);
    fd2 = resNetworkQuery(network2.netId(), host_name, ns_c_in, ns_t_a, 0);

    expectAnswersValid(fd1, AF_INET, "192.0.2.0");
    expectAnswersValid(fd2, AF_INET, "192.0.2.1");
    EXPECT_EQ(GetNumQueries(*dnsPair1->dnsServer, host_name), 2U);
    EXPECT_EQ(GetNumQueries(*dnsPair2->dnsServer, host_name), 1U);
}

TEST_F(ResolverMultinetworkTest, DnsWithVpn) {
    SKIP_IF_BPF_NOT_SUPPORTED;
    SKIP_IF_REMOTE_VERSION_LESS_THAN(mDnsClient.resolvService(), 4);
    constexpr char host_name[] = "ohayou.example.com.";
    constexpr char ipv4_addr[] = "192.0.2.0";
    constexpr char ipv6_addr[] = "2001:db8:cafe:d00d::31";

    const std::pair<ConnectivityType, std::vector<std::string>> testPairs[] = {
            {ConnectivityType::V4, {ipv4_addr}},
            {ConnectivityType::V6, {ipv6_addr}},
            {ConnectivityType::V4V6, {ipv6_addr, ipv4_addr}},
    };
    for (const auto& [type, result] : testPairs) {
        SCOPED_TRACE(fmt::format("ConnectivityType: {}", static_cast<int>(type)));

        // Create a network.
        ScopedPhysicalNetwork underlyingNetwork = CreateScopedPhysicalNetwork(type, "Underlying");
        ScopedVirtualNetwork bypassableVpnNetwork =
                CreateScopedVirtualNetwork(type, false, "BypassableVpn");
        ScopedVirtualNetwork secureVpnNetwork = CreateScopedVirtualNetwork(type, true, "SecureVpn");

        ASSERT_RESULT_OK(underlyingNetwork.init());
        ASSERT_RESULT_OK(bypassableVpnNetwork.init());
        ASSERT_RESULT_OK(secureVpnNetwork.init());
        ASSERT_RESULT_OK(bypassableVpnNetwork.addUser(TEST_UID));
        ASSERT_RESULT_OK(secureVpnNetwork.addUser(TEST_UID2));

        // Set up resolver and start forwarding for networks.
        auto underlyingNwDnsSv =
                setupDns(type, &underlyingNetwork, host_name, ipv4_addr, ipv6_addr);
        ASSERT_RESULT_OK(underlyingNwDnsSv);
        auto bypassableVpnDnsSv =
                setupDns(type, &bypassableVpnNetwork, host_name, ipv4_addr, ipv6_addr);
        ASSERT_RESULT_OK(bypassableVpnDnsSv);
        auto secureVpnDnsSv = setupDns(type, &secureVpnNetwork, host_name, ipv4_addr, ipv6_addr);
        ASSERT_RESULT_OK(secureVpnDnsSv);

        setDefaultNetwork(underlyingNetwork.netId());
        const unsigned underlyingNetId = underlyingNetwork.netId();
        const unsigned bypassableVpnNetId = bypassableVpnNetwork.netId();
        const unsigned secureVpnNetId = secureVpnNetwork.netId();
        // We've called setNetworkForProcess in SetupOemNetwork, so reset to default first.
        ScopedSetNetworkForProcess scopedSetNetworkForProcess(NETID_UNSET);

        // Create a object to represent default network, do not init it.
        ScopedPhysicalNetwork defaultNetwork{NETID_UNSET, "Default"};

        // Test VPN with DNS server under 4 different network selection scenarios.
        // See the test config for the expectation.
        const struct TestConfig {
            ScopedNetwork* selectedNetwork;
            unsigned expectedDnsNetId;
            std::shared_ptr<test::DNSResponder> expectedDnsServer;
        } vpnWithDnsServerConfigs[]{
                // clang-format off
                // Queries use the bypassable VPN by default.
                {&defaultNetwork,       bypassableVpnNetId, *bypassableVpnDnsSv},
                // Choosing the underlying network works because the VPN is bypassable.
                {&underlyingNetwork,    underlyingNetId,    *underlyingNwDnsSv},
                // Selecting the VPN sends the query on the VPN.
                {&bypassableVpnNetwork, bypassableVpnNetId, *bypassableVpnDnsSv},
                // TEST_UID does not have access to the secure VPN.
                {&secureVpnNetwork,     bypassableVpnNetId, *bypassableVpnDnsSv},
                // clang-format on
        };
        for (const auto& config : vpnWithDnsServerConfigs) {
            SCOPED_TRACE(fmt::format("Bypassble VPN with DnsServer, selectedNetwork = {}",
                                     config.selectedNetwork->name()));
            expectDnsWorksForUid(host_name, config.selectedNetwork->netId(), TEST_UID, result);
            expectDnsQueryCountsFn(config.expectedDnsServer, host_name, result.size(),
                                   config.expectedDnsNetId);
        }

        std::vector<ScopedNetwork*> nwVec{&defaultNetwork, &underlyingNetwork,
                                          &bypassableVpnNetwork, &secureVpnNetwork};
        // Test the VPN without DNS server with the same combination as before.
        ASSERT_TRUE(bypassableVpnNetwork.clearDnsConfiguration());
        // Test bypassable VPN, TEST_UID
        for (const auto* selectedNetwork : nwVec) {
            SCOPED_TRACE(fmt::format("Bypassble VPN without DnsServer, selectedNetwork = {}",
                                     selectedNetwork->name()));
            expectDnsWorksForUid(host_name, selectedNetwork->netId(), TEST_UID, result);
            expectDnsQueryCountsFn(*underlyingNwDnsSv, host_name, result.size(), underlyingNetId);
        }

        // The same test scenario as before plus enableVpnIsolation for secure VPN, TEST_UID2.
        for (bool enableVpnIsolation : {false, true}) {
            SCOPED_TRACE(fmt::format("enableVpnIsolation = {}", enableVpnIsolation));
            if (enableVpnIsolation) {
                EXPECT_RESULT_OK(secureVpnNetwork.enableVpnIsolation(TEST_UID2));
            }

            // Test secure VPN without DNS server.
            ASSERT_TRUE(secureVpnNetwork.clearDnsConfiguration());
            for (const auto* selectedNetwork : nwVec) {
                SCOPED_TRACE(fmt::format("Secure VPN without DnsServer, selectedNetwork = {}",
                                         selectedNetwork->name()));
                expectDnsWorksForUid(host_name, selectedNetwork->netId(), TEST_UID2, result);
                expectDnsQueryCountsFn(*underlyingNwDnsSv, host_name, result.size(),
                                       underlyingNetId);
            }

            // Test secure VPN with DNS server.
            ASSERT_TRUE(secureVpnNetwork.setDnsConfiguration());
            for (const auto* selectedNetwork : nwVec) {
                SCOPED_TRACE(fmt::format("Secure VPN with DnsServer, selectedNetwork = {}",
                                         selectedNetwork->name()));
                expectDnsWorksForUid(host_name, selectedNetwork->netId(), TEST_UID2, result);
                expectDnsQueryCountsFn(*secureVpnDnsSv, host_name, result.size(), secureVpnNetId);
            }

            if (enableVpnIsolation) {
                EXPECT_RESULT_OK(secureVpnNetwork.disableVpnIsolation(TEST_UID2));
            }
        }
    }
}

// verify per-application default network selection on DNS.
TEST_F(ResolverMultinetworkTest, PerAppDefaultNetwork) {
    // Netd supports uid ranges on physical network from v6.
    SKIP_IF_REMOTE_VERSION_LESS_THAN(mDnsClient.netdService(), 6);

    constexpr char host_name[] = "ohayou.example.com.";
    constexpr char ipv4_addr[] = "192.0.2.0";
    constexpr char ipv6_addr[] = "2001:db8:cafe:d00d::31";

    const std::pair<ConnectivityType, std::vector<std::string>> testPairs[] = {
            {ConnectivityType::V4, {ipv4_addr}},
            {ConnectivityType::V6, {ipv6_addr}},
            {ConnectivityType::V4V6, {ipv6_addr, ipv4_addr}},
    };
    for (const auto& [ipVersion, expectedDnsReply] : testPairs) {
        SCOPED_TRACE(fmt::format("ConnectivityType: {}", static_cast<int>(ipVersion)));

        // Create networks.
        ScopedPhysicalNetwork sysDefaultNetwork =
                CreateScopedPhysicalNetwork(ipVersion, "SysDefault");
        ScopedPhysicalNetwork appDefaultNetwork =
                CreateScopedPhysicalNetwork(ipVersion, "AppDefault");
        ScopedVirtualNetwork vpn = CreateScopedVirtualNetwork(ipVersion, false, "Vpn");

        ASSERT_RESULT_OK(sysDefaultNetwork.init());
        ASSERT_RESULT_OK(appDefaultNetwork.init());
        ASSERT_RESULT_OK(vpn.init());

        // Set up resolver and start forwarding for networks.
        auto sysDefaultNwDnsSv =
                setupDns(ipVersion, &sysDefaultNetwork, host_name, ipv4_addr, ipv6_addr);
        ASSERT_RESULT_OK(sysDefaultNwDnsSv);
        auto appDefaultNwDnsSv =
                setupDns(ipVersion, &appDefaultNetwork, host_name, ipv4_addr, ipv6_addr);
        ASSERT_RESULT_OK(appDefaultNwDnsSv);
        auto vpnDnsSv = setupDns(ipVersion, &vpn, host_name, ipv4_addr, ipv6_addr);
        ASSERT_RESULT_OK(vpnDnsSv);

        const unsigned systemDefaultNetId = sysDefaultNetwork.netId();
        const unsigned appDefaultNetId = appDefaultNetwork.netId();
        const unsigned vpnNetId = vpn.netId();

        setDefaultNetwork(systemDefaultNetId);
        EXPECT_TRUE(
                mDnsClient.netdService()
                        ->networkSetPermissionForNetwork(appDefaultNetId, INetd::PERMISSION_SYSTEM)
                        .isOk());

        // We've called setNetworkForProcess in SetupOemNetwork, reset to default first.
        ScopedSetNetworkForProcess scopedSetNetworkForProcess(NETID_UNSET);

        // Test DNS query without selecting a network. --> use system default network.
        expectDnsWorksForUid(host_name, NETID_UNSET, TEST_UID, expectedDnsReply);
        expectDnsQueryCountsFn(*sysDefaultNwDnsSv, host_name, expectedDnsReply.size(),
                               systemDefaultNetId);
        // Add user to app default network. --> use app default network.
        ASSERT_RESULT_OK(appDefaultNetwork.addUser(TEST_UID));
        expectDnsWorksForUid(host_name, NETID_UNSET, TEST_UID, expectedDnsReply);
        expectDnsQueryCountsFn(*appDefaultNwDnsSv, host_name, expectedDnsReply.size(),
                               appDefaultNetId);

        // Test DNS query with a selected network.
        // App default network applies to uid, vpn does not applies to uid.
        const struct TestConfig {
            ScopedNetwork* selectedNetwork;
            unsigned expectedDnsNetId;
            std::shared_ptr<test::DNSResponder> expectedDnsServer;
        } vpnWithDnsServerConfigs[]{
                // clang-format off
                // App can select the system default network without any permission.
                {&sysDefaultNetwork, systemDefaultNetId, *sysDefaultNwDnsSv},
                // App can select the restricted network, since its uid was assigned to the network.
                {&appDefaultNetwork, appDefaultNetId, *appDefaultNwDnsSv},
                // App does not have access to the VPN. --> fallback to app default network.
                {&vpn, appDefaultNetId, *appDefaultNwDnsSv},
                // clang-format on
        };
        for (const auto& config : vpnWithDnsServerConfigs) {
            SCOPED_TRACE(fmt::format("Dns over app default network, selectedNetwork = {}",
                                     config.selectedNetwork->name()));
            expectDnsWorksForUid(host_name, config.selectedNetwork->netId(), TEST_UID,
                                 expectedDnsReply);
            expectDnsQueryCountsFn(config.expectedDnsServer, host_name, expectedDnsReply.size(),
                                   config.expectedDnsNetId);
        }

        // App default network applies to uid, vpn applies to uid. --> use vpn.
        ASSERT_RESULT_OK(vpn.addUser(TEST_UID));
        expectDnsWorksForUid(host_name, vpn.netId(), TEST_UID, expectedDnsReply);
        expectDnsQueryCountsFn(*vpnDnsSv, host_name, expectedDnsReply.size(), vpnNetId);

        // vpn without server. --> fallback to app default network.
        ASSERT_TRUE(vpn.clearDnsConfiguration());
        expectDnsWorksForUid(host_name, vpn.netId(), TEST_UID, expectedDnsReply);
        expectDnsQueryCountsFn(*appDefaultNwDnsSv, host_name, expectedDnsReply.size(),
                               appDefaultNetId);
    }
}

// Do not send AAAA query when IPv6 address is link-local with a default route.
TEST_F(ResolverMultinetworkTest, IPv6LinkLocalWithDefaultRoute) {
    constexpr char host_name[] = "ohayou.example.com.";
    ScopedPhysicalNetwork network = CreateScopedPhysicalNetwork(ConnectivityType::V4);
    ASSERT_RESULT_OK(network.init());

    // Add IPv6 default route
    ASSERT_TRUE(mDnsClient.netdService()
                        ->networkAddRoute(network.netId(), network.ifname(), "::/0", "")
                        .isOk());

    const Result<DnsServerPair> dnsPair = network.addIpv4Dns();
    ASSERT_RESULT_OK(dnsPair);
    StartDns(*dnsPair->dnsServer, {{host_name, ns_type::ns_t_a, "192.0.2.0"},
                                   {host_name, ns_type::ns_t_aaaa, "2001:db8:cafe:d00d::31"}});

    ASSERT_TRUE(network.setDnsConfiguration());
    ASSERT_TRUE(network.startTunForwarder());

    auto result = android_getaddrinfofornet_wrapper(host_name, network.netId());
    ASSERT_RESULT_OK(result);
    ScopedAddrinfo ai_result(std::move(result.value()));
    EXPECT_EQ(ToString(ai_result), "192.0.2.0");
    EXPECT_EQ(GetNumQueriesForType(*dnsPair->dnsServer, ns_type::ns_t_a, host_name), 1U);
    EXPECT_EQ(GetNumQueriesForType(*dnsPair->dnsServer, ns_type::ns_t_aaaa, host_name), 0U);

    EXPECT_TRUE(mDnsClient.resolvService()->flushNetworkCache(network.netId()).isOk());
    dnsPair->dnsServer->clearQueries();

    // Add an IPv6 global address. Resolver starts issuing AAAA queries as well as A queries.
    const std::string v6Addr = network.makeIpv6AddrString(1);
    EXPECT_TRUE(
            mDnsClient.netdService()->interfaceAddAddress(network.ifname(), v6Addr, 128).isOk());
    // Ensuring that address is applied. This is required for mainline test (b/249225311).
    usleep(1000 * 1000);

    result = android_getaddrinfofornet_wrapper(host_name, network.netId());
    ASSERT_RESULT_OK(result);
    ScopedAddrinfo ai_results(std::move(result.value()));
    std::vector<std::string> result_strs = ToStrings(ai_results);
    EXPECT_THAT(result_strs,
                testing::UnorderedElementsAreArray({"192.0.2.0", "2001:db8:cafe:d00d::31"}));
    EXPECT_EQ(GetNumQueriesForType(*dnsPair->dnsServer, ns_type::ns_t_a, host_name), 1U);
    EXPECT_EQ(GetNumQueriesForType(*dnsPair->dnsServer, ns_type::ns_t_aaaa, host_name), 1U);
}

// v6 mdns is expected to be sent when the IPv6 address is a link-local with a default route.
TEST_F(ResolverMultinetworkTest, MdnsIPv6LinkLocalWithDefaultRoute) {
    // Kernel 4.4 does not provide an IPv6 link-local address when an interface is added to a
    // network. Skip it because v6 link-local address is a prerequisite for this test.
    SKIP_IF_KERNEL_VERSION_LOWER_THAN(4, 9, 0);

    constexpr char v6addr[] = "::127.0.0.3";
    constexpr char v4addr[] = "127.0.0.3";
    constexpr char host_name[] = "hello.local.";
    ScopedPhysicalNetwork network = CreateScopedPhysicalNetwork(ConnectivityType::V4);
    ASSERT_RESULT_OK(network.init());

    // Add IPv6 default route
    ASSERT_TRUE(mDnsClient.netdService()
                        ->networkAddRoute(network.netId(), network.ifname(), "::/0", "")
                        .isOk());
    // Ensuring that routing is applied. This is required for mainline test (b/247693272).
    usleep(1000 * 1000);

    const Result<DnsServerPair> dnsPair = network.addIpv4Dns();
    ASSERT_RESULT_OK(dnsPair);
    StartDns(*dnsPair->dnsServer, {});

    ASSERT_TRUE(network.setDnsConfiguration());
    ASSERT_TRUE(network.startTunForwarder());

    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService);
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService);
    mdnsv4.setNetwork(network.netId());
    mdnsv6.setNetwork(network.netId());
    StartDns(mdnsv4, {{host_name, ns_type::ns_t_a, v4addr}});
    StartDns(mdnsv6, {{host_name, ns_type::ns_t_aaaa, v6addr}});

    auto result = android_getaddrinfofornet_wrapper("hello.local", network.netId());
    ASSERT_RESULT_OK(result);
    ScopedAddrinfo ai_result(std::move(result.value()));
    EXPECT_THAT(ToStrings(ai_result), testing::UnorderedElementsAreArray({v4addr, v6addr}));

    // make sure queries were sent & received via mdns.
    EXPECT_EQ(GetNumQueries(mdnsv4, host_name), 1U);
    EXPECT_EQ(GetNumQueries(mdnsv6, host_name), 1U);
    EXPECT_EQ(GetNumQueriesForType(*dnsPair->dnsServer, ns_type::ns_t_a, host_name), 0U);
    EXPECT_EQ(GetNumQueriesForType(*dnsPair->dnsServer, ns_type::ns_t_aaaa, host_name), 0U);
}

TEST_F(ResolverTest, NegativeValueInExperimentFlag) {
    // Test setting up different retry count and BASE_TIMEOUT_MSEC in DNS server.
    const struct TestConfig {
        int retryCount;
        int baseTimeoutMsec;
        int expectedRetryCount;
        int expectedBaseTimeoutMsec;
    } TestConfigs[]{{2, 1000, 2, 1000}, {0, 0, RES_DFLRETRY, RES_TIMEOUT}};

    for (const auto& config : TestConfigs) {
        SCOPED_TRACE(fmt::format("Setting up retryCount = {}, baseTimeoutMsec = {}",
                                 config.retryCount, config.baseTimeoutMsec));
        // Initiate negative values in experiment flags.
        ScopedSystemProperties sp1(kRetryCountFlag, "-2");
        ScopedSystemProperties sp2(kRetransIntervalFlag, "-3000");
        resetNetwork();

        ResolverParamsParcel setupParams = DnsResponderClient::GetDefaultResolverParamsParcel();
        setupParams.retryCount = config.retryCount;
        setupParams.baseTimeoutMsec = config.baseTimeoutMsec;
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(setupParams));

        const auto resolvInfo = mDnsClient.getResolverInfo();
        ASSERT_RESULT_OK(resolvInfo);
        EXPECT_EQ(config.expectedRetryCount, resolvInfo.value().params.retry_count);
        EXPECT_EQ(config.expectedBaseTimeoutMsec, resolvInfo.value().params.base_timeout_msec);
    }
}

// Verify that DNS queries can be made for hostnames that exist in etc/hosts when the default
// network is not set and the application does not specify a network. (See
// NetworkController::isUidAllowed for implementation details.)
TEST_F(ResolverTest, NetworkUnspecified_localhost) {
    ScopedDefaultNetwork scopedDefaultNetwork(mDnsClient.netdService(), NETID_UNSET);
    ScopedSetNetworkForProcess scopedSetNetworkForProcess(NETID_UNSET);

    ScopedAddrinfo result = safe_getaddrinfo(kLocalHost, nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ(kLocalHostAddr, ToString(result));

    result = safe_getaddrinfo(kIp6LocalHost, nullptr, nullptr);
    EXPECT_TRUE(result != nullptr);
    EXPECT_EQ(kIp6LocalHostAddr, ToString(result));
}

// Verify uid-based network permission on DNS, which is controlled by INetd::setNetworkAllowlist().
//
// Scenario:
// 1. There are three neworks at the same time:
//  - system default network
//  - enterprise network #1
//  - enterprise network #2
//
// 2. Simulate ConnectivityService calling INetd::setNetworkAllowlist so that
//  - TEST_UID can select only enterprise network #1 and #2. Can not select system default network.
//  - TEST_UID2 is unrestricted on all networks.
TEST_F(ResolverMultinetworkTest, UidAllowedNetworks) {
    // Netd supports it from v13.
    SKIP_IF_REMOTE_VERSION_LESS_THAN(mDnsClient.netdService(), 13);

    constexpr char host_name[] = "ohayou.example.com.";
    constexpr char ipv4_addr[] = "192.0.2.0";
    constexpr char ipv6_addr[] = "2001:db8:cafe:d00d::31";

    const std::pair<ConnectivityType, std::vector<std::string>> testPairs[] = {
            {ConnectivityType::V4, {ipv4_addr}},
            {ConnectivityType::V6, {ipv6_addr}},
            {ConnectivityType::V4V6, {ipv6_addr, ipv4_addr}},
    };
    for (const auto& [ipVersion, expectedDnsReply] : testPairs) {
        SCOPED_TRACE(fmt::format("ConnectivityType: {}", static_cast<int>(ipVersion)));

        // Create networks.
        ScopedPhysicalNetwork sysDefaultNetwork =
                CreateScopedPhysicalNetwork(ipVersion, "SysDefault");
        ScopedPhysicalNetwork enterpriseNetwork_1 =
                CreateScopedPhysicalNetwork(ipVersion, "enterprise_1");
        ScopedPhysicalNetwork enterpriseNetwork_2 =
                CreateScopedPhysicalNetwork(ipVersion, "enterprise_2");
        ASSERT_RESULT_OK(sysDefaultNetwork.init());
        ASSERT_RESULT_OK(enterpriseNetwork_1.init());
        ASSERT_RESULT_OK(enterpriseNetwork_2.init());

        // Set up resolver and start forwarding for networks.
        auto sysDefaultNwDnsSv =
                setupDns(ipVersion, &sysDefaultNetwork, host_name, ipv4_addr, ipv6_addr);
        ASSERT_RESULT_OK(sysDefaultNwDnsSv);
        auto enterpriseNw1DnsSv =
                setupDns(ipVersion, &enterpriseNetwork_1, host_name, ipv4_addr, ipv6_addr);
        ASSERT_RESULT_OK(enterpriseNw1DnsSv);
        auto enterpriseNw2DnsSv =
                setupDns(ipVersion, &enterpriseNetwork_2, host_name, ipv4_addr, ipv6_addr);
        ASSERT_RESULT_OK(enterpriseNw2DnsSv);

        const unsigned systemDefaultNetId = sysDefaultNetwork.netId();
        const unsigned enterprise1NetId = enterpriseNetwork_1.netId();
        const unsigned enterprise2NetId = enterpriseNetwork_2.netId();

        setDefaultNetwork(systemDefaultNetId);

        // We've called setNetworkForProcess in SetupOemNetwork, reset to default first.
        ScopedSetNetworkForProcess scopedSetNetworkForProcess(NETID_UNSET);

        // Add profile app default network for UID. DNS should be sent on it.
        // Note: subPriority 20 = PREFERENCE_ORDER_PROFILE, which is defined
        // in ConnectivityService.java. The value here doesn't really matter.
        ASSERT_RESULT_OK(enterpriseNetwork_1.addUserFromParcel(TEST_UID, /*subPriority*/ 20));
        expectDnsWorksForUid(host_name, NETID_UNSET, TEST_UID, expectedDnsReply);
        expectDnsQueryCountsFn(*enterpriseNw1DnsSv, host_name, expectedDnsReply.size(),
                               enterprise1NetId);

        // Set allowed networks for UIDs. To simplify test, assumes overall UID range is
        // {0, 1, 2, ..., TEST_UID2, TEST_UID}.
        // TEST_UID can't select the system default network. 0 - TEST_UID2 are allowed.
        NativeUidRangeConfig nwDefaultUserConfig = makeNativeUidRangeConfig(
                systemDefaultNetId, {makeUidRangeParcel(0, TEST_UID2)}, /*unused*/ 0);
        // All UIDs can select the enterprise network #1. 0 - TEST_UID are allowed.
        NativeUidRangeConfig nw1UserConfig = makeNativeUidRangeConfig(
                enterprise1NetId, {makeUidRangeParcel(0, TEST_UID)}, /*unused*/ 0);
        // All UIDs can select the enterprise network #2. 0 - TEST_UID are allowed.
        NativeUidRangeConfig nw2UserConfig = makeNativeUidRangeConfig(
                enterprise2NetId, {makeUidRangeParcel(0, TEST_UID)}, /*unused*/ 0);
        EXPECT_TRUE(
                mDnsClient.netdService()
                        ->setNetworkAllowlist({nwDefaultUserConfig, nw1UserConfig, nw2UserConfig})
                        .isOk());

        // Verify that DNS is behaving as the setting.
        struct TestConfig {
            int uid;
            const ScopedNetwork& selectedNetwork;
            bool expectedSuccess;
        } configs[]{
                // clang-format off
                {TEST_UID, sysDefaultNetwork, false},
                {TEST_UID, enterpriseNetwork_1, true},
                {TEST_UID, enterpriseNetwork_2, true},
                {TEST_UID2, sysDefaultNetwork, true},
                {TEST_UID2, enterpriseNetwork_1, true},
                {TEST_UID2, enterpriseNetwork_2, true},
                // clang-format on
        };
        for (const auto& cfg : configs) {
            SCOPED_TRACE(fmt::format("Dns over UID {}, selectedNetwork {}", cfg.uid,
                                     cfg.selectedNetwork.name()));
            if (cfg.expectedSuccess) {
                expectDnsWorksForUid(host_name, cfg.selectedNetwork.netId(), cfg.uid,
                                     expectedDnsReply);
            } else {
                expectDnsFailedForUid(host_name, cfg.selectedNetwork.netId(), cfg.uid);
            }
        }

        // Clear network restrictions.
        EXPECT_TRUE(mDnsClient.netdService()->setNetworkAllowlist({}).isOk());
        // TEST_UID and TEST_UID2 can both select all networks.
        for (const auto& cfg : configs) {
            SCOPED_TRACE(fmt::format("Dns over UID {}, selectedNetwork {}", cfg.uid,
                                     cfg.selectedNetwork.name()));
            expectDnsWorksForUid(host_name, cfg.selectedNetwork.netId(), cfg.uid, expectedDnsReply);
        }
    }
}
