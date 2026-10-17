#define LOG_TAG "azahar-nano"

#include "DrmWindows.h"
#include "AzaharSettings.h"

#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>

#include <algorithm>
#include <cmath>
#include <chrono>
#include <climits>
#include <fstream>
#include <map>
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

// Qualcomm SDE exposes the two rects of one source pipe (smart DMA) as two DRM planes: the second
// carries "primary_smart_plane_id=<first>" in its capabilities blob. One pipe must never feed two
// CRTCs: a rect on each panel leaves the pipe fetching for two timing engines at once, and the
// panel it falls behind on shows the interface's blue underflow fill in a band that rolls at the
// beat of the two refresh rates (60.000 vs 59.983 Hz on the RP Duo Lite). Whether it holds depends
// on the multirect mode the previous owner left on the pipe, so it showed only after some sessions.
// Returns the plane's partner rect, 0 when it has none.
uint32_t smartPartner(int fd, uint32_t planeId) {
    static std::map<uint32_t, uint32_t> partners;
    static bool built = false;
    if (!built) {
        built = true;
        drmModePlaneResPtr pr = drmModeGetPlaneResources(fd);
        if (!pr) return 0;
        for (uint32_t i = 0; i < pr->count_planes; i++) {
            const uint32_t id = pr->planes[i];
            const uint32_t capProp = findProp(fd, id, DRM_MODE_OBJECT_PLANE, "capabilities");
            if (!capProp) continue;
            bool found = false;
            const uint64_t blobId = getPropValue(fd, id, DRM_MODE_OBJECT_PLANE, "capabilities", &found);
            if (!found || !blobId) continue;
            drmModePropertyBlobPtr blob = drmModeGetPropertyBlob(fd, (uint32_t)blobId);
            if (!blob) continue;
            const std::string caps((const char*)blob->data, blob->length);
            drmModeFreePropertyBlob(blob);
            const size_t at = caps.find("primary_smart_plane_id=");
            if (at == std::string::npos) continue;
            const uint32_t primary = (uint32_t)strtoul(caps.c_str() + at + 23, nullptr, 10);
            if (!primary) continue;
            partners[id] = primary;
            partners[primary] = id;
        }
        drmModeFreePlaneResources(pr);
        for (const auto& [a, b] : partners) if (a < b) ALOGI("DRM: planes %u and %u share one source pipe", a, b);
    }
    auto it = partners.find(planeId);
    return it == partners.end() ? 0 : it->second;
}

