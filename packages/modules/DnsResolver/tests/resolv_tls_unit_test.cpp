/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "resolv"

#include <arpa/inet.h>

#include <chrono>

#include <android-base/logging.h>
#include <android-base/macros.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <netdutils/NetNativeTestBase.h>
#include <netdutils/Slice.h>

#include "DnsTlsDispatcher.h"
#include "DnsTlsQueryMap.h"
#include "DnsTlsServer.h"
#include "DnsTlsSessionCache.h"
#include "DnsTlsSocket.h"
#include "DnsTlsTransport.h"
#include "Experiments.h"
#include "IDnsTlsSocket.h"
#include "IDnsTlsSocketFactory.h"
#include "IDnsTlsSocketObserver.h"
#include "tests/dns_responder/dns_tls_frontend.h"

namespace android {
namespace net {

using netdutils::IPAddress;
using netdutils::IPSockAddr;
using netdutils::makeSlice;
using netdutils::Slice;

typedef std::vector<uint8_t> bytevec;

static const std::string DOT_MAXTRIES_FLAG = "dot_maxtries";
static const std::string SERVERNAME1 = "dns.example.com";
static const std::string SERVERNAME2 = "dns.example.org";
static const IPAddress V4ADDR1 = IPAddress::forString("192.0.2.1");
static const IPAddress V4ADDR2 = IPAddress::forString("192.0.2.2");
static const IPAddress V6ADDR1 = IPAddress::forString("2001:db8::1");
static const IPAddress V6ADDR2 = IPAddress::forString("2001:db8::2");

// BaseTest just provides constants that are useful for the tests.
class BaseTest : public NetNativeTestBase {
  protected:
    BaseTest() {
        SERVER1.name = SERVERNAME1;
    }

    DnsTlsServer SERVER1{V4ADDR1};
};

bytevec make_query(uint16_t id, size_t size) {
    bytevec vec(size);
    vec[0] = id >> 8;
    vec[1] = id;
    // Arbitrarily fill the query body with unique data.
    for (size_t i = 2; i < size; ++i) {
        vec[i] = id + i;
    }
    return vec;
}

// Query constants
const unsigned NETID = 123;
const unsigned MARK = 123;
const uint16_t ID = 52;
const uint16_t SIZE = 22;
const bytevec QUERY = make_query(ID, SIZE);

template <class T>
class FakeSocketFactory : public IDnsTlsSocketFactory {
  public:
    FakeSocketFactory() {}
    std::unique_ptr<IDnsTlsSocket> createDnsTlsSocket(
            const DnsTlsServer& server ATTRIBUTE_UNUSED,
            unsigned mark ATTRIBUTE_UNUSED,
            IDnsTlsSocketObserver* observer,
            DnsTlsSessionCache* cache ATTRIBUTE_UNUSED) override {
        return std::make_unique<T>(observer);
    }
};

bytevec make_echo(uint16_t id, const Slice query) {
    bytevec response(query.size() + 2);
    response[0] = id >> 8;
    response[1] = id;
    // Echo the query as the fake response.
    memcpy(response.data() + 2, query.base(), query.size());
    return response;
}

// Simplest possible fake server.  This just echoes the query as the response.
class FakeSocketEcho : public IDnsTlsSocket {
  public:
    explicit FakeSocketEcho(IDnsTlsSocketObserver* observer) : mObserver(observer) {}
    bool query(uint16_t id, const Slice query) override {
        // Return the response immediately (asynchronously).
        std::thread(&IDnsTlsSocketObserver::onResponse, mObserver, make_echo(id, query)).detach();
        return true;
    }
    bool startHandshake() override { return true; }

  private:
    IDnsTlsSocketObserver* const mObserver;
};

class TransportTest : public BaseTest {};

TEST_F(TransportTest, Query) {
    FakeSocketFactory<FakeSocketEcho> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    auto r = transport.query(makeSlice(QUERY)).get();

    EXPECT_EQ(DnsTlsTransport::Response::success, r.code);
    EXPECT_EQ(QUERY, r.response);
    EXPECT_EQ(transport.getConnectCounter(), 1);
}

// Fake Socket that echoes the observed query ID as the response body.
class FakeSocketId : public IDnsTlsSocket {
  public:
    explicit FakeSocketId(IDnsTlsSocketObserver* observer) : mObserver(observer) {}
    bool query(uint16_t id, const Slice query ATTRIBUTE_UNUSED) override {
        // Return the response immediately (asynchronously).
        bytevec response(4);
        // Echo the ID in the header to match the response to the query.
        // This will be overwritten by DnsTlsQueryMap.
        response[0] = id >> 8;
        response[1] = id;
        // Echo the ID in the body, so that the test can verify which ID was used by
        // DnsTlsQueryMap.
        response[2] = id >> 8;
        response[3] = id;
        std::thread(&IDnsTlsSocketObserver::onResponse, mObserver, response).detach();
        return true;
    }
    bool startHandshake() override { return true; }

