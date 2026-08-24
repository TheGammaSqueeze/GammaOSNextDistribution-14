// GammaOS Nano - ES-DE start menu (the options menu opened with the Start/menu button when
// the ES-DE home theme is active). Additive and gated on mEsdeTheme; inert for XMB/DSi/Minima.
//
// Mirrors ES-DE's GuiMenu UI-settings subset in ES-DE's own row order: THEME, THEME VARIANT,
// THEME COLOR SCHEME, THEME FONT SIZE, THEME ASPECT RATIO. Each row shows the current value
// (upper-cased like ES-DE's menu); Left/Right cycle it in
// place, A opens a full-list picker, and choosing a value writes the persist.gammaos.nano.esde.*
// prop and live-reloads the theme through ensureEsdeTheme(). The panel geometry is expressed in
// fractions of the screen so it scales to any panel, matching ES-DE's MenuComponent.
#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoEsdeTheme.h"
#include "NanoMenuShaders.h"   // FONT_CHAR_H
#include "NanoJson.h"          // njson: themes.json parsing

#include <cutils/properties.h>
#include <utils/SystemClock.h>
#include <log/log.h>

#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/wait.h>

#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace android {

// The five ES-DE theme-option rows on the root page. Kept in one table so the renderer and the
// input handlers agree on order, labels and the prop each row writes.
struct EsdeMenuRowDef { const char* label; const char* prop; };
// Row order matches ES-DE's UI Settings menu: theme, variant, colour scheme, font size, aspect
// ratio (ES-DE also has Theme Transitions and Theme Language below these, which nano does not
// implement yet).
static const EsdeMenuRowDef kEsdeMenuRows[] = {
    {"THEME",              "persist.gammaos.nano.esde.themeset"},
    {"THEME VARIANT",      "persist.gammaos.nano.esde.variant"},
    {"THEME COLOR SCHEME", "persist.gammaos.nano.esde.colorscheme"},
    {"THEME FONT SIZE",    "persist.gammaos.nano.esde.fontsize"},
    {"THEME ASPECT RATIO", "persist.gammaos.nano.esde.aspectratio"},
};
static const int kEsdeMenuRowCount = (int)(sizeof(kEsdeMenuRows) / sizeof(kEsdeMenuRows[0]));

// Below the theme-option rows the root page carries two nano action rows: APPLICATIONS (opens an
// app launcher, A only) and NANO SETTINGS (hands off to the XMB menu stack, A only). Row 0 is the
// theme downloader. Naming the indices keeps the renderer and the input handlers in agreement
// instead of scattering raw +1/+2 offsets (which broke every time a row was inserted).
static const int kEsdeRowApps         = kEsdeMenuRowCount + 1;
static const int kEsdeRowNanoSettings = kEsdeMenuRowCount + 2;
static const int kEsdeRootRows        = kEsdeMenuRowCount + 3;

// ES-DE displays an aspect ratio the same as its key except that a "<ratio>_vertical" key is
// shown as "<ratio> vertical" (see ThemeData::sSupportedAspectRatios). Mirror that so the menu
// reads "5:3 vertical" rather than the raw "5:3_vertical".
static std::string esdeAspectDisplay(const std::string& key) {
    const std::string suf = "_vertical";
    if (key.size() > suf.size() && key.compare(key.size() - suf.size(), suf.size(), suf) == 0)
        return key.substr(0, key.size() - suf.size()) + " vertical";
    return key;
}

// ES-DE's menu renders every option value in upper case; the theme name additionally shows its
// directory hyphens as spaces ("art-book-next" -> "ART BOOK NEXT"). This formats the DISPLAY string
// only - the raw value (opts.first) is left intact for prop matching.
static std::string esdeMenuDisplay(std::string s, bool themeName) {
    if (themeName)
        for (auto& c : s) if (c == '-') c = ' ';
    for (auto& c : s) c = toupper((unsigned char)c);
    return s;
}

// ES-DE spells out the two extreme font-size keys in its menus (ThemeData sSupportedFontSizes:
// x-large -> "extra large", x-small -> "extra small"); every other key shows verbatim. Mirror
// that mapping before upper-casing so the row reads "EXTRA LARGE" rather than "X-LARGE".
static std::string esdeFontSizeDisplay(const std::string& key) {
    if (key == "x-large") return "EXTRA LARGE";
    if (key == "x-small") return "EXTRA SMALL";
    return esdeMenuDisplay(key, false);
}

// Build the option list (value + display label) and current index for a root row. Values come
// from the parsed capabilities of the active set (variants/colorSchemes/fontSizes) or, for the
// THEME row, the enumerated installed sets. Returns the current selected index (>=0).
int NanoMenu::esdeMenuOptionCtx(int row, std::string& label, std::string& prop,
                                std::vector<std::pair<std::string, std::string>>& opts) {
    opts.clear();
    if (row < 0 || row >= kEsdeMenuRowCount) { label.clear(); prop.clear(); return 0; }
    label = kEsdeMenuRows[row].label;
    prop = kEsdeMenuRows[row].prop;
    const nanoesde::Capabilities& caps = mEsdeDoc.capabilities();

    if (row == 0) {                                   // THEME: installed sets
        // ES-DE labels a set by its <themeName> ("Slate"), not the directory ("slate-es-de").
        for (auto& s : mEsdeInstalled) {
            const std::string& tn = s.caps.themeName;
            opts.push_back({s.name, tn.empty() ? esdeMenuDisplay(s.name, true)
                                               : esdeMenuDisplay(tn, false)});
        }
    } else if (row == 1) {                            // VARIANT
        // ES-DE lists only selectable variants; trigger-only override variants (selectable=false)
        // never appear in the menu (ThemeData parseThemeCapabilities / GuiMenu).
        for (auto& v : caps.variants) {
            if (!v.selectable) continue;
            opts.push_back({v.name, esdeMenuDisplay(v.label.empty() ? v.name : v.label, false)});
        }
    } else if (row == 2) {                            // COLOR SCHEME
        for (auto& c : caps.colorSchemes)
            opts.push_back({c.name, esdeMenuDisplay(c.label.empty() ? c.name : c.label, false)});
    } else if (row == 3) {                            // FONT SIZE
        for (auto& f : caps.fontSizes) opts.push_back({f, esdeFontSizeDisplay(f)});
    } else if (row == 4) {                            // ASPECT RATIO ("automatic" + declared)
        opts.push_back({"automatic", "AUTOMATIC"});
        for (auto& a : caps.aspectRatios) {
            if (a == "automatic") continue;           // de-dupe: a theme may also declare it
            opts.push_back({a, esdeMenuDisplay(esdeAspectDisplay(a), false)});
        }
    }

    // Current value from the prop (THEME uses the loaded set name).
    std::string cur;
    if (row == 0) {
        cur = mEsdeSetName;
    } else {
        char buf[PROPERTY_VALUE_MAX] = {0};
        property_get(prop.c_str(), buf, row == 4 ? "automatic" : "");
        cur = buf;
    }
    for (int i = 0; i < (int)opts.size(); i++)
        if (opts[i].first == cur) return i;
    return 0;
}

