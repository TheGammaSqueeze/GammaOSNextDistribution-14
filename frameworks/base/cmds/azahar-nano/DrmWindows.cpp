#define LOG_TAG "azahar-nano"

#include "DrmWindows.h"

#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>

#include <algorithm>
#include <chrono>
#include <string>
#include <poll.h>
#include <sched.h>
#include <sys/eventfd.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <drm/drm_fourcc.h>
#include <drm/drm_mode.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <cutils/native_handle.h>
#include <cutils/properties.h>
#include <gui/BufferQueue.h>
#include <log/log.h>
#include <ui/GraphicBufferMapper.h>
#include <ui/PixelFormat.h>

using namespace android;

namespace azahar_nano {

namespace {

uint32_t findProp(int fd, uint32_t objId, uint32_t objType, const char* name) {
    drmModeObjectPropertiesPtr props = drmModeObjectGetProperties(fd, objId, objType);
    if (!props) return 0;
    uint32_t id = 0;
    for (uint32_t i = 0; i < props->count_props && !id; i++) {
        drmModePropertyPtr p = drmModeGetProperty(fd, props->props[i]);
        if (!p) continue;
        if (!strcmp(p->name, name)) id = p->prop_id;
        drmModeFreeProperty(p);
    }
    drmModeFreeObjectProperties(props);
    return id;
}

uint64_t getPropValue(int fd, uint32_t objId, uint32_t objType, const char* name, bool* found) {
    drmModeObjectPropertiesPtr props = drmModeObjectGetProperties(fd, objId, objType);
    uint64_t v = 0;
    if (found) *found = false;
    if (!props) return 0;
    for (uint32_t i = 0; i < props->count_props; i++) {
        drmModePropertyPtr p = drmModeGetProperty(fd, props->props[i]);
        if (!p) continue;
        if (!strcmp(p->name, name)) {
            v = props->prop_values[i];
            if (found) *found = true;
        }
        drmModeFreeProperty(p);
    }
    drmModeFreeObjectProperties(props);
    return v;
}

int64_t monoNs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

const char* connTypeName(uint32_t t) {
    switch (t) {
    case DRM_MODE_CONNECTOR_DSI: return "DSI";
    case DRM_MODE_CONNECTOR_DisplayPort: return "DP";
    case DRM_MODE_CONNECTOR_HDMIA: return "HDMI";
    case DRM_MODE_CONNECTOR_VIRTUAL: return "Virtual";
    case DRM_MODE_CONNECTOR_eDP: return "eDP";
    default: return "other";
    }
}

class FrameListener : public BufferItemConsumer::FrameAvailableListener {
public:
    FrameListener(int wakeFd, std::atomic<uint64_t>* count, std::atomic<int64_t>* lastNs,
                  std::atomic<uint32_t>* hist)
        : mWakeFd(wakeFd), mCount(count), mLastNs(lastNs), mHist(hist) {}
    void onFrameAvailable(const BufferItem&) override { signal(); }
    void onFrameReplaced(const BufferItem&) override { signal(); }
private:
    void signal() {
        (*mCount)++;
        const int64_t now = monoNs();
        const int64_t prev = mLastNs->exchange(now);
        if (prev) {
            // Producer cadence histogram: <10, 10-14, 14-20, 20-28, >28 ms.
            const double dt = (now - prev) / 1e6;
            const int b = dt < 10 ? 0 : dt < 14 ? 1 : dt < 20 ? 2 : dt < 28 ? 3 : 4;
            mHist[b]++;
        }
        const uint64_t one = 1;
        (void)!write(mWakeFd, &one, sizeof(one));
    }
    int mWakeFd;
    std::atomic<uint64_t>* mCount;
    std::atomic<int64_t>* mLastNs;
    std::atomic<uint32_t>* mHist;
};

} // namespace

bool DrmWindows::openDevice() {
    for (int attempt = 0; attempt < 50 && mFd < 0; attempt++) {
        mFd = open("/dev/dri/card0", O_RDWR | O_CLOEXEC);
        if (mFd < 0) usleep(100 * 1000);
    }
    if (mFd < 0) {
        ALOGE("DRM: open card0 failed: %s", strerror(errno));
        return false;
    }
    drmSetClientCap(mFd, DRM_CLIENT_CAP_UNIVERSAL_PLANES, 1);
    drmSetClientCap(mFd, DRM_CLIENT_CAP_ATOMIC, 1);
    int rc = -1;
    for (int attempt = 0; attempt < 50; attempt++) {
        rc = drmSetMaster(mFd);
        if (rc == 0) break;
        usleep(100 * 1000);
    }
    if (rc != 0) {
        ALOGE("DRM: SET_MASTER failed: %s", strerror(errno));
        return false;
    }
    return true;
}

bool DrmWindows::enumerate() {
    drmModeResPtr res = drmModeGetResources(mFd);
    if (!res) {
        ALOGE("DRM: no resources");
        return false;
    }
    std::vector<DrmPanel> found;
    for (int i = 0; i < res->count_connectors; i++) {
        drmModeConnectorPtr c = drmModeGetConnector(mFd, res->connectors[i]);
        if (!c) continue;
        if (c->connection == DRM_MODE_CONNECTED && c->count_modes > 0 &&
            c->connector_type != DRM_MODE_CONNECTOR_VIRTUAL) {
            DrmPanel p;
            p.connId = c->connector_id;
            p.isDsi = c->connector_type == DRM_MODE_CONNECTOR_DSI;
            // Preferred mode, else the first.
            int mi = 0;
            for (int m = 0; m < c->count_modes; m++) {
                if (c->modes[m].type & DRM_MODE_TYPE_PREFERRED) { mi = m; break; }
            }
            drmModeModeInfo mode = c->modes[mi];
            p.width = mode.hdisplay;
            p.height = mode.vdisplay;
            // Exact refresh from the timings (vrefresh is rounded to an integer).
            if (mode.htotal && mode.vtotal) {
                p.refreshHz = (double)mode.clock * 1000.0 / ((double)mode.htotal * (double)mode.vtotal);
                if (mode.flags & DRM_MODE_FLAG_INTERLACE) p.refreshHz *= 2.0;
                if (mode.flags & DRM_MODE_FLAG_DBLSCAN) p.refreshHz /= 2.0;
                if (mode.vscan > 1) p.refreshHz /= mode.vscan;
            } else {
                p.refreshHz = mode.vrefresh;
            }
            drmModeCreatePropertyBlob(mFd, &mode, sizeof(mode), &p.modeBlob);
            // CRTC: the one the encoder currently drives, else the first possible.
            uint32_t crtcId = 0;
            if (c->encoder_id) {
                drmModeEncoderPtr e = drmModeGetEncoder(mFd, c->encoder_id);
                if (e) {
                    crtcId = e->crtc_id;
                    if (!crtcId) {
                        for (int k = 0; k < res->count_crtcs; k++) {
                            if (e->possible_crtcs & (1u << k)) { crtcId = res->crtcs[k]; break; }
                        }
                    }
                    drmModeFreeEncoder(e);
                }
            }
            if (!crtcId) {
                for (int ei = 0; ei < c->count_encoders && !crtcId; ei++) {
                    drmModeEncoderPtr e = drmModeGetEncoder(mFd, c->encoders[ei]);
                    if (!e) continue;
                    for (int k = 0; k < res->count_crtcs; k++) {
                        if (e->possible_crtcs & (1u << k)) { crtcId = res->crtcs[k]; break; }
                    }
                    drmModeFreeEncoder(e);
                }
            }
            p.crtcId = crtcId;
            for (int k = 0; k < res->count_crtcs; k++) {
                if (res->crtcs[k] == crtcId) p.crtcIndex = k;
            }
            ALOGI("DRM: connector %u %s %ux%u@%.3f Hz crtc %u (idx %u)", p.connId,
                  connTypeName(c->connector_type), p.width, p.height, p.refreshHz, p.crtcId,
                  p.crtcIndex);
            if (crtcId) found.push_back(p);
        }
        drmModeFreeConnector(c);
    }
    drmModeFreeResources(res);
    // DSI (the built-in top panel) first.
    std::stable_sort(found.begin(), found.end(),
                     [](const DrmPanel& a, const DrmPanel& b) { return a.isDsi && !b.isDsi; });
    for (auto& p : found) {
        if (!pickPlane(p) || !loadProps(p)) continue;
        mPanels.push_back(p);
        if (mPanels.size() == 2) break;
    }
    return !mPanels.empty();
}

bool DrmWindows::pickPlane(DrmPanel& p) {
    drmModePlaneResPtr pr = drmModeGetPlaneResources(mFd);
    if (!pr) return false;
    uint32_t best = 0;
    int bestType = -1;
    for (uint32_t i = 0; i < pr->count_planes; i++) {
        drmModePlanePtr pl = drmModeGetPlane(mFd, pr->planes[i]);
        if (!pl) continue;
        const bool fits = (pl->possible_crtcs & (1u << p.crtcIndex)) != 0;
        bool fmtOk = false;
        for (uint32_t f = 0; f < pl->count_formats; f++) {
            if (pl->formats[f] == DRM_FORMAT_ABGR8888 || pl->formats[f] == DRM_FORMAT_XBGR8888) {
                fmtOk = true;
            }
        }
        if (fits && fmtOk) {
            bool found = false;
            const uint64_t type = getPropValue(mFd, pl->plane_id, DRM_MODE_OBJECT_PLANE, "type", &found);
            // Prefer a primary plane; an overlay works too.
            int score = (type == DRM_PLANE_TYPE_PRIMARY) ? 2 : (type == DRM_PLANE_TYPE_OVERLAY ? 1 : 0);
            // The plane the previous master (the composer) used as this CRTC's
            // base layer is the safest choice on SDE.
            if (pl->crtc_id == p.crtcId) score += 4;
            // Skip a plane already bound to another panel.
            bool taken = false;
            for (const auto& q : mPanels) taken |= (q.planeId == pl->plane_id);
            if (!taken && score > bestType) {
                bestType = score;
                best = pl->plane_id;
            }
        }
        drmModeFreePlane(pl);
    }
    drmModeFreePlaneResources(pr);
    if (!best) {
        ALOGE("DRM: no plane for crtc %u", p.crtcId);
        return false;
    }
    p.planeId = best;
    return true;
}

bool DrmWindows::loadProps(DrmPanel& p) {
    const uint32_t pl = p.planeId;
    p.pFbId = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "FB_ID");
    p.pCrtcId = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "CRTC_ID");
    p.pSrcX = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "SRC_X");
    p.pSrcY = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "SRC_Y");
    p.pSrcW = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "SRC_W");
    p.pSrcH = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "SRC_H");
    p.pCrtcX = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "CRTC_X");
    p.pCrtcY = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "CRTC_Y");
    p.pCrtcW = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "CRTC_W");
    p.pCrtcH = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "CRTC_H");
    p.pInFence = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "IN_FENCE_FD");
    p.pRotation = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "rotation");
    p.cActive = findProp(mFd, p.crtcId, DRM_MODE_OBJECT_CRTC, "ACTIVE");
    p.cModeId = findProp(mFd, p.crtcId, DRM_MODE_OBJECT_CRTC, "MODE_ID");
    p.connCrtcId = findProp(mFd, p.connId, DRM_MODE_OBJECT_CONNECTOR, "CRTC_ID");
    const bool ok = p.pFbId && p.pCrtcId && p.pSrcW && p.pSrcH && p.pCrtcW && p.pCrtcH &&
                    p.cActive && p.cModeId && p.connCrtcId;
    // The plane keeps whatever rotation its previous owner (the composer or the
    // home) left on it; pin it to 0 degrees ourselves and log a stale value.
    if (p.pRotation) {
        bool found = false;
        const uint64_t cur = getPropValue(mFd, pl, DRM_MODE_OBJECT_PLANE, "rotation", &found);
        if (found && cur != DRM_MODE_ROTATE_0) {
            ALOGW("DRM: plane %u had rotation 0x%llx set, pinning to 0", pl, (unsigned long long)cur);
        }
        p.rotationValue = DRM_MODE_ROTATE_0;
    }
    ALOGI("DRM: plane %u props fb=%u crtc=%u fence=%u rotation=%u (%s)", pl, p.pFbId, p.pCrtcId,
          p.pInFence, p.pRotation, ok ? "ok" : "MISSING");
    return ok;
}

