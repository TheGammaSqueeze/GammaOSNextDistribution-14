/*
 * drastic-nano: the R4 "usrcheat.dat" cheat database that libdrastic reads its preloaded
 * cheats from. Parsed and written here so user-supplied databases (dropped into the cheats
 * folder) can be merged into the one file libdrastic opens, and so the overlay can tell the
 * shipped cheats from the user's.
 *
 * Layout (validated against the shipped database, 3204 games):
 *   0x000  header, 0x100 bytes: "R4 CheatCode" + description text
 *   0x100  table: {char code[4]; u32 crc; u64 offset} per game, terminated by an all-zero entry
 *   block  title\0 (padded to 4) | u32 count (low 28 bits = records at every depth) | 0x20 bytes of
 *          game info | records
 *   record folder: u32 (0x10000000 | flags<<24 | children) name\0 note\0 (padded) children...
 *          cheat:  u32 (flags<<24 | length in words) name\0 note\0 (padded) u32 n | n words
 *          flags: cheat bit 0 = enabled (libdrastic stores it back into this file), folder
 *          bit 0 = pick one at a time.
 */
#pragma once

#include <stdint.h>
#include <functional>
#include <string>
#include <vector>

namespace android {
namespace drastic_cheatdb {

// A game keeps its records as the bytes the file holds (header word, name, note, words; folders
// followed by their children), not as a tree. A big user database has 680 000 cheats: a tree of
// strings and vectors per cheat peaked at half a gigabyte for the merge, on a 1 GB device, while
// the merge only ever concatenates a game's records and flips enable bits. load() validates every
// record, counts it, and writes its header word and padding back in the canonical form the old
// parse-and-serialise round trip produced, so the bytes are safe to copy between databases.
struct Game {
    std::string code;                  // 4 chars
    uint32_t crc = 0;
    std::string title;
    uint32_t countFlags = 0;           // the count word's high nibble, kept as is
    std::string info;                  // the 0x20 game info bytes
    std::string records;               // all records of the game, top level and folder children
    int cheats = 0;                    // leaf records in `records` (libdrastic numbers them depth-first)
    int folders = 0;                   // folder records in `records`
};

struct Db {
    std::string header;                // 0x100 bytes
    std::vector<Game> games;
};

// True when the file starts with the R4 magic.
bool isCheatDb(const std::string& path);
bool load(const std::string& path, Db* out, std::string* err = nullptr);
// Atomic (tmp + rename), written in chunks so the file is never held in memory a second time.
// 0644 like the shipped copy libdrastic reopens read-write.
bool save(const std::string& path, const Db& db, std::string* err = nullptr);

// A game's identity inside a database: its code and CRC.
uint64_t gameKey(const Game& g);

// Appends `from`'s records after `into`'s (the user's cheats after the shipped ones). `from` is
// left without records.
void appendRecords(Game* into, Game* from);

// One call per cheat whose enable bit is set / per cheat of a game. folder is the name of the
// folder the cheat sits in, nullptr for a top-level cheat; both strings stay valid for the call.
using CheatFn = std::function<void(const char* folder, const char* cheat)>;
using CheatPredicate = std::function<bool(const char* folder, const char* cheat)>;
void forEachEnabled(const Game& g, const CheatFn& fn);
// Sets the enable bit of every cheat of the game for which wanted() returns true.
void enableWhere(Game* g, const CheatPredicate& wanted);

} // namespace drastic_cheatdb
} // namespace android
