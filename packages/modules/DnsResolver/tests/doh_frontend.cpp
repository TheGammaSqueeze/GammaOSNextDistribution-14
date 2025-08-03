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
 */

#include "doh_frontend.h"

#define LOG_TAG "DohFrontend"

#include <thread>

#include <android-base/chrono_utils.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <gtest/gtest.h>

#include "dns_tls_certificate.h"

using std::chrono::milliseconds;

namespace test {

constexpr milliseconds kEventTimeoutMs{5000};
constexpr milliseconds kRetryIntervalMs{20};

DohFrontend::~DohFrontend() {
    if (mRustDoh) {
        stopServer();
        rust::frontend_delete(mRustDoh);
    }
}

bool DohFrontend::startServer() {
    std::lock_guard guard(mMutex);
    if (mRustDoh == nullptr) {
        mRustDoh = rust::frontend_new(mAddress.c_str(), mService.c_str(), mBackendAddress.c_str(),
                                      mBackendService.c_str());
        if (mRustDoh == nullptr) {
            LOG(ERROR) << "Failed to create rust DoH frontend";
            return false;
        }
    }

    rust::frontend_set_certificate(mRustDoh, kCertificate);
    rust::frontend_set_private_key(mRustDoh, kPrivatekey);

    return rust::frontend_start(mRustDoh);
}

bool DohFrontend::stopServer() {
    std::lock_guard guard(mMutex);
    if (!mRustDoh) return false;

    return rust::frontend_stop(mRustDoh);
}

int DohFrontend::queries() const {
    std::lock_guard guard(mMutex);
    if (!mRustDoh) return 0;

    rust::Stats stats;
    rust::frontend_stats(mRustDoh, &stats);
    return stats.queries_received;
}

int DohFrontend::connections() const {
    std::lock_guard guard(mMutex);
    if (!mRustDoh) return 0;

    rust::Stats stats;
    rust::frontend_stats(mRustDoh, &stats);
    return stats.connections_accepted;
}

int DohFrontend::aliveConnections() const {
    std::lock_guard guard(mMutex);
    if (!mRustDoh) return 0;

    rust::Stats stats;
    rust::frontend_stats(mRustDoh, &stats);
    return stats.alive_connections;
}

int DohFrontend::resumedConnections() const {
    std::lock_guard guard(mMutex);
    if (!mRustDoh) return 0;

    rust::Stats stats;
    rust::frontend_stats(mRustDoh, &stats);
    return stats.resumed_connections;
}

int DohFrontend::earlyDataConnections() const {
    std::lock_guard guard(mMutex);
    if (!mRustDoh) return 0;

    rust::Stats stats;
    rust::frontend_stats(mRustDoh, &stats);
    return stats.early_data_connections;
}

void DohFrontend::clearQueries() {
    std::lock_guard guard(mMutex);
    if (mRustDoh) {
        frontend_stats_clear_queries(mRustDoh);

        // Because frontend_stats_clear_queries() is asynchronous, query the stat here to ensure
        // that mRustDoh reset the query count before clearQueries() returns.
        rust::Stats stats;
        rust::frontend_stats(mRustDoh, &stats);
        if (stats.queries_received != 0) {
            LOG(ERROR) << "queries_received is not 0";
        }
    }
}

bool DohFrontend::setMaxIdleTimeout(uint64_t value) {
    std::lock_guard guard(mMutex);
    if (!mRustDoh) return false;

    return frontend_set_max_idle_timeout(mRustDoh, value);
}

bool DohFrontend::setMaxBufferSize(uint64_t value) {
    std::lock_guard guard(mMutex);
    if (!mRustDoh) return false;

    return frontend_set_max_buffer_size(mRustDoh, value);
}

bool DohFrontend::setMaxStreamsBidi(uint64_t value) {
    std::lock_guard guard(mMutex);
    if (!mRustDoh) return false;

    return frontend_set_max_streams_bidi(mRustDoh, value);
}

bool DohFrontend::block_sending(bool block) {
    std::lock_guard guard(mMutex);
    if (!mRustDoh) return false;

    return frontend_block_sending(mRustDoh, block);
}

bool DohFrontend::setResetStreamId(uint64_t value) {
    std::lock_guard guard(mMutex);
    if (!mRustDoh) return false;

    return frontend_set_reset_stream_id(mRustDoh, value);
}

bool DohFrontend::waitForAllClientsDisconnected() const {
    android::base::Timer t;
    while (t.duration() < kEventTimeoutMs) {
        if (aliveConnections() == 0) return true;
        std::this_thread::sleep_for(kRetryIntervalMs);
    }
    return false;
}

}  // namespace test