  private:
    IDnsTlsSocketObserver* const mObserver;
};

// Test that IDs are properly reused
TEST_F(TransportTest, IdReuse) {
    FakeSocketFactory<FakeSocketId> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    for (int i = 0; i < 100; ++i) {
        // Send a query.
        std::future<DnsTlsTransport::Result> f = transport.query(makeSlice(QUERY));
        // Wait for the response.
        DnsTlsTransport::Result r = f.get();
        EXPECT_EQ(DnsTlsTransport::Response::success, r.code);

        // All queries should have an observed ID of zero, because it is returned to the ID pool
        // after each use.
        EXPECT_EQ(0, (r.response[2] << 8) | r.response[3]);
    }
    EXPECT_EQ(transport.getConnectCounter(), 1);
}

// These queries might be handled in serial or parallel as they race the
// responses.
TEST_F(TransportTest, RacingQueries_10000) {
    FakeSocketFactory<FakeSocketEcho> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    std::vector<std::future<DnsTlsTransport::Result>> results;
    // Fewer than 65536 queries to avoid ID exhaustion.
    const int num_queries = 10000;
    results.reserve(num_queries);
    for (int i = 0; i < num_queries; ++i) {
        results.push_back(transport.query(makeSlice(QUERY)));
    }
    for (auto& result : results) {
        auto r = result.get();
        EXPECT_EQ(DnsTlsTransport::Response::success, r.code);
        EXPECT_EQ(QUERY, r.response);
    }
    EXPECT_EQ(transport.getConnectCounter(), 1);
}

// A server that waits until sDelay queries are queued before responding.
class FakeSocketDelay : public IDnsTlsSocket {
  public:
    explicit FakeSocketDelay(IDnsTlsSocketObserver* observer) : mObserver(observer) {}
    ~FakeSocketDelay() {
        std::lock_guard guard(mLock);
        sDelay = 1;
        sReverse = false;
        sConnectable = true;
    }
    inline static size_t sDelay = 1;
    inline static bool sReverse = false;
    inline static bool sConnectable = true;

    bool query(uint16_t id, const Slice query) override {
        LOG(DEBUG) << "FakeSocketDelay got query with ID " << int(id);
        std::lock_guard guard(mLock);
        // Check for duplicate IDs.
        EXPECT_EQ(0U, mIds.count(id));
        mIds.insert(id);

        // Store response.
        mResponses.push_back(make_echo(id, query));

        LOG(DEBUG) << "Up to " << mResponses.size() << " out of " << sDelay << " queries";
        if (mResponses.size() == sDelay) {
            std::thread(&FakeSocketDelay::sendResponses, this).detach();
        }
        return true;
    }
    bool startHandshake() override { return sConnectable; }

  private:
    void sendResponses() {
        std::lock_guard guard(mLock);
        if (sReverse) {
            std::reverse(std::begin(mResponses), std::end(mResponses));
        }
        for (auto& response : mResponses) {
            mObserver->onResponse(response);
        }
        mIds.clear();
        mResponses.clear();
    }

