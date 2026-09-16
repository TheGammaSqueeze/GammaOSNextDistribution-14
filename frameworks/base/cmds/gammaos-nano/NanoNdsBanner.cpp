#include "NanoNdsBanner.h"

#include <cstring>
#include <cerrno>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <zlib.h>
#include <log/log.h>
#ifndef LOG_TAG
#define LOG_TAG "GammaOSNano"
#endif

namespace {

constexpr size_t kHeaderLen        = 0x200;
constexpr size_t kBannerLen        = 0x840;      // version 1 banner (icon + 6 titles)
constexpr uint32_t kMaxBannerOff   = 512u << 20; // a DS ROM is at most 512 MB
constexpr size_t kNdsBannerMaxInflate = 24u << 20;   // give up on a deflated zip past this

// The DS header/banner checksum: CRC-16 with the reversed 0x8005 polynomial, init 0xFFFF.
uint16_t crc16(const uint8_t* p, size_t n) {
    uint16_t c = 0xFFFF;
    for (size_t i = 0; i < n; i++) {
        c ^= p[i];
        for (int b = 0; b < 8; b++) c = (c & 1) ? (uint16_t)((c >> 1) ^ 0xA001) : (uint16_t)(c >> 1);
    }
    return c;
}
uint16_t rd16(const uint8_t* p) { return (uint16_t)(p[0] | (p[1] << 8)); }
uint32_t rd32(const uint8_t* p) { return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24); }

bool endsWithNoCase(const std::string& s, const char* suf) {
    const size_t n = strlen(suf);
    if (s.size() < n) return false;
    for (size_t i = 0; i < n; i++) {
        char a = s[s.size() - n + i], b = suf[i];
        if (a >= 'A' && a <= 'Z') a = (char)(a + 32);
        if (a != b) return false;
    }
    return true;
}

// Reads `len` bytes at `off` from an fd; false unless the whole range was read.
bool preadAll(int fd, uint8_t* dst, size_t len, off_t off) {
    size_t got = 0;
    while (got < len) {
        ssize_t r = pread(fd, dst + got, len - got, off + (off_t)got);
        if (r <= 0) return false;
        got += (size_t)r;
    }
    return true;
}

// A source of ROM bytes: raw file, stored zip entry, or a deflated zip entry that is
// inflated sequentially (no seeking backwards) up to the highest offset asked for.
struct RomSource {
    int fd = -1;
    off_t base = 0;            // file offset of ROM byte 0 (raw / stored)
    uint64_t size = 0;         // ROM size in bytes
    bool deflated = false;
    uint64_t compSize = 0;     // deflated: compressed bytes at `base`
    // deflate state
    z_stream zs{};
    bool zinit = false;
    uint64_t outPos = 0;       // ROM bytes produced so far
    uint64_t inPos = 0;        // compressed bytes consumed
    std::vector<uint8_t> inBuf;

    ~RomSource() { if (zinit) inflateEnd(&zs); if (fd >= 0) close(fd); }

    // Fill dst with ROM bytes [off, off+len). For deflate, off must not go backwards.
    bool read(uint8_t* dst, size_t len, uint64_t off) {
        if (off + len > size) return false;
        if (!deflated) return preadAll(fd, dst, len, base + (off_t)off);
        if (off < outPos) return false;                          // backwards: unsupported by design
        if (off + len > kNdsBannerMaxInflate) return false;      // too deep into the stream: not worth it
        if (!zinit) {
            memset(&zs, 0, sizeof(zs));
            if (inflateInit2(&zs, -MAX_WBITS) != Z_OK) return false;
            zinit = true;
            inBuf.resize(64 * 1024);
        }
        uint8_t discard[16 * 1024];
        while (outPos < off + len) {
            if (zs.avail_in == 0) {
                if (inPos >= compSize) return false;
                size_t want = inBuf.size();
                if ((uint64_t)want > compSize - inPos) want = (size_t)(compSize - inPos);
                if (!preadAll(fd, inBuf.data(), want, base + (off_t)inPos)) return false;
                zs.next_in = inBuf.data(); zs.avail_in = (uInt)want; inPos += want;
            }
            // Route the output either into dst (inside the wanted window) or a discard buffer.
            uint8_t* o; size_t cap;
            if (outPos < off) { o = discard; cap = sizeof(discard); if ((uint64_t)cap > off - outPos) cap = (size_t)(off - outPos); }
            else { o = dst + (outPos - off); cap = (size_t)(off + len - outPos); }
            zs.next_out = o; zs.avail_out = (uInt)cap;
            const int rc = inflate(&zs, Z_NO_FLUSH);
            const size_t produced = cap - zs.avail_out;
            outPos += produced;
            if (rc == Z_STREAM_END) { if (outPos < off + len) return false; break; }
            if (rc != Z_OK && rc != Z_BUF_ERROR) return false;
            if (rc == Z_BUF_ERROR && produced == 0 && zs.avail_in == 0 && inPos >= compSize) return false;
        }
        return true;
    }
};

