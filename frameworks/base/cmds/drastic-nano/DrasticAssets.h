/*
 * drastic-nano assets: libdrastic and its data shipped in /system, a private
 * runtime root seeded from it, and the user's saves / save states / shaders on
 * shared storage. Nothing here depends on the DraStic APK being installed.
 */
#pragma once

#include <functional>
#include <string>

namespace android {
namespace drastic_assets {

// Shipped by the product makefiles (gammaos/emulators/drastic-nano): game_database.xml,
// usrcheat.dat, system/<bios + firmware>, config/LC_default.dat, shaders/. The two
// libraries live in /system/lib64 (a system binary's linker namespace cannot dlopen
// from /system/etc).
constexpr const char* kSystemDirDefault = "/system/etc/drastic-nano";
// The shipped asset directory, or the runtime-only developer override
// sys.gammaos.drastic_nano.sysdir (to test an asset tree from /data before it
// is in the image).
std::string systemDir();
// The DraStic root libdrastic sees through FakeJNI. Private, root-owned.
constexpr const char* kRootDefault = "/data/system/drastic-nano/DraStic";
// User-facing data on shared storage.
constexpr const char* kUserDir = "/storage/emulated/0/drastic-nano";
// The DraStic app's own data dir, only ever read for the one-time import.
constexpr const char* kLegacyRoot = "/data/user/0/com.dsemu.drastic/files/DraStic";

// Directory holding libdrastic_arm64.so (/system/lib64, or <sysdir>/lib under the
// developer override), or empty when the system copy is missing.
std::string systemLibDir();

// Build / refresh the runtime root: the writable tree libdrastic expects, the
// system assets linked or copied in, backup/ and savestates/ pointing at the
// user dir, and shaders/ = system defaults overlaid with the user's shaders.
// Idempotent and cheap on a warm root. Returns false when the tree is unusable.
bool seedRoot(const std::string& root);

// Refresh only the merged shaders/ directory (system defaults + user overrides).
void mergeShaders(const std::string& root);

struct LegacyCount { int saves = 0; int states = 0; };
// Saves (*.dsv) and save states (*.dss) still sitting in the DraStic app's dir.
LegacyCount scanLegacy();

struct ImportResult { int moved = 0; int skipped = 0; int failed = 0; };
// Move the legacy saves (.sav/.dsv) and save states (.dss) into the user dir. A
// non-empty file already at the destination is left in place on both sides
// (skipped). Files whose name starts with excludeBase (the running game's ROM
// base name) are skipped, since the session rewrites them at exit.
ImportResult importLegacy(const std::function<void(const char*, float)>& progress,
                          const std::string& excludeBase = std::string());

} // namespace drastic_assets
} // namespace android