bool DrmWindows::modeset() {
    // One black dumb framebuffer per panel so the first commit has something to show.
    drmModeAtomicReqPtr req = drmModeAtomicAlloc();
    bool needModeset = false;
    for (auto& p : mPanels) {
        drm_mode_create_dumb cd{};
        cd.width = p.width;
        cd.height = p.height;
        cd.bpp = 32;
        uint32_t fb = 0;
        if (ioctl(mFd, DRM_IOCTL_MODE_CREATE_DUMB, &cd) == 0) {
            uint32_t handles[4] = {cd.handle, 0, 0, 0};
            uint32_t pitches[4] = {cd.pitch, 0, 0, 0};
            uint32_t offsets[4] = {0, 0, 0, 0};
            drmModeAddFB2(mFd, p.width, p.height, DRM_FORMAT_XBGR8888, handles, pitches, offsets,
                          &fb, 0);
        }
        mBlackFbs.push_back(fb);
        bool lit = false;
        if (drmModeCrtcPtr cur = drmModeGetCrtc(mFd, p.crtcId)) {
            lit = cur->mode_valid && cur->mode.hdisplay == p.width && cur->mode.vdisplay == p.height;
            drmModeFreeCrtc(cur);
        }
        if (lit) {
            bool f = false;
            lit = getPropValue(mFd, p.connId, DRM_MODE_OBJECT_CONNECTOR, "CRTC_ID", &f) == p.crtcId && f;
        }
        ALOGI("DRM: crtc %u %s", p.crtcId, lit ? "already lit, keeping its mode" : "needs a modeset");
        if (!lit) {
            needModeset = true;
            drmModeAtomicAddProperty(req, p.crtcId, p.cActive, 1);
            drmModeAtomicAddProperty(req, p.crtcId, p.cModeId, p.modeBlob);
            drmModeAtomicAddProperty(req, p.connId, p.connCrtcId, p.crtcId);
        }
        if (fb) {
            drmModeAtomicAddProperty(req, p.planeId, p.pFbId, fb);
            drmModeAtomicAddProperty(req, p.planeId, p.pCrtcId, p.crtcId);
            drmModeAtomicAddProperty(req, p.planeId, p.pSrcX, 0);
            drmModeAtomicAddProperty(req, p.planeId, p.pSrcY, 0);
            drmModeAtomicAddProperty(req, p.planeId, p.pSrcW, (uint64_t)p.width << 16);
            drmModeAtomicAddProperty(req, p.planeId, p.pSrcH, (uint64_t)p.height << 16);
            drmModeAtomicAddProperty(req, p.planeId, p.pCrtcX, 0);
            drmModeAtomicAddProperty(req, p.planeId, p.pCrtcY, 0);
            drmModeAtomicAddProperty(req, p.planeId, p.pCrtcW, p.width);
            drmModeAtomicAddProperty(req, p.planeId, p.pCrtcH, p.height);
            if (p.pRotation && p.rotationValue) {
                drmModeAtomicAddProperty(req, p.planeId, p.pRotation, p.rotationValue);
            }
        }
    }
    const int rc = drmModeAtomicCommit(mFd, req, needModeset ? DRM_MODE_ATOMIC_ALLOW_MODESET : 0, nullptr);
    drmModeAtomicFree(req);
    if (rc != 0) {
        ALOGE("DRM: initial commit failed (modeset=%d): %s", needModeset, strerror(errno));
        return false;
    }
    ALOGI("DRM: modeset done on %zu panel(s)", mPanels.size());
    return true;
}

