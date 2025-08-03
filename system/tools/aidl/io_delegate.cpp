/*
 * Copyright (C) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "io_delegate.h"

#include <cstring>
#include <fstream>
#include <type_traits>
#include <vector>

#ifdef _WIN32
#include <direct.h>
#include <windows.h>
#undef ERROR
#else
#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#endif

#include <android-base/strings.h>

#include "logging.h"
#include "os.h"

using std::string;
using std::unique_ptr;
using std::vector;

using android::base::Error;
using android::base::Result;
using android::base::Split;
using android::base::StartsWith;

namespace android {
namespace aidl {

bool IoDelegate::GetAbsolutePath(const string& path, string* absolute_path) {
#ifdef _WIN32

  char buf[4096];
  DWORD path_len = GetFullPathName(path.c_str(), sizeof(buf), buf, nullptr);
  if (path_len <= 0 || path_len >= sizeof(buf)) {
    AIDL_ERROR(path) << "Failed to GetFullPathName";
    return false;
  }
  *absolute_path = buf;

  return true;

#else

  if (path.empty()) {
    AIDL_ERROR(path) << "Giving up on finding an absolute path to represent the empty string.";
    return false;
  }
  if (path[0] == OS_PATH_SEPARATOR) {
    *absolute_path = path;
    return true;
  }

  char buf[4096];
  if (getcwd(buf, sizeof(buf)) == nullptr) {
    AIDL_ERROR(path) << "Path of current working directory does not fit in " << sizeof(buf)
                     << " bytes";
    return false;
  }

  *absolute_path = buf;
  *absolute_path += OS_PATH_SEPARATOR;
  *absolute_path += path;
  return true;
#endif
}

unique_ptr<string> IoDelegate::GetFileContents(
    const string& filename,
    const string& content_suffix) const {
  unique_ptr<string> contents;
  std::ifstream in(filename, std::ios::in | std::ios::binary);
  if (!in) {
    return contents;
  }
  contents.reset(new string);
  in.seekg(0, std::ios::end);
  ssize_t file_size = in.tellg();
  contents->resize(file_size + content_suffix.length());
  in.seekg(0, std::ios::beg);
  // Read the file contents into the beginning of the string
  in.read(&(*contents)[0], file_size);
  // Drop the suffix in at the end.
  contents->replace(file_size, content_suffix.length(), content_suffix);
  in.close();

  return contents;
}

bool IoDelegate::FileIsReadable(const string& path) const {
#ifdef _WIN32
  // check that the file exists and is not write-only
  return (0 == _access(path.c_str(), 0)) &&  // mode 0=exist
         (0 == _access(path.c_str(), 4));    // mode 4=readable
#else
  return (0 == access(path.c_str(), R_OK));
#endif
}

static bool CreateNestedDirs(const string& caller_base_dir, const vector<string>& nested_subdirs) {
  string base_dir = caller_base_dir;
  if (base_dir.empty()) {
    base_dir = ".";
  }
  for (const string& subdir : nested_subdirs) {
    if (base_dir[base_dir.size() - 1] != OS_PATH_SEPARATOR) {
      base_dir += OS_PATH_SEPARATOR;
    }
    base_dir += subdir;
    bool success;
#ifdef _WIN32
    success = _mkdir(base_dir.c_str()) == 0;
#else
    success = mkdir(base_dir.c_str(),
                    S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) == 0;
#endif
    // On darwin when you try to mkdir("/", ...) we get EISDIR.
    if (!success && (errno != EEXIST && errno != EISDIR)) {
      AIDL_ERROR(caller_base_dir) << "Error while creating " << base_dir << ": " << strerror(errno);
      return false;
    }
  }
  return true;
}

bool IoDelegate::CreateDirForPath(const string& path) const {
  if (path.empty()) {
    return true;
  }

  string absolute_path;
  if (!GetAbsolutePath(path, &absolute_path)) {
    return false;
  }

  auto directories = Split(absolute_path, string{OS_PATH_SEPARATOR});

  // The "base" directory is just the root of the file system.  On Windows,
  // this will look like "C:\" but on Unix style file systems we get an empty
  // string after splitting "/foo" with "/"
  string base = directories[0];
  if (base.empty()) {
    base = "/";
  }
  directories.erase(directories.begin());

  // Remove the actual file in question, we're just creating the directory path.
  bool is_file = path.back() != OS_PATH_SEPARATOR;
  if (is_file) {
    directories.pop_back();
  }

  return CreateNestedDirs(base, directories);
}

unique_ptr<CodeWriter> IoDelegate::GetCodeWriter(
    const string& file_path) const {
  if (CreateDirForPath(file_path)) {
    return CodeWriter::ForFile(file_path);
  } else {
    return nullptr;
  }
}

#ifdef _WIN32

static Result<void> add_list_files(const string& dirname, vector<string>* result) {
  AIDL_FATAL_IF(result == nullptr, dirname);

  WIN32_FIND_DATA find_data;
  // Look up the first file.
  // See https://stackoverflow.com/a/14841564/112950 for why we use remove_pointer_t
  // here.
  // Note: we need to use a wildcard expression like `\*` to ensure we traverse
  // the directory. Otherwise Find{First,Next}File will only return the directory
  // itself and stop.
  const string path(dirname + "\\*");
  std::unique_ptr<std::remove_pointer_t<HANDLE>, decltype(&FindClose)> search_handle(
      FindFirstFile(path.c_str(), &find_data), FindClose);

  if (search_handle.get() == INVALID_HANDLE_VALUE) {
    return Error() << "Failed to read directory '" << dirname << "': " << GetLastError();
  }

  bool has_more_files = true;
  do {
    const bool skip = !strcmp(find_data.cFileName, ".") || !strcmp(find_data.cFileName, "..");

    if (!skip) {
      if (find_data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
        if (auto ret = add_list_files(dirname + OS_PATH_SEPARATOR + find_data.cFileName, result);
            !ret.ok()) {
          return ret;
        }
      } else {
        result->emplace_back(dirname + OS_PATH_SEPARATOR + find_data.cFileName);
      }
    }

    has_more_files = FindNextFile(search_handle.get(), &find_data);
    if (!has_more_files) {
      const DWORD err = GetLastError();
      if (err != ERROR_NO_MORE_FILES) {
        return Error() << "Failed to read directory entry in '" << dirname << "': " << err;
      }
    }
  } while (has_more_files);

  return Result<void>();
}
#else
static Result<void> add_list_files(const string& dirname, vector<string>* result) {
  AIDL_FATAL_IF(result == nullptr, dirname);
  std::unique_ptr<DIR, decltype(&closedir)> dir(opendir(dirname.c_str()), closedir);

  if (dir == nullptr) {
    return Error() << "Failed to read directory '" << dirname << "': " << strerror(errno);
  }

  while (true) {
    errno = 0;
    struct dirent* ent = readdir(dir.get());
    if (ent == nullptr) {
      if (errno != 0) {
        return Error() << "Failed to read directory entry in '" << dirname
                       << "': " << strerror(errno);
      }
      break;
    }

    if (!strcmp(ent->d_name, ".") || !strcmp(ent->d_name, "..")) {
      continue;
    }
    if (ent->d_type == DT_REG) {
      result->emplace_back(dirname + OS_PATH_SEPARATOR + ent->d_name);
    } else if (ent->d_type == DT_DIR) {
      if (auto ret = add_list_files(dirname + OS_PATH_SEPARATOR + ent->d_name, result); !ret.ok()) {
        return ret;
      }
    }
  }

  return Result<void>();
}
#endif

Result<vector<string>> IoDelegate::ListFiles(const string& dir) const {
  vector<string> result;
  if (auto ret = add_list_files(dir, &result); !ret.ok()) {
    return ret.error();
  }
  return result;
}

string IoDelegate::CleanPath(const string& path) {
  if (base::StartsWith(path, string{'.', OS_PATH_SEPARATOR})) {
    return path.substr(2);
  }
  return path;
}

}  // namespace android
}  // namespace aidl
