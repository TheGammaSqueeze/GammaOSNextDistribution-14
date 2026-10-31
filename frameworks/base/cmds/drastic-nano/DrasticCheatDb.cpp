#define LOG_TAG "drastic-nano"

#include "DrasticCheatDb.h"

#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <utils/Log.h>

namespace android {
namespace drastic_cheatdb {

namespace {

constexpr size_t kHeaderSize = 0x100;
constexpr size_t kInfoSize   = 0x20;
constexpr uint32_t kFolderBit = 0x10000000u;
const char kMagic[] = "R4 CheatCode";

bool readAll(const std::string& path, std::string* out) {
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size <= 0 || st.st_size > (256 << 20)) { close(fd); return false; }
    out->resize((size_t)st.st_size);
    size_t off = 0;
    while (off < out->size()) {
        ssize_t n = read(fd, &(*out)[off], out->size() - off);
        if (n <= 0) { close(fd); return false; }
        off += (size_t)n;
    }
    close(fd);
    return true;
}

struct Reader {
    std::string& d;
    size_t p = 0;
    bool bad = false;
    explicit Reader(std::string& data) : d(data) {}
    uint32_t u32() {
        if (p + 4 > d.size()) { bad = true; return 0; }
        uint32_t v = (uint8_t)d[p] | ((uint8_t)d[p + 1] << 8) | ((uint8_t)d[p + 2] << 16)
                     | ((uint32_t)(uint8_t)d[p + 3] << 24);
        p += 4;
        return v;
    }
    std::string cstr() {
        size_t e = d.find('\0', p);
        if (e == std::string::npos) { bad = true; p = d.size(); return {}; }
        std::string s = d.substr(p, e - p);
        p = e + 1;
        return s;
    }
    // Steps over a NUL-terminated string without copying it.
    void skipStr() {
        size_t e = d.find('\0', p);
        if (e == std::string::npos) { bad = true; p = d.size(); return; }
        p = e + 1;
    }
    void align4() { p = (p + 3) & ~(size_t)3; }
};

void put32(std::string& out, uint32_t v) {
    out.push_back((char)(v & 0xff)); out.push_back((char)((v >> 8) & 0xff));
    out.push_back((char)((v >> 16) & 0xff)); out.push_back((char)((v >> 24) & 0xff));
}
void set32(std::string& d, size_t at, uint32_t v) {
    d[at] = (char)(v & 0xff); d[at + 1] = (char)((v >> 8) & 0xff);
    d[at + 2] = (char)((v >> 16) & 0xff); d[at + 3] = (char)((v >> 24) & 0xff);
}

// What scanRecord found out about a game's records.
struct Scan {
    int cheats = 0, folders = 0;
    uint32_t seen = 0;                 // records at every depth, the count word's unit
    bool dirty = false;                // some header word or padding byte is not canonical
};

// Walks one record (and, for a folder, its children): checks it the way the old tree parser did
// (strings end inside the file, one level of folders, at most 0x100000 words per cheat) and counts
// it. The old serialiser rebuilt every header word (the cheat's length in words, a folder's flag
// nibble and child count) and wrote zeros as string padding; a record that differs from that is
// marked dirty. With fix set the header and padding are rewritten in place instead, which is done
// on the copy of a game's records (a damaged file whose records overrun into the next game's block
// must not change what that game reads).
bool scanRecord(Reader& r, int depth, Scan* sc, bool fix) {
    const size_t hp = r.p;
    const uint32_t hdr = r.u32();
    if (r.bad) return false;
    r.skipStr();
    r.skipStr();
    if (r.bad) return false;
    const size_t strEnd = r.p;
    r.align4();
    if (r.p > r.d.size()) return false;
    for (size_t i = strEnd; i < r.p; i++) {
        if (r.d[i] != 0) { sc->dirty = true; if (fix) r.d[i] = 0; }
    }
    sc->seen++;
    uint32_t canon;
    if (hdr & kFolderBit) {
        if (depth > 0) return false;           // the format has one level of folders
        const uint32_t n = hdr & 0xffff;
        canon = kFolderBit | (hdr & 0x0f000000u) | n;
        sc->folders++;
        // The header is settled before the children are read; they do not touch it.
        if (canon != hdr) { sc->dirty = true; if (fix) set32(r.d, hp, canon); }
        for (uint32_t i = 0; i < n; i++)
            if (!scanRecord(r, depth + 1, sc, fix)) return false;
    } else {
        const uint32_t n = r.u32();
        if (r.bad || n > 0x100000) return false;
        if (r.p + (size_t)n * 4 > r.d.size()) return false;
        r.p += (size_t)n * 4;
        canon = (hdr & 0xff000000u) | (uint32_t)(((r.p - hp - 4) / 4) & 0xffffff);
        sc->cheats++;
        if (canon != hdr) { sc->dirty = true; if (fix) set32(r.d, hp, canon); }
    }
    return true;
}

// Calls visit(header offset, folder name or nullptr, cheat name) for every cheat in a game's record
// bytes; folders themselves are skipped, their children follow as ordinary records. The bytes came
// through load(), so they are well formed: the bounds checks only keep a damaged Game from running
// off the end. S is std::string or const std::string.
template <class S, class Visit>
void walkCheats(S& rec, Visit&& visit) {
    const size_t size = rec.size();
    const char* base = rec.data();
    auto u32at = [&](size_t at) {
        return (uint32_t)(uint8_t)base[at] | ((uint32_t)(uint8_t)base[at + 1] << 8)
               | ((uint32_t)(uint8_t)base[at + 2] << 16) | ((uint32_t)(uint8_t)base[at + 3] << 24);
    };
    // Offset just past the NUL that ends the string at `at`, or npos.
    auto strEnd = [&](size_t at) -> size_t {
        const void* z = memchr(base + at, 0, size - at);
        return z ? (size_t)((const char*)z - base) + 1 : std::string::npos;
    };
    size_t p = 0;
    const char* folderName = nullptr;
    uint32_t childrenLeft = 0;
    while (p + 4 <= size) {
        const size_t hp = p;
        const uint32_t hdr = u32at(hp);
        const size_t nameAt = hp + 4;
        const size_t noteAt = strEnd(nameAt);
        if (noteAt == std::string::npos) return;
        const size_t after = strEnd(noteAt);
        if (after == std::string::npos) return;
        p = (after + 3) & ~(size_t)3;
        const char* name = base + nameAt;
        if (hdr & kFolderBit) {
            folderName = name;
            childrenLeft = hdr & 0xffff;
            continue;
        }
        if (p + 4 > size) return;
        const uint32_t n = u32at(p);
        if ((size - p - 4) / 4 < n) return;
        p += 4 + (size_t)n * 4;
        const char* parent = nullptr;
        if (childrenLeft > 0) { parent = folderName; childrenLeft--; }
        visit(hp, parent, name);
    }
}

} // namespace

bool isCheatDb(const std::string& path) {
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    char m[sizeof(kMagic)] = {};
    ssize_t n = read(fd, m, sizeof(kMagic) - 1);
    close(fd);
    return n == (ssize_t)(sizeof(kMagic) - 1) && memcmp(m, kMagic, sizeof(kMagic) - 1) == 0;
}

bool load(const std::string& path, Db* out, std::string* err) {
    std::string d;
    if (!readAll(path, &d)) { if (err) *err = "cannot read"; return false; }
    if (d.size() < kHeaderSize + 16 || memcmp(d.data(), kMagic, sizeof(kMagic) - 1) != 0) {
        if (err) *err = "not an R4 cheat database"; return false;
    }
    out->header = d.substr(0, kHeaderSize);
    out->games.clear();
    struct Entry { std::string code; uint32_t crc; uint64_t off; };
    std::vector<Entry> table;
    for (size_t p = kHeaderSize; p + 16 <= d.size(); p += 16) {
        if (d[p] == 0 && d[p + 1] == 0 && d[p + 2] == 0 && d[p + 3] == 0) break;
        Reader r(d); r.p = p + 4;
        uint32_t crc = r.u32();
        uint64_t off = r.u32(); off |= (uint64_t)r.u32() << 32;
        table.push_back({d.substr(p, 4), crc, off});
    }
    out->games.reserve(table.size());
    for (const Entry& e : table) {
        if (e.off + 8 > d.size()) { if (err) *err = "table offset past end"; return false; }
        Reader r(d); r.p = (size_t)e.off;
        Game g;
        g.code = e.code; g.crc = e.crc;
        g.title = r.cstr(); r.align4();
        uint32_t count = r.u32();
        if (r.bad || r.p + kInfoSize > d.size()) { if (err) *err = "truncated game block"; return false; }
        g.countFlags = count & 0xf0000000u;
        g.info = d.substr(r.p, kInfoSize); r.p += kInfoSize;
        // The count is the number of records at every depth (folders plus cheats).
        const uint32_t n = count & 0x0fffffffu;
        Scan sc;
        const size_t recStart = r.p;
        while (sc.seen < n) {
            if (!scanRecord(r, 0, &sc, false)) {
                if (err) *err = "bad record in " + g.code;
                return false;
            }
        }
        g.cheats = sc.cheats;
        g.folders = sc.folders;
        g.records = d.substr(recStart, r.p - recStart);
        if (sc.dirty) {
            // Rare: a file whose length fields or padding differ from what R4 tools write. The
            // copy is walked again and rewritten; the second walk cannot fail, the first accepted it.
            Reader fixer(g.records);
            Scan again;
            while (again.seen < n && scanRecord(fixer, 0, &again, true)) {}
        }
        out->games.push_back(std::move(g));
    }
    return true;
}

bool save(const std::string& path, const Db& db, std::string* err) {
    // Table first (fixed size), blocks after it; offsets are absolute, so the block sizes are
    // summed up front and the blocks then go to the file one after the other.
    const size_t tableBytes = (db.games.size() + 1) * 16;
    std::vector<uint64_t> offsets;
    offsets.reserve(db.games.size());
    uint64_t pos = kHeaderSize + tableBytes;
    for (const Game& g : db.games) {
        offsets.push_back(pos);
        pos += ((g.title.size() + 1 + 3) & ~(size_t)3) + 4 + kInfoSize + g.records.size();
    }

    const std::string tmp = path + ".tmp";
    int fd = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (fd < 0) { if (err) *err = strerror(errno); return false; }
    std::string buf;
    buf.reserve((1 << 20) + (64 << 10));
    bool ok = true;
    auto flush = [&]() {
        size_t off = 0;
        while (ok && off < buf.size()) {
            ssize_t n = write(fd, buf.data() + off, buf.size() - off);
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0) { if (err) *err = strerror(errno); ok = false; break; }
            off += (size_t)n;
        }
        buf.clear();
    };
    // Appends raw bytes, flushing whenever a megabyte has gathered.
    auto emit = [&](const std::string& bytes) {
        buf += bytes;
        if (buf.size() >= (1 << 20)) flush();
    };

