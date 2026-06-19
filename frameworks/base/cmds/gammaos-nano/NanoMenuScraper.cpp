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
 */

// NanoMenu glue for the boxart/cover scraper: the on-disk manifest, the
// background scrape worker (mirrors the music scanner: snapshot inputs, work
// without locks, publish under a mutex, drain on the render thread), per-system
// credential/engine resolution, and the progress modal. The network + image
// fetching lives in NanoScraper.cpp; this file owns the NanoMenu state.

#include "NanoMenu.h"
#include "NanoScraper.h"
#include "NanoJson.h"
#include "stb_image.h"   // stbi_load (impl in NanoMenuPS3Icons.cpp); AImageDecoder fails on the scrape art

#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <time.h>
#include <string.h>
#include <thread>
#include <cmath>

#include <cutils/properties.h>
#include <log/log.h>

namespace android {

static const char* kScrapeIndexPath = "/data/system/nano_scrape/index.json";

// ---------------------------------------------------------------------------
// Manifest (index.json): romPath -> {box, fan, title, scraper, when}
// ---------------------------------------------------------------------------
void NanoMenu::scraperEnsureLoaded() {
    if (mScrapeIndexLoaded) return;
    mScrapeIndexLoaded = true;
    // Make sure the cache dir exists (0700, owned by system).
    mkdir(mScrapeCacheDir.c_str(), 0700);
    loadScrapeIndex();
}

void NanoMenu::loadScrapeIndex() {
    mScrapeIndex.clear();
    int fd = open(kScrapeIndexPath, O_RDONLY);
    if (fd < 0) return;
    std::string content;
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size > 0 && st.st_size < 8 * 1024 * 1024) {
        content.resize(st.st_size);
        ssize_t rd = read(fd, &content[0], st.st_size);
        if (rd > 0) content.resize(rd); else content.clear();
    }
    close(fd);
    if (content.empty()) return;
    njson::Value root;
    if (!njson::parse(content, &root) || !root.isObject()) return;
    const njson::Value* items = root.find("items");
    if (!items || !items->isArray()) return;
    for (const auto& it : items->arr) {
        if (!it.isObject()) continue;
        std::string rom = it.getString("rom");
        if (rom.empty()) continue;
        ScrapeEntry e;
        e.box     = it.getString("box");
        e.fan     = it.getString("fan");
        e.title   = it.getString("title");
        e.scraper = it.getString("scraper");
        e.when    = (long long)it.getInt("when", 0);
        e.synopsis    = it.getString("desc");
        e.genre       = it.getString("genre");
        e.players     = it.getString("players");
        e.rating      = it.getString("rating");
        e.releaseDate = it.getString("date");
        e.developer   = it.getString("dev");
        e.publisher   = it.getString("pub");
        mScrapeIndex[rom] = std::move(e);
    }
    ALOGD("scraper: loaded %zu manifest entries", mScrapeIndex.size());
}

void NanoMenu::saveScrapeIndex() {
    njson::Value root = njson::Value::makeObject();
    root.set("version") = njson::Value::makeNumber(2);
    njson::Value items = njson::Value::makeArray();
    for (const auto& kv : mScrapeIndex) {
        njson::Value o = njson::Value::makeObject();
        o.set("rom")     = njson::Value::makeString(kv.first);
        if (!kv.second.box.empty())     o.set("box")     = njson::Value::makeString(kv.second.box);
        if (!kv.second.fan.empty())     o.set("fan")     = njson::Value::makeString(kv.second.fan);
        if (!kv.second.title.empty())   o.set("title")   = njson::Value::makeString(kv.second.title);
        if (!kv.second.scraper.empty()) o.set("scraper") = njson::Value::makeString(kv.second.scraper);
        if (!kv.second.synopsis.empty())    o.set("desc")    = njson::Value::makeString(kv.second.synopsis);
        if (!kv.second.genre.empty())       o.set("genre")   = njson::Value::makeString(kv.second.genre);
        if (!kv.second.players.empty())     o.set("players") = njson::Value::makeString(kv.second.players);
        if (!kv.second.rating.empty())      o.set("rating")  = njson::Value::makeString(kv.second.rating);
        if (!kv.second.releaseDate.empty()) o.set("date")    = njson::Value::makeString(kv.second.releaseDate);
        if (!kv.second.developer.empty())   o.set("dev")     = njson::Value::makeString(kv.second.developer);
        if (!kv.second.publisher.empty())   o.set("pub")     = njson::Value::makeString(kv.second.publisher);
        o.set("when")    = njson::Value::makeNumber((double)kv.second.when);
        items.arr.push_back(std::move(o));
    }
    root.set("items") = std::move(items);
    std::string text = njson::serialize(root, true);
    std::string tmp = std::string(kScrapeIndexPath) + ".tmp";
    int fd = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) { ALOGW("scraper: cannot write %s", tmp.c_str()); return; }
    ssize_t wr = write(fd, text.data(), text.size());
    close(fd);
    if (wr == (ssize_t)text.size()) rename(tmp.c_str(), kScrapeIndexPath);
    else                            unlink(tmp.c_str());
}