    std::mutex mLock;
    IDnsTlsSocketObserver* const mObserver;
    std::set<uint16_t> mIds GUARDED_BY(mLock);
    std::vector<bytevec> mResponses GUARDED_BY(mLock);
};

TEST_F(TransportTest, ParallelColliding) {
    FakeSocketDelay::sDelay = 10;
    FakeSocketDelay::sReverse = false;
    FakeSocketFactory<FakeSocketDelay> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    std::vector<std::future<DnsTlsTransport::Result>> results;
    // Fewer than 65536 queries to avoid ID exhaustion.
    results.reserve(FakeSocketDelay::sDelay);
    for (size_t i = 0; i < FakeSocketDelay::sDelay; ++i) {
        results.push_back(transport.query(makeSlice(QUERY)));
    }
    for (auto& result : results) {
        auto r = result.get();
        EXPECT_EQ(DnsTlsTransport::Response::success, r.code);
        EXPECT_EQ(QUERY, r.response);
    }
    EXPECT_EQ(transport.getConnectCounter(), 1);
}

TEST_F(TransportTest, ParallelColliding_Max) {
    FakeSocketDelay::sDelay = 65536;
    FakeSocketDelay::sReverse = false;
    FakeSocketFactory<FakeSocketDelay> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    std::vector<std::future<DnsTlsTransport::Result>> results;
    // Exactly 65536 queries should still be possible in parallel,
    // even if they all have the same original ID.
    results.reserve(FakeSocketDelay::sDelay);
    for (size_t i = 0; i < FakeSocketDelay::sDelay; ++i) {
        results.push_back(transport.query(makeSlice(QUERY)));
    }
    for (auto& result : results) {
        auto r = result.get();
        EXPECT_EQ(DnsTlsTransport::Response::success, r.code);
        EXPECT_EQ(QUERY, r.response);
    }
    EXPECT_EQ(transport.getConnectCounter(), 1);
}

TEST_F(TransportTest, ParallelUnique) {
    FakeSocketDelay::sDelay = 10;
    FakeSocketDelay::sReverse = false;
    FakeSocketFactory<FakeSocketDelay> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    std::vector<bytevec> queries(FakeSocketDelay::sDelay);
    std::vector<std::future<DnsTlsTransport::Result>> results;
    results.reserve(FakeSocketDelay::sDelay);
    for (size_t i = 0; i < FakeSocketDelay::sDelay; ++i) {
        queries[i] = make_query(i, SIZE);
        results.push_back(transport.query(makeSlice(queries[i])));
    }
    for (size_t i = 0 ; i < FakeSocketDelay::sDelay; ++i) {
        auto r = results[i].get();
        EXPECT_EQ(DnsTlsTransport::Response::success, r.code);
        EXPECT_EQ(queries[i], r.response);
    }
    EXPECT_EQ(transport.getConnectCounter(), 1);
}

TEST_F(TransportTest, ParallelUnique_Max) {
    FakeSocketDelay::sDelay = 65536;
    FakeSocketDelay::sReverse = false;
    FakeSocketFactory<FakeSocketDelay> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    std::vector<bytevec> queries(FakeSocketDelay::sDelay);
    std::vector<std::future<DnsTlsTransport::Result>> results;
    // Exactly 65536 queries should still be possible in parallel,
    // and they should all be mapped correctly back to the original ID.
    results.reserve(FakeSocketDelay::sDelay);
    for (size_t i = 0; i < FakeSocketDelay::sDelay; ++i) {
        queries[i] = make_query(i, SIZE);
        results.push_back(transport.query(makeSlice(queries[i])));
    }
    for (size_t i = 0 ; i < FakeSocketDelay::sDelay; ++i) {
        auto r = results[i].get();
        EXPECT_EQ(DnsTlsTransport::Response::success, r.code);
        EXPECT_EQ(queries[i], r.response);
    }
    EXPECT_EQ(transport.getConnectCounter(), 1);
}

TEST_F(TransportTest, IdExhaustion) {
    const int num_queries = 65536;
    // A delay of 65537 is unreachable, because the maximum number
    // of outstanding queries is 65536.
    FakeSocketDelay::sDelay = num_queries + 1;
    FakeSocketDelay::sReverse = false;
    FakeSocketFactory<FakeSocketDelay> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    std::vector<std::future<DnsTlsTransport::Result>> results;
    // Issue the maximum number of queries.
    results.reserve(num_queries);
    for (int i = 0; i < num_queries; ++i) {
        results.push_back(transport.query(makeSlice(QUERY)));
    }

    // The ID space is now full, so subsequent queries should fail immediately.
    auto r = transport.query(makeSlice(QUERY)).get();
    EXPECT_EQ(DnsTlsTransport::Response::internal_error, r.code);
    EXPECT_TRUE(r.response.empty());

    for (auto& result : results) {
        // All other queries should remain outstanding.
        EXPECT_EQ(std::future_status::timeout,
                result.wait_for(std::chrono::duration<int>::zero()));
    }
    EXPECT_EQ(transport.getConnectCounter(), 1);
}

// Responses can come back from the server in any order.  This should have no
// effect on Transport's observed behavior.
TEST_F(TransportTest, ReverseOrder) {
    FakeSocketDelay::sDelay = 10;
    FakeSocketDelay::sReverse = true;
    FakeSocketFactory<FakeSocketDelay> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    std::vector<bytevec> queries(FakeSocketDelay::sDelay);
    std::vector<std::future<DnsTlsTransport::Result>> results;
    results.reserve(FakeSocketDelay::sDelay);
    for (size_t i = 0; i < FakeSocketDelay::sDelay; ++i) {
        queries[i] = make_query(i, SIZE);
        results.push_back(transport.query(makeSlice(queries[i])));
    }
    for (size_t i = 0 ; i < FakeSocketDelay::sDelay; ++i) {
        auto r = results[i].get();
        EXPECT_EQ(DnsTlsTransport::Response::success, r.code);
        EXPECT_EQ(queries[i], r.response);
    }
    EXPECT_EQ(transport.getConnectCounter(), 1);
}

TEST_F(TransportTest, ReverseOrder_Max) {
    FakeSocketDelay::sDelay = 65536;
    FakeSocketDelay::sReverse = true;
    FakeSocketFactory<FakeSocketDelay> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    std::vector<bytevec> queries(FakeSocketDelay::sDelay);
    std::vector<std::future<DnsTlsTransport::Result>> results;
    results.reserve(FakeSocketDelay::sDelay);
    for (size_t i = 0; i < FakeSocketDelay::sDelay; ++i) {
        queries[i] = make_query(i, SIZE);
        results.push_back(transport.query(makeSlice(queries[i])));
    }
    for (size_t i = 0 ; i < FakeSocketDelay::sDelay; ++i) {
        auto r = results[i].get();
        EXPECT_EQ(DnsTlsTransport::Response::success, r.code);
        EXPECT_EQ(queries[i], r.response);
    }
    EXPECT_EQ(transport.getConnectCounter(), 1);
}

// Returning null from the factory indicates a connection failure.
class NullSocketFactory : public IDnsTlsSocketFactory {
  public:
    NullSocketFactory() {}
    std::unique_ptr<IDnsTlsSocket> createDnsTlsSocket(
            const DnsTlsServer& server ATTRIBUTE_UNUSED,
            unsigned mark ATTRIBUTE_UNUSED,
            IDnsTlsSocketObserver* observer ATTRIBUTE_UNUSED,
            DnsTlsSessionCache* cache ATTRIBUTE_UNUSED) override {
        return nullptr;
    }
};

TEST_F(TransportTest, ConnectFail) {
    // Failure on creating socket.
    NullSocketFactory factory1;
    DnsTlsTransport transport1(SERVER1, MARK, &factory1);
    auto r = transport1.query(makeSlice(QUERY)).get();

    EXPECT_EQ(DnsTlsTransport::Response::network_error, r.code);
    EXPECT_TRUE(r.response.empty());
    EXPECT_EQ(transport1.getConnectCounter(), 1);

    // Failure on handshaking.
    FakeSocketDelay::sConnectable = false;
    FakeSocketFactory<FakeSocketDelay> factory2;
    DnsTlsTransport transport2(SERVER1, MARK, &factory2);
    r = transport2.query(makeSlice(QUERY)).get();

    EXPECT_EQ(DnsTlsTransport::Response::network_error, r.code);
    EXPECT_TRUE(r.response.empty());
    EXPECT_EQ(transport2.getConnectCounter(), 1);
}

// Simulate a socket that connects but then immediately receives a server
// close notification.
class FakeSocketClose : public IDnsTlsSocket {
  public:
    explicit FakeSocketClose(IDnsTlsSocketObserver* observer)
        : mCloser(&IDnsTlsSocketObserver::onClosed, observer) {}
    ~FakeSocketClose() { mCloser.join(); }
    bool query(uint16_t id ATTRIBUTE_UNUSED,
               const Slice query ATTRIBUTE_UNUSED) override {
        return true;
    }
    bool startHandshake() override { return true; }

