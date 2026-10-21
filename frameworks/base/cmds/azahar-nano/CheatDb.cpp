#define LOG_TAG "azahar-nano"

#include "CheatDb.h"

#include <algorithm>
#include <cctype>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>

#include <sys/stat.h>
#include <unistd.h>

#include <cutils/properties.h>
#include <log/log.h>
#include <ziparchive/zip_archive.h>

namespace azahar_nano {
namespace cheatdb {

namespace {

constexpr const char* kDatabaseDefault = "/system/etc/azahar-nano/cheats3ds.zip";
constexpr const char* kEnabledMark = "*citra_enabled";

std::string trim(const std::string& s) {
    size_t a = 0, b = s.size();
    while (a < b && isspace((unsigned char)s[a])) a++;
    while (b > a && isspace((unsigned char)s[b - 1])) b--;
    return s.substr(a, b - a);
}

std::string lower(std::string s) {
    for (char& c : s) c = (char)tolower((unsigned char)c);
    return s;
}

std::string tidName(uint64_t titleId) {
    char buf[24];
    snprintf(buf, sizeof(buf), "%016llX", (unsigned long long)titleId);
    return buf;
}

bool readFile(const std::string& path, std::string* out) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;
    std::stringstream ss;
    ss << f.rdbuf();
    *out = ss.str();
    return true;
}

bool writeFile(const std::string& path, const std::string& text) {
    const std::string tmp = path + ".tmp";
    {
        std::ofstream f(tmp, std::ios::binary | std::ios::trunc);
        if (!f) return false;
        f << text;
        if (!f) return false;
    }
    if (rename(tmp.c_str(), path.c_str()) != 0) {
        unlink(tmp.c_str());
        return false;
    }
    return true;
}

void ensureDirs(const std::string& dataDir) {
    mkdir((dataDir + "/cheats").c_str(), 0775);
    const std::string custom = customDir(dataDir);
    if (mkdir(custom.c_str(), 0775) == 0 || access((custom + "/README.txt").c_str(), F_OK) != 0) {
        writeFile(custom + "/README.txt",
                  "Your own 3DS cheats, one file per game named by its title id, for example\n"
                  "0004000000055D00.txt (the id is in the in-game menu under Cheats > Cheat files).\n"
                  "\n"
                  "The format is the Gateway / CTRPF text format:\n"
                  "\n"
                  "[Cheat name]\n"
                  "*an optional comment line\n"
                  "D3000000 14000000\n"
                  "128B6044 00005050\n"
                  "D2000000 00000000\n"
                  "\n"
                  "The cheats here are merged with the built-in database when the game starts and\n"
                  "show up first in the menu. Cheats added from the menu are written here too.\n");
    }
}

// Code text (hex words separated by spaces or newlines) into "XXXXXXXX YYYYYYYY" lines.
bool parseCode(const std::string& text, std::vector<std::string>* lines, std::string* err) {
    std::vector<std::string> words;
    std::string cur;
    for (char c : text + " ") {
        if (isspace((unsigned char)c)) {
            if (!cur.empty()) words.push_back(cur);
            cur.clear();
        } else {
            cur += (char)toupper((unsigned char)c);
        }
    }
    if (words.empty()) {
        if (err) *err = "No code entered";
        return false;
    }
    // A single 16 digit word is one line written without the space.
    std::vector<std::string> flat;
    for (const std::string& w : words) {
        if (w.size() == 16) {
            flat.push_back(w.substr(0, 8));
            flat.push_back(w.substr(8));
        } else {
            flat.push_back(w);
        }
    }
    if (flat.size() % 2) {
        if (err) *err = "A code is pairs of 8 hex digits";
        return false;
    }
    for (const std::string& w : flat) {
        if (w.size() != 8 || !std::all_of(w.begin(), w.end(), [](char c) { return isxdigit((unsigned char)c); })) {
            if (err) *err = "Bad code word: " + w;
            return false;
        }
    }
    lines->clear();
    for (size_t i = 0; i + 1 < flat.size(); i += 2) lines->push_back(flat[i] + " " + flat[i + 1]);
    return true;
}

bool sameCode(const Cheat& a, const Cheat& b) {
    return a.code == b.code;
}

} // namespace

std::vector<Cheat> parseText(const std::string& text) {
    std::vector<Cheat> out;
    Cheat cur;
    bool have = false;
    auto flush = [&] {
        if (have && !cur.code.empty()) out.push_back(cur);
        cur = Cheat();
        have = false;
    };
    std::istringstream in(text);
    std::string raw;
    while (std::getline(in, raw)) {
        raw.erase(std::remove(raw.begin(), raw.end(), '\0'), raw.end());
        const std::string line = trim(raw);
        if (line.empty()) continue;
        if (line.size() >= 2 && line.front() == '[' && line.back() == ']') {
            flush();
            cur.name = trim(line.substr(1, line.size() - 2));
            have = true;
        } else if (!have) {
            continue;
        } else if (line == kEnabledMark) {
            cur.enabled = true;
        } else if (line.front() == '*') {
            const std::string c = trim(line.substr(1));
            if (!cur.comments.empty()) cur.comments += '\n';
            cur.comments += c;
        } else if (line.size() >= 2 && line.front() == '{' && line.back() == '}') {
            const std::string c = trim(line.substr(1, line.size() - 2));
            if (!c.empty()) {
                if (!cur.comments.empty()) cur.comments += '\n';
                cur.comments += c;
            }
        } else {
            cur.code.push_back(line);
        }
    }
    flush();
    return out;
}

std::string renderText(const std::vector<Cheat>& cheats) {
    std::string out;
    for (const Cheat& c : cheats) {
        out += '[' + c.name + "]\n";
        if (c.enabled) {
            out += kEnabledMark;
            out += '\n';
        }
        std::istringstream cs(c.comments);
        std::string line;
        while (std::getline(cs, line)) {
            if (!line.empty()) out += '*' + line + '\n';
        }
        for (const std::string& l : c.code) out += l + '\n';
        out += '\n';
    }
    return out;
}

std::string databasePath() {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.azahar_nano.cheat_db", v, kDatabaseDefault);
    return v;
}

std::string cheatFile(const std::string& dataDir, uint64_t titleId) {
    return dataDir + "/cheats/" + tidName(titleId) + ".txt";
}

std::string customDir(const std::string& dataDir) {
    return dataDir + "/cheats/custom";
}

std::string customFile(const std::string& dataDir, uint64_t titleId) {
    return customDir(dataDir) + "/" + tidName(titleId) + ".txt";
}

std::vector<Cheat> databaseCheats(uint64_t titleId) {
    std::vector<Cheat> out;
    const std::string path = databasePath();
    ZipArchiveHandle zip = nullptr;
    if (OpenArchive(path.c_str(), &zip) != 0) {
        ALOGW("cheats: database %s not readable", path.c_str());
        return out;
    }
    ZipEntry64 entry;
    const std::string name = tidName(titleId) + ".txt";
    if (FindEntry(zip, name, &entry) == 0 && entry.uncompressed_length < (16u << 20)) {
        std::string text(entry.uncompressed_length, '\0');
        if (ExtractToMemory(zip, &entry, (uint8_t*)text.data(), (size_t)text.size()) == 0) {
            out = parseText(text);
        }
    }
    CloseArchive(zip);
    return out;
}

int prepare(const std::string& dataDir, uint64_t titleId) {
    if (titleId == 0 || dataDir.empty()) return 0;
    ensureDirs(dataDir);
    std::string text;
    std::vector<Cheat> previous;
    if (readFile(cheatFile(dataDir, titleId), &text)) previous = parseText(text);
    std::vector<Cheat> custom;
    if (readFile(customFile(dataDir, titleId), &text)) custom = parseText(text);
    std::vector<Cheat> db = databaseCheats(titleId);

    // Cheats of the previous file that belong to neither list were the user's (hand written
    // into the core's file, or from before the database): keep them as custom cheats.
    bool customChanged = false;
    for (const Cheat& p : previous) {
        auto inList = [&](const std::vector<Cheat>& l) {
            return std::any_of(l.begin(), l.end(), [&](const Cheat& c) {
                return lower(c.name) == lower(p.name) && sameCode(c, p);
            });
        };
        if (!inList(custom) && !inList(db)) {
            Cheat c = p;
            c.enabled = false;
            custom.push_back(c);
            customChanged = true;
        }
    }
    if (customChanged) writeFile(customFile(dataDir, titleId), renderText(custom));

    std::vector<Cheat> merged;
    merged.reserve(custom.size() + db.size());
    for (Cheat c : custom) { c.enabled = false; merged.push_back(c); }
    for (const Cheat& c : db) {
        // the database copy of a cheat the user also has is not listed twice
        const bool dup = std::any_of(custom.begin(), custom.end(), [&](const Cheat& u) {
            return lower(u.name) == lower(c.name) && sameCode(u, c);
        });
        if (!dup) merged.push_back(c);
    }
    // enabled marks carry over by name (then by code when the name was duplicated)
    std::vector<bool> used(previous.size(), false);
    for (Cheat& m : merged) {
        m.enabled = false;
        for (size_t i = 0; i < previous.size(); i++) {
            if (used[i] || !previous[i].enabled) continue;
            if (lower(previous[i].name) == lower(m.name) && sameCode(previous[i], m)) {
                m.enabled = true;
                used[i] = true;
                break;
            }
        }
    }
    for (Cheat& m : merged) {
        if (m.enabled) continue;
        for (size_t i = 0; i < previous.size(); i++) {
            if (used[i] || !previous[i].enabled) continue;
            if (lower(previous[i].name) == lower(m.name)) {
                m.enabled = true;
                used[i] = true;
                break;
            }
        }
    }
    const std::string out = renderText(merged);
    if (merged.empty()) {
        // nothing for this title: leave no empty file behind (the core logs a missing file)
        unlink(cheatFile(dataDir, titleId).c_str());
    } else if (!writeFile(cheatFile(dataDir, titleId), out)) {
        ALOGE("cheats: could not write %s", cheatFile(dataDir, titleId).c_str());
    }
    ALOGI("cheats: %s: %zu custom + %zu database -> %zu listed", tidName(titleId).c_str(), custom.size(),
          db.size(), merged.size());
    return (int)custom.size();
}

bool addCustom(const std::string& dataDir, uint64_t titleId, const std::string& name,
               const std::string& code, std::string* err) {
    if (titleId == 0) {
        if (err) *err = "No title id for this game";
        return false;
    }
    Cheat c;
    c.name = trim(name);
    if (c.name.empty()) {
        if (err) *err = "The cheat needs a name";
        return false;
    }
    for (char ch : c.name) {
        if (ch == '[' || ch == ']' || ch == '\n') {
            if (err) *err = "The name cannot hold [ ] or line breaks";
            return false;
        }
    }
    if (!parseCode(code, &c.code, err)) return false;
    ensureDirs(dataDir);
    std::string text;
    std::vector<Cheat> custom;
    if (readFile(customFile(dataDir, titleId), &text)) custom = parseText(text);
    custom.push_back(c);
    if (!writeFile(customFile(dataDir, titleId), renderText(custom))) {
        if (err) *err = "Could not write " + customFile(dataDir, titleId);
        return false;
    }
    prepare(dataDir, titleId);
    return true;
}

bool removeCustom(const std::string& dataDir, uint64_t titleId, int index) {
    if (titleId == 0) return false;
    std::string text;
    std::vector<Cheat> custom;
    if (readFile(customFile(dataDir, titleId), &text)) custom = parseText(text);
    if (index < 0 || index >= (int)custom.size()) return false;
    const Cheat gone = custom[index];
    custom.erase(custom.begin() + index);
    if (custom.empty()) {
        unlink(customFile(dataDir, titleId).c_str());
    } else if (!writeFile(customFile(dataDir, titleId), renderText(custom))) {
        return false;
    }
    // its enabled mark must not carry over to a database cheat of the same name
    std::vector<Cheat> previous;
    if (readFile(cheatFile(dataDir, titleId), &text)) {
        previous = parseText(text);
        bool changed = false;
        for (Cheat& p : previous) {
            if (lower(p.name) == lower(gone.name) && sameCode(p, gone) && p.enabled) {
                p.enabled = false;
                changed = true;
            }
        }
        if (changed) writeFile(cheatFile(dataDir, titleId), renderText(previous));
    }
    prepare(dataDir, titleId);
    return true;
}

} // namespace cheatdb
} // namespace azahar_nano
