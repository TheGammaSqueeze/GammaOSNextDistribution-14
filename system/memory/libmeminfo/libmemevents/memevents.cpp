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

#include <bpf/BpfMap.h>
#include <bpf/BpfRingbuf.h>
#include <bpf/WaitForProgsLoaded.h>
#include <fcntl.h>
#include <inttypes.h>
#include <libbpf.h>
#include <sys/epoll.h>
#include <unistd.h>

#include <algorithm>
#include <cstdio>
#include <functional>
#include <optional>
#include <sstream>
#include <string>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/result.h>

#include <memevents/memevents.h>

namespace android {
namespace bpf {
namespace memevents {

static const std::string kClientRingBuffers[MemEventClient::NR_CLIENTS] = {
        MEM_EVENTS_AMS_RB, MEM_EVENTS_LMKD_RB, MEM_EVENTS_TEST_RB};

class MemBpfRingbuf : public BpfRingbufBase {
  public:
    using EventCallback = std::function<void(const mem_event_t&)>;

    /*
     * Non-initializing constructor, requires calling `Initialize` once.
     * This allows us to handle gracefully when we encounter an init
     * error, instead of using a full-constructions that aborts on error.
     */
    MemBpfRingbuf() : BpfRingbufBase(sizeof(mem_event_t)) {}

    /*
     * Initialize the base ringbuffer components. Must be called exactly once.
     */
    base::Result<void> Initialize(const char* path) { return Init(path); }

    /*
     * Consumes all `mem_event_t` messages from the ring buffer, passing them
     * to the callback.
     */
    base::Result<int> ConsumeAll(const EventCallback& callback) {
        return BpfRingbufBase::ConsumeAll([&](const void* mem_event) {
            callback(*reinterpret_cast<const mem_event_t*>(mem_event));
        });
    }

