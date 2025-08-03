/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef BERBERIS_NDK_PROGRAM_TESTS_FILE_H_
#define BERBERIS_NDK_PROGRAM_TESTS_FILE_H_

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

inline const char* InitTempFileTemplate() {
  // tempnam() is not recommended for use, but we only use it to get the temp dir as it varies on
  // different platforms. E.g. /tmp on Linux, or /data/local/tmp on Android. The actual file
  // creation is done by the reliable mkstemp().
  char* gen_name = tempnam(/* dir */ nullptr, /* prefix */ nullptr);
  char* template_name;
  asprintf(&template_name, "%s-ndk-tests-XXXXXX", gen_name);
  free(gen_name);
  return template_name;
}

inline const char* TempFileTemplate() {
  static const char* kTemplateName = InitTempFileTemplate();
  return kTemplateName;
}

class TempFile {
 public:
  TempFile() {
    file_name_ = strdup(TempFileTemplate());
    // Altenatively we could have created a file descriptor by tmpfile() or mkstemp() with the
    // relative filename, but then there is no portable way to identify the full file name.
    fd_ = mkstemp(file_name_);
    if (fd_ < 0) {
      return;
    }
    file_ = fdopen(fd_, "r+");
  }

  ~TempFile() {
    if (file_ != nullptr) {
      fclose(file_);
    }
    unlink(file_name_);
    free(file_name_);
  }

  FILE* get() const { return file_; }

  int fd() const { return fd_; }

  const char* FileName() const { return file_name_; }

 private:
  FILE* file_ = nullptr;
  char* file_name_;
  int fd_;
};

#endif  // BERBERIS_NDK_PROGRAM_TESTS_FILE_H_
