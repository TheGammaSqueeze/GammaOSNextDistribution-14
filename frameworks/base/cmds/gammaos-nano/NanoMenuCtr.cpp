// NanoMenuCtr - nano side of the 3DS HOME Menu theme (persist.gammaos.nano.ndstheme=4).
//
// NanoCtrHome draws and runs the menu; this file feeds it nano's content (the Game category:
// game systems as folders, their games and subfolders, Recently Played, Applications,
// collections), launches through nano's own launch paths, maps raw input onto 3DS buttons and
// places the two 3DS screens on the panels (two panels: top on the primary, bottom on the
// secondary; one panel: stacked or bottom only, by aspect).
#include "NanoMenu.h"
#include "NanoMenuDrm.h"
#include "NanoCtrHome.h"
#include "NanoCtrAudio.h"
#include "NanoMenuUtils.h"
#include "NanoI18n.h"

#include <linux/input.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
// exported by libc, declared only in the internal header (see NanoMenuInput.cpp)
extern "C" uint32_t __system_property_serial(const prop_info* __pi);

#include <android-base/properties.h>
#include <cutils/properties.h>
#include <log/log.h>
#include <utils/SystemClock.h>
#include <utils/Timers.h>

#include "stb_image.h"

#include <EGL/egl.h>
#include <GLES2/gl2ext.h>

#undef LOG_TAG
#define LOG_TAG "NanoCtr"

namespace android {

static const char* kCtrAssetDirs[] = {
    "/data/local/tmp/ctr_assets",   // development override (pushed by the test scripts)
    "/system/etc/nano_ctr",
};

// ---------------------------------------------------------------------------------------- host

class NanoCtrHost : public ctr::CtrHomeHost {
public:
    explicit NanoCtrHost(NanoMenu* m) : mMenu(m) {}

    // Identity of a container: the Ps3Item that opens it, by id.
    std::map<std::string, NanoMenu::Ps3Item> mFolders;
    std::map<std::string, NanoMenu::Ps3Item> mTitles;

    static bool isContainer(int kind) {
        switch (kind) {
            case NanoMenu::PS3_SYSTEM: case NanoMenu::PS3_ROM_FOLDER: case NanoMenu::PS3_RECENT_LIST:
            case NanoMenu::PS3_APP_LIST: case NanoMenu::PS3_COLLECTIONS_LIST: case NanoMenu::PS3_COLLECTION:
            case NanoMenu::PS3_FAVORITES_LIST: case NanoMenu::PS3_PINNED_APPS_LIST:
                return true;
            default:
                return false;
        }
    }

    std::string idOf(const NanoMenu::Ps3Item& it) {
        char buf[64];
        snprintf(buf, sizeof(buf), "%d:%d:%d:", it.kind, it.a, it.b);
        return std::string(buf) + it.payloadStr + ":" + it.label;
    }

    std::vector<ctr::HomeItem> fromLevel(const std::vector<NanoMenu::Ps3Item>& list) {
        std::vector<ctr::HomeItem> out;
        for (const NanoMenu::Ps3Item& it : list) {
            if (it.kind == NanoMenu::PS3_DATA_LEAF && it.action == 0) continue;   // "There are no titles"
            ctr::HomeItem h;
            h.id = idOf(it);
            h.name = it.label;
            if (isContainer(it.kind)) {
                h.kind = ctr::HomeItemKind::Folder;
                mFolders[h.id] = it;
            } else {
                h.kind = ctr::HomeItemKind::Title;
                h.monoIcon = it.nmapTex != 0 && !it.flatOwnTint;
                mTitles[h.id] = it;
                if (it.kind == NanoMenu::PS3_ROM && it.a >= 0 && it.a < (int)mMenu->mXmbSystems.size())
                    h.publisher = mMenu->mXmbSystems[it.a].name;
            }
            out.push_back(std::move(h));
        }
        return out;
    }

    int gameCat() const {
        for (size_t i = 0; i < mMenu->mPs3Cats.size(); i++)
            if (mMenu->mPs3Cats[i].name == "Game") return (int)i;
        return -1;
    }

