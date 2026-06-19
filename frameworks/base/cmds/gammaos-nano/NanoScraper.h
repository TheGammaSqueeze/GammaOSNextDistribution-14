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

// Boxart / cover scraper engine for GammaOS Nano. Self-contained (no GL): fetches
// game cover + background art from ScreenScraper.fr or TheGamesDB via the system
// curl binary and writes them to a disk cache. Modeled on EmulationStation-DE.
//
// Both services require user-supplied credentials (a ScreenScraper developer
// account, or a TheGamesDB API key); the engine reports a clear error when they
// are missing. The caller (NanoMenuScraper.cpp) runs scrapeRom() on a worker
// thread and consumes the written files on the render thread.

#ifndef GAMMAOS_NANO_SCRAPER_H
#define GAMMAOS_NANO_SCRAPER_H

#include <string>

namespace android {
namespace nanoscraper {

enum Engine {
    ENGINE_SCREENSCRAPER = 0,
    ENGINE_THEGAMESDB    = 1,
    ENGINE_OFF           = 2,
};

// Parse an engine name ("screenscraper" / "thegamesdb" / "off"); default
// ScreenScraper for unknown/empty.
Engine engineFromName(const std::string& name);

struct Credentials {
    // ScreenScraper.fr
    std::string ssDevId;
    std::string ssDevPw;
    std::string ssUser;
    std::string ssPass;
    // TheGamesDB
    std::string tgdbKey;
    // Preferred media region: us | eu | jp | wor
    std::string region;
};

struct PlatformIds {
    int ss   = -1;   // ScreenScraper systemeid
    int tgdb = -1;   // TheGamesDB platform id
};

// Resolve a system to scraper platform ids from its romDir / shortname / name.
// manualSsId (if a positive integer string) forces the ScreenScraper systemeid.
PlatformIds platformForSystem(const std::string& romDir,
                              const std::string& shortname,
                              const std::string& name,
                              const std::string& manualSsId);

struct ScrapeOutcome {
    bool ok = false;            // at least one requested medium was written
    bool networkFail = false;   // could not reach / authenticate the API
    bool credMissing = false;   // required credentials are not configured
    std::string title;          // matched game title (best effort)
    std::string boxFile;        // written cover path, or empty
    std::string fanFile;        // written fanart path, or empty
    std::string error;          // human-readable failure reason
    // Game metadata for the Information screen (best effort; empty = no data).
    std::string synopsis;       // description / overview
    std::string genre;
    std::string players;        // e.g. "1" or "1-2"
    std::string rating;         // ScreenScraper "16/20"; TheGamesDB e.g. "E - Everyone"
    std::string releaseDate;    // "YYYY-MM-DD" or "YYYY"
    std::string developer;
    std::string publisher;
};

// Scrape one ROM. Writes "<key>.box.png" / "<key>.fan.jpg" into cacheDir (key =
// cacheKey(romPath)). tmpTag is a unique per-call scratch suffix so concurrent
// callers do not clobber each other's response/temp files. curlPath = curl binary.
ScrapeOutcome scrapeRom(Engine engine, const Credentials& cred,
                        const std::string& romPath, const std::string& displayName,
                        const PlatformIds& plat, bool wantBox, bool wantFan,
                        const std::string& cacheDir, const std::string& tmpTag,
                        const char* curlPath = "/system/bin/curl");

// Stable 64-bit FNV-1a hex key for a rom path (cache filename stem).
std::string cacheKey(const std::string& romPath);

// Release the cached TheGamesDB resource maps (genre/developer/publisher id->name).
// They are fetched lazily on the first TheGamesDB game of a scrape; the scrape worker
// calls this when it finishes so nothing is held at idle. Idempotent.
void freeTgdbResources();

} // namespace nanoscraper
} // namespace android

#endif // GAMMAOS_NANO_SCRAPER_H
