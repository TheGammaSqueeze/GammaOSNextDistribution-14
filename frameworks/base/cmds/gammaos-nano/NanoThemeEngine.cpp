// GammaOS Nano - ES-DE theme engine renderer (see docs/THEME_ENGINE.md).
//
// Draws an ES-DE theme's `system` and `gamelist` views with nano's GLES2 primitives,
// bound to the shared game-system model (mXmbSystems) and scraped art. The parse model
// lives in NanoEsdeTheme (GL-free); this file is the NanoMenu:: render + nav skin, added
// as a fourth home theme gated on persist.gammaos.nano.ndstheme=3 (mEsdeTheme).
//
// MVP scope: background/image (PNG), text/datetime/clock, and the primary navigation
// element (textlist / carousel) listing systems (system view) or games (gamelist view).
// SVG logos, video, animation and per-system logo resolution are follow-ups; unsupported
// elements are skipped, never fatal.
#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoEsdeTheme.h"
#include "NanoMenuShaders.h"   // FONT_CHAR_H
#include <tinyxml2.h>          // parse ES-DE gamelist.xml for per-game metadata (matches the control)
#include "nanosvg.h"           // vendored SVG parser (declarations; impl in libnanosvg_nano)
#include "nanosvgrast.h"       // vendored SVG rasterizer
#include "stb_image.h"         // stbi_info for raster art dimensions (impl in NanoMenuPS3Icons.cpp)

#include <android/imagedecoder.h>   // universal raster decode (webp/gif/heif/avif) - many ES-DE themes ship webp
#include <android/bitmap.h>         // ANDROID_BITMAP_FORMAT_RGBA_8888
#include <fcntl.h>                  // open() for AImageDecoder_createFromFd

#include <GLES2/gl2.h>         // glDisable(GL_SCISSOR_TEST) after scissorLogicalRect (grid clip)

#include <algorithm>           // std::min

#include <log/log.h>
#include <cutils/properties.h>
#include <utils/SystemClock.h>   // uptimeMillis (carousel slide animation clock)

#include <unistd.h>
#include <sys/stat.h>          // mkdir for the on-demand default rating-star cache

#include <cctype>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <string>
#include <vector>

// Theme-set search roots. /data wins so a set can be adb-pushed during development; kEsdeSdcardDir is
// the ES-DE Android user theme folder (/sdcard/ES-DE/themes) so a user can just drop a theme set there
// and pick it - the same location real ES-DE reads. It is watched for changes (esdeSdcardThemesTick).
static const char* kEsdeDataDir = "/data/system/nano_esde_themes";
static const char* kEsdeSystemDir = "/system/etc/nano_esde_themes";
static const char* kEsdeSdcardDir = "/storage/emulated/0/ES-DE/themes";

// Resolve the active theme-set directory: <root>/<name> for the first root that has it.
static std::string esdeSetDir(const std::string& name) {
    if (name.empty()) return "";
    for (const char* root : {kEsdeDataDir, kEsdeSystemDir, kEsdeSdcardDir}) {
        std::string d = std::string(root) + "/" + name;
        if (access((d + "/capabilities.xml").c_str(), F_OK) == 0) return d;
    }
    return "";
}

#include "NanoEsdeSystemNames.inc"

// ES-DE full display name for a system's short name (its ES-DE theme folder / rom dir), e.g.
// "nes" -> "Nintendo Entertainment System". Empty if unknown, so the caller keeps nano's own
// name. Mirrors SystemData::getFullName (fed from es_systems.xml), which ES-DE uses for the
// system carousel/grid/textlist entries and the ${system.fullName} variable.
static std::string esdeSystemFullName(const std::string& esdeName) {
    if (esdeName.empty()) return std::string();
    for (const auto& kv : kEsdeSystemFullNames)
        if (esdeName == kv.first) return kv.second;
    return std::string();
}

// Build the ${system.*} variables ES-DE themes reference. system.fullName resolves to the ES-DE
// es_systems.xml full name (falling back to nano's own name for a non-standard system); per-system
// logo resolution keys off system.theme.
static std::map<std::string, std::string> esdeSysVars(const std::string& sysTheme,
                                                       const std::string& sysName) {
    std::map<std::string, std::string> v;
    std::string full = esdeSystemFullName(sysTheme);
    if (full.empty()) full = sysName;
    v["system.theme"] = sysTheme;
    v["system.name"] = sysName;
    v["system.fullName"] = full;
    v["system.fullName.noCollections"] = full;
    v["system.fullName.autoCollections"] = full;
    v["system.fullName.customCollections"] = full;
    return v;
}

// Apply an ES-DE letterCase transform in place: "uppercase", "lowercase" or "capitalize"
// (Title Case - lowercase then upper-case the first letter of each whitespace-delimited word,
// matching Utils::String::toCapitalized). Any other value (incl. "none") leaves the text as-is.
static void esdeLetterCase(std::string& s, const std::string& lc) {
    if (lc == "uppercase") {
        for (auto& ch : s) ch = toupper((unsigned char)ch);
    } else if (lc == "lowercase") {
        for (auto& ch : s) ch = tolower((unsigned char)ch);
    } else if (lc == "capitalize") {
        bool atWordStart = true;
        for (auto& ch : s) {
            if (isspace((unsigned char)ch)) { atWordStart = true; continue; }
            ch = atWordStart ? toupper((unsigned char)ch) : tolower((unsigned char)ch);
            atWordStart = false;
        }
    }
}

// A 1x1 white texture, created once. Lets a solid ES-DE colour band be drawn through the cover FX
// shader (which needs a texture) so it can carry a colorEnd gradient; white * per-vertex tint = the
// gradient fill.
static GLuint esdeWhiteTex() {
    static GLuint t = 0;
    if (t == 0) {
        glGenTextures(1, &t);
        glBindTexture(GL_TEXTURE_2D, t);
        const unsigned char white[4] = { 255, 255, 255, 255 };
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, white);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }
    return t;
}

// ES-DE magnification filter for an image/video element: nearest by default (crisp pixels), so a
// low-res source (a retro screenshot, a small logo) drawn larger than its native size stays sharp
// like real ES-DE instead of the blurry linear upscale. interpolation="linear" (or an arbitrarily
// rotated image, which ES-DE smooths) selects linear (ImageComponent.cpp:582-603). Set on the bound
// texture right before the draw; minification stays linear so a large source scaled down is smooth.
static GLint esdeImageMagFilter(const nanoesde::Element* e) {
    if (!e) return GL_NEAREST;
    // ES-DE names the property "interpolation" on image/video/animation/badges/rating but
    // "imageInterpolation" on carousel and grid (ThemeData.cpp). An element only ever carries one
    // of the two, so read whichever it has (carousel/grid icons were defaulting to nearest before).
    const std::string& interp = e->has("imageInterpolation")
                                    ? e->getS("imageInterpolation", std::string())
                                    : e->getS("interpolation", std::string());
    if (interp == "linear")  return GL_LINEAR;
    if (interp == "nearest") return GL_NEAREST;
    float rot = e->getF("rotation", 0.0f);
    long r = (long)rot;
    bool arbitraryRot = rot != 0.0f && ((float)r != rot || (r % 90) != 0);
    return arbitraryRot ? GL_LINEAR : GL_NEAREST;
}

namespace android {

// True if any game in the system has scraped art matching one of `mediaTypes`. nano keeps a
// single per-game image (the scraped box, also drawn for the video element) plus an optional
// fanart, so every ES-DE image/video mediaType maps onto "has a box" and "fanart" onto "has a
// fan". Mirrors ES-DE's ViewController scan (any one matching file cancels the trigger). Fails
// open (true) on a bad index or empty type list so a system is never wrongly stripped of art.
bool NanoMenu::esdeSystemHasMedia(int sysIdx, const std::vector<std::string>& mediaTypes) {
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return true;
    if (mediaTypes.empty()) return true;
    bool wantBox = false, wantFan = false;
    for (const auto& t : mediaTypes) {
        if (t == "fanart") wantFan = true;
        else wantBox = true;   // cover / screenshot / miximage / video / ... -> nano's box
    }
    scraperEnsureLoaded();
    const XmbSystem& s = mXmbSystems[sysIdx];
    for (const auto& rom : s.roms) {
        const ScrapeEntry* se = scrapeEntryFor(rom);
        if (!se) continue;
        if (wantBox && !se->box.empty()) return true;
        if (wantFan && !se->fan.empty()) return true;
    }
    return false;
}

// Resolve the effective variant for a focused system given the user-selected variant, applying
// the selected variant's noMedia / noVideos overrides exactly as ES-DE's ViewController does:
// noMedia (scan its mediaType list) takes precedence over noVideos (nano maps "video" onto its
// box, so the noVideos condition is "no box" too). Returns the selected variant unchanged when
// no override applies, the trigger set is empty, or the triggers are disabled by prop.
std::string NanoMenu::esdeEffectiveVariant(const std::string& selectedVariant, int sysIdx) {
    char vt[PROPERTY_VALUE_MAX] = {0};
    property_get("persist.gammaos.nano.esde.variant_triggers", vt, "1");
    if (vt[0] == '0') return selectedVariant;   // ES-DE's ThemeVariantTriggers, default on
    const nanoesde::Capability* cap = nullptr;
    for (const auto& v : mEsdeDoc.capabilities().variants)
        if (v.name == selectedVariant) { cap = &v; break; }
    if (!cap) return selectedVariant;
    if (cap->triggerNoMedia.empty() && cap->triggerNoVideos.empty()) return selectedVariant;
    // noMedia first (it wins when both would fire), then noVideos.
    if (!cap->triggerNoMedia.empty() && !esdeSystemHasMedia(sysIdx, cap->triggerNoMediaTypes))
        return cap->triggerNoMedia;
    if (!cap->triggerNoVideos.empty()) {
        static const std::vector<std::string> kVideo = {"video"};
        if (!esdeSystemHasMedia(sysIdx, kVideo)) return cap->triggerNoVideos;
    }
    return selectedVariant;
}

// (Re)load the selected theme set if the selection changed or nothing is loaded.
void NanoMenu::ensureEsdeTheme() {
    char buf[PROPERTY_VALUE_MAX] = {0};
    property_get("persist.gammaos.nano.esde.themeset", buf, "");
    std::string want = buf[0] ? buf : "slate-es-de";
    if (mEsdeLoaded && want == mEsdeSetName) return;

    mEsdeSetName = want;
    mEsdeLoaded = true;   // latch even on failure so we don't retry every frame
    for (auto& kv : mEsdeTexCache) if (kv.second) glDeleteTextures(1, &kv.second);
    mEsdeTexCache.clear();
    for (auto& kv : mEsdeSvgCache) if (kv.second.tex) glDeleteTextures(1, &kv.second.tex);
    mEsdeSvgCache.clear();
    for (auto& kv : mEsdeAnimCache) for (GLuint t : kv.second.frames) if (t) glDeleteTextures(1, &t);
    mEsdeAnimCache.clear();
    mEsdePngDims.clear();
    esdeBgVideoStop();     // stop any theme background video; the new set re-starts it if it has one
    mEsdeCamCursor = -1;   // snap the carousel to the new set instead of sliding into it
    mEsdeGridCursor = -1; mEsdeGridScroll = 0.0f; mEsdeGamelistGrid = -1;   // reset grid state/cache

    std::string dir = esdeSetDir(want);
    if (dir.empty()) {
        ALOGW("esde: theme set '%s' not found under %s, %s or %s",
              want.c_str(), kEsdeDataDir, kEsdeSystemDir, kEsdeSdcardDir);
        return;
    }
    // Pick a representative system's theme folder for the shared layout load.
    std::string sysTheme = "default", sysName = "System";
    int repIdx = -1;
    for (int i = 0; i < (int)mXmbSystems.size(); i++) {
        if (!mXmbSystems[i].enabled) continue;
        sysTheme = mXmbSystems[i].romDir.empty() ? mXmbSystems[i].shortname : mXmbSystems[i].romDir;
        sysName = mXmbSystems[i].name;
        repIdx = i;
        break;
    }
    mEsdeRepSysTheme = sysTheme;   // remembered so the carousel can swap in each system's folder
    char fs[PROPERTY_VALUE_MAX] = {0}, cs[PROPERTY_VALUE_MAX] = {0}, va[PROPERTY_VALUE_MAX] = {0};
    char ar[PROPERTY_VALUE_MAX] = {0};
    property_get("persist.gammaos.nano.esde.fontsize", fs, "");
    property_get("persist.gammaos.nano.esde.colorscheme", cs, "");
    property_get("persist.gammaos.nano.esde.variant", va, "");
    property_get("persist.gammaos.nano.esde.aspectratio", ar, "automatic");
    float aspect = mHeight > 0 ? (float)mWidth / (float)mHeight : 1.7778f;
    bool ok = mEsdeDoc.load(dir, /*systemName=*/"", va, cs, /*aspect=*/ar, fs,
                            aspect, esdeSysVars(sysTheme, sysName));
    // Now that capabilities are parsed, honour the selected variant's noMedia/noVideos trigger
    // for the representative system and reparse once if it resolves to a different variant.
    if (ok && repIdx >= 0) {
        std::string eff = esdeEffectiveVariant(va, repIdx);
        if (eff != va)
            ok = mEsdeDoc.load(dir, /*systemName=*/"", eff, cs, /*aspect=*/ar, fs,
                               aspect, esdeSysVars(sysTheme, sysName));
    }
    if (!ok)
        ALOGW("esde: load '%s' failed: %s", want.c_str(), mEsdeDoc.error().c_str());
    else {
        auto animName = [](nanoesde::XsAnim a) {
            return a == nanoesde::XsAnim::SLIDE ? "slide" : a == nanoesde::XsAnim::FADE ? "fade" : "instant";
        };
        ALOGI("esde: loaded '%s' set (variant='%s' colorScheme='%s' font='%s' aspect='%s' xsSysToGl=%s xsGlToSys=%s)",
              want.c_str(), va, cs, fs, mEsdeDoc.selectedAspect().c_str(),
              animName(mEsdeDoc.xsSystemToGamelist()), animName(mEsdeDoc.xsGamelistToSystem()));
    }
    esdeCacheSounds();        // snapshot the theme's navigation-sound paths for esdeSfx
    mEsdeLoadedSysIdx = -1;   // force a per-focused-system re-resolve on the next render
}

// Watch the user theme folder (/sdcard/ES-DE/themes) so a set dropped in there - or an edit to the
// ACTIVE set - is picked up live without a restart. Polls a cheap mtime/size signature a few times a
// second (inotify is unreliable on the sdcard FUSE mount). On a change it re-scans the installed list
// (so a new set appears in the theme picker) and, if the active set's own files changed, forces a
// reload on the next ensureEsdeTheme.
void NanoMenu::esdeSdcardThemesTick() {
    if (!mEsdeTheme) return;
    int64_t now = (int64_t)uptimeMillis();
    if (now - mEsdeSdcardPollMs < 1500) return;   // a few polls/sec is plenty for a manual file drop
    mEsdeSdcardPollMs = now;
    struct stat st;

    // Two independent signatures so a sibling theme dropped in does NOT needlessly reparse the
    // active set. dirSig tracks the theme folder's own entry (mtime/size), which changes when a
    // set is added or removed -> re-enumerate the picker only. activeSig tracks the active set's
    // theme.xml/capabilities.xml when it lives on the sdcard, which changes on an in-place edit
    // -> reparse just that set.
    uint64_t dirSig = 1;
    if (stat(kEsdeSdcardDir, &st) == 0)
        dirSig = dirSig * 1000003u + (uint64_t)st.st_mtime * 131u + (uint64_t)st.st_size;
    const std::string active = esdeSetDir(mEsdeSetName);
    const size_t sroot = strlen(kEsdeSdcardDir);
    const bool activeOnSdcard = active.size() >= sroot && active.compare(0, sroot, kEsdeSdcardDir) == 0;
    uint64_t activeSig = 0;
    if (activeOnSdcard) {
        activeSig = 1;
        for (const char* fn : {"/theme.xml", "/capabilities.xml"})
            if (stat((active + fn).c_str(), &st) == 0)
                activeSig = activeSig * 1000003u + (uint64_t)st.st_mtime * 131u + (uint64_t)st.st_size;
    }
    if (mEsdeSdcardSig == 0) {                     // first poll: seed both, never act
        mEsdeSdcardSig = dirSig;
        mEsdeSdcardActiveSig = activeSig;
        return;
    }
    const bool dirChanged = (dirSig != mEsdeSdcardSig);
    const bool activeChanged = (activeSig != mEsdeSdcardActiveSig);
    if (!dirChanged && !activeChanged) return;
    mEsdeSdcardSig = dirSig;
    mEsdeSdcardActiveSig = activeSig;
    if (dirChanged) esdeMenuEnumerateInstalled();  // a set appeared / disappeared: refresh the picker
    if (activeChanged && activeOnSdcard) mEsdeLoaded = false;   // active set edited in place: reparse it
    mDisplayDirty = true;
    ALOGI("esde: user theme folder changed (dir=%d active=%d)", (int)dirChanged, (int)activeChanged);
}

// Re-parse the loaded theme for one focused system so per-system data (slate's systeminfo.xml
// info lines, per-system band colours) tracks the carousel. Only the XML model is rebuilt; the GL
// texture caches and the carousel animation state are untouched, so it is cheap and flicker-free.
void NanoMenu::esdeReloadForSystem(int sysIdx) {
    if (!mEsdeLoaded || mEsdeSetName.empty()) return;
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    std::string dir = esdeSetDir(mEsdeSetName);
    if (dir.empty()) return;
    const auto& s = mXmbSystems[sysIdx];
    std::string sysTheme = s.romDir.empty() ? s.shortname : s.romDir;
    std::string sysName = s.name;
    char fs[PROPERTY_VALUE_MAX] = {0}, cs[PROPERTY_VALUE_MAX] = {0}, va[PROPERTY_VALUE_MAX] = {0};
    char ar[PROPERTY_VALUE_MAX] = {0};
    property_get("persist.gammaos.nano.esde.fontsize", fs, "");
    property_get("persist.gammaos.nano.esde.colorscheme", cs, "");
    property_get("persist.gammaos.nano.esde.variant", va, "");
    property_get("persist.gammaos.nano.esde.aspectratio", ar, "automatic");
    float aspect = mHeight > 0 ? (float)mWidth / (float)mHeight : 1.7778f;
    // Resolve this system's noMedia/noVideos variant trigger up front (capabilities are already
    // parsed from the prior load), so a media-less system reparses straight into its fallback
    // variant instead of the selected one - matching ES-DE's per-system ViewController scan.
    std::string eff = esdeEffectiveVariant(va, sysIdx);
    // Measured ~11-16 ms on the A133P (XML re-parse only, no texture decode); it runs once when
    // the carousel settles on a new system, not per frame, so a brief settle-time cost is fine.
    if (mEsdeDoc.load(dir, /*systemName=*/"", eff, cs, /*aspect=*/ar, fs, aspect,
                      esdeSysVars(sysTheme, sysName))) {
        mEsdeRepSysTheme = sysTheme;
        mEsdeLoadedSysIdx = sysIdx;
    }
}

// The list of enabled systems (system-view items), rebuilt into a reused member vector
// so there is no per-frame allocation after warmup.
void NanoMenu::esdeRebuildSysList() {
    // Remember the selected SYSTEM (not its index) so it survives the reorder below.
    int selSys = (mEsdeSysSel >= 0 && mEsdeSysSel < (int)mEsdeSysList.size())
                     ? mEsdeSysList[mEsdeSysSel] : -1;
    // Pair each visible system with its ES-DE sort key so the key (a linear-scan fullName lookup) is
    // computed once per system, not once per comparison. ES-DE orders the system carousel
    // alphabetically by the es_systems.xml full name, upper-cased (SystemData::sortSystems sorts by
    // sortName, which defaults to fullName, then by fullName). nano's mXmbSystems keeps the user's
    // game-systems order and must never be reordered, so the view's index list is sorted to match.
    std::vector<std::pair<std::string, int>> keyed;
    for (int i = 0; i < (int)mXmbSystems.size(); i++) {
        if (!mXmbSystems[i].enabled || mXmbSystems[i].roms.empty()) continue;
        const auto& s = mXmbSystems[i];
        std::string full = esdeSystemFullName(s.romDir.empty() ? s.shortname : s.romDir);
        if (full.empty()) full = s.name;
        for (auto& ch : full) ch = (char)toupper((unsigned char)ch);
        keyed.emplace_back(std::move(full), i);
    }
    std::stable_sort(keyed.begin(), keyed.end(),
                     [](const std::pair<std::string, int>& a, const std::pair<std::string, int>& b) {
                         return a.first < b.first;
                     });
    mEsdeSysList.clear();
    for (auto& kv : keyed) mEsdeSysList.push_back(kv.second);
    // Re-point the cursor at the same system after the reorder.
    if (selSys >= 0)
        for (int i = 0; i < (int)mEsdeSysList.size(); i++)
            if (mEsdeSysList[i] == selSys) { mEsdeSysSel = i; break; }
    if (mEsdeSysSel >= (int)mEsdeSysList.size() || mEsdeSysSel < 0) mEsdeSysSel = 0;
    // Clamp the game cursor too: a rescan can shrink the selected system's rom list.
    if (!mEsdeSysList.empty()) {
        int gn = (int)mXmbSystems[mEsdeSysList[mEsdeSysSel]].roms.size();
        if (mEsdeGameSel >= gn) mEsdeGameSel = gn > 0 ? gn - 1 : 0;
        if (mEsdeGameSel < 0) mEsdeGameSel = 0;
    }
    // Debug/testing aid: persist.gammaos.nano.esde.gotosys=<system internal name> jumps straight into
    // that system's gamelist once per process, so a control A/B needs no (flaky) dpad navigation.
    // Empty = off. Applied once so manual navigation still works afterwards.
    if (!mEsdeGotoDone && !mEsdeSysList.empty()) {
        char go[PROPERTY_VALUE_MAX] = {0};
        property_get("persist.gammaos.nano.esde.gotosys", go, "");
        if (go[0]) {
            std::string want = go;
            for (int i = 0; i < (int)mEsdeSysList.size(); i++) {
                const auto& s = mXmbSystems[mEsdeSysList[i]];
                std::string sn = s.romDir.empty() ? s.shortname : s.romDir;
                if (sn == want) {
                    mEsdeSysSel = i; mEsdeInGamelist = true; mEsdeGameSel = 0;
                    mEsdeGridCursor = -1; mEsdeGridScroll = 0.0f;
                    mEsdeGridAnimDur = 0.0f; mEsdeGridTransFactor = 1.0f;
                    break;
                }
            }
        }
        mEsdeGotoDone = true;
    }
}

// Rasterize an SVG file (theme logo / console art) aspect-fit into a boxW x boxH pixel
// box, upload as a GL texture, and cache it keyed by "path@WxH". Rasterization runs once
// per (path,size) at load-time cost; the per-frame path only samples the cached texture.
// Returns {0,0,0} on parse/raster failure (the caller then skips the element).
NanoMenu::EsdeSvg NanoMenu::esdeRasterSvg(const std::string& path, int boxW, int boxH) {
    if (boxW < 1) boxW = 1;
    if (boxH < 1) boxH = 1;
    // Cap the raster so a large element cannot spike VRAM/CPU, but only at the panel size (a themed
    // box never sensibly exceeds the screen) bounded by the GLES2 max texture size. A fixed 512 cap
    // shrank wide logos - ABN's system-logo box is 0.65*screenW (666px on 1024x768), so a 512 cap
    // contain-fit the logo far narrower than ES-DE, which fits the full maxSize box.
    int capW = mWidth > 0 ? std::min(mWidth, 2048) : 2048;
    int capH = mHeight > 0 ? std::min(mHeight, 2048) : 2048;
    if (boxW > capW) boxW = capW;
    if (boxH > capH) boxH = capH;
    char key[600];
    snprintf(key, sizeof(key), "%s@%dx%d", path.c_str(), boxW, boxH);
    auto it = mEsdeSvgCache.find(key);
    if (it != mEsdeSvgCache.end()) return it->second;

    EsdeSvg out;
    NSVGimage* img = nsvgParseFromFile(path.c_str(), "px", 96.0f);
    if (img && img->width > 0.5f && img->height > 0.5f) {
        float s = (float)boxW / img->width;
        float sy = (float)boxH / img->height;
        if (sy < s) s = sy;                                  // aspect-fit
        int ow = (int)(img->width * s + 0.5f);
        int oh = (int)(img->height * s + 0.5f);
        if (ow < 1) ow = 1;
        if (oh < 1) oh = 1;
        std::vector<unsigned char> rgba((size_t)ow * oh * 4, 0);
        NSVGrasterizer* rast = nsvgCreateRasterizer();
        if (rast) {
            nsvgRasterize(rast, img, 0.0f, 0.0f, s, rgba.data(), ow, oh, ow * 4);
            nsvgDeleteRasterizer(rast);
            glGenTextures(1, &out.tex);
            glBindTexture(GL_TEXTURE_2D, out.tex);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, ow, oh, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                         rgba.data());
            out.w = ow;
            out.h = oh;
        }
    }
    if (img) nsvgDelete(img);
    mEsdeSvgCache[key] = out;   // cache failures (tex 0) too, to avoid re-parsing a bad file
    return out;
}

// Substitute the ${system.*} placeholders (left raw by the parser) with a system's values.
std::string NanoMenu::esdeResolveSystemPath(const std::string& raw, int sysIdx) {
    if (raw.find("${system.") == std::string::npos) return raw;
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return raw;
    const auto& s = mXmbSystems[sysIdx];
    std::string theme = s.romDir.empty() ? s.shortname : s.romDir;
    std::string out = raw;
    auto rep = [&](const char* k, const std::string& v) {
        size_t p;
        while ((p = out.find(k)) != std::string::npos) out.replace(p, strlen(k), v);
    };
    rep("${system.theme}", theme);
    rep("${system.fullName}", s.name);
    rep("${system.name}", theme);
    return out;
}

// Load a raster image (JPEG/PNG/BMP) via stb_image into a GL texture, downscaling by an integer box
// factor so neither dimension exceeds `cap` (A133-class Mali GPUs commonly cap textures at 2048, and a
// theme's 2560x1440 jpg poster would otherwise fail to upload and draw nothing). Covers the formats
// loadColorIconTexAbs (libpng) cannot decode - notably theme JPEG backgrounds like Adroit's poster.
static GLuint esdeLoadStbTex(const char* path) {
    int w = 0, h = 0, n = 0;
    unsigned char* px = stbi_load(path, &w, &h, &n, 4);
    if (!px || w < 1 || h < 1) { if (px) stbi_image_free(px); return 0; }
    const int cap = 2048;
    int factor = 1;
    while ((w / factor) > cap || (h / factor) > cap) factor++;
    const unsigned char* up = px; int uw = w, uh = h;
    std::vector<unsigned char> small;
    if (factor > 1) {
        uw = std::max(1, w / factor); uh = std::max(1, h / factor);
        small.resize((size_t)uw * uh * 4);
        for (int y = 0; y < uh; y++) for (int x = 0; x < uw; x++) {
            int r = 0, g = 0, b = 0, a = 0, cnt = 0;
            for (int dy = 0; dy < factor; dy++) for (int dx = 0; dx < factor; dx++) {
                int sx = x * factor + dx, sy = y * factor + dy;
                if (sx >= w || sy >= h) continue;
                const unsigned char* s = &px[((size_t)sy * w + sx) * 4];
                r += s[0]; g += s[1]; b += s[2]; a += s[3]; cnt++;
            }
            unsigned char* d = &small[((size_t)y * uw + x) * 4];
            d[0] = (unsigned char)(r / cnt); d[1] = (unsigned char)(g / cnt);
            d[2] = (unsigned char)(b / cnt); d[3] = (unsigned char)(a / cnt);
        }
        up = small.data();
    }
    GLuint tex = 0; glGenTextures(1, &tex); glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, uw, uh, 0, GL_RGBA, GL_UNSIGNED_BYTE, up);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    stbi_image_free(px);
    return tex;
}

// Universal raster decode via Android's AImageDecoder (libjnigraphics). stb_image and libpng
// cannot decode WebP/GIF/HEIF/AVIF, yet many modern ES-DE themes (Linear, Canvas, Aura, ...)
// ship their system art and backgrounds as .webp, so those elements would silently draw nothing
// (or fall back to a system-name text label). AImageDecoder handles png/jpg/webp/gif/heif/bmp
// uniformly, so it is both the decoder for those formats and a last-ditch fallback for the others.
// Straight (unpremultiplied) RGBA to match the libpng/stb paths and nano's SRC_ALPHA blend.
// Downscaled on decode so neither side exceeds the 2048 GPU texture cap; the caller's contain-fit
// uses native dimensions (aspect is preserved), so *outW/*outH report the native size.
static GLuint esdeLoadImageDecoderTex(const char* path, int* outW, int* outH) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return 0;
    AImageDecoder* dec = nullptr;
    if (AImageDecoder_createFromFd(fd, &dec) != ANDROID_IMAGE_DECODER_SUCCESS || !dec) { close(fd); return 0; }
    const AImageDecoderHeaderInfo* hi = AImageDecoder_getHeaderInfo(dec);
    int sw = AImageDecoderHeaderInfo_getWidth(hi), sh = AImageDecoderHeaderInfo_getHeight(hi);
    if (sw < 1 || sh < 1) { AImageDecoder_delete(dec); close(fd); return 0; }
    if (outW) *outW = sw; if (outH) *outH = sh;
    AImageDecoder_setAndroidBitmapFormat(dec, ANDROID_BITMAP_FORMAT_RGBA_8888);
    AImageDecoder_setUnpremultipliedRequired(dec, true);
    const int cap = 2048;
    int tw = sw, th = sh;
    if (sw > cap || sh > cap) {
        float s = std::min((float)cap / sw, (float)cap / sh);
        tw = std::max(1, (int)(sw * s + 0.5f)); th = std::max(1, (int)(sh * s + 0.5f));
        AImageDecoder_setTargetSize(dec, tw, th);
    }
    size_t stride = AImageDecoder_getMinimumStride(dec);
    std::vector<unsigned char> buf(stride * (size_t)th);
    int r = AImageDecoder_decodeImage(dec, buf.data(), stride, buf.size());
    AImageDecoder_delete(dec); close(fd);
    if (r != ANDROID_IMAGE_DECODER_SUCCESS) return 0;
    const unsigned char* up = buf.data();
    std::vector<unsigned char> tight;
    if (stride != (size_t)tw * 4) {   // repack to tightly-packed rows for glTexImage2D
        tight.resize((size_t)tw * th * 4);
        for (int y = 0; y < th; y++) memcpy(&tight[(size_t)y * tw * 4], &buf[(size_t)y * stride], (size_t)tw * 4);
        up = tight.data();
    }
    GLuint tex = 0; glGenTextures(1, &tex); glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, tw, th, 0, GL_RGBA, GL_UNSIGNED_BYTE, up);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return tex;
}

// Native dimensions of any AImageDecoder-supported raster (webp/heif/... that stbi_info cannot read),
// without decoding the pixels. Used for contain-fit when the format is not a stb/libpng one.
static bool esdeImageDecoderDims(const char* path, int* w, int* h) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return false;
    AImageDecoder* dec = nullptr;
    if (AImageDecoder_createFromFd(fd, &dec) != ANDROID_IMAGE_DECODER_SUCCESS || !dec) { close(fd); return false; }
    const AImageDecoderHeaderInfo* hi = AImageDecoder_getHeaderInfo(dec);
    int sw = AImageDecoderHeaderInfo_getWidth(hi), sh = AImageDecoderHeaderInfo_getHeight(hi);
    AImageDecoder_delete(dec); close(fd);
    if (sw < 1 || sh < 1) return false;
    if (w) *w = sw; if (h) *h = sh;
    return true;
}