    std::vector<ctr::HomeItem> items(const std::string& folderId) override {
        if (folderId.empty()) {
            const int gc = gameCat();
            if (gc < 0) return {};
            return fromLevel(mMenu->mPs3Cats[gc].items);
        }
        auto f = mFolders.find(folderId);
        if (f == mFolders.end()) return {};
        const NanoMenu::Ps3Item it = f->second;
        NanoMenu::Ps3Level lvl;
        switch (it.kind) {
            case NanoMenu::PS3_SYSTEM: mMenu->buildRomSubmenu(it.a, lvl); break;
            case NanoMenu::PS3_ROM_FOLDER: lvl.romFolder = it.payloadStr; mMenu->buildRomSubmenu(it.a, lvl); break;
            case NanoMenu::PS3_RECENT_LIST: mMenu->buildRecentSubmenu(lvl); break;
            case NanoMenu::PS3_APP_LIST: mMenu->buildAppSubmenu(lvl); break;
            case NanoMenu::PS3_COLLECTIONS_LIST: mMenu->buildCollectionsSubmenu(lvl); break;
            case NanoMenu::PS3_COLLECTION: mMenu->buildCollectionSubmenu(it.a, lvl); break;
            case NanoMenu::PS3_FAVORITES_LIST: mMenu->buildFavoritesSubmenu(lvl); break;
            case NanoMenu::PS3_PINNED_APPS_LIST: mMenu->buildPinnedAppsSubmenu(lvl); break;
            default: break;
        }
        return fromLevel(lvl.items);
    }

    bool titleIcon(const ctr::HomeItem& item, std::vector<uint8_t>* rgba, int* w, int* h) override {
        auto t = mTitles.find(item.id);
        if (t == mTitles.end()) return false;
        const NanoMenu::Ps3Item& it = t->second;
        if (it.kind == NanoMenu::PS3_APP && !it.payloadStr.empty()) {
            const std::string path = "/data/system/nano_app_icons/" + it.payloadStr + ".png";
            int c = 0;
            unsigned char* px = stbi_load(path.c_str(), w, h, &c, 4);
            if (!px) return false;
            rgba->assign(px, px + (size_t)(*w) * (*h) * 4);
            stbi_image_free(px);
            return true;
        }
        return false;
    }

    GLuint titleIconTexture(const ctr::HomeItem& item, int* w, int* h) override {
        auto t = mTitles.find(item.id);
        if (t == mTitles.end()) return 0;
        const NanoMenu::Ps3Item& it = t->second;
        if (it.kind == NanoMenu::PS3_APP) return 0;   // decoded from the icon cache (titleIcon)
        if (it.iconTex) {
            *w = *h = 48;
            return it.iconTex;
        }
        return 0;
    }

    bool launch(const ctr::HomeItem& item) override {
        auto t = mTitles.find(item.id);
        if (t == mTitles.end()) return false;
        return mMenu->ctrLaunchItem(t->second);
    }

    void appletBar(int button) override { mMenu->ctrAppletBar(button); }

    void backAtRoot() override {
        if (mMenu->mOverlayMode) mMenu->overlayResume();
    }

    std::string appletName(int button) override {
        static const char* kNames[6] = {"", "Quick Menu", "Photo", "Music", "Network", "Video"};
        return button >= 1 && button <= 5 ? std::string(trDyn(kNames[button])) : std::string();
    }

    // Menu sounds: effects follow Nav Sounds, the BGM its own toggle (Theme Settings).
    int playSound(const char* name) override {
        if (!mMenu->mCtrAudio) return 0;
        const bool bgm = !strncmp(name, "BGM_", 4);
        char v[PROPERTY_VALUE_MAX] = {};
        property_get(bgm ? "persist.gammaos.nano.ctr.bgm" : "persist.gammaos.nano.nav_sounds", v, "1");
        if (v[0] == '0' || v[0] == 'f' || v[0] == 'F') return 0;
        // no BGM over a suspended app (the overlay over a live app; same rule as the DSi theme)
        if (bgm && mMenu->mOverlayMode && !mMenu->mOverlayWallpaper) return 0;
        return mMenu->mCtrAudio->play(name);
    }
    bool soundPlaying(int handle) override {
        return mMenu->mCtrAudio && mMenu->mCtrAudio->playing(handle);
    }
    void stopSound(int handle, int fadeFrames) override {
        if (mMenu->mCtrAudio && handle) mMenu->mCtrAudio->stop(handle, fadeFrames);
    }

