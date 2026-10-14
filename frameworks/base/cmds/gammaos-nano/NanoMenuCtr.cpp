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
#include "NanoCtrPanel.h"
#include "NanoCtrArt.h"
#include "NanoMenuUtils.h"
#include "NanoI18n.h"
#include "NanoMenuStrings.h"

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

    // The ROM a grid title stands for ("" for apps and other entries).
    std::string romPathOf(const NanoMenu::Ps3Item& it) {
        if (it.kind == NanoMenu::PS3_ROM && it.a >= 0 && it.a < (int)mMenu->mXmbSystems.size()) {
            const auto& sys = mMenu->mXmbSystems[it.a];
            if (it.b >= 0 && it.b < (int)sys.roms.size()) return sys.roms[it.b];
        } else if (it.kind == NanoMenu::PS3_RECENT && it.a >= 0 && it.a < (int)mMenu->mXmbRecent.size()) {
            return mMenu->mXmbRecent[it.a].romPath;
        }
        return std::string();
    }

    // A title's own art: the SMDH icon of a 3DS ROM, the banner icon of a DS ROM, else the
    // scraped cover (when Boxart is on), else nano's system glyph.
    GLuint titleIconTexture(const ctr::HomeItem& item, int* w, int* h, IconArt* art) override {
        auto t = mTitles.find(item.id);
        if (t == mTitles.end()) return 0;
        const NanoMenu::Ps3Item& it = t->second;
        if (it.kind == NanoMenu::PS3_APP) return 0;   // decoded from the icon cache (titleIcon)
        const std::string rom = romPathOf(it);
        if (!rom.empty()) {
            if (mMenu->mCtrArt && ctr::RomArt::isCtrRom(rom)) {
                if (GLuint g = mMenu->mCtrArt->icon(rom)) { *w = *h = 48; return g; }
            }
            std::string ds;
            if (mMenu->ndsIsDsRomItem(it, &ds)) {
                if (GLuint g = mMenu->ndsBannerTex(ds)) { *w = *h = 32; return g; }
            }
            if (mMenu->mScrapeBoxartOn) {
                float ar = 1.0f;
                if (GLuint g = mMenu->romBoxartTex(rom, &ar)) {
                    *w = *h = 256;
                    art->coverAspect = ar > 0 ? ar : 1.0f;
                    return g;
                }
            }
        }
        if (it.iconTex) {
            *w = *h = 48;
            art->mono = item.monoIcon;
            return it.iconTex;
        }
        return 0;
    }
    uint64_t artGeneration() override { return mMenu->mArtGeneration; }

    void titleArt(const ctr::HomeItem& item, TitleArt* out) override {
        auto t = mTitles.find(item.id);
        if (t == mTitles.end()) return;
        const NanoMenu::Ps3Item& it = t->second;
        const std::string rom = romPathOf(it);
        if (rom.empty()) return;
        if (mMenu->mCtrArt && ctr::RomArt::isCtrRom(rom)) {
            out->cbmd = mMenu->mCtrArt->banner(rom, &out->pending);
            if (out->cbmd || out->pending) return;
            // a 3DS title without a banner: its icon on the card
            if (GLuint g = mMenu->mCtrArt->icon(rom)) { out->icon = g; out->w = out->h = 48; }
            return;
        }
        std::string ds;
        if (mMenu->ndsIsDsRomItem(it, &ds)) {
            if (GLuint g = mMenu->ndsBannerTex(ds)) { out->icon = g; out->w = out->h = 32; out->dsIcon = true; }
            return;
        }
        if (mMenu->mScrapeBoxartOn) {
            float ar = 1.0f;
            if (GLuint g = mMenu->romBoxartTex(rom, &ar)) {
                out->icon = g;
                out->w = out->h = 256;
                out->art.coverAspect = ar > 0 ? ar : 1.0f;
            }
        }
    }

    // A 3DS banner's BCWAV plays once when the banner shows; leaving the title stops it.
    void playBannerSound(const std::shared_ptr<const ctr::RomBanner>& b) override {
        stopBannerSound();
        if (!mMenu->mCtrAudio || !b || !b->hasSound || b->sound.pcm.empty()) return;
        char v[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.nav_sounds", v, "1");
        if (v[0] == '0' || v[0] == 'f' || v[0] == 'F') return;
        const ctr::RomSound& s = b->sound;
        // banner BCWAVs are mastered at full level: no makeup gain (the effects get 2x)
        mBannerVoice = mMenu->mCtrAudio->playPcm(b, s.pcm.data(), s.frames, s.channels, s.sampleRate, false, 0, 0, 0.5f);
    }
    void stopBannerSound() override {
        if (mBannerVoice && mMenu->mCtrAudio) mMenu->mCtrAudio->stop(mBannerVoice, 10);
        mBannerVoice = 0;
    }
    int mBannerVoice = 0;

    const NanoMenu::Ps3Item* titleOf(const std::string& id) const {
        auto t = mTitles.find(id);
        return t == mTitles.end() ? nullptr : &t->second;
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
        // the BGM follows Theme Settings > Menu Music (shared with the DSi theme's music)
        property_get(bgm ? "persist.gammaos.nano.nds.ambiance" : "persist.gammaos.nano.nav_sounds", v, "1");
        if (v[0] == '0' || v[0] == 'f' || v[0] == 'F') return 0;
        // no BGM while a game starts or runs, over a live app, or with a media player
        if (bgm && !mMenu->ctrBgmAllowed()) return 0;
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
        if (!mCtrArt) mCtrArt = new ctr::RomArt("/data/system/nano_ctr_rom");
        mCtrHome = new ctr::CtrHome(root, mCtrHost);
        ALOGI("ctr: 3DS theme assets at %s", root.c_str());
    }
    mCtrHome->setLanguage("English");
    if (!mCtrHome->load()) {
        ALOGE("ctr: 3DS theme failed to load");
        return false;
    }
    if (!mCtrPanel) {
        auto sound = [this](const char* name) { if (mCtrHost) mCtrHost->playSound(name); };
        mCtrPanel = new ctr::CtrPanel(mCtrHome->assets(), sound);
        mCtrDialog = new ctr::CtrDialog(mCtrHome->assets(), sound);
        mCtrHome->overlay = [this](ctr::Renderer& r, bool top) {
            if (top) { mCtrPanel->drawTop(r); mCtrDialog->drawTop(r); }
            else { mCtrPanel->drawBottom(r); mCtrDialog->drawBottom(r); }
        };
        mCtrHome->onTick = [this]() { mCtrPanel->tick(); mCtrDialog->tick(); };
    }
    return true;
}