  private:
    std::thread mCloser;
};

TEST_F(TransportTest, CloseRetryFail) {
    FakeSocketFactory<FakeSocketClose> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    auto r = transport.query(makeSlice(QUERY)).get();

    EXPECT_EQ(DnsTlsTransport::Response::network_error, r.code);
    EXPECT_TRUE(r.response.empty());

    // Reconnections might be triggered depending on the flag.
    EXPECT_EQ(transport.getConnectCounter(),
              Experiments::getInstance()->getFlag(DOT_MAXTRIES_FLAG, DnsTlsQueryMap::kMaxTries));
}

// Simulate a server that occasionally closes the connection and silently
// drops some queries.
class FakeSocketLimited : public IDnsTlsSocket {
  public:
    static int sLimit;  // Number of queries to answer per socket.
    static size_t sMaxSize;  // Silently discard queries greater than this size.
    explicit FakeSocketLimited(IDnsTlsSocketObserver* observer)
        : mObserver(observer), mQueries(0) {}
    ~FakeSocketLimited() {
        {
            LOG(DEBUG) << "~FakeSocketLimited acquiring mLock";
            std::lock_guard guard(mLock);
            LOG(DEBUG) << "~FakeSocketLimited acquired mLock";
            for (auto& thread : mThreads) {
                LOG(DEBUG) << "~FakeSocketLimited joining response thread";
                thread.join();
                LOG(DEBUG) << "~FakeSocketLimited joined response thread";
            }
            mThreads.clear();
        }

        if (mCloser) {
            LOG(DEBUG) << "~FakeSocketLimited joining closer thread";
            mCloser->join();
            LOG(DEBUG) << "~FakeSocketLimited joined closer thread";
        }
    }
    bool query(uint16_t id, const Slice query) override {
        LOG(DEBUG) << "FakeSocketLimited::query acquiring mLock";
        std::lock_guard guard(mLock);
        LOG(DEBUG) << "FakeSocketLimited::query acquired mLock";
        ++mQueries;

        if (mQueries <= sLimit) {
            LOG(DEBUG) << "size " << query.size() << " vs. limit of " << sMaxSize;
            if (query.size() <= sMaxSize) {
                // Return the response immediately (asynchronously).
                mThreads.emplace_back(&IDnsTlsSocketObserver::onResponse, mObserver, make_echo(id, query));
            }
        }
        if (mQueries == sLimit) {
            mCloser = std::make_unique<std::thread>(&FakeSocketLimited::sendClose, this);
        }
        return mQueries <= sLimit;
    }
    bool startHandshake() override { return true; }

  private:
    void sendClose() {
        {
            LOG(DEBUG) << "FakeSocketLimited::sendClose acquiring mLock";
            std::lock_guard guard(mLock);
            LOG(DEBUG) << "FakeSocketLimited::sendClose acquired mLock";
            for (auto& thread : mThreads) {
                LOG(DEBUG) << "FakeSocketLimited::sendClose joining response thread";
                thread.join();
                LOG(DEBUG) << "FakeSocketLimited::sendClose joined response thread";
            }
            mThreads.clear();
        }
        mObserver->onClosed();
    }
    std::mutex mLock;
    IDnsTlsSocketObserver* const mObserver;
    int mQueries GUARDED_BY(mLock);
    std::vector<std::thread> mThreads GUARDED_BY(mLock);
    std::unique_ptr<std::thread> mCloser GUARDED_BY(mLock);
};

int FakeSocketLimited::sLimit;
size_t FakeSocketLimited::sMaxSize;

TEST_F(TransportTest, SilentDrop) {
    FakeSocketLimited::sLimit = 10;  // Close the socket after 10 queries.
    FakeSocketLimited::sMaxSize = 0;  // Silently drop all queries
    FakeSocketFactory<FakeSocketLimited> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);

    // Queue up 10 queries.  They will all be ignored, and after the 10th,
    // the socket will close.  Transport will retry them all, until they
    // all hit the retry limit and expire.
    std::vector<std::future<DnsTlsTransport::Result>> results;
    results.reserve(FakeSocketLimited::sLimit);
    for (int i = 0; i < FakeSocketLimited::sLimit; ++i) {
        results.push_back(transport.query(makeSlice(QUERY)));
    }
    for (auto& result : results) {
        auto r = result.get();
        EXPECT_EQ(DnsTlsTransport::Response::network_error, r.code);
        EXPECT_TRUE(r.response.empty());
    }

    // Reconnections might be triggered depending on the flag.
    EXPECT_EQ(transport.getConnectCounter(),
              Experiments::getInstance()->getFlag(DOT_MAXTRIES_FLAG, DnsTlsQueryMap::kMaxTries));
}

TEST_F(TransportTest, PartialDrop) {
    FakeSocketLimited::sLimit = 10;  // Close the socket after 10 queries.
    FakeSocketLimited::sMaxSize = SIZE - 2;  // Silently drop "long" queries
    FakeSocketFactory<FakeSocketLimited> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);