// Locate the .nds entry of a zip through the central directory (the reliable sizes live
// there; a streamed local header can carry zeros). Only single-file archives with the
// ROM as the first .nds entry are considered.
bool openZipEntry(RomSource& src, uint64_t fileSize) {
    if (fileSize < 22) return false;
    const size_t tail = (size_t)((fileSize < 66 * 1024) ? fileSize : 66 * 1024);
    std::vector<uint8_t> t(tail);
    if (!preadAll(src.fd, t.data(), tail, (off_t)(fileSize - tail))) return false;
    size_t eocd = std::string::npos;
    for (size_t i = tail - 22 + 1; i-- > 0;) {
        if (t[i] == 0x50 && t[i+1] == 0x4b && t[i+2] == 0x05 && t[i+3] == 0x06) { eocd = i; break; }
    }
    if (eocd == std::string::npos) return false;
    const uint16_t entries = rd16(&t[eocd + 10]);
    const uint32_t cdSize = rd32(&t[eocd + 12]);
    const uint32_t cdOff = rd32(&t[eocd + 16]);
    if (entries == 0 || entries > 64 || cdSize < 46 || cdSize > 1u << 20 || (uint64_t)cdOff + cdSize > fileSize) return false;
    std::vector<uint8_t> cd(cdSize);
    if (!preadAll(src.fd, cd.data(), cdSize, (off_t)cdOff)) return false;
    size_t p = 0;
    for (int e = 0; e < entries && p + 46 <= cd.size(); e++) {
        if (rd32(&cd[p]) != 0x02014b50) return false;
        const uint16_t method = rd16(&cd[p + 10]);
        const uint32_t csize = rd32(&cd[p + 20]);
        const uint32_t usize = rd32(&cd[p + 24]);
        const uint16_t nlen = rd16(&cd[p + 28]), xlen = rd16(&cd[p + 30]), clen = rd16(&cd[p + 32]);
        const uint32_t lho = rd32(&cd[p + 42]);
        if (p + 46 + nlen > cd.size()) return false;
        std::string name((const char*)&cd[p + 46], nlen);
        p += 46 + nlen + xlen + clen;
        if (!endsWithNoCase(name, ".nds")) continue;
        if (usize == 0xFFFFFFFFu || csize == 0xFFFFFFFFu) return false;   // zip64: not handled
        if (method != 0 && method != 8) return false;
        uint8_t lh[30];
        if ((uint64_t)lho + 30 > fileSize || !preadAll(src.fd, lh, 30, (off_t)lho)) return false;
        if (rd32(lh) != 0x04034b50) return false;
        const uint64_t data = (uint64_t)lho + 30 + rd16(&lh[26]) + rd16(&lh[28]);
        if (data + csize > fileSize) return false;
        src.base = (off_t)data; src.size = usize; src.deflated = (method == 8); src.compSize = csize;
        return usize >= kHeaderLen + kBannerLen;
    }
    return false;
}

// UTF-16LE (256 bytes, NUL terminated) -> UTF-8. Lines are separated by '\n' in the ROM.
std::string utf16leToUtf8(const uint8_t* p, size_t bytes) {
    std::string out;
    for (size_t i = 0; i + 1 < bytes; i += 2) {
        uint32_t c = rd16(p + i);
        if (c == 0) break;
        if (c >= 0xD800 && c <= 0xDBFF && i + 3 < bytes) {
            const uint32_t lo = rd16(p + i + 2);
            if (lo >= 0xDC00 && lo <= 0xDFFF) { c = 0x10000 + ((c - 0xD800) << 10) + (lo - 0xDC00); i += 2; }
            else continue;
        } else if (c >= 0xDC00 && c <= 0xDFFF) continue;
        if (c < 0x80) out += (char)c;
        else if (c < 0x800) { out += (char)(0xC0 | (c >> 6)); out += (char)(0x80 | (c & 0x3F)); }
        else if (c < 0x10000) { out += (char)(0xE0 | (c >> 12)); out += (char)(0x80 | ((c >> 6) & 0x3F)); out += (char)(0x80 | (c & 0x3F)); }
        else { out += (char)(0xF0 | (c >> 18)); out += (char)(0x80 | ((c >> 12) & 0x3F)); out += (char)(0x80 | ((c >> 6) & 0x3F)); out += (char)(0x80 | (c & 0x3F)); }
    }
    return out;
}