bool DrmWindows::createQueue(DrmPanel& p, int idx) {
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&p.producer, &consumer);
    p.consumer = new BufferItemConsumer(consumer,
                                        GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_HW_RENDER |
                                            GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_SW_READ_RARELY,
                                        2 + kMaxReady, false);
    p.consumer->setName(String8(idx == 0 ? "azahar-nano-top" : "azahar-nano-bottom"));
    p.consumer->setDefaultBufferSize(p.width, p.height);
    p.consumer->setDefaultBufferFormat(PIXEL_FORMAT_RGBA_8888);
    // ConsumerBase keeps only a weak reference to the listener: hold it here.
    p.wakeFd = eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC);
    p.listener = new FrameListener(p.wakeFd, p.availCount.get(), p.lastAvailNs.get(), p.cadence->data());
    p.consumer->setFrameAvailableListener(p.listener);
    p.surface = new Surface(p.producer, true);
    p.surface->setMaxDequeuedBufferCount(3);
    return true;
}

bool DrmWindows::createOverlayQueue(DrmPanel& p, int idx) {
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&p.ovProducer, &consumer);
    // SW_READ keeps gralloc from picking a UBWC (compressed) layout: the plane
    // scans these buffers out with the linear framebuffer we import, and a
    // compressed buffer shows as noise.
    p.ovConsumer = new BufferItemConsumer(consumer,
                                          GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_HW_RENDER |
                                              GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_SW_READ_RARELY,
                                          2 + kMaxReady, false);
    p.ovConsumer->setName(String8(idx == 0 ? "azahar-nano-menu-top" : "azahar-nano-menu-bottom"));
    p.ovConsumer->setDefaultBufferSize(p.width, p.height);
    p.ovConsumer->setDefaultBufferFormat(PIXEL_FORMAT_RGBA_8888);
    // The same wake fd: the presenter re-evaluates both sources on any frame.
    p.ovListener = new FrameListener(p.wakeFd, p.availCount.get(), p.lastAvailNs.get(), p.cadence->data());
    p.ovConsumer->setFrameAvailableListener(p.ovListener);
    p.ovSurface = new Surface(p.ovProducer, true);
    p.ovSurface->setMaxDequeuedBufferCount(2);
    return true;
}