void NanoMenu::ctrRelease() {
    if (mCtrArt) { mCtrArt->releaseGl(); mArtGeneration++; }
    if (mCtrPanel) { mCtrPanel->releaseGl(); delete mCtrPanel; mCtrPanel = nullptr; }
    if (mCtrDialog) { mCtrDialog->releaseGl(); delete mCtrDialog; mCtrDialog = nullptr; }
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
    // the events taken here skip nano's own input path: they still count as input for the idle
    // frame governor (else a minute after the last touch the home drops to the idle rate)
    auto taken = [this]() { mLastInputMs = (int64_t)android::uptimeMillis(); return true; };
    if (ctrSwitchKey(type, code, value)) return taken();
    if (!ctrOwnsInput()) return false;
    auto setBit = [&](uint32_t bit, bool on) {
        if (on) mCtrHeld |= bit; else mCtrHeld &= ~bit;
    };
    if (type == EV_KEY) {
        if (value == 2) return taken();   // key repeat: the menu repeats on its own
        const bool on = value != 0;
        switch (code) {
            case BTN_SOUTH: case KEY_ENTER: setBit(ctr::CTR_A, on); return taken();
            case BTN_EAST: case KEY_BACK: case KEY_ESC: setBit(ctr::CTR_B, on); return taken();
            case BTN_NORTH: setBit(ctr::CTR_X, on); return taken();
            case BTN_WEST: setBit(ctr::CTR_Y, on); return taken();
            case BTN_TL: case KEY_L: setBit(ctr::CTR_L, on); return taken();
            case BTN_TR: case KEY_R: setBit(ctr::CTR_R, on); return taken();
            case BTN_TL2: setBit(ctr::CTR_ZL, on); return taken();
            case BTN_TR2: setBit(ctr::CTR_ZR, on); return taken();
            case BTN_DPAD_UP: case KEY_UP: setBit(ctr::CTR_UP, on); return taken();
            case BTN_DPAD_DOWN: case KEY_DOWN: setBit(ctr::CTR_DOWN, on); return taken();
            case BTN_DPAD_LEFT: case KEY_LEFT: setBit(ctr::CTR_LEFT, on); return taken();
            case BTN_DPAD_RIGHT: case KEY_RIGHT: setBit(ctr::CTR_RIGHT, on); return taken();
            case BTN_START:
                // the focused title's options (Information, collections, ...) like the other themes'
                if (on && ctrOpenOptions()) return taken();
                return false;
            default: return false;   // SELECT / system keys stay with nano
        }
    }
    if (type == EV_ABS) {
        if (code == ABS_HAT0X) {
            setBit(ctr::CTR_LEFT, value < 0);
            setBit(ctr::CTR_RIGHT, value > 0);
            return taken();
        }
        if (code == ABS_HAT0Y) {
            setBit(ctr::CTR_UP, value < 0);
            setBit(ctr::CTR_DOWN, value > 0);
            return taken();
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
            return taken();
        }
    }
    return false;
}

// A touch frame: nano has the raw touch state (mTouchDown / mTouchRawX/Y); map it into bottom
// screen layout space through the rectangle the bottom screen is drawn in.
void NanoMenu::ctrTouchFrame() {
    if (mOskActive || mGSearchActive) return;   // nano's own touch handling has them
    const bool modal = ctrModalKind() == CTR_MODAL_PANEL;
    if (!ctrOwnsInput() && !modal) return;
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
    if (modal) {
        ctrModalTouch(t.x, t.y);
        return;
    }
    mCtrTouchPrev = false;
    mCtrHome->setTouch(t);
}

// ---------------------------------------------------------------------------- lists and dialogs

int NanoMenu::ctrModalKind() const {
    if (!mCtrTheme || !mCtrHome || !mCtrHome->loaded() || mPs3BootActive || mSetupWizardActive) return CTR_MODAL_NONE;
    // screens without a 3DS look yet: the shared XMB chrome
    // (the keyboard and the search results draw on their own over the 3DS screens: ctrRenderOverlays)
    const bool other = mVidActive || mMpActive || mPvActive || mPs3WizActive ||
                       mPhotoMultiActive || mScrapeProgActive ||
                       mMtpActive || mPvPlChooserActive || mVidPlChooserActive || mMpPlChooserActive ||
                       ps3TopScreenKind() == PHOTO_GRID;
    if (other) return CTR_MODAL_FALLBACK;
    if (mPs3DlgActive || mPs3OptActive || mPs3TzActive || mPs3LangActive || mPs3BrightSlider || !mPs3Stack.empty())
        return CTR_MODAL_PANEL;
    return CTR_MODAL_NONE;
}