// "Name\nSubtitle\nPublisher" -> "Name Subtitle": the last line of a multi-line title is the
// publisher on every retail cartridge, so it is dropped; a single line is used as is.
std::string titleFromBannerText(const std::string& text) {
    std::vector<std::string> lines;
    std::string cur;
    for (char ch : text) { if (ch == '\n') { lines.push_back(cur); cur.clear(); } else if (ch != '\r') cur += ch; }
    if (!cur.empty()) lines.push_back(cur);
    auto trim = [](std::string& s) {
        size_t a = 0, b = s.size();
        while (a < b && (unsigned char)s[a] <= ' ') a++;
        while (b > a && (unsigned char)s[b - 1] <= ' ') b--;
        s = s.substr(a, b - a);
    };
    for (auto& l : lines) trim(l);
    std::vector<std::string> keep;
    for (auto& l : lines) if (!l.empty()) keep.push_back(l);
    if (keep.empty()) return "";
    if (keep.size() >= 2) keep.pop_back();
    std::string out;
    for (size_t i = 0; i < keep.size(); i++) { if (i) out += ' '; out += keep[i]; }
    if (out.size() > 120) out.resize(120);
    return out;
}

} // namespace

bool ndsReadBanner(const std::string& path, NdsBannerInfo& out) {
    out.title.clear(); out.rgba.clear();
    RomSource src;
    src.fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (src.fd < 0) { ALOGW("ndsbanner: %s: open failed (%d)", path.c_str(), errno); return false; }
    struct stat st{};
    if (fstat(src.fd, &st) != 0 || !S_ISREG(st.st_mode)) return false;
    const uint64_t fileSize = (uint64_t)st.st_size;
    if (endsWithNoCase(path, ".zip")) {
        if (!openZipEntry(src, fileSize)) { ALOGW("ndsbanner: %s: no usable .nds zip entry", path.c_str()); return false; }
    } else if (endsWithNoCase(path, ".nds")) {
        src.base = 0; src.size = fileSize; src.deflated = false;
    } else {
        return false;
    }
    if (src.size < kHeaderLen + kBannerLen) return false;

    uint8_t hdr[kHeaderLen];
    if (!src.read(hdr, kHeaderLen, 0)) { ALOGW("ndsbanner: %s: header read failed", path.c_str()); return false; }
    // Header checksum (0x15E covers 0x000..0x15D) and the fixed logo checksum: both must hold
    // for a genuine, intact cartridge image.
    if (rd16(hdr + 0x15E) != crc16(hdr, 0x15E) || rd16(hdr + 0x15C) != 0xCF56) { ALOGW("ndsbanner: %s: header checksum mismatch", path.c_str()); return false; }
    const uint32_t bannerOff = rd32(hdr + 0x68);
    if (bannerOff < kHeaderLen || bannerOff > kMaxBannerOff || (uint64_t)bannerOff + kBannerLen > src.size) { ALOGW("ndsbanner: %s: bad banner offset 0x%x", path.c_str(), bannerOff); return false; }

    uint8_t ban[kBannerLen];
    if (!src.read(ban, kBannerLen, bannerOff)) { ALOGW("ndsbanner: %s: banner read failed (offset 0x%x)", path.c_str(), bannerOff); return false; }
    const uint16_t version = rd16(ban);
    if ((version & 0xFF) < 1 || (version & 0xFF) > 3) return false;
    // Banner CRC (0x02) covers 0x20..0x83F for every version.
    if (rd16(ban + 2) != crc16(ban + 0x20, 0x820)) { ALOGW("ndsbanner: %s: banner checksum mismatch", path.c_str()); return false; }

    // Icon: 16 tiles of 8x8, 4 bits per pixel (low nibble first), palette of 16 BGR555 entries.
    out.rgba.assign(32 * 32 * 4, 0);
    const uint8_t* tiles = ban + 0x20;
    const uint8_t* pal = ban + 0x220;
    for (int ty = 0; ty < 4; ty++) for (int tx = 0; tx < 4; tx++) {
        const uint8_t* tile = tiles + (ty * 4 + tx) * 32;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            const uint8_t byte = tile[y * 4 + x / 2];
            const int idx = (x & 1) ? (byte >> 4) : (byte & 0x0F);
            uint8_t* px = &out.rgba[(((ty * 8 + y) * 32) + (tx * 8 + x)) * 4];
            if (idx == 0) { px[0] = px[1] = px[2] = px[3] = 0; continue; }
            const uint16_t c = rd16(pal + idx * 2);
            px[0] = (uint8_t)(((c) & 0x1F) * 255 / 31);
            px[1] = (uint8_t)(((c >> 5) & 0x1F) * 255 / 31);
            px[2] = (uint8_t)(((c >> 10) & 0x1F) * 255 / 31);
            px[3] = 255;
        }
    }
    // Titles: 6 languages x 256 bytes from 0x240 (Japanese, English, French, German, Italian,
    // Spanish). English first, then any other non-empty one.
    static const int kOrder[6] = { 1, 0, 2, 3, 4, 5 };
    for (int k = 0; k < 6; k++) {
        std::string t = titleFromBannerText(utf16leToUtf8(ban + 0x240 + kOrder[k] * 0x100, 0x100));
        if (!t.empty()) { out.title = t; break; }
    }
    return true;
}
