// NanoI18n - runtime, resource-file based translation for the nano UIs.
//
// Translations live in per-language JSON resource files (English-keyed objects)
// so they can be edited/extended without recompiling:
//   /data/system/nano_i18n/<code>.json   (user/OTA override, checked first)
//   /system/etc/gammaos-nano/i18n/<code>.json   (shipped)
// where <code> is "es", "fr", "ja", "zh-cn", "zh-tw", ... ("en" = passthrough).
//
// trDyn(english) returns the translation for the loaded locale, or the original
// English pointer when there is no entry - so dynamic/non-UI strings (SSIDs,
// numbers, paths) pass straight through. Both gammaos-nano and drastic-nano
// compile this and share the same resource files via the system locale.
#pragma once

namespace android {

// Load the translation map for the given resource code (see above). Passing
// nullptr/empty/"en" clears the map (English passthrough). Replaces any prior
// map; cheap to call on every locale change.
void i18nLoad(const char* localeCode);

// Translate an English UI string for the currently loaded locale. Returns the
// argument unchanged when there is no translation (or the locale is English).
const char* trDyn(const char* english);

} // namespace android