// Decode every frame of an animated raster (GIF) into its own GL texture via AImageDecoder's frame
// API, recording each frame's on-screen duration. Frames are downscaled so the longer edge is at
// most kAnimCap px: at native size a full-screen animation would cost one 1024x768 RGBA texture
// (3 MB) PER frame, so the cap keeps a typical loop to a few MB of VRAM on A133-class parts. Frames
// are decoded sequentially into one persistent buffer, which lets AImageDecoder composite each GIF
// frame's disposal/blend against the previous one for us. Lottie (.json) is unsupported and returns
// null. Cached by path (an empty entry is cached on failure so we do not retry the decode per frame);
// freed with the other art caches on theme reload.
const NanoMenu::EsdeAnim* NanoMenu::esdeAnimGet(const std::string& path) {
    auto it = mEsdeAnimCache.find(path);
    if (it != mEsdeAnimCache.end()) return it->second.frames.empty() ? nullptr : &it->second;
    EsdeAnim anim;
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) { mEsdeAnimCache[path] = anim; return nullptr; }
    AImageDecoder* dec = nullptr;
    if (AImageDecoder_createFromFd(fd, &dec) != ANDROID_IMAGE_DECODER_SUCCESS || !dec) {
        close(fd); mEsdeAnimCache[path] = anim; return nullptr;
    }
    const AImageDecoderHeaderInfo* hi = AImageDecoder_getHeaderInfo(dec);
    int sw = AImageDecoderHeaderInfo_getWidth(hi), sh = AImageDecoderHeaderInfo_getHeight(hi);
    if (sw < 1 || sh < 1) { AImageDecoder_delete(dec); close(fd); mEsdeAnimCache[path] = anim; return nullptr; }
    anim.nw = sw; anim.nh = sh;
    AImageDecoder_setAndroidBitmapFormat(dec, ANDROID_BITMAP_FORMAT_RGBA_8888);
    AImageDecoder_setUnpremultipliedRequired(dec, true);   // straight alpha, matches the still path
    const int kAnimCap = 512;
    int tw = sw, th = sh;
    if (sw > kAnimCap || sh > kAnimCap) {
        float s = std::min((float)kAnimCap / sw, (float)kAnimCap / sh);
        int cw = std::max(1, (int)(sw * s + 0.5f)), ch = std::max(1, (int)(sh * s + 0.5f));
        // AImageDecoder_setTargetSize downscales single-frame images, but it is NOT supported
        // for ANIMATED images (it returns an error, leaving the decoder at native size). Only
        // adopt the scaled dimensions if the decoder actually accepts them, else keep native -
        // otherwise the target buffer is sized for the scaled dims while decodeImage still wants
        // the native size, and every frame decode fails with BAD_PARAMETER (a >512px animated
        // GIF, e.g. Cathode's 800x167 hud-ele, then rendered nothing at all).
        if (AImageDecoder_setTargetSize(dec, cw, ch) == ANDROID_IMAGE_DECODER_SUCCESS) {
            tw = cw; th = ch;
        }
    }
    size_t stride = AImageDecoder_getMinimumStride(dec);
    std::vector<unsigned char> buf(stride * (size_t)th), tight;
    AImageDecoderFrameInfo* fi = AImageDecoderFrameInfo_create();
    const int kMaxFrames = 240;   // backstop for a pathological file; a normal loop is far shorter
    for (int i = 0; i < kMaxFrames; i++) {
        if (AImageDecoder_decodeImage(dec, buf.data(), stride, buf.size()) != ANDROID_IMAGE_DECODER_SUCCESS)
            break;
        const unsigned char* up = buf.data();
        if (stride != (size_t)tw * 4) {   // repack to tight rows for glTexImage2D
            tight.resize((size_t)tw * th * 4);
            for (int y = 0; y < th; y++)
                memcpy(&tight[(size_t)y * tw * 4], &buf[(size_t)y * stride], (size_t)tw * 4);
            up = tight.data();
        }
        GLuint tex = 0; glGenTextures(1, &tex); glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, tw, th, 0, GL_RGBA, GL_UNSIGNED_BYTE, up);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        int durMs = 100;   // GIF fallback when a frame declares no delay
        if (fi && AImageDecoder_getFrameInfo(dec, fi) == ANDROID_IMAGE_DECODER_SUCCESS) {
            int64_t ns = AImageDecoderFrameInfo_getDuration(fi);
            if (ns > 0) durMs = (int)(ns / 1000000);
        }
        if (durMs < 10) durMs = 10;   // clamp absurdly short frames so the loop is not a strobe
        anim.frames.push_back(tex); anim.delaysMs.push_back(durMs); anim.totalMs += durMs;
        if (AImageDecoder_advanceFrame(dec) != ANDROID_IMAGE_DECODER_SUCCESS) break;   // FINISHED -> done
    }
    if (fi) AImageDecoderFrameInfo_delete(fi);
    AImageDecoder_delete(dec); close(fd);
    mEsdeAnimCache[path] = std::move(anim);
    auto& a = mEsdeAnimCache[path];
    return a.frames.empty() ? nullptr : &a;
}

// Load theme art (SVG or raster) and return the texture + its contain-fit size in the box.
NanoMenu::EsdeSvg NanoMenu::esdeArtTex(const std::string& path, int boxW, int boxH) {
    if (path.empty() || boxW < 1 || boxH < 1) return EsdeSvg{};
    if (path.size() > 4 && path.compare(path.size() - 4, 4, ".svg") == 0)
        return esdeRasterSvg(path, boxW, boxH);   // vector: aspect-fit + cached internally
    // Raster: texture owned by mEsdeTexCache, native dims cached in mEsdePngDims. PNG goes through the
    // libpng loader; JPEG/BMP (which libpng cannot decode) go through stb_image so theme jpg backgrounds
    // load instead of silently drawing nothing.
    auto isExt = [&](const char* e) {
        size_t n = strlen(e);
        return path.size() > n && strcasecmp(path.c_str() + path.size() - n, e) == 0;
    };
    GLuint tex;
    auto tit = mEsdeTexCache.find(path);
    if (tit != mEsdeTexCache.end()) tex = tit->second;
    else {
        // Per-frame decode budget. A heavy theme (Aura: ~1600 webp, 1920x1080 wallpapers + blur
        // variants + per-system art) would otherwise decode every visible image on the FIRST frame,
        // synchronously on the render thread, and a single frame that long trips the 8s render
        // watchdog into a SIGABRT crash loop. Cap the decodes per rendered frame and defer the rest:
        // an over-budget miss returns empty WITHOUT caching, marks the scene dirty so the loop keeps
        // drawing, and decodes on a later frame - so the art pops in over a few frames instead of
        // stalling one. Small themes never hit the cap, so their behaviour is unchanged.
        if (mEsdeDecodeBudget <= 0) { mEsdeWantsFastFrame = true; mDisplayDirty = true; return EsdeSvg{}; }
        mEsdeDecodeBudget--;
        int nw = 0, nh = 0;   // native dims captured when the AImageDecoder path runs
        if (isExt(".png")) tex = loadColorIconTexAbs(path.c_str());
        else if (isExt(".jpg") || isExt(".jpeg") || isExt(".bmp")) tex = esdeLoadStbTex(path.c_str());
        else tex = esdeLoadImageDecoderTex(path.c_str(), &nw, &nh);   // webp/gif/heif/avif/...
        // Universal fallbacks: AImageDecoder decodes png/jpg/webp/gif/heif/bmp; then stb; then libpng.
        if (!tex) tex = esdeLoadImageDecoderTex(path.c_str(), &nw, &nh);
        if (!tex) tex = esdeLoadStbTex(path.c_str());
        if (!tex) tex = loadColorIconTexAbs(path.c_str());
        if (tex && nw > 0 && nh > 0 && mEsdePngDims.find(path) == mEsdePngDims.end())
            mEsdePngDims[path] = {nw, nh};   // seed dims so the block below skips stbi_info (fails on webp)
        mEsdeTexCache[path] = tex;
    }
    if (!tex) return EsdeSvg{};
    int iw, ih;
    auto dit = mEsdePngDims.find(path);
    if (dit != mEsdePngDims.end()) { iw = dit->second.first; ih = dit->second.second; }
    else {
        int ic = 0;
        if (!stbi_info(path.c_str(), &iw, &ih, &ic) || iw < 1 || ih < 1) {
            if (!esdeImageDecoderDims(path.c_str(), &iw, &ih)) { iw = ih = 1; }   // webp/heif dims
        }
        mEsdePngDims[path] = {iw, ih};
    }
    float s = std::min((float)boxW / iw, (float)boxH / ih);   // contain-fit
    EsdeSvg out;
    out.tex = tex;
    out.w = std::max(1, (int)(iw * s + 0.5f));
    out.h = std::max(1, (int)(ih * s + 0.5f));
    return out;
}

// Resolve a game's media for an ES-DE imageType from the shared ES-DE downloaded_media tree
// (downloaded_media/<system>/<subdir>/<rom-basename>.<ext>), the same files real ES-DE draws, so a
// gamelist element that asks for a screenshot/marquee/titlescreen shows that instead of nano's single
// scraped box. imageType may be a comma/space list (ES-DE tries each in order); the first present
// file wins. Falls back to nano's own scraped cover (romBoxartTex) when no media file is found, so a
// theme still shows something. The resolved path is cached so the extension probe runs once per game.
// Resolve the on-disk media file for a rom + ES-DE imageType (or "" if none exists), caching the
// result per rom+type so the extension probe runs once. Shared by esdeGameMediaTex (to load the
// texture) and the <gameselector> pick (to prefer a game that actually has the requested media).
std::string NanoMenu::esdeGameMediaPath(const std::string& romPath, const std::string& imageType) {
    if (romPath.empty()) return std::string();
    // ES-DE imageType -> downloaded_media subdirectory. "image" defaults to the miximage.
    static const std::unordered_map<std::string, std::string> kSub = {
        {"image", "miximages"},        {"miximage", "miximages"},
        {"cover", "covers"},           {"backcover", "backcovers"},
        {"screenshot", "screenshots"}, {"titlescreen", "titlescreens"},
        {"marquee", "marquees"},       {"3dbox", "3dboxes"},
        {"physicalmedia", "physicalmedia"}, {"fanart", "fanart"},
    };
    std::string cacheKey = romPath + "\x1f" + imageType;
    auto ci = mEsdeMediaPath.find(cacheKey);
    if (ci != mEsdeMediaPath.end()) return ci->second;

    // Split romPath once into <system>/<basename>.
    size_t sl = romPath.find_last_of('/');
    std::string dir = (sl == std::string::npos) ? std::string() : romPath.substr(0, sl);
    std::string file = (sl == std::string::npos) ? romPath : romPath.substr(sl + 1);
    size_t dot = file.find_last_of('.');
    std::string base = (dot == std::string::npos) ? file : file.substr(0, dot);
    size_t sl2 = dir.find_last_of('/');
    std::string sys = (sl2 == std::string::npos) ? dir : dir.substr(sl2 + 1);
    if (sys.empty() || base.empty()) { mEsdeMediaPath[cacheKey] = std::string(); return std::string(); }

    // Try each imageType token in order (ES-DE tries the list left-to-right); first present file wins.
    std::string found;
    const std::string root = "/storage/emulated/0/ES-DE/downloaded_media/";
    auto tokens = imageType;
    size_t start = 0;
    while (start <= tokens.size() && found.empty()) {
        size_t comma = tokens.find_first_of(", ", start);
        std::string tok = tokens.substr(start, comma == std::string::npos ? comma : comma - start);
        // trim
        while (!tok.empty() && (tok.front() == ' ')) tok.erase(tok.begin());
        while (!tok.empty() && (tok.back() == ' ')) tok.pop_back();
        auto it = kSub.find(tok);
        if (it != kSub.end()) {
            std::string stem = root + sys + "/" + it->second + "/" + base;
            for (const char* ext : {".png", ".jpg", ".jpeg", ".webp"}) {
                std::string p = stem + ext;
                if (access(p.c_str(), R_OK) == 0) { found = p; break; }
            }
        }
        if (comma == std::string::npos) break;
        start = comma + 1;
    }
    mEsdeMediaPath[cacheKey] = found;   // cache the hit or the miss
    return found;
}

// ES-DE FileData::getManualPath: true when a manual media file (pdf/cbz/cbr/cb7) exists under
// downloaded_media/<system>/manuals/<rom-basename>, which enables the "manual" badge slot.
static bool esdeManualExists(const std::string& romPath) {
    size_t sl = romPath.find_last_of('/');
    std::string dir = (sl == std::string::npos) ? std::string() : romPath.substr(0, sl);
    std::string file = (sl == std::string::npos) ? romPath : romPath.substr(sl + 1);
    size_t dot = file.find_last_of('.');
    std::string base = (dot == std::string::npos) ? file : file.substr(0, dot);
    size_t s2 = dir.find_last_of('/');
    std::string sys = (s2 == std::string::npos) ? dir : dir.substr(s2 + 1);
    if (sys.empty() || base.empty()) return false;
    std::string stem = "/storage/emulated/0/ES-DE/downloaded_media/" + sys + "/manuals/" + base;
    for (const char* ext : {".pdf", ".cbz", ".cbr", ".cb7"})
        if (access((stem + ext).c_str(), R_OK) == 0) return true;
    return false;
}

GLuint NanoMenu::esdeGameMediaTex(const std::string& romPath, const std::string& imageType, float* outAR) {
    if (outAR) *outAR = 1.0f;
    if (romPath.empty()) return 0;
    // ES-DE imageType="none" (mImageTypeNone) means the element shows NO static image at all - not even
    // nano's own scraped boxart fallback below. A grid/carousel with imageType=none is a name list
    // (X-Grid's gamelistGrid shows game names); without this the romBoxartTex fallback painted those
    // text cells with box art. Whole-string "none" only, matching ES-DE (it is not a comma-list token).
    {
        size_t a = imageType.find_first_not_of(" \t\r\n"), b = imageType.find_last_not_of(" \t\r\n");
        if (a != std::string::npos && imageType.compare(a, b - a + 1, "none") == 0) return 0;
    }
    std::string found = esdeGameMediaPath(romPath, imageType);
    if (found.empty()) return romBoxartTex(romPath, outAR);   // no ES-DE media -> nano's own cover
    EsdeSvg a = esdeArtTex(found, mWidth, mHeight);           // load + cache (decode-budgeted)
    if (!a.tex) return 0;
    auto dit = mEsdePngDims.find(found);
    if (dit != mEsdePngDims.end() && dit->second.second > 0 && outAR)
        *outAR = (float)dit->second.first / (float)dit->second.second;
    return a.tex;
}

// Parse ES-DE's own gamelist.xml for a system once and cache each game's metadata keyed by the full
// rom path, so the ES-DE theme engine shows the identical text ES-DE does. ES-DE stores the release
// date as ISO basic ("19980501T000000"); convert it to YYYY-MM-DD by pure string slicing (NO mktime/
// localtime, which is what shifted nano's own store back a day). The <path> is relative to the rom
// dir, so match games to nano's scanned roms by basename. Cover paths are kept from nano's scrape
// store (gamelist.xml has no media paths - ES-DE derives those by convention). Absent file -> no-op,
// and esdeMetaFor falls back to the scrape store, so nano still works with no ES-DE data present.
void NanoMenu::esdeEnsureGamelistLoaded(int sysIdx) {
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    if (mEsdeGamelistLoadedSys.count(sysIdx)) return;
    mEsdeGamelistLoadedSys.insert(sysIdx);                       // mark attempted (don't retry a missing file)
    const auto& sys = mXmbSystems[sysIdx];
    std::string sysName = sys.romDir.empty() ? sys.shortname : sys.romDir;
    if (sysName.empty() || sys.roms.empty()) return;
    std::string path = "/storage/emulated/0/ES-DE/gamelists/" + sysName + "/gamelist.xml";
    tinyxml2::XMLDocument doc;
    if (doc.LoadFile(path.c_str()) != tinyxml2::XML_SUCCESS) return;
    tinyxml2::XMLElement* root = doc.FirstChildElement("gameList");
    if (!root) return;
    // basename -> full rom path (nano's own scanned paths, which curRom uses).
    std::unordered_map<std::string, std::string> byBase;
    for (const auto& r : sys.roms) {
        size_t sl = r.find_last_of('/');
        byBase[sl == std::string::npos ? r : r.substr(sl + 1)] = r;
    }
    auto childText = [](tinyxml2::XMLElement* g, const char* k) -> std::string {
        tinyxml2::XMLElement* c = g->FirstChildElement(k);
        const char* t = c ? c->GetText() : nullptr;
        return t ? std::string(t) : std::string();
    };
    for (tinyxml2::XMLElement* g = root->FirstChildElement("game"); g; g = g->NextSiblingElement("game")) {
        std::string p = childText(g, "path");
        if (p.empty()) continue;
        size_t sl = p.find_last_of('/');
        std::string base = (sl == std::string::npos) ? p : p.substr(sl + 1);
        auto bi = byBase.find(base);
        if (bi == byBase.end()) continue;                       // game not in nano's scan
        ScrapeEntry e;
        if (const ScrapeEntry* s = scrapeEntryFor(bi->second)) { e.box = s->box; e.fan = s->fan; }
        e.title      = childText(g, "name");
        e.synopsis   = childText(g, "desc");
        e.rating     = childText(g, "rating");
        e.developer  = childText(g, "developer");
        e.publisher  = childText(g, "publisher");
        e.genre      = childText(g, "genre");
        e.players    = childText(g, "players");
        std::string rd = childText(g, "releasedate");           // ISO basic YYYYMMDDThhmmss
        if (rd.size() >= 8 && rd.compare(0, 8, "19700101") != 0) // 19700101 = ES-DE's "unset" epoch
            e.releaseDate = rd.substr(0, 4) + "-" + rd.substr(4, 2) + "-" + rd.substr(6, 2);
        e.lastPlayed = childText(g, "lastplayed");               // ISO basic, or unset -> never
        e.playTime   = childText(g, "playtime");                 // whole seconds, or 0 -> unknown
        e.playCount  = childText(g, "playcount");                // launch tally, or unset -> 0
        e.favorite   = childText(g, "favorite")  == "true";      // badge slots (GamelistView.cpp)
        e.completed  = childText(g, "completed") == "true";
        e.kidgame    = childText(g, "kidgame")   == "true";
        e.broken     = childText(g, "broken")    == "true";
        e.controller = childText(g, "controller");               // controller shortName for the overlay
        e.altemulator = childText(g, "altemulator");
        mEsdeGamelistMeta[bi->second] = std::move(e);
    }
}

const NanoMenu::ScrapeEntry* NanoMenu::esdeMetaFor(const std::string& romPath) {
    auto it = mEsdeGamelistMeta.find(romPath);
    if (it != mEsdeGamelistMeta.end()) return &it->second;
    return scrapeEntryFor(romPath);
}

// ES-DE lastplayed: a <datetime metadata="lastplayed"> auto-enables displayRelative, so a set time
// reads the relative age "N days/hours/minutes/seconds ago" (DateTimeComponent::getDisplayString).
// The gamelist value is ISO basic YYYYMMDDThhmmss; parse it as UTC (timegm) and compare to now so the
// age is timezone-clean, matching ES-DE's <82800s cutoff. Returns "" for an UNSET/never-played time so
// the caller can pick the element's <defaultValue> first (ES-DE only shows "never" when none is set).
static std::string esdeLastPlayedString(const std::string& iso) {
    if (iso.size() < 8 || iso.compare(0, 8, "19700101") == 0) return "";
    std::tm tm{};
    tm.tm_year = atoi(iso.substr(0, 4).c_str()) - 1900;
    tm.tm_mon  = atoi(iso.substr(4, 2).c_str()) - 1;
    tm.tm_mday = atoi(iso.substr(6, 2).c_str());
    if (iso.size() >= 15 && iso[8] == 'T') {
        tm.tm_hour = atoi(iso.substr(9, 2).c_str());
        tm.tm_min  = atoi(iso.substr(11, 2).c_str());
        tm.tm_sec  = atoi(iso.substr(13, 2).c_str());
    }
    time_t t = timegm(&tm);
    if (t < 82800) return "";   // unset -> caller resolves <defaultValue> or "never"
    long dur = (long)(time(nullptr) - t);
    if (dur < 0) dur = 0;
    auto ago = [](long n, const char* one, const char* many) {
        return std::to_string(n) + " " + (n == 1 ? one : many) + " ago";
    };
    if (dur / 86400 > 0) return ago(dur / 86400, "day", "days");
    if (dur / 3600 > 0)  return ago(dur / 3600, "hour", "hours");
    if (dur / 60 > 0)    return ago(dur / 60, "minute", "minutes");
    return ago(dur, "second", "seconds");
}

// ES-DE playtime (FileData::getPlayTimeString): 1..119s -> "1 minute"; 2..119 min -> "N minutes";
// >= 120 min -> "N hours" with a ".tenth" fraction when the leftover minutes are >= 6 (minutes/6),
// mimicking how Steam presents play time. Returns "" for zero play time so the caller can pick the
// element's <defaultValue> first (ES-DE substitutes it for the "unknown" default; TextComponent.cpp:388).
static std::string esdePlayTimeString(const std::string& secsStr) {
    long secs = 0;
    if (!secsStr.empty()) { secs = strtol(secsStr.c_str(), nullptr, 10); if (secs < 0) secs = 0; }
    int hour = (int)(secs / 3600), min = (int)((secs % 3600) / 60), sec = (int)(secs % 60);
    auto pl = [](int n, const char* one, const char* many) {
        return std::to_string(n) + " " + (n == 1 ? one : many);
    };
    if (hour == 0 && min == 0 && sec == 0) return "";   // unset -> caller resolves <defaultValue> or "unknown"
    if (hour == 0 && min == 0) return pl(1, "minute", "minutes");
    if (hour < 2) return pl(hour * 60 + min, "minute", "minutes");
    std::string h = std::to_string(hour), out = pl(hour, "hour", "hours");
    if (min >= 6) { size_t p = out.find(h); if (p != std::string::npos) out.replace(p, h.size(), h + "." + std::to_string(min / 6)); }
    return out;
}

// ES-DE text metadata="rating": RatingComponent::getRatingValue expresses the stored 0..1 rating as a
// 0..5 star count rounded to a tenth (round(r / 0.1) / 10 * 5), so 0.8 -> "4", 0.7 -> "3.5". %g emits
// the value with the shortest form (no trailing zeros), matching ES-DE's default stringstream output.
static std::string esdeRatingValue(const std::string& rating) {
    float r = rating.empty() ? 0.0f : strtof(rating.c_str(), nullptr);
    float stars = (std::round(r / 0.1f) / 10.0f) * 5.0f;
    char buf[16]; snprintf(buf, sizeof(buf), "%g", (double)stars);
    return buf;
}

// ES-DE's RatingComponent uses two built-in star graphics when a <rating> element omits filledPath/
// unfilledPath (the common case - Linear, Slate and most themes rely on the defaults). They are a
// single five-point star: filled = solid, unfilled = outline only, both tinted by the element color.
// nano has no bundled resource tree, so materialise the same two SVGs into a cache dir on first use
// and hand their paths back for the shared esdeArtTex path to rasterise. Without this a themed rating
// row drew nothing (a scraped game's stars vanished on Linear/Slate).
static const char* kStarFilledSvg =
    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"128\" height=\"128\" viewBox=\"0 0 128 128\">"
    "<path d=\"m 64,17.090983 c -5.307206,0 -12.400847,25.54313 -16.694468,28.662627 -4.293619,3.119497 "
    "-30.778633,1.972687 -32.418649,7.020139 -1.640017,5.047453 20.460887,19.687168 22.100904,24.734622 "
    "1.640016,5.047452 -7.634984,29.881809 -3.341365,33.001309 4.29362,3.1195 25.046372,-13.37579 "
    "30.353578,-13.37579 5.307206,1e-6 26.059954,16.49529 30.353573,13.37579 4.29362,-3.11949 "
    "-4.981379,-27.953859 -3.341362,-33.001312 C 92.652228,72.460916 114.75313,57.821204 113.11312,52.773752 "
    "111.4731,47.726299 84.988083,48.873106 80.694463,45.753609 76.400844,42.634111 69.307206,17.090982 "
    "64,17.090983 Z\" style=\"fill:#ffffff;fill-opacity:1\"/></svg>";
static const char* kStarUnfilledSvg =
    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"128\" height=\"128\" viewBox=\"0 0 128 128\">"
    "<path d=\"m 64,17.090983 c -5.307206,0 -12.400847,25.54313 -16.694468,28.662627 -4.293619,3.119497 "
    "-30.778633,1.972687 -32.418649,7.020139 -1.640017,5.047453 20.460887,19.687168 22.100904,24.734622 "
    "1.640016,5.047452 -7.634984,29.881809 -3.341365,33.001309 4.29362,3.1195 25.046372,-13.37579 "
    "30.353578,-13.37579 5.307206,1e-6 26.059954,16.49529 30.353573,13.37579 4.29362,-3.11949 "
    "-4.981379,-27.953859 -3.341362,-33.001312 C 92.652228,72.460916 114.75313,57.821204 113.11312,52.773752 "
    "111.4731,47.726299 84.988083,48.873106 80.694463,45.753609 76.400844,42.634111 69.307206,17.090982 "
    "64,17.090983 Z\" style=\"fill:none;stroke:#ffffff;stroke-opacity:1;stroke-width:4;stroke-linejoin:round\"/></svg>";

std::string NanoMenu::esdeDefaultStarPath(bool filled) {
    static const char* kDir = "/data/system/nano_esde_themes/.defaults";
    std::string path = std::string(kDir) + (filled ? "/star_filled.svg" : "/star_unfilled.svg");
    if (access(path.c_str(), R_OK) != 0) {
        mkdir(kDir, 0755);
        if (FILE* f = fopen(path.c_str(), "wb")) {
            const char* svg = filled ? kStarFilledSvg : kStarUnfilledSvg;
            fwrite(svg, 1, strlen(svg), f);
            fclose(f);
        }
    }
    return access(path.c_str(), R_OK) == 0 ? path : std::string();
}

// ---------------------------------------------------------------- render