const NanoMenu::ScrapeEntry* NanoMenu::scrapeEntryFor(const std::string& romPath) {
    scraperEnsureLoaded();
    auto it = mScrapeIndex.find(romPath);
    return (it != mScrapeIndex.end()) ? &it->second : nullptr;
}

bool NanoMenu::scraperBoxartEnabled() {
    return property_get_bool("persist.gammaos.scraper.boxart", true);
}

// Decode scraped art to a GL texture via stb_image. AImageDecoder
// (photoDecodeTex) silently returns failure on the scrape PNGs on this device,
// while stb_image decodes them fine (it is what the cinfo bg uses). maxDim>0
// downscales (nearest, cheap) to bound VRAM for the small icon slot.
GLuint NanoMenu::scraperDecodeTex(const std::string& path, int maxDim, float* outAR) {
    int w = 0, h = 0, n = 0;
    stbi_uc* d = stbi_load(path.c_str(), &w, &h, &n, 4);
    if (!d || w <= 0 || h <= 0) { if (d) stbi_image_free(d); return 0; }
    if (outAR) *outAR = (float)w / (float)h;
    const stbi_uc* src = d; int sw = w, sh = h;
    std::vector<stbi_uc> small;
    int longSide = w > h ? w : h;
    if (maxDim > 0 && longSide > maxDim) {
        float s = (float)maxDim / (float)longSide;
        int tw = (int)(w * s + 0.5f), th = (int)(h * s + 0.5f);
        if (tw < 1) tw = 1; if (th < 1) th = 1;
        small.resize((size_t)tw * th * 4);
        for (int y = 0; y < th; y++) {
            int sy = (int)(((float)y + 0.5f) / th * h); if (sy >= h) sy = h - 1;
            for (int x = 0; x < tw; x++) {
                int sx = (int)(((float)x + 0.5f) / tw * w); if (sx >= w) sx = w - 1;
                memcpy(&small[((size_t)y * tw + x) * 4], &d[((size_t)sy * w + sx) * 4], 4);
            }
        }
        src = small.data(); sw = tw; sh = th;
    }
    GLuint t = 0; glGenTextures(1, &t); glBindTexture(GL_TEXTURE_2D, t);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, sw, sh, 0, GL_RGBA, GL_UNSIGNED_BYTE, src);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    stbi_image_free(d);
    return t;
}

// Lazy per-ROM cover texture. Decodes the scraped cover (scaled) on first use and
// caches the GLuint + aspect ratio; 0 means "no art / tried". Called from the
// render thread (drawList), so GL is current. Freed by scraperFreeBoxart().
GLuint NanoMenu::romBoxartTex(const std::string& romPath, float* outAR) {
    auto it = mRomBoxartCache.find(romPath);
    if (it != mRomBoxartCache.end()) { if (outAR) *outAR = it->second.ar; return it->second.tex; }
    BoxTex bt;
    const ScrapeEntry* e = scrapeEntryFor(romPath);
    if (e && !e->box.empty())
        bt.tex = scraperDecodeTex(e->box, 256, &bt.ar);
    // Backstop: if the cache grows large (browsing many systems without leaving
    // Game), free it all and rebuild lazily. The free-on-leave-Game path is the
    // normal lifecycle; this only guards a pathological session.
    if (mRomBoxartCache.size() >= 96) scraperFreeBoxart();
    mRomBoxartCache[romPath] = bt;
    if (outAR) *outAR = bt.ar;
    return bt.tex;
}

