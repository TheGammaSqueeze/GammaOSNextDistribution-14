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

#pragma once

#include <memory>
#include <vector>

#include <stddef.h>

#include <memevents/bpf_types.h>

namespace android {
namespace bpf {
namespace memevents {

enum MemEventClient {
    // BASE should always be first, used for lower-bound checks
    BASE = 0,
    AMS = BASE,
    LMKD,
    /*
     * Flag to indicate whether this `MemEventListener` instance is used for
     * testing purposes. This allows us to skip internal calls that would
     * otherwise interfere with test setup, and mocks for BPF ring buffer,
     * and BPF program behavior.
     */
    TEST_CLIENT,
    // NR_CLIENTS should always come after the last valid client
    NR_CLIENTS
};

class MemBpfRingbuf;

class MemEventListener final {
  public:
    MemEventListener(MemEventClient client, bool attachTpForTests = false);
    ~MemEventListener();

    /**
     * Registers the requested memory event to the listener.
     *
     * @param event_type Memory event type to listen for.
     * @return true if registration was successful, false otherwise.
     */
    bool registerEvent(mem_event_type_t event_type);

    /**
     * Waits for a [registered] memory event notification.
     *
     * `listen()` will block until either:
     *    - Receives a registered memory event
     *    - Timeout expires
     *
     * Note that the default timeout (-1) causes `listen()` to block
     * indefinitely.
     *
     * @param timeout_ms number of milliseconds that listen will block.
     * @return true if there are new memory events to read, false otherwise.
     */
    bool listen(int timeout_ms = -1);

    /**
     * Stops listening for a specific memory event type.
     *
     * @param event_type Memory event type to stop listening to.
     * @return true if unregistering was successful, false otherwise
     */
    bool deregisterEvent(mem_event_type_t event_type);

    /**
     * Closes all the events' file descriptors and `mEpfd`. This will also
     * gracefully terminate any ongoing `listen()`.
     */
    void deregisterAllEvents();

    /**
     * Retrieves unread [registered] memory events, and stores them into the
     * provided `mem_events` vector.
     *
     * On first invocation, it will read/store all memory events. After the
     * initial invocation, it will only read/store new, unread, events.
     *
     * @param mem_events vector in which we want to append the read
     * memory events.
     * @return true on success, false on failure.
     */
    bool getMemEvents(std::vector<mem_event_t>& mem_events);

    /**
     * Expose the MemEventClient's ring-buffer file descriptor for polling purposes,
     * not intended for consumption. To consume use `ConsumeAll()`.
     *
     * @return file descriptor (non negative integer), -1 on error.
     */
    int getRingBufferFd();

  private:
    bool mEventsRegistered[NR_MEM_EVENTS];
    int mNumEventsRegistered;
    MemEventClient mClient;
    std::unique_ptr<MemBpfRingbuf> memBpfRb;
    bool mAttachTpForTests;

    bool isValidEventType(mem_event_type_t event_type) const;
};

}  // namespace memevents
}  // namespace bpf
}  // namespace android