// Enumerate installed theme sets (a dir under a theme root with a capabilities.xml). Data root
// wins over the system root. Cheap (one XML per set via parseCapabilities); run on menu open and
// after an install, never per frame.
void NanoMenu::esdeMenuEnumerateInstalled() {
    mEsdeInstalled.clear();
    static const char* roots[] = {"/data/system/nano_esde_themes", "/system/etc/nano_esde_themes", "/storage/emulated/0/ES-DE/themes"};
    for (const char* root : roots) {
        DIR* d = opendir(root);
        if (!d) continue;
        for (dirent* e = readdir(d); e; e = readdir(d)) {
            if (e->d_name[0] == '.') continue;
            std::string dir = std::string(root) + "/" + e->d_name;
            if (access((dir + "/capabilities.xml").c_str(), F_OK) != 0) continue;
            bool dup = false;
            for (auto& s : mEsdeInstalled) if (s.name == e->d_name) { dup = true; break; }
            if (dup) continue;                        // data root already provided this name
            EsdeInstalledSet s;
            s.dir = dir; s.name = e->d_name;
            s.caps = nanoesde::Theme::parseCapabilities(dir);
            mEsdeInstalled.push_back(std::move(s));
        }
        closedir(d);
    }
    std::sort(mEsdeInstalled.begin(), mEsdeInstalled.end(),
              [](const EsdeInstalledSet& a, const EsdeInstalledSet& b) {
                  std::string la = a.name, lb = b.name;
                  for (auto& c : la) c = tolower((unsigned char)c);
                  for (auto& c : lb) c = tolower((unsigned char)c);
                  return la < lb;
              });
}

void NanoMenu::esdeMenuOpen() {
    ensureEsdeTheme();               // make sure caps reflect the current set
    esdeMenuEnumerateInstalled();
    mEsdeMenuActive = true;
    mEsdeMenuClosing = false;
    mEsdeMenuPage = ESDE_PG_ROOT;
    mEsdeMenuSel = 0;
    mEsdeMenuScroll = 0;
    mEsdeMenuAnim = 0.0f;
    mDisplayDirty = true;
}

void NanoMenu::esdeMenuClose() {
    mEsdeMenuClosing = true;         // render keeps drawing during the fade-out
    mEsdeMenuActive = false;
    mDisplayDirty = true;
}

// Write a chosen value and live-reload. Clearing mEsdeLoaded defeats ensureEsdeTheme's
// themeset-only early-out so a variant/colorScheme/aspect/font change actually reparses.
void NanoMenu::esdeMenuApplyOption(const char* prop, const std::string& value) {
    property_set(prop, value.c_str());
    mEsdeLoaded = false;
    ensureEsdeTheme();
    mEsdeInGamelist = false;         // layout may have changed; return to the system view
    mEsdeCamCursor = -1;             // resnap the carousel to the reloaded set
    mDisplayDirty = true;
}

// Number of rows on the current page. The root page is the downloader row (0), the five option
// rows, then the Nano Settings row (last); the downloader page lists the fetched themes; a picker
// lists its options.
int NanoMenu::esdeMenuPageRows() {
    if (mEsdeMenuPage == ESDE_PG_ROOT) return kEsdeRootRows;
    if (mEsdeMenuPage == ESDE_PG_DOWNLOADER) {
        std::lock_guard<std::mutex> lk(mEsdeDlMutex);
        return (int)mEsdeDlList.size();
    }
    if (mEsdeMenuPage == ESDE_PG_APPS) return (int)mAppEntries.size();
    return (int)mEsdeMenuPickOpts.size();
}

void NanoMenu::esdeMenuMove(int dir) {
    int n = esdeMenuPageRows();
    if (n <= 0) return;
    mEsdeMenuSel = ((mEsdeMenuSel + dir) % n + n) % n;
    mDisplayDirty = true;
}

// Left/Right on the root page cycle the focused option's value in place and apply immediately
// (ES-DE OptionListComponent inline < > behavior). The downloader row (0) is not cyclable.
void NanoMenu::esdeMenuCycle(int dir) {
    if (mEsdeMenuPage != ESDE_PG_ROOT) { esdeMenuMove(dir); return; }
    if (mEsdeMenuSel == 0) return;                          // THEME DOWNLOADER row
    if (mEsdeMenuSel >= kEsdeRowApps) return;               // APPLICATIONS / NANO SETTINGS (A-only)
    int optRow = mEsdeMenuSel - 1;
    std::string label, prop;
    std::vector<std::pair<std::string, std::string>> opts;
    int cur = esdeMenuOptionCtx(optRow, label, prop, opts);
    if (opts.size() < 2) return;
    int next = ((cur + dir) % (int)opts.size() + (int)opts.size()) % (int)opts.size();
    esdeMenuApplyOption(prop.c_str(), opts[next].first);
    if (optRow == 0) esdeMenuEnumerateInstalled();  // THEME change re-enumerates sets
    mDisplayDirty = true;
}

