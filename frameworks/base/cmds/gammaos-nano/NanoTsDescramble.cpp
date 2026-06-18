// NanoTsDescramble - shared MPEG-TS descramble data source. See NanoTsDescramble.h.
#include "NanoTsDescramble.h"

#include <unistd.h>
#include <cstring>
#include <cstdint>
#include <vector>

namespace android {
namespace {
constexpr int kTsPkt = 188;

uint32_t mpegCrc32(const uint8_t* d, int n) {     // MPEG-2 systems CRC (poly 0x04C11DB7)
    uint32_t crc = 0xFFFFFFFFu;
    for (int i = 0; i < n; i++) {
        crc ^= (uint32_t)d[i] << 24;
        for (int b = 0; b < 8; b++)
            crc = (crc & 0x80000000u) ? ((crc << 1) ^ 0x04C11DB7u) : (crc << 1);
    }
    return crc;
}

// Clean one TS packet on the PMT pid (188 bytes). Two jobs:
//  1. The real PMT (PUSI + table_id 0x02): rewrite any CA descriptor tag to 0xFF and
//     fix the section CRC, so Android sees a clear (non-scrambled) program.
//  2. ANY other packet on the PMT pid (a foreign table - HDHomeRun multiplexes a
//     proprietary table_id 0xc0 onto the PMT pid - or a continuation): rewrite its PID
//     to 0x1FFF (a null packet). Android's ATSParser parses every PUSI packet on the
//     PMT pid as a PMT and bails with "PMT data error!" the moment it sees a non-0x02
//     table_id, so those packets must not reach it. Returns true if the packet changed.
bool patchPmtPacket(uint8_t* p, int pmtPid) {
    if (p[0] != 0x47) return false;
    int pid = ((p[1] & 0x1f) << 8) | p[2];
    if (pid != pmtPid) return false;
    // Identify the real, single-packet PMT (PUSI + table_id 0x02 + section fits).
    bool isPmt = false;
    int off = 0, secEnd = 0, secLen = 0;
    if ((p[1] >> 6) & 1) {                              // PUSI
        int afc = (p[3] >> 4) & 3;
        if (afc != 0 && afc != 2) {                     // has payload
            off = 4;
            if (afc == 3) off += 1 + p[4];              // adaptation field
            if (off + 1 <= kTsPkt) {
                off += 1 + p[off];                      // pointer_field
                if (off + 3 <= kTsPkt && p[off] == 0x02) {
                    secLen = ((p[off + 1] & 0x0f) << 8) | p[off + 2];
                    secEnd = off + 3 + secLen;          // includes the 4 CRC bytes
                    if (secEnd <= kTsPkt && secLen >= 13) isPmt = true;
                }
            }
        }
    }
    if (!isPmt) {
        // Foreign table / continuation on the PMT pid -> turn it into a null packet so
        // ATSParser only ever parses the real PMT on this pid.
        p[1] = (uint8_t)((p[1] & 0xE0) | 0x1F);
        p[2] = 0xFF;
        return true;
    }
    int pil = ((p[off + 10] & 0x0f) << 8) | p[off + 11];
    int pp = off + 12, piEnd = pp + pil;
    int crcStart = secEnd - 4;
    if (piEnd > crcStart) return false;                // malformed
    bool changed = false;
    while (pp + 2 <= piEnd) {                           // program-level descriptors
        int len = p[pp + 1];
        if (p[pp] == 0x09) { p[pp] = 0xFF; changed = true; }
        pp += 2 + len;
    }
    int es = piEnd;                                     // ES loop
    while (es + 5 <= crcStart) {
        int esil = ((p[es + 3] & 0x0f) << 8) | p[es + 4];
        int d = es + 5, dEnd = d + esil;
        if (dEnd > crcStart) break;
        while (d + 2 <= dEnd) {                         // ES-level descriptors
            int len = p[d + 1];
            if (p[d] == 0x09) { p[d] = 0xFF; changed = true; }
            d += 2 + len;
        }
        es = dEnd;
    }
    if (changed) {
        uint32_t crc = mpegCrc32(p + off, crcStart - off);
        p[crcStart]     = (crc >> 24) & 0xFF;
        p[crcStart + 1] = (crc >> 16) & 0xFF;
        p[crcStart + 2] = (crc >> 8) & 0xFF;
        p[crcStart + 3] = crc & 0xFF;
    }
    return changed;
}

// Data source userdata: a dup'd fd + the file size + the PMT pid to patch.
struct TsPatch { int fd; off64_t size; int pmtPid; };

ssize_t tsReadAt(void* u, off64_t offset, void* buffer, size_t size) {
    TsPatch* t = (TsPatch*)u;
    if (size == 0) return 0;
    if (offset >= t->size) return -1;                  // EOF (API: -1 at end of stream)
    // Read the ENCLOSING 188-aligned window and patch every complete packet in it, then
    // copy out the requested sub-range. This guarantees every byte the extractor sees
    // comes from a fully-patched packet even when its reads are not packet-aligned.
    off64_t aStart = (offset / kTsPkt) * kTsPkt;
    off64_t aEnd = ((offset + (off64_t)size + kTsPkt - 1) / kTsPkt) * kTsPkt;
    if (aEnd > t->size) aEnd = ((t->size + kTsPkt - 1) / kTsPkt) * kTsPkt;
    size_t span = (size_t)(aEnd - aStart);
    std::vector<uint8_t> tmp(span);
    ssize_t got = pread(t->fd, tmp.data(), span, aStart);
    if (got <= 0) return got == 0 ? -1 : got;
    for (off64_t p = 0; p + kTsPkt <= got; p += kTsPkt)
        patchPmtPacket(tmp.data() + (size_t)p, t->pmtPid);
    off64_t skip = offset - aStart;
    if (skip >= got) return -1;
    size_t avail = (size_t)(got - skip);
    size_t n = avail < size ? avail : size;
    memcpy(buffer, tmp.data() + (size_t)skip, n);
    return (ssize_t)n;
}
ssize_t tsGetSize(void* u) { return (ssize_t)((TsPatch*)u)->size; }
void    tsCloseCb(void* /*u*/) {}   // fd is freed in tsFreeDataSource after the source is deleted
}  // namespace

bool tsNeedsDescramble(int fd, int& outPmtPid) {
    static const int kScan = kTsPkt * 4000;            // ~752 KB prefix
    std::vector<uint8_t> buf(kScan);
    ssize_t n = pread(fd, buf.data(), kScan, 0);
    if (n < kTsPkt * 8) return false;
    if (buf[0] != 0x47 || buf[kTsPkt] != 0x47 || buf[2 * kTsPkt] != 0x47) return false;  // need TS sync @0
    // PAT (pid 0) -> first program's PMT pid.
    int pmtPid = -1;
    for (ssize_t i = 0; i + kTsPkt <= n && pmtPid < 0; i += kTsPkt) {
        uint8_t* p = &buf[i];
        if (p[0] != 0x47) continue;
        if ((((p[1] & 0x1f) << 8) | p[2]) != 0) continue;     // PAT pid 0
        if (((p[1] >> 6) & 1) == 0) continue;                  // PUSI
        int afc = (p[3] >> 4) & 3, off = 4;
        if (afc == 3) off += 1 + p[4]; else if (afc != 1) continue;
        off += 1 + p[off];                                     // pointer_field
        if (off + 8 > kTsPkt) continue;
        int sl = ((p[off + 1] & 0x0f) << 8) | p[off + 2];
        int end = off + 3 + sl - 4;                            // before CRC
        if (end > kTsPkt) end = kTsPkt;
        for (int j = off + 8; j + 4 <= end; j += 4) {
            int pn = (p[j] << 8) | p[j + 1];
            int ppid = ((p[j + 2] & 0x1f) << 8) | p[j + 3];
            if (pn != 0) { pmtPid = ppid; break; }
        }
    }
    if (pmtPid < 0) return false;
    // Scan PMT packets for a CA descriptor (program-level or ES-level).
    for (ssize_t i = 0; i + kTsPkt <= n; i += kTsPkt) {
        uint8_t* p = &buf[i];
        if (p[0] != 0x47) continue;
        if ((((p[1] & 0x1f) << 8) | p[2]) != pmtPid) continue;
        if (((p[1] >> 6) & 1) == 0) continue;
        int afc = (p[3] >> 4) & 3, off = 4;
        if (afc == 3) off += 1 + p[4]; else if (afc != 1) continue;
        off += 1 + p[off];
        if (off + 12 > kTsPkt) continue;
        if (p[off] != 0x02) continue;
        int secLen = ((p[off + 1] & 0x0f) << 8) | p[off + 2];
        int secEnd = off + 3 + secLen;
        if (secEnd > kTsPkt) continue;
        int pil = ((p[off + 10] & 0x0f) << 8) | p[off + 11];
        int pp = off + 12, piEnd = pp + pil, crcStart = secEnd - 4;
        if (piEnd > crcStart) continue;
        while (pp + 2 <= piEnd) { if (p[pp] == 0x09) { outPmtPid = pmtPid; return true; } pp += 2 + p[pp + 1]; }
        int es = piEnd;
        while (es + 5 <= crcStart) {
            int esil = ((p[es + 3] & 0x0f) << 8) | p[es + 4];
            int d = es + 5, dEnd = d + esil;
            if (dEnd > crcStart) break;
            while (d + 2 <= dEnd) { if (p[d] == 0x09) { outPmtPid = pmtPid; return true; } d += 2 + p[d + 1]; }
            es = dEnd;
        }
    }
    return false;
}

AMediaDataSource* tsMakeDataSource(int fd, off64_t size, int pmtPid, void** outUserdata) {
    int dfd = dup(fd);
    if (dfd < 0) return nullptr;
    TsPatch* t = new TsPatch{dfd, size, pmtPid};
    AMediaDataSource* ds = AMediaDataSource_new();
    if (!ds) { ::close(dfd); delete t; return nullptr; }
    AMediaDataSource_setUserdata(ds, t);
    AMediaDataSource_setReadAt(ds, tsReadAt);
    AMediaDataSource_setGetSize(ds, tsGetSize);
    AMediaDataSource_setClose(ds, tsCloseCb);
    *outUserdata = t;
    return ds;
}

void tsFreeDataSource(AMediaDataSource* ds, void* userdata) {
    if (ds) AMediaDataSource_delete(ds);
    if (userdata) {
        TsPatch* t = (TsPatch*)userdata;
        if (t->fd >= 0) ::close(t->fd);
        delete t;
    }
}

} // namespace android