    // Queue up 100 queries, alternating "short" which will be served and "long"
    // which will be dropped.
    const int num_queries = 10 * FakeSocketLimited::sLimit;
    std::vector<bytevec> queries(num_queries);
    std::vector<std::future<DnsTlsTransport::Result>> results;
    results.reserve(num_queries);
    for (int i = 0; i < num_queries; ++i) {
        queries[i] = make_query(i, SIZE + (i % 2));
        results.push_back(transport.query(makeSlice(queries[i])));
    }
    // Just check the short queries, which are at the even indices.
    for (int i = 0; i < num_queries; i += 2) {
        auto r = results[i].get();
        EXPECT_EQ(DnsTlsTransport::Response::success, r.code);
        EXPECT_EQ(queries[i], r.response);
    }

    // TODO: transport.getConnectCounter() seems not stable in this test. Find how to check the
    // connect attempts for this test.
}

TEST_F(TransportTest, ConnectCounter) {
    FakeSocketLimited::sLimit = 2;       // Close the socket after 2 queries.
    FakeSocketLimited::sMaxSize = SIZE;  // No query drops.
    FakeSocketFactory<FakeSocketLimited> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);

    // Connecting on demand.
    EXPECT_EQ(transport.getConnectCounter(), 0);

    const int num_queries = 10;
    std::vector<std::future<DnsTlsTransport::Result>> results;
    results.reserve(num_queries);
    for (int i = 0; i < num_queries; i++) {
        // Reconnections take place every two queries.
        results.push_back(transport.query(makeSlice(QUERY)));
    }
    for (int i = 0; i < num_queries; i++) {
        auto r = results[i].get();
        EXPECT_EQ(DnsTlsTransport::Response::success, r.code);
    }

    EXPECT_EQ(transport.getConnectCounter(), num_queries / FakeSocketLimited::sLimit);
}

// Simulate a malfunctioning server that injects extra miscellaneous
// responses to queries that were not asked.  This will cause wrong answers but
// must not crash the Transport.
class FakeSocketGarbage : public IDnsTlsSocket {
  public:
    explicit FakeSocketGarbage(IDnsTlsSocketObserver* observer) : mObserver(observer) {
        // Inject a garbage event.
        mThreads.emplace_back(&IDnsTlsSocketObserver::onResponse, mObserver, make_query(ID + 1, SIZE));
    }
    ~FakeSocketGarbage() {
        std::lock_guard guard(mLock);
        for (auto& thread : mThreads) {
            thread.join();
        }
    }
    bool query(uint16_t id, const Slice query) override {
        std::lock_guard guard(mLock);
        // Return the response twice.
        auto echo = make_echo(id, query);
        mThreads.emplace_back(&IDnsTlsSocketObserver::onResponse, mObserver, echo);
        mThreads.emplace_back(&IDnsTlsSocketObserver::onResponse, mObserver, echo);
        // Also return some other garbage
        mThreads.emplace_back(&IDnsTlsSocketObserver::onResponse, mObserver, make_query(id + 1, query.size() + 2));
        return true;
    }
    bool startHandshake() override { return true; }

  private:
    std::mutex mLock;
    std::vector<std::thread> mThreads GUARDED_BY(mLock);
    IDnsTlsSocketObserver* const mObserver;
};

TEST_F(TransportTest, IgnoringGarbage) {
    FakeSocketFactory<FakeSocketGarbage> factory;
    DnsTlsTransport transport(SERVER1, MARK, &factory);
    for (int i = 0; i < 10; ++i) {
        auto r = transport.query(makeSlice(QUERY)).get();

        EXPECT_EQ(DnsTlsTransport::Response::success, r.code);
        // Don't check the response because this server is malfunctioning.
    }
    EXPECT_EQ(transport.getConnectCounter(), 1);
}

// Dispatcher tests
class DispatcherTest : public BaseTest {};

TEST_F(DispatcherTest, Query) {
    bytevec ans(4096);
    int resplen = 0;
    bool connectTriggered = false;

    auto factory = std::make_unique<FakeSocketFactory<FakeSocketEcho>>();
    DnsTlsDispatcher dispatcher(std::move(factory));
    auto r = dispatcher.query(SERVER1, NETID, MARK, makeSlice(QUERY), makeSlice(ans), &resplen,
                              &connectTriggered);

    EXPECT_EQ(DnsTlsTransport::Response::success, r);
    EXPECT_EQ(int(QUERY.size()), resplen);
    EXPECT_TRUE(connectTriggered);
    ans.resize(resplen);
    EXPECT_EQ(QUERY, ans);

    // Expect to reuse the connection.
    r = dispatcher.query(SERVER1, NETID, MARK, makeSlice(QUERY), makeSlice(ans), &resplen,
                         &connectTriggered);
    EXPECT_EQ(DnsTlsTransport::Response::success, r);
    EXPECT_FALSE(connectTriggered);
}

TEST_F(DispatcherTest, AnswerTooLarge) {
    bytevec ans(SIZE - 1);  // Too small to hold the answer
    int resplen = 0;
    bool connectTriggered = false;

    auto factory = std::make_unique<FakeSocketFactory<FakeSocketEcho>>();
    DnsTlsDispatcher dispatcher(std::move(factory));
    auto r = dispatcher.query(SERVER1, NETID, MARK, makeSlice(QUERY), makeSlice(ans), &resplen,
                              &connectTriggered);

    EXPECT_EQ(DnsTlsTransport::Response::limit_error, r);
    EXPECT_TRUE(connectTriggered);
}

template<class T>
class TrackingFakeSocketFactory : public IDnsTlsSocketFactory {
  public:
    TrackingFakeSocketFactory() {}
    std::unique_ptr<IDnsTlsSocket> createDnsTlsSocket(
            const DnsTlsServer& server,
            unsigned mark,
            IDnsTlsSocketObserver* observer,
            DnsTlsSessionCache* cache ATTRIBUTE_UNUSED) override {
        std::lock_guard guard(mLock);
        keys.emplace(mark, server);
        return std::make_unique<T>(observer);
    }
    std::multiset<std::pair<unsigned, DnsTlsServer>> keys;

