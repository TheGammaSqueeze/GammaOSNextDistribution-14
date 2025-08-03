/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <array>

#include <android-base/test_utils.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <netdutils/NetNativeTestBase.h>

#include "DnsStats.h"

namespace android::net {

using namespace std::chrono_literals;
using android::netdutils::IPSockAddr;
using std::chrono::microseconds;
using std::chrono::milliseconds;
using ::testing::IsEmpty;
using ::testing::UnorderedElementsAreArray;

namespace {

constexpr auto NO_AVERAGE_LATENCY = std::nullopt;

// A helper which can propagate the failure to outside of the stmt to know which line
// of stmt fails. The expectation fails only for the first failed stmt.
#define EXPECT_NO_FAILURE(stmt)                                         \
    do {                                                                \
        bool alreadyFailed = HasFailure();                              \
        stmt;                                                           \
        if (!alreadyFailed && HasFailure()) EXPECT_FALSE(HasFailure()); \
    } while (0)

DnsQueryEvent makeDnsQueryEvent(const Protocol protocol, const NsRcode rcode,
                                const milliseconds& latency) {
    DnsQueryEvent event;
    event.set_protocol(protocol);
    event.set_rcode(rcode);
    event.set_latency_micros(latency.count() * 1000);
    return event;
}

StatsData makeStatsData(const IPSockAddr& server, const int total, const microseconds& latencyUs,
                        const std::map<int, int>& rcodeCounts) {
    StatsData ret(server);
    ret.total = total;
    ret.latencyUs = latencyUs;
    ret.rcodeCounts = rcodeCounts;
    return ret;
}

}  // namespace

// TODO: add StatsDataTest to ensure its methods return correct outputs.

class StatsRecordsTest : public NetNativeTestBase {};

TEST_F(StatsRecordsTest, PushRecord) {
    const IPSockAddr server = IPSockAddr::toIPSockAddr("127.0.0.2", 53);
    constexpr size_t size = 3;
    const StatsRecords::Record recordNoError = {
            .rcode = NS_R_NO_ERROR,
            .linux_errno = 0,
            .latencyUs{10ms},
    };
    const StatsRecords::Record recordTimeout = {
            .rcode = NS_R_TIMEOUT,
            .linux_errno = 0,
            .latencyUs{250ms},
    };

    StatsRecords sr(server, size);
    EXPECT_EQ(sr.getStatsData(), makeStatsData(server, 0, 0ms, {}));

    sr.push(recordNoError);
    EXPECT_EQ(sr.getStatsData(), makeStatsData(server, 1, 10ms, {{NS_R_NO_ERROR, 1}}));

    sr.push(recordNoError);
    EXPECT_EQ(sr.getStatsData(), makeStatsData(server, 2, 20ms, {{NS_R_NO_ERROR, 2}}));

    sr.push(recordTimeout);
    EXPECT_EQ(sr.getStatsData(),
              makeStatsData(server, 3, 270ms, {{NS_R_NO_ERROR, 2}, {NS_R_TIMEOUT, 1}}));

    sr.push(recordTimeout);
    EXPECT_EQ(sr.getStatsData(),
              makeStatsData(server, 3, 510ms, {{NS_R_NO_ERROR, 1}, {NS_R_TIMEOUT, 2}}));

    sr.push(recordTimeout);
    EXPECT_EQ(sr.getStatsData(),
              makeStatsData(server, 3, 750ms, {{NS_R_NO_ERROR, 0}, {NS_R_TIMEOUT, 3}}));
}

class DnsStatsTest : public NetNativeTestBase {
  protected:
    std::string captureDumpOutput() {
        netdutils::DumpWriter dw(STDOUT_FILENO);
        CapturedStdout captured;
        mDnsStats.dump(dw);
        return captured.str();
    }

