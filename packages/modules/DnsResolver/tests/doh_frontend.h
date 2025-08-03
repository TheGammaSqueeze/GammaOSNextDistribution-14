/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <mutex>
#include <string>

#include <android-base/thread_annotations.h>

#include "doh/tests/doh_frontend/include/lib.rs.h"

namespace test {

/*
 * The wrapper between tests and Rust DoH frontend.
 * It is designed to be as close as possible to DnsTlsFrontend, so we can write one test for
 * both DoT and DoH.
 */
class DohFrontend {
  public:
    DohFrontend(const std::string& listen_address = kDefaultListenAddr,
                const std::string& listen_service = kDefaultListenService,
                const std::string& backend_address = kDefaultBackendAddr,
                const std::string& backend_service = kDefaultBackendService)
        : mAddress(listen_address),
          mService(listen_service),
          mBackendAddress(backend_address),
          mBackendService(backend_service) {}
    ~DohFrontend();
    std::string listen_address() const { return mAddress; }
    std::string listen_service() const { return mService; }

    bool startServer();
    bool stopServer();

    // Returns the number of received DoH queries.
    int queries() const;

    // Returns the number of accepted DoH connections.
    int connections() const;

    // Returns the number of alive DoH connections.
    int aliveConnections() const;

    // Returns the number of connections using session resumption.
    int resumedConnections() const;

    // Returns the number of connections that had early data.
    int earlyDataConnections() const;

    void clearQueries();
    bool block_sending(bool block);
    bool setResetStreamId(uint64_t value);
    bool waitForAllClientsDisconnected() const;

    // To make the configuration effective, callers need to restart the DoH server after calling
    // these methods.
    bool setMaxIdleTimeout(uint64_t value);
    bool setMaxBufferSize(uint64_t value);
    bool setMaxStreamsBidi(uint64_t value);

    static void initRustAndroidLogger() { rust::init_android_logger(); }

    static constexpr char kDefaultListenAddr[] = "127.0.0.3";
    static constexpr char kDefaultListenService[] = "443";
    static constexpr char kDefaultBackendAddr[] = "127.0.0.3";
    static constexpr char kDefaultBackendService[] = "53";

  private:
    const std::string mAddress;
    const std::string mService;
    const std::string mBackendAddress;
    const std::string mBackendService;

    mutable std::mutex mMutex;
    rust::DohFrontend* mRustDoh GUARDED_BY(mMutex) = nullptr;
};

}  // namespace test
