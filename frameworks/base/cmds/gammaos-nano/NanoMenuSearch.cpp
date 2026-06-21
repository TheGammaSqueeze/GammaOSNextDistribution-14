// NanoMenuSearch.cpp - global search (Select on the home XMB). Opens the on-screen
// keyboard for a query, then shows a categorized results overlay spanning every XMB
// section (Games, Music, Photos, Videos). Activating a result hands off to that
// section's existing launch / open path. Lazy by construction: nothing is built until
// the user runs a search, and gsearchClose() frees the result list so an idle launcher
// holds no search state. Replaces the old (non-functional) Y-to-search.
#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoMenuUtils.h"   // setLaunchRomPath for the Applications launch

#include <algorithm>
#include <string.h>
#include <strings.h>   // strcasestr

#include <cutils/properties.h>

namespace android {

namespace {
const char* kSectionName[5] = { "Games", "Music", "Photos", "Videos", "IPTV" };
// case-insensitive substring match
bool ciContains(const std::string& hay, const std::string& needle) {
    if (needle.empty()) return false;
    return strcasestr(hay.c_str(), needle.c_str()) != nullptr;
}
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
        gr.sub = "Recently Played"; gr.kind = PS3_RECENT; gr.a = (int)i;
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
        gr.sub = "Application"; gr.kind = PS3_APP; gr.payload = app.packageName;
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
            gr.sub = "IPTV"; gr.kind = PS3_IPTV_CHANNEL; gr.a = (int)i;
            gr.payload = mIptvChannels[i].url;
            mGSearchResults.push_back(gr); iCount++;
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

    drawQuad(0, 0, (float)W, (float)H, 0.04f, 0.05f, 0.06f, 0.90f * a);

    float margin = W * 0.06f;
    drawText("Search Results", margin, 34.0f * ts + slide, 1.7f * ts, 1.0f, 1.0f, 1.0f, a);
    char info[160];
    int n = (int)mGSearchResults.size();
    if (n == 0)
        snprintf(info, sizeof(info), "No results for \"%s\"", mGSearchQuery.c_str());
    else
        snprintf(info, sizeof(info), "\"%s\"   %d %s", mGSearchQuery.c_str(), n, n == 1 ? "result" : "results");
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
            drawText(kSectionName[vr.section], margin, y + rowH * 0.18f, 0.92f * ts, 0.55f, 0.78f, 1.0f, a);
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
    const char* hint = (n > 0) ? "Enter  Select       Back  Cancel" : "Back  Cancel";
    float hs = 0.85f * ts;
    float hw = measureText(hint, hs);
    drawText(hint, (W - hw) * 0.5f, H - hintSpace + 22.0f * ts, hs, 0.85f, 0.90f, 1.0f, a);
}

}  // namespace android
