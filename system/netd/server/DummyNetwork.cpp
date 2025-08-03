/*
 * Copyright (C) 2014 The Android Open Source Project
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

#define LOG_TAG "Netd"

#include "DummyNetwork.h"

#include "errno.h"

namespace android {
namespace net {

// The dummy network is used to blackhole or reject traffic.
// It has an IPv4 and an IPv6 default route that point to a dummy interface
// which drops packets. It is used for system purposes only. Applications
// cannot use multinetwork APIs such as Network#bindSocket or
// android_setsocknetwork to send packets on the dummy network.
// Any attempt to do so will fail with ENETUNREACH.

const char* DummyNetwork::INTERFACE_NAME = "dummy0";

DummyNetwork::DummyNetwork(unsigned netId) : Network(netId) {
    mInterfaces.insert(INTERFACE_NAME);
}

DummyNetwork::~DummyNetwork() {
}

}  // namespace net
}  // namespace android
