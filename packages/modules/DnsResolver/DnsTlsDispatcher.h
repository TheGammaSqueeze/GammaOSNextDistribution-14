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

#ifndef _DNS_DNSTLSDISPATCHER_H
#define _DNS_DNSTLSDISPATCHER_H

#include <list>
#include <map>
#include <memory>
#include <mutex>

#include <android-base/thread_annotations.h>
#include <netdutils/Slice.h>

#include "DnsTlsServer.h"
#include "DnsTlsTransport.h"
#include "IDnsTlsSocketFactory.h"
#include "PrivateDnsValidationObserver.h"
#include "resolv_private.h"

namespace android {
namespace net {

// This is a singleton class that manages the collection of active DnsTlsTransports.
// Queries made here are dispatched to an existing or newly constructed DnsTlsTransport.
// TODO: PrivateDnsValidationObserver is not implemented in this class. Remove it.
class DnsTlsDispatcher : public PrivateDnsValidationObserver {
  public:
    // Constructor with dependency injection for testing.
    explicit DnsTlsDispatcher(std::unique_ptr<IDnsTlsSocketFactory> factory)
        : mFactory(std::move(factory)) {}

    static DnsTlsDispatcher& getInstance();

    // Enqueues |query| for resolution via the given |tlsServers| on the
    // network indicated by |mark|; writes the response into |ans|, and stores
    // the count of bytes written in |resplen|. Returns a success or error code.
    // The order in which servers from |tlsServers| are queried may not be the
    // order passed in by the caller.
    DnsTlsTransport::Response query(const std::list<DnsTlsServer>& tlsServers,
                                    ResState* _Nonnull statp, const netdutils::Slice query,
                                    const netdutils::Slice ans, int* _Nonnull resplen,
                                    bool dotQuickFallback);

    // Given a |query|, sends it to the server on the network indicated by |mark|,
    // and writes the response into |ans|, and indicates the number of bytes written in |resplen|.
    // If the whole procedure above triggers (or experiences) any new connection, |connectTriggered|
    // is set. Returns a success or error code.
    DnsTlsTransport::Response query(const DnsTlsServer& server, unsigned netId, unsigned mark,
                                    const netdutils::Slice query, const netdutils::Slice ans,
                                    int* _Nonnull resplen, bool* _Nonnull connectTriggered);

    // Implement PrivateDnsValidationObserver.
    void onValidationStateUpdate(const std::string&, Validation, uint32_t) override{};

    void forceCleanup(unsigned netId) EXCLUDES(sLock);

  private:
    DnsTlsDispatcher();

    // This lock is static so that it can be used to annotate the Transport struct.
    // DnsTlsDispatcher is a singleton in practice, so making this static does not change
    // the locking behavior.
    static std::mutex sLock;

    // Key = <mark, server>
    typedef std::pair<unsigned, const DnsTlsServer> Key;

    // Transport is a thin wrapper around DnsTlsTransport, adding reference counting and
    // usage monitoring so we can expire idle sessions from the cache.
    struct Transport {
        Transport(const DnsTlsServer& server, unsigned mark, unsigned netId,
                  IDnsTlsSocketFactory* _Nonnull factory, int triggerThr, int unusableThr,
                  int timeout)
            : transport(server, mark, factory),
              mNetId(netId),
              triggerThreshold(triggerThr),
              unusableThreshold(unusableThr),
              mTimeout(timeout) {}

        // DnsTlsTransport is thread-safe, so it doesn't need to be guarded.
        DnsTlsTransport transport;

        // The expected network, assigned from dns_netid, to which Transport will send DNS packets.
        const unsigned mNetId;

        // This use counter and timestamp are used to ensure that only idle sessions are
        // destroyed.
        int useCount GUARDED_BY(sLock) = 0;
        // lastUsed is only guaranteed to be meaningful after useCount is decremented to zero.
        std::chrono::time_point<std::chrono::steady_clock> lastUsed GUARDED_BY(sLock);

        // If DoT revalidation is disabled, it returns true; otherwise, it returns
        // whether or not this Transport is usable.
        bool usable() REQUIRES(sLock);

        // Used to track if this Transport is usable.
        int continuousfailureCount GUARDED_BY(sLock) = 0;

        bool checkRevalidationNecessary() REQUIRES(sLock);

        std::chrono::milliseconds timeout() const { return mTimeout; }

        static constexpr int kDotRevalidationThreshold = -1;
        static constexpr int kDotXportUnusableThreshold = -1;
        static constexpr int kDotQueryTimeoutMs = -1;

      private:
        // The flag to record whether or not dot_revalidation_threshold is ever reached.
        bool isRevalidationThresholdReached GUARDED_BY(sLock) = false;

        // The flag to record whether or not dot_xport_unusable_threshold is ever reached.
        bool isXportUnusableThresholdReached GUARDED_BY(sLock) = false;

        // If the number of continuous query timeouts reaches the threshold, mark the
        // server as unvalidated and trigger a validation.
        // If the value is not a positive value or private DNS mode is strict mode, no threshold is
        // set. Note that it must be at least 10, or it breaks
        // ConnectTlsServerTimeout_ConcurrentQueries test.
        const int triggerThreshold;

        // The threshold to determine if this Transport is considered unusable.
        // If the number of continuous query timeouts reaches the threshold, mark this
        // Transport as unusable. An unusable Transport won't be used anymore.
        // If the value is not a positive value or private DNS mode is strict mode, no threshold is
        // set.
        const int unusableThreshold;

        // The time to await a future (the result of a DNS request) from the DnsTlsTransport
        // of this Transport.
        // To set an infinite timeout, assign the value to -1.
        const std::chrono::milliseconds mTimeout;
    };

    Transport* _Nullable addTransport(const DnsTlsServer& server, unsigned mark, unsigned netId)
            REQUIRES(sLock);
    Transport* _Nullable getTransport(const Key& key) REQUIRES(sLock);

    // Cache of reusable DnsTlsTransports.  Transports stay in cache as long as
    // they are in use and for a few minutes after.
    std::map<Key, std::unique_ptr<Transport>> mStore GUARDED_BY(sLock);

    // The last time we did a cleanup.  For efficiency, we only perform a cleanup once every
    // few minutes.
    std::chrono::time_point<std::chrono::steady_clock> mLastCleanup GUARDED_BY(sLock);

    DnsTlsTransport::Result queryInternal(Transport& transport, const netdutils::Slice query)
            EXCLUDES(sLock);

    void maybeCleanup(std::chrono::time_point<std::chrono::steady_clock> now) REQUIRES(sLock);

    // Drop any cache entries whose useCount is zero and which have not been used recently.
    // This function performs a linear scan of mStore.
    void cleanup(std::chrono::time_point<std::chrono::steady_clock> now,
                 std::optional<unsigned> netId) REQUIRES(sLock);

    // Return a sorted list of usable DnsTlsServers in preference order.
    std::list<DnsTlsServer> getOrderedAndUsableServerList(const std::list<DnsTlsServer>& tlsServers,
                                                          unsigned netId, unsigned mark);

    // Trivial factory for DnsTlsSockets.  Dependency injection is only used for testing.
    std::unique_ptr<IDnsTlsSocketFactory> mFactory;
};

}  // end of namespace net
}  // end of namespace android

#endif  // _DNS_DNSTLSDISPATCHER_H