// A second plane for the CRTC (any type but the one the panel scans out on, with
// an RGBA format): used for the alpha-blended HUD.
bool DrmWindows::pickHudPlane(DrmPanel& p) {
    drmModePlaneResPtr pr = drmModeGetPlaneResources(mFd);
    if (!pr) return false;
    uint32_t best = 0;
    int bestScore = -1;
    for (uint32_t i = 0; i < pr->count_planes; i++) {
        drmModePlanePtr pl = drmModeGetPlane(mFd, pr->planes[i]);
        if (!pl) continue;
        bool taken = pl->plane_id == p.planeId;
        for (const auto& q : mPanels) taken |= (q.planeId == pl->plane_id || q.hudPlaneId == pl->plane_id);
        const bool fits = (pl->possible_crtcs & (1u << p.crtcIndex)) != 0;
        bool fmtOk = false;
        for (uint32_t f = 0; f < pl->count_formats; f++) if (pl->formats[f] == DRM_FORMAT_ABGR8888) fmtOk = true;
        if (!taken && fits && fmtOk) {
            bool found = false;
            const uint64_t type = getPropValue(mFd, pl->plane_id, DRM_MODE_OBJECT_PLANE, "type", &found);
            // Prefer an overlay plane, then a primary of another CRTC, never a cursor.
            const int score = type == DRM_PLANE_TYPE_OVERLAY ? 2 : type == DRM_PLANE_TYPE_PRIMARY ? 1 : -1;
            if (score > bestScore) { bestScore = score; best = pl->plane_id; }
        }
        drmModeFreePlane(pl);
    }
    drmModeFreePlaneResources(pr);
    if (!best || bestScore < 0) return false;
    const uint32_t pl = best;
    p.hudPlaneId = pl;
    p.hPFbId = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "FB_ID");
    p.hPCrtcId = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "CRTC_ID");
    p.hPSrcX = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "SRC_X");
    p.hPSrcY = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "SRC_Y");
    p.hPSrcW = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "SRC_W");
    p.hPSrcH = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "SRC_H");
    p.hPCrtcX = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "CRTC_X");
    p.hPCrtcY = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "CRTC_Y");
    p.hPCrtcW = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "CRTC_W");
    p.hPCrtcH = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "CRTC_H");
    p.hPAlpha = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "alpha");
    p.hPBlend = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "blend_op");
    p.hPZpos = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "zpos");
    p.hPRotation = findProp(mFd, pl, DRM_MODE_OBJECT_PLANE, "rotation");
    // Property ranges: SDE's "alpha" is 0..255 (not the 0..0xffff of the generic
    // property) and every plane on a CRTC needs its own "zpos" stage; take the
    // primary's stage plus one, clamped to the range.
    auto propMax = [&](uint32_t propId, uint64_t def) {
        uint64_t v = def;
        if (drmModePropertyPtr prop = drmModeGetProperty(mFd, propId)) {
            if (prop->count_values >= 2) v = prop->values[1];
            drmModeFreeProperty(prop);
        }
        return v;
    };
    p.hudAlphaMax = p.hPAlpha ? propMax(p.hPAlpha, 255) : 255;
    if (p.hPZpos) {
        bool found = false;
        const uint64_t primaryZ = getPropValue(mFd, p.planeId, DRM_MODE_OBJECT_PLANE, "zpos", &found);
        const uint64_t zmax = propMax(p.hPZpos, 7);
        p.hudZpos = found && primaryZ + 1 <= zmax ? primaryZ + 1 : zmax;
    }
    // blend_op is an enum on SDE ("not_defined", "opaque", "premultiplied", "coverage").
    if (p.hPBlend) {
        drmModePropertyPtr prop = drmModeGetProperty(mFd, p.hPBlend);
        if (prop) {
            for (int i = 0; i < prop->count_enums; i++) {
                if (!strcmp(prop->enums[i].name, "coverage")) p.hudBlendCoverage = prop->enums[i].value;
            }
            drmModeFreeProperty(prop);
        }
    }
    ALOGI("DRM: HUD plane %u for crtc %u (alpha max %llu, blend coverage=%llu, zpos %llu)",
          pl, p.crtcId, (unsigned long long)p.hudAlphaMax, (unsigned long long)p.hudBlendCoverage,
          (unsigned long long)p.hudZpos);
    return p.hPFbId && p.hPCrtcId && p.hPSrcW && p.hPSrcH && p.hPCrtcW && p.hPCrtcH;
}

bool DrmWindows::createHudQueue(DrmPanel& p, int idx) {
    // A strip along the panel's physical top edge: for an upright (portrait buffer,
    // landscape panel) panel that is the buffer's left columns.
    const bool upright = p.height > p.width;
    p.hudW = upright ? kHudStrip : p.width;
    p.hudH = upright ? p.height : kHudStrip;
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&p.hudProducer, &consumer);
    p.hudConsumer = new BufferItemConsumer(consumer,
                                           GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_HW_RENDER |
                                               GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_SW_READ_RARELY,
                                           2, false);
    p.hudConsumer->setName(String8(idx == 0 ? "azahar-nano-hud-top" : "azahar-nano-hud-bottom"));
    p.hudConsumer->setDefaultBufferSize(p.hudW, p.hudH);
    p.hudConsumer->setDefaultBufferFormat(PIXEL_FORMAT_RGBA_8888);
    p.hudListener = new FrameListener(p.wakeFd, p.availCount.get(), p.lastAvailNs.get(), p.cadence->data());
    p.hudConsumer->setFrameAvailableListener(p.hudListener);
    p.hudSurface = new Surface(p.hudProducer, true);
    p.hudSurface->setMaxDequeuedBufferCount(2);
    // HUD frames are only consumed when a game frame flips; while the core is
    // paused a full queue must not block the HUD drawer forever.
    p.hudSurface->setDequeueTimeout(50 * 1000 * 1000);
    return true;
}

ANativeWindow* DrmWindows::hudWindow(int idx, uint32_t* w, uint32_t* h) {
    if (idx < 0 || idx >= (int)mPanels.size()) return nullptr;
    DrmPanel& p = mPanels[idx];
    if (!p.hudPlaneId && !pickHudPlane(p)) {
        ALOGW("DRM: no spare plane for a HUD on crtc %u", p.crtcId);
        return nullptr;
    }
    if (p.hudSurface == nullptr) createHudQueue(p, idx);
    if (w) *w = p.hudW;
    if (h) *h = p.hudH;
    return p.hudSurface.get();
}

void DrmWindows::setHudActive(int idx, bool active) {
    if (idx < 0 || idx >= (int)mPanels.size()) return;
    DrmPanel& p = mPanels[idx];
    p.hudActive->store(active);
    const uint64_t one = 1;
    (void)!write(p.wakeFd, &one, sizeof(one));
}

uint32_t DrmWindows::importHudFb(DrmPanel& p, const sp<GraphicBuffer>& gb) {
    // Same import as the game buffers, cached separately so the HUD can go away
    // without touching the panel's cache.
    auto it = p.hudFbCache.find(gb->getId());
    if (it != p.hudFbCache.end()) return it->second;
    const uint32_t fb = importFb(p, gb);
    if (fb) {
        p.hudFbCache[gb->getId()] = fb;
        p.fbCache.erase(gb->getId());
    }
    return fb;
}

