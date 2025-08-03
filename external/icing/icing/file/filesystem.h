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

// Methods for interacting with the filesystem.

#ifndef ICING_FILE_FILESYSTEM_H_
#define ICING_FILE_FILESYSTEM_H_

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <unordered_set>
#include <vector>

namespace icing {
namespace lib {

// Closes fd when it goes out of scope, if fd >= 0.
class ScopedFd {
 public:
  explicit ScopedFd(int fd = -1) : fd_(fd) {}
  ScopedFd(const ScopedFd&) = delete;
  ScopedFd(ScopedFd&& other) : ScopedFd() { *this = std::move(other); }

  ScopedFd& operator=(const ScopedFd&) = delete;
  ScopedFd& operator=(ScopedFd&& other) {
    std::swap(fd_, other.fd_);
    return *this;
  }
  ~ScopedFd();

  bool is_valid() const { return fd_ >= 0; }
  int operator*() const { return fd_; }
  int get() const { return fd_; }
  void reset(int fd = -1);

 private:
  int fd_;
};

struct FILEDeleter {
  void operator()(FILE* fp) const {
    if (fp) {
      fclose(fp);
    }
  }
};
typedef std::unique_ptr<FILE, FILEDeleter> ScopedFILE;

// Class containing file operation methods.
// LINT.IfChange
class Filesystem {
 public:
  static const int64_t kBadFileSize = std::numeric_limits<int64_t>::max();

  constexpr Filesystem() = default;
  virtual ~Filesystem() = default;

  // Deletes a file, returns true on success or if the file did
  // not yet exist.
  virtual bool DeleteFile(const char* file_name) const;

  // Deletes a directory, returns true on success or if the directory did
  // not yet exist.
  virtual bool DeleteDirectory(const char* dir_name) const;

  // Deletes a directory, including any contents, and returns true on
  // success or if the directory did not yet exist.
  virtual bool DeleteDirectoryRecursively(const char* dir_name) const;

  // Copies the src file to the dst file.
  virtual bool CopyFile(const char* src, const char* dst) const;

  // Copies the src directory and its contents to the dst dir.
  virtual bool CopyDirectory(const char* src_dir, const char* dst_dir,
                             bool recursive) const;

  // Returns true if a file exists.  False if the file doesn't exist.
  // If there is an error getting stat on the file, it logs the error and
  // asserts.
  virtual bool FileExists(const char* file_name) const;

  // Returns true if a directory exists.  False if the file doesn't exist.
  // If there is an error getting stat on the file, it logs the error and
  // asserts.
  virtual bool DirectoryExists(const char* dir_name) const;

  // Return index to start of basename in file_name. Anything before
  // basename is the dirname (including the final slash).
  virtual int GetBasenameIndex(const char* file_name) const;

  // Return a string containing the basename.
  virtual std::string GetBasename(const char* file_name) const;

  // Return a string containing the dirname.
  virtual std::string GetDirname(const char* file_name) const;

  // Gets the names of the entries of a given directory. Does not include "."
  // and "..". Returns false on error.
  virtual bool ListDirectory(const char* dir_name,
                             std::vector<std::string>* entries) const;

  // Adds the names of the entries of a given directory -- recursively if
  // specified, and excluding files/directories named in exclude -- to entries.
  // Regardless of exclude, does not include "." and "..".  Excluded files are
  // excluded at every level.  Returns false on error.
  //
  // Example use case: list all files & directories in fooDir/, recursively,
  // excluding anything named "tmp" or "cache" (presumed directories) and the
  // files within them.
  virtual bool ListDirectory(const char* dir_name,
                             const std::unordered_set<std::string>& exclude,
                             bool recursive,
                             std::vector<std::string>* entries) const;

  // Use glob to return matched files into "matches". Returns false if
  // glob had an error.
  //
  // Cannot match multiple directories so everything up the last slash
  // must be literal.
  virtual bool GetMatchingFiles(const char* glob,
                                std::vector<std::string>* matches) const;

  // Opens the file for read/write. Creates if not existing.  Returns
  // -1 on fail or an open file descriptor on success.
  virtual int OpenForWrite(const char* file_name) const;

  // Opens the file for read/write, and positions the file at the
  // end for appending.  Creates if not existing.  Returns -1 on fail
  // or an open file descriptor on success.
  virtual int OpenForAppend(const char* file_name) const;

