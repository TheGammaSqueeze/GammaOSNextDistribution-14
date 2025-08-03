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
#define LOG_TAG "jniClatCoordinator"

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <linux/if_packet.h>
#include <linux/if_tun.h>
#include <linux/ioctl.h>
#include <log/log.h>
#include <nativehelper/JNIHelp.h>
#include <net/if.h>
#include <spawn.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/xattr.h>
#include <string>
#include <unistd.h>

#include <android-modules-utils/sdk_level.h>
#include <bpf/BpfMap.h>
#include <bpf/BpfUtils.h>
#include <netjniutils/netjniutils.h>
#include <private/android_filesystem_config.h>

#include "libclat/clatutils.h"
#include "nativehelper/scoped_utf_chars.h"

// Sync from system/netd/server/NetdConstants.h
#define __INT_STRLEN(i) sizeof(#i)
#define _INT_STRLEN(i) __INT_STRLEN(i)
#define INT32_STRLEN _INT_STRLEN(INT32_MIN)

#define DEVICEPREFIX "v4-"

namespace android {

static bool fatal = false;

#define ALOGF(s ...) do { ALOGE(s); fatal = true; } while(0)

enum verify { VERIFY_DIR, VERIFY_BIN, VERIFY_PROG, VERIFY_MAP_RO, VERIFY_MAP_RW };

static void verifyPerms(const char * const path,
                        const mode_t mode, const uid_t uid, const gid_t gid,
                        const char * const ctxt,
                        const verify vtype) {
    struct stat s = {};

    if (lstat(path, &s)) ALOGF("lstat '%s' errno=%d", path, errno);
    if (s.st_mode != mode) ALOGF("'%s' mode is 0%o != 0%o", path, s.st_mode, mode);
    if (s.st_uid != uid) ALOGF("'%s' uid is %d != %d", path, s.st_uid, uid);
    if (s.st_gid != gid) ALOGF("'%s' gid is %d != %d", path, s.st_gid, gid);

    char b[255] = {};
    int v = lgetxattr(path, "security.selinux", &b, sizeof(b));
    if (v < 0) ALOGF("lgetxattr '%s' errno=%d", path, errno);
    if (strncmp(ctxt, b, sizeof(b))) ALOGF("context of '%s' is '%s' != '%s'", path, b, ctxt);

    int fd = -1;

    switch (vtype) {
      case VERIFY_DIR: return;
      case VERIFY_BIN: return;
      case VERIFY_PROG:   fd = bpf::retrieveProgram(path); break;
      case VERIFY_MAP_RO: fd = bpf::mapRetrieveRO(path); break;
      case VERIFY_MAP_RW: fd = bpf::mapRetrieveRW(path); break;
    }

    if (fd < 0) ALOGF("bpf_obj_get '%s' failed, errno=%d", path, errno);

    if (fd >= 0) close(fd);
}

#undef ALOGF

bool isGsiImage() {
    // this implementation matches 2 other places in the codebase (same function name too)
    return !access("/system/system_ext/etc/init/init.gsi.rc", F_OK);
}

static const char* kClatdDir = "/apex/com.android.tethering/bin/for-system";
static const char* kClatdBin = "/apex/com.android.tethering/bin/for-system/clatd";

#define V(path, md, uid, gid, ctx, vtype) \
    verifyPerms((path), (md), AID_ ## uid, AID_ ## gid, "u:object_r:" ctx ":s0", VERIFY_ ## vtype)

static void verifyClatPerms() {
    // We might run as part of tests instead of as part of system server
    if (getuid() != AID_SYSTEM) return;

    // First verify the clatd directory and binary,
    // since this is built into the apex file system image,
    // failures here are 99% likely to be build problems.
    V(kClatdDir, S_IFDIR|0750, ROOT, SYSTEM, "system_file", DIR);
    V(kClatdBin, S_IFREG|S_ISUID|S_ISGID|0755, CLAT, CLAT, "clatd_exec", BIN);

    // Move on to verifying that the bpf programs and maps are as expected.
    // This relies on the kernel and bpfloader.

    // Clat BPF was only mainlined during T.
    if (!modules::sdklevel::IsAtLeastT()) return;

    V("/sys/fs/bpf", S_IFDIR|S_ISVTX|0777, ROOT, ROOT, "fs_bpf", DIR);
    V("/sys/fs/bpf/net_shared", S_IFDIR|S_ISVTX|0777, ROOT, ROOT, "fs_bpf_net_shared", DIR);

    // pre-U we do not have selinux privs to getattr on bpf maps/progs
    // so while the below *should* be as listed, we have no way to actually verify
    if (!modules::sdklevel::IsAtLeastU()) return;

#define V2(path, md, vtype) \
    V("/sys/fs/bpf/net_shared/" path, (md), ROOT, SYSTEM, "fs_bpf_net_shared", vtype)

    V2("prog_clatd_schedcls_egress4_clat_rawip",  S_IFREG|0440, PROG);
    V2("prog_clatd_schedcls_ingress6_clat_rawip", S_IFREG|0440, PROG);
    V2("prog_clatd_schedcls_ingress6_clat_ether", S_IFREG|0440, PROG);
    V2("map_clatd_clat_egress4_map",              S_IFREG|0660, MAP_RW);
    V2("map_clatd_clat_ingress6_map",             S_IFREG|0660, MAP_RW);

#undef V2

    // HACK: Some old vendor kernels lack ~5.10 backport of 'bpffs selinux genfscon' support.
    // This is *NOT* supported, but let's allow, at least for now, U+ GSI to boot on them.
    // (without this hack pixel5 R vendor + U gsi breaks)
    if (isGsiImage() && !bpf::isAtLeastKernelVersion(5, 10, 0)) {
        ALOGE("GSI with *BAD* pre-5.10 kernel lacking bpffs selinux genfscon support.");
        return;
    }

    if (fatal) abort();
}

#undef V

static void throwIOException(JNIEnv* env, const char* msg, int error) {
    jniThrowExceptionFmt(env, "java/io/IOException", "%s: %s", msg, strerror(error));
}

jstring com_android_server_connectivity_ClatCoordinator_selectIpv4Address(JNIEnv* env,
                                                                          jclass clazz,
                                                                          jstring v4addr,
                                                                          jint prefixlen) {
    ScopedUtfChars address(env, v4addr);
    in_addr ip;
    if (inet_pton(AF_INET, address.c_str(), &ip) != 1) {
        throwIOException(env, "invalid address", EINVAL);
        return nullptr;
    }

    // Pick an IPv4 address.
    // TODO: this picks the address based on other addresses that are assigned to interfaces, but
    // the address is only actually assigned to an interface once clatd starts up. So we could end
    // up with two clatd instances with the same IPv4 address.
    // Stop doing this and instead pick a free one from the kV4Addr pool.
    in_addr v4 = {net::clat::selectIpv4Address(ip, prefixlen)};
    if (v4.s_addr == INADDR_NONE) {
        jniThrowExceptionFmt(env, "java/io/IOException", "No free IPv4 address in %s/%d",
                             address.c_str(), prefixlen);
        return nullptr;
    }

    char addrstr[INET_ADDRSTRLEN];
    if (!inet_ntop(AF_INET, (void*)&v4, addrstr, sizeof(addrstr))) {
        throwIOException(env, "invalid address", EADDRNOTAVAIL);
        return nullptr;
    }
    return env->NewStringUTF(addrstr);
}

// Picks a random interface ID that is checksum neutral with the IPv4 address and the NAT64 prefix.
jstring com_android_server_connectivity_ClatCoordinator_generateIpv6Address(
        JNIEnv* env, jclass clazz, jstring ifaceStr, jstring v4Str, jstring prefix64Str,
        jint mark) {
    ScopedUtfChars iface(env, ifaceStr);
    ScopedUtfChars addr4(env, v4Str);
    ScopedUtfChars prefix64(env, prefix64Str);

    if (iface.c_str() == nullptr) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid null interface name");
        return nullptr;
    }

    in_addr v4;
    if (inet_pton(AF_INET, addr4.c_str(), &v4) != 1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid clat v4 address %s",
                             addr4.c_str());
        return nullptr;
    }

