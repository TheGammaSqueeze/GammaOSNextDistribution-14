/*
 * Copyright (C) 2026 GammaOS
 *
 * NanoAnw -- custom ANativeWindow implementation used by the drastic-
 * nano shim. GLSurfaceView's internal GLThread calls eglCreateWindow
 * Surface on whatever ANativeWindow our factory returns, then
 * eglSwapBuffers on each frame; our queueBuffer hook forwards the
 * completed slot back to gammaos-nano via the bridge socket so nano
 * can page-flip it to the DRM plane.
 *
 * Design
 * ------
 * - Server allocates the AHB ring once and exposes it; client imports
 *   the handles via AHardwareBuffer_recvHandleFromUnixSocket at first
 *   dequeue and caches the AHardwareBuffer* per slot.
 * - dequeueBuffer hands out the next slot in round-robin order. The
 *   ring is naturally paced by nano's drmFlipRingSlot completing the
 *   previously queued slot; if the caller dequeues faster than nano
 *   flips, we block briefly in dequeueBuffer via the bridge's
 *   DEQUEUE opcode.
 * - queueBuffer posts the slot index to nano over the bridge and
 *   returns immediately. Fences: we use the EGL_ANDROID_native_fence_
 *   sync fd where available; otherwise pass -1 and nano assumes GPU
 *   work is flushed.
 *
 * The implementation is intentionally self-contained in this file so
 * future agents can read the whole thing in one sitting.
 */

#define LOG_TAG "GammaOSNano.Anw"

#include <errno.h>
#include <stdarg.h>
#include <stdint.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <atomic>
#include <mutex>
#include <condition_variable>

#include <android/hardware_buffer.h>
#include <system/window.h>
#include <utils/Log.h>
#include <vndk/hardware_buffer.h>

namespace android {
namespace nano_bridge {

// Opcodes, kept in sync with NanoBridgeServer.cpp. See that file for
// protocol docs; client-side implementation lives here.
enum : uint32_t {
    OP_HELLO            = 0x01,  // client -> server, response: RingInfo + N AHBs
    OP_DEQUEUE          = 0x02,  // client -> server "need next slot index"
                                 //   server blocks until a slot is free
    OP_QUEUE            = 0x03,  // client -> server, payload: slot idx + fence fd
    OP_CANCEL           = 0x04,  // client -> server, payload: slot idx
    OP_SET_SWAP         = 0x05,  // client -> server, payload: swap interval
    OP_QUIT             = 0x06,  // client -> server, connection teardown
};

// Reply payload for OP_HELLO.
struct RingInfo {
    uint32_t ringDepth;
    uint32_t width;
    uint32_t height;
    uint32_t stride;
    uint32_t format;     // AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM = 1
    uint32_t usage_lo;   // lower 32 bits of AHB usage flags
    uint32_t usage_hi;
};

// Reply payload for OP_DEQUEUE.
struct DequeueReply {
    int32_t  slotIdx;
    int32_t  fenceFd;     // -1 if no fence; real fd is passed via SCM_RIGHTS
};

// Payload for OP_QUEUE.
struct QueuePayload {
    uint32_t opcode;      // OP_QUEUE
    int32_t  slotIdx;
    int32_t  fenceFd;     // -1 or real fd passed via SCM_RIGHTS
};

// Constants from NanoMenu.cpp's ring geometry. Keep in sync if nano's
// AHB_RING_DEPTH or default panel dimensions change.
static constexpr uint32_t kMaxRingDepth = 8;

// ----------------------------------------------------------------
// NanoWindow -- our ANativeWindow subclass
// ----------------------------------------------------------------

struct NanoWindowBuffer {
    ANativeWindowBuffer  anwb;     // what we hand out to EGL
    AHardwareBuffer*     ahb;      // local AHB reference (recvHandleFromUnixSocket)
    bool                 inUse;    // between dequeue and queue/cancel
};

struct NanoWindow : public ANativeWindow {
    int              bridgeFd;     // connection to gammaos-nano bridge
    std::mutex       mutex;

    // Slots populated on first use via OP_HELLO + N AHB handle recvs.
    NanoWindowBuffer slots[kMaxRingDepth];
    uint32_t         ringDepth;
    uint32_t         width;
    uint32_t         height;
    uint32_t         stride;
    uint32_t         format;
    uint64_t         usage;