namespace {
uint64_t ctrLevelId(int kind, size_t depth, const std::string& title, size_t rows) {
    uint64_t h = 1469598103934665603ull;
    auto mix = [&](uint64_t v) { h ^= v; h *= 1099511628211ull; };
    mix((uint64_t)kind);
    mix((uint64_t)depth);
    for (char c : title) mix((uint64_t)(unsigned char)c);
    (void)rows;
    return h;
}

bool ctrIsSwitch(const std::string& v, bool* on) {
    static const char* kOn[] = {"On", "ON", "on", "Enabled", "Yes"};
    static const char* kOff[] = {"Off", "OFF", "off", "Disabled", "No"};
    for (const char* k : kOn) if (v == k || v == trDyn(k)) { *on = true; return true; }
    for (const char* k : kOff) if (v == k || v == trDyn(k)) { *on = false; return true; }
    return false;
}
}  // namespace

void NanoMenu::ctrModalUpdate() {
    if (!mCtrPanel || !mCtrDialog) return;
    // a playing BGM fades out the moment the home stops being the home (a launch, an app, a player)
    if (mCtrAudio && !ctrBgmAllowed() && mCtrAudio->bgmPlaying()) mCtrAudio->stopBgm(20);
    // The closing flags belong to the theme that animates the close (every theme clears them
    // when its fade ends; input waits on them): option menus and choosers leave the panel at
    // once, a message dialog when the 3DS dialog finished fading out.
    if (mPs3OptClosing && !mPs3OptActive) { mPs3OptClosing = false; mPs3OptCloseAnim = 0.0f; }
    if (mPs3DlgClosing && !mPs3DlgActive && (ndsDlgIsSidePanel() || !mCtrDialog->active())) {
        mPs3DlgClosing = false;
        mPs3DlgCloseAnim = 0.0f;
    }
    if (mPs3OptActive) mPs3OptAnim = 1.0f;
    if (mPs3DlgActive) mPs3DlgAnim = 1.0f;
    const float bw = mCtrHome->bottomWidth();
    mCtrPanel->setWidth(bw);
    mCtrDialog->setWidth(bw);
    mCtrPanel->setTopSize(mCtrHome->topWidth(), mCtrHome->topHeight());
    mCtrDialog->setTopSize(mCtrHome->topWidth(), mCtrHome->topHeight());
    if (ctrModalKind() != CTR_MODAL_PANEL) {
        mCtrHome->setTopBannerHidden(false);
        mCtrPanel->hide();
        mCtrDialog->hide(true);
        mCtrPanelWasShown = false;
        return;
    }
    // ---- the panel: an option menu, a list-style dialog, or the current list level ----
    std::vector<ctr::PanelRow> rows;
    std::string title;
    int sel = 0;
    uint64_t level = 0;
    // a list-style dialog (a chooser) shows as the panel while it is open; a closing one is gone
    const bool dlgSide = ndsDlgIsSidePanel();
    // a numeric slider is the console's progress dialog (a window with the bar), not a list
    const bool slider = ctrSliderActive();
    // an information page (a game's details, App Information, the guide): paragraphs in the panel
    const bool info = ctrInfoActive();
    const bool dlgList = mPs3DlgActive && dlgSide && !slider && !info;
    const bool picker = mPs3TzActive || mPs3LangActive;
    if (!info) mCtrInfoNav = 0;
    if (info) {
        auto para = [&](const std::string& text) {
            if (text.empty()) return;
            ctr::PanelRow r;
            r.kind = ctr::PanelRow::Text;
            r.label = text;
            rows.push_back(r);
        };
        auto fact = [&](const char* label, const std::string& v) {
            if (!v.empty()) para(std::string(trDyn(label)) + ": " + v);
        };
        if (mPs3DlgRomInfo) {
            fact("Genre", mPs3RomInfoGenre);
            fact("Players", mPs3RomInfoPlayers);
            fact("Rating", mPs3RomInfoRating);
            fact("Released", mPs3RomInfoDate);
            fact("Developer", mPs3RomInfoDev);
            fact("Publisher", mPs3RomInfoPub);
            para(mPs3RomInfoSyn);
            fact("System", mPs3RomInfoSystem);
            fact(mPs3RomInfoCoreIsApp ? "App" : "Core", mPs3RomInfoCore);
            fact("Size", mPs3RomInfoSize);
            fact("File", mPs3RomInfoFileName);
            fact("Folder", mPs3RomInfoDir);
        } else {
            // a text body: one paragraph per line; the column padding of "Label     value" lines
            // (laid out for a fixed width font) becomes "Label: value"
            const std::string body = trDyn(mPs3DlgBody.c_str());
            size_t p = 0;
            while (p <= body.size()) {
                size_t q = body.find('\n', p);
                std::string line = body.substr(p, q == std::string::npos ? std::string::npos : q - p);
                const size_t gap = line.find("  ");
                if (gap != std::string::npos && gap > 0) {
                    const size_t v = line.find_first_not_of(' ', gap);
                    if (v != std::string::npos) line = line.substr(0, gap) + ": " + line.substr(v);
                }
                para(line);
                if (q == std::string::npos) break;
                p = q + 1;
            }
        }
        sel = -1;
        title = mPs3DlgTitle.empty() ? trDyn("Information") : trDyn(mPs3DlgTitle.c_str());
        level = ctrLevelId(6, mPs3Stack.size(), title, rows.size());
    } else if (picker) {
        // Time Zone / System Language: a list of choices; the keys stay with nano's picker handlers
        if (mPs3TzActive) {
            for (const auto& z : mTzEntries) {
                ctr::PanelRow r;
                r.label = z.display;
                rows.push_back(r);
            }
            sel = mTzSelected;
            title = trDyn("Time Zone");
        } else {
            nanoSetLocale((NanoLocale)mLangSelected);   // live preview as the cursor moves
            // a name the console's fonts cannot draw (Arabic) shows its English name
            std::shared_ptr<ctr::Font> font = mCtrHome->assets().font("cbf_std");
            for (int i = 0; i < LOCALE_COUNT; i++) {
                const LocaleInfo& li = nanoGetLocaleInfo((NanoLocale)i);
                ctr::PanelRow r;
                r.label = li.nativeName;
                if (font) {
                    for (char32_t c : ctr::utf8ToU32(r.label))
                        if (c > ' ' && !font->has(c)) { r.label = li.englishName; break; }
                }
                rows.push_back(r);
            }
            sel = mLangSelected;
            title = trDyn("System Language");
        }
        level = ctrLevelId(mPs3TzActive ? 4 : 5, mPs3Stack.size(), title, rows.size());
    } else if (mPs3OptActive) {
        const bool subOpen = mPs3OptSubOpen && mPs3OptSel >= 0 && mPs3OptSel < (int)mPs3OptSubRows.size() &&
                             !mPs3OptSubRows[mPs3OptSel].empty();
        if (subOpen) {
            for (const auto& sr : mPs3OptSubRows[mPs3OptSel]) {
                ctr::PanelRow r;
                r.label = trDyn(sr.label.c_str());
                rows.push_back(r);
            }
            sel = mPs3OptSubSel;
            title = mPs3OptSel < (int)mPs3OptLabels.size() ? trDyn(mPs3OptLabels[mPs3OptSel].c_str()) : trDyn("Options");
        } else {
            for (int i = 0; i < (int)mPs3OptLabels.size(); i++) {
                if (i < (int)mPs3OptSep.size() && mPs3OptSep[i]) continue;
                if (i == mPs3OptSel) sel = (int)rows.size();
                ctr::PanelRow r;
                r.label = trDyn(mPs3OptLabels[i].c_str());
                rows.push_back(r);
            }
            title = mPs3OptCtxLabel.empty() ? trDyn("Options") : trDyn(mPs3OptCtxLabel.c_str());
        }
        level = ctrLevelId(subOpen ? 2 : 1, mPs3Stack.size(), title, rows.size());
    } else if (dlgList) {
        for (const auto& o : mPs3DlgOptions) {
            ctr::PanelRow r;
            r.label = trDyn(o.c_str());
            rows.push_back(r);
        }
        sel = mPs3DlgSel;
        title = mPs3DlgTitle.empty() ? trDyn("Options") : trDyn(mPs3DlgTitle.c_str());
        level = ctrLevelId(3, mPs3Stack.size(), title, rows.size());
    } else if (!mPs3Stack.empty()) {
        const Ps3Level& lv = mPs3Stack.back();
        // every item is a row (nano's selection indexes the items): one that does nothing when
        // chosen (an information line, the empty list's note) is shown dimmed
        for (const Ps3Item& it : lv.items) {
            ctr::PanelRow r;
            r.label = trDyn(it.label.c_str());
            r.enabled = !(it.kind == PS3_DATA_LEAF && it.action == 0);
            bool on = false;
            if (ctrItemSwitch(it, &on)) { r.kind = ctr::PanelRow::Toggle; r.on = on; }
            else {
                const std::string v = it.binding ? resolvePs3ItemValue(it) : it.value;
                if (!v.empty()) { r.kind = ctr::PanelRow::Value; r.value = trDyn(v.c_str()); }
            }
            rows.push_back(r);
        }
        sel = lv.sel;
        title = trDyn(lv.title.c_str());
        level = ctrLevelId(0, mPs3Stack.size(), lv.title, rows.size());
    }
    const bool panel = info || picker || mPs3OptActive || dlgList || !mPs3Stack.empty();
    mCtrHome->setTopBannerHidden(panel);
    if (panel) {
        mCtrPanel->set(title, rows, sel, level);
        mCtrPanel->show();
        // the D-pad scrolls a page a line (24) at a time, L / R a screenful
        if (info && mCtrInfoNav) mCtrPanel->scrollBy(24.0f * (float)mCtrInfoNav);
        mCtrInfoNav = 0;
        mCtrPanelWasShown = true;
    } else {
        mCtrPanel->hide();
        mCtrPanelWasShown = false;
    }
    // ---- the message dialog (confirmations, notices) over everything ----
    if (mPs3BrightSlider) {
        // the Quick Menu brightness: live, in percent like the other themes' bar
        char pct[16];
        snprintf(pct, sizeof(pct), "%d%%", mBrightness * 100 / 255);
        mCtrDialog->set(std::string(), {}, 0);
        mCtrDialog->setProgress(std::string(trDyn("Brightness")) + "\n\n" + pct, (float)mBrightness / 255.0f);
        mCtrDialog->show();
    } else if (slider) {
        const float span = mPs3DlgSldMax - mPs3DlgSldMin;
        const float frac = span > 0 ? std::clamp((mPs3DlgSldVal - mPs3DlgSldMin) / span, 0.0f, 1.0f) : 0.0f;
        char num[32];
        snprintf(num, sizeof(num), "%.*f", std::clamp(mPs3DlgSldScale, 0, 4), mPs3DlgSldVal);
        std::string text = mPs3DlgTitle.empty() ? std::string() : std::string(trDyn(mPs3DlgTitle.c_str())) + "\n\n";
        mCtrDialog->set(std::string(), {}, 0);
        mCtrDialog->setProgress(text + num, frac);
        mCtrDialog->show();
    } else if ((mPs3DlgActive || mPs3DlgClosing) && !dlgSide && !info && !(!mPs3DlgActive && ctrInfoDialog())) {
        mCtrDialog->setProgress(std::string(), -1);
        std::string text;
        if (!mPs3DlgTitle.empty()) text = trDyn(mPs3DlgTitle.c_str());
        if (!mPs3DlgBody.empty()) text += (text.empty() ? "" : "\n\n") + std::string(trDyn(mPs3DlgBody.c_str()));
        std::vector<std::string> buttons;
        for (const auto& o : mPs3DlgOptions) buttons.push_back(trDyn(o.c_str()));
        mCtrDialog->set(text, buttons, mPs3DlgSel);
        if (mPs3DlgActive) mCtrDialog->show();
        else mCtrDialog->hide(false);
    } else {
        mCtrDialog->hide(true);
    }
}