void NanoMenu::renderEsde() {
    ensureEsdeTheme();
    esdeRebuildSysList();

    // Clear the deferred menu-help cache each frame; esdeDrawHelp re-sets it if a helpsystem
    // matching the current view exists, so a theme without one leaves renderEsdeMenu no stale
    // bar to redraw.
    mEsdeHelpElem = nullptr;

    // Bound theme-art decoding per frame so a heavy theme's first frame cannot hold the render
    // thread past the 8s watchdog (see esdeArtTex). 6 large decodes/frame keeps a frame well under
    // the limit while filling a full system view in a few frames; small themes never reach it.
    mEsdeDecodeBudget = 6;

    // ES-DE draws text flat - no drop shadow or outline (its Font has none). nano's drawText
    // otherwise adds an outline/shadow (mTextOutlineMode), so force flat here and restore on exit,
    // as the DSi/Minima themes do, so the ES-DE typeface reads clean instead of haloed.
    const int esdePrevOutline = mTextOutlineMode;
    mTextOutlineMode = 2;

    // Per-system theme data (slate's info panel from systeminfo.xml, per-system band colours) is
    // resolved at load time, so re-parse it for the focused system once the carousel settles on a
    // new one. This is a cheap XML re-parse; the GL texture caches and the carousel animation state
    // are left untouched, so there is no flicker or reset.
    if (!mEsdeSysList.empty() && mEsdeSysSel >= 0 && mEsdeSysSel < (int)mEsdeSysList.size() &&
        mEsdeCamAnimDur <= 0.0f) {
        int foc = mEsdeSysList[mEsdeSysSel];
        if (foc != mEsdeLoadedSysIdx) esdeReloadForSystem(foc);
    }

    // mEsdeForceView lets a slide transition render either view (from the stored selection) so both
    // can be composited; 0 = follow the live mEsdeInGamelist.
    const bool inGamelist = mEsdeForceView ? (mEsdeForceView == 2) : mEsdeInGamelist;
    const bool gamelist = inGamelist && !mEsdeSysList.empty();
    const nanoesde::View* view =
        mEsdeDoc.valid() ? mEsdeDoc.view(gamelist ? "gamelist" : "system") : nullptr;

    // In-game overlay scrim: when nano is the overlay layer composited over a still-running app
    // (mOverlayMode && !mOverlayWallpaper), the dispatcher clears the frame to a translucent black
    // scrim so the app shows through (NanoMenuRender.cpp). The ES-DE home must then NOT repaint an
    // opaque background over that scrim - the same rule the XMB/DSi/Minima homes follow through their
    // own inGameScrim gate. Chrome (logos, text, metadata, the carousel/grid primary) is never
    // full-screen so it still draws over the scrim; only a screen-covering opaque background element
    // is skipped (esdeCoversScreen below). When the app is exited (mOverlayWallpaper true) this is
    // false and the full opaque home draws as normal.
    const bool esdeInGameScrim = mOverlayMode && !mOverlayWallpaper;

    // Fallback: theme missing/invalid -> a readable notice instead of a blank panel.
    if (!view) {
        if (!esdeInGameScrim)   // over a live app: keep the notice text but not the opaque panel
            drawQuad(0, 0, (float)mWidth, (float)mHeight, 0.06f, 0.07f, 0.09f, 1.0f);
        float sc = std::min(mWidth, mHeight) * 0.03f / FONT_CHAR_H;   // font: shorter dim (ES-DE)
        std::string msg = mEsdeDoc.valid() ? "ES-DE theme has no matching view"
                                           : ("ES-DE theme not loaded: " + mEsdeDoc.error());
        drawText(msg.c_str(), mWidth * 0.06f, mHeight * 0.45f, sc, 0.9f, 0.9f, 0.95f, 1.0f);
        drawText(("set: " + mEsdeSetName).c_str(), mWidth * 0.06f, mHeight * 0.52f,
                 sc * 0.8f, 0.7f, 0.7f, 0.75f, 1.0f);
        mTextOutlineMode = esdePrevOutline;
        return;
    }

    // --- small element helpers (lambdas so they can reach the private GL primitives) ---
    auto texFor = [&](const std::string& path) -> GLuint {
        if (path.empty()) return 0;
        // SVG rasterization is a follow-up; skip .svg so we fall back to text/name.
        if (path.size() > 4 && path.compare(path.size() - 4, 4, ".svg") == 0) return 0;
        auto it = mEsdeTexCache.find(path);
        if (it != mEsdeTexCache.end()) return it->second;
        GLuint t = loadColorIconTexAbs(path.c_str());
        mEsdeTexCache[path] = t;   // cache 0 too, to avoid re-decoding a missing file
        return t;
    };
    auto rectOf = [&](const nanoesde::Element* e, float& x, float& y, float& w, float& h) {
        float px = e->getPair("pos", 0, 0), py = e->getPair("pos", 1, 0);
        float sw = e->getPair("size", 0, 0), sh = e->getPair("size", 1, 0);
        if (sw <= 0 && sh <= 0) { sw = e->getPair("maxSize", 0, 0); sh = e->getPair("maxSize", 1, 0); }
        float ox = e->getPair("origin", 0, 0), oy = e->getPair("origin", 1, 0);
        w = sw * mWidth; h = sh * mHeight;
        x = px * mWidth - ox * w; y = py * mHeight - oy * h;
    };
    auto colorOf = [&](const nanoesde::Element* e, const char* key,
                       float dr, float dg, float db, float da, float out[4]) {
        if (!e->getColor(key, out)) { out[0] = dr; out[1] = dg; out[2] = db; out[3] = da; }
        out[3] *= e->getF("opacity", 1.0f);
    };
    // ES-DE scales a themed fontSize by the SHORTER screen dimension: screenWidth when the
    // panel is vertical (W<H), screenHeight when landscape (H<=W) - i.e. min(W,H) either way
    // (es-core Font.cpp getFromTheme + Font.h defaults). On a landscape panel min == height, so
    // this matches the old height-relative sizing; on a portrait panel it keeps text from being
    // (H/W)x too large.
    const float fontDim = (float)std::min(mWidth, mHeight);
    auto fontPx = [&](const nanoesde::Element* e, float defNorm) -> float {
        return e->getF("fontSize", defNorm) * fontDim / (float)FONT_CHAR_H;
    };
    // True when an element is a screen-covering, effectively-opaque background (a theme wallpaper:
    // full-screen <image>/<video>/<animation> or a full-screen solid colour band). ES-DE has no
    // nano-synthesized background - the wallpaper is an ordinary element that spans the viewport - so
    // it is detected by geometry + tint opacity rather than by name. A partial element (chrome) or a
    // translucent one (already composites the app through) is left alone.
    auto esdeCoversScreen = [&](const nanoesde::Element* e) -> bool {
        if (e->type != "image" && e->type != "video" && e->type != "animation") return false;
        float bw = 0.0f, bh = 0.0f;
        if (e->has("cropSize")) {                       // cropSize cover-fit box (e.g. Canvas 1 1 wallpaper)
            bw = e->getPair("cropSize", 0, 1.0f) * mWidth;
            bh = e->getPair("cropSize", 1, 1.0f) * mHeight;
        } else {
            float ex, ey; rectOf(e, ex, ey, bw, bh);    // <size>/<maxSize> box
            if ((bw <= 1.0f || bh <= 1.0f) && (e->type == "video" || e->type == "animation"))
                { bw = (float)mWidth; bh = (float)mHeight; }   // fullBg video/animation (no explicit box)
        }
        if (bw < mWidth * 0.985f || bh < mHeight * 0.985f) return false;   // not full-screen
        float col[4]; colorOf(e, "color", 1, 1, 1, 1, col);
        return col[3] >= 0.98f;                                            // opaque tint only
    };
    // Resolve a text element's fontPath to an mFtFaces index so its text is drawn in the theme's
    // own typeface (Art Book Next's Mulish, etc.) rather than the default UI font. -1 = default.
    auto faceOf = [&](const nanoesde::Element* e) -> int {
        return e ? esdeFontFace(e->getPath("fontPath")) : -1;
    };
    auto drawAligned = [&](const std::string& s, float x, float y, float w, float sc,
                           const char* align, float col[4], int face = -1) {
        float tw = measureText(s.c_str(), sc, face);
        float tx = x;
        if (align && !strcmp(align, "center")) tx = x + (w - tw) * 0.5f;
        else if (align && !strcmp(align, "right")) tx = x + (w - tw);
        drawText(s.c_str(), tx, y, sc, col[0], col[1], col[2], col[3], face);
    };
    // Greedy word-wrap into [x,y] width boxW, clipped to boxH rows (ES-DE container text such as
    // the gamelist synopsis). Splits on whitespace and honors embedded newlines.
    auto drawWrapped = [&](const std::string& s, float x, float y, float boxW, float boxH,
                           float sc, float lineSp, const char* align, float col[4],
                           const std::string& scrollKey = std::string(), int face = -1,
                           const char* vAlign = nullptr, float startDelayMs = 4500.0f,
                           float scrollSpeedConst = 4.0f, float resetDelayMs = 7000.0f) {
        float lineH = sc * FONT_CHAR_H * (lineSp > 0 ? lineSp : 1.0f);
        std::vector<std::string> lines;
        std::string cur, tok;
        auto flush = [&]() { lines.push_back(cur); cur.clear(); };
        auto pushWord = [&](const std::string& wd) {
            if (wd.empty()) return;
            std::string trial = cur.empty() ? wd : cur + " " + wd;
            if (cur.empty() || measureText(trial.c_str(), sc, face) <= boxW) cur = trial;
            else { flush(); cur = wd; }
        };
        size_t n = s.size();
        for (size_t k = 0; k <= n; k++) {
            char ch = k < n ? s[k] : ' ';
            if (ch == '\n') { pushWord(tok); tok.clear(); flush(); }
            else if (ch == ' ' || ch == '\t') { pushWord(tok); tok.clear(); }
            else tok.push_back(ch);
        }
        if (!cur.empty()) flush();

        // ES-DE seats a text block's first baseline at (yTop + yBot)/2 below the box top
        // (Font::buildTextCache with offsetY 0: yTop = the 'S' cap-height bearing, yBot =
        // em*lineSpacing), which leaves a top margin of (yBot - yTop)/2 above the first line.
        // nano's drawText seats the baseline 0.8*em below the draw origin, so add the shortfall
        // as a first-line top inset to reproduce ES-DE's vertical text centering - without it a
        // gamelist description (or any wrapped block) rides ~0.4*em too high inside its box.
        float emPx = sc * (float)FONT_CHAR_H;
        int capRpx = (int)lroundf(emPx); if (capRpx < 6) capRpx = 6;
        const GlyphInfo* capG = ensureGlyph('S', capRpx, face);
        float yTopCap = capG ? (float)capG->bearingY : emPx * 0.72f;
        float yBotLine = emPx * (lineSp > 0 ? lineSp : 1.0f);
        float topInset = (yTopCap + yBotLine) * 0.5f - emPx * 0.8f;

        float contentH = topInset + (float)lines.size() * lineH;
        // ES-DE ScrollableContainer: when a description overflows its box it pauses 4.5s at the
        // top, scrolls up, pauses 7s at the bottom, then fades back to the top. Only engaged when
        // a scrollKey (the game id) is supplied and the text is taller than the box.
        bool scrollMode = !scrollKey.empty() && boxH > 0.0f && contentH > boxH + 1.0f;
        float scrollY = 0.0f;
        if (scrollMode) {
            const int64_t kDelay = (int64_t)startDelayMs, kReset = (int64_t)resetDelayMs;  // containerStartDelay / containerResetDelay (ES-DE defaults 4500 / 7000)
            // ES-DE ScrollableContainer crawl speed = ms per pixel step =
            //   rowModifier * clamp(contentW/(fontSize*1.3),10,40) * (AUTO_SCROLL_SPEED/scrollSpeed)
            //   / resolutionModifier,  resolutionModifier = min(screenW,screenH)/1080,
            //   rowModifier = lines<8 ? lines/8 : 1  (fewer visible lines scroll faster).
            float fontSizePx = sc * FONT_CHAR_H;
            float widthUnits = fontSizePx > 0.0f ? boxW / (fontSizePx * 1.3f) : 10.0f;
            float speedMod = std::min(40.0f, std::max(10.0f, widthUnits));
            float resMod = (float)std::min(mWidth, mHeight) / 1080.0f;
            if (resMod <= 0.0f) resMod = 1.0f;
            speedMod = speedMod * scrollSpeedConst / resMod;  // AUTO_SCROLL_SPEED/containerScrollSpeed (default 4.0)
            float visLines = lineH > 0.0f ? boxH / lineH : 8.0f;
            float rowModifier = visLines < 8.0f ? visLines / 8.0f : 1.0f;
            float msPerPx = rowModifier * speedMod;
            if (msPerPx < 1.0f) msPerPx = 1.0f;
            float maxScroll = contentH - boxH;
            int64_t now = (int64_t)uptimeMillis();
            if (scrollKey != mEsdeDescKey) {
                mEsdeDescKey = scrollKey; mEsdeDescStart = now; mEsdeDescAtEnd = false;
            }
            int64_t elapsed = now - mEsdeDescStart;
            if (elapsed >= kDelay) {
                float px = (float)(elapsed - kDelay) / msPerPx;
                if (px >= maxScroll) {
                    scrollY = maxScroll;
                    if (!mEsdeDescAtEnd) { mEsdeDescAtEnd = true; mEsdeDescEndStart = now; }
                    else if (now - mEsdeDescEndStart >= kReset) {
                        mEsdeDescStart = now; mEsdeDescAtEnd = false; scrollY = 0.0f;
                    }
                } else scrollY = px;
            }
            mEsdeWantsFastFrame = true; mDisplayDirty = true;   // keep animating while it scrolls
        }

        if (scrollMode) {
            scissorLogicalRect(x, y, boxW, boxH);
            for (int li = 0; li < (int)lines.size(); li++) {
                float ly = y + topInset - scrollY + li * lineH;
                if (ly > y + boxH || ly + lineH < y) continue;   // cull off-box lines
                float tw = measureText(lines[li].c_str(), sc, face), tx = x;
                if (align && !strcmp(align, "center")) tx = x + (boxW - tw) * 0.5f;
                else if (align && !strcmp(align, "right")) tx = x + (boxW - tw);
                drawText(lines[li].c_str(), tx, ly, sc, col[0], col[1], col[2], col[3], face);
            }
            glDisable(GL_SCISSOR_TEST);
        } else {
            int maxLines = boxH > 0 ? (int)(boxH / lineH) : (int)lines.size();
            if (maxLines < 1) maxLines = 1;
            // ES-DE positions a text block that FITS its box by verticalAlignment (default CENTER),
            // not pinned to the top (TextComponent yOff, :283-301): a single-line name in a tall box
            // (Catppuccin's game-name, origin-centred 0.13-tall box) centres. Only an overflowing block
            // stays top-anchored (handled by the scroll path above).
            float blockVOff = 0.0f;
            if (boxH > contentH) {
                if (vAlign && !strcmp(vAlign, "top")) blockVOff = 0.0f;
                else if (vAlign && !strcmp(vAlign, "bottom")) blockVOff = boxH - contentH;
                else blockVOff = (boxH - contentH) * 0.5f;   // center = ES-DE default
            }
            for (int li = 0; li < (int)lines.size() && li < maxLines; li++) {
                float tw = measureText(lines[li].c_str(), sc, face), tx = x;
                if (align && !strcmp(align, "center")) tx = x + (boxW - tw) * 0.5f;
                else if (align && !strcmp(align, "right")) tx = x + (boxW - tw);
                drawText(lines[li].c_str(), tx, y + blockVOff + topInset + li * lineH, sc, col[0], col[1], col[2], col[3], face);
            }
        }
    };

    // Current game/system context for metadata + art binding.
    const std::string curRom =
        (gamelist && mEsdeSysSel < (int)mEsdeSysList.size())
            ? [&] {
                  const auto& sys = mXmbSystems[mEsdeSysList[mEsdeSysSel]];
                  return (mEsdeGameSel >= 0 && mEsdeGameSel < (int)sys.roms.size())
                             ? sys.roms[mEsdeGameSel] : std::string();
              }()
            : std::string();

    // The primary navigation element (systems carousel / games textlist / grid). Drawn INLINE
    // at its own zIndex position (ES-DE draws strictly by zIndex) so higher-zIndex chrome
    // (logo, clock, systemstatus, helpsystem) paints on top of it rather than being covered.
    auto drawPrimary = [&](const nanoesde::Element* primary) {
        float x, y, w, h; rectOf(primary, x, y, w, h);
        // Set once the carousel band clip is established (below); a cover item restores this instead
        // of blanket-disabling the scissor, so it does not strip the band clip off later items.
        bool carClip = false;
        const bool isCarousel = primary->type == "carousel";
        const bool isGrid = primary->type == "grid";
        // ES-DE CarouselComponent::applyTheme seeds size = (1.0, 0.23240) and pos = (0, 0.38378)
        // BEFORE reading theme values, so a variant that omits the carousel's pos/size (e.g.
        // slate's noGameMedia system view, reached via the noMedia trigger) still gets a centred
        // full-width band instead of collapsing to the top-left. Apply the same defaults for any
        // missing axis, re-anchoring by origin.
        if (isCarousel && (!primary->has("size") || !primary->has("pos"))) {
            float ox = primary->getPair("origin", 0, 0), oy = primary->getPair("origin", 1, 0);
            if (!primary->has("size") && !primary->has("maxSize")) {
                w = mWidth; h = mHeight * 0.23240f;
            }
            float px = primary->has("pos") ? primary->getPair("pos", 0, 0.0f) : 0.0f;
            float py = primary->has("pos") ? primary->getPair("pos", 1, 0.0f) : 0.38378f;
            x = px * mWidth - ox * w;
            y = py * mHeight - oy * h;
        }
        int count = gamelist
                        ? (mEsdeSysSel < (int)mEsdeSysList.size()
                               ? (int)mXmbSystems[mEsdeSysList[mEsdeSysSel]].roms.size() : 0)
                        : (int)mEsdeSysList.size();
        int sel = gamelist ? mEsdeGameSel : mEsdeSysSel;
        auto label = [&](int i) -> std::string {
            if (gamelist) {
                const auto& sys = mXmbSystems[mEsdeSysList[mEsdeSysSel]];
                bool haveRom = (i >= 0 && i < (int)sys.roms.size());
                // ES-DE labels each game by its <name> metadata (gamelist.xml), not the filename-
                // derived display name, so a game named "Dangan" whose file is "Dangan GB.gb" reads
                // "Dangan" like the control. Fall back to nano's display name when no metadata name.
                const ScrapeEntry* se = haveRom ? esdeMetaFor(sys.roms[i]) : nullptr;
                std::string nm;
                if (se && !se->title.empty()) nm = se->title;
                else if (i >= 0 && i < (int)sys.displayNames.size()) nm = sys.displayNames[i];
                // ES-DE's TextListComponent prepends a favourite indicator (the Font Awesome star
                // U+F005, or "* " in ascii mode) before a favourited game, gated by the textlist's
                // <indicators> (default "symbols"; "none" disables it). Only the textlist primary
                // shows it (GamelistBase), never the carousel/grid, and never in a favourites
                // collection (nano has none). The star renders through nano's FA fallback face.
                if (haveRom && !isCarousel && !isGrid &&
                    (isFavorite(sys.roms[i]) || (se && se->favorite))) {
                    std::string ind = primary->getS("indicators", std::string("symbols"));
                    if (ind == "ascii")     nm = "* " + nm;
                    else if (ind != "none") nm = "\xEF\x80\x85  " + nm;   // U+F005 + two spaces
                }
                return nm;
            }
            // System view: ES-DE labels each entry with the system's FULL name (getFullName from
            // es_systems.xml), not the short id, so map nano's system to its ES-DE full name and
            // fall back to nano's own name for a non-standard system.
            if (i < 0 || i >= (int)mEsdeSysList.size()) return std::string();
            const auto& s = mXmbSystems[mEsdeSysList[i]];
            std::string full = esdeSystemFullName(s.romDir.empty() ? s.shortname : s.romDir);
            return full.empty() ? s.name : full;
        };
        // Per-cell rom path for the grid's cover art (reuses the sys.roms indexing).
        auto gameRom = [&](int i) -> std::string {
            if (!gamelist || mEsdeSysSel >= (int)mEsdeSysList.size()) return std::string();
            const auto& sys = mXmbSystems[mEsdeSysList[mEsdeSysSel]];
            return (i >= 0 && i < (int)sys.roms.size()) ? sys.roms[i] : std::string();
        };

        if (count <= 0) {
            float sc = h > 0 ? h * 0.2f / FONT_CHAR_H : mHeight * 0.03f / FONT_CHAR_H;
            drawText(gamelist ? "No games" : "No systems", x + 8, y + 8, sc, 0.8f, 0.8f, 0.85f, 1.0f);
        } else if (isCarousel) {
            // ES-DE horizontal carousel (CarouselComponent): items sit at fixed positions
            // spaced by itemSpacing = ((size - itemSize*maxItemCount)/maxItemCount) + itemSize;
            // a floating "camera offset" (mEsdeCamOffset) slides across them and the item under
            // it is centred. The index wraps, so the strip loops seamlessly with no black edges.
            // ES-DE fills the carousel box with its background band before the items (drawRect over
            // the whole carousel using color/colorEnd, gradientType direction). The DEFAULT band is
            // 0xFFFFFFD8 (white, alpha 0.847), NOT transparent (CarouselComponent.h:1335), so a theme
            // that omits <color> (carbon) still shows the light band; a theme wanting none sets
            // color=00000000 explicitly (art-book-next, aura, canvas, modern, analogue). nano
            // defaulted this to transparent and drew nothing, dropping carbon's band. Seed the
            // upstream default so it survives, honour a colour->colorEnd gradient like ES-DE.
            float carBg[4] = {1.0f, 1.0f, 1.0f, 0xD8 / 255.0f};   // ES-DE default 0xFFFFFFD8
            primary->getColor("color", carBg);                    // theme override (may be 00000000)
            float carEnd[4]; bool carGrad = primary->getColor("colorEnd", carEnd);
            bool carGradHoriz = primary->getS("gradientType", std::string("horizontal")) != "vertical";
            // In-game overlay: a normal (partial) carousel band is chrome and still draws over the
            // scrim, but a band that fills the whole screen opaquely would re-occlude the app, so skip
            // only that case (mirrors esdeCoversScreen for the primary's own band).
            bool carFullOpaque = (w >= mWidth * 0.985f && h >= mHeight * 0.985f && carBg[3] >= 0.98f && !carGrad);
            if (w > 1 && h > 1 && !(esdeInGameScrim && carFullOpaque) &&
                (carBg[3] > 0.0f || (carGrad && carEnd[3] > 0.0f))) {
                if (carGrad && (carEnd[0] != carBg[0] || carEnd[1] != carBg[1] ||
                                carEnd[2] != carBg[2] || carEnd[3] != carBg[3]))
                    drawIconTexFx(esdeWhiteTex(), x, y, w, h, carBg[0], carBg[1], carBg[2], carBg[3],
                                  0.0f, 1.0f, 0.0f, 0.0f, carEnd, carGradHoriz);
                else
                    drawQuad(x, y, w, h, carBg[0], carBg[1], carBg[2], carBg[3]);
            }
            float maxItemCount = primary->getF("maxItemCount", 3.0f);
            maxItemCount = std::min(30.0f, std::max(0.5f, maxItemCount));   // ES-DE clamp
            float itemScale = std::min(3.0f, std::max(0.2f, primary->getF("itemScale", 1.2f)));
            // Distance-based item shading: unfocused items fade to unfocusedItemOpacity
            // (default 0.5) and dim toward unfocusedItemDimming (default 1.0 = off), both
            // interpolated to full at the centre, exactly as CarouselComponent does.
            float unfOpacity = std::min(1.0f, std::max(0.0f, primary->getF("unfocusedItemOpacity", 0.5f)));
            float unfDimming = std::min(1.0f, std::max(0.0f, primary->getF("unfocusedItemDimming", 1.0f)));
            // ES-DE sizes carousel items relative to the SCREEN (not the carousel box), default
            // {0.25, 0.155}, clamped to [0.05, 1.0] (CarouselComponent).
            float isx = std::min(1.0f, std::max(0.05f, primary->getPair("itemSize", 0, 0.25f)));
            float isy = std::min(1.0f, std::max(0.05f, primary->getPair("itemSize", 1, 0.155f)));
            float itemW = isx * mWidth;
            float itemH = isy * mHeight;
            // ES-DE carousel type: horizontal (items along X) or vertical (items along Y, e.g.
            // Analogue OS Menu's gamelist carousel). The spacing/slide axis swaps accordingly; the
            // wheel variants fall back to their straight axis here. ES-DE's tokens are camelCase:
            // horizontal | vertical | horizontalWheel | verticalWheel (CarouselComponent.h:1345),
            // so only the two vertical tokens select the Y axis.
            std::string carType = primary->getS("type", std::string("horizontal"));
            bool isVertical = (carType == "vertical" || carType == "verticalWheel");
            float spacing = isVertical
                ? ((h - itemH * maxItemCount) / maxItemCount) + itemH
                : ((w - itemW * maxItemCount) / maxItemCount) + itemW;
            if (spacing < 1.0f) spacing = isVertical ? itemH : itemW;
            // ES-DE WHEEL carousels (verticalWheel / horizontalWheel): items are NOT laid out along a
            // straight axis. They sit co-located at the wheel centre (itemSpacing stays {0,0}) and each
            // one is rotated by itemRotation*distance about a pivot itemRotationOrigin*itemSize away, so
            // the items trace an arc and each tilts by that angle (CarouselComponent.h:490 isWheel,
            // :745 itemSpacing{0,0}, :1128-1145 the per-item translate-rotate-translate). Non-wheel
            // carousels ignore all of this. Defaults itemRotation 7.5, itemRotationOrigin {-3,0.5}
            // (CarouselComponent.h:248). Everything below is gated on isWheel so straight carousels are
            // untouched.
            const bool isWheel = (carType == "verticalWheel" || carType == "horizontalWheel");
            float wheelDegPerDist = 0.0f, wheelPivotX = 0.0f, wheelPivotY = 0.0f;
            if (isWheel) {
                wheelDegPerDist = primary->getF("itemRotation", 7.5f);
                float wroX = primary->getPair("itemRotationOrigin", 0, -3.0f);
                float wroY = primary->getPair("itemRotationOrigin", 1, 0.5f);
                // xOffTrans/yOffTrans = -itemRotationOrigin * itemSize; a horizontalWheel makes the item
                // axis horizontal so its pivot has no cross-axis (Y) component.
                wheelPivotX = -wroX * itemW;
                wheelPivotY = (carType == "horizontalWheel") ? 0.0f : -wroY * itemH;
            }
            // Cross-axis item centre honouring itemHorizontalAlignment (vertical carousels) and
            // itemVerticalAlignment (horizontal carousels), mirroring ES-DE's xOff/yOff
            // (CarouselComponent.h:815-831): left/top seats the item box against the near edge,
            // right/bottom against the far edge, else centred. Default centre = unchanged.
            std::string itemHA = primary->getS("itemHorizontalAlignment", std::string());
            std::string itemVA = primary->getS("itemVerticalAlignment", std::string());
            float cx = isVertical
                       ? ((itemHA == "left")  ? x + itemW * 0.5f
                          : (itemHA == "right") ? x + w - itemW * 0.5f
                                                : x + w * 0.5f)
                       : x + w * 0.5f;
            float cyc = !isVertical
                        ? ((itemVA == "top")    ? y + itemH * 0.5f
                           : (itemVA == "bottom") ? y + h - itemH * 0.5f
                                                  : y + h * 0.5f)
                        : y + h * 0.5f;
            // ES-DE horizontalOffset/verticalOffset (CarouselComponent.h:833-834): a uniform
            // per-item shift of mSize.x*hOff / mSize.y*vOff (clamped [-1,1]) applied to EVERY
            // item regardless of orientation, so a theme can seat the wheel off-centre (canvas
            // pushes its marquee strip left, aura drops its boxart row down). Fold it into the
            // base centres; both axes shift together as ES-DE does. Dropped at parse before, so
            // these carousels sat centred. No-op at the 0 default.
            {
                auto cl11 = [](float v) { return std::min(1.0f, std::max(-1.0f, v)); };
                cx  += (float)w * cl11(primary->getF("horizontalOffset", 0.0f));
                cyc += (float)h * cl11(primary->getF("verticalOffset", 0.0f));
            }
            float imgC[4] = {1, 1, 1, 1}; primary->getColor("imageColor", imgC);
            float selC[4]; colorOf(primary, "textSelectedColor", 1, 1, 1, 1, selC);
            float unC[4]; colorOf(primary, "textColor", 0.85f, 0.85f, 0.85f, 0.6f, unC);
            const std::string artTmpl = gamelist ? std::string() : primary->getPath("staticImage");
            const std::string defTmpl = gamelist ? std::string() : primary->getPath("defaultImage");

            // Advance the eased camera offset toward the current cursor (shortest wrapped path).
            const float posMax = (float)count;
            if (mEsdeCamCursor < 0) {                        // first frame: snap, no slide
                mEsdeCamOffset = (float)sel; mEsdeCamCursor = sel; mEsdeCamAnimDur = 0.0f;
            } else if (sel != mEsdeCamCursor) {              // cursor moved: start a new slide
                float startPos = mEsdeCamOffset, target = (float)sel, endPos = target;
                float dist = fabsf(endPos - startPos);
                if (fabsf(target + posMax - startPos) < dist) endPos = target + posMax;
                else if (fabsf(target - posMax - startPos) < dist) endPos = target - posMax;
                mEsdeCamStart = startPos; mEsdeCamTarget = endPos;
                mEsdeCamAnimStart = (int64_t)uptimeMillis(); mEsdeCamAnimDur = 400.0f;
                mEsdeCamCursor = sel;
            }
            if (mEsdeCamAnimDur > 0.0f) {
                float t = (float)((int64_t)uptimeMillis() - mEsdeCamAnimStart) / mEsdeCamAnimDur;
                if (t >= 1.0f) { t = 1.0f; mEsdeCamAnimDur = 0.0f; }
                else { mEsdeWantsFastFrame = true; mDisplayDirty = true; }   // keep rendering until settled
                float te = 1.0f - (1.0f - t) * (1.0f - t);   // ease-out (ES-DE curve)
                float f = mEsdeCamTarget * te + mEsdeCamStart * (1.0f - te);
                while (f < 0.0f) f += posMax;
                while (f >= posMax) f -= posMax;
                mEsdeCamOffset = f;
            }
            const float camOffset = mEsdeCamOffset;

            int center = (int)floorf(camOffset + 0.5f);
            int span = (int)ceilf(maxItemCount / 2.0f) + 3;
            // A wheel draws a fixed count each side (itemsBeforeCenter/After, default 8), NOT maxItemCount
            // (CarouselComponent.h:499). Replace the span with that count so e.g. aura's fullscreen logo
            // (itemsBeforeCenter/After 0) shows only the focused item instead of over-drawing stacked
            // neighbours. Capped so a huge count cannot blow the per-frame draw budget.
            if (isWheel) {
                int wb = (int)primary->getF("itemsBeforeCenter", 8.0f);
                int wa = (int)primary->getF("itemsAfterCenter", 8.0f);
                span = std::min(12, std::max(wb, wa));
            }
            auto wrapIdx = [&](int v) { int n = count; return ((v % n) + n) % n; };
            auto drawItem = [&](int iRaw) {
                int idx = wrapIdx(iRaw);
                float distance = (float)iRaw - camOffset;
                // ES-DE selectedItemMargins: open a gap around the selected item along the scroll
                // axis - items BEFORE it shift by -margin.x, items AFTER it by +margin.y (the pair
                // is clamped [-1,1] and scaled by screen height (vertical) / width (horizontal)),
                // scaled by |distance| for the near items during a slide (CarouselComponent.h:945-962).
                float selMargin = 0.0f;
                if (primary->has("selectedItemMargins")) {
                    auto cl11 = [](float v) { return std::min(1.0f, std::max(-1.0f, v)); };
                    float mBefore = cl11(primary->getPair("selectedItemMargins", 0, 0.0f));
                    float mAfter = cl11(primary->getPair("selectedItemMargins", 1, 0.0f));
                    float dim = isVertical ? (float)mHeight : (float)mWidth;
                    if (distance < 0.0f) selMargin = -mBefore * dim;
                    else if (distance > 0.0f) selMargin = mAfter * dim;
                    if (fabsf(distance) < 1.0f) selMargin *= fabsf(distance);
                }
                // Items slide along X (horizontal) or Y (vertical); the other axis stays centred.
                // A wheel instead rotates each item about the pivot: pos = centre + (R(angle)-I)*pivot,
                // angle = itemRotation*distance, and the item quad is tilted by that same angle.
                float slotCx, slotCy, itemAngleRad = 0.0f;
                if (isWheel) {
                    // ES-DE renders in the SAME top-left / Y-down screen space nano uses (its wheel
                    // transform is translate(-pivot) . rotate(itemRotation*distance) . translate(pivot)
                    // with pivot = -itemRotationOrigin*itemSize), so the item is rotated about the pivot
                    // by exactly itemRotation*distance with NO sign flip - the position AND the tilt.
                    itemAngleRad = (wheelDegPerDist * distance) * (float)(M_PI / 180.0);
                    float caw = cosf(itemAngleRad), saw = sinf(itemAngleRad);
                    float rx = wheelPivotX * caw - wheelPivotY * saw;
                    float ry = wheelPivotX * saw + wheelPivotY * caw;
                    slotCx = cx + (rx - wheelPivotX);
                    slotCy = cyc + (ry - wheelPivotY);
                } else {
                    slotCx = isVertical ? cx : cx + distance * spacing + selMargin;
                    slotCy = isVertical ? cyc + distance * spacing + selMargin : cyc;
                }
                // ES-DE selectedItemOffset: nudge the selected item (and its near neighbours) off the
                // rail as it settles - both components of the [-1,1] pair scale by ONE screen
                // dimension (width for horizontal carousels, height for vertical) and fall off with
                // (1 - |distance|) so only |distance|<1 items move (CarouselComponent.h:964-971,1466).
                if (!isWheel && primary->has("selectedItemOffset")) {
                    auto cl11 = [](float v) { return std::min(1.0f, std::max(-1.0f, v)); };
                    float fall = 1.0f - std::min(1.0f, fabsf(distance));
                    if (fall > 0.0f) {
                        float dimS = isVertical ? (float)mHeight : (float)mWidth;
                        slotCx += fall * cl11(primary->getPair("selectedItemOffset", 0, 0.0f)) * dimS;
                        slotCy += fall * cl11(primary->getPair("selectedItemOffset", 1, 0.0f)) * dimS;
                    }
                }
                float ss = 1.0f;
                if (itemScale > 1.0f) {                      // center item largest, neighbours 1x
                    ss = 1.0f + (itemScale - 1.0f) * (1.0f - fabsf(distance));
                    ss = std::min(itemScale, std::max(1.0f, ss));
                } else if (itemScale < 1.0f) {               // center item 1x, neighbours shrink
                    ss = 1.0f + (1.0f - itemScale) * (fabsf(distance) - 1.0f);
                    ss = std::max(itemScale, std::min(1.0f, ss));
                }
                int bw = (int)(itemW * ss), bh = (int)(itemH * ss);
                float ad = fabsf(distance);
                float opacity = (distance == 0.0f || unfOpacity == 1.0f) ? 1.0f
                              : (ad >= 1.0f ? unfOpacity
                                 : unfOpacity + ((1.0f - unfOpacity) - (1.0f - unfOpacity) * ad));
                float dimming = (distance == 0.0f || unfDimming == 1.0f) ? 1.0f
                              : (ad >= 1.0f ? unfDimming
                                 : unfDimming + ((1.0f - unfDimming) - (1.0f - unfDimming) * ad));
                // ES-DE carousel saturation: imageSaturation applies to every item; unfocusedItemSat-
                // uration desaturates off-centre items toward that value, interpolated by |distance|
                // exactly like opacity/dimming above (CarouselComponent.h:1033-1042). Route the cover
                // through the FX shader only when the result is non-default (Analogue's greyscale
                // thumbnails, ABN famicom monochrome); a plain carousel keeps the shared draw path.
                float imgSat  = primary->getF("imageSaturation", 1.0f);
                float unfSat  = primary->has("unfocusedItemSaturation")
                              ? primary->getF("unfocusedItemSaturation", 1.0f) : imgSat;
                float itemSat = (distance == 0.0f || unfSat == imgSat) ? imgSat
                              : (ad >= 1.0f ? unfSat
                                 : unfSat + ((imgSat - unfSat) - (imgSat - unfSat) * ad));
                // ES-DE scales the item corner radius by the focused-item scale (CarouselComponent),
                // like the grid does, so the enlarged selected item's corners are not too tight.
                float carRad = std::min(0.5f, std::max(0.0f, primary->getF("imageCornerRadius", 0.0f)))
                             * (itemScale > 1.0f ? itemScale : 1.0f) * mWidth;
                auto drawCover = [&](GLuint tx, float dx, float dy, float dw, float dh,
                                     float cr, float cg, float cb, float ca) {
                    if (itemSat != 1.0f || carRad > 0.0f)
                        drawIconTexFx(tx, dx, dy, dw, dh, cr, cg, cb, ca, itemAngleRad, itemSat, 0.0f, carRad);
                    else
                        drawIconTex(tx, dx, dy, dw, dh, cr, cg, cb, ca, itemAngleRad);
                };
                if (!gamelist && !artTmpl.empty()) {
                    int sysIdx = mEsdeSysList[idx];
                    EsdeSvg art = esdeArtTex(esdeResolveSystemPath(artTmpl, sysIdx), bw, bh);
                    if (!art.tex && !defTmpl.empty())
                        art = esdeArtTex(esdeResolveSystemPath(defTmpl, sysIdx), bw, bh);
                    if (art.tex) {
                        // ES-DE tints the focused item with imageSelectedColor when the theme sets
                        // it, and every other item with imageColor.
                        float ic[4] = {imgC[0], imgC[1], imgC[2], imgC[3]};
                        if (idx == sel) primary->getColor("imageSelectedColor", ic);
                        drawCover(art.tex, slotCx - art.w * 0.5f, slotCy - art.h * 0.5f,
                                  (float)art.w, (float)art.h,
                                  ic[0] * dimming, ic[1] * dimming, ic[2] * dimming,
                                  ic[3] * opacity);
                        return;
                    }
                }
                // Gamelist carousel: ES-DE shows each game's imageType media (screenshot/cover/...).
                // nano keeps one scraped image per game (the box), so contain-fit that into the item
                // box; games with no box fall through to the text label below (ES-DE's text fallback).
                // imageType=none is a TEXT carousel (Atari 50 Menu lists game names, no media), so
                // skip the art entirely and let the label draw.
                if (gamelist && primary->getS("imageType", std::string()) != "none") {
                    std::string rom = gameRom(idx);
                    float ar = 1.0f; GLuint tex = rom.empty() ? 0
                        : esdeGameMediaTex(rom, primary->getS("imageType", std::string("cover")), &ar);
                    if (tex && ar > 0.0f && bw > 1 && bh > 1) {
                        float ic[4] = {imgC[0], imgC[1], imgC[2], imgC[3]};
                        if (idx == sel) primary->getColor("imageSelectedColor", ic);
                        // ES-DE CarouselComponent imageFit: contain (default; fit within the box),
                        // fill (stretch to the box, aspect ignored), cover (scale to fill the box and
                        // clip the overflow, like CSS object-fit: cover - Analogue's gamelist carousel).
                        const std::string& fit = primary->getS("imageFit", std::string("contain"));
                        float fw, fh; bool clip = false;
                        if (fit == "fill") {
                            fw = (float)bw; fh = (float)bh;
                        } else if (fit == "cover") {
                            if ((float)bw / ar >= (float)bh) { fw = (float)bw; fh = (float)bw / ar; }
                            else { fh = (float)bh; fw = (float)bh * ar; }
                            clip = true;
                        } else {                       // contain
                            fw = (float)bw; fh = fw / ar;
                            if (fh > bh) { fh = (float)bh; fw = fh * ar; }
                        }
                        float drawX = slotCx - fw * 0.5f, drawY = slotCy - fh * 0.5f;
                        if (clip) {
                            // ES-DE cover honours imageCropPos (default 0.5 = centred): cropPos.y=0
                            // keeps the TOP slice of a tall cover, 1 the bottom (coverFitCrop
                            // mCropOffset). Only one axis overflows, so the other offset is zero.
                            float cpx = std::min(1.0f, std::max(0.0f, primary->getPair("imageCropPos", 0, 0.5f)));
                            float cpy = std::min(1.0f, std::max(0.0f, primary->getPair("imageCropPos", 1, 0.5f)));
                            drawX += (0.5f - cpx) * (fw - (float)bw);
                            drawY += (0.5f - cpy) * (fh - (float)bh);
                            scissorLogicalRect(slotCx - bw * 0.5f, slotCy - bh * 0.5f, (float)bw, (float)bh);
                        }
                        drawCover(tex, drawX, drawY, fw, fh,
                                  ic[0] * dimming, ic[1] * dimming, ic[2] * dimming, ic[3] * opacity);
                        // Restore the carousel band clip rather than turning scissoring off, or later
                        // items in this frame lose the band and bleed past it.
                        if (clip) { if (carClip) scissorLogicalRect(x, y, w, h); else glDisable(GL_SCISSOR_TEST); }
                        return;
                    }
                    // A game with no media of this imageType shows the carousel's <defaultImage>
                    // placeholder (ES-DE CarouselComponent), NOT its name - so a cover carousel of
                    // un-scraped games shows placeholder tiles rather than overlapping text labels.
                    if (bw > 1 && bh > 1) {
                        std::string di = primary->getPath("defaultImage");
                        if (!di.empty()) {
                            EsdeSvg da = esdeArtTex(esdeResolveSystemPath(di, mEsdeSysList[mEsdeSysSel]), bw, bh);
                            if (da.tex) {
                                float ic[4] = {imgC[0], imgC[1], imgC[2], imgC[3]};
                                if (idx == sel) primary->getColor("imageSelectedColor", ic);
                                drawCover(da.tex, slotCx - da.w * 0.5f, slotCy - da.h * 0.5f,
                                          (float)da.w, (float)da.h,
                                          ic[0] * dimming, ic[1] * dimming, ic[2] * dimming, ic[3] * opacity);
                                return;
                            }
                        }
                    }
                }
                std::string s = label(idx); if (s.empty()) return;
                // ES-DE cases the carousel entry text (system name in the system view, game name in
                // the gamelist) by the primary's letterCase when it builds the entry list
                // (SystemView/GamelistBase applying getLetterCase()); apply the same transform so a
                // themed uppercase/lowercase/capitalize carousel label matches. Unset -> no-op.
                esdeLetterCase(s, primary->getS("letterCase", std::string()));
                bool selected = (idx == sel);
                // ES-DE's carousel builds its text labels at FONT_SIZE_LARGE_FIXED when the theme sets
                // no fontSize (Font.h: 0.085*min(W,H) landscape, 0.080 vertical - screen orientation,
                // not carousel axis), and scales the whole focused item - text included - by itemScale
                // (getFromTheme is passed itemScale as the size multiplier). nano scales the item by
                // `ss` (itemScale at the centre, 1 for neighbours), so size the label the same way:
                // the base large font times ss, honoring a themed fontSize via fontPx. The unfocused
                // dimming stays in the label colour (unC), not the size.
                float largeFont = (mWidth < mHeight) ? 0.080f : 0.085f;
                float fsc = fontPx(primary, largeFont) * ss;
                int lblFace = faceOf(primary);
                float tw = measureText(s.c_str(), fsc, lblFace);
                float* col = selected ? selC : unC;
                float txtY = isVertical ? slotCy - fsc * FONT_CHAR_H * 0.5f : y + h * 0.4f;
                // ES-DE aligns a text item inside its slot by itemHorizontalAlignment (the item
                // origin.x): left seats the label's left edge at the item box left, right its right
                // edge at the box right, else centred. Atari 50 Menu's vertical name list uses left.
                const std::string& itemHA = primary->getS("itemHorizontalAlignment", std::string());
                float labelX = (itemHA == "left")  ? slotCx - itemW * 0.5f
                             : (itemHA == "right") ? slotCx + itemW * 0.5f - tw
                                                   : slotCx - tw * 0.5f;
                drawText(s.c_str(), labelX, txtY, fsc,
                         col[0] * dimming, col[1] * dimming, col[2] * dimming, col[3] * opacity, lblFace);
            };
            // ES-DE clips the carousel to its own box (CarouselComponent::render pushClipRect of
            // pos/size), so it shows exactly maxItemCount items and the strip never bleeds past its
            // band - e.g. Atari 50 Menu's gamelist name list stays inside its panel instead of
            // spilling into the header. Mirror that with a scissor of the carousel box, but only when
            // the box is INSET (a full-screen scissor suppresses the draw on the DRM path - the same
            // gotcha as the image/video cropSize clip).
            bool carInset = x > 0.5f || y > 0.5f || x + w < mWidth - 0.5f || y + h < mHeight - 0.5f;
            carClip = carInset && w > 1.0f && h > 1.0f;
            if (carClip) scissorLogicalRect(x, y, w, h);
            // Draw far-to-near so the centred item ends up on top of its overlapping neighbours.
            // ES-DE renders a single-entry carousel as one centered item with no flanking slots;
            // without this guard wrapIdx maps every slot back to index 0 and the lone item is drawn
            // repeated across the band at unfocused opacity.
            if (count == 1) {
                drawItem(center);
            } else {
                for (int k = span; k >= 1; k--) { drawItem(center - k); drawItem(center + k); }
                drawItem(center);
            }
            if (carClip) glDisable(GL_SCISSOR_TEST);
        } else if (isGrid) {
            // ES-DE GridComponent: a multi-column cover grid with row-window scrolling and a
            // focus scale + dim/opacity ease. Rows are windowed so only visible covers are bound
            // (romBoxartTex self-caps at 96). Saturation is applied via the FX shader per cell
            // (below); brightness/gradients are still no-ops; dimming folds into the RGB tint,
            // opacity into alpha.
            auto clampf = [](float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); };
            auto mixf = [](float a, float b, float t) { return a + (b - a) * t; };
            // ES-DE grid imageSaturation / unfocusedItemSaturation (GridComponent): every cover is
            // desaturated toward these, per-cell by focus. Constant for the element; the per-cell
            // value is eased below. Reuses the safe drawIconTexFx (falls back if the FX shader is out).
            // ES-DE clamps these in Grid/CarouselComponent: imageSaturation and
            // unfocusedItemSaturation to [0,1], imageBrightness to [-2,2]. An out-of-range theme
            // value would otherwise over/under-drive the FX shader past what the control shows.
            const float gImgSat = clampf(primary->getF("imageSaturation", 1.0f), 0.0f, 1.0f);
            const float gUnfSat = primary->has("unfocusedItemSaturation")
                                ? clampf(primary->getF("unfocusedItemSaturation", 1.0f), 0.0f, 1.0f) : gImgSat;
            const float gImgBright = clampf(primary->getF("imageBrightness", 0.0f), -2.0f, 2.0f);   // fixed per cover in ES-DE

            float itemScaleForRad;   // set below once itemScale is known (used by cornerRad)
            auto cornerRad = [&](const char* k) {   // ES-DE: clamp(r,0,0.5)*max(itemScale,1)*width
                return clampf(primary->getF(k, 0.0f), 0.0f, 0.5f) * itemScaleForRad * mWidth;
            };

            float itemSX = primary->getPair("itemSize", 0, 0.15f);
            float itemSY = primary->getPair("itemSize", 1, 0.25f);
            float itemW = itemSX * mWidth, itemH = itemSY * mHeight;
            if (itemSX < 0) itemW = itemH;
            if (itemSY < 0) itemH = itemW;
            itemW = clampf(itemW, 0.05f * mWidth, (float)mWidth);
            itemH = clampf(itemH, 0.05f * mHeight, (float)mHeight);
            float itemScale = clampf(primary->getF("itemScale", 1.05f), 0.5f, 2.0f);
            itemScaleForRad = itemScale >= 1.0f ? itemScale : 1.0f;
            bool scaleInwards = primary->getB("scaleInwards", false) && itemScale > 1.0f;
            bool fractionalRows = primary->getB("fractionalRows", false);
            // Auto itemSpacing = 0 when itemScale<1 (ES-DE GridComponent), else the scale margin;
            // never negative (a negative spacing would inflate columns and shrink visRows).
            // ES-DE clamps an explicit itemSpacing to [0, 0.1] of the screen dimension
            // (GridComponent applyTheme); an omitted spacing auto-derives from the scale margin.
            float spX = primary->has("itemSpacing")
                        ? clampf(primary->getPair("itemSpacing", 0, 0.0f), 0.0f, 0.1f) * mWidth
                        : (itemScale < 1.0f ? 0.0f : ((itemScale - 1.0f) * itemW) * 0.5f);
            float spY = primary->has("itemSpacing")
                        ? clampf(primary->getPair("itemSpacing", 1, 0.0f), 0.0f, 0.1f) * mHeight
                        : (itemScale < 1.0f ? 0.0f : ((itemScale - 1.0f) * itemH) * 0.5f);
            float hMargin = 0.0f, vMargin = 0.0f;
            if (itemScale >= 1.0f) {
                hMargin = (itemW * (scaleInwards ? 1.0f : itemScale) - itemW) * 0.5f;
                vMargin = (itemH * (scaleInwards ? 1.0f : itemScale) - itemH) * 0.5f;
            }
            int columns = 0;
            { float acc = hMargin * 2.0f;
              for (;;) { acc += itemW; if (columns != 0) acc += spX; if (acc > w) break; ++columns; } }
            if (columns < 1) columns = 1;
            mEsdeGridColumns = columns;
            int rowsTotal = (count + columns - 1) / columns;

            float visRows = h / (itemH + spY);
            visRows -= (vMargin / h) * visRows * 2.0f;
            visRows += (spY / h) * visRows;
            if (!fractionalRows) visRows = floorf(visRows);
            if (visRows <= 0.0f) visRows = 1.0f;

            // Scroll toward the cursor's row (clamped, no wrap = LIST_PAUSE_AT_END).
            int cursorRow = sel / columns;
            float endRow = 0.0f;
            if ((float)(cursorRow + 1) > visRows) endRow = (float)(cursorRow + 1) - visRows;
            float maxScroll = fmaxf(0.0f, (float)rowsTotal - visRows);
            endRow = clampf(endRow, 0.0f, maxScroll);
            if (mEsdeGridCursor < 0) {
                mEsdeGridScroll = endRow; mEsdeGridTransFactor = 1.0f;
                mEsdeGridCursor = sel; mEsdeGridLastCursor = sel; mEsdeGridAnimDur = 0.0f;
            } else if (sel != mEsdeGridCursor) {
                mEsdeGridScrollStart = mEsdeGridScroll; mEsdeGridScrollTarget = endRow;
                mEsdeGridAnimStart = (int64_t)uptimeMillis(); mEsdeGridAnimDur = 250.0f;  // ES-DE GridComponent
                mEsdeGridLastCursor = mEsdeGridCursor; mEsdeGridCursor = sel;
                mEsdeGridTransFactor = 0.0f;
            }
            if (mEsdeGridAnimDur > 0.0f) {
                float t = (float)((int64_t)uptimeMillis() - mEsdeGridAnimStart) / mEsdeGridAnimDur;
                if (t >= 1.0f) { t = 1.0f; mEsdeGridAnimDur = 0.0f; } else { mEsdeWantsFastFrame = true; mDisplayDirty = true; }
                float te = 1.0f - (1.0f - t) * (1.0f - t);
                mEsdeGridScroll = mEsdeGridScrollTarget * te + mEsdeGridScrollStart * (1.0f - te);
                // ES-DE feeds the SAME eased t into mTransitionFactor (the focus scale/
                // opacity/dim ramp), so the selected cover grows and brightens on the
                // ease-out curve, not linearly.
                mEsdeGridTransFactor = te;
            }
            float scroll = mEsdeGridScroll, tf = mEsdeGridTransFactor;

            float dimY = h;
            if (!fractionalRows && h > itemH) dimY = visRows * (itemH + spY) + vMargin * 2.0f - spY;
            scissorLogicalRect(x, y, w, dimY);

            int currRow = (int)ceilf(scroll);
            int visInt = (int)ceilf(visRows);
            bool anim = mEsdeGridAnimDur > 0.0f;
            int startRow, loadRows = visInt + 1;
            if (currRow > 0) {
                startRow = (anim || spY <= vMargin) ? currRow - 1 : (fractionalRows ? currRow - 1 : currRow);
                if (spY < vMargin && anim) { startRow -= 1; loadRows += 1; }
                if (startRow < 0) startRow = 0;
                loadRows += 1;
            } else startRow = 0;
            int startPos = startRow * columns;
            int endPos = std::min(count, startPos + loadRows * columns);

            int focSys = (!mEsdeSysList.empty() && mEsdeSysSel < (int)mEsdeSysList.size())
                             ? mEsdeSysList[mEsdeSysSel] : -1;
            // NOTE: ES-DE clamps unfocusedItemOpacity to [0.1, 1.0] (Grid/CarouselComponent), i.e. it
            // never fully hides an unfocused item; nano floors at 0.0. Two aura fullscreen system
            // views set unfocusedItemOpacity=0, so the control keeps a faint 0.1 ghost during the
            // slide while nano hides it. Left at 0.0 pending a 6c00 A/B (the control runs 3.4.0-56;
            // this tree is 3.4.0-253) since a prior note claimed the control shows 0 here.
            float unfOp = clampf(primary->getF("unfocusedItemOpacity", 1.0f), 0.0f, 1.0f);
            float unfDim = clampf(primary->getF("unfocusedItemDimming", 1.0f), 0.0f, 1.0f);
            std::string defTmpl = primary->getPath("defaultImage");
            std::string glc = primary->getS("letterCase", std::string());
            bool anyPending = false;

            auto drawCell = [&](int i) {
                int col = i % columns, row = i / columns;
                float ccx = x + hMargin + itemW * col + itemW * 0.5f + spX * col;
                float ccy = y + vMargin + itemH * row + itemH * 0.5f + spY * row - (itemH + spY) * scroll;
                float scale, opacity, dim, sat;
                // ES-DE grid saturation mirrors dim/opacity: the focused cell reaches imageSaturation,
                // unfocused cells sit at unfocusedItemSaturation, eased by the focus factor tf.
                if (i == sel) { scale = mixf(1.0f, itemScale, tf); opacity = mixf(unfOp, 1.0f, tf); dim = mixf(unfDim, 1.0f, tf); sat = mixf(gUnfSat, gImgSat, tf); }
                else if (i == mEsdeGridLastCursor) { scale = mixf(itemScale, 1.0f, tf); opacity = mixf(1.0f, unfOp, tf); dim = mixf(1.0f, unfDim, tf); sat = mixf(gImgSat, gUnfSat, tf); }
                else { scale = 1.0f; opacity = unfOp; dim = unfDim; sat = gUnfSat; }
                const GLint gridMag = esdeImageMagFilter(primary);
                auto drawCell = [&](GLuint tx, float dx, float dy, float dw, float dh,
                                    float cr, float cg, float cb, float ca) {
                    float rad = cornerRad("imageCornerRadius");   // ES-DE grid rounds the cover cell
                    glBindTexture(GL_TEXTURE_2D, tx);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, gridMag);
                    if (sat != 1.0f || gImgBright != 0.0f || rad > 0.0f)
                        drawIconTexFx(tx, dx, dy, dw, dh, cr, cg, cb, ca, 0.0f, sat, gImgBright, rad);
                    else
                        drawIconTex(tx, dx, dy, dw, dh, cr, cg, cb, ca);
                };
                if (scaleInwards && scale != 1.0f) {                    // keep the scaled cell inside at edges
                    // ES-DE tests the item's STATIC (un-scrolled) layout position against the FULL
                    // element bounds using the CONSTANT itemScale (GridComponent.h:836-849), then
                    // anchors the animated cell to that edge; using the scrolled centre / animated
                    // scale / clipped height (as before) shifted the pull-in mid-scroll.
                    float staticCy = y + vMargin + itemH * row + itemH * 0.5f + spY * row;
                    if (row == 0) ccy += (itemH * 0.5f) * (scale - 1.0f);
                    else if (staticCy + (itemH * 0.5f) * itemScale > y + h)
                        ccy -= (itemH * 0.5f) * (scale - 1.0f);
                    if (col == 0) ccx += (itemW * 0.5f) * (scale - 1.0f);
                    else if (col == columns - 1) ccx -= (itemW * 0.5f) * (scale - 1.0f);
                }
                float cellW = itemW * scale, cellH = itemH * scale;
                float bx = ccx - cellW * 0.5f, by = ccy - cellH * 0.5f;
                // ES-DE grid selector (GridComponent): a selectorImage tinted by selectorColor if the
                // theme sets one, else a selectorColor rounded rect. selectorLayer chooses the draw
                // order relative to the cell background box and the cell art (GridComponent.h
                // 871/908/953): BOTTOM = behind the background, MIDDLE = over the background but UNDER
                // the art (gameOS's opaque pink cursor plate with the system logo on top), TOP
                // (default) = over the art (Catppuccin's selector frame). nano handled only bottom vs
                // top and drew "middle" as top, so gameOS's pink selector buried the selected system's
                // logo. Emit at the correct layer of the three.
                std::string selLayer = primary->getS("selectorLayer", std::string("top"));
                bool selBottom = (selLayer == "bottom");
                bool selMiddle = (selLayer == "middle");
                auto emitSel = [&]() {
                    if (i != sel) return;
                    float srs = clampf(primary->getF("selectorRelativeScale", 1.0f), 0.2f, 1.0f);
                    float selW = cellW * srs, selH = cellH * srs;
                    float selc[4]; bool hasCol = primary->getColor("selectorColor", selc);
                    std::string selImg = primary->getPath("selectorImage");
                    if (!selImg.empty()) {
                        EsdeSvg si = esdeArtTex(selImg, (int)selW, (int)selH);
                        if (si.tex) {
                            float r = hasCol ? selc[0] : 1.0f, g = hasCol ? selc[1] : 1.0f,
                                  b = hasCol ? selc[2] : 1.0f, aa = hasCol ? selc[3] : 1.0f;
                            drawIconTex(si.tex, ccx - si.w * 0.5f, ccy - si.h * 0.5f,
                                        (float)si.w, (float)si.h, r, g, b, aa * opacity);
                            return;
                        }
                    }
                    if (hasCol && selc[3] > 0.0f)
                        drawRoundedRect(ccx - selW * 0.5f, ccy - selH * 0.5f, selW, selH,
                                        cornerRad("selectorCornerRadius"),
                                        selc[0], selc[1], selc[2], selc[3] * opacity);
                };
                if (selBottom) emitSel();       // behind the background box
                float bgc[4];
                if (primary->getColor("backgroundColor", bgc)) {
                    float bgScale = clampf(primary->getF("backgroundRelativeScale", 1.0f), 0.2f, 1.0f);
                    float rad = cornerRad("backgroundCornerRadius");
                    drawRoundedRect(ccx - cellW * bgScale * 0.5f, ccy - cellH * bgScale * 0.5f,
                                    cellW * bgScale, cellH * bgScale, rad,
                                    bgc[0] * dim, bgc[1] * dim, bgc[2] * dim, bgc[3] * opacity);
                }
                if (selMiddle) emitSel();       // over the background, under the cell art
                // System-view grid (e.g. adroit's Grid variant): each cell is a system logo,
                // staticImage resolved per system (./files/logos/${system.theme}.svg), with the
                // defaultImage as the fallback. The cells, cursor and layout are already
                // system-indexed above, so only the per-cell art/label differ from the gamelist.
                if (!gamelist) {
                    int cellSys = (i >= 0 && i < (int)mEsdeSysList.size()) ? mEsdeSysList[i] : -1;
                    float relS = clampf(primary->getF("imageRelativeScale", 1.0f), 0.2f, 1.0f);
                    int bW = (int)(cellW * relS), bH = (int)(cellH * relS);
                    std::string tmpl = primary->getPath("staticImage");
                    EsdeSvg a{};
                    if (cellSys >= 0 && !tmpl.empty())
                        a = esdeArtTex(esdeResolveSystemPath(tmpl, cellSys), bW, bH);
                    if (!a.tex && !defTmpl.empty()) a = esdeArtTex(defTmpl, bW, bH);
                    if (a.tex) {
                        // The focused cell uses imageSelectedColor (adroit tints it dark so the
                        // logo reads against the light selector); others use imageColor.
                        float ic[4] = {1, 1, 1, 1};
                        if (!(i == sel && primary->getColor("imageSelectedColor", ic)))
                            primary->getColor("imageColor", ic);
                        drawCell(a.tex, ccx - a.w * 0.5f, ccy - a.h * 0.5f, (float)a.w, (float)a.h,
                                 ic[0] * dim, ic[1] * dim, ic[2] * dim, ic[3] * opacity);
                    } else {
                        // ES-DE GridComponent::mTextColor defaults to 0x000000FF (BLACK)
                        // (GridComponent.h), overridden only when the theme sets <textColor>. A
                        // coverless cell with no textColor is black, not white.
                        float tc[4] = {0, 0, 0, 1}; primary->getColor("textColor", tc);
                        float fsc = fontPx(primary, 0.03f) * scale;
                        // ES-DE labels a coverless system grid cell with the system's FULL name
                        // (GridComponent uses getFullName, e.g. "Sony PlayStation"), same as the
                        // carousel/textlist. Resolve nano's system to its ES-DE full name rather than
                        // the short id ("psx") so a system-name grid (X-Grid) matches the control; fall
                        // back to nano's own name for a non-standard system.
                        std::string s;
                        if (cellSys >= 0 && cellSys < (int)mXmbSystems.size()) {
                            const auto& gsys = mXmbSystems[cellSys];
                            s = esdeSystemFullName(gsys.romDir.empty() ? gsys.shortname : gsys.romDir);
                            if (s.empty()) s = gsys.name;
                        }
                        esdeLetterCase(s, glc);
                        int gFace = faceOf(primary);
                        float glyphH = fsc * FONT_CHAR_H, tw = measureText(s.c_str(), fsc, gFace);
                        // ES-DE sizes the label TextComponent to itemSize*textRelativeScale (a box inset
                        // from the cell), centres it (ALIGN_CENTER), but with textHorizontalScrolling
                        // left-anchors and marquee-scrolls it once it overflows that box (GridComponent
                        // builds the label with the scroll flags; an overflowing scrolling label
                        // left-anchors at the box origin). nano has no per-cell marquee, so match the
                        // scroll's START frame: left-anchor an overflowing scrolling label at the box
                        // left so its BEGINNING shows (X-Grid's long full names) instead of centring it
                        // (which would show the clipped middle). Clip to the text box either way so the
                        // label cannot bleed into the neighbour, exactly as ES-DE bounds it.
                        float gTrs = clampf(primary->getF("textRelativeScale", 1.0f), 0.2f, 1.0f);
                        float tBoxW = cellW * gTrs, tbLeft = ccx - tBoxW * 0.5f;
                        float textX = ccx - tw * 0.5f;
                        bool tOverflow = tw > tBoxW;
                        if (tOverflow && primary->getB("textHorizontalScrolling", false)) textX = tbLeft;
                        if (tOverflow) scissorLogicalRect(tbLeft, by, tBoxW, cellH);
                        drawText(s.c_str(), textX, ccy - glyphH * 0.5f, fsc,
                                 tc[0] * dim, tc[1] * dim, tc[2] * dim, opacity, gFace);
                        if (tOverflow) scissorLogicalRect(x, y, w, dimY);
                    }
                    if (!selBottom && !selMiddle) emitSel();   // top-layer selector over the system logo
                    return;
                }
                float ar = 1.0f; GLuint cover = 0;
                std::string rom = primary->has("imageType") ? gameRom(i) : std::string();
                if (!rom.empty()) cover = esdeGameMediaTex(rom, primary->getS("imageType", std::string("cover")), &ar);
                if (cover && ar > 0.0f) {
                    float relS = clampf(primary->getF("imageRelativeScale", 1.0f), 0.2f, 1.0f);
                    float boxW = cellW * relS, boxH = cellH * relS;
                    // GridComponent imageFit: contain (default), fill (stretch), cover (fill+clip).
                    const std::string& fit = primary->getS("imageFit", std::string("contain"));
                    float fw, fh; bool clip = false;
                    if (fit == "fill") { fw = boxW; fh = boxH; }
                    else if (fit == "cover") {
                        if (boxW / ar >= boxH) { fw = boxW; fh = boxW / ar; }
                        else { fh = boxH; fw = boxH * ar; }
                        clip = true;
                    } else { fw = boxW; fh = boxW / ar; if (fh > boxH) { fh = boxH; fw = boxH * ar; } }
                    // ES-DE tints the SELECTED cell's cover by imageSelectedColor (default =
                    // imageColor, so unset = no change), and the other cells by imageColor
                    // (GridComponent.h: setColorShift(mImageSelectedColor) on the cursor entry,
                    // mImageColor otherwise). Mirror the system-logo path so a theme that gives the
                    // focused cover a distinct tint (adroit) matches; unfocused dimming stays in `dim`.
                    float ic[4] = {1, 1, 1, 1};
                    if (!(i == sel && primary->getColor("imageSelectedColor", ic)))
                        primary->getColor("imageColor", ic);
                    float drawX = ccx - fw * 0.5f, drawY = ccy - fh * 0.5f;
                    if (clip) {
                        // ES-DE cover honours imageCropPos (default 0.5 = centred): cropPos.y=0 keeps
                        // the top slice of a tall cover, 1 the bottom (coverFitCrop mCropOffset).
                        float cpx = std::min(1.0f, std::max(0.0f, primary->getPair("imageCropPos", 0, 0.5f)));
                        float cpy = std::min(1.0f, std::max(0.0f, primary->getPair("imageCropPos", 1, 0.5f)));
                        drawX += (0.5f - cpx) * (fw - boxW);
                        drawY += (0.5f - cpy) * (fh - boxH);
                        scissorLogicalRect(ccx - boxW * 0.5f, ccy - boxH * 0.5f, boxW, boxH);
                    }
                    drawCell(cover, drawX, drawY, fw, fh,
                             ic[0] * dim, ic[1] * dim, ic[2] * dim, ic[3] * opacity);
                    // Restore the grid's outer band clip (set at the top of the grid branch) rather
                    // than disabling scissoring, or later cells this frame bleed past the grid.
                    if (clip) scissorLogicalRect(x, y, w, dimY);
                } else {
                    // Repaint only while a real decode is in flight (the rom has a box path),
                    // never for an unscraped game (which returns 0 forever) - else the grid would
                    // spin the dirty flag at full rate whenever a visible game has no cover.
                    if (!rom.empty()) {
                        const ScrapeEntry* se = scrapeEntryFor(rom);
                        if (se && !se->box.empty()) anyPending = true;
                    }
                    EsdeSvg d{};
                    if (!defTmpl.empty()) d = esdeArtTex(esdeResolveSystemPath(defTmpl, focSys), (int)cellW, (int)cellH);
                    if (d.tex) drawCell(d.tex, ccx - d.w * 0.5f, ccy - d.h * 0.5f, (float)d.w, (float)d.h, dim, dim, dim, opacity);
                    else {
                        float tbg[4];
                        if (primary->getColor("textBackgroundColor", tbg))
                            drawRoundedRect(bx, by, cellW, cellH, cornerRad("textBackgroundCornerRadius"),
                                            tbg[0] * dim, tbg[1] * dim, tbg[2] * dim, tbg[3] * opacity);
                        // ES-DE GridComponent::mTextColor default is 0x000000FF (BLACK), not white -
                        // a coverless game cell without <textColor> renders black (GridComponent.h).
                        float tc[4] = {0, 0, 0, 1}; primary->getColor("textColor", tc);
                        float trs = clampf(primary->getF("textRelativeScale", 1.0f), 0.2f, 1.0f);
                        float fsc = fontPx(primary, 0.03f) * scale;
                        std::string s = label(i);
                        esdeLetterCase(s, glc);
                        // ES-DE builds the coverless cell's name TextComponent with the grid's scroll
                        // flags, sized to itemSize*textRelativeScale with a centred origin. WITHOUT
                        // textHorizontalScrolling a long name WRAPS to multiple lines centred in the box
                        // (GridComponent.h:388-397). WITH it, the name is a SINGLE line that left-anchors
                        // and marquee-scrolls once it overflows (like the system-name cell); nano has no
                        // per-cell marquee, so it renders the scroll's start frame - left-anchor an
                        // overflowing name at the box left so its beginning shows, clipped to the box -
                        // matching X-Grid's gamelistGrid (a name grid) instead of wrapping mid-word.
                        float tBoxW = cellW * trs, tBoxH = cellH * trs;
                        float col4[4] = {tc[0] * dim, tc[1] * dim, tc[2] * dim, opacity};
                        if (primary->getB("textHorizontalScrolling", false)) {
                            int gFace = faceOf(primary);
                            float glyphH = fsc * FONT_CHAR_H, tw = measureText(s.c_str(), fsc, gFace);
                            float tbLeft = ccx - tBoxW * 0.5f, textX = ccx - tw * 0.5f;
                            bool ov = tw > tBoxW;
                            if (ov) { textX = tbLeft; scissorLogicalRect(tbLeft, by, tBoxW, cellH); }
                            drawText(s.c_str(), textX, ccy - glyphH * 0.5f, fsc,
                                     col4[0], col4[1], col4[2], col4[3], gFace);
                            if (ov) scissorLogicalRect(x, y, w, dimY);
                        } else {
                            drawWrapped(s, ccx - tBoxW * 0.5f, ccy - tBoxH * 0.5f, tBoxW, tBoxH, fsc,
                                        primary->getF("lineSpacing", 1.5f), "center", col4,
                                        std::string(), faceOf(primary), "center");
                        }
                    }
                }
                if (!selBottom && !selMiddle) emitSel();   // top-layer selector over the cover cell
            };
            // Non-cursor cells first, then last-cursor and the cursor on top (overlap-correct).
            for (int i = startPos; i < endPos; i++)
                if (i != sel && i != mEsdeGridLastCursor) drawCell(i);
            if (mEsdeGridLastCursor >= startPos && mEsdeGridLastCursor < endPos && mEsdeGridLastCursor != sel)
                drawCell(mEsdeGridLastCursor);
            if (sel >= startPos && sel < endPos) drawCell(sel);
            glDisable(GL_SCISSOR_TEST);
            if (anyPending) { mEsdeWantsFastFrame = true; mDisplayDirty = true; }   // repaint as covers finish decoding

        } else {
            // textlist: a vertical list, selection row highlighted with a selector bar.
            float sc = fontPx(primary, 0.045f);
            // ES-DE textlist entry pitch = fontSize * lineSpacing (TextListComponent), default 1.5.
            // Art Book Next's 4:3 gamelist sets 1.944, so honor the theme value (clamped) rather
            // than a hardcoded 1.5, which was packing the rows too tightly.
            float lineSp = primary->getF("lineSpacing", 1.5f);
            lineSp = lineSp < 0.5f ? 0.5f : (lineSp > 3.0f ? 3.0f : lineSp);
            float rowH = fontPx(primary, 0.045f) * FONT_CHAR_H * lineSp;
            if (rowH < 1) rowH = mHeight * 0.06f;
            // ES-DE TextListComponent fits ceil-ish rows: floor((size.y + lineSpacingHeight/2) /
            // entrySize) where lineSpacingHeight = entrySize - fontSize. This can fit one more row
            // than a plain floor(h/entrySize) when the remainder is at least half the extra spacing.
            float lineSpacingH = rowH - fontPx(primary, 0.045f) * FONT_CHAR_H;
            int rows = h > 0 ? (int)((h + lineSpacingH * 0.5f) / rowH) : 8; if (rows < 1) rows = 8;
            int first = sel - rows / 2; if (first < 0) first = 0;
            if (first > count - rows) first = count - rows > 0 ? count - rows : 0;
            float primC[4]; colorOf(primary, "primaryColor", 0.85f, 0.85f, 0.85f, 1, primC);
            // ES-DE: selectedColor falls back to primaryColor when the theme omits it
            // (TextListComponent), not to white.
            float selC[4];
            if (primary->has("selectedColor")) colorOf(primary, "selectedColor", 1, 1, 1, 1, selC);
            else { selC[0] = primC[0]; selC[1] = primC[1]; selC[2] = primC[2]; selC[3] = primC[3]; }
            // ES-DE's selector defaults to 0x333333FF and is drawn for the selected row;
            // a theme that wants none sets selectorColor=00000000 (as art-book-next does,
            // preferring its selectedBackgroundColor plate). The sum>0 guard below keeps a
            // transparent selector from drawing anything.
            float selBar[4] = {0.2f, 0.2f, 0.2f, 1.0f}; primary->getColor("selectorColor", selBar);
            float selBg[4] = {0, 0, 0, 0}; bool hasSelBg = primary->getColor("selectedBackgroundColor", selBg);
            // ES-DE selectedBackgroundMargins default to {0,0} (no invented pad) so the plate hugs
            // the text; a positive x margin would push the plate left of the row and its rounded
            // corner would be clipped by the row scissor.
            float selMx = primary->getPair("selectedBackgroundMargins", 0, 0.0f) * mWidth;
            float selMy = primary->getPair("selectedBackgroundMargins", 1, 0.0f) * mWidth;
            float selRad = primary->getF("selectedBackgroundCornerRadius", 0.0f) * mWidth;
            // ES-DE draws the plate at the selector height (mSelectorHeight = font pixel size * 1.5
            // by default, or the theme's selectorHeight as a fraction of screen height), NOT the
            // tight glyph height - a short plate makes a large cornerRadius look cut off. nano's
            // font pixel size is the draw scale times FONT_CHAR_H, so scale that by 1.5.
            float selPlateH = primary->has("selectorHeight")
                ? std::min(1.0f, std::max(0.0f, primary->getF("selectorHeight", 0.0f))) * mHeight
                : fontPx(primary, 0.045f) * FONT_CHAR_H * 1.5f;
            float glyphH = sc * FONT_CHAR_H;
            // ES-DE centers each entry's text in a box of height fontSize*1.5 anchored at the ROW
            // TOP (TextListComponent::addEntry sizes the entry TextComponent to {0, fontSize*1.5}
            // with ALIGN_CENTER), and draws the selector/plate at the row top + selectorVerticalOffset
            // with height mSelectorHeight - NOT centered in the full row pitch. With the default
            // lineSpacing 1.5 the box equals the pitch so this is identical to centering in the row;
            // a larger lineSpacing (e.g. art-book-next's 1.944) then keeps the text and highlight in
            // the top fontSize*1.5 of the slot instead of sinking to the middle.
            float entryBoxH = fontPx(primary, 0.045f) * FONT_CHAR_H * 1.5f;
            float selYOff = primary->getF("selectorVerticalOffset", 0.0f) * mHeight;
            std::string alignS = primary->getS("horizontalAlignment", std::string());
            const char* align = alignS.c_str();
            // ES-DE indents textlist rows by mHorizontalMargin (the theme's horizontalMargin as a
            // fraction of screen width, default 0), NOT a hardcoded fraction of the element width.
            // Using the theme value keeps the rows and the selection plate aligned with the control.
            float padX = primary->getF("horizontalMargin", 0.0f) * mWidth;
            std::string tlCase = primary->getS("letterCase", std::string());
            // ES-DE clips textlist rows to the element bounds so a long game name can't bleed past
            // the list; keep every row inside (x,y,w,h).
            if (w > 1 && h > 1) scissorLogicalRect(x, y, w, h);
            for (int r = 0; r < rows && first + r < count; r++) {
                int i = first + r;
                float ry = y + r * rowH;
                bool cur = (i == sel);
                std::string lbl = label(i);
                esdeLetterCase(lbl, tlCase);
                if (cur && (selBar[0] + selBar[1] + selBar[2] + selBar[3]) > 0.0f) {
                    // ES-DE TextListComponent draws the selector as a rect of selectorWidth at
                    // selectorHorizontalOffset/selectorVerticalOffset (both screen-relative). The width
                    // defaults to the full element width (a full-row highlight), but a theme can set a
                    // thin value - Linear uses 0.003 for a ~3px left cursor bar. Without honouring it
                    // nano painted the whole row in the selector colour, hiding the selected game name
                    // (white text on a white bar).
                    float selW = primary->has("selectorWidth")
                        ? std::min(1.0f, std::max(0.0f, primary->getF("selectorWidth", 0.0f))) * mWidth
                        : w;
                    float selXOff = primary->getF("selectorHorizontalOffset", 0.0f) * mWidth;
                    // ES-DE's selector rect is mSelectorHeight tall (fontSize*1.5 by default, or the
                    // theme's selectorHeight), anchored at the row top + selectorVerticalOffset, not
                    // the full row pitch.
                    // ES-DE draws the selector with a selectorColor->selectorColorEnd gradient
                    // (selectorGradientType horizontal/vertical); a flat colour is end==start. Route
                    // through the gradient path only when a distinct end is set, else keep the flat rect.
                    float selBarEnd[4]; bool selGrad = primary->getColor("selectorColorEnd", selBarEnd);
                    bool selHoriz = primary->getS("selectorGradientType", std::string("horizontal")) != "vertical";
                    // A theme can supply a graphical selector cursor via selectorImagePath (an svg
                    // capsule/frame - aura, canvas, cathode, epic-noir). ES-DE stretches it to
                    // selectorWidth x selectorHeight (mSelectorImage setResize) at the selector offsets
                    // and tints it by selectorColor (setColorShift, TextListComponent.h:735-741),
                    // drawing it INSTEAD of the flat/gradient bar. Match that: draw the image stretched
                    // to the selector box, tinted by selectorColor.
                    const std::string& selImgPath = primary->getPath("selectorImagePath");
                    EsdeSvg selImg = selImgPath.empty() ? EsdeSvg{}
                                                        : esdeArtTex(selImgPath, (int)selW, (int)selPlateH);
                    if (selImg.tex)
                        drawIconTex(selImg.tex, x + selXOff, ry + selYOff, selW, selPlateH,
                                    selBar[0], selBar[1], selBar[2], selBar[3]);
                    else if (selGrad && (selBarEnd[0] != selBar[0] || selBarEnd[1] != selBar[1] ||
                                    selBarEnd[2] != selBar[2] || selBarEnd[3] != selBar[3]))
                        drawIconTexFx(esdeWhiteTex(), x + selXOff, ry + selYOff, selW, selPlateH,
                                      selBar[0], selBar[1], selBar[2], selBar[3],
                                      0.0f, 1.0f, 0.0f, 0.0f, selBarEnd, selHoriz);
                    else
                        drawQuad(x + selXOff, ry + selYOff, selW, selPlateH,
                                 selBar[0], selBar[1], selBar[2], selBar[3]);
                }
                // ES-DE selectedBackgroundColor: a rounded plate sized to the selected text plus
                // margins (ABN's subtle row highlight), drawn behind the label.
                if (cur && hasSelBg && selBg[3] > 0.0f) {
                    // ES-DE: width = text width + both margins; positioned at the text left minus
                    // the x margin; height = selector height; corners rounded by cornerRadius. The
                    // plate legitimately extends left of the text by the x margin (e.g. analogue's
                    // 0.0375), which is left of the row's text-clip scissor - ES-DE never scissors
                    // the selector (it truncates the text instead), so drop the scissor around the
                    // plate or its rounded left corner gets sliced into a straight edge.
                    // Measure with the SAME font the label is drawn in (faceOf(primary)); measuring
                    // with the default face sized the plate to a wider glyph run than the themed text,
                    // so the plate overhung the text (Analogue's system-name pill was too wide).
                    float tw = measureText(lbl.c_str(), sc, faceOf(primary));
                    // ES-DE anchors the plate to the selected entry's TEXT position, so it must follow
                    // the textlist horizontalAlignment - a center/right list positions the label away
                    // from the row's left edge and the plate has to move with it. Without this a
                    // center-aligned list (SuperStation One Menu) drew the plate at the left while the
                    // label centred, so the highlight sat beside the text instead of behind it.
                    float aw = w - padX * 2.0f;
                    float textLeft = x + padX;
                    if (alignS == "center")     textLeft += (aw - tw) * 0.5f;
                    else if (alignS == "right") textLeft += (aw - tw);
                    bool hadScissor = (w > 1 && h > 1);
                    if (hadScissor) glDisable(GL_SCISSOR_TEST);
                    drawRoundedRect(textLeft - selMx, ry + selYOff,
                                    tw + selMx + selMy, selPlateH, selRad,
                                    selBg[0], selBg[1], selBg[2], selBg[3]);
                    if (hadScissor) glEnable(GL_SCISSOR_TEST);
                }
                float* col = cur ? selC : primC;
                // Center the label in the fontSize*1.5 entry box at the row top (matching ES-DE's
                // ALIGN_CENTER entry TextComponent). Em-centering here rather than the exact S-bearing
                // baseline: nano's glyph metrics do not track ES-DE's FreeType metrics closely enough
                // for the finer form to be safe, and em-centering already matches the control for the
                // default lineSpacing 1.5 while removing the row sink for larger lineSpacing.
                float availW = w - padX * 2;
                float ty = ry + (entryBoxH - glyphH) * 0.5f;
                float tw = cur ? measureText(lbl.c_str(), sc, faceOf(primary)) : 0.0f;
                // ES-DE TextListComponent marquees the SELECTED entry when its text overflows the
                // element width (mHorizontalScrolling defaults on): after a delay it scrolls left at a
                // font-relative speed with a trailing gap, a second copy making the wrap seamless
                // (TextComponent::update). Speed is the summed advance of the 26 capitals * 0.247;
                // delay/speed/gap follow the theme's textHorizontalScroll* (defaults 3s / 1.0 / 1.5).
                if (cur && w > 1.0f && h > 1.0f && primary->getB("textHorizontalScrolling", true) &&
                    tw > availW + 1.0f) {
                    if (lbl != mEsdeMarqueeLbl) {
                        mEsdeMarqueeLbl = lbl; mEsdeMarqueeStart = (int64_t)uptimeMillis();
                    }
                    float sizeRef = measureText("ABCDEFGHIJKLMNOPQRSTUVWXYZ", sc, faceOf(primary));
                    float spd = sizeRef * 0.247f *
                        std::min(10.0f, std::max(0.1f, primary->getF("textHorizontalScrollSpeed", 1.0f)));
                    float gap = std::min(5.0f, std::max(0.1f, primary->getF("textHorizontalScrollGap", 1.5f)));
                    float delay = std::min(10.0f, std::max(0.0f,
                        primary->getF("textHorizontalScrollDelay", 3.0f))) * 1000.0f;
                    float retLen = spd * gap;
                    float scT = spd > 0.0f ? tw * 1000.0f / spd : 0.0f;
                    float retT = spd > 0.0f ? retLen * 1000.0f / spd : 0.0f;
                    float maxT = delay + scT + retT;
                    float t = maxT > 0.0f
                        ? std::fmod((float)((int64_t)uptimeMillis() - mEsdeMarqueeStart), maxT) : 0.0f;
                    float st2 = scT + retT, off = 0.0f;
                    if (t >= delay && st2 > 0.0f && t < delay + st2)
                        off = (tw + retLen) * (t - delay) / st2;
                    scissorLogicalRect(x + padX, y, availW, h);   // clip to the entry text box
                    drawText(lbl.c_str(), x + padX - off, ty, sc,
                             col[0], col[1], col[2], col[3], faceOf(primary));
                    drawText(lbl.c_str(), x + padX - off + tw + retLen, ty, sc,
                             col[0], col[1], col[2], col[3], faceOf(primary));
                    scissorLogicalRect(x, y, w, h);   // restore the row scissor for later entries
                    mEsdeWantsFastFrame = true; mDisplayDirty = true;
                } else {
                    drawAligned(lbl, x + padX, ty, availW, sc, align, col, faceOf(primary));
                }
            }
            if (w > 1 && h > 1) glDisable(GL_SCISSOR_TEST);
        }
    };

    // ES-DE only draws a <clock> when the DisplayClock setting is on, and it defaults OFF,
    // so the bundled themes (which all define a clock) show none until the user opts in.
    // Mirror that with a prop defaulting to false so nano matches the control out of the box.
    const bool esdeShowClock = property_get_bool("persist.gammaos.nano.esde.clock", false);

    // ES-DE's SystemView/GamelistView keep exactly ONE primary navigation element per view (the
    // first textlist/carousel/grid by name, skipping the rest), so a variant that layers a
    // carousel/grid over the base textlist (e.g. Analogue OS Menu's gamelist-carousel) draws only
    // that one, not the base list underneath. esdeChosenPrimary picks the same element.
    const nanoesde::Element* chosenPrimary = esdeChosenPrimary(view);

    // ES-DE <gameselector>: an image/video can bind its media to a game the selector picks from the
    // FOCUSED system (random / lastplayed / mostplayed) rather than the navigated game. In the system
    // view there is no curRom, so a gameselector-bound background (atari's full-screen screenshot)
    // rendered nothing. Collect the view's gameselector definitions (name -> selection) and resolve a
    // stable rom per focused system. The pick is stable (hashed) to avoid per-frame flicker; ES-DE
    // re-rolls random on each system visit but a valid game's media is what matters here, and
    // lastplayed/mostplayed are not tracked per rom yet so they fall back to the same stable pick.
    std::map<std::string, std::string> esdeGsSel;
    for (const nanoesde::Element* ge : view->drawOrder)
        if (ge->type == "gameselector") esdeGsSel[ge->name] = ge->getS("selection", std::string("random"));
    const int esdeFocSys = (!mEsdeSysList.empty() && mEsdeSysSel < (int)mEsdeSysList.size())
                           ? mEsdeSysList[mEsdeSysSel] : -1;
    // Pull the focused system's ES-DE gamelist.xml metadata (once) so the metadata elements below
    // read the same description/rating/date/etc as real ES-DE rather than nano's own scrape store.
    if (esdeFocSys >= 0) esdeEnsureGamelistLoaded(esdeFocSys);
    auto esdeGsRom = [&](const std::string& selName, const std::string& imageType) -> std::string {
        if (esdeGsSel.empty() || esdeFocSys < 0 || esdeFocSys >= (int)mXmbSystems.size()) return std::string();
        const auto& roms = mXmbSystems[esdeFocSys].roms;
        if (roms.empty()) return std::string();
        // Stable starting pick (hashed so it does not flicker per frame). Prefer a game that actually
        // has the requested media so a background screenshot renders even on a sparsely-scraped
        // library: scan from the pick, returning the first rom whose media exists; else the raw pick.
        size_t h = std::hash<std::string>{}(mXmbSystems[esdeFocSys].shortname + ":" + selName);
        size_t startIdx = h % roms.size();
        for (size_t i = 0; i < roms.size(); ++i) {
            const std::string& r = roms[(startIdx + i) % roms.size()];
            if (!esdeGameMediaPath(r, imageType).empty()) return r;
        }
        return roms[startIdx];
    };

    // --- draw every element in zIndex order ---
    for (const nanoesde::Element* e : view->drawOrder) {
        if (!e->getB("visible", true)) continue;
        if (e->type == "clock" && !esdeShowClock) continue;   // DisplayClock default off
        // scope=none hides an element in this view (ABN hides the clock in gamelist);
        // scope=menu elements only show over a menu, which this view path never draws.
        const std::string& scope = e->getS("scope", std::string());
        if (scope == "none" || scope == "menu") continue;
        // In-game overlay: skip a screen-covering opaque background so the running app shows through.
        if (esdeInGameScrim && esdeCoversScreen(e)) continue;
        const std::string& t = e->type;

        if (t == "image") {
            float x, y, w, h; rectOf(e, x, y, w, h);
            float c[4]; colorOf(e, "color", 1, 1, 1, 1, c);
            // ES-DE rotates the element `rotation` degrees clockwise on screen about rotationOrigin
            // (default 0.5 0.5 = centre; GuiComponent::getTransform). drawIconTex rotates about the
            // quad centre, matching the default origin; themes use it for tilted frames/gradients
            // (Canvas' background gradient, Aura). degrees -> radians.
            float rotRad = e->getF("rotation", 0.0f) * 0.017453293f;
            // ES-DE applies the element's brightness (default 0 = none) and saturation (default 1 =
            // full colour) to the sampled texture (core.glsl). Route the image draws through the FX
            // shader only when a non-default value is set - a darkened/greyscale backdrop or vignette
            // (aura, epic-noir, canvas); plain covers keep the shared drawIconTex path untouched.
            // ES-DE cornerRadius rounds the drawn image quad (ImageComponent.cpp:608-610, clamp
            // 0..0.5 * screenWidth). Folded into the same FX shader as brightness/saturation.
            // ES-DE clamps these at parse (GuiComponent): saturation 0..1, brightness -2..2.
            float imgSat = std::min(1.0f, std::max(0.0f, e->getF("saturation", 1.0f)));
            float imgBright = std::min(2.0f, std::max(-2.0f, e->getF("brightness", 0.0f)));
            float imgRad = std::min(0.5f, std::max(0.0f, e->getF("cornerRadius", 0.0f))) * mWidth;
            // ES-DE colorEnd + gradientType: the tint is a colour->colorEnd gradient across the quad
            // (horizontal left->right, or vertical top->bottom). The element's `color` is the start.
            float imgEnd[4]; bool imgGrad = e->getColor("colorEnd", imgEnd);
            // ES-DE folds the element opacity into the alpha of BOTH gradient stops (ImageComponent::
            // updateColors); colorOf already did it for the start colour, so match it for colorEnd.
            if (imgGrad) imgEnd[3] *= e->getF("opacity", 1.0f);
            bool imgGradHoriz = e->getS("gradientType", std::string("horizontal")) != "vertical";
            bool imgFx = (imgSat != 1.0f || imgBright != 0.0f || imgRad > 0.0f || imgGrad);
            // ES-DE flipHorizontal / flipVertical mirror the image (ImageComponent setFlipX/Y); e.g.
            // Linear mirrors one gradient bar to build the opposite-edge one. Default off (no-op).
            bool imgFlipH = e->getB("flipHorizontal", false);
            bool imgFlipV = e->getB("flipVertical", false);
            const GLint esdeMag = esdeImageMagFilter(e);
            auto drawImg = [&](GLuint tx, float dx, float dy, float dw, float dh,
                               float cr, float cg, float cb, float ca, float rr) {
                glBindTexture(GL_TEXTURE_2D, tx);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, esdeMag);
                if (imgFx) drawIconTexFx(tx, dx, dy, dw, dh, cr, cg, cb, ca, rr, imgSat, imgBright, imgRad,
                                         imgGrad ? imgEnd : nullptr, imgGradHoriz, imgFlipH, imgFlipV);
                else       drawIconTex(tx, dx, dy, dw, dh, cr, cg, cb, ca, rr, imgFlipV,
                                       0.0f, 0.0f, 1.0f, 1.0f, imgFlipH);
            };
            // A tiled spacer image tinted with a colour is ES-DE's idiom for a solid colour
            // panel/band (ABN backgrounds, slate bands): tiling a 1px pixel just fills the box
            // with the colour, so draw that directly rather than contain-fitting the spacer into
            // a centred square. Only for coloured, sized boxes; real tiled art is rare here.
            if (e->getB("tile", false) && e->has("color") && w > 1 && h > 1) {
                // A colorEnd turns the solid band into a gradient: draw a white 1px through the FX
                // shader so the per-vertex colour->colorEnd tint fills the box.
                if (imgGrad) drawImg(esdeWhiteTex(), x, y, w, h, c[0], c[1], c[2], c[3], 0.0f);
                else         drawQuad(x, y, w, h, c[0], c[1], c[2], c[3]);
                continue;
            }
            const int focSys = (!mEsdeSysList.empty() && mEsdeSysSel < (int)mEsdeSysList.size())
                                   ? mEsdeSysList[mEsdeSysSel] : -1;
            // Per-system art/logo. Draw the element resolved for one system, shifted by xoff.
            // ES-DE size with one axis 0 means "derive it from the image aspect ratio" (e.g.
            // ABN's releasedate-icon <size>0 0.0625</size>): expand the zero axis so the aspect-fit
            // fills the fixed axis, then anchor by origin against the fitted size.
            std::string rawPath = e->getPath("path");
            // ES-DE cropSize: cover-fit the image to a box of (cropSize * screen), scaling to fill
            // and cropping the overflow while keeping aspect (ImageComponent resize() mTargetIsCrop
            // + coverFitCrop). Used for full-screen wallpapers that set ONLY cropSize and no <size>
            // (Canvas' background-art .webp), which nano otherwise sized to a zero box and skipped.
            if (e->has("cropSize") && !rawPath.empty()) {
                float csx = e->getPair("cropSize", 0, 1.0f), csy = e->getPair("cropSize", 1, 1.0f);
                float boxW = csx * mWidth, boxH = csy * mHeight;
                std::string p = (rawPath.find("${system.") != std::string::npos && focSys >= 0)
                                    ? esdeResolveSystemPath(rawPath, focSys) : rawPath;
                if (boxW >= 1.0f && boxH >= 1.0f && !p.empty()) {
                    EsdeSvg a = esdeArtTex(p, (int)boxW, (int)boxH);   // loads tex + caches native dims
                    if (a.tex) {
                        int iw = a.w, ih = a.h;                        // fall back to fitted aspect
                        auto dit = mEsdePngDims.find(p);
                        if (dit != mEsdePngDims.end() && dit->second.first > 0 && dit->second.second > 0)
                            { iw = dit->second.first; ih = dit->second.second; }
                        // ES-DE tile=true REPEATS the image across the box at its native size (or
                        // tileSize) instead of scaling it - MinUI's colour-scheme backgrounds are a tiny
                        // gradient PNG (e.g. 4x4) tiled into a fine wash; cover-fitting it stretched the
                        // gradient into a couple of big bands. Tile via GL_REPEAT (POT source only, since
                        // GLES2 forbids NPOT repeat), the UV spanning box/tileSize; a non-POT tile falls
                        // through to the cover-fit below. A full-box cropSize=1 1 tile fills the screen.
                        auto isPow2 = [](int v) { return v > 0 && (v & (v - 1)) == 0; };
                        if (e->getB("tile", false) && isPow2(iw) && isPow2(ih)) {
                            float tsx = e->has("tileSize") ? e->getPair("tileSize", 0, 0.0f) * mWidth : (float)iw;
                            float tsy = e->has("tileSize") ? e->getPair("tileSize", 1, 0.0f) * mHeight : (float)ih;
                            if (tsx < 1.0f) tsx = (float)iw;
                            if (tsy < 1.0f) tsy = (float)ih;
                            float bx0 = e->getPair("pos", 0, 0.0f) * mWidth -
                                        e->getPair("origin", 0, 0.0f) * boxW;
                            float by0 = e->getPair("pos", 1, 0.0f) * mHeight -
                                        e->getPair("origin", 1, 0.0f) * boxH;
                            glBindTexture(GL_TEXTURE_2D, a.tex);
                            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
                            drawIconTex(a.tex, bx0, by0, boxW, boxH, c[0], c[1], c[2], c[3], rotRad,
                                        false, 0.0f, 0.0f, boxW / tsx, boxH / tsy);
                            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                            continue;
                        }
                        float cf = std::max(boxW / (float)iw, boxH / (float)ih);   // cover factor
                        float sw = iw * cf, sh = ih * cf;
                        float ox = e->getPair("origin", 0, 0.0f), oy = e->getPair("origin", 1, 0.0f);
                        float bx = e->getPair("pos", 0, 0.0f) * mWidth - ox * boxW;
                        float by = e->getPair("pos", 1, 0.0f) * mHeight - oy * boxH;
                        // cropPos: fraction of the overflow taken from the top-left (default 0.5 = centred).
                        float cpx = e->getPair("cropPos", 0, 0.5f), cpy = e->getPair("cropPos", 1, 0.5f);
                        // Clip the cover overflow to the box only when the box is inset from the
                        // screen: a full-screen box (a wallpaper) is already clipped by the viewport,
                        // and setting a full-screen scissor here suppressed the draw on this path.
                        bool overflow = (sw > boxW + 1.0f) || (sh > boxH + 1.0f);
                        bool inset = bx > 0.5f || by > 0.5f ||
                                     bx + boxW < mWidth - 0.5f || by + boxH < mHeight - 0.5f;
                        bool needClip = overflow && inset;
                        if (needClip) scissorLogicalRect(bx, by, boxW, boxH);
                        drawImg(a.tex, bx - (sw - boxW) * cpx, by - (sh - boxH) * cpy, sw, sh,
                                c[0], c[1], c[2], c[3], rotRad);
                        if (needClip) glDisable(GL_SCISSOR_TEST);
                        continue;
                    }
                }
            }
            auto drawSysImage = [&](int sysIdx, float xoff) -> bool {
                std::string p = (sysIdx >= 0) ? esdeResolveSystemPath(rawPath, sysIdx) : rawPath;
                if (p.empty()) return false;
                float szx = e->getPair("size", 0, -1), szy = e->getPair("size", 1, -1);
                // ES-DE ImageComponent: an explicit <size> (both axes) STRETCHES the image to fill
                // the box (slate's vertical bands, frames); <maxSize> or a single axis contain-fits
                // / derives from aspect. nano used to contain-fit everything, collapsing a spacer
                // like slate's band.png into a tiny centred square.
                bool stretch = e->has("size") && szx > 0.0f && szy > 0.0f;
                int rw = (int)w, rh = (int)h;
                if (szx == 0.0f && szy > 0.0f) rw = (int)(h * 8.0f);
                else if (szy == 0.0f && szx > 0.0f) rh = (int)(w * 8.0f);
                EsdeSvg a = esdeArtTex(p, rw, rh);
                if (!a.tex) return false;
                if (stretch) {
                    drawImg(a.tex, x + xoff, y, w, h, c[0], c[1], c[2], c[3], rotRad);
                    return true;
                }
                // ES-DE anchors the contain-fitted image by the element origin against the FITTED
                // size (ImageComponent sets mSize to the fitted size, getTransform translates by
                // -origin*mSize), not by centering it inside the maxSize box. Same rule for the
                // aspect-derived and the maxSize cases.
                float ox = e->getPair("origin", 0, 0), oy = e->getPair("origin", 1, 0);
                float dx = e->getPair("pos", 0, 0) * mWidth - ox * a.w;
                float dy = e->getPair("pos", 1, 0) * mHeight - oy * a.h;
                drawImg(a.tex, dx + xoff, dy, (float)a.w, (float)a.h, c[0], c[1], c[2], c[3], rotRad);
                return true;
            };
            // During a system change ES-DE slides the per-system extras in step with the carousel:
            // render the straddling systems translated by (i - camOffset)*width so the logo slides
            // in and out instead of snapping. Only for a per-system path in the system view.
            bool perSystem = !gamelist && rawPath.find("${system.") != std::string::npos;
            if (perSystem && mEsdeCamAnimDur > 0.0f && (int)mEsdeSysList.size() > 1) {
                float camOffset = mEsdeCamOffset; int n = (int)mEsdeSysList.size();
                for (int i = (int)floorf(camOffset); i <= (int)ceilf(camOffset); i++) {
                    int idx = ((i % n) + n) % n;
                    drawSysImage(mEsdeSysList[idx], (float)(i - camOffset) * mWidth);
                }
                continue;
            }
            if (!rawPath.empty() && drawSysImage(focSys, 0.0f)) continue;
            // No static art: bind the scraped cover (gamelist imageType), else the default.
            GLuint tex = 0; float coverAr = 1.0f;
            if (e->has("imageType")) {
                // The navigated game, or a gameselector-picked game (system-view background art).
                std::string iRom = curRom;
                const std::string imgType = e->getS("imageType", std::string("cover"));
                if (iRom.empty()) { const std::string& gs = e->getS("gameselector", std::string());
                                    if (!gs.empty()) iRom = esdeGsRom(gs, imgType); }
                if (!iRom.empty())
                    tex = esdeGameMediaTex(iRom, imgType, &coverAr);
            }
            if (!tex) {
                // ES-DE loads <default> through the SAME setImage/resize path as the resolved media
                // (ImageComponent::setDefaultImage), so the fallback honors the element's size/maxSize/
                // cropSize box: stretched for a both-axis <size>, cover-cropped for <cropSize>, contain-
                // fit for <maxSize>/single-axis. Bind it as the texture with its native aspect and fall
                // through to the shared fit block below. (rectOf gives a 0-box for a cropSize-only
                // element, so drawing the default at esdeArtTex's contain-fit size skipped it entirely -
                // a cropSize <default> background then vanished instead of cover-filling; a both-axis
                // size default contain-fit instead of stretching. maxSize stays byte-identical.)
                std::string def = e->getPath("default");
                if (focSys >= 0) def = esdeResolveSystemPath(def, focSys);
                if (!def.empty()) {
                    float lbW = w, lbH = h;   // rasterise target; take the cropSize box when rectOf gave 0
                    if (lbW < 1.0f || lbH < 1.0f) {
                        lbW = e->getPair("cropSize", 0, 1.0f) * mWidth;
                        lbH = e->getPair("cropSize", 1, 1.0f) * mHeight;
                    }
                    if (lbW < 1.0f) lbW = (float)mWidth;
                    if (lbH < 1.0f) lbH = (float)mHeight;
                    EsdeSvg a = esdeArtTex(def, (int)lbW, (int)lbH);
                    if (a.tex) {
                        tex = a.tex;
                        int diw = a.w, dih = a.h;   // esdeArtTex returns the contain-fit size; the fit
                        auto dit = mEsdePngDims.find(def);   // block needs the NATIVE aspect
                        if (dit != mEsdePngDims.end() && dit->second.first > 0 && dit->second.second > 0)
                            { diw = dit->second.first; dih = dit->second.second; }
                        coverAr = dih > 0 ? (float)diw / (float)dih : 1.0f;
                    }
                }
            }
            if (tex) {
                float ar = coverAr > 0.0f ? coverAr : 1.0f;
                if (e->has("cropSize")) {
                    // ES-DE cover-crops a scraped cover carrying <cropSize> (setCroppedSize ->
                    // coverFitCrop): scale to FILL the cropSize box, crop the overflow honoring
                    // cropPos. rectOf gives w=h=0 for a cropSize-only element, so the box must be
                    // taken from cropSize here (a full-screen gamelist background is cropSize 1 1);
                    // without this the scraped background was contain-fit into a 0x0 box and vanished.
                    // Mirrors the static-path cropSize sub-path above and the video-still cover path.
                    float boxW = e->getPair("cropSize", 0, 1.0f) * mWidth;
                    float boxH = e->getPair("cropSize", 1, 1.0f) * mHeight;
                    float fw, fh;
                    if (boxW / ar >= boxH) { fw = boxW; fh = boxW / ar; }
                    else                   { fh = boxH; fw = boxH * ar; }
                    float ox = e->getPair("origin", 0, 0), oy = e->getPair("origin", 1, 0);
                    float bx = e->getPair("pos", 0, 0) * mWidth - ox * boxW;
                    float by = e->getPair("pos", 1, 0) * mHeight - oy * boxH;
                    float cpx = e->getPair("cropPos", 0, 0.5f), cpy = e->getPair("cropPos", 1, 0.5f);
                    bool overflow = (fw > boxW + 1.0f) || (fh > boxH + 1.0f);
                    bool inset = bx > 0.5f || by > 0.5f ||
                                 bx + boxW < mWidth - 0.5f || by + boxH < mHeight - 0.5f;
                    bool needClip = overflow && inset;
                    if (needClip) scissorLogicalRect(bx, by, boxW, boxH);
                    drawImg(tex, bx - (fw - boxW) * cpx, by - (fh - boxH) * cpy, fw, fh,
                            c[0], c[1], c[2], c[3], rotRad);
                    if (needClip) glDisable(GL_SCISSOR_TEST);
                } else if (e->has("size") && e->getPair("size", 0, -1.0f) > 0.0f &&
                                             e->getPair("size", 1, -1.0f) > 0.0f) {
                    // ES-DE both-axes <size> = setResize = STRETCH the scraped cover to fill the box
                    // (aspect ignored), matching the static-path drawSysImage stretch. A <maxSize> or a
                    // single-axis <size> stays contain-fit below. Fit precedence cropSize -> size ->
                    // maxSize mirrors ImageComponent::applyTheme.
                    float ox = e->getPair("origin", 0, 0), oy = e->getPair("origin", 1, 0);
                    float dx = e->getPair("pos", 0, 0) * mWidth - ox * w;
                    float dy = e->getPair("pos", 1, 0) * mHeight - oy * h;
                    drawImg(tex, dx, dy, w, h, c[0], c[1], c[2], c[3], rotRad);
                } else {
                    // Contain-fit the cover to its own aspect ratio inside the element box, as
                    // ES-DE does for a maxSize image and as nano's own video path already does,
                    // rather than stretching it to fill the box.
                    float fw = w, fh = w / ar;
                    if (fh > h) { fh = h; fw = h * ar; }
                    float ox = e->getPair("origin", 0, 0), oy = e->getPair("origin", 1, 0);
                    float dx = e->getPair("pos", 0, 0) * mWidth - ox * fw;
                    float dy = e->getPair("pos", 1, 0) * mHeight - oy * fh;
                    drawImg(tex, dx, dy, fw, fh, c[0], c[1], c[2], c[3], rotRad);
                }
            } else if (w > 1 && h > 1 && e->has("color"))
                drawQuad(x, y, w, h, c[0], c[1], c[2], c[3]);   // solid band (no texture)

        } else if (t == "animation") {
            // ES-DE animation element: an animated GIF (Lottie .json is unsupported). Decode every
            // frame once (esdeAnimGet), pick the current one by wall clock, and draw it like an
            // <image> minus the crop mode ES-DE does not offer here: an explicit <size> (both axes)
            // stretches; a single 0 axis or <maxSize> derives/contain-fits from the frame aspect;
            // anchored by origin, tinted by color/colorEnd, filtered by brightness/saturation/
            // cornerRadius, faded by opacity, rotated by rotation. mDisplayDirty advances the loop.
            std::string rawPath = e->getPath("path");
            const int focSys = (!mEsdeSysList.empty() && mEsdeSysSel < (int)mEsdeSysList.size())
                                   ? mEsdeSysList[mEsdeSysSel] : -1;
            if (rawPath.find("${system.") != std::string::npos && focSys >= 0)
                rawPath = esdeResolveSystemPath(rawPath, focSys);
            auto isGif = [](const std::string& s) {
                if (s.size() < 4) return false;
                std::string e4 = s.substr(s.size() - 4);
                for (auto& ch : e4) ch = (char)tolower((unsigned char)ch);
                return e4 == ".gif";
            };
            const EsdeAnim* an = (!rawPath.empty() && isGif(rawPath) &&
                                  access(rawPath.c_str(), R_OK) == 0) ? esdeAnimGet(rawPath) : nullptr;
            if (an && !an->frames.empty()) {
                int64_t ph = an->totalMs > 0 ? ((int64_t)uptimeMillis() % an->totalMs) : 0;
                int fidx = 0, acc = 0;
                for (; fidx + 1 < (int)an->frames.size(); fidx++) {
                    acc += an->delaysMs[fidx];
                    if (ph < acc) break;
                }
                GLuint tex = an->frames[fidx];
                float op = e->getF("opacity", 1.0f);
                float rot = e->getF("rotation", 0.0f) * 0.017453293f;
                float ar = an->nh > 0 ? (float)an->nw / (float)an->nh : 1.0f;
                float ox = e->getPair("origin", 0, 0.0f), oy = e->getPair("origin", 1, 0.0f);
                // ES-DE AnimationComponent has no cover/crop mode (ThemeData rejects cropSize on
                // <animation>): a both-axis <size> stretches, a single-0 <size> or <maxSize>
                // contain-fits from the frame aspect, anchored by origin (GIFAnimComponent/
                // LottieAnimComponent resize()).
                float szx = e->getPair("size", 0, 0.0f), szy = e->getPair("size", 1, 0.0f);
                float mxx = e->getPair("maxSize", 0, 0.0f), mxy = e->getPair("maxSize", 1, 0.0f);
                float fw, fh;
                if (e->has("size") && szx > 0.0f && szy > 0.0f) {          // stretch to the box
                    fw = szx * mWidth; fh = szy * mHeight;
                } else if (e->has("size") && szx > 0.0f) {                 // derive height from aspect
                    fw = szx * mWidth; fh = fw / ar;
                } else if (e->has("size") && szy > 0.0f) {                 // derive width from aspect
                    fh = szy * mHeight; fw = fh * ar;
                } else if (e->has("maxSize") && mxx > 0.0f && mxy > 0.0f) { // contain-fit
                    float bw = mxx * mWidth, bh = mxy * mHeight;
                    fw = bw; fh = bw / ar;
                    if (fh > bh) { fh = bh; fw = bh * ar; }
                } else {                                                   // native pixels
                    fw = (float)an->nw; fh = (float)an->nh;
                }
                float dx = e->getPair("pos", 0, 0.0f) * mWidth - ox * fw;
                float dy = e->getPair("pos", 1, 0.0f) * mHeight - oy * fh;
                // ES-DE tints the animation by color->colorEnd (gradientType horizontal/vertical) and
                // applies brightness/saturation/cornerRadius through the same shader path as <image>
                // (GIFAnimComponent/LottieAnimComponent applyTheme; defaults white/none = untinted).
                // interpolation picks the magnify filter (default nearest, matching ES-DE).
                float col[4]; colorOf(e, "color", 1, 1, 1, 1, col);
                float gradEnd[4]; bool grad = e->getColor("colorEnd", gradEnd);
                if (grad) gradEnd[3] *= op;
                bool gradHoriz = e->getS("gradientType", std::string("horizontal")) != "vertical";
                float aSat = std::min(1.0f, std::max(0.0f, e->getF("saturation", 1.0f)));
                float aBright = std::min(2.0f, std::max(-2.0f, e->getF("brightness", 0.0f)));
                float aRad = std::min(0.5f, std::max(0.0f, e->getF("cornerRadius", 0.0f))) * mWidth;
                bool aFx = (aSat != 1.0f || aBright != 0.0f || aRad > 0.0f || grad);
                glBindTexture(GL_TEXTURE_2D, tex);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, esdeImageMagFilter(e));
                if (aFx) drawIconTexFx(tex, dx, dy, fw, fh, col[0], col[1], col[2], col[3], rot,
                                       aSat, aBright, aRad, grad ? gradEnd : nullptr, gradHoriz);
                else     drawIconTex(tex, dx, dy, fw, fh, col[0], col[1], col[2], col[3], rot);
                if (an->frames.size() > 1) { mEsdeWantsFastFrame = true; mDisplayDirty = true; }   // keep advancing the loop
            }

        } else if (t == "text" || t == "gamelistinfo") {
            float x, y, w, h; rectOf(e, x, y, w, h);
            float c[4]; colorOf(e, "color", 1, 1, 1, 1, c);
            // ES-DE TextComponent default fontSize is 0.045; the system-view game counter path keeps
            // its historical 0.035 default, so only branch the fallback for a fontPath-less gamelistinfo.
            float sc = fontPx(e, t == "gamelistinfo" ? 0.045f : 0.035f);
            std::string s = e->getS("text", std::string());
            // ES-DE gamelistinfo (GamelistView): a persistent line in the gamelist view showing the
            // system's game count and favorite count, each preceded by a FontAwesome glyph (controller
            // U+F11B, star U+F005), rendered through the FA fallback face. nano does not model per-folder
            // entry or search filters here, so the folder/filter string variants are not emitted.
            if (t == "gamelistinfo" && !mEsdeSysList.empty() && mEsdeSysSel < (int)mEsdeSysList.size()) {
                const auto& sys = mXmbSystems[mEsdeSysList[mEsdeSysSel]];
                int total = (int)sys.roms.size(), favs = 0;
                // Count a game as a favourite from either nano's own store or the ES-DE <favorite>
                // metadata, so the counter matches the control and the textlist star indicator.
                for (const auto& r : sys.roms) {
                    const ScrapeEntry* se = esdeMetaFor(r);
                    if (isFavorite(r) || (se && se->favorite)) favs++;
                }
                s = "\xEF\x84\x9B " + std::to_string(total) +      // U+F11B controller
                    "  \xEF\x80\x85 " + std::to_string(favs);      // U+F005 star
            }
            if (s.empty() && e->has("metadata")) {
                const std::string& md = e->getS("metadata", std::string());
                if (!curRom.empty()) {
                    const ScrapeEntry* se = esdeMetaFor(curRom);
                    if (se) {
                        if (md == "description") s = se->synopsis;
                        else if (md == "genre") s = se->genre;
                        else if (md == "developer") s = se->developer;
                        else if (md == "publisher") s = se->publisher;
                        else if (md == "players") s = se->players;
                        else if (md == "name") s = se->title;
                        // ES-DE playtime is a text metadata formatted by getPlayTimeString: an
                        // unplayed game reads "unknown", else "N minutes" / "N.t hours" like Steam.
                        else if (md == "playtime") s = esdePlayTimeString(se->playTime);
                        // ES-DE playcount is the raw launch tally (metadata default 0), rating a text
                        // element shows the 0..5 star value via RatingComponent::getRatingValue.
                        else if (md == "playcount") s = se->playCount.empty() ? "0" : se->playCount;
                        else if (md == "rating") s = esdeRatingValue(se->rating);
                    }
                }
                // The game's system name (Analogue's gamelist-carousel subtitle "game-system-name",
                // adroit's systemFullname). System-level, so independent of the scrape entry; a
                // non-collection gamelist's games all belong to the focused system, so systemName ==
                // sourceSystemName here (ES-DE only differs for collection views, which nano lacks).
                if (s.empty() && !curRom.empty() &&
                    (md == "sourceSystemName" || md == "sourceSystemFullname" ||
                     md == "systemName" || md == "systemFullname") &&
                    !mEsdeSysList.empty() && mEsdeSysSel < (int)mEsdeSysList.size()) {
                    const auto& sys = mXmbSystems[mEsdeSysList[mEsdeSysSel]];
                    if (md == "sourceSystemName" || md == "systemName") s = sys.name;
                    else {
                        std::string full =
                            esdeSystemFullName(sys.romDir.empty() ? sys.shortname : sys.romDir);
                        s = full.empty() ? sys.name : full;
                    }
                }
            }
            if (s.empty() && e->has("systemdata") && !mEsdeSysList.empty() &&
                mEsdeSysSel < (int)mEsdeSysList.size()) {
                const auto& sys = mXmbSystems[mEsdeSysList[mEsdeSysSel]];
                const std::string& sd = e->getS("systemdata", std::string());
                // ES-DE systemdata (SystemView.cpp): "name" is the internal es_systems.xml name
                // (SystemData::getName, e.g. "nes") and "fullname" the display full name
                // (getFullName, e.g. "Nintendo Entertainment System"). The value is LOWER-case;
                // nano matched the camelCase "fullName" that no theme emits, so every
                // <systemdata>fullname</systemdata> title (artflix/ps5-menu/xmb-menu/codywheel
                // system-name) resolved empty and vanished. Map "name" to the internal key and
                // "fullname" through nano's ES-DE full-name table (the same lookup the carousel uses).
                std::string sysKey = sys.romDir.empty() ? sys.shortname : sys.romDir;
                if (sd == "name") s = !sysKey.empty() ? sysKey : sys.name;
                else if (sd == "fullname") { s = esdeSystemFullName(sysKey); if (s.empty()) s = sys.name; }
                else if (sd.rfind("gamecount", 0) == 0) {
                    // ES-DE SystemView::updateGameCount: the bare "gamecount" renders
                    // "N games (M favorites)" with singular/plural on both counts; the
                    // *Games / *Favorites variants keep just one half; the *NoText variants
                    // emit the raw number. A game counts as a favorite from nano's own store or the
                    // ES-DE <favorite> metadata (the focused system's gamelist is already loaded).
                    int total = (int)sys.roms.size();
                    int favs = 0;
                    for (const auto& r : sys.roms) {
                        const ScrapeEntry* se = esdeMetaFor(r);
                        if (isFavorite(r) || (se && se->favorite)) favs++;
                    }
                    auto plural = [](int n, const char* one, const char* many) {
                        return std::to_string(n) + " " + (n == 1 ? one : many);
                    };
                    if (sd == "gamecountGamesNoText") s = std::to_string(total);
                    else if (sd == "gamecountFavoritesNoText") s = std::to_string(favs);
                    else if (sd == "gamecountGames") s = plural(total, "game", "games");
                    else if (sd == "gamecountFavorites") s = plural(favs, "favorite", "favorites");
                    else s = plural(total, "game", "games") + " (" + std::to_string(favs) +
                             (favs == 1 ? " favorite)" : " favorites)");
                }
            }
            // ES-DE: a metadata/systemdata-bound text with no value falls back to <defaultValue>
            // (":space:" -> a single space placeholder), so an unscraped Developer/Publisher/Genre
            // row still holds its slot instead of collapsing (TextComponent::setValue). A literal
            // <text> element is unaffected (it already carries its own string).
            if (s.empty() && (e->has("metadata") || e->has("systemdata"))) {
                s = e->getS("defaultValue", std::string());
                if (s == ":space:") s = " ";
                // With no theme <defaultValue>, ES-DE shows the metadata field's built-in default
                // value, and genre/developer/publisher/players/playtime all read "unknown" (the value
                // TextComponent::setValue substitutes the defaultValue for; TextComponent.cpp:388). So
                // an unscraped genre / an unplayed game's playtime row reads "unknown", not blank -
                // e.g. Atari 50 Menu's game-genre. Other fields default to empty and stay blank.
                if (s.empty() && e->has("metadata")) {
                    const std::string& md = e->getS("metadata", std::string());
                    if (md == "genre" || md == "developer" || md == "publisher" ||
                        md == "players" || md == "playtime")
                        s = "unknown";
                }
            }
            if (!s.empty()) {
                const std::string& lc = e->getS("letterCase", std::string());
                esdeLetterCase(s, lc);
                // ES-DE applies <rotation> to a text element; nano's glyph layout is axis-aligned, so a
                // rotated text goes through the offscreen-FBO rotate path, pivoting about the element's
                // rotationOrigin (default origin). Opt-in: rotation==0 falls straight through unchanged.
                float rotDeg = e->getF("rotation", 0.0f);
                if (rotDeg != 0.0f) {
                    float ox = e->getPair("origin", 0, 0.0f), oy = e->getPair("origin", 1, 0.0f);
                    float rox = e->has("rotationOrigin") ? e->getPair("rotationOrigin", 0, ox) : ox;
                    float roy = e->has("rotationOrigin") ? e->getPair("rotationOrigin", 1, oy) : oy;
                    float szx = e->getPair("size", 0, 0.0f), szy = e->getPair("size", 1, 0.0f);
                    float pxn = e->getPair("pos", 0, 0.0f), pyn = e->getPair("pos", 1, 0.0f);
                    esdeDrawRotatedText(s, (pxn + (rox - ox) * szx) * mWidth,
                                           (pyn + (roy - oy) * szy) * mHeight,
                                        sc, faceOf(e), c, rotDeg);
                    continue;
                }
                // ES-DE TextComponent renderBackground: a filled rounded rect the size of the
                // element box (mSize) widened by backgroundMargins.x+.y and shifted left by
                // backgroundMargins.x, drawn BEHIND the glyphs (TextComponent.cpp:262-271; margins
                // and cornerRadius clamped 0..0.5 and scaled by screen width, :613-622). Only a
                // SIZED text element has a defined box here; auto-sized labels are a follow-up.
                // No-op unless the theme sets backgroundColor, so plate-less labels are unchanged.
                if (w > 1.0f && h > 1.0f) {
                    float bg[4];
                    if (e->getColor("backgroundColor", bg) && bg[3] > 0.0f) {
                        bg[3] *= e->getF("opacity", 1.0f);
                        auto cl = [](float v) { return std::min(0.5f, std::max(0.0f, v)); };
                        float bmX = cl(e->getPair("backgroundMargins", 0, 0.0f)) * mWidth;
                        float bmY = cl(e->getPair("backgroundMargins", 1, 0.0f)) * mWidth;
                        float rad = cl(e->getF("backgroundCornerRadius", 0.0f)) * mWidth;
                        drawRoundedRect(x - bmX, y, w + bmX + bmY, h, rad, bg[0], bg[1], bg[2], bg[3]);
                    }
                }
                // ES-DE anchors a text by its rendered glyph box (TextComponent mSize), so a
                // bottom/right/center origin shifts the text by its own measured extent. rectOf
                // only offsets by an explicit <size>; when a dimension is absent (w or h came out
                // 0) apply the origin against the content here - e.g. the atari title's origin
                // (0 1) seats its baseline on the anchor instead of hanging a line-height below it.
                float eox = e->getPair("origin", 0, 0.0f), eoy = e->getPair("origin", 1, 0.0f);
                if (w <= 1.0f && eox != 0.0f) x -= eox * measureText(s.c_str(), sc, faceOf(e));
                if (h <= 1.0f && eoy != 0.0f) y -= eoy * sc * FONT_CHAR_H;
                std::string alignS = e->getS("horizontalAlignment", std::string());
                // A sized multi-line box (e.g. the gamelist synopsis container) word-wraps and
                // clips; a single-line label draws aligned in place. ES-DE turns the description
                // into an auto-scrolling container by default, so pass the game id as the scroll
                // key for a description (or an explicit container) to enable the vertical crawl.
                const std::string& mdT = e->getS("metadata", std::string());
                bool container = e->getB("container", mdT == "description");
                // ES-DE seats every text baseline at (yTop+yBot)/2 below its box top
                // (Font::buildTextCache): yTop = the 'S' cap-height bearing, yBot =
                // getHeight(lineSpacing) = em*lineSpacing (default 1.5). A box taller than that
                // 1.5*em text height then vertical-aligns within the slack (render yOff); a
                // shorter or auto-height box just centres the single line. nano's drawText seats
                // the baseline 0.8*em below the draw origin, so fold ES-DE's seating into a y
                // offset - the single-line twin of drawWrapped's topInset. Without it an
                // auto-height label (e.g. adroit's system-name header) rides ~0.3*em too high.
                float emPx = sc * (float)FONT_CHAR_H;
                int capRpx = (int)lroundf(emPx); if (capRpx < 6) capRpx = 6;
                const GlyphInfo* capG = ensureGlyph('S', capRpx, faceOf(e));
                float yTopCap = capG ? (float)capG->bearingY : emPx * 0.72f;
                // ES-DE clamps lineSpacing to [0.5, 3.0] (TextComponent::setLineSpacing).
                float yBot = emPx * std::min(3.0f, std::max(0.5f, e->getF("lineSpacing", 1.5f)));
                const std::string& va = e->getS("verticalAlignment", std::string());
                float baseFromTop;
                if (h > yBot && !container && va == "top")
                    baseFromTop = (yTopCap + yBot) * 0.5f;
                else if (h > yBot && !container && va == "bottom")
                    baseFromTop = h - (yBot - yTopCap) * 0.5f;
                else {
                    // center (ES-DE default) and every box <= the text height: the line centres,
                    // which for a sized box works out to (boxH + yTop)/2 and for auto-height to
                    // (1.5*em + yTop)/2 - both expressed as (hEff + yTop)/2.
                    float hEff = (h > 1.0f && !container) ? h : yBot;
                    baseFromTop = (hEff + yTopCap) * 0.5f;
                }
                float vOff = baseFromTop - emPx * 0.8f;
                if (w > 1.0f && h > sc * FONT_CHAR_H * 1.6f) {
                    // Pass the element's verticalAlignment so a fitting wrapped block (e.g. a
                    // single-line game-name in a tall box) centres in its box as ES-DE does, rather
                    // than pinning to the top; an overflowing container still top-anchors and scrolls.
                    // A CONTAINER is the exception: ES-DE wraps its text in an auto-height component
                    // (mSize.y == textHeight), so the valign offset branch (mSize.y > textHeight) never
                    // fires and the ScrollableContainer always renders its content from the top. A
                    // container that sets verticalAlignment=bottom (artflix's system-name heading, a
                    // single line in a 0.2-tall box) must therefore still top-anchor, not sink to the
                    // box bottom and overlap the game count / description below it.
                    std::string vAlignS = container ? std::string("top")
                                                     : e->getS("verticalAlignment", std::string());
                    // ES-DE ScrollableContainer honours per-element scroll overrides (setScrollParameters,
                    // ScrollableContainer.cpp:115-129): containerStartDelay (s, clamp 0..10),
                    // containerScrollSpeed (clamp 0.1..10, speed = AUTO_SCROLL_SPEED/value) and
                    // containerResetDelay (s, clamp 0..20). Defaults 4.5s / 1.0 / 7.0s.
                    float startDelayMs = e->has("containerStartDelay")
                        ? std::min(10.0f, std::max(0.0f, e->getF("containerStartDelay", 4.5f))) * 1000.0f
                        : 4500.0f;
                    float scrollSpeedConst = 4.0f /
                        std::min(10.0f, std::max(0.1f, e->getF("containerScrollSpeed", 1.0f)));
                    float resetDelayMs = e->has("containerResetDelay")
                        ? std::min(20.0f, std::max(0.0f, e->getF("containerResetDelay", 7.0f))) * 1000.0f
                        : 7000.0f;
                    drawWrapped(s, x, y, w, h, sc,
                                std::min(3.0f, std::max(0.5f, e->getF("lineSpacing", 1.5f))),
                                alignS.c_str(), c,
                                container ? curRom : std::string(), faceOf(e), vAlignS.c_str(),
                                startDelayMs, scrollSpeedConst, resetDelayMs);
                }
                else if (w > 1.0f) {
                    // ES-DE horizontal container (containerType=horizontal, e.g. linear's Developer/
                    // Publisher): a single-line value that, when it overflows the box, LEFT-anchors
                    // (ignoring the theme alignment; TextComponent.cpp:240-247 offsetX=0 on overflow)
                    // and marquee-scrolls left after containerStartDelay, with a trailing gap + a second
                    // copy wrapping seamlessly (mHorizontalScrolling / ScrollableContainer horizontal).
                    // nano used to centre + clip it, showing the middle of the string; match ES-DE.
                    bool hCont = container &&
                                 e->getS("containerType", std::string()) == "horizontal";
                    float twH = hCont ? measureText(s.c_str(), sc, faceOf(e)) : 0.0f;
                    if (hCont && twH > w + 1.0f) {
                        if (curRom != mEsdeHScrollRom) { mEsdeHScrollRom = curRom; mEsdeHScrollStart.clear(); }
                        std::string hk = e->name;
                        auto hi = mEsdeHScrollStart.find(hk);
                        if (hi == mEsdeHScrollStart.end())
                            hi = mEsdeHScrollStart.emplace(hk, (int64_t)uptimeMillis()).first;
                        // Same speed/delay/gap model as the textlist marquee: speed = the 26-capital
                        // advance * 0.247 * containerScrollSpeed; delay = containerStartDelay (default
                        // 1.5s here, matching TextComponent mScrollDelay); return gap = speed * gap.
                        float sizeRef = measureText("ABCDEFGHIJKLMNOPQRSTUVWXYZ", sc, faceOf(e));
                        float spd = sizeRef * 0.247f *
                            std::min(10.0f, std::max(0.1f, e->getF("containerScrollSpeed", 1.0f)));
                        float cgap = std::min(5.0f, std::max(0.1f, e->getF("containerScrollGap", 1.5f)));
                        float delay = std::min(10.0f, std::max(0.0f,
                            e->getF("containerStartDelay", 1.5f))) * 1000.0f;
                        float retLen = spd * cgap;
                        float scT = spd > 0.0f ? twH * 1000.0f / spd : 0.0f;
                        float retT = spd > 0.0f ? retLen * 1000.0f / spd : 0.0f;
                        float maxT = delay + scT + retT;
                        float t = maxT > 0.0f
                            ? std::fmod((float)((int64_t)uptimeMillis() - hi->second), maxT) : 0.0f;
                        float st2 = scT + retT, off = 0.0f;
                        if (t >= delay && st2 > 0.0f && t < delay + st2)
                            off = (twH + retLen) * (t - delay) / st2;
                        scissorLogicalRect(x, y - sc * FONT_CHAR_H, w, sc * FONT_CHAR_H * 3.0f);
                        drawText(s.c_str(), x - off, y + vOff, sc, c[0], c[1], c[2], c[3], faceOf(e));
                        drawText(s.c_str(), x - off + twH + retLen, y + vOff, sc,
                                 c[0], c[1], c[2], c[3], faceOf(e));
                        glDisable(GL_SCISSOR_TEST);
                        mEsdeWantsFastFrame = true; mDisplayDirty = true;
                    } else {
                        // ES-DE clips a single-line text element to its width, so an over-long value
                        // (e.g. modern's Publisher/Developer) does not bleed into the next column.
                        scissorLogicalRect(x, y - sc * FONT_CHAR_H, w, sc * FONT_CHAR_H * 3.0f);
                        drawAligned(s, x, y + vOff, w, sc, alignS.c_str(), c, faceOf(e));
                        glDisable(GL_SCISSOR_TEST);
                    }
                } else
                    drawAligned(s, x, y + vOff, w, sc, alignS.c_str(), c, faceOf(e));
            }

        } else if (t == "video") {
            // A <video> with an imageType shows the game's media; nano has no live game video so it
            // renders that imageType's scraped boxart (the only per-game art nano keeps) as a static
            // stand-in. A <video> with only a fixed <path> and NO imageType is a played media FILE -
            // e.g. Adroit's backgroundvideo -> ${backgroundmp4}, a full-screen size 1 1 background.
            // Play it through the HW decoder (esdeBgVideo) when the file exists; ./none / missing ->
            // stop any running background video and draw nothing (never stretch the box art here).
            // nano has ONE hardware video decoder, so a no-imageType <video> (a fixed <path> media
            // file, not a per-game preview) can only play if it is a single substantial background:
            // the theme's full-screen video (Adroit's size 1 1 backgroundvideo). A theme that also
            // uses small overlay videos (catppuccin's animated accent bars, size 1 0.01) would starve
            // the one decoder and mis-size, so only a substantial/background video is played; anything
            // nano does not play must still show the element's STATIC image, so we fall through to the
            // <defaultImage> path below instead of returning here. Threshold is area, so a full or
            // half-screen background plays while a thin bar does not.
            const bool noImageType = !e->has("imageType");
            bool playedBg = false;
            if (noImageType) {
                float vsx = e->getPair("size", 0, 0.0f), vsy = e->getPair("size", 1, 0.0f);
                if (vsx * vsy >= 0.2f) {
                    // A substantial/background fixed-path video owns the single decoder.
                    const std::string& vpath = e->getPath("path");
                    bool playable = !vpath.empty() && access(vpath.c_str(), R_OK) == 0;
                    esdeBgVideoTick(playable ? vpath : std::string());
                    if (playable) {
                        float x, y, w, h; rectOf(e, x, y, w, h);
                        if (w < 1.0f) w = (float)mWidth;
                        if (h < 1.0f) h = (float)mHeight;
                        esdeBgVideoDraw(x, y, w, h);
                        playedBg = true;
                    }
                }
            }
            // The still image inside a <video> follows ImageComponent fit semantics (VideoComponent
            // sets up mStaticImage via setResize/setMaxSize/setCroppedSize): size/maxSize contain-fit
            // the cover, cropSize cover-crops it. mDrawPillarboxes only draws the video's black-bar
            // frame and never crops the still image, so a theme's pillarboxes=false (e.g. alekfull's
            // boxart) must NOT fill-crop the cover - it stays contained in its maxSize box.
            float iw = e->getPair("imageMaxSize", 0, 0.0f), ih = e->getPair("imageMaxSize", 1, 0.0f);
            if (iw <= 0 && ih <= 0) { iw = e->getPair("maxSize", 0, 0.0f); ih = e->getPair("maxSize", 1, 0.0f); }
            if (iw <= 0 && ih <= 0) { iw = e->getPair("size", 0, 0.0f); ih = e->getPair("size", 1, 0.0f); }
            bool cropFit = false;
            if (iw <= 0 && ih <= 0) {
                float cx = e->getPair("cropSize", 0, 0.0f), cy = e->getPair("cropSize", 1, 0.0f);
                if (cx > 0 && cy > 0) { iw = cx; ih = cy; cropFit = true; }
            }
            float boxW = iw * mWidth, boxH = ih * mHeight;
            // ES-DE GuiComponent defaults both origin and position to {0,0} (top-left) and only
            // changes them when the element declares "origin"/"pos" - the same defaults nano's image
            // paths use. Defaulting either to 0.5 here silently mis-centres a video that sets pos but
            // no origin: Cathode's system-view backgroundpromo (pos "0 0", cropSize "1 .9", no origin)
            // then anchored its box centre at the screen's top-left corner and pushed its defaultImage
            // (and any still) mostly off-screen. When BOTH are unset the pair still yields a full-screen
            // box, so this only corrects the pos-set/origin-unset case and matches ES-DE exactly.
            float ox = e->getPair("origin", 0, 0.0f), oy = e->getPair("origin", 1, 0.0f);
            float px = e->getPair("pos", 0, 0.0f) * mWidth, py = e->getPair("pos", 1, 0.0f) * mHeight;
            // ES-DE rounds the video's static image by imageCornerRadius (VideoComponent.cpp:287-288,
            // clamped 0..0.5 * screenWidth); videoCornerRadius rounds the live video box. nano only ever
            // draws the static stand-in, so it applies imageCornerRadius, falling back to
            // videoCornerRadius so a tile the theme rounds only on its video is not drawn hard-cornered.
            // Effective on a contain-fit still (the fitted quad is the drawn image); a cover-cropped
            // still is scissor-clipped to a square box, so the rounding is inert there (as it is round
            // the overflow), matching that nano cannot round a cropped box corner.
            float vRad = e->getF("imageCornerRadius", -1.0f);
            if (vRad < 0.0f) vRad = e->getF("videoCornerRadius", 0.0f);
            vRad = std::min(0.5f, std::max(0.0f, vRad)) * mWidth;
            // The media game: the navigated rom, or a gameselector-picked game from the focused
            // system (atari's full-screen background screenshot). A background video with no explicit
            // box fills the screen and covers it (pillarboxes=false), rather than contain-fitting a
            // sized preview.
            const std::string vImgType = e->getS("imageType", std::string("cover"));
            // ES-DE's VideoComponent treats imageType="none" as "no static image fallback": the
            // element shows only the live per-game video (or its <defaultImage>), never box art
            // (setGameImage has no "none" branch, so the static path stays empty - mImageTypeNone).
            // nano cannot decode a per-game video, so a pure-none <video> draws NOTHING here rather
            // than standing in with the scraped cover, matching what the control shows when a game
            // has no video (aura/xmb-menu/ps5-menu game-video regions). A mixed list keeps its
            // cover-family fallback below; only the whole-string "none" is suppressed.
            {
                size_t a = vImgType.find_first_not_of(" \t\r\n");
                size_t b = vImgType.find_last_not_of(" \t\r\n");
                if (a != std::string::npos && vImgType.substr(a, b - a + 1) == "none") continue;
            }
            std::string vRom = curRom;
            if (vRom.empty()) { const std::string& gs = e->getS("gameselector", std::string());
                                if (!gs.empty()) vRom = esdeGsRom(gs, vImgType); }
            const bool fullBg = (boxW <= 1.0f || boxH <= 1.0f);
            if (fullBg) { boxW = (float)mWidth; boxH = (float)mHeight; }
            bool drewVideo = false;
            if (!noImageType && !vRom.empty() && boxW > 1 && boxH > 1) {
                float ar = 1.0f;
                GLuint tex = esdeGameMediaTex(vRom, vImgType, &ar);
                if (tex && ar > 0.0f) {
                    // Cover-fit (fill the box, crop the overflow) only for a full-screen background
                    // or an explicit cropSize; otherwise contain-fit the still cover inside the box,
                    // exactly as ImageComponent does for a size/maxSize static image.
                    const bool cover = fullBg || cropFit;
                    float fw, fh;
                    if (cover) { if (boxW / ar >= boxH) { fw = boxW; fh = boxW / ar; } else { fh = boxH; fw = boxH * ar; } }
                    else       { fw = boxW; fh = boxW / ar; if (fh > boxH) { fh = boxH; fw = boxH * ar; } }
                    // ES-DE anchors by origin against the FITTED size (ImageComponent sets mSize to it).
                    float bx = px - ox * boxW, by = py - oy * boxH;   // the element box (for clipping)
                    float dx = px - ox * fw,   dy = py - oy * fh;     // the fitted image
                    float c[4]; colorOf(e, "color", 1, 1, 1, 1, c);
                    // ES-DE clamps saturation to [0,1] and brightness to [-2,2] in GuiComponent
                    // (applied to the video's static image); match so an out-of-range theme value
                    // cannot over-drive the FX shader past the control.
                    float vSat = std::min(1.0f, std::max(0.0f, e->getF("saturation", 1.0f)));
                    float vBright = std::min(2.0f, std::max(-2.0f, e->getF("brightness", 0.0f)));
                    // Clip the cover overflow only when the box is INSET from the screen (a full-screen
                    // box is already clipped by the viewport, and a full-screen scissor here suppresses
                    // the draw on the DRM path - see the image cropSize path).
                    bool inset = bx > 0.5f || by > 0.5f || bx + boxW < mWidth - 0.5f || by + boxH < mHeight - 0.5f;
                    bool needClip = cover && (fw > boxW + 1.0f || fh > boxH + 1.0f) && inset;
                    if (needClip) scissorLogicalRect(bx, by, boxW, boxH);
                    glBindTexture(GL_TEXTURE_2D, tex);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, esdeImageMagFilter(e));
                    if (vSat != 1.0f || vBright != 0.0f || vRad > 0.0f)
                        drawIconTexFx(tex, dx, dy, fw, fh, c[0], c[1], c[2], c[3], 0.0f, vSat, vBright, vRad);
                    else
                        drawIconTex(tex, dx, dy, fw, fh, c[0], c[1], c[2], c[3]);
                    if (needClip) glDisable(GL_SCISSOR_TEST);
                    drewVideo = true;
                }
            }
            // ES-DE's VideoComponent ALWAYS shows a static image (VideoComponent::setImage falls back to
            // mDefaultImagePath): the game's imageType media, or the <defaultImage> when there is no
            // media - which includes the SYSTEM view (no navigated game) and any unscraped game. nano
            // cannot play the video, so when it drew no media it must still render the defaultImage,
            // contain-fit in the box like ImageComponent, or the element shows nothing (e.g. Showcase's
            // system-view TV, whose md_video defaults to splash.svg).
            if (!drewVideo && !playedBg) {
                std::string di = e->getPath("defaultImage");
                if (!di.empty() && boxW > 1 && boxH > 1) {
                    std::string dp = (di.find("${system.") != std::string::npos && esdeFocSys >= 0)
                                         ? esdeResolveSystemPath(di, esdeFocSys) : di;
                    EsdeSvg a = esdeArtTex(dp, (int)boxW, (int)boxH);
                    if (a.tex && a.w > 0 && a.h > 0) {
                        // The static image follows the video's own fit: a full-screen or cropSize box
                        // cover-fills and crops the overflow (honouring cropPos, default 0.5, like
                        // coverFitCrop); a size/maxSize box contain-fits. Cathode's system-view
                        // backgroundpromo defaults to a full-width cropSize TV frame, so it must cover,
                        // not letterbox to a small centred square.
                        float c[4]; colorOf(e, "color", 1, 1, 1, 1, c);
                        float vSat = std::min(1.0f, std::max(0.0f, e->getF("saturation", 1.0f)));
                        float vBright = std::min(2.0f, std::max(-2.0f, e->getF("brightness", 0.0f)));
                        const bool cover = fullBg || cropFit;
                        float ar = (float)a.w / (float)a.h;
                        float fw, fh, dx, dy;
                        float bx = px - ox * boxW, by = py - oy * boxH;
                        if (cover) {
                            if (boxW / ar >= boxH) { fw = boxW; fh = boxW / ar; }
                            else                   { fh = boxH; fw = boxH * ar; }
                            float cpx = std::min(1.0f, std::max(0.0f, e->getPair("cropPos", 0, 0.5f)));
                            float cpy = std::min(1.0f, std::max(0.0f, e->getPair("cropPos", 1, 0.5f)));
                            dx = bx - (fw - boxW) * cpx; dy = by - (fh - boxH) * cpy;
                        } else {
                            fw = (float)a.w; fh = (float)a.h;
                            dx = px - ox * fw; dy = py - oy * fh;
                        }
                        bool inset = bx > 0.5f || by > 0.5f || bx + boxW < mWidth - 0.5f || by + boxH < mHeight - 0.5f;
                        bool needClip = cover && (fw > boxW + 1.0f || fh > boxH + 1.0f) && inset;
                        if (needClip) scissorLogicalRect(bx, by, boxW, boxH);
                        glBindTexture(GL_TEXTURE_2D, a.tex);
                        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, esdeImageMagFilter(e));
                        if (vSat != 1.0f || vBright != 0.0f || vRad > 0.0f)
                            drawIconTexFx(a.tex, dx, dy, fw, fh, c[0], c[1], c[2], c[3], 0.0f, vSat, vBright, vRad);
                        else
                            drawIconTex(a.tex, dx, dy, fw, fh, c[0], c[1], c[2], c[3]);
                        if (needClip) glDisable(GL_SCISSOR_TEST);
                    }
                }
            }

        } else if (t == "rating") {
            // Five stars: the first round(rating*5) use filledPath, the rest unfilledPath.
            float frac = 0.0f;
            if (!curRom.empty()) {
                const ScrapeEntry* se = esdeMetaFor(curRom);
                if (se && !se->rating.empty()) {
                    frac = (float)atof(se->rating.c_str());
                    if (frac > 20.0f) frac /= 100.0f;       // 0..100
                    else if (frac > 5.0f) frac /= 20.0f;    // 0..20 (ScreenScraper raw)
                    else if (frac > 1.0f) frac /= 5.0f;     // 0..5
                }
            }
            if (frac > 0.0f || !e->getB("hideIfZero", false)) {
                // ES-DE RatingComponent default star height = 0.06 * screenHeight when the theme
                // omits <size> (ABN sets 0.05 explicitly, so this only changes size-less themes).
                float starH = e->getPair("size", 1, 0.06f) * mHeight;
                if (starH <= 0) starH = 0.06f * mHeight;
                const int stars = 5; float totalW = starH * stars;
                // ES-DE's RatingComponent does not set its own origin, so it inherits GuiComponent's
                // default (0,0) - the star row anchors its top-left at pos. nano previously defaulted
                // origin.y to 0.5, seating a size-less rating half a star-height too high vs the
                // control on the many themes (canvas/shinretro/alekfull/art-book-next) that position
                // a rating with <pos>/<size> but omit <origin>.
                float ox = e->getPair("origin", 0, 0.0f), oy = e->getPair("origin", 1, 0.0f);
                float x0 = e->getPair("pos", 0, 0.9f) * mWidth - ox * totalW;
                float yTop = e->getPair("pos", 1, 0.6f) * mHeight - oy * starH;
                std::string fp = e->getPath("filledPath"), up = e->getPath("unfilledPath");
                // ES-DE falls back to its built-in star graphics when the theme omits the paths, which
                // is the norm (Linear/Slate). Materialise the same defaults so the row is not blank.
                if (fp.empty()) fp = esdeDefaultStarPath(true);
                if (up.empty()) up = esdeDefaultStarPath(false);
                float col[4]; colorOf(e, "color", 1, 1, 1, 1, col);
                // ES-DE rounds the rating to the nearest 0.1 then draws the full unfilled
                // 5-star row and overlays the filled row clipped to frac of the total width,
                // so half and fractional stars are shown (not whole-star quantised).
                float rv = roundf(frac * 10.0f) / 10.0f;
                if (rv > 1.0f) rv = 1.0f;
                auto drawStarRow = [&](const std::string& p) {
                    if (p.empty()) return;
                    for (int si = 0; si < stars; si++) {
                        EsdeSvg a = esdeArtTex(p, (int)starH, (int)starH);
                        if (a.tex) drawIconTex(a.tex, x0 + si * starH + (starH - a.w) * 0.5f,
                                               yTop + (starH - a.h) * 0.5f, (float)a.w, (float)a.h,
                                               col[0], col[1], col[2], col[3]);
                    }
                };
                float clipW = rv * totalW;
                // ES-DE `overlay` (default true) draws the FULL unfilled row and overlays the filled
                // row clipped to the fill fraction. overlay=false (Slate/Linear/Catppuccin) instead
                // CLIPS the unfilled row to the region to the RIGHT of the fill, so with a
                // semi-transparent filled star the unfilled does not show through it
                // (RatingComponent::setValue: mIconUnfilled.setClipRegion({clipValue,0,mSize.x,...})).
                // For opaque stars (every reference theme) the two are visually identical, since the
                // filled star fully covers the unfilled one on the filled side either way.
                if (e->getB("overlay", true)) {
                    drawStarRow(up);
                } else if (clipW < totalW) {
                    scissorLogicalRect(x0 + clipW, yTop, totalW - clipW, starH);
                    drawStarRow(up);
                    glDisable(GL_SCISSOR_TEST);
                }   // overlay=false + full rating: no unfilled drawn (the full filled row covers it)
                if (clipW > 0.0f && !fp.empty()) {
                    scissorLogicalRect(x0, yTop, clipW, starH);
                    drawStarRow(fp);
                    glDisable(GL_SCISSOR_TEST);
                }
            }

        } else if (t == "datetime" || t == "clock") {
            float x, y, w, h; rectOf(e, x, y, w, h);
            float c[4]; colorOf(e, "color", 1, 1, 1, 1, c);
            float sc = fontPx(e, 0.033f);
            std::string s;
            if (t == "clock") {
                // Wall clock: the current time via the element's strftime format.
                std::string fmt = e->getS("format", std::string());
                if (fmt.empty()) fmt = "%H:%M";
                time_t now = time(nullptr); struct tm lt; localtime_r(&now, &lt);
                char out[64] = {0};
                if (strftime(out, sizeof(out), fmt.c_str(), &lt) == 0) out[0] = 0;
                out[sizeof(out) - 1] = 0;
                s = out;
            } else {
                // datetime: a per-game metadata date, NOT the current time. nano shows the scraped
                // release date and the ES-DE gamelist lastplayed (playtime is a text element below).
                const std::string& md = e->getS("metadata", std::string());
                if (!curRom.empty() && md == "releasedate") {
                    const ScrapeEntry* se = esdeMetaFor(curRom);
                    if (se) s = se->releaseDate;
                }
                // ES-DE lastplayed auto-enables displayRelative, so it reads "never" for an unplayed
                // game (nano tracks no play history of its own) or "N ... ago" for one played in ES-DE.
                // A theme that forces displayRelative=false gets the absolute YYYY-MM-DD (then run
                // through <format> below, like releasedate).
                else if (!curRom.empty() && md == "lastplayed") {
                    const ScrapeEntry* se = esdeMetaFor(curRom);
                    if (se) {
                        if (e->getB("displayRelative", true))
                            s = esdeLastPlayedString(se->lastPlayed);
                        else if (se->lastPlayed.size() >= 8 &&
                                 se->lastPlayed.compare(0, 8, "19700101") != 0)
                            s = se->lastPlayed.substr(0, 4) + "-" + se->lastPlayed.substr(4, 2) +
                                "-" + se->lastPlayed.substr(6, 2);
                    }
                }
                // ES-DE DateTimeComponent formats the stored date through the element's strftime
                // <format> (getDisplayString -> timeToString(mTime, mFormat)), so <format>%Y</format>
                // shows just the year, %Y-%m the year-month, etc. nano stores the scraped date as
                // "YYYY-MM-DD" or "YYYY" (NanoScraper.h); parse it and re-emit via strftime, falling
                // back to the raw string if it does not parse. Generic for any datetime element.
                if (!s.empty() && e->has("format")) {
                    std::string fmt = e->getS("format", std::string());
                    if (!fmt.empty()) {
                        // Seed day-of-month to 1 like ES-DE's stringToTime (TimeUtil.cpp inits the tm
                        // with tm_mday=1): a year-only date parsed by strptime("%Y") never sets
                        // tm_mday, so a zero-initialised tm would strftime "%Y-%m-%d" as "2020-01-00".
                        // With tm_mday=1 a year-only date resolves to Jan 1, matching ES-DE exactly.
                        struct tm dt = {}; dt.tm_mday = 1; const char* parsed = nullptr;
                        if (s.size() >= 10)      parsed = strptime(s.c_str(), "%Y-%m-%d", &dt);
                        else if (s.size() == 4)  parsed = strptime(s.c_str(), "%Y", &dt);
                        if (parsed) {
                            char out[64] = {0};
                            if (strftime(out, sizeof(out), fmt.c_str(), &dt) > 0) {
                                out[sizeof(out) - 1] = 0; s = out;
                            }
                        }
                    }
                }
                if (s.empty()) {
                    s = e->getS("defaultValue", std::string());
                    // ES-DE DateTimeComponent treats ":space:" as a blank placeholder (a single
                    // space) for an unset value, e.g. linear's Last Played; render it as such
                    // instead of the literal token.
                    if (s == ":space:") s = " ";
                    // With no <defaultValue>, a relative lastplayed on a never-played game reads
                    // "never" (DateTimeComponent.cpp:96); other unset dates stay blank.
                    if (s.empty() && md == "lastplayed" && e->getB("displayRelative", true))
                        s = "never";
                }
            }
            // ES-DE datetime/clock inherit TextComponent's letterCase (uppercase/lowercase/
            // capitalize), e.g. slate uppercases its release / last-played dates. Parsed but
            // never applied before; no-op when the theme omits it.
            esdeLetterCase(s, e->getS("letterCase", std::string()));
            if (!s.empty()) {
                // Draw the theme's rounded plate behind the text (ES-DE clock/datetime
                // backgroundColor), sized to the glyph box, then the text on top.
                float tw = measureText(s.c_str(), sc), th = sc * FONT_CHAR_H;
                // rectOf only offsets by an explicit <size>; a clock/datetime with an origin and no
                // <size> must seat its center/right on the anchor (ES-DE anchors by the rendered glyph
                // box), same as the text branch. Without this a clock with origin 0 0.5 (e.g.
                // Catppuccin's) hangs half a line below its pos instead of centering on it.
                float eox = e->getPair("origin", 0, 0.0f), eoy = e->getPair("origin", 1, 0.0f);
                if (w <= 1.0f && eox != 0.0f) x -= eox * tw;
                // ES-DE renders a datetime/clock as ONE line and auto-sizes the box HEIGHT to that
                // line (DateTimeComponent), so a theme <size> height never stretches the vertical box.
                // A size'd clock therefore seats its single line origin.y*th below pos.y, not at the
                // box top: Cathode's LCD clock is a full-screen "size 1 1" element corner-anchored via
                // "origin 1 1 / pos .995 .996" - without collapsing the height it drew at the screen
                // TOP instead of the bottom-right. Recompute y from the one-line text height; this
                // subsumes the no-size case (rectOf gives h=0 there, so the old h<=1 branch matched).
                y = e->getPair("pos", 1, 0.0f) * mHeight - eoy * th;
                esdeDrawPlate(e, x, y, tw, th);
                drawAligned(s, x, y, w, sc,
                            e->getS("horizontalAlignment", std::string()).c_str(), c, faceOf(e));
            }

        } else if (t == "helpsystem") {
            esdeDrawHelp(e, gamelist, chosenPrimary);

        } else if (t == "systemstatus") {
            esdeDrawSystemStatus(e);

        } else if (t == "textlist" || t == "carousel" || t == "grid") {
            if (e == chosenPrimary)   // ES-DE keeps only the first primary per view
                drawPrimary(e);       // in-place at its zIndex; chrome (higher zIndex) draws over it

        } else if (t == "badges") {
            // ES-DE BadgeComponent: a flexbox of small game-state icons bound to the game's
            // metadata. A slot activates per GamelistView.cpp: favorite/completed/kidgame/broken from
            // the "true" bool metadata, controller/altemulator when their string is non-empty. Each
            // active slot draws the theme's <customBadgeIcon> or the bundled ES-DE default, tinted by
            // badgeIconColor, laid out row/column with left/center/right alignment (FlexboxComponent).
            // The controller badge overlays the specific controller icon on the controller base.
            if (curRom.empty()) continue;
            const ScrapeEntry* meta = esdeMetaFor(curRom);
            // Declared slot order ("all" expands to every slot in ES-DE's canonical order).
            static const char* kAllSlots[] = {"collection", "folder", "favorite", "completed",
                                              "kidgame", "broken", "controller", "altemulator",
                                              "manual"};
            std::vector<std::string> slots;
            {
                std::string slotsStr = e->getS("slots", std::string("all"));
                std::string cur;
                auto flush = [&]() {
                    size_t a = cur.find_first_not_of(" \t"), b = cur.find_last_not_of(" \t");
                    if (a != std::string::npos) { std::string s = cur.substr(a, b - a + 1);
                        for (auto& c : s) c = (char)tolower((unsigned char)c); slots.push_back(s); }
                    cur.clear();
                };
                for (char c : slotsStr) { if (c == ',' || isspace((unsigned char)c)) flush(); else cur += c; }
                flush();
                if (std::find(slots.begin(), slots.end(), std::string("all")) != slots.end()) {
                    slots.erase(std::remove(slots.begin(), slots.end(), std::string("all")), slots.end());
                    for (const char* s : kAllSlots)
                        if (std::find(slots.begin(), slots.end(), std::string(s)) == slots.end())
                            slots.push_back(s);
                }
            }
            // Active slots (metadata condition holds), preserving the declared order.
            struct ActiveBadge { std::string slot, controller; };
            std::vector<ActiveBadge> active;
            for (auto& slot : slots) {
                bool on = false; std::string ctrl;
                if (slot == "favorite")         on = isFavorite(curRom) || (meta && meta->favorite);
                else if (slot == "completed")   on = meta && meta->completed;
                else if (slot == "kidgame")     on = meta && meta->kidgame;
                else if (slot == "broken")      on = meta && meta->broken;
                else if (slot == "altemulator") on = meta && !meta->altemulator.empty();
                else if (slot == "manual")      on = esdeManualExists(curRom);
                else if (slot == "controller") { if (meta && !meta->controller.empty()) { on = true; ctrl = meta->controller; } }
                // folder / collection: nano's ES-DE gamelist has no folder entries or collection
                // editing, so those slots never activate (matches an empty-metadata game).
                if (on) active.push_back({slot, ctrl});
            }
            if (active.empty()) continue;
            float x, y, w, h; rectOf(e, x, y, w, h);
            if (w < 1 || h < 1) continue;
            int perLine = (int)e->getU("itemsPerLine", 4); perLine = perLine < 1 ? 1 : (perLine > 10 ? 10 : perLine);
            int nLines  = (int)e->getU("lines", 3);        nLines  = nLines  < 1 ? 1 : (nLines  > 10 ? 10 : nLines);
            std::string dir = e->getS("direction", std::string("row"));
            std::string align = e->getS("horizontalAlignment", std::string("left"));
            // itemMargin honours ES-DE's -1 sentinel (mirror the other axis, scaled by width/height).
            float imX = e->getPair("itemMargin", 0, 0.01f), imY = e->getPair("itemMargin", 1, 0.01f);
            float mx = (imX == -1.0f) ? roundf(imY * mHeight) : roundf(imX * mWidth);
            float my = (imY == -1.0f) ? roundf(imX * mWidth)  : roundf(imY * mHeight);
            mx = std::max(0.0f, std::min(mx, w * 0.5f)); my = std::max(0.0f, std::min(my, h * 0.5f));
            // Grid dims; if the declared grid cannot hold all active badges, widen the primary axis.
            float gx = (dir == "row") ? (float)perLine : (float)nLines;
            float gy = (dir == "row") ? (float)nLines  : (float)perLine;
            if (gx * gy < (float)active.size()) { if (dir == "row") gx = (float)active.size(); else gy = (float)active.size(); }
            // Square item size: (size + margin - grid*margin)/grid, constrained to the badge's square
            // aspect (min axis), matching FlexboxComponent maxItemSize.
            float cw = (w + mx - gx * mx) / gx, ch = (h + my - gy * my) / gy;
            float bs = roundf(std::min(cw, ch)); if (bs < 1.0f) continue;
            float pitchX = bs + mx, pitchY = bs + my;
            float bc[4] = {1, 1, 1, 1}; e->getColor("badgeIconColor", bc);
            bc[3] *= e->getF("opacity", 1.0f);
            // The controller glyph overlaid on the controller badge has its OWN tint and scale in
            // ES-DE (BadgeComponent controllerIconColor + FlexboxItem::overlaySize), independent of the
            // base plate: controllerIconColor defaults to white (not badgeIconColor), and the overlay
            // scale defaults to 0.5 of the base badge width (FlexboxComponent.h overlaySize{0.5f}), not
            // full size. A theme that recolours the plate via badgeIconColor must leave the glyph white.
            float cc[4] = {1, 1, 1, 1}; e->getColor("controllerIconColor", cc);
            cc[3] *= e->getF("opacity", 1.0f);
            float ctrlSize = e->getF("controllerSize", 0.5f);   // controller-overlay scale (carbon 0.8)
            int rows = dir == "row" ? (((int)active.size() + perLine - 1) / perLine) : nLines;
            (void)rows;
            for (size_t i = 0; i < active.size(); i++) {
                int col, row;
                // itemsPerLine (perLine) is the item count along the fill axis, `lines` (nLines) the
                // number of lines. In row mode a line is a row (fill across); in column mode a line is
                // a COLUMN (fill DOWN), so items stack vertically perLine-per-column, not horizontally.
                // nano used nLines here, which for a lines=1 column laid every badge in one row (ES-DE
                // FlexboxComponent column mode fills y-inner: row = i % itemsPerLine, col = i / it).
                if (dir == "row") { col = (int)i % perLine; row = (int)i / perLine; }
                else              { row = (int)i % perLine; col = (int)i / perLine; }
                float bx = x + col * pitchX, by = y + row * pitchY;
                if (dir == "row") {
                    // ES-DE FlexboxComponent applies TWO shifts for center/right alignment (left needs
                    // neither). (1) A BASE offset on EVERY item that repositions the whole grid inside
                    // the element box: the gx columns occupy (bs+mx)*gx - mx, so the horizontal slack is
                    // w - that = w - pitchX*gx + mx. Right shifts by the full slack, center by half. The
                    // square-aspect item size (bs, height-driven for a wide-short badges box) usually
                    // leaves the grid far narrower than w, so without this a center/right cluster sat at
                    // the element's left edge. (2) A per-(last)-row offset by the empty columns, so a
                    // partial row is itself right/center-aligned within the grid (ES-DE's last-row /
                    // non-full-row comp). Both are additive, mirroring FlexboxComponent.cpp.
                    float base = (float)w - pitchX * gx + mx;
                    if (align == "right")       bx += roundf(base);
                    else if (align == "center") bx += roundf(base * 0.5f);
                    int itemsThisRow = std::min(perLine, (int)active.size() - row * perLine);
                    float empties = (float)((int)gx - itemsThisRow);
                    if (align == "right")       bx += empties * pitchX;
                    else if (align == "center") bx += empties * pitchX * 0.5f;
                }
                // Base icon: the theme's customBadgeIcon or the bundled ES-DE default.
                std::string iconPath = e->getPath(("customBadgeIcon:" + active[i].slot).c_str());
                if (iconPath.empty())
                    iconPath = "/data/system/nano_esde_assets/badges/badge_" + active[i].slot + ".svg";
                EsdeSvg a = esdeArtTex(iconPath, (int)bs, (int)bs);
                if (a.tex) drawIconTex(a.tex, bx + (bs - a.w) * 0.5f, by + (bs - a.h) * 0.5f,
                                       (float)a.w, (float)a.h, bc[0], bc[1], bc[2], bc[3]);
                // Controller overlay: the specific controller icon, scaled by controllerSize, centred.
                if (active[i].slot == "controller" && !active[i].controller.empty()) {
                    std::string cp = "/data/system/nano_esde_assets/controllers/" + active[i].controller + ".svg";
                    if (access(cp.c_str(), R_OK) != 0)
                        cp = "/data/system/nano_esde_assets/controllers/unknown.svg";
                    int os = (int)roundf(bs * ctrlSize); if (os < 1) os = 1;
                    EsdeSvg ov = esdeArtTex(cp, os, os);
                    if (ov.tex) drawIconTex(ov.tex, bx + (bs - ov.w) * 0.5f, by + (bs - ov.h) * 0.5f,
                                            (float)ov.w, (float)ov.h, cc[0], cc[1], cc[2], cc[3]);
                }
            }
        }
        // gamelistinfo / animation: follow-ups (see docs/THEME_ENGINE.md support matrix).
    }

    // ES-DE's SystemView and GamelistView ALWAYS add a default clock and a default systemstatus
    // when the theme declares none (SystemView.cpp:779, GamelistView.cpp:381). The default status
    // cluster (top-right Bluetooth / Wi-Fi / battery icon + battery %) is gated only by hardware
    // presence and the SystemStatus* settings, all of which default ON, so a theme like SimpleMenu
    // that omits <systemstatus> still shows the status bar on real ES-DE. The default clock is gated
    // by DisplayClock, which defaults OFF (mirrored by esdeShowClock). Synthesize the upstream
    // default element and route it through the same draw path so every theme matches the control.
    bool viewHasStatus = false, viewHasClock = false;
    for (const nanoesde::Element* e : view->drawOrder) {
        if (e->type == "systemstatus") viewHasStatus = true;
        else if (e->type == "clock")   viewHasClock = true;
    }
    auto esdeDefPair = [](nanoesde::Element& el, const char* k, float a, float b) {
        nanoesde::Prop p; p.type = nanoesde::PT_NORMALIZED_PAIR; p.v[0] = a; p.v[1] = b; el.props[k] = p;
    };
    auto esdeDefF = [](nanoesde::Element& el, const char* k, float a) {
        nanoesde::Prop p; p.type = nanoesde::PT_FLOAT; p.f = a; el.props[k] = p;
    };
    if (!viewHasStatus) {
        // SystemStatusComponent defaults (SystemStatusComponent.cpp:164-192): pos {0.982,0.016},
        // origin {1,0}, height round(0.035*screenHeight in landscape), textRelativeScale 0.9, white.
        nanoesde::Element def; def.type = "systemstatus"; def.name = "systemstatus_default";
        esdeDefPair(def, "pos", 0.982f, 0.016f);
        esdeDefPair(def, "origin", 1.0f, 0.0f);
        esdeDefF(def, "height", 0.035f);
        esdeDefF(def, "textRelativeScale", 0.9f);
        esdeDrawSystemStatus(&def);
    }
    if (!viewHasClock && esdeShowClock) {
        // DateTimeComponent clock defaults (DateTimeComponent.cpp:191-204): pos {0.018,0.016},
        // origin {0,0}, FONT_SIZE_SMALL (0.035*min(W,H)), white, format "%H:%M".
        time_t now = time(nullptr); struct tm lt; localtime_r(&now, &lt);
        char buf[32] = {0}; strftime(buf, sizeof(buf), "%H:%M", &lt);
        float sc = 0.035f * (float)std::min(mWidth, mHeight) / (float)FONT_CHAR_H;
        drawText(buf, 0.018f * mWidth, 0.016f * mHeight, sc, 1, 1, 1, 1, -1);
    }
    mTextOutlineMode = esdePrevOutline;
}