// A: on root, open the downloader or the focused option's full-list picker; on a picker, commit;
// on the downloader, install the selected theme.
void NanoMenu::esdeMenuSelect() {
    if (mEsdeMenuPage == ESDE_PG_ROOT) {
        if (mEsdeMenuSel == 0) {                     // open the theme downloader
            mEsdeMenuPage = ESDE_PG_DOWNLOADER;
            mEsdeMenuSel = 0; mEsdeMenuScroll = 0;
            bool empty;
            { std::lock_guard<std::mutex> lk(mEsdeDlMutex); empty = mEsdeDlList.empty(); }
            if (empty && !mEsdeDlFetching.load()) esdeDlStartFetch();
        } else if (mEsdeMenuSel == kEsdeRowApps) {   // APPLICATIONS: open the app launcher list
            if (mAppEntries.empty()) loadInstalledApps();
            mEsdeMenuPage = ESDE_PG_APPS;
            mEsdeMenuSel = 0; mEsdeMenuScroll = 0;
        } else if (mEsdeMenuSel == kEsdeRowNanoSettings) {   // NANO SETTINGS: hand off to the XMB menus
            esdeOpenNanoSettings();
            return;
        } else {                                     // open an option picker
            int optRow = mEsdeMenuSel - 1;
            std::string label, prop;
            std::vector<std::pair<std::string, std::string>> opts;
            int cur = esdeMenuOptionCtx(optRow, label, prop, opts);
            if (opts.empty()) { mDisplayDirty = true; return; }
            mEsdeMenuPage = (EsdeMenuPage)(ESDE_PG_THEME + optRow);
            mEsdeMenuPickOpts = opts;
            mEsdeMenuPickProp = prop;
            mEsdeMenuPickTitle = label;
            mEsdeMenuSel = cur;
            mEsdeMenuScroll = 0;
        }
    } else if (mEsdeMenuPage == ESDE_PG_DOWNLOADER) {
        if (mEsdeDlInstalling.load() || mEsdeDlFetching.load()) { mDisplayDirty = true; return; }
        EsdeDlEntry e; bool ok = false;
        {
            std::lock_guard<std::mutex> lk(mEsdeDlMutex);
            if (mEsdeMenuSel >= 0 && mEsdeMenuSel < (int)mEsdeDlList.size()) {
                e = mEsdeDlList[mEsdeMenuSel]; ok = true;
            }
        }
        if (ok && !e.installed) esdeDlStartInstall(e);
    } else if (mEsdeMenuPage == ESDE_PG_APPS) {      // app launcher: start the selected app
        if (mEsdeMenuSel >= 0 && mEsdeMenuSel < (int)mAppEntries.size()) {
            std::string pkg = mAppEntries[mEsdeMenuSel].packageName;
            esdeMenuClose();                         // dismiss the overlay before launching
            overlayLaunchPackage(pkg);
        }
        return;
    } else {                                         // picker: commit the choice
        int i = mEsdeMenuSel;
        if (i >= 0 && i < (int)mEsdeMenuPickOpts.size()) {
            bool wasTheme = (mEsdeMenuPage == ESDE_PG_THEME);
            esdeMenuApplyOption(mEsdeMenuPickProp.c_str(), mEsdeMenuPickOpts[i].first);
            if (wasTheme) esdeMenuEnumerateInstalled();
        }
        mEsdeMenuSel = ((int)mEsdeMenuPage - (int)ESDE_PG_THEME) + 1;   // back onto the option row
        mEsdeMenuPage = ESDE_PG_ROOT;
        mEsdeMenuScroll = 0;
    }
    mDisplayDirty = true;
}

// B: from a picker or the downloader return to root; from root close the menu.
void NanoMenu::esdeMenuBack() {
    if (mEsdeMenuPage == ESDE_PG_DOWNLOADER) {
        mEsdeMenuPage = ESDE_PG_ROOT; mEsdeMenuSel = 0; mEsdeMenuScroll = 0;
        mDisplayDirty = true;
    } else if (mEsdeMenuPage == ESDE_PG_APPS) {
        mEsdeMenuPage = ESDE_PG_ROOT; mEsdeMenuSel = kEsdeRowApps; mEsdeMenuScroll = 0;
        mDisplayDirty = true;
    } else if (mEsdeMenuPage != ESDE_PG_ROOT) {
        mEsdeMenuSel = ((int)mEsdeMenuPage - (int)ESDE_PG_THEME) + 1;
        mEsdeMenuPage = ESDE_PG_ROOT;
        mEsdeMenuScroll = 0;
        mDisplayDirty = true;
    } else {
        esdeMenuClose();
    }
}

// "Nano Settings" row: leave the ES-DE options overlay and hand off to the shared XMB menu stack,
// which the ES-DE home already renders (renderPs3Xmb) and navigates (mPs3Stack) when non-empty -
// the same path the power-hold Quick Menu uses. Push a small chooser with the two menus the built-in
// homes reach from their carousel: the Quick Menu (global actions) and the full Settings tree. Back
// pops the chooser and returns to the ES-DE home automatically (handleBack).
void NanoMenu::esdeOpenNanoSettings() {
    mEsdeMenuActive = false; mEsdeMenuClosing = false; mEsdeMenuAnim = 0.0f;
    if (!mPs3MenuBuilt) initPs3Menu();
    // Clean, top-level XMB state behind the pushed chooser (mirrors openQuickPowerMenu).
    mPs3Stack.clear();
    mPs3DlgActive = false; mPs3OptActive = false; mPs3BrightSlider = false;
    mMenuState = MENU_MAIN;
    int landIdx = (mPs3QuickCatIdx >= 0) ? mPs3QuickCatIdx
                : (mPs3SettingsCatIdx >= 0) ? mPs3SettingsCatIdx : 0;
    if (landIdx >= 0 && landIdx < (int)mPs3Cats.size()) {
        mPs3CatIdx = landIdx;
        mPs3CatItemSel[mPs3CatIdx] = 0;
    }
    mPs3ItemIdx = 0;
    mNdsAtRoot = false;
    Ps3Level lvl; lvl.title = "Nano Settings"; lvl.sel = 0;
    auto add = [&](const char* label, int catIdx, int icon) {
        if (catIdx < 0 || catIdx >= (int)mPs3Cats.size()) return;
        Ps3Item it; it.label = label; it.kind = PS3_CAT_SUBMENU; it.a = catIdx;
        it.iconTex = iconTexForIcon(icon); it.nmapTex = nmapForIcon(icon);
        it.iconR = it.iconG = it.iconB = 1.0f;
        lvl.items.push_back(it);
    };
    add("Quick Menu",    mPs3QuickCatIdx,    54);   // power glyph
    add("Full Settings", mPs3SettingsCatIdx, 44);   // settings glyph
    if (lvl.items.empty()) { mDisplayDirty = true; return; }   // no categories built -> stay on ES-DE
    mPs3Stack.push_back(lvl);
    mNdsCamera = (float)ndsFocusSel(); mNdsScrubbing = false; mNdsFlingVel = 0.0f;
    ps3Sfx(PS3_SFX_OK);
    mDisplayDirty = true;
}

