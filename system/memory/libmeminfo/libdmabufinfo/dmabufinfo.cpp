/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <dirent.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <filesystem>
#include <fstream>
#include <memory>
#include <string>
#include <vector>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <procinfo/process_map.h>

#include <dmabufinfo/dmabuf_sysfs_stats.h>
#include <dmabufinfo/dmabufinfo.h>

namespace android {
namespace dmabufinfo {

static bool FileIsDmaBuf(const std::string& path) {
    return ::android::base::StartsWith(path, "/dmabuf");
}

enum FdInfoResult {
    OK,
    NOT_FOUND,
    ERROR,
};

static FdInfoResult ReadDmaBufFdInfo(pid_t pid, int fd, std::string* name, std::string* exporter,
                             uint64_t* count, uint64_t* size, uint64_t* inode, bool* is_dmabuf_file,
                             const std::string& procfs_path) {
    std::string fdinfo =
            ::android::base::StringPrintf("%s/%d/fdinfo/%d", procfs_path.c_str(), pid, fd);
    std::ifstream fp(fdinfo);
    if (!fp) {
        if (errno == ENOENT) {
            return NOT_FOUND;
        }
        PLOG(ERROR) << "Failed to open " << fdinfo;
        return ERROR;
    }

    for (std::string file_line; getline(fp, file_line);) {
        const char* line = file_line.c_str();
        switch (line[0]) {
            case 'c':
                if (strncmp(line, "count:", 6) == 0) {
                    const char* c = line + 6;
                    *count = strtoull(c, nullptr, 10);
                }
                break;
            case 'e':
                if (strncmp(line, "exp_name:", 9) == 0) {
                    const char* c = line + 9;
                    *exporter = ::android::base::Trim(c);
                    *is_dmabuf_file = true;
                }
                break;
            case 'n':
                if (strncmp(line, "name:", 5) == 0) {
                    const char* c = line + 5;
                    *name = ::android::base::Trim(c);
                }
                break;
            case 's':
                if (strncmp(line, "size:", 5) == 0) {
                    const char* c = line + 5;
                    *size = strtoull(c, nullptr, 10);
                }
                break;
            case 'i':
                if (strncmp(line, "ino:", 4) == 0) {
                    const char* c = line + 4;
                    *inode = strtoull(c, nullptr, 10);
                }
                break;
        }
    }

    return OK;
}

// Public methods
bool ReadDmaBufFdRefs(int pid, std::vector<DmaBuffer>* dmabufs,
                             const std::string& procfs_path) {
    constexpr char permission_err_msg[] =
            "Failed to read fdinfo - requires either PTRACE_MODE_READ or root depending on "
            "the device kernel";
    static bool logged_permission_err = false;

    std::string fdinfo_dir_path =
            ::android::base::StringPrintf("%s/%d/fdinfo", procfs_path.c_str(), pid);
    std::unique_ptr<DIR, decltype(&closedir)> dir(opendir(fdinfo_dir_path.c_str()), &closedir);
    if (!dir) {
        // Don't log permission errors to reduce log spam on devices where fdinfo
        // of other processes can only be read by root.
        if (errno != EACCES) {
            PLOG(ERROR) << "Failed to open " << fdinfo_dir_path << " directory";
        } else if (!logged_permission_err) {
            LOG(ERROR) << permission_err_msg;
            logged_permission_err = true;
        }
        return false;
    }
    struct dirent* dent;
    while ((dent = readdir(dir.get()))) {
        int fd;
        if (!::android::base::ParseInt(dent->d_name, &fd)) {
            continue;
        }

        // Set defaults in case the kernel doesn't give us the information
        // we need in fdinfo
        std::string name = "<unknown>";
        std::string exporter = "<unknown>";
        uint64_t count = 0;
        uint64_t size = 0;
        uint64_t inode = -1;
        bool is_dmabuf_file = false;

        auto fdinfo_result = ReadDmaBufFdInfo(pid, fd, &name, &exporter, &count, &size, &inode,
                                              &is_dmabuf_file, procfs_path);
        if (fdinfo_result != OK) {
            if (fdinfo_result == NOT_FOUND) {
                continue;
            }
            // Don't log permission errors to reduce log spam when the process doesn't
            // have the PTRACE_MODE_READ permission.
            if (errno != EACCES) {
                LOG(ERROR) << "Failed to read fd info for pid: " << pid << ", fd: " << fd;
            } else if (!logged_permission_err) {
                LOG(ERROR) << permission_err_msg;
                logged_permission_err = true;
            }
            return false;
        }
        if (!is_dmabuf_file) {
            continue;
        }
        if (inode == static_cast<uint64_t>(-1)) {
            // Fallback to stat() on the fd path to get inode number
            std::string fd_path =
                    ::android::base::StringPrintf("%s/%d/fd/%d", procfs_path.c_str(), pid, fd);

            struct stat sb;
            if (stat(fd_path.c_str(), &sb) < 0) {
                if (errno == ENOENT) {
                  continue;
                }
                PLOG(ERROR) << "Failed to stat: " << fd_path;
                return false;
            }

            inode = sb.st_ino;
            // If root, calculate size from the allocated blocks.
            size = sb.st_blocks * 512;
        }

        auto buf = std::find_if(dmabufs->begin(), dmabufs->end(),
                                [&inode](const DmaBuffer& dbuf) { return dbuf.inode() == inode; });
        if (buf != dmabufs->end()) {
            if (buf->name() == "" || buf->name() == "<unknown>") buf->SetName(name);
            if (buf->exporter() == "" || buf->exporter() == "<unknown>") buf->SetExporter(exporter);
            if (buf->count() == 0) buf->SetCount(count);
            buf->AddFdRef(pid);
            continue;
        }

        DmaBuffer& db = dmabufs->emplace_back(inode, size, count, exporter, name);
        db.AddFdRef(pid);
    }

    return true;
}

bool ReadDmaBufMapRefs(pid_t pid, std::vector<DmaBuffer>* dmabufs,
                              const std::string& procfs_path,
                              const std::string& dmabuf_sysfs_path) {
    std::string mapspath = ::android::base::StringPrintf("%s/%d/maps", procfs_path.c_str(), pid);
    std::ifstream fp(mapspath);
    if (!fp) {
        LOG(ERROR) << "Failed to open " << mapspath << " for pid: " << pid;
        return false;
    }

    // Process the map if it is dmabuf. Add map reference to existing object in 'dmabufs'
    // if it was already found. If it wasn't create a new one and append it to 'dmabufs'
    auto account_dmabuf = [&](const android::procinfo::MapInfo& mapinfo) {
        // no need to look into this mapping if it is not dmabuf
        if (!FileIsDmaBuf(mapinfo.name)) {
            return;
        }

        auto buf = std::find_if(
                dmabufs->begin(), dmabufs->end(),
                [&mapinfo](const DmaBuffer& dbuf) { return dbuf.inode() == mapinfo.inode; });
        if (buf != dmabufs->end()) {
            buf->AddMapRef(pid);
            return;
        }

        // We have a new buffer, but unknown count and name and exporter name
        // Try to lookup exporter name in sysfs
        std::string exporter;
        bool sysfs_stats = ReadBufferExporter(mapinfo.inode, &exporter, dmabuf_sysfs_path);
        if (!sysfs_stats) {
            exporter = "<unknown>";
        }

        // Using the VMA range as the size of the buffer can be misleading,
        // due to partially mapped buffers or VMAs that extend beyond the
        // buffer size.
        //
        // Attempt to retrieve the real buffer size from sysfs.
        uint64_t size = 0;
        if (!sysfs_stats || !ReadBufferSize(mapinfo.inode, &size, dmabuf_sysfs_path)) {
            size = mapinfo.end - mapinfo.start;
        }

        DmaBuffer& dbuf = dmabufs->emplace_back(mapinfo.inode, size, 0, exporter, "<unknown>");
        dbuf.AddMapRef(pid);
    };

    for (std::string line; getline(fp, line);) {
        if (!::android::procinfo::ReadMapFileContent(line.data(), account_dmabuf)) {
            LOG(ERROR) << "Failed to parse " << mapspath << " for pid: " << pid;
            return false;
        }
    }

    return true;
}

bool ReadDmaBufInfo(pid_t pid, std::vector<DmaBuffer>* dmabufs, bool read_fdrefs,
                    const std::string& procfs_path, const std::string& dmabuf_sysfs_path) {
    dmabufs->clear();

    if (read_fdrefs) {
        if (!ReadDmaBufFdRefs(pid, dmabufs, procfs_path)) {
            LOG(ERROR) << "Failed to read dmabuf fd references";
            return false;
        }
    }

    if (!ReadDmaBufMapRefs(pid, dmabufs, procfs_path, dmabuf_sysfs_path)) {
        LOG(ERROR) << "Failed to read dmabuf map references";
        return false;
    }
    return true;
}

bool ReadProcfsDmaBufs(std::vector<DmaBuffer>* bufs) {
    bufs->clear();

    std::unique_ptr<DIR, int (*)(DIR*)> dir(opendir("/proc"), closedir);
    if (!dir) {
        LOG(ERROR) << "Failed to open /proc directory";
        bufs->clear();
        return false;
    }

    struct dirent* dent;
    while ((dent = readdir(dir.get()))) {
        if (dent->d_type != DT_DIR) continue;

        int pid = atoi(dent->d_name);
        if (pid == 0) {
            continue;
        }

        if (!ReadDmaBufFdRefs(pid, bufs)) {
            LOG(ERROR) << "Failed to read dmabuf fd references for pid " << pid;
        }

        if (!ReadDmaBufMapRefs(pid, bufs)) {
            LOG(ERROR) << "Failed to read dmabuf map references for pid " << pid;
        }
    }

    return true;
}

}  // namespace dmabufinfo
}  // namespace android