    in6_addr nat64Prefix;
    if (inet_pton(AF_INET6, prefix64.c_str(), &nat64Prefix) != 1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid prefix %s", prefix64.c_str());
        return nullptr;
    }

    in6_addr v6;
    if (net::clat::generateIpv6Address(iface.c_str(), v4, nat64Prefix, &v6, mark)) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                             "Unable to find global source address on %s for %s", iface.c_str(),
                             prefix64.c_str());
        return nullptr;
    }

    char addrstr[INET6_ADDRSTRLEN];
    if (!inet_ntop(AF_INET6, (void*)&v6, addrstr, sizeof(addrstr))) {
        throwIOException(env, "invalid address", EADDRNOTAVAIL);
        return nullptr;
    }
    return env->NewStringUTF(addrstr);
}

static jint com_android_server_connectivity_ClatCoordinator_createTunInterface(JNIEnv* env,
                                                                               jclass clazz,
                                                                               jstring tuniface) {
    ScopedUtfChars v4interface(env, tuniface);

    // open the tun device in non blocking mode as required by clatd
    jint fd = open("/dev/net/tun", O_RDWR | O_NONBLOCK | O_CLOEXEC);
    if (fd == -1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "open tun device failed (%s)",
                             strerror(errno));
        return -1;
    }

    struct ifreq ifr = {
            .ifr_flags = static_cast<short>(IFF_TUN | IFF_TUN_EXCL),
    };
    strlcpy(ifr.ifr_name, v4interface.c_str(), sizeof(ifr.ifr_name));

    if (ioctl(fd, TUNSETIFF, &ifr, sizeof(ifr))) {
        close(fd);
        jniThrowExceptionFmt(env, "java/io/IOException", "ioctl(TUNSETIFF) failed (%s)",
                             strerror(errno));
        return -1;
    }

    return fd;
}

