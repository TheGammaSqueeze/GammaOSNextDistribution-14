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
        mScrapeIndex[rom] = std::move(e);
    }
    ALOGD("scraper: loaded %zu manifest entries", mScrapeIndex.size());
}

void NanoMenu::saveScrapeIndex() {
    njson::Value root = njson::Value::makeObject();
    root.set("version") = njson::Value::makeNumber(1);
    njson::Value items = njson::Value::makeArray();
    for (const auto& kv : mScrapeIndex) {
        njson::Value o = njson::Value::makeObject();
        o.set("rom")     = njson::Value::makeString(kv.first);
        if (!kv.second.box.empty())     o.set("box")     = njson::Value::makeString(kv.second.box);
        if (!kv.second.fan.empty())     o.set("fan")     = njson::Value::makeString(kv.second.fan);
        if (!kv.second.title.empty())   o.set("title")   = njson::Value::makeString(kv.second.title);
        if (!kv.second.scraper.empty()) o.set("scraper") = njson::Value::makeString(kv.second.scraper);
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