    // Get the output string from dump() and check the content.
    void verifyDumpOutput(const std::vector<StatsData>& tcpData,
                          const std::vector<StatsData>& udpData,
                          const std::vector<StatsData>& dotData,
                          const std::vector<StatsData>& mdnsData,
                          const std::vector<StatsData>& dohData) {
        // A pattern to capture three matches:
        //     server address (empty allowed), the statistics, and the score.
        const std::regex pattern(R"(\s{4,}([0-9a-fA-F:\.\]\[]*)[ ]?([<(].*[>)])[ ]?(\S*))");
        std::string dumpString = captureDumpOutput();

        const auto check = [&](const std::vector<StatsData>& statsData, const std::string& protocol,
                               std::string* dumpString) {
            SCOPED_TRACE(protocol);
            ASSERT_NE(dumpString->find(protocol), std::string::npos);
            std::smatch sm;

            // Expect to show something even if none of servers or mdns multicast addresses is set.
            if (statsData.empty()) {
                ASSERT_TRUE(std::regex_search(*dumpString, sm, pattern));
                EXPECT_TRUE(sm[1].str().empty());
                EXPECT_EQ(sm[2], "<no data>");
                EXPECT_TRUE(sm[3].str().empty());
                *dumpString = sm.suffix();
                return;
            }

            for (const auto& stats : statsData) {
                ASSERT_TRUE(std::regex_search(*dumpString, sm, pattern));
                EXPECT_EQ(sm[1], stats.sockAddr.toString());
                EXPECT_FALSE(sm[2].str().empty());
                EXPECT_FALSE(sm[3].str().empty());
                *dumpString = sm.suffix();
            }
        };

        check(udpData, "UDP", &dumpString);
        check(dohData, "DOH", &dumpString);
        check(dotData, "TLS", &dumpString);
        check(tcpData, "TCP", &dumpString);
        check(mdnsData, "MDNS", &dumpString);

        // Ensure the whole string has been checked.
        EXPECT_EQ(dumpString, "\n");
    }

    void verifyDnsStatsContent(Protocol protocol, const std::vector<StatsData>& expectedStats,
                               const std::optional<microseconds>& expectedAvgLatency) {
        if (expectedStats.empty()) {
            EXPECT_THAT(mDnsStats.getStats(protocol), IsEmpty());
        } else {
            EXPECT_THAT(mDnsStats.getStats(protocol), UnorderedElementsAreArray(expectedStats));
        }

        EXPECT_EQ(mDnsStats.getAverageLatencyUs(protocol), expectedAvgLatency);
    }

    DnsStats mDnsStats;
};

TEST_F(DnsStatsTest, SetAddrs) {
    // Check before any operation to mDnsStats.
    verifyDumpOutput({}, {}, {}, {}, {});

    static const struct {
        std::vector<std::string> servers;
        std::vector<std::string> expectation;
        bool isSuccess;
    } tests[] = {
            // Normal case.
            {
                    {"127.0.0.1", "127.0.0.2", "fe80::1%22", "2001:db8::2", "::1"},
                    {"127.0.0.1", "127.0.0.2", "fe80::1%22", "2001:db8::2", "::1"},
                    true,
            },
            // Duplicate servers.
            {
                    {"127.0.0.1", "2001:db8::2", "127.0.0.1", "2001:db8::2"},
                    {"127.0.0.1", "2001:db8::2"},
                    true,
            },
            // Invalid server addresses. The state remains in previous state.
            {
                    {"not_an_ip", "127.0.0.3", "127.a.b.2"},
                    {"127.0.0.1", "2001:db8::2"},
                    false,
            },
            // Clean up the old servers 127.0.0.1 and 127.0.0.2.
            {
                    {"127.0.0.4", "2001:db8::5"},
                    {"127.0.0.4", "2001:db8::5"},
                    true,
            },
            // Empty list.
            {{}, {}, true},
    };

    for (const auto& [servers, expectation, isSuccess] : tests) {
        std::vector<IPSockAddr> ipSockAddrs;
        ipSockAddrs.reserve(servers.size());
        for (const auto& server : servers) {
            ipSockAddrs.push_back(IPSockAddr::toIPSockAddr(server, 53));
        }

        EXPECT_TRUE(mDnsStats.setAddrs(ipSockAddrs, PROTO_TCP) == isSuccess);
        EXPECT_TRUE(mDnsStats.setAddrs(ipSockAddrs, PROTO_UDP) == isSuccess);
        EXPECT_TRUE(mDnsStats.setAddrs(ipSockAddrs, PROTO_DOT) == isSuccess);

        std::vector<StatsData> expectedStats;
        expectedStats.reserve(expectation.size());
        for (const auto& exp : expectation) {
            expectedStats.push_back(makeStatsData(IPSockAddr::toIPSockAddr(exp, 53), 0, 0ms, {}));
        }

        EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_TCP, expectedStats, NO_AVERAGE_LATENCY));
        EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_UDP, expectedStats, NO_AVERAGE_LATENCY));
        EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_DOT, expectedStats, NO_AVERAGE_LATENCY));
    }

    verifyDumpOutput({}, {}, {}, {}, {});
}

