// drastic-nano settings lookup with an optional per-game override file.
//
// Every drastic-nano setting is a persist.gammaos.drastic_nano.* property. A game
// may have an override file (<user data folder>/overrides/<rom base>.cfg, one
// "key=value" line per setting, keys without the property prefix). While an
// override is loaded, that file is the source of truth for every key it holds:
// reads of those keys come from the file and never from the global properties,
// and writes of any drastic_nano key go to the file and never to the properties.
// Keys the file does not hold (diagnostic knobs, bookkeeping) and every property
// outside the drastic_nano prefix pass straight through to the property service.
// The global properties are never modified while an override is active, so a
// crash or a sudden power loss can never leave the globals holding one game's
// values.
#pragma once

#include <string>
#include <vector>

namespace android {
namespace drastic_settings {

constexpr const char* kPrefix = "persist.gammaos.drastic_nano.";

// Property-API replacements. Same contract as property_get / property_get_bool /
// property_get_int32 / property_set from libcutils; an empty override value reads
// as "unset" (the default), exactly like an empty property.
int  get(const char* key, char* out, const char* def);
bool getBool(const char* key, bool def);
int  getInt(const char* key, int def);
void set(const char* key, const char* val);

// Override file lifecycle. setOverridePath names this session's file (the
// override may or may not exist yet); load() reads it and activates the override
// when the file exists; create() snapshots the current effective value of each
// short key into a new file and activates it; remove() deletes the file and
// deactivates it, so reads fall back to the global properties at once. Writes to
// the file are coalesced on a worker thread (never on a render thread); flush()
// completes any pending write synchronously (session exit).
void setOverridePath(const std::string& path);
std::string overridePath();
bool overrideActive();
// The override file exists but could not be opened or read (permissions, I/O
// error, storage not mounted): the session runs on the global settings and the
// menu reports it instead of pretending there is no override. overrideError()
// carries the reason for the UI.
bool overrideUnreadable();
std::string overrideError();
bool load();
bool create(const std::vector<std::string>& shortKeys);
bool remove();
void flush();

}  // namespace drastic_settings
}  // namespace android