// Rounded ES-DE plate behind a content box [cx,cy,cw,ch] (the tight text/icon box the caller lays
// out). Matches the plate math shared by HelpComponent / DateTimeComponent / SystemStatusComponent:
// backgroundHorizontalPadding / backgroundVerticalPadding are NORMALIZED_PAIRs of (near, far) sides -
// the box is translated top-left by the near pad and grown by (near + far), horizontal pads scaled by
// screen width and vertical by screen height, each clamped to [0,1]; backgroundCornerRadius clamps to
// [0,0.5] of the width. ES-DE has no height-proportional padding, so the plate hugs the text when the
// theme sets no padding. No-op when backgroundColor is absent/transparent.
void NanoMenu::esdeDrawPlate(const nanoesde::Element* e, float cx, float cy, float cw, float ch) {
    float bg[4];
    if (!e->getColor("backgroundColor", bg)) return;
    bg[3] *= e->getF("opacity", 1.0f);
    if (bg[3] <= 0.0f) return;
    auto clamp01 = [](float v) { return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v); };
    float hNear = clamp01(e->getPair("backgroundHorizontalPadding", 0, 0.0f)) * mWidth;
    float hFar  = clamp01(e->getPair("backgroundHorizontalPadding", 1, 0.0f)) * mWidth;
    float vNear = clamp01(e->getPair("backgroundVerticalPadding", 0, 0.0f)) * mHeight;
    float vFar  = clamp01(e->getPair("backgroundVerticalPadding", 1, 0.0f)) * mHeight;
    float rv = e->getF("backgroundCornerRadius", 0.0f);
    rv = rv < 0.0f ? 0.0f : (rv > 0.5f ? 0.5f : rv);
    drawRoundedRect(cx - hNear, cy - vNear, cw + hNear + hFar, ch + vNear + vFar,
                    rv * mWidth, bg[0], bg[1], bg[2], bg[3]);
}

