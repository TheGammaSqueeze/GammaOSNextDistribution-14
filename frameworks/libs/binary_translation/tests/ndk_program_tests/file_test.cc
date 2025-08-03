/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "gtest/gtest.h"

#include <dirent.h>
#include <dlfcn.h>
#include <pthread.h>
#include <sys/file.h>
#include <sys/select.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cerrno>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>

#include "file.h"  // NOLINT

//------------------------------------------------------------------------------
// Test simple file IO

TEST(File, Mkstemp) {
  char* temp = strdup(TempFileTemplate());
  int fd = mkstemp(temp);
  ASSERT_NE(fd, -1);
  ASSERT_EQ(close(fd), 0);
  ASSERT_EQ(unlink(temp), 0);
  free(temp);
}

extern "C" int mkstemps(char* templ, int suffix_len);

TEST(File, Mkstemps) {
  char* temp;
  asprintf(&temp, "%s%s", TempFileTemplate(), ".txt");
  int fd = mkstemps(temp, 4);
  ASSERT_NE(fd, -1);
  ASSERT_EQ(access(temp, R_OK | W_OK), 0);
  ASSERT_EQ(close(fd), 0);
  ASSERT_EQ(unlink(temp), 0);
  free(temp);
}

TEST(File, Fdopen) {
  TempFile f;
  ASSERT_TRUE(f.get());
}

TEST(File, ReadWrite) {
  TempFile f;
  ASSERT_EQ(fwrite("Hello", 1, 5, f.get()), 5U);
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);
  char buf[5];
  ASSERT_EQ(fread(&buf, 1, 5, f.get()), 5U);
  EXPECT_EQ(buf[0], 'H');
  EXPECT_EQ(buf[4], 'o');
}

TEST(File, PReadWrite) {
  TempFile f;
  const char* hello = "Hello";
  ASSERT_EQ(pwrite(f.fd(), hello, strlen(hello), 0), 5);
  char buf[5];
  ASSERT_EQ(pread(f.fd(), buf, 4, 1), 4);
  buf[4] = '\0';
  EXPECT_STREQ(buf, "ello");
}

TEST(File, fileno) {
  TempFile f;
  ASSERT_EQ(f.fd(), fileno(f.get()));
}

TEST(File, Ftell) {
  TempFile f;
  EXPECT_EQ(ftell(f.get()), 0);
  ASSERT_EQ(fwrite("Hello", 1, 5, f.get()), 5U);
  EXPECT_EQ(ftell(f.get()), 5);
  ASSERT_EQ(fseek(f.get(), 1, SEEK_SET), 0);
  EXPECT_EQ(ftell(f.get()), 1);
}

TEST(File, Lseek) {
  TempFile f;
  ASSERT_EQ(fwrite("Hello", 1, 5, f.get()), 5U);
  ASSERT_EQ(fflush(f.get()), 0);
  EXPECT_EQ(lseek(f.fd(), 1, SEEK_SET), 1);
  EXPECT_EQ(lseek(f.fd(), 1, SEEK_CUR), 2);
  EXPECT_EQ(lseek(f.fd(), -1, SEEK_END), 4);
  EXPECT_EQ(lseek64(f.fd(), -2, SEEK_END), 3);
}

TEST(File, Ftruncate) {
  TempFile f;
  ASSERT_EQ(fwrite("Hello", 1, 5, f.get()), 5U);
  ASSERT_EQ(fflush(f.get()), 0);
  ASSERT_EQ(lseek(f.fd(), 0, SEEK_END), 5);
  ASSERT_EQ(lseek(f.fd(), 0, SEEK_SET), 0);
  EXPECT_EQ(ftruncate(f.fd(), -1), -1);
  EXPECT_EQ(ftruncate(f.fd(), 3), 0);
  EXPECT_EQ(lseek(f.fd(), 0, SEEK_END), 3);
}

TEST(File, Reopen) {
  TempFile f;
  // freopen(nullptr, ...) is not supported in bionic.
  ASSERT_TRUE(freopen(f.FileName(), "r", f.get()));
  EXPECT_EQ(fwrite("Hello", 1, 5, f.get()), 0U);
  ASSERT_TRUE(freopen(f.FileName(), "r+", f.get()));
  EXPECT_EQ(fwrite("Hello", 1, 5, f.get()), 5U);
}