// ------------------------------------------------------------------ render

void NanoMenu::renderEsdeMenu() {
    // Ease the open/close animation (0..1). Closing decays to 0 then stops drawing.
    float step = 1.0f / 8.0f;
    if (mEsdeMenuActive) mEsdeMenuAnim = std::min(1.0f, mEsdeMenuAnim + step);
    else                 mEsdeMenuAnim = std::max(0.0f, mEsdeMenuAnim - step);
    if (mEsdeMenuAnim <= 0.001f && !mEsdeMenuActive) { mEsdeMenuClosing = false; return; }
    if (mEsdeMenuAnim < 1.0f) mDisplayDirty = true;
    float a = mEsdeMenuAnim;

    const float W = (float)mWidth, H = (float)mHeight;
    const bool vertical = H > W;
    const float unit = std::min(W, H);

    // Background behind the options panel. ES-DE (Window.cpp) snapshots the home on
    // menu-open, runs a 2-pass gaussian when MenuBlurBackground is set (its default) and
    // multiplies the result by a dimming factor: 0.80 for the dark / darkred schemes,
    // 0.60 for light. core.glsl multiplies the sampled colour by `dimming`, so 0.80 means
    // 80% brightness (NOT an 80% dim). Mirror that exactly rather than the old flat scrim.
    // persist.gammaos.nano.esde.menucolorscheme picks the scheme (dark default, as ES-DE);
    // persist.gammaos.nano.esde.menublur mirrors MenuBlurBackground (default on) and lets
    // the blur fall back to a plain dim on any GPU where the framebuffer readback fails.
    char msch[PROPERTY_VALUE_MAX] = {0};
    property_get("persist.gammaos.nano.esde.menucolorscheme", msch, "dark");
    const float dimBright = (!strcmp(msch, "light")) ? 0.60f : 0.80f;
    char mblurP[PROPERTY_VALUE_MAX] = {0};
    property_get("persist.gammaos.nano.esde.menublur", mblurP, "1");
    bool bgBlurred = false;
    if (mblurP[0] != '0' && captureGlass(0.0f, 0.0f, W, H)) {
        // captureGlass leaves the blurred home in mGlassBlurTex already in display sRGB,
        // so no tonemap (tonemapOverride 0). Draw it opaque, tinted down to dimBright, and
        // fade with the open ease. radius 0 -> plain full-screen quad, no rounded SDF.
        drawFrostedGlass(0.0f, 0.0f, W, H, 0.0f, dimBright, dimBright, dimBright,
                         1.0f, a, /*waveSpace=*/false, /*tonemapOverride=*/0.0f);
        bgBlurred = true;
    }
    if (!bgBlurred) {
        // Blur off or readback unavailable: ES-DE still dims, so lay the equivalent black
        // scrim at 1 - dimBright alpha (0.20 for dark, 0.40 for light).
        drawQuad(0, 0, W, H, 0.0f, 0.0f, 0.0f, (1.0f - dimBright) * a);
    }

    const bool dl = (mEsdeMenuPage == ESDE_PG_DOWNLOADER);
    const bool apps = (mEsdeMenuPage == ESDE_PG_APPS);

    // Panel geometry (ES-DE MenuComponent, in screen fractions so it scales to any panel).
    float menuW = std::min(H * 1.05f, W * (vertical ? 0.94f : 0.90f));
    float titleH = unit * 0.11f;
    float rowH = unit * (dl ? 0.075f : 0.062f);        // downloader rows carry a second info line
    int rows = esdeMenuPageRows();
    float footerH = unit * (dl ? 0.10f : 0.06f);       // downloader footer holds a status/progress line
    float maxH = H * (vertical ? 0.70f : 0.80f);
    float listH = std::min((float)rows * rowH, maxH - titleH - footerH);
    int maxFit = rowH > 0 ? (int)(listH / rowH) : rows;
    if (maxFit < 1) maxFit = 1;
    float bodyRows = dl ? (float)maxFit : std::min((float)rows, (float)maxFit);
    float menuH = titleH + bodyRows * rowH + footerH;
    float menuX = (W - menuW) * 0.5f;
    float menuY = H * 0.13f;
    menuY += (1.0f - a) * unit * 0.03f;                // slide up + fade in

    // ES-DE menu colors are a GLOBAL scheme (dark/darkred/light in ViewController::setMenuColors),
    // NOT theme-driven, so the panel looks the same across every theme. Mirror all three with their
    // exact constants, selected by persist.gammaos.nano.esde.menucolorscheme (default dark, matching
    // ES-DE's own default), so nano's options panel reads identically to real ES-DE. The selected row
    // is a solid bar in the selector colour (black / dark red / white) with the text kept at its
    // normal shade - ES-DE never brightens the selected row.
    struct MenuCol { float frame, title, prim, sec, sel[3], sep[3]; };
    auto hx = [](int v) { return v / 255.0f; };
    MenuCol mc;
    // ES-DE ViewController::setMenuColors: separators are 0xC6C7C6 (light) / 0x303030 (dark, darkred).
    if (!strcmp(msch, "light")) {                 // 0xEFEFEF / 0x555555 / 0x777777 / 0x888888 / 0xFFFFFF
        mc = {hx(0xEF), hx(0x55), hx(0x77), hx(0x88), {1.0f, 1.0f, 1.0f}, {hx(0xC6), hx(0xC7), hx(0xC6)}};
    } else if (!strcmp(msch, "darkred")) {        // dark frame/text, 0x461816 selector
        mc = {hx(0x19), hx(0x90), hx(0x80), hx(0x93), {hx(0x46), hx(0x18), hx(0x16)}, {hx(0x30), hx(0x30), hx(0x30)}};
    } else {                                      // dark (default): 0x191919 / 0x909090 / 0x808080 / 0x939393 / 0x000000
        mc = {hx(0x19), hx(0x90), hx(0x80), hx(0x93), {0.0f, 0.0f, 0.0f}, {hx(0x30), hx(0x30), hx(0x30)}};
    }
    const float cFrame = mc.frame, cTitle = mc.title, cPrim = mc.prim, cSec = mc.sec;
    float rad = 30.0f * (unit / 1080.0f);
    drawRoundedRect(menuX, menuY, menuW, menuH, rad, cFrame, cFrame, cFrame, a);
    drawRoundedRect(menuX, menuY, menuW, titleH, rad, cFrame, cFrame, cFrame, a);

    // Title.
    std::string title = (mEsdeMenuPage == ESDE_PG_ROOT) ? std::string("UI SETTINGS")
                      : dl ? std::string("THEME DOWNLOADER")
                      : apps ? std::string("APPLICATIONS") : mEsdeMenuPickTitle;
    float titleSc = (unit * 0.045f) / (float)FONT_CHAR_H;
    float tw = measureText(title.c_str(), titleSc);
    drawText(title.c_str(), menuX + (menuW - tw) * 0.5f,
             menuY + (titleH - unit * 0.045f) * 0.5f, titleSc, cTitle, cTitle, cTitle, a);

    // Keep the selection inside the visible window.
    if (mEsdeMenuSel < mEsdeMenuScroll) mEsdeMenuScroll = mEsdeMenuSel;
    if (mEsdeMenuSel >= mEsdeMenuScroll + maxFit) mEsdeMenuScroll = mEsdeMenuSel - maxFit + 1;
    if (mEsdeMenuScroll > rows - maxFit) mEsdeMenuScroll = std::max(0, rows - maxFit);
    if (mEsdeMenuScroll < 0) mEsdeMenuScroll = 0;

    float listY = menuY + titleH;
    float rowSc = (unit * 0.033f) / (float)FONT_CHAR_H;
    float subSc = (unit * 0.024f) / (float)FONT_CHAR_H;
    float glyphH = unit * 0.033f;
    float padX = menuW * 0.05f;

    // Snapshot the download list once (worker may mutate it) for a consistent frame.
    std::vector<EsdeDlEntry> dlSnap;
    if (dl) { std::lock_guard<std::mutex> lk(mEsdeDlMutex); dlSnap = mEsdeDlList; }

    for (int r = 0; r < maxFit && mEsdeMenuScroll + r < rows; r++) {
        int i = mEsdeMenuScroll + r;
        float ry = listY + r * rowH;
        bool cur = (i == mEsdeMenuSel);
        // ES-DE ComponentList selector: the selected row is a solid bar in mMenuColorSelector
        // (black / dark red / white per scheme), drawn behind the row; the text stays its normal
        // colour (ES-DE does not brighten the selected row), so selection reads as a bar, not a tint.
        if (cur) drawQuad(menuX, ry, menuW, rowH, mc.sel[0], mc.sel[1], mc.sel[2], a);

        if (mEsdeMenuPage == ESDE_PG_ROOT) {
            if (i == 0) {                              // THEME DOWNLOADER submenu row
                drawText("THEME DOWNLOADER", menuX + padX, ry + (rowH - glyphH) * 0.5f, rowSc,
                         cPrim, cPrim, cPrim, a);
                drawText(">", menuX + menuW - padX, ry + (rowH - glyphH) * 0.5f, rowSc,
                         cPrim, cPrim, cPrim, a);
                continue;
            }
            if (i == kEsdeRowApps) {                   // APPLICATIONS submenu row (opens the app launcher)
                drawText("APPLICATIONS", menuX + padX, ry + (rowH - glyphH) * 0.5f, rowSc,
                         cPrim, cPrim, cPrim, a);
                drawText(">", menuX + menuW - padX, ry + (rowH - glyphH) * 0.5f, rowSc,
                         cPrim, cPrim, cPrim, a);
                continue;
            }
            if (i == kEsdeRowNanoSettings) {          // NANO SETTINGS submenu row (Quick Menu / Full Settings)
                drawText("NANO SETTINGS", menuX + padX, ry + (rowH - glyphH) * 0.5f, rowSc,
                         cPrim, cPrim, cPrim, a);
                drawText(">", menuX + menuW - padX, ry + (rowH - glyphH) * 0.5f, rowSc,
                         cPrim, cPrim, cPrim, a);
                continue;
            }
            std::string lbl, prop; std::vector<std::pair<std::string, std::string>> opts;
            int ci = esdeMenuOptionCtx(i - 1, lbl, prop, opts);
            std::string val = (ci >= 0 && ci < (int)opts.size()) ? opts[ci].second : std::string();
            float br = opts.empty() ? 0.314f : 1.0f;   // disabled rows dimmed (ES-DE grays these)
            float lc = cPrim * br;
            float labelW = measureText(lbl.c_str(), rowSc);
            drawText(lbl.c_str(), menuX + padX, ry + (rowH - glyphH) * 0.5f, rowSc, lc, lc, lc, a);
            if (!val.empty()) {
                std::string shown = "< " + val + " >";
                float valSc = rowSc;
                float vw = measureText(shown.c_str(), valSc);
                // Keep the value clear of the row label: shrink it to the free span between the
                // label's right edge and the right padding if it would otherwise overlap.
                float avail = menuW - 2.0f * padX - labelW - menuW * 0.03f;
                if (avail > 0.0f && vw > avail) { valSc *= avail / vw; vw = avail; }
                float vGlyphH = glyphH * (valSc / rowSc);
                drawText(shown.c_str(), menuX + menuW - padX - vw, ry + (rowH - vGlyphH) * 0.5f,
                         valSc, cSec, cSec, cSec, a);
            }
        } else if (dl) {                               // downloader theme entry
            if (i >= (int)dlSnap.size()) continue;
            const EsdeDlEntry& e = dlSnap[i];
            float lc = cur ? 0.95f : 0.6f;
            drawText(e.name.c_str(), menuX + padX, ry + rowH * 0.14f, rowSc, lc, lc, lc, a);
            if (e.installed) {
                const char* tag = "INSTALLED";
                float vw = measureText(tag, subSc);
                drawText(tag, menuX + menuW - padX - vw, ry + rowH * 0.18f, subSc, 0.5f, 0.75f, 0.5f, a);
            }
            char sub[160];
            snprintf(sub, sizeof(sub), "%s   %d variants  %d colours  %d aspects",
                     e.author.c_str(), e.variants, e.colorSchemes, e.aspectRatios);
            drawText(sub, menuX + padX, ry + rowH * 0.58f, subSc, 0.45f, 0.45f, 0.45f, a);
        } else if (apps) {                             // app launcher row
            if (i >= (int)mAppEntries.size()) continue;
            const char* lbl = mAppEntries[i].label.c_str();
            float lc = cur ? 0.95f : 0.55f;
            drawText(lbl, menuX + padX, ry + (rowH - glyphH) * 0.5f, rowSc, lc, lc, lc, a);
        } else {                                       // option picker row
            const char* lbl = mEsdeMenuPickOpts[i].second.c_str();
            float lc = cur ? 0.95f : 0.55f;
            drawText(lbl, menuX + padX, ry + (rowH - glyphH) * 0.5f, rowSc, lc, lc, lc, a);
        }
    }

    // ES-DE ComponentList draws a thin separator at every row boundary - the top of each entry plus a
    // closing one at the list bottom - in mMenuColorSeparators (0x303030 dark / 0xC6C7C6 light). These
    // are the subtle lines that divide the menu rows; nano omitted them, so its list read as one flat
    // block. Draw them on top of the rows/selector like ES-DE, a single pixel scaled to the panel.
    {
        int nVis = std::min(maxFit, rows - mEsdeMenuScroll);
        if (nVis < 0) nVis = 0;
        float sepH = std::max(1.0f, unit / 720.0f);
        for (int r = 0; r <= nVis; r++)
            drawQuad(menuX, listY + (float)r * rowH, menuW, sepH, mc.sep[0], mc.sep[1], mc.sep[2], a);
    }

    // Empty app list (e.g. packages.list not yet readable): say so rather than show a blank panel.
    if (apps && mAppEntries.empty()) {
        const char* msg = "No apps installed";
        float mw = measureText(msg, rowSc);
        drawText(msg, menuX + (menuW - mw) * 0.5f, listY + rowH * 0.5f, rowSc, cSec, cSec, cSec, a);
    }

    // Scroll indicators.
    if (rows > maxFit) {
        float ix = menuX + menuW - padX * 0.5f;
        if (mEsdeMenuScroll > 0)
            drawText("^", ix, listY + rowH * 0.1f, rowSc, 0.7f, 0.7f, 0.7f, a);
        if (mEsdeMenuScroll + maxFit < rows)
            drawText("v", ix, listY + (maxFit - 1) * rowH + rowH * 0.1f, rowSc, 0.7f, 0.7f, 0.7f, a);
    }

    float footY = menuY + menuH - footerH;
    // Downloader status / progress line above the help.
    if (dl) {
        std::string status;
        if (mEsdeDlFetching.load())        status = "Fetching theme list...";
        else if (mEsdeDlInstalling.load()) status = "Installing " + mEsdeDlInstalledName + "...";
        else if (!mEsdeDlError.empty())    status = mEsdeDlError;
        else if (dlSnap.empty())           status = "Press A to fetch the theme list";
        if (!status.empty()) {
            float sw = measureText(status.c_str(), subSc);
            bool err = !mEsdeDlError.empty() && !mEsdeDlInstalling.load() && !mEsdeDlFetching.load();
            drawText(status.c_str(), menuX + (menuW - sw) * 0.5f, footY + footerH * 0.12f, subSc,
                     err ? 0.85f : 0.6f, err ? 0.35f : 0.6f, err ? 0.35f : 0.6f, a);
        }
        if (mEsdeDlInstalling.load()) {
            float bx = menuX + padX, bw = menuW - padX * 2, by = footY + footerH * 0.40f, bh = unit * 0.012f;
            drawRoundedRect(bx, by, bw, bh, bh * 0.5f, 0.25f, 0.25f, 0.25f, a);
            float p = mEsdeDlProgress.load() / 100.0f;
            if (p > 0) drawRoundedRect(bx, by, bw * p, bh, bh * 0.5f, 0.4f, 0.7f, 0.9f, a);
        }
    }

    // The menu's help bar is drawn like every ES-DE help bar - button-icon glyphs + labels at the
    // screen bottom, in the theme's helpsystem style - by esdeDrawHelp (which swaps in the menu's
    // prompts, see esdeMenuBuildHelpPrompts, whenever the menu is open), so nothing is drawn inside
    // the panel here (ES-DE shows no separate in-panel legend). The home element pass only cached
    // the view-matched helpsystem element (it draws before the dim/blur above); redraw it here, on
    // top of the panel, so the menu legend is not covered - matching ES-DE's top-GUI HelpComponent.
    if (mEsdeHelpElem)
        esdeDrawHelp(mEsdeHelpElem, mEsdeHelpGamelist, mEsdeHelpPrimary, /*menuOverlayPass=*/true);
}