TEST_F(DnsStatsTest, SetServersDifferentPorts) {
    const std::vector<IPSockAddr> servers = {
            IPSockAddr::toIPSockAddr("127.0.0.1", 0),    IPSockAddr::toIPSockAddr("fe80::1", 0),
            IPSockAddr::toIPSockAddr("127.0.0.1", 53),   IPSockAddr::toIPSockAddr("127.0.0.1", 853),
            IPSockAddr::toIPSockAddr("127.0.0.1", 5353), IPSockAddr::toIPSockAddr("fe80::1", 53),
            IPSockAddr::toIPSockAddr("fe80::1", 853),    IPSockAddr::toIPSockAddr("fe80::1", 5353),
    };

    // Servers setup fails due to port unset.
    EXPECT_FALSE(mDnsStats.setAddrs(servers, PROTO_TCP));
    EXPECT_FALSE(mDnsStats.setAddrs(servers, PROTO_UDP));
    EXPECT_FALSE(mDnsStats.setAddrs(servers, PROTO_DOT));

    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_TCP, {}, NO_AVERAGE_LATENCY));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_UDP, {}, NO_AVERAGE_LATENCY));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_DOT, {}, NO_AVERAGE_LATENCY));
    verifyDumpOutput({}, {}, {}, {}, {});

    EXPECT_TRUE(mDnsStats.setAddrs(std::vector(servers.begin() + 2, servers.end()), PROTO_TCP));
    EXPECT_TRUE(mDnsStats.setAddrs(std::vector(servers.begin() + 2, servers.end()), PROTO_UDP));
    EXPECT_TRUE(mDnsStats.setAddrs(std::vector(servers.begin() + 2, servers.end()), PROTO_DOT));

    const std::vector<StatsData> expectedStats = {
            makeStatsData(servers[2], 0, 0ms, {}), makeStatsData(servers[3], 0, 0ms, {}),
            makeStatsData(servers[4], 0, 0ms, {}), makeStatsData(servers[5], 0, 0ms, {}),
            makeStatsData(servers[6], 0, 0ms, {}), makeStatsData(servers[7], 0, 0ms, {}),
    };

    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_TCP, expectedStats, NO_AVERAGE_LATENCY));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_UDP, expectedStats, NO_AVERAGE_LATENCY));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_DOT, expectedStats, NO_AVERAGE_LATENCY));
    verifyDumpOutput(expectedStats, expectedStats, expectedStats, {}, {});
}