// Adds the HUD plane's state to a panel commit: the newest HUD buffer while the
// HUD is on, or an unbound plane once when it was turned off.
void DrmWindows::addHudProps(DrmPanel& p, drmModeAtomicReqPtr req) {
    if (!p.hudPlaneId || p.hudBroken) return;
    const bool on = p.hudActive->load() && p.hudConsumer != nullptr;
    if (on) {
        BufferItem item;
        while (p.hudConsumer->acquireBuffer(&item, 0, false) == NO_ERROR) {
            if (p.hudHasPending) p.hudConsumer->releaseBuffer(p.hudPending);
            p.hudPending = item;
            p.hudHasPending = true;
        }
        const BufferItem* show = p.hudHasPending ? &p.hudPending : (p.hudHasOnScreen ? &p.hudOnScreen : nullptr);
        if (!show || show->mGraphicBuffer == nullptr) return;
        const uint32_t fb = importHudFb(p, show->mGraphicBuffer);
        if (!fb) return;
        const uint32_t w = show->mGraphicBuffer->getWidth(), h = show->mGraphicBuffer->getHeight();
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPFbId, fb);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcId, p.crtcId);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPSrcX, 0);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPSrcY, 0);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPSrcW, (uint64_t)w << 16);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPSrcH, (uint64_t)h << 16);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcX, 0);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcY, 0);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcW, w);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcH, h);
        if (p.hPAlpha) drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPAlpha, p.hudAlphaMax);
        if (p.hPBlend && p.hudBlendCoverage) drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPBlend, p.hudBlendCoverage);
        if (p.hPZpos) drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPZpos, p.hudZpos);
        if (p.hPRotation) drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPRotation, DRM_MODE_ROTATE_0);
        p.hudCleared = false;
        p.hudInCommit = true;
    } else if (!p.hudCleared) {
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPFbId, 0);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcId, 0);
        p.hudCleared = true;
        if (p.hudHasPending) { p.hudConsumer->releaseBuffer(p.hudPending); p.hudHasPending = false; }
    }
}

ANativeWindow* DrmWindows::overlayWindow(int idx) {
    if (idx < 0 || idx >= (int)mPanels.size()) return nullptr;
    DrmPanel& p = mPanels[idx];
    if (p.ovSurface == nullptr) createOverlayQueue(p, idx);
    return p.ovSurface.get();
}

void DrmWindows::setOverlayActive(int idx, bool active) {
    if (idx < 0 || idx >= (int)mPanels.size()) return;
    DrmPanel& p = mPanels[idx];
    p.ovActive->store(active);
    const uint64_t one = 1;
    (void)!write(p.wakeFd, &one, sizeof(one));
}

uint32_t DrmWindows::importFb(DrmPanel& p, const sp<GraphicBuffer>& gb) {
    const uint64_t id = gb->getId();
    auto it = p.fbCache.find(id);
    if (it != p.fbCache.end()) return it->second;

    const native_handle_t* nh = gb->handle;
    if (!nh || nh->numFds < 1) return 0;
    uint32_t pitch = gb->getStride() * 4;
    uint64_t modifier = 0;
    auto& gm = GraphicBufferMapper::get();
    uint64_t realMod = 0;
    if (gm.getPixelFormatModifier(nh, &realMod) == OK) modifier = realMod;
    std::vector<ui::PlaneLayout> layouts;
    if (gm.getPlaneLayouts(nh, &layouts) == OK && !layouts.empty() &&
        layouts[0].strideInBytes > 0) {
        pitch = (uint32_t)layouts[0].strideInBytes;
    }
    drm_prime_handle ph{};
    ph.fd = nh->data[0];
    if (ioctl(mFd, DRM_IOCTL_PRIME_FD_TO_HANDLE, &ph) != 0 || !ph.handle) {
        ALOGE("DRM: PRIME import failed: %s", strerror(errno));
        return 0;
    }
    drm_mode_fb_cmd2 cmd{};
    cmd.width = gb->getWidth();
    cmd.height = gb->getHeight();
    cmd.pixel_format = gb->getPixelFormat() == PIXEL_FORMAT_RGBX_8888 ? DRM_FORMAT_XBGR8888
                                                                       : DRM_FORMAT_ABGR8888;
    cmd.handles[0] = ph.handle;
    cmd.pitches[0] = pitch;
    if (modifier) {
        cmd.flags = DRM_MODE_FB_MODIFIERS;
        cmd.modifier[0] = modifier;
    }
    if (ioctl(mFd, DRM_IOCTL_MODE_ADDFB2, &cmd) != 0 || !cmd.fb_id) {
        ALOGE("DRM: ADDFB2 failed (%ux%u pitch %u mod 0x%llx): %s", cmd.width, cmd.height, pitch,
              (unsigned long long)modifier, strerror(errno));
        drm_gem_close gc{};
        gc.handle = ph.handle;
        ioctl(mFd, DRM_IOCTL_GEM_CLOSE, &gc);
        return 0;
    }
    ALOGI("DRM: imported buffer %" PRIu64 " as fb %u (%ux%u pitch %u mod 0x%llx)", id, cmd.fb_id,
          cmd.width, cmd.height, pitch, (unsigned long long)modifier);
    p.fbCache[id] = cmd.fb_id;
    return cmd.fb_id;
}

// Debug capture: sys.gammaos.azahar_nano.shot=<prefix> dumps what every panel is
// scanning out right now to <prefix>_<idx>.ppm (the on-screen buffers, so a paused
// or static panel is captured too), then clears the property.
void DrmWindows::captureAll() {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.azahar_nano.shot", v, "");
    if (!v[0]) return;
    property_set("sys.gammaos.azahar_nano.shot", "");
    auto dump = [&](const sp<GraphicBuffer>& gb, const std::string& path) {
        void* ptr = nullptr;
        if (gb->lock(GRALLOC_USAGE_SW_READ_RARELY, &ptr) != OK || !ptr) {
            ALOGE("DRM: shot lock failed");
            return;
        }
        const uint32_t w = gb->getWidth(), h = gb->getHeight(), stride = gb->getStride();
        FILE* f = fopen(path.c_str(), "wb");
        if (f) {
            fprintf(f, "P6\n%u %u\n255\n", w, h);
            std::vector<uint8_t> row(w * 3);
            for (uint32_t y = 0; y < h; y++) {
                const uint8_t* src = (const uint8_t*)ptr + (size_t)y * stride * 4;
                for (uint32_t x = 0; x < w; x++) {
                    row[x * 3] = src[x * 4];
                    row[x * 3 + 1] = src[x * 4 + 1];
                    row[x * 3 + 2] = src[x * 4 + 2];
                }
                fwrite(row.data(), 1, row.size(), f);
            }
            fclose(f);
            ALOGI("DRM: shot %s", path.c_str());
        }
        gb->unlock();
    };
    for (size_t i = 0; i < mPanels.size(); i++) {
        DrmPanel& p = mPanels[i];
        sp<GraphicBuffer> gb, hud;
        {
            std::lock_guard<std::mutex> lk(*p.mu);
            if (p.hasOnScreen) gb = p.onScreen.mGraphicBuffer;
            if (p.hudActive->load() && p.hudHasOnScreen) hud = p.hudOnScreen.mGraphicBuffer;
        }
        if (gb == nullptr) {
            ALOGW("DRM: shot: panel %zu has nothing on screen yet", i);
            continue;
        }
        dump(gb, std::string(v) + "_" + std::to_string(i) + ".ppm");
        // The HUD plane is blended over the game by the display hardware, so it
        // is written as its own file (the strip's alpha is dropped, black = clear).
        if (hud != nullptr) dump(hud, std::string(v) + "_" + std::to_string(i) + "_hud.ppm");
    }
}