  private:
    std::mutex mLock;
};

TEST_F(DispatcherTest, Dispatching) {
    FakeSocketDelay::sDelay = 5;
    FakeSocketDelay::sReverse = true;
    auto factory = std::make_unique<TrackingFakeSocketFactory<FakeSocketDelay>>();
    auto* weak_factory = factory.get();  // Valid as long as dispatcher is in scope.
    DnsTlsDispatcher dispatcher(std::move(factory));

    // Populate a vector of two servers and two socket marks, four combinations
    // in total.
    std::vector<std::pair<unsigned, DnsTlsServer>> keys;
    keys.emplace_back(MARK, SERVER1);
    keys.emplace_back(MARK + 1, SERVER1);
    keys.emplace_back(MARK, V4ADDR2);
    keys.emplace_back(MARK + 1, V4ADDR2);

    // Do several queries on each server.  They should all succeed.
    std::vector<std::thread> threads;
    for (size_t i = 0; i < FakeSocketDelay::sDelay * keys.size(); ++i) {
        auto key = keys[i % keys.size()];
        threads.emplace_back([key, i] (DnsTlsDispatcher* dispatcher) {
            auto q = make_query(i, SIZE);
            bytevec ans(4096);
            int resplen = 0;
            bool connectTriggered = false;
            unsigned mark = key.first;
            unsigned netId = key.first;
            const DnsTlsServer& server = key.second;
            auto r = dispatcher->query(server, netId, mark, makeSlice(q), makeSlice(ans), &resplen,
                                       &connectTriggered);
            EXPECT_EQ(DnsTlsTransport::Response::success, r);
            EXPECT_EQ(int(q.size()), resplen);
            ans.resize(resplen);
            EXPECT_EQ(q, ans);
        }, &dispatcher);
    }
    for (auto& thread : threads) {
        thread.join();
    }
    // We expect that the factory created one socket for each key.
    EXPECT_EQ(keys.size(), weak_factory->keys.size());
    for (auto& key : keys) {
        EXPECT_EQ(1U, weak_factory->keys.count(key));
    }
}

// Check DnsTlsServer's comparison logic.
AddressComparator ADDRESS_COMPARATOR;
bool isAddressEqual(const DnsTlsServer& s1, const DnsTlsServer& s2) {
    bool cmp1 = ADDRESS_COMPARATOR(s1, s2);
    bool cmp2 = ADDRESS_COMPARATOR(s2, s1);
    EXPECT_FALSE(cmp1 && cmp2);
    return !cmp1 && !cmp2;
}

void checkUnequal(const DnsTlsServer& s1, const DnsTlsServer& s2) {
    EXPECT_TRUE(s1 == s1);
    EXPECT_TRUE(s2 == s2);
    EXPECT_TRUE(isAddressEqual(s1, s1));
    EXPECT_TRUE(isAddressEqual(s2, s2));

    EXPECT_TRUE(s1 < s2 ^ s2 < s1);
    EXPECT_FALSE(s1 == s2);
    EXPECT_FALSE(s2 == s1);
}

void checkEqual(const DnsTlsServer& s1, const DnsTlsServer& s2) {
    EXPECT_TRUE(s1 == s1);
    EXPECT_TRUE(s2 == s2);
    EXPECT_TRUE(isAddressEqual(s1, s1));
    EXPECT_TRUE(isAddressEqual(s2, s2));

    EXPECT_FALSE(s1 < s2);
    EXPECT_FALSE(s2 < s1);
    EXPECT_TRUE(s1 == s2);
    EXPECT_TRUE(s2 == s1);
}

class ServerTest : public BaseTest {};

TEST_F(ServerTest, IPv4) {
    checkUnequal(DnsTlsServer(V4ADDR1), DnsTlsServer(V4ADDR2));
    EXPECT_FALSE(isAddressEqual(DnsTlsServer(V4ADDR1), DnsTlsServer(V4ADDR2)));
}

TEST_F(ServerTest, IPv6) {
    checkUnequal(DnsTlsServer(V6ADDR1), DnsTlsServer(V6ADDR2));
    EXPECT_FALSE(isAddressEqual(DnsTlsServer(V6ADDR1), DnsTlsServer(V6ADDR2)));
}

TEST_F(ServerTest, MixedAddressFamily) {
    checkUnequal(DnsTlsServer(V6ADDR1), DnsTlsServer(V4ADDR1));
    EXPECT_FALSE(isAddressEqual(DnsTlsServer(V6ADDR1), DnsTlsServer(V4ADDR1)));
}

TEST_F(ServerTest, IPv6ScopeId) {
    DnsTlsServer s1(IPAddress::forString("fe80::1%1"));
    DnsTlsServer s2(IPAddress::forString("fe80::1%2"));
    checkUnequal(s1, s2);
    EXPECT_FALSE(isAddressEqual(s1, s2));

    EXPECT_FALSE(s1.wasExplicitlyConfigured());
    EXPECT_FALSE(s2.wasExplicitlyConfigured());
}