TEST(File, ODirectoryFlag) {
  TempFile f;
  errno = 0;
  // Tries to open a file with O_DIRECTORY, which should fail.
  ASSERT_EQ(open(f.FileName(), O_RDONLY | O_DIRECTORY), -1);
  EXPECT_EQ(errno, ENOTDIR);
}

TEST(File, TempFile) {
  FILE* f = tmpfile();
  ASSERT_TRUE(f);
  ASSERT_EQ(fclose(f), 0);
}

void TestStatBuf(const struct stat& buf, int file_size, const char* msg) {
  EXPECT_EQ(buf.st_size, file_size) << msg;
  EXPECT_EQ(buf.st_nlink, 1U) << msg;
  // regular file with chmod 600.
  EXPECT_EQ(static_cast<int>(buf.st_mode), S_IFREG | S_IRUSR | S_IWUSR) << msg;
  EXPECT_NE(buf.st_blksize, 0U) << msg;
  EXPECT_EQ(buf.st_blksize % 512, 0U) << msg;
  EXPECT_NE(buf.st_mtime, 0) << msg;
  // We do not support st_atime/st_ctime.
}

TEST(File, Stat) {
  // Make sure file will have the exact permissions we want.
  mode_t saved_umask = umask(S_IRWXG | S_IRWXO);
  TempFile f;
  ASSERT_GE(fputs("test", f.get()), 0);
  ASSERT_EQ(fflush(f.get()), 0);
  struct stat buf;
  ASSERT_EQ(stat(f.FileName(), &buf), 0);
  TestStatBuf(buf, 4, "stat");
  ASSERT_EQ(lstat(f.FileName(), &buf), 0);
  TestStatBuf(buf, 4, "lstat");
  ASSERT_EQ(fstat(f.fd(), &buf), 0);
  TestStatBuf(buf, 4, "fstat");
  // Restore umask.
  umask(saved_umask);
}

int vfprintf_call(FILE* f, const char* format, ...) {
  va_list args;
  va_start(args, format);
  int result = vfprintf(f, format, args);
  va_end(args);
  return result;
}

int vfscanf_call(FILE* f, const char* format, ...) {
  va_list args;
  va_start(args, format);
  int result = vfscanf(f, format, args);
  va_end(args);
  return result;
}

TEST(File, PrintfScanf) {
  TempFile f;
  ASSERT_GT(fprintf(f.get(), "%d %lf %lld %p\n", 1, 2.0, 3LL, &f), 0);
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);
  int res_int;
  double res_double;
  int64_t res_long;
  TempFile* res_pointer;
  ASSERT_EQ(fscanf(f.get(), "%d%lf%lld%p", &res_int, &res_double, &res_long, &res_pointer), 4);
  EXPECT_EQ(res_int, 1);
  EXPECT_DOUBLE_EQ(res_double, 2.0);
  EXPECT_EQ(res_long, 3);
  EXPECT_EQ(res_pointer, &f);
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);

  ASSERT_EQ(vfscanf_call(f.get(), "%d%lf%lld%p", &res_int, &res_double, &res_long, &res_pointer),
            4);
  EXPECT_EQ(res_int, 1);
  EXPECT_DOUBLE_EQ(res_double, 2.0);
  EXPECT_EQ(res_long, 3);
  EXPECT_EQ(res_pointer, &f);
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);

  ASSERT_GT(vfprintf_call(f.get(), "%.1lf_%d_%lld\n", 1.0, 2, 3LL), 0);
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);
  char data[256];
  ASSERT_EQ(fscanf(f.get(), "%s", data), 1);
  EXPECT_STREQ(data, "1.0_2_3");
  const char* in_str = "http://foo.bar.com/main?lang=US";
  char more_data[64];
  ASSERT_EQ(sscanf(in_str, "%15[^:]:%[^\n]", data, more_data), 2);  // NOLINT
  EXPECT_STREQ(data, "http");
  EXPECT_STREQ(more_data, "//foo.bar.com/main?lang=US");
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);
  ASSERT_GT(fprintf(f.get(), "%0*d\n", 2, 1), 0);
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);
  ASSERT_EQ(fscanf(f.get(), "%s", data), 1);
  EXPECT_STREQ(data, "01");
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);
}

