/*
 * Copyright (C) 2026 GammaOS
 *
 * NanoBridge client -- compiled into libgammaos_nano_bridge.so, which
 * the drastic-app shim (com.dsemu.drastic.nano, under app_process)
 * loads via System.loadLibrary("gammaos_nano_bridge"). Exports the
 * JNI-mangled entry points the shim's gammaos.drastic.NanoBridge
 * Java class binds to.
 *
 * Each call opens (or reuses) a connection to the gammaos-nano
 * bridge socket, sends an opcode + optional payload, reads the
 * reply, and returns. See NanoBridgeServer.cpp for the protocol.
 *
 * Skeleton: all calls currently log and return safe defaults. The
 * real opcode dispatch is wired once the server loop is fleshed
 * out and we have a live shim to exercise it against.
 */

#define LOG_TAG "GammaOSNano.BridgeCli"

#include "NanoBridge.h"

#include <errno.h>
#include <mutex>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include <system/window.h>
#include <utils/Log.h>

namespace {

constexpr const char* kSocketPath = "/dev/socket/gammaos_nano_bridge";

std::mutex sConnMutex;
int sConnFd = -1;

int ensureConnected() {
    std::lock_guard<std::mutex> lk(sConnMutex);
    if (sConnFd >= 0) return sConnFd;

    int fd = ::socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) {
        ALOGE("NanoBridgeClient: socket() failed: %s", strerror(errno));
        return -1;
    }
    struct sockaddr_un addr = {};
    addr.sun_family = AF_UNIX;
    const size_t pathLen = strlen(kSocketPath);
    if (pathLen >= sizeof(addr.sun_path)) {
        ALOGE("NanoBridgeClient: socket path too long");
        ::close(fd);
        return -1;
    }
    memcpy(addr.sun_path, kSocketPath, pathLen + 1);

    if (::connect(fd, reinterpret_cast<sockaddr*>(&addr),
                  sizeof(addr)) < 0) {
        ALOGE("NanoBridgeClient: connect(%s) failed: %s", kSocketPath,
              strerror(errno));
        ::close(fd);
        return -1;
    }
    sConnFd = fd;
    ALOGI("NanoBridgeClient: connected (fd=%d)", sConnFd);
    return sConnFd;
}

} // namespace

namespace android { namespace nano_bridge {
    ANativeWindow* createNanoAnativeWindow(int bridgeFd);
}}

extern "C" {

JNIEXPORT jlong JNICALL
Java_gammaos_nano_NanoBridge_getCurrentAhbNativeWindow(
        JNIEnv* /*env*/, jclass /*cls*/) {
    int fd = ensureConnected();
    if (fd < 0) {
        ALOGE("NanoBridgeClient: getCurrentAhbNativeWindow: no conn");
        return 0;
    }
    ANativeWindow* w = android::nano_bridge::createNanoAnativeWindow(fd);
    if (!w) {
        ALOGE("NanoBridgeClient: createNanoAnativeWindow returned null");
        return 0;
    }
    ALOGI("NanoBridgeClient: returning ANativeWindow=%p", w);
    return reinterpret_cast<jlong>(w);
}

JNIEXPORT jboolean JNICALL
Java_gammaos_nano_NanoBridge_menuChordRequested(
        JNIEnv* /*env*/, jclass /*cls*/) {
    int fd = ensureConnected();
    if (fd < 0) return JNI_FALSE;
    // TODO: send opcode 0x02 MENU_CHORD_POLL, read 1 byte.
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_gammaos_nano_NanoBridge_notifyAppEvent(
        JNIEnv* env, jclass /*cls*/, jstring type, jstring payload) {
    int fd = ensureConnected();
    if (fd < 0) return;
    const char* t = env->GetStringUTFChars(type, nullptr);
    const char* p = payload
            ? env->GetStringUTFChars(payload, nullptr) : nullptr;
    ALOGI("NanoBridgeClient: notifyAppEvent type=%s payload=%s [stub]",
          t ? t : "(null)", p ? p : "(null)");
    if (t) env->ReleaseStringUTFChars(type, t);
    if (p) env->ReleaseStringUTFChars(payload, p);
    // TODO: opcode with length-prefixed type + payload utf-8.
}

JNIEXPORT void JNICALL
Java_gammaos_nano_NanoBridge_notifyQuit(
        JNIEnv* /*env*/, jclass /*cls*/) {
    int fd = ensureConnected();
    if (fd < 0) return;
    ALOGI("NanoBridgeClient: notifyQuit [stub]");
    // TODO: opcode to close connection cleanly.
}

} // extern "C"