bool NanoMenu::ctrModalTouch(float x, float y) {
    const bool down = mTouchDown && !mCtrTouchPrev, up = !mTouchDown && mCtrTouchPrev, held = mTouchDown && mCtrTouchPrev;
    mCtrTouchPrev = mTouchDown;
    auto launched = [this]() {
        if (mWaitForRelease && !mOverlayMode && mLaunchFadeStart == 0) mLaunchFadeStart = uptimeMillis();
        mDisplayDirty = true;
    };
    // the Quick Menu brightness: a drag sets it live, a tap elsewhere closes it
    if (mPs3BrightSlider && mCtrDialog->active()) {
        const float f = mCtrDialog->progressTouch(down, held, up, x, y);
        if (down) mCtrSliderTap = f < 0;
        if (f >= 0) {
            const int b = std::clamp((int)lroundf(f * 255.0f), 1, 255);
            if (b != mBrightness) {
                mBrightness = b;
                applyBrightness();
                mDisplayDirty = true;
            }
        } else if (up && mCtrSliderTap) {
            mCtrSliderTap = false;
            mPs3BrightSlider = false;
            mShowBrightnessBar = false;
            mBrightnessBarTimer = 0;
            mDisplayDirty = true;
        }
        return true;
    }
    // a slider: drag the bar; a tap anywhere else keeps the value (A), like the notices
    if (ctrSliderActive() && mCtrDialog->active()) {
        const float f = mCtrDialog->progressTouch(down, held, up, x, y);
        if (down) mCtrSliderTap = f < 0;
        if (f >= 0) ctrSliderSet(f);
        else if (up && mCtrSliderTap) {
            mCtrSliderTap = false;
            ps3XmbSelect();
            launched();
        }
        return true;
    }
    // the dialog is in front
    if (mCtrDialog->active() && (mPs3DlgActive || mPs3DlgClosing) && !ndsDlgIsSidePanel()) {
        const int b = mCtrDialog->touch(down, up, x, y);
        if (b >= 0 && b < (int)mPs3DlgOptions.size()) {
            mPs3DlgSel = b;
            ps3XmbSelect();
            launched();
        } else if (b < 0 && up && mPs3DlgOptions.size() <= 1 && mPs3DlgActive) {
            ps3XmbSelect();   // a notice: any tap is OK
            launched();
        }
        return true;
    }
    if (!mCtrPanel->active()) return false;
    const ctr::PanelHit h = mCtrPanel->touch(down, held, up, x, y);
    switch (h.what) {
        case ctr::PanelHit::Row: {
            if (mPs3TzActive || mPs3LangActive) {
                if (mPs3TzActive) { mTzSelected = h.row; closeTimezoneGlobe(true); }
                else { mLangSelected = h.row; closeLanguagePicker(true); }
            } else if (mPs3OptActive) {
                const bool subOpen = mPs3OptSubOpen && mPs3OptSel >= 0 && mPs3OptSel < (int)mPs3OptSubRows.size() &&
                                     !mPs3OptSubRows[mPs3OptSel].empty();
                if (subOpen) {
                    mPs3OptSubSel = h.row;
                } else {
                    int vis = 0;
                    for (int i = 0; i < (int)mPs3OptLabels.size(); i++) {
                        if (i < (int)mPs3OptSep.size() && mPs3OptSep[i]) continue;
                        if (vis++ == h.row) { mPs3OptSel = i; break; }
                    }
                }
                xmbOptEnter();
            } else if ((mPs3DlgActive || mPs3DlgClosing) && ndsDlgIsSidePanel()) {
                mPs3DlgSel = h.row;
                ps3XmbSelect();
            } else if (!mPs3Stack.empty()) {
                Ps3Level& lv = mPs3Stack.back();
                const int idx = h.row;
                if (idx < 0 || idx >= (int)lv.items.size()) break;
                // a switch: the tapped side is set at once (the console's OFF / ON pair)
                bool on = false;
                lv.sel = idx;
                if (ctrItemSwitch(lv.items[idx], &on) && h.column >= 0) {
                    ctrSetSwitch(idx, h.column == 1);
                    break;
                }
                ps3XmbSelect();
            }
            launched();
            break;
        }
        case ctr::PanelHit::Close:
            // the close button leaves the whole panel
            if (mPs3TzActive) { closeTimezoneGlobe(false); break; }
            if (mPs3LangActive) { closeLanguagePicker(false); break; }
            if (mPs3OptActive || mPs3DlgActive) { ps3XmbBack(); break; }
            for (int guard = 0; guard < 16 && !mPs3Stack.empty() && !mPs3OptActive && !mPs3DlgActive; guard++) {
                const size_t depth = mPs3Stack.size();
                ps3XmbBack();
                if (mPs3Stack.size() == depth) break;
            }
            mDisplayDirty = true;
            break;
        case ctr::PanelHit::Outside:
            if (mPs3TzActive) { closeTimezoneGlobe(false); break; }
            if (mPs3LangActive) { closeLanguagePicker(false); break; }
            ps3XmbBack();
            mDisplayDirty = true;
            break;
        default:
            break;
    }
    return true;
}