// The plane can scale (SDE capabilities "max_upscale" above 1: the VIG pipe on the Duo Lite).
bool planeCanScale(int fd, uint32_t planeId) {
    static std::map<uint32_t, bool> cache;
    auto it = cache.find(planeId);
    if (it != cache.end()) return it->second;
    bool scales = false;
    bool found = false;
    const uint64_t blobId = getPropValue(fd, planeId, DRM_MODE_OBJECT_PLANE, "capabilities", &found);
    if (found && blobId) {
        if (drmModePropertyBlobPtr blob = drmModeGetPropertyBlob(fd, (uint32_t)blobId)) {
            const std::string caps((const char*)blob->data, blob->length);
            drmModeFreePropertyBlob(blob);
            const size_t at = caps.find("max_upscale=");
            if (at != std::string::npos) scales = strtoul(caps.c_str() + at + 12, nullptr, 10) > 1;
        }
    }
    cache[planeId] = scales;
    return scales;
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

// The panel's mount rotation in degrees, the way the home reads it (NanoMenuDrm
// drmPanelMountRotationDeg): port 0 is SurfaceFlinger's primary and follows
// ro.surface_flinger.primary_display_orientation, the others persist.gsf.rot.<port>
// (persist.gsf.sec_rot for all secondaries). Only 0 and 180 are acted on here.
static int panelMountDeg(int port) {
    auto toDeg = [](const char* s) -> int {
        if (!strcmp(s, "ORIENTATION_90") || !strcmp(s, "90")) return 90;
        if (!strcmp(s, "ORIENTATION_180") || !strcmp(s, "180")) return 180;
        if (!strcmp(s, "ORIENTATION_270") || !strcmp(s, "270")) return 270;
        return 0;
    };
    char val[PROPERTY_VALUE_MAX] = {};
    int deg = 0;
    if (port <= 0) {
        if (property_get("ro.surface_flinger.primary_display_orientation", val, "") > 0) deg = toDeg(val);
    } else {
        char key[PROPERTY_KEY_MAX];
        snprintf(key, sizeof(key), "persist.gsf.rot.%d", port);
        if (property_get(key, val, "") > 0) deg = toDeg(val);
        else if (property_get("persist.gsf.sec_rot", val, "") > 0) deg = toDeg(val);
    }
    // 90/270 mounts are the install rotation the upright layout already covers.
    return deg == 180 ? 180 : 0;
}

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
    // Master is taken when the composer lets go (the Duo Lite's does, through its shim,
    // within the wait below). On the Rockchip kernels (RG DS, RG DS Plus) the composer
    // keeps master for good and SET_MASTER answers EBUSY, yet that kernel accepts atomic
    // commits from a root client without master, the way the home and drastic-nano run
    // there. An empty TEST_ONLY commit tells the two apart: EACCES means this kernel wants
    // a master (keep waiting), anything else means commits work and the wait is pointless.
    auto commitsWithoutMaster = [this]() {
        drmModeAtomicReqPtr req = drmModeAtomicAlloc();
        if (!req) return false;
        const int r = drmModeAtomicCommit(mFd, req, DRM_MODE_ATOMIC_TEST_ONLY, nullptr);
        const int e = errno;
        drmModeAtomicFree(req);
        return !(r != 0 && e == EACCES);
    };
    const int64_t t0 = monoNs();
    int rc = -1;
    bool masterless = false;
    for (int attempt = 0; attempt < 50; attempt++) {
        rc = drmSetMaster(mFd);
        if (rc == 0) break;
        if (errno == EBUSY && attempt == 2 && commitsWithoutMaster()) {
            masterless = true;
            break;
        }
        usleep(100 * 1000);
    }
    const int waitedMs = (int)((monoNs() - t0) / 1000000);
    if (rc == 0) {
        ALOGI("DRM: master after %d ms", waitedMs);
    } else if (masterless) {
        ALOGW("DRM: SET_MASTER busy and this kernel takes commits without master; continuing after %d ms",
              waitedMs);
    } else {
        ALOGE("DRM: SET_MASTER failed after %d ms: %s", waitedMs, strerror(errno));
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
            if (crtcId) {
                // SurfaceFlinger numbers the connected panels in this order (port 0, 1): the
                // vendor's persist.gsf.rot.<port> says how each one is mounted.
                p.portIndex = (int)found.size();
                p.mountDeg = panelMountDeg(p.portIndex);
                if (p.mountDeg) ALOGI("DRM: connector %u mounted turned by %d degrees (port %d)", p.connId, p.mountDeg, p.portIndex);
                found.push_back(p);
            }
        }
        drmModeFreeConnector(c);
    }
    drmModeFreeResources(res);
    // DSI (the built-in top panel) first.
    std::stable_sort(found.begin(), found.end(),
                     [](const DrmPanel& a, const DrmPanel& b) { return a.isDsi && !b.isDsi; });
    // Two built-in panels: persist.gammaos.nano.primary_display names the top one the way
    // the home reads it on its DRM path, as an index into the connectors in this order
    // (1 on the RG DS, whose first DSI connector is the bottom panel). A port number
    // (130 on the Duo Lite) is out of range and leaves the order alone.
    {
        char prim[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.primary_display", prim, "0");
        const int idx = atoi(prim);
        if (idx > 0 && idx < (int)found.size()) {
            std::rotate(found.begin(), found.begin() + idx, found.begin() + idx + 1);
            ALOGI("DRM: primary panel from persist.gammaos.nano.primary_display=%d", idx);
        }
    }
    // Two panels: the scaling pipe (only one on the Duo Lite, and a pipe never feeds both panels)
    // is kept for the first (top) panel, whose LCD shader output it scales up; the second panel
    // scans out at its full size. Which panel got it used to depend on the planes the previous
    // owner left behind.
    mReserveScalerForFirst = found.size() >= 2;
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
    int bestType = INT_MIN;   // a penalised plane is still the last resort
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
            // The second of two panels leaves the scaling planes to the first.
            if (mReserveScalerForFirst && mPanels.size() == 1 && planeCanScale(mFd, pl->plane_id)) score -= 8;
            // Skip a plane already bound to another panel, and the other rect of its pipe.
            bool taken = false;
            const uint32_t partner = smartPartner(mFd, pl->plane_id);
            for (const auto& q : mPanels) taken |= (q.planeId == pl->plane_id || (partner && q.planeId == partner));
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
    // Planes the previous master (the composer, after a SurfaceFlinger app) left on our CRTCs keep
    // scanning out its last frames under ours, and a plane attached anywhere counts as taken: a
    // VIG plane left behind kept the top panel off the only scaling pipe (switchToScalingPlane),
    // so the LCD shader output was not scaled after an SF session. Switch them off in this same
    // commit; the panels' own planes are set above.
    const int panelProps = drmModeAtomicGetCursor(req);
    int detached = 0;
    if (drmModePlaneResPtr pr = drmModeGetPlaneResources(mFd)) {
        for (uint32_t i = 0; i < pr->count_planes; i++) {
            drmModePlanePtr pl = drmModeGetPlane(mFd, pr->planes[i]);
            if (!pl) continue;
            const uint32_t id = pl->plane_id, crtc = pl->crtc_id;
            drmModeFreePlane(pl);
            if (!crtc) continue;
            bool ours = false, onOurCrtc = false;
            for (const auto& p : mPanels) {
                ours |= (p.planeId == id || p.hudPlaneId == id);
                onOurCrtc |= (p.crtcId == crtc);
            }
            if (ours || !onOurCrtc) continue;
            const uint32_t fbProp = findProp(mFd, id, DRM_MODE_OBJECT_PLANE, "FB_ID");
            const uint32_t crtcProp = findProp(mFd, id, DRM_MODE_OBJECT_PLANE, "CRTC_ID");
            if (!fbProp || !crtcProp) continue;
            drmModeAtomicAddProperty(req, id, fbProp, 0);
            drmModeAtomicAddProperty(req, id, crtcProp, 0);
            detached++;
            ALOGI("DRM: plane %u left on crtc %u by the previous master, switching it off", id, crtc);
        }
        drmModeFreePlaneResources(pr);
    }
    int rc = drmModeAtomicCommit(mFd, req, needModeset ? DRM_MODE_ATOMIC_ALLOW_MODESET : 0, nullptr);
    if (rc != 0 && detached) {
        // never let the clean up cost the takeover: commit the panels alone, as before
        ALOGW("DRM: initial commit with %d plane(s) switched off failed (%s), retrying without", detached,
              strerror(errno));
        drmModeAtomicSetCursor(req, panelProps);
        rc = drmModeAtomicCommit(mFd, req, needModeset ? DRM_MODE_ATOMIC_ALLOW_MODESET : 0, nullptr);
    }
    drmModeAtomicFree(req);
    if (rc != 0) {
        ALOGE("DRM: initial commit failed (modeset=%d): %s", needModeset, strerror(errno));
        return false;
    }
    ALOGI("DRM: modeset done on %zu panel(s)", mPanels.size());
    return true;
}

