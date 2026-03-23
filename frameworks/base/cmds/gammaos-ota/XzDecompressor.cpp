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
#include <unistd.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include <openssl/sha.h>
#include <utils/Log.h>

namespace android {

static const size_t BUF_SIZE = 1024 * 1024; // 1MB chunks

bool XzDecompressor::decompressToBlock(const std::string& xzPath,
                                       const std::string& blockDevPath,
                                       uint64_t /*expectedSize*/,
                                       ProgressCallback /*progress*/) {
    // Use xz binary to decompress — avoids linking against xz-utils library.
    // The xz binary is staged to tmpfs alongside gammaos-ota.
    // Pipeline: xz -d -c < input.xz > /dev/block/dm-N

    int outFd = open(blockDevPath.c_str(), O_WRONLY);
    if (outFd < 0) {
        ALOGE("Failed to open %s for writing: %s", blockDevPath.c_str(), strerror(errno));
        return false;
    }

    int inFd = open(xzPath.c_str(), O_RDONLY);
    if (inFd < 0) {
        ALOGE("Failed to open %s: %s", xzPath.c_str(), strerror(errno));
        close(outFd);
        return false;
    }

    // Fork and exec xz to decompress
    int pipefd[2];
    if (pipe(pipefd) < 0) {
        ALOGE("pipe() failed: %s", strerror(errno));
        close(inFd);
        close(outFd);
        return false;
    }

    pid_t pid = fork();
    if (pid < 0) {
        ALOGE("fork() failed: %s", strerror(errno));
        close(inFd);
        close(outFd);
        close(pipefd[0]);
        close(pipefd[1]);
        return false;
    }

    if (pid == 0) {
        // Child: xz -d reads from inFd, writes decompressed to pipefd[1]
        close(pipefd[0]); // close read end
        dup2(inFd, STDIN_FILENO);
        dup2(pipefd[1], STDOUT_FILENO);
        close(inFd);
        close(pipefd[1]);
        close(outFd);

        // Try staged xz first, then system xz
        const char* xzPaths[] = {
            "/dev/gammaos-ota-stage/bin/xz",
            "/system/bin/xz",
            nullptr
        };
        for (const char** p = xzPaths; *p; p++) {
            execl(*p, "xz", "-d", "-c", nullptr);
        }
        _exit(127);
    }

    // Parent: read decompressed data from pipefd[0], write to block device
    close(pipefd[1]); // close write end
    close(inFd);

    uint8_t* buf = new uint8_t[BUF_SIZE];
    uint64_t totalWritten = 0;
    bool success = true;

    while (true) {
        ssize_t nread = read(pipefd[0], buf, BUF_SIZE);
        if (nread < 0) {
            if (errno == EINTR) continue;
            ALOGE("Read from xz pipe failed: %s", strerror(errno));
            success = false;
            break;
        }
        if (nread == 0) break; // EOF

        size_t written = 0;
        while (written < (size_t)nread) {
            ssize_t w = write(outFd, buf + written, (size_t)nread - written);
            if (w < 0) {
                if (errno == EINTR) continue;
                ALOGE("Write error at offset %llu: %s",
                      (unsigned long long)(totalWritten + written), strerror(errno));
                success = false;
                break;
            }
            written += (size_t)w;
        }
        if (!success) break;
        totalWritten += (uint64_t)nread;
    }

    close(pipefd[0]);
    delete[] buf;

    // Wait for xz process
    int status;
    waitpid(pid, &status, 0);
    if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        ALOGE("xz process failed with status %d", status);
        success = false;
    }

    fsync(outFd);
    close(outFd);

    if (success) {
        ALOGI("Decompressed %s -> %s: %llu bytes",
              xzPath.c_str(), blockDevPath.c_str(), (unsigned long long)totalWritten);
    }
    return success;
}