// Opens nano's option menu for the title under the 3DS cursor: the menu takes its context from
// the current list, so the title is offered as a one item list while it opens.
bool NanoMenu::ctrOpenOptions() {
    if (!mCtrHome || !mCtrHost) return false;
    const ctr::HomeItem* h = mCtrHome->focusedTitle();
    const Ps3Item* it = h ? mCtrHost->titleOf(h->id) : nullptr;
    if (!it) return false;
    Ps3Level lvl;
    lvl.title = it->label;
    lvl.items.push_back(*it);
    lvl.sel = 0;
    mPs3Stack.push_back(lvl);
    openXmbOpt();
    mPs3Stack.pop_back();
    mCtrHeld = 0;   // the keys held now belong to the menu
    mDisplayDirty = true;
    return mPs3OptActive;
}

// A kind 0 information dialog: shown as a page of paragraphs in the panel. ctrInfoDialog() also
// holds while one closes (the message dialog must not take it over for its fade).
bool NanoMenu::ctrInfoDialog() const {
    return mPs3DlgKind == 0 && (mPs3DlgRomInfo || mPs3DlgGameInfo || mPs3DlgAppInfo) && (int)mPs3DlgOptions.size() <= 1;
}

bool NanoMenu::ctrInfoActive() const {
    return mPs3DlgActive && ctrInfoDialog();
}