TEST_F(DnsStatsTest, AddStatsAndClear) {
    const std::vector<IPSockAddr> servers = {
            IPSockAddr::toIPSockAddr("127.0.0.1", 53),
            IPSockAddr::toIPSockAddr("127.0.0.2", 53),
    };
    const std::vector<IPSockAddr> mdnsaddrs = {
            IPSockAddr::toIPSockAddr("ff02::fb", 5353),
            IPSockAddr::toIPSockAddr("224.0.0.251", 5353),
    };
    const DnsQueryEvent record = makeDnsQueryEvent(PROTO_UDP, NS_R_NO_ERROR, 10ms);
    const DnsQueryEvent mdnsrecord = makeDnsQueryEvent(PROTO_MDNS, NS_R_NO_ERROR, 10ms);

    EXPECT_TRUE(mDnsStats.setAddrs(servers, PROTO_TCP));
    EXPECT_TRUE(mDnsStats.setAddrs(servers, PROTO_UDP));
    EXPECT_TRUE(mDnsStats.setAddrs(mdnsaddrs, PROTO_MDNS));

    // Fail to add stats because of incorrect arguments.
    EXPECT_FALSE(mDnsStats.addStats(IPSockAddr::toIPSockAddr("127.0.0.4", 53), record));
    EXPECT_FALSE(mDnsStats.addStats(IPSockAddr::toIPSockAddr("127.a.b.4", 53), record));

    EXPECT_TRUE(mDnsStats.addStats(servers[0], record));
    EXPECT_TRUE(mDnsStats.addStats(servers[0], record));
    EXPECT_TRUE(mDnsStats.addStats(servers[1], record));
    EXPECT_TRUE(mDnsStats.addStats(mdnsaddrs[0], mdnsrecord));
    EXPECT_TRUE(mDnsStats.addStats(mdnsaddrs[1], mdnsrecord));

    const std::vector<StatsData> expectedStatsForTcp = {
            makeStatsData(servers[0], 0, 0ms, {}),
            makeStatsData(servers[1], 0, 0ms, {}),
    };
    const std::vector<StatsData> expectedStatsForUdp = {
            makeStatsData(servers[0], 2, 20ms, {{NS_R_NO_ERROR, 2}}),
            makeStatsData(servers[1], 1, 10ms, {{NS_R_NO_ERROR, 1}}),
    };

    const std::vector<StatsData> expectedStatsForMdns = {
            makeStatsData(mdnsaddrs[1], 1, 10ms, {{NS_R_NO_ERROR, 1}}),
            makeStatsData(mdnsaddrs[0], 1, 10ms, {{NS_R_NO_ERROR, 1}}),
    };

    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_TCP, expectedStatsForTcp, NO_AVERAGE_LATENCY));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_UDP, expectedStatsForUdp, 10ms));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_DOT, {}, NO_AVERAGE_LATENCY));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_MDNS, expectedStatsForMdns, 10ms));
    verifyDumpOutput(expectedStatsForTcp, expectedStatsForUdp, {}, expectedStatsForMdns, {});

    // Clear stats.
    EXPECT_TRUE(mDnsStats.setAddrs({}, PROTO_TCP));
    EXPECT_TRUE(mDnsStats.setAddrs({}, PROTO_UDP));
    EXPECT_TRUE(mDnsStats.setAddrs({}, PROTO_DOT));
    EXPECT_TRUE(mDnsStats.setAddrs({}, PROTO_MDNS));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_TCP, {}, NO_AVERAGE_LATENCY));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_UDP, {}, NO_AVERAGE_LATENCY));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_DOT, {}, NO_AVERAGE_LATENCY));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_MDNS, {}, NO_AVERAGE_LATENCY));
    verifyDumpOutput({}, {}, {}, {}, {});
}

TEST_F(DnsStatsTest, StatsRemainsInExistentServer) {
    std::vector<IPSockAddr> servers = {
            IPSockAddr::toIPSockAddr("127.0.0.1", 53),
            IPSockAddr::toIPSockAddr("127.0.0.2", 53),
    };
    const DnsQueryEvent recordNoError = makeDnsQueryEvent(PROTO_UDP, NS_R_NO_ERROR, 10ms);
    const DnsQueryEvent recordTimeout = makeDnsQueryEvent(PROTO_UDP, NS_R_TIMEOUT, 250ms);

    EXPECT_TRUE(mDnsStats.setAddrs(servers, PROTO_UDP));

    // Add a record to 127.0.0.1.
    EXPECT_TRUE(mDnsStats.addStats(servers[0], recordNoError));

    // Add four records to 127.0.0.2.
    EXPECT_TRUE(mDnsStats.addStats(servers[1], recordNoError));
    EXPECT_TRUE(mDnsStats.addStats(servers[1], recordNoError));
    EXPECT_TRUE(mDnsStats.addStats(servers[1], recordTimeout));
    EXPECT_TRUE(mDnsStats.addStats(servers[1], recordTimeout));

    std::vector<StatsData> expectedStats = {
            makeStatsData(servers[0], 1, 10ms, {{NS_R_NO_ERROR, 1}}),
            makeStatsData(servers[1], 4, 520ms, {{NS_R_NO_ERROR, 2}, {NS_R_TIMEOUT, 2}}),
    };
    EXPECT_THAT(mDnsStats.getStats(PROTO_UDP), UnorderedElementsAreArray(expectedStats));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_UDP, expectedStats, 106ms));
    verifyDumpOutput({}, expectedStats, {}, {}, {});

    // Update the server list, the stats of 127.0.0.2 will remain.
    servers = {
            IPSockAddr::toIPSockAddr("127.0.0.2", 53),
            IPSockAddr::toIPSockAddr("127.0.0.3", 53),
            IPSockAddr::toIPSockAddr("127.0.0.4", 53),
    };
    EXPECT_TRUE(mDnsStats.setAddrs(servers, PROTO_UDP));
    expectedStats = {
            makeStatsData(servers[0], 4, 520ms, {{NS_R_NO_ERROR, 2}, {NS_R_TIMEOUT, 2}}),
            makeStatsData(servers[1], 0, 0ms, {}),
            makeStatsData(servers[2], 0, 0ms, {}),
    };
    EXPECT_THAT(mDnsStats.getStats(PROTO_UDP), UnorderedElementsAreArray(expectedStats));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_UDP, expectedStats, 130ms));
    verifyDumpOutput({}, expectedStats, {}, {}, {});

    // Let's add a record to 127.0.0.2 again.
    EXPECT_TRUE(mDnsStats.addStats(servers[0], recordNoError));
    expectedStats = {
            makeStatsData(servers[0], 5, 530ms, {{NS_R_NO_ERROR, 3}, {NS_R_TIMEOUT, 2}}),
            makeStatsData(servers[1], 0, 0ms, {}),
            makeStatsData(servers[2], 0, 0ms, {}),
    };
    EXPECT_THAT(mDnsStats.getStats(PROTO_UDP), UnorderedElementsAreArray(expectedStats));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_UDP, expectedStats, 106ms));
    verifyDumpOutput({}, expectedStats, {}, {}, {});
}