static jint com_android_server_connectivity_ClatCoordinator_detectMtu(JNIEnv* env, jclass clazz,
                                                                      jstring platSubnet,
                                                                      jint plat_suffix, jint mark) {
    ScopedUtfChars platSubnetStr(env, platSubnet);

    in6_addr plat_subnet;
    if (inet_pton(AF_INET6, platSubnetStr.c_str(), &plat_subnet) != 1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid plat prefix address %s",
                             platSubnetStr.c_str());
        return -1;
    }

    int ret = net::clat::detect_mtu(&plat_subnet, plat_suffix, mark);
    if (ret < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "detect mtu failed: %s", strerror(-ret));
        return -1;
    }

    return ret;
}

static jint com_android_server_connectivity_ClatCoordinator_openPacketSocket(JNIEnv* env,
                                                                              jclass clazz) {
    // Will eventually be bound to htons(ETH_P_IPV6) protocol,
    // but only after appropriate bpf filter is attached.
    const int sock = socket(AF_PACKET, SOCK_RAW | SOCK_CLOEXEC, 0);
    if (sock < 0) {
        throwIOException(env, "packet socket failed", errno);
        return -1;
    }
    const int on = 1;
    // enable tpacket_auxdata cmsg delivery, which includes L2 header length
    if (setsockopt(sock, SOL_PACKET, PACKET_AUXDATA, &on, sizeof(on))) {
        throwIOException(env, "packet socket auxdata enablement failed", errno);
        close(sock);
        return -1;
    }
    // needed for virtio_net_hdr prepending, which includes checksum metadata
    if (setsockopt(sock, SOL_PACKET, PACKET_VNET_HDR, &on, sizeof(on))) {
        throwIOException(env, "packet socket vnet_hdr enablement failed", errno);
        close(sock);
        return -1;
    }
    return sock;
}