bool NanoMenu::ctrSliderActive() const {
    return mPs3DlgActive && mPs3DlgKind == 1 && mPs3DlgSlider && mPs3DlgOptions.empty();
}

// Sets the open slider to a fraction of its range on its step grid, with the same live previews
// as the D-pad steps (screen calibration, shader parameters).
void NanoMenu::ctrSliderSet(float frac) {
    if (mPs3DlgSldStep <= 0.0f || mPs3DlgSldMax <= mPs3DlgSldMin) return;
    float v = mPs3DlgSldMin + std::clamp(frac, 0.0f, 1.0f) * (mPs3DlgSldMax - mPs3DlgSldMin);
    v = mPs3DlgSldMin + roundf((v - mPs3DlgSldMin) / mPs3DlgSldStep) * mPs3DlgSldStep;
    v = std::clamp(v, mPs3DlgSldMin, mPs3DlgSldMax);
    if (v == mPs3DlgSldVal) return;
    mPs3DlgSldVal = v;
    mDisplayDirty = true;
    screenCalSliderPreview();
    if (mShaderParamEdit >= 0 && mShaderParamEdit < (int)mShaderParams.size()) {
        mShaderParams[mShaderParamEdit].cur = v;
        shaderApplyParamLive(mShaderParamEdit);
    }
}

bool NanoMenu::ctrItemSwitch(const Ps3Item& it, bool* on) {
    if (it.checkState >= 0) { *on = it.checkState == 1; return true; }
    const std::string v = it.binding ? resolvePs3ItemValue(it) : it.value;
    return !v.empty() && ctrIsSwitch(v, on);
}

// A switch row set to OFF or ON. nano edits such a setting through a chooser (or flips a check
// row on select): open it and pick the wanted choice, so the value is stored by nano's own path.
bool NanoMenu::ctrSetSwitch(int itemIndex, bool on) {
    if (mPs3Stack.empty()) return false;
    Ps3Level& lv = mPs3Stack.back();
    if (itemIndex < 0 || itemIndex >= (int)lv.items.size()) return false;
    bool cur = false;
    if (!ctrItemSwitch(lv.items[itemIndex], &cur)) return false;
    lv.sel = itemIndex;
    mDisplayDirty = true;
    if (cur == on) return true;
    // one toggle sound for the flip, not the chooser's open and confirm sounds (the console's
    // switch, theme/settings/panel.ts decide)
    mCtrSfxMute = true;
    struct Unmute { bool* f; ~Unmute() { *f = false; } } unmute{&mCtrSfxMute};
    if (mCtrHost) mCtrHost->playSound("SE_CTR_COMMON_TOGGLE");
    ps3XmbSelect();
    if (mPs3DlgActive) {
        static const char* kOn[] = {"On", "ON", "on", "Enabled", "Yes"};
        static const char* kOff[] = {"Off", "OFF", "off", "Disabled", "No"};
        for (int k = 0; k < (int)mPs3DlgOptions.size(); k++) {
            const std::string& o = mPs3DlgOptions[k];
            bool match = false;
            for (const char* w : on ? kOn : kOff) if (o == w || o == trDyn(w)) match = true;
            if (match) { mPs3DlgSel = k; ps3XmbSelect(); return true; }
        }
        ps3XmbBack();   // not an On / Off chooser after all: leave it as it was
        return false;
    }
    return true;
}

// LEFT / RIGHT choose OFF / ON and A flips, on a switch row of a settings list.
bool NanoMenu::ctrSwitchKey(int type, int code, int value) {
    if (!mCtrTheme || ctrModalKind() != CTR_MODAL_PANEL || mPs3OptActive || mPs3DlgActive || mPs3DlgClosing ||
        mPs3Stack.empty()) {
        mCtrSwitchKeyHeld = false;
        return false;
    }
    Ps3Level& lv = mPs3Stack.back();
    if (lv.sel < 0 || lv.sel >= (int)lv.items.size()) return false;
    bool on = false;
    const bool isSwitch = ctrItemSwitch(lv.items[lv.sel], &on);
    // the release (or the centred hat) of a key used here is ours too
    const bool release = (type == EV_KEY && value == 0) || (type == EV_ABS && code == ABS_HAT0X && value == 0);
    if (release && mCtrSwitchKeyHeld &&
        ((type == EV_KEY && (code == BTN_SOUTH || code == KEY_ENTER || code == BTN_DPAD_LEFT || code == BTN_DPAD_RIGHT ||
                             code == KEY_LEFT || code == KEY_RIGHT)) || type == EV_ABS)) {
        mCtrSwitchKeyHeld = false;
        return true;
    }
    if (!isSwitch) return false;
    int want = -1;
    if (type == EV_KEY && value == 1) {
        if (code == BTN_SOUTH || code == KEY_ENTER) want = on ? 0 : 1;
        else if (code == BTN_DPAD_LEFT || code == KEY_LEFT) want = 0;
        else if (code == BTN_DPAD_RIGHT || code == KEY_RIGHT) want = 1;
    } else if (type == EV_KEY && value == 2 &&
               (code == BTN_SOUTH || code == KEY_ENTER || code == BTN_DPAD_LEFT || code == BTN_DPAD_RIGHT || code == KEY_LEFT || code == KEY_RIGHT)) {
        return true;   // no repeat on a switch
    } else if (type == EV_ABS && code == ABS_HAT0X && value != 0) {
        want = value > 0 ? 1 : 0;
    }
    if (want < 0) return false;
    mCtrSwitchKeyHeld = true;
    if ((want == 1) != on) ctrSetSwitch(lv.sel, want == 1);
    else if (mCtrHost) mCtrHost->playSound("SE_CTR_COMMON_NOMOVE");
    return true;
}

