/*
 * Copyright (C) 2017-2023 The Android Open Source Project
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

#ifndef LOG_TAG
#define LOG_TAG "NetBpfLoad"
#endif

#include <arpa/inet.h>
#include <dirent.h>
#include <elf.h>
#include <error.h>
#include <fcntl.h>
#include <inttypes.h>
#include <linux/bpf.h>
#include <linux/unistd.h>
#include <net/if.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <android/api-level.h>
#include <android-base/logging.h>
#include <android-base/macros.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <log/log.h>

#include "BpfSyscallWrappers.h"
#include "bpf/BpfUtils.h"
#include "loader.h"

using android::base::EndsWith;
using android::bpf::domain;
using std::string;

bool exists(const char* const path) {
    int v = access(path, F_OK);
    if (!v) {
        ALOGI("%s exists.", path);
        return true;
    }
    if (errno == ENOENT) return false;
    ALOGE("FATAL: access(%s, F_OK) -> %d [%d:%s]", path, v, errno, strerror(errno));
    abort();  // can only hit this if permissions (likely selinux) are screwed up
}


const android::bpf::Location locations[] = {
        // S+ Tethering mainline module (network_stack): tether offload
        {
                .dir = "/apex/com.android.tethering/etc/bpf/",
                .prefix = "tethering/",
        },
        // T+ Tethering mainline module (shared with netd & system server)
        // netutils_wrapper (for iptables xt_bpf) has access to programs
        {
                .dir = "/apex/com.android.tethering/etc/bpf/netd_shared/",
                .prefix = "netd_shared/",
        },
        // T+ Tethering mainline module (shared with netd & system server)
        // netutils_wrapper has no access, netd has read only access
        {
                .dir = "/apex/com.android.tethering/etc/bpf/netd_readonly/",
                .prefix = "netd_readonly/",
        },
        // T+ Tethering mainline module (shared with system server)
        {
                .dir = "/apex/com.android.tethering/etc/bpf/net_shared/",
                .prefix = "net_shared/",
        },
        // T+ Tethering mainline module (not shared, just network_stack)
        {
                .dir = "/apex/com.android.tethering/etc/bpf/net_private/",
                .prefix = "net_private/",
        },
};

int loadAllElfObjects(const android::bpf::Location& location) {
    int retVal = 0;
    DIR* dir;
    struct dirent* ent;

    if ((dir = opendir(location.dir)) != NULL) {
        while ((ent = readdir(dir)) != NULL) {
            string s = ent->d_name;
            if (!EndsWith(s, ".o")) continue;

            string progPath(location.dir);
            progPath += s;

            bool critical;
            int ret = android::bpf::loadProg(progPath.c_str(), &critical, location);
            if (ret) {
                if (critical) retVal = ret;
                ALOGE("Failed to load object: %s, ret: %s", progPath.c_str(), std::strerror(-ret));
            } else {
                ALOGI("Loaded object: %s", progPath.c_str());
            }
        }
        closedir(dir);
    }
    return retVal;
}

int createSysFsBpfSubDir(const char* const prefix) {
    if (*prefix) {
        mode_t prevUmask = umask(0);

        string s = "/sys/fs/bpf/";
        s += prefix;

        errno = 0;
        int ret = mkdir(s.c_str(), S_ISVTX | S_IRWXU | S_IRWXG | S_IRWXO);
        if (ret && errno != EEXIST) {
            const int err = errno;
            ALOGE("Failed to create directory: %s, ret: %s", s.c_str(), std::strerror(err));
            return -err;
        }

        umask(prevUmask);
    }
    return 0;
}

// Technically 'value' doesn't need to be newline terminated, but it's best
// to include a newline to match 'echo "value" > /proc/sys/...foo' behaviour,
// which is usually how kernel devs test the actual sysctl interfaces.
int writeProcSysFile(const char *filename, const char *value) {
    android::base::unique_fd fd(open(filename, O_WRONLY | O_CLOEXEC));
    if (fd < 0) {
        const int err = errno;
        ALOGE("open('%s', O_WRONLY | O_CLOEXEC) -> %s", filename, strerror(err));
        return -err;
    }
    int len = strlen(value);
    int v = write(fd, value, len);
    if (v < 0) {
        const int err = errno;
        ALOGE("write('%s', '%s', %d) -> %s", filename, value, len, strerror(err));
        return -err;
    }
    if (v != len) {
        // In practice, due to us only using this for /proc/sys/... files, this can't happen.
        ALOGE("write('%s', '%s', %d) -> short write [%d]", filename, value, len, v);
        return -EINVAL;
    }
    return 0;
}

#define APEX_MOUNT_POINT "/apex/com.android.tethering"
const char * const platformBpfLoader = "/system/bin/bpfloader";
const char * const platformNetBpfLoad = "/system/bin/netbpfload";
const char * const apexNetBpfLoad = APEX_MOUNT_POINT "/bin/netbpfload";

int logTetheringApexVersion(void) {
    char * found_blockdev = NULL;
    FILE * f = NULL;
    char buf[4096];

    f = fopen("/proc/mounts", "re");
    if (!f) return 1;

    // /proc/mounts format: block_device [space] mount_point [space] other stuff... newline
    while (fgets(buf, sizeof(buf), f)) {
        char * blockdev = buf;
        char * space = strchr(blockdev, ' ');
        if (!space) continue;
        *space = '\0';
        char * mntpath = space + 1;
        space = strchr(mntpath, ' ');
        if (!space) continue;
        *space = '\0';
        if (strcmp(mntpath, APEX_MOUNT_POINT)) continue;
        found_blockdev = strdup(blockdev);
        break;
    }
    fclose(f);
    f = NULL;

    if (!found_blockdev) return 2;
    ALOGD("Found Tethering Apex mounted from blockdev %s", found_blockdev);

    f = fopen("/proc/mounts", "re");
    if (!f) { free(found_blockdev); return 3; }

    while (fgets(buf, sizeof(buf), f)) {
        char * blockdev = buf;
        char * space = strchr(blockdev, ' ');
        if (!space) continue;
        *space = '\0';
        char * mntpath = space + 1;
        space = strchr(mntpath, ' ');
        if (!space) continue;
        *space = '\0';
        if (strcmp(blockdev, found_blockdev)) continue;
        if (strncmp(mntpath, APEX_MOUNT_POINT "@", strlen(APEX_MOUNT_POINT "@"))) continue;
        char * at = strchr(mntpath, '@');
        if (!at) continue;
        char * ver = at + 1;
        ALOGI("Tethering APEX version %s", ver);
    }
    fclose(f);
    free(found_blockdev);
    return 0;
}

int main(int argc, char** argv, char * const envp[]) {
    (void)argc;
    android::base::InitLogging(argv, &android::base::KernelLogger);

    ALOGI("NetBpfLoad '%s' starting...", argv[0]);

    // true iff we are running from the module
    const bool is_mainline = !strcmp(argv[0], apexNetBpfLoad);

    // true iff we are running from the platform
    const bool is_platform = !strcmp(argv[0], platformNetBpfLoad);

    const int device_api_level = android_get_device_api_level();
    const bool isAtLeastT = (device_api_level >= __ANDROID_API_T__);
    const bool isAtLeastU = (device_api_level >= __ANDROID_API_U__);
    const bool isAtLeastV = (device_api_level >= __ANDROID_API_V__);
    bool failed = false;

    // last in U QPR2 beta1
    const bool has_platform_bpfloader_rc = exists("/system/etc/init/bpfloader.rc");
    // first in U QPR2 beta~2
    const bool has_platform_netbpfload_rc = exists("/system/etc/init/netbpfload.rc");

    ALOGI("NetBpfLoad api:%d/%d kver:%07x platform:%d mainline:%d rc:%d%d",
          android_get_application_target_sdk_version(), device_api_level,
          android::bpf::kernelVersion(), is_platform, is_mainline,
          has_platform_bpfloader_rc, has_platform_netbpfload_rc);

    if (!is_platform && !is_mainline) {
        ALOGE("Unable to determine if we're platform or mainline netbpfload.");
        return 1;
    }

    if (is_platform) {
        ALOGI("Executing apex netbpfload...");
        const char * args[] = { apexNetBpfLoad, NULL, };
        execve(args[0], (char**)args, envp);
        ALOGE("exec '%s' fail: %d[%s]", apexNetBpfLoad, errno, strerror(errno));
        return 1;
    }

    if (!has_platform_bpfloader_rc && !has_platform_netbpfload_rc) {
        ALOGE("Unable to find platform's bpfloader & netbpfload init scripts.");
        return 1;
    }

    if (has_platform_bpfloader_rc && has_platform_netbpfload_rc) {
        ALOGE("Platform has *both* bpfloader & netbpfload init scripts.");
        return 1;
    }

    logTetheringApexVersion();

    if (is_mainline && has_platform_bpfloader_rc && !has_platform_netbpfload_rc) {
        // Tethering apex shipped initrc file causes us to reach here
        // but we're not ready to correctly handle anything before U QPR2
        // in which the 'bpfloader' vs 'netbpfload' split happened
        const char * args[] = { platformBpfLoader, NULL, };
        execve(args[0], (char**)args, envp);
        ALOGE("exec '%s' fail: %d[%s]", platformBpfLoader, errno, strerror(errno));
        return 1;
    }

    if (isAtLeastT && !android::bpf::isAtLeastKernelVersion(4, 9, 0)) {
        ALOGW("Android T requires kernel 4.9.");
        failed = true;
    }

    if (isAtLeastU && !android::bpf::isAtLeastKernelVersion(4, 14, 0)) {
        ALOGW("Android U requires kernel 4.14.");
        failed = true;
    }

    if (isAtLeastV && !android::bpf::isAtLeastKernelVersion(4, 19, 0)) {
        ALOGE("Android V requires kernel 4.19.");
        failed = true;
    }

    if (isAtLeastV && android::bpf::isX86() && !android::bpf::isKernel64Bit()) {
        ALOGE("Android V requires X86 kernel to be 64-bit.");
        return 1;
    }

    if (android::bpf::isUserspace32bit() && android::bpf::isAtLeastKernelVersion(6, 2, 0)) {
        /* Android 14/U should only launch on 64-bit kernels
         *   T launches on 5.10/5.15
         *   U launches on 5.15/6.1
         * So >=5.16 implies isKernel64Bit()
         *
         * We thus added a test to V VTS which requires 5.16+ devices to use 64-bit kernels.
         *
         * Starting with Android V, which is the first to support a post 6.1 Linux Kernel,
         * we also require 64-bit userspace.
         *
         * There are various known issues with 32-bit userspace talking to various
         * kernel interfaces (especially CAP_NET_ADMIN ones) on a 64-bit kernel.
         * Some of these have userspace or kernel workarounds/hacks.
         * Some of them don't...
         * We're going to be removing the hacks.
         *
         * Additionally the 32-bit kernel jit support is poor,
         * and 32-bit userspace on 64-bit kernel bpf ringbuffer compatibility is broken.
         */
        ALOGE("64-bit userspace required on 6.2+ kernels.");
        failed = true;
    }

    // Ensure we can determine the Android build type.
    if (!android::bpf::isEng() && !android::bpf::isUser() && !android::bpf::isUserdebug()) {
        ALOGE("Failed to determine the build type: got %s, want 'eng', 'user', or 'userdebug'",
              android::bpf::getBuildType().c_str());
        failed = true;
    }

    if (isAtLeastU) {
        // Linux 5.16-rc1 changed the default to 2 (disabled but changeable),
        // but we need 0 (enabled)
        // (this writeFile is known to fail on at least 4.19, but always defaults to 0 on
        // pre-5.13, on 5.13+ it depends on CONFIG_BPF_UNPRIV_DEFAULT_OFF)
        writeProcSysFile("/proc/sys/kernel/unprivileged_bpf_disabled", "0\n");

        // Enable the eBPF JIT -- but do note that on 64-bit kernels it is likely
        // already force enabled by the kernel config option BPF_JIT_ALWAYS_ON.
        // (Note: this (open) will fail with ENOENT 'No such file or directory' if
        //  kernel does not have CONFIG_BPF_JIT=y)
        // BPF_JIT is required by R VINTF (which means 4.14/4.19/5.4 kernels),
        // but 4.14/4.19 were released with P & Q, and only 5.4 is new in R+.
        writeProcSysFile("/proc/sys/net/core/bpf_jit_enable", "1\n");

        // Enable JIT kallsyms export for privileged users only
        // (Note: this (open) will fail with ENOENT 'No such file or directory' if
        //  kernel does not have CONFIG_HAVE_EBPF_JIT=y)
        writeProcSysFile("/proc/sys/net/core/bpf_jit_kallsyms", "1\n");
    }

    // Create all the pin subdirectories
    // (this must be done first to allow selinux_context and pin_subdir functionality,
    //  which could otherwise fail with ENOENT during object pinning or renaming,
    //  due to ordering issues)
    for (const auto& location : locations) {
        if (createSysFsBpfSubDir(location.prefix)) return 1;
    }

    // Note: there's no actual src dir for fs_bpf_loader .o's,
    // so it is not listed in 'locations[].prefix'.
    // This is because this is primarily meant for triggering genfscon rules,
    // and as such this will likely always be the case.
    // Thus we need to manually create the /sys/fs/bpf/loader subdirectory.
    if (createSysFsBpfSubDir("loader")) return 1;

    // Load all ELF objects, create programs and maps, and pin them
    for (const auto& location : locations) {
        if (loadAllElfObjects(location) != 0) {
            failed = true;
        }
    }

    if (failed) {
        ALOGE("=== CRITICAL FAILURE LOADING BPF PROGRAMS ===");
        ALOGE("If this triggers reliably, you're probably missing kernel options or patches.");
        ALOGE("If this triggers randomly, you might be hitting some memory allocation "
                "problems or startup script race.");
        ALOGE("--- DO NOT EXPECT SYSTEM TO BOOT SUCCESSFULLY ---");
     }

    ALOGI("done, transferring control to platform bpfloader.");

    const char * args[] = { platformBpfLoader, NULL, };
    execve(args[0], (char**)args, envp);
    ALOGE("FATAL: execve('%s'): %d[%s]", platformBpfLoader, errno, strerror(errno));
    return 1;
}
