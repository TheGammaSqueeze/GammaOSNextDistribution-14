/*
 * Copyright (C) 2021 The Android Open Source Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#define SYSCALL_NO_TLS 1
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <link.h>
#include <stdalign.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <sys/mman.h>
#include <sys/param.h>
#include <sys/syscall.h>
#include <sys/user.h>
#include <unistd.h>

#include "reloc.h"
#include "syscall.h"

typedef void EntryFunc(void);

// arm64 doesn't have a constant page size and has to use the value from AT_PAGESZ.
#ifndef PAGE_SIZE
#define PAGE_SIZE g_page_size
#endif

#define PAGE_START(x) ((x) & (~(PAGE_SIZE-1)))
#define PAGE_END(x) PAGE_START((x) + (PAGE_SIZE - 1))

#define START "_start"
#include "crt_arch.h"

int main();
weak void _init();
weak void _fini();
int __libc_start_main(int (*)(), int, char **,
  void (*)(), void(*)(), void(*)());

static ElfW(Phdr) replacement_phdr_table[64];
static char replacement_interp[PATH_MAX];

static bool g_debug = false;
static const char* g_prog_name = NULL;
static uintptr_t g_page_size = 0;
static int g_errno = 0;

__attribute__((visibility("hidden"))) extern ElfW(Dyn) _DYNAMIC[];

__attribute__((used))
static long ri_set_errno(unsigned long val) {
  if (val > -4096UL) {
    g_errno = -val;
    return -1;
  }
  return val;
}

#define ri_syscall(...) ri_set_errno(__syscall(__VA_ARGS__))

static ssize_t ri_write(int fd, const void* buf, size_t amt) {
  return ri_syscall(SYS_write, fd, buf, amt);
}

__attribute__((noreturn))
static void ri_exit(int status) {
  ri_syscall(SYS_exit, status);
  __builtin_unreachable();
}

static int ri_open(const char* path, int flags, mode_t mode) {
  return ri_syscall(SYS_openat, AT_FDCWD, path, flags, mode);
}

static int ri_close(int fd) {
  return ri_syscall(SYS_close, fd);
}

static off_t ri_lseek(int fd, off_t offset, int whence) {
  return ri_syscall(SYS_lseek, fd, offset, whence);
}

static ssize_t ri_readlink(const char* path, char* buf, size_t size) {
  return ri_syscall(SYS_readlinkat, AT_FDCWD, path, buf, size);
}

static void* ri_mmap(void* addr, size_t length, int prot, int flags, int fd, off_t offset) {
#ifdef SYS_mmap2
  return (void*)ri_syscall(SYS_mmap2, addr, length, prot, flags, fd, offset/SYSCALL_MMAP2_UNIT);
#else
  return (void*)ri_syscall(SYS_mmap, addr, length, prot, flags, fd, offset);
#endif
}

static void* ri_munmap(void* addr, size_t length) {
  return (void*)ri_syscall(SYS_munmap, addr, length);
}

static int ri_mprotect(void* addr, size_t len, int prot) {
  return ri_syscall(SYS_mprotect, addr, len, prot);
}

static ssize_t ri_pread(int fd, void* buf, size_t size, off_t ofs) {
  return ri_syscall(SYS_pread, fd, buf, size, __SYSCALL_LL_PRW(ofs));
}

static size_t ri_strlen(const char* src) {
  for (size_t len = 0;; ++len) {
    if (src[len] == '\0') return len;
  }
}

static char* ri_strcpy(char* dst, const char* src) {
  char* result = dst;
  while ((*dst = *src) != '\0') {
    ++dst;
    ++src;
  }
  return result;
}

static char* ri_strcat(char* dst, const char* src) {
  ri_strcpy(dst + ri_strlen(dst), src);
  return dst;
}

static void* ri_memset(void* dst, int val, size_t len) {
  for (size_t i = 0; i < len; ++i) {
    ((char*)dst)[i] = val;
  }
  return dst;
}

__attribute__ ((unused))
static void* ri_memcpy(void* dst, const void* src, size_t len) {
  for (size_t i = 0; i < len; ++i) {
    ((char*)dst)[i] = ((char*)src)[i];
  }
  return dst;
}

static int ri_strncmp(const char* x, const char *y, size_t maxlen) {
  for (size_t i = 0;; ++i) {
    if (i == maxlen) return 0;
    int result = (unsigned char)x[i] - (unsigned char)y[i];
    if (result != 0) return result;
    if (x[i] == '\0') return 0;
  }
}

static int ri_strcmp(const char* x, const char *y) {
  return ri_strncmp(x, y, SIZE_MAX);
}

static char* ri_strrchr(const char* str, int ch) {
  char* result = NULL;
  while (true) {
    if (*str == ch) result = (char*)str;
    if (*str == '\0') break;
    ++str;
  }
  return result;
}

static char* ri_strchr(const char* str, int ch) {
  while (*str) {
    if (*str == ch) return (char*)str;
    ++str;
  }
  return NULL;
}

static void ri_dirname(char* path) {
  char* last_slash = ri_strrchr(path, '/');
  if (last_slash == NULL) {
    path[0] = '.';   // returns "."
    path[1] = '\0';
  } else if (last_slash == path) {
    path[1] = '\0';  // returns "/"
  } else {
    *last_slash = '\0';
  }
}

static void out_str_n(const char* str, size_t n) {
  ri_write(STDERR_FILENO, str, n);
}

static void out_str(const char* str) {
  out_str_n(str, ri_strlen(str));
}

static char* ul_to_str(unsigned long i, char* out, unsigned char base) {
  char buf[65];
  char* cur = &buf[65];
  *--cur = '\0';
  do {
    *--cur = "0123456789abcdef"[i % base];
    i /= base;
  } while (i > 0);
  return ri_strcpy(out, cur);
}

static char* l_to_str(long i, char* out, unsigned char base) {
  if (i < 0) {
    *out = '-';
    ul_to_str(-(unsigned long)i, out + 1, base);
    return out;
  } else {
    return ul_to_str(i, out, base);
  }
}

static const char* ri_strerror(int err) {
  switch (err) {
    case EPERM: return "Operation not permitted";
    case ENOENT: return "No such file or directory";
    case EIO: return "I/O error";
    case ENXIO: return "No such device or address";
    case EAGAIN: return "Try again";
    case ENOMEM: return "Out of memory";
    case EACCES: return "Permission denied";
    case ENODEV: return "No such device";
    case ENOTDIR: return "Not a directory";
    case EINVAL: return "Invalid argument";
    case ENFILE: return "File table overflow";
    case EMFILE: return "Too many open files";
    case ESPIPE: return "Illegal seek";
    case ENAMETOOLONG: return "File name too long";
    case ELOOP: return "Too many symbolic links encountered";
  }
  static char buf[64];
  ri_strcpy(buf, "Unknown error ");
  l_to_str(err, buf + ri_strlen(buf), 10);
  return buf;
}

static void outv(const char *fmt, va_list ap) {
  char buf[65];
  while (true) {
    if (fmt[0] == '\0') break;

#define NUM_FMT(num_fmt, type, func, base)                  \
    if (!ri_strncmp(fmt, num_fmt, sizeof(num_fmt) - 1)) {   \
      out_str(func(va_arg(ap, type), buf, base));           \
      fmt += sizeof(num_fmt) - 1;                           \
      continue;                                             \
    }
    NUM_FMT("%d",  int,           l_to_str,  10);
    NUM_FMT("%ld", long,          l_to_str,  10);
    NUM_FMT("%u",  unsigned int,  ul_to_str, 10);
    NUM_FMT("%lu", unsigned long, ul_to_str, 10);
    NUM_FMT("%zu", size_t,        ul_to_str, 10);
    NUM_FMT("%x",  unsigned int,  ul_to_str, 16);
    NUM_FMT("%lx", unsigned long, ul_to_str, 16);
    NUM_FMT("%zx", size_t,        ul_to_str, 16);
#undef NUM_FMT

    if (!ri_strncmp(fmt, "%p", 2)) {
      out_str(ul_to_str((unsigned long)va_arg(ap, void*), buf, 16));
      fmt += 2;
    } else if (!ri_strncmp(fmt, "%s", 2)) {
      const char* arg = va_arg(ap, const char*);
      out_str(arg ? arg : "(null)");
      fmt += 2;
    } else if (!ri_strncmp(fmt, "%%", 2)) {
      out_str("%");
      fmt += 2;
    } else if (fmt[0] == '%') {
      buf[0] = fmt[1];
      buf[1] = '\0';
      out_str("relinterp error: unrecognized output specifier: '%");
      out_str(buf);
      out_str("'\n");
      ri_exit(1);
    } else {
      size_t len = 0;
      while (fmt[len] != '\0' && fmt[len] != '%') ++len;
      out_str_n(fmt, len);
      fmt += len;
    }
  }
}

__attribute__((format(printf, 1, 2)))
static void debug(const char* fmt, ...) {
  if (!g_debug) return;
  out_str("relinterp: ");

  va_list ap;
  va_start(ap, fmt);
  outv(fmt, ap);
  va_end(ap);
  out_str("\n");
}

__attribute__((format(printf, 1, 2), noreturn))
static void fatal(const char* fmt, ...) {
  out_str("relinterp: ");
  if (g_prog_name) {
    out_str(g_prog_name);
    out_str(": ");
  }
  out_str("fatal error: ");

  va_list ap;
  va_start(ap, fmt);
  outv(fmt, ap);
  va_end(ap);
  out_str("\n");
  ri_exit(1);
}

static void* optimizer_barrier(void* val) {
  __asm__ volatile ("nop" :: "r"(&val) : "memory");
  return val;
}

typedef struct {
  unsigned long key;
  unsigned long value;
} AuxEntry;

typedef struct {
  int argc;
  char **argv;
  char **envp;
  size_t envp_count;
  AuxEntry* auxv;
  size_t auxv_count;
} KernelArguments;

static KernelArguments read_args(void* raw_args) {
  KernelArguments result;
  result.argc = *(long*)raw_args;
  result.argv = (char**)((void**)raw_args + 1);
  result.envp = result.argv + result.argc + 1;

  char** envp = result.envp;
  while (*envp != NULL) ++envp;
  result.envp_count = envp - result.envp;
  ++envp;

  result.auxv = (AuxEntry*)envp;
  size_t count = 0;
  while (result.auxv[count].key != 0) {
    ++count;
  }
  result.auxv_count = count;
  return result;
}

static void dump_auxv(const KernelArguments* args) {
  for (size_t i = 0; i < args->auxv_count; ++i) {
    const char* name = "";
    switch (args->auxv[i].key) {
      case AT_BASE: name = " [AT_BASE]"; break;
      case AT_EGID: name = " [AT_EGID]"; break;
      case AT_ENTRY: name = " [AT_ENTRY]"; break;
      case AT_EUID: name = " [AT_EUID]"; break;
      case AT_GID: name = " [AT_GID]"; break;
      case AT_PAGESZ: name = " [AT_PAGESZ]"; break;
      case AT_PHDR: name = " [AT_PHDR]"; break;
      case AT_PHENT: name = " [AT_PHENT]"; break;
      case AT_PHNUM: name = " [AT_PHNUM]"; break;
      case AT_SECURE: name = " [AT_SECURE]"; break;
      case AT_SYSINFO: name = " [AT_SYSINFO]"; break;
      case AT_SYSINFO_EHDR: name = " [AT_SYSINFO_EHDR]"; break;
      case AT_UID: name = " [AT_UID]"; break;
    }
    debug("  %lu => 0x%lx%s", args->auxv[i].key, args->auxv[i].value, name);
  }
}

static unsigned long ri_getauxval(const KernelArguments* args, unsigned long kind,
                                  bool allow_missing) {
  for (size_t i = 0; i < args->auxv_count; ++i) {
    if (args->auxv[i].key == kind) return args->auxv[i].value;
  }
  if (!allow_missing) fatal("could not find aux vector entry %lu", kind);
  return 0;
}

static int elf_flags_to_prot(int flags) {
  int result = 0;
  if (flags & PF_R) result |= PROT_READ;
  if (flags & PF_W) result |= PROT_WRITE;
  if (flags & PF_X) result |= PROT_EXEC;
  return result;
}

typedef struct {
  int fd;
  char path[PATH_MAX];
} OpenedLoader;

typedef struct {
  void* base_addr;
  EntryFunc* entry;
} LoadedInterp;

static LoadedInterp load_interp(const OpenedLoader *loader, ElfW(Ehdr)* hdr) {
  ElfW(Phdr)* phdr = (ElfW(Phdr)*)((char*)hdr + hdr->e_phoff);
  size_t phdr_count = hdr->e_phnum;

  size_t max_vaddr = 0;

  // Find the virtual address extent.
  for (size_t i = 0; i < phdr_count; ++i) {
    if (phdr[i].p_type == PT_LOAD) {
      max_vaddr = PAGE_END(MAX(max_vaddr, phdr[i].p_vaddr + phdr[i].p_memsz));
    }
  }

  // Map an area to fit the loader.
  void* loader_vaddr = ri_mmap(NULL, max_vaddr, PROT_READ | PROT_WRITE,
                               MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  if (loader_vaddr == (void*)MAP_FAILED) {
    fatal("reservation mmap of 0x%zx bytes for %s failed: %s", max_vaddr, loader->path,
          ri_strerror(g_errno));
  }

  // Map each PT_LOAD.
  for (size_t i = 0; i < phdr_count; ++i) {
    if (phdr[i].p_type == PT_LOAD) {
      size_t start = PAGE_START(phdr[i].p_vaddr);
      const size_t end = PAGE_END(phdr[i].p_vaddr + phdr[i].p_memsz);
      if (phdr[i].p_filesz > 0) {
        const size_t file_end = phdr[i].p_vaddr + phdr[i].p_filesz;
        void* tmp = ri_mmap((char*)loader_vaddr + start,
                            file_end - start,
                            elf_flags_to_prot(phdr[i].p_flags),
                            MAP_PRIVATE | MAP_FIXED, loader->fd, PAGE_START(phdr[i].p_offset));
        if (tmp == (void*)MAP_FAILED) {
          fatal("PT_LOAD mmap failed (%s segment #%zu): %s", loader->path, i,
                ri_strerror(g_errno));
        }
        start = file_end;
        if (phdr[i].p_flags & PF_W) {
          // The bytes between p_filesz and PAGE_END(p_filesz) currently come from the file mapping,
          // but they need to be zeroed. (Apparently this zeroing isn't necessary if the segment isn't
          // writable, and zeroing a non-writable page would be inconvenient.)
          ri_memset((char*)loader_vaddr + start, '\0', PAGE_END(start) - start);
        }
        start = PAGE_END(start);
      }
      if (start < end) {
        // The memory is already zeroed, because it comes from an anonymous file mapping. Just set
        // the protections correctly.
        int result = ri_mprotect((char*)loader_vaddr + start, end - start,
                                 elf_flags_to_prot(phdr[i].p_flags));
        if (result != 0) {
          fatal("mprotect of PT_LOAD failed (%s segment #%zu): %s", loader->path, i,
                ri_strerror(g_errno));
        }
      }
    }
  }

  return (LoadedInterp) {
    .base_addr = loader_vaddr,
    .entry = (EntryFunc*)((uintptr_t)loader_vaddr + hdr->e_entry),
  };
}

typedef struct {
  ElfW(Phdr)* phdr;
  size_t phdr_count;
  uintptr_t load_bias;
  uintptr_t page_size;
  char* search_paths;
  ElfW(Ehdr)* ehdr;
  ElfW(Phdr)* first_load;
  bool secure;
} ExeInfo;

static ExeInfo get_exe_info(const KernelArguments* args) {
  ExeInfo result = { 0 };
  result.phdr = (ElfW(Phdr)*)ri_getauxval(args, AT_PHDR, false);
  result.phdr_count = ri_getauxval(args, AT_PHNUM, false);
  result.page_size = ri_getauxval(args, AT_PAGESZ, false);

  unsigned long uid = ri_getauxval(args, AT_UID, false);
  unsigned long euid = ri_getauxval(args, AT_EUID, false);
  unsigned long gid = ri_getauxval(args, AT_GID, false);
  unsigned long egid = ri_getauxval(args, AT_EGID, false);
  unsigned long secure = ri_getauxval(args, AT_SECURE, true);
  result.secure = uid != euid || gid != egid || secure;

  debug("orig phdr     = %p", (void*)result.phdr);
  debug("orig phnum    = %zu", result.phdr_count);

  for (size_t i = 0; i < result.phdr_count; ++i) {
    if (result.phdr[i].p_type == PT_DYNAMIC) {
      result.load_bias = (uintptr_t)&_DYNAMIC - result.phdr[i].p_vaddr;
    }
  }
  debug("load_bias     = 0x%lx", (unsigned long)result.load_bias);

  for (size_t i = 0; i < result.phdr_count; ++i) {
    ElfW(Phdr)* phdr = &result.phdr[i];
    if (phdr->p_type != PT_LOAD) continue;
    result.first_load = phdr;
    if (phdr->p_offset != 0) {
      fatal("expected zero p_offset for first PT_LOAD, found 0x%zx instead",
            (size_t)phdr->p_offset);
    }
    result.ehdr = (ElfW(Ehdr)*)(phdr->p_vaddr + result.load_bias);
    break;
  }
  debug("ehdr          = %p", (void*)result.ehdr);

  ElfW(Word) runpath_offset = -1;
  char* strtab = NULL;
  for (ElfW(Dyn)* dyn = _DYNAMIC; dyn->d_tag != DT_NULL; dyn++) {
    switch (dyn->d_tag) {
    case DT_RUNPATH:
      runpath_offset = dyn->d_un.d_val;
      break;
    case DT_RPATH:
      if (runpath_offset == -1) runpath_offset = dyn->d_un.d_val;
      break;
    case DT_STRTAB:
      strtab = (char*)(dyn->d_un.d_ptr + result.load_bias);
      break;
    }
  }

  if (strtab && runpath_offset != -1) {
    result.search_paths = strtab + runpath_offset;
    debug("dt_runpath    = %s", result.search_paths);
  }
  return result;
}

// Loaders typically read the PT_INTERP of the executable, e.g. to set a pathname on the loader.
// glibc insists on the executable having PT_INTERP, and aborts if it's missing.  Musl passes it
// to debuggers to find symbols for the loader, which includes all the libc symbols.
//
// Make a copy of the phdr table and insert PT_INTERP into the copy.
//
static void insert_pt_interp_into_phdr_table(const KernelArguments* args, const ExeInfo* exe,
                                             const char* loader_realpath) {
  // Reserve extra space for the inserted PT_PHDR and PT_INTERP segments and a null terminator.
  if (exe->phdr_count + 3 > sizeof(replacement_phdr_table) / sizeof(replacement_phdr_table[0])) {
    fatal("too many phdr table entries in executable");
  }

  ElfW(Phdr) newPhdr = {
    .p_type = PT_PHDR,
    // The replacement phdr is in the BSS section, which has no file location.
    // Use 0 for the offset.  If this causes a problem the replacement phdr could
    // be moved to the data section and the correct p_offset calculated.
    .p_offset = 0,
    .p_vaddr = (uintptr_t)&replacement_phdr_table - exe->load_bias,
    .p_paddr = (uintptr_t)&replacement_phdr_table - exe->load_bias,
    .p_memsz = (exe->phdr_count + 1) * sizeof(ElfW(Phdr)),
    .p_filesz = (exe->phdr_count + 1) * sizeof(ElfW(Phdr)),
    .p_flags = PF_R,
    .p_align = alignof(ElfW(Phdr)),
  };

  ElfW(Phdr*) cur = replacement_phdr_table;
  if (exe->phdr[0].p_type != PT_PHDR) {
    // ld.bfd does not insert a PT_PHDR if there is no PT_INTERP, fake one.
    // It has to be first.  We're adding an entry so increase memsz and filesz.
    newPhdr.p_memsz += sizeof(ElfW(Phdr));
    newPhdr.p_filesz += sizeof(ElfW(Phdr));
    *cur = newPhdr;
    ++cur;
  }

  for (size_t i = 0; i < exe->phdr_count; ++i) {
    switch (exe->phdr[i].p_type) {
    case 0:
      fatal("unexpected null phdr entry at index %zu", i);
      break;
    case PT_PHDR:
      *cur = newPhdr;
      break;
    default:
      *cur = exe->phdr[i];
    }
    ++cur;
  }

  // Insert PT_INTERP at the end.
  cur->p_type = PT_INTERP;
  cur->p_offset = 0;
  cur->p_vaddr = (uintptr_t)&replacement_interp - exe->load_bias;
  cur->p_paddr = cur->p_vaddr;
  cur->p_filesz = ri_strlen(replacement_interp) + 1;
  cur->p_memsz = ri_strlen(replacement_interp) + 1;
  cur->p_flags = PF_R;
  cur->p_align = 1;
  ++cur;

  ri_strcpy(replacement_interp, loader_realpath);

  debug("new phdr      = %p", (void*)&replacement_phdr_table);
  debug("new phnum     = %zu", cur - replacement_phdr_table);

  // Update the aux vector with the new phdr+phnum.
  for (size_t i = 0; i < args->auxv_count; ++i) {
    if (args->auxv[i].key == AT_PHDR) {
      args->auxv[i].value = (unsigned long)&replacement_phdr_table;
    } else if (args->auxv[i].key == AT_PHNUM) {
      args->auxv[i].value = cur - replacement_phdr_table;
    }
  }

  // AT_PHDR and AT_PHNUM are now updated to point to the replacement program
  // headers, but the e_phoff and e_phnum in the ELF headers still point to the
  // original program headers.  dynlink.c doesn't use e_phoff value from the
  // main application's program headers.  The e_phoff and e_phnum values could
  // be updated, but that would require using mprotect to allow modifications
  // to the read-only first page.
}

static void realpath_fd(int fd, const char* orig_path, char* out, size_t len) {
  char path[64];
  ri_strcpy(path, "/proc/self/fd/");
  ul_to_str(fd, path + ri_strlen(path), 10);
  ssize_t result = ri_readlink(path, out, len);
  if (result == -1) fatal("could not get realpath of %s: %s", orig_path, ri_strerror(g_errno));
  if ((size_t)result >= len) fatal("realpath of %s too long", orig_path);
}

static int open_loader(const ExeInfo* exe, const char* path, OpenedLoader* loader) {
  debug("trying to open '%s'", path);
  loader->fd = ri_open(path, O_RDONLY, 0);
  if (loader->fd < 0) {
    debug("could not open loader %s: %s", path, ri_strerror(g_errno));
    return -1;
  }

  ElfW(Ehdr) hdr;
  ssize_t l = ri_pread(loader->fd, &hdr, sizeof(hdr), 0);
  if (l < 0) {
    debug("reading elf header from %s failed: %s", path, ri_strerror(g_errno));
    return -1;
  }
  if (l != sizeof(hdr)) {
    debug("file %s too short to contain elf header", path);
    return -1;
  }

  if (hdr.e_ident[0] != ELFMAG0 ||
      hdr.e_ident[1] != ELFMAG1 ||
      hdr.e_ident[2] != ELFMAG2 ||
      hdr.e_ident[3] != ELFMAG3) {
    debug("file %s is not an elf file", path);
    return -1;
  }

  if (hdr.e_machine != exe->ehdr->e_machine) {
    debug("incorrect elf machine for loader %s, expected %d got %d",
          path, exe->ehdr->e_machine, hdr.e_machine);
    return -1;
  }

  if (hdr.e_ident[EI_CLASS] != exe->ehdr->e_ident[EI_CLASS]) {
    debug("incorrect elf class for loader %s, expected %d got %d",
          path, exe->ehdr->e_ident[EI_CLASS], hdr.e_ident[EI_CLASS]);
    return -1;
  }

  realpath_fd(loader->fd, path, loader->path, sizeof(loader->path));

  return 0;
}

static int open_rel_loader(const ExeInfo* exe, const char* dir, const char* rel, OpenedLoader* loader) {
  char buf[PATH_MAX];

  size_t dir_len = ri_strlen(dir);

  if (dir_len + (dir_len == 0 ? 1 : 0) + ri_strlen(rel) + 2 > sizeof(buf)) {
    debug("path to loader exceeds PATH_MAX: %s/%s", dir, rel);
    return 1;
  }

  if (dir_len == 0) {
    ri_strcpy(buf, ".");
  } else {
    ri_strcpy(buf, dir);
    if (dir[dir_len-1] != '/') {
      ri_strcat(buf, "/");
    }
  }
  ri_strcat(buf, rel);

  return open_loader(exe, buf, loader);
}

static void get_origin(char* buf, size_t buf_len) {
  ssize_t len = ri_readlink("/proc/self/exe", buf, buf_len);
  if (len <= 0 || (size_t)len >= buf_len) {
    fatal("could not readlink /proc/self/exe: %s", ri_strerror(g_errno));
  }
  buf[len] = '\0';

  ri_dirname(buf);
}

static int search_path_list_for_loader(const ExeInfo* exe, const char* loader_rel_path, const char* search_path,
                                       const char* search_path_name, bool expand_origin, OpenedLoader *loader) {
  char origin_buf[PATH_MAX];
  char* origin = NULL;

  const char* p = search_path;
  while (p && p[0]) {
    const char* start = p;
    const char* end = ri_strchr(p, ':');
    if (end == NULL) {
      end = start + ri_strlen(p);
      p = NULL;
    } else {
      p = end + 1;
    }
    size_t n = end - start;
    char search_path_entry[PATH_MAX];
    if (n >= sizeof(search_path_entry)) {
      // Too long, skip.
      debug("%s entry too long: %s", search_path_name, start);
      continue;
    }

    ri_memcpy(search_path_entry, start, n);
    search_path_entry[n] = '\0';

    char buf[PATH_MAX];
    char* d = NULL;
    if (expand_origin) {
      d = ri_strchr(search_path_entry, '$');
    }
    if (d && (!ri_strncmp(d, "$ORIGIN", 7) || !ri_strncmp(d, "${ORIGIN}", 9))) {
      if (!origin) {
        get_origin(origin_buf, sizeof(origin_buf));
        origin = origin_buf;
      }

      size_t s = 7;
      if (d[1] == '{') {
        s += 2;
      }
      ri_memcpy(buf, search_path_entry, d - search_path_entry);
      buf[d - search_path_entry] = '\0';
      if (ri_strlen(buf) + ri_strlen(origin) + ri_strlen(d+s) >= sizeof(buf)) {
        debug("path to loader %s%s%s too long", buf, origin, d+s);
        continue;
      }

      ri_strcat(buf, origin);
      ri_strcat(buf, d+s);
    } else {
      ri_strcpy(buf, search_path_entry);
    }
    debug("trying loader %s at %s", loader_rel_path, buf);
    if (!open_rel_loader(exe, buf, loader_rel_path, loader)) {
      debug("opened loader %s at %s", loader_rel_path, buf);
      return 0;
    }
  }

  return -1;
}

static int find_and_open_loader(const ExeInfo* exe, const char* ld_library_path, OpenedLoader* loader) {
  const char* loader_rel_path = LOADER_PATH;

  if (loader_rel_path[0] == '/') {
    return open_loader(exe, loader_rel_path, loader);
  }

  if (exe->secure) {
    fatal("relinterp not supported for secure executables");
  }

  if (!search_path_list_for_loader(exe, loader_rel_path, ld_library_path, "LD_LIBRARY_PATH", false, loader)) {
    return 0;
  }

  if (!exe->search_paths || ri_strlen(exe->search_paths) == 0) {
    // If no DT_RUNPATH search relative to the exe.
    char origin[PATH_MAX];
    get_origin(origin, sizeof(origin));
    return open_rel_loader(exe, origin, loader_rel_path, loader);
  }

  if (!search_path_list_for_loader(exe, loader_rel_path, exe->search_paths, "rpath", true, loader)) {
    return 0;
  }

  fatal("unable to find loader %s in rpath %s", loader_rel_path, exe->search_paths);
}

// Use a trick to determine whether the executable has been relocated yet. This variable points to
// a variable in libc. It will be NULL if and only if the program hasn't been linked yet. This
// should accommodate these situations:
//  - The program was actually statically-linked instead.
//  - Either a PIE or non-PIE dynamic executable.
//  - Any situation where the loader calls the executable's _start:
//     - In normal operation, the kernel calls the executable's _start, _start jumps to the loader's
//       entry point, which jumps to _start again after linking it.
//     - The executable actually has its PT_INTERP set after all.
//     - The user runs the loader, passing it the path of the executable.
// This C file must always be compiled as PIC, or else the linker will use a COPY relocation and
// duplicate "environ" into the executable.
static bool is_exe_relocated(void) {
  // Use the GOT to get the address of environ.
  extern char** environ;
  void* read_environ = optimizer_barrier(&environ);
  debug("read_environ = %p", read_environ);
  return read_environ != NULL;
}

void _start_c(long* raw_args) {
  const KernelArguments args = read_args(raw_args);
  const char* ld_library_path = NULL;

  for (size_t i = 0; i < args.envp_count; ++i) {
    if (!ri_strcmp(args.envp[i], "RELINTERP_DEBUG=1")) {
      g_debug = true;
    }
    if (!ri_strncmp(args.envp[i], "LD_LIBRARY_PATH=", 16)) {
      ld_library_path = args.envp[i] + 16;
    }
  }
  if (args.argc >= 1) {
    g_prog_name = args.argv[0];
  }

  if (is_exe_relocated()) {
    debug("exe is already relocated, starting main executable");
    int argc = raw_args[0];
    char **argv = (void *)(raw_args+1);
    __libc_start_main(main, argc, argv, _init, _fini, 0);
  }

  debug("entering relinterp");

  const ExeInfo exe = get_exe_info(&args);
  g_page_size = exe.page_size;

  OpenedLoader loader;
  if (find_and_open_loader(&exe, ld_library_path, &loader)) {
    fatal("failed to open loader");
  }
  off_t len = ri_lseek(loader.fd, 0, SEEK_END);
  if (len == (off_t)-1) fatal("lseek on %s failed: %s", loader.path, ri_strerror(g_errno));

  void* loader_data = ri_mmap(NULL, len, PROT_READ, MAP_PRIVATE, loader.fd, 0);
  if (loader_data == (void*)MAP_FAILED) {
    fatal("could not mmap %s: %s", loader.path, ri_strerror(g_errno));
  }

  LoadedInterp interp = load_interp(&loader, (ElfW(Ehdr)*)loader_data);
  if (ri_munmap(loader_data, len) != 0) fatal("munmap failed: %s", ri_strerror(g_errno));

  debug("original auxv:");
  dump_auxv(&args);

  // Create a virtual phdr table that includes PT_INTERP, for the benefit of loaders that read the
  // executable PT_INTERP.
  insert_pt_interp_into_phdr_table(&args, &exe, loader.path);
  ri_close(loader.fd);

  // TODO: /proc/pid/auxv isn't updated with the new auxv vector. Is it possible to update it?
  // XXX: If we try to update it, we'd use prctl(PR_SET_MM, PR_SET_MM_AUXV, &vec, size, 0)
  // Maybe updating it would be useful as a way to communicate the loader's base to a debugger.
  // e.g. lldb uses AT_BASE in the aux vector, but it caches the values at process startup, so
  // it wouldn't currently notice a changed value.

  // The loader uses AT_BASE to locate itself, so search for the entry and update it. Even though
  // its value is always zero, the kernel still includes the entry[0]. If this changes (or we want
  // to make weaker assumptions about the kernel's behavior), then we can copy the kernel arguments
  // onto the stack (e.g. using alloca) before jumping to the loader's entry point.
  // [0] https://github.com/torvalds/linux/blob/v5.13/fs/binfmt_elf.c#L263
  for (size_t i = 0; i < args.auxv_count; ++i) {
    if (args.auxv[i].key == AT_BASE) {
      args.auxv[i].value = (unsigned long)interp.base_addr;
      debug("new auxv:");
      dump_auxv(&args);
      debug("transferring to real loader");
      CRTJMP(interp.entry, raw_args);
    }
  }
  fatal("AT_BASE not found in aux vector");
}


// Normally gdb and lldb look for a symbol named "_dl_debug_state" in the
// interpreter to get notified when the dynamic loader has modified the
// list of shared libraries.  When using relinterp, the debugger is not
// aware of the interpreter (PT_INTERP is unset and auxv AT_BASE is 0) so it
// doesn't know where to look for the symbol.  It falls back to looking in the
// executable, so provide a symbol for it to find.  The dynamic loader will
// need to forward its calls to its own _dl_debug_state symbol to this one.
//
// This has to be defined in a .c file because lldb looks for a symbol with
// DWARF language type DW_LANG_C.
extern void _dl_debug_state() {
}
