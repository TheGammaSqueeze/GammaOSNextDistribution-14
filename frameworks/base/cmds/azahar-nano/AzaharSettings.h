// azahar-nano settings lookup with an optional per-game override file.
//
// The frontend's settings are persist.gammaos.azahar_nano.* properties and the
// core's config.ini keys ([Section] key = value). A game may have an override file
// (<user folder>/overrides/<rom base>.cfg): one "key=value" line per setting, a
// property by its short key (lcd_top=4) and a config.ini key as Section/key
// (Renderer/resolution_factor=2). While an override is loaded, that file is the
// source of truth for every key it holds: reads of those keys come from the file,
// never from the global properties or config.ini, and writes of any azahar_nano
// property or any config.ini key go to the file. The global properties and
// config.ini are never modified while an override is active, so a crash or a
// sudden power loss can never leave the globals holding one game's values. The
// config.ini part reaches the core through azn_set_config_overrides (the core lays
// it over config.ini whenever it loads its settings).
//
// Same design as drastic-nano's DrasticSettings.
#pragma once

#include <functional>
#include <string>
#include <utility>
#include <vector>

namespace azahar_nano {
namespace settings {

constexpr const char* kPrefix = "persist.gammaos.azahar_nano.";

// Property-API replacements. Same contract as property_get / property_get_bool /
// property_get_int32 / property_set from libcutils; an empty override value reads
// as "unset" (the default), exactly like an empty property. Keys outside the
// prefix pass straight through.
int  get(const char* key, char* out, const char* def);
bool getBool(const char* key, bool def);
int  getInt(const char* key, int def);
void set(const char* key, const char* val);

// config.ini keys. configLookup is true when the override holds a value for the
// key (then *out is it); setConfig is true when the override took the write (the
// caller does not touch config.ini then).
bool configLookup(const char* section, const char* key, std::string* out);
bool setConfig(const char* section, const char* key, const char* value);
// "Section/key=value" lines of every config key the override holds, for the core
// (empty when no override is active).
std::string overridesText();

// Override file lifecycle (see DrasticSettings.h): setOverridePath names this
// session's file; load() activates it when it exists; create() snapshots the
// current global value of every listed key into a new file (config keys through
// readConfig, which must read config.ini itself) and activates it; remove()
// deletes the file and deactivates it. Writes are coalesced on a worker thread;
// flush() completes a pending write (session exit).
void setOverridePath(const std::string& path);
std::string overridePath();
bool overrideActive();
bool overrideUnreadable();
std::string overrideError();
bool load();
bool create(const std::vector<std::string>& propKeys,
            const std::vector<std::pair<std::string, std::string>>& configKeys,
            const std::function<std::string(const char*, const char*)>& readConfig);
bool remove();
void flush();

}  // namespace settings
}  // namespace azahar_nano