// The home's music only plays on the home itself: not while a launch is under way, not while an
// app runs (sys.gammaos.nano.app_launched is the live signal; mOverlayWallpaper is stale after a
// launch from the overlay), not over a live app and not with a media player up. Same rule as the
// DSi theme's ambiance (NanoMenuRender ndsAmbianceTick).
bool NanoMenu::ctrBgmAllowed() {
    return !mPs3BootActive && !ndsPlayerActive() && !(mOverlayMode && !mOverlayWallpaper) &&
           mLaunchFadeStart == 0 && !mOverlayLaunchPending &&
           !property_get_bool("sys.gammaos.nano.app_launched", false) &&
           property_get_bool("persist.gammaos.nano.nds.ambiance", true);   // Menu Music, applied live
}

void NanoMenu::ctrLeave() {
    if (mCtrAudio) {
        mCtrAudio->stopAll(0);
        mCtrAudio->release();
    }
    ctrRelease();
    mCtrHeld = 0;
    mCtrStickX = mCtrStickY = 0;
    mCtrModelSig = 0;
}

// nano's navigation sounds (PS3_SFX_*) as the console's effects
void NanoMenu::ctrSfx(int which) {
    if (!mCtrHost || mCtrSfxMute) return;
    static const char* kNames[] = {"SE_CTR_HOME_SELECT", "SE_CTR_COMMON_BUTTON", "SE_CTR_COMMON_CANCEL",
                                   "SE_CTR_COMMON_BUTTON", "SE_CTR_COMMON_DIALOG", "SE_CTR_COMMON_ERROR"};
    if (which < 0 || which >= (int)(sizeof(kNames) / sizeof(kNames[0]))) return;
    mCtrHost->playSound(kNames[which]);
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
    if (mCtrForceSingle) return false;
    return sAhbTargetSecondary.glFbo != 0 || !mSecondaryEglSurfaces.empty();
}

// Layout width a screen gets in a rectangle: wider than its design width when the rectangle is
// (widescreen adaptation, persist.gammaos.nano.ctr.widen, default on), capped at 2.4:1.
static float ctrScreenWidth(bool top, float rw, float rh) {
    const float design = top ? 400.0f : 320.0f;
    if (rh <= 0 || !property_get_bool("persist.gammaos.nano.ctr.widen", true)) return design;
    return std::clamp(240.0f * rw / rh, design, 240.0f * 2.4f);
}

// Layout height of the top screen in a rectangle: taller than its design 240 when the rectangle
// is narrower than 5:3 (3:2, 4:3), up to 4:3. Beyond that the wall model's curved ends would show
// at the top and bottom, so squarer and portrait panels letterbox the rest.
static float ctrTopHeight(float rw, float rh) {
    if (rw <= 0 || !property_get_bool("persist.gammaos.nano.ctr.widen", true)) return 240.0f;
    return std::clamp(400.0f * rh / rw, 240.0f, 300.0f);
}

// Test hook: sys.gammaos.nano.ctr.topaspect="w:h" draws the top screen of a dual panel device
// into a centred rectangle of that aspect (checks 4:3, 3:2... on a 16:9 panel).
static void ctrTestTopRect(float W, float H, float* rx, float* ry, float* rw, float* rh) {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.ctr.topaspect", v, "");
    float a = 0, b = 0;
    if (sscanf(v, "%f:%f", &a, &b) != 2 || a <= 0 || b <= 0) return;
    const float want = a / b;
    if (W / H > want) { *rw = H * want; *rh = H; } else { *rw = W; *rh = W / want; }
    *rx = (W - *rw) / 2;
    *ry = (H - *rh) / 2;
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
    } else {   // one panel: the two screens stacked, the bottom one in the lower half
        y = h * 0.5f;
        h *= 0.5f;
    }
    const float lw = ctrScreenWidth(false, w, h);
    ctrFit(lw, 240, x, y, w, h, &rx, &ry, &rw, &rh);
}