bool DrmWindows::flip(DrmPanel& p, BufferItem& item) {
    const uint32_t fb = importFb(p, item.mGraphicBuffer);
    if (!fb) return false;
    drmModeAtomicReqPtr req = drmModeAtomicAlloc();
    const uint32_t w = item.mGraphicBuffer->getWidth();
    const uint32_t h = item.mGraphicBuffer->getHeight();
    drmModeAtomicAddProperty(req, p.planeId, p.pFbId, fb);
    const bool full = !p.geometrySet || p.srcW != w || p.srcH != h || !mMinimalFlips;
    if (full) {
    drmModeAtomicAddProperty(req, p.planeId, p.pCrtcId, p.crtcId);
    drmModeAtomicAddProperty(req, p.planeId, p.pSrcX, 0);
    drmModeAtomicAddProperty(req, p.planeId, p.pSrcY, 0);
    drmModeAtomicAddProperty(req, p.planeId, p.pSrcW, (uint64_t)w << 16);
    drmModeAtomicAddProperty(req, p.planeId, p.pSrcH, (uint64_t)h << 16);
    drmModeAtomicAddProperty(req, p.planeId, p.pCrtcX, 0);
    drmModeAtomicAddProperty(req, p.planeId, p.pCrtcY, 0);
    drmModeAtomicAddProperty(req, p.planeId, p.pCrtcW, p.width);
    drmModeAtomicAddProperty(req, p.planeId, p.pCrtcH, p.height);
    if (p.pRotation && p.rotationValue) {
        drmModeAtomicAddProperty(req, p.planeId, p.pRotation, p.rotationValue);
    }
    }
    addHudProps(p, req);
    int fenceFd = -1;
    if (p.pInFence && item.mFence != nullptr && item.mFence->isValid()) {
        fenceFd = item.mFence->dup();
        if (fenceFd >= 0) drmModeAtomicAddProperty(req, p.planeId, p.pInFence, fenceFd);
    }
    p.commitNs = monoNs();
    p.queuedNs = item.mTimestamp;
    const uint32_t flags = mBlockingCommits ? 0u : (DRM_MODE_ATOMIC_NONBLOCK | DRM_MODE_PAGE_FLIP_EVENT);
    int rc = drmModeAtomicCommit(mFd, req, flags, reinterpret_cast<void*>((uintptr_t)p.crtcId));
    int err = errno;
    if (rc != 0 && p.hudInCommit) {
        // The HUD plane state was refused: drop the HUD for this session (the panel
        // must keep flipping) and retry the same frame without it.
        ALOGE("DRM: commit with the HUD plane refused on crtc %u (%s); HUD disabled", p.crtcId,
              strerror(err));
        p.hudActive->store(false);
        p.hudBroken = true;
        drmModeAtomicFree(req);
        req = drmModeAtomicAlloc();
        drmModeAtomicAddProperty(req, p.planeId, p.pFbId, fb);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPFbId, 0);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcId, 0);
        p.hudCleared = true;
        if (p.hudHasPending) { p.hudConsumer->releaseBuffer(p.hudPending); p.hudHasPending = false; }
        if (fenceFd >= 0) {
            close(fenceFd);
            fenceFd = item.mFence != nullptr && item.mFence->isValid() ? item.mFence->dup() : -1;
            if (fenceFd >= 0 && p.pInFence) drmModeAtomicAddProperty(req, p.planeId, p.pInFence, fenceFd);
        }
        rc = drmModeAtomicCommit(mFd, req, flags, reinterpret_cast<void*>((uintptr_t)p.crtcId));
        err = errno;
    }
    p.hudInCommit = false;
    p.ioctlNs = monoNs() - p.commitNs;
    drmModeAtomicFree(req);
    if (fenceFd >= 0) close(fenceFd);
    if (rc != 0) {
        ALOGE("DRM: flip commit failed on crtc %u: %s", p.crtcId, strerror(err));
        return false;
    }
    p.geometrySet = true;
    p.srcW = w;
    p.srcH = h;
    p.pending = item;
    p.hasPending = true;
    p.flips++;
    if (mBlockingCommits) {
        // The blocking commit returned once the flip landed.
        onFlipDone(p.crtcId, monoNs());
        // The DP bridge panel locks into a self-sustaining stall when its next
        // commit lands right behind the previous one (the same lock SurfaceFlinger
        // needed the follower hold for); a short phase offset after each flip
        // keeps the next commit out of that window.
        if (!p.isDsi && mDpPostFlipUs > 0) usleep(mDpPostFlipUs);
    } else {
        p.flipPending = true;
    }
    return true;
}