void NanoMenu::scraperFreeBoxart() {
    for (auto& kv : mRomBoxartCache)
        if (kv.second.tex) glDeleteTextures(1, &kv.second.tex);
    mRomBoxartCache.clear();
    // Also drop the hover-fanart texture (Phase 4) so no scraper GL lingers.
    if (mFanartTex) { glDeleteTextures(1, &mFanartTex); mFanartTex = 0; }
    mFanartPath.clear(); mFanartTexW = mFanartTexH = 0;
}
bool NanoMenu::scraperFanartEnabled() {
    return property_get_bool("persist.gammaos.scraper.fanart", true);
}

// ---------------------------------------------------------------------------
// Per-system credential / engine resolution (global Settings + per-system
// override). Empty per-system fields inherit the global value.
// ---------------------------------------------------------------------------
nanoscraper::Credentials NanoMenu::scraperCredsFor(int sysIdx) {
    nanoscraper::Credentials c;
    char buf[PROPERTY_VALUE_MAX];
    property_get("persist.gammaos.scraper.ssdevid", buf, ""); c.ssDevId = buf;
    property_get("persist.gammaos.scraper.ssdevpw", buf, ""); c.ssDevPw = buf;
    property_get("persist.gammaos.scraper.ssuser",  buf, ""); c.ssUser  = buf;
    property_get("persist.gammaos.scraper.sspass",  buf, ""); c.ssPass  = buf;
    property_get("persist.gammaos.scraper.tgdbkey", buf, ""); c.tgdbKey = buf;
    property_get("persist.gammaos.scraper.region",  buf, "us"); c.region = buf;
    if (sysIdx >= 0 && sysIdx < (int)mXmbSystems.size()) {
        const XmbSystem& s = mXmbSystems[sysIdx];
        if (!s.scrapeUser.empty()) c.ssUser = s.scrapeUser;   // account override
        if (!s.scrapePass.empty()) c.ssPass = s.scrapePass;
        if (!s.scrapeKey.empty())  c.tgdbKey = s.scrapeKey;
    }
    return c;
}

nanoscraper::Engine NanoMenu::scraperEngineFor(int sysIdx, const nanoscraper::Credentials& cred) {
    (void)cred;
    std::string eng;
    if (sysIdx >= 0 && sysIdx < (int)mXmbSystems.size()) {
        const std::string& ov = mXmbSystems[sysIdx].scraperOverride;
        if (ov == "off")  return nanoscraper::ENGINE_OFF;
        if (!ov.empty())  eng = ov;   // explicit per-system engine
    }
    if (eng.empty()) {
        char buf[PROPERTY_VALUE_MAX];
        property_get("persist.gammaos.scraper.engine", buf, "screenscraper");
        eng = buf;
    }
    return nanoscraper::engineFromName(eng);
}