TEST(File, PositionalPrintf) {
  TempFile f;
  char buf[256];
#define CHECK_PRINTF(result, format, ...)                     \
  do {                                                        \
    ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);                \
    ASSERT_GT(fprintf(f.get(), format "\n", __VA_ARGS__), 0); \
    ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);                \
    ASSERT_EQ(fgets(buf, sizeof(buf), f.get()), buf);         \
    EXPECT_STREQ(buf, result "\n");                           \
  } while (0)

  // Good
  CHECK_PRINTF("2 3.0", "%1$d %2$.1lf", 2, 3.0);
  CHECK_PRINTF("2 3.0", "%2$d %1$.1lf", 3.0, 2);
  CHECK_PRINTF("2.000", "%2$.*1$lf", 3, 2.0);
  CHECK_PRINTF(" abc", "%2$*1$s", 4, "abc");
  CHECK_PRINTF("1 1 2", "%1$d %1$d %2$d", 1, 2);

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wformat"
  // Bad
  CHECK_PRINTF("2 3 1", "%2$d %d %1$d", 1, 2, 3);
  CHECK_PRINTF(" abc 4 1", "%1$*2$s %d %3$d", "abc", 4, 1);

  // Ugly
  CHECK_PRINTF(" abc 1", "%1$*s %d", 4, "abc", 1);
  CHECK_PRINTF("1 2 2", "%d %d %1$2$d", 1, 2);
#pragma GCC diagnostic pop

#undef CHECK_PRINTF
}

TEST(File, FdPrintf) {
  TempFile f;
  using FdPrintf = int (*)(int fd, const char* format, ...);
  FdPrintf fdprintf = reinterpret_cast<FdPrintf>(dlsym(RTLD_DEFAULT, "fdprintf"));
  ASSERT_GT(fdprintf(f.fd(), "%.1lf %d %lld\n", 1.0, 2, 3LL), 0);
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);
  char buf[16];
  ASSERT_EQ(fgets(buf, sizeof(buf), f.get()), buf);
  EXPECT_STREQ(buf, "1.0 2 3\n");
}

TEST(File, GetPut) {
  TempFile f;
  ASSERT_GE(fputs("Hell", f.get()), 0);
  ASSERT_EQ(fputc('o', f.get()), 'o');
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);
  EXPECT_EQ(fgetc(f.get()), 'H');
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);
  char buf[6];
  ASSERT_EQ(buf, fgets(buf, sizeof(buf), f.get()));
  EXPECT_EQ(buf[0], 'H');
  EXPECT_EQ(buf[4], 'o');
}

TEST(File, Feof) {
  TempFile f;
  ASSERT_EQ(fgetc(f.get()), EOF);
  ASSERT_TRUE(feof(f.get()));
  clearerr(f.get());
  ASSERT_FALSE(feof(f.get()));
}

TEST(File, Ungetc) {
  TempFile f;
  ASSERT_EQ(ungetc(' ', f.get()), ' ');
  ASSERT_EQ(fgetc(f.get()), ' ');
  ASSERT_EQ(fgetc(f.get()), EOF);
}

TEST(File, Setvbuf) {
  char buf[1024];
  memset(buf, 1, sizeof(buf));
  TempFile f;
  EXPECT_EQ(setvbuf(f.get(), nullptr, _IOFBF, 1024), 0);
  ASSERT_EQ(setvbuf(f.get(), buf, _IOFBF, sizeof(buf)), 0);
  char data[2048];
  memset(data, 2, sizeof(data));
  ASSERT_EQ(fwrite(data, 1, 1, f.get()), 1U);
  // Check that buffer is used
  EXPECT_TRUE(memchr(buf, 2, sizeof(buf)));
  // Check that it doesn't corrupt read/writes.
  ASSERT_EQ(fwrite(data, 1, sizeof(data), f.get()), sizeof(data));
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);
  char rdata[2048];
  ASSERT_EQ(fread(rdata, 1, sizeof(rdata), f.get()), sizeof(rdata));
  EXPECT_EQ(memcmp(data, rdata, sizeof(data)), 0);
}

