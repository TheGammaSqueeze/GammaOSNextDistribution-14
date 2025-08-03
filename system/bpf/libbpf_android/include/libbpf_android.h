/*
 * Copyright (C) 2018 The Android Open Source Project
 * Android BPF library - public API
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

#include <linux/bpf.h>

#include <fstream>

namespace android {
namespace bpf {

// Bpf programs may specify per-program & per-map selinux_context and pin_subdir.
//
// The BpfLoader needs to convert these bpf.o specified strings into an enum
// for internal use (to check that valid values were specified for the specific
// location of the bpf.o file).
//
// It also needs to map selinux_context's into pin_subdir's.
// This is because of how selinux_context is actually implemented via pin+rename.
//
// Thus 'domain' enumerates all selinux_context's/pin_subdir's that the BpfLoader
// is aware of.  Thus there currently needs to be a 1:1 mapping between the two.
//
enum class domain : int {
    unrecognized = -1,  // invalid for this version of the bpfloader
    unspecified = 0,    // means just use the default for that specific pin location
    platform,           //      fs_bpf               /sys/fs/bpf
    vendor,             // (T+) fs_bpf_vendor        /sys/fs/bpf/vendor
    loader,             // (U+) fs_bpf_loader        /sys/fs/bpf/loader
};

// Note: this does not include domain::unrecognized, but does include domain::unspecified
static constexpr domain AllDomains[] = {
    domain::unspecified,
    domain::platform,
    domain::vendor,
    domain::loader,
};

static constexpr bool unrecognized(domain d) {
    return d == domain::unrecognized;
}

// Note: this doesn't handle unrecognized, handle it first.
static constexpr bool specified(domain d) {
    return d != domain::unspecified;
}

static constexpr unsigned long long domainToBitmask(domain d) {
    return specified(d) ? 1uLL << (static_cast<int>(d) - 1) : 0;
}

static constexpr bool inDomainBitmask(domain d, unsigned long long v) {
    return domainToBitmask(d) & v;
}

struct Location {
    const char* const dir = "";
    const char* const prefix = "";
    unsigned long long allowedDomainBitmask = 0;
    const bpf_prog_type* allowedProgTypes = nullptr;
    size_t allowedProgTypesLength = 0;
};

// BPF loader implementation. Loads an eBPF ELF object
int loadProg(const char* elfPath, bool* isCritical, const Location &location = {});

// Exposed for testing
unsigned int readSectionUint(const char* name, std::ifstream& elfFile, unsigned int defVal);

// Returns the build type string (from ro.build.type).
const std::string& getBuildType();

// The following functions classify the 3 Android build types.
inline bool isEng() {
    return getBuildType() == "eng";
}
inline bool isUser() {
    return getBuildType() == "user";
}
inline bool isUserdebug() {
    return getBuildType() == "userdebug";
}

}  // namespace bpf
}  // namespace android