// Map a help prompt id to the ES-DE customButtonIcon "button" key for the active (XBOX) controller
// set, so a theme's <customButtonIcon button="..."> can override the built-in glyph (Analogue OS
// Menu ships its own minimalist help icons). Empty if the id has no override key.
static std::string esdeHelpCustomKey(const std::string& id) {
    static const std::map<std::string, std::string> m = {
        {"a", "button_a_XBOX"}, {"b", "button_b_XBOX"}, {"x", "button_x_XBOX"},
        {"y", "button_y_XBOX"}, {"start", "button_start_XBOX"}, {"back", "button_back_XBOX"},
        {"left/right", "dpad_leftright"}, {"up/down", "dpad_updown"},
        {"up/down/left/right", "dpad_all"}, {"lr", "button_lr"}, {"ltrt", "button_ltrt"},
        {"l", "button_l"}, {"r", "button_r"}, {"lt", "button_lt"}, {"rt", "button_rt"},
        {"thumbstickclick", "thumbstick_click"},
    };
    auto it = m.find(id);
    return it == m.end() ? std::string() : it->second;
}

// Absolute path to the ES-DE built-in help button graphic for a prompt id (XBOX/generic set,
// bundled under nano's shared ES-DE assets). Empty if the id has no icon.
std::string NanoMenu::esdeHelpIconPath(const std::string& id) {
    static const std::map<std::string, std::string> m = {
        {"a", "button_a_XBOX.svg"}, {"b", "button_b_XBOX.svg"},
        {"x", "button_x_XBOX.svg"}, {"y", "button_y_XBOX.svg"},
        {"start", "button_start_XBOX.svg"}, {"back", "button_back_XBOX.svg"},
        {"left/right", "dpad_leftright.svg"}, {"up/down", "dpad_updown.svg"},
        {"up/down/left/right", "dpad_all.svg"}, {"lr", "button_lr.svg"},
        {"ltrt", "button_ltrt.svg"}, {"l", "button_l.svg"}, {"r", "button_r.svg"},
        {"lt", "button_lt.svg"}, {"rt", "button_rt.svg"}, {"thumbstickclick", "thumbstick.svg"},
    };
    auto it = m.find(id);
    if (it == m.end()) return std::string();
    return std::string("/data/system/nano_esde_assets/help/") + it->second;
}

