/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "resolv_test_utils.h"

#include <arpa/inet.h>

#include <android-base/chrono_utils.h>
#include <android-base/logging.h>
#include <firewall.h>

using android::netdutils::ScopedAddrinfo;

std::string ToString(const hostent* he) {
    if (he == nullptr) return "<null>";
    char buffer[INET6_ADDRSTRLEN];
    if (!inet_ntop(he->h_addrtype, he->h_addr_list[0], buffer, sizeof(buffer))) {
        return "<invalid>";
    }
    return buffer;
}

std::string ToString(const addrinfo* ai) {
    if (!ai) return "<null>";

    const sockaddr* ai_addr = ai->ai_addr;
    char host[NI_MAXHOST];
    const int rv =
            getnameinfo(ai_addr, ai->ai_addrlen, host, sizeof(host), nullptr, 0, NI_NUMERICHOST);
    if (rv != 0) return gai_strerror(rv);
    return host;
}

std::string ToString(const ScopedAddrinfo& ai) {
    return ToString(ai.get());
}

std::string ToString(const sockaddr_storage* addr) {
    if (!addr) return "<null>";
    char host[NI_MAXHOST];
    int rv = getnameinfo((const sockaddr*)addr, sizeof(sockaddr_storage), host, sizeof(host),
                         nullptr, 0, NI_NUMERICHOST);
    if (rv != 0) return gai_strerror(rv);
    return host;
}

std::vector<std::string> ToStrings(const hostent* he) {
    std::vector<std::string> hosts;
    if (he == nullptr) {
        hosts.push_back("<null>");
        return hosts;
    }
    uint32_t i = 0;
    while (he->h_addr_list[i] != nullptr) {
        char host[INET6_ADDRSTRLEN];
        if (!inet_ntop(he->h_addrtype, he->h_addr_list[i], host, sizeof(host))) {
            hosts.push_back("<invalid>");
            return hosts;
        } else {
            hosts.push_back(host);
        }
        i++;
    }
    if (hosts.empty()) hosts.push_back("<invalid>");
    return hosts;
}

std::vector<std::string> ToStrings(const addrinfo* ai) {
    std::vector<std::string> hosts;
    if (!ai) {
        hosts.push_back("<null>");
        return hosts;
    }
    for (const auto* aip = ai; aip != nullptr; aip = aip->ai_next) {
        const sockaddr* ai_addr = aip->ai_addr;
        char host[NI_MAXHOST];
        const int rv = getnameinfo(ai_addr, aip->ai_addrlen, host, sizeof(host), nullptr, 0,
                                   NI_NUMERICHOST);
        if (rv != 0) {
            hosts.clear();
            hosts.push_back(gai_strerror(rv));
            return hosts;
        } else {
            hosts.push_back(host);
        }
    }
    if (hosts.empty()) hosts.push_back("<invalid>");
    return hosts;
}

std::vector<std::string> ToStrings(const ScopedAddrinfo& ai) {
    return ToStrings(ai.get());
}

size_t GetNumQueries(const test::DNSResponder& dns, const char* name) {
    std::vector<test::DNSResponder::QueryInfo> queries = dns.queries();
    size_t found = 0;
    for (const auto& p : queries) {
        if (p.name == name) {
            ++found;
        }
    }
    return found;
}

size_t GetNumQueriesForProtocol(const test::DNSResponder& dns, const int protocol,
                                const char* name) {
    std::vector<test::DNSResponder::QueryInfo> queries = dns.queries();
    size_t found = 0;
    for (const auto& p : queries) {
        if (p.protocol == protocol && p.name == name) {
            ++found;
        }
    }
    return found;
}

size_t GetNumQueriesForType(const test::DNSResponder& dns, ns_type type, const char* name) {
    std::vector<test::DNSResponder::QueryInfo> queries = dns.queries();
    size_t found = 0;
    for (const auto& p : queries) {
        if (p.type == type && p.name == name) {
            ++found;
        }
    }
    return found;
}

