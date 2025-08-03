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
 *
 */

#define LOG_TAG "resolv"

#include "DnsStats.h"

#include <android-base/format.h>
#include <android-base/logging.h>

namespace android::net {

using netdutils::DumpWriter;
using netdutils::IPAddress;
using netdutils::IPSockAddr;
using netdutils::ScopedIndent;
using std::chrono::duration_cast;
using std::chrono::microseconds;
using std::chrono::milliseconds;
using std::chrono::seconds;

namespace {

static constexpr IPAddress INVALID_IPADDRESS = IPAddress();

std::string rcodeToName(int rcode) {
    // clang-format off
    switch (rcode) {
        case NS_R_NO_ERROR: return "NOERROR";
        case NS_R_FORMERR: return "FORMERR";
        case NS_R_SERVFAIL: return "SERVFAIL";
        case NS_R_NXDOMAIN: return "NXDOMAIN";
        case NS_R_NOTIMPL: return "NOTIMP";
        case NS_R_REFUSED: return "REFUSED";
        case NS_R_YXDOMAIN: return "YXDOMAIN";
        case NS_R_YXRRSET: return "YXRRSET";
        case NS_R_NXRRSET: return "NXRRSET";
        case NS_R_NOTAUTH: return "NOTAUTH";
        case NS_R_NOTZONE: return "NOTZONE";
        case NS_R_INTERNAL_ERROR: return "INTERNAL_ERROR";
        case NS_R_TIMEOUT: return "TIMEOUT";
        default: return fmt::format("UNKNOWN({})", rcode);
    }
    // clang-format on
}

bool ensureNoInvalidIp(const std::vector<IPSockAddr>& addrs) {
    for (const auto& addr : addrs) {
        if (addr.ip() == INVALID_IPADDRESS || addr.port() == 0) {
            LOG(WARNING) << "Invalid addr: " << addr;
            return false;
        }
    }
    return true;
}

}  // namespace

// The comparison ignores the last update time.
bool StatsData::operator==(const StatsData& o) const {
    return std::tie(sockAddr, total, rcodeCounts, latencyUs) ==
           std::tie(o.sockAddr, o.total, o.rcodeCounts, o.latencyUs);
}

int StatsData::averageLatencyMs() const {
    return (total == 0) ? 0 : duration_cast<milliseconds>(latencyUs).count() / total;
}

std::string StatsData::toString() const {
    if (total == 0) return fmt::format("{} <no data>", sockAddr.toString());

    const auto now = std::chrono::steady_clock::now();
    const int lastUpdateSec = duration_cast<seconds>(now - lastUpdate).count();
    std::string buf;
    for (const auto& [rcode, counts] : rcodeCounts) {
        if (counts != 0) {
            buf += fmt::format("{}:{} ", rcodeToName(rcode), counts);
        }
    }
    return fmt::format("{} ({}, {}ms, [{}], {}s)", sockAddr.toString(), total, averageLatencyMs(),
                       buf, lastUpdateSec);
}

StatsRecords::StatsRecords(const IPSockAddr& ipSockAddr, size_t size)
    : mCapacity(size), mStatsData(ipSockAddr) {}

void StatsRecords::push(const Record& record) {
    updateStatsData(record, true);
    mRecords.push_back(record);

    if (mRecords.size() > mCapacity) {
        updateStatsData(mRecords.front(), false);
        mRecords.pop_front();
    }

    // Update the quality factors.
    mSkippedCount = 0;

    // Because failures due to no permission can't prove that the quality of DNS server is bad,
    // skip the penalty update. The average latency, however, has been updated. For short-latency
    // servers, it will be fine. For long-latency servers, their average latency will be
    // decreased but the latency-based algorithm will adjust their average latency back to the
    // right range after few attempts when network is not restricted.
    // The check is synced from isNetworkRestricted() in res_send.cpp.
    if (record.linux_errno != EPERM) {
        updatePenalty(record);
    }
}

void StatsRecords::updateStatsData(const Record& record, const bool add) {
    const int rcode = record.rcode;
    if (add) {
        mStatsData.total += 1;
        mStatsData.rcodeCounts[rcode] += 1;
        mStatsData.latencyUs += record.latencyUs;
    } else {
        mStatsData.total -= 1;
        mStatsData.rcodeCounts[rcode] -= 1;
        mStatsData.latencyUs -= record.latencyUs;
    }
    mStatsData.lastUpdate = std::chrono::steady_clock::now();
}

void StatsRecords::updatePenalty(const Record& record) {
    switch (record.rcode) {
        case NS_R_NO_ERROR:
        case NS_R_NXDOMAIN:
        case NS_R_NOTAUTH:
            mPenalty = 0;
            return;
        default:
            // NS_R_TIMEOUT and NS_R_INTERNAL_ERROR are in this case.
            if (mPenalty == 0) {
                mPenalty = 100;
            } else {
                // The evaluated quality drops more quickly when continuous failures happen.
                mPenalty = std::min(mPenalty * 2, kMaxQuality);
            }
            return;
    }
}

double StatsRecords::score() const {
    const int avgRtt = mStatsData.averageLatencyMs();

    // Set the lower bound to -1 in case of "avgRtt + mPenalty < mSkippedCount"
    //   1) when the server doesn't have any stats yet.
    //   2) when the sorting has been disabled while it was enabled before.
    int quality = std::clamp(avgRtt + mPenalty - mSkippedCount, -1, kMaxQuality);

    // Normalization.
    return static_cast<double>(kMaxQuality - quality) * 100 / kMaxQuality;
}

void StatsRecords::incrementSkippedCount() {
    mSkippedCount = std::min(mSkippedCount + 1, kMaxQuality);
}

bool DnsStats::setAddrs(const std::vector<netdutils::IPSockAddr>& addrs, Protocol protocol) {
    if (!ensureNoInvalidIp(addrs)) return false;

    StatsMap& statsMap = mStats[protocol];
    for (const auto& addr : addrs) {
        statsMap.try_emplace(addr, StatsRecords(addr, kLogSize));
    }

    // Clean up the map to eliminate the nodes not belonging to the given list of servers.
    const auto cleanup = [&](StatsMap* statsMap) {
        StatsMap tmp;
        for (const auto& addr : addrs) {
            if (statsMap->find(addr) != statsMap->end()) {
                tmp.insert(statsMap->extract(addr));
            }
        }
        statsMap->swap(tmp);
    };

    cleanup(&statsMap);

    return true;
}

bool DnsStats::addStats(const IPSockAddr& ipSockAddr, const DnsQueryEvent& record) {
    if (ipSockAddr.ip() == INVALID_IPADDRESS) return false;

    bool added = false;
    for (auto& [sockAddr, statsRecords] : mStats[record.protocol()]) {
        if (sockAddr == ipSockAddr) {
            const StatsRecords::Record rec = {
                    .rcode = record.rcode(),
                    .linux_errno = record.linux_errno(),
                    .latencyUs = microseconds(record.latency_micros()),
            };
            statsRecords.push(rec);
            added = true;
        } else {
            statsRecords.incrementSkippedCount();
        }
    }

    return added;
}

std::vector<IPSockAddr> DnsStats::getSortedServers(Protocol protocol) const {
    // DoT unsupported. The handshake overhead is expensive, and the connection will hang for a
    // while. Need to figure out if it is worth doing for DoT servers.
    if (protocol == PROTO_DOT) return {};

    auto it = mStats.find(protocol);
    if (it == mStats.end()) return {};

    // Sorting on insertion in decreasing order.
    std::multimap<double, IPSockAddr, std::greater<double>> sortedData;
    for (const auto& [ip, statsRecords] : it->second) {
        sortedData.insert({statsRecords.score(), ip});
    }

    std::vector<IPSockAddr> ret;
    ret.reserve(sortedData.size());
    for (auto& [_, v] : sortedData) {
        ret.push_back(v);  // IPSockAddr is trivially-copyable.
    }

    return ret;
}

std::optional<microseconds> DnsStats::getAverageLatencyUs(Protocol protocol) const {
    const auto stats = getStats(protocol);

    int count = 0;
    microseconds sum;
    for (const auto& v : stats) {
        count += v.total;
        sum += v.latencyUs;
    }

    if (count == 0) return std::nullopt;
    return sum / count;
}

std::vector<StatsData> DnsStats::getStats(Protocol protocol) const {
    std::vector<StatsData> ret;

    if (mStats.find(protocol) != mStats.end()) {
        for (const auto& [_, statsRecords] : mStats.at(protocol)) {
            ret.push_back(statsRecords.getStatsData());
        }
    }
    return ret;
}

void DnsStats::dump(DumpWriter& dw) {
    const auto dumpStatsMap = [&](StatsMap& statsMap) {
        ScopedIndent indentLog(dw);
        if (statsMap.size() == 0) {
            dw.println("<no data>");
            return;
        }
        for (const auto& [_, statsRecords] : statsMap) {
            const StatsData& data = statsRecords.getStatsData();
            std::string str =
                    fmt::format("{} score{{{:.1f}}}", data.toString(), statsRecords.score());
            dw.println("%s", str.c_str());
        }
    };

    dw.println("Server statistics: (total, RTT avg, {rcode:counts}, last update)");
    ScopedIndent indentStats(dw);

    dw.println("over UDP");
    dumpStatsMap(mStats[PROTO_UDP]);

    dw.println("over DOH");
    dumpStatsMap(mStats[PROTO_DOH]);

    dw.println("over TLS");
    dumpStatsMap(mStats[PROTO_DOT]);

    dw.println("over TCP");
    dumpStatsMap(mStats[PROTO_TCP]);

    dw.println("over MDNS");
    dumpStatsMap(mStats[PROTO_MDNS]);
}

}  // namespace android::net
