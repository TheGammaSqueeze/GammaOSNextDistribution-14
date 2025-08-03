/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <arpa/inet.h>
#include <errno.h>
#include <linux/if_packet.h>
#include <linux/if_tun.h>
#include <net/if.h>
#include <poll.h>
#include <sched.h>
#include <sys/capability.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/types.h>

#include <gtest/gtest.h>

#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>

#define LOG_TAG "NetdTest"

#include "TcUtils.h"

namespace android {
namespace net {

using base::unique_fd;

TEST(NetUtilsWrapperTest, TestFileCapabilities) {
    errno = 0;
    ASSERT_EQ(NULL, cap_get_file("/system/bin/netutils-wrapper-1.0"));
    ASSERT_EQ(ENODATA, errno);
}

// If this test fails most likely your device is lacking device/oem specific
// selinux genfscon rules, something like .../vendor/.../genfs_contexts:
//   genfscon sysfs /devices/platform/.../net u:object_r:sysfs_net:s0
// Easiest debugging is via:
//   adb root && sleep 1 && adb shell 'ls -Z /sys/class/net/*/mtu'
// and look for the mislabeled item(s).
// Everything should be 'u:object_r:sysfs_net:s0'
//
// Another useful command is:
//   adb root && sleep 1 && adb shell find /sys > dump.out
// or in particular:
//   adb root && sleep 1 && adb shell find /sys | egrep '/net$'
// which might (among other things) print out something like:
//   /sys/devices/platform/11110000.usb/11110000.dwc3/gadget/net
// which means you need to add:
//   genfscon sysfs /devices/platform/11110000.usb/11110000.dwc3/gadget/net u:object_r:sysfs_net:s0
TEST(NetdSELinuxTest, CheckProperMTULabels) {
    // Since we expect the egrep regexp to filter everything out,
    // we thus expect no matches and thus a return code of 1
    // NOLINTNEXTLINE(cert-env33-c)
    ASSERT_EQ(W_EXITCODE(1, 0), system("ls -Z /sys/class/net/*/mtu | egrep -q -v "
                                       "'^u:object_r:sysfs_net:s0 /sys/class/net/'"));
}

static void assertBpfContext(const char* const target, const char* const label) {
    // Use 'ls' cli utility to print the selinux context of the target directory or file.
    // egrep -q will return 0 if it matches, ie. if the selinux context is as expected
    std::string cmd = android::base::StringPrintf("ls -dZ %s | egrep -q '^u:object_r:%s:s0 %s$'",
                                                  target, label, target);

    // NOLINTNEXTLINE(cert-env33-c)
    ASSERT_EQ(W_EXITCODE(0, 0), system(cmd.c_str())) << cmd << " - did not return success(0)"
        " - is kernel missing https://android-review.googlesource.com/c/kernel/common/+/1831252"
        " 'UPSTREAM: security: selinux: allow per-file labeling for bpffs' ?";
}

// This test will fail if kernel is missing:
//   https://android-review.googlesource.com/c/kernel/common/+/1831252
//   UPSTREAM: security: selinux: allow per-file labeling for bpffs
TEST(NetdSELinuxTest, CheckProperBpfLabels) {
    assertBpfContext("/sys/fs/bpf", "fs_bpf");
    assertBpfContext("/sys/fs/bpf/net_private", "fs_bpf_net_private");
    assertBpfContext("/sys/fs/bpf/net_shared", "fs_bpf_net_shared");
    assertBpfContext("/sys/fs/bpf/netd_readonly", "fs_bpf_netd_readonly");
    assertBpfContext("/sys/fs/bpf/netd_shared", "fs_bpf_netd_shared");
    assertBpfContext("/sys/fs/bpf/tethering", "fs_bpf_tethering");
    assertBpfContext("/sys/fs/bpf/vendor", "fs_bpf_vendor");
    assertBpfContext("/sys/fs/bpf/loader", "fs_bpf_loader");
}

// Trivial thread function that simply immediately terminates successfully.
static int thread(void*) {
    return 0;
}

typedef int (*thread_t)(void*);

static void nsTest(int flags, bool success, thread_t newThread) {
    // We need a minimal stack, but not clear if it will grow up or down,
    // So allocate 2 pages and give a pointer to the middle.
    static const size_t kPageSize = getpagesize();
    static std::vector<char> stack(kPageSize * 2);

    errno = 0;
    // VFORK: if thread is successfully created, then kernel will wait for it
    // to terminate before we resume -> hence static stack is safe to reuse.
    int tid = clone(newThread, &stack[kPageSize], flags | CLONE_VFORK, NULL);
    if (success) {
        ASSERT_EQ(errno, 0);
        ASSERT_GE(tid, 0);
    } else {
        ASSERT_EQ(errno, EINVAL);
        ASSERT_EQ(tid, -1);
    }
}

// Test kernel configuration option CONFIG_NAMESPACES=y
TEST(NetdNamespaceTest, CheckMountNamespaceSupport) {
    nsTest(CLONE_NEWNS, true, thread);
}

// Test kernel configuration option CONFIG_UTS_NS=y
TEST(NetdNamespaceTest, CheckUTSNamespaceSupport) {
    nsTest(CLONE_NEWUTS, true, thread);
}

// Test kernel configuration option CONFIG_NET_NS=y
TEST(NetdNamespaceTest, CheckNetworkNamespaceSupport) {
    nsTest(CLONE_NEWNET, true, thread);
}

// Test kernel configuration option CONFIG_USER_NS=n
TEST(NetdNamespaceTest, /*DISABLED_*/ CheckNoUserNamespaceSupport) {
    nsTest(CLONE_NEWUSER, false, thread);
}

// Test for all of the above
TEST(NetdNamespaceTest, CheckFullNamespaceSupport) {
    nsTest(CLONE_NEWNS | CLONE_NEWUTS | CLONE_NEWNET, true, thread);
}

}  // namespace net
}  // namespace android