// ---------------------------------------------------------------------------
// Kick off a scrape of the given systems (render thread). Builds the job list
// (skipping already-scraped ROMs unless Overwrite is on), opens the modal, and
// launches the worker.
// ---------------------------------------------------------------------------
void NanoMenu::scrapeSystemsAsync(const std::vector<int>& sysIdxs) {
    if (mScrapeRunning) return;
    scraperEnsureLoaded();
    mkdir(mScrapeCacheDir.c_str(), 0700);

    bool wantBox = scraperBoxartEnabled();
    bool wantFan = scraperFanartEnabled();
    bool overwrite = property_get_bool("persist.gammaos.scraper.overwrite", false);

    std::vector<ScrapeJob> jobs;
    bool anyConfigured = false;     // at least one system had a usable engine + creds
    for (int sysIdx : sysIdxs) {
        if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) continue;
        const XmbSystem& s = mXmbSystems[sysIdx];
        if (!s.enabled) continue;
        nanoscraper::Credentials cred = scraperCredsFor(sysIdx);
        nanoscraper::Engine eng = scraperEngineFor(sysIdx, cred);
        if (eng == nanoscraper::ENGINE_OFF) continue;
        // Skip systems whose engine has no credentials at all (nothing to do).
        bool haveCreds = (eng == nanoscraper::ENGINE_SCREENSCRAPER)
                             ? (!cred.ssDevId.empty() && !cred.ssDevPw.empty())
                             : !cred.tgdbKey.empty();
        if (!haveCreds) continue;
        anyConfigured = true;
        nanoscraper::PlatformIds plat =
            nanoscraper::platformForSystem(s.romDir, s.shortname, s.name, s.scrapePlatform);
        for (size_t i = 0; i < s.roms.size(); i++) {
            const std::string& rom = s.roms[i];
            if (!overwrite) {
                auto it = mScrapeIndex.find(rom);
                if (it != mScrapeIndex.end() && (!it->second.box.empty() || !it->second.fan.empty()))
                    continue;   // already have art
            }
            ScrapeJob j;
            j.romPath = rom;
            j.displayName = (i < s.displayNames.size()) ? s.displayNames[i] : rom;
            j.sysName = s.name;
            j.engine = (int)eng;
            j.cred = cred;
            j.plat = plat;
            jobs.push_back(std::move(j));
        }
    }

    // Show the modal regardless so the user gets feedback (incl. "set credentials").
    mScrapeProgActive = true;
    mScrapeDoneFlag = false;
    mScrapeCancel = false;
    mScrapeError.clear();
    mScrapeStatus.clear();
    mScrapeDone = 0; mScrapeHits = 0; mScrapeFail = 0;
    mScrapeTotal = (int)jobs.size();
    mDisplayDirty = true;

    if (jobs.empty()) {
        // Nothing to do: explain why (no credentials, or already complete).
        mScrapeError = anyConfigured ? "All games already have art (enable Overwrite to refresh)."
                                     : "Set your scraper credentials in Settings first.";
        mScrapeDoneFlag = true;
        mScrapeBox = wantBox; mScrapeFan = wantFan;   // (unused, keeps -Wunused quiet via assign)
        return;
    }

    mScrapeBox = wantBox; mScrapeFan = wantFan;
    mScrapeRunning = true;
    std::thread(&NanoMenu::scrapeThreadFunc, this, std::move(jobs)).detach();
}

void NanoMenu::scrapeAllSystems() {
    std::vector<int> all;
    for (int i = 0; i < (int)mXmbSystems.size(); i++)
        if (mXmbSystems[i].enabled) all.push_back(i);
    scrapeSystemsAsync(all);
}

void NanoMenu::scrapeOneSystem(int sysIdx) {
    scrapeSystemsAsync(std::vector<int>{sysIdx});
}

// ---------------------------------------------------------------------------
// Worker thread: scrape each job, publishing finished art + progress under the
// mutex. Never touches mXmbSystems (everything is snapshotted in the jobs).
// ---------------------------------------------------------------------------
void NanoMenu::scrapeThreadFunc(std::vector<ScrapeJob> jobs) {
    bool wantBox = mScrapeBox, wantFan = mScrapeFan;
    int idx = 0;
    for (auto& j : jobs) {
        {
            std::lock_guard<std::mutex> lk(mScrapeMutex);
            if (mScrapeCancel) break;
            mScrapeStatus = j.sysName + " / " + j.displayName;
        }
        char tag[24];
        snprintf(tag, sizeof(tag), "%d", idx++);
        nanoscraper::ScrapeOutcome r = nanoscraper::scrapeRom(
            (nanoscraper::Engine)j.engine, j.cred, j.romPath, j.displayName,
            j.plat, wantBox, wantFan, mScrapeCacheDir, tag);

        ScrapeEntry e;
        e.box = r.boxFile; e.fan = r.fanFile; e.title = r.title;
        e.synopsis = r.synopsis; e.genre = r.genre; e.players = r.players;
        e.rating = r.rating; e.releaseDate = r.releaseDate;
        e.developer = r.developer; e.publisher = r.publisher;
        e.scraper = (j.engine == (int)nanoscraper::ENGINE_THEGAMESDB) ? "thegamesdb" : "screenscraper";
        e.when = (long long)time(nullptr);
        {
            std::lock_guard<std::mutex> lk(mScrapeMutex);
            mScrapeDone++;
            if (r.ok) { mScrapeHits++; mScrapePending.emplace_back(j.romPath, std::move(e)); }
            else mScrapeFail++;
            if (!r.ok && r.networkFail && mScrapeError.empty()) mScrapeError = r.error;
        }
    }
    {
        std::lock_guard<std::mutex> lk(mScrapeMutex);
        mScrapeRunning = false;
        mScrapeDoneFlag = true;
    }
    ALOGI("scraper: worker done (%d/%d hits, %d fail)", mScrapeHits, mScrapeTotal, mScrapeFail);
}