TEST_F(DnsStatsTest, AddStatsRecords_100000) {
    constexpr size_t operations = 100000;
    constexpr size_t logSize = DnsStats::kLogSize;
    constexpr size_t rcodeNum = 4;  // A value by which kLogSize is divisible.
    ASSERT_EQ(logSize % rcodeNum, 0U);

    const std::vector<IPSockAddr> servers = {
            IPSockAddr::toIPSockAddr("127.0.0.1", 53),
            IPSockAddr::toIPSockAddr("127.0.0.2", 53),
            IPSockAddr::toIPSockAddr("127.0.0.3", 53),
            IPSockAddr::toIPSockAddr("127.0.0.4", 53),
    };

    const std::vector<IPSockAddr> mdnsaddrs = {
            IPSockAddr::toIPSockAddr("ff02::fb", 5353),
            IPSockAddr::toIPSockAddr("224.0.0.251", 5353),
    };

    // To test unknown rcode in rcodeToName(), store the elements as type int.
    const std::array<int, rcodeNum> rcodes = {
            NS_R_NO_ERROR,        // NOERROR
            NS_R_NXDOMAIN,        // NXDOMAIN
            99,                   // UNKNOWN(99)
            NS_R_INTERNAL_ERROR,  // INTERNAL_ERROR
    };

    EXPECT_TRUE(mDnsStats.setAddrs(servers, PROTO_TCP));
    EXPECT_TRUE(mDnsStats.setAddrs(servers, PROTO_UDP));
    EXPECT_TRUE(mDnsStats.setAddrs(servers, PROTO_DOT));
    EXPECT_TRUE(mDnsStats.setAddrs(mdnsaddrs, PROTO_MDNS));

    for (size_t i = 0; i < operations; i++) {
        const NsRcode rcode = static_cast<NsRcode>(rcodes[i % rcodeNum]);
        const auto eventTcp = makeDnsQueryEvent(PROTO_TCP, rcode, milliseconds(i));
        const auto eventUdp = makeDnsQueryEvent(PROTO_UDP, rcode, milliseconds(i));
        const auto eventDot = makeDnsQueryEvent(PROTO_DOT, rcode, milliseconds(i));
        for (const auto& server : servers) {
            SCOPED_TRACE(server.toString() + "-" + std::to_string(i));
            ASSERT_TRUE(mDnsStats.addStats(server, eventTcp));
            ASSERT_TRUE(mDnsStats.addStats(server, eventUdp));
            ASSERT_TRUE(mDnsStats.addStats(server, eventDot));
        }
        const auto eventMdns = makeDnsQueryEvent(PROTO_MDNS, rcode, milliseconds(i));
        for (const auto& mdnsaddr : mdnsaddrs) {
            SCOPED_TRACE(mdnsaddr.toString() + "-" + std::to_string(i));
            ASSERT_TRUE(mDnsStats.addStats(mdnsaddr, eventMdns));
        }
    }

    std::map<int, int> expectedRcodeCounts;
    for (const auto& rcode : rcodes) {
        expectedRcodeCounts.try_emplace(rcode, 32);
    }

    // The average latency 99935.5 ms is derived from (99872ms + 99873ms + ... + 99999ms) / logSize,
    // where logSize is 128.
    const std::vector<StatsData> expectedStats = {
            makeStatsData(servers[0], logSize, logSize * 99935500us, expectedRcodeCounts),
            makeStatsData(servers[1], logSize, logSize * 99935500us, expectedRcodeCounts),
            makeStatsData(servers[2], logSize, logSize * 99935500us, expectedRcodeCounts),
            makeStatsData(servers[3], logSize, logSize * 99935500us, expectedRcodeCounts),
    };

    const std::vector<StatsData> expectedMdnsStats = {
            makeStatsData(mdnsaddrs[1], logSize, logSize * 99935500us, expectedRcodeCounts),
            makeStatsData(mdnsaddrs[0], logSize, logSize * 99935500us, expectedRcodeCounts),
    };
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_TCP, expectedStats, 99935500us));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_UDP, expectedStats, 99935500us));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_DOT, expectedStats, 99935500us));
    EXPECT_NO_FAILURE(verifyDnsStatsContent(PROTO_MDNS, expectedMdnsStats, 99935500us));
    verifyDumpOutput(expectedStats, expectedStats, expectedStats, expectedMdnsStats, {});
}

