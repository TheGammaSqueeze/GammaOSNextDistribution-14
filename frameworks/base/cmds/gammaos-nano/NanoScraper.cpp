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

#include "NanoScraper.h"
#include "NanoJson.h"
#include "NanoI18n.h"    // trDyn() runtime translation of hardcoded UI strings

#include <cctype>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <zlib.h>
#include <map>

#include <log/log.h>

#ifndef LOG_TAG
#define LOG_TAG "gammaos-scraper"
#endif

namespace android {
namespace nanoscraper {

// ---------------------------------------------------------------------------
// Small utilities
// ---------------------------------------------------------------------------

std::string cacheKey(const std::string& romPath) {
    // 64-bit FNV-1a over the full path (same scheme as the photo thumb cache).
    uint64_t h = 1469598103934665603ULL;
    for (unsigned char c : romPath) { h ^= c; h *= 1099511628211ULL; }
    char buf[17];
    snprintf(buf, sizeof(buf), "%016llx", (unsigned long long)h);
    return std::string(buf);
}

// Single-quote a string for safe inclusion in a /bin/sh command line. ROM file
// names are arbitrary user input, so every shelled-out token must go through this.
static std::string shellQuote(const std::string& s) {
    std::string out = "'";
    for (char c : s) {
        if (c == '\'') out += "'\\''";
        else           out += c;
    }
    out += "'";
    return out;
}

static bool fileExistsNonEmpty(const std::string& path, off_t* sizeOut = nullptr) {
    struct stat st;
    if (stat(path.c_str(), &st) != 0 || st.st_size <= 0) return false;
    if (sizeOut) *sizeOut = st.st_size;
    return true;
}

static std::string readFile(const std::string& path, size_t maxBytes) {
    std::string out;
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return out;
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size > 0) {
        size_t n = (size_t)st.st_size;
        if (n > maxBytes) n = maxBytes;
        out.resize(n);
        ssize_t rd = read(fd, &out[0], n);
        if (rd > 0) out.resize((size_t)rd); else out.clear();
    }
    close(fd);
    return out;
}

// Recognize the leading bytes of the common raster formats we can decode, so an
// HTML/text error page returned with HTTP 200 is rejected as "no media".
static bool looksLikeImage(const std::string& path) {
    unsigned char b[12] = {0};
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    ssize_t n = read(fd, b, sizeof(b));
    close(fd);
    if (n < 4) return false;
    if (b[0] == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return true;    // PNG
    if (b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF)              return true;     // JPEG
    if (b[0] == 'G'  && b[1] == 'I'  && b[2] == 'F')               return true;     // GIF
    if (b[0] == 'B'  && b[1] == 'M')                               return true;     // BMP
    if (n >= 12 && b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'
        && b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P')           return true;     // WEBP
    return false;
}

// curl GET into outPath. getParams are URL-encoded via --data-urlencode. Returns
// true if a non-empty file was written (HTTP-level success is checked by the
// caller, which inspects the body for API error markers / image magic).
static bool curlToFile(const char* curl, const std::string& url,
                       const std::vector<std::pair<std::string, std::string>>& getParams,
                       const std::string& outPath, int timeoutSec) {
    unlink(outPath.c_str());
    std::string cmd = shellQuote(curl);
    cmd += " -s -L --max-time " + std::to_string(timeoutSec);
    cmd += " -A 'gammaos-nano-scraper'";
    cmd += " -o " + shellQuote(outPath);
    if (!getParams.empty()) cmd += " -G";
    for (const auto& p : getParams)
        cmd += " --data-urlencode " + shellQuote(p.first + "=" + p.second);
    cmd += " " + shellQuote(url);
    cmd += " 2>/dev/null";

    FILE* f = popen(cmd.c_str(), "r");
    if (!f) return false;
    char drain[256];
    while (fread(drain, 1, sizeof(drain), f) > 0) { /* curl writes to -o; drain stdout */ }
    pclose(f);
    return fileExistsNonEmpty(outPath);
}

// CRC32 of a file (zlib), capped: very large images/ISOs are not worth hashing
// (and ScreenScraper matches CD games by name anyway). Returns "" when skipped.
static std::string crc32File(const std::string& path, off_t maxBytes) {
    off_t sz = 0;
    if (!fileExistsNonEmpty(path, &sz) || sz > maxBytes) return std::string();
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return std::string();
    uLong crc = crc32(0L, Z_NULL, 0);
    std::vector<unsigned char> buf(256 * 1024);
    ssize_t n;
    while ((n = read(fd, buf.data(), buf.size())) > 0)
        crc = crc32(crc, buf.data(), (uInt)n);
    close(fd);
    char hex[9];
    snprintf(hex, sizeof(hex), "%08lx", (unsigned long)crc);
    return std::string(hex);
}

static std::string baseName(const std::string& path) {
    size_t s = path.find_last_of('/');
    return (s == std::string::npos) ? path : path.substr(s + 1);
}

static std::string stripExt(const std::string& name) {
    size_t d = name.find_last_of('.');
    return (d == std::string::npos) ? name : name.substr(0, d);
}

// Clean a display name for a name-based query: drop bracketed/parenthesized
// region/version tags and collapse separators to spaces (es-de does similar).
static std::string cleanQueryName(const std::string& in) {
    std::string out;
    int depth = 0;
    for (char c : in) {
        if (c == '(' || c == '[') { depth++; continue; }
        if (c == ')' || c == ']') { if (depth > 0) depth--; continue; }
        if (depth > 0) continue;
        if (c == '_' || c == '.') c = ' ';
        out += c;
    }
    // collapse runs of spaces + trim
    std::string t;
    bool sp = false;
    for (char c : out) {
        if (c == ' ') { sp = true; continue; }
        if (sp && !t.empty()) t += ' ';
        sp = false;
        t += c;
    }
    return t;
}

// Decode the handful of HTML entities the APIs return in metadata text (es-de does
// the same). In-place, conservative; leaves unknown entities untouched.
static void htmlUnescape(std::string& s) {
    static const std::pair<const char*, const char*> kEnt[] = {
        {"&nbsp;", " "}, {"&amp;", "&"}, {"&#x26;", "&"}, {"&#38;", "&"},
        {"&quot;", "\""}, {"&#34;", "\""}, {"&apos;", "'"}, {"&#39;", "'"},
        {"&#039;", "'"}, {"&#x27;", "'"}, {"&copy;", "\xC2\xA9"}, {"&#169;", "\xC2\xA9"},
        {"&lt;", "<"}, {"&gt;", ">"}, {"&eacute;", "\xC3\xA9"}, {"&deg;", "\xC2\xB0"},
    };
    for (const auto& e : kEnt) {
        size_t p = 0;
        const std::string from = e.first, to = e.second;
        while ((p = s.find(from, p)) != std::string::npos) { s.replace(p, from.size(), to); p += to.size(); }
    }
    // trim leading/trailing whitespace
    size_t a = s.find_first_not_of(" \t\r\n"); size_t b = s.find_last_not_of(" \t\r\n");
    if (a == std::string::npos) s.clear(); else s = s.substr(a, b - a + 1);
}

Engine engineFromName(const std::string& name) {
    if (name == "thegamesdb")   return ENGINE_THEGAMESDB;
    if (name == "off" || name == "none") return ENGINE_OFF;
    return ENGINE_SCREENSCRAPER;
}

// ---------------------------------------------------------------------------
// Platform-id mapping (romDir / shortname -> ScreenScraper systemeid + TheGamesDB
// platform id). Keyed by lowercased folder/short name with common aliases.
// Covers the systems GammaOS ships plus the usual retro set; extend as needed.
// ---------------------------------------------------------------------------
struct PlatRow { const char* key; int ss; int tgdb; };
static const PlatRow kPlatforms[] = {
    // Nintendo
    {"nes",3,7}, {"famicom",3,7}, {"fds",106,4936}, {"famicomdisksystem",106,4936},
    {"snes",4,6}, {"sfc",4,6}, {"superfamicom",4,6}, {"satellaview",107,6},
    {"n64",14,3}, {"nintendo64",14,3},
    {"gb",9,4}, {"gameboy",9,4},
    {"gbc",10,41}, {"gameboycolor",10,41},
    {"gba",12,5}, {"gameboyadvance",12,5},
    {"nds",15,8}, {"ds",15,8}, {"nintendods",15,8},
    {"3ds",17,4912}, {"n3ds",17,4912},
    {"gamecube",13,2}, {"gc",13,2}, {"ngc",13,2},
    {"wii",16,9},
    {"wiiu",18,38},
    {"virtualboy",11,4918}, {"vb",11,4918},
    {"pokemini",211,4957},
    // Sega
    {"megadrive",1,36}, {"genesis",1,18}, {"md",1,36},
    {"mastersystem",2,35}, {"sms",2,35},
    {"gamegear",21,20}, {"gg",21,20},
    {"segacd",20,21}, {"megacd",20,21},
    {"sega32x",19,33}, {"32x",19,33},
    {"saturn",22,17}, {"segasaturn",22,17},
    {"dreamcast",23,16}, {"dc",23,16},
    {"sg1000",109,4949},
    {"naomi",56,23},
    // Sony
    {"psx",57,10}, {"ps1",57,10}, {"playstation",57,10}, {"psone",57,10},
    {"ps2",58,11}, {"playstation2",58,11},
    {"psp",61,13}, {"playstationportable",61,13},
    {"psvita",62,39}, {"vita",62,39}, {"ps3",59,12}, {"playstation3",59,12},
    // NEC
    {"pcengine",31,34}, {"tg16",31,34}, {"turbografx16",31,34}, {"pce",31,34},
    {"pcenginecd",114,4955}, {"tg16cd",114,4955}, {"turbografxcd",114,4955},
    {"supergrafx",105,34},
    {"pcfx",72,4930},
    // SNK
    {"neogeo",142,24}, {"neogeoaes",142,24}, {"neogeomvs",142,24},
    {"neogeocd",70,4956},
    {"ngp",25,4922}, {"neogeopocket",25,4922},
    {"ngpc",82,4923}, {"neogeopocketcolor",82,4923},
    // Atari
    {"atari2600",26,22}, {"a2600",26,22},
    {"atari5200",40,26}, {"a5200",40,26},
    {"atari7800",41,27}, {"a7800",41,27},
    {"atarilynx",28,4924}, {"lynx",28,4924},
    {"atarijaguar",27,28}, {"jaguar",27,28},
    {"atarijaguarcd",171,29},
    {"atarist",42,4937}, {"atari800",43,4943}, {"atari8bit",43,4943},
    // Bandai
    {"wonderswan",45,4925}, {"ws",45,4925},
    {"wonderswancolor",46,4926}, {"wsc",46,4926},
    // Computers / micros
    {"c64",66,40}, {"commodore64",66,40},
    {"amiga",64,4911},
    {"amigacd32",130,4947},
    {"msx",113,4929}, {"msx2",116,4929},
    {"zxspectrum",76,4913}, {"spectrum",76,4913},
    {"amstradcpc",65,4914}, {"cpc",65,4914},
    {"x68000",79,4931},
    {"dos",135,1}, {"pc",135,1},
    {"scummvm",123,1},
    // Other consoles
    {"3do",29,25},
    {"colecovision",48,31}, {"coleco",48,31},
    {"intellivision",115,32},
    {"vectrex",102,4939},
    {"odyssey2",104,4927}, {"o2em",104,4927},
    {"channelf",80,-1},   // es-de has no TheGamesDB platform id for Channel F
    {"pico8",234,-1}, {"openbor",214,-1},   // game engines: ScreenScraper only
    // Arcade
    {"arcade",75,23}, {"mame",75,23}, {"fbneo",75,23}, {"fba",75,23}, {"cps1",75,23},
    {"cps2",75,23}, {"cps3",75,23},
};

PlatformIds platformForSystem(const std::string& romDir,
                              const std::string& shortname,
                              const std::string& name,
                              const std::string& manualSsId) {
    PlatformIds out;
    if (!manualSsId.empty()) {
        int v = atoi(manualSsId.c_str());
        if (v > 0) out.ss = v;
    }
    auto strip = [](const std::string& s) {
        std::string o;
        for (char c : s) { unsigned char u = (unsigned char)c; if (isalnum(u)) o += (char)tolower(u); }
        return o;
    };
    const std::string keys[3] = { strip(romDir), strip(shortname), strip(name) };
    for (const std::string& k : keys) {
        if (k.empty()) continue;
        for (const auto& r : kPlatforms) {
            if (k == r.key) {
                if (out.ss < 0)   out.ss = r.ss;
                if (out.tgdb < 0) out.tgdb = r.tgdb;
                if (out.ss > 0 && out.tgdb > 0) return out;
            }
        }
    }
    return out;
}

// ---------------------------------------------------------------------------
// Region preference + media selection
// ---------------------------------------------------------------------------
// Region preference, mirroring es-de's ScreenScraper fallback chain
// ({region, wor, us, ss, eu, jp, cus}). The requested region is tried first, then
// the universal fallbacks; "ss" picks up media for unofficial titles.
static std::vector<std::string> regionOrder(const std::string& region) {
    std::vector<std::string> base = {"wor", "us", "ss", "eu", "jp", "cus"};
    std::string r = region.empty() ? "us" : region;
    std::vector<std::string> out;
    out.push_back(r);
    for (const std::string& b : base) if (b != r) out.push_back(b);
    return out;
}

// es-de hashes only files up to a size cap (default 32 MB, extended 800 MB) and
// matches larger files (CD/DVD images) by name. 128 MB covers every cartridge
// system while skipping disc images, keeping the worker responsive on eMMC.
static const off_t kHashMaxBytes = 128LL * 1024 * 1024;

// ---------------------------------------------------------------------------
// ScreenScraper.fr
// ---------------------------------------------------------------------------
static ScrapeOutcome scrapeScreenScraper(const Credentials& cred,
                                         const std::string& romPath,
                                         const std::string& displayName,
                                         const PlatformIds& plat,
                                         bool wantBox, bool wantFan,
                                         const std::string& cacheDir,
                                         const std::string& tmpTag,
                                         const std::string& queryName,
                                         const char* curl) {
    ScrapeOutcome r;
    if (cred.ssDevId.empty() || cred.ssDevPw.empty()) {
        r.credMissing = true;
        r.error = "ScreenScraper developer credentials not set";
        return r;
    }
    const std::string key   = cacheKey(romPath);
    const std::string resp  = cacheDir + "/.resp-" + tmpTag + ".json";
    // A user title override drives romnom (crc + size still sent); otherwise the raw
    // filename as before.
    const std::string romnom = queryName.empty() ? baseName(romPath) : queryName;

    std::vector<std::pair<std::string, std::string>> q = {
        {"devid", cred.ssDevId}, {"devpassword", cred.ssDevPw},
        {"softname", cred.ssSoftname.empty() ? std::string("gammaos-nano") : cred.ssSoftname},
        {"output", "json"},
        {"romnom", romnom},
    };
    if (!cred.ssUser.empty()) { q.push_back({"ssid", cred.ssUser}); q.push_back({"sspassword", cred.ssPass}); }
    if (plat.ss > 0) q.push_back({"systemeid", std::to_string(plat.ss)});
    std::string crc = crc32File(romPath, kHashMaxBytes);
    if (!crc.empty()) q.push_back({"crc", crc});
    off_t fsz = 0; if (fileExistsNonEmpty(romPath, &fsz)) q.push_back({"romtaille", std::to_string((long long)fsz)});

    if (!curlToFile(curl, "https://api.screenscraper.fr/api2/jeuInfos.php", q, resp, 30)) {
        r.networkFail = true; r.error = trDyn("ScreenScraper request failed"); return r;
    }
    std::string body = readFile(resp, 2 * 1024 * 1024);
    unlink(resp.c_str());
    // ScreenScraper returns a plain-text error (not JSON) on auth / quota / no-game.
    if (body.empty() || (body[0] != '{' && body[0] != '[')) {
        if (body.find("identifiants") != std::string::npos || body.find("login") != std::string::npos
            || body.find("Erreur") != std::string::npos) {
            r.networkFail = true;
            r.error = std::string(trDyn("ScreenScraper: ")) + (body.size() > 120 ? body.substr(0, 120) : body);
        } else {
            r.error = "No match";
        }
        return r;
    }
    njson::Value root;
    if (!njson::parse(body, &root)) { r.error = "ScreenScraper: bad JSON"; return r; }
    const njson::Value* jeu = nullptr;
    if (const njson::Value* rp = root.find("response")) jeu = rp->find("jeu");
    if (!jeu || !jeu->isObject()) { r.error = "No match"; return r; }

    std::vector<std::string> regs = regionOrder(cred.region);

    // Title: noms[] {region,text} preferred-region first, else first; else "nom".
    if (const njson::Value* noms = jeu->find("noms")) {
        if (noms->isArray() && !noms->arr.empty()) {
            std::string first, picked;
            for (const auto& nm : noms->arr) {
                std::string rg = nm.getString("region");
                std::string tx = nm.getString("text");
                if (first.empty()) first = tx;
                for (const std::string& want : regs)
                    if (rg == want && picked.empty()) picked = tx;
            }
            r.title = picked.empty() ? first : picked;
        }
    }
    if (r.title.empty()) r.title = jeu->getString("nom");
    if (r.title.empty()) r.title = stripExt(displayName);

    // Game metadata for the Information screen (best effort). language-/region-preferred.
    auto langText = [](const njson::Value* arr) -> std::string {
        if (!arr || !arr->isArray() || arr->arr.empty()) return std::string();
        std::string first, picked;
        for (const auto& o : arr->arr) {
            std::string lg = o.getString("langue"), tx = o.getString("text");
            if (first.empty()) first = tx;
            if (lg == "en" && picked.empty()) picked = tx;
        }
        return picked.empty() ? first : picked;
    };
    r.synopsis = langText(jeu->find("synopsis"));
    if (const njson::Value* gs = jeu->find("genres")) {
        if (gs->isArray() && !gs->arr.empty()) {
            const njson::Value* gp = &gs->arr[0];
            for (const auto& g : gs->arr) if (g.getString("principale") == "1") { gp = &g; break; }
            r.genre = langText(gp->find("noms"));
        }
    }
    if (const njson::Value* jo = jeu->find("joueurs")) r.players = jo->getString("text");
    if (const njson::Value* nt = jeu->find("note")) { std::string v = nt->getString("text"); if (!v.empty()) r.rating = v + "/20"; }
    if (const njson::Value* ds = jeu->find("dates")) {
        if (ds->isArray() && !ds->arr.empty()) {
            std::string first, picked;
            for (const auto& d : ds->arr) {
                std::string rg = d.getString("region"), tx = d.getString("text");
                if (first.empty()) first = tx;
                for (const std::string& want : regs) if (rg == want && picked.empty()) picked = tx;
            }
            r.releaseDate = picked.empty() ? first : picked;
        }
    }
    if (const njson::Value* dv = jeu->find("developpeur")) r.developer = dv->getString("text");
    if (const njson::Value* ed = jeu->find("editeur")) r.publisher = ed->getString("text");
    htmlUnescape(r.synopsis); htmlUnescape(r.genre); htmlUnescape(r.players);
    htmlUnescape(r.developer); htmlUnescape(r.publisher); htmlUnescape(r.releaseDate);

    const njson::Value* medias = jeu->find("medias");
    if (!medias || !medias->isArray()) { r.error = "No media"; return r; }

    // Pick the best media url among a list of acceptable types, region-preferred.
    auto pick = [&](const std::vector<std::string>& types) -> std::string {
        // First pass: exact type + region preference. Second pass: type, any region.
        for (const std::string& want : regs) {
            for (const std::string& ty : types)
                for (const auto& m : medias->arr) {
                    if (m.getString("type") != ty) continue;
                    if (m.getString("region") != want) continue;
                    std::string u = m.getString("url");
                    if (!u.empty()) return u;
                }
        }
        for (const std::string& ty : types)
            for (const auto& m : medias->arr) {
                if (m.getString("type") != ty) continue;
                std::string u = m.getString("url");
                if (!u.empty()) return u;
            }
        return std::string();
    };

    auto download = [&](const std::string& url, const std::string& destFinal) -> bool {
        if (url.empty()) return false;
        std::string tmp = destFinal + ".tmp-" + tmpTag;
        std::vector<std::pair<std::string, std::string>> none;
        if (!curlToFile(curl, url, none, tmp, 60)) return false;
        if (!looksLikeImage(tmp)) { unlink(tmp.c_str()); return false; }
        if (rename(tmp.c_str(), destFinal.c_str()) != 0) { unlink(tmp.c_str()); return false; }
        return true;
    };

    if (wantBox) {
        // Cover = box-2D (es-de media_cover); 3D box is the fallback.
        std::string u = pick({"box-2D", "box-3D"});
        if (download(u, cacheDir + "/" + key + ".box.png")) r.boxFile = cacheDir + "/" + key + ".box.png";
    }
    if (wantFan) {
        // Background = fanart (es-de media_fanart); fall back to an in-game
        // screenshot then the title screen when no dedicated fanart exists.
        std::string u = pick({"fanart", "ss", "sstitle"});
        if (download(u, cacheDir + "/" + key + ".fan.jpg")) r.fanFile = cacheDir + "/" + key + ".fan.jpg";
    }
    r.ok = !r.boxFile.empty() || !r.fanFile.empty();
    if (!r.ok && r.error.empty()) r.error = "No usable media";
    return r;
}

// ---------------------------------------------------------------------------
// TheGamesDB resource maps (genre / developer / publisher id -> name). The game
// records reference these by numeric id; the names live in three separate static
// lists. We fetch them ONCE per scrape (the first TheGamesDB game), cache them in
// a file-static, and free them when the worker finishes (freeTgdbResources, called
// from the scrape worker). The scrape worker is single-threaded and only one scrape
// runs at a time, so no locking is needed. Nothing is held at idle.
// ---------------------------------------------------------------------------
namespace {
struct TgdbResMaps {
    std::map<int, std::string> genre, developer, publisher;
    bool loaded = false;
};
TgdbResMaps gTgdbRes;

// Fetch one resource endpoint (Genres/Developers/Publishers) and fold its
// {idstr:{id,name}} object into id->name.
void loadTgdbResMap(const char* curl, const std::string& key, const char* endpoint,
                    const char* member, const std::string& resp,
                    std::map<int, std::string>& out) {
    std::vector<std::pair<std::string, std::string>> q = {{"apikey", key}};
    std::string url = std::string("https://api.thegamesdb.net/v1/") + endpoint;
    if (!curlToFile(curl, url, q, resp, 30)) return;
    std::string body = readFile(resp, 4 * 1024 * 1024);
    unlink(resp.c_str());
    njson::Value root;
    if (body.empty() || !njson::parse(body, &root)) return;
    const njson::Value* data = root.find("data");
    const njson::Value* m = data ? data->find(member) : nullptr;
    if (!m || !m->isObject()) return;
    for (const auto& kv : m->obj) {
        int id = kv.second.getInt("id", -1);
        std::string nm = kv.second.getString("name");
        if (id >= 0 && !nm.empty()) out[id] = nm;
    }
}

void ensureTgdbResMaps(const char* curl, const std::string& key, const std::string& resp) {
    if (gTgdbRes.loaded) return;
    gTgdbRes.loaded = true;   // set first: a failed fetch is not retried per ROM
    loadTgdbResMap(curl, key, "Genres",     "genres",     resp, gTgdbRes.genre);
    loadTgdbResMap(curl, key, "Developers", "developers", resp, gTgdbRes.developer);
    loadTgdbResMap(curl, key, "Publishers", "publishers", resp, gTgdbRes.publisher);
}

// Map the first known id in a game's int-array field to its resource name.
std::string firstResName(const njson::Value* game, const char* field,
                         const std::map<int, std::string>& m) {
    const njson::Value* a = game ? game->find(field) : nullptr;
    if (!a || !a->isArray()) return "";
    for (const auto& e : a->arr) {
        if (!e.isNumber()) continue;
        auto it = m.find(e.asInt());
        if (it != m.end()) return it->second;
    }
    return "";
}
}  // namespace

// Free the cached TheGamesDB resource maps (called by the scrape worker on finish so
// nothing lingers at idle). Idempotent.
void freeTgdbResources() {
    gTgdbRes.genre.clear();
    gTgdbRes.developer.clear();
    gTgdbRes.publisher.clear();
    gTgdbRes.loaded = false;
}

// ---------------------------------------------------------------------------
// TheGamesDB
// ---------------------------------------------------------------------------
static ScrapeOutcome scrapeTheGamesDb(const Credentials& cred,
                                      const std::string& romPath,
                                      const std::string& displayName,
                                      const PlatformIds& plat,
                                      bool wantBox, bool wantFan,
                                      const std::string& cacheDir,
                                      const std::string& tmpTag,
                                      const std::string& queryName,
                                      const char* curl) {
    ScrapeOutcome r;
    if (cred.tgdbKey.empty()) {
        r.credMissing = true;
        r.error = "TheGamesDB API key not set";
        return r;
    }
    const std::string key   = cacheKey(romPath);
    const std::string resp  = cacheDir + "/.resp-" + tmpTag + ".json";
    // A user title override drives the name query (still cleaned of region/version
    // tags); otherwise the filename stem as before.
    const std::string qname = queryName.empty()
        ? cleanQueryName(stripExt(baseName(romPath)))
        : cleanQueryName(queryName);

    // 1) ByGameName -> game id. fields includes "platform" so we can prefer a
    // result on the requested platform; filter[platform] also narrows server-side.
    std::vector<std::pair<std::string, std::string>> q = {
        {"apikey", cred.tgdbKey},
        {"fields", "players,publishers,developers,genres,overview,platform,rating,release_date"},
        {"name", qname},
    };
    if (plat.tgdb > 0) q.push_back({"filter[platform]", std::to_string(plat.tgdb)});
    if (!curlToFile(curl, "https://api.thegamesdb.net/v1/Games/ByGameName", q, resp, 30)) {
        r.networkFail = true; r.error = trDyn("TheGamesDB request failed"); return r;
    }
    std::string body = readFile(resp, 2 * 1024 * 1024);
    unlink(resp.c_str());
    njson::Value root;
    if (body.empty() || !njson::parse(body, &root)) { r.networkFail = true; r.error = trDyn("TheGamesDB: bad response"); return r; }
    int code = root.getInt("code", 0);
    if (code == 403 || code == 401) { r.networkFail = true; r.error = trDyn("TheGamesDB: invalid API key"); return r; }

    const njson::Value* data = root.find("data");
    const njson::Value* games = data ? data->find("games") : nullptr;
    if (!games || !games->isArray() || games->arr.empty()) { r.error = "No match"; return r; }

    int gameId = -1;
    // Prefer a game on the requested platform; otherwise the first result.
    const njson::Value* gsel = nullptr;
    if (plat.tgdb > 0)
        for (const auto& g : games->arr)
            if (g.getInt("platform", -1) == plat.tgdb) { gsel = &g; break; }
    if (!gsel) gsel = &games->arr[0];
    gameId = gsel->getInt("id", -1);
    r.title = gsel->getString("game_title");
    if (gameId < 0) { r.error = "No match"; return r; }
    if (r.title.empty()) r.title = stripExt(displayName);
    // Metadata. overview/players/rating/release_date are directly readable; genre,
    // developer and publisher are numeric ids resolved through the resource maps
    // (fetched once per scrape, freed on finish).
    r.synopsis = gsel->getString("overview");
    { int pl = gsel->getInt("players", 0); if (pl > 0) r.players = std::to_string(pl); }
    r.rating = gsel->getString("rating");
    r.releaseDate = gsel->getString("release_date");
    if (r.releaseDate.size() > 10) r.releaseDate = r.releaseDate.substr(0, 10);
    ensureTgdbResMaps(curl, cred.tgdbKey, resp);
    r.genre     = firstResName(gsel, "genres",     gTgdbRes.genre);
    r.developer = firstResName(gsel, "developers", gTgdbRes.developer);
    r.publisher = firstResName(gsel, "publishers", gTgdbRes.publisher);
    htmlUnescape(r.synopsis); htmlUnescape(r.rating);
    htmlUnescape(r.genre); htmlUnescape(r.developer); htmlUnescape(r.publisher);

    // 2) Images -> boxart + fanart filenames. es-de fetches all image types and
    // filters client-side (no filter[type]) so screenshot/titlescreen remain as
    // background fallbacks.
    std::vector<std::pair<std::string, std::string>> q2 = {
        {"apikey", cred.tgdbKey}, {"games_id", std::to_string(gameId)},
    };
    if (!curlToFile(curl, "https://api.thegamesdb.net/v1/Games/Images", q2, resp, 30)) {
        r.networkFail = true; r.error = trDyn("TheGamesDB images request failed"); return r;
    }
    std::string body2 = readFile(resp, 2 * 1024 * 1024);
    unlink(resp.c_str());
    njson::Value root2;
    if (body2.empty() || !njson::parse(body2, &root2)) { r.networkFail = true; r.error = trDyn("TheGamesDB: bad images response"); return r; }
    const njson::Value* d2 = root2.find("data");
    if (!d2) { r.error = "No media"; return r; }
    std::string base;
    if (const njson::Value* bu = d2->find("base_url")) {
        base = bu->getString("original");
        if (base.empty()) base = bu->getString("large");
        if (base.empty()) base = bu->getString("medium");
    }
    const njson::Value* images = d2->find("images");
    const njson::Value* arr = nullptr;
    if (images && images->isObject()) {
        // Keyed by the game id as a string; there is only the one we asked for.
        const njson::Value* byId = images->find(std::to_string(gameId));
        if (byId && byId->isArray()) arr = byId;
        else if (!images->obj.empty() && images->obj[0].second.isArray()) arr = &images->obj[0].second;
    }
    if (base.empty() || !arr) { r.error = "No media"; return r; }

    auto findImg = [&](const char* type, const char* sidePref) -> std::string {
        std::string anyType;
        for (const auto& im : arr->arr) {
            if (im.getString("type") != type) continue;
            std::string fn = im.getString("filename");
            if (fn.empty()) continue;
            if (sidePref && im.getString("side") == sidePref) return base + fn;
            if (anyType.empty()) anyType = base + fn;
        }
        return anyType;
    };

    auto download = [&](const std::string& url, const std::string& destFinal) -> bool {
        if (url.empty()) return false;
        std::string tmp = destFinal + ".tmp-" + tmpTag;
        std::vector<std::pair<std::string, std::string>> none;
        if (!curlToFile(curl, url, none, tmp, 60)) return false;
        if (!looksLikeImage(tmp)) { unlink(tmp.c_str()); return false; }
        if (rename(tmp.c_str(), destFinal.c_str()) != 0) { unlink(tmp.c_str()); return false; }
        return true;
    };

    if (wantBox) {
        std::string u = findImg("boxart", "front");
        if (download(u, cacheDir + "/" + key + ".box.png")) r.boxFile = cacheDir + "/" + key + ".box.png";
    }
    if (wantFan) {
        std::string u = findImg("fanart", nullptr);
        if (download(u, cacheDir + "/" + key + ".fan.jpg")) r.fanFile = cacheDir + "/" + key + ".fan.jpg";
    }
    r.ok = !r.boxFile.empty() || !r.fanFile.empty();
    if (!r.ok && r.error.empty()) r.error = "No usable media";
    return r;
}

// ---------------------------------------------------------------------------
ScrapeOutcome scrapeRom(Engine engine, const Credentials& cred,
                        const std::string& romPath, const std::string& displayName,
                        const PlatformIds& plat, bool wantBox, bool wantFan,
                        const std::string& cacheDir, const std::string& tmpTag,
                        const std::string& queryName,
                        const char* curlPath) {
    if (engine == ENGINE_OFF || (!wantBox && !wantFan)) {
        ScrapeOutcome r; r.error = "Scraping disabled"; return r;
    }
    if (engine == ENGINE_THEGAMESDB)
        return scrapeTheGamesDb(cred, romPath, displayName, plat, wantBox, wantFan, cacheDir, tmpTag, queryName, curlPath);
    return scrapeScreenScraper(cred, romPath, displayName, plat, wantBox, wantFan, cacheDir, tmpTag, queryName, curlPath);
}

} // namespace nanoscraper
} // namespace android