static jint com_android_server_connectivity_ClatCoordinator_openRawSocket6(JNIEnv* env,
                                                                           jclass clazz,
                                                                           jint mark) {
    int sock = socket(AF_INET6, SOCK_RAW | SOCK_NONBLOCK | SOCK_CLOEXEC, IPPROTO_RAW);
    if (sock < 0) {
        throwIOException(env, "raw socket failed", errno);
        return -1;
    }

    // TODO: check the mark validation
    if (setsockopt(sock, SOL_SOCKET, SO_MARK, &mark, sizeof(mark)) < 0) {
        throwIOException(env, "could not set mark on raw socket", errno);
        close(sock);
        return -1;
    }

    return sock;
}

static void com_android_server_connectivity_ClatCoordinator_addAnycastSetsockopt(
        JNIEnv* env, jclass clazz, jobject javaFd, jstring addr6, jint ifindex) {
    int sock = netjniutils::GetNativeFileDescriptor(env, javaFd);
    if (sock < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid file descriptor");
        return;
    }

    ScopedUtfChars addrStr(env, addr6);

    in6_addr addr;
    if (inet_pton(AF_INET6, addrStr.c_str(), &addr) != 1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid IPv6 address %s",
                             addrStr.c_str());
        return;
    }

    struct ipv6_mreq mreq = {addr, ifindex};
    int ret = setsockopt(sock, SOL_IPV6, IPV6_JOIN_ANYCAST, &mreq, sizeof(mreq));
    if (ret) {
        jniThrowExceptionFmt(env, "java/io/IOException", "setsockopt IPV6_JOIN_ANYCAST failed: %s",
                             strerror(errno));
        return;
    }
}

static void com_android_server_connectivity_ClatCoordinator_configurePacketSocket(
        JNIEnv* env, jclass clazz, jobject javaFd, jstring addr6, jint ifindex) {
    ScopedUtfChars addrStr(env, addr6);

    int sock = netjniutils::GetNativeFileDescriptor(env, javaFd);
    if (sock < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid file descriptor");
        return;
    }

    in6_addr addr;
    if (inet_pton(AF_INET6, addrStr.c_str(), &addr) != 1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid IPv6 address %s",
                             addrStr.c_str());
        return;
    }

    int ret = net::clat::configure_packet_socket(sock, &addr, ifindex);
    if (ret < 0) {
        throwIOException(env, "configure packet socket failed", -ret);
        return;
    }
}

