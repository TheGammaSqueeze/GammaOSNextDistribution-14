/*
 * Copyright (C) 2026 GammaOS
 *
 * See NanoZipExtract.h. The central-directory parse mirrors the (proven)
 * archive-aware reader in NanoRetroAchievements.cpp, but here it writes the
 * inflated payload to disk with progress callbacks instead of hashing it.
 */

#include "NanoZipExtract.h"

#include <zlib.h>

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include <sys/stat.h>
#include <unistd.h>

namespace android {
namespace drastic_zip {

namespace {

inline uint16_t rd16(const unsigned char* p) {
    return (uint16_t)(p[0] | (p[1] << 8));
}
inline uint32_t rd32(const unsigned char* p) {
    return (uint32_t)(p[0] | (p[1] << 8) | (p[2] << 16) | ((uint32_t)p[3] << 24));
}

struct ZipEntry {
    uint64_t dataOfs = 0;    // absolute file offset of the entry payload
    uint64_t compSize = 0;   // compressed payload size
    uint64_t uncompSize = 0; // decompressed size
    uint16_t method = 0;     // 0 = stored, 8 = deflate
};

// Locate the best .nds entry (or largest entry) and resolve its payload
// offset. Returns false for zip64 / encrypted / unsupported-method / truncated
// archives (the caller then falls back to /system/bin/unzip).
bool findEntry(FILE* fp, ZipEntry* out) {
    if (fseek(fp, 0, SEEK_END) != 0) return false;
    long fileSize = ftell(fp);
    if (fileSize < 22) return false;

    long scan = fileSize < 65557L ? fileSize : 65557L;  // 64KB comment + 22
    std::vector<unsigned char> tail((size_t)scan);
    if (fseek(fp, fileSize - scan, SEEK_SET) != 0) return false;
    if (fread(tail.data(), 1, (size_t)scan, fp) != (size_t)scan) return false;

    long eocd = -1;
    for (long i = scan - 22; i >= 0; --i) {
        if (tail[i] == 0x50 && tail[i + 1] == 0x4B &&
            tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
            eocd = i;
            break;
        }
    }
    if (eocd < 0) return false;
    const unsigned char* e = &tail[eocd];
    uint16_t nEntries = rd16(e + 10);
    uint32_t cdSize   = rd32(e + 12);
    uint32_t cdOfs    = rd32(e + 16);
    if (nEntries == 0 || nEntries == 0xFFFFu ||
        cdSize == 0xFFFFFFFFu || cdOfs == 0xFFFFFFFFu)
        return false;  // empty or zip64

    std::vector<unsigned char> cd((size_t)cdSize);
    if (fseek(fp, (long)cdOfs, SEEK_SET) != 0) return false;
    if (fread(cd.data(), 1, (size_t)cdSize, fp) != (size_t)cdSize) return false;

    uint64_t bestUncomp = 0;
    bool found = false, foundNds = false;
    uint64_t p = 0;
    for (uint16_t i = 0; i < nEntries && p + 46 <= cdSize; ++i) {
        const unsigned char* c = &cd[p];
        if (rd32(c) != 0x02014b50u) break;
        uint16_t flag     = rd16(c + 8);
        uint16_t meth     = rd16(c + 10);
        uint32_t csize    = rd32(c + 20);
        uint32_t usize    = rd32(c + 24);
        uint16_t nameLen  = rd16(c + 28);
        uint16_t extraLen = rd16(c + 30);
        uint16_t cmtLen   = rd16(c + 32);
        uint32_t lho      = rd32(c + 42);
        if (p + 46 + (uint64_t)nameLen + extraLen + cmtLen > (uint64_t)cdSize) break;
        const char* name = (const char*)(c + 46);

        bool encrypted = (flag & 0x0001) != 0;
        bool isNds = (nameLen >= 4) &&
            name[nameLen - 4] == '.' &&
            (name[nameLen - 3] == 'n' || name[nameLen - 3] == 'N') &&
            (name[nameLen - 2] == 'd' || name[nameLen - 2] == 'D') &&
            (name[nameLen - 1] == 's' || name[nameLen - 1] == 'S');

        if (!encrypted && (meth == 0 || meth == 8) &&
            csize != 0xFFFFFFFFu && usize != 0xFFFFFFFFu && usize > 0) {
            bool take = false;
            if (isNds && !foundNds) { take = true; foundNds = true; }
            else if (isNds && foundNds && usize > bestUncomp) take = true;
            else if (!foundNds && usize > bestUncomp) take = true;
            if (take) {
                out->method = meth;
                out->compSize = csize;
                out->uncompSize = usize;
                out->dataOfs = lho;  // local-header offset; resolved below
                bestUncomp = usize;
                found = true;
            }
        }
        p += (uint64_t)46 + nameLen + extraLen + cmtLen;
    }
    if (!found) return false;

    unsigned char lh[30];
    if (fseek(fp, (long)out->dataOfs, SEEK_SET) != 0) return false;
    if (fread(lh, 1, 30, fp) != 30) return false;
    if (rd32(lh) != 0x04034b50u) return false;
    uint16_t lNameLen  = rd16(lh + 26);
    uint16_t lExtraLen = rd16(lh + 28);
    out->dataOfs = out->dataOfs + 30 + lNameLen + lExtraLen;
    return true;
}

std::string zipStem(const char* zipPath) {
    std::string s(zipPath ? zipPath : "");
    size_t slash = s.find_last_of('/');
    if (slash != std::string::npos) s = s.substr(slash + 1);
    size_t dot = s.find_last_of('.');
    if (dot != std::string::npos && dot > 0) s = s.substr(0, dot);
    if (s.empty()) s = "rom";
    return s;
}

}  // namespace

int extractNds(const char* zipPath, const char* outDir,
               std::string* outNdsPath, const ProgressFn& onProgress) {
    if (!zipPath || !outDir || !outNdsPath) return kError;

    FILE* fp = fopen(zipPath, "rb");
    if (!fp) return kError;

    ZipEntry z;
    if (!findEntry(fp, &z)) {
        fclose(fp);
        return kFallback;  // unknown layout -> let /system/bin/unzip try
    }

    const std::string tmpPath = std::string(outDir) + "/.extract.tmp";
    const std::string dstPath = std::string(outDir) + "/" + zipStem(zipPath) + ".nds";

    FILE* out = fopen(tmpPath.c_str(), "wb");
    if (!out) { fclose(fp); return kError; }

    if (fseek(fp, (long)z.dataOfs, SEEK_SET) != 0) {
        fclose(out); fclose(fp); unlink(tmpPath.c_str());
        return kError;
    }

    const size_t kBuf = 1u << 16;  // 64KB chunks
    std::vector<unsigned char> inbuf(kBuf);
    std::vector<unsigned char> outbuf(kBuf);
    uint64_t compLeft = z.compSize;
    uint64_t produced = 0;
    bool ok = true;

    if (onProgress) onProgress(0, z.uncompSize);

    if (z.method == 0) {
        // Stored: the payload is the plaintext.
        while (compLeft > 0) {
            size_t want = compLeft < kBuf ? (size_t)compLeft : kBuf;
            size_t got = fread(inbuf.data(), 1, want, fp);
            if (got == 0) { ok = false; break; }
            if (fwrite(inbuf.data(), 1, got, out) != got) { ok = false; break; }
            compLeft -= got;
            produced += got;
            if (onProgress) onProgress(produced, z.uncompSize);
        }
    } else {
        // Deflate: raw stream (no zlib header).
        z_stream zs;
        memset(&zs, 0, sizeof(zs));
        if (inflateInit2(&zs, -MAX_WBITS) != Z_OK) {
            fclose(out); fclose(fp); unlink(tmpPath.c_str());
            return kError;
        }
        bool done = false;
        while (!done) {
            if (zs.avail_in == 0 && compLeft > 0) {
                size_t want = compLeft < kBuf ? (size_t)compLeft : kBuf;
                size_t got = fread(inbuf.data(), 1, want, fp);
                if (got == 0) { ok = false; break; }
                compLeft -= got;
                zs.next_in = inbuf.data();
                zs.avail_in = (uInt)got;
            }
            zs.next_out = outbuf.data();
            zs.avail_out = (uInt)kBuf;
            int r = inflate(&zs, Z_NO_FLUSH);
            size_t have = kBuf - zs.avail_out;
            if (have > 0) {
                if (fwrite(outbuf.data(), 1, have, out) != have) { ok = false; break; }
                produced += have;
                if (onProgress) onProgress(produced, z.uncompSize);
            }
            if (r == Z_STREAM_END) { done = true; }
            else if (r != Z_OK && r != Z_BUF_ERROR) { ok = false; break; }
            else if (have == 0 && zs.avail_in == 0 && compLeft == 0) { done = true; }
            else if (have == 0 && r == Z_BUF_ERROR && zs.avail_in > 0) { ok = false; break; }
        }
        inflateEnd(&zs);
    }

    fflush(out);
    int outFd = fileno(out);
    if (outFd >= 0) fsync(outFd);
    fclose(out);
    fclose(fp);

    // A short read (produced != uncompSize) means the archive was truncated or
    // the stream was mis-parsed; treat it as unusable and fall back.
    if (!ok || produced != z.uncompSize) {
        unlink(tmpPath.c_str());
        return kFallback;
    }

    if (rename(tmpPath.c_str(), dstPath.c_str()) != 0) {
        unlink(tmpPath.c_str());
        return kError;
    }
    if (onProgress) onProgress(z.uncompSize, z.uncompSize);
    *outNdsPath = dstPath;
    return kOk;
}

}  // namespace drastic_zip
}  // namespace android
