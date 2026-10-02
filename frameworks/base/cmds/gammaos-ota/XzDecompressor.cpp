/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "GammaOSOta"

#include "XzDecompressor.h"

#include <fcntl.h>
#include <spawn.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <linux/fs.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <cinttypes>
#include <algorithm>
#include <string>
#include <vector>

#include <map>

#include <openssl/sha.h>
#include <utils/Log.h>

extern char** environ;

namespace android {

static const size_t BUF_SIZE = 1024 * 1024; // 1MB chunks
static const size_t DIO_ALIGN = 4096;       // satisfies 512-byte and 4K-LBA devices

static std::string hexDigest(SHA256_CTX* ctx) {
    uint8_t hash[SHA256_DIGEST_LENGTH];
    SHA256_Final(hash, ctx);
    char hex[SHA256_DIGEST_LENGTH * 2 + 1];
    for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) snprintf(hex + i * 2, 3, "%02x", hash[i]);
    return std::string(hex);
}

// A 1 MiB buffer aligned for O_DIRECT, freed on scope exit.
struct AlignedBuf {
    uint8_t* p = nullptr;
    AlignedBuf() {
        void* v = nullptr;
        if (posix_memalign(&v, DIO_ALIGN, BUF_SIZE) == 0) p = static_cast<uint8_t*>(v);
    }
    ~AlignedBuf() { free(p); }
};