// Build the ES-DE menu help prompts (icon id, label) for the currently-focused row, in the exact
// left-to-right ORDER ES-DE's HelpComponent renders them (it draws getHelpPrompts in vector order,
// with no priority sort - unlike the view/home bar, which esdeDrawHelp reorders to match the
// theme's per-system help; the menu uses the default HelpComponent). The selector rows are inline
// OptionListComponents, so their bar mirrors ES-DE's UI Settings menu exactly:
// ComponentList::getHelpPrompts = [focused component's prompts] + "up/down choose", then the
// GuiSettings container appends the back/close prompt. OptionListComponent contributes
// "left/right change value" then "a select". Hence a selector row reads
// LEFT/RIGHT CHANGE VALUE - A SELECT - UP/DOWN CHOOSE - B CLOSE MENU, and a plain row drops the
// change-value prompt. Sub-pages are their own GuiSettings-style screens (B goes back, not close).
void NanoMenu::esdeMenuBuildHelpPrompts(std::vector<std::pair<std::string, std::string>>& out) const {
    out.clear();
    if (mEsdeMenuPage == ESDE_PG_ROOT) {
        if (mEsdeMenuSel >= 1 && mEsdeMenuSel < kEsdeRowApps)     // an inline OptionList selector row
            out.push_back({"left/right", "change value"});
        out.push_back({"a", "select"});
        out.push_back({"up/down", "choose"});
        out.push_back({"b", "close menu"});
    } else if (mEsdeMenuPage == ESDE_PG_DOWNLOADER) {
        out.push_back({"a", "install"});
        out.push_back({"up/down", "choose"});
        out.push_back({"b", "back"});
    } else if (mEsdeMenuPage == ESDE_PG_APPS) {
        out.push_back({"a", "launch"});
        out.push_back({"up/down", "choose"});
        out.push_back({"b", "back"});
    } else {
        out.push_back({"a", "select"});
        out.push_back({"up/down", "choose"});
        out.push_back({"b", "back"});
    }
}

