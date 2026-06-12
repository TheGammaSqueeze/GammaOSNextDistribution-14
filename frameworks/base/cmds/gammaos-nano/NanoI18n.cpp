// NanoI18n - see NanoI18n.h.
#include "NanoI18n.h"
#include "NanoJson.h"

#include <cstdio>
#include <cstring>
#include <string>
#include <unordered_map>

namespace android {

static std::unordered_map<std::string, std::string> sI18nMap;
// The resource code currently loaded into sI18nMap. nanoSetLocale is called
// every frame on the live-preview language step, so re-reading + re-parsing the
// JSON each time would be wasteful; skip when the code has not changed.
static std::string sLoadedCode;

static bool readFile(const char* path, std::string* out) {
    FILE* f = fopen(path, "rb");
    if (!f) return false;
    char buf[8192];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) out->append(buf, n);
    fclose(f);
    return true;
}

void i18nLoad(const char* localeCode) {
    const char* code = (localeCode && localeCode[0]) ? localeCode : "en";
    if (sLoadedCode == code) return;   // already loaded: no-op (cheap per-frame)
    sLoadedCode = code;

    sI18nMap.clear();
    if (strcmp(code, "en") == 0) return;   // English: passthrough, empty map

    char path[256];
    std::string text;
    // /data override first (user/OTA editable), then the shipped resource.
    snprintf(path, sizeof(path), "/data/system/nano_i18n/%s.json", code);
    if (!readFile(path, &text)) {
        snprintf(path, sizeof(path), "/system/etc/gammaos-nano/i18n/%s.json", code);
        if (!readFile(path, &text)) return;
    }

    njson::Value v;
    if (!njson::parse(text, &v) || !v.isObject()) return;
    sI18nMap.reserve(v.obj.size());
    for (const auto& kv : v.obj) {
        if (kv.second.isString() && !kv.second.str.empty())
            sI18nMap.emplace(kv.first, kv.second.str);
    }
}

const char* trDyn(const char* english) {
    if (!english || !english[0] || sI18nMap.empty()) return english;
    auto it = sI18nMap.find(english);
    return (it != sI18nMap.end()) ? it->second.c_str() : english;
}

} // namespace android
