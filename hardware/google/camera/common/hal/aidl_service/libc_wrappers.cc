#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <string>
#include <unordered_map>

#define LOG_TAG "GCH_libc_wrappers"
#include <log/log.h>

//#define MONITOR_FILE_OP

#ifdef __ANDROID_APEX__

#ifdef MONITOR_FILE_OP

using mkfifo_function_type = int (*)(const char*, mode_t);
using access_function_type = int (*)(const char*, int);
using mkdir_function_type = int (*)(const char*, mode_t);
using rmdir_function_type = int (*)(const char*);
using chdir_function_type = int (*)(const char*);
using link_function_type = int (*)(const char*, const char*);
using unlink_function_type = int (*)(const char*);
using rename_function_type = int (*)(const char*, const char*);
using stat_function_type = int (*)(const char*, struct stat*);
using chmod_function_type = int (*)(const char*, mode_t);
using chown_function_type = int (*)(const char*, uid_t, gid_t);
using utime_function_type = int (*)(const char*, struct utimbuf*);
using opendir_function_type = DIR* (*)(const char*);
using execl_function_type = int (*)(const char*, const char*, ...);
using execle_function_type = int (*)(const char*, const char*, ...);
using execlp_function_type = int (*)(const char*, const char*, ...);
using execv_function_type = int (*)(const char*, char* const[]);
using execvp_function_type = int (*)(const char*, char* const[]);
using openat_function_type = int (*)(int, const char*, int, ...);

extern "C" int mkfifo(const char* pathname, mode_t mode) {
  static auto real_function =
      reinterpret_cast<mkfifo_function_type>(dlsym(RTLD_NEXT, "mkfifo"));
  int ret = real_function(pathname, mode);
  ALOGI("mkfifo (%s,%d) == %d", pathname, mode, ret);
  return ret;
}
extern "C" int access(const char* pathname, int mode) {
  static auto real_function =
      reinterpret_cast<access_function_type>(dlsym(RTLD_NEXT, "access"));
  int ret = real_function(pathname, mode);
  ALOGI("access (%s,%d) == %d", pathname, mode, ret);
  return ret;
}
extern "C" int mkdir(const char* pathname, mode_t mode) {
  static auto real_function =
      reinterpret_cast<mkdir_function_type>(dlsym(RTLD_NEXT, "mkdir"));
  int ret = real_function(pathname, mode);
  ALOGI("mkdir (%s,%d) == %d", pathname, mode, ret);
  return ret;
}
extern "C" int rmdir(const char* pathname) {
  static auto real_function =
      reinterpret_cast<rmdir_function_type>(dlsym(RTLD_NEXT, "rmdir"));
  int ret = real_function(pathname);
  ALOGI("rmdir (%s) == %d", pathname, ret);
  return ret;
}
extern "C" int chdir(const char* path) {
  static auto real_function =
      reinterpret_cast<chdir_function_type>(dlsym(RTLD_NEXT, "chdir"));
  int ret = real_function(path);
  ALOGI("chdir (%s) == %d", path, ret);
  return ret;
}
extern "C" int link(const char* oldpath, const char* newpath) {
  static auto real_function =
      reinterpret_cast<link_function_type>(dlsym(RTLD_NEXT, "link"));
  int ret = real_function(oldpath, newpath);
  ALOGI("link (%s,%s) == %d", oldpath, newpath, ret);
  return ret;
}
extern "C" int unlink(const char* pathname) {
  static auto real_function =
      reinterpret_cast<unlink_function_type>(dlsym(RTLD_NEXT, "unlink"));
  int ret = real_function(pathname);
  ALOGI("unlink (%s) == %d", pathname, ret);
  return ret;
}
extern "C" int rename(const char* oldpath, const char* newpath) {
  static auto real_function =
      reinterpret_cast<rename_function_type>(dlsym(RTLD_NEXT, "rename"));
  int ret = real_function(oldpath, newpath);
  ALOGI("rename (%s,%s) == %d", oldpath, newpath, ret);
  return ret;
}
extern "C" int stat(const char* file_name, struct stat* buf) {
  static auto real_function =
      reinterpret_cast<stat_function_type>(dlsym(RTLD_NEXT, "stat"));
  int ret = real_function(file_name, buf);
  ALOGI("stat (%s, %p) == %d", file_name, buf, ret);
  return ret;
}
extern "C" int chmod(const char* path, mode_t mode) {
  static auto real_function =
      reinterpret_cast<chmod_function_type>(dlsym(RTLD_NEXT, "chmod"));
  int ret = real_function(path, mode);
  ALOGI("chmod (%s) == %d", path, ret);
  return ret;
}
extern "C" int chown(const char* path, uid_t owner, gid_t group) {
  static auto real_function =
      reinterpret_cast<chown_function_type>(dlsym(RTLD_NEXT, "chown"));
  int ret = real_function(path, owner, group);
  ALOGI("chown (%s,%d,%d) == %d", path, owner, group, ret);
  return ret;
}
extern "C" int utime(const char* filename, struct utimbuf* buf) {
  static auto real_function =
      reinterpret_cast<utime_function_type>(dlsym(RTLD_NEXT, "utime"));
  int ret = real_function(filename, buf);
  ALOGI("utime (%s, %p) == %d", filename, buf, ret);
  return ret;
}
extern "C" DIR* opendir(const char* name) {
  static auto real_function =
      reinterpret_cast<opendir_function_type>(dlsym(RTLD_NEXT, "opendir"));
  DIR* ret = real_function(name);
  ALOGI("opendir (%s) == %p", name, ret);
  return ret;
}
extern "C" int execl(const char* path, const char* arg, ...) {
  static auto real_function =
      reinterpret_cast<execl_function_type>(dlsym(RTLD_NEXT, "execl"));
  int ret = real_function(path, arg);
  ALOGI("execl (%s, %s) == %d", path, arg, ret);
  return ret;
}
extern "C" int execle(const char* path, const char* arg, ...) {
  static auto real_function =
      reinterpret_cast<execle_function_type>(dlsym(RTLD_NEXT, "execle"));
  int ret = real_function(path, arg);
  ALOGI("execle (%s, %s) == %d", path, arg, ret);
  return ret;
}
extern "C" int execlp(const char* file, const char* arg, ...) {
  static auto real_function =
      reinterpret_cast<execlp_function_type>(dlsym(RTLD_NEXT, "execlp"));
  int ret = real_function(file, arg);
  ALOGI("execlp %s, %s) == %d", file, arg, ret);
  return ret;
}
extern "C" int execv(const char* path, char* const argv[]) {
  static auto real_function =
      reinterpret_cast<execv_function_type>(dlsym(RTLD_NEXT, "execv"));
  int ret = real_function(path, argv);
  ALOGI("execv (%s, %s, ...) == %d", path, argv[0], ret);
  return ret;
}
extern "C" int execvp(const char* file, char* const argv[]) {
  static auto real_function =
      reinterpret_cast<execvp_function_type>(dlsym(RTLD_NEXT, "execvp"));
  int ret = real_function(file, argv);
  ALOGI("execvp (%s, %s,...) == %d", file, argv[0], ret);
  return ret;
}

