/*
 * Copyright (C) 2026 GammaOS
 *
 * NanoZipExtract: an in-process, streaming ZIP -> .nds extractor for
 * drastic-nano's launch path. It parses the archive's central directory to
 * find the DS ROM entry (and its uncompressed size), then inflates it to a
 * file in the cache dir while reporting byte-accurate progress, so the launch
 * loading screen can show a real percentage instead of the old opaque
 * /system/bin/unzip fork (which emits nothing parseable).
 *
 * It is deliberately conservative: anything it does not understand (zip64,
 * encryption, a store/deflate method it cannot stream, a truncated archive)
 * returns kFallback so the caller can fall back to /system/bin/unzip.
 */

#pragma once

#include <cstdint>
#include <functional>
#include <string>

namespace android {
namespace drastic_zip {

// done/total are decompressed bytes; total is the entry's uncompressed size.
using ProgressFn = std::function<void(uint64_t done, uint64_t total)>;

enum ExtractResult {
    kOk       = 0,   // success; *outNdsPath is the written .nds
    kFallback = 1,   // unsupported archive layout; caller should try /system/bin/unzip
    kError    = -1,  // hard I/O / decompression error
};

// Extract the best .nds entry (or the largest entry as a fallback) from
// zipPath into outDir using a streaming inflate, reporting progress. The
// output is named "<zip-stem>.nds" inside outDir. Returns an ExtractResult.
// On kFallback / kError any partial temp file is removed.
int extractNds(const char* zipPath, const char* outDir,
               std::string* outNdsPath, const ProgressFn& onProgress);

}  // namespace drastic_zip
}  // namespace android