// Test-only atomic commit: can this panel's plane scan out a w x h buffer
// stretched over the whole panel?
bool DrmWindows::testScaledScanout(DrmPanel& p, uint32_t w, uint32_t h, const DrmPanel* retire) {
    sp<GraphicBuffer> gb = new GraphicBuffer(w, h, PIXEL_FORMAT_RGBA_8888, 1, p.bufUsage,
                                             "azahar-nano-scaletest");
    if (gb->initCheck() != OK) return false;
    const uint32_t fb = importFb(p, gb);
    if (!fb) return false;
    drmModeAtomicReqPtr req = drmModeAtomicAlloc();
    drmModeAtomicAddProperty(req, p.planeId, p.pFbId, fb);
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
    // The plane the panel moves from goes off in the same commit (the first flip on the new one
    // retires it). Left on, it shared the CRTC's blend stage with the candidate and SDE took the
    // two as a source split pair it rejects ("invalid src split cfg, stage:0 left:94 right:74"):
    // after a SurfaceFlinger session the top panel starts on the DMA plane the composer used, so no
    // scaling plane ever passed and the LCD shader output was not scaled.
    if (retire && retire->planeId && retire->planeId != p.planeId && retire->pFbId && retire->pCrtcId) {
        drmModeAtomicAddProperty(req, retire->planeId, retire->pFbId, 0);
        drmModeAtomicAddProperty(req, retire->planeId, retire->pCrtcId, 0);
    }
    const int rc = drmModeAtomicCommit(mFd, req, DRM_MODE_ATOMIC_TEST_ONLY, nullptr);
    const int err = errno;
    drmModeAtomicFree(req);
    // The test buffer goes away: drop its framebuffer from the cache.
    p.fbCache.erase(gb->getId());
    drmModeRmFB(mFd, fb);
    ALOGI("DRM: plane %u scaled scanout %ux%u -> %ux%u: %s", p.planeId, w, h, p.width, p.height,
          rc == 0 ? "supported" : strerror(err));
    return rc == 0;
}

