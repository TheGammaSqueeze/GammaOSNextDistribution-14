/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <inttypes.h>
#include <net/if.h>
#include <string.h>
#include <unordered_set>

#include <utils/Log.h>
#include <utils/misc.h>

#include "android-base/file.h"
#include "android-base/strings.h"
#include "android-base/unique_fd.h"
#include "bpf/BpfMap.h"
#include "netd.h"
#include "netdbpf/BpfNetworkStats.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG "BpfNetworkStats"

namespace android {
namespace bpf {

using base::Result;

BpfMap<uint32_t, IfaceValue>& getIfaceIndexNameMap() {
    static BpfMap<uint32_t, IfaceValue> ifaceIndexNameMap(IFACE_INDEX_NAME_MAP_PATH);
    return ifaceIndexNameMap;
}

const BpfMapRO<uint32_t, StatsValue>& getIfaceStatsMap() {
    static BpfMapRO<uint32_t, StatsValue> ifaceStatsMap(IFACE_STATS_MAP_PATH);
    return ifaceStatsMap;
}

Result<IfaceValue> ifindex2name(const uint32_t ifindex) {
    Result<IfaceValue> v = getIfaceIndexNameMap().readValue(ifindex);
    if (v.ok()) return v;
    IfaceValue iv = {};
    if (!if_indextoname(ifindex, iv.name)) return v;
    getIfaceIndexNameMap().writeValue(ifindex, iv, BPF_ANY);
    return iv;
}

void bpfRegisterIface(const char* iface) {
    if (!iface) return;
    if (strlen(iface) >= sizeof(IfaceValue)) return;
    uint32_t ifindex = if_nametoindex(iface);
    if (!ifindex) return;
    IfaceValue ifname = {};
    strlcpy(ifname.name, iface, sizeof(ifname.name));
    getIfaceIndexNameMap().writeValue(ifindex, ifname, BPF_ANY);
}

int bpfGetUidStatsInternal(uid_t uid, StatsValue* stats,
                           const BpfMapRO<uint32_t, StatsValue>& appUidStatsMap) {
    auto statsEntry = appUidStatsMap.readValue(uid);
    if (!statsEntry.ok()) {
        *stats = {};
        return (statsEntry.error().code() == ENOENT) ? 0 : -statsEntry.error().code();
    }
    *stats = statsEntry.value();
    return 0;
}

int bpfGetUidStats(uid_t uid, StatsValue* stats) {
    static BpfMapRO<uint32_t, StatsValue> appUidStatsMap(APP_UID_STATS_MAP_PATH);
    return bpfGetUidStatsInternal(uid, stats, appUidStatsMap);
}

int bpfGetIfaceStatsInternal(const char* iface, StatsValue* stats,
                             const BpfMapRO<uint32_t, StatsValue>& ifaceStatsMap,
                             const IfIndexToNameFunc ifindex2name) {
    *stats = {};
    int64_t unknownIfaceBytesTotal = 0;
    const auto processIfaceStats =
            [iface, stats, ifindex2name, &unknownIfaceBytesTotal](
                    const uint32_t& key,
                    const BpfMapRO<uint32_t, StatsValue>& ifaceStatsMap) -> Result<void> {
        Result<IfaceValue> ifname = ifindex2name(key);
        if (!ifname.ok()) {
            maybeLogUnknownIface(key, ifaceStatsMap, key, &unknownIfaceBytesTotal);
            return Result<void>();
        }
        if (!iface || !strcmp(iface, ifname.value().name)) {
            Result<StatsValue> statsEntry = ifaceStatsMap.readValue(key);
            if (!statsEntry.ok()) {
                return statsEntry.error();
            }
            *stats += statsEntry.value();
        }
        return Result<void>();
    };
    auto res = ifaceStatsMap.iterate(processIfaceStats);
    return res.ok() ? 0 : -res.error().code();
}

int bpfGetIfaceStats(const char* iface, StatsValue* stats) {
    return bpfGetIfaceStatsInternal(iface, stats, getIfaceStatsMap(), ifindex2name);
}

int bpfGetIfIndexStatsInternal(uint32_t ifindex, StatsValue* stats,
                               const BpfMapRO<uint32_t, StatsValue>& ifaceStatsMap) {
    auto statsEntry = ifaceStatsMap.readValue(ifindex);
    if (!statsEntry.ok()) {
        *stats = {};
        return (statsEntry.error().code() == ENOENT) ? 0 : -statsEntry.error().code();
    }
    *stats = statsEntry.value();
    return 0;
}

int bpfGetIfIndexStats(int ifindex, StatsValue* stats) {
    return bpfGetIfIndexStatsInternal(ifindex, stats, getIfaceStatsMap());
}

stats_line populateStatsEntry(const StatsKey& statsKey, const StatsValue& statsEntry,
                              const IfaceValue& ifname) {
    stats_line newLine;
    strlcpy(newLine.iface, ifname.name, sizeof(newLine.iface));
    newLine.uid = (int32_t)statsKey.uid;
    newLine.set = (int32_t)statsKey.counterSet;
    newLine.tag = (int32_t)statsKey.tag;
    newLine.rxPackets = statsEntry.rxPackets;
    newLine.txPackets = statsEntry.txPackets;
    newLine.rxBytes = statsEntry.rxBytes;
    newLine.txBytes = statsEntry.txBytes;
    return newLine;
}

int parseBpfNetworkStatsDetailInternal(std::vector<stats_line>& lines,
                                       const BpfMapRO<StatsKey, StatsValue>& statsMap,
                                       const IfIndexToNameFunc ifindex2name) {
    int64_t unknownIfaceBytesTotal = 0;
    const auto processDetailUidStats =
            [&lines, &unknownIfaceBytesTotal, &ifindex2name](
                    const StatsKey& key,
                    const BpfMapRO<StatsKey, StatsValue>& statsMap) -> Result<void> {
        Result<IfaceValue> ifname = ifindex2name(key.ifaceIndex);
        if (!ifname.ok()) {
            maybeLogUnknownIface(key.ifaceIndex, statsMap, key, &unknownIfaceBytesTotal);
            return Result<void>();
        }
        Result<StatsValue> statsEntry = statsMap.readValue(key);
        if (!statsEntry.ok()) {
            return base::ResultError(statsEntry.error().message(), statsEntry.error().code());
        }
        stats_line newLine = populateStatsEntry(key, statsEntry.value(), ifname.value());
        lines.push_back(newLine);
        if (newLine.tag) {
            // account tagged traffic in the untagged stats (for historical reasons?)
            newLine.tag = 0;
            lines.push_back(newLine);
        }
        return Result<void>();
    };
    Result<void> res = statsMap.iterate(processDetailUidStats);
    if (!res.ok()) {
        ALOGE("failed to iterate per uid Stats map for detail traffic stats: %s",
              strerror(res.error().code()));
        return -res.error().code();
    }

    // Since eBPF use hash map to record stats, network stats collected from
    // eBPF will be out of order. And the performance of findIndexHinted in
    // NetworkStats will also be impacted.
    //
    // Furthermore, since the StatsKey contains iface index, the network stats
    // reported to framework would create items with the same iface, uid, tag
    // and set, which causes NetworkStats maps wrong item to subtract.
    //
    // Thus, the stats needs to be properly sorted and grouped before reported.
    groupNetworkStats(lines);
    return 0;
}

int parseBpfNetworkStatsDetail(std::vector<stats_line>* lines) {
    static BpfMapRO<uint32_t, uint32_t> configurationMap(CONFIGURATION_MAP_PATH);
    static BpfMap<StatsKey, StatsValue> statsMapA(STATS_MAP_A_PATH);
    static BpfMap<StatsKey, StatsValue> statsMapB(STATS_MAP_B_PATH);
    if (!configurationMap.isOk()) return -1;
    auto configuration = configurationMap.readValue(CURRENT_STATS_MAP_CONFIGURATION_KEY);
    if (!configuration.ok()) {
        ALOGE("Cannot read the old configuration from map: %s",
              configuration.error().message().c_str());
        return -configuration.error().code();
    }
    // The target map for stats reading should be the inactive map, which is opposite
    // from the config value.
    BpfMap<StatsKey, StatsValue> *inactiveStatsMap;
    switch (configuration.value()) {
      case SELECT_MAP_A:
        inactiveStatsMap = &statsMapB;
        break;
      case SELECT_MAP_B:
        inactiveStatsMap = &statsMapA;
        break;
      default:
        ALOGE("%s unknown configuration value: %d", __func__, configuration.value());
        return -EINVAL;
    }

    // It is safe to read and clear the old map now since the
    // networkStatsFactory should call netd to swap the map in advance already.
    // TODO: the above comment feels like it may be obsolete / out of date,
    // since we no longer swap the map via netd binder rpc - though we do
    // still swap it.
    int ret = parseBpfNetworkStatsDetailInternal(*lines, *inactiveStatsMap, ifindex2name);
    if (ret) {
        ALOGE("parse detail network stats failed: %s", strerror(errno));
        return ret;
    }

    Result<void> res = inactiveStatsMap->clear();
    if (!res.ok()) {
        ALOGE("Clean up current stats map failed: %s", strerror(res.error().code()));
        return -res.error().code();
    }

    return 0;
}

int parseBpfNetworkStatsDevInternal(std::vector<stats_line>& lines,
                                    const BpfMapRO<uint32_t, StatsValue>& statsMap,
                                    const IfIndexToNameFunc ifindex2name) {
    int64_t unknownIfaceBytesTotal = 0;
    const auto processDetailIfaceStats = [&lines, &unknownIfaceBytesTotal, ifindex2name, &statsMap](
                                             const uint32_t& key, const StatsValue& value,
                                             const BpfMapRO<uint32_t, StatsValue>&) {
        Result<IfaceValue> ifname = ifindex2name(key);
        if (!ifname.ok()) {
            maybeLogUnknownIface(key, statsMap, key, &unknownIfaceBytesTotal);
            return Result<void>();
        }
        StatsKey fakeKey = {
                .uid = (uint32_t)UID_ALL,
                .tag = (uint32_t)TAG_NONE,
                .counterSet = (uint32_t)SET_ALL,
        };
        lines.push_back(populateStatsEntry(fakeKey, value, ifname.value()));
        return Result<void>();
    };
    Result<void> res = statsMap.iterateWithValue(processDetailIfaceStats);
    if (!res.ok()) {
        ALOGE("failed to iterate per uid Stats map for detail traffic stats: %s",
              strerror(res.error().code()));
        return -res.error().code();
    }

    groupNetworkStats(lines);
    return 0;
}

int parseBpfNetworkStatsDev(std::vector<stats_line>* lines) {
    return parseBpfNetworkStatsDevInternal(*lines, getIfaceStatsMap(), ifindex2name);
}

void groupNetworkStats(std::vector<stats_line>& lines) {
    if (lines.size() <= 1) return;
    std::sort(lines.begin(), lines.end());

    // Similar to std::unique(), but aggregates the duplicates rather than discarding them.
    size_t currentOutput = 0;
    for (size_t i = 1; i < lines.size(); i++) {
        // note that == operator only compares the 'key' portion: iface/uid/tag/set
        if (lines[currentOutput] == lines[i]) {
            // while += operator only affects the 'data' portion: {rx,tx}{Bytes,Packets}
            lines[currentOutput] += lines[i];
        } else {
            // okay, we're done aggregating the current line, move to the next one
            lines[++currentOutput] = lines[i];
        }
    }

    // possibly shrink the vector - currentOutput is the last line with valid data
    lines.resize(currentOutput + 1);
}

// True if lhs equals to rhs, only compare iface, uid, tag and set.
bool operator==(const stats_line& lhs, const stats_line& rhs) {
    return ((lhs.uid == rhs.uid) && (lhs.tag == rhs.tag) && (lhs.set == rhs.set) &&
            !strncmp(lhs.iface, rhs.iface, sizeof(lhs.iface)));
}

// True if lhs is smaller than rhs, only compare iface, uid, tag and set.
bool operator<(const stats_line& lhs, const stats_line& rhs) {
    int ret = strncmp(lhs.iface, rhs.iface, sizeof(lhs.iface));
    if (ret != 0) return ret < 0;
    if (lhs.uid < rhs.uid) return true;
    if (lhs.uid > rhs.uid) return false;
    if (lhs.tag < rhs.tag) return true;
    if (lhs.tag > rhs.tag) return false;
    if (lhs.set < rhs.set) return true;
    if (lhs.set > rhs.set) return false;
    return false;
}

stats_line& stats_line::operator=(const stats_line& rhs) {
    if (this == &rhs) return *this;

    strlcpy(iface, rhs.iface, sizeof(iface));
    uid = rhs.uid;
    set = rhs.set;
    tag = rhs.tag;
    rxPackets = rhs.rxPackets;
    txPackets = rhs.txPackets;
    rxBytes = rhs.rxBytes;
    txBytes = rhs.txBytes;
    return *this;
}

stats_line& stats_line::operator+=(const stats_line& rhs) {
    rxPackets += rhs.rxPackets;
    txPackets += rhs.txPackets;
    rxBytes += rhs.rxBytes;
    txBytes += rhs.txBytes;
    return *this;
}

}  // namespace bpf
}  // namespace android