TEST(File, SetBuffer) {
  char buf[1024];
  memset(buf, 1, sizeof(buf));
  TempFile f;
  setbuffer(f.get(), buf, sizeof(buf));
  char data = 2;
  ASSERT_EQ(fwrite(&data, 1, 1, f.get()), 1U);
  ASSERT_TRUE(memchr(buf, 2, sizeof(buf)));
}

int vsprintf_call(char* str, const char* format, ...) {
  va_list args;
  va_start(args, format);
  int result = vsprintf(str, format, args);
  va_end(args);
  return result;
}

int vsnprintf_call(char* str, size_t n, const char* format, ...) {
  va_list args;
  va_start(args, format);
  int result = vsnprintf(str, n, format, args);
  va_end(args);
  return result;
}

int vasprintf_call(char** str, const char* format, ...) {
  va_list args;
  va_start(args, format);
  int result = vasprintf(str, format, args);
  va_end(args);
  return result;
}

TEST(File, StringPrintfScanf) {
  char data[256];
  ASSERT_GT(snprintf(data, sizeof(data), "%d %lf %lld %p", 1, 2.0, 3LL, data), 0);
  int res_int;
  double res_double;
  int64_t res_int64;
  void* res_pointer;
  ASSERT_EQ(sscanf(data,
                   "%d%lf%lld%p",  // NOLINT
                   &res_int,
                   &res_double,
                   &res_int64,
                   &res_pointer),
            4);
  EXPECT_EQ(res_int, 1);
  EXPECT_DOUBLE_EQ(res_double, 2.0);
  EXPECT_EQ(res_int64, 3);
  EXPECT_EQ(res_pointer, data);
  ASSERT_GT(sprintf(data, "%.1lf %d %lld", 1.0, 2, 3LL), 0);  // NOLINT
  EXPECT_STREQ(data, "1.0 2 3");
  ASSERT_GT(sprintf(data, "%%%c", 'd'), 0);  // NOLINT
  EXPECT_STREQ(data, "%d");
  res_int64 = 3;
  ASSERT_GT(sprintf(data,
                    "%d %ld %lld %qd %d",  // NOLINT
                    1,
                    (long)2,
                    res_int64,
                    res_int64 + 1,
                    5),
            0);  // NOLINT
  EXPECT_STREQ(data, "1 2 3 4 5");
  ASSERT_GT(sprintf(data, "%s", "one two"), 0);  // NOLINT
  EXPECT_STREQ(data, "one two");
  char* new_data = nullptr;
  ASSERT_GT(asprintf(&new_data, "%.1lf %d %lld", 1.0, 2, 3LL), 0);  // NOLINT
  ASSERT_NE(new_data, nullptr);
  EXPECT_STREQ(new_data, "1.0 2 3");
  free(new_data);
  char word[256];
  ASSERT_EQ(sscanf(data, "%s", word), 1);  // NOLINT
  EXPECT_STREQ(word, "one");
  ASSERT_GT(vsprintf_call(data, "%.1lf %d %lld", 1.0, 2, 3LL), 0);
  EXPECT_STREQ(data, "1.0 2 3");
  ASSERT_GT(vsnprintf_call(data, 256, "%.1lf %d %lld", 1.0, 2, 3LL), 0);
  EXPECT_STREQ(data, "1.0 2 3");
  new_data = nullptr;
  ASSERT_GT(vasprintf_call(&new_data, "%.1lf %d %lld", 1.0, 2, 3LL), 0);
  ASSERT_NE(new_data, nullptr);
  EXPECT_STREQ(new_data, "1.0 2 3");
  free(new_data);
}

TEST(File, Select) {
  fd_set read;
  struct timeval timeout;
  FD_ZERO(&read);
  FD_SET(STDOUT_FILENO, &read);
  timeout.tv_sec = 0;
  timeout.tv_usec = 1;
  ASSERT_EQ(select(STDOUT_FILENO + 1, &read, nullptr, nullptr, &timeout), 0);
  fd_set write;
  FD_ZERO(&write);
  FD_SET(STDOUT_FILENO, &write);
  ASSERT_EQ(select(STDOUT_FILENO + 1, nullptr, &write, nullptr, nullptr), 1);
}

