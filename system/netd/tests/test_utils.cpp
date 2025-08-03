/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * test_utils.cpp - miscellaneous unit test utilities.
 */

#include <cstdio>
#include <string>
#include <vector>

#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <binder/IResultReceiver.h>
#include <binder/IServiceManager.h>
#include <binder/IShellCallback.h>
#include <binder/TextOutput.h>

#include "test_utils.h"

#define IP_PATH "/system/bin/ip"

using android::IBinder;
using android::IServiceManager;
using android::sp;
using android::String16;
using android::Vector;
using android::base::Split;
using android::base::StringPrintf;

int randomUid() {
    // Pick a random UID consisting of:
    // - Random user profile (0 - 6)
    // - Random app ID starting from 12000 (FIRST_APPLICATION_UID + 2000). This ensures no conflicts
    //   with existing app UIDs unless the user has installed more than 2000 apps, and is still less
    //   than LAST_APPLICATION_UID (19999).
    return 100000 * arc4random_uniform(7) + 12000 + arc4random_uniform(3000);
}

std::vector<std::string> runCommand(const std::string& command) {
    std::vector<std::string> lines;
    FILE* f = popen(command.c_str(), "r");  // NOLINT(cert-env33-c)
    if (f == nullptr) {
        perror("popen");
        return lines;
    }

    char* line = nullptr;
    size_t bufsize = 0;
    ssize_t linelen = 0;
    while ((linelen = getline(&line, &bufsize, f)) >= 0) {
        std::string str = std::string(line, linelen);
        const size_t lastNotWhitespace = str.find_last_not_of(" \t\n\r");
        if (lastNotWhitespace != std::string::npos) {
            str = str.substr(0, lastNotWhitespace + 1);
        }
        lines.push_back(str);
        free(line);
        line = nullptr;
    }

    pclose(f);
    return lines;
}

android::status_t runBinderCommand(const std::string serviceName, const std::string& command) {
    // For services implementing the shell command binder method, we want to avoid forking a shell
    // and directly transact on the binder instead.
    sp<IServiceManager> sm = android::defaultServiceManager();
    sp<IBinder> service = sm->checkService(String16(serviceName.c_str()));

    if (!service) return android::NAME_NOT_FOUND;

    const std::vector<std::string> args = Split(command, " ");
    android::Vector<String16> argVec;
    for (const auto arg : args) {
        argVec.add(String16(arg.data(), arg.size()));
    }
    return IBinder::shellCommand(service, STDIN_FILENO, STDOUT_FILENO, STDERR_FILENO, argVec,
                                 nullptr /* cb */, nullptr /* result */);
}

std::vector<std::string> listIpRules(const char* ipVersion) {
    std::string command = StringPrintf("%s %s rule list", IP_PATH, ipVersion);
    return runCommand(command);
}

std::vector<std::string> listIptablesRule(const char* binary, const char* chainName) {
    std::string command = StringPrintf("%s -w -n -L %s", binary, chainName);
    return runCommand(command);
}

int iptablesRuleLineLength(const char* binary, const char* chainName) {
    return listIptablesRule(binary, chainName).size();
}

bool iptablesRuleExists(const char* binary, const char* chainName,
                        const std::string& expectedRule) {
    std::vector<std::string> rules = listIptablesRule(binary, chainName);
    for (const auto& rule : rules) {
        if (rule.find(expectedRule) != std::string::npos) {
            return true;
        }
    }
    return false;
}

std::vector<std::string> listIpRoutes(const char* ipVersion, const char* table) {
    std::string command = StringPrintf("%s %s route ls table %s", IP_PATH, ipVersion, table);
    return runCommand(command);
}

bool ipRouteExists(const char* ipVersion, const char* table,
                   const std::vector<std::string>& ipRouteSubstrings) {
    std::vector<std::string> routes = listIpRoutes(ipVersion, table);
    for (const auto& route : routes) {
        bool matched = true;
        for (const auto& substring : ipRouteSubstrings) {
            if (route.find(substring) == std::string::npos) {
                matched = false;
                break;
            }
        }

        if (matched) {
            return true;
        }
    }
    return false;
}