bool XzDecompressor::decompressToFile(const std::string& xzPath,
                                       const std::string& outPath,
                                       uint64_t expectedSize,
                                       ProgressCallback progress) {
    // Decompress xz file to a regular file on /data
    // This is safer than piping directly to a block device — if decompression
    // fails, the target partition is untouched.

    int inFd = open(xzPath.c_str(), O_RDONLY);
    if (inFd < 0) {
        ALOGE("Failed to open %s: %s", xzPath.c_str(), strerror(errno));
        return false;
    }

    int outFd = open(outPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (outFd < 0) {
        ALOGE("Failed to create %s: %s", outPath.c_str(), strerror(errno));
        close(inFd);
        return false;
    }

    pid_t pid = fork();
    if (pid < 0) {
        ALOGE("fork() failed: %s", strerror(errno));
        close(inFd);
        close(outFd);
        return false;
    }

    if (pid == 0) {
        // Child: xz -d reads from inFd, writes to outFd
        dup2(inFd, STDIN_FILENO);
        dup2(outFd, STDOUT_FILENO);
        close(inFd);
        close(outFd);

        const char* xzPaths[] = {
            "/dev/gammaos-ota-stage/bin/xz",
            "/system/bin/xz",
            nullptr
        };
        for (const char** p = xzPaths; *p; p++) {
            execl(*p, "xz", "-d", "-c", nullptr);
        }
        _exit(127);
    }

    // Parent: poll output file size for progress while xz runs
    close(inFd);
    close(outFd);

    int status;
    if (expectedSize > 0 && progress) {
        // Non-blocking wait with progress polling
        while (true) {
            pid_t ret = waitpid(pid, &status, WNOHANG);
            if (ret == pid) break;  // xz finished
            if (ret < 0) {
                ALOGE("waitpid failed: %s", strerror(errno));
                break;
            }
            // Poll output file size
            struct stat st;
            if (stat(outPath.c_str(), &st) == 0 && st.st_size > 0) {
                progress((uint64_t)st.st_size, expectedSize);
            }
            usleep(250000); // 250ms polling interval
        }
        // Final progress update
        struct stat st;
        if (stat(outPath.c_str(), &st) == 0) {
            progress((uint64_t)st.st_size, expectedSize);
        }
    } else {
        // Blocking wait (no progress reporting)
        waitpid(pid, &status, 0);
    }

    if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        ALOGE("xz decompression to file failed with status %d", status);
        unlink(outPath.c_str());
        return false;
    }

    struct stat st;
    stat(outPath.c_str(), &st);
    ALOGI("Decompressed %s -> %s: %lld bytes", xzPath.c_str(), outPath.c_str(), (long long)st.st_size);
    return true;
}

bool XzDecompressor::writeFileToBlock(const std::string& filePath,
                                       const std::string& blockDevPath,
                                       uint64_t size,
                                       ProgressCallback progress) {
    int inFd = open(filePath.c_str(), O_RDONLY);
    if (inFd < 0) {
        ALOGE("Failed to open %s: %s", filePath.c_str(), strerror(errno));
        return false;
    }

    int outFd = open(blockDevPath.c_str(), O_WRONLY);
    if (outFd < 0) {
        ALOGE("Failed to open %s for writing: %s", blockDevPath.c_str(), strerror(errno));
        close(inFd);
        return false;
    }

    uint8_t* buf = new uint8_t[BUF_SIZE];
    uint64_t totalWritten = 0;
    uint64_t lastFlushOffset = 0;
    static const uint64_t FLUSH_INTERVAL = 256 * 1024 * 1024; // 256MB
    bool success = true;

    while (totalWritten < size) {
        size_t toRead = ((size - totalWritten) < BUF_SIZE) ? (size_t)(size - totalWritten) : BUF_SIZE;
        ssize_t nread = read(inFd, buf, toRead);
        if (nread <= 0) {
            if (nread < 0 && errno == EINTR) continue;
            ALOGE("Read error at offset %llu: %s", (unsigned long long)totalWritten, strerror(errno));
            success = false;
            break;
        }

        size_t written = 0;
        while (written < (size_t)nread) {
            ssize_t w = write(outFd, buf + written, (size_t)nread - written);
            if (w < 0) {
                if (errno == EINTR) continue;
                ALOGE("Write error at offset %llu: %s",
                      (unsigned long long)(totalWritten + written), strerror(errno));
                success = false;
                break;
            }
            written += (size_t)w;
        }
        if (!success) break;
        totalWritten += (uint64_t)nread;

        // Periodic flush: sync + drop caches every 256MB to prevent
        // page cache accumulation on low-RAM devices
        if (totalWritten - lastFlushOffset >= FLUSH_INTERVAL) {
            fsync(outFd);
            sync();
            // Drop page caches to free memory
            int cacheFd = open("/proc/sys/vm/drop_caches", O_WRONLY);
            if (cacheFd >= 0) {
                write(cacheFd, "3", 1);
                close(cacheFd);
            }
            lastFlushOffset = totalWritten;
        }

        // Report progress
        if (progress) {
            progress(totalWritten, size);
        }
    }

    close(inFd);
    fsync(outFd);
    close(outFd);
    delete[] buf;

    if (success) {
        ALOGI("Wrote %s -> %s: %llu bytes", filePath.c_str(), blockDevPath.c_str(),
              (unsigned long long)totalWritten);
    }
    return success;
}

