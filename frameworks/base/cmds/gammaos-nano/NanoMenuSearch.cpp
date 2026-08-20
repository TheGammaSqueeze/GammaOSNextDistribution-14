// NanoMenuSearch.cpp - global search (Select on the home XMB). Opens the on-screen
// keyboard for a query, then shows a categorized results overlay spanning every XMB
// section (Games, Music, Photos, Videos). Activating a result hands off to that
// section's existing launch / open path. Lazy by construction: nothing is built until
// the user runs a search, and gsearchClose() frees the result list so an idle launcher
// holds no search state. Replaces the old (non-functional) Y-to-search.
#include "NanoMenu.h"
#include "NanoI18n.h"      // trDyn() runtime translation of hardcoded UI strings
#include "NanoMenuPS3.h"
#include "NanoMenuUtils.h"   // setLaunchRomPath for the Applications launch

#include <algorithm>
#include <string.h>
#include <strings.h>   // strcasestr

#include <cutils/properties.h>
#include <utils/SystemClock.h>   // uptimeMillis (touch-launch fade stamp)

#include "NanoPowerMode.h"

namespace android {

namespace {
const char* kSectionName[5] = { "Games", "Music", "Photos", "Videos", "IPTV" };
// case-insensitive substring match
bool ciContains(const std::string& hay, const std::string& needle) {
    if (needle.empty()) return false;
    return strcasestr(hay.c_str(), needle.c_str()) != nullptr;
}
// DSi-theme search list layout, in DS 256x192 units. Shared by renderGlobalSearch's DSi branch
// and gsearchTouch so the drawn rows and the tap hit-test stay in lockstep. The list always
// scrolls from kNdsListTop (whole-row scroll via mGSearchScrollRow); item rows are glossy
// buttons, section headers are thin labels between them.
constexpr float kNdsListTop = 30.0f, kNdsListBot = 166.0f;
constexpr float kNdsPitch = 21.0f, kNdsBtnH = 18.0f;
constexpr float kNdsBx = 12.0f, kNdsBw = 230.0f;
inline int kNdsFitRows() { return (int)((kNdsListBot - kNdsListTop) / kNdsPitch); }
}

// Select pressed on the home XMB: open the query keyboard (plaintext). The submit
// callback builds the results and shows the overlay.
void NanoMenu::gsearchOpen() {
    openOskForPassword("Search", [this](const std::string& v) { gsearchBuild(v); });
    mOskPasswordMode = false;
    mOskPlaintext = true;
    mOskQuery = mGSearchQuery;        // prefill with the last query for quick re-search
    mOsk.caret = (int)mOskQuery.size();
}

void NanoMenu::gsearchClose() {
    mGSearchActive = false;
    mGSearchResults.clear();
    mGSearchResults.shrink_to_fit();   // release the backing store (no idle cost)
    mGSearchSel = 0;
    mGSearchScrollRow = 0;
    mGSearchAnim = 0.0f;
}

// Run the search across every section. Each section is lazily ensured-loaded first so
// the libraries are parsed on demand (the same as visiting the section). Results are
// grouped by section and sorted by label within a section.
void NanoMenu::gsearchBuild(const std::string& q) {
    mGSearchQuery = q;
    mGSearchResults.clear();
    mGSearchSel = 0;
    mGSearchScrollRow = 0;
    std::string query = q;
    // trim surrounding whitespace
    while (!query.empty() && (query.front() == ' ' || query.front() == '\t')) query.erase(query.begin());
    while (!query.empty() && (query.back() == ' ' || query.back() == '\t')) query.pop_back();
    if (query.empty()) { mGSearchActive = false; return; }

    const size_t kMaxPerSection = 80;   // bound the per-section result count

    // --- Games: recents, then ROMs per system, then installed apps ---------
    size_t gCount = 0;
    for (size_t i = 0; i < mXmbRecent.size() && gCount < kMaxPerSection; i++) {
        const XmbRecentEntry& r = mXmbRecent[i];
        if (!ciContains(r.displayName, query)) continue;
        GSearchResult gr; gr.section = 0; gr.label = r.displayName;
        gr.sub = trDyn("Recently Played"); gr.kind = PS3_RECENT; gr.a = (int)i;
        mGSearchResults.push_back(gr); gCount++;
    }
    for (size_t s = 0; s < mXmbSystems.size() && gCount < kMaxPerSection; s++) {
        const XmbSystem& sys = mXmbSystems[s];
        if (!sys.enabled) continue;
        for (size_t i = 0; i < sys.displayNames.size() && gCount < kMaxPerSection; i++) {
            if (!ciContains(sys.displayNames[i], query)) continue;
            GSearchResult gr; gr.section = 0; gr.label = sys.displayNames[i];
            gr.sub = sys.name; gr.kind = PS3_ROM; gr.a = (int)s; gr.b = (int)i;
            mGSearchResults.push_back(gr); gCount++;
        }
    }
    for (size_t i = 0; i < mAppEntries.size() && gCount < kMaxPerSection; i++) {
        const AppEntry& app = mAppEntries[i];
        if (!ciContains(app.label, query)) continue;
        GSearchResult gr; gr.section = 0; gr.label = app.label;
        gr.sub = trDyn("Application"); gr.kind = PS3_APP; gr.payload = app.packageName;
        mGSearchResults.push_back(gr); gCount++;
    }

    // --- Music: tracks (title / artist / album) ----------------------------
    musicEnsureLoaded();
    size_t mCount = 0;
    for (size_t i = 0; i < mMusicTracks.size() && mCount < kMaxPerSection; i++) {
        const MusicTrack& t = mMusicTracks[i];
        if (!ciContains(t.title, query) && !ciContains(t.artist, query) && !ciContains(t.album, query)) continue;
        GSearchResult gr; gr.section = 1; gr.label = t.title.empty() ? t.album : t.title;
        gr.sub = t.artist.empty() ? t.album : (t.artist + "  -  " + t.album);
        gr.kind = PS3_MUSIC_TRACK; gr.a = (int)i;
        mGSearchResults.push_back(gr); mCount++;
    }

    // --- Photos: by name ---------------------------------------------------
    photoEnsureLoaded();
    size_t pCount = 0;
    for (size_t i = 0; i < mPhotos.size() && pCount < kMaxPerSection; i++) {
        if (!ciContains(mPhotos[i].name, query)) continue;
        GSearchResult gr; gr.section = 2; gr.label = mPhotos[i].name;
        gr.sub = mPhotos[i].date; gr.kind = PS3_PHOTO; gr.a = (int)i;
        mGSearchResults.push_back(gr); pCount++;
    }

    // --- Videos: by name ---------------------------------------------------
    videoEnsureLoaded();
    size_t vCount = 0;
    for (size_t i = 0; i < mVideos.size() && vCount < kMaxPerSection; i++) {
        if (!ciContains(mVideos[i].name, query)) continue;
        GSearchResult gr; gr.section = 3; gr.label = mVideos[i].name;
        char sub[64];
        if (mVideos[i].w > 0 && mVideos[i].h > 0)
            snprintf(sub, sizeof(sub), "%s  %dx%d", mVideos[i].vcodec.c_str(), mVideos[i].w, mVideos[i].h);
        else snprintf(sub, sizeof(sub), "%s", mVideos[i].vcodec.c_str());
        gr.sub = sub; gr.kind = PS3_VIDEO_FILE; gr.a = (int)i;
        mGSearchResults.push_back(gr); vCount++;
    }

    // --- IPTV: live channels by name (parse the cache inline on first search so it is
    // included even without first opening the IPTV browser) ---
    if (property_get_bool("persist.gammaos.nano.iptv", true)) {
        iptvEnsureLoadedSync();
        size_t iCount = 0;
        std::lock_guard<std::mutex> lk(mIptvMutex);
        for (size_t i = 0; i < mIptvChannels.size() && iCount < kMaxPerSection; i++) {
            if (!ciContains(mIptvChannels[i].name, query)) continue;
            GSearchResult gr; gr.section = 4; gr.label = mIptvChannels[i].name;
            gr.sub = trDyn("IPTV"); gr.kind = PS3_IPTV_CHANNEL; gr.a = (int)i;
            gr.payload = mIptvChannels[i].url;
            mGSearchResults.push_back(gr); iCount++;
        }
    }

    // --- Internet Radio: live stations by name (grouped under the Music section). Parse the
    // cache inline on first search so stations are included without opening the browser first. ---
    if (property_get_bool("persist.gammaos.nano.radio", true)) {
        radioEnsureLoadedSync();
        size_t rCount = 0;
        std::lock_guard<std::mutex> lk(mRadioMutex);
        for (size_t i = 0; i < mRadioStations.size() && rCount < kMaxPerSection; i++) {
            if (!ciContains(mRadioStations[i].name, query)) continue;
            GSearchResult gr; gr.section = 1; gr.label = mRadioStations[i].name;
            gr.sub = trDyn("Internet Radio"); gr.kind = PS3_RADIO_STATION; gr.a = (int)i;
            gr.payload = mRadioStations[i].url;
            mGSearchResults.push_back(gr); rCount++;
        }
    }

    // Stable order: by section, then label (case-insensitive). std::stable_sort keeps
    // the within-section discovery order for equal labels.
    std::stable_sort(mGSearchResults.begin(), mGSearchResults.end(),
        [](const GSearchResult& a, const GSearchResult& b) {
            if (a.section != b.section) return a.section < b.section;
            return strcasecmp(a.label.c_str(), b.label.c_str()) < 0;
        });

    mGSearchActive = true;
    mGSearchAnim = 0.0f;
}

// Visual row of a result (each section is preceded by one header row).
int NanoMenu::gsearchVisRow(int resultIdx) const {
    int row = 0, prevSection = -1;
    for (int k = 0; k < (int)mGSearchResults.size(); k++) {
        if (mGSearchResults[k].section != prevSection) { prevSection = mGSearchResults[k].section; row++; }
        if (k == resultIdx) return row;   // this item's row
        row++;
    }
    return row;
}

// Total visual rows = one header per section + one row per result. Mirrors gsearchVisRow's numbering.
int NanoMenu::gsearchTotalVisRows() const {
    int row = 0, prevSection = -1;
    for (const auto& g : mGSearchResults) { if (g.section != prevSection) { prevSection = g.section; row++; } row++; }
    return row;
}

// Inverse of gsearchVisRow: the result index at visual row `target`, or -1 if that row is a
// section header or out of range. Header rows sit at the section-change position (row before the
// first item of each section), exactly as gsearchVisRow / renderGlobalSearch lay them out.
int NanoMenu::gsearchResultAtVisRow(int target) const {
    int row = 0, prevSection = -1;
    for (int k = 0; k < (int)mGSearchResults.size(); k++) {
        if (mGSearchResults[k].section != prevSection) {
            prevSection = mGSearchResults[k].section;
            if (row == target) return -1;   // this row is the section header
            row++;
        }
        if (row == target) return k;        // this row is item k
        row++;
    }
    return -1;
}

void NanoMenu::gsearchMove(int dir) {
    int n = (int)mGSearchResults.size();
    if (n == 0) return;
    mGSearchSel += dir;
    if (mGSearchSel < 0) mGSearchSel = 0;
    if (mGSearchSel >= n) mGSearchSel = n - 1;
}

// Activate the selected result via the owning section's existing path.
void NanoMenu::gsearchActivate() {
    if (mGSearchSel < 0 || mGSearchSel >= (int)mGSearchResults.size()) return;
    GSearchResult r = mGSearchResults[mGSearchSel];   // copy: gsearchClose clears the vector
    switch (r.kind) {
        case PS3_RECENT: {
            gsearchClose();
            mXmbSystemIndex = -1; mXmbGameIndex = r.a; mSearchActive = false;
            if (mOverlayMode) { overlayLaunchGame(); return; }
            launchXmbGame();
            return;
        }
        case PS3_ROM: {
            gsearchClose();
            mXmbSystemIndex = r.a; mXmbGameIndex = r.b; mSearchActive = false;
            if (mOverlayMode) { overlayLaunchGame(); return; }
            launchXmbGame();
            return;
        }
        case PS3_APP: {
            if (r.payload.empty()) return;
            gsearchClose();
            if (mOverlayMode) { overlayLaunchPackage(r.payload); return; }
            if (!isLaunchReady()) { showLaunchBusyToast(); return; }
            nano_power::applyDefaultForPackage(r.payload.c_str());
            property_set("sys.gammaos.nano.launch_app", r.payload.c_str());
            property_set("sys.gammaos.nano.launched_pkg", r.payload.c_str());
            setLaunchRomPath("");
            property_set("sys.gammaos.nano.launch_core", "");
            property_set("persist.gammaos.nano.qr_prepared", "0");
            property_set("persist.gammaos.nano.qr_core", "");
            property_set("sys.gammaos.nano.return_apps", "1");
            property_set("service.bootanim.nano_retroarch", "1");
            property_set("sys.gammaos.nano.drop_input", "1");
            mWaitForRelease = true;
            return;
        }
        case PS3_MUSIC_TRACK: {
            gsearchClose();
            std::vector<Ps3Item> list;
            Ps3Item it; it.kind = PS3_MUSIC_TRACK; it.a = r.a; list.push_back(it);
            openMusicPlayer(list, 0);
            return;
        }
        case PS3_VIDEO_FILE: {
            gsearchClose();
            std::vector<Ps3Item> list;
            Ps3Item it; it.kind = PS3_VIDEO_FILE; it.a = r.a; list.push_back(it);
            openVideoPlayer(list, 0);
            return;
        }
        case PS3_PHOTO: {
            gsearchClose();
            std::vector<int> list; list.push_back(r.a);
            openPhotoViewer(list, 0);
            return;
        }
        case PS3_IPTV_CHANNEL: {
            gsearchClose();
            std::vector<VidStreamRef> q;
            VidStreamRef s; s.name = r.label; s.url = r.payload; q.push_back(s);
            openIptvStream(q, 0);
            return;
        }
        case PS3_RADIO_STATION: {
            gsearchClose();
            std::vector<Ps3Item> list;
            Ps3Item it; it.kind = PS3_RADIO_STATION; it.label = r.label; it.payloadStr = r.payload;
            list.push_back(it);
            openRadioStation(list, 0);
            return;
        }
        default: return;
    }
}

// Full-screen categorized results overlay. Models renderPhotoGrid: a dark scrim over
// the live wave, a title + query line, then section headers and result rows with the
// selected row highlighted, and a hint bar. Scrolls when the list overflows.
void NanoMenu::renderGlobalSearch() {
    int W = mWidth, H = mHeight;
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    mGSearchAnim += (1.0f - mGSearchAnim) * (1.0f - expf(-13.0f * dt));
    if (mGSearchAnim > 0.999f) mGSearchAnim = 1.0f;
    float a = mGSearchAnim;
    float slide = (1.0f - a) * 24.0f;
    float ts = fmaxf(1.0f, (float)H / 768.0f);

    // Minima theme: a flat black results list in the Minima language - the query in the accent + a
    // result count, then each hit as a white row (selected in a white capsule) with its section/sub in
    // the accent on the right. Nav stays on the shared gsearch* handlers (mGSearchSel).
    if (mMinimaTheme) {
        setUiBlend();
        const int prevOutline = mTextOutlineMode; mTextOutlineMode = 2;
        const float kF = 16.0f;
        float ar, ag, ab; minimaAccent(ar, ag, ab);
        const float rw = (float)W, rh = (float)H;
        const float sc = rh / 336.0f;
        const float pad = 10.0f * sc, rowH = 30.0f * sc, btnPad = 12.0f * sc;
        drawQuad(0.0f, 0.0f, rw, rh, 0.0f, 0.0f, 0.0f, 0.96f * a);
        std::string hdr = std::string("\"") + mGSearchQuery + "\"";
        drawText(hdr.c_str(), pad + btnPad + slide, pad, (18.0f * sc) / kF, ar, ag, ab, a);
        int nres = (int)mGSearchResults.size();
        { char c[48]; snprintf(c, sizeof(c), "%d %s", nres, nres == 1 ? trDyn("result") : trDyn("results"));
          float cfs = (13.0f * sc) / kF, cw = measureText(c, cfs);
          drawText(c, rw - pad - cw, pad + 3.0f * sc, cfs, 0.6f, 0.6f, 0.65f, a); }
        const float listTop = pad + rowH * 1.15f, listBot = rh - pad;
        if (nres == 0) {
            char msg[192]; snprintf(msg, sizeof(msg), "%s \"%s\"", trDyn("No results for"), mGSearchQuery.c_str());
            float fs = (15.0f * sc) / kF, mw = measureText(msg, fs);
            if (mw > rw * 0.9f && mw > 1.0f) { fs *= rw * 0.9f / mw; mw = measureText(msg, fs); }
            drawText(msg, rw * 0.5f - mw * 0.5f, rh * 0.45f, fs, 0.6f, 0.6f, 0.6f, a);
        } else {
            int sel = mGSearchSel; if (sel < 0) sel = 0; if (sel >= nres) sel = nres - 1;
            int visRows = (int)fmaxf(1.0f, floorf((listBot - listTop) / rowH));
            int top = sel - visRows / 2; if (top > nres - visRows) top = nres - visRows; if (top < 0) top = 0;
            const float fsR = (16.0f * sc) / kF;
            for (int i = top; i < nres && i < top + visRows; i++) {
                const auto& r = mGSearchResults[i];
                float rowY = listTop + (float)(i - top) * rowH + slide;
                float ty = rowY + (rowH - 16.0f * sc) * 0.5f;
                std::string subv = r.sub.empty() ? std::string(trDyn(kSectionName[(r.section >= 0 && r.section < 5) ? r.section : 0]))
                                                 : r.sub;
                float subFs = fsR * 0.8f, subW = measureText(subv.c_str(), subFs);
                float lblMaxW = rw - 2.0f * pad - btnPad * 2.0f - subW - 14.0f * sc;
                std::string lbl = r.label;
                float fs = fsR, tw = measureText(lbl.c_str(), fs);
                if (tw > lblMaxW && lblMaxW > 0.0f) { fs *= lblMaxW / tw; tw = measureText(lbl.c_str(), fs); }
                if (i == sel) {
                    float pillH = rowH * 0.86f, pillW = fminf(tw + btnPad * 2.0f, rw - 2.0f * pad);
                    drawRoundedRect(pad + slide, rowY + rowH * 0.07f, pillW, pillH, pillH * 0.5f, 1.0f, 1.0f, 1.0f, a);
                    drawText(lbl.c_str(), pad + btnPad + slide, ty, fs, 0.0f, 0.0f, 0.0f, a);
                } else {
                    drawText(lbl.c_str(), pad + btnPad + slide, ty, fs, 1.0f, 1.0f, 1.0f, a);
                }
                drawText(subv.c_str(), rw - pad - subW, ty, subFs, ar, ag, ab, 0.85f * a);
            }
        }
        mTextOutlineMode = prevOutline;
        return;
    }

    // DSi theme: render the results as the DSi System-Settings-style dark glossy list on the bottom
    // screen (matching renderNdsPickerList / renderNdsSidePanel) instead of the XMB dark overlay, so
    // search fits the DSi look when that theme is selected. Header band = query + result count; each
    // result is a glossy button (label left, section/subtitle right); section names are thin blue
    // dividers between them. Whole-row scroll via mGSearchScrollRow; tap handling is gsearchTouch.
    if (mNdsTheme) {
        setUiBlend();
        const float kFontH = 16.0f;                                   // DS glyph cell height (FONT_CHAR_H)
        const bool ndsPrevFont = mNdsFontPref; mNdsFontPref = true;
        const int  ndsPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;   // DSi text is flat
        float scale = (float)H / 192.0f;
        if (256.0f * scale > (float)W + 0.5f) scale = (float)W / 256.0f;
        const float offY = ((float)H - 192.0f * scale) * 0.5f;
        const float cx = (float)W * 0.5f;
        auto Y = [&](float d){ return offY + d * scale; };
        auto S = [&](float v){ return v * scale; };
        auto X = [&](float d){ return cx + (d - 128.0f) * scale; };
        const float lh = fmaxf(1.0f, S(1.0f));
        const int nres = (int)mGSearchResults.size();

        // background: #383838 scanline field + darker #303030 header band (renderNdsPickerList parity)
        drawQuad(0, 0, (float)W, (float)H, 0.220f, 0.220f, 0.220f, 1.0f);
        for (float yy = 0; yy < (float)H; yy += S(2.0f)) drawQuad(0, yy, (float)W, lh, 0.255f, 0.255f, 0.255f, 1.0f);
        drawQuad(0, 0, (float)W, Y(23.0f), 0.188f, 0.188f, 0.188f, 1.0f);
        for (float yy = 0; yy < Y(23.0f); yy += S(2.0f)) drawQuad(0, yy, (float)W, lh, 0.220f, 0.220f, 0.220f, 1.0f);

        // header: query (left, shrunk to fit) + result count (right, blue)
        { float fs = S(13.0f) / kFontH;
          char q[96]; snprintf(q, sizeof(q), "\"%s\"", mGSearchQuery.c_str());
          float qmaxW = X(178.0f) - X(6.0f), qw = measureText(q, fs);
          if (qw > qmaxW && qw > 1.0f) fs *= qmaxW / qw;
          drawText(q, X(6.0f), Y(4.0f), fs, 0.984f, 0.984f, 0.984f, 1.0f);
          char c[48]; snprintf(c, sizeof(c), "%d %s", nres, nres == 1 ? trDyn("result") : trDyn("results"));
          float cfs = S(11.0f) / kFontH, cw = measureText(c, cfs);
          drawText(c, X(250.0f) - cw, Y(5.0f), cfs, 0.62f, 0.78f, 1.0f, 1.0f); }
        for (float xx = X(2.0f); xx < X(254.0f); xx += S(4.0f)) drawQuad(xx, Y(21.0f), fmaxf(1.0f, S(2.0f)), lh, 0.510f, 0.510f, 0.510f, 1.0f);

        if (nres == 0) {
            char msg[160]; snprintf(msg, sizeof(msg), "%s \"%s\"", trDyn("No results for"), mGSearchQuery.c_str());
            float fs = S(13.0f) / kFontH, mw = measureText(msg, fs);
            if (mw > S(236.0f) && mw > 1.0f) fs *= S(236.0f) / mw, mw = measureText(msg, fs);
            drawText(msg, cx - mw * 0.5f, Y(92.0f), fs, 0.70f, 0.70f, 0.70f, 1.0f);
        } else {
            const int fitRows = kNdsFitRows();
            const int totalRows = gsearchTotalVisRows();
            // keep the selected item's visual row (and its section header) in view; whole-row scroll
            int selVis = gsearchVisRow(mGSearchSel);
            if (selVis < mGSearchScrollRow + 1) mGSearchScrollRow = selVis - 1;
            if (selVis > mGSearchScrollRow + fitRows - 1) mGSearchScrollRow = selVis - fitRows + 1;
            int maxScroll = totalRows - fitRows; if (maxScroll < 0) maxScroll = 0;
            if (mGSearchScrollRow > maxScroll) mGSearchScrollRow = maxScroll;
            if (mGSearchScrollRow < 0) mGSearchScrollRow = 0;

            for (int vi = 0; vi < fitRows; vi++) {
                int rr = mGSearchScrollRow + vi;
                if (rr < 0 || rr >= totalRows) continue;
                float rowY = kNdsListTop + (float)vi * kNdsPitch;
                int ridx = gsearchResultAtVisRow(rr);
                if (ridx < 0) {                                        // a section header row
                    int below = gsearchResultAtVisRow(rr + 1);
                    int sec = (below >= 0 && below < nres) ? mGSearchResults[below].section : -1;
                    if (sec >= 0 && sec < 5) {
                        float fs = S(10.0f) / kFontH;
                        drawText(trDyn(kSectionName[sec]), X(kNdsBx + 2.0f), Y(rowY + 5.0f), fs, 0.55f, 0.75f, 1.0f, 1.0f);
                    }
                    drawQuad(X(kNdsBx), Y(rowY + kNdsPitch - 4.0f), S(kNdsBw), lh, 0.45f, 0.45f, 0.45f, 1.0f);
                    continue;
                }
                const GSearchResult& g = mGSearchResults[ridx];
                bool sel = (ridx == mGSearchSel);
                drawNdsGlossyBtn(X(kNdsBx), Y(rowY), S(kNdsBw), S(kNdsBtnH), S(4.0f), sel);
                float ic = sel ? 1.0f : 0.157f;                        // white on blue sel / #282828 idle
                float subc = sel ? 0.85f : 0.42f;
                float sfs = S(10.0f) / kFontH;
                float subw = g.sub.empty() ? 0.0f : measureText(g.sub.c_str(), sfs);
                float fs = S(12.0f) / kFontH;
                float labMaxW = S(kNdsBw - 16.0f) - (subw > 0.0f ? subw + S(10.0f) : 0.0f);
                float lw = measureText(g.label.c_str(), fs);
                if (lw > labMaxW && lw > 1.0f) fs *= labMaxW / lw;
                drawText(g.label.c_str(), X(kNdsBx + 8.0f), Y(rowY + 5.0f), fs, ic, ic, ic, 1.0f);
                if (subw > 0.0f)
                    drawText(g.sub.c_str(), X(kNdsBx + kNdsBw - 8.0f) - subw, Y(rowY + 6.0f), sfs, subc, subc, subc, 1.0f);
            }
            // right-edge scrollbar when the list overflows (DSi track + thumb)
            if (totalRows > fitRows) {
                float trackH = S(kNdsListBot - kNdsListTop);
                drawQuad(X(250.0f), Y(kNdsListTop), S(6.0f), trackH, 0.125f, 0.125f, 0.125f, 1.0f);
                float thumbH = trackH * (float)fitRows / (float)totalRows;
                float thumbY = Y(kNdsListTop) + (trackH - thumbH) * ((float)mGSearchScrollRow / (float)(totalRows - fitRows));
                drawQuad(X(250.0f), thumbY, S(5.0f), thumbH, 0.827f, 0.827f, 0.827f, 1.0f);
            }
        }

        // bottom hint bar (Back / OK), identical to renderNdsPickerList.
        drawQuad(0, Y(171.0f), (float)W, lh, 0.443f, 0.443f, 0.443f, 1.0f);
        { const int NB = 14; float bandH = (Y(186.0f) - Y(172.0f)) / (float)NB;
          for (int b = 0; b < NB; b++) { float t = (float)b / (float)(NB - 1); float c = 0.349f * (1.0f - t) + 0.188f * t;
              drawQuad(0, Y(172.0f) + (float)b * bandH, (float)W, bandH + 0.6f, c, c, c, 1.0f); }
          drawQuad(0, Y(186.0f), (float)W, Y(192.0f) - Y(186.0f), 0.188f, 0.188f, 0.188f, 1.0f); }
        { float fs = S(11.0f) / kFontH;
          drawText("Back", X(8.0f), Y(176.0f), fs, 0.898f, 0.898f, 0.898f, 1.0f);
          if (nres > 0) { float tw = measureText("OK", fs); drawText("OK", X(248.0f) - tw, Y(176.0f), fs, 0.898f, 0.898f, 0.898f, 1.0f); } }

        mTextOutlineMode = ndsPrevOutline;
        mNdsFontPref = ndsPrevFont;
        mDisplayDirty = true;   // transient full-screen list: keep it painting while open
        return;
    }

    drawQuad(0, 0, (float)W, (float)H, 0.04f, 0.05f, 0.06f, 0.90f * a);

    float margin = W * 0.06f;
    drawText(trDyn("Search Results"), margin, 34.0f * ts + slide, 1.7f * ts, 1.0f, 1.0f, 1.0f, a);
    char info[160];
    int n = (int)mGSearchResults.size();
    if (n == 0)
        snprintf(info, sizeof(info), "%s \"%s\"", trDyn("No results for"), mGSearchQuery.c_str());
    else
        snprintf(info, sizeof(info), "\"%s\"   %d %s", mGSearchQuery.c_str(), n, n == 1 ? trDyn("result") : trDyn("results"));
    drawText(info, margin, 84.0f * ts + slide, 1.0f * ts, 0.75f, 0.85f, 0.95f, a);

    // Build the flat visual-row list (header rows + item rows). Cheap: results capped.
    struct VRow { bool header; int section; int resultIdx; };
    std::vector<VRow> rows;
    rows.reserve(n + 4);
    int prevSection = -1;
    for (int i = 0; i < n; i++) {
        if (mGSearchResults[i].section != prevSection) {
            prevSection = mGSearchResults[i].section;
            rows.push_back({ true, prevSection, -1 });
        }
        rows.push_back({ false, mGSearchResults[i].section, i });
    }

    float top = 132.0f * ts + slide;
    float rowH = 30.0f * ts;
    float hintSpace = 64.0f * ts;
    int visRows = (int)((H - top - hintSpace) / rowH);
    if (visRows < 1) visRows = 1;

    // Keep the selected item's visual row in view.
    int selVis = -1;
    for (int rr = 0; rr < (int)rows.size(); rr++)
        if (!rows[rr].header && rows[rr].resultIdx == mGSearchSel) { selVis = rr; break; }
    if (selVis >= 0) {
        if (selVis < mGSearchScrollRow + 1) mGSearchScrollRow = selVis - 1;          // keep the header above visible
        if (selVis >= mGSearchScrollRow + visRows) mGSearchScrollRow = selVis - visRows + 1;
    }
    if (mGSearchScrollRow < 0) mGSearchScrollRow = 0;
    int maxScroll = (int)rows.size() - visRows; if (maxScroll < 0) maxScroll = 0;
    if (mGSearchScrollRow > maxScroll) mGSearchScrollRow = maxScroll;

    float pulse = 0.5f + 0.5f * cosf(mEffectTime * 2.0f * 3.14159f / 1.5f);
    for (int vi = 0; vi < visRows; vi++) {
        int rr = mGSearchScrollRow + vi;
        if (rr < 0 || rr >= (int)rows.size()) continue;
        float y = top + vi * rowH;
        const VRow& vr = rows[rr];
        if (vr.header) {
            drawText(trDyn(kSectionName[vr.section]), margin, y + rowH * 0.18f, 0.92f * ts, 0.55f, 0.78f, 1.0f, a);
            continue;
        }
        const GSearchResult& g = mGSearchResults[vr.resultIdx];
        bool sel = (vr.resultIdx == mGSearchSel);
        float ix = margin + 26.0f * ts;   // indent items under their header
        if (sel) {
            float ga = (0.30f + 0.12f * pulse) * a;
            drawQuad(margin, y - 1.0f * ts, (float)W - margin * 2.0f, rowH, 0.85f, 0.90f, 1.0f, ga);
        }
        float lr = sel ? 1.0f : 0.92f, lg = sel ? 1.0f : 0.92f, lb = sel ? 1.0f : 0.92f;
        drawText(g.label.c_str(), ix, y + rowH * 0.16f, 0.95f * ts, lr, lg, lb, a);
        if (!g.sub.empty()) {
            float sw = measureText(g.sub.c_str(), 0.78f * ts);
            drawText(g.sub.c_str(), (float)W - margin - sw, y + rowH * 0.22f, 0.78f * ts,
                     0.62f, 0.70f, 0.80f, a);
        }
    }

    // hint bar
    const char* hint = (n > 0) ? trDyn("Enter  Select       Back  Cancel") : trDyn("Back  Cancel");
    float hs = 0.85f * ts;
    float hw = measureText(hint, hs);
    drawText(hint, (W - hw) * 0.5f, H - hintSpace + 22.0f * ts, hs, 0.85f, 0.90f, 1.0f, a);
}

// DSi theme: touch the results list. Tap a result row to select + activate it, drag the list to
// scroll it, tap the bottom-left "Back" to cancel. Mirrors renderGlobalSearch's DSi layout (the
// shared kNds* constants) so the tapped row maps to exactly the result the renderer drew. Only
// dispatched in the DSi theme once the query keyboard has closed (mGSearchActive && !mOskActive).
void NanoMenu::gsearchTouch() {
    if (mPs3BootActive) { mTouchWasDown = mTouchDown; return; }
    if (!mOverlayMode && mLaunchFadeStart > 0) { mTouchWasDown = mTouchDown; return; }  // frozen during launch
    float px, py; bool mapped = touchMapRaw(mTouchRawX, mTouchRawY, px, py);
    float scale = (float)mHeight / 192.0f;
    if (256.0f * scale > (float)mWidth + 0.5f) scale = (float)mWidth / 256.0f;
    if (scale < 1e-3f) { mTouchWasDown = mTouchDown; return; }
    float offY = ((float)mHeight - 192.0f * scale) * 0.5f;
    float dsX = mapped ? 128.0f + (px - (float)mWidth * 0.5f) / scale : mNdsTouchDownX;
    float dsY = mapped ? (py - offY) / scale : mNdsTouchDownY;

    const int totalRows = gsearchTotalVisRows();
    const int fitRows = kNdsFitRows();
    const int maxScroll = (totalRows > fitRows) ? (totalRows - fitRows) : 0;
    bool down = mTouchDown, downEdge = down && !mTouchWasDown, upEdge = !down && mTouchWasDown;

    if (downEdge && mapped) {
        mNdsTouchMoved = false; mNdsTouchDownX = dsX; mNdsTouchDownY = dsY;
        mNdsSubDownSel = mGSearchScrollRow;                  // scroll origin for a drag
    } else if (down && mapped) {
        if (fabsf(dsY - mNdsTouchDownY) > 8.0f || fabsf(dsX - mNdsTouchDownX) > 8.0f) mNdsTouchMoved = true;
        if (mNdsTouchMoved && maxScroll > 0) {               // drag to scroll (whole-row)
            int ns = mNdsSubDownSel + (int)lroundf((mNdsTouchDownY - dsY) / kNdsPitch);
            if (ns < 0) ns = 0; if (ns > maxScroll) ns = maxScroll;
            if (ns != mGSearchScrollRow) { mGSearchScrollRow = ns; mDisplayDirty = true; }
        }
    } else if (upEdge && !mNdsTouchMoved) {                  // a TAP
        if (mNdsTouchDownY >= kNdsListTop && mNdsTouchDownY <= kNdsListBot &&
            mNdsTouchDownX >= kNdsBx && mNdsTouchDownX <= kNdsBx + kNdsBw) {
            int vi = (int)floorf((mNdsTouchDownY - kNdsListTop) / kNdsPitch);
            float rowY = kNdsListTop + (float)vi * kNdsPitch;   // reject taps in the inter-row gap
            if (vi >= 0 && vi < fitRows && mNdsTouchDownY <= rowY + kNdsBtnH) {
                int ridx = gsearchResultAtVisRow(mGSearchScrollRow + vi);   // -1 for a section header
                if (ridx >= 0 && ridx < (int)mGSearchResults.size()) {
                    mGSearchSel = ridx;
                    gsearchActivate();   // select + launch/open (clears the search state itself)
                    // A touch tap has no select-key release, so stamp the launch fade here (as
                    // ndsTouchFrame does) or an armed game/app launch would hang forever.
                    if (mWaitForRelease && !mOverlayMode && mLaunchFadeStart == 0)
                        mLaunchFadeStart = uptimeMillis();
                }
            }
        } else if (mNdsTouchDownY >= 170.0f && mNdsTouchDownX < 60.0f) {
            gsearchClose();   // bottom-left "Back" -> cancel
        }
    }
    mTouchWasDown = mTouchDown;
}

}  // namespace android
