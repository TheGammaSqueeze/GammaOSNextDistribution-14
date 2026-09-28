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
#include <string>
#include <vector>

namespace android {
namespace drastic_cheatdb {

struct Record {
    bool folder = false;
    uint8_t flags = 0;                 // hdr >> 24 (folder: bit 4 stripped)
    std::string name, note;
    std::vector<uint32_t> words;       // cheat only
    std::vector<Record> children;      // folder only
};

struct Game {
    std::string code;                  // 4 chars
    uint32_t crc = 0;
    std::string title;
    uint32_t countFlags = 0;           // the count word's high nibble, kept as is
    std::string info;                  // the 0x20 game info bytes
    std::vector<Record> records;       // top level
};

struct Db {
    std::string header;                // 0x100 bytes
    std::vector<Game> games;
};

// True when the file starts with the R4 magic.
bool isCheatDb(const std::string& path);
bool load(const std::string& path, Db* out, std::string* err = nullptr);
// Atomic (tmp + rename). 0644 like the shipped copy libdrastic reopens read-write.
bool save(const std::string& path, const Db& db, std::string* err = nullptr);

// Leaf (cheat) and folder counts over a record list, in libdrastic's index order
// (cheats are numbered depth-first, folders in order of appearance).
void countRecords(const std::vector<Record>& recs, int* cheats, int* folders);

} // namespace drastic_cheatdb
} // namespace android