bool PollForCondition(const std::function<bool()>& condition, std::chrono::milliseconds timeout) {
    constexpr std::chrono::milliseconds retryIntervalMs{5};
    android::base::Timer t;
    while (t.duration() < timeout) {
        if (condition()) return true;
        std::this_thread::sleep_for(retryIntervalMs);
    }
    return false;
}

ScopedAddrinfo safe_getaddrinfo(const char* node, const char* service,
                                const struct addrinfo* hints) {
    addrinfo* result = nullptr;
    if (getaddrinfo(node, service, hints, &result) != 0) {
        result = nullptr;  // Should already be the case, but...
    }
    return ScopedAddrinfo(result);
}

int WaitChild(pid_t pid) {
    int status;
    const pid_t got_pid = TEMP_FAILURE_RETRY(waitpid(pid, &status, 0));

    if (got_pid != pid) {
        PLOG(WARNING) << __func__ << ": waitpid failed: wanted " << pid << ", got " << got_pid;
        return 1;
    }

    if (WIFEXITED(status) && WEXITSTATUS(status) == 0) {
        return 0;
    } else {
        return status;
    }
}

int ForkAndRun(const std::vector<std::string>& args) {
    std::vector<const char*> argv;
    argv.resize(args.size() + 1, nullptr);
    std::transform(args.begin(), args.end(), argv.begin(),
                   [](const std::string& in) { return in.c_str(); });

    pid_t pid = fork();
    if (pid == -1) {
        // Fork failed.
        PLOG(ERROR) << __func__ << ": Unable to fork";
        return -1;
    }

    if (pid == 0) {
        execv(argv[0], const_cast<char**>(argv.data()));
        PLOG(ERROR) << __func__ << ": execv failed";
        _exit(1);
    }

    int rc = WaitChild(pid);
    if (rc != 0) {
        PLOG(ERROR) << __func__ << ": Failed run: status=" << rc;
    }
    return rc;
}

// Add routing rules for MDNS packets, or MDNS packets won't know the destination is MDNS
// muticast address "224.0.0.251".
void SetMdnsRoute() {
    const std::vector<std::string> args = {
            "system/bin/ip", "route",  "add",   "local", "224.0.0.251", "dev",       "lo",
            "proto",         "static", "scope", "host",  "src",         "127.0.0.1",
    };
    EXPECT_EQ(0, ForkAndRun(args));

    const std::vector<std::string> args_v6 = {
            "system/bin/ip", "-6", "route", "add",    "local", "ff02::fb",
            "dev",           "lo", "proto", "static", "src",   "::1",
    };
    EXPECT_EQ(0, ForkAndRun(args_v6));
}

void RemoveMdnsRoute() {
    const std::vector<std::string> args = {
            "system/bin/ip", "route",  "del",   "local", "224.0.0.251", "dev",       "lo",
            "proto",         "static", "scope", "host",  "src",         "127.0.0.1",
    };
    EXPECT_EQ(0, ForkAndRun(args));

    const std::vector<std::string> args_v6 = {
            "system/bin/ip", "-6", "route", "del",    "local", "ff02::fb",
            "dev",           "lo", "proto", "static", "src",   "::1",
    };
    EXPECT_EQ(0, ForkAndRun(args_v6));
}

void AllowNetworkInBackground(int uid, bool allow) {
    if (android::modules::sdklevel::IsAtLeastV()) {
        // Background networking is always allowed on earlier versions.
        Firewall* firewall = Firewall::getInstance();
        if (allow) {
            firewall->addRule(uid, BACKGROUND_MATCH);
        } else {
            firewall->removeRule(uid, BACKGROUND_MATCH);
        }
    }
}

bool is64bitAbi() {
    return android::base::GetProperty("ro.product.cpu.abi", "").find("64") != std::string::npos;
}
