/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include "jni.h"

#include <cstring>
#include <cassert>

#include <errno.h>
#include <fcntl.h>
#include <linux/userfaultfd.h>
#include <poll.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <syscall.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <sys/xattr.h>
#include <unistd.h>

#define SELINUX_CTXT_LEN 255

static void* userfault_handler_thread(void* arg) {
  struct uffd_msg msg;
  struct uffdio_copy uffdio_copy;
  ssize_t nread;
  int ret = 0;
  int uffd = reinterpret_cast<intptr_t>(arg);
  int page_sz = sysconf(_SC_PAGE_SIZE);
  char* page = static_cast<char*>(mmap(nullptr, page_sz, PROT_READ | PROT_WRITE,
                                       MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
  if (page == MAP_FAILED) {
    ret = errno;
    goto out;
  }
  std::memset(&msg, '\0', sizeof msg);
  std::memset(&uffdio_copy, '\0', sizeof uffdio_copy);
  std::memset(page, 'a', page_sz);

  // Loop, handling incoming events on the userfaultfd file descriptor
  do {
    // poll on uffd waiting for an event
    struct pollfd pollfd;
    int nready;
    pollfd.fd = uffd;
    pollfd.events = POLLIN;
    nready = poll(&pollfd, 1, -1);
    if (nready == -1) {
      ret = errno;
      break;
    }

    /* Read an event from the userfaultfd */
    nread = read(uffd, &msg, sizeof(msg));
    // EOF on userfaultfd
    if (nread == 0) {
       ret = -1;
       break;
    }

    if (nread == -1) {
      ret = errno;
      break;
    }

    // We expect only one kind of event; verify that assumption
    if (msg.event != UFFD_EVENT_PAGEFAULT) {
      ret = -1;
      break;
    }
    uffdio_copy.src = reinterpret_cast<size_t>(page);

    // Align fault address to page boundary
    uffdio_copy.dst = msg.arg.pagefault.address & ~(page_sz - 1);
    uffdio_copy.len = page_sz;
    uffdio_copy.mode = 0;  // Wake-up thread thread waiting for page-fault resolution
    uffdio_copy.copy = 0;  // Used by kernel to return how many bytes copied
    if (ioctl(uffd, UFFDIO_COPY, &uffdio_copy) < 0) {
      ret = errno;
      break;
    }
  } while (false);
  munmap(page, page_sz);
out:
  pthread_exit(reinterpret_cast<void*>(ret));
}

int64_t uffd_api_ioctl(int fd, uint64_t features) {
  struct uffdio_api api;
  std::memset(&api, '\0', sizeof api);
  api.api = UFFD_API;
  api.features = features;
  if (ioctl(fd, UFFDIO_API, &api) < 0) {
    return -1;
  }
  return api.features;
}

extern "C"
JNIEXPORT bool JNICALL Java_android_os_cts_uffdgc_UserfaultfdTest_confirmKernelVersion(JNIEnv*) {
#if defined(__linux__)
  static constexpr int kRequiredMajor = 5;
  static constexpr int kRequiredMinor = 4;

  int major, minor;
  struct utsname uts;
  uname(&uts);
  assert(strcmp(uts.sysname, "Linux") == 0);
  if (sscanf(uts.release, "%d.%d", &major, &minor) != 2 ||
      (major < kRequiredMajor || (major == kRequiredMajor && minor < kRequiredMinor))) {
    return false;
  }
  return true;
#else
  return false;
#endif
}

extern "C"
JNIEXPORT bool JNICALL Java_android_os_cts_uffdgc_UserfaultfdTest_confirmKernelArch64bit(JNIEnv*) {
#if defined(__linux__)
  struct utsname uts;
  uname(&uts);
  assert(strcmp(uts.sysname, "Linux") == 0);
  if (strstr(uts.machine, "64") != nullptr ||
      strstr(uts.machine, "armv8") == uts.machine) {
    return true;
  }
  return false;
#else
  return false;
#endif
}

extern "C"
JNIEXPORT jint JNICALL Java_android_os_cts_uffdgc_UserfaultfdTest_performKernelSpaceUffd(JNIEnv*) {
  int ret = 0, write_fd = 0;
  void* addr = nullptr;
  pthread_t thr;  // ID of thread that handles page faults
  struct uffdio_register uffdio_register;
  int page_sz = sysconf(_SC_PAGE_SIZE);
  int uffd = syscall(__NR_userfaultfd, O_CLOEXEC | O_NONBLOCK | UFFD_USER_MODE_ONLY);
  if (uffd < 0) {
    ret = errno;
    goto out;
  }

  write_fd = memfd_create("ReadFrom", MFD_CLOEXEC);
  if (write_fd == -1) {
    ret = errno;
    goto out_close_uffd;
  }
  if (ftruncate(write_fd, page_sz) == -1) {
    ret = errno;
    goto out_close_both;
  }

  /* Create a private anonymous mapping. The memory will be
   * demand-zero paged--that is, not yet allocated. When we
   * actually touch the memory, it will be allocated via
   * the userfaultfd.
   */
  addr = mmap(nullptr, page_sz, PROT_READ | PROT_WRITE,
              MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  if (addr == MAP_FAILED) {
    ret = errno;
    goto out_close_both;
  }

  if (uffd_api_ioctl(uffd, /*features*/ 0) == -1) {
    ret = errno;
    goto out_unmap;
  }

  /* Register the memory range of the mapping we just created for
   * handling by the userfaultfd object. In mode, we request to track
   * missing pages (i.e., pages that have not yet been faulted in).
   */
  std::memset(&uffdio_register, '\0', sizeof uffdio_register);
  uffdio_register.range.start = reinterpret_cast<size_t>(addr);
  uffdio_register.range.len = page_sz;
  uffdio_register.mode = UFFDIO_REGISTER_MODE_MISSING;
  if (ioctl(uffd, UFFDIO_REGISTER, &uffdio_register) < 0) {
    ret = errno;
    goto out_unmap;
  }

  // Create a thread that will process the userfaultfd events
  ret = pthread_create(&thr, nullptr, userfault_handler_thread, reinterpret_cast<void*>(uffd));
  if (ret != 0) {
    goto out_unmap;
  }

  // The write is expected to fail as the buffer starting at addr doesn't have a
  // page pinned, and when the syscall will try to read from it, will trigger a
  // userfault. But only user-mode faults are allowed.
  if (write(write_fd, addr, page_sz) >= 0) {
    ret = -1;
  } else {
    ret = errno;
  }

  // Invoke a userfault so that the handler thread then exit normally.
  static_cast<char*>(addr)[42] = 'a';

  pthread_join(thr, nullptr);
out_unmap:
  munmap(addr, page_sz);
out_close_both:
  close(write_fd);
out_close_uffd:
  close(uffd);
out:
  return ret;
}

// Invoking userfaultfd without USER_MODE_ONLY by a process without CAP_SYS_PTRACE
// should not be permitted.
extern "C"
JNIEXPORT jint JNICALL Java_android_os_cts_uffdgc_UserfaultfdTest_uffdWithoutUserModeOnly(JNIEnv*) {
  int uffd = syscall(__NR_userfaultfd, O_CLOEXEC | O_NONBLOCK);
  if (uffd < 0) {
    return errno;
  } else {
    close(uffd);
  }
  return 0;
}

extern "C"
JNIEXPORT jint JNICALL Java_android_os_cts_uffdgc_UserfaultfdTest_performMremapDontUnmap(JNIEnv*) {
  int ret = 0;
  int page_sz = sysconf(_SC_PAGE_SIZE);
  void* old = mmap(nullptr, page_sz, PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_SHARED, -1, 0);
  if (old == MAP_FAILED ||
      mremap(old, page_sz, page_sz, MREMAP_MAYMOVE | MREMAP_DONTUNMAP, nullptr) == MAP_FAILED) {
    ret = errno;
  }
  return ret;
}

extern "C"
JNIEXPORT jint JNICALL Java_android_os_cts_uffdgc_UserfaultfdTest_performMinorUffd(JNIEnv*) {
  uint64_t req_features = UFFD_FEATURE_MINOR_SHMEM;
  int64_t available_features = 0;
  int ret = 0;
  int uffd = syscall(__NR_userfaultfd, O_CLOEXEC | O_NONBLOCK | UFFD_USER_MODE_ONLY);
  if (uffd < 0) {
    ret = errno;
    goto out;
  }
  available_features = uffd_api_ioctl(uffd, req_features);
  if (available_features == -1) {
    ret = errno;
  } else if ((available_features & req_features) != req_features) {
    // Minor feature is not supported by this kernel.
    ret = EINVAL;
  }
  close(uffd);
out:
  return ret;
}

extern "C"
JNIEXPORT jint JNICALL Java_android_os_cts_uffdgc_UserfaultfdTest_checkGetattr(JNIEnv*) {
  ssize_t attr_ret = 0;
  char selinux_ctxt[SELINUX_CTXT_LEN + 1];
  int ret = 0;
  int uffd = syscall(__NR_userfaultfd, O_CLOEXEC | O_NONBLOCK | UFFD_USER_MODE_ONLY);
  if (uffd < 0) {
    ret = errno;
    goto out;
  }
  if (uffd_api_ioctl(uffd, /*features*/ 0) == -1) {
    ret = errno;
    goto out_close;
  }
  attr_ret = fgetxattr(uffd, XATTR_NAME_SELINUX, selinux_ctxt, SELINUX_CTXT_LEN);
  if (attr_ret == -1) {
    ret = errno;
    goto out_close;
  }
  // We should never reach here as the call to fgetxattr must return EACCES.
  selinux_ctxt[attr_ret] = 0;
  if (strstr(selinux_ctxt, "userfaultfd") == nullptr) {
    ret = -1;
  }
out_close:
  close(uffd);
out:
  return ret;
}