TEST_F(ServerTest, Port) {
    DnsTlsServer s1(IPSockAddr::toIPSockAddr("192.0.2.1", 853));
    DnsTlsServer s2(IPSockAddr::toIPSockAddr("192.0.2.1", 854));
    checkUnequal(s1, s2);
    EXPECT_TRUE(isAddressEqual(s1, s2));
    EXPECT_EQ(s1.toIpString(), "192.0.2.1");
    EXPECT_EQ(s2.toIpString(), "192.0.2.1");

    DnsTlsServer s3(IPSockAddr::toIPSockAddr("2001:db8::1", 853));
    DnsTlsServer s4(IPSockAddr::toIPSockAddr("2001:db8::1", 854));
    checkUnequal(s3, s4);
    EXPECT_TRUE(isAddressEqual(s3, s4));
    EXPECT_EQ(s3.toIpString(), "2001:db8::1");
    EXPECT_EQ(s4.toIpString(), "2001:db8::1");

    EXPECT_FALSE(s1.wasExplicitlyConfigured());
    EXPECT_FALSE(s2.wasExplicitlyConfigured());
}

TEST_F(ServerTest, Name) {
    DnsTlsServer s1(V4ADDR1), s2(V4ADDR1);
    s1.name = SERVERNAME1;
    checkUnequal(s1, s2);
    s2.name = SERVERNAME2;
    checkUnequal(s1, s2);
    EXPECT_TRUE(isAddressEqual(s1, s2));

    EXPECT_TRUE(s1.wasExplicitlyConfigured());
    EXPECT_TRUE(s2.wasExplicitlyConfigured());
}

TEST_F(ServerTest, State) {
    DnsTlsServer s1(V4ADDR1), s2(V4ADDR1);
    checkEqual(s1, s2);
    s1.setValidationState(Validation::success);
    checkEqual(s1, s2);
    s2.setValidationState(Validation::fail);
    checkEqual(s1, s2);
    s1.setActive(true);
    checkEqual(s1, s2);
    s2.setActive(false);
    checkEqual(s1, s2);

    EXPECT_EQ(s1.validationState(), Validation::success);
    EXPECT_EQ(s2.validationState(), Validation::fail);
    EXPECT_TRUE(s1.active());
    EXPECT_FALSE(s2.active());
}

class QueryMapTest : public NetNativeTestBase {};

TEST_F(QueryMapTest, Basic) {
    DnsTlsQueryMap map;

    EXPECT_TRUE(map.empty());

    bytevec q0 = make_query(999, SIZE);
    bytevec q1 = make_query(888, SIZE);
    bytevec q2 = make_query(777, SIZE);

    auto f0 = map.recordQuery(makeSlice(q0));
    auto f1 = map.recordQuery(makeSlice(q1));
    auto f2 = map.recordQuery(makeSlice(q2));

    // Check return values of recordQuery
    EXPECT_EQ(0, f0->query.newId);
    EXPECT_EQ(1, f1->query.newId);
    EXPECT_EQ(2, f2->query.newId);

    // Check side effects of recordQuery
    EXPECT_FALSE(map.empty());

    auto all = map.getAll();
    EXPECT_EQ(3U, all.size());

    EXPECT_EQ(0, all[0].newId);
    EXPECT_EQ(1, all[1].newId);
    EXPECT_EQ(2, all[2].newId);

    EXPECT_EQ(q0, all[0].query);
    EXPECT_EQ(q1, all[1].query);
    EXPECT_EQ(q2, all[2].query);

    bytevec a0 = make_query(0, SIZE);
    bytevec a1 = make_query(1, SIZE);
    bytevec a2 = make_query(2, SIZE);

    // Return responses out of order
    map.onResponse(a2);
    map.onResponse(a0);
    map.onResponse(a1);

    EXPECT_TRUE(map.empty());

    auto r0 = f0->result.get();
    auto r1 = f1->result.get();
    auto r2 = f2->result.get();

    EXPECT_EQ(DnsTlsQueryMap::Response::success, r0.code);
    EXPECT_EQ(DnsTlsQueryMap::Response::success, r1.code);
    EXPECT_EQ(DnsTlsQueryMap::Response::success, r2.code);

    const bytevec& d0 = r0.response;
    const bytevec& d1 = r1.response;
    const bytevec& d2 = r2.response;

    // The ID should match the query
    EXPECT_EQ(999, d0[0] << 8 | d0[1]);
    EXPECT_EQ(888, d1[0] << 8 | d1[1]);
    EXPECT_EQ(777, d2[0] << 8 | d2[1]);
    // The body should match the answer
    EXPECT_EQ(bytevec(a0.begin() + 2, a0.end()), bytevec(d0.begin() + 2, d0.end()));
    EXPECT_EQ(bytevec(a1.begin() + 2, a1.end()), bytevec(d1.begin() + 2, d1.end()));
    EXPECT_EQ(bytevec(a2.begin() + 2, a2.end()), bytevec(d2.begin() + 2, d2.end()));
}

TEST_F(QueryMapTest, FillHole) {
    DnsTlsQueryMap map;
    std::vector<std::unique_ptr<DnsTlsQueryMap::QueryFuture>> futures(UINT16_MAX + 1);
    for (uint32_t i = 0; i <= UINT16_MAX; ++i) {
        futures[i] = map.recordQuery(makeSlice(QUERY));
        ASSERT_TRUE(futures[i]);  // answers[i] should be nonnull.
        EXPECT_EQ(i, futures[i]->query.newId);
    }

    // The map should now be full.
    EXPECT_EQ(size_t(UINT16_MAX + 1), map.getAll().size());

    // Trying to add another query should fail because the map is full.
    EXPECT_FALSE(map.recordQuery(makeSlice(QUERY)));

    // Send an answer to query 40000
    auto answer = make_query(40000, SIZE);
    map.onResponse(answer);
    auto result = futures[40000]->result.get();
    EXPECT_EQ(DnsTlsQueryMap::Response::success, result.code);
    EXPECT_EQ(ID, result.response[0] << 8 | result.response[1]);
    EXPECT_EQ(bytevec(answer.begin() + 2, answer.end()),
              bytevec(result.response.begin() + 2, result.response.end()));

    // There should now be room in the map.
    EXPECT_EQ(size_t(UINT16_MAX), map.getAll().size());
    auto f = map.recordQuery(makeSlice(QUERY));
    ASSERT_TRUE(f);
    EXPECT_EQ(40000, f->query.newId);

    // The map should now be full again.
    EXPECT_EQ(size_t(UINT16_MAX + 1), map.getAll().size());
    EXPECT_FALSE(map.recordQuery(makeSlice(QUERY)));
}