void DrmWindows::onFlipDone(unsigned int crtcId, int64_t tsNs) {
    for (auto& p : mPanels) {
        if (p.crtcId != crtcId) continue;
        std::lock_guard<std::mutex> lk(*p.mu);
        p.flipPending = false;
        if (mTraceLeft > 0) {
            mTraceLeft--;
            // Absolute timeline (ms modulo 100 s) so both panels can be correlated.
            auto ms = [](int64_t ns) { return (double)(ns % 100000000000LL) / 1e6; };
            ALOGI("DRM trace: crtc %u ts %.2f sig %.2f acq %.2f commit %.2f flip %.2f (ioctl %.2f ms, gap %.2f ms) pick %d/%d",
                  p.crtcId, ms(p.queuedNs), ms(p.signalNs), ms(p.acquiredNs), ms(p.commitNs), ms(tsNs),
                  p.ioctlNs / 1e6, (tsNs - p.lastFlipNs) / 1e6, p.pickIndex, p.readyCount);
        }
        {
            const int64_t lat = tsNs - p.commitNs;
            p.latSumNs += lat;
            p.latCount++;
            if (lat > p.latMaxNs) p.latMaxNs = lat;
        }
        if (p.lastFlipNs) {
            const int64_t dt = tsNs - p.lastFlipNs;
            if (dt > p.maxGapNs) p.maxGapNs = dt;
            if (dt > 20 * 1000000LL) p.longGaps++;
            // Refresh period estimate from back-to-back flips only (a skipped
            // vblank is a multiple of the period and would bias the mean).
            if (dt > 12 * 1000000LL && dt < 22 * 1000000LL) {
                p.periodSumNs += dt;
                p.periodCount++;
                if (p.periodCount >= 64) {
                    p.measuredPeriodNs->store((double)p.periodSumNs / (double)p.periodCount);
                }
                if (p.periodCount >= 4096) {   // keep the mean responsive
                    p.periodSumNs /= 2;
                    p.periodCount /= 2;
                }
            }
        }
        p.lastFlipNs = tsNs;
        // The buffer that was on screen is now free.
        if (p.hasOnScreen) {
            (p.onScreenIsOverlay ? p.ovConsumer : p.consumer)->releaseBuffer(p.onScreen);
            p.hasOnScreen = false;
        }
        if (p.hasPending) {
            p.onScreen = p.pending;
            p.onScreenIsOverlay = p.pendingIsOverlay;
            p.hasOnScreen = true;
            p.hasPending = false;
        }
        if (p.hudHasPending) {
            if (p.hudHasOnScreen) p.hudConsumer->releaseBuffer(p.hudOnScreen);
            p.hudOnScreen = p.hudPending;
            p.hudHasOnScreen = true;
            p.hudHasPending = false;
        }
        if (!mBlockingCommits) {
            const uint64_t one = 1;
            (void)!write(p.wakeFd, &one, sizeof(one));
        }
    }
}

// Flip completion events (nonblocking mode) are read by the event thread and
// handed to the panel's own presenter through its flag.
void DrmWindows::eventLoop() {
    {
        sched_param sp{};
        sp.sched_priority = 2;
        sched_setscheduler(0, SCHED_FIFO, &sp);
    }
    static DrmWindows* self = nullptr;
    self = this;
    drmEventContext ev{};
    ev.version = DRM_EVENT_CONTEXT_VERSION;
    ev.page_flip_handler = [](int, unsigned int sec, unsigned int usec, unsigned int, void* data) {
        // The SDE event carries whole seconds only (usec stays 0), so the poll
        // wake-up time is the usable timestamp; the period mean averages its jitter.
        (void)sec; (void)usec;
        self->onFlipDone((unsigned int)(uintptr_t)data, monoNs());
    };
    while (mRun) {
        pollfd pfd{mFd, POLLIN, 0};
        const int r = poll(&pfd, 1, 50);
        if (r > 0 && (pfd.revents & POLLIN)) drmHandleEvent(mFd, &ev);
    }
}

// Commits are NONBLOCKING with a page flip event (default): on this SoC a blocking
// atomic ioctl on one CRTC waits behind the other CRTC's commit in the kernel, so
// with two panels on drifting clocks the two presenters locked each other into a
// 33 ms cadence for tens of seconds at a time (measured: 40 flips/s with a gap
// every other frame in the bad phase, 60 flips/s and 0 gaps nonblocking).
//
// Presenter policy: frames are acquired the moment they are queued (so the
// core never waits for a free buffer) and shown in order, one per vblank, as
// soon as their GPU fence has signalled. The emulator's swaps are bursty (a
// heavy 3DS frame and a light one back to back, then the limiter sleeps), so
// the queue keeps up to two frames of cushion; beyond that the oldest frames
// are dropped to cap latency. A frame still rendering is never committed: on
// this SoC the commit would slip a whole vblank waiting for it.
void DrmWindows::panelLoop(int idx) {
    DrmPanel& p = mPanels[idx];
    {
        // The flip must not wait behind the emulator's busy threads: the commit
        // deadline is the panel's vblank.
        sched_param sp{};
        sp.sched_priority = 2;
        if (sched_setscheduler(0, SCHED_FIFO, &sp) != 0) {
            ALOGW("DRM: presenter %d SCHED_FIFO failed: %s", idx, strerror(errno));
        }
    }
    int64_t lastReport = 0, lastShotPoll = 0;
    std::vector<BufferItem> ready;   // acquired frames in arrival order
    bool readyIsOverlay = false;     // which consumer the candidates came from
    auto releaseReady = [&](const BufferItem& it) {
        (readyIsOverlay ? p.ovConsumer : p.consumer)->releaseBuffer(it);
    };
    auto signalled = [](const BufferItem& it) {
        return it.mFence == nullptr || !it.mFence->isValid() ||
               it.mFence->getSignalTime() != Fence::SIGNAL_TIME_PENDING;
    };
    while (mRun) {
        // Source switch (menu opened or closed): drop the other source's backlog.
        const bool wantOverlay = p.ovActive->load() && p.ovConsumer != nullptr;
        if (wantOverlay != readyIsOverlay) {
            for (auto& it : ready) releaseReady(it);
            ready.clear();
            readyIsOverlay = wantOverlay;
        }
        // While the overlay is shown the core (paused) may still queue a frame:
        // return it straight away so it never blocks.
        BufferItem item;
        if (wantOverlay) {
            while (p.consumer->acquireBuffer(&item, 0, false) == NO_ERROR) {
                p.consumer->releaseBuffer(item);
            }
        } else if (p.ovConsumer != nullptr) {
            while (p.ovConsumer->acquireBuffer(&item, 0, false) == NO_ERROR) {
                p.ovConsumer->releaseBuffer(item);
            }
        }
        // Pull everything the active source has queued; beyond the cushion the
        // oldest frames are dropped.
        const sp<BufferItemConsumer>& src = wantOverlay ? p.ovConsumer : p.consumer;
        while (src->acquireBuffer(&item, 0, false) == NO_ERROR) {
            ready.push_back(item);
            while (ready.size() > kMaxReady) {
                releaseReady(ready.front());
                ready.erase(ready.begin());
                p.drops++;
            }
        }
        bool pending;
        {
            std::lock_guard<std::mutex> lk(*p.mu);
            pending = p.flipPending;
        }
        int pickFd = -1;
        if (!pending && !ready.empty()) {
            // Oldest frame first, provided its GPU work is done.
            int pick = signalled(ready.front()) ? 0 : -1;
            if (pick >= 0) {
                BufferItem latest = ready[pick];
                p.pickIndex = pick;
                p.readyCount = (int)ready.size();
                p.signalNs = (latest.mFence != nullptr && latest.mFence->isValid())
                                 ? latest.mFence->getSignalTime() : 0;
                ready.erase(ready.begin(), ready.begin() + pick + 1);
                p.acquiredNs = monoNs();
                p.cbNs = p.lastAvailNs->load();
                p.pendingIsOverlay = readyIsOverlay;
                if (!flip(p, latest)) releaseReady(latest);
                continue;   // re-evaluate at once: a frame may have arrived meanwhile
            }
            // Nothing complete yet: wait for the oldest candidate's fence too.
            if (ready.front().mFence != nullptr && ready.front().mFence->isValid()) {
                pickFd = ready.front().mFence->dup();
            }
        }
        const int64_t now = monoNs();
        if (idx == 0 && now - lastShotPoll > 200000000LL) {
            lastShotPoll = now;
            captureAll();
        }
        if (now - lastReport > 2000000000LL) {
            lastReport = now;
            if (idx == 0) {
                const int tr = property_get_int32("sys.gammaos.azahar_nano.drm_trace", 0);
                if (tr > 0) {
                    mTraceLeft = tr * (int)mPanels.size();
                    property_set("sys.gammaos.azahar_nano.drm_trace", "0");
                }
            }
            ALOGI("DRM: crtc %u flips=%" PRIu64 " drops=%" PRIu64 " avail_cb=%" PRIu64 " gaps>20ms=%" PRIu64
                  " maxgap=%.1fms commit->flip avg %.1f max %.1f ms",
                  p.crtcId, p.flips, p.drops, p.availCount->load(), p.longGaps, p.maxGapNs / 1e6,
                  p.latCount ? p.latSumNs / 1e6 / p.latCount : 0.0, p.latMaxNs / 1e6);
            ALOGI("DRM: crtc %u producer cadence <10:%u 10-14:%u 14-20:%u 20-28:%u >28:%u ms",
                  p.crtcId, (*p.cadence)[0].exchange(0), (*p.cadence)[1].exchange(0),
                  (*p.cadence)[2].exchange(0), (*p.cadence)[3].exchange(0),
                  (*p.cadence)[4].exchange(0));
            p.maxGapNs = 0;
            p.latSumNs = 0; p.latMaxNs = 0; p.latCount = 0;
        }
        // Wait for a new frame (eventfd), a flip event (nonblocking mode, the
        // event thread writes the eventfd) or the pending candidate's fence.
        pollfd pfd[2] = {{p.wakeFd, POLLIN, 0}, {pickFd, POLLIN, 0}};
        const int r = poll(pfd, pickFd >= 0 ? 2 : 1, 20);
        if (r > 0 && (pfd[0].revents & POLLIN)) {
            uint64_t n = 0;
            (void)!read(p.wakeFd, &n, sizeof(n));
        }
        if (pickFd >= 0) close(pickFd);
    }
    for (auto& it : ready) releaseReady(it);
}