TEST_F(DnsStatsTest, GetServers_SortingByLatency) {
    const IPSockAddr server1 = IPSockAddr::toIPSockAddr("127.0.0.1", 53);
    const IPSockAddr server2 = IPSockAddr::toIPSockAddr("127.0.0.2", 53);
    const IPSockAddr server3 = IPSockAddr::toIPSockAddr("2001:db8:cafe:d00d::1", 53);
    const IPSockAddr server4 = IPSockAddr::toIPSockAddr("2001:db8:cafe:d00d::2", 53);

    // Return empty list before setup.
    EXPECT_THAT(mDnsStats.getSortedServers(PROTO_UDP), IsEmpty());

    // Before there's any stats, the list of the sorted servers is the same as the setup's one.
    EXPECT_TRUE(mDnsStats.setAddrs({server1, server2, server3, server4}, PROTO_UDP));
    EXPECT_TRUE(mDnsStats.setAddrs({server1, server2, server3, server4}, PROTO_DOT));
    EXPECT_THAT(mDnsStats.getSortedServers(PROTO_UDP),
                testing::ElementsAreArray({server1, server2, server3, server4}));

    // Add a record to server1. The qualities of the other servers increase.
    EXPECT_TRUE(mDnsStats.addStats(server1, makeDnsQueryEvent(PROTO_UDP, NS_R_NO_ERROR, 10ms)));
    EXPECT_THAT(mDnsStats.getSortedServers(PROTO_UDP),
                testing::ElementsAreArray({server2, server3, server4, server1}));

    // Add a record, with less repose time than server1, to server3.
    EXPECT_TRUE(mDnsStats.addStats(server3, makeDnsQueryEvent(PROTO_UDP, NS_R_NO_ERROR, 5ms)));
    EXPECT_THAT(mDnsStats.getSortedServers(PROTO_UDP),
                testing::ElementsAreArray({server2, server4, server3, server1}));

    // Even though server2 has zero response time, select server4 as the first server because it
    // doesn't have stats yet.
    EXPECT_TRUE(mDnsStats.addStats(server2, makeDnsQueryEvent(PROTO_UDP, NS_R_NO_ERROR, 0ms)));
    EXPECT_THAT(mDnsStats.getSortedServers(PROTO_UDP),
                testing::ElementsAreArray({server4, server2, server3, server1}));

    // Updating DoT record to server4 changes nothing.
    EXPECT_TRUE(mDnsStats.addStats(server4, makeDnsQueryEvent(PROTO_DOT, NS_R_NO_ERROR, 10ms)));
    EXPECT_THAT(mDnsStats.getSortedServers(PROTO_UDP),
                testing::ElementsAreArray({server4, server2, server3, server1}));

    // Add a record, with a very large value of respose time, to server4.
    EXPECT_TRUE(mDnsStats.addStats(server4, makeDnsQueryEvent(PROTO_UDP, NS_R_NO_ERROR, 500000ms)));
    EXPECT_THAT(mDnsStats.getSortedServers(PROTO_UDP),
                testing::ElementsAreArray({server2, server3, server1, server4}));

    // Add some internal_error records with permission error to server2.
    // The internal_error won't cause the priority of server2 drop. (but some of the other
    // quality factors will still be counted, such as skipped_count and latency)
    auto recordFromNetworkRestricted = makeDnsQueryEvent(PROTO_UDP, NS_R_INTERNAL_ERROR, 1ms);
    recordFromNetworkRestricted.set_linux_errno(static_cast<LinuxErrno>(EPERM));
    for (int i = 0; i < 3; i++) {
        EXPECT_TRUE(mDnsStats.addStats(server2, recordFromNetworkRestricted));
    }
    EXPECT_THAT(mDnsStats.getSortedServers(PROTO_UDP),
                testing::ElementsAreArray({server2, server3, server1, server4}));

    // The list of the DNS servers changed.
    EXPECT_TRUE(mDnsStats.setAddrs({server2, server4}, PROTO_UDP));
    EXPECT_THAT(mDnsStats.getSortedServers(PROTO_UDP),
                testing::ElementsAreArray({server2, server4}));

    // It fails to add records to an non-existing server, and nothing is changed in getting
    // the sorted servers.
    EXPECT_FALSE(mDnsStats.addStats(server1, makeDnsQueryEvent(PROTO_UDP, NS_R_NO_ERROR, 10ms)));
    EXPECT_THAT(mDnsStats.getSortedServers(PROTO_UDP),
                testing::ElementsAreArray({server2, server4}));
}

