#define LOG_TAG "azahar-nano"

#include "AzaharSettings.h"

#include <cctype>
#include <cerrno>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <mutex>
#include <strings.h>
#include <sys/stat.h>
#include <thread>
#include <unistd.h>

#include <cutils/properties.h>
#include <log/log.h>

namespace azahar_nano {
namespace settings {

namespace {

std::mutex gMu;                                   // guards everything below
std::mutex gFileMu;                               // serialises writeFile (writer thread vs flush)
std::string gPath;                                // this session's override file
bool gActive = false;                             // the file is the source of truth
bool gUnreadable = false;                         // the file exists but could not be read
std::string gError;                               // why (for the menu)
std::map<std::string, std::string> gValues;       // short key or Section/key -> value
bool gDirty = false;                              // a save is pending
bool gWriterStarted = false;
std::condition_variable gCv;

constexpr size_t kPrefixLen = 28;   // strlen("persist.gammaos.azahar_nano.")
static_assert(sizeof("persist.gammaos.azahar_nano.") - 1 == kPrefixLen, "prefix length");

bool hasPrefix(const char* key) {
    return key && strncmp(key, kPrefix, kPrefixLen) == 0 && key[kPrefixLen] != '\0';
}

std::string configKey(const char* section, const char* key) {
    return std::string(section ? section : "") + "/" + (key ? key : "");
}

bool mkdirs(const std::string& path) {
    std::string cur;
    for (size_t i = 1; i <= path.size(); i++) {
        if (i == path.size() || path[i] == '/') {
            cur = path.substr(0, i);
            if (mkdir(cur.c_str(), 0775) != 0 && errno != EEXIST) return false;
        }
    }
    return true;
}

// Write the table to the file atomically (temp + rename). Caller holds no lock;
// the snapshot was taken under gMu.
bool writeFile(const std::string& path, const std::map<std::string, std::string>& values) {
    std::lock_guard<std::mutex> fl(gFileMu);
    const size_t slash = path.find_last_of('/');
    if (slash != std::string::npos) mkdirs(path.substr(0, slash));
    const std::string tmp = path + ".tmp";
    FILE* f = fopen(tmp.c_str(), "w");
    if (!f) {
        ALOGE("azahar_settings: cannot write %s: %s", tmp.c_str(), strerror(errno));
        return false;
    }
    fputs("# azahar-nano per-game settings override. This file is the source of truth\n"
          "# for this game while it exists; the global settings are not read.\n"
          "# Section/key lines are config.ini keys, the others are frontend settings.\n", f);
    for (const auto& kv : values) {
        fputs(kv.first.c_str(), f);
        fputc('=', f);
        fputs(kv.second.c_str(), f);
        fputc('\n', f);
    }
    const bool ok = fflush(f) == 0 && fsync(fileno(f)) == 0;
    fclose(f);
    if (!ok || rename(tmp.c_str(), path.c_str()) != 0) {
        ALOGE("azahar_settings: cannot commit %s: %s", path.c_str(), strerror(errno));
        unlink(tmp.c_str());
        return false;
    }
    return true;
}

void writerMain() {
    for (;;) {
        std::string path;
        std::map<std::string, std::string> snapshot;
        {
            std::unique_lock<std::mutex> lk(gMu);
            gCv.wait(lk, [] { return gDirty; });
            gDirty = false;
            if (!gActive) continue;
            path = gPath;
            snapshot = gValues;
        }
        writeFile(path, snapshot);
    }
}

// Caller holds gMu.
void scheduleSave() {
    gDirty = true;
    if (!gWriterStarted) {
        gWriterStarted = true;
        std::thread(writerMain).detach();
    }
    gCv.notify_one();
}

// Caller holds gMu. True when the override holds this short key; *val is its
// value (possibly empty = unset).
bool lookupShort(const std::string& shortKey, std::string* val) {
    if (!gActive) return false;
    auto it = gValues.find(shortKey);
    if (it == gValues.end()) return false;
    *val = it->second;
    return true;
}

bool keyOk(const std::string& k) {
    if (k.empty()) return false;
    for (char c : k) {
        if (!(isalnum((unsigned char)c) || c == '_' || c == '.' || c == '/')) return false;
    }
    return true;
}

}  // namespace

int get(const char* key, char* out, const char* def) {
    std::string v;
    bool hit = false;
    if (hasPrefix(key)) {
        std::lock_guard<std::mutex> lk(gMu);
        hit = lookupShort(key + kPrefixLen, &v);
    }
    if (!hit) return property_get(key, out, def);
    if (v.empty()) {
        // Same contract as property_get on an unset property.
        if (def) {
            strlcpy(out, def, PROPERTY_VALUE_MAX);
            return (int)strlen(out);
        }
        out[0] = '\0';
        return 0;
    }
    strlcpy(out, v.c_str(), PROPERTY_VALUE_MAX);
    return (int)strlen(out);
}

bool getBool(const char* key, bool def) {
    char v[PROPERTY_VALUE_MAX] = {};
    if (get(key, v, nullptr) <= 0) return def;
    if (!strcmp(v, "1") || !strcasecmp(v, "true") || !strcasecmp(v, "y") ||
        !strcasecmp(v, "yes") || !strcasecmp(v, "on")) return true;
    if (!strcmp(v, "0") || !strcasecmp(v, "false") || !strcasecmp(v, "n") ||
        !strcasecmp(v, "no") || !strcasecmp(v, "off")) return false;
    return def;
}

int getInt(const char* key, int def) {
    char v[PROPERTY_VALUE_MAX] = {};
    if (get(key, v, nullptr) <= 0) return def;
    char* end = nullptr;
    errno = 0;
    const long n = strtol(v, &end, 10);
    if (end == v || *end != '\0' || errno == ERANGE) return def;
    return (int)n;
}

void set(const char* key, const char* val) {
    const char* v = val ? val : "";
    {
        std::lock_guard<std::mutex> lk(gMu);
        if (gActive && hasPrefix(key)) {
            gValues[key + kPrefixLen] = v;
            scheduleSave();
            return;
        }
    }
    property_set(key, v);
}

bool configLookup(const char* section, const char* key, std::string* out) {
    std::lock_guard<std::mutex> lk(gMu);
    if (!gActive) return false;
    auto it = gValues.find(configKey(section, key));
    if (it == gValues.end() || it->second.empty()) return false;   // empty = unset
    *out = it->second;
    return true;
}

bool setConfig(const char* section, const char* key, const char* value) {
    std::lock_guard<std::mutex> lk(gMu);
    if (!gActive) return false;
    gValues[configKey(section, key)] = value ? value : "";
    scheduleSave();
    return true;
}

std::string overridesText() {
    std::lock_guard<std::mutex> lk(gMu);
    std::string out;
    if (!gActive) return out;
    for (const auto& kv : gValues) {
        if (kv.first.find('/') == std::string::npos || kv.second.empty()) continue;
        out += kv.first;
        out += '=';
        out += kv.second;
        out += '\n';
    }
    return out;
}

void setOverridePath(const std::string& path) {
    std::lock_guard<std::mutex> lk(gMu);
    gPath = path;
}

std::string overridePath() {
    std::lock_guard<std::mutex> lk(gMu);
    return gPath;
}

bool overrideActive() {
    std::lock_guard<std::mutex> lk(gMu);
    return gActive;
}

bool overrideUnreadable() {
    std::lock_guard<std::mutex> lk(gMu);
    return gUnreadable;
}

std::string overrideError() {
    std::lock_guard<std::mutex> lk(gMu);
    return gError;
}

bool load() {
    std::string path;
    {
        std::lock_guard<std::mutex> lk(gMu);
        path = gPath;
        gUnreadable = false;
        gError.clear();
    }
    if (path.empty()) return false;
    struct stat st;
    if (stat(path.c_str(), &st) != 0) {
        if (errno == ENOENT) return false;   // no override for this game
        const std::string err = strerror(errno);
        std::lock_guard<std::mutex> lk(gMu);
        gUnreadable = true;
        gError = err;
        ALOGE("azahar_settings: cannot stat override %s: %s", path.c_str(), err.c_str());
        return false;
    }
    if (!S_ISREG(st.st_mode)) {
        std::lock_guard<std::mutex> lk(gMu);
        gUnreadable = true;
        gError = "not a regular file";
        ALOGE("azahar_settings: override %s is not a regular file", path.c_str());
        return false;
    }
    FILE* f = fopen(path.c_str(), "r");
    if (!f) {
        const std::string err = strerror(errno);
        std::lock_guard<std::mutex> lk(gMu);
        gUnreadable = true;
        gError = err;
        ALOGE("azahar_settings: cannot open override %s: %s", path.c_str(), err.c_str());
        return false;
    }
    std::map<std::string, std::string> values;
    size_t badLines = 0;
    char line[PROPERTY_VALUE_MAX + 256];
    while (fgets(line, sizeof(line), f)) {
        char* s = line;
        while (*s == ' ' || *s == '\t') s++;
        if (*s == '#' || *s == '\n' || *s == '\0') continue;
        char* nl = strpbrk(s, "\r\n");
        if (nl) *nl = '\0';
        char* eq = strchr(s, '=');
        if (!eq) { badLines++; continue; }
        *eq = '\0';
        std::string k(s);
        while (!k.empty() && (k.back() == ' ' || k.back() == '\t')) k.pop_back();
        if (!keyOk(k)) { badLines++; continue; }
        std::string v(eq + 1);
        while (!v.empty() && (v.front() == ' ' || v.front() == '\t')) v.erase(0, 1);
        while (!v.empty() && (v.back() == ' ' || v.back() == '\t')) v.pop_back();
        if (v.size() >= PROPERTY_VALUE_MAX) v.resize(PROPERTY_VALUE_MAX - 1);
        values[k] = v;
    }
    const bool readErr = ferror(f) != 0;
    const std::string err = readErr ? strerror(errno) : std::string();
    fclose(f);
    if (readErr) {
        std::lock_guard<std::mutex> lk(gMu);
        gUnreadable = true;
        gError = err;
        ALOGE("azahar_settings: error reading override %s: %s", path.c_str(), err.c_str());
        return false;
    }
    if (values.empty()) {
        std::lock_guard<std::mutex> lk(gMu);
        gUnreadable = true;
        gError = badLines ? "file is corrupt" : "file is empty";
        ALOGE("azahar_settings: override %s holds no settings (%zu bad lines)", path.c_str(), badLines);
        return false;
    }
    size_t count;
    {
        std::lock_guard<std::mutex> lk(gMu);
        gValues = std::move(values);
        gActive = true;
        gDirty = false;
        count = gValues.size();
    }
    if (badLines) ALOGW("azahar_settings: override %s: skipped %zu unreadable lines", path.c_str(), badLines);
    ALOGI("azahar_settings: per-game override %s active (%zu keys)", path.c_str(), count);
    return true;
}

bool create(const std::vector<std::string>& propKeys,
            const std::vector<std::pair<std::string, std::string>>& configKeys,
            const std::function<std::string(const char*, const char*)>& readConfig) {
    std::string path;
    {
        std::lock_guard<std::mutex> lk(gMu);
        path = gPath;
        if (path.empty()) return false;
        gActive = false;   // the reads below must see the globals
    }
    std::map<std::string, std::string> values;
    for (const std::string& k : propKeys) {
        char v[PROPERTY_VALUE_MAX] = {};
        property_get((std::string(kPrefix) + k).c_str(), v, "");
        values[k] = v;
    }
    for (const auto& sk : configKeys) {
        values[configKey(sk.first.c_str(), sk.second.c_str())] =
            readConfig ? readConfig(sk.first.c_str(), sk.second.c_str()) : "";
    }
    if (!writeFile(path, values)) return false;
    const size_t count = values.size();
    {
        std::lock_guard<std::mutex> lk(gMu);
        gValues = std::move(values);
        gActive = true;
        gUnreadable = false;
        gError.clear();
        gDirty = false;
    }
    ALOGI("azahar_settings: created per-game override %s (%zu keys)", path.c_str(), count);
    return true;
}

bool remove() {
    std::string path;
    {
        std::lock_guard<std::mutex> lk(gMu);
        path = gPath;
        gActive = false;
        gUnreadable = false;
        gError.clear();
        gDirty = false;
        gValues.clear();
    }
    if (path.empty()) return false;
    unlink((path + ".tmp").c_str());
    const bool ok = unlink(path.c_str()) == 0 || errno == ENOENT;
    if (!ok) ALOGE("azahar_settings: cannot delete %s: %s", path.c_str(), strerror(errno));
    else ALOGI("azahar_settings: per-game override %s removed, global settings apply", path.c_str());
    return ok;
}

void flush() {
    std::string path;
    std::map<std::string, std::string> snapshot;
    {
        std::lock_guard<std::mutex> lk(gMu);
        if (!gActive || !gDirty) return;
        gDirty = false;
        path = gPath;
        snapshot = gValues;
    }
    writeFile(path, snapshot);
}

}  // namespace settings
}  // namespace azahar_nano