// The bottom helpsystem bar. Builds the view's prompt set, dedups by icon and sorts by ES-DE's
// fixed priority (Window::setHelpPrompts), then lays out icon+label entries centered on the
// element's pos/origin with a rounded background plate, matching HelpComponent 1:1.
void NanoMenu::esdeDrawHelp(const nanoesde::Element* e, bool gamelist,
                           const nanoesde::Element* primary, bool menuOverlayPass) {
    // Render only the helpsystem whose name matches the active view (help-system-view vs
    // help-gamelist-view; the shared "system,gamelist" view carries both into each view).
    const std::string& nm = e->name;
    bool forGamelist = nm.find("gamelist") != std::string::npos;
    bool forSystem = nm.find("system") != std::string::npos;
    if (gamelist && forSystem && !forGamelist) return;
    if (!gamelist && forGamelist && !forSystem) return;

    // When the options menu is open its help bar belongs to the top GUI and must draw OVER the
    // dimmed/blurred background (ES-DE's HelpComponent). This home element pass runs before
    // renderEsdeMenu lays that background down, so defer: cache the view-matched helpsystem
    // element here and let renderEsdeMenu redraw it on top (menuOverlayPass=true).
    const bool esdeMenuHelp = (mEsdeMenuActive || mEsdeMenuClosing);
    if (esdeMenuHelp && !menuOverlayPass) {
        mEsdeHelpElem = e; mEsdeHelpGamelist = gamelist; mEsdeHelpPrimary = primary;
        return;
    }

    // The view's full ES-DE prompt set (id,label), matching SystemView/GamelistView::
    // getHelpPrompts + ViewController's global start=menu, under the reference device settings
    // (QuickSystemSelect=leftrightshoulders -> "lr"/system, FavoritesAddButton on -> y/Favorites,
    // RandomEntryButton=games -> gamelist thumbstickclick/random, ScreensaverControls off). A
    // theme's <entries> list then selects which of these actually show (ABN trims the gamelist
    // bar to back,start,a = OPTIONS MENU SELECT); "all"/absent keeps every prompt.
    std::vector<std::pair<std::string, std::string>> prompts;
    // When the ES-DE options menu is open its help bar replaces the view's, exactly as ES-DE's
    // HelpComponent shows the top GUI's prompts. Build the menu prompts (per focused row) and render
    // them through this same glyph + helpsystem-style path so the menu legend matches the control.
    // (esdeMenuHelp was resolved above for the defer-to-overlay decision.)
    if (esdeMenuHelp) {
        esdeMenuBuildHelpPrompts(prompts);
    } else if (gamelist) {
        // QuickSystemSelect (leftrightshoulders): the left/right dpad drives quick system-select
        // only when the gamelist primary does not itself consume left/right - a vertical carousel
        // or textlist - in which case the "left/right" dpad icon shows; a horizontal carousel or a
        // grid uses left/right for its own navigation so the shoulder ("lr") icon shows instead.
        // Mirrors GamelistView::mLeftRightAvailable + GamelistBase::getQuickSystemSelectLeftButton.
        std::string sysIcon = "left/right";
        if (primary) {
            if (primary->type == "grid") sysIcon = "lr";
            else if (primary->type == "carousel") {
                std::string ct = primary->getS("type", std::string("horizontal"));
                if (ct != "vertical" && ct != "verticalWheel") sysIcon = "lr";
            }
        }
        prompts.push_back({sysIcon, "system"});
        prompts.push_back({"a", "select"});
        prompts.push_back({"b", "back"});
        prompts.push_back({"x", "view media"});
        prompts.push_back({"back", "options"});
        prompts.push_back({"thumbstickclick", "random"});
        prompts.push_back({"y", "Favorites"});
        prompts.push_back({"start", "menu"});
    } else {
        // The "choose" icon tracks the system view's primary: a vertical carousel or a textlist
        // navigates up/down, a horizontal carousel left/right, a grid all four directions.
        std::string chooseIcon = "left/right";
        if (primary) {
            if (primary->type == "grid") chooseIcon = "up/down/left/right";
            else if (primary->type == "textlist") chooseIcon = "up/down";
            else if (primary->type == "carousel") {
                std::string ct = primary->getS("type", std::string("horizontal"));
                if (ct == "vertical" || ct == "verticalWheel") chooseIcon = "up/down";
            }
        }
        prompts.push_back({chooseIcon, "choose"});
        prompts.push_back({"a", "select"});
        prompts.push_back({"start", "menu"});
    }

    // Apply the helpsystem's <entries> allow-list (icon ids, whitespace or comma separated). The
    // token "all" (or an empty/absent value) disables filtering. Matches HelpComponent::updateGrid.
    // The menu's own prompts are never theme-filtered (they belong to the menu GUI, not the view).
    if (!esdeMenuHelp) {
        std::string en = e->getS("entries", std::string());
        std::vector<std::string> toks;
        std::string cur;
        for (size_t i = 0; i <= en.size(); i++) {
            char ch = i < en.size() ? en[i] : ',';
            if (ch == ',' || isspace((unsigned char)ch)) { if (!cur.empty()) { toks.push_back(cur); cur.clear(); } }
            else cur.push_back((char)tolower((unsigned char)ch));
        }
        bool allowAll = toks.empty();
        for (auto& t : toks) if (t == "all") allowAll = true;
        if (!allowAll) {
            std::vector<std::pair<std::string, std::string>> f;
            for (auto& p : prompts) {
                bool ok = false;
                for (auto& t : toks) if (t == p.first) { ok = true; break; }
                if (ok) f.push_back(p);
            }
            prompts.swap(f);
        }
    }

    static const char* kPri[] = {"thumbstickclick", "lr", "ltrt", "up/down/left/right", "up/down",
                                 "up", "down", "left/right", "rt", "lt", "r", "l", "y", "x", "b",
                                 "a", "start", "back"};
    auto rank = [&](const std::string& id) {
        for (int i = 0; i < (int)(sizeof(kPri) / sizeof(kPri[0])); i++)
            if (id == kPri[i]) return i;
        return -1;
    };
    {  // dedup by icon (first wins), then (view bar only) sort by descending priority
        std::vector<std::pair<std::string, std::string>> dd;
        for (auto& p : prompts) {
            bool seen = false;
            for (auto& q : dd) if (q.first == p.first) { seen = true; break; }
            if (!seen) dd.push_back(p);
        }
        // The view/home bar reproduces the theme's per-system help ordering with this fixed
        // button-priority sort (verified against the control). The options menu instead uses ES-DE's
        // DEFAULT HelpComponent, which renders getHelpPrompts strictly in vector order, so leave the
        // menu prompts in the ES-DE order esdeMenuBuildHelpPrompts already emits and skip the sort.
        if (!esdeMenuHelp) {
            std::stable_sort(dd.begin(), dd.end(), [&](const std::pair<std::string, std::string>& a,
                                                       const std::pair<std::string, std::string>& b) {
                return rank(a.first) > rank(b.first);
            });
        }
        prompts.swap(dd);
    }
    if (prompts.empty()) return;

    float fs = e->getF("fontSize", 0.035f);
    float ers = std::min(3.0f, std::max(0.2f, e->getF("entryRelativeScale", 1.0f)));
    float fontDim = (float)std::min(mWidth, mHeight);   // font scales by the shorter dim (ES-DE)
    // ES-DE loads the help font at fontSize*entryRelativeScale (the label is entryRelativeScale
    // times the entry/icon height), so with ABN's 0.7 the prompt text is 30% smaller than the
    // icons. nano previously drew the label at the full fontSize, making the bar oversized.
    float glyphH = fs * ers * fontDim;
    float sc = glyphH / (float)FONT_CHAR_H;
    int face = e ? esdeFontFace(e->getPath("fontPath")) : -1;   // help labels in the theme font
    // ES-DE sizes each help icon to mLetterHeight = the help font's letter (cap) height * 1.25
    // (HelpComponent.cpp:187/643,665), NOT the raw font pixel size, so the icon tracks the cap height
    // of the actual help typeface rather than its full em. Measure the 'S' cap height of the help face
    // at the unscaled help font size and match it; fall back to the em box if the glyph is unavailable.
    float iconBox = fs * fontDim;                        // icon/entry height at the unscaled size
    {
        int hpx = (int)lroundf(fs * fontDim); if (hpx < 6) hpx = 6;
        const GlyphInfo* capS = ensureGlyph('S', hpx, face);
        if (capS && capS->bmpH > 0) iconBox = (float)capS->bmpH * 1.25f;
    }
    float entrySp = e->getF("entrySpacing", 0.00833f) * mWidth;
    float iconTextSp = e->getF("iconTextSpacing", 0.00416f) * mWidth;
    std::string lc = e->getS("letterCase", std::string("uppercase"));

    float iconC[4]; if (!e->getColor("iconColor", iconC)) { iconC[0]=iconC[1]=iconC[2]=0.47f; iconC[3]=1; }
    float textC[4]; if (!e->getColor("textColor", textC)) { textC[0]=textC[1]=textC[2]=0.47f; textC[3]=1; }
    float op = e->getF("opacity", 1.0f); iconC[3] *= op; textC[3] *= op;

    struct Ent { GLuint tex; float iw, ih; std::string label; float lw; };
    std::vector<Ent> ents; ents.reserve(prompts.size());
    float total = 0.0f;
    for (auto& p : prompts) {
        Ent en{}; en.tex = 0; en.iw = en.ih = 0;
        // A theme's <customButtonIcon> overrides the built-in glyph for this button (Analogue OS
        // Menu replaces the whole set with its own icons); fall back to the built-in otherwise.
        std::string path;
        std::string ckey = esdeHelpCustomKey(p.first);
        if (!ckey.empty()) path = e->getPath(("customButtonIcon:" + ckey).c_str());
        if (path.empty()) path = esdeHelpIconPath(p.first);
        if (!path.empty()) {
            EsdeSvg a = esdeArtTex(path, (int)iconBox, (int)iconBox);
            en.tex = a.tex; en.iw = (float)a.w; en.ih = (float)a.h;
        }
        en.label = p.second;
        esdeLetterCase(en.label, lc);
        en.lw = measureText(en.label.c_str(), sc, face);
        total += (en.tex ? en.iw + iconTextSp : 0.0f) + en.lw;
        ents.push_back(en);
    }
    if (ents.size() > 1) total += entrySp * (float)(ents.size() - 1);
    float rowH = iconBox > glyphH ? iconBox : glyphH;

    // ES-DE's default help position (HelpComponent.cpp:23-27) is the bottom-LEFT corner,
    // (0.012, 0.9515) landscape / (0.012, 0.975) portrait, origin (0,0) - a left-aligned bar. nano
    // defaulted pos.x to 0.5, centring/right-shifting the bar for every theme that omits the help
    // <pos> (carbon and most others), so their gamelist help ran off the right edge instead of
    // sitting left-aligned like the control. Match the upstream default; a theme that sets its own
    // pos/origin (ABN anchors bottom-right) still overrides it.
    float defHelpY = (mHeight > mWidth) ? 0.975f : 0.9515f;
    float ox = e->getPair("origin", 0, 0.0f), oy = e->getPair("origin", 1, 0.0f);
    float x0 = e->getPair("pos", 0, 0.012f) * mWidth - ox * total;
    float yTop = e->getPair("pos", 1, defHelpY) * mHeight - oy * rowH;

    esdeDrawPlate(e, x0, yTop, total, rowH);

    float cx = x0;
    for (auto& en : ents) {
        if (en.tex) {
            drawIconTex(en.tex, cx, yTop + (rowH - en.ih) * 0.5f, en.iw, en.ih,
                        iconC[0], iconC[1], iconC[2], iconC[3]);
            cx += en.iw + iconTextSp;
        }
        drawText(en.label.c_str(), cx, yTop + (rowH - glyphH) * 0.5f, sc,
                 textC[0], textC[1], textC[2], textC[3], face);
        cx += en.lw + entrySp;
    }
}