bool DrmWindows::setLcdOutput(int idx, bool on) {
    if (idx < 0 || idx >= (int)mPanels.size()) return false;
    DrmPanel& p = mPanels[idx];
    const uint32_t w = on ? p.lcdW : p.plainW, h = on ? p.lcdH : p.plainH;
    if (w == 0 || h == 0 || (w == p.bufW && h == p.bufH)) return false;
    // The flip path takes each buffer's own size (the plane geometry follows it), so only
    // the queue's default size changes here; the core then rebuilds its swapchain.
    p.bufW = w;
    p.bufH = h;
    p.consumer->setDefaultBufferSize(w, h);
    p.renderScale = (float)w / (float)p.width;
    ALOGI("DRM: panel %d output now %ux%u (LCD shader %s)", idx, w, h, on ? "on" : "off");
    return true;
}

// The panel's current plane cannot scale: look for another free plane on the
// same CRTC that can (an SDE VIG pipe has a scaler, a DMA pipe does not). The
// old plane is switched off by the first flip on the new one.
bool DrmWindows::switchToScalingPlane(DrmPanel& p) {
    drmModePlaneResPtr pr = drmModeGetPlaneResources(mFd);
    if (!pr) return false;
    const DrmPanel saved = p;
    bool ok = false;
    for (uint32_t i = 0; i < pr->count_planes && !ok; i++) {
        drmModePlanePtr pl = drmModeGetPlane(mFd, pr->planes[i]);
        if (!pl) continue;
        const uint32_t id = pl->plane_id;
        bool fmtOk = false;
        for (uint32_t f = 0; f < pl->count_formats; f++) {
            if (pl->formats[f] == DRM_FORMAT_ABGR8888) fmtOk = true;
        }
        const bool fits = (pl->possible_crtcs & (1u << p.crtcIndex)) != 0;
        // Free: not on screen anywhere (crtc_id 0) and not chosen by any panel, and the other rect
        // of its source pipe not in use on another panel (smartPartner: the scaling VIG pipe is
        // one pipe for planes 74 and 107 on the Duo Lite, so only one panel may scale through it).
        bool taken = pl->crtc_id != 0 || id == saved.planeId;
        // the scaling pipe is kept for the first of two panels (mReserveScalerForFirst)
        if (mReserveScalerForFirst && mPanels.size() >= 2 && &p == &mPanels[1] && planeCanScale(mFd, id)) taken = true;
        const uint32_t partner = smartPartner(mFd, id);
        for (const auto& q : mPanels) {
            taken |= (q.planeId == id || q.hudPlaneId == id);
            if (partner && q.crtcId != p.crtcId && (q.planeId == partner || q.hudPlaneId == partner)) taken = true;
        }
        drmModeFreePlane(pl);
        if (!fits || !fmtOk || taken) continue;
        p.planeId = id;
        if (!loadProps(p)) {
            p = saved;
            continue;
        }
        if (testScaledScanout(p, p.bufW, p.bufH, &saved)) {
            ok = true;
            break;
        }
        p = saved;
    }
    drmModeFreePlaneResources(pr);
    if (ok) {
        p.retirePlaneId = saved.planeId;
        p.retireFbProp = saved.pFbId;
        p.retireCrtcProp = saved.pCrtcId;
        ALOGI("DRM: crtc %u moves from plane %u to scaling plane %u", p.crtcId, saved.planeId,
              p.planeId);
    }
    return ok;
}