static jint com_android_server_connectivity_ClatCoordinator_startClatd(
        JNIEnv* env, jclass clazz, jobject tunJavaFd, jobject readSockJavaFd,
        jobject writeSockJavaFd, jstring iface, jstring pfx96, jstring v4, jstring v6) {
    ScopedUtfChars ifaceStr(env, iface);
    ScopedUtfChars pfx96Str(env, pfx96);
    ScopedUtfChars v4Str(env, v4);
    ScopedUtfChars v6Str(env, v6);

    int tunFd = netjniutils::GetNativeFileDescriptor(env, tunJavaFd);
    if (tunFd < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid tun file descriptor");
        return -1;
    }

    int readSock = netjniutils::GetNativeFileDescriptor(env, readSockJavaFd);
    if (readSock < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid read socket");
        return -1;
    }

    int writeSock = netjniutils::GetNativeFileDescriptor(env, writeSockJavaFd);
    if (writeSock < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid write socket");
        return -1;
    }

    // 1. these are the FD we'll pass to clatd on the cli, so need it as a string
    char tunFdStr[INT32_STRLEN];
    char sockReadStr[INT32_STRLEN];
    char sockWriteStr[INT32_STRLEN];
    snprintf(tunFdStr, sizeof(tunFdStr), "%d", tunFd);
    snprintf(sockReadStr, sizeof(sockReadStr), "%d", readSock);
    snprintf(sockWriteStr, sizeof(sockWriteStr), "%d", writeSock);

    // 2. we're going to use this as argv[0] to clatd to make ps output more useful
    std::string progname("clatd-");
    progname += ifaceStr.c_str();

    // clang-format off
    const char* args[] = {progname.c_str(),
                          "-i", ifaceStr.c_str(),
                          "-p", pfx96Str.c_str(),
                          "-4", v4Str.c_str(),
                          "-6", v6Str.c_str(),
                          "-t", tunFdStr,
                          "-r", sockReadStr,
                          "-w", sockWriteStr,
                          nullptr};
    // clang-format on

    // 3. register vfork requirement
    posix_spawnattr_t attr;
    if (int ret = posix_spawnattr_init(&attr)) {
        throwIOException(env, "posix_spawnattr_init failed", ret);
        return -1;
    }

    // TODO: use android::base::ScopeGuard.
    if (int ret = posix_spawnattr_setflags(&attr, POSIX_SPAWN_USEVFORK
                                           | POSIX_SPAWN_CLOEXEC_DEFAULT)) {
        posix_spawnattr_destroy(&attr);
        throwIOException(env, "posix_spawnattr_setflags failed", ret);
        return -1;
    }

    // 4. register dup2() action: this is what 'clears' the CLOEXEC flag
    // on the tun fd that we want the child clatd process to inherit
    // (this will happen after the vfork, and before the execve).
    // Note that even though dup2(2) is a no-op if fd == new_fd but O_CLOEXEC flag will be removed.
    // See implementation of bionic's posix_spawn_file_actions_adddup2().
    posix_spawn_file_actions_t fa;
    if (int ret = posix_spawn_file_actions_init(&fa)) {
        posix_spawnattr_destroy(&attr);
        throwIOException(env, "posix_spawn_file_actions_init failed", ret);
        return -1;
    }

    if (int ret = posix_spawn_file_actions_adddup2(&fa, tunFd, tunFd)) {
        posix_spawnattr_destroy(&attr);
        posix_spawn_file_actions_destroy(&fa);
        throwIOException(env, "posix_spawn_file_actions_adddup2 for tun fd failed", ret);
        return -1;
    }
    if (int ret = posix_spawn_file_actions_adddup2(&fa, readSock, readSock)) {
        posix_spawnattr_destroy(&attr);
        posix_spawn_file_actions_destroy(&fa);
        throwIOException(env, "posix_spawn_file_actions_adddup2 for read socket failed", ret);
        return -1;
    }
    if (int ret = posix_spawn_file_actions_adddup2(&fa, writeSock, writeSock)) {
        posix_spawnattr_destroy(&attr);
        posix_spawn_file_actions_destroy(&fa);
        throwIOException(env, "posix_spawn_file_actions_adddup2 for write socket failed", ret);
        return -1;
    }

    // 5. actually perform vfork/dup2/execve
    pid_t pid;
    if (int ret = posix_spawn(&pid, kClatdBin, &fa, &attr, (char* const*)args, nullptr)) {
        posix_spawnattr_destroy(&attr);
        posix_spawn_file_actions_destroy(&fa);
        throwIOException(env, "posix_spawn failed", ret);
        return -1;
    }

    posix_spawnattr_destroy(&attr);
    posix_spawn_file_actions_destroy(&fa);

    return pid;
}

// Stop clatd process. SIGTERM with timeout first, if fail, SIGKILL.
// See stopProcess() in system/netd/server/NetdConstants.cpp.
// TODO: have a function stopProcess(int pid, const char *name) in common location and call it.
static constexpr int WAITPID_ATTEMPTS = 50;
static constexpr int WAITPID_RETRY_INTERVAL_US = 100000;

