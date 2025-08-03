/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define LOG_TAG "NetworkTrace"
#define ATRACE_TAG ATRACE_TAG_NETWORK

#include "netdbpf/NetworkTracePoller.h"

#include <bpf/BpfUtils.h>
#include <cutils/trace.h>
#include <log/log.h>
#include <perfetto/tracing/platform.h>
#include <perfetto/tracing/tracing.h>

#include <unordered_map>
#include <unordered_set>

#include "netdbpf/BpfNetworkStats.h"

namespace android {
namespace bpf {
namespace internal {
using ::android::base::StringPrintf;

void NetworkTracePoller::PollAndSchedule(perfetto::base::TaskRunner* runner,
                                         uint32_t poll_ms) {
  // Always schedule another run of ourselves to recursively poll periodically.
  // The task runner is sequential so these can't run on top of each other.
  runner->PostDelayedTask([=]() { PollAndSchedule(runner, poll_ms); }, poll_ms);

  if (mMutex.try_lock()) {
    ConsumeAllLocked();
    mMutex.unlock();
  }
}

bool NetworkTracePoller::Start(uint32_t pollMs) {
  ALOGD("Starting datasource");

  std::scoped_lock<std::mutex> lock(mMutex);
  if (mSessionCount > 0) {
    if (mPollMs != pollMs) {
      // Nothing technical prevents mPollMs from changing, it's just unclear
      // what the right behavior is. Taking the min of active values could poll
      // too frequently giving some sessions too much data. Taking the max could
      // be too infrequent. For now, do nothing.
      ALOGI("poll_ms can't be changed while running, ignoring poll_ms=%d",
            pollMs);
    }
    mSessionCount++;
    return true;
  }

  auto status = mConfigurationMap.init(PACKET_TRACE_ENABLED_MAP_PATH);
  if (!status.ok()) {
    ALOGW("Failed to bind config map: %s", status.error().message().c_str());
    return false;
  }

  auto rb = BpfRingbuf<PacketTrace>::Create(PACKET_TRACE_RINGBUF_PATH);
  if (!rb.ok()) {
    ALOGW("Failed to create ringbuf: %s", rb.error().message().c_str());
    return false;
  }

  mRingBuffer = std::move(*rb);

  auto res = mConfigurationMap.writeValue(0, true, BPF_ANY);
  if (!res.ok()) {
    ALOGW("Failed to enable tracing: %s", res.error().message().c_str());
    return false;
  }

  // Start a task runner to run ConsumeAll every mPollMs milliseconds.
  mTaskRunner = perfetto::Platform::GetDefaultPlatform()->CreateTaskRunner({});
  mPollMs = pollMs;
  PollAndSchedule(mTaskRunner.get(), mPollMs);

  mSessionCount++;
  return true;
}

bool NetworkTracePoller::Stop() {
  ALOGD("Stopping datasource");

  std::scoped_lock<std::mutex> lock(mMutex);
  if (mSessionCount == 0) return false;  // This should never happen

  // If this isn't the last session, don't clean up yet.
  if (--mSessionCount > 0) return true;

  auto res = mConfigurationMap.writeValue(0, false, BPF_ANY);
  if (!res.ok()) {
    ALOGW("Failed to disable tracing: %s", res.error().message().c_str());
  }

  // Make sure everything in the system has actually seen the 'false' we just
  // wrote, things should now be well and truly disabled.
  synchronizeKernelRCU();

  // Drain remaining events from the ring buffer now that tracing is disabled.
  // This prevents the next trace from seeing stale events and allows writing
  // the last batch of events to Perfetto.
  ConsumeAllLocked();

  mTaskRunner.reset();
  mRingBuffer.reset();

  return res.ok();
}

void NetworkTracePoller::TraceIfaces(const std::vector<PacketTrace>& packets) {
  if (packets.empty()) return;

  std::unordered_set<uint32_t> uniqueIfindex;
  for (const PacketTrace& pkt : packets) {
    uniqueIfindex.insert(pkt.ifindex);
  }

  for (uint32_t ifindex : uniqueIfindex) {
    char ifname[IF_NAMESIZE] = {};
    if (if_indextoname(ifindex, ifname) != ifname) continue;

    StatsValue stats = {};
    if (bpfGetIfIndexStats(ifindex, &stats) != 0) continue;

    std::string rxTrack = StringPrintf("%s [%d] Rx Bytes", ifname, ifindex);
    std::string txTrack = StringPrintf("%s [%d] Tx Bytes", ifname, ifindex);
    ATRACE_INT64(rxTrack.c_str(), stats.rxBytes);
    ATRACE_INT64(txTrack.c_str(), stats.txBytes);
  }
}

bool NetworkTracePoller::ConsumeAll() {
  std::scoped_lock<std::mutex> lock(mMutex);
  return ConsumeAllLocked();
}

bool NetworkTracePoller::ConsumeAllLocked() {
  if (mRingBuffer == nullptr) {
    ALOGW("Tracing is not active");
    return false;
  }

  std::vector<PacketTrace> packets;
  base::Result<int> ret = mRingBuffer->ConsumeAll(
      [&](const PacketTrace& pkt) { packets.push_back(pkt); });
  if (!ret.ok()) {
    ALOGW("Failed to poll ringbuf: %s", ret.error().message().c_str());
    return false;
  }

  ATRACE_INT("NetworkTracePackets", packets.size());

  TraceIfaces(packets);
  mCallback(packets);

  return true;
}

}  // namespace internal
}  // namespace bpf
}  // namespace android