int DrmWindows::create() {
    mMinimalFlips = property_get_bool("persist.gammaos.azahar_nano.drm_minimal", true);
    if (!openDevice() || !enumerate()) return 0;
    // Optional 180 degree correction for the DSI panel (plane reflect x+y).
    char rot[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.azahar_nano.drm_top_rot180", rot, "0");
    if (rot[0] == '1' && !mPanels.empty() && mPanels[0].pRotation) {
        mPanels[0].rotationValue = DRM_MODE_ROTATE_180;
    }
    if (!modeset()) return 0;
    // SurfaceFlinger's GammaOS composition gate: it stops presenting while a
    // DRM-direct client owns the panels (same flag gammaos-nano sets).
    property_set("sys.gammaos.nano.drm_active", "1");
    mBlockingCommits = property_get_bool("persist.gammaos.azahar_nano.drm_blocking", false);
    mDpPostFlipUs = property_get_int32("persist.gammaos.azahar_nano.drm_dp_postflip_us", 0);
    for (size_t i = 0; i < mPanels.size(); i++) createQueue(mPanels[i], (int)i);
    mRun = true;
    if (!mBlockingCommits) mThread = std::thread([this] { eventLoop(); });
    for (size_t i = 0; i < mPanels.size(); i++) {
        mPanelThreads.emplace_back([this, i] { panelLoop((int)i); });
    }
    ALOGI("DRM: %s commits, in-order signalled-frame presenter per panel",
          mBlockingCommits ? "blocking" : "nonblocking");
    ALOGI("DRM backend up: %zu panel(s)", mPanels.size());
    return (int)mPanels.size();
}

ANativeWindow* DrmWindows::window(int idx) const {
    if (idx < 0 || idx >= (int)mPanels.size()) return nullptr;
    return mPanels[idx].surface.get();
}

void DrmWindows::teardown() {
    property_set("sys.gammaos.nano.drm_active", "0");
    mRun = false;
    for (auto& p : mPanels) {
        if (p.wakeFd >= 0) {
            const uint64_t one = 1;
            (void)!write(p.wakeFd, &one, sizeof(one));
        }
    }
    for (auto& t : mPanelThreads) if (t.joinable()) t.join();
    mPanelThreads.clear();
    if (mThread.joinable()) mThread.join();
    for (auto& p : mPanels) {
        ALOGI("DRM: panel crtc %u flips=%" PRIu64 " drops=%" PRIu64, p.crtcId, p.flips, p.drops);
        for (auto& kv : p.fbCache) drmModeRmFB(mFd, kv.second);
        p.fbCache.clear();
        p.surface.clear();
        p.consumer.clear();
        p.listener.clear();
        p.ovSurface.clear();
        p.ovConsumer.clear();
        p.ovProducer.clear();
        p.ovListener.clear();
        for (auto& kv : p.hudFbCache) drmModeRmFB(mFd, kv.second);
        p.hudFbCache.clear();
        p.hudSurface.clear();
        p.hudConsumer.clear();
        p.hudProducer.clear();
        p.hudListener.clear();
        if (p.wakeFd >= 0) {
            close(p.wakeFd);
            p.wakeFd = -1;
        }
        p.producer.clear();
        if (p.modeBlob) drmModeDestroyPropertyBlob(mFd, p.modeBlob);
    }
    for (uint32_t fb : mBlackFbs) if (fb) drmModeRmFB(mFd, fb);
    mBlackFbs.clear();
    mPanels.clear();
    if (mFd >= 0) {
        drmDropMaster(mFd);
        close(mFd);
        mFd = -1;
    }

}

} // namespace azahar_nano