// ---------------------------------------------------------------------------
// Render thread: merge finished entries into the manifest + save. Called every
// frame from the main loop; cheap when idle.
// ---------------------------------------------------------------------------
void NanoMenu::scraperDrainResults() {
    std::vector<std::pair<std::string, ScrapeEntry>> pending;
    bool done = false;
    {
        std::lock_guard<std::mutex> lk(mScrapeMutex);
        if (!mScrapePending.empty()) pending.swap(mScrapePending);
        done = mScrapeDoneFlag;
    }
    if (!pending.empty()) {
        for (auto& p : pending) mScrapeIndex[p.first] = std::move(p.second);
        saveScrapeIndex();
        mPs3CatsStale = true;       // refresh columns so new boxart shows (Phase 3)
        mDisplayDirty = true;
    }
    if (done) {
        // Worker finished: keep the summary on screen, the modal closes on B/enter.
        mDisplayDirty = true;
    }
}

void NanoMenu::scraperCancel() {
    std::lock_guard<std::mutex> lk(mScrapeMutex);
    mScrapeCancel = true;
}

// ---------------------------------------------------------------------------
// Progress / result modal. Centered panel over a dim backdrop. While running it
// shows the live count + current game; when done a summary. Dismissed by the
// input layer (B / enter) which clears mScrapeProgActive.
// ---------------------------------------------------------------------------
void NanoMenu::renderScrapeProgress() {
    if (!mScrapeProgActive) return;
    const float W = (float)mWidth, H = (float)mHeight;
    float sf = fminf(W / 1080.0f, H / 720.0f); if (sf < 0.5f) sf = 0.5f;

    int done, total, hits, fail; bool running; std::string status, err;
    {
        std::lock_guard<std::mutex> lk(mScrapeMutex);
        done = mScrapeDone; total = mScrapeTotal; hits = mScrapeHits; fail = mScrapeFail;
        running = mScrapeRunning; status = mScrapeStatus; err = mScrapeError;
    }

    drawQuad(0, 0, W, H, 0.0f, 0.0f, 0.0f, 0.55f);          // dim backdrop
    float pw = W * 0.62f, ph = H * 0.30f;
    float px = (W - pw) * 0.5f, py = (H - ph) * 0.5f;
    drawQuad(px, py, pw, ph, 0.07f, 0.08f, 0.10f, 0.94f);   // panel
    drawQuad(px, py, pw, 3.0f * sf, 0.47f, 0.78f, 1.0f, 0.9f);  // accent top edge

    float cx = W * 0.5f;
    auto centered = [&](const char* s, float y, float scale, float r, float g, float b, float a) {
        float w = measureText(s, scale);
        drawText(s, cx - w * 0.5f, y, scale, r, g, b, a);
    };

    const char* title = "Boxart Scraper";
    centered(title, py + ph * 0.20f, 1.7f * sf, 1.0f, 1.0f, 1.0f, 1.0f);

    char line[256];
    if (!running && mScrapeDoneFlag) {
        if (!err.empty() && hits == 0) {
            centered(err.c_str(), py + ph * 0.52f, 1.0f * sf, 1.0f, 0.85f, 0.6f, 1.0f);
        } else {
            snprintf(line, sizeof(line), "Done. %d game%s with art, %d not found.",
                     hits, hits == 1 ? "" : "s", fail);
            centered(line, py + ph * 0.50f, 1.15f * sf, 0.85f, 1.0f, 0.85f, 1.0f);
        }
        centered("Press X or O to close", py + ph * 0.80f, 0.95f * sf, 0.75f, 0.78f, 0.82f, 0.9f);
    } else {
        snprintf(line, sizeof(line), "%d / %d   (%d found)", done, total, hits);
        centered(line, py + ph * 0.46f, 1.3f * sf, 1.0f, 1.0f, 1.0f, 1.0f);
        // current game (clipped)
        std::string s = status;
        if (measureText(s.c_str(), 0.95f * sf) > pw * 0.9f) {
            while (s.size() > 4 && measureText((s + "...").c_str(), 0.95f * sf) > pw * 0.9f) s.pop_back();
            s += "...";
        }
        centered(s.c_str(), py + ph * 0.66f, 0.95f * sf, 0.78f, 0.82f, 0.88f, 1.0f);
        centered("Press O to cancel", py + ph * 0.86f, 0.9f * sf, 0.7f, 0.72f, 0.76f, 0.85f);
    }
}

} // namespace android