    buf = db.header;
    buf.resize(kHeaderSize, '\0');
    for (size_t i = 0; i < db.games.size(); i++) {
        std::string code = db.games[i].code; code.resize(4, '\0');
        buf += code;
        put32(buf, db.games[i].crc);
        put32(buf, (uint32_t)(offsets[i] & 0xffffffffu));
        put32(buf, (uint32_t)(offsets[i] >> 32));
    }
    buf.append(16, '\0');
    for (const Game& g : db.games) {
        if (!ok) break;
        buf += g.title; buf.push_back('\0');
        while (buf.size() & 3) buf.push_back('\0');
        put32(buf, (g.countFlags & 0xf0000000u) | (uint32_t)((g.cheats + g.folders) & 0x0fffffff));
        std::string info = g.info; info.resize(kInfoSize, '\0');
        buf += info;
        emit(g.records);
    }
    flush();
    if (!ok) { close(fd); unlink(tmp.c_str()); return false; }
    const bool synced = fsync(fd) == 0;
    const bool closed = close(fd) == 0;
    if (!synced || !closed || rename(tmp.c_str(), path.c_str()) != 0) {
        if (err) *err = strerror(errno);
        unlink(tmp.c_str());
        return false;
    }
    return true;
}

uint64_t gameKey(const Game& g) {
    uint32_t c = 0;
    for (size_t i = 0; i < 4 && i < g.code.size(); i++) c |= (uint32_t)(uint8_t)g.code[i] << (8 * i);
    return ((uint64_t)c << 32) | g.crc;
}

void appendRecords(Game* into, Game* from) {
    into->records += from->records;
    into->cheats += from->cheats;
    into->folders += from->folders;
    std::string().swap(from->records);    // give the memory back now, not when the whole file is dropped
    from->cheats = from->folders = 0;
}

void forEachEnabled(const Game& g, const CheatFn& fn) {
    walkCheats(g.records, [&](size_t hp, const char* folder, const char* cheat) {
        if ((uint8_t)g.records[hp + 3] & 1) fn(folder, cheat);
    });
}

void enableWhere(Game* g, const CheatPredicate& wanted) {
    walkCheats(g->records, [&](size_t hp, const char* folder, const char* cheat) {
        if (wanted(folder, cheat)) g->records[hp + 3] = (char)((uint8_t)g->records[hp + 3] | 1);
    });
}

} // namespace drastic_cheatdb
} // namespace android