TEST_F(DnsStatsTest, GetServers_DeprioritizingBadServers) {
    const IPSockAddr server1 = IPSockAddr::toIPSockAddr("127.0.0.1", 53);
    const IPSockAddr server2 = IPSockAddr::toIPSockAddr("127.0.0.2", 53);
    const IPSockAddr server3 = IPSockAddr::toIPSockAddr("127.0.0.3", 53);
    const IPSockAddr server4 = IPSockAddr::toIPSockAddr("127.0.0.4", 53);

    EXPECT_TRUE(mDnsStats.setAddrs({server1, server2, server3, server4}, PROTO_UDP));

    int server1Counts = 0;
    int server2Counts = 0;
    for (int i = 0; i < 5000; i++) {
        const auto servers = mDnsStats.getSortedServers(PROTO_UDP);
        EXPECT_EQ(servers.size(), 4U);
        if (servers[0] == server1) {
            // server1 is relatively slowly responsive.
            EXPECT_TRUE(mDnsStats.addStats(servers[0],
                                           makeDnsQueryEvent(PROTO_UDP, NS_R_NO_ERROR, 200ms)));
            server1Counts++;
        } else if (servers[0] == server2) {
            // server2 is relatively quickly responsive.
            EXPECT_TRUE(mDnsStats.addStats(servers[0],
                                           makeDnsQueryEvent(PROTO_UDP, NS_R_NO_ERROR, 100ms)));
            server2Counts++;
        } else if (servers[0] == server3) {
            // server3 always times out.
            EXPECT_TRUE(mDnsStats.addStats(servers[0],
                                           makeDnsQueryEvent(PROTO_UDP, NS_R_TIMEOUT, 1000ms)));
        } else if (servers[0] == server4) {
            // server4 is unusable.
            EXPECT_TRUE(mDnsStats.addStats(servers[0],
                                           makeDnsQueryEvent(PROTO_UDP, NS_R_INTERNAL_ERROR, 1ms)));
        }
    }

    const std::vector<StatsData> allStatsData = mDnsStats.getStats(PROTO_UDP);
    for (const auto& data : allStatsData) {
        EXPECT_EQ(data.rcodeCounts.size(), 1U);
        if (data.sockAddr == server1 || data.sockAddr == server2) {
            const auto it = data.rcodeCounts.find(NS_R_NO_ERROR);
            ASSERT_NE(it, data.rcodeCounts.end());
            EXPECT_GT(server2Counts, 2 * server1Counts);  // At least twice larger.
        } else if (data.sockAddr == server3) {
            const auto it = data.rcodeCounts.find(NS_R_TIMEOUT);
            ASSERT_NE(it, data.rcodeCounts.end());
            EXPECT_LT(it->second, 10);
        } else if (data.sockAddr == server4) {
            const auto it = data.rcodeCounts.find(NS_R_INTERNAL_ERROR);
            ASSERT_NE(it, data.rcodeCounts.end());
            EXPECT_LT(it->second, 10);
        }
    }
}

}  // namespace android::net