class DnsTlsSocketTest : public NetNativeTestBase {
  protected:
    class MockDnsTlsSocketObserver : public IDnsTlsSocketObserver {
      public:
        MOCK_METHOD(void, onClosed, (), (override));
        MOCK_METHOD(void, onResponse, (std::vector<uint8_t>), (override));
    };

    std::unique_ptr<DnsTlsSocket> makeDnsTlsSocket(IDnsTlsSocketObserver* observer) {
        return std::make_unique<DnsTlsSocket>(this->server, MARK, observer, &this->cache);
    }

    void enableAsyncHandshake(const std::unique_ptr<DnsTlsSocket>& socket) {
        ASSERT_TRUE(socket);
        DnsTlsSocket* delegate = socket.get();
        std::lock_guard guard(delegate->mLock);
        delegate->mAsyncHandshake = true;
    }

    static constexpr char kTlsAddr[] = "127.0.0.3";
    static constexpr char kTlsPort[] = "8530";  // High-numbered port so root isn't required.
    static constexpr char kBackendAddr[] = "192.0.2.1";
    static constexpr char kBackendPort[] = "8531";  // High-numbered port so root isn't required.

    test::DnsTlsFrontend tls{kTlsAddr, kTlsPort, kBackendAddr, kBackendPort};

    const DnsTlsServer server{IPSockAddr::toIPSockAddr(kTlsAddr, std::stoi(kTlsPort))};
    DnsTlsSessionCache cache;
};

TEST_F(DnsTlsSocketTest, SlowDestructor) {
    ASSERT_TRUE(tls.startServer());

    MockDnsTlsSocketObserver observer;
    auto socket = makeDnsTlsSocket(&observer);

    ASSERT_TRUE(socket->initialize());
    ASSERT_TRUE(socket->startHandshake());

    // Test: Time the socket destructor.  This should be fast.
    auto before = std::chrono::steady_clock::now();
    EXPECT_CALL(observer, onClosed);
    socket.reset();
    auto after = std::chrono::steady_clock::now();
    auto delay = after - before;
    LOG(DEBUG) << "Shutdown took " << delay / std::chrono::nanoseconds{1} << "ns";
    // Shutdown should complete in milliseconds, but if the shutdown signal is lost
    // it will wait for the timeout, which is expected to take 20seconds.
    EXPECT_LT(delay, std::chrono::seconds{5});
}

TEST_F(DnsTlsSocketTest, StartHandshake) {
    ASSERT_TRUE(tls.startServer());

    MockDnsTlsSocketObserver observer;
    auto socket = makeDnsTlsSocket(&observer);

    // Call the function before the call to initialize().
    EXPECT_FALSE(socket->startHandshake());

    // Call the function after the call to initialize().
    EXPECT_TRUE(socket->initialize());
    EXPECT_TRUE(socket->startHandshake());

    // Call both of them again.
    EXPECT_FALSE(socket->initialize());
    EXPECT_FALSE(socket->startHandshake());

    // Should happen when joining the loop thread in |socket| destruction.
    EXPECT_CALL(observer, onClosed);
}

TEST_F(DnsTlsSocketTest, ShutdownSignal) {
    ASSERT_TRUE(tls.startServer());

    MockDnsTlsSocketObserver observer;
    std::unique_ptr<DnsTlsSocket> socket;

    const auto setupAndStartHandshake = [&]() {
        socket = makeDnsTlsSocket(&observer);
        EXPECT_TRUE(socket->initialize());
        enableAsyncHandshake(socket);
        EXPECT_TRUE(socket->startHandshake());
    };
    const auto triggerShutdown = [&](const std::string& traceLog) {
        SCOPED_TRACE(traceLog);
        auto before = std::chrono::steady_clock::now();
        EXPECT_CALL(observer, onClosed);
        socket.reset();
        auto after = std::chrono::steady_clock::now();
        auto delay = after - before;
        LOG(INFO) << "Shutdown took " << delay / std::chrono::nanoseconds{1} << "ns";
        EXPECT_LT(delay, std::chrono::seconds{1});
    };

    tls.setHangOnHandshakeForTesting(true);

    // Test 1: Reset the DnsTlsSocket which is doing the handshake.
    setupAndStartHandshake();
    triggerShutdown("Shutdown handshake w/o query requests");

    // Test 2: Reset the DnsTlsSocket which is doing the handshake with some query requests.
    setupAndStartHandshake();

    // DnsTlsSocket doesn't report the status of pending queries. The decision whether to mark
    // a query request as failed or not is made in DnsTlsTransport.
    EXPECT_CALL(observer, onResponse).Times(0);
    EXPECT_TRUE(socket->query(1, makeSlice(QUERY)));
    EXPECT_TRUE(socket->query(2, makeSlice(QUERY)));
    triggerShutdown("Shutdown handshake w/ query requests");
}

} // end of namespace net
} // end of namespace android