std::string XzDecompressor::sha256File(const std::string& path) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return "";

    SHA256_CTX ctx;
    SHA256_Init(&ctx);

    uint8_t buf[BUF_SIZE];
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0) {
        SHA256_Update(&ctx, buf, (size_t)n);
    }
    close(fd);

    uint8_t hash[SHA256_DIGEST_LENGTH];
    SHA256_Final(hash, &ctx);

    char hex[SHA256_DIGEST_LENGTH * 2 + 1];
    for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        snprintf(hex + i * 2, 3, "%02x", hash[i]);
    }
    return std::string(hex);
}

std::string XzDecompressor::sha256BlockDev(const std::string& blockDevPath, uint64_t size) {
    // Use O_DIRECT to bypass page cache — critical after writing a different image
    // to a mounted block device, as stale cached pages cause SHA-256 mismatches
    int fd = open(blockDevPath.c_str(), O_RDONLY | O_DIRECT);
    if (fd < 0) {
        // O_DIRECT may not be supported on all block devices — fall back
        fd = open(blockDevPath.c_str(), O_RDONLY);
    }
    if (fd < 0) return "";

    SHA256_CTX ctx;
    SHA256_Init(&ctx);

    // Use aligned buffer for O_DIRECT compatibility (512-byte alignment)
    void* aligned_buf = nullptr;
    posix_memalign(&aligned_buf, 4096, BUF_SIZE);
    uint8_t* buf = static_cast<uint8_t*>(aligned_buf);

    uint64_t remaining = size;
    uint64_t totalRead = 0;
    uint64_t lastFlushOffset = 0;
    static const uint64_t VERIFY_FLUSH_INTERVAL = 256 * 1024 * 1024; // 256MB
    while (remaining > 0) {
        // O_DIRECT requires reads aligned to block size
        size_t toRead = BUF_SIZE;
        if (remaining < BUF_SIZE) {
            // Last chunk: read full block, but only hash 'remaining' bytes
            toRead = (remaining + 4095) & ~4095ULL; // round up to 4K
            if (toRead > BUF_SIZE) toRead = BUF_SIZE;
        }
        ssize_t n = read(fd, buf, toRead);
        if (n <= 0) break;
        size_t hashBytes = ((uint64_t)n > remaining) ? (size_t)remaining : (size_t)n;
        SHA256_Update(&ctx, buf, hashBytes);
        remaining -= hashBytes;
        totalRead += hashBytes;

        // Periodic cache drop every 256MB to prevent OOM on low-RAM devices
        if (totalRead - lastFlushOffset >= VERIFY_FLUSH_INTERVAL) {
            int cacheFd = open("/proc/sys/vm/drop_caches", O_WRONLY);
            if (cacheFd >= 0) {
                write(cacheFd, "3", 1);
                close(cacheFd);
            }
            lastFlushOffset = totalRead;
        }
    }
    close(fd);
    free(aligned_buf);

    uint8_t hash[SHA256_DIGEST_LENGTH];
    SHA256_Final(hash, &ctx);

    char hex[SHA256_DIGEST_LENGTH * 2 + 1];
    for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        snprintf(hex + i * 2, 3, "%02x", hash[i]);
    }
    return std::string(hex);
}

} // namespace android
