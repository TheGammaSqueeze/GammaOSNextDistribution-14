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

#pragma once

#include <chrono>
#include <deque>
#include <map>
#include <vector>

#include <android-base/thread_annotations.h>
#include <netdutils/DumpWriter.h>
#include <netdutils/InternetAddresses.h>

#include "ResolverStats.h"
#include "stats.pb.h"

namespace android::net {

// The overall information of a StatsRecords.
struct StatsData {
    StatsData(const netdutils::IPSockAddr& ipSockAddr) : sockAddr(ipSockAddr) {
        lastUpdate = std::chrono::steady_clock::now();
    };

    // Socket address.
    netdutils::IPSockAddr sockAddr;

    // The most recent number of records being accumulated.
    int total = 0;

    // The map used to store the number of each rcode.
    std::map<int, int> rcodeCounts;

    // The aggregated RTT in microseconds.
    // For DNS-over-TCP, it includes TCP handshake.
    // For DNS-over-TLS, it might include TCP handshake plus SSL handshake.
    std::chrono::microseconds latencyUs = {};

    // The last update timestamp.
    std::chrono::time_point<std::chrono::steady_clock> lastUpdate;

    int averageLatencyMs() const;
    std::string toString() const;

    // For testing.
    bool operator==(const StatsData& o) const;
    friend std::ostream& operator<<(std::ostream& os, const StatsData& data) {
        return os << data.toString();
    }
};

// A circular buffer based class used to store the statistics for a server with a protocol.
class StatsRecords {
  public:
    struct Record {
        int rcode = 0;        // NS_R_NO_ERROR
        int linux_errno = 0;  // SYS_NO_ERROR
        std::chrono::microseconds latencyUs;
    };

    StatsRecords(const netdutils::IPSockAddr& ipSockAddr, size_t size);

    void push(const Record& record);

    const StatsData& getStatsData() const { return mStatsData; }

    // Quantifies the quality based on the current quality factors and the latency, and normalize
    // the value to a score between 0 to 100.
    double score() const;

    void incrementSkippedCount();

  private:
    void updateStatsData(const Record& record, const bool add);
    void updatePenalty(const Record& record);

    std::deque<Record> mRecords;
    size_t mCapacity;
    StatsData mStatsData;

    // A quality factor used to distinguish if the server can't be evaluated by latency alone, such
    // as instant failure on connect.
    int mPenalty = 0;

    // A quality factor used to prevent starvation.
    int mSkippedCount = 0;

    // The maximum of the quantified result. As the sorting is on the basis of server latency, limit
    // the maximal value of the quantity to 10000 in correspondence with the maximal cleartext
    // query timeout 10000 milliseconds. This helps normalize the value of the quality to a score.
    static constexpr int kMaxQuality = 10000;
};

// DnsStats class manages the statistics of DNS servers or MDNS multicast addresses per netId.
// The class itself is not thread-safe.
class DnsStats {
  public:
    using StatsMap = std::map<netdutils::IPSockAddr, StatsRecords>;

    // Add |addrs| to the map, and remove no-longer-used addresses.
    // Return true if they are successfully added; otherwise, return false.
    bool setAddrs(const std::vector<netdutils::IPSockAddr>& addrs, Protocol protocol);

    // Return true if |record| is successfully added into |server|'s stats; otherwise, return false.
    bool addStats(const netdutils::IPSockAddr& server, const DnsQueryEvent& record);

    std::vector<netdutils::IPSockAddr> getSortedServers(Protocol protocol) const;

    // Returns the average query latency in microseconds.
    std::optional<std::chrono::microseconds> getAverageLatencyUs(Protocol protocol) const;

    void dump(netdutils::DumpWriter& dw);

    std::vector<StatsData> getStats(Protocol protocol) const;

    // TODO: Compatible support for getResolverInfo().

    static constexpr size_t kLogSize = 128;

  private:
    std::map<Protocol, StatsMap> mStats;
};

}  // namespace android::net
