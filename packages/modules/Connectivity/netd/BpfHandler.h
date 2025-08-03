/**
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <netdutils/Status.h>
#include "bpf/BpfMap.h"
#include "netd.h"

using android::bpf::BpfMap;
using android::bpf::BpfMapRO;

namespace android {
namespace net {

class BpfHandler {
  public:
    BpfHandler();
    BpfHandler(const BpfHandler&) = delete;
    BpfHandler& operator=(const BpfHandler&) = delete;
    netdutils::Status init(const char* cg2_path);
    /*
     * Tag the socket with the specified tag and uid. In the qtaguid module, the
     * first tag request that grab the spinlock of rb_tree can update the tag
     * information first and other request need to wait until it finish. All the
     * tag request will be addressed in the order of they obtaining the spinlock.
     * In the eBPF implementation, the kernel will try to update the eBPF map
     * entry with the tag request. And the hashmap update process is protected by
     * the spinlock initialized with the map. So the behavior of two modules
     * should be the same. No additional lock needed.
     */
    int tagSocket(int sockFd, uint32_t tag, uid_t chargeUid, uid_t realUid);

    /*
     * The untag process is similar to tag socket and both old qtaguid module and
     * new eBPF module have spinlock inside the kernel for concurrent update. No
     * external lock is required.
     */
    int untagSocket(int sockFd);

  private:
    // For testing
    BpfHandler(uint32_t perUidLimit, uint32_t totalLimit);

    netdutils::Status initMaps();
    bool hasUpdateDeviceStatsPermission(uid_t uid);

    BpfMap<uint64_t, UidTagValue> mCookieTagMap;
    BpfMapRO<StatsKey, StatsValue> mStatsMapA;
    BpfMapRO<StatsKey, StatsValue> mStatsMapB;
    BpfMapRO<uint32_t, uint32_t> mConfigurationMap;
    BpfMapRO<uint32_t, uint8_t> mUidPermissionMap;

    // The limit on the number of stats entries a uid can have in the per uid stats map. BpfHandler
    // will block that specific uid from tagging new sockets after the limit is reached.
    const uint32_t mPerUidStatsEntriesLimit;

    // The limit on the total number of stats entries in the per uid stats map. BpfHandler will
    // block all tagging requests after the limit is reached.
    const uint32_t mTotalUidStatsEntriesLimit;

    // For testing
    friend class BpfHandlerTest;
};

}  // namespace net
}  // namespace android