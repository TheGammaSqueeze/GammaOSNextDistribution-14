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

#include <set>

#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <cstdint>

#define LOG_TAG "FirewallController"
#define LOG_NDEBUG 0

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <log/log.h>

#include "Controllers.h"
#include "FirewallController.h"
#include "NetdConstants.h"

using android::base::Join;
using android::base::StringAppendF;
using android::base::StringPrintf;

namespace android {
namespace net {

auto FirewallController::execIptablesRestore = ::execIptablesRestore;

const char* FirewallController::TABLE = "filter";

const char* FirewallController::LOCAL_INPUT = "fw_INPUT";
const char* FirewallController::LOCAL_OUTPUT = "fw_OUTPUT";
const char* FirewallController::LOCAL_FORWARD = "fw_FORWARD";

// ICMPv6 types that are required for any form of IPv6 connectivity to work. Note that because the
// fw_dozable chain is called from both INPUT and OUTPUT, this includes both packets that we need
// to be able to send (e.g., RS, NS), and packets that we need to receive (e.g., RA, NA).
const char* FirewallController::ICMPV6_TYPES[] = {
    "packet-too-big",
    "router-solicitation",
    "router-advertisement",
    "neighbour-solicitation",
    "neighbour-advertisement",
    "redirect",
};

FirewallController::FirewallController(void) {
    // If no rules are set, it's in DENYLIST mode
    mFirewallType = DENYLIST;
    mIfaceRules = {};
}

int FirewallController::setupIptablesHooks(void) {
    return flushRules();
}

int FirewallController::setFirewallType(FirewallType ftype) {
    int res = 0;
    if (mFirewallType != ftype) {
        // flush any existing rules
        resetFirewall();

        if (ftype == ALLOWLIST) {
            // create default rule to drop all traffic
            std::string command =
                "*filter\n"
                "-A fw_INPUT -j DROP\n"
                "-A fw_OUTPUT -j REJECT\n"
                "-A fw_FORWARD -j REJECT\n"
                "COMMIT\n";
            res = execIptablesRestore(V4V6, command.c_str());
        }

        // Set this after calling disableFirewall(), since it defaults to ALLOWLIST there
        mFirewallType = ftype;
    }
    return res ? -EREMOTEIO : 0;
}

int FirewallController::flushRules() {
    std::string command =
            "*filter\n"
            ":fw_INPUT -\n"
            ":fw_OUTPUT -\n"
            ":fw_FORWARD -\n"
            "-6 -A fw_OUTPUT ! -o lo -s ::1 -j DROP\n"
            "COMMIT\n";

    return (execIptablesRestore(V4V6, command.c_str()) == 0) ? 0 : -EREMOTEIO;
}

int FirewallController::resetFirewall(void) {
    mFirewallType = ALLOWLIST;
    mIfaceRules.clear();
    return flushRules();
}

int FirewallController::isFirewallEnabled(void) {
    // TODO: verify that rules are still in place near top
    return -1;
}

int FirewallController::setInterfaceRule(const char* iface, FirewallRule rule) {
    if (mFirewallType == DENYLIST) {
        // Unsupported in DENYLIST mode
        return -EINVAL;
    }

    if (!isIfaceName(iface)) {
        errno = ENOENT;
        return -ENOENT;
    }

    // Only delete rules if we actually added them, because otherwise our iptables-restore
    // processes will terminate with "no such rule" errors and cause latency penalties while we
    // spin up new ones.
    const char* op;
    if (rule == ALLOW && mIfaceRules.find(iface) == mIfaceRules.end()) {
        op = "-I";
        mIfaceRules.insert(iface);
    } else if (rule == DENY && mIfaceRules.find(iface) != mIfaceRules.end()) {
        op = "-D";
        mIfaceRules.erase(iface);
    } else {
        return 0;
    }

    std::string command = Join(std::vector<std::string> {
        "*filter",
        StringPrintf("%s fw_INPUT -i %s -j RETURN", op, iface),
        StringPrintf("%s fw_OUTPUT -o %s -j RETURN", op, iface),
        "COMMIT\n"
    }, "\n");
    return (execIptablesRestore(V4V6, command) == 0) ? 0 : -EREMOTEIO;
}

/* static */
std::string FirewallController::makeCriticalCommands(IptablesTarget target, const char* chainName) {
    // Allow ICMPv6 packets necessary to make IPv6 connectivity work. http://b/23158230 .
    std::string commands;
    if (target == V6) {
        for (size_t i = 0; i < ARRAY_SIZE(ICMPV6_TYPES); i++) {
            StringAppendF(&commands, "-A %s -p icmpv6 --icmpv6-type %s -j RETURN\n",
                   chainName, ICMPV6_TYPES[i]);
        }
    }
    return commands;
}

}  // namespace net
}  // namespace android