  // Opens a file for read only.  Fails if file doesn't exist.  Returns
  // file descriptor or -1 on fail.  Set quiet to true to suppress
  // log warnings.
  virtual int OpenForRead(const char* file_name) const;

  // Gets the size of a file, given an open file descriptor.
  // Returns kBadFileSize on error.
  virtual int64_t GetFileSize(int fd) const;

  // Gets the size of a file, given a filename.
  virtual int64_t GetFileSize(const char* filename) const;

  // Truncates the file to the requested size.  Seeks to the
  // end position of the file after truncate.  Returns false
  // if fails.
  virtual bool Truncate(int fd, int64_t new_size) const;

  // Truncates the file to the requested size.
  // Returns false if fails.
  virtual bool Truncate(const char* filename, int64_t new_size) const;

  // Grows the file to the requested size.  Does not change the
  // position pointer.
  virtual bool Grow(int fd, int64_t new_size) const;
  virtual bool Grow(const char* filename, int64_t new_size) const;

  // Writes to a file.  Returns true if all the data was successfully
  // written.  Handles interrupted writes.
  virtual bool Write(int fd, const void* data, size_t data_size) const;
  virtual bool Write(const char* filename, const void* data,
                     size_t data_size) const;

  virtual bool PWrite(int fd, off_t offset, const void* data,
                      size_t data_size) const;
  virtual bool PWrite(const char* filename, off_t offset, const void* data,
                      size_t data_size) const;

  // Reads from a file. Returns true if data was successfully read out. If the
  // file is seekable, read starts at the file offset, and the file offset is
  // incremented by number of bytes read.
  virtual bool Read(int fd, void* buf, size_t buf_size) const;
  virtual bool Read(const char* filename, void* buf, size_t buf_size) const;
  virtual bool PRead(int fd, void* buf, size_t buf_size, off_t offset) const;
  virtual bool PRead(const char* filename, void* buf, size_t buf_size,
                     off_t offset) const;

  // Syncs the file to disk (fdatasync). Returns true on success.
  virtual bool DataSync(int fd) const;

  // Renames a file.  A file with new_name must not already exist.
  virtual bool RenameFile(const char* old_name, const char* new_name) const;

  // Renames two files or directories so their names are swapped.
  // Both names must already exist.
  virtual bool SwapFiles(const char* one, const char* two) const;

  // Creates a directory if it does not yet exist.
  virtual bool CreateDirectory(const char* dir_name) const;

  // Creates a directory if it does not yet exist, building the entire path
  // if it does not yet exist.
  virtual bool CreateDirectoryRecursively(const char* dir_name) const;

  // Compute the disk usage of the given file. Similarly to the
  // 'du' command, it attempts to estimate the actual disk usage, so for
  // sparse files it may return less than their length.
  // Returns kBadFileSize on error.
  virtual int64_t GetDiskUsage(int fd) const;

  // Compute the disk usage of the given file or directory. Similarly to the
  // 'du' command, it attempts to estimate the actual disk usage, so for
  // sparse files it may return less than their length. Returns kBadFileSize
  // on error. Does not recurse on directories.
  virtual int64_t GetFileDiskUsage(const char* path) const;

  // Compute the disk usage of the given file or directory. Similarly to the
  // 'du' command, it attempts to estimate the actual disk usage, so for
  // sparse files it may return less than their length. Returns kBadFileSize
  // on error. Recurses on directories.
  virtual int64_t GetDiskUsage(const char* path) const;

  // Returns the current position in the given file. Returns -1 and sets errno
  // on failure.
  virtual int64_t GetCurrentPosition(int fd) const;

  virtual int64_t SetPosition(int fd, int offset) const;

  // Increments to_increment by size if size is valid, or sets to_increment
  // to kBadFileSize if either size or to_increment is kBadFileSize.
  static void IncrementByOrSetInvalid(int64_t size, int64_t* to_increment);

  // Return -1 if file_size is invalid. Otherwise, return file_size.
  static int64_t SanitizeFileSize(int64_t file_size) {
    return (file_size != kBadFileSize) ? file_size : -1;
  }
};
// LINT.ThenChange(//depot/google3/icing/file/mock-filesystem.h)

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_FILESYSTEM_H_