// The status cluster (wifi + battery), right-anchored per the element's pos/origin. Uses the
// theme's customIcon SVGs (ABN ships icon-wifi / icon-battery-*), tinted by the status color,
// with the battery percent as text and a rounded background plate.
void NanoMenu::esdeDrawSystemStatus(const nanoesde::Element* e) {
    // ES-DE's SystemStatusComponent default textRelativeScale is 0.9 (applyTheme), not 1.0.
    float rel = e->getF("textRelativeScale", 0.9f);
    float statusDim = (float)std::min(mWidth, mHeight);   // text + icons scale by the shorter dim
    // ES-DE sizes the status cluster to the element's height (ABN's systemStatusHeight, 0.0417
    // at the medium font size) with the text at height*textRelativeScale. The default is
    // round(0.035*scale) (nano used 0.03, undersizing it) and the theme height is clamped to
    // [0.01, 0.5] of the scale dimension (SystemStatusComponent.cpp:183/192); honor both.
    float hgt = std::min(0.5f, std::max(0.01f, e->getF("height", 0.035f)));
    float iconBox = statusDim * hgt;
    float glyphH = statusDim * hgt * (rel > 0.0f ? rel : 1.0f);
    float sc = glyphH / (float)FONT_CHAR_H;
    int statusFace = e ? esdeFontFace(e->getPath("fontPath")) : -1;   // status text in the theme font
    // ES-DE spaces the status entries (icons + battery %) by entrySpacing, a fraction of screen WIDTH
    // (default 0.005, clamped 0..0.04; SystemStatusComponent.cpp:37/304), NOT a fraction of the glyph
    // height. nano's glyphH*0.35 gap ran wider than the control; use the upstream width-relative spacing.
    float gap = std::min(0.04f, std::max(0.0f, e->getF("entrySpacing", 0.005f))) * mWidth;

    float col[4]; if (!e->getColor("color", col)) { col[0]=col[1]=col[2]=1; col[3]=1; }
    col[3] *= e->getF("opacity", 1.0f);

    // Icon source: the theme's customIcon if it sets one, else ES-DE's built-in default status
    // SVG (bundled with nano at .../nano_xmb/systemstatus/, /data override first), so a theme that
    // defines a systemstatus element but no customIcons (Canvas, Aura) shows the same bluetooth /
    // wifi / battery glyphs ES-DE draws instead of a bare percentage. `defName` is the ES-DE
    // resource basename (bluetooth, wifi, battery_charging/low/medium/high/full).
    auto iconFor = [&](const char* customKey, const char* defName) -> std::string {
        std::string c = e->getPath((std::string("customIcon:") + customKey).c_str());
        if (!c.empty()) return c;
        std::string dev = std::string("/data/system/nano_xmb/systemstatus/") + defName + ".svg";
        if (access(dev.c_str(), R_OK) == 0) return dev;
        return std::string("/system/etc/nano_xmb/systemstatus/") + defName + ".svg";
    };

    // Assemble items left-to-right in ES-DE's order: bluetooth, wifi, battery icon, battery percent.
    struct Item { GLuint tex; float w, h; std::string text; float tw; };
    std::vector<Item> items;
    float total = 0.0f;
    auto addIcon = [&](const std::string& path) {
        if (path.empty()) return;
        EsdeSvg a = esdeArtTex(path, (int)iconBox, (int)iconBox);
        if (!a.tex) return;
        Item it{}; it.tex = a.tex; it.w = (float)a.w; it.h = (float)a.h;
        items.push_back(it); total += it.w + gap;
    };
    // Radio state from the event-driven net bridge (same source as mWifiRadioOn), current in every
    // view - unlike mNdsBtOn, which only the DSi status path refreshes via pollNdsStatus().
    if (mBtLevel >= kBtLevel_On) addIcon(iconFor("icon_bluetooth", "bluetooth"));
    if (mWifiRadioOn)            addIcon(iconFor("icon_wifi", "wifi"));
    {
        // ES-DE SystemStatusComponent capacity buckets: <=25 low, <=60 medium, <=90 high,
        // >90 full (charging overrides all).
        const char* bat = mBatteryCharging ? "battery_charging"
                        : mBatteryPercent > 90 ? "battery_full"
                        : mBatteryPercent > 60 ? "battery_high"
                        : mBatteryPercent > 25 ? "battery_medium" : "battery_low";
        addIcon(iconFor((std::string("icon_") + bat).c_str(), bat));
    }
    if (mBatteryPercent >= 0) {
        // ES-DE sizes the battery-percentage cell to "100%" (the widest it will ever show) and draws
        // the actual value LEFT-aligned inside it (SystemStatusComponent.cpp:129-137, ALIGN_LEFT), so a
        // shorter reading like "26%" leaves a trailing gap before the right anchor. nano measured the
        // live text and filled to the anchor, seating the whole cluster ~1 digit too far right. Reserve
        // the "100%" advance and left-align the live text to match.
        Item it{}; it.tex = 0; it.text = std::to_string(mBatteryPercent) + "%";
        it.tw = measureText("100%", sc, statusFace);
        items.push_back(it); total += it.tw;
    }
    if (items.empty()) return;
    // ES-DE applies entrySpacing only BETWEEN entries (SystemStatusComponent.cpp:117 skips the
    // last entry), so the final entry must not add a trailing gap. The battery-percent text is
    // always last when present and already adds none; the only case that over-counts is a
    // trailing icon (no battery reading), where the extra gap would shift the right-anchored
    // cluster left by one entrySpacing. Drop that trailing gap to match the control's anchor.
    if (items.back().tex) total -= gap;

    float rowH = iconBox > glyphH ? iconBox : glyphH;
    // ES-DE's SystemStatusComponent default origin is {1.0, 0.0} (right-anchored, TOP-anchored;
    // SystemStatusComponent.cpp:166). A 0.5 y-origin centred the cluster on the near-top pos.y and
    // clipped the icons off the top of the screen.
    // ES-DE's default position is {0.982, 0.016} of the screen (SystemStatusComponent.cpp:164);
    // nano used {0.96, 0.06}, seating the cluster too far in and too low for a theme that omits pos.
    float ox = e->getPair("origin", 0, 1.0f), oy = e->getPair("origin", 1, 0.0f);
    float x0 = e->getPair("pos", 0, 0.982f) * mWidth - ox * total;
    float yTop = e->getPair("pos", 1, 0.016f) * mHeight - oy * rowH;

    esdeDrawPlate(e, x0, yTop, total, rowH);

    float cx = x0;
    for (auto& it : items) {
        if (it.tex) {
            drawIconTex(it.tex, cx, yTop + (rowH - it.h) * 0.5f, it.w, it.h,
                        col[0], col[1], col[2], col[3]);
            cx += it.w + gap;
        } else {
            drawText(it.text.c_str(), cx, yTop + (rowH - glyphH) * 0.5f, sc,
                     col[0], col[1], col[2], col[3], statusFace);
            cx += it.tw;
        }
    }
}