bool DrmWindows::createQueue(DrmPanel& p, int idx) {
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&p.producer, &consumer);
    // Game buffers: the CPU read bit forces gralloc to hand out linear buffers
    // (needed for the capture and for planes that cannot scan out UBWC). With
    // persist.gammaos.azahar_nano.ubwc=1 it is dropped and the buffers come back
    // compressed, which the GPU renders into with far less memory traffic; the
    // framebuffer then carries the QCOM compression modifier (importFb).
    p.bufUsage = GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_HW_TEXTURE;
    // Part of the GammaOS tweaks (on by default) on Adreno, where the display scans UBWC
    // out from any pipe; the ubwc property overrides it. The Mali grallocs answer the same
    // usage with AFBC, which only the Rockchip VOP's cluster windows take (and they refuse
    // the linear menu buffer afterwards): linear buffers there.
    const bool tweaks = settings::getBool("persist.gammaos.azahar_nano.tweaks", true);
    const bool adreno = access("/sys/class/kgsl/kgsl-3d0", F_OK) == 0;
    if (!settings::getBool("persist.gammaos.azahar_nano.ubwc", tweaks && adreno)) {
        p.bufUsage |= GRALLOC_USAGE_SW_READ_RARELY;
    }
    p.consumer = new BufferItemConsumer(consumer, p.bufUsage, 2 + kMaxReady, false);
    p.consumer->setName(String8(idx == 0 ? "azahar-nano-top" : "azahar-nano-bottom"));
    // Optional reduced output size: the core draws its final image into smaller
    // buffers and the display plane scales them up to the panel (SRC < CRTC), so
    // the GPU skips most of the full-panel output pass.
    // persist.gammaos.azahar_nano.render_scale_top / _bottom: percent, 50..100.
    {
        const int pct = std::clamp(settings::getInt(idx == 0 ? "persist.gammaos.azahar_nano.render_scale_top"
                                                               : "persist.gammaos.azahar_nano.render_scale_bottom",
                                                      100), 50, 100);
        const uint32_t shortSide = std::min(p.width, p.height);
        // Buffer size for a wanted short side, keeping the panel's aspect ratio so the
        // core's layout maths is unchanged (0 or too big = the render_scale size).
        const auto sizeFor = [&](uint32_t want, uint32_t& w, uint32_t& h) {
            w = std::max<uint32_t>(2, (p.width * pct / 100) & ~1u);
            h = std::max<uint32_t>(2, (p.height * pct / 100) & ~1u);
            if (want > 0 && want < shortSide) {
                const double k = (double)want / (double)shortSide;
                w = std::max<uint32_t>(2, ((uint32_t)std::lround(p.width * k) + 1) & ~1u);
                h = std::max<uint32_t>(2, ((uint32_t)std::lround(p.height * k) + 1) & ~1u);
            }
        };
        uint32_t want = (idx < 2) ? mOutputShortSide[idx] : 0;
        if (idx < 2 && mOutputScreenW[idx] && mOutputScreenH[idx]) {
            // The smallest buffer at the panel's aspect ratio the emulated screen fits 1:1.
            const uint32_t longSide = std::max(p.width, p.height);
            const uint32_t forWidth =
                (uint32_t)((uint64_t)mOutputScreenW[idx] * shortSide + longSide - 1) / longSide;
            want = std::max(mOutputScreenH[idx], forWidth);
            want = (want + 1) & ~1u;
        }
        sizeFor(want, p.bufW, p.bufH);
        if (want > 0 && want < shortSide) {
            ALOGI("DRM: panel %d native output: short side %u for emulated height %u", idx,
                  std::min(p.bufW, p.bufH), want);
        }
        // Not every plane can scale (the Duo Lite DP pipe refuses SRC != CRTC):
        // prove it with a test-only commit before handing smaller buffers out.
        if ((p.bufW != p.width || p.bufH != p.height) && !testScaledScanout(p, p.bufW, p.bufH) &&
            !switchToScalingPlane(p)) {
            ALOGW("DRM: panel %d has no plane that scales %ux%u to %ux%u, using full-size output",
                  idx, p.bufW, p.bufH, p.width, p.height);
            p.bufW = p.width;
            p.bufH = p.height;
        }
        p.plainW = p.bufW;
        p.plainH = p.bufH;
        // The LCD size, tested now: the live switch must not run a test commit while the
        // panel is flipping.
        p.lcdW = p.plainW;
        p.lcdH = p.plainH;
        // never below the native size: the LCD grid only needs more output pixels
        const uint32_t lcdWant = (idx < 2) ? std::max(mLcdShortSide[idx], want) : 0;
        if (lcdWant > std::min(p.plainW, p.plainH)) {
            uint32_t w = 0, h = 0;
            sizeFor(lcdWant, w, h);
            // a panel that just moved planes still has its old one on until the first flip
            DrmPanel old;
            old.planeId = p.retirePlaneId;
            old.pFbId = p.retireFbProp;
            old.pCrtcId = p.retireCrtcProp;
            if ((w == p.width && h == p.height) || testScaledScanout(p, w, h, p.retirePlaneId ? &old : nullptr)) {
                p.lcdW = w;
                p.lcdH = h;
            }
            ALOGI("DRM: panel %d LCD shader output %ux%u", idx, p.lcdW, p.lcdH);
        }
        if (idx < 2 && mLcdStart[idx]) {
            p.bufW = p.lcdW;
            p.bufH = p.lcdH;
        }
        p.renderScale = (float)p.bufW / (float)p.width;
        ALOGI("DRM: panel %d output %ux%u for %ux%u (%d%%)", idx, p.bufW, p.bufH, p.width, p.height, pct);
    }
    p.consumer->setDefaultBufferSize(p.bufW, p.bufH);
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
// The hardware window each DRM plane belongs to, where the kernel tells (the DRM debugfs
// state names Rockchip VOP2 planes "Esmart1-win2": four areas of one window, which must
// not overlap each other on screen, so an area of the game's window cannot carry the HUD
// over the game). Empty when the file is not there (Qualcomm SDE names nothing).
static std::map<uint32_t, std::string> planeWindowGroups() {
    std::map<uint32_t, std::string> groups;
    std::ifstream in("/sys/kernel/debug/dri/0/state");
    std::string line;
    while (std::getline(in, line)) {
        // "plane[155]: Smart1-win1"
        if (line.rfind("plane[", 0) != 0) continue;
        const size_t close = line.find("]: ");
        if (close == std::string::npos) continue;
        const uint32_t id = (uint32_t)strtoul(line.c_str() + 6, nullptr, 10);
        std::string name = line.substr(close + 3);
        const size_t win = name.find("-win");
        if (win == std::string::npos) continue;   // no area naming: nothing to group
        groups[id] = name.substr(0, win);
    }
    return groups;
}

