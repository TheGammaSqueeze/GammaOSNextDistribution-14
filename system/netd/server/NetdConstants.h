/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include <ifaddrs.h>
#include <netdb.h>
#include <stddef.h>
#include <stdint.h>

#include <mutex>
#include <string>

#include "android/net/INetd.h"

#include <netdutils/UidConstants.h>
#include <private/android_filesystem_config.h>

enum IptablesTarget { V4, V6, V4V6 };

int execIptablesRestore(IptablesTarget target, const std::string& commands);
int execIptablesRestoreWithOutput(IptablesTarget target, const std::string& commands,
                                  std::string *output);
int execIptablesRestoreCommand(IptablesTarget target, const std::string& table,
                               const std::string& command, std::string *output);
bool isIfaceName(const std::string& name);
int parsePrefix(const char *prefix, uint8_t *family, void *address, int size, uint8_t *prefixlen);
void blockSigpipe();
void setCloseOnExec(const char *sock);

void stopProcess(int pid, const char* processName);

// TODO: use std::size() instead.
#define ARRAY_SIZE(a) (sizeof(a) / sizeof(*(a)))

#define __INT_STRLEN(i) sizeof(#i)
#define _INT_STRLEN(i) __INT_STRLEN(i)
#define INT32_STRLEN _INT_STRLEN(INT32_MIN)
#define UINT32_STRLEN _INT_STRLEN(UINT32_MAX)
#define UINT32_HEX_STRLEN sizeof("0x12345678")
#define IPSEC_IFACE_PREFIX "ipsec"

const uid_t INVALID_UID = static_cast<uid_t>(-1);

constexpr char TCP_RMEM_PROC_FILE[] = "/proc/sys/net/ipv4/tcp_rmem";
constexpr char TCP_WMEM_PROC_FILE[] = "/proc/sys/net/ipv4/tcp_wmem";

struct IfaddrsDeleter {
    void operator()(struct ifaddrs *p) const {
        if (p != nullptr) {
            freeifaddrs(p);
        }
    }
};

typedef std::unique_ptr<struct ifaddrs, struct IfaddrsDeleter> ScopedIfaddrs;

namespace android::net {

/**
 * This lock exists to make NetdNativeService RPCs (which come in on multiple Binder threads)
 * coexist with the commands in CommandListener.cpp. These are presumed not thread-safe because
 * CommandListener has only one user (NetworkManagementService), which is connected through a
 * FrameworkListener that passes in commands one at a time.
 */
extern std::mutex gBigNetdLock;

enum FirewallRule { ALLOW = INetd::FIREWALL_RULE_ALLOW, DENY = INetd::FIREWALL_RULE_DENY };

// ALLOWLIST means the firewall denies all by default, uids must be explicitly ALLOWed
// DENYLIST means the firewall allows all by default, uids must be explicitly DENYed

enum FirewallType { ALLOWLIST = INetd::FIREWALL_ALLOWLIST, DENYLIST = INetd::FIREWALL_DENYLIST };

enum ChildChain {
    NONE = INetd::FIREWALL_CHAIN_NONE,
    DOZABLE = INetd::FIREWALL_CHAIN_DOZABLE,
    STANDBY = INetd::FIREWALL_CHAIN_STANDBY,
    POWERSAVE = INetd::FIREWALL_CHAIN_POWERSAVE,
    RESTRICTED = INetd::FIREWALL_CHAIN_RESTRICTED,
    INVALID_CHAIN
};

}  // namespace android::net