void* ThreadPipeReadFunc(void* arg) {
  int* iarg = reinterpret_cast<int*>(arg);
  int fd = iarg[0];
  char buf;
  for (int i = 0; i < 1000; i++) {
    if (read(fd, &buf, 1) != 1) {
      return nullptr;
    }
  }
  iarg[1] = 1;
  return nullptr;
}

TEST(File, Pipe) {
  int fds[2];
  pthread_t thread;
  int arg[2];
  char buf = 0;
  pipe(fds);
  arg[0] = fds[0];
  arg[1] = 0;
  pthread_create(&thread, nullptr, ThreadPipeReadFunc, &arg);
  for (int i = 0; i < 1000; i++) {
    EXPECT_EQ(write(fds[1], &buf, 1), 1);
  }
  pthread_join(thread, nullptr);
  EXPECT_EQ(arg[1], 1);
}

// This function is implemented but is not present in public bionic headers.
extern "C" char* mkdtemp(char* tmpl);

TEST(File, TempDir) {
  char* temp = strdup(TempFileTemplate());
  // Currently mkdtemp can reuse existing directory because our mkdir is not
  // POSIX compliant on Pepper FS.  See crbug.com/314879.
  ASSERT_EQ(mkdtemp(temp), temp);
  struct stat dir_stat;
  ASSERT_EQ(stat(temp, &dir_stat), 0);
  ASSERT_TRUE(S_ISDIR(dir_stat.st_mode));
  ASSERT_EQ(rmdir(temp), 0);
  free(temp);
}

class TempDir {
 public:
  TempDir() {
    name_ = strdup(TempFileTemplate());
    if (mkdtemp(name_) != name_) {
      free(name_);
      name_ = nullptr;
    }
  }

  explicit TempDir(const char* dir) {
    const char* kDirTemplate = "/ndk-tests-XXXXXX";
    size_t max_len = strlen(dir) + strlen(kDirTemplate) + 1;
    name_ = reinterpret_cast<char*>(malloc(max_len));
    snprintf(name_, max_len, "%s%s", dir, kDirTemplate);
    if (mkdtemp(name_) != name_) {
      free(name_);
      name_ = nullptr;
    }
  }

  ~TempDir() {
    if (name_ != nullptr) {
      rmdir(name_);
      free(name_);
    }
  }

  const char* GetDirName() const { return name_; }

  const char* GetBaseName() {
    const char* result = name_;
    for (char* p = name_; *p; p++) {
      if (*p == '/') {
        result = p + 1;
      }
    }
    return result;
  }

 private:
  char* name_;
};

struct dirent* SkipDotDirsWithReaddir(DIR* pdir, struct dirent* entry) {
  // skip "." and ".."
  while (entry != nullptr && entry->d_name[0] == '.') {
    entry = readdir(pdir);  // NOLINT(runtime/threadsafe_fn)
  }
  return entry;
}

// We can't create files in /tmp directories outside of predefined places.
// We create directories inside of our temporary directory instead.
TEST(File, Readdir) {
  TempDir dir;
  ASSERT_NE(dir.GetDirName(), nullptr);
  TempDir dir1(dir.GetDirName());
  ASSERT_NE(dir1.GetDirName(), nullptr);
  TempDir dir2(dir.GetDirName());
  ASSERT_NE(dir2.GetDirName(), nullptr);
  DIR* pdir = opendir(dir.GetDirName());
  ASSERT_NE(pdir, nullptr);
  struct dirent* entry = readdir(pdir);  // NOLINT(runtime/threadsafe_fn)
  entry = SkipDotDirsWithReaddir(pdir, entry);
  ASSERT_NE(entry, nullptr);
  EXPECT_EQ(entry->d_type, DT_DIR);
  bool isDir1 = strcmp(dir1.GetBaseName(), entry->d_name) == 0;
  bool isDir2 = strcmp(dir2.GetBaseName(), entry->d_name) == 0;
  EXPECT_TRUE(isDir1 || isDir2);
  entry = readdir(pdir);  // NOLINT(runtime/threadsafe_fn)
  entry = SkipDotDirsWithReaddir(pdir, entry);
  ASSERT_NE(entry, nullptr);
  EXPECT_EQ(entry->d_type, DT_DIR);
  isDir1 |= strcmp(dir1.GetBaseName(), entry->d_name) == 0;
  isDir2 |= strcmp(dir2.GetBaseName(), entry->d_name) == 0;
  EXPECT_TRUE(isDir1 && isDir2);
  entry = readdir(pdir);  // NOLINT(runtime/threadsafe_fn)
  entry = SkipDotDirsWithReaddir(pdir, entry);
  ASSERT_EQ(entry, nullptr);
  ASSERT_EQ(closedir(pdir), 0);
}