// Read exactly len bytes (a short read before EOF is retried; EOF early is an error).
static bool readFull(int fd, uint8_t* buf, size_t len) {
    size_t got = 0;
    while (got < len) {
        ssize_t n = read(fd, buf + got, len - got);
        if (n < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (n == 0) { errno = ENODATA; return false; }
        got += (size_t)n;
    }
    return true;
}

static const char* const kXzPaths[] = { "/data/gammaos-ota-stage/bin/xz", "/system/bin/xz" };

// Spawn xz with the given argv and fds (-1 = /dev/null). posix_spawn rather than fork: bionic's
// posix_spawn does not run pthread_atfork handlers, and the GPU driver's handler crashed the
// child once vendor had been rewritten (SIGBUS in libGLES_mali.so from fork(), 1.4.3).
static bool spawnXzArgs(char* const argv[], int inFd, int outFd, pid_t* pid) {
    posix_spawn_file_actions_t fa;
    posix_spawn_file_actions_init(&fa);
    if (inFd >= 0) posix_spawn_file_actions_adddup2(&fa, inFd, STDIN_FILENO);
    else posix_spawn_file_actions_addopen(&fa, STDIN_FILENO, "/dev/null", O_RDONLY, 0);
    if (outFd >= 0) posix_spawn_file_actions_adddup2(&fa, outFd, STDOUT_FILENO);
    else posix_spawn_file_actions_addopen(&fa, STDOUT_FILENO, "/dev/null", O_WRONLY, 0);
    int rc = ENOENT;
    for (const char* path : kXzPaths) {
        if (access(path, X_OK) != 0) continue;
        rc = posix_spawn(pid, path, &fa, nullptr, argv, environ);
        if (rc == 0) break;
        ALOGW("posix_spawn %s failed: %s", path, strerror(rc));
    }
    posix_spawn_file_actions_destroy(&fa);
    return rc == 0;
}

// Memory xz may use for threaded decompression. xz 5.6 and later decompress multithreaded by
// default, and on a package made with -T0 that is about 40 MB per thread (5 threads, ~200 MB
// resident, on a quad core): on a 1 GB device that sat on top of the pinned code and the staging
// writes and pushed the system into heavy reclaim. A sixth of what is available keeps the device
// responsive; xz then runs fewer threads (down to one, its soft limit) and the output is bound by
// storage speed either way. 0 when this xz does not know the option (older than 5.4).
static uint64_t xzThreadMemLimit() {
    static int sSupported = -1;
    if (sSupported < 0) {
        char* argv[] = { const_cast<char*>("xz"), const_cast<char*>("--memlimit-mt-decompress=64MiB"),
                         const_cast<char*>("--version"), nullptr };
        pid_t pid = -1;
        int status = 0;
        sSupported = spawnXzArgs(argv, -1, -1, &pid) && waitpid(pid, &status, 0) == pid &&
                     WIFEXITED(status) && WEXITSTATUS(status) == 0;
    }
    if (!sSupported) return 0;
    uint64_t availKb = 0;
    if (FILE* f = fopen("/proc/meminfo", "re")) {
        char line[128];
        while (fgets(line, sizeof(line), f))
            if (sscanf(line, "MemAvailable: %" SCNu64 " kB", &availKb) == 1) break;
        fclose(f);
    }
    const uint64_t kMin = 32ull << 20, kMax = 160ull << 20;
    return std::min(kMax, std::max(kMin, availKb * 1024 / 6));
}

// Start xz -d -c with stdin/stdout on the given fds.
static bool spawnXz(int inFd, int outFd, pid_t* pid) {
    const uint64_t limit = xzThreadMemLimit();
    std::string mt = "--memlimit-mt-decompress=" + std::to_string(limit);
    std::vector<char*> argv = { const_cast<char*>("xz"), const_cast<char*>("-d"), const_cast<char*>("-c") };
    if (limit) {
        argv.push_back(const_cast<char*>("-T0"));
        argv.push_back(const_cast<char*>(mt.c_str()));
        ALOGI("xz threaded decompression limited to %llu MB", (unsigned long long)(limit >> 20));
    }
    argv.push_back(nullptr);
    return spawnXzArgs(argv.data(), inFd, outFd, pid);
}

bool XzDecompressor::decompressToFile(const std::string& xzPath,
                                       const std::string& outPath,
                                       uint64_t expectedSize,
                                       ProgressCallback progress) {
    // Decompress to a regular file on /data first: if decompression fails, the target partition
    // is untouched.
    int inFd = open(xzPath.c_str(), O_RDONLY | O_CLOEXEC);
    if (inFd < 0) {
        ALOGE("Failed to open %s: %s", xzPath.c_str(), strerror(errno));
        return false;
    }
    int outFd = open(outPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (outFd < 0) {
        ALOGE("Failed to create %s: %s", outPath.c_str(), strerror(errno));
        close(inFd);
        return false;
    }

    pid_t pid = -1;
    const bool started = spawnXz(inFd, outFd, &pid);
    close(inFd);
    close(outFd);
    if (!started) {
        ALOGE("Could not start xz for %s", xzPath.c_str());
        unlink(outPath.c_str());
        return false;
    }

    int status = 0;
    for (;;) {
        pid_t ret = waitpid(pid, &status, (expectedSize > 0 && progress) ? WNOHANG : 0);
        if (ret == pid) break;
        if (ret < 0) {
            if (errno == EINTR) continue;
            ALOGE("waitpid(xz) failed: %s", strerror(errno));
            unlink(outPath.c_str());
            return false;
        }
        struct stat st;
        if (progress && stat(outPath.c_str(), &st) == 0 && st.st_size > 0)
            progress((uint64_t)st.st_size, expectedSize);
        usleep(250000);
    }
    if (WIFSIGNALED(status)) {
        ALOGE("xz for %s was killed by signal %d", xzPath.c_str(), WTERMSIG(status));
        unlink(outPath.c_str());
        return false;
    }
    if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        ALOGE("xz for %s failed (exit %d)", xzPath.c_str(), WIFEXITED(status) ? WEXITSTATUS(status) : -1);
        unlink(outPath.c_str());
        return false;
    }

    struct stat st;
    if (stat(outPath.c_str(), &st) != 0) {
        ALOGE("Decompressed output missing after xz exit 0: %s (%s)", outPath.c_str(), strerror(errno));
        unlink(outPath.c_str());
        return false;
    }
    if (progress) progress((uint64_t)st.st_size, expectedSize ? expectedSize : (uint64_t)st.st_size);
    // xz can exit 0 with a short output under memory/IO pressure. A truncated image must never
    // reach a partition, so the size has to match the manifest exactly.
    if (expectedSize > 0 && (uint64_t)st.st_size != expectedSize) {
        ALOGE("Decompressed size mismatch for %s: got %lld bytes, expected %llu", outPath.c_str(),
              (long long)st.st_size, (unsigned long long)expectedSize);
        unlink(outPath.c_str());
        return false;
    }
    // Make the staging file durable before anything reads it back: an error /data reports only at
    // writeback (a failing SD card) shows up here instead of as silently different bytes later.
    int syncFd = open(outPath.c_str(), O_WRONLY | O_CLOEXEC);
    if (syncFd < 0 || fsync(syncFd) != 0) {
        ALOGE("fsync of staging file %s failed: %s", outPath.c_str(), strerror(errno));
        if (syncFd >= 0) close(syncFd);
        unlink(outPath.c_str());
        return false;
    }
    if (close(syncFd) != 0) {
        ALOGE("close of staging file %s failed: %s", outPath.c_str(), strerror(errno));
        unlink(outPath.c_str());
        return false;
    }
    ALOGI("Decompressed %s -> %s: %lld bytes", xzPath.c_str(), outPath.c_str(), (long long)st.st_size);
    return true;
}

bool XzDecompressor::writeFileToBlock(const std::string& filePath,
                                       const std::string& blockDevPath,
                                       uint64_t size,
                                       ProgressCallback progress,
                                       std::string* sentSha) {
    int inFd = open(filePath.c_str(), O_RDONLY | O_CLOEXEC);
    if (inFd < 0) {
        ALOGE("Failed to open %s: %s", filePath.c_str(), strerror(errno));
        return false;
    }
    posix_fadvise(inFd, 0, 0, POSIX_FADV_SEQUENTIAL);

    // Dynamic-partition dm devices are created read-only; clear the block device ro flag first
    // (BLKROSET on an O_RDONLY fd), then reopen for writing.
    int roClearFd = open(blockDevPath.c_str(), O_RDONLY | O_CLOEXEC);
    if (roClearFd >= 0) {
        int roFlag = 0;
        if (ioctl(roClearFd, BLKROSET, &roFlag) != 0)
            ALOGW("BLKROSET(0) failed on %s: %s", blockDevPath.c_str(), strerror(errno));
        close(roClearFd);
    }

    // O_DIRECT: writes go straight to the device, no page cache to fill on a low-RAM device and
    // no need for drop_caches (which would evict running daemons' /system text pages).
    bool useDirect = true;
    int outFd = open(blockDevPath.c_str(), O_WRONLY | O_DIRECT | O_CLOEXEC);
    if (outFd < 0) {
        ALOGW("O_DIRECT open of %s failed (%s), using buffered writes", blockDevPath.c_str(), strerror(errno));
        outFd = open(blockDevPath.c_str(), O_WRONLY | O_CLOEXEC);
        useDirect = false;
    }
    if (outFd < 0) {
        ALOGE("Failed to open %s for writing: %s", blockDevPath.c_str(), strerror(errno));
        close(inFd);
        return false;
    }

    AlignedBuf ab;
    if (!ab.p) {
        ALOGE("posix_memalign failed for %zu bytes", BUF_SIZE);
        close(inFd);
        close(outFd);
        return false;
    }
    SHA256_CTX ctx;
    SHA256_Init(&ctx);
    uint64_t totalWritten = 0;
    bool success = true;

    while (totalWritten < size) {
        const size_t len = ((size - totalWritten) < BUF_SIZE) ? (size_t)(size - totalWritten) : BUF_SIZE;
        if (!readFull(inFd, ab.p, len)) {
            ALOGE("Read of %s failed at offset %llu: %s", filePath.c_str(),
                  (unsigned long long)totalWritten, strerror(errno));
            success = false;
            break;
        }
        SHA256_Update(&ctx, ab.p, len);
        // The staging copy is not needed again once sent: keep the page cache free of it.
        posix_fadvise(inFd, (off_t)totalWritten, (off_t)len, POSIX_FADV_DONTNEED);

        // O_DIRECT needs a length that is a multiple of the logical block size; the tail of an
        // image whose size is not 4K aligned goes out buffered on a second fd.
        if (useDirect && (len & (DIO_ALIGN - 1))) {
            if (fsync(outFd) != 0) {
                ALOGE("fsync of %s before the tail failed: %s", blockDevPath.c_str(), strerror(errno));
                success = false;
                break;
            }
            close(outFd);
            outFd = open(blockDevPath.c_str(), O_WRONLY | O_CLOEXEC);
            if (outFd < 0) {
                ALOGE("Buffered reopen for the tail of %s failed: %s", blockDevPath.c_str(), strerror(errno));
                success = false;
                break;
            }
            useDirect = false;
        }

        size_t done = 0;
        while (done < len) {
            ssize_t w = pwrite(outFd, ab.p + done, len - done, (off_t)(totalWritten + done));
            if (w < 0) {
                if (errno == EINTR) continue;
                ALOGE("Write to %s failed at offset %llu: %s", blockDevPath.c_str(),
                      (unsigned long long)(totalWritten + done), strerror(errno));
                success = false;
                break;
            }
            if (w == 0) {
                ALOGE("Write to %s made no progress at offset %llu (end of device?)", blockDevPath.c_str(),
                      (unsigned long long)(totalWritten + done));
                success = false;
                break;
            }
            done += (size_t)w;
        }
        if (!success) break;
        totalWritten += len;
        if (progress) progress(totalWritten, size);
    }
    close(inFd);

    // Flush to the medium and check it. A device can accept every write() and only report the
    // failure at the flush (EIO from an SD card or eMMC); that used to be ignored, so a bad write
    // could pass as a good one.
    if (outFd >= 0) {
        if (fsync(outFd) != 0) {
            ALOGE("fsync of %s failed: %s", blockDevPath.c_str(), strerror(errno));
            success = false;
        }
        // Drop the block device's buffer cache so a buffered read-back sees the medium.
        ioctl(outFd, BLKFLSBUF, 0);
        if (close(outFd) != 0) {
            ALOGE("close of %s failed: %s", blockDevPath.c_str(), strerror(errno));
            success = false;
        }
    }

    if (sentSha) *sentSha = hexDigest(&ctx);
    if (success)
        ALOGI("Wrote %s -> %s: %llu bytes", filePath.c_str(), blockDevPath.c_str(), (unsigned long long)totalWritten);
    return success;
}

bool XzDecompressor::writeFileToRanges(const std::string& filePath,
                                        const std::vector<ByteRange>& ranges,
                                        uint64_t size,
                                        ProgressCallback progress,
                                        std::string* sentSha) {
    int inFd = open(filePath.c_str(), O_RDONLY | O_CLOEXEC);
    if (inFd < 0) {
        ALOGE("Failed to open %s: %s", filePath.c_str(), strerror(errno));
        return false;
    }
    posix_fadvise(inFd, 0, 0, POSIX_FADV_SEQUENTIAL);
    struct Dev { int fd = -1; bool direct = false; };
    std::map<std::string, Dev> devs;
    auto openDev = [&](const std::string& path, bool direct) -> Dev& {
        Dev& d = devs[path];
        if (d.fd >= 0) { fsync(d.fd); close(d.fd); }
        d.fd = direct ? open(path.c_str(), O_WRONLY | O_DIRECT | O_CLOEXEC) : -1;
        d.direct = d.fd >= 0;
        if (d.fd < 0) d.fd = open(path.c_str(), O_WRONLY | O_CLOEXEC);
        return d;
    };
    AlignedBuf ab;
    if (!ab.p) { close(inFd); return false; }
    SHA256_CTX ctx;
    SHA256_Init(&ctx);
    uint64_t remaining = size, total = 0;
    bool success = true;

    for (const ByteRange& r : ranges) {
        if (remaining == 0 || !success) break;
        Dev* d = &devs[r.device];
        if (d->fd < 0) d = &openDev(r.device, true);
        if (d->fd < 0) {
            ALOGE("Cannot open %s for writing: %s", r.device.c_str(), strerror(errno));
            success = false;
            break;
        }
        uint64_t left = r.length < remaining ? r.length : remaining;
        uint64_t pos = r.offset;
        while (left > 0 && success) {
            const size_t len = left < BUF_SIZE ? (size_t)left : BUF_SIZE;
            if (!readFull(inFd, ab.p, len)) {
                ALOGE("Read of %s failed at offset %llu: %s", filePath.c_str(), (unsigned long long)total,
                      strerror(errno));
                success = false;
                break;
            }
            SHA256_Update(&ctx, ab.p, len);
            posix_fadvise(inFd, (off_t)total, (off_t)len, POSIX_FADV_DONTNEED);
            if (d->direct && ((len | pos) & (DIO_ALIGN - 1))) d = &openDev(r.device, false);
            size_t done = 0;
            while (done < len) {
                ssize_t w = pwrite(d->fd, ab.p + done, len - done, (off_t)(pos + done));
                if (w < 0 && errno == EINTR) continue;
                if (w < 0 && errno == EINVAL && d->direct) { d = &openDev(r.device, false); continue; }
                if (w <= 0) {
                    ALOGE("Write to %s failed at offset %llu: %s", r.device.c_str(),
                          (unsigned long long)(pos + done), w < 0 ? strerror(errno) : "no progress");
                    success = false;
                    break;
                }
                done += (size_t)w;
            }
            pos += len;
            left -= len;
            remaining -= len;
            total += len;
            if (progress) progress(total, size);
        }
    }
    close(inFd);
    for (auto& kv : devs) {
        if (kv.second.fd < 0) continue;
        if (fsync(kv.second.fd) != 0) {
            ALOGE("fsync of %s failed: %s", kv.first.c_str(), strerror(errno));
            success = false;
        }
        ioctl(kv.second.fd, BLKFLSBUF, 0);
        if (close(kv.second.fd) != 0) success = false;
    }
    if (remaining != 0) success = false;
    if (sentSha) *sentSha = hexDigest(&ctx);
    return success;
}

std::string XzDecompressor::sha256File(const std::string& path, uint64_t size,
                                      ProgressCallback progress) {
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return "";
    uint64_t expect = size;
    if (expect == 0) {
        struct stat st;
        if (fstat(fd, &st) == 0) expect = (uint64_t)st.st_size;
    }
    // Clean cached pages are dropped first so the hash is of what is on the medium.
    posix_fadvise(fd, 0, 0, POSIX_FADV_DONTNEED);
    posix_fadvise(fd, 0, 0, POSIX_FADV_SEQUENTIAL);

    std::vector<uint8_t> buf(BUF_SIZE);
    SHA256_CTX ctx;
    SHA256_Init(&ctx);
    uint64_t total = 0;
    for (;;) {
        size_t want = BUF_SIZE;
        if (size > 0) {
            if (total >= size) break;
            if (size - total < want) want = (size_t)(size - total);
        }
        ssize_t n = read(fd, buf.data(), want);
        if (n < 0) {
            if (errno == EINTR) continue;
            close(fd);
            return "";
        }
        if (n == 0) break;
        SHA256_Update(&ctx, buf.data(), (size_t)n);
        posix_fadvise(fd, (off_t)total, n, POSIX_FADV_DONTNEED);
        total += (uint64_t)n;
        if (progress) progress(total, expect);
    }
    close(fd);
    if (size > 0 && total != size) return "";   // short file: not the image the manifest describes
    return hexDigest(&ctx);
}

std::string XzDecompressor::sha256BlockDev(const std::string& blockDevPath, uint64_t size,
                                            ProgressCallback progress) {
    return sha256Ranges({ { blockDevPath, 0, size } }, size, progress);
}

std::string XzDecompressor::sha256Ranges(const std::vector<ByteRange>& ranges, uint64_t size,
                                          ProgressCallback progress) {
    // Reads go past the page cache: after a raw write to a device that is also mounted, cached
    // pages can hold the old content. O_DIRECT reads the medium; a device that refuses O_DIRECT
    // (or an unaligned range) is read buffered after flushing its buffer cache.
    struct Dev { int fd = -1; bool direct = false; };
    std::map<std::string, Dev> devs;
    auto openDev = [&](const std::string& path, bool direct) -> Dev& {
        Dev& d = devs[path];
        if (d.fd >= 0) close(d.fd);
        d.fd = direct ? open(path.c_str(), O_RDONLY | O_DIRECT | O_CLOEXEC) : -1;
        d.direct = d.fd >= 0;
        if (d.fd < 0) {
            d.fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
            if (d.fd >= 0) {
                ioctl(d.fd, BLKFLSBUF, 0);
                posix_fadvise(d.fd, 0, 0, POSIX_FADV_DONTNEED);
            }
        }
        return d;
    };
    auto closeAll = [&]() { for (auto& kv : devs) if (kv.second.fd >= 0) close(kv.second.fd); };

    AlignedBuf ab;
    if (!ab.p) return "";
    SHA256_CTX ctx;
    SHA256_Init(&ctx);
    uint64_t remaining = size, total = 0;

    for (const ByteRange& r : ranges) {
        if (remaining == 0) break;
        Dev* d = &devs[r.device];
        if (d->fd < 0) d = &openDev(r.device, true);
        if (d->fd < 0) { ALOGE("Cannot open %s for read-back", r.device.c_str()); closeAll(); return ""; }
        uint64_t left = r.length < remaining ? r.length : remaining;
        uint64_t pos = r.offset;
        while (left > 0) {
            size_t need = left < BUF_SIZE ? (size_t)left : BUF_SIZE;
            size_t req = need;
            if (d->direct) {
                req = (need + DIO_ALIGN - 1) & ~(DIO_ALIGN - 1);
                if (req > BUF_SIZE) req = BUF_SIZE;
            }
            ssize_t n = pread(d->fd, ab.p, req, (off_t)pos);
            if (n < 0 && errno == EINTR) continue;
            if (n < 0 && errno == EINVAL && d->direct) {   // unaligned for O_DIRECT: read buffered
                d = &openDev(r.device, false);
                if (d->fd < 0) { closeAll(); return ""; }
                continue;
            }
            if (n < (ssize_t)need) {
                ALOGE("Read-back of %s failed at offset %llu: %s", r.device.c_str(), (unsigned long long)pos,
                      n < 0 ? strerror(errno) : "short read");
                closeAll();
                return "";
            }
            SHA256_Update(&ctx, ab.p, need);
            if (!d->direct) posix_fadvise(d->fd, (off_t)pos, (off_t)need, POSIX_FADV_DONTNEED);
            pos += need;
            left -= need;
            remaining -= need;
            total += need;
            if (progress) progress(total, size);
        }
    }
    closeAll();
    if (remaining != 0) {
        ALOGE("Read-back ranges cover %llu of %llu bytes", (unsigned long long)total, (unsigned long long)size);
        return "";
    }
    return hexDigest(&ctx);
}

} // namespace android
