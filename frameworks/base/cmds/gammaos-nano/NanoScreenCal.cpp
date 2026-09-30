/*
 * Copyright (C) 2026 GammaOS
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

#define LOG_TAG "GammaOSNano"

#include "NanoScreenCal.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <stdint.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <chrono>
#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

#include <cutils/properties.h>
#include <utils/Log.h>
#include <zlib.h>

#include <drm.h>
#include <drm_mode.h>

namespace android {
namespace screencal {
namespace {

// ---- settings ----------------------------------------------------------------------------

enum { kTop = 0, kBottom = 1 };
const char* const kScreenName[2] = { "top", "bottom" };

struct Cal {
    int bri = 50, con = 50, sat = 50, hue = 50;   // BCSH, 0..100, 50 = untouched
    int r = 100, g = 100, b = 100;                // channel levels in percent, 100 = untouched
    bool bcshNeutral() const { return bri == 50 && con == 50 && sat == 50 && hue == 50; }
    bool levelsNeutral() const { return r == 100 && g == 100 && b == 100; }
};

struct Field { const char* name; int Cal::*member; int def, lo, hi; };
const Field kFields[] = {
    {"brightness", &Cal::bri, 50, 0, 100}, {"contrast", &Cal::con, 50, 0, 100},
    {"saturation", &Cal::sat, 50, 0, 100}, {"hue", &Cal::hue, 50, 0, 100},
    {"red", &Cal::r, 100, 20, 100}, {"green", &Cal::g, 100, 20, 100}, {"blue", &Cal::b, 100, 20, 100},
};

std::string propName(int screen, const char* field) {
    return std::string(kPropPrefix) + kScreenName[screen] + "." + field;
}

int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }

Cal load(int screen, const std::string& overrideKey = std::string(), const std::string& overrideVal = std::string()) {
    Cal c;
    for (const Field& f : kFields) {
        const std::string key = propName(screen, f.name);
        int v = f.def;
        if (!overrideKey.empty() && key == overrideKey) {
            v = atoi(overrideVal.c_str());
        } else {
            char buf[PROPERTY_VALUE_MAX] = {};
            if (property_get(key.c_str(), buf, "") > 0) v = atoi(buf);
        }
        c.*(f.member) = clampi(v, f.lo, f.hi);
    }
    return c;
}

void store(int screen, const Cal& c) {
    for (const Field& f : kFields) {
        const std::string key = propName(screen, f.name);
        const int v = c.*(f.member);
        // Neutral values are stored as unset, so an untouched device carries no properties.
        property_set(key.c_str(), v == f.def ? "" : std::to_string(v).c_str());
    }
}

// ---- DRM ---------------------------------------------------------------------------------

struct Prop { uint32_t id = 0; uint64_t value = 0; };
struct Port {
    bool valid = false;
    int port = -1;
    uint32_t crtc = 0, conn = 0;
    Prop bri, con, sat, hue;       // connector
    Prop gamma, gammaSize, cubic, cubicSize, modeId;   // crtc
    drm_mode_modeinfo mode = {};
    bool haveMode = false;
};

int drmIo(int fd, unsigned long req, void* arg) {
    int r;
    do r = ioctl(fd, req, arg); while (r == -1 && (errno == EINTR || errno == EAGAIN));
    return r;
}

void readProps(int fd, uint32_t obj, uint32_t type, std::vector<std::pair<std::string, Prop>>* out) {
    drm_mode_obj_get_properties p = {};
    p.obj_id = obj; p.obj_type = type;
    if (drmIo(fd, DRM_IOCTL_MODE_OBJ_GETPROPERTIES, &p) || !p.count_props) return;
    std::vector<uint32_t> ids(p.count_props);
    std::vector<uint64_t> vals(p.count_props);
    p.props_ptr = (uintptr_t)ids.data(); p.prop_values_ptr = (uintptr_t)vals.data();
    if (drmIo(fd, DRM_IOCTL_MODE_OBJ_GETPROPERTIES, &p)) return;
    for (uint32_t i = 0; i < p.count_props && i < ids.size(); i++) {
        drm_mode_get_property gp = {};
        gp.prop_id = ids[i];
        if (drmIo(fd, DRM_IOCTL_MODE_GETPROPERTY, &gp)) continue;
        Prop pr; pr.id = ids[i]; pr.value = vals[i];
        out->emplace_back(std::string(gp.name), pr);
    }
}

Prop find(const std::vector<std::pair<std::string, Prop>>& props, const char* name) {
    for (const auto& kv : props) if (kv.first == name) return kv.second;
    return Prop();
}

// Both DSI ports, indexed by VOP video port (0, 1).
bool discover(int fd, Port ports[2]) {
    // CRTC_ID, MODE_ID and the other atomic properties are only listed to atomic clients.
    drm_set_client_cap cap = { DRM_CLIENT_CAP_ATOMIC, 1 };
    drmIo(fd, DRM_IOCTL_SET_CLIENT_CAP, &cap);
    drm_mode_card_res res = {};
    if (drmIo(fd, DRM_IOCTL_MODE_GETRESOURCES, &res)) return false;
    std::vector<uint32_t> crtcs(res.count_crtcs), conns(res.count_connectors);
    drm_mode_card_res r2 = {};
    r2.count_crtcs = res.count_crtcs; r2.crtc_id_ptr = (uintptr_t)crtcs.data();
    r2.count_connectors = res.count_connectors; r2.connector_id_ptr = (uintptr_t)conns.data();
    if (drmIo(fd, DRM_IOCTL_MODE_GETRESOURCES, &r2)) return false;
    for (uint32_t c : crtcs) {
        std::vector<std::pair<std::string, Prop>> props;
        readProps(fd, c, DRM_MODE_OBJECT_CRTC, &props);
        const Prop portId = find(props, "PORT_ID");
        if (!portId.id || portId.value > 1) continue;
        Port& p = ports[portId.value];
        p.port = (int)portId.value; p.crtc = c;
        p.gamma = find(props, "GAMMA_LUT"); p.gammaSize = find(props, "GAMMA_LUT_SIZE");
        p.cubic = find(props, "CUBIC_LUT"); p.cubicSize = find(props, "CUBIC_LUT_SIZE");
        p.modeId = find(props, "MODE_ID");
        if (p.modeId.value) {
            drm_mode_get_blob b = {};
            b.blob_id = (uint32_t)p.modeId.value; b.length = sizeof(p.mode); b.data = (uintptr_t)&p.mode;
            p.haveMode = drmIo(fd, DRM_IOCTL_MODE_GETPROPBLOB, &b) == 0 && b.length == sizeof(p.mode);
        }
    }
    for (uint32_t c : conns) {
        std::vector<std::pair<std::string, Prop>> props;
        readProps(fd, c, DRM_MODE_OBJECT_CONNECTOR, &props);
        const Prop crtcId = find(props, "CRTC_ID");
        if (!crtcId.value) continue;
        for (int i = 0; i < 2; i++) {
            if (ports[i].crtc != crtcId.value) continue;
            ports[i].conn = c;
            ports[i].bri = find(props, "brightness"); ports[i].con = find(props, "contrast");
            ports[i].sat = find(props, "saturation"); ports[i].hue = find(props, "hue");
        }
    }
    for (int i = 0; i < 2; i++)
        ports[i].valid = ports[i].crtc && ports[i].conn && ports[i].bri.id && ports[i].sat.id;
    return ports[0].valid || ports[1].valid;
}

// The physical screens: the panel on VOP port 1 (DSI-2) is the top screen on both the RG DS and
// the RG DS Plus, port 0 (DSI-1) the bottom one (see NanoMenuDrm, drmPanelMountRotationDeg(1)).
int portOf(int screen) { return screen == kTop ? 1 : 0; }

// Levels on a port: the 3D LUT when the port has one (port 0), otherwise the gamma LUT. The
// RK3568 VOP has a single gamma LUT that only one port can hold, so the two screens cannot both
// use it, and the kernel refuses a second one ("only support 1 gamma").
bool usesCubic(const Port& p) { return p.cubic.id && p.cubicSize.value == 729; }

uint32_t makeLevelsBlob(int fd, const Port& p, const Cal& c) {
    std::vector<drm_color_lut> lut;
    const double gr = c.r / 100.0, gg = c.g / 100.0, gb = c.b / 100.0;
    if (usesCubic(p)) {
        // 9x9x9, red changing fastest (index r + 9g + 81b), 12-bit values. The order is not
        // documented by Rockchip; it is the one verified on VOP2 hardware by the mabur project.
        lut.resize(729);
        for (int b = 0; b < 9; b++)
            for (int g = 0; g < 9; g++)
                for (int r = 0; r < 9; r++) {
                    drm_color_lut& e = lut[r + 9 * g + 81 * b];
                    e.red = (uint16_t)lround(r * 4095.0 / 8.0 * gr);
                    e.green = (uint16_t)lround(g * 4095.0 / 8.0 * gg);
                    e.blue = (uint16_t)lround(b * 4095.0 / 8.0 * gb);
                    e.reserved = 0;
                }
    } else {
        const uint32_t n = p.gammaSize.value ? (uint32_t)p.gammaSize.value : 1024;
        lut.resize(n);
        for (uint32_t i = 0; i < n; i++) {
            const double x = (double)i / (n - 1);
            lut[i].red = (uint16_t)lround(x * gr * 0xffff);
            lut[i].green = (uint16_t)lround(x * gg * 0xffff);
            lut[i].blue = (uint16_t)lround(x * gb * 0xffff);
            lut[i].reserved = 0;
        }
    }
    drm_mode_create_blob blob = {};
    blob.data = (uintptr_t)lut.data(); blob.length = (uint32_t)(lut.size() * sizeof(drm_color_lut));
    if (drmIo(fd, DRM_IOCTL_MODE_CREATEPROPBLOB, &blob)) return 0;
    return blob.blob_id;
}

std::mutex gApplyMu;

// One atomic commit for both screens. This kernel accepts property commits from a client that is
// not the DRM master (the composer is), so nano and drastic-nano sessions keep working while it
// runs; the values stay in the kernel state across those sessions.
bool commitCal(const Cal cal[2], Port portsOut[2]) {
    int fd = open("/dev/dri/card0", O_RDWR | O_CLOEXEC);
    if (fd < 0) { ALOGW("screencal: open card0: %s", strerror(errno)); return false; }
    Port ports[2];
    if (!discover(fd, ports)) { close(fd); ALOGW("screencal: DSI ports not found"); return false; }
    std::vector<uint32_t> objs, counts, props, blobs;
    std::vector<uint64_t> vals;
    for (int s = 0; s < 2; s++) {
        const Port& p = ports[portOf(s)];
        if (!p.valid) continue;
        const Cal& c = cal[s];
        objs.push_back(p.conn); counts.push_back(0);
        auto add = [&](const Prop& pr, uint64_t v) { if (pr.id) { props.push_back(pr.id); vals.push_back(v); counts.back()++; } };
        add(p.bri, c.bri); add(p.con, c.con); add(p.sat, c.sat); add(p.hue, c.hue);
        // Levels: a LUT that is set cannot be switched off again on this kernel (clearing the
        // property leaves the last table active in hardware), so "untouched" is written as an
        // identity table once a table has been set, and not written at all before that.
        const Prop& lutProp = usesCubic(p) ? p.cubic : p.gamma;
        if (lutProp.id && (!c.levelsNeutral() || lutProp.value)) {
            const uint32_t blob = makeLevelsBlob(fd, p, c);
            if (blob) {
                blobs.push_back(blob);
                objs.push_back(p.crtc); counts.push_back(1);
                props.push_back(lutProp.id); vals.push_back(blob);
            }
        }
    }
    bool ok = objs.empty();
    if (!objs.empty()) {
        drm_mode_atomic a = {};
        a.count_objs = (uint32_t)objs.size();
        a.objs_ptr = (uintptr_t)objs.data(); a.count_props_ptr = (uintptr_t)counts.data();
        a.props_ptr = (uintptr_t)props.data(); a.prop_values_ptr = (uintptr_t)vals.data();
        for (int tries = 0; tries < 25; tries++) {   // a page flip in flight answers EBUSY
            if (ioctl(fd, DRM_IOCTL_MODE_ATOMIC, &a) == 0) { ok = true; break; }
            if (errno != EBUSY && errno != EINTR && errno != EAGAIN) break;
            usleep(2000);
        }
        if (!ok) ALOGW("screencal: atomic commit failed: %s", strerror(errno));
    }
    for (uint32_t b : blobs) { drm_mode_destroy_blob d = { b }; drmIo(fd, DRM_IOCTL_MODE_DESTROYPROPBLOB, &d); }
    if (portsOut) for (int i = 0; i < 2; i++) portsOut[i] = ports[i];
    close(fd);
    return ok;
}

// ---- baseparameter -------------------------------------------------------------------------
//
// Rockchip baseparameter (u-boot include/edid.h): "BASP", u16 major, u16 minor, 8 connector
// headers {u32 type, u32 id, u32 offset}, then per-connector base2_disp_info records. u-boot
// only uses a record whose screen_info[0] names the same connector type and id, and checks its
// CRC32 (crc over the first 36932 bytes for 2.x, crc2 over the first 49840 bytes for 3.0).
// The stock RG DS image only has Rockchip's template records (HDMI plus an empty DSI 0), so
// nothing applied; these helpers write real records for both DSI connectors.

constexpr const char* kBaseparameter = "/dev/block/by-name/baseparameter";
constexpr uint32_t kConnectorDsi = 16;   // DRM_MODE_CONNECTOR_DSI
constexpr size_t kOffScreen = 8, kScreenSize = 72, kOffBcsh = 296, kOffOverscan = 304;
constexpr size_t kOffGamma = 316, kOffCubic = 6462, kCubicCap = 4913;
constexpr size_t kV2Crc = 36932, kV3Size = 49844, kV3Crc2 = 49840;

void put16(uint8_t* p, uint16_t v) { memcpy(p, &v, 2); }
void put32(uint8_t* p, uint32_t v) { memcpy(p, &v, 4); }
uint32_t get32(const uint8_t* p) { uint32_t v; memcpy(&v, p, 4); return v; }
uint16_t get16(const uint8_t* p) { uint16_t v; memcpy(&v, p, 2); return v; }

uint32_t readBe32(const std::string& path) {
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return 0;
    uint8_t b[4] = {};
    const ssize_t n = read(fd, b, 4);
    close(fd);
    return n == 4 ? ((uint32_t)b[0] << 24 | (uint32_t)b[1] << 16 | (uint32_t)b[2] << 8 | b[3]) : 0;
}

// The DSI controller index (the baseparameter connector id) routed to a VOP port, from the live
// device tree: route-dsi<N>/connect points at an endpoint under vop/ports/port@<port>.
int dsiIndexForPort(int port) {
    static const char* kVop = "/proc/device-tree/vop@fe040000/ports";
    for (int dsi = 0; dsi < 2; dsi++) {
        const uint32_t ph = readBe32("/proc/device-tree/display-subsystem/route/route-dsi" + std::to_string(dsi) + "/connect");
        if (!ph) continue;
        const std::string portDir = std::string(kVop) + "/port@" + std::to_string(port);
        DIR* d = opendir(portDir.c_str());
        if (!d) continue;
        bool hit = false;
        while (dirent* e = readdir(d)) {
            if (strncmp(e->d_name, "endpoint", 8)) continue;
            if (readBe32(portDir + "/" + e->d_name + "/phandle") == ph) { hit = true; break; }
        }
        closedir(d);
        if (hit) return dsi;
    }
    return port;   // the routing on both devices: dsi0 -> port 0, dsi1 -> port 1
}

bool writeBaseparameter(const Cal cal[2], const Port ports[2]) {
    int fd = open(kBaseparameter, O_RDWR | O_CLOEXEC);
    if (fd < 0) { ALOGW("screencal: %s: %s", kBaseparameter, strerror(errno)); return false; }
    const off_t size = lseek(fd, 0, SEEK_END);
    if (size < 4096 || size > (8 << 20)) { close(fd); return false; }
    std::vector<uint8_t> buf((size_t)size), orig;
    if (pread(fd, buf.data(), buf.size(), 0) != (ssize_t)buf.size() || memcmp(buf.data(), "BASP", 4)) {
        ALOGW("screencal: baseparameter unreadable or not BASP, left alone");
        close(fd);
        return false;
    }
    orig = buf;
    const uint16_t major = get16(&buf[4]), minor = get16(&buf[6]);
    if (major != 2 && major != 3) { ALOGW("screencal: baseparameter v%u.%u not supported", major, minor); close(fd); return false; }
    const bool v3 = major == 3 && minor == 0;
    const size_t rec = v3 ? kV3Size : kV2Crc + 4;
    // An untouched calibration leaves a stock partition alone: records are only written once the
    // user has changed something, and then kept up to date (including back to neutral).
    if (cal[0].bcshNeutral() && cal[0].levelsNeutral() && cal[1].bcshNeutral() && cal[1].levelsNeutral()) {
        bool ours = false;
        for (int i = 0; i < 8 && !ours; i++) {
            const uint32_t off = get32(&buf[16 + 12 * i]);
            ours = get32(&buf[8 + 12 * i]) == kConnectorDsi && off && off + rec <= buf.size() &&
                   get32(&buf[off + kOffScreen]) == kConnectorDsi;
        }
        if (!ours) { close(fd); return true; }
    }
    for (int s = 0; s < 2; s++) {
        const Port& p = ports[portOf(s)];
        if (!p.valid || !p.haveMode) {
            ALOGW("screencal: %s screen not written to baseparameter (port %d valid %d mode %d)",
                  kScreenName[s], portOf(s), p.valid ? 1 : 0, p.haveMode ? 1 : 0);
            continue;
        }
        const uint32_t id = (uint32_t)dsiIndexForPort(p.port);
        int slot = -1;
        for (int i = 0; i < 8 && slot < 0; i++)
            if (get32(&buf[8 + 12 * i]) == kConnectorDsi && get32(&buf[12 + 12 * i]) == id) slot = i;
        for (int i = 0; i < 8 && slot < 0; i++) {
            const uint32_t off = get32(&buf[16 + 12 * i]);
            if (get32(&buf[8 + 12 * i]) == 0 && off && off + rec <= buf.size()) slot = i;
        }
        if (slot < 0) { ALOGW("screencal: no baseparameter slot for DSI %u", id); continue; }
        const uint32_t off = get32(&buf[16 + 12 * slot]);
        if (!off || off + rec > buf.size()) continue;
        put32(&buf[8 + 12 * slot], kConnectorDsi); put32(&buf[12 + 12 * slot], id);
        uint8_t* r = &buf[off];
        char flag[7]; snprintf(flag, sizeof flag, "DISP_%d", slot);
        memcpy(r, flag, 6);
        memset(r + kOffScreen, 0, 4 * kScreenSize);
        put32(r + kOffScreen, kConnectorDsi); put32(r + kOffScreen + 4, id);
        const drm_mode_modeinfo& m = p.mode;
        const int32_t mode[13] = { (int32_t)m.clock, m.hdisplay, m.hsync_start, m.hsync_end, m.htotal,
                                   m.vdisplay, m.vsync_start, m.vsync_end, m.vtotal, (int32_t)m.vrefresh,
                                   m.vscan, (int32_t)m.flags, 0 };
        memcpy(r + kOffScreen + 8, mode, sizeof mode);
        const Cal& c = cal[s];
        put16(r + kOffBcsh, (uint16_t)c.bri); put16(r + kOffBcsh + 2, (uint16_t)c.con);
        put16(r + kOffBcsh + 4, (uint16_t)c.sat); put16(r + kOffBcsh + 6, (uint16_t)c.hue);
        if (get32(r + kOffOverscan) == 0) {
            put32(r + kOffOverscan, 100);
            for (int k = 0; k < 4; k++) put16(r + kOffOverscan + 4 + 2 * k, 100);
        }
        // Levels, in the table this port uses (see usesCubic); the other table stays empty so the
        // composer never loads the shared gamma LUT onto port 0 and locks port 1 out of it.
        memset(r + kOffGamma, 0, kOffCubic - kOffGamma);
        memset(r + kOffCubic, 0, 2 + 3 * 2 * kCubicCap);
        if (!c.levelsNeutral()) {
            const double gain[3] = { c.r / 100.0, c.g / 100.0, c.b / 100.0 };
            if (usesCubic(p)) {
                put16(r + kOffCubic, 729);
                for (int i = 0; i < 729; i++) {
                    const int idx[3] = { i % 9, (i / 9) % 9, i / 81 };   // red fastest
                    for (int ch = 0; ch < 3; ch++)
                        put16(r + kOffCubic + 2 + (size_t)ch * 2 * kCubicCap + 2 * i,
                              (uint16_t)lround(idx[ch] * 4095.0 / 8.0 * gain[ch]));
                }
            } else {
                put16(r + kOffGamma, 1024);
                for (int i = 0; i < 1024; i++)
                    for (int ch = 0; ch < 3; ch++)
                        put16(r + kOffGamma + 2 + (size_t)ch * 2048 + 2 * i,
                              (uint16_t)lround(i / 1023.0 * gain[ch] * 0xffff));
            }
        }
        put32(r + kV2Crc, (uint32_t)crc32(0, r, kV2Crc));
        if (v3) put32(r + kV3Crc2, (uint32_t)crc32(0, r, kV3Crc2));
    }
    bool ok = true;
    if (buf == orig) ALOGI("screencal: baseparameter already up to date");
    if (buf != orig) {
        ok = pwrite(fd, buf.data(), buf.size(), 0) == (ssize_t)buf.size() && fsync(fd) == 0;
        ALOGI("screencal: baseparameter %s", ok ? "updated" : "write failed");
    }
    close(fd);
    return ok;
}

void mirrorComposerProps(const Cal cal[2]);

// Debounced baseparameter mirror: a slider drag commits many values, the partition is written
// once the settings have been quiet for a moment.
std::mutex gBpMu;
std::condition_variable gBpCv;
uint64_t gBpGen = 0;
bool gBpThread = false;

void scheduleBaseparameter() {
    std::lock_guard<std::mutex> lk(gBpMu);
    gBpGen++;
    gBpCv.notify_all();
    if (gBpThread) return;
    gBpThread = true;
    std::thread([] {
        std::unique_lock<std::mutex> lk(gBpMu);
        for (;;) {
            const uint64_t gen = gBpGen;
            gBpCv.wait_for(lk, std::chrono::milliseconds(1500));
            if (gen != gBpGen) continue;   // changed again: wait for quiet
            lk.unlock();
            Cal cal[2] = { load(kTop), load(kBottom) };
            Port ports[2];
            {
                std::lock_guard<std::mutex> ak(gApplyMu);
                int fd = open("/dev/dri/card0", O_RDWR | O_CLOEXEC);
                if (fd >= 0) { discover(fd, ports); close(fd); }
            }
            writeBaseparameter(cal, ports);
            mirrorComposerProps(cal);
            lk.lock();
            if (gen == gBpGen) { gBpThread = false; return; }
        }
    }).detach();
}

// The Rockchip composer re-applies BCSH at boot and on every screen-on. From baseparameter it
// reads the wrong record for the second screen (it names both DSI connectors "DSI-0", so the
// top screen got the bottom screen's values), but it prefers its per-display properties
// persist.vendor.<brightness|contrast|saturation|hue>.<main|aux> when they are set: display 0
// (main) is the panel on port 0, the bottom screen, display 1 (aux) the top screen. Mirror each
// screen's BCSH there, once the calibration has been touched (untouched devices get no props).
void mirrorComposerProps(const Cal cal[2]) {
    static const char* const kField[4] = { "brightness", "contrast", "saturation", "hue" };
    const bool neutral = cal[0].bcshNeutral() && cal[1].bcshNeutral();
    for (int s = 0; s < 2; s++) {
        const char* disp = portOf(s) == 0 ? "main" : "aux";
        const int v[4] = { cal[s].bri, cal[s].con, cal[s].sat, cal[s].hue };
        for (int f = 0; f < 4; f++) {
            const std::string key = std::string("persist.vendor.") + kField[f] + "." + disp;
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get(key.c_str(), cur, "");
            if (neutral && !cur[0]) continue;
            const std::string want = std::to_string(v[f]);
            if (want != cur) property_set(key.c_str(), want.c_str());
        }
    }
}

}  // namespace

bool supported() {
    static int sSupported = -1;
    if (sSupported < 0) {
        char dev[PROPERTY_VALUE_MAX] = {};
        property_get("ro.gammaos.device", dev, "");
        sSupported = (!strcmp(dev, "anbernicrgds") || !strcmp(dev, "anbernicrgdsplus")) ? 1 : 0;
    }
    return sSupported == 1;
}

bool isCalibrationKey(const std::string& key) {
    return key.compare(0, strlen(kPropPrefix), kPropPrefix) == 0;
}

void applyFromProps() {
    if (!supported()) return;
    Cal cal[2] = { load(kTop), load(kBottom) };
    {
        std::lock_guard<std::mutex> lk(gApplyMu);
        commitCal(cal, nullptr);
    }
    scheduleBaseparameter();
}

void preview(const std::string& key, const std::string& value) {
    if (!supported() || !isCalibrationKey(key)) return;
    Cal cal[2] = { load(kTop, key, value), load(kBottom, key, value) };
    std::lock_guard<std::mutex> lk(gApplyMu);
    commitCal(cal, nullptr);
}

void copyTopToBottom() {
    if (!supported()) return;
    store(kBottom, load(kTop));
    applyFromProps();
}

void resetScreen(bool top) {
    if (!supported()) return;
    store(top ? kTop : kBottom, Cal());
    applyFromProps();
}

void resetAll() {
    if (!supported()) return;
    store(kTop, Cal());
    store(kBottom, Cal());
    applyFromProps();
}

}  // namespace screencal
}  // namespace android
