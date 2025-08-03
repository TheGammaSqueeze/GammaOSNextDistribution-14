/*
 * Copyright 2023 The Android Open Source Project
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

#ifndef MMC_DAEMON_DBUS_CONSTANTS_H_
#define MMC_DAEMON_DBUS_CONSTANTS_H_

namespace mmc {

// DBus constants.
constexpr char kMmcServiceName[] = "org.chromium.mmc.CodecManager";
constexpr char kMmcServiceInterface[] = "org.chromium.mmc.CodecManager";
constexpr char kMmcServicePath[] = "/org/chromium/mmc/CodecManager";
const char kMmcServiceError[] = "org.chromium.mmc.CodecManager.Error";
constexpr char kCodecInitMethod[] = "CodecInit";
constexpr char kCodecCleanUpMethod[] = "CodecCleanUp";

// Socket constants.
const char kMmcSocketName[] = "/run/mmc/sockets/";
// The maximum number of socket pending connections.
// MMC daemon expects at most two clients, decoder and encoder of one codec.
constexpr int kClientMaximum = 2;
// Socket default maximum buffer size.
constexpr int kMaximumBufferSize = 32768;

// Thread constants.
constexpr char kWorkerThreadName[] = "bt_mmc_worker_thread";
constexpr int kThreadCheckTimeout = 1;
}  // namespace mmc

#endif  // MMC_DAEMON_DBUS_CONSTANTS_H_