    std::atomic<bool> ringReady{false};
    int               swapInterval;
    std::atomic<int>  outstandingFence{-1};

    NanoWindow() : ANativeWindow(), bridgeFd(-1), ringDepth(0),
                   width(0), height(0), stride(0), format(0),
                   usage(0), swapInterval(1) {
        memset(slots, 0, sizeof(slots));
    }
};

// ----------------------------------------------------------------
// Bridge IO helpers
// ----------------------------------------------------------------

static bool writeAll(int fd, const void* buf, size_t len) {
    const uint8_t* p = static_cast<const uint8_t*>(buf);
    while (len > 0) {
        ssize_t n = ::send(fd, p, len, MSG_NOSIGNAL);
        if (n < 0) {
            if (errno == EINTR) continue;
            ALOGE("NanoAnw: send failed: %s", strerror(errno));
            return false;
        }
        p += n;
        len -= n;
    }
    return true;
}

static bool readAll(int fd, void* buf, size_t len) {
    uint8_t* p = static_cast<uint8_t*>(buf);
    while (len > 0) {
        ssize_t n = ::recv(fd, p, len, 0);
        if (n < 0) {
            if (errno == EINTR) continue;
            ALOGE("NanoAnw: recv failed: %s", strerror(errno));
            return false;
        }
        if (n == 0) {
            ALOGE("NanoAnw: connection closed unexpectedly");
            return false;
        }
        p += n;
        len -= n;
    }
    return true;
}

// Send a single int fd over a unix socket via SCM_RIGHTS alongside
// payload `buf`. Used by OP_QUEUE when we have a fence to transfer.
static bool writeWithFd(int sockFd, const void* buf, size_t len,
                        int fdToPass) {
    struct msghdr msg = {};
    struct iovec iov = {};
    iov.iov_base = const_cast<void*>(buf);
    iov.iov_len = len;
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    union {
        char buf[CMSG_SPACE(sizeof(int))];
        struct cmsghdr align;
    } cbuf;
    if (fdToPass >= 0) {
        msg.msg_control = cbuf.buf;
        msg.msg_controllen = sizeof(cbuf.buf);
        struct cmsghdr* cmsg = CMSG_FIRSTHDR(&msg);
        cmsg->cmsg_level = SOL_SOCKET;
        cmsg->cmsg_type = SCM_RIGHTS;
        cmsg->cmsg_len = CMSG_LEN(sizeof(int));
        memcpy(CMSG_DATA(cmsg), &fdToPass, sizeof(int));
    }
    ssize_t n = ::sendmsg(sockFd, &msg, MSG_NOSIGNAL);
    return n == static_cast<ssize_t>(len);
}

// Receive one int fd via SCM_RIGHTS, returns -1 on error or if no cmsg
// accompanies the payload. Retained for v2 fence-fd inbound path;
// currently unused but annotated so the compiler doesn't drop it.
__attribute__((unused))
static int readFdMaybe(int sockFd) {
    char dummy;
    struct iovec iov = {};
    iov.iov_base = &dummy;
    iov.iov_len = 1;
    union {
        char buf[CMSG_SPACE(sizeof(int))];
        struct cmsghdr align;
    } cbuf;
    struct msghdr msg = {};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    msg.msg_control = cbuf.buf;
    msg.msg_controllen = sizeof(cbuf.buf);

    ssize_t n = ::recvmsg(sockFd, &msg, 0);
    if (n <= 0) return -1;

    for (struct cmsghdr* cm = CMSG_FIRSTHDR(&msg); cm != nullptr;
         cm = CMSG_NXTHDR(&msg, cm)) {
        if (cm->cmsg_level == SOL_SOCKET &&
            cm->cmsg_type == SCM_RIGHTS) {
            int fd;
            memcpy(&fd, CMSG_DATA(cm), sizeof(int));
            return fd;
        }
    }
    return -1;
}

// ----------------------------------------------------------------
// Lazy ring bring-up
// ----------------------------------------------------------------

static bool handshakeRing(NanoWindow* nw) {
    if (nw->ringReady.load(std::memory_order_acquire)) return true;

    std::lock_guard<std::mutex> lk(nw->mutex);
    if (nw->ringReady.load(std::memory_order_relaxed)) return true;

    // Send OP_HELLO (no payload).
    uint32_t op = OP_HELLO;
    if (!writeAll(nw->bridgeFd, &op, sizeof(op))) return false;

    // Read RingInfo.
    RingInfo info = {};
    if (!readAll(nw->bridgeFd, &info, sizeof(info))) return false;
    if (info.ringDepth == 0 || info.ringDepth > kMaxRingDepth) {
        ALOGE("NanoAnw: bad ring depth %u", info.ringDepth);
        return false;
    }
    nw->ringDepth = info.ringDepth;
    nw->width = info.width;
    nw->height = info.height;
    nw->stride = info.stride;
    nw->format = info.format;
    nw->usage = (uint64_t)info.usage_hi << 32 | info.usage_lo;

    ALOGI("NanoAnw: ring depth=%u dims=%ux%u stride=%u fmt=%u usage=0x%llx",
          info.ringDepth, info.width, info.height, info.stride,
          info.format, (long long)nw->usage);

    // Import each AHB sent by the server. AHardwareBuffer_recvHandle
    // FromUnixSocket reads the next handle + its SCM_RIGHTS fds from
    // the socket; we call it once per slot.
    for (uint32_t i = 0; i < info.ringDepth; i++) {
        AHardwareBuffer* ahb = nullptr;
        int rc = AHardwareBuffer_recvHandleFromUnixSocket(
                nw->bridgeFd, &ahb);
        if (rc != 0 || ahb == nullptr) {
            ALOGE("NanoAnw: recvHandleFromUnixSocket slot %u failed "
                  "(rc=%d)", i, rc);
            return false;
        }
        nw->slots[i].ahb = ahb;
        nw->slots[i].inUse = false;

        // Build the ANativeWindowBuffer view. ANativeWindowBuffer has
        // the same first-member layout (android_native_base_t common)
        // as AHardwareBuffer's underlying GraphicBuffer. Simplest
        // shim: borrow the AHB's native handle + dims/format/stride.
        AHardwareBuffer_Desc d = {};
        AHardwareBuffer_describe(ahb, &d);
        ANativeWindowBuffer& b = nw->slots[i].anwb;
        memset(&b, 0, sizeof(b));
        b.common.magic = ANDROID_NATIVE_BUFFER_MAGIC;
        b.common.version = sizeof(ANativeWindowBuffer);
        b.common.incRef = [](android_native_base_t*){};
        b.common.decRef = [](android_native_base_t*){};
        b.width = d.width;
        b.height = d.height;
        b.stride = d.stride;
        b.format = d.format;
        b.usage_deprecated = (uint32_t)d.usage;
        b.usage = d.usage;
        // The GPU driver reads b.handle during eglCreateImage /
        // glEGLImageTargetTexture2DOES; point at the AHB's handle.
        b.handle = AHardwareBuffer_getNativeHandle(ahb);
    }

    nw->ringReady.store(true, std::memory_order_release);
    return true;
}

// ----------------------------------------------------------------
// ANativeWindow vtable
// ----------------------------------------------------------------

static int nano_setSwapInterval(ANativeWindow* w, int interval) {
    auto* nw = static_cast<NanoWindow*>(w);
    ALOGI("NanoAnw: setSwapInterval interval=%d (raw=0x%x)",
          interval, (uint32_t)interval);
    nw->swapInterval = interval;
    uint32_t packet[2] = { OP_SET_SWAP, (uint32_t)interval };
    if (nw->bridgeFd >= 0) (void)writeAll(nw->bridgeFd, packet, sizeof(packet));
    return 0;
}

static int nano_dequeueBuffer(ANativeWindow* w,
                              ANativeWindowBuffer** out,
                              int* fenceFd) {
    auto* nw = static_cast<NanoWindow*>(w);
    ALOGI("NanoAnw: dequeueBuffer entry w=%p out=%p fenceFd=%p",
          w, out, fenceFd);
    if (out == nullptr) {
        ALOGE("NanoAnw: dequeueBuffer called with out=NULL; aborting");
        return -EINVAL;
    }
    if (!handshakeRing(nw)) return -EIO;

    std::lock_guard<std::mutex> lk(nw->mutex);

    uint32_t op = OP_DEQUEUE;
    if (!writeAll(nw->bridgeFd, &op, sizeof(op))) return -EIO;
    DequeueReply rep = {};
    if (!readAll(nw->bridgeFd, &rep, sizeof(rep))) return -EIO;
    if (rep.slotIdx < 0 ||
        rep.slotIdx >= (int32_t)nw->ringDepth) {
        ALOGE("NanoAnw: dequeue got bad slotIdx=%d", rep.slotIdx);
        return -EIO;
    }

    auto& slot = nw->slots[rep.slotIdx];
    if (slot.inUse) {
        ALOGW("NanoAnw: dequeue returned slot %d already in use",
              rep.slotIdx);
    }
    slot.inUse = true;
    *out = &slot.anwb;
    if (fenceFd != nullptr) {
        *fenceFd = rep.fenceFd;  // -1 or a real fd via SCM_RIGHTS (future)
    }
    ALOGI("NanoAnw: dequeueBuffer success slot=%d anwb=%p "
          "(handle=%p w=%u h=%u stride=%u fmt=%d) fence=%d",
          rep.slotIdx, (void*)*out,
          (void*)slot.anwb.handle,
          slot.anwb.width, slot.anwb.height, slot.anwb.stride,
          slot.anwb.format, rep.fenceFd);
    return 0;
}

static int nano_queueBuffer(ANativeWindow* w,
                            ANativeWindowBuffer* buf,
                            int fenceFd) {
    auto* nw = static_cast<NanoWindow*>(w);
    ALOGI("NanoAnw: queueBuffer entry w=%p buf=%p fenceFd=%d", w, buf, fenceFd);
    int slotIdx = -1;
    for (uint32_t i = 0; i < nw->ringDepth; i++) {
        if (&nw->slots[i].anwb == buf) { slotIdx = (int)i; break; }
    }
    if (slotIdx < 0) {
        ALOGE("NanoAnw: queueBuffer got unknown ANativeWindowBuffer*");
        if (fenceFd >= 0) ::close(fenceFd);
        return -EINVAL;
    }
    ALOGI("NanoAnw: queueBuffer matched slot=%d", slotIdx);

    std::lock_guard<std::mutex> lk(nw->mutex);
    nw->slots[slotIdx].inUse = false;

    QueuePayload qp = {};
    qp.opcode = OP_QUEUE;
    qp.slotIdx = slotIdx;
    qp.fenceFd = (fenceFd >= 0) ? 1 : -1;
    if (!writeWithFd(nw->bridgeFd, &qp, sizeof(qp), fenceFd)) {
        if (fenceFd >= 0) ::close(fenceFd);
        return -EIO;
    }
    // Ownership of fenceFd transferred to the server on successful
    // sendmsg; don't close on our side.
    return 0;
}

static int nano_cancelBuffer(ANativeWindow* w,
                             ANativeWindowBuffer* buf,
                             int fenceFd) {
    auto* nw = static_cast<NanoWindow*>(w);
    int slotIdx = -1;
    for (uint32_t i = 0; i < nw->ringDepth; i++) {
        if (&nw->slots[i].anwb == buf) { slotIdx = (int)i; break; }
    }
    if (fenceFd >= 0) ::close(fenceFd);
    if (slotIdx < 0) return -EINVAL;

    std::lock_guard<std::mutex> lk(nw->mutex);
    nw->slots[slotIdx].inUse = false;
    uint32_t packet[2] = { OP_CANCEL, (uint32_t)slotIdx };
    (void)writeAll(nw->bridgeFd, packet, sizeof(packet));
    return 0;
}

static int nano_query(const ANativeWindow* w, int what, int* value) {
    auto* nw = static_cast<const NanoWindow*>(w);
    switch (what) {
        case NATIVE_WINDOW_WIDTH:
        case NATIVE_WINDOW_DEFAULT_WIDTH:
            *value = (int)nw->width; return 0;
        case NATIVE_WINDOW_HEIGHT:
        case NATIVE_WINDOW_DEFAULT_HEIGHT:
            *value = (int)nw->height; return 0;
        case NATIVE_WINDOW_FORMAT:
            *value = (int)nw->format; return 0;
        case NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS:
            *value = 1; return 0;
        case NATIVE_WINDOW_CONCRETE_TYPE:
            // Vendor Mali uses the concrete-type to decide whether
            // to cast the ANW to a Surface* (1) for vtable access.
            // We're not a Surface; return FRAMEBUFFER (0) so Mali
            // takes the generic ANW path.
            *value = NATIVE_WINDOW_FRAMEBUFFER; return 0;
        case NATIVE_WINDOW_CONSUMER_RUNNING_BEHIND:
            *value = 0; return 0;
        case NATIVE_WINDOW_TRANSFORM_HINT:
            *value = 0; return 0;
        default:
            *value = 0; return -ENOENT;
    }
}

static int nano_perform(ANativeWindow* w, int op, ...) {
    auto* nw = static_cast<NanoWindow*>(w);
    va_list args;
    va_start(args, op);
    int rc = 0;
    switch (op) {
        case NATIVE_WINDOW_API_CONNECT: {
            int api = va_arg(args, int);
            ALOGI("NanoAnw: perform API_CONNECT api=%d", api);
            // Record connected API so subsequent ops can validate.
            nw->oem[0] = (intptr_t)api;
            break;
        }
        case NATIVE_WINDOW_API_DISCONNECT: {
            int api = va_arg(args, int);
            ALOGI("NanoAnw: perform API_DISCONNECT api=%d", api);
            nw->oem[0] = 0;
            break;
        }
        case NATIVE_WINDOW_SET_BUFFERS_FORMAT: {
            int fmt = va_arg(args, int);
            ALOGI("NanoAnw: perform SET_BUFFERS_FORMAT fmt=%d", fmt);
            if (fmt > 0) nw->format = (uint32_t)fmt;
            break;
        }
        case NATIVE_WINDOW_SET_BUFFERS_DIMENSIONS:
        case NATIVE_WINDOW_SET_BUFFERS_USER_DIMENSIONS: {
            int bw = va_arg(args, int);
            int bh = va_arg(args, int);
            ALOGI("NanoAnw: perform SET_BUFFERS_DIMENSIONS %dx%d",
                  bw, bh);
            if (bw > 0) nw->width = (uint32_t)bw;
            if (bh > 0) nw->height = (uint32_t)bh;
            break;
        }
        case NATIVE_WINDOW_SET_BUFFERS_TRANSFORM:
        case NATIVE_WINDOW_SET_SCALING_MODE:
        case NATIVE_WINDOW_SET_BUFFER_COUNT: {
            int v = va_arg(args, int);
            ALOGV("NanoAnw: perform op=%d v=%d (ignored)", op, v);
            break;
        }
        case NATIVE_WINDOW_SET_USAGE: {
            int u = va_arg(args, int);
            ALOGI("NanoAnw: perform SET_USAGE 0x%x", u);
            nw->usage = (uint64_t)(uint32_t)u;
            break;
        }
        case NATIVE_WINDOW_SET_USAGE64: {
            uint64_t u = va_arg(args, uint64_t);
            ALOGI("NanoAnw: perform SET_USAGE64 0x%llx",
                  (long long)u);
            nw->usage = u;
            break;
        }
        case NATIVE_WINDOW_SET_BUFFERS_TIMESTAMP:
            va_arg(args, int64_t); break;
        default:
            ALOGV("NanoAnw: perform op=%d unhandled -> ENOENT", op);
            rc = -ENOENT; break;
    }
    va_end(args);
    return rc;
}

// Deprecated legacy entry points -- forward to the modern versions
// with a null fence.
static int nano_dequeueBuffer_DEPRECATED(ANativeWindow* w,
                                         ANativeWindowBuffer** out) {
    int fence = -1;
    int rc = nano_dequeueBuffer(w, out, &fence);
    if (fence >= 0) ::close(fence);
    return rc;
}
static int nano_lockBuffer_DEPRECATED(ANativeWindow* /*w*/,
                                      ANativeWindowBuffer* /*buf*/) {
    return 0;
}
static int nano_queueBuffer_DEPRECATED(ANativeWindow* w,
                                       ANativeWindowBuffer* buf) {
    ALOGI("NanoAnw: queueBuffer_DEPRECATED entry w=%p buf=%p", w, buf);
    return nano_queueBuffer(w, buf, -1);
}
static int nano_cancelBuffer_DEPRECATED(ANativeWindow* w,
                                        ANativeWindowBuffer* buf) {
    return nano_cancelBuffer(w, buf, -1);
}

// ----------------------------------------------------------------
// Public entry
// ----------------------------------------------------------------

static NanoWindow* sGlobalWindow = nullptr;
static std::mutex sGlobalWindowMutex;

// Forward declaration; defined later in this file.
static bool handshakeRing(NanoWindow* nw);

ANativeWindow* createNanoAnativeWindow(int bridgeFd) {
    std::lock_guard<std::mutex> lk(sGlobalWindowMutex);
    if (sGlobalWindow != nullptr) return sGlobalWindow;

    NanoWindow* nw = new NanoWindow();
    nw->bridgeFd = bridgeFd;

    // Refcount stubs -- we leak the window intentionally; process
    // lifetime covers the gameplay session.
    nw->common.magic = ANDROID_NATIVE_WINDOW_MAGIC;
    // Vendor EGL on RK3568 (Mali) rejects ANW with sizeof=192
    // (the AOSP A14 struct size); the driver was built against
    // an older sizeof=184 ABI. Set version to 184 so vendor's
    // version-equality check passes. Actual struct in memory is
    // still 192 -- the trailing 8 bytes are unused by Mali.
    nw->common.version = 184;
    nw->common.incRef = [](android_native_base_t*){};
    nw->common.decRef = [](android_native_base_t*){};

    nw->setSwapInterval = nano_setSwapInterval;
    nw->dequeueBuffer_DEPRECATED = nano_dequeueBuffer_DEPRECATED;
    nw->lockBuffer_DEPRECATED = nano_lockBuffer_DEPRECATED;
    nw->queueBuffer_DEPRECATED = nano_queueBuffer_DEPRECATED;
    nw->query = nano_query;
    nw->perform = nano_perform;
    nw->cancelBuffer_DEPRECATED = nano_cancelBuffer_DEPRECATED;
    nw->dequeueBuffer = nano_dequeueBuffer;
    nw->queueBuffer = nano_queueBuffer;
    nw->cancelBuffer = nano_cancelBuffer;

    // Eagerly run the HELLO handshake so width/height/format are
    // populated BEFORE eglCreateWindowSurface. EGL queries these
    // before accepting the ANativeWindow; zero dims -> EGL_BAD_
    // NATIVE_WINDOW (0x300b). If the handshake fails we still
    // return the window so the caller can log a meaningful error
    // instead of silent null.
    if (!handshakeRing(nw)) {
        ALOGE("NanoAnw: eager handshake failed; returning window "
              "with zero dims (caller will see EGL_BAD_NATIVE_WINDOW)");
    } else {
        ALOGI("NanoAnw: handshake done, dims=%ux%u format=%u",
              nw->width, nw->height, nw->format);
    }

    // Struct-layout diagnostic for vendor-EGL validation. Expert's
    // NDK r27/API 26 build reports sizeof=184. Ours is compiled
    // against AOSP frameworks/native/libs/nativewindow headers; if
    // the size differs that's potentially the validation mismatch.
    ALOGI("NanoAnw: sizeof(ANativeWindow)=%zu, our ANW=%p common="
          "{magic=0x%x version=%u incRef=%p decRef=%p}",
          sizeof(ANativeWindow), nw,
          (unsigned)nw->common.magic,
          (unsigned)nw->common.version,
          (void*)nw->common.incRef,
          (void*)nw->common.decRef);
    // Hex dump first 256 bytes of the struct so we can verify the
    // vtable slots are populated.
    {
        const uint8_t* b = reinterpret_cast<const uint8_t*>(
                static_cast<ANativeWindow*>(nw));
        char hex[3 * 32 + 1];
        for (int row = 0; row < 8; row++) {
            char* p = hex;
            for (int col = 0; col < 32; col++) {
                p += snprintf(p, 4, "%02x ", b[row * 32 + col]);
            }
            *p = 0;
            ALOGI("NanoAnw: anw+0x%03x: %s", row * 32, hex);
        }
    }

    sGlobalWindow = nw;
    return nw;
}

} // namespace nano_bridge
} // namespace android
