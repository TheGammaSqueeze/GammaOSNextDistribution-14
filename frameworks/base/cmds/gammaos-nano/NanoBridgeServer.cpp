/*
 * Copyright (C) 2026 GammaOS
 *
 * NanoBridge server -- the half that runs inside gammaos-nano and
 * accepts connections from the drastic-app shim running under
 * app_process. Skeleton: socket listener + handler dispatch. The
 * actual handlers are stubbed until the shim is ready for the
 * handshake protocol.
 *
 * Protocol (draft, may evolve as we prove it on-device):
 *
 *   Request: 4-byte little-endian opcode, optional payload
 *     0x01 GET_AHB_NATIVE_WINDOW  -> server returns inline struct +
 *                                     SCM_RIGHTS dma-buf fd
 *     0x02 MENU_CHORD_POLL        -> server returns 1-byte boolean
 *                                     (clears latch on read)
 *     0x03 NOTIFY_ROM_LOADED      -> payload: 2-byte length + utf-8
 *                                     absolute path (no reply)
 *     0x04 NOTIFY_QUIT            -> server tears down ring slot
 *     0x05 NOTIFY_SAVE_STATE      -> payload: 4-byte slot (no reply)
 *     0x06 NOTIFY_LOAD_STATE      -> payload: 4-byte slot (no reply)
 *
 * One-connection-at-a-time. The shim runs all bridge calls from its
 * renderer thread serialized.
 */

#define LOG_TAG "GammaOSNano.BridgeSrv"

#include "NanoBridge.h"

#include <atomic>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <android/hardware_buffer.h>

#include <errno.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include <cutils/sockets.h>
#include <utils/Log.h>