extern "C" int getdents(unsigned int, dirent*, unsigned int) __attribute__((__weak__));

TEST(File, Getdents) {
  TempDir dir;
  ASSERT_NE(dir.GetDirName(), nullptr);
  TempDir dir1(dir.GetDirName());
  ASSERT_NE(dir1.GetDirName(), nullptr);
  int dir_fd = open(dir.GetDirName(), O_RDONLY | O_DIRECTORY);
  ASSERT_NE(dir_fd, -1);
  char buf[1024];
  int res = getdents(dir_fd, reinterpret_cast<struct dirent*>(buf), sizeof(buf));
  ASSERT_GT(res, 0);
  struct dirent* entry = reinterpret_cast<struct dirent*>(buf);
  int entries = 0;
  int pos = 0;
  bool has_dir1 = false;
  // Check that d_reclen is available first. d_type is the next field in the
  // dirent structure.
  while (static_cast<int>(offsetof(struct dirent, d_type)) <= res - pos &&
         entry->d_reclen + pos <= res) {
    entries++;
    EXPECT_EQ(entry->d_type, DT_DIR);
    if (strcmp(entry->d_name, dir1.GetBaseName()) == 0) {
      has_dir1 = true;
    }
    pos += entry->d_reclen;
    entry = reinterpret_cast<struct dirent*>(buf + pos);
  }
  EXPECT_EQ(entries, 3);
  EXPECT_TRUE(has_dir1);
  close(dir_fd);
}

bool SkipDotDirsWithReaddir_r(DIR* pdir, struct dirent* entry, struct dirent** result) {
  // skip "." and ".."
  while (*result != nullptr && entry->d_name[0] == '.') {
    if (readdir_r(pdir, entry, result) != 0) {
      return false;
    }
  }
  return true;
}

void TestReaddirRWithDir(DIR* pdir, const char* innerdir) {
  struct dirent entry;
  struct dirent* result;
  ASSERT_EQ(readdir_r(pdir, &entry, &result), 0);
  ASSERT_TRUE(SkipDotDirsWithReaddir_r(pdir, &entry, &result));
  ASSERT_EQ(result, &entry);
  EXPECT_EQ(entry.d_type, DT_DIR);
  EXPECT_STREQ(entry.d_name, innerdir);
  ASSERT_EQ(readdir_r(pdir, &entry, &result), 0);
  ASSERT_TRUE(SkipDotDirsWithReaddir_r(pdir, &entry, &result));
  EXPECT_EQ(result, nullptr);
}

TEST(File, Readdir_r) {
  TempDir dir;
  ASSERT_NE(dir.GetDirName(), nullptr);
  TempDir dir1(dir.GetDirName());
  ASSERT_NE(dir1.GetDirName(), nullptr);
  DIR* pdir = opendir(dir.GetDirName());
  ASSERT_NE(pdir, nullptr);
  TestReaddirRWithDir(pdir, dir1.GetBaseName());
  ASSERT_EQ(closedir(pdir), 0);
}

TEST(File, Rewinddir) {
  TempDir dir;
  ASSERT_NE(dir.GetDirName(), nullptr);
  TempDir dir1(dir.GetDirName());
  ASSERT_NE(dir1.GetDirName(), nullptr);
  DIR* pdir = opendir(dir.GetDirName());
  ASSERT_NE(pdir, nullptr);
  TestReaddirRWithDir(pdir, dir1.GetBaseName());
  rewinddir(pdir);
  TestReaddirRWithDir(pdir, dir1.GetBaseName());
  ASSERT_EQ(closedir(pdir), 0);
}

int ScandirFilter(const struct dirent* entry) {
  return entry->d_name[0] != '.';
}