// ------------------------------------------------------------------ downloader

// fork/exec a process, no shell (avoids injection); returns the exit code, -1 on spawn failure.
// netns=true runs it in init's mount namespace (nano's home service lives in the bootstrap ns;
// network/apex-dependent tools need init's default ns, the same trick gammaos-net uses). The
// child's stdout/stderr go to a log file so a failing curl/unzip leaves a diagnosable trail.
static int esdeRunProc(const std::vector<std::string>& argvIn, bool netns = false) {
    std::vector<std::string> argv;
    if (netns) { argv = {"/system/bin/nsenter", "-t", "1", "-m", "--"}; }
    for (const auto& a : argvIn) argv.push_back(a);
    std::vector<char*> cargv;
    for (auto& s : argv) cargv.push_back(const_cast<char*>(s.c_str()));
    cargv.push_back(nullptr);
    pid_t pid = fork();
    if (pid < 0) { ALOGE("esde-dl: fork failed: %s", strerror(errno)); return -1; }
    if (pid == 0) {
        int fd = open("/data/local/tmp/esde_dl.log", O_WRONLY | O_CREAT | O_APPEND, 0644);
        if (fd >= 0) { dup2(fd, STDOUT_FILENO); dup2(fd, STDERR_FILENO); if (fd > 2) close(fd); }
        execv(cargv[0], cargv.data());
        _exit(127);
    }
    int status = 0;
    while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {}
    int rc = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
    if (rc != 0)   // only surface failures; a normal download is silent
        ALOGW("esde-dl: '%s' exited rc=%d", argvIn.empty() ? "?" : argvIn[0].c_str(), rc);
    return rc;
}

