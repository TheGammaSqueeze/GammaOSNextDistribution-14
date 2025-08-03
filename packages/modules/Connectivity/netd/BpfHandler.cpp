/**
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "BpfHandler"

#include "BpfHandler.h"

#include <linux/bpf.h>
#include <inttypes.h>

#include <android-base/unique_fd.h>
#include <android-modules-utils/sdk_level.h>
#include <bpf/WaitForProgsLoaded.h>
#include <log/log.h>
#include <netdutils/UidConstants.h>
#include <private/android_filesystem_config.h>

#include "BpfSyscallWrappers.h"

namespace android {
namespace net {

using base::unique_fd;
using bpf::getSocketCookie;
using bpf::retrieveProgram;
using netdutils::Status;
using netdutils::statusFromErrno;

constexpr int PER_UID_STATS_ENTRIES_LIMIT = 500;
// At most 90% of the stats map may be used by tagged traffic entries. This ensures
// that 10% of the map is always available to count untagged traffic, one entry per UID.
// Otherwise, apps would be able to avoid data usage accounting entirely by filling up the
// map with tagged traffic entries.
constexpr int TOTAL_UID_STATS_ENTRIES_LIMIT = STATS_MAP_SIZE * 0.9;

static_assert(STATS_MAP_SIZE - TOTAL_UID_STATS_ENTRIES_LIMIT > 100,
              "The limit for stats map is to high, stats data may be lost due to overflow");

static Status attachProgramToCgroup(const char* programPath, const unique_fd& cgroupFd,
                                    bpf_attach_type type) {
    unique_fd cgroupProg(retrieveProgram(programPath));
    if (!cgroupProg.ok()) {
        return statusFromErrno(errno, fmt::format("Failed to get program from {}", programPath));
    }
    if (android::bpf::attachProgram(type, cgroupProg, cgroupFd)) {
        return statusFromErrno(errno, fmt::format("Program {} attach failed", programPath));
    }
    return netdutils::status::ok;
}

static Status checkProgramAccessible(const char* programPath) {
    unique_fd prog(retrieveProgram(programPath));
    if (!prog.ok()) {
        return statusFromErrno(errno, fmt::format("Failed to get program from {}", programPath));
    }
    return netdutils::status::ok;
}

static Status initPrograms(const char* cg2_path) {
    if (!cg2_path) return Status("cg2_path is NULL");

    // This code was mainlined in T, so this should be trivially satisfied.
    if (!modules::sdklevel::IsAtLeastT()) return Status("S- platform is unsupported");

    if (modules::sdklevel::IsAtLeastV()) {
        // V bumps the kernel requirement up to 4.19
        // see also: //system/netd/tests/kernel_test.cpp TestKernel419
        if (!bpf::isAtLeastKernelVersion(4, 19, 0)) {
            return Status("V+ platform with kernel version < 4.19.0 is unsupported");
        }

        // Technically already required by U, but only enforce on V+
        // see also: //system/netd/tests/kernel_test.cpp TestKernel64Bit
        if (bpf::isKernel32Bit() && bpf::isAtLeastKernelVersion(5, 16, 0)) {
            return Status("V+ platform with 32 bit kernel, version >= 5.16.0 is unsupported");
        }
    }

    // Linux 6.1 is highest version supported by U, starting with V new kernels,
    // ie. 6.2+ we are dropping various kernel/system userspace 32-on-64 hacks
    // (for example "ANDROID: xfrm: remove in_compat_syscall() checks").
    // Note: this check/enforcement only applies to *system* userspace code,
    // it does not affect unprivileged apps, the 32-on-64 compatibility
    // problems are AFAIK limited to various CAP_NET_ADMIN protected interfaces.
    // see also: //system/bpf/bpfloader/BpfLoader.cpp main()
    if (bpf::isUserspace32bit() && bpf::isAtLeastKernelVersion(6, 2, 0)) {
        return Status("32 bit userspace with Kernel version >= 6.2.0 is unsupported");
    }

    // U mandates this mount point (though it should also be the case on T)
    if (modules::sdklevel::IsAtLeastU() && !!strcmp(cg2_path, "/sys/fs/cgroup")) {
        return Status("U+ platform with cg2_path != /sys/fs/cgroup is unsupported");
    }

    unique_fd cg_fd(open(cg2_path, O_DIRECTORY | O_RDONLY | O_CLOEXEC));
    if (!cg_fd.ok()) {
        const int err = errno;
        ALOGE("Failed to open the cgroup directory: %s", strerror(err));
        return statusFromErrno(err, "Open the cgroup directory failed");
    }
    RETURN_IF_NOT_OK(checkProgramAccessible(XT_BPF_ALLOWLIST_PROG_PATH));
    RETURN_IF_NOT_OK(checkProgramAccessible(XT_BPF_DENYLIST_PROG_PATH));
    RETURN_IF_NOT_OK(checkProgramAccessible(XT_BPF_EGRESS_PROG_PATH));
    RETURN_IF_NOT_OK(checkProgramAccessible(XT_BPF_INGRESS_PROG_PATH));
    auto ret = attachProgramToCgroup(BPF_EGRESS_PROG_PATH, cg_fd, BPF_CGROUP_INET_EGRESS);
    if (!isOk(ret)) {
        ALOGE("Failed loading egress program");
    }
    auto ret2 = attachProgramToCgroup(BPF_INGRESS_PROG_PATH, cg_fd, BPF_CGROUP_INET_INGRESS);
    if (!isOk(ret)) {
        ALOGE("Failed loading ingress program");
    }

    // For the devices that support cgroup socket filter, the socket filter
    // should be loaded successfully by bpfloader. So we attach the filter to
    // cgroup if the program is pinned properly.
    // TODO: delete the if statement once all devices should support cgroup
    // socket filter (ie. the minimum kernel version required is 4.14).
    if (bpf::isAtLeastKernelVersion(4, 14, 0)) {
        RETURN_IF_NOT_OK(
                attachProgramToCgroup(CGROUP_SOCKET_PROG_PATH, cg_fd, BPF_CGROUP_INET_SOCK_CREATE));
    }

    if (bpf::isAtLeastKernelVersion(4, 19, 0)) {
        RETURN_IF_NOT_OK(attachProgramToCgroup(
                "/sys/fs/bpf/netd_readonly/prog_block_bind4_block_port",
                cg_fd, BPF_CGROUP_INET4_BIND));
        RETURN_IF_NOT_OK(attachProgramToCgroup(
                "/sys/fs/bpf/netd_readonly/prog_block_bind6_block_port",
                cg_fd, BPF_CGROUP_INET6_BIND));

        // This should trivially pass, since we just attached up above,
        // but BPF_PROG_QUERY is only implemented on 4.19+ kernels.
        if (bpf::queryProgram(cg_fd, BPF_CGROUP_INET_EGRESS) <= 0) abort();
        if (bpf::queryProgram(cg_fd, BPF_CGROUP_INET_INGRESS) <= 0) abort();
        if (bpf::queryProgram(cg_fd, BPF_CGROUP_INET_SOCK_CREATE) <= 0) abort();
        if (bpf::queryProgram(cg_fd, BPF_CGROUP_INET4_BIND) <= 0) abort();
        if (bpf::queryProgram(cg_fd, BPF_CGROUP_INET6_BIND) <= 0) abort();
    }

    return netdutils::status::ok;
}

BpfHandler::BpfHandler()
    : mPerUidStatsEntriesLimit(PER_UID_STATS_ENTRIES_LIMIT),
      mTotalUidStatsEntriesLimit(TOTAL_UID_STATS_ENTRIES_LIMIT) {}

BpfHandler::BpfHandler(uint32_t perUidLimit, uint32_t totalLimit)
    : mPerUidStatsEntriesLimit(perUidLimit), mTotalUidStatsEntriesLimit(totalLimit) {}

Status BpfHandler::init(const char* cg2_path) {
    // Make sure BPF programs are loaded before doing anything
    android::bpf::waitForProgsLoaded();
    ALOGI("BPF programs are loaded");

    RETURN_IF_NOT_OK(initPrograms(cg2_path));
    RETURN_IF_NOT_OK(initMaps());

    return netdutils::status::ok;
}

Status BpfHandler::initMaps() {
    RETURN_IF_NOT_OK(mStatsMapA.init(STATS_MAP_A_PATH));
    RETURN_IF_NOT_OK(mStatsMapB.init(STATS_MAP_B_PATH));
    RETURN_IF_NOT_OK(mConfigurationMap.init(CONFIGURATION_MAP_PATH));
    RETURN_IF_NOT_OK(mUidPermissionMap.init(UID_PERMISSION_MAP_PATH));
    // initialized last so mCookieTagMap.isValid() implies everything else is valid too
    RETURN_IF_NOT_OK(mCookieTagMap.init(COOKIE_TAG_MAP_PATH));
    ALOGI("%s successfully", __func__);

    return netdutils::status::ok;
}

bool BpfHandler::hasUpdateDeviceStatsPermission(uid_t uid) {
    // This implementation is the same logic as method ActivityManager#checkComponentPermission.
    // It implies that the real uid can never be the same as PER_USER_RANGE.
    uint32_t appId = uid % PER_USER_RANGE;
    auto permission = mUidPermissionMap.readValue(appId);
    if (permission.ok() && (permission.value() & BPF_PERMISSION_UPDATE_DEVICE_STATS)) {
        return true;
    }
    return ((appId == AID_ROOT) || (appId == AID_SYSTEM) || (appId == AID_DNS));
}

int BpfHandler::tagSocket(int sockFd, uint32_t tag, uid_t chargeUid, uid_t realUid) {
    if (!mCookieTagMap.isValid()) return -EPERM;

    if (chargeUid != realUid && !hasUpdateDeviceStatsPermission(realUid)) return -EPERM;

    // Note that tagging the socket to AID_CLAT is only implemented in JNI ClatCoordinator.
    // The process is not allowed to tag socket to AID_CLAT via tagSocket() which would cause
    // process data usage accounting to be bypassed. Tagging AID_CLAT is used for avoiding counting
    // CLAT traffic data usage twice. See packages/modules/Connectivity/service/jni/
    // com_android_server_connectivity_ClatCoordinator.cpp
    if (chargeUid == AID_CLAT) return -EPERM;

    // The socket destroy listener only monitors on the group {INET_TCP, INET_UDP, INET6_TCP,
    // INET6_UDP}. Tagging listener unsupported socket causes that the tag can't be removed from
    // tag map automatically. Eventually, the tag map may run out of space because of dead tag
    // entries. Note that although tagSocket() of net client has already denied the family which
    // is neither AF_INET nor AF_INET6, the family validation is still added here just in case.
    // See tagSocket in system/netd/client/NetdClient.cpp and
    // TrafficController::makeSkDestroyListener in
    // packages/modules/Connectivity/service/native/TrafficController.cpp
    // TODO: remove this once the socket destroy listener can detect more types of socket destroy.
    int socketFamily;
    socklen_t familyLen = sizeof(socketFamily);
    if (getsockopt(sockFd, SOL_SOCKET, SO_DOMAIN, &socketFamily, &familyLen)) {
        ALOGE("Failed to getsockopt SO_DOMAIN: %s, fd: %d", strerror(errno), sockFd);
        return -errno;
    }
    if (socketFamily != AF_INET && socketFamily != AF_INET6) {
        ALOGE("Unsupported family: %d", socketFamily);
        return -EAFNOSUPPORT;
    }

    int socketProto;
    socklen_t protoLen = sizeof(socketProto);
    if (getsockopt(sockFd, SOL_SOCKET, SO_PROTOCOL, &socketProto, &protoLen)) {
        ALOGE("Failed to getsockopt SO_PROTOCOL: %s, fd: %d", strerror(errno), sockFd);
        return -errno;
    }
    if (socketProto != IPPROTO_UDP && socketProto != IPPROTO_TCP) {
        ALOGE("Unsupported protocol: %d", socketProto);
        return -EPROTONOSUPPORT;
    }

    uint64_t sock_cookie = getSocketCookie(sockFd);
    if (!sock_cookie) return -errno;

    UidTagValue newKey = {.uid = (uint32_t)chargeUid, .tag = tag};

    uint32_t totalEntryCount = 0;
    uint32_t perUidEntryCount = 0;
    // Now we go through the stats map and count how many entries are associated
    // with chargeUid. If the uid entry hit the limit for each chargeUid, we block
    // the request to prevent the map from overflow. Note though that it isn't really
    // safe here to iterate over the map since it might be modified by the system server,
    // which might toggle the live stats map and clean it.
    const auto countUidStatsEntries = [chargeUid, &totalEntryCount, &perUidEntryCount](
                                              const StatsKey& key,
                                              const BpfMapRO<StatsKey, StatsValue>&) {
        if (key.uid == chargeUid) {
            perUidEntryCount++;
        }
        totalEntryCount++;
        return base::Result<void>();
    };
    auto configuration = mConfigurationMap.readValue(CURRENT_STATS_MAP_CONFIGURATION_KEY);
    if (!configuration.ok()) {
        ALOGE("Failed to get current configuration: %s",
              strerror(configuration.error().code()));
        return -configuration.error().code();
    }
    if (configuration.value() != SELECT_MAP_A && configuration.value() != SELECT_MAP_B) {
        ALOGE("unknown configuration value: %d", configuration.value());
        return -EINVAL;
    }

    BpfMapRO<StatsKey, StatsValue>& currentMap =
            (configuration.value() == SELECT_MAP_A) ? mStatsMapA : mStatsMapB;
    base::Result<void> res = currentMap.iterate(countUidStatsEntries);
    if (!res.ok()) {
        ALOGE("Failed to count the stats entry in map: %s",
              strerror(res.error().code()));
        return -res.error().code();
    }

    if (totalEntryCount > mTotalUidStatsEntriesLimit ||
        perUidEntryCount > mPerUidStatsEntriesLimit) {
        ALOGE("Too many stats entries in the map, total count: %u, chargeUid(%u) count: %u,"
              " blocking tag request to prevent map overflow",
              totalEntryCount, chargeUid, perUidEntryCount);
        return -EMFILE;
    }
    // Update the tag information of a socket to the cookieUidMap. Use BPF_ANY
    // flag so it will insert a new entry to the map if that value doesn't exist
    // yet and update the tag if there is already a tag stored. Since the eBPF
    // program in kernel only read this map, and is protected by rcu read lock. It
    // should be fine to concurrently update the map while eBPF program is running.
    res = mCookieTagMap.writeValue(sock_cookie, newKey, BPF_ANY);
    if (!res.ok()) {
        ALOGE("Failed to tag the socket: %s", strerror(res.error().code()));
        return -res.error().code();
    }
    ALOGD("Socket with cookie %" PRIu64 " tagged successfully with tag %" PRIu32 " uid %u "
              "and real uid %u", sock_cookie, tag, chargeUid, realUid);
    return 0;
}

int BpfHandler::untagSocket(int sockFd) {
    uint64_t sock_cookie = getSocketCookie(sockFd);
    if (!sock_cookie) return -errno;

    if (!mCookieTagMap.isValid()) return -EPERM;
    base::Result<void> res = mCookieTagMap.deleteValue(sock_cookie);
    if (!res.ok()) {
        ALOGE("Failed to untag socket: %s", strerror(res.error().code()));
        return -res.error().code();
    }
    ALOGD("Socket with cookie %" PRIu64 " untagged successfully.", sock_cookie);
    return 0;
}

}  // namespace net
}  // namespace android
