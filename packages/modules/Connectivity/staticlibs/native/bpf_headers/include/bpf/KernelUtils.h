/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <stdio.h>
#include <string.h>
#include <sys/personality.h>
#include <sys/utsname.h>

namespace android {
namespace bpf {

#define KVER(a, b, c) (((a) << 24) + ((b) << 16) + (c))

static inline unsigned uncachedKernelVersion() {
    struct utsname buf;
    if (uname(&buf)) return 0;

    unsigned kver_major = 0;
    unsigned kver_minor = 0;
    unsigned kver_sub = 0;
    (void)sscanf(buf.release, "%u.%u.%u", &kver_major, &kver_minor, &kver_sub);
    return KVER(kver_major, kver_minor, kver_sub);
}

static inline unsigned kernelVersion() {
    static unsigned kver = uncachedKernelVersion();
    return kver;
}

static inline __unused bool isAtLeastKernelVersion(unsigned major, unsigned minor, unsigned sub) {
    return kernelVersion() >= KVER(major, minor, sub);
}

// Figure out the bitness of userspace.
// Trivial and known at compile time.
static constexpr bool isUserspace32bit() {
    return sizeof(void*) == 4;
}

static constexpr bool isUserspace64bit() {
    return sizeof(void*) == 8;
}

#if defined(__LP64__)
static_assert(isUserspace64bit(), "huh? LP64 must have 64-bit userspace");
#elif defined(__ILP32__)
static_assert(isUserspace32bit(), "huh? ILP32 must have 32-bit userspace");
#else
#error "huh? must be either LP64 (64-bit userspace) or ILP32 (32-bit userspace)"
#endif

static_assert(isUserspace32bit() || isUserspace64bit(), "must be either 32 or 64 bit");

// Figure out the bitness of the kernel.
static inline bool isKernel64Bit() {
    // a 64-bit userspace requires a 64-bit kernel
    if (isUserspace64bit()) return true;

    static bool init = false;
    static bool cache = false;
    if (init) return cache;

    // Retrieve current personality - on Linux this system call *cannot* fail.
    int p = personality(0xffffffff);
    // But if it does just assume kernel and userspace (which is 32-bit) match...
    if (p == -1) return false;

    // This will effectively mask out the bottom 8 bits, and switch to 'native'
    // personality, and then return the previous personality of this thread
    // (likely PER_LINUX or PER_LINUX32) with any extra options unmodified.
    int q = personality((p & ~PER_MASK) | PER_LINUX);
    // Per man page this theoretically could error out with EINVAL,
    // but kernel code analysis suggests setting PER_LINUX cannot fail.
    // Either way, assume kernel and userspace (which is 32-bit) match...
    if (q != p) return false;

    struct utsname u;
    (void)uname(&u);  // only possible failure is EFAULT, but u is on stack.

    // Switch back to previous personality.
    // Theoretically could fail with EINVAL on arm64 with no 32-bit support,
    // but then we wouldn't have fetched 'p' from the kernel in the first place.
    // Either way there's nothing meaningful we can do in case of error.
    // Since PER_LINUX32 vs PER_LINUX only affects uname.machine it doesn't
    // really hurt us either.  We're really just switching back to be 'clean'.
    (void)personality(p);

    // Possible values of utsname.machine observed on x86_64 desktop (arm via qemu):
    //   x86_64 i686 aarch64 armv7l
    // additionally observed on arm device:
    //   armv8l
    // presumably also might just be possible:
    //   i386 i486 i586
    // and there might be other weird arm32 cases.
    // We note that the 64 is present in both 64-bit archs,
    // and in general is likely to be present in only 64-bit archs.
    cache = !!strstr(u.machine, "64");
    init = true;
    return cache;
}

static inline __unused bool isKernel32Bit() {
    return !isKernel64Bit();
}

static constexpr bool isArm() {
#if defined(__arm__)
    static_assert(isUserspace32bit(), "huh? arm must be 32 bit");
    return true;
#elif defined(__aarch64__)
    static_assert(isUserspace64bit(), "aarch64 must be LP64 - no support for ILP32");
    return true;
#else
    return false;
#endif
}

static constexpr bool isX86() {
#if defined(__i386__)
    static_assert(isUserspace32bit(), "huh? i386 must be 32 bit");
    return true;
#elif defined(__x86_64__)
    static_assert(isUserspace64bit(), "x86_64 must be LP64 - no support for ILP32 (x32)");
    return true;
#else
    return false;
#endif
}

static constexpr bool isRiscV() {
#if defined(__riscv)
    static_assert(isUserspace64bit(), "riscv must be 64 bit");
    return true;
#else
    return false;
#endif
}

static_assert(isArm() || isX86() || isRiscV(), "Unknown architecture");

static __unused const char * describeArch() {
    // ordered so as to make it easier to compile time optimize,
    // only thing not known at compile time is isKernel64Bit()
    if (isUserspace64bit()) {
        if (isArm()) return "64-on-aarch64";
        if (isX86()) return "64-on-x86-64";
        if (isRiscV()) return "64-on-riscv64";
    } else if (isKernel64Bit()) {
        if (isArm()) return "32-on-aarch64";
        if (isX86()) return "32-on-x86-64";
    } else {
        if (isArm()) return "32-on-arm32";
        if (isX86()) return "32-on-x86-32";
    }
}

}  // namespace bpf
}  // namespace android