// Candidate zip-archive URLs for a theme's git repo (nano has no git; GitHub/GitLab serve repo
// zips). master then main since the default branch varies.
static std::vector<std::string> esdeZipUrls(const std::string& git) {
    std::string u = git;
    if (u.size() > 4 && u.compare(u.size() - 4, 4, ".git") == 0) u = u.substr(0, u.size() - 4);
    size_t slash = u.find_last_of('/');
    std::string repo = (slash == std::string::npos) ? u : u.substr(slash + 1);
    std::vector<std::string> out;
    if (u.find("gitlab.com") != std::string::npos) {
        out.push_back(u + "/-/archive/master/" + repo + "-master.zip");
        out.push_back(u + "/-/archive/main/" + repo + "-main.zip");
    } else {                                             // github and github-compatible hosts
        out.push_back(u + "/archive/refs/heads/master.zip");
        out.push_back(u + "/archive/refs/heads/main.zip");
    }
    return out;
}

// The directory inside `base` that holds a theme (has capabilities.xml), directly or one level
// down (repo zips wrap everything in a <repo>-<branch>/ dir). "" if none.
static std::string esdeFindThemeDir(const std::string& base) {
    if (access((base + "/capabilities.xml").c_str(), F_OK) == 0) return base;
    DIR* d = opendir(base.c_str());
    if (!d) return "";
    std::string found;
    for (dirent* e = readdir(d); e; e = readdir(d)) {
        if (e->d_name[0] == '.') continue;
        std::string sub = base + "/" + e->d_name;
        if (access((sub + "/capabilities.xml").c_str(), F_OK) == 0) { found = sub; break; }
    }
    closedir(d);
    return found;
}