    /*
     * Expose ring buffer file descriptor for polling purposes, not intended for
     * consume directly. To consume use `ConsumeAll()`.
     */
    int getRingBufFd() { return mRingFd.get(); }
};

struct MemBpfAttachment {
    const std::string prog;
    const std::string tpGroup;
    const std::string tpEvent;
    const mem_event_type_t event_type;
};

// clang-format off
static const std::vector<std::vector<struct MemBpfAttachment>> attachments = {
    // AMS
    {
        {
            .prog = MEM_EVENTS_AMS_OOM_MARK_VICTIM_TP,
            .tpGroup = "oom",
            .tpEvent = "mark_victim",
            .event_type = MEM_EVENT_OOM_KILL
        },
    },
    // LMKD
    {
        {
            .prog = MEM_EVENTS_LMKD_VMSCAN_DR_BEGIN_TP,
            .tpGroup = "vmscan",
            .tpEvent = "mm_vmscan_direct_reclaim_begin",
            .event_type = MEM_EVENT_DIRECT_RECLAIM_BEGIN
        },
        {
            .prog = MEM_EVENTS_LMKD_VMSCAN_DR_END_TP,
            .tpGroup = "vmscan",
            .tpEvent = "mm_vmscan_direct_reclaim_end",
            .event_type = MEM_EVENT_DIRECT_RECLAIM_END
        },
    },
    // MemEventsTest
    {
        {
            .prog = MEM_EVENTS_TEST_OOM_MARK_VICTIM_TP,
            .tpGroup = "oom",
            .tpEvent = "mark_victim",
            .event_type = MEM_EVENT_OOM_KILL
        },
    },
    // ... next service/client
};
// clang-format on

static std::optional<MemBpfAttachment> findAttachment(mem_event_type_t event_type,
                                                      MemEventClient client) {
    auto it = std::find_if(attachments[client].begin(), attachments[client].end(),
                           [event_type](const MemBpfAttachment memBpfAttch) {
                               return memBpfAttch.event_type == event_type;
                           });
    if (it == attachments[client].end()) return std::nullopt;
    return it[0];
}

/**
 * Helper function that determines if an event type is valid.
 * We define "valid" as an actual event type that we can listen and register to.
 *
 * @param event_type memory event type to validate.
 * @return true if it's less than `NR_MEM_EVENTS` and greater, or equal, to
 * `MEM_EVENT_BASE`, false otherwise.
 */
bool MemEventListener::isValidEventType(mem_event_type_t event_type) const {
    return event_type < NR_MEM_EVENTS && event_type >= MEM_EVENT_BASE;
}

// Public methods

MemEventListener::MemEventListener(MemEventClient client, bool attachTpForTests) {
    if (client >= MemEventClient::NR_CLIENTS || client < MemEventClient::BASE) {
        LOG(ERROR) << "memevent listener failed to initialize, invalid client: " << client;
        std::abort();
    }

    mClient = client;
    mAttachTpForTests = attachTpForTests;
    std::fill_n(mEventsRegistered, NR_MEM_EVENTS, false);
    mNumEventsRegistered = 0;

    /*
     * This flag allows for the MemoryPressureTest suite to hook into a BPF tracepoint
     * and NOT allowing, this testing instance, to skip any skip internal calls.
     * This flag is only allowed to be set for a testing instance, not for normal clients.
     */
    if (mClient != MemEventClient::TEST_CLIENT && attachTpForTests) {
        LOG(ERROR) << "memevent listener failed to initialize, invalid configuration";
        std::abort();
    }

    memBpfRb = std::make_unique<MemBpfRingbuf>();
    if (auto status = memBpfRb->Initialize(kClientRingBuffers[client].c_str()); !status.ok()) {
        /*
         * We allow for the listener to load gracefully, but we added safeguad
         * throughout the public APIs to prevent the listener to do any actions.
         */
        memBpfRb.reset(nullptr);
        if (isAtLeastKernelVersion(5, 8, 0)) {
            LOG(ERROR) << "memevent listener MemBpfRingbuf init failed: "
                       << status.error().message();
        } else {
            LOG(ERROR) << "memevent listener failed to initialize, not supported kernel";
        }
    }
}

MemEventListener::~MemEventListener() {
    deregisterAllEvents();
}

bool MemEventListener::registerEvent(mem_event_type_t event_type) {
    if (!memBpfRb) {
        LOG(ERROR) << "memevent register failed, failure to initialize";
        return false;
    }
    if (!isValidEventType(event_type)) {
        LOG(ERROR) << "memevent register failed, received invalid event type";
        return false;
    }
    if (mEventsRegistered[event_type]) {
        // We are already registered to this event
        return true;
    }

    if (mClient == MemEventClient::TEST_CLIENT && !mAttachTpForTests) {
        mEventsRegistered[event_type] = true;
        mNumEventsRegistered++;
        return true;
    }

    const std::optional<MemBpfAttachment> maybeAttachment = findAttachment(event_type, mClient);
    if (!maybeAttachment.has_value()) {
        /*
         * Not all clients have access to the same tracepoints, for example,
         * AMS doesn't have a bpf prog for the direct reclaim start/end tracepoints.
         */
        LOG(ERROR) << "memevent register failed, client " << mClient
                   << " doesn't support event: " << event_type;
        return false;
    }

    const auto attachment = maybeAttachment.value();
    int bpf_prog_fd = retrieveProgram(attachment.prog.c_str());
    if (bpf_prog_fd < 0) {
        PLOG(ERROR) << "memevent failed to retrieve pinned program from: " << attachment.prog;
        return false;
    }

    // Attach the bpf program to the tracepoint
    if (bpf_attach_tracepoint(bpf_prog_fd, attachment.tpGroup.c_str(), attachment.tpEvent.c_str()) <
        0) {
        PLOG(ERROR) << "memevent failed to attach bpf program to " << attachment.tpGroup << "/"
                    << attachment.tpEvent << " tracepoint";
        return false;
    }

    mEventsRegistered[event_type] = true;
    mNumEventsRegistered++;
    return true;
}

bool MemEventListener::listen(int timeout_ms) {
    if (!memBpfRb) {
        LOG(ERROR) << "memevent listen failed, failure to initialize";
        return false;
    }
    if (mNumEventsRegistered == 0) {
        LOG(ERROR) << "memevents listen failed, not registered to any events";
        return false;
    }

    return memBpfRb->wait(timeout_ms);
}

bool MemEventListener::deregisterEvent(mem_event_type_t event_type) {
    if (!memBpfRb) {
        LOG(ERROR) << "memevent failed to deregister, failure to initialize";
        return false;
    }
    if (!isValidEventType(event_type)) {
        LOG(ERROR) << "memevent failed to deregister, invalid event type";
        return false;
    }

    if (!mEventsRegistered[event_type]) return true;

    if (mClient == MemEventClient::TEST_CLIENT && !mAttachTpForTests) {
        mEventsRegistered[event_type] = false;
        mNumEventsRegistered--;
        return true;
    }

    const std::optional<MemBpfAttachment> maybeAttachment = findAttachment(event_type, mClient);
    if (!maybeAttachment.has_value()) {
        /*
         * We never expect to get here since the listener wouldn't have been to register this
         * `event_type` in the first place.
         */
        LOG(ERROR) << "memevent failed deregister event " << event_type
                   << ", not tp attachment found";
        return false;
    }

    const auto attachment = maybeAttachment.value();
    if (bpf_detach_tracepoint(attachment.tpGroup.c_str(), attachment.tpEvent.c_str()) < 0) {
        PLOG(ERROR) << "memevent failed to deregister event " << event_type << " from bpf prog to "
                    << attachment.tpGroup << "/" << attachment.tpEvent << " tracepoint";
        return false;
    }

    mEventsRegistered[event_type] = false;
    mNumEventsRegistered--;
    return true;
}

void MemEventListener::deregisterAllEvents() {
    if (!memBpfRb) {
        LOG(ERROR) << "memevent deregister all events failed, failure to initialize";
        return;
    }
    if (mNumEventsRegistered == 0) return;
    for (int i = 0; i < NR_MEM_EVENTS; i++) {
        if (mEventsRegistered[i]) deregisterEvent(i);
    }
}

bool MemEventListener::getMemEvents(std::vector<mem_event_t>& mem_events) {
    if (!memBpfRb) {
        LOG(ERROR) << "memevent failed getting memory events, failure to initialize";
        return false;
    }

    base::Result<int> ret = memBpfRb->ConsumeAll([&](const mem_event_t& mem_event) {
        if (mEventsRegistered[mem_event.type]) mem_events.emplace_back(mem_event);
    });

    if (!ret.ok()) {
        LOG(ERROR) << "memevent failed getting memory events: " << ret.error().message();
        return false;
    }

    return true;
}

int MemEventListener::getRingBufferFd() {
    if (!memBpfRb) {
        LOG(ERROR) << "memevent failed getting ring-buffer fd, failure to initialize";
        return -1;
    }
    return memBpfRb->getRingBufFd();
}

}  // namespace memevents
}  // namespace bpf
}  // namespace android