bool DrmWindows::pickHudPlane(DrmPanel& p) {
    drmModePlaneResPtr pr = drmModeGetPlaneResources(mFd);
    if (!pr) return false;
    uint32_t best = 0;
    int bestScore = -1;
    const std::map<uint32_t, std::string> groups = planeWindowGroups();
    std::string gameGroup;
    if (auto it = groups.find(p.planeId); it != groups.end()) gameGroup = it->second;
    for (uint32_t i = 0; i < pr->count_planes; i++) {
        drmModePlanePtr pl = drmModeGetPlane(mFd, pr->planes[i]);
        if (!pl) continue;
        bool taken = pl->plane_id == p.planeId;
        for (const auto& q : mPanels) taken |= (q.planeId == pl->plane_id || q.hudPlaneId == pl->plane_id);
        // another area of the game's own window never shows over it
        if (!gameGroup.empty()) {
            if (auto it = groups.find(pl->plane_id); it != groups.end() && it->second == gameGroup) {
                taken = true;
            }
        }
        // its pipe's other rect: on another panel it rules the plane out, in use on this panel it
        // is allowed but a pipe of its own is better (no multirect sharing at all)
        const uint32_t partner = smartPartner(mFd, pl->plane_id);
        bool partnerOther = false, partnerUsed = false;
        if (partner) {
            for (const auto& q : mPanels) {
                const bool usesIt = q.planeId == partner || q.hudPlaneId == partner;
                partnerUsed |= usesIt;
                if (usesIt && q.crtcId != p.crtcId) partnerOther = true;
            }
            if (partner == p.planeId) partnerUsed = true;
        }
        taken |= partnerOther;
        const bool fits = (pl->possible_crtcs & (1u << p.crtcIndex)) != 0;
        bool fmtOk = false;
        for (uint32_t f = 0; f < pl->count_formats; f++) if (pl->formats[f] == DRM_FORMAT_ABGR8888) fmtOk = true;
        if (!taken && fits && fmtOk) {
            bool found = false;
            const uint64_t type = getPropValue(mFd, pl->plane_id, DRM_MODE_OBJECT_PLANE, "type", &found);
            // Prefer an overlay plane, then a primary of another CRTC, never a cursor.
            int score = type == DRM_PLANE_TYPE_OVERLAY ? 2 : type == DRM_PLANE_TYPE_PRIMARY ? 1 : -1;
            if (score >= 0 && !partnerUsed) score += 4;   // a source pipe of its own
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
    // The panel's size; the plane only scans out the strips in use (addHudProps).
    p.hudW = p.width;
    p.hudH = p.height;
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

void DrmWindows::setHudRegions(int idx, uint32_t regions) {
    if (idx < 0 || idx >= (int)mPanels.size()) return;
    mPanels[idx].hudRegions->store(regions & (kHudTop | kHudBottom));
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
        // Scan out only the strips in use: the top edge as the user sees it is the
        // buffer's left columns on an upright panel (its top rows otherwise), the
        // bottom edge the right columns (bottom rows). Both: the whole buffer.
        const uint32_t regions = p.hudRegions->load();
        const bool upright = h > w;
        const uint32_t strip = std::min(kHudStrip, upright ? w : h);
        uint32_t x = 0, y = 0, rw = w, rh = h;
        if (regions == kHudTop || regions == kHudBottom) {
            // On a panel mounted upside down the HUD is drawn turned, so the strip along
            // the user's top edge is the buffer's bottom one.
            const bool bottom = (regions == kHudBottom) != (p.mountDeg == 180);
            if (upright) {
                rw = strip;
                x = bottom ? w - strip : 0;
            } else {
                rh = strip;
                y = bottom ? h - strip : 0;
            }
        }
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPFbId, fb);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcId, p.crtcId);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPSrcX, (uint64_t)x << 16);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPSrcY, (uint64_t)y << 16);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPSrcW, (uint64_t)rw << 16);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPSrcH, (uint64_t)rh << 16);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcX, x);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcY, y);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcW, rw);
        drmModeAtomicAddProperty(req, p.hudPlaneId, p.hPCrtcH, rh);
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
    if (!modifier) {
        // QTI gralloc reports UBWC through the compression metadata, not the
        // modifier; without the modifier the display reads the data as linear.
        aidl::android::hardware::graphics::common::ExtendableType comp;
        if (gm.getCompression(nh, &comp) == OK && comp.value != 0) {
            modifier = DRM_FORMAT_MOD_QCOM_COMPRESSED;
            if (!p.loggedUbwc) {
                ALOGI("DRM: crtc %u buffers are compressed (%s %lld): UBWC modifier", p.crtcId,
                      comp.name.c_str(), (long long)comp.value);
                p.loggedUbwc = true;
            }
        }
    }
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
    if (p.retirePlaneId) {
        drmModeAtomicAddProperty(req, p.retirePlaneId, p.retireFbProp, 0);
        drmModeAtomicAddProperty(req, p.retirePlaneId, p.retireCrtcProp, 0);
    }
    int fenceFd = -1;
    if (p.pInFence && item.mFence != nullptr && item.mFence->isValid()) {
        fenceFd = item.mFence->dup();
        if (fenceFd >= 0) drmModeAtomicAddProperty(req, p.planeId, p.pInFence, fenceFd);
    }
    p.commitNs = monoNs();
    p.queuedNs = item.mTimestamp;
    // The frame is marked in flight BEFORE the commit, under the panel lock: the flip event can
    // reach the event thread (onFlipDone) before drmModeAtomicCommit returns here when this
    // thread is preempted, and marking it afterwards left flipPending set for good, so every
    // later frame on that panel was dropped (a frozen panel with nothing pending in the kernel).
    // No other event can arrive meanwhile: a commit is only issued once the previous flip is done.
    {
        std::lock_guard<std::mutex> lk(*p.mu);
        p.pending = item;
        p.hasPending = true;
        p.flipPending = !mBlockingCommits;
    }
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
        std::lock_guard<std::mutex> lk(*p.mu);
        p.hasPending = false;
        p.flipPending = false;
        return false;
    }
    p.geometrySet = true;
    p.retirePlaneId = 0;   // the old plane is off from this flip on
    p.srcW = w;
    p.srcH = h;
    p.flips++;
    if (mBlockingCommits) {
        // The blocking commit returned once the flip landed.
        onFlipDone(p.crtcId, monoNs());
        // The DP bridge panel locks into a self-sustaining stall when its next
        // commit lands right behind the previous one (the same lock SurfaceFlinger
        // needed the follower hold for); a short phase offset after each flip
        // keeps the next commit out of that window.
        if (!p.isDsi && mDpPostFlipUs > 0) usleep(mDpPostFlipUs);
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
            // Oldest frame first, provided its GPU work is done. With mShowNewest the newest
            // frame whose GPU work is done goes up instead and the finished ones queued before
            // it are dropped: a frame is never shown later than one vblank after it is ready.
            // The in-order queue otherwise never drains once it holds a backlog (the DP panel
            // runs at 59.98 Hz against a 60 Hz producer), so each panel showed frames 2 to 3
            // vblanks after the core queued them.
            int pick = signalled(ready.front()) ? 0 : -1;
            if (pick >= 0 && mShowNewest) {
                while ((size_t)pick + 1 < ready.size() && signalled(ready[pick + 1])) pick++;
                for (int i = 0; i < pick; i++) {
                    releaseReady(ready[i]);
                    p.stale++;
                }
            }
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
            ALOGI("DRM: crtc %u flips=%" PRIu64 " drops=%" PRIu64 " stale=%" PRIu64 " avail_cb=%" PRIu64
                  " gaps>20ms=%" PRIu64 " maxgap=%.1fms commit->flip avg %.1f max %.1f ms",
                  p.crtcId, p.flips, p.drops, p.stale, p.availCount->load(), p.longGaps, p.maxGapNs / 1e6,
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
    mMinimalFlips = settings::getBool("persist.gammaos.azahar_nano.drm_minimal", true);
    if (!openDevice() || !enumerate()) return 0;
    // Optional 180 degree correction for the DSI panel (plane reflect x+y).
    char rot[PROPERTY_VALUE_MAX] = {};
    settings::get("persist.gammaos.azahar_nano.drm_top_rot180", rot, "0");
    if (rot[0] == '1' && !mPanels.empty() && mPanels[0].pRotation) {
        mPanels[0].rotationValue = DRM_MODE_ROTATE_180;
    }
    if (!modeset()) return 0;
    // SurfaceFlinger's GammaOS composition gate: it stops presenting while a
    // DRM-direct client owns the panels (same flag gammaos-nano sets).
    property_set("sys.gammaos.nano.drm_active", "1");
    mBlockingCommits = settings::getBool("persist.gammaos.azahar_nano.drm_blocking", false);
    mDpPostFlipUs = settings::getInt("persist.gammaos.azahar_nano.drm_dp_postflip_us", 0);
    mShowNewest = settings::getBool("persist.gammaos.azahar_nano.drm_newest", true);
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
    // Release SurfaceFlinger's composition gate only now that master is gone: SF cycles the
    // composer's power as the gate opens, and a composer commit made while we still held
    // master was refused, leaving its displays OFF (black apps on both panels).
    property_set("sys.gammaos.nano.drm_active", "0");
}

} // namespace azahar_nano
