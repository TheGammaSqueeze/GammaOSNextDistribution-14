// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "icing/file/filesystem.h"

#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <fnmatch.h>
#include <pthread.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <unordered_set>

#include "icing/absl_ports/str_cat.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/logging.h"

using std::vector;

namespace icing {
namespace lib {

namespace {

// The size of the block for st_blksize returned by stat() and as a
// consequence also the granularity of GetDiskUsage(). It seems that there is
// no appropriate constant for this. See http://linux.die.net/man/2/stat
constexpr int kStatBlockSize = 512;

// Logs information about open file descriptors.
//
// This function uses getrlimit() to find the maximum number of file
// descriptors, then calls readlink("/proc/self/fd/N") for each possible file
// descriptor number to get a description of the open file from procfs.
//
// We don't use readdir() to list the contents of /proc/self/fd (which would be
// the more obvious approach) because that would require a free file descriptor
// to open the directory, while we call this function when all file descriptors
// are in use.
void LogOpenFileDescriptors() {
  // Determine the limit on file descriptor numbers. RLIMIT_NOFILE should return
  // the maximum file descriptor + 1, which is 1024 on Android by default. We
  // restrict the limit to 4096 so we don't take too much time if the value
  // turns out to be much higher for some reason.
  constexpr int kMaxFileDescriptorsToStat = 4096;
  struct rlimit rlim = {0, 0};
  if (getrlimit(RLIMIT_NOFILE, &rlim) != 0) {
    ICING_LOG(ERROR) << "getrlimit() failed (errno=" << errno << ")";
    return;
  }
  int fd_lim = rlim.rlim_cur;
  if (fd_lim > kMaxFileDescriptorsToStat) {
    ICING_LOG(ERROR) << "Maximum number of file descriptors (" << fd_lim
        << ") too large.";
    fd_lim = kMaxFileDescriptorsToStat;
  }
  ICING_LOG(INFO) << "Listing up to " << fd_lim << " file descriptors.";

  // Verify that /proc/self/fd is a directory. If not, procfs is not mounted or
  // inaccessible for some other reason. In that case, there's no point trying
  // to read from it.
  struct stat statbuf;
  if (stat("/proc/self/fd", &statbuf) != 0 || !S_ISDIR(statbuf.st_mode)) {
    ICING_LOG(ERROR) << "/proc/self/fd not available. Giving up.";
    return;
  }

  // Now read each link individually.
  const int path_size = 1024;
  char path[path_size];
  const int target_size = 1024;
  char target[target_size];
  for (int fd = 0; fd < fd_lim; ++fd) {
    snprintf(path, path_size, "/proc/self/fd/%d", fd);
    ssize_t len = readlink(path, target, target_size);
    if (len >= 0) {
      // Zero-terminate the buffer, because readlink() won't.
      target[len < target_size ? len : target_size - 1] = '\0';
      ICING_LOG(INFO) << "fd " << fd << " -> \"" << target << "\"";
    } else if (errno != ENOENT) {
      ICING_LOG(ERROR) << "fd " << fd << " -> ? (errno=" << errno << ")";
    }
  }
  ICING_LOG(INFO) << "File descriptor list complete.";
}

// Logs an error formatted as: desc1 + file_name + desc2 + strerror(errnum).
//
// If errnum == EMFILE (too many open files), then it also logs a list of open
// file descriptors (see LogOpenFileDescriptors() above).
void LogOpenError(const char* desc1, const char* file_name, const char* desc2,
                  int errnum) {
  if (errnum == ENOENT) {
    ICING_VLOG(1) << desc1 << file_name << desc2 << strerror(errnum);
  } else {
    ICING_LOG(ERROR) << desc1 << file_name << desc2 << strerror(errnum);  
  }
  if (errnum == EMFILE) {
    LogOpenFileDescriptors();
  }
}

// Recursive implementation of ListDirectory. Prefix is used to prepend the
// directory name during recursion.
// We cannot use scandir due to a bug in old platform versions. See b/7339844.
bool ListDirectoryInternal(const char* dir_name,
                           const std::unordered_set<std::string>& exclude,
                           bool recursive, const char* prefix,
                           std::vector<std::string>* entries) {
  DIR* dir = opendir(dir_name);
  if (!dir) {
    LogOpenError("Unable to open directory ", dir_name, ": ", errno);
    return false;
  }

  // According to linux man page
  // (https://man7.org/linux/man-pages/man3/readdir.3.html#RETURN_VALUE), dirent
  // may be statically allocated, so don't free it.
  dirent* p;
  // readdir's implementation seems to be thread safe.
  while ((p = readdir(dir)) != nullptr) {
    std::string file_name(p->d_name);
    if (file_name == "." || file_name == ".." ||
        exclude.find(file_name) != exclude.end()) {
      continue;
    }
    std::string relative_path = absl_ports::StrCat(prefix, p->d_name);
    entries->push_back(relative_path);
    // Recurse down directories, if requested.
    if (recursive && (p->d_type == DT_DIR)) {
      std::string sub_dir_name = absl_ports::StrCat(dir_name, "/", p->d_name);
      std::string relative_path_with_slash =
          absl_ports::StrCat(relative_path, "/");
      if (!ListDirectoryInternal(sub_dir_name.c_str(), exclude, recursive,
                                 relative_path_with_slash.c_str(), entries)) {
        return false;
      }
    }
  }
  if (closedir(dir) != 0) {
    ICING_LOG(ERROR) << "Error closing " << dir_name << " " << strerror(errno);
  }
  return true;
}

}  // namespace

ScopedFd::~ScopedFd() {
  if (fd_ >= 0) {
    close(fd_);
  }
}

void ScopedFd::reset(int fd) {
  if (fd_ >= 0) {
    close(fd_);
  }
  fd_ = fd;
}

const int64_t Filesystem::kBadFileSize;

bool Filesystem::DeleteFile(const char* file_name) const {
  ICING_VLOG(1) << "Deleting file " << file_name;
  int ret = unlink(file_name);
  if (ret != 0 && errno != ENOENT) {
    ICING_LOG(ERROR) << "Deleting file " << file_name << " failed: " << strerror(errno);
    return false;
  }
  return true;
}

bool Filesystem::DeleteDirectory(const char* dir_name) const {
  int ret = rmdir(dir_name);
  if (ret != 0 && errno != ENOENT) {
    ICING_LOG(ERROR) << "Deleting directory " << dir_name << " failed: " << strerror(errno);
    return false;
  }
  return true;
}

bool Filesystem::DeleteDirectoryRecursively(const char* dir_name) const {
  // Ensure the dir_name really is a directory and exists.
  struct stat st;
  if (stat(dir_name, &st) < 0) {
    if (errno == ENOENT) {
      return true;  // If directory didn't exist, this was successful.
    }
    ICING_LOG(ERROR) << "Stat " << dir_name << " failed: " << strerror(errno);
    return false;
  }
  vector<std::string> entries;
  if (!ListDirectory(dir_name, &entries)) {
    return false;
  }

  bool success = true;
  for (vector<std::string>::iterator i = entries.begin(); i != entries.end();
       ++i) {
    std::string filename = std::string(dir_name) + '/' + *i;
    if (stat(filename.c_str(), &st) < 0) {
      ICING_LOG(ERROR) << "Stat " << filename << " failed: " << strerror(errno);
      success = false;
    } else if (S_ISDIR(st.st_mode)) {
      success = DeleteDirectoryRecursively(filename.c_str()) && success;
    } else {
      success = DeleteFile(filename.c_str()) && success;
    }
  }

  if (success) {
    success = DeleteDirectory(dir_name);
  }

  return success;
}

bool Filesystem::FileExists(const char* file_name) const {
  bool exists = false;
  struct stat st;
  if (stat(file_name, &st) == 0) {
    exists = S_ISREG(st.st_mode) != 0;
  } else {
    if (errno != ENOENT) {
      ICING_LOG(ERROR) << "Unable to stat file " << file_name << ": " << strerror(errno);
    }
    exists = false;
  }
  return exists;
}

bool Filesystem::DirectoryExists(const char* dir_name) const {
  bool exists = false;
  struct stat st;
  if (stat(dir_name, &st) == 0) {
    exists = S_ISDIR(st.st_mode) != 0;
  } else {
    if (errno != ENOENT) {
      ICING_LOG(ERROR) << "Unable to stat directory " << dir_name << ": " << strerror(errno);
    }
    exists = false;
  }
  return exists;
}

int Filesystem::GetBasenameIndex(const char* file_name) const {
  // Find final slash.
  const char* last_slash = strrchr(file_name, '/');
  if (!last_slash) {
    // file_name is just basename.
    return 0;
  }

  // Skip slash.
  return last_slash + 1 - file_name;
}

std::string Filesystem::GetBasename(const char* file_name) const {
  size_t len = strlen(file_name);
  int idx = GetBasenameIndex(file_name);
  return std::string(file_name + idx, len - idx);
}

std::string Filesystem::GetDirname(const char* file_name) const {
  int idx = GetBasenameIndex(file_name);
  // Remove the trailing slash
  if (idx > 0) {
    idx -= 1;
  }
  return std::string(file_name, idx);
}

bool Filesystem::ListDirectory(const char* dir_name,
                               vector<std::string>* entries) const {
  entries->clear();
  return ListDirectory(dir_name, /*exclude=*/{}, /*recursive=*/false, entries);
}

bool Filesystem::ListDirectory(const char* dir_name,
                               const std::unordered_set<std::string>& exclude,
                               bool recursive,
                               std::vector<std::string>* entries) const {
  return ListDirectoryInternal(dir_name, exclude, recursive, /*prefix=*/"",
                               entries);
}

bool Filesystem::GetMatchingFiles(const char* glob,
                                  vector<std::string>* matches) const {
  matches->clear();

  // Split dirname/basename.
  int basename_idx = GetBasenameIndex(glob);
  if (basename_idx == 0) {
    // We need a directory.
    ICING_VLOG(1) << "Expected directory, no matching files for: " << glob;
    return true;
  }
  const char* basename_glob = glob + basename_idx;
  std::string dirname(glob, basename_idx);
  vector<std::string> entries;
  if (!ListDirectory(dirname.c_str(), &entries) && errno != ENOENT) {
    return false;
  }

  for (vector<std::string>::iterator i = entries.begin(); i != entries.end();
       ++i) {
    // The filename needs to match glob following last_slash.
    if (!fnmatch(basename_glob, i->c_str(), FNM_PATHNAME)) {
      // Add it to the list.
      matches->push_back(dirname + *i);
    }
  }
  return true;
}

int Filesystem::OpenForWrite(const char* file_name) const {
  int fd = open(file_name, O_RDWR | O_CREAT, S_IRUSR | S_IWUSR);
  if (fd < 0) {
    LogOpenError("Opening file ", file_name, " for write failed: ", errno);
  }
  return fd;
}

int Filesystem::OpenForAppend(const char* file_name) const {
  // Don't use the O_APPEND flag because, although it opens for
  // append, it doesn't set the file cursor to at the end until
  // first write occurs.  This can be confusing if you expect
  // the file position at the end.  Instead, explicitly
  // seek to end after opening.
  int fd = open(file_name, O_RDWR | O_CREAT, S_IRUSR | S_IWUSR);
  if (fd < 0) {
    LogOpenError("Opening file ", file_name, " for write failed: ", errno);
  } else {
    lseek(fd, 0, SEEK_END);
  }
  return fd;
}

int Filesystem::OpenForRead(const char* file_name) const {
  int fd = open(file_name, O_RDONLY);
  if (fd < 0) {
    LogOpenError("Opening file ", file_name, " for read failed: ", errno);
  }
  return fd;
}

int64_t Filesystem::GetFileSize(int fd) const {
  struct stat st;
  if (fstat(fd, &st) < 0) {
    if (errno == ENOENT) {
      ICING_VLOG(1) << "Unable to stat file: " << strerror(errno);
    } else {
      ICING_LOG(WARNING) << "Unable to stat file: " << strerror(errno);
    }
    return kBadFileSize;
  }
  return st.st_size;
}

int64_t Filesystem::GetFileSize(const char* filename) const {
  struct stat st;
  if (stat(filename, &st) < 0) {
    if (errno == ENOENT) {
      ICING_VLOG(1) << "Unable to stat file " << filename << ": " << strerror(errno);
    } else {
      ICING_LOG(WARNING) << "Unable to stat file " << filename << ": " << strerror(errno);
    }
    return kBadFileSize;
  }
  return st.st_size;
}

bool Filesystem::Truncate(int fd, int64_t new_size) const {
  if (ftruncate(fd, new_size) != 0) {
    ICING_LOG(ERROR) << "Unable to truncate file: " << strerror(errno);
    return false;
  }
  lseek(fd, new_size, SEEK_SET);
  return true;
}

bool Filesystem::Truncate(const char* filename, int64_t new_size) const {
  int fd = OpenForAppend(filename);
  if (fd == -1) {
    return false;
  }
  bool success = Truncate(fd, new_size);
  close(fd);
  return success;
}

bool Filesystem::Grow(int fd, int64_t new_size) const {
  if (ftruncate(fd, new_size) != 0) {
    ICING_LOG(ERROR) << "Unable to grow file: " << strerror(errno);
    return false;
  }

  return true;
}

bool Filesystem::Grow(const char* filename, int64_t new_size) const {
  int fd = OpenForAppend(filename);
  if (fd == -1) {
    return false;
  }

  bool grew = Grow(fd, new_size);
  close(fd);
  return grew;
}

bool Filesystem::Write(int fd, const void* data, size_t data_size) const {
  size_t write_len = data_size;
  do {
    // Don't try to write too much at once.
    size_t chunk_size = std::min<size_t>(write_len, 64u * 1024);
    ssize_t wrote = write(fd, data, chunk_size);
    if (wrote < 0) {
      ICING_LOG(ERROR) << "Bad write: " << strerror(errno);
      return false;
    }
    data = static_cast<const uint8_t*>(data) + wrote;
    write_len -= wrote;
  } while (write_len > 0);
  return true;
}

bool Filesystem::Write(const char* filename, const void* data,
                       size_t data_size) const {
  int fd = OpenForWrite(filename);
  if (fd == -1) {
    return false;
  }

  bool success = Write(fd, data, data_size);
  close(fd);
  return success;
}

bool Filesystem::CopyFile(const char* src, const char* dst) const {
  ScopedFd src_fd(OpenForRead(src));

  std::string dir = GetDirname(dst);
  if (!CreateDirectoryRecursively(dir.c_str())) {
    return false;
  }
  ScopedFd dst_fd(OpenForWrite(dst));

  if (!src_fd.is_valid() || !dst_fd.is_valid()) {
    return false;
  }
  uint64_t size = GetFileSize(*src_fd);
  std::unique_ptr<uint8_t[]> buf = std::make_unique<uint8_t[]>(size);
  if (!Read(*src_fd, buf.get(), size)) {
    return false;
  }
  return Write(*dst_fd, buf.get(), size);
}

bool Filesystem::CopyDirectory(const char* src_dir, const char* dst_dir,
                               bool recursive) const {
  DIR* dir = opendir(src_dir);
  if (!dir) {
    LogOpenError("Unable to open directory ", src_dir, ": ", errno);
    return false;
  }

  dirent* p;
  // readdir's implementation seems to be thread safe.
  while ((p = readdir(dir)) != nullptr) {
    std::string file_name(p->d_name);
    if (file_name == "." || file_name == "..") {
      continue;
    }

    std::string full_src_path = absl_ports::StrCat(src_dir, "/", p->d_name);
    std::string full_dst_path = absl_ports::StrCat(dst_dir, "/", p->d_name);

    // Directories are copied when writing a non-directory file, so no
    // explicit copying of a directory is required.
    if (p->d_type != DT_DIR) {
      if (!CopyFile(full_src_path.c_str(), full_dst_path.c_str())) {
        return false;
      }
    }

    // Recurse down directories, if requested.
    if (recursive && (p->d_type == DT_DIR)) {
      std::string src_sub_dir = absl_ports::StrCat(src_dir, "/", p->d_name);
      std::string dst_sub_dir = absl_ports::StrCat(dst_dir, "/", p->d_name);
      if (!CopyDirectory(src_sub_dir.c_str(), dst_sub_dir.c_str(), recursive)) {
        return false;
      }
    }
  }
  if (closedir(dir) != 0) {
    ICING_LOG(ERROR) << "Error closing " << src_dir << ": " << strerror(errno);
  }
  return true;
}

bool Filesystem::PWrite(int fd, off_t offset, const void* data,
                        size_t data_size) const {
  size_t write_len = data_size;
  do {
    // Don't try to write too much at once.
    size_t chunk_size = std::min<size_t>(write_len, 64u * 1024);
    ssize_t wrote = pwrite(fd, data, chunk_size, offset);
    if (wrote < 0) {
      ICING_LOG(ERROR) << "Bad write: " << strerror(errno);
      return false;
    }
    data = static_cast<const uint8_t*>(data) + wrote;
    write_len -= wrote;
    offset += wrote;
  } while (write_len > 0);
  return true;
}

bool Filesystem::PWrite(const char* filename, off_t offset, const void* data,
                        size_t data_size) const {
  int fd = OpenForWrite(filename);
  if (fd == -1) {
    return false;
  }

  bool success = PWrite(fd, offset, data, data_size);
  close(fd);
  return success;
}

bool Filesystem::Read(int fd, void* buf, size_t buf_size) const {
  ssize_t read_status = read(fd, buf, buf_size);
  if (read_status < 0) {
    ICING_LOG(ERROR) << "Bad read: " << strerror(errno);
    return false;
  }
  return true;
}

bool Filesystem::Read(const char* filename, void* buf, size_t buf_size) const {
  int fd = OpenForRead(filename);
  if (fd == -1) {
    return false;
  }

  bool success = Read(fd, buf, buf_size);
  close(fd);
  return success;
}

bool Filesystem::PRead(int fd, void* buf, size_t buf_size, off_t offset) const {
  ssize_t read_status = pread(fd, buf, buf_size, offset);
  if (read_status < 0) {
    ICING_LOG(ERROR) << "Bad read: " << strerror(errno);
    return false;
  }
  return true;
}

bool Filesystem::PRead(const char* filename, void* buf, size_t buf_size,
                       off_t offset) const {
  int fd = OpenForRead(filename);
  if (fd == -1) {
    return false;
  }

  bool success = PRead(fd, buf, buf_size, offset);
  close(fd);
  return success;
}

bool Filesystem::DataSync(int fd) const {
#ifdef __APPLE__  // iOS has no fdatasync(), only fsync()
  int result = fsync(fd);
#else
  int result = fdatasync(fd);
#endif

  if (result < 0) {
    ICING_LOG(ERROR) << "Unable to sync data: " << strerror(errno);
    return false;
  }
  return true;
}

bool Filesystem::RenameFile(const char* old_name, const char* new_name) const {
  if (rename(old_name, new_name) < 0) {
    ICING_LOG(ERROR) << "Unable to rename file " << old_name << " to " << new_name << ": " << strerror(errno);
    return false;
  }
  return true;
}

bool Filesystem::SwapFiles(const char* one, const char* two) const {
  std::string tmp_name = absl_ports::StrCat(one, ".tmp");
  const char* tmp_cstr = tmp_name.c_str();

  // Blow away a tmp file if it already exists
  if (FileExists(tmp_cstr) && !DeleteFile(tmp_cstr)) {
    return false;
  }
  if (DirectoryExists(tmp_cstr) && !DeleteDirectoryRecursively(tmp_cstr)) {
    return false;
  }

  // Perform the swap
  if (!RenameFile(one, tmp_cstr)) {
    return false;
  }
  if (!RenameFile(two, one)) {
    return false;
  }
  if (!RenameFile(tmp_cstr, two)) {
    return false;
  }

  return true;
}

bool Filesystem::CreateDirectory(const char* dir_name) const {
  bool success = DirectoryExists(dir_name);
  if (!success) {
    if (mkdir(dir_name, S_IRUSR | S_IWUSR | S_IXUSR) == 0) {
      success = true;
    } else {
      ICING_LOG(ERROR) << "Creating directory " << dir_name << " failed: " << strerror(errno);
    }
  }
  return success;
}

bool Filesystem::CreateDirectoryRecursively(const char* dir_name) const {
  if ((strlen(dir_name) == 0) || DirectoryExists(dir_name)) {
    return true;
  }
  std::string path_before = GetDirname(dir_name);
  if (!CreateDirectoryRecursively(path_before.c_str())) {
    return false;
  }
  return CreateDirectory(dir_name);
}

int64_t Filesystem::GetDiskUsage(int fd) const {
  struct stat st;
  if (fstat(fd, &st) < 0) {
    ICING_LOG(ERROR) << "Unable to stat file: " << strerror(errno);
    return kBadFileSize;
  }
  return st.st_blocks * kStatBlockSize;
}

int64_t Filesystem::GetFileDiskUsage(const char* path) const {
  struct stat st;
  if (stat(path, &st) != 0) {
    ICING_LOG(ERROR) << "Unable to stat " << path << ": " << strerror(errno);
    return kBadFileSize;
  }
  return st.st_blocks * kStatBlockSize;
}

int64_t Filesystem::GetDiskUsage(const char* path) const {
  struct stat st;
  if (stat(path, &st) != 0) {
    ICING_LOG(ERROR) << "Unable to stat " << path << ": " << strerror(errno);
    return kBadFileSize;
  }
  int64_t result = st.st_blocks * kStatBlockSize;
  if (S_ISDIR(st.st_mode)) {
    vector<std::string> list;
    if (!ListDirectory(path, &list)) {
      return kBadFileSize;
    }
    for (vector<std::string>::iterator i = list.begin(); i != list.end(); ++i) {
      std::string sub_path = std::string(path) + '/' + *i;
      uint64_t sub_usage = GetDiskUsage(sub_path.c_str());
      if (sub_usage != kBadFileSize) {
        result += sub_usage;
      }  // Else just ignore the failing entry.
    }
  }
  return result;
}

int64_t Filesystem::GetCurrentPosition(int fd) const {
  return lseek(fd, 0, SEEK_CUR);
}

int64_t Filesystem::SetPosition(int fd, int offset) const {
  return lseek(fd, offset, SEEK_SET);
}

void Filesystem::IncrementByOrSetInvalid(int64_t size, int64_t* to_increment) {
  if (*to_increment == kBadFileSize) {
    return;
  }
  if (size == kBadFileSize) {
    *to_increment = kBadFileSize;
    return;
  }
  *to_increment += size;
}

}  // namespace lib
}  // namespace icing