namespace android {
namespace nano_bridge {

namespace {

constexpr const char* kSocketPath = "/dev/socket/gammaos_nano_bridge";

// Keep in sync with NanoAnw.cpp opcode enum.
enum : uint32_t {
    OP_HELLO    = 0x01,
    OP_DEQUEUE  = 0x02,
    OP_QUEUE    = 0x03,
    OP_CANCEL   = 0x04,
    OP_SET_SWAP = 0x05,
    OP_QUIT     = 0x06,
};

struct RingInfo {
    uint32_t ringDepth;
    uint32_t width;
    uint32_t height;
    uint32_t stride;
    uint32_t format;
    uint32_t usage_lo;
    uint32_t usage_hi;
};

struct DequeueReply {
    int32_t slotIdx;
    int32_t fenceFd;
};

struct QueuePayload {
    uint32_t opcode;
    int32_t  slotIdx;
    int32_t  fenceFd;
};

// Default ring dimensions. Panel-independent -- we allocate the ring
// at the DS native resolution; the compositor scales to panel at
// scanout. Overridable via properties if needed.
// Dual-DS portrait canvas: 640 wide x 960 tall. Drastic renders
// both screens stacked (top DS on top half, bottom DS on bottom
// half). Nano scans out the canvas to the two physical 640x480
// panels by halves.
constexpr uint32_t kDefaultWidth = 640;
constexpr uint32_t kDefaultHeight = 960;
constexpr uint32_t kDefaultRingDepth = 4;

std::atomic<bool> sServerRunning{false};
std::atomic<bool> sMenuChordLatch{false};
std::mutex sRomPathMutex;
std::string sCurrentRomPath;
std::thread sServerThread;
int sListenFd = -1;

struct SlotRec {
    AHardwareBuffer* ahb = nullptr;
    // DRM PRIME: when nano has DRM master, each AHB is imported as
    // scanout framebuffers (top half + bottom half of the dual-DS
    // canvas) at handshake time so OP_QUEUE can drmModePageFlip
    // straight to them. topFbId=0 means the import failed (HWC/SF
    // still holds master, or the driver rejected the dma-buf); the
    // flip path silently no-ops in that case.
    ShimFb drmFb;
    // "flipping" bit lives on the session mutex path in v1; the
    // atomic will return in v2 when the compositor callback path
    // needs lock-free signalling.
};

struct BridgeSession {
    std::vector<SlotRec> slots;
    std::mutex mtx;
    // Round-robin dequeue cursor. In the full implementation this
    // blocks until the compositor signals completion; v1 just
    // rotates slots sequentially.
    uint32_t nextSlot = 0;
};

bool writeAll(int fd, const void* buf, size_t len) {
    const uint8_t* p = static_cast<const uint8_t*>(buf);
    while (len > 0) {
        ssize_t n = ::send(fd, p, len, MSG_NOSIGNAL);
        if (n < 0) { if (errno == EINTR) continue; return false; }
        p += n; len -= n;
    }
    return true;
}

bool readAll(int fd, void* buf, size_t len) {
    uint8_t* p = static_cast<uint8_t*>(buf);
    while (len > 0) {
        ssize_t n = ::recv(fd, p, len, 0);
        if (n < 0) { if (errno == EINTR) continue; return false; }
        if (n == 0) return false;
        p += n; len -= n;
    }
    return true;
}

// Allocate the AHB ring for a new session.
bool setupRing(BridgeSession& sess) {
    sess.slots.resize(kDefaultRingDepth);
    AHardwareBuffer_Desc d = {};
    d.width = kDefaultWidth;
    d.height = kDefaultHeight;
    d.layers = 1;
    d.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    // Usage mask matches NanoMenu's own ring (NanoMenu.cpp ~2505) so
    // Mali gralloc allocates the AHB in LINEAR layout. Without
    // CPU_READ_OFTEN the 640x960 RGBA allocation hits the Mali AFBC
    // threshold and comes out compressed, and the RK3566 VOP2 rejects
    // that layout via plain ADDFB2 with EINVAL. COMPOSER_OVERLAY alone
    // is not sufficient at this surface size. GPU_FRAMEBUFFER (vs
    // GPU_COLOR_OUTPUT) is what nano's working ring uses; keep them
    // identical so both share the same gralloc path.
    d.usage = AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER
            | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE
            | AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY
            | AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN;
    for (uint32_t i = 0; i < kDefaultRingDepth; i++) {
        int rc = AHardwareBuffer_allocate(&d, &sess.slots[i].ahb);
        if (rc != 0) {
            ALOGE("NanoBridgeServer: AHB alloc %u failed (rc=%d)",
                  i, rc);
            return false;
        }
        AHardwareBuffer_Desc got = {};
        AHardwareBuffer_describe(sess.slots[i].ahb, &got);
        ALOGI("NanoBridgeServer: slot %u allocated %ux%u stride=%u "
              "format=0x%x usage=0x%llx",
              i, got.width, got.height, got.stride, got.format,
              (unsigned long long)got.usage);
        // Best-effort DRM scanout import. If it fails we keep the
        // slot usable for the shim handshake (so bringup logs still
        // work), but OP_QUEUE flips become no-ops for that slot.
        if (!importAhbAsFb(sess.slots[i].ahb, &sess.slots[i].drmFb)) {
            ALOGW("NanoBridgeServer: slot %u AHB -> DRM fb import "
                  "failed; scanout disabled for this slot", i);
        }
    }
    ALOGI("NanoBridgeServer: allocated %u AHBs (%ux%u RGBA8888), "
          "topFb[0..3]=%u/%u/%u/%u bottomFb[0..3]=%u/%u/%u/%u",
          kDefaultRingDepth, kDefaultWidth, kDefaultHeight,
          sess.slots.size() > 0 ? sess.slots[0].drmFb.topFbId : 0,
          sess.slots.size() > 1 ? sess.slots[1].drmFb.topFbId : 0,
          sess.slots.size() > 2 ? sess.slots[2].drmFb.topFbId : 0,
          sess.slots.size() > 3 ? sess.slots[3].drmFb.topFbId : 0,
          sess.slots.size() > 0 ? sess.slots[0].drmFb.bottomFbId : 0,
          sess.slots.size() > 1 ? sess.slots[1].drmFb.bottomFbId : 0,
          sess.slots.size() > 2 ? sess.slots[2].drmFb.bottomFbId : 0,
          sess.slots.size() > 3 ? sess.slots[3].drmFb.bottomFbId : 0);
    return true;
}

// Handle OP_HELLO: send RingInfo + all N AHB handles.
bool handleHello(int clientFd, BridgeSession& sess) {
    if (!setupRing(sess)) return false;

    AHardwareBuffer_Desc d = {};
    AHardwareBuffer_describe(sess.slots[0].ahb, &d);

    RingInfo info = {};
    info.ringDepth = (uint32_t)sess.slots.size();
    info.width = d.width;
    info.height = d.height;
    info.stride = d.stride;
    info.format = d.format;
    info.usage_lo = (uint32_t)(d.usage & 0xffffffffu);
    info.usage_hi = (uint32_t)(d.usage >> 32);
    if (!writeAll(clientFd, &info, sizeof(info))) {
        ALOGE("NanoBridgeServer: failed to send RingInfo");
        return false;
    }

    for (uint32_t i = 0; i < info.ringDepth; i++) {
        int rc = AHardwareBuffer_sendHandleToUnixSocket(
                sess.slots[i].ahb, clientFd);
        if (rc != 0) {
            ALOGE("NanoBridgeServer: sendHandle slot %u failed "
                  "(rc=%d)", i, rc);
            return false;
        }
    }
    ALOGI("NanoBridgeServer: HELLO reply sent (%u slots)",
          info.ringDepth);
    return true;
}

// Handle OP_DEQUEUE: return next free slot index. v1 rotates
// blindly; v2 will wait on compositor completion for real pacing.
bool handleDequeue(int clientFd, BridgeSession& sess) {
    std::lock_guard<std::mutex> lk(sess.mtx);
    DequeueReply rep = {};
    rep.slotIdx = (int32_t)sess.nextSlot;
    rep.fenceFd = -1;
    sess.nextSlot = (sess.nextSlot + 1) % sess.slots.size();
    return writeAll(clientFd, &rep, sizeof(rep));
}

// Consume an SCM_RIGHTS fence fd (if any) alongside the QueuePayload
// the client just wrote. For v1 we discard the fence; v2 will wait
// on it before calling drmFlipRingSlot.
int readFenceIfAny(int sockFd) {
    // The client already wrote the 12-byte QueuePayload (via sendmsg
    // that attached the fd). We've read the payload via readAll; the
    // fd was attached as an SCM_RIGHTS cmsg on the same sendmsg. On
    // AF_UNIX stream sockets, cmsg arrives with the byte stream -- if
    // the client used writeAll for the payload (as in v1), no cmsg is
    // attached. We just return -1 in that case.
    (void)sockFd;
    return -1;
}

void serverLoop() {
    ALOGI("NanoBridgeServer: listen loop entered (fd=%d)", sListenFd);
    while (sServerRunning.load(std::memory_order_acquire)) {
        int client = ::accept(sListenFd, nullptr, nullptr);
        if (client < 0) {
            if (errno == EINTR) continue;
            ALOGW("NanoBridgeServer: accept failed (%s), backing off",
                  strerror(errno));
            continue;
        }
        ALOGI("NanoBridgeServer: shim connected (client_fd=%d)",
              client);

        BridgeSession sess;
        bool fatal = false;
        while (!fatal && sServerRunning.load(std::memory_order_acquire)) {
            uint32_t op = 0;
            if (!readAll(client, &op, sizeof(op))) break;
            switch (op) {
                case OP_HELLO:
                    if (!handleHello(client, sess)) fatal = true;
                    break;
                case OP_DEQUEUE:
                    if (!handleDequeue(client, sess)) fatal = true;
                    break;
                case OP_QUEUE: {
                    QueuePayload qp = {};
                    qp.opcode = op;
                    if (!readAll(client, &qp.slotIdx,
                                 sizeof(qp) - sizeof(qp.opcode))) {
                        fatal = true; break;
                    }
                    int fence = (qp.fenceFd >= 0) ? readFenceIfAny(client) : -1;
                    // Fire-and-forget DRM page flip to the slot's
                    // pre-imported scanout framebuffer. Fence
                    // handling stays TODO (v1 accepts potential
                    // tearing; shim's Mali driver usually finishes
                    // the draw well before the server reads the
                    // opcode because the socket round-trip is ~50 us
                    // and a typical Mali frame is ~4 ms).
                    if (fence >= 0) ::close(fence);
                    if (qp.slotIdx >= 0 &&
                        qp.slotIdx < (int32_t)sess.slots.size()) {
                        const ShimFb& sfb = sess.slots[qp.slotIdx].drmFb;
                        if (sfb.topFbId != 0) {
                            flipShimFb(sfb);
                        } else {
                            ALOGW("NanoBridgeServer: OP_QUEUE slot=%d "
                                  "has no drmFb (scanout disabled)",
                                  qp.slotIdx);
                        }
                    } else {
                        ALOGW("NanoBridgeServer: OP_QUEUE bogus slot=%d "
                              "(ring depth=%zu)", qp.slotIdx,
                              sess.slots.size());
                    }
                    // Periodic heartbeat so we can confirm the
                    // server is still consuming without flooding
                    // logcat at 60 Hz.
                    static std::atomic<uint32_t> sQueueCount{0};
                    uint32_t n = sQueueCount.fetch_add(1,
                            std::memory_order_relaxed) + 1;
                    if (n <= 5 || (n % 120) == 0) {
                        uint32_t topFb = 0;
                        if (qp.slotIdx >= 0 &&
                            qp.slotIdx < (int32_t)sess.slots.size()) {
                            topFb = sess.slots[qp.slotIdx]
                                    .drmFb.topFbId;
                        }
                        ALOGI("NanoBridgeServer: OP_QUEUE slot=%d "
                              "topFb=%u count=%u", qp.slotIdx,
                              topFb, n);
                    }
                    break;
                }
                case OP_CANCEL: {
                    int32_t slot = 0;
                    if (!readAll(client, &slot, sizeof(slot))) {
                        fatal = true; break;
                    }
                    ALOGI("NanoBridgeServer: OP_CANCEL slot=%d", slot);
                    break;
                }
                case OP_SET_SWAP: {
                    int32_t iv = 0;
                    if (!readAll(client, &iv, sizeof(iv))) {
                        fatal = true; break;
                    }
                    ALOGI("NanoBridgeServer: OP_SET_SWAP interval=%d",
                          iv);
                    break;
                }
                case OP_QUIT:
                    ALOGI("NanoBridgeServer: OP_QUIT received");
                    fatal = true;
                    break;
                default:
                    ALOGW("NanoBridgeServer: unknown op 0x%x -- hanging up",
                          op);
                    fatal = true;
                    break;
            }
        }

        // Release session AHBs. v2 retains them across reconnects.
        for (auto& s : sess.slots) {
            releaseAhbFb(s.drmFb);
            s.drmFb = {};
            if (s.ahb) AHardwareBuffer_release(s.ahb);
            s.ahb = nullptr;
        }
        ::close(client);
        ALOGI("NanoBridgeServer: session ended");
    }
    ALOGI("NanoBridgeServer: listen loop exited");
}

} // namespace

bool startServer() {
    bool expected = false;
    if (!sServerRunning.compare_exchange_strong(expected, true)) {
        ALOGW("NanoBridgeServer: startServer called twice, ignoring");
        return true;
    }

    // Prefer the init-provided socket fd (from the `socket
    // gammaos_nano_bridge stream 0666 graphics graphics` line in
    // gammaos-nano.rc). Init already called bind() + listen() under
    // its own privileges and passed the fd to us via an env var,
    // which sidesteps the SELinux bootanim -> /dev/socket bind
    // denial we hit on 2026-04-15. Falls back to self-bind only
    // if the env var is missing (e.g. the service was run from a
    // shell rather than via init), in which case setenforce 0 or
    // a custom sepolicy is required.
    int initFd = android_get_control_socket("gammaos_nano_bridge");
    if (initFd >= 0) {
        // android_get_control_socket hands us the fd opened by init
        // but it's already bound; we just need to mark it as listening
        // (init's LISTEN flag isn't set for SOCK_STREAM by default
        // on AOSP 14, so we do the listen() ourselves).
        if (::listen(initFd, 1) < 0 && errno != EOPNOTSUPP) {
            ALOGE("NanoBridgeServer: listen() on init fd failed: %s",
                  strerror(errno));
            sServerRunning = false;
            return false;
        }
        sListenFd = initFd;
        ALOGI("NanoBridgeServer: using init-supplied fd=%d for %s",
              sListenFd, kSocketPath);
    } else {
        ALOGW("NanoBridgeServer: no init socket fd (ANDROID_SOCKET_"
              "gammaos_nano_bridge unset); falling back to self-bind");
        sListenFd = ::socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
        if (sListenFd < 0) {
            ALOGE("NanoBridgeServer: socket() failed: %s",
                  strerror(errno));
            sServerRunning = false;
            return false;
        }

        struct sockaddr_un addr = {};
        addr.sun_family = AF_UNIX;
        ::unlink(kSocketPath);
        const size_t pathLen = strlen(kSocketPath);
        if (pathLen >= sizeof(addr.sun_path)) {
            ALOGE("NanoBridgeServer: socket path too long");
            ::close(sListenFd);
            sListenFd = -1;
            sServerRunning = false;
            return false;
        }
        memcpy(addr.sun_path, kSocketPath, pathLen + 1);

        if (::bind(sListenFd, reinterpret_cast<sockaddr*>(&addr),
                   sizeof(addr)) < 0) {
            ALOGE("NanoBridgeServer: bind(%s) failed: %s", kSocketPath,
                  strerror(errno));
            ::close(sListenFd);
            sListenFd = -1;
            sServerRunning = false;
            return false;
        }

        if (::listen(sListenFd, 1) < 0) {
            ALOGE("NanoBridgeServer: listen() failed: %s",
                  strerror(errno));
            ::close(sListenFd);
            sListenFd = -1;
            sServerRunning = false;
            return false;
        }
        ALOGI("NanoBridgeServer: self-bound on %s", kSocketPath);
    }

    sServerThread = std::thread(serverLoop);
    return true;
}

void stopServer() {
    if (!sServerRunning.exchange(false)) return;
    if (sListenFd >= 0) {
        ::shutdown(sListenFd, SHUT_RDWR);
        ::close(sListenFd);
        sListenFd = -1;
    }
    if (sServerThread.joinable()) sServerThread.join();
    ::unlink(kSocketPath);
    ALOGI("NanoBridgeServer: stopped");
}

void setMenuChordLatch() {
    sMenuChordLatch.store(true, std::memory_order_release);
}

void setCurrentRomPath(const char* absPath) {
    std::lock_guard<std::mutex> lk(sRomPathMutex);
    sCurrentRomPath = absPath ? absPath : "";
}

} // namespace nano_bridge
} // namespace android
