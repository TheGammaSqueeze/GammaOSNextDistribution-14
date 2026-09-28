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
    const std::string& d;
    size_t p = 0;
    bool bad = false;
    explicit Reader(const std::string& data) : d(data) {}
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
    void align4() { p = (p + 3) & ~(size_t)3; }
};

bool readRecord(Reader& r, Record* rec, int depth) {
    uint32_t hdr = r.u32();
    if (r.bad) return false;
    rec->name = r.cstr();
    rec->note = r.cstr();
    r.align4();
    if (r.bad) return false;
    if (hdr & kFolderBit) {
        if (depth > 0) return false;           // the format has one level of folders
        rec->folder = true;
        rec->flags = (uint8_t)((hdr >> 24) & 0x0f);
        uint32_t n = hdr & 0xffff;
        for (uint32_t i = 0; i < n; i++) {
            Record c;
            if (!readRecord(r, &c, depth + 1)) return false;
            rec->children.push_back(std::move(c));
        }
    } else {
        rec->folder = false;
        rec->flags = (uint8_t)(hdr >> 24);
        uint32_t n = r.u32();
        if (r.bad || n > 0x100000) return false;
        rec->words.reserve(n);
        for (uint32_t i = 0; i < n; i++) rec->words.push_back(r.u32());
        if (r.bad) return false;
    }
    return true;
}

void put32(std::string& out, uint32_t v) {
    out.push_back((char)(v & 0xff)); out.push_back((char)((v >> 8) & 0xff));
    out.push_back((char)((v >> 16) & 0xff)); out.push_back((char)((v >> 24) & 0xff));
}
void putStrings(std::string& out, const std::string& a, const std::string& b) {
    out += a; out.push_back('\0'); out += b; out.push_back('\0');
    while (out.size() & 3) out.push_back('\0');
}

void writeRecord(std::string& out, const Record& rec) {
    if (rec.folder) {
        put32(out, kFolderBit | ((uint32_t)(rec.flags & 0x0f) << 24) | (uint32_t)(rec.children.size() & 0xffff));
        putStrings(out, rec.name, rec.note);
        for (const Record& c : rec.children) writeRecord(out, c);
    } else {
        std::string body;
        putStrings(body, rec.name, rec.note);
        put32(body, (uint32_t)rec.words.size());
        for (uint32_t w : rec.words) put32(body, w);
        put32(out, ((uint32_t)rec.flags << 24) | (uint32_t)((body.size() / 4) & 0xffffff));
        out += body;
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
        uint32_t seen = 0;
        while (seen < n) {
            Record rec;
            if (!readRecord(r, &rec, 0)) {
                if (err) *err = "bad record in " + g.code;
                return false;
            }
            seen += 1 + (uint32_t)rec.children.size();
            g.records.push_back(std::move(rec));
        }
        out->games.push_back(std::move(g));
    }
    return true;
}

bool save(const std::string& path, const Db& db, std::string* err) {
    std::string out = db.header;
    out.resize(kHeaderSize, '\0');
    // Table first (fixed size), blocks after it; offsets are absolute.
    const size_t tableBytes = (db.games.size() + 1) * 16;
    std::string blocks;
    std::vector<uint64_t> offsets;
    offsets.reserve(db.games.size());
    for (const Game& g : db.games) {
        offsets.push_back(kHeaderSize + tableBytes + blocks.size());
        blocks += g.title; blocks.push_back('\0');
        while (blocks.size() & 3) blocks.push_back('\0');
        int cheats = 0, folders = 0;
        countRecords(g.records, &cheats, &folders);
        put32(blocks, (g.countFlags & 0xf0000000u) | (uint32_t)((cheats + folders) & 0x0fffffff));
        std::string info = g.info; info.resize(kInfoSize, '\0');
        blocks += info;
        for (const Record& rec : g.records) writeRecord(blocks, rec);
    }
    for (size_t i = 0; i < db.games.size(); i++) {
        std::string code = db.games[i].code; code.resize(4, '\0');
        out += code;
        put32(out, db.games[i].crc);
        put32(out, (uint32_t)(offsets[i] & 0xffffffffu));
        put32(out, (uint32_t)(offsets[i] >> 32));
    }
    out.append(16, '\0');
    out += blocks;

    const std::string tmp = path + ".tmp";
    int fd = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (fd < 0) { if (err) *err = strerror(errno); return false; }
    size_t off = 0;
    while (off < out.size()) {
        ssize_t n = write(fd, out.data() + off, out.size() - off);
        if (n <= 0) { if (err) *err = strerror(errno); close(fd); unlink(tmp.c_str()); return false; }
        off += (size_t)n;
    }
    if (fsync(fd) != 0 || close(fd) != 0 || rename(tmp.c_str(), path.c_str()) != 0) {
        if (err) *err = strerror(errno);
        unlink(tmp.c_str());
        return false;
    }
    return true;
}

void countRecords(const std::vector<Record>& recs, int* cheats, int* folders) {
    for (const Record& r : recs) {
        if (r.folder) { (*folders)++; countRecords(r.children, cheats, folders); }
        else (*cheats)++;
    }
}

} // namespace drastic_cheatdb
} // namespace android