// The bottom panel (dual-screen devices): MVP paints a plain backdrop so it is not the
// XMB wave. A themed secondary view is a follow-up.
void NanoMenu::renderEsdeSecondary() {
    // Over a live app (in-game scrim) leave the dispatcher's app-dim scrim clear untouched instead of
    // repainting an opaque backdrop, so the running app shows through the bottom panel too (mirrors
    // renderMinimaSecondary). The opaque fill returns once back at the launcher (mOverlayWallpaper).
    if (mOverlayMode && !mOverlayWallpaper) return;
    drawQuad(0, 0, (float)mWidth, (float)mHeight, 0.05f, 0.06f, 0.08f, 1.0f);
}

// ---------------------------------------------------------------- navigation

// True if the loaded gamelist view's primary element is a grid (cached; -1 = unknown). Grids
// need column-aware up/down; textlists move one row.
const nanoesde::Element* NanoMenu::esdeChosenPrimary(const nanoesde::View* v) {
    const nanoesde::Element* chosen = nullptr;
    if (v) for (const auto& kv : v->elements) {
        const nanoesde::Element& el = kv.second;
        if (el.type == "textlist" || el.type == "carousel" || el.type == "grid")
            if (!chosen || el.name < chosen->name) chosen = &el;
    }
    return chosen;
}

bool NanoMenu::esdeGamelistIsGrid() {
    if (mEsdeGamelistGrid < 0) {
        // Grid nav only when the CHOSEN primary is a grid (a variant may layer a grid over the
        // base textlist; the render keeps just one, so the nav model must agree).
        const nanoesde::View* v = mEsdeDoc.valid() ? mEsdeDoc.view("gamelist") : nullptr;
        const nanoesde::Element* p = esdeChosenPrimary(v);
        mEsdeGamelistGrid = (p && p->type == "grid") ? 1 : 0;
    }
    return mEsdeGamelistGrid == 1;
}

// dx: system change (system view) / unused in gamelist; dy: row move. Kept simple and
// self-contained; launching reuses the shared launchXmbGame() path.
void NanoMenu::esdeNav(int dx, int dy) {
    if (mEsdeXsActive) return;                 // ignore navigation while a view transition plays
    esdeRebuildSysList();
    if (mEsdeSysList.empty()) return;
    const int nSys = (int)mEsdeSysList.size();
    auto wrap = [](int v, int n) { return ((v % n) + n) % n; };
    if (!mEsdeInGamelist) {
        // A carousel/textlist system view scrolls by one on either axis; a system-view GRID
        // (adroit) moves a whole row on up/down and one cell on left/right. Follow the chosen
        // primary so a grid layered over a base textlist still navigates as a grid.
        const nanoesde::View* sv = mEsdeDoc.valid() ? mEsdeDoc.view("system") : nullptr;
        const nanoesde::Element* svp = esdeChosenPrimary(sv);
        bool sysGrid = svp && svp->type == "grid";
        int step = (sysGrid && dy != 0) ? (mEsdeGridColumns > 0 ? mEsdeGridColumns : 1) * dy : dx + dy;
        if (step) { mEsdeSysSel = wrap(mEsdeSysSel + step, nSys); mEsdeGameSel = 0;
                    esdeSfx(0); }              // systembrowse: system carousel/list move
    } else if (dx) {                           // gamelist L/R
        if (esdeGamelistIsGrid()) {
            // A grid needs L/R to move the cursor one item (with wrap, ES-DE List::listInput):
            // up/down only jump whole rows, so this is the only way to reach items within a row.
            // Switching systems from a grid is via BACK to the system view.
            int n = (int)mXmbSystems[mEsdeSysList[mEsdeSysSel]].displayNames.size();
            if (n > 0) { mEsdeGameSel = wrap(mEsdeGameSel + dx, n); esdeSfx(4); }  // scroll
        } else {
            // A list flips systems while staying in the gamelist (a nano convenience).
            mEsdeSysSel = wrap(mEsdeSysSel + dx, nSys);
            mEsdeGameSel = 0;
            // snap the grid to the new system instead of animating from the old cursor/scroll
            mEsdeGridCursor = -1; mEsdeGridScroll = 0.0f; mEsdeGridAnimDur = 0.0f; mEsdeGridTransFactor = 1.0f;
            esdeSfx(0);                         // systembrowse: system flip
        }
    } else if (dy) {                           // gamelist: U/D scrolls games (grid = whole-row jump)
        int n = (int)mXmbSystems[mEsdeSysList[mEsdeSysSel]].displayNames.size();
        if (n > 0) {
            int prevSel = mEsdeGameSel;
            if (esdeGamelistIsGrid()) {
                // ES-DE GridComponent guards vertical moves by row membership: Up in the
                // first row and Down in the (possibly partial) last row are no-ops, so the
                // highlight never jumps sideways to a different column at an edge.
                int cols = mEsdeGridColumns > 0 ? mEsdeGridColumns : 1;
                if (dy < 0) {
                    if (mEsdeGameSel >= cols) mEsdeGameSel -= cols;
                } else {
                    int mod = n % cols;
                    int lastRowStart = n - (mod == 0 ? cols : mod);
                    if (mEsdeGameSel < lastRowStart) mEsdeGameSel += cols;
                }
            } else {
                mEsdeGameSel = wrap(mEsdeGameSel + dy, n);
            }
            if (mEsdeGameSel != prevSel) esdeSfx(4);   // scroll: gamelist cursor moved
        }
    }
    mDisplayDirty = true;
}

bool NanoMenu::esdeSelect() {
    if (mEsdeXsActive) return true;            // ignore input while a view transition plays
    esdeRebuildSysList();
    if (mEsdeSysList.empty()) return true;
    if (!mEsdeInGamelist) {
        esdeBeginTransition(true);             // system -> gamelist (cut / slide / fade per the theme)
        esdeSfx(2);                            // select: system -> gamelist
        return true;
    }
    const auto& sys = mXmbSystems[mEsdeSysList[mEsdeSysSel]];
    if (mEsdeGameSel >= 0 && mEsdeGameSel < (int)sys.roms.size()) {
        mXmbSystemIndex = mEsdeSysList[mEsdeSysSel];
        mXmbGameIndex = mEsdeGameSel;
        esdeSfx(6);                            // launch
        launchXmbGame();
    }
    return true;
}

bool NanoMenu::esdeBack() {
    if (mEsdeXsActive) return true;            // ignore input while a view transition plays
    if (mEsdeInGamelist) { esdeBeginTransition(false); esdeSfx(3); return true; }
    return false;   // at system root: let the caller handle (e.g. nothing / exit gesture)
}

// Apply the actual system<->gamelist state change. On entering a gamelist the grid cursor/scroll
// are snapped so it does not ease in from the previous system's state.
void NanoMenu::esdeApplyViewSwap(bool toGamelist) {
    mEsdeInGamelist = toGamelist;
    if (toGamelist) {
        mEsdeGameSel = 0;
        mEsdeGridCursor = -1; mEsdeGridScroll = 0.0f; mEsdeGridAnimDur = 0.0f; mEsdeGridTransFactor = 1.0f;
    }
    mDisplayDirty = true;
}

// Start a system<->gamelist transition using the theme's resolved animation (an optional
// persist.gammaos.nano.esde.transition override forces instant/slide/fade). INSTANT applies the
// swap immediately (the historical cut); SLIDE swaps now and composites both views while the camera
// pans; FADE defers the swap to the fully-black midpoint. See docs/theme-engine/VIEW_TRANSITIONS.md.
void NanoMenu::esdeBeginTransition(bool toGamelist) {
    nanoesde::XsAnim anim =
        toGamelist ? mEsdeDoc.xsSystemToGamelist() : mEsdeDoc.xsGamelistToSystem();
    char ov[PROPERTY_VALUE_MAX] = {0};
    property_get("persist.gammaos.nano.esde.transition", ov, "automatic");
    std::string o = ov;
    if (o == "instant")    anim = nanoesde::XsAnim::INSTANT;
    else if (o == "slide") anim = nanoesde::XsAnim::SLIDE;
    else if (o == "fade")  anim = nanoesde::XsAnim::FADE;
    if (anim == nanoesde::XsAnim::INSTANT || mEsdeSysList.empty()) {
        esdeApplyViewSwap(toGamelist);
        return;
    }
    mEsdeXsActive = true;
    mEsdeXsSlide = (anim == nanoesde::XsAnim::SLIDE);
    mEsdeXsToGamelist = toGamelist;
    mEsdeXsStart = (int64_t)uptimeMillis();
    mEsdeXsSwapped = false;
    mEsdeXsSnapped = false;
    if (mEsdeXsSlide) {
        // The slide renders both views; make the incoming one current straight away so its live
        // state (scroll, cursor) is correct as it slides in. The fade instead swaps at the midpoint.
        esdeApplyViewSwap(toGamelist);
        mEsdeXsSwapped = true;
    }
    mDisplayDirty = true;
}

// Off-screen target for one composited view during a slide. Allocates the RGBA texture each call
// (cheap for a one-shot ~400ms transition and keeps A and B independent).
static bool esdeEnsureFbo(GLuint* fbo, GLuint* tex, int w, int h) {
    if (!*fbo) glGenFramebuffers(1, fbo);
    if (!*tex) glGenTextures(1, tex);
    glBindTexture(GL_TEXTURE_2D, *tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindFramebuffer(GL_FRAMEBUFFER, *fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, *tex, 0);
    return glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
}

// Draw a rotated <text> element. nano's glyph renderer lays out only axis-aligned quads, so a text
// with a non-zero <rotation> is first rendered upright into an offscreen FBO, then that texture is
// blitted rotated about (pivotX, pivotY) - the element's rotationOrigin point - through drawIconTex,
// which already tumbles a quad about its centre. Only reached for rotation != 0, so ordinary text is
// untouched. Used by CarAlt's -90 game-count gauges (two thin vertical labels that otherwise draw
// horizontal and overlap). Text is measured with the same face/scale as the normal path so the
// rotated result matches the layout width.
void NanoMenu::esdeDrawRotatedText(const std::string& s, float pivotX, float pivotY, float sc,
                                   int face, const float col[4], float rotDeg) {
    if (s.empty() || sc <= 0.0f) return;
    float tw = measureText(s.c_str(), sc, face);
    float th = sc * (float)FONT_CHAR_H;
    const int pad = 6;                                   // room for the glyph outline + ascenders
    int fw = (int)ceilf(tw) + pad * 2;
    int fh = (int)ceilf(th) + pad * 2;
    if (fw < 2 || fh < 2 || fw > 2048 || fh > 2048) return;
    // Snapshot the CURRENT target (the on-screen framebuffer / viewport / scissor) BEFORE
    // esdeEnsureFbo binds the scratch FBO, or the "restore" below would rebind the scratch FBO and
    // every later element would render off-screen (a black frame).
    GLint prevFbo = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLint vp[4]; glGetIntegerv(GL_VIEWPORT, vp);
    GLboolean sciss = glIsEnabled(GL_SCISSOR_TEST);
    if (!esdeEnsureFbo(&mEsdeTextRotFbo, &mEsdeTextRotTex, fw, fh)) return;
    mEsdeTextRotW = fw; mEsdeTextRotH = fh;
    glBindFramebuffer(GL_FRAMEBUFFER, mEsdeTextRotFbo);
    glViewport(0, 0, fw, fh);
    if (sciss) glDisable(GL_SCISSOR_TEST);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f); glClear(GL_COLOR_BUFFER_BIT);
    // drawText derives its NDC from mWidth/mHeight; point them at the FBO so the text fills it.
    int savedW = mWidth, savedH = mHeight; mWidth = fw; mHeight = fh;
    drawText(s.c_str(), (float)pad, (float)pad, sc, col[0], col[1], col[2], col[3], face);
    mWidth = savedW; mHeight = savedH;
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glViewport(vp[0], vp[1], vp[2], vp[3]);
    if (sciss) glEnable(GL_SCISSOR_TEST);
    // The FBO holds premultiplied-alpha text (glyphs blended over a transparent target), so blit it
    // with premultiplied "over" or the anti-aliased edges get multiplied by coverage a second time.
    // flipV because an FBO has the GL bottom-left origin; rotate about the rect centre = the pivot.
    float rotRad = rotDeg * 0.017453293f;
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    drawIconTex(mEsdeTextRotTex, pivotX - fw * 0.5f, pivotY - fh * 0.5f, (float)fw, (float)fh,
                1.0f, 1.0f, 1.0f, col[3], rotRad, /*flipV=*/true, 0.0f, 0.0f, 1.0f, 1.0f);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}

// renderEsde() wrapped with the active view transition. No transition -> straight passthrough.
void NanoMenu::renderEsdeHome() {
    // Over a live app (in-game scrim), the slide transition captures each view into an offscreen FBO
    // cleared to OPAQUE black and blits it full-screen, and the fade draws a black quad - both are
    // opaque by construction and would re-occlude the app. Cut straight to the scrim-aware renderEsde
    // (which skips the opaque background) so the app keeps showing through; the animated transition
    // resumes at the launcher (mOverlayWallpaper true).
    if (mOverlayMode && !mOverlayWallpaper) {
        mEsdeXsActive = false; mEsdeWantsFastFrame = false; renderEsde();
        return;
    }
    if (!mEsdeXsActive) {
        mEsdeWantsFastFrame = false; renderEsde();
        return;
    }
    const int64_t now = (int64_t)uptimeMillis();
    const int64_t t = now - mEsdeXsStart;

    if (!mEsdeXsSlide) {
        // FADE: 120ms out (opacity 0->1) / 200ms black hold / 120ms in, view swapped at the black
        // point (ViewController::playViewTransition FADE_DURATION/FADE_WAIT).
        const int64_t OUT = 120, HOLD = 200, IN = 120, TOTAL = OUT + HOLD + IN;
        if (!mEsdeXsSwapped && t >= OUT) { esdeApplyViewSwap(mEsdeXsToGamelist); mEsdeXsSwapped = true; }
        float op;
        if (t < OUT)             op = (float)t / (float)OUT;
        else if (t < OUT + HOLD) op = 1.0f;
        else if (t < TOTAL)      op = 1.0f - (float)(t - OUT - HOLD) / (float)IN;
        else                     { op = 0.0f; mEsdeXsActive = false; }
        renderEsde();
        if (op > 0.001f) drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, op);
        mDisplayDirty = true;
        return;
    }

    // SLIDE: a 400ms ease-out-cubic vertical camera pan. Both views are static for the duration (the
    // selection is fixed), so capture each to its own FBO ONCE and then only composite the two cached
    // textures per frame - this keeps the per-frame cost to two textured quads instead of re-rendering
    // both views every frame (important on A133-class GPUs). mEsdeInGamelist already holds the incoming
    // view; mEsdeForceView lets renderEsde draw either from the stored selection.
    if (!mEsdeXsSnapped) {
        GLint prevFbo = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
        bool ok = esdeEnsureFbo(&mEsdeXsFboA, &mEsdeXsTexA, mWidth, mHeight);
        if (ok) {
            glBindFramebuffer(GL_FRAMEBUFFER, mEsdeXsFboA);
            glViewport(0, 0, mWidth, mHeight);
            glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
            mEsdeForceView = 1; renderEsde();                  // system view -> A
            ok = esdeEnsureFbo(&mEsdeXsFboB, &mEsdeXsTexB, mWidth, mHeight);
        }
        if (ok) {
            glBindFramebuffer(GL_FRAMEBUFFER, mEsdeXsFboB);
            glViewport(0, 0, mWidth, mHeight);
            glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
            mEsdeForceView = 2; renderEsde();                  // gamelist view -> B
        }
        mEsdeForceView = 0;
        glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
        glViewport(0, 0, mWidth, mHeight);
        if (!ok) { renderEsde(); mEsdeXsActive = false; return; }   // FBO failure -> safe cut
        mEsdeXsSnapped = true;
    }
    float lin = (float)t / 400.0f; if (lin < 0.0f) lin = 0.0f; if (lin > 1.0f) lin = 1.0f;
    float p = (lin - 1.0f) * (lin - 1.0f) * (lin - 1.0f) + 1.0f;   // ease-out cubic
    const float H = (float)mHeight;
    // Top-left y of each full-screen view during the vertical pan. sys->gl: system exits up
    // (-p*H), gamelist enters from the bottom ((1-p)*H); reverse for gl->sys. FBO textures are
    // bottom-up vs nano's top-down draw, so drawIconTex samples them with flipV.
    float sysY, glY;
    if (mEsdeXsToGamelist) { sysY = -p * H;          glY = (1.0f - p) * H; }
    else                   { sysY = -(1.0f - p) * H; glY = p * H; }
    drawIconTex(mEsdeXsTexA, 0.0f, sysY, (float)mWidth, H, 1, 1, 1, 1, 0.0f, true);   // system
    drawIconTex(mEsdeXsTexB, 0.0f, glY,  (float)mWidth, H, 1, 1, 1, 1, 0.0f, true);   // gamelist
    if (lin >= 1.0f) mEsdeXsActive = false;
    mDisplayDirty = true;
}

}  // namespace android