int ScandirComparator(const struct dirent** entry1, const struct dirent** entry2) {
  return strcmp((*entry1)->d_name, (*entry2)->d_name);
}

TEST(File, Scandir) {
  TempDir dir;
  ASSERT_NE(dir.GetDirName(), nullptr);
  TempDir dir1(dir.GetDirName());
  ASSERT_NE(dir1.GetDirName(), nullptr);
  TempDir dir2(dir.GetDirName());
  ASSERT_NE(dir2.GetDirName(), nullptr);
  struct dirent** namelist;
  int size = scandir(dir.GetDirName(), &namelist, ScandirFilter, ScandirComparator);
  ASSERT_EQ(size, 2);
  ASSERT_LE(ScandirComparator(const_cast<const struct dirent**>(&namelist[0]),
                              const_cast<const struct dirent**>(&namelist[1])),
            0);
  for (int i = 0; i < size; i++) {
    free(namelist[i]);
  }
  free(namelist);
}

TEST(File, FlockAlwaysSucceeds) {
  TempFile f;
  EXPECT_EQ(flock(f.fd(), LOCK_SH), 0);
  EXPECT_EQ(flock(f.fd(), LOCK_EX), 0);
  EXPECT_EQ(flock(f.fd(), LOCK_UN), 0);
}

// Simple pseudo file.
struct funopen_cookie {
  int pos;
  char magic;
};

int funopen_read(void* cookie, char* data, int size) {
  funopen_cookie* file = static_cast<funopen_cookie*>(cookie);
  for (int i = 0; i < size; i++) {
    data[i] = (file->pos + i) % 256;
  }
  file->pos += size;
  return size;
}

int funopen_write(void* cookie, const char* data, int size) {
  funopen_cookie* file = static_cast<funopen_cookie*>(cookie);
  for (int i = 0; i < size; i++) {
    if (data[i] != file->magic) {
      errno = EIO;
      return 0;
    }
  }
  file->pos += size;
  return size;
}

fpos_t funopen_seek(void* cookie, fpos_t pos, int whence) {
  funopen_cookie* file = static_cast<funopen_cookie*>(cookie);
  switch (whence) {
    case SEEK_SET:
      file->pos = pos;
      break;
    case SEEK_CUR:
      file->pos += pos;
      break;
    default:
      errno = EINVAL;
      return -1;
  }
  return file->pos;
}

int funopen_close(void* /* cookie */) {
  return 0;
}

TEST(File, Funopen) {
  funopen_cookie cookie = {0, 'a'};
  FILE* f = funopen(&cookie, funopen_read, funopen_write, funopen_seek, funopen_close);
  ASSERT_NE(f, nullptr);
  // Disable buffering to make all file operations call funopen_* functions.
  ASSERT_EQ(setvbuf(f, nullptr, _IONBF, 0), 0);
  const size_t kBufSize = 4;
  char buf[kBufSize];

  ASSERT_EQ(fread(buf, 1, kBufSize, f), kBufSize);
  for (size_t i = 0; i < kBufSize; i++) {
    EXPECT_EQ(static_cast<char>(i), buf[i]);
  }
  memset(buf, 'b', kBufSize);
  ASSERT_EQ(fwrite(buf, 1, kBufSize, f), 0U);

  memset(buf, 'a', kBufSize);
  ASSERT_EQ(fwrite(buf, 1, kBufSize, f), kBufSize);

  ASSERT_EQ(ftell(f), static_cast<int>(2 * kBufSize));
  ASSERT_EQ(fseek(f, kBufSize, SEEK_SET), 0);
  ASSERT_EQ(ftell(f), static_cast<int>(kBufSize));

  // No one can hear your scream in our pseudo file.
  ASSERT_EQ(fread(buf, 1, kBufSize, f), kBufSize);
  for (size_t i = 0; i < kBufSize; i++) {
    EXPECT_EQ(buf[i], static_cast<char>(i + kBufSize));
  }

  ASSERT_EQ(fclose(f), 0);
}

TEST(File, UmaskActsSanely) {
  mode_t saved_umask = umask(0600);
  EXPECT_EQ(umask(0700), 0600);
  EXPECT_EQ(umask(0600), 0700);
  umask(saved_umask);
}