extern "C" int openat(int dirfd, const char* pathname, int flags, ...) {
  static auto real_function =
      reinterpret_cast<openat_function_type>(dlsym(RTLD_NEXT, "openat"));
  int ret = real_function(dirfd, pathname, flags);
  ALOGI("openat(%d, %s, 0x%x) == %d", dirfd, pathname, flags, ret);
  return ret;
}
#endif

using __loader_dlopen_function_type = void* (*)(const char* filename, int flags,
                                                const void* caller_addr);

// This is a temporary workaround for prebuilts calling dlopen with absolute
// paths. We interpose __loader_dlopen instead of dlopen because dlopen calls
// __builtin_return_address to determine the linker namespace. We need
// caller_addr to point to the original caller and not "this" lib for the case
// of system libs that use dlopen.
extern "C" void* __loader_dlopen(const char* filename, int flags,
                                 const void* caller_addr) {
  static auto real__loader_dlopen_function =
      reinterpret_cast<__loader_dlopen_function_type>(
          dlsym(RTLD_NEXT, "__loader_dlopen"));
  if (!real__loader_dlopen_function) {
    ALOGE("Could not RTLD_NEXT __loader_dlopen, something very wrong.");
    std::abort();
  }
  void* ret = real__loader_dlopen_function(filename, flags, caller_addr);
  if (!ret) {
    ALOGI("dlopen(%s) failed, seeing if we can fix it", filename);
    std::string original_filename(filename);

    if (original_filename.find("/vendor/") == 0) {
      std::string new_filename = "/apex/com.google.pixel.camera.hal/" +
                                 original_filename.substr(strlen("/vendor/"));
      ALOGI("Trying %s instead of %s\n", new_filename.c_str(), filename);
      ret = real__loader_dlopen_function(new_filename.c_str(), flags,
                                         caller_addr);
      if (ret) {
        ALOGE(
            "ERROR: Update your code to not use absolute paths. dlopen(%s) "
            "failed but dlopen(%s) succeeded instead.",
            filename, new_filename.c_str());
      }
    }
  }
  return ret;
}
#endif