void NanoMenu::esdeDlStartFetch() {
    if (mEsdeDlFetching.exchange(true)) return;
    { std::lock_guard<std::mutex> lk(mEsdeDlMutex); mEsdeDlError.clear(); }
    if (mEsdeDlThread.joinable()) mEsdeDlThread.join();
    mEsdeDlThread = std::thread([this] {
        char urlbuf[PROPERTY_VALUE_MAX] = {0};
        property_get("persist.gammaos.nano.esde.themes_url", urlbuf,
                     "https://gitlab.com/es-de/themes/themes-list/-/raw/master/themes.json");
        const std::string tmp = "/data/system/nano_esde_themes/.themes.json";
        unlink(tmp.c_str());
        // Download through the framework helper: nano itself has no default network, so it routes
        // the fetch through gammaos-net (an app_process with the framework + a validated network).
        int rc = esdeRunProc({"/system/bin/gammaos-net", "download", urlbuf, tmp});
        // Trust the downloaded file if it parses, so an unreliable exit code under nano's process
        // context does not mask a good fetch.
        std::vector<EsdeDlEntry> list;
        std::string err;
        std::string text;
        if (FILE* f = fopen(tmp.c_str(), "rb")) {
            char b[8192]; size_t n;
            while ((n = fread(b, 1, sizeof(b), f)) > 0) text.append(b, n);
            fclose(f);
        }
        njson::Value root;
        if (njson::parse(text, &root) && root.isObject()) {
            const njson::Value* th = root.find("themes");
            if (th && th->isArray()) {
                for (const njson::Value& t : th->arr) {
                    if (!t.isObject()) continue;
                    EsdeDlEntry e;
                    auto s = [&](const char* k) { const njson::Value* v = t.find(k); return v ? v->asString() : std::string(); };
                    auto cnt = [&](const char* k) { const njson::Value* v = t.find(k); return (v && v->isArray()) ? (int)v->arr.size() : 0; };
                    e.name = s("name"); e.reponame = s("reponame"); e.url = s("url"); e.author = s("author");
                    e.variants = cnt("variants"); e.colorSchemes = cnt("colorSchemes");
                    e.aspectRatios = cnt("aspectRatios"); e.fontSizes = cnt("fontSizes");
                    if (e.name.empty() || e.reponame.empty()) continue;
                    e.installed = access(("/data/system/nano_esde_themes/" + e.reponame + "/capabilities.xml").c_str(), F_OK) == 0
                               || access(("/system/etc/nano_esde_themes/" + e.reponame + "/capabilities.xml").c_str(), F_OK) == 0
                               || access(("/storage/emulated/0/ES-DE/themes/" + e.reponame + "/capabilities.xml").c_str(), F_OK) == 0;
                    list.push_back(std::move(e));
                }
            }
        }
        if (list.empty())
            err = "theme list fetch failed (curl rc " + std::to_string(rc) + ", " +
                  std::to_string((long)text.size()) + " bytes)";
        ALOGI("esde-dl: fetch rc=%d bytes=%zu parsed=%zu", rc, text.size(), list.size());
        {
            std::lock_guard<std::mutex> lk(mEsdeDlMutex);
            mEsdeDlList.swap(list);
            mEsdeDlError = err;
        }
        mEsdeDlFetching.store(false);
        mEsdeDlDone.store(true);
    });
}

std::string NanoMenu::esdeDlDoInstall(const EsdeDlEntry& e) {
    const std::string root = "/data/system/nano_esde_themes";
    const std::string staging = root + "/.dl_stage";
    esdeRunProc({"/system/bin/rm", "-rf", staging});
    esdeRunProc({"/system/bin/mkdir", "-p", staging});
    const std::string zip = staging + "/theme.zip";
    mEsdeDlProgress.store(5);
    int rc = -1;
    for (const std::string& url : esdeZipUrls(e.url)) {
        esdeRunProc({"/system/bin/gammaos-net", "download", url, zip});
        struct stat st;
        if (stat(zip.c_str(), &st) == 0 && st.st_size > 1000) { rc = 0; break; }
        rc = -1;
    }
    if (rc != 0) { esdeRunProc({"/system/bin/rm", "-rf", staging}); return "download failed"; }
    mEsdeDlProgress.store(50);
    if (esdeRunProc({"/system/bin/unzip", "-o", "-q", zip, "-d", staging}) != 0) {
        esdeRunProc({"/system/bin/rm", "-rf", staging});
        return "unzip failed";
    }
    mEsdeDlProgress.store(80);
    std::string themeDir = esdeFindThemeDir(staging);
    if (themeDir.empty()) { esdeRunProc({"/system/bin/rm", "-rf", staging}); return "no theme in archive"; }
    const std::string target = root + "/" + e.reponame;
    esdeRunProc({"/system/bin/rm", "-rf", target});
    if (esdeRunProc({"/system/bin/mv", themeDir, target}) != 0) {
        esdeRunProc({"/system/bin/rm", "-rf", staging});
        return "install move failed";
    }
    esdeRunProc({"/system/bin/rm", "-rf", staging});
    // Match the manual-install convention (system:system + the system_data_file context) so a
    // downloaded set is owned and labelled like the bundled ones rather than left root:root from
    // unzip. nano runs as root so it reads either way, but this keeps ownership consistent and the
    // set readable regardless of the process uid.
    esdeRunProc({"/system/bin/chown", "-R", "system:system", target});
    esdeRunProc({"/system/bin/restorecon", "-R", target});
    mEsdeDlProgress.store(100);
    return "";
}

void NanoMenu::esdeDlStartInstall(const EsdeDlEntry& e) {
    if (mEsdeDlInstalling.exchange(true)) return;
    { std::lock_guard<std::mutex> lk(mEsdeDlMutex); mEsdeDlError.clear(); }
    mEsdeDlInstalledName = e.name;
    mEsdeDlProgress.store(0);
    if (mEsdeDlThread.joinable()) mEsdeDlThread.join();
    EsdeDlEntry ec = e;
    mEsdeDlThread = std::thread([this, ec] {
        std::string err = esdeDlDoInstall(ec);
        { std::lock_guard<std::mutex> lk(mEsdeDlMutex); mEsdeDlError = err; }
        mEsdeDlInstalling.store(false);
        mEsdeDlDone.store(true);
    });
}

// Per-frame while the menu is open: join a finished worker and refresh installed state.
void NanoMenu::esdeDlTick() {
    if (!mEsdeDlDone.exchange(false)) return;
    if (mEsdeDlThread.joinable()) mEsdeDlThread.join();
    esdeMenuEnumerateInstalled();
    std::lock_guard<std::mutex> lk(mEsdeDlMutex);
    for (auto& e : mEsdeDlList)
        e.installed = access(("/data/system/nano_esde_themes/" + e.reponame + "/capabilities.xml").c_str(), F_OK) == 0
                   || access(("/system/etc/nano_esde_themes/" + e.reponame + "/capabilities.xml").c_str(), F_OK) == 0
                   || access(("/storage/emulated/0/ES-DE/themes/" + e.reponame + "/capabilities.xml").c_str(), F_OK) == 0;
    mDisplayDirty = true;
}

}  // namespace android
