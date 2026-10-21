// Cheats for azahar-nano: the shipped database (CTRPF, 60FPS and Sharkive Action Replay
// codes, packed by tools/make_cheat_db.py into /system/etc/azahar-nano/cheats3ds.zip) merged
// with the user's own cheat files into the file the core loads for a title.
//
// Files, under the user data folder (the same one that holds saves and states):
//   cheats/<TID>.txt         what the core loads and saves (its enabled marks live here):
//                            the user's cheats first, then the database's
//   cheats/custom/<TID>.txt  the user's own cheats for the title, kept as written; cheats
//                            added from the in-game menu land here too
// The merge runs before every game start, so a database update or an edited custom file
// shows up at the next launch and the enabled marks carry over by cheat name.
#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace azahar_nano {
namespace cheatdb {

struct Cheat {
    std::string name;
    std::string comments;              // '\n' separated, without the leading '*'
    std::vector<std::string> code;     // "XXXXXXXX YYYYYYYY" lines
    bool enabled = false;
};

// Parses Gateway / CTRPF cheat text (the core's own file format).
std::vector<Cheat> parseText(const std::string& text);
// The core's file format (GatewayCheat::ToString), one block per cheat.
std::string renderText(const std::vector<Cheat>& cheats);

std::string databasePath();                                      // the shipped zip
std::string cheatFile(const std::string& dataDir, uint64_t titleId);
std::string customFile(const std::string& dataDir, uint64_t titleId);
std::string customDir(const std::string& dataDir);
// The database's cheats for a title (empty when it has none or the zip is missing).
std::vector<Cheat> databaseCheats(uint64_t titleId);

// Writes cheats/<TID>.txt = custom + database, enabled marks kept from the previous file.
// Returns the number of custom cheats (they come first in the file, so the core's indices
// below it are the user's own). Cheats found in the previous file that are in neither
// list (written by an older build or by hand) are adopted into the custom file.
int prepare(const std::string& dataDir, uint64_t titleId);
// Adds a cheat to the custom file and re-merges; `code` is hex words separated by spaces or
// newlines ("D3000000 14000000 128B6044 00005050"). err explains a refusal.
bool addCustom(const std::string& dataDir, uint64_t titleId, const std::string& name,
               const std::string& code, std::string* err);
// Removes the custom cheat at `index` (its position among the custom cheats) and re-merges.
bool removeCustom(const std::string& dataDir, uint64_t titleId, int index);

} // namespace cheatdb
} // namespace azahar_nano