static void com_android_server_connectivity_ClatCoordinator_stopClatd(JNIEnv* env, jclass clazz,
                                                                      jint pid) {
    if (pid <= 0) {
        jniThrowExceptionFmt(env, "java/lang/IllegalArgumentException", "Invalid pid");
        return;
    }

    int err = kill(pid, SIGTERM);
    if (err) err = errno;
    if (err == ESRCH) {
        ALOGE("clatd child process %d unexpectedly disappeared", pid);
        return;
    }
    if (err) {
        ALOGE("Error killing clatd child process %d: %s", pid, strerror(err));
    }
    int status = 0;
    int ret = 0;
    for (int count = 0; ret == 0 && count < WAITPID_ATTEMPTS; count++) {
        usleep(WAITPID_RETRY_INTERVAL_US);
        ret = waitpid(pid, &status, WNOHANG);
    }
    if (ret == 0) {
        ALOGE("Failed to SIGTERM clatd pid=%d, try SIGKILL", pid);
        // TODO: fix that kill failed or waitpid doesn't return.
        if (kill(pid, SIGKILL)) {
            ALOGE("Failed to SIGKILL clatd pid=%d: %s", pid, strerror(errno));
        }
        ret = waitpid(pid, &status, 0);
    }
    if (ret == -1) {
        ALOGE("Error waiting for clatd child process %d: %s", pid, strerror(errno));
    } else {
        ALOGD("clatd process %d terminated status=%d", pid, status);
    }
}

static jlong com_android_server_connectivity_ClatCoordinator_getSocketCookie(
        JNIEnv* env, jclass clazz, jobject sockJavaFd) {
    int sockFd = netjniutils::GetNativeFileDescriptor(env, sockJavaFd);
    if (sockFd < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid socket file descriptor");
        return -1;
    }

    uint64_t sock_cookie = bpf::getSocketCookie(sockFd);
    if (!sock_cookie) {
        throwIOException(env, "get socket cookie failed", errno);
        return -1;
    }

    ALOGI("Get cookie %" PRIu64 " for socket fd %d", sock_cookie, sockFd);
    return static_cast<jlong>(sock_cookie);
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
        /* name, signature, funcPtr */
        {"native_selectIpv4Address", "(Ljava/lang/String;I)Ljava/lang/String;",
         (void*)com_android_server_connectivity_ClatCoordinator_selectIpv4Address},
        {"native_generateIpv6Address",
         "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;",
         (void*)com_android_server_connectivity_ClatCoordinator_generateIpv6Address},
        {"native_createTunInterface", "(Ljava/lang/String;)I",
         (void*)com_android_server_connectivity_ClatCoordinator_createTunInterface},
        {"native_detectMtu", "(Ljava/lang/String;II)I",
         (void*)com_android_server_connectivity_ClatCoordinator_detectMtu},
        {"native_openPacketSocket", "()I",
         (void*)com_android_server_connectivity_ClatCoordinator_openPacketSocket},
        {"native_openRawSocket6", "(I)I",
         (void*)com_android_server_connectivity_ClatCoordinator_openRawSocket6},
        {"native_addAnycastSetsockopt", "(Ljava/io/FileDescriptor;Ljava/lang/String;I)V",
         (void*)com_android_server_connectivity_ClatCoordinator_addAnycastSetsockopt},
        {"native_configurePacketSocket", "(Ljava/io/FileDescriptor;Ljava/lang/String;I)V",
         (void*)com_android_server_connectivity_ClatCoordinator_configurePacketSocket},
        {"native_startClatd",
         "(Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;Ljava/lang/"
         "String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I",
         (void*)com_android_server_connectivity_ClatCoordinator_startClatd},
        {"native_stopClatd", "(I)V",
         (void*)com_android_server_connectivity_ClatCoordinator_stopClatd},
        {"native_getSocketCookie", "(Ljava/io/FileDescriptor;)J",
         (void*)com_android_server_connectivity_ClatCoordinator_getSocketCookie},
};

int register_com_android_server_connectivity_ClatCoordinator(JNIEnv* env) {
    verifyClatPerms();
    return jniRegisterNativeMethods(env,
            "android/net/connectivity/com/android/server/connectivity/ClatCoordinator",
            gMethods, NELEM(gMethods));
}

};  // namespace android