void NanoMenu::ctrDrawScreen(bool top, float rx, float ry, float rw, float rh) {
    if (sCtrPerf.mode == 4) return;   // probe: the present path's own ceiling
    const float lw = top ? mCtrHome->topWidth() : mCtrHome->bottomWidth();
    const float lh = top ? mCtrHome->topHeight() : 240.0f;
    float ox, oy, ow, oh;
    ctrFit(lw, lh, rx, ry, rw, rh, &ox, &oy, &ow, &oh);
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
    ctr::CtrHome::projection(proj, lw, lh, ox, oy, ow, oh, (float)mWidth, (float)mHeight, sDrmRotMat);
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
    {   // test hook: lay out as a single panel device (sys.gammaos.nano.ctr.single=1)
        static const prop_info* spi = nullptr;
        static uint32_t sser = ~0u;
        if (!spi) spi = __system_property_find("sys.gammaos.nano.ctr.single");
        if (spi && __system_property_serial(spi) != sser) {
            sser = __system_property_serial(spi);
            mCtrForceSingle = property_get_bool("sys.gammaos.nano.ctr.single", false);
        }
    }
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
    if (ctrEnsure()) ctrModalUpdate();
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
        // test hook: sys.gammaos.nano.ctr.open=<root item name> opens that folder (retried while
        // a folder closes first)
        {
            static const prop_info* opi = nullptr;
            static uint32_t oser = 0;
            static std::string want;
            if (!opi) opi = __system_property_find("sys.gammaos.nano.ctr.open");
            if (opi && __system_property_serial(opi) != oser) {
                oser = __system_property_serial(opi);
                char v[PROPERTY_VALUE_MAX] = {};
                property_get("sys.gammaos.nano.ctr.open", v, "");
                want = v;
            }
            if (!want.empty() && mCtrHome->testOpen(want)) want.clear();
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
    // Theme Settings > 3DS Theme (persist.gammaos.nano.ctr.theme), read when the prop changes
    {
        static const prop_info* tpi = nullptr;
        static uint32_t tser = ~0u;
        static int wanted = -1;
        if (!tpi) tpi = __system_property_find("persist.gammaos.nano.ctr.theme");
        const uint32_t ser = tpi ? __system_property_serial(tpi) : 0;
        if (ser != tser) {
            tser = ser;
            wanted = property_get_int32("persist.gammaos.nano.ctr.theme", -1);
        }
        // a new home (GL context loss, theme switch) starts on the default look
        if (mCtrHome->theme() != wanted) {
            mCtrHome->setTheme(wanted);
            mDisplayDirty = true;
        }
    }
    // screen widths from the rectangles each screen is drawn in
    {
        const float W = (float)mWidth, H = (float)mHeight;
        float topW = 400, botW = 320;
        float bx, by, bw, bh;
        ctrBottomRect(bx, by, bw, bh);
        botW = ctrScreenWidth(false, bw, bh);
        float tx = 0, ty = 0, tw = W, th = ctrDualPanel() ? H : H * 0.5f;
        if (ctrDualPanel()) ctrTestTopRect(W, H, &tx, &ty, &tw, &th);
        topW = ctrScreenWidth(true, tw, th);
        mCtrHome->setScreenWidths(topW, botW);
        mCtrHome->setTopHeight(ctrTopHeight(tw, th));
    }
    mCtrHome->update(systemTime(SYSTEM_TIME_MONOTONIC));
    const nsecs_t perfUpdated = sCtrPerf.mode ? sCtrPerf.mark() : 0;
    if (sCtrPerf.mode) sCtrPerf.update += CtrPerf::ms(perfStart, perfUpdated);
    const float W = (float)mWidth, H = (float)mHeight;
    if (ctrDualPanel()) {
        float tx = 0, ty = 0, tw = W, th = H;
        ctrTestTopRect(W, H, &tx, &ty, &tw, &th);
        if (tw < W || th < H) drawQuad(0, 0, W, H, 0, 0, 0, 1);   // test: the rest of the panel black
        ctrDrawScreen(true, tx, ty, tw, th);
        if (sCtrPerf.mode) sCtrPerf.top += CtrPerf::ms(perfUpdated, sCtrPerf.mark());
    } else {
        // one panel, any aspect: the top screen over the bottom one (the usual single screen
        // layout of the 3DS), each widened to its half like the screens of a dual panel device
        ctrDrawScreen(true, 0, 0, W, H * 0.5f);
        ctrDrawScreen(false, 0, H * 0.5f, W, H * 0.5f);
    }
    // nano's screens the 3DS style does not cover yet (pickers, keyboard, wizard, players) keep
    // the shared XMB chrome; lists, option menus and dialogs are the 3DS panel and dialog
    if (ctrModalKind() == CTR_MODAL_FALLBACK) renderPs3Xmb();
    else ctrRenderOverlays();
    if (sCtrPerf.mode == 3) sCtrGpu.mark("rest");   // the rest of nano's frame until the next bottom pass
}

// The keyboard and the global search results over the 3DS screens, without the XMB under them.
// A dual panel device draws them on the bottom panel (the secondary passes call renderGlobalSearch
// and renderOsk), so the top screen only dims behind them; one panel draws them here.
void NanoMenu::ctrRenderOverlays() {
    if (!mOskActive && !mGSearchActive) return;
    if (ctrDualPanel()) {
        setUiBlend();   // the 3DS renderer leaves its own blend state
        drawQuad(0, 0, (float)mWidth, (float)mHeight, 0, 0, 0, 0.45f);
        return;
    }
    if (mGSearchActive) renderGlobalSearch();
    renderOsk();
}

void NanoMenu::renderCtrSecondary() {
    if (!mCtrHome || !mCtrHome->loaded() || mCtrForceSingle) return;
    ctrModalUpdate();   // the secondary pass runs first in a frame
    const nsecs_t t0 = sCtrPerf.mode ? sCtrPerf.mark() : 0;
    if (sCtrPerf.mode == 3) sCtrGpu.mark("bottom");   // also ends the "rest of frame" span
    ctrDrawScreen(false, 0, 0, (float)mWidth, (float)mHeight);
    if (sCtrPerf.mode == 3) sCtrGpu.mark(nullptr);
    if (sCtrPerf.mode) sCtrPerf.bottom += CtrPerf::ms(t0, sCtrPerf.mark());
}

}  // namespace android