    ctr::HomeStatus status() override {
        ctr::HomeStatus s;
        s.wifiConnected = mMenu->mWifiLevel == NanoMenu::kWifiLevel_Connected;
        s.wifiSignal = mMenu->mWifiLevel == NanoMenu::kWifiLevel_Off ? -1
                     : s.wifiConnected ? std::min(3, std::max(0, mMenu->mWifiBars - 1)) : 0;
        const int pct = mMenu->mBatteryPercent;
        s.batteryLevel = pct < 0 ? 5 : pct <= 5 ? 0 : pct <= 10 ? 1 : pct <= 25 ? 2 : pct <= 50 ? 3 : pct <= 75 ? 4 : 5;
        s.adapter = mMenu->mBatteryCharging;
        s.charging = mMenu->mBatteryCharging && pct < 100;
        return s;
    }

private:
    NanoMenu* mMenu;
};

// ---------------------------------------------------------------------------------------- setup

bool NanoMenu::ctrEnsure() {
    if (mCtrHome && mCtrHome->loaded()) return true;
    if (!mCtrHome) {
        std::string root;
        for (const char* d : kCtrAssetDirs) {
            struct stat st;
            if (stat((std::string(d) + "/lyt_launcher.cpk").c_str(), &st) == 0) { root = d; break; }
        }
        if (root.empty()) {
            static bool warned = false;
            if (!warned) { warned = true; ALOGE("ctr: 3DS theme assets not found"); }
            return false;
        }
        if (!mCtrHost) mCtrHost = new NanoCtrHost(this);
        if (!mCtrAudio) mCtrAudio = new ctr::CtrAudio(root + "/sound");
        mCtrHome = new ctr::CtrHome(root, mCtrHost);
        ALOGI("ctr: 3DS theme assets at %s", root.c_str());
    }
    mCtrHome->setLanguage("English");
    if (!mCtrHome->load()) {
        ALOGE("ctr: 3DS theme failed to load");
        return false;
    }
    return true;
}

void NanoMenu::ctrRelease() {
    if (mCtrHome) {
        mCtrHome->releaseGl();
        delete mCtrHome;
        mCtrHome = nullptr;
    }
}

// ---------------------------------------------------------------------------------------- actions

bool NanoMenu::ctrLaunchItem(const Ps3Item& it) {
    switch (it.kind) {
        case PS3_ROM:
        case PS3_RECENT: {
            if (!isLaunchReady()) { showLaunchBusyToast(); return false; }
            mXmbSystemIndex = it.kind == PS3_ROM ? it.a : -1;
            mXmbGameIndex = it.kind == PS3_ROM ? it.b : it.a;
            mSearchActive = false;
            if (mOverlayMode) { overlayLaunchGame(); return true; }
            launchXmbGame();
            // the 3DS fade already covers the screen: hand off once it is black (launchFadeHoldMs)
            if (mWaitForRelease && mLaunchFadeStart == 0) mLaunchFadeStart = uptimeMillis();
            return true;
        }
        case PS3_APP:
        case PS3_LAUNCH_PKG: {
            if (it.payloadStr.empty()) return false;
            if (mOverlayMode) { overlayLaunchPackage(it.payloadStr); return true; }
            if (!isLaunchReady()) { showLaunchBusyToast(); return false; }
            property_set("sys.gammaos.nano.launch_app", it.payloadStr.c_str());
            property_set("sys.gammaos.nano.launched_pkg", it.payloadStr.c_str());
            setLaunchRomPath("");
            property_set("sys.gammaos.nano.launch_core", "");
            property_set("persist.gammaos.nano.qr_prepared", "0");
            property_set("persist.gammaos.nano.qr_core", "");
            property_set("sys.gammaos.nano.return_apps", "1");
            armAppLaunchTrigger();
            property_set("sys.gammaos.nano.drop_input", "1");
            mWaitForRelease = true;
            if (mLaunchFadeStart == 0) mLaunchFadeStart = uptimeMillis();
            return true;
        }
        default: {
            // anything else (a settings or media entry on the grid) opens as a nano list
            Ps3Level lvl;
            lvl.title = it.label;
            lvl.items.push_back(it);
            lvl.sel = 0;
            mPs3Stack.push_back(lvl);
            ps3XmbSelect();
            if (mPs3Stack.size() >= 2) mPs3Stack.erase(mPs3Stack.end() - 2);
            else mPs3Stack.clear();
            mDisplayDirty = true;
            return false;
        }
    }
}

// Applet bar: 0 HOME Menu Settings -> nano Settings, 1 Game Notes -> Quick Menu,
// 2 Friend List -> Photo, 3 Notifications -> Music, 4 Internet Browser -> Network,
// 5 Miiverse -> Video. Each opens that category as a nano list over the home.
void NanoMenu::ctrAppletBar(int button) {
    static const char* kCats[6] = {"Settings", "Quick Menu", "Photo", "Music", "Network", "Video"};
    if (button < 0 || button > 5) return;
    for (size_t i = 0; i < mPs3Cats.size(); i++) {
        if (mPs3Cats[i].name != kCats[button]) continue;
        Ps3Level lvl;
        lvl.title = mPs3Cats[i].name;
        lvl.items = mPs3Cats[i].items;
        lvl.sel = 0;
        mPs3Stack.push_back(lvl);
        mDisplayDirty = true;
        return;
    }
}

// ---------------------------------------------------------------------------------------- input

bool NanoMenu::ctrOwnsInput() const {
    return mCtrTheme && mPs3Xmb && mCtrHome && mCtrHome->loaded() && !mPs3BootActive && mPs3Stack.empty() &&
           !ndsInModal() && !mSetupWizardActive && !(mPspClockOn || mPspClockReveal > 0.0f);
}

bool NanoMenu::ctrInputEvent(int type, int code, int value) {
    if (!ctrOwnsInput()) return false;
    auto setBit = [&](uint32_t bit, bool on) {
        if (on) mCtrHeld |= bit; else mCtrHeld &= ~bit;
    };
    if (type == EV_KEY) {
        if (value == 2) return true;   // key repeat: the menu repeats on its own
        const bool on = value != 0;
        switch (code) {
            case BTN_SOUTH: case KEY_ENTER: setBit(ctr::CTR_A, on); return true;
            case BTN_EAST: case KEY_BACK: case KEY_ESC: setBit(ctr::CTR_B, on); return true;
            case BTN_NORTH: setBit(ctr::CTR_X, on); return true;
            case BTN_WEST: setBit(ctr::CTR_Y, on); return true;
            case BTN_TL: case KEY_L: setBit(ctr::CTR_L, on); return true;
            case BTN_TR: case KEY_R: setBit(ctr::CTR_R, on); return true;
            case BTN_TL2: setBit(ctr::CTR_ZL, on); return true;
            case BTN_TR2: setBit(ctr::CTR_ZR, on); return true;
            case BTN_DPAD_UP: case KEY_UP: setBit(ctr::CTR_UP, on); return true;
            case BTN_DPAD_DOWN: case KEY_DOWN: setBit(ctr::CTR_DOWN, on); return true;
            case BTN_DPAD_LEFT: case KEY_LEFT: setBit(ctr::CTR_LEFT, on); return true;
            case BTN_DPAD_RIGHT: case KEY_RIGHT: setBit(ctr::CTR_RIGHT, on); return true;
            default: return false;   // START / SELECT / system keys stay with nano
        }
    }
    if (type == EV_ABS) {
        if (code == ABS_HAT0X) {
            setBit(ctr::CTR_LEFT, value < 0);
            setBit(ctr::CTR_RIGHT, value > 0);
            return true;
        }
        if (code == ABS_HAT0Y) {
            setBit(ctr::CTR_UP, value < 0);
            setBit(ctr::CTR_DOWN, value > 0);
            return true;
        }
        if (code == ABS_X || code == ABS_Y) {
            // circle pad: past 40 of 156 it sets the D-pad bit (input.ts); stick range read lazily
            // Only signed (centred) axes are sticks; a touchscreen's ABS_X/Y run from 0 and are
            // left to the touch path.
            struct input_absinfo a{};
            int lo = -32768, hi = 32767;
            if (ioctl(mCurrentInputFd, EVIOCGABS(code), &a) == 0 && a.maximum > a.minimum) {
                if (a.minimum >= 0) return false;
                lo = a.minimum;
                hi = a.maximum;
            }
            const float mid = (lo + hi) * 0.5f, half = (hi - lo) * 0.5f;
            const float v = half > 0 ? (value - mid) / half : 0.0f;
            const int dir = v > 40.0f / 156.0f ? 1 : v < -40.0f / 156.0f ? -1 : 0;
            if (code == ABS_X) {
                mCtrStickX = dir;
            } else {
                mCtrStickY = dir;
            }
            return true;
        }
    }
    return false;
}

// A touch frame: nano has the raw touch state (mTouchDown / mTouchRawX/Y); map it into bottom
// screen layout space through the rectangle the bottom screen is drawn in.
void NanoMenu::ctrTouchFrame() {
    if (!ctrOwnsInput()) return;
    float px = 0, py = 0;
    if (!touchMapRaw(mTouchRawX, mTouchRawY, px, py)) return;
    float rx, ry, rw, rh;
    ctrBottomRect(rx, ry, rw, rh);
    if (ctrDualPanel()) {
        // touchMapRaw scales the (calibrated) digitizer to the primary panel's logical size; the
        // bottom screen lives on its own panel, whose rectangle is in that panel's pixels
        float bw = (float)mWidth, bh = (float)mHeight;
        if (sAhbTargetSecondary.glFbo) {
            bw = (float)sAhbTargetSecondary.w;
            bh = (float)sAhbTargetSecondary.h;
        } else if (!mSecondaryCreatedSize.empty() && mSecondaryCreatedSize[0].first > 0) {
            bw = (float)mSecondaryCreatedSize[0].first;
            bh = (float)mSecondaryCreatedSize[0].second;
        }
        px = px / (float)mWidth * bw;
        py = py / (float)mHeight * bh;
    }
    ctr::CtrTouch t;
    t.held = mTouchDown;
    const float lw = mCtrHome->bottomWidth();
    t.x = (px - rx) / rw * lw - lw / 2;
    t.y = 120.0f - (py - ry) / rh * 240.0f;
    mCtrHome->setTouch(t);
}

// ---------------------------------------------------------------------------------------- render

// Frame cost probe (sys.gammaos.nano.ctr.perf: 1 = CPU times, 2 = also GPU via glFinish), logged
// every 120 frames: frame interval, home update, top screen, bottom screen.
namespace {
// GPU time per pass (sys.gammaos.nano.ctr.perf 3): EXT_disjoint_timer_query elapsed-time queries,
// read back a few frames later, logged with the CPU probe.
struct CtrGpuTimer {
    PFNGLGENQUERIESEXTPROC gen = nullptr;
    PFNGLBEGINQUERYEXTPROC begin = nullptr;
    PFNGLENDQUERYEXTPROC end = nullptr;
    PFNGLGETQUERYOBJECTUI64VEXTPROC result = nullptr;
    PFNGLGETQUERYOBJECTUIVEXTPROC avail = nullptr;
    bool tried = false, open = false;
    static const int kPasses = 16, kDepth = 4;
    GLuint q[kDepth][kPasses] = {};
    bool used[kDepth][kPasses] = {};
    int frame = 0;
    double sum[kPasses] = {};
    int n[kPasses] = {};
    std::string names[kPasses];
    int count = 0;
    bool ok() {
        if (!tried) {
            tried = true;
            const char* ext = (const char*)glGetString(GL_EXTENSIONS);
            if (ext && strstr(ext, "GL_EXT_disjoint_timer_query")) {
                gen = (PFNGLGENQUERIESEXTPROC)eglGetProcAddress("glGenQueriesEXT");
                begin = (PFNGLBEGINQUERYEXTPROC)eglGetProcAddress("glBeginQueryEXT");
                end = (PFNGLENDQUERYEXTPROC)eglGetProcAddress("glEndQueryEXT");
                result = (PFNGLGETQUERYOBJECTUI64VEXTPROC)eglGetProcAddress("glGetQueryObjectui64vEXT");
                avail = (PFNGLGETQUERYOBJECTUIVEXTPROC)eglGetProcAddress("glGetQueryObjectuivEXT");
            }
            if (gen) gen(kDepth * kPasses, &q[0][0]);
            ALOGI("ctr perf: gpu timers %s", gen ? "on" : "unavailable");
        }
        return gen != nullptr;
    }
    // ends the running span (if any) and starts the one named `next` (nullptr: none)
    void mark(const char* next) {
        if (!ok()) return;
        const int slot = frame % kDepth;
        if (open) { end(GL_TIME_ELAPSED_EXT); open = false; }
        if (!next) return;
        int idx = 0;
        while (idx < count && names[idx] != next) idx++;
        if (idx == count) {
            if (count == kPasses) return;
            names[count++] = next;
        }
        if (used[slot][idx]) return;   // a span runs once per frame
        begin(GL_TIME_ELAPSED_EXT, q[slot][idx]);
        used[slot][idx] = true;
        open = true;
    }
    void frameStart() {
        if (!ok()) return;
        mark(nullptr);
        frame++;
        const int slot = frame % kDepth;   // the oldest set: read it, then reuse it
        for (int p = 0; p < count; p++) {
            if (!used[slot][p]) continue;
            GLuint a = 0;
            avail(q[slot][p], GL_QUERY_RESULT_AVAILABLE_EXT, &a);
            if (a) {
                GLuint64 ns = 0;
                result(q[slot][p], GL_QUERY_RESULT_EXT, &ns);
                sum[p] += ns / 1e6;
                n[p]++;
            }
            used[slot][p] = false;
        }
    }
    void log() {
        std::string line;
        double total = 0;
        for (int p = 0; p < count; p++) {
            const double v = n[p] ? sum[p] / n[p] : 0;
            total += v;
            char b[64];
            snprintf(b, sizeof(b), " %s %.2f", names[p].c_str(), v);
            line += b;
            sum[p] = 0;
            n[p] = 0;
        }
        ALOGI("ctr perf gpu ms:%s (total %.2f)", line.c_str(), total);
    }
};
CtrGpuTimer sCtrGpu;

struct CtrPerf {
    int mode = 0, frames = 0;
    double interval = 0, worst = 0, update = 0, top = 0, bottom = 0;
    nsecs_t last = 0;
    static double ms(nsecs_t a, nsecs_t b) { return (double)(b - a) / 1e6; }
    void refresh() {
        static const prop_info* pi = nullptr;
        static uint32_t ser = ~0u;
        if (!pi) pi = __system_property_find("sys.gammaos.nano.ctr.perf");
        if (pi && __system_property_serial(pi) != ser) {
            ser = __system_property_serial(pi);
            mode = property_get_int32("sys.gammaos.nano.ctr.perf", 0);
        }
    }
    nsecs_t mark() {
        if (mode == 2) glFinish();
        return systemTime(SYSTEM_TIME_MONOTONIC);
    }
    void frame(nsecs_t now) {
        if (last) {
            const double d = ms(last, now);
            interval += d;
            worst = std::max(worst, d);
        }
        last = now;
        if (++frames < 120) return;
        if (mode == 3) sCtrGpu.log();
        ALOGI("ctr perf: interval %.2f ms (worst %.2f) update %.2f top %.2f bottom %.2f%s", interval / frames, worst,
              update / frames, top / frames, bottom / frames, mode >= 2 ? " (gpu synced)" : "");
        *this = CtrPerf{mode, 0, 0, 0, 0, 0, 0, now};
    }
};
CtrPerf sCtrPerf;

}  // namespace

bool NanoMenu::ctrDualPanel() const {
    return sAhbTargetSecondary.glFbo != 0 || !mSecondaryEglSurfaces.empty();
}

// Layout width a screen gets in a rectangle: wider than its design width when the rectangle is
// (widescreen adaptation, persist.gammaos.nano.ctr.widen, default on), capped at 2.4:1.
static float ctrScreenWidth(bool top, float rw, float rh) {
    const float design = top ? 400.0f : 320.0f;
    if (rh <= 0 || !property_get_bool("persist.gammaos.nano.ctr.widen", true)) return design;
    return std::clamp(240.0f * rw / rh, design, 240.0f * 2.4f);
}

// Contain-fit a screen of lw x lh layout units into a rectangle (centred).
static void ctrFit(float lw, float lh, float rx, float ry, float rw, float rh, float* ox, float* oy, float* ow, float* oh) {
    const float s = std::min(rw / lw, rh / lh);
    *ow = lw * s;
    *oh = lh * s;
    *ox = rx + (rw - *ow) * 0.5f;
    *oy = ry + (rh - *oh) * 0.5f;
}

void NanoMenu::ctrBottomRect(float& rx, float& ry, float& rw, float& rh) const {
    // the bottom screen's own panel (dual) or its part of this one
    float x = 0, y = 0, w = (float)mWidth, h = (float)mHeight;
    if (ctrDualPanel()) {
        if (sAhbTargetSecondary.glFbo) {   // DRM: the bottom panel's scanout buffer
            w = (float)sAhbTargetSecondary.w;
            h = (float)sAhbTargetSecondary.h;
        } else if (!mSecondaryCreatedSize.empty() && mSecondaryCreatedSize[0].first > 0) {
            w = (float)mSecondaryCreatedSize[0].first;
            h = (float)mSecondaryCreatedSize[0].second;
        }
    } else if (mHeight >= mWidth * 0.9f) {   // stacked: the bottom half
        y = h * 0.5f;
        h *= 0.5f;
    }
    const float lw = ctrScreenWidth(false, w, h);
    ctrFit(lw, 240, x, y, w, h, &rx, &ry, &rw, &rh);
}

void NanoMenu::ctrDrawScreen(bool top, float rx, float ry, float rw, float rh) {
    if (sCtrPerf.mode == 4) return;   // probe: the present path's own ceiling
    const float lw = top ? mCtrHome->topWidth() : mCtrHome->bottomWidth();
    float ox, oy, ow, oh;
    ctrFit(lw, 240.0f, rx, ry, rw, rh, &ox, &oy, &ow, &oh);
    // letterbox bars in the screen's own ground colour (only outside the screen: every pixel
    // filled twice costs fill rate on low end GPUs)
    const float g = top ? 232.0f / 255.0f : 0.0f, gb = top ? 238.0f / 255.0f : 0.0f;
    if (oy > ry + 0.5f) drawQuad(rx, ry, rw, oy - ry, g, g, gb, 1.0f);
    if (oy + oh < ry + rh - 0.5f) drawQuad(rx, oy + oh, rw, ry + rh - oy - oh, g, g, gb, 1.0f);
    if (ox > rx + 0.5f) drawQuad(rx, oy, ox - rx, oh, g, g, gb, 1.0f);
    if (ox + ow < rx + rw - 0.5f) drawQuad(ox + ow, oy, rx + rw - ox - ow, oh, g, g, gb, 1.0f);
    // the top screen ground (banner.ts TOP_CLEAR) under the layouts, unless the opaque 3D wall
    // covers it; the bottom base layout is drawn onto the panel's black clear
    if (top && !mCtrHome->topCovered()) drawQuad(ox, oy, ow, oh, 232.0f / 255.0f, 232.0f / 255.0f, 238.0f / 255.0f, 1.0f);
    float proj[16];
    ctr::CtrHome::projection(proj, lw, 240.0f, ox, oy, ow, oh, (float)mWidth, (float)mHeight, sDrmRotMat);
    GLint vp[4];
    glGetIntegerv(GL_VIEWPORT, vp);
    if (top) mCtrHome->drawTop(proj, vp[0], vp[1], vp[2], vp[3]);
    else mCtrHome->drawBottom(proj, vp[0], vp[1], vp[2], vp[3]);
}

// Cheap signature of what the grid shows: the Game category entries and every system's game
// count. Checked every 30 frames; a change (scan finished, settings changed) reloads the grids.
uint64_t NanoMenu::ctrModelSignature() const {
    uint64_t h = 1469598103934665603ull;
    auto mix = [&](uint64_t v) { h ^= v; h *= 1099511628211ull; };
    for (const Ps3Cat& c : mPs3Cats) {
        if (c.name != "Game") continue;
        for (const Ps3Item& it : c.items) {
            mix((uint64_t)it.kind);
            mix((uint64_t)(uint32_t)it.a);
            mix((uint64_t)(uint32_t)it.b);
            for (char ch : it.label) mix((uint64_t)(unsigned char)ch);
        }
    }
    for (const XmbSystem& s : mXmbSystems) mix((uint64_t)s.roms.size());
    mix((uint64_t)mXmbRecent.size());
    return h;
}


void NanoMenu::renderCtrPrimary() {
    sCtrPerf.refresh();
    if (sCtrPerf.mode != 3) ctr::CtrHome::perfMark = nullptr;
    if (sCtrPerf.mode == 3) {
        sCtrGpu.frameStart();
        ctr::CtrHome::perfMark = [](const char* n) { sCtrGpu.mark(n); };
        sCtrGpu.mark("pre");
    }
    const nsecs_t perfStart = sCtrPerf.mode ? sCtrPerf.mark() : 0;
    if (sCtrPerf.mode) sCtrPerf.frame(perfStart);
    if (!mPs3MenuBuilt) initPs3Menu();   // the shared XMB hierarchy feeds the grid (systems, apps)
    if (!ctrEnsure()) {
        renderPs3Xmb();
        return;
    }
    if (++mCtrSigFrame >= 30) {
        mCtrSigFrame = 0;
        const uint64_t sig = ctrModelSignature();
        if (sig != mCtrModelSig) {
            mCtrModelSig = sig;
            mCtrHome->reloadItems();
        }
    }
    // input for this frame: held buttons (+ the circle pad as the D-pad), then the 60 Hz ticks
    uint32_t held = mCtrHeld;
    if (mCtrStickX < 0) held |= ctr::CTR_LEFT;
    if (mCtrStickX > 0) held |= ctr::CTR_RIGHT;
    if (mCtrStickY < 0) held |= ctr::CTR_UP;
    if (mCtrStickY > 0) held |= ctr::CTR_DOWN;
    // scripted keys for tests (sys.gammaos.nano.ctr.keys), read only when the prop changes
    {
        static const prop_info* pi = nullptr;
        static uint32_t ser = 0;
        if (!pi) pi = __system_property_find("sys.gammaos.nano.ctr.keys");
        if (pi && __system_property_serial(pi) != ser) {
            ser = __system_property_serial(pi);
            char v[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.nano.ctr.keys", v, "");
            static const struct { const char* n; uint32_t b; } kNames[] = {
                {"A", ctr::CTR_A}, {"B", ctr::CTR_B}, {"X", ctr::CTR_X}, {"Y", ctr::CTR_Y},
                {"L", ctr::CTR_L}, {"R", ctr::CTR_R}, {"UP", ctr::CTR_UP}, {"DOWN", ctr::CTR_DOWN},
                {"LEFT", ctr::CTR_LEFT}, {"RIGHT", ctr::CTR_RIGHT}, {"HOME", 0x80000000u}};
            std::string list = v;
            size_t p = 0;
            while (!list.empty() && p <= list.size()) {
                size_t q = list.find(',', p);
                const std::string k = list.substr(p, q == std::string::npos ? std::string::npos : q - p);
                for (const auto& n : kNames) if (k == n.n) mCtrScript.push_back(n.b);
                if (q == std::string::npos) break;
                p = q + 1;
            }
        }
        if (!mCtrScript.empty()) {
            const int f = mCtrScriptFrame++;
            if (f < 6) {
                if (mCtrScript.front() == 0x80000000u) { if (f == 0) mCtrHome->pressHome(); }
                else held |= mCtrScript.front();
            } else if (f >= 12) {
                mCtrScript.erase(mCtrScript.begin());
                mCtrScriptFrame = 0;
            }
        }
    }
    if (!ctrOwnsInput()) {
        // a nano list or dialog took the keys: nothing stays held behind it
        held = 0;
        mCtrHeld = 0;
        mCtrStickX = mCtrStickY = 0;
    }
    mCtrHome->setHeld(held);
    // screen widths from the rectangles each screen is drawn in
    {
        const float W = (float)mWidth, H = (float)mHeight;
        float topW = 400, botW = 320;
        float bx, by, bw, bh;
        ctrBottomRect(bx, by, bw, bh);
        botW = ctrScreenWidth(false, bw, bh);
        if (ctrDualPanel()) topW = ctrScreenWidth(true, W, H);
        else if (H >= W * 0.9f) topW = ctrScreenWidth(true, W, H * 0.5f);
        mCtrHome->setScreenWidths(topW, botW);
    }
    mCtrHome->update(systemTime(SYSTEM_TIME_MONOTONIC));
    const nsecs_t perfUpdated = sCtrPerf.mode ? sCtrPerf.mark() : 0;
    if (sCtrPerf.mode) sCtrPerf.update += CtrPerf::ms(perfStart, perfUpdated);
    const float W = (float)mWidth, H = (float)mHeight;
    if (ctrDualPanel()) {
        ctrDrawScreen(true, 0, 0, W, H);
        if (sCtrPerf.mode) sCtrPerf.top += CtrPerf::ms(perfUpdated, sCtrPerf.mark());
    } else if (H >= W * 0.9f) {
        ctrDrawScreen(true, 0, 0, W, H * 0.5f);
        ctrDrawScreen(false, 0, H * 0.5f, W, H * 0.5f);
    } else {
        ctrDrawScreen(false, 0, 0, W, H);
    }
    // nano's own lists and dialogs (settings, quick menu, media) over the home
    if (!mPs3Stack.empty() || ndsInModal()) renderPs3Xmb();
    if (sCtrPerf.mode == 3) sCtrGpu.mark("rest");   // the rest of nano's frame until the next bottom pass
}

void NanoMenu::renderCtrSecondary() {
    if (!mCtrHome || !mCtrHome->loaded()) return;
    const nsecs_t t0 = sCtrPerf.mode ? sCtrPerf.mark() : 0;
    if (sCtrPerf.mode == 3) sCtrGpu.mark("bottom");   // also ends the "rest of frame" span
    ctrDrawScreen(false, 0, 0, (float)mWidth, (float)mHeight);
    if (sCtrPerf.mode == 3) sCtrGpu.mark(nullptr);
    if (sCtrPerf.mode) sCtrPerf.bottom += CtrPerf::ms(t0, sCtrPerf.mark());
}

}  // namespace android
